# Fix: DockerSaveTask Missing SourceRef Component Mode Support

**Status**: Not Started
**Priority**: High
**Created**: 2025-10-09
**Related Integration Test**: plugin-integration-test/docker/scenario-10

## Problem Statement

DockerSaveTask does not support sourceRef component mode (sourceRefImageName + sourceRefTag, etc.), causing "Unable to
build image reference" errors when users attempt to configure save operations using component properties instead of the
full sourceRef string.

This is inconsistent with DockerPublishTask, which fully supports component mode.

## Impact

Users cannot use component mode for save operations, forcing them to use the sourceRef string format. This creates an
inconsistent API and limits flexibility in how users can configure their Docker images.

### Affected Use Case

```groovy
docker {
    images {
        alpineTest {
            // This FAILS for save operations
            sourceRefImageName.set('alpine')
            sourceRefTag.set('latest')

            save {
                compression.set(NONE)
                outputFile.set(layout.buildDirectory.file('docker-images/alpine.tar'))
            }
        }
    }
}
```

Error: `Unable to build image reference`

## Root Cause Analysis

### Evidence

**1. DockerSaveTask is Missing Component Properties**

DockerPublishTask (working correctly) has all 5 component properties:
```groovy
// DockerPublishTask.groovy:54-58
@Input
@Optional
final Property<String> sourceRefRegistry = objectFactory.property(String)
final Property<String> sourceRefNamespace = objectFactory.property(String)
final Property<String> sourceRefImageName = objectFactory.property(String)
final Property<String> sourceRefRepository = objectFactory.property(String)
final Property<String> sourceRefTag = objectFactory.property(String)
```

DockerSaveTask (missing properties) only has:
```groovy
// DockerSaveTask.groovy:76-79
@Input
@Optional
final Property<String> sourceRef = objectFactory.property(String)
// Missing: sourceRefRegistry, sourceRefNamespace, sourceRefImageName, sourceRefRepository, sourceRefTag
```

**2. Plugin Configuration is Incomplete**

GradleDockerPlugin configures DockerPublishTask with all components (lines 568-572):
```groovy
task.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
task.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
task.sourceRefImageName.set(imageSpec.sourceRefImageName)
task.sourceRefRepository.set(imageSpec.sourceRefRepository)
task.sourceRefTag.set(imageSpec.sourceRefTag)
```

But configureDockerSaveTask() only sets the sourceRef string (line 488):
```groovy
task.sourceRef.set(imageSpec.sourceRef)
// Missing: component property configuration
```

**3. Task Logic Cannot Access Component Data**

DockerSaveTask.buildPrimaryImageReference() (line 124) only checks the sourceRef string:
```groovy
private ImageReference buildPrimaryImageReference() {
    if (sourceRef.isPresent() && !sourceRef.get().isEmpty()) {
        return ImageReference.parse(sourceRef.get())
    }
    // Falls through to build mode properties
    // Missing: component assembly logic
}
```

### Why It Fails

1. User specifies components in ImageSpec DSL (e.g., `sourceRefImageName.set('alpine')`)
2. ImageSpec stores them in its properties
3. Plugin configures DockerSaveTask but only passes the sourceRef string property
4. sourceRef string is NOT present (user used components instead)
5. DockerSaveTask can't access components because it doesn't have those properties
6. buildPrimaryImageReference() finds no sourceRef string and no build mode properties
7. Task fails with "Unable to build image reference"

### Architectural Inconsistency

Three different patterns exist across tasks:
- **DockerTagTask**: Accesses ImageSpec.getEffectiveSourceRef() directly
- **DockerPublishTask**: Has component properties (correct pattern for configuration cache)
- **DockerSaveTask**: Missing component properties (bug)

## Solution Design

### Required Changes

#### 1. Add Component Properties to DockerSaveTask

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerSaveTask.groovy`

Add after line 79:
```groovy
@Input
@Optional
final Property<String> sourceRefRegistry = objectFactory.property(String)

@Input
@Optional
final Property<String> sourceRefNamespace = objectFactory.property(String)

@Input
@Optional
final Property<String> sourceRefImageName = objectFactory.property(String)

@Input
@Optional
final Property<String> sourceRefRepository = objectFactory.property(String)

@Input
@Optional
final Property<String> sourceRefTag = objectFactory.property(String)
```

#### 2. Configure Component Properties in Plugin

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`

Modify configureDockerSaveTask() after line 488:
```groovy
task.sourceRef.set(imageSpec.sourceRef)
task.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
task.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
task.sourceRefImageName.set(imageSpec.sourceRefImageName)
task.sourceRefRepository.set(imageSpec.sourceRefRepository)
task.sourceRefTag.set(imageSpec.sourceRefTag)
```

#### 3. Update buildPrimaryImageReference() Logic

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerSaveTask.groovy`

Replace buildPrimaryImageReference() method (lines 124-143):
```groovy
private ImageReference buildPrimaryImageReference() {
    // Try direct sourceRef string first
    if (sourceRef.isPresent() && !sourceRef.get().isEmpty()) {
        return ImageReference.parse(sourceRef.get())
    }

    // Try component assembly for sourceRef mode
    def effectiveProps = EffectiveImageProperties.buildFromSourceRefComponents(
        sourceRefRegistry.getOrElse(""),
        sourceRefNamespace.getOrElse(""),
        sourceRefImageName.getOrElse(""),
        sourceRefRepository.getOrElse(""),
        sourceRefTag.getOrElse("")
    )

    if (effectiveProps != null) {
        return ImageReference.of(
            effectiveProps.registry(),
            effectiveProps.namespace(),
            effectiveProps.imageName(),
            effectiveProps.tag()
        )
    }

    // Fall back to build mode (existing logic)
    def effectiveBuildProps = EffectiveImageProperties.build(
        registry.getOrElse(""),
        namespace.getOrElse(""),
        imageName.getOrElse(""),
        repository.getOrElse(""),
        tags.getOrElse([]).isEmpty() ? "latest" : tags.get()[0]
    )

    return ImageReference.of(
        effectiveBuildProps.registry(),
        effectiveBuildProps.namespace(),
        effectiveBuildProps.imageName(),
        effectiveBuildProps.tag()
    )
}
```

## Implementation Plan

### Phase 1: Unit Tests (TDD Approach)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/DockerSaveTaskTest.groovy`

1. Add test cases for component mode:
   - `testSaveWithSourceRefImageNameAndTag()` - minimal components
   - `testSaveWithSourceRefNamespaceImageNameTag()` - namespace + imageName + tag
   - `testSaveWithSourceRefRegistryNamespaceImageNameTag()` - full Image Name Mode
   - `testSaveWithSourceRefRepositoryTag()` - Repository Mode minimal
   - `testSaveWithSourceRefRegistryRepositoryTag()` - Repository Mode with registry
   - `testSourceRefPrecedence()` - verify sourceRef string takes precedence over components
   - `testComponentsPrecedenceOverBuildMode()` - verify components take precedence over build mode

2. Verify tests FAIL (red phase)

3. Run from `plugin/`:
   ```bash
   ./gradlew clean test
   ```

### Phase 2: Implement Fix

4. **Update DockerSaveTask.groovy**:
   - Add 5 component properties
   - Update buildPrimaryImageReference() method

5. **Update GradleDockerPlugin.groovy**:
   - Add component property configuration in configureDockerSaveTask()

6. **Run unit tests** - verify all pass (green phase):
   ```bash
   ./gradlew clean test
   ```

7. **Verify 100% coverage**:
   - Check report at `build/reports/jacoco/test/html/index.html`
   - If gaps exist, add tests or document in `docs/design-docs/testing/unit-test-gaps.md`

### Phase 3: Functional Tests

**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/task/DockerSaveFunctionalTest.groovy`

8. Add functional test for component mode:
   - Create test project with sourceRefImageName + sourceRefTag
   - Execute dockerSave task
   - Verify tar file is created
   - Verify tar contains correct image

9. Run functional tests:
   ```bash
   ./gradlew clean functionalTest
   ```

### Phase 4: Integration Test (scenario-10)

**File**: `plugin-integration-test/docker/scenario-10/build.gradle`

10. Update scenario-10 to use component mode:
    - Replace `sourceRef.set('alpine:latest')` with:
      ```groovy
      sourceRefImageName.set('alpine')
      sourceRefTag.set('latest')
      ```

11. Build and publish plugin from `plugin/`:
    ```bash
    ./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal
    ```

12. Run scenario-10 from `plugin-integration-test/`:
    ```bash
    ./gradlew -Pplugin_version=1.0.0 docker:scenario-10:clean docker:scenario-10:integrationTest
    ```

13. Verify scenario-10 passes all acceptance criteria:
    - ensureSourceImage pulls alpine:latest
    - dockerImages tags and saves with component-specified source
    - verifySavedDockerImages finds tar file
    - verifyDockerImages finds scenario10-test:latest tag
    - verifyDockerBuildArgs verifies no build args

### Phase 5: Update Documentation

**File**: `plugin-integration-test/docker/README.md`

14. Update coverage matrix:
    - Line 54: Change scenario-10 to "Image Name Mode: sourceRefImageName, sourceRefTag"
    - Verify entry exists (should already be there)

**File**: `plugin/docs/usage/usage-docker.md` (if needed)

15. Verify usage documentation accurately describes component mode for save operations
16. Add examples if needed

### Phase 6: Full Regression Testing

17. Run all unit and functional tests from `plugin/`:
    ```bash
    ./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal
    ```

18. Run all integration tests from `plugin-integration-test/`:
    ```bash
    ./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest
    ```

19. Verify no containers remain:
    ```bash
    docker ps -a
    ```
    Expected: No containers

20. Verify no warnings during build

## Success Criteria

- [ ] All new unit tests pass with 100% code and branch coverage
- [ ] All existing unit tests continue to pass
- [ ] All functional tests pass
- [ ] Scenario-10 uses component form (sourceRefImageName + sourceRefTag)
- [ ] Scenario-10 integration test passes all acceptance criteria
- [ ] All 10 Docker integration test scenarios pass
- [ ] Build completes with no warnings
- [ ] No leftover Docker containers (`docker ps -a` is clean)
- [ ] README.md accurately reflects scenario-10 component mode coverage
- [ ] Usage documentation is accurate

## Testing Coverage

### Unit Test Coverage (Required)

Test all component combinations:
- sourceRefImageName + sourceRefTag (minimal Image Name Mode)
- sourceRefNamespace + sourceRefImageName + sourceRefTag
- sourceRefRegistry + sourceRefNamespace + sourceRefImageName + sourceRefTag (full Image Name Mode)
- sourceRefRepository + sourceRefTag (minimal Repository Mode)
- sourceRefRegistry + sourceRefRepository + sourceRefTag (full Repository Mode)

Test precedence rules:
- sourceRef string > components > build mode

Test edge cases:
- Empty component values
- Missing required components
- Mixed component modes (should fail or use defaults)

### Functional Test Coverage

- End-to-end save operation with component mode
- Verify tar file creation
- Verify correct image saved

### Integration Test Coverage

Scenario-10 covers:
- Source Ref Mode with Image Name Mode: sourceRefImageName + sourceRefTag
- pullIfMissing = true with image already local
- Tag with 1 tag
- Save with compression NONE
- No publish

## References

### Related Files

**Source Files**:
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerSaveTask.groovy` (lines 76-79, 124-143)
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy` (lines 473-510)
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy` (lines 54-58, working reference)
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy` (lines 320-355, component assembly)
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/model/EffectiveImageProperties.groovy` (lines 65-90)

**Test Files**:
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/DockerSaveTaskTest.groovy`
- `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/task/DockerSaveFunctionalTest.groovy`

**Integration Test**:
- `plugin-integration-test/docker/scenario-10/build.gradle`
- `plugin-integration-test/docker/README.md`

### Related Documentation

- `docs/usage/usage-docker.md` - User-facing documentation for docker DSL
- `docs/design-docs/gradle-9-and-10-compatibility.md` - Gradle 9/10 standards
- `docs/design-docs/testing/unit-test-gaps.md` - Coverage gap documentation
- `docs/project-standards/testing/unit-testing.md` - Unit testing requirements
- `docs/project-standards/testing/functional-testing.md` - Functional testing requirements

## Notes

- This bug exists because DockerSaveTask was not updated when component mode was added to other tasks
- The fix follows the established pattern from DockerPublishTask
- Component assembly infrastructure already exists in EffectiveImageProperties
- Configuration cache compatibility is maintained by using Property<T> types
- All code changes must adhere to 100% test coverage requirement
