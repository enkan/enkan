package enkan.component.undertow;

import enkan.component.HealthStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UndertowHealthLifecycleTest {

    @Test
    void healthIsDownBeforeStart() {
        UndertowComponent component = new UndertowComponent();
        assertThat(component.health()).isEqualTo(HealthStatus.DOWN);
    }
}
