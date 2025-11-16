# Example: Testing with JUnit 5 and METHOD Lifecycle

**Type**: User-Facing Example
**Test Framework**: JUnit 5
**Lifecycle**: METHOD (`@ExtendWith(DockerComposeMethodExtension.class)`)
**Use Case**: Integration testing with complete isolation between tests

## Purpose

Demonstrates how to test with **METHOD lifecycle** using **JUnit 5** for complete isolation - containers restart fresh for EACH test method.

**📋 Copy and adapt this for isolated testing scenarios!**

## Key Features

- ✅ JUnit 5 `@ExtendWith(DockerComposeMethodExtension.class)` for automatic container management
- ✅ Containers restart in `@BeforeEach` and stop in `@AfterEach` for EACH test
- ✅ Complete isolation - tests can run in any order
- ✅ Fresh database for every test method
- ✅ Data does NOT persist between tests (proves isolation)

## Why METHOD Lifecycle?

Use METHOD lifecycle when:
- Each test needs a completely clean database or state
- Tests must be independent and idempotent (can run in any order)
- Testing stateful operations that modify global state
- Proving isolation is critical

**Trade-off**: SLOWER than CLASS lifecycle but guarantees independence.

## Test Structure

```java
@ExtendWith(DockerComposeMethodExtension.class)
class IsolatedTestsJUnit5MethodIT {

    // Instance variables (NOT static) - fresh for each test
    private String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() throws IOException {
        // Extension provides COMPOSE_STATE_FILE system property
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
    @DisplayName("Test 1: Create user alice")
    void test1_createUserAlice() {
        // Create user "alice"
        given()
            .contentType("application/json")
            .body("{\"username\":\"alice\",\"email\":\"alice@example.com\"}")
            .post("/api/users")
            .then()
            .statusCode(201);
    }

    @Test
    @DisplayName("Test 2: Verify alice does NOT exist (fresh database)")
    void test2_verifyAliceDoesNotExist() {
        // Database is fresh - alice from test 1 does NOT exist
        given()
            .get("/api/users/alice")
            .then()
            .statusCode(404);  // NOT FOUND - proves isolation!
    }
}
```

## Configuration

```groovy
dependencies {
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${plugin_version}"
    integrationTestImplementation 'io.rest-assured:rest-assured:5.3.0'
    integrationTestImplementation 'com.fasterxml.jackson.core:jackson-databind:2.15.0'
    integrationTestImplementation 'org.junit.jupiter:junit-jupiter:5.9.3'
}

tasks.named('integrationTest') {
    useJUnitPlatform()
}
```

## Running

```bash
./gradlew :dockerOrch:examples:isolated-tests-junit:integrationTest
```

## See Also

- [Spock METHOD Example](../isolated-tests/README.md) - Same pattern with Spock
- [CLASS Lifecycle Example](../web-app-junit/README.md) - JUnit 5 with CLASS lifecycle
- [Main Examples README](../README.md) - All examples
