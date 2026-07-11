package kotowari.inject.parameter;

import enkan.web.data.HttpRequest;
import kotowari.inject.ParameterInjector;

import org.jspecify.annotations.Nullable;

import java.security.Principal;

public class PrincipalInjector implements ParameterInjector<Principal> {
    @Override
    public String getName() {
        return "Principal";
    }

    @Override
    public boolean isApplicable(Class<?> type) {
        return Principal.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("ConstantValue")
    public @Nullable Principal getInjectObject(HttpRequest request) {
        //noinspection ConstantValue - tolerate a null request defensively
        return request != null ? request.getPrincipal() : null;
    }
}
