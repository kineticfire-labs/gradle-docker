# Unit Test Coverage Plan: `com.kineticfire.gradle.docker.task`

## Overview

This document provides a comprehensive plan to achieve 100% unit test coverage for the
`com.kineticfire.gradle.docker.task` package.

### Current Status
- **Package-level Branch Coverage**: 89.4%
- **Package-level Instruction Coverage**: 81%
- **Target**: 100% branch and instruction coverage

### Total Estimated Work
- **New Test Cases Required**: ~35-40 tests across 6 task classes
- **Priority Order**: DockerPublishTask → DockerTagTask → DockerSaveTask → DockerBuildTask → ComposeUpTask →
  ComposeDownTask

---

## 1. DockerBuildTask

### Current Coverage
- **Instruction Coverage**: 95%
- **Branch Coverage**: 84%
- **Priority**: Medium

### Missing Coverage Analysis

#### Uncovered Lines
- **Lines 126-127**: Defensive null check for `dockerService`
  ```groovy
  if (!service) {
      throw new IllegalStateException("dockerService must be provided")
  }
  ```

- **Lines 131-132**: Defensive null check for `contextFile || dockerfileFile`
  ```groovy
  if (!contextFile || !dockerfileFile) {
      throw new IllegalStateException("contextPath and dockerfile must be provided")
  }
  ```

- **Lines 150-151**: Defensive null check for `imageIdRegularFile`
  ```groovy
  if (!imageIdRegularFile) {
      throw new IllegalStateException("imageId must be provided")
  }
  ```

### Required Test Cases (~3-5 new tests)

1. **Test dockerService.get() returns null after initial set**
   - Setup: Set dockerService, then mock to return null on .get()
   - Expected: IllegalStateException with message "dockerService must be provided"
   - Lines covered: 126-127

2. **Test contextPath.get().asFile returns null**
   - Setup: Mock contextPath.get() to return object with asFile == null
   - Expected: IllegalStateException with message "contextPath and dockerfile must be provided"
   - Lines covered: 131-132 (contextFile branch)

3. **Test dockerfile.get().asFile returns null**
   - Setup: Mock dockerfile.get() to return object with asFile == null
   - Expected: IllegalStateException with message "contextPath and dockerfile must be provided"
   - Lines covered: 131-132 (dockerfileFile branch)

4. **Test imageId.get() returns null**
   - Setup: Mock imageId.get() to return null after successful build
   - Expected: IllegalStateException with message "imageId must be provided"
   - Lines covered: 150-151

5. **Test both contextFile and dockerfileFile null**
   - Setup: Mock both to return null
   - Expected: IllegalStateException
   - Lines covered: 131-132 (both branches)

---

## 2. DockerTagTask

### Current Coverage
- **Instruction Coverage**: 95%
- **Branch Coverage**: 78%
- **Priority**: High

### Missing Coverage Analysis

#### Method: `isInSourceRefMode` (Line 133)
- **Current Branch Coverage**: 62% (9 of 24 branches missed)
- **Issue**: Multiple combinations of sourceRef component presence not tested

#### Method: `tagImage` (Line 80)
- **Current Branch Coverage**: 81% (3 of 16 branches missed)
- **Issue**: Edge case branches for empty tags in sourceRef mode

#### Method: `buildImageReferences` (Line 155)
- **Current Branch Coverage**: 88% (4 of 34 branches missed)
- **Issue**: Edge cases for image reference building

#### Method: `pullSourceRefIfNeeded` (Line 213)
- **Current Branch Coverage**: 80% (4 of 20 branches missed)
- **Issue**: pullIfMissing edge cases not fully tested

### Required Test Cases (~8-10 new tests)

#### `isInSourceRefMode` Coverage

1. **Test with sourceRefRepository only (no imageSpec.sourceRef)**
   - Setup: imageSpec with sourceRefRepository.present() == true, sourceRefImageName empty, sourceRef empty
   - Expected: Returns true
   - Lines covered: Line 139-140 (hasRepository branch)

2. **Test with sourceRefImageName only (no imageSpec.sourceRef)**
   - Setup: imageSpec with sourceRefImageName.present() == true, sourceRefRepository empty, sourceRef empty
   - Expected: Returns true
   - Lines covered: Line 140 (hasImageName branch)

3. **Test with both sourceRefRepository and sourceRefImageName**
   - Setup: Both components present
   - Expected: Returns true
   - Lines covered: Line 139-141 (combined branches)

4. **Test with empty sourceRef but empty components**
   - Setup: sourceRef.isPresent() == true but empty string, no components
   - Expected: Returns false
   - Lines covered: Line 135-136 (early exit false case)

5. **Test with sourceRef present but components also present**
   - Setup: Direct sourceRef present, components also present
   - Expected: Returns true (direct sourceRef takes precedence)
   - Lines covered: Line 135-136 (early return true)

#### `tagImage` Coverage

6. **Test sourceRef mode with empty tags list**
   - Setup: isSourceRefMode returns true, tags.getOrElse([]) returns empty list
   - Expected: logger.info called with "No additional tags specified for sourceRef mode, skipping tag operation"
   - Lines covered: Line 102-103

7. **Test build mode with single tag**
   - Setup: Build mode, imageReferences.size() == 1
   - Expected: logger.info called, returns early (no-op)
   - Lines covered: Line 116-119

#### `pullSourceRefIfNeeded` Coverage

8. **Test with imageSpec null or not present**
   - Setup: imageSpec.orNull == null
   - Expected: Returns early without error
   - Lines covered: Line 214-215

9. **Test with pullIfMissing false**
   - Setup: imageSpec.pullIfMissing.getOrElse(false) == false
   - Expected: Returns early, no image pull attempted
   - Lines covered: Line 221

10. **Test with pullIfMissing true but sourceRef empty**
    - Setup: pullIfMissing == true, getEffectiveSourceRef() returns empty string
    - Expected: Returns early, no image pull attempted
    - Lines covered: Line 222-223

11. **Test with pullIfMissing true, sourceRef present, image already exists**
    - Setup: pullIfMissing == true, sourceRef present, service.imageExists returns true
    - Expected: No pullImage call made
    - Lines covered: Line 228

---

## 3. DockerSaveTask

### Current Coverage
- **Instruction Coverage**: 96%
- **Branch Coverage**: 88%
- **Priority**: Medium

### Missing Coverage Analysis

#### Method: `saveImage` (Line 136)
- **Current Branch Coverage**: 70% (6 of 20 branches missed)
- **Uncovered Lines**: 158-159, 164-165

#### Method: `buildPrimaryImageReference` (Line 190)
- **Current Branch Coverage**: 96% (2 of 54 branches missed)
- **Issue**: Edge case for sourceRef component combinations

#### Method: `pullSourceRefIfNeeded` (Line 170)
- **Current Branch Coverage**: 85% (2 of 14 branches missed)
- **Issue**: pullIfMissing edge cases

### Required Test Cases (~5-7 new tests)

#### `saveImage` Coverage

1. **Test outputFile.get() returns null**
   - Setup: Mock outputFile.get() to return null
   - Expected: IllegalStateException with message "outputFile must be provided"
   - Lines covered: 158-159

2. **Test service.saveImage() returns null future**
   - Setup: Mock dockerService.saveImage() to return null
   - Expected: IllegalStateException with message "saveImage future cannot be null"
   - Lines covered: 164-165

#### `buildPrimaryImageReference` Coverage

3. **Test sourceRefRepository with empty registry**
   - Setup: sourceRefRepository present, sourceRefRegistry empty
   - Expected: Returns "repository:tag" format (no registry prefix)
   - Lines covered: Line 213-215 (isEmpty() branch)

4. **Test sourceRefImageName with empty registry and namespace**
   - Setup: sourceRefImageName present, both registry and namespace empty
   - Expected: Returns "imageName:tag" format
   - Lines covered: Line 222-223, 225-226 (empty branches)

5. **Test fallback to build mode when sourceRef components empty**
   - Setup: All sourceRef components empty, use repository in build mode
   - Expected: Returns build mode reference
   - Lines covered: Line 235-251

#### `pullSourceRefIfNeeded` Coverage

6. **Test with pullIfMissing false**
   - Setup: pullIfMissing.getOrElse(false) == false
   - Expected: Returns early, no pull attempted
   - Lines covered: Line 172

7. **Test with pullIfMissing true but effectiveSourceRef empty**
   - Setup: pullIfMissing == true, effectiveSourceRef.getOrElse("") == ""
   - Expected: Returns early, no pull attempted
   - Lines covered: Line 173-174

8. **Test with pullIfMissing true, sourceRef present, image already exists**
   - Setup: pullIfMissing == true, sourceRef present, service.imageExists returns true
   - Expected: No pullImage call made
   - Lines covered: Line 180

---

## 4. DockerPublishTask **[HIGHEST PRIORITY]**

### Current Coverage
- **Instruction Coverage**: 85%
- **Branch Coverage**: 75%
- **Priority**: **CRITICAL**

### Missing Coverage Analysis

#### Method: `validateAuthenticationCredentials` (Line 399)
- **Current Branch Coverage**: 46% (16 of 30 branches missed)
- **CRITICAL GAPS**:
  - **Lines 416-422**: IllegalStateException catch block for username - **NEVER TESTED**
  - **Lines 437-443**: IllegalStateException catch block for password - **NEVER TESTED**
  - **Line 406**: Branch when `authSpec.username.isPresent()` is false
  - **Line 427**: Branch when `authSpec.password.isPresent()` is false

#### Method: `buildSourceImageReference` (Line 276)
- **Current Branch Coverage**: 71% (11 of 38 branches missed)
- **CRITICAL GAPS**:
  - **Lines 279-280**: sourceRef non-empty path - **NEVER TESTED**
  - **Lines 285-286**: effectiveSourceRef non-empty path - **NEVER TESTED**
  - **Lines 294-295**: imageSpec.getEffectiveSourceRef() returning non-empty - **PARTIALLY TESTED**
  - **Line 318**: Registry non-empty branch for repository format
  - **Line 334**: Unreachable fallback return null (may be dead code)

#### Method: `publish` (Line 156)
- **Current Branch Coverage**: 33% (4 of 6 branches missed)
- **CRITICAL GAPS**:
  - **Lines 162-163**: publishSpec.isPresent() && publishSpec.get()?.to path - **NEVER TESTED**
  - **Line 173**: target.auth.isPresent() validation path - **NEVER TESTED**

#### Method: `publishImage` (Line 181)
- **Current Branch Coverage**: 90% (2 of 22 branches missed)
- **GAP**:
  - **Lines 217-219**: targetRefs.empty warning path - **NEVER TESTED**

#### Method: `pullSourceRefIfNeeded` (Line 496)
- **Current Branch Coverage**: 85% (2 of 14 branches missed)

#### Other Issues
- **Line 494**: Unreachable code after switch statement (dead code to investigate)

### Required Test Cases (~15-18 new tests)

#### `validateAuthenticationCredentials` Coverage **[CRITICAL]**

1. **Test username environment variable not set (IllegalStateException)**
   - Setup: Mock authSpec.username.get() to throw IllegalStateException with message containing "environment
     variable"
   - Expected: GradleException with message "Authentication username environment variable is not set for registry..."
   - Lines covered: 416-420

2. **Test username provider not available (IllegalStateException)**
   - Setup: Mock authSpec.username.get() to throw IllegalStateException with message containing "provider"
   - Expected: GradleException with message "Authentication username environment variable is not set for registry..."
   - Lines covered: 416-420

3. **Test password environment variable not set (IllegalStateException)**
   - Setup: Mock authSpec.password.get() to throw IllegalStateException with message containing "environment variable"
   - Expected: GradleException with message "Authentication password/token environment variable is not set for
     registry..."
   - Lines covered: 437-441

4. **Test password provider not available (IllegalStateException)**
   - Setup: Mock authSpec.password.get() to throw IllegalStateException with message containing "provider"
   - Expected: GradleException with message "Authentication password/token environment variable is not set for
     registry..."
   - Lines covered: 437-441

5. **Test IllegalStateException re-thrown when not env var related**
   - Setup: Mock to throw IllegalStateException with message NOT containing "environment variable" or "provider"
   - Expected: Original IllegalStateException re-thrown
   - Lines covered: 422, 443

6. **Test authSpec with username not present**
   - Setup: authSpec.username.isPresent() == false
   - Expected: Validation skips username check
   - Lines covered: 406 (false branch)

7. **Test authSpec with password not present**
   - Setup: authSpec.password.isPresent() == false
   - Expected: Validation skips password check
   - Lines covered: 427 (false branch)

#### `buildSourceImageReference` Coverage **[CRITICAL]**

8. **Test with direct sourceRef set to non-empty value**
   - Setup: task.sourceRef.set("docker.io/myrepo/myimage:latest")
   - Expected: Returns "docker.io/myrepo/myimage:latest"
   - Lines covered: 279-280

9. **Test with effectiveSourceRef set to non-empty value**
   - Setup: task.effectiveSourceRef.set("registry.com/image:tag")
   - Expected: Returns "registry.com/image:tag"
   - Lines covered: 285-286

10. **Test with imageSpec returning non-empty effectiveSourceRef**
    - Setup: Mock imageSpec.get().getEffectiveSourceRef() to return "ghcr.io/org/image:v1"
    - Expected: Returns "ghcr.io/org/image:v1"
    - Lines covered: 294-295

11. **Test with repository format and non-empty registry**
    - Setup: task.repository.set("myorg/myapp"), task.registry.set("registry.example.com"), task.tags.set(["latest"])
    - Expected: Returns "registry.example.com/myorg/myapp:latest"
    - Lines covered: 318-319 (registryValue non-empty branch)

12. **Test unreachable return null at line 334**
    - Attempt: Try to reach this line or document as unreachable dead code
    - If unreachable: Document in `docs/design-docs/testing/unit-test-gaps.md`
    - Lines covered: 334 (if reachable)

#### `publish` Coverage **[CRITICAL]**

13. **Test using publishSpec instead of publishTargets**
    - Setup: Set task.publishSpec with object containing .to property with list of targets
    - Expected: Uses publishSpec.get().to for targets
    - Lines covered: 162-163

14. **Test publish with auth credentials in target**
    - Setup: Create publishTarget with target.auth.isPresent() == true
    - Expected: validateAuthenticationCredentials is called
    - Lines covered: 172-174

#### `publishImage` Coverage

15. **Test publishImage with empty targetRefs**
    - Setup: Mock buildTargetImageReferences to return empty list []
    - Expected: logger.warn called with "No target references for publish target..., skipping"
    - Lines covered: 217-219

#### `pullSourceRefIfNeeded` Coverage

16. **Test with pullIfMissing false**
    - Setup: task.pullIfMissing.set(false)
    - Expected: Returns early, no pull attempted
    - Lines covered: 497 (false branch)

17. **Test with pullIfMissing true but effectiveSourceRef empty**
    - Setup: task.pullIfMissing.set(true), task.effectiveSourceRef.set("")
    - Expected: Returns early, no pull attempted
    - Lines covered: 498-499

18. **Test with pullIfMissing true, sourceRef present, image already exists**
    - Setup: pullIfMissing true, effectiveSourceRef present, service.imageExists returns true
    - Expected: No pullImage call made
    - Lines covered: 505

---

## 5. ComposeUpTask

### Current Coverage
- **Instruction Coverage**: 99%
- **Branch Coverage**: 88%
- **Priority**: Low

### Missing Coverage Analysis

#### Method: `performWaitIfConfigured` (Line 135)
- **Current Branch Coverage**: 87% (2 of 18 branches missed)
- **Issue**: Edge case combinations of wait configurations

#### Groovy Closures
- **`_generateStateFile_closure4`**: 12% instruction coverage
- **`_generateStateFile_closure4._closure6`**: 0% coverage
- **`_composeUp_closure3._closure5`**: 0% coverage

**Note**: These are Groovy-generated closure classes. If they cannot be meaningfully unit tested due to Groovy's code
generation, document in `docs/design-docs/testing/unit-test-gaps.md`.

### Required Test Cases (~2-3 new tests)

1. **Test performWaitIfConfigured with both wait configs empty**
   - Setup: waitForHealthyServices empty, waitForRunningServices empty
   - Expected: Method returns early without waiting
   - Lines covered: Missing branches in lines 137-178

2. **Test performWaitIfConfigured with only waitForHealthy configured**
   - Setup: waitForHealthyServices = ["web"], waitForRunningServices empty
   - Expected: Only healthy wait is performed, running wait is skipped
   - Lines covered: Lines 137-156, skip 159-178

3. **Test performWaitIfConfigured with only waitForRunning configured**
   - Setup: waitForHealthyServices empty, waitForRunningServices = ["db"]
   - Expected: Only running wait is performed, healthy wait is skipped
   - Lines covered: Skip 137-156, execute 159-178

4. **Document Groovy closure coverage**
   - Action: If closures cannot be meaningfully unit tested, document in
     `docs/design-docs/testing/unit-test-gaps.md`
   - Rationale: Groovy-generated code may not be testable via standard unit tests

---

## 6. ComposeDownTask

### Current Coverage
- **Instruction Coverage**: 97%
- **Branch Coverage**: 95%
- **Priority**: Low

### Missing Coverage Analysis

Very minimal gaps - already excellent coverage. Likely 1-2 defensive checks or edge cases.

### Required Test Cases (~1-2 new tests)

1. **Review existing ComposeDownTaskTest**
   - Action: Analyze JaCoCo HTML report for specific uncovered lines
   - Identify: The specific 1-2 branches that are uncovered

2. **Add edge case test for identified gap**
   - Setup: Based on gap analysis
   - Expected: Cover the missing branch
   - Lines covered: TBD based on analysis

3. **Verify captureLogsIfConfigured edge cases**
   - Possible gaps: dockerOrchExt not found, stackSpec not found, logs not present
   - Test these edge cases if not already covered

---

## Implementation Strategy

### Phase 1: High-Impact, Low-Complexity (Start Here)

**Estimated Time**: 2-3 hours

1. **DockerPublishTask authentication tests** (Tests 1-7)
   - Add tests for environment variable not set scenarios
   - Add tests for missing username/password branches
   - **Impact**: +8% branch coverage, critical untested paths

2. **DockerPublishTask sourceRef tests** (Tests 8-12)
   - Add tests for sourceRef/effectiveSourceRef/imageSpec paths
   - **Impact**: +5% branch coverage, critical untested paths

3. **DockerPublishTask publish/publishImage tests** (Tests 13-15)
   - Add tests for publishSpec usage and empty targetRefs
   - **Impact**: +3% branch coverage

### Phase 2: Medium-Impact (Next)

**Estimated Time**: 2-3 hours

4. **DockerTagTask edge cases** (Tests 1-7)
   - Add comprehensive `isInSourceRefMode` tests
   - Add `tagImage` edge case tests
   - **Impact**: +12% branch coverage

5. **DockerTagTask pullIfMissing tests** (Tests 8-11)
   - Complete pullSourceRefIfNeeded coverage
   - **Impact**: +5% branch coverage

6. **DockerSaveTask edge cases** (Tests 1-8)
   - Add null checks and edge case tests
   - **Impact**: +8% branch coverage

### Phase 3: Polish (Final)

**Estimated Time**: 1-2 hours

7. **DockerBuildTask null checks** (Tests 1-5)
   - Add defensive null check tests
   - **Impact**: +11% branch coverage

8. **ComposeUpTask edge cases** (Tests 1-3)
   - Add missing wait configuration tests
   - **Impact**: +10% branch coverage

9. **ComposeDownTask edge cases** (Tests 1-3)
   - Review and add final edge case tests
   - **Impact**: +5% branch coverage

10. **Document unavoidable gaps**
    - Update `docs/design-docs/testing/unit-test-gaps.md` for any Groovy-generated code that cannot be meaningfully
      tested
    - Provide justification for each documented gap

---

## Testing Patterns to Use

### 1. Null/Defensive Checks
```groovy
def "method fails when service is null"() {
    given:
    task.dockerService.set(null)
    // ... other setup

    when:
    task.methodUnderTest()

    then:
    def ex = thrown(IllegalStateException)
    ex.message.contains("service must be provided")
}
```

### 2. Branch Coverage for Conditionals
```groovy
def "method handles empty value"() {
    given:
    task.property.set("")

    when:
    def result = task.methodUnderTest()

    then:
    result == expectedValue
}

def "method handles non-empty value"() {
    given:
    task.property.set("value")

    when:
    def result = task.methodUnderTest()

    then:
    result == expectedValue
}
```

### 3. Exception Handling in Catch Blocks
```groovy
def "method catches and re-throws IllegalStateException with env var message"() {
    given:
    def mockAuthSpec = Mock(AuthSpec)
    mockAuthSpec.username.isPresent() >> true
    mockAuthSpec.username.get() >> { throw new IllegalStateException("environment variable not set") }

    when:
    task.validateAuthenticationCredentials(target, mockAuthSpec)

    then:
    def ex = thrown(GradleException)
    ex.message.contains("environment variable is not set")
}
```

### 4. Edge Cases with Empty Collections
```groovy
def "method handles empty list"() {
    given:
    task.tags.set([])

    when:
    def result = task.methodUnderTest()

    then:
    result == []  // or expected behavior
}
```

### 5. Mocking for Branch Coverage
```groovy
def "method uses fallback when primary value is empty"() {
    given:
    task.sourceRef.set("")
    task.effectiveSourceRef.set("fallback-value")

    when:
    def result = task.buildSourceImageReference()

    then:
    result == "fallback-value"
}
```

---

## Acceptance Criteria

### Coverage Metrics
- [ ] JaCoCo reports **100% branch coverage** for all 6 task classes
- [ ] JaCoCo reports **100% instruction coverage** for all 6 task classes
- [ ] Package-level coverage: **100% branches**, **100% instructions**

### Code Quality
- [ ] All new tests follow existing Spock testing patterns
- [ ] Tests remain isolated (no network, filesystem, or external dependencies)
- [ ] Tests use appropriate mocking for service dependencies
- [ ] Test names clearly describe the scenario being tested

### Documentation
- [ ] Any unavoidable gaps explicitly documented in `docs/design-docs/testing/unit-test-gaps.md`
- [ ] Each documented gap includes:
  - Line numbers affected
  - Reason why 100% coverage is not achievable
  - Justification for the gap
  - Alternative verification approach (if applicable)

### Verification
- [ ] Run `./gradlew clean test` from `plugin/` directory
- [ ] All tests pass
- [ ] JaCoCo HTML report shows 100% coverage for `com.kineticfire.gradle.docker.task` package
- [ ] No warnings from compilation or test execution

---

## Progress Tracking

### DockerPublishTask (15-18 tests)
- [ ] Test 1: Username env var not set
- [ ] Test 2: Username provider not available
- [ ] Test 3: Password env var not set
- [ ] Test 4: Password provider not available
- [ ] Test 5: IllegalStateException re-thrown (non-env)
- [ ] Test 6: Username not present
- [ ] Test 7: Password not present
- [ ] Test 8: Direct sourceRef non-empty
- [ ] Test 9: EffectiveSourceRef non-empty
- [ ] Test 10: ImageSpec effectiveSourceRef
- [ ] Test 11: Repository with non-empty registry
- [ ] Test 12: Unreachable return null investigation
- [ ] Test 13: Using publishSpec
- [ ] Test 14: Auth in target
- [ ] Test 15: Empty targetRefs
- [ ] Test 16: PullIfMissing false
- [ ] Test 17: EffectiveSourceRef empty
- [ ] Test 18: Image already exists

### DockerTagTask (8-10 tests)
- [ ] Test 1: isInSourceRefMode with sourceRefRepository only
- [ ] Test 2: isInSourceRefMode with sourceRefImageName only
- [ ] Test 3: isInSourceRefMode with both components
- [ ] Test 4: isInSourceRefMode with empty components
- [ ] Test 5: isInSourceRefMode with direct sourceRef precedence
- [ ] Test 6: tagImage sourceRef mode empty tags
- [ ] Test 7: tagImage build mode single tag
- [ ] Test 8: pullSourceRefIfNeeded imageSpec null
- [ ] Test 9: pullSourceRefIfNeeded pullIfMissing false
- [ ] Test 10: pullSourceRefIfNeeded sourceRef empty
- [ ] Test 11: pullSourceRefIfNeeded image exists

### DockerSaveTask (5-7 tests)
- [ ] Test 1: outputFile.get() null
- [ ] Test 2: saveImage future null
- [ ] Test 3: sourceRefRepository empty registry
- [ ] Test 4: sourceRefImageName empty registry/namespace
- [ ] Test 5: Fallback to build mode
- [ ] Test 6: pullIfMissing false
- [ ] Test 7: effectiveSourceRef empty
- [ ] Test 8: Image exists

### DockerBuildTask (3-5 tests)
- [ ] Test 1: dockerService.get() null
- [ ] Test 2: contextPath.asFile null
- [ ] Test 3: dockerfile.asFile null
- [ ] Test 4: imageId.get() null
- [ ] Test 5: Both contextFile and dockerfileFile null

### ComposeUpTask (2-3 tests)
- [ ] Test 1: Both wait configs empty
- [ ] Test 2: Only waitForHealthy configured
- [ ] Test 3: Only waitForRunning configured
- [ ] Documentation: Groovy closure gap documentation

### ComposeDownTask (1-2 tests)
- [ ] Test 1: TBD based on gap analysis
- [ ] Test 2: captureLogsIfConfigured edge case

---

## Notes and Considerations

### Potential Dead Code
- **DockerPublishTask line 334**: `return null` after switch statement may be unreachable. Investigate and either:
  - Find a way to reach it, or
  - Document as dead code and consider refactoring

- **DockerPublishTask line 494**: Code after switch statement may be unreachable. Similar investigation needed.

### Groovy-Generated Code
- Closure classes like `_generateStateFile_closure4` are generated by Groovy
- These may not be directly unit-testable
- If they cannot be tested, document in unit test gaps with rationale

### Environment Variable Testing
- Use Spock mocking to simulate missing/empty environment variables
- Mock `Provider.get()` to throw `IllegalStateException` with appropriate message
- Test both "environment variable" and "provider" message variants

### Property-Based Testing
- Consider property-based testing for combinatorial cases (e.g., `isInSourceRefMode`)
- Could reduce number of explicit test cases while increasing coverage

---

## Success Metrics

### Before
- Package instruction coverage: 81%
- Package branch coverage: 89.4%
- Uncovered lines: 556
- Uncovered branches: 84

### After (Target)
- Package instruction coverage: 100%
- Package branch coverage: 100%
- Uncovered lines: 0 (or explicitly documented)
- Uncovered branches: 0 (or explicitly documented)

### Quality Indicators
- No decrease in test execution speed
- All tests remain isolated and fast
- Test code follows DRY principles
- Clear test names that serve as documentation
