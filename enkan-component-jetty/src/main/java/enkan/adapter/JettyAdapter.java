package enkan.adapter;

import enkan.adapter.websocket.JettyWebSocketCreatorFactory;
import enkan.web.application.WebApplication;
import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.FalteringEnvironmentException;
import enkan.exception.MisconfigurationException;
import enkan.servlet.util.ServletUtils;
import enkan.web.websocket.WebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.compression.gzip.GzipCompression;
import org.eclipse.jetty.compression.server.CompressionHandler;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.VirtualThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author kawasima
 */
public class JettyAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(JettyAdapter.class);

    private static class ProxyServlet extends HttpServlet {
        private final WebApplication application;

        ProxyServlet(WebApplication application) {
            this.application = application;
        }

        @Override
        protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
                throws ServletException, IOException {
            HttpRequest request = ServletUtils.buildRequest(servletRequest, application::createRequest);
            try {
                HttpResponse response = application.handle(request);
                ServletUtils.updateServletResponse(servletResponse, response);
            } catch (Exception e) {
                LOG.error("Unhandled exception", e);
                servletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private HttpConfiguration httpConfiguration(OptionMap options) {
        HttpConfiguration config = new HttpConfiguration();
        config.setSendDateHeader(options.getBoolean("sendDateHeader", true));
        config.setOutputBufferSize(options.getInt("outputBufferSize", 32768));
        config.setRequestHeaderSize(options.getInt("requestHeaderSize", 8192));
        config.setResponseHeaderSize(options.getInt("responseHeaderSize", 8192));
        config.setSendServerVersion(options.getBoolean("sendServerVersion", true));
        return config;
    }

    private SslContextFactory.Server createSslContextFactory(OptionMap options) {
        final SslContextFactory.Server context = new SslContextFactory.Server();
        Object keystore = options.get("keystore");
        if (keystore instanceof KeyStore ks) {
            context.setKeyStore(ks);
        } else {
            throw new MisconfigurationException("web.SSL_KEYSTORE_REQUIRED");
        }
        context.setKeyStorePassword(options.getString("keystorePassword"));

        Object truststore = options.get("truststore");
        if (truststore instanceof KeyStore ts) {
            context.setTrustStore(ts);
        }
        context.setTrustStorePassword(options.getString("truststorePassword"));

        String clientAuth = options.getString("clientAuth", "none");
        switch (clientAuth) {
            case "need" -> context.setNeedClientAuth(true);
            case "want" -> context.setWantClientAuth(true);
            case "none" -> { /* no-op */ }
            default -> throw new MisconfigurationException("web.INVALID_CLIENT_AUTH", clientAuth);
        }

        return context;
    }

    private ServerConnector createSslConnector(Server server, OptionMap options) {
        int sslPort = options.getInt("sslPort", 443);
        HttpConfiguration config = httpConfiguration(options);
        config.setSecureScheme("https");
        config.setSecurePort(sslPort);
        config.addCustomizer(new SecureRequestCustomizer());
        HttpConnectionFactory httpFactory = new HttpConnectionFactory(config);

        SslConnectionFactory sslFactory = new SslConnectionFactory(createSslContextFactory(options), "http/1.1");

        ServerConnector connector = new ServerConnector(server, sslFactory, httpFactory);
        connector.setPort(sslPort);
        connector.setHost(options.getString("host"));
        connector.setIdleTimeout(options.getInt("maxIdleTime", 200000));
        return connector;
    }

    private ServerConnector createHttpConnector(Server server, OptionMap options) {
        HttpConnectionFactory factory = new HttpConnectionFactory(httpConfiguration(options));
        ServerConnector connector = new ServerConnector(server, factory);
        connector.setPort(options.getInt("port", 80));
        connector.setHost(options.getString("host", "0.0.0.0"));
        connector.setIdleTimeout(options.getLong("maxIdleTime", 200000));
        return connector;
    }

    private ThreadPool createThreadPool(OptionMap options) {
        if (options.getBoolean("virtualThreads?", false)) {
            return new VirtualThreadPool();
        }
        QueuedThreadPool pool = new QueuedThreadPool(options.getInt("maxThreads", 50));
        pool.setMinThreads(options.getInt("minThreads", 8));
        return pool;
    }

    @SuppressWarnings("unchecked")
    private Server createServer(OptionMap options) {
        Server server = new Server(createThreadPool(options));

        BiFunction<Server, OptionMap, ServerConnector> serverConnectorFactory = (BiFunction<Server, OptionMap, ServerConnector>) options.get("serverConnectorFactory");
        if (serverConnectorFactory != null) {
            Connector connector = serverConnectorFactory.apply(server, options);
            server.addConnector(connector);
        } else if (options.getBoolean("http?", true)) {
            server.addConnector(createHttpConnector(server, options));
        }

        if (options.getBoolean("ssl?", false)) {
            server.addConnector(createSslConnector(server, options));
        }

        return server;
    }

    public Server runJetty(WebApplication application) {
        return runJetty(application, OptionMap.of());
    }

    @SuppressWarnings("unchecked")
    public Server runJetty(WebApplication application, OptionMap options) {
        Server server = createServer(options);
        ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.addServlet(new ServletHolder(new ProxyServlet(application)), "/*");

        Map<String, WebSocketHandler> wsHandlers = (Map<String, WebSocketHandler>) options.get("wsHandlers");
        if (wsHandlers != null && !wsHandlers.isEmpty()) {
            JettyWebSocketServletContainerInitializer.configure(contextHandler, (servletContext, container) -> {
                for (Map.Entry<String, WebSocketHandler> entry : wsHandlers.entrySet()) {
                    WebSocketHandler handler = entry.getValue();
                    container.addMapping(entry.getKey(),
                            JettyWebSocketCreatorFactory.forHandler(handler));
                }
            });
        }

        if (options.getBoolean("compress?", false)) {
            GzipCompression gzip = new GzipCompression();
            gzip.setMinCompressSize(options.getInt("compressMinSize", 1024));
            CompressionHandler compressionHandler = new CompressionHandler(contextHandler);
            compressionHandler.putCompression(gzip);
            server.setHandler(compressionHandler);
        } else {
            server.setHandler(contextHandler);
        }
        try {
            server.setStopAtShutdown(true);
            server.setStopTimeout(options.getLong("stopTimeout", 30000));
            server.start();
            if (options.getBoolean("join?", true)) {
                server.join();
            }
        } catch (Exception ex) {
            try {
                server.stop();
            } catch (Exception stopEx) {
                ex.addSuppressed(stopEx);
            }
            throw new FalteringEnvironmentException(ex);
        }
        return server;
    }
}
