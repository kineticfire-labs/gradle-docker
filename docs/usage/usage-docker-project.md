# dockerProject DSL

The `dockerProject` DSL provides a simplified, single-block configuration for common Docker workflows. It combines the
capabilities of `docker { }`, `dockerTest { }`, and `dockerWorkflows { }` into one streamlined configuration.

## When to Use dockerProject vs the Three-DSL Approach

Use **`dockerProject`** when you have a simple, single-image workflow:
- One Docker image to build/test/publish
- Standard build-test-publish pipeline
- Default conventions are acceptable

Use the **three-DSL approach** (`docker { }`, `dockerTest { }`, `dockerWorkflows { }`) when:
- You have multiple images with complex relationships
- You need fine-grained control over task dependencies
- You need custom pipeline steps or non-standard workflows
- You need to share compose stacks across multiple pipelines

## Quick Start

### Build Mode (with jarFrom)

For Java/Groovy projects that build a JAR:

```groovy
plugins {
    id 'java'  // or 'groovy'
    id 'com.kineticfire.gradle.docker'
}

dockerProject {
    image {
        name.set('my-service')
        tags.set(['1.0.0', 'latest'])
        jarFrom.set('jar')  // References the 'jar' task
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }

    onSuccess {
        additionalTags.set(['tested', 'verified'])
    }
}
```

### Build Mode (with contextDir)

For projects with pre-existing Docker context directories:

```groovy
dockerProject {
    image {
        name.set('static-app')
        tags.set(['1.0.0', 'latest'])
        dockerfile.set('docker/Dockerfile')
        contextDir.set('docker')  // Pre-existing context directory
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
    }
}
```

### SourceRef Mode

For testing existing images from a registry:

```groovy
dockerProject {
    image {
        sourceRefRegistry.set('docker.io')
        sourceRefNamespace.set('library')
        sourceRefImageName.set('nginx')
        sourceRefTag.set('1.25-alpine')

        pullIfMissing.set(true)
        tags.set(['test-nginx', 'latest'])
    }

    test {
        compose.set('src/integrationTest/resources/compose/nginx.yml')
        waitForHealthy.set(['nginx'])
    }
}
```

## Configuration Reference

### image { } Block

The `image` block configures the Docker image to build or use.

#### Build Mode Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | `String` | (required) | Image name |
| `tags` | `List<String>` | `['latest']` | Image tags |
| `version` | `String` | (derived from tags) | Image version |
| `dockerfile` | `String` | `'src/main/docker/Dockerfile'` | Path to Dockerfile |
| `jarFrom` | `String` | (optional) | Task path to get JAR from (e.g., `'jar'`, `':app:jar'`) |
| `contextDir` | `String` | (optional) | Pre-existing context directory path |
| `buildArgs` | `Map<String, String>` | `[:]` | Docker build arguments |
| `labels` | `Map<String, String>` | `[:]` | Docker image labels |
| `registry` | `String` | `''` | Registry for the built image |
| `namespace` | `String` | `''` | Namespace for the built image |

**Note**: Either `jarFrom` or `contextDir` must be specified for build mode. They are mutually exclusive.

#### SourceRef Mode Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `sourceRef` | `String` | `''` | Full source image reference (e.g., `'docker.io/library/nginx:1.25'`) |
| `sourceRefRegistry` | `String` | `''` | Registry component |
| `sourceRefNamespace` | `String` | `''` | Namespace component |
| `sourceRefImageName` | `String` | `''` | Image name component |
| `sourceRefTag` | `String` | `''` | Tag component |
| `sourceRefRepository` | `String` | `''` | Repository (namespace/name) |
| `pullIfMissing` | `Boolean` | `false` | Pull image if not present locally |

**Note**: Use either `sourceRef` (full reference string) or the component properties (`sourceRefRegistry`, etc.).

#### Pull Authentication

```groovy
image {
    sourceRefImageName.set('private-image')
    pullIfMissing.set(true)

    pullAuth {
        username.set(System.getenv('REGISTRY_USER'))
        password.set(System.getenv('REGISTRY_PASS'))
    }
}
```

### test { } Block

The `test` block configures Docker Compose stack and test execution.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `compose` | `String` | (required) | Path to Docker Compose file |
| `waitForHealthy` | `List<String>` | `[]` | Services to wait for HEALTHY status |
| `waitForRunning` | `List<String>` | `[]` | Services to wait for RUNNING status |
| `lifecycle` | `String` | `'class'` | Container lifecycle: `'class'` or `'method'` |
| `testTaskName` | `String` | `'integrationTest'` | Test task to execute |
| `projectName` | `String` | (derived from project) | Compose project name |
| `timeoutSeconds` | `Integer` | `60` | Timeout for health/running checks |
| `pollSeconds` | `Integer` | `2` | Poll interval for status checks |

**Note**: The `test` block is optional. If omitted, no compose stack is created and the pipeline has only a build step.

### onSuccess { } Block

The `onSuccess` block configures actions when tests pass.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `additionalTags` | `List<String>` | `[]` | Tags to add on success |

#### Save Configuration

```groovy
onSuccess {
    // Simple save - compression inferred from filename
    save 'build/images/my-service.tar.gz'

    // Or with explicit compression
    save file: 'build/images/my-service.tar', compression: 'gzip'
}
```

Supported filename extensions for compression inference:
- `.tar.gz`, `.tgz` → GZIP
- `.tar.bz2`, `.tbz2` → BZIP2
- `.tar.xz`, `.txz` → XZ
- `.tar` → NONE
- `.zip` → ZIP

#### Publish Configuration

```groovy
onSuccess {
    publishRegistry.set('ghcr.io')
    publishNamespace.set('myorg')
    publishTags.set(['1.0.0', 'latest'])  // Optional, defaults to additionalTags
}
```

### onFailure { } Block

The `onFailure` block configures actions when tests fail.

```groovy
onFailure {
    additionalTags.set(['failed', 'needs-review'])
}
```

## Container Lifecycle Modes

### Class Lifecycle (Default)

Containers start once per test class:

```groovy
test {
    compose.set('src/integrationTest/resources/compose/app.yml')
    lifecycle.set('class')  // Default
}
```

### Method Lifecycle

Containers restart for each test method. Requires test class annotations:

```groovy
test {
    compose.set('src/integrationTest/resources/compose/app.yml')
    lifecycle.set('method')
}
```

**Requirements for METHOD lifecycle**:
- Set `maxParallelForks = 1` on the test task
- Annotate test classes with `@ComposeUp` (Spock) or `@ExtendWith(DockerComposeMethodExtension.class)` (JUnit 5)

## Generated Tasks

The `dockerProject` DSL generates the following tasks:

| Task | Description |
|------|-------------|
| `dockerBuild<ImageName>` | Build the Docker image |
| `dockerTag<ImageName>` | Tag the Docker image |
| `composeUp<StackName>` | Start the compose stack |
| `composeDown<StackName>` | Stop the compose stack |
| `run<ImageName>Pipeline` | Execute the full pipeline |

For an image named `my-service`, the tasks would be:
- `dockerBuildMyservice`
- `dockerTagMyservice`
- `composeUpMyservice`
- `composeDownMyservice`
- `runMyservicePipeline`

## Migration from Three-DSL Approach

### Before (Three-DSL)

```groovy
docker {
    images {
        myService {
            imageName.set('my-service')
            version.set('1.0.0')
            tags.set(['latest'])
            contextTask = tasks.named('prepareContext')
        }
    }
}

dockerTest {
    composeStacks {
        myServiceTest {
            composeFile.set(file('src/integrationTest/resources/compose/app.yml'))
            waitForHealthy {
                services.set(['app'])
            }
        }
    }
}

dockerWorkflows {
    pipelines {
        myServicePipeline {
            build {
                image.set(docker.images.myService)
            }
            test {
                stack.set(dockerTest.composeStacks.myServiceTest)
                testTaskName.set('integrationTest')
            }
            onTestSuccess {
                additionalTags.set(['tested'])
            }
        }
    }
}
```

### After (dockerProject)

```groovy
dockerProject {
    image {
        name.set('my-service')
        tags.set(['1.0.0', 'latest'])
        jarFrom.set('jar')
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

## Examples

### Full Build-Test-Publish Example

```groovy
plugins {
    id 'java'
    id 'com.kineticfire.gradle.docker'
}

dockerProject {
    image {
        name.set('my-api')
        tags.set(['1.2.0', 'latest'])
        jarFrom.set('jar')

        buildArgs.put('BUILD_VERSION', version)
        labels.put('org.opencontainers.image.title', 'My API Service')
        labels.put('org.opencontainers.image.version', version)
    }

    test {
        compose.set('src/integrationTest/resources/compose/api-stack.yml')
        waitForHealthy.set(['api', 'db', 'redis'])
        timeoutSeconds.set(120)
    }

    onSuccess {
        additionalTags.set(['tested', 'verified'])
        save 'build/images/my-api.tar.gz'
        publishRegistry.set('ghcr.io')
        publishNamespace.set('myorg')
    }

    onFailure {
        additionalTags.set(['failed'])
    }
}
```

### SourceRef with Private Registry

```groovy
dockerProject {
    image {
        sourceRefRegistry.set('private.registry.io')
        sourceRefNamespace.set('base-images')
        sourceRefImageName.set('java-base')
        sourceRefTag.set('21-alpine')

        pullIfMissing.set(true)

        pullAuth {
            username.set(System.getenv('REGISTRY_USER'))
            password.set(System.getenv('REGISTRY_PASS'))
        }

        name.set('my-java-base')
        tags.set(['verified', 'latest'])
    }

    test {
        compose.set('src/integrationTest/resources/compose/java-base-test.yml')
    }

    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

## See Also

- [docker DSL Usage](usage-docker.md) - For fine-grained image control
- [dockerTest DSL Usage](usage-docker-orch.md) - For compose stack management
- [dockerWorkflows DSL Usage](usage-docker-workflows.md) - For custom pipelines
