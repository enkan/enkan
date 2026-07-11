package enkan.web.middleware.multipart;

import enkan.collection.Parameters;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author kawasima
 */
public abstract class MimePart {
    protected final String head;
    private final OutputStream body;
    protected final @Nullable String filename;
    protected final @Nullable String contentType;
    protected final @Nullable String name;

    public MimePart(OutputStream body, String head, @Nullable String filename, @Nullable String contentType, @Nullable String name) {
        this.head = head;
        this.body = body;
        this.filename = filename;
        this.contentType = contentType;
        this.name = name;
    }


    public OutputStream getBody() {
        return body;
    }

    public abstract @Nullable Parameters getData();
    public abstract void write(byte[] buf) throws IOException;
    public abstract void close();
}
