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
- **ComposeDown automatically uses same files as ComposeUp for proper teardown**
- File precedence and merging behavior
- Service orchestration with multiple compose files
- Real Docker Compose CLI interaction
- Plugin integration with actual Docker environment

### 2. Test Scenarios to Implement

#### Basic Multi-File Scenario
- **Base compose file** with core services (e.g., web application)
- **Override compose file** with environment-specific configurations
- Verify services from both files are deployed correctly after composeUp
- Test that override file takes precedence for conflicts
- **Verify composeDown properly tears down all services using same files**
- Confirm no services remain running after composeDown

#### Complex Multi-File Scenario  
- **Base compose file** with shared services (database, cache)
- **Application compose file** with application services
- **Environment compose file** with environment-specific overrides (ports, volumes)
- Verify final configuration matches expected precedence order after composeUp
- Test service dependencies and networking
- **Verify composeDown tears down complex multi-file stack completely**
- Test that all services across all files are properly stopped

#### File Ordering and Precedence
- Create compose files with conflicting configurations
- Verify Docker Compose's precedence rules are respected during composeUp
- Test that file order specified in plugin matches Docker Compose behavior
- Validate port mappings, environment variables, and volume mounts
- **Verify composeDown uses exact same file order for proper teardown**
- Test that precedence order affects both service creation and destruction

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
        // Test composeUp deploys all services from multiple files
        // Test composeDown tears down all services using same files
    }

    @Test
    @DisplayName("ComposeDown automatically uses same files as ComposeUp")
    void composeDownUseSameFilesAsComposeUp() throws Exception {
        // Test that composeDown inherits composeUp file configuration
        // Verify proper teardown of multi-file services
    }

    @Test
    @DisplayName("File precedence works correctly with conflicting configurations")
    void filePrecedenceWorksCorrectly() throws Exception {
        // Test precedence during composeUp and proper teardown with composeDown
    }

    @Test
    @DisplayName("Complex multi-file scenario with service dependencies")
    void complexMultiFileScenarioWorks() throws Exception {
        // Test complex stacks with proper up/down lifecycle
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
- Verify all expected services are running after composeUp
- Check that services have correct configurations from multiple files
- Validate port mappings match expectations
- Confirm environment variables are set correctly

#### Precedence Verification
- Verify override files take precedence over base files
- Check that conflicting configurations resolve correctly
- Validate that service labels show the correct precedence
- Test port, environment, and volume precedence

#### Integration Verification
- Test compose up/down operations work correctly with multi-file stacks
- **Verify composeDown properly cleans up all services from multi-file configuration**
- Verify service health checking works across multiple compose files
- Test log capture functionality during up/down operations
- Validate service discovery and networking with complex multi-file setups
- **Confirm no residual containers or networks after composeDown**

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
3. **ComposeDown Integration**: ComposeDown automatically uses same files as ComposeUp for proper teardown
4. **Precedence Verification**: File precedence and merging behavior is validated
5. **Service Verification**: All expected services are deployed and configured correctly
6. **Teardown Verification**: All services are properly stopped and cleaned up by ComposeDown
7. **Build Integration**: Tests run successfully with `./gradlew clean fullTest`
8. **Configuration Cache**: Plugin behavior is compatible with configuration cache
9. **Reliability**: Tests pass consistently and are not flaky
10. **Performance**: Tests complete in reasonable time
11. **Resource Management**: Tests properly clean up Docker resources with no residual containers

## Success Metrics
- All integration tests pass consistently
- Multi-file compose stacks deploy correctly
- **ComposeDown properly tears down all services from multi-file ComposeUp**
- File precedence behavior matches Docker Compose specifications
- Service configurations are applied correctly during both up and down operations
- **No residual containers or networks remain after composeDown**
- Tests provide confidence in end-to-end functionality
- Build succeeds with all integration tests

## Status

**Status**: done  
**Date**: 2025-09-08  
**Description**: Implemented comprehensive multi-file Docker Compose integration tests covering all required functionality. Created 4 integration tests that verify multi-file stack operations, automatic ComposeDown file inheritance, file precedence behavior, and complex multi-service scenarios. Tests use Docker Compose v2 and validate proper service deployment, configuration precedence, and complete resource cleanup. All tests pass successfully and are integrated into the build system via `./gradlew clean fullTest`.

**Files Created**:
- `MultiFileComposeIntegrationIT.java` - Complete test class with 4 comprehensive test methods
- 6 compose test files covering basic, override, environment, and complex scenarios
- Updated build configuration to include new test task `integrationTestMultiFileCompose`

**Key Achievements**:
- ✅ Multi-file compose stacks start and stop correctly
- ✅ ComposeDown automatically uses same files as ComposeUp for proper teardown  
- ✅ File precedence works correctly with Docker Compose v2 behavior
- ✅ Complex multi-file scenarios with service dependencies function properly
- ✅ All services are properly cleaned up with no residual containers/networks
- ✅ Tests integrate with existing build system and pass consistently