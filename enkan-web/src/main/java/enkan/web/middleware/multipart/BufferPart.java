package enkan.web.middleware.multipart;

import enkan.collection.Parameters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author kawasima
 */
public class BufferPart extends MimePart {
    public BufferPart(String head, String filename, String contentType, String name) {
        super(new ByteArrayOutputStream(), head, filename, contentType, name);
    }

    @Override
    public Parameters getData() {
        String value = ((ByteArrayOutputStream) getBody()).toString(StandardCharsets.ISO_8859_1);
        return Parameters.of(name, value);
    }

    @Override
    public void write(byte[] buf) throws IOException {
        getBody().write(buf);
    }

    @Override
    public void close() {

    }
}
