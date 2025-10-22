# Verification Test: Mixed Wait Strategies (waitForHealthy + waitForRunning)

**Type**: Plugin Mechanics Validation
**Lifecycle**: CLASS (setupSpec/cleanupSpec)
**Purpose**: Validates both wait mechanisms together in realistic multi-service orchestration

## What This Test Validates

This verification test ensures the dockerOrch plugin correctly:
- ✅ Handles BOTH `waitForHealthy` AND `waitForRunning` in the same Docker Compose stack
- ✅ Waits for app service to become HEALTHY (has health check configured)
- ✅ Waits for database service to become RUNNING (no health check configured)
- ✅ Sequences both wait mechanisms appropriately (sequential processing)
- ✅ Respects separate timeout configurations (90s for app, 60s for db)
- ✅ Respects separate poll interval configurations (2s for both)
- ✅ Generates state files with correct information for both services
- ✅ Maps ports correctly for app service (db has no exposed ports)
- ✅ Enables realistic multi-service dependencies (app connects to database)
- ✅ Stops Docker Compose stacks with `composeDown`
- ✅ Cleans up all containers, networks, and volumes

## Test Scenario

This test demonstrates a realistic multi-service application:

### Application Service (`mixed-wait-app`)
- **Technology**: Spring Boot web application with JDBC
- **Dependencies**: PostgreSQL database
- **Wait Strategy**: `waitForHealthy` (has health check)
- **Timeout**: 90 seconds
- **Poll Interval**: 2 seconds
- **Health Check**: HTTP GET to `/health` endpoint
- **Ports**: Exposes 8080 (mapped to random host port)

### Database Service (`mixed-wait-db`)
- **Technology**: PostgreSQL 16 (Alpine)
- **Wait Strategy**: `waitForRunning` (no health check)
- **Timeout**: 60 seconds
- **Poll Interval**: 2 seconds
- **Health Check**: None (tests waitForRunning mechanism)
- **Ports**: None exposed (only accessible from app container)

### Key Features
- **Mixed Wait Mechanisms**: Tests both `waitForHealthy` and `waitForRunning` together
- **Realistic Dependencies**: App depends on database (compose `depends_on`)
- **Database Connectivity**: App connects to PostgreSQL via JDBC
- **Multi-Service State**: State file contains information for both services
- **Different Wait States**: App becomes HEALTHY, database becomes RUNNING

## Test Lifecycle

**Lifecycle Type**: `CLASS`

The test uses Spock's `setupSpec()` and `cleanupSpec()` methods:
- `setupSpec()` - Runs once before all tests (reads state file)
- `cleanupSpec()` - Runs once after all tests (verifies cleanup)
- Tests run with the same Docker Compose stack (started by `composeUpMixedWaitTest` task)

This mirrors how real users would test: start containers once, run multiple tests, tear down once.

## Test Configuration

### Wait-Healthy Configuration (App Service)
- **Services**: `mixed-wait-app`
- **Timeout**: 90 seconds
- **Poll Interval**: 2 seconds
- **Wait Condition**: Container reports HEALTHY status

### Wait-Running Configuration (Database Service)
- **Services**: `mixed-wait-db`
- **Timeout**: 60 seconds
- **Poll Interval**: 2 seconds
- **Wait Condition**: Container reports RUNNING status

### Health Check Configuration (App Only)
- **Health Check Command**: `wget --quiet --tries=1 --spider http://localhost:8080/health`
- **Health Check Interval**: 3 seconds
- **Health Check Timeout**: 2 seconds
- **Health Check Retries**: 10
- **Start Period**: 10 seconds

### Database Configuration
- **Database Name**: `testdb`
- **Username**: `testuser`
- **Password**: `testpass`
- **Connection URL**: `jdbc:postgresql://mixed-wait-db:5432/testdb`

## Running Tests

**⚠️ Important**: All commands must be run from `plugin-integration-test/` directory.

### Full Integration Test Workflow

```bash
# From plugin-integration-test/ directory
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:mixed-wait:app-image:runIntegrationTest
```

This runs:
1. Build app JAR (with Spring Boot + PostgreSQL dependency)
2. Build Docker image
3. Start Docker Compose stack (`composeUpMixedWaitTest`)
4. Wait for app to be HEALTHY (up to 90 seconds, polling every 2 seconds)
5. Wait for database to be RUNNING (up to 60 seconds, polling every 2 seconds)
6. Run integration tests (validates both services and connectivity)
7. Stop Docker Compose stack (`composeDownMixedWaitTest`)

### Individual Tasks

```bash
# Clean build artifacts
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:mixed-wait:clean

# Build the app
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:mixed-wait:app:bootJar

# Build Docker image
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:mixed-wait:app-image:dockerBuildMixedWaitApp

# Start Docker Compose stack (BOTH services)
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:mixed-wait:app-image:composeUpMixedWaitTest

# Run tests (requires stack to be up)
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:mixed-wait:app-image:integrationTest

# Stop Docker Compose stack (BOTH services)
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:mixed-wait:app-image:composeDownMixedWaitTest
```

### Clean Everything

```bash
# Clean build artifacts and Docker images
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:mixed-wait:clean \
  dockerOrch:verification:mixed-wait:app:clean \
  dockerOrch:verification:mixed-wait:app-image:clean
```

## Test Structure

```groovy
class MixedWaitPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        // Runs ONCE before all tests
        // Reads state file generated by composeUp
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    def "plugin should generate valid state file with both services"() { ... }
    def "plugin should start both containers in running state"() { ... }
    def "plugin should wait for app to be healthy and db to be running"() { ... }
    def "plugin should map ports only for app service"() { ... }
    def "app should be accessible via HTTP"() { ... }
    def "app health endpoint should be accessible"() { ... }
    def "app should successfully connect to database"() { ... }
    def "plugin should respect both timeout configurations"() { ... }
    def "plugin should respect both poll interval configurations"() { ... }
    def "plugin should handle multi-service dependencies correctly"() { ... }
    def "plugin should sequence waits appropriately"() { ... }

    def cleanupSpec() {
        // Runs ONCE after all tests
        // Verifies no resource leaks
        CleanupValidator.verifyCleanup(projectName)
    }
}
```

## Tests Performed

### 1. State File Validation (Both Services)
Validates that the state file:
- Has correct structure (stackName, projectName, timestamp, services)
- Contains BOTH `mixed-wait-app` AND `mixed-wait-db` services
- Has exactly 2 services
- Contains correct service information for each

### 2. Container Running State (Both Services)
Validates that:
- App container is in RUNNING state
- Database container is in RUNNING state

### 3. Container Health State (Mixed)
Validates that:
- App container is HEALTHY (has health check)
- Database container is RUNNING but NOT healthy (no health check)
- Plugin correctly differentiates between the two states

### 4. Port Mapping (App Only)
Validates that:
- App service has port mapping (8080 container → random host port)
- Database service has NO port mappings (not exposed)
- State file reflects this correctly

### 5. HTTP Accessibility (App Service)
Validates that:
- Root endpoint (`/`) is accessible
- Returns expected JSON response
- Proper message and version information

### 6. Health Endpoint Accessibility (App Service)
Validates that:
- Health endpoint (`/health`) is accessible
- Returns status "UP"
- Reports uptime correctly

### 7. Database Connectivity
Validates that:
- App can connect to database
- Database endpoint (`/db`) returns successful connection status
- Test query executes correctly (SELECT 1)
- Database type is reported as PostgreSQL

### 8. Timeout Configuration (Both Services)
Validates that:
- App timeout (90 seconds) was respected
- Database timeout (60 seconds) was respected
- Both containers reached expected state within timeout windows

### 9. Poll Interval Configuration (Both Services)
Validates that:
- Poll interval (2 seconds) was used for both services
- Plugin polled multiple times before success
- Timing is appropriate for configured intervals

### 10. Multi-Service Dependencies
Validates that:
- App depends on database (Compose `depends_on`)
- App can resolve database hostname
- Database is accessible from app container
- Compose networking is configured correctly

### 11. Sequential Wait Processing
Validates that:
- Both waits completed before tests started
- State file is written after ALL waits complete
- Both services are in expected state when tests run

## Validation Tools Used

This test uses internal validators from `buildSrc/`:

- **`StateFileValidator`** - Validates state file structure and content
  - `parseStateFile()` - Parse JSON state file
  - `assertValidStructure()` - Check required fields
  - `getServiceNames()` - Get list of service names
  - `getServiceInfo()` - Get service details
  - `getPublishedPort()` - Extract port mappings

- **`DockerComposeValidator`** - Validates Docker Compose state
  - `isContainerRunning()` - Check container is running
  - `isContainerHealthy()` - Check container health status

- **`CleanupValidator`** - Ensures no resource leaks
  - `verifyCleanup()` - Assert no containers/networks remain
  - `forceCleanup()` - Force removal if verification fails

## Expected Results

All 11 tests should pass:
- ✅ State file has valid structure with both services
- ✅ Both containers are in RUNNING state
- ✅ App container is HEALTHY
- ✅ Database container is RUNNING (not HEALTHY)
- ✅ App has port mapping, database does not
- ✅ App is accessible via HTTP
- ✅ Health endpoint is accessible
- ✅ Database connectivity works
- ✅ Both timeout configurations were respected
- ✅ Both poll interval configurations were respected
- ✅ Multi-service dependencies work correctly
- ✅ Wait sequencing is correct

No containers should remain after tests complete.

## Differences from Other Verification Tests

### vs. Wait-Healthy Test

| Aspect | Wait-Healthy Test | Mixed-Wait Test |
|--------|------------------|-----------------|
| **Services** | 1 (app only) | 2 (app + database) |
| **Wait Mechanisms** | waitForHealthy only | BOTH waitForHealthy and waitForRunning |
| **Health Checks** | App has health check | App has, database does not |
| **Port Mappings** | App has ports | App has, database does not |
| **Dependencies** | None | App depends on database |
| **Database** | None | PostgreSQL 16 |
| **Connectivity Tests** | HTTP only | HTTP + database |
| **State Validation** | Single service | Multiple services |

### vs. Wait-Running Test

| Aspect | Wait-Running Test | Mixed-Wait Test |
|--------|------------------|-----------------|
| **Services** | 1 (app only) | 2 (app + database) |
| **Wait Mechanisms** | waitForRunning only | BOTH waitForHealthy and waitForRunning |
| **Health Checks** | None | App has health check |
| **Wait State** | RUNNING only | HEALTHY + RUNNING |
| **Dependencies** | None | App depends on database |
| **Database** | None | PostgreSQL 16 |

### vs. Basic Test

| Aspect | Basic Test | Mixed-Wait Test |
|--------|-----------|-----------------|
| **Services** | 1 (app only) | 2 (app + database) |
| **Wait Mechanisms** | Simple waitForHealthy | BOTH mechanisms |
| **Timeout** | 60 seconds | 90s (app) + 60s (db) |
| **Poll Interval** | 2 seconds | 2s for both |
| **Complexity** | Simple | Realistic multi-service |
| **Dependencies** | None | App + database |

## Troubleshooting

**Tests fail with "container not found"**:
- Ensure `composeUpMixedWaitTest` ran successfully before `integrationTest`
- Use `runIntegrationTest` task which handles dependencies automatically
- Check both containers are present: `docker ps -a | grep verification-mixed-wait`

**Tests fail with "state file not found"**:
- State file is at `build/compose-state/mixedWaitTest-state.json`
- Ensure `composeUpMixedWaitTest` completed successfully
- Check file exists and has both services

**Database connection fails**:
- Check database container is running: `docker ps | grep mixed-wait-db`
- Check database logs: `docker logs <container-id>`
- Verify environment variables in compose file
- Check app can resolve hostname `mixed-wait-db`

**App container is not healthy**:
- Check app logs: `docker logs <container-id>`
- Verify health check command in Dockerfile
- Check `/health` endpoint is accessible inside container
- Verify Spring Boot started successfully

**Database container is reported as healthy (should be RUNNING only)**:
- Verify compose file has NO healthcheck for database
- Check Docker inspect output: `docker inspect <container-id>`
- Database should have Health status "none" or empty

**Cleanup verification fails**:
- Run manual cleanup: `docker ps -a -q --filter "name=verification-mixed-wait" | xargs -r docker rm -f`
- Check for lingering networks: `docker network ls | grep verification-mixed-wait`
- Remove networks: `docker network rm <network-name>`

**Timeout occurs during composeUp**:
- Check if timeout settings are sufficient for your environment
- Verify both services start within configured timeouts
- Check container logs for startup errors
- Consider increasing timeout values if environment is slow

**Port already in use**:
- Docker Compose uses random host ports to avoid conflicts
- Check actual port in state file
- Ensure no other processes are using the assigned port

## Key Differences: Mixed-Wait Test

This test is unique because it:
1. **Uses BOTH wait mechanisms** in the same stack (not just one)
2. **Tests realistic dependencies** (app needs database)
3. **Validates database connectivity** (not just HTTP endpoints)
4. **Checks multiple service states** (HEALTHY + RUNNING)
5. **Verifies port mapping differences** (app exposed, db not exposed)
6. **Demonstrates real-world usage** (multi-service orchestration)

## ⚠️ This is NOT a User Example

This test validates plugin mechanics using internal tools. For user-facing examples of how to test applications, see
`../../examples/web-app/README.md`.
