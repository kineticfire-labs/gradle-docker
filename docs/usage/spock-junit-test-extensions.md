# Spock and JUnit Test Extensions Guide

This guide provides detailed information about using the Spock and JUnit 5 test framework extensions for Docker Compose
orchestration in your integration tests.

## Table of Contents

- [Overview](#overview)
- [Why Use Test Framework Extensions](#why-use-test-framework-extensions)
- [Lifecycle Patterns](#lifecycle-patterns)
- [Spock Extension](#spock-extension)
  - [ComposeUp Annotation](#composeup-annotation)
  - [CLASS Lifecycle](#class-lifecycle-spock)
  - [METHOD Lifecycle](#method-lifecycle-spock)
- [JUnit 5 Extensions](#junit-5-extensions)
  - [DockerComposeClassExtension](#dockercomposeclassextension)
  - [DockerComposeMethodExtension](#dockercomposemethodextension)
  - [CLASS Lifecycle](#class-lifecycle-junit-5)
  - [METHOD Lifecycle](#method-lifecycle-junit-5)
- [Configuration](#configuration)
- [State Files](#state-files)
- [How Extensions Work](#how-extensions-work)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)
- [Complete Examples](#complete-examples)

## Overview

The gradle-docker plugin provides test framework extensions for **Spock** and **JUnit 5** that automatically manage
Docker Compose container lifecycles during your integration tests.

**Key Features:**
- **Automatic Lifecycle Management**: Containers start/stop automatically - no manual Gradle task dependencies needed
- **CLASS and METHOD Lifecycles**: Choose between shared containers (CLASS) or isolated containers (METHOD)
- **State File Generation**: Automatically generates JSON state files with service info, ports, container IDs
- **Health/Readiness Waiting**: Waits for containers to reach RUNNING or HEALTHY status
- **Log Capture**: Captures container logs on teardown
- **Clean Teardown**: Ensures containers are always stopped, even on test failures

## Why Use Test Framework Extensions

### Advantages Over Gradle Tasks

**Test Framework Extensions (RECOMMENDED):**
- ✅ Automatic lifecycle management (no `dependsOn`/`finalizedBy` needed)
- ✅ Support both CLASS and METHOD lifecycles
- ✅ Minimal boilerplate in build.gradle
- ✅ Test framework controls timing (setup/teardown)
- ✅ Better isolation and predictability

**Gradle Tasks (OPTIONAL):**
- ⚠️ Only supports suite lifecycle
- ⚠️ Requires manual `dependsOn`/`finalizedBy` configuration
- ⚠️ More boilerplate
- ✅ Good for CI/CD pipelines or custom orchestration

**When to Use Extensions:**
- Most integration testing scenarios
- When you need CLASS or METHOD lifecycle control
- When you want clean, minimal test code

**When to Use Gradle Tasks:**
- Suite lifecycle (containers run for entire test suite)
- Manual container management in CI/CD pipelines
- Custom orchestration logic outside test frameworks

## Lifecycle Patterns

### CLASS Lifecycle

**Containers start once before all tests in a class, stop once after all tests complete.**

**Timing:**
- **Spock**: Containers start in `setupSpec()`, stop in `cleanupSpec()`
- **JUnit 5**: Containers start in `@BeforeAll`, stop in `@AfterAll`

**Characteristics:**
- All test methods share the same containers
- Faster than METHOD lifecycle (containers start only once)
- State persists between test methods
- Good balance of performance and isolation

**When to use:**
- Tests that build on each other (e.g., register → login → update profile)
- Read-only tests against the same data
- Performance matters and state isolation isn't critical

**Example use cases:**
- Testing a workflow that spans multiple operations
- Testing read-only API endpoints
- Testing query operations that don't modify state

### METHOD Lifecycle

**Containers restart fresh for each test method.**

**Timing:**
- **Spock**: Containers start in `setup()`, stop in `cleanup()` for EACH test method
- **JUnit 5**: Containers start in `@BeforeEach`, stop in `@AfterEach` for EACH test method

**Characteristics:**
- Each test method gets completely fresh containers
- Complete isolation between tests
- Slower than CLASS lifecycle (containers restart for each test)
- State does NOT persist between test methods

**When to use:**
- Each test needs a completely clean database or state
- Tests must be independent and idempotent (can run in any order)
- Testing stateful operations that modify global state

**Example use cases:**
- Database integration tests where each test needs a fresh database
- Testing user creation/deletion (isolation prevents conflicts)
- Testing state mutations that would affect other tests

## Spock Extension

The Spock extension uses the `@ComposeUp` annotation to manage Docker Compose lifecycles.

### ComposeUp Annotation

```groovy
import com.kineticfire.gradle.docker.spock.ComposeUp
import com.kineticfire.gradle.docker.spock.LifecycleMode

// CLASS lifecycle (default)
@ComposeUp(lifecycle = LifecycleMode.CLASS)
class MyIntegrationTest extends Specification { }

// METHOD lifecycle
@ComposeUp(lifecycle = LifecycleMode.METHOD)
class MyIsolatedTest extends Specification { }
```

**Annotation Parameters:**
- `lifecycle` (required): `LifecycleMode.CLASS` or `LifecycleMode.METHOD`

### CLASS Lifecycle (Spock)

#### Complete Example

```groovy
package com.example.integration

import com.kineticfire.gradle.docker.spock.ComposeUp
import com.kineticfire.gradle.docker.spock.LifecycleMode
import spock.lang.Specification
import spock.lang.Shared
import groovy.json.JsonSlurper
import io.restassured.RestAssured
import io.restassured.response.Response

/**
 * Example: Spock with CLASS Lifecycle
 *
 * Containers start once in setupSpec(), stop once in cleanupSpec().
 * All test methods share the same containers.
 */
@ComposeUp(lifecycle = LifecycleMode.CLASS)
class WebAppIT extends Specification {

    @Shared
    String baseUrl

    def setupSpec() {
        // Extension provides COMPOSE_STATE_FILE system property
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)

        // Extract the dynamically assigned host port
        def port = stateData.services['web-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"

        RestAssured.baseURI = baseUrl
        println "=== Containers started, baseUrl: ${baseUrl} ==="
    }

    def cleanupSpec() {
        println "=== Containers stopping ==="
    }

    def "should respond to health check endpoint"() {
        when:
        Response response = RestAssured.get("/health")

        then:
        response.statusCode() == 200
        response.jsonPath().getString("status") == "UP"
    }

    def "should return greeting message"() {
        when:
        Response response = RestAssured.get("/greeting")

        then:
        response.statusCode() == 200
        response.jsonPath().getString("message") == "Hello, World!"
    }

    def "should create and retrieve user"() {
        when: "create a user"
        Response createResponse = RestAssured.given()
            .contentType("application/json")
            .body('{"username":"alice","email":"alice@example.com"}')
            .post("/users")

        then: "user is created"
        createResponse.statusCode() == 200
        def userId = createResponse.jsonPath().getLong("id")

        when: "retrieve the user"
        Response getResponse = RestAssured.get("/users/${userId}")

        then: "user is found"
        getResponse.statusCode() == 200
        getResponse.jsonPath().getString("username") == "alice"
    }
}
```

#### Key Points

- Use `@Shared` for variables that need to be accessed across all tests
- Containers start **once** before all tests
- State **persists** between test methods (user created in one test might be visible in another)
- Faster execution (containers start only once)

### METHOD Lifecycle (Spock)

#### Complete Example

```groovy
package com.example.integration

import com.kineticfire.gradle.docker.spock.ComposeUp
import com.kineticfire.gradle.docker.spock.LifecycleMode
import spock.lang.Specification
import groovy.json.JsonSlurper
import io.restassured.RestAssured
import io.restassured.response.Response

/**
 * Example: Spock with METHOD Lifecycle
 *
 * Containers restart fresh for each test method in setup(), stop in cleanup().
 * Complete isolation between tests.
 */
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
        println "=== Fresh containers started for this test ==="
    }

    def cleanup() {
        println "=== Containers stopping after this test ==="
    }

    def "test 1: should create user alice with fresh database"() {
        when:
        Response response = RestAssured.given()
            .contentType("application/json")
            .body('{"username":"alice","email":"alice@example.com"}')
            .post("/users")

        then:
        response.statusCode() == 200
        response.jsonPath().getString("username") == "alice"
        response.jsonPath().getLong("id") == 1L  // ID is 1 (fresh database)
    }

    def "test 2: should NOT find alice (database is fresh)"() {
        when:
        Response response = RestAssured.get("/users/alice")

        then:
        response.statusCode() == 404  // User should NOT exist (database is fresh)
    }

    def "test 3: should create user alice again with fresh database"() {
        when:
        Response response = RestAssured.given()
            .contentType("application/json")
            .body('{"username":"alice","email":"alice@example.com"}')
            .post("/users")

        then:
        response.statusCode() == 200
        response.jsonPath().getLong("id") == 1L  // ID is 1 again because database is fresh!
    }

    def "test 4: should have empty database (no users from previous tests)"() {
        when:
        Response response = RestAssured.get("/users")

        then:
        response.statusCode() == 200
        response.jsonPath().getList("\$").size() == 0  // Database is empty
    }
}
```

#### Key Points

- Do NOT use `@Shared` - use instance variables that are fresh for each test
- Containers restart **for each test method**
- State **does NOT persist** between test methods (complete isolation)
- Slower execution (containers restart for each test)
- Proves isolation: tests can run in any order

## JUnit 5 Extensions

The JUnit 5 extensions use `@ExtendWith` annotations to manage Docker Compose lifecycles.

### DockerComposeClassExtension

Use `DockerComposeClassExtension` for CLASS lifecycle management.

```java
import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerComposeClassExtension.class)
class MyIntegrationTest {
    // Containers start in @BeforeAll, stop in @AfterAll
}
```

### DockerComposeMethodExtension

Use `DockerComposeMethodExtension` for METHOD lifecycle management.

```java
import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerComposeMethodExtension.class)
class MyIsolatedTest {
    // Containers start in @BeforeEach, stop in @AfterEach
}
```

### CLASS Lifecycle (JUnit 5)

#### Complete Example

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

/**
 * Example: JUnit 5 with CLASS Lifecycle
 *
 * Containers start once in @BeforeAll, stop once in @AfterAll.
 * All test methods share the same containers.
 */
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

        // Extract the dynamically assigned host port
        int port = stateData.get("services")
            .get("web-app")
            .get("publishedPorts")
            .get(0)
            .get("host")
            .asInt();

        baseUrl = "http://localhost:" + port;
        RestAssured.baseURI = baseUrl;
        System.out.println("=== Containers started, baseUrl: " + baseUrl + " ===");
    }

    @AfterAll
    static void teardownAll() {
        System.out.println("=== Containers stopping ===");
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
    @DisplayName("Should return greeting message")
    void shouldReturnGreetingMessage() {
        Response response = given().get("/greeting");

        assertEquals(200, response.statusCode());
        assertEquals("Hello, World!", response.jsonPath().getString("message"));
    }

    @Test
    @Order(3)
    @DisplayName("Should create and retrieve user")
    void shouldCreateAndRetrieveUser() {
        // Create user
        Response createResponse = given()
            .contentType("application/json")
            .body("{\"username\":\"alice\",\"email\":\"alice@example.com\"}")
            .post("/users");

        assertEquals(200, createResponse.statusCode());
        long userId = createResponse.jsonPath().getLong("id");

        // Retrieve user
        Response getResponse = given().get("/users/" + userId);

        assertEquals(200, getResponse.statusCode());
        assertEquals("alice", getResponse.jsonPath().getString("username"));
    }
}
```

#### Key Points

- Use `static` variables and `@BeforeAll`/`@AfterAll` methods
- Containers start **once** before all tests
- State **persists** between test methods
- Faster execution (containers start only once)

### METHOD Lifecycle (JUnit 5)

#### Complete Example

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
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Example: JUnit 5 with METHOD Lifecycle
 *
 * Containers restart fresh for each test method in @BeforeEach, stop in @AfterEach.
 * Complete isolation between tests.
 */
@ExtendWith(DockerComposeMethodExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IsolatedTestsJUnit5MethodIT {

    // Instance variables (NOT static!) - fresh for each test
    private String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Static counters to track lifecycle calls
    private static int setupCallCount = 0;
    private static int cleanupCallCount = 0;

    @BeforeEach
    void setupEach() throws IOException {
        setupCallCount++;

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
        System.out.println("=== Test " + setupCallCount + ": Fresh containers started ===");
    }

    @AfterEach
    void cleanupEach() {
        cleanupCallCount++;
        System.out.println("=== Test " + cleanupCallCount + ": Containers stopping ===");
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
        assertEquals(1L, response.jsonPath().getLong("id"));  // ID is 1 (fresh database)
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

    @Test
    @Order(4)
    @DisplayName("Test 4: Should have empty database (no users from previous tests)")
    void test4_shouldHaveEmptyDatabase() {
        Response response = given().get("/users");

        assertEquals(200, response.statusCode());
        List<Object> users = response.jsonPath().getList("$");
        assertEquals(0, users.size(), "Database should be empty");
    }
}
```

#### Key Points

- Use instance variables (NOT static) and `@BeforeEach`/`@AfterEach` methods
- Containers restart **for each test method**
- State **does NOT persist** between test methods (complete isolation)
- Slower execution (containers restart for each test)
- Proves isolation: tests can run in any order

## Configuration

### Required Configuration

Both Spock and JUnit 5 extensions require:

1. **dockerOrch DSL** in build.gradle defining the stack
2. **System properties** in the test task
3. **Extension annotation** on the test class

#### Example build.gradle

```gradle
plugins {
    id 'java'  // or 'groovy' for Spock
    id 'com.kineticfire.gradle.docker' version '1.0.0'
}

repositories {
    mavenCentral()
}

dependencies {
    // For Spock
    testImplementation platform('org.spockframework:spock-bom:2.3-groovy-3.0')
    testImplementation 'org.spockframework:spock-core'

    // For JUnit 5
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

    // Plugin for extension classes
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
        java.srcDir 'src/integrationTest/java'  // or groovy.srcDir for Spock
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
    description = 'Runs integration tests using test framework extensions'
    group = 'verification'

    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath

    useJUnitPlatform()  // Required for both Spock and JUnit 5

    // REQUIRED: Configure system properties for extensions
    systemProperty 'docker.compose.stack', 'webAppTest'
    systemProperty 'docker.compose.project', 'example-web-app-test'

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

### System Properties

The extensions require these system properties:

- **`docker.compose.stack`** (required): Stack name from dockerOrch DSL
- **`docker.compose.project`** (required): Project name base (used for unique project names)
- **`COMPOSE_STATE_FILE`** (auto-generated): Path to state file - set automatically by extension

**Note:** The `COMPOSE_STATE_FILE` property is automatically generated by the extension. You do NOT need to set it
manually.

## State Files

Extensions automatically generate state files with container information.

### State File Location

**Temporary file with path provided via `COMPOSE_STATE_FILE` system property.**

The extension creates a temporary file for each test class (CLASS lifecycle) or test method (METHOD lifecycle) and sets
the `COMPOSE_STATE_FILE` system property to its path.

### State File Format

```json
{
  "stackName": "webAppTest",
  "projectName": "example-web-app-test-1705323045123",
  "lifecycle": "class",
  "timestamp": "2025-01-15T10:30:45",
  "services": {
    "web-app": {
      "containerId": "abc123def456",
      "containerName": "example-web-app-test-1705323045123-web-app-1",
      "state": "running",
      "publishedPorts": [
        {
          "host": 54321,
          "container": 8080,
          "protocol": "tcp"
        }
      ]
    },
    "database": {
      "containerId": "def789ghi012",
      "containerName": "example-web-app-test-1705323045123-database-1",
      "state": "running",
      "publishedPorts": []
    }
  }
}
```

### Reading State Files

**Java (Jackson):**

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
File stateFile = new File(stateFilePath);
JsonNode stateData = new ObjectMapper().readTree(stateFile);

// Get host port for a service
int port = stateData.get("services")
    .get("web-app")
    .get("publishedPorts")
    .get(0)
    .get("host")
    .asInt();

String baseUrl = "http://localhost:" + port;

// Get container ID
String containerId = stateData.get("services")
    .get("web-app")
    .get("containerId")
    .asText();
```

**Groovy (JsonSlurper):**

```groovy
import groovy.json.JsonSlurper

def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
def stateData = new JsonSlurper().parse(stateFile)

// Get host port for a service
def port = stateData.services['web-app'].publishedPorts[0].host
def baseUrl = "http://localhost:${port}"

// Get container ID
def containerId = stateData.services['web-app'].containerId
```

## How Extensions Work

### Internal Architecture

Both Spock and JUnit 5 extensions follow a similar pattern:

1. **Extension Initialization**: Test framework loads extension via annotation
2. **Read Configuration**: Extension reads system properties (`docker.compose.stack`, `docker.compose.project`)
3. **Resolve Compose File**: Extension resolves compose file path from dockerOrch DSL
4. **Generate Unique Project Name**: Creates timestamp-based unique project name
5. **Start Containers**: Calls `docker compose up` with health/readiness waiting
6. **Generate State File**: Creates JSON state file with service info
7. **Set System Property**: Sets `COMPOSE_STATE_FILE` to state file path
8. **Run Tests**: Test framework executes test methods
9. **Capture Logs**: Captures container logs (if configured in dockerOrch DSL)
10. **Stop Containers**: Calls `docker compose down` to clean up

### Timing

**CLASS Lifecycle:**
- Steps 1-7: Before first test method (setupSpec/@BeforeAll)
- Step 8: All test methods execute
- Steps 9-10: After last test method (cleanupSpec/@AfterAll)

**METHOD Lifecycle:**
- Steps 1-7: Before EACH test method (setup/@BeforeEach)
- Step 8: One test method executes
- Steps 9-10: After EACH test method (cleanup/@AfterEach)

### Service Layer

Extensions use the same service layer as Gradle tasks:
- **JUnitComposeService**: Docker Compose operations (up, down, ps)
- **FileService**: File operations (resolve paths, write state files)
- **SystemPropertyService**: System property access
- **TimeService**: Timestamp generation
- **WaitService**: Health/readiness waiting

This ensures consistent behavior between extensions and Gradle tasks.

## Troubleshooting

### Extension Not Starting Containers

**Symptom:** Test runs but containers are not started

**Solutions:**
1. Verify annotation is present on test class:
   ```groovy
   @ComposeUp(lifecycle = LifecycleMode.CLASS)  // Spock
   ```
   ```java
   @ExtendWith(DockerComposeClassExtension.class)  // JUnit 5
   ```

2. Check system properties in test task:
   ```gradle
   systemProperty 'docker.compose.stack', 'webAppTest'
   systemProperty 'docker.compose.project', 'example-web-app-test'
   ```

3. Verify dockerOrch DSL has matching stack name:
   ```gradle
   dockerOrch {
       stacks {
           webAppTest {  // Must match system property
               // ...
           }
       }
   }
   ```

4. Check test output for error messages during setup

### State File Not Found

**Symptom:** `System.getProperty("COMPOSE_STATE_FILE")` returns null

**Solutions:**
1. Ensure extension annotation is present
2. Verify compose up completed successfully (check test output)
3. Check that stack configuration in dockerOrch DSL is correct

### Containers Not Stopping

**Symptom:** Running `docker ps` shows leftover containers after tests

**Solutions:**
1. Extensions automatically clean up - check for test crashes or JVM kills
2. Manually clean up:
   ```bash
   docker compose -p example-web-app-test down -v
   docker ps -aq --filter name=example-web-app-test | xargs -r docker rm -f
   ```

### Health Checks Timing Out

**Symptom:** Tests fail with "Service did not become healthy within timeout"

**Solutions:**
1. Increase timeout in dockerOrch DSL:
   ```gradle
   wait {
       services = ['web-app']
       timeout = duration(120, 'SECONDS')  // Increased from 30
       waitForStatus = 'RUNNING'  // Or use RUNNING instead of HEALTHY
   }
   ```

2. Verify health check in compose file is correct
3. Check service logs: `docker compose -p <project-name> logs <service-name>`

### Port Conflicts

**Symptom:** `docker compose up` fails with "port is already allocated"

**Solutions:**
1. Don't hardcode host ports in compose files - let Docker assign them:
   ```yaml
   ports:
     - "8080"  # Docker assigns random host port
   ```

2. Read actual port from state file:
   ```java
   int port = stateData.get("services").get("web-app").get("publishedPorts").get(0).get("host").asInt();
   ```

### Wrong Lifecycle Being Used

**Symptom:** Containers behave differently than expected

**Solutions:**
1. Verify annotation/extension matches desired lifecycle:
   - CLASS: `@ComposeUp(lifecycle = LifecycleMode.CLASS)` or `@ExtendWith(DockerComposeClassExtension.class)`
   - METHOD: `@ComposeUp(lifecycle = LifecycleMode.METHOD)` or `@ExtendWith(DockerComposeMethodExtension.class)`

2. Check variable scope:
   - CLASS: Use `@Shared` (Spock) or `static` (JUnit 5)
   - METHOD: Use instance variables (NOT @Shared/static)

3. Check setup/teardown methods:
   - CLASS: `setupSpec/cleanupSpec` (Spock) or `@BeforeAll/@AfterAll` (JUnit 5)
   - METHOD: `setup/cleanup` (Spock) or `@BeforeEach/@AfterEach` (JUnit 5)

## Best Practices

### 1. Choose the Right Lifecycle

**Use CLASS lifecycle when:**
- Tests build on each other (workflow testing)
- State persistence is acceptable or desired
- Performance is important
- Testing read-only operations

**Use METHOD lifecycle when:**
- Tests need complete isolation
- Each test needs a fresh database/state
- Tests are independent and idempotent
- Testing stateful mutations

### 2. Variable Scope Matters

**CLASS lifecycle:**
```groovy
@Shared
String baseUrl  // Spock

private static String baseUrl;  // JUnit 5
```

**METHOD lifecycle:**
```groovy
String baseUrl  // Spock (instance variable, NOT @Shared)

private String baseUrl;  // JUnit 5 (instance variable, NOT static)
```

### 3. Use Dynamic Port Assignment

Don't hardcode host ports - let Docker assign them:

```yaml
services:
  web:
    ports:
      - "8080"  # Docker assigns random available host port
```

Then read the actual port from the state file.

### 4. Configure Appropriate Timeouts

```gradle
wait {
    services = ['web-app']
    timeout = duration(60, 'SECONDS')  // Adjust based on startup time
    pollInterval = duration(2, 'SECONDS')
    waitForStatus = 'HEALTHY'  // Or 'RUNNING' for faster startup
}
```

### 5. Capture Logs for Debugging

```gradle
logs {
    outputFile = file("${buildDir}/compose-logs/web-app-test.log")
    services = ['web-app']  # Only services likely to have errors
    tailLines = 1000
}
```

### 6. Order Tests When State Matters

For CLASS lifecycle, order tests if they depend on each other:

**Spock:**
```groovy
@Stepwise  // Run tests in order
class WebAppIT extends Specification { }
```

**JUnit 5:**
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebAppJUnit5ClassIT {
    @Test
    @Order(1)
    void test1() { }

    @Test
    @Order(2)
    void test2() { }
}
```

### 7. Verify Isolation in METHOD Lifecycle

Prove isolation by testing that data does NOT persist:

```groovy
def "test 1: create user"() {
    // Create user alice
}

def "test 2: user should NOT exist (database is fresh)"() {
    // Verify alice does NOT exist (proves isolation)
}
```

### 8. Use Meaningful Project Names

```gradle
systemProperty 'docker.compose.project', "my-app-${rootProject.name}-test"
```

This helps identify containers in `docker ps` and prevents conflicts.

## Complete Examples

### Working Examples in Repository

**Spock Examples:**
- CLASS lifecycle: `plugin-integration-test/dockerOrch/examples/web-app/`
- METHOD lifecycle: `plugin-integration-test/dockerOrch/examples/isolated-tests/`
- Verification tests: `plugin-integration-test/dockerOrch/verification/lifecycle-class/`, `lifecycle-method/`

**JUnit 5 Examples:**
- CLASS lifecycle: `plugin-integration-test/dockerOrch/examples/web-app-junit/`
- METHOD lifecycle: `plugin-integration-test/dockerOrch/examples/isolated-tests-junit/`

### Running Examples

```bash
# From plugin-integration-test directory

# Run Spock CLASS lifecycle example
./gradlew :dockerOrch:examples:web-app:integrationTest

# Run Spock METHOD lifecycle example
./gradlew :dockerOrch:examples:isolated-tests:integrationTest

# Run JUnit 5 CLASS lifecycle example
./gradlew :dockerOrch:examples:web-app-junit:integrationTest

# Run JUnit 5 METHOD lifecycle example
./gradlew :dockerOrch:examples:isolated-tests-junit:integrationTest
```

## Additional Resources

- [dockerOrch DSL Usage Guide](usage-docker-orch.md) - Complete guide to Docker Compose orchestration
- [Gradle 9 and 10 Compatibility](gradle-9-and-10-compatibility-practices.md) - Best practices for Gradle 9/10
- [Spock Framework Documentation](https://spockframework.org/) - Official Spock docs
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/) - Official JUnit 5 docs
