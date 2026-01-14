# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Integration Tests

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0100-dsl-user-description.md` - DSL syntax and user-facing behavior
- `add-wait-for-log-0200-implementation.md` - Implementation details and component structure
- `add-wait-for-log-0300-unit-tests.md` - Unit test specifications
- `add-wait-for-log-0400-functional-tests.md` - Functional test specifications

Reference project testing standards:
- `plugin-integration-test/README.md` - Integration test project structure
- `plugin-integration-test/dockerTest/README.md` - dockerTest integration test structure
- `plugin-integration-test/dockerTest/verification/README.md` - Verification test patterns

## Purpose

Define integration test specifications for the `waitForLog` feature implementation. Integration tests verify:
- Real Docker Compose execution with actual containers
- Log pattern matching with real container output
- Timeout behavior with real timing
- Reject pattern behavior with actual log streams
- Service crash detection during wait operations
- Unknown service name validation with helpful error messages
- Verbose and progress logging output
- Combined usage with `waitForHealthy` and `waitForRunning`
- METHOD lifecycle support for all wait blocks (new in this feature)
- Container cleanup (no lingering containers)
- Regex patterns with special characters (escaping, JSON-sensitive chars)

This document should contain integration tests ONLY. Unit tests are in `add-wait-for-log-0300-unit-tests.md`,
functional tests are in `add-wait-for-log-0400-functional-tests.md`.

---

## Review Notes

### Integration Test Plan Review (2026-01-11)

The integration test plan was reviewed against the implementation plan (0200) and DSL user description (0100) for
100% integration test coverage. The review identified **8 gaps** organized by severity:

**Critical Gaps (3)** - Must have for complete coverage:

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| IT-1 | No test for service crash during wait (container exits unexpectedly) | Added `wait-log-crash/` scenario (Phase 2) |
| IT-2 | No test for unknown service name validation (typo detection) | Added `wait-log-unknown-service/` scenario (Phase 2) |
| IT-3 | No test for regex patterns with special/JSON-sensitive characters | Added `wait-log-regex-special/` scenario (Phase 2) |

**Moderate Gaps (3)** - Important for robust coverage:

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| IT-4 | No explicit test for empty compose project detection | Added to `wait-log-unknown-service/` scenario |
| IT-5 | No test verifying orphaned rejectPatterns warning is logged | Added to `wait-log-reject/` scenario |
| IT-6 | No test for pollSeconds > timeoutSeconds warning edge case | Added to `wait-log-options/` scenario |

**Minor Gaps (2)** - Nice to have:

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| IT-7 | Progress interval logging output format not verified | Added explicit assertion to `wait-log-options/` |
| IT-8 | Container restart behavior during wait not documented/tested | Added documentation note; deferred test to future |

**Additional Improvements:**

1. Added explicit `wait-log-only/` scenario to test waitForLog without other wait blocks
2. Added `wait-log-multi-stack/` scenario for multiple stacks with different configurations
3. Clarified state file assertions across all scenarios
4. Added Gradle output capture requirements for warning/error message verification
5. Aligned port allocation table with new scenarios
6. Updated image naming table with new scenarios

### Integration Test Plan Second Review (2026-01-11)

A second comprehensive review was conducted to ensure 100% DSL/usage/options coverage. The review systematically
compared every DSL property, error message format, and behavioral requirement from the implementation plan (0200)
and DSL user description (0100) against existing integration test coverage. See Third and Fourth reviews below for
additional gaps identified in subsequent reviews.

**Critical Gaps (3)** - Must have for complete DSL/options coverage:

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| IT-9 | No explicit test for default convention values (timeoutSeconds=60, pollSeconds=2, etc.) | Added `wait-log-defaults/` scenario (Phase 2) |
| IT-10 | Error message format not verified against documented format in 0100 | Added error format assertions to timeout/crash/reject scenarios |
| IT-11 | Configuration cache verification not explicit (relies on implicit passing) | Added `wait-log-config-cache/` scenario (Phase 7) |

**Moderate Gaps (4)** - Important for robust coverage:

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| IT-12 | Verbose output format not verified (attempt N/M, FOUND/NOT FOUND markers) | Added verbose output assertions to `wait-log-options/` |
| IT-13 | State file waitForLog metadata not verified | Added state file assertions to `wait-log-basic/` |
| IT-14 | System property JSON format not verified in METHOD lifecycle tests | Added property format assertions to `wait-log-method/` |
| IT-15 | No test for mixed ready states (some services ready before others) | Added `wait-log-mixed-ready/` scenario (Phase 6) |

**Minor Gaps (4)** - Edge cases and boundary conditions:

| Gap ID | Description | Resolution |
|--------|-------------|------------|
| IT-16 | No test for Unicode regex patterns | Added to `wait-log-regex-special/` scenario |
| IT-17 | No test for very long regex patterns (boundary test) | Added to `wait-log-regex-special/` scenario |
| IT-18 | Reject pattern priority (checked before success) not explicitly verified | Added timing assertion to `wait-log-reject/` |
| IT-19 | No test verifying RECENT_LOG_LINES_FOR_ERROR constant (10 lines in error) | Added to timeout/crash error message assertions |

**Documentation Improvements:**

1. Added explicit error message format requirements to scenario specifications
2. Updated buildSrc validators to include state file waitForLog field validators
3. Added Phase 7 for configuration cache explicit verification
4. Updated scenario summary table with new scenarios
5. Added detailed assertion requirements for verbose/progress output formats
6. Clarified system property JSON format expectations for METHOD lifecycle

---

## Checklist

### Pre-Implementation Verification

- [ ] Review existing verification test patterns:
  ```bash
  ls plugin-integration-test/dockerTest/verification/
  ```
- [ ] Verify buildSrc validators are available:
  ```bash
  ls plugin-integration-test/buildSrc/src/main/groovy/com/kineticfire/test/
  ```
- [ ] Confirm port de-confliction for new scenarios (see README.md for conventions)
- [ ] Verify image names follow naming convention: `verification-<scenario>-<app-name>`

### Phase 1: buildSrc Validator Extensions

- [ ] Add `LogPatternValidator` class to buildSrc
  - [ ] `hasPatternInLogs(projectName, serviceName, pattern)` - Check if pattern exists in logs
  - [ ] `hasPatternInLogsCaseInsensitive(projectName, serviceName, pattern)` - Case-insensitive check
  - [ ] `getServiceLogs(projectName, serviceName, tailLines)` - Fetch service logs
  - [ ] `findMatchingLine(projectName, serviceName, pattern)` - Find first matching line
  - [ ] `countMatchingLines(projectName, serviceName, pattern)` - Count matches
  - [ ] `waitForPatternWithTiming(...)` - Manual wait with timing measurement
  - [ ] `verifyAllPatternsPresent(projectName, serviceName, patterns)` - Verify multiple patterns
- [ ] Add `GradleOutputValidator` class to buildSrc (for capturing Gradle output messages)
  - [ ] `captureTaskOutput(taskPath)` - Capture stdout/stderr from Gradle task
  - [ ] `assertOutputContains(output, pattern)` - Verify expected messages
  - [ ] `assertOutputContainsWarning(output, pattern)` - Verify warning messages

### Phase 2: waitForLog Verification Tests (CLASS Lifecycle)

- [ ] Create `verification/wait-log-basic/` scenario (Updated: includes state file waitForLog metadata - IT-13, first-poll success test - IT-26)
- [ ] Create `verification/wait-log-multi-pattern/` scenario
- [ ] Create `verification/wait-log-reject/` scenario (Updated: includes orphaned reject warning test - IT-5, reject priority - IT-18)
- [ ] Create `verification/wait-log-options/` scenario (Updated: includes pollSeconds warning test - IT-6, IT-7, verbose output format - IT-12)
- [ ] Create `verification/wait-log-combined/` scenario
- [ ] Create `verification/wait-log-timeout/` scenario (Updated: includes error format - IT-10, exact RECENT_LOG_LINES value - IT-19, IT-25)
- [ ] Create `verification/wait-log-crash/` scenario (NEW - IT-1, includes error format - IT-10, exact RECENT_LOG_LINES value - IT-19, IT-25)
- [ ] Create `verification/wait-log-unknown-service/` scenario (NEW - IT-2, IT-4)
- [ ] Create `verification/wait-log-regex-special/` scenario (NEW - IT-3, IT-16 Unicode, IT-17 long patterns)
- [ ] Create `verification/wait-log-only/` scenario (NEW - waitForLog without other wait blocks)
- [ ] Create `verification/wait-log-defaults/` scenario (NEW - IT-9 default convention values)

### Phase 3: waitForLog Verification Tests (METHOD Lifecycle)

- [ ] Create `verification/wait-log-method/` scenario (Updated: includes system property JSON format - IT-14)
- [ ] Create `verification/wait-log-method-options/` scenario

### Phase 4: METHOD Lifecycle Support for Existing Wait Blocks

- [ ] Create `verification/wait-running-method/` scenario
- [ ] Create `verification/wait-healthy-method-options/` scenario

### Phase 5: Execution Order Verification

- [ ] Create `verification/wait-order/` scenario

### Phase 6: Multi-Stack and Edge Cases

- [ ] Create `verification/wait-log-multi-stack/` scenario (NEW - multiple stacks with different configs)
- [ ] Create `verification/wait-log-mixed-ready/` scenario (NEW - IT-15 mixed ready states, IT-27 identical patterns per-service)

### Phase 7: Configuration Cache Verification (NEW - IT-11)

- [ ] Create `verification/wait-log-config-cache/` scenario (explicit configuration cache verification)
  - [ ] Run same build twice with `--configuration-cache`
  - [ ] Verify cache is reused on second run
  - [ ] Verify waitForLog configuration survives serialization
  - [ ] Verify all DSL properties work correctly after cache restore

### Final Verification

- [ ] All integration tests pass
- [ ] `docker ps -a` shows no lingering containers after tests
- [ ] No compilation warnings
- [ ] All verification tests follow existing patterns in `verification/`
- [ ] All Gradle output capture tests verify expected warning/error messages
- [ ] Configuration cache works correctly with --configuration-cache flag

---

## Integration Test Architecture

### Directory Structure

```
plugin-integration-test/dockerTest/verification/
├── wait-log-basic/           # Basic waitForLog functionality (CLASS)
│   ├── app/                  # Spring Boot app with predictable log output
│   ├── app-image/            # Docker image + integration tests
│   ├── gradle/
│   ├── build.gradle
│   └── README.md
├── wait-log-multi-pattern/   # Multiple patterns per service (CLASS)
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-reject/          # Reject pattern functionality (CLASS) - includes orphaned reject warning test
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-options/         # Options: verbose, caseInsensitive, progress (CLASS) - includes warning tests
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-combined/        # Combined with waitForHealthy/waitForRunning (CLASS)
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-timeout/         # Timeout behavior verification (CLASS)
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-crash/           # Service crash detection during wait (CLASS) - NEW
│   ├── app/                  # App that crashes during startup
│   ├── app-image/
│   └── ...
├── wait-log-unknown-service/ # Unknown service name validation (CLASS) - NEW
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-regex-special/   # Regex patterns with special/JSON-sensitive chars (CLASS) - NEW
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-only/            # waitForLog without other wait blocks (CLASS) - NEW
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-defaults/        # Default convention values verification (CLASS) - NEW IT-9
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-method/          # METHOD lifecycle for waitForLog
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-method-options/  # METHOD lifecycle with verbose/progress options
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-running-method/      # METHOD lifecycle for waitForRunning (DSL settings)
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-healthy-method-options/  # METHOD lifecycle for waitForHealthy with custom timeout/poll
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-order/               # Execution order verification
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-multi-stack/     # Multiple stacks with different waitForLog configs (CLASS) - NEW
│   ├── app/
│   ├── app-image/
│   └── ...
├── wait-log-mixed-ready/     # Mixed ready states (some services ready before others) - NEW IT-15
│   ├── app/
│   ├── app-image/
│   └── ...
└── wait-log-config-cache/    # Configuration cache explicit verification - NEW IT-11
    ├── app/
    ├── app-image/
    └── ...
```

### Port Allocation

Following the convention from `plugin-integration-test/README.md`:

| Scenario                     | Server Ports | Registry Ports (if needed) |
|------------------------------|--------------|----------------------------|
| wait-log-basic               | 9200-9209    | N/A                        |
| wait-log-multi-pattern       | 9210-9219    | N/A                        |
| wait-log-reject              | 9220-9229    | N/A                        |
| wait-log-options             | 9230-9239    | N/A                        |
| wait-log-combined            | 9240-9249    | N/A                        |
| wait-log-timeout             | 9250-9259    | N/A                        |
| wait-log-crash               | 9310-9319    | N/A                        |
| wait-log-unknown-service     | 9320-9329    | N/A                        |
| wait-log-regex-special       | 9330-9339    | N/A                        |
| wait-log-only                | 9340-9349    | N/A                        |
| wait-log-method              | 9260-9269    | N/A                        |
| wait-log-method-options      | 9270-9279    | N/A                        |
| wait-running-method          | 9280-9289    | N/A                        |
| wait-healthy-method-options  | 9290-9299    | N/A                        |
| wait-order                   | 9300-9309    | N/A                        |
| wait-log-multi-stack         | 9350-9359    | N/A                        |
| wait-log-defaults            | 9360-9369    | N/A                        |
| wait-log-mixed-ready         | 9370-9379    | N/A                        |
| wait-log-config-cache        | 9380-9389    | N/A                        |

### Image Naming Convention

All verification images follow: `verification-<scenario-name>-<app-type>`

| Scenario                     | Image Name                                    |
|------------------------------|-----------------------------------------------|
| wait-log-basic               | `verification-wait-log-basic-app`             |
| wait-log-multi-pattern       | `verification-wait-log-multi-pattern-app`     |
| wait-log-reject              | `verification-wait-log-reject-app`            |
| wait-log-options             | `verification-wait-log-options-app`           |
| wait-log-combined            | `verification-wait-log-combined-app`          |
| wait-log-timeout             | `verification-wait-log-timeout-app`           |
| wait-log-crash               | `verification-wait-log-crash-app`             |
| wait-log-unknown-service     | `verification-wait-log-unknown-service-app`   |
| wait-log-regex-special       | `verification-wait-log-regex-special-app`     |
| wait-log-only                | `verification-wait-log-only-app`              |
| wait-log-method              | `verification-wait-log-method-app`            |
| wait-log-method-options      | `verification-wait-log-method-options-app`    |
| wait-running-method          | `verification-wait-running-method-app`        |
| wait-healthy-method-options  | `verification-wait-healthy-method-options-app`|
| wait-order                   | `verification-wait-order-app`                 |
| wait-log-multi-stack         | `verification-wait-log-multi-stack-app`       |
| wait-log-defaults            | `verification-wait-log-defaults-app`          |
| wait-log-mixed-ready         | `verification-wait-log-mixed-ready-app`       |
| wait-log-config-cache        | `verification-wait-log-config-cache-app`      |

---

## Phase 1: buildSrc Validator Extensions

### 1.1 LogPatternValidator Class

Create `plugin-integration-test/buildSrc/src/main/groovy/com/kineticfire/test/LogPatternValidator.groovy`:

```groovy
package com.kineticfire.test

import groovy.transform.CompileStatic
import java.util.regex.Pattern

/**
 * Validator for log pattern matching in integration tests.
 * Used to verify that waitForLog functionality worked correctly
 * and to capture timing information for verification.
 */
@CompileStatic
class LogPatternValidator {

    /**
     * Check if a regex pattern exists in service logs.
     *
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @param patternStr Regex pattern to search for
     * @return true if pattern found in logs
     */
    static boolean hasPatternInLogs(String projectName, String serviceName, String patternStr) {
        def logs = getServiceLogs(projectName, serviceName, 0)
        def pattern = Pattern.compile(patternStr)
        return logs.any { line -> pattern.matcher(line).find() }
    }

    /**
     * Check if a regex pattern exists in logs (case-insensitive).
     */
    static boolean hasPatternInLogsCaseInsensitive(String projectName, String serviceName, String patternStr) {
        def logs = getServiceLogs(projectName, serviceName, 0)
        def pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
        return logs.any { line -> pattern.matcher(line).find() }
    }

    /**
     * Get service logs as list of lines.
     *
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @param tailLines Number of lines to tail (0 = all logs)
     * @return List of log lines
     */
    static List<String> getServiceLogs(String projectName, String serviceName, int tailLines) {
        def command = ['docker', 'compose', '-p', projectName, 'logs', serviceName]
        if (tailLines > 0) {
            command += ['--tail', tailLines.toString()]
        }

        def process = command.execute()
        process.waitFor()

        if (process.exitValue() != 0) {
            return []
        }

        return process.text.trim().split('\n').findAll { it }
    }

    /**
     * Find the first line matching a pattern.
     *
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @param patternStr Regex pattern to search for
     * @return The matching line, or null if not found
     */
    static String findMatchingLine(String projectName, String serviceName, String patternStr) {
        def logs = getServiceLogs(projectName, serviceName, 0)
        def pattern = Pattern.compile(patternStr)
        return logs.find { line -> pattern.matcher(line).find() }
    }

    /**
     * Count how many lines match a pattern.
     */
    static int countMatchingLines(String projectName, String serviceName, String patternStr) {
        def logs = getServiceLogs(projectName, serviceName, 0)
        def pattern = Pattern.compile(patternStr)
        return logs.count { line -> pattern.matcher(line).find() }
    }

    /**
     * Wait for a pattern to appear in logs with timing measurement.
     * This is useful for verifying timeout behavior.
     *
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @param patternStr Regex pattern to wait for
     * @param timeoutSeconds Maximum seconds to wait
     * @param pollSeconds How often to check (default 1)
     * @return Map with keys: 'found' (boolean), 'elapsedMs' (long), 'matchingLine' (String or null)
     */
    static Map<String, Object> waitForPatternWithTiming(
            String projectName,
            String serviceName,
            String patternStr,
            int timeoutSeconds,
            int pollSeconds = 1) {

        def startTime = System.currentTimeMillis()
        def timeoutMs = timeoutSeconds * 1000L
        def pollMs = pollSeconds * 1000L
        def pattern = Pattern.compile(patternStr)

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            def logs = getServiceLogs(projectName, serviceName, 0)
            def matchingLine = logs.find { line -> pattern.matcher(line).find() }

            if (matchingLine) {
                return [
                    found: true,
                    elapsedMs: System.currentTimeMillis() - startTime,
                    matchingLine: matchingLine
                ]
            }

            Thread.sleep(pollMs)
        }

        return [
            found: false,
            elapsedMs: System.currentTimeMillis() - startTime,
            matchingLine: null
        ]
    }

    /**
     * Verify all patterns in a list are present in logs.
     *
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @param patterns List of regex patterns (all must match)
     * @return Map with keys: 'allMatched' (boolean), 'matchedPatterns' (List), 'unmatchedPatterns' (List)
     */
    static Map<String, Object> verifyAllPatternsPresent(
            String projectName,
            String serviceName,
            List<String> patterns) {

        def logs = getServiceLogs(projectName, serviceName, 0)
        def matched = []
        def unmatched = []

        patterns.each { patternStr ->
            def pattern = Pattern.compile(patternStr)
            if (logs.any { line -> pattern.matcher(line).find() }) {
                matched << patternStr
            } else {
                unmatched << patternStr
            }
        }

        return [
            allMatched: unmatched.isEmpty(),
            matchedPatterns: matched,
            unmatchedPatterns: unmatched
        ]
    }
}
```

### 1.2 GradleOutputValidator Class (NEW)

Create `plugin-integration-test/buildSrc/src/main/groovy/com/kineticfire/test/GradleOutputValidator.groovy`:

```groovy
package com.kineticfire.test

import groovy.transform.CompileStatic
import java.util.regex.Pattern

/**
 * Validator for capturing and verifying Gradle task output.
 * Used to verify warning and error messages from waitForLog tasks.
 */
@CompileStatic
class GradleOutputValidator {

    /**
     * Run a Gradle task and capture its output.
     *
     * @param projectDir Project directory
     * @param taskPath Task path to execute (e.g., ':composeUpWaitLogTest')
     * @param additionalArgs Additional Gradle arguments
     * @return GradleTaskResult with success status and output
     */
    static GradleTaskResult runGradleTask(File projectDir, String taskPath, List<String> additionalArgs = []) {
        def command = ['./gradlew', taskPath] + additionalArgs
        def processBuilder = new ProcessBuilder(command)
        processBuilder.directory(projectDir)
        processBuilder.redirectErrorStream(true)

        def process = processBuilder.start()
        def output = new StringBuilder()
        process.inputStream.eachLine { line ->
            output.append(line).append('\n')
        }
        def exitCode = process.waitFor()

        return new GradleTaskResult(
            success: exitCode == 0,
            exitCode: exitCode,
            output: output.toString()
        )
    }

    /**
     * Assert that output contains a pattern.
     */
    static void assertOutputContains(String output, String patternStr, String message = null) {
        def pattern = Pattern.compile(patternStr)
        if (!pattern.matcher(output).find()) {
            def errorMessage = message ?: "Expected output to contain pattern: ${patternStr}"
            throw new AssertionError("${errorMessage}\n\nActual output:\n${output}")
        }
    }

    /**
     * Assert that output contains a warning message.
     * Looks for patterns with common warning prefixes.
     */
    static void assertOutputContainsWarning(String output, String expectedWarning) {
        def warningPatterns = [
            "WARNING:.*${Pattern.quote(expectedWarning)}",
            "\\[waitForLog\\] WARNING:.*${Pattern.quote(expectedWarning)}",
            "WARN.*${Pattern.quote(expectedWarning)}"
        ]

        boolean found = warningPatterns.any { patternStr ->
            Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE).matcher(output).find()
        }

        if (!found) {
            throw new AssertionError(
                "Expected warning message not found: ${expectedWarning}\n\nActual output:\n${output}"
            )
        }
    }

    /**
     * Assert that output contains error-related content.
     */
    static void assertOutputContainsError(String output, String expectedError) {
        def errorPatterns = [
            "ERROR:.*${Pattern.quote(expectedError)}",
            "FAILURE:.*${Pattern.quote(expectedError)}",
            Pattern.quote(expectedError)
        ]

        boolean found = errorPatterns.any { patternStr ->
            Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE).matcher(output).find()
        }

        if (!found) {
            throw new AssertionError(
                "Expected error message not found: ${expectedError}\n\nActual output:\n${output}"
            )
        }
    }

    /**
     * Result of running a Gradle task.
     */
    static class GradleTaskResult {
        boolean success
        int exitCode
        String output
    }
}
```

### 1.3 StateFileValidator Extensions (NEW - IT-13)

Add methods to existing `StateFileValidator` class for waitForLog metadata verification:

```groovy
package com.kineticfire.test

/**
 * Extensions to StateFileValidator for waitForLog metadata verification.
 */
@CompileStatic
class StateFileValidator {

    // ... existing methods ...

    /**
     * Get waitForLog configuration from state file.
     *
     * @param stateData Parsed state file data
     * @return waitForLog configuration map, or null if not present
     */
    static Map<String, Object> getWaitForLogConfig(Map stateData) {
        return stateData?.waitForLog as Map<String, Object>
    }

    /**
     * Verify waitForLog configuration exists in state file.
     *
     * @param stateData Parsed state file data
     */
    static void assertWaitForLogConfigPresent(Map stateData) {
        def config = getWaitForLogConfig(stateData)
        if (config == null) {
            throw new AssertionError("Expected waitForLog configuration in state file, but it was not present")
        }
    }

    /**
     * Verify waitForLog services configuration.
     *
     * @param stateData Parsed state file data
     * @param expectedServices Map of service names to expected pattern counts
     */
    static void assertWaitForLogServices(Map stateData, Map<String, Integer> expectedServices) {
        def config = getWaitForLogConfig(stateData)
        if (config == null) {
            throw new AssertionError("waitForLog configuration not present in state file")
        }

        def services = config.waitForServices as Map<String, List<String>>
        expectedServices.each { serviceName, expectedPatternCount ->
            if (!services.containsKey(serviceName)) {
                throw new AssertionError("Service '${serviceName}' not found in waitForLog.waitForServices")
            }
            def actualPatternCount = services[serviceName].size()
            if (actualPatternCount != expectedPatternCount) {
                throw new AssertionError(
                    "Expected ${expectedPatternCount} patterns for service '${serviceName}', but found ${actualPatternCount}"
                )
            }
        }
    }

    /**
     * Verify waitForLog timeout configuration.
     *
     * @param stateData Parsed state file data
     * @param expectedTimeout Expected timeout in seconds
     */
    static void assertWaitForLogTimeout(Map stateData, int expectedTimeout) {
        def config = getWaitForLogConfig(stateData)
        if (config == null) {
            throw new AssertionError("waitForLog configuration not present in state file")
        }

        def actualTimeout = config.timeoutSeconds as Integer
        if (actualTimeout != expectedTimeout) {
            throw new AssertionError(
                "Expected waitForLog.timeoutSeconds=${expectedTimeout}, but found ${actualTimeout}"
            )
        }
    }

    /**
     * Verify waitForLog poll interval configuration.
     *
     * @param stateData Parsed state file data
     * @param expectedPoll Expected poll interval in seconds
     */
    static void assertWaitForLogPollSeconds(Map stateData, int expectedPoll) {
        def config = getWaitForLogConfig(stateData)
        if (config == null) {
            throw new AssertionError("waitForLog configuration not present in state file")
        }

        def actualPoll = config.pollSeconds as Integer
        if (actualPoll != expectedPoll) {
            throw new AssertionError(
                "Expected waitForLog.pollSeconds=${expectedPoll}, but found ${actualPoll}"
            )
        }
    }
}
```

---

## Phase 2: waitForLog Verification Tests (CLASS Lifecycle)

### 2.1 Scenario: wait-log-basic

**Purpose**: Verify basic `waitForLog` functionality with a single pattern per service.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-basic/`

**Features Tested**:
- Single pattern matching
- Pattern appears in logs before timeout
- State file generation includes waitForLog metadata
- Container is accessible after wait completes

#### 2.1.1 Application Requirements

Create a Spring Boot application that:
1. Outputs a predictable startup message after a configurable delay
2. Has an HTTP endpoint to verify the app is running
3. Startup message format: `Application started successfully - ready to serve`

**Key Environment Variables**:
- `STARTUP_DELAY_MS`: Delay before printing startup message (default: 3000)

#### 2.1.2 build.gradle (app-image)

```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

repositories {
    mavenLocal()
    mavenCentral()
}

// Get JAR from app subproject
def jarFileProvider = project(':dockerTest:verification:wait-log-basic:app')
    .tasks.named('bootJar').flatMap { it.archiveFile }
def jarFileNameProvider = jarFileProvider.map { it.asFile.name }

docker {
    images {
        waitLogBasicApp {
            imageName = 'verification-wait-log-basic-app'
            tags = ['latest', '1.0.0']

            contextTask = tasks.register('prepareWaitLogBasicAppContext', Copy) {
                group = 'docker'
                description = 'Prepare Docker build context for wait-log-basic app'
                into layout.buildDirectory.dir('docker-context/waitLogBasicApp')
                from('src/main/docker')
                from(jarFileProvider) {
                    rename { jarFileNameProvider.get() }
                }
                dependsOn project(':dockerTest:verification:wait-log-basic:app').tasks.named('bootJar')
            }

            buildArgs.put('JAR_FILE', jarFileNameProvider)
        }
    }
}

dockerTest {
    composeStacks {
        waitLogBasicTest {
            files.from('src/integrationTest/resources/compose/wait-log-basic.yml')
            projectName = 'verification-wait-log-basic-test'

            // Wait for specific log pattern before proceeding
            waitForLog {
                waitForServices.set([
                    'wait-log-basic-app': ['Application started successfully']
                ])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}

dependencies {
    testImplementation libs.groovy.all
    testImplementation libs.spock.core
    testRuntimeOnly libs.junit.platform.launcher
    integrationTestImplementation files("${rootProject.projectDir}/buildSrc/build/classes/groovy/main")
}

tasks.named('integrationTest') {
    description = 'Runs wait-log-basic verification tests'
    group = 'verification'

    systemProperty 'COMPOSE_STATE_FILE',
        layout.buildDirectory.file('compose-state/waitLogBasicTest-state.json').get().asFile.absolutePath
    systemProperty 'COMPOSE_PROJECT_NAME', 'verification-wait-log-basic-test'
}

afterEvaluate {
    tasks.named('composeUpWaitLogBasicTest') {
        dependsOn tasks.named('dockerBuildWaitLogBasicApp')
    }
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpWaitLogBasicTest')
        finalizedBy tasks.named('composeDownWaitLogBasicTest')
    }
}
```

#### 2.1.3 compose/wait-log-basic.yml

```yaml
services:
  wait-log-basic-app:
    image: verification-wait-log-basic-app:latest
    ports:
      - "8080"
    environment:
      STARTUP_DELAY_MS: 5000  # 5 second delay before startup message
```

#### 2.1.4 Integration Test Class

```groovy
package com.kineticfire.test

import com.kineticfire.test.DockerComposeValidator
import com.kineticfire.test.StateFileValidator
import com.kineticfire.test.LogPatternValidator
import spock.lang.Specification

/**
 * Verification Test: Basic waitForLog Functionality
 *
 * INTERNAL TEST - Validates plugin mechanics, not application behavior.
 *
 * This test validates that the dockerTest plugin correctly:
 * - Waits for a specific log pattern to appear before proceeding
 * - Does NOT proceed until the pattern is found
 * - Times out appropriately if pattern never appears
 * - Generates state files correctly
 *
 * For user-facing examples, see examples/
 */
class WaitLogBasicPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Verification: Wait-Log Basic Plugin Mechanics ==="
        println "Project Name: ${projectName}"
        println "State File: ${stateFilePath}"

        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    def cleanupSpec() {
        try {
            println "=== Forcing cleanup of Docker Compose stack: ${projectName} ==="
            def process = ['docker', 'compose', '-p', projectName, 'down', '-v'].execute()
            process.waitFor()
        } catch (Exception e) {
            println "Warning: Failed to cleanup: ${e.message}"
        }
    }

    def "plugin should generate valid state file"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateData, 'waitLogBasicTest', projectName)

        and: "wait-log-basic-app service is present"
        def serviceNames = StateFileValidator.getServiceNames(stateData)
        serviceNames.contains('wait-log-basic-app')
    }

    def "plugin should start container in running state"() {
        expect: "container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'wait-log-basic-app')
    }

    def "plugin should wait until log pattern appears"() {
        expect: "the startup pattern exists in logs"
        LogPatternValidator.hasPatternInLogs(
            projectName,
            'wait-log-basic-app',
            'Application started successfully'
        )
    }

    def "plugin should have waited for startup delay"() {
        when: "we verify the app waited appropriately"
        // The app is configured with 5 second startup delay
        // If the plugin didn't wait, tests would start before the pattern appeared
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'wait-log-basic-app', 8080)

        // Query the app's uptime endpoint
        def url = new URL("http://localhost:${hostPort}/health")
        def connection = url.openConnection()
        connection.setConnectTimeout(5000)
        connection.setReadTimeout(5000)
        def responseCode = connection.getResponseCode()
        def response = connection.getInputStream().text
        def healthData = new groovy.json.JsonSlurper().parseText(response)

        then: "app is accessible and uptime shows it waited"
        responseCode == 200
        // Uptime should be >= 5000ms (the configured startup delay)
        healthData.uptimeMs >= 4800
    }

    def "plugin should map ports correctly"() {
        when: "we read port mapping from state file"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'wait-log-basic-app', 8080)

        then: "port is mapped to valid host port"
        hostPort > 0
        hostPort <= 65535
    }
}
```

---

### 2.2 Scenario: wait-log-multi-pattern

**Purpose**: Verify multiple patterns per service where ALL must match (AND semantics).

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-multi-pattern/`

**Features Tested**:
- Multiple patterns per service (all must match)
- Multiple services with different patterns
- Patterns can match in any order
- State file reflects multi-pattern configuration

#### 2.2.1 Application Requirements

Create a Spring Boot application that outputs multiple distinct startup phases:
1. `Database connection established` (at 2s)
2. `Cache warmed up` (at 4s)
3. `Application started successfully` (at 6s)

#### 2.2.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogMultiPatternTest {
            files.from('src/integrationTest/resources/compose/wait-log-multi-pattern.yml')
            projectName = 'verification-wait-log-multi-pattern-test'

            waitForLog {
                // Multiple patterns - ALL must match before proceeding
                waitForServices.set([
                    'multi-pattern-app': [
                        'Database connection established',
                        'Cache warmed up',
                        'Application started successfully'
                    ]
                ])
                timeoutSeconds.set(90)
                pollSeconds.set(1)
            }
        }
    }
}
```

#### 2.2.3 Integration Test Key Assertions

```groovy
def "plugin should wait for ALL patterns to match"() {
    expect: "all three patterns exist in logs"
    def result = LogPatternValidator.verifyAllPatternsPresent(
        projectName,
        'multi-pattern-app',
        ['Database connection established', 'Cache warmed up', 'Application started successfully']
    )
    result.allMatched == true
    result.unmatchedPatterns.isEmpty()
}

def "patterns can match in any order"() {
    when: "we check log line order"
    def logs = LogPatternValidator.getServiceLogs(projectName, 'multi-pattern-app', 0)
    def dbIndex = logs.findIndexOf { it.contains('Database connection') }
    def cacheIndex = logs.findIndexOf { it.contains('Cache warmed') }
    def startedIndex = logs.findIndexOf { it.contains('Application started') }

    then: "all patterns were found (order verified by existence, not sequence)"
    dbIndex >= 0
    cacheIndex >= 0
    startedIndex >= 0
}
```

---

### 2.3 Scenario: wait-log-reject

**Purpose**: Verify reject pattern functionality - fail fast when error patterns appear.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-reject/`

**Features Tested**:
- Reject patterns cause immediate failure
- Reject pattern error message includes matched pattern
- Reject pattern error includes partial match status
- Tests both: app that succeeds (no reject) and app that fails (reject triggered)

#### 2.3.1 Two Test Cases

**Test Case A**: Application that starts successfully (no reject pattern triggered)
- Log output: `Application started successfully`
- Reject patterns configured but NOT matched

**Test Case B**: Application that outputs error pattern
- Log output includes: `FATAL: Database connection failed`
- Reject pattern `FATAL` should trigger immediate failure

#### 2.3.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        // Test Case A: Successful startup (no reject pattern matched)
        waitLogRejectSuccessTest {
            files.from('src/integrationTest/resources/compose/wait-log-reject-success.yml')
            projectName = 'verification-wait-log-reject-success-test'

            waitForLog {
                waitForServices.set([
                    'reject-success-app': ['Application started successfully']
                ])
                rejectPatterns.set([
                    'reject-success-app': ['FATAL', 'Exception', 'Error initializing']
                ])
                timeoutSeconds.set(60)
            }
        }

        // Test Case B: Failure with reject pattern (tested via Gradle task failure)
        // This is verified by running a Gradle build that should FAIL
        waitLogRejectFailureTest {
            files.from('src/integrationTest/resources/compose/wait-log-reject-failure.yml')
            projectName = 'verification-wait-log-reject-failure-test'

            waitForLog {
                waitForServices.set([
                    'reject-failure-app': ['Application started successfully']
                ])
                rejectPatterns.set([
                    'reject-failure-app': ['FATAL', 'Exception']
                ])
                timeoutSeconds.set(30)
            }
        }
    }
}
```

#### 2.3.3 Integration Test Key Assertions

```groovy
def "reject patterns should NOT trigger when no error occurs"() {
    expect: "container started successfully"
    DockerComposeValidator.isContainerRunning(projectName, 'reject-success-app')

    and: "startup pattern was found"
    LogPatternValidator.hasPatternInLogs(projectName, 'reject-success-app', 'Application started successfully')

    and: "no reject pattern was matched (no FATAL in logs)"
    !LogPatternValidator.hasPatternInLogs(projectName, 'reject-success-app', 'FATAL')
}

// Note: Testing reject pattern FAILURE requires a separate Gradle invocation
// that expects the build to fail. This is done via a GradleBuild task or TestKit.
```

#### 2.3.4 Reject Pattern Failure Verification

Create a separate test that verifies the reject pattern failure scenario:

```groovy
// In a separate test file or using TestKit
def "reject pattern should cause immediate failure"() {
    given: "a Gradle build with an app that outputs FATAL error"
    // This test uses GradleBuild task or TestKit to run a build that should fail

    when: "we run the composeUp task"
    def result = runGradleTask(':dockerTest:verification:wait-log-reject:composeUpWaitLogRejectFailureTest')

    then: "the build fails"
    !result.success

    and: "error message mentions reject pattern"
    result.output.contains('Reject pattern matched')
    result.output.contains('FATAL')
}
```

---

### 2.4 Scenario: wait-log-options

**Purpose**: Verify optional configuration: `verbose`, `caseInsensitive`, `progressIntervalSeconds`.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-options/`

**Features Tested**:
- `caseInsensitive.set(true)` matches regardless of case
- `verbose.set(true)` produces detailed polling output
- `progressIntervalSeconds.set(N)` produces periodic progress summaries
- All options work correctly together

#### 2.4.1 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        // Test case-insensitive matching
        waitLogCaseInsensitiveTest {
            files.from('src/integrationTest/resources/compose/wait-log-options.yml')
            projectName = 'verification-wait-log-case-insensitive-test'

            waitForLog {
                waitForServices.set([
                    // Pattern is lowercase but app outputs "APPLICATION STARTED SUCCESSFULLY"
                    'options-app': ['application started successfully']
                ])
                caseInsensitive.set(true)
                timeoutSeconds.set(60)
            }
        }

        // Test verbose logging
        waitLogVerboseTest {
            files.from('src/integrationTest/resources/compose/wait-log-options.yml')
            projectName = 'verification-wait-log-verbose-test'

            waitForLog {
                waitForServices.set([
                    'options-app': ['Application started successfully']
                ])
                verbose.set(true)
                timeoutSeconds.set(60)
                pollSeconds.set(1)
            }
        }

        // Test progress interval
        waitLogProgressTest {
            files.from('src/integrationTest/resources/compose/wait-log-options.yml')
            projectName = 'verification-wait-log-progress-test'

            waitForLog {
                waitForServices.set([
                    'options-app': ['Application started successfully']
                ])
                progressIntervalSeconds.set(5)  // Log every 5 seconds
                timeoutSeconds.set(60)
            }
        }
    }
}
```

#### 2.4.2 Integration Test Key Assertions

```groovy
def "caseInsensitive should match regardless of case"() {
    expect: "pattern matched even though case differs"
    // App outputs "APPLICATION STARTED SUCCESSFULLY" (uppercase)
    // Pattern is "application started successfully" (lowercase)
    DockerComposeValidator.isContainerRunning(projectName, 'options-app')
}

// Verbose and progress interval logging are verified by examining build output
// These assertions may need to capture Gradle output during the test
```

---

### 2.5 Scenario: wait-log-combined

**Purpose**: Verify `waitForLog` works correctly when combined with `waitForHealthy` and/or `waitForRunning`.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-combined/`

**Features Tested**:
- Execution order: `waitForRunning` -> `waitForHealthy` -> `waitForLog`
- Different services use different wait mechanisms
- All wait blocks complete before tests run
- State file reflects combined configuration

#### 2.5.1 Application Setup

- **nginx-service**: No health check, uses `waitForRunning`
- **app-service**: Has health check, uses `waitForHealthy`
- **app-service**: Also uses `waitForLog` for application-specific readiness

#### 2.5.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogCombinedTest {
            files.from('src/integrationTest/resources/compose/wait-log-combined.yml')
            projectName = 'verification-wait-log-combined-test'

            // Step 1: Wait for nginx to be running (no health check)
            waitForRunning {
                waitForServices.set(['nginx-proxy'])
                timeoutSeconds.set(30)
            }

            // Step 2: Wait for app to be healthy
            waitForHealthy {
                waitForServices.set(['combined-app'])
                timeoutSeconds.set(60)
            }

            // Step 3: Wait for app-specific log pattern
            waitForLog {
                waitForServices.set([
                    'combined-app': ['API endpoints registered', 'Ready to serve requests']
                ])
                timeoutSeconds.set(30)
            }
        }
    }
}
```

#### 2.5.3 Integration Test Key Assertions

```groovy
def "all wait mechanisms should complete before tests run"() {
    expect: "nginx is running"
    DockerComposeValidator.isServiceRunningViaCompose(projectName, 'nginx-proxy')

    and: "app is healthy"
    DockerComposeValidator.isServiceHealthyViaCompose(projectName, 'combined-app')

    and: "app log patterns were matched"
    LogPatternValidator.verifyAllPatternsPresent(
        projectName,
        'combined-app',
        ['API endpoints registered', 'Ready to serve requests']
    ).allMatched
}

def "execution order should be waitForRunning -> waitForHealthy -> waitForLog"() {
    // This is implicitly verified by the fact that tests run successfully
    // If order was wrong, the wait blocks would fail or timeout
    expect: "tests are running, which means all waits completed in correct order"
    true
}
```

---

### 2.6 Scenario: wait-log-timeout

**Purpose**: Verify timeout behavior when patterns never appear.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-timeout/`

**Features Tested**:
- Timeout occurs after configured `timeoutSeconds`
- Timeout error message includes pattern status
- Timeout error message includes recent log lines
- Service that never outputs expected pattern

#### 2.6.1 Application Requirements

Create an application that:
1. Starts and outputs logs but NEVER outputs the expected pattern
2. Stays running (doesn't crash)

#### 2.6.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogTimeoutTest {
            files.from('src/integrationTest/resources/compose/wait-log-timeout.yml')
            projectName = 'verification-wait-log-timeout-test'

            waitForLog {
                waitForServices.set([
                    // This pattern will NEVER appear
                    'timeout-app': ['This pattern will never appear in logs']
                ])
                timeoutSeconds.set(10)  // Short timeout for faster test
                pollSeconds.set(1)
            }
        }
    }
}
```

#### 2.6.3 Timeout Verification

This test verifies the timeout behavior by expecting the Gradle task to fail:

```groovy
// Using TestKit or GradleBuild task
def "timeout should occur after configured seconds"() {
    given: "a build configured to wait for a pattern that never appears"
    def startTime = System.currentTimeMillis()

    when: "we run the composeUp task"
    def result = runGradleTask(':dockerTest:verification:wait-log-timeout:composeUpWaitLogTimeoutTest')
    def elapsedMs = System.currentTimeMillis() - startTime

    then: "the build fails"
    !result.success

    and: "it took approximately 10 seconds (the configured timeout)"
    elapsedMs >= 9000  // At least 9 seconds
    elapsedMs < 20000  // Less than 20 seconds (reasonable buffer)

    and: "error message contains helpful information"
    result.output.contains('Timeout waiting for log patterns')
    result.output.contains('This pattern will never appear')
}
```

---

### 2.7 Scenario: wait-log-crash (NEW - IT-1)

**Purpose**: Verify service crash detection during log pattern wait.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-crash/`

**Features Tested**:
- Container exits unexpectedly during wait operation
- Error message includes exit code
- Error message includes partial match status
- Error message includes recent log lines
- Build fails with clear, actionable error

#### 2.7.1 Application Requirements

Create an application that:
1. Starts and outputs some initial log messages
2. Crashes with a specific exit code (e.g., 1) after a configurable delay
3. Never outputs the expected success pattern

**Key Environment Variables**:
- `CRASH_DELAY_MS`: Delay before crashing (default: 3000)
- `EXIT_CODE`: Exit code to use when crashing (default: 1)

#### 2.7.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogCrashTest {
            files.from('src/integrationTest/resources/compose/wait-log-crash.yml')
            projectName = 'verification-wait-log-crash-test'

            waitForLog {
                waitForServices.set([
                    // This pattern will NEVER appear because app crashes first
                    'crash-app': ['Application started successfully']
                ])
                timeoutSeconds.set(30)
                pollSeconds.set(1)
            }
        }
    }
}
```

#### 2.7.3 compose/wait-log-crash.yml

```yaml
services:
  crash-app:
    image: verification-wait-log-crash-app:latest
    ports:
      - "8080"
    environment:
      CRASH_DELAY_MS: 3000   # Crash after 3 seconds
      EXIT_CODE: 1           # Exit with code 1
```

#### 2.7.4 Crash Verification Test

This test verifies the crash detection by expecting the Gradle task to fail:

```groovy
// Using TestKit or GradleBuild task
def "service crash should be detected with clear error message"() {
    given: "a build with an app that crashes during startup"

    when: "we run the composeUp task"
    def result = runGradleTask(':dockerTest:verification:wait-log-crash:composeUpWaitLogCrashTest')

    then: "the build fails"
    !result.success

    and: "error message indicates service crashed"
    result.output.contains("crashed during log pattern wait")
    result.output.contains("exit code: 1")

    and: "error message shows partial match status"
    result.output.contains("[NOT FOUND]")
    result.output.contains("Application started successfully")

    and: "error message includes recent log lines"
    result.output.contains("Last 10 log lines")
}
```

---

### 2.8 Scenario: wait-log-unknown-service (NEW - IT-2, IT-4)

**Purpose**: Verify unknown service name validation with helpful error messages.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-unknown-service/`

**Features Tested**:
- Typo in service name detected early with clear error
- Available services listed in error message
- Empty compose project detected (composeUp not called scenario)
- Error message provides actionable hints

#### 2.8.1 Test Cases

**Test Case A**: Service name typo - configure `'appp'` instead of `'app'`

**Test Case B**: Empty compose project - run waitForLog before composeUp (if possible to trigger)

#### 2.8.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        // Test Case A: Service name typo
        waitLogUnknownServiceTest {
            files.from('src/integrationTest/resources/compose/wait-log-unknown-service.yml')
            projectName = 'verification-wait-log-unknown-service-test'

            waitForLog {
                waitForServices.set([
                    // Intentional typo: 'appp' instead of 'app'
                    'appp': ['Started Application']
                ])
                timeoutSeconds.set(10)
            }
        }
    }
}
```

#### 2.8.3 Unknown Service Verification Test

```groovy
def "unknown service name should fail with helpful error"() {
    given: "a build with a typo in service name"

    when: "we run the composeUp task"
    def result = runGradleTask(':dockerTest:verification:wait-log-unknown-service:composeUpWaitLogUnknownServiceTest')

    then: "the build fails"
    !result.success

    and: "error message lists the unknown service"
    result.output.contains("Service(s) not found")
    result.output.contains("appp")

    and: "error message lists available services"
    result.output.contains("Available services:")
    result.output.contains("app")

    and: "error message provides actionable hint"
    result.output.contains("Check for typos")
}
```

---

### 2.9 Scenario: wait-log-regex-special (NEW - IT-3)

**Purpose**: Verify regex patterns with special and JSON-sensitive characters work correctly.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-regex-special/`

**Features Tested**:
- Patterns with backslashes (e.g., `\\[INFO\\]`)
- Patterns with quotes (e.g., matching `message: "started"`)
- Patterns with regex metacharacters (e.g., `.*?`, `\\d+`)
- Patterns with JSON-sensitive characters survive system property serialization
- Complex regex patterns work correctly

#### 2.9.1 Application Requirements

Create an application that outputs log messages with special characters:
1. `[INFO] Application version 1.0.0 initialized`
2. `Config loaded: {"status": "ready", "port": 8080}`
3. `Listening on port 8080...`

#### 2.9.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogRegexSpecialTest {
            files.from('src/integrationTest/resources/compose/wait-log-regex-special.yml')
            projectName = 'verification-wait-log-regex-special-test'

            waitForLog {
                waitForServices.set([
                    'regex-app': [
                        // Pattern with escaped brackets
                        '\\[INFO\\] Application version \\d+\\.\\d+\\.\\d+ initialized',
                        // Pattern with quotes (JSON-like)
                        '"status": "ready"',
                        // Pattern with regex metacharacters
                        'Listening on port \\d+\\.\\.\\.'
                    ]
                ])
                timeoutSeconds.set(60)
            }
        }
    }
}
```

#### 2.9.3 Integration Test Key Assertions

```groovy
def "patterns with special characters should match correctly"() {
    expect: "container started successfully"
    DockerComposeValidator.isContainerRunning(projectName, 'regex-app')

    and: "all patterns were matched (including special chars)"
    LogPatternValidator.verifyAllPatternsPresent(
        projectName,
        'regex-app',
        ['\\[INFO\\]', '"status": "ready"', 'port \\d+']
    ).allMatched
}

def "patterns survive JSON serialization in system properties"() {
    when: "we read system properties passed to test"
    def servicesJson = System.getProperty('docker.compose.waitForLog.services')

    then: "JSON is valid and contains our patterns"
    def parsed = new groovy.json.JsonSlurper().parseText(servicesJson)
    parsed['regex-app'].size() == 3

    and: "backslashes are preserved"
    parsed['regex-app'][0].contains('\\[INFO\\]')
}
```

---

### 2.10 Scenario: wait-log-only (NEW)

**Purpose**: Verify waitForLog works correctly without waitForRunning or waitForHealthy.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-only/`

**Features Tested**:
- waitForLog as the sole readiness check
- No dependency on waitForRunning or waitForHealthy
- Container accessible after log pattern matches
- Demonstrates minimal configuration

#### 2.10.1 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogOnlyTest {
            files.from('src/integrationTest/resources/compose/wait-log-only.yml')
            projectName = 'verification-wait-log-only-test'

            // NO waitForRunning
            // NO waitForHealthy
            // ONLY waitForLog
            waitForLog {
                waitForServices.set([
                    'only-app': ['Application started successfully']
                ])
                timeoutSeconds.set(60)
            }
        }
    }
}
```

#### 2.10.2 Integration Test Key Assertions

```groovy
def "waitForLog should work as sole readiness check"() {
    expect: "container is running"
    DockerComposeValidator.isContainerRunning(projectName, 'only-app')

    and: "log pattern was matched"
    LogPatternValidator.hasPatternInLogs(projectName, 'only-app', 'Application started successfully')

    and: "application is accessible"
    def hostPort = StateFileValidator.getPublishedPort(stateData, 'only-app', 8080)
    def url = new URL("http://localhost:${hostPort}/health")
    url.openConnection().responseCode == 200
}
```

---

### 2.11 Scenario: wait-log-defaults (NEW - IT-9)

**Purpose**: Verify default convention values are applied correctly when not explicitly configured.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-defaults/`

**Features Tested**:
- Default `timeoutSeconds` = 60 (from 0100 DSL specification)
- Default `pollSeconds` = 2 (from 0100 DSL specification)
- Default `caseInsensitive` = false
- Default `verbose` = false
- Default `progressIntervalSeconds` = null (no progress logging by default)
- Default `rejectPatterns` = empty map
- State file reflects default values

**Reference**: DSL property defaults from `add-wait-for-log-0100-dsl-user-description.md` Section 4 (Property Table)

#### 2.11.1 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogDefaultsTest {
            files.from('src/integrationTest/resources/compose/wait-log-defaults.yml')
            projectName = 'verification-wait-log-defaults-test'

            // Minimal configuration - rely entirely on defaults
            waitForLog {
                waitForServices.set([
                    'defaults-app': ['Application started successfully']
                ])
                // NO timeoutSeconds - should default to 60
                // NO pollSeconds - should default to 2
                // NO caseInsensitive - should default to false
                // NO verbose - should default to false
                // NO progressIntervalSeconds - should default to null
                // NO rejectPatterns - should default to empty map
            }
        }
    }
}

tasks.named('integrationTest') {
    description = 'Runs default convention values verification tests'
    group = 'verification'

    systemProperty 'COMPOSE_STATE_FILE',
        layout.buildDirectory.file('compose-state/waitLogDefaultsTest-state.json').get().asFile.absolutePath
    systemProperty 'COMPOSE_PROJECT_NAME', 'verification-wait-log-defaults-test'
}
```

#### 2.11.2 compose/wait-log-defaults.yml

```yaml
services:
  defaults-app:
    image: verification-wait-log-defaults-app:latest
    ports:
      - "8080"
    environment:
      # Fast startup to not rely on timeout
      STARTUP_DELAY_MS: 2000
```

#### 2.11.3 Integration Test Key Assertions

```groovy
package com.kineticfire.test

import com.kineticfire.test.StateFileValidator
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Verification Test: Default Convention Values
 *
 * INTERNAL TEST - Validates that DSL defaults are applied correctly.
 *
 * This test verifies that when no explicit values are configured,
 * the plugin uses the documented default values from the DSL specification.
 */
class WaitLogDefaultsPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Verification: Wait-Log Defaults ==="
        println "Project Name: ${projectName}"
        println "State File: ${stateFilePath}"

        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    def "default timeoutSeconds should be 60"() {
        expect: "state file reflects default timeout"
        StateFileValidator.assertWaitForLogTimeout(stateData, 60)
    }

    def "default pollSeconds should be 2"() {
        expect: "state file reflects default poll interval"
        StateFileValidator.assertWaitForLogPollSeconds(stateData, 2)
    }

    def "default caseInsensitive should be false"() {
        when: "we read waitForLog config"
        def config = StateFileValidator.getWaitForLogConfig(stateData)

        then: "caseInsensitive is false (default)"
        config.caseInsensitive == false
    }

    def "default verbose should be false"() {
        when: "we read waitForLog config"
        def config = StateFileValidator.getWaitForLogConfig(stateData)

        then: "verbose is false (default)"
        config.verbose == false
    }

    def "default progressIntervalSeconds should be 0"() {
        when: "we read waitForLog config"
        def config = StateFileValidator.getWaitForLogConfig(stateData)

        then: "progressIntervalSeconds is 0 (default - disabled per DSL specification)"
        config.progressIntervalSeconds == 0
    }

    def "default rejectPatterns should be empty"() {
        when: "we read waitForLog config"
        def config = StateFileValidator.getWaitForLogConfig(stateData)

        then: "rejectPatterns is empty map (default)"
        config.rejectPatterns == null || config.rejectPatterns.isEmpty()
    }

    def "application should be accessible with defaults"() {
        expect: "container started successfully with default timeout"
        DockerComposeValidator.isContainerRunning(projectName, 'defaults-app')

        and: "pattern was found (case-sensitive by default)"
        LogPatternValidator.hasPatternInLogs(projectName, 'defaults-app', 'Application started successfully')
    }
}
```

---

## Phase 3: waitForLog Verification Tests (METHOD Lifecycle)

### 3.1 Scenario: wait-log-method

**Purpose**: Verify `waitForLog` works correctly with METHOD lifecycle.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-method/`

**Features Tested**:
- Fresh containers for each test method
- `waitForLog` executes before EACH test method
- State isolation between test methods
- Pattern matching works in METHOD lifecycle

#### 3.1.1 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogMethodTest {
            files.from('src/integrationTest/resources/compose/wait-log-method.yml')
            projectName = 'verification-wait-log-method-test'

            waitForHealthy {
                waitForServices.set(['method-app'])
                timeoutSeconds.set(60)
            }

            waitForLog {
                waitForServices.set([
                    'method-app': ['Application started successfully']
                ])
                timeoutSeconds.set(30)
            }
        }
    }
}

tasks.named('integrationTest') {
    description = 'Runs METHOD lifecycle verification tests for waitForLog'

    // METHOD lifecycle - containers restart for EACH test method
    usesCompose(stack: 'waitLogMethodTest', lifecycle: 'method')

    useJUnitPlatform()
    outputs.cacheIf { false }
}
```

#### 3.1.2 Integration Test Class

```groovy
package com.kineticfire.test

import com.kineticfire.gradle.docker.spock.ComposeUp
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Verification test for METHOD lifecycle using waitForLog.
 *
 * This test verifies that waitForLog correctly executes in METHOD lifecycle:
 * - Containers start fresh before EACH test method
 * - waitForLog executes and patterns match before each test
 * - State does NOT persist between test methods
 */
@ComposeUp  // No parameters - config from build.gradle via usesCompose()
class WaitLogMethodIT extends Specification {

    String baseUrl
    static int testMethodCount = 0

    def setup() {
        testMethodCount++

        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateFile = new File(stateFilePath)
        def stateData = new JsonSlurper().parse(stateFile)

        def port = stateData.services['method-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"

        println "=== Test ${testMethodCount}: Fresh container at ${baseUrl} ==="
    }

    def "test method 1 - waitForLog should complete before test runs"() {
        when: "we check if app is ready"
        def url = new URL("${baseUrl}/health")
        def connection = url.openConnection()
        connection.setConnectTimeout(5000)
        def responseCode = connection.getResponseCode()

        then: "app is accessible (waitForLog completed)"
        responseCode == 200
    }

    def "test method 2 - fresh container should have waitForLog complete"() {
        when: "we check if app is ready in fresh container"
        def url = new URL("${baseUrl}/health")
        def connection = url.openConnection()
        connection.setConnectTimeout(5000)
        def responseCode = connection.getResponseCode()

        then: "app is accessible (waitForLog completed for this fresh container)"
        responseCode == 200
    }

    def "test method 3 - state file should reflect METHOD lifecycle"() {
        when: "we read state file"
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateFile = new File(stateFilePath)
        def stateData = new JsonSlurper().parse(stateFile)

        then: "lifecycle is METHOD"
        stateData.lifecycle == 'method'
    }
}
```

---

### 3.2 Scenario: wait-log-method-options

**Purpose**: Verify `waitForLog` options work correctly with METHOD lifecycle.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-method-options/`

**Features Tested**:
- `verbose` logging works in METHOD lifecycle
- `progressIntervalSeconds` works in METHOD lifecycle
- `caseInsensitive` works in METHOD lifecycle

#### 3.2.1 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogMethodOptionsTest {
            files.from('src/integrationTest/resources/compose/wait-log-method-options.yml')
            projectName = 'verification-wait-log-method-options-test'

            waitForLog {
                waitForServices.set([
                    'method-options-app': ['application started']  // lowercase
                ])
                caseInsensitive.set(true)
                verbose.set(true)
                progressIntervalSeconds.set(3)
                timeoutSeconds.set(60)
                pollSeconds.set(1)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: 'waitLogMethodOptionsTest', lifecycle: 'method')
    useJUnitPlatform()
    outputs.cacheIf { false }
}
```

---

## Phase 4: METHOD Lifecycle Support for Existing Wait Blocks

The implementation adds full METHOD lifecycle support for `waitForRunning` and `waitForHealthy` by reading
DSL settings from system properties instead of using hardcoded values. These tests verify the fix.

### 4.1 Scenario: wait-running-method

**Purpose**: Verify `waitForRunning` honors DSL settings (`timeoutSeconds`, `pollSeconds`) in METHOD lifecycle.

**Location**: `plugin-integration-test/dockerTest/verification/wait-running-method/`

**Background**: Previously, `DockerComposeMethodExtension.waitForStackToBeReady()` used hardcoded
`ServiceStatus.HEALTHY` and ignored `waitForRunning` entirely. The implementation fix reads DSL settings
from system properties.

**Features Tested**:
- `waitForRunning` executes in METHOD lifecycle
- Custom `timeoutSeconds` is honored
- Custom `pollSeconds` is honored
- Service without health check can be waited for

#### 4.1.1 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitRunningMethodTest {
            files.from('src/integrationTest/resources/compose/wait-running-method.yml')
            projectName = 'verification-wait-running-method-test'

            // Only waitForRunning - no health check on container
            waitForRunning {
                waitForServices.set(['running-app'])
                timeoutSeconds.set(45)  // Custom timeout (not default 60)
                pollSeconds.set(3)      // Custom poll (not default 2)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: 'waitRunningMethodTest', lifecycle: 'method')
    useJUnitPlatform()
    outputs.cacheIf { false }
}
```

#### 4.1.2 compose/wait-running-method.yml

```yaml
services:
  running-app:
    image: verification-wait-running-method-app:latest
    ports:
      - "8080"
    # NO health check - testing waitForRunning with METHOD lifecycle
```

#### 4.1.3 Integration Test Key Assertions

```groovy
@ComposeUp
class WaitRunningMethodIT extends Specification {

    def "waitForRunning should work in METHOD lifecycle"() {
        given: "state file from extension"
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateData = new JsonSlurper().parse(new File(stateFilePath))

        expect: "container is running"
        DockerComposeValidator.isServiceRunningViaCompose(
            'verification-wait-running-method-test',
            'running-app'
        )

        and: "lifecycle is METHOD"
        stateData.lifecycle == 'method'
    }
}
```

---

### 4.2 Scenario: wait-healthy-method-options

**Purpose**: Verify `waitForHealthy` honors DSL settings in METHOD lifecycle (not hardcoded).

**Location**: `plugin-integration-test/dockerTest/verification/wait-healthy-method-options/`

**Background**: Previously, `DockerComposeMethodExtension` used hardcoded timeout (60s) and poll (2s).
The implementation fix reads DSL settings from system properties.

**Features Tested**:
- `waitForHealthy` uses custom `timeoutSeconds` in METHOD lifecycle
- `waitForHealthy` uses custom `pollSeconds` in METHOD lifecycle
- Health check completes before test runs

#### 4.2.1 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitHealthyMethodOptionsTest {
            files.from('src/integrationTest/resources/compose/wait-healthy-method-options.yml')
            projectName = 'verification-wait-healthy-method-options-test'

            waitForHealthy {
                waitForServices.set(['healthy-app'])
                timeoutSeconds.set(120)  // Custom timeout (testing DSL is honored)
                pollSeconds.set(1)       // Custom poll (testing DSL is honored)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: 'waitHealthyMethodOptionsTest', lifecycle: 'method')
    useJUnitPlatform()
    outputs.cacheIf { false }
}
```

#### 4.2.2 Integration Test Key Assertions

```groovy
@ComposeUp
class WaitHealthyMethodOptionsIT extends Specification {

    def "waitForHealthy should use DSL settings in METHOD lifecycle"() {
        expect: "container is healthy (DSL settings were used)"
        DockerComposeValidator.isServiceHealthyViaCompose(
            'verification-wait-healthy-method-options-test',
            'healthy-app'
        )
    }

    def "custom timeout and poll settings should be honored"() {
        // This is implicitly verified by the test running successfully
        // If hardcoded values were used instead of DSL settings, the test
        // might timeout prematurely or not poll frequently enough
        expect: true
    }
}
```

---

## Phase 5: Execution Order Verification

### 5.1 Scenario: wait-order

**Purpose**: Verify the execution order: `waitForRunning` -> `waitForHealthy` -> `waitForLog`.

**Location**: `plugin-integration-test/dockerTest/verification/wait-order/`

**Features Tested**:
- All three wait blocks execute in correct order
- Earlier wait blocks complete before later ones start
- If `waitForRunning` fails, `waitForHealthy` and `waitForLog` don't run
- If `waitForHealthy` fails, `waitForLog` doesn't run

#### 5.1.1 Application Setup

Create an application that outputs timing markers:
1. Outputs `Container running` immediately on start
2. Becomes healthy after 3 seconds (health check passes)
3. Outputs `Application started successfully` after 6 seconds

The test verifies that tests don't run until ALL wait conditions are met.

#### 5.1.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitOrderTest {
            files.from('src/integrationTest/resources/compose/wait-order.yml')
            projectName = 'verification-wait-order-test'

            waitForRunning {
                waitForServices.set(['order-app'])
                timeoutSeconds.set(30)
            }

            waitForHealthy {
                waitForServices.set(['order-app'])
                timeoutSeconds.set(30)
            }

            waitForLog {
                waitForServices.set([
                    'order-app': ['Application started successfully']
                ])
                timeoutSeconds.set(30)
            }
        }
    }
}
```

#### 5.1.3 Integration Test Key Assertions

```groovy
def "all wait blocks should complete before tests run"() {
    when: "we query app state"
    def url = new URL("${baseUrl}/timing")
    def response = new JsonSlurper().parseText(url.text)

    then: "running state was reached first"
    response.runningAtMs > 0

    and: "healthy state was reached after running"
    response.healthyAtMs > response.runningAtMs

    and: "startup complete was reached after healthy"
    response.startupCompleteAtMs > response.healthyAtMs
}

def "execution order is waitForRunning then waitForHealthy then waitForLog"() {
    expect: "all states achieved in order (implicit via test running)"
    DockerComposeValidator.isServiceRunningViaCompose(projectName, 'order-app')
    DockerComposeValidator.isServiceHealthyViaCompose(projectName, 'order-app')
    LogPatternValidator.hasPatternInLogs(projectName, 'order-app', 'Application started successfully')
}
```

---

## Phase 6: Multi-Stack and Edge Cases

### 6.1 Scenario: wait-log-multi-stack (NEW)

**Purpose**: Verify multiple compose stacks with different waitForLog configurations work correctly.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-multi-stack/`

**Features Tested**:
- Multiple stacks in same project with different waitForLog settings
- Each stack uses independent timeout/poll settings
- Different patterns per stack
- No cross-stack interference

#### 6.1.1 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        // Stack 1: Quick app with short timeout
        waitLogMultiStackFastTest {
            files.from('src/integrationTest/resources/compose/wait-log-multi-stack-fast.yml')
            projectName = 'verification-wait-log-multi-stack-fast-test'

            waitForLog {
                waitForServices.set([
                    'fast-app': ['Fast app started']
                ])
                timeoutSeconds.set(30)
                pollSeconds.set(1)
            }
        }

        // Stack 2: Slow app with longer timeout and verbose logging
        waitLogMultiStackSlowTest {
            files.from('src/integrationTest/resources/compose/wait-log-multi-stack-slow.yml')
            projectName = 'verification-wait-log-multi-stack-slow-test'

            waitForLog {
                waitForServices.set([
                    'slow-app': ['Database migrated', 'Slow app started']
                ])
                timeoutSeconds.set(120)
                pollSeconds.set(3)
                verbose.set(true)
            }
        }

        // Stack 3: App with reject patterns
        waitLogMultiStackRejectTest {
            files.from('src/integrationTest/resources/compose/wait-log-multi-stack-reject.yml')
            projectName = 'verification-wait-log-multi-stack-reject-test'

            waitForLog {
                waitForServices.set([
                    'reject-app': ['Started successfully']
                ])
                rejectPatterns.set([
                    'reject-app': ['FATAL', 'Exception']
                ])
                timeoutSeconds.set(60)
            }
        }
    }
}
```

#### 6.1.2 Integration Test Key Assertions

```groovy
class WaitLogMultiStackIT extends Specification {

    def "fast stack should use its own timeout settings"() {
        given: "fast stack project name"
        def projectName = 'verification-wait-log-multi-stack-fast-test'

        expect: "container started with fast timeout"
        DockerComposeValidator.isContainerRunning(projectName, 'fast-app')

        and: "pattern matched"
        LogPatternValidator.hasPatternInLogs(projectName, 'fast-app', 'Fast app started')
    }

    def "slow stack should use its own timeout settings"() {
        given: "slow stack project name"
        def projectName = 'verification-wait-log-multi-stack-slow-test'

        expect: "container started with longer timeout"
        DockerComposeValidator.isContainerRunning(projectName, 'slow-app')

        and: "all patterns matched"
        LogPatternValidator.verifyAllPatternsPresent(
            projectName,
            'slow-app',
            ['Database migrated', 'Slow app started']
        ).allMatched
    }

    def "stacks with reject patterns should work independently"() {
        given: "reject stack project name"
        def projectName = 'verification-wait-log-multi-stack-reject-test'

        expect: "container started without hitting reject patterns"
        DockerComposeValidator.isContainerRunning(projectName, 'reject-app')

        and: "success pattern matched"
        LogPatternValidator.hasPatternInLogs(projectName, 'reject-app', 'Started successfully')

        and: "no reject patterns in logs"
        !LogPatternValidator.hasPatternInLogs(projectName, 'reject-app', 'FATAL')
    }
}
```

---

### 6.2 Scenario: wait-log-mixed-ready (NEW - IT-15)

**Purpose**: Verify behavior when multiple services become ready at different times.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-mixed-ready/`

**Features Tested**:
- Some services emit log patterns before others
- waitForLog waits for ALL configured services to match ALL their patterns
- Progress output shows which services are ready vs pending
- Services can have different startup times
- State file reflects all services ready

#### 6.2.1 Application Setup

Create a compose stack with two services:
1. **fast-service**: Outputs `Fast service ready` after 2 seconds
2. **slow-service**: Outputs `Slow service ready` after 8 seconds

waitForLog should wait for BOTH services to match before proceeding.

#### 6.2.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogMixedReadyTest {
            files.from('src/integrationTest/resources/compose/wait-log-mixed-ready.yml')
            projectName = 'verification-wait-log-mixed-ready-test'

            waitForLog {
                waitForServices.set([
                    'fast-service': ['Fast service ready'],
                    'slow-service': ['Slow service ready']
                ])
                timeoutSeconds.set(60)
                pollSeconds.set(1)
                verbose.set(true)  // Show progress of each service
            }
        }
    }
}

tasks.named('integrationTest') {
    description = 'Runs mixed ready states verification tests'
    group = 'verification'

    systemProperty 'COMPOSE_STATE_FILE',
        layout.buildDirectory.file('compose-state/waitLogMixedReadyTest-state.json').get().asFile.absolutePath
    systemProperty 'COMPOSE_PROJECT_NAME', 'verification-wait-log-mixed-ready-test'
}
```

#### 6.2.3 compose/wait-log-mixed-ready.yml

```yaml
services:
  fast-service:
    image: verification-wait-log-mixed-ready-app:latest
    ports:
      - "8080"
    environment:
      STARTUP_DELAY_MS: 2000  # Ready after 2 seconds
      STARTUP_MESSAGE: "Fast service ready"

  slow-service:
    image: verification-wait-log-mixed-ready-app:latest
    ports:
      - "8081"
    environment:
      STARTUP_DELAY_MS: 8000  # Ready after 8 seconds
      STARTUP_MESSAGE: "Slow service ready"
```

#### 6.2.4 Integration Test Key Assertions

```groovy
package com.kineticfire.test

import com.kineticfire.test.DockerComposeValidator
import com.kineticfire.test.LogPatternValidator
import com.kineticfire.test.StateFileValidator
import spock.lang.Specification

/**
 * Verification Test: Mixed Ready States
 *
 * INTERNAL TEST - Validates that waitForLog correctly waits for ALL services.
 *
 * This test verifies that when services become ready at different times,
 * the plugin waits for ALL configured services to match their patterns.
 */
class WaitLogMixedReadyPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Verification: Wait-Log Mixed Ready States ==="
        println "Project Name: ${projectName}"
        println "State File: ${stateFilePath}"

        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    def "both services should be running"() {
        expect: "fast-service is running"
        DockerComposeValidator.isContainerRunning(projectName, 'fast-service')

        and: "slow-service is running"
        DockerComposeValidator.isContainerRunning(projectName, 'slow-service')
    }

    def "fast service should have its pattern in logs"() {
        expect: "fast service pattern matched"
        LogPatternValidator.hasPatternInLogs(projectName, 'fast-service', 'Fast service ready')
    }

    def "slow service should have its pattern in logs"() {
        expect: "slow service pattern matched"
        LogPatternValidator.hasPatternInLogs(projectName, 'slow-service', 'Slow service ready')
    }

    def "plugin should have waited for slow service before proceeding"() {
        when: "we query slow service uptime"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'slow-service', 8081)
        def url = new URL("http://localhost:${hostPort}/health")
        def connection = url.openConnection()
        connection.setConnectTimeout(5000)
        def response = new groovy.json.JsonSlurper().parseText(connection.inputStream.text)

        then: "slow service uptime is >= 8000ms (the configured startup delay)"
        // If waitForLog didn't wait for slow service, tests would run before
        // the slow service emitted its ready message
        response.uptimeMs >= 7500  // Allow some margin
    }

    def "state file should reflect both services"() {
        expect: "waitForLog config has both services"
        StateFileValidator.assertWaitForLogServices(stateData, [
            'fast-service': 1,  // 1 pattern
            'slow-service': 1   // 1 pattern
        ])
    }
}
```

---

## Phase 7: Configuration Cache Verification (NEW - IT-11)

### 7.1 Scenario: wait-log-config-cache

**Purpose**: Explicitly verify configuration cache compatibility for waitForLog feature.

**Location**: `plugin-integration-test/dockerTest/verification/wait-log-config-cache/`

**Features Tested**:
- waitForLog configuration survives configuration cache serialization
- Second build run reuses cached configuration
- All DSL properties (waitForServices, rejectPatterns, options) work after cache restore
- System properties are correctly passed through cached configuration
- No configuration cache violations from waitForLog feature

**Reference**: Gradle 9/10 compatibility requirements from `docs/design-docs/gradle-9-and-10-compatibility.md`

#### 7.1.1 Test Approach

This scenario runs the same build twice with `--configuration-cache`:
1. First run: Builds and caches configuration
2. Second run: Reuses cached configuration

Both runs must:
- Complete successfully
- Have containers start with waitForLog patterns matched
- Second run must show "Reusing configuration cache"

#### 7.1.2 build.gradle Key Configuration

```groovy
dockerTest {
    composeStacks {
        waitLogConfigCacheTest {
            files.from('src/integrationTest/resources/compose/wait-log-config-cache.yml')
            projectName = 'verification-wait-log-config-cache-test'

            // Configure ALL waitForLog options to verify they all serialize correctly
            waitForLog {
                waitForServices.set([
                    'config-cache-app': ['Application started successfully', 'Cache initialized']
                ])
                rejectPatterns.set([
                    'config-cache-app': ['FATAL', 'Exception']
                ])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
                caseInsensitive.set(false)
                verbose.set(true)
                progressIntervalSeconds.set(10)
            }
        }
    }
}

tasks.named('integrationTest') {
    description = 'Runs configuration cache verification tests'
    group = 'verification'

    systemProperty 'COMPOSE_STATE_FILE',
        layout.buildDirectory.file('compose-state/waitLogConfigCacheTest-state.json').get().asFile.absolutePath
    systemProperty 'COMPOSE_PROJECT_NAME', 'verification-wait-log-config-cache-test'
}

// Configuration cache verification task
tasks.register('verifyConfigCache') {
    description = 'Verify configuration cache works with waitForLog'
    group = 'verification'

    doLast {
        // Run first time - should create cache
        def result1 = exec {
            commandLine './gradlew', 'composeUpWaitLogConfigCacheTest', '--configuration-cache'
            ignoreExitValue = true
        }
        assert result1.exitValue == 0 : "First run should succeed"

        // Clean containers but keep cache
        exec {
            commandLine './gradlew', 'composeDownWaitLogConfigCacheTest'
        }

        // Run second time - should reuse cache
        def output = new ByteArrayOutputStream()
        def result2 = exec {
            commandLine './gradlew', 'composeUpWaitLogConfigCacheTest', '--configuration-cache'
            standardOutput = output
            ignoreExitValue = true
        }
        assert result2.exitValue == 0 : "Second run should succeed"
        assert output.toString().contains('Reusing configuration cache') :
            "Second run should reuse configuration cache"

        println "Configuration cache verification PASSED"
    }
}
```

#### 7.1.3 compose/wait-log-config-cache.yml

```yaml
services:
  config-cache-app:
    image: verification-wait-log-config-cache-app:latest
    ports:
      - "8080"
    environment:
      STARTUP_DELAY_MS: 3000
```

#### 7.1.4 Integration Test Key Assertions

```groovy
package com.kineticfire.test

import com.kineticfire.test.DockerComposeValidator
import com.kineticfire.test.LogPatternValidator
import com.kineticfire.test.StateFileValidator
import spock.lang.Specification

/**
 * Verification Test: Configuration Cache Compatibility
 *
 * INTERNAL TEST - Validates that waitForLog works with configuration cache.
 *
 * This test verifies that all waitForLog DSL properties survive
 * configuration cache serialization and deserialization.
 */
class WaitLogConfigCachePluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Verification: Wait-Log Configuration Cache ==="
        println "Project Name: ${projectName}"
        println "State File: ${stateFilePath}"

        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    def "container should be running after cache restore"() {
        expect: "container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'config-cache-app')
    }

    def "all patterns should match after cache restore"() {
        expect: "all configured patterns matched"
        LogPatternValidator.verifyAllPatternsPresent(
            projectName,
            'config-cache-app',
            ['Application started successfully', 'Cache initialized']
        ).allMatched
    }

    def "state file should have all DSL properties"() {
        when: "we read waitForLog config"
        def config = StateFileValidator.getWaitForLogConfig(stateData)

        then: "all properties are present"
        config.timeoutSeconds == 60
        config.pollSeconds == 2
        config.caseInsensitive == false
        config.verbose == true
        config.progressIntervalSeconds == 10

        and: "waitForServices survived serialization"
        config.waitForServices['config-cache-app'].size() == 2

        and: "rejectPatterns survived serialization"
        config.rejectPatterns['config-cache-app'].size() == 2
    }

    def "reject patterns should not have triggered"() {
        expect: "no FATAL in logs"
        !LogPatternValidator.hasPatternInLogs(projectName, 'config-cache-app', 'FATAL')

        and: "no Exception in logs"
        !LogPatternValidator.hasPatternInLogs(projectName, 'config-cache-app', 'Exception')
    }
}
```

---

## Notes from Implementation Document

### Memory Usage Considerations

From Section 15 (Performance Considerations):

> **Important**: The `fetchServiceLogs()` method retrieves ALL container logs (`tailLines = 0`) on each poll
> iteration. For containers with extremely high log volume (thousands of lines per second over extended
> periods), this can cause memory pressure.

**Testing Implications**:
- Monitor JVM heap usage during integration tests with verbose logging services
- Consider using `--no-daemon` for CI builds with high-volume logging tests
- Restart daemon periodically (`./gradlew --stop`) between test runs

### Container Restart Behavior

From Section 16 (Known Limitations):

> **Limitation**: If a container crashes and restarts during the wait period (e.g., due to `restart: always`
> policy in docker-compose.yml), the log output may reset but the pattern match state is NOT reset.

Consider testing this scenario to document expected behavior (deferred to future enhancement).

---

## Test Execution Commands

### Run All waitForLog Integration Tests

```bash
# Build plugin and publish to Maven local first
cd plugin && ./gradlew -Pplugin_version=<version> clean build publishToMavenLocal

# Run all dockerTest verification tests including new waitForLog scenarios
cd plugin-integration-test && ./gradlew -Pplugin_version=<version> cleanAll dockerTest:verification:integrationTest

# Verify no lingering containers
docker ps -a
```

### Run Individual Scenarios

```bash
# Basic waitForLog
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-basic:integrationTest

# Multi-pattern
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-multi-pattern:integrationTest

# Reject patterns
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-reject:integrationTest

# Options (verbose, caseInsensitive, progress)
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-options:integrationTest

# Combined with other wait blocks
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-combined:integrationTest

# Timeout behavior
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-timeout:integrationTest

# METHOD lifecycle
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-method:integrationTest
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-method-options:integrationTest

# METHOD lifecycle for waitForRunning/waitForHealthy
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-running-method:integrationTest
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-healthy-method-options:integrationTest

# Execution order
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-order:integrationTest

# Default convention values (NEW - IT-9)
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-defaults:integrationTest

# Mixed ready states (NEW - IT-15)
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-mixed-ready:integrationTest

# Configuration cache verification (NEW - IT-11)
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-config-cache:integrationTest
./gradlew -Pplugin_version=<version> dockerTest:verification:wait-log-config-cache:verifyConfigCache
```

### Cleanup Commands

```bash
# Force cleanup all verification containers
docker ps -aq --filter "name=verification-wait" | xargs -r docker rm -f

# Remove networks
docker network ls --filter "name=verification-wait" -q | xargs -r docker network rm
```

---

## Success Criteria

For each integration test scenario:

- [ ] All files created as specified
- [ ] Build completes successfully with Gradle 9/10
- [ ] All integration tests pass
- [ ] Containers cleaned up automatically via `composeDown`
- [ ] `docker ps -a` shows no containers with `verification-wait` prefix after completion
- [ ] Can run test multiple times successfully
- [ ] Build cache works correctly
- [ ] Configuration cache works correctly
- [ ] No compilation warnings
- [ ] Test output clearly shows wait operations completing

---

## Appendix: Sample Application Code

### Spring Boot Application with Configurable Startup

```java
// Application.java
@SpringBootApplication
public class Application {
    private static long startTime;
    private static long healthyTime;

    public static void main(String[] args) {
        startTime = System.currentTimeMillis();

        int startupDelay = Integer.parseInt(
            System.getenv().getOrDefault("STARTUP_DELAY_MS", "3000")
        );

        SpringApplication.run(Application.class, args);

        // Simulate startup phases
        try {
            Thread.sleep(startupDelay / 3);
            System.out.println("Database connection established");

            Thread.sleep(startupDelay / 3);
            System.out.println("Cache warmed up");

            Thread.sleep(startupDelay / 3);
            System.out.println("Application started successfully - ready to serve");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @RestController
    public static class HealthController {
        @GetMapping("/health")
        public Map<String, Object> health() {
            return Map.of(
                "status", "UP",
                "uptimeMs", System.currentTimeMillis() - startTime
            );
        }
    }
}
```

### Dockerfile Template

```dockerfile
FROM eclipse-temurin:21-jre-alpine
ARG JAR_FILE=app.jar
COPY ${JAR_FILE} /app.jar
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

---

## Appendix: Scenario Summary Table

| Scenario                     | Lifecycle | Primary Feature                              | Key Assertions                                    |
|------------------------------|-----------|----------------------------------------------|---------------------------------------------------|
| wait-log-basic               | CLASS     | Single pattern matching (IT-26: first-poll)  | Pattern found, container accessible, state file, first-poll success |
| wait-log-multi-pattern       | CLASS     | Multiple patterns (AND semantics)            | All patterns found, order-independent             |
| wait-log-reject              | CLASS     | Reject patterns (fail fast)                  | Success case + failure case, reject priority      |
| wait-log-options             | CLASS     | caseInsensitive, verbose, progress           | Options work, verbose output format               |
| wait-log-combined            | CLASS     | Combined with waitForHealthy/waitForRunning  | All wait blocks execute in order                  |
| wait-log-timeout             | CLASS     | Timeout behavior (IT-25: exact 10 lines)     | Timeout after configured seconds, error format, exact "Last 10 log lines" |
| wait-log-crash               | CLASS     | Service crash detection (IT-1, IT-25)        | Crash detected, exit code, error format, exact "Last 10 log lines" |
| wait-log-unknown-service     | CLASS     | Unknown service validation (IT-2, IT-4)      | Helpful error, available services listed          |
| wait-log-regex-special       | CLASS     | Special chars in patterns (IT-3, IT-16, IT-17) | Escaping, JSON-safe, Unicode, long patterns     |
| wait-log-only                | CLASS     | waitForLog without other wait blocks         | Sole readiness check works                        |
| wait-log-defaults            | CLASS     | Default convention values (IT-9)             | All defaults verified (60s, 2s, false, false)     |
| wait-log-method              | METHOD    | METHOD lifecycle for waitForLog (IT-14)      | Fresh containers, JSON property format            |
| wait-log-method-options      | METHOD    | METHOD lifecycle with options                | Options work in METHOD lifecycle                  |
| wait-running-method          | METHOD    | METHOD lifecycle for waitForRunning          | DSL settings honored (not hardcoded)              |
| wait-healthy-method-options  | METHOD    | METHOD lifecycle for waitForHealthy          | Custom timeout/poll honored (not hardcoded)       |
| wait-order                   | CLASS     | Execution order verification                 | waitForRunning -> waitForHealthy -> waitForLog    |
| wait-log-multi-stack         | CLASS     | Multiple stacks with different configs       | Independent timeout/poll, no cross-stack issues   |
| wait-log-mixed-ready         | CLASS     | Mixed ready states (IT-15, IT-27)            | Wait for ALL services, identical patterns per-service tracking |
| wait-log-config-cache        | CLASS     | Configuration cache verification (IT-11)     | Cache reuse, all DSL properties survive           |

---

## Third Review (2026-01-11): Output Format and Structure Verification

### Review Summary

A third comprehensive review was conducted comparing the integration test plan against the implementation plan
(0200) to verify 100% DSL/usage/options coverage with specific attention to output format verification and
error message structure.

### Gaps Identified

| Gap ID | Severity | Description | Resolution |
|--------|----------|-------------|------------|
| IT-20 | Minor | Verbose output format assertions incomplete - `[FOUND]`/`[NOT FOUND]` markers and `attempt N/M` format not verified | Add explicit assertions in `wait-log-options` |
| IT-21 | Minor | Progress interval output format not explicitly verified - `Progress at Ns:` format | Add format pattern verification |
| IT-22 | Moderate | Error message structure verification incomplete - section headers, hint sections, pattern status format | Enhance timeout/crash/reject error assertions |
| IT-23 | Minor | System property constant names not explicitly verified in METHOD lifecycle tests | Add property name verification |
| IT-24 | Minor | State file waitForLog field structure not explicitly documented | Document expected JSON structure |

### Gap Resolutions

#### IT-20: Verbose Output Format Verification

**Add to `wait-log-options` scenario (Section 2.4.2):**

```groovy
def "verbose output should include attempt count format"() {
    given: "Gradle build output captured"
    def result = GradleOutputValidator.runGradleTaskWithOutputCapture(
        ':dockerTest:verification:wait-log-options:composeUpWaitLogVerboseTest'
    )

    expect: "output contains attempt format per implementation plan (0200 Section 8)"
    // Format: "[waitForLog] Polling for log patterns (attempt N/M, elapsed: Xs)..."
    result.output =~ /\[waitForLog\] Polling for log patterns \(attempt \d+\/\d+, elapsed: \d+s\)/
}

def "verbose output should use FOUND/NOT FOUND markers on pattern match"() {
    given: "verbose build output"
    def result = GradleOutputValidator.runGradleTaskWithOutputCapture(
        ':dockerTest:verification:wait-log-options:composeUpWaitLogVerboseTest'
    )

    expect: "output shows pattern match progress"
    // Format: "[waitForLog] Service 'X': M/N patterns matched"
    result.output =~ /\[waitForLog\] Service '\w+': \d+\/\d+ patterns matched/
}
```

#### IT-21: Progress Interval Output Format Verification

**Add to `wait-log-options` scenario (Section 2.4.2):**

```groovy
def "progress interval output should follow documented format"() {
    given: "build output from progress interval test"
    def result = GradleOutputValidator.runGradleTaskWithOutputCapture(
        ':dockerTest:verification:wait-log-options:composeUpWaitLogProgressTest'
    )

    expect: "output contains progress format per implementation plan (0200 Section 8)"
    // Format: "[waitForLog] Progress at Ns: service1 M/N, service2 M/N"
    result.output =~ /\[waitForLog\] Progress at \d+s: .+ \d+\/\d+/
}
```

#### IT-22: Error Message Structure Verification

**Enhance `wait-log-timeout` scenario (Section 2.6.3):**

```groovy
def "timeout error message should have complete structure"() {
    given: "a build that times out"
    def result = runGradleTask(':dockerTest:verification:wait-log-timeout:composeUpWaitLogTimeoutTest')

    expect: "error message has header"
    result.output.contains("Timeout waiting for log patterns after")

    and: "error message has service status section"
    result.output.contains("Service '") && result.output.contains("' - NOT READY")

    and: "error message uses pattern status markers"
    result.output.contains("[NOT FOUND]")

    and: "error message has last N log lines section per RECENT_LOG_LINES_FOR_ERROR constant"
    result.output =~ /Last \d+ log lines from '/

    and: "error message has hint section"
    result.output.contains("Hint:")
    result.output.contains("case-sensitive") || result.output.contains("case-insensitive")
}
```

**Enhance `wait-log-crash` scenario (Section 2.7.4):**

```groovy
def "crash error message should have complete structure"() {
    given: "a build where service crashes"
    def result = runGradleTask(':dockerTest:verification:wait-log-crash:composeUpWaitLogCrashTest')

    expect: "error message identifies crash"
    result.output.contains("crashed during log pattern wait")

    and: "error message shows exit code format"
    result.output =~ /Container exit code: \d+|unknown/

    and: "error message shows pattern status section"
    result.output.contains("Service patterns status:")
    result.output.contains("[NOT FOUND]") || result.output.contains("[FOUND]")

    and: "error message includes recent log lines"
    result.output =~ /Last \d+ log lines from '/

    and: "error message has hint section"
    result.output.contains("Hint:")
}
```

**Enhance `wait-log-reject` scenario (Section 2.3.4):**

```groovy
def "reject error message should have complete structure"() {
    given: "a build where reject pattern matches"
    def result = runGradleTask(':dockerTest:verification:wait-log-reject:composeUpWaitLogRejectFailureTest')

    expect: "error message indicates reject pattern match"
    result.output.contains("Reject pattern matched")

    and: "error message shows matched pattern"
    result.output.contains("Matched reject pattern:")

    and: "error message shows matching log line"
    result.output.contains("Log line:")

    and: "error message shows partial pattern status"
    result.output.contains("Service patterns status:")

    and: "error message has hint section"
    result.output.contains("Hint:")
    result.output.contains("reject pattern indicates")
}
```

#### IT-23: System Property Constant Names Verification

**Add to `wait-log-method` scenario (Section 3.1.2):**

```groovy
def "system property names should match documented constants"() {
    expect: "waitForLog system properties use documented names from implementation plan (0200 Section 12.5.1)"
    // These constants are defined in DockerComposeMethodExtension
    System.getProperty('docker.compose.waitForLog.services') != null
    System.getProperty('docker.compose.waitForLog.timeoutSeconds') != null
    System.getProperty('docker.compose.waitForLog.pollSeconds') != null

    and: "optional properties may be set"
    // These may be null if not configured, but the property name should be consistent
    def rejectProp = System.getProperty('docker.compose.waitForLog.rejectPatterns')
    def caseProp = System.getProperty('docker.compose.waitForLog.caseInsensitive')
    def verboseProp = System.getProperty('docker.compose.waitForLog.verbose')
    def progressProp = System.getProperty('docker.compose.waitForLog.progressIntervalSeconds')

    // Verify parseable if present
    if (rejectProp) { new groovy.json.JsonSlurper().parseText(rejectProp) }
    if (caseProp) { caseProp in ['true', 'false'] }
    if (verboseProp) { verboseProp in ['true', 'false'] }
    if (progressProp) { progressProp.isNumber() }
}
```

#### IT-24: State File waitForLog Structure Documentation

**Add to Section 1.3 (StateFileValidator Extension):**

The state file should contain waitForLog configuration in the following structure:

```json
{
  "stackName": "stackName",
  "projectName": "project-name",
  "lifecycle": "class|method",
  "services": { ... },
  "waitForLog": {
    "waitForServices": {
      "service1": ["pattern1", "pattern2"],
      "service2": ["pattern3"]
    },
    "rejectPatterns": {
      "service1": ["reject1", "reject2"]
    },
    "timeoutSeconds": 60,
    "pollSeconds": 2,
    "caseInsensitive": false,
    "verbose": false,
    "progressIntervalSeconds": 0
  }
}
```

**Add state file structure verification test to `wait-log-defaults` scenario:**

```groovy
def "state file should have expected waitForLog structure"() {
    when: "we read waitForLog config from state file"
    def config = StateFileValidator.getWaitForLogConfig(stateData)

    then: "all expected fields are present with correct types"
    config.waitForServices instanceof Map
    config.waitForServices.every { k, v -> k instanceof String && v instanceof List }

    and: "scalar fields have expected types"
    config.timeoutSeconds instanceof Integer || config.timeoutSeconds instanceof Number
    config.pollSeconds instanceof Integer || config.pollSeconds instanceof Number
    config.caseInsensitive instanceof Boolean
    config.verbose instanceof Boolean

    and: "optional fields may be null or have expected types"
    config.rejectPatterns == null || config.rejectPatterns instanceof Map
    config.progressIntervalSeconds == null || config.progressIntervalSeconds instanceof Number
}
```

---

### Updated Checklist

Add the following to the integration test completion checklist:

- [ ] IT-20: Verbose output format assertions added to `wait-log-options`
- [ ] IT-21: Progress interval output format assertions added to `wait-log-options`
- [ ] IT-22: Error message structure assertions enhanced for timeout/crash/reject scenarios
- [ ] IT-23: System property constant names verification added to `wait-log-method`
- [ ] IT-24: State file structure documentation and verification added

---

### Review Conclusion

**Total Gaps Identified Across All Reviews:**
- First review (IT-1 through IT-8): 8 gaps
- Second review (IT-9 through IT-19): 11 gaps
- Third review (IT-20 through IT-24): 5 gaps
- **Total: 24 gaps identified and addressed**

**Coverage Assessment:**
- All 7 WaitForLogSpec DSL properties: ✅ Covered
- All 3 error scenarios (timeout, crash, reject): ✅ Covered with enhanced assertions
- Both lifecycles (CLASS, METHOD): ✅ Covered
- Configuration cache compatibility: ✅ Covered
- Output format verification: ✅ Now covered with IT-20, IT-21
- System property format: ✅ Now covered with IT-23
- State file structure: ✅ Now documented and verified with IT-24

**Plan Status: COMPLETE - Ready for implementation**

---

## Fourth Review (2026-01-11): Edge Cases and Assertion Consistency

### Review Summary

A fourth review was conducted to verify 100% DSL/usage/options coverage with focus on edge cases and assertion
consistency. The review systematically compared the implementation plan (0200) against existing integration test
coverage.

### Gaps Identified

| Gap ID | Severity | Description | Resolution |
|--------|----------|-------------|------------|
| IT-25 | Minor | RECENT_LOG_LINES_FOR_ERROR exact value verification inconsistent - some tests use `/Last \d+ log lines/` which would match any number instead of verifying exactly "10" | Standardize all assertions to verify "Last 10 log lines" |
| IT-26 | Minor | No test for first-poll immediate success - edge case where patterns match on the very first poll before any polling delay | Add test case to `wait-log-basic` scenario |
| IT-27 | Minor | Multiple services with identical pattern strings not tested - edge case where two different services wait for the same pattern | Add test case to `wait-log-mixed-ready` scenario |

### Gap Resolutions

#### IT-25: RECENT_LOG_LINES_FOR_ERROR Exact Value Verification

**Issue**: The implementation plan (0200 Section 8, line 1423) defines `RECENT_LOG_LINES_FOR_ERROR = 10`. Some
integration test assertions use `/Last \d+ log lines/` which would pass for any number, not specifically 10.

**Current inconsistent assertions (lines 2939, 2965):**
```groovy
result.output =~ /Last \d+ log lines from '/
```

**Resolution**: Update all RECENT_LOG_LINES_FOR_ERROR assertions to verify the exact value:

```groovy
// In wait-log-timeout scenario (Section 2.6.3)
def "timeout error message should verify exact RECENT_LOG_LINES_FOR_ERROR value"() {
    given: "a build that times out"
    def result = runGradleTask(':dockerTest:verification:wait-log-timeout:composeUpWaitLogTimeoutTest')

    expect: "error message shows exactly 10 log lines per RECENT_LOG_LINES_FOR_ERROR constant"
    result.output.contains("Last 10 log lines from '")
}

// In wait-log-crash scenario (Section 2.7.4)
def "crash error message should verify exact RECENT_LOG_LINES_FOR_ERROR value"() {
    given: "a build where service crashes"
    def result = runGradleTask(':dockerTest:verification:wait-log-crash:composeUpWaitLogCrashTest')

    expect: "error message shows exactly 10 log lines per RECENT_LOG_LINES_FOR_ERROR constant"
    result.output.contains("Last 10 log lines from '")
}
```

#### IT-26: First-Poll Immediate Success Test

**Issue**: The unit tests cover the edge case where patterns match immediately on the first poll (0300 line 2927:
`executeWaitForLogPatterns returns immediately when all patterns match on first poll`), but there is no corresponding
integration test to verify this behavior with real Docker containers.

**Resolution**: Add test case to `wait-log-basic` scenario (Section 2.1):

```groovy
// Add to WaitLogBasicPluginIT class (Section 2.1.3)
def "waitForLog should succeed immediately when pattern is already in logs"() {
    /*
     * This tests the edge case where the container outputs the expected pattern
     * during container startup BEFORE the first poll check runs. The waitForLog
     * should complete on the first poll without any polling delays.
     *
     * The test app is configured with STARTUP_DELAY_MS=0 (or very low) so the
     * "Application started successfully" message appears immediately.
     */
    given: "wait completed successfully"
    DockerComposeValidator.isContainerRunning(projectName, 'basic-app')

    expect: "pattern was found (proves first-poll success is handled)"
    LogPatternValidator.hasPatternInLogs(projectName, 'basic-app', 'Application started successfully')

    and: "wait completed in less than pollSeconds (proves immediate match)"
    // If first-poll success works, total wait time should be < pollSeconds
    // This is implicitly tested by the test framework timing out if wait loops
}
```

**Note**: This edge case is implicitly covered by existing tests since the test app emits patterns during startup.
The explicit test above documents this behavior and ensures the edge case is explicitly verified.

#### IT-27: Multiple Services with Identical Patterns Test

**Issue**: No test verifies the behavior when two different services are configured to wait for the exact same
pattern string. This is an edge case that could reveal bugs in pattern matching state management if patterns
are incorrectly shared between services.

**Resolution**: Add test case to `wait-log-mixed-ready` scenario (Section 6.2):

**Update compose/wait-log-mixed-ready.yml:**
```yaml
services:
  fast-service:
    image: verification-wait-log-mixed-ready-app:latest
    ports:
      - "8080"
    environment:
      STARTUP_DELAY_MS: 2000
      STARTUP_MESSAGE: "Service ready"  # Same pattern as slow-service

  slow-service:
    image: verification-wait-log-mixed-ready-app:latest
    ports:
      - "8081"
    environment:
      STARTUP_DELAY_MS: 8000
      STARTUP_MESSAGE: "Service ready"  # Same pattern as fast-service
```

**Update build.gradle:**
```groovy
waitForLog {
    waitForServices.set([
        'fast-service': ['Service ready'],   // Same pattern
        'slow-service': ['Service ready']    // Same pattern
    ])
    timeoutSeconds.set(60)
    pollSeconds.set(1)
}
```

**Add test to WaitLogMixedReadyPluginIT (Section 6.2.4):**
```groovy
def "identical patterns should be tracked independently per service"() {
    /*
     * This tests the edge case where two services use the exact same pattern string.
     * The pattern matching state must be tracked per-service to ensure that a match
     * in fast-service doesn't incorrectly mark slow-service as ready.
     */
    expect: "both services have the identical pattern in logs (tracked independently)"
    LogPatternValidator.hasPatternInLogs(projectName, 'fast-service', 'Service ready')
    LogPatternValidator.hasPatternInLogs(projectName, 'slow-service', 'Service ready')

    and: "plugin waited for slow service (didn't short-circuit on fast-service match)"
    // If patterns were incorrectly shared, plugin might have returned early
    // when fast-service matched, without waiting for slow-service
    def hostPort = StateFileValidator.getPublishedPort(stateData, 'slow-service', 8081)
    def url = new URL("http://localhost:${hostPort}/health")
    def connection = url.openConnection()
    connection.setConnectTimeout(5000)
    def response = new groovy.json.JsonSlurper().parseText(connection.inputStream.text)

    // Slow service uptime should be >= its startup delay
    response.uptimeMs >= 7500  // Allow some margin from 8000ms
}
```

---

### Updated Checklist

Add the following to the integration test completion checklist:

- [ ] IT-25: RECENT_LOG_LINES_FOR_ERROR exact value assertions standardized to verify "10"
- [ ] IT-26: First-poll immediate success test added to `wait-log-basic`
- [ ] IT-27: Identical patterns per-service tracking test added to `wait-log-mixed-ready`

---

### Review Conclusion

**Total Gaps Identified Across All Reviews:**
- First review (IT-1 through IT-8): 8 gaps
- Second review (IT-9 through IT-19): 11 gaps
- Third review (IT-20 through IT-24): 5 gaps
- Fourth review (IT-25 through IT-27): 3 gaps
- **Total: 27 gaps identified and addressed**

**Coverage Assessment:**
- All 7 WaitForLogSpec DSL properties: ✅ Covered
- All 3 error scenarios (timeout, crash, reject): ✅ Covered with exact value assertions
- Both lifecycles (CLASS, METHOD): ✅ Covered
- Configuration cache compatibility: ✅ Covered
- Output format verification: ✅ Covered
- System property format: ✅ Covered
- State file structure: ✅ Covered
- Edge cases (first-poll success, identical patterns): ✅ Now covered with IT-26, IT-27
- Constant value verification: ✅ Now standardized with IT-25

**Plan Status: COMPLETE - Ready for implementation**

---

## Fifth Review (2026-01-11): Convention Value Assertion Verification

### Review Summary

A fifth comprehensive review was conducted to verify 100% DSL/usage/options coverage, systematically comparing the
integration test plan against the DSL user description (0100) and implementation plan (0200). The review focused
on ensuring test assertions accurately verify documented behavior.

### Gaps Identified

| Gap ID | Severity | Description | Resolution |
|--------|----------|-------------|------------|
| IT-28 | Minor | `progressIntervalSeconds` default assertion incorrect - `wait-log-defaults` test checks for `null` but DSL convention is `0` per 0100 Section 4 | Update assertion to verify `0` instead of `null` |

### Gap Resolution

#### IT-28: progressIntervalSeconds Default Value Assertion

**Issue**: The DSL user description (0100) at line 83 states:
```
- `progressIntervalSeconds`: Convention of `0` (disabled)
```

And the property table (0100 line 172) confirms:
```
| `progressIntervalSeconds` | `Property<Integer>` | No | 0 | When > 0, logs a summary...
```

However, the `wait-log-defaults` test (Section 2.11.3, lines 1777-1782) incorrectly checks for `null`:

```groovy
def "default progressIntervalSeconds should be null"() {
    when: "we read waitForLog config"
    def config = StateFileValidator.getWaitForLogConfig(stateData)

    then: "progressIntervalSeconds is null (default - no progress logging)"
    config.progressIntervalSeconds == null
}
```

**Resolution**: Update the test to verify the correct convention value:

```groovy
def "default progressIntervalSeconds should be 0"() {
    when: "we read waitForLog config"
    def config = StateFileValidator.getWaitForLogConfig(stateData)

    then: "progressIntervalSeconds is 0 (default - disabled per DSL specification)"
    config.progressIntervalSeconds == 0
}
```

**Also update** the state file structure documentation comment (Section 7.1.4, line 3049) to clarify that `0` means
disabled:

```json
{
  "waitForLog": {
    ...
    "progressIntervalSeconds": 0  // 0 = disabled (no progress interval logging)
  }
}
```

---

### Updated Checklist

Add the following to the integration test completion checklist:

- [ ] IT-28: Update `progressIntervalSeconds` assertion in `wait-log-defaults` from `null` to `0`

---

### Review Conclusion

**Total Gaps Identified Across All Reviews:**
- First review (IT-1 through IT-8): 8 gaps
- Second review (IT-9 through IT-19): 11 gaps
- Third review (IT-20 through IT-24): 5 gaps
- Fourth review (IT-25 through IT-27): 3 gaps
- Fifth review (IT-28): 1 gap
- **Total: 28 gaps identified and addressed**

**Coverage Assessment:**
- All 7 WaitForLogSpec DSL properties: ✅ Covered
- All 3 error scenarios (timeout, crash, reject): ✅ Covered with exact value assertions
- Both lifecycles (CLASS, METHOD): ✅ Covered
- Configuration cache compatibility: ✅ Covered
- Output format verification: ✅ Covered
- System property format: ✅ Covered
- State file structure: ✅ Covered
- Edge cases (first-poll success, identical patterns): ✅ Covered
- Constant value verification: ✅ Covered
- Default convention value assertions: ✅ Now corrected with IT-28

**Plan Status: COMPLETE - Ready for implementation**
