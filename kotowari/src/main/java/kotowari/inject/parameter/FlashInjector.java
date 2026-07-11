package kotowari.inject.parameter;

import enkan.data.Flash;
import enkan.web.data.HttpRequest;
import kotowari.inject.ParameterInjector;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public class FlashInjector<T extends Serializable> implements ParameterInjector<Flash<T>> {
    @Override
    public String getName() {
        return "Flash";
    }

    @Override
    public boolean isApplicable(Class<?> type) {
        return Flash.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable Flash<T> getInjectObject(HttpRequest request) {
        return (Flash<T>) request.getFlash();
    }
}
