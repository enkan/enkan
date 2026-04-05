package enkan.web.signature;

import enkan.security.crypto.Signer;
import enkan.security.crypto.Verifier;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.sf.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Static utility for RFC 9421 HTTP Message Signatures signing and verification.
 *
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9421">RFC 9421</a>
 */
public final class HttpMessageSignatures {

    private HttpMessageSignatures() {}

    /**
     * Verifies all signatures present on the request.
     *
     * <p>Parses {@code Signature} and {@code Signature-Input} headers, resolves
     * keys via the provided resolver, and returns a list of successfully verified
     * results. Signatures with unknown key IDs or failed verification are silently
     * skipped.
     *
     * @param request     the HTTP request
     * @param keyResolver resolves verifiers by key ID and algorithm
     * @return list of successfully verified signatures (may be empty)
     */
    public static List<VerifyResult> verifyAll(HttpRequest request, SignatureKeyResolver keyResolver) {
        String signatureHeader = request.getHeaders().get("Signature");
        String signatureInputHeader = request.getHeaders().get("Signature-Input");
        if (signatureHeader == null || signatureInputHeader == null) {
            return List.of();
        }

        SfDictionary signatureDict = StructuredFields.parseDictionary(signatureHeader);
        SfDictionary signatureInputDict = StructuredFields.parseDictionary(signatureInputHeader);

        List<VerifyResult> results = new ArrayList<>();
        for (var entry : signatureInputDict.members().entrySet()) {
            String label = entry.getKey();
            if (!(entry.getValue() instanceof SfInnerList innerList)) continue;

            // Extract algorithm and keyid from inner list parameters
            SfParameters sigParams = innerList.parameters();
            SfValue algValue = sigParams.get("alg");
            SfValue keyIdValue = sigParams.get("keyid");
            if (!(algValue instanceof SfValue.SfString algStr)) continue;
            if (!(keyIdValue instanceof SfValue.SfString keyIdStr)) continue;

            SignatureAlgorithm algorithm;
            try {
                algorithm = SignatureAlgorithm.fromSfName(algStr.value());
            } catch (IllegalArgumentException e) {
                continue;
            }

            // Validate time claims
            if (!validateTimeParams(sigParams)) continue;

            // Resolve verifier
            Optional<Verifier> verifier = keyResolver.resolveVerifier(keyIdStr.value(), algorithm);
            if (verifier.isEmpty()) continue;

            // Get signature bytes
            SfMember sigMember = signatureDict.members().get(label);
            if (!(sigMember instanceof SfItem sigItem
                    && sigItem.value() instanceof SfValue.SfByteSequence sigBytes)) continue;

            // Verify
            VerifyResult result = verify(request, label, innerList, sigBytes.value(), verifier.get());
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * Verifies a single signature on a request.
     *
     * @param request        the HTTP request
     * @param label          the signature label
     * @param signatureInput the parsed {@code Signature-Input} inner list for this label
     * @param signatureBytes the raw signature bytes from the {@code Signature} header
     * @param verifier       the cryptographic verifier
     * @return the verification result, or {@code null} if verification fails
     */
    public static VerifyResult verify(HttpRequest request, String label,
                                       SfInnerList signatureInput, byte[] signatureBytes,
                                       Verifier verifier) {
        return verify(request, null, label, signatureInput, signatureBytes, verifier);
    }

    /**
     * Verifies a single signature on a response.
     */
    public static VerifyResult verify(HttpRequest request, HttpResponse response,
                                       String label, SfInnerList signatureInput,
                                       byte[] signatureBytes, Verifier verifier) {
        List<SignatureComponent> components = signatureInput.items().stream()
                .map(SignatureComponent::fromSfItem)
                .toList();

        // Build covered values map
        Map<String, String> coveredValues = new LinkedHashMap<>();
        for (SignatureComponent comp : components) {
            try {
                coveredValues.put(comp.name(),
                        SignatureBaseBuilder.resolveComponentValue(request, response, comp));
            } catch (Exception e) {
                return null;
            }
        }

        String signatureBase = SignatureBaseBuilder.buildSignatureBase(
                request, response, components, signatureInput.parameters());

        if (!verifier.verify(signatureBase.getBytes(StandardCharsets.UTF_8), signatureBytes)) {
            return null;
        }

        // Extract metadata
        SfParameters params = signatureInput.parameters();
        String keyId = params.get("keyid") instanceof SfValue.SfString s ? s.value() : null;
        String algName = params.get("alg") instanceof SfValue.SfString s ? s.value() : null;
        SignatureAlgorithm algorithm = algName != null ? SignatureAlgorithm.fromSfName(algName) : null;

        return new VerifyResult(label, keyId, algorithm, Collections.unmodifiableMap(coveredValues));
    }

    /**
     * Creates a signature over the given request.
     *
     * @param request    the HTTP request
     * @param components the components to cover
     * @param algorithm  the signature algorithm
     * @param signer     the cryptographic signer
     * @param keyId      the key identifier
     * @param tag        optional tag parameter (may be {@code null})
     * @return the signature result containing header values to set
     */
    public static SignatureResult sign(HttpRequest request,
                                        List<SignatureComponent> components,
                                        SignatureAlgorithm algorithm,
                                        Signer signer, String keyId, String tag) {
        return sign(request, null, components, algorithm, signer, keyId, tag);
    }

    /**
     * Creates a signature over the given response.
     */
    public static SignatureResult sign(HttpRequest request, HttpResponse response,
                                        List<SignatureComponent> components,
                                        SignatureAlgorithm algorithm,
                                        Signer signer, String keyId, String tag) {
        Map<String, SfValue> paramMap = new LinkedHashMap<>();
        paramMap.put("alg", new SfValue.SfString(algorithm.sfName()));
        paramMap.put("keyid", new SfValue.SfString(keyId));
        paramMap.put("created", new SfValue.SfInteger(Instant.now().getEpochSecond()));
        if (tag != null) {
            paramMap.put("tag", new SfValue.SfString(tag));
        }
        SfParameters params = new SfParameters(paramMap);

        String signatureBase = SignatureBaseBuilder.buildSignatureBase(
                request, response, components, params);
        byte[] sig = signer.sign(signatureBase.getBytes(StandardCharsets.UTF_8));

        String signatureInputValue = SignatureBaseBuilder.serializeSignatureParams(components, params);
        String signatureValue = StructuredFields.serializeItem(
                new SfItem(new SfValue.SfByteSequence(sig)));

        return new SignatureResult(signatureInputValue, signatureValue);
    }

    /**
     * Result of signing an HTTP message, containing the header values
     * to add to the {@code Signature-Input} and {@code Signature} dictionaries.
     */
    public record SignatureResult(String signatureInputValue, String signatureValue) {}

    // -------------------------------------------------------------------------
    // Time validation
    // -------------------------------------------------------------------------

    private static boolean validateTimeParams(SfParameters params) {
        long now = Instant.now().getEpochSecond();
        SfValue created = params.get("created");
        if (created instanceof SfValue.SfInteger c && c.value() > now) {
            return false;
        }
        SfValue expires = params.get("expires");
        if (expires instanceof SfValue.SfInteger e && e.value() <= now) {
            return false;
        }
        return true;
    }
}
