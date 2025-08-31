# Use Case - 6 - Project Developer Create and Publish Image

## Document Metadata

| Key     | Value      |
|---------|------------|
| Status  | Draft      |
| Version | 0.0.0      |
| Updated | 2025-08-30 |

## Definition

**Actor**: Project Developer

**Goal**: Create and publish a Docker image

**Preconditions**: `gradle-docker` plugin applied to the build, Docker CLI installed, Docker daemon available, can run 
Docker commands without `sudo`

**Post conditions**: Docker image is created, (optionally) additional tags are created, and (optionally) published to a 
Docker image registry

**Steps by Actor to achieve goal**:
1. Project Developer creates a `build.gradle` file
1. Project Developer applies the `gradle-docker` plugin in their `build.gradle` file
1. Project Developer invokes the task (using the command line or triggered by another task) to assemble the Docker build
context and any configurations
1. Project Developer invokes the task (using the command line or triggered by another task) to build the Docker image
using the defined context
1. Project Developer optionally invokes the task (using the command line or triggered by another task) to create one or
more tags for the image
1. Project Developer optionally invokes the task (using the command line or triggered by another task) to publish one or
   more tagged images to a Docker image registry

**Derived functional requirements**: fr-1, fr-2, fr-3, fr-4, fr-5, fr-6, fr-7, fr-8, fr-9, fr-10, fr-11

**Derived non-functional requirements**:  

## Assumptions

It is assumed, by convention defined by this plugin, that the Docker context (files used to build the Docker image 
including the Dockerfile) are located at `src/main/docker`.

The plugin views a project with a goal of building a software application as a Docker image as having two subprojects:
one to build the application and one to build the Docker image:
- `the-application-project/`
   - `app/`
      - `src/main/java/...`
      - `src/test/java/...`
   - `app-image/`
      - `src/main/docker/`

In this scenario, the `app/` directory contains the source and build file to build the application, such as a JAR file.
The `app-image/` directory then assembles the application into a Docker image and tests it, possibly using images from
`docker/` and/or `compose/` files to orchestration multiple containers.

## Concept Groovy DSL Using the Plugin's Docker Tasks

### Define and Build Multiple Images

DSL to define and build multiple images:
```groovy
plugins {
    id "com.kineticfire.gradle.gradle-docker" version "0.1.0"
}

docker {
    images {
        image("alpine") {
            context    = file("src/main/docker")
            dockerfile = file("src/main/docker/Dockerfile")
            buildArgs  = [
                    "BASE_IMAGE": "eclipse-temurin:21-jre-alpine",
                    "JAR_FILE"  : "app.jar"
            ]
            tags = ["myapp:${version}-alpine", "myapp:alpine"]
            // optional: artifact export for this image
            save {
                compression = "gzip" // or "none"
                outputFile  = layout.buildDirectory.file("docker/myapp-${version}-alpine.tar.gz")
            }
        }
        image("ubuntu") {
            context    = file("src/main/docker")
            dockerfile = file("src/main/docker/Dockerfile")
            buildArgs  = [
                    "BASE_IMAGE": "eclipse-temurin:21-jre-ubuntu",
                    "JAR_FILE"  : "app.jar"
            ]
            tags = ["myapp:${version}-ubuntu", "myapp:ubuntu"]
        }
    }
}
```

Invoke tasks to build the images:
```groovy
./gradlew dockerBuildAlpine dockerBuildUbuntu
# or all at once:
./gradlew dockerBuildAll
```

### Tagging multiple images (retag)

DSL to tag (retag) multiple images:
```groovy
// Map sources → list of target tags (repo/tag)
dockerTag {
  images = [
    "myapp:${version}-alpine": [
      "ghcr.io/acme/myapp:${version}-alpine",
      "ghcr.io/acme/myapp:alpine"
    ],
    "myapp:${version}-ubuntu": [
      "ghcr.io/acme/myapp:${version}-ubuntu",
      "ghcr.io/acme/myapp:ubuntu"
    ]
  ]
}
```

Invoke tasks to tag (retag multiple images):
```groovy
./gradlew dockerTagAll    # runs all tag rules above
# or task-per-image if your plugin exposes them, e.g.:
# ./gradlew dockerTagAlpine dockerTagUbuntu
```

### Publishing multiple images

DSL to publish multiple images:
```groovy
dockerPublish {
  // push these exact refs (can mix local & retagged)
  images = [
    "ghcr.io/acme/myapp:${version}-alpine",
    "ghcr.io/acme/myapp:alpine",
    "ghcr.io/acme/myapp:${version}-ubuntu",
    "ghcr.io/acme/myapp:ubuntu"
  ]
  // optional pass-through for registry auth/env; plugin shells out to `docker push`
  registry {
    // url = "ghcr.io"            // optional; informational
    // username = providers.gradleProperty("REG_USER")
    // password = providers.gradleProperty("REG_TOKEN")
  }
}
```

Invoke tasks to publish multiple images:
```groovy
./gradlew dockerPublishAll
# or publish a subset:
# ./gradlew dockerPublish --images ghcr.io/acme/myapp:${version}-alpine
```

### Save images to files

DSL to save images to file (note the `save` block):
```groovy
docker {
  images {
    image("alpine") {
      tags = ["myapp:${version}-alpine", "myapp:alpine"]
      // ...
      save {
        compression = "gzip"  // "none" supported too
        outputFile  = layout.buildDirectory.file("docker/pkg/myapp-${version}-alpine.tar.gz")
      }
    }
    image("ubuntu") {
      tags = ["myapp:${version}-ubuntu", "myapp:ubuntu"]
      // ...
      save {
        compression = "gzip"
        outputFile  = layout.buildDirectory.file("docker/pkg/myapp-${version}-ubuntu.tar.gz")
      }
    }
  }
}
```

Invoke tasks to save images:
```groovy
./gradlew dockerSaveAlpine dockerSaveUbuntu
# or all:
./gradlew dockerSaveAll
```