# Default component stack overview

This is how the fixed `SystemFactory` and `ApplicationFactory` fit together.
Read this before writing anything under `com.example.<project>`.

## Component graph (wired by `SystemFactory`)

```
                          ┌─────────────┐
                          │ JettyComponent │  "http"
                          └──────┬─────────┘
                                 │ depends on
                                 ▼
                          ┌─────────────────────┐
                          │ ApplicationComponent │  "app"
                          │ (→ ApplicationFactory)│
                          └──┬─────┬─────┬──────┘
                             │     │     │
           ┌─────────────────┘     │     └─────────────────┐
           ▼                       ▼                       ▼
  ┌──────────────┐       ┌─────────────────┐     ┌──────────────────────┐
  │ JacksonBeans │       │  JooqProvider   │     │  HikariCPComponent   │
  │  Converter   │       │   "jooq"        │     │   "datasource"       │
  └──────────────┘       └───┬─────────────┘     └──────▲───────────────┘
                             │                           │
                             │                           │
                             ▼                           │
                          ┌────────────────┐             │
                          │ FlywayMigration ├─────────────┘
                          │    "flyway"    │
                          └────────────────┘
```

Startup order (topological): `datasource` → `flyway` → `jooq` → `app` →
`http`. Flyway runs migrations during its own startup, **before** jOOQ hands
out any `DSLContext`, so controllers never see a half-migrated schema.

## Middleware stack (wired by `ApplicationFactory`)

Order matters. The fixed template installs exactly:

```
DefaultCharsetMiddleware
TraceMiddleware
ContentTypeMiddleware
ParamsMiddleware
NestedParamsMiddleware
CookiesMiddleware
ContentNegotiationMiddleware
RoutingMiddleware(routes)          ← resolves controller class + method
JooqDslContextMiddleware           ← sets request.getExtension("jooqDslContext")
JooqTransactionMiddleware          ← wraps @Transactional methods
FormMiddleware
SerDesMiddleware(JSON reader+writer, ToStringBodyWriter)
ValidateBodyMiddleware
ControllerInvokerMiddleware(injector)   ← actually calls the controller
```

## What you generate, what is fixed

| Fixed (template) | Generated (you) |
|---|---|
| `pom.xml` | `RoutesDef.java` |
| `DevMain.java` | `controller/*Controller.java` |
| `*SystemFactory.java` | Domain records + Raoh decoders |
| `*ApplicationFactory.java` | `src/main/resources/db/migration/V1__*.sql` |
| `jaxrs/JsonBodyReader.java` | (Optional) unit tests |
| `jaxrs/JsonBodyWriter.java` | |

## Request lifecycle (happy path)

1. Jetty hands the request to `ApplicationComponent.handle(request)`.
2. The middleware chain runs top-to-bottom.
3. `RoutingMiddleware` finds a match in the compiled `Routes` → attaches
   controller class + method name to the request.
4. `JooqDslContextMiddleware` attaches the non-transactional `DSLContext`.
5. `JooqTransactionMiddleware` checks `@Transactional` — if present, opens a
   transaction and replaces the extension.
6. `SerDesMiddleware` peeks at the `Accept` / `Content-Type` headers and picks
   a reader/writer.
7. `ControllerInvokerMiddleware` resolves parameter types, instantiates the
   controller (cached), and invokes the method.
8. The return value travels back up the chain; `SerDesMiddleware` serializes
   it with `JsonBodyWriter`.

## What is NOT in the default stack

- **No Spring / Spring Boot**. Anywhere. Ever.
- **No Doma2 / JPA / Hibernate / MyBatis** — use jOOQ + Raoh.
- **No Lombok** — use records.
- **No Thymeleaf / Freemarker** — the default stack is JSON-only. Add a
  template engine component if you need HTML rendering.
- **No OpenTelemetry / Micrometer** by default — add the component if needed.
