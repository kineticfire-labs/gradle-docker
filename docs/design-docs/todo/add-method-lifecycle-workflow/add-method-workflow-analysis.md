# Analysis: Adding Method Lifecycle Support to dockerWorkflows DSL

**Status:** Phase 1 COMPLETE ✅ | Phase 2 Pending (Future)
**Date:** 2025-12-01 (Analysis) | 2025-12-03 (Phase 1 Complete)
**Author:** Development Team
**Related Documents:**
- [Architectural Limitations Analysis](../architectural-limitations/architectural-limitations-analysis.md)
- [Architectural Limitations Plan](../architectural-limitations/architectural-limitations-plan.md)
- [Workflow Support Lifecycle](../workflow-lifecycle/workflow-support-lifecycle.md)
- [How dockerWorkflows Supports Method Lifecycle](../workflow-lifecycle/workflow-cannot-method-lifecycle.md)

---

## Executive Summary

The `dockerWorkflows` DSL now supports per-method container lifecycle through enhanced test framework integration.
The implementation uses the `lifecycle = WorkflowLifecycle.METHOD` setting to delegate compose management to the
test framework extension (`@ComposeUp` for Spock, `@ExtendWith` for JUnit 5), while preserving the pipeline's
conditional post-test actions (tag/save/publish on success).

**Implementation:** Option E (Enhanced Test Framework Integration) was implemented as recommended.

**Phase 1 Status:** COMPLETE ✅ - All 8 steps implemented and verified (2025-12-03)
**Phase 2 Status:** Pending (Future) - Auto-detection enhancement to eliminate annotation requirement

---

## Problem Statement

### Current Architecture

```
dockerWorkflows Pipeline Execution:
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline (Gradle Task)                                    │
│    ├── BuildStepExecutor.execute()                              │
│    │      └── dockerBuildMyApp (runs ONCE)                      │
│    ├── TestStepExecutor.execute()                               │
│    │      ├── composeUpTestStack (runs ONCE)                    │
│    │      ├── integrationTest   (runs ONCE - all methods)       │
│    │      └── composeDownTestStack (runs ONCE)                  │
│    └── ConditionalExecutor.execute()                            │
│           └── dockerTagMyApp:tested (if tests passed)           │
└─────────────────────────────────────────────────────────────────┘
```

### Why Method Lifecycle Fails

For method lifecycle, we need:
```
Test Method 1: composeUp → run test → composeDown
Test Method 2: composeUp → run test → composeDown
Test Method 3: composeUp → run test → composeDown
```

But Gradle provides:
```
composeUp (ONCE) → all test methods → composeDown (ONCE)
```

**The gap**: Gradle has no mechanism to re-execute tasks between individual test method invocations within a single
test task execution.

### Impact

Users wanting per-method container isolation AND conditional post-test actions (tag/save/publish on success) are caught
between two incomplete solutions:

| Feature | dockerOrch + testIntegration | dockerWorkflows |
|---------|------------------------------|-----------------|
| Method lifecycle | ✅ | ❌ |
| Class lifecycle | ✅ | ✅ (with delegateStackManagement) |
| Conditional on test result | ❌ | ✅ |
| Tag/Save/Publish on success | ❌ | ✅ |
| Pipeline orchestration | ❌ | ✅ |

---

## Alternative Solutions Analysis

### Option A: Test Framework Hooks with Pipeline Result Aggregation

**Concept**: Let the test framework (JUnit 5 / Spock) manage container lifecycle per method, then aggregate results
back to the pipeline for conditional actions.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline                                                  │
│    ├── BuildStepExecutor.execute()                              │
│    │      └── dockerBuildMyApp                                  │
│    ├── TestStepExecutor.execute()                               │
│    │      ├── [NO composeUp - framework manages]                │
│    │      ├── integrationTest (each method does compose up/down)│
│    │      │      ├── @BeforeEach: ComposeLifecycleManager.up()  │
│    │      │      ├── test method                                │
│    │      │      └── @AfterEach: ComposeLifecycleManager.down() │
│    │      └── [NO composeDown - framework manages]              │
│    └── ConditionalExecutor.execute()                            │
│           └── dockerTagMyApp:tested (if all tests passed)       │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation**:
1. Add `lifecycle` property to `TestStepSpec`
2. When `lifecycle = METHOD`:
   - Skip pipeline's composeUp/composeDown calls
   - Set system properties for test framework to detect
   - Test uses `@ComposeUp` (Spock) or `@ExtendWith(ComposeExtension.class)` (JUnit 5)
3. Pipeline captures test results regardless of lifecycle mode

**Pros**:
- ✅ Leverages existing `@ComposeUp` implementation
- ✅ Works with current architecture
- ✅ Configuration cache compatible (no Project references)
- ✅ Clear separation: framework handles lifecycle, pipeline handles conditional actions

**Cons**:
- ⚠️ Requires coordination between DSL config and test annotations
- ⚠️ User must add annotation to test class when using method lifecycle
- ⚠️ Potential for misconfiguration (DSL says method, test doesn't have annotation)

**Effort**: ~3-4 days

---

### Option B: Custom Test Executor with Embedded Compose Control

**Concept**: Replace Gradle's test task execution with a custom executor that controls compose lifecycle per test
method.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline                                                  │
│    ├── BuildStepExecutor.execute()                              │
│    ├── TestStepExecutor.execute()                               │
│    │      └── CustomMethodLifecycleTestExecutor                 │
│    │            ├── Discover test classes/methods               │
│    │            ├── For each method:                            │
│    │            │      ├── composeUp (via ComposeService)       │
│    │            │      ├── Run single test method               │
│    │            │      └── composeDown (via ComposeService)     │
│    │            └── Aggregate results                           │
│    └── ConditionalExecutor.execute()                            │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation**:
1. Create `CustomMethodLifecycleTestExecutor` that:
   - Uses JUnit Platform Launcher API to discover and run tests
   - Wraps each test method execution with compose up/down
   - Injects `ComposeService` for container control
2. Replace standard test task execution when `lifecycle = METHOD`

**Pros**:
- ✅ Complete control over execution order
- ✅ No test annotations required
- ✅ Works for both JUnit 5 and Spock (via JUnit Platform)

**Cons**:
- ❌ Complex implementation (replicate test execution logic)
- ❌ Must handle test filtering, parallel execution, reports
- ❌ Configuration cache challenges (JUnit launcher may hold non-serializable state)
- ❌ High maintenance burden

**Effort**: ~10-15 days

---

### Option C: Gradle Worker API with Per-Method Compose Control

**Concept**: Use Gradle's Worker API to execute tests in isolated workers, each controlling its own compose lifecycle.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline                                                  │
│    ├── BuildStepExecutor.execute()                              │
│    ├── TestStepExecutor.execute()                               │
│    │      └── WorkerExecutor.submit(TestMethodWorker) per method│
│    │            ├── Worker 1: up → test1 → down                 │
│    │            ├── Worker 2: up → test2 → down                 │
│    │            └── Worker 3: up → test3 → down                 │
│    └── ConditionalExecutor.execute() (after all workers done)   │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation**:
1. Discover test methods at configuration time or execution start
2. Submit each test method as a separate Worker
3. Each worker runs compose up/down around its test

**Pros**:
- ✅ Gradle-native parallelism control
- ✅ Configuration cache compatible (Workers are serializable)
- ✅ Clean isolation per test method

**Cons**:
- ❌ Port conflicts when running parallel workers (each needs unique compose project)
- ❌ Complex test discovery (need to enumerate methods before execution)
- ❌ Sequential execution required to avoid port conflicts, negating Worker benefits
- ❌ Report aggregation complexity

**Effort**: ~8-12 days

---

### Option D: Process-Per-Test with Result File Aggregation

**Concept**: Fork a new Gradle process for each test method, each with its own compose lifecycle.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline                                                  │
│    ├── BuildStepExecutor.execute()                              │
│    ├── TestStepExecutor.execute()                               │
│    │      ├── Discover test methods                             │
│    │      ├── For each method:                                  │
│    │      │      └── ExecOperations.exec {                      │
│    │      │            ./gradlew test --tests "Class.method"    │
│    │      │            // includes composeUp/Down via task deps │
│    │      │          }                                          │
│    │      └── Aggregate exit codes / test results               │
│    └── ConditionalExecutor.execute()                            │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation**:
1. Use `ExecOperations` to spawn Gradle processes
2. Each process runs single test with compose task dependencies
3. Collect results from JUnit XML reports

**Pros**:
- ✅ Complete isolation (separate JVM, separate compose project)
- ✅ Leverages existing compose task infrastructure
- ✅ Configuration cache compatible (ExecOperations is safe)

**Cons**:
- ❌ Very slow (JVM startup per test method)
- ❌ Complex test discovery
- ❌ Gradle daemon overhead per invocation
- ❌ Configuration cache invalidated frequently

**Effort**: ~6-8 days

---

### Option E: Enhanced Test Framework Integration (Recommended)

**Concept**: Extend current test framework integration to communicate lifecycle intent from DSL to test runtime via
system properties. The test framework extensions already support this pattern.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  Configuration Time:                                            │
│    dockerWorkflows { pipelines { ci {                           │
│        test {                                                   │
│            lifecycle = WorkflowLifecycle.METHOD  // Type-safe   │
│            testTaskName = 'integrationTest'                     │
│        }                                                        │
│    }}}                                                          │
│                                                                 │
│  → Sets system property: docker.compose.lifecycle=method        │
│  → Sets system property: docker.compose.stack=testStack         │
├─────────────────────────────────────────────────────────────────┤
│  Test Runtime:                                                  │
│    @ComposeUp / JUnit extension reads system props              │
│    → Detects lifecycle=method                                   │
│    → Applies method lifecycle behavior (ALREADY IMPLEMENTED)    │
│    → Reports results to pipeline via shared file                │
├─────────────────────────────────────────────────────────────────┤
│  Pipeline Execution:                                            │
│    TestStepExecutor detects lifecycle=METHOD                    │
│    → Skips composeUp/Down (framework handles)                   │
│    → Runs test task                                             │
│    → Reads aggregated result                                    │
│    → Passes to ConditionalExecutor                              │
└─────────────────────────────────────────────────────────────────┘
```

**Key Insight**: The test framework extensions (`ComposeMethodInterceptor`, `DockerComposeMethodExtension`,
`DockerComposeSpockExtension`) **already read system properties** to configure lifecycle. The pipeline just needs to
set the correct system property - the extension already knows what to do with it.

**Implementation Details** (Refined):

1. **Create `WorkflowLifecycle` Enum** (NEW - type-safe):
   ```groovy
   // plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/workflow/WorkflowLifecycle.groovy
   enum WorkflowLifecycle {
       /**
        * CLASS lifecycle - Containers start once before all test methods and stop after all complete.
        * This is the default. The pipeline orchestrates compose up/down via Gradle tasks,
        * or delegates to testIntegration when delegateStackManagement=true.
        */
       CLASS,

       /**
        * METHOD lifecycle - Containers start fresh before each test method and stop after each.
        * When METHOD is selected, the pipeline automatically delegates compose management to the
        * test framework extension (@ComposeUp for Spock, @ExtendWith for JUnit 5).
        */
       METHOD
   }
   ```

2. **Add `lifecycle` Property to TestStepSpec**:
   ```groovy
   // TestStepSpec.groovy
   abstract Property<WorkflowLifecycle> getLifecycle()

   // In constructor:
   lifecycle.convention(WorkflowLifecycle.CLASS)
   ```

3. **Modify TestStepExecutor**:
   ```groovy
   PipelineContext execute(TestStepSpec testSpec, PipelineContext context) {
       validateTestSpec(testSpec)

       def lifecycle = testSpec.lifecycle.getOrElse(WorkflowLifecycle.CLASS)
       def testTaskName = testSpec.testTaskName.get()

       // METHOD lifecycle forces delegation to test framework
       def shouldDelegateCompose = (lifecycle == WorkflowLifecycle.METHOD) ||
                                   testSpec.delegateStackManagement.getOrElse(false)

       // For METHOD lifecycle, set system properties so test framework knows what to do
       if (lifecycle == WorkflowLifecycle.METHOD) {
           def stackSpec = testSpec.stack.get()
           setSystemPropertiesForMethodLifecycle(testTaskName, stackSpec)
       }

       def testTask = lookupTask(testTaskName)
       // ... validation ...

       try {
           executeBeforeTestHook(testSpec)

           if (!shouldDelegateCompose) {
               // CLASS lifecycle with pipeline management
               def stackName = testSpec.stack.get().name
               executeComposeUpTask(computeComposeUpTaskName(stackName))
           } else {
               LOGGER.info("Compose management delegated to test framework (lifecycle: {})", lifecycle)
           }

           // Execute test task
           executeTestTask(testTask)
           testResult = resultCapture.captureFromTask(testTask)

       } finally {
           if (!shouldDelegateCompose) {
               def stackName = testSpec.stack.get().name
               executeComposeDownTask(computeComposeDownTaskName(stackName))
           }
       }

       // ... hooks and return ...
   }

   private void setSystemPropertiesForMethodLifecycle(String testTaskName, ComposeStackSpec stackSpec) {
       def testTask = lookupTask(testTaskName) as Test

       testTask.systemProperty('docker.compose.lifecycle', 'method')
       testTask.systemProperty('docker.compose.stack', stackSpec.name)
       testTask.systemProperty('docker.compose.files', stackSpec.files.collect { it.absolutePath }.join(','))
       testTask.systemProperty('docker.compose.projectName', stackSpec.projectName.getOrElse(''))

       // Set wait configuration if present
       def waitForHealthy = stackSpec.waitForHealthy.getOrNull()
       if (waitForHealthy) {
           testTask.systemProperty('docker.compose.waitForHealthy.services',
               waitForHealthy.waitForServices.getOrElse([]).join(','))
           testTask.systemProperty('docker.compose.waitForHealthy.timeoutSeconds',
               waitForHealthy.timeoutSeconds.getOrElse(60).toString())
       }

       // ... similar for waitForRunning ...
   }
   ```

4. **Add Validation in PipelineValidator** (Enforce sequential execution as ERROR):
   ```groovy
   void validateTestStep(PipelineSpec pipeline, TestStepSpec testSpec, Project project) {
       // ... existing validation ...

       def lifecycle = testSpec.lifecycle.getOrElse(WorkflowLifecycle.CLASS)

       if (lifecycle == WorkflowLifecycle.METHOD) {
           validateMethodLifecycleConfiguration(pipeline, testSpec, project)
       }
   }

   private void validateMethodLifecycleConfiguration(PipelineSpec pipeline, TestStepSpec testSpec, Project project) {
       def testTaskName = testSpec.testTaskName.get()
       def testTask = project.tasks.findByName(testTaskName)

       // NOTE: Validation for delegateStackManagement + lifecycle=METHOD redundancy does NOT exist in Phase 1.
       // This validation is added in Phase 2, Step 2. Phase 1 focuses on core functionality only.

       if (testTask instanceof Test) {
           def maxForks = testTask.maxParallelForks

           if (maxForks > 1) {
               throw new GradleException(
                   "Pipeline '${pipeline.name}' has lifecycle=METHOD but test task '${testTaskName}' " +
                   "has maxParallelForks=${maxForks}. " +
                   "Method lifecycle requires sequential test execution (maxParallelForks=1) to avoid port conflicts. " +
                   "Either:\n" +
                   "  1. Set maxParallelForks=1 on the test task, OR\n" +
                   "  2. Use lifecycle=CLASS for parallel test execution\n\n" +
                   "Example fix:\n" +
                   "  tasks.named('${testTaskName}') {\n" +
                   "      maxParallelForks = 1\n" +
                   "  }"
               )
           }
       }

       // Also validate stack is configured (needed for system properties)
       if (!testSpec.stack.isPresent()) {
           throw new GradleException(
               "Pipeline '${pipeline.name}' has lifecycle=METHOD but no stack is configured. " +
               "Method lifecycle requires a stack to configure compose settings. " +
               "Add: stack = dockerOrch.composeStacks.<yourStack>"
           )
       }
   }
   ```

**Pros**:
- ✅ Minimal user-facing change (just set `lifecycle = WorkflowLifecycle.METHOD` in DSL)
- ✅ Type-safe enum with IDE autocomplete and compile-time checking
- ✅ No required test framework extension changes (ALREADY IMPLEMENTED)
- ✅ Configuration cache compatible
- ✅ Leverages existing compose service infrastructure
- ✅ Works for both JUnit 5 and Spock
- ✅ Maintains pipeline's conditional action capability
- ✅ Backward compatible - `CLASS` remains default

**Cons**:
- ⚠️ Requires test class annotation (`@ComposeUp` for Spock, `@ExtendWith` for JUnit 5)
- ⚠️ System property coordination (could fail if misconfigured)
- ⚠️ Sequential test execution required to avoid port conflicts

**Effort**: ~16 hours (2-3 days) - REDUCED from 7 days due to existing infrastructure

---

## Evaluation Criteria Matrix

| Criterion | Weight | Option A | Option B | Option C | Option D | Option E |
|-----------|--------|----------|----------|----------|----------|----------|
| Implementation complexity | 20% | 3/5 | 1/5 | 2/5 | 2/5 | 4/5 |
| User experience (simplicity) | 25% | 3/5 | 5/5 | 4/5 | 3/5 | 5/5 |
| Configuration cache compat | 15% | 5/5 | 2/5 | 4/5 | 4/5 | 5/5 |
| Maintenance burden | 15% | 4/5 | 1/5 | 2/5 | 3/5 | 4/5 |
| Works with JUnit & Spock | 10% | 5/5 | 4/5 | 4/5 | 5/5 | 5/5 |
| Performance | 10% | 4/5 | 3/5 | 3/5 | 1/5 | 4/5 |
| Risk level | 5% | 4/5 | 2/5 | 2/5 | 3/5 | 4/5 |
| **Weighted Score** | **100%** | **3.65** | **2.55** | **3.05** | **2.80** | **4.40** |

---

## Recommendation: Option E (Enhanced Test Framework Integration)

### Rationale

1. **Best User Experience**: Users only need to set `lifecycle = WorkflowLifecycle.METHOD` in the DSL—minimal test
   code changes required (annotation on test class).

2. **Leverages Existing Infrastructure**: The `@ComposeUp` extension and `DockerComposeMethodExtension` already:
   - Read system properties (`docker.compose.lifecycle`, `docker.compose.stack`, etc.)
   - Implement per-method compose up/down
   - Generate unique project names to avoid conflicts

3. **Configuration Cache Safe**: Uses system properties (safe) rather than holding non-serializable state.

4. **Clear Architecture**: Maintains separation of concerns:
   - DSL declares intent (lifecycle mode)
   - Pipeline sets system properties and skips Gradle compose tasks
   - Test framework executes lifecycle
   - Pipeline handles conditional actions based on test results

5. **Reduced Scope**: By leveraging existing infrastructure, Phase 1 effort is reduced from 7 days to 2-3 days.

### Target DSL Usage

```groovy
dockerWorkflows {
    pipelines {
        ciPipeline {
            description = 'CI pipeline with method-level test isolation'

            build {
                image = docker.images.myApp
            }

            test {
                stack = dockerOrch.composeStacks.testStack
                testTaskName = 'integrationTest'
                lifecycle = WorkflowLifecycle.METHOD  // Type-safe enum
            }

            onTestSuccess {
                additionalTags = ['tested', 'approved']
                publish {
                    to('production') {
                        registry = 'ghcr.io'
                        namespace = 'myorg'
                    }
                }
            }
        }
    }
}
```

### Test Class Requirement

When using `lifecycle = METHOD`, the test class **MUST** have the appropriate annotation:

**Spock:**
```groovy
@ComposeUp  // Reads system properties set by pipeline
class MyIntegrationTest extends Specification {
    def "test method 1"() { /* gets fresh containers */ }
    def "test method 2"() { /* gets fresh containers */ }
}
```

**JUnit 5:**
```java
@ExtendWith(DockerComposeMethodExtension.class)
class MyIntegrationTest {
    @Test
    void testMethod1() { /* gets fresh containers */ }
    @Test
    void testMethod2() { /* gets fresh containers */ }
}
```

---

## Implementation Plan

### Phase 1: Core Method Lifecycle Support (Annotation-Based)

**Step 1: Create WorkflowLifecycle Enum** (0.5 hours)
```
plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/workflow/WorkflowLifecycle.groovy
└── Enum with CLASS and METHOD values
└── Comprehensive Javadoc explaining each mode
```

**Step 2: Add lifecycle Property to TestStepSpec** (0.5 hours)
```
plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/workflow/TestStepSpec.groovy
└── Property<WorkflowLifecycle> lifecycle
└── Convention: WorkflowLifecycle.CLASS
```

**Step 3: Modify TestStepExecutor** (2 hours)
```
plugin/src/main/groovy/com/kineticfire/gradle/docker/workflow/executor/TestStepExecutor.groovy
├── Detect lifecycle = METHOD
├── Set system properties for test framework
│   ├── docker.compose.lifecycle=method
│   ├── docker.compose.stack=<stackName>
│   ├── docker.compose.files=<paths>
│   ├── docker.compose.projectName=<name>
│   └── docker.compose.waitFor* properties
└── Skip Gradle compose tasks when METHOD
```

**Step 4: Add Validation to PipelineValidator** (1 hour)
```
plugin/src/main/groovy/com/kineticfire/gradle/docker/workflow/validation/PipelineValidator.groovy
├── Validate maxParallelForks=1 when METHOD (ERROR, not warning)
└── Validate stack is configured when METHOD
```

**Step 5: Unit Tests** (4 hours)
```
plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/workflow/WorkflowLifecycleTest.groovy
└── Enum value tests

plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/workflow/TestStepSpecTest.groovy
├── lifecycle property default is CLASS
├── lifecycle property can be set to METHOD
└── lifecycle property persists across configuration

plugin/src/test/groovy/com/kineticfire/gradle/docker/workflow/executor/TestStepExecutorTest.groovy
├── METHOD lifecycle sets system properties
├── METHOD lifecycle skips Gradle compose tasks
├── CLASS lifecycle calls Gradle compose tasks
└── System properties include all stack configuration

plugin/src/test/groovy/com/kineticfire/gradle/docker/workflow/validation/PipelineValidatorTest.groovy
├── METHOD lifecycle rejects parallel test execution (ERROR)
├── METHOD lifecycle accepts sequential test execution
├── METHOD lifecycle requires stack configuration
├── METHOD lifecycle + delegateStackManagement=true produces ERROR
└── CLASS lifecycle allows parallel test execution
```

**Step 6: Functional Tests** (2 hours)
```
plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/workflow/MethodLifecycleFunctionalTest.groovy
├── DSL parsing with lifecycle = WorkflowLifecycle.METHOD
├── DSL parsing with lifecycle = WorkflowLifecycle.CLASS
├── Default lifecycle is CLASS
└── Enum values are type-checked
```

**Step 7: Integration Test Scenario** (4 hours)
```
plugin-integration-test/dockerWorkflows/scenario-8-method-lifecycle/
├── build.gradle                          # Pipeline configuration
├── app-image/
│   ├── build.gradle                      # Image build + test configuration
│   └── src/
│       ├── main/docker/
│       │   └── Dockerfile
│       └── integrationTest/
│           ├── groovy/com/kineticfire/test/
│           │   └── MethodLifecycleIT.groovy  # Spock test with @ComposeUp
│           └── resources/compose/
│               └── app.yml
```

> **Note:** Phase 1 Step 7 originally referenced `scenario-7-method-lifecycle` but the actual implementation used
> `scenario-8-method-lifecycle` (port 9207). This has been corrected to match the implemented scenario.

**Step 8: Documentation** (2 hours)
```
docs/usage/usage-docker-workflows.md
└── Document lifecycle option with examples

docs/design-docs/todo/add-method-lifecycle-workflow/workflow-cannot-method-lifecycle.md
└── Update to reflect new METHOD lifecycle capability
```

**Phase 1 Total: ~16 hours (2-3 days)**

### Phase 2: Auto-Detection Enhancement (Future)

After Phase 1 is complete and validated, implement global extensions to eliminate the annotation requirement.

**Goal:** Eliminate the need for `@ComposeUp` (Spock) and `@ExtendWith` (JUnit 5) annotations when using
`lifecycle = WorkflowLifecycle.METHOD` in dockerWorkflows pipelines. After Phase 2 implementation, adding these
annotations to test classes should produce an error—all compose lifecycle management must be via system properties
set by the pipeline.

---

#### Phase 2 Step Dependencies

The Phase 2 steps have explicit dependencies that must be followed:

```
Step Dependencies Diagram:

  ┌─────────────────────────────────────────────────────────────────────────┐
  │                                                                         │
  │   Step 1: Refactor JUnit 5 Extensions ──────────────────────┐          │
  │      (Remove hardcoded defaults, add system-property config) │          │
  │                                                              │          │
  │                      │                                       │          │
  │                      ▼                                       │          │
  │                                                              │          │
  │   Step 2: Add Validation ◄────── (Independent, can run      │          │
  │      (delegateStackManagement     in parallel with Step 1)  │          │
  │       + lifecycle=METHOD)                                    │          │
  │                                                              │          │
  │                      │                                       │          │
  │                      ▼                                       ▼          │
  │                                                                         │
  │   Step 3: Create Spock Global Extension ◄─── Step 1 must be complete   │
  │      (Uses existing interceptors)            before delegation works    │
  │                                                                         │
  │                      │                                                  │
  │                      ▼                                                  │
  │                                                                         │
  │   Step 4: Create Spock Service Registration                             │
  │      (META-INF/services file)                                           │
  │                                                                         │
  │                      │                                                  │
  │                      ▼                                                  │
  │                                                                         │
  │   Step 5: Create JUnit 5 Global Extension ◄─── Step 1 MUST be complete │
  │      (Delegates to refactored extensions)      Extensions must support  │
  │                                                system-property config   │
  │                      │                                                  │
  │                      ▼                                                  │
  │                                                                         │
  │   Step 6: Create JUnit 5 Service Registration                           │
  │      (META-INF/services file)                                           │
  │                                                                         │
  │                      │                                                  │
  │                      ▼                                                  │
  │                                                                         │
  │   Steps 7-9: Unit Tests, Functional Tests, Integration Tests            │
  │      (Can be written incrementally as each step completes)              │
  │                                                                         │
  └─────────────────────────────────────────────────────────────────────────┘
```

**Critical Dependency**: Steps 3 and 5 (global extensions) **cannot work correctly** until Step 1 is complete.
The JUnit 5 extensions currently have hardcoded defaults that prevent system-property-only configuration.

---

#### Service Class Locations (Important for Implementation)

The service interfaces and implementations used by test framework extensions are split between Java and Groovy:

**Java files** (in `plugin/src/main/java/com/kineticfire/gradle/docker/junit/service/`):
| File | Type | Description |
|------|------|-------------|
| `ProcessExecutor.java` | Interface | External process execution |
| `DefaultProcessExecutor.java` | Implementation | Uses `ProcessBuilder` |
| `FileService.java` | Interface | File system operations |
| `DefaultFileService.java` | Implementation | Standard `java.nio.file` |
| `SystemPropertyService.java` | Interface | System property access |
| `DefaultSystemPropertyService.java` | Implementation | `System.getProperty()` |
| `TimeService.java` | Interface | Time operations |
| `DefaultTimeService.java` | Implementation | `LocalDateTime.now()` |

**Groovy file** (in `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/service/`):
| File | Type | Description |
|------|------|-------------|
| `JUnitComposeService.groovy` | Implementation | Standalone `ComposeService` for test extensions |

**Why "JUnit" in the package name?** These services are **framework-agnostic** despite the package name. The name
is a historical artifact from when they were first created for JUnit 5 support. Both Spock and JUnit 5 extensions
use these same services because they need standalone implementations that work without Gradle context.

**Import pattern** (works in both Groovy and Java):
```groovy
import com.kineticfire.gradle.docker.junit.service.*  // All service interfaces and implementations
import com.kineticfire.gradle.docker.service.ComposeService  // ComposeService interface from main service package
```

---

**Step 1: Refactor JUnit 5 Extensions to Remove Hardcoded Defaults** (2-3 hours)

Before implementing global extensions, refactor the existing JUnit 5 extensions to remove hardcoded defaults
and add system-properties-only configuration methods. The Spock interceptors (`ComposeMethodInterceptor`,
`ComposeClassInterceptor`) already receive all configuration via their constructor's `config` map parameter
and have NO hardcoded defaults—they are ready for delegation as-is.

**Pre-Implementation: Locate Hardcoded Defaults**

Search for hardcoded defaults that must be removed from JUnit 5 extensions:

```bash
# Search for hardcoded service names in JUnit extensions (the "time-server" default)
rg "time-server" plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/

# Search for waitForServices defaults in JUnit extensions
rg "waitServicesProperty" plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/ -C 5
```

**Known Hardcoded Default:**
The JUnit 5 extensions have a hardcoded default in `waitForStackToBeReady()`:
```java
// Default for plugin integration tests
services = Collections.singletonList("time-server");
```
This must be changed to fail if no services are specified via system properties.

**Files to Modify:**
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtension.groovy`
  - Remove `"time-server"` hardcoded default in `waitForStackToBeReady()` (search for `Collections.singletonList("time-server")`)
  - Add `createConfigurationFromSystemProperties(ExtensionContext context)` method
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeClassExtension.groovy`
  - Remove `"time-server"` hardcoded default in `waitForStackToBeReady()` (search for `Collections.singletonList("time-server")`)
  - Add `createConfigurationFromSystemProperties(ExtensionContext context)` method

**Files NOT Modified (already support delegation):**
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeMethodInterceptor.groovy` - ✅ No hardcoded defaults
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeClassInterceptor.groovy` - ✅ No hardcoded defaults

**Required Change: Remove Hardcoded Default**

In both `DockerComposeMethodExtension.groovy` and `DockerComposeClassExtension.groovy`, change:

```java
// BEFORE (line ~346):
} else {
    // Default for plugin integration tests
    services = Collections.singletonList("time-server");
}
```

To:

```java
// AFTER:
} else {
    // No hardcoded default - system property is required when using global extension
    System.err.println("Warning: No wait services configured via docker.compose.waitServices system property. " +
                       "Health check will be skipped.");
    return;  // Skip health check if no services specified
}
```

**Required Change: Add Backward Compatibility for System Property Names**

The JUnit 5 extensions currently read `docker.compose.waitServices` but the canonical property name used by
`TestIntegrationExtension` and `TestStepExecutor` is `docker.compose.waitForHealthy.services`. Add backward
compatibility by reading BOTH property names, with the canonical name taking precedence.

In both `DockerComposeMethodExtension.groovy` and `DockerComposeClassExtension.groovy`, update the
`waitForStackToBeReady()` method to read both property names:

```java
// BEFORE (approximately line ~340):
String waitServicesProperty = systemPropertyService.getProperty(COMPOSE_WAIT_SERVICES_PROPERTY);
List<String> services;
if (waitServicesProperty != null && !waitServicesProperty.isEmpty()) {
    services = Arrays.asList(waitServicesProperty.split(","));
} else {
    // ... (hardcoded default removed above)
}
```

```java
// AFTER - with backward compatibility:
// Read canonical name first, fall back to legacy for backward compatibility
String waitServicesProperty = systemPropertyService.getProperty("docker.compose.waitForHealthy.services");
if (waitServicesProperty == null || waitServicesProperty.isEmpty()) {
    // Legacy property name - for backward compatibility with older configurations
    waitServicesProperty = systemPropertyService.getProperty(COMPOSE_WAIT_SERVICES_PROPERTY);  // "docker.compose.waitServices"
}

List<String> services;
if (waitServicesProperty != null && !waitServicesProperty.isEmpty()) {
    services = Arrays.asList(waitServicesProperty.split(","));
} else {
    // No hardcoded default - system property is required when using global extension
    System.err.println("Warning: No wait services configured. Health check will be skipped.");
    return;  // Skip health check if no services specified
}
```

**Why this matters:**
- Existing configurations using `docker.compose.waitServices` continue to work (backward compatible)
- New configurations using `docker.compose.waitForHealthy.services` also work (canonical name)
- The canonical name takes precedence if both are set
- No breaking changes for existing users

**Required Addition: `createConfigurationFromSystemProperties` Method**

Add this method to both JUnit 5 extension classes. This method enables the global extension to delegate
configuration setup to the existing extensions:

```java
/**
 * Creates configuration map from system properties only (no annotation fallback).
 *
 * <p>This method is used by the global extension when auto-detecting compose configuration
 * from system properties set by the pipeline. Unlike annotation-based configuration, this
 * method requires ALL configuration to come from system properties.</p>
 *
 * @param context the JUnit extension context
 * @return configuration map suitable for compose lifecycle management
 * @throws IllegalStateException if required system properties are missing
 */
Map<String, Object> createConfigurationFromSystemProperties(ExtensionContext context) {
    // Read required properties
    String stackName = systemPropertyService.getProperty("docker.compose.stack", "");
    String composeFilesStr = systemPropertyService.getProperty("docker.compose.files", "");
    String lifecycleStr = systemPropertyService.getProperty("docker.compose.lifecycle", "class");

    // Validate required properties
    if (stackName.isEmpty()) {
        throw new IllegalStateException(
            "docker.compose.stack system property is required but not set. " +
            "Ensure the pipeline configures lifecycle=METHOD with a stack."
        );
    }
    if (composeFilesStr.isEmpty()) {
        throw new IllegalStateException(
            "docker.compose.files system property is required but not set. " +
            "Ensure the stack has compose files configured."
        );
    }

    // Parse compose files (comma-separated)
    List<String> composeFiles = Arrays.stream(composeFilesStr.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());

    // Read optional properties
    String projectNameBase = systemPropertyService.getProperty("docker.compose.projectName", "");
    if (projectNameBase.isEmpty()) {
        projectNameBase = stackName;  // Fallback to stack name
    }

    // Parse wait configuration
    String waitForHealthyStr = systemPropertyService.getProperty("docker.compose.waitForHealthy.services", "");
    List<String> waitForHealthy = waitForHealthyStr.isEmpty() ? Collections.emptyList() :
        Arrays.stream(waitForHealthyStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

    String waitForRunningStr = systemPropertyService.getProperty("docker.compose.waitForRunning.services", "");
    List<String> waitForRunning = waitForRunningStr.isEmpty() ? Collections.emptyList() :
        Arrays.stream(waitForRunningStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

    // Parse timeout and poll values
    String timeoutStr = systemPropertyService.getProperty("docker.compose.waitForHealthy.timeoutSeconds", "");
    if (timeoutStr.isEmpty()) {
        timeoutStr = systemPropertyService.getProperty("docker.compose.waitForRunning.timeoutSeconds", "60");
    }
    int timeoutSeconds = Integer.parseInt(timeoutStr);

    String pollStr = systemPropertyService.getProperty("docker.compose.waitForHealthy.pollSeconds", "");
    if (pollStr.isEmpty()) {
        pollStr = systemPropertyService.getProperty("docker.compose.waitForRunning.pollSeconds", "2");
    }
    int pollSeconds = Integer.parseInt(pollStr);

    // Get class name for unique project name generation
    String className = context.getTestClass()
        .map(Class::getSimpleName)
        .orElse("UnknownTest");

    // Build configuration map (matches Spock interceptor config map format)
    Map<String, Object> config = new HashMap<>();
    config.put("stackName", stackName);
    config.put("composeFiles", composeFiles);
    config.put("lifecycle", lifecycleStr.equalsIgnoreCase("method") ? "METHOD" : "CLASS");
    config.put("projectNameBase", projectNameBase);
    config.put("className", className);
    config.put("waitForHealthy", waitForHealthy);
    config.put("waitForRunning", waitForRunning);
    config.put("timeoutSeconds", timeoutSeconds);
    config.put("pollSeconds", pollSeconds);

    return config;
}
```

**Required Imports for the new method:**
```java
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
```

---

### Expected Unit Test Adjustments After Step 1

> **⚠️ Implementation Note:** Completing Step 1 will likely cause some existing unit tests to fail. This section
> documents the expected test adjustments required after implementing the changes above.

**Tests That May Need Updates:**

The following test files test the `waitForStackToBeReady()` method behavior and may need adjustments:

1. **`DockerComposeMethodExtensionTest.groovy`** (if it exists)
   - Tests that expect the `"time-server"` default behavior must be updated
   - Tests should verify the new warning message and early return when no services configured
   - Add tests for backward compatibility (reading both `docker.compose.waitServices` and
     `docker.compose.waitForHealthy.services`)

2. **`DockerComposeClassExtensionTest.groovy`** (if it exists)
   - Same changes as above for the class-level extension

**New Test Cases Required:**

After Step 1, add these test cases to verify the changes:

```groovy
// Test: No services configured - should skip health check with warning
def "waitForStackToBeReady skips health check when no services configured"() {
    given:
    systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> null
    systemPropertyService.getProperty("docker.compose.waitServices") >> null  // Legacy name

    when:
    extension.waitForStackToBeReady(stackName, projectName)

    then:
    // Verify warning printed and method returned early (no compose wait calls)
    0 * composeService.waitForHealthy(_, _, _)
}

// Test: Canonical property name takes precedence
def "waitForStackToBeReady uses canonical property name over legacy"() {
    given:
    systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> "app,db"
    systemPropertyService.getProperty("docker.compose.waitServices") >> "old-service"  // Should be ignored

    when:
    extension.waitForStackToBeReady(stackName, projectName)

    then:
    1 * composeService.waitForHealthy(_, ["app", "db"], _)  // Uses canonical, not legacy
}

// Test: Legacy property name works when canonical is not set
def "waitForStackToBeReady falls back to legacy property name"() {
    given:
    systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> null
    systemPropertyService.getProperty("docker.compose.waitServices") >> "legacy-service"

    when:
    extension.waitForStackToBeReady(stackName, projectName)

    then:
    1 * composeService.waitForHealthy(_, ["legacy-service"], _)
}
```

**Verification Command:**

After completing Step 1, run the unit tests to identify failures:
```bash
cd plugin && ./gradlew test --tests "*DockerComposeMethodExtension*" --tests "*DockerComposeClassExtension*"
```

Review failures and update tests according to the new behavior. Do NOT proceed to Step 2 until all unit tests pass.

---

**Step 2: Add `delegateStackManagement + lifecycle=METHOD` Validation** (1 hour)

Add validation to `PipelineValidator` to produce an error when both `lifecycle=METHOD` and
`delegateStackManagement=true` are set (redundant configuration).

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/workflow/validation/PipelineValidator.groovy`

```groovy
// In validateMethodLifecycleConfiguration():
if (testSpec.delegateStackManagement.getOrElse(false)) {
    throw new GradleException(
        "Pipeline '${pipeline.name}' has both lifecycle=METHOD and delegateStackManagement=true. " +
        "This is redundant: lifecycle=METHOD automatically delegates compose management to the test framework. " +
        "Remove delegateStackManagement=true from your configuration."
    )
}
```

**Step 3: Create Spock Global Extension** (3 hours)

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/DockerComposeGlobalExtension.groovy`

```groovy
package com.kineticfire.gradle.docker.spock

import com.kineticfire.gradle.docker.junit.service.*  // Reuses JUnit service implementations (framework-agnostic)
import com.kineticfire.gradle.docker.junit.service.JUnitComposeService
import com.kineticfire.gradle.docker.service.ComposeService
import com.kineticfire.gradle.docker.spock.ComposeUp  // Required for annotation conflict detection
import com.kineticfire.gradle.docker.spock.LifecycleMode
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo

/**
 * Global Spock extension that auto-detects Docker Compose configuration from system properties.
 *
 * <p>This extension eliminates the need for @ComposeUp annotation when the pipeline sets
 * system properties for METHOD lifecycle. It checks for the presence of docker.compose.*
 * system properties and registers appropriate interceptors.</p>
 *
 * <p>Detection Logic:</p>
 * <ul>
 *   <li>Checks for docker.compose.lifecycle system property</li>
 *   <li>Checks for docker.compose.stack system property</li>
 *   <li>Errors if class has @ComposeUp annotation (configuration conflict)</li>
 *   <li>Only applies to test classes when all required properties are present</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> After Phase 2, test classes must NOT have @ComposeUp annotations when
 * using dockerWorkflows pipelines. All configuration comes from system properties set by the
 * pipeline. Having both creates a configuration conflict and produces an error.</p>
 */
class DockerComposeGlobalExtension implements IGlobalExtension {

    // Service dependencies (injected via constructor for testability)
    private final ComposeService composeService
    private final ProcessExecutor processExecutor
    private final FileService fileService
    private final SystemPropertyService systemPropertyService
    private final TimeService timeService

    /**
     * Default constructor - uses default service implementations.
     * Called by Spock's ServiceLoader mechanism.
     *
     * <h3>Why "JUnit" Service Implementations in Spock Extension?</h3>
     *
     * <p>Both Spock and JUnit 5 extensions share the same service implementations from
     * {@code com.kineticfire.gradle.docker.junit.service.*}. Despite the package name containing "junit",
     * these are <strong>framework-agnostic</strong> implementations that work without Gradle context.</p>
     *
     * <p><strong>The package name is a historical artifact from when these services were first created
     * for JUnit 5 support.</strong> The services implement interfaces like {@code ComposeService},
     * {@code ProcessExecutor}, {@code FileService}, etc., and have no dependencies on JUnit 5 APIs.</p>
     *
     * <p><strong>Key insight:</strong> The Gradle plugin's {@code DockerServiceImpl} and
     * {@code ExecLibraryComposeService} require Gradle context (Project, services, etc.). Test framework
     * extensions run <em>inside the test JVM</em>, which doesn't have Gradle context. Therefore, test
     * extensions need standalone implementations that execute Docker commands directly via
     * {@code ProcessBuilder} or similar—which is exactly what these "JUnit" services provide.</p>
     *
     * <p>The existing {@code DockerComposeSpockExtension} already uses these same services (see its
     * constructor), confirming they work correctly with Spock.</p>
     *
     * <p><strong>Future refactoring consideration:</strong> The {@code JUnitComposeService} could be
     * renamed to {@code StandaloneComposeService} or {@code TestComposeService} to better reflect its
     * framework-agnostic nature. This is a low-priority cosmetic change that doesn't affect functionality.</p>
     */
    DockerComposeGlobalExtension() {
        // Error handling: Service creation should not fail during extension loading.
        // If any service fails to initialize, catch the exception and log it,
        // allowing other tests to run. The extension will be non-functional but
        // won't prevent test execution entirely.
        ComposeService cs = null
        ProcessExecutor pe = null
        FileService fs = null
        SystemPropertyService sps = null
        TimeService ts = null

        try {
            cs = new JUnitComposeService()  // Framework-agnostic ComposeService (name is historical)
            pe = new DefaultProcessExecutor()
            fs = new DefaultFileService()
            sps = new DefaultSystemPropertyService()
            ts = new DefaultTimeService()
        } catch (Exception e) {
            System.err.println("WARNING: DockerComposeGlobalExtension failed to initialize services: " + e.getMessage())
            System.err.println("Docker Compose auto-detection will be disabled for this test run.")
            // Services remain null - visitSpec will return early if services are null
        }

        this.composeService = cs
        this.processExecutor = pe
        this.fileService = fs
        this.systemPropertyService = sps
        this.timeService = ts
    }

    /**
     * Constructor with injected services (for unit testing).
     */
    DockerComposeGlobalExtension(ComposeService composeService,
                                  ProcessExecutor processExecutor,
                                  FileService fileService,
                                  SystemPropertyService systemPropertyService,
                                  TimeService timeService) {
        this.composeService = composeService
        this.processExecutor = processExecutor
        this.fileService = fileService
        this.systemPropertyService = systemPropertyService
        this.timeService = timeService
    }

    @Override
    void start() {
        // Called once when Spock starts - can log initialization
    }

    @Override
    void visitSpec(SpecInfo spec) {
        // Guard: If services failed to initialize, skip this extension entirely
        if (systemPropertyService == null) {
            return  // Services not available - silently skip
        }

        // Check for system properties indicating pipeline-managed compose
        def lifecycle = systemPropertyService.getProperty('docker.compose.lifecycle', '')
        def stack = systemPropertyService.getProperty('docker.compose.stack', '')
        def files = systemPropertyService.getProperty('docker.compose.files', '')

        // Only apply if all required properties are present
        if (lifecycle.isEmpty() || stack.isEmpty() || files.isEmpty()) {
            return
        }

        // Guard: Verify all services are available before proceeding
        if (composeService == null || processExecutor == null || fileService == null || timeService == null) {
            System.err.println("WARNING: DockerComposeGlobalExtension services not fully initialized. " +
                "Skipping compose auto-detection for: " + spec.name)
            return
        }

        // ERROR if class has @ComposeUp annotation - configuration conflict
        if (spec.getAnnotation(ComposeUp) != null) {
            throw new IllegalStateException(
                "Test class '${spec.name}' has @ComposeUp annotation but compose lifecycle is being managed " +
                "automatically via system properties (docker.compose.lifecycle=${lifecycle}). " +
                "Remove the @ComposeUp annotation from the test class."
            )
        }

        // Create configuration from system properties
        def config = createConfigurationFromSystemProperties(spec)

        // Register appropriate interceptor based on lifecycle mode
        if (lifecycle.equalsIgnoreCase('method')) {
            def interceptor = new ComposeMethodInterceptor(
                config,
                composeService,
                processExecutor,
                fileService,
                systemPropertyService,
                timeService
            )
            spec.addSetupInterceptor(interceptor)
            spec.addCleanupInterceptor(interceptor)
        } else {
            def interceptor = new ComposeClassInterceptor(
                config,
                composeService,
                processExecutor,
                fileService,
                systemPropertyService,
                timeService
            )
            spec.addSetupSpecInterceptor(interceptor)
            spec.addCleanupSpecInterceptor(interceptor)
        }
    }

    @Override
    void stop() {
        // Called once when Spock finishes - can perform cleanup
    }

    private Map<String, Object> createConfigurationFromSystemProperties(SpecInfo spec) {
        // Read all configuration from system properties (no annotation fallback)
        def stackName = systemPropertyService.getProperty('docker.compose.stack', '')
        def composeFilesStr = systemPropertyService.getProperty('docker.compose.files', '')
        def lifecycleStr = systemPropertyService.getProperty('docker.compose.lifecycle', 'class')
        def projectNameBase = systemPropertyService.getProperty('docker.compose.projectName', '') ?: stackName

        // Parse compose files (comma-separated)
        def composeFiles = composeFilesStr.split(',').collect { it.trim() }.findAll { !it.isEmpty() }

        // Parse lifecycle mode
        def lifecycle = lifecycleStr.equalsIgnoreCase('method') ? LifecycleMode.METHOD : LifecycleMode.CLASS

        // Parse wait configuration
        def waitForHealthyStr = systemPropertyService.getProperty('docker.compose.waitForHealthy.services', '')
        def waitForHealthy = waitForHealthyStr ?
            waitForHealthyStr.split(',').collect { it.trim() }.findAll { !it.isEmpty() } : []

        def waitForRunningStr = systemPropertyService.getProperty('docker.compose.waitForRunning.services', '')
        def waitForRunning = waitForRunningStr ?
            waitForRunningStr.split(',').collect { it.trim() }.findAll { !it.isEmpty() } : []

        // Parse timeout and poll values
        def timeoutStr = systemPropertyService.getProperty('docker.compose.waitForHealthy.timeoutSeconds', '') ?:
                         systemPropertyService.getProperty('docker.compose.waitForRunning.timeoutSeconds', '')
        def timeoutSeconds = timeoutStr ? Integer.parseInt(timeoutStr) : 60

        def pollStr = systemPropertyService.getProperty('docker.compose.waitForHealthy.pollSeconds', '') ?:
                      systemPropertyService.getProperty('docker.compose.waitForRunning.pollSeconds', '')
        def pollSeconds = pollStr ? Integer.parseInt(pollStr) : 2

        return [
            stackName: stackName,
            composeFiles: composeFiles,
            lifecycle: lifecycle,
            projectNameBase: projectNameBase,
            waitForHealthy: waitForHealthy,
            waitForRunning: waitForRunning,
            timeoutSeconds: timeoutSeconds,
            pollSeconds: pollSeconds,
            className: spec.name ? spec.name.substring(spec.name.lastIndexOf('.') + 1) : 'UnknownSpec'
        ]
    }
}
```

**Key Design Decisions:**
- **Error** (not skip) when class has `@ComposeUp` annotation - prevents configuration conflicts
- Require ALL necessary system properties (lifecycle, stack, files) before applying
- Use same interceptors as annotation-driven extension for consistency
- Services injected via constructor for testability

**Required Config Map Keys for Spock Interceptors:**

The Spock interceptors (`ComposeMethodInterceptor`, `ComposeClassInterceptor`) receive configuration via a `Map<String, Object>`
constructor parameter. The global extension must populate this map with the following keys:

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `stackName` | `String` | Yes | Name of the compose stack |
| `composeFiles` | `List<String>` | Yes | List of compose file paths (absolute) |
| `lifecycle` | `LifecycleMode` | Yes | `LifecycleMode.METHOD` or `LifecycleMode.CLASS` |
| `projectNameBase` | `String` | Yes | Base name for unique project name generation |
| `className` | `String` | Yes | **Simple** class name (not fully qualified) for unique project name generation. Extract from `spec.name` using `spec.name.substring(spec.name.lastIndexOf('.') + 1)` since `spec.name` returns the fully qualified name (e.g., `com.example.TestSpec` → `TestSpec`). |
| `waitForHealthy` | `List<String>` | No | Services to wait for healthy status |
| `waitForRunning` | `List<String>` | No | Services to wait for running status |
| `timeoutSeconds` | `Integer` | No | Wait timeout in seconds (default: 60) |
| `pollSeconds` | `Integer` | No | Poll interval in seconds (default: 2) |

Example config map creation (used in `createConfigurationFromSystemProperties`):
```groovy
return [
    stackName: stackName,
    composeFiles: composeFiles,
    lifecycle: lifecycle,
    projectNameBase: projectNameBase,
    className: spec.name?.substring(spec.name.lastIndexOf('.') + 1) ?: 'UnknownSpec',
    waitForHealthy: waitForHealthy,
    waitForRunning: waitForRunning,
    timeoutSeconds: timeoutSeconds,
    pollSeconds: pollSeconds
]
```

**Step 4: Create Service Registration for Spock** (0.5 hours)

**File:** `plugin/src/main/resources/META-INF/services/org.spockframework.runtime.extension.IGlobalExtension`

**Content (single line, no whitespace):**
```
com.kineticfire.gradle.docker.spock.DockerComposeGlobalExtension
```

**Notes:**
- This file enables Spock's ServiceLoader to discover the global extension automatically
- No additional configuration required in user's build.gradle
- **IMPORTANT**: The `plugin/src/main/resources/` directory does NOT exist. This step requires creating the full
  directory structure:
  ```bash
  # Create the directory structure
  mkdir -p plugin/src/main/resources/META-INF/services/

  # Create the service file
  echo 'com.kineticfire.gradle.docker.spock.DockerComposeGlobalExtension' > \
      plugin/src/main/resources/META-INF/services/org.spockframework.runtime.extension.IGlobalExtension

  # Verify Gradle's processResources task includes this directory (should be automatic with standard layout)
  # The standard Groovy/Java plugin source set convention includes src/main/resources automatically
  ```
- **Verified**: As of 2025-12-04, no `META-INF/services/` files exist in `plugin/src/main/`

**Service File Format Requirements:**
- The file must contain the fully qualified class name on a single line
- **No trailing whitespace** after the class name
- **No extra newlines** at the end of the file (at most one newline character)
- **UTF-8 encoding** is required
- The class name must be exactly correct: `com.kineticfire.gradle.docker.spock.DockerComposeGlobalExtension`
- ServiceLoader is sensitive to whitespace—any deviation can cause discovery failures

**Spock IGlobalExtension Dependency:**
The `IGlobalExtension` interface is part of Spock's public extension API and is available in `spock-core`.
This project already has `spock-core` as a dependency, so no additional dependencies are required.
Verify by checking imports compile successfully:
```groovy
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo
```

**Step 5: Create JUnit 5 Global Extension** (3 hours)

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeGlobalExtension.groovy`

**Decision: Use Groovy for JUnit 5 Global Extension**

The JUnit 5 `DockerComposeGlobalExtension` will be written in Groovy (`.groovy` file) with Java-style syntax, matching
the existing JUnit 5 extensions (`DockerComposeMethodExtension.groovy`, `DockerComposeClassExtension.groovy`).

**Rationale:**

1. **Consistency**: All plugin source code is in `src/main/groovy/`; keeping JUnit 5 extensions in the same source set
   simplifies the build and avoids additional source set configuration.

2. **Groovy is a Java superset**: Any valid Java code is valid Groovy code. The JUnit 5 extensions can use pure Java
   syntax while benefiting from Groovy's flexible runtime and compilation in the same build.

3. **Simpler build**: No need to configure separate Java source sets for these specific classes.

4. **Test framework interoperability**: The extensions use JUnit 5 APIs (`BeforeEachCallback`, `AfterEachCallback`,
   `ExtensionContext`) which work identically whether compiled as Groovy or Java.

The Spock extensions use idiomatic Groovy syntax since they naturally integrate with Spock's Groovy-based DSL.

**Extension-Lifecycle Mapping:**
- `DockerComposeMethodExtension` → METHOD lifecycle (containers per test method)
- `DockerComposeClassExtension` → CLASS lifecycle (containers per test class)

**Required Dependency Note:**

The JUnit 5 global extension uses `org.junit.platform.commons.support.AnnotationSupport` for annotation detection.
This class is part of `junit-platform-commons`, which is typically available transitively through `junit-jupiter-api`.
Verify it's on the classpath by checking your dependencies:

```bash
./gradlew dependencies --configuration testRuntimeClasspath | grep junit-platform-commons
```

If not present, add explicitly:
```groovy
dependencies {
    implementation 'org.junit.platform:junit-platform-commons:1.10.0'
}
```

```groovy
package com.kineticfire.gradle.docker.junit

import com.kineticfire.gradle.docker.junit.service.*
import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.support.AnnotationSupport  // From junit-platform-commons

/**
 * Global JUnit 5 extension that auto-detects Docker Compose configuration from system properties.
 *
 * <p>This extension eliminates the need for @ExtendWith annotation when the pipeline sets
 * system properties for METHOD lifecycle.</p>
 *
 * <p>IMPORTANT: For this extension to be auto-discovered, users must enable JUnit 5's
 * extension auto-detection in junit-platform.properties:</p>
 * <pre>
 * junit.jupiter.extensions.autodetection.enabled=true
 * </pre>
 *
 * <p>Detection Logic:</p>
 * <ul>
 *   <li>Checks for docker.compose.lifecycle system property</li>
 *   <li>Checks for docker.compose.stack system property</li>
 *   <li>Errors if class has @ExtendWith for DockerCompose extensions (configuration conflict)</li>
 *   <li>Only applies when all required properties are present</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> After Phase 2, test classes must NOT have @ExtendWith annotations for
 * DockerCompose extensions when using dockerWorkflows pipelines. All configuration comes from
 * system properties set by the pipeline. Having both creates a configuration conflict and
 * produces an error.</p>
 *
 * <p><b>Extension-Lifecycle Mapping:</b></p>
 * <ul>
 *   <li>{@link DockerComposeMethodExtension} → METHOD lifecycle (containers per test method)</li>
 *   <li>{@link DockerComposeClassExtension} → CLASS lifecycle (containers per test class)</li>
 * </ul>
 */
class DockerComposeGlobalExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private final SystemPropertyService systemPropertyService

    // Delegate to existing extensions based on lifecycle mode
    // These are created lazily when needed, configured via system properties
    private DockerComposeMethodExtension methodExtension
    private DockerComposeClassExtension classExtension

    DockerComposeGlobalExtension() {
        // Error handling: If service creation fails, log and continue with null.
        // The extension will be non-functional but won't prevent test execution.
        SystemPropertyService sps = null
        try {
            sps = new DefaultSystemPropertyService()
        } catch (Exception e) {
            System.err.println("WARNING: DockerComposeGlobalExtension failed to initialize: " + e.getMessage())
            System.err.println("Docker Compose auto-detection will be disabled for this test run.")
        }
        this.systemPropertyService = sps
    }

    DockerComposeGlobalExtension(SystemPropertyService systemPropertyService) {
        this.systemPropertyService = systemPropertyService
    }

    // Guard method: Check if extension is properly initialized
    private boolean isInitialized() {
        return systemPropertyService != null
    }

    @Override
    void beforeAll(ExtensionContext context) throws Exception {
        // Guard: If services failed to initialize, skip entirely
        if (!isInitialized()) {
            return
        }
        checkForAnnotationConflict(context)
        if (!shouldApply(context)) {
            return
        }
        if (isClassLifecycle()) {
            getOrCreateClassExtension().beforeAll(context)
        }
        // METHOD lifecycle doesn't use beforeAll
    }

    @Override
    void afterAll(ExtensionContext context) throws Exception {
        if (!shouldApply(context)) {
            return
        }
        if (isClassLifecycle()) {
            getOrCreateClassExtension().afterAll(context)
        }
    }

    @Override
    void beforeEach(ExtensionContext context) throws Exception {
        if (!shouldApply(context)) {
            return
        }
        if (isMethodLifecycle()) {
            getOrCreateMethodExtension().beforeEach(context)
        }
    }

    @Override
    void afterEach(ExtensionContext context) throws Exception {
        if (!shouldApply(context)) {
            return
        }
        if (isMethodLifecycle()) {
            getOrCreateMethodExtension().afterEach(context)
        }
    }

    /**
     * Check for @ExtendWith annotation conflicts and throw error if found.
     *
     * <p>Uses JUnit 5's AnnotationSupport.findRepeatableAnnotations() to properly handle
     * all annotation patterns including multiple @ExtendWith, composed annotations,
     * and meta-annotations.</p>
     */
    private void checkForAnnotationConflict(ExtensionContext context) {
        // Check for required system properties first - only error if pipeline is managing compose
        def lifecycle = systemPropertyService.getProperty('docker.compose.lifecycle', '')
        def stack = systemPropertyService.getProperty('docker.compose.stack', '')
        def files = systemPropertyService.getProperty('docker.compose.files', '')

        if (lifecycle.isEmpty() || stack.isEmpty() || files.isEmpty()) {
            return  // Pipeline not managing compose, no conflict possible
        }

        Class<?> testClass = context.getRequiredTestClass()

        // Use AnnotationSupport to find ALL @ExtendWith annotations (handles repeatable, composed, meta)
        List<ExtendWith> extendWithAnnotations = AnnotationSupport.findRepeatableAnnotations(
            testClass, ExtendWith.class
        )

        for (ExtendWith extendWith : extendWithAnnotations) {
            for (Class<? extends Extension> ext : extendWith.value()) {
                if (ext == DockerComposeMethodExtension.class) {
                    throw new IllegalStateException(
                        "Test class '${testClass.name}' has @ExtendWith(DockerComposeMethodExtension.class) " +
                        "but compose lifecycle is being managed automatically via system properties " +
                        "(docker.compose.lifecycle=${lifecycle}). " +
                        "Remove the @ExtendWith annotation from the test class."
                    )
                }
                if (ext == DockerComposeClassExtension.class) {
                    throw new IllegalStateException(
                        "Test class '${testClass.name}' has @ExtendWith(DockerComposeClassExtension.class) " +
                        "but compose lifecycle is being managed automatically via system properties " +
                        "(docker.compose.lifecycle=${lifecycle}). " +
                        "Remove the @ExtendWith annotation from the test class."
                    )
                }
            }
        }
    }

    private boolean shouldApply(ExtensionContext context) {
        // Check for required system properties
        def lifecycle = systemPropertyService.getProperty('docker.compose.lifecycle', '')
        def stack = systemPropertyService.getProperty('docker.compose.stack', '')
        def files = systemPropertyService.getProperty('docker.compose.files', '')

        return !lifecycle.isEmpty() && !stack.isEmpty() && !files.isEmpty()
    }

    private boolean isMethodLifecycle() {
        return 'method'.equalsIgnoreCase(systemPropertyService.getProperty('docker.compose.lifecycle', ''))
    }

    private boolean isClassLifecycle() {
        return 'class'.equalsIgnoreCase(systemPropertyService.getProperty('docker.compose.lifecycle', 'class'))
    }

    private DockerComposeMethodExtension getOrCreateMethodExtension() {
        if (methodExtension == null) {
            // Create extension configured via system properties (no annotation)
            methodExtension = new DockerComposeMethodExtension()
        }
        return methodExtension
    }

    private DockerComposeClassExtension getOrCreateClassExtension() {
        if (classExtension == null) {
            // Create extension configured via system properties (no annotation)
            classExtension = new DockerComposeClassExtension()
        }
        return classExtension
    }
}
```

**Key Design Decisions:**
- **Single extension with all four callbacks** (`BeforeAllCallback`, `AfterAllCallback`, `BeforeEachCallback`,
  `AfterEachCallback`) - This is the simplest approach that mirrors the Spock global extension pattern.
  The extension conditionally invokes callbacks based on lifecycle mode detected from system properties:
  - CLASS lifecycle: uses `beforeAll`/`afterAll` (delegates to `DockerComposeClassExtension`)
  - METHOD lifecycle: uses `beforeEach`/`afterEach` (delegates to `DockerComposeMethodExtension`)
  Alternative approaches (separate extensions or splitting callbacks) were considered but rejected as unnecessary
  complexity since the extension already routes to different delegate extensions based on lifecycle mode.
- **Annotation conflict check in `beforeAll`**: JUnit 5 guarantees that `beforeAll` is called before any test
  methods execute, even for test classes that only use `@BeforeEach`/`@AfterEach` lifecycle methods. This makes
  `beforeAll` the appropriate place to detect annotation conflicts early. The check will always execute before
  any `beforeEach` callbacks, ensuring conflicts are caught before container lifecycle begins.
- **Error** (not skip) when class has `@ExtendWith` for DockerCompose extensions - prevents configuration conflicts
- Uses `AnnotationSupport.findRepeatableAnnotations()` to handle all annotation patterns (repeatable, composed, meta)
- Written in Groovy to match existing JUnit 5 extensions in this project
- Delegates to existing `DockerComposeMethodExtension` and `DockerComposeClassExtension`
- Uses `SystemPropertyService` for testability (dependency injection)
- Lazily creates delegate extensions when needed
- Requires all system properties before applying
- Clear error messages explain extension-lifecycle mapping

**Why Spock and JUnit 5 Global Extensions Have Different Injection Patterns:**

The Spock global extension (`DockerComposeGlobalExtension` in `spock/`) has a constructor with 5 injected services
(`ComposeService`, `ProcessExecutor`, `FileService`, `SystemPropertyService`, `TimeService`), while the JUnit 5
global extension (`DockerComposeGlobalExtension` in `junit/`) only injects `SystemPropertyService`.

**This asymmetry is intentional and correct:**

1. **Spock global extension creates interceptors directly**: It instantiates `ComposeMethodInterceptor` or
   `ComposeClassInterceptor` directly, passing the config map and all services to their constructors. Therefore,
   it needs all 5 services.

2. **JUnit 5 global extension delegates to existing extensions**: It lazily creates `DockerComposeMethodExtension`
   or `DockerComposeClassExtension` instances, which handle their own service creation internally. Therefore, it
   only needs `SystemPropertyService` to read configuration and delegate appropriately.

This design minimizes code duplication—the JUnit 5 delegate extensions already encapsulate their service dependencies,
so the global extension doesn't need to duplicate that knowledge.

**Annotation Detection Timing - Important Clarification:**

When a test class has both `@ExtendWith(DockerComposeMethodExtension.class)` AND JUnit 5 auto-detection is enabled,
**both extensions will be registered** by JUnit 5's extension mechanism:

1. The `@ExtendWith` annotation causes JUnit 5 to instantiate and register `DockerComposeMethodExtension`
2. ServiceLoader discovery causes JUnit 5 to instantiate and register `DockerComposeGlobalExtension`

Both extensions would receive lifecycle callbacks (`beforeAll`, `beforeEach`, etc.) and could both attempt to
start containers, causing port conflicts or duplicate operations.

**The annotation conflict check prevents this:**

```
Test Class with @ExtendWith + System Properties Set:
┌─────────────────────────────────────────────────────────────────┐
│  JUnit 5 Extension Registration (before any tests run)         │
│    ├── Register @ExtendWith(DockerComposeMethodExtension)      │
│    └── Register DockerComposeGlobalExtension (via ServiceLoader)│
├─────────────────────────────────────────────────────────────────┤
│  beforeAll() callbacks                                          │
│    ├── DockerComposeGlobalExtension.beforeAll() ◄─── FIRST     │
│    │      └── checkForAnnotationConflict()                     │
│    │            └── THROWS IllegalStateException (conflict!)   │
│    └── DockerComposeMethodExtension.beforeAll() ◄─── NEVER REACHED │
└─────────────────────────────────────────────────────────────────┘
```

**Why check in `beforeAll` (not during registration)?**
- JUnit 5's extension registration phase doesn't provide access to test class metadata
- The `ExtensionContext` with test class information is only available during lifecycle callbacks
- `beforeAll` is guaranteed to run before any `beforeEach` callbacks
- Checking early in `beforeAll` ensures the error is thrown before any compose operations begin

**Order Guarantee:** JUnit 5 does NOT guarantee a specific order for extensions registered via different
mechanisms (annotation vs ServiceLoader). However, `beforeAll` from the global extension will always run
before any test methods or `beforeEach` callbacks, so the conflict check catches the problem early regardless
of which extension's `beforeAll` runs first.

**Step 6: Create Service Registration for JUnit 5** (0.5 hours)
```
plugin/src/main/resources/META-INF/services/org.junit.jupiter.api.extension.Extension
└── Contains: com.kineticfire.gradle.docker.junit.DockerComposeGlobalExtension
```

**CRITICAL:** JUnit 5 requires explicit user opt-in for extension auto-detection. The pipeline does NOT
automatically enable this setting. See "JUnit 5 Auto-Detection: Important Implications" section below
for detailed rationale and user configuration options.

---

### JUnit 5 Delegation Pattern Clarification

> **⚠️ Implementation Clarification Required:** This section explains how the JUnit 5 global extension delegates
> to the existing extensions and how configuration flows through the system.

**The Configuration Flow:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  1. Pipeline/usesCompose() sets system properties on Test task:             │
│     - docker.compose.lifecycle=method                                       │
│     - docker.compose.stack=testStack                                        │
│     - docker.compose.files=/path/to/compose.yml                             │
│     - docker.compose.waitForHealthy.services=app                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  2. Test JVM starts with system properties available                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  3. DockerComposeGlobalExtension.beforeAll/beforeEach() called:             │
│     ├── Reads system properties via SystemPropertyService                   │
│     ├── Detects lifecycle mode (METHOD or CLASS)                            │
│     ├── Creates delegate extension: new DockerComposeMethodExtension()      │
│     └── Calls delegate.beforeEach(context)                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  4. DockerComposeMethodExtension.beforeEach() runs:                         │
│     ├── Reads same system properties internally (it already does this!)     │
│     ├── Gets stack name from docker.compose.stack                           │
│     ├── Gets compose files from docker.compose.files                        │
│     └── Starts containers via ComposeService                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key Insight: The delegate extensions ALREADY read system properties internally.**

The existing `DockerComposeMethodExtension` and `DockerComposeClassExtension` were designed to read configuration
from system properties (this is how `usesCompose()` works today). When the global extension creates a new instance
via `new DockerComposeMethodExtension()`, the delegate extension's internal methods will read the same system
properties that the global extension used to detect lifecycle mode.

**This means:**
1. The `createConfigurationFromSystemProperties()` method added in Step 1 is NOT called by the global extension
2. It exists for potential future use or direct programmatic configuration
3. The delegate extensions' default constructors + internal system property reading handle everything

**Why Step 1 still matters:**
- Step 1 removes the hardcoded `"time-server"` default that would override system properties
- Step 1 adds backward compatibility for legacy property names (see below)
- Without Step 1, the delegate extensions would use incorrect defaults when system properties are missing

**Verification During Implementation:**

Before implementing Step 5, verify that `DockerComposeMethodExtension.beforeEach()` and
`DockerComposeClassExtension.beforeAll()` correctly read these system properties:
- `docker.compose.stack` (required)
- `docker.compose.files` (required)
- `docker.compose.waitServices` or `docker.compose.waitForHealthy.services` (optional)

The implementer should trace through the existing extension code to confirm the delegation pattern works.

---

### JUnit 5 Auto-Detection: Important Implications

> ⚠️ **Important**: JUnit 5's global extension auto-detection is a JVM-wide setting that, once enabled,
> discovers and applies *all* extensions registered via `META-INF/services`—not just the Docker Compose
> extension. If the pipeline automatically enables this setting, it could unexpectedly activate third-party
> extensions the user has on their classpath but didn't intend to use, potentially causing test failures,
> performance degradation, or conflicting behaviors.

**Severity: Medium** — Affects JUnit 5 users who have other extensions on their classpath. Could cause
silent behavioral changes or test failures that are difficult to diagnose.

**Scope: JUnit 5 only** — Spock's global extension mechanism (`IGlobalExtension`) is always active and
doesn't have this opt-in requirement, so Spock users are unaffected.

**Policy Decision:** Users must explicitly opt-in to extension auto-detection. The pipeline will NOT
automatically set this system property. This ensures users:
1. Understand they are enabling discovery of ALL extensions on their classpath
2. Can review their dependencies for potential conflicts before enabling
3. Maintain full control over which extensions apply to their tests

**Required User Configuration (one of the following):**

Option A - Properties file (recommended for project-wide consistency):
```properties
# src/integrationTest/resources/junit-platform.properties
junit.jupiter.extensions.autodetection.enabled=true
```

Option B - Build configuration (for task-specific control):
```groovy
tasks.named('integrationTest') {
    systemProperty 'junit.jupiter.extensions.autodetection.enabled', 'true'
}
```

**Troubleshooting:** If you experience unexpected test behaviors after enabling auto-detection:

1. **Check `gradle dependencies` for transitive dependencies** that might register extensions

2. **Discover all registered extensions on your classpath:**
   ```bash
   # Find all JUnit 5 extension service files in your build output and dependencies
   find ~/.gradle/caches -name "*.jar" -exec sh -c \
     'unzip -l "{}" 2>/dev/null | grep -q "META-INF/services/org.junit.jupiter.api.extension.Extension" && echo "{}"' \;

   # Check a specific JAR for registered extensions
   unzip -p build/libs/your-app.jar META-INF/services/org.junit.jupiter.api.extension.Extension 2>/dev/null || echo "None found"

   # List all extensions from all JARs in a Gradle project
   ./gradlew dependencies --configuration integrationTestRuntimeClasspath | grep -E "^\+---|^\\\\---" | head -50
   ```

3. **Inspect a suspect dependency:**
   ```bash
   # Extract and view service file from a JAR
   jar tf ~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/<hash>/<artifact>.jar | grep Extension
   ```

4. Consider using `@ExtendWith` explicitly on test classes instead of auto-detection

---

**Step 7: Unit Tests** (4 hours)

**Testing Pattern Notes:**
- Follow existing test patterns in `DockerComposeSpockExtensionTest.groovy` and `DockerComposeMethodExtensionTest.groovy`
- Use `Mock(SpecInfo)` for Spock extension tests, `Mock(ExtensionContext)` for JUnit 5 extension tests
- Inject mock `SystemPropertyService` to control system property values without affecting actual system state
- Test both positive cases (interceptor registered) and negative cases (no interceptor when conditions not met)
- Use `@AutoCleanup` or explicit cleanup blocks to restore system property state

**Testing Annotation Conflict Detection:**

To properly test annotation conflict detection, use real annotated inner classes rather than mocking annotations.
This ensures the reflection-based annotation detection works correctly:

```groovy
// Define real annotated test classes as static inner classes for testing
@ComposeUp
static class AnnotatedSpockTestClass {}

static class UnannotatedTestClass {}

def "detects @ComposeUp annotation on real test class"() {
    given: "system properties indicate pipeline-managed compose"
    stubAllRequiredSystemProperties('method')

    def extension = createExtension()
    def spec = Mock(SpecInfo)
    // Use reflection to get the actual annotation from the real class
    spec.getAnnotation(ComposeUp) >> AnnotatedSpockTestClass.getAnnotation(ComposeUp)
    spec.name >> 'AnnotatedSpockTestClass'

    when:
    extension.visitSpec(spec)

    then: "error thrown because annotation conflicts with system properties"
    def e = thrown(IllegalStateException)
    e.message.contains("@ComposeUp annotation")
}
```

For JUnit 5 tests, use `AnnotationSupport.findRepeatableAnnotations()` with real annotated classes:

```groovy
@ExtendWith(DockerComposeMethodExtension)
static class JUnit5AnnotatedTestClass {}

def "detects @ExtendWith annotation on real test class"() {
    given:
    stubRequiredSystemProperties()

    def extension = new DockerComposeGlobalExtension(mockSystemPropertyService)
    def context = Mock(ExtensionContext)
    context.getRequiredTestClass() >> JUnit5AnnotatedTestClass  // Real class, not mock

    when:
    extension.beforeAll(context)

    then:
    def e = thrown(IllegalStateException)
    e.message.contains("@ExtendWith")
}
```

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/spock/DockerComposeGlobalExtensionTest.groovy`

```groovy
class DockerComposeGlobalExtensionTest extends Specification {

    def mockComposeService = Mock(ComposeService)
    def mockProcessExecutor = Mock(ProcessExecutor)
    def mockFileService = Mock(FileService)
    def mockSystemPropertyService = Mock(SystemPropertyService)
    def mockTimeService = Mock(TimeService)

    def "applies ComposeMethodInterceptor when all system properties present and no annotation"() {
        given: "system properties indicate METHOD lifecycle"
        stubAllRequiredSystemProperties('method')

        def extension = createExtension()
        def spec = Mock(SpecInfo)
        spec.getAnnotation(ComposeUp) >> null  // No annotation
        spec.name >> 'com.example.TestSpec'

        when:
        extension.visitSpec(spec)

        then: "ComposeMethodInterceptor registered for setup/cleanup (per method)"
        1 * spec.addSetupInterceptor({ it instanceof ComposeMethodInterceptor })
        1 * spec.addCleanupInterceptor({ it instanceof ComposeMethodInterceptor })
        0 * spec.addSetupSpecInterceptor(_)
        0 * spec.addCleanupSpecInterceptor(_)
    }

    def "applies ComposeClassInterceptor when lifecycle is class"() {
        given: "system properties indicate CLASS lifecycle"
        stubAllRequiredSystemProperties('class')

        def extension = createExtension()
        def spec = Mock(SpecInfo)
        spec.getAnnotation(ComposeUp) >> null
        spec.name >> 'com.example.TestSpec'

        when:
        extension.visitSpec(spec)

        then: "ComposeClassInterceptor registered for setupSpec/cleanupSpec (per class)"
        0 * spec.addSetupInterceptor(_)
        0 * spec.addCleanupInterceptor(_)
        1 * spec.addSetupSpecInterceptor({ it instanceof ComposeClassInterceptor })
        1 * spec.addCleanupSpecInterceptor({ it instanceof ComposeClassInterceptor })
    }

    def "does not apply when system properties absent"() {
        given: "no system properties set"
        mockSystemPropertyService.getProperty('docker.compose.lifecycle', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.stack', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.files', _) >> ''

        def extension = createExtension()
        def spec = Mock(SpecInfo)
        spec.getAnnotation(ComposeUp) >> null

        when:
        extension.visitSpec(spec)

        then: "no interceptor registered"
        0 * spec.addSetupInterceptor(_)
        0 * spec.addCleanupInterceptor(_)
        0 * spec.addSetupSpecInterceptor(_)
        0 * spec.addCleanupSpecInterceptor(_)
    }

    def "throws error when class has @ComposeUp annotation and system properties present"() {
        given: "system properties indicate pipeline-managed compose"
        stubAllRequiredSystemProperties('method')

        def extension = createExtension()
        def spec = Mock(SpecInfo)
        spec.getAnnotation(ComposeUp) >> Mock(ComposeUp)  // Has annotation - conflict!
        spec.name >> 'com.example.AnnotatedTestSpec'

        when:
        extension.visitSpec(spec)

        then: "IllegalStateException thrown with clear error message"
        def e = thrown(IllegalStateException)
        e.message.contains("@ComposeUp annotation")
        e.message.contains("docker.compose.lifecycle")
        e.message.contains("Remove the @ComposeUp annotation")
    }

    def "no error when class has @ComposeUp annotation but no system properties"() {
        given: "no system properties set (pipeline not managing compose)"
        mockSystemPropertyService.getProperty('docker.compose.lifecycle', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.stack', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.files', _) >> ''

        def extension = createExtension()
        def spec = Mock(SpecInfo)
        spec.getAnnotation(ComposeUp) >> Mock(ComposeUp)  // Has annotation, but no conflict

        when:
        extension.visitSpec(spec)

        then: "no error - annotation-based extension will handle this class"
        noExceptionThrown()
        0 * spec.addSetupInterceptor(_)
        0 * spec.addCleanupInterceptor(_)
    }

    // Helper methods
    private DockerComposeGlobalExtension createExtension() {
        new DockerComposeGlobalExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
    }

    private void stubAllRequiredSystemProperties(String lifecycle) {
        mockSystemPropertyService.getProperty('docker.compose.lifecycle', _) >> lifecycle
        mockSystemPropertyService.getProperty('docker.compose.stack', _) >> 'testStack'
        mockSystemPropertyService.getProperty('docker.compose.files', _) >> '/path/to/compose.yml'
        mockSystemPropertyService.getProperty('docker.compose.projectName', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.waitForHealthy.services', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.waitForRunning.services', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.waitForHealthy.timeoutSeconds', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.waitForRunning.timeoutSeconds', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.waitForHealthy.pollSeconds', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.waitForRunning.pollSeconds', _) >> ''
    }
}
```

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/junit/DockerComposeGlobalExtensionTest.groovy`

Tests for JUnit 5 extension covering:
- `shouldApply()` logic with mock `ExtensionContext`
- Lifecycle mode detection via system properties
- Delegation to correct extension:
  - `DockerComposeMethodExtension` → METHOD lifecycle
  - `DockerComposeClassExtension` → CLASS lifecycle
- **Error** when `@ExtendWith` for DockerCompose extensions is present with system properties

```groovy
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

class DockerComposeGlobalExtensionTest extends Specification {

    def mockSystemPropertyService = Mock(SystemPropertyService)

    def "shouldApply returns true when all system properties present and no annotation"() {
        given:
        stubRequiredSystemProperties()

        def extension = new DockerComposeGlobalExtension(mockSystemPropertyService)
        def context = Mock(ExtensionContext)
        context.getRequiredTestClass() >> TestClassWithoutAnnotation

        when:
        def result = extension.shouldApply(context)

        then:
        result == true
    }

    def "throws error when @ExtendWith(DockerComposeMethodExtension) present with system properties"() {
        given: "system properties indicate pipeline-managed compose"
        stubRequiredSystemProperties()

        def extension = new DockerComposeGlobalExtension(mockSystemPropertyService)
        def context = Mock(ExtensionContext)
        context.getRequiredTestClass() >> TestClassWithMethodExtension  // Has @ExtendWith(DockerComposeMethodExtension)

        when:
        extension.beforeAll(context)  // checkForAnnotationConflict called here

        then: "IllegalStateException thrown with clear error message"
        def e = thrown(IllegalStateException)
        e.message.contains("@ExtendWith(DockerComposeMethodExtension.class)")
        e.message.contains("docker.compose.lifecycle")
        e.message.contains("Remove the @ExtendWith annotation")
    }

    def "throws error when @ExtendWith(DockerComposeClassExtension) present with system properties"() {
        given: "system properties indicate pipeline-managed compose"
        stubRequiredSystemProperties()

        def extension = new DockerComposeGlobalExtension(mockSystemPropertyService)
        def context = Mock(ExtensionContext)
        context.getRequiredTestClass() >> TestClassWithClassExtension  // Has @ExtendWith(DockerComposeClassExtension)

        when:
        extension.beforeAll(context)

        then: "IllegalStateException thrown with clear error message"
        def e = thrown(IllegalStateException)
        e.message.contains("@ExtendWith(DockerComposeClassExtension.class)")
        e.message.contains("docker.compose.lifecycle")
        e.message.contains("Remove the @ExtendWith annotation")
    }

    def "no error when @ExtendWith present but no system properties"() {
        given: "no system properties set (pipeline not managing compose)"
        mockSystemPropertyService.getProperty('docker.compose.lifecycle', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.stack', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.files', _) >> ''

        def extension = new DockerComposeGlobalExtension(mockSystemPropertyService)
        def context = Mock(ExtensionContext)
        context.getRequiredTestClass() >> TestClassWithMethodExtension

        when:
        extension.beforeAll(context)

        then: "no error - annotation-based extension will handle this class"
        noExceptionThrown()
    }

    def "shouldApply returns false when system properties missing"() {
        given:
        mockSystemPropertyService.getProperty('docker.compose.lifecycle', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.stack', _) >> ''
        mockSystemPropertyService.getProperty('docker.compose.files', _) >> ''

        def extension = new DockerComposeGlobalExtension(mockSystemPropertyService)
        def context = Mock(ExtensionContext)
        context.getRequiredTestClass() >> TestClassWithoutAnnotation

        when:
        def result = extension.shouldApply(context)

        then:
        result == false
    }

    def "delegates to DockerComposeMethodExtension for method lifecycle"() {
        given:
        stubRequiredSystemProperties()
        mockSystemPropertyService.getProperty('docker.compose.lifecycle', _) >> 'method'

        def extension = new DockerComposeGlobalExtension(mockSystemPropertyService)
        def context = Mock(ExtensionContext)
        context.getRequiredTestClass() >> TestClassWithoutAnnotation

        when:
        extension.beforeEach(context)

        then: "DockerComposeMethodExtension.beforeEach invoked (handles setup)"
        noExceptionThrown()
    }

    def "delegates to DockerComposeClassExtension for class lifecycle"() {
        given:
        stubRequiredSystemProperties()
        mockSystemPropertyService.getProperty('docker.compose.lifecycle', _) >> 'class'

        def extension = new DockerComposeGlobalExtension(mockSystemPropertyService)
        def context = Mock(ExtensionContext)
        context.getRequiredTestClass() >> TestClassWithoutAnnotation

        when:
        extension.beforeAll(context)

        then: "DockerComposeClassExtension.beforeAll invoked (handles setup)"
        noExceptionThrown()
    }

    // Helper methods
    private void stubRequiredSystemProperties() {
        mockSystemPropertyService.getProperty('docker.compose.lifecycle', _) >> 'method'
        mockSystemPropertyService.getProperty('docker.compose.stack', _) >> 'testStack'
        mockSystemPropertyService.getProperty('docker.compose.files', _) >> '/path/to/compose.yml'
    }

    // Test helper classes - MUST be defined as static inner classes within the test class.
    // This ensures annotation reflection works correctly and keeps test fixtures co-located.
    // Do NOT define these in separate files.
    //
    // NOTE: Using JUnit 5 @ExtendWith annotation in a Spock test file is intentional.
    // This test file (DockerComposeGlobalExtensionTest.groovy) is a Spock Specification that
    // tests the JUnit 5 global extension. The static inner classes below are NOT actual tests -
    // they are fixtures used to verify annotation detection logic. Since we're testing JUnit 5
    // extension behavior, these fixtures must have JUnit 5 annotations. Groovy can reference
    // JUnit 5 annotations without issue; the file extension (.groovy) only affects the test
    // framework used to run THIS test class (Spock), not the annotations on inner classes.
    static class TestClassWithoutAnnotation {}

    @ExtendWith(DockerComposeMethodExtension)
    static class TestClassWithMethodExtension {}

    @ExtendWith(DockerComposeClassExtension)
    static class TestClassWithClassExtension {}
}
```

**Step 8: Functional Tests** (2 hours)

**File:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/workflow/GlobalExtensionFunctionalTest.groovy`

```groovy
class GlobalExtensionFunctionalTest extends Specification {

    @TempDir
    File projectDir

    File buildFile
    File settingsFile

    def setup() {
        settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile << "rootProject.name = 'test-project'"

        buildFile = new File(projectDir, 'build.gradle')
    }

    /**
     * IMPLEMENTATION NOTE: Service File Verification Approach
     *
     * The tests below verify that service files are correctly packaged in the plugin JAR.
     * However, the approach using `getClass().getClassLoader().getResource()` has a limitation:
     * in TestKit functional tests, the plugin is loaded separately via `GradleRunner.withPluginClasspath()`,
     * so the test's classloader may not have direct access to the plugin's service files.
     *
     * ALTERNATIVE APPROACHES (decide during implementation):
     *
     * 1. **Direct JAR inspection** (recommended):
     *    ```groovy
     *    def pluginJar = new File('build/libs/gradle-docker-plugin.jar')
     *    def jarFile = new java.util.jar.JarFile(pluginJar)
     *    def entry = jarFile.getEntry('META-INF/services/org.spockframework.runtime.extension.IGlobalExtension')
     *    assert entry != null
     *    def content = jarFile.getInputStream(entry).text
     *    assert content.contains('DockerComposeGlobalExtension')
     *    ```
     *
     * 2. **Integration test validation** (implicit verification):
     *    If scenario-10 (auto-detect without annotation) passes, it proves the service files
     *    are correctly packaged and auto-discovery works. This is the most reliable validation.
     *
     * 3. **Unit test the service file creation** (build verification):
     *    Add a unit test that verifies the source file exists in src/main/resources/META-INF/services/
     *    and contains the correct content before the JAR is built.
     *
     * The tests below use approach #1 if classloader access fails, falling back to JAR inspection.
     */

    def "plugin JAR contains Spock global extension service file"() {
        given: "the plugin classpath JARs from TestKit"
        // GradleRunner.withPluginClasspath() loads plugin JARs for testing
        // We need to verify the plugin JAR contains the service file

        when: "we examine the plugin JAR on the classpath"
        def pluginClasspath = getClass().getClassLoader().getResource(
            'META-INF/services/org.spockframework.runtime.extension.IGlobalExtension')

        then: "the Spock global extension service file exists in the plugin JAR"
        pluginClasspath != null

        and: "the service file contains the correct extension class"
        def serviceContent = pluginClasspath.text
        serviceContent.contains('com.kineticfire.gradle.docker.spock.DockerComposeGlobalExtension')
    }

    def "plugin JAR contains JUnit 5 global extension service file"() {
        given: "the plugin classpath JARs from TestKit"
        // GradleRunner.withPluginClasspath() loads plugin JARs for testing
        // We need to verify the plugin JAR contains the service file

        when: "we examine the plugin JAR on the classpath"
        def pluginClasspath = getClass().getClassLoader().getResource(
            'META-INF/services/org.junit.jupiter.api.extension.Extension')

        then: "the JUnit 5 global extension service file exists in the plugin JAR"
        pluginClasspath != null

        and: "the service file contains the correct extension class"
        def serviceContent = pluginClasspath.text
        serviceContent.contains('com.kineticfire.gradle.docker.junit.DockerComposeGlobalExtension')
    }

    def "METHOD lifecycle DSL configures integrationTest task with system properties"() {
        given: "a project with dockerWorkflows pipeline using METHOD lifecycle"
        def composeFile = new File(projectDir, 'src/integrationTest/resources/compose/app.yml')
        composeFile.parentFile.mkdirs()
        composeFile << """
            services:
              app:
                image: alpine:latest
        """

        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    testPipeline {
                        test {
                            lifecycle = WorkflowLifecycle.METHOD
                            stack = dockerOrch.composeStacks.testStack
                            testTaskName = 'integrationTest'
                        }
                    }
                }
            }

            // Task to dump system properties configured on integrationTest
            tasks.register('dumpSystemProps') {
                dependsOn 'integrationTestClasses'
                doLast {
                    def testTask = tasks.named('integrationTest').get()
                    def sysProps = testTask.systemProperties
                    println "SYSTEM_PROPS_START"
                    sysProps.each { k, v -> println "\${k}=\${v}" }
                    println "SYSTEM_PROPS_END"
                }
            }
        """

        when: "we query the system properties configured on integrationTest"
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('dumpSystemProps', '--info')
            .withPluginClasspath()
            .build()

        then: "the correct docker.compose.* system properties are set"
        result.output.contains('docker.compose.lifecycle=method')
        result.output.contains('docker.compose.stack=testStack')
        result.output.contains('docker.compose.files=')
        result.output.contains('app.yml')
    }

    def "METHOD lifecycle with delegateStackManagement=true produces error"() {
        given: "a project with redundant configuration"
        def composeFile = new File(projectDir, 'src/integrationTest/resources/compose/app.yml')
        composeFile.parentFile.mkdirs()
        composeFile << "services:\\n  app:\\n    image: alpine:latest"

        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    testPipeline {
                        test {
                            lifecycle = WorkflowLifecycle.METHOD
                            delegateStackManagement = true  // Redundant with lifecycle=METHOD
                            stack = dockerOrch.composeStacks.testStack
                            testTaskName = 'integrationTest'
                        }
                    }
                }
            }
        """

        when: "validation runs during configuration"
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('tasks', '--info')
            .withPluginClasspath()
            .buildAndFail()

        then: "error message explains the redundancy"
        result.output.contains('lifecycle=METHOD')
        result.output.contains('delegateStackManagement=true')
        result.output.contains('redundant')
    }

    /**
     * IMPLEMENTATION NOTE: Annotation Conflict Tests and ServiceLoader Dependency
     *
     * The annotation conflict tests below rely on the global extension being auto-discovered
     * via ServiceLoader during test execution. For this to work:
     *
     * 1. The plugin JAR with META-INF/services files must be on the test runtime classpath
     * 2. For Spock: Global extension mechanism (IGlobalExtension) is always active
     * 3. For JUnit 5: Extension auto-detection must be enabled via junit-platform.properties
     *
     * If these tests fail unexpectedly:
     * - Verify the service files are correctly packaged in the plugin JAR
     * - For JUnit 5 tests, ensure junit-platform.properties is in test resources with:
     *   junit.jupiter.extensions.autodetection.enabled=true
     * - Check that GradleRunner.withPluginClasspath() is properly configured
     */

    def "Spock test class with @ComposeUp annotation produces error when system properties present"() {
        given: "a project with METHOD lifecycle and a test class that has @ComposeUp annotation"
        def composeFile = new File(projectDir, 'src/integrationTest/resources/compose/app.yml')
        composeFile.parentFile.mkdirs()
        composeFile << """
            services:
              app:
                image: alpine:latest
        """

        // Create a test class with @ComposeUp annotation (this should cause an error)
        def testDir = new File(projectDir, 'src/integrationTest/groovy/com/example')
        testDir.mkdirs()
        new File(testDir, 'AnnotatedTestIT.groovy') << """
            package com.example

            import com.kineticfire.gradle.docker.spock.ComposeUp
            import spock.lang.Specification

            @ComposeUp  // This annotation conflicts with pipeline-managed compose
            class AnnotatedTestIT extends Specification {
                def "test method"() {
                    expect:
                    true
                }
            }
        """

        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            repositories {
                mavenCentral()
            }

            dependencies {
                integrationTestImplementation 'org.spockframework:spock-core:2.3-groovy-4.0'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    testPipeline {
                        test {
                            lifecycle = WorkflowLifecycle.METHOD
                            stack = dockerOrch.composeStacks.testStack
                            testTaskName = 'integrationTest'
                        }
                    }
                }
            }
        """

        when: "we run the integration test (which triggers the global extension)"
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('integrationTest', '--info')
            .withPluginClasspath()
            .buildAndFail()

        then: "error message indicates annotation conflict"
        result.output.contains('@ComposeUp annotation')
        result.output.contains('docker.compose.lifecycle')
        result.output.contains('Remove the @ComposeUp annotation')
    }

    def "JUnit 5 test class with @ExtendWith annotation produces error when system properties present"() {
        given: "a project with METHOD lifecycle and a test class that has @ExtendWith annotation"
        def composeFile = new File(projectDir, 'src/integrationTest/resources/compose/app.yml')
        composeFile.parentFile.mkdirs()
        composeFile << """
            services:
              app:
                image: alpine:latest
        """

        // Create a test class with @ExtendWith annotation (this should cause an error)
        def testDir = new File(projectDir, 'src/integrationTest/java/com/example')
        testDir.mkdirs()
        new File(testDir, 'AnnotatedJUnitIT.java') << """
            package com.example;

            import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.extension.ExtendWith;

            @ExtendWith(DockerComposeMethodExtension.class)  // This annotation conflicts with pipeline-managed compose
            class AnnotatedJUnitIT {
                @Test
                void testMethod() {
                    // Test content
                }
            }
        """

        // Enable JUnit 5 extension auto-detection
        def resourceDir = new File(projectDir, 'src/integrationTest/resources')
        resourceDir.mkdirs()
        new File(resourceDir, 'junit-platform.properties') << """
            junit.jupiter.extensions.autodetection.enabled=true
        """

        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            repositories {
                mavenCentral()
            }

            dependencies {
                integrationTestImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    testPipeline {
                        test {
                            lifecycle = WorkflowLifecycle.METHOD
                            stack = dockerOrch.composeStacks.testStack
                            testTaskName = 'integrationTest'
                        }
                    }
                }
            }
        """

        when: "we run the integration test (which triggers the global extension)"
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('integrationTest', '--info')
            .withPluginClasspath()
            .buildAndFail()

        then: "error message indicates annotation conflict"
        result.output.contains('@ExtendWith')
        result.output.contains('docker.compose.lifecycle')
        result.output.contains('Remove the @ExtendWith annotation')
    }
}
```

**Step 9: JUnit 5 Integration Test Scenario** (3 hours)

**Directory:** `plugin-integration-test/dockerWorkflows/scenario-9-method-lifecycle-junit/`

This scenario mirrors scenario-8 but uses JUnit 5 instead of Spock.

**Port Allocation Strategy:**

> **IMPORTANT:** These port allocations apply to `plugin-integration-test/dockerWorkflows/` scenarios ONLY.
> There are separate `docker/scenario-9` and `docker/scenario-10` directories that test different features
> (sourceRef mode, save/publish). Those scenarios use different port ranges (509x for registries).

Integration test scenarios in `dockerWorkflows/` manually deconflict ports to avoid collisions. Each scenario uses a
unique port range. **Complete port allocation table:**

| Scenario | Description | Host Port | Container Port |
|----------|-------------|-----------|----------------|
| scenario-1-basic | Basic workflow integration test | 9200 | 8080 |
| scenario-2-delegated-lifecycle | Delegated CLASS lifecycle (Spock) | 9201 | 8080 |
| scenario-3-failed-tests | Failed test handling | 9202 | 8080 |
| scenario-4-multiple-pipelines | Multiple pipelines | 9203 | 8080 |
| scenario-5-complex-success | Complex success actions | 9204 | 8080 |
| scenario-6-hooks | Hook execution | 9205 | 8080 |
| scenario-7-save-publish | Save and publish actions | 9206 | 8080 |
| scenario-8-method-lifecycle | METHOD lifecycle (Spock) | 9207 | 8080 |
| scenario-9-method-lifecycle-junit | METHOD lifecycle (JUnit 5) [Phase 2] | 9208 | 8080 |
| scenario-10-method-lifecycle-auto-detect | Auto-detect without annotation [Phase 2] | 9209 | 8080 |

Ports are configured in each scenario's `compose/app.yml` file and must be unique across all simultaneously-running
`dockerWorkflows/` scenarios. When adding new `dockerWorkflows/` scenarios, select the next available port in the
92xx range (9210, 9211, etc.).

**Port Conflict Troubleshooting:**

If integration tests fail with port binding errors:

1. **Check for leftover containers:**
   ```bash
   docker ps -a | grep workflow-scenario
   docker compose ls | grep workflow
   ```

2. **Check for processes using the port:**
   ```bash
   # Linux
   ss -tlnp | grep 920
   # macOS
   lsof -i :9207
   ```

3. **Force cleanup and retry:**
   ```bash
   # Stop all workflow containers
   docker ps -q --filter "name=workflow-scenario" | xargs -r docker stop
   docker ps -aq --filter "name=workflow-scenario" | xargs -r docker rm

   # Retry tests
   ./gradlew cleanAll integrationTest
   ```

4. **If ports conflict with local services:**
   - Modify the port in the scenario's `compose/app.yml`
   - Update corresponding test assertions
   - Document the port change in the scenario's README

**File:** `scenario-9-method-lifecycle-junit/app-image/build.gradle`

```groovy
// Similar to scenario-8 but for JUnit 5
plugins {
    id 'java'
    id 'com.kineticfire.gradle.docker'
}

import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

// ... docker, dockerOrch, dockerWorkflows configuration same as scenario-8 ...

dependencies {
    integrationTestImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    integrationTestImplementation 'io.rest-assured:rest-assured:5.3.2'
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${project.findProperty('plugin_version')}"
}

// Enable JUnit 5 extension auto-detection (for Phase 2)
// tasks.named('integrationTest') {
//     systemProperty 'junit.jupiter.extensions.autodetection.enabled', 'true'
// }
```

**File:** `scenario-9-method-lifecycle-junit/app-image/src/integrationTest/java/com/kineticfire/test/MethodLifecycleJUnitIT.java`

```java
package com.kineticfire.test;

import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;
import io.restassured.RestAssured;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * JUnit 5 Integration Test: dockerWorkflows Method Lifecycle
 *
 * Demonstrates METHOD lifecycle with JUnit 5 extension.
 */
@ExtendWith(DockerComposeMethodExtension.class)  // Phase 1: annotation required
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MethodLifecycleJUnitIT {

    private static String firstTestStartTime = null;
    private static String secondTestStartTime = null;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 9208;  // Different port for JUnit scenario
    }

    @Test
    @Order(1)
    @Timeout(30)  // Fail test if container not ready within 30 seconds
    void firstTestMethodGetsFreshContainer() {
        // Container should be ready - extension handles startup/health wait

        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"));

        var response = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response();

        firstTestStartTime = response.jsonPath().getString("startTime");
        int requestCount = response.jsonPath().getInt("requests");

        Assertions.assertNotNull(firstTestStartTime);
        Assertions.assertTrue(requestCount <= 5, "Fresh container should have low request count");
    }

    @Test
    @Order(2)
    @Timeout(30)  // Fail test if container not ready within 30 seconds
    void secondTestMethodGetsDifferentFreshContainer() {
        // Container should be ready - extension handles startup/health wait

        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"));

        var response = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response();

        secondTestStartTime = response.jsonPath().getString("startTime");
        int requestCount = response.jsonPath().getInt("requests");

        Assertions.assertNotNull(secondTestStartTime);
        Assertions.assertNotEquals(firstTestStartTime, secondTestStartTime,
            "Different container instance expected (method lifecycle)");
        Assertions.assertTrue(requestCount <= 5, "Fresh container should have low request count");
    }

    @Test
    @Order(3)
    @Timeout(30)  // Fail test if container not ready within 30 seconds
    void thirdTestMethodAlsoGetsFreshContainer() {
        // Container should be ready - extension handles startup/health wait

        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"));

        var response = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String thirdTestStartTime = response.jsonPath().getString("startTime");
        int requestCount = response.jsonPath().getInt("requests");

        Assertions.assertNotNull(thirdTestStartTime);
        Assertions.assertNotEquals(firstTestStartTime, thirdTestStartTime);
        Assertions.assertNotEquals(secondTestStartTime, thirdTestStartTime);
        Assertions.assertTrue(requestCount <= 5);
    }
}
```

**Directory Structure:**
```
plugin-integration-test/dockerWorkflows/scenario-9-method-lifecycle-junit/
├── build.gradle                          # Pipeline with lifecycle=METHOD for JUnit 5
├── app-image/
│   ├── build.gradle                      # JUnit 5 dependencies and configuration
│   └── src/
│       ├── main/docker/
│       │   └── Dockerfile
│       └── integrationTest/
│           ├── java/com/kineticfire/test/
│           │   └── MethodLifecycleJUnitIT.java  # JUnit 5 test with @ExtendWith
│           └── resources/compose/
│               └── app.yml
```

**Step 10: Auto-Detection Integration Test Scenario (Phase 2 Validation)** (2 hours)

**Directory:** `plugin-integration-test/dockerWorkflows/scenario-10-method-lifecycle-auto-detect/`

This scenario validates Phase 2's goal: no annotation required.

> **Note:** This integration test intentionally enables `junit.jupiter.extensions.autodetection.enabled=true`
> to validate the auto-detection feature. This is appropriate for the plugin's controlled test environment
> where we know exactly which extensions are on the classpath. This does NOT mean users should blindly
> enable auto-detection—see "JUnit 5 Auto-Detection: Important Implications" section above for guidance
> users should follow when deciding whether to enable this setting.

**Test Class (Spock):** `AutoDetectMethodLifecycleIT.groovy`

```groovy
package com.kineticfire.test

import spock.lang.Specification
import spock.lang.Stepwise

/**
 * AUTO-DETECTION TEST: NO @ComposeUp annotation!
 *
 * This test relies on the global extension auto-detection.
 * The global extension reads system properties set by the pipeline and
 * automatically applies compose lifecycle management.
 */
@Stepwise
class AutoDetectMethodLifecycleIT extends Specification {
    // Same test logic as scenario-8
    // Global extension reads system properties and applies interceptors

    def "first test method gets fresh container"() {
        // ... same as MethodLifecycleIT
    }

    def "second test method gets different fresh container"() {
        // ... same as MethodLifecycleIT
    }

    def "third test method also gets fresh container"() {
        // ... same as MethodLifecycleIT
    }
}
```

**Test Class (JUnit 5):** `AutoDetectMethodLifecycleJUnitIT.java`

```java
package com.kineticfire.test;

import org.junit.jupiter.api.*;

/**
 * AUTO-DETECTION TEST: NO @ExtendWith annotation!
 *
 * This test relies on the global extension auto-detection.
 * Requires junit.jupiter.extensions.autodetection.enabled=true
 * in junit-platform.properties.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AutoDetectMethodLifecycleJUnitIT {
    // Same test logic as scenario-9
    // Global extension reads system properties and applies lifecycle

    @Test
    @Order(1)
    void firstTestMethodGetsFreshContainer() {
        // ... same as MethodLifecycleJUnitIT
    }

    @Test
    @Order(2)
    void secondTestMethodGetsDifferentFreshContainer() {
        // ... same as MethodLifecycleJUnitIT
    }

    @Test
    @Order(3)
    void thirdTestMethodAlsoGetsFreshContainer() {
        // ... same as MethodLifecycleJUnitIT
    }
}
```

**Required File:** `src/integrationTest/resources/junit-platform.properties`

```properties
# Enable JUnit 5 extension auto-detection for global extension discovery
junit.jupiter.extensions.autodetection.enabled=true
```

**Directory Structure:**
```
plugin-integration-test/dockerWorkflows/scenario-10-method-lifecycle-auto-detect/
├── build.gradle
├── app-image/
│   ├── build.gradle
│   └── src/integrationTest/
│       ├── groovy/com/kineticfire/test/
│       │   └── AutoDetectMethodLifecycleIT.groovy  # NO @ComposeUp annotation
│       ├── java/com/kineticfire/test/
│       │   └── AutoDetectMethodLifecycleJUnitIT.java  # NO @ExtendWith annotation
│       └── resources/
│           ├── compose/app.yml
│           └── junit-platform.properties  # Enables JUnit 5 auto-detection
```

**Step 11: Remove Annotations from Existing Integration Tests** (3 hours)

Phase 2 eliminates annotation requirements. The following integration test files with `@ComposeUp` or `@ExtendWith`
annotations must be updated to remove annotations and configure via `build.gradle` instead:

**Files with `@ComposeUp` Annotations (Spock):**

> **⚠️ IMPORTANT - Pre-Implementation Verification Required:**
>
> Before implementing Step 11, the implementer MUST verify the file list is current by running:
> ```bash
> # Search for actual @ComposeUp annotations (not just comments mentioning them)
> rg "^@ComposeUp" plugin-integration-test/ --glob "*.groovy"
> # Or search for the import + annotation combination
> rg -l "import.*ComposeUp" plugin-integration-test/ --glob "*.groovy" | xargs -I{} grep -l "^@ComposeUp" {}
> ```
>
> **Why verification is needed:** The `rg "@ComposeUp"` command may return false positives from comments that mention
> the annotation (e.g., "We do NOT use @ComposeUp here..."). Manual verification ensures only files with actual
> annotations are modified.
>
> **Verified 2025-12-04:** `DelegatedClassLifecycleIT.groovy` does NOT have `@ComposeUp` annotation—it only has a
> comment explaining why the annotation is not used. This was verified by reading the actual file content.

1. `plugin-integration-test/dockerOrch/verification/lifecycle-class/app-image/src/integrationTest/groovy/com/kineticfire/test/LifecycleClassIT.groovy`
2. `plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/src/integrationTest/groovy/com/kineticfire/test/LifecycleMethodIT.groovy`
3. `plugin-integration-test/dockerOrch/examples/database-app/app-image/src/integrationTest/groovy/com/kineticfire/test/DatabaseAppExampleIT.groovy`
4. `plugin-integration-test/dockerOrch/examples/isolated-tests/app-image/src/integrationTest/groovy/com/kineticfire/test/IsolatedTestsExampleIT.groovy`
5. `plugin-integration-test/dockerOrch/examples/web-app/app-image/src/integrationTest/groovy/com/kineticfire/test/WebAppExampleIT.groovy`
6. `plugin-integration-test/dockerOrch/examples/stateful-web-app/app-image/src/integrationTest/groovy/com/kineticfire/test/StatefulWebAppExampleIT.groovy`
7. `plugin-integration-test/dockerWorkflows/scenario-8-method-lifecycle/app-image/src/integrationTest/groovy/com/kineticfire/test/MethodLifecycleIT.groovy`

> **Note:** `scenario-2-delegated-lifecycle/.../DelegatedClassLifecycleIT.groovy` does NOT have `@ComposeUp` annotation.
> It uses Gradle task dependencies via `testIntegration.usesCompose()` - no annotation removal needed.

**Files with `@ExtendWith(DockerCompose*)` Annotations (JUnit 5):**

> **Note:** Verify this list by running `rg "@ExtendWith.*DockerCompose" plugin-integration-test/ --type java` before implementation.

8. `plugin-integration-test/dockerOrch/examples/web-app-junit/app-image/src/integrationTest/java/com/kineticfire/test/WebAppJUnit5ClassIT.java`
9. `plugin-integration-test/dockerOrch/examples/isolated-tests-junit/app-image/src/integrationTest/java/com/kineticfire/test/IsolatedTestsJUnit5MethodIT.java`

**Total: 9 files** (7 Spock + 2 JUnit 5)

**No unit tests or functional tests are affected** - these files do not use `@ComposeUp` or `@ExtendWith(DockerCompose*)` annotations.

For each Spock file:
1. Remove the `@ComposeUp` annotation from the test class
2. Configure compose lifecycle via `usesCompose()` in the scenario's `build.gradle`

For each JUnit 5 file:
1. Remove the `@ExtendWith(DockerComposeMethodExtension.class)` or `@ExtendWith(DockerComposeClassExtension.class)` annotation
2. Configure compose lifecycle via `usesCompose()` in the scenario's `build.gradle`
3. Add `junit-platform.properties` with `junit.jupiter.extensions.autodetection.enabled=true`

---

**Step 12: Documentation** (2 hours)

**File:** `docs/usage/usage-docker-workflows.md`

Add section:

```markdown
## Method Lifecycle: Auto-Detection (Phase 2)

Starting with version X.X.X, the `@ComposeUp` (Spock) and `@ExtendWith` (JUnit 5) annotations
are optional when using `lifecycle = WorkflowLifecycle.METHOD` in dockerWorkflows pipelines.

The pipeline sets system properties that are automatically detected by global test framework
extensions:

### Spock (No Configuration Required)

```groovy
// No annotation needed - system properties are auto-detected
class MyIntegrationTest extends Specification {
    def "test method 1"() { /* gets fresh containers */ }
    def "test method 2"() { /* gets fresh containers */ }
}
```

### JUnit 5 (Requires Extension Auto-Detection)

JUnit 5 requires explicit opt-in for extension auto-detection.

> ⚠️ **Important**: Enabling `junit.jupiter.extensions.autodetection.enabled` activates ALL extensions
> registered via `META-INF/services` on your classpath, not just Docker Compose. This is a JVM-wide
> setting. Review your dependencies before enabling this setting. If you experience unexpected test
> behaviors, check for conflicting extensions from transitive dependencies.
>
> See "JUnit 5 Auto-Detection: Important Implications" in the Phase 2 implementation section for
> detailed guidance on when to enable this setting and troubleshooting steps.

Add to `src/integrationTest/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

Or configure in build.gradle:

```groovy
tasks.named('integrationTest') {
    systemProperty 'junit.jupiter.extensions.autodetection.enabled', 'true'
}
```

Then your test class needs no annotation:

```java
// No @ExtendWith needed - system properties are auto-detected
class MyIntegrationTest {
    @Test
    void testMethod1() { /* gets fresh containers */ }
    @Test
    void testMethod2() { /* gets fresh containers */ }
}
```

### No Backward Compatibility for Annotations

After Phase 2 implementation, **annotations are no longer supported**. The global extension will detect when a test
class has `@ComposeUp` (Spock) or `@ExtendWith(DockerComposeMethodExtension.class)` (JUnit 5) and **produce an error**
instructing the user to remove the annotation and configure via `build.gradle` instead.

This ensures a single, consistent way to configure compose lifecycle across all tests.

### Decision Tree: When Is Annotation Required?

| Scenario | Spock | JUnit 5 |
|----------|-------|---------|
| Using `dockerWorkflows` with `lifecycle = METHOD` (Phase 2+) | No annotation (error if present) | No annotation (error if present)* |
| Using `dockerOrch` + `testIntegration` | Configure via `usesCompose()` in build.gradle | Configure via `usesCompose()` in build.gradle |
| Using compose extensions standalone | Not supported—use `dockerWorkflows` or `dockerOrch` | Not supported—use `dockerWorkflows` or `dockerOrch` |

*JUnit 5 requires `junit.jupiter.extensions.autodetection.enabled=true`
```

**File:** `docs/usage/spock-junit-test-extensions.md`

Add section on global extension auto-detection with same content as above.

---

### Phase 2 Implementation Checklist

| Step | Task | Hours | Status |
|------|------|-------|--------|
| 1 | Refactor JUnit 5 extensions to remove hardcoded defaults | 2-3 | Pending |
| 2 | Add `delegateStackManagement + lifecycle=METHOD` validation to PipelineValidator | 1 | Pending |
| 3 | Create Spock `DockerComposeGlobalExtension` | 3 | Pending |
| 4 | Create Spock service registration file | 0.5 | Pending |
| 5 | Create JUnit 5 `DockerComposeGlobalExtension` (Groovy) | 3 | Pending |
| 6 | Create JUnit 5 service registration file | 0.5 | Pending |
| 7 | Unit tests for global extensions | 4 | Pending |
| 8 | Functional tests for service registration | 2 | Pending |
| 9 | scenario-9: JUnit 5 method lifecycle integration test (port 9208) | 3 | Pending |
| 10 | scenario-10: Auto-detect integration test (port 9209, validates Phase 2) | 2 | Pending |
| 11 | Remove annotations from existing tests (9 files: 7 Spock + 2 JUnit 5) & update build.gradle | 3 | Pending |
| 12 | Documentation updates | 2 | Pending |
| **Total** | | **26-27** | |

**Phase 2 Total: ~26-27 hours (3-4 days)**

---

## Existing Infrastructure (No Changes Required)

The following components already exist and work correctly. No modifications needed for Phase 1:

| Component | Location | Status |
|-----------|----------|--------|
| `LifecycleMode` enum | `plugin/src/main/groovy/.../spock/LifecycleMode.groovy` | ✅ Complete |
| `ComposeMethodInterceptor` | `plugin/src/main/groovy/.../spock/ComposeMethodInterceptor.groovy` | ✅ Complete (388 lines) |
| `DockerComposeMethodExtension` | `plugin/src/main/groovy/.../junit/DockerComposeMethodExtension.groovy` | ✅ Complete |
| `DockerComposeSpockExtension` | `plugin/src/main/groovy/.../spock/DockerComposeSpockExtension.groovy` | ✅ Complete (482 lines) |
| System property reading | In all interceptors/extensions | ✅ Complete |
| `docker.compose.lifecycle` property | Read by extensions | ✅ Complete |
| `TestIntegrationExtension.usesCompose()` | `plugin/src/main/groovy/.../extension/TestIntegrationExtension.groovy` | ✅ Supports 'class' and 'method' |
| `delegateStackManagement` property | `TestStepSpec.groovy` | ✅ Complete |
| TaskLookup abstraction | `plugin/src/main/groovy/.../workflow/TaskLookup.groovy` | ✅ Config cache safe |

---

## Files to Modify/Create

### Phase 1 Production Code (COMPLETE)

| File | Change Type | Description |
|------|-------------|-------------|
| `WorkflowLifecycle.groovy` | Create | New enum (CLASS, METHOD) |
| `TestStepSpec.groovy` | Modify | Add `lifecycle` property with convention `CLASS` |
| `TestStepExecutor.groovy` | Modify | Handle METHOD lifecycle: set system properties, skip Gradle compose tasks |
| `PipelineValidator.groovy` | Modify | Add validation for METHOD lifecycle configuration |

### Phase 1 Test Code (COMPLETE)

| File | Change Type | Description |
|------|-------------|-------------|
| `WorkflowLifecycleTest.groovy` | Create | Tests for enum |
| `TestStepSpecTest.groovy` | Modify | Tests for lifecycle property |
| `TestStepExecutorTest.groovy` | Modify | Tests for method lifecycle handling |
| `PipelineValidatorTest.groovy` | Modify | Tests for lifecycle validation |
| `MethodLifecycleFunctionalTest.groovy` | Create | DSL parsing tests |

### Phase 1 Integration Tests (COMPLETE)

| Directory | Change Type | Description |
|-----------|-------------|-------------|
| `scenario-8-method-lifecycle/` | Create | Method lifecycle with Spock |

### Phase 1 Documentation (COMPLETE)

| File | Change Type | Description |
|------|-------------|-------------|
| `usage-docker-workflows.md` | Modify | Document lifecycle option |
| `workflow-cannot-method-lifecycle.md` | Modify | Update to reflect new capability |
| `dockerWorkflows/README.md` | Modify | Add scenario-8 to table |

### Phase 2 Production Code (PENDING)

| File | Change Type | Description |
|------|-------------|-------------|
| `DockerComposeMethodExtension.groovy` | Modify | Remove `"time-server"` hardcoded default; add `createConfigurationFromSystemProperties(ExtensionContext)` |
| `DockerComposeClassExtension.groovy` | Modify | Remove `"time-server"` hardcoded default; add `createConfigurationFromSystemProperties(ExtensionContext)` |
| `ComposeMethodInterceptor.groovy` | None | ✅ Already ready - receives all config via constructor parameter |
| `ComposeClassInterceptor.groovy` | None | ✅ Already ready - receives all config via constructor parameter |
| `spock/DockerComposeGlobalExtension.groovy` | Create | Spock global extension implementing `IGlobalExtension` |
| `junit/DockerComposeGlobalExtension.groovy` | Create | JUnit 5 global extension (Groovy for consistency) |
| `plugin/src/main/resources/META-INF/services/org.spockframework.runtime.extension.IGlobalExtension` | Create | Spock service registration (new directory structure) |
| `plugin/src/main/resources/META-INF/services/org.junit.jupiter.api.extension.Extension` | Create | JUnit 5 service registration |

### Phase 2 Test Code (PENDING)

| File | Change Type | Description |
|------|-------------|-------------|
| `spock/DockerComposeGlobalExtensionTest.groovy` | Create | Tests for Spock global extension |
| `junit/DockerComposeGlobalExtensionTest.groovy` | Create | Tests for JUnit 5 global extension |
| `GlobalExtensionFunctionalTest.groovy` | Create | Functional tests for service registration |

### Phase 2 Integration Tests (PENDING)

| Directory | Change Type | Description |
|-----------|-------------|-------------|
| `scenario-9-method-lifecycle-junit/` | Create | Method lifecycle with JUnit 5 (port 9208) |
| `scenario-10-method-lifecycle-auto-detect/` | Create | Auto-detection without annotation (port 9209) |

### Phase 2 Existing Test Updates (PENDING)

| File | Change Type | Description |
|------|-------------|-------------|
| `dockerOrch/verification/lifecycle-class/.../LifecycleClassIT.groovy` | Modify | Remove `@ComposeUp`, configure via `build.gradle` |
| `dockerOrch/verification/lifecycle-method/.../LifecycleMethodIT.groovy` | Modify | Remove `@ComposeUp`, configure via `build.gradle` |
| `dockerOrch/examples/database-app/.../DatabaseAppExampleIT.groovy` | Modify | Remove `@ComposeUp`, configure via `build.gradle` |
| `dockerOrch/examples/isolated-tests/.../IsolatedTestsExampleIT.groovy` | Modify | Remove `@ComposeUp`, configure via `build.gradle` |
| `dockerOrch/examples/web-app/.../WebAppExampleIT.groovy` | Modify | Remove `@ComposeUp`, configure via `build.gradle` |
| `dockerOrch/examples/stateful-web-app/.../StatefulWebAppExampleIT.groovy` | Modify | Remove `@ComposeUp`, configure via `build.gradle` |
| `dockerWorkflows/scenario-8-method-lifecycle/.../MethodLifecycleIT.groovy` | Modify | Remove `@ComposeUp`, configure via `build.gradle` |
| `dockerOrch/examples/web-app-junit/.../WebAppJUnit5ClassIT.java` | Modify | Remove `@ExtendWith`, configure via `build.gradle` |
| `dockerOrch/examples/isolated-tests-junit/.../IsolatedTestsJUnit5MethodIT.java` | Modify | Remove `@ExtendWith`, configure via `build.gradle` |

**Total: 9 files** (7 Spock + 2 JUnit 5)

**Note:** `dockerWorkflows/scenario-2-delegated-lifecycle/.../DelegatedClassLifecycleIT.groovy` is NOT included because
it does not use `@ComposeUp` - it relies on Gradle task dependencies via `testIntegration.usesCompose()` in build.gradle.

**Note:** No unit tests or functional tests are affected by annotation removal.

### Phase 2 Documentation (PENDING)

| File | Change Type | Description |
|------|-------------|-------------|
| `usage-docker-workflows.md` | Modify | Document auto-detection (annotation produces error) |
| `spock-junit-test-extensions.md` | Modify | Document global extension auto-detection, deprecate annotations |
| `dockerWorkflows/README.md` | Modify | Add scenario-9 and scenario-10 to table |

---

## Relationship Between `lifecycle` and `delegateStackManagement`

| `lifecycle` | `delegateStackManagement` | Pipeline Compose Management | Who Handles Compose |
|-------------|---------------------------|-----------------------------|--------------------|
| `CLASS` (default) | `false` (default) | Pipeline calls `composeUp`/`composeDown` tasks once | Pipeline via Gradle tasks |
| `CLASS` | `true` | Pipeline skips compose tasks | `testIntegration` via task dependencies |
| `METHOD` | not set | Pipeline skips compose tasks | Test framework extension per method |
| `METHOD` | `true` | **ERROR** - redundant configuration | N/A |

**Key behavior**: When `lifecycle = METHOD`, compose management delegation is automatic—Gradle tasks cannot execute
per-method, so the test framework extension handles it. Setting `delegateStackManagement = true` with `lifecycle = METHOD`
is redundant and produces an error to prevent configuration confusion. Choose one approach:
- Use `lifecycle = METHOD` for per-method container isolation (recommended for test isolation)
- Use `delegateStackManagement = true` with default `lifecycle = CLASS` for class-level delegation to `testIntegration`

---

## Clarification: `usesCompose()` vs `dockerWorkflows` Lifecycle Configuration

### Two Configuration Paths

There are two distinct paths to configure compose lifecycle for tests:

1. **`testIntegration.usesCompose()`** (via `dockerOrch` DSL)
   - Used when tests are NOT part of a `dockerWorkflows` pipeline
   - Directly configures a test task to use compose with specified lifecycle
   - Example: `testIntegration.usesCompose(tasks.integrationTest, 'testStack', 'method')`

2. **`dockerWorkflows` pipeline `lifecycle` property** (via `dockerWorkflows` DSL)
   - Used when tests ARE part of a `dockerWorkflows` pipeline
   - Pipeline orchestrates build → test → conditional actions
   - Example: `test { lifecycle = WorkflowLifecycle.METHOD; stack = ... }`

### System Property Convergence

Both paths converge on the **same system properties** at test runtime:

| System Property | Set By `usesCompose()` | Set By `dockerWorkflows` |
|-----------------|------------------------|--------------------------|
| `docker.compose.lifecycle` | ✅ | ✅ |
| `docker.compose.stack` | ✅ | ✅ |
| `docker.compose.files` | ✅ | ✅ |
| `docker.compose.projectName` | ✅ | ✅ |
| `docker.compose.waitForHealthy.services` | ✅ | ✅ |
| `docker.compose.waitForRunning.services` | ✅ | ✅ |

### Global Extensions Read from Either Path

The Phase 2 global extensions (`DockerComposeGlobalExtension` for Spock and JUnit 5) detect compose configuration
**purely from system properties**. They don't know or care whether the properties were set by:
- `testIntegration.usesCompose()` in `dockerOrch` context
- `TestStepExecutor` in `dockerWorkflows` context
- Manual `systemProperty()` calls in `build.gradle`

This means Phase 2's global extensions work seamlessly with both configuration approaches.

### When to Use Which Approach

| Use Case | Recommended Approach |
|----------|---------------------|
| Standalone integration tests (no pipeline) | `testIntegration.usesCompose()` |
| Pipeline with build → test → tag/publish | `dockerWorkflows` with `lifecycle` |
| Need conditional post-test actions | `dockerWorkflows` (only option) |
| Simple compose + test without build artifacts | `testIntegration.usesCompose()` |

### Phase 2 Impact on Existing Tests

After Phase 2, tests using `testIntegration.usesCompose()` will:
- **Continue to work** with the global extensions (system properties are set the same way)
- **NOT require annotation removal** if they don't have annotations currently
- **Require annotation removal** if they do have `@ComposeUp` or `@ExtendWith` (error on conflict)

---

## Canonical System Property Names

The following system properties are used for compose configuration. These names are canonical and must be used
consistently across all configuration paths (`usesCompose()`, `dockerWorkflows`, and global extensions):

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `docker.compose.lifecycle` | `String` | Yes | Either `"class"` or `"method"` |
| `docker.compose.stack` | `String` | Yes | Name of the compose stack |
| `docker.compose.files` | `String` | Yes | Comma-separated list of compose file paths (absolute) |
| `docker.compose.project` | `String` | No | Project name for compose (legacy property) |
| `docker.compose.projectName` | `String` | No | Base name for unique project name generation |
| `docker.compose.waitServices` | `String` | No | Comma-separated service names to wait for (JUnit 5 extensions) |
| `docker.compose.waitForHealthy.services` | `String` | No | Comma-separated service names to wait for healthy |
| `docker.compose.waitForHealthy.timeoutSeconds` | `String` | No | Timeout in seconds (default: 60) |
| `docker.compose.waitForHealthy.pollSeconds` | `String` | No | Poll interval in seconds (default: 2) |
| `docker.compose.waitForRunning.services` | `String` | No | Comma-separated service names to wait for running |
| `docker.compose.waitForRunning.timeoutSeconds` | `String` | No | Timeout in seconds (default: 60) |
| `docker.compose.waitForRunning.pollSeconds` | `String` | No | Poll interval in seconds (default: 2) |

### System Property Name Inconsistency (Phase 2 Pre-Requisite)

> ⚠️ **IMPORTANT**: Before implementing Phase 2 global extensions, the system property names must be unified.
> There is currently an inconsistency between what different components use:

| Component | Wait Services Property | Notes |
|-----------|----------------------|-------|
| `TestIntegrationExtension.usesCompose()` | `docker.compose.waitForHealthy.services` | Canonical name |
| `TestStepExecutor` (Phase 1) | `docker.compose.waitForHealthy.services` | Matches canonical |
| `DockerComposeMethodExtension` (JUnit 5) | `docker.compose.waitServices` | **Legacy name** |
| `DockerComposeClassExtension` (JUnit 5) | `docker.compose.waitServices` | **Legacy name** |

**Phase 2 Step 1 must address this** by updating the JUnit 5 extensions to read BOTH property names (canonical and
legacy) for backward compatibility, with canonical taking precedence:

```java
// In DockerComposeMethodExtension and DockerComposeClassExtension:
// Read canonical name first, fall back to legacy for backward compatibility
String waitServices = systemPropertyService.getProperty("docker.compose.waitForHealthy.services", "");
if (waitServices == null || waitServices.isEmpty()) {
    // Legacy property name - for backward compatibility with older configurations
    waitServices = systemPropertyService.getProperty("docker.compose.waitServices", "");
}
```

This approach:
1. **Maintains backward compatibility** with existing configurations that use `docker.compose.waitServices`
2. **Prioritizes canonical names** so new configurations use the consistent naming convention
3. **Requires no migration** for existing users who already have working configurations

**Verified in codebase**: The property names shown above are verified against:
- `TestIntegrationExtension.groovy:161-169` (sets `docker.compose.stack`, `docker.compose.lifecycle`, `docker.compose.files`)
- `DockerComposeMethodExtension.groovy:57` (reads `docker.compose.waitServices`)
- `TestStepExecutor.groovy:326-332` (sets `docker.compose.lifecycle`, `docker.compose.stack`, `docker.compose.files`)

---

## Risk Mitigation

### Phase 1 Risks

| Risk | Mitigation |
|------|------------|
| Port conflicts during parallel test execution | Validate and ERROR when `lifecycle = METHOD` and parallel enabled |
| System property not reaching test JVM | Use `Test.systemProperty()` API, document in troubleshooting |
| User forgets annotation on test class | Document requirement clearly; Phase 2 eliminates this with global extensions |
| Performance degradation | Document that method lifecycle is slower; recommend class for speed |

### Phase 2 Risks

| Risk | Mitigation |
|------|------------|
| Global extension applies to wrong tests | Require ALL system properties before applying |
| JUnit 5 auto-detection not enabled | Document requirement clearly with prominent warning; users must explicitly opt-in (pipeline does NOT auto-enable) |
| JUnit 5 auto-detection enables unwanted extensions | Document that auto-detection is JVM-wide; advise users to review classpath dependencies before enabling; provide troubleshooting steps |
| User has existing annotated tests | Global extension produces error with clear migration instructions |
| Global extension registered but no compose config | Extension checks for all required system properties before applying |
| Port conflicts between integration test scenarios | Manual port allocation strategy documented (92xx range) |

---

## Phase 2 Implementation Details

### Conflict Detection Logic

The global extensions must detect annotation conflicts and produce errors with clear migration instructions.

**Spock `DockerComposeGlobalExtension` conflict detection:**
```groovy
void visitSpec(SpecInfo spec) {
    // Check if system properties are present (indicates global extension should handle)
    if (!hasRequiredSystemProperties()) {
        return  // No system properties = don't apply, let annotation-based extension handle if present
    }

    // Check for conflicting @ComposeUp annotation on the spec class
    Class<?> specClass = spec.reflection
    if (specClass.isAnnotationPresent(ComposeUp)) {
        throw new IllegalStateException(
            "Annotation conflict detected on ${specClass.name}.\n" +
            "The @ComposeUp annotation is present but system properties indicate global extension should handle.\n" +
            "Remove the @ComposeUp annotation from ${specClass.simpleName} - configuration is now via build.gradle."
        )
    }

    // Apply global extension logic...
}
```

**JUnit 5 `DockerComposeGlobalExtension` conflict detection:**
```groovy
void beforeAll(ExtensionContext context) {
    // Check if system properties are present
    if (!hasRequiredSystemProperties()) {
        return
    }

    // Check for conflicting @ExtendWith annotations with compose extensions
    Class<?> testClass = context.requiredTestClass
    ExtendWith extendWithAnnotation = testClass.getAnnotation(ExtendWith)

    if (extendWithAnnotation != null) {
        List<Class<?>> conflictingExtensions = Arrays.stream(extendWithAnnotation.value())
            .filter { ext ->
                ext == DockerComposeMethodExtension || ext == DockerComposeClassExtension
            }
            .collect(Collectors.toList())

        if (!conflictingExtensions.isEmpty()) {
            throw new IllegalStateException(
                "Annotation conflict detected on ${testClass.name}.\n" +
                "@ExtendWith contains Docker Compose extensions but system properties indicate global extension should handle.\n" +
                "Remove DockerComposeMethodExtension/DockerComposeClassExtension from @ExtendWith - configuration is now via build.gradle."
            )
        }
    }
}
```

### Handling `@ExtendWith` with Multiple Extensions

JUnit 5's `@ExtendWith` can contain multiple extensions. The conflict detection must specifically check for
`DockerComposeMethodExtension` or `DockerComposeClassExtension` among the extensions, not just the presence
of any `@ExtendWith` annotation.

**Example of valid usage (no conflict):**
```java
@ExtendWith(MockitoExtension.class)  // Other extensions are fine
class MyTest { ... }
```

**Example of conflict (produces error):**
```java
@ExtendWith({MockitoExtension.class, DockerComposeMethodExtension.class})  // Error!
class MyTest { ... }
```

### Port Allocation for Scenarios 9 and 10

The following ports are allocated for integration test scenarios:

| Scenario | Port | Description |
|----------|------|-------------|
| scenario-1 | 9200 | Basic build workflow |
| scenario-2 | 9201 | Delegated class lifecycle |
| scenario-3 | 9202 | On-success tagging |
| scenario-4 | 9203 | On-failure handling |
| scenario-5 | 9204 | Always step |
| scenario-6 | 9205 | Save workflow |
| scenario-7 | 9206 | Publish workflow |
| scenario-8 | 9207 | Method lifecycle (Spock) |
| **scenario-9** | **9208** | **Method lifecycle (JUnit 5)** |
| **scenario-10** | **9209** | **Auto-detection without annotation** |

**Verification required:** Before implementation, verify ports 9208 and 9209 are not in use by checking:
```bash
grep -r "920[89]" plugin-integration-test/
```

### JUnit 5 Auto-Detection Complexity Warning

> ⚠️ **IMPORTANT: JVM-Wide Implications of JUnit 5 Auto-Detection**
>
> When `junit.jupiter.extensions.autodetection.enabled=true` is set:
> - Auto-detection applies to **ALL tests on the JVM classpath**, not just your project
> - In multi-module projects, other modules may unexpectedly have extensions applied
> - Third-party libraries that include JUnit 5 extensions will be auto-registered
>
> **Recommendations:**
> 1. Only enable auto-detection in projects that **exclusively** use Docker Compose for testing
> 2. For multi-module projects, enable auto-detection **per-module** via `junit-platform.properties`
> 3. Review your classpath dependencies before enabling to avoid unexpected behaviors
>
> **Troubleshooting:**
> - If tests fail unexpectedly after enabling auto-detection, check for extension conflicts
> - Use `--debug` flag to see which extensions are being loaded
> - Temporarily disable auto-detection to isolate the issue

### ThreadLocal Cleanup Verification

Both JUnit 5 extensions (`DockerComposeMethodExtension`, `DockerComposeClassExtension`) use `ThreadLocal` storage
for `uniqueProjectName` and `composeState`. When used as global extensions via ServiceLoader, proper cleanup
is critical to prevent memory leaks in long-running test suites.

**Verification checklist:**
- [ ] `ThreadLocal.remove()` is called in `afterEach()` / `afterAll()` for all ThreadLocal variables
- [ ] Cleanup occurs even if exceptions are thrown during test execution
- [ ] Unit tests verify ThreadLocal cleanup with mock verification

**Current implementation (verified):**
```groovy
// In afterEach/afterAll:
uniqueProjectName.remove()
composeState.remove()
```

### Error Message Templates

The following error messages should be used for consistent user experience:

**1. Annotation Conflict - Spock:**
```
ERROR: Docker Compose Extension Conflict

The @ComposeUp annotation is present on class 'MyIntegrationTest' but Docker Compose
configuration is being provided via system properties (build.gradle configuration).

This typically happens when:
- You're using dockerWorkflows with lifecycle=METHOD
- You're using testIntegration.usesCompose() in build.gradle

To fix this error:
1. Remove the @ComposeUp annotation from your test class
2. Configuration is now handled automatically via build.gradle

Before:
  @ComposeUp
  class MyIntegrationTest extends Specification { ... }

After:
  // No annotation needed - global extension auto-detects from system properties
  class MyIntegrationTest extends Specification { ... }
```

**2. Annotation Conflict - JUnit 5:**
```
ERROR: Docker Compose Extension Conflict

The @ExtendWith annotation on class 'MyIntegrationTest' contains DockerComposeMethodExtension
or DockerComposeClassExtension, but Docker Compose configuration is being provided via
system properties (build.gradle configuration).

To fix this error:
1. Remove DockerComposeMethodExtension or DockerComposeClassExtension from @ExtendWith
2. Keep other extensions (e.g., MockitoExtension) if needed
3. Configuration is now handled automatically via build.gradle

Before:
  @ExtendWith({MockitoExtension.class, DockerComposeMethodExtension.class})
  class MyIntegrationTest { ... }

After:
  @ExtendWith(MockitoExtension.class)  // Keep other extensions, remove compose ones
  class MyIntegrationTest { ... }
```

**3. Missing System Properties:**
```
ERROR: Docker Compose configuration incomplete

Required system properties for Docker Compose lifecycle are missing:
- docker.compose.lifecycle: [present/MISSING]
- docker.compose.stack: [present/MISSING]
- docker.compose.files: [present/MISSING]

Ensure your build.gradle configures the test task with usesCompose() or
your dockerWorkflows pipeline includes a properly configured test step.

Example build.gradle configuration:
  testIntegration.usesCompose(tasks.integrationTest, 'myStack', 'method')
```

**4. Redundant Configuration (delegateStackManagement + lifecycle=METHOD):**
```
ERROR: Redundant configuration detected

Both 'lifecycle = METHOD' and 'delegateStackManagement = true' are set.

When lifecycle=METHOD, the test framework extension automatically handles container
lifecycle per test method. Setting delegateStackManagement=true is redundant because:
- lifecycle=METHOD already delegates compose management to the test framework
- delegateStackManagement=true is for class-level delegation to testIntegration

Choose ONE approach:
1. Use 'lifecycle = METHOD' for per-method container isolation (test framework handles compose)
2. Use 'delegateStackManagement = true' with default CLASS lifecycle (testIntegration handles compose)

To fix: Remove 'delegateStackManagement = true' from your test step configuration.
```

### Migration Example

**Before Phase 2 (annotation required):**
```groovy
// build.gradle
dockerWorkflows {
    pipelines {
        myPipeline {
            test {
                lifecycle = WorkflowLifecycle.METHOD
                stack = 'testStack'
            }
        }
    }
}

// MyIntegrationTest.groovy
@ComposeUp  // Required annotation to trigger Spock extension
class MyIntegrationTest extends Specification {
    def "my test"() { ... }
}
```

**After Phase 2 (annotation removed, global extension auto-detects):**
```groovy
// build.gradle - same configuration
dockerWorkflows {
    pipelines {
        myPipeline {
            test {
                lifecycle = WorkflowLifecycle.METHOD
                stack = 'testStack'
            }
        }
    }
}

// MyIntegrationTest.groovy
// No annotation needed - global extension auto-detects from system properties set by pipeline
class MyIntegrationTest extends Specification {
    def "my test"() { ... }
}
```

**JUnit 5 Migration:**
```java
// Before Phase 2
@ExtendWith(DockerComposeMethodExtension.class)
class MyIntegrationTest { ... }

// After Phase 2
// No @ExtendWith needed for compose - global extension handles it
class MyIntegrationTest { ... }

// If you have other extensions, keep them:
@ExtendWith(MockitoExtension.class)  // Other extensions are still fine
class MyIntegrationTest { ... }
```

---

### Backward Compatibility Note

**No backward compatibility considerations or migration guide are required for Phase 2.**

**Rationale:** The plugin is not yet published and has no external users. This is a conscious design decision—all
breaking changes can be made without maintaining deprecated APIs, migration guides, or transition periods. When the
plugin is eventually published, Phase 2 will already be complete and users will only ever know the final design.

**Breaking Changes Permitted (No Migration Guide Needed):**
- Test class annotations (`@ComposeUp`, `@ExtendWith`) will immediately produce errors when system properties are present
- No deprecation warnings or gradual transition period is needed
- No documentation of "before/after" migration steps required
- All integration tests in this repository will be updated as part of Phase 2 implementation

**Decision Date:** 2025-12-04

---

## Gradle 9/10 Configuration Cache Compatibility

All proposed changes maintain configuration cache compatibility:

1. **WorkflowLifecycle enum**: Enum values are serializable
2. **TestStepSpec.lifecycle**: Uses `Property<WorkflowLifecycle>` (lazy evaluation, serializable)
3. **System properties**: Set via `Test.systemProperty()` which is configuration cache safe
4. **TestStepExecutor**: No Project references captured; uses TaskLookup abstraction
5. **Test extensions**: Read system properties at execution time, not configuration time

---

## Lifecycle Options Summary

After implementation, the following lifecycle options will be available:

| Lifecycle | Containers Start | Containers Stop | Use Case |
|-----------|------------------|-----------------|----------|
| `CLASS` (default) | Before all tests | After all tests | Fast, shared state OK |
| `METHOD` | Before each test method | After each test method | Complete isolation |

**Note**: The `'suite'` lifecycle terminology was removed from the codebase. See
`docs/design-docs/done/remove-suite-lifecycle-terminology.md`.

All lifecycle options work with the full pipeline capabilities:
- Build step execution
- Test step execution
- Conditional actions on test success (tag, save, publish)
- Conditional actions on test failure
- Always step (cleanup)

---

## Success Criteria

The implementation is complete when:

### Phase 1 (Core Method Lifecycle Support) ✅ COMPLETE (2025-12-03)

- [x] `WorkflowLifecycle` enum created with `CLASS` and `METHOD` values
- [x] `lifecycle` property added to TestStepSpec with `convention(WorkflowLifecycle.CLASS)`
- [x] TestStepExecutor handles `lifecycle = METHOD` by:
  - [x] Setting system properties for test framework
  - [x] Skipping Gradle compose tasks
- [x] PipelineValidator enforces:
  - [x] Sequential execution (`maxParallelForks=1`) as ERROR for METHOD lifecycle
  - [x] Stack must be configured for METHOD lifecycle
- [x] Unit tests achieve 100% coverage on new code
- [x] Functional tests verify DSL parsing for lifecycle option
- [x] Integration test scenario-8 (Spock method lifecycle) passes (3 tests, 0 failures)
- [x] No Docker containers remain after integration tests
- [x] Configuration cache works with method lifecycle scenarios
- [x] Documentation updated with lifecycle option details

### Phase 2 (Auto-Detection - Future)

**Step 1 - Refactor JUnit 5 Extensions:**
- [ ] Remove `"time-server"` hardcoded default from `DockerComposeMethodExtension` and `DockerComposeClassExtension`
- [ ] Add `createConfigurationFromSystemProperties(ExtensionContext)` method to JUnit 5 extensions
- Note: Spock interceptors (`ComposeMethodInterceptor`, `ComposeClassInterceptor`) already support delegation - no changes needed

**Step 2 - Validation:**
- [ ] Add `delegateStackManagement + lifecycle=METHOD` redundancy validation to `PipelineValidator`
- [ ] Validation produces error with clear message explaining the redundancy and how to fix it

**Steps 3-6 - Production Code:**
- [ ] Spock `DockerComposeGlobalExtension` created implementing `IGlobalExtension`
- [ ] Spock service registration file created at `plugin/src/main/resources/META-INF/services/org.spockframework.runtime.extension.IGlobalExtension`
  - Note: This creates new `plugin/src/main/resources/META-INF/services/` directory structure
- [ ] JUnit 5 `DockerComposeGlobalExtension.groovy` created (Groovy for consistency)
- [ ] JUnit 5 service registration file created at `plugin/src/main/resources/META-INF/services/org.junit.jupiter.api.extension.Extension`
- [ ] Global extensions produce **error** when test class has annotation (not skip)
- [ ] Global extensions only apply when all required system properties are present:
  - `docker.compose.lifecycle`
  - `docker.compose.stack`
  - `docker.compose.files`

**Steps 7-10 - Testing:**
- [ ] Unit tests achieve 100% coverage on new global extension code
- [ ] Unit tests follow existing patterns using mock `SystemPropertyService` for isolation
- [ ] Functional tests verify plugin JAR contains service files
- [ ] Functional tests verify annotation conflict produces error (Spock `@ComposeUp`)
- [ ] Functional tests verify annotation conflict produces error (JUnit 5 `@ExtendWith`)
- [ ] scenario-9 (JUnit 5 method lifecycle, port 9208) created and passes
- [ ] scenario-10 (auto-detect without annotation, port 9209) created and passes for both Spock and JUnit 5

**Step 11 - Existing Test Updates (9 files: 7 Spock + 2 JUnit 5):**

Spock tests (remove `@ComposeUp`):
- [ ] `dockerOrch/verification/lifecycle-class/.../LifecycleClassIT.groovy`
- [ ] `dockerOrch/verification/lifecycle-method/.../LifecycleMethodIT.groovy`
- [ ] `dockerOrch/examples/database-app/.../DatabaseAppExampleIT.groovy`
- [ ] `dockerOrch/examples/isolated-tests/.../IsolatedTestsExampleIT.groovy`
- [ ] `dockerOrch/examples/web-app/.../WebAppExampleIT.groovy`
- [ ] `dockerOrch/examples/stateful-web-app/.../StatefulWebAppExampleIT.groovy`
- [ ] `dockerWorkflows/scenario-8-method-lifecycle/.../MethodLifecycleIT.groovy`

JUnit 5 tests (remove `@ExtendWith`, add `junit-platform.properties`):
- [ ] `dockerOrch/examples/web-app-junit/.../WebAppJUnit5ClassIT.java`
- [ ] `dockerOrch/examples/isolated-tests-junit/.../IsolatedTestsJUnit5MethodIT.java`

- [ ] All tests configured via `usesCompose()` in `build.gradle` instead of annotations
- [ ] JUnit 5 tests have `junit-platform.properties` with auto-detection enabled
- Note: `dockerWorkflows/scenario-2-delegated-lifecycle/.../DelegatedClassLifecycleIT.groovy` is NOT included because
  it does not use `@ComposeUp` - it relies on Gradle task dependencies via `testIntegration.usesCompose()` in build.gradle.
- Note: No unit tests or functional tests are affected

**Step 12 - Documentation:**
- [ ] `usage-docker-workflows.md` updated with auto-detection (annotation produces error)
- [ ] `spock-junit-test-extensions.md` updated to deprecate annotations
- [ ] JUnit 5 auto-detection requirement (`junit-platform.properties`) documented
- [ ] `dockerWorkflows/README.md` updated with scenario-9 and scenario-10

**Verification:**
- [ ] Annotations on test classes produce clear error with migration instructions
- [ ] No Docker containers remain after integration tests
- [ ] Configuration cache works with auto-detected tests
- [ ] Test class annotation no longer required when system properties are set

**JUnit 5 Auto-Detection Policy:**
- [ ] Documentation includes prominent warning about JUnit 5 auto-detection JVM-wide implications
- [ ] scenario-10 includes comment explaining why auto-detection is intentionally enabled for testing
- [ ] Pipeline does NOT automatically set `junit.jupiter.extensions.autodetection.enabled` system property
- [ ] Troubleshooting guidance provided for users who experience unexpected behaviors after enabling auto-detection

---

## Phase 2 Troubleshooting Guide

This section provides guidance for common issues during Phase 2 implementation.

### ServiceLoader Not Finding Extensions

**Symptom:** Global extensions are not being applied; test classes without annotations don't get compose lifecycle.

**Diagnosis:**
```bash
# Verify service files are in the built JAR
jar tf plugin/build/libs/gradle-docker-*.jar | grep META-INF/services

# Check service file content
unzip -p plugin/build/libs/gradle-docker-*.jar \
    META-INF/services/org.spockframework.runtime.extension.IGlobalExtension
```

**Common Causes:**
1. **Service file not in resources:** Verify file exists at `plugin/src/main/resources/META-INF/services/`
2. **Trailing whitespace:** Service files are whitespace-sensitive
3. **Wrong class name:** Must be fully qualified: `com.kineticfire.gradle.docker.spock.DockerComposeGlobalExtension`
4. **File encoding:** Must be UTF-8

**Fix:**
```bash
# Recreate service file with correct content
echo -n 'com.kineticfire.gradle.docker.spock.DockerComposeGlobalExtension' > \
    plugin/src/main/resources/META-INF/services/org.spockframework.runtime.extension.IGlobalExtension
```

### JUnit 5 Auto-Detection Not Working

**Symptom:** JUnit 5 global extension not applied even with system properties set.

**Diagnosis:**
1. Check if auto-detection is enabled:
```bash
# In test class, add temporary debug
System.out.println("autodetection: " + 
    System.getProperty("junit.jupiter.extensions.autodetection.enabled"));
```

2. Verify service file:
```bash
jar tf plugin/build/libs/gradle-docker-*.jar | grep org.junit.jupiter
```

**Common Causes:**
1. **Auto-detection not enabled:** Must set `junit.jupiter.extensions.autodetection.enabled=true`
2. **Service file missing:** Check `META-INF/services/org.junit.jupiter.api.extension.Extension`
3. **Dependency conflict:** Another extension may be interfering

**Fix:**
```properties
# In src/test/resources/junit-platform.properties
junit.jupiter.extensions.autodetection.enabled=true
```

### Annotation Conflict Error When Expected

**Symptom:** `IllegalStateException` about annotation conflict when test class has `@ComposeUp` or `@ExtendWith`.

**This is expected behavior!** Phase 2 intentionally errors when annotations are present alongside system properties.

**Fix:** Remove the annotation from the test class. The pipeline sets system properties that configure compose
lifecycle automatically.

### System Properties Not Being Set

**Symptom:** Global extension doesn't activate because system properties are empty.

**Diagnosis:**
```groovy
// Add to test class setup
def lifecycle = System.getProperty('docker.compose.lifecycle')
def stack = System.getProperty('docker.compose.stack')
def files = System.getProperty('docker.compose.files')
println "lifecycle=${lifecycle}, stack=${stack}, files=${files}"
```

**Common Causes:**
1. **Wrong lifecycle mode:** Verify `lifecycle = WorkflowLifecycle.METHOD` in pipeline config
2. **Missing stack:** Verify `stack = dockerOrch.composeStacks.yourStack` is configured
3. **Empty files:** Verify stack has compose files configured

**Fix:** Check `TestStepExecutor.setSystemPropertiesForMethodLifecycle()` is being called.

### Both Extensions Running (Duplicate Compose Operations)

**Symptom:** Containers start twice; port conflicts; compose operations duplicate.

**Cause:** Both annotation-based extension AND global extension are running.

**Diagnosis:** The annotation conflict check should prevent this. If it's not working:
1. Verify system properties are being set (see above)
2. Check `checkForAnnotationConflict()` is being called
3. Verify `AnnotationSupport.findRepeatableAnnotations()` is finding the annotation

**Fix:** Ensure the annotation conflict check runs early in `beforeAll`:
```java
@Override
void beforeAll(ExtensionContext context) throws Exception {
    checkForAnnotationConflict(context)  // MUST be first!
    // ... rest of method
}
```

### Service Initialization Failures

**Symptom:** Warning message about services failing to initialize; extension silently skipped.

**Diagnosis:** Check stderr for initialization warnings:
```
WARNING: DockerComposeGlobalExtension failed to initialize services: <error message>
```

**Common Causes:**
1. **Missing dependency:** Service class not on classpath
2. **Class loading issue:** Circular dependency or initialization error
3. **Docker not available:** `JUnitComposeService` may fail if Docker is not running

**Fix:** Ensure all service dependencies are available. The error handling allows tests to continue,
but compose lifecycle won't be managed.

### Spock vs JUnit 5 Behavior Differences

| Aspect | Spock | JUnit 5 |
|--------|-------|---------|
| Auto-detection | Always enabled | Requires opt-in |
| Extension registration | `IGlobalExtension.visitSpec()` | ServiceLoader + `beforeAll` |
| Annotation API | `spec.getAnnotation()` | `AnnotationSupport.findRepeatableAnnotations()` |
| Service injection | 5 services (creates interceptors) | 1 service (delegates to extensions) |

---

## Conclusion

Option E (Enhanced Test Framework Integration) provides the best balance of user experience, implementation complexity,
and architectural fit. The key insight is that **the test framework extensions already implement method lifecycle**
and read system properties to configure themselves.

The pipeline just needs to:
1. Add a type-safe `lifecycle` property to `TestStepSpec`
2. Set the correct system properties when `lifecycle = METHOD`
3. Skip Gradle compose tasks (let test framework handle)
4. Validate configuration (sequential execution required)

This approach preserves the pipeline's ability to perform conditional post-test actions while enabling complete
container isolation per test method—achieving the best of both worlds with minimal new code.

---

## Related Documents

- [Architectural Limitations Analysis](../architectural-limitations/architectural-limitations-analysis.md)
- [Architectural Limitations Plan](../architectural-limitations/architectural-limitations-plan.md)
- [Workflow Support Lifecycle](../workflow-lifecycle/workflow-support-lifecycle.md)
- [Why dockerWorkflows Cannot Support Method Lifecycle](../workflow-lifecycle/workflow-cannot-method-lifecycle.md)
- [Gradle 9/10 Compatibility](../../gradle-9-and-10-compatibility.md)
- [Spock/JUnit Test Extensions](../../../usage/spock-junit-test-extensions.md)
