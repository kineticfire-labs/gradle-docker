# Design Document: Add `waitForLog` Block to `dockerTest` DSL

## Overview

The `waitForLog` block is a new readiness check mechanism for the `dockerTest` DSL that waits for specific log output
from containers before considering them ready. It complements the existing `waitForHealthy` (waits for Docker health
check to pass) and `waitForRunning` (waits for container state to be "running") blocks. Unlike those blocks which
accept a simple list of service names, `waitForLog` requires per-service configuration mapping each service to one or
more regex patterns that must appear in its logs. This feature is useful for services that don't have health checks or
require application-specific readiness indicators beyond basic container status.

## DSL / User Description

### Functionality Goals

1. **Log-Based Readiness Detection**: Wait for specific text patterns to appear in container logs before proceeding
   with tests
2. **Per-Service Configuration**: Each service specifies its own readiness pattern(s) since different services have
   different startup indicators
3. **Multiple Pattern Support**: Support multiple patterns per service where ALL patterns must match (multi-phase
   startup detection)
4. **Regex Matching**: Use regex patterns for flexibility (e.g., `Started .* in \d+ seconds` handles variable content)
5. **Case-Insensitive Option**: Optional case-insensitive matching for simpler pattern configuration
6. **Reject Pattern Support**: Fail fast when error patterns appear in logs (e.g., `FATAL`, `Exception`)
7. **Configurable Progress Logging**: Optional verbose logging of partial match progress to help debug slow startups
8. **Composability**: Can be used alone or in combination with `waitForHealthy` and/or `waitForRunning`
9. **Consistent Timeout Behavior**: Same timeout and polling configuration as existing wait blocks

### Spec Class

The `waitForLog` block uses a dedicated `WaitForLogSpec` class (separate from `WaitSpec` used by `waitForHealthy` and
`waitForRunning`) to provide type-safe, per-service pattern configuration:

```groovy
abstract class WaitForLogSpec {
    abstract MapProperty<String, List<String>> getWaitForServices()
    abstract MapProperty<String, List<String>> getRejectPatterns()
    abstract Property<Integer> getTimeoutSeconds()
    abstract Property<Integer> getPollSeconds()
    abstract Property<Boolean> getCaseInsensitive()
    abstract Property<Boolean> getVerbose()
    abstract Property<Integer> getProgressIntervalSeconds()
}
```

**Property Conventions:**
- `waitForServices`: **No convention** - must be explicitly set. Validation will fail if not configured or empty.
- `timeoutSeconds`: Convention of `60` (seconds)
- `pollSeconds`: Convention of `2` (seconds)
- `rejectPatterns`: Convention of empty map `[:]` (explicitly set to ensure `isPresent()` returns true)
- `caseInsensitive`: Convention of `false`
- `verbose`: Convention of `false`
- `progressIntervalSeconds`: Convention of `0` (disabled)

This matches the pattern established by `WaitSpec` where `waitForServices` has no convention to enable fail-fast
validation when the block is configured without specifying services.

The property name `waitForServices` maintains consistency with `WaitSpec` used by `waitForHealthy` and
`waitForRunning`. The enclosing block name (`waitForLog`) provides context, and the map syntax
`['service': ['pattern']]` clearly indicates the different configuration structure.

### DSL Syntax

The `waitForLog` block uses a map-based syntax where each service maps to a list of patterns.

**Syntax Note:** Groovy DSL supports two equivalent syntaxes for property assignment, both of which are configuration-
cache compatible:

```groovy
// Style 1: Explicit .set() method (recommended for consistency with existing documentation)
waitForLog {
    waitForServices.set([
        'app': ['Started Application']
    ])
    timeoutSeconds.set(60)
}

// Style 2: Direct assignment (Groovy converts this to a set() call)
waitForLog {
    waitForServices = [
        'app': ['Started Application']
    ]
    timeoutSeconds = 60
}
```

Both styles invoke the same underlying mechanism and are fully supported.

```groovy
dockerTest {
    composeStacks {
        myTest {
            files.from('src/integrationTest/resources/compose/app.yml')

            // NEW: Log-based readiness check
            waitForLog {
                // MapProperty<String, List<String>> - all values are lists
                // Single patterns are specified as one-element lists
                // Multiple patterns = ALL must match before service is considered ready
                waitForServices.set([
                    'app': ['Started Application in .* seconds'],
                    'db': ['PostgreSQL init complete', 'ready to accept connections'],
                    'redis': ['Ready to accept connections']
                ])

                // Optional: Fail immediately if these patterns appear
                rejectPatterns.set([
                    'app': ['FATAL', 'Exception.*startup'],
                    'db': ['FATAL', 'failed to start']
                ])

                // Optional: Case-insensitive matching (default: false)
                caseInsensitive.set(false)

                // Optional: Verbose progress logging (default: false)
                verbose.set(true)

                // Optional: Timeout in seconds (default: 60)
                timeoutSeconds.set(120)

                // Optional: Poll interval in seconds (default: 2)
                pollSeconds.set(2)

                // Optional: Log progress summary at this interval in seconds (default: 0 = disabled)
                progressIntervalSeconds.set(30)
            }
        }
    }
}
```

### Configuration Properties

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `waitForServices` | `MapProperty<String, List<String>>` | Yes | - | Map of service names to pattern list. ALL patterns in the list must match before the service is considered ready. |
| `rejectPatterns` | `MapProperty<String, List<String>>` | No | empty | Map of service names to reject pattern list. If ANY pattern matches, the wait fails immediately with an error. |
| `caseInsensitive` | `Property<Boolean>` | No | `false` | When `true`, all pattern matching is case-insensitive. |
| `verbose` | `Property<Boolean>` | No | `false` | When `true`, logs detailed progress during polling including which patterns have matched and when. |
| `timeoutSeconds` | `Property<Integer>` | No | 60 | Maximum seconds to wait for all patterns to appear before failing. |
| `pollSeconds` | `Property<Integer>` | No | 2 | How often (in seconds) to check container logs for patterns. |
| `progressIntervalSeconds` | `Property<Integer>` | No | 0 | When > 0, logs a summary of pattern match status at this interval (in seconds) even when `verbose` is false. Set to 0 to disable. Useful for long-running waits to show progress without full verbose output. |

### Configuration Cache Compatibility

The `WaitForLogSpec` class must be compatible with Gradle 9/10 configuration cache:

1. **Use Property API**: All configuration values use `Property<T>` or `MapProperty<K,V>` for lazy evaluation
2. **No Project References**: The spec class must not hold references to `Project` or other non-serializable objects
3. **Serializable Values**: Pattern values (strings and lists) are inherently serializable

### Pattern Matching Semantics

1. **Regex Patterns**: All patterns are interpreted as Java regex patterns
2. **Substring Match**: Patterns are matched against each log line as a substring (no need to match entire line)
3. **Line-by-Line**: Each log line is checked independently; multi-line patterns are not supported. Docker treats each
   `\n`-delimited output as a separate log entry. Multi-line messages (like Java stack traces) become multiple separate
   log entries. To match part of a stack trace, match individual lines (e.g., `'Caused by: NullPointerException'` or
   `'at com\\.example\\.'`). This is a Docker limitation, not plugin behavior.
4. **Case Sensitivity**: Pattern matching is case-sensitive by default; use `caseInsensitive.set(true)` for
   case-insensitive matching. Alternatively, use regex syntax for per-pattern control: `'(?i)started application'`
5. **Multiple Patterns (AND)**: When a service has multiple patterns in `waitForServices`, ALL patterns must be found
   (in any order) before the service is considered ready
6. **First Match Sufficient**: A pattern only needs to match once. Once matched in any log line, it is considered
   "found" for the duration of that wait operation.
7. **ANY Semantics**: To match ANY of several patterns, use regex alternation: `'(Started|Ready|Initialized)'`
8. **Reject Patterns (OR)**: When a service has reject patterns, if ANY reject pattern matches, the wait fails
   immediately. The `rejectPatterns` map can be a subset of services in `waitForServices` - not all services require
   reject patterns.
9. **Reject Pattern Priority**: On each poll iteration, reject patterns are checked **before** success patterns. If
   any reject pattern matches, the wait fails immediately without checking success patterns. This ensures fast failure
   when error conditions are detected.

### Regex Escaping

Patterns use Java regex syntax. Common special characters that need escaping with `\\`:

| Character | Escaped | Example Use Case |
|-----------|---------|------------------|
| `.` | `\\.` | Match literal dot: `'version 1\\.0\\.0'` |
| `[` `]` | `\\[` `\\]` | Match brackets: `'\\[INFO\\] Server started'` |
| `(` `)` | `\\(` `\\)` | Match parentheses: `'init\\(\\) complete'` |
| `$` | `\\$` | Match dollar sign: `'\\$HOME is set'` |
| `*` `+` `?` | `\\*` `\\+` `\\?` | Match literal: `'waiting\\.\\.\\.'` |

**Tip:** For complex literal strings, consider case-insensitive matching instead of escaping everything.

### Usage Examples

#### Simple Case: One Pattern Per Service

```groovy
waitForLog {
    waitForServices.set([
        'app': ['Started Application'],
        'db': ['ready to accept connections']
    ])
    // Uses defaults: timeoutSeconds=60, pollSeconds=2, caseInsensitive=false, verbose=false
}
```

#### Multiple Patterns: Multi-Phase Startup

```groovy
// Wait for database to complete initialization AND be ready for connections
waitForLog {
    waitForServices.set([
        'app': ['Database connection established', 'Started Application'],
        'db': ['PostgreSQL init process complete', 'ready to accept connections']
    ])
    timeoutSeconds.set(120)
    pollSeconds.set(2)
}
```

#### Case-Insensitive Matching

```groovy
waitForLog {
    waitForServices.set([
        'app': ['started application'],  // matches "Started Application", "STARTED APPLICATION", etc.
        'db': ['ready']
    ])
    caseInsensitive.set(true)
    timeoutSeconds.set(60)
}
```

#### Verbose Progress Logging

```groovy
waitForLog {
    waitForServices.set([
        'app': ['Database connection established', 'Started Application'],
        'db': ['PostgreSQL init complete', 'ready to accept connections']
    ])
    verbose.set(true)  // Enable detailed progress logging
    timeoutSeconds.set(120)
}
```

#### Reject Patterns: Fail Fast on Errors

```groovy
waitForLog {
    waitForServices.set([
        'app': ['Started Application'],
        'db': ['ready to accept connections']
    ])

    // Fail immediately if any of these patterns appear
    rejectPatterns.set([
        'app': ['FATAL', 'Exception', 'Error initializing'],
        'db': ['FATAL', 'failed to initialize', 'out of memory']
    ])

    timeoutSeconds.set(60)
}
```

If a reject pattern matches, the wait fails immediately with a message like:

```
Reject pattern matched in service 'app' - failing immediately.
  Matched reject pattern: 'Exception'
  Log line: "[2025-01-03 10:15:35] ERROR Exception during startup: NullPointerException"

  Service patterns status:
    [FOUND]     'Database connection established' - matched at 12s
    [NOT FOUND] 'Started Application'

Hint: A reject pattern indicates the service encountered an error during startup.
      Check the full container logs for details.
```

#### Combined with Other Wait Blocks

Users can combine all three wait mechanisms for comprehensive readiness checking:

```groovy
composeStacks {
    myTest {
        files.from('compose.yml')

        // Step 1: Wait for containers to be running
        waitForRunning {
            waitForServices.set(['nginx'])  // No health check available
            timeoutSeconds.set(30)
        }

        // Step 2: Wait for health checks to pass
        waitForHealthy {
            waitForServices.set(['app', 'db'])
            timeoutSeconds.set(60)
        }

        // Step 3: Wait for application-specific log output
        waitForLog {
            waitForServices.set([
                'app': ['Startup complete - ready to serve requests']
            ])
            timeoutSeconds.set(30)
        }
    }
}
```

**Execution Order**: When multiple wait blocks are specified, they execute in order:
`waitForRunning` -> `waitForHealthy` -> `waitForLog`

**Note:** The current implementation executes wait blocks in the order `waitForHealthy` -> `waitForRunning`. This
change updates the execution order to `waitForRunning` -> `waitForHealthy` -> `waitForLog` because waiting for
running before healthy reflects the natural startup sequence: containers must be running before health checks can
pass, and health checks should pass before checking for application-specific log messages.

**Important**: If a preceding wait block fails (e.g., `waitForRunning` times out or `waitForHealthy` fails), the
subsequent wait blocks are **not executed**. The failure is reported immediately and the compose stack is torn down.
This fail-fast behavior prevents wasted time waiting for log patterns when the container hasn't even reached a
running or healthy state.

### Common Patterns for Popular Services

```groovy
waitForLog {
    waitForServices.set([
        // Spring Boot
        'spring-app': ['Started .* in .* seconds'],

        // PostgreSQL
        'postgres': ['database system is ready to accept connections'],

        // MySQL (note: MySQL logs "ready for connections" on its own line)
        'mysql': ['ready for connections'],

        // Redis
        'redis': ['Ready to accept connections'],

        // Elasticsearch
        'elasticsearch': ['started'],

        // Kafka
        'kafka': ['\\[KafkaServer id=\\d+\\] started'],

        // Node.js (Express)
        'node-app': ['Server listening on port \\d+'],

        // Nginx
        'nginx': ['start worker process']
    ])
    timeoutSeconds.set(120)
}
```

### ⚠️ Performance Considerations

**Important**: The `waitForLog` feature fetches **all container logs** on each poll iteration to ensure patterns
that appeared early in startup are not missed. For containers with high log volume (thousands of lines per second),
this can cause performance degradation.

**Recommendations for High-Volume Logging Services:**

1. **Use longer poll intervals**: Set `pollSeconds.set(5)` or higher to reduce log fetch frequency
2. **Choose early patterns**: Select patterns that appear early in startup to minimize wait time
3. **Consider health checks**: For services with high log volume, prefer `waitForHealthy` when possible
4. **Limit monitored services**: Only include services that truly need log-based readiness detection

**Example for high-volume services:**

```groovy
waitForLog {
    waitForServices.set([
        'high-volume-app': ['Application started']  // Single, early pattern
    ])
    pollSeconds.set(5)      // Less frequent polling
    timeoutSeconds.set(120) // Allow more time between polls
}
```

### Progress Logging

Progress logging behavior depends on the `verbose` setting:

#### Default Behavior (`verbose.set(false)` or not specified)

Minimal logging showing only key events:

```
[waitForLog] Waiting for log patterns in 2 services (timeout: 60s)...
[waitForLog] All services ready after 22 seconds
```

#### Verbose Behavior (`verbose.set(true)`)

Detailed logging during the wait period to help users understand startup status and debug slow-starting services:

```
[waitForLog] Polling for log patterns (attempt 1/30, elapsed: 0s)...
[waitForLog] Service 'db': 0/2 patterns matched
[waitForLog] Service 'app': 0/2 patterns matched

[waitForLog] Polling for log patterns (attempt 5/30, elapsed: 8s)...
[waitForLog] Service 'db': 1/2 patterns matched
  [FOUND] 'PostgreSQL init process complete' - matched at 4s
[waitForLog] Service 'app': 0/2 patterns matched

[waitForLog] Polling for log patterns (attempt 10/30, elapsed: 18s)...
[waitForLog] Service 'db': 2/2 patterns matched - READY
[waitForLog] Service 'app': 1/2 patterns matched
  [FOUND] 'Database connection established' - matched at 15s

[waitForLog] Polling for log patterns (attempt 12/30, elapsed: 22s)...
[waitForLog] Service 'app': 2/2 patterns matched - READY
[waitForLog] All services ready after 22 seconds
```

Verbose progress logging is written to the Gradle console (stdout) during task execution:
- Shows which patterns have matched and when they matched
- Indicates remaining patterns that haven't matched yet
- Helps users tune timeout values based on actual startup times
- Makes it clear which service is causing delays
- Useful for debugging slow startups or pattern mismatches

#### Periodic Progress Logging (`progressIntervalSeconds`)

For long-running waits where full verbose output would be excessive, use `progressIntervalSeconds` to log a summary
at regular intervals:

```groovy
waitForLog {
    waitForServices.set([
        'app': ['Database migrated', 'Cache warmed', 'Started Application']
    ])
    timeoutSeconds.set(300)     // 5 minute timeout
    progressIntervalSeconds.set(30)  // Log summary every 30 seconds
}
```

Output example:

```
[waitForLog] Waiting for log patterns in 1 service (timeout: 300s)...
[waitForLog] Progress at 30s: app 1/3 patterns matched
[waitForLog] Progress at 60s: app 2/3 patterns matched
[waitForLog] Progress at 90s: app 2/3 patterns matched
[waitForLog] All services ready after 95 seconds
```

This provides visibility into long waits without the per-poll detail of verbose mode. Set to 0 (default) to disable
periodic progress logging.

### Validation Error Messages

The plugin validates configuration at **task configuration time** (not execution time) and provides clear error
messages. This fail-fast approach ensures invalid configurations are caught early, before any containers are started.
All regex patterns are compiled during configuration to verify syntax validity.

#### Empty `waitForServices`

If `waitForServices` is not configured or is empty:

```
Configuration error in 'waitForLog' block: 'waitForServices' cannot be empty.
At least one service with patterns must be specified.

Example:
    waitForLog {
        waitForServices.set([
            'app': ['Started Application']
        ])
    }
```

#### Empty Pattern List for a Service

If a service is configured with an empty pattern list, a **hard validation error** is raised at configuration time
(build fails before any containers start):

```
Configuration error in 'waitForLog' block: Pattern list for service 'app' cannot be empty.
Each service must have at least one pattern to match.

Example:
    waitForServices.set([
        'app': ['Started Application']  // At least one pattern required
    ])
```

This validation prevents silent misconfiguration where a service would be considered "ready" immediately due to
having no patterns to match.

#### Invalid Regex Pattern

If a pattern contains invalid regex syntax:

```
Configuration error in 'waitForLog' block: Invalid regex pattern for service 'app'.
  Pattern: 'Started [invalid'
  Error: Unclosed character class near index 8

Hint: Escape special regex characters with \\ (e.g., \\[ to match literal [)
```

### Error Handling

When a timeout occurs, the plugin provides a detailed error message including elapsed time and match timing
(regardless of `verbose` setting):

```
Timeout waiting for log patterns after 60 seconds.

Service 'app' - NOT READY (1/2 patterns matched):
  [FOUND]     'Database connection established' - matched at 12s
  [NOT FOUND] 'Started Application'

Service 'db' - READY (2/2 patterns matched):
  [FOUND] 'PostgreSQL init process complete' - matched at 4s
  [FOUND] 'ready to accept connections' - matched at 8s

Last 10 log lines from 'app':
  [2025-01-03 10:15:32] INFO  Initializing Spring context...
  [2025-01-03 10:15:33] INFO  Database connection established
  [2025-01-03 10:15:34] INFO  Loading application beans...
  [2025-01-03 10:15:35] ERROR Failed to initialize cache service
  ...

Hint: Check if the expected log message format matches your pattern.
      Patterns are case-sensitive regex expressions (use caseInsensitive.set(true) for case-insensitive matching).
      Use verbose.set(true) to see detailed progress during polling.
```

#### Service Crash During Wait

If a container exits or crashes during the wait period:

```
Service 'app' crashed during log pattern wait.
  Container exit code: 1
  Container status: exited

  Service patterns status:
    [FOUND]     'Database connection established' - matched at 12s
    [NOT FOUND] 'Started Application'

  Last 10 log lines from 'app':
    [2025-01-03 10:15:32] INFO  Initializing Spring context...
    [2025-01-03 10:15:33] INFO  Database connection established
    [2025-01-03 10:15:34] ERROR OutOfMemoryError: Java heap space
    [2025-01-03 10:15:34] FATAL Application terminated unexpectedly

Hint: The container exited before all patterns were matched.
      Check the container logs with: docker logs <container-id>
      Review the application startup configuration and resource limits.
```

### Total Timeout Calculation

When combining multiple wait blocks, each block has its own independent timeout. The total maximum wait time is the
sum of all configured timeouts:

```groovy
waitForRunning {
    waitForServices.set(['nginx'])
    timeoutSeconds.set(30)    // Max 30 seconds for running check
}
waitForHealthy {
    waitForServices.set(['app', 'db'])
    timeoutSeconds.set(60)    // Max 60 seconds for health checks
}
waitForLog {
    waitForServices.set(['app': ['Ready to serve']])
    timeoutSeconds.set(120)   // Max 120 seconds for log patterns
}
// Total maximum wait time: 30 + 60 + 120 = 210 seconds
```

Plan CI pipeline timeouts accordingly to account for the cumulative wait time.

### Integration with Test Framework Extensions

When using `usesCompose()` with test framework extensions, `waitForLog` is automatically executed as part of the
compose stack startup:

```groovy
// build.gradle
dockerTest {
    composeStacks {
        myTest {
            files.from('compose.yml')
            waitForLog {
                waitForServices.set(['app': ['Ready to serve']])
                timeoutSeconds.set(60)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "myTest", lifecycle: "class")
}
```

```groovy
// Test class - no additional configuration needed
@ComposeUp
class MyAppIT extends Specification {
    // waitForLog is automatically applied before tests run
}
```

### Limitations

⚠️ **Initial Release**: The `waitForLog` block is only supported with `Lifecycle.CLASS` in the current release.
`Lifecycle.METHOD` support will be added in a future release.

| Wait Block | `Lifecycle.CLASS` | `Lifecycle.METHOD` |
|------------|-------------------|-------------------|
| `waitForRunning` | ✅ Supported | ✅ Supported |
| `waitForHealthy` | ✅ Supported | ✅ Supported |
| `waitForLog` | ✅ Supported | ❌ Not yet supported |

If you need log-based readiness with per-method compose lifecycle, either:
1. Use `Lifecycle.CLASS` instead (compose stack shared across all test methods)
2. Wait for a future release that adds `Lifecycle.METHOD` support for `waitForLog`

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

3. **ComposeStackSpec.getName()**: ✅ Verified - `ComposeStackSpec` has a `getName()` method that returns the stack
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
1. ✅ Constructor injection: `ComposeStackSpec(String name, ObjectFactory objectFactory)`
2. ✅ `getName()` method returning the stack name (line 46-48)
3. ✅ `objectFactory` field available for creating nested specs
4. ✅ Existing pattern for `waitForHealthy` and `waitForRunning` DSL methods

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
- `timeService: TimeService` - ✅ Exists
- `processExecutor: ProcessExecutor` - ✅ Exists
- `getComposeCommand()` - ✅ Exists
- `serviceLogger: ServiceLogger` - ✅ Exists

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

**Main Method** (refactored into smaller, focused methods per code quality standards - methods ≤ 30-40 lines):

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
            throw e
        } catch (java.util.concurrent.ExecutionException e) {
            // Unwrap ExecutionException to expose actual cause
            throw e.cause ?: e
        } catch (Exception e) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                "Error waiting for log patterns: ${e.message}",
                e
            )
        }
    })
}

/**
 * Core implementation of wait-for-log-patterns logic.
 * Separated from the main method to keep CompletableFuture handling separate from business logic.
 */
private Map<String, WaitForLogResult> executeWaitForLogPatterns(WaitForLogConfig config) {
    logWaitStart(config)
    def matchState = initializeMatchState(config)
    return pollForPatterns(config, matchState)
}

/**
 * Log the start of the wait operation.
 */
private void logWaitStart(WaitForLogConfig config) {
    def serviceCount = config.services.size()
    def servicePlural = serviceCount == 1 ? "" : "s"
    serviceLogger.info("[waitForLog] Waiting for log patterns in ${serviceCount} service${servicePlural} (timeout: ${config.timeout.toSeconds()}s)...")
}

/**
 * Initialize match tracking state for all services.
 * @return Map with 'matchedPatterns', 'matchTimes', 'startTime', 'lastProgressLog' keys
 */
private Map initializeMatchState(WaitForLogConfig config) {
    def startTime = timeService.currentTimeMillis()
    return [
        matchedPatterns: config.services.collectEntries { [(it): new HashSet<Integer>()] },
        matchTimes: config.services.collectEntries { [(it): [:]] },
        startTime: startTime,
        lastProgressLog: startTime,
        attempt: 0
    ]
}

/**
 * Main polling loop - checks patterns until all match or timeout.
 */
private Map<String, WaitForLogResult> pollForPatterns(WaitForLogConfig config, Map matchState) {
    def timeoutMillis = config.timeout.toMillis()
    def progressIntervalMillis = config.progressInterval.toMillis()

    while (timeService.currentTimeMillis() - matchState.startTime < timeoutMillis) {
        matchState.attempt++
        def elapsedSeconds = (timeService.currentTimeMillis() - matchState.startTime) / 1000

        logVerbosePollingStart(config, matchState.attempt, elapsedSeconds)

        def allReady = checkAllServices(config, matchState, elapsedSeconds)

        matchState.lastProgressLog = logPeriodicProgress(
            config, matchState, progressIntervalMillis, elapsedSeconds
        )

        if (allReady) {
            return handleAllServicesReady(config, matchState)
        }

        timeService.sleep(config.pollInterval.toMillis())
    }

    // Timeout reached
    handleTimeout(config, matchState)
}

/**
 * Check all services for pattern matches. Returns true if all services are ready.
 */
private boolean checkAllServices(WaitForLogConfig config, Map matchState, Number elapsedSeconds) {
    boolean allReady = true

    for (String serviceName : config.services) {
        def serviceReady = checkServicePatterns(config, matchState, serviceName, elapsedSeconds)
        if (!serviceReady) {
            allReady = false
        }
    }

    return allReady
}

/**
 * Check patterns for a single service. Returns true if service is ready.
 */
private boolean checkServicePatterns(WaitForLogConfig config, Map matchState,
                                      String serviceName, Number elapsedSeconds) {
    def patterns = config.servicePatterns[serviceName]
    def serviceMatchedPatterns = matchState.matchedPatterns[serviceName]
    def serviceMatchTimes = matchState.matchTimes[serviceName]

    // Skip if already fully matched
    if (serviceMatchedPatterns.size() == patterns.size()) {
        logVerboseServiceReady(config, serviceName, patterns.size())
        return true
    }

    // Check if container is still running
    verifyServiceRunning(config, matchState, serviceName)

    // Fetch and check logs
    def logLines = fetchServiceLogs(config.projectName, serviceName)
    checkForRejectPatterns(config, matchState, serviceName, logLines)
    updateServiceMatches(config, matchState, serviceName, logLines, elapsedSeconds)

    return serviceMatchedPatterns.size() == patterns.size()
}

/**
 * Verify service is still running; throw if crashed.
 */
private void verifyServiceRunning(WaitForLogConfig config, Map matchState, String serviceName) {
    if (!isServiceRunning(config.projectName, serviceName)) {
        def results = buildResults(config, matchState.matchedPatterns, matchState.matchTimes)
        throw new ComposeServiceException(
            ComposeServiceException.ErrorType.SERVICE_CRASHED,
            buildCrashErrorMessage(serviceName, results, config),
            "Check container logs with: docker logs <container-id>"
        )
    }
}

/**
 * Fetch logs for a service.
 *
 * <p>Wraps captureLogs() with better error context for user-facing messages.</p>
 *
 * @param projectName Compose project name
 * @param serviceName Service name to fetch logs for
 * @return List of log lines (empty lines filtered out)
 * @throws ComposeServiceException if log capture fails
 */
private List<String> fetchServiceLogs(String projectName, String serviceName) {
    try {
        def logsConfig = new LogsConfig([serviceName], 0, false, null)  // 0 = all logs
        def logs = captureLogs(projectName, logsConfig).get()
        return logs.split('\n').findAll { it?.trim() }
    } catch (Exception e) {
        // Wrap with user-friendly error message
        throw new ComposeServiceException(
            ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED,
            "Failed to fetch logs for service '${serviceName}' in project '${projectName}': ${e.message}",
            "Check that the service exists and containers are running. " +
            "You can manually verify with: docker compose -p ${projectName} logs ${serviceName}",
            e
        )
    }
}

/**
 * Check for reject patterns; throw if any match.
 */
private void checkForRejectPatterns(WaitForLogConfig config, Map matchState,
                                     String serviceName, List<String> logLines) {
    def rejectCheck = checkRejectPatterns(config, serviceName, logLines)
    if (rejectCheck != null) {
        def results = buildResults(config, matchState.matchedPatterns, matchState.matchTimes)
        throw new ComposeServiceException(
            ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED,
            buildRejectErrorMessage(serviceName, rejectCheck, results, config),
            "A reject pattern indicates the service encountered an error during startup."
        )
    }
}

/**
 * Update pattern matches for a service.
 */
private void updateServiceMatches(WaitForLogConfig config, Map matchState,
                                   String serviceName, List<String> logLines, Number elapsedSeconds) {
    def patterns = config.servicePatterns[serviceName]
    def serviceMatchedPatterns = matchState.matchedPatterns[serviceName]
    def serviceMatchTimes = matchState.matchTimes[serviceName]

    LogPatternMatcher.updateMatches(
        patterns, serviceMatchedPatterns, logLines, elapsedSeconds.longValue(), serviceMatchTimes
    )

    if (config.verbose) {
        logServiceProgress(serviceName, patterns, serviceMatchedPatterns, serviceMatchTimes)
    }
}

/**
 * Handle successful completion when all services are ready.
 */
private Map<String, WaitForLogResult> handleAllServicesReady(WaitForLogConfig config, Map matchState) {
    def elapsedTotal = (timeService.currentTimeMillis() - matchState.startTime) / 1000
    serviceLogger.info("[waitForLog] All services ready after ${elapsedTotal.intValue()} seconds")
    return buildResults(config, matchState.matchedPatterns, matchState.matchTimes)
}

/**
 * Handle timeout - throws ComposeServiceException with detailed error message.
 */
private void handleTimeout(WaitForLogConfig config, Map matchState) {
    def results = buildResults(config, matchState.matchedPatterns, matchState.matchTimes)
    throw new ComposeServiceException(
        ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
        buildTimeoutErrorMessage(config, results),
        "Use verbose.set(true) to see detailed progress during polling."
    )
}

// Verbose logging helpers

private void logVerbosePollingStart(WaitForLogConfig config, int attempt, Number elapsedSeconds) {
    if (config.verbose) {
        serviceLogger.info("[waitForLog] Polling for log patterns (attempt ${attempt}/${config.totalWaitAttempts}, elapsed: ${elapsedSeconds.intValue()}s)...")
    }
}

private void logVerboseServiceReady(WaitForLogConfig config, String serviceName, int patternCount) {
    if (config.verbose) {
        serviceLogger.info("[waitForLog] Service '${serviceName}': ${patternCount}/${patternCount} patterns matched - READY")
    }
}

private long logPeriodicProgress(WaitForLogConfig config, Map matchState,
                                  long progressIntervalMillis, Number elapsedSeconds) {
    if (progressIntervalMillis > 0 && !config.verbose) {
        def now = timeService.currentTimeMillis()
        if (now - matchState.lastProgressLog >= progressIntervalMillis) {
            logProgressSummary(config, matchState.matchedPatterns, elapsedSeconds.intValue())
            return now
        }
    }
    return matchState.lastProgressLog
}

// Helper methods for waitForLogPatterns implementation
// (Add as private methods in ExecLibraryComposeService)

private LogPatternMatcher.RejectCheckResult checkRejectPatterns(
        WaitForLogConfig config, String serviceName, List<String> logLines) {
    def rejectPatterns = config.rejectPatterns[serviceName]
    if (rejectPatterns == null || rejectPatterns.isEmpty()) {
        return null
    }
    return LogPatternMatcher.checkRejectPatterns(rejectPatterns, logLines)
}

/**
 * Check if a service container is still running.
 *
 * <p>Uses JSON output format for reliable parsing instead of text matching,
 * which could incorrectly match container names or column headers.</p>
 *
 * @param projectName Compose project name
 * @param serviceName Service name to check
 * @return true if service is running, false otherwise
 */
private boolean isServiceRunning(String projectName, String serviceName) {
    try {
        def composeCommand = getComposeCommand()
        // Use JSON format for reliable parsing instead of text matching
        def command = composeCommand + ["-p", projectName, "ps", serviceName, "--format", "json"]
        def result = processExecutor.execute(command)
        if (result.isSuccess() && result.stdout) {
            // Parse JSON output - each line is a JSON object for a container
            def output = result.stdout.trim()
            if (output.isEmpty()) {
                return false
            }
            // Docker Compose v2 outputs one JSON object per line
            def jsonSlurper = new groovy.json.JsonSlurper()
            for (String line : output.split('\n')) {
                if (line.trim().isEmpty()) continue
                def container = jsonSlurper.parseText(line)
                // Check the State field - valid running states are "running" or "Up"
                def state = container.State?.toString()?.toLowerCase()
                if (state == "running" || state?.startsWith("up")) {
                    return true
                }
            }
        }
        return false
    } catch (Exception e) {
        serviceLogger.debug("Error checking service running state: ${e.message}")
        return false
    }
}

private Map<String, WaitForLogResult> buildResults(
        WaitForLogConfig config,
        Map<String, Set<Integer>> matchedPatterns,
        Map<String, Map<Integer, Long>> matchTimes) {
    return config.services.collectEntries { serviceName ->
        def patterns = config.servicePatterns[serviceName]
        def serviceMatched = matchedPatterns[serviceName]
        def serviceMatchTimes = matchTimes[serviceName]

        def patternMatches = patterns.withIndex().collect { pattern, index ->
            new WaitForLogResult.PatternMatch(
                pattern.pattern(),
                serviceMatched.contains(index),
                serviceMatchTimes[index]
            )
        }

        def ready = serviceMatched.size() == patterns.size()
        [(serviceName): new WaitForLogResult(serviceName, patternMatches, ready)]
    }
}

/**
 * Get recent log lines for a specific service (for error reporting).
 *
 * <p>This helper method fetches the last N lines of logs from a service container
 * to include in error messages, helping users diagnose issues.</p>
 *
 * @param projectName Compose project name
 * @param serviceName Service name
 * @param lineCount Number of recent lines to retrieve
 * @return List of recent log lines, or error message if retrieval fails
 */
private List<String> getRecentLogs(String projectName, String serviceName, int lineCount) {
    try {
        // Use existing LogsConfig signature: (services, tailLines, follow, outputFile)
        def config = new LogsConfig([serviceName], lineCount, false, null)
        def logs = captureLogs(projectName, config).get()
        return logs.split('\n').toList()
    } catch (Exception e) {
        serviceLogger.debug("Failed to get recent logs for '${serviceName}': ${e.message}")
        return ["(Unable to retrieve logs: ${e.message})"]
    }
}

private void logServiceProgress(String serviceName, List<Pattern> patterns,
                                 Set<Integer> matchedPatterns, Map<Integer, Long> matchTimes) {
    // Null safety for defensive programming
    def matchedCount = matchedPatterns?.size() ?: 0
    def totalPatterns = patterns?.size() ?: 0

    if (matchedCount == totalPatterns) {
        serviceLogger.info("[waitForLog] Service '${serviceName}': ${matchedCount}/${totalPatterns} patterns matched - READY")
    } else {
        serviceLogger.info("[waitForLog] Service '${serviceName}': ${matchedCount}/${totalPatterns} patterns matched")
        patterns?.eachWithIndex { pattern, index ->
            if (matchedPatterns?.contains(index)) {
                serviceLogger.info("  [FOUND] '${pattern.pattern()}' - matched at ${matchTimes?.get(index)}s")
            }
        }
    }
}

private void logProgressSummary(WaitForLogConfig config, Map<String, Set<Integer>> matchedPatterns, int elapsedSeconds) {
    def summary = config.services.collect { serviceName ->
        // Null safety for defensive programming
        def matched = matchedPatterns?.get(serviceName)?.size() ?: 0
        def total = config.servicePatterns?.get(serviceName)?.size() ?: 0
        "${serviceName} ${matched}/${total}"
    }.join(", ")
    serviceLogger.info("[waitForLog] Progress at ${elapsedSeconds}s: ${summary}")
}

private String buildTimeoutErrorMessage(WaitForLogConfig config, Map<String, WaitForLogResult> results) {
    def sb = new StringBuilder()
    sb.append("Timeout waiting for log patterns after ${config.timeout.toSeconds()} seconds.\n\n")

    results.each { serviceName, result ->
        def status = result.ready ? "READY" : "NOT READY"
        sb.append("Service '${serviceName}' - ${status} (${result.matchedCount}/${result.totalPatterns} patterns matched):\n")
        result.patternMatches.each { match ->
            if (match.matched) {
                sb.append("  [FOUND]     '${match.pattern}' - matched at ${match.matchedAtSeconds}s\n")
            } else {
                sb.append("  [NOT FOUND] '${match.pattern}'\n")
            }
        }

        // Add recent log lines for services that are not ready
        if (!result.ready) {
            sb.append("\n  Last ${RECENT_LOG_LINES_FOR_ERROR} log lines from '${serviceName}':\n")
            def recentLogs = getRecentLogs(config.projectName, serviceName, RECENT_LOG_LINES_FOR_ERROR)
            recentLogs.each { line ->
                sb.append("    ${line}\n")
            }
        }
        sb.append("\n")
    }

    sb.append("Hint: Check if the expected log message format matches your pattern.\n")
    sb.append("      Patterns are case-${config.caseInsensitive ? 'insensitive' : 'sensitive'} regex expressions.\n")
    return sb.toString()
}

private String buildRejectErrorMessage(String serviceName, LogPatternMatcher.RejectCheckResult rejectMatch,
                                        Map<String, WaitForLogResult> results, WaitForLogConfig config) {
    def sb = new StringBuilder()
    sb.append("Reject pattern matched in service '${serviceName}' - failing immediately.\n")
    sb.append("  Matched reject pattern: '${rejectMatch.patternString}'\n")
    sb.append("  Log line: \"${rejectMatch.matchingLogLine}\"\n\n")

    def result = results[serviceName]
    if (result != null) {
        sb.append("  Service patterns status:\n")
        result.patternMatches.each { match ->
            if (match.matched) {
                sb.append("    [FOUND]     '${match.pattern}' - matched at ${match.matchedAtSeconds}s\n")
            } else {
                sb.append("    [NOT FOUND] '${match.pattern}'\n")
            }
        }
    }

    // Add recent log lines for context
    sb.append("\n  Last ${RECENT_LOG_LINES_FOR_ERROR} log lines from '${serviceName}':\n")
    def recentLogs = getRecentLogs(config.projectName, serviceName, RECENT_LOG_LINES_FOR_ERROR)
    recentLogs.each { line ->
        sb.append("    ${line}\n")
    }

    return sb.toString()
}

private String buildCrashErrorMessage(String serviceName, Map<String, WaitForLogResult> results, WaitForLogConfig config) {
    def sb = new StringBuilder()
    sb.append("Service '${serviceName}' crashed during log pattern wait.\n\n")

    def result = results[serviceName]
    if (result != null) {
        sb.append("  Service patterns status:\n")
        result.patternMatches.each { match ->
            if (match.matched) {
                sb.append("    [FOUND]     '${match.pattern}' - matched at ${match.matchedAtSeconds}s\n")
            } else {
                sb.append("    [NOT FOUND] '${match.pattern}'\n")
            }
        }
    }

    // Add recent log lines for diagnostics
    sb.append("\n  Last ${RECENT_LOG_LINES_FOR_ERROR} log lines from '${serviceName}':\n")
    def recentLogs = getRecentLogs(config.projectName, serviceName, RECENT_LOG_LINES_FOR_ERROR)
    recentLogs.each { line ->
        sb.append("    ${line}\n")
    }

    sb.append("\nHint: The container exited before all patterns were matched.\n")
    sb.append("      Check the container logs with: docker logs <container-id>\n")
    sb.append("      Review the application startup configuration and resource limits.\n")
    return sb.toString()
}
```

### 9. ComposeServiceException Extension

Add new error types to `ComposeServiceException`. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/exception/ComposeServiceException.groovy`:

**Existing ErrorType enum** (add new values):

```groovy
enum ErrorType {
    COMPOSE_UNAVAILABLE("Docker Compose is not available. Please install Docker Compose v2."),
    COMPOSE_FILE_NOT_FOUND("Compose file not found. Check the file path."),
    SERVICE_START_FAILED("Service startup failed. Check compose configuration and dependencies."),
    SERVICE_STOP_FAILED("Service shutdown failed. Services may still be running."),
    SERVICE_TIMEOUT("Service did not reach desired state within timeout period."),
    PLATFORM_UNSUPPORTED("Docker Compose operations not supported on this platform."),
    LOGS_CAPTURE_FAILED("Failed to capture Docker Compose logs."),
    UNKNOWN("An unknown Docker Compose operation error occurred."),
    // NEW: Add these error types for waitForLog feature
    LOG_PATTERN_TIMEOUT("Timeout waiting for log patterns to appear."),
    LOG_REJECT_PATTERN_MATCHED("A reject pattern was matched in container logs."),
    SERVICE_CRASHED("Service container crashed during wait operation.")

    final String defaultSuggestion

    ErrorType(String defaultSuggestion) {
        this.defaultSuggestion = defaultSuggestion
    }
}
```

**Existing Constructors** (no changes needed - already support required patterns):

```groovy
// Constructor with ErrorType, message, and custom suggestion - ALREADY EXISTS
ComposeServiceException(ErrorType errorType, String message, String suggestion, Throwable cause = null) {
    super(message, cause)
    this.errorType = errorType
    this.suggestion = suggestion ?: errorType.defaultSuggestion
}

// Constructor with ErrorType and message (uses default suggestion) - ALREADY EXISTS
ComposeServiceException(ErrorType errorType, String message, Throwable cause = null) {
    super(message, cause)
    this.errorType = errorType
    this.suggestion = errorType.defaultSuggestion
}
```

### 10. ComposeUpTask Extension

Add `waitForLog*` properties and execution to `ComposeUpTask`. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy`:

**Required Imports** (add to existing imports):

```groovy
// Complete imports to add for waitForLog functionality
import com.kineticfire.gradle.docker.util.WaitForLogConfigBuilder
import org.gradle.api.provider.MapProperty
import java.time.Duration
```

**Add Flattened Input Properties** (add after existing `waitForRunning*` properties):

```groovy
// Flattened per Part 2 of compatibility guide to avoid @Nested serialization issues

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

**Update `performWaitIfConfigured` Method**

The current implementation executes in order: `waitForHealthy` → `waitForRunning`. This must be
changed to: `waitForRunning` → `waitForHealthy` → `waitForLog`.

**CURRENT implementation** (lines 148-192 in `ComposeUpTask.groovy`):

```groovy
/**
 * Wait for services to reach desired state if configured
 */
private void performWaitIfConfigured(String stackName, String projectName) {
    // Wait for healthy services (configured during configuration phase)
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

    // Wait for running services (configured during configuration phase)
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
}
```

**NEW implementation** (replace the entire method):

```groovy
/**
 * Wait for services to reach desired state if configured.
 *
 * <p>Execution order: waitForRunning → waitForHealthy → waitForLog</p>
 *
 * <p>This order reflects the natural startup sequence: containers must be running
 * before health checks can pass, and health checks should pass before checking
 * for application-specific log messages.</p>
 */
private void performWaitIfConfigured(String stackName, String projectName) {
    // First: Wait for running services (containers must be running first)
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

    // Second: Wait for healthy services (health checks require running containers)
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

    // Third: Wait for log patterns (application-specific readiness)
    if (waitForLogServices.isPresent() && !waitForLogServices.get().isEmpty()) {
        performWaitForLog(projectName)
    }
}

/**
 * Wait for log patterns to appear in service logs.
 *
 * @param projectName Compose project name
 */
private void performWaitForLog(String projectName) {
    def config = WaitForLogConfigBuilder.build(
        projectName,
        waitForLogServices.get(),
        waitForLogRejectPatterns.getOrNull(),
        waitForLogTimeoutSeconds.getOrElse(60),
        waitForLogPollSeconds.getOrElse(2),
        waitForLogCaseInsensitive.getOrElse(false),
        waitForLogVerbose.getOrElse(false),
        waitForLogProgressIntervalSeconds.getOrElse(0)
    )

    def waitFuture = composeService.get().waitForLogPatterns(config)
    waitFuture.get()  // Throws on timeout, reject, or crash
}
```

### 10.1 Cleanup Behavior on Failure

**Important**: When `waitForLog` (or any wait block) fails, the `ComposeUpTask` must ensure proper cleanup
to prevent lingering containers. This follows the existing pattern established by `waitForHealthy` and
`waitForRunning`.

**Cleanup Behavior**: The `ComposeUpTask.execute()` method already wraps the wait operations in a try-catch
block that calls `composeDown` on failure. Verify this pattern exists and apply it consistently:

```groovy
// In ComposeUpTask.execute() @TaskAction method (verify existing pattern):
try {
    // ... composeUp ...
    performWaitIfConfigured(stackName, projectName)
} catch (Exception e) {
    // Ensure containers are cleaned up on failure
    logger.lifecycle("Wait operation failed, cleaning up containers...")
    try {
        composeService.get().down(projectName, downConfig).get()
    } catch (Exception cleanupException) {
        logger.warn("Failed to clean up containers: ${cleanupException.message}")
    }
    throw e
}
```

**Verification Required**: Before implementation, verify that `ComposeUpTask` has this cleanup pattern.
If not present, add it to ensure no lingering containers remain after `waitForLog` failures.

```bash
# Verify existing cleanup pattern in ComposeUpTask:
rg "catch.*Exception" plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy -C 5
```

**User Impact**: When `waitForLog` fails (timeout, reject pattern, or service crash), the compose stack
is automatically torn down. This prevents test infrastructure from accumulating orphaned containers.

### 11. Plugin Wiring (GradleDockerPlugin)

Wire the `WaitForLogSpec` properties to `ComposeUpTask` inputs. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`.

**Location Finder**: Use these commands to locate the existing wiring code:

```bash
# Find where ComposeUpTask is registered
rg "ComposeUpTask" plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy

# Find existing waitForHealthy/waitForRunning wiring pattern
rg "waitForHealthy|waitForRunning" plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy -C 3
```

**Location Context**: Find the existing task registration for `ComposeUpTask` where `waitForHealthy` and
`waitForRunning` are wired. The `waitForLog` wiring follows the same pattern.

**Implementation** - add after the existing `waitForRunning` wiring block:

The wiring uses **conditional wiring** to match the existing pattern used for `waitForHealthy` and
`waitForRunning` in `GradleDockerPlugin.groovy` (lines 801-818). This ensures consistency with the
existing codebase.

```groovy
// Inside task registration for ComposeUpTask (find existing pattern for waitForHealthy/waitForRunning)
// This is typically in a method like registerComposeUpTask() or configureComposeStack()

tasks.register("composeUp${stackName.capitalize()}", ComposeUpTask) { task ->
    // ... existing wiring for composeService, stackName, files, etc. ...

    // ... existing waitForHealthy wiring ...

    // ... existing waitForRunning wiring ...

    // NEW: Wire waitForLog properties using conditional wiring (matches existing codebase pattern)
    if (stackSpec.waitForLog.present) {
        def logSpec = stackSpec.waitForLog.get()
        if (logSpec.waitForServices.present) {
            task.waitForLogServices.set(logSpec.waitForServices)
        }
        if (logSpec.rejectPatterns.present) {
            task.waitForLogRejectPatterns.set(logSpec.rejectPatterns)
        }
        task.waitForLogTimeoutSeconds.set(logSpec.timeoutSeconds.getOrElse(60))
        task.waitForLogPollSeconds.set(logSpec.pollSeconds.getOrElse(2))
        task.waitForLogCaseInsensitive.set(logSpec.caseInsensitive.getOrElse(false))
        task.waitForLogVerbose.set(logSpec.verbose.getOrElse(false))
        task.waitForLogProgressIntervalSeconds.set(logSpec.progressIntervalSeconds.getOrElse(0))
    }
}
```

**Why conditional wiring (not flatMap)?**

The existing codebase uses conditional wiring for `waitForHealthy` and `waitForRunning`:

```groovy
// Existing pattern from GradleDockerPlugin.groovy (lines 801-818)
if (stackSpec.waitForHealthy.present) {
    def waitSpec = stackSpec.waitForHealthy.get()
    if (waitSpec.waitForServices.present) {
        task.waitForHealthyServices.set(waitSpec.waitForServices)
    }
    task.waitForHealthyTimeoutSeconds.set(waitSpec.timeoutSeconds.getOrElse(60))
    task.waitForHealthyPollSeconds.set(waitSpec.pollSeconds.getOrElse(2))
}
```

Using conditional wiring for `waitForLog` maintains consistency with the existing codebase. While
`flatMap` would be more elegant, introducing a different pattern creates inconsistency.

**Future Improvement**: If desired, a separate refactoring could update all three wait blocks
(`waitForHealthy`, `waitForRunning`, `waitForLog`) to use `flatMap` for consistency.

### 12. Execution Order Change (Breaking Change)

The execution order is updated to: `waitForRunning` → `waitForHealthy` → `waitForLog`

This is a **behavior change** from the current order (`waitForHealthy` → `waitForRunning`). The
complete implementation is shown in section 10 above.

**Rationale**: Containers must be running before health checks can pass, and health checks should
pass before checking for application-specific log messages. This reflects the natural startup
sequence.

**Impact Analysis**:
- Users who configured both `waitForRunning` and `waitForHealthy` may see different timing behavior
- The change is semantically correct: you cannot check health of a non-running container
- Existing configurations will continue to work; the order change improves reliability

#### Migration Notes

This change should be documented in the release notes and CHANGELOG:

**CHANGELOG entry:**
```markdown
### Changed (Breaking)
- **Wait block execution order changed**: The execution order for wait blocks is now
  `waitForRunning` → `waitForHealthy` → `waitForLog` (previously `waitForHealthy` → `waitForRunning`).
  This change reflects the correct startup sequence where containers must be running before health
  checks can pass. Most users will not be affected, but if you relied on the previous ordering,
  review your timeout configurations.
```

**User action required**: None for most users. If you have builds that depended on the specific
timing of the previous order, you may need to adjust timeout values.

### Consolidated Summary of Files to Create/Modify

This section provides a complete list of all files affected by this implementation, organized by implementation phase.

#### Phase 0: Prerequisite Changes (Must Complete First)

| File | Action | Section | Purpose |
|------|--------|---------|---------|
| `model/LogsConfig.groovy` | Modify | Pre-Impl | Remove `Math.max(1, tailLines)` constraint (line 32). Optionally add `hasLimitedTail()` helper. |
| `service/ExecLibraryComposeService.groovy` | Optional | Pre-Impl | Replace `tailLines > 0` with `hasLimitedTail()` for clarity (existing code already works) |

#### Phase 1: Core Components (Create New Files)

| File | Action | Section | Purpose |
|------|--------|---------|---------|
| `spec/WaitForLogSpec.groovy` | Create | §1 | DSL configuration class with Property API |
| `model/WaitForLogConfig.groovy` | Create | §2 | Immutable runtime config with compiled patterns |
| `model/WaitForLogResult.groovy` | Create | §3 | Per-service pattern match results |
| `util/LogPatternMatcher.groovy` | Create | §4 | Pure utility class for pattern matching |
| `util/WaitForLogConfigBuilder.groovy` | Create | §5 | Config builder with validation |

#### Phase 2: Integration (Modify Existing Files)

| File | Action | Section | Purpose |
|------|--------|---------|---------|
| `spec/ComposeStackSpec.groovy` | Modify | §6 | Add `waitForLog` property and DSL methods |
| `service/ComposeService.groovy` | Modify | §7 | Add `waitForLogPatterns()` interface method |
| `service/ExecLibraryComposeService.groovy` | Modify | §8 | Implement `waitForLogPatterns()`, add helpers |
| `exception/ComposeServiceException.groovy` | Modify | §9 | Add new error types to enum |
| `task/ComposeUpTask.groovy` | Modify | §10 | Add flattened properties, update execution order |
| `GradleDockerPlugin.groovy` | Modify | §11 | Wire spec properties to task inputs |
| `extension/TestIntegrationExtension.groovy` | Modify | §13 | Propagate waitForLog to test framework |

#### Phase 3: Documentation Updates

| File | Action | Section | Purpose |
|------|--------|---------|---------|
| `docs/usage/usage-docker-orch.md` | Modify | §14 | Add waitForLog DSL documentation |
| `CHANGELOG.md` | Modify | §14 | Document new feature and breaking change |
| `README.md` | Modify | §14 | Update feature list |

#### Existing Files Used (No Changes Required)

| File | Status | Notes |
|------|--------|-------|
| `service/ComposeService.captureLogs()` | ✅ Exists | Used as-is for log capture |
| `model/LogsConfig.groovy` (after Phase 0) | ✅ Modified | Used with new `hasLimitedTail()` method |

#### Verification Checklist Before Implementation

Before starting implementation, verify (status based on codebase review):

- [x] **Guava dependency**: Defined in `libs.versions.toml` but NOT in `build.gradle`. Available as transitive
      dependency from docker-java. **Action**: Add `implementation libs.guava` for stability, or remove
      `@VisibleForTesting` annotations.
- [x] **ServiceLogger interface**: ⚠️ Does NOT have `lifecycle()` method. Only has `info()`, `debug()`, `warn()`,
      `error()` - all with single String parameter. **Action**: Use `info()` with Groovy string interpolation.
- [x] **ComposeStackSpec.getName()**: ✅ Exists (line 46-48)
- [x] **TestIntegrationExtension.setComprehensiveSystemProperties()**: ✅ Exists (line 179-217)
- [x] **Existing wiring patterns**: ✅ `waitForHealthy` and `waitForRunning` patterns exist in `GradleDockerPlugin`
- [ ] **Test.systemProperty Provider support**: Verify whether Gradle 9's `Test.systemProperty()` accepts `Provider<String>`
      values. This affects the implementation approach in Section 13 (TestIntegrationExtension).

      **Verification step**:
      ```bash
      # Check Gradle 9 API documentation or test locally with a minimal example:
      # test.systemProperty("key", providers.provider { "value" })
      # If this fails, use eager evaluation with .get() instead
      ```

      **Action**: If Provider is not accepted, use the eager evaluation fallback shown in Section 13.

### 13. Test Framework Extension Integration

**⚠️ CRITICAL: Lifecycle Support Limitation**

| Lifecycle | `waitForLog` Support | Notes |
|-----------|---------------------|-------|
| `CLASS` | ✅ **Fully Supported** | Executed by `ComposeUpTask` before tests run |
| `METHOD` | ❌ **Not Supported (Phase 2)** | Test framework extensions (Spock/JUnit 5) must be updated separately |

For `METHOD` lifecycle, the `waitForLog` block configuration is propagated via system properties (as
described below), but the test framework extensions (`@ComposeUp` annotation, `DockerComposeMethodExtension`)
**do not yet consume these properties**. This will be implemented in a separate phase.

**Initial Release Scope**: `waitForLog` only works with `Lifecycle.CLASS` in the initial implementation.

**User-Facing Documentation Requirement**: The following must be documented in `docs/usage/usage-docker-orch.md`:

```markdown
### Lifecycle Support

The `waitForLog` block is supported with `Lifecycle.CLASS` only in the current release.

| Wait Block | `Lifecycle.CLASS` | `Lifecycle.METHOD` |
|------------|-------------------|-------------------|
| `waitForRunning` | ✅ Supported | ✅ Supported |
| `waitForHealthy` | ✅ Supported | ✅ Supported |
| `waitForLog` | ✅ Supported | ❌ Not yet supported |

If you need log-based readiness with per-method compose lifecycle, either:
1. Use `Lifecycle.CLASS` instead (compose stack shared across all test methods)
2. Wait for a future release that adds `Lifecycle.METHOD` support for `waitForLog`
```

**Why this limitation exists**: The test framework extensions (`DockerComposeMethodExtension`) run outside
of Gradle's task execution context and need to independently call Docker Compose. While we can pass
configuration via system properties, the extensions need to be updated to parse and execute
`waitForLogPatterns()`. This is out of scope for the initial implementation.

---

When using `usesCompose()` with test framework extensions, `waitForLog` configuration must be
propagated so it's executed as part of compose stack startup. Modify `TestIntegrationExtension`
to pass `waitForLog` configuration to the test lifecycle.

**Modify `TestIntegrationExtension.groovy`** (`plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtension.groovy`):

**Verification Required Before Implementation:**

1. Verify that `TestIntegrationExtension` has a method named `setComprehensiveSystemProperties()` (or similar)
2. Verify the method signature - the example below assumes parameters `(Test test, String stackName, stackSpec, Lifecycle lifecycle)`
3. Verify how existing `waitForHealthy` and `waitForRunning` blocks are handled in this method
4. Adjust the implementation below to match the actual method signature and patterns used

The existing `setComprehensiveSystemProperties()` method already sets system properties for
`waitForHealthy` and `waitForRunning`. Add `waitForLog` in the same pattern.

**Required Imports** (add to existing imports in `TestIntegrationExtension.groovy`):

```groovy
// JSON serialization for MapProperty values (part of Groovy runtime, no additional dependencies)
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper  // If reading JSON in this file (may not be needed here)
```

**Update `setComprehensiveSystemProperties` Method** (add after existing waitForRunning block):

```groovy
/**
 * Set comprehensive system properties from ComposeStackSpec for test framework extensions to consume
 * @param test Test task to configure
 * @param stackName Name of the compose stack
 * @param stackSpec ComposeStackSpec containing all configuration
 * @param lifecycle Lifecycle mode (Lifecycle.CLASS or Lifecycle.METHOD)
 */
private void setComprehensiveSystemProperties(Test test, String stackName, stackSpec, Lifecycle lifecycle) {
    // ... existing basic configuration properties ...

    // ... existing waitForHealthy block ...

    // ... existing waitForRunning block ...

    // NEW: Wait for log settings - use provider-based approach for configuration cache compatibility
    def waitForLogSpec = stackSpec.waitForLog.getOrNull()
    if (waitForLogSpec) {
        test.systemProperty("docker.compose.waitForLog.enabled", "true")

        // Use provider.map() for lazy evaluation - configuration cache compatible
        test.systemProperty("docker.compose.waitForLog.services",
            waitForLogSpec.waitForServices.map { map -> new JsonBuilder(map).toString() })
        test.systemProperty("docker.compose.waitForLog.timeoutSeconds",
            waitForLogSpec.timeoutSeconds.map { it.toString() })
        test.systemProperty("docker.compose.waitForLog.pollSeconds",
            waitForLogSpec.pollSeconds.map { it.toString() })
        test.systemProperty("docker.compose.waitForLog.caseInsensitive",
            waitForLogSpec.caseInsensitive.map { it.toString() })
        test.systemProperty("docker.compose.waitForLog.verbose",
            waitForLogSpec.verbose.map { it.toString() })
        test.systemProperty("docker.compose.waitForLog.progressIntervalSeconds",
            waitForLogSpec.progressIntervalSeconds.map { it.toString() })

        // Only set reject patterns if configured (use provider for lazy evaluation)
        test.systemProperty("docker.compose.waitForLog.rejectPatterns",
            waitForLogSpec.rejectPatterns.map { map ->
                map.isEmpty() ? "{}" : new JsonBuilder(map).toString()
            })
    }

    // ... existing state file path ...
}
```

**Configuration Cache Compatibility Note**:

The implementation uses `provider.map()` to maintain lazy evaluation, which is fully configuration
cache compatible. The map transformation only occurs when the provider value is actually needed,
not during configuration.

**Alternative (if provider.map() causes issues with Test.systemProperty)**:

Some versions of Gradle's Test task may not accept Provider for systemProperty. In that case,
use the eager approach with explicit `.get()`:

```groovy
// Fallback: Eager evaluation (less ideal but works if provider not accepted)
if (waitForLogSpec) {
    test.systemProperty("docker.compose.waitForLog.enabled", "true")
    test.systemProperty("docker.compose.waitForLog.services",
        serializeMapProperty(waitForLogSpec.waitForServices))
    // ... etc
}

/**
 * Serialize a MapProperty to JSON string.
 */
private String serializeMapProperty(org.gradle.api.provider.MapProperty<String, List<String>> mapProperty) {
    if (!mapProperty.present || mapProperty.get().isEmpty()) {
        return "{}"
    }
    return new JsonBuilder(mapProperty.get()).toString()
}
```

**Recommendation**: Start with the provider-based approach. Test with `--configuration-cache` twice
to verify reuse. Fall back to eager evaluation only if necessary.

**Test Framework Extension Changes (Out of Scope)**:

The test framework extensions (Spock `@ComposeUp` / JUnit 5 `DockerComposeClassExtension`) must be
updated in a **separate implementation phase** to:

1. Read the new system properties at test setup time:
   ```groovy
   def waitForLogEnabled = System.getProperty("docker.compose.waitForLog.enabled") == "true"
   def servicesJson = System.getProperty("docker.compose.waitForLog.services")
   ```

2. Deserialize JSON back to map:
   ```groovy
   def services = new JsonSlurper().parseText(servicesJson) as Map<String, List<String>>
   ```

3. Invoke `waitForLogPatterns()` after `composeUp` but before running tests

4. Handle failures by failing the test class/method setup with clear error messages

This separation allows the Gradle plugin changes to be completed and tested independently of
the test framework extension changes.

**User Configuration Example**:

```groovy
dockerTest {
    composeStacks {
        myTest {
            files.from('compose.yml')
            waitForLog {
                waitForServices.set(['app': ['Ready to serve']])
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "myTest", lifecycle: "class")
}
```

When configured this way, the `waitForLog` block is automatically executed:
- **CLASS lifecycle**: As part of `composeUp` task before tests run
- **METHOD lifecycle**: By the test framework extension before each test method

### Configuration Cache Verification

After implementation, verify configuration cache compatibility:

```bash
# First run - stores configuration
cd plugin-integration-test && ./gradlew composeUpMyTest --configuration-cache

# Second run - should reuse cached configuration
./gradlew composeUpMyTest --configuration-cache

# Should see: "Reusing configuration cache."
```

### MapProperty Serialization Fallback

If `MapProperty<String, List<String>>` causes serialization issues with the configuration cache,
use a JSON string representation as a fallback approach.

**⚠️ Implementation Decision: Primary vs Fallback Approach**

There are two implementation options:

| Approach | Pros | Cons |
|----------|------|------|
| **Primary: MapProperty** | Type-safe, IDE support, Gradle-native | May have serialization issues |
| **Fallback: JSON String** | Guaranteed serializable, simpler | Less type safety, manual (de)serialization |

**Recommendation**: Start with `MapProperty` during development, but test configuration cache
**early and often**. If any serialization issues appear during the first integration test with
`--configuration-cache`, switch to JSON serialization immediately rather than trying to debug
complex serialization issues.

**When to Use This Fallback:**
- If configuration cache tests fail with serialization errors for `MapProperty`
- Test early by running: `./gradlew composeUpMyTest --configuration-cache` twice
- **Consider using JSON from the start** if you want to avoid potential mid-development refactoring

**Fallback Implementation:**

1. **In ComposeUpTask**, replace the `MapProperty` with a `Property<String>` containing JSON:

```groovy
import groovy.json.JsonSlurper

// Instead of:
// @Input @Optional abstract MapProperty<String, List<String>> getWaitForLogServices()

// Use:
@Input
@Optional
abstract Property<String> getWaitForLogServicesJson()

@Input
@Optional
abstract Property<String> getWaitForLogRejectPatternsJson()
```

2. **In GradleDockerPlugin**, serialize the map when wiring (using Groovy JsonBuilder):

```groovy
import groovy.json.JsonBuilder

// Serialize map to JSON when configuring task
if (stackSpec.waitForLog.isPresent()) {
    def logSpec = stackSpec.waitForLog.get()

    task.waitForLogServicesJson.set(
        providers.provider { new JsonBuilder(logSpec.waitForServices.get()).toString() }
    )
    if (logSpec.rejectPatterns.present && !logSpec.rejectPatterns.get().isEmpty()) {
        task.waitForLogRejectPatternsJson.set(
            providers.provider { new JsonBuilder(logSpec.rejectPatterns.get()).toString() }
        )
    }
    // ... other properties unchanged
}
```

3. **In performWaitForLog()**, deserialize the JSON back to a map (using Groovy JsonSlurper):

```groovy
import groovy.json.JsonSlurper

private void performWaitForLog(String projectName) {
    def jsonSlurper = new JsonSlurper()

    Map<String, List<String>> services = waitForLogServicesJson.present
        ? jsonSlurper.parseText(waitForLogServicesJson.get()) as Map<String, List<String>>
        : null

    Map<String, List<String>> rejectPatterns = waitForLogRejectPatternsJson.present
        ? jsonSlurper.parseText(waitForLogRejectPatternsJson.get()) as Map<String, List<String>>
        : null

    def config = WaitForLogConfigBuilder.build(
        projectName,
        services,
        rejectPatterns,
        waitForLogTimeoutSeconds.getOrElse(60),
        waitForLogPollSeconds.getOrElse(2),
        waitForLogCaseInsensitive.getOrElse(false),
        waitForLogVerbose.getOrElse(false),
        waitForLogProgressIntervalSeconds.getOrElse(0)
    )

    def waitFuture = composeService.get().waitForLogPatterns(config)
    waitFuture.get()
}
```

This fallback ensures configuration cache compatibility by using only primitive/String types
that are guaranteed to serialize correctly. The Groovy `JsonBuilder` and `JsonSlurper` classes
are part of the Groovy runtime and don't require additional dependencies.

**Recommendation:** Start with `MapProperty` (primary implementation). Only switch to JSON
fallback if configuration cache tests reveal serialization issues.

### 14. Usage Documentation Updates

After implementation, update the following documentation files:

| File | Updates Required |
|------|------------------|
| `docs/usage/usage-docker-orch.md` | Add `waitForLog` section with examples, configuration options, and common patterns |
| `docs/usage/usage-docker.md` | No changes (docker image operations, not compose) |
| `CHANGELOG.md` | Add entry for new feature and breaking execution order change |
| `README.md` | Update feature list to mention log-based readiness checks |

**Example content for `usage-docker-orch.md`**:

```markdown
### Log-Based Readiness Checks

The `waitForLog` block waits for specific patterns to appear in container logs before
considering a service ready. This is useful for services without health checks or that
require application-specific startup indicators.

#### Basic Usage

```groovy
dockerTest {
    composeStacks {
        myTest {
            files.from('compose.yml')

            waitForLog {
                waitForServices.set([
                    'app': ['Started Application'],
                    'db': ['ready to accept connections']
                ])
                timeoutSeconds.set(120)
            }
        }
    }
}
```

[Include additional examples from the DSL section above]
```

### 15. Performance Considerations

#### Large Log Output

When containers produce large log output, fetching all logs on every poll iteration can be
expensive in terms of memory and I/O. The current implementation fetches all logs on each
poll to ensure patterns that appeared early in startup are not missed.

**Known Limitations:**
- Each poll iteration fetches the complete log history for monitored services
- For containers with very high log volume (thousands of lines per second), this can cause
  performance degradation
- No incremental log fetching (Docker Compose `logs` command doesn't support `--since` with
  sub-second precision needed for polling)

**Mitigation Strategies:**
1. **Tune poll interval**: Use longer `pollSeconds` values (e.g., 5-10 seconds) for services
   with high log volume
2. **Use early patterns**: Choose patterns that appear early in startup to minimize wait time
3. **Consider health checks**: For services with high log volume, prefer `waitForHealthy` when
   possible

**⚠️ Poll Interval Warning:**

Setting `pollSeconds` too low (e.g., < 2 seconds) can cause:
- Excessive I/O load from frequent log fetching
- Higher CPU usage from repeated pattern matching
- Potential rate limiting issues with Docker daemon

**Recommended minimum**: `pollSeconds.set(2)` (the default). For services with high log volume
or many patterns, consider `pollSeconds.set(5)` or higher.

**Future Improvements** (out of scope for initial implementation):
- Track last-seen log position and only check new lines
- Support Docker API directly for more efficient log streaming
- Add option to limit log history depth per poll

#### Pattern Matching Performance

The implementation compiles regex patterns once during configuration building and reuses them
for all polls. Pattern matching is performed line-by-line, which is efficient for typical
log output.

**Best Practices:**
- Use simple patterns when possible (literal strings match faster than complex regex)
- Avoid overly broad patterns like `.*` at the start of patterns
- Use case-insensitive mode (`caseInsensitive.set(true)`) instead of `(?i)` in every pattern
  for better performance with multiple patterns

### 16. Implementation Checklist

This checklist provides a step-by-step guide for implementing the `waitForLog` feature.

#### Pre-Implementation Verification

- [ ] Read and understand the existing `waitForHealthy` and `waitForRunning` implementations
- [ ] Verify Guava dependency in `plugin/build.gradle`:
  ```bash
  rg "libs.guava" plugin/build.gradle
  ```
  If not found, add `implementation libs.guava` to dependencies block
- [ ] Confirm `ServiceLogger` interface methods (**verified**: `info()`, `debug()`, `warn()`, `error()` only - NO `lifecycle()`)
- [ ] Confirm `ComposeStackSpec.getName()` exists (**verified**: line 46-48)
- [ ] Confirm `TestIntegrationExtension.setComprehensiveSystemProperties()` signature (**verified**: line 179-217)
- [ ] Review existing wiring patterns in `GradleDockerPlugin`:
  ```bash
  rg "waitForHealthy|waitForRunning" plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy -C 3
  ```

#### Phase 0: Prerequisite Changes

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

#### Phase 1: Core Components

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

#### Phase 2: Integration

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

#### Phase 3: Functional Tests

- [ ] Add functional tests for `waitForLog` DSL configuration
- [ ] Add functional tests for validation error messages
- [ ] Add functional tests for property wiring
- [ ] Verify all functional tests pass

#### Phase 4: Configuration Cache Verification

- [ ] Run with `--configuration-cache` flag
- [ ] Verify second run reuses cached configuration
- [ ] If MapProperty serialization fails, implement JSON fallback

#### Phase 5: Integration Tests

- [ ] Create integration test scenario for `waitForLog`
- [ ] Test with real Docker containers
- [ ] Test timeout behavior
- [ ] Test reject pattern behavior
- [ ] Test verbose logging
- [ ] Test progress interval logging
- [ ] Verify no lingering containers

#### Phase 6: Documentation

- [ ] Update `docs/usage/usage-docker-orch.md`
- [ ] Update `CHANGELOG.md` with new feature and breaking change
- [ ] Update `README.md` feature list

#### Final Verification

- [ ] All unit tests pass (100% coverage where possible)
- [ ] All functional tests pass
- [ ] All integration tests pass
- [ ] Configuration cache works correctly
- [ ] `docker ps -a` shows no lingering containers
- [ ] Documentation is complete and accurate

### Unit Test Strategy Notes

While tests are deferred to a separate phase, the following notes inform test design:

#### Highly Testable Components (Pure Functions)

| Component | Testability | Notes |
|-----------|-------------|-------|
| `LogPatternMatcher` | 100% | Pure static methods, no dependencies. Test all branches. |
| `WaitForLogConfig` | 100% | Immutable data class. Test construction and getters. |
| `WaitForLogResult` | 100% | Immutable data class. Test both constructors. |
| `WaitForLogConfigBuilder` | 100% | Pure validation logic. Test all error paths. |

#### Components Requiring Mocks

| Component | Dependencies to Mock | Notes |
|-----------|---------------------|-------|
| `ExecLibraryComposeService.waitForLogPatterns()` | `TimeService`, `ProcessExecutor`, `ServiceLogger` | Mock time for deterministic timeout testing |
| `ComposeUpTask.performWaitForLog()` | `ComposeService` | Mock service to test task orchestration |
| `ComposeStackSpec.waitForLog()` | None (uses `ObjectFactory`) | Use `ProjectBuilder` for testing |

#### Testing Verbose and Progress Logging

To verify that verbose logging and progress logging produce correct output, use a mock `ServiceLogger`:

```groovy
// Example test setup for verifying logging output
def mockServiceLogger = Mock(ServiceLogger)
def service = new ExecLibraryComposeService(
    mockProcessExecutor, mockTimeService, mockServiceLogger
)

// Test verbose logging
when:
service.waitForLogPatterns(configWithVerboseTrue).get()

then:
// Verify specific log messages were produced
1 * mockServiceLogger.info({ it.contains("[waitForLog] Waiting for log patterns") })
_ * mockServiceLogger.info({ it.contains("[waitForLog] Polling for log patterns") })
_ * mockServiceLogger.info({ it.contains("patterns matched") })

// Test progress interval logging
when:
service.waitForLogPatterns(configWithProgressInterval).get()

then:
// Verify progress summary was logged at expected interval
_ * mockServiceLogger.info({ it.contains("[waitForLog] Progress at") })
```

**Key Test Cases for Logging:**

1. **Verbose mode enabled**: Verify per-poll progress messages are logged
2. **Verbose mode disabled**: Verify only start/end messages are logged
3. **Progress interval**: Verify summary is logged at configured intervals
4. **Pattern found messages**: Verify `[FOUND]` messages include pattern and timestamp
5. **Service ready messages**: Verify `READY` status is logged when all patterns match

#### Test Coverage Priorities

1. **LogPatternMatcher**: Achieve 100% branch coverage on all pattern matching logic
2. **WaitForLogConfigBuilder**: Test all validation error messages
3. **Timeout/reject scenarios**: Test edge cases in `waitForLogPatterns()`
4. **Execution order**: Verify `waitForRunning` → `waitForHealthy` → `waitForLog` order
5. **Verbose/progress logging**: Verify correct log output for all logging modes
