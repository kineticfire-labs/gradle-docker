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

## Last Updated
2025-10-12
