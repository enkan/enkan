# Enkan Init Reference

This directory contains **canonical, copy-paste-ready** code patterns that the
`/init` command injects into the LLM prompt. Every snippet here compiles as-is
against the default jOOQ + Raoh + HikariCP + Flyway + H2 stack.

## How to use this reference (for the LLM)

Treat these files as the **single source of truth** for API shapes. Other
sources (GitHub fetches, model memory) may be out of date or reference a
different stack (Doma2, JPA). When in doubt, copy from these files.

## File guide

| File | What it shows |
|---|---|
| `imports.md` | Exact FQCNs for every import the default stack needs. Copy these verbatim — do not guess. |
| `patterns/routes.md` | `Routes.define(...).compile()` DSL and the `RoutesDef` class shape. |
| `patterns/controller.md` | Controller methods, parameter injection rules, how to obtain `DSLContext`. |
| `patterns/raoh-decoder.md` | `JooqRecordDecoders.combine(...).map(...)` — the correct Raoh API. |
| `patterns/domain-record.md` | Java `record` with a paired static Raoh decoder. |
| `patterns/flyway-migration.md` | `V1__*.sql` file layout and H2-compatible DDL. |
| `components/jooq.md` | How `JooqProvider` + `DSLContext` reach the controller. |

## Non-negotiable rules

1. **Package for HTTP types is `enkan.web.data.HttpRequest` / `HttpResponse`**,
   not `enkan.data.*`.
2. **`Ok` and `Err` are top-level records in `net.unit8.raoh`**, not nested in
   `Result`. Import them separately.
3. **Controllers do NOT use JAX-RS annotations** (`@Path`, `@GET`, `@POST`).
   Routing is declarative in `RoutesDef.java`.
4. **Never instantiate `DSLContext` / `DataSource` / `Flyway` yourself.** They
   are wired by the fixed `SystemFactory` and surfaced to controllers via the
   request extension `"jooqDslContext"`.
5. **Transactional methods use `jakarta.transaction.Transactional`**, not
   Spring's `org.springframework.transaction.annotation.Transactional`.
