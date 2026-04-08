type=page
status=published
title=GraalVM Native Image & CRaC | Enkan
~~~~~~

# GraalVM Native Image & CRaC

Enkan supports two approaches to fast startup: GraalVM Native Image (compile to
a native binary) and CRaC (Checkpoint/Restore in Userspace). Both eliminate JVM
warm-up time; Native Image also reduces memory footprint, while CRaC can restore
a fully warmed-up JVM in milliseconds.

---

## GraalVM Native Image

The `kotowari-graalvm` module provides two GraalVM
[Feature](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html)
implementations that automate the reflection configuration required for a native build.

| Feature | Role |
|---------|------|
| `EnkanFeature` | Registers components, `MixinUtils`-generated classes, and SPI implementations |
| `KotowariFeature` | Registers controllers, parameter types, return types, and generates a reflection-free dispatcher |

### Add the dependency

```xml
<dependency>
  <groupId>net.unit8.enkan</groupId>
  <artifactId>kotowari-graalvm</artifactId>
  <version>0.15.1-SNAPSHOT</version>
</dependency>
```

### Configure `native-image.properties`

Place the following in
`src/main/resources/META-INF/native-image/<groupId>/<artifactId>/native-image.properties`:

```properties
Args = --features=enkan.graalvm.EnkanFeature,kotowari.graalvm.KotowariFeature \
       --initialize-at-build-time=enkan.util.MixinUtils,kotowari.graalvm.NativeDispatcherRegistry,kotowari.graalvm.KotowariDispatcher \
       -H:+ReportExceptionStackTraces
```

### Pre-generate mixin classes (recommended)

The `GenerateMixinConfig` tool writes `$Mixin` class files and their
`reflect-config.json` entries at Maven `prepare-package` time, so they are
included in the fat JAR before `native-image` runs:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>generate-mixin-config</id>
      <phase>prepare-package</phase>
      <goals><goal>java</goal></goals>
      <configuration>
        <mainClass>kotowari.example.graalvm.GenerateMixinConfig</mainClass>
      </configuration>
    </execution>
  </executions>
</plugin>
```

See `kotowari-graalvm-example` for a complete working example using Undertow + Jackson.

### Build the native image

```bash
mvn -Pnative package
```

The resulting binary starts in single-digit milliseconds and uses a fraction of
the heap required by the JVM version.

> **Preview API note:** If your application uses `RequestTimeoutMiddleware`
> (which relies on `StructuredTaskScope`, a preview API), add
> `--enable-preview` to both the `native-image` build arguments and the
> runtime invocation of the binary.

### What the features do automatically

**`EnkanFeature`:**
- Registers all `@Inject`-annotated fields (including superclass hierarchy) for reflection
- Generates hidden-class `ComponentBinder` instances for reflection-free field injection
- Registers all `META-INF/services` SPI implementations

**`KotowariFeature`:**
- Calls `MixinUtils.createFactory()` at build time so all `$Mixin` subclasses exist in the image
- Registers all `$Mixin` classes for reflection
- Registers controller classes, their methods, and all parameter / return types
- Generates a `KotowariDispatcher` class that replaces reflective controller dispatch with
  direct `invokevirtual` bytecode — zero reflection at runtime

---

## CRaC (Checkpoint/Restore in Userspace)

CRaC takes a snapshot of a running JVM process (checkpoint) and restores it
later — potentially on a different host — with the JVM already warmed up. Enkan
integrates via the `org.crac` portability shim, so the same code runs on both
CRaC-capable JVMs and standard JVMs.

### Register with the CRaC context

Call `registerCrac()` **after** `system.start()`:

```java
EnkanSystem system = EnkanSystem.of(
    "app",  new ApplicationComponent(AppFactory.class.getName()),
    "http", new JettyComponent()
).relationships(
    component("http").using("app")
);

system.start();
system.registerCrac(); // opt-in; no-op on non-CRaC JVMs
```

`registerCrac()` registers a `Resource` that:
- Calls `system.stop()` before the checkpoint is taken (closes sockets, flushes state)
- Calls `system.start()` after restore (re-opens sockets, reconnects to databases)

A second overload accepts an explicit `Context<Resource>` for testing:

```java
system.registerCrac(Core.getGlobalContext()); // default
system.registerCrac(myTestContext);           // for unit tests
```

### Take a checkpoint

With a CRaC-enabled JDK (e.g., Azul Zulu CRaC or OpenJDK CRaC builds):

```bash
# Start your application
java -XX:CRaCCheckpointTo=/tmp/checkpoint -jar myapp.jar

# In another terminal, trigger the checkpoint
jcmd <pid> JDK.checkpoint
```

### Restore

```bash
java -XX:CRaCRestoreFrom=/tmp/checkpoint
```

The application is running immediately — no JVM startup, no JIT warm-up.

### Behaviour on non-CRaC JVMs

The `org.crac` portability shim absorbs `registerCrac()` silently. No exception
is thrown; the method is a no-op. The same binary runs on both CRaC and standard
JVMs.

### Limitations

- File descriptors (sockets, database connections) are closed at checkpoint and
  re-opened at restore. Connection pools (HikariCP) restart cleanly via the
  component lifecycle.
- Do not checkpoint with open WebSocket connections or active SSE streams.
- CRaC snapshots are JVM-version-specific; snapshots cannot be shared across
  different JDK versions.

---

## Choosing between Native Image and CRaC

| | GraalVM Native Image | CRaC |
|---|---|---|
| Startup time | Single-digit ms (cold) | Near-zero (warm restore) |
| Memory footprint | Significantly reduced | Same as JVM |
| JIT optimization | AOT (profile-guided optional) | Full JIT after restore |
| Build complexity | High (reflection config, native-image) | Low (standard JVM build) |
| Best for | Serverless, CLI tools, short-lived containers | Long-running services, scale-to-zero |
