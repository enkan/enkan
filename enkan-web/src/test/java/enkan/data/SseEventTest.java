package enkan.data;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseEventTest {

    @Test
    void dataOnlyEvent() {
        SseEvent event = SseEvent.of("hello");
        assertThat(event.toWireFormat()).isEqualTo("data: hello\n\n");
    }

    @Test
    void eventWithAllFields() {
        SseEvent event = SseEvent.builder()
                .event("update")
                .data("payload")
                .id("42")
                .retry(Duration.ofSeconds(5))
                .build();
        assertThat(event.toWireFormat()).isEqualTo(
                "event: update\nid: 42\nretry: 5000\ndata: payload\n\n");
    }

    @Test
    void multiLineData() {
        SseEvent event = SseEvent.of("line1\nline2\nline3");
        assertThat(event.toWireFormat()).isEqualTo(
                "data: line1\ndata: line2\ndata: line3\n\n");
    }

    @Test
    void keepAliveComment() {
        assertThat(SseEvent.keepAlive().toWireFormat()).isEqualTo(": keep-alive\n\n");
    }

    @Test
    void eventTypeOnly() {
        SseEvent event = SseEvent.builder().event("ping").build();
        assertThat(event.toWireFormat()).isEqualTo("event: ping\n\n");
    }

    // --- line break handling in data ---

    @Test
    void dataWithCarriageReturn() {
        SseEvent event = SseEvent.of("line1\rline2");
        assertThat(event.toWireFormat()).isEqualTo(
                "data: line1\ndata: line2\n\n");
    }

    @Test
    void dataWithCrLf() {
        SseEvent event = SseEvent.of("line1\r\nline2\r\nline3");
        assertThat(event.toWireFormat()).isEqualTo(
                "data: line1\ndata: line2\ndata: line3\n\n");
    }

    @Test
    void dataWithMixedLineBreaks() {
        SseEvent event = SseEvent.of("a\nb\rc\r\nd");
        assertThat(event.toWireFormat()).isEqualTo(
                "data: a\ndata: b\ndata: c\ndata: d\n\n");
    }

    @Test
    void writeToHandlesCrLfSameAsToWireFormat() throws Exception {
        SseEvent event = SseEvent.of("line1\r\nline2\rline3\nline4");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        event.writeTo(out);
        assertThat(out.toString(StandardCharsets.UTF_8))
                .isEqualTo(event.toWireFormat());
    }

    // --- validation tests ---

    @Test
    void eventFieldRejectsNewline() {
        assertThatThrownBy(() -> SseEvent.builder().event("bad\nevent").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line breaks");
    }

    @Test
    void eventFieldRejectsCarriageReturn() {
        assertThatThrownBy(() -> SseEvent.builder().event("bad\revent").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line breaks");
    }

    @Test
    void idFieldRejectsNewline() {
        assertThatThrownBy(() -> SseEvent.builder().id("bad\nid").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line breaks");
    }

    @Test
    void idFieldRejectsNulCharacter() {
        assertThatThrownBy(() -> SseEvent.builder().id("bad\0id").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("U+0000");
    }

    @Test
    void retryRejectsNegativeDuration() {
        assertThatThrownBy(() -> SseEvent.builder().retry(Duration.ofSeconds(-1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    // --- writeTo (direct OutputStream) tests ---

    @Test
    void writeToProducesSameOutputAsToWireFormat() throws Exception {
        SseEvent event = SseEvent.builder()
                .event("update")
                .data("line1\nline2")
                .id("42")
                .retry(Duration.ofSeconds(3))
                .build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        event.writeTo(out);
        assertThat(out.toString(StandardCharsets.UTF_8))
                .isEqualTo(event.toWireFormat());
    }

    @Test
    void writeToKeepAlive() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseEvent.keepAlive().writeTo(out);
        assertThat(out.toString(StandardCharsets.UTF_8))
                .isEqualTo(": keep-alive\n\n");
    }
}
