package enkan.system.devel;

import enkan.system.Repl;
import enkan.system.SystemCommand;
import enkan.system.devel.command.AutoResetCommand;
import enkan.system.devel.command.CompileCommand;
import enkan.system.devel.compiler.MavenCompiler;
import enkan.system.repl.SystemCommandRegister;

/**
 * @author kawasima
 */
public class DevelCommandRegister implements SystemCommandRegister {
    private Compiler compiler;

    public DevelCommandRegister() {
        this(new MavenCompiler());
    }
    /**
     * init with specified compiler.
     *
     * @param compiler Compiler
     */
    public DevelCommandRegister(final Compiler compiler) {
        this.compiler = compiler;
    }

    @Override
    public void register(final Repl repl) {
        repl.registerLocalCommand("autoreset", new AutoResetCommand(repl));
        repl.registerLocalCommand("compile", new CompileCommand(compiler));
        registerOptionalInitCommand(repl);
    }

    private void registerOptionalInitCommand(Repl repl) {
        try {
            Class<?> clazz = Class.forName("enkan.system.repl.command.InitCommand");
            if (SystemCommand.class.isAssignableFrom(clazz)) {
                SystemCommand cmd = (SystemCommand) clazz.getDeclaredConstructor().newInstance();
                repl.registerLocalCommand("init", cmd);
            }
        } catch (ClassNotFoundException ignored) {
            // InitCommand is client-specific and may be absent in devel runtime.
        } catch (ReflectiveOperationException ignored) {
            // Ignore if constructor invocation fails; devel commands still work.
        }
    }

    public void setCompiler(Compiler compiler) {
        this.compiler = compiler;
    }
}
