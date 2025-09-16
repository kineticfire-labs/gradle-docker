# Docker Publish Configuration API Changes

## Overview

This document outlines the API changes needed to make the Docker publish configuration consistent with the image tags API by using full image references instead of the `repository` + `tags` combination.

## Current API Inconsistency

### Current Publish API:
```groovy
docker {
    images {
        myapp {
            tags = ['myapp:1.0.0', 'myapp:latest']  // Full image references (after recent changes)
            publish {
                to('staging') {
                    repository = 'registry.company.com:8080/team/app'
                    tags = ['v1.0', 'latest']  // Simple tags combined with repository
                }
            }
        }
    }
}
```

### Problems:
- **API Inconsistency**: Image tags use full references, publish tags use simple tags + repository
- **Cognitive Overhead**: Two different formats to understand
- **Magic Combination**: Behind-the-scenes repository + tag concatenation
- **Less Flexible**: Cannot have different registries per tag within same publish target

## Required Changes

### New Unified API:
```groovy
docker {
    images {
        myapp {
            tags = ['myapp:1.0.0', 'myapp:latest']  // Full image references
            publish {
                to('staging') {
                    tags = [
                        'registry.company.com:8080/team/app:v1.0',
                        'registry.company.com:8080/team/app:latest'
                    ]  // Full image references - consistent format
                }
                to('docker-hub') {
                    tags = [
                        'myuser/app:v1.0',
                        'myuser/app:latest'
                    ]  // Different registry, same consistent format
                }
            }
        }
    }
}
```

### API Benefits:
1. **Consistency**: Both image tags and publish tags use identical full reference format
2. **Clarity**: Each tag is explicit about where it will be published
3. **Flexibility**: Different publish targets can have different registries/namespaces per tag
4. **Docker Compliance**: Matches Docker's native tag format exactly per https://docs.docker.com/reference/cli/docker/image/tag/
5. **No Magic**: No behind-the-scenes combination of repository + tag

## Implementation Plan

### 1. Model Layer Changes (`PublishTargetSpec`)

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/PublishTargetSpec.groovy`

**Changes:**
- Remove `repository` property entirely
- Keep `tags` property as `ListProperty<String>`
- Update validation to use `isValidImageReference()` for each tag
- Remove repository-specific validation logic
- Update constructor/initialization if needed

### 2. Extension Layer Changes (`DockerExtension`)

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerExtension.groovy`

**Changes in `validatePublishTarget()` method:**
- Remove repository validation logic
- Change tag validation from `isValidSimpleTag()` to `isValidImageReference()`
- Update error messages to reflect full image reference requirements
- Remove repository-related error messages

**Changes in `validatePublishConfiguration()` method:**
- Update to work with full image references instead of repository + tag combinations
- Maintain logic that ensures at least one target exists

### 3. Task Layer Changes (`DockerPublishTask`)

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`

**Changes:**
- Update task execution logic to use full image references directly
- Remove repository + tag combination logic in `doAction()` method
- Simplify Docker push commands to use tags directly
- Update logging/output messages

### 4. Plugin Configuration Changes (`GradleDockerPlugin`)

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`

**Changes:**
- Update publish task configuration to pass full image references
- Remove repository-related configuration passing
- Ensure task dependencies work with new format

## Unit Test Changes

### A. PublishTargetSpec Tests

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/PublishTargetSpecTest.groovy`

**New/Updated Tests:**
- Test creation with full image reference tags
- Test validation of valid full image references
- Test validation failure for invalid image references
- Test empty tags list behavior
- Remove all repository-related tests

### B. DockerExtension Validation Tests

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerExtensionTest.groovy`

**Updated Tests:**
- Update `validatePublishTarget` tests to use full image references
- Update error message expectations for invalid image references
- Remove repository validation tests
- Add tests for various registry formats in publish targets
- Update `validatePublishConfiguration` tests

**New Test Cases:**
```groovy
// Example test cases to add
def "validatePublishTarget accepts full image references"()
def "validatePublishTarget fails with invalid image reference format"()
def "validatePublishTarget handles multi-registry scenarios"()
def "validatePublishConfiguration works with full image reference tags"()
```

### C. DockerPublishTask Tests

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/DockerPublishTaskTest.groovy`

**Updated Tests:**
- Update task execution tests to work with full image references
- Remove repository + tag combination logic tests
- Update Docker service mock expectations
- Test direct tag publishing without repository combination

**New Test Cases:**
```groovy
// Example test cases to update/add
def "publishes images using full image references"()
def "handles multiple registries in publish targets"()
def "fails gracefully with invalid full image references"()
```

### D. GradleDockerPlugin Tests

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/GradleDockerPluginTest.groovy`

**Updated Tests:**
- Update publish task configuration tests
- Test plugin configuration with new publish API format
- Remove repository-related plugin tests

## Functional Test Updates

### A. DockerPublishValidationFunctionalTest

**File:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/DockerPublishValidationFunctionalTest.groovy`

**Updated Tests:**
- Update all publish configuration examples to use full image references
- Remove repository + tags combination examples
- Update error message expectations

### B. Additional Functional Tests
- Update any other functional tests that use publish configurations
- Ensure functional tests demonstrate the new API format

## Validation and Error Handling

### Enhanced Validation
- Reuse existing `isValidImageReference()` method for publish tags
- Ensure consistent error messages between image tags and publish tags
- Validate that all tags in a publish target are valid image references

### Error Message Updates
- Update error messages to refer to "image reference" instead of "repository" or "tag name"
- Provide helpful examples of correct full image reference format
- Maintain consistency with image tag validation messages

## Test Coverage Requirements

### Unit Test Coverage
- **100% line coverage** for all modified validation methods
- **100% branch coverage** for all new validation logic
- **Comprehensive error scenarios** for invalid image references
- **Multi-registry scenarios** testing

### Functional Test Coverage
- **End-to-end publish workflows** with full image references
- **Mixed registry scenarios** (Docker Hub, private registries, localhost)
- **Error handling and validation** in functional test scenarios
- **Configuration validation** with new API format

## Documentation Updates Required

### API Documentation
- Update DSL documentation to show new publish configuration
- Remove repository + tags examples
- Add full image reference examples
- Document API consistency achievement

### Design Documentation
- Update `docs/design-docs/image-naming-tagging-change.md`
- Document publish API consistency achievement
- Update examples throughout documentation

## Implementation Order

1. **Model Layer** - Update `PublishTargetSpec` (remove repository, update validation)
2. **Extension Layer** - Update `DockerExtension` validation methods
3. **Task Layer** - Update `DockerPublishTask` execution logic
4. **Plugin Layer** - Update `GradleDockerPlugin` task configuration
5. **Unit Tests** - Update/add all unit tests for modified components
6. **Functional Tests** - Update publish configuration scenarios
7. **Validation Testing** - Ensure 100% test coverage on unit and functional tests
8. **Documentation** - Update all relevant documentation

## Configuration Cache Compatibility

All changes maintain Provider/Property patterns:
- `publishTarget.tags` remains `ListProperty<String>`
- Validation occurs during configuration phase
- No `project` access during execution

## Breaking Changes and Migration

### Breaking Changes
Since this project is in early development with no users, no backward compatibility is required.

- **REMOVED**: `repository` property from publish targets
- **CHANGED**: `tags` in publish targets now must be full image references

### Migration Example
```groovy
// Old API (to be removed)
publish {
    to('staging') {
        repository = 'registry.example.com/team/myapp'
        tags = ['latest', '1.0.0']  // Simple tags combined with repository
    }
}

// New API (only supported format)
publish {
    to('staging') {
        tags = [
            'registry.example.com/team/myapp:latest',
            'registry.example.com/team/myapp:1.0.0'
        ]  // Full image references required
    }
}
```

## Expected Benefits

- **API Consistency**: Publish tags match image tags format exactly
- **Simplified Logic**: No repository + tag combination needed
- **Enhanced Clarity**: Each tag is explicit about its destination
- **Better Validation**: Consistent validation across all image references
- **Reduced Complexity**: Single format to understand and maintain
- **Enhanced Flexibility**: Different registries per tag within same publish target
- **Docker Native**: Matches Docker CLI expectations exactly

## Conclusion

This change achieves complete API consistency across the plugin while simplifying the overall design and making the publish configuration more intuitive and flexible. The unified approach to image references will make the plugin easier to learn, use, and maintain.