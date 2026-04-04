package enkan.endpoint.devel;

import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplConsoleEndpointTest {

    private final ReplConsoleEndpoint endpoint = new ReplConsoleEndpoint();

    private DefaultHttpRequest requestFor(String uri) {
        DefaultHttpRequest req = new DefaultHttpRequest();
        req.setUri(uri);
        req.setRequestMethod("GET");
        return req;
    }

    @Test
    void servesIndexHtml() {
        HttpResponse response = endpoint.handle(requestFor("/x-enkan/repl/"));
        assertThat(response).isNotNull();
        assertThat(response.getHeaders().get("Content-Type")).asString().contains("text/html");
    }

    @Test
    void servesJsAsset() {
        HttpResponse response = endpoint.handle(requestFor("/x-enkan/repl/enkan-repl.js"));
        assertThat(response).isNotNull();
        assertThat(response.getHeaders().get("Content-Type")).asString().contains("application/javascript");
    }

    @Test
    void servesCssAsset() {
        HttpResponse response = endpoint.handle(requestFor("/x-enkan/repl/enkan-repl.css"));
        assertThat(response).isNotNull();
        assertThat(response.getHeaders().get("Content-Type")).asString().contains("text/css");
    }

    @Test
    void servesXtermJs() {
        HttpResponse response = endpoint.handle(requestFor("/x-enkan/repl/xterm.js"));
        assertThat(response).isNotNull();
        assertThat(response.getHeaders().get("Content-Type")).asString().contains("application/javascript");
    }

    @Test
    void servesXtermCss() {
        HttpResponse response = endpoint.handle(requestFor("/x-enkan/repl/xterm.css"));
        assertThat(response).isNotNull();
        assertThat(response.getHeaders().get("Content-Type")).asString().contains("text/css");
    }

    @Test
    void servesAddonFitJs() {
        HttpResponse response = endpoint.handle(requestFor("/x-enkan/repl/addon-fit.js"));
        assertThat(response).isNotNull();
        assertThat(response.getHeaders().get("Content-Type")).asString().contains("application/javascript");
    }

    @Test
    void returnsNullForUnknownPath() {
        HttpResponse response = endpoint.handle(requestFor("/x-enkan/repl/nonexistent.txt"));
        assertThat(response).isNull();
    }

    @Test
    void returnsNullForDirectoryTraversal() {
        HttpResponse response = endpoint.handle(requestFor("/x-enkan/repl/../../../etc/passwd"));
        assertThat(response).isNull();
    }

    @Test
    void returnsNullForNonMatchingPath() {
        HttpResponse response = endpoint.handle(requestFor("/other/path"));
        assertThat(response).isNull();
    }
}
