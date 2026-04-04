package enkan.web.middleware.multipart;

import enkan.collection.Parameters;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author kawasima
 */
public abstract class MimePart {
    protected final String head;
    private final OutputStream body;
    protected final String filename;
    protected final String contentType;
    protected final String name;

    public MimePart(OutputStream body, String head, String filename, String contentType, String name) {
        this.head = head;
        this.body = body;
        this.filename = filename;
        this.contentType = contentType;
        this.name = name;
    }


    public OutputStream getBody() {
        return body;
    }

    public abstract Parameters getData();
    public abstract void write(byte[] buf) throws IOException;
    public abstract void close();
}
