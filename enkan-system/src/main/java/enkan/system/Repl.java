package enkan.system;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.Future;

/**
 * The interface for a REPL server.
 *
 * @author kawasima
 */
public interface Repl extends Runnable {
    /**
     * Register a command into REPL.
     *
     * @param name    The name of the given command
     * @param command A command object
     */
    void registerCommand(String name, SystemCommand command);

    /**
     * Add a background task to REPL.
     *
     * @param name The name of the given background task
     * @param task A background task
     */
    void addBackgroundTask(String name, Runnable task);

    /**
     * Get a future object for a background task.
     *
     * @param name The name of a background task
     * @return The future of background task, or {@code null} if none is registered
     */
    @Nullable Future<?> getBackground(String name);

    /**
     * Get the port number of this REPL.
     *
     * @return the port number
     */
    Integer getPort();

    /**
     * Evaluate a statement in the REPL's scripting environment and send the result to the transport.
     *
     * @param statement The statement to evaluate
     * @param transport The transport to send output to
     */
    void eval(String statement, Transport transport);

    /**
     * Register a local command that executes outside the scripting environment.
     * Unlike {@link #registerCommand}, local commands are not serialized and can
     * hold references to objects in the host JVM (e.g., the Repl itself).
     *
     * @param name    The name of the command
     * @param command A command object
     */
    void registerLocalCommand(String name, SystemCommand command);

    /**
     * Execute a REPL command string (e.g. "/start", "/help routes").
     * Output is sent to the server-side console.
     *
     * @param command the command string to execute
     */
    void execute(String command);

    /**
     * Add a transport provider that will be started when the REPL runs.
     *
     * @param provider the transport provider to add
     */
    void addTransportProvider(enkan.system.repl.TransportProvider provider);
}
