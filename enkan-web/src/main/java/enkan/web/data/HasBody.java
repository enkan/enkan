package enkan.web.data;

import org.jspecify.annotations.Nullable;

public interface HasBody {
    /**
     * Returns raw body.
     *
     * @return the raw body of this response, or {@code null} if none is set
     */
    @Nullable Object getBody();
}
