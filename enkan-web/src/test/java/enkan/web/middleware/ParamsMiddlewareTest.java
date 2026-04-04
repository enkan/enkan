package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class ParamsMiddlewareTest {

    private HttpResponse handle(HttpRequest request) {
        ParamsMiddleware middleware = new ParamsMiddleware();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                HttpResponse.of("ok"));
        return middleware.handle(request, chain);
    }

    @Test
    void queryStringIsParsedIntoParams() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("host", "example.com"))
                .set(HttpRequest::setQueryString, "a=1&b=2")
                .build();

        handle(request);

        assertThat(request.getQueryParams()).containsEntry("a", "1");
        assertThat(request.getQueryParams()).containsEntry("b", "2");
        assertThat(request.getParams()).containsEntry("a", "1");
        assertThat(request.getParams()).containsEntry("b", "2");
    }

    @Test
    void nullQueryStringResultsInEmptyParams() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("host", "example.com"))
                .build();

        handle(request);

        assertThat(request.getQueryParams()).isNotNull();
        assertThat(request.getQueryParams()).isEmpty();
        assertThat(request.getParams()).isNotNull();
    }

    @Test
    void formBodyIsParsedForUrlEncodedContentType() {
        byte[] body = "name=alice&age=30".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("host", "example.com",
                        "content-type", "application/x-www-form-urlencoded"))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body))
                .build();

        handle(request);

        assertThat(request.getFormParams()).containsEntry("name", "alice");
        assertThat(request.getFormParams()).containsEntry("age", "30");
        assertThat(request.getParams()).containsEntry("name", "alice");
    }

    @Test
    void nonFormContentTypeSkipsBodyParsing() {
        byte[] body = "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("host", "example.com",
                        "content-type", "application/json"))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body))
                .build();

        handle(request);

        assertThat(request.getFormParams()).isNotNull();
        assertThat(request.getFormParams()).isEmpty();
    }

    @Test
    void nullBodySkipsBodyParsing() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("host", "example.com",
                        "content-type", "application/x-www-form-urlencoded"))
                .build();

        handle(request);

        assertThat(request.getFormParams()).isNotNull();
        assertThat(request.getFormParams()).isEmpty();
    }

    @Test
    void alreadyPopulatedParamsAreNotOverwritten() {
        Parameters existingParams = Parameters.of("existing", "value");
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("host", "example.com"))
                .set(HttpRequest::setQueryString, "a=1")
                .set(HttpRequest::setParams, existingParams)
                .build();

        handle(request);

        assertThat(request.getParams()).containsEntry("existing", "value");
        assertThat(request.getParams()).containsEntry("a", "1");
    }
}
