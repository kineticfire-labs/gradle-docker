# Docker Workflows DSL Guide

This guide explains how to use the `dockerWorkflows` DSL to define CI/CD pipelines that orchestrate Docker image
building, testing, and conditional post-test actions.

## Table of Contents

- [Overview](#overview)
- [When to Use dockerWorkflows](#when-to-use-dockerworkflows)
- [Quick Start](#quick-start)
- [Pipeline DSL Reference](#pipeline-dsl-reference)
  - [pipelines Container](#pipelines-container)
  - [build Step](#build-step)
  - [test Step](#test-step)
  - [onTestSuccess Step](#ontestsuccess-step)
- [Lifecycle Options](#lifecycle-options)
  - [Suite Lifecycle (Default)](#suite-lifecycle-default)
  - [Class Lifecycle](#class-lifecycle)
  - [Method Lifecycle](#method-lifecycle)
- [Delegated Stack Management](#delegated-stack-management)
- [Combining dockerWorkflows with testIntegration](#combining-dockerworkflows-with-testintegration)
- [Lifecycle Options Comparison](#lifecycle-options-comparison)
- [Complete Examples](#complete-examples)
- [Task Reference](#task-reference)
- [Troubleshooting](#troubleshooting)

---

## Overview

The `dockerWorkflows` DSL provides a high-level abstraction for defining CI/CD pipelines that:

1. **Build** Docker images using the `docker` DSL
2. **Test** images using Docker Compose stacks from the `dockerOrch` DSL
3. **Conditionally execute actions** based on test results (e.g., tag or publish on success)

A typical workflow follows this pattern:

```
build → test → (on success) tag/publish
```

**Key Features:**
- Single pipeline definition orchestrates multiple Gradle tasks
- Conditional tagging: apply tags only when tests pass
- Conditional publishing: push images only when tests pass
- Integrates with `docker`, `dockerOrch`, and `testIntegration` DSLs

---

## When to Use dockerWorkflows

Use `dockerWorkflows` when you need:

| Use Case | Recommendation |
|----------|----------------|
| Simple integration testing | Use `dockerOrch` directly with `testIntegration` |
| Conditional tag on test success | ✅ Use `dockerWorkflows` |
| Conditional publish on test success | ✅ Use `dockerWorkflows` |
| Method-level container lifecycle | ✅ Use `dockerWorkflows` with `lifecycle = METHOD` |
| CI/CD pipeline orchestration | ✅ Use `dockerWorkflows` |

**All lifecycle modes are supported:** Suite (default), Class, and Method. See
[Lifecycle Options](#lifecycle-options) for details.

---

## Quick Start

This minimal example shows a pipeline that builds an image, runs tests, and tags the image as `tested` on success:

```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

// Define the image to build
docker {
    images {
        myApp {
            imageName = 'my-application'
            tags = ['latest', '1.0.0']
            context.set(file('src/main/docker'))
        }
    }
}

// Define the compose stack for testing
dockerOrch {
    composeStacks {
        appTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = 'my-app-test'

            waitForHealthy {
                waitForServices.set(['app'])
                timeoutSeconds.set(60)
            }
        }
    }
}

// Define the pipeline
dockerWorkflows {
    pipelines {
        ciPipeline {
            description = 'Build, test, and tag on success'

            build {
                image = docker.images.myApp
            }

            test {
                stack = dockerOrch.composeStacks.appTest
                testTaskName = 'integrationTest'
            }

            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

Run the pipeline:

```bash
./gradlew runCiPipeline
```

This executes in order:
1. `dockerBuildMyApp` - builds the image
2. `composeUpAppTest` - starts the compose stack
3. `integrationTest` - runs the tests
4. `composeDownAppTest` - stops the compose stack
5. `dockerTagMyApp` (with 'tested' tag) - only if tests passed

---

## Pipeline DSL Reference

### pipelines Container

The `pipelines` container holds one or more named pipeline definitions:

```groovy
dockerWorkflows {
    pipelines {
        pipelineName {
            description = 'Optional description'
            // steps...
        }

        anotherPipeline {
            // steps...
        }
    }
}
```

Each pipeline generates a `run<PipelineName>` task (e.g., `runCiPipeline`).

### build Step

The `build` step specifies which Docker image to build:

```groovy
build {
    image = docker.images.myApp  // Required: reference to an image defined in docker DSL
}
```

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `image` | ImageSpec | Yes | Reference to an image defined in the `docker` DSL |

### test Step

The `test` step configures testing:

```groovy
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

test {
    stack = dockerOrch.composeStacks.appTest  // Compose stack
    testTaskName = 'integrationTest'          // Name of the test task to run
    lifecycle = WorkflowLifecycle.CLASS       // Container lifecycle (default: CLASS)
    delegateStackManagement = false           // Default: false
}
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `stack` | ComposeStackSpec | Yes | - | Compose stack to use for testing |
| `testTaskName` | String | Yes | - | Name of the Gradle test task to execute |
| `lifecycle` | WorkflowLifecycle | No | `CLASS` | Container lifecycle: `CLASS` or `METHOD` |
| `delegateStackManagement` | boolean | No | `false` | If `true`, skip compose up/down; delegate to `testIntegration` |

**Lifecycle Values:**
- `WorkflowLifecycle.CLASS` - Containers start once before all tests and stop after all complete (default)
- `WorkflowLifecycle.METHOD` - Containers start fresh before each test method and stop after each

See [Lifecycle Options](#lifecycle-options) for detailed usage examples.

### onTestSuccess Step

The `onTestSuccess` step defines actions to execute only when tests pass:

```groovy
onTestSuccess {
    additionalTags = ['tested', 'verified']  // Tags to apply on success
    publish = true                            // Whether to publish the image
}
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `additionalTags` | List<String> | No | `[]` | Tags to add to the image when tests pass |
| `publish` | boolean | No | `false` | Whether to publish the image to a registry |

---

## Lifecycle Options

The `lifecycle` property in the test step controls when containers are started and stopped during test execution.

### Suite Lifecycle (Default)

With `lifecycle = WorkflowLifecycle.CLASS` (the default), containers start once before all tests and stop after
all tests complete. This is the fastest option when test isolation is not required.

```groovy
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

dockerWorkflows {
    pipelines {
        ciPipeline {
            build {
                image = docker.images.myApp
            }

            test {
                stack = dockerOrch.composeStacks.appTest
                testTaskName = 'integrationTest'
                lifecycle = WorkflowLifecycle.CLASS  // Default - can be omitted
            }

            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

**Execution flow:**
1. Build the Docker image
2. Start compose stack (once)
3. Run ALL test methods
4. Stop compose stack (once)
5. Apply tags on success

### Class Lifecycle

Class lifecycle behaves the same as suite lifecycle when using `dockerWorkflows` directly. For true per-class
container restarts, use `delegateStackManagement = true` with `testIntegration.usesCompose()`. See
[Delegated Stack Management](#delegated-stack-management).

### Method Lifecycle

With `lifecycle = WorkflowLifecycle.METHOD`, containers start fresh before each test method and stop after each
test method. This provides complete test isolation at the cost of slower execution.

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
                stack = dockerOrch.composeStacks.appTest
                testTaskName = 'integrationTest'
                lifecycle = WorkflowLifecycle.METHOD  // Fresh containers per test
            }

            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

**Requirements for METHOD lifecycle:**

1. **Sequential test execution required:** The test task must have `maxParallelForks = 1` to avoid port conflicts:
   ```groovy
   tasks.named('integrationTest') {
       maxParallelForks = 1  // Required for METHOD lifecycle
   }
   ```

2. **Test class must use @ComposeUp annotation:** The test framework extension handles per-method compose
   operations:
   ```groovy
   import com.kineticfire.gradle.docker.spock.ComposeUp

   @ComposeUp  // Required for METHOD lifecycle
   class MyIntegrationTest extends Specification {
       def "test method 1"() { /* gets fresh containers */ }
       def "test method 2"() { /* gets fresh containers */ }
   }
   ```

   For JUnit 5:
   ```java
   import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;

   @ExtendWith(DockerComposeMethodExtension.class)
   class MyIntegrationTest {
       @Test
       void testMethod1() { /* gets fresh containers */ }
       @Test
       void testMethod2() { /* gets fresh containers */ }
   }
   ```

**How METHOD lifecycle works:**
1. Pipeline builds the Docker image
2. Pipeline sets system properties for the test framework
3. Pipeline skips Gradle compose tasks (no `composeUp`/`composeDown`)
4. Test framework extension handles compose up/down per method
5. Pipeline applies tags on success after all tests complete

**Execution flow for each test method:**
1. `@ComposeUp` starts compose stack
2. Test method executes
3. `@ComposeUp` stops compose stack
4. Repeat for next test method...

---

## Delegated Stack Management

Use `delegateStackManagement = true` when you want `testIntegration.usesCompose()` to manage the compose lifecycle
instead of the pipeline.

**Why delegate?**
- `testIntegration.usesCompose()` supports **class lifecycle**: containers restart for each test class
- The pipeline can still orchestrate build → test → conditional actions
- Combines fine-grained lifecycle control with CI/CD pipeline benefits

**Example:**

```groovy
// Configure testIntegration to manage compose with class lifecycle
afterEvaluate {
    testIntegration {
        usesCompose(integrationTest, 'appTest', 'class')
    }
}

// Pipeline delegates compose management
dockerWorkflows {
    pipelines {
        delegatedPipeline {
            build {
                image = docker.images.myApp
            }

            test {
                testTaskName = 'integrationTest'
                delegateStackManagement = true  // Pipeline skips compose up/down
            }

            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

When `delegateStackManagement = true`:
- The pipeline does **not** call `composeUp*` or `composeDown*`
- `testIntegration` manages container lifecycle via Spock/JUnit extensions
- The `stack` property becomes optional (but can still be set for validation)

---

## Combining dockerWorkflows with testIntegration

The recommended pattern for class-level lifecycle with pipeline benefits:

```groovy
// 1. Define the image
docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['latest']
            contextTask = tasks.register('prepareContext', Copy) {
                into layout.buildDirectory.dir('docker-context')
                from('src/main/docker')
            }
        }
    }
}

// 2. Define the compose stack
dockerOrch {
    composeStacks {
        appTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = 'my-app-test'
            waitForHealthy {
                waitForServices.set(['app'])
                timeoutSeconds.set(60)
            }
        }
    }
}

// 3. Configure testIntegration for class lifecycle
afterEvaluate {
    testIntegration {
        usesCompose(integrationTest, 'appTest', 'class')
    }

    // Ensure image is built before compose up
    tasks.named('composeUpAppTest') {
        dependsOn tasks.named('dockerBuildMyApp')
    }
}

// 4. Define the pipeline with delegation
dockerWorkflows {
    pipelines {
        ciPipeline {
            build {
                image = docker.images.myApp
            }

            test {
                testTaskName = 'integrationTest'
                delegateStackManagement = true
            }

            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

**Note:** For method lifecycle with pipeline benefits, use `lifecycle = WorkflowLifecycle.METHOD` in the test step.
See [Method Lifecycle](#method-lifecycle) for details.

---

## Lifecycle Options Comparison

| Lifecycle | dockerWorkflows | How to Configure |
|-----------|-----------------|------------------|
| **Suite/Class** | ✅ Supported | `lifecycle = WorkflowLifecycle.CLASS` (default) |
| **Class (delegated)** | ✅ Supported | `delegateStackManagement = true` with `testIntegration` |
| **Method** | ✅ Supported | `lifecycle = WorkflowLifecycle.METHOD` with `@ComposeUp` |

All lifecycle modes preserve the pipeline's ability to perform conditional post-test actions (tag on success,
publish on success).

---

## Complete Examples

### Example 1: Basic Pipeline (Suite Lifecycle)

```groovy
dockerWorkflows {
    pipelines {
        basicPipeline {
            description = 'Basic CI pipeline'

            build {
                image = docker.images.myApp
            }

            test {
                stack = dockerOrch.composeStacks.appTest
                testTaskName = 'integrationTest'
            }

            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

### Example 2: Delegated Class Lifecycle

```groovy
afterEvaluate {
    testIntegration {
        usesCompose(integrationTest, 'appTest', 'class')
    }
}

dockerWorkflows {
    pipelines {
        classLifecyclePipeline {
            description = 'Pipeline with class lifecycle via delegation'

            build {
                image = docker.images.myApp
            }

            test {
                testTaskName = 'integrationTest'
                delegateStackManagement = true
            }

            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

### Example 3: Pipeline with Publish on Success

```groovy
docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['1.0.0']
            registry = 'docker.io'
            namespace = 'myuser'
            context.set(file('src/main/docker'))
        }
    }
}

dockerWorkflows {
    pipelines {
        publishPipeline {
            description = 'Build, test, tag and publish on success'

            build {
                image = docker.images.myApp
            }

            test {
                stack = dockerOrch.composeStacks.appTest
                testTaskName = 'integrationTest'
            }

            onTestSuccess {
                additionalTags = ['tested', 'latest']
                publish = true  // Push to registry on success
            }
        }
    }
}
```

### Example 4: Method Lifecycle Pipeline

```groovy
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

dockerWorkflows {
    pipelines {
        isolatedTestPipeline {
            description = 'Pipeline with fresh containers per test method'

            build {
                image = docker.images.myApp
            }

            test {
                stack = dockerOrch.composeStacks.appTest
                testTaskName = 'integrationTest'
                lifecycle = WorkflowLifecycle.METHOD  // Fresh containers per test
            }

            onTestSuccess {
                additionalTags = ['tested', 'verified']
            }
        }
    }
}

// Required: sequential test execution for METHOD lifecycle
tasks.named('integrationTest') {
    maxParallelForks = 1
}
```

Test class (Spock):

```groovy
import com.kineticfire.gradle.docker.spock.ComposeUp

@ComposeUp  // Required for METHOD lifecycle
class IsolatedTestIT extends Specification {
    def "first test gets fresh container"() {
        // Container started fresh for this test
        expect:
        // ... test logic
    }

    def "second test also gets fresh container"() {
        // Container restarted - completely fresh state
        expect:
        // ... test logic
    }
}
```

---

## Task Reference

For each pipeline named `myPipeline`, the plugin generates:

| Task | Description |
|------|-------------|
| `runMyPipeline` | Executes the complete pipeline |

The pipeline task orchestrates these underlying tasks:

| Step | Tasks Invoked |
|------|---------------|
| build | `dockerBuild<ImageName>` |
| test (CLASS lifecycle) | `composeUp<StackName>`, `<testTaskName>`, `composeDown<StackName>` |
| test (METHOD lifecycle) | `<testTaskName>` only (compose handled by test framework) |
| test (with delegation) | `<testTaskName>` only |
| onTestSuccess | `dockerTag<ImageName>`, optionally `dockerPublish<ImageName>` |

---

## Troubleshooting

### Pipeline fails to find image

Ensure the image is defined in the `docker` DSL before referencing it:

```groovy
docker {
    images {
        myApp { /* ... */ }  // Define first
    }
}

dockerWorkflows {
    pipelines {
        ciPipeline {
            build {
                image = docker.images.myApp  // Reference after definition
            }
        }
    }
}
```

### Image not built before compose up

Add an explicit dependency:

```groovy
afterEvaluate {
    tasks.named('composeUpAppTest') {
        dependsOn tasks.named('dockerBuildMyApp')
    }
}
```

### Tests not using class lifecycle

Ensure both `testIntegration.usesCompose()` and `delegateStackManagement = true` are configured:

```groovy
afterEvaluate {
    testIntegration {
        usesCompose(integrationTest, 'appTest', 'class')  // Required
    }
}

dockerWorkflows {
    pipelines {
        myPipeline {
            test {
                delegateStackManagement = true  // Also required
            }
        }
    }
}
```

### Port conflicts with @ComposeUp (CLASS lifecycle)

When using CLASS lifecycle (the default), do **not** add `@ComposeUp` annotation to your test classes. The pipeline
manages compose up/down via Gradle tasks - adding the annotation would cause port conflicts.

### Method lifecycle not working

If method lifecycle is configured but containers aren't restarting per method:

1. **Verify `lifecycle = WorkflowLifecycle.METHOD`** is set in the test step
2. **Ensure `@ComposeUp` annotation** is present on the test class
3. **Check `maxParallelForks = 1`** is set on the test task
4. **Verify system properties** are being passed (check test output for compose start/stop messages)

```groovy
// All three are required for METHOD lifecycle:

// 1. In pipeline:
test {
    lifecycle = WorkflowLifecycle.METHOD
}

// 2. On test class:
@ComposeUp
class MyTest extends Specification { }

// 3. In test task configuration:
tasks.named('integrationTest') {
    maxParallelForks = 1
}
```

### Method lifecycle fails with parallel tests

Method lifecycle requires sequential test execution. If you see port conflicts or "address already in use" errors:

```groovy
tasks.named('integrationTest') {
    maxParallelForks = 1  // Required for METHOD lifecycle
}
```

The pipeline validates this and throws an error if `maxParallelForks > 1` with METHOD lifecycle.

---

## Related Documentation

- [Docker DSL Guide](usage-docker.md) - Image building, tagging, saving, publishing
- [Docker Orch DSL Guide](usage-docker-orch.md) - Compose stack management
- [Spock/JUnit Test Extensions](spock-junit-test-extensions.md) - @ComposeUp and lifecycle annotations
- [Method Lifecycle Implementation](../design-docs/todo/add-method-lifecycle-workflow/add-method-workflow-analysis.md)
