# Plan: Remove "SUITE" Lifecycle Terminology from Entire Project

**Status:** 🔴 Not Started
**Created:** 2025-11-23
**Priority:** CRITICAL (Clean Up Before Publication)
**Estimated Effort:** 4-5 hours

---

## Context

The project incorrectly uses "suite lifecycle" terminology throughout code and documentation. This is **incorrect
terminology** - there are only TWO lifecycles:

1. **CLASS lifecycle** - Containers start once for all tests in a class
2. **METHOD lifecycle** - Containers restart for each test method

**NO "suite lifecycle" exists.** What was called "suite lifecycle" is actually **CLASS lifecycle managed via Gradle
tasks** instead of test framework extensions.

**IMPORTANT:** This plugin has NOT been published yet, so there are NO external users. This is a cleanup task before
initial publication. No backward compatibility or migration guide is needed.

**Related Documents:**
- Recently completed: `update-docker-orch-readme.md` (removed suite lifecycle from integration test README)
- Active documentation: `docs/usage/usage-docker-orch.md`, `docs/usage/spock-junit-test-extensions.md`
- Plugin code: `TestIntegrationExtension.groovy`, `GradleDockerPlugin.groovy`, `ComposeUpTask.groovy`
- Integration test documentation: `plugin-integration-test/dockerOrch/verification/logs-capture/README.md`

---

## Complete Audit Results

### CRITICAL: Active Plugin Code (API Breaking Changes Required)

**1. plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtension.groovy**
- Line 61: JavaDoc mentions "suite" as valid lifecycle parameter
- Line 97-98: `case 'suite':` - accepts "suite" as valid lifecycle value, calls `configureSuiteLifecycle()`
- Line 108: Error message lists 'suite' as valid option
- Line 127: `private void configureSuiteLifecycle()` method exists

**Impact:** This is part of the public API. Users can currently pass `lifecycle: 'suite'` to `usesCompose()`.

**2. plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy**
- Line 801: `def lifecycle = args.lifecycle ?: 'suite'` - **defaults to 'suite'**

**Impact:** Default lifecycle is currently 'suite'. This affects all users who don't specify lifecycle explicitly.

**3. plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy**
- Line 194: Code example shows `lifecycle: "suite"`

**Impact:** Documentation in code shows incorrect pattern to users.

---

### HIGH PRIORITY: Active User-Facing Documentation

**1. docs/usage/usage-docker-orch.md** (5 references)
- Line 134: "- Only supports suite lifecycle (not class or method)"
- Line 144: "- **SUITE Lifecycle** - Containers start once for entire test suite (fastest, only via Gradle tasks)"
- Line 1194: "- You need containers to run for the entire test suite (suite lifecycle only)"
- Line 1198: "**Note:** Gradle tasks only support **suite lifecycle**. For CLASS or METHOD lifecycles, use test
  framework extensions."
- Line 1591: "- **Gradle Tasks:** For suite lifecycle, CI/CD pipelines, or custom orchestration needs"

**Impact:** Main usage guide teaches incorrect terminology to all users.

**2. docs/usage/spock-junit-test-extensions.md** (1 reference)
- Line 207: "- ⚠️ Only supports suite lifecycle"

**Impact:** Test extensions guide teaches incorrect terminology.

---

### HIGH PRIORITY: Active Functional Tests (4 files, 7+ references)

**1. plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ValidationMessagesFunctionalTest.groovy**
- Line 281: `usesCompose stack: 'invalidStackName', lifecycle: 'suite'`

**2. plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/PluginIntegrationFunctionalTest.groovy**
- Line 101: `usesCompose stack: 'testStack', lifecycle: 'suite'`

**3. plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ConfigurationCacheFunctionalTest.groovy**
- Line 894: `usesCompose(stack: 'testDb', lifecycle: 'suite')`

**4. plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TestExtensionFunctionalTest.groovy**
- Lines 64, 190, 200-210: Multiple "suite" references

**Impact:** Tests validate incorrect API behavior. Must be updated to test correct behavior.

---

### MEDIUM PRIORITY: Integration Test Documentation

**1. plugin-integration-test/dockerOrch/verification/logs-capture/README.md** (2 references)
- Line 4: `**Lifecycle**: SUITE (composeUp before tests, composeDown after tests)`
- Line 380: `lifecycle = 'SUITE'` in code example

**Impact:** Integration test documentation that demonstrates plugin usage. Should use correct terminology.

**Verification Result:** ✅ No integration test build.gradle files use `lifecycle: 'suite'`. Only documentation needs
correction.

---

### LOW PRIORITY: Historical Archived Documents (Reference Only)

**1. docs/design-docs/done/readme-dockerorch-examples-cleanup.md** (multiple references)
- Lines 150, 154, 158, 162: "**Lifecycle**: suite"
- Lines 185-187: Lifecycle pattern descriptions
- Lines 212, 223, 228, 235, 241, 247: "suite" in tables

**2. docs/design-docs/done/increase-functional-test-coverage.md**
- Line 167: `usesCompose stack: 'testStack', lifecycle: 'suite'`

**3. docs/design-docs/done/autowire-test-task-dependencies.md**
- Line 91: "suite lifecycle (one set of containers for all tests, managed by Gradle tasks)"

**Impact:** These are archived/completed work documents. Low priority but should be corrected for historical accuracy.

---

## Implementation Approach

**APPROVED APPROACH:** Remove "suite" Completely (No Backward Compatibility)

**Reasoning:**
1. **Plugin NOT yet published** - There are NO external users to impact
2. **No migration guide needed** - This is pre-publication cleanup
3. **Clean, correct API from day one** - No technical debt or deprecated code paths
4. **Correct terminology from start** - Users learn the right patterns immediately

**Changes Required:**
1. Remove `case 'suite':` from TestIntegrationExtension.groovy
2. Remove `configureSuiteLifecycle()` method (it's empty anyway)
3. Change default from `'suite'` to `'class'` in GradleDockerPlugin.groovy
4. Update error messages to only mention 'class' and 'method'
5. Update all documentation to remove "suite lifecycle"
6. Update all functional tests to use `lifecycle: 'class'`
7. Update integration test documentation to use correct terminology

---

## Implementation Plan

### Phase 1: Update Plugin Code (CRITICAL - API Changes)

**Task 1.1: Remove "suite" lifecycle from TestIntegrationExtension**

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtension.groovy`

**Changes:**
1. Line 61 (JavaDoc): Remove "suite" from parameter description
   ```groovy
   // OLD:
   * @param args Map containing 'stack' (String, required) and 'lifecycle' (String, optional: 'class', 'method',
   'suite'; default 'suite')

   // NEW:
   * @param args Map containing 'stack' (String, required) and 'lifecycle' (String, optional: 'class', 'method';
   default 'class')
   ```

2. Lines 97-99: Remove `case 'suite':` entirely
   ```groovy
   // DELETE THESE LINES:
   case 'suite':
       configureSuiteLifecycle()
       break
   ```

3. Line 108: Update error message to only mention 'class' and 'method'
   ```groovy
   // OLD:
   throw new IllegalArgumentException("Invalid lifecycle '${lifecycle}'. Must be 'class', 'method', or 'suite'.")

   // NEW:
   throw new IllegalArgumentException("Invalid lifecycle '${lifecycle}'. Must be 'class' or 'method'. " +
       "Note: What was previously called 'suite' lifecycle is now correctly called 'class' lifecycle " +
       "when using Gradle task orchestration.")
   ```

4. Lines 127-135: **Rename method** from `configureSuiteLifecycle()` to match reality
   ```groovy
   // OLD:
   private void configureSuiteLifecycle() {
       // No system properties needed for suite lifecycle
       // Compose up/down handled by Gradle task dependencies
   }

   // NEW: Delete this method entirely (it's empty anyway)
   // The configureClassLifecycle() method already handles this case correctly
   ```

**Rationale:**
- Removes incorrect API parameter
- Forces users to use correct terminology
- Eliminates dead code path

---

**Task 1.2: Change default lifecycle to 'class'**

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`

**Change:**
Line 801: Change default from 'suite' to 'class'
```groovy
// OLD:
def lifecycle = args.lifecycle ?: 'suite'

// NEW:
def lifecycle = args.lifecycle ?: 'class'
```

**Rationale:**
- 'class' is more common use case than 'method'
- Maintains backward compatibility for users who didn't specify lifecycle (behavior is same, just correct name)

---

**Task 1.3: Update code example in ComposeUpTask**

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy`

**Change:**
Line 194: Update example to show correct lifecycle parameter
```groovy
// OLD:
// tasks.named('integrationTest') { usesCompose(stack: "myStack", lifecycle: "suite") }

// NEW:
// tasks.named('integrationTest') { usesCompose(stack: "myStack", lifecycle: "class") }
```

**Rationale:** Code examples should teach correct patterns.

---

### Phase 2: Update Functional Tests

**Task 2.1: Update ValidationMessagesFunctionalTest.groovy**

**File:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ValidationMessagesFunctionalTest.groovy`

**Change:**
Line 281: `usesCompose stack: 'invalidStackName', lifecycle: 'suite'`
→ `usesCompose stack: 'invalidStackName', lifecycle: 'class'`

---

**Task 2.2: Update PluginIntegrationFunctionalTest.groovy**

**File:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/PluginIntegrationFunctionalTest.groovy`

**Change:**
Line 101: `usesCompose stack: 'testStack', lifecycle: 'suite'`
→ `usesCompose stack: 'testStack', lifecycle: 'class'`

---

**Task 2.3: Update ConfigurationCacheFunctionalTest.groovy**

**File:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ConfigurationCacheFunctionalTest.groovy`

**Change:**
Line 894: `usesCompose(stack: 'testDb', lifecycle: 'suite')`
→ `usesCompose(stack: 'testDb', lifecycle: 'class')`

---

**Task 2.4: Update TestExtensionFunctionalTest.groovy**

**File:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TestExtensionFunctionalTest.groovy`

**Changes:**
- Line 64: `lifecycle: 'suite'` → `lifecycle: 'class'`
- Line 190: `lifecycle: 'suite'` → `lifecycle: 'class'`
- Lines 200-210: Update all "suite" references to "class"

---

**Task 2.5: Add negative test for rejected "suite" parameter**

**File:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ValidationMessagesFunctionalTest.groovy`

**Add new test:**
```groovy
def "usesCompose rejects invalid 'suite' lifecycle parameter"() {
    given:
    buildFile << """
        tasks.named('integrationTest') {
            usesCompose stack: 'testStack', lifecycle: 'suite'
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withArguments('integrationTest')
        .withPluginClasspath()
        .buildAndFail()

    then:
    result.output.contains("Invalid lifecycle 'suite'")
    result.output.contains("Must be 'class' or 'method'")
}
```

**Rationale:** Verify that incorrect parameter is properly rejected with clear error message.

---

### Phase 3: Update User-Facing Documentation

**Task 3.1: Update usage-docker-orch.md**

**File:** `docs/usage/usage-docker-orch.md`

**Changes Required:**

1. **Line 134:** Remove reference to suite lifecycle
   ```markdown
   <!-- OLD: -->
   - Only supports suite lifecycle (not class or method)

   <!-- NEW: -->
   - Only supports CLASS lifecycle (containers managed by Gradle tasks)
   - For METHOD lifecycle, use test framework extensions
   ```

2. **Line 144:** Remove SUITE lifecycle section entirely
   ```markdown
   <!-- DELETE THIS SECTION: -->
   - **SUITE Lifecycle** - Containers start once for entire test suite (fastest, only via Gradle tasks)

   <!-- REPLACE WITH: -->
   (Already covered in CLASS lifecycle section - Gradle tasks are just an alternative orchestration approach)
   ```

3. **Line 1194:** Correct terminology
   ```markdown
   <!-- OLD: -->
   - You need containers to run for the entire test suite (suite lifecycle only)

   <!-- NEW: -->
   - You need CLASS lifecycle containers managed via Gradle tasks (not test framework extensions)
   ```

4. **Line 1198:** Clarify Gradle tasks use CLASS lifecycle
   ```markdown
   <!-- OLD: -->
   **Note:** Gradle tasks only support **suite lifecycle**. For CLASS or METHOD lifecycles, use test framework
   extensions.

   <!-- NEW: -->
   **Note:** Gradle tasks provide CLASS lifecycle orchestration (manual `dependsOn` / `finalizedBy` wiring).
   For automatic CLASS or METHOD lifecycle management, use test framework extensions.
   ```

5. **Line 1591:** Correct terminology
   ```markdown
   <!-- OLD: -->
   - **Gradle Tasks:** For suite lifecycle, CI/CD pipelines, or custom orchestration needs

   <!-- NEW: -->
   - **Gradle Tasks:** For CLASS lifecycle with manual orchestration, CI/CD pipelines, or custom orchestration needs
   ```

**Rationale:** Main usage guide must teach correct terminology.

---

**Task 3.2: Update spock-junit-test-extensions.md**

**File:** `docs/usage/spock-junit-test-extensions.md`

**Change:**
Line 207: Correct description of Gradle task limitation
```markdown
<!-- OLD: -->
- ⚠️ Only supports suite lifecycle

<!-- NEW: -->
- ⚠️ Only supports CLASS lifecycle (manual task orchestration, not automatic extension-based)
```

**Rationale:** Accurately describe what Gradle tasks support.

---

### Phase 4: Update Integration Test Documentation

**Task 4.1: Update logs-capture README**

**File:** `plugin-integration-test/dockerOrch/verification/logs-capture/README.md`

**Changes:**
1. Line 4: Update lifecycle description
   ```markdown
   <!-- OLD: -->
   **Lifecycle**: SUITE (composeUp before tests, composeDown after tests)

   <!-- NEW: -->
   **Lifecycle**: CLASS (composeUp before tests, composeDown after tests, managed by Gradle tasks)
   ```

2. Line 380: Update code example
   ```groovy
   <!-- OLD: -->
   lifecycle = 'SUITE'

   <!-- NEW: -->
   // Lifecycle managed by Gradle tasks (CLASS lifecycle via composeUp/composeDown dependencies)
   // No lifecycle parameter needed in dockerOrch DSL when using Gradle task orchestration
   ```

**Rationale:** Integration test documentation should demonstrate correct patterns.

---

### Phase 5: Update Historical Documents (Low Priority)

**Task 5.1: Update readme-dockerorch-examples-cleanup.md**

**File:** `docs/design-docs/done/readme-dockerorch-examples-cleanup.md`

**Changes:**
- Lines 150, 154, 158, 162: "**Lifecycle**: suite" → "**Lifecycle**: class"
- Lines 185-187: Update lifecycle pattern descriptions
- Lines 212, 223, 228, 235, 241, 247: "suite" → "class"

**Rationale:** Historical accuracy in archived documents.

---

**Task 5.2: Update increase-functional-test-coverage.md**

**File:** `docs/design-docs/done/increase-functional-test-coverage.md`

**Change:**
Line 167: `usesCompose stack: 'testStack', lifecycle: 'suite'`
→ `usesCompose stack: 'testStack', lifecycle: 'class'`

---

**Task 5.3: Update autowire-test-task-dependencies.md**

**File:** `docs/design-docs/done/autowire-test-task-dependencies.md`

**Change:**
Line 91: Correct terminology
```markdown
<!-- OLD: -->
suite lifecycle (one set of containers for all tests, managed by Gradle tasks)

<!-- NEW: -->
CLASS lifecycle managed via Gradle tasks (one set of containers for all tests, manual orchestration)
```

---

## Testing and Validation

### After Implementation:

1. **Unit Tests:**
   - [ ] All existing unit tests pass
   - [ ] No unit test changes required (unit tests mock the extension)

2. **Functional Tests:**
   - [ ] All functional tests pass with new 'class' lifecycle parameter
   - [ ] New negative test passes (rejects 'suite' parameter with helpful error)
   - [ ] `./gradlew clean functionalTest` from `plugin/` succeeds

3. **Integration Tests:**
   - [ ] All integration tests pass (none use usesCompose with lifecycle parameter)
   - [ ] `./gradlew cleanAll integrationTest` from `plugin-integration-test/` succeeds

4. **Documentation Validation:**
   - [ ] No references to "suite lifecycle" remain in active documentation
   - [ ] All lifecycle references use only "class" or "method"
   - [ ] Cross-reference links still work

5. **Terminology Verification:**
   - [ ] Search entire project for "suite" returns only historical documents and compose file references
   - [ ] No API code accepts "suite" as valid lifecycle parameter
   - [ ] Error messages guide users to correct terminology

6. **Build Validation:**
   - [ ] Plugin builds successfully: `./gradlew -Pplugin_version=X.X.X build`
   - [ ] No compilation warnings related to lifecycle terminology
   - [ ] publishToMavenLocal succeeds

---

## Success Criteria

- [ ] `case 'suite':` removed from TestIntegrationExtension.groovy
- [ ] Default lifecycle changed from 'suite' to 'class' in GradleDockerPlugin.groovy
- [ ] All functional tests updated to use `lifecycle: 'class'`
- [ ] New negative test added to verify 'suite' is rejected
- [ ] All 5 references in usage-docker-orch.md corrected
- [ ] 1 reference in spock-junit-test-extensions.md corrected
- [ ] All 3 historical documents updated for accuracy
- [ ] Error messages guide users: "suite is now called class"
- [ ] All tests pass (unit, functional, integration)
- [ ] No "suite lifecycle" references remain in active code or documentation
- [ ] Project-wide search for "suite" returns only expected results (compose files, historical docs)

---

## No User Communication Required

**Reasoning:** Plugin has not been published yet, so there are no external users. This is internal cleanup before
initial publication.

**No CHANGELOG entry needed** for this change since it's pre-publication.

---

## Non-Goals (Explicitly Out of Scope)

- Changing behavior of lifecycle patterns (only terminology changes)
- Modifying how Gradle tasks orchestrate containers (behavior unchanged)
- Changing test framework extensions (behavior unchanged)
- Modifying integration test structure or organization
- Adding new lifecycle types

---

## Estimated Time Breakdown

| Task | Estimated Time |
|------|----------------|
| Phase 1: Update plugin code (3 files) | 1 hour |
| Phase 2: Update functional tests (4 files + new test) | 1.5 hours |
| Phase 3: Update user documentation (2 files) | 1 hour |
| Phase 4: Update integration test documentation (1 file) | 20 minutes |
| Phase 5: Update historical documents (3 files) | 30 minutes |
| Testing and validation | 1 hour |

**Total Estimated Time:** 5 hours (half development day)

---

## Dependencies and Prerequisites

- No code changes required outside plugin subproject
- No build system changes required
- Plugin code, functional tests, and documentation edits only
- Must verify all tests pass after changes
- Must verify integration tests still pass (they don't use lifecycle parameter in build.gradle)

---

## Implementation Notes

- This is **pre-publication cleanup** - not a breaking change (no external users yet)
- All behavior remains identical, only parameter name and documentation changes
- Error messages clearly state only 'class' and 'method' are valid
- Default lifecycle changes from 'suite' to 'class' (same behavior, correct terminology)

---

## Related Work

- Completed: `update-docker-orch-readme.md` - Removed suite lifecycle from integration test README
- Completed: 5-phase usage documentation consistency plan
- This task: Complete the terminology correction project-wide

---

## Review and Approval

**Plan Status:** Ready for Review
**Approved By:** [To be filled]
**Approval Date:** [To be filled]

**Implementation Notes:**
- Breaking change requires user communication via CHANGELOG
- All phases should be completed together in single PR
- Validate all tests pass before merging
- Update version number to reflect breaking change
