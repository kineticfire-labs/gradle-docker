# Integration Test Implementation Plan - dockerOrch Task

**Date Created**: 2025-10-11
**Author**: Engineering Team
**Status**: Ready to Implement
**Estimated Effort**: 26 hours (3-4 days)
**Depends On**: Phase 1 Complete (core functionality implemented and unit tested)

## Executive Summary

Create user-centric integration tests for the `dockerOrch` task that:
1. **Mirror real-world usage**: Build app → Build image → Orchestrate containers → Test → Teardown
2. **Validate end-to-end functionality**: All Phase 1 features tested with real Docker
3. **Double as user examples**: Each scenario demonstrates plugin usage patterns
4. **Follow project structure**: Match `docker/` integration test patterns
5. **Ensure Gradle 9/10 compatibility**: Build cache, configuration cache, version catalogs

## Gradle 9 and 10 Compatibility Requirements

### Critical Compatibility Rules

All integration test scenarios MUST adhere to these Gradle 9/10 requirements:

#### 1. **Use Version Catalogs** (NOT direct dependency declarations)

**❌ WRONG (Old Style)**:
```groovy
dependencies {
    testImplementation 'org.spockframework:spock-core:2.3-groovy-4.0'
}
```

**✅ CORRECT (Version Catalog)**:
```groovy
// In gradle/libs.versions.toml
[versions]
spock = "2.3-groovy-4.0"
groovy = "4.0.23"

[libraries]
spock-core = { module = "org.spockframework:spock-core", version.ref = "spock" }
groovy-all = { module = "org.apache.groovy:groovy-all", version.ref = "groovy" }

// In build.gradle
dependencies {
    testImplementation libs.spock.core
    testImplementation libs.groovy.all
}
```

#### 2. **Configuration Cache Support**

All tasks must be compatible with Gradle's configuration cache:

```groovy
// Enable configuration cache in gradle.properties
org.gradle.configuration-cache=true
org.gradle.caching=true
```

**Requirements**:
- No `Project` object captured in task actions
- Use `Property<T>` and `Provider<T>` APIs
- All inputs/outputs properly declared
- No runtime resolution of configurations during configuration phase

#### 3. **Build Cache Configuration**

**Enable Build Cache** (unlike functional tests which have TestKit issues):

```groovy
// settings.gradle
buildCache {
    local {
        enabled = true
        directory = file('build-cache')
        removeUnusedEntriesAfterDays = 7
    }
}
```

**Task Cacheability**:
```groovy
// Mark tasks as cacheable where appropriate
tasks.register('integrationTest', Test) {
    // Integration tests are NOT cacheable (interact with Docker)
    outputs.cacheIf { false }
}
```

#### 4. **Provider API Usage**

Use lazy configuration with Provider API:

```groovy
// ✅ CORRECT
def jarFile = project(':app').tasks.named('bootJar').flatMap { it.archiveFile }

docker {
    images {
        webApp {
            buildArgs.put('JAR_FILE', jarFile.map { it.asFile.name })
        }
    }
}

// ❌ WRONG - eager evaluation
def jarFile = project(':app').tasks.bootJar.outputs.files.singleFile
```

#### 5. **Task Configuration Avoidance**

Use `tasks.named()` and `tasks.register()`:

```groovy
// ✅ CORRECT - lazy configuration
tasks.named('integrationTest') {
    // Configuration only when task is needed
}

tasks.register('runIntegrationTest') {
    // Registered but not configured until needed
}

// ❌ WRONG - eager configuration
tasks.integrationTest {
    // Configured immediately
}
```

#### 6. **Dependency Declaration in Version Catalog**

**File Location**: Single shared `gradle/libs.versions.toml` at `plugin-integration-test/dockerOrch/gradle/` (shared across all dockerOrch scenarios via symlinks)

**Structure**:
```toml
[versions]
groovy = "4.0.23"
spock = "2.3-groovy-4.0"
spring-boot = "3.2.0"
spring-dependency-management = "1.1.4"
junit-platform = "1.10.1"
postgres = "42.7.1"

[libraries]
# Testing
groovy-all = { module = "org.apache.groovy:groovy-all", version.ref = "groovy" }
spock-core = { module = "org.spockframework:spock-core", version.ref = "spock" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junit-platform" }

# Spring Boot
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgres" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
kineticfire-docker = { id = "com.kineticfire.gradle.docker", version = "1.0.0-SNAPSHOT" }
```

#### 7. **Avoid Deprecated APIs**

**No `version` in Docker Compose files**:
```yaml
# ❌ WRONG
version: '3.8'
services:
  ...

# ✅ CORRECT
services:
  ...
```

**Use modern task APIs**:
```groovy
// ✅ CORRECT
tasks.register('myTask', Test) {
    // Modern API
}

// ❌ WRONG
task myTask(type: Test) {
    // Deprecated API
}
```

---

## Overall Directory Structure

**IMPORTANT**:
- Root `settings.gradle` includes ALL docker and dockerOrch scenarios
- Each scenario has its own `settings.gradle` (makes it a Gradle project)
- Shared version catalog at `dockerOrch/gradle/libs.versions.toml` (symlinked into each scenario)
- buildSrc at root only (accessible to all)

```
plugin-integration-test/
├─ settings.gradle                              # ROOT - includes ALL scenarios
├─ build.gradle                                 # ROOT - aggregator tasks
├─ gradle.properties                            # Gradle 9/10 config
├─ gradle/
│  └─ libs.versions.toml                       # Root version catalog (if needed)
├─ buildSrc/                                    # Shared validators (accessible to all)
│  ├─ build.gradle
│  └─ src/main/groovy/com/kineticfire/test/
│     ├─ DockerComposeValidator.groovy
│     ├─ StateFileValidator.groovy
│     ├─ HttpValidator.groovy
│     └─ CleanupValidator.groovy
│
├─ docker/
│  ├─ build.gradle                              # Docker aggregator
│  ├─ gradle/
│  │  └─ libs.versions.toml                    # Shared for all docker scenarios
│  ├─ README.md
│  └─ scenario-*/                               # Each has: settings.gradle, gradle->../gradle symlink
│
└─ dockerOrch/
   ├─ build.gradle                              # DockerOrch aggregator
   ├─ gradle/
   │  └─ libs.versions.toml                    # SHARED for all dockerOrch scenarios
   ├─ gradlew                                   # Gradle wrapper
   ├─ README.md                                  # Scenario tracking matrix
   │
   ├─ scenario-1/                                # Web app + healthy wait (Groovy)
   │  ├─ settings.gradle                        # Includes 'app' and 'app-image'
   │  ├─ gradle -> ../gradle                    # SYMLINK to dockerOrch/gradle/
   │  ├─ gradlew -> ../gradlew                  # SYMLINK for convenience
   │  ├─ gradle.properties
   │  ├─ app/
   │  │  ├─ build.gradle
   │  │  └─ src/
   │  │     ├─ main/java/...                   # Spring Boot application
   │  │     └─ test/java/...                   # Unit tests
   │  └─ app-image/
   │     ├─ build.gradle                        # Uses docker + dockerOrch DSLs
   │     └─ src/
   │        ├─ main/docker/                     # Image build assets
   │        │  ├─ Dockerfile
   │        │  ├─ entrypoint.sh
   │        │  └─ .dockerignore
   │        └─ integrationTest/
   │           ├─ groovy/                       # Spock tests
   │           │  └─ com/kineticfire/test/
   │           │     └─ WebAppIT.groovy
   │           └─ resources/
   │              └─ compose/
   │                 └─ web-app.yml             # Compose file
   │
   ├─ scenario-2/                                # App + DB, mixed wait, Java
   │  ├─ settings.gradle
   │  ├─ gradle -> ../gradle                    # SYMLINK
   │  ├─ app/
   │  │  └─ build.gradle
   │  └─ app-image/
   │     └─ build.gradle
   │
   ├─ scenario-3/                                # Microservices, Groovy
   │  ├─ settings.gradle
   │  └─ gradle -> ../gradle                    # SYMLINK
   │
   ├─ scenario-4/                                # Existing images only (NO app/)
   │  ├─ settings.gradle                        # Only includes 'app-image'
   │  ├─ gradle -> ../gradle                    # SYMLINK
   │  └─ app-image/
   │     └─ build.gradle
   │
   ├─ scenario-5/                                # Class lifecycle, Java
   ├─ scenario-6/                                # Method lifecycle, Java
   └─ scenario-7/                                # Logs capture, Groovy
```

**Key Pattern Notes:**
- Each scenario's `gradle/` directory is a **symlink** to `../gradle` (→ `dockerOrch/gradle/`)
- This follows the exact pattern used by existing `docker/` integration tests
- All scenarios share the same version catalog via symlinks
- Scenarios are run from root: `./gradlew dockerOrch:scenario-1:integrationTest`

---

## Phase 2A: Shared Infrastructure (Estimated: 4 hours)

### Task 1: Create buildSrc Validators (2 hours)

**Location**: `plugin-integration-test/buildSrc/`

**Purpose**: Reusable validation code shared across ALL integration test scenarios

#### File: `buildSrc/build.gradle`

```groovy
plugins {
    id 'groovy-gradle-plugin'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation localGroovy()
}

// Ensure Gradle 9/10 compatibility
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

#### File: `buildSrc/src/main/groovy/com/kineticfire/test/DockerComposeValidator.groovy`

```groovy
package com.kineticfire.test

import groovy.transform.CompileStatic

/**
 * Validator for Docker Compose operations
 * Used by integration tests to verify container state
 */
@CompileStatic
class DockerComposeValidator {

    /**
     * Check if a container is running
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @return true if container is running
     */
    static boolean isContainerRunning(String projectName, String serviceName) {
        def process = [
            'docker', 'ps',
            '--filter', "name=${projectName}",
            '--filter', "name=${serviceName}",
            '--filter', 'status=running',
            '--format', '{{.Names}}'
        ].execute()
        process.waitFor()
        def output = process.text.trim()
        return output.contains(serviceName)
    }

    /**
     * Get all running containers for a project
     */
    static List<String> getRunningContainers(String projectName) {
        def process = [
            'docker', 'ps',
            '--filter', "name=${projectName}",
            '--filter', 'status=running',
            '--format', '{{.Names}}'
        ].execute()
        process.waitFor()
        return process.text.trim().split('\n').findAll { it }
    }

    /**
     * Get container status (running, healthy, unhealthy, exited, etc.)
     */
    static String getContainerStatus(String projectName, String serviceName) {
        // First get container name
        def nameProcess = [
            'docker', 'ps', '-a',
            '--filter', "name=${projectName}",
            '--filter', "name=${serviceName}",
            '--format', '{{.Names}}'
        ].execute()
        nameProcess.waitFor()
        def containerName = nameProcess.text.trim().split('\n').find { it }

        if (!containerName) {
            return 'not-found'
        }

        // Get status including health
        def statusProcess = [
            'docker', 'inspect', containerName,
            '--format', '{{.State.Status}}'
        ].execute()
        statusProcess.waitFor()
        def status = statusProcess.text.trim()

        // Check health if container is running
        if (status == 'running') {
            def healthProcess = [
                'docker', 'inspect', containerName,
                '--format', '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}'
            ].execute()
            healthProcess.waitFor()
            def health = healthProcess.text.trim()

            if (health == 'healthy') {
                return 'healthy'
            } else if (health == 'unhealthy') {
                return 'unhealthy'
            } else if (health == 'starting') {
                return 'starting'
            }
        }

        return status
    }

    /**
     * Check if container is healthy
     */
    static boolean isContainerHealthy(String projectName, String serviceName) {
        return getContainerStatus(projectName, serviceName) == 'healthy'
    }

    /**
     * Get container logs
     */
    static String getContainerLogs(String projectName, String serviceName, int tailLines = 100) {
        def nameProcess = [
            'docker', 'ps', '-a',
            '--filter', "name=${projectName}",
            '--filter', "name=${serviceName}",
            '--format', '{{.Names}}'
        ].execute()
        nameProcess.waitFor()
        def containerName = nameProcess.text.trim().split('\n').find { it }

        if (!containerName) {
            return "Container not found: ${projectName}/${serviceName}"
        }

        def logsProcess = [
            'docker', 'logs', '--tail', tailLines.toString(), containerName
        ].execute()
        logsProcess.waitFor()
        return logsProcess.text
    }

    /**
     * Find all containers (running or stopped) matching project name
     */
    static List<String> findAllContainers(String projectName) {
        def process = [
            'docker', 'ps', '-a',
            '--filter', "name=${projectName}",
            '--format', '{{.Names}}'
        ].execute()
        process.waitFor()
        return process.text.trim().split('\n').findAll { it }
    }

    /**
     * Find all networks matching project name
     */
    static List<String> findAllNetworks(String projectName) {
        def process = [
            'docker', 'network', 'ls',
            '--filter', "name=${projectName}",
            '--format', '{{.Name}}'
        ].execute()
        process.waitFor()
        return process.text.trim().split('\n').findAll {
            it && it != 'bridge' && it != 'host' && it != 'none'
        }
    }

    /**
     * Assert no containers or networks remain
     */
    static void assertNoLeaks(String projectName) {
        def containers = findAllContainers(projectName)
        def networks = findAllNetworks(projectName)

        if (containers || networks) {
            def message = "Resource leaks detected for project '${projectName}':\n"
            if (containers) {
                message += "  Containers: ${containers.join(', ')}\n"
            }
            if (networks) {
                message += "  Networks: ${networks.join(', ')}\n"
            }
            throw new AssertionError(message)
        }
    }

    /**
     * Force cleanup of all resources for a project
     */
    static void forceCleanup(String projectName) {
        // Remove containers
        def containers = findAllContainers(projectName)
        containers.each { containerName ->
            ['docker', 'rm', '-f', containerName].execute().waitFor()
        }

        // Remove networks
        def networks = findAllNetworks(projectName)
        networks.each { networkName ->
            ['docker', 'network', 'rm', networkName].execute().waitFor()
        }
    }
}
```

#### File: `buildSrc/src/main/groovy/com/kineticfire/test/StateFileValidator.groovy`

```groovy
package com.kineticfire.test

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

/**
 * Validator for Docker Compose state files
 * Parses and validates state JSON generated by dockerOrch tasks
 */
@CompileStatic
class StateFileValidator {

    /**
     * Parse state file JSON
     */
    static Map parseStateFile(File stateFile) {
        if (!stateFile.exists()) {
            throw new FileNotFoundException("State file not found: ${stateFile.absolutePath}")
        }

        def slurper = new JsonSlurper()
        return slurper.parse(stateFile) as Map
    }

    /**
     * Validate state file has required structure
     */
    static void assertValidStructure(Map state, String expectedStackName, String expectedProjectName) {
        assert state.stackName == expectedStackName,
            "Expected stackName '${expectedStackName}', got '${state.stackName}'"

        assert state.projectName == expectedProjectName,
            "Expected projectName '${expectedProjectName}', got '${state.projectName}'"

        assert state.lifecycle != null, "Missing 'lifecycle' field"
        assert state.timestamp != null, "Missing 'timestamp' field"
        assert state.services != null, "Missing 'services' field"
        assert state.services instanceof Map, "'services' should be a map"
    }

    /**
     * Get published (host) port for a service's container port
     */
    static int getPublishedPort(Map state, String serviceName, int containerPort) {
        def service = state.services[serviceName] as Map
        if (!service) {
            throw new IllegalArgumentException("Service '${serviceName}' not found in state file")
        }

        def ports = service.publishedPorts as List<Map>
        def portMapping = ports.find { (it.container as Integer) == containerPort }

        if (!portMapping) {
            throw new IllegalArgumentException(
                "Container port ${containerPort} not found for service '${serviceName}'"
            )
        }

        return portMapping.host as Integer
    }

    /**
     * Get container ID for a service
     */
    static String getContainerId(Map state, String serviceName) {
        def service = state.services[serviceName] as Map
        if (!service) {
            throw new IllegalArgumentException("Service '${serviceName}' not found in state file")
        }
        return service.containerId as String
    }

    /**
     * Get container name for a service
     */
    static String getContainerName(Map state, String serviceName) {
        def service = state.services[serviceName] as Map
        if (!service) {
            throw new IllegalArgumentException("Service '${serviceName}' not found in state file")
        }
        return service.containerName as String
    }

    /**
     * Get all service info
     */
    static Map getServiceInfo(Map state, String serviceName) {
        def service = state.services[serviceName] as Map
        if (!service) {
            throw new IllegalArgumentException("Service '${serviceName}' not found in state file")
        }
        return service
    }

    /**
     * Get all service names
     */
    static List<String> getServiceNames(Map state) {
        return (state.services as Map).keySet().toList()
    }
}
```

#### File: `buildSrc/src/main/groovy/com/kineticfire/test/HttpValidator.groovy`

```groovy
package com.kineticfire.test

import groovy.transform.CompileStatic

/**
 * Validator for HTTP operations
 * Used to verify web services are responding correctly
 */
@CompileStatic
class HttpValidator {

    /**
     * Make HTTP GET request and return response code
     */
    static int getResponseCode(String urlString, int timeoutMs = 5000) {
        def url = new URL(urlString)
        def connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.requestMethod = 'GET'

        try {
            connection.connect()
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Make HTTP GET request and return response body
     */
    static String getResponseBody(String urlString, int timeoutMs = 5000) {
        def url = new URL(urlString)
        def connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.requestMethod = 'GET'

        try {
            connection.connect()
            if (connection.responseCode == 200) {
                return connection.inputStream.text
            } else {
                return connection.errorStream?.text ?: "HTTP ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Check if HTTP service is responding with 200 OK
     */
    static boolean isServiceResponding(String host, int port, String path = '/', int timeoutMs = 5000) {
        try {
            def url = "http://${host}:${port}${path}"
            def responseCode = getResponseCode(url, timeoutMs)
            return responseCode == 200
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Wait for HTTP service to respond with 200 OK
     */
    static boolean waitForService(String host, int port, String path = '/',
                                   int maxWaitSeconds = 30, int pollIntervalMs = 500) {
        def endTime = System.currentTimeMillis() + (maxWaitSeconds * 1000)

        while (System.currentTimeMillis() < endTime) {
            if (isServiceResponding(host, port, path, 2000)) {
                return true
            }
            Thread.sleep(pollIntervalMs)
        }

        return false
    }

    /**
     * Make HTTP POST request
     */
    static String post(String urlString, String body, String contentType = 'application/json',
                       int timeoutMs = 5000) {
        def url = new URL(urlString)
        def connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.requestMethod = 'POST'
        connection.doOutput = true
        connection.setRequestProperty('Content-Type', contentType)

        try {
            connection.outputStream.withWriter { writer ->
                writer.write(body)
            }

            if (connection.responseCode >= 200 && connection.responseCode < 300) {
                return connection.inputStream.text
            } else {
                throw new IOException("HTTP ${connection.responseCode}: ${connection.errorStream?.text}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
```

#### File: `buildSrc/src/main/groovy/com/kineticfire/test/CleanupValidator.groovy`

```groovy
package com.kineticfire.test

import groovy.transform.CompileStatic

/**
 * Validator for cleanup operations
 * Ensures no Docker resources leak after tests
 */
@CompileStatic
class CleanupValidator {

    /**
     * Find lingering containers for a project
     */
    static List<String> findLingeringContainers(String projectName) {
        return DockerComposeValidator.findAllContainers(projectName)
    }

    /**
     * Find lingering networks for a project
     */
    static List<String> findLingeringNetworks(String projectName) {
        return DockerComposeValidator.findAllNetworks(projectName)
    }

    /**
     * Assert no resource leaks
     */
    static void assertNoLeaks(String projectName) {
        DockerComposeValidator.assertNoLeaks(projectName)
    }

    /**
     * Force cleanup of all resources
     */
    static void forceCleanup(String projectName) {
        DockerComposeValidator.forceCleanup(projectName)
    }

    /**
     * Verify cleanup after test
     */
    static void verifyCleanup(String projectName, boolean forceCleanupOnFailure = true) {
        try {
            assertNoLeaks(projectName)
        } catch (AssertionError e) {
            if (forceCleanupOnFailure) {
                System.err.println("Cleanup verification failed, forcing cleanup...")
                forceCleanup(projectName)
            }
            throw e
        }
    }
}
```

---

### Task 2: Update dockerOrch README with Scenario Matrix (1 hour)

**Location**: `plugin-integration-test/dockerOrch/README.md`

**Content**:

```markdown
# Integration Test Coverage - 'dockerOrch' Task

This document tracks integration test scenarios for the `dockerOrch` task.

## Test Philosophy

These integration tests:
1. **Mirror real-world usage**: Build app → Build image → Orchestrate → Test → Teardown
2. **Double as examples**: Users can copy scenarios as starting points
3. **Use real Docker**: No mocks, actual containers
4. **Validate Phase 1 work**: End-to-end testing of wait, logs, state files
5. **Are Gradle 9/10 compatible**: Build cache, configuration cache, version catalogs

## Scenario Coverage Matrix

| Scenario | User Story | Image Source | Wait Config | Test Lang | Lifecycle | Logs | Status |
|----------|-----------|--------------|-------------|-----------|-----------|------|--------|
| 01 | Web app build + test | Built (docker DSL) | HEALTHY | Groovy | Suite | ❌ | TODO |
| 02 | App + database | Built + postgres | RUNNING + HEALTHY | Java | Suite | ❌ | TODO |
| 03 | Microservices | Multiple built | Mixed | Groovy | Suite | ❌ | TODO |
| 04 | Existing images | sourceRef only | RUNNING | Groovy | Suite | ❌ | TODO |
| 05 | Class lifecycle | Built | HEALTHY | Java | Class | ❌ | TODO |
| 06 | Method lifecycle | Built | HEALTHY | Java | Method | ❌ | TODO |
| 07 | Logs capture | Built | HEALTHY | Groovy | Suite | ✅ | TODO |

## Plugin Feature Coverage

| Feature | Tested By Scenarios | Notes |
|---------|---------------------|-------|
| `composeUp` task | All | Start containers |
| `composeDown` task | All | Stop and remove containers |
| `waitForHealthy` | 1, 2, 3, 5, 6, 7 | Wait for HEALTHY status |
| `waitForRunning` | 2, 4 | Wait for RUNNING status (no health check) |
| Mixed wait states | 2, 3 | Some HEALTHY, some RUNNING |
| `logs` capture | 7 | Automatic log capture on teardown |
| State file generation | All | JSON state in build/compose-state/ |
| State file consumption | All | Tests read state file for ports/IDs |
| JUnit class extension | 5 | `@ExtendWith(DockerComposeClassExtension)` |
| JUnit method extension | 6 | `@ExtendWith(DockerComposeMethodExtension)` |
| Integration with `docker` DSL | 1, 2, 3, 5, 6, 7 | Build image then orchestrate |
| Using existing images | 2 (postgres), 4 (all) | Reference public images |

## Gradle 9/10 Compatibility

All scenarios demonstrate proper Gradle 9/10 usage:
- ✅ Version catalogs (`gradle/libs.versions.toml`)
- ✅ Build cache enabled and working
- ✅ Configuration cache compatible
- ✅ Provider API usage (lazy configuration)
- ✅ Task configuration avoidance
- ✅ No deprecated APIs

## Running Scenarios

From any scenario root directory:

```bash
# Full workflow
./gradlew :app-image:runIntegrationTest

# Step by step
./gradlew :app:build                          # Build application JAR
./gradlew :app-image:dockerBuild<ImageName>   # Build Docker image
./gradlew :app-image:composeUp<StackName>     # Start compose stack
./gradlew :app-image:integrationTest          # Run integration tests
./gradlew :app-image:composeDown<StackName>   # Stop compose stack

# Cleanup if needed
./gradlew :app-image:cleanupContainers
docker ps -a | grep <project-name>            # Verify no leaks
```

## Validation Infrastructure

Common validators in `buildSrc/`:
- `DockerComposeValidator`: Container state validation
- `StateFileValidator`: Parse and validate state JSON
- `HttpValidator`: HTTP endpoint testing
- `CleanupValidator`: Verify no resource leaks

## Success Criteria

For each scenario:
- [ ] All files created as specified
- [ ] Build completes successfully with Gradle 9/10
- [ ] All integration tests pass
- [ ] Containers cleaned up automatically
- [ ] `docker ps -a` shows no containers after completion
- [ ] Can run scenario multiple times successfully
- [ ] Build cache works correctly
- [ ] Configuration cache works correctly
```

---

### Task 3: Test Validators Independently (1 hour)

Create simple test to verify validators work:

**Location**: `plugin-integration-test/buildSrc/src/test/groovy/com/kineticfire/test/ValidatorTest.groovy`

```groovy
package com.kineticfire.test

import spock.lang.Specification

class ValidatorTest extends Specification {

    def "DockerComposeValidator should find docker command"() {
        when: "we check if docker is available"
        def process = ['docker', '--version'].execute()
        process.waitFor()

        then: "docker command should exist"
        process.exitValue() == 0
    }

    def "StateFileValidator should parse valid JSON"() {
        given: "a temporary state file"
        def tempFile = File.createTempFile("state", ".json")
        tempFile.text = '''
        {
            "stackName": "test",
            "projectName": "test-project",
            "lifecycle": "suite",
            "timestamp": "2025-01-01T00:00:00Z",
            "services": {
                "web": {
                    "containerId": "abc123",
                    "containerName": "web-1",
                    "state": "running",
                    "publishedPorts": []
                }
            }
        }
        '''

        when: "we parse the file"
        def state = StateFileValidator.parseStateFile(tempFile)

        then: "state should be valid"
        state.stackName == 'test'
        state.services.web != null

        cleanup:
        tempFile.delete()
    }
}
```

---

### Task 4: Create Root Gradle Configuration (2 hours)

**Purpose**: Configure multi-project structure with aggregator tasks at multiple levels

**IMPORTANT**: All Gradle execution MUST be from `plugin-integration-test/` directory due to buildSrc

#### File: `plugin-integration-test/settings.gradle` (Root - already exists)

**NOTE**: The root settings.gradle already includes dockerOrch scenarios. You'll need to add these lines:

```groovy
// Add to existing file after docker scenarios:

// DockerOrch integration tests
include 'dockerOrch'
include 'dockerOrch:scenario-1'
include 'dockerOrch:scenario-2'
include 'dockerOrch:scenario-3'
include 'dockerOrch:scenario-4'
include 'dockerOrch:scenario-5'
include 'dockerOrch:scenario-6'
include 'dockerOrch:scenario-7'
```

**NOTE**: Each scenario also has its own `settings.gradle` (see below).

---

#### File: `plugin-integration-test/dockerOrch/scenario-1/settings.gradle`

**Pattern**: Each scenario is a standalone Gradle project with subprojects

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ... (standard header)
 */

pluginManagement {
    plugins {
        id 'com.kineticfire.gradle.gradle-docker' version "${providers.gradleProperty('plugin_version').get()}"
    }
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = 'scenario-1'

// Subprojects for this scenario
include 'app'
include 'app-image'

// Build cache configuration (Gradle 9/10)
buildCache {
    local {
        enabled = true
        directory = file('build-cache')
        removeUnusedEntriesAfterDays = 7
    }
}
```

**Repeat for all scenarios** (scenario-2 through scenario-7), adjusting:
- `rootProject.name` to match scenario number
- Scenario 4 only includes `'app-image'` (no app subproject)

---

#### File: `plugin-integration-test/dockerOrch/scenario-1/gradle.properties`

```properties
# Gradle 9/10 Performance Features
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.daemon=true

# JVM settings
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m

# Plugin version (passed from parent build)
plugin_version=1.0.0-SNAPSHOT
```

**Repeat for all scenarios** with same content.

---

#### Create Symlinks for Each Scenario

For each scenario, create symlinks to share resources:

```bash
# From plugin-integration-test/dockerOrch/ directory
cd scenario-1
ln -s ../gradle gradle
ln -s ../gradlew gradlew
cd ..

# Repeat for scenarios 2-7
for i in {2..7}; do
  cd scenario-$i
  ln -s ../gradle gradle
  ln -s ../gradlew gradlew
  cd ..
done
```

This follows the exact pattern used by `docker/` scenarios.

---

#### File: `plugin-integration-test/build.gradle`

```groovy
// Root build file - defines aggregator tasks for all integration tests

plugins {
    id 'base'
}

// ========================================
// Task: cleanAll
// Clean all integration tests
// ========================================
tasks.register('cleanAll') {
    group = 'verification'
    description = 'Clean all integration tests (docker + dockerOrch)'

    // Clean all subprojects
    dependsOn subprojects.collect { it.tasks.matching { it.name == 'clean' } }
}

// ========================================
// Task: integrationTest
// Run ALL integration tests (docker + dockerOrch)
// ========================================
tasks.register('integrationTest') {
    group = 'verification'
    description = 'Run ALL integration tests (docker + dockerOrch)'

    dependsOn ':docker:integrationTest'
    dependsOn ':dockerOrch:integrationTest'

    doLast {
        logger.lifecycle("✅ All integration tests complete")
    }
}

// ========================================
// Task: dockerIntegrationTest
// Run only docker integration tests
// ========================================
tasks.register('dockerIntegrationTest') {
    group = 'verification'
    description = 'Run docker integration tests only'

    dependsOn ':docker:integrationTest'
}

// ========================================
// Task: dockerOrchIntegrationTest
// Run only dockerOrch integration tests
// ========================================
tasks.register('dockerOrchIntegrationTest') {
    group = 'verification'
    description = 'Run dockerOrch integration tests only'

    dependsOn ':dockerOrch:integrationTest'
}
```

---

#### File: `plugin-integration-test/dockerOrch/gradle/libs.versions.toml`

**Shared version catalog for all dockerOrch scenarios** (symlinked into each scenario):

```toml
[versions]
groovy = "4.0.23"
spock = "2.3-groovy-4.0"
spring-boot = "3.2.0"
spring-dependency-management = "1.1.4"
junit-platform = "1.10.1"
postgres = "42.7.1"

[libraries]
# Testing
groovy-all = { module = "org.apache.groovy:groovy-all", version.ref = "groovy" }
spock-core = { module = "org.spockframework:spock-core", version.ref = "spock" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junit-platform" }

# Spring Boot
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgres" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
kineticfire-docker = { id = "com.kineticfire.gradle.docker", version = "1.0.0-SNAPSHOT" }
```

#### File: `plugin-integration-test/dockerOrch/build.gradle`

```groovy
// DockerOrch aggregator - runs all dockerOrch scenarios

plugins {
    id 'base'
}

// ========================================
// Task: integrationTest
// Run all dockerOrch integration tests
// ========================================
tasks.register('integrationTest') {
    group = 'verification'
    description = 'Run all dockerOrch integration tests'

    // Depend on each scenario's integrationTest task
    dependsOn ':dockerOrch:scenario-1:integrationTest'
    dependsOn ':dockerOrch:scenario-2:integrationTest'
    dependsOn ':dockerOrch:scenario-3:integrationTest'
    dependsOn ':dockerOrch:scenario-4:integrationTest'
    dependsOn ':dockerOrch:scenario-5:integrationTest'
    dependsOn ':dockerOrch:scenario-6:integrationTest'
    dependsOn ':dockerOrch:scenario-7:integrationTest'

    doLast {
        logger.lifecycle("✅ All dockerOrch scenarios complete")
    }
}

// ========================================
// Task: clean
// Clean all dockerOrch scenarios
// ========================================
tasks.register('clean') {
    group = 'verification'
    description = 'Clean all dockerOrch scenarios'

    dependsOn ':dockerOrch:scenario-1:clean'
    dependsOn ':dockerOrch:scenario-2:clean'
    dependsOn ':dockerOrch:scenario-3:clean'
    dependsOn ':dockerOrch:scenario-4:clean'
    dependsOn ':dockerOrch:scenario-5:clean'
    dependsOn ':dockerOrch:scenario-6:clean'
    dependsOn ':dockerOrch:scenario-7:clean'
}
```

#### File: `plugin-integration-test/dockerOrch/scenario-1/build.gradle`

**Example scenario aggregator** (coordinates app + app-image subprojects):

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ... (standard header)
 */

// Scenario 1 aggregator - coordinates app and app-image subprojects

plugins {
    id 'base'
}

// ========================================
// Safety Check: Ensure run from top-level
// ========================================
if (rootProject.name != 'plugin-integration-test') {
    tasks.register('integrationTest') {
        description = 'Integration test must be run from top-level directory'
        group = 'verification'
        doFirst {
            throw new GradleException("""
❌ DockerOrch scenario-1 integration test must be run from the top-level directory.

✅ SOLUTION:
   cd ${file('../..').canonicalPath}
   ./gradlew dockerOrch:scenario-1:integrationTest

ℹ️  This ensures access to the shared buildSrc functionality.
""")
        }
    }
    return
}

// ========================================
// Task: integrationTest
// Run this scenario's integration test
// ========================================
tasks.register('integrationTest') {
    group = 'verification'
    description = 'Run scenario-1 integration test'

    // Depends on the full workflow in app-image
    dependsOn ':dockerOrch:scenario-1:app-image:runIntegrationTest'

    doLast {
        logger.lifecycle("✅ Scenario 1 integration test complete")
    }
}

// ========================================
// Task: clean
// Clean this scenario
// ========================================
tasks.register('clean') {
    group = 'verification'
    description = 'Clean scenario-1'

    dependsOn ':dockerOrch:scenario-1:app:clean'
    dependsOn ':dockerOrch:scenario-1:app-image:clean'
}
```

**Repeat for all scenarios** with appropriate scenario number adjustments.

---

## Supported Task Execution Patterns

**All commands MUST be run from `plugin-integration-test/` directory** (buildSrc requirement):

```bash
cd plugin-integration-test

# ========================================
# Clean Tasks
# ========================================

# Clean everything (docker + dockerOrch)
./gradlew cleanAll

# Clean only docker scenarios
./gradlew docker:clean

# Clean only dockerOrch scenarios
./gradlew dockerOrch:clean

# Clean single docker scenario
./gradlew docker:scenario-1:clean

# Clean single dockerOrch scenario
./gradlew dockerOrch:scenario-1:clean

# ========================================
# Integration Test Tasks
# ========================================

# Run ALL integration tests (docker + dockerOrch)
./gradlew -Pplugin_version=1.0.0-SNAPSHOT integrationTest

# Run only docker integration tests
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerIntegrationTest

# Run only dockerOrch integration tests
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrchIntegrationTest

# Run single docker scenario
./gradlew -Pplugin_version=1.0.0-SNAPSHOT docker:scenario-1:integrationTest

# Run single dockerOrch scenario (runs full workflow)
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:scenario-1:integrationTest

# ========================================
# Combined Clean + Test
# ========================================

# Clean and run single docker scenario
./gradlew -Pplugin_version=1.0.0-SNAPSHOT docker:scenario-1:clean docker:scenario-1:integrationTest

# Clean and run single dockerOrch scenario
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:scenario-1:clean dockerOrch:scenario-1:integrationTest

# ========================================
# Step-by-Step Execution (for debugging)
# ========================================

# For dockerOrch scenario 1:
./gradlew dockerOrch:scenario-1:app:build
./gradlew dockerOrch:scenario-1:app-image:dockerBuildWebApp
./gradlew dockerOrch:scenario-1:app-image:composeUpWebAppTest
./gradlew dockerOrch:scenario-1:app-image:integrationTest
./gradlew dockerOrch:scenario-1:app-image:composeDownWebAppTest
```

---

## Phase 2B: Core Scenarios (Estimated: 12 hours)

### Scenario 1: Web App Build + Healthy Wait (Suite, Groovy) - 6 hours

**User Story**: "I built a REST API with a health endpoint. I want to build the image and run integration tests."

**Demonstrates**:
- Building JAR with Gradle
- Building Docker image from JAR
- `waitForHealthy` configuration
- Suite lifecycle (fastest)
- State file consumption
- Groovy/Spock tests

#### Directory Structure

```
scenario-1/
├─ settings.gradle                 # (see Task 4)
├─ gradle.properties               # (see Task 4)
├─ gradle -> ../gradle             # SYMLINK to dockerOrch/gradle/
├─ gradlew -> ../gradlew           # SYMLINK to dockerOrch/gradlew
├─ app/
│  ├─ build.gradle
│  └─ src/
│     ├─ main/java/com/kineticfire/test/
│     │  ├─ Application.java
│     │  └─ HealthController.java
│     └─ test/java/com/kineticfire/test/
│        └─ ApplicationTest.java
└─ app-image/
   ├─ build.gradle
   └─ src/
      ├─ main/docker/
      │  ├─ Dockerfile
      │  ├─ entrypoint.sh
      │  └─ .dockerignore
      └─ integrationTest/
         ├─ groovy/com/kineticfire/test/
         │  └─ WebAppIT.groovy
         └─ resources/
            └─ compose/
               └─ web-app.yml
```

**NOTE**: `settings.gradle`, `gradle.properties`, and `gradle/libs.versions.toml` are defined in Task 4 above.

#### File: `app/build.gradle`

```groovy
plugins {
    id 'java'
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = 'com.kineticfire.test'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation libs.spring.boot.starter.web
    testImplementation libs.spring.boot.starter.test
}

tasks.named('test') {
    useJUnitPlatform()
}

// Create executable JAR
tasks.named('bootJar') {
    archiveBaseName.set('web-app')
    archiveVersion.set(version)
}
```

#### File: `app/src/main/java/com/kineticfire/test/Application.java`

```java
package com.kineticfire.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

#### File: `app/src/main/java/com/kineticfire/test/HealthController.java`

```java
package com.kineticfire.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        );
    }

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
            "message", "Web App is running",
            "version", "1.0.0"
        );
    }
}
```

#### File: `app/src/test/java/com/kineticfire/test/ApplicationTest.java`

```java
package com.kineticfire.test;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTest {

    @Test
    void contextLoads() {
        // Verify Spring context loads
    }
}
```

#### File: `app-image/build.gradle`

```groovy
plugins {
    id 'groovy'
    alias(libs.plugins.kineticfire.docker)
}

repositories {
    mavenLocal()  // Plugin must be in Maven local
    mavenCentral()
}

dependencies {
    // Testing dependencies from version catalog
    testImplementation libs.groovy.all
    testImplementation libs.spock.core
    testRuntimeOnly libs.junit.platform.launcher
}

// Get JAR file from app subproject using Provider API (Gradle 9/10)
def jarFileProvider = project(':app').tasks.named('bootJar').flatMap { it.archiveFile }
def jarFileNameProvider = jarFileProvider.map { it.asFile.name }

// Configure docker DSL
docker {
    images {
        webApp {
            imageName = 'web-app-test'
            tags = ['latest', '1.0.0']

            // Build context
            buildDirectory = file('src/main/docker')

            // Pass JAR file name as build arg (lazy)
            buildArgs.put('JAR_FILE', jarFileNameProvider)

            // Ensure JAR is built first
            dependsOn project(':app').tasks.named('bootJar')

            // Copy JAR into build context before building
            doFirst {
                copy {
                    from jarFileProvider
                    into file('src/main/docker')
                }
            }
        }
    }
}

// Configure dockerOrch DSL
dockerOrch {
    composeStacks {
        webAppTest {
            files.from('src/integrationTest/resources/compose/web-app.yml')
            projectName = "web-app-test-${project.name}"

            // Wait for app to be healthy
            waitForHealthy {
                services.set(['web-app'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}

// Create integration test source set
sourceSets {
    integrationTest {
        groovy {
            srcDir 'src/integrationTest/groovy'
        }
        resources {
            srcDir 'src/integrationTest/resources'
        }
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
    }
}

configurations {
    integrationTestImplementation.extendsFrom testImplementation
    integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
}

// Register integration test task (Gradle 9/10 style)
tasks.register('integrationTest', Test) {
    description = 'Runs integration tests against Docker Compose stack'
    group = 'verification'

    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath

    useJUnitPlatform()

    // Must run after compose up
    mustRunAfter tasks.named('composeUpWebAppTest')

    // Pass state file location to tests (lazy)
    systemProperty 'COMPOSE_STATE_FILE',
        layout.buildDirectory.file('compose-state/webAppTest-state.json').get().asFile.absolutePath

    // Pass project name for validators
    systemProperty 'COMPOSE_PROJECT_NAME', "web-app-test-${project.name}"

    // Not cacheable - interacts with Docker
    outputs.cacheIf { false }
}

// Full integration test workflow
tasks.register('runIntegrationTest') {
    description = 'Full integration test: build image -> up -> test -> down'
    group = 'verification'

    // Build image first (lazy)
    dependsOn tasks.named('dockerBuildWebApp')

    // Then compose up
    dependsOn tasks.named('composeUpWebAppTest')

    // Then run tests
    dependsOn tasks.named('integrationTest')

    // Always tear down
    finalizedBy tasks.named('composeDownWebAppTest')

    doLast {
        logger.lifecycle("Integration test complete for scenario-01")
    }
}

// Cleanup task
tasks.register('cleanupContainers') {
    description = 'Force cleanup of any lingering containers'
    group = 'verification'

    doLast {
        def projectName = "web-app-test-${project.name}"
        def result = exec {
            commandLine 'docker', 'ps', '-a', '-q', '--filter', "name=${projectName}"
            standardOutput = new ByteArrayOutputStream()
            ignoreExitValue = true
        }

        def containerIds = result.standardOutput.toString().trim()
        if (containerIds) {
            exec {
                commandLine 'docker', 'rm', '-f'
                args containerIds.split('\n')
            }
        }
    }
}
```

#### File: `app-image/src/main/docker/Dockerfile`

```dockerfile
# Multi-stage build for efficient image
FROM eclipse-temurin:17-jre-alpine

# Create app user
RUN addgroup -S app && adduser -S app -G app

# Set working directory
WORKDIR /app

# Copy JAR (passed as build arg from Gradle)
ARG JAR_FILE
COPY ${JAR_FILE} app.jar

# Copy entrypoint script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Change ownership
RUN chown -R app:app /app

# Switch to app user
USER app

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]
```

#### File: `app-image/src/main/docker/entrypoint.sh`

```bash
#!/bin/sh
set -e

echo "Starting Web App..."
echo "Java Version:"
java -version

exec java -jar /app/app.jar
```

#### File: `app-image/src/main/docker/.dockerignore`

```
# Ignore everything except what we explicitly copy
*
!*.jar
!entrypoint.sh
```

#### File: `app-image/src/integrationTest/resources/compose/web-app.yml`

```yaml
# Docker Compose file for integration testing
# NOTE: No 'version' field - deprecated in Docker Compose

services:
  web-app:
    image: web-app-test:latest
    ports:
      - "9081:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=test
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 5
      start_period: 10s
```

#### File: `app-image/src/integrationTest/groovy/com/kineticfire/test/WebAppIT.groovy`

```groovy
package com.kineticfire.test

import com.kineticfire.test.DockerComposeValidator
import com.kineticfire.test.StateFileValidator
import com.kineticfire.test.HttpValidator
import com.kineticfire.test.CleanupValidator
import spock.lang.Specification
import groovy.json.JsonSlurper

/**
 * Integration test for Scenario 1: Web App Build + Healthy Wait
 *
 * Validates:
 * - Image built from app JAR
 * - Container starts and becomes healthy
 * - State file generated correctly
 * - HTTP endpoints accessible
 */
class WebAppIT extends Specification {

    static String projectName
    static Map stateData
    static int hostPort

    def setupSpec() {
        // Read system properties set by Gradle
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Scenario 1: Web App Integration Test ==="
        println "Project Name: ${projectName}"
        println "State File: ${stateFilePath}"

        // Parse state file
        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)

        // Get published port for web-app service
        hostPort = StateFileValidator.getPublishedPort(stateData, 'web-app', 8080)

        println "Testing against web-app on localhost:${hostPort}"
    }

    def cleanupSpec() {
        // Verify no resource leaks after all tests
        // Note: composeDown should have already cleaned up
        println "=== Verifying cleanup for project: ${projectName} ==="
    }

    def "state file should have valid structure"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateData, 'webAppTest', projectName)

        and: "web-app service is present"
        def serviceNames = StateFileValidator.getServiceNames(stateData)
        serviceNames.contains('web-app')

        and: "web-app has port mapping"
        def serviceInfo = StateFileValidator.getServiceInfo(stateData, 'web-app')
        serviceInfo.publishedPorts.size() > 0
    }

    def "web-app container should be running"() {
        expect: "container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'web-app')
    }

    def "web-app container should be healthy"() {
        expect: "container reports healthy status"
        DockerComposeValidator.isContainerHealthy(projectName, 'web-app')
    }

    def "web-app should respond to health check"() {
        when: "we call the health endpoint"
        def response = HttpValidator.getResponseBody("http://localhost:${hostPort}/health")
        def json = new JsonSlurper().parseText(response)

        then: "response indicates app is UP"
        json.status == 'UP'
        json.timestamp != null
    }

    def "web-app should respond to root endpoint"() {
        when: "we call the root endpoint"
        def responseCode = HttpValidator.getResponseCode("http://localhost:${hostPort}/")

        then: "we get 200 OK"
        responseCode == 200

        when: "we get the response body"
        def response = HttpValidator.getResponseBody("http://localhost:${hostPort}/")
        def json = new JsonSlurper().parseText(response)

        then: "response contains expected message"
        json.message == 'Web App is running'
        json.version == '1.0.0'
    }

    def "web-app should handle multiple concurrent requests"() {
        when: "we make 10 concurrent requests"
        def futures = (1..10).collect {
            Thread.start {
                HttpValidator.getResponseCode("http://localhost:${hostPort}/health")
            }
        }
        def results = futures.collect { it.join(); it }

        then: "all requests should succeed with 200"
        results.every { it == 200 }
    }
}
```

---

### Scenario 4: Existing Images Only (Suite, Groovy) - 3 hours

**User Story**: "I want to test against official images without building anything"

**Demonstrates**:
- No image building required
- Using existing images (sourceRef pattern)
- `waitForRunning` (standard images don't have custom health checks)
- Quick setup for standard infrastructure testing

#### Directory Structure

```
scenario-04-existing-images/
├─ settings.gradle
├─ gradle.properties
├─ gradle/
│  └─ libs.versions.toml
└─ app-image/                              # No 'app/' directory - no building
   ├─ build.gradle                         # Only dockerOrch, no docker DSL
   └─ src/
      └─ integrationTest/
         ├─ groovy/com/kineticfire/test/
         │  └─ ExistingImagesIT.groovy
         └─ resources/
            └─ compose/
               └─ existing-images.yml
```

#### File: `app-image/build.gradle`

```groovy
plugins {
    id 'groovy'
    alias(libs.plugins.kineticfire.docker)
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation libs.groovy.all
    testImplementation libs.spock.core
    testRuntimeOnly libs.junit.platform.launcher
}

// Configure dockerOrch DSL (NO docker DSL - no building)
dockerOrch {
    composeStacks {
        existingTest {
            files.from('src/integrationTest/resources/compose/existing-images.yml')
            projectName = "existing-test-${project.name}"

            // Wait for services to be RUNNING (no health checks)
            waitForRunning {
                services.set(['nginx', 'redis'])
                timeoutSeconds.set(30)
                pollSeconds.set(2)
            }
        }
    }
}

// Integration test source set
sourceSets {
    integrationTest {
        groovy.srcDir 'src/integrationTest/groovy'
        resources.srcDir 'src/integrationTest/resources'
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
    }
}

configurations {
    integrationTestImplementation.extendsFrom testImplementation
    integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
}

// Integration test task
tasks.register('integrationTest', Test) {
    description = 'Runs integration tests against existing images'
    group = 'verification'

    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath

    useJUnitPlatform()
    mustRunAfter tasks.named('composeUpExistingTest')

    systemProperty 'COMPOSE_STATE_FILE',
        layout.buildDirectory.file('compose-state/existingTest-state.json').get().asFile.absolutePath
    systemProperty 'COMPOSE_PROJECT_NAME', "existing-test-${project.name}"

    outputs.cacheIf { false }
}

// Full workflow (no build step)
tasks.register('runIntegrationTest') {
    description = 'Full integration test: up -> test -> down'
    group = 'verification'

    dependsOn tasks.named('composeUpExistingTest')
    dependsOn tasks.named('integrationTest')
    finalizedBy tasks.named('composeDownExistingTest')
}
```

#### File: `app-image/src/integrationTest/resources/compose/existing-images.yml`

```yaml
# Test with existing public images only
services:
  nginx:
    image: nginx:alpine
    ports:
      - "9084:80"
    # No custom health check - wait for RUNNING

  redis:
    image: redis:alpine
    ports:
      - "9379:6379"
    # No custom health check - wait for RUNNING
```

#### File: `app-image/src/integrationTest/groovy/com/kineticfire/test/ExistingImagesIT.groovy`

```groovy
package com.kineticfire.test

import com.kineticfire.test.DockerComposeValidator
import com.kineticfire.test.StateFileValidator
import com.kineticfire.test.HttpValidator
import spock.lang.Specification

/**
 * Integration test for Scenario 4: Existing Images Only
 *
 * Validates:
 * - Using existing public images (no building)
 * - Wait for RUNNING status
 * - Standard services accessible
 */
class ExistingImagesIT extends Specification {

    static String projectName
    static Map stateData
    static int nginxPort
    static int redisPort

    def setupSpec() {
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Scenario 4: Existing Images Test ==="

        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)

        nginxPort = StateFileValidator.getPublishedPort(stateData, 'nginx', 80)
        redisPort = StateFileValidator.getPublishedPort(stateData, 'redis', 6379)

        println "Nginx on localhost:${nginxPort}"
        println "Redis on localhost:${redisPort}"
    }

    def "both containers should be running"() {
        expect: "nginx is running"
        DockerComposeValidator.isContainerRunning(projectName, 'nginx')

        and: "redis is running"
        DockerComposeValidator.isContainerRunning(projectName, 'redis')
    }

    def "nginx should serve default page"() {
        when: "we request the nginx default page"
        def responseCode = HttpValidator.getResponseCode("http://localhost:${nginxPort}/")

        then: "we get 200 OK"
        responseCode == 200

        when: "we get the response body"
        def body = HttpValidator.getResponseBody("http://localhost:${nginxPort}/")

        then: "it contains nginx welcome message"
        body.contains('Welcome to nginx')
    }

    def "redis should accept connections"() {
        when: "we connect to redis and send PING"
        def socket = new Socket('localhost', redisPort)
        socket.soTimeout = 5000

        def writer = new PrintWriter(socket.outputStream, true)
        def reader = new BufferedReader(new InputStreamReader(socket.inputStream))

        // Redis protocol: *1\r\n$4\r\nPING\r\n
        writer.print("*1\r\n\$4\r\nPING\r\n")
        writer.flush()

        // Read response
        def response = reader.readLine()

        socket.close()

        then: "redis responds with PONG"
        response == '+PONG'
    }
}
```

---

### Scenario 7: Logs Capture (Suite, Groovy) - 3 hours

**User Story**: "When tests fail, I need container logs for debugging"

**Demonstrates**:
- `logs` configuration in dockerOrch
- Automatic log capture before teardown
- Log file generation and content validation

#### Key Addition to `app-image/build.gradle`:

```groovy
dockerOrch {
    composeStacks {
        logsTest {
            files.from('src/integrationTest/resources/compose/logs-test.yml')
            projectName = "logs-test-${project.name}"

            waitForHealthy {
                services.set(['app'])
                timeoutSeconds.set(60)
            }

            // Configure log capture
            logs {
                writeTo.set(layout.buildDirectory.file('compose-logs/logsTest.log'))
                tailLines.set(200)
                services.set(['app'])  // Capture only app logs
            }
        }
    }
}
```

#### Integration Test Addition:

```groovy
def "logs should be captured to file"() {
    given: "the log file location"
    def logFile = new File(project.buildDir, 'compose-logs/logsTest.log')

    expect: "log file should exist after teardown"
    // This test runs before teardown, so we manually trigger log capture
    // In real usage, logs are captured automatically by composeDown
    logFile.exists()

    and: "log file should contain app output"
    def logs = logFile.text
    logs.contains('Starting Web App')
    logs.contains('Started Application')
}
```

---

## Phase 2C: Advanced Scenarios (Estimated: 10 hours)

### Scenario 2: App + Database Mixed Wait (3 hours)
### Scenario 5: Class Lifecycle (3 hours)
### Scenario 6: Method Lifecycle (3 hours)
### Scenario 3: Microservices (4 hours)

(Detailed specifications available - abbreviated here for length)

---

## Implementation Order

| Phase | Scenario | Effort | Features Validated |
|-------|----------|--------|-------------------|
| **2A** | Infrastructure | 4 hours | buildSrc validators, README |
| **2B** | Scenario 1 | 6 hours | Build, healthy wait, state file, Groovy |
| **2B** | Scenario 4 | 3 hours | Existing images, running wait |
| **2B** | Scenario 7 | 3 hours | Logs capture |
| **2C** | Scenario 2 | 3 hours | Mixed wait, database, Java |
| **2C** | Scenario 5 | 3 hours | Class lifecycle, Java |
| **2C** | Scenario 6 | 3 hours | Method lifecycle, Java |
| **2C** | Scenario 3 | 4 hours | Microservices, complex |
| **Total** | | **26 hours** | **All Phase 1 features** |

---

## How to Run

### Prerequisite: Build and Publish Plugin

```bash
# From plugin/ directory
cd plugin
./gradlew -Pplugin_version=1.0.0-SNAPSHOT clean build publishToMavenLocal
cd ..
```

### Run a Single Scenario

```bash
# From scenario root (e.g., dockerOrch/scenario-01-web-app-build-healthy/)
cd plugin-integration-test/dockerOrch/scenario-01-web-app-build-healthy

# Full workflow
./gradlew :app-image:runIntegrationTest

# Step by step for debugging
./gradlew :app:build
./gradlew :app-image:dockerBuildWebApp
./gradlew :app-image:composeUpWebAppTest
./gradlew :app-image:integrationTest
./gradlew :app-image:composeDownWebAppTest

# Verify cleanup
docker ps -a | grep web-app-test
```

### Run All Scenarios

```bash
# From plugin-integration-test/
cd plugin-integration-test

# Run all dockerOrch scenarios
./gradlew -Pplugin_version=1.0.0-SNAPSHOT cleanAll dockerOrchIntegrationTest
```

---

## Success Criteria

For each scenario:
- [ ] All files created as specified
- [ ] Builds successfully with Gradle 9/10
- [ ] Build cache works correctly
- [ ] Configuration cache works correctly
- [ ] All integration tests pass
- [ ] Containers start and become ready
- [ ] State file generated with correct content
- [ ] Tests can read and use state file
- [ ] Containers cleaned up automatically
- [ ] `docker ps -a` shows no containers after completion
- [ ] Can run scenario multiple times successfully
- [ ] No warnings about deprecated Gradle features

---

## Gradle 9/10 Checklist

- [ ] Version catalogs used for all dependencies
- [ ] Build cache enabled in settings.gradle
- [ ] Configuration cache enabled in gradle.properties
- [ ] Provider API used for all lazy values
- [ ] `tasks.register()` and `tasks.named()` used exclusively
- [ ] No `Project` objects captured in task actions
- [ ] All task inputs/outputs declared properly
- [ ] No deprecated APIs used
- [ ] No `version` field in Docker Compose files
- [ ] Modern plugin application syntax (`alias(libs.plugins.*)`)

---

## References

- Phase 1 Plan: `/docs/design-docs/todo/2025-10-10-enhance-docker-orch.md`
- Gradle 9/10 Compatibility: `/docs/design-docs/gradle-9-and-10-compatibility.md`
- Usage Practices: `/docs/usage/gradle-9-and-10-compatibility-practices.md`
- Docker Integration Test Pattern: `/plugin-integration-test/docker/README.md`
- User Documentation: `/docs/usage/usage-docker-orch.md`
