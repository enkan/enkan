package enkan.component.undertow;

import enkan.component.undertow.digest.CombinedDigestConduit;
import enkan.component.undertow.digest.DigestConduit;
import enkan.component.undertow.digest.DigestOuterHandler;
import enkan.web.application.WebApplication;
import enkan.web.collection.Headers;
import enkan.web.http.fields.digest.DigestFields;
import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.data.StreamingBody;
import enkan.exception.MisconfigurationException;
import enkan.exception.ServiceUnavailableException;
import enkan.exception.UnreachableException;
import io.undertow.Undertow;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.xnio.streams.ChannelInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.*;

/**
 * Undertow exchange adapter.
 *
 * @author kawasima
 */
public class UndertowAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowAdapter.class);
    private static final HttpString REPR_DIGEST = HttpString.tryFromString("Repr-Digest");

    public record UndertowServer(Undertow undertow, GracefulShutdownHandler shutdownHandler) {}

    private static final IoCallback callback = new IoCallback() {
        @Override
        public void onComplete(HttpServerExchange exchange, Sender sender) {
        }

        @Override
        public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
            LOG.error("Failed to send response body", exception);
        }
    };

    private static void setBody(HttpServerExchange exchange, Object body) throws IOException {
        switch (body) {
            case null -> {
                // Do nothing
            }
            case StreamingBody streaming -> {
                exchange.getOutputStream().flush();
                streaming.writeTo(exchange.getOutputStream());
            }
            case String s -> exchange.getResponseSender().send(s);
            case InputStream inputStream -> {
                Sender sender = exchange.getResponseSender();
                try (ReadableByteChannel chan = Channels.newChannel(inputStream)) {
                    ByteBuffer buf = ByteBuffer.allocate(4096);
                    for (; ; ) {
                        int size = chan.read(buf);
                        if (size <= 0) break;
                        buf.flip();
                        sender.send(buf, callback);
                        buf.clear();
                    }
                    sender.close(IoCallback.END_EXCHANGE);
                }
            }
            case File file -> {
                Sender sender = exchange.getResponseSender();
                try (FileInputStream fis = new FileInputStream(file);
                     FileChannel chan = fis.getChannel()) {
                    ByteBuffer buf = ByteBuffer.allocate(4096);
                    for (; ; ) {
                        int size = chan.read(buf);
                        if (size <= 0) break;
                        buf.flip();
                        sender.send(buf, callback);
                        buf.clear();
                    }
                    sender.close(IoCallback.END_EXCHANGE);
                }
            }
            default -> throw new UnreachableException();
        }
    }

    private void setResponseHeaders(Headers headers, HttpServerExchange exchange) {
        HeaderMap map = exchange.getResponseHeaders();
        headers.forEachHeader((headerName, v) -> {
            switch (v) {
                case String s -> map.add(HttpString.tryFromString(headerName), s);
                case Number n -> map.add(HttpString.tryFromString(headerName), n.longValue());
                default -> { /* ignore unsupported header value types */ }
            }
        });
    }

    private void setOptions(Undertow.Builder builder, OptionMap options) {
        if (options.containsKey("ioThreads")) builder.setIoThreads(options.getInt("ioThreads"));
        if (options.containsKey("workerThreads")) builder.setWorkerThreads(options.getInt("workerThreads"));
        if (options.containsKey("bufferSize")) builder.setBufferSize(options.getInt("bufferSize"));
        if (options.containsKey("directBuffers")) builder.setDirectBuffers(options.getBoolean("directBuffers"));
    }

    public UndertowServer runUndertow(WebApplication application, OptionMap options) {
        Undertow.Builder builder = Undertow.builder();

        String digestAlgorithm = options.getString("digestAlgorithm");
        boolean compress = options.getBoolean("compress?", false);

        HttpHandler appHandler = new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                if (exchange.isInIoThread()) {
                    exchange.dispatch(this);
                    return;
                }

                // Register digest conduit(s) inside appHandler so they observe pre-compression bytes.
                if (digestAlgorithm != null) {
                    String wantRepr = exchange.getRequestHeaders().getFirst("Want-Repr-Digest");
                    String reprAlgo = DigestFields.negotiateAlgorithm(wantRepr, digestAlgorithm);

                    if (!compress) {
                        // No compression: on-wire bytes == representation bytes.
                        // Use a single CombinedDigestConduit to buffer once and set both headers.
                        String wantContent = exchange.getRequestHeaders().getFirst("Want-Content-Digest");
                        String contentAlgo = DigestFields.negotiateAlgorithm(wantContent, digestAlgorithm);
                        if (reprAlgo != null || contentAlgo != null) {
                            String finalReprAlgo = reprAlgo;
                            String finalContentAlgo = contentAlgo;
                            exchange.addResponseWrapper((factory, ex) ->
                                    new CombinedDigestConduit(factory.create(), ex, finalReprAlgo, finalContentAlgo));
                        }
                    } else {
                        // Compression enabled: only Repr-Digest is set here (pre-compression bytes).
                        // Content-Digest (post-compression bytes) is handled by DigestOuterHandler.
                        if (reprAlgo != null) {
                            exchange.addResponseWrapper((factory, ex) ->
                                    new DigestConduit(factory.create(), ex, reprAlgo, REPR_DIGEST));
                        }
                    }
                }

                HttpRequest request = application.createRequest();
                request.setRequestMethod(exchange.getRequestMethod().toString());
                request.setUri(exchange.getRequestURI());
                request.setProtocol(exchange.getProtocol().toString());
                request.setQueryString(exchange.getQueryString());
                request.setCharacterEncoding(exchange.getRequestCharset());
                request.setBody(new ChannelInputStream(exchange.getRequestChannel()));
                request.setContentLength(exchange.getRequestContentLength());
                request.setRemoteAddr(exchange.getSourceAddress()
                        .getAddress()
                        .getHostAddress());
                request.setScheme(exchange.getRequestScheme());
                request.setServerName(exchange.getHostName());
                request.setServerPort(exchange.getHostPort());
                Headers headers = Headers.empty();
                exchange.getRequestHeaders().forEach(e -> {
                    String headerName = e.getHeaderName().toString();
                    e.forEach(v -> headers.put(headerName, v));
                });
                request.setHeaders(headers);

                try {
                    HttpResponse response = application.handle(request);
                    exchange.setStatusCode(response.getStatus());
                    setResponseHeaders(response.getHeaders(), exchange);

                    exchange.startBlocking();
                    setBody(exchange, response.getBody());
                } catch (ServiceUnavailableException ex) {
                    exchange.setStatusCode(503);
                } finally {
                    exchange.endExchange();
                }
            }
        };

        HttpHandler finalHandler;
        if (compress) {
            HttpHandler encodingHandler = new EncodingHandler(appHandler,
                    new ContentEncodingRepository()
                            .addEncodingHandler("gzip", new GzipEncodingProvider(), 50));
            // DigestOuterHandler wraps EncodingHandler to observe post-compression bytes
            finalHandler = digestAlgorithm != null
                    ? new DigestOuterHandler(encodingHandler, digestAlgorithm)
                    : encodingHandler;
        } else {
            finalHandler = appHandler;
        }

        GracefulShutdownHandler shutdownHandler = new GracefulShutdownHandler(finalHandler);
        builder.setHandler(shutdownHandler);

        setOptions(builder, options);
        if (options.getBoolean("http?", true)) {
            builder.addHttpListener(options.getInt("port", 80),
                    options.getString("host", "0.0.0.0"));
        }

        if (options.getBoolean("ssl?", false)) {
            builder.addHttpsListener(options.getInt("sslPort", 443),
                    options.getString("host", "0.0.0.0"),
                    createSslContext(options));
        }
        Undertow undertow = builder.build();
        undertow.start();

        return new UndertowServer(undertow, shutdownHandler);
    }

    private SSLContext createSslContext(OptionMap options) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            KeyManager[] keyManagers = null;
            TrustManager[] trustManagers = null;

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore keystore = (KeyStore) options.get("keystore");
            if (keystore != null) {
                kmf.init(keystore, options.getString("keystorePassword", "").toCharArray());
                keyManagers = kmf.getKeyManagers();
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore truststore = (KeyStore) options.get("truststore");
            if (truststore != null) {
                tmf.init(truststore);
                trustManagers = tmf.getTrustManagers();
            }

            context.init(keyManagers, trustManagers, null);
            return context;
        } catch (UnrecoverableKeyException e) {
            throw new MisconfigurationException("core.UNRECOVERABLE_KEY", e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new MisconfigurationException("core.NO_SUCH_ALGORITHM",
                    e.getMessage(), "TLS, TLSv1.2, TLSv1.3", e);
        } catch (KeyStoreException e) {
            throw new MisconfigurationException("core.KEY_STORE", e.getMessage(), e);
        } catch (KeyManagementException e) {
            throw new MisconfigurationException("core.KEY_MANAGEMENT", e.getMessage(), e);
        }
    }
}
