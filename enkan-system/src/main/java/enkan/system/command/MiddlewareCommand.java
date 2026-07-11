package enkan.system.command;

import enkan.Application;
import enkan.MiddlewareChain;
import enkan.component.ApplicationComponent;
import enkan.component.SystemComponent;
import enkan.predicate.AnyPredicate;
import enkan.predicate.NonePredicate;
import enkan.system.EnkanSystem;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import enkan.system.ReplResponse;
import enkan.system.SystemCommand;
import enkan.system.Transport;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * @author kawasima
 */
public class MiddlewareCommand implements SystemCommand {
    private static final long serialVersionUID = 1L;

    private void list(Application<?, ?> app, Transport transport) {
        List<MiddlewareChain<?, ?, ?, ?>> chains = app.getMiddlewareStack();
        chains.forEach(chain -> transport.send(ReplResponse.withOut(chain.toString())));
    }

    @Override
    public String shortDescription() {
        return "Manage middleware";
    }

    @Override
    public String detailedDescription() {
        return "List or configure middleware in an application.\nUsage:\n  /middleware <app> list\n  /middleware <app> predicate <middleware> ANY|NONE";
    }

    @Override
    public boolean execute(@Nullable EnkanSystem system, Transport transport, String... args) {
        Objects.requireNonNull(system);
        if (args == null || args.length < 2) {
            transport.sendOut("middleware [appName] [list/predicate]");
            return true;
        }

        String appName = args[0];
        SystemComponent<?> component = system.getComponent(appName);
        if (!(component instanceof ApplicationComponent)) {
            transport.sendErr(String.format("Application %s is not found.", appName));
            return true;
        }
        Application<?, ?> app = ((ApplicationComponent<?, ?>) component).getApplication();
        if (app == null) {
            transport.sendErr(String.format("Application %s is not started.", appName));
            return true;
        }


        switch (args[1]) {
            case "list" -> {
                list(app, transport);
                transport.sendOut("", ReplResponse.ResponseStatus.DONE);
            }
            case "predicate" -> {
                if (args.length < 3) {
                    transport.sendOut("Usage: /middleware [app name] predicate [middleware name] [predicate name]");
                    break;
                }
                String middlewareName = args[2];
                Optional<MiddlewareChain<?, ?, ?, ?>> middlewareChain = app.getMiddlewareStack().stream()
                        .filter(chain -> chain.getName().equals(middlewareName))
                        .findFirst();

                if (middlewareChain.isPresent()) {
                    if (args.length > 3) {
                        String predicateName = args[3];
                        switch (predicateName) {
                            case "ANY" -> middlewareChain.get().setPredicate(new AnyPredicate<>());
                            case "NONE" -> middlewareChain.get().setPredicate(new NonePredicate<>());
                            default -> {
                                transport.sendErr(String.format(Locale.US, "Unknown predicate: %s. Use ANY or NONE.", predicateName));
                                break;
                            }
                        }
                        transport.sendOut(String.format(Locale.US, "Middleware %s's predicate has changed to %s.", middlewareName, predicateName));
                    } else {
                        transport.sendOut("Usage: /middleware [app name] predicate [middleware name] [predicate name]");
                    }
                } else {
                    transport.sendErr(String.format("Middleware %s not found.", middlewareName));
                }
            }
            default -> transport.sendErr(String.format("Unknown subcommand: %s. Use list or predicate.", args[1]));
        }

        return true;
    }
}
