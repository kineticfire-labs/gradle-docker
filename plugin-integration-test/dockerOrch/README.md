# dockerOrch Integration Tests

Integration tests for the `dockerOrch` task (Docker Compose orchestration).

## Purpose

These integration tests validate the `dockerOrch` plugin functionality by:
1. **Running real Docker Compose stacks** - No mocks, actual containers
2. **Testing real-world scenarios** - Build → Compose Up → Test → Compose Down
3. **Demonstrating plugin usage** - Users can copy scenarios as examples
4. **Validating wait mechanisms** - `waitForHealthy` and `waitForRunning` functionality
5. **Ensuring Gradle 9/10 compatibility** - Build cache, configuration cache, Provider API

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
| Logs Capture     | `verification/logs-capture/`     | ⏳ Planned  | CLASS     | Log capture configuration, file generation                    |
| Multi Service    | `verification/multi-service/`    | ⏳ Planned  | CLASS     | Complex orchestration (3+ services)                           |
| Existing Images  | `verification/existing-images/`  | ⏳ Planned  | CLASS     | Public images (nginx, redis), sourceRef pattern               |

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
| Database App             | `examples/database-app/`           | ⏳ Planned  | TBD          | CLASS     | Database integration               | JDBC, JPA, Spring Data              |
| Microservices            | `examples/microservices/`          | ⏳ Planned  | TBD          | CLASS     | Service orchestration              | RestAssured, service discovery      |
| Kafka App                | `examples/kafka-app/`              | ⏳ Planned  | TBD          | CLASS     | Event-driven architecture          | Kafka client, TestProducer/Consumer |
| Batch Job                | `examples/batch-job/`              | ⏳ Planned  | TBD          | CLASS     | Scheduled processing               | Spring Batch, JDBC                  |

**Test Framework Variants:**
- **Spock** examples use `@ComposeUp` annotation for automatic container lifecycle management
- **JUnit 5** examples use `@ExtendWith(DockerComposeClassExtension.class)` or `@ExtendWith(DockerComposeMethodExtension.class)`
- Both frameworks provide the same functionality; choose based on your project's test framework preference

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
