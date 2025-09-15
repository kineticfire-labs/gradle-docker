# Plugin Integration Tests

Integration tests for the gradle-docker plugin that verify real Docker functionality using actual Docker commands and images.

## Quick Start

To correctly run all integration tests:

```bash
./gradlew cleanAll integrationTest
```

**Why both commands?**
- `cleanAll` ensures Docker images are removed and all projects are cleaned
- `integrationTest` runs the actual tests  
- Running `cleanAll` first prevents Gradle from marking tasks as up-to-date when they should re-run

## What Gets Tested

The `integrationTest` task runs integration tests from all Docker subprojects:

**Current Test Projects:**
- `:docker:images-a` - Tests Docker image build, tag, and verification workflows

**How Integration Tests Work:**
1. **Clean**: Remove existing Docker images to ensure clean test environment
2. **Build**: Build Docker images using the gradle-docker plugin  
3. **Verify**: Confirm images were created with expected names and tags

## Integration Test Structure

```
plugin-integration-test/
├── app/                    # Sample Java application (builds JAR for Docker images)
├── docker/                 # Docker integration tests
│   └── images-a/          # Image build and verification tests
└── buildSrc/              # Reusable Docker testing library
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
./gradlew :docker:images-a:integrationTest

# Run with clean for that project only
./gradlew :docker:images-a:clean :docker:images-a:integrationTest

# Run all integration tests (from any directory in project)
./gradlew cleanAll integrationTest
```

## Requirements

- **Docker**: Must be installed and daemon running
- **Java 21+**: Required for building sample application
- **Gradle 9**: Uses configuration cache and Provider API
- **gradle-docker plugin**: Must be published to Maven local (`./gradlew publishToMavenLocal` from `../plugin/`)

## Troubleshooting

**"Docker command not found"**
- Ensure Docker is installed and `docker --version` works

**"Image not found" errors**  
- Run `cleanAll` first to ensure clean state
- Check `docker images` to see what images exist

**Plugin not found errors**
- Build and publish plugin first: `cd ../plugin && ./gradlew publishToMavenLocal`