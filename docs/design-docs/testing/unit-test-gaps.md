# Unit Test Coverage Gaps

This document tracks areas of the codebase that cannot achieve 100% unit test coverage and explains why.

## Spock Extension - Interceptor Classes

### Classes Affected
- `com.kineticfire.gradle.docker.spock.ComposeClassInterceptor`
- `com.kineticfire.gradle.docker.spock.ComposeMethodInterceptor`

### Reason for Gap
These interceptor classes have characteristics that make them extremely difficult to unit test effectively:

1. **ThreadLocal State Management**: The interceptors use ThreadLocal variables to maintain state across test
   lifecycle methods (setup/cleanup). This pattern is inherently difficult to test in isolation because:
   - Thread-local state is implicitly bound to the test execution thread
   - Mocking thread-local behavior requires complex test infrastructure
   - Race conditions and timing issues are difficult to reproduce

2. **Complex Async Operations**: Heavy use of `CompletableFuture` for orchestrating Docker Compose operations:
   - Multiple async operations that must complete in specific orders
   - Error handling and cleanup paths involve chained futures
   - Test doubles for futures add significant complexity without adding value

3. **External Process Dependencies**: Direct interaction with:
   - Docker daemon via ComposeService
   - Docker CLI via ProcessExecutor
   - Filesystem for state file management
   - These require extensive mocking that mirrors implementation details rather than testing behavior

4. **Tight Coupling to Spock Runtime**: The interceptors interact deeply with Spock's internal runtime model:
   - `IMethodInvocation` interface
   - `MethodInfo` and `FeatureInfo` classes
   - `MethodKind` enumeration
   - Mocking these classes creates brittle tests that break when Spock internals change

### Test Coverage Alternative
While unit tests are not practical for these classes, they receive comprehensive coverage through:

1. **Integration Tests**: The integration test suite in `plugin-integration-test/dockerOrch/` exercises these
   interceptors in real-world scenarios:
   - Actual Docker Compose stack orchestration
   - Real container lifecycle management (up/down/health checks)
   - Actual file I/O and process execution
   - Both CLASS and METHOD lifecycle modes

2. **Functional Tests**: The functional test suite validates the Spock extension behavior end-to-end with actual test
   execution.

### Justification
The project standards document (`docs/project-standards/testing/unit-testing.md`) states:
> "If 100% is not possible (e.g., due to external service calls), document the gap in the gap file."

These interceptor classes fall into this category. Their value is in coordinating external services (Docker, Compose,
filesystem), which is better validated through integration testing than through complex mocking.

### Mitigation
To minimize this gap, the `DockerComposeSpockExtension` class (which creates and configures the interceptors) has 100%
unit test coverage with 28 comprehensive test cases covering:
- Annotation validation
- Configuration creation
- Lifecycle interceptor registration
- Project name generation and sanitization
- All edge cases and error conditions

This ensures the configuration and setup logic is thoroughly tested, while the runtime orchestration is validated
through integration tests.

## DockerPublishTask - Empty Target References Edge Case

### Classes Affected
- `com.kineticfire.gradle.docker.task.DockerPublishTask`

### Reason for Gap
The code path where `buildTargetImageReferences()` returns an empty list cannot be reached in unit tests due to the
inheritance mechanism in `EffectiveImageProperties`. Specifically:

1. **Property Inheritance**: When `EffectiveImageProperties.parseFromDirectSourceRef()` parses any sourceRef
   (including digest-only references like 'sha256:abc123'), it extracts components that get inherited by targets
   via `applyTargetOverrides()`.

2. **Automatic Fallbacks**: The system has multiple fallback mechanisms:
   - Empty publishTags inherits from source tags
   - Empty target imageName inherits from parsed sourceRef components
   - Default tags are applied when none are specified

3. **Architecture Design**: The inheritance-based design makes it architecturally impossible to create a scenario
   where a valid sourceRef exists but no targetRefs can be built, because any parseable sourceRef will provide
   inheritable properties.

### Test Coverage Alternative
While this specific edge case cannot be unit tested, related scenarios are covered:
- The warning message and skip logic exist in the code at DockerPublishTask.groovy:217-220
- Integration tests validate real-world publish scenarios with various target configurations
- All other branches in `buildTargetImageReferences()` have 100% unit test coverage

### Justification
This edge case represents an architecturally impossible state in the current implementation. Attempting to force
it through complex mocking would test artificial scenarios that cannot occur in practice.

### Mitigation
- All reachable code paths in `buildTargetImageReferences()` have 100% unit test coverage
- The DockerPublishTask test suite has 56 tests covering all practical scenarios
- Integration tests validate actual publish workflows with real Docker registries

## JUnit Service Package - Boundary Layer Classes

### Classes Affected
- `com.kineticfire.gradle.docker.junit.service.DefaultFileService`
- `com.kineticfire.gradle.docker.junit.service.DefaultProcessExecutor`
- `com.kineticfire.gradle.docker.junit.service.DefaultSystemPropertyService`
- `com.kineticfire.gradle.docker.junit.service.DefaultTimeService`

### Reason for Gap
These classes are thin boundary layer wrappers around JDK APIs with no business logic:
- **DefaultFileService**: Direct delegation to `java.nio.file.Files.*` and `java.nio.file.Paths.*`
  - 100% coverage achieved through comprehensive tests (56 test cases)
  - All methods tested with real filesystem operations
- **DefaultProcessExecutor**: Direct delegation to `java.lang.ProcessBuilder`
  - ~98% coverage achieved through comprehensive tests (32 test cases)
  - All methods tested with real process execution
  - Remaining gaps are extreme edge cases (e.g., system-specific IO failures)
- **DefaultSystemPropertyService**: Direct delegation to `System.getProperty/setProperty`
  - 100% coverage achieved through comprehensive tests (26 test cases)
  - All methods tested with real system properties
- **DefaultTimeService**: Direct delegation to `LocalDateTime.now()` and `Thread.sleep()`
  - ~95% coverage achieved through comprehensive tests (17 test cases)
  - All methods tested with real time operations

### Test Coverage Strategy
These classes follow the "impure shell, pure core" pattern from the project standards. They serve as test seams to
enable unit testing of higher-level business logic (like `JUnitComposeService`). The comprehensive test suites verify:
1. Correct delegation to JDK APIs
2. Exception propagation
3. Edge cases and boundary conditions
4. Integration with actual system resources

### Justification
Per project standards (`docs/project-standards/testing/unit-testing.md`), these are "boundary functions" where "side
effects (I/O, clock, randomness, OS, network, DB) [are isolated] behind tiny boundary functions." Their purpose is to
provide mockable interfaces for higher-level code.

While these tests exercise real system operations (filesystem, processes, system properties, time), they are still
considered unit tests because:
1. They test single classes in isolation
2. They use controlled test environments (temp directories, test properties)
3. They verify correct behavior of the abstraction layer
4. They enable true unit testing of business logic through dependency injection

### Coverage Statistics
- **DefaultFileService**: 100% instruction, 100% branch (56 tests)
- **DefaultProcessExecutor**: ~98% instruction, ~97% branch (32 tests)
- **DefaultSystemPropertyService**: 100% instruction, 100% branch (26 tests)
- **DefaultTimeService**: ~95% instruction, ~93% branch (17 tests)
- **JUnitComposeService**: ~98% instruction, ~97% branch (103 tests)

### Total Package Coverage
- **Overall Package**: ~98% instruction, ~97% branch (234 total tests)

## Last Updated
2025-10-13
