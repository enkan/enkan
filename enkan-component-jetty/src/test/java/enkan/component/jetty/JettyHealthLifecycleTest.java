package enkan.component.jetty;

import enkan.component.HealthStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JettyHealthLifecycleTest {

    @Test
    void healthIsDownBeforeStart() {
        JettyComponent component = new JettyComponent();
        assertThat(component.health()).isEqualTo(HealthStatus.DOWN);
    }
}
