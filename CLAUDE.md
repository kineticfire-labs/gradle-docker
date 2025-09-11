# CLAUDE.md

Use this file to guide Claude Code (`claude.ai/code`) when working with code in this repository.

## STATE THE PURPOSE
Create and publish a Gradle 9 plugin that provides Docker integration for:
- Building, tagging, saving, and publishing Docker images with multiple compression formats (none, gzip, bzip2, xz, zip)
- Orchestrating Docker Compose (`up` / `down`) for container-based testing
- Performing health checks and waiting for containers to reach `RUNNING` or `HEALTHY` states
- Integrating with JUnit 5 via extensions for test lifecycle and Docker Compose

## DESCRIBE THE ARCHITECTURE OVERVIEW

### Plugin Core Structure
- **Main Plugin**: `GradleDockerPlugin` - Entry point, validates requirements, registers services and extensions
- **Extensions**: `DockerExtension` (`docker` DSL), `DockerOrchExtension` (`dockerOrch` DSL), `TestIntegrationExtension`
- **Service Layer**: abstract external dependencies via interfaces - `DockerService`, `ComposeService`, `JsonService`
- **Task Layer**: implement Gradle tasks (`DockerBuildTask`, `DockerSaveTask`, `DockerPublishTask`, etc.)

### Apply Key Design Patterns
- **Service Abstraction**: `DockerServiceImpl` uses Docker Java Client, `ExecLibraryComposeService` uses exec library
- **Provider API**: configure all values with Gradle’s `Property<T>` and `Provider<T>` for lazy evaluation
- **Configuration Cache**:  make tasks serializable; avoid capturing Project references
- **Task Generation**: create dynamic per-image tasks (e.g., `dockerBuildAlpine`, `dockerSaveUbuntu`)

### Follow Testing Strategy
- **Achieve 100% code and branch coverage in unit tests, as measured by JaCoCo.**
  - If any code cannot be unit tested (e.g., direct external service calls), **document the gap** in `docs/design-docs/testing/unit-test-gaps.md`.
- **Achieve 100% coverage in functional tests.**
  - If any code cannot be functionally tested, **document the gap** in the same file.
- **Achieve 100% coverage in integration tests.**
  - If any code cannot be integration tested, **document the gap** in the same file.
- **Do not treat partial coverage (e.g., 60–80%) as acceptable.**
  - The only acceptable deviation is explicitly documented, justified, and referenced in the gap doc.
- **Mock Services**: replace external dependencies (Docker daemon, exec library) in unit tests

### Specify Locations
- **Plugin**
  - **Build file**: `plugin/build.gradle` 
  - **Source code**: `plugin/src/main`
  - **Unit tests**: `plugin/src/test/`
  - **Functional tests**: `plugin/src/functionalTest`
- **Integration Tests**
  - **Build file**: `plugin-integration-test/build.gradle`
  - **Example executable (used in image under test)**: `plugin-integration-test/app/`
  - **Example image under test**: `plugin-integration-test/app-image/src/main/`
  - **Functional tests**: `plugin-integration-test/app-image/functionalTest/`
  - **Integration tests**: `plugin-integration-test/app-image/integrationTest/`

## USE DEVELOPMENT COMMANDS

### Develop Plugin (from `plugin/` directory)
```bash
# Run unit tests only
#   - produces code coverage report at 'build/reports/jacoco/test/html/index.html'
./gradlew clean test

# Run functional tests only  
./gradlew clean functionalTest

# Build with unit tests and functional tests
#   - produces code coverage report at 'build/reports/jacoco/test/html/index.html'
./gradlew clean build

# Build plugin, running unit tests and functional tests, and put plugin in Maven local (necessary for integration tests)
#   - produces code coverage report at 'build/reports/jacoco/test/html/index.html'
./gradlew clean build publishToMavenLocal
```

### Run Integration Tests (from `plugin-integration-test/` directory)
```bash
# NOTE: if changes were made to plugin source, then it must be re-built and published to maven local!  See the 'plugin'
# project above!

# Full integration test with smoke + integration tests
./gradlew clean testAll

# Build application and Docker image
./gradlew clean buildAll

# Individual test suites
./gradlew smokeTest
./gradlew integrationTestSuite
```

### Follow Development Workflow
1. **Build the plugin**:
    - Run: `cd plugin && ./gradlew clean build publishToMavenLocal`.
    - Do not declare success until the build completes without errors.
2. **Run integration tests**:
    - Run: `cd ../plugin-integration-test && ./gradlew clean testAll`.
    - Do not declare success until every test passes.
    - Do not treat partial pass rates (e.g., “most tests passed”) as acceptable.

## Use Gradle 9 Standards
- **Ensure code is compatible with Gradle 9’s build cache**; see 
  `docs/design-docs/gradle-9-configuration-cache-guidance.md`.
- **Declare dependencies in `build.gradle`** and define their module and version in `gradle/libs.versions.toml` (next to 
  `build.gradle`) using Gradle 9’s version catalog.
- Do not use outdated dependency definition approaches.

## Handle Functional Test Dependency Issues
- **Treat the `TestKit` dependency as incompatible with Gradle 9’s build cache**; see 
  `docs/design-docs/functional-test-testkit-gradle-issue.md`.
- **Write functional tests, but disable them if required** due to the `TestKit` issue.
- Do not declare success if functional tests are missing or skipped without justification.

## Follow Docker Requirements
- **Do not specify `version` in Docker Compose files**; it is deprecated and generates warnings.
- Do not declare success if Docker Compose files include the deprecated `version` field.

## SATISFY GENERAL ACCEPTANCE CRITERIA (e.g., "DEFINITION OF DONE")
- **All unit tests and functional tests must pass.**
  - Do not declare success until every unit and functional test passes.
  - Run: `./gradlew clean test functionalTest` (from `plugin/` directory).
  - Do not treat partial pass rates (e.g., “most tests passed”) as acceptable.
- **The plugin must build successfully.**
  - Do not declare success until the build completes without errors.
  - Run: `./gradlew build` (from `plugin/` directory).
- **All integration tests must pass.**
  - Do not declare success until every integration test passes.
  - Run:
      - Rebuild plugin to Maven local: `./gradlew build publishToMavenLocal` (from `plugin/` directory).
      - Run tests: `./gradlew clean fullTest` (from `plugin-integration-test/` directory).
  - Do not treat partial pass rates (e.g., “most tests passed”) as acceptable.
- **No lingering containers may remain.**
  - Do not declare success until `docker ps -a` shows no containers.
  - Do not treat “some leftover containers are acceptable” as valid.

## ENFORCE PROJECT STANDARDS ENFORCEMENT

### Meet Testing Requirements (`docs/project-standards/testing/testing.md`)
- **Achieve 100% line and branch coverage** in all tests.
    - If 100% is not possible (e.g., due to external service calls), **document the gap** in the gap file.
    - Applies to all methods, including private methods; **use reflection if necessary** to test these.
- **Ensure unit tests are isolated** (no network, filesystem, or clock dependencies).
    - Write or refactor code to make the maximum amount of code unit-testable.
    - Move code that cannot be easily unit tested into the smallest method possible, and document gaps.
- **Apply property-based testing** where applicable to achieve input domain coverage.
- **Do not declare success if partial coverage or untested code exists without explicit gap documentation.**

### Ensure Code Quality (`docs/project-standards/style/style.md`)
- **Keep files ≤ 500 lines, functions ≤ 30–40 lines, and max 4 parameters per function.**
- **Limit line length to 120 characters; use spaces (never tabs).**
- **Keep cyclomatic complexity ≤ 10 and nesting depth ≤ 3.**
- **Do not declare success if code exceeds these limits without refactoring.**

### Apply Development Philosophies (`docs/project-standards/development-philosophies/development-philosophies.md`)
- **Apply KISS, YAGNI, and DRY principles.**
- **Follow SOLID design patterns and enforce Separation of Concerns.**
- **Prefer Convention over Configuration and Fail Fast approaches.**
- **Write code for human understanding; favor regularity over special cases.**
- **Do not declare success if code violates these philosophies.**

### Follow Documentation Requirements
- **Apply documentation rules to both source code comments and text documents (e.g., `README.md`).**
- **Limit line length to 120 characters; use spaces (never tabs).**
- **Do not declare success if documentation violates these standards.**

## Follow Search Patterns
- **Use `rg "pattern"` instead of `grep -r "pattern" .`** for faster, more accurate recursive searches.
- **Use `rg --files -g "*.groovy"` instead of `find . -name "*.groovy"`** for consistent file listing.
- **Do not use `grep` or `find` when an equivalent `rg` command exists.**  