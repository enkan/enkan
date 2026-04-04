package enkan.system.command;

import enkan.component.WebServerComponent;
import enkan.system.EnkanSystem;
import enkan.system.SystemCommand;
import enkan.system.Transport;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class StartCommand implements SystemCommand {
    private static final long serialVersionUID = 1L;

    @Override
    public String shortDescription() {
        return "Start the system";
    }

    @Override
    public String detailedDescription() {
        return "Start the system. Optionally pass a path to open in browser.\nUsage: /start [path]";
    }

    @Override
    public boolean execute(EnkanSystem system, Transport transport, String... args) {
        system.start();
        transport.sendOut("Started server");
        if (args.length > 0) {
            if (Desktop.isDesktopSupported()) {
                system.getAllComponents().stream()
                        .filter(WebServerComponent.class::isInstance)
                        .map(WebServerComponent.class::cast)
                        .findFirst()
                        .ifPresent(web -> {
                            try {
                                Desktop.getDesktop().browse(URI.create("http://localhost:" + web.getPort() + "/" + args[0].replaceAll("^/", "")));
                            } catch (IOException _) {
                                // ignore
                            }
                        });
            }
        }
        return true;
    }
}
