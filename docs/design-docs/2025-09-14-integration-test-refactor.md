# Integration Test Refactor: Reusable Docker Image Tasks

**Date**: 2025-09-14  
**Status**: Planned  
**Type**: Enhancement  

## Overview

Refactor the Docker image clean and verification tasks in the integration test suite to be reusable across future similar tests using Gradle buildSrc with convention plugins and manual configuration.

## Problem Statement

Currently, the `cleanDockerImages` and `verifyDockerImages` tasks in `plugin-integration-test/docker/all-in-one-dockerfile-default/build.gradle` (lines 99-176) are:
- Hardcoded with specific image names `['time-server:1.0.0', 'time-server:latest']`
- Duplicated logic that will need to be copied for each new integration test
- Tightly coupled to specific image names

Additionally, line 183 incorrectly calls `dockerBuildTimeServer` instead of `dockerBuild`, which should build all images defined in the docker DSL.

## Solution Approach

**Selected Strategy**: Convention Plugin with Manual Configuration (Option 2)
- Explicit configuration of what is being tested
- Avoids mixing integration test with implementation being tested
- No auto-detection from docker DSL to maintain clear separation of concerns

## Implementation Plan

### Phase 1: Create buildSrc Infrastructure

**Location**: `plugin-integration-test/buildSrc/`

**Structure**:
```
plugin-integration-test/
├── buildSrc/
│   ├── build.gradle
│   ├── src/
│   │   ├── main/groovy/
│   │   │   ├── DockerImageCleanTask.groovy
│   │   │   ├── DockerImageVerifyTask.groovy
│   │   │   └── docker-image-testing.gradle
│   │   └── test/groovy/
│   │       ├── DockerImageCleanTaskTest.groovy
│   │       └── DockerImageVerifyTaskTest.groovy
│   └── README.md
```

### Phase 2: Custom Task Types with Manual Configuration

**Custom Task Implementation**:
```groovy
// DockerImageCleanTask.groovy
abstract class DockerImageCleanTask extends DefaultTask {
    @Input
    abstract ListProperty<String> getImageNames()
    
    @TaskAction
    void cleanImages() {
        // ProcessBuilder logic - configuration cache compatible
    }
}

// DockerImageVerifyTask.groovy  
abstract class DockerImageVerifyTask extends DefaultTask {
    @Input
    abstract ListProperty<String> getImageNames()
    
    @TaskAction
    void verifyImages() {
        // ProcessBuilder logic - configuration cache compatible
    }
}
```

**Convention Plugin**:
```groovy
// docker-image-testing.gradle
void registerDockerImageTasks(Project project, List<String> imageNames) {
    project.tasks.register('cleanDockerImages', DockerImageCleanTask) {
        imageNames.set(imageNames)
        group = 'verification'
        description = 'Remove Docker images before building to ensure clean integration test'
    }
    
    project.tasks.register('verifyDockerImages', DockerImageVerifyTask) {
        imageNames.set(imageNames)  
        group = 'verification'
        description = 'Verify Docker images were created with expected tags'
    }
}
```

### Phase 3: Configuration Cache Compatibility Checks

**What to check**: Custom tasks in buildSrc, not integration test code

**Implementation Strategy**:

1. **Task Input/Output Annotations**:
   ```groovy
   abstract class DockerImageCleanTask extends DefaultTask {
       @Input
       abstract ListProperty<String> getImageNames()  // ✅ Serializable
       
       // ❌ Avoid: Project references, closures, non-serializable objects
   }
   ```

2. **ProcessBuilder Usage** (already implemented):
   ```groovy
   // ✅ Configuration cache compatible
   def process = new ProcessBuilder('docker', 'images', '-q', imageName)
   
   // ❌ Avoid: project.exec, task.project
   ```

3. **Testing Configuration Cache**:
   ```groovy
   // In unit tests
   @Test
   void "task is configuration cache compatible"() {
       def result = gradle.withArguments('cleanDockerImages', '--configuration-cache')
           .build()
       
       assert result.output.contains('Configuration cache entry stored')
       assert !result.output.contains('problems were found')
   }
   ```

4. **Gradle Test Kit Validation**:
   ```groovy
   // Test that tasks work with configuration cache enabled
   def result = gradle.withArguments('integrationTest', '--configuration-cache')
       .build()
   ```

### Phase 4: Documentation

**File**: `plugin-integration-test/buildSrc/README.md`

**Content Structure**:
```markdown
# Docker Image Testing BuildSrc

## Overview
- Purpose: Reusable Docker image clean/verify tasks for integration tests
- Scope: Only for plugin-integration-test projects

## Usage

### Basic Setup
- Apply convention plugin
- Register tasks with image list
- Use in integrationTest task

### Image Name Format
- Format: "image-name:tag"
- Examples: ["time-server:1.0.0", "time-server:latest"]
- Multiple images: ["app:1.0", "db:latest", "cache:dev"]

### Task Registration
- registerDockerImageTasks(project, imageList)
- Automatic task creation: cleanDockerImages, verifyDockerImages
- Group: 'verification'

### Integration with integrationTest
- Dependency order: clean → build → verify
- mustRunAfter configuration

## Custom Tasks

### DockerImageCleanTask
- Purpose: Remove specified Docker images
- Input: List of image names
- Behavior: Graceful failure if image doesn't exist

### DockerImageVerifyTask  
- Purpose: Verify specified Docker images exist
- Input: List of image names
- Behavior: Fail if any image missing

## Configuration Cache Compatibility
- Uses ProcessBuilder (not project.exec)
- Serializable inputs only
- Tested with --configuration-cache

## Testing
- Unit tests for custom tasks
- Integration tests for full workflow
- Configuration cache validation

## Examples
- See: docker/all-in-one-dockerfile-default/build.gradle
- Pattern for new integration tests
```

### Phase 5: Unit Testing Strategy

**Test Structure**:
```
buildSrc/src/test/groovy/
├── DockerImageCleanTaskTest.groovy
├── DockerImageVerifyTaskTest.groovy
└── DockerImageTestingPluginTest.groovy
```

**Test Cases**:

1. **DockerImageCleanTaskTest**:
   - Task executes without error when images exist
   - Task handles missing images gracefully  
   - Task processes multiple images correctly
   - Configuration cache compatibility
   - Input validation (empty list, null values)

2. **DockerImageVerifyTaskTest**:
   - Task succeeds when all images exist
   - Task fails when images missing
   - Task reports which specific images are missing
   - Configuration cache compatibility

3. **Integration Test**:
   - Full workflow: clean → build → verify
   - Task registration works correctly
   - Task dependencies configured properly

**Mock Strategy**:
```groovy
// Mock ProcessBuilder for unit tests (don't actually call Docker)
class MockProcessBuilder extends ProcessBuilder {
    // Return predefined results for test scenarios
}
```

### Phase 6: Gradle 9 Provider API Compatibility

**What this means**: Use Gradle's lazy evaluation APIs properly

**Requirements**:

1. **Use Property/Provider Types**:
   ```groovy
   // ✅ Gradle 9 compatible
   @Input
   abstract ListProperty<String> getImageNames()
   
   // ❌ Avoid eager evaluation
   @Input  
   List<String> imageNames
   ```

2. **Lazy Configuration**:
   ```groovy
   // ✅ Deferred evaluation
   tasks.register('cleanDockerImages', DockerImageCleanTask) {
       imageNames.set(['time-server:1.0.0', 'time-server:latest'])
   }
   
   // ❌ Eager evaluation during configuration
   def images = ['time-server:1.0.0', 'time-server:latest']
   ```

3. **Configuration Cache Requirements**:
   - No Project references in task actions
   - No task.project access during execution
   - Serializable task inputs only

**How to ensure compatibility**:
1. **Use @Input annotations** on Property/Provider types
2. **Test with --configuration-cache** flag
3. **Avoid accessing project during task execution**
4. **Use Provider.map() for transformations**

### Phase 7: Fix dockerBuild Task Reference

**Current**: `dependsOn 'dockerBuildTimeServer'` (line 183)
**Updated**: `dependsOn 'dockerBuild'`

**Rationale**:
- `dockerBuild` builds ALL images defined in docker DSL
- `dockerBuildTimeServer` builds only the specific timeServer image
- Using `dockerBuild` makes integration tests work with any number of images
- Future-proofs for tests with multiple images

## Implementation Order

1. **Phase 7**: Fix `dockerBuild` reference (quick win, current session)
2. **Phase 1**: Create buildSrc structure  
3. **Phase 2**: Implement custom tasks and convention plugin
4. **Phase 3**: Add configuration cache compatibility
5. **Phase 4**: Write documentation
6. **Phase 5**: Add unit tests
7. **Phase 6**: Validate Gradle 9 Provider API usage
8. **Final**: Update integration test to use buildSrc

## Benefits

- ✅ Explicit configuration (no magic auto-detection)
- ✅ Reusable across future integration tests
- ✅ Testable buildSrc logic
- ✅ Configuration cache compatible
- ✅ Gradle 9 Provider API compliant
- ✅ Clear separation of concerns

## Usage Example

**After Implementation**:
```groovy
// In future integration test build.gradle
apply from: "$rootDir/buildSrc/src/main/groovy/docker-image-testing.gradle"

registerDockerImageTasks(project, [
    'my-app:1.0.0', 
    'my-app:latest',
    'my-db:2.1.0'
])

tasks.register('integrationTest') {
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerBuild'  
    dependsOn 'verifyDockerImages'
    
    tasks.dockerBuild.mustRunAfter tasks.cleanDockerImages
    tasks.verifyDockerImages.mustRunAfter tasks.dockerBuild
}
```

## Related Documentation

- [Gradle 9 Configuration Cache Requirements](https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html)
- [Sharing Build Logic Between Subprojects](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html)
- [Custom Task Types](https://docs.gradle.org/current/userguide/custom_tasks.html)
- [Gradle Provider API](https://docs.gradle.org/current/javadoc/org/gradle/api/provider/Provider.html)