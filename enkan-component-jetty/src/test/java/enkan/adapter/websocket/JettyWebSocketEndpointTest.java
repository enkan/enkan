package enkan.adapter.websocket;

import enkan.web.websocket.WebSocketHandler;
import enkan.web.websocket.WebSocketSession;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.api.exceptions.WebSocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class JettyWebSocketEndpointTest {

    // --- tracking handler ---------------------------------------------------

    static class TrackingHandler implements WebSocketHandler {
        final List<WebSocketSession> opens = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final List<byte[]> binaries = new ArrayList<>();
        final List<Integer> closeCodes = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();

        @Override public void onOpen(WebSocketSession s) { opens.add(s); }
        @Override public void onMessage(WebSocketSession s, String m) { messages.add(m); }
        @Override public void onBinary(WebSocketSession s, ByteBuffer d) {
            byte[] copy = new byte[d.remaining()];
            d.get(copy);
            binaries.add(copy);
        }
        @Override public void onClose(WebSocketSession s, int code, String r) { closeCodes.add(code); }
        @Override public void onError(WebSocketSession s, Throwable t) { errors.add(t); }
    }

    // --- stub Jetty Session -------------------------------------------------

    static class StubSession implements Session {
        final List<String> sentTexts = new ArrayList<>();
        final List<byte[]> sentBinaries = new ArrayList<>();
        boolean open = true;

        @Override public void demand() {}
        @Override public void sendText(String t, Callback cb) { sentTexts.add(t); cb.succeed(); }
        @Override public void sendBinary(ByteBuffer d, Callback cb) {
            byte[] copy = new byte[d.remaining()]; d.get(copy);
            sentBinaries.add(copy); cb.succeed();
        }
        @Override public void sendPartialText(String t, boolean l, Callback cb) {}
        @Override public void sendPartialBinary(ByteBuffer d, boolean l, Callback cb) {}
        @Override public void sendPing(ByteBuffer d, Callback cb) {}
        @Override public void sendPong(ByteBuffer d, Callback cb) {}
        @Override public void close() { open = false; }
        @Override public void close(int code, String r, Callback cb) { open = false; cb.succeed(); }
        @Override public void disconnect() { open = false; }
        @Override public boolean isOpen() { return open; }
        @Override public boolean isSecure() { return false; }
        @Override public SocketAddress getLocalSocketAddress() { return null; }
        @Override public SocketAddress getRemoteSocketAddress() { return null; }
        @Override public String getProtocolVersion() { return "13"; }
        @Override public UpgradeRequest getUpgradeRequest() { return null; }
        @Override public UpgradeResponse getUpgradeResponse() { return null; }
        @Override public void addIdleTimeoutListener(Predicate<WebSocketTimeoutException> p) {}
        @Override public Duration getIdleTimeout() { return Duration.ofSeconds(30); }
        @Override public void setIdleTimeout(Duration d) {}
        @Override public int getInputBufferSize() { return 4096; }
        @Override public void setInputBufferSize(int s) {}
        @Override public int getOutputBufferSize() { return 4096; }
        @Override public void setOutputBufferSize(int s) {}
        @Override public long getMaxBinaryMessageSize() { return 65535; }
        @Override public void setMaxBinaryMessageSize(long s) {}
        @Override public long getMaxTextMessageSize() { return 65535; }
        @Override public void setMaxTextMessageSize(long s) {}
        @Override public long getMaxFrameSize() { return 65535; }
        @Override public void setMaxFrameSize(long s) {}
        @Override public boolean isAutoFragment() { return false; }
        @Override public void setAutoFragment(boolean b) {}
        @Override public int getMaxOutgoingFrames() { return -1; }
        @Override public void setMaxOutgoingFrames(int i) {}
    }

    private TrackingHandler handler;
    private JettyWebSocketEndpoint endpoint;
    private StubSession jettySession;

    @BeforeEach
    void setUp() {
        handler = new TrackingHandler();
        endpoint = new JettyWebSocketEndpoint("test-id", handler);
        jettySession = new StubSession();
        endpoint.onWebSocketOpen(jettySession);
    }

    // --- onOpen -------------------------------------------------------------

    @Test
    void onOpenDispatchesWithCorrectSessionId() {
        assertThat(handler.opens).hasSize(1);
        assertThat(handler.opens.get(0).getId()).isEqualTo("test-id");
    }

    @Test
    void sessionIsOpenAfterConnect() {
        assertThat(handler.opens.get(0).isOpen()).isTrue();
    }

    // --- onMessage ----------------------------------------------------------

    @Test
    void textMessageIsDispatchedToHandler() {
        endpoint.onWebSocketText("hello world");
        assertThat(handler.messages).containsExactly("hello world");
    }

    @Test
    void multipleTextMessagesAreDispatchedInOrder() {
        endpoint.onWebSocketText("first");
        endpoint.onWebSocketText("second");
        assertThat(handler.messages).containsExactly("first", "second");
    }

    // --- onBinary -----------------------------------------------------------

    @Test
    void binaryPayloadIsDeliveredToHandler() {
        byte[] data = "binary-data".getBytes(StandardCharsets.UTF_8);
        ByteBuffer payload = ByteBuffer.wrap(data);
        endpoint.onWebSocketBinary(payload, Callback.NOOP);
        assertThat(handler.binaries).hasSize(1);
        assertThat(handler.binaries.get(0)).isEqualTo(data);
    }

    @Test
    void binaryPayloadIsCopiedBeforeCallbackSucceeds() {
        // Simulate Jetty recycling the buffer when callback.succeed() is called.
        byte[] original = "payload".getBytes(StandardCharsets.UTF_8);
        ByteBuffer payload = ByteBuffer.wrap(original);

        Callback recyclingCallback = Callback.from(
                () -> {
                    // Clear the original buffer after succeed — simulates Jetty recycling.
                    payload.clear();
                    payload.put(new byte[original.length]);
                    payload.flip();
                },
                _ -> {}
        );

        endpoint.onWebSocketBinary(payload, recyclingCallback);

        // The handler must have received a copy, not the (now-cleared) original.
        assertThat(handler.binaries).hasSize(1);
        assertThat(new String(handler.binaries.get(0), StandardCharsets.UTF_8)).isEqualTo("payload");
    }

    // --- onClose ------------------------------------------------------------

    @Test
    void closeEventIsDispatchedToHandler() {
        endpoint.onWebSocketClose(1000, "Normal Closure");
        assertThat(handler.closeCodes).containsExactly(1000);
    }

    // --- onError ------------------------------------------------------------

    @Test
    void errorIsDispatchedToHandler() {
        var ex = new RuntimeException("connection reset");
        endpoint.onWebSocketError(ex);
        assertThat(handler.errors).containsExactly(ex);
    }

    // --- sendText error routing ---------------------------------------------

    @Test
    void sendTextFailureIsRoutedToOnError() {
        var sendError = new RuntimeException("send failed");
        var failingSession = new StubSession() {
            @Override
            public void sendText(String t, Callback cb) {
                cb.fail(sendError);
            }
        };

        var h = new TrackingHandler();
        var ep = new JettyWebSocketEndpoint("err-id", h);
        ep.onWebSocketOpen(failingSession);

        h.opens.get(0).sendText("trigger-failure");

        assertThat(h.errors).hasSize(1);
        assertThat(h.errors.get(0)).isSameAs(sendError);
    }

    // --- sendBinary error routing -------------------------------------------

    @Test
    void sendBinaryFailureIsRoutedToOnError() {
        var sendError = new RuntimeException("binary send failed");
        var failingSession = new StubSession() {
            @Override
            public void sendBinary(ByteBuffer d, Callback cb) {
                cb.fail(sendError);
            }
        };

        var h = new TrackingHandler();
        var ep = new JettyWebSocketEndpoint("err-id", h);
        ep.onWebSocketOpen(failingSession);

        h.opens.get(0).sendBinary(ByteBuffer.wrap(new byte[]{1, 2, 3}));

        assertThat(h.errors).hasSize(1);
        assertThat(h.errors.get(0)).isSameAs(sendError);
    }

    // --- sendBinary buffer copy ---------------------------------------------

    @Test
    void sendBinaryDoesNotExposeOriginalBufferToJetty() {
        // Verify that JettyWebSocketSession copies the buffer before passing it to Jetty,
        // so callers can safely reuse/modify the buffer after sendBinary() returns.
        var capturedBuffers = new java.util.ArrayList<ByteBuffer>();
        var capturingSession = new StubSession() {
            @Override
            public void sendBinary(ByteBuffer d, Callback cb) {
                // Record the buffer that Jetty received (before cb.succeed modifies anything).
                capturedBuffers.add(d);
                cb.succeed();
            }
        };

        var h = new TrackingHandler();
        var ep = new JettyWebSocketEndpoint("copy-id", h);
        ep.onWebSocketOpen(capturingSession);

        byte[] original = "hello".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(original);
        h.opens.get(0).sendBinary(buf);

        // Mutate the original array after the call — Jetty's copy must be unaffected.
        original[0] = 'X';

        assertThat(capturedBuffers).hasSize(1);
        byte[] sent = new byte[capturedBuffers.get(0).remaining()];
        capturedBuffers.get(0).get(sent);
        assertThat(new String(sent, StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    // --- onBinary exception handling ----------------------------------------

    @Test
    void onBinaryHandlerExceptionRoutesToOnErrorAndFailsCallback() {
        var handlerError = new RuntimeException("onBinary exploded");
        var throwingHandler = new TrackingHandler() {
            @Override
            public void onBinary(WebSocketSession s, java.nio.ByteBuffer d) {
                throw handlerError;
            }
        };

        var ep = new JettyWebSocketEndpoint("ex-id", throwingHandler);
        ep.onWebSocketOpen(jettySession);

        var callbackFailures = new java.util.ArrayList<Throwable>();
        Callback recordingCallback = Callback.from(() -> {}, callbackFailures::add);

        ep.onWebSocketBinary(ByteBuffer.wrap(new byte[]{9}), recordingCallback);

        assertThat(throwingHandler.errors).hasSize(1);
        assertThat(throwingHandler.errors.get(0)).isSameAs(handlerError);
        assertThat(callbackFailures).hasSize(1);
        assertThat(callbackFailures.get(0)).isSameAs(handlerError);
    }
}
