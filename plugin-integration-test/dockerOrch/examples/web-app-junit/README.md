[← Back to Examples](../README.md)

# Example: Testing with JUnit 5 and CLASS Lifecycle

**Type**: User-Facing Example
**Test Framework**: JUnit 5
**Lifecycle**: CLASS (`@ExtendWith(DockerComposeClassExtension.class)`)
**Use Case**: REST API testing with JUnit 5

## Purpose

Demonstrates how to test a Spring Boot REST API using the dockerOrch plugin with **JUnit 5** test framework extension for CLASS lifecycle.

**📋 Copy and adapt this for your JUnit 5 projects!**

## Key Features

- ✅ JUnit 5 `@ExtendWith(DockerComposeClassExtension.class)` for automatic container management
- ✅ Containers start once in `@BeforeAll`, stop once in `@AfterAll`
- ✅ All tests share the same containers (fast execution)
- ✅ State persists between test methods
- ✅ Uses static variables for shared data

## Test Structure

```java
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

## Configuration

### build.gradle

```groovy
plugins {
    id 'java'
    id 'com.kineticfire.gradle.docker'
}

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

### resources/application-test.properties

```properties
docker.compose.stack=webAppTest
docker.compose.project=example-web-app-junit-test
```

## Running

```bash
./gradlew :dockerOrch:examples:web-app-junit:integrationTest
```

## See Also

**Related Examples:**
- [Spock CLASS Example](../web-app/README.md) - Same app with Spock
- [METHOD Lifecycle Example](../isolated-tests-junit/README.md) - JUnit 5 with METHOD lifecycle
- [Database Integration Example](../database-app/README.md) - Multi-service with PostgreSQL

**Documentation:**
- [Main Examples README](../README.md) - All examples and decision guide
- [dockerOrch DSL Usage Guide](../../../../docs/usage/usage-docker-orch.md) - Complete DSL reference
- [Spock and JUnit Test Extensions](../../../../docs/usage/spock-junit-test-extensions.md) - Extension details
