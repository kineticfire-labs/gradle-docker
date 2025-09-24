# Implementation Plan: pullIfMissing Architecture Improvements

**Doc meta**
- Owner: Development Team
- Status: Planned
- Version: 1.0.0
- Last updated: 2025-01-15
- Comment: Move pullIfMissing to image-level architecture with validation for better user experience and consistency across operations

## Overview

Move pullIfMissing to image-level architecture for better user experience and consistency across operations, including validation to prevent invalid configurations.

## Phase 1: Image-Level pullIfMissing Architecture

### Step 1.1: Update ImageSpec with pullIfMissing Support and Validation
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy`
**Changes**:
- Add `Property<Boolean> pullIfMissing` property
- Add `NamedDomainObjectContainer<AuthSpec> pullAuth` property for pull authentication
- Add sourceRef component assembly properties:
  - `Property<String> sourceRefRegistry`
  - `Property<String> sourceRefNamespace`
  - `Property<String> sourceRefImageName`
  - `Property<String> sourceRefTag`
- Add method `String getEffectiveSourceRef()` to assemble full reference from components
- Add validation method `validateSourceRefConfiguration()` to ensure either full sourceRef or component assembly
- Add validation method `validatePullIfMissingConfiguration()` to prevent pullIfMissing=true with build context:
```groovy
void validatePullIfMissingConfiguration() {
    if (pullIfMissing.getOrElse(false) && hasBuildContext()) {
        throw new GradleException(
            "Cannot set pullIfMissing=true when build context is configured for image '${name}'. " +
            "Either build the image (remove pullIfMissing) or reference an existing image (use sourceRef instead of build context)."
        )
    }
}

private boolean hasBuildContext() {
    return contextTask.isPresent() ||
           (contextPath.isPresent() && contextPath.get().asFile.exists())
}
```

### Step 1.2: Update SaveSpec to Remove pullIfMissing
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/SaveSpec.groovy`
**Changes**:
- Remove `Property<Boolean> pullIfMissing` property
- Remove `Property<AuthSpec> auth` property
- Update logic to delegate to parent ImageSpec for pull behavior

### Step 1.3: Add pullIfMissing Support to Tag and Publish Operations
**Files**:
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerTagTask.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`

**Logic**: Both tasks check image-level `pullIfMissing` and pull sourceRef if needed before operation:
```groovy
private void pullSourceRefIfNeeded() {
    imageSpec.validatePullIfMissingConfiguration()

    if (imageSpec.pullIfMissing.getOrElse(false) && imageSpec.sourceRef.isPresent()) {
        def sourceRef = imageSpec.getEffectiveSourceRef()
        def authConfig = imageSpec.pullAuth.isPresent() ?
            imageSpec.pullAuth.get().toAuthConfig() : null

        if (!dockerService.imageExists(sourceRef).get()) {
            dockerService.pullImage(sourceRef, authConfig).get()
        }
    }
}
```

### Step 1.4: Update DockerSaveTask for New Architecture
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerSaveTask.groovy`
**Changes**:
- Remove operation-level pullIfMissing logic
- Use image-level pullIfMissing from parent ImageSpec
- Call image-level validation: `imageSpec.validatePullIfMissingConfiguration()`
- Use image-level pullAuth instead of save-level auth

### Step 1.5: Update Service Layer for Consistent Pull Logic
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/DockerServiceImpl.groovy`
**Enhancement**: Ensure pullImage method handles component-assembled sourceRefs correctly

## Phase 2: DSL and Configuration Updates

### Step 2.1: Update DSL Structure
**Pattern**: Enable image-level configuration:
```groovy
docker {
    images {
        myApp {
            sourceRef.set("myapp")                    // Base image name
            sourceRefRegistry.set("ghcr.io")          // Component assembly
            sourceRefNamespace.set("company")         // Component assembly
            sourceRefTag.set("v1.0")                 // Component assembly
            pullIfMissing.set(true)                   // Image-level setting

            pullAuth {                                // Pull-specific auth
                username.set(providers.environmentVariable("REGISTRY_USER"))
                password.set(providers.environmentVariable("REGISTRY_TOKEN"))
            }

            save { /* inherits pullIfMissing behavior */ }
            tag { /* inherits pullIfMissing behavior */ }
            publish {
                to('prod') { /* inherits pullIfMissing behavior */ }
            }
        }
    }
}
```

### Step 2.2: Add SourceRef Builder Methods
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy`
**Methods**:
```groovy
void sourceRef(String registry, String namespace, String imageName, String tag) {
    sourceRefRegistry.set(registry)
    sourceRefNamespace.set(namespace)
    sourceRefImageName.set(imageName)
    sourceRefTag.set(tag)
}

void sourceRef(@DelegatesTo(SourceRefSpec) Closure closure) {
    // DSL block configuration
}
```

## Phase 3: Testing Updates

### Step 3.1: Update Unit Tests
**Files to Update**:
- `DockerSaveTaskTest.groovy` - Remove save-level pullIfMissing tests
- `DockerTagTaskTest.groovy` - Add pullIfMissing support tests
- `DockerPublishTaskTest.groovy` - Add pullIfMissing support tests
- `ImageSpecTest.groovy` - Add image-level pullIfMissing tests
- `SaveSpecTest.groovy` - Remove pullIfMissing tests

**Test Categories**:
- Image-level pullIfMissing configuration
- SourceRef component assembly
- Pull authentication configuration
- Validation of conflicting configurations (pullIfMissing=true with build context)
- Cross-operation pullIfMissing behavior consistency

**Key Validation Tests**:
- `pullIfMissing with build context throws exception`
- `pullIfMissing with sourceRef succeeds`
- `pullIfMissing false with build context succeeds`

### Step 3.2: Update Functional Tests
**Files to Update**:
- All functional test files that use pullIfMissing
- Add tests for new DSL structure
- Add tests for component-assembled sourceRef

**Test Scenarios**:
- pullIfMissing works across save, tag, and publish operations
- Component assembly creates correct full image references
- Pull authentication works with new architecture
- Validation catches invalid configurations

## Phase 4: Documentation Updates

### Step 4.1: Update Usage Documentation
**File**: `docs/design-docs/usage-docker.md`

**Add Sections**:

**A) Image-Level pullIfMissing:**
```groovy
docker {
    images {
        myApp {
            sourceRef.set("ghcr.io/company/baseimage:v2.0")
            pullIfMissing.set(true)  // Applies to all operations

            pullAuth {
                username.set(providers.environmentVariable("GHCR_USER"))
                password.set(providers.environmentVariable("GHCR_TOKEN"))
            }

            save { /* will pull if missing */ }
            tag { /* will pull if missing */ }
            publish {
                to('prod') { /* will pull if missing */ }
            }
        }
    }
}
```

**B) SourceRef Component Assembly:**
```groovy
docker {
    images {
        myApp {
            // Assemble sourceRef from components
            sourceRefRegistry.set("docker.io")
            sourceRefNamespace.set("library")
            sourceRefImageName.set("alpine")
            sourceRefTag.set("3.18")
            // Results in: docker.io/library/alpine:3.18

            pullIfMissing.set(true)
        }
    }
}
```

**C) Mixed Usage Patterns:**
```groovy
docker {
    images {
        // Full sourceRef
        prodApp {
            sourceRef.set("ghcr.io/company/app:v1.0")
            pullIfMissing.set(true)
        }

        // Component assembly
        devApp {
            sourceRefRegistry.set("localhost:5000")
            sourceRefImageName.set("app")
            sourceRefTag.set("latest")
            pullIfMissing.set(true)
        }
    }
}
```

### Step 4.2: Add Migration Notes
**Section**: Document the architectural change and new capabilities:
- pullIfMissing now works across all operations (save, tag, publish)
- SourceRef supports both full references and component assembly
- Pull authentication is separate from publish authentication

## Success Criteria

1. **Validation**: pullIfMissing=true with build context throws clear error
2. **Architecture**: pullIfMissing works consistently across save, tag, and publish
3. **Flexibility**: SourceRef supports both full references and component assembly
4. **Authentication**: Pull-specific auth configuration works correctly
5. **Testing**: All unit and functional tests pass
6. **Documentation**: Usage examples reflect new architecture and capabilities

## Benefits

1. **Consistency**: Same pull behavior across all operations
2. **Flexibility**: Component assembly for complex registry configurations
3. **Clarity**: Separate pull auth from publish auth
4. **User Safety**: Clear validation prevents invalid configurations
5. **Maintainability**: Centralized pull logic reduces code duplication