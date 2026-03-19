package enkan.system.command;

import enkan.system.Repl;
import enkan.system.repl.SystemCommandRegister;

/**
 * Registers the {@code /micrometer} REPL command.
 *
 * @author kawasima
 */
public class MicrometerCommandRegister implements SystemCommandRegister {
    @Override
    public void register(Repl repl) {
        repl.registerCommand("micrometer", new MicrometerCommand());
    }
}
