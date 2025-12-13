# How dockerWorkflows Supports Method Lifecycle

**Date:** 2025-12-03 (Updated)
**Status:** Feature Implemented ✅
**Related:**
- [Workflow Support Lifecycle](workflow-support-lifecycle.md)
- [Method Lifecycle Implementation Analysis](../add-method-lifecycle-workflow/add-method-workflow-analysis.md)

---

## Summary

The `dockerWorkflows` DSL now supports per-method container lifecycle (fresh containers for each test method)
through a combination of the `lifecycle = WorkflowLifecycle.METHOD` setting and the test framework extension
(`@ComposeUp` for Spock, `@ExtendWith(DockerComposeMethodExtension.class)` for JUnit 5).

**Previous Status:** Method lifecycle was not supported (architectural limitation).
**Current Status:** Method lifecycle IS supported via enhanced test framework integration.

---

## Background: Container Lifecycle Modes

The plugin supports two container lifecycle modes for integration testing:

| Lifecycle | Containers Start | Containers Stop | Use Case |
|-----------|------------------|-----------------|----------|
| **CLASS** | Before all tests | After all tests | Fast, shared state OK (default) |
| **METHOD** | Before each test method | After each test method | Complete isolation |

---

## How Method Lifecycle Works

### The Original Limitation

Gradle tasks execute once per build invocation. When `integrationTest` runs, it executes ALL test methods in a
single task execution. There is no Gradle mechanism to restart tasks between individual test method executions.

### The Solution: Test Framework Integration

Instead of trying to make Gradle tasks restart per method, the solution delegates container lifecycle to the
test framework extension, which operates at the JVM level inside the test process:

1. **Pipeline configures method lifecycle**: `lifecycle = WorkflowLifecycle.METHOD`
2. **Pipeline sets system properties**: Compose file paths, project name, wait configuration
3. **Pipeline skips Gradle compose tasks**: No `composeUp`/`composeDown` task execution
4. **Test framework extension reads system properties**: `@ComposeUp` annotation detects method lifecycle
5. **Extension manages compose per method**: Up before each method, down after each method
6. **Pipeline handles post-test actions**: Tag/publish on success after all tests complete

### Execution Flow

```
runMyPipeline
    ├── dockerBuildMyApp              (build step)
    ├── integrationTest               (test step - no Gradle compose tasks!)
    │   └── For each test method:
    │       ├── @ComposeUp calls compose up
    │       ├── Test method executes
    │       └── @ComposeUp calls compose down
    └── dockerTagMyApp:tested         (onTestSuccess - if all tests passed)
```

---

## Usage Example

### Pipeline Configuration

```groovy
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

dockerWorkflows {
    pipelines {
        isolatedPipeline {
            description = 'Pipeline with fresh containers per test method'

            build {
                image = docker.images.myApp
            }

            test {
                stack = dockerTest.composeStacks.appTest
                testTaskName = 'integrationTest'
                lifecycle = WorkflowLifecycle.METHOD  // KEY: Method lifecycle
            }

            onTestSuccess {
                additionalTags = ['tested']  // Still works! Applied after all tests pass
            }
        }
    }
}

// Required: sequential test execution to avoid port conflicts
tasks.named('integrationTest') {
    maxParallelForks = 1
}
```

### Test Class (Spock)

```groovy
import com.kineticfire.gradle.docker.spock.ComposeUp

@ComposeUp  // Required: reads system properties set by pipeline
class IsolatedTestIT extends Specification {

    def "first test gets fresh container"() {
        // Container started fresh for this test
        expect:
        // ... test logic
    }

    def "second test also gets fresh container"() {
        // Container restarted - completely fresh state
        // No data persists from first test
        expect:
        // ... test logic
    }
}
```

### Test Class (JUnit 5)

```java
import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerComposeMethodExtension.class)  // Required for method lifecycle
class IsolatedTestIT {

    @Test
    void firstTestGetsFreshContainer() {
        // Container started fresh for this test
    }

    @Test
    void secondTestAlsoGetsFreshContainer() {
        // Container restarted - completely fresh state
    }
}
```

---

## Requirements for Method Lifecycle

1. **`lifecycle = WorkflowLifecycle.METHOD`** in the pipeline's test step
2. **`@ComposeUp` annotation** (Spock) or `@ExtendWith(DockerComposeMethodExtension.class)` (JUnit 5) on test class
3. **`maxParallelForks = 1`** on the test task (validated by pipeline - throws error if > 1)
4. **Stack must be configured** in the test step (provides compose file paths)

---

## Validation

The pipeline validator enforces:

- **Sequential execution required**: If `lifecycle = METHOD` and `maxParallelForks > 1`, throws `GradleException`
  with clear message and fix instructions
- **Stack required**: If `lifecycle = METHOD` and no stack is configured, throws `GradleException`

---

## System Properties Set by Pipeline

When `lifecycle = WorkflowLifecycle.METHOD`, the pipeline sets these system properties on the test task:

| Property | Description |
|----------|-------------|
| `docker.compose.lifecycle` | Set to `method` |
| `docker.compose.stack` | Stack name |
| `docker.compose.files` | Comma-separated compose file paths |
| `docker.compose.projectName` | Compose project name (if configured) |
| `docker.compose.waitForHealthy.services` | Services to wait for healthy |
| `docker.compose.waitForHealthy.timeoutSeconds` | Health check timeout |
| `docker.compose.waitForRunning.services` | Services to wait for running |
| `docker.compose.waitForRunning.timeoutSeconds` | Running check timeout |

The `@ComposeUp` extension reads these properties to configure itself.

---

## Comparison: Before and After

### Before (Not Supported)

| Feature | dockerWorkflows | @ComposeUp (standalone) |
|---------|-----------------|-------------------------|
| Suite lifecycle | ✅ | ❌ |
| Class lifecycle | ✅ (delegated) | ✅ |
| Method lifecycle | ❌ | ✅ |
| Tag on success | ✅ | ❌ |
| Publish on success | ✅ | ❌ |

**Trade-off:** Users had to choose between method lifecycle OR conditional post-test actions.

### After (Supported)

| Feature | dockerWorkflows |
|---------|-----------------|
| Suite lifecycle | ✅ (`lifecycle = CLASS`) |
| Class lifecycle | ✅ (`delegateStackManagement = true`) |
| Method lifecycle | ✅ (`lifecycle = METHOD`) |
| Tag on success | ✅ (all modes) |
| Publish on success | ✅ (all modes) |

**No trade-off:** Users get both method lifecycle AND conditional post-test actions.

---

## Integration Test Verification

The method lifecycle feature is verified by:

- **Location:** `plugin-integration-test/dockerWorkflows/scenario-8-method-lifecycle/`
- **Test file:** `MethodLifecycleIT.groovy`
- **What it proves:**
  - Test 1 records container start time
  - Test 2 verifies container start time is DIFFERENT (proving fresh container)
  - Test 3 verifies container start time is DIFFERENT from both previous tests
  - Pipeline's `onTestSuccess` correctly applies 'tested' tag after all tests pass

---

## Historical Context

This document was originally titled "Why dockerWorkflows Cannot Support Method Lifecycle" and explained the
architectural limitation. The limitation was overcome by:

1. Leveraging the existing `@ComposeUp` extension's ability to manage compose lifecycle at the JVM level
2. Adding the `lifecycle` property to `TestStepSpec`
3. Having the pipeline set system properties instead of executing Gradle compose tasks
4. Letting the test framework extension read those properties and handle per-method lifecycle

This approach maintains Gradle's task execution model while achieving method-level container isolation.

---

## Related Documents

- [Method Lifecycle Implementation Analysis](../add-method-lifecycle-workflow/add-method-workflow-analysis.md)
- [Workflow Support Lifecycle Plan](workflow-support-lifecycle.md)
- [Spock/JUnit Test Extensions](../../../usage/spock-junit-test-extensions.md)
- [Docker Workflows Usage Guide](../../../usage/usage-docker-workflows.md)
- [Integration Test: Scenario 8](../../../../plugin-integration-test/dockerWorkflows/scenario-8-method-lifecycle/)
