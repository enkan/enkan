package enkan.component.jetty;

import enkan.web.application.WebApplication;
import enkan.web.collection.Headers;
import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.web.middleware.WebMiddleware;
import enkan.util.Predicates;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.eclipse.jetty.util.thread.VirtualThreadPool;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JettyAdapterTest {

    private Server server;
    private int port;
    private final AtomicReference<WebMiddleware> handler = new AtomicReference<>();

    @BeforeAll
    void startServer() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        WebApplication app = new WebApplication();
        app.use(Predicates.any(), "dispatch", new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                return handler.get().handle(req, chain);
            }
        });
        OptionMap options = OptionMap.of("http?", true, "port", port, "host", "127.0.0.1", "join?", false);
        server = new JettyAdapter().runJetty(app, options);
    }

    @AfterAll
    void stopServer() throws Exception {
        if (server != null) {
            server.stop();
            server.join();
        }
    }

    private HttpURLConnection connect(String path) throws IOException {
        URI uri = URI.create("http://127.0.0.1:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        return conn;
    }

    private WebApplication appWith(WebMiddleware endpoint) {
        WebApplication app = new WebApplication();
        app.use(Predicates.any(), "endpoint", endpoint);
        return app;
    }

    // ------------------------------------------------------------------ body

    @Test
    void stringBodyIsReturned() throws Exception {
        handler.set(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of("hello");
                res.setHeaders(Headers.empty());
                return res;
            }
        });

        HttpURLConnection conn = connect("/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("hello");
    }

    @Test
    void inputStreamBodyIsReturned() throws Exception {
        handler.set(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                byte[] bytes = "stream-body".getBytes(StandardCharsets.UTF_8);
                HttpResponse res = HttpResponse.of(new ByteArrayInputStream(bytes));
                res.setHeaders(Headers.empty());
                return res;
            }
        });

        HttpURLConnection conn = connect("/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("stream-body");
    }

    @Test
    void fileBodyIsReturned() throws Exception {
        File tmp = File.createTempFile("jetty-test-", ".txt");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), "file-body");

        handler.set(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of(tmp);
                res.setHeaders(Headers.empty());
                return res;
            }
        });

        HttpURLConnection conn = connect("/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("file-body");
    }

    // ---------------------------------------------------------------- status

    @Test
    void statusCodeIsPreserved() throws Exception {
        handler.set(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of("not found");
                res.setStatus(404);
                res.setHeaders(Headers.empty());
                return res;
            }
        });

        HttpURLConnection conn = connect("/");
        conn.setInstanceFollowRedirects(false);
        assertThat(conn.getResponseCode()).isEqualTo(404);
    }

    // -------------------------------------------------------------- headers

    @Test
    void responseHeaderIsForwarded() throws Exception {
        handler.set(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of("ok");
                res.setHeaders(Headers.of("X-Custom-Header", "test-value"));
                return res;
            }
        });

        HttpURLConnection conn = connect("/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getHeaderField("X-Custom-Header")).isEqualTo("test-value");
    }

    // ------------------------------------------------------- request mapping

    @Test
    void requestMethodIsMapped() throws Exception {
        handler.set(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of(req.getRequestMethod());
                res.setHeaders(Headers.empty());
                return res;
            }
        });

        HttpURLConnection conn = connect("/");
        conn.setRequestMethod("POST");
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("POST");
    }

    @Test
    void requestUriIsMapped() throws Exception {
        handler.set(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of(req.getUri());
                res.setHeaders(Headers.empty());
                return res;
            }
        });

        HttpURLConnection conn = connect("/foo/bar");
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("/foo/bar");
    }

    @Test
    void requestHeaderIsMapped() throws Exception {
        handler.set(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                String value = (String) req.getHeaders().getRawType("X-Test");
                HttpResponse res = HttpResponse.of(value != null ? value : "");
                res.setHeaders(Headers.empty());
                return res;
            }
        });

        HttpURLConnection conn = connect("/");
        conn.setRequestProperty("X-Test", "mapped");
        assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("mapped");
    }

    // ---------------------------------------------------- virtual threads

    @Test
    void virtualThreadPoolStartsAndHandlesRequest() throws Exception {
        int vtPort;
        try (ServerSocket s = new ServerSocket(0)) {
            vtPort = s.getLocalPort();
        }
        WebApplication app = new WebApplication();
        app.use(Predicates.any(), "vt", new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of("vt-ok");
                res.setHeaders(Headers.empty());
                return res;
            }
        });
        OptionMap options = OptionMap.of("http?", true, "port", vtPort, "host", "127.0.0.1",
                "join?", false, "virtualThreads?", true);
        Server vtServer = new JettyAdapter().runJetty(app, options);
        try {
            assertThat(vtServer.getThreadPool()).isInstanceOf(VirtualThreadPool.class);

            URI uri = URI.create("http://127.0.0.1:" + vtPort + "/");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            assertThat(conn.getResponseCode()).isEqualTo(200);
            assertThat(new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("vt-ok");
        } finally {
            vtServer.stop();
            vtServer.join();
        }
    }

    // ----------------------------------------------------------- SSL config

    @Test
    void sslConnectorRequiresKeystore() {
        OptionMap options = OptionMap.of("http?", false, "ssl?", true, "sslPort", 8443, "join?", false);
        assertThatThrownBy(() -> new JettyAdapter().runJetty(appWith(new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                return HttpResponse.of("ok");
            }
        }), options))
                .isInstanceOf(MisconfigurationException.class)
                .hasMessageContaining("SSL_KEYSTORE_REQUIRED");
    }
}
