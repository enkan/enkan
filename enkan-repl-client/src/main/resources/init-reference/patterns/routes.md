# Routes pattern — `RoutesDef.java`

The fixed `ApplicationFactory` calls `RoutesDef.routes()`. You MUST generate a
`RoutesDef.java` file in the base package (same directory as
`*ApplicationFactory.java`) with this exact shape.

## Full example

```java
package com.example.todo;  // MUST be the base package

import kotowari.routing.Routes;
import com.example.todo.controller.TodoController;

public final class RoutesDef {
    private RoutesDef() {}

    public static Routes routes() {
        return Routes.define(r -> {
            r.get("/api/todos").to(TodoController.class, "list");
            r.get("/api/todos/:id").to(TodoController.class, "show");
            r.post("/api/todos").to(TodoController.class, "create");
            r.put("/api/todos/:id").to(TodoController.class, "update");
            r.delete("/api/todos/:id").to(TodoController.class, "delete");
        }).compile();
    }
}
```

## Rules

1. **Class is `public final class RoutesDef`** with a private constructor.
2. **Method signature is `public static Routes routes()`** — the fixed
   ApplicationFactory calls exactly this name.
3. **Chain `.compile()`** at the end of `Routes.define(...)` — the compiled
   form is what `RoutingMiddleware` expects.
4. **Path parameters use `:name`** style (e.g. `/api/todos/:id`). They are
   surfaced to the controller as `Parameters.getLong("id")` etc.
5. **`.to(ControllerClass.class, "methodName")`** — string method name, not a
   method reference. The method is resolved reflectively.

## Available HTTP verbs

`r.get(path)`, `r.post(path)`, `r.put(path)`, `r.patch(path)`, `r.delete(path)`,
`r.head(path)`, `r.options(path)`.

## Imports

```java
import kotowari.routing.Routes;
// Import every controller class you reference with .class above.
```

## Do NOT

- Do not use JAX-RS annotations (`@Path`, `@GET`, etc.) on controllers.
- Do not return a raw `Routes.RoutesBuilder` — call `.compile()`.
- Do not put `RoutesDef` in a sub-package — it must live next to
  `*ApplicationFactory.java`.
