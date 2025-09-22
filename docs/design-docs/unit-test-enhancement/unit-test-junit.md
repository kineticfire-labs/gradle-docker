# Unit Test Enhancement Plan: com.kineticfire.gradle.docker.junit Package

## Executive Summary

This document outlines a comprehensive plan to achieve 100% code and branch coverage for the `com.kineticfire.gradle.docker.junit` package, which currently has 0% coverage. The package contains two JUnit 5 extension classes that manage Docker Compose lifecycle for integration testing.

## Current State Analysis

### Coverage Metrics
- **Overall Package Coverage**: 0% (completely untested)
- **Total Missed Instructions**: 2,297 out of 2,313
- **Total Missed Branches**: 132 out of 132
- **Untested Methods**: 36 out of 38

### Classes Under Analysis

#### DockerComposeMethodExtension
- **Coverage**: 0%
- **Missed Instructions**: 1,161
- **Missed Branches**: 66
- **Purpose**: Manages Docker Compose lifecycle per test method (fresh containers for each test)

#### DockerComposeClassExtension
- **Coverage**: 0%
- **Missed Instructions**: 1,136
- **Missed Branches**: 66
- **Purpose**: Manages Docker Compose lifecycle per test class (shared containers across methods)

## Code Categorization

### Category 1: Code with External Dependencies

#### 1a. Instructions with External Calls
These instructions directly interact with external systems and cannot be unit tested without mocking:

- **Docker/Docker Compose Operations**
  - ProcessBuilder calls to `docker` and `docker compose` commands
  - Process execution and `waitFor()` operations
  - Process output stream reading

- **File System Operations**
  - `Files.exists()` - checking file existence
  - `Files.createDirectories()` - creating directory structures
  - `Files.writeString()` - writing state files
  - `Files.delete()` - cleaning up state files
  - `Files.list()` - listing directory contents
  - `Paths.get()` - path resolution

- **System Operations**
  - `System.getProperty()` - reading system properties
  - `System.setProperty()` - setting system properties
  - `Thread.sleep()` - waiting operations
  - `LocalDateTime.now()` - current time retrieval

#### 1b. Branches with External Dependencies
These conditional branches depend on external system responses:

- Process exit code checks (exitCode != 0)
- File existence conditions
- Container health check loops
- Error handling for process failures
- Cleanup exception handling branches
- Timeout and retry logic branches

### Category 2: Pure Logic Code (Directly Testable)

These methods contain pure business logic without external dependencies:

- **String Manipulation**
  - `sanitizeProjectName()` - converts strings to valid Docker Compose project names
  - `capitalize()` - capitalizes first character of strings
  - String formatting and concatenation logic

- **Data Generation**
  - `generateUniqueProjectName()` - creates unique project names (ExtensionContext can be mocked)
  - JSON state file content generation
  - Command argument construction

- **State Management**
  - ThreadLocal operations
  - Constructor initialization
  - Field management

## Refactoring Strategy for Testability

### Phase 1: Create Service Abstractions

#### 1.1 ProcessExecutor Interface
```java
public interface ProcessExecutor {
    ProcessResult execute(String... command);
    ProcessResult executeWithTimeout(int timeout, TimeUnit unit, String... command);
    ProcessResult executeInDirectory(File directory, String... command);
}
```

#### 1.2 FileService Interface
```java
public interface FileService {
    boolean exists(Path path);
    void createDirectories(Path path) throws IOException;
    void writeString(Path path, String content) throws IOException;
    void delete(Path path) throws IOException;
    Stream<Path> list(Path dir) throws IOException;
}
```

#### 1.3 SystemPropertyService Interface
```java
public interface SystemPropertyService {
    String getProperty(String key);
    String getProperty(String key, String defaultValue);
    void setProperty(String key, String value);
}
```

#### 1.4 TimeService Interface
```java
public interface TimeService {
    LocalDateTime now();
    void sleep(long millis) throws InterruptedException;
}
```

### Phase 2: Refactor Extensions to Use Dependency Injection

#### 2.1 Add Service Dependencies
- Modify constructors to accept service interfaces
- Provide no-argument constructors for backward compatibility
- Use default service implementations in no-arg constructors

#### 2.2 Extract External Calls
- Replace all ProcessBuilder usage with ProcessExecutor
- Replace all Files static calls with FileService
- Replace System property access with SystemPropertyService
- Replace time operations with TimeService

### Phase 3: Comprehensive Unit Test Implementation

#### 3.1 Test Categories

**Pure Logic Tests (100% achievable)**
- Test `sanitizeProjectName()` with various input patterns
  - Valid names (unchanged)
  - Invalid characters (replacement)
  - Leading/trailing hyphens (removal)
  - Empty/null inputs (edge cases)

- Test `capitalize()` method
  - Normal strings
  - Empty strings
  - Single character strings
  - Null inputs

**Orchestration Logic Tests (with mocks)**
- Test `beforeEach()`/`afterEach()` lifecycle
  - Successful startup scenarios
  - Startup failure with cleanup
  - Various exception handling paths

- Test `beforeAll()`/`afterAll()` lifecycle
  - Normal execution flow
  - Cleanup after failures
  - ThreadLocal management

**Error Handling Tests**
- Simulate process failures (non-zero exit codes)
- Simulate file system errors
- Simulate timeout scenarios
- Test cleanup resilience

**Configuration Tests**
- Test with missing system properties
- Test with various property values
- Test default value fallbacks

#### 3.2 Mock Scenarios

**Process Execution Mocks**
- Successful docker compose up/down
- Failed process execution
- Timeout scenarios
- Different exit codes

**File System Mocks**
- File exists/not exists
- Directory creation success/failure
- Write operations success/failure
- Cleanup scenarios

**System Property Mocks**
- Properties present/absent
- Various property values
- Default value usage

### Phase 4: Coverage Gap Documentation

Any code that absolutely cannot be unit tested will be documented in `docs/design-docs/testing/unit-test-gaps.md` with:
- Specific code location
- Reason why it cannot be tested
- Risk assessment
- Mitigation strategy (integration/functional tests)

## Implementation Plan

### Step 1: Create Service Interfaces (Week 1)
1. Define all service interfaces in `com.kineticfire.gradle.docker.junit.service` package
2. Create default implementations using existing code
3. Ensure interfaces cover all external operations

### Step 2: Refactor Extensions (Week 1-2)
1. Add service dependencies to extension constructors
2. Extract all external calls to use services
3. Maintain backward compatibility
4. Verify functionality with existing integration tests

### Step 3: Write Unit Tests (Week 2-3)
1. Create test classes for both extensions
2. Implement tests for pure logic methods (immediate 15-20% coverage)
3. Implement orchestration tests with mocks (additional 60-70% coverage)
4. Implement error handling tests (remaining 10-25% coverage)

### Step 4: Achieve 100% Coverage (Week 3-4)
1. Run coverage analysis after each test addition
2. Identify remaining uncovered branches
3. Add specific test cases for edge conditions
4. Document any truly untestable code

## Expected Outcomes

### Coverage Targets
- **Line Coverage**: 100%
- **Branch Coverage**: 100%
- **Method Coverage**: 100%

### Quality Improvements
- Increased code maintainability through dependency injection
- Better separation of concerns
- Improved testability for future changes
- Comprehensive test suite as documentation

### Risk Mitigation
- All error paths tested
- Edge cases covered
- Cleanup logic verified
- Thread safety validated

## Testing Tools and Technologies

### Required Dependencies
- JUnit 5 (already in use)
- Mockito for mocking services
- AssertJ for fluent assertions
- JaCoCo for coverage reporting

### Testing Patterns
- Arrange-Act-Assert pattern
- Given-When-Then for behavior tests
- Builder pattern for test data
- Parameterized tests for edge cases

## Success Criteria

1. **100% code coverage** as measured by JaCoCo
2. **100% branch coverage** for all decision points
3. **All methods tested** including private methods via reflection if needed
4. **No untested code** without explicit documentation in gaps file
5. **All tests pass** consistently without flakiness
6. **Tests run quickly** (< 5 seconds for entire package)

## Maintenance Considerations

### Long-term Sustainability
- Service interfaces allow easy mocking for new tests
- Dependency injection pattern supports future extensions
- Clear separation between logic and external calls
- Comprehensive test suite prevents regression

### Documentation Requirements
- Each test method should clearly document what it tests
- Complex test scenarios need inline comments
- Coverage gaps must be documented with justification
- Update this plan as implementation progresses

## Conclusion

This plan provides a clear path to achieve 100% unit test coverage for the `com.kineticfire.gradle.docker.junit` package. By introducing service abstractions and dependency injection, we can effectively mock all external dependencies while maintaining the original functionality. The phased approach ensures systematic progress toward complete coverage while maintaining code quality and backward compatibility.