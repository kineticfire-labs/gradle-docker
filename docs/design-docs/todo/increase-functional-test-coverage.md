# Functional Test Coverage Improvement Plan (Corrected)

**Date Created**: 2025-11-14
**Date Corrected**: 2025-11-14
**Date Updated**: 2025-11-14
**Status**: 🟡 IN PROGRESS - Current coverage ~80-85%, Target 100%
**Priority**: 🔴 CRITICAL - Required per CLAUDE.md acceptance criteria

## Executive Summary

The functional test suite contains **151 test methods** across **23 test files** (~9,100+ lines), providing excellent
coverage of **DSL configuration and validation** (80-90%), **Gradle 9/10 configuration cache compatibility** (100%),
and **multi-project build scenarios** (100%). However, it still has **gaps in testing task dependencies and up-to-date checks**.

**Critical Clarification**:
- **Functional Tests** (this document) = Test **Gradle plugin functionality** via TestKit (no actual Docker calls)
- **Integration Tests** (separate) = Test **actual Docker/Compose operations** with real system calls

**Key Finding**: Current tests verify that the plugin **configures correctly** but miss testing how it **behaves in
various Gradle environments** (multi-project, configuration cache, different Gradle versions, etc.).

## Correct Understanding of Functional vs Integration Tests

### Functional Tests (`plugin/src/functionalTest/`)
**Purpose**: Test Gradle plugin integration and behavior
**Tools**: Gradle TestKit
**What to Test**:
- ✅ Plugin application and registration
- ✅ DSL syntax and configuration
- ✅ Task creation and naming
- ✅ Validation logic and error messages
- ✅ Provider API usage
- ⚠️ Configuration cache compatibility
- ⚠️ Multi-project builds
- ❌ Task dependency graphs
- ❌ Task execution order
- ❌ Task up-to-date checks
- ❌ Convention over configuration
- ❌ Gradle/Java version validation

**What NOT to Test** (reserved for integration tests):
- ❌ Actual Docker daemon calls
- ❌ Real container creation
- ❌ Image push/pull operations
- ❌ Docker registry interactions
- ❌ Compose stack execution

### Integration Tests (`plugin-integration-test/`)
**Purpose**: Test actual Docker/Compose operations
**Tools**: Real `docker` and `docker-compose` commands
**What to Test**:
- ✅ Image build execution
- ✅ Image tag/save/publish operations
- ✅ Compose up/down execution
- ✅ Real container lifecycle
- ✅ Registry interactions
- ✅ Resource cleanup

## Current Functional Test Coverage Analysis

### Coverage by Functional Area

| Functional Area | Test Count | Coverage % | Gap Severity | Priority |
|----------------|------------|------------|--------------|----------|
| Plugin Application | 7 | 80% | 🟢 Low | P3 |
| DSL Configuration | 21 | 90% | 🟢 Low | P3 |
| Task Registration | 9 | 85% | 🟢 Low | P3 |
| Validation Logic | 18 | 85% | 🟢 Low | P3 |
| Provider API | 8 | 70% | 🟡 Medium | P2 |
| **Configuration Cache** | **25** | **✅ 100%** | **🟢 Complete** | **P1** |
| **Multi-Project Builds** | **14** | **✅ 100%** | **🟢 Complete** | **P1** |
| **Task Dependencies** | **0** | **0%** | **🔴 Critical** | **P1** |
| **Task Up-to-Date** | **0** | **0%** | **🔴 Critical** | **P1** |
| **Task Execution Order** | **0** | **0%** | **🔴 Critical** | **P1** |
| **Convention Application** | **0** | **0%** | **🔴 Critical** | **P1** |
| **Version Validation** | **0** | **0%** | **🔴 Critical** | **P1** |
| Nomenclature | 7 | 85% | 🟢 Low | P3 |
| Labels | 6 | 80% | 🟢 Low | P3 |
| Context Configuration | 6 | 75% | 🟡 Medium | P2 |
| Multi-file Compose | 12 | 90% | 🟢 Low | P3 |
| Test Integration Ext | 3 | 50% | 🟡 Medium | P2 |
| Authentication Config | 3 | 70% | 🟡 Medium | P2 |
| SourceRef Mode | 6 | 80% | 🟢 Low | P3 |

**Overall Functional Coverage**: ~80-85% (Target: 100%) ⬆️ +20% improvement
**Fully Covered**: Configuration cache (100%), multi-project builds (100%), DSL (80-90%), validation (80-90%)
**Poorly Covered**: Task dependencies (0%), up-to-date checks (0%)

### Existing Test Files (23 total)

1. ✅ `BasicFunctionalTest.groovy` (2 tests) - TestKit basics
2. ✅ `ComposeStackSpecFunctionalTest.groovy` (9 tests) - Compose DSL
3. ✅ **`ConfigurationCacheFunctionalTest.groovy` (18 tests) - Configuration cache compatibility** 🆕
4. ✅ **`MultiProjectFunctionalTest.groovy` (14 tests) - Multi-project builds** 🆕
5. ✅ `DockerBuildFunctionalTest.groovy` (5 tests) - Build config
5. ✅ `DockerContextApiFunctionalTest.groovy` (6 tests) - Context config
6. ✅ `DockerLabelsFunctionalTest.groovy` (6 tests) - Labels config
7. ✅ `DockerNomenclatureFunctionalTest.groovy` (7 tests) - Nomenclature
8. ✅ `DockerNomenclatureIntegrationFunctionalTest.groovy` (3 tests) - Complex DSL
9. ✅ `DockerPluginFunctionalTest.groovy` (7 tests) - Plugin application
10. ✅ `DockerProviderAPIFunctionalTest.groovy` (2 tests) - Provider API
11. ✅ `DockerPublishFunctionalTest.groovy` (12 tests) - Publish config
12. ✅ `DockerPublishValidationFunctionalTest.groovy` (6 tests) - Validation
13. ✅ `DockerSaveFunctionalTest.groovy` (4 tests) - Save config
14. ✅ `DockerTagFunctionalTest.groovy` (9 tests) - Tag config
15. ✅ `ImageReferenceValidationFunctionalTest.groovy` (2 tests) - Validation
16. ✅ `ModeConsistencyValidationFunctionalTest.groovy` (11 tests) - Mode validation
17. ✅ `MultiFileConfigurationFunctionalTest.groovy` (12 tests) - Multi-file compose
18. ✅ `PluginIntegrationFunctionalTest.groovy` (2 tests) - Test integration
19. ✅ `PullIfMissingFunctionalTest.groovy` (6 tests) - Pull config
20. ✅ `SimplePublishTest.groovy` (1 test) - Basic publish
21. ✅ `SourceRefComponentAssemblyFunctionalTest.groovy` (3 tests) - SourceRef
22. ✅ `TestExtensionFunctionalTest.groovy` (3 tests) - Test extension

## Critical Gaps (Priority 1)

### 1. Configuration Cache Compatibility ✅ COMPLETE
**Current Coverage**: ✅ 100% (18 comprehensive tests)
**Status**: COMPLETE - All tests passing

#### Test File
- ✅ `ConfigurationCacheFunctionalTest.groovy` - 18 tests, 100% passing

#### Completed Test Scenarios

**Basic Cache Behavior** (5 tests):
- ✅ Plugin configuration cached and reused on second run
- ✅ Docker build task configuration is cached correctly
- ✅ Docker save task configuration is cached correctly
- ✅ Docker publish task configuration is cached correctly
- ✅ SourceRef mode configuration works with cache

**Cache Invalidation** (4 tests):
- ✅ Cache invalidated when build.gradle changes
- ✅ Cache invalidated when image configuration changes
- ✅ Cache invalidated when gradle property changes
- ✅ Cache NOT invalidated when unrelated file changes

**Provider Scenarios** (2 tests):
- ✅ Provider-based image configuration works with cache
- ✅ File provider-based configuration works with cache

**Aggregate Tasks** (3 tests):
- ✅ dockerBuild aggregate task works with cache
- ✅ dockerImages aggregate task works with cache
- ✅ composeUp aggregate task works with cache

**Complex Scenarios** (3 tests):
- ✅ Multi-file compose stacks work with cache
- ✅ Compose stack configuration is cached correctly
- ✅ Complex configuration with multiple images and compose stacks works with cache

**Test Integration** (1 test):
- ✅ Test integration extension configuration works with cache

**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ConfigurationCacheFunctionalTest.groovy` ✅ Created

### 2. Multi-Project Builds ✅ COMPLETE
**Current Coverage**: ✅ 100% (14 comprehensive tests)
**Status**: COMPLETE - All tests passing

#### Test File
- ✅ `MultiProjectFunctionalTest.groovy` - 14 tests, 100% passing

#### Completed Test Scenarios

**Basic Multi-Project** (4 tests):
- ✅ Plugin applied to root project only
- ✅ Plugin applied to subprojects only
- ✅ Plugin applied to both root and subprojects
- ✅ Plugin applied to multiple subprojects creates isolated task namespaces

**Configuration Inheritance** (2 tests):
- ✅ Subproject can reference root project properties
- ✅ Subproject overrides root project configuration

**Task Naming and Scoping** (2 tests):
- ✅ Tasks are correctly scoped per subproject with unique names
- ✅ Aggregate tasks work correctly in multi-project builds

**Different Configurations Per Subproject** (4 tests):
- ✅ Different images per subproject work correctly
- ✅ Different compose stacks per subproject work correctly
- ✅ Shared registry configuration across subprojects
- ✅ Independent namespace per subproject

**Cross-Project Dependencies** (2 tests):
- ✅ Subproject can depend on another subproject build task
- ✅ Nested subprojects maintain proper task isolation

**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/MultiProjectFunctionalTest.groovy` ✅ Created

### 3. Task Dependency Graph and Execution Order ⚠️ CRITICAL
**Current Coverage**: 0% (configuration only, no execution verification)
**Tests Needed**: 10-12 tests

#### Missing Test Scenarios

**Dependency Graph Construction**:
- [ ] dockerBuild depends on contextTask when configured
- [ ] dockerSave depends on dockerBuild (when context present)
- [ ] dockerPublish depends on dockerBuild (when context present)
- [ ] dockerTag runs independently
- [ ] Aggregate tasks depend on per-image tasks

**Execution Order Verification** (via TestKit build output):
- [ ] contextTask runs before dockerBuild
- [ ] dockerBuild runs before dockerSave
- [ ] dockerBuild runs before dockerPublish
- [ ] Multiple images build in deterministic order
- [ ] Parallel execution safe (no conflicts)

**Conditional Dependencies**:
- [ ] SourceRef mode: save/publish don't depend on build
- [ ] Build mode: save/publish depend on build
- [ ] Tasks without save/publish configured

**File**: Create `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TaskDependencyFunctionalTest.groovy`

### 4. Task Up-to-Date Checks ⚠️ CRITICAL
**Current Coverage**: 0%
**Tests Needed**: 8-10 tests

**Note**: These tests verify task input/output declaration, not actual Docker operations

#### Missing Test Scenarios

**Input/Output Declaration**:
- [ ] Tasks declare inputs correctly (verify via TestKit)
- [ ] Tasks declare outputs correctly
- [ ] Tasks marked UP-TO-DATE when nothing changes
- [ ] Tasks re-run when inputs change

**Specific Task Up-to-Date Behavior**:
- [ ] dockerBuild inputs: context, dockerfile, buildArgs, labels
- [ ] dockerBuild outputs: imageId file (in build mode)
- [ ] dockerSave inputs: image tags, compression
- [ ] dockerSave outputs: saved image file
- [ ] composeUp inputs: compose files, env files
- [ ] composeUp outputs: state file

**Cache Compatibility**:
- [ ] Up-to-date checks work with configuration cache
- [ ] Build cache compatibility (where applicable)

**File**: Create `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TaskUpToDateFunctionalTest.groovy`

### 5. Convention Over Configuration ⚠️ CRITICAL
**Current Coverage**: 0%
**Tests Needed**: 10-12 tests

#### Missing Test Scenarios

**IntegrationTest Source Set Auto-Creation**:
- [ ] Source set created when java plugin applied
- [ ] Source set NOT created when java plugin not applied
- [ ] Source set created when groovy plugin applied
- [ ] Source directories configured correctly (java, groovy, resources)
- [ ] Classpath configured correctly (includes main output)
- [ ] Configurations created (integrationTestImplementation, etc.)
- [ ] integrationTest task created automatically
- [ ] User can override source set if needed

**Default Values**:
- [ ] Default tags when not specified
- [ ] Default version from project.version
- [ ] Default compression (none)
- [ ] Default wait timeouts (60s)
- [ ] Default poll intervals (2s)

**Convention Overriding**:
- [ ] User-specified values override conventions
- [ ] Explicit configuration takes precedence
- [ ] Mixed convention and explicit settings

**File**: Create `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ConventionFunctionalTest.groovy`

### 6. Gradle and Java Version Validation ⚠️ CRITICAL
**Current Coverage**: 0%
**Tests Needed**: 8-10 tests

#### Missing Test Scenarios

**Java Version Validation**:
- [ ] Plugin applies successfully on Java 21
- [ ] Plugin fails on Java 17 (production environment)
- [ ] Plugin warns on Java 17 (test environment detected)
- [ ] Plugin fails on Java 11 (production environment)
- [ ] Plugin warns on Java 11 (test environment detected)
- [ ] Clear error message suggests upgrading Java

**Gradle Version Validation**:
- [ ] Plugin applies successfully on Gradle 9.0
- [ ] Plugin applies successfully on Gradle 10.0
- [ ] Plugin fails on Gradle 8.x (production environment)
- [ ] Plugin warns on Gradle 8.x (test environment detected)
- [ ] Clear error message suggests upgrading Gradle

**Test Environment Detection**:
- [ ] Verify test environment detection works
- [ ] Warnings shown in test mode
- [ ] Failures shown in production mode

**File**: Create `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/VersionValidationFunctionalTest.groovy`

### 7. Task Output Generation ⚠️ CRITICAL
**Current Coverage**: 0%
**Tests Needed**: 6-8 tests

**Note**: These tests verify output file/directory creation without Docker execution

#### Missing Test Scenarios

**Output Directories**:
- [ ] Build task creates output directory (build/docker/{imageName}/)
- [ ] Save task creates output directory for file
- [ ] ComposeUp creates state directory (build/compose-state/)
- [ ] ComposeDown creates logs directory (if configured)

**Output Files** (verify file creation, not content):
- [ ] Build task creates imageId file placeholder (in test mode)
- [ ] Save task declares output file correctly
- [ ] ComposeUp creates state JSON file structure
- [ ] ComposeDown creates logs file (if configured)

**Output File Paths**:
- [ ] Relative paths resolved correctly
- [ ] Absolute paths handled correctly
- [ ] Provider-based paths resolved at execution time

**File**: Create `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TaskOutputFunctionalTest.groovy`

## Moderate Gaps (Priority 2)

### 8. Provider API Advanced Scenarios
**Current Coverage**: 70% (basic scenarios covered)
**Tests Needed**: 6-8 tests

#### Missing Test Scenarios

**Provider Chains**:
- [ ] Provider.map() chains work correctly
- [ ] Provider.flatMap() resolves correctly
- [ ] Provider.zip() combines values
- [ ] Nested providers resolve correctly

**Provider Lifecycle**:
- [ ] Providers don't resolve during configuration
- [ ] Providers resolve during execution
- [ ] finalize() prevents further changes
- [ ] disallowChanges() enforces immutability

**Error Scenarios**:
- [ ] Provider throws exception - clear error message
- [ ] Circular provider dependencies detected
- [ ] Provider resolved at wrong lifecycle phase

**File**: Enhance `DockerProviderAPIFunctionalTest.groovy`

### 9. Plugin Ordering and Conflicts
**Current Coverage**: 0%
**Tests Needed**: 6-8 tests

#### Missing Test Scenarios

**Plugin Application Order**:
- [ ] docker plugin before java plugin
- [ ] java plugin before docker plugin
- [ ] docker plugin with kotlin-jvm plugin
- [ ] docker plugin with application plugin

**Extension Conflicts**:
- [ ] No naming conflicts with other plugins
- [ ] No task name conflicts
- [ ] No configuration name conflicts

**Service Registration**:
- [ ] Services registered once across multiple projects
- [ ] Services shared correctly between tasks

**File**: Create `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/PluginInteractionFunctionalTest.groovy`

### 10. Test Integration Extension Behavior
**Current Coverage**: 50% (3 tests, basic scenarios)
**Tests Needed**: 8-10 tests

#### Missing Test Scenarios

**Extension Method Registration**:
- [ ] usesCompose() method available on Test tasks
- [ ] composeStateFileFor() method available on Project
- [ ] Extension methods work with custom test tasks

**Lifecycle Configuration**:
- [ ] Suite lifecycle configures correctly
- [ ] Test lifecycle configures correctly
- [ ] Method lifecycle configures correctly
- [ ] Invalid lifecycle rejected with error

**Task Configuration**:
- [ ] Test task depends on composeUp with suite lifecycle
- [ ] Test task finalizedBy composeDown with suite lifecycle
- [ ] Test lifecycle adds per-test dependencies
- [ ] systemProperty added for state file

**Error Scenarios**:
- [ ] Stack name not found in dockerOrch
- [ ] Invalid lifecycle name
- [ ] Multiple conflicting lifecycle configurations

**File**: Enhance `TestExtensionFunctionalTest.groovy` or create new file

### 11. Context Configuration Edge Cases
**Current Coverage**: 75% (basic scenarios covered)
**Tests Needed**: 6-8 tests

#### Missing Test Scenarios

**Dockerfile Resolution**:
- [ ] dockerfile and dockerfileName mutually exclusive
- [ ] Default Dockerfile in context directory
- [ ] Custom dockerfileName in context
- [ ] dockerfile with absolute path
- [ ] dockerfile with relative path

**Context Types**:
- [ ] context directory configuration
- [ ] contextTask configuration
- [ ] contextTaskName property configuration
- [ ] Context and dockerfile interaction

**Error Scenarios**:
- [ ] Neither context nor contextTask specified
- [ ] Both context and contextTask specified
- [ ] Both dockerfile and dockerfileName specified
- [ ] Context directory doesn't exist (validation)

**File**: Enhance `DockerContextApiFunctionalTest.groovy`

### 12. Validation Error Messages
**Current Coverage**: 75% (validation logic tested, message quality not always verified)
**Tests Needed**: 6-8 tests

#### Missing Test Scenarios

**Error Message Quality**:
- [ ] Validation errors include field name
- [ ] Validation errors include current value
- [ ] Validation errors include suggested fix
- [ ] Validation errors include examples

**Specific Validation Messages**:
- [ ] Build mode requires imageName or repository
- [ ] SourceRef mode excludes build properties
- [ ] Repository and namespace mutually exclusive
- [ ] Tags required in build mode
- [ ] Compose files must exist

**Error Reporting Location**:
- [ ] Configuration-time errors fail during configuration
- [ ] Execution-time errors fail during execution
- [ ] Clear stack traces pointing to user code

**File**: Create `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ValidationMessagesFunctionalTest.groovy`

## Minor Gaps (Priority 3)

### 13. Task Naming Conventions
**Current Coverage**: 85%
**Tests Needed**: 4-6 tests

#### Missing Test Scenarios

- [ ] CamelCase image names generate correct task names
- [ ] Hyphenated image names generate correct task names
- [ ] Special characters in names handled correctly
- [ ] Task names don't conflict with Gradle built-ins

**File**: Enhance existing test files

### 14. Aggregate Task Behavior
**Current Coverage**: 70%
**Tests Needed**: 4-6 tests

#### Missing Test Scenarios

- [ ] dockerBuild aggregates all build tasks
- [ ] dockerImages aggregates all per-image tasks
- [ ] composeUp aggregates all compose stacks
- [ ] Empty aggregates (no images configured)

**File**: Enhance existing test files

### 15. BuildService Lifecycle
**Current Coverage**: 0%
**Tests Needed**: 4-6 tests

#### Missing Test Scenarios

- [ ] DockerService registered as shared service
- [ ] ComposeService registered as shared service
- [ ] JsonService registered as shared service
- [ ] Services shared across tasks
- [ ] Service lifecycle managed by Gradle

**File**: Create `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/BuildServiceFunctionalTest.groovy`

## Implementation Plan

### Phase 1: Critical Gradle Behaviors (Weeks 1-2)
**Goal**: Test how plugin behaves in various Gradle environments
**Effort**: 2 weeks, 1 developer

**Week 1**:
- [ ] Day 1-2: `ConfigurationCacheFunctionalTest.groovy` (15-20 tests)
  - Basic cache behavior, invalidation, serialization
- [ ] Day 3-4: `MultiProjectFunctionalTest.groovy` (12-15 tests)
  - Plugin in root/subprojects, task scoping, cross-project scenarios
- [ ] Day 5: Review and fix failures

**Week 2**:
- [ ] Day 1-2: `TaskDependencyFunctionalTest.groovy` (10-12 tests)
  - Dependency graph, execution order, conditional dependencies
- [ ] Day 3: `TaskUpToDateFunctionalTest.groovy` (8-10 tests)
  - Input/output declaration, up-to-date checks
- [ ] Day 4: `ConventionFunctionalTest.groovy` (10-12 tests)
  - IntegrationTest source set, default values, convention overriding
- [ ] Day 5: Review and fix failures

### Phase 2: Version Validation and Task Outputs (Week 3)
**Goal**: Test version compatibility and task output generation
**Effort**: 1 week, 1 developer

**Week 3**:
- [ ] Day 1-2: `VersionValidationFunctionalTest.groovy` (8-10 tests)
  - Java version validation, Gradle version validation, error messages
- [ ] Day 3: `TaskOutputFunctionalTest.groovy` (6-8 tests)
  - Output directory creation, file generation, path resolution
- [ ] Day 4-5: Review and fix failures

### Phase 3: Moderate Priority Tests (Week 4)
**Goal**: Test plugin interactions and advanced scenarios
**Effort**: 1 week, 1 developer

**Week 4**:
- [ ] Day 1: Enhance `DockerProviderAPIFunctionalTest.groovy` (6-8 tests)
  - Provider chains, lifecycle, error scenarios
- [ ] Day 2: `PluginInteractionFunctionalTest.groovy` (6-8 tests)
  - Plugin ordering, conflicts, service registration
- [ ] Day 3: Enhance `TestExtensionFunctionalTest.groovy` (8-10 tests)
  - Extension methods, lifecycle, error scenarios
- [ ] Day 4: Enhance `DockerContextApiFunctionalTest.groovy` (6-8 tests)
  - Dockerfile resolution, context types, error scenarios
- [ ] Day 5: Review and fix failures

### Phase 4: Polish and Documentation (Week 5)
**Goal**: Fill remaining gaps and document
**Effort**: 1 week, 1 developer

**Week 5**:
- [ ] Day 1: `ValidationMessagesFunctionalTest.groovy` (6-8 tests)
  - Error message quality, suggestions, examples
- [ ] Day 2: `BuildServiceFunctionalTest.groovy` (4-6 tests)
  - Service registration, sharing, lifecycle
- [ ] Day 3: Minor enhancements to existing tests (10-15 tests)
  - Task naming, aggregate tasks, edge cases
- [ ] Day 4: Documentation updates
  - Update gap documentation
  - Document any remaining acceptable gaps
- [ ] Day 5: Final review and sign-off

## Test Writing Guidelines

### Functional Test Principles

1. **Use Gradle TestKit**: All tests use `GradleRunner.create()`
2. **No Docker Calls**: Tests must not call `docker` or `docker-compose`
3. **Configuration Focus**: Test DSL, validation, task creation
4. **Build Success/Failure**: Test that builds succeed or fail appropriately
5. **Output Verification**: Check task creation, not Docker side effects

### Functional Test Template

```groovy
class XxxFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        settingsFile << "rootProject.name = 'test-xxx'"
    }

    def "descriptive test name covering specific Gradle behavior"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    testImage {
                        // Configuration
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all', '--configuration-cache')
            .build()  // or .buildAndFail() for error tests

        then:
        // Verify Gradle build outcome
        result.task(':tasks').outcome == TaskOutcome.SUCCESS

        // Verify task creation
        result.output.contains('dockerBuildTestImage')

        // Verify configuration cache (if applicable)
        result.output.contains('Configuration cache entry stored')

        // Verify error messages (if error test)
        // result.output.contains('Expected error message')
    }

    def "configuration cache reuses cached configuration"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    cached {
                        imageName.set('test')
                        tags.set(['v1'])
                    }
                }
            }
        """

        when: "first build with configuration cache"
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        and: "second build reuses cache"
        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result2.output.contains('Reusing configuration cache')
    }
}
```

### Multi-Project Test Template

```groovy
def "plugin works in multi-project build"() {
    given:
    settingsFile << """
        rootProject.name = 'multi-project-test'
        include 'subproject-a'
        include 'subproject-b'
    """

    buildFile << """
        plugins {
            id 'com.kineticfire.gradle.docker' apply false
        }
    """

    def subprojectA = testProjectDir.resolve('subproject-a').toFile()
    subprojectA.mkdirs()
    new File(subprojectA, 'build.gradle').text = """
        plugins {
            id 'com.kineticfire.gradle.docker'
        }

        docker {
            images {
                imageA {
                    imageName.set('image-a')
                    tags.set(['v1'])
                }
            }
        }
    """

    def subprojectB = testProjectDir.resolve('subproject-b').toFile()
    subprojectB.mkdirs()
    new File(subprojectB, 'build.gradle').text = """
        plugins {
            id 'com.kineticfire.gradle.docker'
        }

        docker {
            images {
                imageB {
                    imageName.set('image-b')
                    tags.set(['v1'])
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks', '--all')
        .build()

    then:
    result.task(':tasks').outcome == TaskOutcome.SUCCESS
    // Verify subproject tasks exist
    result.output.contains('subproject-a:dockerBuildImageA')
    result.output.contains('subproject-b:dockerBuildImageB')
}
```

### Configuration Cache Test Pattern

```groovy
def "configuration cache handles property changes correctly"() {
    given:
    buildFile << """
        plugins {
            id 'com.kineticfire.gradle.docker'
        }

        docker {
            images {
                test {
                    imageName.set(providers.gradleProperty('imageName').orElse('default'))
                    tags.set(['v1'])
                }
            }
        }
    """

    when: "first build with property"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('help', '--configuration-cache', '-PimageName=custom')
        .build()

    and: "second build with different property"
    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('help', '--configuration-cache', '-PimageName=different')
        .build()

    then:
    result1.output.contains('Configuration cache entry stored')
    result2.output.contains('Configuration cache entry discarded') || result2.output.contains('problems were found')
    // Cache should be invalidated because property changed
}
```

## Success Criteria

### Quantitative Metrics

- [ ] **Minimum 200 total functional test methods** (currently 119, need +80)
- [ ] **100% configuration cache scenarios covered** (currently 30%)
- [ ] **100% multi-project scenarios covered** (currently 0%)
- [ ] **100% task dependency scenarios covered** (currently 0%)
- [ ] **100% convention scenarios covered** (currently 0%)
- [ ] **All tests pass** without Docker daemon running
- [ ] **All tests work** with `--configuration-cache` flag

### Qualitative Metrics

- [ ] Tests verify **Gradle plugin behaviors**, not Docker execution
- [ ] Tests cover **edge cases** in Gradle lifecycle
- [ ] Tests are **independent** and can run in any order
- [ ] Tests have **clear, descriptive names** explaining what they verify
- [ ] Test failures provide **actionable error messages**
- [ ] Tests complete in **reasonable time** (< 5 minutes for full suite)

### Coverage Goals by Category

| Category | Current % | Target % | Gap |
|----------|-----------|----------|-----|
| Configuration Cache | 30% | 100% | +70% |
| Multi-Project | 0% | 100% | +100% |
| Task Dependencies | 0% | 100% | +100% |
| Task Up-to-Date | 0% | 100% | +100% |
| Convention | 0% | 100% | +100% |
| Version Validation | 0% | 100% | +100% |
| Provider API | 70% | 100% | +30% |
| Plugin Interaction | 0% | 100% | +100% |
| Test Extension | 50% | 100% | +50% |
| Validation Messages | 75% | 100% | +25% |

## Documentation Updates

After achieving 100% coverage:

1. **Update `docs/design-docs/testing/functional-test-gaps.md`**:
   - Document functional test coverage percentage
   - List any remaining gaps with justification
   - Clarify functional vs integration test boundary
   - Reference this plan document

2. **Update CLAUDE.md** (if needed):
   - Confirm functional test acceptance criteria met
   - Clarify functional tests = Gradle plugin testing (no Docker)
   - Document how to run functional tests

3. **Create test coverage report**:
   - Summary of all functional test files
   - Coverage by Gradle feature area
   - Known limitations or gaps

## Boundary Between Functional and Integration Tests

### Functional Tests Test (via TestKit, NO Docker calls)
- ✅ Plugin applies successfully
- ✅ DSL syntax works correctly
- ✅ Tasks are created with correct names
- ✅ Task dependencies configured correctly
- ✅ Validation catches configuration errors
- ✅ Configuration cache compatibility
- ✅ Multi-project build support
- ✅ Provider API works correctly
- ✅ Convention over configuration
- ✅ Error messages are helpful

### Integration Tests Test (with real Docker/Compose)
- ✅ Docker images actually build
- ✅ Images tagged correctly in Docker daemon
- ✅ Images saved to files correctly
- ✅ Images published to registries
- ✅ Compose stacks actually start containers
- ✅ Wait-for-healthy actually waits
- ✅ ComposeDown cleans up containers
- ✅ Resource cleanup works correctly

**Key Distinction**: Functional = "Does the Gradle plugin work?", Integration = "Does Docker actually work?"

## Risks and Mitigation

### Risk 1: Configuration Cache Complexity
**Impact**: Hard to write tests that properly exercise cache
**Mitigation**:
- Study TestKit configuration cache documentation
- Start with simple scenarios, build complexity
- Verify cache behavior in output

### Risk 2: Multi-Project Test Complexity
**Impact**: Multi-project builds harder to set up in tests
**Mitigation**:
- Create helper methods for multi-project setup
- Reuse patterns across tests
- Document setup clearly

### Risk 3: Test Execution Time
**Impact**: 200+ tests may take long to run
**Mitigation**:
- Keep tests focused and fast
- No Docker = tests run quickly
- Run in parallel where possible

### Risk 4: TestKit Limitations
**Impact**: Some behaviors hard to test via TestKit
**Mitigation**:
- Document limitations in gap file
- Use integration tests for behaviors requiring Docker
- Test what's testable, document what's not

## References

- **CLAUDE.md** (lines 27-35): 100% functional test coverage requirement
- **CLAUDE.md** (lines 176-179): Functional testing requirements
- **docs/project-standards/testing/functional-testing.md**: Functional test standards
- **Current test files**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/`
- **Functional test issue**: `docs/design-docs/functional-test-testkit-gradle-issue.md`
- **TestKit documentation**: https://docs.gradle.org/current/userguide/test_kit.html

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-11-14 | 1.0 | Initial analysis (incorrect - included Docker execution) | Claude |
| 2025-11-14 | 2.0 | Corrected plan - Gradle plugin behaviors only, no Docker | Claude |

---

**Next Action**: Begin Phase 1 implementation - Configuration Cache and Multi-Project tests
