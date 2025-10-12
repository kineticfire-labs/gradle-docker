# Plugin Does Not Support METHOD-Level Container Restarts

**Date**: 2025-10-11
**Status**: Critical Design Issue
**Severity**: High - Prevents proper test isolation for METHOD-level lifecycle tests

## Executive Summary

The Gradle Docker plugin's current implementation **does not support METHOD-level container restarts** when using
Gradle tasks for Docker Compose orchestration. The `composeUp` and `composeDown` tasks execute once per test class,
not per test method, preventing proper test isolation for METHOD-level lifecycle scenarios.

This was discovered during integration testing of the lifecycle-method verification tests, where 2 out of 7 tests
failed because containers did not restart between test methods as expected.

## Evidence from Code

### 1. Hardcoded Suite Lifecycle in ComposeUpTask

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy`
**Line**: 194

```groovy
def stateData = [
    stackName: stackName.get(),
    projectName: effectiveProjectName,
    lifecycle: "suite",  // ← HARDCODED - no support for "method" lifecycle
    timestamp: now,
    services: serviceData
]
```

The state file always writes `"lifecycle": "suite"` regardless of the actual test lifecycle being used. This indicates
the task was designed only for CLASS/SUITE-level lifecycle.

### 2. Gradle Task Dependency System Limitation

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`
**Lines**: 237-250 (task registration), 355-366 (wiring tasks to tests)

```groovy
// Task registration - creates SINGLE task instance per stack
tasks.register(composeUpTaskName, ComposeUpTask) { task ->
    task.group = 'Docker Compose'
    task.description = "Compose up for stack '${stackName}'"
    // ... configuration
}

// Task wiring - runs ONCE via Gradle's task dependency graph
afterEvaluate {
    tasks.withType(Test).matching { it.name == integrationTestTaskName }.configureEach { task ->
        task.dependsOn composeUpTaskName    // Runs BEFORE test task starts
        task.finalizedBy composeDownTaskName // Runs AFTER test task completes
    }
}
```

**How Gradle's Task Graph Works**:
1. `dependsOn composeUpTaskName` executes the `composeUp` task **once** before the test task begins
2. The test task runs **all test methods** in sequence
3. `finalizedBy composeDownTaskName` executes the `composeDown` task **once** after all tests complete

This execution model means:
- Containers start BEFORE first test method
- Containers remain running THROUGH all test methods
- Containers stop AFTER last test method

**There is no mechanism to re-execute tasks between test methods.**

### 3. Test Failure Evidence

**File**:
`plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/build/reports/tests/integrationTest/index.html`

**Test Results**:
- Total: 7 tests
- Failures: 2 tests (29% failure rate)
- Success rate: 71%

**Failed Tests**:
1. `containers should be fresh for each test - verify isolation`
2. `state should not persist between test methods - verify test 2 isolation`

**Failure Pattern**:
Both failures show that state **persists** between test methods when it **should not**:
- Tests expect 404 (container restarted, state cleared)
- Tests get 200 (container still running, state persisted)

This proves containers do NOT restart between test methods.

### 4. State File Confirms Suite Lifecycle

**File**:
`plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/build/compose-state/lifecycleMethodTest-state.json`
**Lines**: 4

```json
{
    "stackName": "lifecycleMethodTest",
    "projectName": "verification-lifecycle-method-test",
    "lifecycle": "suite",  // ← Confirms suite-level lifecycle was used
    "timestamp": "2025-10-12T03:00:57.841056642Z",
    "services": {
        "state-app": {
            "containerId": "2fe2f726eebf",
            "containerName": "state-app",
            "state": "running"  // ← Container RUNNING at test completion
            // ...
        }
    }
}
```

The state file generated during the lifecycle-method test execution shows `"lifecycle": "suite"`, confirming the plugin
used suite-level lifecycle even though the test was designed to verify method-level lifecycle.

## Why This Limitation Exists

The plugin has **TWO separate orchestration systems** for different use cases:

### System 1: Gradle Tasks (Current Integration Tests)
- **Purpose**: Gradle-native orchestration for integration tests
- **Components**: `ComposeUpTask`, `ComposeDownTask`
- **Lifecycle**: CLASS/SUITE level only
- **Mechanism**: Gradle's `dependsOn` and `finalizedBy` task graph
- **Used by**: Spock integration tests in `plugin-integration-test/`

### System 2: JUnit 5 Extensions (Separate System)
- **Purpose**: Per-method lifecycle for JUnit 5 tests
- **Components**: `DockerComposeMethodExtension`, `DockerComposeClassExtension`
- **Lifecycle**: Both CLASS and METHOD level
- **Mechanism**: JUnit 5 lifecycle callbacks (`@BeforeEach`, `@AfterEach`)
- **Used by**: JUnit 5 tests (if configured with annotations)

**The Problem**: Integration tests use **Spock + Gradle tasks** (System 1), which only supports CLASS-level lifecycle.
The METHOD-level support exists **only for JUnit 5** (System 2), which is a completely separate system.

## Impact Assessment

### Current State
- ✅ CLASS-level lifecycle: **WORKS** (lifecycle-class tests: 7/7 passing)
- ❌ METHOD-level lifecycle: **FAILS** (lifecycle-method tests: 2/7 failing)
- ✅ JUnit 5 users with `@ComposeUp(lifecycle=METHOD)`: **WORKS** (uses different system)
- ❌ Gradle task users expecting METHOD lifecycle: **BROKEN**

### Affected Users
1. **Integration test developers**: Cannot write tests that require fresh containers per test method using Gradle tasks
2. **Plugin users with Spock**: Cannot achieve METHOD-level isolation with current Gradle task approach
3. **Documentation accuracy**: Current docs may imply METHOD-level works with Gradle tasks (needs verification)

## Recommended Solutions

### Option 1: Create Spock Extension for METHOD Lifecycle (Recommended)

**Approach**: Build a Spock extension similar to `DockerComposeMethodExtension` that supports METHOD-level lifecycle.

**Implementation**:
1. Create `DockerComposeSpockExtension` implementing Spock's extension APIs
2. Use Spock interceptors to hook into `setup()` and `cleanup()` methods
3. Call `ComposeService` directly (bypassing Gradle tasks)
4. Register extension via Spock's `@UseExtension` annotation

**Pros**:
- Native Spock integration
- Consistent with JUnit 5 extension approach
- No changes to Gradle task system required
- Clean separation of concerns

**Cons**:
- Requires new extension development
- Different API from Gradle task approach
- May require changes to existing integration tests

**Example Usage**:
```groovy
@ComposeUp(stackName = "myStack", composeFile = "compose.yml", lifecycle = METHOD)
class MyIntegrationTest extends Specification {
    def "test 1"() {
        // Fresh containers
    }

    def "test 2"() {
        // Fresh containers again
    }
}
```

### Option 2: Re-architect Gradle Task System for Re-execution

**Approach**: Modify Gradle tasks to support multiple executions within a single test task.

**Implementation Challenges**:
1. Gradle's task graph is designed for single execution per task
2. Would require complex workarounds (e.g., multiple task instances)
3. May conflict with Gradle's configuration cache
4. Would introduce significant complexity

**Pros**:
- Keeps Gradle task approach
- No new extension system needed

**Cons**:
- ⚠️ **NOT RECOMMENDED** - fights against Gradle's design
- Complex implementation with high maintenance burden
- May break in future Gradle versions
- Configuration cache compatibility issues

### Option 3: Document Limitation and Recommend JUnit 5 for METHOD Lifecycle

**Approach**: Accept limitation and guide users to appropriate tools for each use case.

**Implementation**:
1. Document that Gradle tasks support only CLASS-level lifecycle
2. Recommend JUnit 5 extensions for METHOD-level needs
3. Update integration tests to reflect supported scenarios
4. Provide clear migration guide for users needing METHOD lifecycle

**Pros**:
- No code changes required
- Clear guidance for users
- Leverages existing JUnit 5 support

**Cons**:
- Limited functionality for Spock users
- May not meet all user requirements
- Leaves gap in integration test coverage

## Recommendation

**Primary Recommendation**: **Option 1 - Create Spock Extension**

This approach:
1. Provides native METHOD-level support for Spock (matching JUnit 5 capability)
2. Maintains architectural consistency (extensions for test lifecycle, tasks for build lifecycle)
3. Enables comprehensive integration test coverage
4. Aligns with plugin's design philosophy (separate concerns)

**Secondary Action**: **Option 3 - Document Current Limitations**

While implementing Option 1:
1. Document that Gradle tasks currently support only CLASS-level lifecycle
2. Add clear examples showing when to use tasks vs extensions
3. Update integration tests to properly reflect supported vs aspirational features

## Next Steps

1. **Immediate**: Update status documents to reflect this finding
2. **Short-term**: Create Spock extension for METHOD-level lifecycle
3. **Medium-term**: Update integration tests to use appropriate lifecycle mechanism
4. **Long-term**: Consider deprecating Gradle task approach for test lifecycle in favor of test framework extensions

## References

- **Task Implementation**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/ComposeUpTask.groovy:194`
- **Plugin Registration**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy:237-366`
- **JUnit Extension**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtension.groovy`
- **Test Failures**:
  `plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/build/reports/tests/integrationTest/index.html`
- **State File**:
  `plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/build/compose-state/lifecycleMethodTest-state.json:4`
