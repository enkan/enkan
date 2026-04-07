package enkan.system.repl.client;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: typing {@code /connect <bad port>} where nothing is listening
 * must NOT silently terminate the REPL. The user should see a clear error
 * message and the REPL should still be available to accept the next command.
 */
class ReplClientConnectFailureTest {

    /**
     * Picks a port number that no process should be listening on. We bind to
     * an ephemeral port and immediately close, then assume the port stays free
     * for the duration of the test (race-tolerant: even if something else binds
     * later, the test still exercises the failure path).
     */
    private static int unusedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static LineReader newDumbLineReader(ByteArrayOutputStream out) throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .streams(new ByteArrayInputStream(new byte[0]), out)
                .dumb(true)
                .build();
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }

    @Test
    @Timeout(value = 15, unit = java.util.concurrent.TimeUnit.SECONDS)
    void connectToNonListeningPortDoesNotKillRepl() throws Exception {
        int badPort = unusedPort();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        LineReader reader = newDumbLineReader(captured);

        ReplClient.ConsoleHandler handler = new ReplClient.ConsoleHandler(reader);
        try {
            handler.connect("localhost", badPort);

            // Give async monitor / poll one beat to surface its result.
            // (connect() should already be synchronous in returning, but
            // belt-and-braces.)
            Thread.sleep(200);

            // The REPL must still be available — i.e. the connect failure
            // must not have set isAvailable=false.
            assertThat(handler.isAvailable())
                    .as("REPL must remain available after a failed /connect")
                    .isTrue();

            // The user must see *some* error message in the terminal output.
            String terminalOutput = captured.toString(StandardCharsets.UTF_8);
            assertThat(terminalOutput)
                    .as("Terminal output should explain the connect failure, got: <%s>",
                            terminalOutput)
                    .containsIgnoringCase("connect");

            // connectedPort should remain unset (-1) so the prompt still shows the
            // disconnected indicator.
            assertThat(handler.getConnectedPort())
                    .as("connectedPort must remain -1 after a failed /connect")
                    .isLessThan(0);
        } finally {
            handler.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void repeatedFailedConnectAttemptsDoNotLeakStateOrKillRepl() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        LineReader reader = newDumbLineReader(captured);

        ReplClient.ConsoleHandler handler = new ReplClient.ConsoleHandler(reader);
        try {
            // Three failures in a row must each fail cleanly without
            // accumulating instance-field damage.
            for (int i = 0; i < 3; i++) {
                handler.connect("localhost", unusedPort());
                assertThat(handler.isAvailable())
                        .as("REPL still available after attempt %d", i + 1)
                        .isTrue();
                assertThat(handler.getConnectedPort())
                        .as("connectedPort still -1 after attempt %d", i + 1)
                        .isLessThan(0);
            }

            // The error message should appear at least three times.
            String terminalOutput = captured.toString(StandardCharsets.UTF_8);
            int errCount = terminalOutput.split("Failed to connect", -1).length - 1;
            assertThat(errCount)
                    .as("Each attempt should print a connect-failure message; got: <%s>",
                            terminalOutput)
                    .isGreaterThanOrEqualTo(3);
        } finally {
            handler.close();
        }
    }
}
