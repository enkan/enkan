type=page
status=published
title=REPL-Driven Development | Enkan
~~~~~~

# REPL-Driven Development

One of Enkan's most distinctive features is its built-in REPL (Read-Eval-Print Loop).
Unlike typical Java development cycles that require a full JVM restart for every change,
Enkan's REPL lets you start, stop, and reload the application while the JVM stays running —
and gives you a live interactive shell into the running system.

---

## What the REPL Is

The Enkan REPL is a JShell-based interactive environment that runs inside the same JVM as
your application. It is not just a control console — it is a full Java expression evaluator.
You can:

- Start and stop the HTTP server with a command
- Reload application code in ~1 second without restarting the JVM
- Inspect the live middleware stack and routing table
- Execute arbitrary Java expressions against live components (database, cache, etc.)
- Test domain objects interactively without writing test classes
- Scaffold an entirely new Enkan project with [`/init`](../reference/repl.html#init) (AI-assisted)

---

## The `enkan-repl-client` Standalone Client

Enkan ships a standalone client jar (`enkan-repl-client.jar`) published with every
[release](https://github.com/enkan/enkan/releases/latest). It has two primary uses:

- **Scaffolding a new project** from scratch via the client-local `/init` command —
  no project checkout, no build setup required. See
  [Getting Started](../getting-started.html) for the walkthrough.
- **Attaching to a running Enkan application** over ZeroMQ, to inspect or manipulate
  the live system (see [Connecting to a Remote Process](#connecting-to-a-remote-process) below).

```bash
java -jar enkan-repl-client.jar           # disconnected shell; /init and /connect work
java -jar enkan-repl-client.jar 64815     # connect immediately to port 64815
```

At the disconnected prompt (`enkan ✗  ❯`), the client-local commands
(`/init`, `/help`, `/connect`, `/exit`) are available. Once connected
(`enkan(64815)❯`), all commands registered on the server are also available.

---

## Starting the REPL

Bootstrap the REPL from your `main` method:

```java
import enkan.system.repl.JShellRepl;
import enkan.system.repl.ReplBoot;
import kotowari.system.KotowariCommandRegister;

public class DevMain {
    public static void main(String[] args) {
        JShellRepl repl = new JShellRepl(MySystemFactory.class.getName());

        new ReplBoot(repl)
                .register(new KotowariCommandRegister())
                .onReady("/start")
                .start();
    }
}
```

Run it:

```bash
% mvn -e compile exec:java -Dexec.mainClass=com.example.DevMain
```

The REPL starts, executes `/start` automatically, and presents a prompt:

```bash
enkan>
```

---

## The Basic Development Loop

```bash
enkan> /start                   # start the HTTP server
System started.

# ... edit Java source files in your IDE ...
# ... IDE compiles to target/classes/ ...

enkan> /reset                   # reload changed classes, restart app layer
System reset in 743ms.          # DB pool, caches — still alive

enkan> /stop                    # graceful shutdown
System stopped.
```

`/reset` discards and recreates the application `ClassLoader`, picking up any recompiled
`.class` files from `target/classes/`. Long-lived components (connection pools, caches)
are **not** restarted unless they depend on the application layer.

### Automatic reset on file change

Register the `/autoreset` command to watch for compiled class changes:

```java
new ReplBoot(repl)
        .register(new KotowariCommandRegister())
        .register(r -> r.registerLocalCommand("autoreset", new AutoResetCommand(repl)))
        .start();
```

```bash
enkan> /autoreset
Start to watch modification of an application.

# Now just save + compile in your IDE — the app resets automatically
```

---

## Inspecting the Live System

### Middleware stack

```bash
enkan> /middleware app list
ANY   defaultCharset   (enkan.middleware.DefaultCharsetMiddleware@4929dbc3)
ANY   trace            (enkan.middleware.TraceMiddleware@1c985ffd)
ANY   session          (enkan.middleware.SessionMiddleware@32424a32)
ANY   routing          (kotowari.middleware.RoutingMiddleware@226c7147)
ANY   controllerInvoker (kotowari.middleware.ControllerInvokerMiddleware@5f4da5c3)
```

### Routing table

```bash
enkan> /routes app
GET    /                       HomeController#index
GET    /customers               CustomerController#index
GET    /customers/:id           CustomerController#show
POST   /customers               CustomerController#create
```

### Metrics

```bash
enkan> /metrics
requests.count = 142
requests.mean_rate = 3.21/s
requests.p99 = 12.4ms
```

---

## Live Java Evaluation

The REPL is a full JShell session. Every component in the system is accessible via the
`system` variable, which is bound to your running `EnkanSystem` instance.

### Query live data through your domain layer

```bash
enkan> var ds = system.getComponent("datasource", HikariCPComponent.class)
enkan> var dao = new CustomerDao(ds.getDataSource())
enkan> dao.findById(42L)
$3 ==> Customer{id=42, name="Alice", email="alice@example.com"}
```

### Test business logic without HTTP

```bash
enkan> var order = new Order("customer-42", List.of(new OrderItem("SKU-001", 3)))
enkan> order.totalAmount()
$5 ==> 4800

enkan> order.applyDiscount(DiscountPolicy.LOYALTY)
enkan> order.totalAmount()
$7 ==> 4320
```

### Toggle maintenance mode live

```bash
enkan> /middleware app predicate serviceUnavailable ANY
# All requests now return 503 — no redeploy needed

enkan> /middleware app predicate serviceUnavailable NONE
# Back to normal
```

---

## Connecting to a Remote Process

The REPL server listens on a ZeroMQ port (random by default; set `repl.port` to fix it).
A separate client process can attach to a running application — useful for production
inspection without a restart.

### Server side (your application)

```java
JShellRepl repl = new JShellRepl(MySystemFactory.class.getName());
new ReplBoot(repl).start();
// Logs: "Listen 64815"
```

Or fix the port:

```bash
java -Drepl.port=64815 -jar myapp.jar
```

### Client side

```bash
# From the enkan-repl-client JAR
java -jar enkan-repl-client.jar 64815
Connected to server (port = 64815)
enkan> /metrics
```

Or from within another REPL session:

```bash
enkan> /connect 64815
Connected to server (port = 64815)
```

Remote connections can run any registered command. Arbitrary JShell expressions are
restricted to local connections for security.

---

## Available Commands

See the [REPL Commands reference](../reference/repl.html) for the full list of built-in,
Kotowari, Devel, and Metrics commands.
