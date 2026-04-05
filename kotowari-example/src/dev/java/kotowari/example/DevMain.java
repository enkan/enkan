package kotowari.example;

import enkan.system.command.JsonRequestCommand;
import enkan.system.command.SqlCommand;
import enkan.system.devel.command.AutoResetCommand;
import enkan.system.devel.command.CompileCommand;
import enkan.system.repl.JShellRepl;
import enkan.system.repl.ReplBoot;
import enkan.system.repl.websocket.WebSocketTransportProvider;
import kotowari.system.KotowariCommandRegister;
/**
 * A main class for development.
 *
 * @author kawasima
 */
public class DevMain {
    public static void main(String[] args) {
        JShellRepl repl = new JShellRepl(ExampleSystemFactory.class.getName());

        new ReplBoot(repl)
                .register(new KotowariCommandRegister())
                .register(r -> {
                    r.registerCommand("sql", new SqlCommand());
                    r.registerCommand("jsonRequest", new JsonRequestCommand());
                    r.registerLocalCommand("autoreset", new AutoResetCommand(repl));
                    r.registerLocalCommand("compile", new CompileCommand());
                })
                .transport(new WebSocketTransportProvider())
                .onReady("/start")
                .start();

    }
}
