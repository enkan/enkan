package enkan.web.endpoint;

import enkan.component.HealthStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthEndpointTest {

    @Test
    void allUpReturnsUp() {
        Map<String, HealthStatus> statuses = new LinkedHashMap<>();
        statuses.put("a", HealthStatus.UP);
        statuses.put("b", HealthStatus.UP);

        assertThat(HealthEndpoint.computeOverallStatus(statuses)).isEqualTo(HealthStatus.UP);
    }

    @Test
    void emptyComponentsReturnsUp() {
        assertThat(HealthEndpoint.computeOverallStatus(Map.of())).isEqualTo(HealthStatus.UP);
    }

    @Test
    void anyDownReturnsDown() {
        Map<String, HealthStatus> statuses = new LinkedHashMap<>();
        statuses.put("a", HealthStatus.UP);
        statuses.put("b", HealthStatus.DOWN);

        assertThat(HealthEndpoint.computeOverallStatus(statuses)).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void startingIsWorseThanUp() {
        Map<String, HealthStatus> statuses = new LinkedHashMap<>();
        statuses.put("a", HealthStatus.UP);
        statuses.put("b", HealthStatus.STARTING);

        assertThat(HealthEndpoint.computeOverallStatus(statuses)).isEqualTo(HealthStatus.STARTING);
    }

    @Test
    void stoppingIsWorseThanStarting() {
        Map<String, HealthStatus> statuses = new LinkedHashMap<>();
        statuses.put("a", HealthStatus.STARTING);
        statuses.put("b", HealthStatus.STOPPING);

        assertThat(HealthEndpoint.computeOverallStatus(statuses)).isEqualTo(HealthStatus.STOPPING);
    }

    @Test
    void downIsWorstStatus() {
        Map<String, HealthStatus> statuses = new LinkedHashMap<>();
        statuses.put("a", HealthStatus.STARTING);
        statuses.put("b", HealthStatus.STOPPING);
        statuses.put("c", HealthStatus.DOWN);
        statuses.put("d", HealthStatus.UP);

        assertThat(HealthEndpoint.computeOverallStatus(statuses)).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void nullStatusIsTreatedAsDown() {
        Map<String, HealthStatus> statuses = new LinkedHashMap<>();
        statuses.put("a", HealthStatus.UP);
        statuses.put("b", null);

        assertThat(HealthEndpoint.computeOverallStatus(statuses)).isEqualTo(HealthStatus.DOWN);
    }
}
