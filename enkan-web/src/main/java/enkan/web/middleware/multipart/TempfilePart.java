package enkan.web.middleware.multipart;

import enkan.collection.Parameters;

import org.jspecify.annotations.Nullable;

import java.io.*;
import java.util.Objects;

/**
 * @author kawasima
 */
public class TempfilePart extends MimePart {
    private final File tempfile;

    private static OutputStream getOutputStream(File file) throws IOException {
        return new BufferedOutputStream(new FileOutputStream(file));
    }

    public TempfilePart(File tempfile, String head, @Nullable String filename, @Nullable String contentType, @Nullable String name) throws IOException {
        super(getOutputStream(tempfile), head, filename, contentType, name);
        this.tempfile = tempfile;
    }

    private @Nullable String last(String @Nullable [] strArray) {
        if (strArray == null || strArray.length == 0) {
            return null;
        } else {
            return strArray[strArray.length - 1];
        }
    }

    @Override
    public @Nullable Parameters getData() {
        String partName = Objects.requireNonNull(name);
        if (filename != null) {
            String fn = Objects.requireNonNull(last(filename.split("[/\\\\]")));
            return Parameters.of(partName,
                    Parameters.of(
                            "filename", fn,
                            "name", partName,
                            "tempfile", tempfile,
                            "type", contentType,
                            "head", head));
        } else if (contentType != null) {
            return Parameters.of(partName,
                    Parameters.of(
                            "type", contentType,
                            "name", partName,
                            "tempfile", tempfile,
                            "head", head));
        }
        return null;
    }

    @Override
    public void write(byte[] buf) throws IOException {
        getBody().write(buf);
    }

    @Override
    public void close() {
        try {
            getBody().close();
        } catch (IOException _) {
            // ignore
        }
    }

    /**
     * Deletes the underlying temp file. Called during cleanup on parse failure.
     */
    void deleteTempfile() {
        if (tempfile != null) {
            tempfile.delete();
        }
    }
}
