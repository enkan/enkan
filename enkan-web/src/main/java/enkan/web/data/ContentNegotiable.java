package enkan.web.data;

import enkan.data.Extendable;
import jakarta.ws.rs.core.MediaType;
import java.util.Locale;

/**
 * @author kawasima
 */
public interface ContentNegotiable extends Extendable {
    default MediaType getMediaType() {
        return getExtension("mediaType");
    }

    default void setMediaType(MediaType mediaType) {
        setExtension("mediaType", mediaType);
    }

    default Locale getLocale() {
        return getExtension("locale");
    }

    default void setLocale(Locale locale) {
        setExtension("locale", locale);
    }

    default String getCharset() {
        return getExtension("charset");
    }

    default void setCharset(String charset) {
        setExtension("charset", charset);
    }

    default String getEncoding() {
        return getExtension("encoding");
    }

    default void setEncoding(String encoding) {
        setExtension("encoding", encoding);
    }
}
