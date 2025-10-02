# Implementation Plan: Fix Integration Test Publish Tag Mismatch

## Executive Summary

This document provides a comprehensive implementation plan to fix the fundamental design flaw in the Docker plugin's publish block inheritance system. The issue extends beyond the initial scenario-3 integration test failure to encompass a complete lack of property inheritance from source images to publish targets.

## Problem Analysis

### Root Cause: Incomplete Inheritance Design

The current publish block implementation has **partial inheritance** that's confusing and broken:

1. **imageName**: ✅ Inherits from source (line 298 in DockerPublishTask.groovy)
2. **namespace**: ❌ No inheritance (defaults to empty string)
3. **repository**: ❌ No inheritance (defaults to empty string)
4. **registry**: ✅ Override expected (no inheritance needed)
5. **publishTags**: ✅ Override expected (no inheritance needed)

### Critical Gaps Identified

#### Gap 1: Missing SourceRef Component Support
- `DockerPublishTask.buildSourceImageReference()` only handles direct `sourceRef` property
- **Does NOT use** `imageSpec.getEffectiveSourceRef()` which handles component assembly
- SourceRef components (sourceRefRegistry, sourceRefNamespace, etc.) not passed to task

#### Gap 2: Inconsistent Property Inheritance
- Only `imageName` has fallback inheritance logic
- `namespace` and `repository` default to empty strings when not specified in target
- Creates malformed image references

#### Gap 3: No Empty Publish Target Support
- Users expect minimal configuration for simple republishing
- Current implementation requires explicit property repetition

## Expected User Experience

### Scenario 1: Empty Publish Target (Build Mode)
```groovy
docker {
    images {
        myApp {
            // BUILD: Creates complete image reference
            registry.set("localhost:5000")
            namespace.set("mycompany")
            imageName.set("myapp")
            tags.set(["latest"])

            publish {
                to('testRegistry') {
                    // INHERITS EVERYTHING! Should work with empty block
                }
            }
        }
    }
}
// Expected Result: Publishes localhost:5000/mycompany/myapp:latest to same location
```

### Scenario 2: SourceRef Component Inheritance
```groovy
docker {
    images {
        existingApp {
            // SOURCE: Use components to define source image
            sourceRefRegistry.set("docker.io")
            sourceRefNamespace.set("library")
            sourceRefImageName.set("nginx")
            sourceRefTag.set("1.21")
            // Effective: docker.io/library/nginx:1.21

            publish {
                to('prod') {
                    registry.set("prod.company.com")  // Override registry
                    // Should inherit: namespace=library, imageName=nginx
                    publishTags(["deployed"])
                    // Expected: prod.company.com/library/nginx:deployed
                }

                to('backup') {
                    // Should inherit ALL properties!
                    // Expected: docker.io/library/nginx:latest (default tag)
                }
            }
        }
    }
}
```

## Implementation Strategy

### Core Principle: Source Properties → Effective Properties → Target Overrides

```
Source Image Properties (Build/SourceRef)
    ↓
Effective Source Properties (Resolved)
    ↓
Target Overrides (Publish Block)
    ↓
Final Target Image References
```

## Implementation Plan

### Phase 1: Fix Source Image Reference Resolution (High Priority)

#### 1.1 Update DockerPublishTask.buildSourceImageReference()

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`

**Current Issue**: Only handles direct `sourceRef`, ignores component assembly

**Fix**:
```groovy
String buildSourceImageReference() {
    // Use ImageSpec for comprehensive source resolution
    def imageSpecValue = imageSpec.orNull
    if (imageSpecValue) {
        def effectiveSourceRef = imageSpecValue.getEffectiveSourceRef()
        if (effectiveSourceRef && !effectiveSourceRef.isEmpty()) {
            return effectiveSourceRef
        }
    }

    // Fallback to direct task properties (existing logic)
    // ... existing build mode logic
}
```

**Impact**: Fixes sourceRef component mode support immediately

#### 1.2 Pass SourceRef Components to Task

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`

**Current Issue**: Only passes direct `sourceRef` property to task

**Fix**: Add missing sourceRef component properties in `configureDockerPublishTask()`:
```groovy
// Add after line 498
task.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
task.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
task.sourceRefImageName.set(imageSpec.sourceRefImageName)
task.sourceRefRepository.set(imageSpec.sourceRefRepository)
task.sourceRefTag.set(imageSpec.sourceRefTag)
```

**Impact**: Enables task to access all sourceRef data

#### 1.3 Add SourceRef Properties to DockerPublishTask

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`

**Add Properties**:
```groovy
@Input
@Optional
abstract Property<String> getSourceRefRegistry()

@Input
@Optional
abstract Property<String> getSourceRefNamespace()

@Input
@Optional
abstract Property<String> getSourceRefImageName()

@Input
@Optional
abstract Property<String> getSourceRefRepository()

@Input
@Optional
abstract Property<String> getSourceRefTag()
```

**Update Constructor**:
```groovy
// Add conventions
sourceRefRegistry.convention("")
sourceRefNamespace.convention("")
sourceRefImageName.convention("")
sourceRefRepository.convention("")
sourceRefTag.convention("")
```

### Phase 2: Implement Complete Property Inheritance (High Priority)

#### 2.1 Create EffectiveImageProperties Helper Class

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/model/EffectiveImageProperties.groovy`

```groovy
/**
 * Represents resolved image properties for inheritance calculations
 */
class EffectiveImageProperties {
    final String registry
    final String namespace
    final String imageName
    final String repository
    final List<String> tags

    EffectiveImageProperties(String registry, String namespace, String imageName,
                           String repository, List<String> tags) {
        this.registry = registry ?: ""
        this.namespace = namespace ?: ""
        this.imageName = imageName ?: ""
        this.repository = repository ?: ""
        this.tags = tags ?: []
    }

    /**
     * Factory method to resolve properties from ImageSpec
     */
    static EffectiveImageProperties fromImageSpec(ImageSpec imageSpec) {
        // Step 1: Check for direct sourceRef
        if (imageSpec.sourceRef.isPresent() && !imageSpec.sourceRef.get().isEmpty()) {
            return parseFromDirectSourceRef(imageSpec.sourceRef.get())
        }

        // Step 2: Check for sourceRef components
        if (hasSourceRefComponents(imageSpec)) {
            return buildFromSourceRefComponents(imageSpec)
        }

        // Step 3: Use build mode properties
        return buildFromBuildMode(imageSpec)
    }

    /**
     * Factory method to resolve properties from task properties (fallback)
     */
    static EffectiveImageProperties fromTaskProperties(DockerPublishTask task) {
        // Handle direct sourceRef
        def sourceRefValue = task.sourceRef.getOrElse("")
        if (!sourceRefValue.isEmpty()) {
            return parseFromDirectSourceRef(sourceRefValue)
        }

        // Handle sourceRef components
        if (hasSourceRefComponents(task)) {
            return buildFromSourceRefComponents(task)
        }

        // Use build mode properties
        return new EffectiveImageProperties(
            task.registry.getOrElse(""),
            task.namespace.getOrElse(""),
            task.imageName.getOrElse(""),
            task.repository.getOrElse(""),
            task.tags.getOrElse([])
        )
    }

    /**
     * Apply target overrides with inheritance
     */
    EffectiveImageProperties applyTargetOverrides(PublishTarget target) {
        return new EffectiveImageProperties(
            target.registry.getOrElse("") ?: this.registry,
            target.namespace.getOrElse("") ?: this.namespace,
            target.imageName.getOrElse("") ?: this.imageName,
            target.repository.getOrElse("") ?: this.repository,
            target.publishTags.getOrElse([]) ?: this.tags
        )
    }

    private static EffectiveImageProperties parseFromDirectSourceRef(String sourceRef) {
        def parts = ImageRefParts.parse(sourceRef)
        return new EffectiveImageProperties(
            parts.registry,
            parts.namespace,
            parts.repository,
            "", // repository is in namespace for parsed refs
            [parts.tag]
        )
    }

    private static EffectiveImageProperties buildFromSourceRefComponents(imageSpec) {
        // Use ImageSpec.getEffectiveSourceRef() and parse result
        def effectiveRef = imageSpec.getEffectiveSourceRef()
        return parseFromDirectSourceRef(effectiveRef)
    }

    private static EffectiveImageProperties buildFromBuildMode(ImageSpec imageSpec) {
        return new EffectiveImageProperties(
            imageSpec.registry.getOrElse(""),
            imageSpec.namespace.getOrElse(""),
            imageSpec.imageName.getOrElse(""),
            imageSpec.repository.getOrElse(""),
            imageSpec.tags.getOrElse([])
        )
    }

    private static boolean hasSourceRefComponents(imageSpec) {
        return !imageSpec.sourceRefRegistry.getOrElse("").isEmpty() ||
               !imageSpec.sourceRefNamespace.getOrElse("").isEmpty() ||
               !imageSpec.sourceRefImageName.getOrElse("").isEmpty() ||
               !imageSpec.sourceRefRepository.getOrElse("").isEmpty() ||
               !imageSpec.sourceRefTag.getOrElse("").isEmpty()
    }

    private static boolean hasSourceRefComponents(DockerPublishTask task) {
        return !task.sourceRefRegistry.getOrElse("").isEmpty() ||
               !task.sourceRefNamespace.getOrElse("").isEmpty() ||
               !task.sourceRefImageName.getOrElse("").isEmpty() ||
               !task.sourceRefRepository.getOrElse("").isEmpty() ||
               !task.sourceRefTag.getOrElse("").isEmpty()
    }
}
```

#### 2.2 Update DockerPublishTask.buildTargetImageReferences()

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`

**Replace existing method**:
```groovy
List<String> buildTargetImageReferences(PublishTarget target) {
    // Step 1: Get effective source properties
    def sourceProps = getEffectiveSourceProperties()

    // Step 2: Apply target overrides with inheritance
    def effectiveProps = sourceProps.applyTargetOverrides(target)

    // Step 3: Validate we have required properties
    def effectiveTags = effectiveProps.tags
    if (effectiveTags.isEmpty()) {
        // Default to ["latest"] if no tags available
        effectiveTags = ["latest"]
    }

    // Step 4: Build target references with effective properties
    def targetRefs = []

    if (!effectiveProps.repository.isEmpty()) {
        // Using repository format
        def baseRef = effectiveProps.registry.isEmpty() ?
            effectiveProps.repository :
            "${effectiveProps.registry}/${effectiveProps.repository}"
        effectiveTags.each { tag ->
            targetRefs.add("${baseRef}:${tag}")
        }
    } else if (!effectiveProps.imageName.isEmpty()) {
        // Using namespace + imageName format
        def baseRef = ""
        if (!effectiveProps.registry.isEmpty()) {
            baseRef += "${effectiveProps.registry}/"
        }
        if (!effectiveProps.namespace.isEmpty()) {
            baseRef += "${effectiveProps.namespace}/"
        }
        baseRef += effectiveProps.imageName

        effectiveTags.each { tag ->
            targetRefs.add("${baseRef}:${tag}")
        }
    } else {
        // No valid image reference can be built
        logger.warn("Cannot build target image reference: missing imageName and repository")
        return []
    }

    return targetRefs
}

private EffectiveImageProperties getEffectiveSourceProperties() {
    def imageSpecValue = imageSpec.orNull
    if (imageSpecValue) {
        return EffectiveImageProperties.fromImageSpec(imageSpecValue)
    } else {
        return EffectiveImageProperties.fromTaskProperties(this)
    }
}
```

### Phase 3: Add Empty Publish Target Support (Medium Priority)

#### 3.1 Update PublishTarget Validation

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/PublishTarget.groovy`

**Modify validateRegistry()** to allow empty targets:
```groovy
void validateRegistry() {
    def registryValue = registry.getOrElse("")
    def repositoryValue = repository.getOrElse("")
    def publishTagsValue = publishTags.getOrElse([])

    // Allow completely empty targets (will inherit everything)
    def hasAnyTargetProperty = !registryValue.isEmpty() ||
                              !namespace.getOrElse("").isEmpty() ||
                              !imageName.getOrElse("").isEmpty() ||
                              !repositoryValue.isEmpty() ||
                              !publishTagsValue.isEmpty()

    if (!hasAnyTargetProperty) {
        // Empty target is valid - will inherit all properties
        return
    }

    // For non-empty targets, existing validation logic applies
    // ... rest of existing validation
}
```

#### 3.2 Handle Empty Publish Tags

**In EffectiveImageProperties.applyTargetOverrides()**:
```groovy
// Special handling for empty publishTags - inherit source tags
def effectivePublishTags = target.publishTags.getOrElse([])
if (effectivePublishTags.isEmpty() && !this.tags.isEmpty()) {
    effectivePublishTags = this.tags
}

return new EffectiveImageProperties(
    target.registry.getOrElse("") ?: this.registry,
    target.namespace.getOrElse("") ?: this.namespace,
    target.imageName.getOrElse("") ?: this.imageName,
    target.repository.getOrElse("") ?: this.repository,
    effectivePublishTags
)
```

### Phase 4: Testing Strategy (Medium Priority)

#### 4.1 Unit Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/model/EffectiveImagePropertiesTest.groovy`

Test scenarios:
- Build mode property resolution
- Direct sourceRef parsing
- SourceRef component assembly
- Target override application
- Empty target inheritance
- Edge cases (missing properties, malformed refs)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/DockerPublishTaskTest.groovy`

Test scenarios:
- Property inheritance from ImageSpec
- Empty publish target handling
- Target reference building with inheritance
- All inheritance permutations

#### 4.2 Integration Tests

Create new integration test scenarios:

**File**: `plugin-integration-test/docker/scenario-4/build.gradle`
```groovy
// Test empty publish target inheritance (build mode)
docker {
    images {
        inheritanceTest {
            namespace.set('scenario4')
            imageName.set('inheritance-test')
            tags.set(['latest'])

            publish {
                to('emptyTarget') {}  // Should inherit everything
            }
        }
    }
}
```

**File**: `plugin-integration-test/docker/scenario-5/build.gradle`
```groovy
// Test sourceRef component inheritance
docker {
    images {
        sourceRefTest {
            sourceRefRegistry.set('docker.io')
            sourceRefNamespace.set('library')
            sourceRefImageName.set('nginx')
            sourceRefTag.set('1.21')

            publish {
                to('partialOverride') {
                    registry.set('localhost:5025')
                    publishTags(['test'])
                    // Should inherit namespace and imageName
                }
            }
        }
    }
}
```

#### 4.3 Validation Tests

Verify existing scenarios still work:
- scenario-1: Basic functionality
- scenario-2: Partial inheritance (should work better)
- scenario-3: Complete inheritance (should fix the bug)

### Phase 5: Documentation Updates (Low Priority)

#### 5.1 Update Usage Documentation

**File**: `docs/usage/usage-docker.md`

Add sections:
- "Property Inheritance in Publish Targets"
- "Empty Publish Target Configuration"
- "SourceRef Component Inheritance Examples"

#### 5.2 Add Design Documentation

**File**: `docs/design-docs/publish-inheritance-design.md`

Document:
- Inheritance precedence rules
- Property resolution algorithm
- Mode detection logic
- Edge case handling

## Success Criteria

### Immediate Success Criteria (Phase 1-2)
- [ ] scenario-3 integration test passes without publish errors
- [ ] sourceRef component modes work with publish targets
- [ ] All existing integration tests continue to pass
- [ ] Unit tests achieve 100% coverage for new inheritance logic

### Complete Success Criteria (All Phases)
- [ ] Empty publish targets work correctly
- [ ] All inheritance scenarios pass integration tests
- [ ] Documentation demonstrates simplified usage patterns
- [ ] No regression in existing functionality
- [ ] Performance impact is negligible

## Risk Assessment and Mitigation

### Risk 1: Breaking Changes
**Mitigation**: Implement as enhancement with backward compatibility
- Existing explicit configurations continue to work
- New inheritance only applies when properties are missing

### Risk 2: Complex Edge Cases
**Mitigation**: Comprehensive testing strategy
- Test all mode combinations (build/sourceRef × inheritance patterns)
- Property-based testing for input domain coverage
- Negative testing for malformed configurations

### Risk 3: Performance Impact
**Mitigation**: Lazy evaluation and caching
- Only resolve effective properties when needed
- Cache resolved properties within task execution
- Benchmark against existing implementation

## Implementation Timeline

- **Phase 1**: 2-3 days (Critical fixes)
- **Phase 2**: 3-4 days (Core inheritance)
- **Phase 3**: 1-2 days (Empty target support)
- **Phase 4**: 2-3 days (Testing)
- **Phase 5**: 1-2 days (Documentation)

**Total Estimated Effort**: 9-14 days

## Dependencies

1. Understanding of existing Docker nomenclature patterns
2. Access to integration test environment with registries
3. JaCoCo coverage reporting for validation
4. Review and approval process for design changes

This implementation plan provides a comprehensive solution to the publish block inheritance issues while maintaining backward compatibility and improving user experience significantly.