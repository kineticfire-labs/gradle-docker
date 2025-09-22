# Unit Test Enhancement Plan: com.kineticfire.gradle.docker.junit Package (Part 2)

## Overview

This document outlines the comprehensive plan to achieve 100% unit test coverage for the `com.kineticfire.gradle.docker.junit` package. The plan categorizes untested code, identifies refactoring opportunities, and provides a structured approach to reach complete coverage.

## Current State Assessment

### Source Files (10 total)
1. `DockerComposeClassExtension.java` (482 lines) - JUnit 5 class-level extension
2. `DockerComposeMethodExtension.java` (485 lines) - JUnit 5 method-level extension
3. **Service Interfaces (4):**
   - `ProcessExecutor.java` (116 lines) - Process execution interface + ProcessResult inner class
   - `TimeService.java` (43 lines) - Time operations interface
   - `SystemPropertyService.java` (51 lines) - System property access interface
   - `FileService.java` (90 lines) - File system operations interface
4. **Service Implementations (4):**
   - `DefaultProcessExecutor.java` (99 lines) - Process execution implementation
   - `DefaultTimeService.java` (44 lines) - Time service implementation
   - `DefaultSystemPropertyService.java` (47 lines) - System property implementation
   - `DefaultFileService.java` (74 lines) - File service implementation

### Existing Tests (2 total)
- `DockerComposeClassExtensionTest.groovy` - Covers the class extension
- `DockerComposeMethodExtensionTest.groovy` - Covers the method extension

## Coverage Gap Analysis

### Category 1: Code with External Dependencies (Docker/Process/File System)

#### 1a. Completely Untested Code with External Dependencies

**DockerComposeClassExtension.java:**
- `cleanupExistingContainers()` (lines 233-267) - Multiple docker command execution paths
- `startComposeStack()` (lines 269-293) - Docker compose up with error handling
- `stopComposeStack()` (lines 295-312) - Docker compose down with error handling
- `waitForStackToBeReady()` (lines 314-349) - Health check timeout scenarios
- `forceRemoveContainersByName()` (lines 409-474) - Container removal by label

**DockerComposeMethodExtension.java:**
- Same methods as above but with different compose file paths

#### 1b. Partially Tested Code with Coverage Gaps

**Both Extension Classes:**
- **Error handling branches**: Missing coverage for failure scenarios during:
  - Docker compose file not found
  - Docker commands returning non-zero exit codes
  - Process execution timeouts
  - IOException during state file operations
- **Edge cases in sanitizeProjectName()**: Missing coverage for:
  - Names starting with non-alphanumeric characters
  - Multiple consecutive special characters
  - Very long project names

### Category 2: Code That Should Have Unit Tests (No External Dependencies)

#### 2a. Completely Missing Tests (0% coverage)

**Service Implementations:**
1. **DefaultProcessExecutor.java** - No tests exist
   - All methods: `execute()`, `executeWithTimeout()`, `executeInDirectory()`, `executeInDirectoryWithTimeout()`
   - `readProcessOutput()` private method

2. **DefaultTimeService.java** - No tests exist
   - `now()` and `sleep()` methods

3. **DefaultSystemPropertyService.java** - No tests exist
   - `getProperty()` (2 overloads) and `setProperty()` methods

4. **DefaultFileService.java** - No tests exist
   - All 8 methods: `exists()`, `createDirectories()`, `writeString()`, `delete()`, `list()`, `resolve()`, `toFile()`

**Service Interfaces:**
5. **ProcessExecutor.ProcessResult** inner class - No tests exist
   - Constructor and getter methods

#### 2b. Existing Tests with Coverage Gaps

**DockerComposeClassExtension.java:**
- Missing test scenarios:
  - `beforeAll()` with startup failure and cleanup failure
  - `afterAll()` with null project name scenario
  - `waitForStackToBeReady()` timeout scenarios (maxAttempts reached)
  - `sanitizeProjectName()` edge cases mentioned above
  - `cleanupStateFile()` with file deletion failures

**DockerComposeMethodExtension.java:**
- Missing test scenarios similar to class extension
- Additional: `generateUniqueProjectName()` with method name edge cases

## Refactoring Opportunities for Better Testability

### 1. External Dependencies That Could Use Mocks

**Current Status**: The extension classes already use dependency injection with service interfaces, making them highly testable with mocks. No refactoring needed here.

**Services That Need Mock-Based Testing**: All the Default* service implementations can be tested using mocks for their external dependencies:

- `DefaultProcessExecutor` - Mock `ProcessBuilder` and `Process`
- `DefaultTimeService` - Mock `LocalDateTime.now()` and `Thread.sleep()`
- `DefaultSystemPropertyService` - Mock `System.getProperty()`/`System.setProperty()`
- `DefaultFileService` - Mock `Files` class static methods

### 2. Implementation Refactoring for Better Testability

**Minimal Refactoring Needed**: The code is already well-designed for testing. However, some minor improvements could help:

#### DefaultProcessExecutor.java:
- Extract `ProcessBuilder` creation to a protected method to allow easier mocking in tests
- Consider making `readProcessOutput()` package-private instead of private for direct testing

#### Extension Classes:
- Extract constants for magic numbers (like sleep times, max attempts) to make them configurable in tests
- Consider extracting the complex container cleanup logic into smaller, more focused methods

## Implementation Plan for 100% Coverage

### Success Criteria
1. **All unit tests must pass** - No failing tests allowed
2. **The plugin must build successfully** - Clean build with no compilation errors
3. **No warnings should be generated** - Zero compilation, test, or build warnings

### Phase 1: Service Implementation Tests (Highest Priority)

Create comprehensive unit tests for all 4 Default* service classes:

#### 1.1 DefaultProcessExecutorTest
- **Coverage Target**: 100% line and branch coverage
- **Test Strategy**: Mock ProcessBuilder/Process interactions
- **Key Test Cases**:
  - All public method variants with different parameter combinations
  - Process timeout scenarios
  - IOException handling during process execution
  - Process output reading with various output formats
  - Working directory specification

#### 1.2 DefaultTimeServiceTest
- **Coverage Target**: 100% line and branch coverage
- **Test Strategy**: Mock static method calls
- **Key Test Cases**:
  - `now()` method returns current time
  - `sleep()` method delegates to Thread.sleep()
  - InterruptedException handling in sleep()

#### 1.3 DefaultSystemPropertyServiceTest
- **Coverage Target**: 100% line and branch coverage
- **Test Strategy**: Mock System class interactions
- **Key Test Cases**:
  - `getProperty(key)` with existing and non-existing properties
  - `getProperty(key, defaultValue)` with existing properties and fallback scenarios
  - `setProperty(key, value)` successful execution

#### 1.4 DefaultFileServiceTest
- **Coverage Target**: 100% line and branch coverage
- **Test Strategy**: Mock Files static methods
- **Key Test Cases**:
  - All 8 methods with successful operations
  - IOException handling for file operations
  - Stream handling in `list()` method
  - Path resolution with various input combinations

#### 1.5 ProcessResultTest
- **Coverage Target**: 100% line and branch coverage
- **Test Strategy**: Direct unit testing
- **Key Test Cases**:
  - Constructor with various exit codes and output strings
  - Getter methods return correct values
  - Edge cases with null/empty output

### Phase 2: Extension Class Coverage Gaps (Medium Priority)

Enhance existing tests to cover missing scenarios:

#### 2.1 DockerComposeClassExtensionTest Enhancements
- **Error handling paths**:
  - Docker compose file not found scenarios
  - Non-zero exit codes from docker commands
  - Process execution timeouts
  - IOException during state file operations
- **Edge cases**:
  - `sanitizeProjectName()` with special characters and edge cases
  - `waitForStackToBeReady()` timeout scenarios (maxAttempts reached)
  - `afterAll()` with null project name fallback
  - `cleanupStateFile()` with file deletion failures

#### 2.2 DockerComposeMethodExtensionTest Enhancements
- **Same error handling paths** as class extension
- **Additional edge cases**:
  - `generateUniqueProjectName()` with method name edge cases
  - Method-specific state file generation and cleanup

### Phase 3: Integration Points (Lower Priority)

Test the interfaces to ensure contract compliance:

#### 3.1 Service Interface Contract Tests
- Verify that all implementations properly implement their interfaces
- Test interface default methods (if any)
- Ensure consistent exception handling across implementations

#### 3.2 Cross-cutting Concerns
- Error propagation between layers
- Exception handling consistency
- Resource cleanup verification

## Verification and Quality Assurance

### Testing Standards
- **100% line and branch coverage** as measured by JaCoCo
- **Property-based testing** where applicable for input domain coverage
- **Reflection-based testing** for private methods where necessary
- **Isolated unit tests** with no external dependencies

### Build Verification
- All tests must pass: `./gradlew clean test`
- Plugin must build successfully: `./gradlew build`
- No compilation warnings
- No test warnings
- No build warnings

### Documentation Requirements
- Any code that cannot be unit tested must be documented in `docs/design-docs/testing/unit-test-gaps.md`
- Justification required for any deviations from 100% coverage
- Test gap documentation must include refactoring recommendations

## Expected Outcome

Following this plan will achieve **100% line and branch coverage** for the `com.kineticfire.gradle.docker.junit` package. The only acceptable gaps are:
- Direct system calls that cannot be reasonably mocked (must be documented)
- Truly unreachable code paths (should be identified and potentially removed)

The existing architecture with dependency injection makes this package highly testable, and the service layer abstraction isolates external dependencies effectively, enabling comprehensive mock-based testing.