package enkan.middleware.multipart;

import enkan.exception.MisconfigurationException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Collects multipart MIME parts with optional per-part size limits.
 *
 * @author kawasima
 */
public class MultipartCollector {
    private final BiFunction<String, String, File> tempfileFactory;
    private final List<MimePart> mimeParts = new ArrayList<>();
    private final List<Long> partSizes = new ArrayList<>();
    private final List<Boolean> isFile = new ArrayList<>();
    private final long maxFileSize;
    private final long maxFormFieldSize;

    public MultipartCollector(BiFunction<String, String, File> tempfileFactory) {
        this(tempfileFactory, -1, -1);
    }

    public MultipartCollector(BiFunction<String, String, File> tempfileFactory,
                              long maxFileSize, long maxFormFieldSize) {
        this.tempfileFactory = tempfileFactory;
        this.maxFileSize = maxFileSize;
        this.maxFormFieldSize = maxFormFieldSize;
    }

    public void onMimeHead(int mimeIndex, String head, String filename, String contentType, String name) throws IOException {
        if (filename != null) {
            File tempfile = tempfileFactory.apply(filename, contentType);
            mimeParts.add(new TempfilePart(tempfile, head, filename, contentType, name));
            isFile.add(true);
        } else {
            mimeParts.add(new BufferPart(head, null, contentType, name));
            isFile.add(false);
        }
        partSizes.add(0L);
    }

    public void onMimeBody(int mimeIndex, byte[] content) throws IOException {
        long newSize = partSizes.get(mimeIndex) + content.length;
        long limit = isFile.get(mimeIndex) ? maxFileSize : maxFormFieldSize;
        if (limit >= 0 && newSize > limit) {
            cleanup();
            throw new MisconfigurationException("web.MULTIPART_PART_TOO_LARGE", limit);
        }
        partSizes.set(mimeIndex, newSize);
        mimeParts.get(mimeIndex).getBody().write(content);
    }

    public void onMimeFinish(int mimeIndex) {
        mimeParts.get(mimeIndex).close();
    }

    /**
     * Closes all parts and deletes any temp files created during parsing.
     * Called on parse failure to prevent resource leaks.
     */
    public void cleanup() {
        for (int i = 0; i < mimeParts.size(); i++) {
            mimeParts.get(i).close();
            if (isFile.get(i)) {
                MimePart part = mimeParts.get(i);
                if (part instanceof TempfilePart tp) {
                    tp.deleteTempfile();
                }
            }
        }
    }

    public Stream<MimePart> stream() {
        return mimeParts.stream();
    }
}
