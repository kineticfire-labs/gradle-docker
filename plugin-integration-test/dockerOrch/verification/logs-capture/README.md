# Verification Test: Log Capture Functionality

**Type**: Plugin Mechanics Validation
**Lifecycle**: SUITE (composeUp before tests, composeDown after tests)
**Purpose**: Validates plugin's log capture capabilities with different configurations

## What This Test Validates

This verification test ensures the dockerOrch plugin correctly:
- ✅ Captures container logs to configured output files
- ✅ Respects `tailLines` configuration (limits number of lines captured)
- ✅ Supports service-specific log capture (only specified services)
- ✅ Creates log files at expected locations in build directory
- ✅ Captures startup logs, runtime logs, and request-triggered logs
- ✅ Works with multiple log capture configurations simultaneously

## Key Demonstrations

### 1. Full Log Capture

Captures all logs from all services without any line limit:

```groovy
dockerOrch {
    composeStacks {
        logsCaptureTestFull {
            files.from('src/integrationTest/resources/compose/logs-capture.yml')

            logs {
                outputFile = layout.buildDirectory.file('compose-logs/full-test.log')
                // No tailLines specified - captures all logs
            }
        }
    }
}
```

**Use Case**: When you need complete logs for comprehensive debugging or audit trails.

### 2. Tail Lines Configuration

Captures only the last N lines of logs:

```groovy
dockerOrch {
    composeStacks {
        logsCaptureTestTail {
            files.from('src/integrationTest/resources/compose/logs-capture.yml')

            logs {
                outputFile = layout.buildDirectory.file('compose-logs/tail-test.log')
                tailLines = 20  // Only last 20 lines
            }
        }
    }
}
```

**Use Case**: When you only need recent logs and want to keep file sizes small.

### 3. Service-Specific Log Capture

Captures logs from specific services only:

```groovy
dockerOrch {
    composeStacks {
        logsCaptureTestService {
            files.from('src/integrationTest/resources/compose/logs-capture.yml')

            logs {
                outputFile = layout.buildDirectory.file('compose-logs/service-test.log')
                services = ['logs-capture-app']  // Only this service
                tailLines = 100
            }
        }
    }
}
```

**Use Case**: When you have multi-service stacks but only need logs from services likely to have errors.

## Test Architecture

This test uses a single-service stack with a Spring Boot application that:
- **Logs at startup**: Application initialization messages
- **Logs at different levels**: INFO, WARN, ERROR
- **Logs on requests**: Request-triggered log messages
- **Logs multi-line content**: Multiple consecutive log lines

The app provides REST endpoints to trigger logging:
- `GET /log/{level}` - Trigger log at specified level (info, warn, error)
- `GET /multiline` - Trigger multi-line log output
- `GET /health` - Health check endpoint

## Test Structure

```groovy
class LogsCapturePluginIT extends Specification {

    static String projectNameFull
    static File logFileFull

    static String projectNameTail
    static File logFileTail

    static String projectNameService
    static File logFileService

    def setupSpec() {
        // Read state and log file paths from system properties
        // Trigger log messages before running tests
    }

    def "plugin should create log file for full log capture"() { ... }
    def "full log file should contain startup messages"() { ... }
    def "full log file should contain warning and error messages"() { ... }
    def "full log file should contain multi-line test messages"() { ... }
    def "full log file should contain request-triggered logs"() { ... }

    def "plugin should create log file for tail log capture"() { ... }
    def "tail log file should respect tailLines configuration"() { ... }

    def "plugin should create log file for service-specific log capture"() { ... }
    def "service-specific log file should contain logs from specified service"() { ... }
    def "service-specific log file should respect tailLines configuration"() { ... }

    def "full log should be longer than tail log"() { ... }
    def "all log files should be accessible and valid"() { ... }
}
```

## Running Tests

**⚠️ Important**: All commands must be run from `plugin-integration-test/` directory.

### Full Integration Test Workflow

```bash
# From plugin-integration-test/ directory
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:logs-capture:app-image:runIntegrationTest
```

This runs:
1. Build app JAR (Spring Boot app with logging endpoints)
2. Build Docker image
3. Start three Docker Compose stacks (full, tail, service)
4. Wait for all services to be HEALTHY
5. Trigger log messages via REST API calls
6. Run integration tests (validates log files and content)
7. Stop all Docker Compose stacks

### Individual Tasks

```bash
# Clean build artifacts
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:logs-capture:clean

# Build the app JAR
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:logs-capture:app:bootJar

# Build Docker image
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:logs-capture:app-image:dockerBuildLogsCaptureApp

# Start all Docker Compose stacks
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:logs-capture:app-image:composeUpAll

# Run tests (requires stacks to be up)
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:logs-capture:app-image:integrationTest

# Stop all Docker Compose stacks
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:logs-capture:app-image:composeDownAll
```

## Tests Performed

### 1. Full Log Capture Validation (7 tests)
- Log file is created at expected location
- Log file contains startup messages
- Log file contains warning and error messages
- Log file contains multi-line test messages
- Log file contains request-triggered logs
- State file has valid structure
- Container is in HEALTHY state

### 2. Tail Lines Configuration Validation (3 tests)
- Log file is created
- Log file respects tailLines limit (≤20 lines)
- Log file contains recent logs (last lines)

### 3. Service-Specific Log Capture Validation (3 tests)
- Log file is created
- Log file contains logs from specified service
- Log file respects tailLines limit (≤100 lines)

### 4. Log File Comparison Tests (2 tests)
- Full log is longer than tail log
- All log files are accessible and valid

**Total**: 18 integration tests

## Validation Tools Used

This test uses internal validators from `buildSrc/`:

- **`StateFileValidator`** - Validates state file structure and content
  - `parseStateFile()` - Parse JSON state file
  - `assertValidStructure()` - Check required fields
  - `getPublishedPort()` - Extract port mappings

- **`DockerComposeValidator`** - Validates Docker Compose state
  - `isServiceHealthyViaCompose()` - Check service is healthy

- **File Operations** - Standard Groovy file operations
  - `File.exists()` - Check file exists
  - `File.text` - Read entire file content
  - `File.readLines()` - Read file as list of lines
  - `File.length()` - Get file size in bytes

## Expected Results

All 18 tests should pass:
- ✅ All three log files are created at expected locations
- ✅ Full log contains all startup and request logs (no line limit)
- ✅ Tail log respects 20-line limit and contains recent logs
- ✅ Service-specific log respects 100-line limit
- ✅ Log files contain expected content (startup messages, warnings, errors)
- ✅ All containers are HEALTHY before tests run
- ✅ State files have valid structure

No containers should remain after tests complete.

## Troubleshooting

**Tests fail with "log file not found"**:
- Ensure Docker Compose stacks started successfully
- Check that `composeUp*` tasks completed before `integrationTest`
- Log files are created at:
  - `build/compose-logs/full-test.log`
  - `build/compose-logs/tail-test.log`
  - `build/compose-logs/service-test.log`
- Use `runIntegrationTest` task which handles dependencies automatically

**Log files are empty or missing content**:
- Ensure containers had time to generate logs (health check ensures app started)
- Verify container is running: `docker ps -a | grep logs-capture`
- Check container logs directly: `docker logs <container-id>`
- Verify log capture configuration in build.gradle

**Tail lines test fails (too many lines)**:
- Check that `tailLines` configuration is properly set in build.gradle
- Verify the plugin is respecting the `tailLines` parameter
- This could indicate a plugin bug if consistently fails

**Containers remain after tests**:
- Run manual cleanup:
  ```bash
  docker ps -a -q --filter "name=verification-logs-capture" | xargs -r docker rm -f
  ```
- Check networks: `docker network ls | grep verification-logs-capture`

## Use Case

This test validates the plugin's ability to:

1. **Capture logs for debugging**
   - When tests fail, logs provide debugging context
   - Can capture logs automatically on test failure
   - Logs persist after containers are removed

2. **Control log file size**
   - `tailLines` limits the number of lines captured
   - Useful for large applications with verbose logging
   - Prevents excessive disk usage

3. **Focus on specific services**
   - Multi-service stacks may have many containers
   - Only capture logs from services likely to have errors
   - Reduces log file size and improves signal-to-noise ratio

4. **Support multiple configurations**
   - Different stacks can have different log capture settings
   - Mix full capture with tail capture in same build
   - Flexibility for various debugging needs

## Log Capture Best Practices

### When to Use Full Log Capture

```groovy
logs {
    outputFile = layout.buildDirectory.file('compose-logs/full.log')
    // No tailLines - captures everything
}
```

**Use when**:
- Debugging complex issues requiring full context
- Running in CI/CD where logs are artifacts
- Troubleshooting intermittent failures

### When to Use Tail Lines

```groovy
logs {
    outputFile = layout.buildDirectory.file('compose-logs/recent.log')
    tailLines = 500
}
```

**Use when**:
- You only need recent activity
- Applications have verbose logging
- Managing disk space is important

### When to Use Service-Specific Capture

```groovy
logs {
    outputFile = layout.buildDirectory.file('compose-logs/app-only.log')
    services = ['api-server', 'background-worker']
    tailLines = 1000
}
```

**Use when**:
- You know which services are likely to fail
- Some services have extremely verbose logging
- You want focused, relevant logs

### Dynamic Log File Names

```groovy
logs {
    outputFile = layout.buildDirectory.file(
        "compose-logs/${stackName}-${new Date().format('yyyyMMdd-HHmmss')}.log"
    )
    tailLines = 1000
}
```

**Use when**:
- Running multiple test runs and comparing logs
- Preserving logs from previous runs
- CI/CD environments where logs should be timestamped

## Configuration Options

The `logs` block supports the following options:

| Option | Type | Required | Default | Description |
|--------|------|----------|---------|-------------|
| `outputFile` | `File` | Yes | N/A | Path to log output file |
| `tailLines` | `Integer` | No | All lines | Limit number of lines captured |
| `services` | `List<String>` | No | All services | Specific services to capture logs from |
| `follow` | `Boolean` | No | `false` | Follow logs (continuous capture) |

## ⚠️ This is NOT a User Example

This test validates plugin mechanics using internal tools. For user-facing examples of how to capture logs
in real applications, see the `examples/` directory tests which demonstrate log capture in practical scenarios.

## Integration with Test Lifecycle

### SUITE Lifecycle

This test uses SUITE lifecycle (composeUp before tests, composeDown after tests):

```groovy
dockerOrch {
    composeStacks {
        myTest {
            lifecycle = 'SUITE'

            logs {
                outputFile = layout.buildDirectory.file('compose-logs/test.log')
                tailLines = 1000
            }
        }
    }
}
```

**Benefits**:
- Logs captured at test completion (includes all test activity)
- Single startup/shutdown (efficient)
- Logs reflect cumulative test activity

### When Logs Are Captured

Logs are captured when `composeDown` runs:
1. Tests complete (pass or fail)
2. `composeDown` task executes
3. Plugin captures logs before stopping containers
4. Log files written to configured location
5. Containers stopped and removed

**Important**: Logs are captured BEFORE containers are stopped, ensuring all logs are available.
