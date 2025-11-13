# Follow-up Plan: Integration Test Source Set Convention

**Status**: Action Required
**Date**: 2025-10-25
**Related**: `boilerplate-dsl.md`, `boilerplate-dsl-plan.md`
**Author**: Analysis by Claude Code

## Executive Summary

The integration test source set convention implementation is **highly successful** with **one critical gap** identified:

- ✅ **Implementation Quality**: 9.5/10 - Outstanding work
- ⚠️ **Critical Issue**: 2 Docker integration test scenarios (scenario-13, scenario-99) are **NOT being executed**
- ✅ **dockerOrch tests**: All 11 tests properly configured and running
- ❌ **Docker tests**: 12 of 14 tests running, 2 missing from aggregator

This document provides detailed findings and actionable recommendations to complete the implementation.

---

## Detailed Findings: Integration Test Execution Status

### Overall Structure

**Root aggregator**: `plugin-integration-test/build.gradle`
- ✅ Depends on `:docker:integrationTest`
- ✅ Depends on `:dockerOrch:integrationTest`
- ✅ Container cleanup and verification tasks configured
- ✅ Proper task orchestration at root level

### dockerOrch Integration Tests
**Status**: ✅ **ALL TESTS CONFIGURED AND RUNNING**

**File**: `plugin-integration-test/dockerOrch/build.gradle` (lines 31-53)

**Verification tests** (6 scenarios):
1. ✅ `:dockerOrch:verification:basic:app-image:integrationTest`
2. ✅ `:dockerOrch:verification:lifecycle-class:app-image:integrationTest`
3. ✅ `:dockerOrch:verification:lifecycle-method:app-image:integrationTest`
4. ✅ `:dockerOrch:verification:wait-healthy:app-image:integrationTest`
5. ✅ `:dockerOrch:verification:wait-running:app-image:integrationTest`
6. ✅ `:dockerOrch:verification:mixed-wait:app-image:integrationTest`

**Example tests** (5 scenarios):
1. ✅ `:dockerOrch:examples:web-app:app-image:integrationTest`
2. ✅ `:dockerOrch:examples:web-app-junit:app-image:integrationTest`
3. ✅ `:dockerOrch:examples:stateful-web-app:app-image:integrationTest`
4. ✅ `:dockerOrch:examples:isolated-tests:app-image:integrationTest`
5. ✅ `:dockerOrch:examples:isolated-tests-junit:app-image:integrationTest`

**Total**: 11 dockerOrch tests - ✅ **ALL CONFIGURED**

**Verification Method**:
```bash
# All 11 tests properly listed in dockerOrch/build.gradle integrationTest task
$ grep "dependsOn.*:dockerOrch.*:integrationTest" plugin-integration-test/dockerOrch/build.gradle | wc -l
11
```

---

### Docker Integration Tests
**Status**: ❌ **CRITICAL GAP FOUND - 2 TESTS NOT RUNNING**

**File**: `plugin-integration-test/docker/build.gradle` (lines 54-71)

#### Current Configuration

**Tests included in aggregator** (12 scenarios):
```groovy
tasks.register('integrationTest') {
    description = 'Run integration tests for all Docker projects'
    group = 'verification'

    dependsOn providers.provider { project(':docker:scenario-1').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-2').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-3').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-4').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-5').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-6').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-7').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-8').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-9').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-10').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-11').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-12').tasks.named('integrationTest') }
    // ❌ scenario-13 MISSING from this list
    // ❌ scenario-99 MISSING from this list
}
```

#### Evidence of Missing Tests

**Actual scenario directories on disk** (14 total):
```bash
$ ls -1d plugin-integration-test/docker/scenario-*
scenario-1
scenario-2
scenario-3
scenario-4
scenario-5
scenario-6
scenario-7
scenario-8
scenario-9
scenario-10
scenario-11
scenario-12
scenario-13  ← EXISTS but NOT in docker/build.gradle aggregator task
scenario-99  ← EXISTS but NOT in docker/build.gradle aggregator task
```

**Verification**:
```bash
$ ls -1d /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/docker/scenario-* | wc -l
14

$ grep "scenario-13\|scenario-99" /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/docker/build.gradle
# (no output - these scenarios are missing from the aggregator)
```

**Settings.gradle verification** (plugin-integration-test/settings.gradle lines 39-52):
```groovy
// Docker scenarios 1-12 included
include 'docker:scenario-13'  ← Declared in settings.gradle ✅
include 'docker:scenario-99'  ← Declared in settings.gradle ✅
```

**Analysis**:
- Scenarios 13 and 99 **exist as valid subprojects**
- They are **declared in settings.gradle** (can be built)
- They are **NOT included in docker/build.gradle aggregator task**
- They can be run individually but **won't execute during `./gradlew integrationTest`**

#### Root Cause Analysis

**Primary issue**: `plugin-integration-test/docker/build.gradle` has two locations where scenarios must be listed:

1. **integrationTest task** (lines 54-71):
   - Currently lists scenarios 1-12
   - Missing scenarios 13 and 99

2. **cleanAll task** (lines 74-91):
   - Currently lists scenarios 1-12
   - Missing scenarios 13 and 99

**Impact Assessment**:

1. **Test Coverage Gap**: Unknown functionality in scenarios 13 and 99 is not being verified
2. **CLAUDE.md Violation**: "Do not declare success until every test passes" (lines 128-133)
3. **Silent Failure**: Running `./gradlew integrationTest` appears successful but skips 2 tests
4. **Cleanup Gap**: `./gradlew cleanAll` won't clean scenarios 13 and 99

**Risk Level**: ⚠️ **HIGH**
- Integration tests are the final verification before production
- Missing tests could hide defects in docker functionality
- Creates false confidence in "all tests passing"

---

## Recommendations

### Priority 1: Fix Missing Integration Tests ⚠️ CRITICAL

**Objective**: Include scenario-13 and scenario-99 in the docker integration test aggregator

**Status**: Must be completed before declaring implementation success

#### Step 1.1: Add Missing Tests to integrationTest Task

**File**: `plugin-integration-test/docker/build.gradle`
**Lines to modify**: 59-71

**Current code** (lines 59-71):
```groovy
    dependsOn providers.provider { project(':docker:scenario-9').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-10').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-11').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-12').tasks.named('integrationTest') }
}
```

**Required changes**:
```groovy
    dependsOn providers.provider { project(':docker:scenario-9').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-10').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-11').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-12').tasks.named('integrationTest') }
    dependsOn providers.provider { project(':docker:scenario-13').tasks.named('integrationTest') }  // ADD THIS LINE
    dependsOn providers.provider { project(':docker:scenario-99').tasks.named('integrationTest') }  // ADD THIS LINE
}
```

**Rationale**:
- Maintains consistency with existing pattern
- Uses Provider API (Gradle 9/10 compatible)
- Ensures all scenarios are tested

#### Step 1.2: Add Missing Tests to cleanAll Task

**File**: `plugin-integration-test/docker/build.gradle`
**Lines to modify**: 79-91

**Current code** (lines 79-91):
```groovy
    dependsOn providers.provider { project(':docker:scenario-9').tasks.named('clean') }
    dependsOn providers.provider { project(':docker:scenario-10').tasks.named('clean') }
    dependsOn providers.provider { project(':docker:scenario-11').tasks.named('clean') }
    dependsOn providers.provider { project(':docker:scenario-12').tasks.named('clean') }
}
```

**Required changes**:
```groovy
    dependsOn providers.provider { project(':docker:scenario-9').tasks.named('clean') }
    dependsOn providers.provider { project(':docker:scenario-10').tasks.named('clean') }
    dependsOn providers.provider { project(':docker:scenario-11').tasks.named('clean') }
    dependsOn providers.provider { project(':docker:scenario-12').tasks.named('clean') }
    dependsOn providers.provider { project(':docker:scenario-13').tasks.named('clean') }  // ADD THIS LINE
    dependsOn providers.provider { project(':docker:scenario-99').tasks.named('clean') }  // ADD THIS LINE
}
```

**Rationale**:
- Ensures cleanup happens for all scenarios
- Prevents build artifact accumulation
- Maintains consistency with integrationTest task

#### Step 1.3: Verification Steps

After making the changes, verify the fix:

**Command 1**: Verify all 14 scenarios are now included
```bash
cd plugin-integration-test/docker
grep "scenario-.*integrationTest" build.gradle | wc -l
# Expected output: 14
```

**Command 2**: Verify scenarios can be resolved
```bash
cd plugin-integration-test
./gradlew :docker:scenario-13:tasks --all
./gradlew :docker:scenario-99:tasks --all
# Both commands should succeed and show available tasks
```

**Command 3**: Build plugin and run all integration tests
```bash
# Build plugin
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

# Run all integration tests (should now include scenarios 13 and 99)
cd ../plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest
```

**Command 4**: Verify no lingering containers
```bash
docker ps -a
# Expected: No containers (or only unrelated containers)
```

**Success Criteria**:
- ✅ All 14 docker scenarios execute during `./gradlew integrationTest`
- ✅ Build completes with BUILD SUCCESSFUL
- ✅ Zero test failures
- ✅ Zero lingering containers
- ✅ Both scenario-13 and scenario-99 appear in test output

---

### Priority 2: Improve Integration Test Documentation

**Objective**: Update project documentation to reflect the convention and provide migration guidance

**Status**: Enhancement - complete after Priority 1 is verified

#### Step 2.1: Update Usage Documentation

**File**: `docs/usage/usage-docker-orch.md`

**Location**: Add new section after existing content (estimated line ~100+)

**Section Title**: `## Integration Test Source Set Convention`

**Content to add**:

```markdown
## Integration Test Source Set Convention

The gradle-docker plugin automatically creates an `integrationTest` source set when the java or groovy plugin
is applied to your project. This eliminates the need for manual boilerplate configuration.

### Automatic Setup

When you apply the gradle-docker plugin to a project with the java or groovy plugin, the plugin automatically
provides:

1. **Source directories**:
   - `src/integrationTest/java` (always configured)
   - `src/integrationTest/groovy` (when groovy plugin is applied)
   - `src/integrationTest/resources` (always configured)

2. **Configurations**:
   - `integrationTestImplementation` (extends `testImplementation`)
   - `integrationTestRuntimeOnly` (extends `testRuntimeOnly`)

3. **Tasks**:
   - `integrationTest` - runs all integration tests using JUnit Platform
   - `processIntegrationTestResources` - processes test resources with INCLUDE duplicatesStrategy

4. **Classpath**:
   - Integration tests automatically have access to main source set classes
   - Integration tests inherit dependencies from test configurations

### Minimal Configuration Example

```groovy
plugins {
    id 'groovy'  // or 'java'
    id 'com.kineticfire.gradle.docker'
}

docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['latest']
            context.set(file('src/main/docker'))
        }
    }
}

dockerOrch {
    composeStacks {
        myTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = "my-app-test"
            waitForHealthy {
                waitForServices.set(['my-app'])
            }
        }
    }
}

// Add integration test dependencies
dependencies {
    integrationTestImplementation 'org.spockframework:spock-core:2.3'
    // or for JUnit:
    // integrationTestImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
}

// Wire Docker operations to integration tests
afterEvaluate {
    tasks.named('composeUpMyTest') {
        dependsOn tasks.named('dockerBuildMyApp')
    }
    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpMyTest')
        finalizedBy tasks.named('composeDownMyTest')
    }
}

// That's it! Convention provides source set, configurations, and task automatically.
```

### How It Works

**Trigger**: The convention applies automatically when the java or groovy plugin is present in your project.

**When it applies**:
- ✅ Your project has java or groovy plugin applied
- ✅ The gradle-docker plugin is applied
- ✅ You haven't manually created the integrationTest source set

**When it doesn't apply**:
- ❌ No java/groovy plugin (not a JVM project)
- ❌ You manually created integrationTest source set before applying the plugin (your config takes precedence)

### Language Support

Write integration tests in:
- **Java only**: Use `java` plugin, tests in `src/integrationTest/java`
- **Groovy/Spock only**: Use `groovy` plugin, tests in `src/integrationTest/groovy`
- **Both**: Use `groovy` plugin, place tests in either directory

The convention works regardless of your main application language.

**Example**: Java application with Spock integration tests:
```groovy
plugins {
    id 'java'          // Main app is Java
    id 'groovy'        // Add groovy for Spock tests
    id 'com.kineticfire.gradle.docker'
}

dependencies {
    // Main app dependencies
    implementation 'org.springframework.boot:spring-boot-starter-web:3.2.0'

    // Integration tests use Spock (Groovy)
    integrationTestImplementation 'org.spockframework:spock-core:2.3'
}

// Convention automatically configures both:
// - src/integrationTest/java/      (available but may be empty)
// - src/integrationTest/groovy/    (put Spock tests here)
```

### Customizing the Convention

Override any aspect using standard Gradle DSL:

**Change source directories**:
```groovy
sourceSets {
    integrationTest {
        groovy.srcDirs = ['custom/test/path']
        resources.srcDirs = ['custom/resources']
    }
}
```

**Customize the test task**:
```groovy
tasks.named('integrationTest') {
    maxParallelForks = 4
    systemProperty 'custom.prop', 'value'
    // Any Test task configuration
}
```

**Add additional dependencies**:
```groovy
dependencies {
    integrationTestImplementation 'io.rest-assured:rest-assured:5.3.0'
    integrationTestRuntimeOnly 'ch.qos.logback:logback-classic:1.4.11'
}
```

### Disable the Convention

If you need complete control, create the source set yourself before applying the plugin:

```groovy
// Option 1: Create source set manually
sourceSets {
    integrationTest {
        // Your custom configuration
    }
}

plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}
// Plugin sees existing source set and won't create its own
```

### Benefits

**Before convention** (per project):
- ~40-50 lines of repetitive boilerplate code
- Manual maintenance across multiple projects
- Risk of inconsistency and copy-paste errors
- Steep learning curve for new users

**After convention**:
- 0 lines of boilerplate required
- Automatic consistency across all projects
- Plugin ensures best practices
- Works out of the box

### Migration Guide for Existing Projects

If you have existing projects with manual integrationTest source set configuration:

**Step 1**: Identify boilerplate to remove
Look for these blocks in your `build.gradle`:
```groovy
sourceSets {
    integrationTest {
        groovy.srcDir 'src/integrationTest/groovy'
        resources.srcDir 'src/integrationTest/resources'
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
    }
}

configurations {
    integrationTestImplementation.extendsFrom testImplementation
    integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
}

tasks.named('processIntegrationTestResources') {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.register('integrationTest', Test) {
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    outputs.cacheIf { false }
}
```

**Step 2**: Remove standard boilerplate
Delete the blocks above from your `build.gradle`.

**Step 3**: Keep only customizations
Retain any project-specific customizations:
```groovy
// Keep customizations like this:
tasks.named('integrationTest') {
    maxParallelForks = 4
    systemProperty 'test.db.url', 'jdbc:h2:mem:test'
}
```

**Step 4**: Verify
```bash
./gradlew clean integrationTest
```

**Result**: Same functionality, less code, easier maintenance.

### Complete Examples

See these integration test examples for complete working demonstrations:
- `plugin-integration-test/dockerOrch/examples/web-app/` - Spock-based tests
- `plugin-integration-test/dockerOrch/examples/web-app-junit/` - JUnit-based tests
- `plugin-integration-test/dockerOrch/examples/isolated-tests/` - Test isolation pattern
- `plugin-integration-test/dockerOrch/verification/basic/` - Minimal setup

Each example includes inline comments explaining the convention.
```

**Rationale**:
- Comprehensive guide for users
- Covers common use cases and edge cases
- Provides migration path for existing projects
- Includes complete working examples

#### Step 2.2: Update CLAUDE.md

**File**: `CLAUDE.md`

**Section**: Lines 201-216 "Follow Integration Test Requirements"

**Current content** (lines 203-206):
```markdown
Write real integration test code that uses the Gradle Docker/Compose plugin exactly like a user of the plugin would (not
a developer of the plugin).  These double as demonstrations of the plugin to its user base.  **Do not** test DSL or
internals here.
- **No mocks/stubs/fakes** for Docker, Compose, filesystem, or network. Use the real stack.
```

**Add after line 206**:

```markdown
- **Integration Test Source Set Convention**: The plugin automatically creates the `integrationTest` source set when
  the java or groovy plugin is present. Do NOT manually create source set boilerplate.
  - Put integration tests in `src/integrationTest/java` or `src/integrationTest/groovy`
  - Put compose files in `src/integrationTest/resources/compose/`
  - Use `integrationTestImplementation` for dependencies
  - The `integrationTest` task is automatically registered
  - Only add customization if needed (overrides convention)
- **Example minimal setup**:
  ```groovy
  dockerOrch {
      composeStacks {
          myTest { files.from('src/integrationTest/resources/compose/app.yml') }
      }
  }

  dependencies {
      integrationTestImplementation 'org.spockframework:spock-core:2.3'
  }
  // Convention provides source set, task, configurations automatically!
  ```
```

**Rationale**:
- Updates AI agent instructions to use convention
- Prevents future creation of unnecessary boilerplate
- Maintains consistency with new approach

#### Step 2.3: Update Implementation Status

**File**: `docs/design-docs/todo/boilerplate-dsl.md`

**Line to update**: Line 4

**Current**: `**Status**: Implemented`

**Change to**: `**Status**: Implemented and Documented`

**Add new section at the end** (after line 574):

```markdown
## Documentation Updates

**Date**: 2025-10-25
**Status**: Complete

### Usage Documentation
Updated `docs/usage/usage-docker-orch.md` with comprehensive convention guide including:
- Automatic setup explanation
- Minimal configuration examples
- Language support (Java/Groovy)
- Customization options
- Migration guide for existing projects
- Complete working examples

### Development Documentation
Updated `CLAUDE.md` integration test requirements to:
- Reference the automatic source set convention
- Provide minimal setup example
- Guide AI agents to use convention instead of boilerplate

### Benefits Delivered
- Users can now understand the convention without reading code
- Migration path provided for existing projects
- Examples demonstrate best practices
- Consistent guidance for both human and AI developers
```

**Rationale**:
- Closes the documentation loop
- Provides audit trail
- Confirms completion of Priority 2

#### Step 2.4: Verification Steps

After updating documentation:

**Command 1**: Verify documentation file exists and is properly formatted
```bash
cd docs/usage
grep -c "Integration Test Source Set Convention" usage-docker-orch.md
# Expected: 1 (section exists)
```

**Command 2**: Verify CLAUDE.md updates
```bash
grep -c "Integration Test Source Set Convention" CLAUDE.md
# Expected: 1 (section added)
```

**Command 3**: Review for line length compliance (max 120 characters)
```bash
# Check usage-docker-orch.md
awk 'length > 120 {print NR": "length" chars - "substr($0,1,50)"..."}' docs/usage/usage-docker-orch.md

# Check CLAUDE.md updates
awk 'length > 120 {print NR": "length" chars - "substr($0,1,50)"..."}' CLAUDE.md | grep -A2 -B2 "Convention"
```

**Success Criteria**:
- ✅ usage-docker-orch.md has new convention section
- ✅ CLAUDE.md references the convention
- ✅ boilerplate-dsl.md status updated
- ✅ All lines ≤ 120 characters
- ✅ Markdown properly formatted
- ✅ Code examples have proper syntax highlighting

---

## Implementation Checklist

### Priority 1: Fix Missing Integration Tests ⚠️ CRITICAL

- [ ] Edit `plugin-integration-test/docker/build.gradle` integrationTest task (add scenario-13, scenario-99)
- [ ] Edit `plugin-integration-test/docker/build.gradle` cleanAll task (add scenario-13, scenario-99)
- [ ] Verify: Count dependsOn statements (should be 14 for integrationTest, 14 for cleanAll)
- [ ] Verify: Test resolution of scenario-13 and scenario-99
- [ ] Build plugin: `cd plugin && ./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal`
- [ ] Run integration tests: `cd ../plugin-integration-test && ./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest`
- [ ] Verify: All 14 docker scenarios execute
- [ ] Verify: BUILD SUCCESSFUL
- [ ] Verify: Zero test failures
- [ ] Verify: Zero lingering containers (`docker ps -a`)

### Priority 2: Improve Integration Test Documentation

- [ ] Add convention section to `docs/usage/usage-docker-orch.md`
- [ ] Update `CLAUDE.md` integration test requirements section
- [ ] Update `docs/design-docs/todo/boilerplate-dsl.md` status
- [ ] Verify: All documentation lines ≤ 120 characters
- [ ] Verify: Markdown properly formatted
- [ ] Verify: Code examples have syntax highlighting
- [ ] Review: Ensure examples are accurate and complete

---

## Success Criteria

**Priority 1 Complete When**:
- ✅ Both scenario-13 and scenario-99 are included in `docker/build.gradle` aggregator tasks
- ✅ Running `./gradlew integrationTest` executes all 14 docker scenarios
- ✅ All integration tests pass (both docker and dockerOrch)
- ✅ Zero lingering containers after test execution
- ✅ No test gaps in coverage

**Priority 2 Complete When**:
- ✅ `usage-docker-orch.md` has comprehensive convention documentation
- ✅ `CLAUDE.md` references the convention and provides examples
- ✅ `boilerplate-dsl.md` status reflects documentation completion
- ✅ All documentation meets project standards (line length, formatting)
- ✅ Users can understand and use the convention without reading code

**Overall Success**:
- ✅ All 25 integration tests running (14 docker + 11 dockerOrch)
- ✅ Full documentation coverage
- ✅ No known gaps or issues
- ✅ Complies with CLAUDE.md acceptance criteria

---

## Timeline Estimate

**Priority 1** (Critical): 1-2 hours
- Edit build.gradle files: 15 minutes
- Build and test plugin: 30 minutes
- Full integration test run: 30-45 minutes
- Verification: 15 minutes

**Priority 2** (Documentation): 2-3 hours
- Write usage-docker-orch.md section: 60-90 minutes
- Update CLAUDE.md: 30 minutes
- Update status in boilerplate-dsl.md: 15 minutes
- Review and verification: 30 minutes

**Total**: 3-5 hours

**Recommended Sequence**:
1. Complete Priority 1 first (fixes critical gap)
2. Verify all tests pass
3. Complete Priority 2 (documents the working implementation)

---

## Risk Assessment

### Priority 1 Risks

**Risk**: Scenario-13 or scenario-99 might have test failures
- **Probability**: Medium
- **Impact**: Medium
- **Mitigation**: Run scenarios individually first to identify any issues before adding to aggregator
- **Fallback**: If either scenario has legitimate failures, investigate and fix the scenario code

**Risk**: Changes might break existing integration tests
- **Probability**: Low
- **Impact**: High
- **Mitigation**: Only adding new dependsOn statements, not modifying existing ones
- **Fallback**: Revert changes if issues occur

### Priority 2 Risks

**Risk**: Documentation might be incomplete or unclear
- **Probability**: Low
- **Impact**: Low
- **Mitigation**: Follow existing documentation patterns, include multiple examples
- **Fallback**: Iterate based on user feedback

---

## Notes

**Why scenario-13 and scenario-99 were missed**:
- Likely added to the project after the docker/build.gradle aggregator was last updated
- Declared in settings.gradle but not added to aggregator tasks
- Tests can run individually but don't execute during full test suite

**Prevention for future**:
- Consider adding a verification task that checks settings.gradle includes match aggregator includes
- Document the process for adding new integration test scenarios
- Add reminder comments in docker/build.gradle near aggregator tasks

**Related Files**:
- Implementation: `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy` (lines 816-968)
- Unit tests: `plugin/src/test/groovy/com/kineticfire/gradle/docker/IntegrationTestConventionTest.groovy`
- Integration tests: All files in `plugin-integration-test/dockerOrch/`
- Gap location: `plugin-integration-test/docker/build.gradle` (lines 59-71, 79-91)
