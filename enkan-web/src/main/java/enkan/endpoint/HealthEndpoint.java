package enkan.endpoint;

import enkan.Endpoint;
import enkan.component.HealthCheckable;
import enkan.component.HealthStatus;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.system.EnkanSystem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An HTTP endpoint that reports the health of all system components.
 *
 * <p>Responds with a JSON body of the form:
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "datasource": "UP",
 *     "cache": "DOWN"
 *   }
 * }
 * }</pre>
 *
 * <p>The overall {@code status} reflects the worst component status using
 * this precedence: {@code DOWN} &gt; {@code STOPPING} &gt; {@code STARTING} &gt; {@code UP}.
 *
 * <p>HTTP status codes:
 * <ul>
 *   <li>200 — all components are {@code UP}</li>
 *   <li>503 — at least one component is {@code DOWN}, {@code STARTING}, or {@code STOPPING}</li>
 * </ul>
 *
 * <p>Components that do not implement {@link HealthCheckable} are not included
 * in the {@code components} map but do not affect the overall status.
 *
 * <p>Usage in an {@code ApplicationFactory}:
 * <pre>{@code
 * app.use(path("^/health$"), new HealthEndpoint(system));
 * }</pre>
 *
 * @author kawasima
 */
public class HealthEndpoint implements Endpoint<HttpRequest, HttpResponse> {
    private final EnkanSystem system;

    public HealthEndpoint(EnkanSystem system) {
        this.system = system;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Map<String, HealthStatus> componentStatuses = new LinkedHashMap<>();

        system.getComponentMap().forEach((name, component) -> {
            if (component instanceof HealthCheckable checkable) {
                HealthStatus status;
                try {
                    status = checkable.health();
                } catch (Exception e) {
                    status = HealthStatus.DOWN;
                }
                componentStatuses.put(name, status);
            }
        });

        HealthStatus overall = computeOverallStatus(componentStatuses);

        String json = buildJson(overall, componentStatuses);
        HttpResponse response = HttpResponse.of(json);
        response.setStatus(overall == HealthStatus.UP ? 200 : 503);
        response.getHeaders().put("Content-Type", "application/json");
        return response;
    }

    private static final Map<HealthStatus, Integer> SEVERITY = Map.of(
            HealthStatus.UP, 0,
            HealthStatus.STARTING, 1,
            HealthStatus.STOPPING, 2,
            HealthStatus.DOWN, 3
    );

    /**
     * Computes the overall health status from individual component statuses.
     * Uses worst-status-wins: DOWN &gt; STOPPING &gt; STARTING &gt; UP.
     */
    static HealthStatus computeOverallStatus(Map<String, HealthStatus> componentStatuses) {
        HealthStatus worst = HealthStatus.UP;
        for (HealthStatus s : componentStatuses.values()) {
            if (SEVERITY.get(s) > SEVERITY.get(worst)) {
                worst = s;
            }
        }
        return worst;
    }

    private String buildJson(HealthStatus overall, Map<String, HealthStatus> componentStatuses) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"").append(overall.name()).append("\"");
        if (!componentStatuses.isEmpty()) {
            sb.append(",\"components\":{");
            boolean first = true;
            for (Map.Entry<String, HealthStatus> entry : componentStatuses.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":\"")
                  .append(entry.getValue().name()).append("\"");
                first = false;
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }
}
