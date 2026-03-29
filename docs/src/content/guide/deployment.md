type=page
status=published
title=Deployment | Enkan
~~~~~~

# Deployment

---

## Packaging as an Executable JAR

The recommended way to ship an Enkan application is as a self-contained fat JAR using the Maven Shade plugin.

Add the following to your `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <transformers>
                    <transformer implementation=
                        "org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>com.example.MyMain</mainClass>
                    </transformer>
                    <transformer implementation=
                        "org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Build and run:

```bash
mvn package
java -jar target/myapp.jar
```

---

## Entry Point

The main class starts the REPL (or the system directly) and blocks:

```java
public class MyMain {
    public static void main(String[] args) {
        PseudoRepl repl = new PseudoRepl(MySystemFactory.class.getName());
        ReplBoot.start(repl,
            new KotowariCommandRegister(),
            new DevelCommandRegister());
    }
}
```

For production, `PseudoRepl` listens on the console.
To skip the REPL and start the system directly:

```java
public class MyMain {
    public static void main(String[] args) {
        EnkanSystem system = new MySystemFactory().create();
        Runtime.getRuntime().addShutdownHook(new Thread(system::stop));
        system.start();
    }
}
```

---

## Configuration via Environment Variables

Enkan follows [12-factor app](https://12factor.net/config) conventions.
Configuration is read from three sources in priority order:

1. Java system properties (`-Dkey=value`)
2. Environment variables (`KEY=value`)
3. `env.properties` on the classpath (development defaults)

Use `Env` to read values in your system factory:

```java
import enkan.Env;

builder(new JettyComponent())
    .set(JettyComponent::setPort, Env.getInt("PORT", 3000))
    .set(JettyComponent::setHost, Env.get("HOST", "0.0.0.0"))
    .build()
```

`Env` normalises key names: `DATABASE_URL` and `database.url` are treated as the same key.

### `env.properties` example

```properties
PORT=3000
HOST=0.0.0.0
DATABASE_URL=jdbc:h2:mem:dev
```

In production, set environment variables instead of bundling an `env.properties` file.

---

## HTTP Server Configuration

Both `JettyComponent` and `UndertowComponent` inherit from `WebServerComponent` and share the same configuration API.

| Property | Setter | Default | Description |
|----------|--------|---------|-------------|
| Port | `setPort(int)` | `80` | HTTP listen port |
| Host | `setHost(String)` | `0.0.0.0` | Bind address |
| SSL enabled | `setSsl(boolean)` | `false` | Enable HTTPS |
| SSL port | `setSslPort(int)` | `443` | HTTPS listen port |
| Keystore path | `setKeystorePath(String)` | — | Path to JKS keystore |
| Keystore password | `setKeystorePassword(String)` | — | Keystore password |
| Pre-stop delay | `setPreStopDelay(long)` | `0` | Milliseconds to keep serving after shutdown begins |
| Stop timeout | `setStopTimeout(long)` | `30000` | Milliseconds to wait for in-flight requests |

### HTTPS example

```java
builder(new JettyComponent())
    .set(JettyComponent::setPort,            Env.getInt("PORT", 8080))
    .set(JettyComponent::setSsl,             true)
    .set(JettyComponent::setSslPort,         Env.getInt("SSL_PORT", 8443))
    .set(JettyComponent::setKeystorePath,    Env.get("KEYSTORE_PATH"))
    .set(JettyComponent::setKeystorePassword, Env.get("KEYSTORE_PASSWORD"))
    .build()
```

---

## Graceful Shutdown

When stopping, Enkan web server components follow a three-phase shutdown sequence:

1. **Pre-stop delay** — The component reports `STOPPING` health status but continues to serve requests. This gives load balancers and Kubernetes Endpoints time to stop routing new traffic to the pod.
2. **Drain** — The server stops accepting new connections and waits for in-flight requests to complete.
3. **Force stop** — After the timeout, the server is forcefully stopped.

### Configuration

```java
builder(new JettyComponent())
    .set(JettyComponent::setPort, Env.getInt("PORT", 3000))
    .set(JettyComponent::setPreStopDelay, 5000)  // 5s for Endpoints propagation
    .set(JettyComponent::setStopTimeout, 25000)   // 25s drain
    .build()
```

### Kubernetes deployment

Set `preStopDelay + stopTimeout` to be less than the pod's `terminationGracePeriodSeconds` (default 30s) to ensure the JVM exits cleanly before Kubernetes sends SIGKILL:

```yaml
spec:
  terminationGracePeriodSeconds: 35
```

### Health status lifecycle

The `HealthCheckable` interface reports four states that map to Kubernetes probes:

| HealthStatus | Readiness | Liveness | Meaning |
|---|---|---|---|
| `STARTING` | not ready | alive | Initializing — don't send traffic, don't kill |
| `UP` | ready | alive | Normal operation |
| `STOPPING` | not ready | alive | Draining — stop sending traffic, don't kill |
| `DOWN` | not ready | dead | Failed — restart |

---

## Database Migrations

[FlywayMigration](../reference/components.md) runs migrations automatically when the component starts.
Add it to the system and declare it as a dependency of any component that needs a migrated schema:

```java
EnkanSystem.of(
    "datasource", new HikariCPComponent(OptionMap.of("uri", Env.get("DATABASE_URL"))),
    "flyway",     new FlywayMigration(),
    "doma",       new DomaProvider(),
    ...
).relationships(
    component("flyway").using("datasource"),
    component("doma").using("datasource", "flyway"),   // waits for migrations
    ...
);
```

Migration SQL files go in `src/main/resources/db/migration/` following Flyway's naming convention (`V1__description.sql`).

---

## Running in a Servlet Container

Enkan supports deployment as a WAR via `enkan-servlet`.

1. Change packaging to `war` in `pom.xml`.
2. Extend `jakarta.servlet.http.HttpServlet` and delegate to `ServletUtils`:

```java
@WebServlet(urlPatterns = "/*")
public class MyServlet extends HttpServlet {
    private EnkanSystem system;

    @Override
    public void init() {
        system = new MySystemFactory().create();
        system.start();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        ApplicationComponent<HttpRequest, HttpResponse> app =
            system.getComponent("app", ApplicationComponent.class);
        HttpRequest request = ServletUtils.buildRequest(req);
        HttpResponse response = app.getApplication().handle(request);
        ServletUtils.updateServletResponse(res, response);
    }

    @Override
    public void destroy() {
        system.stop();
    }
}
```

---

## Containerisation (Docker)

A minimal `Dockerfile` for a fat JAR application:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/myapp.jar app.jar
EXPOSE 3000
ENV PORT=3000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:

```bash
docker build -t myapp .
docker run -p 3000:3000 -e DATABASE_URL=jdbc:postgresql://db/mydb myapp
```
