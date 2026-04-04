package kotowari.routing;

import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;

import java.util.List;

/**
 * Recognizes routing information from HttpRequest.
 *
 * @author kawasima
 */
public interface Recognizer {
    void setRoutes(List<Route> routes);
    OptionMap recognize(HttpRequest request);
    boolean isOptimized();
    void optimize();
}
