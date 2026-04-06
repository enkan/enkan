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
        InitCommand command = new InitCommand() {
            @Override
            void verifyApiReachable(String apiUrl, String apiKey) {
            }
        };
        CapturingTransport transport = new CapturingTransport();
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
}
