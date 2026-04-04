package enkan.middleware.devel;

import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.exception.UnreachableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StacktraceMiddlewareTest {

    private final StacktraceMiddleware middleware = new StacktraceMiddleware();

    private DefaultHttpRequest requestWithAccept(String accept) {
        DefaultHttpRequest req = new DefaultHttpRequest();
        req.setHeaders(accept != null
                ? Headers.of("Accept", accept)
                : Headers.empty());
        return req;
    }

    // ---------------------------------------------------------- normal request

    @Test
    void normalResponseIsPassedThrough() {
        DefaultHttpRequest request = requestWithAccept(null);
        HttpResponse response = middleware.handle(request,
                new TestMiddlewareChain(req -> HttpResponse.of("ok")));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBodyAsString()).isEqualTo("ok");
    }

    // -------------------------------------------------------- generic exception

    @Test
    void htmlAcceptRendersStacktraceHtml() {
        DefaultHttpRequest request = requestWithAccept("text/html");
        HttpResponse response = middleware.handle(request,
                new TestMiddlewareChain(req -> { throw new RuntimeException("boom"); }));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaders().get("Content-Type")).contains("text/html");
        assertThat(response.getBodyAsString()).contains("RuntimeException");
    }

    @Test
    void noAcceptHeaderRendersStacktraceHtml() {
        DefaultHttpRequest request = requestWithAccept(null);
        HttpResponse response = middleware.handle(request,
                new TestMiddlewareChain(req -> { throw new RuntimeException("boom"); }));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaders().get("Content-Type")).contains("text/html");
    }

    // ----------------------------------------------- javascript accept header

    @Test
    void javascriptAcceptRendersPlainText() {
        DefaultHttpRequest request = requestWithAccept("text/javascript");
        HttpResponse response = middleware.handle(request,
                new TestMiddlewareChain(req -> { throw new RuntimeException("boom"); }));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaders().get("Content-Type")).isEqualTo("text/javascript");
        assertThat(response.getBodyAsString()).contains("RuntimeException");
    }

    @Test
    void javascriptAcceptHeaderWithParametersIsHandled() {
        DefaultHttpRequest request = requestWithAccept("text/javascript, */*;q=0.8");
        HttpResponse response = middleware.handle(request,
                new TestMiddlewareChain(req -> { throw new RuntimeException("boom"); }));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaders().get("Content-Type")).isEqualTo("text/javascript");
        assertThat(response.getBodyAsString()).contains("RuntimeException");
    }

    // ----------------------------------------- MisconfigurationException

    @Test
    void misconfigurationExceptionRendersConfigPage() {
        DefaultHttpRequest request = requestWithAccept("text/html");
        HttpResponse response = middleware.handle(request,
                new TestMiddlewareChain(req -> {
                    throw new MisconfigurationException("jpa.NOT_ENTITY_MANAGEABLE_REQUEST");
                }));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaders().get("Content-Type")).contains("text/html");
    }

    // ----------------------------------------- UnreachableException

    @Test
    void unreachableExceptionRendersUnreachablePage() {
        DefaultHttpRequest request = requestWithAccept("text/html");
        HttpResponse response = middleware.handle(request,
                new TestMiddlewareChain(req -> { throw new UnreachableException(); }));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaders().get("Content-Type")).contains("text/html");
    }
}
