# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- DSL / User Description

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context

## Purpose

Explain the functionality that should be achieved and how the user will interact with the new functionality,
specifically describing the new DSL additions.

This document MUST NOT include implementation aspects or tests--  those are deferred to other documents.

The overview of the design is at `add-wait-for-log-0000-overview.md`.

## Checklist

### DSL Documentation Completeness

- [ ] Functionality goals defined
- [ ] Spec class properties documented
- [ ] Property conventions specified
- [ ] DSL syntax (both `.set()` and direct assignment styles)
- [ ] Configuration properties table with types, required/optional, defaults, and descriptions
- [ ] Configuration cache compatibility requirements documented
- [ ] Pattern matching semantics documented
- [ ] Regex escaping guide included
- [ ] Usage examples for common scenarios
- [ ] Combined usage with other wait blocks documented
- [ ] Common patterns for popular services (Spring Boot, PostgreSQL, Redis, etc.)
- [ ] Performance considerations documented
- [ ] Progress logging behavior documented (default, verbose, periodic)
- [ ] Validation error messages documented (including unknown service, orphaned reject patterns)
- [ ] Configuration warnings documented (pollSeconds > timeoutSeconds edge case)
- [ ] Error handling (timeout, service crash, reject pattern) documented
- [ ] Total timeout calculation explained
- [ ] Test framework extension integration documented
- [ ] Limitations documented (Lifecycle.METHOD not supported with enforcement error message)
- [ ] Docker Compose v2+ requirement documented

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

**⚠️ Configuration Warning**: If `pollSeconds` is greater than `timeoutSeconds`, a warning is logged because only one
poll attempt will occur before timeout. Example: with `timeoutSeconds=5` and `pollSeconds=10`, the wait will timeout
after the first (and only) poll attempt at t=0 if patterns aren't already present.

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

#### Unknown Service Name

If a configured service name doesn't exist in the compose project (e.g., typo), the error is caught early
with a helpful message listing available services:

```
Service(s) not found in compose project 'myproject': [appp].
Available services: [app, db, redis]

Hint: Check for typos in service names in your waitForLog configuration.
```

This validation runs at the start of the wait loop (after containers are created) and prevents silent
timeouts caused by misspelled service names.

#### Orphaned Reject Patterns Warning

If `rejectPatterns` contains services that are not in `waitForServices`, a warning is logged:

```
[waitForLog] WARNING: rejectPatterns contains services not in waitForServices: [unknown-service].
These reject patterns will never be checked.
```

This is a warning (not an error) because the configuration is technically valid, but likely indicates a
misconfiguration.

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

**Enforcement**: If you configure `waitForLog` with `Lifecycle.METHOD`, the build will fail with a clear error
message explaining the limitation and suggesting alternatives. This prevents silent misconfiguration where
the `waitForLog` block would be ignored.

**Example error when using waitForLog with Lifecycle.METHOD:**
```
Configuration error: 'waitForLog' is not yet supported with Lifecycle.METHOD.
The 'waitForLog' block in compose stack 'myTest' cannot be used with per-method lifecycle.

Options:
  1. Change to Lifecycle.CLASS: usesCompose(stack: 'myTest', lifecycle: 'class')
  2. Remove the 'waitForLog' block and use 'waitForHealthy' or 'waitForRunning' instead

Lifecycle.METHOD support for 'waitForLog' will be added in a future release.
```

If you need log-based readiness with per-method compose lifecycle, either:
1. Use `Lifecycle.CLASS` instead (compose stack shared across all test methods)
2. Wait for a future release that adds `Lifecycle.METHOD` support for `waitForLog`

⚠️ **Docker Compose v2+ Required**: The `waitForLog` feature requires Docker Compose v2.x or later due to its use of
`docker compose ps --format json` for service status checks. Docker Compose v1 (the Python-based `docker-compose`
command) uses a different JSON format that is not compatible.

| Docker Compose Version | Support |
|------------------------|---------|
| v2.x+ (`docker compose`) | ✅ Fully supported |
| v1.x (`docker-compose`) | ❌ Not supported |

If you are using Docker Compose v1, you will see JSON parsing errors when using `waitForLog`. Upgrade to Docker
Compose v2 (included with Docker Desktop 3.4+) or use `waitForHealthy` / `waitForRunning` instead.