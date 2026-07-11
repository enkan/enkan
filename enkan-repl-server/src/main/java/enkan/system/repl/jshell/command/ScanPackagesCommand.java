package enkan.system.repl.jshell.command;

import enkan.system.EnkanSystem;
import enkan.system.ReplResponse;
import enkan.system.SystemCommand;
import enkan.system.Transport;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class ScanPackagesCommand implements SystemCommand {
    @Override
    public boolean execute(@Nullable EnkanSystem system, Transport transport, String... args) {
        Objects.requireNonNull(system).getAllComponents().forEach(
                c -> transport.send(ReplResponse.withOut(c.getClass().getName()))
        );
        return true;
    }
}
