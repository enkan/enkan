package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Headers;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.exception.MisconfigurationException;

import java.io.Closeable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static enkan.util.BeanBuilder.builder;

/**
 * Middleware that enforces a per-request timeout for downstream processing.
 *
 * <p>Each request is executed in a virtual thread. If the downstream middleware
 * chain does not complete within the configured timeout, this middleware cancels
 * the task and returns a {@code 504 Gateway Timeout} response (or any status
 * code configured via {@link #setTimeoutStatus(int)}).
 *
 * <p>This is equivalent to <a href="https://hono.dev/docs/middleware/builtin/timeout">
 * Hono's {@code timeout()} middleware</a>, using Java virtual threads for
 * efficient concurrency.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * app.use(new RequestTimeoutMiddleware());
 * }</pre>
 *
 * <h2>Custom timeout</h2>
 * <pre>{@code
 * var m = new RequestTimeoutMiddleware();
 * m.setTimeoutMillis(5_000); // 5 seconds
 * app.use(m);
 * }</pre>
 *
 * <h2>Middleware ordering note (Doma2)</h2>
 * <p>Because the downstream chain runs in a separate virtual thread, any
 * {@code ThreadLocal}-based context is <em>not</em> inherited by that thread.
 * Most Enkan middleware passes state via the request object and is unaffected.
 * The only known exception is Doma2's {@code LocalTransaction}, which stores
 * its context in a {@code ThreadLocal}. When using Doma2, place this middleware
 * <em>inside</em> (downstream of) the Doma2 transaction middleware so that both
 * run on the same thread.
 *
 * <h2>Streaming responses</h2>
 * <p>Streaming response bodies ({@code StreamingBody}) written after the
 * timeout deadline are undefined. Do not use this middleware with streaming
 * handlers that rely on post-response writes.
 *
 * @author kawasima
 */
@Middleware(name = "requestTimeout")
public class RequestTimeoutMiddleware implements WebMiddleware, Closeable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private long timeoutMillis = 30_000;
    private int  timeoutStatus = 504;

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        Future<HttpResponse> future = executor.submit(
                () -> castToHttpResponse(chain.next(request)));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return timeoutResponse();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return timeoutResponse();
        }
    }

    private HttpResponse timeoutResponse() {
        return builder(HttpResponse.of("Gateway Timeout"))
                .set(HttpResponse::setStatus, timeoutStatus)
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                .build();
    }

    /**
     * Sets the request timeout in milliseconds. Defaults to {@code 30000} (30 seconds).
     *
     * @param timeoutMillis timeout in milliseconds; must be positive
     * @throws MisconfigurationException if {@code timeoutMillis} is not positive
     */
    public void setTimeoutMillis(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new MisconfigurationException("core.INVALID_ARGUMENT", "timeoutMillis");
        }
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Sets the HTTP status code returned when a request times out. Defaults to {@code 504}.
     *
     * @param timeoutStatus the HTTP status code to return on timeout
     */
    public void setTimeoutStatus(int timeoutStatus) {
        this.timeoutStatus = timeoutStatus;
    }

    /**
     * Shuts down the internal virtual thread executor.
     * Call this when the middleware is no longer needed (e.g., on application shutdown)
     * to allow in-flight virtual threads to complete and release resources.
     */
    @Override
    public void close() {
        executor.shutdown();
    }
}
