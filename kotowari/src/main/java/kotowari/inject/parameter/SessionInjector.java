package kotowari.inject.parameter;

import enkan.web.data.HttpRequest;
import enkan.data.Session;
import kotowari.inject.ParameterInjector;

import org.jspecify.annotations.Nullable;

public class SessionInjector implements ParameterInjector<Session> {
    @Override
    public String getName() {
        return "Session";
    }

    @Override
    public boolean isApplicable(Class<?> type) {
        return Session.class.isAssignableFrom(type);
    }

    @Override
    public @Nullable Session getInjectObject(HttpRequest request) {
        return request.getSession();
    }
}
