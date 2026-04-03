package enkan.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class FetchMetadataMiddlewareTest {

    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain;

    @BeforeEach
    void setup() {
        chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("ok")).build());
    }

    // --- allowed requests ---

    @Test
    void noFetchSiteHeaderIsAllowed() {
        // Non-browser clients (curl, mobile SDKs) do not send Sec-Fetch-Site; they must pass through.
        HttpRequest request = new DefaultHttpRequest();

        HttpResponse response = new FetchMetadataMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void sameSiteRequestIsAllowed() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("sec-fetch-site", "same-site"))
                .build();

        HttpResponse response = new FetchMetadataMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void sameOriginRequestIsAllowed() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("sec-fetch-site", "same-origin"))
                .build();

        HttpResponse response = new FetchMetadataMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void noneRequestIsAllowed() {
        // "none" means the request was user-initiated (typed URL, bookmark) — always safe.
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("sec-fetch-site", "none"))
                .build();

        HttpResponse response = new FetchMetadataMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void crossSiteNavigateGetIsAllowed() {
        // User clicked a cross-origin link — this is legitimate top-level navigation.
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "sec-fetch-site", "cross-site",
                        "sec-fetch-mode", "navigate"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build();

        HttpResponse response = new FetchMetadataMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowedPathPermitsCrossOrigin() {
        FetchMetadataMiddleware middleware = new FetchMetadataMiddleware();
        middleware.setAllowedPaths(Set.of("/api/public/feed"));

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "sec-fetch-site", "cross-site",
                        "sec-fetch-mode", "cors"))
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setUri, "/api/public/feed")
                .build();

        HttpResponse response = middleware.handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // --- rejected requests ---

    @Test
    void crossSiteNavigatePostIsRejected() {
        // Cross-origin POST navigation can be used for CSRF — must be rejected.
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "sec-fetch-site", "cross-site",
                        "sec-fetch-mode", "navigate"))
                .set(HttpRequest::setRequestMethod, "POST")
                .build();

        HttpResponse response = new FetchMetadataMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void crossSiteNoCorsIsRejected() {
        // Cross-site no-cors (e.g. <script src="..."> or <img src="...">) — reject.
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "sec-fetch-site", "cross-site",
                        "sec-fetch-mode", "no-cors"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build();

        HttpResponse response = new FetchMetadataMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void crossSiteCorsIsRejected() {
        // Cross-site fetch() with mode:cors — rejected unless the path is in the allow-list.
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "sec-fetch-site", "cross-site",
                        "sec-fetch-mode", "cors"))
                .set(HttpRequest::setRequestMethod, "POST")
                .build();

        HttpResponse response = new FetchMetadataMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowedPathDoesNotMatchOtherPath() {
        FetchMetadataMiddleware middleware = new FetchMetadataMiddleware();
        middleware.setAllowedPaths(Set.of("/api/public/feed"));

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "sec-fetch-site", "cross-site",
                        "sec-fetch-mode", "cors"))
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setUri, "/api/private/data")
                .build();

        HttpResponse response = middleware.handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    // --- response characteristics ---

    @Test
    void rejectedResponseHasStatus403AndContentType() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "sec-fetch-site", "cross-site",
                        "sec-fetch-mode", "cors"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build();

        HttpResponse response = new FetchMetadataMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat((String) response.getHeaders().get("content-type")).isEqualTo("text/plain");
    }

    @Test
    void chainIsNotCalledWhenRejected() {
        AtomicBoolean endpointCalled = new AtomicBoolean(false);
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> trackingChain =
                new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                        (Endpoint<HttpRequest, HttpResponse>) req -> {
                            endpointCalled.set(true);
                            return builder(HttpResponse.of("ok")).build();
                        });

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "sec-fetch-site", "cross-site",
                        "sec-fetch-mode", "no-cors"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build();

        new FetchMetadataMiddleware().handle(request, trackingChain);

        assertThat(endpointCalled.get()).isFalse();
    }
}
