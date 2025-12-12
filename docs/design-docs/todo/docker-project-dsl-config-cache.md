# Docker Project DSL - Configuration Cache Compatibility Plan

## Status: TODO

## Overview

This document outlines the plan to make the `dockerProject` DSL fully compatible with Gradle's configuration cache, ensuring compliance with Gradle 9 and 10 requirements as specified in `docs/design-docs/gradle-9-and-10-compatibility.md`.

**Important**: Since the plugin is not yet published and has no external users, backward compatibility is NOT required. The current non-compliant implementation will be **replaced entirely** with a configuration-cache-compatible architecture.

**Key Principle**: The user-facing `dockerProject` DSL syntax remains **unchanged**. Only the internal implementation changes.

---

## Current `dockerProject` DSL (User-Facing)

The `dockerProject` DSL is a self-contained, multi-image workflow DSL. Users configure everything inline without needing separate `docker` or `dockerOrch` blocks.

### Multiple Image Support

The `dockerProject` DSL supports building multiple images via the `images { }` container. This is useful for:
- Building the main service image alongside test fixture images (e.g., database with seed data)
- Building related images that are tested together (e.g., API + worker)
- Creating supporting infrastructure images for integration testing

Each image is defined with a **named block** inside `images { }`. One image must be designated as **primary** - this image receives the `onSuccess.additionalTags` after tests pass.

```groovy
dockerProject {
    images {
        // Primary image - receives onSuccess.additionalTags
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            primary.set(true)  // This image receives additionalTags on success
        }

        // Supporting image - test fixture database with seed data
        testDb {
            imageName.set('test-db')
            tags.set(['latest'])
            contextDir.set('src/test/docker/db')
            // Not primary - doesn't receive onSuccess tags
        }
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')  // Uses both images
        waitForHealthy.set(['app', 'db'])
    }

    onSuccess {
        additionalTags.set(['tested'])  // Applied to myApp only (primary image)
    }
}
```

**Key Rules:**
- At least one image must be defined
- Exactly one image must have `primary.set(true)` (or be the only image)
- If only one image is defined, it's automatically primary
- `onSuccess.additionalTags` applies **only** to the primary image

### Image Naming Modes

The `dockerProject.images.<name>` block supports two modes for specifying the image name, consistent with the `docker` DSL:

#### Image Name Mode

Use individual properties to specify the image identity:

```groovy
dockerProject {
    images {
        myApp {
            // Image Name Mode: specify imageName, optionally with registry and namespace
            imageName.set('my-app')                      // Required: image name
            registry.set('registry.example.com')         // Optional: registry host
            namespace.set('myorg')                       // Optional: namespace/org
            tags.set(['latest', '1.0.0'])

            jarFrom.set(':app:jar')
            buildArgs.put('VERSION', '1.0.0')
            labels.put('title', 'My App')
        }
    }
    // ... test, onSuccess blocks
}
```

#### Repository Mode

Use `repository` to combine namespace and image name:

```groovy
dockerProject {
    images {
        myApp {
            // Repository Mode: specify repository (combines namespace/imageName)
            repository.set('myorg/my-app')               // Required: namespace/imageName
            registry.set('registry.example.com')         // Optional: registry host
            tags.set(['latest', '1.0.0'])

            jarFrom.set(':app:jar')
            buildArgs.put('VERSION', '1.0.0')
            labels.put('title', 'My App')
        }
    }
    // ... test, onSuccess blocks
}
```

**Note**: `imageName` and `repository` are mutually exclusive. Specifying both results in a validation error.

### Image Modes Overview

Each image block in `dockerProject.images` operates in one of two modes:

| Mode | When Active | Description |
|------|-------------|-------------|
| **Build Mode** | `jarFrom`, `contextTask`, or `contextDir` is set | Builds a new image from source |
| **Source Reference Mode** | `sourceRef` is set OR no build properties set | References an existing image |

**Key Rule**: Build Mode properties (`jarFrom`, `contextTask`, `contextDir`) and `sourceRef` are **mutually exclusive**. Setting any build property activates Build Mode; setting `sourceRef` activates Source Reference Mode.

### Build Mode: Context Options

When building an image, each `dockerProject.images.<name>` block supports three mutually exclusive ways to prepare the Docker build context:

| Property | Use Case | Description |
|----------|----------|-------------|
| `jarFrom` | Simple Java projects | Auto-generates a Copy task that copies JAR + Dockerfile to context |
| `contextTask` | Complex/non-Java projects | User provides full control over context preparation |
| `contextDir` | Pre-built contexts | Points to an existing directory as context |

#### Option 1: `jarFrom` (Simple Java Projects)

For Java projects that need a JAR file in the Docker context:

```groovy
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')              // Task path to JAR-producing task
            jarName.set('my-cool-app-v1.0.0.jar') // Optional: custom name (default: 'app.jar')
        }
    }
    // ...
}
```

**Supported `jarFrom` formats:**

| Format | Meaning |
|--------|---------|
| `'jar'` | Current project's `jar` task |
| `':jar'` | Root project's `jar` task |
| `':app:jar'` | Subproject `:app`'s `jar` task |
| `':sub:project:jar'` | Nested subproject's `jar` task |

#### Option 2: `contextTask` (Complex/Non-Java Projects)

For projects that need full control over context preparation (Java with config files, Python, ML, etc.):

```groovy
// First, register the context preparation task
def prepareContextTask = tasks.register('prepareContext', Copy) {
    into layout.buildDirectory.dir('docker-context/myApp')
    from('src/main/docker')  // Dockerfile
    from(project(':app').tasks.named('jar')) { rename { 'app.jar' } }
    from('config/application.yml')
    from('config/logback.xml')
}

dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            contextTask = prepareContextTask  // Reference the task
        }
    }
    // ...
}
```

**Configuration Cache Compatibility:**

The `contextTask` property uses a dual-property pattern for configuration cache safety, matching the `docker` DSL:

| Property | Type | Config Cache | Purpose |
|----------|------|--------------|---------|
| `contextTask` | `TaskProvider<Task>` | ❌ Not cached (`@Internal`) | Convenient DSL assignment |
| `contextTaskName` | `Property<String>` | ✅ Cached (`@Input`) | Task name for cache |
| `contextTaskPath` | `Property<String>` | ✅ Cached (`@Input`) | Full task path for cache |

When you assign `contextTask`, the plugin automatically populates `contextTaskName` and `contextTaskPath` from the task provider. During configuration cache replay, the plugin uses the string properties to look up the task.

**Alternative: Direct string path assignment** (for advanced users or scripted builds):
```groovy
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            // Use task path directly instead of TaskProvider
            contextTaskPath.set(':app-image:prepareContext')
        }
    }
}
```

**Dependency Examples for `contextTask`:**

**Scenario A: Self-contained project (no subproject dependencies)**
```groovy
// All files are local to this project
def prepareContextTask = tasks.register('prepareContext', Copy) {
    into layout.buildDirectory.dir('docker-context/myApp')
    from('src/main/docker')      // Dockerfile
    from('src/main/python')       // Python source
    from('models/')               // Model files
    from('requirements.txt')
}

dockerProject {
    images {
        myApp {
            contextTask = prepareContextTask
        }
    }
}
```

**Scenario B: Depend on another project's task output**
```groovy
// Gradle automatically infers task dependency from `project(':app').tasks.named('jar')`
def prepareContextTask = tasks.register('prepareContext', Copy) {
    into layout.buildDirectory.dir('docker-context/myApp')
    from('src/main/docker')
    from(project(':app').tasks.named('jar')) { rename { 'app.jar' } }

    // Or depend on non-Java project output
    from(project(':model-training').tasks.named('trainModel')) {
        include '*.pkl'
    }
}

dockerProject {
    images {
        myApp {
            contextTask = prepareContextTask
        }
    }
}
```

**Scenario C: Explicit task dependencies**
```groovy
def prepareContextTask = tasks.register('prepareContext', Copy) {
    into layout.buildDirectory.dir('docker-context/myApp')
    from('src/main/docker')
    from('src/main/python')
    from('models/')

    // Explicit dependency on any task
    dependsOn(':data-prep:processData')
    dependsOn(':model-training:exportModel')
}

dockerProject {
    images {
        myApp {
            contextTask = prepareContextTask
        }
    }
}
```

#### Option 3: `contextDir` (Pre-existing Context)

For cases where the Docker context already exists:

```groovy
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            contextDir.set('docker/')  // Pre-existing directory
        }
    }
    // ...
}
```

### Dockerfile Customization

By default, `dockerProject` expects a Dockerfile at `src/main/docker/Dockerfile`. You can customize the Dockerfile location using two properties:

| Property | Use Case | Description |
|----------|----------|-------------|
| `dockerfile` | Different path | Full path to Dockerfile relative to project root |
| `dockerfileName` | Different name, default location | Just the filename (uses default `src/main/docker/` directory) |

#### Using `dockerfileName` (Same Location, Different Name)

When your Dockerfile is in the default location (`src/main/docker/`) but has a different name:

```groovy
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            dockerfileName.set('Dockerfile.production')  // Uses src/main/docker/Dockerfile.production
            jarFrom.set(':app:jar')
        }
    }
    // ...
}
```

Common use cases for `dockerfileName`:
- `Dockerfile.production` vs `Dockerfile.development`
- `Dockerfile.jvm` vs `Dockerfile.native`
- `Dockerfile.alpine` vs `Dockerfile.debian`

#### Using `dockerfile` (Different Path)

When your Dockerfile is in a completely different location:

```groovy
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            dockerfile.set('docker/custom/MyDockerfile')  // Full path from project root
            jarFrom.set(':app:jar')
        }
    }
    // ...
}
```

#### Dockerfile Properties in Different Build Modes

| Build Mode | `dockerfile` Behavior | `dockerfileName` Behavior |
|------------|----------------------|---------------------------|
| `jarFrom` | Copies specified Dockerfile to context | Uses specified name from `src/main/docker/` |
| `contextTask` | User controls Dockerfile in task | Ignored (user controls context) |
| `contextDir` | Must exist in specified directory | Ignored (looks in contextDir) |

**Note**: `dockerfile` and `dockerfileName` are mutually exclusive. Specifying both results in a validation error.

### Source Reference Mode: Using Existing Images

When you want to use an existing image (already built locally or available in a registry) without building it, use Source Reference Mode. This is useful for:

- Testing images built by another process or CI job
- Adding tags to existing images
- Publishing pre-built images to registries
- Running integration tests against third-party images

#### Using `sourceRef` (Full Image Reference)

Specify the complete image reference as a single string:

```groovy
dockerProject {
    images {
        existingApp {
            // Reference an existing image - no build step
            sourceRef.set('myregistry.com/myorg/existing-app:v1.0')
        }
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }

    onSuccess {
        additionalTags.set(['tested', 'verified'])  // Applied to primary image only
        publish {
            to('production') {
                registry.set('prod-registry.com')
                namespace.set('production')
                tags.set(['latest', 'v1.0'])
            }
        }
    }
}
```

#### Using Component Properties (Without Build Context)

Alternatively, use the component properties (`imageName`, `registry`, `namespace`, `tags`) **without** any build context properties:

```groovy
dockerProject {
    images {
        existingApp {
            // Reference existing image using components
            // NO jarFrom, contextTask, or contextDir = Source Reference Mode
            imageName.set('existing-app')
            registry.set('myregistry.com')
            namespace.set('myorg')
            tags.set(['v1.0'])
        }
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }

    onSuccess {
        additionalTags.set(['tested'])  // Applied to primary image only
    }
}
```

#### Source Reference Mode vs Build Mode

| Aspect | Build Mode | Source Reference Mode |
|--------|------------|----------------------|
| **Activator** | `jarFrom`, `contextTask`, or `contextDir` | `sourceRef` OR no build properties |
| **Build step** | Runs `docker build` | Skipped |
| **Image must exist** | No (will be created) | Yes (must be pullable or local) |
| **Use case** | Creating new images | Testing/tagging existing images |
| **Generated tasks** | `dockerBuild*`, then test/tag/publish | Only test/tag/publish (no build) |

#### `sourceRef` Format Examples

| Format | Description |
|--------|-------------|
| `'nginx:latest'` | Docker Hub official image |
| `'myorg/myapp:v1.0'` | Docker Hub with namespace |
| `'registry.example.com/myapp:v1.0'` | Private registry |
| `'registry.example.com/myorg/myapp:v1.0'` | Private registry with namespace |
| `'gcr.io/my-project/myapp:v1.0'` | Google Container Registry |
| `'123456789.dkr.ecr.us-east-1.amazonaws.com/myapp:v1.0'` | AWS ECR |

#### Validation Rules

1. **Mutual Exclusivity**: Cannot set `sourceRef` with any build property (`jarFrom`, `contextTask`, `contextDir`)
2. **Image Identity Required**: In Source Reference Mode, must have either:
   - `sourceRef` set, OR
   - At least `imageName` (or `repository`) set
3. **Image Must Exist**: When using Source Reference Mode, the image must exist locally or be pullable (based on `pullPolicy`)
4. **Pull Policy Only in Source Reference Mode**: Cannot set `pullPolicy` with any build property

**Error example:**
```groovy
// INVALID - cannot use both sourceRef and jarFrom
dockerProject {
    images {
        myApp {
            sourceRef.set('existing:v1.0')
            jarFrom.set(':app:jar')  // ❌ Error: mutual exclusivity violation
        }
    }
}
```

### Pull Policy: Controlling Image Retrieval

When using Source Reference Mode (referencing existing images), you may need to control whether the plugin attempts to pull the image from a registry. The `pullPolicy` property provides this control using the shared `PullPolicy` enum.

**PullPolicy Enum:**
```groovy
// Defined in: com.kineticfire.gradle.docker.PullPolicy
// Shared by both 'docker' and 'dockerProject' DSLs
enum PullPolicy {
    NEVER,      // Fail if image not found locally (default - safest)
    IF_MISSING, // Pull only if not found locally
    ALWAYS      // Always pull from registry (useful for :latest tags)
}
```

| Value | Description | Use Case |
|-------|-------------|----------|
| `PullPolicy.NEVER` (default) | Fail if image not found locally | CI builds where image must be pre-built |
| `PullPolicy.IF_MISSING` | Pull only if not found locally | Development, testing third-party images |
| `PullPolicy.ALWAYS` | Always pull latest from registry | When using `:latest` or mutable tags |

**Example - Pull if missing:**
```groovy
import com.kineticfire.gradle.docker.PullPolicy

dockerProject {
    images {
        nginx {
            sourceRef.set('nginx:1.25')
            pullPolicy.set(PullPolicy.IF_MISSING)  // Pull if not local
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['nginx'])
    }
}
```

**Example - Always pull (for :latest):**
```groovy
import com.kineticfire.gradle.docker.PullPolicy

dockerProject {
    images {
        myApp {
            sourceRef.set('myregistry.com/myapp:latest')
            pullPolicy.set(PullPolicy.ALWAYS)  // Always get latest
        }
    }
    test { ... }
}
```

**Example - Hybrid: build one image, pull another:**
```groovy
import com.kineticfire.gradle.docker.PullPolicy

dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            jarFrom.set(':app:jar')
            primary.set(true)
            // pullPolicy not applicable - this is Build Mode
        }
        database {
            sourceRef.set('postgres:15')
            pullPolicy.set(PullPolicy.IF_MISSING)  // Pull postgres if needed
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app', 'db'])
    }
    onSuccess {
        additionalTags.set(['tested'])  // Applied to myApp (primary)
    }
}
```

**With static import (cleaner DSL):**
```groovy
import static com.kineticfire.gradle.docker.PullPolicy.*

dockerProject {
    images {
        nginx {
            sourceRef.set('nginx:latest')
            pullPolicy.set(ALWAYS)
        }
    }
    // ...
}
```

**Validation Rule:** `pullPolicy` can only be set in Source Reference Mode. Setting it with any build property (`jarFrom`, `contextTask`, `contextDir`) results in a validation error:
```groovy
// INVALID - pullPolicy only applies in Source Reference Mode
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            jarFrom.set(':app:jar')
            pullPolicy.set(PullPolicy.IF_MISSING)  // ❌ Error: pullPolicy only in Source Reference Mode
        }
    }
}
```

### Build Arguments and Labels

Both `buildArgs` and `labels` are `MapProperty<String, String>` types that accept multiple key-value pairs:

```groovy
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')

            // Build arguments - multiple entries supported
            // Option 1: Individual puts
            buildArgs.put('VERSION', '1.0.0')
            buildArgs.put('BUILD_DATE', '2025-01-15')
            buildArgs.put('GIT_COMMIT', 'abc123')

            // Option 2: Set all at once (replaces existing)
            buildArgs.set([
                'VERSION': '1.0.0',
                'BUILD_DATE': '2025-01-15',
                'GIT_COMMIT': 'abc123'
            ])

            // Option 3: Merge with existing (putAll)
            buildArgs.putAll([
                'VERSION': '1.0.0',
                'BUILD_DATE': '2025-01-15'
            ])

            // Labels - same options apply
            labels.put('org.opencontainers.image.title', 'My Application')
            labels.put('org.opencontainers.image.version', '1.0.0')
            labels.put('org.opencontainers.image.vendor', 'My Company')
            labels.put('org.opencontainers.image.authors', 'team@example.com')

            // Or set all at once
            labels.set([
                'org.opencontainers.image.title': 'My Application',
                'org.opencontainers.image.version': '1.0.0',
                'org.opencontainers.image.vendor': 'My Company'
            ])
        }
    }
    // ...
}
```

**Available Methods on MapProperty:**

| Method | Description |
|--------|-------------|
| `put(key, value)` | Add a single entry |
| `putAll(map)` | Add multiple entries (merges with existing) |
| `set(map)` | Replace all entries with a new map |

### Test Block: Lifecycle and Wait Strategies

The `test` block configures how integration tests run against Docker Compose stacks.

#### Test Lifecycle

The `lifecycle` property controls when the compose stack is started and stopped. It uses the `Lifecycle` enum:

**Lifecycle Enum:**
```groovy
// Defined in: com.kineticfire.gradle.docker.Lifecycle
enum Lifecycle {
    CLASS,   // Stack starts once before all tests, stops after all tests
    METHOD   // Stack starts/stops for each test method
}
```

| Value | Description | Use Case |
|-------|-------------|----------|
| `Lifecycle.CLASS` (default) | Stack starts once before all tests, stops after all tests | Stateless/read-only tests, faster execution |
| `Lifecycle.METHOD` | Stack starts/stops for each test method | State-mutating tests, test isolation |

**Example - Class lifecycle (default):**
```groovy
import com.kineticfire.gradle.docker.Lifecycle

test {
    compose.set('src/integrationTest/resources/compose/app.yml')
    waitForHealthy.set(['app'])
    timeoutSeconds.set(60)
    testTaskName.set('integrationTest')
    // lifecycle.set(Lifecycle.CLASS)  // Default - can be omitted
}
```

Flow: `composeUp → test A → test B → test C → composeDown`

**Example - Method lifecycle:**
```groovy
import com.kineticfire.gradle.docker.Lifecycle

test {
    compose.set('src/integrationTest/resources/compose/app.yml')
    waitForHealthy.set(['app'])
    timeoutSeconds.set(60)
    testTaskName.set('integrationTest')
    lifecycle.set(Lifecycle.METHOD)  // Fresh container for each test
}
```

Flow: `composeUp → test A → composeDown → composeUp → test B → composeDown → ...`

**With static import (cleaner DSL):**
```groovy
import static com.kineticfire.gradle.docker.Lifecycle.*

test {
    compose.set('src/integrationTest/resources/compose/app.yml')
    waitForHealthy.set(['app'])
    timeoutSeconds.set(60)
    testTaskName.set('integrationTest')
    lifecycle.set(METHOD)  // Clean syntax with static import
}
```

**When to use each:**

| Lifecycle | Best For | Trade-off |
|-----------|----------|-----------|
| `Lifecycle.CLASS` | Stateless APIs, read-only operations | Faster, but tests may share state |
| `Lifecycle.METHOD` | Database writes, state mutations | Slower, but complete isolation |

**No annotations required in test code:**

With the DSL-configured lifecycle, test code remains clean - no `@ExtendWith` or `@DockerCompose` annotations needed:

```groovy
// Test code - lifecycle is handled by the plugin based on DSL configuration
class MyIntegrationSpec extends Specification {

    def "test endpoint A"() {
        when:
        def response = get("http://localhost:8080/api/endpoint")

        then:
        response.statusCode == 200
    }

    def "test endpoint B - fresh container if lifecycle=METHOD"() {
        when:
        def response = post("http://localhost:8080/api/data", [name: "test"])

        then:
        response.statusCode == 201
    }
}
```

#### Wait Strategies

The `test` block supports two wait strategies that can be used **independently or together**:

| Property | Description | Container State |
|----------|-------------|-----------------|
| `waitForHealthy` | Wait for containers to pass their HEALTHCHECK | `healthy` |
| `waitForRunning` | Wait for containers to be in running state | `running` |

**Key behaviors:**
- Both properties are `ListProperty<String>` accepting service names
- **Both can be used simultaneously** - all services in both lists will be waited on
- `timeoutSeconds` and `pollSeconds` apply to **both** wait strategies
- Default timeout: 60 seconds; Default poll interval: 2 seconds

**Example - Using both wait strategies:**
```groovy
import com.kineticfire.gradle.docker.Lifecycle

test {
    compose.set('src/integrationTest/resources/compose/app.yml')

    // Wait for 'app' and 'db' to be HEALTHY (requires HEALTHCHECK in Dockerfile)
    waitForHealthy.set(['app', 'db'])

    // Wait for 'redis' and 'cache' to be RUNNING (no healthcheck needed)
    waitForRunning.set(['redis', 'cache'])

    // Timeout and poll interval apply to ALL services in both lists
    timeoutSeconds.set(60)
    pollSeconds.set(2)

    testTaskName.set('integrationTest')
    lifecycle.set(Lifecycle.CLASS)  // or Lifecycle.METHOD
}
```

**When to use each:**
- **`waitForHealthy`**: For services with a HEALTHCHECK defined in their Dockerfile (e.g., application servers, databases with health endpoints)
- **`waitForRunning`**: For services without a HEALTHCHECK (e.g., Redis, simple utilities) or when you only need the container to be started

**Example - waitForHealthy only:**
```groovy
test {
    compose.set('src/integrationTest/resources/compose/app.yml')
    waitForHealthy.set(['app'])  // All services have healthchecks
    timeoutSeconds.set(60)
    testTaskName.set('integrationTest')
}
```

**Example - waitForRunning only:**
```groovy
test {
    compose.set('src/integrationTest/resources/compose/app.yml')
    waitForRunning.set(['app', 'db', 'redis'])  // Just wait for running state
    timeoutSeconds.set(30)
    testTaskName.set('integrationTest')
}
```

### Multiple Test Configurations

The `dockerProject` DSL supports multiple named test configurations via the `tests { }` block. This enables:

1. **Test Organization**: Group related tests together for readability and maintainability
2. **Performance Optimization**: Separate CLASS lifecycle tests (faster, shared container) from METHOD lifecycle tests (slower, isolated containers)
3. **Different Compose Configurations**: Different test groups can use different compose files with different services

#### Basic Structure

```groovy
import com.kineticfire.gradle.docker.Lifecycle

dockerProject {
    images {
        myApp { ... }  // Primary image (auto-primary when single image)
    }

    tests {
        // User-defined name - can be any meaningful identifier
        apiTests {
            compose.set('src/integrationTest/resources/compose/api.yml')
            waitForHealthy.set(['app'])
            lifecycle.set(Lifecycle.CLASS)
            testClasses.set(['com.example.api.**'])  // Package pattern
        }

        statefulTests {
            compose.set('src/integrationTest/resources/compose/stateful.yml')
            waitForHealthy.set(['app', 'db'])
            lifecycle.set(Lifecycle.METHOD)
            testClasses.set(['com.example.stateful.**'])
        }
    }

    onSuccess {
        additionalTags.set(['tested'])  // Only applied if ALL tests pass (to primary image)
    }
}
```

#### Auto-Generated Artifacts

For each named test configuration, the plugin auto-generates:

| Artifact | Naming Pattern | Example for `apiTests` |
|----------|----------------|------------------------|
| Test task | `{name}IntegrationTest` | `apiTestsIntegrationTest` |
| composeUp task | `composeUp{Name}` | `composeUpApiTests` |
| composeDown task | `composeDown{Name}` | `composeDownApiTests` |

All test configurations share the `integrationTest` source set by default:
- Source: `src/integrationTest/groovy/`
- Resources: `src/integrationTest/resources/`

#### Test Class Association

Tests are associated with configurations via package patterns using `testClasses`:

```groovy
tests {
    apiTests {
        compose.set('src/integrationTest/resources/compose/api.yml')
        testClasses.set(['com.example.api.**'])  // All classes in api package
    }

    dbTests {
        compose.set('src/integrationTest/resources/compose/db.yml')
        testClasses.set([
            'com.example.db.**',           // All classes in db package
            'com.example.repository.**'     // And repository package
        ])
    }
}
```

**Directory Structure (Shared Source Set):**
```
src/
  integrationTest/
    groovy/
      com/example/
        api/
          ApiHealthIT.groovy      ← Runs with apiTests config
          ApiEndpointsIT.groovy   ← Runs with apiTests config
        db/
          DatabaseIT.groovy       ← Runs with dbTests config
        repository/
          RepositoryIT.groovy     ← Runs with dbTests config
    resources/
      compose/
        api.yml
        db.yml
```

#### Test Execution Order

- **CLASS lifecycle tests**: Can run in parallel (if multiple CLASS configs exist)
- **METHOD lifecycle tests**: Run in definition order (sequential by nature due to port allocation)
- **Mixed**: CLASS lifecycle tests run first (in parallel), then METHOD lifecycle tests run sequentially

#### Pipeline Behavior

1. Build image
2. Run **all** test configurations
3. If **ALL** tests pass → execute `onSuccess`
4. If **ANY** test fails → skip `onSuccess`, execute `onFailure` (if defined)

#### Performance Benefits

Separating CLASS and METHOD lifecycle tests provides significant performance gains:

```
Example: 100 integration tests
- 80 read-only tests (can share container) → CLASS lifecycle
- 20 state-mutating tests (need isolation) → METHOD lifecycle

With single lifecycle (all METHOD):
  100 container startups × ~10 seconds = ~1000 seconds = 16+ minutes

With separated lifecycles:
  CLASS: 1 startup + 80 tests = ~10 seconds + test time
  METHOD: 20 startups × ~10 seconds = ~200 seconds
  Total container overhead: ~210 seconds = 3.5 minutes

Savings: 12+ minutes just on container lifecycle!
```

#### Full Example with Multiple Configurations

```groovy
import com.kineticfire.gradle.docker.Lifecycle

dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            // Single image is automatically primary
        }
    }

    tests {
        // Fast tests - share a single container
        apiTests {
            compose.set('src/integrationTest/resources/compose/api.yml')
            waitForHealthy.set(['app'])
            timeoutSeconds.set(60)
            lifecycle.set(Lifecycle.CLASS)  // Default - fast
            testClasses.set(['com.example.api.**', 'com.example.readonly.**'])
        }

        // Slow tests - fresh container per test for isolation
        statefulTests {
            compose.set('src/integrationTest/resources/compose/full-stack.yml')
            waitForHealthy.set(['app', 'db', 'redis'])
            timeoutSeconds.set(120)
            lifecycle.set(Lifecycle.METHOD)  // Isolated - slower but safe
            testClasses.set(['com.example.stateful.**', 'com.example.mutation.**'])
        }

        // Tests with different infrastructure
        messagingTests {
            compose.set('src/integrationTest/resources/compose/messaging.yml')
            waitForHealthy.set(['app', 'rabbitmq'])
            timeoutSeconds.set(90)
            lifecycle.set(Lifecycle.CLASS)
            testClasses.set(['com.example.messaging.**'])
        }
    }

    onSuccess {
        additionalTags.set(['tested', 'verified'])
    }
}
```

#### Properties Reference for Test Configurations

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `compose` | `Property<String>` | (required) | Path to Docker Compose file |
| `waitForHealthy` | `ListProperty<String>` | `[]` | Services to wait for healthy status |
| `waitForRunning` | `ListProperty<String>` | `[]` | Services to wait for running status |
| `lifecycle` | `Property<Lifecycle>` | `Lifecycle.CLASS` | Container lifecycle mode |
| `timeoutSeconds` | `Property<Integer>` | `60` | Timeout for waiting on containers |
| `pollSeconds` | `Property<Integer>` | `2` | Poll interval when waiting |
| `testClasses` | `ListProperty<String>` | (required) | Package patterns for test class association |

---

### onSuccess Block: Multiple Publish Targets

The `onSuccess` block supports publishing to multiple registries with different configurations, authentication, and tags per target. This addresses the common need to push images to both internal and public registries.

#### Simple Single-Registry Publishing

For simple cases with a single registry, use flat properties:

```groovy
onSuccess {
    additionalTags.set(['tested', 'stable'])
    saveFile.set('build/images/my-app.tar.gz')

    // Simple single-registry publish
    publishRegistry.set('localhost:5000')
    publishNamespace.set('myorg')
    publishTags.set(['latest', 'tested'])
}
```

#### Multiple Registry Publishing

For publishing to multiple registries, use the `publish { }` block with named targets:

```groovy
onSuccess {
    additionalTags.set(['tested', 'stable'])
    saveFile.set('build/images/my-app.tar.gz')

    publish {
        to('internal') {
            registry.set('registry.internal.com')
            namespace.set('platform')
            tags.set(['latest', 'tested', '1.0.0'])
            // No auth needed for internal registry
        }

        to('dockerhub') {
            registry.set('docker.io')
            repository.set('mycompany/my-app')  // Alternative to namespace
            tags.set(['latest', '1.0.0'])

            auth {
                username.set(providers.environmentVariable('DOCKERHUB_USER'))
                password.set(providers.environmentVariable('DOCKERHUB_TOKEN'))
            }
        }

        to('backup') {
            registry.set('backup.registry.com')
            namespace.set('backup-org')
            tags.set(['backup-latest'])

            auth {
                username.set('backupuser')
                password.set(providers.environmentVariable('BACKUP_REGISTRY_TOKEN'))
            }
        }
    }
}
```

#### Publish Target Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `registry` | `Property<String>` | Yes | Registry host (e.g., `docker.io`, `registry.internal.com`) |
| `namespace` | `Property<String>` | No | Namespace/organization for the image |
| `repository` | `Property<String>` | No | Full repository path (alternative to `namespace` + image name) |
| `tags` | `ListProperty<String>` | Yes | Tags to publish to this registry |
| `auth` | Block | No | Authentication credentials for this registry |

**Auth block properties:**

| Property | Type | Description |
|----------|------|-------------|
| `username` | `Property<String>` | Registry username |
| `password` | `Property<String>` | Registry password or token |

#### Design Decisions

1. **Mutual Exclusivity**: Cannot use both flat properties (`publishRegistry`, etc.) AND `publish { }` block together
2. **Image Name Inheritance**: Image name is inherited from `dockerProject.image` - no need to repeat it
3. **Named Targets**: Each target has a name (e.g., `'internal'`, `'dockerhub'`) for meaningful task names
4. **Per-Target Auth**: Different registries can have different authentication or no auth
5. **Per-Target Tags**: Different tags can be published to different registries

#### Generated Tasks

For multiple publish targets, separate tasks are generated:

| Target Name | Generated Task |
|-------------|----------------|
| `internal` | `dockerProjectPublishInternal` |
| `dockerhub` | `dockerProjectPublishDockerhub` |
| `backup` | `dockerProjectPublishBackup` |

The `runDockerProject` lifecycle task depends on all publish tasks.

#### Use Cases

| Use Case | Configuration |
|----------|---------------|
| Internal + Public | Push to internal registry for staging, Docker Hub for release |
| Multi-Cloud | Push to AWS ECR, GCP Artifact Registry, Azure Container Registry |
| Backup Strategy | Primary registry + backup/DR registry |
| Different Auth | Internal registry (no auth) + public registry (with auth) |
| Different Tags | `latest` on internal, `v1.0.0` only on public |

#### Full Example with Multiple Registries

```groovy
import com.kineticfire.gradle.docker.Lifecycle

dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        lifecycle.set(Lifecycle.CLASS)
    }

    onSuccess {
        additionalTags.set(['tested', 'verified'])  // Applied to primary image only
        saveFile.set('build/images/my-app.tar.gz')

        publish {
            to('internal') {
                registry.set('registry.internal.com')
                namespace.set('platform')
                tags.set(['latest', 'tested', '1.0.0'])
            }

            to('dockerhub') {
                registry.set('docker.io')
                repository.set('mycompany/my-app')
                tags.set(['latest', '1.0.0'])

                auth {
                    username.set(providers.environmentVariable('DOCKERHUB_USER'))
                    password.set(providers.environmentVariable('DOCKERHUB_TOKEN'))
                }
            }
        }
    }
}
```

---

### Full DSL Example (Single Image, Single Test Configuration)

For simple projects with a single image and single test configuration:

```groovy
import com.kineticfire.gradle.docker.Lifecycle

dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            jarName.set('my-app.jar')  // Optional: custom JAR name
            // primary.set(true)  // Automatic when only one image

            // Multiple build arguments
            buildArgs.put('VERSION', '1.0.0')
            buildArgs.put('BUILD_DATE', '2025-01-15')
            buildArgs.put('JAVA_VERSION', '21')

            // Multiple labels (OCI standard labels)
            labels.put('org.opencontainers.image.title', 'My App')
            labels.put('org.opencontainers.image.version', '1.0.0')
            labels.put('org.opencontainers.image.vendor', 'My Company')
        }
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])      // Wait for app to be healthy
        waitForRunning.set(['redis'])    // Wait for redis to be running
        timeoutSeconds.set(60)           // Applies to both
        testTaskName.set('integrationTest')
        lifecycle.set(Lifecycle.CLASS)   // Lifecycle.CLASS (default) or Lifecycle.METHOD
    }

    onSuccess {
        additionalTags.set(['tested', 'stable'])  // Applied to primary image only
        saveFile.set('build/images/my-app.tar.gz')
        publishRegistry.set('localhost:5000')
        publishNamespace.set('myorg')
        publishTags.set(['latest', 'tested'])
    }
}
```

### DSL Usage Examples

```groovy
// Example 1: Simple local image (most common)
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }
    // ...
}

// Example 2: Image with namespace (Docker Hub style)
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            namespace.set('myorg')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }
    // ...
}

// Example 3: Image with repository (alternative to namespace+imageName)
dockerProject {
    images {
        myApp {
            repository.set('myorg/my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }
    // ...
}

// Example 4: Private registry with repository
dockerProject {
    images {
        myApp {
            repository.set('myorg/my-app')
            registry.set('registry.example.com')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }
    // ...
}

// Example 5: Private registry with Image Name Mode
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            registry.set('registry.example.com')
            namespace.set('myorg')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }
    // ...
}

// Example 6: Multiple images (primary + test fixture)
dockerProject {
    images {
        // Primary image - receives onSuccess.additionalTags
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            primary.set(true)
        }

        // Test fixture image - database with seed data
        testDb {
            imageName.set('test-db')
            tags.set(['latest'])
            contextDir.set('src/test/docker/db')
        }
    }

    test {
        compose.set('src/integrationTest/resources/compose/full-stack.yml')
        waitForHealthy.set(['app', 'db'])
    }

    onSuccess {
        additionalTags.set(['tested'])  // Applied to myApp only
    }
}
```

---

## Current State

The `DockerProjectRunTask` (which powers the `dockerProject` DSL) is currently marked as `notCompatibleWithConfigurationCache()`. This is **unacceptable** for a plugin that must be Gradle 9 and 10 compliant. The current implementation must be replaced.

---

## Why the Current Implementation Is Not Configuration Cache Compatible

### Problem 1: Non-Serializable Nested Objects

The `DockerProjectRunTask` holds nested spec objects that contain non-serializable references:

```
DockerProjectRunTask
  └── Property<DockerProjectSpec> (@Internal)
        └── DockerProjectImageSpec
              └── (potential TaskProvider references)
        └── DockerProjectTestSpec
              └── (nested objects)
        └── DockerProjectSuccessSpec
              └── (nested objects)
```

**Specific violations:**

1. **Nested spec objects** contain `ObjectFactory`-created instances that may hold transitive references to non-serializable state.

2. **Task references** stored as `TaskProvider<Task>` cannot be serialized because they hold references to `Project` and other non-serializable Gradle internals.

### Problem 2: Dynamic Task Execution at Runtime

The `DockerProjectRunTask` executes other tasks programmatically during its `@TaskAction`:

```groovy
// Dynamic lookup at runtime
def task = taskLookup.findByName(taskName)
// Dynamic execution at runtime
taskLookup.execute(task)
```

This violates the Gradle 9/10 requirement from the golden rules:
> "No `afterEvaluate` and no cross-task mutation at execution - Do not configure task B from task A's `@TaskAction`. Wire via inputs/providers and dependencies."

Configuration cache requires that all task relationships be established at configuration time, not discovered and executed dynamically at runtime.

### Problem 3: `Task.project` Invocation at Execution Time

The configuration cache warnings indicate `Task.project` is being invoked at execution time. This occurs because:

1. `TaskContainer` operations (used by `TaskExecutionService`) internally reference `Project`
2. When task actions are executed dynamically, those actions may internally reference `project`

This violates the golden rule:
> "No `Project` at execution time - Inside `@TaskAction`, never call `task.project` or any `Project` APIs."

### Summary of Violations

| Issue | Location | Gradle Rule Violated |
|-------|----------|---------------------|
| Non-serializable nested specs | `DockerProjectRunTask` | Cannot serialize complex object graphs |
| Dynamic task lookup | Runtime task execution | No cross-task execution at runtime |
| Dynamic task execution | `TaskLookup.execute()` | Wire via dependencies, not runtime calls |
| `Task.project` access | `TaskExecutionService` internals | No Project at execution time |

---

## Recommended Solution: Task Dependency Architecture

### Approach

Eliminate dynamic task execution entirely and instead generate a graph of Gradle tasks with proper dependencies at configuration time. This is the idiomatic Gradle approach and provides full configuration cache compatibility.

### Current Architecture (Not Configuration Cache Compatible)

```
User DSL
    ↓
DockerProjectExtension (holds nested spec objects)
    ↓
DockerProjectRunTask (@TaskAction)
    ├── Holds Property<DockerProjectSpec> with nested objects
    ├── Dynamically executes tasks at runtime via TaskLookup
    └── Marked: notCompatibleWithConfigurationCache()
```

### New Architecture (Configuration Cache Compatible)

```
User DSL
    ↓
DockerProjectExtension (holds nested spec objects) ← UNCHANGED
    ↓
DockerProjectTaskGenerator (at configuration time)
    ├── Reads specs and generates task dependency graph
    ├── Creates individual tasks with flattened, serializable inputs
    └── Wires dependencies via dependsOn/finalizedBy
    ↓
Generated Task Graph (all standard Gradle tasks)
    ├── dockerBuildMyApp (existing task type)
    ├── composeUpMyAppTest (existing task type)
    ├── integrationTest (user's test task)
    ├── composeDownMyAppTest (existing task type)
    ├── dockerProjectTagOnSuccess (new lightweight task)
    ├── dockerSaveMyApp (existing task type)
    └── dockerPublishMyApp (existing task type)
    ↓
runDockerProject (lifecycle task, dependsOn the graph)
```

### Key Design Principles

#### 1. Configuration-Time Wiring

All task relationships are established during configuration, not execution:

```groovy
// Plugin generates this at configuration time
project.afterEvaluate {
    if (extension.image.name.isPresent() || extension.image.repository.isPresent()) {
        def generator = new DockerProjectTaskGenerator()
        generator.generate(project, extension)
    }
}
```

#### 2. File-Based State Communication

Use output files to communicate state between pipeline steps:

```groovy
// Test task writes result to file
project.tasks.named(testTaskName) {
    def resultFile = project.layout.buildDirectory.file("docker-project/test-result.json")
    outputs.file(resultFile)

    doLast {
        def result = [
            success: !state.failure,
            timestamp: System.currentTimeMillis()
        ]
        resultFile.get().asFile.text = JsonOutput.toJson(result)
    }
}

// Downstream tasks read via input file
project.tasks.register('dockerProjectTagOnSuccess', DockerProjectTagOnSuccessTask) {
    testResultFile.set(project.layout.buildDirectory.file("docker-project/test-result.json"))
    onlyIf { testResultFile.get().asFile.exists() }
}
```

#### 3. Flattened Input Properties

All task inputs are primitive types or Provider types - no nested spec objects:

```groovy
abstract class DockerProjectTagOnSuccessTask extends DefaultTask {

    @Input
    abstract Property<String> getImageName()

    @Input
    abstract ListProperty<String> getAdditionalTags()

    @InputFile
    abstract RegularFileProperty getTestResultFile()

    @Inject
    abstract ExecOperations getExecOperations()

    @TaskAction
    void tagImage() {
        def resultFile = testResultFile.get().asFile
        if (!resultFile.exists()) {
            throw new GradleException("Test result file not found")
        }

        def result = new JsonSlurper().parse(resultFile)
        if (!result.success) {
            logger.lifecycle("Tests failed, skipping additional tags")
            return
        }

        def image = imageName.get()
        additionalTags.get().each { tag ->
            execOperations.exec {
                commandLine 'docker', 'tag', "${image}:latest", "${image}:${tag}"
            }
        }
    }
}
```

### Benefits of This Approach

| Aspect | Current (`DockerProjectRunTask`) | New (Task Dependencies) |
|--------|----------------------------------|------------------------|
| Configuration Cache | Not compatible | Fully compatible |
| Task Graph Visibility | Hidden (runtime execution) | Visible (`./gradlew tasks --all`) |
| Build Caching | Limited | Full (per-step caching) |
| Parallelization | None | Gradle-managed |
| State Communication | In-memory objects | File-based providers |
| Gradle 10 Ready | No | Yes |
| Incremental Builds | No | Yes (per-step up-to-date checks) |

---

## What Changes vs What Stays

| Component | Current | New | Change? |
|-----------|---------|-----|---------|
| User DSL syntax | `dockerProject { image { ... } }` | `dockerProject { images { name { ... } } }` | **Multi-image container** |
| `DockerProjectExtension` | Holds specs | Same | **No change** |
| `ProjectImageSpec` | Has `name` property | Add `repository`, `jarName`, `contextTask` | **Minor additions** |
| `ProjectTestSpec` | Nested spec | Add `lifecycle` property | **Minor addition** |
| `DockerProjectSuccessSpec` | Nested spec | Same | **No change** |
| `DockerProjectRunTask` | Orchestrates at runtime | **DELETE** | **Removed** |
| Task generation | In `DockerProjectRunTask` | `DockerProjectTaskGenerator` | **New** |
| Task wiring | Runtime dynamic | Configuration-time dependencies | **New** |

---

## Generated Task Graph Example

For a full scenario (build → test → tag → save → publish), the generator creates:

```
runDockerProject
    └── dependsOn: dockerPublishMyApp

dockerPublishMyApp
    └── dependsOn: dockerSaveMyApp

dockerSaveMyApp
    └── dependsOn: dockerProjectTagOnSuccess

dockerProjectTagOnSuccess
    ├── dependsOn: integrationTest
    ├── inputs: testResultFile, imageName, additionalTags
    └── onlyIf: testResultFile.exists && testsPassed

integrationTest
    ├── dependsOn: composeUpMyAppTest
    ├── dependsOn: dockerBuildMyApp
    ├── finalizedBy: composeDownMyAppTest
    └── outputs: test-result.json

composeUpMyAppTest
    └── dependsOn: dockerBuildMyApp

dockerBuildMyApp
    └── dependsOn: prepareDockerContext (from jarFrom)
```

---

## Implementation Plan

Since the plugin has no external users, this is a **clean replacement** rather than a migration. The current non-compliant implementation will be removed entirely.

### Phase 1: Add New Properties to Spec Classes

**Goal**: Add new properties to `ProjectImageSpec`, `ProjectTestSpec`, add support for multiple images, and add support for multiple test configurations.

**Tasks for Multiple Image Support**:
1. Rename `name` property to `imageName` in `ProjectImageSpec`
   - Provides consistency with `docker.images.<name>` DSL
   - Update all references from `name` to `imageName`
2. Add `Property<Boolean> primary` to `ProjectImageSpec`
   - Convention: `primary.convention(false)`
   - Designates which image receives `onSuccess.additionalTags`
3. Create `NamedDomainObjectContainer<ProjectImageSpec> images` in `DockerProjectExtension`
   - Replaces singular `image` block with named container
   - Each image is identified by its DSL block name (e.g., `myApp`, `testDb`)
   - Located in `DockerProjectExtension`
4. Add validation for primary image
   - At least one image must be defined
   - Exactly one image must have `primary.set(true)` (if multiple images)
   - If only one image is defined, it's automatically primary
5. Update `DockerProjectTaskGenerator` to handle multiple images
   - Generate `dockerBuild{Name}` task for each image in build mode
   - Wire all build tasks to complete before test phase
   - Apply `onSuccess.additionalTags` only to primary image
6. Update task naming conventions
   - Build tasks: `dockerBuild{ImageBlockName}` (e.g., `dockerBuildMyApp`, `dockerBuildTestDb`)
   - Tag-on-success: only applies to primary image
7. Update `DockerProjectSpec.isConfigured()` to check `images` container
8. Update translator code to iterate over images container

**Tasks for Multiple Test Configurations**:
1. Create `ProjectTestConfigSpec` class for named test configurations
   - Properties: `compose`, `waitForHealthy`, `waitForRunning`, `lifecycle`, `timeoutSeconds`, `pollSeconds`, `testClasses`
   - `testClasses` is `ListProperty<String>` for package patterns
   - Located in `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/ProjectTestConfigSpec.groovy`
2. Add `NamedDomainObjectContainer<ProjectTestConfigSpec> tests` to `DockerProjectExtension`
   - User-defined names (e.g., `apiTests`, `statefulTests`)
   - Auto-generates test tasks named `{name}IntegrationTest`
3. Update `DockerProjectSpec` to include both `test` (single) and `tests` (multiple) blocks
   - `test { }` - single test configuration (backward compatible)
   - `tests { name { } }` - multiple named test configurations
   - Validation: cannot use both `test` and `tests` simultaneously
4. Update `DockerProjectTaskGenerator` to handle multiple test configurations
   - Generate separate test tasks for each named config
   - Configure test class filtering via Gradle's `Test.filter.includeTestsMatching()`
   - Wire CLASS lifecycle tests for parallel execution where possible
   - Wire METHOD lifecycle tests for sequential execution

**Tasks for ProjectImageSpec**:
1. Add `Property<String> repository` to `ProjectImageSpec`
   - Convention: `repository.convention('')`
   - Validation: `imageName` and `repository` are mutually exclusive
2. Add `Property<String> jarName` to `ProjectImageSpec`
   - Convention: `jarName.convention('app.jar')`
   - Only applies when `jarFrom` is used
3. Add `Property<String> dockerfileName` to `ProjectImageSpec`
   - Convention: `dockerfileName.convention('')` (empty means use `dockerfile` path directly)
   - Only applies when `jarFrom` is used (ignored for `contextTask` and `contextDir`)
   - Validation: mutually exclusive with `dockerfile` (cannot set both)
   - When set, uses `src/main/docker/{dockerfileName}` as the Dockerfile path
4. Add `contextTask` properties to `ProjectImageSpec` (matches `docker` DSL pattern for config cache safety)
   - Add `TaskProvider<Task> contextTask` (`@Internal`, not cached)
     - User-facing DSL property for convenient task assignment
     - Setter auto-populates `contextTaskName` and `contextTaskPath` from the TaskProvider
   - Add `Property<String> contextTaskName` (`@Input`, cached)
     - Convention: empty string
     - Auto-populated when `contextTask` is assigned
   - Add `Property<String> contextTaskPath` (`@Input`, cached)
     - Convention: empty string
     - Auto-populated when `contextTask` is assigned (full path like `:app-image:prepareContext`)
   - Validation: mutually exclusive with `jarFrom` and `contextDir`
   - During configuration cache replay, plugin uses string properties to look up the task
5. Add `Property<String> sourceRef` to `ProjectImageSpec`
   - No convention (optional property)
   - Validation: mutually exclusive with all build properties (`jarFrom`, `contextTask`, `contextDir`)
   - When set, activates Source Reference Mode (no build step)
6. Update `isConfigured()` check in `DockerProjectSpec` to include `repository` and `sourceRef`
7. Add `isSourceRefMode()` method to detect Source Reference Mode
   - Returns true if `sourceRef` is set OR no build properties are set
8. Update `isBuildMode()` to include `contextTask` and exclude when `sourceRef` is set
9. Update translator code that maps `ProjectImageSpec` to `ImageSpec`
10. Update `createContextTask()` in translator to use `jarName` instead of hardcoded `'app.jar'`
11. Update `createContextTask()` in translator to use `dockerfileName` when set
    - If `dockerfileName` is set, resolve to `src/main/docker/{dockerfileName}`
    - If `dockerfile` is set (and `dockerfileName` is not), use `dockerfile` directly
    - Default: `src/main/docker/Dockerfile`
12. Update `DockerProjectTaskGenerator` to skip build task when in Source Reference Mode
13. Add DSL helper methods to `ProjectImageSpec` (matches `docker` DSL API for consistency)
    - Add `buildArg(String key, String value)` method that calls `buildArgs.put(key, value)`
    - Add `label(String key, String value)` method that calls `labels.put(key, value)`
    - These are convenience methods - users can also use `buildArgs.put()` and `labels.put()` directly
14. Add `sourceRef(Closure)` DSL method to `ProjectImageSpec`
    - Create inner `SourceRefSpec` class with properties: `registry`, `namespace`, `name`, `tag`
    - `sourceRef { }` closure configures SourceRefSpec, then computes full reference string
    - Store computed reference in `sourceRef` property
    - Example: `sourceRef { registry = 'docker.io'; namespace = 'library'; name = 'nginx'; tag = 'latest' }`
    - Provides component-based alternative to string-based `sourceRef.set('docker.io/library/nginx:latest')`
15. Add `getEffectiveSourceRef()` internal helper to `ProjectImageSpec`
    - Computes effective source reference from component properties when `sourceRef` is not set
    - Combines `registry`, `namespace`, `name`, and first tag into a reference string
    - Used internally for Source Reference Mode when image specified via components

**Tasks for Lifecycle Enum (Shared)**:
16. Create `com.kineticfire.gradle.docker.Lifecycle` enum
   - Values: `CLASS`, `METHOD`
   - Location: `plugin/src/main/groovy/com/kineticfire/gradle/docker/Lifecycle.groovy`
   - This enum is shared between `dockerProject` and `dockerOrch` DSLs

**Tasks for PullPolicy Enum (Shared)**:
17. Create `com.kineticfire.gradle.docker.PullPolicy` enum
   - Values: `NEVER`, `IF_MISSING`, `ALWAYS`
   - Location: `plugin/src/main/groovy/com/kineticfire/gradle/docker/PullPolicy.groovy`
   - This enum is shared between `dockerProject` and `docker` DSLs
   - Default value: `NEVER` (safest - fail if image not found locally)

**Tasks for pullPolicy in ProjectImageSpec**:
18. Add `Property<PullPolicy> pullPolicy` to `ProjectImageSpec`
    - Convention: `pullPolicy.convention(PullPolicy.NEVER)`
    - Type-safe: no validation needed (compiler enforces valid values)
    - Only applicable in Source Reference Mode (not Build Mode)
19. Add validation: `pullPolicy` only in Source Reference Mode
    - If `pullPolicy` is set AND any build property is set (`jarFrom`, `contextTask`, `contextDir`), throw validation error
    - Error message: "pullPolicy can only be set in Source Reference Mode"
20. Update `DockerProjectTaskGenerator` to respect `pullPolicy` for referenced images
    - When in Source Reference Mode with `pullPolicy = NEVER`: Fail if image not local
    - When in Source Reference Mode with `pullPolicy = IF_MISSING`: Pull only if not local
    - When in Source Reference Mode with `pullPolicy = ALWAYS`: Always pull from registry

**Tasks for docker DSL Migration (pullIfMissing → pullPolicy)**:
21. Deprecate `pullIfMissing(boolean)` method in `ImageSpec`
    - Add `@Deprecated` annotation with explanation
    - Method internally converts to `PullPolicy`: `true` → `IF_MISSING`, `false` → `NEVER`
22. Add `Property<PullPolicy> pullPolicy` to `ImageSpec`
    - Convention: `pullPolicy.convention(PullPolicy.NEVER)`
    - Works alongside deprecated `pullIfMissing()` for backward compatibility
23. Update `DockerBuildTask` to use `pullPolicy` instead of `pullIfMissing`
    - Convert existing `pullIfMissing` boolean logic to enum-based logic
    - Support `ALWAYS` option (new functionality)
24. Update `ImageSpec` DSL methods to support `PullPolicy`
    - Add `pullPolicy(PullPolicy policy)` method
    - Keep `pullIfMissing(boolean)` as deprecated wrapper

**Tasks for ProjectTestSpec**:
25. Add `Property<Lifecycle> lifecycle` to `ProjectTestSpec`
    - Convention: `lifecycle.convention(Lifecycle.CLASS)`
    - Type-safe: no validation needed (compiler enforces valid values)
26. Update translator/generator to configure test task lifecycle based on this property
    - For `Lifecycle.CLASS`: Standard composeUp → tests → composeDown flow
    - For `Lifecycle.METHOD`: Register JUnit extension that manages compose per test method

**Tasks for dockerOrch Migration**:
27. Update `TestIntegrationExtension.usesCompose()` to accept `Lifecycle` enum
    - Change `String lifecycle` parameter to `Lifecycle lifecycle`
    - Remove string validation logic (no longer needed with enum)
28. Update all callers of `usesCompose()` to pass `Lifecycle` enum values

**Tasks for Multiple Publish Targets (onSuccess)**:
29. Create `PublishTargetSpec` class for named publish targets
    - Properties: `registry`, `namespace`, `repository`, `tags`
    - Nested `auth` block with `username`, `password`
    - Located in `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/PublishTargetSpec.groovy`
30. Create `PublishTargetAuthSpec` class for authentication
    - Properties: `username`, `password` (both `Property<String>`)
    - Located in `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/PublishTargetAuthSpec.groovy`
31. Update `ProjectSuccessSpec` to support multiple publish targets
    - Add `NamedDomainObjectContainer<PublishTargetSpec> publish` container
    - Add `publish(Action<NamedDomainObjectContainer<PublishTargetSpec>>)` method for DSL
    - Keep existing flat properties (`publishRegistry`, `publishNamespace`, `publishTags`) for simple cases
    - Validation: cannot use both flat properties AND `publish { }` block simultaneously
32. Update `DockerProjectTaskGenerator` to handle multiple publish targets
    - Generate `dockerProjectPublish{Name}` task for each named target
    - Wire all publish tasks as dependencies of `runDockerProject`
    - Pass auth credentials to Docker login before push

**Deliverables**:
- New `Lifecycle` enum shared by `dockerProject` and `dockerOrch` DSLs
- New `PullPolicy` enum shared by `dockerProject` and `docker` DSLs
  - Values: `NEVER` (default), `IF_MISSING`, `ALWAYS`
  - Location: `plugin/src/main/groovy/com/kineticfire/gradle/docker/PullPolicy.groovy`
- **Multiple Image Support**:
  - Renamed `name` property to `imageName` in `ProjectImageSpec` for consistency with `docker` DSL
  - New `Property<Boolean> primary` to designate which image receives `onSuccess.additionalTags`
  - New `NamedDomainObjectContainer<ProjectImageSpec> images` in `DockerProjectExtension`
  - Validation: at least one image required, exactly one primary image
  - Auto-primary: if only one image defined, it's automatically primary
- Updated `ProjectImageSpec` with:
  - `imageName` property (renamed from `name`)
  - `primary` property for multi-image scenarios
  - `repository`, `jarName`, `dockerfileName`, `sourceRef` properties
  - `pullPolicy` property (`Property<PullPolicy>`) for Source Reference Mode
  - `contextTask` triple-property pattern for config cache safety (`contextTask`, `contextTaskName`, `contextTaskPath`)
  - DSL helper methods: `buildArg(key, value)`, `label(key, value)` for API consistency with `docker` DSL
  - `sourceRef(Closure)` DSL method with inner `SourceRefSpec` class for component-based sourceRef
  - `getEffectiveSourceRef()` internal helper for computing reference from component properties
- **docker DSL Migration (pullIfMissing → pullPolicy)**:
  - Deprecated `pullIfMissing(boolean)` method in `ImageSpec` (converts to `PullPolicy`)
  - New `pullPolicy` property in `ImageSpec` using `PullPolicy` enum
  - Updated `DockerBuildTask` to use `pullPolicy` instead of `pullIfMissing`
  - Added `pullPolicy(PullPolicy)` method for type-safe DSL
  - `ALWAYS` pull policy adds new functionality not available with boolean
- Updated `ProjectTestSpec` with `Property<Lifecycle> lifecycle`
- Updated `TestIntegrationExtension` to use `Lifecycle` enum instead of strings
- New `PublishTargetSpec` and `PublishTargetAuthSpec` for multiple publish targets
- Source Reference Mode support (no build step when `sourceRef` is set)
- Updated `ProjectSuccessSpec` with `publish { }` container for named targets
- Validation logic for mutual exclusivity (no lifecycle validation needed - enum is type-safe)
- Unit tests for new properties, DSL helpers, Lifecycle enum, PullPolicy enum, pullPolicy property, multi-image support, and publish target specs

### Phase 2: Create Task Generator Infrastructure

**Goal**: Create the generator that produces configuration-cache-compatible task graphs.

**Tasks**:
1. Create `DockerProjectTaskGenerator` class with methods:
   - `generate(Project project, DockerProjectExtension extension)`
   - `registerBuildTask(Project project, ProjectImageSpec imageSpec)`
   - `registerComposeUpTask(Project project, ProjectTestSpec testSpec)`
   - `registerComposeDownTask(Project project, ProjectTestSpec testSpec)`
   - `registerTagOnSuccessTask(Project project, DockerProjectExtension extension)`
   - `registerSaveTask(Project project, DockerProjectExtension extension)`
   - `registerPublishTask(Project project, DockerProjectExtension extension)`
   - `wireTestTaskDependencies(Project project, DockerProjectExtension extension)`
2. Handle both Image Name Mode and Repository Mode when generating task names
3. Wire task generation into `GradleDockerPlugin.configureDockerProject()`

**Deliverables**:
- `DockerProjectTaskGenerator` class
- Unit tests for task generation logic

### Phase 3: Create Lightweight Conditional Tasks

**Goal**: Create simple, serializable tasks for conditional operations.

**Tasks**:
1. Create `DockerProjectTagOnSuccessTask`:
   - Flattened inputs: `imageName`, `additionalTags`, `testResultFile`
   - Reads test result file to determine if tests passed
   - Applies additional tags only on success
2. Create `DockerProjectStateFile` utility for reading/writing JSON state:
   - Test results written to `build/docker-project/test-result.json`
   - Provides type-safe access to state data

**Deliverables**:
- `DockerProjectTagOnSuccessTask` class
- `DockerProjectStateFile` utility
- Unit tests for both classes

### Phase 4: Update Plugin Registration

**Goal**: Replace `DockerProjectRunTask` with task graph generation.

**Tasks**:
1. Update `GradleDockerPlugin.configureDockerProject()`:
   - Remove `DockerProjectRunTask` registration
   - Call `DockerProjectTaskGenerator.generate()` in `afterEvaluate`
   - Register `runDockerProject` as lifecycle task depending on generated graph
2. Delete non-compliant code:
   - `DockerProjectRunTask.groovy`
   - Any executor classes specific to `dockerProject` that use dynamic task execution

**Deliverables**:
- Updated `GradleDockerPlugin` configuration
- Removed non-compliant task classes
- Compilation succeeds

### Phase 5: Testing

**Goal**: Comprehensive testing at all levels - unit, functional, and integration.

See the detailed **Test Strategy** section below for complete test specifications.

**Deliverables**:
- All unit tests passing with 100% coverage on new code
- All functional tests passing
- All integration tests passing with `--configuration-cache`
- Updated documentation:
  - `docs/usage/usage-docker.md`: Update `pullIfMissing` section to document `pullPolicy` enum and deprecation
  - `docs/usage/usage-docker-project.md`: Add `pullPolicy` property to Source Reference Mode section

---

## Test Strategy

### Overview

Tests are required at three levels:
1. **Unit Tests** - Test individual classes in isolation (mocked dependencies)
2. **Functional Tests** - Test plugin behavior via Gradle TestKit (no real Docker)
3. **Integration Tests** - Test end-to-end with real Docker operations

### Unit Tests

#### 1. `ProjectImageSpec` Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectImageSpecTest.groovy`

**New tests for imageName property (renamed from name):**

| Test | Description |
|------|-------------|
| `imageName property has empty string convention` | Verify default convention |
| `imageName can be set` | Verify property can be set |
| `isImageNameMode returns true when imageName is set` | Verify mode detection |
| `getEffectiveImageName returns imageName in Image Name Mode` | Verify name resolution |

**New tests for primary property:**

| Test | Description |
|------|-------------|
| `primary property has false convention` | Verify default convention |
| `primary can be set to true` | Verify property can be set |
| `primary can be set to false` | Verify property can be set |

**New tests for repository property:**

| Test | Description |
|------|-------------|
| `repository property has empty string convention` | Verify default convention |
| `repository can be set` | Verify property can be set |
| `isRepositoryMode returns true when repository is set` | Verify mode detection |
| `neither mode is active when nothing is set` | Verify default state |
| `validation fails when both imageName and repository are set` | Verify mutual exclusivity |
| `validation passes when only imageName is set` | Verify valid config |
| `validation passes when only repository is set` | Verify valid config |
| `getEffectiveImageName extracts name from repository` | Verify name extraction from `myorg/my-app` |
| `getEffectiveNamespace returns namespace in Image Name Mode` | Verify namespace resolution |
| `getEffectiveNamespace extracts namespace from repository` | Verify namespace extraction |
| `getEffectiveNamespace handles multi-level repository` | Handle `myorg/team/my-app` |

**New tests for jarName property:**

| Test | Description |
|------|-------------|
| `jarName property has 'app.jar' convention` | Verify default convention |
| `jarName can be set` | Verify property can be set |
| `jarName is used when jarFrom is set` | Verify jarName applies with jarFrom |
| `jarName is ignored when contextTask is set` | Verify jarName only applies to jarFrom mode |

**New tests for dockerfileName property:**

| Test | Description |
|------|-------------|
| `dockerfileName property has empty string convention` | Verify default convention is empty |
| `dockerfileName can be set` | Verify property can be set |
| `dockerfileName is used when jarFrom is set` | Verify dockerfileName applies with jarFrom |
| `dockerfileName is ignored when contextTask is set` | Verify dockerfileName only applies to jarFrom mode |
| `dockerfileName is ignored when contextDir is set` | Verify dockerfileName only applies to jarFrom mode |
| `validation fails when both dockerfileName and dockerfile are set` | Verify mutual exclusivity |
| `dockerfileName resolves to src/main/docker/{name}` | Verify path resolution |

**New tests for contextTask properties (triple-property pattern for config cache):**

| Test | Description |
|------|-------------|
| `contextTask property is @Internal (not cached)` | Verify annotation |
| `contextTask can be set with TaskProvider` | Verify property can be set |
| `contextTaskName property has empty string convention` | Verify default convention |
| `contextTaskPath property has empty string convention` | Verify default convention |
| `setting contextTask auto-populates contextTaskName` | Verify name extraction |
| `setting contextTask auto-populates contextTaskPath` | Verify path extraction |
| `contextTaskName and contextTaskPath are @Input (cached)` | Verify annotations |
| `isBuildMode returns true when contextTask is set` | Verify mode detection |
| `validation fails when both jarFrom and contextTask are set` | Verify mutual exclusivity |
| `validation fails when both contextDir and contextTask are set` | Verify mutual exclusivity |
| `validation passes when only contextTask is set` | Verify valid config |
| `contextTaskPath includes project path` | Verify `:subproject:taskName` format |

**New tests for sourceRef property (Source Reference Mode):**

| Test | Description |
|------|-------------|
| `sourceRef property has no convention` | Verify no default |
| `sourceRef can be set` | Verify property can be set |
| `isSourceRefMode returns true when sourceRef is set` | Verify mode detection |
| `isSourceRefMode returns true when no build properties set` | Verify implicit mode |
| `isBuildMode returns false when sourceRef is set` | Verify mutual exclusivity |
| `validation fails when both sourceRef and jarFrom are set` | Verify mutual exclusivity |
| `validation fails when both sourceRef and contextTask are set` | Verify mutual exclusivity |
| `validation fails when both sourceRef and contextDir are set` | Verify mutual exclusivity |
| `validation passes when only sourceRef is set` | Verify valid config |
| `sourceRef accepts full image reference` | Verify `myregistry.com/myorg/app:v1.0` |
| `sourceRef accepts Docker Hub reference` | Verify `nginx:latest` |
| `sourceRef accepts namespace reference` | Verify `myorg/myapp:v1.0` |

**New tests for pullPolicy property (Source Reference Mode):**

| Test | Description |
|------|-------------|
| `pullPolicy property has NEVER convention` | Verify default is PullPolicy.NEVER |
| `pullPolicy can be set to NEVER` | Verify property can be set |
| `pullPolicy can be set to IF_MISSING` | Verify property can be set |
| `pullPolicy can be set to ALWAYS` | Verify property can be set |
| `pullPolicy is valid when sourceRef is set` | Verify pullPolicy works in Source Reference Mode |
| `pullPolicy is valid when no build properties set` | Verify pullPolicy works in implicit Source Reference Mode |
| `validation fails when pullPolicy set with jarFrom` | Verify pullPolicy only in Source Reference Mode |
| `validation fails when pullPolicy set with contextTask` | Verify pullPolicy only in Source Reference Mode |
| `validation fails when pullPolicy set with contextDir` | Verify pullPolicy only in Source Reference Mode |
| `validation passes with pullPolicy NEVER and sourceRef` | Verify valid config |
| `validation passes with pullPolicy IF_MISSING and sourceRef` | Verify valid config |
| `validation passes with pullPolicy ALWAYS and sourceRef` | Verify valid config |

**New tests for DSL helper methods (API consistency with `docker` DSL):**

| Test | Description |
|------|-------------|
| `buildArg method adds entry to buildArgs map` | Verify `buildArg('KEY', 'value')` works |
| `buildArg method can be called multiple times` | Verify multiple calls accumulate |
| `label method adds entry to labels map` | Verify `label('key', 'value')` works |
| `label method can be called multiple times` | Verify multiple calls accumulate |
| `buildArg method is equivalent to buildArgs.put` | Verify same result |
| `label method is equivalent to labels.put` | Verify same result |

**New tests for sourceRef(Closure) DSL method:**

| Test | Description |
|------|-------------|
| `sourceRef closure configures SourceRefSpec` | Basic closure configuration works |
| `sourceRef closure computes full reference` | `registry/namespace/name:tag` |
| `sourceRef closure sets sourceRef property` | Verify string property is set |
| `sourceRef closure with registry only` | `registry/name:tag` (no namespace) |
| `sourceRef closure with namespace only` | `namespace/name:tag` (no registry) |
| `sourceRef closure with name and tag only` | `name:tag` |
| `sourceRef closure uses 'latest' tag as default` | When tag not specified |
| `SourceRefSpec has registry property` | Verify property exists |
| `SourceRefSpec has namespace property` | Verify property exists |
| `SourceRefSpec has name property` | Verify property exists |
| `SourceRefSpec has tag property` | Verify property exists |

**New tests for getEffectiveSourceRef() internal helper:**

| Test | Description |
|------|-------------|
| `getEffectiveSourceRef returns sourceRef when set` | Direct sourceRef takes precedence |
| `getEffectiveSourceRef computes from components when sourceRef not set` | Fallback behavior |
| `getEffectiveSourceRef includes registry when set` | `registry/namespace/name:tag` |
| `getEffectiveSourceRef includes namespace when set` | `namespace/name:tag` |
| `getEffectiveSourceRef uses first tag from tags list` | Tag extraction |
| `getEffectiveSourceRef returns null when name not set` | Invalid state handling |
| `getEffectiveSourceRef uses 'latest' when tags empty` | Default tag |

**Tests for buildArgs property (MapProperty):**

| Test | Description |
|------|-------------|
| `buildArgs property has empty map convention` | Verify default is empty map |
| `buildArgs accepts zero entries` | Verify empty map works |
| `buildArgs accepts one entry via put` | Verify single entry |
| `buildArgs accepts multiple entries via put` | Verify multiple puts |
| `buildArgs accepts multiple entries via set` | Verify set with map |
| `buildArgs accepts multiple entries via putAll` | Verify putAll merges |
| `buildArgs entries are passed to docker build` | Verify translation to ImageSpec |

**Tests for labels property (MapProperty):**

| Test | Description |
|------|-------------|
| `labels property has empty map convention` | Verify default is empty map |
| `labels accepts zero entries` | Verify empty map works |
| `labels accepts one entry via put` | Verify single entry |
| `labels accepts multiple entries via put` | Verify multiple puts |
| `labels accepts multiple entries via set` | Verify set with map |
| `labels accepts multiple entries via putAll` | Verify putAll merges |
| `labels entries are applied to built image` | Verify translation to ImageSpec |

#### 2. `DockerProjectTaskGenerator` Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/project/DockerProjectTaskGeneratorTest.groovy`

| Test | Description |
|------|-------------|
| `generates build task with correct name for Image Name Mode` | `dockerBuildMyApp` from `name='my-app'` |
| `generates build task with correct name for Repository Mode` | `dockerBuildMyApp` from `repository='myorg/my-app'` |
| `generates compose up task name from test spec` | `composeUpMyAppTest` |
| `generates tag on success task` | `dockerProjectTagOnSuccess` |
| `wires correct task dependencies for full pipeline` | Verify dependency graph |
| `skips publish task when not configured` | Verify conditional task creation |
| `skips save task when not configured` | Verify conditional task creation |

#### 3. `DockerProjectTagOnSuccessTask` Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/DockerProjectTagOnSuccessTaskTest.groovy`

| Test | Description |
|------|-------------|
| `applies tags when test result indicates success` | Verify docker tag commands executed |
| `skips tagging when test result indicates failure` | Verify no docker commands |
| `throws exception when test result file is missing` | Verify error handling |
| `handles empty additional tags list` | Verify no-op when empty |

#### 4. `DockerProjectStateFile` Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/project/DockerProjectStateFileTest.groovy`

| Test | Description |
|------|-------------|
| `writes test result to file` | Verify JSON output |
| `reads test result from file` | Verify JSON parsing |
| `isTestSuccessful returns true for successful result` | Verify success detection |
| `isTestSuccessful returns false for failed result` | Verify failure detection |
| `isTestSuccessful returns false when file doesn't exist` | Verify missing file handling |

#### 5. `ProjectTestSpec` Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectTestSpecTest.groovy`

**Tests for lifecycle property (enum-based):**

| Test | Description |
|------|-------------|
| `lifecycle property has Lifecycle.CLASS convention` | Verify default convention is `Lifecycle.CLASS` |
| `lifecycle can be set to Lifecycle.CLASS` | Verify explicit CLASS setting |
| `lifecycle can be set to Lifecycle.METHOD` | Verify METHOD setting |
| `lifecycle property is type-safe` | Only accepts `Lifecycle` enum values |

#### 5a. `ProjectTestConfigSpec` Tests (Multiple Test Configurations)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectTestConfigSpecTest.groovy`

| Test | Description |
|------|-------------|
| `compose property has no convention` | Verify required property |
| `compose can be set` | Verify property works |
| `waitForHealthy property has empty list convention` | Verify default |
| `waitForRunning property has empty list convention` | Verify default |
| `lifecycle property has Lifecycle.CLASS convention` | Verify default |
| `timeoutSeconds property has 60 convention` | Verify default |
| `pollSeconds property has 2 convention` | Verify default |
| `testClasses property has no convention` | Verify required property |
| `testClasses accepts single pattern` | Verify single entry |
| `testClasses accepts multiple patterns` | Verify multiple entries |
| `testClasses patterns support glob syntax` | Verify `com.example.api.**` works |

#### 5b. `DockerProjectExtension` Images Container Tests (Multiple Image Support)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerProjectExtensionTest.groovy`

| Test | Description |
|------|-------------|
| `images container accepts named configurations` | Verify container creation |
| `images container allows multiple images` | Verify multiple named images |
| `images container requires at least one image` | Verify validation |
| `image block name becomes task name suffix` | Verify `myApp` → `dockerBuildMyApp` |
| `single image is automatically primary` | Verify auto-primary when only one image |
| `validation fails when no image has primary set` | Verify multiple images need explicit primary |
| `validation fails when multiple images have primary set` | Verify only one primary |
| `validation passes when exactly one image has primary` | Verify valid multi-image config |

#### 5c. `DockerProjectExtension` Multiple Tests Container Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerProjectExtensionTest.groovy`

| Test | Description |
|------|-------------|
| `tests container accepts named configurations` | Verify container creation |
| `tests container allows multiple configurations` | Verify multiple names |
| `tests and test cannot be used together` | Verify mutual exclusivity |
| `test configuration name becomes task name prefix` | Verify `apiTests` → `apiTestsIntegrationTest` |

#### 6. `Lifecycle` Enum Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/LifecycleTest.groovy`

| Test | Description |
|------|-------------|
| `Lifecycle has CLASS value` | Verify CLASS constant exists |
| `Lifecycle has METHOD value` | Verify METHOD constant exists |
| `Lifecycle has exactly two values` | Verify only CLASS and METHOD exist |
| `Lifecycle.values() returns all values` | Verify enum completeness |

#### 7. `PullPolicy` Enum Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/PullPolicyTest.groovy`

| Test | Description |
|------|-------------|
| `PullPolicy has NEVER value` | Verify NEVER constant exists |
| `PullPolicy has IF_MISSING value` | Verify IF_MISSING constant exists |
| `PullPolicy has ALWAYS value` | Verify ALWAYS constant exists |
| `PullPolicy has exactly three values` | Verify only NEVER, IF_MISSING, ALWAYS exist |
| `PullPolicy.values() returns all values` | Verify enum completeness |
| `PullPolicy.valueOf() works for NEVER` | Verify string-to-enum conversion |
| `PullPolicy.valueOf() works for IF_MISSING` | Verify string-to-enum conversion |
| `PullPolicy.valueOf() works for ALWAYS` | Verify string-to-enum conversion |

#### 8. `TestIntegrationExtension` Tests (dockerOrch Enum Migration)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtensionTest.groovy`

**New/updated tests for Lifecycle enum:**

| Test | Description |
|------|-------------|
| `usesCompose accepts Lifecycle.CLASS` | Verify CLASS enum value works |
| `usesCompose accepts Lifecycle.METHOD` | Verify METHOD enum value works |
| `usesCompose configures class lifecycle correctly` | Verify composeUp/Down wiring for CLASS |
| `usesCompose configures method lifecycle correctly` | Verify JUnit extension for METHOD |

#### 9. `PublishTargetSpec` Tests (Multiple Publish Targets)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/PublishTargetSpecTest.groovy`

| Test | Description |
|------|-------------|
| `registry property has no convention` | Verify required property |
| `registry can be set` | Verify property works |
| `namespace property has empty string convention` | Verify default |
| `repository property has empty string convention` | Verify default |
| `tags property has empty list convention` | Verify default |
| `tags accepts multiple entries` | Verify list property |
| `auth block can be configured` | Verify nested auth block |
| `namespace and repository are mutually exclusive` | Verify validation |

#### 10. `PublishTargetAuthSpec` Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/PublishTargetAuthSpecTest.groovy`

| Test | Description |
|------|-------------|
| `username property has no convention` | Verify optional property |
| `username can be set` | Verify property works |
| `password property has no convention` | Verify optional property |
| `password can be set` | Verify property works |
| `username accepts Provider for environment variable` | Verify lazy evaluation |
| `password accepts Provider for environment variable` | Verify lazy evaluation |

#### 11. `ProjectSuccessSpec` Multiple Publish Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectSuccessSpecTest.groovy`

| Test | Description |
|------|-------------|
| `publish container accepts named targets` | Verify container creation |
| `publish container allows multiple targets` | Verify multiple names |
| `flat properties and publish block are mutually exclusive` | Verify validation |
| `publish target name becomes task name suffix` | Verify `internal` → `dockerProjectPublishInternal` |
| `each publish target can have different auth` | Verify per-target auth |
| `each publish target can have different tags` | Verify per-target tags |

### Functional Tests

**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/DockerProjectDslFunctionalTest.groovy`

#### Image Name Mode Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts image name mode configuration` | Basic DSL parsing |
| `dockerProject DSL with namespace in Image Name Mode` | With `namespace` property |
| `dockerProject DSL with registry in Image Name Mode` | With `registry` property |

#### Repository Mode Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts repository mode configuration` | Basic repository mode |
| `dockerProject DSL with registry in Repository Mode` | With `registry` property |
| `dockerProject DSL with multi-level repository` | Handle `myorg/team/my-app` |

#### Validation Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL fails when both imageName and repository are set` | Mutual exclusivity |
| `dockerProject DSL fails when neither imageName nor repository is set` | Required field validation |
| `dockerProject DSL fails when both jarFrom and contextTask are set` | Build mode mutual exclusivity |
| `dockerProject DSL fails when both contextDir and contextTask are set` | Build mode mutual exclusivity |
| `dockerProject DSL fails when both sourceRef and jarFrom are set` | Source ref mutual exclusivity |
| `dockerProject DSL fails when both sourceRef and contextTask are set` | Source ref mutual exclusivity |
| `dockerProject DSL fails when both sourceRef and contextDir are set` | Source ref mutual exclusivity |

#### Source Reference Mode Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts sourceRef configuration` | Basic sourceRef works |
| `dockerProject DSL sourceRef skips build task generation` | No dockerBuild task |
| `dockerProject DSL sourceRef accepts full image reference` | `myregistry.com/myorg/app:v1.0` |
| `dockerProject DSL sourceRef accepts Docker Hub reference` | `nginx:latest` |
| `dockerProject DSL sourceRef accepts namespace reference` | `myorg/myapp:v1.0` |
| `dockerProject DSL sourceRef still generates test tasks` | Test step runs |
| `dockerProject DSL sourceRef still generates onSuccess tasks` | Tag/publish tasks run |
| `dockerProject DSL implicit sourceRef when no build props` | Using name without jarFrom/contextTask/contextDir |

#### Pull Policy Tests (Source Reference Mode)

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts pullPolicy with sourceRef` | Basic pullPolicy works |
| `dockerProject DSL pullPolicy defaults to NEVER` | Verify default convention |
| `dockerProject DSL accepts pullPolicy NEVER` | Explicit NEVER setting |
| `dockerProject DSL accepts pullPolicy IF_MISSING` | IF_MISSING setting |
| `dockerProject DSL accepts pullPolicy ALWAYS` | ALWAYS setting |
| `dockerProject DSL pullPolicy requires import statement` | Verify import is needed for enum |
| `dockerProject DSL fails when pullPolicy set with jarFrom` | Validation error |
| `dockerProject DSL fails when pullPolicy set with contextTask` | Validation error |
| `dockerProject DSL fails when pullPolicy set with contextDir` | Validation error |
| `dockerProject DSL pullPolicy works with implicit sourceRef` | When no build props and no explicit sourceRef |

#### docker DSL Pull Policy Tests (Migration)

| Test | Description |
|------|-------------|
| `docker DSL accepts pullPolicy with PullPolicy enum` | Basic pullPolicy works |
| `docker DSL pullPolicy defaults to NEVER` | Verify default convention |
| `docker DSL accepts pullPolicy NEVER` | Explicit NEVER setting |
| `docker DSL accepts pullPolicy IF_MISSING` | IF_MISSING setting |
| `docker DSL accepts pullPolicy ALWAYS` | ALWAYS setting |
| `docker DSL pullIfMissing true converts to IF_MISSING` | Backward compatibility |
| `docker DSL pullIfMissing false converts to NEVER` | Backward compatibility |
| `docker DSL pullIfMissing generates deprecation warning` | Deprecation notice |

#### Build Context Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts jarFrom with custom jarName` | Custom JAR naming |
| `dockerProject DSL accepts contextTask configuration` | User-defined context task |
| `dockerProject DSL contextTask wires dependencies correctly` | Task dependency verification |
| `dockerProject DSL contextTask auto-populates contextTaskName` | Config cache name extraction |
| `dockerProject DSL contextTask auto-populates contextTaskPath` | Config cache path extraction |
| `dockerProject DSL contextTask is configuration cache compatible` | Verify cache reuse with contextTask |

#### DSL Helper Methods Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts buildArg method` | Single `buildArg('KEY', 'value')` |
| `dockerProject DSL accepts multiple buildArg calls` | Multiple calls accumulate |
| `dockerProject DSL accepts label method` | Single `label('key', 'value')` |
| `dockerProject DSL accepts multiple label calls` | Multiple calls accumulate |
| `dockerProject DSL buildArg method works with buildArgs.put` | Can mix approaches |
| `dockerProject DSL label method works with labels.put` | Can mix approaches |

#### sourceRef Closure Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts sourceRef closure` | Basic closure syntax |
| `dockerProject DSL sourceRef closure with all components` | `registry`, `namespace`, `name`, `tag` |
| `dockerProject DSL sourceRef closure computes full reference` | Verify computed string |
| `dockerProject DSL sourceRef closure without registry` | `namespace/name:tag` |
| `dockerProject DSL sourceRef closure without namespace` | `name:tag` |
| `dockerProject DSL sourceRef closure defaults tag to latest` | When tag not specified |
| `dockerProject DSL sourceRef closure and sourceRef.set are mutually exclusive` | Validation error |

#### Dockerfile Customization Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts dockerfileName` | Custom Dockerfile name |
| `dockerProject DSL dockerfileName resolves to src/main/docker path` | Verify path resolution |
| `dockerProject DSL accepts dockerfile custom path` | Full custom path |
| `dockerProject DSL fails when both dockerfileName and dockerfile are set` | Mutual exclusivity |
| `dockerProject DSL dockerfileName is ignored with contextTask` | Only applies to jarFrom |
| `dockerProject DSL dockerfileName is ignored with contextDir` | Only applies to jarFrom |

#### Build Args and Labels Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts zero buildArgs` | Empty buildArgs works |
| `dockerProject DSL accepts one buildArg` | Single entry via put |
| `dockerProject DSL accepts multiple buildArgs via put` | Multiple individual puts |
| `dockerProject DSL accepts multiple buildArgs via set` | Set with map literal |
| `dockerProject DSL accepts zero labels` | Empty labels works |
| `dockerProject DSL accepts one label` | Single entry via put |
| `dockerProject DSL accepts multiple labels via put` | Multiple individual puts |
| `dockerProject DSL accepts multiple labels via set` | Set with map literal |
| `dockerProject DSL buildArgs are passed to generated build task` | Verify task configuration |
| `dockerProject DSL labels are passed to generated build task` | Verify task configuration |

#### Lifecycle Tests (Enum-Based)

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts Lifecycle.CLASS` | Explicit CLASS lifecycle |
| `dockerProject DSL accepts Lifecycle.METHOD` | METHOD lifecycle setting |
| `dockerProject DSL lifecycle defaults to Lifecycle.CLASS when not set` | Verify default convention |
| `dockerProject DSL lifecycle requires import statement` | Verify import is needed for enum |
| `dockerProject DSL configures JUnit extension for Lifecycle.METHOD` | Extension wired for METHOD |
| `dockerProject DSL configures task dependencies for Lifecycle.CLASS` | composeUp/Down wired |

#### dockerOrch Lifecycle Tests (Enum Migration)

| Test | Description |
|------|-------------|
| `dockerOrch usesCompose accepts Lifecycle.CLASS` | Enum value in dockerOrch DSL |
| `dockerOrch usesCompose accepts Lifecycle.METHOD` | Enum value in dockerOrch DSL |
| `dockerOrch lifecycle configures correctly with enum` | Verify enum-based configuration |

#### Multiple Test Configurations Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts tests block with named configurations` | Basic `tests { }` parsing |
| `dockerProject DSL tests block accepts multiple named configurations` | Multiple named configs |
| `dockerProject DSL tests and test are mutually exclusive` | Cannot use both |
| `dockerProject DSL tests configuration generates correct task names` | `apiTests` → `apiTestsIntegrationTest` |
| `dockerProject DSL tests configuration accepts testClasses patterns` | Package pattern filtering |
| `dockerProject DSL tests configurations with different lifecycles` | CLASS and METHOD in same project |
| `dockerProject DSL tests CLASS lifecycle configs can run in parallel` | Verify parallel execution setup |
| `dockerProject DSL tests METHOD lifecycle configs run sequentially` | Verify sequential execution |
| `dockerProject DSL onSuccess only runs if ALL tests pass` | Multi-config success gate |

#### Multiple Publish Targets Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL accepts onSuccess with flat publish properties` | Single registry with flat properties |
| `dockerProject DSL accepts onSuccess with publish block` | Multiple registries with `publish { }` |
| `dockerProject DSL publish block accepts multiple named targets` | `to('internal')`, `to('dockerhub')` |
| `dockerProject DSL publish target accepts auth block` | Nested auth configuration |
| `dockerProject DSL publish target auth accepts Provider` | Environment variable support |
| `dockerProject DSL flat properties and publish block are mutually exclusive` | Validation error |
| `dockerProject DSL publish target generates correct task name` | `internal` → `dockerProjectPublishInternal` |
| `dockerProject DSL each publish target can have different tags` | Per-target tag lists |
| `dockerProject DSL publish targets wire to runDockerProject` | All publish tasks as dependencies |

#### Task Graph Tests

| Test | Description |
|------|-------------|
| `task graph is generated correctly for full pipeline` | All tasks present |
| `task graph includes save task when configured` | Conditional save task |
| `task graph includes publish task when configured` | Conditional publish task |
| `task graph generates separate tasks for each test config` | Multiple test configs → multiple task chains |
| `task graph wires CLASS lifecycle tests for parallel execution` | Parallel task setup |
| `task graph wires METHOD lifecycle tests for sequential execution` | Sequential task setup |

#### Configuration Cache Tests

| Test | Description |
|------|-------------|
| `dockerProject DSL is configuration cache compatible` | Verify cache reuse |
| `dockerProject DSL with repository mode is configuration cache compatible` | Repository mode cache |

### Integration Tests

**Location**: `plugin-integration-test/dockerProject/`

#### Existing Scenarios (Update for Config Cache)

| Scenario | Purpose | Updates Needed |
|----------|---------|----------------|
| `scenario-1-build-mode` | Basic build with Image Name Mode | Add `--configuration-cache` verification |
| `scenario-2-sourceref-mode` | Using existing image (no build) | Add `--configuration-cache` verification |
| `scenario-3-save-publish` | Full pipeline with save & publish | Add `--configuration-cache` verification |

#### New Integration Test Scenarios

##### Scenario 4: Repository Mode

**Location**: `plugin-integration-test/dockerProject/scenario-4-repository-mode/`

**Purpose**: Verify Repository Mode works end-to-end with real Docker

**Configuration**:
```groovy
dockerProject {
    images {
        scenario4App {
            repository.set('scenario4org/scenario4-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            buildArgs.put('BUILD_VERSION', '1.0.0')
            labels.put('org.opencontainers.image.title', 'Scenario 4 - Repository Mode')
            // Single image is automatically primary
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        timeoutSeconds.set(60)
        testTaskName.set('integrationTest')
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

**Verifications**:
1. Image builds successfully with repository-style name
2. Image is tagged correctly: `scenario4org/scenario4-app:latest`, `scenario4org/scenario4-app:1.0.0`
3. Test runs against the image
4. On success, `scenario4org/scenario4-app:tested` tag is applied
5. Cleanup removes all images

##### Scenario 5: Repository Mode with Private Registry

**Location**: `plugin-integration-test/dockerProject/scenario-5-repository-registry/`

**Purpose**: Verify Repository Mode with private registry publishing

**Configuration**:
```groovy
dockerProject {
    images {
        scenario5App {
            repository.set('scenario5org/scenario5-app')
            registry.set('localhost:5035')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            // Single image is automatically primary
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        testTaskName.set('integrationTest')
    }
    onSuccess {
        additionalTags.set(['tested', 'stable'])
        publishRegistry.set('localhost:5035')
        publishTags.set(['latest', '1.0.0', 'tested'])
    }
}
```

**Verifications**:
1. Spin up local registry on port 5035
2. Build image with repository name
3. Run tests
4. On success, publish to registry
5. Verify images exist in registry via `docker pull localhost:5035/scenario5org/scenario5-app:tested`
6. Cleanup registry and images

##### Scenario 6: Image Name Mode with All Options (Multiple BuildArgs and Labels)

**Location**: `plugin-integration-test/dockerProject/scenario-6-imagename-full/`

**Purpose**: Verify Image Name Mode with registry, namespace, and all options including **multiple buildArgs and labels**

**Configuration**:
```groovy
dockerProject {
    images {
        scenario6App {
            imageName.set('scenario6-app')
            registry.set('localhost:5036')
            namespace.set('scenario6ns')
            tags.set(['latest', '1.0.0', 'dev'])
            jarFrom.set(':app:jar')

            // Multiple build arguments (3 entries)
            buildArgs.put('BUILD_VERSION', '1.0.0')
            buildArgs.put('BUILD_DATE', '2025-01-15')
            buildArgs.put('JAVA_VERSION', '21')

            // Multiple labels (4 entries - OCI standard labels)
            labels.put('org.opencontainers.image.title', 'Scenario 6 App')
            labels.put('org.opencontainers.image.version', '1.0.0')
            labels.put('org.opencontainers.image.vendor', 'Test Vendor')
            labels.put('org.opencontainers.image.authors', 'test@example.com')

            // Single image is automatically primary
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        testTaskName.set('integrationTest')
    }
    onSuccess {
        additionalTags.set(['tested', 'verified'])
        saveFile.set('build/images/scenario6-app.tar.gz')
        publishRegistry.set('localhost:5036')
        publishNamespace.set('scenario6ns')
        publishTags.set(['latest', '1.0.0', 'tested'])
    }
}
```

**Verifications**:
1. Build image with full naming: `localhost:5036/scenario6ns/scenario6-app`
2. All tags applied
3. **All 4 labels verified via `docker inspect`**:
   - `org.opencontainers.image.title` = `Scenario 6 App`
   - `org.opencontainers.image.version` = `1.0.0`
   - `org.opencontainers.image.vendor` = `Test Vendor`
   - `org.opencontainers.image.authors` = `test@example.com`
4. Tests run
5. Additional tags applied on success
6. Image saved to tar.gz (verify file exists and size)
7. Published to registry
8. Pull from registry to verify

**Label Verification Script**:
```bash
# Verify all labels were applied
LABELS=$(docker inspect scenario6-app:latest --format '{{json .Config.Labels}}')
echo "$LABELS" | jq -e '.["org.opencontainers.image.title"] == "Scenario 6 App"' || exit 1
echo "$LABELS" | jq -e '.["org.opencontainers.image.version"] == "1.0.0"' || exit 1
echo "$LABELS" | jq -e '.["org.opencontainers.image.vendor"] == "Test Vendor"' || exit 1
echo "$LABELS" | jq -e '.["org.opencontainers.image.authors"] == "test@example.com"' || exit 1
echo "All 4 labels verified successfully"
```

##### Scenario 7: Configuration Cache Verification

**Location**: `plugin-integration-test/dockerProject/scenario-7-config-cache/`

**Purpose**: Explicitly verify configuration cache compatibility

**Configuration**:
```groovy
dockerProject {
    images {
        scenario7App {
            imageName.set('scenario7-app')
            tags.set(['latest'])
            jarFrom.set(':app:jar')
            // Single image is automatically primary
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        testTaskName.set('integrationTest')
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

**Verification Script**:
```bash
# First run - should store configuration
./gradlew runDockerProject --configuration-cache 2>&1 | tee run1.log
grep -q "BUILD SUCCESSFUL" run1.log || exit 1

# Clean Docker resources but keep configuration cache
docker rmi scenario7-app:latest scenario7-app:tested || true

# Second run - should reuse configuration cache
./gradlew runDockerProject --configuration-cache 2>&1 | tee run2.log
grep -q "Reusing configuration cache" run2.log || exit 1

# Verify no configuration cache problems
! grep -q "problems were found" run2.log || exit 1

echo "Configuration cache verification PASSED"
```

##### Scenario 8: Custom Context Task (Non-Java/Complex Projects)

**Location**: `plugin-integration-test/dockerProject/scenario-8-context-task/`

**Purpose**: Verify `contextTask` property for complex context preparation (Java with config files, simulating non-Java patterns)

**Configuration**:
```groovy
dockerProject {
    images {
        scenario8App {
            imageName.set('scenario8-app')
            tags.set(['latest', '1.0.0'])

            // Use contextTask for full control over context preparation
            contextTask.set(tasks.register('prepareScenario8Context', Copy) {
                into layout.buildDirectory.dir('docker-context/scenario8App')

                // Copy Dockerfile
                from('src/main/docker')

                // Copy JAR from app subproject with custom name
                from(project(':app').tasks.named('jar')) {
                    rename { 'my-custom-app.jar' }
                }

                // Copy additional configuration files
                from('src/main/resources/config') {
                    into 'config'
                }

                // Copy startup script
                from('src/main/scripts/entrypoint.sh')
            })

            // Single image is automatically primary
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        testTaskName.set('integrationTest')
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

**Verifications**:
1. Context preparation task is registered correctly
2. Context includes all specified files (JAR, config, scripts)
3. JAR has custom name (`my-custom-app.jar`) in context
4. Task dependencies are wired (`:app:jar` runs before context task)
5. Docker image builds successfully from custom context
6. Tests run against the image
7. Additional tags applied on success
8. Configuration cache compatible

**Test Files to Create**:
- `src/main/docker/Dockerfile` - Dockerfile that uses `my-custom-app.jar`
- `src/main/resources/config/application.yml` - Sample config file
- `src/main/scripts/entrypoint.sh` - Sample startup script

##### Scenario 9: waitForRunning Verification (Minimal Container)

**Location**: `plugin-integration-test/dockerProject/scenario-9-wait-for-running/`

**Purpose**: Verify `waitForRunning` wait strategy works correctly with a minimal container that has no healthcheck.

**Why This Test Exists**:
This scenario specifically tests the `waitForRunning` behavior in isolation. Unlike `waitForHealthy` which requires
a HEALTHCHECK in the Dockerfile, `waitForRunning` only waits for the container to reach the "running" state.

**Design Decision - File-Based Verification**:
Testing `waitForRunning` presents a challenge: the container is "running" but we need to verify it's actually
accessible. We deliberately avoid using a service (HTTP, TCP) because:
1. Service startup creates race conditions - container may be "running" before service accepts connections
2. We want to test the plugin's `waitForRunning` behavior, not service startup timing

Instead, we use `docker exec` to read a file baked into the image. This is deterministic because:
- The file exists the moment the image is built
- If `waitForRunning` completes and container is running, `docker exec` works immediately
- No race condition - file access doesn't depend on any service initialization

**Dockerfile**:
```dockerfile
# Minimal image for waitForRunning verification
# No HEALTHCHECK - this specifically tests waitForRunning (not waitForHealthy)
FROM alpine:3.19

# Create verification file at build time
# This file exists immediately when container starts - no initialization needed
RUN echo "waitForRunning-verified" > /verification.txt

# Keep container running indefinitely
# Using 'sleep infinity' - simplest approach, no ports, no services
CMD ["sleep", "infinity"]
```

**Docker Compose** (`src/integrationTest/resources/compose/app.yml`):
```yaml
services:
  app:
    image: scenario9-running-test:latest
    # No healthcheck - we're testing waitForRunning
    # No ports - we use docker exec for verification
```

**Configuration**:
```groovy
dockerProject {
    images {
        scenario9App {
            imageName.set('scenario9-running-test')
            tags.set(['latest'])
            contextDir.set('src/main/docker')  // Contains minimal Dockerfile
            // Single image is automatically primary
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        // KEY: Using waitForRunning, NOT waitForHealthy
        waitForRunning.set(['app'])
        timeoutSeconds.set(30)  // Should be fast - container starts immediately
        testTaskName.set('integrationTest')
    }
    onSuccess {
        additionalTags.set(['verified'])
    }
}
```

**Integration Test** (`src/integrationTest/groovy/...Spec.groovy`):
```groovy
class WaitForRunningVerificationSpec extends Specification {

    def "container is accessible after waitForRunning completes"() {
        given: "the compose project name from system property"
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME')

        when: "we exec into the container to read the verification file"
        def containerName = "${projectName}-app-1"
        def process = ["docker", "exec", containerName, "cat", "/verification.txt"].execute()
        def output = process.text.trim()
        def exitCode = process.waitFor()

        then: "the file contents confirm the container is running and accessible"
        exitCode == 0
        output == "waitForRunning-verified"
    }
}
```

**Verifications**:
1. Image builds successfully (minimal Alpine + file + sleep)
2. `waitForRunning` completes when container reaches "running" state
3. `docker exec` successfully reads verification file (proves container is accessible)
4. File contents match expected value
5. Additional tags applied on success
6. Cleanup removes container and images
7. Configuration cache compatible

**What This Test Proves**:
- `waitForRunning` correctly detects when container is running
- Container is immediately accessible after `waitForRunning` completes
- No race conditions with file-based verification approach
- Plugin handles containers without HEALTHCHECK correctly

##### Scenario 10: Method Lifecycle (Per-Test Container Isolation)

**Location**: `plugin-integration-test/dockerProject/scenario-10-method-lifecycle/`

**Purpose**: Verify `lifecycle.set(Lifecycle.METHOD)` properly starts/stops the compose stack for each test method.

**Why This Test Exists**:
Method lifecycle is essential for tests that mutate state (database writes, cache modifications). Each test
method gets a fresh container, ensuring complete isolation between tests.

**Configuration**:
```groovy
import com.kineticfire.gradle.docker.Lifecycle

dockerProject {
    images {
        scenario10App {
            imageName.set('scenario10-method-lifecycle')
            tags.set(['latest'])
            jarFrom.set(':app:jar')
            // Single image is automatically primary
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        timeoutSeconds.set(60)
        testTaskName.set('integrationTest')
        lifecycle.set(Lifecycle.METHOD)  // KEY: Each test gets fresh container
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

**Integration Test** (`src/integrationTest/groovy/...Spec.groovy`):
```groovy
class MethodLifecycleVerificationSpec extends Specification {

    // Counter endpoint: GET returns count, POST increments count
    // Each test should see count=0 because container is fresh

    def "first test increments counter"() {
        when: "we increment the counter"
        def postResponse = post("http://localhost:8080/counter")

        then: "count becomes 1"
        postResponse.statusCode == 200

        when: "we read the counter"
        def getResponse = get("http://localhost:8080/counter")

        then: "count is 1"
        getResponse.body == '{"count":1}'
    }

    def "second test also sees counter at zero - proving fresh container"() {
        when: "we read the counter without incrementing"
        def getResponse = get("http://localhost:8080/counter")

        then: "count is 0 - container was restarted, state was reset"
        getResponse.body == '{"count":0}'
    }

    def "third test also starts fresh"() {
        when: "we increment twice"
        post("http://localhost:8080/counter")
        post("http://localhost:8080/counter")
        def getResponse = get("http://localhost:8080/counter")

        then: "count is 2 (not 3 from previous tests)"
        getResponse.body == '{"count":2}'
    }
}
```

**What This Test Proves**:
- `lifecycle.set(Lifecycle.METHOD)` restarts the compose stack for each test method
- Each test sees fresh container state (counter at 0)
- State mutations in one test don't affect subsequent tests
- JUnit extension is properly wired to manage compose lifecycle

**Verifications**:
1. First test increments counter to 1
2. Second test sees counter at 0 (fresh container)
3. Third test sees counter at 2 after two increments (not cumulative from previous)
4. Compose stack starts/stops for each test (verified via logs or timing)
5. Additional tags applied on success
6. Configuration cache compatible

**Note**: This test takes longer than class lifecycle tests due to multiple compose start/stop cycles.
This is expected behavior and demonstrates the trade-off between isolation and speed.

##### Scenario 11: Multiple Test Configurations (Performance Optimization)

**Location**: `plugin-integration-test/dockerProject/scenario-11-multi-test-configs/`

**Purpose**: Verify multiple test configurations with different lifecycles work correctly, demonstrating the performance optimization pattern.

**Why This Test Exists**:
This scenario demonstrates the key use case for multiple test configurations: separating CLASS lifecycle (fast, shared container) tests from METHOD lifecycle (slow, isolated) tests for performance gains.

**Configuration**:
```groovy
import com.kineticfire.gradle.docker.Lifecycle

dockerProject {
    images {
        scenario11App {
            imageName.set('scenario11-multi-config')
            tags.set(['latest'])
            jarFrom.set(':app:jar')
            // Single image is automatically primary
        }
    }

    tests {
        // Fast tests - stateless API tests share a container
        apiTests {
            compose.set('src/integrationTest/resources/compose/api.yml')
            waitForHealthy.set(['app'])
            timeoutSeconds.set(60)
            lifecycle.set(Lifecycle.CLASS)  // Fast - shared container
            testClasses.set(['com.example.api.**'])
        }

        // Slow tests - stateful tests get fresh containers
        statefulTests {
            compose.set('src/integrationTest/resources/compose/stateful.yml')
            waitForHealthy.set(['app', 'db'])
            timeoutSeconds.set(90)
            lifecycle.set(Lifecycle.METHOD)  // Isolated - fresh per test
            testClasses.set(['com.example.stateful.**'])
        }
    }

    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

**Directory Structure**:
```
src/
  integrationTest/
    groovy/
      com/example/
        api/
          ApiHealthIT.groovy       ← Runs with apiTests (CLASS lifecycle)
          ApiEndpointsIT.groovy    ← Runs with apiTests (CLASS lifecycle)
        stateful/
          StatefulDatabaseIT.groovy ← Runs with statefulTests (METHOD lifecycle)
    resources/
      compose/
        api.yml       ← Simple app-only compose
        stateful.yml  ← Full stack with db
```

**Integration Tests**:

`com.example.api.ApiHealthIT`:
```groovy
class ApiHealthIT extends Specification {
    // These tests share a container (CLASS lifecycle)
    // No @ComposeUp annotation needed

    def "health endpoint returns 200"() {
        expect:
        given().get("http://localhost:8080/health").then().statusCode(200)
    }

    def "health endpoint returns healthy status"() {
        expect:
        given().get("http://localhost:8080/health").then().body("status", equalTo("healthy"))
    }
}
```

`com.example.stateful.StatefulDatabaseIT`:
```groovy
@ComposeUp  // Required for METHOD lifecycle
class StatefulDatabaseIT extends Specification {
    // These tests get fresh containers (METHOD lifecycle)

    def "first test creates data"() {
        when:
        def response = post("http://localhost:8080/data", [name: "test1"])

        then:
        response.statusCode == 201

        when: "we read the data"
        def getResponse = get("http://localhost:8080/data/test1")

        then:
        getResponse.body.name == "test1"
    }

    def "second test sees empty database - proving isolation"() {
        when: "we try to read data from previous test"
        def response = get("http://localhost:8080/data/test1")

        then: "data doesn't exist - fresh container"
        response.statusCode == 404
    }
}
```

**Verifications**:
1. Both test configurations are created: `apiTestsIntegrationTest`, `statefulTestsIntegrationTest`
2. API tests run with CLASS lifecycle (shared container)
3. Stateful tests run with METHOD lifecycle (fresh container per test)
4. API tests can run in parallel with other CLASS lifecycle configs (if any)
5. Stateful tests run sequentially
6. `onSuccess` only executes if BOTH test configs pass
7. All tests pass and additional tags are applied
8. Configuration cache compatible

**What This Test Proves**:
- Multiple test configurations work correctly
- Different lifecycles can coexist in one project
- Test class filtering via `testClasses` works correctly
- Performance optimization pattern is viable
- `onSuccess` acts as a gate requiring all tests to pass

##### Scenario 12: Multiple Publish Targets

**Location**: `plugin-integration-test/dockerProject/scenario-12-multi-publish/`

**Purpose**: Verify publishing to multiple registries with different configurations, authentication, and tags.

**Why This Test Exists**:
This scenario demonstrates the multi-registry publish pattern, which is common in enterprise environments where images need to be pushed to both internal and public registries with different configurations.

**Configuration**:
```groovy
import com.kineticfire.gradle.docker.Lifecycle

dockerProject {
    images {
        scenario12App {
            imageName.set('scenario12-multi-publish')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            // Single image is automatically primary
        }
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        lifecycle.set(Lifecycle.CLASS)
    }

    onSuccess {
        additionalTags.set(['tested'])

        publish {
            to('primary') {
                registry.set('localhost:5121')
                namespace.set('primary-org')
                tags.set(['latest', '1.0.0', 'tested'])
            }

            to('backup') {
                registry.set('localhost:5122')
                namespace.set('backup-org')
                tags.set(['backup-latest', 'backup-1.0.0'])
            }
        }
    }
}
```

**Test Infrastructure**:
- Two local Docker registries on ports 5121 and 5122
- No authentication (for simplicity in local testing)
- Different namespaces per registry
- Different tags per registry

**Verifications**:
1. Image builds successfully
2. Tests pass
3. `dockerProjectPublishPrimary` task is generated and runs
4. `dockerProjectPublishBackup` task is generated and runs
5. Primary registry contains:
   - `localhost:5121/primary-org/scenario12-multi-publish:latest`
   - `localhost:5121/primary-org/scenario12-multi-publish:1.0.0`
   - `localhost:5121/primary-org/scenario12-multi-publish:tested`
6. Backup registry contains:
   - `localhost:5122/backup-org/scenario12-multi-publish:backup-latest`
   - `localhost:5122/backup-org/scenario12-multi-publish:backup-1.0.0`
7. Both registries can be pulled from (verify with `docker pull`)
8. Cleanup removes registries and images
9. Configuration cache compatible

**Verification Script**:
```bash
# Verify primary registry
docker pull localhost:5121/primary-org/scenario12-multi-publish:latest || exit 1
docker pull localhost:5121/primary-org/scenario12-multi-publish:1.0.0 || exit 1
docker pull localhost:5121/primary-org/scenario12-multi-publish:tested || exit 1
echo "Primary registry verified"

# Verify backup registry
docker pull localhost:5122/backup-org/scenario12-multi-publish:backup-latest || exit 1
docker pull localhost:5122/backup-org/scenario12-multi-publish:backup-1.0.0 || exit 1
echo "Backup registry verified"

echo "All multi-publish verifications passed!"
```

**What This Test Proves**:
- Multiple publish targets work correctly
- Each target can have different registry, namespace, and tags
- All publish tasks are generated and executed
- Both registries receive the correct images with correct tags
- Multi-registry pattern is viable for enterprise use cases

##### Scenario 13: Pull Policy with Source Reference Mode

**Location**: `plugin-integration-test/dockerProject/scenario-13-pull-policy/`

**Purpose**: Verify `pullPolicy` property works correctly in Source Reference Mode with all three policy values.

**Why This Test Exists**:
This scenario demonstrates the `pullPolicy` feature which controls how the plugin handles referenced images. It tests:
- `NEVER`: Fail if image not local (default, safest)
- `IF_MISSING`: Pull only if not found locally
- `ALWAYS`: Always pull from registry (useful for `:latest` tags)

**Configuration**:
```groovy
import com.kineticfire.gradle.docker.PullPolicy

dockerProject {
    images {
        // Reference nginx from Docker Hub with pullPolicy
        nginx {
            sourceRef.set('nginx:1.25-alpine')
            pullPolicy.set(PullPolicy.IF_MISSING)  // Pull if not local
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['nginx'])
        timeoutSeconds.set(60)
        testTaskName.set('integrationTest')
    }
    onSuccess {
        additionalTags.set(['verified'])
    }
}
```

**Docker Compose** (`src/integrationTest/resources/compose/app.yml`):
```yaml
services:
  nginx:
    image: nginx:1.25-alpine
    ports:
      - "9213:80"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost"]
      interval: 5s
      timeout: 3s
      retries: 5
```

**Integration Test** (`src/integrationTest/groovy/...Spec.groovy`):
```groovy
class PullPolicyVerificationSpec extends Specification {

    def "nginx responds to requests after pullPolicy IF_MISSING"() {
        when: "we request the nginx default page"
        def response = given()
            .get("http://localhost:9213/")

        then: "we get the nginx welcome page"
        response.statusCode == 200
        response.body.asString().contains("Welcome to nginx")
    }
}
```

**Verifications**:
1. Image is pulled if not local (IF_MISSING behavior)
2. Compose stack starts successfully with referenced image
3. Tests pass against the running nginx container
4. `onSuccess.additionalTags` are applied (verify `nginx:1.25-alpine` gets `:verified` tag)
5. Cleanup removes tags applied by onSuccess
6. Configuration cache compatible

**Additional Test Cases** (can be separate verification tasks):

**Test NEVER policy (expect failure if image not local)**:
```groovy
// First remove the image to test NEVER policy
// docker rmi nginx:1.25-alpine

dockerProject {
    images {
        nginx {
            sourceRef.set('nginx:1.25-alpine')
            pullPolicy.set(PullPolicy.NEVER)  // Should fail if not local
        }
    }
    // ...
}
// Expected: Build fails with "image not found locally"
```

**Test ALWAYS policy (always pulls)**:
```groovy
dockerProject {
    images {
        nginx {
            sourceRef.set('nginx:latest')
            pullPolicy.set(PullPolicy.ALWAYS)  // Always pull latest
        }
    }
    // ...
}
// Expected: Always pulls even if image exists locally
```

**What This Test Proves**:
- `pullPolicy` property works in Source Reference Mode
- `IF_MISSING` pulls only when needed
- `NEVER` fails correctly when image is not local
- `ALWAYS` always pulls from registry
- Referenced images can be used in compose stacks
- `onSuccess` tags work with source reference images

### Test Coverage Summary

| Test Type | Scenario | Image Name Mode | Repository Mode | sourceRef | contextTask | dockerfileName | buildArgs/labels | waitForRunning | lifecycle | Multi-Config | Multi-Publish | pullPolicy | Config Cache |
|-----------|----------|-----------------|-----------------|-----------|-------------|----------------|------------------|----------------|-----------|--------------|---------------|------------|--------------|
| Unit | Lifecycle enum | N/A | N/A | N/A | N/A | N/A | N/A | N/A | ✅ (NEW) | N/A | N/A | N/A | N/A |
| Unit | PullPolicy enum | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | ✅ (NEW) | N/A |
| Unit | ProjectTestConfigSpec | N/A | N/A | N/A | N/A | N/A | N/A | ✅ | ✅ | ✅ (NEW) | N/A | N/A | N/A |
| Unit | ProjectImageSpec | ✅ | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | N/A | N/A | N/A | N/A | ✅ (NEW) | N/A |
| Unit | ProjectTestSpec | N/A | N/A | N/A | N/A | N/A | N/A | N/A | ✅ (NEW) | N/A | N/A | N/A | N/A |
| Unit | PublishTargetSpec | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | ✅ (NEW) | N/A | N/A |
| Unit | PublishTargetAuthSpec | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | ✅ (NEW) | N/A | N/A |
| Unit | ProjectSuccessSpec | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | ✅ (NEW) | N/A | N/A |
| Unit | TestIntegrationExtension | N/A | N/A | N/A | N/A | N/A | N/A | N/A | ✅ (UPDATE) | N/A | N/A | N/A | N/A |
| Unit | DockerProjectTaskGenerator | ✅ | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ | N/A | ✅ | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | N/A |
| Unit | DockerProjectTagOnSuccessTask | ✅ (NEW) | ✅ (NEW) | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| Unit | DockerProjectStateFile | ✅ (NEW) | ✅ (NEW) | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| Functional | DSL parsing | ✅ | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) |
| Functional | Validation | ✅ | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | N/A | N/A | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | N/A |
| Functional | Task graph | ✅ | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ | N/A | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) | ✅ (NEW) |
| Functional | dockerOrch lifecycle | N/A | N/A | N/A | N/A | N/A | N/A | N/A | ✅ (UPDATE) | N/A | N/A | N/A | N/A |
| Integration | scenario-1 | ✅ (existing) | - | - | - | - | - | - | CLASS | - | - | - | ✅ (UPDATE) |
| Integration | scenario-2 | - | - | ✅ (existing) | - | - | - | - | CLASS | - | - | - | ✅ (UPDATE) |
| Integration | scenario-3 | ✅ (existing) | - | - | - | - | - | - | CLASS | - | - | - | ✅ (UPDATE) |
| Integration | scenario-4 | - | ✅ (NEW) | - | - | - | - | - | CLASS | - | - | - | ✅ |
| Integration | scenario-5 | - | ✅ (NEW) | - | - | - | - | - | CLASS | - | - | - | ✅ |
| Integration | scenario-6 | ✅ (NEW) | - | - | - | - | ✅ (multiple) | - | CLASS | - | - | - | ✅ |
| Integration | scenario-7 | ✅ | - | - | - | - | - | - | CLASS | - | - | - | ✅ (NEW - explicit) |
| Integration | scenario-8 | ✅ | - | - | ✅ (NEW) | - | - | - | CLASS | - | - | - | ✅ |
| Integration | scenario-9 | ✅ | - | - | - | - | - | ✅ (NEW) | CLASS | - | - | - | ✅ |
| Integration | scenario-10 | ✅ | - | - | - | - | - | - | ✅ METHOD (NEW) | - | - | - | ✅ |
| Integration | scenario-11 | ✅ | - | - | - | - | - | - | ✅ CLASS+METHOD | ✅ (NEW) | - | - | ✅ |
| Integration | scenario-12 | ✅ | - | - | - | - | - | - | CLASS | - | ✅ (NEW) | - | ✅ |
| Integration | scenario-13 | - | - | ✅ | - | - | - | - | CLASS | - | - | ✅ (NEW) | ✅ |

---

## dockerOrch Lifecycle Enum Migration

The `Lifecycle` enum is shared between `dockerProject` and `dockerOrch` DSLs. This section details the migration of `dockerOrch`'s `TestIntegrationExtension` from string-based lifecycle to enum-based lifecycle.

### Current Implementation (Strings)

```groovy
// TestIntegrationExtension.groovy (current)
void usesCompose(Test test, String stackName, String lifecycle) {
    // ...
    switch (lifecycle.toLowerCase()) {
        case 'class':
            configureClassLifecycle(test, stackName, stackSpec)
            break
        case 'method':
            configureMethodLifecycle(test, stackName, stackSpec)
            break
        default:
            throw new IllegalArgumentException(
                "Invalid lifecycle '${lifecycle}'. Must be 'class' or 'method'."
            )
    }
}
```

### New Implementation (Enum)

```groovy
import com.kineticfire.gradle.docker.Lifecycle

// TestIntegrationExtension.groovy (new)
void usesCompose(Test test, String stackName, Lifecycle lifecycle) {
    // ...
    switch (lifecycle) {
        case Lifecycle.CLASS:
            configureClassLifecycle(test, stackName, stackSpec)
            break
        case Lifecycle.METHOD:
            configureMethodLifecycle(test, stackName, stackSpec)
            break
        // No default needed - enum is exhaustive
    }
}
```

### User DSL Changes

**Before (strings):**
```groovy
testIntegration {
    usesCompose stack: 'myTest', lifecycle: 'class'
}
```

**After (enum):**
```groovy
import com.kineticfire.gradle.docker.Lifecycle

testIntegration {
    usesCompose stack: 'myTest', lifecycle: Lifecycle.CLASS
}
```

### Files to Update

| File | Change |
|------|--------|
| `Lifecycle.groovy` (NEW) | Create shared enum in `com.kineticfire.gradle.docker` |
| `TestIntegrationExtension.groovy` | Change `String lifecycle` to `Lifecycle lifecycle`, remove validation |
| `TestIntegrationExtensionTest.groovy` | Update tests to use `Lifecycle` enum |
| Functional tests using dockerOrch lifecycle | Update to use `Lifecycle` enum |
| Integration tests using dockerOrch lifecycle | Update to use `Lifecycle` enum |

### Benefits

1. **Type Safety**: Compiler catches invalid values; no runtime validation needed
2. **IDE Support**: Autocomplete for `Lifecycle.CLASS` and `Lifecycle.METHOD`
3. **Consistency**: Same enum used across `dockerProject` and `dockerOrch` DSLs
4. **Reduced Code**: No string validation, normalization, or case-insensitive matching

---

## Future: `dockerWorkflows` DSL

The `dockerWorkflows` DSL would follow the **same pattern**:
- Keep user-facing DSL unchanged
- Replace `PipelineRunTask` with `PipelineTaskGenerator`
- Generate task dependency graphs at configuration time
- Delete runtime orchestration code

**Recommendation**: Complete `dockerProject` configuration cache compliance first (simpler, proves the pattern), then apply the same approach to `dockerWorkflows`.

---

## Success Criteria

1. **Configuration Cache Compatibility**: Running `./gradlew runDockerProject --configuration-cache` twice shows cache reuse on second run with **zero warnings**
2. **No Serialization Issues**: No Task objects, closures, or non-serializable objects in task inputs
3. **Full Gradle 9/10 Compliance**: All `dockerProject` code follows the golden rules in `gradle-9-and-10-compatibility.md`
4. **User DSL Unchanged**: The `dockerProject { ... }` syntax remains exactly the same for users
5. **Both Naming Modes Work**: Image Name Mode (`name`) and Repository Mode (`repository`) both function correctly
6. **Test Coverage**: All new code has 100% unit test coverage
7. **Functional Tests**: All functional tests pass
8. **Integration Tests**: All `dockerProject` integration tests pass with `--configuration-cache`
9. **Documentation**: User documentation reflects the implementation

---

## Timeline Estimate

| Phase | Estimated Effort | Dependencies |
|-------|-----------------|--------------|
| Phase 1: Add Repository Property | 0.5-1 day | None |
| Phase 2: Task Generator Infrastructure | 2-3 days | Phase 1 |
| Phase 3: Lightweight Conditional Tasks | 1-2 days | Phase 2 |
| Phase 4: Update Plugin Registration | 1-2 days | Phase 3 |
| Phase 5: Testing | 3-4 days | Phase 4 |
| **Total** | **7.5-12 days** | |

---

## Documentation Updates Required

After implementation, the following documentation must be updated:

### `docs/usage/usage-docker-project.md`

Update the usage documentation to include:

**Design Decision: Save and Publish Only in onSuccess**

The `dockerProject` DSL intentionally restricts save and publish operations to the `onSuccess` block only. This differs from the `docker` DSL which provides standalone `dockerSave*` and `dockerPublish*` tasks.

**Rationale:**
- The `dockerProject` DSL is designed for the "build → test → publish" workflow
- Save and publish should only occur after tests pass to prevent publishing untested images
- This enforces a quality gate: images are only saved/published if they pass integration tests
- For use cases requiring save/publish independent of test results, use the `docker` DSL directly

**Example - Save/Publish on Success:**
```groovy
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            jarFrom.set(':app:jar')
            // Single image is automatically primary
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        // These only execute if tests pass (applied to primary image)
        saveFile.set('build/images/my-app.tar.gz')
        publishRegistry.set('registry.example.com')
        publishNamespace.set('myorg')
        publishTags.set(['latest', '1.0.0'])
        additionalTags.set(['tested', 'verified'])
    }
}
```

**What's NOT supported (by design):**
```groovy
// NOT SUPPORTED - no top-level save/publish blocks
dockerProject {
    images { myApp { ... } }
    test { ... }      // ✅ Supported
    onSuccess { ... } // ✅ Supported (save/publish go here, applied to primary image)
    save { ... }      // ❌ Does NOT exist - use onSuccess.saveFile instead
    publish { ... }   // ❌ Does NOT exist - use onSuccess.publishRegistry instead
}
```

For workflows requiring save/publish without testing, use the `docker` DSL:
```groovy
docker {
    images {
        myApp {
            imageName = 'my-app'
            // ...
        }
    }
}
// Use: ./gradlew dockerBuildMyApp dockerSaveMyApp dockerPublishMyApp
```

---

1. **Build Context Options Section** - Document all three context options:
   - `jarFrom` with `jarName` for simple Java projects
   - `contextTask` for complex/non-Java projects
   - `contextDir` for pre-existing contexts

2. **Dependency Examples for contextTask** - Include the three scenarios:

   **Scenario A: Self-contained project (no subproject dependencies)**
   ```groovy
   // All files are local to this project
   contextTask.set(tasks.register('prepareContext', Copy) {
       into layout.buildDirectory.dir('docker-context/myApp')
       from('src/main/docker')      // Dockerfile
       from('src/main/python')       // Python source
       from('models/')               // Model files
       from('requirements.txt')
   })
   ```

   **Scenario B: Depend on another project's task output**
   ```groovy
   // Gradle automatically infers task dependency from `project(':app').tasks.named('jar')`
   contextTask.set(tasks.register('prepareContext', Copy) {
       into layout.buildDirectory.dir('docker-context/myApp')
       from('src/main/docker')
       from(project(':app').tasks.named('jar')) { rename { 'app.jar' } }

       // Or depend on non-Java project output
       from(project(':model-training').tasks.named('trainModel')) {
           include '*.pkl'
       }
   })
   ```

   **Scenario C: Explicit task dependencies**
   ```groovy
   contextTask.set(tasks.register('prepareContext', Copy) {
       into layout.buildDirectory.dir('docker-context/myApp')
       from('src/main/docker')
       from('src/main/python')
       from('models/')

       // Explicit dependency on any task
       dependsOn(':data-prep:processData')
       dependsOn(':model-training:exportModel')
   })
   ```

3. **Property Reference Table** - Add/update:

   | Property | Purpose | Applies When |
   |----------|---------|--------------|
   | `name` | Docker image name (`docker build -t NAME`) | Always |
   | `repository` | Docker image repository (`namespace/name`) | Alternative to `name` |
   | `jarFrom` | Task path to JAR-producing task | Simple Java projects |
   | `jarName` | Custom name for JAR in context (default: `app.jar`) | When using `jarFrom` |
   | `contextTask` | Full control over context preparation | Complex/non-Java projects |
   | `contextDir` | Pre-existing context directory | External context |

4. **Supported `jarFrom` formats:**

   | Format | Meaning |
   |--------|---------|
   | `'jar'` | Current project's `jar` task |
   | `':jar'` | Root project's `jar` task |
   | `':app:jar'` | Subproject `:app`'s `jar` task |
   | `':sub:project:jar'` | Nested subproject's `jar` task |

---

## References

- [Gradle Configuration Cache Requirements](https://docs.gradle.org/current/userguide/configuration_cache.html)
- [Gradle 9 and 10 Compatibility](../gradle-9-and-10-compatibility.md)
- [DockerProjectExtension Source](../../../plugin/src/main/groovy/com/kineticfire/gradle/docker/DockerProjectExtension.groovy)
- [ProjectImageSpec Source](../../../plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/ProjectImageSpec.groovy)
- [ImageSpec Source](../../../plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy)
- [Integration Test Scenarios](../../../plugin-integration-test/dockerProject/)
- [Usage Documentation](../../usage/usage-docker-project.md)
