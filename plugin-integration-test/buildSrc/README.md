# Docker Image Testing BuildSrc

## Overview

- **Purpose**: Reusable Docker image clean/verify tasks for integration tests
- **Scope**: Only for plugin-integration-test projects
- **Compatibility**: Gradle 9 configuration cache compatible

## Usage

### Basic Setup

1. Apply the convention plugin in your integration test `build.gradle`:
   ```groovy
   apply from: "$rootDir/buildSrc/src/main/groovy/docker-image-testing.gradle"
   ```

2. Register tasks with your image list:
   ```groovy
   registerDockerImageTasks(project, [
       'time-server:1.0.0',
       'time-server:latest'
   ])
   ```

3. Use in your `integrationTest` task:
   ```groovy
   tasks.register('integrationTest') {
       dependsOn 'cleanDockerImages'
       dependsOn 'dockerBuild'
       dependsOn 'verifyDockerImages'
       
       // Ensure proper task ordering
       tasks.dockerBuild.mustRunAfter tasks.cleanDockerImages
       tasks.verifyDockerImages.mustRunAfter tasks.dockerBuild
   }
   ```

### Image Name Format

- **Format**: `"image-name:tag"`
- **Examples**: 
  - Single image: `["time-server:1.0.0", "time-server:latest"]`
  - Multiple images: `["app:1.0", "db:latest", "cache:dev"]`
- **Requirements**: Images must be valid Docker image names with tags

### Task Registration

- **Function**: `registerDockerImageTasks(project, imageList)`
- **Automatic task creation**: 
  - `cleanDockerImages` - Removes specified images
  - `verifyDockerImages` - Verifies specified images exist
- **Group**: `verification`

### Integration with integrationTest

- **Dependency order**: clean → build → verify
- **Use `mustRunAfter`** for proper task ordering
- **Build task**: Use `dockerBuild` (not specific image tasks)

## Custom Tasks

### DockerImageCleanTask

- **Purpose**: Remove specified Docker images before building
- **Input**: List of image names (`ListProperty<String>`)
- **Behavior**: 
  - Gracefully handles missing images (logs warning, continues)
  - Uses `docker rmi -f` for forced removal
  - Logs all actions for debugging

### DockerImageVerifyTask

- **Purpose**: Verify specified Docker images exist after building
- **Input**: List of image names (`ListProperty<String>`)
- **Behavior**: 
  - Fails fast if any image is missing
  - Reports exactly which images are missing
  - Logs success/failure for each image

## Configuration Cache Compatibility

- **Uses ProcessBuilder** (not `project.exec`) for Docker commands
- **Serializable inputs only** (`@Input ListProperty<String>`)
- **No project references** in task actions
- **Tested with** `--configuration-cache` flag

## Testing

- **Unit tests** for custom tasks (see `src/test/groovy/`)
- **Integration tests** for full workflow
- **Configuration cache validation** in test suite
- **Mock ProcessBuilder** for unit tests (no actual Docker calls)

## Examples

### Basic Integration Test

```groovy
// In docker/my-test/build.gradle
apply from: "$rootDir/buildSrc/src/main/groovy/docker-image-testing.gradle"

registerDockerImageTasks(project, [
    'my-app:1.0.0',
    'my-app:latest'
])

tasks.register('integrationTest') {
    description = 'Run complete Docker integration test: clean → build → verify'
    group = 'verification'
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerBuild'
    dependsOn 'verifyDockerImages'
    
    // Ensure proper task ordering
    tasks.dockerBuild.mustRunAfter tasks.cleanDockerImages
    tasks.verifyDockerImages.mustRunAfter tasks.dockerBuild
}
```

### Multi-Image Test

```groovy
registerDockerImageTasks(project, [
    'frontend:1.0.0',
    'frontend:latest',
    'backend:1.0.0', 
    'backend:latest',
    'database:5.7'
])
```

### Running Tests

```bash
# Run specific integration test
./gradlew :docker:my-test:integrationTest

# Run with configuration cache
./gradlew integrationTest --configuration-cache

# Run all integration tests
./gradlew integrationTest
```

## Error Handling

### Clean Task Errors
- **Missing images**: Logged as info, task continues
- **Docker daemon unavailable**: Task fails with clear error
- **Permission issues**: Task fails with Docker error details

### Verify Task Errors
- **Missing images**: Task fails immediately with list of missing images
- **Docker daemon unavailable**: Task fails with clear error
- **Malformed image names**: Logged as warning, task continues

## Best Practices

1. **Use explicit image lists** - Don't auto-detect from docker DSL
2. **Include all expected tags** - Both versioned and latest tags
3. **Test configuration cache** - Always test with `--configuration-cache`
4. **Proper task ordering** - Use `mustRunAfter` for dependencies
5. **Clear error messages** - Let tasks fail fast with specific errors

## Troubleshooting

### Common Issues

1. **"Docker command not found"**
   - Ensure Docker is installed and on PATH
   - Check Docker daemon is running

2. **"Configuration cache problems"**
   - Verify no `project.exec` usage in task actions
   - Check task inputs are serializable

3. **"Images not found during verify"**
   - Check image names match exactly what docker build creates
   - Verify docker build actually succeeded

4. **"Permission denied removing images"**
   - Use `docker rmi -f` (already implemented)
   - Check Docker daemon permissions

### Debug Commands

```bash
# Check what images exist
docker images

# Check specific image
docker images -q time-server:1.0.0

# Debug with Gradle info logging
./gradlew integrationTest --info

# Debug configuration cache
./gradlew integrationTest --configuration-cache --info
```