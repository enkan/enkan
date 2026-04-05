package enkan.web.jwt;

import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProcessorTest {

    // ----------------------------------------------------------- HMAC round-trip

    @Test
    void hmacSha256SignAndVerifyRoundTrip() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        byte[] claims = "{\"sub\":\"user1\",\"iss\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        JwtHeader header = new JwtHeader("HS256");

        String token = JwtProcessor.sign(header, claims, key);
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);

        byte[] payload = JwtProcessor.verify(token, key);
        assertThat(payload).isNotNull();
        assertThat(new String(payload, StandardCharsets.UTF_8)).isEqualTo("{\"sub\":\"user1\",\"iss\":\"test\"}");
    }

    @Test
    void hmacVerifyWithWrongKeyReturnsNull() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("HmacSHA256");
        SecretKey key1 = kg.generateKey();
        SecretKey key2 = kg.generateKey();
        byte[] claims = "{\"sub\":\"user1\"}".getBytes(StandardCharsets.UTF_8);

        String token = JwtProcessor.sign(new JwtHeader("HS256"), claims, key1);
        assertThat(JwtProcessor.verify(token, key2)).isNull();
    }

    // ----------------------------------------------------------- Ed25519

    @Test
    void ed25519SignAndVerifyRoundTrip() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] claims = "{\"sub\":\"ed25519-user\"}".getBytes(StandardCharsets.UTF_8);
        JwtHeader header = new JwtHeader("EdDSA");

        String token = JwtProcessor.sign(header, claims, kp.getPrivate());
        byte[] payload = JwtProcessor.verify(token, kp.getPublic());
        assertThat(payload).isNotNull();
        assertThat(new String(payload, StandardCharsets.UTF_8)).contains("ed25519-user");
    }

    // ----------------------------------------------------------- ECDSA

    @Test
    void ecdsaP256SignAndVerifyRoundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        byte[] claims = "{\"sub\":\"ec-user\"}".getBytes(StandardCharsets.UTF_8);

        String token = JwtProcessor.sign(new JwtHeader("ES256"), claims, kp.getPrivate());
        byte[] payload = JwtProcessor.verify(token, kp.getPublic());
        assertThat(payload).isNotNull();
    }

    // ----------------------------------------------------------- RSA

    @Test
    void rsaSha256SignAndVerifyRoundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        byte[] claims = "{\"sub\":\"rsa-user\"}".getBytes(StandardCharsets.UTF_8);

        String token = JwtProcessor.sign(new JwtHeader("RS256"), claims, kp.getPrivate());
        byte[] payload = JwtProcessor.verify(token, kp.getPublic());
        assertThat(payload).isNotNull();
    }

    // ----------------------------------------------------------- alg:none rejection

    @Test
    void algNoneIsRejected() {
        assertThatThrownBy(() -> JwsAlgorithm.fromJwsName("none"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("none");
    }

    // ----------------------------------------------------------- time claims

    @Test
    void expiredTokenReturnsNull() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        long pastExp = Instant.now().getEpochSecond() - 60;
        byte[] claims = ("{\"sub\":\"user1\",\"exp\":" + pastExp + "}").getBytes(StandardCharsets.UTF_8);

        String token = JwtProcessor.sign(new JwtHeader("HS256"), claims, key);
        assertThat(JwtProcessor.verify(token, key)).isNull();
    }

    @Test
    void notYetValidTokenReturnsNull() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        long futureNbf = Instant.now().getEpochSecond() + 3600;
        byte[] claims = ("{\"sub\":\"user1\",\"nbf\":" + futureNbf + "}").getBytes(StandardCharsets.UTF_8);

        String token = JwtProcessor.sign(new JwtHeader("HS256"), claims, key);
        assertThat(JwtProcessor.verify(token, key)).isNull();
    }

    @Test
    void validExpAndNbfTokenSucceeds() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        long now = Instant.now().getEpochSecond();
        byte[] claims = ("{\"sub\":\"user1\",\"exp\":" + (now + 3600) + ",\"nbf\":" + (now - 60) + "}")
                .getBytes(StandardCharsets.UTF_8);

        String token = JwtProcessor.sign(new JwtHeader("HS256"), claims, key);
        assertThat(JwtProcessor.verify(token, key)).isNotNull();
    }

    // ----------------------------------------------------------- malformed tokens

    @Test
    void nullTokenReturnsNull() {
        assertThat(JwtProcessor.verify((String) null, (java.security.Key) null)).isNull();
    }

    @Test
    void malformedTokenTooFewPartsReturnsNull() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        assertThat(JwtProcessor.verify("only.two", key)).isNull();
    }

    @Test
    void tamperedPayloadReturnsNull() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        byte[] claims = "{\"sub\":\"user1\"}".getBytes(StandardCharsets.UTF_8);
        String token = JwtProcessor.sign(new JwtHeader("HS256"), claims, key);

        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." +
                java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString("{\"sub\":\"hacker\"}".getBytes()) +
                "." + parts[2];
        assertThat(JwtProcessor.verify(tampered, key)).isNull();
    }

    // ----------------------------------------------------------- header decoding

    @Test
    void decodeHeaderExtractsFields() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        byte[] claims = "{}".getBytes(StandardCharsets.UTF_8);
        JwtHeader header = new JwtHeader("JWT", "HS256", "my-key-1");

        String token = JwtProcessor.sign(header, claims, key);
        JwtHeader decoded = JwtProcessor.decodeHeader(token);
        assertThat(decoded).isNotNull();
        assertThat(decoded.typ()).isEqualTo("JWT");
        assertThat(decoded.alg()).isEqualTo("HS256");
        assertThat(decoded.kid()).isEqualTo("my-key-1");
    }

    // ----------------------------------------------------------- custom deserializer

    @Test
    void verifyWithDeserializerReturnsConvertedPayload() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        byte[] claims = "{\"sub\":\"user1\"}".getBytes(StandardCharsets.UTF_8);

        String token = JwtProcessor.sign(new JwtHeader("HS256"), claims, key);
        String result = JwtProcessor.verify(token, key,
                bytes -> new String(bytes, StandardCharsets.UTF_8));
        assertThat(result).isEqualTo("{\"sub\":\"user1\"}");
    }
}
