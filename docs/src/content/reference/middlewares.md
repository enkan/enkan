type=page
status=published
title=Middleware catalog | Enkan
~~~~~~

# Middleware catalog

## Core

enkan-core package has middlewares as follows:

### Authentication

AuthenticationMiddleware enables to authenticate a request using some backends.

#### Properties

|Name|Description|
|:-----|:----|
|backends|The list of authentication backends.|

#### Usage

```java
app.use(new AuthenticationMiddleware(backends));
```

### ServiceUnavailable

#### Usage

```java
app.use(new ServiceUnavailableMiddleware(serviceUnavailableEndpoint));
```
#### Properties

|Name    |Type    |Description|
|:-------|:-------|:-----------|
|endpoint|Endpoint|Returns the response when service is unavailable.|
## Web

enkan-web package has middlewares as follows:

### AbsoluteRedirects

`AbsoluteRedirectsMiddleware` rewrites the URL contains `Location` http response header to the absolute URL.

```
Location: /abc/123
    ↓
Location: http://myhost/abc/123
```

#### Usage

```java
app.use(new AbsoluteRedirectsMiddleware());
```

#### Properties

AbsoluteRedirectsMiddleware has no properties.

### AntiForgery

`AntiForgeryMiddleware` appends the token check for protecting from the CSRF attack.
When the request method isn't GET/HEAD/OPTIONS, AntiForgeryMiddleware validates the given token.
If the token is invalid, it returns the forbidden response.

Because `AntiForgeryMiddleware` requires Session, We recommend using `Conversation` instead of this.

#### Usage

```java
app.use(new AntiForgeryMiddleware());
```

#### Properties

AntiForgeryMiddleware has no properties.

### ContentNegotiation

Parses the `Accept` header and sets the value to a request.

#### Usage

```java
app.use(new ContentNegotiationMiddleware());
```

### ContentType

Adds the `Content-Type` header if the response does not contains a `Content-Type` header yet.

#### Usage

```java
app.use(new ContentTypeMiddleware());
```

### Conversation

Creates a conversation and save states related with its conversation to the conversation store.

#### Usage

```java
app.use(new ConversationMiddleware());
```

### Cookies

Parses `Cookie` header and sets the `Set-Cookie` header to the response.

#### Usage

```java
app.use(new CookiesMiddleware());
```

### DefaultCharset

Adds the default charset to the response.

#### Usage

```java
app.use(new DefaultCharsetMiddleware());
```

### Flash

Serializes and deserializes a flash value.

#### Usage

```java
app.use(new FlashMiddleware());
```

### IdleSessionTimeout

Enkan Session has no expires in default. This middleware append the feature of timeout to the session.

#### Usage

```java
app.use(new IdleSessionTimeoutMiddleware());
```

### MethodOverride

Override a request method.

#### Usage

```java
app.use(new MethodOverrideMiddleware());
```

### MultipartParams

Parses the multipart request.

#### Usage

```java
app.use(new MultipartParamsMiddleware());
```

### NestedParams

NestedParamsMiddleware enables to parse nested parameters like `foo[][bar]`. It requires ParamMiddleware.

#### Usage

```java
app.use(new NestedParamsMiddleware());
```

### Normalization

Normalize the parameter values. It's for trimming spaces or converting letter case.

#### Usage

```java
app.use(new NormalizationMiddleware());
```

### Params

ParamMiddleware enables to parse urlencoded query string and post body and set to a request object. 

#### Usage

```java
app.use(new ParamsMiddleware());
```

### Resources

Returns the asset file that is searched from classpath.

#### Usage

```java
app.use(new ResourceMiddleware());
```

#### Properties

|Name|Description|
|:-----|:----|
|rootPath|The path prefix. Default value is `public`|



### Session

SessionMiddleware enables to store/load session objects to/from in-memory stores.

#### Usage

```java
app.use(new SessionMiddleware());
```

#### Properties

|Name|Description|
|:-----|:----|
|cookieName|A name of cookie for session id. Default value is `enkan-session`|
|store|A storage for session.|


### Trace

Adds the response header for tracing using middlewares.

#### Usage

```java
app.use(new TraceMiddleware());
```

### SecurityHeaders

Applies a suite of security-related HTTP response headers in a single middleware (similar to [Helmet.js](https://helmetjs.github.io/) for Express).

All headers are enabled by default with safe values. Pass `null` to any setter to disable a specific header.

#### Default headers

| Header | Default value |
|:---|:---|
| `Content-Security-Policy` | `default-src 'self'` |
| `Strict-Transport-Security` | `max-age=15552000; includeSubDomains` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `SAMEORIGIN` |
| `X-XSS-Protection` | `0` (disabled — use CSP instead) |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Cross-Origin-Opener-Policy` | `same-origin` |
| `Cross-Origin-Resource-Policy` | `same-origin` |

#### Usage

```java
// All defaults
app.use(new SecurityHeadersMiddleware());

// Custom CSP, disable HSTS for local development
SecurityHeadersMiddleware sec = new SecurityHeadersMiddleware();
sec.setContentSecurityPolicy("default-src 'self'; img-src *");
sec.setStrictTransportSecurity(null); // disable
app.use(sec);
```

#### Properties

| Name | Description |
|:---|:---|
| `contentSecurityPolicy` | `Content-Security-Policy` value. `null` disables the header. |
| `strictTransportSecurity` | `Strict-Transport-Security` value. `null` disables the header. |
| `contentTypeOptions` | `X-Content-Type-Options` value. Default: `nosniff`. |
| `frameOptions` | `X-Frame-Options` value. Default: `SAMEORIGIN`. |
| `xssProtection` | `X-XSS-Protection` value. Default: `0`. |
| `referrerPolicy` | `Referrer-Policy` value. |
| `crossOriginOpenerPolicy` | `Cross-Origin-Opener-Policy` value. |
| `crossOriginResourcePolicy` | `Cross-Origin-Resource-Policy` value. |

## Kotowari

### ControllerInvoker

Invoke a controller method.

#### Usage

```java
app.use(new ControllerInvokerMiddleware());
```

### Form

Populate the request body to a Java bean object.

#### Usage

```java
app.use(new FormMiddleware());
```

#### Properties

`FormMiddleware` has no properties.

### RenderTemplate

Render HTML using a template. When a controller returns `TemplatedHttpResponse`, it has yet to be rendered.  

#### Usage

```java
app.use(new RenderTemplateMiddleware());
```

#### Properties

`RenderTemplateMiddleware` has no properties.

### Routing

Routes the request to a controller method.

#### Usage

```java
app.use(new RoutingMiddleware(routes));
```
#### Properties

|Name|Description|
|:-----|:----|
|routes|Defined routes|

### SerDes

Deserialize the request body and serializes the response body.

#### Usage

```java
app.use(new SerDesMiddleware());
```

#### Properties

|Name|Description|
|:-----|:----|
|bodyReaders||
|bodyWriters||
 

### Transaction

Manages database transactions around controller invocation.

#### Usage

```java
// requires enkan-component-doma2 dependency
app.use(new DomaTransactionMiddleware<>(config));
```

### ValidateBody

Validates the body object. If body object implements the `Validatable` interface, the error messages are set to it.

#### Usage

```java
app.use(new ValidateBodyMiddleware());
```

#### Properties

`ValidateBodyMiddleware` has no properties.

## Additional Middleware

### Cors

`CorsMiddleware` handles Cross-Origin Resource Sharing (CORS) headers.

#### Usage

```java
app.use(new CorsMiddleware());
```

### Forwarded

`ForwardedMiddleware` applies forwarded-address information from trusted reverse proxies.
It supports both the standard `Forwarded` header (RFC 7239) and the legacy `X-Forwarded-For`,
`X-Forwarded-Proto`, and `X-Forwarded-Host` headers. Headers are only trusted when the
direct connection's remote address matches a configured trusted proxy CIDR range,
preventing header spoofing by untrusted clients.

When headers are trusted, the middleware updates `remoteAddr`, `scheme`, and `serverName`
on the request.

#### Usage

```java
// Default — trusts loopback only (127.0.0.0/8 and ::1/128)
app.use(new ForwardedMiddleware());

// With a custom trusted proxy range
ForwardedMiddleware fw = new ForwardedMiddleware();
fw.setTrustedProxies(List.of("10.0.0.0/8", "172.16.0.0/12", "127.0.0.0/8", "::1/128"));
app.use(fw);
```

#### Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `trustedProxies` | `List<String>` | `["127.0.0.0/8", "::1/128"]` | CIDR ranges of trusted proxies |
| `preferStandard` | `boolean` | `true` | Prefer RFC 7239 `Forwarded` over legacy `X-Forwarded-*` when both are present |

### CacheControl

Sets `Cache-Control` response headers based on configurable rules.
A `staticPattern` regex distinguishes static assets from dynamic responses.
URIs matching the pattern receive the `staticDirective`; all others receive the `dynamicDirective`.
If a downstream handler already set `Cache-Control`, this middleware leaves it unchanged.

#### Usage

```java
CacheControlMiddleware cache = new CacheControlMiddleware();
cache.setStaticPattern(Pattern.compile("^/assets/"));
cache.setStaticMaxAge(Duration.ofDays(365));
cache.setDynamicDirective("no-cache");
app.use(cache);
```

#### Properties

| Name | Type | Description | Default |
|:---|:---|:---|:---|
| `staticPattern` | `Pattern` | Regex to identify static asset URIs | `null` (disabled) |
| `staticMaxAge` | `Duration` | Convenience setter: produces `public, max-age=N, immutable` | — |
| `staticDirective` | `String` | Full directive for static assets | `"public, max-age=31536000, immutable"` |
| `dynamicDirective` | `String` | Directive for non-static responses; `null` to omit | `"no-cache"` |

## jOOQ

### JooqDslContext

Stores a non-transactional `DSLContext` in the request extensions under the key `jooqDslContext`.
Stack `JooqTransactionMiddleware` after this when transactional support is needed.
Requires `JooqProvider` component.

#### Usage

```java
// requires enkan-component-jooq dependency
app.use(new JooqDslContextMiddleware());
```

### JooqTransaction

Wraps the downstream handler in a jOOQ transaction when the matched controller class or method is annotated with `@Transactional`.
Method-level annotation overrides class-level. Only `REQUIRED` propagation is supported.
Requires `JooqDslContextMiddleware` to be stacked before this middleware.

#### Usage

```java
app.use(new JooqDslContextMiddleware());
app.use(new JooqTransactionMiddleware());
```

## OpenTelemetry

### Tracing

Creates an OpenTelemetry `SERVER` span for each HTTP request.
Extracts W3C Trace Context (`traceparent`/`tracestate`) from incoming headers.
Records HTTP semantic convention attributes: method, URL path, query string, status code, server address/port, client address, and network protocol version.
Requires `OpenTelemetryComponent`.

#### Usage

```java
// requires enkan-component-opentelemetry dependency
app.use(new TracingMiddleware());
```
