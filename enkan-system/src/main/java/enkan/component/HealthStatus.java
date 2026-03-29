package enkan.component;

/**
 * Represents the health status of a system component.
 *
 * <p>The four states model the full component lifecycle and map naturally
 * to Kubernetes probe semantics:
 *
 * <table>
 *   <tr><th>Status</th><th>Readiness</th><th>Liveness</th><th>Meaning</th></tr>
 *   <tr><td>STARTING</td><td>not ready</td><td>alive</td><td>Initializing — don't send traffic, don't kill</td></tr>
 *   <tr><td>UP</td><td>ready</td><td>alive</td><td>Normal operation</td></tr>
 *   <tr><td>STOPPING</td><td>not ready</td><td>alive</td><td>Draining — stop sending traffic, don't kill</td></tr>
 *   <tr><td>DOWN</td><td>not ready</td><td>dead</td><td>Failed — restart</td></tr>
 * </table>
 *
 * @author kawasima
 */
public enum HealthStatus {
    STARTING,
    UP,
    STOPPING,
    DOWN
}
