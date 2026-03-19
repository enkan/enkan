# Enkan Project Instructions

## Language

All code, comments, commit messages, and documentation MUST be written in **English**.

## Overview

Enkan is a middleware-chain-based web framework for Java 25.
Maven multi-module project (`enkan-parent` version `0.14.0`).

## Module Structure

| Module | Role |
| ------ | ---- |
| `enkan-core` | Core types and interfaces (`HttpRequest`, `HttpResponse`, `Middleware`, etc.) |
| `enkan-web` | Web middleware collection |
| `enkan-system` | `EnkanSystem`, `Repl` interface, dependency injection |
| `enkan-repl-server` | `JShellRepl` implementation (ZeroMQ + JShell) |
| `enkan-repl-client` | JLine-based REPL client |
| `enkan-servlet` | Servlet API bridge (`ServletUtils`) |
| `kotowari` | MVC routing and controller invocation |
| `kotowari-jpa` | JPA `EntityManager` parameter injector |
| `kotowari-graalvm` | GraalVM Native Image support (`EnkanFeature`, `KotowariFeature`) |
| `kotowari-graalvm-example` | Native Image example app (Undertow + Jackson) |
| `enkan-devel` | Development commands (autoreset, compile) |
| `enkan-throttling` | Token Bucket-based rate limiting |
| `enkan-component-jetty` | Jetty server component (virtual thread support) |
| `enkan-component-undertow` | Undertow server component |
| `enkan-component-jackson` | Jackson JSON serialization |
| `enkan-component-micrometer` | Micrometer metrics (replaces Dropwizard Metrics) |
| `enkan-component-opentelemetry` | OpenTelemetry distributed tracing |
| `enkan-component-metrics` | Dropwizard Metrics (deprecated) |
| `enkan-component-*` | Other components (JPA, EclipseLink, Flyway, HikariCP, Thymeleaf, Freemarker, jOOQ, Doma2) |

## Build and Test

```sh
# Full build
mvn test

# Specific module only
mvn test -pl kotowari -am

# CI mode (excludes flaky JShell integration tests)
mvn test -B -DexcludedGroups=integration
```

## Key Architectural Knowledge

### JShellRepl Command Types

- `registerCommand(name, command)`: Serializes the command into the JShell environment. `SystemCommand implements Serializable` is required.
- `registerLocalCommand(name, command)`: Executes on the host JVM side. No serialization needed. Use for commands that reference non-serializable objects like `Repl`.
- `eval(statement, transport)`: Executes a statement inside JShell from the host side. Use when you need to manipulate JShell-internal variables.

### kotowari Parameter Injection

- `ParameterUtils.getDefaultParameterInjectors()` returns a new `LinkedList` each time (not a shared instance).
- Default injectors: `HttpRequest`, `Parameters`, `Session`, `Flash`, `Principal`, `Conversation`, `ConversationState`, `Locale`.
- `EntityManagerInjector` is NOT in the default list. Add it explicitly when using the `kotowari-jpa` module.

### MixinUtils Pattern

Add capabilities to request objects via `MixinUtils.mixin(request, SomeInterface.class)`.
Examples: `EntityManageable`, `BodyDeserializable`, `Routable`, `ContentNegotiable`.

`MixinUtils.createFactory()` uses the Class File API to generate a subclass at startup for pre-mixed `HttpRequest` objects (`WebApplication.buildRequestFactory()`).

## Release Procedure

### 1. Prepare the release

```sh
# Merge develop → master
git checkout master
git merge develop

# Set release version (removes -SNAPSHOT)
./scripts/update-version.sh 0.14.0

# Commit and tag
git add -A
git commit -m "release: v0.14.0"
git tag v0.14.0
```

### 2. Publish

```sh
# Push tag — triggers .github/workflows/release.yml
git push origin master --tags
```

The release workflow automatically:

- Publishes all modules to Maven Central (via `central-publishing-maven-plugin`)
- Builds `enkan-repl-client.jar`
- Creates a GitHub Release with auto-generated release notes

**Required GitHub Secrets:** `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`

### 3. Prepare next development cycle

```sh
git checkout develop
git merge master
./scripts/update-version.sh 0.14.1-SNAPSHOT
git add -A
git commit -m "chore: bump version to 0.14.1-SNAPSHOT"
git push origin develop
```

### 4. Post-release checklist

- Update `docs/src/content/getting-started.md` version references
- Update `README.md` archetype version (`-DarchetypeVersion=`)
- Verify artifacts on Maven Central (may take ~30 min to propagate)

## Improvement Proposals (ADR)

Improvement proposals and architectural decisions are tracked as GitHub Issues.

- Use `gh issue create` to file new proposals
- Include the following sections in the issue body:
  - **Rationale** — why the change is needed
  - **Scope** — what is affected
  - **Proposed direction** — concrete approach
  - **Acceptance criteria** — how to verify completion
- Label proposals with `enhancement` and a priority label (`priority:high`, `priority:medium`, `priority:low`)
- Close the issue with a reference commit (`closes #N`) when implemented
- Close with `wontfix` label when rejected, with a brief reason in a comment

## Code Review Checklist

Apply this checklist both when reviewing others' code **and** before submitting your own changes.

- Verify all branches for malformed/invalid input, not just the happy path. **This applies to tests too**: confirm that each test actually exercises the code path it claims — e.g. don't percent-encode chars when testing a regex that runs on the raw string.
- When multiple parsing strategies exist (e.g. quoted vs. unquoted), ensure malformed input in one strategy does not silently fall through to another and produce a wrong result.
- When extending the scope of a utility function (e.g. adding new types to a filter), trace all call sites to verify that downstream behavior is correct for the new inputs — not just the entry point.
- String comparisons against HTTP header values (media types, field names) must be case-insensitive per the relevant RFC. Verify that `equalsIgnoreCase`, `toLowerCase(Locale.ROOT)`, or `regionMatches(true, ...)` is used appropriately.
- When modifying a utility on the request/response hot path, check for unnecessary allocations (e.g. `String.split`, `toLowerCase`) and prefer allocation-free alternatives (`indexOf` loop, `regionMatches`) consistent with surrounding code.

## Pull Requests

- **Never commit directly to `develop` or `master`.** Always work on a feature branch and open a PR.
- Always target `develop` as the base branch when creating PRs (not `main`)
- Use `gh pr create --base develop` explicitly
- Workflow: create feature branch → commit → `gh pr create --base develop`
