# Analysis: Adding Method Lifecycle Support to dockerWorkflows DSL

**Status:** Phase 1 COMPLETE ✅ | Phase 2 CANCELLED (See [Decision Document](do-not-implement-auto-detection-phase-2.md))
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
**Phase 2 Status:** CANCELLED - See [Decision: Do Not Implement Phase 2](do-not-implement-auto-detection-phase-2.md)

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

**Cons** (accepted trade-offs in final design):
- ⚠️ Requires test class annotation (`@ComposeUp` for Spock, `@ExtendWith` for JUnit 5)
  - This was evaluated for removal via auto-detection (Phase 2) but rejected due to JVM-wide auto-detection
    risks for JUnit 5 users. See [Decision Document](do-not-implement-auto-detection-phase-2.md).
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

### Phase 2: Auto-Detection Enhancement - CANCELLED

Phase 2 was originally planned to eliminate the annotation requirement by implementing global test framework
extensions that auto-detect Docker Compose configuration from system properties set by the pipeline or `usesCompose()`.

**Decision:** Phase 2 will NOT be implemented.

**Rationale:** After thorough analysis, the marginal user value (eliminating one annotation per test class) does
not justify:
1. **JVM-wide auto-detection implications for JUnit 5 users** - Enabling `junit.jupiter.extensions.autodetection.enabled`
   activates ALL extensions on the classpath, not just Docker Compose extensions. This could cause unexpected behavior
   with Testcontainers, Spring Boot Test, or other extensions.
2. **Framework asymmetry** - Spock's `IGlobalExtension` is always active; JUnit 5 requires explicit opt-in. This
   creates inconsistent user experiences.
3. **Implementation risk and maintenance burden** - Two global extensions with complex conflict detection and
   service delegation add ongoing maintenance cost.

**Details:** See [Decision: Do Not Implement Phase 2](do-not-implement-auto-detection-phase-2.md)

**Alternative:** The annotation requirement is a minor inconvenience that serves as useful documentation. Users
benefit more from improved error messages and documentation than from complex auto-detection machinery.

---

### Phase 1 Improvements (Recommended)

To improve the Phase 1 user experience without the risks of auto-detection:

1. **Better Error Messages:** When tests fail due to missing annotation, provide clear guidance:
   ```
   ERROR: Test class 'MyIntegrationTest' configured with lifecycle=METHOD but missing annotation.
   Add: @ComposeUp (Spock) or @ExtendWith(DockerComposeMethodExtension.class) (JUnit 5)
   ```

2. **Documentation:** Update usage docs to explain why the annotation is required and provide copy-paste examples.

3. **IDE Templates:** Provide IntelliJ IDEA live templates for quick test class creation.

Estimated effort: 4-8 hours | Risk: None

---

## Related Documents

- [Decision: Do Not Implement Phase 2](do-not-implement-auto-detection-phase-2.md)
- [Architectural Limitations Analysis](../architectural-limitations/architectural-limitations-analysis.md)
- [Architectural Limitations Plan](../architectural-limitations/architectural-limitations-plan.md)
- [Workflow Support Lifecycle](../workflow-lifecycle/workflow-support-lifecycle.md)
- [Why dockerWorkflows Cannot Support Method Lifecycle](../workflow-lifecycle/workflow-cannot-method-lifecycle.md)
- [Gradle 9/10 Compatibility](../../gradle-9-and-10-compatibility.md)
- [Spock/JUnit Test Extensions](../../../usage/spock-junit-test-extensions.md)