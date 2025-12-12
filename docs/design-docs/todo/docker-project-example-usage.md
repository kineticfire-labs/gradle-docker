# dockerProject DSL Example Usage

This document provides complete examples of the `dockerProject` DSL test configuration for both class and method lifecycles, including the DSL configuration, Docker Compose files, and test files in both Groovy (Spock) and Java (JUnit 5).

---

## Class Lifecycle (Default) - Container shared across all tests

In class lifecycle mode, the container starts **once** before all tests and stops **after** all tests complete. This is the default behavior and is ideal for stateless/read-only tests.

**Flow**: `composeUp → test A → test B → test C → composeDown`

### 1. DSL Configuration (`build.gradle`)

```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
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

// No lifecycle specified = defaults to CLASS lifecycle
dockerProject {
    image {
        name.set('my-app')
        tags.set(['latest', '1.0.0'])
        jarFrom.set(':app:jar')
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        timeoutSeconds.set(60)
        testTaskName.set('integrationTest')
        // lifecycle.set(Lifecycle.CLASS)  // Default - can be omitted
    }

    onSuccess {
        additionalTags.set(['tested'])
    }
}

// Integration test dependencies
dependencies {
    integrationTestImplementation libs.rest.assured
    integrationTestImplementation libs.rest.assured.json.path
}

// Configure integration test task
tasks.named('integrationTest') {
    systemProperty 'COMPOSE_PROJECT_NAME', 'my-app-test'
    systemProperty 'IMAGE_NAME', 'my-app'
}

// Task orchestration for CLASS lifecycle
afterEvaluate {
    tasks.named('composeUpMyappTest') {
        dependsOn tasks.named('dockerBuildMyapp')
    }

    // CLASS lifecycle: composeUp → all tests → composeDown
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpMyappTest')
        finalizedBy tasks.named('composeDownMyappTest')
    }
}
```

### 2. Docker Compose File (`src/integrationTest/resources/compose/app.yml`)

```yaml
services:
  app:
    image: my-app:latest
    ports:
      - "9300:8080"
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 5
      start_period: 10s
```

### 3. Groovy Spock Test (`src/integrationTest/groovy/com/example/test/AppClassLifecycleIT.groovy`)

```groovy
package com.example.test

import io.restassured.RestAssured
import spock.lang.Specification

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * CLASS Lifecycle Test - No annotation needed!
 *
 * Container starts ONCE before all tests, stops AFTER all tests.
 * Flow: composeUp → test A → test B → test C → composeDown
 */
class AppClassLifecycleIT extends Specification {

    def setupSpec() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9300
    }

    def cleanupSpec() {
        // Optional: Force cleanup in case tests fail
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME', 'my-app-test')
        try {
            def process = ['docker', 'compose', '-p', projectName, 'down', '-v'].execute()
            process.waitFor()
        } catch (Exception e) {
            println "Warning: Cleanup failed: ${e.message}"
        }
    }

    def "health endpoint returns healthy status"() {
        expect:
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
    }

    def "time endpoint returns current time"() {
        expect:
        given()
            .when()
            .get("/time")
            .then()
            .statusCode(200)
            .body("time", notNullValue())
            .body("timezone", equalTo("UTC"))
    }

    def "echo endpoint echoes message"() {
        expect:
        given()
            .queryParam("msg", "hello-world")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo("hello-world"))
    }

    def "metrics endpoint returns uptime"() {
        expect:
        given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .body("uptime", greaterThanOrEqualTo(0))
            .body("requests", greaterThanOrEqualTo(0))
    }
}
```

### 4. Java JUnit 5 Test (`src/integrationTest/java/com/example/test/AppClassLifecycleIT.java`)

```java
package com.example.test;

import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * CLASS Lifecycle Test - No annotation needed!
 *
 * Container starts ONCE before all tests, stops AFTER all tests.
 * Flow: composeUp → test A → test B → test C → composeDown
 */
class AppClassLifecycleIT {

    @BeforeAll
    static void setupSpec() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 9300;
    }

    @AfterAll
    static void cleanupSpec() {
        // Optional: Force cleanup in case tests fail
        String projectName = System.getProperty("COMPOSE_PROJECT_NAME", "my-app-test");
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-p", projectName, "down", "-v");
            pb.start().waitFor();
        } catch (Exception e) {
            System.out.println("Warning: Cleanup failed: " + e.getMessage());
        }
    }

    @Test
    void healthEndpointReturnsHealthyStatus() {
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"));
    }

    @Test
    void timeEndpointReturnsCurrentTime() {
        given()
            .when()
            .get("/time")
            .then()
            .statusCode(200)
            .body("time", notNullValue())
            .body("timezone", equalTo("UTC"));
    }

    @Test
    void echoEndpointEchoesMessage() {
        given()
            .queryParam("msg", "hello-world")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo("hello-world"));
    }

    @Test
    void metricsEndpointReturnsUptime() {
        given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .body("uptime", greaterThanOrEqualTo(0))
            .body("requests", greaterThanOrEqualTo(0));
    }
}
```

---

## Method Lifecycle - Fresh container for each test

In method lifecycle mode, the container starts **fresh** before each test method and stops **after** each test method. This provides complete test isolation and is ideal for state-mutating tests.

**Flow**: `composeUp → test A → composeDown → composeUp → test B → composeDown → ...`

### 1. DSL Configuration (`build.gradle`)

```groovy
import com.kineticfire.gradle.docker.Lifecycle

plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
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

dockerProject {
    image {
        name.set('my-app')
        tags.set(['latest', '1.0.0'])
        jarFrom.set(':app:jar')
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        timeoutSeconds.set(60)
        testTaskName.set('integrationTest')
        lifecycle.set(Lifecycle.METHOD)  // KEY: Fresh container per test
    }

    onSuccess {
        additionalTags.set(['tested'])
    }
}

// Integration test dependencies - includes plugin for @ComposeUp annotation
dependencies {
    integrationTestImplementation libs.groovy.all
    integrationTestImplementation libs.spock.core
    integrationTestImplementation libs.rest.assured
    integrationTestImplementation libs.rest.assured.json.path
    integrationTestRuntimeOnly libs.junit.platform.launcher

    // Add plugin classes for @ComposeUp annotation access
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${project.findProperty('plugin_version')}"
}

// Configure integration test task
afterEvaluate {
    tasks.named('integrationTest') {
        // CRITICAL: METHOD lifecycle requires sequential test execution
        // to avoid port conflicts when containers are started per method
        maxParallelForks = 1

        systemProperty 'COMPOSE_PROJECT_NAME', 'my-app-test'
        systemProperty 'IMAGE_NAME', 'my-app'
    }
}

// Task orchestration for METHOD lifecycle
afterEvaluate {
    // For METHOD lifecycle, the test framework handles compose lifecycle
    // We only need the image built before tests run
    tasks.named('integrationTest') {
        dependsOn tasks.named('dockerBuildMyapp')
    }

    // Ensure pipeline has all required dependencies
    tasks.named('runMyappPipeline') {
        dependsOn tasks.named('prepareMyappContext')
        dependsOn tasks.named('integrationTestClasses')
    }
}
```

### 2. Docker Compose File (`src/integrationTest/resources/compose/app.yml`)

```yaml
# Same compose file works for both lifecycles
services:
  app:
    image: my-app:latest
    ports:
      - "9300:8080"
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 5
      start_period: 10s
```

### 3. Groovy Spock Test (`src/integrationTest/groovy/com/example/test/AppMethodLifecycleIT.groovy`)

```groovy
package com.example.test

import com.kineticfire.gradle.docker.spock.ComposeUp
import io.restassured.RestAssured
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * METHOD Lifecycle Test - Uses @ComposeUp annotation
 *
 * Container starts FRESH before each test, stops after each test.
 * Flow: composeUp → test A → composeDown → composeUp → test B → composeDown → ...
 *
 * Key: The @ComposeUp annotation reads system properties set by dockerProject DSL
 * and handles compose lifecycle per test method.
 */
@ComposeUp  // Required for METHOD lifecycle - reads config from system properties
@Stepwise   // Optional: ensures tests run in order (useful for verification)
class AppMethodLifecycleIT extends Specification {

    @Shared
    String firstTestStartTime = null

    @Shared
    String secondTestStartTime = null

    def setupSpec() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9300

        println "=== METHOD Lifecycle Test ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "Container lifecycle: METHOD (fresh container for each test)"
    }

    def "first test records container start time"() {
        when: "we wait for container to be ready and get metrics"
        sleep(2000)  // Give container time to start and become healthy

        def response = given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
            .extract()
            .response()
        println "First test: Health check passed"

        def metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response()

        firstTestStartTime = metricsResponse.jsonPath().getString("startTime")
        def requestCount = metricsResponse.jsonPath().getInt("requests")
        println "First test: Container start time: ${firstTestStartTime}"
        println "First test: Request count: ${requestCount}"

        then: "container is fresh (low request count)"
        firstTestStartTime != null
        // Fresh container should have low request count (health checks + our calls)
        requestCount <= 5
    }

    def "second test gets a DIFFERENT container - proving isolation"() {
        when: "we wait for the new container and get metrics"
        sleep(2000)  // Give container time to start and become healthy

        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
        println "Second test: Health check passed"

        def metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response()

        secondTestStartTime = metricsResponse.jsonPath().getString("startTime")
        def requestCount = metricsResponse.jsonPath().getInt("requests")
        println "Second test: Container start time: ${secondTestStartTime}"
        println "Second test: Request count: ${requestCount}"
        println "Second test: First test start time was: ${firstTestStartTime}"

        then: "this is a DIFFERENT container (different start time)"
        secondTestStartTime != null
        // KEY ASSERTION: Start time should be DIFFERENT, proving fresh container per method
        secondTestStartTime != firstTestStartTime
        println "SUCCESS: Different container instance (method lifecycle verified)"

        and: "request count is low (fresh container, no persisted state)"
        requestCount <= 5
    }

    def "third test also gets a fresh container"() {
        when: "we wait for yet another new container"
        sleep(2000)  // Give container time to start and become healthy

        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
        println "Third test: Health check passed"

        def metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response()

        def thirdTestStartTime = metricsResponse.jsonPath().getString("startTime")
        def requestCount = metricsResponse.jsonPath().getInt("requests")
        println "Third test: Container start time: ${thirdTestStartTime}"
        println "Third test: Request count: ${requestCount}"

        then: "this is a DIFFERENT container from both previous tests"
        thirdTestStartTime != null
        thirdTestStartTime != firstTestStartTime
        thirdTestStartTime != secondTestStartTime
        println "SUCCESS: Third container also fresh (different from first and second)"

        and: "request count is low (fresh container)"
        requestCount <= 5
    }
}
```

### 4. Java JUnit 5 Test (`src/integrationTest/java/com/example/test/AppMethodLifecycleIT.java`)

```java
package com.example.test;

import com.kineticfire.gradle.docker.junit.ComposeUp;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * METHOD Lifecycle Test - Uses @ComposeUp annotation
 *
 * Container starts FRESH before each test, stops after each test.
 * Flow: composeUp → test A → composeDown → composeUp → test B → composeDown → ...
 *
 * Key: The @ComposeUp annotation reads system properties set by dockerProject DSL
 * and handles compose lifecycle per test method.
 */
@ComposeUp  // Required for METHOD lifecycle - reads config from system properties
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)  // Ensure ordered execution
class AppMethodLifecycleIT {

    private static String firstTestStartTime;
    private static String secondTestStartTime;

    @BeforeAll
    static void setupSpec() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 9300;

        System.out.println("=== METHOD Lifecycle Test ===");
        System.out.println("Testing endpoint: " + RestAssured.baseURI + ":" + RestAssured.port);
        System.out.println("Container lifecycle: METHOD (fresh container for each test)");
    }

    @Test
    @Order(1)
    void firstTestRecordsContainerStartTime() throws InterruptedException {
        Thread.sleep(2000);  // Give container time to start and become healthy

        // Health check
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"));
        System.out.println("First test: Health check passed");

        // Get metrics
        Response metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response();

        firstTestStartTime = metricsResponse.jsonPath().getString("startTime");
        int requestCount = metricsResponse.jsonPath().getInt("requests");
        System.out.println("First test: Container start time: " + firstTestStartTime);
        System.out.println("First test: Request count: " + requestCount);

        // Assertions
        assertNotNull(firstTestStartTime);
        assertTrue(requestCount <= 5, "Fresh container should have low request count");
    }

    @Test
    @Order(2)
    void secondTestGetsDifferentContainerProvingIsolation() throws InterruptedException {
        Thread.sleep(2000);  // Give container time to start and become healthy

        // Health check
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"));
        System.out.println("Second test: Health check passed");

        // Get metrics
        Response metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response();

        secondTestStartTime = metricsResponse.jsonPath().getString("startTime");
        int requestCount = metricsResponse.jsonPath().getInt("requests");
        System.out.println("Second test: Container start time: " + secondTestStartTime);
        System.out.println("Second test: Request count: " + requestCount);
        System.out.println("Second test: First test start time was: " + firstTestStartTime);

        // KEY: Proves fresh container - different start time
        assertNotNull(secondTestStartTime);
        assertNotEquals(firstTestStartTime, secondTestStartTime,
            "Container should have different start time (fresh instance)");
        System.out.println("SUCCESS: Different container instance (method lifecycle verified)");

        // Fresh state, no accumulated requests
        assertTrue(requestCount <= 5, "Fresh container should have low request count");
    }

    @Test
    @Order(3)
    void thirdTestAlsoGetsFreshContainer() throws InterruptedException {
        Thread.sleep(2000);  // Give container time to start and become healthy

        // Health check
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"));
        System.out.println("Third test: Health check passed");

        // Get metrics
        Response metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String thirdTestStartTime = metricsResponse.jsonPath().getString("startTime");
        int requestCount = metricsResponse.jsonPath().getInt("requests");
        System.out.println("Third test: Container start time: " + thirdTestStartTime);
        System.out.println("Third test: Request count: " + requestCount);

        // Assertions - different from both previous tests
        assertNotNull(thirdTestStartTime);
        assertNotEquals(firstTestStartTime, thirdTestStartTime);
        assertNotEquals(secondTestStartTime, thirdTestStartTime);
        System.out.println("SUCCESS: Third container also fresh (different from first and second)");

        assertTrue(requestCount <= 5, "Fresh container should have low request count");
    }
}
```

---

## Summary Comparison

| Aspect | Class Lifecycle | Method Lifecycle |
|--------|----------------|------------------|
| **DSL** | `lifecycle.set(Lifecycle.CLASS)` or omit (default) | `lifecycle.set(Lifecycle.METHOD)` |
| **Annotation** | **None required** | `@ComposeUp` required |
| **Task wiring** | `dependsOn composeUp`, `finalizedBy composeDown` | Only `dependsOn dockerBuild`, test framework manages compose |
| **Container lifecycle** | One container for all tests | Fresh container per test |
| **Test parallelism** | Any | `maxParallelForks = 1` (required) |
| **Speed** | Faster | Slower (container restarts) |
| **Test isolation** | Shared state | Complete isolation |
| **Use case** | Read-only/stateless tests | State-mutating tests |

---

## When to Use Each Lifecycle

### Use Class Lifecycle (default) when:
- Tests are read-only and don't modify container state
- Tests query APIs without side effects
- Speed is important (one container startup vs many)
- Tests don't depend on fresh state

### Use Method Lifecycle when:
- Tests modify database records
- Tests change cache state
- Tests need to verify initial container state
- Complete test isolation is required
- You need to verify container restart behavior

---

## Key Notes

1. **Import for Lifecycle enum**: When using method lifecycle, import the enum:
   ```groovy
   import com.kineticfire.gradle.docker.Lifecycle
   ```

2. **Plugin dependency for @ComposeUp**: Method lifecycle tests need access to the plugin's annotation classes:
   ```groovy
   integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${project.findProperty('plugin_version')}"
   ```

3. **Sequential execution for method lifecycle**: Always set `maxParallelForks = 1` to avoid port conflicts when containers restart per test.

4. **Compose file is the same**: The Docker Compose file works identically for both lifecycles - the difference is in how the plugin manages the compose lifecycle.
