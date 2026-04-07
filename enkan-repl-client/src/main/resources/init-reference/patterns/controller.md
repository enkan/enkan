# Controller pattern

Kotowari instantiates controllers via `ComponentInjector.newInstance(...)` and
invokes methods via reflection. Parameters are injected by type.

## Parameter injection rules

A controller method can accept zero or more of:

| Parameter type | What it is | When to use |
|---|---|---|
| `enkan.web.data.HttpRequest` | The current request | When you need headers, extensions, the session, etc. **Use this to obtain `DSLContext`.** |
| `enkan.collection.Parameters` | Path + query + form params | For `:id` path variables and query string. |
| Any other POJO / record | Deserialized request body | For `POST` / `PUT` JSON bodies. |

**Return value**: a POJO or record is serialized to JSON by `SerDesMiddleware`
when the client accepts `application/json`. Return `HttpResponse` directly only
when you need to set headers, redirect, or return non-JSON.

## Canonical controller (default jOOQ + Raoh stack)

```java
package com.example.todo.controller;

import com.example.todo.Todo;
import enkan.collection.Parameters;
import enkan.web.data.HttpRequest;
import jakarta.transaction.Transactional;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Err;
import net.unit8.raoh.Result;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class TodoController {

    // GET /api/todos  — read-only, no @Transactional needed (auto-commit)
    public List<Todo> list(HttpRequest request) {
        DSLContext dsl = request.getExtension("jooqDslContext");
        List<Todo> result = new ArrayList<>();
        for (Record rec : dsl.select().from(table("todos")).fetch()) {
            Result<Todo> decoded = Todo.DECODER.decode(rec);
            if (decoded instanceof Ok<Todo>(Todo todo)) {
                result.add(todo);
            }
            // In a real app you would collect Err issues and return 500/log them.
        }
        return result;
    }

    // GET /api/todos/:id
    public Todo show(HttpRequest request, Parameters params) {
        DSLContext dsl = request.getExtension("jooqDslContext");
        Long id = params.getLong("id");
        Record rec = dsl.select().from(table("todos"))
                .where(field("id").eq(id))
                .fetchOne();
        if (rec == null) return null;     // Serializes as null → 200 with "null" body
        return Todo.DECODER.decode(rec).getOrThrow();
    }

    // POST /api/todos  — body is deserialized into the Todo parameter
    @Transactional
    public Todo create(HttpRequest request, Todo todo) {
        DSLContext dsl = request.getExtension("jooqDslContext");
        Long id = dsl.insertInto(table("todos"))
                .columns(field("title"), field("completed"))
                .values(todo.title(), todo.completed())
                .returning(field("id"))
                .fetchOne()
                .get(field("id"), Long.class);
        return new Todo(id, todo.title(), todo.completed());
    }

    // PUT /api/todos/:id
    @Transactional
    public Todo update(HttpRequest request, Parameters params, Todo todo) {
        DSLContext dsl = request.getExtension("jooqDslContext");
        Long id = params.getLong("id");
        dsl.update(table("todos"))
                .set(field("title"), todo.title())
                .set(field("completed"), todo.completed())
                .where(field("id").eq(id))
                .execute();
        return new Todo(id, todo.title(), todo.completed());
    }

    // DELETE /api/todos/:id
    @Transactional
    public void delete(HttpRequest request, Parameters params) {
        DSLContext dsl = request.getExtension("jooqDslContext");
        dsl.deleteFrom(table("todos"))
                .where(field("id").eq(params.getLong("id")))
                .execute();
    }
}
```

## How `DSLContext` reaches the controller

1. `JooqDslContextMiddleware` stores the non-transactional `DSLContext` in
   `request.getExtension("jooqDslContext")`.
2. If the controller method (or class) has `@Transactional`,
   `JooqTransactionMiddleware` wraps the handler in a jOOQ transaction and
   **replaces** the extension with a transaction-scoped `DSLContext`.
3. Inside the method, `request.getExtension("jooqDslContext")` returns the
   correct one — transactional or not — automatically.

**Do not** obtain the `DSLContext` any other way (no `@Inject JooqProvider`, no
`DSL.using(...)`) — bypassing the middleware will escape the transaction.

## `@Transactional` rules

- Use `jakarta.transaction.Transactional` from JTA, **not** Spring's.
- Only `TxType.REQUIRED` is supported (which is the default, so annotate with
  just `@Transactional`).
- Rollback is automatic on any exception thrown from the method.

## Do NOT

- Do not annotate controllers with `@RestController`, `@Path`, `@GET`, etc.
- Do not inject `DataSource`, `DSLContext`, `Flyway`, or `EntityManager`
  directly into controllers. The middleware + request extension is the only
  supported path.
- Do not use `org.springframework.*` anywhere.
- Do not use Lombok (`@Data`, `@Getter`, etc.) — the POM does not include it.
