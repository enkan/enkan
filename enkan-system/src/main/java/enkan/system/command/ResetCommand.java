package enkan.system.command;

import enkan.system.EnkanSystem;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import enkan.system.SystemCommand;
import enkan.system.Transport;

public class ResetCommand implements SystemCommand {
    private static final long serialVersionUID = 1L;

    @Override
    public String shortDescription() {
        return "Restart the system";
    }

    @Override
    public boolean execute(@Nullable EnkanSystem system, Transport transport, String... args) {
        Objects.requireNonNull(system);
        system.stop();
        system.start();
        transport.sendOut("Reset server");
        return true;
    }
}
