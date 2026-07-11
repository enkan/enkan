package enkan.component.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import enkan.component.AbstractBeansConverter;
import enkan.component.ComponentLifecycle;
import enkan.exception.MisconfigurationException;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * @author kawasima
 */
public class JacksonBeansConverter extends AbstractBeansConverter<JacksonBeansConverter> {
    private @Nullable ObjectMapper mapper;
    private @Nullable ObjectMapper nonNullMapper;

    @Override
    public void copy(Object source, Object destination, CopyOption copyOption) {
        ObjectMapper mapper = Objects.requireNonNull(this.mapper, "JacksonBeansConverter has not been started");
        try {
            switch (copyOption) {
                case REPLACE_NON_NULL -> {
                    byte[] buf = Objects.requireNonNull(nonNullMapper).writeValueAsBytes(source);
                    mapper.readerForUpdating(destination).readValue(buf);
                }
                case REPLACE_ALL -> {
                    byte[] buf = mapper.writeValueAsBytes(source);
                    mapper.readerForUpdating(destination).readValue(buf);
                }
                case PRESERVE_NON_NULL ->
                    throw new MisconfigurationException("jackson.UNSUPPORTED_COPY_OPTION", "PRESERVE_NON_NULL",
                            "Use REPLACE_NON_NULL or REPLACE_ALL instead.");
            }
        } catch (JacksonException e) {
            throw new MisconfigurationException("jackson.IO_ERROR");
        }
    }

    @Override
    public <S> S createFrom(Object source, Class<S> destinationClass) {
        if (destinationClass == null)
            throw new IllegalArgumentException("destinationClass is null");

        if (Number.class.isAssignableFrom(destinationClass)
                || destinationClass.equals(String.class)) {
            throw new IllegalArgumentException("destinationClass cannot be mapped to JSON object class");
        }
        return Objects.requireNonNull(mapper, "JacksonBeansConverter has not been started")
                .convertValue(source, destinationClass);
    }

    @Override
    protected ComponentLifecycle<JacksonBeansConverter> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(JacksonBeansConverter component) {
                component.mapper = JsonMapper.builder()
                        .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
                        .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();

                component.nonNullMapper = component.mapper.rebuild()
                        .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
                        .build();
            }

            @Override
            public void stop(JacksonBeansConverter component) {
                component.mapper = null;
                component.nonNullMapper = null;
            }
        };
    }

    @Override
    public String toString() {
        return "#JacksonBeansConverter {\n"
                + "  \"dependencies\": " + dependenciesToString()
                + "\n}";
    }
}
