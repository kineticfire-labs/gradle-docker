# Registry Integration Testing Implementation Plan

## Overview

This document outlines the comprehensive implementation plan for Docker registry integration testing, including authentication support for the save task and robust multi-registry testing infrastructure. This plan consolidates buildSrc directories and implements all registry infrastructure in the mature plugin-integration-test buildSrc.

## Current State Analysis

### BuildSrc Directory Duplication Issue

❌ **BUILDSCR DUPLICATION IDENTIFIED:**

- **Root buildSrc** (`/gradle-docker/buildSrc/`): Contains only 2 files, appears abandoned
- **Plugin Integration Test buildSrc** (`/gradle-docker/plugin-integration-test/buildSrc/`): Comprehensive, mature implementation with full Docker testing library

**Decision:** Delete root buildSrc and implement all registry infrastructure in `/plugin-integration-test/buildSrc/` (the active, working implementation).

### DockerRegistryImageVerifyTask Issues

❌ **MAJOR ISSUES IDENTIFIED:**

1. **API Mismatch**: The task expects `imageNames` in format "image-name:tag" but constructs full image references as `"${registryUrl}/${imageName}"`. This doesn't support full image names like "com.example:5000/namespace/team/my-image:1.0.0".

2. **No Real Registry Support**: The task only verifies existing images in registries using `docker manifest inspect` - it doesn't create or manage test registries.

3. **Single Registry Limitation**: Only supports one registry URL per task instance, not multiple registries.

4. **No Authentication Support**: No authentication parameters or configuration.

5. **Limited Verification Scope**: Can only verify images already in registries, not test the full publish workflow.

### Save Task `pullIfMissing` Authentication Gap

❌ **AUTHENTICATION GAP IDENTIFIED:**

The `DockerSaveTask` supports `pullIfMissing` but has **NO authentication support** for pulling from private registries. This is a significant functional gap.

### Current Testing Infrastructure Limitations

- No Docker registry containers or management
- No multi-registry test scenarios
- No authentication testing for save operations
- Limited integration test coverage for publish workflows
- **No robust cleanup mechanisms for failed tests**

## Implementation Plan

### Phase 1: Consolidate BuildSrc Directories

#### 1.1: Clean Up Duplication
```bash
# Delete redundant root buildSrc
rm -rf /gradle-docker/buildSrc/

# Verify no projects reference root buildSrc
grep -r "buildSrc" --exclude-dir=plugin-integration-test .
```

**Actions:**
- Delete `/gradle-docker/buildSrc/` directory entirely
- Verify no projects have dependencies on root buildSrc
- Confirm all integration tests continue to work with plugin-integration-test buildSrc

#### 1.2: Target BuildSrc Location
**Primary Location:** `/gradle-docker/plugin-integration-test/buildSrc/`

**Rationale:**
- Mature, comprehensive Docker testing infrastructure already exists
- Integration tests already depend on this buildSrc
- Registry testing is logically part of integration testing
- Has proper build.gradle, README.md, and full project structure

### Phase 2: Enhance Existing BuildSrc with Registry Infrastructure

#### 2.1: New Registry Management Tasks
```
plugin-integration-test/buildSrc/src/main/groovy/
├── docker-image-testing.gradle              # EXISTING - enhance with registry functions
├── DockerRegistryStartTask.groovy           # NEW - Start registry containers
├── DockerRegistryStopTask.groovy            # NEW - Stop registry containers
├── DockerRegistryConfigTask.groovy          # NEW - Configure authentication
├── DockerRegistryCleanupTask.groovy         # NEW - Emergency cleanup
├── RegistryTestFixture.groovy               # NEW - Registry management utility
├── DockerRegistryImageVerifyTask.groovy     # EXISTING - enhance for full image names
├── DockerImageCleanTask.groovy              # EXISTING - keep as-is
├── DockerImageVerifyTask.groovy             # EXISTING - keep as-is
└── DockerSavedImageVerifyTask.groovy        # EXISTING - keep as-is
```

#### 2.2: Enhanced Registry Functions in docker-image-testing.gradle
```groovy
// NEW registry functions to add:
ext.registerMultiRegistryWorkflow = { Project project, Map<String, RegistryConfig> registries ->
    // Support multiple registries with different auth configs
}

ext.registerRegistryLifecycleManagement = { Project project ->
    // Start/stop registry containers for tests
    // Include cleanup hooks for test failures
}

ext.registerRegistryCleanupHooks = { Project project ->
    // Emergency cleanup for orphaned containers
    // Cleanup on test failure or interruption
}

ext.registerAuthenticatedRegistryVerification = { Project project, String registry, AuthConfig auth, List<String> images ->
    // Verify images in authenticated registry
}

ext.registerSaveWithPullVerification = { Project project, SaveConfig config ->
    // Test save with pullIfMissing + authentication
    // Verify pulled image can be saved and loaded back
}

ext.registerMultiRegistryVerification = { Project project, Map<String, List<String>> imagesByRegistry ->
    // Verify images across multiple registries
}
```

**Features:**
- Start multiple registry containers (authenticated/unauthenticated)
- Dynamic port allocation to avoid conflicts
- SSL/TLS support for realistic testing
- Credential configuration and testing
- Full lifecycle management (start/stop/cleanup)
- **Robust cleanup mechanisms for test failures**

#### 2.3: Robust Cleanup Infrastructure

**Container Lifecycle Management:**
```groovy
class RegistryTestFixture {
    private static final String REGISTRY_LABEL = "gradle-docker-test-registry"
    private Map<String, String> runningContainers = [:]
    private Map<String, Integer> allocatedPorts = [:]
    private Set<String> createdVolumes = []
    private Set<String> createdNetworks = []

    // Startup with cleanup registration
    Map<String, DockerRegistry> startTestRegistries() {
        // Register shutdown hooks immediately after container start
        // Track all containers and ports for cleanup
    }

    // Graceful shutdown
    void stopAllRegistries() {
        // Stop containers in reverse startup order
        // Remove containers and clean up volumes
        // Release allocated ports
    }

    // Emergency cleanup for failed tests
    void emergencyCleanup() {
        // Force stop and remove all tracked containers
        // Clean up orphaned volumes and networks
        // Release all allocated ports
    }

    // Verification and health checks
    void verifyRegistryHealth() {
        // Health check all running registries
        // Detect and clean up unhealthy containers
    }
}
```

**Cleanup Strategy:**
1. **Immediate Registration**: Register cleanup hooks immediately when containers start
2. **Test Finalizers**: Use Gradle test finalizers to ensure cleanup runs even on test failure
3. **JVM Shutdown Hooks**: Register JVM shutdown hooks as backup cleanup mechanism
4. **Container Tracking**: Track all started containers and allocated resources
5. **Port Management**: Dynamic port allocation with cleanup on failure
6. **Volume Cleanup**: Remove test volumes and networks created for registries
7. **Emergency Cleanup Task**: Dedicated Gradle task to clean up orphaned containers

#### 2.4: Update BuildSrc README.md

**Add Registry Integration Section to plugin-integration-test/buildSrc/README.md:**

```markdown
## Registry Integration Testing

The Docker Image Testing Library now includes comprehensive registry integration testing support with authentication and multi-registry scenarios.

### Registry Management Functions

#### registerRegistryLifecycleManagement

Manages Docker registry containers for integration testing with robust cleanup.

```groovy
registerRegistryLifecycleManagement(project)
```

**Creates Tasks:**
- `startTestRegistries` - Start authenticated and unauthenticated registry containers
- `stopTestRegistries` - Stop and clean up all test registries
- `emergencyRegistryCleanup` - Emergency cleanup for orphaned containers

#### registerMultiRegistryWorkflow

Sets up multiple registries with different authentication configurations.

```groovy
def registryConfig = [
    'public': new RegistryConfig(port: 5000, auth: false),
    'private': new RegistryConfig(port: 5001, auth: true, username: 'testuser', password: 'testpass')
]
registerMultiRegistryWorkflow(project, registryConfig)
```

#### registerAuthenticatedRegistryVerification

Verifies images exist in authenticated registries.

```groovy
registerAuthenticatedRegistryVerification(project, 'localhost:5001', authConfig, [
    'localhost:5001/myapp:latest',
    'localhost:5001/myapp:v1.0.0'
])
```

#### registerSaveWithPullVerification

Tests save operations with pullIfMissing and authentication.

```groovy
registerSaveWithPullVerification(project, saveConfig)
```

### Registry Integration Test Example

```groovy
// Apply the Docker image testing library
apply from: "$rootDir/buildSrc/src/main/groovy/docker-image-testing.gradle"

// Register registry lifecycle management
registerRegistryLifecycleManagement(project)

// Register multi-registry workflow
def registries = [
    'public': new RegistryConfig(port: 5000, auth: false),
    'private': new RegistryConfig(port: 5001, auth: true, username: 'testuser', password: 'testpass')
]
registerMultiRegistryWorkflow(project, registries)

// Register verification tasks
registerAuthenticatedRegistryVerification(project, 'localhost:5001', authConfig, [
    'localhost:5001/myapp:latest'
])

// Integration test with registry support
tasks.register('registryIntegrationTest') {
    description = 'Run complete Docker registry integration test'
    group = 'verification'

    dependsOn 'startTestRegistries'
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerImages'
    dependsOn 'verifyDockerImages'
    dependsOn 'verifyRegistryDockerImages'

    // CRITICAL: Always clean up registries, even on failure
    finalizedBy 'stopTestRegistries'
    finalizedBy 'emergencyRegistryCleanup'

    // Ensure proper task ordering
    tasks.dockerImages.mustRunAfter tasks.startTestRegistries
    tasks.verifyRegistryDockerImages.mustRunAfter tasks.dockerImages
}
```

### Authentication Support

Registry integration testing supports multiple authentication methods:

- **Username/Password**: Traditional registry authentication
- **Token-based**: GitHub Container Registry, etc.
- **Credential Helper**: AWS ECR, GCP, etc.
- **Unauthenticated**: Public registries

### Cleanup Mechanisms

Multiple layers of cleanup ensure no registry containers are left running:

1. **Gradle Finalizers**: `finalizedBy 'stopTestRegistries'`
2. **JVM Shutdown Hooks**: Backup cleanup on process termination
3. **Emergency Tasks**: `emergencyRegistryCleanup` for orphaned containers
4. **Health Monitoring**: Automatic cleanup of unhealthy containers

### Testing Scope

- **Local Registries Only**: All tests use local Docker registry containers
- **No Public Registry Testing**: No testing against DockerHub, GHCR (no test accounts)
- **Multi-Registry Support**: Test scenarios with 2-3 local registries
- **Authentication Testing**: Full auth workflow testing with local registries
```

### Phase 3: Fix and Enhance DockerRegistryImageVerifyTask

#### 3.1: Support Full Image Names
```groovy
abstract class DockerRegistryImageVerifyTask extends DefaultTask {
    @Input
    abstract ListProperty<String> getFullImageReferences()  // CHANGED: Accept full refs

    // REMOVED: registryUrl - now parsed from full image references

    @Input  // NEW: Authentication support
    abstract Property<AuthConfig> getAuthConfig()
}
```

#### 3.2: Multi-Registry Support
```groovy
// Support verification across multiple registries in single task
Map<String, List<String>> parseImagesByRegistry(List<String> fullImageReferences) {
    // Group images by registry URL extracted from full references
}
```

#### 3.3: Authentication Integration
```groovy
// Add authentication support for private registry verification
private void verifyWithAuth(String fullImageRef, AuthConfig auth) {
    // Use docker login + manifest inspect for authenticated verification
}
```

### Phase 4: Add Authentication to DockerSaveTask

#### 4.1: Extend SaveSpec with Authentication (Consistent with PublishSpec)
```groovy
abstract class SaveSpec {
    abstract Property<String> getCompression()
    abstract RegularFileProperty getOutputFile()
    abstract Property<Boolean> getPullIfMissing()

    // NEW: Authentication for pullIfMissing - SAME as PublishSpec.auth
    abstract Property<AuthSpec> getAuth()

    void auth(Closure closure) {
        // Configure authentication when pullIfMissing=true
        // IDENTICAL DSL to PublishSpec.auth for consistency
    }
}
```

#### 4.2: Enhance DockerSaveTask
```groovy
private String resolveImageSource() {
    if (sourceRef.present) {
        String ref = sourceRef.get()
        if (pullIfMissing.get() && !dockerService.get().imageExists(ref).get()) {
            // NEW: Pass authentication if configured
            // Extract registry from sourceRef automatically
            AuthConfig auth = getAuthConfigFromSaveSpec()
            dockerService.get().pullImage(ref, auth).get()
        }
        return ref
    }
    return imageName.get()
}
```

#### 4.3: Save Task Authentication DSL Examples

**Design Decision: No `serverAddress` in auth block**
- Registry address is **automatically extracted** from `sourceRef`
- Eliminates redundant configuration
- Reduces configuration errors
- Matches user expectations (one source of truth)

**Example 1: Save with pullIfMissing and Username/Password Authentication**
```groovy
docker {
    images {
        myApp {
            sourceRef = 'private.registry.com:5000/namespace/myapp:1.0.0'

            save {
                outputFile = file('build/docker-images/myapp-backup.tar.gz')
                compression = 'gzip'
                pullIfMissing = true  // Will pull from registry if not local

                // Authentication for pulling from private registry
                // Registry address extracted from sourceRef automatically
                auth {
                    username.set('myuser')
                    password.set('mypassword')
                    // NO serverAddress - extracted from sourceRef
                }
            }
        }
    }
}
```

**Example 2: Save with pullIfMissing and Token Authentication**
```groovy
docker {
    images {
        ghcrApp {
            sourceRef = 'ghcr.io/myorg/myapp:latest'

            save {
                outputFile = file('build/docker-images/ghcr-app.tar')
                compression = 'none'
                pullIfMissing = true

                // Token-based authentication (e.g., GitHub Container Registry)
                // Registry 'ghcr.io' extracted from sourceRef
                auth {
                    registryToken.set('ghp_abcd1234567890')
                    // NO serverAddress needed
                }
            }
        }
    }
}
```

**Example 3: Save with pullIfMissing and No Authentication (Public Registry)**
```groovy
docker {
    images {
        publicApp {
            sourceRef = 'docker.io/library/nginx:alpine'

            save {
                outputFile = file('build/docker-images/nginx-backup.tar.bz2')
                compression = 'bzip2'
                pullIfMissing = true
                // No auth block = assumes public registry requiring no authentication
                // Registry 'docker.io' extracted from sourceRef
            }
        }
    }
}
```

**Example 4: Save with pullIfMissing and Credential Helper**
```groovy
docker {
    images {
        awsApp {
            sourceRef = '123456789012.dkr.ecr.us-west-2.amazonaws.com/myapp:prod'

            save {
                outputFile = file('build/docker-images/aws-app.tar.xz')
                compression = 'xz'
                pullIfMissing = true

                // Use Docker credential helper for authentication
                // Registry address extracted from sourceRef
                auth {
                    helper.set('docker-credential-ecr-login')
                    // NO serverAddress needed
                }
            }
        }
    }
}
```

**Authentication DSL Consistency:**
- **IDENTICAL** to `PublishSpec.auth` DSL structure
- Same property names: `username`, `password`, `registryToken`, `helper`
- Same configuration patterns and closure syntax
- **NO** `serverAddress` in either save or publish auth blocks
- Registry addresses always extracted from image references

**Authentication Behavior Rules:**
1. **No `auth` block**: Assumes public registry requiring no authentication
2. **`auth` block present**: Uses provided credentials for private registry access
3. **`pullIfMissing = false`**: Authentication configuration is ignored (not needed)
4. **`pullIfMissing = true` + no `auth`**: Attempts unauthenticated pull (will fail for private registries)
5. **Registry extraction**: Always extracted from `sourceRef` - no redundant configuration

### Phase 5: Comprehensive Integration Test Framework with Robust Cleanup

#### 5.1: Multi-Registry Test Scenarios with Cleanup
```groovy
// New integration test scenarios:
- scenario-3-multi-registry-publish    // Publish to 2+ registries
- scenario-4-authenticated-publish     // Authenticated registry publish
- scenario-5-save-with-pull-auth      // Save with pullIfMissing + auth
- scenario-6-mixed-auth-workflow      // Mixed authenticated/unauthenticated

// ALL scenarios include robust cleanup:
tasks.register('integrationTest') {
    dependsOn 'startTestRegistries'
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerImages'
    dependsOn 'verifyDockerImages'
    dependsOn 'verifySavedDockerImages'
    dependsOn 'verifyRegistryDockerImages'

    // CRITICAL: Always clean up registries, even on failure
    finalizedBy 'stopTestRegistries'
    finalizedBy 'emergencyRegistryCleanup'

    // Ensure proper task ordering
    tasks.dockerImages.mustRunAfter tasks.startTestRegistries
    tasks.verifyRegistryDockerImages.mustRunAfter tasks.dockerImages
    tasks.stopTestRegistries.mustRunAfter tasks.verifyRegistryDockerImages
}
```

#### 5.2: Save with Authentication Integration Tests with Cleanup
```groovy
// scenario-5-save-with-pull-auth example with robust cleanup:
apply from: "$rootDir/buildSrc/src/main/groovy/docker-image-testing.gradle"

// Register registry lifecycle management with cleanup
registerRegistryLifecycleManagement(project)
registerRegistryCleanupHooks(project)

docker {
    images {
        authenticatedPull {
            sourceRef = 'localhost:5001/test/app:v1.0.0'  // Private test registry

            save {
                outputFile = file('build/docker-images/auth-pulled-app.tar')
                compression = 'none'
                pullIfMissing = true

                // Registry 'localhost:5001' extracted from sourceRef
                auth {
                    username.set('testuser')
                    password.set('testpass')
                    // NO serverAddress - extracted automatically
                }
            }
        }

        publicPull {
            sourceRef = 'localhost:5002/public/app:latest'  // Public test registry

            save {
                outputFile = file('build/docker-images/public-pulled-app.tar')
                compression = 'gzip'
                pullIfMissing = true
                // No auth block - public registry
            }
        }
    }
}

// Integration test with comprehensive cleanup
tasks.register('integrationTest') {
    description = 'Run complete Docker integration test with registry authentication'
    group = 'verification'

    dependsOn 'startTestRegistries'
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerImages'
    dependsOn 'verifyDockerImages'
    dependsOn 'verifySavedDockerImages'
    dependsOn 'verifyRegistryDockerImages'

    // CRITICAL: Always clean up registries, even on failure
    finalizedBy 'stopTestRegistries'
    finalizedBy 'emergencyRegistryCleanup'  // Backup cleanup

    // Register JVM shutdown hook for emergency cleanup
    doFirst {
        Runtime.runtime.addShutdownHook(new Thread({
            logger.warn("JVM shutdown detected, performing emergency cleanup")
            project.extensions.findByType(RegistryTestFixture)?.emergencyCleanup()
        }))
    }

    // Ensure proper task ordering
    tasks.startTestRegistries.mustRunAfter tasks.cleanDockerImages
    tasks.dockerImages.mustRunAfter tasks.startTestRegistries
    tasks.verifyDockerImages.mustRunAfter tasks.dockerImages
    tasks.verifySavedDockerImages.mustRunAfter tasks.dockerImages
    tasks.verifyRegistryDockerImages.mustRunAfter tasks.dockerImages
    tasks.stopTestRegistries.mustRunAfter tasks.verifyRegistryDockerImages
}
```

**Testing Scope Limitation:**
- **LOCAL REGISTRIES ONLY**: All integration tests use local Docker registry containers
- **NO PUBLIC REGISTRY TESTING**: We do NOT test against DockerHub, GHCR, or other public registries
- **REASON**: No test accounts configured for public registries
- **FUTURE ITEM**: Public registry integration testing requires account setup and credentials management

#### 5.3: Emergency Cleanup Tasks
```groovy
// Emergency cleanup tasks for CI/CD environments
tasks.register('emergencyRegistryCleanup') {
    description = 'Emergency cleanup of orphaned registry containers from failed tests'
    group = 'verification'

    doLast {
        def fixture = new RegistryTestFixture()
        fixture.emergencyCleanup()
    }
}

tasks.register('listOrphanedRegistries') {
    description = 'List any orphaned registry containers from failed tests'
    group = 'verification'

    doLast {
        def orphanedContainers = findContainersByLabel("gradle-docker-test-registry")
        if (orphanedContainers.isEmpty()) {
            logger.lifecycle("No orphaned registry containers found")
        } else {
            logger.lifecycle("Found ${orphanedContainers.size()} orphaned registry containers:")
            orphanedContainers.each { containerId ->
                logger.lifecycle("  - ${containerId}")
            }
            logger.lifecycle("Run 'gradlew emergencyRegistryCleanup' to clean them up")
        }
    }
}

// CI/CD friendly cleanup
tasks.register('cleanupAllTestResources') {
    description = 'Clean up all test resources including containers, volumes, and networks'
    group = 'verification'

    dependsOn 'emergencyRegistryCleanup'
    dependsOn 'cleanDockerImages'
}
```

## Unit Test Coverage Plan (100%)

### Phase 1: DockerRegistryImageVerifyTask Tests
```groovy
class DockerRegistryImageVerifyTaskTest extends Specification {
    // Full image reference parsing
    // Multi-registry verification
    // Authentication handling
    // Error scenarios (auth failures, network issues)
    // Edge cases (malformed references, missing registries)
}
```

### Phase 2: Enhanced SaveSpec Tests
```groovy
class SaveSpecTest extends Specification {
    // Authentication configuration (IDENTICAL to PublishSpec tests)
    // PullIfMissing + auth combinations
    // Auth validation scenarios
    // DSL configuration tests for all auth types
    // No auth block behavior (public registry assumption)
    // Registry extraction from sourceRef
    // Consistency with PublishSpec.auth DSL
}
```

### Phase 3: Enhanced DockerSaveTask Tests
```groovy
class DockerSaveTaskTest extends Specification {
    // Add new test cases:
    def "task pulls with username/password auth when pullIfMissing=true and auth configured"()
    def "task pulls with token auth when pullIfMissing=true and auth configured"()
    def "task pulls without auth when pullIfMissing=true and no auth configured"()
    def "task fails gracefully when pullIfMissing=true, no auth, but private registry"()
    def "task ignores auth configuration when pullIfMissing=false"()
    def "task validates auth configuration completeness"()
    def "task extracts registry address from sourceRef correctly"()
    def "task handles malformed sourceRef gracefully"()
}
```

### Phase 4: Registry Management Tests
```groovy
class DockerRegistryStartTaskTest extends Specification {
    // Registry container lifecycle
    // Port allocation
    // Authentication setup
    // SSL/TLS configuration
    // Cleanup mechanisms
    // Emergency cleanup scenarios
}

class RegistryTestFixtureTest extends Specification {
    // Container tracking and cleanup
    // Port allocation and release
    // Volume and network cleanup
    // Emergency cleanup effectiveness
    // Health check and recovery
}
```

### Phase 5: Integration Test Coverage
```groovy
// Comprehensive integration test matrix:
// - Multiple LOCAL registries (2-3 per test)
// - Authenticated + unauthenticated combinations
// - Save with pullIfMissing + auth (all auth types)
// - Publish + verify roundtrip testing
// - Error handling and cleanup verification
// - Mixed public/private registry scenarios
// - Cleanup robustness testing (simulated failures)
// - NO PUBLIC REGISTRY TESTING (future item)
```

## Implementation Priority

1. **HIGH**: Consolidate buildSrc directories (delete root buildSrc)
2. **HIGH**: Add authentication to DockerSaveTask for pullIfMissing (consistent with publish auth DSL)
3. **HIGH**: Implement robust cleanup infrastructure for registry containers in plugin-integration-test buildSrc
4. **HIGH**: Fix DockerRegistryImageVerifyTask API (supports full image names)
5. **MEDIUM**: Create registry container management infrastructure
6. **MEDIUM**: Update plugin-integration-test/buildSrc/README.md with registry documentation
7. **MEDIUM**: Multi-registry verification support
8. **MEDIUM**: Comprehensive save + pull authentication integration tests
9. **LOW**: Enhanced integration test scenarios

## Key Design Decisions

1. **BuildSrc Consolidation**: Use plugin-integration-test/buildSrc as single source of truth
2. **No `serverAddress` in auth blocks**: Registry address always extracted from image references (sourceRef or publish tags)
3. **DSL consistency**: Save auth DSL is IDENTICAL to publish auth DSL for user consistency
4. **No authentication = public registry**: When no `auth` block is provided, assume public registry access
5. **Explicit authentication**: Require explicit `auth` configuration for private registries
6. **Fail-fast validation**: Validate authentication configuration completeness at task execution time
7. **Local testing only**: Integration tests use local registry containers, NOT public registries
8. **Future public registry support**: Requires account setup and credential management (separate initiative)
9. **Robust cleanup**: Multiple layers of cleanup (finalizers, shutdown hooks, emergency tasks)
10. **Container labeling**: All test containers labeled for emergency cleanup identification
11. **Resource tracking**: Track all allocated resources (containers, volumes, networks, ports) for cleanup

## Cleanup Strategy Details

### Multiple Cleanup Layers
1. **Primary Cleanup**: Gradle task finalizers (`finalizedBy 'stopTestRegistries'`)
2. **Secondary Cleanup**: JVM shutdown hooks registered at test start
3. **Emergency Cleanup**: Dedicated cleanup tasks for orphaned resources
4. **CI/CD Cleanup**: Comprehensive cleanup tasks for build environments

### Resource Tracking
- **Container Tracking**: Map of registry names to container IDs
- **Port Tracking**: Set of allocated ports for release
- **Volume Tracking**: Set of created volumes for cleanup
- **Network Tracking**: Set of created networks for cleanup
- **Label-Based Discovery**: Find orphaned resources by test labels

### Failure Scenarios Handled
- **Test Interruption**: JVM shutdown hooks catch Ctrl+C and cleanup
- **Test Framework Failure**: Emergency cleanup tasks find orphaned containers
- **Container Health Issues**: Health checks detect and clean up unhealthy registries
- **Port Conflicts**: Dynamic port allocation with conflict resolution
- **CI/CD Environment**: Comprehensive cleanup for shared build agents

## Migration Steps

### Step 1: BuildSrc Consolidation
1. Verify all integration tests use plugin-integration-test buildSrc
2. Check for any references to root buildSrc in project files
3. Delete `/gradle-docker/buildSrc/` directory
4. Run integration tests to confirm no breakage

### Step 2: Enhance BuildSrc Infrastructure
1. Add new registry management tasks to plugin-integration-test buildSrc
2. Enhance docker-image-testing.gradle with registry functions
3. Update DockerRegistryImageVerifyTask for full image name support
4. Add RegistryTestFixture utility class

### Step 3: Documentation Update
1. Update plugin-integration-test/buildSrc/README.md with registry sections
2. Document all new registry functions and cleanup mechanisms
3. Provide comprehensive examples for multi-registry testing

### Step 4: Authentication Implementation
1. Add authentication support to DockerSaveTask
2. Ensure DSL consistency between save and publish auth
3. Implement registry address extraction from sourceRef

### Step 5: Testing and Validation
1. Implement comprehensive unit tests for all new functionality
2. Create integration test scenarios with registry authentication
3. Validate cleanup mechanisms under failure conditions

This comprehensive plan consolidates the buildSrc infrastructure, implements robust registry testing capabilities, and ensures no test resources are left orphaned in any failure scenario.