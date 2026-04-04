package enkan.servlet.util;

import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.data.StreamingBody;
import enkan.exception.FalteringEnvironmentException;
import enkan.exception.UnreachableException;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author kawasima
 */
public class ServletUtils {
    private static Headers getHeaders(HttpServletRequest servletRequest) {
        Headers headers = Headers.empty();
        Enumeration<String> names = servletRequest.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            Enumeration<String> valueEnumeration = servletRequest.getHeaders(name);
            while (valueEnumeration.hasMoreElements()) {
                headers.put(name, valueEnumeration.nextElement());
            }
        }
        return headers;
    }

    private static Long getContentLength(HttpServletRequest servletRequest) {
        long length = servletRequest.getContentLengthLong();
        return length >= 0 ? length : null;
    }

    public static HttpRequest buildRequest(HttpServletRequest servletRequest) throws IOException {
        return buildRequest(servletRequest, DefaultHttpRequest::new);
    }

    public static HttpRequest buildRequest(HttpServletRequest servletRequest,
                                           Supplier<HttpRequest> requestFactory) throws IOException {
        HttpRequest request = requestFactory.get();
        request.setServerPort(servletRequest.getServerPort());
        request.setServerName(servletRequest.getServerName());
        request.setRemoteAddr(servletRequest.getRemoteAddr());
        request.setUri(servletRequest.getRequestURI());
        request.setQueryString(servletRequest.getQueryString());
        request.setHeaders(getHeaders(servletRequest));
        request.setScheme(servletRequest.getScheme());
        request.setRequestMethod(servletRequest.getMethod());
        request.setProtocol(servletRequest.getProtocol());
        request.setContentType(servletRequest.getContentType());
        request.setContentLength(getContentLength(servletRequest));
        request.setCharacterEncoding(servletRequest.getCharacterEncoding());
        request.setBody(servletRequest.getInputStream());
        return request;
    }

    private static void setHeaders(HttpServletResponse servletResponse, Headers headers) {
        headers.keySet().forEach(k -> {
            List<?> values = headers.getList(k);
            if (values == null) return;
            boolean first = true;
            for (Object v : values) {
                if (first) {
                    servletResponse.setHeader(k, Objects.toString(v));
                    first = false;
                } else {
                    servletResponse.addHeader(k, Objects.toString(v));
                }
            }
        });
    }

    private static void setBody(HttpServletResponse servletResponse, Object body) throws IOException {
        switch (body) {
            case null -> {
                // Do nothing
            }
            case String s -> {
                PrintWriter writer = servletResponse.getWriter();
                writer.print(s);
            }
            case InputStream input -> {
                try (ServletOutputStream output = servletResponse.getOutputStream()) {
                    byte[] buf = new byte[4096];
                    for (; ; ) {
                        int size = input.read(buf);
                        if (size <= 0) break;
                        output.write(buf, 0, size);
                    }
                }
            }
            case File file -> {
                try (InputStream in = new FileInputStream(file)) {
                    setBody(servletResponse, in);
                }
            }
            case StreamingBody streaming -> {
                servletResponse.flushBuffer();
                streaming.writeTo(servletResponse.getOutputStream());
            }
            default -> throw new UnreachableException();
        }
    }

    public static void updateServletResponse(HttpServletResponse servletResponse, HttpResponse response) {
        if (servletResponse == null || response == null) return;

        servletResponse.setStatus(response.getStatus());
        setHeaders(servletResponse, response.getHeaders());
        try {
            setBody(servletResponse, response.getBody());
        } catch (IOException ex) {
            throw new FalteringEnvironmentException(ex);
        }
    }
}
