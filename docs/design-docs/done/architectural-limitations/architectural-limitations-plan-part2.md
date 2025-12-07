# Implementation Plan Part 2: Save/Publish DSL Methods for onTestSuccess

**Status:** Planning Phase
**Date:** 2025-11-30
**Related Documents:**
- [Main Implementation Plan](architectural-limitations-plan.md)

---

## Problem Statement

The `onTestSuccess` DSL block in `dockerWorkflows` pipelines supports `additionalTags` configuration, but **save and
publish operations cannot be configured via DSL** despite the backend executors being fully implemented.

### Current State

**Implemented ✅:**
- `SaveOperationExecutor.groovy` - saves images to tar files with compression
- `PublishOperationExecutor.groovy` - publishes to registries with auth
- `SuccessStepExecutor.groovy` - wires executors and calls them
- `SuccessStepSpec.groovy` - has `Property<SaveSpec>` and `Property<PublishSpec>` definitions
- Unit tests use programmatic `.set()` methods

**Missing ❌:**
- DSL configuration methods in `SuccessStepSpec` (`save {}`, `publish {}`)
- Functional tests for DSL parsing
- Integration test scenarios demonstrating save/publish in workflows

### Impact

Users cannot write:
```groovy
onTestSuccess {
    additionalTags = ['tested']

    save {
        outputFile = file('build/my-image.tar')
        compression = 'GZIP'
    }

    publish {
        to('production') {
            registry = 'ghcr.io'
            namespace = 'myorg'
        }
    }
}
```

---

## Implementation Steps

### Step 1: Add DSL Methods to SuccessStepSpec

**Goal:** Enable `save {}` and `publish {}` DSL blocks in `onTestSuccess`.

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/workflow/SuccessStepSpec.groovy`

**Changes:**
1. Add `void save(@DelegatesTo(SaveSpec) Closure closure)` method
2. Add `void save(Action<SaveSpec> action)` method
3. Add `void publish(@DelegatesTo(PublishSpec) Closure closure)` method
4. Add `void publish(Action<PublishSpec> action)` method
5. Store `ObjectFactory` and `ProjectLayout` for creating nested specs

**Estimated LOC:** ~40 lines added

---

### Step 2: Update Unit Tests for DSL Methods

**Goal:** Achieve 100% coverage of new DSL methods.

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/workflow/SuccessStepSpecTest.groovy`

**Test Cases:**
1. `save closure configures SaveSpec correctly`
2. `save action configures SaveSpec correctly`
3. `save closure sets outputFile and compression`
4. `publish closure configures PublishSpec correctly`
5. `publish action configures PublishSpec correctly`
6. `publish closure with targets configures correctly`
7. `combined save and publish DSL works`

**Estimated LOC:** ~150 lines added

---

### Step 3: Add Functional Tests for DSL Parsing

**Goal:** Verify DSL parsing works end-to-end via Gradle TestKit.

**File:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/SavePublishDslFunctionalTest.groovy`

**Test Cases:**
1. `onTestSuccess with save block parses correctly`
2. `onTestSuccess with publish block parses correctly`
3. `onTestSuccess with save and publish blocks parses correctly`
4. `save block with compression option parses correctly`
5. `publish block with multiple targets parses correctly`

**Estimated LOC:** ~200 lines

---

### Step 4: Create Integration Test Scenario

**Goal:** Demonstrate save/publish operations in a real workflow.

**Location:** `plugin-integration-test/dockerWorkflows/scenario-7-save-publish/`

**Structure:**
```
scenario-7-save-publish/
├── build.gradle
└── app-image/
    ├── build.gradle
    └── src/
        ├── main/docker/Dockerfile
        └── integrationTest/
            ├── groovy/com/kineticfire/test/SavePublishIT.groovy
            └── resources/compose/app.yml
```

**Features to Test:**
1. Build image
2. Run integration tests
3. On success: save to tar file with GZIP compression
4. On success: (optional) publish to local test registry
5. Verify tar file exists and is valid
6. Cleanup

**Port Allocation:** 9206 (workflow scenario 7)

**Estimated LOC:** ~300 lines total

---

### Step 5: Update settings.gradle

**Goal:** Include new scenario in build.

**File:** `plugin-integration-test/settings.gradle`

**Changes:**
```groovy
include 'dockerWorkflows:scenario-7-save-publish'
include 'dockerWorkflows:scenario-7-save-publish:app-image'
```

---

### Step 6: Update README

**Goal:** Document new scenario.

**File:** `plugin-integration-test/dockerWorkflows/README.md`

**Changes:**
- Add scenario 7 to test scenarios table
- Add port 9206 to port allocations
- Add scenario description

---

## Quality Gates

Each step must meet these criteria:

1. **Code Quality**
   - All code adheres to style guide (max 120 chars, max 500 lines per file)
   - Cyclomatic complexity ≤ 10
   - No compiler warnings

2. **Test Coverage**
   - Unit tests: 100% line and branch coverage for new code
   - Functional tests: DSL parsing verified
   - Integration tests: Real Docker operations verified

3. **Validation**
   - All unit tests pass: `./gradlew test`
   - All functional tests pass: `./gradlew functionalTest`
   - Plugin builds: `./gradlew -Pplugin_version=1.0.0 build publishToMavenLocal`
   - Integration tests pass: `./gradlew -Pplugin_version=1.0.0 integrationTest`
   - No lingering Docker containers

---

## Total Estimated Effort

| Step | Description | Effort |
|------|-------------|--------|
| Step 1 | Add DSL methods to SuccessStepSpec | 30 min |
| Step 2 | Update unit tests | 1 hour |
| Step 3 | Add functional tests | 1 hour |
| Step 4 | Create integration test scenario | 2 hours |
| Step 5 | Update settings.gradle | 5 min |
| Step 6 | Update README | 15 min |
| **TOTAL** | | **~5 hours** |

---

## Success Criteria

The implementation is complete when:

- [ ] `save {}` DSL block works in `onTestSuccess`
- [ ] `publish {}` DSL block works in `onTestSuccess`
- [ ] All unit tests pass with 100% coverage on new code
- [ ] All functional tests pass
- [ ] Integration test scenario 7 passes
- [ ] No Docker containers remain after tests
- [ ] README documentation updated
