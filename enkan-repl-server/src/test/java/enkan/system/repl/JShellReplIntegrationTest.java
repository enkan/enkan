package enkan.system.repl;

import enkan.system.ReplResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that start a real {@link JShellRepl} server and communicate
 * with it using {@link ReplTestClient}, verifying command execution and completion.
 *
 * <p>The test uses {@link MinimalSystemFactory} — an empty EnkanSystem — to keep
 * startup time as short as possible.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JShellReplIntegrationTest {
    private JShellRepl repl;
    private ExecutorService replThread;
    private ReplTestClient client;

    @BeforeAll
    void startServer() throws Exception {
        repl = new JShellRepl(MinimalSystemFactory.class.getName());
        replThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-repl-server");
            t.setDaemon(true);
            return t;
        });
        replThread.submit(repl);

        int port = repl.getPort(); // blocks until the server is listening
        client = new ReplTestClient(port);

        // Warm up: wait for JShell to finish internal initialization.
        // The first command may arrive before JShell is ready to evaluate,
        // causing empty responses on slow CI environments.
        client.send("/help");
    }

    @AfterAll
    void stopServer() throws Exception {
        if (client != null) client.close();
        if (replThread != null) {
            replThread.shutdownNow();
            replThread.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    // -----------------------------------------------------------------------
    // Command execution
    // -----------------------------------------------------------------------

    @Test
    void helpCommandReturnsAtLeastOneLine() {
        List<ReplResponse> responses = client.send("/help");

        // At least one response must carry a non-null out or err field
        boolean hasContent = responses.stream()
                .anyMatch(r -> r.getOut() != null || r.getErr() != null);
        assertThat(hasContent).isTrue();

        // Last response must carry DONE
        ReplResponse last = responses.get(responses.size() - 1);
        assertThat(last.getStatus()).contains(ReplResponse.ResponseStatus.DONE);
    }

    @Test
    void expressionEvaluationReturnsResult() {
        List<ReplResponse> responses = client.send("1 + 1");

        // The value "2" must appear somewhere in the out frames
        boolean hasResult = responses.stream()
                .anyMatch(r -> "2".equals(r.getOut()));
        assertThat(hasResult)
                .as("Expected '2' in response out fields, got: %s", responses)
                .isTrue();

        ReplResponse last = responses.get(responses.size() - 1);
        assertThat(last.getStatus()).contains(ReplResponse.ResponseStatus.DONE);
    }

    @Test
    void unknownCommandReturnsErrorOrNoResult() {
        List<ReplResponse> responses = client.send("/nonexistentcommand");

        // Server must still terminate with DONE (not hang)
        ReplResponse last = responses.get(responses.size() - 1);
        assertThat(last.getStatus()).contains(ReplResponse.ResponseStatus.DONE);
    }

    @Test
    void multiTokenExpressionEvaluatesCorrectly() {
        List<ReplResponse> responses = client.send("var x = 42");

        // Variable declaration produces no value output (JShell returns "")
        // but must complete with DONE
        ReplResponse last = responses.get(responses.size() - 1);
        assertThat(last.getStatus()).contains(ReplResponse.ResponseStatus.DONE);
    }

    // -----------------------------------------------------------------------
    // Status command
    // -----------------------------------------------------------------------

    @Test
    void statusCommandReportsStoppedBeforeStart() {
        List<ReplResponse> responses = client.send("/status");

        boolean hasStopped = responses.stream()
                .anyMatch(r -> r.getOut() != null && r.getOut().contains("stopped"));
        assertThat(hasStopped)
                .as("Expected 'stopped' in /status response before start, got: %s", responses)
                .isTrue();

        ReplResponse last = responses.get(responses.size() - 1);
        assertThat(last.getStatus()).contains(ReplResponse.ResponseStatus.DONE);
    }

    @Test
    void statusCommandReportsStartedAfterStart() {
        client.send("/start");
        try {
            List<ReplResponse> responses = client.send("/status");

            boolean hasStarted = responses.stream()
                    .anyMatch(r -> r.getOut() != null && r.getOut().contains("started"));
            assertThat(hasStarted)
                    .as("Expected 'started' in /status response after /start, got: %s", responses)
                    .isTrue();
        } finally {
            client.send("/stop");
        }
    }

    // -----------------------------------------------------------------------
    // Completer
    // -----------------------------------------------------------------------

    @Test
    void completerPortIsReturnedByCompleterCommand() {
        int completerPort = client.fetchCompleterPort();
        assertThat(completerPort).isBetween(1024, 65535);
    }

    @Test
    void commandCompletionForSlashReturnsBuiltinCommands() {
        int completerPort = client.fetchCompleterPort();
        List<String> candidates = client.complete(completerPort, "/", 1);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates).allMatch(c -> c.startsWith("/"));
        // Built-in commands registered in JShellRepl
        assertThat(candidates).anyMatch(c -> c.equals("/start") || c.equals("/stop") || c.equals("/help"));
    }

    @Test
    void commandCompletionFiltersOnPrefix() {
        int completerPort = client.fetchCompleterPort();
        List<String> candidates = client.complete(completerPort, "/st", 3);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates).allMatch(c -> c.startsWith("/st"));
    }

    @Test
    void javaExpressionCompletionSuggestsSystemClass() {
        int completerPort = client.fetchCompleterPort();
        List<String> candidates = client.complete(completerPort, "Sys", 3);

        assertThat(candidates)
                .as("JShell completion for 'Sys' should include 'System'")
                .anyMatch(c -> c.contains("System"));
    }
}
