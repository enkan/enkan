type=page
status=published
title=Getting started | Enkan
~~~~~~

# Getting started

## Prerequisite

- Java 25 or higher
- Maven 3.6.3 or higher
- An API key for an OpenAI-compatible chat completion endpoint (only for `/init`)

## Scaffold a project with `/init` (recommended)

The fastest way to start an Enkan project is the AI-powered `/init` command in the
REPL client. It interactively collects your requirements, lets you review and
revise the generated plan, writes a compilable project skeleton, runs
`mvn compile` with an automatic fix loop, and optionally launches the app and
connects the REPL.

### 1. Download the REPL client

Grab the standalone client jar from the
[latest release](https://github.com/enkan/enkan/releases/latest):

```bash
curl -L -o enkan-repl-client.jar \
  https://github.com/enkan/enkan/releases/latest/download/enkan-repl-client.jar
```

### 2. Configure the LLM endpoint

`/init` uses an OpenAI-compatible chat completion API. It works with OpenAI,
Anthropic's [OpenAI-compatible endpoint](https://docs.anthropic.com/en/api/openai-sdk),
LM Studio, Ollama, vLLM, and any other server that speaks the same protocol.

| Env var | System property | Default |
|---------|----------------|---------|
| `ENKAN_AI_API_URL` | `enkan.ai.apiUrl` | `https://api.anthropic.com/v1` |
| `ENKAN_AI_API_KEY` | `enkan.ai.apiKey` | _(required)_ |
| `ENKAN_AI_MODEL` | `enkan.ai.model` | `claude-sonnet-4-5` |

The URL may be either a base (`/v1`) or the full path (`/v1/chat/completions`) —
both are accepted.

```bash
export ENKAN_AI_API_KEY=sk-...
```

### 3. Run `/init`

```bash
java -jar enkan-repl-client.jar
```

```
  Enkan Project Generator

── Project Setup ─────────────────────────
? What kind of application do you want to build? REST API for a bookstore
? Project name [rest-api-bookstore]:
? Group ID [com.example]:
? Output directory [./rest-api-bookstore]:

── Plan Draft ────────────────────────────
(the LLM shows a proposed plan — components, routes, file list)
Plan feedback (say 'yes' to proceed, or describe changes, 'cancel' to abort) > yes

── Generating ────────────────────────────
  ✓ src/main/java/.../BookstoreApplicationFactory.java
  ✓ src/main/java/.../BookController.java
  ...

✓ Done! Created 5 file(s)

── Compiling ─────────────────────────────
✓ Compilation succeeded.

? Start and connect to the generated app? [Y]:
⚡ Starting app (mvn compile exec:exec -Pdev)...
Connected to server (port = 3001)
enkan(3001)❯
```

At the `enkan(3001)❯` prompt you are connected to a running REPL server inside
your new project. From here you can run [`/routes`](reference/repl.html#routes-app),
[`/start`, `/stop`, `/reset`](guide/repl.html), or evaluate live Java expressions.

`/init` writes the following files verbatim (without touching the LLM), so the
scaffold is always consistent:

- `pom.xml` — including the `dev` profile that runs `DevMain`
- `src/dev/java/.../DevMain.java` — starts the REPL server
- `src/main/java/.../<Project>SystemFactory.java` — minimal `EnkanSystem.of(...)` skeleton

The LLM then fills in the `ApplicationFactory`, controllers, and domain classes
based on your approved plan.

---

## Manual setup

If you prefer to wire everything by hand, add `kotowari` (MVC framework) and a
server component to your `pom.xml`:

```xml
<dependency>
  <groupId>net.unit8.enkan</groupId>
  <artifactId>kotowari</artifactId>
  <version>0.14.2-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>net.unit8.enkan</groupId>
  <artifactId>enkan-component-jetty</artifactId>
  <version>0.14.2-SNAPSHOT</version>
</dependency>
```

If you only need the middleware core without MVC routing, use `enkan-web` instead of `kotowari`.

## Hello World

### 1. Define your application factory

An application factory builds the middleware stack and routing table:

```java
import enkan.Application;
import enkan.application.WebApplication;
import enkan.config.ApplicationFactory;
import enkan.middleware.*;
import enkan.system.EnkanSystem;
import kotowari.middleware.*;
import kotowari.routing.Routes;

public class MyAppFactory implements ApplicationFactory {
    @Override
    public Application create(EnkanSystem system) {
        WebApplication app = new WebApplication();

        Routes routes = Routes.define(r -> {
            r.get("/").to(HomeController.class, "index");
        }).compile();

        app.use(new DefaultCharsetMiddleware());
        app.use(new ContentTypeMiddleware());
        app.use(new ParamsMiddleware());
        app.use(new RoutingMiddleware(routes));
        app.use(new ControllerInvokerMiddleware(system));
        return app;
    }
}
```

### 2. Write a controller

Controllers are plain Java classes — no annotations, no base class:

```java
import enkan.data.HttpResponse;

public class HomeController {
    public HttpResponse index() {
        return HttpResponse.of("Hello, World!");
    }
}
```

### 3. Wire the system

```java
import enkan.system.EnkanSystem;
import enkan.component.ApplicationComponent;
import enkan.component.jetty.JettyComponent;

import static enkan.system.EnkanSystem.component;

public class MySystemFactory {
    public EnkanSystem create() {
        return EnkanSystem.of(
            "app",  new ApplicationComponent(MyAppFactory.class.getName()),
            "http", new JettyComponent()
        ).relationships(
            component("http").using("app")
        );
    }
}
```

## Start the REPL

```bash
% mvn -e compile exec:java
```

Start the system with the `/start` command:

```bash
enkan> /start
```

Access [http://localhost:3000/](http://localhost:3000/) in your browser.

## What's next?

- [Why Enkan?](guide/why-enkan.html) — design philosophy and comparison with other frameworks
- [EnkanSystem](guide/enkan-system.html) — component lifecycle and dependency wiring
- [Controllers](guide/controller.html) — parameter injection, form handling, templates
- [Component Catalog](reference/components.html) — all available components and their configuration
- [Middleware Reference](reference/middlewares.html) — all available middleware
