# 'docker' DSL Usage Guide

This document provides simple, informal examples of how to use the 'docker' DSL for the 'gradle-docker' plugin.

## Prerequisites

First, add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
}
```

## Recommended Directory Layout

```
the-application-project/                  # a project that (1) builds an application and (2) puts the application in a Linux image
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

## Two Usage Modes

The plugin supports two distinct usage modes:

1. **Build Mode** - Building new Docker images using nomenclature options
   1. registry, namespace, imageName, tags
   2. registry, repository, tags
2. **SourceRef Mode** - Working with existing/pre-built images using `sourceRef`

## Build Mode: Building New Docker Images

Use Docker nomenclature properties for building new images. Choose one of two naming approaches:

### Approach 1: Registry + Namespace + ImageName

```groovy
docker {
    images {
        timeServer {
            // Build configuration
            contextTask = tasks.register('prepareTimeServerContext', Copy) {
                group = 'docker'
                description = 'Prepare Docker build context for time server image'
                into layout.buildDirectory.dir('docker-context/timeServer')
                from('src/main/docker')
                from(file('../../app/build/libs')) {
                    include 'app-*.jar'
                    def projectVersion = version
                    rename { "app-${projectVersion}.jar" }
                }
                dependsOn ':app:jar'
            }
            
            // Docker nomenclature (Approach 1)
            registry.set("ghcr.io")                     // Optional registry
            namespace.set("kineticfire/stuff")          // Optional namespace
            imageName.set("time-server")                // Required: image name
            version.set(project.version.toString())     // Defaults to project.version
            tags.set(["latest", "1.0.0"])              // Tag names only (no registry/namespace)
            
            // Build arguments
            buildArgs.put("JAR_FILE", "app-${version}.jar")
            buildArgs.put("BUILD_VERSION", version)
            buildArgs.put("BUILD_TIME", new Date().format('yyyy-MM-dd HH:mm:ss'))
            
            // Custom labels
            labels.put("org.opencontainers.image.revision", gitSha)
            labels.put("org.opencontainers.image.version", version)
            labels.put("maintainer", "team@kineticfire.com")
            
            save {
                compression.set(SaveCompression.GZIP)  // Enum: NONE, GZIP, BZIP2, XZ, ZIP
                outputFile.set(layout.buildDirectory.file("docker-images/time-server.tar.gz"))
                // defaults to pullIfMissing.set(false)
            }
            
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

### Approach 2: Registry + Repository

```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareMyAppContext', Copy) { /* ... */ }
            
            // Docker nomenclature (Approach 2) - mutually exclusive with namespace+imageName
            registry.set("docker.io")                   // Optional registry  
            repository.set("acme/my-awesome-app")       // Full repository path
            version.set("1.2.3")
            tags.set(["latest", "stable"])
            
            labels.put("description", "My awesome application")
            
            save {
                compression.set(SaveCompression.ZIP)
                outputFile.set(file("build/my-app.zip"))
                // defaults to pullIfMissing.set(false)
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
            
            // Apply new local tags to the sourceRef
            tags.set(["local:latest", "local:stable"])
        }
    }
}
```

### Scenario 2: Save Existing Image with Pull Support

```groovy
docker {
    images {
        remoteImage {
            sourceRef.set("ghcr.io/acme/private-app:1.0.0")
            
            save {
                compression.set(SaveCompression.GZIP)
                outputFile.set(layout.buildDirectory.file("docker-images/private-app.tar.gz"))
                pullIfMissing.set(true)  // Pull if not available locally
                
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
docker {
    images {
        myExistingImage {
            sourceRef.set("my-app:1.0.0")
            
            // Apply tags first
            tags.set(["timeServer:1.0.0", "timeServer:stable"])
            
            // Save to file
            save {
                compression.set(SaveCompression.BZIP2)  // Enum value required
                outputFile.set(file("build/docker-images/my-app-v1.0.0.tar.bz2"))
                // defaults to pullIfMissing.set(false)
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

## Comprehensive Publishing Examples

### Basic Registry Examples

#### Docker Hub Publishing
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }
            
            registry.set("docker.io")  // Explicit Docker Hub registry
            repository.set("username/myapp")
            publishTags.set(["latest", "v1.0"])
            
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
            
            registry.set("ghcr.io")
            repository.set("username/myapp")
            publishTags.set(["latest"])
            
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

**Note**: Environment variables are validated automatically by the plugin with helpful error messages if missing or empty, including registry-specific suggestions for common variable names.

## Key API Properties

### Docker Nomenclature Properties
- `registry.set(String)` - Registry hostname (e.g., "ghcr.io", "localhost:5000")
- `namespace.set(String)` - Namespace/organization (e.g., "kineticfire/stuff")  
- `imageName.set(String)` - Image name (e.g., "my-app")
- `repository.set(String)` - Alternative to namespace+imageName (e.g., "acme/my-app")
- `version.set(String)` - Image version (defaults to project.version)
- `tags.set(List<String>)` - Tag names only (e.g., ["latest", "1.0.0"])
- `labels.put(String, String)` - Custom Docker labels for build (**NEW!**)

### SaveCompression Enum
Use enum values instead of strings:
- `SaveCompression.NONE` - No compression
- `SaveCompression.GZIP` - Gzip compression (.tar.gz)
- `SaveCompression.BZIP2` - Bzip2 compression (.tar.bz2)  
- `SaveCompression.XZ` - XZ compression (.tar.xz)
- `SaveCompression.ZIP` - ZIP compression (.zip)

### Authentication for Save Operations  
Save operations support optional authentication when `pullIfMissing=true`:
```groovy
save {
    pullIfMissing.set(true)
    
    // Optional - only add if registry requires authentication
    // If omitted, operation proceeds without auth (may fail if registry requires it)
    auth {
        username.set("user")
        password.set("pass")
        // serverAddress automatically extracted from image reference
    }
}
```

**Note**: Authentication blocks are always optional. If a registry requires authentication and none is provided, Docker will return a descriptive authentication error.

### Provider API Properties
All properties use Gradle's Provider API for configuration cache compatibility:
- `.set(value)` - Set property value
- `.convention(defaultValue)` - Set default value
- `.get()` - Get property value (only in task actions)

## Running Generated Tasks

The plugin automatically generates tasks based on the DSL block name:

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

## Key Benefits

1. **Gradle 9 Compatibility** - Full configuration cache and Provider API support
2. **Proper Docker Nomenclature** - Separate registry, namespace, imageName, tags
3. **Custom Labels** - Add metadata to built images
4. **Enhanced Authentication** - Support for private registries in save operations
5. **Dual Mode Support** - Build new images OR work with existing images
6. **Type Safety** - Enum-based compression options prevent typos

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
These tasks run specific operations for specific images:

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

## Task Dependencies

### Automatic Dependencies
The plugin automatically configures these dependencies:

- `dockerSaveTimeServer` depends on `dockerBuildTimeServer` (when image has build context)
- `dockerPublishTimeServer` depends on `dockerBuildTimeServer` (when image has build context)
- `dockerImageTimeServer` depends on all configured operations for that image
- `dockerImages` depends on all `dockerImage*` tasks

### Build Context Types
Both traditional `context` and `contextTask` scenarios get proper dependencies:

```groovy
// Traditional context
docker {
    images {
        myApp {
            context = file('src/main/docker')
            save {
                outputFile = file('myapp.tar')
                compression = 'gzip'  // Required parameter
            }  // ← dockerSaveMyApp depends on dockerBuildMyApp
        }
    }
}

// Copy task context
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }
            save {
                outputFile = file('myapp.tar')
                compression = 'none'  // Required parameter
            }  // ← dockerSaveMyApp depends on dockerBuildMyApp
        }
    }
}
```