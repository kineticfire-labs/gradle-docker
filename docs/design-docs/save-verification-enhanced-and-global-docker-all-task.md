# Enhanced Save Verification and Global Docker Tasks Implementation Plan

## Overview

This document outlines the comprehensive plan to fix the save DSL configuration issue and enhance the Docker plugin with better task architecture and verification capabilities.

## Issues Identified

### Issue 1: contextTask Dependency Bug
**Location**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy:258-261`

**Problem**: Save/publish task dependencies only consider `imageSpec.context.present` but not `imageSpec.contextTask != null`

**Current Buggy Code**:
```groovy
if (imageSpec.context.present) {
    project.tasks.findByName("dockerSave${capitalizedName}")?.dependsOn(buildTaskName)
    project.tasks.findByName("dockerPublish${capitalizedName}")?.dependsOn(buildTaskName)
}
```

**Root Cause**: Integration tests using `contextTask` don't get proper save task dependencies, so save tasks don't execute when `dockerBuild` runs.

### Issue 2: Missing Global Docker Operations Task
**Problem**: No single task to execute ALL configured Docker operations (build + tag + save + publish) for images

**Current Architecture**:
- Individual operation aggregates: `dockerBuild`, `dockerSave`, `dockerTag`, `dockerPublish`
- Per-image operations: `dockerBuildTimeServer`, `dockerSaveTimeServer`, etc.
- **Missing**: Complete operation aggregates per image and globally

### Issue 3: Save Verification Insufficient
**Problem**: Current `DockerSavedImageVerifyTask` only checks file existence, not Docker image validity

**Enhancement Needed**: Verify saved images can actually be loaded by Docker daemon

## Implementation Plan

### Phase 1: Critical Bug Fixes

#### 1.1 Fix contextTask Dependency Logic
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`

**Change at lines 258-261**:
```groovy
// FIXED VERSION:
def hasBuildContext = imageSpec.context.present || imageSpec.contextTask != null  
if (hasBuildContext) {
    project.tasks.findByName("dockerSave${capitalizedName}")?.dependsOn(buildTaskName)
    project.tasks.findByName("dockerPublish${capitalizedName}")?.dependsOn(buildTaskName)
}
```

**Rationale**: Both `context` and `contextTask` represent build contexts that should trigger save/publish dependencies.

#### 1.2 Add Comprehensive Unit Tests
**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/GradleDockerPluginTest.groovy`

**New Tests Required**:
- `"plugin configures save task dependencies for contextTask scenarios"`
- `"plugin configures publish task dependencies for contextTask scenarios"`
- `"plugin configures task dependencies for mixed context and contextTask scenarios"`

**Test Scenarios**:
1. Image with `contextTask` + save configuration → save task depends on build task
2. Image with `contextTask` + publish configuration → publish task depends on build task
3. Image with traditional `context` → existing behavior preserved
4. Image with neither `context` nor `contextTask` → no dependency setup

#### 1.3 Add Functional Tests
**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ContextTaskSaveFunctionalTest.groovy`

**Test Coverage**:
- End-to-end contextTask save scenario
- Verify task dependency execution order
- Verify actual save file creation

### Phase 2: Task Architecture Enhancement

#### 2.1 Create Per-Image Aggregate Tasks
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`

**New Task Creation Logic** (add to `registerDockerImageTasks` method):
```groovy
// Create per-image aggregate task
project.tasks.register("dockerImage${capitalizedName}") { task ->
    task.group = 'docker'
    task.description = "Run all configured Docker operations for image: ${imageSpec.name}"
    
    // Always depend on build and tag
    task.dependsOn("dockerBuild${capitalizedName}")
    task.dependsOn("dockerTag${capitalizedName}")
    
    // Conditionally depend on save and publish
    if (imageSpec.save.present) {
        task.dependsOn("dockerSave${capitalizedName}")
    }
    if (imageSpec.publish.present) {
        task.dependsOn("dockerPublish${capitalizedName}")
    }
}
```

#### 2.2 Create Global Aggregate Task
**Add to `registerAggregateTasksMethod`**:
```groovy
project.tasks.register('dockerImages') {
    group = 'docker'
    description = 'Run all configured Docker operations for all images'
    // Dependencies configured in configureTaskDependencies
}
```

**Add to `configureTaskDependencies` method**:
```groovy
// Configure global aggregate task
project.tasks.named('dockerImages') {
    def imageTaskNames = dockerExt.images.names.collect { "dockerImage${it.capitalize()}" }
    if (imageTaskNames) {
        dependsOn imageTaskNames
    }
}
```

#### 2.3 Updated Task Hierarchy
```
dockerImages (new) - Run all operations for all images
├── dockerImageTimeServer (new) - Run all operations for timeServer
│   ├── dockerBuildTimeServer - Build timeServer image
│   ├── dockerTagTimeServer - Tag timeServer image  
│   ├── dockerSaveTimeServer - Save timeServer image (if configured)
│   └── dockerPublishTimeServer - Publish timeServer image (if configured)
└── dockerImageMyApp (new) - Run all operations for myApp
    ├── dockerBuildMyApp
    ├── dockerTagMyApp
    └── ...

Existing Operation Aggregates (unchanged):
├── dockerBuild - Build all configured images
├── dockerSave - Save all configured images  
├── dockerTag - Tag all configured images
└── dockerPublish - Publish all configured images
```

### Phase 3: Enhanced Save Verification

#### 3.1 Enhance DockerSavedImageVerifyTask
**File**: `plugin-integration-test/buildSrc/src/main/groovy/DockerSavedImageVerifyTask.groovy`

**Enhanced `verifySavedImages()` method**:
```groovy
@TaskAction
void verifySavedImages() {
    def filePaths = getFilePaths().get()
    
    if (filePaths.isEmpty()) {
        logger.info('No saved image file paths to verify')
        return
    }

    def missingFiles = []
    def verifiedFiles = []

    // Phase 1: File existence verification
    for (String filePath : filePaths) {
        Path path = Paths.get(filePath)
        
        if (!path.isAbsolute()) {
            path = layout.projectDirectory.asFile.toPath().resolve(path)
        }

        if (Files.exists(path) && Files.isRegularFile(path)) {
            long fileSize = Files.size(path)
            logger.info("✓ File exists: ${path} (${fileSize} bytes)")
            verifiedFiles.add(path.toString())
        } else {
            logger.error("✗ Missing file: ${path}")
            missingFiles.add(filePath)
        }
    }

    if (!missingFiles.isEmpty()) {
        throw new RuntimeException("Failed to verify ${missingFiles.size()} saved image file(s): ${missingFiles}")
    }

    // Phase 2: Docker load verification
    verifyDockerLoadCapability(verifiedFiles)
    
    logger.lifecycle("Successfully verified ${verifiedFiles.size()} saved Docker image file(s)")
}

private void verifyDockerLoadCapability(List<String> verifiedFiles) {
    def loadFailures = []
    
    for (String filePath : verifiedFiles) {
        try {
            def process = ['docker', 'load', '-i', filePath].execute()
            def exitCode = process.waitFor()
            def stdout = process.inputStream.text.trim()
            def stderr = process.errorStream.text.trim()
            
            if (exitCode == 0) {
                logger.info("✓ Docker load verified: ${filePath} → ${stdout}")
            } else {
                logger.error("✗ Docker load failed: ${filePath} → ${stderr}")
                loadFailures.add("${filePath}: ${stderr}")
            }
        } catch (Exception e) {
            logger.error("✗ Docker load exception: ${filePath} → ${e.message}")
            loadFailures.add("${filePath}: ${e.message}")
        }
    }
    
    if (!loadFailures.isEmpty()) {
        throw new RuntimeException("Docker load verification failed for ${loadFailures.size()} file(s):\n${loadFailures.join('\n')}")
    }
    
    logger.lifecycle("Docker load verification passed for ${verifiedFiles.size()} image file(s)")
}
```

#### 3.2 Update Integration Test Usage
**File**: `plugin-integration-test/buildSrc/src/main/groovy/docker-image-testing.gradle`

**Update task description**:
```groovy
project.tasks.register('verifySavedDockerImages', DockerSavedImageVerifyTask) {
    it.filePaths.set(filePaths)
    it.group = 'verification'
    it.description = 'Verify Docker image files exist and can be loaded by Docker'
}
```

#### 3.3 Docker Load Verification Behavior
**Based on testing**:
- **Existing Images**: Docker load succeeds (return code 0) and shows "Loaded image: image:tag"
- **Corrupted Files**: Docker load fails (return code 1) with error like "unexpected EOF"
- **Invalid Archives**: Docker load fails (return code 1) with JSON error messages
- **No Image Cleanup Needed**: Docker handles existing images gracefully

### Phase 4: Integration Test Updates

#### 4.1 Update Integration Test Dependencies
**File**: `plugin-integration-test/docker/scenario-2/build.gradle`

**Change integration test task** (lines 81-91):
```groovy
// Current:
dependsOn 'dockerBuild'

// Updated:  
dependsOn 'dockerImages'  // or specifically 'dockerImageTimeServer'
```

**Rationale**: Use new aggregate task that includes all configured operations including save.

#### 4.2 Enhanced Save Verification Tests
**File**: `plugin-integration-test/buildSrc/src/test/groovy/DockerSavedImageVerifyTaskTest.groovy`

**New Tests Required**:
- `"verifySavedImages succeeds with valid Docker image files"`
- `"verifySavedImages fails with corrupted Docker image files"`
- `"verifySavedImages fails when Docker daemon unavailable"`
- `"verifySavedImages handles compressed image files (.tar.gz, .tar.bz2)"`

### Phase 5: Documentation Updates

#### 5.1 Complete Task Reference Documentation
**File**: `docs/design-docs/usage.md`

**Add Complete Task Architecture Section**:

```markdown
# Available Gradle Tasks

## Complete Operation Aggregate Tasks
These tasks run ALL configured operations for images:

- `dockerImages` - Run all configured operations for all images
- `dockerImageTimeServer` - Run all configured operations for timeServer image
- `dockerImageMyApp` - Run all configured operations for myApp image

## Operation-Specific Aggregate Tasks
These tasks run specific operations across all images:

- `dockerBuild` - Build all configured images
- `dockerSave` - Save all configured images to files
- `dockerTag` - Tag all configured images  
- `dockerPublish` - Publish all configured images to registries

## Per-Image Operation Tasks
These tasks run specific operations for specific images:

- `dockerBuildTimeServer` - Build timeServer image
- `dockerSaveTimeServer` - Save timeServer image to file
- `dockerTagTimeServer` - Tag timeServer image
- `dockerPublishTimeServer` - Publish timeServer image to registries

## Task Execution Examples

```bash
# Run ALL operations for ALL images (build + tag + save + publish)
./gradlew dockerImages

# Run ALL operations for specific image
./gradlew dockerImageTimeServer

# Run specific operation for ALL images
./gradlew dockerSave
./gradlew dockerBuild

# Run specific operation for specific image
./gradlew dockerSaveTimeServer
./gradlew dockerBuildTimeServer
```

## Integration Test Best Practices

For integration tests, use complete operation tasks:

```groovy
tasks.register('integrationTest') {
    dependsOn 'dockerImages'  // Runs all configured operations
    dependsOn 'verifyDockerImages'
    // ...
}
```

This ensures all configured operations (build, tag, save, publish) are executed before verification.
```

#### 5.2 Task Dependency Documentation
**Add section explaining task relationships**:

```markdown
## Task Dependencies

### Automatic Dependencies
The plugin automatically configures these dependencies:

- `dockerSaveTimeServer` depends on `dockerBuildTimeServer` (when image has build context)
- `dockerPublishTimeServer` depends on `dockerBuildTimeServer` (when image has build context)
- `dockerImageTimeServer` depends on all configured operations for that image
- `dockerImages` depends on all `dockerImage*` tasks

### Build Context Types
Both traditional `context` and `contextTask` scenarios get proper dependencies:

```groovy
// Traditional context
docker {
    images {
        myApp {
            context = file('src/main/docker')
            save { /* save config */ }  // ← dockerSaveMyApp depends on dockerBuildMyApp
        }
    }
}

// Copy task context  
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }
            save { /* save config */ }  // ← dockerSaveMyApp depends on dockerBuildMyApp
        }
    }
}
```
```

### Phase 6: Testing and Verification

#### 6.1 Unit Test Coverage
**Target**: 100% coverage for new/modified code
- Context dependency logic (both context and contextTask scenarios)
- New aggregate task creation
- Task dependency configuration

#### 6.2 Functional Test Coverage
**Target**: End-to-end scenarios
- contextTask save operations
- New aggregate task execution
- Task dependency verification

#### 6.3 Integration Test Verification
**File**: `plugin-integration-test/docker/scenario-2`
- Run `./gradlew clean integrationTest`
- Verify `build/docker-images/scenario2-time-server-latest.tar` is created
- Verify file can be loaded by Docker (`docker load` succeeds)

#### 6.4 Plugin Test Suite
**From plugin directory**:
```bash
./gradlew clean test          # Unit tests
./gradlew clean functionalTest  # Functional tests  
./gradlew clean build         # Complete build
```

## Expected Outcomes

### Success Criteria
1. **✅ Image Output**: Save creates `build/docker-images/scenario2-time-server-latest.tar`
2. **✅ Docker Load**: Saved image can be loaded with `docker load -i file.tar`
3. **✅ Unit Tests**: All plugin unit tests pass (100% coverage for new code)
4. **✅ Functional Tests**: All plugin functional tests pass
5. **✅ Integration Test**: scenario-2 integration test passes with save verification

### Architecture Benefits
1. **Better User Experience**: Single `dockerImages` command runs all operations
2. **Granular Control**: Existing per-operation and per-image tasks remain available
3. **Robust Verification**: Saved images validated for both existence and Docker compatibility
4. **Consistent Dependencies**: Both `context` and `contextTask` scenarios work identically

### Backward Compatibility
- **Existing Tasks**: All current tasks remain unchanged
- **Existing DSL**: No breaking changes to configuration syntax
- **New Features Only**: Only additions, no modifications to existing behavior

## Implementation Notes

### Docker Load Verification Details
- **Return Code 0**: Successful load (even for existing images)
- **Return Code 1**: Failed load (corrupted, invalid, or missing files)
- **Auto-Compression**: Docker automatically handles .tar, .tar.gz, .tar.bz2, .tar.xz
- **No Cleanup Needed**: Docker gracefully handles loading existing images

### Error Handling
- **Clear Messages**: Specific error messages for each failure type
- **File vs Docker Errors**: Separate validation phases with distinct error reporting
- **Exception Types**: RuntimeException with descriptive messages for verification failures

### Performance Considerations
- **Conditional Dependencies**: Only configured operations included in aggregate tasks
- **Parallel Execution**: Gradle's built-in task parallelization applies
- **Docker Verification**: Only runs when `verifySavedDockerImages` task is explicitly called

This comprehensive plan addresses the root cause bug, enhances plugin architecture with better task organization, and provides robust verification of saved Docker images while maintaining full backward compatibility.