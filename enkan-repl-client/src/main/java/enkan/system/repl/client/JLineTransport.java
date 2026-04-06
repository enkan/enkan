package enkan.system.repl.client;

import enkan.system.ReplResponse;
import enkan.system.Transport;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.io.PrintWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A {@link Transport} implementation backed by a JLine {@link LineReader}.
 *
 * <p>Used to run client-local commands (e.g. {@code /init}) directly inside
 * the {@code enkan-repl} CLI client without a REPL server connection.
 *
 * <ul>
 *   <li>{@link #send} writes {@code out} or {@code err} to the terminal writer.</li>
 *   <li>{@link #recv} reads a line from the terminal via {@code readLine("")}.</li>
 *   <li>{@link #startSpinner} / {@link #stopSpinner} show an animated indicator.</li>
 * </ul>
 *
 * @author kawasima
 */
public class JLineTransport implements Transport {
    private static final String[] SPINNER_FRAMES = {"|", "/", "-", "\\"};

    private final LineReader reader;
    private final PrintWriter writer;
    private String pendingPrompt = "";

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "spinner");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> spinnerFuture;
    private final AtomicInteger spinnerTick = new AtomicInteger(0);
    private volatile Consumer<Integer> connectCallback;

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
    public void sendPrompt(String prompt) {
        pendingPrompt = prompt != null ? prompt : "";
    }

    @Override
    public String recv(long timeout) {
        try {
            String p = pendingPrompt;
            pendingPrompt = "";
            return reader.readLine(p);
        } catch (EndOfFileException | UserInterruptException e) {
            return null;
        }
    }

    @Override
    public void startSpinner(String label) {
        stopSpinner();
        spinnerTick.set(0);
        String prefix = label != null ? label : "Thinking";
        spinnerFuture = scheduler.scheduleAtFixedRate(() -> {
            String frame = SPINNER_FRAMES[spinnerTick.getAndIncrement() % SPINNER_FRAMES.length];
            writer.print("\r" + prefix + "... " + frame + " ");
            writer.flush();
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    public void setConnectCallback(Consumer<Integer> cb) {
        this.connectCallback = cb;
    }

    @Override
    public void requestConnect(int port) {
        if (connectCallback != null) connectCallback.accept(port);
    }

    @Override
    public void stopSpinner() {
        if (spinnerFuture != null && !spinnerFuture.isDone()) {
            spinnerFuture.cancel(false);
            spinnerFuture = null;
            // Clear the spinner line
            writer.print("\r\033[2K");
            writer.flush();
        }
    }
}
