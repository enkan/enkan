package kotowari.inject.parameter;

import enkan.collection.Parameters;
import enkan.web.data.HttpRequest;
import kotowari.inject.ParameterInjector;

import org.jspecify.annotations.Nullable;

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
    public @Nullable Parameters getInjectObject(HttpRequest request) {
        return request.getParams();
    }
}
