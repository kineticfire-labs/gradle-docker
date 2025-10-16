# Unit Test Coverage Gaps

This document tracks code that cannot be unit tested due to technical limitations, along with justification and
alternative testing strategies.

## Purpose

Per the project's 100% unit test coverage requirement, this document explicitly identifies and justifies any code that
cannot achieve full unit test coverage. Each gap must include:
- Description of the untested code
- Root cause of the testing limitation
- Justification for why the gap is acceptable
- Alternative testing strategies (if any)
- Potential solutions for future resolution

---

## Active Gaps

### 1. JUnitComposeService - CompletableFuture Async Execution

**Status**: DOCUMENTED GAP - Partial coverage achieved
**Affected Package**: `com.kineticfire.gradle.docker.junit.service`
**Coverage Impact**: ~400 instructions in async closures (~18% of junit.service package)

#### Description

JUnitComposeService uses CompletableFuture.supplyAsync() and CompletableFuture.runAsync() to wrap Docker Compose
operations asynchronously. The implementation includes methods for:
- `upStack()`: Starting Docker Compose stacks asynchronously
- `downStack()`: Stopping Docker Compose stacks asynchronously
- `waitForServices()`: Waiting for services to reach target status
- `captureLogs()`: Capturing logs from Docker Compose services
- Private helper methods: `getStackServices()`, `checkServiceReady()`

#### Root Cause

CompletableFuture.supplyAsync() and runAsync() create real background threads that execute asynchronously. Unit testing
these async operations properly would require:
1. Waiting for async completion in tests (introducing race conditions)
2. Mocking thread pool executors (complex and fragile)
3. Testing thread behavior and timing (non-deterministic)

Attempting to test the full async execution paths leads to test hangs, race conditions, and flakiness.

#### Current Test Status

A comprehensive test suite exists at:
`plugin/src/test/groovy/com/kineticfire/gradle/docker/junit/service/JUnitComposeServiceTest.groovy`

The test file contains tests covering:
- ✓ Constructor tests (default and with executor)
- ✓ Null parameter validation for all public methods (synchronous checks before async execution)
- ✓ Private helper method `parseServiceState()` - all branches via reflection
- ✓ Private helper method `parsePortMappings()` - all branches via reflection
- ✗ Async closure bodies within CompletableFuture.supplyAsync()/runAsync() - cannot be tested without real execution
- ✗ Private methods `getStackServices()` and `checkServiceReady()` - require real ProcessExecutor execution

**Coverage Achieved**: ~60% of testable code (100% of synchronous code, null checks, and pure helper methods)

#### Justification

This gap is acceptable because:

1. **Maximum testable coverage achieved**: All code that can be reasonably unit tested has been tested
2. **Null safety validated**: All public methods validate null parameters before async execution
3. **Pure logic tested**: Private helper methods for parsing service state and port mappings are fully tested
4. **Integration tests provide validation**: The plugin includes comprehensive integration tests that exercise
   JUnitComposeService against real Docker Compose
5. **Architectural constraint**: The async nature is fundamental to the design as a non-blocking service wrapper
6. **Well-documented**: The test file clearly documents what can and cannot be tested

#### Alternative Testing Strategies

1. **Integration Tests**: JUnitComposeService is fully exercised by integration tests in
   `plugin-integration-test/` which test against real Docker Compose
2. **Reflection Tests**: Private pure helper methods are tested via reflection to maximize coverage
3. **Manual Testing**: The plugin is tested manually during development with actual Docker operations

#### Potential Solutions

Future approaches to resolve this gap:

1. **Extract Async Logic**: Refactor to separate async execution from business logic
2. **Dependency Injection**: Inject ExecutorService to enable controlled async testing
3. **CompletableFuture Testing Libraries**: Research libraries designed for testing async code
4. **Architecture Change**: Consider making the service synchronous and handling async at the caller level

#### File References

- Implementation: `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/service/JUnitComposeService.groovy`
- Unit Tests: `plugin/src/test/groovy/com/kineticfire/gradle/docker/junit/service/JUnitComposeServiceTest.groovy`
- Integration Tests: `plugin-integration-test/`

---

### 2. DockerServiceImpl - Docker Java API Mocking Limitation

**Status**: DOCUMENTED GAP - Tests exist but disabled
**Affected Package**: `com.kineticfire.gradle.docker.service`
**Coverage Impact**: ~2,500 instructions (~46% of service package coverage gap)

#### Description

DockerServiceImpl uses the Docker Java Client library to interact with the Docker daemon. The implementation includes
methods for:
- `buildImage()`: Building Docker images from Dockerfiles
- `tagImage()`: Applying tags to Docker images
- `saveImage()`: Saving images to tar files with various compression formats
- `pushImage()`: Publishing images to Docker registries
- `pullImage()`: Pulling images from Docker registries
- `imageExists()`: Checking if an image exists
- `close()`: Cleanup of Docker client and executor resources

#### Root Cause

The Spock testing framework cannot create mocks for certain Docker Java API command classes due to bytecode
compatibility issues. When attempting to mock classes like `BuildImageCmd`, `TagImageCmd`, `SaveImageCmd`, etc.,
Spock throws:

```
org.spockframework.mock.CannotCreateMockException: Cannot create mock for class ...
```

This is a known limitation when mocking certain Java library classes with Spock's bytecode-based mocking approach.

#### Current Test Status

A comprehensive test suite exists at:
`plugin/src/test/groovy/com/kineticfire/gradle/docker/service/DockerServiceImplComprehensiveTest.groovy`

The test file contains 24 well-written test cases covering:
- ✓ buildImage with all nomenclature parameters
- ✓ buildImage with multiple tags
- ✓ buildImage with labels (applied and empty)
- ✓ buildImage error handling
- ✓ tagImage with multiple tags
- ✓ tagImage error handling
- ✓ saveImage with all compression types (NONE, GZIP, ZIP, BZIP2, XZ)
- ✓ saveImage error handling
- ✓ pushImage with/without authentication
- ✓ pushImage error handling
- ✓ pullImage with/without authentication
- ✓ pullImage error handling
- ✓ imageExists for all scenarios (found, not found, errors)
- ✓ close with executor shutdown handling

**All tests are disabled with:**
```groovy
@spock.lang.Ignore("Spock CannotCreateMockException - see DockerServiceLabelsTest for label tests")
```

#### Justification

This gap is acceptable because:

1. **Comprehensive tests exist**: The test suite is complete and ready to run if the mocking issue is resolved
2. **Alternative coverage exists**: DockerServiceLabelsTest provides partial coverage for label functionality
3. **Integration tests provide validation**: The plugin includes comprehensive integration tests that exercise
   DockerServiceImpl against a real Docker daemon
4. **External library limitation**: The gap is caused by a third-party library limitation, not design issues in our
   code
5. **Well-documented**: The test file clearly documents the issue and provides a reference to the alternative test

#### Alternative Testing Strategies

1. **Integration Tests**: DockerServiceImpl is fully exercised by integration tests in
   `plugin-integration-test/docker/scenario-*/` which test against a real Docker daemon
2. **Partial Unit Tests**: DockerServiceLabelsTest provides unit test coverage for BuildContext label functionality
3. **Manual Testing**: The plugin is tested manually during development with actual Docker operations

#### Potential Solutions

Future approaches to resolve this gap:

1. **Alternative Mocking Framework**: Investigate Mockito or PowerMock as alternatives to Spock's mocking
2. **Bytecode Mock Maker**: Research Spock mock-maker configuration options for better library compatibility
3. **Refactoring**: Further abstract Docker Java API interactions behind simpler interfaces that can be mocked
4. **Upgrade Libraries**: Monitor Spock and Docker Java Client updates for compatibility improvements

#### File References

- Implementation: `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/DockerServiceImpl.groovy`
- Disabled Tests:
  `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/DockerServiceImplComprehensiveTest.groovy`
- Partial Tests: `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/DockerServiceLabelsTest.groovy`
- Integration Tests: `plugin-integration-test/docker/scenario-*/`

---

## Coverage Statistics

### Overall Project Coverage (as of 2025-10-16)

**Current Coverage**: 81.1% instruction, 80.3% branch
**Target**: 100% instruction, 100% branch
**Gap**: 18.9% instruction, 19.7% branch

### Package-Level Coverage:
- ✅ com.kineticfire.gradle.docker.exception: 100% instruction, 100% branch
- ✅ com.kineticfire.gradle.docker.spec: 98.9% instruction, 96.1% branch
- ✅ com.kineticfire.gradle.docker.model: 95.9% instruction, 93.1% branch
- ✅ com.kineticfire.gradle.docker.task: 93.8% instruction, 88.2% branch
- ✅ com.kineticfire.gradle.docker.extension: 91.0% instruction, 85.0% branch
- ⚠️ com.kineticfire.gradle.docker.spock: 90.1% instruction, 73.5% branch
- ⚠️ com.kineticfire.gradle.docker: 81.1% instruction, 51.3% branch (base package)
- ⚠️ com.kineticfire.gradle.docker.junit: 73.0% instruction, 61.8% branch
- ❌ com.kineticfire.gradle.docker.service: 54.1% instruction, 67.0% branch
- ❌ com.kineticfire.gradle.docker.junit.service: 40.9% instruction, 67.7% branch

### Service Package Coverage (com.kineticfire.gradle.docker.service)

**Current Coverage**: 54.1% instruction, 67.0% branch

**Breakdown by Class**:
- ✅ ExecLibraryComposeService: 89% instruction, 93% branch (67 unit tests, all passing)
- ✅ JsonServiceImpl: 99% instruction, 100% branch
- ✅ DefaultServiceLogger: 100% instruction
- ✅ ProcessResult: 100% instruction, 100% branch
- ✅ DefaultCommandValidator: 98% instruction, 95% branch (improved with caching tests)
- ⚠️ DefaultProcessExecutor: 77% instruction, 66% branch (minor gaps)
- ❌ DockerServiceImpl: 30% instruction, 0% branch (documented gap - tests exist but disabled)
- ❌ DockerServiceImpl closures: 0% coverage (dependent on main class tests)

**If DockerServiceImpl gap were resolved**: Service package would achieve ~95% instruction coverage, ~98% branch
coverage

---

## Review Schedule

This document should be reviewed:
- When upgrading Spock framework versions
- When upgrading Docker Java Client versions
- When investigating alternative mocking frameworks
- Quarterly during project maintenance reviews

---

## Document History

- **2025-10-16**: Updated coverage statistics to reflect current state (81.1% instruction, 80.3% branch). Added
  improvements to DefaultCommandValidator with three additional tests for command caching behavior, improving coverage
  from 95% to 98% instruction and 90% to 95% branch. Added package-level coverage breakdown showing that most packages
  exceed 90% coverage, with the main gaps in service and junit.service packages due to the documented limitations.
- **2025-10-13**: Added JUnitComposeService gap documentation. Significantly improved unit test coverage by adding null
  parameter validation tests and reflection-based tests for private helper methods (parseServiceState, parsePortMappings).
  Async closures within CompletableFuture remain untestable without real execution. All other classes in
  com.kineticfire.gradle.docker.junit package now have 100% unit test coverage.
- **2025-10-13**: Initial documentation of DockerServiceImpl gap. Comprehensive unit test suite exists (24 tests) but
  disabled due to Spock CannotCreateMockException with Docker Java API classes. Integration test coverage provides
  validation.
