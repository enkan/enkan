package enkan.component;

import enkan.collection.OptionMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import static org.assertj.core.api.Assertions.*;

class WebServerComponentTest {

    private TestWebServerComponent component;

    @BeforeEach
    void setUp() {
        component = new TestWebServerComponent();
    }

    @Test
    void defaultConfigurationIsCorrect() {
        assertThat(component.getPort()).isEqualTo(80);
        assertThat(component.getHost()).isEqualTo("0.0.0.0");
        assertThat(component.isHttp()).isTrue();
        assertThat(component.isSsl()).isFalse();
        assertThat(component.getSslPort()).isEqualTo(443);
    }

    @Test
    void optionMapContainsBasicSettings() {
        component.setPort(8080);
        component.setHost("localhost");
        component.setSslPort(8443);

        OptionMap options = component.getOptionMap();

        assertThat(options.get("port")).isEqualTo(8080);
        assertThat(options.get("host")).isEqualTo("localhost");
        assertThat(options.get("sslPort")).isEqualTo(8443);
    }

    @Test
    void sslWithoutKeystoreThrowsMisconfigurationException() {
        component.setSsl(true);
        assertThatThrownBy(() -> component.getOptionMap())
                .hasMessageContaining("SSL_KEYSTORE_REQUIRED");
    }

    @Test
    void keystoreCanBeLoadedFromFile(@TempDir Path tempDir) throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException {
        // Create a test keystore
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, "password".toCharArray());

        File keystoreFile = tempDir.resolve("keystore.jks").toFile();
        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            ks.store(fos, "password".toCharArray());
        }

        component.setKeystoreFile(keystoreFile);
        component.setKeystorePassword("password");

        KeyStore loadedKeystore = component.getKeystore();

        assertThat(loadedKeystore).isNotNull();
    }

    @Test
    void truststoreCanBeLoadedFromFile(@TempDir Path tempDir) throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException {
        // Create a test truststore
        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(null, "password".toCharArray());

        File truststoreFile = tempDir.resolve("truststore.jks").toFile();
        try (FileOutputStream fos = new FileOutputStream(truststoreFile)) {
            ts.store(fos, "password".toCharArray());
        }

        component.setTruststoreFile(truststoreFile);
        component.setTruststorePassword("password");

        KeyStore loadedTruststore = component.getTruststore();

        assertThat(loadedTruststore).isNotNull();
    }

    @Test
    void nonExistentKeystoreFileThrowsMisconfigurationException() {
        component.setKeystoreFile(new File("/non/existent/file.jks"));
        component.setKeystorePassword("password");

        assertThatThrownBy(() -> component.getKeystore())
                .hasMessageContaining("CANT_READ_KEYSTORE_FILE");
    }

    @Test
    void nonExistentTruststoreFileThrowsMisconfigurationException() {
        component.setTruststoreFile(new File("/non/existent/file.jks"));
        component.setTruststorePassword("password");

        assertThatThrownBy(() -> component.getTruststore())
                .hasMessageContaining("CANT_READ_TRUSTSTORE_FILE");
    }

    // ------------------------------------------------ graceful shutdown config

    @Test
    void defaultPreStopDelayIsZero() {
        assertThat(component.getPreStopDelay()).isEqualTo(0);
    }

    @Test
    void defaultStopTimeoutIs30Seconds() {
        assertThat(component.getStopTimeout()).isEqualTo(30000);
    }

    @Test
    void preStopDelayAcceptsZero() {
        component.setPreStopDelay(0);
        assertThat(component.getPreStopDelay()).isEqualTo(0);
    }

    @Test
    void preStopDelayAcceptsPositiveValue() {
        component.setPreStopDelay(5000);
        assertThat(component.getPreStopDelay()).isEqualTo(5000);
    }

    @Test
    void preStopDelayRejectsNegativeValue() {
        assertThatThrownBy(() -> component.setPreStopDelay(-1))
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    @Test
    void stopTimeoutAcceptsZero() {
        component.setStopTimeout(0);
        assertThat(component.getStopTimeout()).isEqualTo(0);
    }

    @Test
    void stopTimeoutRejectsNegativeValue() {
        assertThatThrownBy(() -> component.setStopTimeout(-1))
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    @Test
    void stopTimeoutIsIncludedInOptionMap() {
        component.setStopTimeout(15000);
        OptionMap options = component.getOptionMap();
        assertThat(options.getLong("stopTimeout")).isEqualTo(15000);
    }

    private static class TestWebServerComponent extends WebServerComponent<TestWebServerComponent> {
        public OptionMap getOptionMap() {
            return buildOptionMap();
        }

        @Override
        protected ComponentLifecycle<TestWebServerComponent> lifecycle() {
            return new ComponentLifecycle<>() {;
                @Override
                public void start(TestWebServerComponent component) {
                    // Do nothing
                }

                @Override
                public void stop(TestWebServerComponent component) {
                    // Do nothing
                }
            };
        }
    }
}