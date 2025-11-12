# Refactor Plugin ID: `com.kineticfire.gradle.gradle-docker` → `com.kineticfire.gradle.docker`

## Objective

Change the plugin ID from `com.kineticfire.gradle.gradle-docker` to `com.kineticfire.gradle.docker` throughout the
entire codebase, including plugin implementation, tests, and documentation.

**Reason**: Simplify plugin ID by removing the redundant "gradle" prefix. The project and repository will remain named
"gradle-docker" - only the plugin ID for usage changes.

**Current usage**:
```groovy
plugins {
    id 'com.kineticfire.gradle.gradle-docker'
}
```

**Target usage**:
```groovy
plugins {
    id 'com.kineticfire.gradle.docker'
}
```

---

## Scope Analysis

### Files Impacted (by category):
1. **Plugin Implementation**: 1 file (`plugin/build.gradle`)
2. **Plugin Unit Tests**: 2 files in `plugin/src/test/`
3. **Plugin Functional Tests**: 20 files in `plugin/src/functionalTest/` (most are `.disabled`)
4. **Integration Tests**: 59 `build.gradle` files + 1 `settings.gradle` in `plugin-integration-test/`
5. **Documentation**: ~20+ README and usage documentation files in `docs/` and `plugin-integration-test/`

### Total Occurrences:
- **Direct string references**: ~40+ occurrences of `com.kineticfire.gradle.gradle-docker`
- **Implicit references**: Plugin descriptor generated from `plugin/build.gradle`
- **Total files to modify**: ~107 files

---

## Detailed Refactoring Plan

### PHASE 1: Plugin Implementation Changes

#### 1.1 Update Plugin Descriptor
**File**: `plugin/build.gradle:127`

**Change**:
```groovy
// BEFORE:
gradlePlugin {
    website = 'https://github.com/kineticfire-labs/gradle-docker'
    vcsUrl = 'https://github.com/kineticfire-labs/gradle-docker'

    plugins {
        gradleDockerPlugin {
            id = 'com.kineticfire.gradle.gradle-docker'
            implementationClass = 'com.kineticfire.gradle.docker.GradleDockerPlugin'
            ...
        }
    }
}

// AFTER:
gradlePlugin {
    website = 'https://github.com/kineticfire-labs/gradle-docker'
    vcsUrl = 'https://github.com/kineticfire-labs/gradle-docker'

    plugins {
        gradleDockerPlugin {
            id = 'com.kineticfire.gradle.docker'
            implementationClass = 'com.kineticfire.gradle.docker.GradleDockerPlugin'
            ...
        }
    }
}
```

**Impact**: This is the **single source of truth** for the plugin ID. When Gradle builds the plugin:
- It generates `META-INF/gradle-plugins/com.kineticfire.gradle.docker.properties`
- The old `com.kineticfire.gradle.gradle-docker.properties` will no longer be generated
- The `implementationClass` remains unchanged: `com.kineticfire.gradle.docker.GradleDockerPlugin`

**Verification Step**:
```bash
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build
# Verify plugin descriptor
ls -la build/resources/main/META-INF/gradle-plugins/
# Should contain: com.kineticfire.gradle.docker.properties (not gradle-docker)
# Check content:
cat build/resources/main/META-INF/gradle-plugins/com.kineticfire.gradle.docker.properties
# Should contain: implementation-class=com.kineticfire.gradle.docker.GradleDockerPlugin
```

---

### PHASE 2: Unit Test Changes

#### 2.1 Update Unit Test Files
**Files to update** (2 files):
1. `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtensionTest.groovy`
2. `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtensionTest.groovy`

**Search Strategy**:
```bash
rg "com\.kineticfire\.gradle\.gradle-docker" plugin/src/test/
```

**Expected patterns to find and replace**:
```groovy
// Pattern 1: Plugin application in tests
// BEFORE:
project.apply plugin: 'com.kineticfire.gradle.gradle-docker'

// AFTER:
project.apply plugin: 'com.kineticfire.gradle.docker'

// Pattern 2: Plugin ID in plugins block
// BEFORE:
plugins {
    id 'com.kineticfire.gradle.gradle-docker'
}

// AFTER:
plugins {
    id 'com.kineticfire.gradle.docker'
}

// Pattern 3: String literals in assertions
// BEFORE:
assert pluginId == 'com.kineticfire.gradle.gradle-docker'

// AFTER:
assert pluginId == 'com.kineticfire.gradle.docker'
```

**Verification Step**:
```bash
cd plugin
./gradlew clean test
# Expected result: 2233 passed, 0 failures, 24 skipped
# Check for coverage report
open build/reports/jacoco/test/html/index.html
```

---

### PHASE 3: Functional Test Changes

#### 3.1 Update Functional Test Files
**Files to update** (20 files in `plugin/src/functionalTest/groovy/`):

**Note**: Most functional tests are `.disabled` due to TestKit/Gradle 9 configuration cache issues (see
`docs/design-docs/functional-test-testkit-gradle-issue.md`), but they should still be updated for future use.

**Search Strategy**:
```bash
rg "com\.kineticfire\.gradle\.gradle-docker" plugin/src/functionalTest/
```

**Common pattern in functional tests**:
```groovy
// Test setup typically includes:
// BEFORE:
buildFile << """
    plugins {
        id 'com.kineticfire.gradle.gradle-docker'
    }

    docker {
        images {
            testImage {
                ...
            }
        }
    }
"""

// AFTER:
buildFile << """
    plugins {
        id 'com.kineticfire.gradle.docker'
    }

    docker {
        images {
            testImage {
                ...
            }
        }
    }
"""
```

**Files to update** (from grep results):
1. `DockerPublishFunctionalTest.groovy`
2. `DockerTagFunctionalTest.groovy.disabled`
3. `DockerNomenclatureFunctionalTest.groovy.disabled`
4. `ModeConsistencyValidationFunctionalTest.groovy.disabled`
5. `SourceRefComponentAssemblyFunctionalTest.groovy.disabled`
6. `PullIfMissingFunctionalTest.groovy.disabled`
7. `DockerSaveFunctionalTest.groovy.disabled`
8. `DockerNomenclatureIntegrationFunctionalTest.groovy.disabled`
9. `DockerProviderAPIFunctionalTest.groovy.disabled`
10. `DockerLabelsFunctionalTest.groovy.disabled`
11. `DockerPluginFunctionalTest.groovy.disabled`
12. `SimplePublishTest.groovy.disabled`
13. `DockerBuildFunctionalTest.groovy.disabled`
14. `DockerPublishValidationFunctionalTest.groovy.disabled`
15. `ImageReferenceValidationFunctionalTest.groovy.disabled`
16. `MultiFileConfigurationFunctionalTest.groovy.disabled`
17. `ComposeStackSpecFunctionalTest.groovy.disabled`
18. `DockerContextApiFunctionalTest.groovy.disabled`
19. `PluginIntegrationFunctionalTest.groovy.disabled`
20. `TestExtensionFunctionalTest.groovy.disabled`

**Verification Step**:
```bash
cd plugin
./gradlew clean functionalTest
# Expected: Tests complete (most will be skipped due to .disabled suffix)
```

---

### PHASE 4: Integration Test Changes

#### 4.1 Update Settings File (CRITICAL - Do This First)
**File**: `plugin-integration-test/settings.gradle:19`

**Change**:
```groovy
// BEFORE:
pluginManagement {
    plugins {
        id 'com.kineticfire.gradle.gradle-docker' version "${providers.gradleProperty('plugin_version').get()}"
    }
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

// AFTER:
pluginManagement {
    plugins {
        id 'com.kineticfire.gradle.docker' version "${providers.gradleProperty('plugin_version').get()}"
    }
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

**Impact**: This change affects ALL 59 build.gradle files in integration tests that use
`id 'com.kineticfire.gradle.gradle-docker'` without specifying version (version comes from pluginManagement).

#### 4.2 Update All Integration Test build.gradle Files
**Files to update** (~59 files):

**Locations**:
- `plugin-integration-test/docker/scenario-*/build.gradle` (~13 files)
- `plugin-integration-test/dockerOrch/verification/*/app-image/build.gradle` (~20 files)
- `plugin-integration-test/dockerOrch/examples/*/app-image/build.gradle` (~10 files)
- Other scattered build.gradle files across integration test subprojects

**Search Strategy**:
```bash
# Find all files containing the old plugin ID
rg --files-with-matches "com\.kineticfire\.gradle\.gradle-docker" plugin-integration-test/

# Count them
rg --files-with-matches "com\.kineticfire\.gradle\.gradle-docker" plugin-integration-test/ | wc -l
```

**Standard pattern**:
```groovy
// BEFORE:
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.gradle-docker'
}

docker {
    images {
        timeServer {
            ...
        }
    }
}

// AFTER:
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

docker {
    images {
        timeServer {
            ...
        }
    }
}
```

**Systematic approach**:
1. Generate list of files: `rg --files-with-matches "com\.kineticfire\.gradle\.gradle-docker" plugin-integration-test/
   > /tmp/files-to-update.txt`
2. For each file in the list, replace the plugin ID
3. Verify syntax remains correct (proper quotes, no typos)

**Verification Step** (after all integration test files updated):
```bash
# Build and publish plugin with new ID
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

# Run ALL integration tests
cd ../plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest

# Verify no lingering containers
docker ps -a  # Should show NO containers
```

---

### PHASE 5: Documentation Updates

#### 5.1 Update Primary Usage Documentation
**Files**:
1. `docs/usage/usage-docker.md` - Primary usage documentation for 'docker' DSL
2. `docs/usage/usage-docker-orch.md` - Compose orchestration usage for 'dockerOrch' DSL
3. `docs/usage/spock-junit-test-extensions.md` - Test extension documentation
4. `docs/usage/gradle-9-and-10-compatibility-practices.md` - Compatibility guide

**Search Strategy**:
```bash
rg "com\.kineticfire\.gradle\.gradle-docker" docs/usage/
```

**Pattern**:
```markdown
<!-- BEFORE: -->
## Prerequisites

First, add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
}
```

<!-- AFTER: -->
## Prerequisites

First, add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'com.kineticfire.gradle.docker' version '1.0.0'
}
```
```

#### 5.2 Update Integration Test README Files
**Files** (~20 README files):

**Locations**:
- `plugin-integration-test/README.md` - Top-level integration test guide
- `plugin-integration-test/docker/README.md` - Docker scenarios documentation
- `plugin-integration-test/docker/scenario-99/README.md` - Specific scenario documentation
- `plugin-integration-test/dockerOrch/README.md` - Compose orchestration documentation
- `plugin-integration-test/dockerOrch/verification/README.md` - Verification tests overview
- `plugin-integration-test/dockerOrch/verification/*/README.md` - Individual verification scenario docs
- `plugin-integration-test/dockerOrch/examples/README.md` - Examples overview
- `plugin-integration-test/dockerOrch/examples/*/README.md` - Individual example scenario docs
- `plugin-integration-test/buildSrc/README.md` - Reusable testing library documentation

**Search Strategy**:
```bash
rg "com\.kineticfire\.gradle\.gradle-docker" plugin-integration-test/ --type md
```

**Update**:
- Code examples showing plugin usage
- Prose text referencing the plugin ID
- Ensure consistency across all examples

#### 5.3 Update Design Documentation
**Files**:
- `docs/design-docs/done/boilerplate-dsl.md`
- `docs/design-docs/done/boilerplate-dsl-plan.md`
- `docs/design-docs/done/boilerplate-dsl-plan-followup.md`
- `docs/design-docs/gradle-9-and-10-compatibility.md`

**Search Strategy**:
```bash
rg "com\.kineticfire\.gradle\.gradle-docker" docs/design-docs/
```

**Note**: These are historical/reference documents but should still be updated for consistency.

**Verification Step**:
```bash
# Verify no remaining occurrences of old plugin ID
rg "com\.kineticfire\.gradle\.gradle-docker" .
# Should return NO results (or only results in this plan file)
```

---

## Execution Strategy

### Recommended Order of Operations:
1. **Phase 1**: Update `plugin/build.gradle` → Build plugin → Verify descriptor file
2. **Phase 2**: Update unit tests → Run `./gradlew clean test` → Verify all pass
3. **Phase 3**: Update functional tests → Run `./gradlew clean functionalTest` → Verify completion
4. **Phase 4**: Update integration tests
   - First: Update `plugin-integration-test/settings.gradle`
   - Then: Update all `build.gradle` files in integration tests
   - Finally: Run full integration test suite
5. **Phase 5**: Update all documentation
6. **Final Verification**: Run complete test suite from scratch

### Detailed Execution Steps:

#### Step 1: Plugin Implementation
```bash
cd plugin
# Edit plugin/build.gradle line 127
./gradlew -Pplugin_version=1.0.0 clean build
# Verify META-INF/gradle-plugins/ contains new file name
ls -la build/resources/main/META-INF/gradle-plugins/
```

#### Step 2: Unit Tests
```bash
cd plugin
# Update 2 test files in plugin/src/test/
./gradlew clean test
# Expected: 2233 passed, 0 failures, 24 skipped
```

#### Step 3: Functional Tests
```bash
cd plugin
# Update 20 test files in plugin/src/functionalTest/
./gradlew clean functionalTest
# Expected: Tests complete (most skipped due to .disabled)
```

#### Step 4: Integration Tests
```bash
# Update plugin-integration-test/settings.gradle
# Update all build.gradle files in plugin-integration-test/

# Build and publish plugin
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

# Run integration tests
cd ../plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest

# Verify cleanup
docker ps -a  # Should be empty
```

#### Step 5: Documentation
```bash
# Update all documentation files
# Verify no remaining occurrences
rg "com\.kineticfire\.gradle\.gradle-docker" .
```

### Risk Mitigation:
- **Test incrementally** after each phase
- **Use `rg` (ripgrep)** for consistent search across all files
- **Verify syntax** after each file edit to avoid breaking Groovy/Gradle syntax
- **Run verification commands** after each phase to catch issues early

---

## Verification & Testing Plan

### Incremental Verification (After Each Phase)

#### After Phase 1 (Plugin Implementation):
```bash
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build

# Verify plugin descriptor file
ls -la build/resources/main/META-INF/gradle-plugins/
# Should contain: com.kineticfire.gradle.docker.properties
# Should NOT contain: com.kineticfire.gradle.gradle-docker.properties

cat build/resources/main/META-INF/gradle-plugins/com.kineticfire.gradle.docker.properties
# Expected content:
# implementation-class=com.kineticfire.gradle.docker.GradleDockerPlugin
```

#### After Phase 2 (Unit Tests):
```bash
cd plugin
./gradlew clean test

# Expected results:
# - 2233 tests passed
# - 0 failures
# - 24 skipped (DockerServiceImplComprehensiveTest - requires Docker daemon)

# Verify coverage
open build/reports/jacoco/test/html/index.html
```

#### After Phase 3 (Functional Tests):
```bash
cd plugin
./gradlew clean functionalTest

# Expected: Tests complete (most skipped due to .disabled suffix)
# No failures should occur
```

#### After Phase 4 (Integration Tests):
```bash
# Rebuild and publish plugin with new ID
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

# Run integration tests
cd ../plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest

# Expected: All integration tests pass (100% pass rate)
# Verify Docker cleanup
docker ps -a  # Should show NO containers
docker images | grep scenario  # Should show built images
```

#### After Phase 5 (Documentation):
```bash
# Verify no remaining occurrences of old plugin ID
rg "com\.kineticfire\.gradle\.gradle-docker" .

# Should return ONLY this plan file (or no results if this file uses different search term)
# Any other results indicate missed updates
```

### Final Complete Verification

Run the complete workflow from scratch:

```bash
# 1. Clean everything
cd plugin
./gradlew clean
cd ../plugin-integration-test
./gradlew cleanAll
docker system prune -af

# 2. Build plugin
cd ../plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

# Expected:
# - Build successful
# - All unit tests pass (2233 passed, 0 failures, 24 skipped)
# - No warnings

# 3. Run integration tests
cd ../plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest

# Expected:
# - All integration tests pass (100% pass rate required)
# - No failures
# - No warnings

# 4. Verify cleanup
docker ps -a  # Must be empty (no lingering containers)

# 5. Check for any remaining old plugin ID references
cd ..
rg "com\.kineticfire\.gradle\.gradle-docker" --type-not md | grep -v "refactor-plugin-id.md"
# Should return NO results
```

### Complete Verification Checklist

- [ ] Phase 1: Plugin implementation updated
  - [ ] `plugin/build.gradle` line 127 changed to `id = 'com.kineticfire.gradle.docker'`
  - [ ] Plugin builds successfully
  - [ ] Plugin descriptor file `com.kineticfire.gradle.docker.properties` exists
  - [ ] Old plugin descriptor file `com.kineticfire.gradle.gradle-docker.properties` does NOT exist

- [ ] Phase 2: Unit tests updated
  - [ ] 2 unit test files updated
  - [ ] All unit tests pass (2233 passed, 0 failures, 24 skipped)
  - [ ] No warnings during test execution

- [ ] Phase 3: Functional tests updated
  - [ ] 20 functional test files updated
  - [ ] Functional tests complete without errors

- [ ] Phase 4: Integration tests updated
  - [ ] `plugin-integration-test/settings.gradle` updated
  - [ ] All 59 integration test `build.gradle` files updated
  - [ ] Plugin published to Maven local successfully
  - [ ] All integration tests pass (100% pass rate)
  - [ ] No Docker containers remain after tests (`docker ps -a` is empty)

- [ ] Phase 5: Documentation updated
  - [ ] 4 usage documentation files updated (`docs/usage/*.md`)
  - [ ] ~20 README files updated (`plugin-integration-test/**/README.md`)
  - [ ] Design documentation files updated (`docs/design-docs/**/*.md`)
  - [ ] No remaining occurrences of `com.kineticfire.gradle.gradle-docker` (except in this plan file)

- [ ] Final verification
  - [ ] Complete workflow runs successfully from clean state
  - [ ] No warnings during build
  - [ ] No warnings during test execution
  - [ ] All acceptance criteria satisfied (per CLAUDE.md)

---

## Acceptance Criteria (per CLAUDE.md)

This refactoring must satisfy the project's Definition of Done:

- [ ] **All unit tests pass**
  - Command: `./gradlew clean test` (from `plugin/` directory)
  - Expected: 2233 passed, 0 failures, 24 skipped

- [ ] **All functional tests complete**
  - Command: `./gradlew clean functionalTest` (from `plugin/` directory)
  - Expected: Tests complete without errors (most skipped due to .disabled)

- [ ] **Plugin builds successfully**
  - Command: `./gradlew -Pplugin_version=1.0.0 build` (from `plugin/` directory)
  - Expected: Build successful, no errors

- [ ] **All integration tests pass**
  - Commands:
    1. `./gradlew -Pplugin_version=1.0.0 build publishToMavenLocal` (from `plugin/` directory)
    2. `./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest` (from `plugin-integration-test/` directory)
  - Expected: 100% pass rate, no failures

- [ ] **No lingering containers**
  - Command: `docker ps -a`
  - Expected: No containers listed

- [ ] **No warnings**
  - All compilation, test, and build operations produce no warnings

- [ ] **Code quality standards maintained**
  - Lines ≤ 120 characters
  - All files properly formatted
  - No violations of project standards

---

## Summary Statistics

### Changes Required:
- **Plugin implementation**: 1 line in 1 file
- **Unit tests**: ~2-4 lines across 2 files
- **Functional tests**: ~20-40 lines across 20 files
- **Integration test settings**: 1 line in 1 file
- **Integration test builds**: ~59-70 lines across 59 files
- **Documentation**: ~30-50 lines across ~25 files

### Total Impact:
- **Files modified**: ~107 files
- **Lines changed**: ~110-165 lines
- **Occurrences replaced**: ~110-165 occurrences

### Test Coverage:
- **Unit tests**: 2233 tests (100% must pass)
- **Functional tests**: ~20 test files (completion required)
- **Integration tests**: ~59 test projects (100% must pass)

### Search Commands:
```bash
# Find all occurrences
rg "com\.kineticfire\.gradle\.gradle-docker" .

# Find in specific areas
rg "com\.kineticfire\.gradle\.gradle-docker" plugin/
rg "com\.kineticfire\.gradle\.gradle-docker" plugin-integration-test/
rg "com\.kineticfire\.gradle\.gradle-docker" docs/

# Count occurrences
rg "com\.kineticfire\.gradle\.gradle-docker" . | wc -l

# Find files containing pattern
rg --files-with-matches "com\.kineticfire\.gradle\.gradle-docker" .
```

---

## Notes

- This refactoring is a **simple find-and-replace** operation across the codebase
- The plugin's implementation class (`com.kineticfire.gradle.docker.GradleDockerPlugin`) remains unchanged
- The project name (`gradle-docker`) remains unchanged
- The repository name remains unchanged
- Only the plugin ID for usage changes
- All DSL, task names, and functionality remain exactly the same
- Users will only see the difference in the `plugins {}` block
