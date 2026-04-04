package kotowari.data;

import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpResponse;
import enkan.exception.MisconfigurationException;

import java.util.HashMap;
import java.util.Map;

/**
 * A HTTP response with template.
 *
 * @author kawasima
 */
public class TemplatedHttpResponse extends DefaultHttpResponse {
    private final String templateName;
    private final Map<String, Object> context;

    private TemplatedHttpResponse(String templateName) {
        super(200, Headers.of("Content-Type", "text/html"));
        this.templateName = templateName;
        context = new HashMap<>();
    }

    public static TemplatedHttpResponse create(String templateName, Object... keyVals) {
        if (keyVals.length % 2 != 0) {
            throw new MisconfigurationException("core.MISSING_KEY_VALUE_PAIR");
        }
        TemplatedHttpResponse response = new TemplatedHttpResponse(templateName);
        for (int i = 0; i < keyVals.length; i += 2) {
            response.context.put(keyVals[i].toString(), keyVals[i+1]);
        }
        return response;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * Get the name of the template.
     *
     * @return a name of the template
     */
    public String getTemplateName() {
        return templateName;
    }
}
