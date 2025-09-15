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
    │   ├── docker-image-testing.gradle    # Library functions
    │   ├── DockerImageCleanTask.groovy     # Clean images task
    │   ├── DockerImageVerifyTask.groovy    # Verify built images task
    │   ├── DockerSavedImageVerifyTask.groovy    # Verify saved files task
    │   └── DockerRegistryImageVerifyTask.groovy # Verify registry images task
    └── test/groovy/
        ├── DockerImageCleanTaskTest.groovy
        ├── DockerImageVerifyTaskTest.groovy  
        ├── DockerSavedImageVerifyTaskTest.groovy
        ├── DockerRegistryImageVerifyTaskTest.groovy
        ├── DockerImageTestingLibraryTest.groovy
        └── DockerImageTestingSimpleIntegrationTest.groovy
```