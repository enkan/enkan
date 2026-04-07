# Domain record + decoder pairing

The default pattern is: one Java `record` per table row, with a `public static
final` Raoh decoder next to it. This keeps the decoder collocated with the
type it builds and avoids a separate `Decoders.java` file.

## Shape

```java
package com.example.todo;

import net.unit8.raoh.decode.Decoder;
import org.jooq.Record;

import static net.unit8.raoh.decode.ObjectDecoders.*;
import static net.unit8.raoh.jooq.JooqRecordDecoders.combine;
import static net.unit8.raoh.jooq.JooqRecordDecoders.field;

public record Todo(Long id, String title, boolean completed) {
    public static final Decoder<Record, Todo> DECODER = combine(
            field("id",        long_()),
            field("title",     string()),
            field("completed", bool())
    ).map(Todo::new);
}
```

## Rules

1. **Declare the decoder as `public static final` inside the record.** Static
   fields are allowed in records.
2. **Record components match the column order** used in `combine(...)`.
   Misordering the columns will not fail at compile time but will swap values
   at runtime. Match the table DDL column order to reduce mistakes.
3. **Nullable columns**: wrap the field decoder in `nullable(...)`:
   ```java
   field("description", nullable(string()))   // field value becomes Optional<String>? No — see below.
   ```
   For truly optional fields, prefer `optionalField` / `optionalNullableField`.
4. **Use primitive wrappers** for nullable columns (`Long`, `Boolean`), and
   primitives (`long`, `boolean`) only when the column is `NOT NULL`.

## Why records and not POJO classes

- `SerDesMiddleware` + Jackson 3.x serialize records to JSON directly.
- Records are immutable, which matches Raoh's parse-don't-validate philosophy.
- No Lombok required — records have built-in accessors, `equals`, `hashCode`,
  `toString`.

## Naming

- Record: `Todo` (singular, PascalCase).
- Package: `com.example.<project>` (the base package, same level as
  `RoutesDef`). For larger projects, group records under
  `com.example.<project>.domain` — both are acceptable.

## Do NOT

- Do not use Lombok annotations.
- Do not add `@JsonProperty` annotations unless you actually need to rename a
  field — Jackson 3 reads record components by name.
- Do not implement custom `equals` / `hashCode` — records already do.
- Do not add constructors with side effects — decoders call `Todo::new`
  directly.
