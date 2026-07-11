package kotowari.io;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author kawasima
 */
public class LazyRenderInputStream extends InputStream {
    private final LazyRenderer renderer;
    private @Nullable InputStream in;

    public LazyRenderInputStream(LazyRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public int read() throws IOException {
        InputStream stream = in;
        if (stream == null) {
            stream = renderer.render();
            in = stream;
        }
        return stream.read();
    }

    @Override
    public void close() throws IOException {
        if (in != null) {
            in.close();
        }
    }
}
