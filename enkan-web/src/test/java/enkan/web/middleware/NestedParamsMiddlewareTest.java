package enkan.web.middleware;

import enkan.collection.Parameters;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.exception.MisconfigurationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author kawasima
 */
class NestedParamsMiddlewareTest {
    NestedParamsMiddleware sut = new NestedParamsMiddleware();

    @Test
    void testParseNestedKeys() {
        Function<String, String[]> keyParser = sut.parseNestedKeys;
        assertThat(keyParser.apply("foo[]")).containsExactly("foo", "");
        assertThat(keyParser.apply("foo[bar][][baz]"))
                .containsExactly("foo", "bar", "", "baz");
        assertThat(keyParser.apply(null))
                .isEmpty();
        assertThat(keyParser.apply(""))
                .containsExactly("");
    }

    @Test
    void testNestedParams() {
        Parameters params = Parameters.empty();
        sut.assocNested(params,
                new String[]{"val", ""},
                new ArrayList<String>(){{ add("hoge"); }});
        assertThat(params.getIn("val", 0))
                .isEqualTo("hoge");

        params = Parameters.empty();
        sut.assocNested(params,
                new String[]{"foo", "bar"},
                new ArrayList<String>(){{ add("baz"); }});
        assertThat(params.getIn("foo", "bar"))
                .isEqualTo("baz");

        params = Parameters.empty();
        sut.assocNested(params,
                new String[]{"foo", "bar"},
                new ArrayList<String>(){{ add("baz"); add("bay"); }});
        assertThat(params.getIn("foo", "bar", 1))
                .isEqualTo("bay");
    }

    @Test
    void deepNestingThrowsMisconfigurationException() {
        // Build keys with 34 nesting levels (exceeds MAX_NESTING_DEPTH=32)
        String[] keys = new String[34];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "k" + i;
        }
        Parameters params = Parameters.empty();
        List<String> values = new ArrayList<>(List.of("value"));

        assertThatThrownBy(() -> sut.assocNested(params, keys, values))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void testNestedParamsRequest() {
        HttpRequest request = new DefaultHttpRequest();
        request.setParams(Parameters.of(
                "foo[aaa]", "a3",
                "foo[bbb]", "b3",
                "bar[][telNo]", "090"));
        sut.nestedParamsRequest(request, sut.parseNestedKeys);
        assertThat(request.getParams().getIn("bar", "0", "telNo"))
                .isEqualTo("090");
    }
}

