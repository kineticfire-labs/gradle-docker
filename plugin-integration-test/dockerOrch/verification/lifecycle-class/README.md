# Verification Test: CLASS Lifecycle

**Type**: Plugin Mechanics Validation
**Lifecycle**: CLASS (setupSpec/cleanupSpec)
**Purpose**: Verify that CLASS lifecycle works correctly - containers persist across test methods

## What This Test Validates

This verification test proves the CLASS lifecycle implementation works correctly:
- ✅ `setupSpec()` is called exactly once before all tests
- ✅ Containers start once and remain running between test methods
- ✅ State persists across test methods (write in test N, read in test N+1)
- ✅ `cleanupSpec()` is called exactly once after all tests
- ✅ Cleanup removes all resources

## Key Test Points

**State Persistence Test**:
- Test 1: Writes `key=test, value=class-lifecycle` to the application
- Test 2: Reads back the same key and verifies value still exists
- **This proves containers were NOT restarted between tests**

**Lifecycle Verification**:
- Static counters track how many times `setupSpec()` and `cleanupSpec()` are called
- Tests verify these counters confirm single execution

## Running Tests

From `plugin-integration-test/` directory:

```bash
# Full workflow
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:lifecycle-class:app-image:runIntegrationTest

# Individual steps
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-class:app:bootJar
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-class:app-image:dockerBuildStateApp
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-class:app-image:composeUpLifecycleClassTest
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-class:app-image:integrationTest
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-class:app-image:composeDownLifecycleClassTest
```

## Expected Results

All 7 tests should pass:
- ✅ setupSpec called exactly once
- ✅ State file is valid
- ✅ Containers running between tests
- ✅ State persists (write test)
- ✅ State persists (read test)
- ✅ Containers still running after all tests
- ✅ cleanupSpec not yet called (will be after last test)

## Validation Tools Used

- **`StateFileValidator`** - Validates state file structure
- **`DockerComposeValidator`** - Checks container state (running, healthy)
- **`CleanupValidator`** - Ensures no resource leaks
- **RestAssured** - HTTP testing for state persistence

## ⚠️ This is NOT a User Example

This test validates plugin mechanics. For user-facing examples, see `../../examples/`
