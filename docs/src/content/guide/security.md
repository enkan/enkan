type=page
status=published
title=Security | Enkan
~~~~~~

# Security

Enkan provides a layered security toolkit. Each feature is an independent
middleware or utility — compose only what your application needs.

---

## Security headers

`SecurityHeadersMiddleware` applies a safe set of HTTP response headers in one
middleware (similar to [Helmet.js](https://helmetjs.github.io/) for Express).
See the [middleware reference](../reference/middlewares.html#securityheaders) for
the full property list.

```java
app.use(new SecurityHeadersMiddleware());
```

---

## CSRF protection

`AntiForgeryMiddleware` validates a token on every non-safe (`POST`, `PUT`,
`PATCH`, `DELETE`) request. The token is stored in the session and must be
submitted as the `__anti-forgery-token` form field or `X-CSRF-Token` header.

```java
app.use(new SessionMiddleware());   // must be upstream
app.use(new AntiForgeryMiddleware());
```

For multi-step flows consider using `ConversationMiddleware` instead, which
provides built-in state protection without a separate CSRF token.

---

## CSP nonce

`CspNonceMiddleware` generates a fresh cryptographically random nonce
(128-bit, Base64url) for every request and injects it into the `script-src`
directive of `Content-Security-Policy`. Place it **before** (outer of)
`SecurityHeadersMiddleware` — it generates the nonce on the way in,
then rewrites the CSP header that `SecurityHeadersMiddleware` sets on the way out.

```java
CspNonceMiddleware nonce = new CspNonceMiddleware();
nonce.setStrictDynamic(true); // recommended for SPA-style inline bootstrapping
app.use(nonce);                           // outer (runs first)
app.use(new SecurityHeadersMiddleware()); // inner (runs after)
```

Read the nonce in a controller or template:

```java
String nonce = request.getExtension(CspNonceMiddleware.EXTENSION_KEY);
```

```html
<!-- Thymeleaf -->
<script th:nonce="${#request.getExtension('cspNonce')}">
  const data = /* ... */;
</script>
```

---

## Fetch Metadata

`FetchMetadataMiddleware` implements the W3C
[Resource Isolation Policy](https://www.w3.org/TR/fetch-metadata/) using
`Sec-Fetch-Site` and `Sec-Fetch-Mode` headers. It blocks cross-origin
non-navigational requests by default, defending against CSRF, XSSI, and
cross-origin information leaks.

```java
FetchMetadataMiddleware fm = new FetchMetadataMiddleware();
// Add paths that must be reachable from other origins (public APIs, webhooks)
fm.setAllowedPaths(Set.of("/api/public/items", "/webhooks/stripe"));
app.use(fm);
```

Browsers that do not send `Sec-Fetch-*` headers (non-browser clients, older
browsers) are always allowed through. Use alongside CSRF tokens for full coverage.

**CORS interaction:** if you use `CorsMiddleware`, CORS-enabled paths must also
appear in `allowedPaths` so that preflight (`OPTIONS`) requests reach
`CorsMiddleware` without being blocked first.

---

## JWT (JSON Web Tokens)

`JwtProcessor` provides stateless JWT signing and verification with no external
JSON library dependency. It guards against algorithm confusion attacks and
validates `exp` / `nbf` time claims with a 60-second clock skew tolerance.

### Signing

```java
import enkan.web.jwt.JwtProcessor;
import enkan.web.jwt.JwtHeader;
import enkan.web.jwt.JwsAlgorithm;

// Build the JOSE header
JwtHeader header = new JwtHeader("HS256", "my-key-id");

// Serialize your claims to JSON bytes however you like
byte[] claims = """
    {"sub":"user-42","exp":%d}
    """.formatted(Instant.now().plusSeconds(3600).getEpochSecond())
    .getBytes(StandardCharsets.UTF_8);

String token = JwtProcessor.sign(header, claims, secretKey);
```

### Verification

```java
// Most secure: ignore header alg, enforce expected algorithm explicitly
byte[] payload = JwtProcessor.verify(token, JwsAlgorithm.HS256, secretKey);
if (payload == null) {
    // token is invalid or expired
}

// With automatic deserialization
MyClaims claims = JwtProcessor.verify(token, secretKey,
    bytes -> jsonMapper.readValue(bytes, MyClaims.class));
```

### Supported algorithms

| Category | Values |
|----------|--------|
| HMAC | `HS256`, `HS384`, `HS512` |
| RSA PKCS#1 v1.5 | `RS256`, `RS384`, `RS512` |
| RSA-PSS | `PS256`, `PS384`, `PS512` |
| ECDSA | `ES256`, `ES384`, `ES512` |
| EdDSA | `EdDSA` |

### TokenBackend — JWT-based authentication

Combine `JwtProcessor` with the built-in `TokenBackend` to authenticate
requests via `Authorization: Bearer <token>`:

```java
TokenBackend<MyPrincipal> backend = new TokenBackend<>(token -> {
    byte[] payload = JwtProcessor.verify(token, JwsAlgorithm.HS256, secretKey);
    if (payload == null) return null;
    String sub = extractSubject(payload);
    return new MyPrincipal(sub);
});

app.use(new AuthenticationMiddleware(List.of(backend)));
```

---

## HTTP Message Signatures (RFC 9421)

`SignatureVerificationMiddleware` verifies
[RFC 9421](https://www.rfc-editor.org/rfc/rfc9421) HTTP Message Signatures on
incoming requests. Useful for webhook receivers, inter-service calls, and
open banking APIs.

### Implement a key resolver

```java
import enkan.web.signature.SignatureKeyResolver;
import enkan.web.signature.SignatureAlgorithm;
import enkan.security.crypto.Verifier;
import enkan.security.crypto.JcaVerifier;

SignatureKeyResolver keyResolver = (keyId, algorithm) -> {
    PublicKey key = keyStore.getPublicKey(keyId);  // your own key store
    if (key == null) return Optional.empty();
    return Optional.of(new JcaVerifier(algorithm.crypto(), key));
};
```

### Register the middleware

```java
SignatureVerificationMiddleware verify =
    new SignatureVerificationMiddleware(keyResolver);

// Only accept requests carrying a "sig1" label
verify.setRequiredLabels(Set.of("sig1"));

// That label must cover at least these components
verify.setRequiredComponents(Set.of("@method", "@path", "content-digest"));

// Advertise what we accept in 401 responses (Accept-Signature header)
verify.setAcceptSignature(
    "sig1",
    List.of(SignatureComponent.of("@method"),
            SignatureComponent.of("@path"),
            SignatureComponent.of("content-digest")),
    SignatureAlgorithm.ED25519,
    "my-public-key-id"
);

app.use(verify);
```

### Read verification results in a controller

```java
import enkan.web.middleware.SignatureVerificationMiddleware;
import enkan.web.signature.VerifyResult;

public HttpResponse handle(HttpRequest request) {
    List<VerifyResult> results =
        request.getExtension(SignatureVerificationMiddleware.EXTENSION_KEY);
    String keyId = results.get(0).keyId();
    // ...
}
```

### Supported signature algorithms

| Category | Values |
|----------|--------|
| HMAC | `hmac-sha256`, `hmac-sha384`, `hmac-sha512` |
| EdDSA | `ed25519` |
| ECDSA | `ecdsa-p256-sha256`, `ecdsa-p384-sha384`, `ecdsa-p521-sha512` |
| RSA-PSS | `rsa-pss-sha256`, `rsa-pss-sha384`, `rsa-pss-sha512` |
| RSA v1.5 | `rsa-v1_5-sha256`, `rsa-v1_5-sha384`, `rsa-v1_5-sha512` |

---

## Content Digest (RFC 9530)

`DigestValidationMiddleware` validates `Content-Digest` / `Repr-Digest` request
headers per [RFC 9530](https://www.rfc-editor.org/rfc/rfc9530). It buffers the
request body, computes the digest, and returns 400 on mismatch.

```java
// Apply only to routes that require integrity verification
app.use(new DigestValidationMiddleware(),
        PathPredicate.of("/api/events/*"));
```

On the server side (Jetty), digest headers can also be generated on responses:

```java
new JettyComponent().enableDigestFields("sha-256")
```

---

## Authentication and authorization

See the dedicated [Authentication guide](authentication.html) for:

- Session-based authentication (`SessionBackend`)
- Token-based authentication (`TokenBackend`)
- Custom backend implementations
- Role-based access control with `PermissionPredicate`

---

## Defense-in-depth checklist

Compose multiple middlewares for layered protection:

```java
// Typical middleware stack (outer → inner)
app.use(new ForwardedMiddleware());          // trust reverse proxy headers
app.use(new CspNonceMiddleware());           // generate nonce BEFORE SecurityHeaders writes CSP
app.use(new SecurityHeadersMiddleware());    // HSTS, CSP (nonce is injected by CspNonceMiddleware)
app.use(new FetchMetadataMiddleware());      // block cross-origin abuse
app.use(new CorsMiddleware());               // allow legitimate cross-origin APIs
app.use(new SessionMiddleware());
app.use(new AntiForgeryMiddleware());        // CSRF token check
app.use(new AuthenticationMiddleware(...)); // identify the user
// ... routing and controller invocation
```
