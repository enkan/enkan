package enkan.web.signature;

import enkan.security.crypto.Signer;
import enkan.security.crypto.Verifier;
import enkan.web.collection.Headers;
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
     * results. Signatures with unknown key IDs, unknown algorithms, or failed
     * verification are silently skipped.
     *
     * <p>Time-based validation (when {@code created} / {@code expires} are present):
     * <ul>
     *   <li>{@code created} more than {@value #CLOCK_SKEW_SECONDS}s in the future → rejected</li>
     *   <li>{@code created} more than {@value #MAX_SIGNATURE_AGE_SECONDS}s in the past → rejected</li>
     *   <li>{@code expires} more than {@value #CLOCK_SKEW_SECONDS}s in the past → rejected</li>
     * </ul>
     *
     * @param request     the HTTP request
     * @param keyResolver resolves verifiers by key ID and algorithm
     * @return list of successfully verified signatures (may be empty)
     * @throws SfParseException if either signature header is structurally malformed (→ HTTP 400)
     * @throws UnsupportedOperationException if an unsupported feature (e.g. {@code ;bs}) is encountered (→ HTTP 501)
     */
    public static List<VerifyResult> verifyAll(HttpRequest request, SignatureKeyResolver keyResolver) {
        String signatureHeader = getHeaderForParsing(request.getHeaders(), "Signature");
        String signatureInputHeader = getHeaderForParsing(request.getHeaders(), "Signature-Input");
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
            } catch (UnsupportedOperationException e) {
                throw e; // propagate so callers can return 501
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
        SignatureAlgorithm algorithm;
        try {
            algorithm = algName != null ? SignatureAlgorithm.fromSfName(algName) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }

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
    // Header normalization
    // -------------------------------------------------------------------------

    /**
     * Retrieves a header value and normalizes it for SF parsing.
     *
     * <p>{@code Headers.get(String)} returns {@code val.toString()}, which for a
     * multi-value {@code List} produces {@code "[v1, v2]"} — not a valid SF string.
     * This method retrieves the raw object via the {@code Map<String,Object>} view
     * and joins list values with {@code ", "} before SF parsing.
     */
    private static String getHeaderForParsing(Headers headers, String name) {
        // Parameters.get() overrides Map.get() to return val.toString(), losing List structure.
        // Use entrySet() to access the raw Object value for proper multi-value joining.
        String lowerName = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (lowerName.equals(entry.getKey())) {
                Object val = entry.getValue();
                if (val instanceof List<?> list) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(list.get(i).toString().strip());
                    }
                    return sb.toString();
                }
                return val.toString();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Time validation
    // -------------------------------------------------------------------------

    /** Default clock skew tolerance in seconds. */
    private static final long CLOCK_SKEW_SECONDS = 60;

    /** Default maximum signature age in seconds (5 minutes). */
    private static final long MAX_SIGNATURE_AGE_SECONDS = 300;

    private static boolean validateTimeParams(SfParameters params) {
        long now = Instant.now().getEpochSecond();
        SfValue created = params.get("created");
        SfValue expires = params.get("expires");

        if (created instanceof SfValue.SfInteger c) {
            // Reject future-dated signatures (with clock skew tolerance)
            if (c.value() - CLOCK_SKEW_SECONDS > now) return false;
            // Reject stale signatures to limit replay window
            if (now - c.value() > MAX_SIGNATURE_AGE_SECONDS) return false;
            // Reject structurally invalid signatures where expires precedes created
            if (expires instanceof SfValue.SfInteger e && c.value() > e.value()) return false;
        }
        if (expires instanceof SfValue.SfInteger e) {
            // Reject expired signatures (with clock skew tolerance)
            if (e.value() + CLOCK_SKEW_SECONDS <= now) return false;
        }
        return true;
    }
}
