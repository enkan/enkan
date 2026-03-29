# enkan

[![Test](https://github.com/enkan/enkan/actions/workflows/test.yml/badge.svg)](https://github.com/enkan/enkan/actions/workflows/test.yml)
[![License: EPL-2.0](https://img.shields.io/badge/License-EPL--2.0-blue.svg)](https://www.eclipse.org/legal/epl-2.0/)

**Enkan** (円環) is a middleware-chain web framework for Java 25, inspired by [Ring](https://github.com/ring-clojure/ring) (Clojure) and [Connect](https://github.com/senchalabs/connect) (Node.js).

A web application should be **explicit, traceable, and operable — not magical.**

## Why Enkan?

| | Enkan | Spring Boot |
|---|---|---|
| Configuration | Java code only — no YAML, no classpath scanning | YAML + annotations + auto-configuration |
| Middleware ordering | Explicit `app.use(...)` | Implicit (annotation-driven) |
| Request capabilities | Added by middleware — `getSession()` doesn't exist until `SessionMiddleware` runs | Always present |
| Component lifecycle | Explicit dependency graph | `@Bean` + `@Autowired` |
| Live inspection | Built-in JShell REPL — inspect objects, toggle features, hot-reload | Actuator (HTTP endpoints only) |
| Startup | ~3 seconds (no scanning) | 10+ seconds |

Read more: [Why Enkan?](https://enkan.github.io/guide/why-enkan.html)

## Features

- **Type-safe middleware composition** — four type parameters catch wrong ordering at compile time
- **Capability-based request enrichment** — `MixinUtils.mixin()` adds interfaces to request objects dynamically
- **REPL-driven development** — hot-reload in ~1 second, inspect routing, toggle middleware predicates live
- **Component system** — explicit lifecycle graph for stateful services (DB pools, HTTP servers, template engines)
- **GraalVM Native Image support** — build ahead-of-time compiled binaries via `kotowari-graalvm`
- **Virtual Thread support** — Jetty component runs on virtual threads by default
- **Observability** — Micrometer metrics and OpenTelemetry tracing built in

## Get Started

```xml
<dependency>
  <groupId>net.unit8.enkan</groupId>
  <artifactId>kotowari</artifactId>
  <version>0.14.1</version>
</dependency>
<dependency>
  <groupId>net.unit8.enkan</groupId>
  <artifactId>enkan-component-jetty</artifactId>
  <version>0.14.1</version>
</dependency>
```

### Define your application

```java
public class MyAppFactory implements ApplicationFactory {
    @Override
    public Application create(EnkanSystem system) {
        WebApplication app = new WebApplication();

        Routes routes = Routes.define(r -> {
            r.get("/").to(HomeController.class, "index");
            r.resource(CustomerController.class);
        }).compile();

        app.use(new DefaultCharsetMiddleware());
        app.use(new ContentTypeMiddleware());
        app.use(new ParamsMiddleware());
        app.use(new SessionMiddleware());
        app.use(new RoutingMiddleware(routes));
        app.use(new ControllerInvokerMiddleware(system));
        return app;
    }
}
```

### Write a plain Java controller

```java
public class HomeController {
    @Inject
    private TemplateEngineComponent templateEngine;

    public HttpResponse index() {
        return templateEngine.render("home");
    }
}
```

### Wire the system

```java
EnkanSystem.of(
    "template",   new FreemarkerComponent(),
    "datasource", new HikariCPComponent(OptionMap.of("uri", "jdbc:h2:mem:test")),
    "app",        new ApplicationComponent("com.example.MyAppFactory"),
    "http",       builder(new JettyComponent())
                      .set(JettyComponent::setPort, Env.getInt("PORT", 3000))
                      .build()
).relationships(
    component("http").using("app"),
    component("app").using("template", "datasource")
);
```

### Start via REPL

```
enkan> /start
enkan> /routes app
GET    /                {controller=HomeController, action=index}
GET    /customers       {controller=CustomerController, action=index}
POST   /customers       {controller=CustomerController, action=create}
...

enkan> /reset           # hot-reload in ~1 second
```

## Components

| Component | Module |
|---|---|
| Jetty (virtual threads) | `enkan-component-jetty` |
| Undertow | `enkan-component-undertow` |
| HikariCP | `enkan-component-HikariCP` |
| Flyway | `enkan-component-flyway` |
| Freemarker | `enkan-component-freemarker` |
| Thymeleaf | `enkan-component-thymeleaf` |
| Jackson | `enkan-component-jackson` |
| Doma2 | `enkan-component-doma2` |
| JPA / EclipseLink | `enkan-component-jpa` / `enkan-component-eclipselink` |
| jOOQ | `enkan-component-jooq` |
| Micrometer | `enkan-component-micrometer` |
| OpenTelemetry | `enkan-component-opentelemetry` |
| Metrics (deprecated) | `enkan-component-metrics` |

## Requirements

- Java 25 or higher
- Maven 3.6.3+

## Documentation

- [Getting Started](https://enkan.github.io/getting-started.html)
- [Why Enkan?](https://enkan.github.io/guide/why-enkan.html)
- [Component Catalog](https://enkan.github.io/reference/components.html)
- [Middleware Reference](https://enkan.github.io/reference/middlewares.html)
- [Kotowari (MVC framework)](https://enkan.github.io/kotowari.html)

## License

Copyright &copy; 2016-2026 kawasima

Distributed under the [Eclipse Public License, Version 2.0](https://www.eclipse.org/legal/epl-2.0/).
