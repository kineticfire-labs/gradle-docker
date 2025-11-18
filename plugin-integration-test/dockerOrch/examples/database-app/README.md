[← Back to Examples](../README.md)

# Example: Database Integration Testing with PostgreSQL

**Type**: User-Facing Example
**Test Framework**: Spock
**Lifecycle**: CLASS (`@ComposeUp` annotation)
**Use Case**: Multi-service integration testing (app + database)

## Configuration Approach

This example uses the **RECOMMENDED pattern** where Docker Compose configuration is defined in `build.gradle` and
passed to the test via `usesCompose()`, with a zero-parameter `@ComposeUp` annotation in the test class.

**Why this matters for database testing:**
- **Single source of truth** - Database connection details, wait configurations, and service orchestration are all
  defined in one place
- **Easy multi-service coordination** - Configure both app and database services together in `build.gradle`
- **Reusable across tests** - Multiple test classes can share the same database stack configuration
- **Environment flexibility** - Easy to swap database versions or configurations via Gradle properties

**Configuration highlights from `build.gradle`:**

```gradle
dockerOrch {
    composeStacks {
        databaseAppTest {
            stackName = 'databaseAppTest'
            composeFiles = [file('src/integrationTest/resources/compose/database-app.yml')]

            wait {
                services = ['app', 'postgres']  // Wait for BOTH services
                timeout = duration(90, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "databaseAppTest", lifecycle: "class")
}
```

## Purpose

Demonstrates real-world database integration testing with:
- ✅ Spring Boot + JPA + PostgreSQL
- ✅ Multi-service Docker Compose orchestration
- ✅ REST API testing with RestAssured
- ✅ **Direct database validation with JDBC/Groovy SQL**
- ✅ Dual verification (API responses + database state)

**📋 Copy and adapt this for your database integration testing!**

## Key Features

This example is unique because it shows:
1. **Multi-service orchestration** - app container + PostgreSQL container
2. **Dual validation** - verify both API behavior AND database state
3. **Direct database access** - use Groovy SQL to query PostgreSQL directly
4. **Complete CRUD workflow** - create, read, update, delete operations

## Test Structure

```groovy
@ComposeUp  // No parameters! All configuration from build.gradle via usesCompose()
class DatabaseAppExampleIT extends Specification {

    @Shared String baseUrl
    @Shared String dbUrl
    @Shared String dbUser = "testuser"
    @Shared String dbPass = "testpass"

    def setupSpec() {
        // Read port mappings for both app and database
        def stateData = new JsonSlurper().parse(new File(System.getProperty('COMPOSE_STATE_FILE')))

        def appPort = stateData.services['app'].publishedPorts[0].host
        baseUrl = "http://localhost:${appPort}"

        def dbPort = stateData.services['postgres'].publishedPorts[0].host
        dbUrl = "jdbc:postgresql://localhost:${dbPort}/testdb"

        RestAssured.baseURI = baseUrl
    }

    def "create user via REST API and verify in database"() {
        when: "create user via API"
        def response = given()
            .contentType("application/json")
            .body('{"username":"alice","email":"alice@example.com","fullName":"Alice Smith"}')
            .post("/api/users")
            .then()
            .statusCode(201)
            .extract().response()

        def userId = response.path("id")

        then: "user exists in database"
        Sql.withInstance(dbUrl, dbUser, dbPass, "org.postgresql.Driver") { sql ->
            def rows = sql.rows("SELECT * FROM users WHERE username = 'alice'")
            assert rows.size() == 1
            assert rows[0].email == "alice@example.com"
            assert rows[0].full_name == "Alice Smith"
        }
    }
}
```

## Running the Example

From `plugin-integration-test/` directory:

```bash
# Run the complete test
./gradlew :dockerOrch:examples:database-app:integrationTest

# Build Docker image manually
./gradlew :dockerOrch:examples:database-app:app-image:dockerBuildDatabaseApp

# Clean all artifacts
./gradlew :dockerOrch:examples:database-app:clean
```

## Docker Compose Configuration

```yaml
services:
  app:
    image: example-database-app:1.0.0
    ports:
      - "8080"  # Random host port
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: testdb
      DB_USER: testuser
      DB_PASSWORD: testpass
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 10

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: testuser
      POSTGRES_PASSWORD: testpass
    ports:
      - "5432"  # Random host port
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U testuser -d testdb"]
      interval: 5s
      timeout: 3s
      retries: 5
```

## Dependencies

```groovy
dependencies {
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${plugin_version}"
    integrationTestImplementation libs.rest.assured
    integrationTestImplementation libs.postgresql  // PostgreSQL JDBC driver
}
```

## Test Cases

1. **Health check** - Verify app responds correctly
2. **Create user** - POST via API, verify in database with SELECT
3. **Retrieve user** - GET via API, verify response data
4. **Update user** - PUT via API, verify change in database
5. **Delete user** - DELETE via API, verify removal from database

## Adapting for Your Project

1. **Replace the database** - Swap PostgreSQL for MySQL, MongoDB, etc.
2. **Update connection details** - Change dbUrl, credentials, driver
3. **Modify compose file** - Add your database service configuration
4. **Write your tests** - Validate your API + database interactions

## See Also

**Related Examples:**
- [Web App Example](../web-app/README.md) - Basic CLASS lifecycle pattern
- [Stateful Web App Example](../stateful-web-app/README.md) - Session management workflow
- [Isolated Tests Example](../isolated-tests/README.md) - METHOD lifecycle with database

**Documentation:**
- [Main Examples README](../README.md) - All examples and decision guide
- [dockerOrch DSL Usage Guide](../../../../docs/usage/usage-docker-orch.md) - Complete DSL reference
- [Spock and JUnit Test Extensions](../../../../docs/usage/spock-junit-test-extensions.md) - Extension details
