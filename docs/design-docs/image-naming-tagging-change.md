# Docker Image Naming and Tagging API Changes

## Overview

This document outlines the API changes needed to support Docker's full image reference format for naming and tagging Docker images built by the Gradle plugin.

## Current Limitations

### Current Image Naming System:
- Image names derived from Gradle task names via camelCase → kebab-case conversion
- Example: `timeServer` task → `time-server` image name
- Cannot include registry/namespace information

### Current Tagging System:
- Simple tag names like `'latest'`, `'1.0.0'`
- Combined with derived image name: `"${dockerImageName}:${tag}"`
- Results in: `time-server:latest`, `time-server:1.0.0`
- No support for full image references with host/port/namespace

## Required Changes

### API Requirements:
1. Make `tags` parameter required when building images
2. Support full Docker image references per https://docs.docker.com/reference/cli/docker/image/tag/
3. Remove automatic image name derivation from task names
4. Support examples like: `['timeServer:1.0.0', 'example.com:5000/team/timeServer:1.0.0', 'example.com:5000/team/timeServer:latest']`
5. Maintain existing per-image Gradle task generation

## Implementation Plan

### Core Implementation Changes

#### A. ImageSpec Validation (DockerExtension.groovy):
- Make `tags` required for build contexts
- Remove simple tag validation, add full image reference validation
- Validate consistent image names across all tags
- Remove backward compatibility code

```groovy
boolean isValidImageReference(String imageRef) {
    // Support full format: [registry[:port]/]namespace/name:tag
    return imageRef.matches(/^([a-zA-Z0-9.-]+(:[\d]+)?\/)?[a-zA-Z0-9._/-]+:[a-zA-Z0-9._-]+$/)
}

void validateImageSpec(ImageSpec imageSpec) {
    // Make tags required for build contexts
    if (hasContext && (!imageSpec.tags.present || imageSpec.tags.get().isEmpty())) {
        throw new GradleException("Image '${imageSpec.name}' must specify at least one tag when building")
    }

    // Validate consistent image names across all tags
    if (imageSpec.tags.present && !imageSpec.tags.get().isEmpty()) {
        def firstImageName = extractImageName(imageSpec.tags.get().first())
        imageSpec.tags.get().each { tag ->
            def imageName = extractImageName(tag)
            if (imageName != firstImageName) {
                throw new GradleException("All tags must reference the same image name. Found: ${imageName} vs ${firstImageName}")
            }
        }
    }
}
```

#### B. Plugin Task Configuration (GradleDockerPlugin.groovy):
- **KEEP**: Dynamic task generation logic (`dockerBuild${capitalizedName}`, `dockerSave${capitalizedName}`, etc.)
- **KEEP**: Task registration based on DSL block names (`timeServer` → `dockerBuildTimeServer`)
- **REMOVE**: Automatic image name derivation from task names (`dockerImageName = imageSpec.name.replaceAll(...)`)
- **CHANGE**: Use `task.tags.set(imageSpec.tags)` directly instead of combining derived name with simple tags

#### C. Task Updates:
- **DockerBuildTask**: No changes needed - already uses tags as-is
- **DockerTagTask**: No major changes - already handles full image references
- **DockerSaveTask**: Use `tags.get().first()` as the image reference for `docker save` command
- **DockerPublishTask**: No changes needed - already handles publishing correctly

#### D. Remove Unused Code:
- Remove `isValidTagName()` method (simple tag validation)
- Remove `isValidDockerTag()` deprecated method
- Remove automatic image naming logic (the `replaceAll` camelCase → kebab-case conversion)
- **KEEP**: All task generation and naming logic based on DSL block names

### Preserved Functionality

The per-image task generation remains unchanged:

```groovy
docker {
    images {
        timeServer {  // Creates: dockerBuildTimeServer, dockerSaveTimeServer, etc.
            tags.set(['timeServer:1.0.0', 'registry.com/timeServer:latest'])
        }
        echoServer {  // Creates: dockerBuildEchoServer, dockerSaveEchoServer, etc.
            tags.set(['echoServer:2.0.0'])
        }
    }
}
```

**Available Gradle tasks remain:**
- `./gradlew dockerBuildTimeServer` (builds only timeServer block)
- `./gradlew dockerBuildEchoServer` (builds only echoServer block)
- `./gradlew dockerBuild` (builds all image blocks)
- Same pattern for save, publish, etc.

### API Migration

Since this project is in early development with no users, no backward compatibility is required.

```groovy
// Old API (to be removed)
docker {
    images {
        timeServer {  // Image name derived from this
            tags.set(['latest', '1.0.0'])  // Simple tags combined with derived name
        }
    }
}

// New API (only supported format)
docker {
    images {
        timeServer {  // Just a configuration block name, not used for image naming
            tags.set([
                'timeServer:1.0.0',
                'example.com:5000/team/timeServer:1.0.0',
                'example.com:5000/team/timeServer:latest'
            ])  // Full image references required
        }
    }
}
```

## Configuration Cache Compatibility

All changes maintain Provider/Property patterns:
- `imageSpec.tags` remains `ListProperty<String>`
- Validation occurs during configuration phase
- No `project` access during execution

## Unit Test Coverage Requirements

### Test Coverage Plan:

#### A. DockerExtensionTest.groovy:
- Test required tags validation for build contexts
- Test optional tags for sourceRef scenarios
- Test full image reference format validation
- Test consistent image name validation across multiple tags
- Test registry/namespace/port parsing

#### B. Task Tests:
- **DockerBuildTaskTest**: Test with full image references
- **DockerTagTaskTest**: Test registry-aware tagging
- **DockerSaveTaskTest**: Test using first tag as image reference
- **DockerPublishTaskTest**: Test publishing with full image references

#### C. Integration Scenarios:
- Mixed simple and full image references
- Multi-registry scenarios
- Per-image task generation with new API

## Implementation Order

1. **Update validation logic** in DockerExtension
2. **Add image reference parsing utilities**
3. **Update task configuration** in GradleDockerPlugin
4. **Update task implementations** (Build, Tag, Save, Publish)
5. **Write comprehensive unit tests** for 100% coverage
6. **Update integration tests** to use new API
7. **Update documentation and examples**

## Benefits

- Full compatibility with Docker's official image reference format
- Support for multi-registry workflows
- Cleaner API without automatic naming magic
- Maintains Gradle 9 configuration cache compatibility
- Preserves useful per-image task generation