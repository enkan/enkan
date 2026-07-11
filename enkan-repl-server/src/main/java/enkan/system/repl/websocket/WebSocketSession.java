package enkan.system.repl.websocket;

import org.jspecify.annotations.Nullable;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Represents a single WebSocket connection.
 * Handles RFC 6455 frame encoding/decoding.
 *
 * @author kawasima
 */
public class WebSocketSession implements Closeable {
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final String id;
    private volatile boolean closed;

    /**
     * Create a session with pre-existing buffered streams.
     * The caller is responsible for ensuring these streams are the same ones
     * used during the HTTP upgrade handshake (to avoid data loss from buffering).
     */
    public WebSocketSession(Socket socket, InputStream in, OutputStream out) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.id = socket.getRemoteSocketAddress().toString();
    }

    public String getId() {
        return id;
    }

    public boolean isClosed() {
        return closed || socket.isClosed();
    }

    /**
     * Read the next text message from the WebSocket connection (blocking).
     *
     * @return the text message, or null if the connection was closed
     * @throws IOException if an I/O error occurs
     */
    public @Nullable String readMessage() throws IOException {
        while (!closed) {
            int firstByte = in.read();
            if (firstByte == -1) {
                close();
                return null;
            }

            int opcode = firstByte & 0x0F;

            int secondByte = in.read();
            if (secondByte == -1) {
                close();
                return null;
            }

            boolean masked = (secondByte & 0x80) != 0;
            long payloadLength = secondByte & 0x7F;

            if (payloadLength == 126) {
                payloadLength = readUint16();
            } else if (payloadLength == 127) {
                payloadLength = readUint64();
            }

            byte[] maskKey = null;
            if (masked) {
                maskKey = readExact(4);
            }

            byte[] payload = readExact((int) payloadLength);

            if (masked) {
                unmask(payload, Objects.requireNonNull(maskKey));
            }

            switch (opcode) {
                case OPCODE_TEXT:
                    return new String(payload, StandardCharsets.UTF_8);

                case OPCODE_CLOSE:
                    sendCloseFrame();
                    close();
                    return null;

                case OPCODE_PING:
                    sendFrame(OPCODE_PONG, payload);
                    break;

                case OPCODE_PONG:
                    // ignore
                    break;

                default:
                    // unsupported opcode, skip
                    break;
            }
        }
        return null;
    }

    /**
     * Send a text message over the WebSocket connection.
     *
     * @param text the message to send
     * @throws IOException if an I/O error occurs
     */
    public synchronized void sendText(String text) throws IOException {
        if (closed) return;
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        sendFrame(OPCODE_TEXT, payload);
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
        // FIN bit set, opcode
        out.write(0x80 | opcode);

        // Server does not mask
        if (payload.length <= 125) {
            out.write(payload.length);
        } else if (payload.length <= 65535) {
            out.write(126);
            out.write((payload.length >> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        } else {
            out.write(127);
            long len = payload.length;
            for (int i = 7; i >= 0; i--) {
                out.write((int) ((len >> (8 * i)) & 0xFF));
            }
        }

        out.write(payload);
        out.flush();
    }

    private void sendCloseFrame() {
        try {
            sendFrame(OPCODE_CLOSE, new byte[0]);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private int readUint16() throws IOException {
        byte[] buf = readExact(2);
        return ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
    }

    private long readUint64() throws IOException {
        byte[] buf = readExact(8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (buf[i] & 0xFF);
        }
        return value;
    }

    private byte[] readExact(int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buf, offset, length - offset);
            if (read == -1) {
                throw new EOFException("WebSocket connection closed unexpectedly");
            }
            offset += read;
        }
        return buf;
    }

    private static void unmask(byte[] payload, byte[] maskKey) {
        for (int i = 0; i < payload.length; i++) {
            payload[i] ^= maskKey[i % 4];
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        socket.close();
    }
}
