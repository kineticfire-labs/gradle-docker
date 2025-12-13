# Example User Build.gradle for dockerWorkflows Plugin

**Date:** 2025-12-01
**Status:** Reference Documentation
**Related Documents:**
- [Architectural Limitations Plan](architectural-limitations-plan.md)
- [Why dockerWorkflows Cannot Support Method Lifecycle](../workflow-lifecycle/workflow-cannot-method-lifecycle.md)

---

## Overview

This document provides a clean example of what a **user's build.gradle** file would look like when using the
`dockerWorkflows` plugin. Unlike the integration test build.gradle files, this example excludes verification code
and test infrastructure that users don't need.

---

## Complete Example Build.gradle

```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker' version '1.0.0'
}

repositories {
    mavenCentral()
}

// Dependencies for your integration tests
dependencies {
    integrationTestImplementation 'org.apache.groovy:groovy-all:4.0.15'
    integrationTestImplementation 'org.spockframework:spock-core:2.3-groovy-4.0'
    integrationTestRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.0'
    integrationTestImplementation 'io.rest-assured:rest-assured:5.3.2'
}

// =============================================================================
// DOCKER DSL: Define the image to build
// =============================================================================
docker {
    images {
        myApp {
            imageName = 'my-company/my-app'
            tags = ['latest', '1.0.0']

            // Option 1: Use a context directory directly
            // context.set(file('src/main/docker'))

            // Option 2: Use a contextTask for more complex builds
            contextTask = tasks.register('prepareDockerContext', Copy) {
                into layout.buildDirectory.dir('docker-context/myApp')
                from('src/main/docker')

                // Copy your application JAR into the context
                from(tasks.named('jar')) {
                    rename { 'app.jar' }
                }
            }
        }
    }
}

// =============================================================================
// DOCKER ORCH DSL: Define the compose stack for testing
// =============================================================================
dockerTest {
    composeStacks {
        appTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = 'my-app-test'

            waitForHealthy {
                waitForServices.set(['app'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}

// =============================================================================
// DOCKER WORKFLOWS DSL: Define the pipeline
// =============================================================================
dockerWorkflows {
    pipelines {
        ciPipeline {
            description = 'CI pipeline: build, test, tag on success'

            // Build step: build the myApp image
            build {
                image = docker.images.myApp
            }

            // Test step: run integration tests against the containerized app
            test {
                stack = dockerTest.composeStacks.appTest
                testTaskName = 'integrationTest'
            }

            // On test success: add tags to mark the image as verified
            onTestSuccess {
                additionalTags = ['tested', 'verified']
            }
        }
    }
}

// =============================================================================
// Integration Test Configuration (optional customization)
// =============================================================================
tasks.named('integrationTest') {
    // Pass any system properties your tests need
    systemProperty 'APP_BASE_URL', 'http://localhost:8080'
}
```

---

## Differences: Integration Test vs User Build.gradle

The integration test build.gradle files include verification code that users don't need:

| Integration Test Has | User Build.gradle Needs |
|---------------------|------------------------|
| `mavenLocal()` repository | Not needed (plugin from Maven Central) |
| `runWorkflowIntegrationTest` task | Not needed (just run `runCiPipeline`) |
| `cleanupWorkflow` task | Not needed (plugin handles cleanup) |
| Verification `exec` blocks | Not needed |
| `afterEvaluate` task wiring | Not needed (pipeline handles orchestration) |
| Complex dependency paths | Simple plugin version reference |
| `buildSrc` dependencies | Not needed |

---

## How to Run

```bash
# Run the full pipeline (build -> test -> tag on success)
./gradlew runCiPipeline

# Or run individual tasks if needed
./gradlew dockerBuildMyApp           # Just build the image
./gradlew integrationTest            # Just run tests (manual compose management)
./gradlew dockerTagMyApp             # Just tag the image
```

---

## Supporting Files

### Compose File

Location: `src/integrationTest/resources/compose/app.yml`

```yaml
services:
  app:
    image: my-company/my-app:latest
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 5
```

### Dockerfile

Location: `src/main/docker/Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY app.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=3s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### Integration Test

Location: `src/integrationTest/groovy/com/mycompany/MyAppIT.groovy`

```groovy
package com.mycompany

import spock.lang.Specification
import static io.restassured.RestAssured.*

class MyAppIT extends Specification {

    def "health endpoint returns OK"() {
        when:
        def response = given()
            .baseUri('http://localhost:8080')
            .get('/health')

        then:
        response.statusCode() == 200
    }

    def "API returns expected data"() {
        when:
        def response = given()
            .baseUri('http://localhost:8080')
            .get('/api/data')

        then:
        response.statusCode() == 200
        response.body().jsonPath().getString('status') == 'success'
    }
}
```

---

## Pipeline Execution Flow

When you run `./gradlew runCiPipeline`, the plugin orchestrates these steps automatically:

```
1. dockerBuildMyApp       - Builds the Docker image
2. composeUpAppTest       - Starts the compose stack
3. [wait for healthy]     - Waits for containers to be healthy
4. integrationTest        - Runs your integration tests
5. composeDownAppTest     - Stops the compose stack
6. [if tests passed]      - Applies 'tested' and 'verified' tags
```

If tests fail, the pipeline:
- Still runs `composeDownAppTest` (cleanup always runs)
- Does NOT apply the success tags
- Fails the build with appropriate error message

---

## Variations

### Multiple Pipelines

```groovy
dockerWorkflows {
    pipelines {
        devPipeline {
            build { image = docker.images.myApp }
            test {
                stack = dockerTest.composeStacks.appTest
                testTaskName = 'integrationTest'
            }
            // No onTestSuccess - just build and test
        }

        releasePipeline {
            build { image = docker.images.myApp }
            test {
                stack = dockerTest.composeStacks.appTest
                testTaskName = 'integrationTest'
            }
            onTestSuccess {
                additionalTags = ['release', 'stable', 'v1.0.0']
            }
        }
    }
}
```

Run with: `./gradlew runDevPipeline` or `./gradlew runReleasePipeline`

### Delegated Stack Management (Class Lifecycle)

For test isolation at the class level:

```groovy
dockerWorkflows {
    pipelines {
        ciPipeline {
            build { image = docker.images.myApp }
            test {
                testTaskName = 'integrationTest'
                delegateStackManagement = true  // Let testIntegration handle compose
            }
            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}

testIntegration {
    usesCompose(integrationTest, 'appTest', 'class')  // Class-level lifecycle
}
```

**Note:** Method-level lifecycle is NOT supported with `dockerWorkflows`. See
[Why dockerWorkflows Cannot Support Method Lifecycle](../workflow-lifecycle/workflow-cannot-method-lifecycle.md).

### With Hooks

```groovy
dockerWorkflows {
    pipelines {
        ciPipeline {
            build {
                image = docker.images.myApp
                beforeBuild = { println "Starting build..." }
                afterBuild = { println "Build complete!" }
            }
            test {
                stack = dockerTest.composeStacks.appTest
                testTaskName = 'integrationTest'
                beforeTest = { println "Starting tests..." }
                afterTest = { result -> println "Tests ${result.success ? 'passed' : 'failed'}!" }
            }
            onTestSuccess {
                additionalTags = ['tested']
                afterSuccess = { println "Success tags applied!" }
            }
        }
    }
}
```

---

## Project Structure

```
my-project/
├── build.gradle                              # Main build file (example above)
├── settings.gradle
├── src/
│   ├── main/
│   │   ├── java/                             # Your application code
│   │   └── docker/
│   │       └── Dockerfile                    # Your Dockerfile
│   └── integrationTest/
│       ├── groovy/
│       │   └── com/mycompany/
│       │       └── MyAppIT.groovy            # Your integration tests
│       └── resources/
│           └── compose/
│               └── app.yml                   # Your compose file
└── gradle/
    └── libs.versions.toml                    # Version catalog (optional)
```

---

## Summary

The `dockerWorkflows` DSL simplifies the build-test-tag workflow by:

1. **Orchestrating tasks automatically** - No need for manual `dependsOn` or `finalizedBy` wiring
2. **Handling cleanup** - Compose stacks are always torn down, even on test failure
3. **Conditional tagging** - Success tags only applied when tests pass
4. **Providing hooks** - Optional callbacks for customization at each step

Users only need to define their image, compose stack, and pipeline configuration. The plugin handles the rest.
