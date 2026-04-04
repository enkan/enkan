package enkan.component.freemarker;

import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.system.EnkanSystem;
import freemarker.cache.FileTemplateLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FreemarkerTemplateEngineTest {
    private EnkanSystem system;
    private FreemarkerTemplateEngine freemarker;

    @BeforeEach
    void setUp() {
        system = EnkanSystem.of("freemarker", new FreemarkerTemplateEngine());
        freemarker = system.getComponent("freemarker");
        system.start();
    }

    @AfterEach
    void tearDown() {
        if (system != null) {
            system.stop();
        }
    }

    private String readResponse(HttpResponse response) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBodyAsStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
        }
        return sb.toString();
    }

    // ---- FileTemplateLoader (existing behavior) ----

    @Test
    void renderFromFileTemplateLoader() throws IOException {
        system.stop();
        FreemarkerTemplateEngine engine = new FreemarkerTemplateEngine();
        engine.setTemplateLoader(new FileTemplateLoader(new File("src/test/resources")));
        system = EnkanSystem.of("freemarker", engine);
        system.start();

        HttpResponse response = engine.render("file/hello");
        assertThat(readResponse(response)).isEqualTo("Hello, Freemarker");
    }

    // ---- ClassTemplateLoader (default classpath) ----

    @Test
    void renderFromClasspathWithVariable() throws IOException {
        HttpResponse response = freemarker.render("greeting", "name", "World");
        assertThat(readResponse(response)).isEqualTo("Hello, World!");
    }

    @Test
    void renderSetsContentTypeToTextHtml() throws IOException {
        HttpResponse response = freemarker.render("greeting", "name", "test");
        assertThat(response.getHeaders().get("Content-Type")).isEqualTo("text/html");
    }

    // ---- Validatable form adapter ----

    @Test
    void validFormHasNoErrors() throws IOException {
        TestForm form = new TestForm();
        form.setName("Alice");

        HttpResponse response = freemarker.render("form", "form", form);
        String body = readResponse(response);
        assertThat(body).startsWith("VALID");
        assertThat(body).doesNotContain("name:");
    }

    @Test
    void invalidFormShowsErrors() throws IOException {
        TestForm form = new TestForm();
        form.addError("name", "must not be blank");

        HttpResponse response = freemarker.render("form", "form", form);
        String body = readResponse(response);
        assertThat(body).startsWith("INVALID");
        assertThat(body).contains("name: must not be blank");
    }

    @Test
    void formHasErrorsForSpecificField() throws IOException {
        TestForm form = new TestForm();
        form.addError("name", "too short");

        HttpResponse response = freemarker.render("form", "form", form);
        assertThat(readResponse(response)).contains("name: too short");
    }

    // ---- createFunction ----

    @Test
    void createFunctionIsCallableFromTemplate() throws IOException {
        Object doubleFunc = freemarker.createFunction(args -> {
            int val = Integer.parseInt(args.getFirst().toString());
            return val * 2;
        });

        HttpResponse response = freemarker.render("function", "double", doubleFunc);
        assertThat(readResponse(response)).isEqualTo("10");
    }

    // ---- Error handling ----

    @Test
    void renderMissingTemplateThrowsMisconfigurationException() {
        assertThatThrownBy(() -> {
            HttpResponse response = freemarker.render("nonexistent");
            // trigger lazy rendering
            readResponse(response);
        }).isInstanceOf(MisconfigurationException.class);
    }

    // ---- System restart ----

    @Test
    void systemRestartPreservesTemplateLoader() throws IOException {
        system.stop();
        system.start();
        freemarker = system.getComponent("freemarker");

        HttpResponse response = freemarker.render("greeting", "name", "Restart");
        assertThat(readResponse(response)).isEqualTo("Hello, Restart!");
    }

    @Test
    void userSetClassLoaderPreservedAfterRestart() throws IOException {
        system.stop();
        FreemarkerTemplateEngine engine = new FreemarkerTemplateEngine();
        engine.setClassLoader(Thread.currentThread().getContextClassLoader());
        system = EnkanSystem.of("freemarker", engine);
        system.start();

        system.stop();
        system.start();

        HttpResponse response = engine.render("greeting", "name", "AfterRestart");
        assertThat(readResponse(response)).isEqualTo("Hello, AfterRestart!");
    }

    // ---- MultiTemplateLoader with custom loader ----

    @Test
    void fileLoaderTakesPriorityOverClasspathLoader() throws IOException {
        system.stop();
        FreemarkerTemplateEngine engine = new FreemarkerTemplateEngine();
        engine.setTemplateLoader(new FileTemplateLoader(new File("src/test/resources")));
        system = EnkanSystem.of("freemarker", engine);
        system.start();

        // file/hello.ftl exists in file loader; classpath loader would not find it under "templates/"
        HttpResponse response = engine.render("file/hello");
        assertThat(readResponse(response)).isEqualTo("Hello, Freemarker");
    }
}
