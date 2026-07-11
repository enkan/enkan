package enkan.web.middleware.multipart;

import enkan.collection.Parameters;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author kawasima
 */
public class BufferPart extends MimePart {
    public BufferPart(String head, @Nullable String filename, @Nullable String contentType, @Nullable String name) {
        super(new ByteArrayOutputStream(), head, filename, contentType, name);
    }

    @Override
    public Parameters getData() {
        String value = ((ByteArrayOutputStream) getBody()).toString(StandardCharsets.ISO_8859_1);
        return Parameters.of(Objects.requireNonNull(name), value);
    }

    @Override
    public void write(byte[] buf) throws IOException {
        getBody().write(buf);
    }

    @Override
    public void close() {

    }
}
