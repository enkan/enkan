# enkan

[![Test](https://github.com/enkan/enkan/actions/workflows/test.yml/badge.svg)](https://github.com/enkan/enkan/actions/workflows/test.yml)
[![License: EPL-2.0](https://img.shields.io/badge/License-EPL--2.0-blue.svg)](https://www.eclipse.org/legal/epl-2.0/)

Enkan(円環) is a microframework implementing a middleware pattern like ring or connect.

## Concept

- Minimal (Simple made easy)
- Ease of development
- Ease of operation

### Minimal

- Middleware pattern
- No configuration files
- Avoid blackbox
- Fewer annotations
- Less library dependencies
- Single instance (Middlewares and components, controller)

### Ease of development

- Faster startup (Preventing to scan classes)
- Reloading classes without restarting JVM
- Trace execution of middlewares
- Alert misconfiguration

### Ease of operation

- Starting server is fast. (~3 seconds)
- Resetting application is very fast. (~1 second)
- Run-time change predicates of middleware on the REPL

## Requirements

- Java 25 or higher
- Jakarta EE 10 Specification

## Middleware

`Middleware` is an implementation of filters and chains.

- Service Unavailable
- Session
- Flash
- Cookie
- Parsing parameters
- Trace log
- Populating form (kotowari)
- JSR-303 Validation (kotowari)
- Routing like Rails (kotowari)
- Injecting components to a controller (kotowari)

## Components

In Enkan, `component` is an object manages lifecycle of stateful objects.

- HikariCP
- Flyway
- Freemarker
- Thymeleaf
- Jetty
- Undertow
- Doma2
- JPA(EclipseLink)
- jOOQ
- Jackson
- S2Util-beans
- Metrics

Using enkan and kotowari, the following will be your code

```java
public class ExampleController {
    @Inject
    private TemplateEngineComponent templateEngine;

    @Inject
    private DomaDaoProvider daoProvider;

    public HttpResponse index(ExampleForm form) {
        CustomerDao customerDao = daoProvider.get(CustomerDao.class);
        Customer customer = customerDao.selectById(form.getId());
        return templateEngine.render("example",
                "customer", customer);
    }
}
```

## Get started

Add the following dependency to your pom.xml:

```xml
<dependency>
  <groupId>net.unit8.enkan</groupId>
  <artifactId>enkan-web</artifactId>
  <version>0.13.1-SNAPSHOT</version>
</dependency>
```

Or generate a blank project from the Maven archetype:

```sh
mvn archetype:generate \
  -DarchetypeGroupId=net.unit8.enkan \
  -DarchetypeArtifactId=kotowari-archetype \
  -DarchetypeVersion=0.13.0
```

## Manual

### EnkanSystem

The Enkan system consist of components. A component is a singleton instance that shares data between requests.

```java
EnkanSystem.of(
    "doma", new DomaProvider(),
    "flyway", new FlywayMigration(),
    "template", new FreemarkerComponent(),
    "datasource", new HikariCPComponent(OptionMap.of("uri", "jdbc:h2:mem:test")),
    "app", new ApplicationComponent("kotowari.example.MyApplicationFactory"),
    "http", builder(new JettyComponent())
        .set(JettyComponent::setPort, Env.getInt("PORT", 3000))
        .build()
).relationships(
    component("http").using("app"),
    component("app").using("template", "doma", "datasource"),
    component("doma").using("datasource"),
    component("flyway").using("datasource")
);
```

### Application

An application has a stack of middlewares.
A middleware is a single instance. By `use` method, the middleware is used by application.

```java
app.use(ANY("/secret"), new AuthenticateMiddleware());
```

### REPL

Enkan system is operated by a REPL interface.

- Start a server
```
enkan> /start
```
- Stop a server
```
enkan> /stop
```
- Reload an application
```
enkan> /reset
```
- Show routing information
```
enkan> /routes app
GET    /                                        {controller=class kotowari.example.controller.ExampleController, action=index}
POST   /login                                   {controller=class kotowari.example.controller.LoginController, action=login}
```
- Show middleware stack
```
enkan> /middleware app list
ANY   defaultCharset (enkan.middleware.DefaultCharsetMiddleware@4929dbc3)
NONE  serviceUnavailable (enkan.middleware.ServiceUnavailableMiddleware@2ee4fa3b)
ANY   stacktrace (enkan.middleware.StacktraceMiddleware@545872dd)
ANY   trace (enkan.middleware.TraceMiddleware@1c985ffd)
ANY   contentType (enkan.middleware.ContentTypeMiddleware@1b68686e)
ANY   params (enkan.middleware.ParamsMiddleware@58d3a07)
ANY   session (enkan.middleware.SessionMiddleware@32424a32)
ANY   routing (kotowari.middleware.RoutingMiddleware@226c7147)
ANY   controllerInvoker (kotowari.middleware.ControllerInvokerMiddleware@2b13e2e7)
```
- Rewrite a predicate of middleware
```
enkan> /middleware app predicate serviceUnavailable ANY
```

Enkan REPL can also attach to a running process via JShell:

```
enkan> /connect 64815
Connected to server (port = 64815)
enkan> system.getComponent("doma")
#DomaProvider {
  "dependencies": ["flyway", "datasource"]
}
```

### Kotowari

Kotowari is a web routing framework on Enkan.

It provides a rails-like syntax for routing definition.

```java
Routes routes = Routes.define(r -> {
    r.get("/").to(ExampleController.class, "index");
    r.get("/m1").to(ExampleController.class, "method1");
    r.post("/login").to(LoginController.class, "login");
    r.resource(CustomerController.class);
}).compile();
```

## License

Copyright © 2016-2026 kawasima

Distributed under the [Eclipse Public License, Version 2.0](https://www.eclipse.org/legal/epl-2.0/).
