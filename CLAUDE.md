# CLAUDE.md

Use this file to guide Claude Code (`claude.ai/code`) when working with code in this repository.

## STATE THE PURPOSE
Create and publish a Gradle 9 plugin that provides Docker integration for:
- Building, tagging, saving, and publishing Docker images
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
  - If any code cannot be unit tested (e.g., direct external service calls), **document the gap** in 
    `docs/design-docs/testing/unit-test-gaps.md`.
- **Achieve 100% coverage in functional tests.**
  - If any code cannot be functionally tested, **document the gap** in the same file.
- **Achieve 100% coverage in integration tests.**
  - If any code cannot be integration tested, **document the gap** in the same file.
- **Do not treat partial coverage (e.g., 60–80%) as acceptable.**
  - The only acceptable deviation is explicitly documented, justified, and referenced in the gap doc.
- **Mock Services**: replace external dependencies (Docker daemon, exec library) in unit tests

### Specify Locations
- **Plugin**
  - **Build file for 'plugin' subproject**: `plugin/build.gradle` 
  - **Source code**: `plugin/src/main`
  - **Unit tests**: `plugin/src/test/`
  - **Functional tests**: `plugin/src/functionalTest`
- **Integration Tests**
  - **Build file for 'plugin-integration-test' subproject**: `plugin-integration-test/build.gradle`
  - **Example executable (used in image under test)**: `plugin-integration-test/app/`
  - **Integration tests for `docker` task**: `plugin-integration-test/app-image/integrationTest/docker/`
  - **Integration tests for `dockerOrch` task**: `plugin-integration-test/app-image/integrationTest/dockerOrch/`
- **Re-usable Integration Test Verification Functionality**
  - **buildSrc**: `plugin-integration-test/buildSrc/`

## USE DEVELOPMENT COMMANDS

### Develop Plugin (from `plugin/` directory)
```bash
# Run unit tests only
#   - produces code coverage report at 'build/reports/jacoco/test/html/index.html'
./gradlew clean test

# Run functional tests only  
./gradlew clean functionalTest

# Build with unit tests
#   - set 'version' to the plugin version such as '1.0.0'
#   - produces code coverage report at 'build/reports/jacoco/test/html/index.html'
./gradlew -Pplugin_version=<version> clean build

# Build plugin, running unit tests and putting the plugin in Maven local (necessary for integration tests)
#   - set 'version' to the plugin version such as '1.0.0'
#   - produces code coverage report at 'build/reports/jacoco/test/html/index.html'
./gradlew -Pplugin_version=<version> clean build publishToMavenLocal

# Run integration tests: building the plugin, running unit tests, and putting the plugin in Maven local (necessary for 
# integration tests) 
#   - set 'version' to the plugin version such as '1.0.0'
#   - produces code coverage report at 'build/reports/jacoco/test/html/index.html'
./gradlew -Pplugin_version=<version> clean build publishToMavenLocal cleanIntegrationTest integrationTest
```

### Run Integration Tests (from `plugin-integration-test/` directory)
```bash
# NOTE: if changes were made to plugin source, then it must be re-built and published to maven local using!  See the 
# 'plugin' project above!
./gradlew -Pplugin_version=<version> cleanAll integrationTest
```

### Adhere to Plugin Usage
- Follow plugin usage:
   - for 'docker' DSL (e.g., tasks for build, tag, save, publish): `docs/usage/usage-docker.md`.
   - for 'dockerOrch' DSL (e.g., using 'docker compose' for image testing): `docs/usage/usage-docker-orch.md`.
- If necessary to modify the usage, then update these documents.

### Follow Development Workflow
1. **Build the plugin**:
    - Run: `cd plugin && ./gradlew -Pplugin_version=<version> clean build publishToMavenLocal`.
    - Do not declare success until the build completes without errors.
2. **Run integration tests**:
    - Run: `cd ../plugin-integration-test && ./gradlew cleanAll integrationTest`.
    - Do not declare success until every test passes.
    - Do not treat partial pass rates (e.g., “most tests passed”) as acceptable.

## Use Gradle 9 and 10 Standards
- **Ensure code is compatible with Gradle 9 and 10**; see 
  `docs/design-docs/gradle-9-and-10-compatibility.md` and specific instructions for the plugin in 
  `docs/usage/gradle-9-and-10-compatibility-practices.md`.
- **Declare dependencies in `build.gradle`** and define their module and version in `gradle/libs.versions.toml` (next to 
  `build.gradle`) using Gradle 9’s version catalog.
- Do not use outdated dependency definition approaches.

## Handle Functional Test Configuration Cache
- **TestKit is compatible with Gradle 9 and 10** but has configuration cache limitations (GitHub issue #34505).
- **Functional tests are marked as incompatible with configuration cache** in build.gradle to work around TestKit
  service cleanup conflicts.
- **All functional tests must be enabled and passing** - this is a hard requirement for project acceptance criteria.
- Do not declare success if functional tests are missing, disabled, or skipped without explicit documented
  justification.

## Follow Docker Requirements
- **Do not specify `version` in Docker Compose files**; it is deprecated and generates warnings.
- Do not declare success if Docker Compose files include the deprecated `version` field.

## SATISFY GENERAL ACCEPTANCE CRITERIA (e.g., "DEFINITION OF DONE")
- **All unit tests and functional tests must pass.**
  - Do not declare success until every unit and functional test passes.
  - Run: `./gradlew clean test functionalTest` (from `plugin/` directory).
  - Do not treat partial pass rates (e.g., "most tests passed") as acceptable.
  - All functional tests must be enabled (no `.disabled` files) unless explicitly documented gap exists.
- **The plugin must build successfully.**
  - Do not declare success until the build completes without errors.
  - Run: `./gradlew -Pplugin_version=<version> build` (from `plugin/` directory).
- **All integration tests must pass.**
  - Do not declare success until every integration test passes.
  - Run:
      - Rebuild plugin to Maven local: `./gradlew -Pplugin_version=<version> build publishToMavenLocal` (from `plugin/` directory).
      - Run tests: `./gradlew cleanAll integrationTest` (from `plugin-integration-test/` directory).
  - Do not treat partial pass rates (e.g., “most tests passed”) as acceptable.
- **No lingering containers may remain.**
  - Do not declare success until `docker ps -a` shows no containers.
  - Do not treat “some leftover containers are acceptable” as valid.


## ENFORCE PROJECT STANDARDS ENFORCEMENT

### Write code for maximal unit test coverage (minimize & simplify mocks)
- Prefer pure functions; move logic out of methods with side effects into pure helpers.
- Isolate side effects (I/O, clock, randomness, OS, network, DB) behind tiny boundary functions.
- Inject dependencies (constructor/params) as interfaces; never locate them inside functions.
- No globals/singletons/static I/O; avoid hidden state and implicit caches.
- Keep classes and functions small, single-purpose, with minimal public surface.
- Make functions deterministic and total; validate inputs early with guard clauses.
- Return values, not void; avoid work in constructors.
- Flatten control flow: explicit branches, early returns, shallow nesting.
- Favor immutable data/value objects over mutable maps and optional internal state.
- Abstract time, randomness, config (e.g., `Clock`, `Rng`, `Config`) and pass them in.
- Apply impure shell, pure core: boundary → translate → pure core → translate → boundary.
- Create test seams: pass factories/selectors; avoid `new` inside logic.
- When mocking is needed, mock only boundary interfaces; keep them narrow and stable.
- Prefer fakes/in-memory impls over deep mocks for complex protocols.
- Control concurrency: inject executors/schedulers; make async code awaitable/testable.
- No sleeps; wait on signals/conditions; expose timeouts as parameters.
- Make boundary helpers idempotent; surface retries as configurable policy.
- Inject logger; keep log formatting outside core logic.
- Keep configuration external; pass as data, never read env/files in core.
- Aim for one behavior per function to map cleanly to one focused test.

### Meet Unit Testing Requirements (`docs/project-standards/testing/unit-testing.md`)
- **Achieve 100% line and branch coverage** in all tests.
    - If 100% is not possible (e.g., due to external service calls), **document the gap** in the gap file.
    - Applies to all methods, including private methods; **use reflection if necessary** to test these.
- **Ensure unit tests are isolated** (no network, filesystem, or clock dependencies).
    - Write or refactor code to make the maximum amount of code unit-testable.
    - Move code that cannot be easily unit tested into the smallest method possible, and document gaps.
- **Apply property-based testing** where applicable to achieve input domain coverage.
- **Do not declare success if partial coverage or untested code exists without explicit gap documentation.**

### Meeting Functional Testing Requirements (`docs/project-standards/testing/functional-testing.md`)
1. 100% functionality coverage is required.
2. Validate end-to-end behavior.


### Adhere to Integration Testing Requirements

Write **real, end-to-end** integration tests that exercise the Gradle Docker/Compose plugin exactly like a user would.  
**Do not** test DSL or internals here. Prove that:
- Write integration tasks that use the plugin's Gradle tasks to build, tag, save, and publish a Docker image by actually 
  calling the Docker engine
  - Verify build and tag operations by listing images with `docker images`
  - Verify save operations by doing an `ls` on the filesystem
  - Verify the publish operations by using a local, temporary Docker private registry and checking that registry for the
    expected image; public Docker repository functionality will be added later, but must be verified to work in a
    similar manner 
- `composeUp` actually brings containers up by actually calling Docker Compose, checking the containers are in the 
  expected status with `docker ps -a`, and test code interacts with the containers
- `composeDown` actually calls Docker Compose and tears down the containers, and check with `docker ps -a`

#### Follow Ground Rules
1. **No mocks/stubs/fakes** for Docker, Compose, filesystem, or network. Use the real stack.
2. Run tests against a **peer project** laid out like a normal user project (builds a JAR, applies the plugin, uses its
   tasks).
3. Each test must **create and clean up** all resources it uses (images, tags, containers, networks, temp files, temp
   registry).
4. **Fail fast with clear messages** and leave **zero** residual containers, images, or networks on success.

### Follow Integration Test Requirements

Write real integration test code that uses the Gradle Docker/Compose plugin exactly like a user of the plugin would (not 
a developer of the plugin).  These double as demonstrations of the plugin to its user base.  **Do not** test DSL or 
internals here.
- **No mocks/stubs/fakes** for Docker, Compose, filesystem, or network. Use the real stack.
- Follow the directions and integration test layout at [README](plugin-integration-test/README.md).
- For 'docker' type tasks (build, tag, save, and publish) using the `docker` Gradle DSL task:
   - Follow the [README](plugin-integration-test/docker/README.md) for tracking integration test scenarios for the 
     `docker` task against the plugin aspects tested
   - Write integration test scenarios for the `docker` task at `plugin-integration-test/docker/scenario-<number>/`
- For 'docker compose' type tasks (using `composeUp` and `composeDown`) that test Docker images:
   - Follow the [README](plugin-integration-test/compose/README.md) for tracking integration test scenarios for the
     `dockerORch` task against the plugin aspects tested
  - Write integration test scenarios for the `dockerOrch` task at `plugin-integration-test/docker/scenario-<number>/`
- **Integration Test Source Set Convention**: The plugin automatically creates the `integrationTest` source set when
  the java or groovy plugin is present. Do NOT manually create source set boilerplate.
  - Put integration tests in `src/integrationTest/java` or `src/integrationTest/groovy`
  - Put compose files in `src/integrationTest/resources/compose/`
  - Use `integrationTestImplementation` for dependencies
  - The `integrationTest` task is automatically registered
  - Only add customization if needed (overrides convention)
- **Example minimal setup**:
  ```groovy
  dockerOrch {
      composeStacks {
          myTest { files.from('src/integrationTest/resources/compose/app.yml') }
      }
  }

  dependencies {
      integrationTestImplementation 'org.spockframework:spock-core:2.3'
  }
  // Convention provides source set, task, configurations automatically!
  ```

Write concise, clear comments to explain the demonstration code to a user.
- Explain non-obvious settings or configurations not fully shown, such as optional settings, default values, and
  mutually exclusive parameters
- Explain how to run the plugin's custom Gradle tasks

### Ensure Code Quality (`docs/project-standards/style/style.md`)
- **Keep files ≤ 500 lines, functions ≤ 30–40 lines, and max 4 parameters per function.**
- **Limit line length to 120 characters; use spaces (never tabs).**
- **Keep cyclomatic complexity ≤ 10 and nesting depth ≤ 3.**
- **Do not declare success if code exceeds these limits without refactoring.**
- **Correct ALL warnings resulting from the compilation, test, build, etc.**
- **Do NOT declare success if compiling, testing, building, etc. the code generates warnings.**

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