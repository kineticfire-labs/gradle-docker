# Example Integration Tests

These demonstrate how to use the dockerOrch plugin for testing your applications with both **Spock** and **JUnit 5**
test framework extensions.

## ⭐ Recommended Configuration Pattern

**All examples in this directory use the RECOMMENDED pattern for Docker Compose configuration:**

### 3-Step Approach

1. **Configure stack in `build.gradle`** → Define your Docker Compose stack in the `dockerOrch.composeStacks { }` DSL
2. **Use `usesCompose()` in test task** → Pass stack configuration to tests via system properties
3. **Use zero-parameter annotation in test** → `@ComposeUp` (Spock) or `@ExtendWith(Extension.class)` (JUnit 5) with
   no parameters

### Example Configuration

**build.gradle:**

```gradle
dockerOrch {
    composeStacks {
        webAppTest {
            projectName = 'example-web-app-test'
            stackName = 'webAppTest'
            composeFiles = [file('src/integrationTest/resources/compose/web-app.yml')]

            wait {
                services = ['web-app']
                timeout = duration(60, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "webAppTest", lifecycle: "class")
    useJUnitPlatform()
    outputs.cacheIf { false }
}
```

**Spock Test:**

```groovy
@ComposeUp  // No parameters! All configuration from build.gradle
class WebAppExampleIT extends Specification {
    // Tests...
}
```

**JUnit 5 Test:**

```java
@ExtendWith(DockerComposeClassExtension.class)  // No parameters! Configuration from build.gradle
class WebAppJUnit5ClassIT {
    // Tests...
}
```

### Benefits of This Pattern

- ✅ **Single source of truth** - All configuration lives in `build.gradle`
- ✅ **No duplication** - Configuration is not repeated in test annotations
- ✅ **Easy to share** - Multiple test classes can reference the same stack configuration
- ✅ **CLI/CI overrides** - Easy to override stack configuration via Gradle properties
- ✅ **Clean test code** - Test classes focus on test logic, not infrastructure configuration

**Note:** All examples demonstrate this pattern. The `@ComposeUp` and `@ExtendWith` annotations in example tests use
zero parameters, reading all configuration from the `build.gradle` file via `usesCompose()`.

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

## Supported Lifecycle Types

The plugin supports two container lifecycle modes for integration testing:

1. **METHOD Lifecycle** - Containers restart for each test method
   - Complete isolation between tests
   - Fresh state for every test method
   - Slower but guarantees independence
   - Use when: Each test needs clean database/state, tests must run in any order

2. **CLASS Lifecycle** - Containers start once for all tests in a class
   - State persists between test methods
   - Faster execution (containers start once)
   - Tests can share data and build on each other
   - Use when: Testing workflows, read-only operations, performance matters

## Decision Guide

### When to Use Test Framework Extensions (Recommended)

**Use `@ComposeUp` (Spock) or `@ExtendWith(DockerComposeExtension)` (JUnit 5) when:**
- ✅ You want automatic container lifecycle management
- ✅ You're writing standard integration tests
- ✅ You don't need custom orchestration logic
- ✅ You prefer declarative configuration over imperative tasks

**Benefits:**
- Automatic cleanup (even if tests fail)
- Minimal boilerplate
- Clear lifecycle semantics (CLASS or METHOD)
- No manual task dependency wiring

**All examples in this directory use test framework extensions.**

### When to Use Gradle Tasks (Advanced)

**Use `composeUp*`/`composeDown*` Gradle tasks when:**
- Manual container control in CI/CD pipelines
- Custom orchestration beyond test lifecycle
- Need to run containers outside of test execution
- Complex multi-stack scenarios

**Trade-offs:**
- More verbose configuration
- Manual task dependency management
- Must handle cleanup explicitly

### Choosing CLASS vs METHOD Lifecycle

| Scenario | Lifecycle | Why |
|----------|-----------|-----|
| Read-only API tests | **CLASS** | Containers start once, all tests read the same data |
| Database CRUD tests (each test modifies) | **METHOD** | Each test needs fresh database |
| Workflow tests (register → login → update) | **CLASS** | Tests build on each other, share state |
| Isolated unit-like integration tests | **METHOD** | Complete independence, can run in any order |
| Performance-critical test suites | **CLASS** | Faster - containers start once |
| Testing idempotency | **METHOD** | Prove tests work in any order |

### Choosing Spock vs JUnit 5

Both frameworks work identically with this plugin. Choose based on your preference:

**Spock:**
- Groovy-based, expressive BDD syntax
- Use `@ComposeUp(lifecycle = LifecycleMode.CLASS/METHOD)`
- Uses `@Shared` for shared state
- `setupSpec()`/`cleanupSpec()` for CLASS, `setup()`/`cleanup()` for METHOD

**JUnit 5:**
- Java-based, standard Java testing
- Use `@ExtendWith(DockerComposeClassExtension.class)` or `DockerComposeMethodExtension.class`
- Uses `static` variables for shared state
- `@BeforeAll`/`@AfterAll` for CLASS, `@BeforeEach`/`@AfterEach` for METHOD

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

### Database App (Spock + CLASS + Multi-Service)

**Directory**: `database-app/`

**Use Case**: Database integration testing with PostgreSQL

**Lifecycle**: CLASS (containers shared across all tests)

**Stack**: Spring Boot + JPA + PostgreSQL (multi-service)

**Tests**:
- Health check endpoint
- Create user via REST API and verify in database
- Retrieve user via REST API
- Update user and verify in database
- Delete user and verify removed from database

**Test Framework**: Spock with `@ComposeUp(lifecycle = LifecycleMode.CLASS)`

**Key Features**:
- **Multi-service orchestration** (app + PostgreSQL database)
- **Dual validation** - tests both REST API responses AND direct database state
- **Direct database access** with Groovy SQL for verification
- Demonstrates testing data persistence and CRUD operations
- Uses `waitForHealthy` for both services

**Key Points**:
- Uses `@Shared` variables for database connection details
- Direct JDBC validation using `groovy.sql.Sql`
- Verifies API behavior matches database state
- Shows how to test app + database integration

**See**: [database-app/README.md](database-app/README.md)

---

### Stateful Web App (Spock + CLASS with Stateful Testing)

**Directory**: `stateful-web-app/`

**Use Case**: Testing stateful workflows where tests build on each other

**Lifecycle**: CLASS (containers shared across all tests)

**Stack**: Spring Boot + Session management API

**Tests**:
- Register user account
- Login and receive sessionId
- Update profile using sessionId
- Get profile data
- Logout and invalidate session
- Verify session invalidation

**Test Framework**: Spock with `@ComposeUp(lifecycle = LifecycleMode.CLASS)`

**Key Feature**: Demonstrates CLASS lifecycle with stateful testing patterns. Tests intentionally build on each other,
carrying state (sessionId) from one test to the next.

**Use When**:
- Testing workflows where steps depend on each other (register → login → update → logout)
- State persistence between test methods is desired
- Testing session management or multi-step processes

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

**⚠️ Important**: All commands must be run from `plugin-integration-test/` directory.

### Run All Examples

```bash
./gradlew dockerOrch:examples:integrationTest
```

**Expected Result**: All 6 example test suites pass. No lingering containers remain.

### Run by Lifecycle Type

**CLASS Lifecycle Examples** (faster, containers start once):
```bash
# Spock + CLASS
./gradlew :dockerOrch:examples:web-app:integrationTest           # Basic REST API
./gradlew :dockerOrch:examples:database-app:integrationTest      # Multi-service + PostgreSQL
./gradlew :dockerOrch:examples:stateful-web-app:integrationTest  # Stateful workflows

# JUnit 5 + CLASS
./gradlew :dockerOrch:examples:web-app-junit:integrationTest     # Basic REST API
```

**METHOD Lifecycle Examples** (complete isolation, containers restart per test):
```bash
# Spock + METHOD
./gradlew :dockerOrch:examples:isolated-tests:integrationTest    # Proves isolation

# JUnit 5 + METHOD
./gradlew :dockerOrch:examples:isolated-tests-junit:integrationTest  # Proves isolation
```

### Run by Test Framework

**Spock Examples:**
```bash
./gradlew :dockerOrch:examples:web-app:integrationTest
./gradlew :dockerOrch:examples:database-app:integrationTest
./gradlew :dockerOrch:examples:stateful-web-app:integrationTest
./gradlew :dockerOrch:examples:isolated-tests:integrationTest
```

**JUnit 5 Examples:**
```bash
./gradlew :dockerOrch:examples:web-app-junit:integrationTest
./gradlew :dockerOrch:examples:isolated-tests-junit:integrationTest
```

### Expected Test Results

Each example should:
- ✅ Build Docker image successfully
- ✅ Start containers and wait for healthy status
- ✅ Run all tests (passing)
- ✅ Stop containers automatically
- ✅ Leave no containers running (`docker ps -a` shows none)

### Verify No Lingering Containers

After running examples:
```bash
docker ps -a
# Should show no containers from examples (project names: example-*)
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

## Adding New Examples

**Consistency Checklist**: When adding a new example, ensure the following for consistency:

### Documentation Requirements

- [ ] **Main README updated** (`README.md` in this directory):
  - [ ] Add new example section with description, directory, use case, lifecycle, stack, tests, and key features
  - [ ] Include "See:" link to individual README
  - [ ] Update "Running Examples" section with new commands
  - [ ] Update "Run All Examples" expected count
  - [ ] Verify "Run by Lifecycle Type" and "Run by Test Framework" sections include new example

- [ ] **Individual README created** (`<example-name>/README.md`):
  - [ ] Add breadcrumb navigation at top (linking back to main README)
  - [ ] Document example type, test framework, lifecycle, and use case
  - [ ] Include "Purpose" section explaining what the example demonstrates
  - [ ] Include "Key Features" bulleted list
  - [ ] Include "Test Structure" with code example showing annotation usage
  - [ ] Include "Configuration" section with dependencies
  - [ ] Include "Running" section with `./gradlew` command
  - [ ] Include "See Also" section with cross-references to related examples
  - [ ] Follow 120-character line length limit

- [ ] **Code Quality**:
  - [ ] Test class uses `@ComposeUp` (Spock) or `@ExtendWith` (JUnit 5) annotation
  - [ ] Test includes comprehensive comments explaining the approach
  - [ ] Test includes "Why this pattern?" explanation
  - [ ] Build file includes plugin dependency: `integrationTestImplementation "com.kineticfire.gradle:..."`
  - [ ] Compose file does not include deprecated `version` field
  - [ ] All code follows 120-character line length limit

### Validation Requirements

- [ ] **Test execution**:
  - [ ] Run: `./gradlew :dockerOrch:examples:<example-name>:integrationTest`
  - [ ] Verify all tests pass
  - [ ] Verify no lingering containers: `docker ps -a` shows no containers after completion
  - [ ] Verify expected output matches documentation

- [ ] **Consistency verification**:
  - [ ] Main README description matches actual test code implementation
  - [ ] Individual README code examples match actual test code
  - [ ] Lifecycle mode in annotation matches documented lifecycle
  - [ ] Test framework (Spock/JUnit 5) matches documented framework
  - [ ] All cross-references and links work correctly

### Review Process

Before committing a new example:

1. **Self-review**: Check all items in this checklist
2. **Test locally**: Run the example and verify output
3. **Verify documentation**: Ensure main README, individual README, and code all align
4. **Check cross-references**: Verify all links and references are accurate
5. **Validate format**: Ensure all files follow project standards (120 chars, spaces not tabs)

---

**Copy these examples and adapt them for your own projects!**
