package enkan.system.repl.client;

import enkan.system.ReplResponse;
import enkan.system.Transport;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.io.PrintWriter;

/**
 * A {@link Transport} implementation backed by a JLine {@link LineReader}.
 *
 * <p>Used to run client-local commands (e.g. {@code /init}) directly inside
 * the {@code enkan-repl} CLI client without a REPL server connection.
 *
 * <ul>
 *   <li>{@link #send} writes {@code out} or {@code err} to the terminal writer.</li>
 *   <li>{@link #recv} reads a line from the terminal via {@code readLine("")}.</li>
 * </ul>
 *
 * @author kawasima
 */
public class JLineTransport implements Transport {
    private final LineReader reader;
    private final PrintWriter writer;

    public JLineTransport(LineReader reader) {
        this.reader = reader;
        this.writer = reader.getTerminal().writer();
    }

    @Override
    public void send(ReplResponse response) {
        if (response.getOut() != null && !response.getOut().isEmpty()) {
            writer.print(response.getOut());
            writer.flush();
        }
        if (response.getErr() != null && !response.getErr().isEmpty()) {
            writer.println(response.getErr());
            writer.flush();
        }
    }

    @Override
    public String recv(long timeout) {
        try {
            return reader.readLine("");
        } catch (EndOfFileException | UserInterruptException e) {
            return null;
        }
    }
}
