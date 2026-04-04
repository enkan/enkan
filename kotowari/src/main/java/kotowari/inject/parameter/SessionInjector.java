package kotowari.inject.parameter;

import enkan.web.data.HttpRequest;
import enkan.data.Session;
import kotowari.inject.ParameterInjector;

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
    public Session getInjectObject(HttpRequest request) {
        return request.getSession();
    }
}
