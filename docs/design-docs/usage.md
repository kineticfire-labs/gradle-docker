# Docker Plugin Usage Guide

This document provides examples of how to use the Gradle Docker plugin DSL for common scenarios involving existing Docker images.

## Prerequisites

First, add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
}
```

## Scenario 1: Apply Two Tags to an Existing Image

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

## Scenario 2: Save an Existing Image to File

```gradle
docker {
    images {
        myExistingImage {
            sourceRef = 'my-app:1.0.0'

            save {
                outputFile = file('build/docker-images/my-app.tar.gz')
                compression = 'gzip'  // Options: 'none', 'gzip', 'bzip2', 'xz', 'zip'
                pullIfMissing = false  // Set to true if image might not be local
            }
        }
    }
}
```

## Scenario 3: Publish an Existing Image to Local Registry

```gradle
docker {
    images {
        myExistingImage {
            sourceRef = 'my-app:1.0.0'

            publish {
                to('localRegistry') {
                    repository = 'localhost:5000/my-app'
                    publishTags = ['latest', 'v1.0.0']

                    // Optional authentication if required
                    auth {
                        username = 'myuser'
                        password = 'mypassword'
                        // Or use registry = 'localhost:5000' for registry-specific auth
                    }
                }
            }
        }
    }
}
```

## Scenario 4: Combined Actions - Tag, Save, and Publish

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
                compression = 'gzip'
                pullIfMissing = false
            }

            // Publish to local registry
            publish {
                to('localRegistry') {
                    repository = 'localhost:5000/my-app'
                    publishTags = ['v1.0.0', 'stable', 'latest']
                }

                // You can have multiple publish targets
                to('backup') {
                    repository = 'localhost:6000/backup/my-app'
                    publishTags = ['v1.0.0']
                }
            }
        }
    }
}
```

## Running the Tasks

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
2. **Tags vs PublishTags** - Use `tags.set([...])` for local tagging, and `publishTags = [...]` within publish targets
   for registry-specific tags
3. **Compression Options** - Available: `'none'`, `'gzip'`, `'bzip2'`, `'xz'`, `'zip'`
4. **Task Generation** - The plugin automatically generates tasks based on your image name (e.g.,
   `dockerTagMyExistingImage`, `dockerSaveMyExistingImage`)
5. **pullIfMissing** - Set to `true` if the source image might not be available locally and needs to be pulled

The plugin will execute these operations in the correct order automatically when you run the generated tasks.