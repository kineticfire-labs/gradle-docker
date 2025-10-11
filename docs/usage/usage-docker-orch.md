# 'dockerOrch' DSL Usage Guide

This document provides simple, informal examples of how to use the 'dockerOrch' (e.g., 'docker compose') DSL for the 
'gradle-docker' plugin.

## Prerequisites

First, add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
}
```

## Recommended Directory Layout

```
the-application-project/                  # a project that (1) builds an application and tests it, and (2) puts the 
│                                           application in a Linux image and tests the unit of delivery by spinning up 
│                                           the container and testing it
├─ app/                                   # builds the application, such as a JAR (or other artifact)
│  ├─ build.gradle
│  └─ src/
│     ├─ main/java/...
│     └─ test/java/...
└─ app-image/                              # builds the Linux image + tests it
   ├─ build.gradle
   ├─ src/
   │  ├─ main/docker/                      # Dockerfile + build assets (image context)
   │  │  ├─ Dockerfile
   │  │  └─ ...                            # scripts, config, .dockerignore, etc.
   │  ├─ integrationTest/groovy/           # Groovy/Spock integration tests
   │  ├─ integrationTest/java/             # Java/JUnit integration tests
   │  ├─ integrationTest/resources/
   │  │  ├─ compose/                       # compose files for integration tests
   │  │  └─ docker/                        # optional: test-only wrapper image assets
   │  └─ testFixtures/                     # (optional) shared test helpers/utilities
   ├─ docs/                                # (optional) runbooks, diagrams for tests
   └─ build/                               # outputs: transcripts, logs, saved tars, state JSON, etc.
      ├─ docker/                           # image tars (from dockerSave*)
      ├─ compose-logs/                     # compose logs by task/suite
      └─ compose/                          # compose state files (JSON) per stack
```


## Gradle 9 and 10 Compatibility

This plugin is fully compatible with Gradle 9 and 10, including configuration cache support. Follow these patterns for
best compatibility in [Gradle 9 and 10 Compatibility](gradle-9-and-10-compatibility-practices.md).

## Overview of Docker Compose Orchestration

The `dockerOrch` DSL provides three lifecycle patterns for Docker Compose orchestration in your tests:

1. **Suite Lifecycle** - Containers run for the entire test suite (fastest, best for stable services)
2. **Class Lifecycle** - Containers run for all tests in a class (balanced performance and isolation)
3. **Method Lifecycle** - Containers run for each test method (maximum isolation, slower)

Each pattern includes:
- Automated container startup and shutdown
- Health/readiness waiting
- Log capture
- State file generation with service info

## Suite Lifecycle Pattern

The suite lifecycle starts containers before all tests and stops them after all tests complete. This is the most
performant option for tests that don't modify the containers' state.

### Basic Configuration

```gradle
// build.gradle
plugins {
    id 'java'
    id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
}

dockerOrch {
    stacks {
        apiTests {
            projectName = 'my-app'
            stackName = 'api-test-stack'
            composeFiles = [
                file('src/integrationTest/resources/compose/api-test.yml')
            ]
            envFiles = [
                file('src/integrationTest/resources/compose/.env')
            ]
        }
    }
}

// Define integration test source set
sourceSets {
    integrationTest {
        java.srcDir 'src/integrationTest/java'
        resources.srcDir 'src/integrationTest/resources'
        compileClasspath += sourceSets.main.output + configurations.testRuntimeClasspath
        runtimeClasspath += output + compileClasspath
    }
}

// Create custom integration test task
task integrationTest(type: Test) {
    description = 'Runs integration tests with Docker Compose'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath

    // Link to dockerOrch stack
    usesCompose('apiTests')

    // Tests depend on containers being started
    dependsOn composeUpApiTests
    finalizedBy composeDownApiTests
}
```

### Docker Compose File

```yaml
# src/integrationTest/resources/compose/api-test.yml
services:
  api-server:
    image: my-api:latest
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=postgres
      - DB_PORT=5432
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 10

  postgres:
    image: postgres:15
    environment:
      - POSTGRES_DB=testdb
      - POSTGRES_USER=test
      - POSTGRES_PASSWORD=test
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "test"]
      interval: 2s
      timeout: 1s
      retries: 10
```

### Wait for Services

```gradle
dockerOrch {
    stacks {
        apiTests {
            projectName = 'my-app'
            stackName = 'api-test-stack'
            composeFiles = [file('src/integrationTest/resources/compose/api-test.yml')]

            // Wait for services to be healthy
            wait {
                services = ['api-server', 'postgres']
                timeout = duration(60, 'SECONDS')
                pollInterval = duration(2, 'SECONDS')
                waitForStatus = 'HEALTHY'  // Options: 'RUNNING', 'HEALTHY'
            }
        }
    }
}
```

### Capture Logs

```gradle
dockerOrch {
    stacks {
        apiTests {
            projectName = 'my-app'
            stackName = 'api-test-stack'
            composeFiles = [file('src/integrationTest/resources/compose/api-test.yml')]

            // Capture logs when stack is torn down
            logs {
                outputFile = file("${buildDir}/compose-logs/api-test-suite.log")
                services = ['api-server']  // Optional: specific services only
                tailLines = 1000           // Optional: limit lines (default: all)
                follow = false             // Optional: follow logs (default: false)
            }
        }
    }
}
```

### Accessing State Files

After `composeUp` completes, a state file is generated at `build/compose-state/<stackName>-state.json`:

```json
{
  "stackName": "api-test-stack",
  "projectName": "my-app-123456",
  "lifecycle": "suite",
  "timestamp": "2025-01-15T10:30:45",
  "services": {
    "api-server": {
      "containerId": "abc123def456",
      "containerName": "my-app-api-server-1",
      "state": "running",
      "publishedPorts": ["0.0.0.0:8080->8080/tcp"]
    },
    "postgres": {
      "containerId": "def789ghi012",
      "containerName": "my-app-postgres-1",
      "state": "running",
      "publishedPorts": ["5432/tcp"]
    }
  }
}
```

Access in tests via system property:

```java
String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
// Parse JSON to get service info, ports, etc.
```

## Class Lifecycle Pattern

The class lifecycle starts containers before all test methods in a class and stops them after all methods complete.
This provides better isolation than suite lifecycle while maintaining good performance.

### JUnit 5 Configuration

```java
package com.example.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DockerComposeClassExtension.class)
class ApiIntegrationTest {

    // Containers start once before all tests
    // All test methods share the same containers

    @Test
    void testGetUser() {
        // Test implementation
        // Access services via ports from state file
    }

    @Test
    void testCreateUser() {
        // Test implementation
    }

    // Containers stop once after all tests
}
```

### Gradle Configuration

```gradle
dockerOrch {
    stacks {
        classTests {
            projectName = 'my-app'
            stackName = 'class-test-stack'
            composeFiles = [file('src/integrationTest/resources/compose/integration-class.yml')]

            wait {
                services = ['api-server']
                timeout = duration(30, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }

            logs {
                outputFile = file("${buildDir}/compose-logs/class-test.log")
                tailLines = 500
            }
        }
    }
}

task integrationTest(type: Test) {
    usesCompose('classTests')
    // Extension reads docker.compose.stack and docker.compose.project from system properties
}
```

### System Properties

The extension requires these system properties (automatically set by `usesCompose()`):
- `docker.compose.stack` - Stack name
- `docker.compose.project` - Project name base
- `COMPOSE_STATE_FILE` - Path to generated state file

## Method Lifecycle Pattern

The method lifecycle starts fresh containers for each test method. This provides maximum isolation but is the slowest
option.

### JUnit 5 Configuration

```java
package com.example.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DockerComposeMethodExtension.class)
class IsolatedApiTest {

    @Test
    void testUserCreation() {
        // Fresh containers started before this test
        // Test implementation
        // Containers stopped after this test
    }

    @Test
    void testUserDeletion() {
        // Fresh containers started before this test
        // Test implementation
        // Containers stopped after this test
    }
}
```

### Gradle Configuration

```gradle
dockerOrch {
    stacks {
        methodTests {
            projectName = 'my-app'
            stackName = 'method-test-stack'
            composeFiles = [file('src/integrationTest/resources/compose/integration-method.yml')]

            wait {
                services = ['api-server', 'postgres']
                timeout = duration(45, 'SECONDS')
                waitForStatus = 'RUNNING'  // Can use RUNNING for faster tests
            }
        }
    }
}

task integrationTest(type: Test) {
    usesCompose('methodTests')
}
```

## Advanced Configuration Examples

### Multiple Stacks

```gradle
dockerOrch {
    stacks {
        smokeTests {
            projectName = 'my-app'
            stackName = 'smoke-stack'
            composeFiles = [file('src/integrationTest/resources/compose/smoke.yml')]
        }

        performanceTests {
            projectName = 'my-app'
            stackName = 'perf-stack'
            composeFiles = [file('src/integrationTest/resources/compose/performance.yml')]
        }
    }
}

task smokeTest(type: Test) {
    usesCompose('smokeTests')
    dependsOn composeUpSmokeTests
    finalizedBy composeDownSmokeTests
}

task performanceTest(type: Test) {
    usesCompose('performanceTests')
    dependsOn composeUpPerformanceTests
    finalizedBy composeDownPerformanceTests
}
```

### Multiple Compose Files

```gradle
dockerOrch {
    stacks {
        fullStack {
            projectName = 'my-app'
            stackName = 'full-stack'
            composeFiles = [
                file('src/integrationTest/resources/compose/base.yml'),
                file('src/integrationTest/resources/compose/services.yml'),
                file('src/integrationTest/resources/compose/overrides.yml')
            ]
            envFiles = [
                file('src/integrationTest/resources/compose/.env.base'),
                file('src/integrationTest/resources/compose/.env.local')
            ]
        }
    }
}
```

### Conditional Waiting

```gradle
dockerOrch {
    stacks {
        conditionalWait {
            projectName = 'my-app'
            stackName = 'conditional-stack'
            composeFiles = [file('src/integrationTest/resources/compose/conditional.yml')]

            wait {
                // Wait for database to be running, API to be healthy
                services = ['postgres', 'api-server']
                timeout = duration(60, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }
        }
    }
}
```

## Troubleshooting Guide

### Common Issues and Solutions

#### 1. Containers Not Stopping After Tests

**Symptom:** Running `docker ps` shows leftover containers from previous test runs.

**Solution:** Ensure `finalizedBy composeDown*` is configured:

```gradle
task integrationTest(type: Test) {
    dependsOn composeUpApiTests
    finalizedBy composeDownApiTests  // IMPORTANT: Always clean up
}
```

**Manual Cleanup:**
```bash
# Stop all containers for a project
docker compose -p my-app down -v

# Force remove containers by name pattern
docker ps -aq --filter name=my-app | xargs -r docker rm -f
```

#### 2. Health Checks Timing Out

**Symptom:** Tests fail with "Service did not become healthy within timeout"

**Solutions:**
- Increase timeout: `timeout = duration(120, 'SECONDS')`
- Use `waitForStatus = 'RUNNING'` instead of `'HEALTHY'` if health checks aren't critical
- Verify health check in compose file is correct
- Check service logs: `docker compose -p my-app logs <service-name>`

```gradle
wait {
    services = ['slow-service']
    timeout = duration(120, 'SECONDS')  // Increased timeout
    pollInterval = duration(5, 'SECONDS')  // Check less frequently
    waitForStatus = 'RUNNING'  // Or just wait for running
}
```

#### 3. Port Conflicts

**Symptom:** `docker compose up` fails with "port is already allocated"

**Solutions:**
- Use unique project names to avoid conflicts:
  ```gradle
  projectName = "my-app-${System.currentTimeMillis()}"
  ```
- Don't hardcode host ports in compose files; let Docker assign them:
  ```yaml
  ports:
    - "8080"  # Docker assigns random host port
  ```
- Find conflicting containers:
  ```bash
  docker ps --filter publish=8080
  docker stop $(docker ps -q --filter publish=8080)
  ```

#### 4. State File Not Found

**Symptom:** `System.getProperty("COMPOSE_STATE_FILE")` returns null

**Solutions:**
- Ensure `usesCompose('stackName')` is called on the test task
- Verify `composeUp` task completed successfully
- Check `build/compose-state/` directory exists and contains JSON files
- For suite lifecycle, state file is generated during `composeUp` task

#### 5. Logs Not Captured

**Symptom:** Log files are empty or missing

**Solutions:**
- Verify `logs {}` block is configured in `dockerOrch` DSL
- Ensure `composeDown` task runs (it captures logs during shutdown)
- Check parent directories exist or plugin can create them
- Verify services are actually running and producing logs

```gradle
logs {
    outputFile = file("${buildDir}/compose-logs/test.log")
    services = []  // Empty list = capture all services
    tailLines = 0  // 0 = capture all lines (not just tail)
}
```

#### 6. Extension Not Running Containers

**Symptom:** JUnit extension (`DockerComposeClassExtension` or `DockerComposeMethodExtension`) doesn't start containers

**Solutions:**
- Verify `@ExtendWith` annotation is present on test class
- Ensure compose file exists at expected path:
  ```
  src/integrationTest/resources/compose/integration-class.yml
  src/integrationTest/resources/compose/integration-method.yml
  ```
- Check test task has `usesCompose('stackName')` configured
- Verify system properties are set (they're auto-set by `usesCompose()`)
- Check test output for error messages during `beforeAll`/`beforeEach`

#### 7. Compose File Version Warnings

**Symptom:** Warning about deprecated `version` field in compose file

**Solution:** Remove the `version:` field from compose files (deprecated in Compose Specification):

```yaml
# WRONG - deprecated
version: '3.8'
services:
  web:
    image: nginx

# CORRECT - no version field
services:
  web:
    image: nginx
```

#### 8. Configuration Cache Issues

**Symptom:** Gradle configuration cache warnings or failures

**Solution:** Follow these patterns for configuration cache compatibility:

```gradle
// WRONG - captures Project reference
dockerOrch {
    stacks {
        test {
            composeFiles = [project.file('compose.yml')]  // BAD
        }
    }
}

// CORRECT - uses file() method
dockerOrch {
    stacks {
        test {
            composeFiles = [file('compose.yml')]  // GOOD
        }
    }
}
```

See [Gradle 9 and 10 Compatibility](gradle-9-and-10-compatibility-practices.md) for more details.

## Best Practices

### 1. Choose the Right Lifecycle

- **Suite:** For read-only tests, smoke tests, tests that don't modify state
- **Class:** For tests that modify state but can share setup/teardown
- **Method:** For tests that need complete isolation or modify global state

### 2. Optimize Health Checks

```yaml
services:
  api:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/health"]
      interval: 2s      # Check frequently for fast startup
      timeout: 1s       # Fail fast
      retries: 30       # But allow enough retries
      start_period: 10s # Grace period before health checks count
```

### 3. Use Meaningful Project Names

```gradle
projectName = "my-app-${rootProject.name}"  // Unique per project
```

### 4. Structure Compose Files

```
src/integrationTest/resources/compose/
├── base.yml              # Common services (databases, queues)
├── services.yml          # Application services
├── overrides.yml         # Test-specific overrides
├── .env.base             # Shared environment variables
└── .env.local            # Local overrides (gitignored)
```

### 5. Capture Useful Logs

```gradle
logs {
    outputFile = file("${buildDir}/compose-logs/${name}-${new Date().format('yyyyMMdd-HHmmss')}.log")
    services = ['api-server']  # Only services likely to have errors
    tailLines = 1000           # Last 1000 lines usually sufficient
}
```

### 6. Clean Up Aggressively

```gradle
// Always clean up, even on failure
task integrationTest(type: Test) {
    dependsOn composeUpApiTests
    finalizedBy composeDownApiTests

    doFirst {
        // Optional: Clean up any leftover containers before starting
        exec {
            commandLine 'docker', 'compose', '-p', 'my-app', 'down', '-v'
            ignoreExitValue = true
        }
    }
}
```

## Complete Example

See the [integration test examples](../../plugin-integration-test/dockerOrch/) in this repository for complete,
working examples of all three lifecycle patterns.