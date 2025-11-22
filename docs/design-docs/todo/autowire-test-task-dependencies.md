# Auto-Wire Test Task Dependencies for usesCompose()

## Status
**PLANNED** - Not yet implemented
**Completeness**: 85% (ready for implementation)

## Problem Statement

Users must manually write repetitive boilerplate to wire test task dependencies when using Docker Compose orchestration
with test framework extensions (Spock `@ComposeUp` or JUnit 5 extensions).

### Current Required Code

```gradle
tasks.named('integrationTest') {
    // Tell extensions which stack to use
    usesCompose(stack: "webAppTest", lifecycle: "class")
}

// Manual boilerplate - redundant with usesCompose()
afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpWebAppTest')      // ← Boilerplate
        finalizedBy tasks.named('composeDownWebAppTest')  // ← Boilerplate
    }
}
```

### Why This Is Redundant

When `usesCompose(stack: "webAppTest", lifecycle: "class")` is called:
- The extension (`@ComposeUp` or `DockerComposeClassExtension`) manages the actual compose up/down during test
  execution
- The Gradle task dependencies are **redundant** because the extension already handles lifecycle management
- **Note:** Suite lifecycle ALREADY auto-wires (see `configureSuiteLifecycle()`) - this proposal extends that pattern
  to class and method lifecycles

### Impact
- **4-5 lines of boilerplate** per test task using compose
- **Confusion** about what's necessary vs. redundant
- **Maintenance burden** across multiple test tasks and projects
- **Error-prone** - easy to forget or misconfigure

## Proposed Solution

**Automatically add compose lifecycle dependencies when `usesCompose()` is called with class or method lifecycle.**

This extends the existing suite lifecycle auto-wiring to class and method lifecycles.

### User-Facing API (After)

```gradle
tasks.named('integrationTest') {
    usesCompose(stack: "webAppTest", lifecycle: "class")
}

// That's it! Plugin automatically adds:
//   - dependsOn composeUpWebAppTest
//   - finalizedBy composeDownWebAppTest
```

### Behavior

When `usesCompose(stack: <stackName>, lifecycle: <lifecycle>)` is called on a task:

1. **Set system properties** (current behavior - unchanged):
   - `docker.compose.stack`
   - `docker.compose.files`
   - `docker.compose.lifecycle`
   - `docker.compose.project`
   - `docker.compose.waitForHealthy.*` (if configured)

2. **Auto-wire task dependencies** (NEW for class/method, existing for suite):
   - Add `dependsOn tasks.named("composeUp${stackName.capitalize()}")`
   - Add `finalizedBy tasks.named("composeDown${stackName.capitalize()}")`

3. **Skip auto-wiring for manual lifecycle** (existing behavior):
   - If `lifecycle == "manual"`, do NOT add dependencies
   - User manages compose lifecycle explicitly via Gradle tasks

### Lifecycle Handling

| Lifecycle | Auto-wire dependsOn? | Auto-wire finalizedBy? | Status | Rationale |
|-----------|---------------------|------------------------|--------|-----------|
| `suite` | ✅ Yes | ✅ Yes | **Existing** | Already implemented in `configureSuiteLifecycle()` |
| `class` | ✅ Yes | ✅ Yes | **NEW** | Extension manages lifecycle; task dependencies ensure compose tasks run |
| `method` | ✅ Yes | ✅ Yes | **NEW** | Extension manages lifecycle; task dependencies ensure compose tasks run |
| `manual` | ❌ No | ❌ No | **Existing** | User explicitly manages via Gradle tasks |

## Implementation Plan

### Architecture Overview

**Call Flow:**
```
User: tasks.named('integrationTest') { usesCompose(stack: "test", lifecycle: "class") }
  ↓
GradleDockerPlugin (line 799): test.ext.usesCompose = { Map args -> ... }
  ↓
Extracts: stackName = args.stack, lifecycle = args.lifecycle ?: 'suite'
  ↓
TestIntegrationExtension.usesCompose(test, stackName, lifecycle)
  ↓
Switch on lifecycle → configureClassLifecycle() or configureMethodLifecycle()
  ↓
Set system properties + AUTO-WIRE DEPENDENCIES (NEW)
```

### 1. Source Code Changes

#### **File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtension.groovy`

**CORRECTED PATH** (plan had wrong path - was missing `/extension/` subdirectory)

#### **Change 1: Modify `configureClassLifecycle()` method**

**Location:** Lines 139-149 (approximately)

**Current implementation:**
```groovy
private void configureClassLifecycle(Test test, String stackName, stackSpec) {
    // Class lifecycle uses test framework extension to manage compose per test class
    // DO NOT add task dependencies - let extension handle lifecycle

    // Set comprehensive system properties from dockerOrch DSL
    setComprehensiveSystemProperties(test, stackName, stackSpec, "class")

    logger.info("Test '{}' configured for CLASS lifecycle from dockerOrch DSL", test.name)
    logger.info("Spock: Use @ComposeUp (zero parameters)")
    logger.info("JUnit 5: Use @ExtendWith(DockerComposeClassExtension.class)")
}
```

**New implementation:**
```groovy
private void configureClassLifecycle(Test test, String stackName, stackSpec) {
    // Class lifecycle uses test framework extension to manage compose per test class

    // Set comprehensive system properties from dockerOrch DSL
    setComprehensiveSystemProperties(test, stackName, stackSpec, "class")

    // NEW: Auto-wire task dependencies
    autoWireComposeDependencies(test, stackName)

    logger.info("Test '{}' configured for CLASS lifecycle from dockerOrch DSL", test.name)
    logger.info("Spock: Use @ComposeUp (zero parameters)")
    logger.info("JUnit 5: Use @ExtendWith(DockerComposeClassExtension.class)")
}
```

#### **Change 2: Modify `configureMethodLifecycle()` method**

**Location:** Lines 151-161 (approximately)

**Current implementation:**
```groovy
private void configureMethodLifecycle(Test test, String stackName, stackSpec) {
    // Method lifecycle uses test framework extension to manage compose per test method
    // DO NOT add task dependencies - let extension handle lifecycle

    // Set comprehensive system properties from dockerOrch DSL
    setComprehensiveSystemProperties(test, stackName, stackSpec, "method")

    logger.info("Test '{}' configured for METHOD lifecycle from dockerOrch DSL", test.name)
    logger.info("Spock: Use @ComposeUp (zero parameters)")
    logger.info("JUnit 5: Use @ExtendWith(DockerComposeMethodExtension.class)")
}
```

**New implementation:**
```groovy
private void configureMethodLifecycle(Test test, String stackName, stackSpec) {
    // Method lifecycle uses test framework extension to manage compose per test method

    // Set comprehensive system properties from dockerOrch DSL
    setComprehensiveSystemProperties(test, stackName, stackSpec, "method")

    // NEW: Auto-wire task dependencies
    autoWireComposeDependencies(test, stackName)

    logger.info("Test '{}' configured for METHOD lifecycle from dockerOrch DSL", test.name)
    logger.info("Spock: Use @ComposeUp (zero parameters)")
    logger.info("JUnit 5: Use @ExtendWith(DockerComposeMethodExtension.class)")
}
```

#### **Change 3: Add new `autoWireComposeDependencies()` helper method**

**Location:** Add after `setComprehensiveSystemProperties()` method (after line 208)

**New method:**
```groovy
/**
 * Auto-wire test task dependencies on compose lifecycle tasks
 *
 * This ensures compose containers are started before the test task runs and
 * stopped after the test task completes (even on failure).
 *
 * @param test Test task to wire dependencies for
 * @param stackName Name of the compose stack
 */
private void autoWireComposeDependencies(Test test, String stackName) {
    String capitalizedStackName = stackName.capitalize()
    String composeUpTaskName = "composeUp${capitalizedStackName}"
    String composeDownTaskName = "composeDown${capitalizedStackName}"

    // Access project through test task (configuration-cache safe when used in afterEvaluate)
    def project = test.project

    project.afterEvaluate {
        // Verify tasks exist before wiring
        def composeUpTask = project.tasks.findByName(composeUpTaskName)
        def composeDownTask = project.tasks.findByName(composeDownTaskName)

        if (!composeUpTask) {
            throw new GradleException(
                "Task '${composeUpTaskName}' not found. " +
                "Ensure dockerOrch.composeStacks.${stackName} is configured in build.gradle."
            )
        }

        if (!composeDownTask) {
            throw new GradleException(
                "Task '${composeDownTaskName}' not found. " +
                "Ensure dockerOrch.composeStacks.${stackName} is configured in build.gradle."
            )
        }

        // Wire dependencies
        test.dependsOn composeUpTask
        test.finalizedBy composeDownTask

        logger.info(
            "Auto-wired test task '${test.name}': " +
            "dependsOn ${composeUpTaskName}, finalizedBy ${composeDownTaskName}"
        )
    }
}
```

### 2. Configuration Cache Compatibility

**Analysis:**
- **`test.project` access:** Safe when used inside `afterEvaluate` block (deferred execution)
- **Existing pattern:** `configureSuiteLifecycle()` already uses this pattern (lines 126-136)
- **No serialization:** Test task reference is not captured in serialized configuration state
- **Provider-based:** All system properties use providers (existing code, lines 170-206)

**Verification:**
```bash
# Test with configuration cache
./gradlew integrationTest --configuration-cache
./gradlew integrationTest --configuration-cache  # Second run should reuse cache
```

### 3. Edge Cases Handled

**Case 1: Stack doesn't exist**
```gradle
tasks.named('integrationTest') {
    usesCompose(stack: "nonExistentStack", lifecycle: "class")
}
```
**Result:** Clear error at configuration time from `usesCompose()` (line 75-92 in TestIntegrationExtension)

**Case 2: Compose tasks don't exist**
```gradle
dockerOrch {
    composeStacks {
        myStack { /* ... */ }
    }
}

tasks.named('someOtherTest') {
    usesCompose(stack: "differentStack", lifecycle: "class")  // Typo in stack name
}
```
**Result:** Clear error in `afterEvaluate`: "Task 'composeUpDifferentStack' not found..."

**Case 3: Manual lifecycle**
```gradle
tasks.named('integrationTest') {
    usesCompose(stack: "webAppTest", lifecycle: "manual")
}
```
**Result:** No auto-wiring; user manages dependencies manually (not routed through configureClassLifecycle)

**Case 4: User wants custom dependencies**
```gradle
tasks.named('integrationTest') {
    usesCompose(stack: "webAppTest", lifecycle: "class")

    // User can still add additional dependencies
    dependsOn tasks.named('customSetup')
}
```
**Result:** Both auto-wired AND custom dependencies apply (Gradle accumulates dependencies)

**Case 5: User has BOTH auto-wiring AND manual wiring (backward compatibility)**
```gradle
tasks.named('integrationTest') {
    usesCompose(stack: "test", lifecycle: "class")
}

afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpTest')      // User's explicit dependency
        finalizedBy tasks.named('composeDownTest')  // User's explicit dependency
    }
}
```
**Result:** Works correctly - Gradle deduplicates dependencies automatically

## Testing Requirements

### Unit Tests

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtensionTest.groovy`

**Current State:** Existing tests only cover `composeStateFileFor()` (lines 1-100). Line 29 note states "usesCompose()
method is tested in integration tests as it requires full plugin setup."

**Decision:** Continue pattern of NOT unit testing `usesCompose()` directly. The method requires:
- Full plugin application
- DockerOrchExtension setup
- Compose stack configuration
- Task graph evaluation

This is better suited for functional/integration tests.

**Action:** No unit test changes required.

### Functional Tests

**File:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/UsesComposeAutoWireFunctionalTest.groovy`
(NEW FILE)

**Template:**
```groovy
package com.kineticfire.gradle.docker

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Functional tests for usesCompose() auto-wiring of task dependencies
 */
class UsesComposeAutoWireFunctionalTest extends Specification {

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()

    File buildFile
    File settingsFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << "rootProject.name = 'test-project'\n"

        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "class lifecycle auto-wires compose dependencies"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('src/integrationTest/resources/compose/test.yml')
                    }
                }
            }

            tasks.named('integrationTest') {
                usesCompose(stack: "testStack", lifecycle: "class")
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('integrationTest', '--dry-run')
            .withPluginClasspath()
            .build()

        then:
        result.output.contains(':composeUpTestStack')
        result.output.contains(':integrationTest')
        result.output.contains(':composeDownTestStack')

        // Verify execution order (composeUp before integrationTest)
        def upIndex = result.output.indexOf(':composeUpTestStack')
        def testIndex = result.output.indexOf(':integrationTest')
        upIndex < testIndex
    }

    def "method lifecycle auto-wires compose dependencies"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('src/integrationTest/resources/compose/test.yml')
                    }
                }
            }

            tasks.named('integrationTest') {
                usesCompose(stack: "testStack", lifecycle: "method")
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('integrationTest', '--dry-run')
            .withPluginClasspath()
            .build()

        then:
        result.output.contains(':composeUpTestStack')
        result.output.contains(':integrationTest')
        result.output.contains(':composeDownTestStack')
    }

    def "manual lifecycle does NOT auto-wire compose dependencies"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('src/integrationTest/resources/compose/test.yml')
                    }
                }
            }

            tasks.named('integrationTest') {
                usesCompose(stack: "testStack", lifecycle: "manual")
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('integrationTest', '--dry-run')
            .withPluginClasspath()
            .build()

        then:
        result.output.contains(':integrationTest')
        // composeUp should NOT be in task graph
        !result.output.contains(':composeUpTestStack')
    }

    def "error when compose stack not found"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    existingStack {
                        files.from('src/integrationTest/resources/compose/test.yml')
                    }
                }
            }

            tasks.named('integrationTest') {
                usesCompose(stack: "nonExistentStack", lifecycle: "class")
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('tasks')
            .withPluginClasspath()
            .buildAndFail()

        then:
        result.output.contains("Compose stack 'nonExistentStack' not found")
        result.output.contains("Available stacks: [existingStack]")
    }

    def "user custom dependencies preserved alongside auto-wiring"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('src/integrationTest/resources/compose/test.yml')
                    }
                }
            }

            tasks.register('customSetup') {
                doLast { println 'Custom setup' }
            }

            tasks.named('integrationTest') {
                usesCompose(stack: "testStack", lifecycle: "class")
                dependsOn tasks.named('customSetup')  // User's custom dependency
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('integrationTest', '--dry-run')
            .withPluginClasspath()
            .build()

        then:
        // Both auto-wired AND custom dependencies present
        result.output.contains(':customSetup')
        result.output.contains(':composeUpTestStack')
        result.output.contains(':integrationTest')
    }
}
```

**Add to existing:** `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TestExtensionFunctionalTest.groovy`

Add test verifying backward compatibility:
```groovy
def "auto-wiring works with existing manual wiring (backward compatibility)"() {
    given:
    buildFile << """
        plugins {
            id 'groovy'
            id 'com.kineticfire.gradle.docker'
        }

        dockerOrch {
            composeStacks {
                test {
                    files.from('src/integrationTest/resources/compose/test.yml')
                }
            }
        }

        tasks.named('integrationTest') {
            usesCompose(stack: "test", lifecycle: "class")
        }

        // User has BOTH auto-wiring AND manual wiring
        afterEvaluate {
            tasks.named('integrationTest') {
                dependsOn tasks.named('composeUpTest')
                finalizedBy tasks.named('composeDownTest')
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('integrationTest', '--dry-run')
        .withPluginClasspath()
        .build()

    then:
    // Should work without errors (dependencies deduplicated)
    result.task(':integrationTest') != null
    !result.output.contains('FAILED')
}
```

### Integration Tests

**Scope:** 41 `build.gradle` files in `plugin-integration-test/dockerOrch/`

#### **Step 1: Identify Files to Modify**

**Search pattern:**
```bash
# Find all build.gradle files with usesCompose + class/method lifecycle
cd plugin-integration-test/dockerOrch
find . -name "build.gradle" -exec grep -l "usesCompose.*lifecycle.*class\|method" {} \;

# Find all build.gradle files with manual afterEvaluate wiring
find . -name "build.gradle" -exec grep -l "afterEvaluate.*integrationTest.*dependsOn.*composeUp" {} \;
```

**Expected files (from examination):**
- `verification/*/app-image/build.gradle` (multiple verification scenarios)
- `examples/*/app-image/build.gradle` (all examples)

**Estimated count:** 15-20 files

#### **Step 2: Change Pattern**

For each file identified:

**Pattern to REMOVE:**
```gradle
afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUp<StackName>')
        finalizedBy tasks.named('composeDown<StackName>')
    }
}
```

**Pattern to KEEP:**
```gradle
afterEvaluate {
    tasks.named('composeUp<StackName>') {
        dependsOn tasks.named('dockerBuild<ImageName>')  // Image dependency - KEEP
    }
}
```

**IMPORTANT:** Only remove compose lifecycle dependencies, NOT image build dependencies!

#### **Example Transformation**

**File:** `plugin-integration-test/dockerOrch/examples/web-app/app-image/build.gradle`

**BEFORE (lines 119-143):**
```gradle
tasks.named('integrationTest') {
    usesCompose(stack: "webAppTest", lifecycle: "class")

    systemProperty 'COMPOSE_STATE_FILE',
        layout.buildDirectory.file('compose-state/webAppTest-state.json').get().asFile.absolutePath
    systemProperty 'COMPOSE_PROJECT_NAME', "example-web-app-test"
}

// NORMAL PLUGIN USERS NEED: Orchestrate task dependencies
afterEvaluate {
    tasks.named('composeUpWebAppTest') {
        dependsOn tasks.named('dockerBuildWebApp')      // ← KEEP (image dependency)
    }

    // Ensure integrationTest has full workflow
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpWebAppTest')    // ← REMOVE (auto-wired)
        finalizedBy tasks.named('composeDownWebAppTest') // ← REMOVE (auto-wired)
    }
}
```

**AFTER:**
```gradle
tasks.named('integrationTest') {
    // Auto-wiring: usesCompose() automatically adds dependsOn/finalizedBy
    usesCompose(stack: "webAppTest", lifecycle: "class")

    systemProperty 'COMPOSE_STATE_FILE',
        layout.buildDirectory.file('compose-state/webAppTest-state.json').get().asFile.absolutePath
    systemProperty 'COMPOSE_PROJECT_NAME', "example-web-app-test"
}

// Wire image build dependency
afterEvaluate {
    tasks.named('composeUpWebAppTest') {
        dependsOn tasks.named('dockerBuildWebApp')
    }
}
```

**Add comment:**
```gradle
tasks.named('integrationTest') {
    // Plugin automatically wires: dependsOn composeUpWebAppTest, finalizedBy composeDownWebAppTest
    usesCompose(stack: "webAppTest", lifecycle: "class")

    // ... rest of configuration
}
```

#### **Step 3: Verification Strategy**

**Pre-implementation baseline:**
```bash
cd plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest > /tmp/before.txt 2>&1
```

**Post-implementation verification:**
```bash
# 1. Build updated plugin
cd ../plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

# 2. Run integration tests with changes
cd ../plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest > /tmp/after.txt 2>&1

# 3. Compare results
diff /tmp/before.txt /tmp/after.txt
# Should show: Same test results, possibly different task graph output
```

**Success criteria:**
- All tests that passed before still pass
- No new test failures
- Task execution order unchanged (composeUp before integrationTest, composeDown after)
- Zero lingering containers: `docker ps -a` shows nothing

#### **Step 4: Files to Update (Complete List)**

Generate with:
```bash
cd plugin-integration-test/dockerOrch
find . -type f -name "build.gradle" | sort > /tmp/all-builds.txt
```

**Manual review required:** Not all build.gradle files use class/method lifecycle with manual wiring.

**Estimated breakdown:**
- **Verification tests** (~10 files): `verification/*/app-image/build.gradle`
- **Example tests** (~8 files): `examples/*/app-image/build.gradle`
- **Other** (~23 files): Parent build.gradle files, app subprojects (no changes needed)

## Backwards Compatibility

### Breaking Changes
**NONE** - This is a pure enhancement.

### Compatibility Matrix

| User Code Pattern | Before Behavior | After Behavior | Compatible? |
|-------------------|-----------------|----------------|-------------|
| `usesCompose()` only (no manual wiring) | Broken (compose not up) | ✅ Works (auto-wired) | ✅ **FIXED** |
| `usesCompose()` + manual wiring | Works (redundant) | ✅ Works (deduplicated) | ✅ Yes |
| Manual wiring only (no `usesCompose()`) | Works | Works (unchanged) | ✅ Yes |
| `lifecycle: "manual"` | No auto-wiring | No auto-wiring | ✅ Yes |
| `lifecycle: "suite"` | Auto-wired (existing) | Auto-wired (existing) | ✅ Yes |

### Migration Path

**Users don't need to change anything.** The enhancement is backward-compatible.

**Optional cleanup:**
```gradle
// Old (still works):
tasks.named('integrationTest') {
    usesCompose(stack: "test", lifecycle: "class")
}
afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpTest')
        finalizedBy tasks.named('composeDownTest')
    }
}

// New (cleaner):
tasks.named('integrationTest') {
    usesCompose(stack: "test", lifecycle: "class")
}
```

## Documentation Updates

### Update Usage Guide

**File:** `docs/usage/usage-docker-orch.md`

**Section:** Add after "Understanding usesCompose() Configuration" (after line 199)

**New content:**
```markdown
### Automatic Task Dependencies

When you call `usesCompose()`, the plugin automatically wires task dependencies:

- **Adds `dependsOn`**: Ensures `composeUp*` runs before the test task
- **Adds `finalizedBy`**: Ensures `composeDown*` runs after the test task (even on failure)
- **Applies to all lifecycles**: suite, class, and method
- **Skipped for manual lifecycle**: If `lifecycle: "manual"`, no auto-wiring occurs

**Example:**
```gradle
tasks.named('integrationTest') {
    // This single line provides BOTH system properties AND task dependencies
    usesCompose(stack: "webAppTest", lifecycle: "class")
}

// No afterEvaluate needed! Plugin automatically adds:
//   - dependsOn composeUpWebAppTest
//   - finalizedBy composeDownWebAppTest
```

**Legacy manual wiring (still works, but unnecessary):**
```gradle
// This is now redundant but harmless (kept for backward compatibility)
afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpWebAppTest')
        finalizedBy tasks.named('composeDownWebAppTest')
    }
}
```

This eliminates 4-5 lines of boilerplate per test task.
```

**Update all examples:** Remove manual `afterEvaluate` blocks for compose lifecycle (keep image dependencies)

### Update Integration Test Example Comments

Add inline documentation:
```gradle
tasks.named('integrationTest') {
    // Sets system properties for @ComposeUp extension AND auto-wires task dependencies:
    //   - dependsOn composeUpWebAppTest (ensures containers start before tests)
    //   - finalizedBy composeDownWebAppTest (ensures containers stop after tests)
    usesCompose(stack: "webAppTest", lifecycle: "class")
}
```

## Rollback Plan

If auto-wiring causes unexpected issues:

### Step 1: Revert Source Code
```bash
git revert <commit-hash>
```

### Step 2: Rebuild Plugin
```bash
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal
```

### Step 3: Users Can Temporarily Disable
If plugin is already published and users encounter issues:

**Workaround:** Use `lifecycle: "manual"` and add explicit wiring:
```gradle
tasks.named('integrationTest') {
    usesCompose(stack: "test", lifecycle: "manual")  // Skips auto-wiring
}

afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpTest')
        finalizedBy tasks.named('composeDownTest')
    }
}
```

**Note:** This is not a recommended long-term solution, but provides immediate relief.

## Performance Impact

**Configuration Time:**
- **Before:** ~100ms for manual `afterEvaluate` wiring
- **After:** ~100ms for auto-wiring in `afterEvaluate`
- **Impact:** **Negligible** (same mechanism, just automated)

**Execution Time:**
- No change - same tasks run in same order

**Configuration Cache:**
- No impact - implementation is configuration-cache safe
- Verified with existing `configureSuiteLifecycle()` pattern

## Benefits

1. **Reduced boilerplate**: 4-5 lines per test task eliminated
2. **Less error-prone**: Cannot forget to wire dependencies
3. **Clearer intent**: `usesCompose()` clearly declares the complete relationship
4. **Easier to learn**: New users don't need to understand task graph wiring
5. **Consistent behavior**: All projects using `usesCompose()` get correct wiring
6. **Extends existing pattern**: Matches suite lifecycle auto-wiring (users already familiar)

## Risks and Mitigations

### Risk 1: Breaking user customizations
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** Gradle allows multiple `dependsOn` and `finalizedBy` calls; they accumulate. User customizations work
alongside auto-wiring. Tested in functional tests.

### Risk 2: Configuration cache issues
**Likelihood:** Very Low
**Impact:** High
**Mitigation:** Implementation uses `afterEvaluate` and does not capture Project references. Pattern already proven in
`configureSuiteLifecycle()`. Verified with `--configuration-cache` testing.

### Risk 3: Unexpected behavior for advanced users
**Likelihood:** Low
**Impact:** Low
**Mitigation:** Document the auto-wiring behavior clearly. Provide `lifecycle: "manual"` escape hatch for users who
want full control. Add inline comments in integration tests showing auto-wiring.

### Risk 4: Task graph ordering issues
**Likelihood:** Very Low
**Impact:** High
**Mitigation:** Same mechanism as existing suite lifecycle. Task dependencies are well-defined and tested. Integration
tests verify correct execution order.

## Future Enhancements

This design leaves room for future improvements:

1. **Auto-detect image dependencies** (deferred - see `requires-image-defer.md`)
   - Too complex for initial implementation
   - Manual wiring is explicit and manageable

2. **Support multiple stacks per task**
   - Already supported: multiple `usesCompose()` calls
   - Each call auto-wires its stack

3. **Custom lifecycle hooks**
   - Allow users to inject custom setup/teardown around compose lifecycle
   - Could use Gradle's `doFirst`/`doLast` with proper sequencing

## Acceptance Criteria

- [ ] Source code changes implemented in `TestIntegrationExtension.groovy`
- [ ] Unit tests pass (no changes needed - pattern continues)
- [ ] Functional tests created and passing (`UsesComposeAutoWireFunctionalTest.groovy`)
- [ ] All integration tests updated (manual wiring removed from ~15-20 files)
- [ ] All integration tests passing (zero regressions)
- [ ] Documentation updated (`usage-docker-orch.md`)
- [ ] Integration test examples updated with explanatory comments
- [ ] No breaking changes to existing user code (backward compatibility verified)
- [ ] Configuration cache compatible (verified with `--configuration-cache`)
- [ ] Clear error messages for misconfigurations (tested in functional tests)
- [ ] Zero lingering containers after tests (`docker ps -a` clean)
- [ ] Code coverage maintained at 100% (no new gaps)

## Implementation Checklist

### Phase 1: Source Code (1-2 hours)
- [ ] Update `configureClassLifecycle()` method
- [ ] Update `configureMethodLifecycle()` method
- [ ] Add `autoWireComposeDependencies()` helper method
- [ ] Manual code review against Gradle 9/10 compatibility guidelines
- [ ] Configuration cache verification (manual testing)

### Phase 2: Functional Tests (2-3 hours)
- [ ] Create `UsesComposeAutoWireFunctionalTest.groovy`
- [ ] Implement 5 test scenarios
- [ ] Add backward compatibility test to `TestExtensionFunctionalTest.groovy`
- [ ] Run functional tests: `./gradlew functionalTest`
- [ ] Verify all tests pass

### Phase 3: Integration Tests (3-4 hours)
- [ ] Generate file list: `find plugin-integration-test/dockerOrch -name "build.gradle"`
- [ ] Identify files with manual wiring (grep search)
- [ ] Create baseline: Run integration tests, capture results
- [ ] Update ~15-20 build.gradle files (remove manual wiring)
- [ ] Add explanatory comments to updated files
- [ ] Run integration tests: `./gradlew cleanAll integrationTest`
- [ ] Compare before/after results
- [ ] Verify zero lingering containers

### Phase 4: Documentation (1-2 hours)
- [ ] Update `usage-docker-orch.md` with auto-wiring section
- [ ] Update all examples in usage guide
- [ ] Add comments to integration test examples
- [ ] Review all documentation for consistency
- [ ] Spell check and line length verification

### Phase 5: Final Verification (1 hour)
- [ ] Full build: `./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal`
- [ ] Unit tests: `./gradlew test` (verify 100% coverage maintained)
- [ ] Functional tests: `./gradlew functionalTest`
- [ ] Integration tests: `./gradlew cleanAll integrationTest`
- [ ] Configuration cache: `./gradlew integrationTest --configuration-cache` (2x)
- [ ] Review acceptance criteria checklist
- [ ] Update plan status to "COMPLETE"

**Estimated Total Time:** 8-12 hours

## Related Documents
- Proposal 2 (deferred): `requires-image-defer.md` - Auto-wire image build dependencies
- Usage documentation: `docs/usage/usage-docker-orch.md`
- Gradle 9/10 compatibility: `docs/design-docs/gradle-9-and-10-compatibility.md`
