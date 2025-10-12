# Verification Tests

These tests validate the dockerOrch plugin's internal mechanics. They are **NOT** examples of how users should write
tests.

## Purpose

Verification tests prove the plugin infrastructure works correctly by validating:

- ✅ Containers start and reach correct states (RUNNING, HEALTHY)
- ✅ State files are generated with correct structure and data
- ✅ Port mapping works correctly (container → host)
- ✅ Wait mechanisms block properly until conditions are met
- ✅ Cleanup removes all containers, networks, and volumes
- ✅ Lifecycle timing is correct (class vs method level)
- ✅ Log capture generates files with expected content
- ✅ Multi-service orchestration coordinates correctly

## Testing Approach

Verification tests use **internal validators** from `buildSrc/`:

- `DockerComposeValidator` - Validates Docker Compose stack state
- `StateFileValidator` - Validates state file structure and content
- `CleanupValidator` - Ensures no resource leaks after tests
- `HttpValidator` - Basic HTTP connectivity checks
- `PortValidator` - Validates port mapping correctness

These validators directly interact with Docker CLI and file system to verify plugin behavior.

## ⚠️ Important Warning

**Do NOT copy these tests for your own projects!**

These tests are designed for plugin developers to validate plugin mechanics. They use internal tools that real users
would never need.

For user-facing examples of how to test your applications, see `../examples/`

## Verification Scenarios

| Scenario | Description | Lifecycle | Features Tested |
|----------|-------------|-----------|-----------------|
| `basic/` | Basic compose up/down with health checks | CLASS | composeUp, composeDown, state files, port mapping, cleanup |
| `wait-healthy/` | Wait for healthy functionality | CLASS | waitForHealthy, health check timing, timeout handling |
| `wait-running/` | Wait for running functionality | CLASS | waitForRunning, running state detection |
| `mixed-wait/` | Both wait types together | CLASS | Sequential wait processing, multiple services |
| `lifecycle-class/` | Class-level lifecycle | CLASS | ✅ COMPLETE - setupSpec/cleanupSpec timing, state persistence across methods |
| `lifecycle-method/` | Method-level lifecycle | METHOD | ✅ COMPLETE - setup/cleanup timing, state isolation between methods |
| `logs-capture/` | Log capture functionality | CLASS | Logs configuration, file generation |
| `multi-service/` | Complex orchestration | CLASS | Multiple services, dependencies, scaling |
| `existing-images/` | Public images without building | CLASS | sourceRef pattern, Docker Hub images |

**Lifecycle Types:**
- **CLASS** - Containers start once per test class (setupSpec/cleanupSpec or @BeforeAll/@AfterAll), all tests run,
  containers stop once
- **METHOD** - Containers start/stop for each test method (setup/cleanup or @BeforeEach/@AfterEach)

## Running Verification Tests

From `plugin-integration-test/` directory:

```bash
# Run all verification tests
./gradlew dockerOrch:verification:integrationTest

# Run specific verification scenario
./gradlew dockerOrch:verification:basic:integrationTest
./gradlew dockerOrch:verification:wait-healthy:integrationTest
```

## Example Test Structure

```groovy
class BasicPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    def "plugin should generate valid state file"() {
        expect:
        StateFileValidator.assertValidStructure(stateData, 'stackName', projectName)
    }

    def "plugin should wait until container is healthy"() {
        expect:
        DockerComposeValidator.isContainerHealthy(projectName, 'service-name')
    }

    def cleanupSpec() {
        CleanupValidator.verifyNoContainersRemain(projectName)
    }
}
```

## Key Characteristics

1. **Uses buildSrc validators** - Direct validation of plugin behavior
2. **Tests plugin mechanics** - Not application logic
3. **Validates infrastructure** - State files, containers, ports, cleanup
4. **For developers** - Helps maintain plugin quality
5. **Not user-facing** - Real users would never write tests like these

---

**For user-facing examples**, see [`../examples/README.md`](../examples/README.md)
