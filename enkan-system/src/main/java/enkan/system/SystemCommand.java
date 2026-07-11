package enkan.system;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * A command for Enkan system.
 *
 * @author kawasima
 */
public interface SystemCommand extends Serializable {
    /**
     * Execute this command.
     *
     * @param system Enkan system, or {@code null} when the command is run
     *               host-side as a local command (see
     *               {@link Repl#registerLocalCommand}) with no attached system
     * @param transport A transport
     * @param args arguments
     * @return true if the command will terminate the REPL, otherwise false.
     */
    boolean execute(@Nullable EnkanSystem system, Transport transport, String... args);

    /**
     * A short one-line description shown in the command list.
     *
     * @return short description
     */
    default String shortDescription() {
        return "";
    }

    /**
     * A detailed description shown by {@code /help <command>}.
     * Defaults to {@link #shortDescription()}.
     *
     * @return detailed description
     */
    default String detailedDescription() {
        return shortDescription();
    }
}
