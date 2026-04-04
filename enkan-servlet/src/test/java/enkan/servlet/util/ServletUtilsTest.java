package enkan.servlet.util;

import enkan.web.collection.Headers;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.data.StreamingBody;
import enkan.exception.UnreachableException;
import org.junit.jupiter.api.Test;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.*;

/**
 * @author kawasima
 */
class ServletUtilsTest {

    // ---------------------------------------------------------------- Stub helpers

    /**
     * Creates an HttpServletRequest stub backed by a Map.
     * Only methods actually called by ServletUtils.buildRequest() need entries.
     */
    private static HttpServletRequest stubRequest(Map<String, Object> props) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{ HttpServletRequest.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (props.containsKey(name)) {
                        Object val = props.get(name);
                        // Dispatch getHeaders(String) to the StubHeaderProvider
                        if (val instanceof StubHeaderProvider provider && args != null && args.length == 1) {
                            return provider.getHeaders((String) args[0]);
                        }
                        return val;
                    }
                    // Sensible defaults for common methods
                    return switch (name) {
                        case "getServerPort" -> 0;
                        case "getContentLengthLong" -> -1L;
                        case "getHeaderNames" -> Collections.emptyEnumeration();
                        case "getInputStream" -> stubServletInputStream(new byte[0]);
                        default -> null;
                    };
                });
    }

    /**
     * Creates an HttpServletResponse stub that captures setStatus, setHeader, addHeader,
     * and provides a writer or output stream for body capture.
     */
    private static StubServletResponse stubResponse() {
        return new StubServletResponse();
    }

    private static class StubServletResponse {
        int status;
        boolean flushed;
        final Map<String, List<String>> headers = new LinkedHashMap<>();
        final StringWriter writerCapture = new StringWriter();
        final ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();

        HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{ HttpServletResponse.class },
                    (p, method, args) -> switch (method.getName()) {
                        case "setStatus" -> { status = (int) args[0]; yield null; }
                        case "setHeader" -> {
                            headers.put((String) args[0], new ArrayList<>(List.of((String) args[1])));
                            yield null;
                        }
                        case "addHeader" -> {
                            headers.computeIfAbsent((String) args[0], k -> new ArrayList<>())
                                    .add((String) args[1]);
                            yield null;
                        }
                        case "getHeaders" -> headers.getOrDefault(args[0], Collections.emptyList());
                        case "getWriter" -> new PrintWriter(writerCapture);
                        case "getOutputStream" -> new ServletOutputStream() {
                            @Override public boolean isReady() { return true; }
                            @Override public void setWriteListener(jakarta.servlet.WriteListener l) {}
                            @Override public void write(int b) { outputCapture.write(b); }
                            @Override public void write(byte[] b, int off, int len) { outputCapture.write(b, off, len); }
                        };
                        case "flushBuffer" -> { flushed = true; yield null; }
                        default -> null;
                    });
        }
    }

    private static ServletInputStream stubServletInputStream(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        return new ServletInputStream() {
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(jakarta.servlet.ReadListener l) {}
            @Override public int read() { return bais.read(); }
        };
    }

    // ---------------------------------------------------------------- buildRequest

    @Test
    void buildRequestMapsBasicFields() throws IOException {
        HttpServletRequest servletRequest = stubRequest(Map.ofEntries(
                Map.entry("getServerPort", 8080),
                Map.entry("getServerName", "localhost"),
                Map.entry("getRemoteAddr", "127.0.0.1"),
                Map.entry("getRequestURI", "/foo/bar"),
                Map.entry("getQueryString", "q=1"),
                Map.entry("getScheme", "http"),
                Map.entry("getMethod", "GET"),
                Map.entry("getProtocol", "HTTP/1.1"),
                Map.entry("getContentType", "application/json"),
                Map.entry("getContentLengthLong", 42L),
                Map.entry("getCharacterEncoding", "UTF-8")
        ));

        HttpRequest request = ServletUtils.buildRequest(servletRequest);

        assertThat(request.getServerPort()).isEqualTo(8080);
        assertThat(request.getServerName()).isEqualTo("localhost");
        assertThat(request.getRemoteAddr()).isEqualTo("127.0.0.1");
        assertThat(request.getUri()).isEqualTo("/foo/bar");
        assertThat(request.getQueryString()).isEqualTo("q=1");
        assertThat(request.getScheme()).isEqualTo("http");
        assertThat(request.getRequestMethod()).isEqualTo("GET");
        assertThat(request.getProtocol()).isEqualTo("HTTP/1.1");
        assertThat(request.getContentLength()).isEqualTo(42L);
        assertThat(request.getCharacterEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void buildRequestPreservesHttpMethodCase() throws IOException {
        HttpServletRequest servletRequest = stubRequest(Map.of(
                "getMethod", "POST",
                "getContentLengthLong", -1L
        ));

        HttpRequest request = ServletUtils.buildRequest(servletRequest);

        assertThat(request.getRequestMethod()).isEqualTo("POST");
    }

    @Test
    void buildRequestMapsHeaders() throws IOException {
        HttpServletRequest servletRequest = stubRequest(Map.of(
                "getMethod", "GET",
                "getContentLengthLong", -1L,
                "getHeaderNames", Collections.enumeration(List.of("Content-Type", "X-Custom")),
                "getHeaders", (StubHeaderProvider) name -> switch (name) {
                    case "Content-Type" -> Collections.enumeration(List.of("application/json"));
                    case "X-Custom" -> Collections.enumeration(List.of("value1"));
                    default -> Collections.emptyEnumeration();
                }
        ));

        HttpRequest request = ServletUtils.buildRequest(servletRequest);

        assertThat(request.getHeaders().getRawType("Content-Type")).isEqualTo("application/json");
        assertThat(request.getHeaders().getRawType("X-Custom")).isEqualTo("value1");
    }

    @Test
    void buildRequestStoresEachMultiValueHeaderSeparately() throws IOException {
        HttpServletRequest servletRequest = stubRequest(Map.of(
                "getMethod", "GET",
                "getContentLengthLong", -1L,
                "getHeaderNames", Collections.enumeration(List.of("Set-Cookie")),
                "getHeaders", (StubHeaderProvider) name ->
                        "Set-Cookie".equals(name)
                                ? Collections.enumeration(List.of("a=1", "b=2"))
                                : Collections.emptyEnumeration()
        ));

        HttpRequest request = ServletUtils.buildRequest(servletRequest);

        assertThat(request.getHeaders().getList("Set-Cookie")).containsExactly("a=1", "b=2");
    }

    @Test
    void buildRequestReturnsNullContentLengthWhenNegative() throws IOException {
        HttpServletRequest servletRequest = stubRequest(Map.of(
                "getMethod", "GET",
                "getContentLengthLong", -1L
        ));

        HttpRequest request = ServletUtils.buildRequest(servletRequest);

        assertThat(request.getContentLength()).isNull();
    }

    // --------------------------------------------------------- updateServletResponse

    @Test
    void updateServletResponseSetsStatusCode() throws IOException {
        StubServletResponse stub = stubResponse();

        HttpResponse response = HttpResponse.of("ok");
        response.setStatus(201);
        response.setHeaders(Headers.empty());

        ServletUtils.updateServletResponse(stub.proxy(), response);

        assertThat(stub.status).isEqualTo(201);
    }

    @Test
    void updateServletResponseSetsStringBody() throws IOException {
        StubServletResponse stub = stubResponse();

        HttpResponse response = builder(HttpResponse.of("hello"))
                .set(HttpResponse::setHeaders, Headers.empty())
                .build();

        ServletUtils.updateServletResponse(stub.proxy(), response);

        assertThat(stub.writerCapture.toString()).isEqualTo("hello");
    }

    @Test
    void updateServletResponseSetsInputStreamBody() throws IOException {
        StubServletResponse stub = stubResponse();

        byte[] bytes = "stream".getBytes(StandardCharsets.UTF_8);
        HttpResponse response = builder(HttpResponse.of(new ByteArrayInputStream(bytes)))
                .set(HttpResponse::setHeaders, Headers.empty())
                .build();

        ServletUtils.updateServletResponse(stub.proxy(), response);

        assertThat(stub.outputCapture.toString(StandardCharsets.UTF_8)).isEqualTo("stream");
    }

    @Test
    void updateServletResponseSetsFileBody() throws IOException {
        File tmp = File.createTempFile("servlet-test-", ".txt");
        tmp.deleteOnExit();
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write("file-content");
        }

        StubServletResponse stub = stubResponse();

        HttpResponse response = builder(HttpResponse.of(tmp))
                .set(HttpResponse::setHeaders, Headers.empty())
                .build();

        ServletUtils.updateServletResponse(stub.proxy(), response);

        assertThat(stub.outputCapture.toString(StandardCharsets.UTF_8)).isEqualTo("file-content");
    }

    @Test
    void updateServletResponseSetsStringHeader() throws IOException {
        StubServletResponse stub = stubResponse();

        HttpResponse response = builder(HttpResponse.of(""))
                .set(HttpResponse::setHeaders, Headers.of("AAA", "val"))
                .build();

        ServletUtils.updateServletResponse(stub.proxy(), response);

        assertThat(stub.headers).containsKey("Aaa");
        assertThat(stub.headers.get("Aaa")).containsExactly("val");
    }

    @Test
    void updateServletResponseSetsNumericHeader() throws IOException {
        StubServletResponse stub = stubResponse();

        HttpResponse response = builder(HttpResponse.of(""))
                .set(HttpResponse::setHeaders, Headers.of("AAA", 1))
                .build();

        ServletUtils.updateServletResponse(stub.proxy(), response);

        assertThat(stub.headers).containsKey("Aaa");
        assertThat(stub.headers.get("Aaa")).containsExactly("1");
    }

    @Test
    void updateServletResponseDoesNothingForNullArguments() {
        StubServletResponse stub = stubResponse();
        assertThatCode(() -> {
            ServletUtils.updateServletResponse(null, HttpResponse.of("ok"));
            ServletUtils.updateServletResponse(stub.proxy(), null);
        }).doesNotThrowAnyException();
    }

    @Test
    void updateServletResponseHandlesNullBody() throws IOException {
        StubServletResponse stub = stubResponse();
        HttpResponse response = builder(HttpResponse.of(""))
                .set(HttpResponse::setHeaders, Headers.empty())
                .build();
        // Clear all body fields — getBody() returns null
        response.setBody((String) null);

        ServletUtils.updateServletResponse(stub.proxy(), response);

        assertThat(stub.writerCapture.toString()).isEmpty();
        assertThat(stub.outputCapture.size()).isZero();
    }

    @Test
    void updateServletResponseStreamingBodyFlushesAndWrites() throws IOException {
        StubServletResponse stub = stubResponse();

        StreamingBody streaming = out -> out.write("streamed".getBytes(StandardCharsets.UTF_8));
        HttpResponse response = builder(HttpResponse.of(streaming))
                .set(HttpResponse::setHeaders, Headers.empty())
                .build();

        ServletUtils.updateServletResponse(stub.proxy(), response);

        assertThat(stub.flushed).as("flushBuffer should be called before writeTo").isTrue();
        assertThat(stub.outputCapture.toString(StandardCharsets.UTF_8)).isEqualTo("streamed");
    }

    // ---------------------------------------------------------------- Helper interface

    /**
     * Functional interface for the getHeaders(String) dispatcher in the stub.
     * Used because the Proxy needs to dispatch getHeaders calls per header name.
     */
    @FunctionalInterface
    private interface StubHeaderProvider {
        Enumeration<String> getHeaders(String name);
    }
}
