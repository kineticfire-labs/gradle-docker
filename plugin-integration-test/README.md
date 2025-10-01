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
- `plugin-integration-test/compose/*`
   - mid-level aggregator for 'docker compose' type commands for testing a Docker image using `composeUp`/`composeDown` 
     for the `dockerOrch` task

## Integration Test Structure

```
plugin-integration-test/    # all integration tests:  top-level aggregator
├── app/                    # Sample Java application (builds JAR for Docker images)
├── compose/                # Docker Compose integration tests for `dockerOrch` task: mid-level aggregator
│   └── scenario-1/         # individual integration test '1'
│   └── scenario-n/         # individual integration test 'n'
├── docker/                 # Docker integration tests for `docker` task: mid-level aggregator
│   └── scenario-1/         # individual integration test '1'
│   └── scenario-n/         # individual integration test 'n'
└── buildSrc/               # Reusable Docker testing library
```

## Task Organization

**Task Names:**
- Integration tests are identified by task name `integrationTest` (not by group)
- Each subproject has its own `integrationTest` task
- The root `integrationTest` depends on all subproject integration tests

**Task Groups:**
- `verification` group contains Docker verification tasks (`cleanDockerImages`, `verifyDockerImages`)
- `build` group contains cleanup tasks (`cleanAll`)

## Running Specific Tests

```bash
# Run specific Docker project integration test
#   - set 'version' to the plugin version such as '1.0.0'
./gradlew -Pplugin_version=<version> :docker:scenario-1:integrationTest

# Run with clean for that project only
#   - set 'version' to the plugin version such as '1.0.0'
./gradlew -Pplugin_version=<version> :docker:scenario-1:clean :docker:scenario-1:integrationTest

# Run all integration tests (from any directory in project)
#   - set 'version' to the plugin version such as '1.0.0'
./gradlew -Pplugin_version=<version> cleanAll integrationTest
```

## Requirements

- **Docker**: Must be installed and daemon running
- **Java 21+**: Required for building sample application
- **Gradle 9**: Uses configuration cache and Provider API
- **gradle-docker plugin**: Must be published to Maven local (`./gradlew clean publishToMavenLocal` from `../plugin/`)

## De-conflict Integration Tests

Integration tests run in parallel.  Shared resources must be manually deconflicted:
- Docker images must have unique names per integration test, such as `scenario-<number>-<image name>` or 
  `scenario-<number>/<image name>`
- Ports must be unique, such as those used for exposed services (e.g., the `TimeServer`) or for Docker private 
  registries

## Troubleshooting

**"Docker command not found"**
- Ensure Docker is installed and `docker --version` works

**"Image not found" errors**  
- Run `cleanAll` first to ensure clean state
- Check `docker images` to see what images exist

**Plugin not found errors**
- Build and publish plugin first: `cd ../plugin && ./gradlew clean publishToMavenLocal`