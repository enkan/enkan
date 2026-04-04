package kotowari.middleware;

import enkan.Endpoint;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.data.Routable;
import enkan.web.middleware.NestedParamsMiddleware;
import enkan.web.middleware.ParamsMiddleware;
import enkan.util.MixinUtils;
import enkan.util.Predicates;
import kotowari.data.BodyDeserializable;
import kotowari.test.controller.TestController;
import kotowari.test.form.NestedForm;
import kotowari.util.ParameterUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static enkan.util.ReflectionUtils.tryReflection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author kawasima
 */
public class FormMiddlewareTest {
    private static final Pattern RE_NESTED_NAME = Pattern.compile("^(?s)(.*?)((?:\\[.*?])*)$");
    private static final Pattern RE_NESTED_TOKEN = Pattern.compile("\\[(.*?)]");
    protected final Function<String, String[]> parseNestedKeys = (paramName) -> {
        if (paramName == null) return new String[]{};

        Matcher m = RE_NESTED_NAME.matcher(paramName);
        List<String> keys = new ArrayList<>();

        if (m.find()) {
            keys.add(m.group(1));
            String ks = m.group(2);
            if (ks != null && !ks.isEmpty()) {
                Matcher mt = RE_NESTED_TOKEN.matcher(ks);
                while (mt.find()) {
                    keys.add(mt.group(1));
                }
            } else {
                return new String[]{ m.group(1) };
            }
        }

        return keys.toArray(new String[0]);
    };

    private Parameters parseFromQuery(String qs) {
        HttpRequest request = new DefaultHttpRequest();
        request.setHeaders(Headers.empty());
        request.setRequestMethod("GET");
        request.setQueryString(qs);

        new ParamsMiddleware().paramsRequest(request);
        new NestedParamsMiddleware().nestedParamsRequest(request, parseNestedKeys);

        return request.getParams();
    }

    @Test
    public void test() {
        JacksonBeansConverter beans = new JacksonBeansConverter() {{
            lifecycle().start(this);
        }};
        HttpRequest request = new DefaultHttpRequest();
        request.setHeaders(Headers.empty());
        request.setRequestMethod("GET");
        request.setQueryString("intVal=123&doubleVal=1.7320508&decimalVal=65536" +
                "&item[name]=item1&itemList[][name]=item2&itemList[][name]=item3" +
                "&itemArray[][name]=item4&itemArray[][name]=item5");
        request = MixinUtils.mixin(request, BodyDeserializable.class);

        new ParamsMiddleware().paramsRequest(request);
        new NestedParamsMiddleware().nestedParamsRequest(request, parseNestedKeys);

        FormMiddleware formMiddleware = new FormMiddleware();
        formMiddleware.setParameterInjectors(ParameterUtils.getDefaultParameterInjectors());
        formMiddleware.beans = beans;
        request = MixinUtils.mixin(request, Routable.class);
        Method method = tryReflection(() -> TestController.class.getMethod("index", NestedForm.class));
        ((Routable) request).setControllerMethod(method);
        formMiddleware.handle(request, new DefaultMiddlewareChain<>(Predicates.none(), "dummy",
                (Endpoint<HttpRequest, HttpResponse>) r -> null));

        NestedForm form = ((BodyDeserializable) request).getDeserializedBody();
        assertThat(form.getIntVal()).isEqualTo(123);
        assertThat(form.getDoubleVal()).isEqualTo(1.7320508);
        assertThat(form.getDecimalVal()).isEqualTo(new BigDecimal("65536"));
        assertThat(form.getItem().getName()).isEqualTo("item1");
        assertThat(form.getItemList().size()).isEqualTo(2);
        assertThat(form.getItemList().get(1).getName()).isEqualTo("item3");
    }

    @Test
    public void parseNestedQueryStringsCorrectly() {
        Parameters p = parseFromQuery("foo=bar");
        assertEquals("bar", p.getIn("foo"));

        p = parseFromQuery("foo");
        assertNull(p.getIn("foo"));

        p = parseFromQuery("foo=");
        assertEquals("", p.getIn("foo"));

        p = parseFromQuery("foo=\"bar\"");
        assertEquals("\"bar\"", p.getIn("foo"));

        p = parseFromQuery("foo=bar&foo=quux");
        assertEquals("bar", p.getIn("foo", 0));

        p = parseFromQuery("foo&foo=");
        assertEquals("", p.getIn("foo"));

        p = parseFromQuery("a=b&pid%3D1234=1023");
        assertEquals("1023", p.getIn("pid=1234"));
        assertEquals("b", p.getIn("a"));

        // Difference from rack. rack returns "[nil]".
        p = parseFromQuery("foo[]");
        assertEquals(0, ((List<?>) p.getIn("foo")).size());

        p = parseFromQuery("foo[]=bar");
        assertEquals(1, p.getList("foo").size());
        assertEquals("bar", p.getIn("foo", 0));

        p = parseFromQuery("foo[]=bar&foo[]");
        assertEquals(2, p.getList("foo").size());
        assertEquals("bar", p.getIn("foo", 0));
        assertNull(p.getIn("foo", 1));

        p = parseFromQuery("x[y][z]=1");
        assertEquals("1", p.getIn("x", "y", "z"));

        p = parseFromQuery("x[y][]=1");
        assertEquals("1", p.getIn("x", "y", 0));

        p = parseFromQuery("x[y][z][]=1");
        assertEquals("1", p.getIn("x", "y", "z", 0));

        p = parseFromQuery("x[y][z][]=1&x[y][z][]=2");
        assertEquals("1", p.getIn("x", "y", "z", 0));
        assertEquals("2", p.getIn("x", "y", "z", 1));

        p = parseFromQuery("x[y][][z]=1");
        assertEquals("1", p.getIn("x", "y", 0, "z"));

        p = parseFromQuery("x[y][][z][]=1");
        assertEquals("1", p.getIn("x", "y", 0, "z", 0));
    }
}
