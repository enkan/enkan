package enkan.adapter;

import enkan.web.application.WebApplication;
import enkan.web.collection.Headers;
import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.data.StreamingBody;
import enkan.exception.ServiceUnavailableException;
import enkan.exception.UnreachableException;
import enkan.web.middleware.WebMiddleware;
import enkan.util.Predicates;
import enkan.adapter.UndertowAdapter.UndertowServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UndertowAdapterTest {

    private UndertowServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.undertow().stop();
            server = null;
        }
    }

    private int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** エンドポイントとなる WebMiddleware を1つ登録した WebApplication を生成する */
    private WebApplication appWith(WebMiddleware endpoint) {
        WebApplication app = new WebApplication();
        app.use(Predicates.any(), "endpoint", endpoint);
        return app;
    }

    private UndertowServer start(WebMiddleware endpoint, int port) {
        OptionMap options = OptionMap.of("http?", true, "port", port, "host", "127.0.0.1");
        server = new UndertowAdapter().runUndertow(appWith(endpoint), options);
        return server;
    }

    private HttpURLConnection connect(int port, String path) throws IOException {
        URI uri = URI.create("http://127.0.0.1:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        return conn;
    }

    // ------------------------------------------------------------------ body

    @Test
    void stringBodyIsReturned() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of("hello");
                res.setHeaders(Headers.empty());
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("hello");
    }

    @Test
    void inputStreamBodyIsReturned() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                byte[] bytes = "stream-body".getBytes(StandardCharsets.UTF_8);
                HttpResponse res = HttpResponse.of(new ByteArrayInputStream(bytes));
                res.setHeaders(Headers.empty());
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("stream-body");
    }

    @Test
    void fileBodyIsReturned() throws Exception {
        File tmp = File.createTempFile("undertow-test-", ".txt");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), "file-body");

        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of(tmp);
                res.setHeaders(Headers.empty());
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("file-body");
    }

    @Test
    void nullBodyReturnsEmptyResponse() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of((String) null);
                res.setHeaders(Headers.empty());
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getInputStream().readAllBytes()).isEmpty();
    }

    @Test
    void unsupportedBodyTypeThrowsUnreachableException() throws Exception {
        UndertowAdapter adapter = new UndertowAdapter();
        java.lang.reflect.Method m = UndertowAdapter.class.getDeclaredMethod("setBody",
                io.undertow.server.HttpServerExchange.class, Object.class);
        m.setAccessible(true);
        assertThatThrownBy(() -> {
            try {
                m.invoke(adapter, null, 12345);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(UnreachableException.class);
    }

    @Test
    void streamingBodyIsStreamed() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                StreamingBody body = out -> out.write("streamed-content".getBytes(StandardCharsets.UTF_8));
                HttpResponse res = HttpResponse.of(body);
                res.setHeaders(Headers.of("Content-Type", "text/event-stream"));
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getHeaderField("Content-Type")).isEqualTo("text/event-stream");
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("streamed-content");
    }

    // ---------------------------------------------------------------- status

    @Test
    void statusCodeIsPreserved() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of("not found");
                res.setStatus(404);
                res.setHeaders(Headers.empty());
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        conn.setInstanceFollowRedirects(false);
        assertThat(conn.getResponseCode()).isEqualTo(404);
    }

    // -------------------------------------------------------------- headers

    @Test
    void responseHeaderIsForwarded() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of("ok");
                res.setHeaders(Headers.of("X-Custom-Header", "test-value"));
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getHeaderField("X-Custom-Header")).isEqualTo("test-value");
    }

    @Test
    void numericResponseHeaderIsForwarded() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of("ok");
                Headers headers = Headers.empty();
                headers.put("Content-Length", 2L);
                res.setHeaders(headers);
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getContentLength()).isEqualTo(2);
    }

    // ----------------------------------------------------------- exceptions

    @Test
    void serviceUnavailableExceptionReturns503() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                throw new ServiceUnavailableException();
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(503);
    }

    // ------------------------------------------------------- request mapping

    @Test
    void requestMethodIsMapped() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of(req.getRequestMethod());
                res.setHeaders(Headers.empty());
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        conn.setRequestMethod("POST");
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("POST");
    }

    @Test
    void requestUriIsMapped() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of(req.getUri());
                res.setHeaders(Headers.empty());
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/foo/bar");
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("/foo/bar");
    }

    @Test
    void requestHeaderIsMapped() throws Exception {
        int port = findFreePort();
        start(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                String value = (String) req.getHeaders().getRawType("X-Test");
                HttpResponse res = HttpResponse.of(value != null ? value : "");
                res.setHeaders(Headers.empty());
                return res;
            }
        }, port);

        HttpURLConnection conn = connect(port, "/");
        conn.setRequestProperty("X-Test", "mapped");
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("mapped");
    }
}
