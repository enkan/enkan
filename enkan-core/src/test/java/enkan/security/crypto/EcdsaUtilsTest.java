package enkan.security.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;

class EcdsaUtilsTest {

    // ----------------------------------------------------------- null / empty / malformed

    @Test
    void derToP1363ReturnsNullForNull() {
        assertThat(EcdsaUtils.derToP1363(null, 256)).isNull();
    }

    @Test
    void derToP1363ReturnsNullForEmpty() {
        assertThat(EcdsaUtils.derToP1363(new byte[0], 256)).isNull();
    }

    @Test
    void derToP1363ReturnsNullForTooShort() {
        assertThat(EcdsaUtils.derToP1363(new byte[]{0x30, 0x01, 0x02}, 256)).isNull();
    }

    @Test
    void derToP1363ReturnsNullForWrongSequenceTag() {
        // 0x31 instead of 0x30 SEQUENCE
        byte[] bad = new byte[]{0x31, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x01};
        assertThat(EcdsaUtils.derToP1363(bad, 256)).isNull();
    }

    @Test
    void derToP1363ReturnsNullForWrongIntegerTag() {
        // first INTEGER tag is 0x03 instead of 0x02
        byte[] bad = new byte[]{0x30, 0x06, 0x03, 0x01, 0x01, 0x02, 0x01, 0x01};
        assertThat(EcdsaUtils.derToP1363(bad, 256)).isNull();
    }

    @Test
    void p1363ToDerReturnsNullForNull() {
        assertThat(EcdsaUtils.p1363ToDer(null)).isNull();
    }

    @Test
    void p1363ToDerReturnsNullForEmpty() {
        assertThat(EcdsaUtils.p1363ToDer(new byte[0])).isNull();
    }

    @Test
    void p1363ToDerReturnsNullForOddLength() {
        assertThat(EcdsaUtils.p1363ToDer(new byte[]{1, 2, 3})).isNull();
    }

    // ----------------------------------------------------------- round-trip identity

    @Test
    void derToP1363ThenBackIsIdentityForP256() throws Exception {
        KeyPair kp = ecKeyPair("secp256r1");
        byte[] data = "test data".getBytes();
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(kp.getPrivate());
        sig.update(data);
        byte[] derSig = sig.sign();

        byte[] p1363 = EcdsaUtils.derToP1363(derSig, 256);
        assertThat(p1363).isNotNull().hasSize(64);

        byte[] backToDer = EcdsaUtils.p1363ToDer(p1363);
        assertThat(backToDer).isNotNull();

        // Verify with the round-tripped DER
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(kp.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(backToDer)).isTrue();
    }

    @Test
    void derToP1363ThenBackIsIdentityForP521() throws Exception {
        KeyPair kp = ecKeyPair("secp521r1");
        byte[] data = "test data".getBytes();
        Signature sig = Signature.getInstance("SHA512withECDSA");
        sig.initSign(kp.getPrivate());
        sig.update(data);
        byte[] derSig = sig.sign();

        byte[] p1363 = EcdsaUtils.derToP1363(derSig, 521);
        assertThat(p1363).isNotNull().hasSize(132);

        byte[] backToDer = EcdsaUtils.p1363ToDer(p1363);
        assertThat(backToDer).isNotNull();

        Signature verifier = Signature.getInstance("SHA512withECDSA");
        verifier.initVerify(kp.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(backToDer)).isTrue();
    }

    // ----------------------------------------------------------- high-bit padding

    @Test
    void p1363ToDerAddsLeadingZeroPadWhenHighBitSet() {
        // r = 0x80 (high bit set), s = 0x01
        byte[] p1363 = new byte[4];
        p1363[0] = (byte) 0x80;
        p1363[1] = 0x01;
        p1363[2] = 0x00;
        p1363[3] = 0x01;

        byte[] der = EcdsaUtils.p1363ToDer(p1363);
        assertThat(der).isNotNull();
        // r should have a 0x00 pad before 0x80
        assertThat(der[0]).isEqualTo((byte) 0x30); // SEQUENCE
        assertThat(der[2]).isEqualTo((byte) 0x02); // INTEGER
        assertThat(der[3]).isEqualTo((byte) 0x03); // r length = 3 (pad + 0x80 + 0x01)
        assertThat(der[4]).isEqualTo((byte) 0x00); // pad
        assertThat(der[5]).isEqualTo((byte) 0x80);
    }

    // ----------------------------------------------------------- helpers

    private static KeyPair ecKeyPair(String curve) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec(curve));
        return kpg.generateKeyPair();
    }
}
