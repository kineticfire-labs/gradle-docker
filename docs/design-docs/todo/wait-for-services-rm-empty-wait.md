# Design Document: Require Non-Empty `waitForServices` in `waitForHealthy` and `waitForRunning` Blocks

## Overview

### Problem Statement

The current `WaitSpec` class (used by `waitForHealthy` and `waitForRunning` DSL blocks) sets an empty list `[]` as the
default convention for `waitForServices`. This allows users to write empty or incomplete wait blocks that have no
semantic meaning:

```groovy
dockerTest {
    composeStacks {
        myTest {
            files.from('docker-compose.yml')
            waitForHealthy { }  // Empty block - accepted but meaningless
            waitForRunning {
                timeoutSeconds.set(120)  // Has timeout but no services - meaningless
            }
        }
    }
}
```

These configurations are silently ignored at runtime because `ComposeUpTask.performWaitIfConfigured()` checks
`!waitForServices.get().isEmpty()` before executing any wait logic. This leads to:

1. **Silent misconfiguration**: Users may think they configured waiting, but nothing happens
2. **Confusing behavior**: No error or warning is given
3. **Wasted effort**: Setting `timeoutSeconds` or `pollSeconds` in an empty block has no effect
4. **Violates fail-fast principle**: Invalid configurations should be caught early

### Solution

Remove the empty list convention from `WaitSpec.waitForServices` and add validation in `ComposeStackSpec` to require
at least one service when a `waitForHealthy` or `waitForRunning` block is configured. This follows the project's
"Fail Fast" and "Convention over Configuration" philosophies.

### Expected Outcome

After this change:

```groovy
// This will FAIL with a clear error message
waitForHealthy { }

// This will FAIL with a clear error message  
waitForRunning {
    timeoutSeconds.set(120)
}

// This will SUCCEED - at least one service specified
waitForHealthy {
    waitForServices.set(['app', 'db'])
    timeoutSeconds.set(60)
}
```

## Source Code Changes

### 1. `WaitSpec.groovy`

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/WaitSpec.groovy`

**Change**: Remove the `waitForServices.convention([])` line from the constructor.

**Before**:
```groovy
@Inject
WaitSpec() {
    timeoutSeconds.convention(60)
    pollSeconds.convention(2)
    waitForServices.convention([])  // Remove this line
}
```

**After**:
```groovy
@Inject
WaitSpec() {
    timeoutSeconds.convention(60)
    pollSeconds.convention(2)
    // waitForServices has no convention - must be explicitly set
}
```

**Rationale**: Without a convention, `waitForServices.present` returns `false` when not explicitly set, making it
easier to detect unconfigured blocks.

### 2. `ComposeStackSpec.groovy`

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ComposeStackSpec.groovy`

**Change**: Add validation in the `waitForHealthy(Closure)`, `waitForHealthy(Action)`, `waitForRunning(Closure)`, and
`waitForRunning(Action)` methods to require at least one service.

**Before** (example for `waitForHealthy(Closure)`):
```groovy
void waitForHealthy(@DelegatesTo(WaitSpec) Closure closure) {
    def waitSpec = objectFactory.newInstance(WaitSpec)
    closure.delegate = waitSpec
    closure.call()
    waitForHealthy.set(waitSpec)
}
```

**After**:
```groovy
void waitForHealthy(@DelegatesTo(WaitSpec) Closure closure) {
    def waitSpec = objectFactory.newInstance(WaitSpec)
    closure.delegate = waitSpec
    closure.call()
    validateWaitSpec(waitSpec, 'waitForHealthy')
    waitForHealthy.set(waitSpec)
}

void waitForHealthy(Action<WaitSpec> action) {
    def waitSpec = objectFactory.newInstance(WaitSpec)
    action.execute(waitSpec)
    validateWaitSpec(waitSpec, 'waitForHealthy')
    waitForHealthy.set(waitSpec)
}

void waitForRunning(@DelegatesTo(WaitSpec) Closure closure) {
    def waitSpec = objectFactory.newInstance(WaitSpec)
    closure.delegate = waitSpec
    closure.call()
    validateWaitSpec(waitSpec, 'waitForRunning')
    waitForRunning.set(waitSpec)
}

void waitForRunning(Action<WaitSpec> action) {
    def waitSpec = objectFactory.newInstance(WaitSpec)
    action.execute(waitSpec)
    validateWaitSpec(waitSpec, 'waitForRunning')
    waitForRunning.set(waitSpec)
}

/**
 * Validates that a WaitSpec has at least one service configured.
 * @param waitSpec The WaitSpec to validate
 * @param blockName The name of the DSL block for error messages
 * @throws GradleException if waitForServices is not set or is empty
 */
private void validateWaitSpec(WaitSpec waitSpec, String blockName) {
    if (!waitSpec.waitForServices.present || waitSpec.waitForServices.get().isEmpty()) {
        throw new GradleException(
            "Configuration error in '${blockName}' block for compose stack '${name}': " +
            "'waitForServices' must specify at least one service.\n\n" +
            "Example:\n" +
            "    ${blockName} {\n" +
            "        waitForServices.set(['service1', 'service2'])\n" +
            "        timeoutSeconds.set(60)\n" +
            "    }\n\n" +
            "If you don't need to wait for services, remove the empty '${blockName}' block."
        )
    }
}
```

**Note**: Import `org.gradle.api.GradleException` if not already imported.

### 3. No Changes Required to Task Classes

The following files already handle empty service lists correctly and require **no changes**:

- `ComposeUpTask.groovy` - Already checks `!waitForHealthyServices.get().isEmpty()` before waiting
- `GradleDockerPlugin.groovy` - Already checks `waitSpec.waitForServices.present` before setting task properties
- `TestStepExecutor.groovy` - Already handles empty services correctly
- `TestIntegrationExtension.groovy` - Already uses `getOrElse([])` pattern

These remain as defensive checks but will no longer be triggered since validation happens earlier.

## Unit Test Changes

### 1. `WaitSpecTest.groovy`

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/WaitSpecTest.groovy`

**Changes Required**:

1. **Update test**: `"waitForServices has convention of empty list"` - This test should be changed to verify that
   `waitForServices` has no convention (is not present when not set).

   **Before**:
   ```groovy
   def "waitForServices has convention of empty list"() {
       expect:
       waitSpec.waitForServices.present
       waitSpec.waitForServices.get() == []
       waitSpec.waitForServices.get().isEmpty()
   }
   ```

   **After**:
   ```groovy
   def "waitForServices has no convention - must be explicitly set"() {
       expect:
       !waitSpec.waitForServices.present
   }
   ```

2. **Update test**: `"services is initially empty when not configured"` - Similar change.

   **Before**:
   ```groovy
   def "services is initially empty when not configured"() {
       expect:
       waitSpec.waitForServices.get().isEmpty()
   }
   ```

   **After**:
   ```groovy
   def "services is not present when not configured"() {
       expect:
       !waitSpec.waitForServices.present
   }
   ```

3. **Update test**: `"empty services list is supported"` - This test should verify that an explicitly set empty list
   is technically possible (even though validation will reject it at a higher level).

   **Before**:
   ```groovy
   def "empty services list is supported"() {
       when:
       waitSpec.waitForServices.set([])

       then:
       waitSpec.waitForServices.present
       waitSpec.waitForServices.get().isEmpty()
   }
   ```

   **After**: Keep this test as-is. At the `WaitSpec` level, an empty list can still be set (the validation happens
   in `ComposeStackSpec`). This test documents the Property API behavior.

4. **Update test**: `"constructor initializes with defaults"` - Remove assertion about `waitForServices`.

   **Before**:
   ```groovy
   def "constructor initializes with defaults"() {
       expect:
       waitSpec != null
       waitSpec.timeoutSeconds.get() == 60
       waitSpec.pollSeconds.get() == 2
   }
   ```

   **After**: Keep as-is (it doesn't assert `waitForServices` convention).

### 2. `ComposeStackSpecTest.groovy`

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/ComposeStackSpecTest.groovy`

**Changes Required**:

1. **Add new tests** for validation error cases:

   ```groovy
   // ===== WAIT FOR HEALTHY VALIDATION TESTS =====

   def "waitForHealthy(Closure) throws exception when waitForServices is empty"() {
       when:
       composeStack.waitForHealthy {
           timeoutSeconds.set(60)
           // No services specified
       }

       then:
       def e = thrown(GradleException)
       e.message.contains("Configuration error in 'waitForHealthy' block")
       e.message.contains("'waitForServices' must specify at least one service")
       e.message.contains(composeStack.name)
   }

   def "waitForHealthy(Closure) throws exception when waitForServices is explicitly empty list"() {
       when:
       composeStack.waitForHealthy {
           waitForServices.set([])
           timeoutSeconds.set(60)
       }

       then:
       def e = thrown(GradleException)
       e.message.contains("Configuration error in 'waitForHealthy' block")
       e.message.contains("'waitForServices' must specify at least one service")
   }

   def "waitForHealthy(Action) throws exception when waitForServices is empty"() {
       when:
       composeStack.waitForHealthy(new Action<WaitSpec>() {
           @Override
           void execute(WaitSpec waitSpec) {
               waitSpec.timeoutSeconds.set(60)
               // No services specified
           }
       })

       then:
       def e = thrown(GradleException)
       e.message.contains("Configuration error in 'waitForHealthy' block")
   }

   // ===== WAIT FOR RUNNING VALIDATION TESTS =====

   def "waitForRunning(Closure) throws exception when waitForServices is empty"() {
       when:
       composeStack.waitForRunning {
           timeoutSeconds.set(60)
           // No services specified
       }

       then:
       def e = thrown(GradleException)
       e.message.contains("Configuration error in 'waitForRunning' block")
       e.message.contains("'waitForServices' must specify at least one service")
       e.message.contains(composeStack.name)
   }

   def "waitForRunning(Closure) throws exception when waitForServices is explicitly empty list"() {
       when:
       composeStack.waitForRunning {
           waitForServices.set([])
           timeoutSeconds.set(60)
       }

       then:
       def e = thrown(GradleException)
       e.message.contains("Configuration error in 'waitForRunning' block")
       e.message.contains("'waitForServices' must specify at least one service")
   }

   def "waitForRunning(Action) throws exception when waitForServices is empty"() {
       when:
       composeStack.waitForRunning(new Action<WaitSpec>() {
           @Override
           void execute(WaitSpec waitSpec) {
               waitSpec.timeoutSeconds.set(60)
               // No services specified
           }
       })

       then:
       def e = thrown(GradleException)
       e.message.contains("Configuration error in 'waitForRunning' block")
   }
   ```

2. **Update existing tests** that configure `waitForHealthy` or `waitForRunning` - ensure they specify services:

   Review tests at lines ~165-213 in `ComposeStackSpecTest.groovy`. These tests already set `waitForServices`, so
   they should pass without modification. Verify each test that calls `waitForHealthy { }` or `waitForRunning { }`
   includes `waitForServices.set([...])`.

### 3. `ComposeUpTaskTest.groovy`

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/ComposeUpTaskTest.groovy`

**Changes Required**:

Review tests starting at line ~366. Several tests configure `waitForHealthy` or `waitForRunning` blocks. Update tests
that have empty or missing `waitForServices`:

1. **Lines 378-383**: Test `"composeUp waits for healthy services when configured"` - Already has a comment about
   DSL limitation; verify the extension configuration includes services or restructure the test.

2. **Lines 417-421**: Test `"composeUp waits for running services when configured"` - Similar issue.

3. **Lines 455-465**: Test `"composeUp waits for mixed states when both configured"` - Similar issue.

4. **Lines 486-498**: Test `"composeUp does not wait when no wait configured"` - This test is valid and should
   remain unchanged (tests the case where no wait block is configured at all).

For tests that configure wait blocks through the extension DSL (lines ~366-484), the tests need to be updated to
either:
- Include `waitForServices.set([...])` in the wait block, OR
- Restructure to test the validation error case, OR
- Configure the task properties directly without going through the extension DSL

### 4. `TestStepExecutorTest.groovy`

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/workflow/executor/TestStepExecutorTest.groovy`

**Changes Required**:

1. **Lines 1189-1200**: Test `"setWaitSpecSystemProperties does not set empty services"` - This test sets
   `waitForServices.set([])`. The test is still valid at the executor level (testing the method's handling of empty
   lists), but consider if this scenario is still reachable given upstream validation.

2. **Lines 1331-1347**: Test `"setWaitSpecSystemProperties uses convention values when not explicitly set"` - This
   test relies on the empty convention. Update to explicitly set an empty list or test the "not present" case:

   **After**:
   ```groovy
   def "setWaitSpecSystemProperties handles unset waitForServices"() {
       given:
       def gradleTestTask = project.tasks.create('conventionTest', GradleTestTask)
       def waitSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
       // waitForServices is not set (no convention anymore)

       when:
       executor.setWaitSpecSystemProperties(gradleTestTask, 'docker.compose.wait', waitSpec)

       then:
       noExceptionThrown()
       // Unset services means no services property set
       !gradleTestTask.systemProperties.containsKey('docker.compose.wait.services')
       // But timeout and poll get their convention values
       gradleTestTask.systemProperties['docker.compose.wait.timeoutSeconds'] == '60'
       gradleTestTask.systemProperties['docker.compose.wait.pollSeconds'] == '2'
   }
   ```

## Functional Test Changes

### Files to Review

Search for functional tests that configure `waitForHealthy` or `waitForRunning`:

```bash
rg "waitForHealthy|waitForRunning" plugin/src/functionalTest --type groovy
```

**Expected Changes**:

1. **Add new functional tests** to verify the validation error is properly raised during Gradle configuration:

   ```groovy
   def "build fails when waitForHealthy block has no services"() {
       given:
       buildFile << """
           plugins {
               id 'com.kineticfire.gradle-docker'
           }

           dockerTest {
               composeStacks {
                   testStack {
                       files.from('docker-compose.yml')
                       waitForHealthy {
                           timeoutSeconds.set(60)
                       }
                   }
               }
           }
       """
       file('docker-compose.yml') << "services:\n  app:\n    image: alpine"

       when:
       def result = GradleRunner.create()
           .withProjectDir(testProjectDir)
           .withPluginClasspath()
           .withArguments('tasks')
           .buildAndFail()

       then:
       result.output.contains("Configuration error in 'waitForHealthy' block")
       result.output.contains("'waitForServices' must specify at least one service")
   }

   def "build fails when waitForRunning block has no services"() {
       given:
       buildFile << """
           plugins {
               id 'com.kineticfire.gradle-docker'
           }

           dockerTest {
               composeStacks {
                   testStack {
                       files.from('docker-compose.yml')
                       waitForRunning {
                           timeoutSeconds.set(60)
                       }
                   }
               }
           }
       """
       file('docker-compose.yml') << "services:\n  app:\n    image: alpine"

       when:
       def result = GradleRunner.create()
           .withProjectDir(testProjectDir)
           .withPluginClasspath()
           .withArguments('tasks')
           .buildAndFail()

       then:
       result.output.contains("Configuration error in 'waitForRunning' block")
       result.output.contains("'waitForServices' must specify at least one service")
   }
   ```

2. **Update existing functional tests** that may have empty wait blocks - ensure they either:
   - Specify services, OR
   - Test the error case explicitly

## Integration Test Changes

### Files to Review

Check integration test scenarios:

```bash
rg "waitForHealthy|waitForRunning" plugin-integration-test --type groovy
```

**Expected Changes**:

Integration tests should already have properly configured wait blocks with services (since they test real Docker
behavior). Review each occurrence to verify `waitForServices` is set. If any test has an empty wait block, it
should be updated.

No new integration tests are needed specifically for this validation change, as the validation occurs during Gradle
configuration (before Docker is involved). The functional tests cover the validation error cases.

## Documentation Changes

### 1. `docs/usage/usage-docker-orch.md`

**Change**: Update documentation to clarify that `waitForServices` is required when using `waitForHealthy` or
`waitForRunning` blocks.

Add a note in the DSL reference section:

```markdown
### Required Properties

When using `waitForHealthy` or `waitForRunning` blocks, the `waitForServices` property is **required**. The build
will fail if you configure an empty wait block:

```groovy
// INVALID - will fail
waitForHealthy {
    timeoutSeconds.set(60)
}

// VALID - at least one service specified
waitForHealthy {
    waitForServices.set(['app', 'db'])
    timeoutSeconds.set(60)
}
```

If you don't need to wait for services, simply omit the `waitForHealthy` or `waitForRunning` block entirely.
```

## Acceptance Criteria

1. [ ] `WaitSpec.waitForServices` no longer has an empty list convention
2. [ ] `ComposeStackSpec.waitForHealthy(Closure)` throws `GradleException` when `waitForServices` is empty or not set
3. [ ] `ComposeStackSpec.waitForHealthy(Action)` throws `GradleException` when `waitForServices` is empty or not set
4. [ ] `ComposeStackSpec.waitForRunning(Closure)` throws `GradleException` when `waitForServices` is empty or not set
5. [ ] `ComposeStackSpec.waitForRunning(Action)` throws `GradleException` when `waitForServices` is empty or not set
6. [ ] Error message includes the stack name, block name, and example of correct usage
7. [ ] All unit tests pass (update tests as described above)
8. [ ] All functional tests pass (add validation error tests, update existing tests)
9. [ ] All integration tests pass (verify existing tests have services configured)
10. [ ] Documentation updated to reflect required `waitForServices` property

## Risk Assessment

**Risk Level**: Low

**Breaking Change**: Yes, but minimal impact.

- Users with empty `waitForHealthy { }` or `waitForRunning { }` blocks will see build failures
- The fix is straightforward: either add services or remove the empty block
- Empty blocks had no effect anyway, so the functional change is minimal

**Migration Path**:

Users encountering the new error can:
1. Add the required services: `waitForServices.set(['service1', 'service2'])`
2. Remove the empty block if waiting is not needed

The error message provides clear guidance on both options.
