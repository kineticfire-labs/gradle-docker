# Test Coverage Analysis and Enhancement Plan

## Status: PLANNED

## Overview

This document analyzes the test coverage for the `dockerProject` and `dockerWorkflows` DSL implementations and provides a plan to address identified gaps, specifically for `CleanupTask` and `TagOnSuccessTask`.

---

## Test Coverage Analysis

### Current State Summary

| Test Type | Location | Lines of Code | Coverage |
|-----------|----------|---------------|----------|
| Unit Tests | `plugin/src/test/` | ~5,157 lines (generator + spec) | Comprehensive |
| Functional Tests | `plugin/src/functionalTest/` | ~20,676 lines (44 files) | Good - mostly indirect |
| Integration Tests | `plugin-integration-test/` | 30+ scenarios | Comprehensive |

### Component-Level Coverage

| Component | Unit Tests | Functional Tests | Integration Tests |
|-----------|------------|------------------|-------------------|
| TaskGraphGenerator | ✅ Direct | ⚠️ Indirect | ⚠️ Indirect |
| TaskNamingUtils | ✅ Direct | N/A (utility) | ⚠️ Indirect |
| PipelineStateFile | ✅ Direct | N/A (utility) | ⚠️ Indirect |
| DockerProjectTaskGenerator | ✅ Direct | ⚠️ Indirect | ✅ 9 scenarios |
| WorkflowTaskGenerator | ✅ Direct | ⚠️ Indirect | ✅ 8 scenarios |
| ProjectImageSpec | ✅ Direct | ⚠️ Indirect | ✅ Yes |
| TagOnSuccessTask | ✅ Direct | ❌ **None** | ⚠️ Indirect |
| CleanupTask | ⚠️ **Partial** | ❌ **None** | ⚠️ Indirect |

### Explanation of Coverage Types

- **✅ Direct**: Component is explicitly tested with dedicated test cases
- **⚠️ Indirect**: Component is exercised through DSL usage but not directly tested
- **❌ None**: No tests exist for this component at this test level
- **N/A**: Not applicable (e.g., pure utility classes don't need functional tests)

### Why Indirect Coverage Is Acceptable (In Most Cases)

For most components, indirect coverage through DSL tests is appropriate because:

1. **Functional tests should test the public API** (DSL syntax), not internal implementation
2. **Integration tests verify end-to-end behavior** with real Docker operations
3. **Unit tests provide direct coverage** for implementation details

However, two components have **insufficient coverage**:

---

## Gap Analysis

### Gap 1: CleanupTask - Partial Implementation and Incomplete Tests

**Location:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/CleanupTask.groovy`

**Issues Identified:**

1. **Placeholder Implementation** (lines 152-184):
   ```groovy
   // Future: Iterate containers and remove them via DockerService
   // Future: Iterate networks and remove them via DockerService
   // Future: Iterate images and remove them via DockerService
   ```
   The task logs "would remove" but doesn't actually perform cleanup operations.

2. **Unit Test Coverage** (`CleanupTaskTest.groovy`):
   - Tests exist but may not cover all branches
   - Placeholder logic means tests pass without verifying actual cleanup

3. **No Functional Tests**:
   - CleanupTask behavior is not verified via Gradle TestKit
   - Edge cases (failures, partial cleanup) are not tested

**Risk:** Cleanup operations silently fail, leaving Docker resources behind.

### Gap 2: TagOnSuccessTask - No Functional Tests

**Location:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/TagOnSuccessTask.groovy`

**Issues Identified:**

1. **Unit Tests Exist** (`TagOnSuccessTaskTest.groovy`):
   - Direct tests verify task action logic
   - Mocked DockerService verifies tag operations

2. **No Functional Tests**:
   - Task registration via DSL is not verified
   - Task configuration wiring is not tested
   - Task dependency chain is not verified
   - `onlyIf` predicate behavior with test result files is not tested

**Risk:** Task may be incorrectly wired or have configuration issues not caught by unit tests.

---

## Enhancement Plan

### Part A: CleanupTask Unit Test Enhancement

**Goal:** Achieve 100% unit test coverage for CleanupTask, including the placeholder logic paths.

#### Step A.1: Analyze Existing Unit Tests

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/CleanupTaskTest.groovy`

1. Read existing tests to identify coverage gaps
2. Identify untested branches and edge cases
3. Document which test cases need to be added

#### Step A.2: Add Missing Unit Test Cases

Add test cases for:

1. **Compose stack cleanup**
   - Test `stackName` is set and `composeService` is present → calls `downStack()`
   - Test `stackName` is empty → skips compose cleanup
   - Test `composeService` not present → skips compose cleanup
   - Test `downStack()` throws exception → logs warning, doesn't fail task

2. **Container cleanup**
   - Test `removeContainers = true` with non-empty `containerNames` → logs cleanup message
   - Test `removeContainers = false` → skips container cleanup
   - Test `containerNames` is empty → skips container cleanup

3. **Network cleanup**
   - Test `removeNetworks = true` with non-empty `networkNames` → logs cleanup message
   - Test `removeNetworks = false` → skips network cleanup
   - Test `networkNames` is empty → skips network cleanup

4. **Image cleanup**
   - Test `removeImages = true` with non-empty `imageNames` → logs cleanup message
   - Test `removeImages = false` → skips image cleanup
   - Test `imageNames` is empty → skips image cleanup

5. **Combined scenarios**
   - Test all cleanup options enabled → all operations logged
   - Test no cleanup options enabled → task completes with no operations
   - Test mixed success/failure → correct counts logged

6. **Edge cases**
   - Test null values handled gracefully
   - Test empty string `stackName` treated as not set
   - Test `@UntrackedTask` annotation prevents up-to-date skipping

#### Step A.3: Add Tests for Future Implementation

When the placeholder logic is replaced with actual DockerService calls, add:

1. Test `dockerService.removeContainer()` is called for each container
2. Test `dockerService.removeNetwork()` is called for each network
3. Test `dockerService.removeImage()` is called for each image
4. Test partial failures don't prevent other cleanup operations
5. Test exceptions are caught and logged, not propagated

---

### Part B: CleanupTask Functional Tests

**Goal:** Verify CleanupTask is correctly registered and configured via DSL.

**File to Create:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/CleanupTaskFunctionalTest.groovy`

#### Step B.1: Create Test Class Structure

```groovy
class CleanupTaskFunctionalTest extends Specification {
    @TempDir Path testProjectDir
    File settingsFile
    File buildFile

    def setup() { ... }
}
```

#### Step B.2: Add DSL Configuration Tests

1. **Test cleanup task created when always block configured**
   ```groovy
   def "dockerWorkflows with always block creates cleanup task"() {
       // Configure dockerWorkflows with always { removeTestContainers = true }
       // Verify cleanup task is registered
   }
   ```

2. **Test cleanup task not created when no always block**
   ```groovy
   def "dockerWorkflows without always block skips cleanup task"() {
       // Configure dockerWorkflows without always block
       // Verify no cleanup task registered
   }
   ```

3. **Test cleanup task not created when always block has no cleanup options**
   ```groovy
   def "dockerWorkflows with empty always block skips cleanup task"() {
       // Configure always {} with no options set
       // Verify no cleanup task registered
   }
   ```

#### Step B.3: Add Task Dependency Tests

1. **Test cleanup task is finalizer for test task**
   ```groovy
   def "cleanup task is finalizedBy test task"() {
       // Configure workflow with always block
       // Verify test task has finalizedBy relationship to cleanup task
   }
   ```

2. **Test cleanup task runs after test failure**
   ```groovy
   def "cleanup task runs even when test fails"() {
       // Configure workflow with always block
       // Mock test failure
       // Verify cleanup task is still scheduled
   }
   ```

#### Step B.4: Add Task Configuration Tests

1. **Test removeContainers property wiring**
   ```groovy
   def "cleanup task removeContainers configured from always block"() {
       // Configure always { removeTestContainers = true }
       // Verify task.removeContainers.get() == true
   }
   ```

2. **Test removeImages property wiring**
   ```groovy
   def "cleanup task removeImages configured from always block"() {
       // Configure always { cleanupImages = true }
       // Verify task.removeImages.get() == true
   }
   ```

3. **Test stackName property wiring**
   ```groovy
   def "cleanup task stackName configured from test step"() {
       // Configure test step with stack
       // Verify cleanup task has stackName set
   }
   ```

#### Step B.5: Add Edge Case Tests

1. **Test cleanup task with all options disabled**
2. **Test cleanup task with partial options enabled**
3. **Test cleanup task in multiple pipeline scenario**

---

### Part C: TagOnSuccessTask Functional Tests

**Goal:** Verify TagOnSuccessTask is correctly registered, configured, and wired via DSL.

**File to Create:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TagOnSuccessTaskFunctionalTest.groovy`

#### Step C.1: Create Test Class Structure

```groovy
class TagOnSuccessTaskFunctionalTest extends Specification {
    @TempDir Path testProjectDir
    File settingsFile
    File buildFile

    def setup() { ... }
}
```

#### Step C.2: Add dockerProject DSL Tests

1. **Test tag on success task created when additionalTags configured**
   ```groovy
   def "dockerProject with additionalTags creates tag on success task"() {
       // Configure dockerProject with onSuccess { additionalTags = ['tested'] }
       // Verify dockerProjectTagOnSuccess task is registered
   }
   ```

2. **Test tag on success task not created when no additionalTags**
   ```groovy
   def "dockerProject without additionalTags skips tag on success task"() {
       // Configure dockerProject with onSuccess {} (no additionalTags)
       // Verify no dockerProjectTagOnSuccess task
   }
   ```

3. **Test tag on success task not created when additionalTags empty**
   ```groovy
   def "dockerProject with empty additionalTags skips tag on success task"() {
       // Configure onSuccess { additionalTags = [] }
       // Verify no dockerProjectTagOnSuccess task
   }
   ```

#### Step C.3: Add dockerWorkflows DSL Tests

1. **Test workflow tag on success task created**
   ```groovy
   def "dockerWorkflows with onTestSuccess additionalTags creates tag task"() {
       // Configure pipeline with onTestSuccess { additionalTags = ['verified'] }
       // Verify workflow{PipelineName}TagOnSuccess task is registered
   }
   ```

2. **Test workflow tag on success task uses onSuccess fallback**
   ```groovy
   def "dockerWorkflows falls back to onSuccess when onTestSuccess empty"() {
       // Configure pipeline with onSuccess { additionalTags = ['stable'] }
       // No onTestSuccess additionalTags
       // Verify tag task uses onSuccess additionalTags
   }
   ```

#### Step C.4: Add Task Property Configuration Tests

1. **Test imageName property wiring**
   ```groovy
   def "tag on success task imageName configured from image spec"() {
       // Configure dockerProject with imageName = 'my-app'
       // Verify task.imageName.get() == 'my-app'
   }
   ```

2. **Test additionalTags property wiring**
   ```groovy
   def "tag on success task additionalTags configured from onSuccess"() {
       // Configure onSuccess { additionalTags = ['tested', 'verified'] }
       // Verify task.additionalTags.get() == ['tested', 'verified']
   }
   ```

3. **Test sourceImageRef property wiring for sourceRef mode**
   ```groovy
   def "tag on success task sourceImageRef configured for sourceRef image"() {
       // Configure dockerProject with sourceRef image
       // Verify task.sourceImageRef is set to effective source ref
   }
   ```

4. **Test testResultFile property wiring**
   ```groovy
   def "tag on success task testResultFile configured to state directory"() {
       // Configure dockerProject
       // Verify task.testResultFile points to correct location
   }
   ```

#### Step C.5: Add Task Dependency Tests

1. **Test tag on success depends on test task**
   ```groovy
   def "tag on success task depends on test task"() {
       // Configure dockerProject with test block
       // Verify dependsOn relationship
   }
   ```

2. **Test tag on success must run after test**
   ```groovy
   def "tag on success task mustRunAfter test task"() {
       // Verify mustRunAfter relationship prevents reordering
   }
   ```

3. **Test save task depends on tag on success**
   ```groovy
   def "save task depends on tag on success task"() {
       // Configure onSuccess with additionalTags and save
       // Verify save depends on tag on success
   }
   ```

4. **Test publish depends on tag on success when no save**
   ```groovy
   def "publish task depends on tag on success when no save configured"() {
       // Configure onSuccess with additionalTags and publish (no save)
       // Verify publish depends on tag on success
   }
   ```

#### Step C.6: Add onlyIf Predicate Tests

1. **Test task skipped when test result file missing**
   ```groovy
   def "tag on success skipped when test result file not found"() {
       // Configure task but don't create test result file
       // Verify task is skipped with appropriate message
   }
   ```

2. **Test task skipped when tests failed**
   ```groovy
   def "tag on success skipped when tests failed"() {
       // Create test result file with success = false
       // Verify task is skipped
   }
   ```

3. **Test task executes when tests passed**
   ```groovy
   def "tag on success executes when tests passed"() {
       // Create test result file with success = true
       // Verify task executes (or would execute - may need mock)
   }
   ```

#### Step C.7: Add Edge Case Tests

1. **Test with multiple images (primary image gets tags)**
   ```groovy
   def "tag on success only tags primary image in multi-image scenario"() {
       // Configure dockerProject with multiple images
       // Verify only primary image receives additional tags
   }
   ```

2. **Test with tags containing colons (full image refs)**
   ```groovy
   def "tag on success handles tags containing colons"() {
       // Configure additionalTags = ['registry.io/org/app:tested']
       // Verify tag is used as-is without modification
   }
   ```

3. **Test with empty imageName**
   ```groovy
   def "tag on success throws when imageName empty"() {
       // Configure task with empty imageName
       // Verify IllegalStateException thrown
   }
   ```

---

## Implementation Order

1. **Part A: CleanupTask Unit Tests** (prerequisite for understanding current behavior)
2. **Part C: TagOnSuccessTask Functional Tests** (simpler, fewer dependencies)
3. **Part B: CleanupTask Functional Tests** (more complex, may need service mocking)

---

## Acceptance Criteria

1. All new unit tests pass
2. All new functional tests pass
3. No regressions in existing tests
4. Code coverage for CleanupTask reaches 100% (excluding documented gaps)
5. Code coverage for TagOnSuccessTask functional behavior verified
6. Tests follow project conventions:
   - Spock framework
   - Clear test names describing behavior
   - Proper setup/cleanup of test resources

---

## Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `plugin/src/test/groovy/.../CleanupTaskTest.groovy` | Modify | Add missing unit test cases |
| `plugin/src/functionalTest/groovy/.../CleanupTaskFunctionalTest.groovy` | Create | New functional test class |
| `plugin/src/functionalTest/groovy/.../TagOnSuccessTaskFunctionalTest.groovy` | Create | New functional test class |

---

## Notes

- Functional tests use Gradle TestKit and do NOT call actual Docker commands
- Functional tests verify task registration, configuration, and dependency wiring
- Integration tests (in `plugin-integration-test/`) already provide indirect coverage of actual Docker operations
- Per CLAUDE.md: "Focus on Gradle integration, not Docker integration" for functional tests
