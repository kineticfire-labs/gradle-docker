# Plan: Fix Configuration Duplication Between dockerTest DSL and Test Annotations

> **Note:** This document references "suite lifecycle" which has since been consolidated into "class lifecycle".
> There is no separate "suite" lifecycle - it is simply "class" lifecycle managed via Gradle tasks.

**Status**: Not Started
**Priority**: High
**Estimated Effort**: 11-15 hours
**Created**: 2025-11-16
**Updated**: 2025-11-16 (comprehensive plan with all user feedback incorporated)

**Latest Updates**:
- Added JUnit 5 examples verification task (Task 3.5)
- Added main examples README update task (Task 3.6) with framework comparison
- Added complete implementation strategy and rollback plan section
- Added incremental verification approach with specific checkpoints
- Added branch strategy (feature branch, not main)
- Expanded acceptance criteria with detailed, specific requirements
- Added conflict detection requirements (FAIL when both DSL and annotation specify same parameter)
- Completed waitForRunning implementation details
- Added edge case handling for system properties (spaces, special characters)
- Enhanced unit/functional/integration test requirements for both Spock and JUnit 5
- Clarified that JUnit 5 needs verification only (already correct by design)
- Updated all task checklists to include both frameworks explicitly

## Problem Statement

### Current State: Configuration Duplication

Users must duplicate Docker Compose configuration in **two places**:

1. **build.gradle** (dockerTest DSL):
```groovy
dockerTest {
    composeStacks {
        webAppTest {
            files.from('src/integrationTest/resources/compose/web-app.yml')
            waitForHealthy {
                waitForServices.set(['web-app'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}
```

2. **Test class** (@ComposeUp annotation):
```groovy
@ComposeUp(
    stackName = "webAppTest",              // DUPLICATE
    composeFile = "compose/web-app.yml",   // DUPLICATE
    waitForHealthy = ["web-app"],          // DUPLICATE
    timeoutSeconds = 60,                   // DUPLICATE
    pollSeconds = 2                        // DUPLICATE
)
class WebAppIT extends Specification { }
```

### Issues with Current State

1. **Violates DRY principle**: Same configuration maintained in two places
2. **Error-prone**: Changes must be synchronized manually
3. **Violates stated goal**: "all settings in build.gradle file" (per user requirement)
4. **Inconsistent examples**: Some use both DSL + annotation, others use annotation only
5. **Confusing to users**: Unclear which approach to use

### Root Cause

Test framework extensions (Spock @ComposeUp, JUnit @ExtendWith) were implemented to read configuration
from **annotations only**, never from the dockerTest DSL. This diverged from the original design vision.

**Original Design** (from uc-7-proj-dev-compose-orchestration.md):
- All configuration in build.gradle via dockerTest DSL
- `usesCompose` method wires test tasks to compose stacks
- Extensions read configuration from system properties set by `usesCompose`

**Actual Implementation**:
- `usesCompose` method exists but is **incomplete** for CLASS and METHOD lifecycles
- SUITE lifecycle works correctly (uses Gradle tasks, reads from DSL)
- CLASS and METHOD lifecycles set system properties but extensions **ignore them**
- Extensions read from annotations instead, forcing duplication

## Goal

**Complete the original vision**: Make test framework extensions read configuration from dockerTest DSL
via system properties, eliminating the need for duplicate configuration in annotations.

**Single Source of Truth**: All Docker Compose configuration defined once in build.gradle.

## Solution: Option A - Complete the Original Vision

### High-Level Approach

1. **Make annotation parameters optional** with empty/default values
2. **Modify extensions to read from system properties first**, annotation second
3. **Complete the `usesCompose` implementation** to pass all necessary configuration via system properties
4. **Update examples** to use build.gradle configuration via `usesCompose`
5. **Maintain backward compatibility** - annotations still work if users prefer them

### Architecture Changes

```
┌─────────────────────────────────────────────────────────────────────┐
│ build.gradle (SINGLE SOURCE OF TRUTH)                               │
│                                                                      │
│ dockerTest {                                                         │
│     composeStacks {                                                  │
│         webAppTest {                                                 │
│             files.from('compose.yml')                                │
│             waitForHealthy { waitForServices.set(['web-app']) }      │
│         }                                                            │
│     }                                                                │
│ }                                                                    │
│                                                                      │
│ tasks.named('integrationTest') {                                     │
│     usesCompose stack: "webAppTest", lifecycle: "class"              │
│ }                                                                    │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ Sets system properties
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│ TestIntegrationExtension.usesCompose()                              │
│ - Reads configuration from dockerTest DSL                           │
│ - Sets comprehensive system properties:                             │
│   * COMPOSE_STATE_FILE                                              │
│   * docker.compose.stack                                            │
│   * docker.compose.files                                            │
│   * docker.compose.waitForHealthy                                   │
│   * docker.compose.timeoutSeconds                                   │
│   * docker.compose.pollSeconds                                      │
│   * docker.compose.lifecycle                                        │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ Extensions read system properties
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Test Framework Extensions                                           │
│                                                                      │
│ @ComposeUp  // No parameters needed!                                │
│ class WebAppIT extends Specification {                              │
│     // Extension reads config from system properties                │
│ }                                                                    │
│                                                                      │
│ Priority:                                                            │
│ 1. Read from system properties (set by usesCompose)                 │
│ 2. Fallback to annotation parameters (backward compatibility)       │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **Backward Compatibility**: Annotation parameters still work if specified
2. **Configuration Priority**: System properties (from DSL) override annotation parameters
3. **Minimal Annotation**: `@ComposeUp` with no parameters is valid when using `usesCompose`
4. **Clear Error Messages**: If neither system properties nor annotation parameters are found, fail with helpful message

## Framework-Specific Implementation Details

### Spock (Groovy) - Requires Major Changes

**Current State:**
- Configuration duplicated in `@ComposeUp` annotation and build.gradle
- Example: `@ComposeUp(stackName = "webAppTest", composeFile = "...", lifecycle = LifecycleMode.CLASS, ...)`

**Target State:**
- Zero-parameter `@ComposeUp`, all config from build.gradle
- Example: `@ComposeUp` with no parameters at all

**Changes Required:**
- Make all annotation parameters optional (including lifecycle)
- Read configuration from system properties (set by `usesCompose`)
- Fallback to annotation parameters for backward compatibility

**Lifecycle Determination:**
- Current: In annotation parameter `lifecycle = LifecycleMode.CLASS`
- After: Read from system property `docker.compose.lifecycle` (set by `usesCompose`)

### JUnit 5 (Java) - Already Correct, Needs Verification Only

**Current State:**
- Extensions are parameter-less markers (already best practice!)
- Example: `@ExtendWith(DockerComposeClassExtension.class)` - no parameters

**Target State:**
- Same as current (verify and test only)

**Changes Required:**
- **Minor**: Verify extensions read system properties correctly
- Add tests to confirm system property reading
- Ensure clear error messages when config missing

**Lifecycle Determination:**
- Specified by which extension is used:
  - `@ExtendWith(DockerComposeClassExtension.class)` → CLASS lifecycle
  - `@ExtendWith(DockerComposeMethodExtension.class)` → METHOD lifecycle
- No parameter needed, no system property needed for lifecycle mode

### Key Insight

**JUnit 5 is already correct** - it's the model Spock should follow:
- No configuration duplication in JUnit 5 (extensions are parameter-less)
- JUnit 5 already reads everything from system properties (it has no choice)
- Spock needs major changes, JUnit 5 needs minor verification

### Both Frameworks Use Identical build.gradle

```groovy
dockerTest {
    composeStacks {
        myTest {
            files.from('compose.yml')
            waitForHealthy { waitForServices.set(['app']) }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose stack: "myTest", lifecycle: "class"
}
```

**Only test code differs (framework syntax):**
- Spock: `@ComposeUp` (zero parameters after plan implementation)
- JUnit 5: `@ExtendWith(DockerComposeClassExtension.class)` (already zero parameters)

## Implementation Plan

### Phase 1: Core Infrastructure (4-5 hours)

#### Task 1.1: Enhance TestIntegrationExtension.usesCompose()

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtension.groovy`

**Current Implementation** (lines 119-143):
- Sets minimal system properties: `docker.compose.stack`, `docker.compose.lifecycle`, `docker.compose.project`
- Tells user to use @ExtendWith annotation
- Does NOT pass compose file paths, wait settings, etc.

**Required Changes**:
1. Read ALL configuration from ComposeStackSpec (from dockerTest DSL)
2. Set comprehensive system properties for extensions to consume:
   - `docker.compose.files` - comma-separated list of compose file paths
   - `docker.compose.projectName` - Docker Compose project name
   - `docker.compose.waitForHealthy.services` - comma-separated service names
   - `docker.compose.waitForHealthy.timeoutSeconds` - timeout value
   - `docker.compose.waitForHealthy.pollSeconds` - poll interval
   - `docker.compose.waitForRunning.services` - comma-separated service names
   - `docker.compose.waitForRunning.timeoutSeconds` - timeout value
   - `docker.compose.waitForRunning.pollSeconds` - poll interval
   - `COMPOSE_STATE_FILE` - path to state JSON file
3. Apply to both `configureClassLifecycle()` and `configureMethodLifecycle()`

**Example Implementation**:
```groovy
private void configureClassLifecycle(Test test, String stackName, stackSpec) {
    // Set system properties from dockerTest DSL
    test.systemProperty("docker.compose.stack", stackName)
    test.systemProperty("docker.compose.lifecycle", "class")
    test.systemProperty("docker.compose.projectName", stackSpec.projectName.getOrElse(""))

    // Compose files (serialize as comma-separated paths)
    def filePaths = stackSpec.files.collect { it.absolutePath }.join(',')
    test.systemProperty("docker.compose.files", filePaths)

    // Wait for healthy settings
    if (stackSpec.waitForHealthy) {
        def services = stackSpec.waitForHealthy.waitForServices.getOrElse([]).join(',')
        test.systemProperty("docker.compose.waitForHealthy.services", services)
        test.systemProperty("docker.compose.waitForHealthy.timeoutSeconds",
            stackSpec.waitForHealthy.timeoutSeconds.getOrElse(60).toString())
        test.systemProperty("docker.compose.waitForHealthy.pollSeconds",
            stackSpec.waitForHealthy.pollSeconds.getOrElse(2).toString())
    }

    // Wait for running settings
    if (stackSpec.waitForRunning) {
        def services = stackSpec.waitForRunning.waitForServices.getOrElse([]).join(',')
        test.systemProperty("docker.compose.waitForRunning.services", services)
        test.systemProperty("docker.compose.waitForRunning.timeoutSeconds",
            stackSpec.waitForRunning.timeoutSeconds.getOrElse(60).toString())
        test.systemProperty("docker.compose.waitForRunning.pollSeconds",
            stackSpec.waitForRunning.pollSeconds.getOrElse(2).toString())
    }

    // State file path
    test.systemProperty("COMPOSE_STATE_FILE", composeStateFileFor(stackName).get())

    logger.info("Test '{}' configured for CLASS lifecycle from dockerTest DSL", test.name)
}
```

**Edge Case Handling**:
- File paths with spaces: URL-encode or use alternate delimiter (e.g., semicolon)
- Service names with special characters: Handle `my-app`, `app_v2`, etc.
- Comma-separated lists: Escape commas in individual values if needed
- Test with paths containing: spaces, commas, special characters

**Error Handling**:
- If stackName not found in dockerTest.composeStacks:
  ```groovy
  throw new IllegalArgumentException(
      "Compose stack '${stackName}' not found in dockerTest configuration. " +
      "Available stacks: ${dockerTestExt.composeStacks*.name}. " +
      "Check dockerTest { composeStacks { ... } } in build.gradle."
  )
  ```

**Acceptance Criteria**:
- [ ] All ComposeStackSpec configuration serialized to system properties
- [ ] Both CLASS and METHOD lifecycle methods updated
- [ ] Wait for healthy AND wait for running settings both serialized
- [ ] Stack name validation with clear error message
- [ ] Edge cases handled: file paths with spaces, special characters
- [ ] Unit tests verify all system properties are set correctly
- [ ] Unit tests verify error thrown for nonexistent stack name
- [ ] Integration tests verify properties are accessible from test code

#### Task 1.2: Make @ComposeUp Annotation Parameters Optional

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeUp.groovy`

**Current Implementation**:
- All parameters are required (no defaults)
- `stackName` is especially problematic (required but should come from DSL)

**Required Changes**:
1. Make all parameters optional with defaults:
   - `stackName = ""` (empty = read from system property)
   - `composeFile = ""` (empty = read from system property)
   - `composeFiles = []` (empty = read from system property)
   - `waitForHealthy = []` (empty = read from system property)
   - `waitForRunning = []` (empty = read from system property)
   - `timeoutSeconds = 0` (0 = read from system property)
   - `pollSeconds = 0` (0 = read from system property)
   - `lifecycle = LifecycleMode.CLASS` (default = read from system property)

**Example**:
```groovy
@Target([ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerComposeSpockExtension.class)
@interface ComposeUp {
    String stackName() default ""                      // Empty = read from system property
    String composeFile() default ""                    // Empty = read from system property
    String[] composeFiles() default []                 // Empty = read from system property
    String[] waitForHealthy() default []               // Empty = read from system property
    String[] waitForRunning() default []               // Empty = read from system property
    int timeoutSeconds() default 0                     // 0 = read from system property
    int pollSeconds() default 0                        // 0 = read from system property
    LifecycleMode lifecycle() default LifecycleMode.CLASS  // Default = read from system property
}
```

**Acceptance Criteria**:
- [ ] All parameters have sensible defaults
- [ ] Annotation compiles with no parameters: `@ComposeUp`
- [ ] Existing code with parameters still works (backward compatibility)
- [ ] Unit tests verify optional parameters

#### Task 1.3: Update DockerComposeSpockExtension to Read System Properties

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtension.groovy`

**Current Implementation** (line 113):
```groovy
def config = createConfiguration(annotation, spec)  // Reads from annotation ONLY
```

**Required Changes**:
1. Read system properties first
2. Use annotation parameters as fallback (backward compatibility)
3. **Fail if BOTH system property AND annotation specify same parameter** (prevents duplication)
4. Fail with clear message if neither source provides required configuration

**Example Implementation**:
```groovy
private Configuration createConfiguration(ComposeUp annotation, SpecInfo spec) {
    def config = new Configuration()

    // 1. Stack name (required)
    config.stackName = readConfigValue(
        systemProperty: "docker.compose.stack",
        annotationValue: annotation.stackName(),
        required: true,
        errorMessage: "stackName must be set via usesCompose or @ComposeUp annotation"
    )

    // 2. Compose files
    config.composeFiles = readComposeFiles(annotation)

    // 3. Wait for healthy services
    config.waitForHealthy = readWaitConfig(annotation, "healthy")

    // 4. Wait for running services
    config.waitForRunning = readWaitConfig(annotation, "running")

    // 5. Lifecycle mode - read from system property first, annotation second
    def lifecycleProp = System.getProperty("docker.compose.lifecycle")
    config.lifecycle = lifecycleProp ?
        LifecycleMode.valueOf(lifecycleProp.toUpperCase()) :
        annotation.lifecycle()

    // 6. State file path
    config.stateFilePath = System.getProperty("COMPOSE_STATE_FILE")

    return config
}

private String readConfigValue(Map params) {
    def sysPropValue = System.getProperty(params.systemProperty)
    def hasSysProp = sysPropValue && !sysPropValue.isEmpty()
    def hasAnnotation = params.annotationValue && !params.annotationValue.isEmpty()

    // FAIL if both system property AND annotation specify same parameter
    if (hasSysProp && hasAnnotation) {
        throw new IllegalStateException(
            "Configuration conflict for '${params.paramName}': " +
            "Specified in BOTH build.gradle (via usesCompose: '${sysPropValue}') " +
            "AND @ComposeUp annotation ('${params.annotationValue}'). " +
            "Remove annotation parameter to use build.gradle configuration. " +
            "To fix: use EITHER build.gradle OR annotation, not both."
        )
    }

    // Use system property (from build.gradle)
    if (hasSysProp) {
        return sysPropValue
    }

    // Fallback to annotation value (backward compatibility)
    if (hasAnnotation) {
        return params.annotationValue
    }

    // Required but not found in either source
    if (params.required) {
        throw new IllegalStateException(params.errorMessage)
    }

    return null
}

private List<String> readComposeFiles(ComposeUp annotation) {
    // Try system property first (comma-separated list)
    def sysProp = System.getProperty("docker.compose.files")
    if (sysProp && !sysProp.isEmpty()) {
        return sysProp.split(',').collect { it.trim() }
    }

    // Fallback to annotation
    if (annotation.composeFile()) {
        return [annotation.composeFile()]
    }
    if (annotation.composeFiles().length > 0) {
        return annotation.composeFiles().toList()
    }

    throw new IllegalStateException(
        "Compose files must be set via usesCompose or @ComposeUp annotation"
    )
}

private WaitConfig readWaitConfig(ComposeUp annotation, String waitType) {
    def config = new WaitConfig()

    // Try system properties first
    def servicesProp = System.getProperty("docker.compose.waitFor${waitType.capitalize()}.services")
    if (servicesProp && !servicesProp.isEmpty()) {
        config.services = servicesProp.split(',').collect { it.trim() }
        config.timeoutSeconds = Integer.parseInt(
            System.getProperty("docker.compose.waitFor${waitType.capitalize()}.timeoutSeconds", "60")
        )
        config.pollSeconds = Integer.parseInt(
            System.getProperty("docker.compose.waitFor${waitType.capitalize()}.pollSeconds", "2")
        )
        return config
    }

    // Fallback to annotation
    def annotationServices = (waitType == "healthy") ?
        annotation.waitForHealthy() : annotation.waitForRunning()
    if (annotationServices.length > 0) {
        config.services = annotationServices.toList()
        config.timeoutSeconds = annotation.timeoutSeconds() ?: 60
        config.pollSeconds = annotation.pollSeconds() ?: 2
        return config
    }

    return config  // Empty config is valid (no wait)
}
```

**Acceptance Criteria**:
- [ ] Extension reads system properties with priority over annotation
- [ ] Lifecycle mode read from system property first, annotation second
- [ ] **Conflict detection**: Fail if BOTH system property AND annotation specify same parameter
- [ ] Annotation parameters still work when system properties not set (backward compatibility)
- [ ] Clear error messages when required config is missing
- [ ] Clear error message on conflict: "Configuration conflict for 'stackName': Specified in BOTH build.gradle... AND @ComposeUp annotation..."
- [ ] Unit tests verify system property reading for all parameters including lifecycle
- [ ] Unit tests verify fallback to annotation (when system property not set)
- [ ] Unit tests verify conflict detection (fail when both sources specify same parameter)
- [ ] Unit tests verify error cases (missing required config)

### Phase 2: JUnit 5 Extensions - Verification Only (1-2 hours)

#### Task 2.1: Verify JUnit 5 Extensions (Already Correct by Design)

**Files**:
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeClassExtension.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtension.groovy`

**Current State Analysis**:

JUnit 5 extensions **don't have configuration parameters** - they're parameter-less markers:
- `@ExtendWith(DockerComposeClassExtension.class)` - no parameters
- `@ExtendWith(DockerComposeMethodExtension.class)` - no parameters

This means:
- ✅ JUnit 5 **already** enforces "all config in build.gradle"
- ✅ No duplication problem exists for JUnit 5
- ✅ JUnit 5 is the model Spock should follow

**Required Changes**:

**NOT a redesign** - just verification and testing:

1. **Verify** DockerComposeClassExtension reads all config from system properties:
   - Stack name (`docker.compose.stack`)
   - Compose files (`docker.compose.files`)
   - Wait settings (`docker.compose.waitForHealthy.*`, `docker.compose.waitForRunning.*`)
   - Project name (`docker.compose.projectName`)
   - State file path (`COMPOSE_STATE_FILE`)

2. **Verify** DockerComposeMethodExtension reads same system properties

3. **Ensure** clear error messages when required configuration missing:
   - "Stack name not found: set docker.compose.stack via usesCompose in build.gradle"
   - "Compose files not found: set docker.compose.files via usesCompose in build.gradle"

4. **Add comprehensive tests** to verify system property reading

**Note on Lifecycle:**
- Lifecycle is **not** read from system property for JUnit 5
- Lifecycle is determined by **which extension** is used:
  - `DockerComposeClassExtension` → CLASS lifecycle
  - `DockerComposeMethodExtension` → METHOD lifecycle
- This is correct design - no changes needed

**Acceptance Criteria**:
- [ ] Verified: DockerComposeClassExtension reads all config from system properties
- [ ] Verified: DockerComposeMethodExtension reads all config from system properties
- [ ] Clear error messages when configuration missing (not generic NPE)
- [ ] Unit tests verify system property reading for all configuration
- [ ] Integration tests with JUnit 5 pass (all examples)
- [ ] Documentation updated to explain JUnit 5 usage pattern

### Phase 3: Update Examples (3-4 hours)

#### Task 3.1: Update web-app Example to Use usesCompose

**Files**:
- `plugin-integration-test/dockerTest/examples/web-app/app-image/build.gradle`
- `plugin-integration-test/dockerTest/examples/web-app/app-image/src/integrationTest/groovy/com/kineticfire/test/WebAppExampleIT.groovy`

**Current State**: Has BOTH dockerTest DSL and @ComposeUp annotation (duplication)

**Required Changes**:

**build.gradle** - Add usesCompose:
```groovy
dockerTest {
    composeStacks {
        webAppTest {
            files.from('src/integrationTest/resources/compose/web-app.yml')
            projectName = "example-web-app-test"
            waitForHealthy {
                waitForServices.set(['web-app'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}

tasks.named('integrationTest') {
    useJUnitPlatform()
    usesCompose stack: "webAppTest", lifecycle: "class"  // ADD THIS
}
```

**WebAppExampleIT.groovy** - Simplify annotation:
```groovy
@ComposeUp  // No parameters at all! All config from build.gradle
class WebAppExampleIT extends Specification {
    // Extension reads config from system properties set by usesCompose
}
```

**Acceptance Criteria**:
- [ ] build.gradle uses usesCompose
- [ ] @ComposeUp annotation has NO parameters (all config from build.gradle)
- [ ] Integration test passes
- [ ] No configuration duplication

#### Task 3.2: Update stateful-web-app Example

**Files**:
- `plugin-integration-test/dockerTest/examples/stateful-web-app/app-image/build.gradle`
- `plugin-integration-test/dockerTest/examples/stateful-web-app/app-image/src/integrationTest/groovy/com/kineticfire/test/StatefulWebAppExampleIT.groovy`

**Apply same pattern as web-app example.**

**Acceptance Criteria**:
- [ ] Configuration moved to build.gradle
- [ ] @ComposeUp annotation has NO parameters
- [ ] Integration test passes

#### Task 3.3: Update isolated-tests Example

**Files**:
- `plugin-integration-test/dockerTest/examples/isolated-tests/app-image/build.gradle`
- `plugin-integration-test/dockerTest/examples/isolated-tests/app-image/src/integrationTest/groovy/com/kineticfire/test/IsolatedTestsExampleIT.groovy`

**Current State**: Has @ComposeUp annotation ONLY, no dockerTest DSL

**Required Changes**: Add dockerTest DSL, use usesCompose, simplify annotation

**Acceptance Criteria**:
- [ ] dockerTest DSL added
- [ ] usesCompose configured
- [ ] @ComposeUp annotation has NO parameters
- [ ] Integration test passes

#### Task 3.4: Update database-app Example

**Files**:
- `plugin-integration-test/dockerTest/examples/database-app/app-image/build.gradle`
- `plugin-integration-test/dockerTest/examples/database-app/app-image/src/integrationTest/groovy/com/kineticfire/test/DatabaseAppExampleIT.groovy`

**Apply same pattern.**

**Acceptance Criteria**:
- [ ] Configuration moved to build.gradle
- [ ] @ComposeUp annotation has NO parameters
- [ ] Integration test passes

#### Task 3.5: Verify JUnit 5 Examples

**Files**:
- `plugin-integration-test/dockerTest/examples/isolated-tests-junit/app-image/build.gradle`
- `plugin-integration-test/dockerTest/examples/isolated-tests-junit/app-image/src/integrationTest/java/com/kineticfire/test/IsolatedTestsJUnit5ClassIT.java`
- `plugin-integration-test/dockerTest/examples/isolated-tests-junit/app-image/src/integrationTest/java/com/kineticfire/test/IsolatedTestsJUnit5MethodIT.java`
- Any other JUnit 5 example files

**Current State**: JUnit 5 examples already use parameter-less extensions (`@ExtendWith`)

**Required Changes**:
1. Verify usesCompose configuration exists and is correct in build.gradle
2. Verify all JUnit 5 tests use parameter-less @ExtendWith annotations
3. Ensure all JUnit 5 integration tests pass
4. Document any JUnit 5 specific patterns or best practices

**Acceptance Criteria**:
- [ ] All JUnit 5 examples identified and cataloged
- [ ] Verify each example uses usesCompose in build.gradle
- [ ] Verify each example uses parameter-less @ExtendWith (DockerComposeClassExtension or DockerComposeMethodExtension)
- [ ] All JUnit 5 integration tests pass
- [ ] No configuration duplication in JUnit 5 examples

#### Task 3.6: Update Main Examples README

**File**: `plugin-integration-test/dockerTest/examples/README.md`

**Current State**: Main examples README may not clearly explain the usesCompose pattern or framework comparison

**Required Changes**:
1. Add section explaining usesCompose as the recommended pattern
2. Add framework comparison (Java/JUnit 5 vs Groovy/Spock)
3. Update overview to show both frameworks are supported
4. Link to individual example READMEs
5. Explain decision criteria for choosing a framework

**Acceptance Criteria**:
- [ ] Main README clearly explains usesCompose pattern
- [ ] Framework comparison section added (not called "migration" - this is about choosing)
- [ ] Both frameworks presented equally (no preference stated)
- [ ] Links to all example READMEs updated
- [ ] Clear, concise overview of the examples

#### Task 3.7: Update Example READMEs

**Files**: All example README.md files

**Required Changes**:
1. Show usesCompose configuration in build.gradle
2. Update test code snippets to show @ComposeUp with NO parameters
3. Explain that ALL configuration comes from build.gradle (single source of truth)
4. Note that annotation parameters are optional (for backward compatibility)

**Acceptance Criteria**:
- [ ] All example READMEs show usesCompose pattern
- [ ] Code snippets show @ComposeUp with no parameters
- [ ] Explanation is clear

### Phase 4: Update Documentation (2-3 hours)

#### Task 4.1: Update usage-docker-orch.md

**File**: `docs/usage/usage-docker-orch.md`

**Required Changes**:
1. **Add section: "Choosing a Test Framework"** - Explain Java/JUnit 5 vs Groovy/Spock
2. **Section: "Test Framework Extensions"** - Update to show usesCompose pattern
3. Add note about configuration priority (system properties > annotation)
4. Show parameterless annotation usage for both frameworks:
   - Spock: `@ComposeUp` with no parameters
   - JUnit 5: `@ExtendWith(DockerComposeClassExtension.class)` (already parameterless)
5. Update all code examples to use usesCompose
6. Add section explaining backward compatibility (annotation parameters still work for Spock)
7. Explain framework-specific differences (lifecycle determination, test syntax)

**Example content to add**:
```markdown
### Choosing a Test Framework: Java/JUnit 5 vs Groovy/Spock

The dockerTest plugin supports both Java with JUnit 5 and Groovy with Spock for integration testing.
Both frameworks use **identical build.gradle configuration** but differ in test code syntax.

#### Framework Comparison

| Aspect | Groovy/Spock | Java/JUnit 5 |
|--------|--------------|--------------|
| **Test Language** | Groovy (dynamic) | Java (static) |
| **Test Style** | BDD (given/when/then) | Traditional (arrange/act/assert) |
| **Extension Annotation** | `@ComposeUp` | `@ExtendWith(DockerComposeClassExtension.class)` |
| **Configuration** | Zero-parameter annotation | Zero-parameter annotation (already) |
| **Lifecycle Methods** | `setupSpec()` / `setup()` | `@BeforeAll` / `@BeforeEach` |
| **Shared Variables** | `@Shared` | `static` |
| **Learning Curve** | Groovy syntax required | Standard Java |

#### When to Choose Groovy/Spock

✅ **Use Spock if:**
- Your team prefers BDD-style tests (given/when/then)
- You want expressive, readable test specifications
- You're comfortable with Groovy syntax
- You value concise test code

**Example:**
```groovy
@ComposeUp  // No parameters - all config in build.gradle
class WebAppIT extends Specification {
    @Shared String baseUrl

    def setupSpec() {
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)
        baseUrl = "http://localhost:${stateData.services['web-app'].publishedPorts[0].host}"
    }

    def "should respond to health check endpoint"() {
        when: "we call the /health endpoint"
        def response = RestAssured.get("${baseUrl}/health")

        then: "we get 200 OK"
        response.statusCode() == 200
    }
}
```

#### When to Choose Java/JUnit 5

✅ **Use JUnit 5 if:**
- Your team prefers standard Java (no Groovy required)
- You want static typing and IDE support
- You're familiar with JUnit patterns
- You value consistency with production code language

**Example:**
```java
@ExtendWith(DockerComposeClassExtension.class)  // No parameters - all config in build.gradle
class WebAppIT {
    private static String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setupAll() throws IOException {
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        JsonNode stateData = objectMapper.readTree(new File(stateFilePath));
        int port = stateData.get("services").get("web-app")
            .get("publishedPorts").get(0).get("host").asInt();
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @DisplayName("Should respond to health check endpoint")
    void shouldRespondToHealthCheckEndpoint() {
        Response response = RestAssured.get(baseUrl + "/health");
        assertEquals(200, response.statusCode(), "Expected 200 OK");
    }
}
```

#### Both Use Identical build.gradle

**Regardless of framework choice**, all configuration goes in build.gradle:

```groovy
dockerTest {
    composeStacks {
        webAppTest {
            files.from('src/integrationTest/resources/compose/web-app.yml')
            waitForHealthy {
                waitForServices.set(['web-app'])
                timeoutSeconds.set(60)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose stack: "webAppTest", lifecycle: "class"
}
```

**Only difference:** Test code syntax (Groovy vs Java)

### Recommended Pattern: Configuration in build.gradle

The recommended approach is to define all Docker Compose configuration in build.gradle
and use `usesCompose` to wire test tasks:

**build.gradle:**
```groovy
dockerTest {
    composeStacks {
        webAppTest {
            files.from('src/integrationTest/resources/compose/web-app.yml')
            waitForHealthy {
                waitForServices.set(['web-app'])
                timeoutSeconds.set(60)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose stack: "webAppTest", lifecycle: "class"
}
```

**Test class:**
```groovy
@ComposeUp  // No parameters! All config comes from build.gradle
class WebAppIT extends Specification {
    @Shared String baseUrl

    def setupSpec() {
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        // ...
    }
}
```

**Benefits:**
- Single source of truth (build.gradle)
- No configuration duplication
- Easier to maintain
- Clear separation: build config vs test logic

### Backward Compatibility: Annotation Parameters

For backward compatibility, annotation parameters still work if you prefer to configure
directly in test code:

```groovy
@ComposeUp(
    stackName = "webAppTest",
    composeFile = "compose/web-app.yml",
    waitForHealthy = ["web-app"],
    timeoutSeconds = 60,
    lifecycle = LifecycleMode.CLASS
)
class WebAppIT extends Specification { }
```

**Configuration Priority:**
1. System properties (set by `usesCompose` from dockerTest DSL) - HIGHEST
2. Annotation parameters - FALLBACK
3. Error if neither is specified - FAIL FAST

**Recommendation:** Use build.gradle configuration unless you have a specific reason to
configure in test code.
```

**Acceptance Criteria**:
- [ ] New section: "Choosing a Test Framework" added with comparison table
- [ ] Both Groovy/Spock and Java/JUnit 5 examples provided
- [ ] Framework-specific differences explained (lifecycle determination, syntax)
- [ ] Documentation shows usesCompose as primary pattern for both frameworks
- [ ] Spock annotation parameters documented as optional/backward compatibility
- [ ] JUnit 5 documented as already using parameter-less extensions
- [ ] Configuration priority clearly explained
- [ ] All code examples updated for both frameworks

#### Task 4.2: Update uc-7-proj-dev-compose-orchestration.md

**File**: `docs/design-docs/requirements/use-cases/uc-7-proj-dev-compose-orchestration.md`

**Required Changes**:
1. Update to reflect completed implementation
2. Note that original vision is now fully implemented
3. Update examples to show system property reading

**Acceptance Criteria**:
- [ ] Use case document reflects completed implementation
- [ ] Examples are accurate

### Phase 5: Testing (3-4 hours)

#### Task 5.1: Unit Tests

**Files**:
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtensionTest.groovy`
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtensionTest.groovy`
- New: `plugin/src/test/groovy/com/kineticfire/gradle/docker/junit/DockerComposeClassExtensionTest.groovy`
- New: `plugin/src/test/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtensionTest.groovy`

**Required Coverage**:
1. **TestIntegrationExtension**:
   - [ ] usesCompose sets all system properties from ComposeStackSpec
   - [ ] CLASS lifecycle configuration
   - [ ] METHOD lifecycle configuration
   - [ ] All wait settings serialized correctly (both waitForHealthy and waitForRunning)
   - [ ] Compose file paths serialized correctly
   - [ ] Edge case: File paths with spaces
   - [ ] Edge case: Service names with special characters
   - [ ] Error handling: Stack name not found in dockerTest.composeStacks
   - [ ] Error handling: Clear error message lists available stacks

2. **DockerComposeSpockExtension**:
   - [ ] Reads stack name from system property
   - [ ] Reads compose files from system property (comma-separated list)
   - [ ] Reads wait for healthy settings from system properties
   - [ ] Reads wait for running settings from system properties
   - [ ] Reads lifecycle mode from system property
   - [ ] **Conflict detection: Fails when BOTH system property AND annotation specify same parameter**
   - [ ] Conflict detection: Error message clearly identifies conflicting parameter
   - [ ] Conflict detection: Error message explains how to fix
   - [ ] Falls back to annotation parameters when system properties not set (all parameters)
   - [ ] Throws error when neither system properties nor annotation provides config
   - [ ] Backward compatibility: annotation-only configuration still works
   - [ ] Zero-parameter annotation works: `@ComposeUp` with all config from DSL

3. **JUnit Extensions**:
   - [ ] DockerComposeClassExtension reads all system properties correctly
   - [ ] DockerComposeMethodExtension reads all system properties correctly
   - [ ] Both extensions read stack name from system property
   - [ ] Both extensions read compose files from system property
   - [ ] Both extensions read wait for healthy settings from system properties
   - [ ] Both extensions read wait for running settings from system properties
   - [ ] Error handling: Clear error messages when configuration missing
   - [ ] Lifecycle determined by extension type (not system property)

**Acceptance Criteria**:
- [ ] 100% line and branch coverage for new/modified code
- [ ] All tests pass
- [ ] Property-based tests where applicable

#### Task 5.2: Functional Tests

**Files**:
- `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TestExtensionFunctionalTest.groovy`

**Required Tests**:
1. **Spock with usesCompose**:
   - [ ] CLASS lifecycle reads all config from DSL (zero-parameter annotation)
   - [ ] METHOD lifecycle reads all config from DSL (zero-parameter annotation)
   - [ ] Lifecycle mode read from build.gradle via system property, not annotation
   - [ ] Stack name read from system property
   - [ ] Compose files read from system property
   - [ ] Wait for healthy settings read from system properties
   - [ ] Wait for running settings read from system properties
   - [ ] State file path available in test via COMPOSE_STATE_FILE
   - [ ] Wait settings applied correctly (containers reach expected state)

2. **JUnit 5 with usesCompose**:
   - [ ] CLASS lifecycle reads all config from DSL (parameter-less @ExtendWith)
   - [ ] METHOD lifecycle reads all config from DSL (parameter-less @ExtendWith)
   - [ ] Stack name read from system property
   - [ ] Compose files read from system property
   - [ ] Wait for healthy settings read from system properties
   - [ ] Wait for running settings read from system properties
   - [ ] State file path available in test via COMPOSE_STATE_FILE
   - [ ] Lifecycle determined by extension type (DockerComposeClassExtension vs DockerComposeMethodExtension)

3. **Backward Compatibility** (Spock only):
   - [ ] Annotation-only configuration still works (no usesCompose, all parameters in annotation)
   - [ ] Lifecycle in annotation still works
   - [ ] All annotation parameters still work independently

4. **Conflict Detection** (Spock only):
   - [ ] Test fails when BOTH build.gradle AND annotation specify stack name
   - [ ] Test fails when BOTH build.gradle AND annotation specify compose files
   - [ ] Test fails when BOTH build.gradle AND annotation specify wait settings
   - [ ] Error message identifies the conflicting parameter
   - [ ] Error message explains how to fix (remove annotation parameter)

5. **Error Cases**:
   - [ ] Clear error when no configuration provided (neither DSL nor annotation)
   - [ ] Clear error when stack name not found in dockerTest.composeStacks
   - [ ] Error message lists available stack names
   - [ ] Clear error when required compose files not specified

**Acceptance Criteria**:
- [ ] All functional tests pass
- [ ] Configuration cache compatible
- [ ] Clear, descriptive test names

#### Task 5.3: Integration Tests

**Files**: All example integration tests (both Spock and JUnit 5)

**Required Verification - Spock Examples**:
- [ ] web-app integration test passes (Spock, CLASS lifecycle)
- [ ] stateful-web-app integration test passes (Spock, CLASS lifecycle)
- [ ] isolated-tests integration test passes (Spock, METHOD lifecycle)
- [ ] database-app integration test passes (Spock)
- [ ] All Spock tests use usesCompose pattern in build.gradle
- [ ] All Spock tests use @ComposeUp with zero parameters
- [ ] No configuration duplication in any Spock example
- [ ] State files generated correctly and accessible from tests
- [ ] Tests can read service ports from state files
- [ ] Tests can connect to containers successfully

**Required Verification - JUnit 5 Examples**:
- [ ] isolated-tests-junit CLASS lifecycle test passes (JUnit 5)
- [ ] isolated-tests-junit METHOD lifecycle test passes (JUnit 5)
- [ ] All JUnit 5 tests use usesCompose pattern in build.gradle
- [ ] All JUnit 5 tests use parameter-less @ExtendWith annotations
- [ ] No configuration duplication in any JUnit 5 example
- [ ] State files generated correctly and accessible from tests
- [ ] Tests can read service ports from state files
- [ ] Tests can connect to containers successfully

**Container Cleanup Verification**:
- [ ] No containers left running after all Spock tests complete
- [ ] No containers left running after all JUnit 5 tests complete
- [ ] `docker ps -a` shows zero containers after full test suite

**Acceptance Criteria**:
- [ ] **100% of integration tests pass** (zero failures)
- [ ] All Spock examples demonstrate recommended pattern (usesCompose + zero-parameter annotation)
- [ ] All JUnit 5 examples demonstrate recommended pattern (usesCompose + parameter-less extension)
- [ ] Both frameworks work identically from user perspective (same build.gradle config)
- [ ] `docker ps -a` shows no lingering containers after tests

## Files to Modify

### Plugin Source Code
1. `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtension.groovy`
   - Enhance usesCompose() to set comprehensive system properties

2. `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeUp.groovy`
   - Make all parameters optional with defaults

3. `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtension.groovy`
   - Read system properties first, fail if annotation also specifies same parameter

4. `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeClassExtension.groovy`
   - Verify reads system properties (no changes expected)

5. `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtension.groovy`
   - Verify reads system properties (no changes expected)

### Plugin Tests
6. `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtensionTest.groovy`
7. `plugin/src/test/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtensionTest.groovy`
8. `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TestExtensionFunctionalTest.groovy`

### Integration Test Examples - Spock
9. `plugin-integration-test/dockerTest/examples/web-app/app-image/build.gradle`
10. `plugin-integration-test/dockerTest/examples/web-app/app-image/src/integrationTest/groovy/com/kineticfire/test/WebAppExampleIT.groovy`
11. `plugin-integration-test/dockerTest/examples/stateful-web-app/app-image/build.gradle`
12. `plugin-integration-test/dockerTest/examples/stateful-web-app/app-image/src/integrationTest/groovy/com/kineticfire/test/StatefulWebAppExampleIT.groovy`
13. `plugin-integration-test/dockerTest/examples/isolated-tests/app-image/build.gradle`
14. `plugin-integration-test/dockerTest/examples/isolated-tests/app-image/src/integrationTest/groovy/com/kineticfire/test/IsolatedTestsExampleIT.groovy`
15. `plugin-integration-test/dockerTest/examples/database-app/app-image/build.gradle`
16. `plugin-integration-test/dockerTest/examples/database-app/app-image/src/integrationTest/groovy/com/kineticfire/test/DatabaseAppExampleIT.groovy`

### Integration Test Examples - JUnit 5
17. `plugin-integration-test/dockerTest/examples/isolated-tests-junit/app-image/build.gradle` (verify only)
18. `plugin-integration-test/dockerTest/examples/isolated-tests-junit/app-image/src/integrationTest/java/com/kineticfire/test/IsolatedTestsJUnit5ClassIT.java` (verify only)
19. `plugin-integration-test/dockerTest/examples/isolated-tests-junit/app-image/src/integrationTest/java/com/kineticfire/test/IsolatedTestsJUnit5MethodIT.java` (verify only)

### Example Documentation
20. `plugin-integration-test/dockerTest/examples/README.md` (add framework comparison)
21. `plugin-integration-test/dockerTest/examples/web-app/README.md`
22. `plugin-integration-test/dockerTest/examples/stateful-web-app/README.md`
23. `plugin-integration-test/dockerTest/examples/isolated-tests/README.md`
24. `plugin-integration-test/dockerTest/examples/database-app/README.md`
25. `plugin-integration-test/dockerTest/examples/isolated-tests-junit/README.md` (verify and update if needed)

### Usage Documentation
26. `docs/usage/usage-docker-orch.md`
27. `docs/design-docs/requirements/use-cases/uc-7-proj-dev-compose-orchestration.md`

## Acceptance Criteria

### Functional Requirements

**Configuration in build.gradle**:
- [ ] All Docker Compose configuration can be defined in build.gradle via dockerTest DSL
  - [ ] Compose file paths (single or multiple)
  - [ ] Project name
  - [ ] Wait for healthy settings (services, timeout, poll interval)
  - [ ] Wait for running settings (services, timeout, poll interval)
  - [ ] Stack name
- [ ] usesCompose method accepts stack name and lifecycle ("class" or "method")
- [ ] usesCompose sets ALL configuration as system properties for extensions to read

**Annotation Parameters**:
- [ ] All @ComposeUp annotation parameters are optional (Spock)
  - [ ] stackName can be empty string (read from system property)
  - [ ] composeFile can be empty string (read from system property)
  - [ ] composeFiles can be empty array (read from system property)
  - [ ] waitForHealthy can be empty array (read from system property)
  - [ ] waitForRunning can be empty array (read from system property)
  - [ ] timeoutSeconds can be 0 (read from system property)
  - [ ] pollSeconds can be 0 (read from system property)
  - [ ] lifecycle has default value but can be overridden
- [ ] Annotation compiles with zero parameters: `@ComposeUp`
- [ ] JUnit 5 @ExtendWith annotations remain parameter-less (already correct)

**Extension Behavior**:
- [ ] Test framework extensions read ALL configuration from system properties set by usesCompose
  - [ ] Stack name from `docker.compose.stack`
  - [ ] Compose files from `docker.compose.files` (comma-separated)
  - [ ] Project name from `docker.compose.projectName`
  - [ ] Wait for healthy services from `docker.compose.waitForHealthy.services`
  - [ ] Wait for healthy timeout from `docker.compose.waitForHealthy.timeoutSeconds`
  - [ ] Wait for healthy poll interval from `docker.compose.waitForHealthy.pollSeconds`
  - [ ] Wait for running services from `docker.compose.waitForRunning.services`
  - [ ] Wait for running timeout from `docker.compose.waitForRunning.timeoutSeconds`
  - [ ] Wait for running poll interval from `docker.compose.waitForRunning.pollSeconds`
  - [ ] State file path from `COMPOSE_STATE_FILE`
  - [ ] Lifecycle mode from `docker.compose.lifecycle` (Spock only)
- [ ] JUnit 5 extensions correctly read all system properties (verification only, no changes)

**Configuration Priority and Conflict Detection**:
- [ ] System properties (from build.gradle via usesCompose) take priority over annotation parameters
- [ ] **FAIL** (not warn) if BOTH system property AND annotation specify same parameter
- [ ] Error message clearly identifies the conflicting parameter
- [ ] Error message explains how to fix (remove annotation parameter)
- [ ] Fallback to annotation parameters when system properties not set (backward compatibility)

**Error Handling**:
- [ ] Clear error message when stack name not found in dockerTest.composeStacks
- [ ] Error message lists available stack names
- [ ] Clear error message when required configuration missing from both sources
- [ ] Clear error message when compose files not specified
- [ ] Error messages are actionable (tell user how to fix)

### Code Quality
- [ ] 100% unit test coverage for new/modified code
- [ ] All functional tests pass
- [ ] All integration tests pass
- [ ] No compilation warnings
- [ ] Code follows project style guidelines (≤120 char lines, ≤500 line files, etc.)

### Testing
- [ ] **100% pass rate required**: All tests at all levels must pass
- [ ] Unit tests verify system property setting and reading (at `plugin/src/test/`)
- [ ] Functional tests verify end-to-end behavior with TestKit (at `plugin/src/functionalTest/`)
- [ ] Integration tests verify real Docker Compose integration (at `plugin-integration-test/`)
- [ ] Backward compatibility tests verify annotation-only configuration still works
- [ ] No containers left running after tests (`docker ps -a` is clean)
- [ ] Zero test failures, zero test errors, no skipped tests

### Documentation
- [ ] usage-docker-orch.md shows usesCompose as primary pattern for both Spock and JUnit 5
- [ ] Framework comparison section added (Java/JUnit 5 vs Groovy/Spock)
- [ ] All examples use build.gradle configuration
- [ ] Backward compatibility documented (Spock annotation parameters)
- [ ] JUnit 5 documented as already using parameter-less extensions
- [ ] Configuration priority clearly explained
- [ ] Example READMEs updated for both frameworks

### Build and Deployment
- [ ] `./gradlew clean build` passes (from plugin/ directory)
- [ ] `./gradlew functionalTest` passes
- [ ] `./gradlew publishToMavenLocal` succeeds
- [ ] `./gradlew cleanAll integrationTest` passes (from plugin-integration-test/ directory)

### All Tests Must Pass
- [ ] **All unit tests** at `plugin/src/test/` must pass
- [ ] **All functional tests** at `plugin/src/functionalTest/` must pass
- [ ] **All integration tests** at `plugin-integration-test/` must pass
- [ ] Zero test failures, zero test errors
- [ ] No tests skipped or disabled without documented justification

## Migration Guide for Users

### Before (Duplication)

```groovy
// build.gradle
dockerTest {
    composeStacks {
        myApp { files.from('compose.yml') }
    }
}

// Test class
@ComposeUp(
    stackName = "myApp",           // DUPLICATE
    composeFile = "compose.yml",   // DUPLICATE
    waitForHealthy = ["app"],
    timeoutSeconds = 60
)
class MyAppIT extends Specification { }
```

### After (Single Source of Truth)

```groovy
// build.gradle - ALL configuration here
dockerTest {
    composeStacks {
        myApp {
            files.from('compose.yml')
            waitForHealthy {
                waitForServices.set(['app'])
                timeoutSeconds.set(60)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose stack: "myApp", lifecycle: "class"
}

// Test class - NO duplication, NO parameters
@ComposeUp  // That's it! All config in build.gradle
class MyAppIT extends Specification { }
```

### Migration Steps

1. Move all configuration from @ComposeUp annotation to dockerTest DSL in build.gradle
2. Add `usesCompose stack: "stackName", lifecycle: "class"` to test task
3. Remove ALL parameters from @ComposeUp annotation (leave just `@ComposeUp`)
4. Verify tests still pass
5. Remove commented-out old configuration

## Implementation Strategy and Rollback Plan

### Branch Strategy

**DO NOT work on main branch** - all work must be done in a feature branch:

1. **Create feature branch**: `feature/docker-orch-dsl-annotations-fix`
   - Branch from: `main`
   - Naming: Descriptive, follows project conventions
   - Purpose: Isolate all changes for this plan

2. **Incremental commits**:
   - Commit after each phase completes
   - Commit messages reference this plan document
   - Example: "Phase 1 Task 1.1: Enhance usesCompose() - refs docker-orch-dsl-annoations-fix.md"

3. **Testing checkpoints**:
   - After Phase 1: Run unit tests + functional tests
   - After Phase 2: Run unit tests + functional tests
   - After Phase 3: Run integration tests
   - After Phase 5: Run ALL tests (unit + functional + integration)

4. **Merge to main**:
   - Only merge when ALL acceptance criteria met
   - Require: 100% test pass rate at all levels
   - Require: Zero compilation warnings
   - Require: Clean `docker ps -a` output

### Incremental Verification Approach

**Test after each major change** to catch issues early:

**After Task 1.1 (usesCompose enhancement)**:
```bash
cd plugin
./gradlew test  # Unit tests for TestIntegrationExtension
```
Expected: All unit tests pass, new system properties are set correctly

**After Task 1.3 (DockerComposeSpockExtension update)**:
```bash
cd plugin
./gradlew test  # Unit tests for extension
./gradlew functionalTest  # End-to-end with TestKit
```
Expected: All tests pass, extensions read system properties

**After Task 2.1 (JUnit 5 verification)**:
```bash
cd plugin
./gradlew test functionalTest
```
Expected: JUnit 5 tests confirm system property reading

**After Phase 3 (examples updated)**:
```bash
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

cd ../plugin-integration-test
./gradlew cleanAll integrationTest
```
Expected: All integration tests pass, no configuration duplication

**Final Verification (before merge to main)**:
```bash
# Full plugin build
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build functionalTest publishToMavenLocal

# Full integration test suite
cd ../plugin-integration-test
./gradlew cleanAll integrationTest

# Verify no containers left
docker ps -a
```
Expected:
- Zero test failures across all levels
- Zero compilation warnings
- No containers in `docker ps -a`

### Rollback Plan

**If implementation fails or tests don't pass:**

1. **Identify failure point**: Which phase/task failed?

2. **Immediate rollback options**:
   - **Option A**: Revert last commit and retry
     ```bash
     git reset --hard HEAD~1
     ```

   - **Option B**: Revert to last working checkpoint
     ```bash
     git reset --hard <last-working-commit-sha>
     ```

   - **Option C**: Abandon feature branch, start fresh
     ```bash
     git checkout main
     git branch -D feature/docker-orch-dsl-annotations-fix
     git checkout -b feature/docker-orch-dsl-annotations-fix
     ```

3. **Investigate and fix**:
   - Review test failures carefully
   - Check unit test output for specific assertion failures
   - Review functional test output for configuration cache issues
   - Review integration test output for Docker Compose errors
   - Fix root cause before retrying

4. **Retry with fix**:
   - Make targeted fix
   - Re-run verification steps
   - Only proceed to next phase when current phase passes 100%

**Critical Rule**: Do NOT merge to main if ANY test fails or ANY acceptance criterion is not met.

### Migration Strategy: Maintaining Backward Compatibility

**Key Principle**: Existing code continues to work unchanged

**Phase 1-2 Changes (Infrastructure)**:
- Extensions enhanced to READ system properties
- No breaking changes - annotation parameters still work
- If system properties not set, extensions fall back to annotations
- Existing tests continue to pass

**Phase 3 Changes (Examples)**:
- Update examples to demonstrate new pattern
- Old pattern still works (backward compatible)
- Examples show best practice (usesCompose + zero-parameter annotation)

**User Migration Path** (optional, not required):
1. Users can keep using annotation-only configuration (works forever)
2. Users can migrate incrementally:
   - Add dockerTest DSL in build.gradle
   - Add usesCompose to test task
   - Remove annotation parameters one-by-one
   - Verify tests pass after each change
3. Users can adopt new pattern for new tests, keep old pattern for existing tests

**No forced migration** - this is purely additive functionality

## Risks and Mitigations

### Risk 1: Breaking Existing Users
**Mitigation**: Maintain backward compatibility - annotation parameters still work

### Risk 2: Complex System Property Serialization
**Mitigation**:
- Use simple comma-separated format for lists
- Test edge cases (file paths with spaces, special characters)
- Document serialization format

### Risk 3: Configuration Priority Confusion
**Mitigation**:
- Clear documentation of priority (system properties > annotation)
- FAIL (not warn) when both are specified (prevents duplication)
- Fail fast with helpful errors when neither is specified

### Risk 4: Integration Test Failures
**Mitigation**:
- Update examples incrementally (one at a time)
- Test each example after migration
- Keep integration tests in CI
- Use incremental verification approach

## Success Metrics

- [ ] Zero configuration duplication in examples
- [ ] All examples use @ComposeUp with zero parameters
- [ ] **100% test pass rate**: All unit tests, functional tests, and integration tests pass
  - Unit tests at `plugin/src/test/`
  - Functional tests at `plugin/src/functionalTest/`
  - Integration tests at `plugin-integration-test/`
- [ ] All integration tests pass with usesCompose pattern
- [ ] Documentation clearly explains single source of truth approach
- [ ] Backward compatibility tests pass (annotation parameters still work)
- [ ] No build warnings or errors

## Implementation Checklist

### Phase 1: Core Infrastructure
- [ ] Task 1.1: Enhance TestIntegrationExtension.usesCompose()
- [ ] Task 1.2: Make @ComposeUp annotation parameters optional
- [ ] Task 1.3: Update DockerComposeSpockExtension to read system properties

### Phase 2: JUnit 5 Extensions - Verification Only
- [ ] Task 2.1: Verify JUnit 5 extensions (already correct by design)

### Phase 3: Update Examples
- [ ] Task 3.1: Update web-app example (Spock)
- [ ] Task 3.2: Update stateful-web-app example (Spock)
- [ ] Task 3.3: Update isolated-tests example (Spock)
- [ ] Task 3.4: Update database-app example (Spock)
- [ ] Task 3.5: Verify JUnit 5 examples (JUnit 5)
- [ ] Task 3.6: Update main examples README (framework comparison)
- [ ] Task 3.7: Update individual example READMEs

### Phase 4: Update Documentation
- [ ] Task 4.1: Update usage-docker-orch.md
- [ ] Task 4.2: Update uc-7-proj-dev-compose-orchestration.md

### Phase 5: Testing
- [ ] Task 5.1: Unit tests
- [ ] Task 5.2: Functional tests
- [ ] Task 5.3: Integration tests

### Final Verification
- [ ] **All unit tests pass** at `plugin/src/test/` (100% pass rate required)
- [ ] **All functional tests pass** at `plugin/src/functionalTest/` (100% pass rate required)
- [ ] **All integration tests pass** at `plugin-integration-test/` (100% pass rate required)
- [ ] Zero test failures across all test suites
- [ ] No containers lingering (`docker ps -a` is clean)
- [ ] Documentation complete
- [ ] Examples demonstrate best practices
- [ ] Build completes without warnings or errors

## Notes

- This plan completes the original design vision from uc-7
- **All annotation parameters are optional**, including lifecycle mode (for Spock)
- **JUnit 5 extensions are already correct** - parameter-less by design
- Recommended usage: `@ComposeUp` (Spock) or `@ExtendWith(DockerComposeClassExtension.class)` (JUnit 5) with zero parameters, all config in build.gradle
- Maintains backward compatibility for Spock users who prefer annotation configuration
- Aligns with project goal: "all settings in build.gradle file"
- Eliminates DRY violation (configuration duplication)
- Makes examples more maintainable
- No conflicts possible since only one source of configuration (build.gradle)
- **Framework choice is about test syntax preference** - both use identical build.gradle configuration

## Critical Success Criteria: Test Requirements

**MANDATORY**: This plan is NOT complete until **100% of all tests pass** at all levels:

1. **Unit Tests** (`plugin/src/test/`):
   - All existing unit tests must continue to pass
   - New unit tests for system property reading must pass
   - Coverage must remain at 100% for modified code

2. **Functional Tests** (`plugin/src/functionalTest/`):
   - All existing functional tests must continue to pass
   - New functional tests for usesCompose integration must pass
   - TestKit tests must be configuration cache compatible

3. **Integration Tests** (`plugin-integration-test/`):
   - All existing integration tests must continue to pass
   - All examples must pass with new zero-parameter annotation approach
   - No containers left running after tests complete
   - Real Docker Compose integration verified

**Zero tolerance for test failures**: If any test fails, the implementation is incomplete and must be fixed before the plan can be considered done.

## Summary: Framework-Specific Work

### Spock (Groovy) - Major Changes Required

**Scope**: Redesign annotation to support zero-parameter usage
**Effort**: ~9-12 hours
**Tasks**:
1. Make all @ComposeUp parameters optional (including lifecycle)
2. Update DockerComposeSpockExtension to read system properties first
3. Add comprehensive tests for system property reading
4. Update all Spock examples to use zero-parameter annotation
5. Document backward compatibility

**Result**: `@ComposeUp` with no parameters, all config from build.gradle

### JUnit 5 (Java) - Verification Only

**Scope**: Verify existing implementation is correct
**Effort**: ~1-2 hours
**Tasks**:
1. Verify DockerComposeClassExtension reads system properties
2. Verify DockerComposeMethodExtension reads system properties
3. Add tests confirming system property reading
4. Ensure clear error messages
5. Update documentation

**Result**: No code changes needed - already correct by design!

### Documentation - Both Frameworks

**Scope**: Update usage guide with framework comparison
**Effort**: ~2-3 hours
**Tasks**:
1. Add "Choosing a Test Framework" section
2. Show examples for both Spock and JUnit 5
3. Explain framework differences (lifecycle, syntax)
4. Update all code examples

**Result**: Clear guidance for users choosing between frameworks
