# Example Integration Tests

These demonstrate how to use the dockerOrch plugin for testing your applications with both **Spock** and **JUnit 5**
test framework extensions.

## Purpose

Example tests show **real-world usage** of the dockerOrch plugin:

- ✅ Test application business logic
- ✅ Validate API contracts
- ✅ Check database operations
- ✅ Test service-to-service communication
- ✅ Verify stateful workflows

## 📋 Copy and Adapt These!

**These examples are designed to be copied and adapted for your own projects.**

Each example includes:
- Full application source code (Spring Boot with REST API)
- Complete dockerOrch configuration
- Integration tests using standard libraries (RestAssured, Jackson)
- Detailed comments explaining the approach
- Both Spock and JUnit 5 variants

## Testing Approach

### Test Framework Extensions (PRIMARY)

All examples use **test framework extensions** (Spock or JUnit 5) for automatic container lifecycle management:

**Spock:**
- `@ComposeUp(lifecycle = LifecycleMode.CLASS)` - CLASS lifecycle
- `@ComposeUp(lifecycle = LifecycleMode.METHOD)` - METHOD lifecycle

**JUnit 5:**
- `@ExtendWith(DockerComposeClassExtension.class)` - CLASS lifecycle
- `@ExtendWith(DockerComposeMethodExtension.class)` - METHOD lifecycle

**Why Use Extensions:**
- ✅ Automatic container management (no manual task dependencies)
- ✅ Support CLASS and METHOD lifecycles
- ✅ Clean test code with minimal boilerplate
- ✅ Test framework controls setup/teardown timing

### Testing Libraries

Examples use **standard testing libraries** that real users would use:

- **RestAssured** - REST API testing
- **Jackson** - JSON parsing
- **Spock** or **JUnit 5** - Test framework

These are the same tools you would use in your own projects.

## Lifecycle Patterns

### CLASS Lifecycle

**Containers start once before all tests, stop once after all tests.**

**When to use:**
- Tests that build on each other (workflow: register → login → update)
- Read-only tests against the same data
- Performance matters and state isolation isn't critical

**Timing:**
- **Spock**: Containers start in `setupSpec()`, stop in `cleanupSpec()`
- **JUnit 5**: Containers start in `@BeforeAll`, stop in `@AfterAll`

### METHOD Lifecycle

**Containers restart fresh for each test method.**

**When to use:**
- Each test needs a completely clean database or state
- Tests must be independent and idempotent (can run in any order)
- Testing stateful operations that modify global state

**Timing:**
- **Spock**: Containers start in `setup()`, stop in `cleanup()` for EACH test
- **JUnit 5**: Containers start in `@BeforeEach`, stop in `@AfterEach` for EACH test

## Example Scenarios

### Web App (Spock + CLASS Lifecycle)

**Directory**: `web-app/`

**Use Case**: Testing REST API endpoints with Spock

**Lifecycle**: CLASS (containers shared across all tests)

**Stack**: Spring Boot + REST endpoints

**Tests**:
- Health check endpoint
- Greeting endpoint
- Root endpoint with version info
- User creation workflow

**Test Framework**: Spock with `@ComposeUp(lifecycle = LifecycleMode.CLASS)`

**Key Points**:
- Uses `@Shared` variables for test data
- Containers start once in `setupSpec()`
- All tests share the same containers
- Fast execution

**See**: [web-app/README.md](web-app/README.md)

---

### Web App (JUnit 5 + CLASS Lifecycle)

**Directory**: `web-app-junit/`

**Use Case**: Testing REST API endpoints with JUnit 5

**Lifecycle**: CLASS (containers shared across all tests)

**Stack**: Spring Boot + REST endpoints (same app as web-app)

**Tests**:
- Health check endpoint
- Greeting endpoint
- Root endpoint with version info
- User creation workflow

**Test Framework**: JUnit 5 with `@ExtendWith(DockerComposeClassExtension.class)`

**Key Points**:
- Uses `static` variables for test data
- Containers start once in `@BeforeAll`
- All tests share the same containers
- Fast execution

**See**: [web-app-junit/README.md](web-app-junit/README.md)

---

### Stateful Web App (Gradle Tasks + SUITE Lifecycle)

**Directory**: `stateful-web-app/`

**Use Case**: Testing stateful workflows with Gradle tasks (suite lifecycle)

**Lifecycle**: SUITE (containers run for entire test suite using Gradle tasks)

**Stack**: Spring Boot + Session management API

**Tests**:
- Register user account
- Login and receive sessionId
- Update profile using sessionId
- Get profile data
- Logout and invalidate session
- Verify session invalidation

**Test Framework**: Spock with Gradle tasks (`composeUp*`/`composeDown*`)

**Key Feature**: Demonstrates Gradle task approach for suite lifecycle. Tests build on each other, carrying state
(sessionId) from one test to the next.

**Use When**:
- You need suite lifecycle (containers run for entire test suite)
- Manual container management in CI/CD pipelines
- Custom orchestration logic

**See**: [stateful-web-app/README.md](stateful-web-app/README.md)

---

### Isolated Tests (Spock + METHOD Lifecycle)

**Directory**: `isolated-tests/`

**Use Case**: Testing with METHOD lifecycle for complete isolation (Spock)

**Lifecycle**: METHOD (containers restart for each test method)

**Stack**: Spring Boot + H2 in-memory database + JPA + User API

**Tests**:
- Create user "alice" in test 1
- Verify "alice" does NOT exist in test 2 (fresh database!)
- Create user "alice" again in test 3 (succeeds because database is fresh)
- Create user "bob" in test 4
- Verify "bob" does NOT exist in test 5 (isolation!)
- Verify empty database in test 6

**Test Framework**: Spock with `@ComposeUp(lifecycle = LifecycleMode.METHOD)`

**Key Feature**: Demonstrates **why METHOD lifecycle** is needed - containers restart for EACH test, ensuring complete
database isolation. Tests can run in any order.

**Key Points**:
- Uses instance variables (NOT `@Shared`)
- Containers restart for each test in `setup()`
- Complete isolation between tests
- Proves isolation (data does NOT persist)

**See**: [isolated-tests/README.md](isolated-tests/README.md)

---

### Isolated Tests (JUnit 5 + METHOD Lifecycle)

**Directory**: `isolated-tests-junit/`

**Use Case**: Testing with METHOD lifecycle for complete isolation (JUnit 5)

**Lifecycle**: METHOD (containers restart for each test method)

**Stack**: Spring Boot + H2 in-memory database + JPA + User API (same app as isolated-tests)

**Tests**:
- Create user "alice" in test 1
- Verify "alice" does NOT exist in test 2 (fresh database!)
- Create user "alice" again in test 3 (succeeds because database is fresh)
- Create user "bob" in test 4
- Verify "bob" does NOT exist in test 5 (isolation!)
- Verify empty database in test 6
- Health check with fresh database

**Test Framework**: JUnit 5 with `@ExtendWith(DockerComposeMethodExtension.class)`

**Key Feature**: Demonstrates **why METHOD lifecycle** is needed - containers restart for EACH test, ensuring complete
database isolation. Tests can run in any order.

**Key Points**:
- Uses instance variables (NOT `static`)
- Containers restart for each test in `@BeforeEach`
- Complete isolation between tests
- Proves isolation (data does NOT persist)

**See**: [isolated-tests-junit/README.md](isolated-tests-junit/README.md)

---

## Running Examples

From `plugin-integration-test/` directory:

```bash
# Run all examples
./gradlew dockerOrch:examples:integrationTest

# Spock examples
./gradlew :dockerOrch:examples:web-app:integrationTest
./gradlew :dockerOrch:examples:stateful-web-app:integrationTest
./gradlew :dockerOrch:examples:isolated-tests:integrationTest

# JUnit 5 examples
./gradlew :dockerOrch:examples:web-app-junit:integrationTest
./gradlew :dockerOrch:examples:isolated-tests-junit:integrationTest
```

## Example Test Structure

### Spock (CLASS Lifecycle)

```groovy
import com.kineticfire.gradle.docker.spock.ComposeUp
import com.kineticfire.gradle.docker.spock.LifecycleMode
import spock.lang.Specification
import spock.lang.Shared
import groovy.json.JsonSlurper
import io.restassured.RestAssured

@ComposeUp(lifecycle = LifecycleMode.CLASS)
class WebAppIT extends Specification {

    @Shared
    String baseUrl

    def setupSpec() {
        // Extension provides COMPOSE_STATE_FILE system property
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)

        // Get dynamically assigned port
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
}
```

### JUnit 5 (CLASS Lifecycle)

```java
import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DockerComposeClassExtension.class)
class WebAppJUnit5ClassIT {

    private static String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setupAll() throws IOException {
        // Extension provides COMPOSE_STATE_FILE system property
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        File stateFile = new File(stateFilePath);
        JsonNode stateData = objectMapper.readTree(stateFile);

        // Get dynamically assigned port
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
    @DisplayName("Should respond to health check endpoint")
    void shouldRespondToHealthCheckEndpoint() {
        Response response = given().get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("UP", response.jsonPath().getString("status"));
    }
}
```

## Key Characteristics

1. **Uses test framework extensions** - Spock `@ComposeUp` or JUnit 5 `@ExtendWith`
2. **Automatic lifecycle management** - No manual task dependencies needed
3. **Uses standard libraries** - RestAssured, Jackson, etc.
4. **Tests application logic** - Not plugin mechanics
5. **Real-world scenarios** - Patterns users actually need
6. **Copy-paste ready** - Detailed comments and explanations
7. **For users** - Shows how to use the plugin effectively

## How to Adapt for Your Project

1. **Choose the example closest to your use case**
   - CLASS lifecycle: web-app (Spock) or web-app-junit (JUnit 5)
   - METHOD lifecycle: isolated-tests (Spock) or isolated-tests-junit (JUnit 5)
   - Gradle tasks: stateful-web-app

2. **Copy the directory structure** (app/, app-image/, build files)

3. **Replace the application** with your own code

4. **Update Docker Compose file** with your service definitions

5. **Modify the tests** to validate your business logic

6. **Adjust wait configurations** based on your startup times

## Common Patterns

### Reading Port Mapping (Groovy/Spock)

```groovy
def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
def stateData = new JsonSlurper().parse(stateFile)
def port = stateData.services['service-name'].publishedPorts[0].host
def baseUrl = "http://localhost:${port}"
```

### Reading Port Mapping (Java/JUnit 5)

```java
String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
File stateFile = new File(stateFilePath);
JsonNode stateData = new ObjectMapper().readTree(stateFile);

int port = stateData.get("services")
    .get("service-name")
    .get("publishedPorts")
    .get(0)
    .get("host")
    .asInt();

String baseUrl = "http://localhost:" + port;
```

### dockerOrch Configuration

```gradle
dockerOrch {
    stacks {
        myStack {
            projectName = 'my-app-test'
            stackName = 'myStack'
            composeFiles = [file('src/integrationTest/resources/compose/my-app.yml')]

            wait {
                services = ['my-app']
                timeout = duration(60, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }

            logs {
                outputFile = file("${buildDir}/compose-logs/my-app-test.log")
                tailLines = 1000
            }
        }
    }
}
```

### Test Task Configuration (Spock)

```gradle
tasks.register('integrationTest', Test) {
    description = 'Runs integration tests using Spock extension'
    group = 'verification'

    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath

    useJUnitPlatform()

    // Required for extension
    systemProperty 'docker.compose.stack', 'myStack'
    systemProperty 'docker.compose.project', 'my-app-test'

    // Not cacheable - interacts with Docker
    outputs.cacheIf { false }
}
```

### Test Task Configuration (JUnit 5)

Same as Spock - both use `useJUnitPlatform()`.

---

## Additional Resources

- **[Spock and JUnit Test Extensions Guide](../../docs/usage/spock-junit-test-extensions.md)** - Comprehensive guide
  to test framework extensions
- **[dockerOrch DSL Usage Guide](../../docs/usage/usage-docker-orch.md)** - Complete guide to Docker Compose
  orchestration
- **[Plugin Verification Tests](../verification/README.md)** - For plugin mechanics validation

---

**Copy these examples and adapt them for your own projects!**
