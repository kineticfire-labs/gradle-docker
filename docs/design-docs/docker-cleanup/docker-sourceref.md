# Implementation Plan: SourceRef Repository Support & Mode Validation

**Doc meta**
- Owner: Development Team
- Status: Ready for Implementation
- Version: 1.0.0
- Last updated: 2025-01-24
- Comment: Add repository support to sourceRef component assembly and implement comprehensive mode validation for architectural consistency

## Overview

Add missing repository support to sourceRef component assembly to achieve feature parity with build mode nomenclature options. Implement comprehensive validation to prevent mixing incompatible configuration approaches across build mode and sourceRef mode.

## Current Architecture Gap

**Build Mode** (for new images) supports **both nomenclature patterns**:
1. `registry` + `namespace` + `imageName` + `tags` ✅
2. `registry` + `repository` + `tags` ✅

**SourceRef Mode** (for existing images) only supports **one pattern**:
1. `sourceRefRegistry` + `sourceRefNamespace` + `sourceRefImageName` + `sourceRefTag` ✅
2. `sourceRefRegistry` + `sourceRefRepository` + `sourceRefTag` ❌ **MISSING**

This creates an inconsistent user experience where the same Docker nomenclature patterns aren't available across both modes.

## Phase 6: Add Repository Support to SourceRef Component Assembly

### Step 6.1: Add Missing Property
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy`
**Location**: After existing sourceRef properties (around line 139)

**Add Property**:
```groovy
@Input
@Optional
abstract Property<String> getSourceRefRepository()
```

**Update Constructor Conventions** (around line 62):
```groovy
sourceRefRepository.convention("")
```

### Step 6.2: Update Assembly Logic
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy`
**Method**: `getEffectiveSourceRef()` (around line 293)

**Replace Entire Method**:
```groovy
String getEffectiveSourceRef() {
    if (sourceRef.isPresent() && !sourceRef.get().isEmpty()) {
        return sourceRef.get()
    }

    // Assemble from components
    def registry = sourceRefRegistry.getOrElse("")
    def namespace = sourceRefNamespace.getOrElse("")
    def repository = sourceRefRepository.getOrElse("")
    def imageName = sourceRefImageName.getOrElse("")
    def tag = sourceRefTag.getOrElse("")

    // If tag is empty, default to "latest"
    if (tag.isEmpty()) {
        tag = "latest"
    }

    // Repository approach takes precedence (mirrors build mode logic)
    if (!repository.isEmpty()) {
        def baseRef = registry.isEmpty() ? repository : "${registry}/${repository}"
        return "${baseRef}:${tag}"
    }

    // Fall back to namespace + imageName approach
    if (imageName.isEmpty()) {
        throw new GradleException("Either sourceRef, sourceRefRepository, or sourceRefImageName must be specified for image '${name}'")
    }

    def reference = ""
    if (!registry.isEmpty()) {
        reference += registry + "/"
    }
    if (!namespace.isEmpty()) {
        reference += namespace + "/"
    }
    reference += imageName
    // Always add tag - it defaults to "latest" if empty
    reference += ":" + tag

    return reference
}
```

### Step 6.3: Add Repository Method to SourceRefSpec
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy`
**Class**: `SourceRefSpec` (around line 377)

**Add Method**:
```groovy
void repository(String repository) {
    parent.sourceRefRepository.set(repository)
}
```

### Step 6.4: Update Validation Logic
**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy`
**Location**: After existing validation methods (around line 351)

**Update `validateSourceRefConfiguration()` Method**:
```groovy
void validateSourceRefConfiguration() {
    def hasDirectSourceRef = sourceRef.isPresent() && !sourceRef.get().isEmpty()
    def hasRepositorySourceRef = sourceRefRepository.isPresent() && !sourceRefRepository.get().isEmpty()
    def hasImageNameSourceRef = sourceRefImageName.isPresent() && !sourceRefImageName.get().isEmpty()

    if (!hasDirectSourceRef && !hasRepositorySourceRef && !hasImageNameSourceRef && pullIfMissing.getOrElse(false)) {
        throw new GradleException(
            "pullIfMissing=true requires either sourceRef, sourceRefRepository, or sourceRefImageName to be specified for image '${name}'"
        )
    }
}
```

**Add New Comprehensive Mode Validation Method**:
```groovy
void validateModeConsistency() {
    // Detect build mode properties
    def hasBuildMode = contextTask != null ||
        !registry.getOrElse("").isEmpty() ||
        !namespace.getOrElse("").isEmpty() ||
        !imageName.getOrElse("").isEmpty() ||
        !repository.getOrElse("").isEmpty() ||
        !buildArgs.get().isEmpty() ||
        !labels.get().isEmpty()

    // Detect sourceRef mode properties
    def hasDirectSourceRef = !sourceRef.getOrElse("").isEmpty()
    def hasSourceRefComponents = !sourceRefRegistry.getOrElse("").isEmpty() ||
        !sourceRefNamespace.getOrElse("").isEmpty() ||
        !sourceRefImageName.getOrElse("").isEmpty() ||
        !sourceRefRepository.getOrElse("").isEmpty() ||
        !sourceRefTag.getOrElse("").isEmpty()

    // Rule 2a: Build mode excludes all sourceRef properties
    if (hasBuildMode && (hasDirectSourceRef || hasSourceRefComponents)) {
        throw new GradleException(
            "Cannot mix build mode properties (context, registry, namespace, imageName, repository, buildArgs, labels) " +
            "with sourceRef mode properties for image '${name}'. Choose either build mode OR sourceRef mode."
        )
    }

    // Rule 2b: Direct sourceRef excludes component assembly and build mode
    if (hasDirectSourceRef && (hasSourceRefComponents || hasBuildMode)) {
        throw new GradleException(
            "Cannot use direct sourceRef with component assembly properties or build mode properties for image '${name}'. " +
            "Use sourceRef alone for complete image references."
        )
    }

    // Rule 2c & 2d: Component assembly mutual exclusions
    def hasNamespaceMode = !sourceRefNamespace.getOrElse("").isEmpty() || !sourceRefImageName.getOrElse("").isEmpty()
    def hasRepositoryMode = !sourceRefRepository.getOrElse("").isEmpty()

    if (hasNamespaceMode && hasRepositoryMode) {
        throw new GradleException(
            "Cannot use both namespace+imageName and repository approaches in sourceRef component assembly for image '${name}'. " +
            "Choose either sourceRefNamespace+sourceRefImageName OR sourceRefRepository."
        )
    }

    // Ensure repository mode doesn't mix with namespace/imageName
    if (hasRepositoryMode && (hasNamespaceMode || hasBuildMode)) {
        throw new GradleException(
            "When using sourceRefRepository, cannot use sourceRef, sourceRefNamespace, sourceRefImageName, or build mode properties " +
            "for image '${name}'. Repository mode should use: sourceRefRegistry + sourceRefRepository + sourceRefTag only."
        )
    }
}
```

### Step 6.5: Update Task Validation Calls
**Files**:
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerSaveTask.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerTagTask.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`

**Update `pullSourceRefIfNeeded()` Method** (add validation call):
```groovy
private void pullSourceRefIfNeeded() {
    def imageSpecValue = imageSpec.orNull
    if (!imageSpecValue) return

    imageSpecValue.validateModeConsistency()        // NEW
    imageSpecValue.validateSourceRefConfiguration()
    imageSpecValue.validatePullIfMissingConfiguration()

    // ... existing pull logic
}
```

## Phase 7: Documentation Updates

### Step 7.1: Update "Two Usage Modes" Section
**File**: `docs/design-docs/usage-docker.md`
**Location**: Around line 43 (after Build Mode description)

**Update Section**:
```markdown
## Two Usage Modes

The plugin supports two distinct usage modes:

1. **Build Mode** - Building new Docker images using nomenclature options
   1. registry, namespace, imageName, tags
   2. registry, repository, tags

2. **SourceRef Mode** - Working with existing/pre-built images using sourceRef
   1. Complete sourceRef string (e.g., "ghcr.io/company/app:v1.0")
   2. registry, namespace, imageName, tag (component assembly)
   3. registry, repository, tag (component assembly)
```

### Step 7.2: Add Scenario 6
**File**: `docs/design-docs/usage-docker.md`
**Location**: After Scenario 5 (around line 323)

**Add New Scenario**:
```markdown
### Scenario 6: SourceRef Component Assembly (Repository Approach)

Using the repository approach for component assembly (alternative to namespace+imageName):

```groovy
docker {
    images {
        // Repository-based component assembly
        ghcrApp {
            sourceRefRegistry.set("ghcr.io")
            sourceRefRepository.set("company/myapp")    // Combines namespace/imageName
            sourceRefTag.set("v2.1.0")
            // Results in: ghcr.io/company/myapp:v2.1.0

            pullIfMissing.set(true)

            pullAuth {
                username.set(providers.environmentVariable("GHCR_USER"))
                password.set(providers.environmentVariable("GHCR_TOKEN"))
            }

            save {
                outputFile.set(file("build/ghcr-app.tar"))
            }
        }

        // Mixed approaches in same project (different images)
        dockerhubApp {
            sourceRefRegistry.set("docker.io")
            sourceRefRepository.set("username/webapp")
            // sourceRefTag defaults to "latest"
            // Results in: docker.io/username/webapp:latest

            pullIfMissing.set(true)

            tag {
                tags.set(["local:webapp"])
            }
        }

        // Registry-only repository
        localApp {
            sourceRefRepository.set("internal/app")     // No registry specified
            sourceRefTag.set("stable")
            // Results in: internal/app:stable

            pullIfMissing.set(true)

            publish {
                to('prod') {
                    registry.set("prod.company.com")
                    repository.set("production/app")
                    publishTags.set(["deployed"])
                }
            }
        }
    }
}
```
```

### Step 7.3: Update API Properties Section
**File**: `docs/design-docs/usage-docker.md`
**Location**: Around line 485 (in existing sourceRef properties)

**Add to Existing Properties List**:
```markdown
- `sourceRefRepository.set(String)` - Source repository component (e.g., "company/app") - alternative to namespace+imageName
```

### Step 7.4: Update Usage Notes Section
**File**: `docs/design-docs/usage-docker.md`
**Location**: Around line 567 (Component Assembly Priority section)

**Update Component Assembly Priority**:
```markdown
#### Component Assembly Priority
1. Full `sourceRef.set("registry/namespace/image:tag")` takes precedence over all components
2. Repository approach: `sourceRefRepository` takes precedence over `sourceRefNamespace + sourceRefImageName`
3. Namespace approach: used when `sourceRefRepository` is not specified
4. Required: either `sourceRef` OR `sourceRefRepository` OR `sourceRefImageName` must be specified when using pullIfMissing=true

#### Component Assembly Patterns
- **Full Reference**: `sourceRef.set("ghcr.io/company/app:v1.0")` - complete image reference
- **Repository Assembly**: `sourceRefRegistry + sourceRefRepository + sourceRefTag` - mirrors build mode repository approach
- **Namespace Assembly**: `sourceRefRegistry + sourceRefNamespace + sourceRefImageName + sourceRefTag` - mirrors build mode namespace approach
- **Mutually Exclusive**: Cannot mix repository and namespace approaches in same image configuration
```

## Phase 8: Comprehensive Test Coverage

### Step 8.1: Unit Test Updates
**Files to Update**:
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/ImageSpecTest.groovy`

**New Test Categories**:

**A) Repository Component Assembly Tests**:
```groovy
def "getEffectiveSourceRef assembles from repository components correctly"() {
    when:
    imageSpec.sourceRefRegistry.set("ghcr.io")
    imageSpec.sourceRefRepository.set("company/app")
    imageSpec.sourceRefTag.set("v1.0")

    then:
    imageSpec.getEffectiveSourceRef() == "ghcr.io/company/app:v1.0"
}

def "getEffectiveSourceRef handles repository without registry"() {
    when:
    imageSpec.sourceRefRepository.set("company/app")
    imageSpec.sourceRefTag.set("latest")

    then:
    imageSpec.getEffectiveSourceRef() == "company/app:latest"
}

def "getEffectiveSourceRef prefers repository over namespace+imageName"() {
    when:
    imageSpec.sourceRefRepository.set("company/app")
    imageSpec.sourceRefNamespace.set("other")
    imageSpec.sourceRefImageName.set("other")
    imageSpec.sourceRefTag.set("v1.0")

    then:
    imageSpec.getEffectiveSourceRef() == "company/app:v1.0"
}
```

**B) Repository DSL Tests**:
```groovy
def "sourceRef closure DSL with repository configures correctly"() {
    when:
    imageSpec.sourceRef {
        registry "docker.io"
        repository "username/webapp"
        tag "stable"
    }

    then:
    imageSpec.sourceRefRegistry.get() == "docker.io"
    imageSpec.sourceRefRepository.get() == "username/webapp"
    imageSpec.sourceRefTag.get() == "stable"
    imageSpec.getEffectiveSourceRef() == "docker.io/username/webapp:stable"
}
```

**C) Mode Consistency Validation Tests**:
```groovy
def "validateModeConsistency throws when mixing build mode with sourceRef"() {
    when:
    imageSpec.registry.set("docker.io")  // build mode
    imageSpec.sourceRef.set("nginx:latest")  // sourceRef mode
    imageSpec.validateModeConsistency()

    then:
    def ex = thrown(GradleException)
    ex.message.contains("Cannot mix build mode properties with sourceRef mode properties")
}

def "validateModeConsistency throws when mixing direct sourceRef with components"() {
    when:
    imageSpec.sourceRef.set("nginx:latest")
    imageSpec.sourceRefRegistry.set("docker.io")
    imageSpec.validateModeConsistency()

    then:
    def ex = thrown(GradleException)
    ex.message.contains("Cannot use direct sourceRef with component assembly")
}

def "validateModeConsistency throws when mixing repository with namespace approaches"() {
    when:
    imageSpec.sourceRefRepository.set("company/app")
    imageSpec.sourceRefNamespace.set("company")
    imageSpec.sourceRefImageName.set("app")
    imageSpec.validateModeConsistency()

    then:
    def ex = thrown(GradleException)
    ex.message.contains("Cannot use both namespace+imageName and repository approaches")
}

def "validateModeConsistency succeeds with pure repository mode"() {
    when:
    imageSpec.sourceRefRegistry.set("ghcr.io")
    imageSpec.sourceRefRepository.set("company/app")
    imageSpec.sourceRefTag.set("v1.0")
    imageSpec.validateModeConsistency()

    then:
    noExceptionThrown()
}

def "validateModeConsistency succeeds with pure namespace mode"() {
    when:
    imageSpec.sourceRefRegistry.set("docker.io")
    imageSpec.sourceRefNamespace.set("library")
    imageSpec.sourceRefImageName.set("alpine")
    imageSpec.validateModeConsistency()

    then:
    noExceptionThrown()
}
```

### Step 8.2: Functional Test Updates
**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/SourceRefRepositoryFunctionalTest.groovy`

**Create New Functional Test**:
```groovy
def "sourceRef repository component assembly works in DSL"() {
    given:
    def projectDir = File.createTempDir()
    def buildFile = new File(projectDir, 'build.gradle')

    buildFile.text = '''
        plugins {
            id 'com.kineticfire.gradle.gradle-docker'
        }

        docker {
            images {
                repositoryTest {
                    sourceRefRegistry.set("docker.io")
                    sourceRefRepository.set("library/nginx")
                    sourceRefTag.set("alpine")
                    pullIfMissing.set(false)

                    tag {
                        tags.set(["test:repository-assembly"])
                    }
                }
            }
        }
    '''

    when:
    def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('help', '--stacktrace')
            .withPluginClasspath()
            .build()

    then:
    result.task(':help').outcome == TaskOutcome.SUCCESS
}

def "mode consistency validation prevents mixing approaches"() {
    given:
    def projectDir = File.createTempDir()
    def buildFile = new File(projectDir, 'build.gradle')

    buildFile.text = '''
        plugins {
            id 'com.kineticfire.gradle.gradle-docker'
        }

        docker {
            images {
                invalidMix {
                    registry.set("docker.io")              // build mode
                    sourceRefRepository.set("company/app") // sourceRef mode

                    save {
                        outputFile.set(file("build/test.tar"))
                    }
                }
            }
        }
    '''

    when:
    def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('dockerSaveInvalidMix', '--stacktrace')
            .withPluginClasspath()
            .buildAndFail()

    then:
    result.output.contains("Cannot mix build mode properties with sourceRef mode properties")
}
```

## Success Criteria

1. **Repository Support**: sourceRefRepository property works correctly for component assembly
2. **Feature Parity**: SourceRef mode supports both nomenclature patterns like Build mode
3. **Assembly Logic**: Repository approach takes precedence over namespace+imageName (mirrors build mode)
4. **Validation Complete**: All mode consistency rules enforced with clear error messages
5. **DSL Enhanced**: Closure-based sourceRef DSL supports repository method
6. **Documentation Updated**: Usage examples show all three SourceRef approaches clearly
7. **Test Coverage**: 100% coverage of new functionality and validation rules
8. **Backward Compatible**: Existing configurations continue to work unchanged

## Implementation Priority

1. **Phase 6** (Critical): Add repository support and comprehensive validation
2. **Phase 7** (High): Update documentation with new examples
3. **Phase 8** (High): Add comprehensive test coverage

## Verification Steps

1. Repository component assembly works: `sourceRefRegistry + sourceRefRepository + sourceRefTag`
2. Repository takes precedence over namespace+imageName when both specified
3. All validation rules prevent invalid configurations with clear messages
4. DSL closure syntax supports repository method
5. Documentation examples work in real usage scenarios
6. All unit and functional tests pass
7. Existing functionality remains unaffected

## Benefits After Completion

1. **Architectural Consistency**: SourceRef mode matches Build mode capabilities exactly
2. **User Experience**: Same nomenclature patterns available across both modes
3. **Flexibility**: Users can choose registry/repository OR registry/namespace/imageName patterns
4. **Safety**: Comprehensive validation prevents configuration errors and confusion
5. **Maintainability**: Clear separation between mutually exclusive configuration approaches
6. **Documentation**: Complete coverage of all supported usage patterns with examples