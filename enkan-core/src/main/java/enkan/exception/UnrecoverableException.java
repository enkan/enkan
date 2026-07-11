package enkan.exception;

import org.jspecify.annotations.Nullable;

/**
 * Base class for exceptions that represent a condition from which the
 * application cannot meaningfully recover at runtime.
 *
 * <p>Subclasses cover distinct failure categories:
 * <ul>
 *   <li>{@link MisconfigurationException} — the application is incorrectly
 *       configured; a developer must fix the setup.</li>
 *   <li>{@link FalteringEnvironmentException} — a transient infrastructure
 *       failure; the same request might succeed if retried.</li>
 *   <li>{@link UnreachableException} — a code path was reached that the
 *       framework authors believed to be impossible; indicates a framework
 *       bug.</li>
 * </ul>
 *
 * @author kawasima
 */
public abstract class UnrecoverableException extends RuntimeException {
    protected UnrecoverableException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    protected UnrecoverableException(String msg) {
        super(msg);
    }
}
