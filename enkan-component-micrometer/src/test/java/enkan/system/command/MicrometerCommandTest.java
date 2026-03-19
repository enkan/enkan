package enkan.system.command;

import enkan.component.LifecycleManager;
import enkan.component.micrometer.MicrometerComponent;
import enkan.system.EnkanSystem;
import enkan.system.ReplResponse;
import enkan.system.Transport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerCommandTest {

    static class CapturingTransport implements Transport {
        final List<String> outLines = new ArrayList<>();

        @Override
        public void send(ReplResponse response) {
            String out = response.getOut();
            if (out != null) outLines.add(out);
        }

        @Override
        public String recv(long timeout) {
            return null;
        }
    }

    private MicrometerComponent micrometerComponent;
    private MicrometerCommand command;
    private CapturingTransport transport;

    @BeforeEach
    void setUp() {
        micrometerComponent = new MicrometerComponent();
        command = new MicrometerCommand();
        transport = new CapturingTransport();
    }

    @AfterEach
    void tearDown() {
        if (micrometerComponent.getRequestTimer() != null) {
            LifecycleManager.stop(micrometerComponent);
        }
    }

    @Test
    void outputsMetricSectionsWhenStarted() {
        LifecycleManager.start(micrometerComponent);
        EnkanSystem system = EnkanSystem.of("micrometer", micrometerComponent);

        command.execute(system, transport);

        assertThat(transport.outLines).anyMatch(l -> l.contains("Active Requests"));
        assertThat(transport.outLines).anyMatch(l -> l.contains("Errors"));
        assertThat(transport.outLines).anyMatch(l -> l.contains("Request Timer"));
    }

    @Test
    void outputsCounterAndTimerValues() {
        LifecycleManager.start(micrometerComponent);
        EnkanSystem system = EnkanSystem.of("micrometer", micrometerComponent);

        command.execute(system, transport);

        assertThat(transport.outLines).anyMatch(l -> l.contains("count"));
        assertThat(transport.outLines).anyMatch(l -> l.contains("mean"));
    }

    @Test
    void reportsNotStartedWhenComponentNotStarted() {
        EnkanSystem system = EnkanSystem.of("micrometer", micrometerComponent);

        command.execute(system, transport);

        assertThat(transport.outLines).anyMatch(l -> l.contains("not started"));
    }

    @Test
    void reportsNotRegisteredWhenNoMicrometerComponent() {
        EnkanSystem system = EnkanSystem.of();

        command.execute(system, transport);

        assertThat(transport.outLines).anyMatch(l -> l.contains("not registered"));
    }

    @Test
    void returnsTrue() {
        LifecycleManager.start(micrometerComponent);
        EnkanSystem system = EnkanSystem.of("micrometer", micrometerComponent);

        boolean result = command.execute(system, transport);

        assertThat(result).isTrue();
    }
}
