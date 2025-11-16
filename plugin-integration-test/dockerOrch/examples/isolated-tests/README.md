# Example: Testing with Spock and METHOD Lifecycle

**Type**: User-Facing Example
**Test Framework**: Spock
**Lifecycle**: METHOD (`@ComposeUp` annotation)
**Use Case**: Integration testing with complete isolation between tests

## Purpose

Demonstrates how to test with **METHOD lifecycle** using **Spock** for complete isolation - containers restart fresh for EACH test method.

**📋 Copy and adapt this for isolated testing scenarios!**

## Key Features

- ✅ Spock `@ComposeUp(lifecycle = LifecycleMode.METHOD)` for automatic container management
- ✅ Containers restart in `setup()` and stop in `cleanup()` for EACH test
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

```groovy
@ComposeUp(
    stackName = "isolatedTestsTest",
    composeFile = "src/integrationTest/resources/compose/isolated-tests.yml",
    lifecycle = LifecycleMode.METHOD,
    waitForHealthy = ["isolated-tests"],
    timeoutSeconds = 60,
    pollSeconds = 2
)
class IsolatedTestsExampleIT extends Specification {

    // Instance variables (NOT @Shared) - fresh for each test
    String baseUrl

    def setup() {
        // Extension provides COMPOSE_STATE_FILE system property
        def stateData = new JsonSlurper().parse(new File(System.getProperty('COMPOSE_STATE_FILE')))

        def port = stateData.services['isolated-tests'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"

        RestAssured.baseURI = baseUrl
    }

    def "test 1: create user alice"() {
        when: "we create user alice"
        given()
            .contentType("application/json")
            .body('{"username":"alice","email":"alice@example.com"}')
            .post("/api/users")
            .then()
            .statusCode(201)
    }

    def "test 2: verify alice does NOT exist (fresh database)"() {
        when: "we look for alice"
        def response = given().get("/api/users/alice")

        then: "alice does NOT exist - database is fresh"
        response.statusCode() == 404  // NOT FOUND - proves isolation!
    }
}
```

## Configuration

```groovy
dependencies {
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${plugin_version}"
    integrationTestImplementation libs.rest.assured
    integrationTestImplementation libs.groovy.all
    integrationTestImplementation libs.spock.core
}

tasks.named('integrationTest') {
    useJUnitPlatform()
}
```

## Running

```bash
./gradlew :dockerOrch:examples:isolated-tests:integrationTest
```

## See Also

- [JUnit 5 METHOD Example](../isolated-tests-junit/README.md) - Same pattern with JUnit 5
- [CLASS Lifecycle Example](../web-app/README.md) - Spock with CLASS lifecycle
- [Main Examples README](../README.md) - All examples
