# Verification Test: METHOD Lifecycle

**Type**: Plugin Mechanics Validation
**Lifecycle**: METHOD (setup/cleanup)
**Purpose**: Verify that METHOD lifecycle works correctly - containers restart for each test method

## What This Test Validates

This verification test proves the METHOD lifecycle implementation works correctly:
- ✅ `setup()` is called before each test method
- ✅ Containers restart for each test method (fresh state)
- ✅ State does NOT persist across test methods (write in test N, verify gone in test N+1)
- ✅ `cleanup()` is called after each test method
- ✅ Cleanup happens multiple times (once per test)

## Key Test Points

**State Isolation Test**:
- Test 1: Writes `key=test1, value=method-1` to the application
- Test 2: Tries to read `test1` key and expects 404 (NOT FOUND)
- **This proves containers WERE restarted between tests**

**Lifecycle Verification**:
- Static counters track how many times `setup()` and `cleanup()` are called
- Tests verify these counters increase with each test

## Running Tests

From `plugin-integration-test/` directory:

```bash
# Full workflow
./gradlew -Pplugin_version=1.0.0-SNAPSHOT \
  dockerOrch:verification:lifecycle-method:app-image:runIntegrationTest

# Individual steps
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-method:app:bootJar
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-method:app-image:dockerBuildStateApp
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-method:app-image:composeUpLifecycleMethodTest
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-method:app-image:integrationTest
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:verification:lifecycle-method:app-image:composeDownLifecycleMethodTest
```

## Expected Results

All 7 tests should pass:
- ✅ setup called before each test
- ✅ State file is valid
- ✅ State written in test 1
- ✅ State from test 1 does NOT exist in test 2 (isolation verified!)
- ✅ State written in test 2
- ✅ No state from previous tests exists
- ✅ cleanup called after each test

## Validation Tools Used

- **`StateFileValidator`** - Validates state file structure
- **`DockerComposeValidator`** - Checks container state (running, healthy)
- **`CleanupValidator`** - Ensures no resource leaks (called multiple times)
- **RestAssured** - HTTP testing for state isolation verification

## When to Use METHOD Lifecycle

Use METHOD lifecycle when:
- Tests require clean, isolated state
- Each test should start from scratch
- Testing idempotency (can run in any order)
- Database rollback scenarios
- Testing with different configurations

**Trade-off**: Slower execution (containers restart for each test) but guarantees isolation.

## ⚠️ This is NOT a User Example

This test validates plugin mechanics. For user-facing examples, see `../../examples/isolated-tests/`
