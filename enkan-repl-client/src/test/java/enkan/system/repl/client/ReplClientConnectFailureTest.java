package enkan.system.repl.client;

import enkan.system.ReplResponse;
import enkan.system.repl.serdes.Fressian;
import enkan.system.repl.serdes.ReplResponseWriter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: typing {@code /connect <bad port>} where nothing is listening
 * must NOT silently terminate the REPL. The user should see a clear error
 * message and the REPL should still be available to accept the next command.
 */
class ReplClientConnectFailureTest {

    /**
     * Picks a port number that no process should be listening on. We bind to
     * an ephemeral port and immediately close, then assume the port stays free
     * for the duration of the test (race-tolerant: even if something else binds
     * later, the test still exercises the failure path).
     */
    private static int unusedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static LineReader newDumbLineReader(ByteArrayOutputStream out) throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .streams(new ByteArrayInputStream(new byte[0]), out)
                .dumb(true)
                .build();
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }

    @Test
    @Timeout(value = 15, unit = java.util.concurrent.TimeUnit.SECONDS)
    void connectToNonListeningPortDoesNotKillRepl() throws Exception {
        int badPort = unusedPort();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        LineReader reader = newDumbLineReader(captured);

        ReplClient.ConsoleHandler handler = new ReplClient.ConsoleHandler(reader);
        try {
            handler.connect("localhost", badPort);

            // Give async monitor / poll one beat to surface its result.
            // (connect() should already be synchronous in returning, but
            // belt-and-braces.)
            Thread.sleep(200);

            // The REPL must still be available — i.e. the connect failure
            // must not have set isAvailable=false.
            assertThat(handler.isAvailable())
                    .as("REPL must remain available after a failed /connect")
                    .isTrue();

            // The user must see *some* error message in the terminal output.
            String terminalOutput = captured.toString(StandardCharsets.UTF_8);
            assertThat(terminalOutput)
                    .as("Terminal output should explain the connect failure, got: <%s>",
                            terminalOutput)
                    .containsIgnoringCase("connect");

            // connectedPort should remain unset (-1) so the prompt still shows the
            // disconnected indicator.
            assertThat(handler.getConnectedPort())
                    .as("connectedPort must remain -1 after a failed /connect")
                    .isLessThan(0);
        } finally {
            handler.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void repeatedFailedConnectAttemptsDoNotLeakStateOrKillRepl() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        LineReader reader = newDumbLineReader(captured);

        ReplClient.ConsoleHandler handler = new ReplClient.ConsoleHandler(reader);
        try {
            // Three failures in a row must each fail cleanly without
            // accumulating instance-field damage.
            for (int i = 0; i < 3; i++) {
                handler.connect("localhost", unusedPort());
                assertThat(handler.isAvailable())
                        .as("REPL still available after attempt %d", i + 1)
                        .isTrue();
                assertThat(handler.getConnectedPort())
                        .as("connectedPort still -1 after attempt %d", i + 1)
                        .isLessThan(0);
            }

            // The error message should contain "Failed to connect" at least three times.
            String terminalOutput = captured.toString(StandardCharsets.UTF_8);
            int errCount = terminalOutput.split("Failed to connect to ", -1).length - 1;
            assertThat(errCount)
                    .as("Each attempt should print a connect-failure message; got: <%s>",
                            terminalOutput)
                    .isGreaterThanOrEqualTo(3);
        } finally {
            handler.close();
        }
    }

    /**
     * Phase-2 monitor path regression: after a successful /connect, if the server
     * shuts down, the REPL must detect the disconnect and mark itself unavailable
     * (serverDisconnected path). The ZMQ monitor fires DISCONNECTED after handshake,
     * which must call close() and set isAvailable=false.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void serverShutdownAfterSuccessfulConnectClosesRepl() throws Exception {
        Fressian fressian = new Fressian();
        fressian.putWriteHandler(ReplResponse.class, new ReplResponseWriter());

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        LineReader reader = newDumbLineReader(captured);
        ReplClient.ConsoleHandler handler = new ReplClient.ConsoleHandler(reader);

        try (ZContext serverCtx = new ZContext()) {
            // Bind a ROUTER socket to act as the REPL server.
            ZMQ.Socket server = serverCtx.createSocket(SocketType.ROUTER);
            int port = server.bindToRandomPort("tcp://localhost");

            // Start a thread to handle the /completer handshake, then shut down.
            Thread serverThread = Thread.ofVirtual().start(() -> {
                // Receive the /completer message (ROUTER frame: identity + body).
                ZMsg req = ZMsg.recvMsg(server);
                if (req == null) return;
                byte[] identity = req.pop().getData();

                // Reply with a ReplResponse whose out = "-1" (no completer port).
                ReplResponse resp = new ReplResponse();
                resp.setOut("-1");
                byte[] payload = fressian.write(resp);

                ZMsg reply = new ZMsg();
                reply.add(identity);
                reply.add(payload);
                reply.send(server);

                // Brief pause so the client finishes committing sockets, then shut down.
                try { Thread.sleep(300); } catch (InterruptedException ignored) { }
                server.close();
                serverCtx.close();
            });

            // Connect — this should succeed (completer handshake completes).
            handler.connect("localhost", port);
            assertThat(handler.getConnectedPort())
                    .as("connectedPort must be set after a successful connect")
                    .isEqualTo(port);

            serverThread.join(10_000);

            // Give the ZMQ monitor thread time to detect DISCONNECTED and call close().
            long deadline = System.currentTimeMillis() + 8_000;
            while (handler.isAvailable() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }

            assertThat(handler.isAvailable())
                    .as("REPL must become unavailable after server shuts down")
                    .isFalse();
        } finally {
            handler.close();
        }
    }

    /**
     * Regression: after a failed /connect, connectedPort must remain -1.
     * A subsequent failed connect must also leave connectedPort at -1, confirming
     * that closePreviousConnection() does not incorrectly promote the port field.
     */
    @Test
    @Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void failedConnectFollowedByAnotherFailedConnectLeavesPortAtMinusOne() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        LineReader reader = newDumbLineReader(captured);

        ReplClient.ConsoleHandler handler = new ReplClient.ConsoleHandler(reader);
        try {
            int port1 = unusedPort();
            int port2 = unusedPort();

            handler.connect("localhost", port1);
            assertThat(handler.getConnectedPort())
                    .as("connectedPort must be -1 after first failed connect to %d", port1)
                    .isLessThan(0);

            handler.connect("localhost", port2);
            assertThat(handler.getConnectedPort())
                    .as("connectedPort must be -1 after second failed connect to %d", port2)
                    .isLessThan(0);

            // REPL must still be alive after both failures.
            assertThat(handler.isAvailable())
                    .as("REPL must remain available after two consecutive failed connects")
                    .isTrue();
        } finally {
            handler.close();
        }
    }
}
