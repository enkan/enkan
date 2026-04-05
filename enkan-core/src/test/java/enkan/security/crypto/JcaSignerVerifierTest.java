package enkan.security.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;

class JcaSignerVerifierTest {

    private static final byte[] DATA = "hello world".getBytes(StandardCharsets.UTF_8);

    // ----------------------------------------------------------- HMAC

    @Test
    void hmacSha256SignAndVerifyRoundTrip() throws Exception {
        assertHmacRoundTrip(CryptoAlgorithm.HMAC_SHA256);
    }

    @Test
    void hmacSha384SignAndVerifyRoundTrip() throws Exception {
        assertHmacRoundTrip(CryptoAlgorithm.HMAC_SHA384);
    }

    @Test
    void hmacSha512SignAndVerifyRoundTrip() throws Exception {
        assertHmacRoundTrip(CryptoAlgorithm.HMAC_SHA512);
    }

    private void assertHmacRoundTrip(CryptoAlgorithm alg) throws Exception {
        SecretKey key = KeyGenerator.getInstance(alg.jcaName()).generateKey();
        Signer signer = new JcaSigner(alg, key);
        Verifier verifier = new JcaVerifier(alg, key);

        byte[] signature = signer.sign(DATA);
        assertThat(verifier.verify(DATA, signature)).isTrue();
    }

    @Test
    void hmacVerifyFailsWithWrongKey() throws Exception {
        CryptoAlgorithm alg = CryptoAlgorithm.HMAC_SHA256;
        KeyGenerator kg = KeyGenerator.getInstance(alg.jcaName());
        SecretKey key1 = kg.generateKey();
        SecretKey key2 = kg.generateKey();

        byte[] signature = new JcaSigner(alg, key1).sign(DATA);
        assertThat(new JcaVerifier(alg, key2).verify(DATA, signature)).isFalse();
    }

    @Test
    void hmacVerifyFailsWithTamperedData() throws Exception {
        CryptoAlgorithm alg = CryptoAlgorithm.HMAC_SHA256;
        SecretKey key = KeyGenerator.getInstance(alg.jcaName()).generateKey();

        byte[] signature = new JcaSigner(alg, key).sign(DATA);
        assertThat(new JcaVerifier(alg, key).verify("tampered".getBytes(), signature)).isFalse();
    }

    // ----------------------------------------------------------- Ed25519

    @Test
    void ed25519SignAndVerifyRoundTrip() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        CryptoAlgorithm alg = CryptoAlgorithm.ED25519;

        byte[] signature = new JcaSigner(alg, kp.getPrivate()).sign(DATA);
        assertThat(new JcaVerifier(alg, kp.getPublic()).verify(DATA, signature)).isTrue();
    }

    @Test
    void ed25519VerifyFailsWithWrongKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp1 = kpg.generateKeyPair();
        KeyPair kp2 = kpg.generateKeyPair();
        CryptoAlgorithm alg = CryptoAlgorithm.ED25519;

        byte[] signature = new JcaSigner(alg, kp1.getPrivate()).sign(DATA);
        assertThat(new JcaVerifier(alg, kp2.getPublic()).verify(DATA, signature)).isFalse();
    }

    // ----------------------------------------------------------- ECDSA

    @Test
    void ecdsaP256SignAndVerifyRoundTrip() throws Exception {
        KeyPair kp = ecKeyPair("secp256r1");
        CryptoAlgorithm alg = CryptoAlgorithm.ECDSA_P256_SHA256;

        byte[] signature = new JcaSigner(alg, kp.getPrivate()).sign(DATA);
        // P1363 format: 64 bytes for P-256
        assertThat(signature).hasSize(64);
        assertThat(new JcaVerifier(alg, kp.getPublic()).verify(DATA, signature)).isTrue();
    }

    @Test
    void ecdsaP384SignAndVerifyRoundTrip() throws Exception {
        KeyPair kp = ecKeyPair("secp384r1");
        CryptoAlgorithm alg = CryptoAlgorithm.ECDSA_P384_SHA384;

        byte[] signature = new JcaSigner(alg, kp.getPrivate()).sign(DATA);
        assertThat(signature).hasSize(96);
        assertThat(new JcaVerifier(alg, kp.getPublic()).verify(DATA, signature)).isTrue();
    }

    @Test
    void ecdsaP521SignAndVerifyRoundTrip() throws Exception {
        KeyPair kp = ecKeyPair("secp521r1");
        CryptoAlgorithm alg = CryptoAlgorithm.ECDSA_P521_SHA512;

        byte[] signature = new JcaSigner(alg, kp.getPrivate()).sign(DATA);
        assertThat(signature).hasSize(132);
        assertThat(new JcaVerifier(alg, kp.getPublic()).verify(DATA, signature)).isTrue();
    }

    @Test
    void ecdsaVerifyFailsWithTamperedSignature() throws Exception {
        KeyPair kp = ecKeyPair("secp256r1");
        CryptoAlgorithm alg = CryptoAlgorithm.ECDSA_P256_SHA256;

        byte[] signature = new JcaSigner(alg, kp.getPrivate()).sign(DATA);
        signature[0] ^= 0xff; // tamper
        assertThat(new JcaVerifier(alg, kp.getPublic()).verify(DATA, signature)).isFalse();
    }

    // ----------------------------------------------------------- RSA-PSS

    @Test
    void rsaPssSha512SignAndVerifyRoundTrip() throws Exception {
        KeyPair kp = rsaKeyPair();
        CryptoAlgorithm alg = CryptoAlgorithm.RSA_PSS_SHA512;

        byte[] signature = new JcaSigner(alg, kp.getPrivate()).sign(DATA);
        assertThat(new JcaVerifier(alg, kp.getPublic()).verify(DATA, signature)).isTrue();
    }

    @Test
    void rsaPssSha256SignAndVerifyRoundTrip() throws Exception {
        KeyPair kp = rsaKeyPair();
        CryptoAlgorithm alg = CryptoAlgorithm.RSA_PSS_SHA256;

        byte[] signature = new JcaSigner(alg, kp.getPrivate()).sign(DATA);
        assertThat(new JcaVerifier(alg, kp.getPublic()).verify(DATA, signature)).isTrue();
    }

    // ----------------------------------------------------------- RSA PKCS#1 v1.5

    @Test
    void rsaV15Sha256SignAndVerifyRoundTrip() throws Exception {
        KeyPair kp = rsaKeyPair();
        CryptoAlgorithm alg = CryptoAlgorithm.RSA_V1_5_SHA256;

        byte[] signature = new JcaSigner(alg, kp.getPrivate()).sign(DATA);
        assertThat(new JcaVerifier(alg, kp.getPublic()).verify(DATA, signature)).isTrue();
    }

    @Test
    void rsaVerifyFailsWithWrongKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp1 = kpg.generateKeyPair();
        KeyPair kp2 = kpg.generateKeyPair();
        CryptoAlgorithm alg = CryptoAlgorithm.RSA_V1_5_SHA256;

        byte[] signature = new JcaSigner(alg, kp1.getPrivate()).sign(DATA);
        assertThat(new JcaVerifier(alg, kp2.getPublic()).verify(DATA, signature)).isFalse();
    }

    // ----------------------------------------------------------- helpers

    private static KeyPair ecKeyPair(String curveName) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec(curveName));
        return kpg.generateKeyPair();
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }
}
