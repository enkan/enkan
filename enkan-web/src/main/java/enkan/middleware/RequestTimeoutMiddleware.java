package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Headers;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.exception.MisconfigurationException;

import java.time.Duration;
import java.util.concurrent.StructuredTaskScope;

import static enkan.util.BeanBuilder.builder;

/**
 * Middleware that enforces a per-request timeout for downstream processing,
 * using {@link StructuredTaskScope}.
 *
 * <p>The downstream chain runs in a virtual thread forked by
 * {@link StructuredTaskScope}. If the chain does not complete within the
 * configured timeout, the scope is cancelled and this middleware returns
 * a {@code 504 Gateway Timeout} response (configurable via
 * {@link #setTimeoutStatus(int)}).
 *
 * <p>Using {@code StructuredTaskScope} provides two benefits over a plain
 * executor:
 * <ol>
 *   <li><b>{@link java.lang.ScopedValue ScopedValue} inheritance</b> —
 *       bindings active in the calling thread are inherited by the subtask,
 *       consistent with how {@code StructuredTaskScope} is specified.</li>
 *   <li><b>Structured shutdown</b> — {@code scope.close()} always waits for
 *       the subtask thread to finish, so in-flight work cannot outlive the
 *       scope boundary.</li>
 * </ol>
 *
 * <h2>Preview API requirement</h2>
 * <p>{@link StructuredTaskScope} is a preview API in Java 25. This class is
 * compiled with {@code --enable-preview} as part of the enkan-web build.
 * Applications that reference this class must also be <em>run</em> with
 * {@code --enable-preview}; applications that do not reference it are
 * unaffected.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * app.use(new RequestTimeoutMiddleware());
 * }</pre>
 *
 * <h2>Shutdown blocking after timeout</h2>
 * <p>When a timeout fires, {@link StructuredTaskScope#close()} waits for the
 * subtask thread to finish before returning. If the subtask is blocked on
 * non-interruptible I/O (e.g. a JDBC query that ignores interrupt), the caller
 * will be blocked until the subtask completes naturally, even though the 504
 * response has already been prepared. Design subtasks to be interrupt-responsive
 * to avoid this.
 *
 * <h2>Middleware ordering note (Doma2)</h2>
 * <p>Because the downstream chain runs in a separate virtual thread,
 * {@code ThreadLocal}-based context is <em>not</em> inherited. Most Enkan
 * middleware passes state via the request object and is unaffected. The only
 * known exception is Doma2's {@code LocalTransaction}. When using Doma2,
 * place this middleware <em>inside</em> (downstream of) the Doma2 transaction
 * middleware.
 *
 * @author kawasima
 */
@Middleware(name = "requestTimeout")
public class RequestTimeoutMiddleware implements WebMiddleware {

    private long timeoutMillis = 30_000;
    private int  timeoutStatus = 504;

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<HttpResponse>awaitAllSuccessfulOrThrow(),
                cf -> cf.withTimeout(Duration.ofMillis(timeoutMillis)))) {
            var task = scope.fork(() -> castToHttpResponse(chain.next(request)));
            try {
                scope.join();
            } catch (StructuredTaskScope.TimeoutException e) {
                return timeoutResponse();
            }
            return task.get();
        } catch (StructuredTaskScope.FailedException e) {
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
     * @param timeoutStatus the HTTP status code to return on timeout; must be between 100 and 599
     * @throws MisconfigurationException if {@code timeoutStatus} is outside the valid range
     */
    public void setTimeoutStatus(int timeoutStatus) {
        if (timeoutStatus < 100 || timeoutStatus > 599) {
            throw new MisconfigurationException("core.INVALID_ARGUMENT", "timeoutStatus");
        }
        this.timeoutStatus = timeoutStatus;
    }
}
