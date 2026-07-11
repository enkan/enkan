package enkan.system.devel;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * A result of compiling the project.
 *
 * @author kawasima
 */
public record CompileResult(@Nullable Throwable executionException) implements Serializable {
    public static CompileResult success() {
        return new CompileResult(null);
    }

    public static CompileResult failure(Throwable exception) {
        return new CompileResult(exception);
    }
}
