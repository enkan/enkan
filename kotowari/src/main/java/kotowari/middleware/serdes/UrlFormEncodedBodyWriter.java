package kotowari.middleware.serdes;

import enkan.web.util.CodecUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import enkan.component.BeansConverter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UrlFormEncodedBodyWriter implements MessageBodyWriter<Object> {
    @Inject
    private BeansConverter beansConverter;

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return Objects.equals(mediaType.getType(), "application")
                && Objects.equals(mediaType.getSubtype(), "x-www-form-urlencoded");
    }

    @Override
    public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        Map<String, Object> m = new HashMap<>();
        beansConverter.copy(o, m);
        String encoded = CodecUtils.formEncode(m);
        entityStream.write(encoded.getBytes());
    }
}
