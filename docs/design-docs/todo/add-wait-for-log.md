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
- `rejectPatterns`: Convention of empty map
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

## Implementation

This section describes the implementation plan for the `waitForLog` feature, following the existing patterns
established in the codebase and ensuring Gradle 9/10 configuration cache compatibility.

### Component Overview

The implementation adds the following components:

| Component | Type | Purpose |
|-----------|------|---------|
| `WaitForLogSpec` | Spec class | DSL configuration for log-based readiness |
| `WaitForLogConfig` | Model class | Immutable runtime configuration |
| `LogsConfig` | Model class | Log capture configuration |
| `LogPatternMatcher` | Pure utility | Regex pattern matching logic (includes `RejectCheckResult` inner class) |
| `WaitForLogResult` | Model class | Per-service pattern match results |
| `WaitForLogConfigBuilder` | Utility | Config builder with validation |
| `ComposeService` extension | Interface | Add `waitForLogPatterns()` and `captureLogs()` methods |
| `ComposeUpTask` extension | Task | Add `waitForLog*` properties |
| `ComposeStackSpec` extension | Spec | Add `waitForLog` DSL block |
| `TestIntegrationExtension` extension | Extension | Propagate `waitForLog` to test framework |

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
        // rejectPatterns has no convention - empty map by default via MapProperty
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

```groovy
package com.kineticfire.gradle.docker.model

import java.time.Duration
import java.util.regex.Pattern

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

```groovy
package com.kineticfire.gradle.docker.util

import com.google.common.annotations.VisibleForTesting

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

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
     * @param patterns Patterns to match
     * @param matchedPatterns Set of already-matched pattern indices (modified in place)
     * @param logLines New log lines to check
     * @param elapsedSeconds Current elapsed time for recording match time
     * @param matchTimes Map of pattern index to match time (modified in place)
     * @return Number of newly matched patterns
     */
    static int updateMatches(
            List<Pattern> patterns,
            Set<Integer> matchedPatterns,
            List<String> logLines,
            long elapsedSeconds,
            Map<Integer, Long> matchTimes) {
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

```groovy
// Add import
import com.kineticfire.gradle.docker.spec.WaitForLogSpec

// Ensure ObjectFactory is available (if not already present):
// ComposeStackSpec should have an injected ObjectFactory for creating nested specs.
// If not present, add:
@Inject
abstract ObjectFactory getObjectFactory()

// Add property
abstract Property<WaitForLogSpec> getWaitForLog()

// Add DSL methods
void waitForLog(@DelegatesTo(WaitForLogSpec) Closure closure) {
    def waitForLogSpec = objectFactory.newInstance(WaitForLogSpec)
    closure.delegate = waitForLogSpec
    closure.call()
    validateWaitForLogSpec(waitForLogSpec)
    waitForLog.set(waitForLogSpec)
}

void waitForLog(Action<WaitForLogSpec> action) {
    def waitForLogSpec = objectFactory.newInstance(WaitForLogSpec)
    action.execute(waitForLogSpec)
    validateWaitForLogSpec(waitForLogSpec)
    waitForLog.set(waitForLogSpec)
}

/**
 * Validates that a WaitForLogSpec has valid configuration.
 *
 * <p>This validation occurs during DSL configuration (not provider resolution)
 * to provide early feedback on configuration errors.</p>
 *
 * @param spec The WaitForLogSpec to validate
 * @throws GradleException if validation fails
 */
private void validateWaitForLogSpec(WaitForLogSpec spec) {
    // Check waitForServices is present and non-empty
    if (!spec.waitForServices.present || spec.waitForServices.get().isEmpty()) {
        throw new GradleException(
            "Configuration error in 'waitForLog' block for compose stack '${name}': " +
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
                "Configuration error in 'waitForLog' block for compose stack '${name}': " +
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

### 7.5 LogsConfig Model Class and captureLogs Method

The `waitForLogPatterns()` implementation requires the ability to fetch container logs. This
section describes the `LogsConfig` model class and `captureLogs()` method that must exist in
`ComposeService`.

**LogsConfig Model Class**

If not already present, create at `plugin/src/main/groovy/com/kineticfire/gradle/docker/model/LogsConfig.groovy`:

```groovy
package com.kineticfire.gradle.docker.model

/**
 * Configuration for fetching container logs.
 */
class LogsConfig {
    final List<String> services
    final int tailLines      // 0 = all lines
    final boolean timestamps
    final String since       // null = from beginning

    LogsConfig(List<String> services, int tailLines, boolean timestamps, String since) {
        this.services = Collections.unmodifiableList(services ?: [])
        this.tailLines = tailLines
        this.timestamps = timestamps
        this.since = since
    }
}
```

**ComposeService Interface Extension**

Add `captureLogs()` method to `ComposeService` interface if not already present:

```groovy
// Add to ComposeService interface
import com.kineticfire.gradle.docker.model.LogsConfig

/**
 * Capture logs from one or more services.
 *
 * @param projectName Compose project name
 * @param config Log capture configuration
 * @return CompletableFuture with log output as string
 */
CompletableFuture<String> captureLogs(String projectName, LogsConfig config)
```

**ExecLibraryComposeService Implementation**

Implement in `ExecLibraryComposeService`:

```groovy
@Override
CompletableFuture<String> captureLogs(String projectName, LogsConfig config) {
    return CompletableFuture.supplyAsync({
        def composeCommand = getComposeCommand()
        def command = composeCommand + ["-p", projectName, "logs", "--no-color"]

        if (config.tailLines > 0) {
            command += ["--tail", config.tailLines.toString()]
        }
        if (config.timestamps) {
            command += ["--timestamps"]
        }
        if (config.since) {
            command += ["--since", config.since]
        }
        command += config.services

        def result = processExecutor.execute(command)
        if (!result.isSuccess()) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.COMMAND_FAILED,
                "Failed to capture logs: ${result.stderr}"
            )
        }
        return result.stdout ?: ""
    })
}

/**
 * Get recent log lines for a specific service (for error reporting).
 *
 * @param projectName Compose project name
 * @param serviceName Service name
 * @param lineCount Number of recent lines to retrieve
 * @return List of recent log lines
 */
private List<String> getRecentLogs(String projectName, String serviceName, int lineCount) {
    try {
        def config = new LogsConfig([serviceName], lineCount, false, null)
        def logs = captureLogs(projectName, config).get()
        return logs.split('\n').toList()
    } catch (Exception e) {
        serviceLogger.debug("Failed to get recent logs for '{}': {}", serviceName, e.message)
        return ["(Unable to retrieve logs: ${e.message})"]
    }
}
```

### 8. ExecLibraryComposeService Implementation

Implement `waitForLogPatterns()` in `ExecLibraryComposeService`. The implementation follows the
existing patterns for `waitForServices()`.

**Note:** This implementation assumes the following dependencies already exist in
`ExecLibraryComposeService`:
- `timeService`: A `TimeService` interface for time operations (`currentTimeMillis()`, `sleep()`)
- `processExecutor`: A `ProcessExecutor` for running shell commands
- `getComposeCommand()`: Method returning the docker compose command prefix (e.g., `["docker", "compose"]`)
- `serviceLogger`: Logger instance for output

Verify these exist or add them to the class dependencies before implementing:

```groovy
@Override
CompletableFuture<Map<String, WaitForLogResult>> waitForLogPatterns(WaitForLogConfig config) {
    if (config == null) {
        throw new NullPointerException("Wait-for-log config cannot be null")
    }
    return CompletableFuture.supplyAsync({
        try {
            def serviceCount = config.services.size()
            serviceLogger.lifecycle("[waitForLog] Waiting for log patterns in {} service{} (timeout: {}s)...",
                serviceCount, serviceCount == 1 ? "" : "s", config.timeout.toSeconds())

            def startTime = timeService.currentTimeMillis()
            def timeoutMillis = config.timeout.toMillis()

            // Track match state per service
            // Map<serviceName, Set<patternIndex>>
            def matchedPatterns = config.services.collectEntries { [(it): new HashSet<Integer>()] }
            // Map<serviceName, Map<patternIndex, matchTimeSeconds>>
            def matchTimes = config.services.collectEntries { [(it): [:]] }

            def lastProgressLog = startTime
            def progressIntervalMillis = config.progressInterval.toMillis()
            int attempt = 0

            while (timeService.currentTimeMillis() - startTime < timeoutMillis) {
                attempt++
                def elapsedSeconds = (timeService.currentTimeMillis() - startTime) / 1000

                if (config.verbose) {
                    serviceLogger.lifecycle("[waitForLog] Polling for log patterns (attempt {}/{}, elapsed: {}s)...",
                        attempt, config.totalWaitAttempts, elapsedSeconds.intValue())
                }

                def allReady = true

                for (String serviceName : config.services) {
                    def patterns = config.servicePatterns[serviceName]
                    def serviceMatchedPatterns = matchedPatterns[serviceName]
                    def serviceMatchTimes = matchTimes[serviceName]

                    // Skip if already fully matched
                    if (serviceMatchedPatterns.size() == patterns.size()) {
                        if (config.verbose) {
                            serviceLogger.lifecycle("[waitForLog] Service '{}': {}/{} patterns matched - READY",
                                serviceName, patterns.size(), patterns.size())
                        }
                        continue
                    }

                    // Check if container is still running
                    if (!isServiceRunning(config.projectName, serviceName)) {
                        def results = buildPartialResults(config, matchedPatterns, matchTimes, serviceName)
                        throw new ComposeServiceException(
                            ComposeServiceException.ErrorType.SERVICE_CRASHED,
                            buildCrashErrorMessage(serviceName, results, config),
                            "Check container logs with: docker logs <container-id>"
                        )
                    }

                    // Fetch logs for this service
                    def logsConfig = new LogsConfig([serviceName], 0, false, null)  // 0 = all logs
                    def logs = captureLogs(config.projectName, logsConfig).get()
                    def logLines = logs.split('\n').toList()

                    // Check reject patterns first
                    def rejectCheck = checkRejectPatterns(config, serviceName, logLines)
                    if (rejectCheck != null) {
                        def results = buildPartialResults(config, matchedPatterns, matchTimes, serviceName)
                        throw new ComposeServiceException(
                            ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED,
                            buildRejectErrorMessage(serviceName, rejectCheck, results, config),
                            "A reject pattern indicates the service encountered an error during startup."
                        )
                    }

                    // Update pattern matches
                    def newMatches = LogPatternMatcher.updateMatches(
                        patterns, serviceMatchedPatterns, logLines, elapsedSeconds.longValue(), serviceMatchTimes
                    )

                    if (config.verbose) {
                        logServiceProgress(serviceName, patterns, serviceMatchedPatterns, serviceMatchTimes)
                    }

                    if (serviceMatchedPatterns.size() < patterns.size()) {
                        allReady = false
                    }
                }

                // Check for periodic progress logging
                if (progressIntervalMillis > 0 && !config.verbose) {
                    def now = timeService.currentTimeMillis()
                    if (now - lastProgressLog >= progressIntervalMillis) {
                        logProgressSummary(config, matchedPatterns, elapsedSeconds.intValue())
                        lastProgressLog = now
                    }
                }

                if (allReady) {
                    def elapsedTotal = (timeService.currentTimeMillis() - startTime) / 1000
                    serviceLogger.lifecycle("[waitForLog] All services ready after {} seconds", elapsedTotal.intValue())
                    return buildResults(config, matchedPatterns, matchTimes)
                }

                timeService.sleep(config.pollInterval.toMillis())
            }

            // Timeout - build detailed error message
            def results = buildResults(config, matchedPatterns, matchTimes)
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                buildTimeoutErrorMessage(config, results),
                "Use verbose.set(true) to see detailed progress during polling."
            )

        } catch (ComposeServiceException e) {
            throw e
        } catch (Exception e) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                "Error waiting for log patterns: ${e.message}",
                e
            )
        }
    })
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

private boolean isServiceRunning(String projectName, String serviceName) {
    try {
        def composeCommand = getComposeCommand()
        def command = composeCommand + ["-p", projectName, "ps", serviceName, "--format", "table"]
        def result = processExecutor.execute(command)
        if (result.isSuccess() && result.stdout) {
            def output = result.stdout.toLowerCase()
            return output.contains("up") || output.contains("running")
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

private Map<String, WaitForLogResult> buildPartialResults(
        WaitForLogConfig config,
        Map<String, Set<Integer>> matchedPatterns,
        Map<String, Map<Integer, Long>> matchTimes,
        String failedService) {
    // Build results but mark failed service appropriately
    return buildResults(config, matchedPatterns, matchTimes)
}

private void logServiceProgress(String serviceName, List<Pattern> patterns,
                                 Set<Integer> matchedPatterns, Map<Integer, Long> matchTimes) {
    def matchedCount = matchedPatterns.size()
    def totalPatterns = patterns.size()

    if (matchedCount == totalPatterns) {
        serviceLogger.lifecycle("[waitForLog] Service '{}': {}/{} patterns matched - READY",
            serviceName, matchedCount, totalPatterns)
    } else {
        serviceLogger.lifecycle("[waitForLog] Service '{}': {}/{} patterns matched",
            serviceName, matchedCount, totalPatterns)
        patterns.eachWithIndex { pattern, index ->
            if (matchedPatterns.contains(index)) {
                serviceLogger.lifecycle("  [FOUND] '{}' - matched at {}s",
                    pattern.pattern(), matchTimes[index])
            }
        }
    }
}

private void logProgressSummary(WaitForLogConfig config, Map<String, Set<Integer>> matchedPatterns, int elapsedSeconds) {
    def summary = config.services.collect { serviceName ->
        def matched = matchedPatterns[serviceName].size()
        def total = config.servicePatterns[serviceName].size()
        "${serviceName} ${matched}/${total}"
    }.join(", ")
    serviceLogger.lifecycle("[waitForLog] Progress at {}s: {}", elapsedSeconds, summary)
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
            sb.append("\n  Last 10 log lines from '${serviceName}':\n")
            def recentLogs = getRecentLogs(config.projectName, serviceName, 10)
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
    sb.append("\n  Last 10 log lines from '${serviceName}':\n")
    def recentLogs = getRecentLogs(config.projectName, serviceName, 10)
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
    sb.append("\n  Last 10 log lines from '${serviceName}':\n")
    def recentLogs = getRecentLogs(config.projectName, serviceName, 10)
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

```groovy
enum ErrorType {
    // ... existing types ...
    LOG_PATTERN_TIMEOUT,
    LOG_REJECT_PATTERN_MATCHED,
    SERVICE_CRASHED
}
```

### 10. ComposeUpTask Extension

Add `waitForLog*` properties and execution to `ComposeUpTask`. Modify
`plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy`:

```groovy
// Add imports
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.util.WaitForLogConfigBuilder

// Add flattened input properties for waitForLog
// (Flattened per Part 2 of compatibility guide to avoid @Nested serialization issues)

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

// Update performWaitIfConfigured to include waitForLog
private void performWaitIfConfigured(String stackName, String projectName) {
    // First: Wait for running services (natural startup sequence)
    if (waitForRunningServices.isPresent() && !waitForRunningServices.get().isEmpty()) {
        // ... existing implementation ...
    }

    // Second: Wait for healthy services
    if (waitForHealthyServices.isPresent() && !waitForHealthyServices.get().isEmpty()) {
        // ... existing implementation ...
    }

    // Third: Wait for log patterns (application-specific readiness)
    if (waitForLogServices.isPresent() && !waitForLogServices.get().isEmpty()) {
        performWaitForLog(projectName)
    }
}

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

### 11. Plugin Wiring (GradleDockerPlugin)

Wire the `WaitForLogSpec` properties to `ComposeUpTask` inputs. Add to the task configuration
in `GradleDockerPlugin`:

```groovy
// Inside task registration for ComposeUpTask
tasks.register("composeUp${stackName.capitalize()}", ComposeUpTask) { task ->
    // ... existing wiring ...

    // Wire waitForLog properties (if configured)
    if (stackSpec.waitForLog.isPresent()) {
        def logSpec = stackSpec.waitForLog.get()
        task.waitForLogServices.set(logSpec.waitForServices)
        task.waitForLogRejectPatterns.set(logSpec.rejectPatterns)
        task.waitForLogTimeoutSeconds.set(logSpec.timeoutSeconds)
        task.waitForLogPollSeconds.set(logSpec.pollSeconds)
        task.waitForLogCaseInsensitive.set(logSpec.caseInsensitive)
        task.waitForLogVerbose.set(logSpec.verbose)
        task.waitForLogProgressIntervalSeconds.set(logSpec.progressIntervalSeconds)
    }
}
```

### 12. Execution Order Change

The design document specifies updating the execution order to:
`waitForRunning` → `waitForHealthy` → `waitForLog`

This is a behavior change from the current order (`waitForHealthy` → `waitForRunning`). The
`performWaitIfConfigured` method in `ComposeUpTask` must be updated to reflect this order, as
shown in section 10 above.

**Rationale**: Containers must be running before health checks can pass, and health checks should
pass before checking for application-specific log messages. This reflects the natural startup
sequence.

### Summary of Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `spec/WaitForLogSpec.groovy` | Create | DSL configuration class |
| `model/WaitForLogConfig.groovy` | Create | Immutable runtime config |
| `model/WaitForLogResult.groovy` | Create | Per-service match results |
| `model/LogsConfig.groovy` | Create/Verify | Log capture configuration |
| `util/LogPatternMatcher.groovy` | Create | Pure pattern matching logic |
| `util/WaitForLogConfigBuilder.groovy` | Create | Config builder with validation |
| `spec/ComposeStackSpec.groovy` | Modify | Add `waitForLog` block |
| `service/ComposeService.groovy` | Modify | Add `waitForLogPatterns()` and `captureLogs()` |
| `service/ExecLibraryComposeService.groovy` | Modify | Implement `waitForLogPatterns()` and `captureLogs()` |
| `exception/ComposeServiceException.groovy` | Modify | Add error types |
| `task/ComposeUpTask.groovy` | Modify | Add properties and execution |
| `GradleDockerPlugin.groovy` | Modify | Wire spec to task properties |
| `TestIntegrationExtension.groovy` | Modify | Propagate waitForLog to test extensions |

### 13. Test Framework Extension Integration

When using `usesCompose()` with test framework extensions, `waitForLog` configuration must be
propagated so it's executed as part of compose stack startup. Modify `TestIntegrationExtension`
to pass `waitForLog` configuration to the test lifecycle.

**Modify `TestIntegrationExtension.groovy`:**

```groovy
// Add property for waitForLog configuration
// This is passed from ComposeStackSpec when usesCompose() is called

// When configuring a test task with usesCompose():
void usesCompose(Map<String, Object> options) {
    def stackName = options.stack as String
    def lifecycle = options.lifecycle as String ?: "class"

    // Find the compose stack spec
    def dockerTestExt = project.extensions.findByType(DockerTestExtension)
    def stackSpec = dockerTestExt?.composeStacks?.findByName(stackName)

    if (stackSpec != null) {
        // Pass waitForLog configuration to test system properties
        // This enables the test framework extension to trigger waitForLog
        if (stackSpec.waitForLog.isPresent()) {
            def logSpec = stackSpec.waitForLog.get()

            // Serialize waitForLog config as system properties for test framework
            testTask.systemProperty("compose.${stackName}.waitForLog.enabled", "true")
            testTask.systemProperty("compose.${stackName}.waitForLog.services",
                serializeMapToJson(logSpec.waitForServices.get()))
            testTask.systemProperty("compose.${stackName}.waitForLog.timeout",
                logSpec.timeoutSeconds.get().toString())
            testTask.systemProperty("compose.${stackName}.waitForLog.poll",
                logSpec.pollSeconds.get().toString())
            testTask.systemProperty("compose.${stackName}.waitForLog.caseInsensitive",
                logSpec.caseInsensitive.get().toString())
            testTask.systemProperty("compose.${stackName}.waitForLog.verbose",
                logSpec.verbose.get().toString())

            if (logSpec.rejectPatterns.present && !logSpec.rejectPatterns.get().isEmpty()) {
                testTask.systemProperty("compose.${stackName}.waitForLog.rejectPatterns",
                    serializeMapToJson(logSpec.rejectPatterns.get()))
            }
        }
    }
}

/**
 * Serialize a Map<String, List<String>> to JSON for passing via system properties.
 */
private String serializeMapToJson(Map<String, List<String>> map) {
    def jsonService = project.extensions.getByType(DockerExtension).jsonService
    return jsonService.toJson(map)
}
```

**Note:** The test framework extension (e.g., Spock/JUnit extension) must be updated to:
1. Read these system properties at test setup time
2. Invoke `waitForLogPatterns()` after `composeUp` but before running tests
3. Handle failures appropriately (fail test class/method setup)

This integration ensures that when a user configures:

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

The `waitForLog` block is automatically executed when the compose stack starts up, before any
test methods run.

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

**Fallback Implementation:**

1. **In ComposeUpTask**, replace the `MapProperty` with a `Property<String>` containing JSON:

```groovy
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

2. **In GradleDockerPlugin**, serialize the map when wiring:

```groovy
// Serialize map to JSON when configuring task
if (stackSpec.waitForLog.isPresent()) {
    def logSpec = stackSpec.waitForLog.get()
    def jsonService = ... // inject or obtain JsonService

    task.waitForLogServicesJson.set(
        providers.provider { jsonService.toJson(logSpec.waitForServices.get()) }
    )
    if (logSpec.rejectPatterns.present) {
        task.waitForLogRejectPatternsJson.set(
            providers.provider { jsonService.toJson(logSpec.rejectPatterns.get()) }
        )
    }
    // ... other properties
}
```

3. **In performWaitForLog()**, deserialize the JSON back to a map:

```groovy
private void performWaitForLog(String projectName) {
    def jsonService = ... // obtain JsonService

    Map<String, List<String>> services = waitForLogServicesJson.present
        ? jsonService.fromJson(waitForLogServicesJson.get(), Map)
        : null

    Map<String, List<String>> rejectPatterns = waitForLogRejectPatternsJson.present
        ? jsonService.fromJson(waitForLogRejectPatternsJson.get(), Map)
        : null

    def config = WaitForLogConfigBuilder.build(
        projectName,
        services,
        rejectPatterns,
        // ... other properties
    )
    // ... rest of implementation
}
```

This fallback ensures configuration cache compatibility by using only primitive/String types
that are guaranteed to serialize correctly. Test this approach early in implementation to
determine if the fallback is needed.
