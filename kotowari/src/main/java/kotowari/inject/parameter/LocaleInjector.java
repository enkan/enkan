package kotowari.inject.parameter;

import enkan.web.data.ContentNegotiable;
import enkan.web.data.HttpRequest;
import kotowari.inject.ParameterInjector;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

public class LocaleInjector implements ParameterInjector<Locale> {
    @Override
    public String getName() {
        return "Locale";
    }

    @Override
    public boolean isApplicable(Class<?> type) {
        return Locale.class.equals(type);
    }

    @Override
    public @Nullable Locale getInjectObject(HttpRequest request) {
        if (request instanceof ContentNegotiable cn) {
            return cn.getLocale();
        }
        return null;
    }
}
