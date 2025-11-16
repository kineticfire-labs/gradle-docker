# Example: Testing Stateful Workflows with CLASS Lifecycle

**Type**: User-Facing Example
**Test Framework**: Spock
**Lifecycle**: CLASS (`@ComposeUp` annotation)
**Use Case**: Session management and stateful workflow testing

## Purpose

Demonstrates **CLASS lifecycle with stateful testing patterns** - tests that intentionally build on each other, carrying state between methods.

**📋 Copy and adapt this for your stateful testing scenarios!**

## Key Features

- ✅ Spock `@ComposeUp(lifecycle = LifecycleMode.CLASS)` for automatic container management
- ✅ Tests build on each other (register → login → update → logout)
- ✅ State (sessionId) carried across test methods
- ✅ Fast execution - containers start once
- ✅ Demonstrates workflow testing patterns

## When to Use This Pattern

Use CLASS lifecycle with stateful testing when:
- Testing workflows where steps depend on each other
- Carrying state (like sessionId, authToken) between test methods is desired
- Testing session management or multi-step processes
- Performance matters and complete isolation isn't required

## Test Structure

```groovy
@ComposeUp(
    stackName = "statefulWebAppTest",
    composeFile = "src/integrationTest/resources/compose/stateful-web-app.yml",
    lifecycle = LifecycleMode.CLASS,
    waitForHealthy = ["stateful-web-app"],
    timeoutSeconds = 60,
    pollSeconds = 2
)
class StatefulWebAppExampleIT extends Specification {

    @Shared String baseUrl
    static String sessionId  // State carried across tests
    @Shared String username = "alice"
    @Shared String password = "secret123"

    def setupSpec() {
        // Extension provides COMPOSE_STATE_FILE system property
        def stateData = new JsonSlurper().parse(new File(System.getProperty('COMPOSE_STATE_FILE')))

        def port = stateData.services['stateful-web-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"

        RestAssured.baseURI = baseUrl
    }

    def "step 1: should register a new user account"() {
        when: "we register a new user"
        def response = given()
            .contentType("application/json")
            .body("{\"username\":\"${username}\",\"password\":\"${password}\"}")
            .post("/api/register")

        then: "registration succeeds (or user already exists)"
        response.statusCode() in [200, 409]  // Idempotent design
    }

    def "step 2: should login and receive a sessionId"() {
        when: "we login"
        def response = given()
            .contentType("application/json")
            .body("{\"username\":\"${username}\",\"password\":\"${password}\"}")
            .post("/api/login")

        then: "we get a sessionId"
        response.statusCode() == 200
        sessionId = response.jsonPath().getString("sessionId")
        sessionId != null
    }

    def "step 3: should update profile using sessionId"() {
        when: "we update profile"
        given()
            .header("Session-Id", sessionId)
            .contentType("application/json")
            .body('{"email":"alice@example.com"}')
            .put("/api/profile")
            .then()
            .statusCode(200)
    }

    def "step 4: should logout and invalidate session"() {
        when: "we logout"
        given()
            .header("Session-Id", sessionId)
            .post("/api/logout")
            .then()
            .statusCode(200)
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
./gradlew :dockerOrch:examples:stateful-web-app:integrationTest
```

## See Also

- [CLASS Lifecycle Without State](../web-app/README.md) - Tests that don't share state
- [METHOD Lifecycle Example](../isolated-tests/README.md) - Complete isolation between tests
- [Main Examples README](../README.md) - All examples
