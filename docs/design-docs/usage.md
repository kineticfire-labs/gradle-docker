# Docker Plugin Usage Guide

This document provides simple, informal examples of how to use the Gradle Docker plugin DSL.

> **Breaking Change Note:** The `compression` parameter is now required for save configuration. This ensures explicit control over output format and prevents unexpected compressed files. Since this project is in early development with no external users, no backward compatibility is provided.

## Prerequisites

First, add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
}
```

## Building a New Docker Image

```groovy
docker {
    images {
        timeServer {
            contextTask = tasks.register('prepareTimeServerContext', Copy) {
                group = 'docker'
                description = 'Prepare Docker build context for time server image'
                into layout.buildDirectory.dir('docker-context/timeServer')
                from('src/main/docker')
                from(file('../../app/build/libs')) {
                    include 'app-*.jar'
                    def projectVersion = version // Capture version during configuration time
                    rename { "app-${projectVersion}.jar" }
                }
                dependsOn ':app:jar'
            }
            // dockerfile will default to 'build/docker-context/timeServer/Dockerfile' based on image name
            //   - could set custom name as dockerfileName = 'MyDockerfile', then path will be 'build/docker-context/timeServer/MyDockerfile'
            //   - could set custom path/name with dockerfile.set(file('build/docker-context/timeServer/custom/path/CustomDockerfile')) 
            tags.set(["time-server:${version}",
                      "time-server:latest"
            ])
            buildArgs = [
                'JAR_FILE': "app-${version}.jar",
                'BUILD_VERSION': version,
                'BUILD_TIME': new Date().format('yyyy-MM-dd HH:mm:ss')
            ]
            publish {
                to('basic') {
                    tags = ['localhost:5000/time-server:latest']
                }
            }
        }
    }
}
```

### 


```bash
# For tagging only
./gradlew dockerTagMyExistingImage

# For saving only
./gradlew dockerSaveMyExistingImage

# For publishing only
./gradlew dockerPublishMyExistingImage

# For combined operations (runs all configured operations)
./gradlew dockerBuildMyExistingImage

# Or run all Docker operations for all images
./gradlew dockerBuild
```

## Acting on a Docker Image That is Already Built

### Scenario 1: Apply Two Tags to an Existing Image

```gradle
docker {
    images {
        myExistingImage {
            // Specify the existing image reference
            sourceRef = 'my-app:1.0.0'  // or any other existing image reference

            // Apply two new tags
            tags.set(['timeServer:1.0.0', 'timeServer:stable'])
        }
    }
}
```

### Scenario 2: Save an Existing Image to File

```gradle
docker {
    images {
        myExistingImage {
            sourceRef = 'my-app:1.0.0'

            save {
                outputFile = file('build/docker-images/my-app.tar.gz')
                compression = 'gzip'  // Required! Options: 'none', 'gzip', 'bzip2', 'xz', 'zip'
                pullIfMissing = false  // Set to true if image might not be local
            }
        }
    }
}
```

### Scenario 3: Publish an Existing Image to Local Registry

```gradle
docker {
    images {
        myExistingImage {
            sourceRef = 'my-app:1.0.0'

            publish {
                to('localRegistry') {
                    tags = ['localhost:5000/timeServer:1.0.0', 'localhost:5000/timeServer:stable']

                    // Optional authentication if required
                    auth {
                        username.set('myuser')
                        password.set('mypassword')
                        serverAddress.set('localhost:5000')
                    }
                }
            }
        }
    }
}
```

### Scenario 4: Combined Actions - Tag, Save, and Publish

```gradle
docker {
    images {
        myExistingImage {
            sourceRef = 'my-app:1.0.0'

            // Apply tags first
            tags.set(['timeServer:1.0.0', 'timeServer:stable'])

            // Save to file
            save {
                outputFile = file('build/docker-images/my-app-v1.0.0.tar.gz')
                compression = 'gzip'  // Required parameter
                pullIfMissing = false
            }

            // Publish to local registry
            publish {
                to('localRegistry') {
                    tags = ['localhost:5000/timeServer:1.0.0', 'localhost:5000/timeServer:stable']
                }

                // You can have multiple publish targets
                to('backup') {
                    tags = ['backup.registry.com/timeServer:1.0.0', 'backup.registry.com/timeServer:stable']
                }
            }
        }
    }
}
```

### Running the Tasks

After configuring your `build.gradle`, you can run the generated tasks:

```bash
# For tagging only
./gradlew dockerTagMyExistingImage

# For saving only
./gradlew dockerSaveMyExistingImage

# For publishing only
./gradlew dockerPublishMyExistingImage

# For combined operations (runs all configured operations)
./gradlew dockerBuildMyExistingImage

# Or run all Docker operations for all images
./gradlew dockerBuild
```

## Key Points

1. **`sourceRef`** - This is the key property that tells the plugin you're working with an existing image rather than
   building from a Dockerfile
2. **Image Tags vs Publish Tags** - Use `tags.set([...])` for local image tagging, and `tags = [...]` within publish targets
   for full registry image references
3. **Compression Options** - Required parameter. Available: `'none'`, `'gzip'`, `'bzip2'`, `'xz'`, `'zip'`
4. **Task Generation** - The plugin automatically generates tasks based on your image name (e.g.,
   `dockerTagMyExistingImage`, `dockerSaveMyExistingImage`)
5. **pullIfMissing** - Set to `true` if the source image might not be available locally and needs to be pulled

The plugin will execute these operations in the correct order automatically when you run the generated tasks.