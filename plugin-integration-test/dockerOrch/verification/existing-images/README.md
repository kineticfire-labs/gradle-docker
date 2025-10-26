# Verification Test: Existing/Public Docker Images

**Type**: Plugin Mechanics Validation
**Lifecycle**: CLASS (setupSpec/cleanupSpec)
**Purpose**: Validates plugin works correctly with public Docker Hub images using sourceRef pattern

## What This Test Validates

This verification test ensures the dockerOrch plugin correctly:
- ✅ Works with public Docker Hub images (nginx, redis) using `sourceRef` configuration
- ✅ Pulls public images automatically (no build required)
- ✅ Combines custom-built images with public images in same stack
- ✅ Uses `waitForRunning` for all services (public images lack health checks)
- ✅ Generates state files with correct structure for all services
- ✅ Maps ports correctly for all services (custom app + public images)
- ✅ Enables interaction between custom app and public services
- ✅ Stops Docker Compose stacks with `composeDown`
- ✅ Cleans up all containers, networks, and volumes

## Key Demonstrations

### 1. sourceRef Pattern for Public Images

This test showcases the `sourceRef` pattern for using existing Docker images:

```groovy
docker {
    images {
        nginx {
            sourceRef {
                registry = 'docker.io'
                namespace = 'library'
                imageName = 'nginx'
                tag = 'alpine'
            }
            // No build needed - image pulled from Docker Hub
        }

        redis {
            sourceRef {
                registry = 'docker.io'
                namespace = 'library'
                imageName = 'redis'
                tag = 'alpine'
            }
            // No build needed - image pulled from Docker Hub
        }
    }
}
```

## Test Architecture

This test uses a 3-service stack:
1. **Custom App** (verification-existing-images-app) - Built from Dockerfile
   - Spring Boot application
   - Interacts with both nginx and redis
   - Exposes REST API for testing
2. **Nginx** (public image) - Serves static HTML content
   - Uses nginx:alpine from Docker Hub
   - Mounts test HTML file
3. **Redis** (public image) - Provides caching/storage
   - Uses redis:alpine from Docker Hub
   - No persistence configuration

## Test Lifecycle

**Lifecycle Type**: `CLASS`

The test uses Spock's `setupSpec()` and `cleanupSpec()` methods:
- `setupSpec()` - Runs once before all tests in the class (reads state file)
- Tests run with the same Docker Compose stack (started by `composeUpExistingImagesTest` task)
- `cleanupSpec()` - Forces cleanup even if tests fail
- Gradle task workflow handles normal cleanup via `finalizedBy`

This mirrors how real users would test: start containers once, run multiple tests, tear down once.

## Test Configuration

### Docker Images
- **Custom App**: Built from Dockerfile (verification-existing-images-app:latest)
- **Nginx**: Pulled from Docker Hub (nginx:alpine)
- **Redis**: Pulled from Docker Hub (redis:alpine)

### Docker Compose Configuration
- **NO Health Checks**: None of the services have health checks configured
- **Service Names**: existing-images-app, nginx, redis
- **Ports**: All use random host port mapping to avoid conflicts
  - App: 8080
  - Nginx: 80
  - Redis: 6379

### 2. Mixed Wait Strategy

This scenario demonstrates using **both** wait strategies in the same stack:

```groovy
dockerOrch {
    composeStacks {
        existingImagesTest {
            // Custom app with health check -> waitForHealthy
            waitForHealthy {
                waitForServices.set(['existing-images-app'])
                timeoutSeconds.set(90)
                pollSeconds.set(1)
            }

            // Public images without health checks -> waitForRunning
            waitForRunning {
                waitForServices.set(['nginx', 'redis'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}
```

**Why Mixed Strategy?**
- Custom app takes 3-4 seconds to start Spring Boot → needs health check
- Public images (nginx, redis) start instantly → waitForRunning is sufficient
- This pattern is realistic: custom apps often need health checks, standard services don't

### Wait Configuration
- **Strategy**: MIXED (waitForHealthy + waitForRunning)
  - `existing-images-app`: waitForHealthy (90s timeout, 1s poll)
  - `nginx`, `redis`: waitForRunning (60s timeout, 2s poll)

## Running Tests

**⚠️ Important**: All commands must be run from `plugin-integration-test/` directory.

### Full Integration Test Workflow

```bash
# From plugin-integration-test/ directory
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:existing-images:app-image:runIntegrationTest
```

This runs:
1. Build app JAR (Spring Boot app with nginx/redis integration)
2. Build custom Docker image (app only)
3. Pull public images (nginx, redis) automatically
4. Start Docker Compose stack (`composeUpExistingImagesTest`)
5. Wait for all services to be RUNNING (up to 60 seconds, polling every 2 seconds)
6. Run integration tests (validates all services and interactions)
7. Stop Docker Compose stack (`composeDownExistingImagesTest`)

### Individual Tasks

```bash
# Clean build artifacts
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:existing-images:clean

# Build the app JAR
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:existing-images:app:bootJar

# Build custom Docker image (app only)
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:existing-images:app-image:dockerBuildExistingImagesApp

# Start Docker Compose stack (pulls nginx/redis automatically)
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:existing-images:app-image:composeUpExistingImagesTest

# Run tests (requires stack to be up)
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:existing-images:app-image:integrationTest

# Stop Docker Compose stack
./gradlew -Pplugin_version=1.0.0 \
  dockerOrch:verification:existing-images:app-image:composeDownExistingImagesTest
```

## Test Structure

```groovy
class ExistingImagesPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        // Runs ONCE before all tests
        // Reads state file generated by composeUp
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    def cleanupSpec() {
        // Force cleanup even if tests fail
    }

    def "plugin should generate valid state file with all three services"() { ... }
    def "plugin should start all containers in running state"() { ... }
    def "plugin should wait for all services to be running"() { ... }
    def "plugin should map ports for all services"() { ... }
    def "nginx should serve static content"() { ... }
    def "app should fetch content from nginx"() { ... }
    def "app should test redis connection"() { ... }
    def "app should store and retrieve data from redis"() { ... }
    def "app should delete data from redis"() { ... }
}
```

## Tests Performed

### 1. State File Validation
Validates that the state file:
- Has correct structure (stackName, projectName, timestamp, services)
- Contains all three services (existing-images-app, nginx, redis)
- Has port mapping information for each service

### 2. Container Running State
Validates that all containers are in RUNNING state:
- existing-images-app
- nginx
- redis

### 3. Mixed Wait Strategy Validation
Validates that plugin correctly uses both wait strategies:
- App uses `waitForHealthy` → waits for HEALTHY state
- Nginx/Redis use `waitForRunning` → waits for RUNNING state
- Both strategies work together in the same stack

### 4. Port Mapping for All Services
Validates that ports are mapped correctly:
- App: 8080 → random host port
- Nginx: 80 → random host port
- Redis: 6379 → random host port

### 5. Nginx Serves Static Content
Validates that:
- Nginx is accessible via HTTP
- Static HTML file is served correctly
- Content matches expected test HTML

### 6. App Fetches Content from Nginx
Validates that:
- App can communicate with nginx
- App endpoint `/nginx` fetches and returns nginx content
- Service-to-service networking works

### 7. App Tests Redis Connection
Validates that:
- App can connect to redis
- Redis responds to PING command
- App endpoint `/redis/test` returns PONG

### 8. App Stores and Retrieves Data from Redis
Validates that:
- App can store data in redis (POST /cache/{key})
- App can retrieve data from redis (GET /cache/{key})
- Data persists across requests

### 9. App Deletes Data from Redis
Validates that:
- App can delete data from redis (DELETE /cache/{key})
- Deleted data returns 404 on subsequent retrieval
- Redis operations work correctly

## Validation Tools Used

This test uses internal validators from `buildSrc/`:

- **`StateFileValidator`** - Validates state file structure and content
  - `parseStateFile()` - Parse JSON state file
  - `assertValidStructure()` - Check required fields
  - `getPublishedPort()` - Extract port mappings
  - `getServiceInfo()` - Get service details

- **`DockerComposeValidator`** - Validates Docker Compose state
  - `isContainerRunning()` - Check container is running
  - `isServiceRunningViaCompose()` - Check running state via compose

- **`CleanupValidator`** - Ensures no resource leaks
  - `findLingeringContainers()` - Find remaining containers
  - `verifyCleanup()` - Assert no containers/networks remain

## Expected Results

All 10 tests should pass:
- ✅ State file has valid structure with all three services
- ✅ All containers are in RUNNING state (app, nginx, redis)
- ✅ Mixed wait strategy works (app HEALTHY, nginx/redis RUNNING)
- ✅ Port mappings are correct for all services
- ✅ Nginx serves static HTML correctly
- ✅ App health endpoint is accessible (confirms health check works)
- ✅ App fetches content from nginx successfully
- ✅ App connects to redis and receives PONG
- ✅ App stores and retrieves data from redis
- ✅ App deletes data from redis

No containers should remain after tests complete.

## Troubleshooting

**Tests fail with "image not found for nginx/redis"**:
- Ensure Docker can pull from Docker Hub
- Check network/proxy settings
- Try manual pull: `docker pull nginx:alpine` and `docker pull redis:alpine`

**Tests fail with "container not found"**:
- Ensure `composeUpExistingImagesTest` ran successfully
- Use `runIntegrationTest` task which handles dependencies automatically

**Tests fail with "state file not found"**:
- State file is at `build/compose-state/existingImagesTest-state.json`
- Ensure `composeUpExistingImagesTest` completed successfully

**Tests fail with "connection refused" or SocketException on HTTP calls**:
- This indicates the app container is running but Spring Boot hasn't finished starting
- Solution: Ensure health check is configured in Docker Compose and using `waitForHealthy` for app
- The health check gives Spring Boot time to fully start (3-4 seconds)
- Check containers are running: `docker ps -a | grep existing-images`
- Verify port mapping: check state file or `docker ps`
- Check logs: `docker logs <container-id>`
- Verify health status: `docker inspect <container-id> | grep -A 10 Health`

**Nginx tests fail**:
- Verify static HTML file exists at `src/integrationTest/resources/static/index.html`
- Check nginx logs: `docker logs <nginx-container-id>`
- Verify volume mount in Docker Compose file

**Redis tests fail**:
- Check redis is running: `docker ps | grep redis`
- Verify app can connect: check app logs
- Test redis directly: `docker exec <redis-container-id> redis-cli ping`

**Cleanup verification fails**:
- Run manual cleanup: `docker ps -a -q --filter "name=verification-existing-images" | xargs -r docker rm -f`
- Check networks: `docker network ls | grep verification-existing-images`

## Use Case

This test validates the plugin's ability to:
1. **Use existing Docker Hub images without building**
   - Public images are widely used (nginx, postgres, redis, mysql)
   - Users should be able to use them directly via `sourceRef`
   - No Dockerfile needed for public images

2. **Combine custom and public images in same stack**
   - Real applications often use both
   - Custom app image + public database/cache/proxy images
   - Plugin should handle mixed stacks seamlessly

3. **Use mixed wait strategies appropriately**
   - Custom apps need health checks → use `waitForHealthy`
   - Public images without health checks → use `waitForRunning`
   - Plugin supports both strategies in the same stack
   - This is the **realistic pattern** for production-like testing

4. **Enable realistic testing scenarios**
   - Test app integration with standard services
   - Validate service-to-service communication
   - Prove the full stack works together
   - Ensure app is fully started before tests run

## ⚠️ This is NOT a User Example

This test validates plugin mechanics using internal tools. For user-facing examples of how to test applications with
public images, see `../../examples/database-app/README.md` which demonstrates PostgreSQL integration.
