# Implementation Plan: Workflow Support Lifecycle - Part 2 (Remaining Items)

> **Note:** This document references "suite lifecycle" which has since been consolidated into "class lifecycle".
> There is no separate "suite" lifecycle - it is simply "class" lifecycle managed via Gradle tasks.

**Status:** Complete
**Date:** 2025-11-29
**Target Version:** 2.0.0
**Parent Document:** [Workflow Support Lifecycle](workflow-support-lifecycle.md)
**Relationship:** Follow-up to complete remaining items from Step 12.D

---

## Background

During verification of the Workflow Support Lifecycle plan (Option D), three discrepancies were identified between
the plan's stated completeness and the actual implementation. This plan addresses those remaining items.

## Discrepancies to Address

| Item | Plan Status | Actual Status | Priority |
|------|-------------|---------------|----------|
| `docs/usage/usage-docker-workflows.md` | Should be created/updated | File does NOT exist | Medium |
| `PipelineValidatorTest` for `delegateStackManagement` | Expected per plan | Not found | Low |
| Scenario-3 misleading name | Named "method-lifecycle" | Actually tests class lifecycle | Medium |

### Scenario-3 Resolution Decision

**Decision: Remove scenario-3 entirely (Option B)**

Scenario-3 (`scenario-3-method-lifecycle`) is misleading and redundant:
- It claims to test "method lifecycle" but actually tests class lifecycle
- It is functionally identical to scenario-2 (`scenario-2-delegated-lifecycle`)
- Method lifecycle cannot be achieved with `dockerWorkflows` due to architectural limitations
- Keeping it causes user confusion

See [Why dockerWorkflows Cannot Support Method Lifecycle](workflow-cannot-method-lifecycle.md) for the full
technical explanation of why method lifecycle is impossible with `dockerWorkflows`.

---

## Step Overview

- [x] **Step 12.D.10**: Create `docs/usage/usage-docker-workflows.md` User Documentation
- [x] **Step 12.D.11**: Add `PipelineValidatorTest` Unit Tests for `delegateStackManagement`
- [x] **Step 12.D.12**: Remove Scenario-3 (Redundant and Misleading)
- [x] **Step 12.D.13**: Update Aggregator and Original Plan Documentation

---

## Detailed Implementation Steps

### Step 12.D.10: Create `docs/usage/usage-docker-workflows.md` User Documentation

**Goal:** Create comprehensive user-facing documentation for the `dockerWorkflows` DSL.

**File:** `docs/usage/usage-docker-workflows.md`

**Content Outline:**

1. **Overview**
   - What is `dockerWorkflows`?
   - When to use it vs. manual task orchestration
   - Relationship to `docker`, `dockerOrch`, and `testIntegration` DSLs

2. **Quick Start**
   - Minimal pipeline configuration example
   - Build → test → tag on success pattern

3. **Pipeline DSL Reference**
   - `pipelines { }` container
   - `build { }` step configuration
   - `test { }` step configuration
   - `onTestSuccess { }` conditional actions

4. **Delegated Stack Management**
   - When and why to use `delegateStackManagement = true`
   - How it interacts with `testIntegration.usesCompose()`
   - Example configuration for class lifecycle

5. **Combining dockerWorkflows with testIntegration**
   - Best practices for combining the two DSLs
   - Example configurations for class lifecycle
   - **Important limitation:** Method lifecycle is NOT supported with dockerWorkflows
   - Link to [Why dockerWorkflows Cannot Support Method Lifecycle](../design-docs/todo/workflow-cannot-method-lifecycle.md)

6. **Lifecycle Options Comparison**
   - Table comparing suite and class lifecycles (method NOT supported)
   - When to use each option
   - For method lifecycle, use `@ComposeUp` annotation without dockerWorkflows

7. **Complete Examples**
   - Basic pipeline (scenario-1 reference)
   - Delegated class lifecycle (scenario-2 reference)
   - Pipeline with publish on success

8. **Task Reference**
   - `run<PipelineName>` task
   - Relationship to underlying docker/compose tasks

**Estimated LOC:** 300-400 (documentation)

---

### Step 12.D.11: Add `PipelineValidatorTest` Unit Tests for `delegateStackManagement`

**Goal:** Add unit tests for the `delegateStackManagement` validation logic in `PipelineValidator`.

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/workflow/validation/PipelineValidatorTest.groovy`

**New test cases:**

1. `"validates pipeline with delegateStackManagement true and no stack"`
   - Configure `TestStepSpec` with `delegateStackManagement = true` and no `stack`
   - Verify validation passes (stack is optional when delegating)

2. `"warns when delegateStackManagement true and stack is also set"`
   - Configure `TestStepSpec` with `delegateStackManagement = true` AND `stack` set
   - Verify validation passes but warning is logged
   - Use log capture or spy to verify warning message

3. `"validates pipeline with delegateStackManagement false requires stack"`
   - Configure `TestStepSpec` with `delegateStackManagement = false` and no `stack`
   - Verify validation behavior (may pass in `isTestStepConfigured` but fail later)

4. `"isTestStepConfigured returns true when delegateStackManagement true and only testTaskName set"`
   - Verify the `isTestStepConfigured` method logic for delegation mode

**Estimated LOC:** 80-100

---

### Step 12.D.12: Remove Scenario-3 (Redundant and Misleading)

**Goal:** Remove the misleading `scenario-3-method-lifecycle` directory entirely.

**Rationale:**
- The scenario claims to test "method lifecycle" but actually tests class lifecycle
- It is functionally identical to scenario-2, providing no additional test coverage
- Method lifecycle cannot be achieved with `dockerWorkflows` (see architectural limitation doc)
- The misleading name causes user confusion

**Files/Directories to Remove:**

```
plugin-integration-test/dockerWorkflows/scenario-3-method-lifecycle/
├── app-image/
│   ├── build.gradle
│   ├── src/
│   │   ├── main/docker/Dockerfile
│   │   └── integrationTest/
│   │       ├── groovy/com/kineticfire/test/DelegatedMethodLifecycleIT.groovy
│   │       └── resources/compose/app.yml
└── build.gradle
```

**Update settings.gradle:**

Remove the include for scenario-3:

```groovy
// REMOVE this line:
include 'dockerWorkflows:scenario-3-method-lifecycle:app-image'
```

**Estimated changes:** Delete ~15 files, update 1 file (settings.gradle)

---

### Step 12.D.13: Update Aggregator and Original Plan Documentation

**Goal:** Update references to scenario-3 in aggregator and plan documents.

**Files to Update:**

1. **`plugin-integration-test/dockerWorkflows/build.gradle`**
   - Remove scenario-3 from `integrationTest` task dependencies
   - Remove scenario-3 from `clean` task dependencies
   - Remove scenario-3 images from `cleanDockerImages` task

   Before:
   ```groovy
   dependsOn ':dockerWorkflows:scenario-3-method-lifecycle:app-image:integrationTest'
   dependsOn ':dockerWorkflows:scenario-3-method-lifecycle:app-image:clean'
   docker rmi workflow-scenario3-app:latest 2>/dev/null || true
   docker rmi workflow-scenario3-app:1.0.0 2>/dev/null || true
   docker rmi workflow-scenario3-app:tested 2>/dev/null || true
   ```

   After:
   ```groovy
   // All scenario-3 references removed
   ```

2. **`docs/design-docs/todo/workflow-support-lifecycle.md`**
   - Update Step 12.D.7 to indicate scenario-3 was removed as redundant
   - Add note explaining method lifecycle limitation
   - Reference the new architectural limitation document

3. **`plugin-integration-test/settings.gradle`**
   - Remove `include 'dockerWorkflows:scenario-3-method-lifecycle:app-image'`

**Estimated LOC changes:** 30-40 (mostly deletions)

---

## Implementation Order & Dependencies

```
Step 12.D.10 (User Documentation) ────────────────────────┐
   Create usage-docker-workflows.md                       │
   No dependencies on other steps                         │
                                                          │
Step 12.D.11 (Unit Tests) ────────────────────────────────┤
   Add PipelineValidatorTest cases                        │
   No dependencies on other steps                         │
                                                          │
Step 12.D.12 (Remove Scenario-3) ─────────────────────────┤
   Delete scenario-3 directory                            │
   Update settings.gradle                                 │
                                                          ▼
Step 12.D.13 (Update References) ─────────────────────────┘
   Update aggregator build.gradle
   Update original plan document
   DEPENDS ON: Step 12.D.12
```

---

## Verification

### Step 12.D.10 Verification

```bash
# Verify file exists and is properly formatted
cat docs/usage/usage-docker-workflows.md

# Verify line length compliance (max 120 chars)
awk 'length > 120' docs/usage/usage-docker-workflows.md
```

### Step 12.D.11 Verification

```bash
cd plugin
./gradlew -Pplugin_version=1.0.0 clean test --tests "*PipelineValidatorTest*"
```

**Expected:** All tests pass, including new `delegateStackManagement` tests

### Step 12.D.12 Verification

```bash
# Verify scenario-3 directory no longer exists
ls plugin-integration-test/dockerWorkflows/scenario-3-method-lifecycle
# Expected: No such file or directory

# Verify settings.gradle no longer references scenario-3
grep -r "scenario-3" plugin-integration-test/settings.gradle
# Expected: No matches
```

### Step 12.D.13 Verification

```bash
# Verify aggregator no longer references scenario-3
grep -r "scenario-3" plugin-integration-test/dockerWorkflows/build.gradle
# Expected: No matches

# Run remaining integration tests
cd plugin-integration-test
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:integrationTest
# Expected: scenario-1 and scenario-2 pass
```

---

## Estimated Changes Summary

| Category | Files Added | Files Deleted | Files Modified | Estimated LOC |
|----------|-------------|---------------|----------------|---------------|
| User Documentation | 1 | 0 | 0 | 300-400 |
| Unit Tests | 0 | 0 | 1 | 80-100 |
| Scenario-3 Removal | 0 | ~15 | 0 | -400 (deletion) |
| Reference Updates | 0 | 0 | 3 | 30-40 |
| **TOTAL** | **1** | **~15** | **4** | **10-140 net** |

---

## Estimated Effort

| Step | Description | Effort |
|------|-------------|--------|
| Step 12.D.10 | Create user documentation | 0.5-1 day |
| Step 12.D.11 | Add PipelineValidator tests | 0.25 day |
| Step 12.D.12 | Remove scenario-3 | 0.25 day |
| Step 12.D.13 | Update references | 0.25 day |
| **TOTAL** | | **1.25-1.75 days** |

---

## Quality Gates

Each step must meet these criteria before marking complete:

1. **Documentation (Step 12.D.10)**
   - Line length ≤ 120 characters
   - All code examples are syntactically correct
   - Examples reference actual integration test scenarios
   - Method lifecycle limitation is clearly documented

2. **Unit Tests (Step 12.D.11)**
   - All tests pass
   - Tests cover positive and negative cases
   - Log verification for warning messages (if applicable)

3. **Scenario-3 Removal (Step 12.D.12)**
   - Directory completely removed
   - settings.gradle updated
   - No dangling references

4. **Reference Updates (Step 12.D.13)**
   - Aggregator build.gradle updated
   - Original plan document updated
   - All integration tests still pass

---

## Success Criteria

The implementation is complete when:

- [x] `docs/usage/usage-docker-workflows.md` exists with comprehensive user documentation
- [x] Documentation clearly states method lifecycle is NOT supported
- [x] `PipelineValidatorTest` includes tests for `delegateStackManagement` validation
- [x] `scenario-3-method-lifecycle` directory is completely removed
- [x] `settings.gradle` no longer references scenario-3
- [x] `dockerWorkflows/build.gradle` aggregator no longer references scenario-3
- [x] Original plan document (`workflow-support-lifecycle.md`) is updated with notes about scenario-3 removal
- [x] All remaining unit tests pass
- [x] All remaining integration tests pass (scenario-1 and scenario-2)
- [x] Documentation follows project style guidelines (120 char line limit)

---

## Related Documents

- [Workflow Support Lifecycle Plan (Part 1)](workflow-support-lifecycle.md) - Original plan
- [Why dockerWorkflows Cannot Support Method Lifecycle](workflow-cannot-method-lifecycle.md) - Architectural limitation
- [Gradle 9/10 Compatibility](../gradle-9-and-10-compatibility.md) - Configuration cache requirements
- [Testing Requirements](../../project-standards/testing/unit-testing.md) - Unit test standards
- [Spock/JUnit Test Extensions](../../usage/spock-junit-test-extensions.md) - @ComposeUp for method lifecycle
