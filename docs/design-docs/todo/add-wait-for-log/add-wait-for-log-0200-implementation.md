# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Implementation

## Purpose

Define the implementation plan to achieve the desired functionality.

This document should NOT include tests -- those are deferred to other documents.

The overview of the design is at `add-wait-for-log-0000-overview.md`.
The DSL / user description is at `add-wait-for-log-0100-dsl-user-description.md`.

## Checklist

### Pre-Implementation Verification

- [ ] Read and understand the existing `waitForHealthy` and `waitForRunning` implementations
- [ ] Verify Guava dependency in `plugin/build.gradle`:
  ```bash
  rg "libs.guava" plugin/build.gradle
  ```
  If not found, add `implementation libs.guava` to dependencies block
- [ ] Confirm `ServiceLogger` interface methods (**verified**: `info()`, `debug()`, `warn()`, `error()` only - NO
      `lifecycle()`)
- [ ] Confirm `ComposeStackSpec.getName()` exists (**verified**: line 46-48)
- [ ] Confirm `TestIntegrationExtension.setComprehensiveSystemProperties()` signature (**verified**: line 179-217)
- [ ] Review existing wiring patterns in `GradleDockerPlugin`:
  ```bash
  rg "waitForHealthy|waitForRunning" plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy -C 3
  ```

### Phase 0: Prerequisite Changes

- [ ] Modify `LogsConfig.groovy` (line 32 only - minimal change):
  - [ ] Remove `Math.max(1, tailLines)` constraint (change to just `this.tailLines = tailLines`)
  - [ ] (Optional) Add `hasLimitedTail()` helper method for readability
  - [ ] Update Javadoc to document that `0` (or any non-positive value) means "all logs"
- [ ] (Optional) Modify `ExecLibraryComposeService.buildLogsCommand()`:
  - [ ] Replace `if (config.tailLines > 0)` with `if (config.hasLimitedTail())` for clarity
  - [ ] **Note**: This is optional - existing code already handles the condition correctly
- [ ] Write unit tests for LogsConfig changes:
  - [ ] Test `tailLines = 0` results in `tailLines` being `0` (all logs)
  - [ ] Test `tailLines = -1` results in `tailLines` being `-1` (all logs)
  - [ ] Test `tailLines = 100` results in `tailLines` being `100` (existing behavior preserved)
  - [ ] Test `tailLines = 1` results in `tailLines` being `1` (minimum positive value)
  - [ ] If `hasLimitedTail()` helper added:
    - [ ] Test `hasLimitedTail()` returns `false` for `tailLines = 0`
    - [ ] Test `hasLimitedTail()` returns `false` for `tailLines = -1`
    - [ ] Test `hasLimitedTail()` returns `true` for `tailLines = 1`
    - [ ] Test `hasLimitedTail()` returns `true` for `tailLines = 100`
- [ ] Run existing tests to verify no regressions (especially `LogsConfigTest` and `ExecLibraryComposeServiceTest`)

### Phase 1: Core Components

- [ ] Create `WaitForLogSpec.groovy` (Section 1)
  - [ ] Verify conventions are set correctly
  - [ ] Write unit tests
- [ ] Create `WaitForLogConfig.groovy` (Section 2)
  - [ ] Verify immutability
  - [ ] Write unit tests
- [ ] Create `WaitForLogResult.groovy` (Section 3)
  - [ ] Write unit tests
- [ ] Create `LogPatternMatcher.groovy` (Section 4)
  - [ ] Test all pure functions
  - [ ] Achieve 100% branch coverage
- [ ] Create `WaitForLogConfigBuilder.groovy` (Section 5)
  - [ ] Test validation logic
  - [ ] Test error messages

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
- [ ] Modify `TestIntegrationExtension.groovy` (Section 13)
  - [ ] Add system property propagation
  - [ ] Write unit tests

### Phase 3: Functional Tests

- [ ] Add functional tests for `waitForLog` DSL configuration
- [ ] Add functional tests for validation error messages
- [ ] Add functional tests for property wiring
- [ ] Verify all functional tests pass

### Phase 4: Configuration Cache Verification

- [ ] Run with `--configuration-cache` flag
- [ ] Verify second run reuses cached configuration
- [ ] If MapProperty serialization fails, implement JSON fallback

### Phase 5: Integration Tests

- [ ] Create integration test scenario for `waitForLog`
- [ ] Test with real Docker containers
- [ ] Test timeout behavior
- [ ] Test reject pattern behavior
- [ ] Test verbose logging
- [ ] Test progress interval logging
- [ ] Verify no lingering containers

### Phase 6: Documentation

- [ ] Update `docs/usage/usage-docker-orch.md`
- [ ] Update `CHANGELOG.md` with new feature and breaking change
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

**Optional enhancement** - Add a helper method for clarity (recommended but not required):

```groovy
/**
 * Returns true if tailLines should be applied (positive value).
 * When false, the --tail flag should be omitted to fetch all logs.
 */
boolean hasLimitedTail() {
    return tailLines > 0
}
```

If `hasLimitedTail()` is added, update `buildLogsCommand()` to use it for improved readability:

```groovy
// BEFORE:
if (config.tailLines > 0) {

// AFTER (optional, for clarity):
if (config.hasLimitedTail()) {
```

**Note**: The existing `buildLogsCommand()` already handles the condition correctly. Adding `hasLimitedTail()` improves
code readability but is not strictly necessary for functionality.

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

   If not found, add to `plugin/build.gradle` in the `dependencies` block:
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
3. **Validation Timing**: Validation occurs in DSL block methods (not during provider resolution) and in `@TaskAction`
4. **Task Cacheability**: Tasks with external side effects use `@UntrackedTask` (already applied to `ComposeUpTask`)
5. **Service Injection**: Use `@Inject` abstract getters for services
6. **Flattened Properties**: Task inputs use individual `@Input` properties, not nested spec references

### 1. WaitForLogSpec Class

The spec class provides the DSL configuration interface. This is a non-abstract class that uses
`ObjectFactory` to create properties explicitly, which allows conventions to be set in the
constructor. Create at `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/WaitForLogSpec.groovy`:

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
    WaitForLogSpec(ObjectFactory objects) {
        // Create properties using ObjectFactory
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

**Configuration Cache Note**: This class stores `java.util.regex.Pattern` objects which implement `Serializable`.
Pattern serialization is supported by the JVM, so this class is configuration-cache compatible. However, if
serialization issues arise during configuration cache testing, consider storing pattern strings instead and
compiling them at execution time.

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
     */
    int getTotalWaitAttempts() {
        return Math.max(1, (timeout.toSeconds() / pollInterval.toSeconds()).intValue())
    }

    /**
     * Get list of all services being monitored.
     */
    List<String> getServices() {
        return new ArrayList<>(servicePatterns.keySet())
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
 * <p>Validation is performed here (during task execution) rather than during
 * configuration to maintain Gradle 9/10 configuration cache compatibility.</p>
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

        // Validate waitForServices
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
The existing `ComposeStackSpec` has:
1. Constructor injection: `ComposeStackSpec(String name, ObjectFactory objectFactory)`
2. `getName()` method returning the stack name (line 46-48)
3. `objectFactory` field available for creating nested specs
4. Existing pattern for `waitForHealthy` and `waitForRunning` DSL methods

**Required Imports** (add to existing imports in `ComposeStackSpec.groovy`):

```groovy
// Complete imports to add for waitForLog functionality
// Note: Action and GradleException are likely already imported
import com.kineticfire.gradle.docker.spec.WaitForLogSpec
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.provider.Property

// Ensure ObjectFactory is available (verify this exists or add if needed):
// ComposeStackSpec should have an injected ObjectFactory for creating nested specs.
// Check existing waitForHealthy/waitForRunning implementation for the pattern used.
@Inject
abstract ObjectFactory getObjectFactory()

// Add property (no convention - isPresent() returns false until explicitly set)
// This matches the pattern used by waitForHealthy and waitForRunning properties
abstract Property<WaitForLogSpec> getWaitForLog()

/**
 * Property Convention Note:
 * The waitForLog property has NO convention set. This means:
 * - waitForLog.isPresent() returns false by default
 * - The property only has a value after the waitForLog{} DSL block is configured
 * - This is intentional: it allows checking if the user configured a waitForLog block
 * - This pattern matches waitForHealthy and waitForRunning in the existing codebase
 */

// Add DSL methods
void waitForLog(@DelegatesTo(WaitForLogSpec) Closure closure) {
    def waitForLogSpec = objectFactory.newInstance(WaitForLogSpec)
    closure.delegate = waitForLogSpec
    closure.call()
    // Pass stack name explicitly (getName() returns the stack name in ComposeStackSpec)
    validateWaitForLogSpec(waitForLogSpec, getName())
    waitForLog.set(waitForLogSpec)
}

void waitForLog(Action<WaitForLogSpec> action) {
    def waitForLogSpec = objectFactory.newInstance(WaitForLogSpec)
    action.execute(waitForLogSpec)
    // Pass stack name explicitly (getName() returns the stack name in ComposeStackSpec)
    validateWaitForLogSpec(waitForLogSpec, getName())
    waitForLog.set(waitForLogSpec)
}

/**
 * Validates that a WaitForLogSpec has valid configuration.
 *
 * <p>This validation occurs during DSL configuration (not provider resolution)
 * to provide early feedback on configuration errors.</p>
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

    // Validate each service has at least one pattern
    spec.waitForServices.get().each { serviceName, patterns ->
        if (patterns == null || patterns.isEmpty()) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block for compose stack '${stackName}': " +
                "Pattern list for service '${serviceName}' cannot be empty.\n" +
                "Each service must have at least one pattern to match."
            )
        }
    }

    // Note: Regex pattern validation is deferred to task execution time
    // to avoid potential configuration cache issues with Pattern compilation
}
```

### 7. ComposeService Interface Extension

Add the `waitForLogPatterns()` method to `ComposeService`. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ComposeService.groovy`:

```groovy
// Add import
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult

// Add method
/**
 * Wait for log patterns to appear in service logs.
 *
 * @param config Wait-for-log configuration with patterns, timeout, etc.
 * @return CompletableFuture with results per service
 * @throws ComposeServiceException if timeout, reject pattern match, or service crash
 */
CompletableFuture<Map<String, WaitForLogResult>> waitForLogPatterns(WaitForLogConfig config)
```

### 7.5 Reference: Existing Components Used (No Changes Required)

> **Note**: This section documents existing codebase components that will be used by the `waitForLog`
> implementation. These are shown for reference only - **no changes are required** to these components
> (except for the `LogsConfig` prerequisite change documented in Phase 0).

The `waitForLogPatterns()` implementation requires the ability to fetch container logs. Both
`LogsConfig` and `captureLogs()` **already exist** in the codebase.

**Existing LogsConfig Model Class** (`plugin/src/main/groovy/com/kineticfire/gradle/docker/model/LogsConfig.groovy`):

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
        this.tailLines = Math.max(1, tailLines)
        this.follow = follow
        this.outputFile = outputFile
    }

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

**Existing ExecLibraryComposeService Implementation** (excerpt):

The implementation already exists with the `buildLogsCommand()` pure function pattern. The new
`waitForLogPatterns()` method and its helper methods (including `getRecentLogs()`) are defined
in **Section 8** below.

### 8. ExecLibraryComposeService Implementation

Implement `waitForLogPatterns()` in `ExecLibraryComposeService`. The implementation follows the
existing patterns for `waitForServices()`.

**Required Imports** (add to `ExecLibraryComposeService.groovy`):

```groovy
// Complete imports to add for waitForLog functionality
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import com.kineticfire.gradle.docker.util.LogPatternMatcher
import java.util.regex.Pattern
import java.util.Set
import java.util.HashSet
import java.util.Map
import java.util.List
import java.util.concurrent.CompletableFuture

// JSON parsing for isServiceRunning() method (part of Groovy runtime, no additional dependencies)
import groovy.json.JsonSlurper
```

**Thread Safety Note**: The `waitForLogPatterns()` implementation runs entirely within a single thread context
(inside `CompletableFuture.supplyAsync()`). The mutable `HashSet` and `HashMap` used for tracking match state are
not shared across threads, so no synchronization is required.

**Dependencies Already Verified** (see "Existing Dependencies Verification" section above):
- `timeService: TimeService` - Exists
- `processExecutor: ProcessExecutor` - Exists
- `getComposeCommand()` - Exists
- `serviceLogger: ServiceLogger` - Exists

**Logger Usage Clarification**:

| Context | Logger | Methods | Example |
|---------|--------|---------|---------|
| Service layer (`ExecLibraryComposeService`) | `serviceLogger` (injected `ServiceLogger`) | `info()`, `debug()`, `warn()`, `error()` | `serviceLogger.info("[waitForLog] ...")` |
| Task layer (`ComposeUpTask`) | `logger` (Gradle's inherited `Logger`) | `lifecycle()`, `info()`, `debug()`, etc. | `logger.lifecycle("Waiting for ...")` |

This distinction is intentional:
- **Service layer** uses `ServiceLogger` for testability (can be mocked in unit tests)
- **Task layer** uses Gradle's `logger` for Gradle-native lifecycle messages

The implementation in this section uses `serviceLogger.info()` which is correct for the service layer.

**Implementation:**

**IMPORTANT**: The `ServiceLogger` interface only supports single-String methods (`info(String)`, `debug(String)`,
etc.) with NO parameterized logging. Use Groovy string interpolation (`"${variable}"`) instead of SLF4J-style
placeholders (`{}`).

**Constants** (add at the top of `ExecLibraryComposeService.groovy` class):

```groovy
/** Number of recent log lines to include in error messages for diagnostics */
private static final int RECENT_LOG_LINES_FOR_ERROR = 10
```

**Main Method** (refactored into smaller, focused methods per code quality standards - methods <= 30-40 lines):

See the full implementation code in the overview document section 8. The implementation includes:

- `waitForLogPatterns()` - Main entry point
- `executeWaitForLogPatterns()` - Core logic
- `logWaitStart()` - Log start message
- `initializeMatchState()` - Initialize tracking state
- `pollForPatterns()` - Main polling loop
- `checkAllServices()` - Check all services for patterns
- `checkServicePatterns()` - Check patterns for single service
- `verifyServiceRunning()` - Verify container is running
- `fetchServiceLogs()` - Fetch logs for a service
- `checkForRejectPatterns()` - Check for reject patterns
- `updateServiceMatches()` - Update pattern matches
- `handleAllServicesReady()` - Handle success
- `handleTimeout()` - Handle timeout
- `logVerbosePollingStart()` - Verbose logging helper
- `logVerboseServiceReady()` - Verbose logging helper
- `logPeriodicProgress()` - Periodic progress logging
- `isServiceRunning()` - Check if container is running
- `buildResults()` - Build result objects
- `getRecentLogs()` - Get recent logs for error reporting
- `logServiceProgress()` - Log service progress
- `logProgressSummary()` - Log progress summary
- `buildTimeoutErrorMessage()` - Build timeout error message
- `buildRejectErrorMessage()` - Build reject error message
- `buildCrashErrorMessage()` - Build crash error message

### 9. ComposeServiceException Extension

Add new error types to `ComposeServiceException`. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/exception/ComposeServiceException.groovy`:

**Add new values to ErrorType enum:**

```groovy
// NEW: Add these error types for waitForLog feature
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
import java.time.Duration
```

**Add Flattened Input Properties** (add after existing `waitForRunning*` properties):

```groovy
@Input @Optional abstract MapProperty<String, List<String>> getWaitForLogServices()
@Input @Optional abstract MapProperty<String, List<String>> getWaitForLogRejectPatterns()
@Input @Optional abstract Property<Integer> getWaitForLogTimeoutSeconds()
@Input @Optional abstract Property<Integer> getWaitForLogPollSeconds()
@Input @Optional abstract Property<Boolean> getWaitForLogCaseInsensitive()
@Input @Optional abstract Property<Boolean> getWaitForLogVerbose()
@Input @Optional abstract Property<Integer> getWaitForLogProgressIntervalSeconds()
```

**Update `performWaitIfConfigured` Method**

Change execution order from `waitForHealthy` -> `waitForRunning` to:
`waitForRunning` -> `waitForHealthy` -> `waitForLog`

### 11. Plugin Wiring (GradleDockerPlugin)

Wire the `WaitForLogSpec` properties to `ComposeUpTask` inputs using conditional wiring pattern
(matching existing `waitForHealthy` and `waitForRunning` wiring).

### 12. Execution Order Change (Breaking Change)

The execution order is updated to: `waitForRunning` -> `waitForHealthy` -> `waitForLog`

**CHANGELOG entry:**
```markdown
### Changed (Breaking)
- **Wait block execution order changed**: The execution order for wait blocks is now
  `waitForRunning` -> `waitForHealthy` -> `waitForLog` (previously `waitForHealthy` -> `waitForRunning`).
```

### 13. Test Framework Extension Integration

Update `TestIntegrationExtension.setComprehensiveSystemProperties()` to propagate `waitForLog` configuration
via system properties for test framework extensions to consume.

**Lifecycle Support Limitation:**
- `Lifecycle.CLASS`: Fully supported
- `Lifecycle.METHOD`: Not supported in initial release

### 14. Usage Documentation Updates

| File | Updates Required |
|------|------------------|
| `docs/usage/usage-docker-orch.md` | Add `waitForLog` section with examples |
| `CHANGELOG.md` | Document new feature and breaking change |
| `README.md` | Update feature list |

### 15. Performance Considerations

- Each poll iteration fetches complete log history
- Use longer `pollSeconds` for high-volume logging services
- Recommended minimum: `pollSeconds.set(2)`
- Consider `waitForHealthy` for high-volume services when possible
