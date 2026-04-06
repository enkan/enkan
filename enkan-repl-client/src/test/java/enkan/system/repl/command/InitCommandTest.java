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
        // Backslash path separators (Windows-style) are normalized.
        assertThat(InitCommand.isFixedTemplateFile("src\\main\\java\\com\\example\\TodoSystemFactory.java")).isTrue();
        // Files that should be generated by the AI are not skipped.
        assertThat(InitCommand.isFixedTemplateFile("src/main/java/com/example/TodoApplicationFactory.java")).isFalse();
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
                ### src/main/java/com/example/FooController.java
                ```java
                public class FooController {}
                ```
                """;
        CapturingTransport transport = new CapturingTransport();
        int written = cmd.writeGeneratedFiles(tmp, response, transport);

        // Only the controller is written; pom.xml and *SystemFactory.java are fixed templates.
        assertThat(written).isEqualTo(1);
        assertThat(java.nio.file.Files.exists(tmp.resolve("pom.xml"))).isFalse();
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/main/java/com/example/FooSystemFactory.java"))).isFalse();
        assertThat(java.nio.file.Files.exists(tmp.resolve("src/main/java/com/example/FooController.java"))).isTrue();
    }
}
