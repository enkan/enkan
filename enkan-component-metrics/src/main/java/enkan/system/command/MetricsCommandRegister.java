package enkan.system.command;

import enkan.system.Repl;
import enkan.system.repl.SystemCommandRegister;

/**
 * @deprecated Use {@code enkan.system.command.MicrometerCommandRegister} instead.
 *             This class will be removed in a future major release.
 * @author kawasima
 */
@Deprecated(forRemoval = true)
public class MetricsCommandRegister implements SystemCommandRegister {
    @Override
    public void register(Repl repl) {
        repl.registerCommand("metrics", new MetricsCommand());
    }
}
