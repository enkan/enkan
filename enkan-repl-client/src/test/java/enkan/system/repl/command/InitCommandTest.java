package enkan.system.repl.command;

import enkan.system.EnkanSystem;
import enkan.system.ReplResponse;
import enkan.system.Transport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InitCommandTest {
    static class CapturingTransport implements Transport {
        final List<String> outLines = new ArrayList<>();
        final List<String> errLines = new ArrayList<>();
        final List<String> promptLines = new ArrayList<>();
        final Deque<String> inputs = new ArrayDeque<>();
        int recvCount;

        @Override
        public void send(ReplResponse response) {
            if (response.getOut() != null) outLines.add(response.getOut());
            if (response.getErr() != null) errLines.add(response.getErr());
        }

        @Override
        public void sendPrompt(String prompt) {
            promptLines.add(prompt);
        }

        @Override
        public String recv(long timeout) {
            recvCount++;
            return inputs.pollFirst();
        }
    }

    @Test
    void failsFastWhenLlmConnectionCheckFails() {
        InitCommand command = new InitCommand() {
            @Override
            void verifyApiReachable(String apiUrl, String apiKey) throws IOException {
                throw new IOException("connection refused");
            }
        };
        CapturingTransport transport = new CapturingTransport();
        System.setProperty("enkan.ai.apiKey", "dummy-key");
        try {
            command.execute(EnkanSystem.of(), transport);
        } finally {
            System.clearProperty("enkan.ai.apiKey");
        }

        assertThat(transport.errLines).anyMatch(line -> line.contains("Unable to connect to LLM API"));
        assertThat(transport.recvCount).isEqualTo(0);
    }

    @Test
    void firstPromptIsSingleLineWithoutContinuationNewline() {
        InitCommand command = new InitCommand() {
            @Override
            void verifyApiReachable(String apiUrl, String apiKey) {
            }

            @Override
            String reviewPlanInteractively(Transport transport, String description, String projectName, String groupId, String outputDir) {
                return "approved-plan";
            }

            @Override
            void generateWithApi(Transport transport, String description, String projectName, String groupId, Path outPath) {
            }
        };
        CapturingTransport transport = new CapturingTransport();
        transport.inputs.add("todo app");
        transport.inputs.add("todo");
        transport.inputs.add("com.example");
        transport.inputs.add("./todo");
        System.setProperty("enkan.ai.apiKey", "dummy-key");
        try {
            command.execute(EnkanSystem.of(), transport);
        } finally {
            System.clearProperty("enkan.ai.apiKey");
        }

        assertThat(transport.promptLines)
                .anyMatch(line -> line.contains("What kind of application do you want to build?"));
        assertThat(transport.promptLines)
                .noneMatch(line -> line.contains("\n> "));
    }

    @Test
    void cancelsImmediatelyWhenPromptInputIsInterrupted() {
        // Explicit verifyApiReachable override to keep the test independent from real connectivity.
        InitCommand command = new InitCommand() {
            @Override
            void verifyApiReachable(String apiUrl, String apiKey) {
            }
        };
        CapturingTransport transport = new CapturingTransport();
        // No inputs queued → recv() returns null → PromptAbortedException path
        System.setProperty("enkan.ai.apiKey", "dummy-key");
        try {
            command.execute(EnkanSystem.of(), transport);
        } finally {
            System.clearProperty("enkan.ai.apiKey");
        }

        assertThat(transport.errLines).anyMatch(line -> line.contains("Init cancelled."));
        assertThat(transport.recvCount).isEqualTo(1);
    }

    @Test
    void planReviewProceedsWithNaturalYes() {
        InitCommand command = new InitCommand() {
            @Override
            String generatePlanWithApi(String description, String projectName, String groupId,
                    String outputDir, Transport transport) {
                return "draft-plan";
            }
        };
        CapturingTransport transport = new CapturingTransport();
        transport.inputs.add("yes");

        String result = command.reviewPlanInteractively(transport, "desc", "app", "com.example", "./app");

        assertThat(result).isEqualTo("draft-plan");
    }

    @Test
    void planReviewCanReviseThenApprove() {
        InitCommand command = new InitCommand() {
            @Override
            String generatePlanWithApi(String description, String projectName, String groupId,
                    String outputDir, Transport transport) {
                return "plan-v1";
            }

            @Override
            String revisePlanWithApi(String description, String projectName, String groupId,
                    String outputDir, String currentPlan, String feedback, Transport transport) {
                return "plan-v2";
            }
        };
        CapturingTransport transport = new CapturingTransport();
        transport.inputs.add("DBはPostgreSQLにしたい");
        transport.inputs.add("yes");

        String result = command.reviewPlanInteractively(transport, "desc", "app", "com.example", "./app");

        assertThat(result).isEqualTo("plan-v2");
    }

    @Test
    void planReviewCanCancelByNaturalInput() {
        InitCommand command = new InitCommand() {
            @Override
            String generatePlanWithApi(String description, String projectName, String groupId,
                    String outputDir, Transport transport) {
                return "draft-plan";
            }
        };
        CapturingTransport transport = new CapturingTransport();
        transport.inputs.add("キャンセル");

        String result = command.reviewPlanInteractively(transport, "desc", "app", "com.example", "./app");

        assertThat(result).isNull();
    }

    @Test
    void markdownRendererConvertsHeadingsAndListMarkersForTerminal() {
        String markdown = """
                ## Plan
                * item1
                - [ ] item2
                ```java
                * keep
                ```
                """;

        String rendered = InitCommand.renderMarkdownForTerminal(markdown);

        assertThat(rendered).contains("Plan");
        assertThat(rendered).contains("- item1");
        assertThat(rendered).contains("[ ] item2");
        assertThat(rendered).contains("* keep");
        assertThat(rendered).doesNotContain("```");
    }

    @Test
    void detectsSpringMarkersInGeneratedText() {
        String bad = "import org.springframework.boot.SpringApplication;\n@SpringBootApplication";
        String good = "import enkan.system.EnkanSystem;";

        assertThat(InitCommand.containsForbiddenFramework(bad)).isTrue();
        assertThat(InitCommand.containsForbiddenFramework(good)).isFalse();
    }

    @Test
    void enkanVersionReturnsUnknownOutsidePackagedJar() {
        // When running from tests/IDE, the package has no Implementation-Version manifest.
        // The fallback should be "UNKNOWN" so stale values never silently leak into generated poms.
        String version = InitCommand.enkanVersion();
        assertThat(version).isNotBlank();
        // In a packaged jar this would be an actual version; in tests we expect the fallback.
        assertThat(version).isEqualTo("UNKNOWN");
    }

    @Test
    void inferProjectNameStripsStopWordsAndLimitsLength() {
        assertThat(InitCommand.inferProjectName("I want to build a todo app"))
                .isEqualTo("todo-app");
        assertThat(InitCommand.inferProjectName("a REST API for a bookstore"))
                .isEqualTo("rest-api-bookstore");
    }

    @Test
    void inferProjectNameFallsBackForEmptyOrOverlongInput() {
        assertThat(InitCommand.inferProjectName(""))
                .isEqualTo("my-app");
        // After stop-word removal, only stop words remain → empty → "my-app"
        assertThat(InitCommand.inferProjectName("a the for with and or"))
                .isEqualTo("my-app");
        // A long description exceeding 30 chars after cleaning falls back to "my-app"
        String longInput = "implementing distributed microservice orchestration platform system";
        assertThat(InitCommand.inferProjectName(longInput))
                .isEqualTo("my-app");
    }

    @Test
    void inferProjectNameHandlesNumbersAndSymbols() {
        // Non-alphanumeric is stripped, numbers are kept.
        assertThat(InitCommand.inferProjectName("chat v2.0 app!"))
                .isEqualTo("chat-v20-app");
    }

    @Test
    void isApprovalInputAcceptsEnglishAndJapanese() {
        assertThat(InitCommand.isApprovalInput("yes")).isTrue();
        assertThat(InitCommand.isApprovalInput("  YES  ")).isTrue();
        assertThat(InitCommand.isApprovalInput("y")).isTrue();
        assertThat(InitCommand.isApprovalInput("ok")).isTrue();
        assertThat(InitCommand.isApprovalInput("承認")).isTrue();
        assertThat(InitCommand.isApprovalInput("maybe")).isFalse();
        assertThat(InitCommand.isApprovalInput("")).isFalse();
    }

    @Test
    void isCancelInputAcceptsEnglishAndJapanese() {
        assertThat(InitCommand.isCancelInput("cancel")).isTrue();
        assertThat(InitCommand.isCancelInput("  Cancel ")).isTrue();
        assertThat(InitCommand.isCancelInput("キャンセル")).isTrue();
        assertThat(InitCommand.isCancelInput("中止")).isTrue();
        assertThat(InitCommand.isCancelInput("proceed")).isFalse();
        assertThat(InitCommand.isCancelInput("")).isFalse();
    }

    @Test
    void markdownRendererHandlesNullAndBlank() {
        assertThat(InitCommand.renderMarkdownForTerminal(null)).isEmpty();
        assertThat(InitCommand.renderMarkdownForTerminal("")).isEmpty();
        assertThat(InitCommand.renderMarkdownForTerminal("   \n  ")).isEmpty();
    }

    @Test
    void forbiddenFrameworkDetectionHandlesNullAndBlank() {
        assertThat(InitCommand.containsForbiddenFramework(null)).isFalse();
        assertThat(InitCommand.containsForbiddenFramework("")).isFalse();
        assertThat(InitCommand.containsForbiddenFramework("   ")).isFalse();
    }

    @Test
    void forbiddenFrameworkDetectionIsCaseInsensitive() {
        assertThat(InitCommand.containsForbiddenFramework("ORG.SPRINGFRAMEWORK.boot.Foo")).isTrue();
        assertThat(InitCommand.containsForbiddenFramework("@SpringBootApplication")).isTrue();
        assertThat(InitCommand.containsForbiddenFramework("SpringApplication.run(App.class)")).isTrue();
    }

    @Test
    void extractFilePathHandlesMarkdownHeaderFormats() {
        assertThat(InitCommand.extractFilePath("### src/main/java/Foo.java"))
                .isEqualTo("src/main/java/Foo.java");
        assertThat(InitCommand.extractFilePath("**src/main/java/Foo.java**"))
                .isEqualTo("src/main/java/Foo.java");
        assertThat(InitCommand.extractFilePath("**`pom.xml`**"))
                .isEqualTo("pom.xml");
        // Non-path lines should return null.
        assertThat(InitCommand.extractFilePath("## Heading"))
                .isNull();
        assertThat(InitCommand.extractFilePath("### not a path"))
                .isNull();
        assertThat(InitCommand.extractFilePath("### https://example.com/foo"))
                .isNull();
    }

    @Test
    void isFixedTemplateFileRecognizesSkippableTemplates() {
        assertThat(InitCommand.isFixedTemplateFile("pom.xml")).isTrue();
        assertThat(InitCommand.isFixedTemplateFile("src/dev/java/com/example/DevMain.java")).isTrue();
        assertThat(InitCommand.isFixedTemplateFile("src/main/java/com/example/Main.java")).isTrue();
        assertThat(InitCommand.isFixedTemplateFile("src/main/java/com/example/TodoSystemFactory.java")).isTrue();
        // ApplicationFactory, JsonBodyReader, JsonBodyWriter are now also fixed templates
        // because the generator writes them deterministically — the LLM must not replace them.
        assertThat(InitCommand.isFixedTemplateFile("src/main/java/com/example/TodoApplicationFactory.java")).isTrue();
        assertThat(InitCommand.isFixedTemplateFile("src/main/java/com/example/jaxrs/JsonBodyReader.java")).isTrue();
        assertThat(InitCommand.isFixedTemplateFile("src/main/java/com/example/jaxrs/JsonBodyWriter.java")).isTrue();
        // Backslash path separators (Windows-style) are normalized.
        assertThat(InitCommand.isFixedTemplateFile("src\\main\\java\\com\\example\\TodoSystemFactory.java")).isTrue();
        // Files that the LLM generates are not skipped.
        assertThat(InitCommand.isFixedTemplateFile("src/main/java/com/example/RoutesDef.java")).isFalse();
        assertThat(InitCommand.isFixedTemplateFile("src/main/java/com/example/TodoController.java")).isFalse();
    }

    @Test
    void unescapeJsonHandlesStandardEscapes() {
        assertThat(InitCommand.unescapeJson("hello\\nworld")).isEqualTo("hello\nworld");
        assertThat(InitCommand.unescapeJson("tab\\there")).isEqualTo("tab\there");
        assertThat(InitCommand.unescapeJson("quote\\\"in\\\"string")).isEqualTo("quote\"in\"string");
        assertThat(InitCommand.unescapeJson("back\\\\slash")).isEqualTo("back\\slash");
        assertThat(InitCommand.unescapeJson("cr\\r\\nlf")).isEqualTo("cr\r\nlf");
    }

    @Test
    void unescapeJsonHandlesTrailingBackslashAndUnknownEscape() {
        // Trailing lone backslash is kept verbatim (no out-of-bounds read).
        assertThat(InitCommand.unescapeJson("trailing\\")).isEqualTo("trailing\\");
        // Unknown escape sequences are preserved as-is (backslash + next char).
        assertThat(InitCommand.unescapeJson("\\z")).isEqualTo("\\z");
        assertThat(InitCommand.unescapeJson("")).isEqualTo("");
    }

    @Test
    void extractJsonStringFieldExtractsAndExcludes() {
        InitCommand cmd = new InitCommand();
        String json = "{\"content\":\"hello\",\"other\":\"skip\"}";
        assertThat(cmd.extractJsonStringField(json, "\"content\":\"", null))
                .isEqualTo("hello");
        // excludeKey skips reasoning_content's embedded "content":"..." substring.
        String reasoning = "{\"reasoning_content\":\"deep\"}";
        assertThat(cmd.extractJsonStringField(reasoning, "\"content\":\"", "\"reasoning_content\""))
                .isNull();
        assertThat(cmd.extractJsonStringField(reasoning, "\"reasoning_content\":\"", null))
                .isEqualTo("deep");
        // Missing key returns null.
        assertThat(cmd.extractJsonStringField("{}", "\"content\":\"", null))
                .isNull();
    }

    @Test
    void writeGeneratedFilesWritesCodeBlocksUnderOutPath(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        InitCommand cmd = new InitCommand();
        String response = """
                ### src/main/java/App.java
                ```java
                public class App {}
                ```
                ### src/main/java/Util.java
                ```java
                public class Util {}
                ```
                """;
        CapturingTransport transport = new CapturingTransport();
        int written = cmd.writeGeneratedFiles(tmp, response, transport);

        assertThat(written).isEqualTo(2);
        assertThat(java.nio.file.Files.readString(tmp.resolve("src/main/java/App.java")))
                .contains("public class App {}");
        assertThat(java.nio.file.Files.readString(tmp.resolve("src/main/java/Util.java")))
                .contains("public class Util {}");
    }

    @Test
    void writeGeneratedFilesRejectsPathTraversal(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        InitCommand cmd = new InitCommand();
        String response = """
                ### ../evil.java
                ```java
                malicious
                ```
                """;
        CapturingTransport transport = new CapturingTransport();
        int written = cmd.writeGeneratedFiles(tmp, response, transport);

        assertThat(written).isZero();
        // The evil file must not exist anywhere outside tmp.
        assertThat(java.nio.file.Files.exists(tmp.getParent().resolve("evil.java"))).isFalse();
    }

    @Test
    void resolveChatCompletionUriAppendsPathWhenMissing() {
        InitCommand withBase = new InitCommand();
        withBase.apiUrl = "https://api.example.com/v1";
        assertThat(withBase.resolveChatCompletionUri().toString())
                .isEqualTo("https://api.example.com/v1/chat/completions");

        InitCommand withTrailingSlash = new InitCommand();
        withTrailingSlash.apiUrl = "https://api.example.com/v1/";
        assertThat(withTrailingSlash.resolveChatCompletionUri().toString())
                .isEqualTo("https://api.example.com/v1/chat/completions");

        InitCommand withFullPath = new InitCommand();
        withFullPath.apiUrl = "https://api.example.com/v1/chat/completions";
        assertThat(withFullPath.resolveChatCompletionUri().toString())
                .isEqualTo("https://api.example.com/v1/chat/completions");
    }

    @Test
    void writeGeneratedFilesConsumesFencedBlockWhenSkippingFixedTemplate(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        // Regression: when a fixed-template file is skipped, the parser must still
        // consume its fenced code block so that inner lines beginning with "### " or
        // "**" are not misinterpreted as new file headers.
        InitCommand cmd = new InitCommand();
        String response = """
                ### pom.xml
                ```xml
                <!-- ### src/evil/Injected.java -->
                <!-- **`src/evil/Bold.java`** -->
                <project/>
                ```
                ### src/main/java/com/example/Real.java
                ```java
                public class Real {}
                ```
                """;
        CapturingTransport transport = new CapturingTransport();
        int written = cmd.writeGeneratedFiles(tmp, response, transport);

        assertThat(written).isEqualTo(1);
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/evil/Injected.java"))).isFalse();
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/evil/Bold.java"))).isFalse();
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/main/java/com/example/Real.java"))).isTrue();
    }

    @Test
    void writeGeneratedFilesSkipsFixedTemplateFiles(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        InitCommand cmd = new InitCommand();
        String response = """
                ### pom.xml
                ```xml
                <project/>
                ```
                ### src/main/java/com/example/FooSystemFactory.java
                ```java
                public class FooSystemFactory {}
                ```
                ### src/main/java/com/example/FooApplicationFactory.java
                ```java
                public class FooApplicationFactory {}
                ```
                ### src/main/java/com/example/jaxrs/JsonBodyReader.java
                ```java
                public class JsonBodyReader {}
                ```
                ### src/main/java/com/example/RoutesDef.java
                ```java
                public class RoutesDef {}
                ```
                ### src/main/java/com/example/FooController.java
                ```java
                public class FooController {}
                ```
                """;
        CapturingTransport transport = new CapturingTransport();
        int written = cmd.writeGeneratedFiles(tmp, response, transport);

        // Only RoutesDef and FooController are written; the rest are fixed templates.
        assertThat(written).isEqualTo(2);
        assertThat(java.nio.file.Files.exists(tmp.resolve("pom.xml"))).isFalse();
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/main/java/com/example/FooSystemFactory.java"))).isFalse();
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/main/java/com/example/FooApplicationFactory.java"))).isFalse();
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/main/java/com/example/jaxrs/JsonBodyReader.java"))).isFalse();
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/main/java/com/example/RoutesDef.java"))).isTrue();
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/main/java/com/example/FooController.java"))).isTrue();
    }

    // ------------------------------------------------------------------------
    //  Standalone POM template (§1 of the init-command rewrite)
    // ------------------------------------------------------------------------

    @Test
    void pomTemplateIsStandaloneAndHasNoParent() {
        String pom = InitCommand.pomTemplate("com.example", "todo", "com.example.todo", "1.2.3");
        // No <parent> element — external projects cannot inherit from enkan-parent.
        assertThat(pom).doesNotContain("<parent>");
        assertThat(pom).doesNotContain("enkan-parent");
        // Required properties and compiler config.
        assertThat(pom).contains("<maven.compiler.release>25</maven.compiler.release>");
        assertThat(pom).contains("<enkan.version>1.2.3</enkan.version>");
        // Default jOOQ+Raoh stack dependencies.
        assertThat(pom).contains("<artifactId>enkan-component-jooq</artifactId>");
        assertThat(pom).contains("<artifactId>enkan-component-HikariCP</artifactId>");
        assertThat(pom).contains("<artifactId>enkan-component-flyway</artifactId>");
        assertThat(pom).contains("<artifactId>raoh-jooq</artifactId>");
        assertThat(pom).contains("<groupId>com.h2database</groupId>");
        // Dev profile is still present for REPL/exec:exec.
        assertThat(pom).contains("<id>dev</id>");
        assertThat(pom).contains("com.example.todo.DevMain");
    }

    @Test
    void pomTemplateIsValidXml() throws Exception {
        String pom = InitCommand.pomTemplate("com.example", "todo", "com.example.todo", "1.2.3");
        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        var doc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(pom.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("project");
    }

    // ------------------------------------------------------------------------
    //  enkanVersion resolution (§1)
    // ------------------------------------------------------------------------

    @Test
    void enkanVersionHonoursSystemPropertyOverride() {
        System.setProperty("enkan.version", "9.9.9-TEST");
        try {
            assertThat(InitCommand.enkanVersion()).isEqualTo("9.9.9-TEST");
        } finally {
            System.clearProperty("enkan.version");
        }
    }

    @Test
    void resolveEnkanVersionOrAbortReturnsNullAndErrorsWhenUnknown() {
        // The existing enkanVersionReturnsUnknownOutsidePackagedJar test already establishes
        // that enkanVersion() returns "UNKNOWN" in the default test environment. We rely on
        // that here instead of stubbing, to keep the abort path exercised end-to-end.
        assertThat(InitCommand.enkanVersion()).isEqualTo("UNKNOWN");
        InitCommand cmd = new InitCommand();
        CapturingTransport transport = new CapturingTransport();
        String result = cmd.resolveEnkanVersionOrAbort(transport);
        assertThat(result).isNull();
        assertThat(transport.errLines)
                .anyMatch(l -> l.contains("Cannot determine Enkan version"));
        assertThat(transport.errLines)
                .anyMatch(l -> l.contains("-Denkan.version"));
    }

    // ------------------------------------------------------------------------
    //  SystemFactory and ApplicationFactory templates (§2, §3)
    // ------------------------------------------------------------------------

    @Test
    void systemFactoryTemplateWiresJooqHikariFlyway() {
        String sf = InitCommand.systemFactoryTemplate("com.example.todo", "TodoSystemFactory", "todo");
        assertThat(sf).contains("import enkan.component.hikaricp.HikariCPComponent;");
        assertThat(sf).contains("import enkan.component.jooq.JooqProvider;");
        assertThat(sf).contains("import enkan.component.flyway.FlywayMigration;");
        assertThat(sf).contains("import org.jooq.SQLDialect;");
        assertThat(sf).contains("JooqProvider::setDialect, SQLDialect.H2");
        assertThat(sf).contains("component(\"jooq\").using(\"datasource\", \"flyway\")");
        assertThat(sf).contains("component(\"flyway\").using(\"datasource\")");
        assertThat(sf).contains("com.example.todo.TodoApplicationFactory");
    }

    @Test
    void applicationFactoryTemplateContainsJooqMiddlewareAndDelegatesToRoutesDef() {
        String af = InitCommand.applicationFactoryTemplate("com.example.todo", "TodoApplicationFactory");
        assertThat(af).contains("import enkan.middleware.jooq.JooqDslContextMiddleware;");
        assertThat(af).contains("import enkan.middleware.jooq.JooqTransactionMiddleware;");
        assertThat(af).contains("new JooqDslContextMiddleware<>()");
        assertThat(af).contains("new JooqTransactionMiddleware<>()");
        // Routes come from a generated RoutesDef class — the LLM only has to write that.
        assertThat(af).contains("RoutesDef.routes()");
        // The JSON body reader/writer imports refer to the generated jaxrs sub-package.
        assertThat(af).contains("import com.example.todo.jaxrs.JsonBodyReader;");
        assertThat(af).contains("import com.example.todo.jaxrs.JsonBodyWriter;");
    }

    @Test
    void jsonBodyReaderAndWriterTemplatesLandInJaxrsSubPackage() {
        String reader = InitCommand.jsonBodyReaderTemplate("com.example.todo");
        String writer = InitCommand.jsonBodyWriterTemplate("com.example.todo");
        assertThat(reader).startsWith("package com.example.todo.jaxrs;");
        assertThat(writer).startsWith("package com.example.todo.jaxrs;");
        assertThat(reader).contains("implements MessageBodyReader<T>");
        assertThat(writer).contains("implements MessageBodyWriter<T>");
    }

    // ------------------------------------------------------------------------
    //  Compile-fix loop: POM error parsing (§5)
    // ------------------------------------------------------------------------

    @Test
    void extractFileFromErrorParsesJavaCompilerFormat(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("src/main/java/com/example/Foo.java");
        java.nio.file.Files.createDirectories(source.getParent());
        java.nio.file.Files.writeString(source, "class Foo {}");
        String line = "[ERROR] " + source + ":[3,14] cannot find symbol";

        Path result = InitCommand.extractFileFromError(line, tmp);

        assertThat(result).isEqualTo(source.toAbsolutePath().normalize());
    }

    @Test
    void extractFileFromErrorParsesMavenModelFormat(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path pom = tmp.resolve("pom.xml");
        java.nio.file.Files.writeString(pom, "<project/>");
        String line = "[FATAL] Non-resolvable parent POM for foo:bar:1.0-SNAPSHOT: "
                + "The artifact X could not be resolved @ " + pom + ", line 6, column 13";

        Path result = InitCommand.extractFileFromError(line, tmp);

        assertThat(result).isEqualTo(pom.toAbsolutePath().normalize());
    }

    @Test
    void extractFileFromErrorReturnsNullForUnparseableLines() {
        assertThat(InitCommand.extractFileFromError("INFO] nothing here", Path.of("/tmp"))).isNull();
        assertThat(InitCommand.extractFileFromError("[ERROR] some gibberish without a path", Path.of("/tmp"))).isNull();
    }

    @Test
    void buildFixUserPromptIncludesPomByDefaultForValidatePhase(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path pom = tmp.resolve("pom.xml");
        java.nio.file.Files.writeString(pom, "<project>...</project>");
        InitCommand.BuildResult result = new InitCommand.BuildResult(
                InitCommand.BuildPhase.VALIDATE,
                "[FATAL] Non-resolvable parent POM...");

        String prompt = InitCommand.buildFixUserPrompt(result, tmp);

        assertThat(prompt).contains("### pom.xml");
        assertThat(prompt).contains("<project>...</project>");
    }

    // ------------------------------------------------------------------------
    //  Generation prompt (§4)
    // ------------------------------------------------------------------------

    /** Offline stub: skip the GitHub fetch so the prompt tests do not require network. */
    private static InitCommand offlineInitCommand() {
        return new InitCommand() {
            @Override
            java.util.Map<String, String> fetchAllReferences() {
                return java.util.Map.of();
            }
        };
    }

    @Test
    void planPromptMentionsJooqRaohAsDefaultStack() {
        String planner = offlineInitCommand().buildPlannerSystemPrompt();
        assertThat(planner).contains("jOOQ");
        assertThat(planner).contains("Raoh");
        assertThat(planner).contains("HikariCP");
        assertThat(planner).contains("Flyway");
    }

    @Test
    void generationPromptIncludesJooqReferencesForDefaultStack() {
        String[] prompts = offlineInitCommand()
                .buildPrompt("A TODO REST API", "todo", "com.example", Path.of("/tmp/todo"));
        String sys = prompts[0];
        assertThat(sys).contains("JooqRecordDecoders");
        assertThat(sys).contains("DSLContext");
        assertThat(sys).contains("jooqDslContext");
        assertThat(sys).contains("RoutesDef.routes()");
    }

    @Test
    void generationPromptOpensJpaEscapeHatchWhenUserAsks() {
        String[] prompts = offlineInitCommand()
                .buildPrompt("A REST API using JPA/Hibernate", "blog", "com.example", Path.of("/tmp/blog"));
        String user = prompts[1];
        assertThat(user).contains("JPA/Hibernate");
        assertThat(user).contains("jakarta.persistence");
    }

    // ------------------------------------------------------------------------
    //  Post-generation validation (§6)
    // ------------------------------------------------------------------------

    @Test
    void validateGenerationDetectsMissingRoutesDef(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        InitCommand cmd = new InitCommand();
        // Only a controller, no RoutesDef.
        java.nio.file.Files.createDirectories(tmp.resolve("src/main/java/com/example"));
        java.nio.file.Files.writeString(tmp.resolve("src/main/java/com/example/FooController.java"),
                "package com.example; public class FooController {}");

        List<String> issues = cmd.validateGeneration(tmp, "", "com.example", "todo app");

        assertThat(issues).anyMatch(i -> i.contains("RoutesDef.java"));
    }

    @Test
    void validateGenerationDetectsControllerReferencedButNotGenerated(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        InitCommand cmd = new InitCommand();
        java.nio.file.Files.createDirectories(tmp.resolve("src/main/java/com/example"));
        java.nio.file.Files.writeString(tmp.resolve("src/main/java/com/example/RoutesDef.java"),
                "package com.example;\n"
                        + "import kotowari.routing.Routes;\n"
                        + "public final class RoutesDef {\n"
                        + "    public static Routes routes() {\n"
                        + "        return Routes.define(r -> { r.get(\"/\").to(MissingController.class, \"index\"); }).compile();\n"
                        + "    }\n"
                        + "}\n");

        List<String> issues = cmd.validateGeneration(tmp, "", "com.example", "todo app");

        assertThat(issues).anyMatch(i -> i.contains("MissingController"));
    }

    @Test
    void validateGenerationFlagsForbiddenImports(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        InitCommand cmd = new InitCommand();
        java.nio.file.Files.createDirectories(tmp.resolve("src/main/java/com/example"));
        java.nio.file.Files.writeString(tmp.resolve("src/main/java/com/example/BadController.java"),
                "package com.example;\n"
                        + "import org.springframework.web.bind.annotation.RestController;\n"
                        + "import org.seasar.doma.Dao;\n"
                        + "import lombok.Data;\n"
                        + "public class BadController {}\n");
        // RoutesDef present so we isolate the import check.
        java.nio.file.Files.writeString(tmp.resolve("src/main/java/com/example/RoutesDef.java"),
                "package com.example; public final class RoutesDef { public static Object routes() { return null; } }");

        List<String> issues = cmd.validateGeneration(tmp, "", "com.example", "todo app");

        assertThat(issues).anyMatch(i -> i.contains("org.springframework"));
        assertThat(issues).anyMatch(i -> i.contains("org.seasar.doma"));
        assertThat(issues).anyMatch(i -> i.contains("lombok"));
    }

    @Test
    void validateGenerationAllowsJpaImportsWhenUserRequested(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        InitCommand cmd = new InitCommand();
        java.nio.file.Files.createDirectories(tmp.resolve("src/main/java/com/example"));
        java.nio.file.Files.writeString(tmp.resolve("src/main/java/com/example/User.java"),
                "package com.example;\nimport jakarta.persistence.Entity;\n@Entity public class User {}");
        java.nio.file.Files.writeString(tmp.resolve("src/main/java/com/example/RoutesDef.java"),
                "package com.example; public final class RoutesDef { public static Object routes() { return null; } }");

        List<String> issues = cmd.validateGeneration(tmp, "", "com.example", "A REST API using JPA/Hibernate");

        assertThat(issues).noneMatch(i -> i.contains("jakarta.persistence"));
    }

    @Test
    void parseManifestExtractsFilePaths() {
        String response = """
                ### src/main/java/Foo.java
                ```java
                class Foo {}
                ```
                ### MANIFEST
                ```
                src/main/java/Foo.java
                src/main/resources/db/migration/V1__init.sql
                ```
                """;

        assertThat(InitCommand.parseManifest(response))
                .containsExactly("src/main/java/Foo.java", "src/main/resources/db/migration/V1__init.sql");
    }

    @Test
    void parseManifestReturnsEmptyWhenMissing() {
        String response = """
                ### src/main/java/Foo.java
                ```java
                class Foo {}
                ```
                """;

        assertThat(InitCommand.parseManifest(response)).isEmpty();
    }

    // ------------------------------------------------------------------------
    //  init-reference classpath resources (Phase 1 static reference)
    // ------------------------------------------------------------------------

    @Test
    void loadInitReferenceLoadsAllDeclaredResourcesFromClasspath() {
        java.util.Map<String, String> refs = new InitCommand().loadInitReference();

        // Every resource must load, be non-empty, and be keyed by its normalized path.
        assertThat(refs).isNotEmpty();
        assertThat(refs).containsKeys(
                "init-reference/README.md",
                "init-reference/imports.md",
                "init-reference/patterns/routes.md",
                "init-reference/patterns/controller.md",
                "init-reference/patterns/raoh-decoder.md",
                "init-reference/patterns/domain-record.md",
                "init-reference/patterns/flyway-migration.md",
                "init-reference/components/jooq.md",
                "init-reference/components/overview.md");
        refs.forEach((path, content) -> {
            assertThat(content)
                    .as("content for %s", path)
                    .isNotBlank();
        });
    }

    @Test
    void loadInitReferenceDoesNotUseStaleResultDotOkSyntaxInCodeBlocks() {
        // Regression guard: `Ok` and `Err` are top-level records in net.unit8.raoh,
        // NOT nested in Result. Reference files are allowed to mention `Result.Ok`
        // in anti-example text ("do NOT write ..."), but MUST NOT use it inside
        // fenced code blocks, since that would teach the LLM the wrong pattern.
        java.util.Map<String, String> refs = new InitCommand().loadInitReference();
        refs.forEach((path, content) -> {
            boolean inCode = false;
            int lineNo = 0;
            for (String line : content.split("\n", -1)) {
                lineNo++;
                if (line.trim().startsWith("```")) {
                    inCode = !inCode;
                    continue;
                }
                if (inCode) {
                    assertThat(line)
                            .as("file %s line %d (inside code block) must not use Result.Ok / Result.Err",
                                    path, lineNo)
                            .doesNotContain("Result.Ok")
                            .doesNotContain("Result.Err");
                }
            }
        });
    }

    @Test
    void loadInitReferenceContainsKeyCanonicalTerms() {
        java.util.Map<String, String> refs = new InitCommand().loadInitReference();
        String combined = String.join("\n", refs.values());

        // Routing + controller plumbing
        assertThat(combined).contains("RoutesDef");
        assertThat(combined).contains("Routes.define");
        assertThat(combined).contains("jooqDslContext");
        assertThat(combined).contains("request.getExtension(\"jooqDslContext\")");

        // Raoh API — the exact decoders the LLM needs
        assertThat(combined).contains("JooqRecordDecoders");
        assertThat(combined).contains("combine(");
        assertThat(combined).contains("field(");
        assertThat(combined).contains("long_()");
        assertThat(combined).contains("string()");
        assertThat(combined).contains("bool()");

        // Result sum type imports
        assertThat(combined).contains("net.unit8.raoh.Ok");
        assertThat(combined).contains("net.unit8.raoh.Err");

        // HTTP type package — small models get this wrong
        assertThat(combined).contains("enkan.web.data.HttpRequest");

        // Transactional annotation source
        assertThat(combined).contains("jakarta.transaction.Transactional");

        // Flyway migration layout
        assertThat(combined).contains("V1__");
        assertThat(combined).contains("db/migration");
    }

    @Test
    void generationPromptEmbedsCanonicalPatternContent() {
        // Do NOT stub loadInitReference — we want to prove that the real classpath
        // resources reach the prompt output.
        InitCommand cmd = new InitCommand() {
            @Override
            java.util.Map<String, String> fetchAllReferences() {
                return java.util.Map.of();  // skip GitHub fetch, keep classpath load
            }
        };
        String[] prompts = cmd.buildPrompt("A TODO REST API", "todo", "com.example", Path.of("/tmp/todo"));
        String sys = prompts[0];

        // A line that is unique to each reference file, proving it was embedded.
        assertThat(sys).contains("init-reference/patterns/routes.md");
        assertThat(sys).contains("init-reference/patterns/controller.md");
        assertThat(sys).contains("init-reference/patterns/raoh-decoder.md");
        assertThat(sys).contains("Routes.define(r ->");
        assertThat(sys).contains("Todo.DECODER");
        assertThat(sys).contains("field(\"title\"");

        // The "CANONICAL PATTERNS (authoritative" banner should be present so the
        // LLM can find the section.
        assertThat(sys).contains("CANONICAL PATTERNS");
    }

    @Test
    void plannerPromptEmbedsCanonicalPatternContent() {
        InitCommand cmd = new InitCommand() {
            @Override
            java.util.Map<String, String> fetchAllReferences() {
                return java.util.Map.of();
            }
        };
        String planner = cmd.buildPlannerSystemPrompt();

        assertThat(planner).contains("CANONICAL PATTERNS");
        assertThat(planner).contains("init-reference/patterns/routes.md");
        assertThat(planner).contains("RoutesDef");
        // The planner should also clearly separate legacy references.
        assertThat(planner).contains("LEGACY REFERENCE");
    }
}
