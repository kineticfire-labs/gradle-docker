# Implementation Plan: Workflow Support for Test Lifecycle (Option D)

> **Note:** This document references "suite lifecycle" which has since been consolidated into "class lifecycle".
> There is no separate "suite" lifecycle - it is simply "class" lifecycle managed via Gradle tasks.

**Status:** Complete
**Date:** 2025-11-28
**Completion Date:** 2025-11-29
**Target Version:** 2.0.0
**Parent Document:** [Architectural Limitations Plan](architectural-limitations/architectural-limitations-plan.md)
**Relationship:** Offshoot of Step 12 (Integration Testing)

---

## Background

This plan addresses a gap identified during Step 12 integration testing of the `dockerWorkflows` DSL. The current
implementation manages Docker Compose lifecycle at a "suite" level (containers stay up for the entire test step),
but users may need finer-grained control:

- **Method lifecycle**: Fresh containers per test method
- **Class lifecycle**: Containers persist per test class

The existing `testIntegration` extension already supports these lifecycles, but cannot be combined with
`dockerWorkflows` pipeline's conditional post-test actions (tag/save/publish on success).

## Problem Statement

| Feature | dockerTest + testIntegration | dockerWorkflows |
|---------|------------------------------|-----------------|
| Method lifecycle | ✅ | ❌ |
| Class lifecycle | ✅ | ❌ (suite-level only) |
| Conditional on test result | ❌ | ✅ |
| Tag/Save/Publish on success | ❌ | ✅ |

Users wanting per-method/per-class container isolation AND conditional post-test actions are caught between
two incomplete solutions.

## Solution: Option D - Decouple Lifecycle from Pipeline Orchestration

Separate concerns between:
- **`testIntegration`**: Controls HOW tests interact with containers (lifecycle: method vs class)
- **`dockerWorkflows`**: Controls WHAT happens before/after tests (pipeline flow: build → test → conditional → cleanup)

When `delegateStackManagement = true`, the pipeline skips its own `composeUp`/`composeDown` calls and relies on
`testIntegration` to manage container lifecycle via Gradle task dependencies (`dependsOn`, `finalizedBy`).

### Target DSL Usage

```groovy
// Configure testIntegration for lifecycle control
testIntegration {
    usesCompose(integrationTest, 'testStack', 'method')  // or 'class'
}

// Configure pipeline for conditional actions
dockerWorkflows {
    pipelines {
        ciPipeline {
            build {
                image = docker.images.myApp
            }
            test {
                testTaskName = 'integrationTest'
                delegateStackManagement = true  // NEW: testIntegration handles compose lifecycle
            }
            onTestSuccess {
                additionalTags = ['tested', 'approved']
                publish { ... }
            }
        }
    }
}
```

---

## Step Overview

- [x] **Step 12.D.1**: Add `delegateStackManagement` Property to TestStepSpec
- [x] **Step 12.D.2**: Modify TestStepExecutor to Respect `delegateStackManagement`
- [x] **Step 12.D.3**: Update PipelineValidator for New Configuration
- [x] **Step 12.D.4**: Unit Tests for Spec and Executor Changes
- [x] **Step 12.D.5**: Functional Tests for DSL Configuration
- [x] **Step 12.D.6**: Integration Test Scenario - Delegated Class Lifecycle
- [x] **Step 12.D.7**: Integration Test Scenario - Delegated Method Lifecycle (REMOVED - see notes below)
- [x] **Step 12.D.8**: Documentation Updates
- [x] **Step 12.D.9**: Verification and Quality Gates

---

## Detailed Implementation Steps

### Step 12.D.1: Add `delegateStackManagement` Property to TestStepSpec

**Goal:** Add a new boolean property to `TestStepSpec` that controls whether the pipeline manages
the compose lifecycle or delegates to `testIntegration`.

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/workflow/TestStepSpec.groovy`

**Changes:**
```groovy
/**
 * When true, the pipeline delegates compose stack lifecycle management to testIntegration extension.
 * The pipeline will NOT execute composeUp/composeDown - instead relying on the test task's
 * dependsOn and finalizedBy relationships configured by testIntegration.usesCompose().
 *
 * Default: false (pipeline manages compose lifecycle)
 */
abstract Property<Boolean> getDelegateStackManagement()
```

**Constructor change:**
```groovy
delegateStackManagement.convention(false)
```

**Gradle 9/10 Compatibility:**
- Uses `Property<Boolean>` (lazy evaluation)
- No Project reference captured
- Configuration cache safe

**Estimated LOC:** 15

---

### Step 12.D.2: Modify TestStepExecutor to Respect `delegateStackManagement`

**Goal:** When `delegateStackManagement = true`, skip internal `composeUp`/`composeDown` calls.

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/workflow/executor/TestStepExecutor.groovy`

**Current `execute()` flow:**
```
execute():
    validateTestSpec()
    executeBeforeTestHook()
    executeComposeUpTask()      ← managed by executor
    executeTestTask()
    captureTestResult()
    executeComposeDownTask()    ← managed by executor (in finally)
    executeAfterTestHook()
    return context.withTestResult()
```

**New `execute()` flow when `delegateStackManagement = true`:**
```
execute():
    validateTestSpec()
    executeBeforeTestHook()
    // Skip: composeUp - testIntegration handles via task.dependsOn(composeUp)
    executeTestTask()           ← Test task already depends on composeUp
    captureTestResult()
    // Skip: composeDown - testIntegration handles via task.finalizedBy(composeDown)
    executeAfterTestHook()
    return context.withTestResult()
```

**Key method changes:**

```groovy
PipelineContext execute(TestStepSpec testSpec, PipelineContext context) {
    validateTestSpec(testSpec)

    def delegateStackManagement = testSpec.delegateStackManagement.getOrElse(false)
    def testTaskName = testSpec.testTaskName.get()

    // Look up the test task at execution time
    def testTask = lookupTask(testTaskName)
    if (testTask == null) {
        throw new GradleException("Test task '${testTaskName}' not found.")
    }

    TestResult testResult = null
    Exception testException = null

    try {
        executeBeforeTestHook(testSpec)

        if (!delegateStackManagement) {
            // Execute compose up (only when we manage lifecycle)
            def stackSpec = testSpec.stack.get()
            def composeUpTaskName = computeComposeUpTaskName(stackSpec.name)
            executeComposeUpTask(composeUpTaskName)
        }

        // Execute test task
        try {
            executeTestTask(testTask)
            testResult = resultCapture.captureFromTask(testTask)
        } catch (Exception e) {
            LOGGER.warn("Test task failed with exception: {}", e.message)
            testException = e
            testResult = resultCapture.captureFailure(testTask, e)
        }
    } finally {
        if (!delegateStackManagement) {
            // Execute compose down (only when we manage lifecycle)
            def stackSpec = testSpec.stack.get()
            def composeDownTaskName = computeComposeDownTaskName(stackSpec.name)
            executeComposeDownTask(composeDownTaskName)
        }
    }

    executeAfterTestHook(testSpec, testResult)

    if (testException != null) {
        throw new GradleException("Test execution failed: ${testException.message}", testException)
    }

    return context.withTestResult(testResult)
}
```

**Validation changes:**

```groovy
void validateTestSpec(TestStepSpec testSpec) {
    if (testSpec == null) {
        throw new GradleException("TestStepSpec cannot be null")
    }
    if (!testSpec.testTaskName.isPresent()) {
        throw new GradleException("TestStepSpec.testTaskName must be configured")
    }

    def delegateStackManagement = testSpec.delegateStackManagement.getOrElse(false)

    // Stack is required ONLY when we manage lifecycle
    if (!delegateStackManagement && !testSpec.stack.isPresent()) {
        throw new GradleException("TestStepSpec.stack must be configured when delegateStackManagement is false")
    }
}
```

**Estimated LOC changes:** 30

---

### Step 12.D.3: Update PipelineValidator for New Configuration

**Goal:** Add validation for the new `delegateStackManagement` flag and warn about unused configuration.

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/workflow/validation/PipelineValidator.groovy`

**New validation logic:**
1. When `delegateStackManagement = true` and `stack` is also set, log a warning (stack is unused)
2. When `delegateStackManagement = true`, optionally verify the test task has compose dependencies
   configured (nice-to-have, may be complex to detect)

**Estimated LOC:** 25

---

### Step 12.D.4: Unit Tests for Spec and Executor Changes

**Goal:** Achieve 100% coverage on new functionality.

#### TestStepSpecTest Updates

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/workflow/TestStepSpecTest.groovy`

**New test cases:**
1. `"delegateStackManagement property has default value of false"`
2. `"delegateStackManagement property can be set to true"`
3. `"delegateStackManagement property can be set to false explicitly"`
4. `"delegateStackManagement property can be updated after initial configuration"`

**Estimated LOC:** 60

#### TestStepExecutorTest Updates

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/workflow/executor/TestStepExecutorTest.groovy`

**New test cases:**
1. `"execute skips composeUp when delegateStackManagement is true"`
2. `"execute skips composeDown when delegateStackManagement is true"`
3. `"execute still runs composeUp when delegateStackManagement is false"` (existing behavior)
4. `"execute still runs composeDown when delegateStackManagement is false"` (existing behavior)
5. `"execute still runs beforeTest hook when delegateStackManagement is true"`
6. `"execute still runs afterTest hook when delegateStackManagement is true"`
7. `"execute still captures test result when delegateStackManagement is true"`
8. `"execute still throws on test failure when delegateStackManagement is true"`
9. `"validateTestSpec allows missing stack when delegateStackManagement is true"`
10. `"validateTestSpec requires stack when delegateStackManagement is false"`

**Estimated LOC:** 200

#### PipelineValidatorTest Updates (if needed)

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/workflow/validation/PipelineValidatorTest.groovy`

**New test cases:**
1. `"validates pipeline with delegateStackManagement true and no stack"`
2. `"warns when delegateStackManagement true and stack is also set"`

**Estimated LOC:** 50

---

### Step 12.D.5: Functional Tests for DSL Configuration

**Goal:** Verify DSL parsing and task creation with new configuration.

**Files:**
- `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/DockerWorkflowsExtensionFunctionalTest.groovy`
- `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/WorkflowPluginIntegrationFunctionalTest.groovy`

**New test cases:**
1. `"can configure test step with delegateStackManagement true"`
2. `"delegateStackManagement defaults to false"`
3. `"pipeline task is created when delegateStackManagement is true"`
4. `"pipeline with delegateStackManagement true skips internal compose calls"` (verifies via task output)

**Estimated LOC:** 150

---

### Step 12.D.6: Integration Test Scenario - Delegated Class Lifecycle

**Goal:** Demonstrate using `dockerWorkflows` pipeline with `testIntegration` for per-class lifecycle.

**Directory:** `plugin-integration-test/dockerWorkflows/scenario-2-delegated-lifecycle/`

**Structure:**
```
scenario-2-delegated-lifecycle/
├── app-image/
│   ├── build.gradle
│   ├── settings.gradle
│   └── src/
│       ├── main/
│       │   └── docker/
│       │       └── Dockerfile
│       └── integrationTest/
│           ├── groovy/
│           │   └── com/example/workflow/
│           │       └── DelegatedLifecycleTest.groovy
│           └── resources/
│               └── compose/
│                   └── app.yml
```

**build.gradle key sections:**
```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

docker {
    images {
        delegatedApp {
            imageName = 'workflow-scenario2-app'
            tags = ['latest', '1.0.0']
            contextTask = tasks.register('prepareDelegatedContext', Copy) { ... }
        }
    }
}

dockerTest {
    composeStacks {
        delegatedTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = "workflow-scenario2-test"
            waitForHealthy {
                waitForServices.set(['app'])
                timeoutSeconds.set(60)
            }
        }
    }
}

// Configure testIntegration for CLASS lifecycle
testIntegration {
    usesCompose(integrationTest, 'delegatedTest', 'class')
}

// Configure pipeline with delegated stack management
dockerWorkflows {
    pipelines {
        delegatedPipeline {
            description = 'Pipeline with delegated class lifecycle'

            build {
                image = docker.images.delegatedApp
            }

            test {
                testTaskName = 'integrationTest'
                delegateStackManagement = true  // Let testIntegration handle compose
            }

            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}

// Ensure image is built before compose up
afterEvaluate {
    tasks.named('composeUpDelegatedTest') {
        dependsOn tasks.named('dockerBuildDelegatedApp')
    }
}
```

**Integration test class:**
```groovy
class DelegatedLifecycleTest extends Specification {

    def "first test method uses container"() {
        // Verify container is running
        // Make HTTP request
    }

    def "second test method uses same container instance (class lifecycle)"() {
        // Verify same container ID as first test
        // Container persists between methods
    }
}
```

**Verification task:**
```groovy
tasks.register('runDelegatedIntegrationTest') {
    description = 'Run the delegated lifecycle pipeline and verify results'
    group = 'verification'

    dependsOn project(':app').tasks.named('jar')
    dependsOn tasks.named('runDelegatedPipeline')

    doLast {
        // Verify the 'tested' tag was applied
        def result = exec {
            commandLine 'docker', 'images', '-q', 'workflow-scenario2-app:tested'
            standardOutput = new ByteArrayOutputStream()
            ignoreExitValue = true
        }
        def imageId = result.standardOutput.toString().trim()
        if (imageId.isEmpty()) {
            throw new GradleException("Expected image 'workflow-scenario2-app:tested' not found!")
        }
        logger.lifecycle("SUCCESS: Image 'workflow-scenario2-app:tested' found")
    }
}
```

**Estimated LOC:** 250

---

### Step 12.D.7: Integration Test Scenario - Delegated Method Lifecycle

**Status:** REMOVED

**Original Goal:** Demonstrate using `dockerWorkflows` pipeline with per-method lifecycle.

**Removal Reason:** Scenario-3 was removed during the Part 2 implementation because:
1. Method lifecycle cannot be achieved with `dockerWorkflows` (Gradle tasks run once per build)
2. The scenario was misleadingly named but actually tested class lifecycle
3. It was functionally identical to scenario-2

**Alternative:** For method lifecycle, use `@ComposeUp` Spock annotation directly without a pipeline.
See [Why dockerWorkflows Cannot Support Method Lifecycle](workflow-cannot-method-lifecycle.md).

---

### Step 12.D.8: Update dockerWorkflows/build.gradle Aggregator

**Goal:** Add new scenarios to the integration test aggregator.

**File:** `plugin-integration-test/dockerWorkflows/build.gradle`

**Changes (updated after scenario-3 removal):**
```groovy
tasks.register('integrationTest') {
    group = 'verification'
    description = 'Run all dockerWorkflows integration tests'

    // Scenario 1: Basic workflow (build → test → success tag)
    dependsOn ':dockerWorkflows:scenario-1-basic:app-image:integrationTest'

    // Scenario 2: Delegated class lifecycle (testIntegration manages compose per-class)
    dependsOn ':dockerWorkflows:scenario-2-delegated-lifecycle:app-image:integrationTest'

    // Note: scenario-3-method-lifecycle was removed as redundant.
    // Method lifecycle cannot be achieved with dockerWorkflows due to Gradle task architecture.
    // For method lifecycle, use @ComposeUp annotation directly without a pipeline.

    doLast {
        logger.lifecycle("All dockerWorkflows tests complete")
    }
}

tasks.named('clean') {
    dependsOn ':dockerWorkflows:scenario-1-basic:app-image:clean'
    dependsOn ':dockerWorkflows:scenario-2-delegated-lifecycle:app-image:clean'
}

tasks.register('cleanDockerImages', Exec) {
    group = 'verification'
    description = 'Remove Docker images created by workflow tests'

    commandLine 'sh', '-c', '''
        docker rmi workflow-scenario1-app:latest 2>/dev/null || true
        docker rmi workflow-scenario1-app:1.0.0 2>/dev/null || true
        docker rmi workflow-scenario1-app:tested 2>/dev/null || true
        docker rmi workflow-scenario2-app:latest 2>/dev/null || true
        docker rmi workflow-scenario2-app:1.0.0 2>/dev/null || true
        docker rmi workflow-scenario2-app:tested 2>/dev/null || true
        echo "Cleaned up workflow test images"
    '''
}
```

**Estimated LOC:** 30

---

### Step 12.D.9: Documentation Updates

**Goal:** Document the new `delegateStackManagement` feature.

#### Update/Create Usage Documentation

**File:** `docs/usage/usage-docker-workflows.md`

**New sections:**
1. **Delegated Stack Management**
   - When and why to use `delegateStackManagement = true`
   - How it interacts with `testIntegration.usesCompose()`

2. **Combining dockerWorkflows with testIntegration**
   - Best practices for combining the two DSLs
   - Example configurations for class vs method lifecycle

3. **Lifecycle Options Comparison**
   - Table comparing suite, class, and method lifecycles
   - When to use each option

**Estimated LOC:** 200 (documentation)

---

## Verification

### Run All Unit Tests

```bash
cd plugin
./gradlew -Pplugin_version=1.0.0 clean test
```

**Expected:** All tests pass, 100% coverage on new code

### Run All Functional Tests

```bash
cd plugin
./gradlew -Pplugin_version=1.0.0 clean functionalTest
```

**Expected:** All tests pass

### Run All Integration Tests

```bash
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

cd ../plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest
```

**Expected:** All tests pass, no lingering containers

### Verify Configuration Cache Compatibility

```bash
cd plugin-integration-test
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-2-delegated-lifecycle:app-image:integrationTest \
    --configuration-cache
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-2-delegated-lifecycle:app-image:integrationTest \
    --configuration-cache
```

**Expected:** Second run reuses configuration cache

---

## Implementation Order & Dependencies

```
Step 12.D.1 (Spec) ───────────────────────────────────┐
   Add delegateStackManagement property               │
                                                      ▼
Step 12.D.2 (Executor) ───────────────────────────────┐
   Modify TestStepExecutor                            │
   Update validation                                  │
                                                      ▼
Step 12.D.3 (Plugin Wiring) ──────────────────────────┐
   Update PipelineValidator                           │
                                                      ▼
Step 12.D.4 (Unit Tests) ─────────────────────────────┐
   TestStepSpec tests                                 │
   TestStepExecutor tests                             │
   PipelineValidator tests                            │
                                                      ▼
Step 12.D.5 (Functional Tests) ───────────────────────┐
   DSL configuration tests                            │
   Workflow integration tests                         │
                                                      ▼
Step 12.D.6 (Integration - Class) ────────────────────┐
   scenario-2-delegated-lifecycle                     │
                                                      ▼
Step 12.D.7 (Integration - Method) ───────────────────┐
   scenario-3-method-lifecycle                        │
                                                      ▼
Step 12.D.8 (Aggregator Update) ──────────────────────┐
   Update dockerWorkflows/build.gradle                │
                                                      ▼
Step 12.D.9 (Documentation) ──────────────────────────┘
   Usage docs
```

---

## Estimated Changes Summary

| Category | Files Added | Files Modified | Estimated LOC |
|----------|-------------|----------------|---------------|
| Spec Classes | 0 | 1 (TestStepSpec.groovy) | 15 |
| Executors | 0 | 1 (TestStepExecutor.groovy) | 30 |
| Validators | 0 | 1 (PipelineValidator.groovy) | 25 |
| Unit Tests | 0 | 2-3 | 310 |
| Functional Tests | 0 | 2 | 150 |
| Integration Tests | ~8-10 | 1 | 480 |
| Documentation | 0-1 | 0-1 | 200 |
| **TOTAL** | ~10 | ~8 | ~1,210 |

---

## Backward Compatibility

- **Default behavior unchanged:** `delegateStackManagement.convention(false)` preserves existing behavior
- **No breaking changes:** All existing pipelines continue to work as before
- **Additive feature:** Users opt-in by setting `delegateStackManagement = true`

---

## Gradle 9/10 Compatibility Checklist

- [x] Uses `Property<Boolean>` for lazy evaluation
- [x] No Project references in task actions
- [x] No `.get()` during configuration
- [x] TaskContainer access via injected field (already implemented in PipelineRunTask)
- [x] Configuration cache compatibility verified via test commands

---

## Estimated Effort

| Step | Description | Effort |
|------|-------------|--------|
| Step 12.D.1 | Add delegateStackManagement property | 0.25 days |
| Step 12.D.2 | Modify TestStepExecutor | 0.5 days |
| Step 12.D.3 | Update PipelineValidator | 0.25 days |
| Step 12.D.4 | Unit tests | 1 day |
| Step 12.D.5 | Functional tests | 0.5 days |
| Step 12.D.6 | Integration test - class lifecycle | 1 day |
| Step 12.D.7 | Integration test - method lifecycle | 0.5 days |
| Step 12.D.8 | Update aggregator | 0.25 days |
| Step 12.D.9 | Documentation | 0.5 days |
| **TOTAL** | | **4.75 days (~1 week)** |

---

## Quality Gates

Each step must meet these criteria before proceeding:

1. **Code Quality**
   - All code adheres to style guide (max 120 chars, max 500 lines per file)
   - Cyclomatic complexity ≤ 10
   - No compiler warnings

2. **Test Coverage**
   - Unit tests: 100% line and branch coverage on new code
   - Functional tests: All new DSL features covered
   - Integration tests: Both class and method lifecycle scenarios pass

3. **Validation**
   - All unit tests pass
   - All functional tests pass
   - All integration tests pass
   - No lingering Docker containers after tests
   - Configuration cache enabled and working

---

## Success Criteria

The implementation is complete when:

- [x] `delegateStackManagement` property added to TestStepSpec with `convention(false)`
- [x] TestStepExecutor respects `delegateStackManagement` flag
- [x] Unit tests achieve 100% coverage on new code
- [x] Functional tests verify DSL parsing and task creation
- [x] Integration test scenario-2 (class lifecycle) passes
- [x] Scenario-3 removed (was redundant and misleadingly named; see Part 2 implementation notes)
- [x] No Docker containers remain after integration tests
- [x] Configuration cache works with delegated lifecycle scenarios
- [x] Documentation is updated

---

## Implementation Notes (Completion)

### Method Lifecycle Limitation

During implementation, we discovered that true **method lifecycle** (fresh container per test method) cannot be
achieved using only Gradle task dependencies (`testIntegration.usesCompose()`). Gradle tasks run before/after the
entire test task, not before/after each test method.

**For method lifecycle, users must use the `@ComposeUp` Spock annotation**, which hooks into the Spock test framework
at the method level. However, `@ComposeUp` cannot be combined with `testIntegration.usesCompose()` on the same test
class - this causes port conflicts because both mechanisms try to start the same compose stack.

**Recommendation**: When `delegateStackManagement = true`:
- Use `testIntegration.usesCompose(task, stack, 'class')` for class lifecycle via Gradle tasks
- Use `@ComposeUp` Spock annotation directly (without `testIntegration.usesCompose()`) for method lifecycle

### Scenario-3 Removed

Scenario-3 (`scenario-3-method-lifecycle`) was originally designed to test method lifecycle but was ultimately
**removed entirely** during the Part 2 follow-up implementation. The removal was necessary because:

1. **Misleading Name**: The scenario was named "method-lifecycle" but actually tested class lifecycle
2. **Redundancy**: It was functionally identical to scenario-2 (`scenario-2-delegated-lifecycle`)
3. **Architectural Limitation**: True method lifecycle cannot be achieved with `dockerWorkflows` due to Gradle's
   task architecture (tasks run once per build, not per test method)

**For method lifecycle**, users should use the `@ComposeUp` Spock annotation directly without a `dockerWorkflows`
pipeline. See [Why dockerWorkflows Cannot Support Method Lifecycle](workflow-cannot-method-lifecycle.md) for
the full technical explanation.

The remaining integration tests (scenario-1 and scenario-2) provide sufficient coverage:
- **Scenario-1**: Basic pipeline with suite lifecycle (containers up for entire test task)
- **Scenario-2**: Delegated class lifecycle via `testIntegration.usesCompose()`

---

## Related Documents

- [Architectural Limitations Plan](architectural-limitations/architectural-limitations-plan.md) - Parent plan
- [Gradle 9/10 Compatibility](../gradle-9-and-10-compatibility.md) - Configuration cache requirements
- [TestIntegrationExtension](../../../../plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtension.groovy) - Lifecycle implementation
- [TestStepExecutor](../../../../plugin/src/main/groovy/com/kineticfire/gradle/docker/workflow/executor/TestStepExecutor.groovy) - Current executor implementation
