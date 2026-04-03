package enkan.data;

import enkan.collection.Headers;
import enkan.collection.Multimap;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A default implementation for HTTP response
 *
 * @author kawasima
 */
public class DefaultHttpResponse implements HttpResponse {
    private int status;
    private Headers headers;
    private Multimap<String, Cookie> cookies;
    private Session session;
    private String bodyString;
    private InputStream bodyStream;
    private File bodyFile;
    private StreamingBody bodyStreaming;

    private final Map<String, Object> extensions;

    protected DefaultHttpResponse(int status, Headers headers) {
        this.status = status;
        this.headers = headers;
        this.cookies = Multimap.empty();
        this.extensions = new HashMap<>();
        this.session = new PersistentMarkedSession();
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public Headers getHeaders() {
        return headers;
    }

    @Override
    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

    @Override
    public Multimap<String, Cookie> getCookies() {
        return cookies;
    }

    @Override
    public void setCookies(Multimap<String, Cookie> cookies) {
        this.cookies = cookies;
    }

    @Override
    public Object getBody() {
        if (bodyStreaming != null) {
            return bodyStreaming;
        } else if (bodyString != null) {
            return bodyString;
        } else if (bodyStream != null) {
            return bodyStream;
        } else if (bodyFile != null) {
            return bodyFile;
        } else {
            return null;
        }
    }

    @Override
    public InputStream getBodyAsStream() {
        if (bodyStream != null) {
            return bodyStream;
        } else if (bodyString != null) {
            return new ByteArrayInputStream(bodyString.getBytes(StandardCharsets.UTF_8));
        } else if (bodyFile != null) {
            try {
                return new FileInputStream(bodyFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    @Override
    public String getBodyAsString() {
        if (bodyStream != null) {
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else if (bodyString != null) {
            return bodyString;
        } else if (bodyFile != null) {
            try(BufferedReader reader = new BufferedReader(new FileReader(bodyFile))) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return "";
        }
    }

    @Override
    public void setBody(String body) {
        this.bodyString = body;
        bodyStream = null;
        bodyFile = null;
        bodyStreaming = null;
    }

    @Override
    public void setBody(InputStream body) {
        this.bodyStream = body;
        bodyString = null;
        bodyFile = null;
        bodyStreaming = null;
    }

    @Override
    public void setBody(File body) {
        this.bodyFile = body;
        bodyString = null;
        bodyStream = null;
        bodyStreaming = null;
    }

    @Override
    public void setBody(StreamingBody body) {
        this.bodyStreaming = body;
        bodyString = null;
        bodyStream = null;
        bodyFile = null;
    }

    @Override
    public String toString() {
        String b;
        if (bodyStreaming != null) {
            b = bodyStreaming.toString();
        } else if (bodyStream != null) {
            b = bodyStream.toString();
        } else if (bodyFile != null) {
            b = bodyFile.toString();
        } else {
            b = bodyString;
        }
        return "{status=" +
                status +
                ", headers=" + Objects.toString(headers.toString(), "{}") +
                ", body=" + b +
                "}";
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getExtension(String name) {
        return (T) extensions.get(name);
    }

    @Override
    public <T> void setExtension(String name, T extension) {
        extensions.put(name, extension);
    }
}
