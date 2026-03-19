type=page
status=published
title=Getting started | Enkan
~~~~~~

# Getting started

## Prerequisite

- Java 25 or higher
- Maven 3.6.3 or higher

## Add the dependency

Add `kotowari` (MVC framework) and a server component to your `pom.xml`:

```xml
<dependency>
  <groupId>net.unit8.enkan</groupId>
  <artifactId>kotowari</artifactId>
  <version>0.13.0</version>
</dependency>
<dependency>
  <groupId>net.unit8.enkan</groupId>
  <artifactId>enkan-component-jetty</artifactId>
  <version>0.13.0</version>
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
