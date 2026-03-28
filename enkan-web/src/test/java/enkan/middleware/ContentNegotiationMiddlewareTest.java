package enkan.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.data.ContentNegotiable;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Stream;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class ContentNegotiationMiddlewareTest {
    private ContentNegotiationMiddleware middleware;
    private HttpRequest request;

    @BeforeEach
    void setup() {
        middleware = new ContentNegotiationMiddleware();
    }

    @Test
    void acceptLanguageWithoutAllowedLanguages() {
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders,
                        Headers.of("Accept-Language", "ja,en;q=0.8,en-US;q=0.6"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    Optional<Locale> locale = Stream.of(req)
                            .map(ContentNegotiable.class::cast)
                            .map(ContentNegotiable::getLocale)
                            .filter(Objects::nonNull)
                            .findFirst();

                    assertThat(locale).isNotPresent();
                    return builder(HttpResponse.of("hello"))
                            .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html"))
                            .build();
                });
        middleware.handle(request, chain);
    }

    @Test
    void acceptLanguage() {
        middleware.setAllowedLanguages(new HashSet<>(Collections.singletonList("ja")));
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders,
                        Headers.of("Accept-Language", "ja,en;q=0.8,en-US;q=0.6"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    Optional<Locale> locale = Stream.of(req)
                            .map(ContentNegotiable.class::cast)
                            .map(ContentNegotiable::getLocale)
                            .filter(Objects::nonNull)
                            .findFirst();

                    assertThat(locale).isPresent()
                            .get()
                            .isEqualTo(Locale.JAPANESE);
                    return builder(HttpResponse.of("hello"))
                            .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html"))
                            .build();
                });
        middleware.handle(request, chain);
    }

    @Test
    void acceptHtmlWhenAllowed() {
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders,
                        Headers.of("Accept", "text/html,application/xhtml+xml;q=0.9"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    MediaType mediaType = ((ContentNegotiable) req).getMediaType();
                    assertThat(mediaType).isNotNull();
                    assertThat(mediaType.getType()).isEqualTo("text");
                    assertThat(mediaType.getSubtype()).isEqualTo("html");
                    return HttpResponse.of("hello");
                });
        middleware.handle(request, chain);
    }

    @Test
    void acceptWildcardResolvesToAllowedType() {
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders,
                        Headers.of("Accept", "*/*"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    MediaType mediaType = ((ContentNegotiable) req).getMediaType();
                    assertThat(mediaType).isNotNull();
                    assertThat(mediaType.getType()).isEqualTo("text");
                    assertThat(mediaType.getSubtype()).isEqualTo("html");
                    return HttpResponse.of("hello");
                });
        middleware.handle(request, chain);
    }

    @Test
    void noAcceptHeaderDefaultsToAllowedType() {
        request = new DefaultHttpRequest();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    MediaType mediaType = ((ContentNegotiable) req).getMediaType();
                    assertThat(mediaType).isNotNull();
                    assertThat(mediaType.getType()).isEqualTo("text");
                    assertThat(mediaType.getSubtype()).isEqualTo("html");
                    return HttpResponse.of("hello");
                });
        middleware.handle(request, chain);
    }

    @Test
    void acceptMultipleTypesPicksHighestQuality() {
        middleware.setAllowedTypes(new HashSet<>(Arrays.asList("text/html", "application/json")));
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders,
                        Headers.of("Accept", "application/json;q=0.9,text/html;q=0.8"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    MediaType mediaType = ((ContentNegotiable) req).getMediaType();
                    assertThat(mediaType).isNotNull();
                    assertThat(mediaType.getType()).isEqualTo("application");
                    assertThat(mediaType.getSubtype()).isEqualTo("json");
                    return HttpResponse.of("hello");
                });
        middleware.handle(request, chain);
    }

    @Test
    void charsetAndEncodingNotNegotiatedByDefault() {
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "Accept", "text/html",
                        "Accept-Charset", "utf-8",
                        "Accept-Encoding", "gzip"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    ContentNegotiable cn = (ContentNegotiable) req;
                    assertThat(cn.getCharset()).isNull();
                    assertThat(cn.getEncoding()).isNull();
                    return HttpResponse.of("ok");
                });
        middleware.handle(request, chain);
    }

    @Test
    void charsetNegotiatedWhenConfigured() {
        middleware.setAllowedCharsets(Set.of("utf-8", "iso-8859-1"));
        // Give utf-8 a higher q to avoid tie with iso-8859-1's default q=1.0
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "Accept", "text/html",
                        "Accept-Charset", "utf-8, iso-8859-1;q=0.5"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    assertThat(((ContentNegotiable) req).getCharset()).isEqualTo("utf-8");
                    return HttpResponse.of("ok");
                });
        middleware.handle(request, chain);
    }

    @Test
    void encodingNegotiatedWhenConfigured() {
        middleware.setAllowedEncodings(Set.of("gzip", "deflate"));
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "Accept", "text/html",
                        "Accept-Encoding", "gzip"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    assertThat(((ContentNegotiable) req).getEncoding()).isEqualTo("gzip");
                    return HttpResponse.of("ok");
                });
        middleware.handle(request, chain);
    }

    @Test
    void charsetAndEncodingBothConfigured() {
        middleware.setAllowedCharsets(Set.of("utf-8"));
        middleware.setAllowedEncodings(Set.of("gzip"));
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "Accept", "text/html",
                        "Accept-Charset", "utf-8",
                        "Accept-Encoding", "gzip"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    ContentNegotiable cn = (ContentNegotiable) req;
                    assertThat(cn.getCharset()).isEqualTo("utf-8");
                    assertThat(cn.getEncoding()).isEqualTo("gzip");
                    return HttpResponse.of("ok");
                });
        middleware.handle(request, chain);
    }

    @Test
    void acceptLanguageWithRegionFallsBackToLanguage() {
        middleware.setAllowedLanguages(new HashSet<>(Collections.singletonList("en")));
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders,
                        Headers.of("Accept-Language", "en-US,en;q=0.9"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    Optional<Locale> locale = Stream.of(req)
                            .map(ContentNegotiable.class::cast)
                            .map(ContentNegotiable::getLocale)
                            .filter(Objects::nonNull)
                            .findFirst();
                    assertThat(locale).isPresent()
                            .get()
                            .isEqualTo(Locale.ENGLISH);
                    return HttpResponse.of("hello");
                });
        middleware.handle(request, chain);
    }
}