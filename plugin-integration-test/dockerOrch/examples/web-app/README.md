# Example: Testing a Web Application with Spock and CLASS Lifecycle

**Type**: User-Facing Example
**Test Framework**: Spock
**Lifecycle**: CLASS (`@ComposeUp` annotation)
**Use Case**: REST API testing with dockerOrch plugin

## Purpose

Demonstrates how to test a Spring Boot REST API using the dockerOrch plugin with **Spock** test framework extension for CLASS lifecycle.

**📋 Copy and adapt this for your own projects!**

## Key Features

- ✅ Spock `@ComposeUp(lifecycle = LifecycleMode.CLASS)` for automatic container management
- ✅ Containers start once before all tests, stop once after all tests
- ✅ Fast execution - all tests share the same containers
- ✅ State persists between test methods
- ✅ Uses `@Shared` variables for test data

## Test Structure

```groovy
@ComposeUp(
    stackName = "webAppTest",
    composeFile = "src/integrationTest/resources/compose/web-app.yml",
    lifecycle = LifecycleMode.CLASS,
    waitForHealthy = ["web-app"],
    timeoutSeconds = 60,
    pollSeconds = 2
)
class WebAppExampleIT extends Specification {

    @Shared String baseUrl

    def setupSpec() {
        // Extension provides COMPOSE_STATE_FILE system property
        def stateData = new JsonSlurper().parse(new File(System.getProperty('COMPOSE_STATE_FILE')))

        // Get dynamically assigned port
        def port = stateData.services['web-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"

        RestAssured.baseURI = baseUrl
    }

    def "should respond to health check endpoint"() {
        when: "we call the /health endpoint"
        def response = given().get("/health")

        then: "we get 200 OK"
        response.statusCode() == 200

        and: "response indicates app is UP"
        response.jsonPath().getString("status") == "UP"
    }
}
```

## Configuration

### build.gradle

```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

dependencies {
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${plugin_version}"
    integrationTestImplementation libs.rest.assured
    integrationTestImplementation libs.groovy.all
    integrationTestImplementation libs.spock.core
}

tasks.named('integrationTest') {
    useJUnitPlatform()  // Spock uses JUnit Platform
}
```

## Running

From `plugin-integration-test/` directory:

```bash
./gradlew :dockerOrch:examples:web-app:integrationTest
```

## See Also

- [JUnit 5 CLASS Example](../web-app-junit/README.md) - Same app with JUnit 5
- [METHOD Lifecycle Example](../isolated-tests/README.md) - Spock with METHOD lifecycle
- [Stateful Testing Example](../stateful-web-app/README.md) - Tests that build on each other
- [Main Examples README](../README.md) - All examples
