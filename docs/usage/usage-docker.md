# 'docker' DSL Usage Guide

This document provides simple, informal examples of how to use the 'docker' DSL for the 'gradle-docker' plugin.

## Prerequisites

First, add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'com.kineticfire.gradle.docker' version '1.0.0'
}
```

## Recommended Directory Layout

Includes references to the use of the `dockerOrch` DSL for testing a Linux image; see `usage-docker-orch.md` for more
information.

```
the-application-project/                  # a project that (1) builds an application and tests it, and (2) puts the
│                                           application in a Linux image and tests the unit of delivery by spinning up
│                                           the container and testing it
├─ app/                                   # builds the application, such as a JAR (or other artifact)
│  ├─ build.gradle
│  └─ src/
│     ├─ main/java/...
│     └─ test/java/...
└─ app-image/                              # builds the Linux image + tests it
   ├─ build.gradle
   ├─ src/
   │  ├─ main/docker/                      # Dockerfile + build assets (image context)
   │  │  ├─ Dockerfile
   │  │  └─ ...                            # scripts, config, .dockerignore, etc.
   │  ├─ integrationTest/groovy/           # Groovy/Spock integration tests
   │  ├─ integrationTest/java/             # Java/JUnit integration tests
   │  ├─ integrationTest/resources/
   │  │  ├─ compose/                       # compose files for integration tests
   │  │  └─ docker/                        # optional: test-only wrapper image assets
   │  └─ testFixtures/                     # (optional) shared test helpers/utilities
   ├─ docs/                                # (optional) runbooks, diagrams for tests
   └─ build/                               # outputs: transcripts, logs, saved tars, state JSON, etc.
      ├─ docker/                           # image tars (from dockerSave*)
      ├─ compose-logs/                     # compose logs by task/suite
      └─ compose/                          # compose state files (JSON) per stack
```

## Quick Start: Minimal Example

The absolute minimum configuration to build a Docker image:

```groovy
docker {
    images {
        myApp {  // DSL block name - generates task: dockerBuildMyApp
            // Required: image name
            imageName = 'my-app'

            // Required: one or more tags
            tags = ['latest']

            // Required: task to prepare Docker build context (files needed for 'docker build')
            contextTask = tasks.register('prepareContext', Copy) {
                into layout.buildDirectory.dir('docker-context')
                from('src/main/docker')  // Must contain Dockerfile
            }
        }
    }
}

// Run: ./gradlew dockerBuildMyApp (task name from DSL block name 'myApp')
```

**What this does:**
- Copies files from `src/main/docker/` (including Dockerfile) to `build/docker-context/`
- Builds Docker image named `my-app:latest` using `build/docker-context/Dockerfile`
- Stores image in local Docker daemon

## Build Context Setup

### What is contextTask?

`contextTask` prepares the **Docker build context** - the directory containing all files available during `docker build`.
This typically includes:
- Dockerfile
- Application artifacts (JAR, WAR, etc.)
- Configuration files, scripts, assets

### Directory Convention

The plugin follows this convention:
- **Context output**: `layout.buildDirectory.dir('docker-context/<imageName>')`
- **Default Dockerfile location**: `build/docker-context/<imageName>/Dockerfile`
- **Override with**: `dockerfile.set(file(...))` (full path) or `dockerfileName.set(...)` (name within context)

### Example

```groovy
docker {
    images {
        webApp {  // DSL block name - generates tasks: dockerBuildWebApp, dockerSaveWebApp, etc.
            imageName = 'web-app'  // Docker image name - NOT used for task names
            tags = ['latest']

            // Convention: output to docker-context/<dslBlockName>
            contextTask = tasks.register('prepareWebAppContext', Copy) {
                into layout.buildDirectory.dir('docker-context/webApp')
                from('src/main/docker')
            }
            // Default Dockerfile: build/docker-context/webApp/Dockerfile
        }
    }
}
```

## Two Usage Modes

The plugin supports two distinct usage modes:

1. **Build Mode** - Building new Docker images using nomenclature options
   1. **Image Name Mode** - registry, namespace, imageName, tags
   2. **Repository Mode** - registry, repository, tags
2. **SourceRef Mode** - Working with existing/pre-built images using sourceRef
   1. **Complete SourceRef Mode** - string (e.g., "ghcr.io/company/app:v1.0")
   2. **Image Name Mode** - registry, namespace, imageName, tag
   3. **Repository Mode** - registry, repository, tag

### When to Use Each Mode

| Scenario                              | Mode           | Why                                       |
|---------------------------------------|----------------|-------------------------------------------|
| Building from scratch with Dockerfile | Build Mode     | Creating new image                        |
| Building custom application image     | Build Mode     | Need to specify Dockerfile, build context |
| Tagging existing image locally        | SourceRef Mode | No build needed, image already exists     |
| Publishing third-party image          | SourceRef Mode | Image already built                       |


## Gradle 9 and 10 Compatibility

This plugin is fully compatible with Gradle 9 and 10, including configuration cache support. Follow these patterns for
best compatibility in [Gradle 9 and 10 Compatibility](gradle-9-and-10-compatibility-practices.md).

### Why Use Provider API?

The Provider API enables:
- **Configuration cache** (Gradle 9/10 requirement)
- **Lazy evaluation** - values resolved at execution time, not configuration time
- **Performance** - avoids unnecessary work during configuration

Example:
```groovy
// Good: Lazy evaluation with Provider API
version.set(providers.provider { project.version.toString() })
buildArgs.put('JAR_FILE', jarFileNameProvider)

// Bad: Eager evaluation (breaks configuration cache)
version.set(project.version.toString())  // Evaluated during configuration
```

See `gradle-9-and-10-compatibility-practices.md` for complete details.

## Property Assignment Styles

The plugin supports two property assignment styles:

### Style 1: Simple Assignment (Groovy DSL Convenience)

```groovy
docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['latest', '1.0.0']
            registry = 'ghcr.io'
        }
    }
}
```

**When to use**: Simple, static values in Groovy DSL.

### Style 2: Explicit .set() (Gradle Provider API)

```groovy
docker {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            registry.set('ghcr.io')
            version.set(providers.provider { project.version.toString() })
        }
    }
}
```

**When to use**:
- **Required for Kotlin DSL**
- Provider values (`.provider`, `.map`, `.flatMap`)
- Complex value composition

**Both styles work in Groovy DSL.** Choose based on preference and use case.

## Build Mode: Building New Docker Images

Use Docker nomenclature properties for building new images.

Two naming approaches:
1. registry (optional) + namespace (optional) + name + tag(s)
2. registry (optional) + repository + tag(s)

Recommended:
1. Create an image reference sufficient for the local image store, such as:
   1. namespace + name + tag(s)
   2. repository + tag(s)
2. When publishing an image:
   1. Set the registry
   2. If necessary, override the namespace

Choose one of two naming approaches:

### Minimal Build Configuration

```groovy
docker {
    images {
        simpleApp {
            imageName = 'simple-app'
            tags = ['latest']

            contextTask = tasks.register('prepareSimpleContext', Copy) {
                into layout.buildDirectory.dir('docker-context')
                from('src/main/docker')
            }
        }
    }
}
```

This is the minimum required configuration for Build Mode.

### Multi-Project JAR Build (Common Pattern)

**Common use case**: Build a Docker image containing a JAR from another Gradle project.

```groovy
// Get JAR file from app subproject using Provider API (Gradle 9/10 compatible)
def jarFileProvider = project(':app').tasks.named('bootJar').flatMap { it.archiveFile }
def jarFileNameProvider = jarFileProvider.map { it.asFile.name }

docker {
    images {
        webApp {
            imageName = 'web-app'
            tags = ['latest', '1.0.0']

            // Build context task
            contextTask = tasks.register('prepareWebAppContext', Copy) {
                group = 'docker'
                description = 'Prepare Docker build context for web app image'
                into layout.buildDirectory.dir('docker-context/webApp')
                from('src/main/docker')

                // Copy JAR from app project using provider
                from(jarFileProvider) {
                    rename { jarFileNameProvider.get() }
                }

                // Ensure JAR is built first
                dependsOn project(':app').tasks.named('bootJar')
            }

            // Pass JAR file name as build arg (lazy evaluation)
            buildArgs.put('JAR_FILE', jarFileNameProvider)
        }
    }
}
```

**Key points:**
- Uses `.flatMap()` to get task output as Provider
- Uses `.map()` to transform provider value
- JAR filename evaluated at execution time, not configuration time
- `dependsOn` ensures JAR is built before context preparation

### Approach 1: Registry + Namespace + ImageName + Tag(s)

```groovy
import static com.kineticfire.gradle.docker.model.SaveCompression.NONE

docker {
    images {
        timeServer {
            // Build configuration - prepares Docker build context
            contextTask = tasks.register('prepareTimeServerContext', Copy) {
                group = 'docker'
                description = 'Prepare Docker build context for time server image'
                into layout.buildDirectory.dir('docker-context/timeServer')
                from('src/main/docker')

                // Example: copy JAR from multi-project build
                def jarFileProvider = project(':app').tasks.named('jar').flatMap { it.archiveFile }
                from(jarFileProvider) {
                    rename { "app-${project.version}.jar" }
                }
                dependsOn project(':app').tasks.named('jar')
            }

            // Approach 1: Image Name Mode - registry, namespace, imageName, tag(s)
            registry = "ghcr.io"                     // Optional registry
            namespace = "kineticfire/stuff"          // Optional namespace
            imageName = "time-server"                // Required: image name
            // version defaults to project.version; override if needed:
            // version.set(providers.provider { project.version.toString() })
            tags = ["latest", "1.0.0"]               // Required: one or more tags

            // Optional build arguments using 'put' w/ provider API (Gradle 9/10 compatible)
            buildArgs.put("JAR_FILE", providers.provider { "app-${project.version}.jar" })
            buildArgs.put("BUILD_VERSION", providers.provider { project.version.toString() })

            // Optional build arguments using 'putAll' w/ provider
            //buildArgs.putAll(providers.provider {
            //  [
            //          'JAR_FILE': "app-${project.version}.jar",
            //          'BUILD_VERSION': project.version.toString()
            //  ]
            //})

            // Optional custom labels using 'put' w/ provider API (Gradle 9/10 compatible)
            labels.put("org.opencontainers.image.revision", providers.provider { gitSha })
            labels.put("org.opencontainers.image.version", providers.provider { project.version.toString() })
            labels.put("maintainer", "team@kineticfire.com")

            // Optional custom labels using 'putAll' w/ provider
            //labels.putAll(providers.provider {
            //  [
            //          "org.opencontainers.image.revision": gitSha,
            //          "org.opencontainers.image.version": project.version.toString(),
            //          "maintainer": "team@kineticfire.com"
            //  ]
            //})

            // No dockerfile specified, so defaults to 'build/docker-context/timeServer/Dockerfile'
            // Override options:
            //   dockerfileName.set("CustomDockerFile")  // name within context directory
            //   dockerfile.set(file("path/to/CustomDockerfile"))  // absolute path

            // Optional: save image to file
            save {
                compression.set(NONE)  // Enum: NONE, GZIP, BZIP2, XZ, ZIP; defaults to NONE
                outputFile.set(layout.buildDirectory.file("docker-images/time-server.tar.gz"))
            }

            // Optional: publish to registries (see section 'Comprehensive Publishing Examples')
            publish {
                to('localRegistry') {
                    registry.set("localhost:5000")
                    namespace.set("test")
                    publishTags.set(["edge", "test"])   // Published tag names
                }
            }
        }
    }
}
```

### Approach 2: Registry + Repository + Tag(s)

```groovy
import static com.kineticfire.gradle.docker.model.SaveCompression.ZIP

docker {
    images {
        myApp {
            contextTask = tasks.register('prepareMyAppContext', Copy) {
                into layout.buildDirectory.dir('docker-context')
                from('src/main/docker')
            }

            // Approach 2: registry (optional) + repository + tag(s)
            registry = "docker.io"                   // Optional registry
            repository = "acme/my-awesome-app"       // Required: full repository path
            // version defaults to project.version; override if needed:
            // version.set(providers.provider { "1.2.3" })
            tags = ["latest", "stable"]              // Required: one or more tags

            labels.put("description", providers.provider { "My awesome application" })

            save {
                compression.set(ZIP)
                outputFile.set(file("build/my-app.zip"))
            }
        }
    }
}
```

## SourceRef Mode: Working with Existing Images

Use `sourceRef` for working with existing/pre-built images. No build properties allowed.

### Scenario 1: Apply Tags to Existing Image

```groovy
docker {
    images {
        existingApp {
            // Use existing image - NO build properties allowed
            sourceRef.set("ghcr.io/acme/myapp:1.2.3")
            // defaults to pullIfMissing.set(false)

            // Apply new local tags to the sourceRef
            tags.set(["local:latest", "local:stable"])
        }
    }
}
```

### Scenario 2: Save Existing Image with Pull Support

```groovy
import static com.kineticfire.gradle.docker.model.SaveCompression.GZIP

docker {
    images {
        remoteImage {
            sourceRef.set("ghcr.io/acme/private-app:1.0.0")
            // defaults to pullIfMissing.set(false)

            save {
                compression.set(GZIP)
                outputFile.set(layout.buildDirectory.file("docker-images/private-app.tar.gz"))

                // Optional authentication - only needed if registry requires credentials
                // If omitted and registry requires auth, Docker will return an auth error
                auth {
                    username.set("myuser")
                    password.set("mypass")
                    // serverAddress extracted automatically from image reference
                }
            }
        }
    }
}
```

### Scenario 3: Tag, Save, and Publish Existing Image

```groovy
import static com.kineticfire.gradle.docker.model.SaveCompression.BZIP2

docker {
    images {
        myExistingImage {
            sourceRef.set("my-app:1.0.0")
           // defaults to pullIfMissing.set(false)

            // Apply tags first
            tags.set(["timeServer:1.0.0", "timeServer:stable"])

            // Save to file
            save {
                compression.set(BZIP2)  // Enum value required
                outputFile.set(file("build/docker-images/my-app-v1.0.0.tar.bz2"))
            }

            // Publish to registries
            publish {
                to('dockerhub') {
                    registry.set("docker.io")
                    repository.set("acme/myapp")
                    publishTags.set(["published-latest"])

                    auth {
                        username.set("dockeruser")
                        password.set("dockerpass")
                    }
                }

                to('backup') {
                    registry.set("backup.registry.com")
                    namespace.set("acme")
                    imageName.set("myapp")
                    publishTags.set(["backup-latest"])
                }
            }
        }
    }
}
```

### Scenario 4: Pull Missing Images Automatically

```groovy
import static com.kineticfire.gradle.docker.model.SaveCompression.GZIP

docker {
    images {
        myApp {
            sourceRef.set("ghcr.io/company/baseimage:v2.0")
            pullIfMissing.set(true)  // Image-level setting - applies to all operations

            // Pull-specific authentication at image level
            pullAuth {
                username.set(providers.environmentVariable("GHCR_USER"))
                password.set(providers.environmentVariable("GHCR_TOKEN"))
            }

            // All operations inherit pullIfMissing behavior
            save {
                compression.set(GZIP)
                outputFile.set(layout.buildDirectory.file("docker-images/baseimage.tar.gz"))
            }

            tag {
                tags.set(["local:latest", "local:stable"])
            }

            publish {
                to('prod') {
                    registry.set("docker.io")
                    repository.set("company/myapp")
                    publishTags.set(["published-latest"])
                }
            }
        }
    }
}
```

### Scenario 5: SourceRef Component Assembly

Instead of specifying a full sourceRef string, you can assemble it from components:

```groovy
docker {
    images {
        // Pattern A: Individual component properties
        alpineApp {
            // Assemble sourceRef from components
            sourceRefRegistry.set("docker.io")      // Registry
            sourceRefNamespace.set("library")       // Namespace/organization
            sourceRefImageName.set("alpine")        // Image name (required)
            sourceRefTag.set("3.18")               // Tag (defaults to "latest" if omitted)
            // Results in: docker.io/library/alpine:3.18

            pullIfMissing.set(true)

            save {
                // compression type defaults to NONE
                outputFile.set(file("build/alpine-base.tar"))
            }
        }

        // Pattern B: Helper method for component assembly
        ubuntuApp {
            sourceRef("docker.io", "library", "ubuntu", "22.04")
            // Results in: docker.io/library/ubuntu:22.04

            pullIfMissing.set(true)
        }

        // Pattern C: Mixed usage - some components, some full references
        customApp {
            sourceRefRegistry.set("my-registry.company.com:5000")
            sourceRefImageName.set("custom-base")
            // sourceRefTag defaults to "latest"
            // Results in: my-registry.company.com:5000/custom-base:latest

            pullIfMissing.set(true)

            pullAuth {
                username.set(providers.environmentVariable("COMPANY_REGISTRY_USER"))
                password.set(providers.environmentVariable("COMPANY_REGISTRY_TOKEN"))
            }
        }
    }
}
```

### Scenario 6: SourceRef Component Assembly (Repository Approach)

Using the repository approach for component assembly (alternative to namespace+imageName):

```groovy
docker {
    images {
        // Repository-based component assembly
        ghcrApp {
            sourceRefRegistry.set("ghcr.io")
            sourceRefRepository.set("company/myapp")    // Combines namespace/imageName
            sourceRefTag.set("v2.1.0")
            // Results in: ghcr.io/company/myapp:v2.1.0

            pullIfMissing.set(true)

            pullAuth {
                username.set(providers.environmentVariable("GHCR_USER"))
                password.set(providers.environmentVariable("GHCR_TOKEN"))
            }

            save {
                // compression type defaults to NONE
                outputFile.set(file("build/ghcr-app.tar"))
            }
        }

        // Mixed approaches in same project (different images)
        dockerhubApp {
            sourceRefRegistry.set("docker.io")
            sourceRefRepository.set("username/webapp")
            // sourceRefTag defaults to "latest"
            // Results in: docker.io/username/webapp:latest

            pullIfMissing.set(true)

            tag {
                tags.set(["local:webapp"])
            }
        }

        // Registry-only repository
        localApp {
            sourceRefRepository.set("internal/app")     // No registry specified
            sourceRefTag.set("stable")
            // Results in: internal/app:stable

            pullIfMissing.set(true)

            publish {
                to('prod') {
                    registry.set("prod.company.com")
                    repository.set("production/app")
                    publishTags.set(["deployed"])
                }
            }
        }
    }
}
```

## Provider API Patterns

Common Provider API patterns for configuration cache compatibility:

### Pattern 1: Task Output as Provider

```groovy
// Get JAR file from task output
def jarFileProvider = project(':app').tasks.named('bootJar').flatMap { it.archiveFile }

// Transform provider value
def jarFileNameProvider = jarFileProvider.map { it.asFile.name }

// Use in buildArgs
buildArgs.put('JAR_FILE', jarFileNameProvider)
```

### Pattern 2: Environment Variables

```groovy
// Read environment variable as provider
pullAuth {
    username.set(providers.environmentVariable("GHCR_USER"))
    password.set(providers.environmentVariable("GHCR_TOKEN"))
}
```

### Pattern 3: Project Properties

```groovy
// Use project version as provider
version.set(providers.provider { project.version.toString() })

// Compose providers
buildArgs.put('APP_VERSION', providers.provider { project.version.toString() })
```

### Pattern 4: Provider Composition

```groovy
// Chain multiple transformations
def jarFileProvider = project(':app').tasks.named('bootJar')
    .flatMap { it.archiveFile }
    .map { it.asFile.name }

// Use .get() only at execution time (inside task action or Copy spec)
from(jarFileProvider) {
    rename { jarFileProvider.get() }  // OK: executed during Copy task action
}
```

**Key rule**: Never call `.get()` during configuration phase (outside of task actions).

## Comprehensive Publishing Examples

### Basic Registry Examples


#### Private Registry
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }

            publish {
                to('privateRegistry') {
                    registry.set("my-company.com:5000")
                    repository.set("team/myapp")
                    publishTags.set(["latest", "beta"])
                    // No auth block - registry allows anonymous push
                }
            }
        }
    }
}
```


#### Private Registry & Inherit Image Properties
```groovy
docker {
    images {
        myApp {

            // build a new image
            contextTask = ...

            registry = 'my-company.com:5000'
            namespace = 'library'
            imageName = 'my-image'
            tags = ['1.21']

            // use 'sourceRef' for existing image
            //sourceRefRegistry.set('my-company.com:5000')
            //sourceRefNamespace.set('library')
            //sourceRefImageName.set('nginx')
            //sourceRefTag.set('1.21')

            publish {
                to('privateRegistry') {
                }
            }
        }
    }
}
```


#### Docker Hub Publishing
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }

            registry = "docker.io"  // Explicit Docker Hub registry
            repository = "username/myapp"
            publishTags = ["latest", "v1.0"]

            publish {
                to('dockerhub') {
                    registry.set("docker.io")  // Explicit Docker Hub registry
                    repository.set("username/myapp")
                    publishTags.set(["latest", "v1.0"])

                    auth {
                        username.set(providers.environmentVariable("DOCKERHUB_USERNAME"))
                        // Use Personal Access Token (preferred) instead of password
                        password.set(providers.environmentVariable("DOCKERHUB_TOKEN"))
                        // serverAddress automatically extracted as docker.io
                    }
                }
            }
        }
    }
}
```

#### GitHub Container Registry
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }

            registry = "ghcr.io"
            repository = "username/myapp"
            publishTags = ["latest"]

            publish {
                to('ghcr') {
                    registry.set("ghcr.io")
                    repository.set("username/myapp")
                    publishTags.set(["latest"])

                    auth {
                        username.set(providers.environmentVariable("GHCR_USERNAME"))
                        password.set(providers.environmentVariable("GHCR_PASSWORD"))
                        // serverAddress automatically extracted as ghcr.io
                    }
                }
            }
        }
    }
}
```

### Registry Authentication Examples

#### Private Registry WITHOUT Authentication
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }

            publish {
                to('privateRegistry') {
                    registry.set("my-company.com:5000")
                    repository.set("team/myapp")
                    publishTags.set(["latest", "beta"])
                    // No auth block - registry allows anonymous push
                }
            }
        }
    }
}
```

#### Private Registry WITH Authentication
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }

            publish {
                to('authenticatedRegistry') {
                    registry.set("secure-registry.company.com")
                    namespace.set("engineering")
                    imageName.set("myapp")
                    publishTags.set(["latest", "v2.1"])

                    auth {
                        username.set(providers.environmentVariable("REGISTRY_USERNAME"))
                        password.set(providers.environmentVariable("REGISTRY_PASSWORD"))
                        // serverAddress automatically extracted as secure-registry.company.com
                    }
                }
            }
        }
    }
}
```

### Local Development Example

#### Local Development Registry
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }

            publish {
                to('localDev') {
                    registry.set("localhost:5000")  // Example for local dev registry
                    repository.set("myapp")
                    publishTags.set(["dev", "latest"])
                    // No auth block - local registry typically runs without authentication
                }
            }
        }
    }
}
```

**Note**: Environment variables are validated automatically by the plugin with helpful error messages if missing or
empty, including registry-specific suggestions for common variable names.

## Key API Properties

### Docker Nomenclature Properties
- `registry` or `registry.set(String)` - Registry hostname (e.g., "ghcr.io", "localhost:5000")
- `namespace` or `namespace.set(String)` - Namespace/organization (e.g., "kineticfire/stuff")
- `imageName` or `imageName.set(String)` - Image name (e.g., "my-app")
- `repository` or `repository.set(String)` - Alternative to namespace+imageName (e.g., "acme/my-app")
- `version` or `version.set(String)` - Image version (defaults to project.version; override if needed)
- `tags` or `tags.set(List<String>)` - Tag names only (e.g., ["latest", "1.0.0"])
- `labels.put(String, String)` - Custom Docker labels for build

### docker.compression Enum
Use enum values instead of strings:
- `docker.compression.NONE` - No compression
- `docker.compression.GZIP` - Gzip compression (.tar.gz)
- `docker.compression.BZIP2` - Bzip2 compression (.tar.bz2)
- `docker.compression.XZ` - XZ compression (.tar.xz)
- `docker.compression.ZIP` - ZIP compression (.zip)

### pullIfMissing and SourceRef Properties (Image-Level)
- `pullIfMissing.set(Boolean)` - Whether to pull source image if missing locally (defaults to false)
- `sourceRef.set(String)` - Full source image reference (e.g., "ghcr.io/company/app:v1.0")
- `sourceRefRegistry.set(String)` - Source registry component (e.g., "docker.io")
- `sourceRefNamespace.set(String)` - Source namespace component (e.g., "library")
- `sourceRefImageName.set(String)` - Source image name component (e.g., "alpine")
- `sourceRefTag.set(String)` - Source tag component (defaults to "latest" if omitted)
- `sourceRefRepository.set(String)` - Source repository component (e.g., "company/app") - alternative to
  namespace+imageName

### pullAuth Configuration (Image-Level)
```groovy
pullAuth {
    username.set(providers.environmentVariable("REGISTRY_USER"))
    password.set(providers.environmentVariable("REGISTRY_TOKEN"))
}
```

**Note**: pullAuth is separate from publish auth - pullAuth is used for pulling source images, while publish auth is
used for pushing to target registries.

### Authentication for Save Operations

```groovy
save {
    // Optional - only add if registry requires authentication
    // If omitted, operation proceeds without auth (may fail if registry requires it)
    auth {
        username.set("user")
        password.set("pass")
        // serverAddress automatically extracted from image reference
    }
}
```

**Note**: Authentication blocks are always optional. If a registry requires authentication and none is provided, Docker
will return a descriptive authentication error.

## Running Generated Tasks

### Task Naming Convention

The plugin generates task names based on the **DSL block name** (the identifier used in the `images { }` block),
NOT the `imageName` property value.

**Pattern**: `docker<Operation><CapitalizedDslBlockName>`

**Example:**
```groovy
docker {
    images {
        webApp {  // <-- DSL block name: "webApp" (used for task names)
            imageName = 'example-web-app'  // <-- Docker image name (NOT used for task names)
            tags = ['latest']
        }
    }
}
```

**Generated tasks:**
- `dockerBuildWebApp` - Build operation + capitalized "webApp"
- `dockerSaveWebApp` - Save operation + capitalized "webApp"
- `dockerTagWebApp` - Tag operation + capitalized "webApp"
- `dockerPublishWebApp` - Publish operation + capitalized "webApp"
- `dockerImageWebApp` - All operations + capitalized "webApp"

**Note**: The Docker image will be named `example-web-app:latest`, but tasks use the DSL block name `webApp`.

### Task Execution Examples

```bash
# For a specific DSL block named 'timeServer'
./gradlew dockerBuildTimeServer    # Build only
./gradlew dockerTagTimeServer      # Tag only
./gradlew dockerSaveTimeServer     # Save only
./gradlew dockerPublishTimeServer  # Publish only

# Run ALL configured operations for specific DSL block named 'timeServer'
./gradlew dockerImageTimeServer

# Run specific operation across ALL images
./gradlew dockerBuild     # Build all images
./gradlew dockerSave      # Save all images
./gradlew dockerTag       # Tag all images
./gradlew dockerPublish   # Publish all images

# Run ALL operations for ALL images
./gradlew dockerImages
```

## Validation Rules

### Build Mode Rules
- **Mutually exclusive**: Cannot use both `repository` and `namespace`+`imageName`
- **Required**: Either `repository` OR `imageName` must be specified
- **No sourceRef**: Cannot use `sourceRef` with build properties (contextTask, buildArgs, labels, etc.)

### SourceRef Mode Rules
- **Required sourceRef**: Must specify valid `sourceRef` image reference
- **No build properties**: Cannot use contextTask, buildArgs, labels, dockerfile when using `sourceRef`
- **Authentication**: Optional for all registries; provide auth block if registry requires credentials

### Usage Notes and Patterns

#### pullIfMissing Behavior
- **Default**: `pullIfMissing.set(false)` - operations fail if source image is missing locally
- **When true**: automatically pulls source image if not found locally before performing operation
- **Applies to**: save, tag, and publish operations when using sourceRef mode
- **Authentication**: use `pullAuth` block for pull operations, separate from publish authentication

#### SourceRef vs Build Context
- **Cannot combine**: pullIfMissing=true with build context (contextTask, dockerfile, buildArgs) will throw validation
  error
- **Either/or**: use pullIfMissing for existing images OR build context for new images, not both

#### Component Assembly Priority
1. Full `sourceRef.set("registry/namespace/image:tag")` takes precedence over all components
2. Repository approach: `sourceRefRepository` takes precedence over `sourceRefNamespace + sourceRefImageName`
3. Namespace approach: used when `sourceRefRepository` is not specified
4. Required: either `sourceRef` OR `sourceRefRepository` OR `sourceRefImageName` must be specified when using
   pullIfMissing=true

#### Component Assembly Patterns
- **Full Reference**: `sourceRef.set("ghcr.io/company/app:v1.0")` - complete image reference
- **Repository Assembly**: `sourceRefRegistry + sourceRefRepository + sourceRefTag` - mirrors build mode repository
  approach
- **Namespace Assembly**: `sourceRefRegistry + sourceRefNamespace + sourceRefImageName + sourceRefTag` - mirrors build
  mode namespace approach
- **Mutually Exclusive**: Cannot mix repository and namespace approaches in same image configuration

## Task Dependencies

### Automatic Dependencies

The plugin automatically configures these dependencies:

- `dockerSaveTimeServer` depends on `dockerBuildTimeServer` (when image has build context)
- `dockerPublishTimeServer` depends on `dockerBuildTimeServer` (when image has build context)
- `dockerImageTimeServer` depends on all configured operations for that image
- `dockerImages` depends on all `dockerImage*` tasks

### Multi-Project Task Dependencies

For multi-project builds, wire task dependencies using `afterEvaluate`:

```groovy
// Ensure JAR is built before Docker context is prepared
afterEvaluate {
    tasks.named('prepareWebAppContext') {
        dependsOn project(':app').tasks.named('bootJar')
    }
}
```

This pattern is common when:
- Building Docker images from JARs in other projects
- Copying artifacts from multi-project builds
- Coordinating build order across projects

## Integration with dockerOrch DSL

The `docker` and `dockerOrch` DSLs are **independent** but commonly used together:
- **docker DSL**: Build, tag, save, and publish Docker images
- **dockerOrch DSL**: Test images using Docker Compose orchestration

They are **mutually exclusive in purpose** - use `docker` DSL for image operations, `dockerOrch` DSL for testing.

### Common Pattern: Build then Test

```groovy
// Build image with docker DSL
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

// Test image with dockerOrch DSL
dockerOrch {
    composeStacks {
        myTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = 'my-app-test'

            waitForHealthy {
                waitForServices.set(['my-app'])
                timeoutSeconds.set(60)
            }
        }
    }
}

// Wire tasks: build image before compose up
afterEvaluate {
    tasks.named('composeUpMyTest') {
        dependsOn tasks.named('dockerBuildMyApp')
    }
}
```

**Note**: See `docs/usage/usage-docker-orch.md` for complete `dockerOrch` DSL documentation.

## Key Benefits

1. **Gradle 9 & 10 Compatibility** - Full configuration cache and Provider API support with best practices
2. **Proper Docker Nomenclature** - Separate registry, namespace, imageName, tags following Docker standards
3. **Custom Labels** - Add metadata to built images using provider-safe patterns
4. **Enhanced Authentication** - Support for private registries in save operations
5. **Dual Mode Support** - Build new images OR work with existing images seamlessly
6. **Type Safety** - Enum-based compression options prevent typos
7. **Configuration Cache Ready** - Optimized for Gradle's configuration cache with proper provider usage

# Available Gradle Tasks

## Complete Operation Aggregate Tasks
These tasks run ALL configured operations for images:

- `dockerImages` - Run all configured operations for all images
- `dockerImageTimeServer` - Run all configured operations for timeServer image
- `dockerImageMyApp` - Run all configured operations for myApp image

## Operation-Specific Aggregate Tasks
These tasks run specific operations across all images:

- `dockerBuild` - Build all configured images
- `dockerSave` - Save all configured images to files
- `dockerTag` - Tag all configured images
- `dockerPublish` - Publish all configured images to registries

## Per-Image Operation Tasks
These tasks run specific operations for specific images and are dynamically created based on the name of the DSL block
such as, in this case, `timeServer`:

- `dockerBuildTimeServer` - Build timeServer image
- `dockerSaveTimeServer` - Save timeServer image to file
- `dockerTagTimeServer` - Tag timeServer image
- `dockerPublishTimeServer` - Publish timeServer image to registries

## Task Execution Examples

```bash
# Run ALL operations for ALL images (build + tag + save + publish)
./gradlew dockerImages

# Run ALL operations for specific image
./gradlew dockerImageTimeServer

# Run specific operation for ALL images
./gradlew dockerSave
./gradlew dockerBuild

# Run specific operation for specific image
./gradlew dockerSaveTimeServer
./gradlew dockerBuildTimeServer
```

## Integration Test Best Practices

For integration tests, use complete operation tasks:

```groovy
tasks.register('integrationTest') {
    dependsOn 'dockerImages'  // Runs all configured operations
    dependsOn 'verifyDockerImages'
    // ...
}
```

This ensures all configured operations (build, tag, save, publish) are executed before verification.
