package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.signature.*;
import enkan.web.util.sf.*;

import java.util.*;

import static enkan.util.BeanBuilder.builder;

/**
 * Middleware that verifies RFC 9421 HTTP Message Signatures on incoming requests.
 *
 * <p>When verification succeeds, the {@link VerifyResult} list is stored as a
 * request extension ({@code "signatureVerifyResults"}) so downstream handlers
 * can access the cryptographically verified component values.
 *
 * <p>When {@code acceptSignatureLabel} is configured, 401 responses include an
 * {@code Accept-Signature} header telling the client what signature parameters
 * are expected.
 *
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9421">RFC 9421</a>
 */
@Middleware(name = "signatureVerification")
public class SignatureVerificationMiddleware implements WebMiddleware {

    /** Request extension key for verified results. */
    public static final String EXTENSION_KEY = "signatureVerifyResults";

    private final SignatureKeyResolver keyResolver;
    private Set<String> requiredLabels = Set.of();
    private Set<String> requiredComponents = Set.of();

    // Accept-Signature negotiation
    private String acceptSignatureLabel;
    private List<SignatureComponent> acceptComponents;
    private SignatureAlgorithm acceptAlgorithm;
    private String acceptKeyId;

    public SignatureVerificationMiddleware(SignatureKeyResolver keyResolver) {
        this.keyResolver = keyResolver;
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
                                              MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        String signatureHeader = request.getHeaders().get("Signature");
        String signatureInputHeader = request.getHeaders().get("Signature-Input");

        if (signatureHeader == null || signatureInputHeader == null) {
            if (!requiredLabels.isEmpty() || !requiredComponents.isEmpty()) {
                return errorResponse(401, "Missing Signature or Signature-Input header");
            }
            return chain.next(request);
        }

        List<VerifyResult> results;
        try {
            results = HttpMessageSignatures.verifyAll(request, keyResolver);
        } catch (SfParseException e) {
            return errorResponse(400, "Invalid Signature-Input header");
        } catch (UnsupportedOperationException e) {
            return errorResponse(501, e.getMessage());
        }

        // Check required labels
        if (!requiredLabels.isEmpty()) {
            for (String label : requiredLabels) {
                boolean found = results.stream().anyMatch(r -> label.equals(r.label()));
                if (!found) {
                    return errorResponse(401, "Required signature label missing or invalid: " + label);
                }
            }
        }

        // Check required components coverage
        if (!requiredComponents.isEmpty()) {
            for (VerifyResult result : results) {
                for (String component : requiredComponents) {
                    if (!result.coveredValues().containsKey(component)) {
                        return errorResponse(401,
                                "Signature does not cover required component: " + component);
                    }
                }
            }
            // If there are required components but no valid signatures, reject
            if (results.isEmpty()) {
                return errorResponse(401, "No valid signature found");
            }
        }

        // If no required labels/components, at least one valid signature is needed
        if (requiredLabels.isEmpty() && requiredComponents.isEmpty() && results.isEmpty()) {
            return errorResponse(401, "No valid signature found");
        }

        request.setExtension(EXTENSION_KEY, results);
        return chain.next(request);
    }

    private HttpResponse errorResponse(int status, String message) {
        HttpResponse response = builder(HttpResponse.of(message))
                .set(HttpResponse::setStatus, status)
                .build();
        if (status == 401 && acceptSignatureLabel != null) {
            response.getHeaders().put("Accept-Signature", buildAcceptSignature());
        }
        return response;
    }

    private String buildAcceptSignature() {
        List<SfItem> items = acceptComponents.stream()
                .map(c -> new SfItem(new SfValue.SfString(c.name()), c.parameters()))
                .toList();
        Map<String, SfValue> paramMap = new LinkedHashMap<>();
        if (acceptAlgorithm != null) {
            paramMap.put("alg", new SfValue.SfString(acceptAlgorithm.sfName()));
        }
        if (acceptKeyId != null) {
            paramMap.put("keyid", new SfValue.SfString(acceptKeyId));
        }
        SfInnerList innerList = new SfInnerList(items, new SfParameters(paramMap));
        Map<String, SfMember> dict = new LinkedHashMap<>();
        dict.put(acceptSignatureLabel, innerList);
        return StructuredFields.serializeDictionary(new SfDictionary(dict));
    }

    // -------------------------------------------------------------------------
    // Configuration setters
    // -------------------------------------------------------------------------

    /**
     * Sets the signature labels that must be present and valid.
     * Empty means any valid signature is accepted.
     */
    public void setRequiredLabels(Set<String> requiredLabels) {
        this.requiredLabels = requiredLabels;
    }

    /**
     * Sets the component identifiers that must be covered by every valid signature.
     */
    public void setRequiredComponents(Set<String> requiredComponents) {
        this.requiredComponents = requiredComponents;
    }

    /**
     * Configures Accept-Signature negotiation. When set, 401 responses include
     * an {@code Accept-Signature} header with the specified parameters.
     */
    public void setAcceptSignature(String label, List<SignatureComponent> components,
                                   SignatureAlgorithm algorithm, String keyId) {
        this.acceptSignatureLabel = label;
        this.acceptComponents = components;
        this.acceptAlgorithm = algorithm;
        this.acceptKeyId = keyId;
    }
}
