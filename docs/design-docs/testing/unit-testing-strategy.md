# Unit Testing Strategy

**Project**: Gradle Docker Plugin
**Date Created**: 2025-10-07
**Last Updated**: 2025-11-13
**Author**: Engineering Team

## Overview

This document describes the unit testing strategy for the Gradle Docker plugin, including testing patterns, coverage 
targets, architectural approach, and best practices applied throughout the codebase.

## Testing Philosophy

### Impure Shell / Pure Core Architecture

The plugin follows the **"Impure Shell / Pure Core"** architectural pattern to maximize testability:

```
┌─────────────────────────────────────────────────────────────┐
│                      Impure Shell                           │
│  (Boundary Layer - External I/O - Low Unit Test Coverage)  │
│                                                             │
│  ┌───────────────────────────────────────────────────┐    │
│  │  Service Implementations                          │    │
│  │  - DockerServiceImpl (Docker daemon I/O)         │    │
│  │  - ExecLibraryComposeService (Process execution) │    │
│  │  - ProcessExecutor (OS process spawning)         │    │
│  └───────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                            ↑
                            │ Service Interfaces (Mocked in Tests)
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                       Pure Core                             │
│   (Business Logic - 90-100% Unit Test Coverage)            │
│                                                             │
│  ┌───────────────────────────────────────────────────┐    │
│  │  Task Layer                                       │    │
│  │  DockerBuildTask, DockerPublishTask, etc.        │    │
│  └───────────────────────────────────────────────────┘    │
│  ┌───────────────────────────────────────────────────┐    │
│  │  Extension Layer                                  │    │
│  │  DockerExtension, DockerOrchExtension             │    │
│  └───────────────────────────────────────────────────┘    │
│  ┌───────────────────────────────────────────────────┐    │
│  │  Model Layer                                      │    │
│  │  EffectiveImageProperties, ImageRefParts, etc.   │    │
│  └───────────────────────────────────────────────────┘    │
│  ┌───────────────────────────────────────────────────┐    │
│  │  Spec Layer                                       │    │
│  │  ImageSpec, PublishSpec, SaveSpec, etc.          │    │
│  └───────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

**Key Principles**:

1. **Pure Core** (90-100% coverage): All business logic, validation, configuration, and data transformation
2. **Impure Shell** (15-20% coverage): Only external I/O operations (Docker daemon, OS processes, network)
3. **Service Interfaces**: Clean abstractions mocked in all business logic tests
4. **Integration Tests**: Verify boundary layer behavior with real external systems

## Coverage Targets by Layer

### High Coverage Layers (Business Logic)

| Layer | Target Coverage | Actual Coverage | Purpose |
|-------|----------------|-----------------|----------|
| **Exception** | 100% / 100% | 100% / 100% | Custom exception classes |
| **Model** | 95%+ / 90%+ | 95% / 92% | Pure domain model logic |
| **Spec** | 95%+ / 90%+ | 97% / 91% | DSL specification and validation |
| **Extension** | 85%+ / 75%+ | 80% / 76% | Gradle extension DSL |
| **Task** | 90%+ / 80%+ | 90% / 79% | Gradle task implementations |
| **Main Plugin** | 80%+ / 50%+ | 82% / 52% | Plugin entry point and wiring |
| **JUnit Extensions** | 85%+ / 75%+ | 88% / 77% | JUnit 5 test lifecycle integration |

### Low Coverage Layers (Boundary Code)

| Layer | Target Coverage | Actual Coverage | Why Low Coverage is Acceptable |
|-------|----------------|-----------------|-------------------------------|
| **Service** | 15-20% | 15% / 22% | 84% is external I/O (Docker daemon, OS processes) |

**Service Layer Breakdown**:
- **Testable Components**: JsonServiceImpl (99%), ProcessResult (100%), CommandValidator (95%) - Pure logic
- **Boundary Components**: DockerServiceImpl (30%), ExecLibraryComposeService (0%) - External I/O only

## Testing Patterns and Best Practices

### 1. Mock Service Interfaces, Not External Libraries

**DO**: Mock service interfaces in business logic tests
```groovy
def dockerService = Mock(DockerService)
def composeService = Mock(ComposeService)

dockerService.buildImage(_, _) >> CompletableFuture.completedFuture(mockResult)
```

**DON'T**: Mock external libraries (Docker Java Client, ProcessBuilder)
```groovy
// AVOID - mocking external library internals
def dockerClient = Mock(DockerClient)
def buildImageCmd = Mock(BuildImageCmd)
dockerClient.buildImageCmd() >> buildImageCmd
```

**Rationale**: Service interfaces are stable abstractions we control; external library APIs change and lead to brittle 
tests.

### 2. Use Spock for BDD-Style Testing

**Spock Framework Features Used**:

- **`where:` blocks** for parameterized testing:
```groovy
def "validates compression type #compressionType"() {
    expect:
    CompressionType.fromString(input) == expected

    where:
    input    | expected
    "gzip"   | CompressionType.GZIP
    "bzip2"  | CompressionType.BZIP2
    "none"   | CompressionType.NONE
}
```

- **`Mock()` for clean service mocking**:
```groovy
def dockerService = Mock(DockerService)
```

- **`expect:` / `then:` sections** for clear assertions:
```groovy
when:
task.buildImage()

then:
1 * dockerService.buildImage(_, _) >> CompletableFuture.completedFuture(mockResult)
```

### 3. Test Private Methods via Java Reflection

**When to Use**: Testing private helper methods that contain complex logic

**Example**:
```groovy
def "pullSourceRefIfNeeded pulls image when pullIfMissing is true and image missing"() {
    given:
    def method = DockerPublishTask.class.getDeclaredMethod('pullSourceRefIfNeeded')
    method.setAccessible(true)
    
    task.imageSpec.set(imageSpec)
    imageSpec.pullIfMissing.set(true)
    imageSpec.sourceRef.set('alpine:latest')
    
    dockerService.imageExists('alpine:latest') >> CompletableFuture.completedFuture(false)
    dockerService.pullImage('alpine:latest', null) >> CompletableFuture.completedFuture(null)
    
    when:
    method.invoke(task)
    
    then:
    1 * dockerService.imageExists('alpine:latest')
    1 * dockerService.pullImage('alpine:latest', null)
}
```

**Rationale**: Private methods often contain critical business logic that deserves explicit test coverage.

### 4. Property-Based Testing for Input Domain Coverage

**Example**: Testing all enum values
```groovy
def "fromString handles all compression types"() {
    expect:
    CompressionType.fromString(type.toString().toLowerCase()) == type
    
    where:
    type << CompressionType.values()
}
```

**Rationale**: Ensures exhaustive coverage of input domains (enums, ranges, boundary values).

### 5. Test Gradle Provider API Behavior

**Pattern**: Test lazy evaluation, property setting, and provider chaining

```groovy
def "tags property accepts provider"() {
    given:
    def tagProvider = project.provider { ['v1.0', 'v2.0'] }
    
    when:
    imageSpec.tags.set(tagProvider)
    
    then:
    imageSpec.tags.get() == ['v1.0', 'v2.0']
}
```

**Rationale**: Gradle 9/10 requires Provider API for configuration cache compatibility.

### 6. Test Edge Cases and Error Paths

**Examples**:
- Null handling
- Empty collections
- Mutual exclusivity validation
- Exception throwing paths
- Boundary conditions

```groovy
def "validateModeConsistency throws when mixing sourceRef and build properties"() {
    when:
    imageSpec.sourceRef.set('alpine:latest')
    imageSpec.buildArgs.set(['ARG1': 'value1'])
    imageSpec.validateModeConsistency()
    
    then:
    def ex = thrown(GradleException)
    ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
}
```

### 7. Use ProjectBuilder for Gradle Integration

**Pattern**: Create test Gradle projects for task testing

```groovy
def project = ProjectBuilder.builder().build()
project.pluginManager.apply(GradleDockerPlugin)

def task = project.tasks.create('testTask', DockerBuildTask)
task.dockerService.set(dockerService)
```

**Rationale**: Provides real Gradle project context for task and extension testing.

### 8. Mock CompletableFuture for Async Service Methods

**Pattern**: Service methods return `CompletableFuture<T>`

```groovy
dockerService.buildImage(_, _) >> CompletableFuture.completedFuture(mockImageId)
dockerService.imageExists(_) >> CompletableFuture.completedFuture(true)
```

**Rationale**: All service methods are async for Gradle configuration cache compatibility.

## Test Organization

### Directory Structure

```
plugin/src/test/groovy/com/kineticfire/gradle/docker/
├── exception/          # Exception class tests (100% coverage)
├── model/              # Pure domain model tests (95% coverage)
│   ├── BuildContextTest.groovy
│   ├── CompressionTypeTest.groovy
│   ├── EffectiveImagePropertiesTest.groovy
│   └── ImageRefPartsTest.groovy
├── spec/               # DSL specification tests (97% coverage)
│   ├── ImageSpecTest.groovy
│   ├── PublishSpecTest.groovy
│   └── SaveSpecTest.groovy
├── extension/          # Gradle extension tests (80% coverage)
│   ├── DockerExtensionTest.groovy
│   └── DockerOrchExtensionTest.groovy
├── task/               # Gradle task tests (90% coverage)
│   ├── DockerBuildTaskTest.groovy
│   ├── DockerPublishTaskTest.groovy
│   ├── DockerSaveTaskTest.groovy
│   └── DockerTagTaskTest.groovy
├── service/            # Service implementation tests (15% coverage - boundary layer)
│   ├── JsonServiceImplTest.groovy      # 99% coverage (pure logic)
│   ├── ProcessResultTest.groovy        # 100% coverage (data class)
│   └── DefaultCommandValidatorTest.groovy  # 95% coverage (pure validation)
├── junit/              # JUnit 5 extension tests (88% coverage)
└── GradleDockerPluginTest.groovy  # Main plugin test (82% coverage)
```

### Test Naming Conventions

**Pattern**: `<method/feature> <specific behavior>`

**Examples**:
- `"buildImage builds image with all parameters"`
- `"validateModeConsistency throws when mixing sourceRef and build properties"`
- `"fromString handles case insensitivity"`
- `"pullSourceRefIfNeeded pulls image when pullIfMissing is true and image missing"`

### Test Method Structure (Given-When-Then)

```groovy
def "descriptive test name"() {
    given: "setup test data and mocks"
    def imageSpec = new ImageSpec(project.objects)
    imageSpec.sourceRef.set('alpine:latest')
    
    when: "execute the behavior being tested"
    task.buildImage()
    
    then: "verify expected outcomes"
    1 * dockerService.buildImage(_, _)
    task.result.get() == expectedResult
}
```

## Coverage Measurement

### Tools

- **JaCoCo**: Code coverage measurement
- **Gradle Test Task**: Test execution and reporting

### Running Coverage Reports

```bash
# Run unit tests with coverage
cd plugin
./gradlew clean test

# View coverage report
open build/reports/jacoco/test/html/index.html
```

### Coverage Metrics

**Two Types of Coverage Measured**:

1. **Instruction Coverage**: Percentage of bytecode instructions executed
2. **Branch Coverage**: Percentage of decision branches executed (if/else, switch, loops)

**Target**: 90-95% instruction / 80-90% branch for business logic layers

## Documented Gaps

All coverage gaps are documented in `docs/design-docs/tech-debt/unit-test-gaps.md`:

1. **Service Layer Boundary Code** (84% of service package): External I/O operations
2. **Unreachable Defensive Code**: DockerPublishTask environment variable handling
3. **Functional Tests**: 22 tests disabled due to Gradle 9 TestKit incompatibility

**Mitigation**: All gaps covered by integration tests in `plugin-integration-test/`

## Skipped Unit Tests

The following unit tests are intentionally skipped with documented reasons and alternative coverage.

### 1. DockerServiceImplComprehensiveTest (24 tests skipped)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/DockerServiceImplComprehensiveTest.groovy`

**Reason**: Entire class disabled due to Spock framework limitation - cannot create mocks for Docker Java API command classes (BuildImageCmd, TagImageCmd, SaveImageCmd, etc.)

**Technical Details**: Docker Java API uses abstract classes with complex inheritance that Spock's mock-maker cannot handle, resulting in CannotCreateMockException

**Skipped Tests**:
1. buildImage with all nomenclature parameters
2. buildImage with multiple tags applies additional tags
3. buildImage handles DockerException
4. buildImage applies labels when provided
5. buildImage skips labels when empty map provided
6. tagImage tags all provided tags
7. tagImage handles DockerException
8. saveImage with no compression
9. saveImage with GZIP compression
10. saveImage with ZIP compression
11. saveImage with BZIP2 compression
12. saveImage with XZ compression
13. saveImage handles DockerException
14. pushImage with authentication
15. pushImage without authentication
16. pushImage handles DockerException
17. pullImage with authentication
18. pullImage without authentication
19. pullImage handles DockerException
20. imageExists returns true when image exists
21. imageExists returns false when image not found
22. imageExists returns false on other exceptions
23. close shuts down executor and client
24. close handles already shutdown executor

**Alternative Coverage**: The following active test files provide equivalent or better coverage:

| Test File | Coverage Provided |
|-----------|-------------------|
| `DockerServiceLabelsTest.groovy` | Label handling and validation (buildImage labels) |
| `DockerServiceImplPureFunctionsTest.groovy` | Pure function logic extracted from service |
| `DockerServiceImplMockabilityTest.groovy` | Mockability verification and service contracts |
| `DockerServiceFocusedTest.groovy` | Focused scenario tests with alternative mocking |
| `DockerServiceTest.groovy` | General service contract testing |
| `DockerServiceImplTest.groovy` | Service implementation testing |
| `DockerServiceExceptionTest.groovy` | Exception handling and error paths |

**Integration Coverage**: All DockerService operations (build, tag, save, push, pull) are tested in integration tests:
- `plugin-integration-test/docker/*/` - Full end-to-end Docker operations

**Resolution Options**:
1. Use manual test doubles instead of Spock mocks (requires significant refactoring)
2. Rely on integration tests for Docker API interaction (current approach)
3. Extract more pure logic from service implementations for unit testing (ongoing)

**Status**: ✅ Acceptable - Alternative coverage is comprehensive

### 2. DockerfilePathResolverTest - Windows Path Test (1 test skipped)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/util/DockerfilePathResolverTest.groovy`

**Test**: `validateDockerfileLocation handles Windows-style paths`

**Reason**: Platform-specific test that only runs on Windows OS

**Skip Condition**: Test is skipped when `System.getProperty('os.name')` does not contain 'windows'

**Alternative Coverage**: Unix/Linux path handling is tested by other tests in the same file:
- `validateDockerfileLocation succeeds for valid paths` - Tests Unix path validation
- `validateDockerfileLocation throws exception when Dockerfile outside context` - Tests path security on Unix
- `validateDockerfileLocation throws exception when Dockerfile at parent level` - Tests path traversal on Unix

**Integration Coverage**: Windows path handling is tested manually on Windows CI/CD pipeline

**Status**: ✅ Acceptable - Platform-specific behavior correctly isolated

### Summary

**Total Skipped Unit Tests**: 25
- **DockerServiceImplComprehensiveTest**: 24 tests (Spock mocking limitation)
- **DockerfilePathResolverTest**: 1 test (platform-specific)

**Impact on Coverage**: Minimal - All skipped functionality has equivalent coverage through:
1. Alternative unit test files (7 DockerService test files)
2. Integration tests (`plugin-integration-test/`)
3. Platform-specific testing (Windows CI/CD)

## Integration with CI/CD

### Build Pipeline Checks

```bash
# Pre-commit checks
./gradlew clean test

# Full build with coverage
./gradlew clean build

# Integration test verification
cd ../plugin-integration-test
./gradlew cleanAll integrationTest
```

### Coverage Thresholds

**Enforced Minimums** (per layer):
- Business logic layers: ≥ 80% instruction coverage
- Overall project: ≥ 75% instruction coverage

## Common Testing Scenarios

### Scenario 1: Testing a New Task

```groovy
class NewDockerTaskTest extends Specification {
    Project project
    NewDockerTask task
    DockerService dockerService
    
    def setup() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply(GradleDockerPlugin)
        
        task = project.tasks.create('testTask', NewDockerTask)
        dockerService = Mock(DockerService)
        task.dockerService.set(dockerService)
    }
    
    def "task executes expected behavior"() {
        given:
        // Setup test data
        
        when:
        task.execute()
        
        then:
        // Verify service calls and outcomes
    }
}
```

### Scenario 2: Testing Validation Logic

```groovy
def "validateConfiguration throws when required property missing"() {
    when:
    spec.requiredProperty.set(null)
    spec.validateConfiguration()
    
    then:
    def ex = thrown(GradleException)
    ex.message.contains("requiredProperty must be specified")
}
```

### Scenario 3: Testing Provider API

```groovy
def "property accepts provider and evaluates lazily"() {
    given:
    def provider = project.provider { 'lazy-value' }
    
    when:
    spec.property.set(provider)
    
    then:
    spec.property.get() == 'lazy-value'
}
```

### Scenario 4: Testing Equals/HashCode

```groovy
def "equals returns true for same values"() {
    given:
    def obj1 = new BuildContext(path1, dockerfile1, args1)
    def obj2 = new BuildContext(path1, dockerfile1, args1)
    
    expect:
    obj1 == obj2
    obj1.hashCode() == obj2.hashCode()
}
```

## Future Improvements

### Planned Enhancements

1. **Mutation Testing**: Add PIT mutation testing to validate test quality
2. **Property-Based Testing**: Expand use of property-based testing for domain models
3. **TestKit Re-enablement**: Re-enable functional tests when Gradle fixes TestKit compatibility
4. **Service Layer Refactoring**: Extract more pure logic from service implementations

### Continuous Improvement

- Review coverage reports quarterly
- Add tests for all bug fixes
- Update this document with new patterns as they emerge

## References

- **Coverage Reports**: `plugin/build/reports/jacoco/test/html/index.html`
- **Coverage Gaps**: `docs/design-docs/tech-debt/unit-test-gaps.md`
- **Project Standards**:
  - Unit Testing: `docs/project-standards/testing/unit-testing.md`
  - Code Style: `docs/project-standards/style/style.md`
- **Integration Tests**: `plugin-integration-test/README.md`

## Conclusion

This unit testing strategy emphasizes:
- **High coverage** of business logic (90-100%)
- **Accepting low coverage** of boundary layer (15-20%)
- **Clean abstractions** via service interfaces
- **Integration tests** for external I/O validation
- **Best practices** from Spock, Gradle, and clean architecture

The result is a maintainable, well-tested codebase with clear separation between pure logic (unit tested) and external 
I/O (integration tested).
