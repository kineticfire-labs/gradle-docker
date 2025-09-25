# Implementation Plan: pullIfMissing Architecture - Completion Phase (Part 2)

**Doc meta**
- Owner: Development Team
- Status: Ready for Implementation
- Version: 1.0.0
- Last updated: 2025-01-24
- Comment: Complete the pullIfMissing architecture implementation with bug fixes, documentation updates, validation improvements, and comprehensive test coverage

## Overview

Complete the pullIfMissing architecture implementation that was successfully moved to the image level. The core functionality is implemented and working, but requires critical bug fixes, documentation updates, validation chain completion, and comprehensive test coverage to be production-ready.

## Phase 1: Critical Bug Fixes

### Step 1.1: Fix AuthSpec Property Type in ImageSpec
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy`
**Issue**: Property type mismatch causing runtime errors
**Location**: Line 140
**Changes**:
```groovy
// Change FROM:
@Nested
@Optional
abstract Property<AuthSpec> getPullAuth()

// Change TO:
@Nested
@Optional
AuthSpec pullAuth
```

**Update DSL methods accordingly**:
```groovy
// Update pullAuth DSL methods to work with AuthSpec field instead of Property<AuthSpec>
void pullAuth(@DelegatesTo(AuthSpec) Closure closure) {
    if (!pullAuth) {
        pullAuth = objectFactory.newInstance(AuthSpec)
    }
    closure.delegate = pullAuth
    closure.call()
}

void pullAuth(Action<AuthSpec> action) {
    if (!pullAuth) {
        pullAuth = objectFactory.newInstance(AuthSpec)
    }
    action.execute(pullAuth)
}
```

**Update task implementations**:
- Update all tasks that access `imageSpecValue.pullAuth.isPresent()` and `imageSpecValue.pullAuth.get()`
- Change to `imageSpecValue.pullAuth != null` and `imageSpecValue.pullAuth`

## Phase 2: Complete Validation Chain

### Step 2.1: Add Missing Validation Calls in Tasks
**Files**:
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerSaveTask.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerTagTask.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`

**Enhancement**: Add `validateSourceRefConfiguration()` call in `pullSourceRefIfNeeded()` method:
```groovy
private void pullSourceRefIfNeeded() {
    def imageSpecValue = imageSpec.orNull
    if (!imageSpecValue) return

    // Add this line to complete validation chain
    imageSpecValue.validateSourceRefConfiguration()
    imageSpecValue.validatePullIfMissingConfiguration()

    if (imageSpecValue.pullIfMissing.getOrElse(false)) {
        // ... existing pull logic
    }
}
```

## Phase 3: Documentation Updates

### Step 3.1: Update Usage Documentation Examples
**File**: `docs/design-docs/usage-docker.md`

**A) Add Image-Level pullIfMissing=true Example**:
Add new section after line 196 (after existing sourceRef examples):

```markdown
### Scenario 4: Pull Missing Images Automatically

```groovy
docker {
    images {
        myApp {
            sourceRef.set("ghcr.io/company/baseimage:v2.0")
            pullIfMissing.set(true)  // Image-level setting - applies to all operations

            // Pull-specific authentication at image level
            pullAuth {
                username.set(providers.environmentVariable("GHCR_USER"))
                password.set(providers.environmentVariable("GHCR_TOKEN"))
            }

            // All operations inherit pullIfMissing behavior
            save {
                compression.set(SaveCompression.GZIP)
                outputFile.set(layout.buildDirectory.file("docker-images/baseimage.tar.gz"))
            }

            tag {
                tags.set(["local:latest", "local:stable"])
            }

            publish {
                to('prod') {
                    registry.set("docker.io")
                    repository.set("company/myapp")
                    publishTags.set(["published-latest"])
                }
            }
        }
    }
}
```
```

**B) Add SourceRef Component Assembly Examples**:
Add new section after the pullIfMissing example:

```markdown
### Scenario 5: SourceRef Component Assembly

Instead of specifying a full sourceRef string, you can assemble it from components:

```groovy
docker {
    images {
        // Pattern A: Individual component properties
        alpineApp {
            // Assemble sourceRef from components
            sourceRefRegistry.set("docker.io")      // Registry
            sourceRefNamespace.set("library")       // Namespace/organization
            sourceRefImageName.set("alpine")        // Image name (required)
            sourceRefTag.set("3.18")               // Tag (defaults to "latest" if omitted)
            // Results in: docker.io/library/alpine:3.18

            pullIfMissing.set(true)

            save {
                outputFile.set(file("build/alpine-base.tar"))
            }
        }

        // Pattern B: Helper method for component assembly
        ubuntuApp {
            sourceRef("docker.io", "library", "ubuntu", "22.04")
            // Results in: docker.io/library/ubuntu:22.04

            pullIfMissing.set(true)
        }

        // Pattern C: Mixed usage - some components, some full references
        customApp {
            sourceRefRegistry.set("my-registry.company.com:5000")
            sourceRefImageName.set("custom-base")
            // sourceRefTag defaults to "latest"
            // Results in: my-registry.company.com:5000/custom-base:latest

            pullIfMissing.set(true)

            pullAuth {
                username.set(providers.environmentVariable("COMPANY_REGISTRY_USER"))
                password.set(providers.environmentVariable("COMPANY_REGISTRY_TOKEN"))
            }
        }
    }
}
```
```

**C) Update Key API Properties Section**:
Add after line 382 (after existing properties):

```markdown
### pullIfMissing and SourceRef Properties (Image-Level)
- `pullIfMissing.set(Boolean)` - Whether to pull source image if missing locally (defaults to false)
- `sourceRef.set(String)` - Full source image reference (e.g., "ghcr.io/company/app:v1.0")
- `sourceRefRegistry.set(String)` - Source registry component (e.g., "docker.io")
- `sourceRefNamespace.set(String)` - Source namespace component (e.g., "library")
- `sourceRefImageName.set(String)` - Source image name component (e.g., "alpine")
- `sourceRefTag.set(String)` - Source tag component (defaults to "latest" if omitted)

### pullAuth Configuration (Image-Level)
```groovy
pullAuth {
    username.set(providers.environmentVariable("REGISTRY_USER"))
    password.set(providers.environmentVariable("REGISTRY_TOKEN"))
}
```

**Note**: pullAuth is separate from publish auth - pullAuth is used for pulling source images, while publish auth is used for pushing to target registries.
```

### Step 3.2: Add Usage Notes Section
Add new section before "Key Benefits":

```markdown
### Usage Notes and Patterns

#### pullIfMissing Behavior
- **Default**: `pullIfMissing.set(false)` - operations fail if source image is missing locally
- **When true**: automatically pulls source image if not found locally before performing operation
- **Applies to**: save, tag, and publish operations when using sourceRef mode
- **Authentication**: use `pullAuth` block for pull operations, separate from publish authentication

#### SourceRef vs Build Context
- **Cannot combine**: pullIfMissing=true with build context (contextTask, dockerfile, buildArgs) will throw validation error
- **Either/or**: use pullIfMissing for existing images OR build context for new images, not both

#### Component Assembly Priority
1. Full `sourceRef.set("registry/namespace/image:tag")` takes precedence
2. Component assembly used if sourceRef is empty or not set
3. Required: either sourceRef OR sourceRefImageName must be specified when using pullIfMissing=true
```

## Phase 4: Add Missing DSL Enhancement

### Step 4.1: Add Closure-Based SourceRef Configuration
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy`
**Location**: After existing sourceRef method (around line 280)

**Create SourceRefSpec class**:
```groovy
// Add new inner class or separate file
class SourceRefSpec {
    private final ImageSpec parent

    SourceRefSpec(ImageSpec parent) {
        this.parent = parent
    }

    void registry(String registry) {
        parent.sourceRefRegistry.set(registry)
    }

    void namespace(String namespace) {
        parent.sourceRefNamespace.set(namespace)
    }

    void imageName(String imageName) {
        parent.sourceRefImageName.set(imageName)
    }

    void tag(String tag) {
        parent.sourceRefTag.set(tag)
    }
}
```

**Add DSL method to ImageSpec**:
```groovy
void sourceRef(@DelegatesTo(SourceRefSpec) Closure closure) {
    def sourceRefSpec = new SourceRefSpec(this)
    closure.delegate = sourceRefSpec
    closure.call()
}
```

**Usage**:
```groovy
docker {
    images {
        myApp {
            sourceRef {
                registry "docker.io"
                namespace "library"
                imageName "alpine"
                tag "3.18"
            }
            pullIfMissing true
        }
    }
}
```

## Phase 5: Comprehensive Test Coverage

### Step 5.1: Unit Test Updates
**Files to Update**:
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/ImageSpecTest.groovy`
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/DockerSaveTaskTest.groovy`
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/DockerTagTaskTest.groovy`
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/DockerPublishTaskTest.groovy`

**New Test Categories**:

**A) ImageSpec Component Assembly Tests**:
```groovy
@Test
void "getEffectiveSourceRef assembles from components correctly"() {
    imageSpec.sourceRefRegistry.set("docker.io")
    imageSpec.sourceRefNamespace.set("library")
    imageSpec.sourceRefImageName.set("alpine")
    imageSpec.sourceRefTag.set("3.18")

    assert imageSpec.getEffectiveSourceRef() == "docker.io/library/alpine:3.18"
}

@Test
void "getEffectiveSourceRef defaults tag to latest when empty"() {
    imageSpec.sourceRefImageName.set("alpine")

    assert imageSpec.getEffectiveSourceRef() == "alpine:latest"
}

@Test
void "getEffectiveSourceRef prefers direct sourceRef over components"() {
    imageSpec.sourceRef.set("direct:reference")
    imageSpec.sourceRefImageName.set("component")

    assert imageSpec.getEffectiveSourceRef() == "direct:reference"
}
```

**B) Validation Tests**:
```groovy
@Test
void "validatePullIfMissingConfiguration throws when pullIfMissing true with build context"() {
    imageSpec.pullIfMissing.set(true)
    imageSpec.context.set(layout.projectDirectory.dir("src/docker"))

    assertThrows(GradleException) {
        imageSpec.validatePullIfMissingConfiguration()
    }
}

@Test
void "validateSourceRefConfiguration throws when pullIfMissing true but no sourceRef"() {
    imageSpec.pullIfMissing.set(true)

    assertThrows(GradleException) {
        imageSpec.validateSourceRefConfiguration()
    }
}
```

**C) pullAuth Configuration Tests**:
```groovy
@Test
void "pullAuth DSL creates AuthSpec correctly"() {
    imageSpec.pullAuth {
        username.set("testuser")
        password.set("testpass")
    }

    assert imageSpec.pullAuth != null
    assert imageSpec.pullAuth.username.get() == "testuser"
    assert imageSpec.pullAuth.password.get() == "testpass"
}
```

**D) Task pullSourceRefIfNeeded Tests**:
```groovy
@Test
void "pullSourceRefIfNeeded calls both validation methods"() {
    def imageSpec = Mock(ImageSpec)
    task.imageSpec.set(imageSpec)
    imageSpec.pullIfMissing.getOrElse(false) >> false

    task.pullSourceRefIfNeeded()

    verify(imageSpec).validateSourceRefConfiguration()
    verify(imageSpec).validatePullIfMissingConfiguration()
}

@Test
void "pullSourceRefIfNeeded pulls image when missing and pullIfMissing true"() {
    def mockService = Mock(DockerService)
    def mockImageSpec = Mock(ImageSpec)
    def mockAuth = Mock(AuthSpec)
    def mockAuthConfig = Mock(AuthConfig)

    task.dockerService.set(mockService)
    task.imageSpec.set(mockImageSpec)

    mockImageSpec.pullIfMissing.getOrElse(false) >> true
    mockImageSpec.getEffectiveSourceRef() >> "test:image"
    mockImageSpec.pullAuth >> mockAuth
    mockAuth.toAuthConfig() >> mockAuthConfig
    mockService.imageExists("test:image") >> CompletableFuture.completedFuture(false)
    mockService.pullImage("test:image", mockAuthConfig) >> CompletableFuture.completedFuture(null)

    task.pullSourceRefIfNeeded()

    verify(mockService).pullImage("test:image", mockAuthConfig)
}
```

### Step 5.2: Functional Test Updates
**Files to Create/Update**:
- `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/PullIfMissingFunctionalTest.groovy`
- `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/SourceRefComponentAssemblyFunctionalTest.groovy`

**Test Scenarios**:
- End-to-end pullIfMissing behavior across save, tag, publish operations
- Component assembly creates correct image references
- pullAuth authentication works with registries
- Validation prevents invalid configurations
- Mixed usage patterns (some images with pullIfMissing, some without)

### Step 5.3: Integration Test Updates
**Files to Update**:
- Add integration tests in `plugin-integration-test/` that verify pullIfMissing works with real Docker daemon
- Test component assembly with actual registry operations
- Verify pullAuth works with test registry

## Success Criteria

1. **Critical Bug Fixed**: AuthSpec property type corrected, no runtime errors
2. **Validation Complete**: All tasks call both validation methods consistently
3. **Documentation Updated**: Usage examples show all new capabilities clearly
4. **DSL Enhanced**: Closure-based sourceRef configuration available
5. **100% Test Coverage**: Unit, functional, and integration tests cover all new functionality
6. **Backward Compatible**: Existing configurations continue to work unchanged
7. **User Experience**: Clear error messages, helpful defaults, intuitive DSL

## Implementation Priority

1. **Phase 1** (Critical): Fix AuthSpec property type - **MUST BE DONE FIRST**
2. **Phase 2** (High): Complete validation chain
3. **Phase 3** (High): Update documentation with examples
4. **Phase 4** (Medium): Add closure-based DSL method
5. **Phase 5** (High): Add comprehensive test coverage

## Verification Steps

1. Build plugin successfully: `./gradlew clean build`
2. All unit tests pass: `./gradlew test`
3. All functional tests pass: `./gradlew functionalTest`
4. Integration tests pass: `cd ../plugin-integration-test && ./gradlew clean testAll`
5. Documentation examples work in real usage scenarios
6. No configuration cache warnings: `./gradlew --configuration-cache`

## Benefits After Completion

1. **Consistent Architecture**: pullIfMissing works uniformly across all operations
2. **Flexible Configuration**: Multiple ways to specify source references
3. **Better User Experience**: Clear validation, helpful errors, intuitive DSL
4. **Production Ready**: Comprehensive test coverage and documentation
5. **Maintainable**: Clean architecture with proper separation of concerns