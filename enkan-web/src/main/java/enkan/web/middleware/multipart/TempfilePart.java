package enkan.web.middleware.multipart;

import enkan.collection.Parameters;

import java.io.*;

/**
 * @author kawasima
 */
public class TempfilePart extends MimePart {
    private final File tempfile;

    private static OutputStream getOutputStream(File file) throws IOException {
        return new BufferedOutputStream(new FileOutputStream(file));
    }

    public TempfilePart(File tempfile, String head, String filename, String contentType, String name) throws IOException {
        super(getOutputStream(tempfile), head, filename, contentType, name);
        this.tempfile = tempfile;
    }

    private String last(String[] strArray) {
        if (strArray == null || strArray.length == 0) {
            return null;
        } else {
            return strArray[strArray.length - 1];
        }
    }

    @Override
    public Parameters getData() {
        if (filename != null) {
            String fn = last(filename.split("[/\\\\]"));
            return Parameters.of(name,
                    Parameters.of(
                            "filename", fn,
                            "name", name,
                            "tempfile", tempfile,
                            "type", contentType,
                            "head", head));
        } else if (contentType != null) {
            return Parameters.of(name,
                    Parameters.of(
                            "type", contentType,
                            "name", name,
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
        } catch (IOException ignore) {
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
