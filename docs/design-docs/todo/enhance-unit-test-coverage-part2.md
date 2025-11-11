# Phase 3 Completion Plan: ExecLibraryComposeService Refactoring

## Executive Summary

Complete Phase 3 by extracting pure command-building logic from `ExecLibraryComposeService`, reducing method lengths from
47-63 lines to under 40 lines, and achieving 100% test coverage on all pure functions.

**Scope:**
- Extract 3 pure command-building functions (~60 lines of pure logic)
- Reduce 4 methods below 40-line threshold
- Add 15-20 comprehensive unit tests
- No changes to `DockerServiceImpl.executeBuild()` (accepted as 80-line external boundary)

**Estimated Effort:** 4-6 hours

**Expected Outcomes:**
- All methods ≤ 40 lines (except documented external boundaries)
- 100% coverage on extracted pure functions
- Service package coverage: 57% → 62%+ (limited by external boundaries)

---

## Current State Analysis

### Methods to Refactor

| Method | Current Lines | Pure Logic | External Calls | Target Lines |
|--------|---------------|------------|----------------|--------------|
| `upStack()` | 63 | 20 (command building) | 43 | 35-40 |
| `downStack(ComposeConfig)` | 52 | 18 (command building) | 34 | 30-35 |
| `captureLogs()` | 49 | 15 (command building) | 34 | 30-35 |
| `waitForServices()` | 47 | 5 (minimal) | 42 | Accept as-is |

### Pure Logic to Extract

**Command Building Logic** (~53 lines total):
1. `buildUpCommand()` - Lines 75-92 (~18 lines)
2. `buildDownCommand()` - Lines 204-220 (~17 lines)
3. `buildLogsCommand()` - Lines 334-348 (~15 lines)

**Benefits of Extraction:**
- 100% testable without process execution
- Can verify command structure with simple string assertions
- Enables property-based testing for various config combinations
- Reduces method complexity to orchestration only

---

## Refactoring Plan

### Recommendation 1: Extract buildUpCommand()

#### Current Code Location
**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeService.groovy`
**Lines:** 75-92 (inside `upStack()` method)

#### New Pure Function Signature
```groovy
/**
 * Pure function - Build docker compose up command from configuration.
 * 100% unit testable with no external dependencies.
 *
 * @param config Compose configuration
 * @param baseCommand Base compose command (e.g., ['docker', 'compose'])
 * @return Complete command list ready for execution
 */
@VisibleForTesting
protected List<String> buildUpCommand(ComposeConfig config, List<String> baseCommand) {
    def command = baseCommand.clone()

    // Add compose files
    config.composeFiles.each { file ->
        command.addAll(["-f", file.toString()])
    }

    // Add project name
    command.addAll(["-p", config.projectName])

    // Add env files
    config.envFiles.each { envFile ->
        command.addAll(["--env-file", envFile.toString()])
    }

    // Add the up command
    command.addAll(["up", "-d"])

    return command
}
```

#### Modified upStack() Method
```groovy
@Override
CompletableFuture<ComposeState> upStack(ComposeConfig config) {
    if (config == null) {
        throw new NullPointerException("Compose config cannot be null")
    }
    return CompletableFuture.supplyAsync({
        try {
            serviceLogger.info("Starting Docker Compose stack: ${config.stackName}")

            // Build command (pure logic - extracted)
            def composeCommand = getComposeCommand()
            def command = buildUpCommand(config, composeCommand)

            serviceLogger.debug("Executing: ${command.join(' ')}")

            // Execute command (external call - keep minimal)
            def workingDir = config.composeFiles.first().parent.toFile()
            def result = processExecutor.execute(command, workingDir)

            if (!result.isSuccess()) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                    "Docker Compose up failed with exit code ${result.exitCode}: ${result.stderr}",
                    "Check your compose file syntax and service configurations"
                )
            }

            // Get current stack state
            def services = getStackServices(config.projectName)

            def composeState = new ComposeState(
                config.stackName,
                config.projectName,
                services
            )

            serviceLogger.info("Docker Compose stack started: ${config.stackName}")
            return composeState

        } catch (ComposeServiceException e) {
            throw e
        } catch (Exception e) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                "Failed to start compose stack: ${e.message}",
                e
            )
        }
    })
}
```

**Line Count:** 63 → ~40 lines (reduction of 23 lines)

#### Unit Tests to Add

**New Test File Section:** Add to `ExecLibraryComposeServiceUnitTest.groovy`

```groovy
// ============ buildUpCommand Tests ============

def "buildUpCommand builds command with single compose file"() {
    given:
    def composeFile = tempDir.resolve("docker-compose.yml")
    def config = new ComposeConfig([composeFile], "test-project", "test-stack")
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildUpCommand(config, baseCommand)

    then:
    command == ['docker', 'compose', '-f', composeFile.toString(),
                '-p', 'test-project', 'up', '-d']
}

def "buildUpCommand builds command with multiple compose files"() {
    given:
    def file1 = tempDir.resolve("docker-compose.yml")
    def file2 = tempDir.resolve("docker-compose.override.yml")
    def config = new ComposeConfig([file1, file2], "test-project", "test-stack")
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildUpCommand(config, baseCommand)

    then:
    command.contains('-f')
    command.indexOf(file1.toString()) < command.indexOf(file2.toString())
    command.contains('up')
    command.contains('-d')
}

def "buildUpCommand includes env files when present"() {
    given:
    def composeFile = tempDir.resolve("docker-compose.yml")
    def envFile = tempDir.resolve(".env")
    def config = new ComposeConfig([composeFile], [envFile], "test-project", "test-stack", [:])
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildUpCommand(config, baseCommand)

    then:
    command.contains('--env-file')
    command.contains(envFile.toString())
}

def "buildUpCommand uses custom project name"() {
    given:
    def composeFile = tempDir.resolve("docker-compose.yml")
    def config = new ComposeConfig([composeFile], "custom-project-name", "test-stack")
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildUpCommand(config, baseCommand)

    then:
    def projectIndex = command.indexOf('-p')
    projectIndex >= 0
    command[projectIndex + 1] == 'custom-project-name'
}

def "buildUpCommand does not mutate base command"() {
    given:
    def composeFile = tempDir.resolve("docker-compose.yml")
    def config = new ComposeConfig([composeFile], "test-project", "test-stack")
    def baseCommand = ['docker', 'compose']
    def originalSize = baseCommand.size()

    when:
    service.buildUpCommand(config, baseCommand)

    then:
    baseCommand.size() == originalSize  // Ensure clone() worked
}

@Unroll
def "buildUpCommand handles edge case: #scenario"() {
    given:
    def config = new ComposeConfig(files, envFiles, projectName, stackName, [:])
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildUpCommand(config, baseCommand)

    then:
    command.contains('up')
    command.contains('-d')
    command.contains(projectName)

    where:
    scenario                  | files                        | envFiles | projectName | stackName
    "no env files"            | [Path.of("compose.yml")]     | []       | "proj1"     | "stack1"
    "multiple env files"      | [Path.of("compose.yml")]     | [Path.of(".env"), Path.of(".env.local")] | "proj2" | "stack2"
    "long project name"       | [Path.of("compose.yml")]     | []       | "very-long-project-name-with-hyphens" | "stack3"
}
```

**Test Count:** 7 unit tests
**Coverage:** 100% of `buildUpCommand()` logic

---

### Recommendation 2: Extract buildDownCommand()

#### Current Code Location
**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeService.groovy`
**Lines:** 204-220 (inside `downStack(ComposeConfig)` method)

#### New Pure Function Signature
```groovy
/**
 * Pure function - Build docker compose down command from configuration.
 * 100% unit testable with no external dependencies.
 *
 * @param config Compose configuration
 * @param baseCommand Base compose command (e.g., ['docker', 'compose'])
 * @return Complete command list ready for execution
 */
@VisibleForTesting
protected List<String> buildDownCommand(ComposeConfig config, List<String> baseCommand) {
    def command = baseCommand.clone()

    // Add compose files for proper teardown
    config.composeFiles.each { file ->
        command.addAll(["-f", file.toString()])
    }

    // Add project name
    command.addAll(["-p", config.projectName])

    // Add env files if present
    config.envFiles.each { envFile ->
        command.addAll(["--env-file", envFile.toString()])
    }

    // Add the down command
    command.addAll(["down", "--remove-orphans"])

    return command
}
```

#### Modified downStack(ComposeConfig) Method
```groovy
@Override
CompletableFuture<Void> downStack(ComposeConfig config) {
    if (config == null) {
        throw new NullPointerException("Compose config cannot be null")
    }
    return CompletableFuture.runAsync({
        try {
            serviceLogger.info("Stopping Docker Compose stack: ${config.stackName} (project: ${config.projectName})")

            // Build command (pure logic - extracted)
            def composeCommand = getComposeCommand()
            def command = buildDownCommand(config, composeCommand)

            serviceLogger.debug("Executing: ${command.join(' ')}")

            // Execute command (external call - keep minimal)
            def workingDir = config.composeFiles.first().parent.toFile()
            def result = processExecutor.execute(command, workingDir)

            if (!result.isSuccess()) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                    "Docker Compose down failed with exit code ${result.exitCode}: ${result.stderr}",
                    "Check your compose file syntax and project configuration"
                )
            }

            serviceLogger.info("Docker Compose stack stopped: ${config.stackName} (project: ${config.projectName})")

        } catch (ComposeServiceException e) {
            throw e
        } catch (Exception e) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                "Failed to stop compose stack: ${e.message}",
                e
            )
        }
    })
}
```

**Line Count:** 52 → ~35 lines (reduction of 17 lines)

#### Unit Tests to Add

**New Test File Section:** Add to `ExecLibraryComposeServiceUnitTest.groovy`

```groovy
// ============ buildDownCommand Tests ============

def "buildDownCommand builds command with single compose file"() {
    given:
    def composeFile = tempDir.resolve("docker-compose.yml")
    def config = new ComposeConfig([composeFile], "test-project", "test-stack")
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildDownCommand(config, baseCommand)

    then:
    command == ['docker', 'compose', '-f', composeFile.toString(),
                '-p', 'test-project', 'down', '--remove-orphans']
}

def "buildDownCommand includes remove-orphans flag"() {
    given:
    def composeFile = tempDir.resolve("docker-compose.yml")
    def config = new ComposeConfig([composeFile], "test-project", "test-stack")
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildDownCommand(config, baseCommand)

    then:
    command.contains('--remove-orphans')
}

def "buildDownCommand handles multiple compose files"() {
    given:
    def file1 = tempDir.resolve("docker-compose.yml")
    def file2 = tempDir.resolve("docker-compose.override.yml")
    def config = new ComposeConfig([file1, file2], "test-project", "test-stack")
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildDownCommand(config, baseCommand)

    then:
    command.count { it == '-f' } == 2
    command.contains(file1.toString())
    command.contains(file2.toString())
}

def "buildDownCommand includes env files when present"() {
    given:
    def composeFile = tempDir.resolve("docker-compose.yml")
    def envFile = tempDir.resolve(".env")
    def config = new ComposeConfig([composeFile], [envFile], "test-project", "test-stack", [:])
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildDownCommand(config, baseCommand)

    then:
    command.contains('--env-file')
    command.contains(envFile.toString())
}

def "buildDownCommand does not mutate base command"() {
    given:
    def composeFile = tempDir.resolve("docker-compose.yml")
    def config = new ComposeConfig([composeFile], "test-project", "test-stack")
    def baseCommand = ['docker', 'compose']
    def originalSize = baseCommand.size()

    when:
    service.buildDownCommand(config, baseCommand)

    then:
    baseCommand.size() == originalSize  // Ensure clone() worked
}
```

**Test Count:** 5 unit tests
**Coverage:** 100% of `buildDownCommand()` logic

---

### Recommendation 3: Extract buildLogsCommand()

#### Current Code Location
**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeService.groovy`
**Lines:** 334-348 (inside `captureLogs()` method)

#### New Pure Function Signature
```groovy
/**
 * Pure function - Build docker compose logs command from configuration.
 * 100% unit testable with no external dependencies.
 *
 * @param projectName Docker Compose project name
 * @param config Logs configuration
 * @param baseCommand Base compose command (e.g., ['docker', 'compose'])
 * @return Complete command list ready for execution
 */
@VisibleForTesting
protected List<String> buildLogsCommand(String projectName, LogsConfig config, List<String> baseCommand) {
    def command = baseCommand + ["-p", projectName, "logs"]

    if (config.follow) {
        command.add("--follow")
    }

    if (config.tailLines > 0) {
        command.addAll(["--tail", config.tailLines.toString()])
    }

    if (config.services && !config.services.isEmpty()) {
        command.addAll(config.services)
    }

    return command
}
```

#### Modified captureLogs() Method
```groovy
@Override
CompletableFuture<String> captureLogs(String projectName, LogsConfig config) {
    if (projectName == null) {
        throw new NullPointerException("Project name cannot be null")
    }
    if (config == null) {
        throw new NullPointerException("Logs config cannot be null")
    }
    return CompletableFuture.supplyAsync({
        try {
            serviceLogger.info("Capturing logs for project: ${projectName}")

            // Build command (pure logic - extracted)
            def composeCommand = getComposeCommand()
            def command = buildLogsCommand(projectName, config, composeCommand)

            // Execute command (external call - keep minimal)
            def result = processExecutor.execute(command)

            if (!result.isSuccess()) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED,
                    "Failed to capture logs: ${result.stderr}",
                    "Check if the project and services exist"
                )
            }

            return result.stdout

        } catch (ComposeServiceException e) {
            throw e
        } catch (Exception e) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED,
                "Failed to capture logs: ${e.message}",
                e
            )
        }
    })
}
```

**Line Count:** 49 → ~35 lines (reduction of 14 lines)

#### Unit Tests to Add

**New Test File Section:** Add to `ExecLibraryComposeServiceUnitTest.groovy`

```groovy
// ============ buildLogsCommand Tests ============

def "buildLogsCommand builds basic command"() {
    given:
    def config = new LogsConfig(["service1"])
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildLogsCommand("test-project", config, baseCommand)

    then:
    command == ['docker', 'compose', '-p', 'test-project', 'logs', 'service1']
}

def "buildLogsCommand includes follow flag when enabled"() {
    given:
    def config = new LogsConfig(["service1"], 0, true)
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildLogsCommand("test-project", config, baseCommand)

    then:
    command.contains('--follow')
}

def "buildLogsCommand includes tail lines when specified"() {
    given:
    def config = new LogsConfig(["service1"], 100, false)
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildLogsCommand("test-project", config, baseCommand)

    then:
    command.contains('--tail')
    command.contains('100')
}

def "buildLogsCommand handles multiple services"() {
    given:
    def config = new LogsConfig(["service1", "service2", "service3"])
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildLogsCommand("test-project", config, baseCommand)

    then:
    command.contains('service1')
    command.contains('service2')
    command.contains('service3')
}

def "buildLogsCommand handles empty services list"() {
    given:
    def config = new LogsConfig([])
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildLogsCommand("test-project", config, baseCommand)

    then:
    command == ['docker', 'compose', '-p', 'test-project', 'logs']
}

def "buildLogsCommand combines all options"() {
    given:
    def config = new LogsConfig(["service1", "service2"], 50, true)
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildLogsCommand("test-project", config, baseCommand)

    then:
    command.contains('--follow')
    command.contains('--tail')
    command.contains('50')
    command.contains('service1')
    command.contains('service2')
}

@Unroll
def "buildLogsCommand handles edge case: #scenario"() {
    given:
    def config = new LogsConfig(services, tailLines, follow)
    def baseCommand = ['docker', 'compose']

    when:
    def command = service.buildLogsCommand(projectName, config, baseCommand)

    then:
    command.contains(projectName)
    command.contains('logs')

    where:
    scenario                      | projectName  | services          | tailLines | follow
    "no services, no options"     | "proj1"      | []                | 0         | false
    "single service with follow"  | "proj2"      | ["web"]           | 0         | true
    "multiple services with tail" | "proj3"      | ["web", "db"]     | 200       | false
    "all options combined"        | "proj4"      | ["web"]           | 100       | true
}
```

**Test Count:** 8 unit tests
**Coverage:** 100% of `buildLogsCommand()` logic

---

### Recommendation 4: Accept waitForServices() as-is

**Decision:** `waitForServices()` at 47 lines is **ACCEPTABLE** because:
1. Uses injected `TimeService` - already 100% mockable and tested
2. Contains minimal pure logic (loop orchestration)
3. Reduction would require extracting trivial 5-line fragments
4. Already has comprehensive unit tests demonstrating mockability

**No changes required.**

---

## Implementation Steps

### Step 1: Extract buildUpCommand() (1-1.5 hours)
1. Create new method in `ExecLibraryComposeService` with `@VisibleForTesting`
2. Modify `upStack()` to call `buildUpCommand()`
3. Add 7 unit tests to `ExecLibraryComposeServiceUnitTest`
4. Run tests: `./gradlew clean test`
5. Verify all tests pass

### Step 2: Extract buildDownCommand() (1 hour)
1. Create new method in `ExecLibraryComposeService` with `@VisibleForTesting`
2. Modify `downStack(ComposeConfig)` to call `buildDownCommand()`
3. Add 5 unit tests to `ExecLibraryComposeServiceUnitTest`
4. Run tests: `./gradlew clean test`
5. Verify all tests pass

### Step 3: Extract buildLogsCommand() (1 hour)
1. Create new method in `ExecLibraryComposeService` with `@VisibleForTesting`
2. Modify `captureLogs()` to call `buildLogsCommand()`
3. Add 8 unit tests to `ExecLibraryComposeServiceUnitTest`
4. Run tests: `./gradlew clean test`
5. Verify all tests pass

### Step 4: Update Documentation (0.5 hours)
1. Update `docs/design-docs/testing/unit-test-gaps.md`:
   - Update line numbers for refactored methods
   - Add note about command building extraction
   - Update coverage statistics
2. Update `docs/design-docs/todo/enhance-unit-test-coverage.md`:
   - Mark Phase 3 as **COMPLETE**
   - Document final coverage numbers
   - Add completion date (2025-01-30)

### Step 5: Final Verification (0.5 hours)
1. Run full test suite: `./gradlew clean test`
2. Verify JaCoCo report shows improved coverage
3. Check method line counts with script
4. Run integration tests: `cd ../plugin-integration-test && ./gradlew cleanAll integrationTest`
5. Verify no regressions

---

## Expected Outcomes

### Method Line Counts (After Refactoring)

| Method | Before | After | Status |
|--------|--------|-------|--------|
| `upStack()` | 63 | 38 | ✅ Under 40 |
| `downStack(ComposeConfig)` | 52 | 33 | ✅ Under 40 |
| `captureLogs()` | 49 | 33 | ✅ Under 40 |
| `waitForServices()` | 47 | 47 | ✅ Acceptable (mockable) |
| `executeBuild()` | 80 | 80 | ✅ External boundary (documented) |

### Test Coverage

**New Pure Functions:**
- `buildUpCommand()`: 100% coverage (7 tests)
- `buildDownCommand()`: 100% coverage (5 tests)
- `buildLogsCommand()`: 100% coverage (8 tests)

**Total New Tests:** 20 unit tests

**Expected Coverage Improvements:**
```
Service Package:
  Before: 57% instructions, 68% branches
  After:  62% instructions, 72% branches

Overall:
  Before: 81% instructions, 80% branches
  After:  82% instructions, 81% branches
```

### Code Quality Metrics

| Metric | Before | After | Target | Status |
|--------|--------|-------|--------|--------|
| Methods > 40 lines (excluding documented boundaries) | 4 | 0 | 0 | ✅ |
| Pure functions tested | 6 | 9 | All | ✅ |
| External boundaries documented | Yes | Yes | Yes | ✅ |
| Cyclomatic complexity | ≤10 | ≤10 | ≤10 | ✅ |

---

## Testing Strategy

### Unit Testing Approach

**Command Builder Tests:**
1. **Happy path**: Standard configurations with expected output
2. **Edge cases**: Empty lists, null handling, boundary conditions
3. **Multiple variations**: Different file counts, option combinations
4. **Immutability**: Verify base command not mutated (`.clone()` works)
5. **Property-based**: Use `@Unroll` for comprehensive input coverage

**Test Organization:**
- Group tests by method: `// ============ buildUpCommand Tests ============`
- Use descriptive names: `"buildUpCommand builds command with single compose file"`
- Use `given-when-then` structure consistently
- Assert exact command structure where possible

### Integration Testing

**No Changes Required** - Existing integration tests already verify:
- `upStack()` creates containers correctly
- `downStack()` removes containers correctly
- `captureLogs()` captures actual container logs
- All tests use refactored methods transparently

---

## Risk Mitigation

### Risk 1: Breaking Existing Functionality
**Mitigation:**
- Extract logic without changing behavior
- Run full test suite after each extraction
- Verify integration tests still pass
- Use parallel development branch

### Risk 2: Test Complexity
**Mitigation:**
- Keep tests simple with direct assertions
- Use helper methods for common setup
- Avoid over-mocking (pure functions need no mocks)
- Follow existing test patterns in `DockerServiceImplPureFunctionsTest`

### Risk 3: Configuration Cache Compatibility
**Mitigation:**
- All extracted methods are static or use only injected dependencies
- No `Project` references
- Command building is pure (no side effects)
- Already validated by Phase 1 and Phase 2 patterns

---

## Acceptance Criteria

### Functional Requirements
- [ ] All unit tests pass (existing + 20 new tests)
- [ ] All integration tests pass (no regressions)
- [ ] Plugin builds successfully: `./gradlew -Pplugin_version=1.0.0 build publishToMavenLocal`

### Code Quality Requirements
- [ ] All methods ≤ 40 lines (except documented external boundaries)
- [ ] All pure functions have 100% test coverage
- [ ] No cyclomatic complexity > 10
- [ ] No compilation warnings
- [ ] No test warnings

### Documentation Requirements
- [ ] `unit-test-gaps.md` updated with new line numbers
- [ ] `enhance-unit-test-coverage.md` marked Phase 3 complete
- [ ] All extracted methods have clear JavaDoc
- [ ] Gap file references integration test coverage

### Gradle Compatibility Requirements
- [ ] Configuration cache enabled and working
- [ ] No configuration cache violations
- [ ] All tasks properly cacheable

---

## Success Metrics

### Primary Metrics
- ✅ Methods > 40 lines: 4 → 0 (excluding documented boundaries)
- ✅ Pure function test coverage: 100% (all 9 functions)
- ✅ Service package coverage: 57% → 62%+
- ✅ Total new tests: 20 unit tests

### Quality Metrics
- ✅ Zero methods > 40 lines (excluding external boundaries)
- ✅ Zero untested pure logic
- ✅ All command builders testable in milliseconds
- ✅ Clean separation: pure core vs. impure shell

### Maintainability Metrics
- ✅ Reduced method complexity (single responsibility)
- ✅ Improved testability (no mocks needed for command building)
- ✅ Better documentation (clear function boundaries)
- ✅ Easier debugging (isolated logic vs. execution)

---

## Files to Modify

### Source Files (1 file)
1. `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeService.groovy`
   - Add 3 new `@VisibleForTesting protected` methods
   - Modify 3 existing methods to call extracted functions
   - **Net change:** +60 lines (new methods), -54 lines (removed inline logic) = +6 lines

### Test Files (1 file)
1. `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeServiceUnitTest.groovy`
   - Add 3 new test sections
   - Add 20 new test methods
   - **Net change:** +250-300 lines

### Documentation Files (2 files)
1. `docs/design-docs/testing/unit-test-gaps.md`
   - Update line numbers in ExecLibraryComposeService section
   - Add note about command builder extraction
   - Update coverage statistics
   - **Net change:** ~20 lines modified

2. `docs/design-docs/todo/enhance-unit-test-coverage.md`
   - Update Phase 3 status to COMPLETE
   - Add completion date and final metrics
   - **Net change:** ~10 lines modified

---

## Timeline

| Phase | Duration | Cumulative |
|-------|----------|------------|
| Step 1: Extract buildUpCommand() | 1-1.5 hours | 1-1.5 hours |
| Step 2: Extract buildDownCommand() | 1 hour | 2-2.5 hours |
| Step 3: Extract buildLogsCommand() | 1 hour | 3-3.5 hours |
| Step 4: Update Documentation | 0.5 hours | 3.5-4 hours |
| Step 5: Final Verification | 0.5 hours | 4-4.5 hours |
| **Total** | **4-4.5 hours** | |

---

## Conclusion

This plan completes Phase 3 by extracting the remaining pure command-building logic from `ExecLibraryComposeService`,
achieving:

1. **All methods ≤ 40 lines** (excluding documented external boundaries)
2. **100% coverage on all pure functions** (9 functions total)
3. **Clean separation** of pure logic from external calls
4. **20 new comprehensive unit tests** with property-based coverage

The refactoring follows the successful pattern from `DockerServiceImpl.buildImage()` and maintains full Gradle 9/10
compatibility. All extracted logic is deterministic, easily testable, and requires no mocks.

**Phase 3 will be FULLY COMPLETE** after implementing this plan.

---

**Document Version:** 1.0
**Created:** 2025-01-30
**Status:** Ready for Implementation
**Estimated Effort:** 4-4.5 hours
**Expected Coverage Improvement:** 57% → 62%+ (service package)
