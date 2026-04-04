package kotowari.inject.parameter;

import enkan.collection.Parameters;
import enkan.web.data.HttpRequest;
import kotowari.inject.ParameterInjector;

public class ParametersInjector implements ParameterInjector<Parameters> {
    @Override
    public String getName() {
        return "Parameters";
    }

    @Override
    public boolean isApplicable(Class<?> type) {
        return Parameters.class.isAssignableFrom(type);
    }

    @Override
    public Parameters getInjectObject(HttpRequest request) {
        return request.getParams();
    }
}
