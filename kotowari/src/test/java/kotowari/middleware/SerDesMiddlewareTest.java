package kotowari.middleware;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.jakarta.rs.json.JacksonJsonProvider;
import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.data.*;
import enkan.web.data.ContentNegotiable;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import enkan.util.MixinUtils;
import kotowari.data.BodyDeserializable;
import kotowari.test.dto.TestDto;
import kotowari.util.ParameterUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static enkan.util.BeanBuilder.*;
import static enkan.util.ReflectionUtils.*;
import static org.assertj.core.api.Assertions.*;

/**
 * @author kawasima
 */
public class SerDesMiddlewareTest {
    final JacksonJsonProvider jsonProvider = new JacksonJsonProvider(JsonMapper.builder().build());
    final JacksonJsonProvider strictJsonProvider = new JacksonJsonProvider(
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());

    @Test
    public void test() throws IOException {
        SerDesMiddleware<Object> middleware = builder(new SerDesMiddleware<>())
                .set(SerDesMiddleware::setBodyReaders, jsonProvider)
                .set(SerDesMiddleware::setBodyWriters, jsonProvider)
                .build();

        String body = "{\"a\":1, \"b\":\"ccb\"}";
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Type", "application/json"))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body.getBytes()))
                .build();

        TestDto testDto = middleware.deserialize(request, TestDto.class, TestDto.class, new MediaType("application", "json"));
        assertThat(testDto.getA()).isEqualTo(1);
        assertThat(testDto.getB()).isEqualTo("ccb");
    }

    @Test
    public void deserializeList() throws IOException {
        SerDesMiddleware<Object> middleware = builder(new SerDesMiddleware<>())
                .set(SerDesMiddleware::setBodyReaders, jsonProvider)
                .set(SerDesMiddleware::setBodyWriters, jsonProvider)
                .build();

        String body = "[{\"a\":1, \"b\":\"ccb\"}, {\"a\":2, \"b\":\"ooo\"}]";
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Type", "application/json"))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body.getBytes()))
                .build();

        Object testDto = middleware.deserialize(request, List.class, new TypeReference<List<TestDto>>(){}.getType(), new MediaType("application", "json"));
        assertThat(testDto).isInstanceOf(List.class);
        assertThat(testDto).asInstanceOf(InstanceOfAssertFactories.LIST).size().isEqualTo(2);
        assertThat(testDto).asInstanceOf(InstanceOfAssertFactories.LIST).contains(builder(new TestDto())
                .set(TestDto::setA, 1)
                .set(TestDto::setB, "ccb")
                .build());
    }

    @Test
    public void deserializeUnknownListOrMap() throws IOException {
        SerDesMiddleware<Object> middleware = builder(new SerDesMiddleware<>())
                .set(SerDesMiddleware::setBodyReaders, jsonProvider)
                .set(SerDesMiddleware::setBodyWriters, jsonProvider)
                .build();

        String body = "[{\"a\":1, \"b\":\"ccb\"}, {\"a\":2, \"b\":\"ooo\"}]";
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Type", "application/json"))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body.getBytes()))
                .build();

        Object testDto = middleware.deserialize(request, Object.class, Object.class, new MediaType("application", "json"));
        assertThat(testDto).isInstanceOf(List.class);
        assertThat(testDto).asInstanceOf(InstanceOfAssertFactories.LIST).size().isEqualTo(2);
    }

    @Test
    public void controller() throws IOException {
        SerDesMiddleware<Object> middleware = builder(new SerDesMiddleware<>())
                .set(SerDesMiddleware::setBodyReaders, jsonProvider)
                .set(SerDesMiddleware::setBodyWriters, jsonProvider)
                .set(SerDesMiddleware::setParameterInjectors, ParameterUtils.getDefaultParameterInjectors())
                .build();

        String body = "[{\"a\":1, \"b\":\"ccb\"}, {\"a\":2, \"b\":\"ooo\"}]";
        HttpRequest request = MixinUtils.mixin(builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Content-Type", "application/json"))
                .set(HttpRequest::setBody, new ByteArrayInputStream(body.getBytes()))
                .build(),
                Routable.class,
                ContentNegotiable.class,
                BodyDeserializable.class);
        ((ContentNegotiable) request).setMediaType(new MediaType("application", "json"));
        TestController controller = new TestController();
        MiddlewareChain<HttpRequest, Object, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, Object>) req ->
                        tryReflection(() -> {
                            List<TestDto> obj = ((BodyDeserializable) req).getDeserializedBody();
                            return ((Routable) req).getControllerMethod().invoke(controller, obj);
                        }));

        HttpResponse resp = tryReflection(() -> {
            Method fooMethod = TestController.class.getMethod("foo", List.class);
            ((Routable) request).setControllerMethod(fooMethod);
            return middleware.handle(request, chain);
        });

        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Object> res = mapper.readValue(resp.getBodyAsStream(), new TypeReference<>() {
        });
        assertThat(res).containsEntry("a", 1);
        assertThat(res).containsEntry("b", "ccb");
    }

    @Test
    public void nullSerialize() {
        SerDesMiddleware<Object> middleware = builder(new SerDesMiddleware<>())
                .set(SerDesMiddleware::setBodyReaders, jsonProvider)
                .build();
        try (InputStream in = middleware.serialize(null, new MediaType("application", "json"))) {
            assertThat(in).isInstanceOf(ByteArrayInputStream.class);
        } catch (IOException e) {
            fail("IOException occurred.", e);
        }
    }

    @Test
    public void raiseException() {
        assertThatThrownBy(() -> {
            SerDesMiddleware<Object> middleware = builder(new SerDesMiddleware<>())
                    .set(SerDesMiddleware::setBodyReaders, strictJsonProvider)
                    .set(SerDesMiddleware::setBodyWriters, strictJsonProvider)
                    .build();

            String body = "{\"a\":1, \"b\":\"ccb\", \"c\": \"This is an unknown property.\"}";
            HttpRequest request = builder(new DefaultHttpRequest())
                    .set(HttpRequest::setHeaders, Headers.of("Content-Type", "application/json"))
                    .set(HttpRequest::setBody, new ByteArrayInputStream(body.getBytes()))
                    .build();

            middleware.deserialize(request, TestDto.class, TestDto.class, new MediaType("application", "json"));
        }).isInstanceOf(UnrecognizedPropertyException.class);
    }

    static class TestController {
        public TestDto foo(List<TestDto> testDtoList) {
            return testDtoList.getFirst();
        }
    }
}
