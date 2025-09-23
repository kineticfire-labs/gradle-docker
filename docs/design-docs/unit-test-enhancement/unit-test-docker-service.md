# Unit Test Enhancement Plan for com.kineticfire.gradle.docker.service Package

## Overview

This document outlines the comprehensive plan to achieve 100% code and branch coverage for the
`com.kineticfire.gradle.docker.service` package. The analysis focuses on identifying untested code,
categorizing it by testability challenges, and providing actionable strategies for improvement.

## Current Coverage Assessment

### Service Package Classes

| Class | Type | Methods | Current Coverage | Target |
|-------|------|---------|------------------|--------|
| JsonService | Interface | 3 | N/A (Interface) | 100% |
| JsonServiceImpl | Implementation | 8 | ~80% | 100% |
| DockerService | Interface | 7 | N/A (Interface) | 100% |
| DockerServiceImpl | Implementation | ~15 | ~30% | 100% |
| ComposeService | Interface | 5 | N/A (Interface) | 100% |
| ExecLibraryComposeService | Implementation | ~12 | ~25% | 100% |

## Code Coverage Analysis

### Category 1: Code Lacking Tests Due to External Calls

#### 1a) Instructions lacking tests:

**DockerServiceImpl.groovy:**
- `createDockerClient()` method - Creates real Docker client, connects to daemon, calls `client.pingCmd().exec()`
- All Docker API calls in core methods:
  - `buildImage()` - Docker Java API build operations
  - `tagImage()` - Docker Java API tag operations
  - `saveImage()` - Docker Java API save + file I/O with compression streams
  - `pushImage()` - Docker Java API push operations
  - `pullImage()` - Docker Java API pull operations
  - `imageExists()` - Docker Java API inspect operations
- `close()` method - Executor service shutdown and Docker client close

**ExecLibraryComposeService.groovy:**
- `validateDockerCompose()` - Executes external processes (`docker compose version`, `docker-compose --version`)
- `getComposeCommand()` - Executes external process to determine command availability
- All process execution in core methods:
  - `upStack()` - Process creation and execution
  - `downStack()` - Process creation and execution
  - `waitForServices()` - Process creation and execution
  - `captureLogs()` - Process creation and execution
- `getStackServices()` - Process execution and JSON parsing of external command output
- `checkServiceReady()` - Process execution for service status checking

**JsonServiceImpl.groovy:**
- `writeComposeState()` and `readComposeState()` - File system operations:
  - `Files.createDirectories()`
  - `Files.writeString()`
  - `Files.readString()`
  - `Files.exists()`

#### 1b) Branches lacking tests:

**DockerServiceImpl:**
- Exception handling branches in all methods (`DockerException`, general `Exception`)
- Compression type branches in `saveImage()` (GZIP, BZIP2, XZ, ZIP, NONE)
- Auth credential branches in `pushImage()` and `pullImage()`

**ExecLibraryComposeService:**
- Process failure branches (exit code != 0)
- Exception handling in all methods (`ComposeServiceException`, general `Exception`)
- Command detection fallback branches in `getComposeCommand()`
- Service state parsing branches in `parseServiceState()`

**JsonServiceImpl:**
- File I/O exception handling branches in file operations
- Directory creation failure branches

### Category 2: Code Untested That Does Not Fall Into #1

#### Completely untested code:
- **DockerServiceImpl**: Constructor initialization logic, executor service creation, daemon thread creation
- **ExecLibraryComposeService**: Constructor initialization, logger initialization
- **JsonServiceImpl**: ObjectMapper configuration in constructor

#### Partially tested code needing more coverage:
- **JsonServiceImpl**: Error handling paths in `parseJson()`, `getObjectMapper()` method, edge cases in file operations
- **ExecLibraryComposeService**: Edge cases in `parseServiceState()`, JSON parsing edge cases in `getStackServices()`

## Testing Enhancement Strategy

### Phase 1: Mock-Based Testing (No Refactoring Required)

#### JsonServiceImpl Enhancements

**Priority: HIGH (Quick Wins)**

```groovy
// Add tests for file operations with mocked Files
@Mock
static Files

def "writeComposeState handles file permission errors"() {
    given:
    Files.createDirectories(_) >> { throw new IOException("Permission denied") }

    when:
    service.writeComposeState(composeState, outputFile)

    then:
    thrown(RuntimeException)
}

def "readComposeState handles non-existent file"() {
    given:
    Files.exists(_) >> false

    when:
    service.readComposeState(inputFile)

    then:
    def ex = thrown(RuntimeException)
    ex.message.contains("File does not exist")
}
```

**Test Coverage Additions:**
- File operation exception paths (permission denied, disk full, invalid paths)
- `getObjectMapper()` method direct testing
- Edge cases in `parseJson()` method
- Directory creation scenarios in `writeComposeState()`

#### DockerServiceImpl Enhancements

**Priority: HIGH (Major Coverage Gap)**

```groovy
// Mock Docker Java API interactions
@Mock DockerClient mockDockerClient
@Mock BuildImageCmd mockBuildCmd
@Mock TagImageCmd mockTagCmd
@Mock SaveImageCmd mockSaveCmd

def "buildImage handles DockerException"() {
    given:
    mockDockerClient.buildImageCmd() >> mockBuildCmd
    mockBuildCmd.exec(_) >> { throw new DockerException("Build failed") }

    when:
    service.buildImage(context).get()

    then:
    def ex = thrown(ExecutionException)
    ex.cause instanceof DockerServiceException
    ex.cause.errorType == DockerServiceException.ErrorType.BUILD_FAILED
}
```

**Test Coverage Additions:**
- Mock all Docker Java API interactions
- Test all compression types in `saveImage()` (NONE, GZIP, BZIP2, XZ, ZIP)
- Test error handling paths for each method
- Test async execution paths
- Test `close()` method execution
- Test auth credential handling branches

#### ExecLibraryComposeService Enhancements

**Priority: HIGH (Major Coverage Gap)**

```groovy
// Mock process execution
@Mock ProcessBuilder mockProcessBuilder
@Mock Process mockProcess

def "upStack handles process execution failure"() {
    given:
    mockProcessBuilder.start() >> mockProcess
    mockProcess.waitFor() >> 1
    mockProcess.errorStream >> new ByteArrayInputStream("Compose failed".bytes)

    when:
    service.upStack(config).get()

    then:
    def ex = thrown(ExecutionException)
    ex.cause instanceof ComposeServiceException
}
```

**Test Coverage Additions:**
- Mock ProcessBuilder and Process for all external calls
- Test command detection logic in `getComposeCommand()`
- Test JSON parsing in `getStackServices()`
- Test service state parsing edge cases
- Test timeout scenarios in `waitForServices()`
- Test process failure handling

### Phase 2: Strategic Refactoring for Better Testability

#### High-Impact Refactoring Opportunities

**DockerServiceImpl Refactoring:**

```groovy
// Extract to injectable dependencies
interface DockerClientFactory {
    DockerClient createClient()
}

interface CompressionHandler {
    void compressStream(InputStream input, OutputStream output, SaveCompression type)
}

interface ExecutorServiceProvider {
    ExecutorService getExecutorService()
}

// Modified constructor
@Inject
DockerServiceImpl(DockerClientFactory clientFactory,
                  CompressionHandler compressionHandler,
                  ExecutorServiceProvider executorProvider) {
    this.dockerClient = clientFactory.createClient()
    this.compressionHandler = compressionHandler
    this.executorService = executorProvider.getExecutorService()
}
```

**ExecLibraryComposeService Refactoring:**

```groovy
// Extract to injectable dependencies
interface ProcessExecutor {
    ProcessResult execute(List<String> command, File workingDir)
}

interface CommandDetector {
    List<String> detectComposeCommand()
}

interface ServiceStateParser {
    ServiceStatus parseState(String statusText)
}

// Modified constructor
@Inject
ExecLibraryComposeService(ProcessExecutor processExecutor,
                          CommandDetector commandDetector,
                          ServiceStateParser stateParser) {
    this.processExecutor = processExecutor
    this.commandDetector = commandDetector
    this.stateParser = stateParser
}
```

**JsonServiceImpl Refactoring:**

```groovy
// Extract to injectable dependencies
interface FileSystemService {
    void writeString(Path path, String content)
    String readString(Path path)
    boolean exists(Path path)
    void createDirectories(Path path)
}

// Modified constructor
@Inject
JsonServiceImpl(FileSystemService fileSystemService) {
    this.fileSystemService = fileSystemService
    this.objectMapper = createObjectMapper()
}
```

## Implementation Plan

### Phase 1 Priority (Immediate Wins - ~85-90% Coverage)

1. **Week 1: JsonServiceImpl**
   - Add file operation mocking tests
   - Test all error paths
   - Test edge cases in JSON parsing

2. **Week 2: DockerServiceImpl**
   - Add comprehensive Docker API mocking
   - Test all compression types
   - Test error handling branches

3. **Week 3: ExecLibraryComposeService**
   - Add process execution mocking
   - Test command detection logic
   - Test JSON parsing scenarios

### Phase 2 Priority (Selective Refactoring - 100% Coverage)

1. **Week 4: Critical Path Analysis**
   - Identify remaining untestable code after Phase 1
   - Document justified gaps in unit-test-gaps.md
   - Plan minimal refactoring for remaining gaps

2. **Week 5: Strategic Refactoring**
   - Apply dependency injection for critical external calls
   - Create focused utility classes for complex logic
   - Maintain backward compatibility

3. **Week 6: Verification**
   - Run JaCoCo coverage reports
   - Verify 100% line and branch coverage
   - Update gap documentation

## Testing Infrastructure Requirements

### Mock Library Enhancements
- Enhanced Spock mocking for static method calls (`Files.*`)
- PowerMock integration for `ProcessBuilder` mocking
- Custom test doubles for Docker Java API classes

### Test Utilities
- `MockDockerClientFactory` for consistent Docker API mocking
- `MockProcessExecutor` for process execution testing
- `TestFileSystemService` for file operation testing

### Coverage Verification
- JaCoCo configuration for 100% coverage enforcement
- Automated coverage reporting in CI/CD pipeline
- Gap documentation automation

## Success Criteria

1. **100% line coverage** for all service implementation classes
2. **100% branch coverage** for all conditional logic
3. **Zero untested methods** (excluding documented gaps)
4. **Comprehensive error path testing** for all external integrations
5. **Maintainable test suite** with clear mocking strategies
6. **Performance impact < 5%** for additional test execution time

## Risk Mitigation

### Technical Risks
- **Mock complexity**: Start with simple mocks, evolve as needed
- **Test brittleness**: Focus on behavior verification over implementation details
- **Refactoring scope**: Limit to critical paths only

### Process Risks
- **Coverage false positives**: Verify meaningful test assertions, not just coverage numbers
- **Test maintenance burden**: Invest in reusable test utilities and clear documentation
- **Integration compatibility**: Ensure mocked behavior matches real system behavior

## Documentation Updates

1. **Unit Test Gaps**: Document any remaining uncoverable code in `docs/design-docs/testing/unit-test-gaps.md`
2. **Test Utilities**: Document mock infrastructure in `docs/design-docs/testing/test-utilities.md`
3. **Coverage Reports**: Automate JaCoCo report generation and archival
4. **Developer Guide**: Update testing guidelines with service-specific patterns

This plan provides a systematic approach to achieving 100% unit test coverage for the service package while maintaining code quality and test maintainability.