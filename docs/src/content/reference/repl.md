type=page
status=published
title=REPL Commands | Enkan
~~~~~~

# REPL Commands

## Core Commands

These commands are always available in the Enkan REPL.

### /start

Start the Enkan system. All components are started in dependency order.

### /stop

Stop the Enkan system. All components are stopped in reverse dependency order.

### /reset

Stop and restart the Enkan system. This triggers class reloading
when `META-INF/reload.xml` is present in the classpath.

### /shutdown

Shut down the Enkan system and exit the REPL process.

### /help

Show the list of available commands.

### /middleware [app] list

Show the middleware stack for the specified application component.

## Client-Local Commands

These commands run inside the REPL client (`enkan-repl-client`) itself and do
not require a connection to a running Enkan server.

### /init

Scaffold a new Enkan project using an AI assistant. Interactively collects the
project description, name, group ID, and output directory; shows a plan for
review (which can be revised or cancelled); writes a compilable project
skeleton; runs `mvn compile` with an automatic fix loop; and optionally launches
the generated app and auto-connects the REPL to it.

Requires the following environment variables (or system properties):

| Env var | System property | Default |
|---------|----------------|---------|
| `ENKAN_AI_API_URL` | `enkan.ai.apiUrl` | `https://api.anthropic.com/v1` |
| `ENKAN_AI_API_KEY` | `enkan.ai.apiKey` | _(required)_ |
| `ENKAN_AI_MODEL` | `enkan.ai.model` | `claude-sonnet-4-5` |

Works with any OpenAI-compatible chat completion endpoint (OpenAI, Anthropic's
OpenAI-compatible endpoint, LM Studio, Ollama, vLLM, etc.). See
[Getting Started](../getting-started.html) for a full walkthrough.

### /help [command]

List all client-local commands, or show the detail for a single command
(e.g. `/help init`).

### /connect [host] port

Connect the client to a running Enkan REPL server on the given port (host
defaults to `localhost`).

### /exit

Exit the REPL client.

## Devel Commands

Registered by `DevelCommandRegister`. Requires the `enkan-devel` dependency.

### /autoreset

Watch compiled class files for changes and automatically reset the application
when a modification is detected. Uses `WatchService` to monitor all
directories marked with `META-INF/reload.xml`.

See [Development Tools](../guide/development.html) for details on the class reloading mechanism.

### /compile

Compile the project using the configured build tool.
By default, uses Maven (`MavenCompiler`). Can be configured to use Gradle
by passing a `GradleCompiler` to `DevelCommandRegister`.

## Kotowari Commands

Registered by `KotowariCommandRegister`. Available when using the Kotowari web framework.

### /routes [app]

Show the routing table for the specified application component.

## Metrics Commands

Registered by `MetricsCommandRegister`. Requires the `enkan-component-metrics` dependency.

### /metrics

Display collected application metrics from the Metrics component.
