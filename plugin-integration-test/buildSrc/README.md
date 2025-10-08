# Docker Image Testing Library

A composable library of Gradle tasks for Docker image testing in integration test scenarios. This buildSrc module provides reusable functions for creating Docker image verification tasks with maximum flexibility.

## Overview

The Docker Image Testing Library provides individual functions for different aspects of Docker image testing:

- **Clean Images**: Remove Docker images before building for clean integration tests
- **Verify Built Images**: Check that Docker images exist after building
- **Verify Saved Images**: Validate that Docker images have been saved to files
- **Verify Registry Images**: Confirm that Docker images exist in Docker registries
- **Build Workflow**: Convenience function combining clean and verify built operations

## Quick Start

### Basic Usage

Apply the library in your integration test `build.gradle`:

```groovy
// Apply the Docker image testing library
apply from: "$rootDir/buildSrc/src/main/groovy/docker-image-testing.gradle"

// Use individual functions for maximum flexibility
registerCleanDockerImagesTask(project, ['my-app:1.0.0', 'my-app:latest'])
registerVerifyBuiltImagesTask(project, ['my-app:1.0.0', 'my-app:latest']) 

// Or use convenience workflow function
registerBuildWorkflowTasks(project, ['my-app:1.0.0', 'my-app:latest'])
```

### Integration Test Example

```groovy
tasks.register('integrationTest') {
    description = 'Run complete Docker integration test workflow'
    group = 'verification'
    
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerBuild'  
    dependsOn 'verifyDockerImages'
    
    // Ensure proper task ordering
    tasks.dockerBuild.mustRunAfter tasks.cleanDockerImages
    tasks.verifyDockerImages.mustRunAfter tasks.dockerBuild
}
```

## Available Functions

### registerCleanDockerImagesTask

Removes Docker images before building to ensure clean integration tests.

```groovy
registerCleanDockerImagesTask(project, imageNames)
```

**Parameters:**
- `project`: The Gradle project to register tasks on
- `imageNames`: List of Docker image names in format `"image-name:tag"`

**Creates Task:** `cleanDockerImages` in the `verification` group

**Example:**
```groovy
registerCleanDockerImagesTask(project, [
    'my-app:1.0.0',
    'my-app:latest'
])
```

### registerVerifyBuiltImagesTask

Verifies that Docker images exist after building.

```groovy
registerVerifyBuiltImagesTask(project, imageNames)
```

**Parameters:**
- `project`: The Gradle project to register tasks on
- `imageNames`: List of Docker image names in format `"image-name:tag"`

**Creates Task:** `verifyDockerImages` in the `verification` group

**Example:**
```groovy
registerVerifyBuiltImagesTask(project, [
    'my-app:1.0.0',
    'my-app:latest',
    'my-database:5.7'
])
```

### registerVerifySavedImagesTask

Verifies that Docker image files have been saved to specified file paths.

```groovy
registerVerifySavedImagesTask(project, filePaths)
```

**Parameters:**
- `project`: The Gradle project to register tasks on
- `filePaths`: List of file paths to saved Docker images (full paths with extensions)

**Supported Formats:**
- `.tar` - Uncompressed tar archive
- `.tar.gz` - Gzip compressed tar archive
- `.tar.bz2` - Bzip2 compressed tar archive  
- `.tar.xz` - XZ compressed tar archive
- `.zip` - ZIP compressed archive

**Creates Task:** `verifySavedDockerImages` in the `verification` group

**Example:**
```groovy
registerVerifySavedImagesTask(project, [
    'build/saved/my-app-1.0.0.tar',
    'build/saved/my-app-latest.tar.gz',
    'build/saved/my-database-5.7.tar.bz2'
])
```

### registerVerifyRegistryImagesTask

Verifies that Docker images exist in a specified Docker registry.

```groovy
registerVerifyRegistryImagesTask(project, imageNames, registryUrl)
```

**Parameters:**
- `project`: The Gradle project to register tasks on
- `imageNames`: List of Docker image names in format `"image-name:tag"`
- `registryUrl`: The registry URL (e.g., `"localhost:5000"`, `"docker.io"`)

**Registry Support:**
- ✅ Can check image existence without authentication for public registries like Docker Hub
- 🚧 Will need authentication support for checking images in registries that require authentication
- 🚧 Will need authentication support for publishing to public registries like Docker Hub

**Creates Task:** `verifyRegistryDockerImages` in the `verification` group

**Example:**
```groovy
// Local private registry for testing
registerVerifyRegistryImagesTask(project, [
    'my-app:1.0.0',
    'my-app:latest'
], 'localhost:5000')

// Public Docker Hub registry
registerVerifyRegistryImagesTask(project, [
    'alpine:latest'
], 'docker.io')
```

### registerVerifyBuildArgsTask

Verifies that Docker images were built with expected build arguments.

```groovy
registerVerifyBuildArgsTask(project, buildArgsPerImage)
```

**Parameters:**
- `project`: The Gradle project to register tasks on
- `buildArgsPerImage`: Map of image references to their expected build args

**Format:**
```groovy
[
    'image:tag': ['ARG_NAME': 'expected_value', ...],
    'another:tag': [:]  // Empty map = expect zero build args
]
```

**Creates Task:** `verifyDockerBuildArgs` in the `verification` group

**How It Works:**
- Executes `docker history` to extract build arg values from image layers
- Parses the history output for build arg annotations (pattern: `|N ARG=value`)
- Compares extracted args against expected values
- Supports verification of zero build args (no annotation found)

**Example:**
```groovy
// Verify multiple build args
registerVerifyBuildArgsTask(project, [
    'my-app:1.0.0': [
        'JAR_FILE': 'app-1.0.0.jar',
        'BUILD_VERSION': '1.0.0'
    ],
    'my-app:latest': [
        'JAR_FILE': 'app-1.0.0.jar',
        'BUILD_VERSION': '1.0.0'
    ]
])

// Verify single build arg
registerVerifyBuildArgsTask(project, [
    'my-service:dev': [
        'ENV': 'development'
    ]
])

// Verify zero build args (empty map)
registerVerifyBuildArgsTask(project, [
    'base-image:latest': [:]
])
```

**Task Ordering:**
The verification task must run after build and tag operations:

```groovy
tasks.register('integrationTest') {
    dependsOn 'dockerBuild'
    dependsOn 'dockerTag'
    dependsOn 'verifyDockerBuildArgs'
}

tasks.named('verifyDockerBuildArgs') {
    mustRunAfter 'dockerBuild'
    mustRunAfter 'dockerTag'
}
```

### registerBuildWorkflowTasks

Convenience function that registers both clean and verify built image tasks for the common Docker build workflow.

```groovy
registerBuildWorkflowTasks(project, imageNames)
```

**Parameters:**
- `project`: The Gradle project to register tasks on
- `imageNames`: List of Docker image names in format `"image-name:tag"`

**Creates Tasks:**
- `cleanDockerImages` - Remove images before building
- `verifyDockerImages` - Verify images exist after building

**Example:**
```groovy
registerBuildWorkflowTasks(project, [
    'my-app:1.0.0',
    'my-app:latest'
])
```

## Task Composition Examples

### Build → Save → Verify Saved Workflow

```groovy
// Register individual tasks for flexible composition
registerCleanDockerImagesTask(project, ['my-app:1.0.0'])
registerVerifyBuiltImagesTask(project, ['my-app:1.0.0'])
registerVerifySavedImagesTask(project, ['build/saved/my-app-1.0.0.tar.gz'])

tasks.register('buildSaveVerifyWorkflow') {
    description = 'Build, save, and verify Docker image workflow'
    group = 'verification'
    
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerBuild'
    dependsOn 'dockerSave'
    dependsOn 'verifyDockerImages'
    dependsOn 'verifySavedDockerImages'
    
    // Task ordering
    tasks.dockerBuild.mustRunAfter tasks.cleanDockerImages
    tasks.dockerSave.mustRunAfter tasks.dockerBuild
    tasks.verifyDockerImages.mustRunAfter tasks.dockerSave
    tasks.verifySavedDockerImages.mustRunAfter tasks.dockerSave
}
```

### Build → Tag → Publish → Verify Registry Workflow

```groovy
// Note: No clean task - we want to keep the built image for tagging/publishing
registerVerifyBuiltImagesTask(project, ['my-app:1.0.0'])
registerVerifyRegistryImagesTask(project, ['my-app:1.0.0'], 'localhost:5000')

tasks.register('buildPublishVerifyWorkflow') {
    description = 'Build, publish, and verify registry workflow'
    group = 'verification'
    
    dependsOn 'dockerBuild'
    dependsOn 'dockerTag' 
    dependsOn 'dockerPublish'
    dependsOn 'verifyDockerImages'
    dependsOn 'verifyRegistryDockerImages'
    
    // Task ordering
    tasks.dockerTag.mustRunAfter tasks.dockerBuild
    tasks.dockerPublish.mustRunAfter tasks.dockerTag
    tasks.verifyDockerImages.mustRunAfter tasks.dockerBuild
    tasks.verifyRegistryDockerImages.mustRunAfter tasks.dockerPublish
}
```

### Multiple Image Formats and Registries

```groovy
def appImages = ['my-app:1.0.0', 'my-app:latest']
def savedFiles = [
    'build/saved/my-app-1.0.0.tar',
    'build/saved/my-app-1.0.0.tar.gz', 
    'build/saved/my-app-latest.tar.bz2'
]

// Register all verification types
registerBuildWorkflowTasks(project, appImages)
registerVerifySavedImagesTask(project, savedFiles)
registerVerifyRegistryImagesTask(project, appImages, 'localhost:5000')
registerVerifyRegistryImagesTask(project, ['my-app:1.0.0'], 'docker.io')

tasks.register('comprehensiveImageTest') {
    description = 'Comprehensive Docker image testing across formats and registries'
    group = 'verification'
    
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerBuild'
    dependsOn 'dockerSave'
    dependsOn 'dockerPublish'
    dependsOn 'verifyDockerImages'
    dependsOn 'verifySavedDockerImages'
    dependsOn 'verifyRegistryDockerImages'
}
```

## Docker Registry Integration Testing

The library provides comprehensive support for Docker registry integration testing with authentication, container lifecycle management, and cleanup mechanisms.

### Registry Management Plugin

Apply the registry management plugin for full registry lifecycle control:

```groovy
// Apply registry management plugin
apply plugin: RegistryManagementPlugin

// Configure test registries using DSL
registryManagement {
    // Simple unauthenticated registry
    registry('local-registry', 5000)
    
    // Authenticated registry  
    authenticatedRegistry('auth-registry', 5001, 'testuser', 'testpass')
    
    // Custom configured registry
    registry('custom-registry', 5002) {
        withAuth('admin', 'secret')
        withLabels([
            'test-type': 'integration',
            'project': 'gradle-docker'
        ])
    }
}

// Registry tasks are automatically available:
// - startTestRegistries
// - stopTestRegistries  
// - cleanupTestRegistries
```

### Registry Test Workflow Function

Use the complete registry workflow for comprehensive integration testing:

```groovy
// Configure registries and image references
def registryConfigs = [
    new RegistryTestFixture.RegistryConfig('main', 5000),
    new RegistryTestFixture.RegistryConfig('secure', 5001).withAuth('user', 'pass')
]

def imageReferences = [
    'localhost:5000/my-app:latest',
    'localhost:5001/secure-app:latest'
]

// Register complete workflow
registerRegistryTestWorkflow(project, registryConfigs, imageReferences)
```

### Registry Utility Functions

#### registerVerifyRegistryImagesTask

Verifies that Docker images exist in specified registries.

```groovy
registerVerifyRegistryImagesTask(project, imageReferences)
```

**Parameters:**
- `project`: The Gradle project to register tasks on
- `imageReferences`: List of full image references in format `"registry/image-name:tag"`

**Creates Task:** `verifyRegistryDockerImages` in the `verification` group

**Example:**
```groovy
registerVerifyRegistryImagesTask(project, [
    'localhost:5000/my-app:1.0.0',
    'localhost:5000/my-app:latest',
    'localhost:5001/secure-service:1.0'
])
```

#### registerRegistryTestWorkflow  

Creates a complete workflow for registry testing with authentication support.

```groovy
registerRegistryTestWorkflow(project, registryConfigs, imageReferences)
```

**Parameters:**
- `project`: The Gradle project to register tasks on
- `registryConfigs`: List of `RegistryTestFixture.RegistryConfig` objects
- `imageReferences`: List of full image references to verify

**Creates Workflow:**
1. Starts test registries with authentication
2. Runs integration tests  
3. Verifies published images in registries
4. Stops and cleans up registries

### Registry Container Management

#### RegistryTestFixture

Core utility providing robust registry container lifecycle management:

```groovy
// Create fixture
def fixture = new RegistryTestFixture()

// Configure registries
def configs = [
    new RegistryTestFixture.RegistryConfig('local', 5000),
    new RegistryTestFixture.RegistryConfig('auth', 5001).withAuth('user', 'pass')
]

// Start registries  
def registries = fixture.startTestRegistries(configs)

// Use registries in tests...
registries.each { name, info ->
    println "Registry ${name} available at ${info.url}"
}

// Clean shutdown
fixture.stopAllRegistries()

// Emergency cleanup (if needed)
fixture.emergencyCleanup()
```

#### Registry Configuration Options

```groovy
// Basic registry
def basicConfig = new RegistryTestFixture.RegistryConfig('basic', 5000)

// With authentication
def authConfig = new RegistryTestFixture.RegistryConfig('secure', 5001)
    .withAuth('username', 'password')

// With custom labels
def labeledConfig = new RegistryTestFixture.RegistryConfig('labeled', 5002)
    .withLabels([
        'environment': 'test',
        'component': 'integration'
    ])

// Combined configuration
def fullConfig = new RegistryTestFixture.RegistryConfig('full', 5003)
    .withAuth('admin', 'secret')  
    .withLabels(['purpose': 'testing'])
```

### Registry Integration Examples

#### Complete Registry Test Scenario

```groovy
// Configure multiple registries for comprehensive testing
apply plugin: RegistryManagementPlugin

registryManagement {
    registry('public-sim', 5000)                    // Simulates public registry
    authenticatedRegistry('private', 5001, 'dev', 'devpass')  // Private registry
    registry('backup', 5002) {                     // Backup registry
        withAuth('backup', 'backuppass')
        withLabels(['tier': 'backup'])
    }
}

// Register verification for published images
registerVerifyRegistryImagesTask(project, [
    'localhost:5000/my-app:latest',
    'localhost:5000/my-app:1.0.0',
    'localhost:5001/private-service:latest',
    'localhost:5002/backup-service:stable'
])

// Integration test task
tasks.register('registryIntegrationTest') {
    group = 'verification'
    description = 'Complete registry integration test with authentication'
    
    dependsOn 'startTestRegistries'
    dependsOn 'dockerBuild'
    dependsOn 'dockerPublish'  
    dependsOn 'verifyRegistryDockerImages'
    finalizedBy 'stopTestRegistries'
    
    // Ensure proper task ordering
    tasks.dockerPublish.mustRunAfter tasks.dockerBuild
    tasks.verifyRegistryDockerImages.mustRunAfter tasks.dockerPublish
}
```

#### Authentication Testing Example

```groovy
// Test both authenticated and unauthenticated scenarios
tasks.register('authenticationTest') {
    dependsOn 'startTestRegistries'
    finalizedBy 'stopTestRegistries'
    
    doLast {
        def registries = project.extensions.testRegistries
        
        // Test unauthenticated access
        def publicRegistry = registries['public-sim']
        println "Testing unauthenticated access to ${publicRegistry.url}"
        
        // Test authenticated access  
        def privateRegistry = registries['private']
        println "Testing authenticated access to ${privateRegistry.url}"
        println "Using credentials: ${privateRegistry.username}"
        
        // Your authentication test logic here...
    }
}
```

### Cleanup and Safety Features

#### Multiple Cleanup Layers

1. **Graceful Shutdown**: `stopAllRegistries()` - Clean container shutdown
2. **Finalizer Tasks**: Automatic cleanup via `finalizedBy` 
3. **JVM Shutdown Hooks**: Emergency cleanup on unexpected termination
4. **Emergency Cleanup**: `emergencyCleanup()` - Force cleanup orphaned resources
5. **Session-based Isolation**: Unique session IDs prevent conflicts

#### Resource Tracking

- **Container Tracking**: All started containers tracked by ID
- **Port Allocation**: Prevents port conflicts across tests
- **Volume Management**: Created volumes tracked and cleaned up
- **Network Management**: Test networks properly removed
- **Label-based Discovery**: Find orphaned resources by labels

#### Health Monitoring

```groovy
// Verify registry health during tests
def fixture = new RegistryTestFixture()
fixture.verifyRegistryHealth()  // Checks all running registries

// Custom health verification in tasks
tasks.register('verifyRegistryHealth') {
    dependsOn 'startTestRegistries'
    
    doLast {
        def registries = project.extensions.testRegistries
        registries.each { name, info ->
            // Custom health check logic
            def healthyUrl = "http://${info.url}/v2/"
            // ... health check implementation
        }
    }
}
```

## Configuration Cache Compatibility

All tasks are fully compatible with Gradle's configuration cache for optimal build performance:

- Uses `ListProperty<String>` and `Property<String>` for lazy evaluation
- Avoids capturing `Project` references in task actions
- Uses `ProcessBuilder` for external command execution instead of `project.exec`

## Testing

The library includes comprehensive test coverage:

### Unit Tests
- **DockerImageCleanTaskTest** - Tests clean task behavior
- **DockerImageVerifyTaskTest** - Tests built image verification
- **DockerSavedImageVerifyTaskTest** - Tests saved file verification
- **DockerRegistryImageVerifyTaskTest** - Tests registry verification
- **DockerImageTestingLibraryTest** - Tests all registration functions

### Integration Tests
- **DockerImageTestingSimpleIntegrationTest** - Real Docker command integration tests

Run tests:
```bash
./gradlew :buildSrc:test
```

## Architecture

The library follows these design principles:

- **Single Responsibility**: Each function has one clear purpose
- **Composable**: Mix and match functions for different test scenarios  
- **Explicit**: Test intent is clear from function names
- **Configuration Cache Compatible**: Uses Gradle 9 Provider API patterns
- **Cross-Platform**: Uses ProcessBuilder for Docker command execution

## Future Enhancements

- **Registry Authentication**: Support for authenticated registry operations
- **Multiple Compression Format Support**: Enhanced compression options for saved images
- **Public Registry Publishing**: Full Docker Hub integration with authentication
- **Advanced Image Verification**: Hash verification and metadata validation

## Project Structure

```
buildSrc/
├── build.gradle                    # BuildSrc configuration
├── README.md                       # This documentation
└── src/
    ├── main/groovy/
    │   ├── docker-image-testing.gradle         # Core library functions
    │   ├── registry-integration-example.gradle # Registry usage examples
    │   ├── DockerImageCleanTask.groovy          # Clean images task
    │   ├── DockerImageVerifyTask.groovy         # Verify built images task
    │   ├── DockerSavedImageVerifyTask.groovy    # Verify saved files task
    │   ├── DockerRegistryImageVerifyTask.groovy # Verify registry images task
    │   ├── RegistryTestFixture.groovy           # Registry container management
    │   ├── RegistryManagementPlugin.groovy     # Registry lifecycle plugin
    │   ├── RegistryManagementExtension.groovy  # Registry configuration DSL
    │   ├── DockerRegistryStartTask.groovy      # Start registry containers
    │   ├── DockerRegistryStopTask.groovy       # Stop registry containers
    │   └── DockerRegistryCleanupTask.groovy    # Emergency registry cleanup
    └── test/groovy/
        ├── DockerImageCleanTaskTest.groovy
        ├── DockerImageVerifyTaskTest.groovy  
        ├── DockerSavedImageVerifyTaskTest.groovy
        ├── DockerRegistryImageVerifyTaskTest.groovy
        ├── DockerImageTestingLibraryTest.groovy
        └── DockerImageTestingSimpleIntegrationTest.groovy
```