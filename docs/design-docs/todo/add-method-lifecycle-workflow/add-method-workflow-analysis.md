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

       // Validate that delegateStackManagement is not explicitly set with lifecycle=METHOD
       // Both accomplish the same thing (delegate compose to test framework), so having both
       // is redundant and indicates a configuration misunderstanding
       if (testSpec.delegateStackManagement.isPresent() && testSpec.delegateStackManagement.get()) {
           throw new GradleException(
               "Pipeline '${pipeline.name}' has both lifecycle=METHOD and delegateStackManagement=true. " +
               "These are redundant - lifecycle=METHOD automatically delegates compose management to the test framework. " +
               "Remove one of these settings:\n\n" +
               "Option A - Use lifecycle (recommended for per-method container isolation):\n" +
               "  test {\n" +
               "      lifecycle = WorkflowLifecycle.METHOD\n" +
               "      // delegateStackManagement not needed\n" +
               "  }\n\n" +
               "Option B - Use delegateStackManagement (for class-level delegation to testIntegration):\n" +
               "  test {\n" +
               "      delegateStackManagement = true\n" +
               "      // lifecycle defaults to CLASS\n" +
               "  }"
           )
       }

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
plugin-integration-test/dockerWorkflows/scenario-7-method-lifecycle/
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

**Step 0: Refactor Existing Extensions for Delegation** (4-6 hours)

Before implementing global extensions, refactor the existing `DockerComposeMethodExtension` and
`DockerComposeClassExtension` (JUnit 5) and `ComposeMethodInterceptor`/`ComposeClassInterceptor` (Spock) to support
delegation from global extensions. This involves:

1. Extract shared configuration-from-system-properties logic into reusable methods
2. Ensure extensions can be instantiated and called without annotation context
3. Remove hardcoded defaults (e.g., `"time-server"` wait service) - all configuration must come from system properties

**File Changes:**
- `DockerComposeMethodExtension.groovy` - Add `createConfigurationFromSystemProperties()` method
- `DockerComposeClassExtension.groovy` - Add `createConfigurationFromSystemProperties()` method
- `ComposeMethodInterceptor.groovy` - Ensure constructor accepts config map from any source
- `ComposeClassInterceptor.groovy` - Ensure constructor accepts config map from any source

**Step 1: Create Spock Global Extension** (3 hours)

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/DockerComposeGlobalExtension.groovy`

```groovy
package com.kineticfire.gradle.docker.spock

import com.kineticfire.gradle.docker.spock.service.*
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
     */
    DockerComposeGlobalExtension() {
        this(
            new JUnitComposeService(),
            new DefaultProcessExecutor(),
            new DefaultFileService(),
            new DefaultSystemPropertyService(),
            new DefaultTimeService()
        )
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
        // Check for system properties indicating pipeline-managed compose
        def lifecycle = systemPropertyService.getProperty('docker.compose.lifecycle', '')
        def stack = systemPropertyService.getProperty('docker.compose.stack', '')
        def files = systemPropertyService.getProperty('docker.compose.files', '')

        // Only apply if all required properties are present
        if (lifecycle.isEmpty() || stack.isEmpty() || files.isEmpty()) {
            return
        }

        // ERROR if class has @ComposeUp annotation - configuration conflict
        if (spec.getAnnotation(ComposeUp) != null) {
            throw new IllegalStateException(
                "Test class '${spec.name}' has @ComposeUp annotation but compose configuration is managed by " +
                "dockerWorkflows pipeline with lifecycle=${lifecycle}. " +
                "Remove the @ComposeUp annotation from the test class. " +
                "All compose configuration should be in build.gradle via the dockerWorkflows DSL."
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

**Step 2: Create Service Registration for Spock** (0.5 hours)

**File:** `plugin/src/main/resources/META-INF/services/org.spockframework.runtime.extension.IGlobalExtension`

```
com.kineticfire.gradle.docker.spock.DockerComposeGlobalExtension
```

**Notes:**
- This file enables Spock's ServiceLoader to discover the global extension automatically
- No additional configuration required in user's build.gradle
- **IMPORTANT**: The `plugin/src/main/resources/` directory is currently empty. This step creates the
  `META-INF/services/` directory structure for the first time in the plugin

**Step 3: Create JUnit 5 Global Extension** (3 hours)

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

```groovy
package com.kineticfire.gradle.docker.junit

import com.kineticfire.gradle.docker.junit.service.*
import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.support.AnnotationSupport

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
        this(new DefaultSystemPropertyService())
    }

    DockerComposeGlobalExtension(SystemPropertyService systemPropertyService) {
        this.systemPropertyService = systemPropertyService
    }

    @Override
    void beforeAll(ExtensionContext context) throws Exception {
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
                        "but compose configuration is managed by dockerWorkflows pipeline with lifecycle=${lifecycle}. " +
                        "Remove the @ExtendWith annotation from the test class. " +
                        "DockerComposeMethodExtension is for METHOD lifecycle (containers per test method). " +
                        "All compose configuration should be in build.gradle via the dockerWorkflows DSL."
                    )
                }
                if (ext == DockerComposeClassExtension.class) {
                    throw new IllegalStateException(
                        "Test class '${testClass.name}' has @ExtendWith(DockerComposeClassExtension.class) " +
                        "but compose configuration is managed by dockerWorkflows pipeline with lifecycle=${lifecycle}. " +
                        "Remove the @ExtendWith annotation from the test class. " +
                        "DockerComposeClassExtension is for CLASS lifecycle (containers per test class). " +
                        "All compose configuration should be in build.gradle via the dockerWorkflows DSL."
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
- **Error** (not skip) when class has `@ExtendWith` for DockerCompose extensions - prevents configuration conflicts
- Uses `AnnotationSupport.findRepeatableAnnotations()` to handle all annotation patterns (repeatable, composed, meta)
- Written in Groovy to match existing JUnit 5 extensions in this project
- Delegates to existing `DockerComposeMethodExtension` and `DockerComposeClassExtension`
- Uses `SystemPropertyService` for testability (dependency injection)
- Lazily creates delegate extensions when needed
- Requires all system properties before applying
- Clear error messages explain extension-lifecycle mapping

**Step 4: Create Service Registration for JUnit 5** (0.5 hours)
```
plugin/src/main/resources/META-INF/services/org.junit.jupiter.api.extension.Extension
└── Contains: com.kineticfire.gradle.docker.junit.DockerComposeGlobalExtension
```

**CRITICAL:** JUnit 5 requires explicit user opt-in for extension auto-detection. The pipeline does NOT
automatically enable this setting. See "JUnit 5 Auto-Detection: Important Implications" section below
for detailed rationale and user configuration options.

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
1. Check `gradle dependencies` for transitive dependencies that might register extensions
2. Look for `META-INF/services/org.junit.jupiter.api.extension.Extension` files in your dependency JARs
3. Consider using `@ExtendWith` explicitly on test classes instead of auto-detection

---

**Step 5: Unit Tests** (4 hours)

**Testing Pattern Notes:**
- Follow existing test patterns in `DockerComposeSpockExtensionTest.groovy` and `DockerComposeMethodExtensionTest.groovy`
- Use `Mock(SpecInfo)` for Spock extension tests, `Mock(ExtensionContext)` for JUnit 5 extension tests
- Inject mock `SystemPropertyService` to control system property values without affecting actual system state
- Test both positive cases (interceptor registered) and negative cases (no interceptor when conditions not met)
- Use `@AutoCleanup` or explicit cleanup blocks to restore system property state

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
        e.message.contains("dockerWorkflows pipeline")
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
        e.message.contains("dockerWorkflows pipeline")
        e.message.contains("Remove the @ExtendWith annotation")
        e.message.contains("METHOD lifecycle")  // Explains extension-lifecycle mapping
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
        e.message.contains("CLASS lifecycle")  // Explains extension-lifecycle mapping
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

    // Test helper classes (would be defined in a separate file or as static inner classes)
    static class TestClassWithoutAnnotation {}

    @ExtendWith(DockerComposeMethodExtension)
    static class TestClassWithMethodExtension {}

    @ExtendWith(DockerComposeClassExtension)
    static class TestClassWithClassExtension {}
}
```

**Step 6: Functional Tests** (2 hours)

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

    def "service file registers Spock global extension"() {
        given: "a project that applies the plugin and processes resources"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
        """

        when: "processResources runs"
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('processResources', '--info')
            .withPluginClasspath()
            .build()

        then: "the Spock global extension service file exists and contains correct class"
        def serviceFile = new File(projectDir,
            'build/resources/main/META-INF/services/org.spockframework.runtime.extension.IGlobalExtension')
        serviceFile.exists()
        serviceFile.text.contains('com.kineticfire.gradle.docker.spock.DockerComposeGlobalExtension')
    }

    def "service file registers JUnit 5 global extension"() {
        given: "a project that applies the plugin and processes resources"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
        """

        when: "processResources runs"
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments('processResources', '--info')
            .withPluginClasspath()
            .build()

        then: "the JUnit 5 global extension service file exists and contains correct class"
        def serviceFile = new File(projectDir,
            'build/resources/main/META-INF/services/org.junit.jupiter.api.extension.Extension')
        serviceFile.exists()
        serviceFile.text.contains('com.kineticfire.gradle.docker.junit.DockerComposeGlobalExtension')
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
}
```

**Step 7: JUnit 5 Integration Test Scenario** (3 hours)

**Directory:** `plugin-integration-test/dockerWorkflows/scenario-9-method-lifecycle-junit/`

This scenario mirrors scenario-8 but uses JUnit 5 instead of Spock.

**Port Allocation Strategy:**
Integration test scenarios manually deconflict ports to avoid collisions. Each scenario uses a unique port range:
- scenario-8 (Spock method lifecycle): port 9207
- scenario-9 (JUnit 5 method lifecycle): port 9208
- scenario-10 (auto-detect): port 9209

Ports are configured in each scenario's `compose/app.yml` file and must be unique across all simultaneously-running
scenarios. When adding new scenarios, select the next available port in the 92xx range.

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
    void firstTestMethodGetsFreshContainer() throws Exception {
        Thread.sleep(2000);

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
    void secondTestMethodGetsDifferentFreshContainer() throws Exception {
        Thread.sleep(2000);

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
    void thirdTestMethodAlsoGetsFreshContainer() throws Exception {
        Thread.sleep(2000);

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

**Step 8: Auto-Detection Integration Test Scenario (Phase 2 Validation)** (2 hours)

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

**Step 9: Documentation** (2 hours)

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
| 0 | Refactor existing extensions for delegation | 2 | Pending |
| 1 | Create Spock `DockerComposeGlobalExtension` | 3 | Pending |
| 2 | Create Spock service registration file | 0.5 | Pending |
| 3 | Create JUnit 5 `DockerComposeGlobalExtension` (Groovy) | 3 | Pending |
| 4 | Create JUnit 5 service registration file | 0.5 | Pending |
| 5 | Unit tests for global extensions | 4 | Pending |
| 6 | Functional tests for service registration | 2 | Pending |
| 7 | scenario-9: JUnit 5 method lifecycle integration test | 3 | Pending |
| 8 | scenario-10: Auto-detect integration test (validates Phase 2) | 2 | Pending |
| 9 | Remove annotations from existing tests & update build.gradle | 2 | Pending |
| 10 | Documentation updates | 2 | Pending |
| **Total** | | **24** | |

**Phase 2 Total: ~24 hours (3-4 days)**

---

## Existing Infrastructure (No Changes Required)

The following components already exist and work correctly. No modifications needed for Phase 1:

| Component | Location | Status |
|-----------|----------|--------|
| `LifecycleMode` enum | `plugin/src/main/groovy/.../spock/LifecycleMode.groovy` | ✅ Complete |
| `ComposeMethodInterceptor` | `plugin/src/main/groovy/.../spock/ComposeMethodInterceptor.groovy` | ✅ Complete (388 lines) |
| `DockerComposeMethodExtension` | `plugin/src/main/java/.../junit/DockerComposeMethodExtension.java` | ✅ Complete |
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
| `DockerComposeMethodExtension.groovy` | Modify | Add `createConfigurationFromSystemProperties()` method for delegation |
| `DockerComposeClassExtension.groovy` | Modify | Add `createConfigurationFromSystemProperties()` method for delegation |
| `ComposeMethodInterceptor.groovy` | Modify | Remove hardcoded defaults; all config from system properties |
| `ComposeClassInterceptor.groovy` | Modify | Remove hardcoded defaults; all config from system properties |
| `spock/DockerComposeGlobalExtension.groovy` | Create | Spock global extension implementing `IGlobalExtension` |
| `junit/DockerComposeGlobalExtension.groovy` | Create | JUnit 5 global extension (Groovy for consistency) |
| `META-INF/services/org.spockframework.runtime.extension.IGlobalExtension` | Create | Spock service registration (new directory structure) |
| `META-INF/services/org.junit.jupiter.api.extension.Extension` | Create | JUnit 5 service registration |

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
| `scenario-8-method-lifecycle/` tests | Modify | Remove `@ComposeUp` annotation, configure via `build.gradle` |
| All `dockerOrch` integration tests | Modify | Remove annotations, configure via `usesCompose()` in build.gradle |
| All `dockerWorkflows` integration tests | Modify | Remove annotations if present, configure via DSL |

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

**Step 0 - Refactoring:**
- [ ] Existing extensions refactored to support delegation from global extensions
- [ ] `createConfigurationFromSystemProperties()` method added to extensions
- [ ] Hardcoded defaults removed from interceptors (e.g., `"time-server"` wait service)

**Production Code:**
- [ ] Spock `DockerComposeGlobalExtension` created implementing `IGlobalExtension`
- [ ] Spock service registration file created at `META-INF/services/org.spockframework.runtime.extension.IGlobalExtension`
  - Note: This creates new `plugin/src/main/resources/META-INF/services/` directory structure
- [ ] JUnit 5 `DockerComposeGlobalExtension.groovy` created (Groovy for consistency)
- [ ] JUnit 5 service registration file created at `META-INF/services/org.junit.jupiter.api.extension.Extension`
- [ ] Global extensions produce **error** when test class has annotation (not skip)
- [ ] Global extensions only apply when all required system properties are present:
  - `docker.compose.lifecycle`
  - `docker.compose.stack`
  - `docker.compose.files`

**Testing:**
- [ ] Unit tests achieve 100% coverage on new global extension code
- [ ] Unit tests follow existing patterns using mock `SystemPropertyService` for isolation
- [ ] Functional tests verify service file registration
- [ ] scenario-9 (JUnit 5 method lifecycle, port 9208) created and passes
- [ ] scenario-10 (auto-detect without annotation, port 9209) created and passes for both Spock and JUnit 5

**Existing Test Updates:**
- [ ] All `@ComposeUp` annotations removed from Spock integration tests
- [ ] All `@ExtendWith(DockerComposeMethodExtension.class)` annotations removed from JUnit 5 tests
- [ ] All tests configured via `usesCompose()` in `build.gradle` instead of annotations
- [ ] scenario-8 tests updated to remove annotations

**Documentation:**
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
