package enkan.data;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link StreamingBody} implementation for Server-Sent Events.
 *
 * <p>The emitter acts as a bridge between the producer (application code that
 * calls {@link #send}) and the consumer ({@link #writeTo} running on the
 * virtual thread that holds the HTTP connection open).</p>
 *
 * <p>Usage with {@link enkan.component.JobExecutor}:</p>
 * <pre>{@code
 * SseEmitter emitter = new SseEmitter();
 * jobExecutor.submit(() -> {
 *     emitter.send(SseEvent.of("hello"));
 *     emitter.send(SseEvent.builder().event("update").data(json).id("1").build());
 *     emitter.complete();
 * });
 * HttpResponse response = HttpResponse.of(emitter);
 * return response;
 * }</pre>
 *
 * <p>The {@code Content-Type} header should be set to {@code text/event-stream}
 * by the caller or a middleware.</p>
 *
 * @author kawasima
 */
public class SseEmitter implements StreamingBody {

    /**
     * Sentinel object used only for reference-identity comparison.
     * Not a valid SseEvent — never exposed to users.
     */
    private static final SseEvent COMPLETION_SENTINEL = new SseEvent(null, null, null, null);

    private final BlockingQueue<SseEvent> queue;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    /**
     * Creates an emitter with an unbounded queue.
     *
     * <p><strong>Warning:</strong> an unbounded queue means a fast producer
     * can fill memory if the consumer (network write) is slow.  Prefer
     * {@link #SseEmitter(int)} with a bounded capacity for production use
     * to get natural back-pressure.</p>
     */
    public SseEmitter() {
        this.queue = new LinkedBlockingQueue<>();
    }

    /**
     * Creates an emitter with a bounded queue.
     * When the queue is full, {@link #send} blocks, providing natural
     * back-pressure for slow clients.
     *
     * @param capacity the maximum number of buffered events
     */
    public SseEmitter(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * Enqueues an event for delivery.  Blocks if the queue is bounded and full.
     *
     * @param event the event to send (must not be null)
     * @throws NullPointerException if event is null
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws IllegalStateException if {@link #complete()} has already been called
     */
    public void send(SseEvent event) throws InterruptedException {
        if (event == null) {
            throw new NullPointerException("event must not be null");
        }
        if (completed.get()) {
            throw new IllegalStateException("SseEmitter has already been completed");
        }
        queue.put(event);
    }

    /**
     * Signals that no more events will be sent.
     * The {@link #writeTo} loop will finish writing any queued events and then return.
     * This method blocks if the queue is bounded and full.
     * Calling this method more than once has no effect.
     */
    public void complete() {
        if (completed.compareAndSet(false, true)) {
            try {
                queue.put(COMPLETION_SENTINEL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Dequeue loop that writes SSE-formatted frames to the output stream.
     * This method blocks (on a virtual thread) until {@link #complete()} is
     * called or an {@link IOException} occurs (typically client disconnect).
     *
     * <p><strong>Important:</strong> if the producer never calls {@link #complete()},
     * this method will block indefinitely on the virtual thread.  The thread is
     * released when the executor is shut down (via {@code shutdownNow()}, which
     * triggers {@link InterruptedException}).  Callers should ensure that producer
     * code always calls {@link #complete()} in a {@code finally} block.</p>
     *
     * <p>When an {@code IOException} occurs (e.g. client disconnected), this
     * method sets the completion flag and drains the queue so that any producer
     * blocked on {@link #send} is unblocked rather than leaked.</p>
     */
    @Override
    public void writeTo(OutputStream out) throws IOException {
        try {
            while (true) {
                SseEvent event = queue.take();
                if (event == COMPLETION_SENTINEL) {
                    break;
                }
                event.writeTo(out);
                out.flush();
            }
        } catch (InterruptedException e) {
            // Shutdown via shutdownNow() — mark as completed and drain the queue
            // so that any producer blocked on send() is unblocked.
            completed.set(true);
            queue.clear();
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // Client disconnected — mark as completed and drain the queue
            // so that any producer blocked on send() is unblocked.
            completed.set(true);
            queue.clear();
            throw e;
        }
    }
}
