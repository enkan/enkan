package enkan.component.thymeleaf;

import enkan.component.LifecycleManager;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.dialect.IDialect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ThymeleafTemplateEngineTest {

    ThymeleafTemplateEngine engine;

    @BeforeEach
    public void setup() {
        engine = new ThymeleafTemplateEngine();
        LifecycleManager.start(engine);
    }

    @AfterEach
    public void tearDown() {
        LifecycleManager.stop(engine);
    }

    private String bodyAsString(HttpResponse response) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader((InputStream) response.getBody()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    // ------------------------------------------------------------------- render

    @Test
    public void variablesAreRenderedIntoTemplate() throws IOException {
        HttpResponse response = engine.render("test1", "name", "kawasima", "message", "hello");
        assertThat(bodyAsString(response)).contains("<span>kawasima</span>");
    }

    @Test
    public void messageVariableIsRendered() throws IOException {
        HttpResponse response = engine.render("test1", "name", "kawasima", "message", "hello");
        assertThat(bodyAsString(response)).contains("hello");
    }

    @Test
    public void contentTypeIsSetToTextHtml() {
        HttpResponse response = engine.render("test1", "name", "kawasima", "message", "hello");
        assertThat(response.getHeaders().get("content-type")).isEqualTo("text/html; charset=UTF-8");
    }

    // --------------------------------------------------------------- functions

    @Test
    public void customFunctionIsAppliedInTemplate() throws IOException {
        HttpResponse response = engine.render("test2", "func",
                (Function<List<?>, Object>) list -> list.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.joining("&")));
        assertThat(bodyAsString(response)).contains("<span>abc&amp;def</span>");
    }

    // ----------------------------------------------------------------- dialect

    @Test
    public void emptyDialectsDisablesDefaultDialect() throws IOException {
        // When an empty dialect set is given, Thymeleaf removes the default
        // Standard Dialect, so th:text expressions are left unprocessed.
        Set<IDialect> emptyDialects = new HashSet<>();
        ThymeleafTemplateEngine customEngine = builder(new ThymeleafTemplateEngine())
                .set(ThymeleafTemplateEngine::setDialects, emptyDialects)
                .build();
        LifecycleManager.start(customEngine);
        try {
            HttpResponse response = customEngine.render("test1", "name", "kawasima", "message", "hello");
            assertThat(bodyAsString(response)).contains("th:text=\"${name}\"");
        } finally {
            LifecycleManager.stop(customEngine);
        }
    }

    // --------------------------------------------------------------- lifecycle

    @Test
    public void renderThrowsWhenNotStarted() {
        ThymeleafTemplateEngine notStarted = new ThymeleafTemplateEngine();
        assertThatThrownBy(() -> notStarted.render("test1", "name", "kawasima"))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    public void invalidEncodingThrowsAtSetTime() {
        assertThatThrownBy(() -> engine.setEncoding("INVALID-CHARSET-XYZ"))
                .isInstanceOf(MisconfigurationException.class);
    }
}
