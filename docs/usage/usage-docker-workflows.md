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
| Method-level container lifecycle | ❌ Use `@ComposeUp` without `dockerWorkflows` |
| CI/CD pipeline orchestration | ✅ Use `dockerWorkflows` |

**Important Limitation:** `dockerWorkflows` does **not** support per-method container lifecycle. See
[Lifecycle Options Comparison](#lifecycle-options-comparison) for details.

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
test {
    stack = dockerOrch.composeStacks.appTest  // Compose stack (optional with delegation)
    testTaskName = 'integrationTest'          // Name of the test task to run
    delegateStackManagement = false           // Default: false
}
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `stack` | ComposeStackSpec | Conditional | - | Compose stack to use. Required unless `delegateStackManagement = true` |
| `testTaskName` | String | Yes | - | Name of the Gradle test task to execute |
| `delegateStackManagement` | boolean | No | `false` | If `true`, skip compose up/down; delegate to `testIntegration` |

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

**Important:** Method lifecycle is **not supported** with `dockerWorkflows`. For method lifecycle (fresh containers
per test method), use `@ComposeUp` annotation directly without a pipeline. See
[Why dockerWorkflows Cannot Support Method Lifecycle](../design-docs/todo/workflow-cannot-method-lifecycle.md) for
technical details.

---

## Lifecycle Options Comparison

| Lifecycle | dockerWorkflows | @ComposeUp / testIntegration |
|-----------|-----------------|------------------------------|
| **Suite** | ✅ Supported | ✅ Supported |
| **Class** | ✅ Supported (with delegateStackManagement) | ✅ Supported |
| **Method** | ❌ **Not Supported** | ✅ Supported |

### Suite Lifecycle
Containers start once before all tests and stop after all tests complete.

```groovy
// Use standard pipeline test step (no delegation)
dockerWorkflows {
    pipelines {
        ciPipeline {
            test {
                stack = dockerOrch.composeStacks.appTest
                testTaskName = 'integrationTest'
            }
        }
    }
}
```

### Class Lifecycle
Containers restart for each test class. Requires delegation to `testIntegration`.

```groovy
// Delegate to testIntegration for class lifecycle
testIntegration {
    usesCompose(integrationTest, 'appTest', 'class')
}

dockerWorkflows {
    pipelines {
        ciPipeline {
            test {
                testTaskName = 'integrationTest'
                delegateStackManagement = true
            }
        }
    }
}
```

### Method Lifecycle (Not Supported with dockerWorkflows)

For method lifecycle, use `@ComposeUp` **without** a pipeline:

```groovy
// NO dockerWorkflows pipeline - use direct approach

dockerOrch {
    composeStacks {
        isolatedTest {
            files.from('src/integrationTest/resources/compose/app.yml')
        }
    }
}

testIntegration {
    usesCompose(integrationTest, 'isolatedTest', 'method')
}
```

```groovy
@ComposeUp  // Method lifecycle - each test method gets fresh containers
class IsolatedTestIT extends Specification {
    def "test 1"() { /* fresh containers */ }
    def "test 2"() { /* fresh containers again */ }
}
```

**Trade-off:** You lose conditional post-test actions (tag on success, publish on success).

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
| test (without delegation) | `composeUp<StackName>`, `<testTaskName>`, `composeDown<StackName>` |
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

### Port conflicts with @ComposeUp

Do not combine `@ComposeUp` annotation with a pipeline that manages the same compose stack. The two mechanisms are
mutually exclusive. Either:
- Use the pipeline for suite/class lifecycle, OR
- Use `@ComposeUp` for method lifecycle (without a pipeline)

---

## Related Documentation

- [Docker DSL Guide](usage-docker.md) - Image building, tagging, saving, publishing
- [Docker Orch DSL Guide](usage-docker-orch.md) - Compose stack management
- [Spock/JUnit Test Extensions](spock-junit-test-extensions.md) - @ComposeUp and lifecycle annotations
- [Why dockerWorkflows Cannot Support Method Lifecycle](../design-docs/todo/workflow-cannot-method-lifecycle.md)
