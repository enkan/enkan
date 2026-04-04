package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.DigestFieldsUtils;
import enkan.web.util.sf.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

import static enkan.util.BeanBuilder.builder;

/**
 * Validates {@code Content-Digest} and {@code Repr-Digest} headers on incoming requests
 * per RFC 9530.
 *
 * <p>When a client sends one of these headers, this middleware reads and buffers the
 * request body, computes the digest, and compares it to the declared value.
 * A mismatch results in {@code 400 Bad Request}. If neither header is present,
 * the request passes through unmodified.
 *
 * <p>The request body is fully buffered in memory to allow digest computation.
 * This middleware is unsuitable for large streaming uploads; apply it selectively
 * via routing.
 *
 * <p>Algorithm negotiation: only {@code sha-256} and {@code sha-512} are supported.
 * Any entry in the digest header using an unsupported algorithm is silently ignored.
 * If all entries use unsupported algorithms the request passes through (the server
 * cannot verify what it does not understand — RFC 9530 §5.1).
 *
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9530">RFC 9530</a>
 */
@Middleware(name = "digestValidation")
public class DigestValidationMiddleware implements WebMiddleware {

    private enum VerifyResult {
        OK,
        MISMATCH,
        MALFORMED
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
                                              MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        String contentDigestHeader = request.getHeaders().get("Content-Digest");
        String reprDigestHeader    = request.getHeaders().get("Repr-Digest");

        if (contentDigestHeader == null && reprDigestHeader == null) {
            return chain.next(request);
        }

        // Buffer the body for digest computation, then restore it for downstream.
        byte[] body;
        try {
            InputStream bodyStream = request.getBody();
            body = (bodyStream == null) ? new byte[0] : bodyStream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read request body for digest validation", e);
        }
        request.setBody(new ByteArrayInputStream(body));

        // Validate whichever digest headers are present.
        // RFC 9530 §5.1: Content-Digest and Repr-Digest are equivalent for
        // representations without content-coding, so we check both independently.
        if (contentDigestHeader != null) {
            VerifyResult result = verifyDigest(body, contentDigestHeader);
            if (result != VerifyResult.OK) {
                return builder(HttpResponse.of(errorMessage("Content-Digest", result)))
                        .set(HttpResponse::setStatus, 400)
                        .build();
            }
        }
        if (reprDigestHeader != null) {
            VerifyResult result = verifyDigest(body, reprDigestHeader);
            if (result != VerifyResult.OK) {
                return builder(HttpResponse.of(errorMessage("Repr-Digest", result)))
                        .set(HttpResponse::setStatus, 400)
                        .build();
            }
        }

        return chain.next(request);
    }

    private static String errorMessage(String headerName, VerifyResult result) {
        return switch (result) {
            case MALFORMED -> "Invalid " + headerName + " header";
            case MISMATCH  -> headerName + " mismatch";
            case OK        -> throw new AssertionError("should not build error message for OK");
        };
    }

    /**
     * Verifies the body against every supported-algorithm entry in the SF Dictionary
     * digest header. Returns {@link VerifyResult#OK} only if every such entry matches
     * (i.e. all present supported algorithms agree). Returns {@link VerifyResult#OK}
     * also when no supported algorithm is present — the server cannot verify what it
     * does not understand, so the request passes through (RFC 9530 §5.1).
     */
    private static VerifyResult verifyDigest(byte[] body, String digestHeaderValue) {
        SfDictionary dict;
        try {
            dict = StructuredFields.parseDictionary(digestHeaderValue);
        } catch (SfParseException e) {
            return VerifyResult.MALFORMED;
        }

        for (var entry : dict.members().entrySet()) {
            String algorithm = entry.getKey();
            if (!DigestFieldsUtils.SUPPORTED_ALGORITHMS.contains(algorithm)) {
                continue;
            }
            if (!(entry.getValue() instanceof SfItem item &&
                  item.value() instanceof SfValue.SfByteSequence bs)) {
                return VerifyResult.MALFORMED;
            }
            byte[] expected = bs.value();
            byte[] actual = computeRaw(body, algorithm);
            if (!Arrays.equals(expected, actual)) {
                return VerifyResult.MISMATCH;
            }
        }
        return VerifyResult.OK;
    }

    private static byte[] computeRaw(byte[] data, String algorithm) {
        try {
            var digest = java.security.MessageDigest.getInstance(
                    DigestFieldsUtils.toJcaAlgorithm(algorithm));
            return digest.digest(data);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(algorithm + " not available", e);
        }
    }
}
