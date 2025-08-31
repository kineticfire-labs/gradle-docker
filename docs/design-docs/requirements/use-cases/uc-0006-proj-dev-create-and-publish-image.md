# Use Case - 00006 - Project Developer Create and Publish Image

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
Docker commands with `sudo`

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

**Derived functional requirements**: fr-0001, fr-0002, fr-0003, fr-0004, fr-0005, fr-0006, fr-0007

**Derived non-functional requirements**:  

## Assumptions

It is assumed, by convention defined by this plugin, that the Docker context (files used to build the Docker image 
including the Dockerfile) are located at `src/docker`.

The plugin views a project with a goal of building a software application as a Docker image as having two subprojects:
one to build the application and one to build the Docker image:
- `the-application-project/`
   - `app/`
      - `src/main/java/...`
      - `src/test/java/...`
   - `app-docker/`
      - `src/main/docker/`
      - `src/test/`
         - `docker/`
         - `compose/`

In this scenario, the `app/` directory contains the source and build file to build the application, such as a JAR file.
The `app-docker/` directory then assembles the application into a Docker image and tests it, possibly using images from
`docker/` and/or `compose/` files to orchestration multiple containers.

## Concept Groovy DSL Using the Plugin's Docker Tasks

```groovy
dockerClean {}

dockerBuild {
    context = [file("src/other/docker"), file("src/another/docker")]
    dockerfile = file("Dockerfile")
    buildArgs = [ "JAR_FILE": "app.jar" ]
    tags = ["myapp:${project.version}", "myapp:latest"]
}

dockerTag {
    sourceImage = "myapp:latest"
    targetImage = ["myapp:edge", "myapp:nightly"]
}

dockerPublish {
    image ["myapp:${project.version}", "myapp:latest", "myapp:edge", "myapp:nightly"]
}

dockerSave {
    image "myapp:${project.version}"
    compression "gzip"
    outputDirectory "docker-build/image"
    outputFilename "myapp-${project.version}.tar.gz"
}
```
