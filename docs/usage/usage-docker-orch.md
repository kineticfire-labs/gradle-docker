# 'dockerOrch' DSL Usage Guide

This document provides examples of how to use Docker Compose orchestration in your integration tests with the
'gradle-docker' plugin.

## Prerequisites

First, add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'com.kineticfire.gradle.docker' version '1.0.0'
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

The `dockerOrch` DSL provides two ways to orchestrate Docker Compose in your integration tests:

### 1. Test Framework Extensions (RECOMMENDED)

Use **Spock or JUnit 5 extensions** to automatically manage container lifecycles within your test classes. This is the
**primary and recommended approach** for most integration testing scenarios.

**Advantages:**
- Automatic container management (no manual Gradle task dependencies)
- Supports both CLASS and METHOD lifecycles
- Clean test code with minimal boilerplate
- Test framework handles setup/teardown timing

**Available Extensions:**
- **Spock**: `@ComposeUp` annotation with `LifecycleMode.CLASS` or `LifecycleMode.METHOD`
- **JUnit 5**: `@ExtendWith(DockerComposeClassExtension.class)` or `@ExtendWith(DockerComposeMethodExtension.class)`

### 2. Gradle Tasks (OPTIONAL)

Use **Gradle tasks** (`composeUp*` / `composeDown*`) for suite-level orchestration or when you need manual control.

**Use Cases:**
- Suite lifecycle (containers run for entire test suite)
- Manual container management in CI/CD pipelines
- Custom orchestration scenarios
- Non-test-framework integrations

**Limitations:**
- Only supports suite lifecycle (not class or method)
- Requires explicit `dependsOn` and `finalizedBy` in build.gradle
- More boilerplate configuration

## Lifecycle Patterns

All orchestration approaches support these lifecycle patterns:

- **CLASS Lifecycle** - Containers start once for all tests in a class (balanced performance and isolation)
- **METHOD Lifecycle** - Containers restart for each test method (maximum isolation, slower)
- **SUITE Lifecycle** - Containers start once for entire test suite (fastest, only via Gradle tasks)

Each pattern includes:
- Automated container startup and shutdown
- Health/readiness waiting
- Log capture
- State file generation with service info (ports, container IDs, etc.)

## Test Framework Extensions (Recommended)

### Spock Extension

The Spock extension uses the `@ComposeUp` annotation to manage Docker Compose lifecycles.

#### Configuration Pattern (RECOMMENDED)

**✅ Recommended**: Configure Docker Compose in `build.gradle` and use zero-parameter `@ComposeUp` annotation:

```gradle
// build.gradle
dockerOrch {
    composeStacks {
        webAppTest {
            files.from('src/integrationTest/resources/compose/web-app.yml')
            waitForHealthy {
                waitForServices.set(['web-app'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "webAppTest", lifecycle: "class")
}
```

```groovy
// Test class
@ComposeUp  // No parameters! Configuration from build.gradle via usesCompose()
class WebAppIT extends Specification {
    // ...
}
```

**Benefits:**
- Single source of truth in `build.gradle`
- No duplication between annotation and build script
- Easy to share configuration across multiple test classes
- Easy to override via command line or CI/CD

**⚠️ Backward Compatible**: You can still use annotation-only configuration with all parameters:

```groovy
@ComposeUp(
    stackName = "webAppTest",
    composeFile = "src/integrationTest/resources/compose/web-app.yml",
    lifecycle = LifecycleMode.CLASS,
    waitForHealthy = ["web-app"]
)
class WebAppIT extends Specification { /* ... */ }
```

#### CLASS Lifecycle with Spock

Containers start once in `setupSpec()` and stop once in `cleanupSpec()`. All test methods share the same containers.

**When to use:**
- Tests that build on each other (e.g., register → login → update)
- Read-only tests against the same data
- Performance matters and isolation is not critical

**Example Test:**

```groovy
package com.example.integration

import com.kineticfire.gradle.docker.spock.ComposeUp
import spock.lang.Specification
import spock.lang.Shared
import groovy.json.JsonSlurper
import io.restassured.RestAssured

@ComposeUp  // Reads configuration from build.gradle via usesCompose()
class WebAppIT extends Specification {

    @Shared
    String baseUrl

    def setupSpec() {
        // Extension provides COMPOSE_STATE_FILE system property
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)

        // Extract the host port for the web-app service
        def port = stateData.services['web-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"

        RestAssured.baseURI = baseUrl
    }

    def "should respond to health check endpoint"() {
        when:
        def response = RestAssured.get("/health")

        then:
        response.statusCode() == 200
        response.jsonPath().getString("status") == "UP"
    }

    def "should return greeting"() {
        when:
        def response = RestAssured.get("/greeting")

        then:
        response.statusCode() == 200
        response.jsonPath().getString("message") == "Hello, World!"
    }
}
```

**Gradle Configuration:**

```gradle
// build.gradle

plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker' version '1.0.0'
}

repositories {
    mavenCentral()
}

dependencies {
    // Spock for testing
    testImplementation platform('org.spockframework:spock-bom:2.3-groovy-3.0')
    testImplementation 'org.spockframework:spock-core'

    // Plugin for @ComposeUp annotation
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${project.findProperty('plugin_version')}"

    // REST testing
    integrationTestImplementation 'io.rest-assured:rest-assured:5.3.0'
    integrationTestImplementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'
}

// Configure dockerOrch DSL
dockerOrch {
    composeStacks {
        webAppTest {
            files.from('src/integrationTest/resources/compose/web-app.yml')
            projectName = "web-app-test"

            waitForHealthy {
                waitForServices.set(['web-app'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}

// Plugin automatically creates integrationTest source set when java/groovy plugin is present!
// Just add integration test dependencies:
dependencies {
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${project.findProperty('plugin_version')}"
    integrationTestImplementation 'io.rest-assured:rest-assured:5.3.0'
}

// Customize the convention-created integrationTest task
tasks.named('integrationTest') {
    // ============================================================================
    // RECOMMENDED: Use usesCompose() to pass configuration to @ComposeUp annotation
    // ============================================================================
    // This automatically sets system properties that @ComposeUp reads:
    //   - docker.compose.stack
    //   - docker.compose.files
    //   - docker.compose.lifecycle
    //   - docker.compose.waitForHealthy.services
    //   - docker.compose.waitForHealthy.timeoutSeconds
    //   - docker.compose.waitForHealthy.pollSeconds
    //   - docker.compose.project
    usesCompose(stack: "webAppTest", lifecycle: "class")

    // Not cacheable - interacts with Docker
    outputs.cacheIf { false }
}

// Ensure Docker image is built before running integration tests
afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('dockerBuildWebApp')  // Your image build task
    }
}
```

**Docker Compose File:**

```yaml
# src/integrationTest/resources/compose/web-app.yml
services:
  web-app:
    image: my-web-app:latest
    ports:
      - "8080"  # Dynamic port assignment
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 10
```

#### METHOD Lifecycle with Spock

Containers restart fresh for each test method in `setup()` and stop in `cleanup()`. Complete isolation between tests.

**When to use:**
- Each test needs a completely clean database or state
- Tests must be independent and idempotent (can run in any order)
- Testing stateful operations that modify global state

**Example Test:**

```groovy
package com.example.integration

import com.kineticfire.gradle.docker.spock.ComposeUp
import com.kineticfire.gradle.docker.spock.LifecycleMode
import spock.lang.Specification
import groovy.json.JsonSlurper
import io.restassured.RestAssured

@ComposeUp(lifecycle = LifecycleMode.METHOD)
class IsolatedTestsIT extends Specification {

    String baseUrl  // Instance variable (NOT @Shared) - fresh for each test

    def setup() {
        // Extension provides fresh COMPOSE_STATE_FILE for EACH test
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)

        def port = stateData.services['isolated-tests'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"

        RestAssured.baseURI = baseUrl
    }

    def "test 1: should create user alice with fresh database"() {
        when:
        def response = RestAssured.given()
            .contentType("application/json")
            .body('{"username":"alice","email":"alice@example.com"}')
            .post("/users")

        then:
        response.statusCode() == 200
        response.jsonPath().getString("username") == "alice"
        response.jsonPath().getLong("id") == 1L
    }

    def "test 2: should NOT find alice (database is fresh)"() {
        when:
        def response = RestAssured.get("/users/alice")

        then:
        response.statusCode() == 404  // User should NOT exist (database is fresh)
    }

    def "test 3: should create user alice again with fresh database"() {
        when:
        def response = RestAssured.given()
            .contentType("application/json")
            .body('{"username":"alice","email":"alice@example.com"}')
            .post("/users")

        then:
        response.statusCode() == 200
        response.jsonPath().getLong("id") == 1L  // ID is 1 again because database is fresh!
    }
}
```

**Gradle Configuration:**

Same as CLASS lifecycle example above, but with different stack name:

```gradle
dockerOrch {
    stacks {
        isolatedTestsTest {
            projectName = 'isolated-tests-test'
            stackName = 'isolatedTestsTest'
            composeFiles = [file('src/integrationTest/resources/compose/isolated-tests.yml')]
            // ... wait and logs configuration
        }
    }
}

tasks.register('integrationTest', Test) {
    // ... same configuration as CLASS example

    systemProperty 'docker.compose.stack', 'isolatedTestsTest'
    systemProperty 'docker.compose.project', 'example-isolated-tests-test'
}
```

### JUnit 5 Extensions

The JUnit 5 extensions use `@ExtendWith` annotations to manage Docker Compose lifecycles.

#### CLASS Lifecycle with JUnit 5

Use `DockerComposeClassExtension` for CLASS lifecycle. Containers start once in `@BeforeAll` and stop once in
`@AfterAll`. All test methods share the same containers.

**Example Test:**

```java
package com.example.integration;

import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DockerComposeClassExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebAppJUnit5ClassIT {

    private static String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setupAll() throws IOException {
        // Extension provides COMPOSE_STATE_FILE system property
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        File stateFile = new File(stateFilePath);
        JsonNode stateData = objectMapper.readTree(stateFile);

        // Extract the host port for the web-app service
        int port = stateData.get("services")
            .get("web-app")
            .get("publishedPorts")
            .get(0)
            .get("host")
            .asInt();

        baseUrl = "http://localhost:" + port;
        RestAssured.baseURI = baseUrl;
    }

    @Test
    @Order(1)
    @DisplayName("Should respond to health check endpoint")
    void shouldRespondToHealthCheckEndpoint() {
        Response response = given().get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("UP", response.jsonPath().getString("status"));
    }

    @Test
    @Order(2)
    @DisplayName("Should return greeting")
    void shouldReturnGreeting() {
        Response response = given().get("/greeting");

        assertEquals(200, response.statusCode());
        assertEquals("Hello, World!", response.jsonPath().getString("message"));
    }
}
```

**Gradle Configuration:**

```gradle
// build.gradle

plugins {
    id 'java'
    id 'com.kineticfire.gradle.docker' version '1.0.0'
}

repositories {
    mavenCentral()
}

dependencies {
    // JUnit 5
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

    // Plugin for @ExtendWith annotation access
    testImplementation "com.kineticfire.gradle:gradle-docker:${project.findProperty('plugin_version')}"

    // REST testing
    testImplementation 'io.rest-assured:rest-assured:5.3.0'
    testImplementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'
}

// Configure dockerOrch DSL
dockerOrch {
    stacks {
        webAppTest {
            projectName = 'web-app-test'
            stackName = 'webAppTest'
            composeFiles = [file('src/integrationTest/resources/compose/web-app.yml')]

            wait {
                services = ['web-app']
                timeout = duration(30, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }

            logs {
                outputFile = file("${buildDir}/compose-logs/web-app-test.log")
                tailLines = 1000
            }
        }
    }
}

// Create integration test source set
sourceSets {
    integrationTest {
        java.srcDir 'src/integrationTest/java'
        resources.srcDir 'src/integrationTest/resources'
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
    }
}

configurations {
    integrationTestImplementation.extendsFrom testImplementation
    integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
}

// Register integration test task
tasks.register('integrationTest', Test) {
    description = 'Runs CLASS lifecycle integration tests using JUnit 5 extension'
    group = 'verification'

    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath

    useJUnitPlatform()

    // Configure system properties for JUnit extension
    systemProperty 'docker.compose.stack', 'webAppTest'
    systemProperty 'docker.compose.project', 'example-web-app-junit-test'

    // NOTE: No systemProperty for COMPOSE_STATE_FILE needed - extension generates it
    // NOTE: No dependsOn/finalizedBy needed - extension manages container lifecycle

    // Not cacheable - interacts with Docker
    outputs.cacheIf { false }
}

// Ensure Docker image is built before running integration tests
afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('dockerBuildWebApp')  // Your image build task
    }
}
```

#### METHOD Lifecycle with JUnit 5

Use `DockerComposeMethodExtension` for METHOD lifecycle. Containers restart fresh for each test method in `@BeforeEach`
and stop in `@AfterEach`. Complete isolation between tests.

**Example Test:**

```java
package com.example.integration;

import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DockerComposeMethodExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IsolatedTestsJUnit5MethodIT {

    // Instance variables (NOT static!) - fresh for each test
    private String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setupEach() throws IOException {
        // Extension provides fresh COMPOSE_STATE_FILE for EACH test
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        File stateFile = new File(stateFilePath);
        JsonNode stateData = objectMapper.readTree(stateFile);

        int port = stateData.get("services")
            .get("isolated-tests")
            .get("publishedPorts")
            .get(0)
            .get("host")
            .asInt();

        baseUrl = "http://localhost:" + port;
        RestAssured.baseURI = baseUrl;
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Should create user alice with fresh database")
    void test1_shouldCreateUserAliceWithFreshDatabase() {
        Response response = given()
            .contentType("application/json")
            .body("{\"username\":\"alice\",\"email\":\"alice@example.com\"}")
            .post("/users");

        assertEquals(200, response.statusCode());
        assertEquals("alice", response.jsonPath().getString("username"));
        assertEquals(1L, response.jsonPath().getLong("id"));
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Should NOT find alice (database is fresh)")
    void test2_shouldNotFindAlice() {
        Response response = given().get("/users/alice");

        assertEquals(404, response.statusCode(), "User should NOT exist (database is fresh)");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Should create user alice again with fresh database")
    void test3_shouldCreateUserAliceAgainWithFreshDatabase() {
        Response response = given()
            .contentType("application/json")
            .body("{\"username\":\"alice\",\"email\":\"alice@example.com\"}")
            .post("/users");

        assertEquals(200, response.statusCode());
        assertEquals(1L, response.jsonPath().getLong("id"), "ID is 1 again because database is fresh!");
    }
}
```

**Gradle Configuration:**

Same as CLASS lifecycle example above, but with different stack name and system properties:

```gradle
dockerOrch {
    stacks {
        isolatedTestsTest {
            projectName = 'isolated-tests-test'
            stackName = 'isolatedTestsTest'
            composeFiles = [file('src/integrationTest/resources/compose/isolated-tests.yml')]
            // ... wait and logs configuration
        }
    }
}

tasks.register('integrationTest', Test) {
    // ... same configuration as CLASS example

    systemProperty 'docker.compose.stack', 'isolatedTestsTest'
    systemProperty 'docker.compose.project', 'example-isolated-tests-junit-test'
}
```

## Gradle Tasks (Optional - Suite Lifecycle)

Gradle tasks provide suite-level orchestration using `composeUp*` and `composeDown*` tasks. Use this approach when:
- You need containers to run for the entire test suite (suite lifecycle only)
- You're orchestrating containers manually in CI/CD pipelines
- You need custom orchestration logic outside test frameworks

**Note:** Gradle tasks only support **suite lifecycle**. For CLASS or METHOD lifecycles, use test framework extensions.

### Basic Suite Configuration

```gradle
// build.gradle
plugins {
    id 'java'
    id 'com.kineticfire.gradle.docker' version '1.0.0'
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

            wait {
                services = ['api-server', 'postgres']
                timeout = duration(60, 'SECONDS')
                pollInterval = duration(2, 'SECONDS')
                waitForStatus = 'HEALTHY'  // Options: 'RUNNING', 'HEALTHY'
            }

            logs {
                outputFile = file("${buildDir}/compose-logs/api-test-suite.log")
                services = ['api-server']  // Optional: specific services only
                tailLines = 1000           // Optional: limit lines (default: all)
                follow = false             // Optional: follow logs (default: false)
            }
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

## Accessing State Files

After containers are started (by extension or Gradle task), a state file is generated with service information.

### State File Location

- **Test Framework Extensions**: Temporary file, path provided via `COMPOSE_STATE_FILE` system property
- **Gradle Tasks**: `build/compose-state/<stackName>-state.json`

### State File Format

```json
{
  "stackName": "api-test-stack",
  "projectName": "my-app-123456",
  "lifecycle": "class",
  "timestamp": "2025-01-15T10:30:45",
  "services": {
    "api-server": {
      "containerId": "abc123def456",
      "containerName": "my-app-api-server-1",
      "state": "running",
      "publishedPorts": [
        {
          "host": 54321,
          "container": 8080,
          "protocol": "tcp"
        }
      ]
    },
    "postgres": {
      "containerId": "def789ghi012",
      "containerName": "my-app-postgres-1",
      "state": "running",
      "publishedPorts": []
    }
  }
}
```

### Accessing State in Tests

**Java (Jackson):**

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
File stateFile = new File(stateFilePath);
JsonNode stateData = new ObjectMapper().readTree(stateFile);

int port = stateData.get("services")
    .get("api-server")
    .get("publishedPorts")
    .get(0)
    .get("host")
    .asInt();

String baseUrl = "http://localhost:" + port;
```

**Groovy (JsonSlurper):**

```groovy
import groovy.json.JsonSlurper

def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
def stateData = new JsonSlurper().parse(stateFile)

def port = stateData.services['api-server'].publishedPorts[0].host
def baseUrl = "http://localhost:${port}"
```

## System Properties Reference

The plugin uses these system properties (automatically set when using extensions or `usesCompose()`):

- **`docker.compose.stack`** - Stack name from dockerOrch DSL
- **`docker.compose.project`** - Project name base
- **`COMPOSE_STATE_FILE`** - Path to generated state file with service info

These are used internally by extensions and are available to your test code.

## Troubleshooting Guide

### Common Issues and Solutions

#### 1. Containers Not Stopping After Tests

**Symptom:** Running `docker ps` shows leftover containers from previous test runs.

**Solution for Gradle Tasks:** Ensure `finalizedBy composeDown*` is configured:

```gradle
task integrationTest(type: Test) {
    dependsOn composeUpApiTests
    finalizedBy composeDownApiTests  // IMPORTANT: Always clean up
}
```

**Solution for Extensions:** Extensions automatically clean up. If containers remain, check for test failures or
crashes that prevented cleanup. Manually clean up:

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
- Ensure `usesCompose('stackName')` is called on the test task (for Gradle tasks)
- For extensions, verify `@ComposeUp` or `@ExtendWith` annotation is present
- Check that stack name in system property matches dockerOrch DSL
- Verify compose up completed successfully before tests access state file

#### 5. Extension Not Running Containers

**Symptom:** Test framework extension doesn't start containers

**Solutions:**
- Verify annotation is present: `@ComposeUp(lifecycle = LifecycleMode.CLASS)` or `@ExtendWith(DockerComposeClassExtension.class)`
- Ensure compose file exists at expected path
- Check test task has system properties configured:
  ```gradle
  systemProperty 'docker.compose.stack', 'webAppTest'
  systemProperty 'docker.compose.project', 'example-web-app-test'
  ```
- Verify dockerOrch DSL has matching stack configured
- Check test output for error messages during setup

#### 6. Compose File Version Warnings

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

#### 7. Configuration Cache Issues

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

- **CLASS/Suite:** For tests that build on each other (workflow: register → login → update), read-only tests, or when
  performance matters
- **METHOD:** For tests that need complete isolation, fresh database state, or independent/idempotent execution

### 2. Choose the Right Orchestration Approach

- **Test Framework Extensions (Recommended):** For most integration testing scenarios, provides automatic lifecycle
  management
- **Gradle Tasks:** For suite lifecycle, CI/CD pipelines, or custom orchestration needs

### 3. Optimize Health Checks

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

### 4. Use Meaningful Project Names

```gradle
projectName = "my-app-${rootProject.name}"  // Unique per project
```

### 5. Structure Compose Files

```
src/integrationTest/resources/compose/
├── base.yml              # Common services (databases, queues)
├── services.yml          # Application services
├── overrides.yml         # Test-specific overrides
├── .env.base             # Shared environment variables
└── .env.local            # Local overrides (gitignored)
```

### 6. Capture Useful Logs

```gradle
logs {
    outputFile = file("${buildDir}/compose-logs/${name}-${new Date().format('yyyyMMdd-HHmmss')}.log")
    services = ['api-server']  # Only services likely to have errors
    tailLines = 1000           # Last 1000 lines usually sufficient
}
```

### 7. Clean Up Aggressively

```gradle
// For Gradle tasks - always clean up, even on failure
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

### 8. Use Dynamic Port Assignment

```yaml
# Don't hardcode host ports - let Docker assign them
services:
  web:
    ports:
      - "8080"  # Docker assigns random available host port
```

Then read the actual port from the state file in your tests.

## Complete Examples

See the working examples in this repository:

- **Spock Examples**:
  - CLASS lifecycle: `plugin-integration-test/dockerOrch/examples/web-app/`
  - METHOD lifecycle: `plugin-integration-test/dockerOrch/examples/isolated-tests/`

- **JUnit 5 Examples**:
  - CLASS lifecycle: `plugin-integration-test/dockerOrch/examples/web-app-junit/`
  - METHOD lifecycle: `plugin-integration-test/dockerOrch/examples/isolated-tests-junit/`

- **Gradle Task Examples**:
  - Suite lifecycle: `plugin-integration-test/dockerOrch/examples/stateful-web-app/`

For more detailed information on Spock and JUnit 5 extensions, see
[Spock and JUnit Test Extensions Guide](spock-junit-test-extensions.md).

## Integration Test Source Set Convention

The gradle-docker plugin automatically creates an `integrationTest` source set when the java or groovy plugin
is applied to your project. This eliminates the need for manual boilerplate configuration.

### Automatic Setup

When you apply the gradle-docker plugin to a project with the java or groovy plugin, the plugin automatically
provides:

1. **Source directories**:
   - `src/integrationTest/java` (always configured)
   - `src/integrationTest/groovy` (when groovy plugin is applied)
   - `src/integrationTest/resources` (always configured)

2. **Configurations**:
   - `integrationTestImplementation` (extends `testImplementation`)
   - `integrationTestRuntimeOnly` (extends `testRuntimeOnly`)

3. **Tasks**:
   - `integrationTest` - runs all integration tests using JUnit Platform
   - `processIntegrationTestResources` - processes test resources with INCLUDE duplicatesStrategy

4. **Classpath**:
   - Integration tests automatically have access to main source set classes
   - Integration tests inherit dependencies from test configurations

### Minimal Configuration Example

```groovy
plugins {
    id 'groovy'  // or 'java'
    id 'com.kineticfire.gradle.docker'
}

docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['latest']
            context.set(file('src/main/docker'))
        }
    }
}

dockerOrch {
    composeStacks {
        myTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = "my-app-test"
            waitForHealthy {
                waitForServices.set(['my-app'])
            }
        }
    }
}

// Add integration test dependencies
dependencies {
    integrationTestImplementation 'org.spockframework:spock-core:2.3'
    // or for JUnit:
    // integrationTestImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
}

// Wire Docker operations to integration tests
afterEvaluate {
    tasks.named('composeUpMyTest') {
        dependsOn tasks.named('dockerBuildMyApp')
    }
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpMyTest')
        finalizedBy tasks.named('composeDownMyTest')
    }
}

// That's it! Convention provides source set, configurations, and task automatically.
```

### How It Works

**Trigger**: The convention applies automatically when the java or groovy plugin is present in your project.

**When it applies**:
- ✅ Your project has java or groovy plugin applied
- ✅ The gradle-docker plugin is applied
- ✅ You haven't manually created the integrationTest source set

**When it doesn't apply**:
- ❌ No java/groovy plugin (not a JVM project)
- ❌ You manually created integrationTest source set before applying the plugin (your config takes precedence)

### Language Support

Write integration tests in:
- **Java only**: Use `java` plugin, tests in `src/integrationTest/java`
- **Groovy/Spock only**: Use `groovy` plugin, tests in `src/integrationTest/groovy`
- **Both**: Use `groovy` plugin, place tests in either directory

The convention works regardless of your main application language.

**Example**: Java application with Spock integration tests:
```groovy
plugins {
    id 'java'          // Main app is Java
    id 'groovy'        // Add groovy for Spock tests
    id 'com.kineticfire.gradle.docker'
}

dependencies {
    // Main app dependencies
    implementation 'org.springframework.boot:spring-boot-starter-web:3.2.0'

    // Integration tests use Spock (Groovy)
    integrationTestImplementation 'org.spockframework:spock-core:2.3'
}

// Convention automatically configures both:
// - src/integrationTest/java/      (available but may be empty)
// - src/integrationTest/groovy/    (put Spock tests here)
```

### Customizing the Convention

Override any aspect using standard Gradle DSL:

**Change source directories**:
```groovy
sourceSets {
    integrationTest {
        groovy.srcDirs = ['custom/test/path']
        resources.srcDirs = ['custom/resources']
    }
}
```

**Customize the test task**:
```groovy
tasks.named('integrationTest') {
    maxParallelForks = 4
    systemProperty 'custom.prop', 'value'
    // Any Test task configuration
}
```

**Add additional dependencies**:
```groovy
dependencies {
    integrationTestImplementation 'io.rest-assured:rest-assured:5.3.0'
    integrationTestRuntimeOnly 'ch.qos.logback:logback-classic:1.4.11'
}
```

### Disable the Convention

If you need complete control, create the source set yourself before applying the plugin:

```groovy
// Option 1: Create source set manually
sourceSets {
    integrationTest {
        // Your custom configuration
    }
}

plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}
// Plugin sees existing source set and won't create its own
```

### Benefits

**Before convention** (per project):
- ~40-50 lines of repetitive boilerplate code
- Manual maintenance across multiple projects
- Risk of inconsistency and copy-paste errors
- Steep learning curve for new users

**After convention**:
- 0 lines of boilerplate required
- Automatic consistency across all projects
- Plugin ensures best practices
- Works out of the box

### Migration Guide for Existing Projects

If you have existing projects with manual integrationTest source set configuration:

**Step 1**: Identify boilerplate to remove
Look for these blocks in your `build.gradle`:
```groovy
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

tasks.named('processIntegrationTestResources') {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.register('integrationTest', Test) {
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    outputs.cacheIf { false }
}
```

**Step 2**: Remove standard boilerplate
Delete the blocks above from your `build.gradle`.

**Step 3**: Keep only customizations
Retain any project-specific customizations:
```groovy
// Keep customizations like this:
tasks.named('integrationTest') {
    maxParallelForks = 4
    systemProperty 'test.db.url', 'jdbc:h2:mem:test'
}
```

**Step 4**: Verify
```bash
./gradlew clean integrationTest
```

**Result**: Same functionality, less code, easier maintenance.

### Complete Examples

See these integration test examples for complete working demonstrations:
- `plugin-integration-test/dockerOrch/examples/web-app/` - Spock-based tests
- `plugin-integration-test/dockerOrch/examples/web-app-junit/` - JUnit-based tests
- `plugin-integration-test/dockerOrch/examples/isolated-tests/` - Test isolation pattern
- `plugin-integration-test/dockerOrch/verification/basic/` - Minimal setup

Each example includes inline comments explaining the convention.
