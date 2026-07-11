package enkan.data;

import org.jspecify.annotations.Nullable;

/**
 * @author kawasima
 */
public interface UriAvailable {
    @Nullable String getUri();
    void setUri(@Nullable String uri);

    @Nullable String getRequestMethod();
    void setRequestMethod(@Nullable String method);
}
