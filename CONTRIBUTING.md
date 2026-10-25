# Contributing to Estoa

Thanks for taking the time to contribute. Estoa is a small, local-first project:
keeping it simple, auditable, and dependency-light is part of the goal.

## Before you start

1. Open an [issue](https://github.com/mikear/estoa/issues) or a
   [discussion](https://github.com/mikear/estoa/discussions) before writing
   large changes. It avoids duplicated work.
2. For bugs, use the template and include the version, OS, and minimal steps.
3. For vulnerabilities, do **not** open a public issue. Follow
   [SECURITY.md](SECURITY.md).

## Development setup

Requirements: JDK 21+ and Maven 3.9+. There is no Maven wrapper in the repo.

```bash
cd src
mvn test
```

Build the fat JAR for your platform:

```bash
mvn clean package -DskipTests            # Linux
mvn clean package -DskipTests -Pwindows  # Windows
mvn clean package -DskipTests -Pmacos    # macOS Intel
```

Run the demo:

```bash
java -jar orion-app/target/estoa.jar --demo
```

## Project rules

- **Canonical identity:** entities are merged as `TYPE:normalized-value` in
  lowercase. Do not create duplicate nodes for the same observable.
- **Connectors never throw:** return a `SourceResult` with the error and let the
  rest of the flow continue.
- **No secrets in the repo:** API keys live encrypted in `~/.orion/keys.dat`.
  Never commit keys, dumps, or case data.
- **Local-first:** do not add telemetry or silent network calls. New network
  activity must be explicit, documented, and triggered by the user.
- **Spanish UI:** user-facing strings, comments, and docs are in Spanish with
  correct accents and UTF-8 encoding (no BOM).
- **Minimal dependencies:** prefer the standard library. Justify any new
  dependency in the pull request.
- **No emojis in the UI:** icons come from the Tabler set in `Icons.java`.

## Tests

- Every behavior change needs a test. `mvn test` must pass before a pull request.
- The suite currently has 72 JUnit 5 tests across 14 suites.
- For UI changes, include a screenshot from `--demo --screenshot` if possible.

## Pull requests

1. Fork the repo and create a branch: `git checkout -b feature/my-change`.
2. Make a focused change; avoid unrelated refactors in the same PR.
3. Run `mvn test`.
4. Describe **what** changed and **why**, and link the related issue.
5. Update documentation or screenshots when behavior or UI changes.
6. Make sure CI is green.

## Commit messages

Short and imperative. Spanish or English is fine. Examples:

```text
Fix new-case graph reset
Add EPSS source to risk scoring
Docs: clarify H2 lock troubleshooting
```

## Code of conduct

By participating you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
