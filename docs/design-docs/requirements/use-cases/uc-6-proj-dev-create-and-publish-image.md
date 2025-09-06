# Use Case - 6 - Project Developer Create, Tag, Save, and Publish Image

## Document Metadata

| Key     | Value       |
|---------|-------------|
| Status  | Implemented |
| Version | 1.0.0       |
| Updated | 2025-08-31  |

## Definition

**Actor**: Project Developer

**Goal**: Create, tag, save, and/or publish a Docker image

**Preconditions**: `gradle-docker` plugin applied to the build, Docker CLI installed, Docker daemon available, can run 
Docker commands without `sudo`

**Post conditions**: Docker image is created, (optionally) additional tags are created, and (optionally) published to a 
Docker image registry

**Steps by Actor to achieve goal**:
1. Project Developer creates a `build.gradle` file
1. Project Developer applies the `gradle-docker` plugin in their `build.gradle` file
1. Project Developer configures the Gradle task in a task configuration block
1. Project Developer invokes Gradle tasks to, for all images configured: build only; build and tag; build, tag, and 
save; build, tag, and publish; or to perform those actions on a per-image basis  

**Alternative steps by Actor to achieve goal**:
1. Project Developer creates a `build.gradle` file
1. Project Developer applies the `gradle-docker` plugin in their `build.gradle` file
1. Project Developer configures one or more Gradle tasks in a task configuration block, based on the desired outcomes,
e.g., build, tag, save, publish.
1. Project Developer invokes the appropriate tasks for all configured images or on a per-image basis 

**Derived functional requirements**: fr-11, fr-12, fr-13, fr-14, fr-15, fr-16, fr-17, fr-18, fr-19, fr-20, fr-21, fr-22

**Derived non-functional requirements**: nfr-23, nfr-24, nfr-25  

## Assumptions

It is assumed, by convention defined by this plugin, that the Docker context (files used to build the Docker image 
including the Dockerfile) are located at `src/main/docker`. If no context is explicitly specified in the 
configuration, the plugin will fallback to this convention.

The plugin views a project with a goal of building a software application as a Docker image as having two subprojects:
one to build the application and one to build the Docker image:
- `the-application-project/`
   - `app/`
      - `src/main/java/...`
      - `src/test/java/...`
   - `app-image/`
      - `src/main/docker/`

In this scenario, the `app/` directory contains the source and build file to build the application, such as a JAR file.
The `app-image/` directory then assembles the application into a Docker image.  This applies the Separation of Concern
(SoC) principle, since the build in the `app-image` directory will carry out several activities specifically related
to the image (not the original source code or resultant JAR file) like building, tagging, saving, and publishing the
image; it could also perform integration tests on the image (not shown here or discussed in this use case, see 
[Project Dev Compose Orchestration](uc-7-proj-dev-compose-orchestration.md)).

## Task Naming Conventions and Dependencies

The plugin generates tasks using the pattern `docker<Action><ImageName>` where:
- `image("alpine")` generates tasks: `dockerBuildAlpine`, `dockerSaveAlpine`, `dockerTagAlpine`, `dockerPublishAlpine`
- `image("ubuntu")` generates tasks: `dockerBuildUbuntu`, `dockerSaveUbuntu`, `dockerTagUbuntu`, `dockerPublishUbuntu`
- Image names are capitalized in task names (first letter uppercase)

**Task Dependencies**:
- **With build context**: `dockerSave` depends on `dockerBuild`, `dockerPublish` depends on `dockerBuild`
- **Without build context** (using `sourceRef`): Tasks are independent and operate on pre-existing images
- Aggregate tasks (`dockerBuild`, `dockerSave`, etc.) operate on all configured images

## Concept Groovy DSL Using the Plugin's Docker Tasks

### Configuration to Build, Tag, Save, and Publish

DSL configuration to build, tag, save, and publish:
```groovy
plugins { id "com.kineticfire.gradle.gradle-docker" version "0.1.0" }

docker {
    images {
        image("alpine") {
            context    = file("src/main/docker")
            dockerfile = file("src/main/docker/Dockerfile")
            buildArgs  = [ "BASE_IMAGE": "eclipse-temurin:21-jre-alpine", "JAR_FILE":"app.jar" ]
            tags       = ["myapp:${version}-alpine", "myapp:alpine"]
            save {
                compression = "gzip"  // Options: "none", "gzip", "bzip2", "xz", "zip"
                outputFile  = layout.buildDirectory.file("docker/pkg/myapp-${version}-alpine.tar.gz")
            }
            publish { // per-image publish targets
                to {
                    name       = "ghcr"
                    repository = "ghcr.io/acme/myapp"
                    tags       = ["${version}-alpine", "alpine"]
                    auth {
                        username = providers.gradleProperty("GHCR_USER")
                        password = providers.gradleProperty("GHCR_TOKEN")
                    }
                }
                to {
                    name       = "dockerhub"
                    repository = "acme/myapp" // docker.io/acme/myapp
                    tags       = ["${version}-alpine"]
                    auth {
                        username = providers.gradleProperty("DH_USER")
                        password = providers.gradleProperty("DH_TOKEN")
                    }
                }
            }
        }

        image("ubuntu") {
            context    = file("src/main/docker")
            dockerfile = file("src/main/docker/Dockerfile")
            buildArgs  = [ "BASE_IMAGE": "eclipse-temurin:21-jre-ubuntu", "JAR_FILE":"app.jar" ]
            tags       = ["myapp:${version}-ubuntu", "myapp:ubuntu"]
            save {
                compression = "gzip"  // Options: "none", "gzip", "bzip2", "xz", "zip"
                outputFile  = layout.buildDirectory.file("docker/pkg/myapp-${version}-ubuntu.tar.gz")
            }
            publish {
                to {
                    name       = "private"
                    repository = "registry.example.com/myteam/myapp"
                    tags       = ["${version}-ubuntu", "ubuntu"]
                    auth {
                        helper = "generic"   // your plugin: e.g., reads REG_USER/REG_PASS env, or custom login
                        username = providers.gradleProperty("PRIV_USER")
                        password = providers.gradleProperty("PRIV_TOKEN")
                    }
                }
            }
        }
    }

    // OPTIONAL: extra retags after build (source → targets)
    dockerTag {
        images = [
                "myapp:${version}-alpine": ["ghcr.io/acme/myapp:edge"],
                "myapp:${version}-ubuntu": ["registry.example.com/myteam/myapp:latest"]
        ]
    }
}
```

Invoke tasks to build, save, tag, and publish the images:
```bash
# build all images (dockerSave/dockerPublish will depend on this)
./gradlew dockerBuild
# build a specific image
./gradlew dockerBuildAlpine dockerBuildUbuntu

# retag as configured (optional dockerTag block above)
./gradlew dockerTag
# retag a specific image  
./gradlew dockerTagAlpine dockerTagUbuntu

# save all configured images to files (depends on dockerBuild when context present)
./gradlew dockerSave
# save a specific image
./gradlew dockerSaveAlpine dockerSaveUbuntu

# publish to all configured registries (depends on dockerBuild when context present)
./gradlew dockerPublish
# publish a specific image
./gradlew dockerPublishAlpine dockerPublishUbuntu
```

### Tagging images (retag)

DSL to tag (retag) images without building:
```groovy
docker {
    images {
        // No build context/dockerfile here → this is an *external* (prebuilt) image
        image("alpine") {
            sourceRef = "ghcr.io/acme/myapp:1.2.3-alpine"
            tag {
                pullIfMissing = true
                to {
                    repository = "docker.io/acme/myapp"
                    tags       = ["1.2.3-alpine", "alpine"]
                }
                to {
                    repository = "ghcr.io/acme/myapp"
                    tags       = ["edge-alpine"]
                }
            }
        }
        image("ubuntu") {
            sourceRef = "registry.example.com/myteam/myapp:1.2.3-ubuntu"
            tag {
                pullIfMissing = true
                to {
                    repository = "docker.io/acme/myapp"
                    tags       = ["1.2.3-ubuntu", "ubuntu"]
                }
                to {
                    repository = "registry.example.com/myteam/myapp"
                    tags       = ["latest-ubuntu"]
                }
            }
        }
    }
}
```
Assumes myapp:1.2.3-* already exist locally—e.g., built in a prior step or pulled.

Invoke tasks to tag (retag images) without building:
```bash
# tag all
./gradlew dockerTag
# tag a specific image  
./gradlew dockerTagAlpine dockerTagUbuntu
```

### Publishing multiple images without building

DSL to publish multiple images without building:
```groovy
docker {
    images {
        // No build context/dockerfile here → this is an *external* (prebuilt) image
        image("alpine") {
            publish {
                to {
                    repository = "docker.io/acme/myapp"
                    tags       = ["1.2.3-alpine", "alpine"]
                    auth {
                        username = providers.gradleProperty("DH_USER")
                        password = providers.gradleProperty("DH_TOKEN")
                    }
                }
            }
        }
        image("ubuntu") {
            publish {
                to {
                    repository = "registry.example.com/myteam/myapp"
                    tags       = ["1.2.3-ubuntu", "ubuntu"]
                    auth {
                        username = providers.gradleProperty("PRIV_USER")
                        password = providers.gradleProperty("PRIV_TOKEN")
                    }
                }
            }
        }
    }
}
```

Invoke tasks to publish images without building:
```bash
# publish all
./gradlew dockerPublish
# publish specific image
./gradlew dockerPublishAlpine dockerPublishUbuntu
```

### Save images to files without building

DSL to save images to file without building:
```groovy
plugins { id "com.kineticfire.gradle.gradle-docker" version "0.1.0" }

docker {
    images {
        // No build context/dockerfile here → this is an *external* (prebuilt) image
        image("alpine") {
            sourceRef   = "ghcr.io/acme/myapp:1.2.3-alpine"  // the image you want to save
            save {
                compression = "gzip"  // Options: "none", "gzip", "bzip2", "xz", "zip"                            // or "none"
                outputFile  = layout.buildDirectory.file("docker/pkg/myapp-1.2.3-alpine.tar.gz")
                pullIfMissing = true                            // plugin pulls if not local
            }
        }
        image("ubuntu") {
            sourceRef   = "registry.example.com/myteam/myapp:1.2.3-ubuntu"
            save {
                compression = "gzip"  // Options: "none", "gzip", "bzip2", "xz", "zip"
                outputFile  = layout.buildDirectory.file("docker/pkg/myapp-1.2.3-ubuntu.tar.gz")
                pullIfMissing = true
            }
        }
    }
}
```

If the images aren’t local yet, ensure your plugin pulls them or document that users should docker pull first.

Invoke tasks to save images without building:
```bash
# save all (no dependencies since no build context)
./gradlew dockerSave
# save a specific image
./gradlew dockerSaveAlpine dockerSaveUbuntu
```