package enkan.data;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Represents a single Server-Sent Event as defined by the
 * <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">WHATWG EventSource specification</a>.
 *
 * <p>Use the {@link #builder()} method to construct instances:</p>
 * <pre>{@code
 * SseEvent event = SseEvent.builder()
 *     .event("update")
 *     .data("{\"count\":42}")
 *     .id("msg-42")
 *     .build();
 * }</pre>
 *
 * @param data  the event payload (may contain newlines — each line becomes a separate {@code data:} field)
 * @param event the event type (maps to the {@code event:} field; {@code null} means "message")
 * @param id    the event ID (maps to the {@code id:} field; used by the client for reconnection)
 * @param retry the reconnection time hint (maps to the {@code retry:} field)
 * @author kawasima
 */
public record SseEvent(String data, String event, String id, Duration retry) {

    /**
     * Validates fields according to the WHATWG SSE specification.
     * <ul>
     *   <li>{@code event} and {@code id} must not contain newlines (CR, LF) or NUL</li>
     *   <li>{@code retry} must not be negative</li>
     * </ul>
     */
    public SseEvent {
        if (event != null) {
            requireNoLineBreaksOrNul(event, "event");
        }
        if (id != null) {
            requireNoLineBreaksOrNul(id, "id");
        }
        if (retry != null && retry.isNegative()) {
            throw new IllegalArgumentException("SSE retry must not be negative");
        }
    }

    private static void requireNoLineBreaksOrNul(String value, String fieldName) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r') {
                throw new IllegalArgumentException(
                        "SSE " + fieldName + " must not contain line breaks");
            }
            if (c == '\0') {
                throw new IllegalArgumentException(
                        "SSE " + fieldName + " must not contain U+0000");
            }
        }
    }

    /**
     * Creates a simple data-only event.
     *
     * @param data the event payload
     * @return a new SseEvent
     */
    public static SseEvent of(String data) {
        return new SseEvent(data, null, null, null);
    }

    /**
     * Returns a keep-alive comment event ({@code : keep-alive}).
     * Comments are ignored by EventSource clients but prevent proxies
     * from closing idle connections.
     */
    public static SseEvent keepAlive() {
        return KEEP_ALIVE;
    }

    private static final SseEvent KEEP_ALIVE = new SseEvent(null, null, null, null);

    /**
     * Formats this event as a wire-format SSE block.
     *
     * @return the formatted string ready to write to the output stream
     */
    public String toWireFormat() {
        if (this == KEEP_ALIVE) {
            return ": keep-alive\n\n";
        }

        StringBuilder sb = new StringBuilder();
        if (event != null) {
            sb.append("event: ").append(event).append('\n');
        }
        if (id != null) {
            sb.append("id: ").append(id).append('\n');
        }
        if (retry != null) {
            sb.append("retry: ").append(retry.toMillis()).append('\n');
        }
        if (data != null) {
            int start = 0;
            int len = data.length();
            while (start < len) {
                int idx = indexOfLineBreak(data, start);
                if (idx < 0) {
                    break;
                }
                sb.append("data: ").append(data, start, idx).append('\n');
                start = (idx < len - 1 && data.charAt(idx) == '\r' && data.charAt(idx + 1) == '\n')
                        ? idx + 2 : idx + 1;
            }
            sb.append("data: ").append(data, start, len).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Finds the index of the next line break (LF, CR, or CRLF) starting from {@code from}.
     *
     * @return the index of CR or LF, or -1 if none found
     */
    private static int indexOfLineBreak(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return -1;
    }

    private static final byte[] DATA_PREFIX = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_PREFIX = "event: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ID_PREFIX = "id: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RETRY_PREFIX = "retry: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LF = { '\n' };
    private static final byte[] KEEP_ALIVE_BYTES = ": keep-alive\n\n".getBytes(StandardCharsets.UTF_8);

    /**
     * Writes this event directly to the given output stream, avoiding
     * intermediate String allocation on the hot path.
     *
     * @param out the output stream to write to
     * @throws IOException if writing fails
     */
    public void writeTo(OutputStream out) throws IOException {
        if (this == KEEP_ALIVE) {
            out.write(KEEP_ALIVE_BYTES);
            return;
        }
        if (event != null) {
            out.write(EVENT_PREFIX);
            out.write(event.getBytes(StandardCharsets.UTF_8));
            out.write(LF);
        }
        if (id != null) {
            out.write(ID_PREFIX);
            out.write(id.getBytes(StandardCharsets.UTF_8));
            out.write(LF);
        }
        if (retry != null) {
            out.write(RETRY_PREFIX);
            out.write(Long.toString(retry.toMillis()).getBytes(StandardCharsets.UTF_8));
            out.write(LF);
        }
        if (data != null) {
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            int start = 0;
            int len = dataBytes.length;
            while (start < len) {
                int idx = indexOfLineBreakInBytes(dataBytes, start);
                if (idx < 0) {
                    break;
                }
                out.write(DATA_PREFIX);
                out.write(dataBytes, start, idx - start);
                out.write(LF);
                // Skip CRLF as a single line break
                start = (idx < len - 1 && dataBytes[idx] == '\r' && dataBytes[idx + 1] == '\n')
                        ? idx + 2 : idx + 1;
            }
            out.write(DATA_PREFIX);
            out.write(dataBytes, start, len - start);
            out.write(LF);
        }
        out.write(LF);
    }

    private static int indexOfLineBreakInBytes(byte[] bytes, int from) {
        for (int i = from; i < bytes.length; i++) {
            if (bytes[i] == '\n' || bytes[i] == '\r') {
                return i;
            }
        }
        return -1;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String data;
        private String event;
        private String id;
        private Duration retry;

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        public Builder event(String event) {
            this.event = event;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder retry(Duration retry) {
            this.retry = retry;
            return this;
        }

        public SseEvent build() {
            return new SseEvent(data, event, id, retry);
        }
    }
}
