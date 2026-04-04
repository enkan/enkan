package enkan.web.middleware.multipart;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An {@link InputStream} wrapper that enforces a maximum number of bytes read.
 *
 * <p>Once the limit is exceeded, subsequent reads throw {@link SizeLimitExceededException}.</p>
 *
 * @author kawasima
 */
public class BoundedInputStream extends FilterInputStream {
    private final long limit;
    private long count;

    public BoundedInputStream(InputStream in, long limit) {
        super(in);
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            count++;
            checkLimit();
        }
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int n = super.read(buf, off, len);
        if (n > 0) {
            count += n;
            checkLimit();
        }
        return n;
    }

    private void checkLimit() throws SizeLimitExceededException {
        if (count > limit) {
            throw new SizeLimitExceededException(limit);
        }
    }

    public static class SizeLimitExceededException extends IOException {
        private final long limit;

        SizeLimitExceededException(long limit) {
            super("Input exceeded the maximum allowed size of " + limit + " bytes");
            this.limit = limit;
        }

        long getLimit() {
            return limit;
        }
    }
}
