package enkan.system.command;

import enkan.system.EnkanSystem;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import enkan.system.SystemCommand;
import enkan.system.Transport;

import static enkan.system.ReplResponse.ResponseStatus.SHUTDOWN;

public class ShutdownCommand implements SystemCommand {
    private static final long serialVersionUID = 1L;

    @Override
    public String shortDescription() {
        return "Shutdown the REPL server";
    }

    @Override
    public boolean execute(@Nullable EnkanSystem system, Transport transport, String... args) {
        Objects.requireNonNull(system);
        system.stop();
        transport.sendOut("Shutdown server", SHUTDOWN);
        return false;
    }
}
