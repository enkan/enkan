# Exhaustive import list (default jOOQ+Raoh stack)

Small models often guess wrong package names. These are the **exact** FQCNs the
default stack uses. Copy them verbatim.

## Common package pitfalls (read this first)

Enkan went through a package rename. Training data still shows the OLD paths.
The rules:

| What                | OLD (wrong)                                                     | NEW (correct)                                       |
| ------------------- | --------------------------------------------------------------- | --------------------------------------------------- |
| HTTP types          | `enkan.data.HttpRequest`                                        | `enkan.web.data.HttpRequest`                        |
| Most middlewares    | `enkan.middleware.XxxMiddleware`                                | `enkan.web.middleware.XxxMiddleware`                |
| Content negotiation | `enkan.web.middleware.negotiation.ContentNegotiationMiddleware` | `enkan.web.middleware.ContentNegotiationMiddleware` |

**Exceptions** — a few middlewares kept the old `enkan.middleware.*` path
because they live in separate component modules, not in `enkan-web`:

- `enkan.middleware.jooq.JooqDslContextMiddleware`
- `enkan.middleware.jooq.JooqTransactionMiddleware`
- `enkan.middleware.ServiceUnavailableMiddleware` (in `enkan-core`, rarely used)

If you remember a middleware as `enkan.middleware.Xxx` and it is NOT jOOQ
or `ServiceUnavailable`, the correct path is almost certainly
`enkan.web.middleware.Xxx`.

## HTTP types

```java
import enkan.web.data.HttpRequest;     // NOT enkan.data.HttpRequest
import enkan.web.data.HttpResponse;    // NOT enkan.data.HttpResponse
import enkan.web.util.HttpResponseUtils;
```

## Request-scoped utilities

```java
import enkan.collection.Parameters;    // path/query/form params — NOT java.util.Map
import enkan.system.inject.ComponentInjector;
```

## Web middlewares (all under `enkan.web.middleware`)

```java
import enkan.web.middleware.ContentNegotiationMiddleware;   // NOT .negotiation.*
import enkan.web.middleware.ContentTypeMiddleware;
import enkan.web.middleware.CookiesMiddleware;
import enkan.web.middleware.DefaultCharsetMiddleware;       // NOT enkan.middleware.*
import enkan.web.middleware.NestedParamsMiddleware;
import enkan.web.middleware.ParamsMiddleware;
import enkan.web.middleware.TraceMiddleware;
```

## Kotowari routing and middleware

```java
import kotowari.routing.Routes;
import kotowari.middleware.RoutingMiddleware;
import kotowari.middleware.ControllerInvokerMiddleware;
import kotowari.middleware.SerDesMiddleware;
import kotowari.middleware.FormMiddleware;
import kotowari.middleware.ValidateBodyMiddleware;
import kotowari.middleware.serdes.ToStringBodyWriter;
```

## jOOQ DSL

```java
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
```

## jOOQ integration middleware

```java
import enkan.component.jooq.JooqProvider;
import enkan.middleware.jooq.JooqDslContextMiddleware;
import enkan.middleware.jooq.JooqTransactionMiddleware;
```

## Transactional annotation

```java
import jakarta.transaction.Transactional;   // NOT org.springframework.*
```

## Raoh (row → domain decoding)

```java
// Top-level Result sum type
import net.unit8.raoh.Result;
import net.unit8.raoh.Ok;       // top-level record — do not qualify with Result.
import net.unit8.raoh.Err;      // top-level record — do not qualify with Result.
import net.unit8.raoh.Issues;

// Field decoders (string(), int_(), long_(), bool(), decimal(), date(), dateTime(), enumOf(Class))
import static net.unit8.raoh.decode.ObjectDecoders.*;

// Record combinators (combine, field, nested, optionalField)
import static net.unit8.raoh.jooq.JooqRecordDecoders.*;
import net.unit8.raoh.jooq.JooqRecordDecoder;
import net.unit8.raoh.decode.Decoder;
```

## Components (wiring — already handled by the fixed SystemFactory)

These are for reference only. The generated `SystemFactory` already imports them.

```java
import enkan.Env;
import enkan.collection.OptionMap;
import enkan.component.ApplicationComponent;
import enkan.component.WebServerComponent;
import enkan.component.builtin.HmacEncoder;
import enkan.component.flyway.FlywayMigration;
import enkan.component.hikaricp.HikariCPComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.component.jetty.JettyComponent;
import enkan.config.EnkanSystemFactory;
import enkan.system.EnkanSystem;
import static enkan.component.ComponentRelationship.component;
import static enkan.util.BeanBuilder.builder;
```
