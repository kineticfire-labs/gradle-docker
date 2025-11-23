# dockerOrch Integration Tests

Integration tests for the `dockerOrch` task (Docker Compose orchestration).

## Purpose

These integration tests validate the `dockerOrch` plugin functionality by:
1. **Running real Docker Compose stacks** - No mocks, actual containers
2. **Testing real-world scenarios** - Build → Compose Up → Test → Compose Down
3. **Demonstrating plugin usage** - Users can copy scenarios as examples
4. **Validating wait mechanisms** - `waitForHealthy` and `waitForRunning` functionality
5. **Ensuring Gradle 9/10 compatibility** - Build cache, configuration cache, Provider API

**For comprehensive usage documentation**, see:
- [dockerOrch DSL Usage Guide](../../docs/usage/usage-docker-orch.md) - Complete guide with all examples and patterns
- [Spock and JUnit Test Extensions Guide](../../docs/usage/spock-junit-test-extensions.md) - Detailed extension
  documentation

This README focuses on the integration test structure and organization. For complete DSL usage, configuration patterns,
and detailed examples, refer to the usage guides above.

## Test Organization

The `dockerOrch/` tests are organized into two distinct categories:

### Verification Tests (`verification/`)

**Purpose**: Validate plugin mechanics (for plugin developers)

These tests use internal validators from `buildSrc/` to verify the plugin infrastructure works correctly. They test
features that users typically wouldn't test directly.

**⚠️ Important**: Do NOT copy these tests for your own projects. See `examples/` for user-facing demonstrations.

| Scenario         | Location                         | Status     | Lifecycle | Plugin Features Tested                                        |
|------------------|----------------------------------|------------|-----------|---------------------------------------------------------------|
| Basic            | `verification/basic/`            | ✅ Complete | CLASS     | composeUp, composeDown, state files, port mapping, cleanup    |
| Wait Healthy     | `verification/wait-healthy/`     | ✅ Complete | CLASS     | waitForHealthy, health check timing, timeout handling         |
| Wait Running     | `verification/wait-running/`     | ✅ Complete | CLASS     | waitForRunning, running state detection                       |
| Mixed Wait       | `verification/mixed-wait/`       | ✅ Complete | CLASS     | Both wait types together (app + database)                     |
| Lifecycle Class  | `verification/lifecycle-class/`  | ✅ Complete | CLASS     | Class-level lifecycle, setupSpec/cleanupSpec, state persistence |
| Lifecycle Method | `verification/lifecycle-method/` | ✅ Complete | METHOD    | Method-level lifecycle, setup/cleanup, state isolation        |
| Existing Images  | `verification/existing-images/`  | ✅ Complete | CLASS     | Public images (nginx, redis), sourceRef pattern               |
| Logs Capture     | `verification/logs-capture/`     | ✅ Complete | CLASS     | Log capture (full, tail, service-specific), writeTo, tailLines |
| Multi Service    | `verification/multi-service/`    | ✅ Complete | CLASS     | Complex orchestration (4 services: app, postgres, redis, nginx), mixed wait strategies |

**Lifecycle Types:**
- **CLASS** - Containers start once per test class in setupSpec/@BeforeAll, all test methods run against same
  containers, containers stop in cleanupSpec/@AfterAll. Most efficient for integration testing.
- **METHOD** - Containers start in setup/@BeforeEach, one test method runs, containers stop in cleanup/@AfterEach.
  Use when tests require isolated, clean state.

### Example Tests (`examples/`)

**Purpose**: Demonstrate real-world usage (for plugin users)

These tests show how real users would test their applications using standard testing libraries. Each example serves as
living documentation and a copy-paste template.

**✅ Recommended**: Copy and adapt these for your own projects!

| Example                  | Location                           | Status     | Test Framework | Lifecycle | Use Case                           | Testing Libraries                   |
|--------------------------|------------------------------------|-----------|--------------|-----------|------------------------------------|-------------------------------------|
| Web App (Spock)          | `examples/web-app/`                | ✅ Complete | Spock        | CLASS     | REST API testing                   | Spock, RestAssured, Groovy          |
| Web App (JUnit 5)        | `examples/web-app-junit/`          | ✅ Complete | JUnit 5      | CLASS     | REST API testing                   | JUnit 5, RestAssured, Jackson       |
| Stateful Web App         | `examples/stateful-web-app/`       | ✅ Complete | Spock        | Gradle Tasks | Session management, workflow tests | Spock, RestAssured, Groovy       |
| Isolated Tests (Spock)   | `examples/isolated-tests/`         | ✅ Complete | Spock        | METHOD    | Database isolation, independent tests | Spock, RestAssured, JPA, H2      |
| Isolated Tests (JUnit 5) | `examples/isolated-tests-junit/`   | ✅ Complete | JUnit 5      | METHOD    | Database isolation, independent tests | JUnit 5, RestAssured, JPA, H2    |
| Database App (Spock)     | `examples/database-app/`           | ✅ Complete | Spock        | CLASS     | PostgreSQL integration, CRUD + JDBC validation | Spock, RestAssured, JPA, PostgreSQL, Groovy SQL |
| Microservices            | `examples/microservices/`          | ⏳ Planned  | TBD          | CLASS     | Service orchestration              | RestAssured, service discovery      |
| Kafka App                | `examples/kafka-app/`              | ⏳ Planned  | TBD          | CLASS     | Event-driven architecture          | Kafka client, TestProducer/Consumer |
| Batch Job                | `examples/batch-job/`              | ⏳ Planned  | TBD          | CLASS     | Scheduled processing               | Spring Batch, JDBC                  |

**Test Framework Variants and Configuration Patterns:**

| Test Type | Framework | Configuration Pattern | Annotation | Notes |
|-----------|-----------|----------------------|------------|-------|
| **Examples** | Spock | **Recommended** (build.gradle + `usesCompose()`) | `@ComposeUp` (zero-parameter) | All examples demonstrate this pattern |
| **Examples** | JUnit 5 | **Recommended** (build.gradle + `usesCompose()`) | `@ExtendWith(DockerComposeClassExtension.class)` (zero-parameter) | Same pattern as Spock examples |
| **Verification** | Spock | Backward compatible (annotation-only) | `@ComposeUp(stackName=..., composeFiles=..., ...)` | Uses full annotation parameters |
| **Verification** | JUnit 5 | Manual system properties | `@ExtendWith(...)` + manual systemProperty calls | Verification tests use internal patterns |

**Configuration Pattern Guide:**

1. **Recommended Pattern (All Examples):**
   - Define stack in `dockerOrch.composeStacks { }` in `build.gradle`
   - Use `usesCompose(stack: "stackName", lifecycle: "class/method")` in test task
   - Use zero-parameter annotation in test class
   - **Benefits:** Single source of truth, no duplication, easy to share across tests

2. **Backward Compatible Pattern (Some Verification Tests):**
   - Configure all parameters directly in the annotation
   - No `usesCompose()` needed
   - **Use when:** Maintaining legacy tests, quick prototyping

**Which pattern should you use?**
- ✅ **For new projects:** Use the recommended pattern (see examples/)
- ⚠️ **For plugin verification:** See verification/ tests (not intended for user copying)

## Task Organization

**Task Names:**
- Integration tests are identified by task name `integrationTest` (not by group)
- Each subproject has its own `integrationTest` task
- The root `integrationTest` depends on all subproject integration tests

**Task Groups:**
- `verification` group contains Docker verification tasks (`cleanDockerImages`, `verifyDockerImages`)
- `build` group contains cleanup tasks (`cleanAll`)

**Generated Tasks:**
- `composeUp<StackName>` - Start Docker Compose stack
- `composeDown<StackName>` - Stop and remove Docker Compose stack
- Each stack defined in `dockerOrch { composeStacks { ... } }` generates these tasks

## Key Plugin Features

### Auto-Wiring of Test Dependencies

When using `usesCompose()` with **class** or **method** lifecycles, the plugin **automatically wires** compose lifecycle
task dependencies to your test task. You no longer need manual `afterEvaluate` blocks for `composeUp*` / `composeDown*`
dependencies!

**What gets auto-wired:**
```gradle
tasks.named('integrationTest') {
    usesCompose(stack: "myStack", lifecycle: "class")
}

// Plugin automatically adds (you don't write this!):
// integrationTest.dependsOn 'composeUpMyStack'
// integrationTest.finalizedBy 'composeDownMyStack'
```

**Manual wiring still required:** You must still wire **image build dependencies** to compose tasks (plugin cannot
auto-detect image sources).

```gradle
afterEvaluate {
    tasks.named('composeUpMyStack') {
        dependsOn tasks.named('dockerBuildMyApp')  // Required: ensure image exists
    }
}
```

**See:** [Understanding Task Dependencies](../../docs/usage/usage-docker-orch.md#understanding-task-dependencies) for
complete details and patterns.

---

### Integration Test Source Set Convention

The plugin **automatically creates** the `integrationTest` source set when java or groovy plugin is present. This
eliminates ~40-50 lines of boilerplate per project!

**Automatic setup includes:**
- Source directories: `src/integrationTest/java`, `src/integrationTest/groovy`, `src/integrationTest/resources`
- Configurations: `integrationTestImplementation`, `integrationTestRuntimeOnly`
- Tasks: `integrationTest`, `processIntegrationTestResources`
- Classpath: Automatic access to main source set classes

**Minimal configuration example:**
```gradle
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

// Plugin creates integrationTest source set automatically!

dependencies {
    integrationTestImplementation 'org.spockframework:spock-core:2.3'
}

// That's it! Convention provides everything else.
```

**See:** [Integration Test Source Set Convention](../../docs/usage/usage-docker-orch.md#integration-test-source-set-convention)
for customization options and migration guide.

---

### Wait Mechanisms

Choose the appropriate wait mechanism for your services:

**waitForHealthy (RECOMMENDED):**
- Container is RUNNING **AND** health check passed
- Use for: databases, web apps, APIs (anything needing initialization)
- Requires: Health check defined in compose file

**waitForRunning:**
- Container process has started (may not be ready)
- Use for: simple services without health checks
- Faster but less reliable

**Decision guide:**

| Factor | waitForRunning | waitForHealthy |
|--------|----------------|----------------|
| **Service has health check** | Optional | ✅ Required |
| **Service needs initialization** | ❌ Not reliable | ✅ Recommended |
| **Speed** | ⚡ Faster | ⏱️ Waits for health |
| **Test reliability** | ⚠️ May fail if not ready | ✅ Runs when ready |
| **Examples** | Static files, proxies | Databases, web apps, APIs |

**See:** [Container Readiness: Waiting for Services](../../docs/usage/usage-docker-orch.md#container-readiness-waiting-for-services)
for decision guide and examples.

---

### Lifecycle Patterns

**IMPORTANT:** There are only TWO lifecycles:

**CLASS Lifecycle:**
- Containers start once before all tests in a class
- All test methods share the same containers
- State persists between test methods
- Faster execution (containers start only once)
- Used by: Test framework extensions AND Gradle tasks

**METHOD Lifecycle:**
- Containers restart fresh for each test method
- Complete isolation between tests
- State does NOT persist between test methods
- Slower execution (containers restart for each test)
- Used by: Test framework extensions only

**Orchestration Approaches:**

1. **Test Framework Extensions (RECOMMENDED):** Use Spock or JUnit 5 extensions for automatic lifecycle management
   - Supports: CLASS and METHOD lifecycles
   - Configuration: Via `usesCompose()` in build.gradle + zero-parameter annotation
   - Auto-wiring: Compose up/down dependencies added automatically

2. **Gradle Tasks (ALTERNATIVE):** Use `composeUp*` / `composeDown*` tasks for manual orchestration
   - Supports: CLASS lifecycle only
   - Configuration: Manual `dependsOn` / `finalizedBy` in build.gradle
   - Use cases: CI/CD pipelines, custom orchestration, manual control

**See:** [Lifecycle Patterns](../../docs/usage/usage-docker-orch.md#lifecycle-patterns) for detailed comparison and when
to use each.

## Running dockerOrch Tests

**⚠️ All commands must be run from `/plugin-integration-test/` directory.**

```bash
# Run all dockerOrch integration tests (verification + examples)
./gradlew -Pplugin_version=<version> dockerOrch:integrationTest

# Run all verification tests (plugin mechanics validation)
./gradlew -Pplugin_version=<version> dockerOrch:verification:integrationTest

# Run all example tests (user-facing demonstrations)
./gradlew -Pplugin_version=<version> dockerOrch:examples:integrationTest

# Run specific verification test
./gradlew -Pplugin_version=<version> dockerOrch:verification:basic:integrationTest
./gradlew -Pplugin_version=<version> dockerOrch:verification:wait-healthy:integrationTest

# Run specific example test
./gradlew -Pplugin_version=<version> dockerOrch:examples:web-app:integrationTest
./gradlew -Pplugin_version=<version> dockerOrch:examples:database-app:integrationTest

# Run with clean for specific test
./gradlew -Pplugin_version=<version> dockerOrch:verification:basic:clean dockerOrch:verification:basic:integrationTest
./gradlew -Pplugin_version=<version> dockerOrch:examples:web-app:clean dockerOrch:examples:web-app:integrationTest

# Verify no containers remain after tests
docker ps -a | grep verification
docker ps -a | grep example
```

## Troubleshooting

### Quick Diagnostics

**Containers not starting:**
1. Check test output for error messages during setup
2. Verify `usesCompose()` matches stack name in dockerOrch DSL
3. Ensure compose file exists at expected path
4. Check Docker daemon is running: `docker info`

**Containers not stopping:**
1. Extensions automatically clean up - check for test crashes
2. Manually clean: `docker compose -p <project-name> down -v`
3. Force remove: `docker ps -aq --filter name=<project-name> | xargs -r docker rm -f`

**Health checks timing out:**
1. Increase timeout in dockerOrch DSL: `timeoutSeconds.set(120)`
2. Use `waitForRunning` instead of `waitForHealthy` (faster but less reliable)
3. Check service logs: `docker compose -p <project-name> logs <service-name>`

**Port conflicts:**
1. Use dynamic port assignment in compose files: `ports: - "8080"` (not `"9091:8080"`)
2. Read actual port from state file in tests
3. Find conflicting containers: `docker ps --filter publish=8080`

**State file not found:**
1. Ensure extension annotation is present on test class
2. Verify compose up completed successfully (check test output)
3. Check dockerOrch DSL configuration matches system properties

**Configuration conflicts (Spock):**
1. Use EITHER `usesCompose()` in build.gradle OR annotation parameters, not both
2. Recommended: Remove all annotation parameters, use `usesCompose()` only

---

### Comprehensive Troubleshooting Guide

For complete troubleshooting with detailed solutions, see:
- [dockerOrch Troubleshooting Guide](../../docs/usage/usage-docker-orch.md#troubleshooting-guide) -
  Covers 8 common issues including containers not stopping, health check timeouts, port conflicts,
  configuration conflicts, configuration cache issues, and more

Each issue includes:
- Symptom description
- Root cause explanation
- Step-by-step solutions
- Verification commands
- Prevention strategies

## Validation Infrastructure

Common validators in `buildSrc/`:
- `DockerComposeValidator`: Container state validation (running, healthy)
- `StateFileValidator`: Parse and validate state JSON files
- `HttpValidator`: HTTP endpoint testing
- `CleanupValidator`: Verify no resource leaks after tests

**Note**: Validators are used primarily by verification tests. Example tests use standard testing libraries
(RestAssured, JDBC, etc.).

For detailed validator documentation, see verification test README files.

## Success Criteria

For each dockerOrch test:
- [ ] All files created as specified
- [ ] Build completes successfully with Gradle 9/10
- [ ] All integration tests pass
- [ ] Containers cleaned up automatically via `composeDown`
- [ ] `docker ps -a` shows no containers after completion
- [ ] Can run test multiple times successfully
- [ ] Build cache works correctly
- [ ] Configuration cache works correctly

## De-conflict Integration Tests

Integration tests run in parallel. Shared resources must be manually de-conflicted:

**Docker Compose Project Names:**
- Use unique project names per test (e.g., `verification-basic-web-app-test`, `example-web-app-test`)
- Configured via `dockerOrch { composeStacks { stackName { projectName = "unique-name" } } }`

**Ports:**
- Use random port assignment in Docker Compose files: `"8080"` (not `"9091:8080"`)
- Plugin assigns random host ports to avoid conflicts
- Tests read actual ports from state file

**Image Names:**
- Verification tests: `verification-<scenario>-<image-name>` (e.g., `verification-basic-web-app`)
- Example tests: `example-<scenario>-<image-name>` (e.g., `example-web-app`)

For complete de-conflict rules, see [`../README.md`](../README.md#de-conflict-integration-tests).

## Gradle 9/10 Compatibility

All scenarios demonstrate proper Gradle 9/10 usage:
- ✅ Version catalogs (`gradle/libs.versions.toml`)
- ✅ Build cache enabled and working
- ✅ Configuration cache compatible
- ✅ Provider API usage (lazy configuration)
- ✅ Task configuration avoidance
- ✅ No deprecated APIs
