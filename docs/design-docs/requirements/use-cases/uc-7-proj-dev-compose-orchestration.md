# Use Case - 7 - Project Developer Compose Orchestration

This document describes Gradle tasks and functionality related to image testing.  Tests here are integration tests 
only-- there are no unit tests or functional tests.

## Document Metadata

| Key     | Value       |
|---------|-------------|
| Status  | Implemented |
| Version | 1.1.0       |
| Updated | 2025-11-20  |

## Implementation Status

✅ **All phases are now fully implemented:**

- **Phase 1**: Core Compose Operations - COMPLETE
- **Phase 2**: Health Checking & Testing Integration - COMPLETE
- **Phase 3**: Advanced Orchestration (CLASS/METHOD lifecycles) - COMPLETE

**Key Features Implemented:**
- `dockerOrch.composeStacks { }` DSL for defining compose stacks
- `usesCompose(stack: "name", lifecycle: "class|method")` for passing configuration to test tasks
- Spock extension (`@ComposeUp`) with zero-parameter annotation support
- JUnit 5 extensions (`@ExtendWith(DockerComposeClassExtension.class)` and `DockerComposeMethodExtension.class`)
- Automatic state file generation with service ports and container info
- Health checking with configurable timeouts
- Integration test source set convention (automatic setup)

See [usage-docker-orch.md](../../../usage/usage-docker-orch.md) for complete usage documentation.

## Definition

**Actor**: Project Developer

**Goal**: Orchestrate Docker containers using Docker Compose for testing

**Preconditions**: `gradle-docker` plugin applied to the build, Docker CLI installed, Docker Compose v2 installed, 
Docker daemon available, can run Docker and Docker Compose commands without `sudo`, yaml file(s) for Docker Compose 
defines services

**Post conditions**: Docker Compose services started and stopped

**Steps by Actor to achieve goal**:
1. Project Developer creates a `build.gradle` file
1. Project Developer applies the `gradle-docker` plugin in their `build.gradle` file
1. Project Developer invokes the task (using the command line or triggered by another task) to start containers, defined
by a yaml file(s), using Docker Compose
1. Project Developer invokes the task (using the command line or triggered by another task) uses the Docker 
containers/services
1. Project Developer invokes the task (using the command line or triggered by another task) to stop the containers

**Derived functional requirements**: fr-23, fr-24, fr-25, fr-26, fr-27, fr-28, fr-29, fr-30, fr-31, fr-32

**Derived non-functional requirements**: nfr-23, nfr-24, nfr-25, nfr-26, nfr-31  

## Assumptions

The plugin views a project with a goal of building a software application as a Docker image as having two subprojects:
one to build the application and one to build the Docker image:

```
the-application-project/
├─ app/                                   # builds the JAR (or other artifact)
│  ├─ build.gradle
│  └─ src/
│     ├─ main/java/...
│     └─ test/java/...
└─ app-image/                              # builds the Docker image + tests it
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

In this scenario, the `app/` directory contains the source and build file to build the application, such as a JAR file.

The `app-image/` directory then assembles the application into a Docker image and tests it.  This applies Separation of 
Concerns (SoC) principle, given the amount of functionality is being performed in the process of building the image,
particularly with integration tests.  Within the `app-image/` directory, tests are located at:
- `app-image/src/integrationTest/`: contains integration tests

## Implementation Phases

The Docker Compose orchestration functionality will be implemented in phases:

### Phase 1: Core Compose Operations
- Basic `composeUp`/`composeDown` tasks
- Stack definitions with file paths and project names
- Wait for services in `running` state with timeout
- Simple logging capture

### Phase 2: Health Checking & Testing Integration  
- Wait for `healthy` service state
- JSON state file generation for service connectivity info
- Test task integration with `usesCompose`
- Basic lifecycle management (suite/class level)

### Phase 3: Advanced Orchestration
- Method-level lifecycle management
- Complex waiting strategies (log pattern matching)
- Advanced polling configurations
- Enhanced error handling and retries

## Service State Management

**Supported Service States**:
- **`running`**: Container is running but may not be ready for connections
- **`healthy`**: Container passes health checks and is ready for use (preferred for testing)

**Timeout Behavior**:
- Each wait operation has a configurable `timeoutSeconds`
- On timeout, tasks fail fast with clear error messages indicating which services failed to reach the desired state
- Optional `pollSeconds` configuration for checking intervals (default: 2 seconds)

## JSON State File Format

The plugin generates a JSON state file containing service connectivity information:

```json
{
  "stackName": "dbOnly",
  "services": {
    "db": {
      "containerId": "abc123def456",
      "containerName": "db-myproject-1",
      "state": "healthy",
      "publishedPorts": [
        {"container": 5432, "host": 54321, "protocol": "tcp"}
      ]
    },
    "api": {
      "containerId": "def456ghi789", 
      "containerName": "api-myproject-1",
      "state": "running",
      "publishedPorts": [
        {"container": 8080, "host": 58080, "protocol": "tcp"}
      ]
    }
  },
  "composeProject": "db-myproject",
  "timestamp": "2025-08-31T10:30:00Z"
}
```

This file is written after successful `composeUp` and passed to test tasks via system properties.

## Concept Groovy DSL Using the Plugin's Docker Tasks

### Complete Gradle Groovy DSL example

```groovy
plugins {
  id "groovy"
  id "java" // for integration tests in Java
  id "com.kineticfire.gradle.docker" version "0.1.0"
}

repositories { mavenCentral() }

dependencies {
  // Integration (Java/JUnit)
  integrationTestImplementation "org.junit.jupiter:junit-jupiter:5.10.2"
}

sourceSets {
  integrationTest {
    java.srcDir "src/integrationTest/java"
    resources.srcDir "src/integrationTest/resources"
    compileClasspath += sourceSets.main.output + configurations.testRuntimeClasspath
    runtimeClasspath += output + compileClasspath
  }
}

configurations {
  integrationTestImplementation.extendsFrom testImplementation
  integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
}

// --- Define multiple compose stacks once ---
dockerOrch {
  composeStacks {
    stack("dbOnly") {
      files       = [file("compose-db.yml")]
      envFiles    = [file(".env")]
      projectName = "db-${project.name}"
      waitForRunning {
        services       = ["another"]
        timeoutSeconds = 60
      } 
      waitForHealthy {
        services       = ["db"]
        timeoutSeconds = 60
      }
      logs {
        writeTo   = file("$buildDir/compose/dbOnly-logs")
        tailLines = 200
      }
    }
    stack("apiDb") {
      files       = [file("compose-db.yml"), file("compose-api.yml")]
      projectName = "api-${project.name}"
      waitForHealthy {
        services       = ["db","api"]
        timeoutSeconds = 90
        pollSeconds    = 2
        successWhenLogsMatch = [ "api": "Started .* in .* seconds" ]  # Phase 3 feature
      }
      logs { writeTo = file("$buildDir/compose/apiDb-logs") }
    }
  }
}

// --- Classic Test tasks bound to stacks ---

tasks.register("integrationTest", Test) {
  description = "Java integration tests against api+db stack"
  testClassesDirs = sourceSets.integrationTest.output.classesDirs
  classpath       = sourceSets.integrationTest.runtimeClasspath
  useJUnitPlatform()
  usesCompose stack: "apiDb", lifecycle: "suite"    // or "method" if you need full isolation (Phase 3)
  systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("apiDb")
}
```

#### Explaining Items from the Example DSL

##### What does the 'lifecycle' mean?

What does lifecycle: "method" mean?
- "suite": one compose up before the Test task starts; one compose down after it finishes. Fastest.
- "class": up/down around each test class. Good isolation with moderate cost.
- "method": up/down around each test method. Maximum isolation, slowest.

The plugin would wrap per-method via a TestListener (beforeTest/afterTest). Typically also suffixes the Compose project 
name with a unique id (class#method hash) to avoid collisions. Use "method" only when you truly need a pristine 
environment per test method.


##### “Where and what are the tests?”

Tests are normal source files in the project `src/integrationTest/java`).   The Gradle DSL just wires which Test task runs 
which source set and how it’s composed.

Each Test task points at a source set (or patterns) and the plugin adds the compose lifecycle.


##### What does 'systemProperty' and 'composeStateFileFor' do?

Reference the line `systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("dbOnly")`.

The plugin’s `composeUp` writes a connection info JSON (e.g., service → ports, container reference (ID, 
reference (name, ID, etc.), etc.)).

`composeStateFileFor("dbOnly")` (helper from the plugin) resolves the path to that JSON for the dbOnly stack.

`systemProperty(...)` injects the path into the test JVM as `System.getProperty("COMPOSE_STATE_FILE")`.

Tests read it to build URLs/connection strings. (Examples below.)


### Minimal test code to consume COMPOSE_STATE_FILE

Example functional tests using Groovy + Spock.

```groovy
import groovy.json.JsonSlurper
import spock.lang.*

class ApiHealthSpec extends Specification {

  @Shared Map state

  def setupSpec() {
    def path = System.getProperty("COMPOSE_STATE_FILE")
    assert path : "COMPOSE_STATE_FILE missing"
    state = new JsonSlurper().parse(new File(path)) as Map
  }

  def "db is reachable"() {
    given:
    def db = state.services.db
    // resolve host/port from your JSON shape; example:
    def port = db.publishedPorts.find { it.container == 5432 }?.host ?: 5432

    expect:
    // ping DB, e.g., via JDBC or simple socket
    new Socket("localhost", port).withCloseable { true }
  }
}
```

### Java + JUnit 5 (integration tests)

Example integration tests using Java + JUnit 5.

```groovy
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;

import java.nio.file.*;

class ApiIT {
  static JsonNode state;

  @BeforeAll
  static void load() throws Exception {
    var path = System.getProperty("COMPOSE_STATE_FILE");
    Assertions.assertNotNull(path, "COMPOSE_STATE_FILE missing");
    state = new ObjectMapper().readTree(Files.readString(Path.of(path)));
  }

  @Test
  void apiResponds() throws Exception {
    var port = state.at("/services/api/publishedPorts/0/host").asInt(8080);
    var url  = new java.net.URL("http://localhost:" + port + "/health");
    try (var in = url.openStream()) {
      Assertions.assertTrue(in.read() >= 0);
    }
  }
}
```
