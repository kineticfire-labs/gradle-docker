# Example: Testing a Web Application with JUnit 5 and Docker Compose

**Type**: User-Facing Example
**Test Framework**: JUnit 5
**Lifecycle**: CLASS (`@BeforeAll`/`@AfterAll`)
**Use Case**: REST API testing with dockerOrch plugin and JUnit 5 extension

## Purpose

This example demonstrates how to test a Spring Boot REST API using the dockerOrch plugin with **JUnit 5 test framework
extensions**. It shows:

- ✅ How to use `@ExtendWith(DockerComposeClassExtension.class)` for automatic container management
- ✅ How to configure dockerOrch DSL
- ✅ How to read port mappings from state files (Jackson/JSON parsing)
- ✅ How to test REST endpoints with RestAssured
- ✅ How to handle dynamic port assignment

**📋 Copy and adapt this for your own projects!**

## Test Framework Extension

This example uses the **JUnit 5 extension** for automatic container lifecycle management:

```java
@ExtendWith(DockerComposeClassExtension.class)
class WebAppJUnit5ClassIT {
    // Extension automatically starts/stops containers
}
```

**Why use the extension?**
- ✅ Automatic container management (no manual Gradle task dependencies needed)
- ✅ Clean test code with minimal boilerplate
- ✅ Test framework controls setup/teardown timing
- ✅ Containers start in `@BeforeAll`, stop in `@AfterAll`

**Alternative approaches**:
- Gradle tasks (`composeUp*`/`composeDown*`) - for suite lifecycle or manual control
- Spock extension (`@ComposeUp`) - if using Spock instead of JUnit 5

## Test Lifecycle

**Lifecycle Type**: `CLASS`

The extension manages Docker Compose automatically:
- **`@BeforeAll`** - Extension starts containers, waits for healthy, generates state file
- **Test methods run** - All tests share the same containers
- **`@AfterAll`** - Extension stops containers and captures logs

Your test code reads the state file to get port mappings:
```java
@BeforeAll
static void setupAll() throws IOException {
    String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
    File stateFile = new File(stateFilePath);
    JsonNode stateData = new ObjectMapper().readTree(stateFile);

    int port = stateData.get("services")
        .get("web-app")
        .get("publishedPorts")
        .get(0)
        .get("host")
        .asInt();

    baseUrl = "http://localhost:" + port;
    RestAssured.baseURI = baseUrl;
}
```

**Why CLASS lifecycle?**
- Containers start once, tests run multiple times (efficient)
- Mirrors real-world usage: start environment, run test suite, tear down
- Reduces test execution time (no container restarts between tests)
- Good for tests that build on each other or don't modify state

## Running Tests

**⚠️ Important**: All commands must be run from `plugin-integration-test/` directory.

### Quick Start

```bash
# From plugin-integration-test/ directory
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:examples:web-app-junit:app-image:integrationTest
```

This runs the complete workflow:
1. Build app JAR (`bootJar`)
2. Build Docker image (`dockerBuildWebApp`)
3. Extension starts Docker Compose automatically in `@BeforeAll`
4. Extension waits for container to be HEALTHY
5. Run integration tests (`integrationTest`)
6. Extension stops Docker Compose automatically in `@AfterAll`

### Individual Tasks

```bash
# Clean build artifacts
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:examples:web-app-junit:clean

# Build the Spring Boot app
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:examples:web-app-junit:app:bootJar

# Build Docker image
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:examples:web-app-junit:app-image:dockerBuildWebApp

# Run tests (extension manages Docker Compose automatically)
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:examples:web-app-junit:app-image:integrationTest
```

## Plugin Configuration

### build.gradle (dockerOrch DSL)

```gradle
dockerOrch {
    stacks {
        webAppTest {
            // Compose file location
            projectName = 'web-app-test'
            stackName = 'webAppTest'
            composeFiles = [file('src/integrationTest/resources/compose/web-app.yml')]

            // Wait for app to be healthy before running tests
            wait {
                services = ['web-app']
                timeout = duration(30, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }

            // Capture logs when containers stop
            logs {
                outputFile = file("${buildDir}/compose-logs/web-app-test.log")
                tailLines = 1000
            }
        }
    }
}

// Register integration test task
tasks.register('integrationTest', Test) {
    description = 'Runs CLASS lifecycle integration tests using JUnit 5 extension'
    group = 'verification'

    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath

    useJUnitPlatform()

    // Configure system properties for JUnit extension
    systemProperty 'docker.compose.stack', 'webAppTest'
    systemProperty 'docker.compose.project', 'example-web-app-junit-test'

    // NOTE: No systemProperty for COMPOSE_STATE_FILE needed - extension generates it
    // NOTE: No dependsOn/finalizedBy needed - extension manages container lifecycle

    outputs.cacheIf { false }
}
```

**Key Configuration Points:**
- `projectName` - Unique name for this stack (prevents conflicts)
- `stackName` - Must match system property `docker.compose.stack`
- `composeFiles` - Path to Docker Compose file
- `wait` - Block until container health check passes
- `systemProperty` - Required for extension to find stack configuration
- **No `dependsOn composeUp*` or `finalizedBy composeDown*` needed** - extension handles this

### Docker Compose File

```yaml
# src/integrationTest/resources/compose/web-app.yml
services:
  web-app:
    image: example-web-app:latest
    ports:
      - "8080"  # Random host port assigned
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 10s
```

**Important Notes:**
- Don't include `version:` (deprecated)
- Use random host ports (`"8080"`) to avoid conflicts
- Include health check for `waitForStatus = 'HEALTHY'` to work
- `start_period` gives app time to initialize

## Test Structure

### Complete Test Class

```java
package com.kineticfire.test;

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

        // Extract published port (Docker assigns random port)
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
}
```

**Key Points:**
- Use `static` variables for data shared across all tests
- Extension provides `COMPOSE_STATE_FILE` system property automatically
- Use Jackson `ObjectMapper` to parse JSON state file
- Use RestAssured for REST API testing
- Use `@Order` annotations to control test execution order

### Reading Port Mapping (Jackson)

```java
// Extension provides COMPOSE_STATE_FILE system property
String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
File stateFile = new File(stateFilePath);
JsonNode stateData = new ObjectMapper().readTree(stateFile);

// Extract published port (Docker assigns random port)
int port = stateData.get("services")
    .get("web-app")
    .get("publishedPorts")
    .get(0)
    .get("host")
    .asInt();

String baseUrl = "http://localhost:" + port;
```

**Why read from state file?**
- Docker Compose assigns random host ports to avoid conflicts
- State file contains actual port mapping
- Extension generates state file automatically during `@BeforeAll`

### Testing REST Endpoints

```java
@Test
@DisplayName("Should respond to health check endpoint")
void shouldRespondToHealthCheckEndpoint() {
    Response response = given().get("/health");

    assertEquals(200, response.statusCode());
    assertEquals("UP", response.jsonPath().getString("status"));
}

@Test
@DisplayName("Should return app information from root endpoint")
void shouldReturnAppInformation() {
    Response response = given().get("/");

    assertEquals(200, response.statusCode());
    assertEquals("Web App is running", response.jsonPath().getString("message"));
    assertEquals("1.0.0", response.jsonPath().getString("version"));
}
```

**RestAssured Benefits:**
- Fluent API for REST testing
- Built-in JSON path support
- Standard tool in Java ecosystem
- Easy to read and maintain

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

**Key Dependencies:**
- `junit-jupiter-api` - JUnit 5 API
- `gradle-docker` - Plugin with extension classes
- `rest-assured` - REST API testing
- `jackson-databind` - JSON parsing for state files

## Expected Results

All tests should pass:
- ✅ Health check endpoint returns 200 OK with "UP" status
- ✅ Greeting endpoint returns "Hello, World!"
- ✅ Root endpoint returns app information
- ✅ User creation workflow succeeds

## Adapting for Your Project

### 1. Copy Directory Structure

```
your-project/
├── app/                      # Your application
│   ├── src/main/java/       # Application code
│   └── build.gradle         # App build config
└── app-image/               # Docker + integration tests
    ├── src/
    │   ├── main/docker/     # Dockerfile, entrypoint.sh
    │   └── integrationTest/
    │       ├── java/        # Test code (JUnit 5)
    │       └── resources/
    │           └── compose/ # Docker Compose file
    └── build.gradle         # Plugin config
```

### 2. Update build.gradle

```gradle
dockerOrch {
    stacks {
        myAppTest {
            projectName = 'my-app-test'
            stackName = 'myAppTest'  // Must match system property
            composeFiles = [file('src/integrationTest/resources/compose/my-app.yml')]

            wait {
                services = ['my-service']
                timeout = duration(60, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }
        }
    }
}

tasks.register('integrationTest', Test) {
    // ... source sets configuration

    useJUnitPlatform()

    systemProperty 'docker.compose.stack', 'myAppTest'
    systemProperty 'docker.compose.project', 'my-app-test'

    outputs.cacheIf { false }
}
```

### 3. Write Your Tests

```java
import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;

@ExtendWith(DockerComposeClassExtension.class)
class MyAppIT {

    private static String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setupAll() throws IOException {
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
    void shouldTestYourBusinessLogic() {
        // Your tests here
    }
}
```

## Troubleshooting

**Container fails health check**:
- Check health check command works inside container: `docker exec <container> curl localhost:8080/health`
- Increase `timeout` in wait configuration
- Check application logs: `docker logs <container>`

**Tests can't connect to app**:
- Verify `COMPOSE_STATE_FILE` system property is set (extension sets this automatically)
- Check that stack name matches: `systemProperty 'docker.compose.stack', 'webAppTest'`
- Ensure dockerOrch DSL has matching stack configuration

**Extension not starting containers**:
- Verify `@ExtendWith(DockerComposeClassExtension.class)` annotation is present
- Check system properties are configured in test task
- Verify dockerOrch DSL has stack with matching name

**Jackson errors (JSON parsing)**:
- Add dependency: `implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'`

## Comparison with Spock

This example uses **JUnit 5**. For the **Spock** version, see: `../web-app/README.md`

**Key Differences:**
- **JUnit 5**: `@ExtendWith(DockerComposeClassExtension.class)` + `@BeforeAll`/`@AfterAll` + `static` variables
- **Spock**: `@ComposeUp(lifecycle = LifecycleMode.CLASS)` + `setupSpec()`/`cleanupSpec()` + `@Shared` variables

Both use the same dockerOrch DSL configuration and produce identical test behavior.

## See Also

- **[Spock and JUnit Test Extensions Guide](../../../docs/usage/spock-junit-test-extensions.md)** - Complete guide
- **[dockerOrch DSL Usage Guide](../../../docs/usage/usage-docker-orch.md)** - Plugin documentation
- **Spock version**: `../web-app/README.md` - Same example with Spock
- **METHOD lifecycle**: `../isolated-tests-junit/README.md` - Fresh containers for each test
