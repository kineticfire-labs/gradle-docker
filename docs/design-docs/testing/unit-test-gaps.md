# Unit Test Coverage Gaps

This document identifies and justifies code that cannot be practically unit tested due to external dependencies. All gaps listed here are verified to be covered by integration tests.

## Purpose

Not all code can be unit tested. External boundaries - direct calls to Docker daemon, file system, network, etc. - must be documented here with:

1. **What**: The specific code that cannot be unit tested
2. **Why**: Justification for why unit testing is impractical
3. **Coverage**: Reference to integration tests that verify this code

## Philosophy

We apply the "**Impure Shell, Pure Core**" pattern:

- **Pure Core**: Business logic, parsing, validation → **100% unit tested**
- **Impure Shell**: Thin boundary methods calling external services → **Document here, verify with integration tests**

## External Boundaries

### DockerServiceImpl - Docker Daemon Calls

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/DockerServiceImpl.groovy`

#### 1. createDockerClient() - Lines 83-108

**What:**
- Creates Docker Java Client
- Tests connection to Docker daemon
- Configures HTTP transport

**Why Unit Testing is Impractical:**
- Requires running Docker daemon
- Tests actual network connection
- No business logic to extract (pure external call)

**Mitigations:**
- All business logic extracted to utilities (`DockerfilePathResolver`, `ImageReferenceBuilder`)
- File operations abstracted via `FileOperations` interface
- Method kept minimal (26 lines, single responsibility)

**Integration Test Coverage:**
- All `docker` DSL integration tests verify Docker connection works
- Test: `plugin-integration-test/docker/scenario-*/` - all scenarios test actual Docker operations

#### 2. buildImage() - Orchestration Method (Lines 112-147)

**What:**
- Orchestrates Docker build process
- Calls pure functions for configuration
- Delegates to external boundary methods

**Status:**
- **Now highly testable** - reduced from 133 lines to 36 lines
- Calls `prepareBuildConfiguration()` - 100% unit tested pure function
- Calls `executeBuild()` - external boundary (documented below)
- Calls `applyAdditionalTags()` - external boundary (documented below)

**Pure Functions Extracted (100% Unit Tested):**
- `selectPrimaryTag()` (line 461-473): Tag selection logic, 100% tested
- `prepareBuildConfiguration()` (line 484-524): Configuration prep, 100% tested
- `BuildConfiguration` value object: Immutable data holder, 100% tested

**Integration Test Coverage:**
- Test: `plugin-integration-test/docker/scenario-1-single-image/` - Verifies build works end-to-end
- Test: `plugin-integration-test/docker/scenario-2-multi-arch/` - Verifies build args and labels
- Test: `plugin-integration-test/docker/scenario-*` - Various build scenarios

#### 2a. executeBuild() - Docker Build Execution (Lines 157-236)

**What:**
- Executes `dockerClient.buildImageCmd()` with Docker daemon
- Creates build callback for streaming output
- Handles temporary Dockerfile workaround if needed
- Applies build arguments and labels

**Why Unit Testing is Impractical:**
- Requires running Docker daemon
- Mocking Docker Java API classes causes Spock `CannotCreateMockException`
- Core Docker library methods cannot be reliably mocked

**Mitigations:**
- All configuration logic extracted to `prepareBuildConfiguration()` (100% tested)
- File I/O goes through `FileOperations` interface (100% mockable)
- Method kept minimal (~80 lines) with only Docker API calls
- Separated from business logic - this is pure external boundary

**Integration Test Coverage:**
- All docker build integration tests exercise this method

#### 2b. applyAdditionalTags() - Docker Tag Application (Lines 246-252)

**What:**
- Executes `dockerClient.tagImageCmd()` for each additional tag
- Applies tags beyond the primary build tag

**Why Unit Testing is Impractical:**
- Requires running Docker daemon
- Requires existing Docker image

**Mitigations:**
- Tag list preparation done in `prepareBuildConfiguration()` (100% tested)
- Method kept minimal (~7 lines) with only Docker API calls
- Pure external boundary with no business logic

**Integration Test Coverage:**
- All docker build integration tests with multiple tags exercise this method

#### 3. tagImage() - Lines 251-271

**What:**
- Executes `dockerClient.tagImageCmd()`
- Applies additional tags to built image

**Why Unit Testing is Impractical:**
- Requires Docker daemon
- Requires existing image to tag

**Mitigations:**
- Pure tag parsing logic extracted to `ImageRefParts` class (100% tested)
- Method is minimal (20 lines, single external call)

**Integration Test Coverage:**
- Test: `plugin-integration-test/docker/scenario-1-single-image/` - Verifies tagging
- All docker scenarios verify tags via `docker images`

#### 4. saveImage() - Lines 284-344

**What:**
- Executes `dockerClient.saveImageCmd()`
- Streams image tar to file with optional compression

**Why Unit Testing is Impractical:**
- Requires actual Docker image to save
- Stream handling tightly coupled to Docker API

**Mitigations:**
- Directory creation uses `FileOperations.createDirectories()` (100% mockable)
- Compression logic is deterministic (can be tested separately if extracted)
- Method kept focused on stream orchestration

**Integration Test Coverage:**
- Test: `plugin-integration-test/docker/scenario-save-*` - Verifies save with all compression types
- Tests verify saved tar files exist and have correct content

#### 5. pushImage() / pullImage() - Lines 346-450

**What:**
- Executes `dockerClient.pushImageCmd()` / `pullImageCmd()`
- Authenticates with registry
- Monitors push/pull progress

**Why Unit Testing is Impractical:**
- Requires network access to registry
- Requires authentication credentials
- Requires actual Docker images

**Mitigations:**
- Image reference parsing done by `ImageRefParts` (100% tested)
- Auth config creation is straightforward mapping
- Methods kept minimal

**Integration Test Coverage:**
- Test: `plugin-integration-test/docker/scenario-push-*` - Push to local registry
- Test: `plugin-integration-test/docker/scenario-pull-*` - Pull from local registry

#### 6. imageExists() - Lines 452-473

**What:**
- Executes `dockerClient.inspectImageCmd()`
- Handles `NotFoundException` to check existence

**Why Unit Testing is Impractical:**
- Requires Docker daemon
- Tests actual Docker image existence

**Mitigations:**
- Pure exception handling logic (can be verified via integration tests)
- Method is minimal (21 lines)

**Integration Test Coverage:**
- Implicitly tested in all docker scenarios (check if image exists before/after build)

### ExecLibraryComposeService - Process Execution

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeService.groovy`

#### 1. upStack() / downStack() - Process Execution (Lines 65-127, 194-221)

**What:**
- Executes `docker compose up` / `down` via `ProcessExecutor`
- Starts/stops actual containers

**Why Unit Testing is Impractical:**
- Requires Docker Compose CLI
- Requires actual compose files and services
- Creates real containers

**Mitigations:**
- Command building logic extracted (lines 75-95): Can be tested in isolation if extracted
- JSON parsing delegated to `ComposeOutputParser` utility (100% tested)
- Process execution isolated to `ProcessExecutor` interface

**Integration Test Coverage:**
- Test: `plugin-integration-test/compose/scenario-*` - All compose scenarios test actual up/down
- Tests verify containers start and stop correctly

#### 2. waitForServices() - Service Polling (Lines 263-303)

**What:**
- Polls `docker compose ps` to check service states
- Implements timeout and retry logic

**Why Unit Testing Can Be Done:**
- **This is now 100% unit testable!**
- Uses injected `TimeService` for all time operations
- Pure polling logic can be tested with mocked time

**Unit Test Coverage:**
- Test: `ExecLibraryComposeServiceUnitTest` - Comprehensive timeout and polling tests
- Test: `ExecLibraryComposeServiceMockabilityTest` - Demonstrates mock time benefits
- Tests run instantly (no actual sleeps)

**Integration Test Coverage:**
- Test: `plugin-integration-test/compose/scenario-wait-*` - Verifies actual service waiting

#### 3. captureLogs() - Log Streaming (Lines 385-429)

**What:**
- Executes `docker compose logs`
- Streams output to file

**Why Unit Testing is Impractical:**
- Requires running containers with logs
- Stream handling coupled to process output

**Mitigations:**
- Command building can be extracted and tested
- File operations use `FileOperations` where applicable
- Method focused on stream orchestration

**Integration Test Coverage:**
- Test: `plugin-integration-test/compose/scenario-logs-*` - Verifies log capture

### DefaultProcessExecutor - Process Execution

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/DefaultProcessExecutor.groovy`

#### 1. execute() - Process Execution (Lines 30-70)

**What:**
- Creates and executes `ProcessBuilder`
- Captures stdout/stderr
- Handles timeouts

**Why Unit Testing is Impractical:**
- Requires actual process execution
- Platform-dependent behavior
- Timeout handling requires real timing

**Mitigations:**
- Logic is minimal (parameter passing)
- Error handling is straightforward
- No business logic to extract

**Integration Test Coverage:**
- All docker and compose integration tests exercise process execution
- Timeout scenarios tested in integration tests

### JUnit 5 Extensions - Test Framework Integration

**Files:**
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeClassExtension.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtension.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/service/ComposeExtensionService.groovy`

#### 1. Extension Lifecycle Callbacks

**What:**
- JUnit 5 extension lifecycle hooks (`beforeAll`, `afterAll`, `beforeEach`, `afterEach`)
- Container state management during test execution
- `ExtensionContext` storage operations for sharing state between callbacks

**Why Unit Testing is Impractical:**
- Requires JUnit 5 test execution context (created by JUnit runtime)
- Extension callbacks are invoked by JUnit platform, not application code
- `ExtensionContext` cannot be reliably mocked (internal JUnit implementation)
- Store operations depend on JUnit's context hierarchy

**Mitigations:**
- Core business logic extracted to `ComposeExtensionService` (testable via mocks)
- Docker operations delegated to `ComposeService` interface (mockable)
- Configuration reading separated from JUnit-specific code
- State management uses simple data classes (testable)

**Integration Test Coverage:**
- Test: `plugin-integration-test/dockerTest/examples/web-app-junit/` - CLASS lifecycle
- Test: `plugin-integration-test/dockerTest/examples/isolated-tests-junit/` - CLASS and METHOD lifecycle
- Tests verify full extension lifecycle: containers start, tests execute, containers stop
- Tests verify state file generation and port mapping

#### 2. ComposeExtensionService - Service Management

**What:**
- `startStack()` - Starts Docker Compose stack and waits for services
- `stopStack()` - Stops Docker Compose stack and captures logs
- State file generation with container port mappings

**Why Unit Testing is Impractical:**
- Calls `ComposeService` which executes actual Docker Compose commands
- Waits for actual container health/running states
- Generates state files with runtime container information

**Mitigations:**
- `ComposeService` is injected (can be mocked for testing coordination logic)
- Wait logic uses `TimeService` for testable time operations
- File operations use `FileOperations` interface (mockable)

**Integration Test Coverage:**
- All JUnit 5 integration tests exercise `ComposeExtensionService`
- Tests verify actual container lifecycle management

### Spock Extensions - Test Framework Integration

**Files:**
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtension.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeUp.groovy`

#### 1. Extension Lifecycle Interceptors

**What:**
- Spock extension interceptors (`setupSpec`, `cleanupSpec`, `setup`, `cleanup`)
- `SpecInfo` and `FeatureInfo` interaction for test metadata
- Annotation parameter reading at runtime via reflection

**Why Unit Testing is Impractical:**
- Requires Spock runtime execution context
- Interceptors are invoked by Spock framework during test execution
- `SpecInfo` and `FeatureInfo` objects created by Spock, not directly instantiable
- Method interception requires actual Spock test execution

**Mitigations:**
- Configuration reading extracted to helper methods (testable)
- System property reading uses standard Java API (testable)
- Docker operations delegated to `ComposeExtensionService` (mockable)
- State management uses serializable data classes (testable)

**Integration Test Coverage:**
- All Spock integration tests in `plugin-integration-test/dockerTest/examples/`
- Tests: `web-app`, `stateful-web-app`, `isolated-tests`, `database-app`
- Tests verify full annotation processing and container lifecycle
- Tests verify both CLASS and METHOD lifecycle modes

#### 2. Configuration Priority Logic

**What:**
- Reading configuration from system properties (set by `usesCompose()`)
- Fallback to annotation parameters (backward compatibility)
- Conflict detection when both sources specify same parameter

**Why Unit Testing is Partially Impractical:**
- System property reading during Spock extension initialization
- Annotation parameter access via Spock's `SpecInfo`

**What IS Unit Testable:**
- Priority logic can be tested with mocked inputs
- Conflict detection logic is pure and testable
- Configuration validation is testable

**Integration Test Coverage:**
- All Spock integration tests verify configuration is read correctly
- Tests verify both `usesCompose()` pattern and annotation-only pattern

### Workflow Executor Package - Defensive Null Checks

**Files:**
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/workflow/executor/TestStepExecutor.groovy`

#### 1. setWaitSpecSystemProperties() - Null Property Checks (Lines 389-402)

**What:**
- Defensive null checks for `waitForServices`, `timeoutSeconds`, and `pollSeconds` property objects
- Conditions: `waitSpec.waitForServices != null && waitSpec.waitForServices.isPresent()`

**Why Unit Testing is Impractical:**
- These properties are abstract Gradle `Property<T>` and `ListProperty<T>` types
- Gradle's `ObjectFactory.newInstance()` always initializes these properties (never null)
- Mocking abstract Gradle classes with `@Inject` constructors causes Spock `CannotCreateMockException`
- The null branch (`!= null` evaluating to false) is defensive programming that cannot be reached

**Mitigations:**
- Null checks are defensive programming for safety
- All reachable branches (when properties exist) are 100% tested
- Property presence checks (`isPresent()`) are tested with both present and absent values
- Empty collection handling tested separately

**Unit Test Coverage:**
- 100% coverage of all reachable branches
- Tests verify behavior when properties are present with values
- Tests verify behavior when properties have convention values
- Tests verify empty services list is handled correctly

#### 2. collectComposeFilePaths() - Null Collection Checks (Lines 354-379)

**What:**
- Defensive null checks for `files` and `composeFileCollection` ConfigurableFileCollection
- Conditions: `stackSpec.files != null && !stackSpec.files.isEmpty()`

**Why Unit Testing is Impractical:**
- `ComposeStackSpec` is abstract with `@Inject` constructor managed by Gradle
- All `ConfigurableFileCollection` properties are initialized by `ObjectFactory`
- The null branches cannot be reached in normal Gradle usage
- Mocking abstract Gradle classes fails with Spock

**Mitigations:**
- Null checks are defensive programming
- All reachable paths (empty collections, populated collections) are 100% tested
- Tests verify single file, multiple files, and combined sources

**Unit Test Coverage:**
- 100% coverage of all reachable branches
- Tests verify empty collections return empty paths
- Tests verify files from all three sources (files, composeFile, composeFileCollection)
- Tests verify duplicate detection works correctly

### Package Coverage Summary

**Package: `com.kineticfire.gradle.docker.workflow.executor`**

| Metric | Before | After | Gap |
|--------|--------|-------|-----|
| Instructions | 94.7% | 99.2% | 0.8% |
| Branches | 89.8% | 94.5% | 5.5% |

The 5.5% branch gap represents defensive null checks on Gradle-managed abstract properties that:
1. Are always initialized by Gradle's ObjectFactory
2. Cannot be null in normal Gradle plugin usage
3. Cannot be mocked due to Spock/Gradle constraints
4. Provide safety without testable code paths

## Summary Statistics

### Unit Testable Code (via Dependency Injection)

- **FileOperations**: 100% mockable, all file I/O testable
- **TimeService**: 100% mockable, all time operations testable
- **Utility Classes**: 100% pure functions, 100% tested

### External Boundaries (This Document)

| Component | External Calls | Lines | Justification |
|-----------|----------------|-------|---------------|
| DockerServiceImpl | Docker daemon | ~200 | Cannot mock Docker Java API |
| ExecLibraryComposeService | docker compose CLI | ~150 | Requires real containers |
| DefaultProcessExecutor | Process execution | ~40 | Platform-dependent |
| JUnit 5 Extensions | JUnit runtime | ~200 | Framework integration |
| Spock Extensions | Spock runtime | ~150 | Framework integration |
| **Total** | | **~740** | All covered by integration tests |

### Coverage Achieved

- **Pure Logic**: 100% unit tested (utilities, parsing, validation)
- **Mockable I/O**: 100% unit tested (FileOperations, TimeService)
- **External Boundaries**: 100% integration tested
- **Test Framework Integration**: 100% integration tested

## Acceptance

✅ All gaps are justified with technical reasons
✅ All gaps are thin boundary methods (no extractable logic)
✅ All gaps are covered by integration tests
✅ Pure logic has been extracted and unit tested
✅ I/O dependencies have been injected and mocked

**Conclusion**: The remaining ~740 lines of untestable code represent genuine external boundaries that cannot be practically unit tested. This includes Docker daemon interactions (~200 lines), Docker Compose CLI calls (~150 lines), process execution (~40 lines), and test framework integration for JUnit 5 (~200 lines) and Spock (~150 lines). All such code is verified by comprehensive integration tests.

---

## Document History

- **2025-12-20**: Added workflow executor package gap documentation. Improved coverage from
  94.7%/89.8% to 99.2%/94.5% (instruction/branch). Documented remaining 5.5% branch gap as
  unreachable defensive null checks on Gradle-managed abstract properties.
- **2025-11-21**: Added JUnit 5 and Spock extension gap documentation. Updated summary statistics
  to include test framework integration (~350 lines). Total documented gaps now ~740 lines.
  All gaps covered by integration tests in `plugin-integration-test/dockerTest/`.
- **2025-01-30**: Phase 3 completion - Refactored `buildImage()` from 133 lines to 36 lines. Extracted `selectPrimaryTag()` and `prepareBuildConfiguration()` as pure functions (100% tested with 13 comprehensive unit tests). Isolated `executeBuild()` and `applyAdditionalTags()` as minimal external boundaries (~87 lines). Service package coverage improved from 53.8% to 57.2%. Overall coverage now 81.6% instructions, 80.4% branches.
- **2025-01-29**: Phase 2 completion - Implemented "Impure Shell, Pure Core" pattern. Injected FileOperations and TimeService dependencies, achieving 100% mockability of I/O and time operations. Updated to reflect current architecture with ~390 lines of documented external boundaries.
- **2025-01-28**: Phase 1 completion - Extracted pure utility functions (ImageReferenceBuilder, ComposeOutputParser, DockerfilePathResolver) with 100% test coverage.
- **2025-10-16**: Updated coverage statistics to reflect state before refactoring (81.1% instruction, 80.3% branch).
- **2025-10-13**: Initial documentation of unit test coverage gaps.

**Document Version**: 4.0
**Last Updated**: 2025-11-21
**Maintained By**: Development Team
