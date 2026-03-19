package enkan.system.command;

import enkan.component.micrometer.MicrometerComponent;
import enkan.system.EnkanSystem;
import enkan.system.ReplResponse;
import enkan.system.SystemCommand;
import enkan.system.Transport;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * REPL command that displays Micrometer metrics.
 *
 * @author kawasima
 */
public class MicrometerCommand implements SystemCommand {

    protected Optional<MicrometerComponent> findMicrometer(EnkanSystem system) {
        return system.getAllComponents().stream()
                .filter(c -> c instanceof MicrometerComponent)
                .map(MicrometerComponent.class::cast)
                .findFirst();
    }

    private void printTimer(Transport t, Timer timer) {
        HistogramSnapshot snapshot = timer.takeSnapshot();
        t.send(ReplResponse.withOut(
                String.format(Locale.US, "             count = %d", snapshot.count())));
        t.send(ReplResponse.withOut(
                String.format(Locale.US, "             total = %2.2f sec", snapshot.total(TimeUnit.SECONDS))));
        t.send(ReplResponse.withOut(
                String.format(Locale.US, "              mean = %2.2f sec", snapshot.mean(TimeUnit.SECONDS))));
        t.send(ReplResponse.withOut(
                String.format(Locale.US, "               max = %2.2f sec", snapshot.max(TimeUnit.SECONDS))));
        for (ValueAtPercentile vp : snapshot.percentileValues()) {
            t.send(ReplResponse.withOut(
                    String.format(Locale.US, "          %5.1f%% <= %2.2f sec",
                            vp.percentile() * 100, vp.value(TimeUnit.SECONDS))));
        }
    }

    @Override
    public String shortDescription() {
        return "Show application metrics (Micrometer)";
    }

    @Override
    public String detailedDescription() {
        return "Display metrics (active requests, errors, request timer).\n"
                + "Requires MicrometerComponent to be registered and started.\n"
                + "Usage: /micrometer";
    }

    @Override
    public boolean execute(EnkanSystem system, Transport transport, String... args) {
        Optional<MicrometerComponent> found = findMicrometer(system);
        if (found.isEmpty()) {
            transport.send(ReplResponse.withOut("MicrometerComponent is not registered in the system."));
            transport.sendOut("", ReplResponse.ResponseStatus.DONE);
            return true;
        }
        MicrometerComponent micrometer = found.get();
        if (micrometer.getRequestTimer() == null) {
            transport.send(ReplResponse.withOut("MicrometerComponent is not started."));
            transport.sendOut("", ReplResponse.ResponseStatus.DONE);
            return true;
        }

        transport.send(ReplResponse.withOut("-- Active Requests ----------------------------------"));
        transport.send(ReplResponse.withOut(
                String.format(Locale.US, "             count = %d",
                        micrometer.getActiveRequests().get())));

        transport.send(ReplResponse.withOut("-- Errors -------------------------------------------"));
        transport.send(ReplResponse.withOut(
                String.format(Locale.US, "             count = %.0f",
                        micrometer.getErrorCounter().count())));

        transport.send(ReplResponse.withOut("-- Request Timer ------------------------------------"));
        printTimer(transport, micrometer.getRequestTimer());

        transport.sendOut("", ReplResponse.ResponseStatus.DONE);
        return true;
    }
}
