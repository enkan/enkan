package enkan.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.data.*;
import enkan.util.Predicates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class IdleSessionTimeoutMiddlewareTest {

    private static final String SESSION_KEY = IdleSessionTimeoutMiddleware.class.getName() + "/idleTimeout";

    private IdleSessionTimeoutMiddleware middleware;

    @BeforeEach
    void setup() {
        middleware = new IdleSessionTimeoutMiddleware();
        middleware.setTimeout(600);
    }

    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> echoChain() {
        return new DefaultMiddlewareChain<>(Predicates.any(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("ok"))
                                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                                .set(HttpResponse::setSession, new Session())
                                .build());
    }

    @Test
    void recentSessionIsPreserved() {
        Session session = new Session();
        long futureTime = System.currentTimeMillis() / 1000 + 3600;
        session.put(SESSION_KEY, Long.toString(futureTime));

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setSession, session)
                .build();

        HttpResponse response = middleware.handle(request, echoChain());
        assertThat(response.getBody()).isEqualTo("ok");
        // Session should still have the timeout key (updated)
        assertThat(response.getSession()).isNotNull();
        assertThat(response.getSession().get(SESSION_KEY)).isNotNull();
    }

    @Test
    void expiredSessionIsInvalidated() {
        Session session = new Session();
        long pastTime = System.currentTimeMillis() / 1000 - 100;
        session.put(SESSION_KEY, Long.toString(pastTime));

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setSession, session)
                .build();

        HttpResponse response = middleware.handle(request, echoChain());
        // Expired session results in redirect with null session
        assertThat(response.getSession()).isNull();
        assertThat(response.getStatus()).isEqualTo(307);
    }

    @Test
    void newSessionGetsTimestampSet() {
        Session session = new Session();

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setSession, session)
                .build();

        HttpResponse response = middleware.handle(request, echoChain());
        assertThat(response.getSession()).isNotNull();
        assertThat(response.getSession().get(SESSION_KEY)).isNotNull();
    }

    @Test
    void sessionWithoutTimestampGetsOneAdded() {
        Session session = new Session();
        session.put("user", "kawasima");

        // Use a chain that preserves the request session on the response
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(Predicates.any(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("ok"))
                                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                                .set(HttpResponse::setSession, req.getSession())
                                .build());

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setSession, session)
                .build();

        HttpResponse response = middleware.handle(request, chain);
        assertThat(response.getSession()).isNotNull();
        assertThat(response.getSession().get(SESSION_KEY)).isNotNull();
        // Existing session data should be preserved
        assertThat(response.getSession().get("user")).isEqualTo("kawasima");
    }
}
