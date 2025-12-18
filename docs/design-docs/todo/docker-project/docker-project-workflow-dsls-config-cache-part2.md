# Enhancement Plan: dockerProject and dockerWorkflows DSL Implementation

## Status: PLANNED

## Overview

This document outlines enhancements to improve the `dockerProject` and `dockerWorkflows` DSL implementations based on code analysis. These enhancements focus on code quality, feature completeness, reliability, and maintainability.

---

## Priority 1: DRY - Extract Repository Parsing Logic

**Effort:** Low | **Impact:** High (maintainability)

### Problem
Repository parsing logic is duplicated 9 times in `DockerProjectTaskGenerator.groovy`:
```groovy
def repo = imageSpec.repository.get()
def lastSlash = repo.lastIndexOf('/')
def extractedImageName = lastSlash >= 0 ? repo.substring(lastSlash + 1) : repo
```

### Implementation Steps

1. **Add `RepositoryParts` data class to `TaskNamingUtils.groovy`**
   - Create static inner class with `namespace` and `imageName` fields
   - Add `equals()`, `hashCode()`, and `toString()` methods

2. **Add `parseRepository(String)` method to `TaskNamingUtils`**
   - Handle null/empty input (return empty parts)
   - Parse namespace and imageName from repository string
   - Return `RepositoryParts` instance

3. **Refactor `DockerProjectTaskGenerator.groovy`**
   - Replace all 9 occurrences with calls to `TaskNamingUtils.parseRepository()`
   - Update lines: 203, 257, 276, 495, 505, 569, 584, 636, 653

4. **Add unit tests to `TaskNamingUtilsTest.groovy`**
   - Test null input
   - Test empty string
   - Test simple image name (no slash): `"myapp"` → `namespace: "", imageName: "myapp"`
   - Test namespace/image: `"myorg/myapp"` → `namespace: "myorg", imageName: "myapp"`
   - Test nested namespace: `"myorg/sub/myapp"` → `namespace: "myorg/sub", imageName: "myapp"`

---

## Priority 2: Complete Multiple Test Configurations Feature

**Effort:** Medium | **Impact:** High (feature completeness)

### Problem
`ProjectTestConfigSpec` class exists but is not wired into the DSL. The design specifies a `tests { }` container for multiple named test configurations with different lifecycle modes.

### Implementation Steps

1. **Update `DockerProjectSpec.groovy`**
   - Add `NamedDomainObjectContainer<ProjectTestConfigSpec> testsContainer` field
   - Initialize in `initializeNestedSpecs()` method
   - Add `getTests()` accessor method
   - Add `tests(Closure)` DSL method
   - Add `tests(Action)` DSL method

2. **Add validation for mutual exclusivity**
   - Add `validateTestConfiguration()` method
   - Throw `GradleException` if both `test {}` and `tests {}` are configured
   - Call validation in `isConfigured()` or add separate validation hook

3. **Update `DockerProjectTaskGenerator.groovy`**
   - Add `generateMultipleTestTasks()` method
   - For each `ProjectTestConfigSpec`:
     - Generate test task named `{configName}IntegrationTest`
     - Generate corresponding `composeUp{ConfigName}` and `composeDown{ConfigName}` tasks
     - Apply test class filtering via `Test.filter.includeTestsMatching()`
   - Update `wireTaskDependencies()` to handle multiple test configurations
   - Update lifecycle task to depend on all test configurations passing

4. **Add unit tests**
   - Test `tests {}` DSL configuration
   - Test mutual exclusivity validation
   - Test task generation for multiple configs
   - Test test class filtering configuration

5. **Add functional test**
   - Create functional test verifying multiple test configurations register correct tasks

6. **Add integration test scenario**
   - Create `scenario-10-multiple-tests/` with:
     - Two test configurations: `apiTests` (CLASS lifecycle), `statefulTests` (METHOD lifecycle)
     - Different compose files for each
     - Different test class packages

---

## Priority 3: Complete CleanupTask Implementation

**Effort:** Medium | **Impact:** Medium (functionality)

### Problem
`CleanupTask` has placeholder logic that logs "would remove" instead of actually removing containers, networks, and images.

### Implementation Steps

1. **Extend `DockerService` interface** (if needed)
   - Add `removeContainer(String containerId)` method
   - Add `removeNetwork(String networkId)` method
   - Add `removeImage(String imageRef)` method

2. **Implement in `DockerServiceImpl`**
   - Implement container removal via Docker Java Client
   - Implement network removal via Docker Java Client
   - Implement image removal via Docker Java Client
   - Handle errors gracefully (log warnings, don't fail)

3. **Update `CleanupTask.groovy`**
   - Replace placeholder logic with actual DockerService calls
   - Wrap each removal in try-catch for best-effort cleanup
   - Log success/failure for each operation

4. **Add unit tests for CleanupTask**
   - Test container removal calls DockerService
   - Test network removal calls DockerService
   - Test image removal calls DockerService
   - Test failures are logged but don't fail the task
   - Test compose stack cleanup via ComposeService

5. **Document any gaps**
   - If some cleanup scenarios cannot be unit tested, document in `docs/design-docs/testing/unit-test-gaps.md`

---

## Priority 4: Thread-Safe File Writes in PipelineStateFile

**Effort:** Low | **Impact:** Medium (reliability)

### Problem
`PipelineStateFile.writeToFile()` claims thread safety but uses non-atomic file write.

### Implementation Steps

1. **Update `writeToFile()` in `PipelineStateFile.groovy`**
   - Write to temporary file first (`{filename}.tmp`)
   - Use `java.nio.file.Files.move()` with `ATOMIC_MOVE` option
   - Handle `AtomicMoveNotSupportedException` by falling back to regular move
   - Clean up temp file on failure

2. **Add unit tests**
   - Test successful atomic write
   - Test fallback when atomic move not supported
   - Test temp file cleanup on failure

3. **Update class documentation**
   - Update thread safety comment to accurately describe the behavior

---

## Priority 5: Add Mutual Exclusivity Validation

**Effort:** Low | **Impact:** Medium (UX)

### Problem
Mutual exclusivity rules are documented but not enforced with clear error messages.

### Implementation Steps

1. **Add `validate()` method to `ProjectImageSpec.groovy`**
   - Check `imageName` and `repository` mutual exclusivity
   - Check `jarFrom`, `contextTask`, `contextDir`, `sourceRef` mutual exclusivity
   - Check `pullPolicy` only valid in Source Reference Mode
   - Throw `GradleException` with specific, helpful error messages

2. **Call validation from `DockerProjectTaskGenerator.generate()`**
   - Validate each image spec before generating tasks
   - Fail fast with clear error message

3. **Add unit tests**
   - Test each mutual exclusivity rule
   - Test error messages are clear and actionable

---

## Priority 6: Pass Context Data to Hooks

**Effort:** Low | **Impact:** Low (debugging)

### Problem
Hook callbacks receive `null` instead of useful context data.

### Implementation Steps

1. **Create `HookContext` class**
   - Location: `plugin/src/main/groovy/com/kineticfire/gradle/docker/workflow/HookContext.groovy`
   - Properties: `taskName`, `pipelineName`, `timestamp`, `phase` (before/after)

2. **Update `WorkflowTaskGenerator.groovy`**
   - Create `HookContext` instances for each hook execution
   - Pass context instead of `null` to `beforeBuild`, `afterBuild`, `beforeTest`, `afterTest`, `afterSuccess`

3. **Add unit tests**
   - Test context is properly populated
   - Test context is passed to hooks

4. **Update documentation**
   - Document hook context properties in usage docs

---

## Priority 7: Add Image Name Priority Constants

**Effort:** Low | **Impact:** Low (readability)

### Problem
`deriveImageName()` has 6 fallback levels that are hard to follow.

### Implementation Steps

1. **Add documentation comment to `deriveImageName()`**
   - Document the priority order with numbered list
   - Explain rationale for each fallback level

2. **Consider extracting to helper class** (optional)
   - Create `ImageNameResolver` with explicit priority handling
   - Make priority order configurable if needed in future

---

## Priority 8: Cache Task Lookups in Generators

**Effort:** Low | **Impact:** Low (performance)

### Problem
Multiple calls to `taskExists()` followed by `project.tasks.named()` for the same task.

### Implementation Steps

1. **Refactor `wireTaskDependencies()` in both generators**
   - Use `project.tasks.findByName()` once per task
   - Store result in local variable
   - Use stored reference for subsequent operations

2. **Verify no functional change**
   - Run existing unit and functional tests to confirm behavior unchanged

---

## Acceptance Criteria

1. All unit tests pass with 100% coverage on new/modified code
2. All functional tests pass
3. All integration tests pass (scenarios 1-9 + any new scenarios)
4. No compiler warnings
5. Code follows project style guidelines (≤500 lines/file, ≤40 lines/function)
6. Documentation updated for any user-facing changes

---

## Execution Order

Recommended execution order based on dependencies:

1. **Priority 1** (DRY) - No dependencies, foundational refactor
2. **Priority 5** (Validation) - No dependencies, improves error handling
3. **Priority 4** (Thread Safety) - No dependencies, standalone fix
4. **Priority 8** (Task Caching) - No dependencies, simple optimization
5. **Priority 7** (Constants) - No dependencies, documentation improvement
6. **Priority 6** (Hook Context) - No dependencies, standalone enhancement
7. **Priority 3** (CleanupTask) - May need DockerService extensions
8. **Priority 2** (Multiple Tests) - Largest change, depends on stable foundation
