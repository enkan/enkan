# jOOQ component reference

## What the fixed `SystemFactory` wires

```java
"datasource", new HikariCPComponent(OptionMap.of("uri", "jdbc:h2:mem:...")),
"flyway",     new FlywayMigration(),
"jooq",       builder(new JooqProvider())
                     .set(JooqProvider::setDialect, SQLDialect.H2)
                     .build(),
```

with relationships:

```
http → app → jackson, hmac, jooq, datasource
jooq → datasource, flyway
flyway → datasource
```

## What the fixed `ApplicationFactory` adds to the middleware stack

```java
app.use(new JooqDslContextMiddleware<>());   // sets request.getExtension("jooqDslContext")
app.use(new JooqTransactionMiddleware<>());  // wraps @Transactional methods in a tx
```

Both run **after** `RoutingMiddleware` so the method being invoked is known
to the transaction middleware.

## Controller access pattern

```java
public List<Todo> list(HttpRequest request) {
    DSLContext dsl = request.getExtension("jooqDslContext");
    return dsl.select().from(table("todos")).fetch()
            .stream()
            .map(Todo.DECODER::decode)
            .map(r -> r.getOrThrow())     // or pattern-match for structured errors
            .toList();
}
```

## Writing queries

Use jOOQ's plain-SQL DSL (`table("name")`, `field("name")`) rather than
generated sources — the default stack does not run jOOQ's code generator.

```java
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;

// SELECT
dsl.select(field("id"), field("title"))
   .from(table("todos"))
   .where(field("completed").eq(false))
   .orderBy(field("id").desc())
   .fetch();

// INSERT returning generated id
Long id = dsl.insertInto(table("todos"))
        .columns(field("title"), field("completed"))
        .values("Buy milk", false)
        .returning(field("id"))
        .fetchOne()
        .get(field("id"), Long.class);

// UPDATE
dsl.update(table("todos"))
   .set(field("completed"), true)
   .where(field("id").eq(42L))
   .execute();

// DELETE
dsl.deleteFrom(table("todos"))
   .where(field("id").eq(42L))
   .execute();
```

## Do NOT

- Do not call `JooqProvider::getDSLContext` directly from a controller. It
  returns the non-transactional `DSLContext`, escaping `@Transactional`.
- Do not run jOOQ's code generator plugin. The default POM does not configure
  it, and the reference patterns use the plain-SQL DSL.
- Do not instantiate `DSL.using(...)` yourself.
