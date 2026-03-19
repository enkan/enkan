# Contributing to Enkan

Thank you for your interest in contributing to Enkan!

## Reporting Bugs

Please open a [GitHub Issue](https://github.com/enkan/enkan/issues) with:

- A clear description of the problem
- Steps to reproduce
- Expected vs. actual behavior
- Java version and OS

## Suggesting Features

Open a GitHub Issue labeled `enhancement` with:

- **Rationale** -- why the change is needed
- **Scope** -- what is affected
- **Proposed direction** -- concrete approach
- **Acceptance criteria** -- how to verify completion

## Development Setup

### Requirements

- Java 25 or higher
- Maven 3.6.3+

### Build and Test

```sh
mvn test
```

## Pull Request Workflow

1. Create a feature branch from `develop`:

   ```sh
   git checkout -b feature/<topic> develop
   ```

2. Make your changes and commit.

3. Push and open a PR targeting `develop`:

   ```sh
   gh pr create --base develop
   ```

**Important:** Never commit directly to `develop` or `master`.

## Code Review Checklist

Before submitting, verify:

- All branches for malformed/invalid input are covered, not just the happy path
- String comparisons against HTTP header values are case-insensitive per the relevant RFC
- No unnecessary allocations on the request/response hot path
- Tests exercise the code path they claim to test

## License

By contributing, you agree that your contributions will be licensed under the [Eclipse Public License, Version 2.0](https://www.eclipse.org/legal/epl-2.0/).
