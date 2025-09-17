# Registry Integration Testing Implementation Plan

## Overview

This document outlines the comprehensive implementation plan for Docker registry integration testing, including authentication support for the save task and robust multi-registry testing infrastructure.

## Current State Analysis

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

### Phase 1: Create Real Docker Registry Infrastructure

#### 1.1: Docker Registry Management Tasks
```groovy
// New buildSrc tasks needed:
- DockerRegistryStartTask      // Start test registry containers
- DockerRegistryStopTask       // Stop and clean up registries
- DockerRegistryConfigTask     // Configure authentication
- DockerRegistryCleanupTask    // Emergency cleanup for failed tests
```

**Features:**
- Start multiple registry containers (authenticated/unauthenticated)
- Dynamic port allocation to avoid conflicts
- SSL/TLS support for realistic testing
- Credential configuration and testing
- Full lifecycle management (start/stop/cleanup)
- **Robust cleanup mechanisms for test failures**

#### 1.2: Enhanced Registry Functions
```groovy
// Enhanced docker-image-testing.gradle functions:
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
```

#### 1.3: Robust Cleanup Infrastructure

**Container Lifecycle Management:**
```groovy
class RegistryTestFixture {
    private Map<String, String> runningContainers = [:]
    private Map<String, Integer> allocatedPorts = [:]

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

**Cleanup Implementation:**
```groovy
// Test finalizers in integration tests
tasks.register('integrationTest') {
    dependsOn 'startTestRegistries'
    dependsOn 'dockerImages'
    dependsOn 'verifyDockerImages'

    // CRITICAL: Always clean up, even on failure
    finalizedBy 'stopTestRegistries'

    doFirst {
        // Register JVM shutdown hook as backup
        Runtime.runtime.addShutdownHook(new Thread({
            logger.warn("Emergency cleanup triggered by JVM shutdown")
            project.extensions.findByType(RegistryTestFixture)?.emergencyCleanup()
        }))
    }
}

// Emergency cleanup task
tasks.register('emergencyRegistryCleanup') {
    description = 'Emergency cleanup of orphaned registry containers'
    group = 'verification'

    doLast {
        // Find and stop all containers with our test labels
        // Clean up volumes, networks, and allocated ports
        // Reset port allocation tracking
    }
}
```

### Phase 2: Fix and Enhance DockerRegistryImageVerifyTask

#### 2.1: Support Full Image Names
```groovy
abstract class DockerRegistryImageVerifyTask extends DefaultTask {
    @Input
    abstract ListProperty<String> getFullImageReferences()  // CHANGED: Accept full refs

    // REMOVED: registryUrl - now parsed from full image references

    @Input  // NEW: Authentication support
    abstract Property<AuthConfig> getAuthConfig()
}
```

#### 2.2: Multi-Registry Support
```groovy
// Support verification across multiple registries in single task
Map<String, List<String>> parseImagesByRegistry(List<String> fullImageReferences) {
    // Group images by registry URL extracted from full references
}
```

#### 2.3: Authentication Integration
```groovy
// Add authentication support for private registry verification
private void verifyWithAuth(String fullImageRef, AuthConfig auth) {
    // Use docker login + manifest inspect for authenticated verification
}
```

### Phase 3: Add Authentication to DockerSaveTask

#### 3.1: Extend SaveSpec with Authentication (Consistent with PublishSpec)
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

#### 3.2: Enhance DockerSaveTask
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

#### 3.3: Save Task Authentication DSL Examples

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

### Phase 4: Comprehensive Integration Test Framework with Robust Cleanup

#### 4.1: Multi-Registry Test Scenarios with Cleanup
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

    // Ensure proper task ordering
    tasks.dockerImages.mustRunAfter tasks.startTestRegistries
    tasks.verifyRegistryDockerImages.mustRunAfter tasks.dockerImages
    tasks.stopTestRegistries.mustRunAfter tasks.verifyRegistryDockerImages
}
```

#### 4.2: Registry Test Infrastructure with Cleanup
```groovy
// buildSrc registry management with robust cleanup:
class RegistryTestFixture {
    private static final String REGISTRY_LABEL = "gradle-docker-test-registry"
    private Map<String, String> runningContainers = [:]
    private Map<String, Integer> allocatedPorts = [:]
    private Set<String> createdVolumes = []
    private Set<String> createdNetworks = []

    Map<String, DockerRegistry> startTestRegistries() {
        try {
            // Start registries with proper labeling for cleanup
            // Register immediate cleanup hooks
            // Track all resources for cleanup
        } catch (Exception e) {
            // Emergency cleanup on startup failure
            emergencyCleanup()
            throw e
        }
    }

    void configureAuthentication(String registryName, AuthConfig auth) {
        // Configure auth with failure cleanup
    }

    void stopAllRegistries() {
        // Graceful shutdown with proper order
        runningContainers.each { name, containerId ->
            stopContainer(containerId)
            removeContainer(containerId)
        }
        cleanupVolumes()
        cleanupNetworks()
        releaseAllocatedPorts()
    }

    void emergencyCleanup() {
        logger.warn("Performing emergency cleanup of test registries")

        // Find and stop ALL containers with our test label
        def orphanedContainers = findContainersByLabel(REGISTRY_LABEL)
        orphanedContainers.each { containerId ->
            forceStopContainer(containerId)
            forceRemoveContainer(containerId)
        }

        // Clean up volumes created by test registries
        def orphanedVolumes = findVolumesByLabel(REGISTRY_LABEL)
        orphanedVolumes.each { volumeName ->
            forceRemoveVolume(volumeName)
        }

        // Clean up networks created by test registries
        def orphanedNetworks = findNetworksByLabel(REGISTRY_LABEL)
        orphanedNetworks.each { networkName ->
            forceRemoveNetwork(networkName)
        }

        // Release allocated ports
        releaseAllocatedPorts()

        logger.lifecycle("Emergency cleanup completed")
    }

    void verifyRegistryHealth() {
        // Health check all running registries
        // Clean up unhealthy containers
        runningContainers.each { name, containerId ->
            if (!isContainerHealthy(containerId)) {
                logger.warn("Unhealthy registry container detected: ${name}, cleaning up")
                stopContainer(containerId)
                removeContainer(containerId)
                runningContainers.remove(name)
            }
        }
    }

    // Port management with cleanup
    private int allocatePort() {
        // Find available port and track for cleanup
    }

    private void releaseAllocatedPorts() {
        allocatedPorts.clear()
    }

    // Container management with labeling for cleanup
    private String startContainer(String image, Map<String, String> config) {
        def labels = [
            (REGISTRY_LABEL): "true",
            "gradle-docker-test-session": UUID.randomUUID().toString()
        ]
        // Start container with labels for cleanup identification
    }
}
```

#### 4.3: Save with Authentication Integration Tests with Cleanup
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

#### 4.4: Emergency Cleanup Tasks
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

### Phase 5: Enhanced Verification Functions

#### 5.1: New Registry Verification Functions
```groovy
ext.registerMultiRegistryVerification = { Project project, Map<String, List<String>> imagesByRegistry ->
    // Verify images across multiple registries
}

ext.registerPublishWorkflowVerification = { Project project, PublishConfig config ->
    // End-to-end publish + verify workflow
}

ext.registerAuthenticatedRegistryVerification = { Project project, String registry, AuthConfig auth, List<String> images ->
    // Verify images in authenticated registry
}
```

#### 5.2: Save + Pull Integration Testing
```groovy
ext.registerSaveWithPullVerification = { Project project, SaveConfig config ->
    // Test save with pullIfMissing + authentication
    // Verify pulled image can be saved and loaded back
}

ext.registerAuthenticatedPullVerification = { Project project, AuthConfig auth, String sourceRef ->
    // Verify authentication works for pullIfMissing scenarios
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

1. **HIGH**: Add authentication to DockerSaveTask for pullIfMissing (consistent with publish auth DSL)
2. **HIGH**: Implement robust cleanup infrastructure for registry containers
3. **HIGH**: Fix DockerRegistryImageVerifyTask API (supports full image names)
4. **MEDIUM**: Create registry container management infrastructure
5. **MEDIUM**: Multi-registry verification support
6. **MEDIUM**: Comprehensive save + pull authentication integration tests
7. **LOW**: Enhanced integration test scenarios

## Key Design Decisions

1. **No `serverAddress` in auth blocks**: Registry address always extracted from image references (sourceRef or publish tags)
2. **DSL consistency**: Save auth DSL is IDENTICAL to publish auth DSL for user consistency
3. **No authentication = public registry**: When no `auth` block is provided, assume public registry access
4. **Explicit authentication**: Require explicit `auth` configuration for private registries
5. **Fail-fast validation**: Validate authentication configuration completeness at task execution time
6. **Local testing only**: Integration tests use local registry containers, NOT public registries
7. **Future public registry support**: Requires account setup and credential management (separate initiative)
8. **Robust cleanup**: Multiple layers of cleanup (finalizers, shutdown hooks, emergency tasks)
9. **Container labeling**: All test containers labeled for emergency cleanup identification
10. **Resource tracking**: Track all allocated resources (containers, volumes, networks, ports) for cleanup

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

This comprehensive plan ensures robust integration testing with proper cleanup mechanisms to prevent test resource leakage in any failure scenario.