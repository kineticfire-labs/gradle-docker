# Task: Write Integration Tests for Multi-File Docker Compose

## Area: Integration Tests

## Type: Integration Test Implementation

## Description
Create comprehensive integration tests for multi-file Docker Compose functionality at `@plugin-integration-test/app-image/src/integrationTest/` that cover all functionality and run with `./gradlew clean fullTest`.

## Context
You are a Principal Software Engineer expert at Java, Gradle, custom Gradle plugins, Groovy, Docker, and Docker Compose. Follow guidance from `@docs/design-docs/gradle-9-configuration-cache-guidance.md` for configuration cache compatibility.

## Requirements

### 1. Integration Test Scope
Create end-to-end integration tests that verify:
- Multi-file Docker Compose stack operations (up/down)
- File precedence and merging behavior
- Service orchestration with multiple compose files
- Real Docker Compose CLI interaction
- Plugin integration with actual Docker environment

### 2. Test Scenarios to Implement

#### Basic Multi-File Scenario
- **Base compose file** with core services (e.g., web application)
- **Override compose file** with environment-specific configurations
- Verify services from both files are deployed correctly
- Test that override file takes precedence for conflicts

#### Complex Multi-File Scenario  
- **Base compose file** with shared services (database, cache)
- **Application compose file** with application services
- **Environment compose file** with environment-specific overrides (ports, volumes)
- Verify final configuration matches expected precedence order
- Test service dependencies and networking

#### File Ordering and Precedence
- Create compose files with conflicting configurations
- Verify Docker Compose's precedence rules are respected
- Test that file order specified in plugin matches Docker Compose behavior
- Validate port mappings, environment variables, and volume mounts

#### Environment File Integration
- Test multi-file compose with environment files
- Verify environment variables are properly resolved
- Test precedence between compose files and env files

### 3. Test Implementation Structure

#### Create New Test Class
`plugin-integration-test/app-image/src/integrationTest/java/com/kineticfire/gradle/docker/integration/appimage/MultiFileComposeIntegrationIT.java`

#### Test Class Structure
```java
@DisplayName("Multi-File Docker Compose Integration Tests")
class MultiFileComposeIntegrationIT {

    @Test
    @DisplayName("Multi-file compose stack starts and stops correctly")
    void multiFileComposeStackStartsAndStopsCorrectly() throws Exception {
        // Test implementation
    }

    @Test
    @DisplayName("File precedence works correctly with conflicting configurations")
    void filePrecedenceWorksCorrectly() throws Exception {
        // Test implementation
    }

    @Test
    @DisplayName("Complex multi-file scenario with service dependencies")
    void complexMultiFileScenarioWorks() throws Exception {
        // Test implementation
    }
}
```

### 4. Test Infrastructure

#### Create Test Compose Files
Create compose files in `plugin-integration-test/app-image/src/integrationTest/resources/compose/multi-file/`:
- `docker-compose.base.yml` - Base services
- `docker-compose.override.yml` - Override configurations  
- `docker-compose.environment.yml` - Environment-specific settings
- `docker-compose.complex-base.yml` - Complex scenario base
- `docker-compose.complex-app.yml` - Complex scenario application layer
- `docker-compose.complex-env.yml` - Complex scenario environment layer

#### Example Base Compose File
```yaml
version: '3.8'
services:
  web:
    image: nginx:alpine
    labels:
      - "test=multi-file-base"
  
  db:
    image: postgres:alpine
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: testuser
      POSTGRES_PASSWORD: testpass
```

#### Example Override Compose File
```yaml
version: '3.8'
services:
  web:
    ports:
      - "8080:80"
    environment:
      - ENV=production
    labels:
      - "test=multi-file-override"
      
  cache:
    image: redis:alpine
```

### 5. Test Verification Points

#### Service Deployment Verification
- Verify all expected services are running
- Check that services have correct configurations
- Validate port mappings match expectations
- Confirm environment variables are set correctly

#### Precedence Verification
- Verify override files take precedence over base files
- Check that conflicting configurations resolve correctly
- Validate that service labels show the correct precedence
- Test port, environment, and volume precedence

#### Integration Verification
- Test compose up/down operations work correctly
- Verify service health checking works
- Test log capture functionality
- Validate service discovery and networking

### 6. Configuration Cache Compatibility

#### Ensure Tested Plugin Code Is Compatible
Based on `@docs/design-docs/gradle-9-configuration-cache-guidance.md`:
- Verify plugin configuration uses Provider API correctly
- Ensure no `.get()` calls during configuration phase
- Test that multi-file configurations are serializable
- Validate configuration cache works with multi-file stacks

### 7. Build Integration

#### Gradle Build Configuration
Ensure tests integrate with existing build:
- Tests run with `./gradlew clean fullTest`
- Tests can run independently with `./gradlew integrationTest`
- Proper test dependencies and setup
- Integration with existing test lifecycle

## Files to Create

### Test Files
- `plugin-integration-test/app-image/src/integrationTest/java/com/kineticfire/gradle/docker/integration/appimage/MultiFileComposeIntegrationIT.java`

### Resource Files
- `plugin-integration-test/app-image/src/integrationTest/resources/compose/multi-file/docker-compose.base.yml`
- `plugin-integration-test/app-image/src/integrationTest/resources/compose/multi-file/docker-compose.override.yml`
- `plugin-integration-test/app-image/src/integrationTest/resources/compose/multi-file/docker-compose.environment.yml`
- Additional compose files for complex scenarios

### Build Configuration Updates (if needed)
- Update integration test configuration to include new test classes
- Ensure proper test resource handling

## Build Command
```bash
cd plugin-integration-test
./gradlew clean fullTest
```

## Acceptance Criteria
1. **Comprehensive Coverage**: All multi-file compose functionality is tested end-to-end
2. **Real Environment**: Tests use actual Docker and Docker Compose CLI
3. **Precedence Verification**: File precedence and merging behavior is validated
4. **Service Verification**: All expected services are deployed and configured correctly
5. **Build Integration**: Tests run successfully with `./gradlew clean fullTest`
6. **Configuration Cache**: Plugin behavior is compatible with configuration cache
7. **Reliability**: Tests pass consistently and are not flaky
8. **Performance**: Tests complete in reasonable time
9. **Resource Management**: Tests properly clean up Docker resources

## Success Metrics
- All integration tests pass consistently
- Multi-file compose stacks deploy correctly
- File precedence behavior matches Docker Compose specifications
- Service configurations are applied correctly
- Tests provide confidence in end-to-end functionality
- Build succeeds with all integration tests