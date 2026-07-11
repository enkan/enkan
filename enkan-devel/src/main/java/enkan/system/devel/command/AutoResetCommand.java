package enkan.system.devel.command;

import enkan.exception.FalteringEnvironmentException;
import enkan.system.EnkanSystem;
import enkan.system.Repl;
import enkan.system.SystemCommand;
import enkan.system.Transport;
import enkan.system.devel.ClassWatcher;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Future;

public class AutoResetCommand implements SystemCommand {
    private final transient Repl repl;

    public AutoResetCommand(Repl repl) {
        this.repl = repl;
    }

    @Override
    public String shortDescription() {
        return "Watch class changes and auto-reset";
    }

    @Override
    public String detailedDescription() {
        return "Watch for class file changes and automatically restart the system.\nUsage:\n  /autoreset       - Start watching\n  /autoreset stop  - Stop watching";
    }

    @Override
    public boolean execute(@Nullable EnkanSystem system, Transport transport, String... args) {
        if (args.length > 0 && "stop".equalsIgnoreCase(args[0])) {
            Future<?> watcher = repl.getBackground("classWatcher");
            if (watcher == null) {
                transport.sendOut("Autoreset is not running.");
            } else {
                watcher.cancel(true);
                transport.sendOut("Stopped autoreset.");
            }
            return true;
        }

        if (repl.getBackground("classWatcher") != null) {
            transport.sendOut("Autoreset is already running.");
            return true;
        }

        try {
            final ClassWatcher classWatcher = new ClassWatcher(
                    watchPaths(),
                    () -> repl.eval("system.stop(); system.start()", transport));
            repl.addBackgroundTask("classWatcher", classWatcher);
            transport.sendOut("Start to watch modification of an application.");
            return true;
        } catch (IOException ex) {
            throw new FalteringEnvironmentException(ex);
        }
    }

    private Set<Path> watchPaths() {
        return Set.of(new File("target/classes").toPath());
    }
}
