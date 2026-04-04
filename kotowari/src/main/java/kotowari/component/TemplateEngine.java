package kotowari.component;

import enkan.component.SystemComponent;
import enkan.web.data.HttpResponse;

import java.util.List;
import java.util.function.Function;

/**
 * Provides an abstract class to be subclassed to create a template engine.
 *
 * @author kawasima
 */
public abstract class TemplateEngine<T extends TemplateEngine<T>> extends SystemComponent<T> {
    /**
     * Render the HTML template.
     *
     * @param name the name of the template
     * @param keyOrVals the
     * @return a response object
     */
    public abstract HttpResponse render(String name, Object... keyOrVals);

    public abstract Object createFunction(Function<List<?>, Object> func);
}
