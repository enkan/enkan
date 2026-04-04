package enkan.component.undertow;

import enkan.web.application.WebApplication;
import enkan.web.collection.Headers;
import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.WebMiddleware;
import enkan.util.Predicates;
import enkan.web.util.DigestFieldsUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RFC 9530 Digest Fields on the Undertow adapter.
 */
class UndertowAdapterDigestTest {

    private UndertowAdapter.UndertowServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.undertow().stop();
            server = null;
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    }

    private UndertowAdapter.UndertowServer startWith(String body, OptionMap extra) throws IOException {
        WebApplication app = new WebApplication();
        app.use(Predicates.any(), "handler", new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of(body);
                res.setHeaders(Headers.empty());
                return res;
            }
        });
        server = new UndertowAdapter().runUndertow(app, extra);
        return server;
    }

    private HttpURLConnection connect(int port, String path) throws IOException {
        return connect(port, path, "identity");
    }

    private HttpURLConnection connect(int port, String path, String acceptEncoding) throws IOException {
        URI uri = URI.create("http://127.0.0.1:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        conn.setRequestProperty("Accept-Encoding", acceptEncoding);
        return conn;
    }

    // ----------------------------------------------------------- no compression

    @Test
    void reprDigestIsSetWithoutCompression() throws Exception {
        int port = freePort();
        startWith("hello undertow", OptionMap.of(
                "http?", true, "port", port, "host", "127.0.0.1",
                "digestAlgorithm", "sha-256"));

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        byte[] body = conn.getInputStream().readAllBytes();

        String reprDigest = conn.getHeaderField("Repr-Digest");
        assertThat(reprDigest).isNotNull().startsWith("sha-256=:");

        String expected = DigestFieldsUtils.computeDigestHeader(body, "sha-256");
        assertThat(reprDigest).isEqualTo(expected);
    }

    @Test
    void contentDigestIsSetWithoutCompression() throws Exception {
        int port = freePort();
        startWith("hello undertow", OptionMap.of(
                "http?", true, "port", port, "host", "127.0.0.1",
                "digestAlgorithm", "sha-256"));

        HttpURLConnection conn = connect(port, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        byte[] body = conn.getInputStream().readAllBytes();

        String contentDigest = conn.getHeaderField("Content-Digest");
        assertThat(contentDigest).isNotNull().startsWith("sha-256=:");

        String expected = DigestFieldsUtils.computeDigestHeader(body, "sha-256");
        assertThat(contentDigest).isEqualTo(expected);
    }

    @Test
    void reprDigestEqualsContentDigestWithoutCompression() throws Exception {
        int port = freePort();
        startWith("equality test", OptionMap.of(
                "http?", true, "port", port, "host", "127.0.0.1",
                "digestAlgorithm", "sha-256"));

        HttpURLConnection conn = connect(port, "/");
        conn.getInputStream().readAllBytes();

        assertThat(conn.getHeaderField("Repr-Digest"))
                .isNotNull()
                .isEqualTo(conn.getHeaderField("Content-Digest"));
    }

    // ----------------------------------------------------------- negotiation

    @Test
    void wantReprDigestSha512IsHonored() throws Exception {
        int port = freePort();
        startWith("negotiate", OptionMap.of(
                "http?", true, "port", port, "host", "127.0.0.1",
                "digestAlgorithm", "sha-256"));

        HttpURLConnection conn = connect(port, "/");
        conn.setRequestProperty("Want-Repr-Digest", "sha-512=10");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        conn.getInputStream().readAllBytes();

        assertThat(conn.getHeaderField("Repr-Digest")).isNotNull().startsWith("sha-512=:");
    }

    @Test
    void wantReprDigestUnsupportedOmitsHeader() throws Exception {
        int port = freePort();
        startWith("no algo", OptionMap.of(
                "http?", true, "port", port, "host", "127.0.0.1",
                "digestAlgorithm", "sha-256"));

        HttpURLConnection conn = connect(port, "/");
        conn.setRequestProperty("Want-Repr-Digest", "md5=5");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        conn.getInputStream().readAllBytes();

        assertThat(conn.getHeaderField("Repr-Digest")).isNull();
    }

    // ----------------------------------------------------------- with compression

    @Test
    void reprDigestIsSetWithCompression() throws Exception {
        int port = freePort();
        String plainBody = "compressed undertow response";
        startWith(plainBody, OptionMap.of(
                "http?", true, "port", port, "host", "127.0.0.1",
                "digestAlgorithm", "sha-256", "compress?", true));

        // Request with gzip to actually exercise the compression path
        HttpURLConnection conn = connect(port, "/", "gzip");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        conn.getInputStream().readAllBytes();

        // Repr-Digest is over the pre-compression representation bytes
        String reprDigest = conn.getHeaderField("Repr-Digest");
        assertThat(reprDigest).isNotNull().startsWith("sha-256=:");

        String expected = DigestFieldsUtils.computeDigestHeader(
                plainBody.getBytes(StandardCharsets.UTF_8), "sha-256");
        assertThat(reprDigest).isEqualTo(expected);
    }

    @Test
    void contentDigestIsSetWithCompression() throws Exception {
        int port = freePort();
        startWith("compressed undertow content", OptionMap.of(
                "http?", true, "port", port, "host", "127.0.0.1",
                "digestAlgorithm", "sha-256", "compress?", true));

        // Request with gzip so the server actually compresses the response
        HttpURLConnection conn = connect(port, "/", "gzip");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        // Read the raw (compressed) bytes — Content-Digest covers these on-wire bytes
        byte[] compressedBody = conn.getInputStream().readAllBytes();

        String contentDigest = conn.getHeaderField("Content-Digest");
        assertThat(contentDigest).isNotNull().startsWith("sha-256=:");

        // Verify digest matches the actual on-wire (gzip-compressed) bytes
        String expected = DigestFieldsUtils.computeDigestHeader(compressedBody, "sha-256");
        assertThat(contentDigest).isEqualTo(expected);
    }

    @Test
    void wantContentDigestIsHonoredWithCompression() throws Exception {
        int port = freePort();
        startWith("want content digest", OptionMap.of(
                "http?", true, "port", port, "host", "127.0.0.1",
                "digestAlgorithm", "sha-256", "compress?", true));

        HttpURLConnection conn = connect(port, "/", "gzip");
        conn.setRequestProperty("Want-Content-Digest", "sha-512=10");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        conn.getInputStream().readAllBytes();

        assertThat(conn.getHeaderField("Content-Digest")).isNotNull().startsWith("sha-512=:");
    }
}
