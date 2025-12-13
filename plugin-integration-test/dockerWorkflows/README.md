# dockerWorkflows Integration Tests

This directory contains integration tests for the `dockerWorkflows` DSL extension. These tests validate complete
build-test-publish pipelines using real Docker operations.

## Overview

The `dockerWorkflows` DSL provides a high-level orchestration layer that combines:
- **docker** DSL: Building and tagging Docker images
- **dockerTest** DSL: Managing Docker Compose stacks for testing
- **Conditional logic**: Executing different operations based on test results

## Test Scenarios

| Scenario | Directory | Purpose | Verification Task |
|----------|-----------|---------|-------------------|
| 1 | `scenario-1-basic` | Basic workflow (build, test, tag on success) | `integrationTest` |
| 2 | `scenario-2-delegated-lifecycle` | Workflow with delegated compose lifecycle | `integrationTest` |
| 3 | `scenario-3-failed-tests` | Failure path verification (intentionally failing tests) | `verifyFailedPipeline` |
| 4 | `scenario-4-multiple-pipelines` | Multiple pipelines (dev, staging, prod) with different tags | `verifyMultiplePipelines` |
| 5 | `scenario-5-complex-success` | Complex success operations (multiple tags) | `verifyComplexSuccess` |
| 6 | `scenario-6-hooks` | All hook types (beforeBuild, afterBuild, beforeTest, afterTest, afterSuccess) | `verifyHooks` |
| 7 | `scenario-7-save-publish` | Save and publish DSL operations (save image to tar.gz) | `verifySavePublish` |
| 8 | `scenario-8-method-lifecycle` | Method lifecycle (fresh containers per test method) | `verifyMethodLifecycle` |

## Port Allocations

Each scenario uses a dedicated port to avoid conflicts when running tests in parallel:

| Scenario | Port |
|----------|------|
| scenario-1-basic | 9200 |
| scenario-2-delegated-lifecycle | 9201 |
| scenario-3-failed-tests | 9202 |
| scenario-4-multiple-pipelines | 9203 |
| scenario-5-complex-success | 9204 |
| scenario-6-hooks | 9205 |
| scenario-7-save-publish | 9206 |
| scenario-8-method-lifecycle | 9207 |

## Running the Tests

### Run All Workflow Tests

```bash
cd plugin-integration-test
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:integrationTest
```

### Run a Single Scenario

```bash
# Scenario 1: Basic workflow
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-1-basic:app-image:integrationTest

# Scenario 3: Failed tests verification
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-3-failed-tests:app-image:verifyFailedPipeline

# Scenario 4: Multiple pipelines verification
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-4-multiple-pipelines:app-image:verifyMultiplePipelines

# Scenario 5: Complex success operations
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-5-complex-success:app-image:verifyComplexSuccess

# Scenario 6: Hooks verification
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-6-hooks:app-image:verifyHooks

# Scenario 7: Save/Publish verification
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-7-save-publish:app-image:verifySavePublish

# Scenario 8: Method lifecycle verification
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-8-method-lifecycle:app-image:verifyMethodLifecycle
```

### Cleanup Docker Resources

```bash
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:cleanDockerImages
```

## Scenario Details

### Scenario 1: Basic Workflow

Demonstrates the simplest workflow configuration:
- Build a Docker image from Dockerfile
- Run integration tests against containerized app
- Apply 'tested' tag on success
- Automatic cleanup via composeDown

**DSL Example:**
```groovy
dockerWorkflows {
    pipelines {
        basicPipeline {
            build {
                image = docker.images.myApp
            }
            test {
                stack = dockerTest.composeStacks.myTest
                testTaskName = 'integrationTest'
            }
            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

### Scenario 2: Delegated Lifecycle

Tests the workflow lifecycle management where the pipeline handles compose up/down:
- Pipeline manages Docker Compose lifecycle
- Tests run between composeUp and composeDown
- Supports testIntegration lifecycle annotation patterns

### Scenario 3: Failed Tests

Verifies failure path behavior:
- Tests intentionally fail (404 expected vs 200 returned)
- Validates that failure path is triggered
- Confirms cleanup runs even after test failures
- Uses wrapper task (`verifyFailedPipeline`) to validate failure behavior

### Scenario 4: Multiple Pipelines

Demonstrates multiple pipelines with different configurations:
- **devPipeline**: Build and test only (no additional tags)
- **stagingPipeline**: Build, test, apply 'staging' tag on success
- **prodPipeline**: Build, test, apply 'prod' and 'release' tags on success

**Key Verification:**
- Each pipeline applies the correct Docker tags
- Tags are visible via `docker images` command

### Scenario 5: Complex Success Operations

Validates multiple success operations:
- Apply multiple additional tags ('verified', 'stable') on test success
- Complete pipeline orchestration (build, test, conditional, cleanup)
- No lingering containers after test

### Scenario 6: Hooks and Customization

Tests all available hook types:
- **beforeBuild**: Executes before Docker build starts
- **afterBuild**: Executes after Docker build completes
- **beforeTest**: Executes before tests run (after composeUp)
- **afterTest**: Executes after tests complete (receives TestResult)
- **afterSuccess**: Executes after successful test completion

**Verification Method:**
Hooks create marker files in `build/hook-markers/` directory:
- `beforeBuild.marker`
- `afterBuild.marker`
- `beforeTest.marker`
- `afterTest.marker` (includes TestResult info)
- `afterSuccess.marker`

### Scenario 7: Save and Publish Operations

Demonstrates the `save { }` and `publish { }` DSL blocks in `onTestSuccess`:
- Build the Docker image
- Run integration tests against containerized app
- On success: Apply 'tested' tag and save image to compressed tar file

**DSL Example:**
```groovy
dockerWorkflows {
    pipelines {
        savePublishPipeline {
            build {
                image = docker.images.myApp
            }
            test {
                stack = dockerTest.composeStacks.myTest
                testTaskName = 'integrationTest'
            }
            onTestSuccess {
                additionalTags = ['tested']

                // Save to compressed tar file
                save {
                    outputFile.set(file('build/images/my-image.tar.gz'))
                    compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
                }

                // Publish to registry (optional)
                publish {
                    publishTags.set(['tested', 'release'])
                    to('staging') {
                        registry.set('registry.example.com')
                        namespace.set('myproject')
                    }
                }
            }
        }
    }
}
```

**Key Verification:**
- Image saved to specified output file with GZIP compression
- Saved file is a valid gzip archive
- 'tested' tag applied to image

### Scenario 8: Method Lifecycle

Demonstrates the `lifecycle = WorkflowLifecycle.METHOD` configuration where containers are started
fresh before each test method and stopped after each test method:
- Pipeline sets `lifecycle = WorkflowLifecycle.METHOD` in the test step
- Pipeline sets system properties for the test framework to detect
- Test class uses `@ComposeUp` annotation (Spock) which reads the system properties
- Each test method gets fresh containers (no state persists between tests)
- Pipeline still orchestrates build → test → conditional tag operations

**DSL Example:**
```groovy
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

dockerWorkflows {
    pipelines {
        methodLifecyclePipeline {
            build {
                image = docker.images.myApp
            }
            test {
                stack = dockerTest.composeStacks.myTest
                testTaskName = 'integrationTest'
                lifecycle = WorkflowLifecycle.METHOD  // Fresh containers per test method
            }
            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

**Test Class Requirement:**
When using `lifecycle = METHOD`, the test class **MUST** have the `@ComposeUp` annotation:
```groovy
@ComposeUp  // Reads system properties set by the pipeline
class MyIntegrationTest extends Specification {
    def "test method 1"() { /* gets fresh containers */ }
    def "test method 2"() { /* gets fresh containers */ }
}
```

**Key Verification:**
- Each test method gets a different container (verified by different start times)
- Request counts reset between tests (proving fresh state)
- Pipeline's conditional tagging still works after all tests pass

## Project Structure

```
dockerWorkflows/
├── build.gradle                    # Aggregator for all scenarios
├── README.md                       # This file
├── scenario-1-basic/
│   └── app-image/
│       ├── build.gradle            # Pipeline configuration
│       └── src/
│           ├── main/docker/        # Dockerfile
│           └── integrationTest/
│               ├── groovy/         # Test classes
│               └── resources/compose/  # Docker Compose files
├── scenario-2-delegated-lifecycle/
│   └── ...
├── scenario-3-failed-tests/
│   └── ...
├── scenario-4-multiple-pipelines/
│   └── ...
├── scenario-5-complex-success/
│   └── ...
├── scenario-6-hooks/
│   └── ...
├── scenario-7-save-publish/
│   └── ...
└── scenario-8-method-lifecycle/
    └── ...
```

## Image Naming Convention

Each scenario uses a unique image name pattern:
- `workflow-scenario1-app`
- `workflow-scenario2-app`
- `workflow-scenario3-app`
- `workflow-scenario4-app`
- `workflow-scenario5-app`
- `workflow-scenario6-app`
- `workflow-scenario7-app`
- `workflow-scenario8-app`

## Expected Behavior

### Successful Workflow

1. **Build Phase**: Docker image is built and tagged with base tags (e.g., `latest`, `1.0.0`)
2. **Test Phase**:
   - Docker Compose stack is started (composeUp)
   - Integration tests run against containerized app
   - Compose stack is stopped (composeDown)
3. **Success Phase** (if tests pass):
   - Additional tags are applied (e.g., `tested`, `staging`, `prod`)
   - Success hooks execute
4. **Cleanup Phase**: Always runs regardless of success/failure

### Failed Workflow

1. **Build Phase**: Same as successful workflow
2. **Test Phase**: Same as successful workflow, but tests fail
3. **Failure Phase**:
   - Failure-specific operations execute
   - Failure hooks execute (if configured)
4. **Cleanup Phase**: Always runs regardless of success/failure

## Troubleshooting

### Container Cleanup Issues

If containers are not cleaned up properly:
```bash
docker compose -p workflow-scenario1-test down -v
docker compose -p workflow-scenario2-test down -v
# ... etc
```

### Image Cleanup

Remove all workflow test images:
```bash
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:cleanDockerImages
```

### Port Conflicts

If you encounter port conflicts, check for running containers:
```bash
docker ps -a --filter 'name=workflow-scenario'
```

### Viewing Pipeline Logs

Run with `--info` for detailed pipeline execution logs:
```bash
./gradlew -Pplugin_version=1.0.0 :dockerWorkflows:scenario-1-basic:app-image:integrationTest --info
```

## Related Documentation

- [dockerWorkflows Usage Guide](../../docs/usage/usage-docker-workflows.md) (pending)
- [docker DSL Usage](../../docs/usage/usage-docker.md)
- [dockerTest DSL Usage](../../docs/usage/usage-docker-orch.md)
