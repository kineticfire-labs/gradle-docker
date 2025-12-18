# Integration Test Coverage - 'dockerProject' DSL

This document provides documentation and a feature matrix for the `dockerProject` DSL integration tests.

## Overview

The `dockerProject` DSL provides a simplified, single-block configuration for common Docker workflows. It serves as a
facade over the three underlying DSLs (`docker`, `dockerTest`, `dockerWorkflows`), automatically translating the
simplified configuration into the full three-DSL setup.

### When to Use dockerProject

Use `dockerProject` when you have a **single Docker image** with a standard workflow:
- Build (or reference) an image
- Test with Docker Compose
- Apply additional tags, save, or publish on success

For complex multi-image scenarios, multiple pipelines, or advanced customization, use the underlying DSLs directly.

## Port Allocation

Integration tests run in parallel. To avoid conflicts, each scenario uses unique ports:

| Scenario | Port | Description |
|----------|------|-------------|
| scenario-1-build-mode | 9300 | Build mode with jarFrom |
| scenario-2-sourceref-mode | 9301 | SourceRef mode (external image) |
| scenario-3-save-publish | 9302 | Save and publish on success |
| scenario-4-method-lifecycle | 9303 | Method lifecycle mode |
| scenario-5-contextdir-mode | 9304 | Build mode with contextDir |
| scenario-6-repository-mode | 9306 | Repository mode (alternative to imageName) |
| scenario-7-repository-registry | 9307 | Repository mode with registry publishing |
| scenario-8-imagename-full | 9308 | Full imageName mode with all options |
| scenario-9-config-cache | 9309 | Configuration cache verification |

Registry ports (for publish scenarios):
- scenario-3: 5032
- scenario-7: 5037
- scenario-8: 5038

## Scenario Descriptions

### scenario-1-build-mode

**Purpose:** Demonstrates the basic `dockerProject` DSL with build mode using `jarFrom`.

**Features Tested:**
- `image.name` - Docker image name
- `image.tags` - Multiple tags (latest, 1.0.0)
- `image.jarFrom` - Reference to JAR-producing task (`:app:jar`)
- `image.buildArgs` - Build arguments
- `image.labels` - Image labels
- `test.compose` - Docker Compose file path
- `test.waitForHealthy` - Health check waiting
- `onSuccess.additionalTags` - Tag on success (tested)

**DSL Example:**
```groovy
dockerProject {
    image {
        name.set('project-scenario1-app')
        tags.set(['latest', '1.0.0'])
        jarFrom.set(':app:jar')
        buildArgs.put('BUILD_VERSION', '1.0.0')
        labels.put('org.opencontainers.image.title', 'Project Scenario 1')
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

### scenario-2-sourceref-mode

**Purpose:** Demonstrates `dockerProject` DSL with sourceRef mode for referencing external images.

**Features Tested:**
- `image.sourceRefImageName` - External image name (nginx)
- `image.sourceRefTag` - External image tag (1.27-alpine)
- `image.pullIfMissing` - Pull image if not local
- `test.compose` - Docker Compose testing
- `test.waitForRunning` - Wait for running status (vs healthy)
- `onSuccess.additionalTags` - Tag pulled image on success

**DSL Example:**
```groovy
dockerProject {
    image {
        name.set('project-scenario2-nginx')
        tags.set(['latest', '1.27'])
        sourceRefImageName.set('nginx')
        sourceRefTag.set('1.27-alpine')
        pullIfMissing.set(true)
    }
    test {
        compose.set('src/integrationTest/resources/compose/nginx.yml')
        waitForRunning.set(['nginx'])
    }
    onSuccess {
        additionalTags.set(['verified'])
    }
}
```

### scenario-3-save-publish

**Purpose:** Demonstrates save and publish functionality on test success.

**Features Tested:**
- `onSuccess.saveFile` - Save image to tar.gz file
- `onSuccess.publishRegistry` - Publish to private registry
- `onSuccess.publishNamespace` - Publish namespace
- `onSuccess.publishTags` - Specific tags to publish
- Automatic compression inference from file extension

**DSL Example:**
```groovy
dockerProject {
    image {
        name.set('project-scenario3-app')
        tags.set(['latest', '1.0.0'])
        jarFrom.set(':app:jar')
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested', 'stable'])
        saveFile.set('build/images/project-scenario3-app.tar.gz')
        publishRegistry.set('localhost:5032')
        publishNamespace.set('scenario3')
        publishTags.set(['latest', '1.0.0', 'tested'])
    }
}
```

### scenario-4-method-lifecycle

**Purpose:** Demonstrates method lifecycle mode where containers restart per test method.

**Features Tested:**
- `test.lifecycle.set('method')` - Method-level container lifecycle
- `@ComposeUp` Spock annotation integration
- Fresh container isolation per test method
- Different container start times verification
- `maxParallelForks = 1` requirement for port conflict avoidance

**Key Characteristics:**
- Containers start fresh before each test method
- Containers stop after each test method
- Each test gets isolated state (no persistence)
- Requires running the pipeline task (not just integrationTest)

**DSL Example:**
```groovy
dockerProject {
    image {
        name.set('project-scenario4-app')
        tags.set(['latest', '1.0.0'])
        jarFrom.set(':app:jar')
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        lifecycle.set('method')  // KEY: Method lifecycle
        testTaskName.set('integrationTest')
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

**Important:** For method lifecycle, run the pipeline task:
```bash
./gradlew :dockerProject:scenario-4-method-lifecycle:app-image:runProjectscenario4appPipeline
```

### scenario-5-contextdir-mode

**Purpose:** Demonstrates build mode using a pre-existing Docker context directory.

**Features Tested:**
- `image.contextDir` - Pre-existing Docker context directory
- User-managed context layout (vs auto-generated from jarFrom)
- Dockerfile located inside context directory
- Custom `prepareContext` task for file preparation

**Key Differences from jarFrom:**

| Aspect | jarFrom | contextDir |
|--------|---------|------------|
| Context location | Auto-generated in `build/docker-context` | User-specified directory |
| Dockerfile location | `src/main/docker/Dockerfile` | Inside context directory |
| File management | Plugin copies JAR automatically | User manages all context files |
| Use case | Simple JAR-based applications | Complex/custom build contexts |

**DSL Example:**
```groovy
dockerProject {
    image {
        name.set('project-scenario5-app')
        tags.set(['latest', '1.0.0'])
        contextDir.set('src/main/docker-context')
        buildArgs.put('BUILD_VERSION', '1.0.0')
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

### scenario-6-repository-mode

**Purpose:** Demonstrates the `repository` property as an alternative to `imageName`.

**Features Tested:**
- `images.*.repository` - Repository-style image name (e.g., 'myorg/myapp')
- Alternative to imageName + namespace combination
- Docker Hub naming convention support

**Key Concept:**
The `repository` property allows specifying namespace and image name in a single property:
- `repository.set('scenario6org/scenario6-app')` → image named `scenario6org/scenario6-app:tag`
- Equivalent to: `imageName.set('scenario6-app')` + `namespace.set('scenario6org')`

**DSL Example:**
```groovy
dockerProject {
    images {
        scenario6App {
            repository.set('scenario6org/scenario6-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

### scenario-7-repository-registry

**Purpose:** Demonstrates repository mode combined with private registry publishing.

**Features Tested:**
- `images.*.repository` - Repository-style image name
- `images.*.registry` - Target registry for publishing
- `onSuccess.publishRegistry` - Publish to private registry on success
- `onSuccess.publishTags` - Specific tags to publish

**DSL Example:**
```groovy
dockerProject {
    images {
        scenario7App {
            repository.set('scenario7org/scenario7-app')
            registry.set('localhost:5037')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested', 'stable'])
        publishRegistry.set('localhost:5037')
        publishTags.set(['latest', '1.0.0', 'tested'])
    }
}
```

### scenario-8-imagename-full

**Purpose:** Demonstrates full imageName mode with all configuration options.

**Features Tested:**
- `images.*.imageName` + `registry` + `namespace` - Full image naming
- Multiple `buildArgs` (4 build arguments)
- Multiple `labels` (4 OCI standard labels)
- `onSuccess.saveFile` - Save image to tar.gz
- `onSuccess.publishRegistry` + `publishNamespace` + `publishTags` - Full publish config

**DSL Example:**
```groovy
dockerProject {
    images {
        scenario8App {
            imageName.set('scenario8-app')
            registry.set('localhost:5038')
            namespace.set('scenario8ns')
            tags.set(['latest', '1.0.0', 'dev'])
            jarFrom.set(':app:jar')

            buildArgs.put('BUILD_VERSION', '1.0.0')
            buildArgs.put('BUILD_DATE', '2025-01-15')
            buildArgs.put('JAVA_VERSION', '21')
            buildArgs.put('BUILDER', 'gradle-docker-plugin')

            labels.put('org.opencontainers.image.title', 'Scenario 8 App')
            labels.put('org.opencontainers.image.version', '1.0.0')
            labels.put('org.opencontainers.image.vendor', 'Test Vendor')
            labels.put('org.opencontainers.image.authors', 'test@example.com')
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested', 'verified'])
        saveFile.set('build/images/scenario8-app.tar.gz')
        publishRegistry.set('localhost:5038')
        publishNamespace.set('scenario8ns')
        publishTags.set(['latest', '1.0.0', 'tested'])
    }
}
```

### scenario-9-config-cache

**Purpose:** Explicitly verifies configuration cache compatibility.

**Features Tested:**
- Configuration cache stores and reuses correctly
- Second run shows "Reusing configuration cache"
- All tasks execute correctly from cached state
- File-based state communication works with configuration cache

**Verification:**
```bash
# First run (stores cache)
./gradlew --configuration-cache :dockerProject:scenario-9-config-cache:app-image:runProjectscenario9appPipeline

# Second run (should reuse cache - look for "Reusing configuration cache")
./gradlew --configuration-cache :dockerProject:scenario-9-config-cache:app-image:runProjectscenario9appPipeline
```

**DSL Example:**
```groovy
dockerProject {
    images {
        scenario9App {
            imageName.set('project-scenario9-app')
            tags.set(['latest'])
            jarFrom.set(':app:jar')
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

## Feature Coverage Matrix

### Image Configuration

| Feature | s1 | s2 | s3 | s4 | s5 | s6 | s7 | s8 | s9 |
|---------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| imageName | ✓ | ✓ | ✓ | ✓ | ✓ | | | ✓ | ✓ |
| repository | | | | | | ✓ | ✓ | | |
| registry | | | | | | | ✓ | ✓ | |
| namespace | | | | | | | | ✓ | |
| tags | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| jarFrom | ✓ | | ✓ | ✓ | | ✓ | ✓ | ✓ | ✓ |
| contextDir | | | | | ✓ | | | | |
| sourceRef* | | ✓ | | | | | | | |
| pullIfMissing | | ✓ | | | | | | | |
| buildArgs | ✓ | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| labels | ✓ | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |

Legend: s1=scenario-1, s2=scenario-2, etc.

### Test Configuration

| Feature | s1 | s2 | s3 | s4 | s5 | s6 | s7 | s8 | s9 |
|---------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| compose | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| waitForHealthy | ✓ | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| waitForRunning | | ✓ | | | | | | | |
| lifecycle='class' | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | ✓ | ✓ |
| lifecycle='method' | | | | ✓ | | | | | |
| timeoutSeconds | | | ✓ | ✓ | | ✓ | ✓ | ✓ | ✓ |

### Success/Failure Configuration

| Feature | s1 | s2 | s3 | s4 | s5 | s6 | s7 | s8 | s9 |
|---------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| additionalTags | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| saveFile | | | ✓ | | | | | ✓ | |
| publishRegistry | | | ✓ | | | | ✓ | ✓ | |
| publishNamespace | | | ✓ | | | | | ✓ | |
| publishTags | | | ✓ | | | | ✓ | ✓ | |

## Running Tests

### Run All dockerProject Tests
```bash
cd plugin-integration-test
./gradlew -Pplugin_version=1.0.0 :dockerProject:integrationTest
```

### Run Individual Scenarios
```bash
# Scenario 1: Build mode
./gradlew -Pplugin_version=1.0.0 :dockerProject:scenario-1-build-mode:app-image:integrationTest

# Scenario 2: SourceRef mode
./gradlew -Pplugin_version=1.0.0 :dockerProject:scenario-2-sourceref-mode:app-image:integrationTest

# Scenario 3: Save and publish
./gradlew -Pplugin_version=1.0.0 :dockerProject:scenario-3-save-publish:app-image:integrationTest

# Scenario 4: Method lifecycle (MUST use pipeline task)
./gradlew -Pplugin_version=1.0.0 :dockerProject:scenario-4-method-lifecycle:app-image:runProjectscenario4appPipeline

# Scenario 5: ContextDir mode
./gradlew -Pplugin_version=1.0.0 :dockerProject:scenario-5-contextdir-mode:app-image:integrationTest

# Scenario 6: Repository mode
./gradlew -Pplugin_version=1.0.0 :dockerProject:scenario-6-repository-mode:app-image:integrationTest

# Scenario 7: Repository mode with registry
./gradlew -Pplugin_version=1.0.0 :dockerProject:scenario-7-repository-registry:app-image:integrationTest

# Scenario 8: Full imageName mode
./gradlew -Pplugin_version=1.0.0 :dockerProject:scenario-8-imagename-full:app-image:integrationTest

# Scenario 9: Config cache verification
./gradlew -Pplugin_version=1.0.0 :dockerProject:scenario-9-config-cache:app-image:integrationTest
```

### Cleanup
```bash
# Clean all build artifacts
./gradlew -Pplugin_version=1.0.0 :dockerProject:clean

# Remove Docker images created by tests
./gradlew -Pplugin_version=1.0.0 :dockerProject:cleanDockerImages

# Complete cleanup (both)
./gradlew -Pplugin_version=1.0.0 :dockerProject:cleanAll
```

## Translation to Underlying DSLs

The `dockerProject` DSL automatically translates to the three underlying DSLs:

```
dockerProject {           →  docker {
    image { ... }              images {
    test { ... }                   <sanitizedName> { ... }
    onSuccess { ... }          }
}                          }
                           
                           dockerTest {
                               composeStacks {
                                   <sanitizedName>Test { ... }
                               }
                           }
                           
                           dockerWorkflows {
                               pipelines {
                                   <sanitizedName>Pipeline {
                                       build { ... }
                                       test { ... }
                                       onTestSuccess { ... }
                                   }
                               }
                           }
```

### Generated Task Names

For an image named `project-scenario1-app`, the following tasks are generated:

| Task | Description |
|------|-------------|
| `prepareProjectscenario1appContext` | Prepares Docker build context (jarFrom mode) |
| `dockerBuildProjectscenario1app` | Builds the Docker image |
| `dockerTagProjectscenario1app` | Tags the image (if additional tags) |
| `composeUpProjectscenario1appTest` | Starts Docker Compose stack |
| `composeDownProjectscenario1appTest` | Stops Docker Compose stack |
| `runProjectscenario1appPipeline` | Executes the full pipeline |

## Troubleshooting

### Container Not Running After composeUp Shows UP-TO-DATE

If Gradle shows `composeUpProjectscenario#appTest UP-TO-DATE` but the container isn't running, the compose task
may have been cached incorrectly. Use `--rerun-tasks`:

```bash
./gradlew :dockerProject:scenario-#:app-image:integrationTest --rerun-tasks
```

### Method Lifecycle Tests Fail with "Stack name not configured"

Method lifecycle requires running the **pipeline task**, not the integrationTest task directly. The pipeline sets
required system properties (`docker.compose.lifecycle`, `docker.compose.stack`) that the `@ComposeUp` annotation needs.

```bash
# Wrong - direct integrationTest doesn't set system properties
./gradlew :dockerProject:scenario-4-method-lifecycle:app-image:integrationTest  # FAILS

# Correct - pipeline sets system properties
./gradlew :dockerProject:scenario-4-method-lifecycle:app-image:runProjectscenario4appPipeline  # WORKS
```

### Port Already in Use

Each scenario uses a unique port (9300-9304). If you see "port already in use" errors:

1. Check for leftover containers: `docker ps -a`
2. Stop and remove them: `docker compose -p <project-name> down -v`
3. Or use the cleanup tasks: `./gradlew :dockerProject:cleanDockerImages`

### Context Path Does Not Exist

For jarFrom mode, ensure the `prepareContext` task dependency is set on the pipeline:

```groovy
afterEvaluate {
    tasks.named('runProjectscenario#appPipeline') {
        dependsOn tasks.named('prepareProjectscenario#appContext')
    }
}
```
