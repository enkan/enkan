package enkan.web.data;

import enkan.data.Extendable;
import jakarta.ws.rs.core.MediaType;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * @author kawasima
 */
public interface ContentNegotiable extends Extendable {
    default @Nullable MediaType getMediaType() {
        return getExtension("mediaType");
    }

    default void setMediaType(@Nullable MediaType mediaType) {
        if (mediaType != null) {
            setExtension("mediaType", mediaType);
        }
    }

    default @Nullable Locale getLocale() {
        return getExtension("locale");
    }

    default void setLocale(@Nullable Locale locale) {
        if (locale != null) {
            setExtension("locale", locale);
        }
    }

    default @Nullable String getCharset() {
        return getExtension("charset");
    }

    default void setCharset(@Nullable String charset) {
        if (charset != null) {
            setExtension("charset", charset);
        }
    }

    default @Nullable String getEncoding() {
        return getExtension("encoding");
    }

    default void setEncoding(@Nullable String encoding) {
        if (encoding != null) {
            setExtension("encoding", encoding);
        }
    }
}
