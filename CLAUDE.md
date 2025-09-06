# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## PURPOSE
Create and publish a Gradle plugin that provides Docker integration for:
- Building, tagging, saving, and publishing Docker images with multiple compression formats (none, gzip, bzip2, xz, zip)
- Docker Compose orchestration (`up`/`down`) for container-based testing
- Health checking and waiting for containers to reach `RUNNING` or `HEALTHY` states
- JUnit 5 extensions for test lifecycle integration with Docker Compose

## ARCHITECTURE OVERVIEW

### Plugin Core Structure
- **Main Plugin**: `GradleDockerPlugin` - Entry point, validates requirements, registers services and extensions
- **Extensions**: `DockerExtension` (docker DSL), `DockerOrchExtension` (dockerOrch DSL), `TestIntegrationExtension`
- **Service Layer**: Abstracts external dependencies via interfaces - `DockerService`, `ComposeService`, `JsonService`
- **Task Layer**: Gradle tasks for operations - `DockerBuildTask`, `DockerSaveTask`, `DockerPublishTask`, etc.

### Key Design Patterns
- **Service Abstraction**: `DockerServiceImpl` uses Docker Java Client, `ExecLibraryComposeService` uses exec library
- **Provider API**: All configuration uses Gradle's modern `Property<T>` and `Provider<T>` for lazy evaluation
- **Configuration Cache**: Tasks are serializable and don't capture Project references
- **Task Generation**: Dynamic per-image tasks (e.g., `dockerBuildAlpine`, `dockerSaveUbuntu`)

### Testing Strategy
- **100% Coverage Requirement**: Both line and branch coverage via JaCoCo
- **Test Pyramid**: Unit tests (70%), functional tests (20%), integration tests (8%), e2e tests (2%)
- **Service Mocking**: External dependencies (Docker daemon, exec library) are mocked in unit tests
- **Gap Documentation**: Any coverage gaps must be documented in `docs/design-docs/testing/unit-test-gaps.md`

## DEVELOPMENT COMMANDS

### Plugin Development (from `plugin/` directory)
```bash
# Run unit tests only
./gradlew clean test

# Run functional tests only  
./gradlew clean functionalTest

# Build with full test suite
./gradlew clean build

# Coverage report (after tests)
./gradlew jacocoTestReport
# Reports in: build/reports/jacoco/test/html/index.html
```

### Integration Testing (from `plugin-integration-test/` directory)
```bash
# Full integration test with smoke + integration tests
./gradlew clean testAll

# Build application and Docker image
./gradlew clean buildAll

# Individual test suites
./gradlew smokeTest
./gradlew integrationTestSuite
```

### Development Workflow
1. Build plugin: `cd plugin && ./gradlew clean build`
2. Test integration: `cd ../plugin-integration-test && ./gradlew clean testAll`

## PROJECT STANDARDS ENFORCEMENT

### Testing Requirements (`docs/project-standards/testing/testing.md`)
- Achieve 100% line and branch coverage or document gaps
- Unit tests must be isolated (no network, filesystem, clock dependencies)
- Use property-based testing where applicable for input domain coverage

### Code Quality (`docs/project-standards/style/style.md`)
- Files ≤ 500 lines, functions ≤ 30-40 lines, max 4 parameters
- Line length ≤ 120 characters, use spaces (never tabs)
- Cyclomatic complexity ≤ 10, nesting depth ≤ 3

### Development Philosophies (`docs/project-standards/development-philosophies/development-philosophies.md`)
- KISS, YAGNI, DRY principles
- SOLID design patterns, Separation of Concerns
- Convention over Configuration, Fail Fast
- Write code for human understanding, favor regularity over special cases

## SEARCH PATTERNS
- Use `rg "pattern"` instead of `grep -r "pattern" .`
- Use `rg --files -g "*.groovy"` instead of `find . -name "*.groovy"`