package enkan.system.repl;

import enkan.system.ReplResponse;
import enkan.system.repl.serdes.Fressian;
import enkan.system.repl.serdes.ReplResponseReader;
import enkan.system.repl.serdes.ReplResponseWriter;
import enkan.system.repl.serdes.ResponseStatusReader;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMsg;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal ZMQ DEALER client that speaks the Enkan REPL protocol (Fressian over ZMQ).
 * Used only in tests — not part of production code.
 */
public class ReplTestClient implements AutoCloseable {
    private final ZContext ctx;
    private final org.zeromq.ZMQ.Socket socket;
    private final Fressian fressian;

    public ReplTestClient(int port) {
        ctx = new ZContext();
        socket = ctx.createSocket(SocketType.DEALER);
        socket.setReceiveTimeOut(5000);
        socket.connect("tcp://localhost:" + port);

        fressian = new Fressian();
        fressian.putReadHandler(ReplResponse.class, new ReplResponseReader());
        fressian.putReadHandler(ReplResponse.ResponseStatus.class, new ResponseStatusReader());
        fressian.putWriteHandler(ReplResponse.class, new ReplResponseWriter());
        fressian.putWriteHandler(ReplResponse.ResponseStatus.class, new ReplResponseWriter());
    }

    /**
     * Sends a raw text command (e.g. "/help" or "1 + 1") and collects all
     * ReplResponse frames until one carries the DONE or SHUTDOWN status.
     */
    public List<ReplResponse> send(String command) {
        socket.send(command);
        List<ReplResponse> responses = new ArrayList<>();
        while (true) {
            ZMsg msg = ZMsg.recvMsg(socket);
            if (msg == null) {
                throw new AssertionError("Timed out waiting for response to: " + command);
            }
            ReplResponse res = fressian.read(msg.pop().getData(), ReplResponse.class);
            responses.add(res);
            if (res.getStatus().contains(ReplResponse.ResponseStatus.DONE)
                    || res.getStatus().contains(ReplResponse.ResponseStatus.SHUTDOWN)) {
                break;
            }
        }
        return responses;
    }

    /**
     * Requests the completer port via "/completer" and returns it.
     */
    public int fetchCompleterPort() {
        List<ReplResponse> responses = send("/completer");
        String port = responses.stream()
                .map(ReplResponse::getOut)
                .filter(o -> o != null && o.matches("\\d+"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No completer port returned"));
        return Integer.parseInt(port);
    }

    /**
     * Sends a completion request to the completer socket and returns the candidates.
     * The completer socket uses DEALER/ROUTER with frames: ["", input, cursor].
     */
    public List<String> complete(int completerPort, String input, int cursor) {
        org.zeromq.ZMQ.Socket completerSock = ctx.createSocket(SocketType.DEALER);
        completerSock.setReceiveTimeOut(15000);
        completerSock.connect("tcp://localhost:" + completerPort);
        try {
            ZMsg req = new ZMsg();
            req.add("");         // delimiter
            req.add(input);
            req.add(Integer.toString(cursor));
            req.send(completerSock);

            ZMsg reply = ZMsg.recvMsg(completerSock);
            if (reply == null) {
                throw new AssertionError("Timed out waiting for completion reply");
            }
            reply.pop(); // delimiter
            if (reply.isEmpty()) {
                return List.of();
            }
            reply.pop(); // anchor
            List<String> candidates = new ArrayList<>();
            while (!reply.isEmpty()) {
                candidates.add(reply.popString());
            }
            return candidates;
        } finally {
            completerSock.close();
        }
    }

    @Override
    public void close() {
        ctx.close();
    }
}
