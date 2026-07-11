package enkan.system.command;

import enkan.system.EnkanSystem;
import org.jspecify.annotations.Nullable;
import enkan.system.ReplResponse;
import enkan.system.SystemCommand;
import enkan.system.Transport;

import java.util.Map;
import java.util.TreeMap;

/**
 * The HelpCommand class provides a method to execute a help command.
 * It sends a response with a list of available commands and their descriptions.
 *
 * @author kawasima
 */
public class HelpCommand implements SystemCommand {
    private static final long serialVersionUID = 1L;

    private final Map<String, SystemCommand> commands;

    public HelpCommand(Map<String, SystemCommand> commands) {
        this.commands = commands;
    }

    @Override
    public String shortDescription() {
        return "Show available commands";
    }

    @Override
    public String detailedDescription() {
        return "Show available commands with descriptions.\nUsage: /help [command]";
    }

    @Override
    public boolean execute(@Nullable EnkanSystem system, Transport transport, String... args) {
        if (args.length > 0 && !args[0].isEmpty()) {
            String name = args[0].startsWith("/") ? args[0].substring(1) : args[0];
            SystemCommand cmd = commands.get(name);
            if (cmd != null) {
                transport.send(ReplResponse.withOut("/" + name + " - " + cmd.detailedDescription()));
            } else {
                transport.send(ReplResponse.withOut("Unknown command: " + name));
            }
        } else {
            int maxLen = commands.keySet().stream()
                    .mapToInt(String::length)
                    .max().orElse(0);
            new TreeMap<>(commands).forEach((name, cmd) -> {
                String desc = cmd.shortDescription();
                String line = String.format("  /%-" + maxLen + "s  %s", name, desc.isEmpty() ? "" : "- " + desc);
                transport.send(ReplResponse.withOut(line));
            });
        }
        transport.send(new ReplResponse().done());
        return true;
    }
}
