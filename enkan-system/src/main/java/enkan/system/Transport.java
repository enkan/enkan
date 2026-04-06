package enkan.system;

import enkan.system.ReplResponse.ResponseStatus;

import java.util.Arrays;

import static enkan.system.ReplResponse.ResponseStatus.*;
/**
 * Communication channel between a {@link SystemCommand} and the REPL client.
 *
 * <p>A {@code Transport} abstracts the underlying message transport (e.g.
 * ZeroMQ) so that commands can send output and receive further input without
 * depending on any particular networking library.
 *
 * <p>The convenience methods {@link #sendOut} and {@link #sendErr} build and
 * send a {@link ReplResponse} in a single call.  Most commands only need
 * these two methods; the low-level {@link #send} and {@link #recv} are
 * intended for transports that need fine-grained control over framing or
 * status flags.
 *
 * @author kawasima
 */
public interface Transport {
    /**
     * Sends a {@link ReplResponse} frame to the REPL client.
     *
     * @param response the response frame to send
     */
    void send(ReplResponse response);

    /**
     * Blocks until a message arrives from the client or the timeout expires.
     *
     * @param timeout maximum wait time in milliseconds; {@code -1} means wait
     *                indefinitely
     * @return the received message, or {@code null} on timeout
     */
    String recv(long timeout);

    /**
     * Sends a normal output message with status {@code DONE}.
     *
     * @param message the text to send to the client
     */
    default void sendOut(String message) {
        sendOut(message, DONE);
    }

    /**
     * Sends a normal output message with the specified status flags.
     *
     * @param message  the text to send to the client
     * @param statuses additional status flags to attach to the response
     */
    default void sendOut(String message, ResponseStatus... statuses) {
        ReplResponse res = ReplResponse.withOut(message);
        res.getStatus().addAll(Arrays.asList(statuses));
        send(res);
    }

    /**
     * Sends an error message with status {@code DONE} and {@code ERROR}.
     *
     * @param errMessage the error text to send to the client
     * @param statuses   additional status flags to attach to the response
     */
    default void sendErr(String errMessage, ResponseStatus... statuses) {
        ReplResponse res = ReplResponse.withErr(errMessage);
        res.getStatus().addAll(Arrays.asList(statuses));
        res.getStatus().add(DONE);
        res.getStatus().add(ERROR);
        send(res);
    }

    /**
     * Blocks indefinitely until a message arrives from the client.
     *
     * @return the received message
     */
    default String recv() {
        return recv(-1);
    }

    /**
     * Sends a prompt message to the client and signals that user input is
     * expected.  The client should display the prompt text and allow the user
     * to type a response, which will be delivered via {@link #recv()}.
     *
     * @param prompt the prompt text to display to the user
     */
    default void sendPrompt(String prompt) {
        ReplResponse res = ReplResponse.withOut(prompt);
        res.getStatus().add(NEED_INPUT);
        send(res);
    }

    /**
     * Starts an animated "thinking" indicator. No-op by default.
     *
     * @param label text shown next to the spinner, e.g. {@code "Thinking"}
     */
    default void startSpinner(String label) {}

    /**
     * Stops the animated "thinking" indicator and clears it from the terminal.
     * No-op by default.
     */
    default void stopSpinner() {}

    /**
     * Requests the client to connect to a REPL server on the given port.
     * No-op by default.
     *
     * @param port the server port to connect to
     */
    default void requestConnect(int port) {}

}
