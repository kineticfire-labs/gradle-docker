# Enhancement Plan: Complete `dockerOrch` Task Implementation

**Date Created**: 2025-10-10
**Author**: Engineering Team
**Status**: In Progress
**Estimated Effort**: 6-9 days (47-68 hours)

## Executive Summary

The `dockerOrch` implementation is approximately 60-70% complete. The core infrastructure is sound and well-designed,
but critical integration points are missing, and there are NO integration tests to validate functionality.

**Current State**: Not production-ready
**Required Work**: Complete wait/logs integration, add state file generation for suite lifecycle, create comprehensive
integration tests

## Current Implementation Assessment

### What Works (40% Complete)
✅ **Basic orchestration**: Can start and stop compose stacks via Gradle tasks
✅ **DSL configuration**: `dockerOrch { }` DSL properly captures user intent
✅ **Multi-file compose support**: Handles multiple compose files and env files
✅ **JUnit lifecycle extensions**: Class and method extensions work for direct JUnit integration

### What's Missing (60% Incomplete)
❌ **Wait functionality NOT integrated** in tasks:
   - `WaitSpec` and `WaitConfig` exist but `ComposeUpTask` doesn't call `composeService.waitForServices()`
   - Suite lifecycle has no mechanism to wait for containers to be ready
   - Cannot wait for mixed container states (some HEALTHY, some RUNNING)

❌ **Logs functionality NOT integrated**:
   - `LogsSpec` and `LogsConfig` exist but no task calls `composeService.captureLogs()`
   - No automatic log capture on test completion/failure

❌ **State file generation incomplete**:
   - JUnit extensions generate state files
   - But suite lifecycle (Gradle tasks) does not generate state files
   - Tests expecting state files will fail in suite lifecycle mode

❌ **No validation of container health** in suite lifecycle

❌ **Zero integration tests** to validate end-to-end functionality

## Implementation Plan

### Priority 0: Complete Core Functionality (Est: 2-3 days)

#### Task 1: Integrate Wait Functionality in `ComposeUpTask`

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy`

**Changes Required**:

```groovy
@TaskAction
void composeUp() {
    def projectName = this.projectName.get()
    def stackName = this.stackName.get()

    logger.lifecycle("Starting Docker Compose stack: {} (project: {})", stackName, projectName)

    def composeFilePaths = composeFiles.files.collect { it.toPath() }
    def envFilePaths = envFiles?.files?.collect { it.toPath() } ?: []

    def config = new ComposeConfig(composeFilePaths, envFilePaths, projectName, stackName, [:])

    try {
        // Start the compose stack
        def future = composeService.get().upStack(config)
        def composeState = future.get()

        logger.lifecycle("Successfully started compose stack '{}' with {} services",
            stackName, composeState.services.size())

        composeState.services.each { serviceName, serviceInfo ->
            logger.info("  Service '{}': {} ({})", serviceName,
                serviceInfo.state, serviceInfo.ports.collect { "${it.hostPort}:${it.containerPort}" }.join(', '))
        }

        // ⚠️ ADD THIS: Wait for services to be ready
        performWaitIfConfigured(stackName, projectName)

    } catch (Exception e) {
        throw new RuntimeException("Failed to start compose stack '${stackName}': ${e.message}", e)
    }
}

// ⚠️ ADD THIS METHOD
private void performWaitIfConfigured(String stackName, String projectName) {
    def dockerOrchExt = project.extensions.findByName('dockerOrch')
    if (!dockerOrchExt) {
        return
    }

    def stackSpec = dockerOrchExt.composeStacks.findByName(stackName)
    if (!stackSpec) {
        return
    }

    // Wait for healthy services
    if (stackSpec.waitForHealthy.present) {
        def waitSpec = stackSpec.waitForHealthy.get()
        if (waitSpec.services.present && !waitSpec.services.get().isEmpty()) {
            logger.lifecycle("Waiting for services to be HEALTHY: {}", waitSpec.services.get())

            def waitConfig = new WaitConfig(
                projectName,
                waitSpec.services.get(),
                Duration.ofSeconds(waitSpec.timeoutSeconds.getOrElse(60)),
                Duration.ofSeconds(waitSpec.pollSeconds.getOrElse(2)),
                ServiceStatus.HEALTHY
            )

            def waitFuture = composeService.get().waitForServices(waitConfig)
            waitFuture.get()

            logger.lifecycle("All services are HEALTHY")
        }
    }

    // Wait for running services
    if (stackSpec.waitForRunning.present) {
        def waitSpec = stackSpec.waitForRunning.get()
        if (waitSpec.services.present && !waitSpec.services.get().isEmpty()) {
            logger.lifecycle("Waiting for services to be RUNNING: {}", waitSpec.services.get())

            def waitConfig = new WaitConfig(
                projectName,
                waitSpec.services.get(),
                Duration.ofSeconds(waitSpec.timeoutSeconds.getOrElse(60)),
                Duration.ofSeconds(waitSpec.pollSeconds.getOrElse(2)),
                ServiceStatus.RUNNING
            )

            def waitFuture = composeService.get().waitForServices(waitConfig)
            waitFuture.get()

            logger.lifecycle("All services are RUNNING")
        }
    }
}
```

**Unit Tests Required**:
- Test wait for healthy services only
- Test wait for running services only
- Test wait for mixed (some healthy, some running)
- Test timeout scenario
- Test when no wait configured (no-op)

**Estimated Effort**: 4-6 hours

---

#### Task 2: Integrate Logs Functionality in `ComposeDownTask`

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeDownTask.groovy`

**Changes Required**:

```groovy
@TaskAction
void composeDown() {
    def projectName = this.projectName.get()
    def stackName = this.stackName.get()

    logger.lifecycle("Stopping Docker Compose stack: {} (project: {})", stackName, projectName)

    try {
        // ⚠️ ADD THIS: Capture logs before tearing down
        captureLogsIfConfigured(stackName, projectName)

        // Use compose files if provided, otherwise fall back to project name only
        if (composeFiles?.files?.size() > 0) {
            def composeFilePaths = composeFiles.files.collect { it.toPath() }
            def envFilePaths = envFiles?.files?.collect { it.toPath() } ?: []

            def config = new ComposeConfig(composeFilePaths, envFilePaths, projectName, stackName, [:])

            def future = composeService.get().downStack(config)
            future.get()
        } else {
            def future = composeService.get().downStack(projectName)
            future.get()
        }

        logger.lifecycle("Successfully stopped compose stack '{}'", stackName)

    } catch (Exception e) {
        throw new RuntimeException("Failed to stop compose stack '${stackName}': ${e.message}", e)
    }
}

// ⚠️ ADD THIS METHOD
private void captureLogsIfConfigured(String stackName, String projectName) {
    def dockerOrchExt = project.extensions.findByName('dockerOrch')
    if (!dockerOrchExt) {
        return
    }

    def stackSpec = dockerOrchExt.composeStacks.findByName(stackName)
    if (!stackSpec || !stackSpec.logs.present) {
        return
    }

    def logsSpec = stackSpec.logs.get()

    logger.lifecycle("Capturing logs for stack '{}'", stackName)

    def logsConfig = new LogsConfig(
        logsSpec.services.getOrElse([]),
        logsSpec.follow.getOrElse(false),
        logsSpec.tailLines.getOrElse(100)
    )

    def logsFuture = composeService.get().captureLogs(projectName, logsConfig)
    def logs = logsFuture.get()

    // Write logs to configured location
    if (logsSpec.writeTo.present) {
        def logFile = logsSpec.writeTo.get().asFile
        logFile.parentFile.mkdirs()
        logFile.text = logs
        logger.lifecycle("Logs written to: {}", logFile.absolutePath)
    } else {
        logger.info("Logs:\n{}", logs)
    }
}
```

**Unit Tests Required**:
- Test log capture when configured
- Test log capture with specific services
- Test log capture with tail lines limit
- Test when no logs configured (no-op)
- Test error handling when log capture fails

**Estimated Effort**: 3-4 hours

---

#### Task 3: Generate State Files in Suite Lifecycle

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy`

**Changes Required**:

```groovy
@TaskAction
void composeUp() {
    // ... existing code ...

    // Start the compose stack
    def future = composeService.get().upStack(config)
    def composeState = future.get()

    logger.lifecycle("Successfully started compose stack '{}' with {} services",
        stackName, composeState.services.size())

    // ... existing logging ...

    // Wait for services
    performWaitIfConfigured(stackName, projectName)

    // ⚠️ ADD THIS: Generate state file for test consumption
    generateStateFile(stackName, projectName, composeState)

    // ... rest of method ...
}

// ⚠️ ADD THIS METHOD
private void generateStateFile(String stackName, String projectName, ComposeState composeState) {
    def buildDir = project.layout.buildDirectory.get().asFile
    def stateDir = new File(buildDir, "compose-state")
    stateDir.mkdirs()

    def stateFile = new File(stateDir, "${stackName}-state.json")

    // Build state JSON
    def stateJson = new groovy.json.JsonBuilder()
    stateJson {
        stackName stackName
        projectName projectName
        lifecycle "suite"
        timestamp new java.time.Instant.now().toString()
        services composeState.services.collectEntries { serviceName, serviceInfo ->
            [
                (serviceName): [
                    containerId: serviceInfo.containerId,
                    containerName: serviceInfo.containerName ?: "${serviceName}-${projectName}-1",
                    state: serviceInfo.state,
                    publishedPorts: serviceInfo.ports.collect { port ->
                        [
                            container: port.containerPort,
                            host: port.hostPort,
                            protocol: port.protocol ?: "tcp"
                        ]
                    }
                ]
            ]
        }
    }

    stateFile.text = stateJson.toPrettyString()

    logger.lifecycle("State file generated: {}", stateFile.absolutePath)
}
```

**Unit Tests Required**:
- Test state file generation with single service
- Test state file generation with multiple services
- Test state file contains all expected fields
- Test state file path is correct
- Test state file JSON is valid

**Estimated Effort**: 4-6 hours

---

#### Task 4: Add Unit Tests for New Functionality

**Files**:
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/ComposeUpTaskTest.groovy`
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/ComposeDownTaskTest.groovy`

**Test Coverage Required**:
- All scenarios listed in Tasks 1-3 above
- Error handling paths
- Edge cases (empty configs, null values, etc.)

**Estimated Effort**: 6-8 hours

---

### Priority 1: Create Integration Tests (Est: 3-4 days)

Create integration tests in `/plugin-integration-test/dockerOrch/`:

```
dockerOrch/
├── scenario-1/          # Suite lifecycle - basic up/down
│   ├── build.gradle     # Minimal compose up/down, verify containers start/stop
│   ├── compose.yml      # Single service (nginx)
│   └── src/
│       └── integrationTest/
│           └── groovy/
│               └── BasicComposeIT.groovy
│
├── scenario-2/          # Suite lifecycle - wait for mixed states (HEALTHY + RUNNING)
│   ├── build.gradle     # Configure waitForHealthy and waitForRunning
│   ├── compose.yml      # Multiple services: some with health checks, some without
│   └── src/
│       └── integrationTest/
│           └── groovy/
│               └── MixedWaitStatesIT.groovy
│
├── scenario-3/          # Class lifecycle - JUnit extension
│   ├── build.gradle     # Configure class lifecycle
│   ├── compose.yml      # Single service with health check
│   └── src/
│       └── integrationTest/
│           └── java/
│               └── ClassLifecycleIT.java  # Uses @ExtendWith(DockerComposeClassExtension)
│
├── scenario-4/          # Method lifecycle - JUnit extension
│   ├── build.gradle     # Configure method lifecycle
│   ├── compose.yml      # Single service with health check
│   └── src/
│       └── integrationTest/
│           └── java/
│               └── MethodLifecycleIT.java  # Uses @ExtendWith(DockerComposeMethodExtension)
│
├── scenario-5/          # Suite lifecycle - logs capture
│   ├── build.gradle     # Configure logs capture to file
│   ├── compose.yml      # Service that generates logs
│   └── src/
│       └── integrationTest/
│           └── groovy/
│               └── LogsCaptureIT.groovy
│
└── scenario-6/          # Suite lifecycle - state file consumption
    ├── build.gradle     # Verify state file generation and contents
    ├── compose.yml      # Multiple services with ports
    └── src/
        └── integrationTest/
            └── groovy/
                └── StateFileConsumptionIT.groovy
```

#### Scenario 1: Basic Up/Down
**Purpose**: Verify basic compose stack lifecycle
**Key Tests**:
- Compose up starts containers
- Containers are running (verify with `docker ps`)
- Compose down stops containers
- No lingering containers after down

**Estimated Effort**: 4 hours

---

#### Scenario 2: Mixed Wait States (HEALTHY + RUNNING)
**Purpose**: Verify wait functionality with mixed container states
**Configuration**:
```groovy
dockerOrch {
    composeStacks {
        mixedWait {
            files.from('compose.yml')
            projectName = "mixed-wait-${project.name}"

            // Wait for services with health checks
            waitForHealthy {
                services = ["web-server"]  // Has health check
                timeoutSeconds = 30
            }

            // Wait for services without health checks
            waitForRunning {
                services = ["redis", "worker"]  // No health check
                timeoutSeconds = 20
            }
        }
    }
}
```

**Compose File**:
```yaml
services:
  web-server:
    image: nginx:latest
    ports:
      - "9050:80"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost"]
      interval: 5s
      timeout: 3s
      retries: 3
      start_period: 5s

  redis:
    image: redis:alpine
    # No health check - wait for RUNNING

  worker:
    image: alpine:latest
    command: sh -c "while true; do sleep 1; done"
    # No health check - wait for RUNNING
```

**Key Tests**:
- Verify web-server reaches HEALTHY state
- Verify redis reaches RUNNING state
- Verify worker reaches RUNNING state
- All containers ready before tests execute
- Verify via HTTP request to web-server
- Verify via redis-cli ping to redis
- Timeout if services don't reach expected state

**Estimated Effort**: 6 hours

---

#### Scenario 3: Class Lifecycle
**Purpose**: Verify JUnit class lifecycle integration
**Key Tests**:
- Containers start before all tests
- Multiple tests share same containers
- Containers stop after all tests
- State file accessible to tests
- No container leaks

**Estimated Effort**: 4 hours

---

#### Scenario 4: Method Lifecycle
**Purpose**: Verify JUnit method lifecycle integration
**Key Tests**:
- Fresh containers for each test method
- Complete isolation between tests
- Containers stop after each test
- State file accessible to each test
- No container leaks

**Estimated Effort**: 4 hours

---

#### Scenario 5: Logs Capture
**Purpose**: Verify automatic log capture functionality
**Configuration**:
```groovy
dockerOrch {
    composeStacks {
        logsTest {
            files.from('compose.yml')
            projectName = "logs-test-${project.name}"

            logs {
                writeTo = file("$buildDir/compose-logs/logsTest")
                tailLines = 200
                services = ["app"]  // Capture specific service
            }
        }
    }
}
```

**Key Tests**:
- Logs captured on compose down
- Log file created at specified path
- Log file contains expected content
- Tail lines limit respected
- Service filtering works

**Estimated Effort**: 4 hours

---

#### Scenario 6: State File Consumption
**Purpose**: Verify state file generation and consumption
**Key Tests**:
- State file generated in build/compose-state/
- State file contains all services
- State file has correct port mappings
- Tests can read and parse state file
- State file has all required fields (stackName, projectName, lifecycle, services, etc.)

**Estimated Effort**: 4 hours

**Total Integration Test Effort**: 26 hours (3-4 days)

---

### Priority 2: Refactor JUnit Extensions (Est: 1-2 days)

**Problem**: JUnit extensions duplicate compose logic instead of using `ComposeService`.

**Current Issue**:
- `DockerComposeClassExtension` and `DockerComposeMethodExtension` directly execute `docker compose` commands
- Bypasses the plugin's `ComposeService` abstraction
- Code duplication with `ExecLibraryComposeService`
- Different error handling and logging
- Harder to unit test

**Solution**:

1. **Inject `ComposeService` into JUnit extensions**:
   - Pass `ComposeService` instance via system property or generated config file
   - Read service instance in extension constructors

2. **Remove direct process execution**:
   - Replace direct `docker compose` calls with `composeService.upStack()`, `composeService.downStack()`
   - Use same `WaitConfig` and `LogsConfig` models

3. **Benefits**:
   - Single source of truth for compose operations
   - Better testability (can mock `ComposeService`)
   - Consistent error messages
   - Easier maintenance

**Files to Modify**:
- `plugin/src/main/java/com/kineticfire/gradle/docker/junit/DockerComposeClassExtension.java`
- `plugin/src/main/java/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtension.java`

**Unit Tests Required**:
- Test service injection mechanism
- Test compose operations use service
- Test error handling through service
- Mock `ComposeService` in extension tests

**Estimated Effort**: 8-12 hours

---

### Priority 3: Documentation & Examples (Est: 1 day)

#### Update `/docs/usage/usage-docker-orch.md`

**Sections to Add**:

1. **Overview of Three Lifecycle Patterns**
   - Suite: Fastest, shared containers for all tests
   - Class: Balanced, fresh containers per test class
   - Method: Slowest, maximum isolation, fresh containers per test method

2. **Suite Lifecycle Examples**
   ```groovy
   dockerOrch {
       composeStacks {
           integrationSuite {
               files.from('src/integrationTest/resources/compose/integration.yml')
               projectName = "integration-${project.name}"

               waitForHealthy {
                   services = ["web-server", "database"]
                   timeoutSeconds = 60
               }

               waitForRunning {
                   services = ["redis", "worker"]
                   timeoutSeconds = 30
               }

               logs {
                   writeTo = file("$buildDir/compose-logs/integrationSuite")
                   tailLines = 500
               }
           }
       }
   }

   tasks.register('integrationTest', Test) {
       usesCompose stack: "integrationSuite", lifecycle: "suite"
       systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("integrationSuite")
   }
   ```

3. **Class Lifecycle Examples**
   ```groovy
   tasks.register('integrationTestClass', Test) {
       usesCompose stack: "integrationClass", lifecycle: "class"
       systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("integrationClass")
   }
   ```

   ```java
   @ExtendWith(DockerComposeClassExtension.class)
   class MyIntegrationTest {
       // Tests share containers
   }
   ```

4. **Method Lifecycle Examples**
   ```groovy
   tasks.register('integrationTestMethod', Test) {
       usesCompose stack: "integrationMethod", lifecycle: "method"
       systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("integrationMethod")
   }
   ```

   ```java
   @ExtendWith(DockerComposeMethodExtension.class)
   class MyIsolatedTest {
       // Each test gets fresh containers
   }
   ```

5. **Mixed Container States Example**
   ```groovy
   dockerOrch {
       composeStacks {
           mixedHealth {
               files.from('compose.yml')

               // Some services have health checks - wait for HEALTHY
               waitForHealthy {
                   services = ["api-server", "web-server"]
                   timeoutSeconds = 60
               }

               // Some services don't have health checks - wait for RUNNING
               waitForRunning {
                   services = ["redis", "postgres", "worker"]
                   timeoutSeconds = 30
               }
           }
       }
   }
   ```

6. **State File Consumption Example**
   ```groovy
   @Test
   void testServiceEndpoint() {
       // Read state file
       def stateFile = new File(System.getProperty("COMPOSE_STATE_FILE"))
       def state = new JsonSlurper().parse(stateFile)

       // Get port mapping for service
       def webServer = state.services['web-server']
       def port = webServer.publishedPorts.find { it.container == 80 }.host

       // Connect to container
       def url = new URL("http://localhost:${port}/health")
       def response = url.text

       assert response.contains("healthy")
   }
   ```

7. **Troubleshooting Guide**
   - Container won't start (check logs, check compose file syntax)
   - Health check timeout (increase timeout, check health check command)
   - Lingering containers (verify compose down is called, check docker ps -a)
   - Port conflicts (ensure unique ports per test)
   - State file not found (verify task configuration, check build directory)

**Estimated Effort**: 6-8 hours

---

## Production Readiness Checklist

### Must-Have (Blocking):
- [ ] **Complete wait integration** in tasks (Task 1)
- [ ] **Complete logs integration** in tasks (Task 2)
- [ ] **Generate state files** in suite lifecycle (Task 3)
- [ ] **Unit tests** for wait/logs paths in tasks (Task 4)
- [ ] **Integration test scenario 1** - Basic up/down
- [ ] **Integration test scenario 2** - Mixed wait states (HEALTHY + RUNNING)
- [ ] **Integration test scenario 3** - Class lifecycle
- [ ] **Integration test scenario 4** - Method lifecycle
- [ ] **Integration test scenario 5** - Logs capture
- [ ] **Integration test scenario 6** - State file consumption
- [ ] **Verify zero container leaks** after all test completions

### Should-Have (Important):
- [ ] **Refactor JUnit extensions** to use `ComposeService` (Priority 2)
- [ ] **Documentation** with examples for all three lifecycles (Priority 3)
- [ ] **Error handling** for common failure scenarios
- [ ] **Better logging** to help users debug issues

## Timeline

| Phase | Tasks | Effort | Duration |
|-------|-------|--------|----------|
| **Phase 1** | P0 Tasks 1-4 (Core Functionality) | 17-24 hours | 2-3 days |
| **Phase 2** | P1 Tasks (Integration Tests) | 26 hours | 3-4 days |
| **Phase 3** | P2 Tasks (Refactor Extensions) | 8-12 hours | 1-2 days |
| **Phase 4** | P3 Tasks (Documentation) | 6-8 hours | 1 day |
| **Total** | | **57-70 hours** | **7-10 days** |

## Success Criteria

1. ✅ All 6 integration test scenarios pass
2. ✅ Unit test coverage ≥90% for task execution paths
3. ✅ Zero container leaks after test completion
4. ✅ Documentation complete with all three lifecycle patterns
5. ✅ State files generated correctly in all lifecycles
6. ✅ Wait works for HEALTHY, RUNNING, and mixed states
7. ✅ Logs captured and written correctly
8. ✅ JUnit extensions use `ComposeService` (no code duplication)

## Notes

- **Architecture is sound**: No major refactoring needed
- **Foundation is solid**: DSL, models, service layer all complete
- **Remaining work is integration**: Wiring components together and testing
- **Testing is critical**: Zero integration tests currently exist
- **Documentation is important**: Users need clear examples for all lifecycle patterns

## References

- Current analysis: `/docs/design-docs/todo/2025-10-10-enhance-docker-orch.md` (this file)
- Usage documentation: `/docs/usage/usage-docker-orch.md` (to be updated)
- Integration test structure: `/plugin-integration-test/dockerOrch/` (to be created)
- Unit tests: `/plugin/src/test/groovy/com/kineticfire/gradle/docker/task/`
- Example implementation: `/old-plugin-integration-test/app-image/build.gradle` (lines 184-219)
