# Recommendations for Adding Method Lifecycle to dockerWorkflows DSL

**Status:** Analysis Complete
**Date:** 2025-12-01
**Related Documents:**
- [Analysis Document](add-method-workflow-analysis.md)
- [Architectural Limitations Plan](../architectural-limitations/architectural-limitations-plan.md)
- [Workflow Support Lifecycle](../workflow-lifecycle/workflow-support-lifecycle.md)

---

## Executive Summary

The proposed Option E (Enhanced Test Framework Integration) from the analysis document is approved with refinements.
This document captures the refined recommendations based on review of the existing implementation.

---

## Recommendation #1: Add `lifecycle` Property Using an Enum

### Background

The analysis document proposed adding a `lifecycle` property to `TestStepSpec`. After reviewing the existing
implementation, we found:

1. **"SUITE" lifecycle does not exist** - This was incorrect terminology that was already removed from the codebase
   (see `docs/design-docs/done/remove-suite-lifecycle-terminology.md`)
2. **`LifecycleMode` enum already exists** - Located at `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/LifecycleMode.groovy`
   with values `CLASS` and `METHOD`
3. **`lifecycle: 'class'` configuration already works** - `TestIntegrationExtension.usesCompose()` accepts `'class'`
   and `'method'` string values

### Recommendation

Add a `lifecycle` property to `TestStepSpec` using a new workflow-specific enum (or reuse/extend `LifecycleMode`).

**Create enum:**

```groovy
// plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/workflow/WorkflowLifecycle.groovy
package com.kineticfire.gradle.docker.spec.workflow

/**
 * Lifecycle modes for container management in dockerWorkflows pipelines.
 *
 * Determines when containers start and stop during test execution within a pipeline.
 */
enum WorkflowLifecycle {
    /**
     * CLASS lifecycle - Containers start once before all test methods and stop after all complete.
     *
     * This is the default. The pipeline orchestrates compose up/down via Gradle tasks,
     * or delegates to testIntegration when delegateStackManagement=true.
     *
     * Use CLASS lifecycle when:
     * - Tests can share the same container environment
     * - Container startup time is expensive and you want to minimize it
     * - Tests perform read-only operations or can tolerate shared state
     */
    CLASS,

    /**
     * METHOD lifecycle - Containers start fresh before each test method and stop after each completes.
     *
     * When METHOD is selected, the pipeline automatically delegates compose management to the
     * test framework extension (@ComposeUp for Spock, @ExtendWith for JUnit 5). The test class
     * MUST have the appropriate annotation.
     *
     * Use METHOD lifecycle when:
     * - Tests need complete isolation from each other
     * - Tests modify database or application state
     * - Tests must be independent and idempotent
     * - Order independence is critical
     *
     * Note: METHOD lifecycle is slower because containers restart for each test method.
     */
    METHOD
}
```

**Add property to TestStepSpec:**

```groovy
// In TestStepSpec.groovy

/**
 * Lifecycle mode for container management during test execution.
 *
 * - CLASS (default): Containers managed once per test task via Gradle compose tasks
 * - METHOD: Containers managed per test method via test framework extension
 *
 * When METHOD is selected, the test class MUST have @ComposeUp (Spock) or
 * @ExtendWith(DockerComposeMethodExtension.class) (JUnit 5) annotation.
 * The pipeline will set system properties to configure the extension.
 */
abstract Property<WorkflowLifecycle> getLifecycle()

// In constructor:
lifecycle.convention(WorkflowLifecycle.CLASS)
```

### Relationship Between `lifecycle` and `delegateStackManagement`

| `lifecycle` | `delegateStackManagement` | Pipeline Compose Management | Who Handles Compose |
|-------------|---------------------------|-----------------------------|--------------------|
| `CLASS` (default) | `false` (default) | Pipeline calls `composeUp`/`composeDown` tasks once | Pipeline via Gradle tasks |
| `CLASS` | `true` | Pipeline skips compose tasks | `testIntegration` via task dependencies |
| `METHOD` | `true` (forced automatically) | Pipeline skips compose tasks | Test framework extension per method |

**Key behavior**: When `lifecycle = METHOD`, the pipeline automatically forces `delegateStackManagement = true`
because Gradle tasks cannot execute per-method—only the test framework extension can.

### How Method Lifecycle Works

When `lifecycle = METHOD`:

1. **Pipeline does NOT call Gradle compose tasks** - They would interfere with per-method management
2. **Pipeline sets system properties** on the test task:
   - `docker.compose.lifecycle=method`
   - `docker.compose.stack=<stackName>`
   - `docker.compose.files=<comma-separated paths>`
   - (other properties from ComposeStackSpec)
3. **Pipeline executes the test task**
4. **Test framework extension** (inside test JVM) reads system properties and:
   - For each test method: `setup()` → `docker compose up` → test runs → `cleanup()` → `docker compose down`
5. **Pipeline captures aggregate test results** and executes conditional actions

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Pipeline Execution (Gradle Task Level)                                     │
│                                                                             │
│    runCiPipeline                                                            │
│      ├── BuildStepExecutor: dockerBuildMyApp (runs ONCE)                    │
│      ├── TestStepExecutor:                                                  │
│      │     ├── [SKIPS composeUp Gradle task]                                │
│      │     ├── Sets system properties for test framework                    │
│      │     │                                                                │
│      │     └── Executes: integrationTest task ─────────────────────────┐    │
│      │                                                                 │    │
│      │     ┌───────────────────────────────────────────────────────────┘    │
│      │     │  INSIDE TEST JVM (ComposeMethodInterceptor):                   │
│      │     │                                                                │
│      │     │    setup():  docker compose up    ← fresh containers           │
│      │     │    test method 1 runs                                          │
│      │     │    cleanup(): docker compose down                              │
│      │     │                                                                │
│      │     │    setup():  docker compose up    ← fresh containers           │
│      │     │    test method 2 runs                                          │
│      │     │    cleanup(): docker compose down                              │
│      │     │                                                                │
│      │     │    setup():  docker compose up    ← fresh containers           │
│      │     │    test method 3 runs                                          │
│      │     │    cleanup(): docker compose down                              │
│      │     │                                                                │
│      │     └── Test JVM exits, returns results                              │
│      │                                                                      │
│      │     [SKIPS composeDown Gradle task]                                  │
│      │                                                                      │
│      └── ConditionalExecutor: if tests passed → tag/save/publish            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Implementation in TestStepExecutor

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

### User-Facing DSL

```groovy
dockerWorkflows {
    pipelines {
        ciPipeline {
            description = 'CI pipeline with per-method test isolation'

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

### Benefits of This Approach

1. **Type-safe enum** - IDE autocomplete, compile-time checking
2. **Two clear options** - `CLASS` and `METHOD` (no "suite" confusion)
3. **Automatic delegation** - `METHOD` lifecycle automatically delegates to test framework
4. **Backward compatible** - `CLASS` remains default, existing pipelines work unchanged
5. **Leverages existing infrastructure** - Reuses `ComposeMethodInterceptor` and `DockerComposeMethodExtension`
6. **Configuration cache compatible** - Uses `Property<WorkflowLifecycle>` (serializable enum)

### Validation Requirements

Add validation in `PipelineValidator`:

1. When `lifecycle = METHOD`, warn if `stack` is not configured (needed for system properties)
2. When `lifecycle = METHOD`, warn if test parallelism is enabled (`maxParallelForks > 1`)
   - Consider making this an error, not just a warning, as parallel method lifecycle causes port conflicts

---

## Summary of Changes Required

| File | Change |
|------|--------|
| `WorkflowLifecycle.groovy` | Create new enum (CLASS, METHOD) |
| `TestStepSpec.groovy` | Add `lifecycle` property with convention `CLASS` |
| `TestStepExecutor.groovy` | Handle METHOD lifecycle: set system properties, skip Gradle compose tasks |
| `PipelineValidator.groovy` | Add validation for METHOD lifecycle configuration |
| Unit tests | Test new property and behavior |
| Functional tests | Test DSL parsing |
| Integration tests | Create scenario demonstrating method lifecycle |

---

## Estimated Effort

Given that the test framework infrastructure (`ComposeMethodInterceptor`, `DockerComposeMethodExtension`) already
exists and works, the main work is wiring the pipeline to set system properties correctly.

**Estimated: 4-5 days** (reduced from original 7-day estimate)

- Day 1: Add enum and property, modify TestStepExecutor
- Day 2: Add validation, unit tests
- Day 3: Functional tests
- Day 4: Integration test scenario
- Day 5: Documentation, edge case handling

---

## Recommendation #2: Enforce Sequential Test Execution for Method Lifecycle

### Problem

When `lifecycle = METHOD` is configured, each test method starts fresh containers. If tests run in parallel
(`maxParallelForks > 1`), multiple test methods will attempt to start containers simultaneously, causing:

1. **Port conflicts** - Multiple compose stacks trying to bind to the same host ports
2. **Resource exhaustion** - Too many containers running simultaneously
3. **Unpredictable failures** - Race conditions in container startup/shutdown
4. **Flaky tests** - Intermittent failures that are hard to diagnose

### Recommendation

**Enforce sequential execution as an ERROR, not a warning.** The pipeline should fail fast during validation if
method lifecycle is configured with parallel test execution.

### Implementation

**In `PipelineValidator.groovy`:**

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

**Alternative: Auto-enforce sequential execution**

Instead of failing, the pipeline could automatically set `maxParallelForks = 1` when method lifecycle is detected.
However, this "magic" behavior could surprise users. Failing fast with a clear error message is preferred because:

1. **Explicit is better than implicit** - User understands why tests run slower
2. **No hidden side effects** - Build behavior is predictable
3. **Clear documentation** - Error message explains the constraint and how to fix it

### Why This Matters

Consider this scenario without enforcement:

```groovy
dockerWorkflows {
    pipelines {
        ci {
            test {
                lifecycle = WorkflowLifecycle.METHOD
                stack = dockerOrch.composeStacks.appStack  // Uses port 8080
            }
        }
    }
}

// Elsewhere in build.gradle
tasks.withType(Test) {
    maxParallelForks = Runtime.runtime.availableProcessors()  // e.g., 8 forks
}
```

Without enforcement:
- 8 test methods start simultaneously
- All 8 try to run `docker compose up` with port 8080
- 7 fail with "port already in use"
- User sees cryptic Docker errors, not a clear explanation

With enforcement:
- Validation catches the misconfiguration immediately
- User gets a clear error message explaining the constraint
- User makes an informed decision: sequential method lifecycle OR parallel class lifecycle

### Testing

Add unit test in `PipelineValidatorTest.groovy`:

```groovy
def "validates method lifecycle rejects parallel test execution"() {
    given:
    def testTask = project.tasks.create('integrationTest', Test)
    testTask.maxParallelForks = 4

    def testSpec = createTestStepSpec()
    testSpec.lifecycle.set(WorkflowLifecycle.METHOD)
    testSpec.testTaskName.set('integrationTest')
    testSpec.stack.set(createComposeStackSpec('testStack'))

    when:
    validator.validateTestStep(pipeline, testSpec, project)

    then:
    def e = thrown(GradleException)
    e.message.contains('maxParallelForks')
    e.message.contains('Method lifecycle requires sequential test execution')
}

def "validates method lifecycle accepts sequential test execution"() {
    given:
    def testTask = project.tasks.create('integrationTest', Test)
    testTask.maxParallelForks = 1  // Sequential

    def testSpec = createTestStepSpec()
    testSpec.lifecycle.set(WorkflowLifecycle.METHOD)
    testSpec.testTaskName.set('integrationTest')
    testSpec.stack.set(createComposeStackSpec('testStack'))

    when:
    validator.validateTestStep(pipeline, testSpec, project)

    then:
    noExceptionThrown()
}
```

---

## Recommendation #3: Leverage Existing Infrastructure

### Background

The original analysis document (Option E) estimated 7 days of effort and described building new functionality.
After reviewing the codebase, we found that **most of the required infrastructure already exists and is tested**.

### What Already Exists

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

### What Actually Needs to Be Built

The scope is narrower than the analysis suggested:

| Component | Work Required | Effort |
|-----------|---------------|--------|
| `WorkflowLifecycle` enum | Create new file (~30 lines) | 0.5 hours |
| `TestStepSpec.lifecycle` property | Add property + convention (~10 lines) | 0.5 hours |
| `TestStepExecutor` modifications | Add lifecycle check + system property setting (~50 lines) | 2 hours |
| `PipelineValidator` additions | Add method lifecycle validation (~40 lines) | 1 hour |
| Unit tests | Test new property and behavior (~200 lines) | 4 hours |
| Functional tests | Test DSL parsing (~100 lines) | 2 hours |
| Integration test scenario | New scenario-7 (~300 lines total) | 4 hours |
| Documentation updates | Update usage docs | 2 hours |

**Total: ~16 hours (2-3 days)** vs original 7-day estimate

### Key Insight: System Property Bridge

The critical insight is that the test framework extensions already read system properties to configure themselves.
The `DockerComposeSpockExtension.readLifecycleMode()` method (lines 352-372) already handles:

```groovy
private LifecycleMode readLifecycleMode(ComposeUp annotation) {
    def sysPropValue = systemPropertyService.getProperty('docker.compose.lifecycle', '')

    if (sysPropValue && !sysPropValue.isEmpty()) {
        switch (sysPropValue.toLowerCase()) {
            case 'class':
                return LifecycleMode.CLASS
            case 'method':
                return LifecycleMode.METHOD
            // ...
        }
    }
    return annotation.lifecycle()
}
```

**The pipeline just needs to set the system property** - the extension already knows what to do with it.

### Implementation Focus

Given the existing infrastructure, focus implementation on:

1. **Adding `lifecycle` property to `TestStepSpec`** - Simple property addition
2. **Setting system properties in `TestStepExecutor`** - Bridge between DSL and test framework
3. **Validation logic in `PipelineValidator`** - Enforce sequential execution, require stack

The test framework side (`ComposeMethodInterceptor`, `DockerComposeMethodExtension`) requires **no changes**.

### Risk Reduction

Leveraging existing infrastructure reduces risk:

- **Proven code paths** - Method lifecycle interceptors are already tested
- **Known behavior** - System property communication is established pattern
- **Minimal new code** - Less code = fewer bugs
- **Consistent architecture** - Same patterns as `testIntegration.usesCompose()`

---

## Recommendation #4: Auto-Detect Lifecycle to Eliminate Annotation Requirement (Phase 2)

### Status

**Priority:** Medium (Phase 2 Enhancement)
**Phase:** Implement AFTER method lifecycle support is working with the current annotation-based approach
**Applies to:**
- **Both DSLs:** `dockerWorkflows` AND `dockerOrch`
- **Both test frameworks:** Java JUnit 5 AND Groovy Spock

### Problem: Redundant User Configuration

Currently, users must configure compose lifecycle in **two places**:

**1. In build.gradle (DSL configuration):**

```groovy
// Using dockerOrch DSL
dockerOrch {
    composeStacks {
        myApp { files.from('src/integrationTest/resources/compose/app.yml') }
    }
}

tasks.named('integrationTest') {
    usesCompose stack: "myApp", lifecycle: "method"
}

// OR using dockerWorkflows DSL
dockerWorkflows {
    pipelines {
        ci {
            test {
                stack = dockerOrch.composeStacks.myApp
                lifecycle = WorkflowLifecycle.METHOD
            }
        }
    }
}
```

**2. In test class (annotation):**

**Spock:**
```groovy
@ComposeUp  // Required to trigger DockerComposeSpockExtension
class MyIntegrationTest extends Specification { ... }
```

**JUnit 5:**
```java
@ExtendWith(DockerComposeMethodExtension.class)  // Required for method lifecycle
// OR
@ExtendWith(DockerComposeClassExtension.class)   // Required for class lifecycle
class MyIntegrationTest { ... }
```

### Why the Annotation Is Redundant

The annotation's **only purpose** is to trigger the test framework extension. However:

1. The DSL already declares the stack name, lifecycle mode, and all configuration
2. System properties bridge all configuration to the test runtime
3. The extension reads configuration from system properties, not annotation parameters
4. If the user forgets the annotation, tests fail with confusing errors
5. If the user uses the wrong annotation (class vs method), behavior is incorrect

### Proposed Solution: Global Extensions with Auto-Detection

Both Spock and JUnit 5 support **global extensions** discovered via Java's ServiceLoader mechanism.
A global extension can detect whether compose lifecycle management should be applied by checking for the
`docker.compose.stack` system property.

**How It Works:**

1. Plugin ships global extensions registered in `META-INF/services/`
2. When tests run, the global extension checks for `docker.compose.stack` system property
3. If present, the extension auto-applies compose lifecycle management
4. If absent, the extension does nothing (zero overhead for non-compose tests)
5. Lifecycle mode (`class` or `method`) is read from `docker.compose.lifecycle` system property

### Implementation: Spock Global Extension

```groovy
// plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/DockerComposeGlobalExtension.groovy
package com.kineticfire.gradle.docker.spock

import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo

/**
 * Global Spock extension that auto-applies compose lifecycle management when
 * docker.compose.stack system property is set.
 *
 * This eliminates the need for @ComposeUp annotation when using dockerOrch or
 * dockerWorkflows DSL configuration.
 */
class DockerComposeGlobalExtension implements IGlobalExtension {

    @Override
    void visitSpec(SpecInfo spec) {
        // Check if compose is configured via system property
        String stackName = System.getProperty('docker.compose.stack')
        if (stackName == null || stackName.isEmpty()) {
            return  // No compose configured, skip this spec
        }

        // Check if @ComposeUp annotation is already present (defer to explicit annotation)
        if (spec.getAnnotation(ComposeUp) != null) {
            return  // Explicit annotation takes precedence
        }

        // Auto-apply lifecycle management based on docker.compose.lifecycle property
        String lifecycle = System.getProperty('docker.compose.lifecycle', 'class')

        if ('method'.equalsIgnoreCase(lifecycle)) {
            // Add per-method interceptors
            spec.features.each { feature ->
                feature.addIterationInterceptor(new ComposeMethodInterceptor(...))
            }
        } else {
            // Add class-level interceptors for setupSpec/cleanupSpec
            spec.addSetupSpecInterceptor(new ComposeClassSetupInterceptor(...))
            spec.addCleanupSpecInterceptor(new ComposeClassCleanupInterceptor(...))
        }
    }
}
```

**Registration:** `META-INF/services/org.spockframework.runtime.extension.IGlobalExtension`
```
com.kineticfire.gradle.docker.spock.DockerComposeGlobalExtension
```

### Implementation: JUnit 5 Global Extension

```java
// plugin/src/main/java/com/kineticfire/gradle/docker/junit/DockerComposeGlobalExtension.java
package com.kineticfire.gradle.docker.junit;

import org.junit.jupiter.api.extension.*;

/**
 * Global JUnit 5 extension that auto-applies compose lifecycle management when
 * docker.compose.stack system property is set.
 *
 * This eliminates the need for @ExtendWith annotation when using dockerOrch or
 * dockerWorkflows DSL configuration.
 */
public class DockerComposeGlobalExtension implements
        BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        String stackName = System.getProperty("docker.compose.stack");
        if (stackName == null || stackName.isEmpty()) return;

        String lifecycle = System.getProperty("docker.compose.lifecycle", "class");
        if ("class".equalsIgnoreCase(lifecycle)) {
            // Start compose stack for class lifecycle
            startComposeStack(context);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        String stackName = System.getProperty("docker.compose.stack");
        if (stackName == null || stackName.isEmpty()) return;

        String lifecycle = System.getProperty("docker.compose.lifecycle", "class");
        if ("class".equalsIgnoreCase(lifecycle)) {
            // Stop compose stack for class lifecycle
            stopComposeStack(context);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        String stackName = System.getProperty("docker.compose.stack");
        if (stackName == null || stackName.isEmpty()) return;

        String lifecycle = System.getProperty("docker.compose.lifecycle", "class");
        if ("method".equalsIgnoreCase(lifecycle)) {
            // Start compose stack for method lifecycle
            startComposeStack(context);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        String stackName = System.getProperty("docker.compose.stack");
        if (stackName == null || stackName.isEmpty()) return;

        String lifecycle = System.getProperty("docker.compose.lifecycle", "class");
        if ("method".equalsIgnoreCase(lifecycle)) {
            // Stop compose stack for method lifecycle
            stopComposeStack(context);
        }
    }

    private void startComposeStack(ExtensionContext context) { /* ... */ }
    private void stopComposeStack(ExtensionContext context) { /* ... */ }
}
```

**Registration:** `META-INF/services/org.junit.jupiter.api.extension.Extension`
```
com.kineticfire.gradle.docker.junit.DockerComposeGlobalExtension
```

### Benefits

| Benefit | Description |
|---------|-------------|
| **Zero annotation required** | Users only configure in build.gradle DSL |
| **No lifecycle mismatch possible** | Lifecycle mode comes from DSL, not annotation |
| **Consistent experience** | Same pattern for both Spock and JUnit 5 |
| **Works with both DSLs** | Applies to `dockerOrch` AND `dockerWorkflows` |
| **Backward compatible** | Existing annotations still work (explicit annotation takes precedence) |
| **Zero overhead** | Global extension exits immediately if system property not set |

### Considerations and Risks

| Consideration | Mitigation |
|---------------|------------|
| Global extension runs on ALL tests | Early exit when system property absent (near-zero overhead) |
| Backward compatibility | Detect if `@ComposeUp` annotation already applied and defer to it |
| JUnit 5 extension lifecycle | Method-level requires `BeforeEachCallback`/`AfterEachCallback` |
| Extension ordering | Global vs annotation-based extension ordering must be deterministic |
| Test isolation | Ensure global extension doesn't interfere with non-compose tests |
| Service registration | META-INF/services files must be correctly packaged in plugin JAR |

### Why Phase 2?

This enhancement should be implemented **AFTER** the annotation-based method lifecycle is working because:

1. **Foundation first** - The annotation-based approach establishes the system property bridge pattern
2. **Lower risk** - Global extensions are more complex than annotation-based extensions
3. **Incremental delivery** - Users get method lifecycle support sooner with annotations
4. **Validation** - Phase 1 validates the system property communication works correctly
5. **Scope containment** - Keeps Phase 1 focused on core functionality

### User Experience Comparison

**Phase 1 (Current/Annotation-Based):**
```groovy
// build.gradle
dockerWorkflows {
    pipelines {
        ci {
            test { lifecycle = WorkflowLifecycle.METHOD }
        }
    }
}

// Test class - ANNOTATION REQUIRED
@ComposeUp
class MyTest extends Specification { ... }
```

**Phase 2 (Auto-Detection):**
```groovy
// build.gradle - SAME AS PHASE 1
dockerWorkflows {
    pipelines {
        ci {
            test { lifecycle = WorkflowLifecycle.METHOD }
        }
    }
}

// Test class - NO ANNOTATION NEEDED
class MyTest extends Specification { ... }
```

### Estimated Effort

| Component | Effort |
|-----------|--------|
| Spock global extension | 4 hours |
| JUnit 5 global extension | 4 hours |
| Service registration files | 0.5 hours |
| Unit tests | 4 hours |
| Integration tests | 4 hours |
| Documentation | 2 hours |
| **Total** | **~18 hours (2-3 days)** |

---

## Recommendation #5: Add Integration Test for Key Scenario

### Purpose

Create an integration test that demonstrates the complete method lifecycle workflow, proving:

1. Two or more test methods run with fresh containers each
2. Container isolation is real (each method gets clean state)
3. Conditional actions (tag/save/publish) work based on aggregate test results
4. No lingering containers after all tests complete

### Integration Test Location

```
plugin-integration-test/dockerWorkflows/scenario-7-method-lifecycle/
├── build.gradle                          # Pipeline configuration
├── app-image/
│   ├── build.gradle                      # Image build + test configuration
│   └── src/
│       ├── main/docker/
│       │   └── Dockerfile                # Reuse existing time-server app
│       └── integrationTest/
│           ├── groovy/com/kineticfire/test/
│           │   └── MethodLifecycleIT.groovy  # Spock test with @ComposeUp
│           └── resources/compose/
│               └── app.yml               # Compose file for test stack
```

### Test Scenario Design

The integration test should prove container isolation by:

1. **Test Method 1**: Write a unique value to the container (e.g., create a file, insert DB record)
2. **Test Method 2**: Verify the unique value does NOT exist (proving fresh container)
3. **Both methods pass**: Pipeline should apply success tags

**Example Test Class:**

```groovy
// MethodLifecycleIT.groovy
package com.kineticfire.test

import com.kineticfire.gradle.docker.spock.ComposeUp
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * Integration test demonstrating METHOD lifecycle in dockerWorkflows pipeline.
 *
 * Each test method gets fresh containers. This test proves isolation by:
 * 1. Method 1 creates a marker file in the container
 * 2. Method 2 verifies the marker file does NOT exist (fresh container)
 *
 * If both tests pass, the pipeline applies 'method-tested' tag to the image.
 */
@ComposeUp  // Reads system properties from pipeline; lifecycle=method triggers per-method compose
class MethodLifecycleIT extends Specification {

    // Container endpoint from COMPOSE_STATE_FILE
    String appEndpoint

    def setup() {
        // Read container info from state file generated by ComposeMethodInterceptor
        def stateFile = System.getProperty('COMPOSE_STATE_FILE')
        // Parse JSON to get container endpoint...
        appEndpoint = parseEndpointFromStateFile(stateFile)
    }

    def "method 1 - create marker file in container"() {
        when: "we create a marker file via the app's API"
        def response = new URL("${appEndpoint}/marker/create?id=test-${System.currentTimeMillis()}").text

        then: "the marker is created successfully"
        response.contains('created')

        and: "we can verify it exists"
        def checkResponse = new URL("${appEndpoint}/marker/exists").text
        checkResponse.contains('true')
    }

    def "method 2 - verify fresh container has no marker"() {
        when: "we check for the marker file in a FRESH container"
        def response = new URL("${appEndpoint}/marker/exists").text

        then: "the marker does NOT exist (proving fresh container)"
        response.contains('false')

        // If this test sees 'true', it means containers were NOT restarted
        // between methods, indicating method lifecycle is broken
    }

    def "method 3 - verify container ID is different each time"() {
        when: "we get the container's unique ID"
        def containerId = new URL("${appEndpoint}/container-id").text

        then: "we record it (in real test, compare with previous methods)"
        containerId != null
        containerId.length() > 0

        // In a more sophisticated test, store container IDs in a static list
        // and verify each method gets a different container ID
    }

    private String parseEndpointFromStateFile(String stateFilePath) {
        // Parse JSON state file to extract host:port
        // ComposeMethodInterceptor generates this file after each compose up
        def stateJson = new groovy.json.JsonSlurper().parse(new File(stateFilePath))
        def appService = stateJson.services.app
        def port = appService.publishedPorts.find { it.container == 8080 }?.host
        return "http://localhost:${port}"
    }
}
```

### Build Configuration

**`scenario-7-method-lifecycle/app-image/build.gradle`:**

```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

// Reuse shared app module
evaluationDependsOn(':app')

docker {
    images {
        methodLifecycleApp {
            imageName = 'workflow-scenario7-app'
            tags = ['latest', '1.0.0']
            context.set(file('src/main/docker'))
        }
    }
}

dockerOrch {
    composeStacks {
        methodTestStack {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = 'workflow-scenario7'
            waitForHealthy {
                waitForServices = ['app']
                timeoutSeconds = 60
            }
        }
    }
}

dockerWorkflows {
    pipelines {
        methodLifecyclePipeline {
            description = 'Pipeline demonstrating METHOD lifecycle - fresh containers per test method'

            build {
                image = docker.images.methodLifecycleApp
            }

            test {
                stack = dockerOrch.composeStacks.methodTestStack
                testTaskName = 'integrationTest'
                lifecycle = WorkflowLifecycle.METHOD  // KEY: per-method containers
            }

            onTestSuccess {
                additionalTags = ['method-tested']
            }
        }
    }
}

// CRITICAL: Method lifecycle requires sequential execution
tasks.named('integrationTest') {
    maxParallelForks = 1
}

dependencies {
    integrationTestImplementation 'org.spockframework:spock-core:2.3-groovy-3.0'
}
```

### Verification Criteria

The integration test passes when:

1. ✅ `./gradlew :dockerWorkflows:scenario-7-method-lifecycle:app-image:integrationTest` succeeds
2. ✅ All 3 test methods pass
3. ✅ Method 2 proves container isolation (marker file absent)
4. ✅ Image `workflow-scenario7-app:method-tested` exists after pipeline completes
5. ✅ `docker ps -a` shows no lingering containers
6. ✅ Test runs with configuration cache enabled

### Why This Test Is Critical

This integration test is the **proof that the feature works end-to-end**:

- **Unit tests** prove individual components work
- **Functional tests** prove DSL parsing works
- **This integration test** proves the complete workflow:
  - DSL → system properties → test framework → per-method compose → aggregate results → conditional actions

Without this test, we cannot be confident the feature actually delivers value to users.

### Update README

Add scenario to `plugin-integration-test/dockerWorkflows/README.md`:

```markdown
| Scenario | Name | Purpose | Port |
|----------|------|---------|------|
| 7 | scenario-7-method-lifecycle | Method lifecycle with per-method container isolation | 9207 |
```

---

## Summary of All Recommendations

| # | Recommendation | Priority | Phase | Effort |
|---|----------------|----------|-------|--------|
| 1 | Add `lifecycle` property using `WorkflowLifecycle` enum | High | 1 | 4 hours |
| 2 | Enforce sequential test execution for method lifecycle | High | 1 | 2 hours |
| 3 | Leverage existing infrastructure (reduced scope) | High | 1 | N/A (scope reduction) |
| 4 | Auto-detect lifecycle to eliminate annotation requirement | Medium | 2 | 18 hours |
| 5 | Add integration test for key scenario | High | 1 | 4 hours |

**Phase 1 estimated effort: 4-5 days** (reduced from 7 days by leveraging existing infrastructure)
**Phase 2 estimated effort: 2-3 days** (after Phase 1 is complete and validated)

---

## Next Steps

### Phase 1: Core Method Lifecycle Support (Annotation-Based)

1. Review and approve these recommendations
2. Create implementation plan with detailed steps
3. Implement in order: Recommendation #1 → #2 → #5 (integration test)
4. Update documentation after implementation
5. Verify all tests pass with method lifecycle using `@ComposeUp` annotation

### Phase 2: Auto-Detection Enhancement (After Phase 1 Complete)

1. Validate Phase 1 is working correctly in production use
2. Implement Recommendation #4 (global extensions for auto-detection)
3. Update documentation to show annotation-free usage
4. Maintain backward compatibility with existing annotation-based tests

