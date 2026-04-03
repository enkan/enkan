package enkan.component;

import enkan.collection.OptionMap;
import enkan.exception.MisconfigurationException;
import enkan.exception.UnreachableException;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import static enkan.util.ThreadingUtils.some;

/**
 * @author kawasima
 */
public abstract class WebServerComponent<T extends WebServerComponent<T>> extends SystemComponent<T> {
    @DecimalMax("65535")
    @DecimalMin("1")
    private Integer port = 80;

    private String host = "0.0.0.0";

    private boolean isHttp = true;
    private boolean isSsl = false;
    private int sslPort = 443;

    private File keystoreFile;
    private KeyStore keystore;
    private String keystorePassword;

    // preStopDelay is used by Component.stop(), not passed to the Adapter via buildOptionMap().
    // stopTimeout is passed to the Adapter via buildOptionMap() to configure the server's drain timeout.
    private long preStopDelay = 0;
    private long stopTimeout = 30000;

    private File truststoreFile;
    private KeyStore truststore;
    private String truststorePassword;

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public boolean isHttp() {
        return isHttp;
    }

    public void setHttp(boolean http) {
        isHttp = http;
    }

    public boolean isSsl() {
        return isSsl;
    }

    public void setSsl(boolean ssl) {
        isSsl = ssl;
    }

    public int getSslPort() {
        return sslPort;
    }

    public void setSslPort(int sslPort) {
        this.sslPort = sslPort;
    }

    public void setKeystoreFile(File keystoreFile) {
        this.keystoreFile = keystoreFile;
    }

    public void setKeystorePath(String keystorePath) {
        if (keystorePath != null && !keystorePath.isEmpty()) {
            this.keystoreFile = new File(keystorePath);
        }
    }

    public KeyStore getKeystore() {
        if (keystore == null && keystoreFile != null) {
            try {
                keystore = KeyStore.getInstance("JKS");
                try (InputStream in = new FileInputStream(keystoreFile)) {
                    keystore.load(in, some(keystorePassword, String::toCharArray).orElse(null));
                }
            } catch (KeyStoreException e) {
                throw new MisconfigurationException("core.KEY_STORE", e.getMessage(), e);
            } catch (CertificateException e) {
                throw new MisconfigurationException("core.CERTIFICATE", e.getMessage(), e);
            } catch (NoSuchAlgorithmException e) {
                throw new UnreachableException(e);
            } catch (IOException e) {
                throw new MisconfigurationException("core.CANT_READ_KEYSTORE_FILE", keystoreFile, e);
            }
        }
        return keystore;
    }

    public void setKeystore(KeyStore keystore) {
        this.keystore = keystore;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public void setTruststoreFile(File truststoreFile) {
        this.truststoreFile = truststoreFile;
    }

    public void setTruststorePath(String truststorePath) {
        if (truststorePath != null && !truststorePath.isEmpty()) {
            this.truststoreFile = new File(truststorePath);
        }
    }

    public KeyStore getTruststore() {
        if (truststore == null && truststoreFile != null) {
            try {
                truststore = KeyStore.getInstance("JKS");
                try (InputStream in = new FileInputStream(truststoreFile)) {
                    truststore.load(in, some(truststorePassword, String::toCharArray).orElse(null));
                }
            } catch (KeyStoreException e) {
                throw new MisconfigurationException("core.KEY_STORE", e);
            } catch (CertificateException e) {
                throw new MisconfigurationException("core.CERTIFICATE", e);
            } catch (NoSuchAlgorithmException e) {
                throw new UnreachableException(e);
            } catch (IOException e) {
                throw new MisconfigurationException("core.CANT_READ_TRUSTSTORE_FILE", truststoreFile, e);
            }
        }

        return truststore;
    }

    public void setTruststore(KeyStore truststore) {
        this.truststore = truststore;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }

    public long getPreStopDelay() {
        return preStopDelay;
    }

    /**
     * Set the delay in milliseconds before the server stops accepting new connections.
     * During this delay, the server continues to serve requests while health status
     * reports STOPPING, allowing load balancers and Kubernetes Endpoints to propagate
     * the change.
     *
     * @param preStopDelay delay in milliseconds (default 0)
     */
    public void setPreStopDelay(long preStopDelay) {
        if (preStopDelay < 0) {
            throw new MisconfigurationException("core.INVALID_ARGUMENT", "preStopDelay", preStopDelay);
        }
        this.preStopDelay = preStopDelay;
    }

    public long getStopTimeout() {
        return stopTimeout;
    }

    /**
     * Set the timeout in milliseconds to wait for in-flight requests to complete
     * after the server stops accepting new connections. After this timeout, the
     * server is forcefully stopped.
     *
     * @param stopTimeout timeout in milliseconds (default 30000)
     */
    public void setStopTimeout(long stopTimeout) {
        if (stopTimeout < 0) {
            throw new MisconfigurationException("core.INVALID_ARGUMENT", "stopTimeout", stopTimeout);
        }
        this.stopTimeout = stopTimeout;
    }

    protected OptionMap buildOptionMap() {
        if (isSsl && keystoreFile == null && keystore == null) {
            throw new MisconfigurationException("core.SSL_KEYSTORE_REQUIRED");
        }

        OptionMap options = OptionMap.of(
                "http?", isHttp,
                "ssl?",  isSsl,
                "port",  port,
                "host",  host,
                "sslPort", sslPort,
                "stopTimeout", stopTimeout);

        KeyStore keystore = getKeystore();
        if (keystore != null) options.put("keystore", keystore);
        if (keystorePassword != null) options.put("keystorePassword", keystorePassword);

        KeyStore truststore = getTruststore();
        if (truststore != null) options.put("truststore", truststore);
        if (truststorePassword != null) options.put("truststorePassword", truststorePassword);

        return options;
    }
}
