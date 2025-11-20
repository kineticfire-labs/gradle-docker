# Plan Status: Fix Configuration Duplication - Part 2 (Continuation)

**Status**: In Progress
**Priority**: High
**Created**: 2025-11-19
**Related**: See `docker-orch-dsl-annoations-fix.md` for original plan

---

## Purpose

This document tracks the continuation of the plan to fix configuration duplication between dockerOrch DSL and test
annotations. This is Part 2, documenting progress made on 2025-11-19 and remaining work.

---

## Work Completed in This Session

### Phase 3: Update Examples - PARTIALLY COMPLETE

#### ✅ Task 3.3: Updated isolated-tests Example (Spock)
**Files Modified:**
- `plugin-integration-test/dockerOrch/examples/isolated-tests/app-image/build.gradle`
  - Added `dockerOrch` DSL configuration with `isolatedTestsTest` stack
  - Added `usesCompose(stack: "isolatedTestsTest", lifecycle: "method")` to integrationTest task
- `plugin-integration-test/dockerOrch/examples/isolated-tests/app-image/src/integrationTest/groovy/com/kineticfire/test/IsolatedTestsExampleIT.groovy`
  - Removed all parameters from `@ComposeUp` annotation (now just `@ComposeUp`)
  - Removed unused `LifecycleMode` import
  - Updated documentation to explain all config comes from build.gradle

**Result**: ✅ Test passes, no containers remain

---

### Phase 5: Testing - PARTIALLY COMPLETE

#### ✅ Fixed Verification Test: lifecycle-class
**Files Modified:**
- `plugin-integration-test/dockerOrch/verification/lifecycle-class/app-image/build.gradle`
  - Added `dockerOrch` DSL configuration with `lifecycleClassTest` stack
  - Added `usesCompose(stack: "lifecycleClassTest", lifecycle: "class")` to integrationTest task
- `plugin-integration-test/dockerOrch/verification/lifecycle-class/app-image/src/integrationTest/groovy/com/kineticfire/test/LifecycleClassIT.groovy`
  - Removed unused `LifecycleMode` import
  - Updated documentation

**Issue Found**: Test was failing with `IllegalArgumentException` because `@ComposeUp` had no parameters but build.gradle
had no `dockerOrch` DSL configuration and no `usesCompose()` call.

**Result**: ✅ Test passes

---

#### ✅ Fixed Verification Test: lifecycle-method
**Files Modified:**
- `plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/build.gradle`
  - Added `dockerOrch` DSL configuration with `lifecycleMethodTest` stack
  - Added `usesCompose(stack: "lifecycleMethodTest", lifecycle: "method")` to integrationTest task
- `plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/src/integrationTest/groovy/com/kineticfire/test/LifecycleMethodIT.groovy`
  - Removed unused `LifecycleMode` import
  - Updated documentation

**Issue Found**: Same as lifecycle-class - missing configuration.

**Result**: ✅ Test passes

---

## Plan Status Analysis

### ✅ Phase 1: Core Infrastructure - ASSUMED COMPLETE
(Should have been done before this session)
- Task 1.1: Enhance TestIntegrationExtension.usesCompose()
- Task 1.2: Make @ComposeUp Annotation Parameters Optional
- Task 1.3: Update DockerComposeSpockExtension to Read System Properties

**Note**: This was plugin source code changes that should have been completed earlier in the plan implementation.

---

### ✅ Phase 2: JUnit 5 Extensions - ASSUMED COMPLETE
(Should have been done before this session)
- Task 2.1: Verify JUnit 5 Extensions

---

### ⚠️ Phase 3: Update Examples - **~25% COMPLETE**

**Completed:**
- ✅ Task 3.3: isolated-tests example (Spock)

**Remaining:**
- ❌ Task 3.1: Update web-app example (Spock)
- ❌ Task 3.2: Update stateful-web-app example (Spock)
- ❌ Task 3.4: Update database-app example (Spock)
- ❌ Task 3.5: Verify JUnit 5 examples (isolated-tests-junit, web-app-junit, etc.)
- ❌ Task 3.6: Update main examples README (`plugin-integration-test/dockerOrch/examples/README.md`)
- ❌ Task 3.7: Update individual example READMEs

**Critical Gap**: Only 1 of 4 Spock examples updated. JUnit 5 examples not verified. Documentation not updated.

---

### ❌ Phase 4: Update Documentation - **0% COMPLETE**

**All tasks NOT STARTED:**
- ❌ Task 4.1: Update `docs/usage/usage-docker-orch.md`
  - Add "Choosing a Test Framework" section with comparison table
  - Add Groovy/Spock vs Java/JUnit 5 examples
  - Show usesCompose as primary pattern for both frameworks
  - Document backward compatibility for Spock annotation parameters
  - Document JUnit 5 as already using parameter-less extensions
  - Update all code examples

- ❌ Task 4.2: Update `docs/design-docs/requirements/use-cases/uc-7-proj-dev-compose-orchestration.md`
  - Note that original vision is now fully implemented

---

### ⚠️ Phase 5: Testing - **~10% COMPLETE**

**Completed:**
- ✅ Fixed 2 verification tests: lifecycle-class, lifecycle-method
- ✅ Verified these specific tests pass
- ✅ Manually cleaned up containers after tests

**Remaining according to original plan:**

#### Task 5.1: Unit Tests - NOT VERIFIED
- ❌ Verify 100% line and branch coverage for modified code
- ❌ Verify all unit tests pass: `./gradlew test` (from `plugin/` directory)
- ❌ Verify conflict detection tests (FAIL when both DSL and annotation specify same parameter)
- ❌ Verify system property reading tests
- ❌ Verify fallback to annotation parameters tests (backward compatibility)

#### Task 5.2: Functional Tests - NOT VERIFIED
- ❌ Run all functional tests: `./gradlew functionalTest`
- ❌ Verify configuration cache compatibility
- ❌ Test usesCompose with CLASS and METHOD lifecycles (Spock)
- ❌ Test usesCompose with JUnit 5 extensions
- ❌ Test backward compatibility (annotation-only configuration still works)
- ❌ Test conflict detection scenarios (both DSL + annotation)
- ❌ Test error cases (missing configuration)

#### Task 5.3: Integration Tests - PARTIALLY VERIFIED
**Spock Examples - Status Unknown:**
- ❓ web-app integration test (likely needs fixing)
- ❓ stateful-web-app integration test (likely needs fixing)
- ✅ isolated-tests integration test (FIXED - passes)
- ❓ database-app integration test (likely needs fixing)

**JUnit 5 Examples - Status Unknown:**
- ❓ isolated-tests-junit CLASS lifecycle
- ❓ isolated-tests-junit METHOD lifecycle
- ❓ web-app-junit examples
- ❓ Any other JUnit 5 examples

**Verification Tests - Partially Verified:**
- ✅ lifecycle-class (FIXED - passes)
- ✅ lifecycle-method (FIXED - passes)
- ❓ basic verification test
- ❓ existing-images verification test
- ❓ logs-capture verification test
- ❓ mixed-wait verification test
- ❓ multi-service verification test
- ❓ wait-healthy verification test
- ❓ wait-running verification test

**Full Test Suite - NOT RUN:**
- ❌ Run full integration test suite: `./gradlew cleanAll integrationTest` (from plugin-integration-test/)
- ❌ Verify 100% pass rate (zero failures required)
- ❌ Verify no containers remain after full suite

---

## Critical Issues Found

### 🚨 Issue 1: Tests Leave Containers Running

**Problem**: Integration tests are leaving containers running after completion.

**Evidence**:
- After running isolated-tests: 0 containers (test cleaned up properly)
- After running lifecycle-class: 0 containers (test cleaned up properly)
- After running lifecycle-method: 16 containers left running!
  - 4 example containers (web-app, stateful-web-app, database-app + postgres)
  - 12 registry containers from docker scenarios

**Impact**: Violates project acceptance criteria (CLAUDE.md lines 137-139):
```
- **No lingering containers may remain.**
  - Do not declare success until `docker ps -a` shows no containers.
  - Do not treat "some leftover containers are acceptable" as valid.
```

**Root Cause**: Tests that ran before lifecycle-method did not clean up their containers. Likely issues:
1. Tests without proper `finalizedBy` for cleanup tasks
2. Tests that failed and didn't run cleanup hooks
3. Example tests (web-app, stateful-web-app, database-app) using CLASS lifecycle without cleanup

**Action Required**:
1. Investigate which tests are leaving containers
2. Add proper cleanup mechanisms (finalizedBy, try/finally, etc.)
3. Verify `docker ps -a` is clean after EACH test, not just manually after failures

---

### 🚨 Issue 2: Not All Examples Updated to New Pattern

**Problem**: Only 1 of 4+ Spock examples has been updated.

**Completed:**
- ✅ isolated-tests (METHOD lifecycle)

**Not Updated (likely still using annotation-only configuration):**
- ❌ web-app (CLASS lifecycle)
- ❌ stateful-web-app (CLASS lifecycle)
- ❌ database-app (CLASS lifecycle)

**Impact**:
- Examples show inconsistent patterns (old vs new)
- Users may copy old pattern from examples
- Cannot demonstrate "single source of truth" principle consistently

**Action Required**: Update remaining examples to use:
1. `dockerOrch` DSL in build.gradle
2. `usesCompose()` in integrationTest task
3. Zero-parameter `@ComposeUp` annotation

---

### 🚨 Issue 3: No Full Test Suite Run

**Problem**: We haven't verified that all tests pass together.

**Required per acceptance criteria** (original plan lines 1190-1194):
```
- [ ] `./gradlew clean build` passes (from plugin/ directory)
- [ ] `./gradlew functionalTest` passes
- [ ] `./gradlew publishToMavenLocal` succeeds
- [ ] `./gradlew cleanAll integrationTest` passes (from plugin-integration-test/ directory)
```

**What we've done:**
- Fixed individual failing tests one-by-one
- Manually verified specific tests pass
- Did NOT run full test suite

**Impact**:
- Unknown how many other tests are failing
- Unknown if our fixes broke other tests
- Cannot verify 100% pass rate requirement

**Action Required**: Run full test suite to discover all issues at once.

---

### ⚠️ Issue 4: JUnit 5 Examples Not Verified

**Problem**: Plan says JUnit 5 examples are "already correct by design" but we haven't verified them.

**Original plan assumption** (lines 177-197):
```
JUnit 5 (Java) - Already Correct, Needs Verification Only
- Extensions are parameter-less markers (already best practice!)
- Example: @ExtendWith(DockerComposeClassExtension.class) - no parameters
```

**What we need to verify:**
1. JUnit 5 examples actually use parameter-less extensions (assumption is correct)
2. JUnit 5 examples have `dockerOrch` DSL configuration in build.gradle
3. JUnit 5 examples have `usesCompose()` in integrationTest task
4. All JUnit 5 integration tests pass

**Action Required**: Review and test all JUnit 5 examples.

---

### ⚠️ Issue 5: Documentation Not Updated

**Problem**: Users still see old pattern in documentation.

**Files that need updates:**
- `docs/usage/usage-docker-orch.md` - Primary usage documentation
- `docs/design-docs/requirements/use-cases/uc-7-proj-dev-compose-orchestration.md` - Design doc
- `plugin-integration-test/dockerOrch/examples/README.md` - Main examples README
- Individual example READMEs (web-app, stateful-web-app, isolated-tests, database-app, etc.)

**Impact**: Even after all code is fixed, users won't know about the new recommended pattern.

**Action Required**: Complete Phase 4 after examples are working.

---

## Recommendations

### 1. Run Full Test Suite FIRST - CRITICAL

Before fixing more individual tests, discover all failures at once:

```bash
# Clean up any lingering containers
docker stop $(docker ps -aq) 2>/dev/null && docker rm $(docker ps -aq) 2>/dev/null

# Build plugin
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build functionalTest publishToMavenLocal

# Run all integration tests
cd ../plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest

# Verify no containers remain
docker ps -a
```

**Expected Outcome**: Will reveal all failing tests, not just the ones we've encountered.

**Why**: More efficient to see all failures than discovering them one-by-one.

---

### 2. Fix Remaining Spock Examples - HIGH PRIORITY

Update in this order (by complexity):
1. **web-app** (CLASS lifecycle, single service)
2. **stateful-web-app** (CLASS lifecycle, single service)
3. **database-app** (CLASS lifecycle, multi-service with postgres)

Each needs:
- Add `dockerOrch.composeStacks { ... }` configuration
- Add `usesCompose(stack: "...", lifecycle: "class")` to integrationTest task
- Remove all parameters from `@ComposeUp` annotation
- Remove unused `LifecycleMode` import
- Update documentation comments

---

### 3. Verify JUnit 5 Examples - HIGH PRIORITY

Check all JUnit 5 examples:
- `isolated-tests-junit` (CLASS and METHOD lifecycle)
- `web-app-junit` (if exists)
- Any others

Verify each has:
- `dockerOrch.composeStacks { ... }` configuration in build.gradle
- `usesCompose(stack: "...", lifecycle: "...")` in integrationTest task
- Parameter-less `@ExtendWith(DockerComposeClassExtension.class)` or `@ExtendWith(DockerComposeMethodExtension.class)`
- All tests pass

---

### 4. Investigate Container Cleanup - CRITICAL

**Debug approach:**
1. Run integration tests one subproject at a time
2. Check `docker ps -a` after EACH subproject
3. Identify which tests leave containers
4. Fix cleanup mechanisms

**Likely culprits:**
- Example tests using CLASS lifecycle (containers up for entire test class)
- Registry tests from docker scenarios
- Tests that fail before cleanup hooks run

**Possible fixes:**
- Add `finalizedBy` to ensure cleanup runs even on failure
- Use try/finally in test setup/cleanup
- Add Gradle task dependencies to ensure cleanup runs
- Add better error handling in cleanup tasks

---

### 5. Complete Phase 4 Documentation - MEDIUM PRIORITY

After all examples work, update documentation:
1. `docs/usage/usage-docker-orch.md` - Show usesCompose pattern for both Spock and JUnit 5
2. Add framework comparison section
3. Update all example READMEs
4. Update design documents

---

### 6. Final Verification - REQUIRED

Before declaring plan complete, verify ALL acceptance criteria (lines 1108-1201):

**Functional Requirements:**
- [ ] All Docker Compose configuration can be defined in build.gradle via dockerOrch DSL
- [ ] usesCompose method works for CLASS and METHOD lifecycles
- [ ] All @ComposeUp annotation parameters are optional (can use zero parameters)
- [ ] JUnit 5 @ExtendWith annotations remain parameter-less

**Extension Behavior:**
- [ ] Extensions read ALL configuration from system properties set by usesCompose
- [ ] Configuration priority works: system properties override annotation parameters
- [ ] Conflict detection works: FAIL when both sources specify same parameter
- [ ] Clear error messages when configuration missing

**Testing:**
- [ ] 100% unit test pass rate
- [ ] 100% functional test pass rate
- [ ] 100% integration test pass rate
- [ ] Zero test failures, zero test errors
- [ ] No containers left running after tests (`docker ps -a` is clean)

**Code Quality:**
- [ ] No compilation warnings
- [ ] Code follows project style guidelines

**Documentation:**
- [ ] usage-docker-orch.md shows usesCompose as primary pattern
- [ ] Framework comparison section added
- [ ] All examples use build.gradle configuration
- [ ] Example READMEs updated

---

## Work Breakdown - Remaining Tasks

### Immediate Next Steps (Priority Order)

#### Step 1: Discovery (30 minutes)
```bash
# Run full test suite to discover all failures
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build functionalTest publishToMavenLocal
cd ../plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest 2>&1 | tee test-results.txt
docker ps -a > containers-after-tests.txt
```

**Output**:
- List of all failing tests
- List of all lingering containers
- Scope of remaining work

---

#### Step 2: Fix Spock Examples (2-3 hours)
**For each of: web-app, stateful-web-app, database-app**
1. Add `dockerOrch` DSL configuration
2. Add `usesCompose()` to integrationTest task
3. Update test file (remove annotation parameters, update comments)
4. Run test to verify: `./gradlew -Pplugin_version=1.0.0 :dockerOrch:examples:<name>:app-image:integrationTest`
5. Verify no containers remain: `docker ps -a`

**Estimated time**: 30-45 minutes per example

---

#### Step 3: Verify JUnit 5 Examples (1-2 hours)
1. List all JUnit 5 example projects
2. Review each build.gradle and test file
3. Verify correct pattern (likely already correct)
4. Run each test to verify passes
5. Document any issues found

**Estimated time**: 15-30 minutes per example

---

#### Step 4: Fix Container Cleanup (2-4 hours)
1. Run tests one-by-one to identify which leave containers
2. Review cleanup mechanisms in each failing test
3. Add/fix finalizedBy, cleanup tasks, error handling
4. Re-run to verify cleanup works
5. Run full suite to verify `docker ps -a` is clean

**Estimated time**: Depends on number of tests with issues

---

#### Step 5: Update Documentation (2-3 hours)
1. Update `docs/usage/usage-docker-orch.md`
   - Add framework comparison section
   - Update all examples to show usesCompose
   - Document backward compatibility
2. Update `plugin-integration-test/dockerOrch/examples/README.md`
3. Update individual example READMEs (4-6 files)
4. Update design documents

**Estimated time**: 30 minutes per major doc

---

#### Step 6: Final Verification (30 minutes)
1. Run full test suite (unit + functional + integration)
2. Verify 100% pass rate
3. Verify `docker ps -a` is clean
4. Verify no compilation warnings
5. Check all acceptance criteria

---

## Success Metrics

**Plan is NOT complete until ALL of these are true:**

- [ ] **100% test pass rate** at all levels (unit, functional, integration)
- [ ] **Zero containers** after running full test suite (`docker ps -a` is empty)
- [ ] **All Spock examples** use zero-parameter `@ComposeUp` with config in build.gradle
- [ ] **All JUnit 5 examples** use parameter-less `@ExtendWith` with config in build.gradle
- [ ] **Documentation** shows usesCompose pattern as primary approach
- [ ] **No duplication** in any example or test
- [ ] **Build completes** without errors or warnings

---

## Overall Plan Progress

**Phase Completion:**
- Phase 1: ✅ 100% (assumed complete)
- Phase 2: ✅ 100% (assumed complete)
- Phase 3: ⚠️ ~25% (1 of 4 Spock examples, JUnit 5 not verified, no READMEs)
- Phase 4: ❌ 0% (no documentation updated)
- Phase 5: ⚠️ ~10% (2 verification tests fixed, full suite not verified)

**Overall Progress: ~40-50% complete**

---

## Notes

### Pattern Successfully Applied (3 times)

The following pattern has been successfully applied to fix failing tests:

**Before (fails with IllegalArgumentException):**
```groovy
// build.gradle - NO dockerOrch DSL
tasks.named('integrationTest') {
    // NO usesCompose call
}

// Test file
@ComposeUp  // No parameters, expects config from build.gradle
class MyTest extends Specification { }
```

**After (passes):**
```groovy
// build.gradle - ADD dockerOrch DSL
dockerOrch {
    composeStacks {
        myTestStack {
            files.from('src/integrationTest/resources/compose/my-test.yml')
            waitForHealthy {
                waitForServices.set(['my-service'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "myTestStack", lifecycle: "class")  // or "method"
    useJUnitPlatform()
    outputs.cacheIf { false }
}

// Test file - same
@ComposeUp  // No parameters! All config from build.gradle
class MyTest extends Specification { }
```

This pattern should be applied to all remaining examples and tests.

---

## Files Modified in This Session

1. `plugin-integration-test/dockerOrch/examples/isolated-tests/app-image/build.gradle`
2. `plugin-integration-test/dockerOrch/examples/isolated-tests/app-image/src/integrationTest/groovy/com/kineticfire/test/IsolatedTestsExampleIT.groovy`
3. `plugin-integration-test/dockerOrch/verification/lifecycle-class/app-image/build.gradle`
4. `plugin-integration-test/dockerOrch/verification/lifecycle-class/app-image/src/integrationTest/groovy/com/kineticfire/test/LifecycleClassIT.groovy`
5. `plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/build.gradle`
6. `plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/src/integrationTest/groovy/com/kineticfire/test/LifecycleMethodIT.groovy`

---

## Related Documents

- Original Plan: `docs/design-docs/todo/docker-orch-dsl-annoations-fix.md`
- Project Guidelines: `CLAUDE.md`
- Gradle 9/10 Compatibility: `docs/design-docs/gradle-9-and-10-compatibility.md`
- Usage Documentation: `docs/usage/usage-docker-orch.md`
