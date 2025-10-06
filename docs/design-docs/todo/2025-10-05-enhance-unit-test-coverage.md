# Unit Test Coverage Enhancement Plan

**Date:** 2025-10-05
**Status:** Proposed
**Target Completion:** 3 weeks from approval
**Author:** AI Analysis

---

## Executive Summary

Current unit test coverage stands at **71.0% instruction** and **66.7% branch coverage**. This plan targets
**92-95% overall coverage** by focusing on high-ROI unit testing of business logic while accepting documented gaps
for boundary layer classes that interface directly with external systems (Docker daemon, OS processes).

**Key Decision:** We will **NOT** enable the three disabled test files (`*.groovy.disabled`) because they require
extensive mocking of external libraries (Docker Java Client, process execution) which provides low return on
investment. These boundary layer classes are better validated through integration tests.

---

## Current State Analysis

### Coverage by Package

| Package | Instructions | Branches | Classification | Target |
|---------|--------------|----------|----------------|--------|
| **service** | 15.7% | 22.5% | Boundary + Logic | 35-40% |
| **extension** | 69.1% | 66.5% | Pure Logic | 100% |
| **main plugin** | 77.7% | 51.4% | Pure Logic | 95%+ |
| **task** | 82.6% | 63.6% | Business Logic | 95%+ |
| **model** | 88.7% | 80.5% | Pure Logic | 100% |
| **junit** | 88.9% | 77.3% | Mixed | 90%+ |
| **spec** | 96.2% | 87.7% | Pure Logic | 100% |
| **exception** | 100.0% | 100.0% | Complete | 100% |
| **junit.service** | 100.0% | 100.0% | Complete | 100% |

### Critical Findings

1. **Service Layer (15.7%)**
   - `DockerServiceImpl`: 30% instruction, 0% branch
   - `ExecLibraryComposeService`: 0% instruction, 0% branch
   - **Assessment:** These are boundary layer classes wrapping external APIs
   - **Decision:** Document as integration-tested, focus on extractable logic only

2. **Extension Layer (69.1%)**
   - `TestIntegrationExtension`: **16% coverage** - CRITICAL GAP
   - `DockerExtension`: 71% - needs completion
   - `DockerOrchExtension`: 91% - minor gaps
   - **Assessment:** Pure business logic with NO external dependencies
   - **Decision:** Must achieve 100% coverage

3. **Task Layer (82.6% / 63.6% branch)**
   - Moderate instruction coverage, poor branch coverage
   - **Assessment:** Business logic that calls service interfaces
   - **Decision:** Mock clean service interfaces, test all logic paths

4. **Main Plugin (77.7% / 51.4% branch)**
   - Poor branch coverage indicates untested error paths
   - **Assessment:** Orchestration and configuration logic
   - **Decision:** Must achieve 95%+ coverage

---

## Disabled Tests - Decision Not to Enable

### Files in Question

1. `DockerServiceImplEnhancedTest.groovy.disabled`
2. `ExecLibraryComposeServiceEnhancedTest.groovy.disabled`
3. `JsonServiceImplEnhancedTest.groovy.disabled`

### Why NOT to Enable

#### 1. Low Return on Investment

**Problem:** These tests require extensive mocking of external libraries:
- `DockerClient` and all its command objects (`BuildImageCmd`, `TagImageCmd`, etc.)
- Callback mechanisms (`ResultCallback<T>`, `BuildImageResultCallback`)
- `ProcessExecutor` and process results
- Groovy closure behavior within callbacks

**Reality Check:**
- Mocking complex external APIs primarily tests the mock setup, not your code
- Example: Mocking `BuildImageCmd` to return a callback that calls `awaitImageId()` tests that you can write
  mocks, not that your build logic is correct
- Integration tests already validate these work with real Docker/processes

#### 2. Architectural Correctness

**The Code Follows "Impure Shell, Pure Core" Pattern:**
- **Boundary Layer (Impure Shell):**
  - `DockerServiceImpl` - wraps Docker Java Client
  - `ExecLibraryComposeService` - wraps OS process execution
  - **Correct approach:** Integration test these

- **Business Logic (Pure Core):**
  - Extensions (DSL processing, validation)
  - Tasks (orchestration, property handling)
  - Models (data structures, parsing)
  - **Correct approach:** Unit test these thoroughly

**Per CLAUDE.md Line 151:**
> "Isolate side effects (I/O, clock, randomness, OS, network, DB) behind tiny boundary functions."

The service implementations ARE the boundary functions. They SHOULD interact with external systems.

#### 3. Maintenance Burden

**Complex mocks are brittle:**
- Break when Docker Java Client updates
- Require constant maintenance as library APIs evolve
- Create false confidence - passing tests with elaborate mocks don't guarantee real Docker integration works
- Debugging test failures becomes an exercise in mock archaeology

#### 4. Existing Documentation is Correct

The file `docs/design-docs/tech-debt/unit-test-gaps.md` already correctly documents:
- `DockerServiceImpl.createDockerClient()` as direct external service call
- Docker daemon ping as direct external service call
- ExecLibraryComposeService process execution as OS interaction
- Documents these as covered by integration tests
- States gaps represent < 5% of codebase (acceptable)

**This documentation is accurate and should be preserved.**

### What WILL Be Done Instead

**For Service Layer:**
1. Test any **extractable pure logic** that doesn't require external calls
2. Test validation, parsing, string manipulation within services
3. Accept boundary methods as integration-tested
4. Document clearly in gap file

**Example - Test These (No External Dependencies):**
```groovy
// Testable without Docker
parseServiceState("running") == ServiceStatus.RUNNING
parseServiceState("healthy") == ServiceStatus.HEALTHY

// Testable without processes
getComposeCommand() format validation
```

**Don't Test These (Require External Systems):**
```groovy
// Requires Docker daemon
buildImage(context).get()  // Actual Docker build

// Requires OS process execution
processExecutor.execute(["docker", "ps"])  // Actual process spawn
```

---

## Revised Test Coverage Strategy

### Philosophy: Test What Matters

**High-Value Testing:**
✅ Business logic without external dependencies
✅ Validation and parsing logic
✅ Configuration building and DSL processing
✅ Error handling and edge cases in pure logic
✅ Property configuration and wiring

**Low-Value Testing:**
❌ Elaborate mocks of third-party libraries
❌ Boundary layer methods that wrap external APIs
❌ Code that only works with real external systems

**Acceptance Criteria:**
- 100% coverage of unit-testable code (pure logic, no external dependencies)
- Documented, justified gaps for boundary layer classes (< 5-8% of codebase)
- All gaps covered by integration tests
- All new code follows testable design patterns

---

## Implementation Plan

### Week 1: Extension Layer (High Priority, High ROI)

#### Task 1.1: TestIntegrationExtension - 16% → 100%

**Priority:** CRITICAL
**Current Coverage:** 16% instruction, 0% branch
**Why Critical:** Pure business logic, NO external dependencies, currently almost untested
**Estimated Effort:** 4-6 hours
**Expected Coverage Gain:** +8% overall

**Methods to Test:**

1. **`composeStackFor(String stackName)`**
   - Test cases:
     - Valid stack name returns correct ComposeStackSpec
     - Null stack name throws NullPointerException
     - Unknown stack name throws IllegalArgumentException
     - Multiple stack names return different specs
   - Expected: Full branch coverage

2. **`composeConfigFor(ComposeStackSpec spec)`**
   - Test cases:
     - Valid spec builds correct ComposeConfig
     - Null spec throws NullPointerException
     - Spec with missing files handles defaults
     - Spec with env files includes them in config
     - Project name generation from stack name
   - Expected: Full branch coverage

3. **`composeStateFileFor(String stackName)`**
   - Test cases:
     - Valid stack name generates correct path
     - Path includes build directory provider
     - Path includes stack name in filename
     - Null stack name throws exception
   - Expected: Full branch coverage

4. **`saveComposeState(ComposeState state, RegularFile file)`**
   - Test cases:
     - Valid state serializes to JSON correctly
     - File creation and writing works
     - Parent directory creation if needed
     - Null state throws exception
     - Null file throws exception
     - IOException handling
   - Expected: Full branch coverage

5. **`loadComposeState(RegularFile file)`**
   - Test cases:
     - Valid JSON file deserializes correctly
     - Missing file returns null or throws (check implementation)
     - Corrupted JSON throws exception with clear message
     - Null file throws exception
     - File read errors handled
   - Expected: Full branch coverage

**Test Strategy:**
- Use Spock data-driven tests for multiple inputs
- Mock RegularFile and DirectoryProperty as needed
- Use temporary directories for file operations
- Test both success and failure paths

**Acceptance Criteria:**
- All 6 methods tested
- 100% line coverage
- 100% branch coverage
- All error paths tested
- Property-based tests for string inputs

---

#### Task 1.2: DockerExtension - 71% → 100%

**Priority:** HIGH
**Current Coverage:** 71% instruction, 65% branch
**Why Important:** Core DSL configuration, validation logic
**Estimated Effort:** 6-8 hours
**Expected Coverage Gain:** +5% overall

**Specific Gaps to Address:**

1. **`validatePublishConfiguration()` - closure4 has 0% coverage**
   - Location: Line references in DockerExtension
   - Test cases:
     - Valid publish configuration passes
     - Invalid publish configuration throws with clear message
     - Multiple publish targets validated correctly
     - Empty publish configuration handled
   - Focus on closure logic that's currently untested

2. **`validatePublishTarget()` - 54% branch coverage**
   - Test cases:
     - Registry-qualified targets validated
     - Non-qualified targets validated
     - Invalid formats rejected with clear errors
     - Edge cases: empty strings, null values, special characters
     - Mutual exclusivity of configuration options
   - Achieve 100% branch coverage

3. **Complex Validation Logic**
   - Test all validation methods comprehensively
   - Test error message clarity
   - Test validation order and short-circuit behavior
   - Test interaction between validations

**Test Strategy:**
- Use Spock's `where:` blocks for comprehensive input coverage
- Test both valid and invalid configurations
- Verify error messages are clear and actionable
- Test DSL builder pattern usage

**Acceptance Criteria:**
- All validation methods have 100% coverage
- All closures tested
- All branches in validation logic tested
- Clear error messages verified in tests
- Edge cases documented in test names

---

#### Task 1.3: DockerOrchExtension - 91% → 100%

**Priority:** MEDIUM
**Current Coverage:** 91% instruction, 97% branch
**Why Important:** Minor gaps to close for completeness
**Estimated Effort:** 2-3 hours
**Expected Coverage Gain:** +1% overall

**Specific Gaps:**

1. **`_validateEnvFiles_closure4` - 54% branch coverage**
   - Test cases:
     - Valid env files pass validation
     - Missing env files throw exception
     - Invalid file paths rejected
     - Empty env file list handled
     - Null env file in list handled

2. **Minor validation edge cases**
   - Test remaining untested branches
   - Test error conditions
   - Test boundary values

**Test Strategy:**
- Identify specific untested branches from JaCoCo report
- Write targeted tests for each branch
- Use parameterized tests for edge cases

**Acceptance Criteria:**
- 100% instruction coverage
- 100% branch coverage
- All validation edge cases tested

---

### Week 2: Task Layer and Main Plugin (High Priority, High ROI)

#### Task 2.1: Task Layer Branch Coverage - 63.6% → 95%+

**Priority:** HIGH
**Current Coverage:** 82.6% instruction, 63.6% branch
**Why Important:** Business logic and orchestration, currently missing 36% of branches
**Estimated Effort:** 12-16 hours
**Expected Coverage Gain:** +8% overall

**Strategy: Mock Service Interfaces, Test Task Logic**

The key insight: Task classes call `DockerService` and `ComposeService` interfaces. These interfaces are clean,
small, and easy to mock. We test the task logic, not the service implementation.

**For Each Task Class:**

1. **DockerBuildTask**
   - Test cases:
     - Valid configuration builds successfully
     - Missing required properties throws clear error
     - Invalid dockerfile path throws exception
     - Invalid context path throws exception
     - Build args properly passed to service
     - Tags properly passed to service
     - Service build failure handled with clear error
     - Service unavailable handled
     - Async completion handling
   - Mock `DockerService.buildImage()` to return CompletableFuture
   - Test both success and failure scenarios

2. **DockerTagTask**
   - Test cases:
     - Valid source image and tags succeed
     - Missing source image throws exception
     - Empty tags list throws exception
     - Invalid tag format throws exception
     - Service tag failure handled
     - Multiple tags processed correctly
   - Mock `DockerService.tagImage()` to return CompletableFuture

3. **DockerSaveTask**
   - Test cases:
     - Valid image and output file succeed
     - Each compression type properly configured
     - Missing image ID throws exception
     - Invalid output path throws exception
     - Service save failure handled
     - Parent directory creation handled
   - Mock `DockerService.saveImage()` to return CompletableFuture
   - Test all compression type configurations

4. **DockerPublishTask**
   - Test cases:
     - Valid image and registry succeed
     - Authentication credentials properly passed
     - Missing authentication handled
     - Invalid image reference throws exception
     - Service push failure handled
     - Registry connection failure handled
   - Mock `DockerService.pushImage()` to return CompletableFuture
   - Test with and without authentication

5. **ComposeUpTask**
   - Test cases:
     - Valid compose config starts stack
     - Missing compose files throws exception
     - Invalid project name throws exception
     - Service start failure handled
     - Wait configuration properly applied
     - Service status checking works
   - Mock `ComposeService.upStack()` to return CompletableFuture<ComposeState>
   - Mock `ComposeService.waitForServices()` if task calls it

6. **ComposeDownTask**
   - Test cases:
     - Valid project name stops stack
     - Missing project name throws exception
     - Service stop failure handled
     - Cleanup verification
   - Mock `ComposeService.downStack()` to return CompletableFuture<Void>

**Mocking Pattern Example:**
```groovy
class DockerBuildTaskTest extends Specification {

    DockerService mockDockerService
    DockerBuildTask task
    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        mockDockerService = Mock(DockerService)

        // Create task with mocked service
        task = project.tasks.create('testBuild', DockerBuildTask)
        task.dockerService.set(mockDockerService)
    }

    def "successful build with valid configuration"() {
        given:
        def context = new BuildContext(...)
        task.contextPath.set(context.contextPath)
        task.tags.set(["test:latest"])

        and: "mock service returns successful future"
        def futureImageId = CompletableFuture.completedFuture("sha256:abc123")
        mockDockerService.buildImage(_) >> futureImageId

        when:
        task.build()

        then:
        1 * mockDockerService.buildImage(_) >> { BuildContext ctx ->
            assert ctx.tags == ["test:latest"]
            return futureImageId
        }
        notThrown(Exception)
    }

    def "build fails when service throws exception"() {
        given:
        task.contextPath.set(validPath)
        task.tags.set(["test:latest"])

        and: "mock service throws exception"
        mockDockerService.buildImage(_) >> {
            CompletableFuture.failedFuture(
                new DockerServiceException(ErrorType.BUILD_FAILED, "Build failed")
            )
        }

        when:
        task.build()

        then:
        def ex = thrown(DockerServiceException)
        ex.errorType == ErrorType.BUILD_FAILED
    }
}
```

**Test Coverage Goals Per Task:**
- 95%+ instruction coverage
- 90%+ branch coverage
- All property configurations tested
- All validation logic tested
- All error handling paths tested
- Success and failure scenarios tested

**Acceptance Criteria:**
- All 6 task classes have 95%+ coverage
- Service interface interactions verified via mocks
- All error paths tested
- All property validation tested
- No direct external dependencies in tests

---

#### Task 2.2: GradleDockerPlugin - 77.7% / 51.4% → 95%+

**Priority:** HIGH
**Current Coverage:** 77.7% instruction, 51.4% branch (critical branch gap)
**Why Important:** Core plugin orchestration, task generation, configuration wiring
**Estimated Effort:** 8-10 hours
**Expected Coverage Gain:** +5% overall

**Areas to Test:**

1. **Plugin Application**
   - Test cases:
     - Plugin applies to Java/Gradle project successfully
     - Required Gradle version check
     - Incompatible Gradle version throws clear error
     - Plugin applied multiple times (idempotent)

2. **Service Registration**
   - Test cases:
     - DockerService registered as BuildService
     - ComposeService registered as BuildService
     - Service singleton behavior
     - Service lifecycle (creation, usage, cleanup)
     - Service registration failure handling

3. **Extension Creation**
   - Test cases:
     - `docker` extension created and accessible
     - `dockerOrch` extension created and accessible
     - `testIntegration` extension created and accessible
     - Extension configuration via DSL works
     - Extension validation at configuration time

4. **Task Generation**
   - Test cases:
     - Dynamic tasks created per image spec
     - Task naming conventions correct (e.g., `dockerBuildMyImage`)
     - Task dependencies configured correctly
     - Task inputs/outputs wired properly
     - Multiple image specs generate multiple tasks
     - No image specs = no tasks generated
     - Task configuration from extension works

5. **Error Handling**
   - Test cases:
     - Docker daemon unavailable at plugin apply time
     - Invalid extension configuration
     - Task generation failures
     - Service registration failures
     - Clear error messages for common problems

**Test Strategy:**
```groovy
class GradleDockerPluginTest extends Specification {

    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    def "plugin applies successfully to project"() {
        when:
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        then:
        project.plugins.hasPlugin('com.kineticfire.gradle.docker')
    }

    def "plugin creates expected extensions"() {
        given:
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        expect:
        project.extensions.findByName('docker') != null
        project.extensions.findByName('dockerOrch') != null
        project.extensions.findByName('testIntegration') != null
    }

    def "plugin generates tasks for each image spec"() {
        given:
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        and:
        project.docker {
            images {
                alpine {
                    tags = ['myapp:latest']
                    dockerfile = file('Dockerfile.alpine')
                }
                ubuntu {
                    tags = ['myapp:ubuntu']
                    dockerfile = file('Dockerfile.ubuntu')
                }
            }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('dockerBuildAlpine') != null
        project.tasks.findByName('dockerBuildUbuntu') != null
        project.tasks.findByName('dockerTagAlpine') != null
        project.tasks.findByName('dockerTagUbuntu') != null
    }
}
```

**Branch Coverage Focus:**
- Test all conditional logic in plugin apply
- Test all branches in task generation loops
- Test all error handling paths
- Test configuration validation branches

**Acceptance Criteria:**
- 95%+ instruction coverage
- 90%+ branch coverage
- All plugin lifecycle tested
- All extension creation tested
- All task generation logic tested
- All error paths tested with clear messages
- Configuration cache compatibility verified

---

### Week 3: Model, Spec, and Final Polish

#### Task 3.1: Model Layer - 88.7% → 100%

**Priority:** MEDIUM
**Current Coverage:** 88.7% instruction, 80.5% branch
**Why Important:** Data structures and parsing logic, should be 100%
**Estimated Effort:** 4-6 hours
**Expected Coverage Gain:** +2% overall

**Focus Areas:**

1. **`ImageRefParts.parse()`**
   - Test cases:
     - Simple image name: `nginx`
     - Image with tag: `nginx:latest`
     - Image with registry: `docker.io/nginx`
     - Image with registry and tag: `docker.io/nginx:1.21`
     - Image with port: `localhost:5000/myapp`
     - Image with namespace: `myorg/myapp:v1.0`
     - Complex: `registry.example.com:5000/namespace/image:tag`
     - Edge cases: empty string, null, invalid formats
     - Special characters in various parts
   - Test all parsing branches
   - Test `isRegistryQualified()` method

2. **`BuildContext` validation**
   - Test cases:
     - Valid build context with all fields
     - Missing required fields
     - Invalid paths
     - Empty tags list
     - Null values
     - Build args with special characters

3. **`SaveCompression` enum**
   - Test cases:
     - All enum values exist
     - File extension mapping correct
     - MIME type mapping (if present)

4. **`AuthConfig`**
   - Test cases:
     - Username/password auth
     - Token-based auth
     - Missing credentials detection
     - `hasCredentials()` method all branches
     - `toDockerJavaAuthConfig()` conversion

5. **`ComposeConfig` validation**
   - Test cases:
     - Valid config with all fields
     - Missing required fields
     - Empty file lists
     - Invalid paths

6. **Other model classes**
   - `WaitConfig` - timeout, poll interval validation
   - `LogsConfig` - follow, tail lines configuration
   - `ServiceInfo` - construction and getters
   - `ServiceStatus` - enum values
   - `ComposeState` - state management
   - `PortMapping` - port parsing and validation

**Test Strategy:**
- Use Spock `where:` blocks for comprehensive input testing
- Test boundary values
- Test null/empty cases
- Test invalid formats
- Verify error messages

**Acceptance Criteria:**
- 100% instruction coverage
- 100% branch coverage
- All parsing logic tested with valid and invalid inputs
- All validation logic tested
- All edge cases documented

---

#### Task 3.2: Spec Layer - 96.2% → 100%

**Priority:** LOW
**Current Coverage:** 96.2% instruction, 87.7% branch
**Why Important:** DSL specifications, easy wins
**Estimated Effort:** 2-3 hours
**Expected Coverage Gain:** +1% overall

**Focus Areas:**

1. **`PublishSpec`**
   - Test remaining validation branches
   - Test mutual exclusivity of options
   - Test error conditions

2. **`SaveSpec`**
   - Test compression type configuration
   - Test output file validation
   - Test remaining branches

3. **`ImageSpec`**
   - Test dockerfile path validation
   - Test context path validation
   - Test tag validation
   - Test build args configuration

4. **`ComposeStackSpec`**
   - Test compose file validation
   - Test env file validation
   - Test wait configuration

5. **`AuthSpec`**
   - Test credential configuration
   - Test validation logic

**Test Strategy:**
- Identify untested branches from JaCoCo report
- Write targeted tests for each branch
- Focus on validation and error cases

**Acceptance Criteria:**
- 100% instruction coverage
- 100% branch coverage
- All validation branches tested

---

#### Task 3.3: Service Layer - Extractable Logic Testing

**Priority:** MEDIUM
**Current Coverage:** 15.7% overall (varies by class)
**Target Coverage:** 35-40% overall
**Why Important:** Test pure logic within services without external dependencies
**Estimated Effort:** 4-6 hours
**Expected Coverage Gain:** +3-5% overall

**What to Test (WITHOUT External Dependencies):**

1. **`ExecLibraryComposeService.parseServiceState()`**
   - Test cases:
     - `"running"` → `ServiceStatus.RUNNING`
     - `"up"` → `ServiceStatus.RUNNING`
     - `"healthy"` → `ServiceStatus.HEALTHY`
     - `"Running (healthy)"` → `ServiceStatus.HEALTHY`
     - `"exited"` → `ServiceStatus.STOPPED`
     - `"stopped"` → `ServiceStatus.STOPPED`
     - `"restarting"` → `ServiceStatus.RESTARTING`
     - `""` → `ServiceStatus.UNKNOWN`
     - `null` → `ServiceStatus.UNKNOWN`
     - Mixed case: `"RUNNING"`, `"Up"`
   - **This is pure string parsing logic - NO external dependencies**

2. **`ExecLibraryComposeService.getComposeCommand()`**
   - Test that it returns expected command format
   - Test command structure (list of strings)
   - **No process execution needed, just command building**

3. **`DockerServiceImpl` - Testable Logic**
   - Test any string parsing/formatting
   - Test configuration building
   - Test validation logic
   - **Avoid testing methods that directly call Docker client**

4. **`JsonServiceImpl` (already at 99%)**
   - Close remaining 1% gap
   - Test error handling branches
   - Test edge cases in JSON parsing

**What NOT to Test:**
- ❌ `DockerServiceImpl.buildImage()` - requires Docker daemon
- ❌ `DockerServiceImpl.createDockerClient()` - requires Docker daemon
- ❌ `ExecLibraryComposeService.upStack()` - requires process execution
- ❌ Any method that calls `processExecutor.execute()` with actual processes
- ❌ Any method that calls `dockerClient.*` with actual Docker operations

**Test Strategy:**
```groovy
class ExecLibraryComposeServiceTest extends Specification {

    ExecLibraryComposeService service

    def setup() {
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)

        // Use test constructor with mocked dependencies
        service = new ExecLibraryComposeService(mockExecutor, mockValidator, mockLogger)
    }

    @Unroll
    def "parseServiceState maps '#input' to #expected"() {
        expect:
        service.parseServiceState(input) == expected

        where:
        input                    | expected
        "running"                | ServiceStatus.RUNNING
        "up"                     | ServiceStatus.RUNNING
        "healthy"                | ServiceStatus.HEALTHY
        "Running (healthy)"      | ServiceStatus.HEALTHY
        "exited"                 | ServiceStatus.STOPPED
        "stopped"                | ServiceStatus.STOPPED
        "restarting"             | ServiceStatus.RESTARTING
        ""                       | ServiceStatus.UNKNOWN
        null                     | ServiceStatus.UNKNOWN
    }
}
```

**Acceptance Criteria:**
- All extractable pure logic tested
- Service layer coverage increases to 35-40%
- No tests requiring Docker daemon or process execution
- Boundary methods documented as integration-tested

---

#### Task 3.4: Documentation Updates

**Priority:** MEDIUM
**Estimated Effort:** 2-3 hours

**Actions:**

1. **Update `docs/design-docs/tech-debt/unit-test-gaps.md`**
   - Add detailed section on boundary layer classes
   - Document `DockerServiceImpl` methods as integration-tested
   - Document `ExecLibraryComposeService` methods as integration-tested
   - Clarify which portions ARE unit tested (extractable logic)
   - Update statistics with new coverage numbers
   - Add architectural justification for gaps

2. **Create `docs/design-docs/testing/unit-testing-strategy.md`**
   - Document "Impure Shell, Pure Core" testing approach
   - Explain why boundary classes are integration-tested
   - Provide guidelines for writing testable code
   - Include examples of good vs. bad test design
   - Document mocking strategy for service interfaces

3. **Update Test README files**
   - Add overview of test strategy to `plugin/src/test/README.md`
   - Document how to run coverage reports
   - Document coverage expectations

**Documentation Template for Gaps File:**

```markdown
## Boundary Layer Classes (Integration-Tested)

### Philosophy

This codebase follows the "Impure Shell, Pure Core" architectural pattern:
- **Pure Core:** Business logic with no external dependencies (Extensions, Tasks, Models, Specs)
- **Impure Shell:** Boundary layer interfacing with external systems (Service Implementations)

The boundary layer classes directly interact with external systems and are better validated through
integration tests rather than elaborate unit test mocks.

### DockerServiceImpl (30% unit test coverage)

**Tested in Unit Tests (30%):**
- Constructor and basic initialization
- Getter methods
- Basic validation logic
- String parsing/formatting

**Integration-Tested (70%):**
- `buildImage()` - Requires Docker daemon, Docker Java Client
- `tagImage()` - Requires Docker daemon
- `saveImage()` - Requires Docker daemon, file I/O
- `pushImage()` - Requires Docker daemon, registry connection
- `pullImage()` - Requires Docker daemon, registry connection
- `imageExists()` - Requires Docker daemon
- `createDockerClient()` - Creates actual network socket to Docker daemon

**Justification:**
- These methods wrap Docker Java Client API calls
- Mocking `DockerClient` extensively tests mock setup, not business logic
- Integration tests validate actual Docker operations work correctly
- Boundary layer should be thin - logic extracted to pure core where possible

**Integration Test Coverage:** See `plugin-integration-test/app-image/integrationTest/docker/`

### ExecLibraryComposeService (0% unit test coverage, 45% from constructor)

**Tested in Unit Tests:**
- `parseServiceState()` - Pure string parsing logic
- Constructor with dependency injection
- Basic validation

**Integration-Tested:**
- `upStack()` - Spawns actual `docker compose up` process
- `downStack()` - Spawns actual `docker compose down` process
- `waitForServices()` - Polls actual container states
- `getStackServices()` - Executes actual `docker compose ps` process
- `checkServiceReady()` - Queries actual service state
- `captureLogs()` - Executes actual `docker compose logs` process

**Justification:**
- These methods spawn OS processes via ProcessExecutor
- Mocking process execution extensively doesn't validate OS integration
- Integration tests validate actual compose operations work correctly
- Process spawning is inherently an external dependency

**Integration Test Coverage:** See `plugin-integration-test/app-image/integrationTest/compose/`
```

**Acceptance Criteria:**
- Gap documentation updated and accurate
- Testing strategy documented
- Coverage statistics updated
- Examples provided for future developers

---

#### Task 3.5: JUnit Extensions Polish

**Priority:** LOW
**Current Coverage:** 88.9% instruction, 77.3% branch
**Target:** 90%+ instruction, 80%+ branch
**Estimated Effort:** 2-3 hours
**Expected Coverage Gain:** +0.5% overall

**Focus Areas:**

1. **`DockerComposeClassExtension`**
   - Test lifecycle methods (beforeAll, afterAll)
   - Test configuration parsing
   - Test error handling
   - **Note:** Some process execution portions will remain integration-tested

2. **`DockerComposeMethodExtension`**
   - Test lifecycle methods (beforeEach, afterEach)
   - Test configuration parsing
   - Test error handling
   - **Note:** Some process execution portions will remain integration-tested

**Test Strategy:**
- Mock JUnit Extension context
- Test configuration parsing without process execution
- Test error handling for invalid configurations
- Accept process execution portions as integration-tested

**Acceptance Criteria:**
- 90%+ instruction coverage
- 80%+ branch coverage
- Configuration and validation logic tested
- Process execution portions documented as integration-tested

---

## Final Targets and Expected Outcomes

### Coverage Targets

| Package | Current (Inst/Branch) | Target (Inst/Branch) | Classification |
|---------|----------------------|---------------------|----------------|
| **exception** | 100% / 100% | 100% / 100% | Complete ✅ |
| **junit.service** | 100% / 100% | 100% / 100% | Complete ✅ |
| **spec** | 96.2% / 87.7% | **100% / 100%** | Pure Logic |
| **model** | 88.7% / 80.5% | **100% / 100%** | Pure Logic |
| **extension** | 69.1% / 66.5% | **100% / 100%** | Pure Logic |
| **task** | 82.6% / 63.6% | **95% / 90%** | Business Logic |
| **main plugin** | 77.7% / 51.4% | **95% / 90%** | Orchestration |
| **junit** | 88.9% / 77.3% | **90% / 80%** | Mixed |
| **service** | 15.7% / 22.5% | **35-40% / 30-35%** | Boundary + Logic |

### Overall Expected Results

**Current Overall Coverage:**
- 71.0% instruction
- 66.7% branch

**Target Overall Coverage:**
- **92-95% instruction**
- **87-90% branch**

**Documented Gaps (Acceptable):**
- 5-8% of codebase
- Boundary layer classes
- Covered by integration tests
- Clearly documented with justification

---

## Success Criteria

### Quantitative Metrics

✅ **Overall instruction coverage ≥ 92%**
✅ **Overall branch coverage ≥ 87%**
✅ **Extension layer = 100% coverage** (currently 69.1%)
✅ **Task layer ≥ 95% instruction, ≥ 90% branch** (currently 82.6% / 63.6%)
✅ **Main plugin ≥ 95% instruction, ≥ 90% branch** (currently 77.7% / 51.4%)
✅ **Model layer = 100% coverage** (currently 88.7%)
✅ **Spec layer = 100% coverage** (currently 96.2%)
✅ **Service layer ≥ 35% coverage** (currently 15.7%)
✅ **All unit tests pass:** `./gradlew clean test`
✅ **No compilation or test warnings**
✅ **Build completes successfully:** `./gradlew -Pplugin_version=1.0.0 build`

### Qualitative Criteria

✅ **All business logic (pure core) has 100% coverage**
✅ **All boundary layer gaps documented with justification**
✅ **Gap documentation updated in `unit-test-gaps.md`**
✅ **Testing strategy documented**
✅ **All tests compatible with Gradle 9 and 10** (per `gradle-9-and-10-compatibility.md`)
✅ **No configuration cache issues in tests**
✅ **Tests use proper mocking of service interfaces** (not external libraries)
✅ **All error paths tested with clear assertions**
✅ **Property-based tests for validation logic**
✅ **No disabled tests (current disabled tests remain disabled with documented justification)**

---

## Gradle 9/10 Compatibility Requirements

All new tests must comply with Gradle 9 and 10 standards per
`docs/design-docs/gradle-9-and-10-compatibility.md`:

### Test Design Requirements

1. **No Project References at Execution Time**
   - Tests must not capture `Project` in closure
   - Use `ProjectBuilder` for test projects
   - Inject services via Gradle's injection mechanism

2. **Provider API Testing**
   - Test `Property<T>` and `Provider<T>` patterns
   - Test lazy evaluation behavior
   - Do not eagerly call `.get()` in test setup

3. **Configuration Cache Compatibility**
   - Tests must not violate configuration cache rules
   - Serialize test state properly
   - Avoid mutable static state

4. **BuildService Testing**
   - Test BuildService lifecycle
   - Test service registration and lookup
   - Test singleton behavior

### Example Test Pattern

```groovy
class MyTaskTest extends Specification {

    Project project
    MyTask task
    DockerService mockService

    def setup() {
        // Use ProjectBuilder for test project
        project = ProjectBuilder.builder().build()

        // Create mock service
        mockService = Mock(DockerService)

        // Register task
        task = project.tasks.register('myTask', MyTask).get()

        // Wire service via property (Gradle 9 pattern)
        task.dockerService.set(mockService)
    }

    def "task executes with valid configuration"() {
        given: "valid task configuration"
        task.imageName.set("test:latest")  // Use .set() not =
        task.contextPath.set(project.layout.projectDirectory.dir("docker"))

        and: "mock service returns success"
        mockService.buildImage(_) >> CompletableFuture.completedFuture("sha256:abc")

        when: "task executes"
        task.build()

        then: "service called correctly"
        1 * mockService.buildImage(_) >> { BuildContext ctx ->
            assert ctx.tags.contains("test:latest")
            return CompletableFuture.completedFuture("sha256:abc")
        }
    }
}
```

---

## Risk Mitigation

### Risk: Coverage targets not achievable in 3 weeks

**Mitigation:**
- Prioritize by ROI: Extensions → Tasks → Plugin → Model → Spec
- Focus on high-impact gaps first (TestIntegrationExtension, Task branches)
- Defer low-priority items (spec minor gaps) if time-constrained

### Risk: Tests become flaky or unstable

**Mitigation:**
- Use deterministic test data
- Mock external dependencies consistently
- Use Spock's isolation features
- Avoid timing-dependent tests
- Use proper test lifecycle management

### Risk: Mocking becomes too complex

**Mitigation:**
- Keep mocks simple - mock clean interfaces only
- If mocking becomes elaborate, consider refactoring code for testability
- Extract pure logic from impure shell
- Use fakes instead of deep mocks where appropriate

### Risk: Gradle compatibility issues in tests

**Mitigation:**
- Follow Gradle 9/10 patterns strictly
- Test with both Gradle 9 and 10
- Run with `--configuration-cache` to verify compatibility
- Use Provider API consistently

### Risk: Integration tests fail after changes

**Mitigation:**
- Run integration tests after unit test changes: `./gradlew cleanAll integrationTest`
- Ensure boundary layer behavior unchanged
- Update integration tests if boundary behavior changes
- Maintain integration test suite alongside unit tests

---

## Verification and Validation

### During Development

**After Each Task:**
1. Run unit tests: `./gradlew clean test`
2. Check coverage report: `build/reports/jacoco/test/html/index.html`
3. Verify no warnings in build output
4. Check specific package coverage in JaCoCo report

**After Each Week:**
1. Run full build: `./gradlew -Pplugin_version=1.0.0 clean build`
2. Verify overall coverage meets weekly target
3. Run integration tests: `./gradlew -Pplugin_version=1.0.0 build publishToMavenLocal && cd ../plugin-integration-test && ./gradlew cleanAll integrationTest`
4. Check for any regression in existing tests

### Final Validation

**Before Declaring Complete:**

1. **Coverage Check**
   ```bash
   cd plugin
   ./gradlew clean test jacocoTestReport
   # Verify coverage meets all targets in report
   ```

2. **Build Check**
   ```bash
   ./gradlew -Pplugin_version=1.0.0 clean build
   # Must complete with no errors or warnings
   ```

3. **Integration Test Check**
   ```bash
   cd plugin
   ./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal
   cd ../plugin-integration-test
   ./gradlew cleanAll integrationTest
   # All tests must pass
   ```

4. **Configuration Cache Check**
   ```bash
   ./gradlew test --configuration-cache
   ./gradlew test --configuration-cache
   # Second run should reuse cache
   ```

5. **Documentation Check**
   - `docs/design-docs/tech-debt/unit-test-gaps.md` updated
   - All gaps documented with justification
   - Statistics accurate

6. **No Lingering Containers**
   ```bash
   docker ps -a
   # Should show no containers after test run
   ```

---

## Effort Estimation

### Time Breakdown by Week

**Week 1: Extension Layer**
- Task 1.1 (TestIntegrationExtension): 4-6 hours
- Task 1.2 (DockerExtension): 6-8 hours
- Task 1.3 (DockerOrchExtension): 2-3 hours
- **Total: 12-17 hours**

**Week 2: Task Layer and Plugin**
- Task 2.1 (Task Layer): 12-16 hours
- Task 2.2 (GradleDockerPlugin): 8-10 hours
- **Total: 20-26 hours**

**Week 3: Model, Spec, Final Polish**
- Task 3.1 (Model Layer): 4-6 hours
- Task 3.2 (Spec Layer): 2-3 hours
- Task 3.3 (Service Layer Logic): 4-6 hours
- Task 3.4 (Documentation): 2-3 hours
- Task 3.5 (JUnit Extensions): 2-3 hours
- **Total: 14-21 hours**

### Total Effort

**Estimated Total: 46-64 hours** (approximately 1.5-2 weeks of full-time work, spread over 3 calendar weeks)

### Contingency

- Buffer: +20% for unexpected issues = **55-77 hours total**
- Critical path: Extension → Task → Plugin (can parallelize Model/Spec work)

---

## Recommendations

### DO:

✅ Focus on high-ROI testing (business logic)
✅ Mock clean service interfaces (DockerService, ComposeService)
✅ Extract pure logic from boundary methods where possible
✅ Use property-based testing for validation logic
✅ Document all gaps with clear justification
✅ Follow Gradle 9/10 patterns in all tests
✅ Test error paths and edge cases
✅ Keep tests simple and maintainable

### DON'T:

❌ Enable disabled tests with extensive external library mocks
❌ Mock Docker Java Client or ProcessExecutor in detail
❌ Test boundary layer methods that require external systems
❌ Write tests that only check null pointer exceptions
❌ Ignore branch coverage
❌ Leave gaps undocumented
❌ Violate configuration cache rules in tests
❌ Declare success below 92% overall coverage

### Long-Term Improvements:

1. **Establish pre-commit coverage checks**
   - Require coverage on changed files
   - Block commits that reduce coverage

2. **Add coverage to CI/CD**
   - Fail build if coverage drops below threshold
   - Generate coverage reports in CI
   - Track coverage trends over time

3. **Code review checklist**
   - Unit tests written for all new code
   - Coverage maintained or improved
   - Gaps documented if applicable

4. **Refactor for testability**
   - Extract closures into testable methods
   - Inject all dependencies
   - Keep boundary layer thin

---

## Conclusion

This plan achieves **92-95% overall coverage** by focusing on high-value unit testing of business logic while
accepting documented gaps for boundary layer classes. The approach is pragmatic, maintainable, and aligned with
software engineering best practices.

**Key Insights:**
- Not all code should be unit tested the same way
- Boundary layer classes are better integration tested
- Mock clean interfaces, not external libraries
- Focus effort where it provides value
- Document justified gaps clearly

**Success Factors:**
- Prioritize by ROI
- Test pure logic thoroughly
- Accept boundary layer gaps
- Maintain excellent documentation
- Follow Gradle 9/10 standards

This plan is achievable in 3 weeks and will result in a robust, maintainable test suite with clear justification
for all coverage gaps.

---

**Status:** Ready for review and approval
**Next Steps:** Obtain stakeholder approval and begin Week 1 implementation
