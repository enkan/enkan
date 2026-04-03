package enkan.data;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseEmitterTest {

    @Test
    void writesToOutputStreamAndCompletes() throws Exception {
        SseEmitter emitter = new SseEmitter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Thread writer = Thread.ofVirtual().start(() -> {
            try {
                emitter.writeTo(out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        emitter.send(SseEvent.of("hello"));
        emitter.send(SseEvent.builder().event("update").data("world").id("1").build());
        emitter.complete();

        writer.join(5_000);

        String result = out.toString();
        assertThat(result).contains("data: hello\n\n");
        assertThat(result).contains("event: update\nid: 1\ndata: world\n\n");
    }

    @Test
    void boundedQueueProvideBackpressure() throws Exception {
        // With capacity=2 we can enqueue exactly 2 events before blocking.
        // We send 3 events from the producer, then start the consumer.
        // All 3 events must eventually be delivered, proving the producer
        // was blocked and then released when the consumer drained the queue.
        SseEmitter emitter = new SseEmitter(2);
        CountDownLatch allSent = new CountDownLatch(1);

        Thread producer = Thread.ofVirtual().start(() -> {
            try {
                emitter.send(SseEvent.of("first"));
                emitter.send(SseEvent.of("second"));
                // This third send blocks until the consumer drains
                emitter.send(SseEvent.of("third"));
                allSent.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Start consumer — this unblocks the producer's third send
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread consumer = Thread.ofVirtual().start(() -> {
            try {
                emitter.writeTo(out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(allSent.await(5, TimeUnit.SECONDS))
                .as("Producer should eventually finish sending all events")
                .isTrue();
        emitter.complete();
        consumer.join(5_000);
        producer.join(5_000);

        String result = out.toString();
        assertThat(result).contains("data: first");
        assertThat(result).contains("data: second");
        assertThat(result).contains("data: third");
    }

    @Test
    void sendAfterCompleteThrows() throws Exception {
        SseEmitter emitter = new SseEmitter();
        emitter.complete();
        assertThatThrownBy(() -> emitter.send(SseEvent.of("late")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ioExceptionUnblocksProducer() throws Exception {
        SseEmitter emitter = new SseEmitter(1);
        CountDownLatch producerDone = new CountDownLatch(1);

        // Simulate an OutputStream that throws IOException on second write
        OutputStream failingOut = new OutputStream() {
            private int writeCount = 0;
            @Override
            public void write(int b) throws IOException {
                // count calls to flush (used as a proxy for event writes)
            }
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                writeCount++;
                if (writeCount > 5) { // let a few writes through, then fail
                    throw new IOException("simulated client disconnect");
                }
            }
            @Override
            public void flush() throws IOException {
                if (writeCount > 5) {
                    throw new IOException("simulated client disconnect");
                }
            }
        };

        // Start consumer
        Thread consumer = Thread.ofVirtual().start(() -> {
            try {
                emitter.writeTo(failingOut);
            } catch (IOException e) {
                // Expected — client disconnect
            }
        });

        // Producer sends events; after IOException the producer should be unblocked
        Thread producer = Thread.ofVirtual().start(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    emitter.send(SseEvent.of("event-" + i));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IllegalStateException e) {
                // Expected after consumer sets completed flag
            } finally {
                producerDone.countDown();
            }
        });

        // Producer must finish within a reasonable time (not hang forever)
        assertThat(producerDone.await(5, TimeUnit.SECONDS))
                .as("Producer should be unblocked after IOException")
                .isTrue();
        consumer.join(5_000);
        producer.join(5_000);
    }

    @Test
    void sendNullThrowsNpe() {
        SseEmitter emitter = new SseEmitter();
        assertThatThrownBy(() -> emitter.send(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void doubleCompleteIsIdempotent() throws Exception {
        SseEmitter emitter = new SseEmitter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Thread writer = Thread.ofVirtual().start(() -> {
            try {
                emitter.writeTo(out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        emitter.send(SseEvent.of("hello"));
        emitter.complete();
        emitter.complete(); // should not enqueue a second sentinel

        writer.join(5_000);
        assertThat(out.toString()).isEqualTo("data: hello\n\n");
    }
}
