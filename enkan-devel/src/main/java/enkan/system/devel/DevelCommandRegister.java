package enkan.system.devel;

import enkan.system.Repl;
import enkan.system.devel.command.AutoResetCommand;
import enkan.system.devel.command.CompileCommand;
import enkan.system.repl.command.InitCommand;
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
        repl.registerLocalCommand("init", new InitCommand());
    }

    public void setCompiler(Compiler compiler) {
        this.compiler = compiler;
    }
}
