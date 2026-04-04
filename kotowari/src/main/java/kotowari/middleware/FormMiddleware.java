package kotowari.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.component.BeansConverter;
import enkan.data.*;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.exception.UnreachableException;

import enkan.web.middleware.WebMiddleware;
import enkan.util.MixinUtils;
import kotowari.data.BodyDeserializable;
import kotowari.inject.ParameterInjector;
import kotowari.util.ParameterUtils;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.List;

/**
 * Sets the form object to the request.
 *
 * @author kawasima
 */
@Middleware(name = "form", dependencies = {"params", "routing"}, mixins = BodyDeserializable.class)
public class FormMiddleware implements WebMiddleware {
    @Inject
    protected BeansConverter beans;

    private List<ParameterInjector<?>> parameterInjectors;

    @PostConstruct
    protected void setupParameterInjectors() {
        if (parameterInjectors == null) {
            parameterInjectors = ParameterUtils.getDefaultParameterInjectors();
        }
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        Method method = ((Routable) request).getControllerMethod();
        if (method == null) {
            throw new MisconfigurationException("kotowari.CONTROLLER_METHOD_NOT_FOUND", "FormMiddleware");
        }
        request = MixinUtils.mixin(request, BodyDeserializable.class);
        for (Parameter parameter : method.getParameters()) {
            Class<?> type = parameter.getType();
            if (parameterInjectors.stream().anyMatch(injector-> injector.isApplicable(type)))
                continue;

            BodyDeserializable bodyDeserializable = (BodyDeserializable) request;
            Object body = bodyDeserializable.getDeserializedBody();
            try {
                if (body == null) {
                    if (!Collection.class.isAssignableFrom(type) && !type.isArray()) {
                        bodyDeserializable.setDeserializedBody(beans.createFrom(request.getParams(), type));
                    }
                } else {
                    beans.copy(request.getParams(), body, BeansConverter.CopyOption.REPLACE_NON_NULL);
                    bodyDeserializable.setDeserializedBody(body);
                }
            } catch (ClassCastException e) {
                if (!Serializable.class.isAssignableFrom(type)) {
                    throw new MisconfigurationException("kotowari.FORM_IS_NOT_SERIALIZABLE", type);
                } else {
                    throw new UnreachableException(e);
                }
            }
        }

        return castToHttpResponse(chain.next(request));
    }

    public void setParameterInjectors(List<ParameterInjector<?>> parameterInjectors) {
        this.parameterInjectors = parameterInjectors;
    }
}
