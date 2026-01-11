# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Implementation

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0100-dsl-user-description.md` - DSL syntax and user-facing behavior

## Purpose

Define the implementation plan to achieve the desired functionality.

This document should NOT include tests -- those are deferred to other documents.

The overview of the design is at `add-wait-for-log-0000-overview.md`.
The DSL / user description is at `add-wait-for-log-0100-dsl-user-description.md`.

## Checklist

### Pre-Implementation Verification

- [ ] Read and understand the existing `waitForHealthy` and `waitForRunning` implementations
- [ ] **Verify abstract MapProperty pattern limitation**: Test whether Gradle can handle
  `abstract MapProperty<String, List<String>>` with `@Inject`. Create a scratch test to verify:
  ```groovy
  // In a test file or scratch project, try:
  abstract class TestMapPropertySpec {
      @Inject TestMapPropertySpec() {}
      abstract MapProperty<String, List<String>> getTestMap()
  }
  // Then instantiate via: objectFactory.newInstance(TestMapPropertySpec)
  ```
  **Expected result**: This will likely fail due to nested generic type erasure - Gradle cannot infer
  the inner `List<String>` type. If it fails, the non-abstract class pattern with constructor injection
  (as designed in Section 1) is required. If it succeeds, consider refactoring `WaitForLogSpec` to use
  the abstract pattern for consistency with `WaitSpec`.

  **Verification command** (run unit test after creating test class):
  ```bash
  cd plugin && ./gradlew test --tests "*TestMapPropertySpec*"
  ```
- [ ] **Verify MapProperty<String, List<String>> configuration cache serialization**: The above test verifies
  *instantiation*, but configuration cache also requires *serialization/deserialization*. Create a minimal
  functional test project to verify:
  ```groovy
  // In a scratch functional test project (plugin/src/functionalTest/groovy):
  // 1. Create a task with abstract MapProperty<String, List<String>> input
  // 2. Run with --configuration-cache twice
  // 3. Verify second run reuses cached configuration

  // TestTask.groovy
  @UntrackedTask(because = "Test task")
  abstract class TestMapPropertyTask extends DefaultTask {
      @Input
      @Optional
      abstract MapProperty<String, List<String>> getTestMap()

      @TaskAction
      void run() {
          println "Test map: ${testMap.getOrElse([:])}"
      }
  }

  // build.gradle
  tasks.register('testMapProperty', TestMapPropertyTask) {
      testMap.set([
          'service1': ['pattern1', 'pattern2'],
          'service2': ['pattern3']
      ])
  }
  ```
  **Verification commands** (MUST run both to verify cache reuse):
  ```bash
  # First run - stores configuration cache
  ./gradlew testMapProperty --configuration-cache

  # Second run - MUST say "Reusing configuration cache" (not "Calculating task graph")
  ./gradlew testMapProperty --configuration-cache
  ```
  **If serialization fails**: See "MapProperty Serialization Fallback Strategies" section (Section 17) for
  alternative approaches. The recommended fallback is JSON String encoding.

  **IMPORTANT**: This verification MUST pass before proceeding with implementation. If it fails, implement
  the JSON String fallback (Option A in Section 17) instead of native `MapProperty<String, List<String>>`.
- [ ] **ACTION REQUIRED - Add Guava dependency to `plugin/build.gradle`**:
  Guava is defined in `libs.versions.toml` but **not currently declared** in `build.gradle`.
  This is a **required action**, not just verification. Add `implementation libs.guava` to the
  `dependencies` block after existing implementation entries.

  **Steps**:
  1. Open `plugin/build.gradle`
  2. Add `implementation libs.guava` in the `dependencies` block
  3. Verify the addition:
     ```bash
     rg "libs.guava" plugin/build.gradle
     ```
  4. Verify Guava is available on the compile classpath:
     ```bash
     cd plugin && ./gradlew dependencies --configuration compileClasspath | grep guava
     ```
- [ ] Confirm `ServiceLogger` interface methods (**verified**: `info()`, `debug()`, `warn()`, `error()` only - NO
      `lifecycle()`)
- [ ] Confirm `ComposeStackSpec.getName()` exists (**verified**: line 46-48)
- [ ] Verify `Lifecycle` enum exists in `TestIntegrationExtension` (required for METHOD validation in Section 12):
  ```bash
  rg "enum Lifecycle|Lifecycle\\.METHOD|Lifecycle\\.CLASS" plugin/src/main/groovy -C 2
  ```
- [ ] Confirm `TestIntegrationExtension.setComprehensiveSystemProperties()` signature (**verified**: line 179-217)
- [ ] Verify current execution order in `ComposeUpTask.performWaitIfConfigured()` (**verified**: currently
      `waitForHealthy` -> `waitForRunning`, will change to `waitForRunning` -> `waitForHealthy` -> `waitForLog`)
- [ ] Review existing wiring patterns in `GradleDockerPlugin`:
  ```bash
  rg "waitForHealthy|waitForRunning" plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy -C 3
  ```
- [ ] Verify `LogsConfig` constructor signature matches expected usage:
  ```bash
  rg "class LogsConfig" plugin/src/main/groovy -A 20
  ```
  Confirm constructor is: `LogsConfig(List<String> services, int tailLines, boolean follow, Path outputFile)`
- [ ] Verify Docker Compose version is v2.x+ (required for JSON format support):
  ```bash
  docker compose version
  ```
  Confirm version is 2.0.0 or higher (the JSON format may vary between major versions)
- [ ] Verify Docker Compose `ps --format json` output format includes expected fields:
  ```bash
  docker compose ps --format json
  ```
  Confirm output includes `State` and `ExitCode` fields (required for `isServiceRunning()` and `getServiceExitCode()`)
- [ ] Verify `JsonSlurper` is available (part of Groovy runtime, should be present)
- [ ] Verify `CompletableFuture` return type is used consistently in existing `ComposeService` methods
- [ ] Consider if `RECENT_LOG_LINES_FOR_ERROR` constant (10 lines) should be configurable or match an existing pattern
- [ ] Verify Docker Compose `ps --format json` includes `Service` field for service name extraction:
  ```bash
  docker compose ps --format json -a
  ```
  Confirm output includes `Service` field (required for `getComposeProjectServices()`)
- [ ] Verify `Duration` import exists in `ComposeUpTask.groovy` (**verified**: already present at line 42):
  ```bash
  rg "import java.time.Duration" plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy
  ```
- [ ] **Docker Compose v1 Compatibility**: This feature requires Docker Compose v2.x+ due to JSON format
  differences. Document this requirement in user-facing documentation. Docker Compose v1 users will see
  JSON parsing errors.
  **Deferred Enhancement**: Runtime detection and warning for Docker Compose v1 is deferred to a future
  enhancement. For now, users encountering JSON parsing errors should be directed to the documentation
  which clearly states the v2+ requirement. The current `isServiceRunning()` implementation logs JSON
  parsing failures at warn level, which provides some diagnostic information.
- [ ] Verify `composeService` is declared as `Property<ComposeService>` in `ComposeUpTask`:
  ```bash
  rg "Property.*ComposeService|composeService" plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy -C 3
  ```
- [ ] Verify `GradleException` import exists in `ComposeStackSpec.groovy` (**verified**: already present at line 19):
  ```bash
  rg "import org.gradle.api.GradleException" plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ComposeStackSpec.groovy
  ```
- [ ] Review `DockerComposeMethodExtension.waitForStackToBeReady()` implementation:
  ```bash
  rg "waitForStackToBeReady" plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtension.groovy -A 30
  ```
  **Note**: Current implementation has hardcoded HEALTHY status and ignores DSL settings. This will be fixed
  as part of full lifecycle support (Section 12.5).
- [ ] Review `DockerComposeClassExtension` for similar patterns:
  ```bash
  rg "waitForStackToBeReady" plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeClassExtension.groovy -A 30
  ```
- [ ] Verify `JUnitComposeService` implements `ComposeService` interface:
  ```bash
  rg "class JUnitComposeService|implements ComposeService" plugin/src/main/groovy/com/kineticfire/gradle/docker/junit -C 2
  ```
- [ ] Verify `SystemPropertyService` interface for reading system properties in test framework extensions:
  ```bash
  rg "interface SystemPropertyService|class.*SystemPropertyService" plugin/src/main/groovy -C 3
  ```
- [ ] **Verify existing system property format for waitForRunning/waitForHealthy**:
  The helper methods in Section 12.5 assume specific formats for existing system properties. Verify
  these formats match how `TestIntegrationExtension` currently serializes them:
  ```bash
  # Check how waitForRunning services are serialized
  rg "waitForRunning.*systemProperty|systemProperty.*waitForRunning" plugin/src/main/groovy -C 5

  # Check how waitForHealthy services are serialized
  rg "waitForHealthy.*systemProperty|systemProperty.*waitForHealthy" plugin/src/main/groovy -C 5
  ```
  **Expected format**: Services should be serialized as comma-separated values (e.g., `"app,db,redis"`).
  If a different format is used, update Section 12.5 helper methods to match.
- [ ] **Verify ProcessExecutor.execute() return type interface**:
  The implementation assumes `result.isSuccess()` and `result.stdout` exist on the process execution result.
  ```bash
  rg "interface ProcessExecutor|class ProcessExecutor" plugin/src/main/groovy -A 10
  rg "class.*Result|ProcessResult|ExecutionResult" plugin/src/main/groovy -A 10
  ```
  **Expected**: Result object with `isSuccess()` method and `stdout` property. If the interface differs,
  update Section 8 helper methods (`isServiceRunning`, `getServiceExitCode`, `getComposeProjectServices`).
- [ ] **Verify required imports exist in ExecLibraryComposeService**:
  ```bash
  # Check Duration import
  rg "import java.time.Duration" plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeService.groovy

  # Check existing HashSet/HashMap usage patterns
  rg "HashSet|HashMap" plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeService.groovy
  ```
  **Note**: Groovy auto-imports `java.util.*`, so explicit imports may not be needed. Verify compilation
  succeeds with the new code.

### Phase 0: Prerequisite Changes

- [ ] Modify `LogsConfig.groovy`:
  - [ ] Remove `Math.max(1, tailLines)` constraint (change to just `this.tailLines = tailLines`)
  - [ ] Add `hasLimitedTail()` helper method for readability (required for code clarity)
  - [ ] Update Javadoc to document that `0` (or any non-positive value) means "all logs"
- [ ] **Verify LogsConfig change impact** - search for all `LogsConfig` usages:
  ```bash
  rg "LogsConfig" plugin/src/main/groovy --type groovy
  rg "tailLines" plugin/src/main/groovy --type groovy
  ```
  Confirm all callers handle `tailLines <= 0` correctly (no code assumes `tailLines >= 1`)
- [ ] **Verify no other code paths depend on `tailLines > 0` pattern**:
  ```bash
  rg "tailLines.*>.*0|tailLines.*<.*1" plugin/src/main/groovy --type groovy
  ```
  Ensure all usages will work correctly with the new `hasLimitedTail()` method
- [ ] Modify `ExecLibraryComposeService.buildLogsCommand()`:
  - [ ] Replace `if (config.tailLines > 0)` with `if (config.hasLimitedTail())` for clarity
- [ ] Write unit tests for LogsConfig changes:
  - [ ] Test `tailLines = 0` results in `tailLines` being `0` (all logs)
  - [ ] Test `tailLines = -1` results in `tailLines` being `-1` (all logs)
  - [ ] Test `tailLines = 100` results in `tailLines` being `100` (existing behavior preserved)
  - [ ] Test `tailLines = 1` results in `tailLines` being `1` (minimum positive value)
  - [ ] Test `hasLimitedTail()` returns `false` for `tailLines = 0`
  - [ ] Test `hasLimitedTail()` returns `false` for `tailLines = -1`
  - [ ] Test `hasLimitedTail()` returns `true` for `tailLines = 1`
  - [ ] Test `hasLimitedTail()` returns `true` for `tailLines = 100`
- [ ] Run existing tests to verify no regressions (especially `LogsConfigTest` and `ExecLibraryComposeServiceTest`)

### Phase 1: Core Components

- [ ] Create `WaitForLogSpec.groovy` (Section 1)
  - [ ] **PREREQUISITE**: Complete Pre-Implementation Verification for MapProperty serialization
  - [ ] If serialization verification failed, implement JSON String fallback per Section 17 FIRST
  - [ ] Verify conventions are set correctly
  - [ ] Write unit tests
- [ ] Create `WaitForLogConfig.groovy` (Section 2)
  - [ ] Verify immutability
  - [ ] Write unit tests
  - [ ] Test `getTotalWaitAttempts()` edge cases:
    - [ ] Test `timeout < pollInterval` (e.g., timeout=1s, poll=2s) returns 1
    - [ ] Test `timeout == pollInterval` returns 1
    - [ ] Test ceiling division works (e.g., timeout=61s, poll=2s returns 31, not 30)
    - [ ] Test large values don't cause integer overflow
- [ ] Create `WaitForLogResult.groovy` (Section 3)
  - [ ] Write unit tests
- [ ] Create `LogPatternMatcher.groovy` (Section 4)
  - [ ] Test all pure functions
  - [ ] Achieve 100% branch coverage
- [ ] Create `WaitForLogConfigBuilder.groovy` (Section 5)
  - [ ] Test validation logic
  - [ ] Test error messages
  - [ ] Test orphaned reject patterns warning is logged
  - [ ] Test `pollSeconds > timeoutSeconds` warning is logged

### Phase 2: Integration

- [ ] Modify `ComposeStackSpec.groovy` (Section 6)
  - [ ] Add `waitForLog` property
  - [ ] Add DSL methods (Closure and Action variants)
  - [ ] Add validation method
  - [ ] Write unit tests
- [ ] Modify `ComposeService.groovy` (Section 7)
  - [ ] Add `waitForLogPatterns()` method signature
- [ ] Modify `ExecLibraryComposeService.groovy` (Section 8)
  - [ ] Implement `waitForLogPatterns()`
  - [ ] Add all helper methods
  - [ ] Write unit tests with mocked dependencies
- [ ] Modify `ComposeServiceException.groovy` (Section 9)
  - [ ] Add new error types
  - [ ] Write unit tests
- [ ] Modify `ComposeUpTask.groovy` (Section 10)
  - [ ] Add flattened input properties
  - [ ] Update `performWaitIfConfigured()` execution order
  - [ ] Add `performWaitForLog()` method
  - [ ] Write unit tests
- [ ] Modify `GradleDockerPlugin.groovy` (Section 11)
  - [ ] Add property wiring for waitForLog
  - [ ] Write unit tests
- [ ] Modify `TestIntegrationExtension.groovy` (Section 12)
  - [ ] Add system property propagation for `waitForLog` (both CLASS and METHOD lifecycles)
  - [ ] Write unit tests for system property propagation
- [ ] Modify `DockerComposeMethodExtension.groovy` (Section 12.5)
  - [ ] Update `waitForStackToBeReady()` to read DSL system properties
  - [ ] Add `performWaitForRunning()` method
  - [ ] Add `performWaitForHealthy()` method
  - [ ] Add `performWaitForLog()` method (full lifecycle support)
  - [ ] Add helper methods (parseIntProperty, parseBooleanProperty, parseJsonMapProperty)
  - [ ] Write unit tests
- [ ] Modify `DockerComposeClassExtension.groovy` (Section 12.5)
  - [ ] Update `waitForStackToBeReady()` to read DSL system properties
  - [ ] Add `performWaitForLog()` method call (executes before test class runs)
  - [ ] Write unit tests
- [ ] Modify `JUnitComposeService.groovy` (Section 12.5.5)
  - [ ] Add `waitForLogPatterns()` delegation method (if not using @Delegate pattern)
  - [ ] Write unit tests

### Phase 3: Functional Tests

- [ ] Add functional tests for `waitForLog` DSL configuration
- [ ] Add functional tests for validation error messages
- [ ] Add functional tests for property wiring
- [ ] Add functional test for `waitForLog` with `Lifecycle.CLASS`
- [ ] Add functional test for `waitForLog` with `Lifecycle.METHOD`
- [ ] Verify all functional tests pass

### Phase 4: Configuration Cache Verification

- [ ] **MapProperty<String, List<String>> serialization verification** (CRITICAL):
  - [ ] Create functional test with `waitForLog` DSL containing multiple services
  - [ ] Run with `--configuration-cache` flag (first run)
  - [ ] Run with `--configuration-cache` flag again - **MUST say "Reusing configuration cache"**
  - [ ] If second run says "Calculating task graph", serialization has failed - implement fallback
- [ ] **Pattern content verification**:
  - [ ] Test with simple patterns: `['Started Application']`
  - [ ] Test with regex special characters: `['\\[INFO\\].*started', 'port:\\s+\\d+']`
  - [ ] Test with escape sequences: `['message: \\"ready\\"', 'path\\\\to\\\\file']`
  - [ ] Test with case-insensitive flag patterns: `['(?i)ready', '(?i)started']`
  - [ ] Verify patterns match correctly after cache restore (values not corrupted)
- [ ] **Multi-service verification**:
  - [ ] Test with 3+ services in `waitForServices` map
  - [ ] Test with services having different numbers of patterns (1, 3, 5 patterns)
  - [ ] Test with `rejectPatterns` populated for some but not all services
- [ ] **Edge case verification**:
  - [ ] Test with empty `rejectPatterns` (should serialize as empty map `{}`)
  - [ ] Test with very long pattern strings (500+ characters)
  - [ ] Test with Unicode characters in patterns
- [ ] **If MapProperty serialization fails**:
  - [ ] Implement JSON String fallback per Section 17
  - [ ] Re-run all above tests with fallback implementation
  - [ ] Document the limitation in release notes

### Phase 5: Integration Tests

- [ ] Create integration test scenario for `waitForLog` with CLASS lifecycle
  - [ ] Test with real Docker containers
  - [ ] Test timeout behavior
  - [ ] Test reject pattern behavior
  - [ ] Test verbose logging
  - [ ] Test progress interval logging
  - [ ] Verify no lingering containers
- [ ] Create integration test scenario for `waitForLog` with METHOD lifecycle
  - [ ] Test with real Docker containers
  - [ ] Test timeout behavior
  - [ ] Test reject pattern behavior
  - [ ] Test verbose logging
  - [ ] Test progress interval logging
  - [ ] Verify no lingering containers
- [ ] Create integration test scenario for `waitForRunning` with METHOD lifecycle
  - [ ] Verify DSL settings are honored (not hardcoded)
- [ ] Create integration test scenario for `waitForHealthy` with METHOD lifecycle
  - [ ] Verify DSL settings are honored (timeout, poll interval)

### Phase 6: Documentation

- [ ] Update `docs/usage/usage-docker-orch.md`
- [ ] Update `CHANGELOG.md` with:
  - [ ] New `waitForLog` feature description with full lifecycle support (CLASS and METHOD)
  - [ ] Execution order is now `waitForRunning` -> `waitForHealthy` -> `waitForLog`
- [ ] Update `README.md` feature list

### Final Verification

- [ ] All unit tests pass (100% coverage where possible)
- [ ] All functional tests pass
- [ ] All integration tests pass
- [ ] Configuration cache works correctly
- [ ] `docker ps -a` shows no lingering containers
- [ ] Documentation is complete and accurate

---

## Implementation

This section describes the implementation plan for the `waitForLog` feature, following the existing patterns
established in the codebase and ensuring Gradle 9/10 configuration cache compatibility.

### Pre-Implementation Requirements

#### LogsConfig Modification Required

The existing `LogsConfig` class enforces `tailLines = Math.max(1, tailLines)`, which means passing `0` for "all logs"
actually returns only the last 1 line. **This must be fixed before implementing `waitForLogPatterns()`**.

**Current behavior** (`model/LogsConfig.groovy`):
```groovy
this.tailLines = Math.max(1, tailLines)  // 0 becomes 1!
```

**Simplified Fix**: The existing `buildLogsCommand()` in `ExecLibraryComposeService.groovy` (lines 373-375) already
checks `if (config.tailLines > 0)` before adding the `--tail` flag. Therefore, **only the `LogsConfig` constructor
needs to change** - just remove the `Math.max(1, tailLines)` constraint:

**Required fix** - Minimal change to `LogsConfig`:

Modify `plugin/src/main/groovy/com/kineticfire/gradle/docker/model/LogsConfig.groovy` (line 32 only):

```groovy
// BEFORE:
this.tailLines = Math.max(1, tailLines)

// AFTER (simply remove the constraint):
this.tailLines = tailLines
```

**Semantic values for `tailLines`:**
- `tailLines > 0`: Fetch only the last N lines (adds `--tail N` flag)
- `tailLines <= 0`: Fetch all logs (no `--tail` flag added)
- **Convention**: Use `0` to indicate "all logs" for clarity

**Required enhancement** - Add a helper method for clarity:

```groovy
/**
 * Returns true if tailLines should be applied (positive value).
 * When false, the --tail flag should be omitted to fetch all logs.
 */
boolean hasLimitedTail() {
    return tailLines > 0
}
```

Update `buildLogsCommand()` in `ExecLibraryComposeService.groovy` to use `hasLimitedTail()` for improved readability:

```groovy
// BEFORE:
if (config.tailLines > 0) {

// AFTER:
if (config.hasLimitedTail()) {
```

**Note**: The existing `buildLogsCommand()` already handles the condition correctly, but using `hasLimitedTail()`
improves code readability and makes the intent clearer.

**Implementation note**: This prerequisite change should be implemented and tested first, as `waitForLogPatterns()`
depends on the ability to fetch all container logs.

#### Dependency Verification Required

Before implementation, verify the following dependencies and interfaces exist in the codebase:

1. **Guava dependency for `@VisibleForTesting`**: Guava is defined in `libs.versions.toml` (line 28, 33) but may not
   be explicitly declared in `build.gradle`. It is available as a transitive dependency from docker-java.

   **Verification step**:
   ```bash
   rg "libs.guava" plugin/build.gradle
   ```

   If not found, add to `plugin/build.gradle` in the `dependencies` block (after existing `implementation` entries):
   ```groovy
   implementation libs.guava
   ```

   **Note**: The existing codebase already uses `@VisibleForTesting` in `ExecLibraryComposeService.groovy` and
   `DockerServiceImpl.groovy`, so Guava must be available. Adding an explicit dependency ensures stability if
   docker-java's transitive dependencies change.

2. **ServiceLogger interface**: The existing `ServiceLogger` interface has **only** these methods:
   - `info(String message)` - single String parameter only
   - `debug(String message)` - single String parameter only
   - `warn(String message)` - single String parameter only
   - `error(String message)` and `error(String message, Throwable throwable)`

   **IMPORTANT**: There is NO `lifecycle()` method and NO parameterized logging (varargs) support.
   All logging in this implementation uses `info()` with Groovy string interpolation:
   ```groovy
   // CORRECT - use string interpolation
   serviceLogger.info("[waitForLog] Waiting for ${count} services...")

   // WRONG - no parameterized logging support
   // serviceLogger.info("[waitForLog] Waiting for {} services...", count)
   ```

3. **ComposeStackSpec.getName()**: Verified - `ComposeStackSpec` has a `getName()` method that returns the stack
   name (line 46-48 in the existing implementation).

4. **DefaultServiceLogger**: Verify that `DefaultServiceLogger` implements all `ServiceLogger` methods. The
   implementation should delegate to SLF4J or Gradle's logger.

### Validation Strategy: Two-Phase Approach

This implementation uses a **two-phase validation strategy** to balance early error detection with configuration
cache compatibility:

**Phase 1 - Configuration Time (DSL block methods):**
- Validates that `waitForServices` is present and non-empty
- Validates that each service has at least one pattern string
- Provides immediate feedback during `./gradlew tasks` or project configuration
- Does NOT compile regex patterns (avoids potential Pattern serialization issues)

**Phase 2 - Execution Time (task action via `WaitForLogConfigBuilder`):**
- Compiles regex patterns (validates syntax)
- Re-validates structure (defensive, in case properties were modified)
- Throws `GradleException` with detailed error messages and examples

This approach ensures users get fast feedback on structural errors while deferring regex compilation
to execution time for maximum configuration cache compatibility.

### Component Overview

The implementation adds the following components:

| Component | Type | Purpose |
|-----------|------|---------|
| `WaitForLogSpec` | Spec class | DSL configuration for log-based readiness |
| `WaitForLogConfig` | Model class | Immutable runtime configuration |
| `LogPatternMatcher` | Pure utility | Regex pattern matching logic (includes `RejectCheckResult` inner class) |
| `WaitForLogResult` | Model class | Per-service pattern match results |
| `WaitForLogConfigBuilder` | Utility | Config builder with validation |
| `ComposeService` extension | Interface | Add `waitForLogPatterns()` method |
| `ComposeUpTask` extension | Task | Add `waitForLog*` properties |
| `ComposeStackSpec` extension | Spec | Add `waitForLog` DSL block |
| `TestIntegrationExtension` extension | Extension | Propagate `waitForLog` to test framework |

### Existing Dependencies Verification

The following dependencies **already exist** in the codebase and will be used by this implementation:

**In `ExecLibraryComposeService.groovy`:**
- `timeService: TimeService` - Provides `currentTimeMillis()` and `sleep()` for testable time operations
- `processExecutor: ProcessExecutor` - Executes shell commands
- `serviceLogger: ServiceLogger` - Logging interface for output
- `getComposeCommand()` - Returns compose command prefix (e.g., `["docker", "compose"]`)

**In `ComposeService.groovy` interface:**
- `captureLogs(String projectName, LogsConfig config)` - Already exists for capturing container logs

**In `model/LogsConfig.groovy`:**
- Existing class with properties: `services`, `tailLines`, `follow`, `outputFile`
- **Note:** The existing `LogsConfig` uses `follow` (boolean) instead of `timestamps` and `since`. The
  implementation will use the existing class signature.

**In `ComposeServiceException.groovy`:**
- Existing constructors support the 3-argument pattern: `ComposeServiceException(ErrorType, String message, String suggestion)`
- The `ErrorType` enum will be extended with new values

### Gradle 9/10 Configuration Cache Compatibility

All components follow the patterns established in `docs/design-docs/gradle-9-and-10-compatibility.md`:

1. **Provider API**: All configuration uses `Property<T>`, `MapProperty<K,V>` for lazy evaluation
2. **No Project References**: Spec classes use `@Inject` with `ObjectFactory` only
3. **Validation Timing**: Early validation in DSL methods (structural), full validation in `@TaskAction` (regex)
4. **Task Cacheability**: Tasks with external side effects use `@UntrackedTask` (already applied to `ComposeUpTask`)
5. **Service Injection**: Use `@Inject` abstract getters for services
6. **Flattened Properties**: Task inputs use individual `@Input` properties, not nested spec references

### 1. WaitForLogSpec Class

The spec class provides the DSL configuration interface. This is a non-abstract class that uses
`ObjectFactory` to create properties explicitly, which allows conventions to be set in the
constructor. Create at `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/WaitForLogSpec.groovy`:

> **Design Note - Non-Abstract Class Pattern**: Unlike `WaitSpec` which uses abstract properties (e.g.,
> `abstract ListProperty<String> getWaitForServices()`), `WaitForLogSpec` uses explicit field assignment because
> `MapProperty<String, List<String>>` requires manual creation via `ObjectFactory.mapProperty()` due to nested generic
> type erasure. Gradle's managed property mechanism cannot automatically instantiate properties with nested generics.
> The `@SuppressWarnings('unchecked')` annotation is necessary because `mapProperty(String, List)` loses the inner
> `<String>` type information at runtime.
>
> **IMPORTANT - Verification Required**: Before implementing this class, complete BOTH verification checklist items
> in the Pre-Implementation Verification section:
> 1. "Verify abstract MapProperty pattern limitation" - Tests instantiation
> 2. "Verify MapProperty<String, List<String>> configuration cache serialization" - Tests serialization
>
> - **If instantiation verification fails** (expected): Use the non-abstract class pattern shown below.
> - **If instantiation verification succeeds**: Refactor `WaitForLogSpec` to use the abstract pattern for consistency
>   with `WaitSpec`. Change to `abstract class WaitForLogSpec` with abstract property getters and an empty
>   `@Inject` constructor.
> - **If configuration cache serialization fails**: Implement the JSON String fallback documented in Section 17
>   (MapProperty Serialization Fallback Strategies). This is a blocking issue that must be resolved before
>   proceeding with full implementation.

```groovy
package com.kineticfire.gradle.docker.spec

// Complete imports for this file
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * Specification for waiting on Docker service log patterns.
 *
 * <p>This class configures log-based readiness checks where each service maps to one or more
 * regex patterns that must appear in its logs before the service is considered ready.</p>
 *
 * <p>Note: {@code waitForServices} has no default convention and must be explicitly set.
 * Validation is performed in {@link ComposeStackSpec} when configuring the waitForLog block.</p>
 *
 * <p>Configuration cache compatibility: This class uses only Property API types and is
 * instantiated via ObjectFactory.newInstance(). No Project references are held.</p>
 */
class WaitForLogSpec {

    private final MapProperty<String, List<String>> waitForServices
    private final MapProperty<String, List<String>> rejectPatterns
    private final Property<Integer> timeoutSeconds
    private final Property<Integer> pollSeconds
    private final Property<Boolean> caseInsensitive
    private final Property<Boolean> verbose
    private final Property<Integer> progressIntervalSeconds

    @Inject
    @SuppressWarnings('unchecked')  // Required due to JVM type erasure with nested generics (List<String>)
    WaitForLogSpec(ObjectFactory objects) {
        // Create properties using ObjectFactory
        // Note: The raw List type is unavoidable due to Gradle API limitations.
        // The actual values will be List<String> at runtime.
        this.waitForServices = objects.mapProperty(String, List)
        this.rejectPatterns = objects.mapProperty(String, List)
        this.timeoutSeconds = objects.property(Integer)
        this.pollSeconds = objects.property(Integer)
        this.caseInsensitive = objects.property(Boolean)
        this.verbose = objects.property(Boolean)
        this.progressIntervalSeconds = objects.property(Integer)

        // Set conventions for optional properties
        // Note: waitForServices has no convention - must be explicitly set
        this.timeoutSeconds.convention(60)
        this.pollSeconds.convention(2)
        this.caseInsensitive.convention(false)
        this.verbose.convention(false)
        this.progressIntervalSeconds.convention(0)
        // Set explicit empty map convention for rejectPatterns
        // (MapProperty has no value unless explicitly set or given a convention)
        this.rejectPatterns.convention([:])
    }

    /**
     * Map of service names to list of regex patterns. ALL patterns in the list must match
     * (in any order) before the service is considered ready.
     *
     * <p>Required: Must be explicitly set with at least one service.</p>
     */
    MapProperty<String, List<String>> getWaitForServices() {
        return waitForServices
    }

    /**
     * Map of service names to list of reject patterns. If ANY pattern matches,
     * the wait fails immediately.
     *
     * <p>Optional: Services not in this map have no reject patterns.</p>
     */
    MapProperty<String, List<String>> getRejectPatterns() {
        return rejectPatterns
    }

    /**
     * Maximum seconds to wait for all patterns to match before failing.
     *
     * <p>Default: 60 seconds</p>
     */
    Property<Integer> getTimeoutSeconds() {
        return timeoutSeconds
    }

    /**
     * Poll interval in seconds for checking container logs.
     *
     * <p>Default: 2 seconds</p>
     */
    Property<Integer> getPollSeconds() {
        return pollSeconds
    }

    /**
     * When true, all pattern matching is case-insensitive.
     *
     * <p>Default: false</p>
     */
    Property<Boolean> getCaseInsensitive() {
        return caseInsensitive
    }

    /**
     * When true, logs detailed progress during polling.
     *
     * <p>Default: false</p>
     */
    Property<Boolean> getVerbose() {
        return verbose
    }

    /**
     * When > 0, logs a summary of pattern match status at this interval (in seconds).
     * Set to 0 to disable periodic progress logging.
     *
     * <p>Default: 0 (disabled)</p>
     */
    Property<Integer> getProgressIntervalSeconds() {
        return progressIntervalSeconds
    }
}
```

### 2. WaitForLogConfig Model Class

The model class provides an immutable runtime configuration. Create at
`plugin/src/main/groovy/com/kineticfire/gradle/docker/model/WaitForLogConfig.groovy`:

> **Naming Note**: `WaitForLogConfig` is intentionally named differently from the existing `WaitConfig` (used by
> `waitForServices()` for running/healthy checks) to reflect the more complex configuration structure. While `WaitConfig`
> holds a simple list of service names, `WaitForLogConfig` holds per-service pattern maps, reject patterns, and
> additional options like `caseInsensitive` and `verbose`. The distinct names prevent confusion and make clear these
> are different configuration types.

**Configuration Cache Note**: This class stores `java.util.regex.Pattern` objects which implement `Serializable`.
Pattern serialization is supported by the JVM, so this class is configuration-cache compatible.

> **Fallback Implementation (if needed)**: If serialization issues arise during Phase 4 configuration cache testing,
> replace `Map<String, List<Pattern>>` fields with `Map<String, List<String>>` (pattern strings) and add:
> ```groovy
> @Transient
> private Map<String, List<Pattern>> compiledPatternsCache
>
> Map<String, List<Pattern>> getCompiledServicePatterns() {
>     if (compiledPatternsCache == null) {
>         compiledPatternsCache = LogPatternMatcher.compilePatterns(servicePatternStrings, caseInsensitive)
>     }
>     return compiledPatternsCache
> }
> ```
> This approach defers compilation to first access and avoids Pattern serialization entirely.
> **Note**: This fallback is unlikely to be needed as Pattern serialization has been stable since Java 1.4.

```groovy
package com.kineticfire.gradle.docker.model

// Complete imports for this file
import java.time.Duration
import java.util.regex.Pattern
import java.util.Collections
import java.util.Objects
import java.util.Map
import java.util.List
import java.util.ArrayList

/**
 * Immutable configuration for waiting for log patterns.
 *
 * <p>This class is created from WaitForLogSpec during task execution and contains
 * pre-compiled regex patterns for efficient matching.</p>
 */
class WaitForLogConfig {
    final String projectName
    final Map<String, List<Pattern>> servicePatterns
    final Map<String, List<Pattern>> rejectPatterns
    final Duration timeout
    final Duration pollInterval
    final boolean caseInsensitive
    final boolean verbose
    final Duration progressInterval

    WaitForLogConfig(
            String projectName,
            Map<String, List<Pattern>> servicePatterns,
            Map<String, List<Pattern>> rejectPatterns,
            Duration timeout,
            Duration pollInterval,
            boolean caseInsensitive,
            boolean verbose,
            Duration progressInterval) {
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null")
        this.servicePatterns = Collections.unmodifiableMap(
            Objects.requireNonNull(servicePatterns, "Service patterns cannot be null")
        )
        this.rejectPatterns = Collections.unmodifiableMap(rejectPatterns ?: [:])
        this.timeout = timeout ?: Duration.ofSeconds(60)
        this.pollInterval = pollInterval ?: Duration.ofSeconds(2)
        this.caseInsensitive = caseInsensitive
        this.verbose = verbose
        this.progressInterval = progressInterval ?: Duration.ZERO
    }

    /**
     * Calculate total wait attempts based on timeout and poll interval.
     *
     * <p>Uses ceiling division to ensure the timeout period is fully covered.
     * For example, with timeout=61s and poll=2s, this returns 31 attempts
     * (not 30 from truncating integer division).</p>
     */
    int getTotalWaitAttempts() {
        return Math.max(1, (int) Math.ceil(timeout.toSeconds() / (double) pollInterval.toSeconds()))
    }

    /**
     * Get list of all services being monitored.
     */
    List<String> getServices() {
        return new ArrayList<>(servicePatterns.keySet())
    }

    /**
     * Check if progress interval logging is enabled.
     */
    boolean hasProgressInterval() {
        return progressInterval != null && progressInterval.toSeconds() > 0
    }

    @Override
    String toString() {
        return "WaitForLogConfig{projectName='${projectName}', services=${services}, " +
               "timeout=${timeout}, pollInterval=${pollInterval}, caseInsensitive=${caseInsensitive}}"
    }
}
```

### 3. WaitForLogResult Model Class

Create at `plugin/src/main/groovy/com/kineticfire/gradle/docker/model/WaitForLogResult.groovy`:

```groovy
package com.kineticfire.gradle.docker.model

// Complete imports for this file
import java.util.Collections
import java.util.List

/**
 * Result of a wait-for-log operation for a single service.
 *
 * <p>Tracks which patterns have matched and when, enabling detailed progress reporting
 * and timeout diagnostics.</p>
 */
class WaitForLogResult {

    /**
     * Status of an individual pattern match.
     */
    static class PatternMatch {
        final String pattern
        final boolean matched
        final Long matchedAtSeconds  // null if not matched

        PatternMatch(String pattern, boolean matched, Long matchedAtSeconds) {
            this.pattern = pattern
            this.matched = matched
            this.matchedAtSeconds = matchedAtSeconds
        }
    }

    final String serviceName
    final List<PatternMatch> patternMatches
    final boolean ready
    final boolean rejected
    final String rejectPattern  // null if not rejected
    final String rejectLogLine  // null if not rejected

    WaitForLogResult(String serviceName, List<PatternMatch> patternMatches, boolean ready) {
        this.serviceName = serviceName
        this.patternMatches = Collections.unmodifiableList(patternMatches)
        this.ready = ready
        this.rejected = false
        this.rejectPattern = null
        this.rejectLogLine = null
    }

    WaitForLogResult(String serviceName, List<PatternMatch> patternMatches,
                     String rejectPattern, String rejectLogLine) {
        this.serviceName = serviceName
        this.patternMatches = Collections.unmodifiableList(patternMatches)
        this.ready = false
        this.rejected = true
        this.rejectPattern = rejectPattern
        this.rejectLogLine = rejectLogLine
    }

    int getMatchedCount() {
        return patternMatches.count { it.matched }
    }

    int getTotalPatterns() {
        return patternMatches.size()
    }
}
```

### 4. LogPatternMatcher Utility Class

A pure utility class for pattern matching logic. Create at
`plugin/src/main/groovy/com/kineticfire/gradle/docker/util/LogPatternMatcher.groovy`:

**Note on `@VisibleForTesting`**: This annotation requires the Guava library. If Guava is not a project dependency,
either add it to `plugin/build.gradle` or remove the `@VisibleForTesting` annotations entirely. The annotations are
documentation-only and do not affect runtime behavior.

```groovy
package com.kineticfire.gradle.docker.util

// NOTE: If Guava is not available, remove this import and all @VisibleForTesting annotations
import com.google.common.annotations.VisibleForTesting

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import java.util.Set
import java.util.Map
import java.util.List

/**
 * Pure utility class for log pattern matching.
 *
 * <p>This class contains no state and all methods are pure functions, making it
 * 100% unit testable without mocks.</p>
 */
class LogPatternMatcher {

    /**
     * Result of checking reject patterns against log lines.
     *
     * <p>This simple data class replaces Tuple2 for type safety and clarity.</p>
     */
    static class RejectCheckResult {
        final String patternString
        final String matchingLogLine

        RejectCheckResult(String patternString, String matchingLogLine) {
            this.patternString = patternString
            this.matchingLogLine = matchingLogLine
        }
    }

    private LogPatternMatcher() {
        // Utility class - prevent instantiation
    }

    /**
     * Compile a regex pattern string with optional case-insensitivity.
     *
     * @param patternString The regex pattern
     * @param caseInsensitive Whether to compile with CASE_INSENSITIVE flag
     * @return Compiled Pattern
     * @throws PatternSyntaxException if pattern is invalid
     */
    @VisibleForTesting
    static Pattern compilePattern(String patternString, boolean caseInsensitive) {
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0
        return Pattern.compile(patternString, flags)
    }

    /**
     * Compile all patterns for all services.
     *
     * @param servicePatterns Map of service -> pattern strings
     * @param caseInsensitive Whether to use case-insensitive matching
     * @return Map of service -> compiled patterns
     * @throws IllegalArgumentException if any pattern is invalid (wraps PatternSyntaxException)
     */
    static Map<String, List<Pattern>> compilePatterns(
            Map<String, List<String>> servicePatterns,
            boolean caseInsensitive) {
        def result = [:]
        servicePatterns.each { serviceName, patterns ->
            try {
                result[serviceName] = patterns.collect { patternString ->
                    compilePattern(patternString, caseInsensitive)
                }
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                    "Invalid regex pattern for service '${serviceName}': ${e.pattern}\n" +
                    "Error: ${e.description}\n" +
                    "Hint: Escape special regex characters with \\\\ (e.g., \\\\[ to match literal [)",
                    e
                )
            }
        }
        return result
    }

    /**
     * Check if a pattern matches any line in the log output.
     *
     * @param pattern Compiled regex pattern
     * @param logLines List of log lines to search
     * @return true if pattern matches any line
     */
    @VisibleForTesting
    static boolean matchesAnyLine(Pattern pattern, List<String> logLines) {
        return logLines.any { line ->
            pattern.matcher(line).find()
        }
    }

    /**
     * Find the first log line matching a pattern.
     *
     * @param pattern Compiled regex pattern
     * @param logLines List of log lines to search
     * @return The matching log line, or null if no match
     */
    @VisibleForTesting
    static String findMatchingLine(Pattern pattern, List<String> logLines) {
        return logLines.find { line ->
            pattern.matcher(line).find()
        }
    }

    /**
     * Check for reject pattern matches in log output.
     *
     * @param rejectPatterns List of reject patterns to check
     * @param logLines Log lines to search
     * @return RejectCheckResult with matched pattern and log line, or null if no match
     */
    static RejectCheckResult checkRejectPatterns(
            List<Pattern> rejectPatterns,
            List<String> logLines) {
        for (Pattern pattern : rejectPatterns) {
            def matchingLine = findMatchingLine(pattern, logLines)
            if (matchingLine != null) {
                return new RejectCheckResult(pattern.pattern(), matchingLine)
            }
        }
        return null
    }

    /**
     * Update match state for a service based on new log output.
     *
     * <p>This method modifies the provided collections in place for efficiency.</p>
     *
     * @param patterns Patterns to match (must not be null or empty)
     * @param matchedPatterns Set of already-matched pattern indices (modified in place, must not be null)
     * @param logLines New log lines to check (must not be null, may be empty)
     * @param elapsedSeconds Current elapsed time for recording match time
     * @param matchTimes Map of pattern index to match time (modified in place, must not be null)
     * @return Number of newly matched patterns
     * @throws NullPointerException if any required parameter is null
     */
    static int updateMatches(
            List<Pattern> patterns,
            Set<Integer> matchedPatterns,
            List<String> logLines,
            long elapsedSeconds,
            Map<Integer, Long> matchTimes) {
        // Null safety - fail fast with clear error messages
        if (patterns == null) {
            throw new NullPointerException("patterns cannot be null")
        }
        if (matchedPatterns == null) {
            throw new NullPointerException("matchedPatterns cannot be null")
        }
        if (logLines == null) {
            throw new NullPointerException("logLines cannot be null")
        }
        if (matchTimes == null) {
            throw new NullPointerException("matchTimes cannot be null")
        }

        int newMatches = 0
        patterns.eachWithIndex { pattern, index ->
            if (!matchedPatterns.contains(index) && matchesAnyLine(pattern, logLines)) {
                matchedPatterns.add(index)
                matchTimes[index] = elapsedSeconds
                newMatches++
            }
        }
        return newMatches
    }
}
```

### 5. WaitForLogConfigBuilder Utility Class

A builder to construct WaitForLogConfig from WaitForLogSpec with validation. Create at
`plugin/src/main/groovy/com/kineticfire/gradle/docker/util/WaitForLogConfigBuilder.groovy`:

```groovy
package com.kineticfire.gradle.docker.util

import com.kineticfire.gradle.docker.model.WaitForLogConfig
import org.gradle.api.GradleException

import java.time.Duration
import java.util.regex.Pattern

/**
 * Builder for constructing WaitForLogConfig from task properties.
 *
 * <p>This class performs validation and pattern compilation, converting from
 * DSL property values to an immutable runtime configuration.</p>
 *
 * <p>This is Phase 2 validation (execution time) - validates regex syntax and
 * re-validates structure. Phase 1 validation (configuration time) occurs in
 * ComposeStackSpec.validateWaitForLogSpec().</p>
 */
class WaitForLogConfigBuilder {

    private WaitForLogConfigBuilder() {
        // Utility class
    }

    /**
     * Build WaitForLogConfig from task property values.
     *
     * <p>This method validates inputs and compiles regex patterns.</p>
     *
     * @param projectName Compose project name
     * @param waitForServices Map of service -> pattern strings
     * @param rejectPatterns Map of service -> reject pattern strings (may be null)
     * @param timeoutSeconds Timeout in seconds
     * @param pollSeconds Poll interval in seconds
     * @param caseInsensitive Whether to use case-insensitive matching
     * @param verbose Whether to enable verbose logging
     * @param progressIntervalSeconds Progress logging interval (0 = disabled)
     * @return Configured WaitForLogConfig
     * @throws GradleException if validation fails
     */
    static WaitForLogConfig build(
            String projectName,
            Map<String, List<String>> waitForServices,
            Map<String, List<String>> rejectPatterns,
            int timeoutSeconds,
            int pollSeconds,
            boolean caseInsensitive,
            boolean verbose,
            int progressIntervalSeconds) {

        // Validate waitForServices (defensive - should be caught at config time)
        if (waitForServices == null || waitForServices.isEmpty()) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block: 'waitForServices' cannot be empty.\n" +
                "At least one service with patterns must be specified.\n\n" +
                "Example:\n" +
                "    waitForLog {\n" +
                "        waitForServices.set([\n" +
                "            'app': ['Started Application']\n" +
                "        ])\n" +
                "    }"
            )
        }

        // Validate timeout and poll values are positive
        if (timeoutSeconds <= 0) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block: 'timeoutSeconds' must be positive, got: ${timeoutSeconds}"
            )
        }
        if (pollSeconds <= 0) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block: 'pollSeconds' must be positive, got: ${pollSeconds}"
            )
        }

        // Warn if pollSeconds > timeoutSeconds (only one poll attempt will occur)
        if (pollSeconds > timeoutSeconds) {
            System.err.println("[waitForLog] WARNING: pollSeconds (${pollSeconds}) > timeoutSeconds (${timeoutSeconds}). " +
                "Only one poll attempt will be made before timeout. Consider increasing timeoutSeconds or " +
                "decreasing pollSeconds.")
        }

        // Validate each service has at least one pattern
        waitForServices.each { serviceName, patterns ->
            if (patterns == null || patterns.isEmpty()) {
                throw new GradleException(
                    "Configuration error in 'waitForLog' block: Pattern list for service " +
                    "'${serviceName}' cannot be empty.\n" +
                    "Each service must have at least one pattern to match.\n\n" +
                    "Example:\n" +
                    "    waitForServices.set([\n" +
                    "        '${serviceName}': ['Started Application']  // At least one pattern required\n" +
                    "    ])"
                )
            }
        }

        // Compile patterns (validates regex syntax)
        Map<String, List<Pattern>> compiledPatterns
        Map<String, List<Pattern>> compiledRejectPatterns

        try {
            compiledPatterns = LogPatternMatcher.compilePatterns(waitForServices, caseInsensitive)
        } catch (IllegalArgumentException e) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block: ${e.message}"
            )
        }

        try {
            compiledRejectPatterns = rejectPatterns != null
                ? LogPatternMatcher.compilePatterns(rejectPatterns, caseInsensitive)
                : [:]
        } catch (IllegalArgumentException e) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block (rejectPatterns): ${e.message}"
            )
        }

        // Warn about orphaned reject patterns (services in rejectPatterns but not in waitForServices)
        // This is a warning, not an error, because it may be intentional in some cases
        if (rejectPatterns != null) {
            def orphanedServices = rejectPatterns.keySet() - waitForServices.keySet()
            if (!orphanedServices.isEmpty()) {
                // Use Gradle's logger for warnings during build
                // Note: This requires injecting a logger or using a static warning mechanism
                // For now, log to stderr as a fallback; in production, use proper Gradle logging
                System.err.println("[waitForLog] WARNING: rejectPatterns contains services not in " +
                    "waitForServices: ${orphanedServices}. These reject patterns will never be checked.")
            }
        }

        return new WaitForLogConfig(
            projectName,
            compiledPatterns,
            compiledRejectPatterns,
            Duration.ofSeconds(timeoutSeconds),
            Duration.ofSeconds(pollSeconds),
            caseInsensitive,
            verbose,
            Duration.ofSeconds(progressIntervalSeconds)
        )
    }
}
```

### 6. ComposeStackSpec Extension

Add the `waitForLog` block to `ComposeStackSpec`. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ComposeStackSpec.groovy`:

**Prerequisites - Verified Against Existing Codebase:**
The existing `ComposeStackSpec` is an abstract class with:
1. Constructor: `ComposeStackSpec(String name, ObjectFactory objectFactory)` with stored fields
2. `getName()` method returning the stack name (line 46-48)
3. `objectFactory` field available for creating nested specs
4. Existing pattern for `waitForHealthy` and `waitForRunning` using `Property<WaitSpec>` (abstract)

**Required Changes** (add to existing `ComposeStackSpec.groovy`):

```groovy
// Add imports at top of file (only needed if not using wildcard imports within the same package)
// Note: WaitForLogSpec is in the same package (spec), so this import may be unnecessary
// depending on existing import style. Add only if explicit imports are used.
import org.gradle.api.Action          // Required for Action<WaitForLogSpec> method signature
import org.gradle.api.GradleException  // Required for validateWaitForLogSpec() error throwing

// Add abstract property declaration (after existing waitForHealthy and waitForRunning)
abstract Property<WaitForLogSpec> getWaitForLog()

// Add DSL methods (after existing waitForHealthy/waitForRunning methods)

/**
 * Configure log-based readiness checking using a Closure.
 *
 * @param closure Configuration closure for WaitForLogSpec
 */
void waitForLog(@DelegatesTo(WaitForLogSpec) Closure closure) {
    def waitForLogSpec = objectFactory.newInstance(WaitForLogSpec)
    closure.delegate = waitForLogSpec
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure.call()
    validateWaitForLogSpec(waitForLogSpec, name)
    waitForLog.set(waitForLogSpec)
}

/**
 * Configure log-based readiness checking using an Action.
 *
 * @param action Configuration action for WaitForLogSpec
 */
void waitForLog(Action<WaitForLogSpec> action) {
    def waitForLogSpec = objectFactory.newInstance(WaitForLogSpec)
    action.execute(waitForLogSpec)
    validateWaitForLogSpec(waitForLogSpec, name)
    waitForLog.set(waitForLogSpec)
}

/**
 * Validates that a WaitForLogSpec has valid configuration.
 *
 * <p>This is Phase 1 validation (configuration time) - checks structural validity.
 * Regex pattern compilation is deferred to Phase 2 (execution time) in
 * WaitForLogConfigBuilder to avoid configuration cache issues.</p>
 *
 * @param spec The WaitForLogSpec to validate
 * @param stackName The name of the compose stack (for error messages)
 * @throws GradleException if validation fails
 */
private void validateWaitForLogSpec(WaitForLogSpec spec, String stackName) {
    // Check waitForServices is present and non-empty
    if (!spec.waitForServices.present || spec.waitForServices.get().isEmpty()) {
        throw new GradleException(
            "Configuration error in 'waitForLog' block for compose stack '${stackName}': " +
            "'waitForServices' must specify at least one service with patterns.\n\n" +
            "Example:\n" +
            "    waitForLog {\n" +
            "        waitForServices.set([\n" +
            "            'app': ['Started Application']\n" +
            "        ])\n" +
            "    }\n\n" +
            "If you don't need log-based readiness checks, remove the empty 'waitForLog' block."
        )
    }

    // Validate each service has at least one pattern and all patterns are strings
    spec.waitForServices.get().each { serviceName, patterns ->
        if (patterns == null || patterns.isEmpty()) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block for compose stack '${stackName}': " +
                "Pattern list for service '${serviceName}' cannot be empty.\n" +
                "Each service must have at least one pattern to match."
            )
        }
        // Type-safety check: ensure all patterns are strings (catches type erasure issues)
        patterns.each { pattern ->
            if (!(pattern instanceof String)) {
                throw new GradleException(
                    "Configuration error in 'waitForLog' block for compose stack '${stackName}': " +
                    "Pattern values must be strings, got: ${pattern?.getClass()?.name ?: 'null'}\n" +
                    "For service '${serviceName}', ensure all patterns are quoted strings."
                )
            }
        }
    }

    // Note: Regex pattern validation is deferred to task execution time (Phase 2)
    // to avoid potential configuration cache issues with Pattern compilation
}
```

### 7. ComposeService Interface Extension

Add the `waitForLogPatterns()` method to `ComposeService`. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ComposeService.groovy`:

```groovy
// Add imports
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import java.util.concurrent.CompletableFuture  // Required for return type

// Add method signature
/**
 * Wait for log patterns to appear in service logs.
 *
 * <p>This method polls container logs until all specified patterns are found for each
 * service, or until timeout/reject pattern match/service crash occurs.</p>
 *
 * @param config Wait-for-log configuration with patterns, timeout, etc.
 * @return CompletableFuture with results per service
 * @throws ComposeServiceException if timeout, reject pattern match, or service crash
 */
CompletableFuture<Map<String, WaitForLogResult>> waitForLogPatterns(WaitForLogConfig config)
```

### 7.5 Dependencies on Existing Components (Reference Only)

> **Important**: This section documents existing codebase components that the `waitForLog` implementation
> depends on. This is **reference documentation only** to help implementers understand the dependencies.
> **No changes are required** to these components (except for the `LogsConfig` prerequisite change
> documented in Phase 0). Review these signatures before implementation to ensure they match the actual
> codebase.

The `waitForLogPatterns()` implementation requires the ability to fetch container logs. Both
`LogsConfig` and `captureLogs()` **already exist** in the codebase.

**Existing LogsConfig Model Class** (`plugin/src/main/groovy/com/kineticfire/gradle/docker/model/LogsConfig.groovy`):

> **IMPORTANT**: Before implementation, verify this constructor signature matches the actual code using:
> ```bash
> rg "class LogsConfig" plugin/src/main/groovy -A 20
> ```
> The `fetchServiceLogs()` method uses: `new LogsConfig([serviceName], 0, false, null)`

```groovy
package com.kineticfire.gradle.docker.model

import java.nio.file.Path

/**
 * Configuration for capturing Docker Compose logs
 */
class LogsConfig {
    final List<String> services
    final int tailLines
    final boolean follow
    final Path outputFile

    LogsConfig(List<String> services, int tailLines = 100, boolean follow = false, Path outputFile = null) {
        this.services = services ?: []
        this.tailLines = Math.max(1, tailLines)  // WILL BE CHANGED in Phase 0
        this.follow = follow
        this.outputFile = outputFile
    }

    // WILL BE ADDED in Phase 0:
    // boolean hasLimitedTail() { return tailLines > 0 }

    boolean hasSpecificServices() {
        return !services.empty
    }

    boolean hasOutputFile() {
        return outputFile != null
    }
}
```

**Existing ComposeService Interface** (`plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ComposeService.groovy`):

```groovy
/**
 * Capture logs from compose services
 * @param projectName Compose project name
 * @param config Logs configuration
 * @return CompletableFuture with captured logs
 * @throws ComposeServiceException if log capture fails
 */
CompletableFuture<String> captureLogs(String projectName, LogsConfig config)
```

### 8. ExecLibraryComposeService Implementation

Implement `waitForLogPatterns()` in `ExecLibraryComposeService`. The implementation follows the
existing patterns for `waitForServices()`.

**Required Imports** (add to `ExecLibraryComposeService.groovy`):

> **Implementation Note**: The import lists in this document are provided as guidance. Some imports
> (e.g., `java.util.Set`, `java.util.HashMap`) may already be present or automatically available
> in Groovy. During implementation, rely on IDE assistance to add missing imports and remove
> duplicates. Verify all imports compile successfully before proceeding.

```groovy
// Complete imports to add for waitForLog functionality
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import com.kineticfire.gradle.docker.util.LogPatternMatcher
import java.util.regex.Pattern
import java.util.Set
import java.util.HashSet
import java.util.HashMap
import java.util.concurrent.ExecutionException  // Required for unwrapping CompletableFuture.get() exceptions

// JSON parsing for isServiceRunning() method (part of Groovy runtime, no additional dependencies)
import groovy.json.JsonSlurper
import groovy.json.JsonException  // For explicit exception handling in JSON parsing
```

**Thread Safety Note**: The `waitForLogPatterns()` implementation runs entirely within a single thread context
(inside `CompletableFuture.supplyAsync()`). The mutable collections used for tracking match state are:
- `Map<String, Set<Integer>> matchedPatternsByService` - tracks which pattern indices matched per service
- `Map<String, Map<Integer, Long>> matchTimesByService` - tracks when each pattern matched

These are not shared across threads, so no synchronization is required.

**Implementation Note**: Add the following comment in the actual code near the mutable collections:
```groovy
// NOTE: These maps are NOT thread-safe. The polling loop must remain single-threaded.
// Do not refactor to use parallel stream processing without adding synchronization.
```

**Dependencies Already Verified** (see "Existing Dependencies Verification" section above):
- `timeService: TimeService` - Exists (provides `currentTimeMillis()` and `sleep()`)
- `processExecutor: ProcessExecutor` - Exists (executes shell commands)
- `getComposeCommand()` - Exists (returns compose command prefix)
- `serviceLogger: ServiceLogger` - Exists (logging interface)

**Logger Usage Clarification**:

| Context | Logger | Methods | Example |
|---------|--------|---------|---------|
| Service layer (`ExecLibraryComposeService`) | `serviceLogger` (injected `ServiceLogger`) | `info()`, `debug()`, `warn()`, `error()` | `serviceLogger.info("[waitForLog] ...")` |
| Task layer (`ComposeUpTask`) | `logger` (Gradle's inherited `Logger`) | `lifecycle()`, `info()`, `debug()`, etc. | `logger.lifecycle("Waiting for ...")` |

This distinction is intentional:
- **Service layer** uses `ServiceLogger` for testability (can be mocked in unit tests)
- **Task layer** uses Gradle's `logger` for Gradle-native lifecycle messages

**Constants** (add at the top of `ExecLibraryComposeService.groovy` class):

```groovy
/**
 * Number of recent log lines to include in error messages for diagnostics.
 *
 * <p>Future enhancement: Consider making this configurable via DSL property
 * (e.g., {@code errorLogLines.set(20)}) in a future release if users need
 * more context for debugging.</p>
 */
private static final int RECENT_LOG_LINES_FOR_ERROR = 10
```

> **Thread Pool Note**: `CompletableFuture.supplyAsync()` uses the common ForkJoinPool by default.
> This matches existing patterns in `ExecLibraryComposeService` for `waitForServices()`. If thread
> isolation is needed in the future, consider injecting an `Executor` parameter.

> **JsonSlurper Thread Safety Note**: `JsonSlurper` is not thread-safe when using the `LAX` parser type.
> The current implementation is single-threaded within `supplyAsync()`, so this is safe. If parallelizing
> service checks in the future, create a new `JsonSlurper` instance per thread or use `JsonSlurper.setType(JsonParserType.INDEX_OVERLAY)`.

**Complete Implementation** (add to `ExecLibraryComposeService.groovy`):

```groovy
@Override
CompletableFuture<Map<String, WaitForLogResult>> waitForLogPatterns(WaitForLogConfig config) {
    if (config == null) {
        throw new NullPointerException("Wait-for-log config cannot be null")
    }
    return CompletableFuture.supplyAsync({
        try {
            return executeWaitForLogPatterns(config)
        } catch (ComposeServiceException e) {
            // Re-throw domain exceptions as-is
            throw e
        } catch (InterruptedException e) {
            // Preserve interrupt status and fail with clear message
            Thread.currentThread().interrupt()
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                "Interrupted while waiting for log patterns",
                e
            )
        } catch (java.util.concurrent.ExecutionException e) {
            // Unwrap ExecutionException from CompletableFuture.get() calls (e.g., from captureLogs)
            def cause = e.cause ?: e
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                "Error waiting for log patterns: ${cause.message}",
                cause
            )
        } catch (Exception e) {
            // Catch-all for unexpected exceptions
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                "Error waiting for log patterns: ${e.message}",
                e
            )
        }
    })
}

/**
 * Core logic for waiting for log patterns.
 * Separated for testability and clarity.
 *
 * <p><b>Thread Safety:</b> This method MUST remain single-threaded. The polling loop uses
 * mutable collections that are not thread-safe. Do not override this method to add
 * parallelization without adding proper synchronization.</p>
 */
@VisibleForTesting
protected final Map<String, WaitForLogResult> executeWaitForLogPatterns(WaitForLogConfig config) {
    logWaitStart(config)

    // Validate all configured services exist in the compose project
    // This provides early failure for typos in service names rather than silent timeout
    validateServicesExist(config)

    // Initialize tracking state for each service
    def matchedPatternsByService = initializeMatchedPatternsMap(config)
    def matchTimesByService = initializeMatchTimesMap(config)

    def startTime = timeService.currentTimeMillis()
    def timeoutMillis = config.timeout.toMillis()
    def pollMillis = config.pollInterval.toMillis()
    def progressMillis = config.progressInterval.toMillis()
    def lastProgressLogTime = startTime
    int attemptNumber = 0

    while (timeService.currentTimeMillis() - startTime < timeoutMillis) {
        attemptNumber++
        // Calculate currentTime once at the start of each iteration for consistency
        // All time-based calculations in this iteration use this snapshot
        def currentTime = timeService.currentTimeMillis()
        def elapsedSeconds = (currentTime - startTime) / 1000

        if (config.verbose) {
            logVerbosePollingStart(attemptNumber, config.totalWaitAttempts, elapsedSeconds)
        }

        // Check all services
        def checkResult = checkAllServices(
            config, matchedPatternsByService, matchTimesByService, elapsedSeconds
        )

        if (checkResult.rejected) {
            // A reject pattern was matched - fail immediately
            throw buildRejectException(checkResult.serviceName, checkResult.rejectResult,
                                       matchedPatternsByService, matchTimesByService, config)
        }

        if (checkResult.crashed) {
            // A service crashed - fail immediately
            throw buildCrashException(checkResult.serviceName, checkResult.exitCode,
                                      matchedPatternsByService, matchTimesByService, config)
        }

        // Check if all services are ready
        if (areAllServicesReady(config, matchedPatternsByService)) {
            serviceLogger.info("[waitForLog] All services ready after ${elapsedSeconds} seconds")
            return buildResults(config, matchedPatternsByService, matchTimesByService, true)
        }

        // Log periodic progress if configured
        // Uses config.hasProgressInterval() for clarity instead of checking progressMillis > 0
        // Uses currentTime captured at loop start for consistent timing
        if (config.hasProgressInterval() && (currentTime - lastProgressLogTime) >= progressMillis) {
            logPeriodicProgress(config, matchedPatternsByService, elapsedSeconds)
            lastProgressLogTime = currentTime
        }

        timeService.sleep(pollMillis)
    }

    // Timeout reached
    def elapsedSeconds = (timeService.currentTimeMillis() - startTime) / 1000
    throw buildTimeoutException(config, matchedPatternsByService, matchTimesByService, elapsedSeconds)
}

/**
 * Log the start of the wait operation.
 */
private void logWaitStart(WaitForLogConfig config) {
    def serviceCount = config.services.size()
    def timeoutSecs = config.timeout.toSeconds()
    serviceLogger.info("[waitForLog] Waiting for log patterns in ${serviceCount} service(s) (timeout: ${timeoutSecs}s)...")
}

/**
 * Initialize the map tracking which patterns have matched per service.
 */
private Map<String, Set<Integer>> initializeMatchedPatternsMap(WaitForLogConfig config) {
    def result = new HashMap<String, Set<Integer>>()
    config.services.each { serviceName ->
        result[serviceName] = new HashSet<Integer>()
    }
    return result
}

/**
 * Initialize the map tracking when each pattern matched per service.
 */
private Map<String, Map<Integer, Long>> initializeMatchTimesMap(WaitForLogConfig config) {
    def result = new HashMap<String, Map<Integer, Long>>()
    config.services.each { serviceName ->
        result[serviceName] = new HashMap<Integer, Long>()
    }
    return result
}

/**
 * Validate that all configured services exist in the compose project.
 *
 * <p>This provides early failure for typos in service names rather than silent timeout.
 * A service "exists" if it appears in `docker compose ps` output (regardless of state).</p>
 *
 * <p><b>Note:</b> This validation runs once at the start of the wait loop. If a service
 * is defined in docker-compose.yml but hasn't started yet (e.g., depends_on delay),
 * this check will still pass because compose knows about it.</p>
 *
 * @param config The wait configuration containing service names to validate
 * @throws ComposeServiceException if any configured service is not found in compose project
 */
@VisibleForTesting
protected void validateServicesExist(WaitForLogConfig config) {
    def composeServices = getComposeProjectServices(config.projectName)

    // Special case: empty compose project (no services found at all)
    // This likely means composeUp wasn't called or project doesn't exist
    if (composeServices.isEmpty()) {
        throw new ComposeServiceException(
            ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
            "No services found in compose project '${config.projectName}'.\n" +
            "This typically means the compose project doesn't exist or hasn't been started.\n\n" +
            "Hint: Ensure 'composeUp' task has been executed before 'waitForLog' runs.\n" +
            "      Verify the compose project name matches your docker-compose.yml configuration.",
            "Run 'docker compose -p ${config.projectName} ps' to check project status"
        )
    }

    def missingServices = config.services.findAll { serviceName ->
        !composeServices.contains(serviceName)
    }

    if (!missingServices.isEmpty()) {
        throw new ComposeServiceException(
            ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
            "Service(s) not found in compose project '${config.projectName}': ${missingServices}.\n" +
            "Available services: ${composeServices}\n\n" +
            "Hint: Check for typos in service names in your waitForLog configuration.",
            "Verify service names match those defined in your docker-compose.yml"
        )
    }
}

/**
 * Get list of services defined in a compose project.
 *
 * @param projectName Compose project name
 * @return Set of service names, empty set if project not found or error
 */
@VisibleForTesting
protected Set<String> getComposeProjectServices(String projectName) {
    try {
        def composeCommand = getComposeCommand()
        def command = composeCommand + ["-p", projectName, "ps", "--format", "json", "-a"]
        def result = processExecutor.execute(command)

        if (result.isSuccess() && result.stdout) {
            def jsonSlurper = new JsonSlurper()
            def services = new HashSet<String>()
            def lines = result.stdout.trim().split('\n')
            for (line in lines) {
                if (line.trim()) {
                    try {
                        def container = jsonSlurper.parseText(line.trim())
                        // Extract service name from container info
                        // Docker Compose v2 uses "Service" field
                        def serviceName = container?.Service
                        if (serviceName) {
                            services.add(serviceName)
                        }
                    } catch (groovy.json.JsonException ignored) {
                        // Skip malformed lines
                    }
                }
            }
            return services
        }
        return new HashSet<String>()
    } catch (Exception e) {
        serviceLogger.debug("Error getting compose project services: ${e.message}")
        return new HashSet<String>()
    }
}

/**
 * Check all services for pattern matches, reject patterns, and crashes.
 * Returns a CheckResult indicating the outcome.
 */
@VisibleForTesting
protected CheckAllServicesResult checkAllServices(
        WaitForLogConfig config,
        Map<String, Set<Integer>> matchedPatternsByService,
        Map<String, Map<Integer, Long>> matchTimesByService,
        long elapsedSeconds) {

    for (String serviceName : config.services) {
        // First check if service is still running
        if (!isServiceRunning(config.projectName, serviceName)) {
            def exitCode = getServiceExitCode(config.projectName, serviceName)
            return CheckAllServicesResult.crashed(serviceName, exitCode)
        }

        // Fetch logs for this service
        def logLines = fetchServiceLogs(config.projectName, serviceName)

        // Check for reject patterns FIRST (before success patterns)
        def rejectPatterns = config.rejectPatterns[serviceName]
        if (rejectPatterns != null && !rejectPatterns.isEmpty()) {
            def rejectResult = LogPatternMatcher.checkRejectPatterns(rejectPatterns, logLines)
            if (rejectResult != null) {
                return CheckAllServicesResult.rejected(serviceName, rejectResult)
            }
        }

        // Update success pattern matches
        // Defensive: verify service exists in config (should always be true if config is valid)
        def patterns = config.servicePatterns[serviceName]
        if (patterns == null) {
            throw new IllegalStateException(
                "Internal error: service '${serviceName}' not found in servicePatterns. " +
                "This indicates a bug in WaitForLogConfig construction."
            )
        }
        def matchedPatterns = matchedPatternsByService[serviceName]
        def matchTimes = matchTimesByService[serviceName]

        def newMatches = LogPatternMatcher.updateMatches(
            patterns, matchedPatterns, logLines, elapsedSeconds, matchTimes
        )

        if (config.verbose && newMatches > 0) {
            logVerboseNewMatches(serviceName, matchedPatterns.size(), patterns.size())
        }
    }

    return CheckAllServicesResult.ok()
}

/**
 * Result object for checkAllServices().
 *
 * <p>Uses static factory methods for clarity and type safety.</p>
 *
 * <p><b>Visibility Note:</b> This class is {@code protected static} rather than package-private to allow
 * subclass access for testing purposes. The {@code @VisibleForTesting} annotation documents that external
 * access is only intended for tests, not production subclasses. The {@code static} modifier is required
 * because the class does not need access to outer class instance state.</p>
 */
@VisibleForTesting
protected static class CheckAllServicesResult {
    boolean rejected = false
    boolean crashed = false
    String serviceName
    LogPatternMatcher.RejectCheckResult rejectResult
    Integer exitCode

    /** All services checked successfully, no issues found */
    static CheckAllServicesResult ok() {
        return new CheckAllServicesResult()
    }

    /** A reject pattern was matched */
    static CheckAllServicesResult rejected(String serviceName, LogPatternMatcher.RejectCheckResult rejectResult) {
        def result = new CheckAllServicesResult()
        result.rejected = true
        result.serviceName = serviceName
        result.rejectResult = rejectResult
        return result
    }

    /** A service container crashed/exited */
    static CheckAllServicesResult crashed(String serviceName, Integer exitCode) {
        def result = new CheckAllServicesResult()
        result.crashed = true
        result.serviceName = serviceName
        result.exitCode = exitCode
        return result
    }
}

/**
 * Check if all services have all their patterns matched.
 */
private boolean areAllServicesReady(WaitForLogConfig config, Map<String, Set<Integer>> matchedPatternsByService) {
    return config.services.every { serviceName ->
        def patterns = config.servicePatterns[serviceName]
        def matched = matchedPatternsByService[serviceName]
        matched.size() == patterns.size()
    }
}

/**
 * Fetch logs for a specific service.
 * Returns all logs (tailLines = 0) to ensure early patterns are not missed.
 *
 * <p><b>Note:</b> Uses LogsConfig constructor: {@code LogsConfig(services, tailLines, follow, outputFile)}.
 * Verify this signature matches the actual class before implementation (see Phase 0 checklist).</p>
 *
 * <p><b>Exception handling:</b> The {@code captureLogs()} method returns a CompletableFuture.
 * If log capture fails, {@code get()} throws ExecutionException wrapping the actual cause.
 * This is caught by the outer try-catch in {@code executeWaitForLogPatterns()} which converts
 * it to a ComposeServiceException.</p>
 *
 * <p><b>Empty log handling:</b> Containers that exist but have not yet produced any log output
 * (e.g., just started, slow to initialize) will return an empty list. This is expected behavior -
 * patterns simply won't match until logs appear. The polling loop will continue checking until
 * timeout or until patterns match.</p>
 */
@VisibleForTesting
protected List<String> fetchServiceLogs(String projectName, String serviceName) {
    // tailLines = 0 means "all logs" after Phase 0 LogsConfig modification
    // Constructor args: services, tailLines, follow, outputFile
    def logsConfig = new LogsConfig([serviceName], 0, false, null)
    def logsFuture = captureLogs(projectName, logsConfig)
    def logsOutput = logsFuture.get()
    // Handle null, empty string, and whitespace-only output
    // Empty string would produce [""] which could cause false pattern matches
    // Containers with no log output yet return empty list - patterns won't match until logs appear
    // Pre-compute trim() to avoid double evaluation
    def trimmedOutput = logsOutput?.trim()
    return trimmedOutput ? trimmedOutput.split('\n').toList() : []
}

/**
 * Check if a service container is currently running.
 *
 * <p>Uses `docker compose ps --format json` which outputs JSON with these fields:</p>
 * <pre>
 * {"Name":"project-service-1","State":"running","ExitCode":0,...}
 * </pre>
 *
 * <p><b>Expected JSON fields:</b></p>
 * <ul>
 *   <li>{@code State}: Container state (e.g., "running", "exited", "created")</li>
 *   <li>{@code ExitCode}: Exit code when container stopped (used by getServiceExitCode)</li>
 * </ul>
 *
 * <p><b>Docker Compose Version Compatibility:</b></p>
 * <ul>
 *   <li>Tested with Docker Compose v2.x (the standalone `docker compose` plugin)</li>
 *   <li>The JSON format may vary between Docker Compose versions</li>
 *   <li>If JSON parsing fails, the raw output is logged at debug level to aid troubleshooting</li>
 *   <li>Handles missing fields gracefully (returns false rather than throwing)</li>
 * </ul>
 *
 * <p><b>Note:</b> Returns {@code false} if the container doesn't exist, was never created,
 * or has exited. The caller cannot distinguish between these cases from this method alone.
 * Use {@link #getServiceExitCode} to check if a container exited vs never existed (returns null).</p>
 *
 * @param projectName Compose project name
 * @param serviceName Service name to check
 * @return true if container is in "running" state, false otherwise
 */
@VisibleForTesting
protected boolean isServiceRunning(String projectName, String serviceName) {
    try {
        def composeCommand = getComposeCommand()
        def command = composeCommand + ["-p", projectName, "ps", serviceName, "--format", "json"]
        def result = processExecutor.execute(command)

        if (result.isSuccess() && result.stdout) {
            def jsonSlurper = new JsonSlurper()
            // Docker Compose ps --format json outputs one JSON object per line
            // Format tested with Docker Compose v2.x
            def lines = result.stdout.trim().split('\n')
            for (line in lines) {
                if (line.trim()) {
                    try {
                        def container = jsonSlurper.parseText(line.trim())
                        // Defensive: handle missing State field gracefully
                        def state = container?.State?.toLowerCase() ?: ''
                        if (state == 'running') {
                            return true
                        }
                    } catch (groovy.json.JsonException jsonEx) {
                        // Log at warn level to aid debugging malformed docker output
                        // Include raw line content at debug level for troubleshooting version differences
                        serviceLogger.warn("Failed to parse docker compose ps JSON output: ${jsonEx.message}")
                        serviceLogger.debug("Raw JSON line that failed to parse: ${line}")
                    }
                }
            }
        }
        return false
    } catch (Exception e) {
        serviceLogger.debug("Error checking if service is running: ${e.message}")
        return false
    }
}

/**
 * Get the exit code of a stopped/crashed service.
 *
 * <p><b>Design Note:</b> This method is intentionally separate from {@link #isServiceRunning}
 * even though both execute the same `docker compose ps` command. This separation follows the
 * Single Responsibility Principle (SRP) - each method has one clear purpose. The minor
 * performance overhead (two process spawns instead of one when checking a crashed service)
 * is acceptable because:</p>
 * <ul>
 *   <li>Service crashes are exceptional cases, not the normal path</li>
 *   <li>Code clarity and testability are more valuable than micro-optimization</li>
 *   <li>Each method can be unit tested independently with focused assertions</li>
 * </ul>
 *
 * <p><b>Return values:</b></p>
 * <ul>
 *   <li>{@code 0}: Container exited successfully</li>
 *   <li>{@code non-zero}: Container exited with error</li>
 *   <li>{@code null}: Container not found, never created, or still running</li>
 * </ul>
 *
 * <p><b>Note:</b> A null return value is ambiguous - it could mean the container
 * doesn't exist OR the ExitCode field is missing from the JSON output. The error
 * message in {@link #buildCrashException} displays "unknown" for null exit codes.</p>
 *
 * @param projectName Compose project name
 * @param serviceName Service name to check
 * @return Exit code, or null if unavailable
 */
@VisibleForTesting
protected Integer getServiceExitCode(String projectName, String serviceName) {
    try {
        def composeCommand = getComposeCommand()
        def command = composeCommand + ["-p", projectName, "ps", serviceName, "--format", "json"]
        def result = processExecutor.execute(command)

        if (result.isSuccess() && result.stdout) {
            def jsonSlurper = new JsonSlurper()
            def lines = result.stdout.trim().split('\n')
            for (line in lines) {
                if (line.trim()) {
                    def container = jsonSlurper.parseText(line.trim())
                    // Defensive: handle missing ExitCode field gracefully
                    return container?.ExitCode as Integer
                }
            }
        }
        return null
    } catch (Exception e) {
        serviceLogger.debug("Error getting service exit code: ${e.message}")
        return null
    }
}

/**
 * Get recent logs for error reporting.
 */
@VisibleForTesting
protected List<String> getRecentLogs(String projectName, String serviceName, int lineCount) {
    def logsConfig = new LogsConfig([serviceName], lineCount, false, null)
    def logsFuture = captureLogs(projectName, logsConfig)
    def logsOutput = logsFuture.get()
    return logsOutput ? logsOutput.split('\n').toList() : []
}

/**
 * Build result objects from match state.
 */
private Map<String, WaitForLogResult> buildResults(
        WaitForLogConfig config,
        Map<String, Set<Integer>> matchedPatternsByService,
        Map<String, Map<Integer, Long>> matchTimesByService,
        boolean allReady) {

    def results = [:]
    config.services.each { serviceName ->
        def patterns = config.servicePatterns[serviceName]
        def matchedPatterns = matchedPatternsByService[serviceName]
        def matchTimes = matchTimesByService[serviceName]

        def patternMatches = patterns.withIndex().collect { pattern, index ->
            def matched = matchedPatterns.contains(index)
            def matchedAt = matchTimes[index]
            new WaitForLogResult.PatternMatch(pattern.pattern(), matched, matchedAt)
        }

        def serviceReady = matchedPatterns.size() == patterns.size()
        results[serviceName] = new WaitForLogResult(serviceName, patternMatches, serviceReady)
    }
    return results
}

// --- Logging helpers ---

private void logVerbosePollingStart(int attempt, int totalAttempts, long elapsedSeconds) {
    serviceLogger.info("[waitForLog] Polling for log patterns (attempt ${attempt}/${totalAttempts}, elapsed: ${elapsedSeconds}s)...")
}

private void logVerboseNewMatches(String serviceName, int matchedCount, int totalCount) {
    serviceLogger.info("[waitForLog] Service '${serviceName}': ${matchedCount}/${totalCount} patterns matched")
}

private void logPeriodicProgress(WaitForLogConfig config, Map<String, Set<Integer>> matchedPatternsByService,
                                  long elapsedSeconds) {
    def summary = config.services.collect { serviceName ->
        def patterns = config.servicePatterns[serviceName]
        def matched = matchedPatternsByService[serviceName]
        "${serviceName} ${matched.size()}/${patterns.size()}"
    }.join(', ')
    serviceLogger.info("[waitForLog] Progress at ${elapsedSeconds}s: ${summary}")
}

// --- Exception builders ---

private ComposeServiceException buildTimeoutException(
        WaitForLogConfig config,
        Map<String, Set<Integer>> matchedPatternsByService,
        Map<String, Map<Integer, Long>> matchTimesByService,
        long elapsedSeconds) {

    def sb = new StringBuilder()
    sb.append("Timeout waiting for log patterns after ${elapsedSeconds} seconds.\n\n")

    config.services.each { serviceName ->
        def patterns = config.servicePatterns[serviceName]
        def matchedPatterns = matchedPatternsByService[serviceName]
        def matchTimes = matchTimesByService[serviceName]

        def status = matchedPatterns.size() == patterns.size() ? "READY" : "NOT READY"
        def matchCount = "${matchedPatterns.size()}/${patterns.size()}"
        sb.append("Service '${serviceName}' - ${status} (${matchCount} patterns matched):\n")

        patterns.eachWithIndex { pattern, index ->
            def matched = matchedPatterns.contains(index)
            def matchedAt = matchTimes[index]
            if (matched) {
                sb.append("  [FOUND]     '${pattern.pattern()}' - matched at ${matchedAt}s\n")
            } else {
                sb.append("  [NOT FOUND] '${pattern.pattern()}'\n")
            }
        }
        sb.append("\n")
    }

    // Add recent logs for unready services
    config.services.each { serviceName ->
        def matchedPatterns = matchedPatternsByService[serviceName]
        def patterns = config.servicePatterns[serviceName]
        if (matchedPatterns.size() < patterns.size()) {
            sb.append("Last ${RECENT_LOG_LINES_FOR_ERROR} log lines from '${serviceName}':\n")
            def recentLogs = getRecentLogs(config.projectName, serviceName, RECENT_LOG_LINES_FOR_ERROR)
            recentLogs.each { line -> sb.append("  ${line}\n") }
            sb.append("\n")
        }
    }

    sb.append("Hint: Check if the expected log message format matches your pattern.\n")
    def caseSensitivity = config.caseInsensitive ? 'insensitive' : 'sensitive'
    sb.append("      Patterns are case-${caseSensitivity} regex expressions.\n")
    sb.append("      Use verbose.set(true) to see detailed progress during polling.")

    return new ComposeServiceException(
        ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
        sb.toString(),
        "Increase timeoutSeconds or check service startup logs"
    )
}

private ComposeServiceException buildRejectException(
        String serviceName,
        LogPatternMatcher.RejectCheckResult rejectResult,
        Map<String, Set<Integer>> matchedPatternsByService,
        Map<String, Map<Integer, Long>> matchTimesByService,
        WaitForLogConfig config) {

    def patterns = config.servicePatterns[serviceName]
    def matchedPatterns = matchedPatternsByService[serviceName]
    def matchTimes = matchTimesByService[serviceName]

    def sb = new StringBuilder()
    sb.append("Reject pattern matched in service '${serviceName}' - failing immediately.\n")
    sb.append("  Matched reject pattern: '${rejectResult.patternString}'\n")
    sb.append("  Log line: \"${rejectResult.matchingLogLine}\"\n\n")

    sb.append("  Service patterns status:\n")
    patterns.eachWithIndex { pattern, index ->
        def matched = matchedPatterns.contains(index)
        def matchedAt = matchTimes[index]
        if (matched) {
            sb.append("    [FOUND]     '${pattern.pattern()}' - matched at ${matchedAt}s\n")
        } else {
            sb.append("    [NOT FOUND] '${pattern.pattern()}'\n")
        }
    }

    sb.append("\nHint: A reject pattern indicates the service encountered an error during startup.\n")
    sb.append("      Check the full container logs for details.")

    return new ComposeServiceException(
        ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED,
        sb.toString(),
        "Check service logs and fix the startup error"
    )
}

private ComposeServiceException buildCrashException(
        String serviceName,
        Integer exitCode,
        Map<String, Set<Integer>> matchedPatternsByService,
        Map<String, Map<Integer, Long>> matchTimesByService,
        WaitForLogConfig config) {

    def patterns = config.servicePatterns[serviceName]
    def matchedPatterns = matchedPatternsByService[serviceName]
    def matchTimes = matchTimesByService[serviceName]

    def sb = new StringBuilder()
    sb.append("Service '${serviceName}' crashed during log pattern wait.\n")
    // Note: exitCode is null if container was never found or ExitCode field missing from JSON
    sb.append("  Container exit code: ${exitCode != null ? exitCode : 'unknown (container may not exist)'}\n")
    sb.append("  Container status: exited or not found\n\n")

    sb.append("  Service patterns status:\n")
    patterns.eachWithIndex { pattern, index ->
        def matched = matchedPatterns.contains(index)
        def matchedAt = matchTimes[index]
        if (matched) {
            sb.append("    [FOUND]     '${pattern.pattern()}' - matched at ${matchedAt}s\n")
        } else {
            sb.append("    [NOT FOUND] '${pattern.pattern()}'\n")
        }
    }

    sb.append("\n  Last ${RECENT_LOG_LINES_FOR_ERROR} log lines from '${serviceName}':\n")
    def recentLogs = getRecentLogs(config.projectName, serviceName, RECENT_LOG_LINES_FOR_ERROR)
    recentLogs.each { line -> sb.append("    ${line}\n") }

    sb.append("\nHint: The container exited before all patterns were matched.\n")
    sb.append("      Check the container logs with: docker logs <container-id>\n")
    sb.append("      Review the application startup configuration and resource limits.")

    return new ComposeServiceException(
        ComposeServiceException.ErrorType.SERVICE_CRASHED,
        sb.toString(),
        "Check container logs and fix the startup error"
    )
}
```

### 9. ComposeServiceException Extension

Add new error types to `ComposeServiceException`. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/exception/ComposeServiceException.groovy`:

**Add new values to ErrorType enum:**

```groovy
// NEW: Add these error types for waitForLog feature (add after existing error types)
LOG_PATTERN_TIMEOUT("Timeout waiting for log patterns to appear."),
LOG_REJECT_PATTERN_MATCHED("A reject pattern was matched in container logs."),
SERVICE_CRASHED("Service container crashed during wait operation.")
```

### 10. ComposeUpTask Extension

Add `waitForLog*` properties and execution to `ComposeUpTask`. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy`:

**Required Imports** (add to existing imports):

```groovy
import com.kineticfire.gradle.docker.util.WaitForLogConfigBuilder
import org.gradle.api.provider.MapProperty
```

**Note on Existing Imports**: The following imports should already exist in `ComposeUpTask.groovy` for
the existing `waitForRunning` and `waitForHealthy` functionality. **Verify they are present before
implementation** - if any are missing, add them:
```groovy
import java.time.Duration           // REQUIRED for performWaitIfConfigured() - add if missing
import com.kineticfire.gradle.docker.model.WaitConfig      // Used for wait configuration
import com.kineticfire.gradle.docker.model.ServiceStatus   // Used for RUNNING/HEALTHY status
```

**Verification command** (from Pre-Implementation Checklist):
```bash
rg "import java.time.Duration" plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy
```

**Add Flattened Input Properties** (add after existing `waitForRunning*` properties):

```groovy
// waitForLog properties (flattened for configuration cache compatibility)
@Input
@Optional
abstract MapProperty<String, List<String>> getWaitForLogServices()

@Input
@Optional
abstract MapProperty<String, List<String>> getWaitForLogRejectPatterns()

@Input
@Optional
abstract Property<Integer> getWaitForLogTimeoutSeconds()

@Input
@Optional
abstract Property<Integer> getWaitForLogPollSeconds()

@Input
@Optional
abstract Property<Boolean> getWaitForLogCaseInsensitive()

@Input
@Optional
abstract Property<Boolean> getWaitForLogVerbose()

@Input
@Optional
abstract Property<Integer> getWaitForLogProgressIntervalSeconds()
```

**Update `performWaitIfConfigured` Method** - change execution order and add waitForLog:

```groovy
/**
 * Wait for services to reach desired state if configured.
 *
 * <p>Execution order: waitForRunning -> waitForHealthy -> waitForLog</p>
 * <p>This order reflects the natural startup sequence: containers must be running
 * before health checks can pass, and health checks should pass before checking
 * for application-specific log messages.</p>
 */
private void performWaitIfConfigured(String stackName, String projectName) {
    // Step 1: Wait for running services (fastest check)
    if (waitForRunningServices.isPresent() && !waitForRunningServices.get().isEmpty()) {
        def services = waitForRunningServices.get()
        def timeoutSeconds = waitForRunningTimeoutSeconds.getOrElse(60)
        def pollSeconds = waitForRunningPollSeconds.getOrElse(2)

        logger.lifecycle("Waiting for services to be RUNNING: {}", services)

        def waitConfig = new WaitConfig(
            projectName,
            services,
            Duration.ofSeconds(timeoutSeconds),
            Duration.ofSeconds(pollSeconds),
            ServiceStatus.RUNNING
        )

        def waitFuture = composeService.get().waitForServices(waitConfig)
        waitFuture.get()

        logger.lifecycle("All services are RUNNING")
    }

    // Step 2: Wait for healthy services (requires health check to pass)
    if (waitForHealthyServices.isPresent() && !waitForHealthyServices.get().isEmpty()) {
        def services = waitForHealthyServices.get()
        def timeoutSeconds = waitForHealthyTimeoutSeconds.getOrElse(60)
        def pollSeconds = waitForHealthyPollSeconds.getOrElse(2)

        logger.lifecycle("Waiting for services to be HEALTHY: {}", services)

        def waitConfig = new WaitConfig(
            projectName,
            services,
            Duration.ofSeconds(timeoutSeconds),
            Duration.ofSeconds(pollSeconds),
            ServiceStatus.HEALTHY
        )

        def waitFuture = composeService.get().waitForServices(waitConfig)
        waitFuture.get()

        logger.lifecycle("All services are HEALTHY")
    }

    // Step 3: Wait for log patterns (application-specific readiness)
    performWaitForLog(projectName)
}

/**
 * Wait for log patterns to appear in service logs.
 */
private void performWaitForLog(String projectName) {
    if (!waitForLogServices.isPresent() || waitForLogServices.get().isEmpty()) {
        return
    }

    def services = waitForLogServices.get()
    logger.lifecycle("Waiting for log patterns in services: {}", services.keySet())

    // NOTE on getOrNull() vs getOrElse():
    // - rejectPatterns uses getOrNull() because WaitForLogConfigBuilder.build() accepts null
    //   and handles it appropriately (treats as empty map)
    // - Scalar properties use getOrElse() with defaults for consistency with the wiring in
    //   GradleDockerPlugin (Section 11) which also provides these defaults
    def config = WaitForLogConfigBuilder.build(
        projectName,
        services,
        waitForLogRejectPatterns.getOrNull(),
        waitForLogTimeoutSeconds.getOrElse(60),
        waitForLogPollSeconds.getOrElse(2),
        waitForLogCaseInsensitive.getOrElse(false),
        waitForLogVerbose.getOrElse(false),
        waitForLogProgressIntervalSeconds.getOrElse(0)
    )

    def waitFuture = composeService.get().waitForLogPatterns(config)
    waitFuture.get()

    logger.lifecycle("All log patterns matched")
}
```

### 11. Plugin Wiring (GradleDockerPlugin)

Wire the `WaitForLogSpec` properties to `ComposeUpTask` inputs. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`:

Find the `configureComposeUpTask` method and locate the existing `waitForRunning` property wiring block
(look for comments like `// Configure wait-for-running settings` or code setting `task.waitForRunningServices`).
Add the following **immediately after** the `waitForRunning` wiring block, following the same pattern:

```groovy
// Configure wait-for-log settings (Gradle 10 compatibility)
// Uses conditional wiring pattern matching waitForHealthy and waitForRunning
if (stackSpec.waitForLog.present) {
    def waitForLogSpec = stackSpec.waitForLog.get()

    // Wire the services map (required)
    if (waitForLogSpec.waitForServices.present) {
        task.waitForLogServices.set(waitForLogSpec.waitForServices)
    }

    // Wire optional properties with defaults
    // NOTE: Using getOrElse() is intentionally defensive even though conventions are set in WaitForLogSpec.
    // This ensures robustness if: (1) conventions are accidentally removed during refactoring,
    // (2) the spec is constructed without ObjectFactory (edge case), or (3) convention behavior changes.
    // The slight redundancy is acceptable for improved maintainability and fail-safe behavior.
    task.waitForLogRejectPatterns.set(waitForLogSpec.rejectPatterns.getOrElse([:]))
    task.waitForLogTimeoutSeconds.set(waitForLogSpec.timeoutSeconds.getOrElse(60))
    task.waitForLogPollSeconds.set(waitForLogSpec.pollSeconds.getOrElse(2))
    task.waitForLogCaseInsensitive.set(waitForLogSpec.caseInsensitive.getOrElse(false))
    task.waitForLogVerbose.set(waitForLogSpec.verbose.getOrElse(false))
    task.waitForLogProgressIntervalSeconds.set(waitForLogSpec.progressIntervalSeconds.getOrElse(0))
}
```

### 12. Test Framework Extension Integration (TestIntegrationExtension)

Update `TestIntegrationExtension.setComprehensiveSystemProperties()` to propagate `waitForLog` configuration
via system properties for test framework extensions to consume.

**Lifecycle Support:**
- `Lifecycle.CLASS`: Properties are propagated and used by `ComposeUpTask` (Gradle task handles wait logic
  during `composeUp` task execution).
- `Lifecycle.METHOD`: Properties are propagated via system properties and consumed by test framework
  extensions (`DockerComposeMethodExtension`) which handle wait logic in `beforeEach()`.

Both lifecycles are fully supported. The test framework extensions (Section 12.5) read the system
properties and execute the appropriate wait operations.

Modify `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtension.groovy`:

**Add system property propagation for waitForLog** (after the `waitForRunning` properties block, around line 210):

```groovy
// Wait for log settings - propagate for both CLASS and METHOD lifecycles
// For CLASS: ComposeUpTask handles wait logic; properties also available to test code
// For METHOD: DockerComposeMethodExtension reads properties and executes wait in beforeEach()
def waitForLogSpec = stackSpec.waitForLog.getOrNull()
if (waitForLogSpec) {
    // Serialize the services map as JSON for system property
    def servicesJson = new groovy.json.JsonBuilder(
        waitForLogSpec.waitForServices.getOrElse([:])
    ).toString()
    test.systemProperty("docker.compose.waitForLog.services", servicesJson)

    // Serialize reject patterns as JSON
    def rejectPatternsJson = new groovy.json.JsonBuilder(
        waitForLogSpec.rejectPatterns.getOrElse([:])
    ).toString()
    test.systemProperty("docker.compose.waitForLog.rejectPatterns", rejectPatternsJson)

    // Scalar properties
    test.systemProperty("docker.compose.waitForLog.timeoutSeconds",
        waitForLogSpec.timeoutSeconds.getOrElse(60).toString())
    test.systemProperty("docker.compose.waitForLog.pollSeconds",
        waitForLogSpec.pollSeconds.getOrElse(2).toString())
    test.systemProperty("docker.compose.waitForLog.caseInsensitive",
        waitForLogSpec.caseInsensitive.getOrElse(false).toString())
    test.systemProperty("docker.compose.waitForLog.verbose",
        waitForLogSpec.verbose.getOrElse(false).toString())
    test.systemProperty("docker.compose.waitForLog.progressIntervalSeconds",
        waitForLogSpec.progressIntervalSeconds.getOrElse(0).toString())
}
```

**Add import at top of file**:

```groovy
import groovy.json.JsonBuilder
```

> **JSON Serialization Note**: The regex pattern strings are serialized directly to JSON. Groovy's `JsonBuilder`
> handles standard escape sequences (backslashes, quotes) correctly. However, if patterns contain unusual
> Unicode characters or very long strings, verify JSON parsing on the receiving end works correctly.
>
> **Testing Consideration**: Include functional tests with patterns containing:
> - Backslashes: `'\\[INFO\\]'`
> - Quotes: `'message: "started"'`
> - Special regex chars: `'.*?', '\\d+', '(?i)pattern'`
>
> If JSON serialization issues arise, consider Base64 encoding the pattern strings as a fallback.

### 12.5 Test Framework Extension Updates (Full Lifecycle Support)

This section implements full lifecycle support for `waitForRunning`, `waitForHealthy`, and `waitForLog`
in the JUnit 5 test framework extensions. Currently, `DockerComposeMethodExtension` and
`DockerComposeClassExtension` have hardcoded wait behavior that ignores DSL settings. This update
fixes that gap.

**Code Style Note**: The code examples in this section use Groovy syntax (the actual extension files
are `.groovy` files). If the actual files are Java (`.java`), adjust the syntax accordingly:
- Remove `def` keywords, use explicit types
- Add semicolons
- Use explicit generic types instead of diamond operator where needed

**Problem Statement:**

The current `DockerComposeMethodExtension.waitForStackToBeReady()` implementation has these issues:
1. Uses hardcoded `ServiceStatus.HEALTHY` - ignores `waitForRunning` entirely
2. Uses hardcoded timeout (60s) and poll interval (2s) - ignores DSL `timeoutSeconds` and `pollSeconds`
3. Does not support `waitForLog` at all

**Solution:**

Update the test framework extensions to read the system properties set by `TestIntegrationExtension`
and execute the appropriate wait operations in the correct order:
`waitForRunning` -> `waitForHealthy` -> `waitForLog`

> **Service Dependency Note**: The test framework extensions use `composeService` (a `ComposeService` instance)
> to execute wait operations. This service is typically obtained via constructor injection or field injection
> during extension initialization. Verify that existing extension code provides this dependency before adding
> the new `performWaitForLog()` calls:
> ```bash
> rg "composeService|ComposeService" plugin/src/main/groovy/com/kineticfire/gradle/docker/junit -C 3
> ```

#### 12.5.1 System Property Constants

Add constants for the new system properties. These should be added to both `DockerComposeMethodExtension`
and `DockerComposeClassExtension`:

```java
// Existing constants
private static final String COMPOSE_STACK_PROPERTY = "docker.compose.stack";
private static final String COMPOSE_PROJECT_PROPERTY = "docker.compose.project";
private static final String COMPOSE_FILES_PROPERTY = "docker.compose.files";

// Wait for running settings
private static final String WAIT_FOR_RUNNING_SERVICES = "docker.compose.waitForRunning.services";
private static final String WAIT_FOR_RUNNING_TIMEOUT = "docker.compose.waitForRunning.timeoutSeconds";
private static final String WAIT_FOR_RUNNING_POLL = "docker.compose.waitForRunning.pollSeconds";

// Wait for healthy settings
private static final String WAIT_FOR_HEALTHY_SERVICES = "docker.compose.waitForHealthy.services";
private static final String WAIT_FOR_HEALTHY_TIMEOUT = "docker.compose.waitForHealthy.timeoutSeconds";
private static final String WAIT_FOR_HEALTHY_POLL = "docker.compose.waitForHealthy.pollSeconds";

// Wait for log settings
private static final String WAIT_FOR_LOG_SERVICES = "docker.compose.waitForLog.services";
private static final String WAIT_FOR_LOG_REJECT_PATTERNS = "docker.compose.waitForLog.rejectPatterns";
private static final String WAIT_FOR_LOG_TIMEOUT = "docker.compose.waitForLog.timeoutSeconds";
private static final String WAIT_FOR_LOG_POLL = "docker.compose.waitForLog.pollSeconds";
private static final String WAIT_FOR_LOG_CASE_INSENSITIVE = "docker.compose.waitForLog.caseInsensitive";
private static final String WAIT_FOR_LOG_VERBOSE = "docker.compose.waitForLog.verbose";
private static final String WAIT_FOR_LOG_PROGRESS_INTERVAL = "docker.compose.waitForLog.progressIntervalSeconds";
```

#### 12.5.2 Updated waitForStackToBeReady() Method

Replace the existing `waitForStackToBeReady()` method in **`DockerComposeMethodExtension`** with this
implementation that honors DSL settings for all wait blocks including `waitForLog`:

```groovy
/**
 * Wait for services to reach desired state based on DSL configuration.
 *
 * <p>Execution order: waitForRunning -> waitForHealthy -> waitForLog</p>
 * <p>This method supports full lifecycle (METHOD) for all wait blocks.</p>
 */
private void waitForStackToBeReady(String stackName, String uniqueProjectName) throws Exception {
    System.out.println("Waiting for containers to become ready...")

    // Step 1: Wait for RUNNING services (if configured)
    performWaitForRunning(uniqueProjectName)

    // Step 2: Wait for HEALTHY services (if configured)
    performWaitForHealthy(uniqueProjectName)

    // Step 3: Wait for log patterns (if configured)
    performWaitForLog(uniqueProjectName)

    System.out.println("All configured wait conditions satisfied for stack '" + stackName + "'")
}
```

For **`DockerComposeClassExtension`**, the implementation also includes the `performWaitForLog()` call.
**Note**: While `ComposeUpTask.performWaitIfConfigured()` could handle `waitForLog` for CLASS lifecycle
during the Gradle task execution phase, calling `performWaitForLog()` in the CLASS extension ensures
consistency between CLASS and METHOD lifecycles and provides flexibility for future enhancements where
test classes may need to re-verify readiness conditions.

```groovy
/**
 * Wait for services to reach desired state based on DSL configuration.
 *
 * <p>Execution order: waitForRunning -> waitForHealthy -> waitForLog</p>
 * <p>This method supports full lifecycle (CLASS) for all wait blocks.</p>
 */
private void waitForStackToBeReady(String stackName, String uniqueProjectName) throws Exception {
    System.out.println("Waiting for containers to become ready...")

    // Step 1: Wait for RUNNING services (if configured)
    performWaitForRunning(uniqueProjectName)

    // Step 2: Wait for HEALTHY services (if configured)
    performWaitForHealthy(uniqueProjectName)

    // Step 3: Wait for log patterns (if configured)
    performWaitForLog(uniqueProjectName)

    System.out.println("All configured wait conditions satisfied for stack '" + stackName + "'")
}

/**
 * Wait for services to reach RUNNING state.
 */
private void performWaitForRunning(String projectName) throws Exception {
    String servicesProperty = systemPropertyService.getProperty(WAIT_FOR_RUNNING_SERVICES);
    if (servicesProperty == null || servicesProperty.isEmpty()) {
        return;
    }

    List<String> services = Arrays.asList(servicesProperty.split(","));
    int timeoutSeconds = parseIntProperty(WAIT_FOR_RUNNING_TIMEOUT, 60);
    int pollSeconds = parseIntProperty(WAIT_FOR_RUNNING_POLL, 2);

    System.out.println("Waiting for services to be RUNNING: " + services);

    WaitConfig waitConfig = new WaitConfig(
        projectName,
        services,
        Duration.ofSeconds(timeoutSeconds),
        Duration.ofSeconds(pollSeconds),
        ServiceStatus.RUNNING
    );

    composeService.waitForServices(waitConfig).get();
    System.out.println("All services are RUNNING");
}

/**
 * Wait for services to reach HEALTHY state.
 */
private void performWaitForHealthy(String projectName) throws Exception {
    String servicesProperty = systemPropertyService.getProperty(WAIT_FOR_HEALTHY_SERVICES);
    if (servicesProperty == null || servicesProperty.isEmpty()) {
        return;
    }

    List<String> services = Arrays.asList(servicesProperty.split(","));
    int timeoutSeconds = parseIntProperty(WAIT_FOR_HEALTHY_TIMEOUT, 60);
    int pollSeconds = parseIntProperty(WAIT_FOR_HEALTHY_POLL, 2);

    System.out.println("Waiting for services to be HEALTHY: " + services);

    WaitConfig waitConfig = new WaitConfig(
        projectName,
        services,
        Duration.ofSeconds(timeoutSeconds),
        Duration.ofSeconds(pollSeconds),
        ServiceStatus.HEALTHY
    );

    composeService.waitForServices(waitConfig).get();
    System.out.println("All services are HEALTHY");
}

/**
 * Wait for log patterns to appear in service logs.
 */
private void performWaitForLog(String projectName) throws Exception {
    String servicesJson = systemPropertyService.getProperty(WAIT_FOR_LOG_SERVICES);
    if (servicesJson == null || servicesJson.isEmpty() || servicesJson.equals("{}")) {
        return;
    }

    // Parse JSON configuration
    Map<String, List<String>> services = parseJsonMapProperty(servicesJson);
    if (services.isEmpty()) {
        return;
    }

    Map<String, List<String>> rejectPatterns = parseJsonMapProperty(
        systemPropertyService.getProperty(WAIT_FOR_LOG_REJECT_PATTERNS)
    );

    int timeoutSeconds = parseIntProperty(WAIT_FOR_LOG_TIMEOUT, 60);
    int pollSeconds = parseIntProperty(WAIT_FOR_LOG_POLL, 2);
    boolean caseInsensitive = parseBooleanProperty(WAIT_FOR_LOG_CASE_INSENSITIVE, false);
    boolean verbose = parseBooleanProperty(WAIT_FOR_LOG_VERBOSE, false);
    int progressIntervalSeconds = parseIntProperty(WAIT_FOR_LOG_PROGRESS_INTERVAL, 0);

    System.out.println("Waiting for log patterns in services: " + services.keySet());

    // Build WaitForLogConfig using the builder
    WaitForLogConfig config = WaitForLogConfigBuilder.build(
        projectName,
        services,
        rejectPatterns,
        timeoutSeconds,
        pollSeconds,
        caseInsensitive,
        verbose,
        progressIntervalSeconds
    );

    composeService.waitForLogPatterns(config).get();
    System.out.println("All log patterns matched");
}
```

#### 12.5.3 Helper Methods

Add these helper methods to both extensions:

```java
/**
 * Parse an integer system property with a default value.
 */
private int parseIntProperty(String propertyName, int defaultValue) {
    String value = systemPropertyService.getProperty(propertyName);
    if (value == null || value.isEmpty()) {
        return defaultValue;
    }
    try {
        return Integer.parseInt(value);
    } catch (NumberFormatException e) {
        System.err.println("Warning: Invalid integer for " + propertyName + ": " + value +
                          ", using default: " + defaultValue);
        return defaultValue;
    }
}

/**
 * Parse a boolean system property with a default value.
 */
private boolean parseBooleanProperty(String propertyName, boolean defaultValue) {
    String value = systemPropertyService.getProperty(propertyName);
    if (value == null || value.isEmpty()) {
        return defaultValue;
    }
    return Boolean.parseBoolean(value);
}

/**
 * Parse a JSON map system property into Map<String, List<String>>.
 *
 * @param json JSON string in format: {"key1": ["val1", "val2"], "key2": ["val3"]}
 * @return Parsed map, or empty map if json is null/empty/invalid
 */
@SuppressWarnings("unchecked")
private Map<String, List<String>> parseJsonMapProperty(String json) {
    if (json == null || json.isEmpty()) {
        return Collections.emptyMap();
    }
    try {
        // Use Groovy's JsonSlurper for parsing
        Object parsed = new groovy.json.JsonSlurper().parseText(json);
        if (parsed instanceof Map) {
            Map<String, Object> rawMap = (Map<String, Object>) parsed;
            Map<String, List<String>> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                if (entry.getValue() instanceof List) {
                    List<String> values = new ArrayList<>();
                    for (Object item : (List<?>) entry.getValue()) {
                        values.add(String.valueOf(item));
                    }
                    result.put(entry.getKey(), values);
                }
            }
            return result;
        }
        return Collections.emptyMap();
    } catch (Exception e) {
        System.err.println("Warning: Failed to parse JSON map: " + e.getMessage());
        return Collections.emptyMap();
    }
}
```

#### 12.5.4 Required Imports

Add these imports to both `DockerComposeMethodExtension.java` and `DockerComposeClassExtension.java`:

```java
import com.kineticfire.gradle.docker.model.WaitForLogConfig;
import com.kineticfire.gradle.docker.util.WaitForLogConfigBuilder;
import java.util.HashMap;
import java.util.Map;
```

#### 12.5.5 JUnitComposeService Implementation

`JUnitComposeService` (used by the test framework extensions) must implement the `waitForLogPatterns()`
method added to the `ComposeService` interface in Section 7.

**Verification command:**
```bash
rg "class JUnitComposeService" plugin/src/main/groovy/com/kineticfire/gradle/docker/junit -A 30
```

**Expected finding**: `JUnitComposeService` likely delegates to an underlying `ComposeService` implementation
(probably `ExecLibraryComposeService`). If so, the delegation pattern should already handle the new method.

**If `JUnitComposeService` does NOT implement `waitForLogPatterns()`**, add the following delegation method:

```groovy
// In JUnitComposeService.groovy

@Override
CompletableFuture<Map<String, WaitForLogResult>> waitForLogPatterns(WaitForLogConfig config) {
    // Delegate to the underlying compose service implementation
    return delegate.waitForLogPatterns(config)
}
```

**Required imports** (add if not present):
```groovy
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import java.util.concurrent.CompletableFuture
```

**Alternative - If `JUnitComposeService` uses `@Delegate` annotation**:

If the class uses Groovy's `@Delegate` annotation pattern, no changes are needed - the delegation is automatic:
```groovy
class JUnitComposeService implements ComposeService {
    @Delegate
    private final ComposeService delegate  // Automatically delegates all interface methods
    // ...
}
```

**Verification after implementation**:
```bash
# Verify the method exists
rg "waitForLogPatterns" plugin/src/main/groovy/com/kineticfire/gradle/docker/junit

# Run unit tests to ensure delegation works
cd plugin && ./gradlew test --tests "*JUnitComposeService*"
```

#### 12.5.6 Behavior When No Wait Blocks Configured

If no `waitForRunning`, `waitForHealthy`, or `waitForLog` blocks are configured in the DSL,
the system properties won't be set, and the wait methods will return early (no-op). This is
the expected behavior - containers will be started but no readiness checks will be performed.

### 13. Execution Order

The execution order in `ComposeUpTask.performWaitIfConfigured()` is:
- `waitForRunning` -> `waitForHealthy` -> `waitForLog`

**Rationale**: This order reflects the natural startup sequence:
1. Containers must be running before health checks can pass
2. Health checks should pass before checking for application-specific log messages

### 14. Usage Documentation Updates

| File | Updates Required |
|------|------------------|
| `docs/usage/usage-docker-orch.md` | Add `waitForLog` section with examples |
| `CHANGELOG.md` | Document new `waitForLog` feature with full lifecycle support |
| `README.md` | Update feature list |

### 15. Performance Considerations

- Each poll iteration fetches complete log history (to ensure patterns that appeared early are not missed)
- Use longer `pollSeconds` for high-volume logging services
- Recommended minimum: `pollSeconds.set(2)`
- Consider `waitForHealthy` for high-volume services when possible
- The `progressIntervalSeconds` feature allows visibility into long waits without verbose per-poll logging

#### Service Name Validation

The implementation validates that all configured service names exist in the compose project at the start
of the wait loop (via `validateServicesExist()`). This provides:

1. **Early failure for typos**: A misspelled service name fails immediately with a clear error message
   listing available services, rather than timing out after 60+ seconds
2. **Better user experience**: The error message includes available service names and hints for resolution
3. **Debugging aid**: Helps identify configuration mismatches between `waitForLog` and docker-compose.yml

**Example error for unknown service:**
```
Service(s) not found in compose project 'myproject': [appp].
Available services: [app, db, redis]

Hint: Check for typos in service names in your waitForLog configuration.
```

#### Memory Usage Warning

**Important**: The `fetchServiceLogs()` method retrieves ALL container logs (`tailLines = 0`) on each poll
iteration. For containers with extremely high log volume (thousands of lines per second over extended
periods), this can cause memory pressure.

**Mitigations:**
1. Use longer `pollSeconds` values (e.g., 5-10 seconds) to reduce fetch frequency
2. Choose patterns that appear early in startup to minimize total wait time
3. For high-volume services, prefer `waitForHealthy` when health checks are available
4. Monitor JVM heap usage during integration tests with verbose logging services

**Future Enhancement**: Consider adding a `maxLogLines` property in a future release to cap log retrieval.
This would trade off the ability to match very early patterns for bounded memory usage. For now, the
DSL/User Description documents this limitation in the "Performance Considerations" section.

**Gradle Daemon Considerations**: If running repeated integration tests with high-volume logging services,
logs accumulate in the Gradle daemon's heap across test runs. Consider using `--no-daemon` for CI builds
or restarting the daemon periodically (`./gradlew --stop`) to reclaim memory between test runs.

#### Non-Existent Compose Project Handling

**Scenario**: What happens if `waitForLog` is configured but `composeUp` wasn't called or the compose
project doesn't exist?

**Behavior**: The `validateServicesExist()` method calls `getComposeProjectServices()` which executes
`docker compose ps --format json -a`. If the project doesn't exist:
- Docker Compose returns an empty result (no services)
- `getComposeProjectServices()` returns an empty set
- `validateServicesExist()` detects the empty set and provides a **specific error message** for this case

**Example error (empty compose project):**
```
No services found in compose project 'myproject'.
This typically means the compose project doesn't exist or hasn't been started.

Hint: Ensure 'composeUp' task has been executed before 'waitForLog' runs.
      Verify the compose project name matches your docker-compose.yml configuration.
```

This implementation provides a clear, actionable error message that distinguishes between:
1. **Empty compose project**: No services found at all (suggests composeUp not called)
2. **Missing specific services**: Some services found but configured service names don't match (suggests typo)

### 16. Known Limitations

This section documents known limitations of the `waitForLog` implementation. These are intentional design
decisions or constraints that may affect users in specific scenarios.

#### Container Restart During Wait

**Limitation**: If a container crashes and restarts during the wait period (e.g., due to `restart: always`
policy in docker-compose.yml), the log output may reset but the pattern match state is NOT reset.

**Behavior**: Previously matched patterns remain marked as "matched" even if the container restarts and
those log lines are no longer present. This means:
- If Pattern A matched at t=5s, then the container restarts at t=10s, Pattern A is still considered matched
- The wait may succeed even though the final running container never produced the matched log line
- This could mask container instability issues

**Rationale**: Resetting match state on container restart would require:
1. Detecting container restarts (comparing container IDs across polls)
2. Clearing match state for the restarted service
3. Potentially re-fetching and re-matching all patterns

This complexity was deferred to keep the initial implementation simpler.

**Workaround**: If container stability is a concern:
1. Use `rejectPatterns` to detect crash indicators in logs
2. Use `waitForHealthy` which re-checks health status on each poll
3. Consider removing `restart: always` during test execution

**Future Enhancement**: Consider adding a `resetOnRestart.set(true)` option that detects container restarts
and clears match state.

#### No Maximum Log Size Limit

**Limitation**: The `fetchServiceLogs()` method retrieves ALL container logs on each poll iteration.
There is no configurable upper limit on log size.

**Impact**: For containers producing extremely high log volume (thousands of lines per second), this can
cause:
- Increased memory usage during log parsing
- Longer poll times
- Potential OutOfMemoryError in extreme cases

**Workaround**:
1. Use longer `pollSeconds` values to reduce fetch frequency
2. Choose patterns that appear early in startup
3. For high-volume services, prefer `waitForHealthy` when possible
4. Ensure adequate JVM heap size for the Gradle daemon

**Future Enhancement**: Consider adding a `maxLogLines.set(10000)` property to cap log retrieval. This
would trade off the ability to match very early patterns for bounded memory usage.

#### Interruption Handling

**Limitation**: If the wait operation is interrupted (e.g., Ctrl+C during build, Gradle daemon termination),
the containers remain running. There is no automatic cleanup in the interrupt handler.

**Behavior**: On interrupt:
1. The `InterruptedException` is caught
2. Thread interrupt status is preserved
3. A `ComposeServiceException` is thrown
4. Containers continue running

**Rationale**: Cleanup on interrupt is handled by:
1. Gradle's `finalizedBy` task dependency (composeDown runs after test failure)
2. The user running `docker compose down` manually if needed

Adding cleanup to the interrupt handler could cause issues:
- Race conditions with Gradle's task finalization
- Unexpected behavior if interrupt was intentional (user wants containers for debugging)

**Workaround**: If containers are left running after an interrupted build:
```bash
docker compose -p <project-name> down
# or
docker ps -a  # Find leftover containers
docker rm -f <container-ids>
```

### 17. MapProperty Serialization Fallback Strategies

This section documents fallback strategies if `MapProperty<String, List<String>>` fails to serialize correctly
for Gradle's configuration cache. The pre-implementation verification (see checklist) should identify this
issue before implementation begins.

#### Background

The `waitForLog` feature requires storing a map where:
- **Key**: Service name (String)
- **Value**: List of regex pattern strings (List<String>)

This nested generic type (`MapProperty<String, List<String>>`) is a **new pattern** in this codebase. All existing
`MapProperty` usages are simple `MapProperty<String, String>` key-value pairs.

**Risk Assessment**:
| Risk | Likelihood | Impact |
|------|------------|--------|
| Instantiation fails for abstract property | LOW | HIGH |
| Configuration cache serialization fails | LOW-MEDIUM | HIGH |
| Deserialization produces incorrect data | LOW | HIGH |

#### Option A: JSON String Encoding (RECOMMENDED FALLBACK)

Replace `MapProperty<String, List<String>>` with `MapProperty<String, String>` where the value is a JSON-encoded
list of patterns.

**WaitForLogSpec changes**:
```groovy
// BEFORE (nested generic):
private final MapProperty<String, List<String>> waitForServices

// AFTER (JSON-encoded):
private final MapProperty<String, String> waitForServicesJson

// Add helper method for DSL convenience:
void waitForServices(Map<String, List<String>> services) {
    def jsonBuilder = new groovy.json.JsonBuilder()
    services.each { serviceName, patterns ->
        waitForServicesJson.put(serviceName, jsonBuilder(patterns).toString())
    }
}
```

**ComposeUpTask changes**:
```groovy
// BEFORE:
@Input
@Optional
abstract MapProperty<String, List<String>> getWaitForLogServices()

// AFTER:
@Input
@Optional
abstract MapProperty<String, String> getWaitForLogServicesJson()

// In performWaitForLog(), parse JSON before use:
def services = waitForLogServicesJson.get().collectEntries { serviceName, patternsJson ->
    def patterns = new groovy.json.JsonSlurper().parseText(patternsJson) as List<String>
    [(serviceName): patterns]
}
```

**Pros**:
- Minimal code changes
- Proven serialization (String is always safe)
- Already used for system property propagation in TestIntegrationExtension

**Cons**:
- Runtime JSON parsing overhead (negligible for typical use)
- Less type-safe at compile time
- Slightly more complex DSL implementation

#### Option B: Custom Holder Class with @Nested

Create a dedicated class to hold per-service patterns, using `@Nested` annotation for Gradle to track as
structured input.

**New class - ServicePatternSpec**:
```groovy
class ServicePatternSpec {
    final String serviceName
    final ListProperty<String> patterns

    @Inject
    ServicePatternSpec(String serviceName, ObjectFactory objects) {
        this.serviceName = serviceName
        this.patterns = objects.listProperty(String)
    }
}
```

**WaitForLogSpec changes**:
```groovy
// BEFORE:
private final MapProperty<String, List<String>> waitForServices

// AFTER:
private final ListProperty<ServicePatternSpec> servicePatterns

void service(String name, @DelegatesTo(ServicePatternSpec) Closure closure) {
    def spec = objectFactory.newInstance(ServicePatternSpec, name)
    closure.delegate = spec
    closure.call()
    servicePatterns.add(spec)
}
```

**DSL usage changes**:
```groovy
// BEFORE:
waitForLog {
    waitForServices.set([
        'app': ['Started Application', 'Listening on port'],
        'db': ['ready for connections']
    ])
}

// AFTER:
waitForLog {
    service('app') {
        patterns.addAll('Started Application', 'Listening on port')
    }
    service('db') {
        patterns.add('ready for connections')
    }
}
```

**Pros**:
- Type-safe
- Gradle-idiomatic with `@Nested` support
- Better IDE completion

**Cons**:
- More verbose DSL
- More classes to maintain
- Significant refactoring required

#### Option C: Flatten Completely

Use separate properties for each aspect, with service names as a list and patterns as a concatenated string
with delimiter.

**Not recommended** due to:
- DSL becomes unwieldy for many services
- Parsing complexity increases
- Error-prone delimiter handling

#### Recommendation

If `MapProperty<String, List<String>>` fails configuration cache verification:

1. **Implement Option A (JSON String)** - It requires minimal changes and the pattern is already proven
   in `TestIntegrationExtension` for system property propagation.

2. **Do NOT attempt Option B or C** unless Option A also fails (extremely unlikely).

3. **Document the limitation** in release notes:
   ```markdown
   ### Implementation Notes
   - Service patterns are stored as JSON-encoded strings internally for configuration cache compatibility
   - This is transparent to DSL users - the Map<String, List<String>> API remains unchanged
   ```

#### Verification After Fallback Implementation

If a fallback is implemented, re-run all Phase 4 configuration cache tests:

```bash
# Full verification suite
cd plugin && ./gradlew clean test functionalTest --configuration-cache
cd plugin && ./gradlew clean test functionalTest --configuration-cache  # Must reuse cache

# Integration test verification
cd plugin && ./gradlew -Pplugin_version=1.0.0 build publishToMavenLocal
cd plugin-integration-test && ./gradlew cleanAll integrationTest --configuration-cache
```
