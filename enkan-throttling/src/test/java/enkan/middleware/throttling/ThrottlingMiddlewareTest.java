package enkan.middleware.throttling;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import enkan.throttling.LimitRate;
import enkan.throttling.Throttle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

public class ThrottlingMiddlewareTest {
    private static final MiddlewareChain<HttpRequest, HttpResponse, ?, ?> NEXT =
            new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                    (Endpoint<HttpRequest, HttpResponse>) request -> HttpResponse.of("ok"));

    private ThrottlingMiddleware middleware(List<Throttle> throttles) {
        return builder(new ThrottlingMiddleware())
                .set(ThrottlingMiddleware::setThrottles, throttles)
                .build();
    }

    private DefaultHttpRequest requestFrom(String ip) {
        DefaultHttpRequest req = new DefaultHttpRequest();
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void firstRequestIsAllowed() {
        ThrottlingMiddleware middleware = middleware(List.of(
                new Throttle("IP", new LimitRate(3, Duration.ofMinutes(1)), HttpRequest::getRemoteAddr)
        ));

        HttpResponse res = middleware.handle(requestFrom("10.0.0.1"), NEXT);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void requestsWithinLimitAreAllowed() {
        ThrottlingMiddleware middleware = middleware(List.of(
                new Throttle("IP", new LimitRate(3, Duration.ofMinutes(1)), HttpRequest::getRemoteAddr)
        ));
        DefaultHttpRequest req = requestFrom("10.0.0.1");

        // capacity=3: first 3 requests should pass
        for (int i = 0; i < 3; i++) {
            assertThat(middleware.handle(req, NEXT).getStatus())
                    .as("request %d should be 200", i + 1)
                    .isEqualTo(200);
        }
    }

    @Test
    void requestsExceedingLimitAreThrottled() {
        ThrottlingMiddleware middleware = middleware(List.of(
                new Throttle("IP", new LimitRate(3, Duration.ofMinutes(1)), HttpRequest::getRemoteAddr)
        ));
        DefaultHttpRequest req = requestFrom("10.0.0.1");

        // Exhaust the bucket
        for (int i = 0; i < 3; i++) {
            middleware.handle(req, NEXT);
        }

        // 4th request must be throttled
        HttpResponse res = middleware.handle(req, NEXT);
        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void differentClientsHaveIndependentBuckets() {
        ThrottlingMiddleware middleware = middleware(List.of(
                new Throttle("IP", new LimitRate(1, Duration.ofMinutes(1)), HttpRequest::getRemoteAddr)
        ));

        // Exhaust bucket for client A
        middleware.handle(requestFrom("10.0.0.1"), NEXT);

        // Client B should still be allowed
        HttpResponse res = middleware.handle(requestFrom("10.0.0.2"), NEXT);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void nullDiscriminatorIsAlwaysAllowed() {
        ThrottlingMiddleware middleware = middleware(List.of(
                // discriminator returns null when remoteAddr is null
                new Throttle("IP", new LimitRate(1, Duration.ofMinutes(1)), HttpRequest::getRemoteAddr)
        ));

        DefaultHttpRequest req = new DefaultHttpRequest(); // remoteAddr = null

        for (int i = 0; i < 5; i++) {
            assertThat(middleware.handle(req, NEXT).getStatus())
                    .as("null discriminator should always pass through")
                    .isEqualTo(200);
        }
    }

    @Test
    void noThrottlesAlwaysPassThrough() {
        ThrottlingMiddleware middleware = middleware(List.of());

        for (int i = 0; i < 10; i++) {
            assertThat(middleware.handle(requestFrom("10.0.0.1"), NEXT).getStatus())
                    .isEqualTo(200);
        }
    }
}
