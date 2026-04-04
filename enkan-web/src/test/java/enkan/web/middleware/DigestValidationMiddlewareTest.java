package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.predicate.AnyPredicate;
import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.DigestFieldsUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class DigestValidationMiddlewareTest {

    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain;

    @BeforeEach
    void setup() {
        chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("ok")).build());
    }

    // ----------------------------------------------------------- no headers: pass-through

    @Test
    void noDigestHeaderPassesThrough() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.empty())
                .set(HttpRequest::setBody, new ByteArrayInputStream("body".getBytes()))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ----------------------------------------------------------- Content-Digest

    @Test
    void validContentDigestPasses() {
        byte[] body = "hello world".getBytes();
        String header = DigestFieldsUtils.computeDigestHeader(body, "sha-256");

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Digest", header))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tamperedBodyFailsContentDigest() {
        byte[] originalBody = "hello world".getBytes();
        String header = DigestFieldsUtils.computeDigestHeader(originalBody, "sha-256");

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Digest", header))
                .set(HttpRequest::setBody, new ByteArrayInputStream("tampered body".getBytes()))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) response.getBody()).contains("Content-Digest");
    }

    // ----------------------------------------------------------- Repr-Digest

    @Test
    void validReprDigestPasses() {
        byte[] body = "hello world".getBytes();
        String header = DigestFieldsUtils.computeDigestHeader(body, "sha-256");

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Repr-Digest", header))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tamperedBodyFailsReprDigest() {
        byte[] originalBody = "hello world".getBytes();
        String header = DigestFieldsUtils.computeDigestHeader(originalBody, "sha-512");

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Repr-Digest", header))
                .set(HttpRequest::setBody, new ByteArrayInputStream("tampered".getBytes()))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) response.getBody()).contains("Repr-Digest");
    }

    // ----------------------------------------------------------- body restoration

    @Test
    void bodyIsRestoredAfterValidation() throws Exception {
        byte[] body = "hello world".getBytes();
        String header = DigestFieldsUtils.computeDigestHeader(body, "sha-256");

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> capturingChain =
                new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                        (Endpoint<HttpRequest, HttpResponse>) req -> {
                            try {
                                byte[] consumed = req.getBody().readAllBytes();
                                return builder(HttpResponse.of(new String(consumed))).build();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Digest", header))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, capturingChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat((String) response.getBody()).isEqualTo("hello world");
    }

    // ----------------------------------------------------------- null body

    @Test
    void nullBodyWithDigestHeaderTreatedAsEmpty() {
        byte[] empty = new byte[0];
        String header = DigestFieldsUtils.computeDigestHeader(empty, "sha-256");

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Digest", header))
                // body is null
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ----------------------------------------------------------- unsupported algorithm

    @Test
    void unsupportedAlgorithmOnlyPassesThrough() {
        // Header present but only unsupported algorithm — cannot verify, pass through
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Digest", "md5=:abc:"))
                .set(HttpRequest::setBody, new ByteArrayInputStream("body".getBytes()))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ----------------------------------------------------------- malformed header

    @Test
    void malformedDigestHeaderReturns400() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Digest", "not valid SF !!!"))
                .set(HttpRequest::setBody, new ByteArrayInputStream("body".getBytes()))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    // ----------------------------------------------------------- sha-512

    @Test
    void sha512ValidationPasses() {
        byte[] body = "sha-512 test".getBytes();
        String header = DigestFieldsUtils.computeDigestHeader(body, "sha-512");

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Digest", header))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ----------------------------------------------------------- both headers

    @Test
    void bothHeadersPresentAndValidPasses() {
        byte[] body = "both headers".getBytes();
        String contentHeader = DigestFieldsUtils.computeDigestHeader(body, "sha-256");
        String reprHeader    = DigestFieldsUtils.computeDigestHeader(body, "sha-512");

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "Content-Digest", contentHeader,
                        "Repr-Digest", reprHeader))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void contentDigestValidButReprDigestInvalidReturns400() {
        byte[] body = "both headers".getBytes();
        String goodContent = DigestFieldsUtils.computeDigestHeader(body, "sha-256");
        String badRepr = DigestFieldsUtils.computeDigestHeader("wrong".getBytes(), "sha-256");

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "Content-Digest", goodContent,
                        "Repr-Digest", badRepr))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body))
                .build();

        HttpResponse response = new DigestValidationMiddleware().handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(400);
    }
}
