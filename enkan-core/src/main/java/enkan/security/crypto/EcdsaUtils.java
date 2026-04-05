package enkan.security.crypto;

/**
 * Converts ECDSA signatures between DER encoding (used by JCA) and
 * IEEE P1363 / raw R||S format (used by JWS RFC 7518 §3.4 and other protocols).
 *
 * <p>All methods are pure byte-array operations with no external dependencies.
 *
 * @author kawasima
 */
public final class EcdsaUtils {

    private EcdsaUtils() {}

    /**
     * Converts a DER-encoded ECDSA signature to raw R||S (IEEE P1363) format.
     *
     * <p>Each component (R, S) is zero-padded to {@code (keyBits+7)/8} bytes.
     *
     * @param der     the DER-encoded signature (SEQUENCE { INTEGER r, INTEGER s })
     * @param keyBits the EC key size in bits (e.g. 256, 384, 521)
     * @return the raw R||S signature, or {@code null} if the input is malformed
     */
    public static byte[] derToP1363(byte[] der, int keyBits) {
        int componentLen = (keyBits + 7) / 8;
        if (der == null || der.length < 8) return null;
        if (der[0] != 0x30) return null; // must be SEQUENCE tag

        int pos = 1;
        int seqLenByte = der[pos++] & 0xff;
        if ((seqLenByte & 0x80) != 0) {
            int lenLen = seqLenByte & 0x7f;
            if (pos + lenLen > der.length) return null;
            pos += lenLen;
        }
        if (pos + 2 > der.length) return null;

        // Parse r
        if (pos >= der.length || der[pos] != 0x02) return null; // must be INTEGER tag
        pos++;
        if (pos >= der.length) return null;
        int rLenByte = der[pos++] & 0xff;
        int rLen;
        if ((rLenByte & 0x80) != 0) {
            int lenLen = rLenByte & 0x7f;
            if (lenLen == 0 || pos + lenLen > der.length) return null;
            rLen = 0;
            for (int k = 0; k < lenLen; k++) rLen = (rLen << 8) | (der[pos++] & 0xff);
        } else {
            rLen = rLenByte;
        }
        if (pos + rLen + 2 > der.length) return null;
        int rSrc = pos;
        pos += rLen;

        // Parse s
        if (pos >= der.length || der[pos] != 0x02) return null; // must be INTEGER tag
        pos++;
        if (pos >= der.length) return null;
        int sLenByte = der[pos++] & 0xff;
        int sLen;
        if ((sLenByte & 0x80) != 0) {
            int lenLen = sLenByte & 0x7f;
            if (lenLen == 0 || pos + lenLen > der.length) return null;
            sLen = 0;
            for (int k = 0; k < lenLen; k++) sLen = (sLen << 8) | (der[pos++] & 0xff);
        } else {
            sLen = sLenByte;
        }
        if (pos + sLen > der.length) return null;
        int sSrc = pos;

        byte[] result = new byte[componentLen * 2];

        // Copy r right-aligned, stripping leading 0x00 padding
        int rSkip = rLen > componentLen ? rLen - componentLen : 0;
        for (int j = 0; j < rSkip; j++) {
            if (der[rSrc + j] != 0x00) return null;
        }
        int rCopy = rLen - rSkip;
        System.arraycopy(der, rSrc + rSkip, result, componentLen - rCopy, rCopy);

        // Copy s right-aligned, stripping leading 0x00 padding
        int sSkip = sLen > componentLen ? sLen - componentLen : 0;
        for (int j = 0; j < sSkip; j++) {
            if (der[sSrc + j] != 0x00) return null;
        }
        int sCopy = sLen - sSkip;
        System.arraycopy(der, sSrc + sSkip, result, componentLen * 2 - sCopy, sCopy);
        return result;
    }

    /**
     * Converts a raw R||S (IEEE P1363) ECDSA signature to DER encoding.
     *
     * @param p1363 the raw R||S signature (must have even length)
     * @return the DER-encoded signature, or {@code null} if the input is malformed
     */
    public static byte[] p1363ToDer(byte[] p1363) {
        if (p1363 == null || p1363.length == 0 || (p1363.length % 2) != 0) {
            return null;
        }
        int half = p1363.length / 2;

        // Strip leading zeros and compute DER integer lengths
        int rOff = 0;
        while (rOff < half - 1 && p1363[rOff] == 0) rOff++;
        boolean rPad = (p1363[rOff] & 0x80) != 0;
        int rDerLen = half - rOff + (rPad ? 1 : 0);

        int sOff = half;
        while (sOff < p1363.length - 1 && p1363[sOff] == 0) sOff++;
        boolean sPad = (p1363[sOff] & 0x80) != 0;
        int sDerLen = p1363.length - sOff + (sPad ? 1 : 0);

        int contentLen = 2 + rDerLen + 2 + sDerLen;
        // DER length encoding: short-form (0x00-0x7f), or long-form 0x81/<1 byte> or 0x82/<2 bytes>
        int lenFieldSize = contentLen <= 0x7f ? 1 : contentLen <= 0xff ? 2 : 3;
        byte[] der = new byte[1 + lenFieldSize + contentLen];

        int i = 0;
        der[i++] = 0x30; // SEQUENCE tag
        if (contentLen <= 0x7f) {
            der[i++] = (byte) contentLen;
        } else if (contentLen <= 0xff) {
            der[i++] = (byte) 0x81;
            der[i++] = (byte) contentLen;
        } else {
            der[i++] = (byte) 0x82;
            der[i++] = (byte) (contentLen >> 8);
            der[i++] = (byte) contentLen;
        }

        der[i++] = 0x02; // INTEGER tag for r
        der[i++] = (byte) rDerLen;
        if (rPad) der[i++] = 0x00;
        System.arraycopy(p1363, rOff, der, i, half - rOff);
        i += half - rOff;

        der[i++] = 0x02; // INTEGER tag for s
        der[i++] = (byte) sDerLen;
        if (sPad) der[i++] = 0x00;
        System.arraycopy(p1363, sOff, der, i, p1363.length - sOff);

        return der;
    }
}
