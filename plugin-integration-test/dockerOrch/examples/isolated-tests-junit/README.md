# Example: Testing with METHOD Lifecycle for Complete Isolation (JUnit 5)

**Type**: User-Facing Example
**Test Framework**: JUnit 5
**Lifecycle**: METHOD (`@BeforeEach`/`@AfterEach`)
**Use Case**: Integration testing with complete isolation between tests

## Purpose

This example demonstrates how to test with **METHOD lifecycle** using **JUnit 5 test framework extensions**. This
provides **complete isolation** between test methods by restarting containers fresh for each test.

**Key Demonstration:**
- ✅ Containers restart for EACH test method (fresh database every time)
- ✅ Tests can run in any order (complete independence)
- ✅ Data does NOT persist between tests (proves isolation)
- ✅ How to use `@ExtendWith(DockerComposeMethodExtension.class)`
- ✅ How to configure instance variables (NOT `static`)
- ✅ How to test database operations with guaranteed isolation

**📋 Copy and adapt this for your own projects!**

## When to Use METHOD Lifecycle

**Use METHOD lifecycle when:**
- Each test needs a completely clean database or state
- Tests must be independent and idempotent (can run in any order)
- Testing stateful operations that modify global state
- You need to prove data does NOT persist between tests

**Don't use METHOD lifecycle when:**
- Tests build on each other (workflow testing) - use CLASS lifecycle instead
- Performance is critical and isolation isn't needed - use CLASS lifecycle instead
- Testing read-only operations - use CLASS lifecycle instead

**Trade-off**: SLOWER than CLASS lifecycle (containers restart for each test) but guarantees isolation.

## Test Framework Extension

This example uses the **JUnit 5 METHOD extension** for automatic per-test container management:

```java
@ExtendWith(DockerComposeMethodExtension.class)
class IsolatedTestsJUnit5MethodIT {
    // Containers restart for EACH test method
}
```

**Why use the METHOD extension?**
- ✅ Automatic container restart for each test (complete isolation)
- ✅ Clean test code with minimal boilerplate
- ✅ Test framework controls setup/teardown timing
- ✅ Containers start in `@BeforeEach`, stop in `@AfterEach`

**Alternative approaches**:
- CLASS extension (`@ExtendWith(DockerComposeClassExtension.class)`) - for shared containers
- Spock METHOD extension (`@ComposeUp(lifecycle = LifecycleMode.METHOD)`) - if using Spock

## Test Lifecycle

**Lifecycle Type**: `METHOD`

The extension manages Docker Compose for EACH test method:

1. **`@BeforeEach`** (BEFORE EACH TEST):
   - Extension starts fresh containers
   - Extension waits for healthy
   - Extension generates new state file
   - Your code reads state file and extracts port

2. **Test method runs** - One test method executes with fresh containers

3. **`@AfterEach`** (AFTER EACH TEST):
   - Extension captures logs
   - Extension stops containers

4. **Repeat for next test** - Steps 1-3 repeat for each test method

**Critical Points:**
- Use **instance variables** (NOT `static`) - fresh for each test
- Use **`@BeforeEach`** (NOT `@BeforeAll`) - runs for each test
- Each test gets a **fresh database** with no data from previous tests

## Demonstrating Isolation

This example **proves** containers restart by showing data does NOT persist:

```java
@Test
@Order(1)
void test1_shouldCreateUserAliceWithFreshDatabase() {
    // Create user "alice" with ID 1
}

@Test
@Order(2)
void test2_shouldNotFindAlice() {
    // Verify "alice" does NOT exist (proves containers restarted!)
}

@Test
@Order(3)
void test3_shouldCreateUserAliceAgainWithFreshDatabase() {
    // Create user "alice" again - succeeds with ID 1 (proves fresh database!)
}
```

The fact that test 2 can't find alice (created in test 1) **proves** the database restarted fresh.

## Running Tests

**⚠️ Important**: All commands must be run from `plugin-integration-test/` directory.

### Quick Start

```bash
# From plugin-integration-test/ directory
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:examples:isolated-tests-junit:app-image:integrationTest
```

This runs the complete workflow:
1. Build app JAR (`bootJar`)
2. Build Docker image (`dockerBuildIsolatedTests`)
3. For each test method:
   - Extension starts fresh containers in `@BeforeEach`
   - Extension waits for HEALTHY
   - Test runs
   - Extension stops containers in `@AfterEach`

**Note**: This is slower than CLASS lifecycle because containers restart for each test, but guarantees isolation.

### Individual Tasks

```bash
# Build Docker image
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:examples:isolated-tests-junit:app-image:dockerBuildIsolatedTests

# Run tests (extension manages Docker Compose automatically)
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:examples:isolated-tests-junit:app-image:integrationTest
```

## Plugin Configuration

### build.gradle (dockerOrch DSL)

```gradle
dockerOrch {
    stacks {
        isolatedTestsTest {
            projectName = 'isolated-tests-test'
            stackName = 'isolatedTestsTest'
            composeFiles = [file('src/integrationTest/resources/compose/isolated-tests.yml')]

            wait {
                services = ['isolated-tests']
                timeout = duration(30, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }

            logs {
                outputFile = file("${buildDir}/compose-logs/isolated-tests-test.log")
                tailLines = 1000
            }
        }
    }
}

tasks.register('integrationTest', Test) {
    description = 'Runs METHOD lifecycle integration tests using JUnit 5 extension'
    group = 'verification'

    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath

    useJUnitPlatform()

    // Configure system properties for JUnit extension
    systemProperty 'docker.compose.stack', 'isolatedTestsTest'
    systemProperty 'docker.compose.project', 'example-isolated-tests-junit-test'

    // NOTE: No systemProperty for COMPOSE_STATE_FILE needed - extension generates it
    // NOTE: No dependsOn/finalizedBy needed - extension manages container lifecycle

    outputs.cacheIf { false }
}
```

**Key Configuration Points:**
- `stackName` - Must match system property `docker.compose.stack`
- `systemProperty` - Required for extension to find stack configuration
- **No `dependsOn` or `finalizedBy` needed** - extension handles lifecycle automatically

## Test Structure

### Complete Test Class

```java
package com.kineticfire.test;

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

@ExtendWith(DockerComposeMethodExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IsolatedTestsJUnit5MethodIT {

    // Instance variables (NOT static!) - fresh for each test
    private String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        System.out.println("=== Test " + cleanupCallCount + " completed, containers stopping ===");
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

**Critical Points:**
- `private String baseUrl` - **Instance variable** (NOT `static`), fresh for each test
- `@BeforeEach` - Runs **before EACH test** (NOT `@BeforeAll`)
- `@AfterEach` - Runs **after EACH test** (NOT `@AfterAll`)
- Each test gets a fresh `COMPOSE_STATE_FILE` system property

### Why Instance Variables?

```java
// CORRECT for METHOD lifecycle
private String baseUrl;  // Fresh for each test

// WRONG for METHOD lifecycle
private static String baseUrl;  // Shared across tests (use for CLASS lifecycle)
```

METHOD lifecycle requires instance variables because:
- Each test method gets a new class instance
- Instance variables are re-initialized for each test
- Static variables would persist across tests (wrong for METHOD lifecycle)

### Reading State File (Jackson)

```java
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
```

**Key Points:**
- Extension generates **new state file** for each test
- Must read in `@BeforeEach` (NOT `@BeforeAll`)
- Port may be different for each test (Docker assigns random ports)

## Demonstrating Isolation

### Test 1: Create User Alice

```java
@Test
@Order(1)
void test1_shouldCreateUserAliceWithFreshDatabase() {
    Response response = given()
        .contentType("application/json")
        .body("{\"username\":\"alice\",\"email\":\"alice@example.com\"}")
        .post("/users");

    assertEquals(200, response.statusCode());
    assertEquals(1L, response.jsonPath().getLong("id"));  // ID is 1 (fresh database)
}
```

### Test 2: Verify Alice Does NOT Exist

```java
@Test
@Order(2)
void test2_shouldNotFindAlice() {
    Response response = given().get("/users/alice");

    // Alice should NOT exist because database restarted fresh!
    assertEquals(404, response.statusCode(), "User should NOT exist (database is fresh)");
}
```

**This proves isolation!** If containers didn't restart, alice would still exist from test 1.

### Test 3: Create Alice Again

```java
@Test
@Order(3)
void test3_shouldCreateUserAliceAgainWithFreshDatabase() {
    Response response = given()
        .contentType("application/json")
        .body("{\"username\":\"alice\",\"email\":\"alice@example.com\"}")
        .post("/users");

    assertEquals(200, response.statusCode());
    // ID is 1 AGAIN because database is fresh!
    assertEquals(1L, response.jsonPath().getLong("id"), "ID is 1 again because database is fresh!");
}
```

**This proves fresh database!** If database persisted, creating alice would fail (duplicate) or get ID 2.

## Application Code

### Spring Boot Application with User API

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepository userRepository;

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);
    }

    @GetMapping("/{username}")
    public ResponseEntity<User> getUser(@PathVariable String username) {
        return userRepository.findByUsername(username)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
```

### In-Memory H2 Database

Uses H2 in-memory database that resets when container restarts:

```yaml
# application.properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
```

This ensures a fresh database for each test.

## Dependencies

```gradle
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
```

## Expected Results

All tests should pass, demonstrating isolation:
- ✅ Test 1: Create user "alice" with ID 1
- ✅ Test 2: "alice" does NOT exist (proves containers restarted!)
- ✅ Test 3: Create "alice" again with ID 1 (proves fresh database!)
- ✅ Test 4: Create user "bob" with ID 1
- ✅ Test 5: "bob" does NOT exist (proves isolation!)
- ✅ Test 6: Database is empty (no users from previous tests)
- ✅ Test 7: Health check succeeds with userCount = 0

## Adapting for Your Project

### 1. Choose the Right Lifecycle

**Use METHOD lifecycle when:**
- Each test needs a completely clean state
- Tests must be independent (can run in any order)
- Testing database operations that modify state

**Use CLASS lifecycle instead when:**
- Tests build on each other (workflow testing)
- Performance is critical
- Testing read-only operations

### 2. Use Instance Variables (NOT static)

```java
// CORRECT for METHOD lifecycle
private String baseUrl;  // Fresh for each test

// WRONG for METHOD lifecycle
private static String baseUrl;  // Would persist across tests
```

### 3. Use @BeforeEach (NOT @BeforeAll)

```java
// CORRECT for METHOD lifecycle
@BeforeEach
void setupEach() throws IOException {
    // Read state file for EACH test
}

// WRONG for METHOD lifecycle
@BeforeAll
static void setupAll() throws IOException {
    // Only runs once for all tests
}
```

### 4. Write Your Tests

```java
import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;

@ExtendWith(DockerComposeMethodExtension.class)
class MyIsolatedTestsIT {

    private String baseUrl;  // Instance variable
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setupEach() throws IOException {
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        File stateFile = new File(stateFilePath);
        JsonNode stateData = objectMapper.readTree(stateFile);

        int port = stateData.get("services")
            .get("my-service")
            .get("publishedPorts")
            .get(0)
            .get("host")
            .asInt();

        baseUrl = "http://localhost:" + port;
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void test1_shouldCreateData() {
        // Create some data
    }

    @Test
    void test2_shouldNotFindData() {
        // Verify data does NOT exist (proves isolation)
    }
}
```

## Troubleshooting

**Tests are slow**:
- Expected! METHOD lifecycle restarts containers for each test
- Consider CLASS lifecycle if isolation isn't critical
- Optimize health check settings in compose file

**Extension not starting containers**:
- Verify `@ExtendWith(DockerComposeMethodExtension.class)` annotation is present
- Check system properties are configured in test task
- Ensure using `@BeforeEach` (NOT `@BeforeAll`)

**Variables not fresh for each test**:
- Ensure variables are instance variables (NOT `static`)
- Verify using `@BeforeEach` (NOT `@BeforeAll`)

**Data persists between tests**:
- Check that `@ExtendWith(DockerComposeMethodExtension.class)` is used (not `DockerComposeClassExtension`)
- Verify logs show containers starting/stopping for each test

## Comparison with Spock

This example uses **JUnit 5 METHOD**. For the **Spock METHOD** version, see: `../isolated-tests/README.md`

**Key Differences:**
- **JUnit 5**: `@ExtendWith(DockerComposeMethodExtension.class)` + `@BeforeEach`/`@AfterEach` + instance variables
- **Spock**: `@ComposeUp(lifecycle = LifecycleMode.METHOD)` + `setup()`/`cleanup()` + instance variables (NOT `@Shared`)

Both produce identical test behavior - containers restart for each test.

## Comparison with CLASS Lifecycle

For **CLASS lifecycle** (containers shared across tests), see: `../web-app-junit/README.md`

**Key Differences:**
- **METHOD**: Containers restart for EACH test (`@BeforeEach`/`@AfterEach`)
- **CLASS**: Containers start once for ALL tests (`@BeforeAll`/`@AfterAll`)

**Trade-offs:**
- METHOD: Slower but guarantees isolation
- CLASS: Faster but state persists between tests

## See Also

- **[Spock and JUnit Test Extensions Guide](../../../docs/usage/spock-junit-test-extensions.md)** - Complete guide
- **[dockerOrch DSL Usage Guide](../../../docs/usage/usage-docker-orch.md)** - Plugin documentation
- **Spock METHOD version**: `../isolated-tests/README.md` - Same example with Spock
- **JUnit 5 CLASS lifecycle**: `../web-app-junit/README.md` - Shared containers
