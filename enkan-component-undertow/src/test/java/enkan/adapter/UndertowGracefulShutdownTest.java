package enkan.adapter;

import enkan.adapter.UndertowAdapter.UndertowServer;
import enkan.application.WebApplication;
import enkan.collection.Headers;
import enkan.collection.OptionMap;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.middleware.WebMiddleware;
import enkan.util.Predicates;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UndertowGracefulShutdownTest {

    private int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private HttpURLConnection connect(int port, String path) throws IOException {
        URI uri = URI.create("http://127.0.0.1:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        return conn;
    }

    @Test
    void shutdownRejects503AfterDrainStarts() throws Exception {
        int port = findFreePort();
        OptionMap options = OptionMap.of("http?", true, "port", port, "host", "127.0.0.1");
        WebApplication app = new WebApplication();
        app.use(Predicates.any(), "endpoint", new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                HttpResponse res = HttpResponse.of("ok");
                res.setHeaders(Headers.empty());
                return res;
            }
        });
        UndertowServer server = new UndertowAdapter().runUndertow(app, options);
        try {
            // Verify server is working
            HttpURLConnection conn = connect(port, "/");
            assertThat(conn.getResponseCode()).isEqualTo(200);

            // Initiate graceful shutdown
            server.shutdownHandler().shutdown();

            // New requests should get 503
            HttpURLConnection conn2 = connect(port, "/");
            assertThat(conn2.getResponseCode()).isEqualTo(503);
        } finally {
            server.undertow().stop();
        }
    }

    @Test
    void inFlightRequestCompletesBeforeShutdown() throws Exception {
        int port = findFreePort();
        OptionMap options = OptionMap.of("http?", true, "port", port, "host", "127.0.0.1");

        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch handlerRelease = new CountDownLatch(1);

        WebApplication app = new WebApplication();
        app.use(Predicates.any(), "slow", new WebMiddleware() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req,
                    enkan.MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                handlerStarted.countDown();
                try {
                    handlerRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                HttpResponse res = HttpResponse.of("completed");
                res.setHeaders(Headers.empty());
                return res;
            }
        });
        UndertowServer server = new UndertowAdapter().runUndertow(app, options);
        try {
            // Start a slow request in background
            CompletableFuture<String> response = CompletableFuture.supplyAsync(() -> {
                try {
                    HttpURLConnection conn = connect(port, "/");
                    return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            // Wait until the handler has started processing
            assertThat(handlerStarted.await(3, TimeUnit.SECONDS)).isTrue();

            // Initiate graceful shutdown while request is in-flight
            server.shutdownHandler().shutdown();

            // Release the handler to complete
            handlerRelease.countDown();

            // The in-flight request should complete successfully
            String body = response.get(5, TimeUnit.SECONDS);
            assertThat(body).isEqualTo("completed");

            // Shutdown should complete since all requests are done
            assertThat(server.shutdownHandler().awaitShutdown(3000)).isTrue();
        } finally {
            server.undertow().stop();
        }
    }
}
