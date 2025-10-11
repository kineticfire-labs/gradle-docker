# Plugin Integration Tests

Integration tests for the gradle-docker plugin that verify real Docker functionality using actual Docker commands and 
images.

## Quick Start

To correctly run all integration tests:

```bash
# NOTES:
#   - set 'version' to the plugin version such as '1.0.0'
#   - if changes were made to plugin source, then it must be re-built and published to maven local using 
#     `./gradlew -Pplugin_version=<version> clean build publishToMavenLocal` in the `plugin/` directory 
./gradlew -Pplugin_version=<version> cleanAll integrationTest
```

**Why both commands?**
- `cleanAll` ensures Docker images are removed and all projects are cleaned
- `integrationTest` runs the actual tests  
- Running `cleanAll` first prevents Gradle from marking tasks as up-to-date when they should re-run

## What Gets Tested

The `integrationTest` task runs integration tests from all subprojects:
- `plugin-integration-test/docker/*`
   - mid-level aggregator for 'docker' type commands (build, tag, save, publish) for the `docker` task 
- `plugin-integration-test/dockerOrch/*`
   - mid-level aggregator for 'docker compose' type commands for testing a Docker image using `composeUp`/`composeDown` 
     for the `dockerOrch` task

## Integration Test Structure

```
plugin-integration-test/    # all integration tests:  top-level aggregator
├── app/                    # Sample Java application (builds JAR for Docker images)
├── dockerOrch/             # Docker Compose integration tests for `dockerOrch` task: mid-level aggregator
│   ├── verification/       # Plugin mechanics validation (for developers)
│   │   ├── basic/          # Basic up/down, state files, cleanup
│   │   ├── wait-healthy/   # Wait for healthy functionality
│   │   ├── wait-running/   # Wait for running functionality
│   │   └── ...             # Additional verification scenarios
│   └── examples/           # User-facing demonstrations (for users)
│       ├── web-app/        # Simple REST API testing
│       ├── database-app/   # App with PostgreSQL
│       └── ...             # Additional example scenarios
├── docker/                 # Docker integration tests for `docker` task: mid-level aggregator
│   └── scenario-1/         # individual integration test '1'
│   └── scenario-n/         # individual integration test 'n'
└── buildSrc/               # Reusable Docker testing library
```

## dockerOrch Test Organization

The `dockerOrch/` tests are organized into two distinct categories:

### Verification Tests (`dockerOrch/verification/`)

**Purpose**: Validate plugin mechanics (for plugin developers)

These tests use internal validators from `buildSrc/` to verify the plugin infrastructure works correctly. They test
features that users typically wouldn't test directly.

**⚠️ Important**: Do NOT copy these tests for your own projects. See `dockerOrch/examples/` for user-facing
demonstrations.

| Scenario | Location | Status | Plugin Features Tested |
|----------|----------|--------|------------------------|
| Basic | `verification/basic/` | ✅ Complete | composeUp, composeDown, state files, port mapping, cleanup |
| Wait Healthy | `verification/wait-healthy/` | ⏳ Planned | waitForHealthy, health check timing, timeout handling |
| Wait Running | `verification/wait-running/` | ⏳ Planned | waitForRunning, running state detection |
| Mixed Wait | `verification/mixed-wait/` | ⏳ Planned | Both wait types together (app + database) |
| Lifecycle Suite | `verification/lifecycle-suite/` | ⏳ Planned | Class-level lifecycle (suite), setupSpec/cleanupSpec |
| Lifecycle Test | `verification/lifecycle-test/` | ⏳ Planned | Method-level lifecycle (test), setup/cleanup |
| Logs Capture | `verification/logs-capture/` | ⏳ Planned | Log capture configuration, file generation |
| Multi Service | `verification/multi-service/` | ⏳ Planned | Complex orchestration (3+ services) |
| Existing Images | `verification/existing-images/` | ⏳ Planned | Public images (nginx, redis), sourceRef pattern |

### Example Tests (`dockerOrch/examples/`)

**Purpose**: Demonstrate real-world usage (for plugin users)

These tests show how real users would test their applications using standard testing libraries. Each example serves as
living documentation and a copy-paste template.

**✅ Recommended**: Copy and adapt these for your own projects!

| Example | Location | Status | Use Case | Testing Libraries |
|---------|----------|--------|----------|-------------------|
| Web App | `examples/web-app/` | ✅ Complete | REST API testing | RestAssured, HTTP client |
| Database App | `examples/database-app/` | ⏳ Planned | Database integration | JDBC, JPA, Spring Data |
| Microservices | `examples/microservices/` | ⏳ Planned | Service orchestration | RestAssured, service discovery |
| Kafka App | `examples/kafka-app/` | ⏳ Planned | Event-driven architecture | Kafka client, TestProducer/Consumer |
| Batch Job | `examples/batch-job/` | ⏳ Planned | Scheduled processing | Spring Batch, JDBC |

## Task Organization

**Task Names:**
- Integration tests are identified by task name `integrationTest` (not by group)
- Each subproject has its own `integrationTest` task
- The root `integrationTest` depends on all subproject integration tests

**Task Groups:**
- `verification` group contains Docker verification tasks (`cleanDockerImages`, `verifyDockerImages`)
- `build` group contains cleanup tasks (`cleanAll`)

## Integration Test Execution Requirements

**⚠️ IMPORTANT: All integration tests must be run from this top-level directory (`plugin-integration-test/`).**

**Why this requirement?**
- Integration tests use shared functionality from `buildSrc/` directory
- Gradle's buildSrc mechanism only works within the project where it's defined
- This ensures single source of truth for all Docker testing functionality
- Prevents code duplication and maintains consistency

## Running Integration Tests

**All commands must be run from `/plugin-integration-test/` directory:**

```bash
# Run all integration tests
./gradlew -Pplugin_version=<version> cleanAll integrationTest

# Run all Docker integration tests
./gradlew -Pplugin_version=<version> docker:integrationTest

# Run specific Docker scenario
./gradlew -Pplugin_version=<version> docker:scenario-1:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-2:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-3:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-4:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-5:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-6:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-7:integrationTest

# Run with clean for specific scenario
./gradlew -Pplugin_version=<version> docker:scenario-1:clean docker:scenario-1:integrationTest

# Run all Docker tests with clean
./gradlew -Pplugin_version=<version> docker:cleanAll docker:integrationTest
```

**❌ What will NOT work:**
```bash
# These will fail with helpful error messages
cd docker && ./gradlew scenario-1:integrationTest
cd docker/scenario-1 && ./gradlew integrationTest
```

### Run Integration Tests That Publish to Public Image Repositories

Integration tests that publish to public image repositories do not run with the group of basic integration tests and
must be specifically selected.  These often require additional configuration, namely credentials.

Run integration tests for publish to Docker Hub:
```bash
# Set your credentials
export DOCKERHUB_USERNAME='your-username'
export DOCKERHUB_TOKEN='your-pat-token'

# Run the integration test (replace 1.0.0 with your plugin version)
./gradlew -Pplugin_version=1.0.0 docker:scenario-99:dockerHubIntegrationTest -PenableDockerHubTests=true
```

## Requirements

- **Docker**: Must be installed and daemon running
- **Java 21+**: Required for building sample application
- **Gradle 9**: Uses configuration cache and Provider API
- **gradle-docker plugin**: Must be published to Maven local (`./gradlew clean publishToMavenLocal` from `../plugin/`)

## De-conflict Integration Tests

Integration tests run in parallel.  Shared resources must be manually de-conflicted according to the following sections.

### Image Names - De-conflict
Docker images must have unique names per integration test, such as `scenario-<number>-<image name>` or 
`scenario-<number>/<image name>`.

#### Public Image Names & Tags - De-conflict

When using public images (e.g., those from Docker Hub), integration tests must use:
- tags like `latest` or `edge`, as available, instead of `1.27` because the numeric version could be aged-off and no
  longer available
- do NOT use images from Bitnami, VMware, or Broadcom as public images could be temporarily or permanently made 
  unavailable as evidenced by the
  [Bitnami image brownout and shift to paid images](https://github.com/bitnami/containers/issues/83267) 
- unique registry:repository:image:tag combinations per integration test such as one test uses `alpine:latest` and 
  another test uses `haproxy:latest`.  Note that DockerHub images do not have an explicit registry. These must be 
  recorded in the table below to track.

| Registry | Repository | Image Name   | Image Tag | `docker` or `dockerOrch` | Integration Test |
|----------|------------|--------------|-----------|--------------------------|------------------|
|          |            | httpd        | latest    | `docker`                 | scenario-5       |
|          |            | nginx        | latest    | `docker`                 | scenario-9       |
|          |            | alpine       | latest    | `docker`                 | scenario-10      |
|          |            | php          | latest    | `docker`                 | scenario-11      |
|          |            | haproxy      | latest    | `docker`                 | scenario-12      |
|          | apache     | kafka-native | latest    | `docker`                 | scenario-13      |



### Ports - De-conflict

Ports must be unique, such as those used for exposed services (e.g., the `TimeServer`) or for Docker private 
registries.

#### Registry Ports - De-conflict

Registry ports must be chosen from the following convention to ensure uniqueness:
- `docker` registry integration tests: use registry ports `5ssr` where `ss` indicates the integration test scenario  
  number and the `r` is used and allocated by the test itself, usually starting at 0.
  - Example:
     - For `scenario-5`: ports `5050` and `5051`
     - For `scenario-12`: ports `5120` and `5121`
  - Ports `6xxx` are reserved for future `docker` registry integration tests, if needed
- `dockerOrch` registry  integration tests: use registry ports `7ssr` where `ss` indicates the integration test scenario
  number and the `r` is used and allocated by the test itself, usually starting at 0.
  - Example:
    - For `scenario-5`: ports `7050` and `7051`
    - For `scenario-12`: ports `7120` and `7121`
  - Ports `8xxx` are reserved for future `dockerOrch` registry integration tests, if needed

#### Server/Service Ports - De-conflict

Ports `9ssp` are reserved for servers/services during integration tests for `dockerOrch` (`docker` does not spin-up
servers/services, aside from registries, as part of its tests).
- `dockerOrch` integration tests using a server/service: use ports `9ssp` where `ss` indicates the integration test 
  scenario number and the `p` is used and allocated by the test itself, usually starting at 0.
  - Example:
    - For `scenario-5`: ports `9050` and `9051`
    - For `scenario-12`: ports `9120` and `9121`


## Troubleshooting

**"Docker command not found"**
- Ensure Docker is installed and `docker --version` works

**"Image not found" errors**  
- Run `cleanAll` first to ensure clean state
- Check `docker images` to see what images exist

**Plugin not found errors**
- Build and publish plugin first: `cd ../plugin && ./gradlew clean publishToMavenLocal`