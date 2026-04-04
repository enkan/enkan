package enkan.component.jetty;

import enkan.web.application.WebApplication;
import enkan.web.collection.Headers;
import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.WebMiddleware;
import enkan.util.Predicates;
import enkan.web.util.DigestFieldsUtils;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RFC 9530 Digest Fields on the Jetty adapter.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JettyAdapterDigestTest {

    private Server serverNoCompress;
    private Server serverWithCompress;
    private int portNoCompress;
    private int portWithCompress;

    private final AtomicReference<String> responseBody = new AtomicReference<>("hello");

    @BeforeAll
    void startServers() throws IOException {
        portNoCompress = freePort();
        portWithCompress = freePort();

        WebApplication app = buildApp();

        OptionMap optNoCompress = OptionMap.of(
                "http?", true, "port", portNoCompress, "host", "127.0.0.1",
                "join?", false, "digestAlgorithm", "sha-256");
        serverNoCompress = new JettyAdapter().runJetty(app, optNoCompress);

        OptionMap optWithCompress = OptionMap.of(
                "http?", true, "port", portWithCompress, "host", "127.0.0.1",
                "join?", false, "digestAlgorithm", "sha-256", "compress?", true);
        serverWithCompress = new JettyAdapter().runJetty(app, optWithCompress);
    }

    @AfterAll
    void stopServers() throws Exception {
        for (Server s : new Server[]{serverNoCompress, serverWithCompress}) {
            if (s != null) { s.stop(); s.join(); }
        }
    }

    private WebApplication buildApp() {
        WebApplication app = new WebApplication();
        app.use(Predicates.any(), "handler", new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of(responseBody.get());
                res.setHeaders(Headers.empty());
                return res;
            }
        });
        return app;
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
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
        responseBody.set("hello");
        HttpURLConnection conn = connect(portNoCompress, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);

        String reprDigest = conn.getHeaderField("Repr-Digest");
        assertThat(reprDigest).isNotNull().startsWith("sha-256=:");

        // Verify the value matches the body
        byte[] body = conn.getInputStream().readAllBytes();
        String expected = DigestFieldsUtils.computeDigestHeader(body, "sha-256");
        assertThat(reprDigest).isEqualTo(expected);
    }

    @Test
    void contentDigestIsSetWithoutCompression() throws Exception {
        responseBody.set("hello");
        HttpURLConnection conn = connect(portNoCompress, "/");
        assertThat(conn.getResponseCode()).isEqualTo(200);

        String contentDigest = conn.getHeaderField("Content-Digest");
        assertThat(contentDigest).isNotNull().startsWith("sha-256=:");

        // Without compression Content-Digest == Repr-Digest
        byte[] body = conn.getInputStream().readAllBytes();
        String expected = DigestFieldsUtils.computeDigestHeader(body, "sha-256");
        assertThat(contentDigest).isEqualTo(expected);
    }

    @Test
    void reprDigestEqualsContentDigestWithoutCompression() throws Exception {
        responseBody.set("no-compress-equality");
        HttpURLConnection conn = connect(portNoCompress, "/");
        conn.getInputStream().readAllBytes(); // consume

        String reprDigest    = conn.getHeaderField("Repr-Digest");
        String contentDigest = conn.getHeaderField("Content-Digest");
        assertThat(reprDigest).isNotNull().isEqualTo(contentDigest);
    }

    // ----------------------------------------------------------- negotiation

    @Test
    void wantReprDigestSha512IsHonored() throws Exception {
        responseBody.set("negotiation test");
        HttpURLConnection conn = connect(portNoCompress, "/");
        conn.setRequestProperty("Want-Repr-Digest", "sha-512=10");

        assertThat(conn.getResponseCode()).isEqualTo(200);
        String reprDigest = conn.getHeaderField("Repr-Digest");
        assertThat(reprDigest).isNotNull().startsWith("sha-512=:");
    }

    @Test
    void wantReprDigestUnsupportedAlgorithmOmitsHeader() throws Exception {
        responseBody.set("no algo");
        HttpURLConnection conn = connect(portNoCompress, "/");
        conn.setRequestProperty("Want-Repr-Digest", "md5=5");

        assertThat(conn.getResponseCode()).isEqualTo(200);
        // No supported algorithm — header must be absent
        assertThat(conn.getHeaderField("Repr-Digest")).isNull();
    }

    // ----------------------------------------------------------- with compression server

    @Test
    void reprDigestIsSetWithCompression() throws Exception {
        responseBody.set("compressed response body");
        // Request gzip to actually exercise the compression path
        HttpURLConnection conn = connect(portWithCompress, "/", "gzip");
        assertThat(conn.getResponseCode()).isEqualTo(200);

        String reprDigest = conn.getHeaderField("Repr-Digest");
        assertThat(reprDigest).isNotNull().startsWith("sha-256=:");

        // Repr-Digest covers the pre-compression (plain) body
        conn.getInputStream().readAllBytes();
        String expected = DigestFieldsUtils.computeDigestHeader(
                "compressed response body".getBytes(StandardCharsets.UTF_8), "sha-256");
        assertThat(reprDigest).isEqualTo(expected);
    }

    @Test
    void contentDigestIsSetWithCompression() throws Exception {
        responseBody.set("compressed content digest body");
        // Request gzip so the server actually compresses the response
        HttpURLConnection conn = connect(portWithCompress, "/", "gzip");
        assertThat(conn.getResponseCode()).isEqualTo(200);

        // Read the raw on-wire (gzip-compressed) bytes — Content-Digest covers these
        byte[] compressedBody = conn.getInputStream().readAllBytes();

        String contentDigest = conn.getHeaderField("Content-Digest");
        assertThat(contentDigest).isNotNull().startsWith("sha-256=:");

        // Verify digest matches actual on-wire bytes
        String expected = DigestFieldsUtils.computeDigestHeader(compressedBody, "sha-256");
        assertThat(contentDigest).isEqualTo(expected);
    }
}
