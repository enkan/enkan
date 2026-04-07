# Raoh jOOQ decoder pattern

Raoh turns a flat jOOQ `Record` into a typed domain value and accumulates
errors at precise field paths. Use it instead of jOOQ's `into(Todo.class)` —
it's type-safe and surfaces missing/invalid columns as structured errors.

## The exact API

- `combine(dec1, dec2, ..., decN)` — up to 16 arguments, returns a `CombinerN`
  that you then call `.map(Constructor::new)` on.
- `field(name, fieldDecoder)` — extracts a named column and decodes it.
- Field decoders come from `net.unit8.raoh.decode.ObjectDecoders`:
  `string()`, `int_()`, `long_()`, `double_()`, `float_()`, `bool()`,
  `decimal()`, `date()`, `time()`, `dateTime()`, `enumOf(Cls.class)`.
- `nullable(dec)` wraps a decoder to allow null.
- The result of `.map(Constructor::new)` is a `Decoder<Record, T>`. Storing it
  as a `JooqRecordDecoder<T>` requires the method reference `::decode`.

## Minimal decoder (place inside the domain record)

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

## Pattern-matching on `Result`

```java
import net.unit8.raoh.Result;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Err;

Result<Todo> result = Todo.DECODER.decode(rec);
switch (result) {
    case Ok<Todo>(Todo todo) -> {
        // use todo
    }
    case Err<Todo>(var issues) -> {
        // issues.asList() → List<Issue>; each Issue has path(), code(), message()
    }
}
```

## Common `Err` paths to inspect

- `Issue.code() == "missing_field"` → column is not in the result set
- `Issue.code() == "type_mismatch"` → column value wasn't the expected type
- `Issue.code() == "required"` → column was present but null
- `Issue.path().toString()` → e.g. `"/title"` for the field that failed

## If you just want the value or to throw

```java
Todo todo = Todo.DECODER.decode(rec).getOrThrow();
// or
Todo todo = Todo.DECODER.decode(rec).orElseThrow(issues ->
        new IllegalArgumentException("invalid row: " + issues.asList()));
```

## JOIN → nested records

For multi-table queries that flatten into one `Record`, use `nested(...)` to
reuse sub-decoders:

```java
import static net.unit8.raoh.jooq.JooqRecordDecoders.nested;

public record UserWithAddress(User user, Address address) {
    public static final Decoder<Record, UserWithAddress> DECODER = combine(
            nested(User.DECODER::decode),     // JooqRecordDecoder reference
            nested(Address.DECODER::decode)
    ).map(UserWithAddress::new);
}
```

## Do NOT

- **Do NOT write `Result.Ok` or `Result.Err`** — `Ok` and `Err` are top-level
  records in `net.unit8.raoh`, not nested inside `Result`.
- Do not use `rec.get("title", String.class)` for decoding — it throws on type
  mismatch instead of returning a structured error.
- Do not use jOOQ's `fetchInto(Todo.class)` — it reflects on setters, which
  records don't have.
