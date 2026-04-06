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
        final Deque<String> inputs = new ArrayDeque<>();
        int recvCount;

        @Override
        public void send(ReplResponse response) {
            if (response.getOut() != null) outLines.add(response.getOut());
            if (response.getErr() != null) errLines.add(response.getErr());
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

        assertThat(transport.outLines)
                .anyMatch(line -> line.contains("What kind of application do you want to build?"));
        assertThat(transport.outLines)
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
            String generatePlanWithApi(String apiUrl, String apiKey, String model,
                    String description, String projectName, String groupId, String outputDir,
                    Transport transport) {
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
            String generatePlanWithApi(String apiUrl, String apiKey, String model,
                    String description, String projectName, String groupId, String outputDir,
                    Transport transport) {
                return "plan-v1";
            }

            @Override
            String revisePlanWithApi(String apiUrl, String apiKey, String model, String description,
                    String projectName, String groupId, String outputDir, String currentPlan, String feedback,
                    Transport transport) {
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
            String generatePlanWithApi(String apiUrl, String apiKey, String model,
                    String description, String projectName, String groupId, String outputDir,
                    Transport transport) {
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
}
