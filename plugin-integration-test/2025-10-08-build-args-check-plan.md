# Build Args Verification Helper - Implementation Plan

**Date**: 2025-10-08
**Status**: Ready for Implementation
**Author**: AI Coding Agent

## Executive Summary

This plan details the implementation of integration test helpers to verify that Docker build args configured in the
image build DSL are actually present in the resultant Docker images. The solution uses `docker history` parsing to
extract and verify build arg values non-intrusively.

## Background

### Problem Statement

Integration tests need to verify that build args configured in the `docker` DSL are correctly passed to Docker during
image builds. This verification must:
1. Support images with 1 or more build args
2. Support images with 0 build args (verify none were set)
3. Work across multiple image tags
4. Run after images are built and tagged

### Technical Investigation Results

Docker build args are **not directly stored in final image metadata**, but they **are visible in image history** via
the `docker history` command. When a `RUN` instruction executes while ARG declarations are in scope, Docker annotates
that layer with the build arg values.

**Evidence from actual docker history output:**

```bash
# Scenario 1 (2 build args):
|2 BUILD_VERSION=1.0.0 JAR_FILE=app-1.0.0.jar /bin/sh -c apt-get update && apt-get install -y curl ...

# Scenario 2 (1 build arg):
|1 JAR_FILE=app-1.0.0.jar /bin/sh -c apt-get update && apt-get install -y curl ...

# Scenario 3 (0 build args):
/bin/sh -c apt-get update && apt-get install -y curl ...
```

**Key findings:**
- ✅ Build args appear in history with pattern: `|<count> ARG1=value1 ARG2=value2 ... /bin/sh -c <command>`
- ✅ Zero build args can be verified by absence of the `|N` pattern
- ✅ Multiple tags of same image show identical build args
- ✅ All existing Dockerfiles have RUN instructions after ARG declarations, making this approach viable

## Solution Architecture

### Design Overview

Follow the established pattern in `buildSrc` for Docker image verification tasks:
1. **Task Class**: Custom Gradle task with configuration cache compatibility
2. **Registration Function**: Extension function in `docker-image-testing.gradle`
3. **Integration Usage**: Applied in scenario build.gradle files

### Components

#### 1. DockerBuildArgsVerifyTask.groovy

**File**: `plugin-integration-test/buildSrc/src/main/groovy/DockerBuildArgsVerifyTask.groovy`

**Purpose**: Custom Gradle task to verify build args in Docker images using history parsing

**Key Features**:
- Gradle 9/10 configuration cache compatible
- Uses `ProcessBuilder` for Docker command execution
- Supports exact match and subset match modes
- Handles zero build args verification
- Works with multiple image tags

**Input Properties**:
```groovy
@Input
abstract ListProperty<String> getImageNames()  // List of "image:tag" references

@Input
abstract MapProperty<String, MapProperty<String, String>> getExpectedBuildArgs()
// Map of "image:tag" -> Map of "ARG_NAME" -> "expected_value"

@Input
@Optional
abstract Property<Boolean> getStrictMode()  // Default: true (exact match)
```

**Core Logic**:
```groovy
@TaskAction
void verifyBuildArgs() {
    def imagesToVerify = imageNames.get()
    def expectedArgs = expectedBuildArgs.get()

    imagesToVerify.each { imageRef ->
        def expected = expectedArgs[imageRef] ?: [:]
        def actual = extractBuildArgsFromHistory(imageRef)

        if (strictMode.get()) {
            verifyExactMatch(imageRef, expected, actual)
        } else {
            verifySubsetMatch(imageRef, expected, actual)
        }
    }
}

private Map<String, String> extractBuildArgsFromHistory(String imageRef) {
    // Execute: docker history <image> --no-trunc --format '{{.CreatedBy}}'
    // Parse for pattern: |N ARG1=val1 ARG2=val2 /bin/sh -c ...
    // Return: Map of arg name -> value
}
```

**Parsing Strategy**:
```groovy
def buildArgPattern = ~/\|(\d+)\s+(.+?)\s+\/bin\/sh/

def extractBuildArgsFromHistory(String imageRef) {
    def process = new ProcessBuilder('docker', 'history', imageRef, '--no-trunc',
                                     '--format', '{{.CreatedBy}}')
        .redirectErrorStream(true)
        .start()

    def output = process.inputStream.text
    process.waitFor()

    if (process.exitValue() != 0) {
        throw new RuntimeException("Failed to get history for ${imageRef}: ${output}")
    }

    // Parse each line looking for build args pattern
    for (line in output.split('\n')) {
        def matcher = buildArgPattern.matcher(line)
        if (matcher.find()) {
            def count = matcher.group(1).toInteger()
            def argsString = matcher.group(2)

            // Parse "ARG1=val1 ARG2=val2" into map
            return parseArgString(argsString)
        }
    }

    // No build args found
    return [:]
}

private Map<String, String> parseArgString(String argsString) {
    def args = [:]
    def currentKey = null
    def currentValue = new StringBuilder()

    // Handle args with spaces in values: ARG1=value1 ARG2=value with spaces ARG3=value3
    argsString.split(/\s+(?=[A-Z_]+=)/).each { arg ->
        def parts = arg.split('=', 2)
        if (parts.size() == 2) {
            args[parts[0]] = parts[1]
        }
    }

    return args
}
```

**Verification Logic**:
```groovy
private void verifyExactMatch(String imageRef, Map expected, Map actual) {
    if (expected.size() != actual.size()) {
        throw new RuntimeException("""
Build args count mismatch for ${imageRef}:
  Expected ${expected.size()} args: ${expected.keySet()}
  Found ${actual.size()} args: ${actual.keySet()}
""")
    }

    expected.each { argName, expectedValue ->
        if (!actual.containsKey(argName)) {
            throw new RuntimeException("Missing build arg '${argName}' in ${imageRef}")
        }
        if (actual[argName] != expectedValue) {
            throw new RuntimeException("""
Build arg value mismatch for '${argName}' in ${imageRef}:
  Expected: ${expectedValue}
  Actual: ${actual[argName]}
""")
        }
    }

    logger.lifecycle("✓ Verified ${expected.size()} build args for ${imageRef}")
}

private void verifySubsetMatch(String imageRef, Map expected, Map actual) {
    // Verify expected args are present (allow additional args)
    expected.each { argName, expectedValue ->
        if (!actual.containsKey(argName)) {
            throw new RuntimeException("Missing build arg '${argName}' in ${imageRef}")
        }
        if (actual[argName] != expectedValue) {
            throw new RuntimeException("""
Build arg value mismatch for '${argName}' in ${imageRef}:
  Expected: ${expectedValue}
  Actual: ${actual[argName]}
""")
        }
    }

    logger.lifecycle("✓ Verified ${expected.size()} expected build args in ${imageRef}")
}
```

**Error Handling**:
- Image doesn't exist: Fail with message directing user to run build first
- Unexpected build args: List actual vs expected with clear diff
- Missing build args: List which expected args are missing
- Docker command failure: Capture and include stderr in error message

#### 2. Registration Function

**File**: `plugin-integration-test/buildSrc/src/main/groovy/docker-image-testing.gradle`

**Add this function**:
```groovy
/**
 * Register Docker build args verification task.
 *
 * Verifies that Docker images were built with expected build arguments by parsing
 * the docker history output. Supports verification of images with zero, one, or
 * multiple build args.
 *
 * @param project The Gradle project to register tasks on
 * @param buildArgsPerImage Map of image references to their expected build args
 *                          Format: ['image:tag': ['ARG_NAME': 'value', ...]]
 *                          Empty map for an image means expect zero build args
 */
ext.registerVerifyBuildArgsTask = { Project project, Map<String, Map<String, String>> buildArgsPerImage ->
    if (buildArgsPerImage == null || buildArgsPerImage.isEmpty()) {
        throw new IllegalArgumentException("buildArgsPerImage cannot be null or empty")
    }

    project.tasks.register('verifyDockerBuildArgs', DockerBuildArgsVerifyTask) {
        it.imageNames.set(buildArgsPerImage.keySet().toList())
        it.expectedBuildArgs.set(buildArgsPerImage)
        it.strictMode.set(true)  // Default to exact match
        it.group = 'verification'
        it.description = 'Verify Docker images were built with expected build arguments'
    }

    project.logger.info("Registered build args verification for images: ${buildArgsPerImage.keySet()}")
}
```

#### 3. Documentation Update

**File**: `plugin-integration-test/buildSrc/README.md`

**Add section after "registerVerifyRegistryImagesTask"**:

```markdown
### registerVerifyBuildArgsTask

Verifies that Docker images were built with expected build arguments.

```groovy
registerVerifyBuildArgsTask(project, buildArgsPerImage)
```

**Parameters:**
- `project`: The Gradle project to register tasks on
- `buildArgsPerImage`: Map of image references to their expected build args

**Format:**
```groovy
[
    'image:tag': ['ARG_NAME': 'expected_value', ...],
    'another:tag': [:]  // Empty map = expect zero build args
]
```

**Creates Task:** `verifyDockerBuildArgs` in the `verification` group

**How It Works:**
- Executes `docker history` to extract build arg values from image layers
- Parses the history output for build arg annotations (pattern: `|N ARG=value`)
- Compares extracted args against expected values
- Supports verification of zero build args (no annotation found)

**Example:**
```groovy
// Verify multiple build args
registerVerifyBuildArgsTask(project, [
    'my-app:1.0.0': [
        'JAR_FILE': 'app-1.0.0.jar',
        'BUILD_VERSION': '1.0.0'
    ],
    'my-app:latest': [
        'JAR_FILE': 'app-1.0.0.jar',
        'BUILD_VERSION': '1.0.0'
    ]
])

// Verify single build arg
registerVerifyBuildArgsTask(project, [
    'my-service:dev': [
        'ENV': 'development'
    ]
])

// Verify zero build args (empty map)
registerVerifyBuildArgsTask(project, [
    'base-image:latest': [:]
])
```

**Task Ordering:**
The verification task must run after build and tag operations:

```groovy
tasks.register('integrationTest') {
    dependsOn 'dockerBuild'
    dependsOn 'dockerTag'
    dependsOn 'verifyDockerBuildArgs'
}

tasks.named('verifyDockerBuildArgs') {
    mustRunAfter 'dockerBuild'
    mustRunAfter 'dockerTag'
}
```
```

## Implementation Demonstration

### Scenario 1: Multiple Build Args, Multiple Tags

**File**: `plugin-integration-test/docker/scenario-1/build.gradle`

**Current build args configuration**:
```groovy
buildArgs.put("JAR_FILE", "app-${project.version}.jar")
buildArgs.put("BUILD_VERSION", project.version.toString())
```

**Add verification after existing workflow tasks**:
```groovy
// Register build args verification for this integration test
registerVerifyBuildArgsTask(project, [
    'scenario1-time-server:1.0.0': [
        'JAR_FILE': 'app-1.0.0.jar',
        'BUILD_VERSION': '1.0.0'
    ],
    'scenario1-time-server:latest': [
        'JAR_FILE': 'app-1.0.0.jar',
        'BUILD_VERSION': '1.0.0'
    ]
])
```

**Update integrationTest task**:
```groovy
tasks.register('integrationTest') {
    description = 'Run complete Docker integration test: clean → build → verify'
    group = 'verification'
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerBuild'
    dependsOn 'verifyDockerImages'
    dependsOn 'verifyDockerBuildArgs'  // NEW
}

tasks.named('verifyDockerBuildArgs') {
    mustRunAfter 'dockerBuild'
    mustRunAfter 'verifyDockerImages'
}
```

### Scenario 2: Single Build Arg, Single Tag

**File**: `plugin-integration-test/docker/scenario-2/build.gradle`

**Current build args configuration**:
```groovy
buildArgs.put("JAR_FILE", "app-${project.version}.jar")
```

**Add verification after existing workflow tasks**:
```groovy
// Register build args verification for this integration test
registerVerifyBuildArgsTask(project, [
    'scenario2/scenario2-time-server:latest': [
        'JAR_FILE': 'app-1.0.0.jar'
    ]
])
```

**Update integrationTest task**:
```groovy
tasks.register('integrationTest') {
    description = 'Run complete Docker integration test: clean → build → save → publish → verify'
    group = 'verification'

    dependsOn 'startTestRegistries'
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerImages'
    dependsOn 'verifyDockerImages'
    dependsOn 'verifySavedDockerImages'
    dependsOn 'verifyRegistryDockerImages'
    dependsOn 'verifyDockerBuildArgs'  // NEW
    dependsOn 'stopTestRegistries'
}

tasks.named('verifyDockerBuildArgs') {
    mustRunAfter 'dockerImages'
    mustRunAfter 'verifyDockerImages'
}
```

### Scenario 3: Zero Build Args

**File**: `plugin-integration-test/docker/scenario-3/build.gradle`

**Current configuration**: No `buildArgs` configuration (none passed to Docker)

**Add verification after existing workflow tasks**:
```groovy
// Register build args verification for this integration test
// Empty map indicates we expect ZERO build args
registerVerifyBuildArgsTask(project, [
    'localhost:5000/scenario3/scenario3-time-server:latest': [:]
])
```

**Update integrationTest task**:
```groovy
tasks.register('integrationTest') {
    description = 'Run complete Docker integration test: clean → build → save → publish → verify'
    group = 'verification'

    dependsOn 'startTestRegistries'
    dependsOn 'cleanDockerImages'
    dependsOn 'dockerImages'
    dependsOn 'verifyDockerImages'
    dependsOn 'verifySavedDockerImages'
    dependsOn 'verifyRegistryDockerImages'
    dependsOn 'verifyDockerBuildArgs'  // NEW
    dependsOn 'stopTestRegistries'
}

tasks.named('verifyDockerBuildArgs') {
    mustRunAfter 'dockerImages'
    mustRunAfter 'verifyDockerImages'
}
```

## Testing Strategy

### Unit Tests

**File**: `plugin-integration-test/buildSrc/src/test/groovy/DockerBuildArgsVerifyTaskTest.groovy`

**Test cases**:
1. **testVerifyWithMultipleBuildArgs** - Verify task correctly identifies 2 build args
2. **testVerifyWithSingleBuildArg** - Verify task correctly identifies 1 build arg
3. **testVerifyWithZeroBuildArgs** - Verify task correctly identifies 0 build args
4. **testMissingBuildArg** - Verify task fails when expected arg is missing
5. **testUnexpectedBuildArg** - Verify task fails when unexpected arg is found
6. **testBuildArgValueMismatch** - Verify task fails when arg value doesn't match
7. **testImageDoesNotExist** - Verify task fails gracefully with clear error message
8. **testMultipleTags** - Verify same args across multiple tags
9. **testParseArgString** - Test parsing of arg string with various formats
10. **testConfigurationCacheCompatibility** - Verify task is configuration cache compatible

### Integration Tests

**Update**: `plugin-integration-test/buildSrc/src/test/groovy/DockerImageTestingLibraryTest.groovy`

**Add test cases**:
```groovy
def "registerVerifyBuildArgsTask creates task with correct configuration"() {
    given:
    def buildArgsMap = [
        'test-image:1.0.0': ['ARG1': 'value1', 'ARG2': 'value2'],
        'test-image:latest': ['ARG1': 'value1', 'ARG2': 'value2']
    ]

    when:
    registerVerifyBuildArgsTask(project, buildArgsMap)

    then:
    def task = project.tasks.getByName('verifyDockerBuildArgs')
    task instanceof DockerBuildArgsVerifyTask
    task.imageNames.get() == ['test-image:1.0.0', 'test-image:latest']
    task.expectedBuildArgs.get() == buildArgsMap
    task.group == 'verification'
}

def "registerVerifyBuildArgsTask with empty map expects zero build args"() {
    given:
    def buildArgsMap = ['base-image:latest': [:]]

    when:
    registerVerifyBuildArgsTask(project, buildArgsMap)

    then:
    def task = project.tasks.getByName('verifyDockerBuildArgs')
    task.expectedBuildArgs.get()['base-image:latest'] == [:]
}
```

### Real Integration Tests

Run actual integration tests for all three scenarios:

```bash
# Scenario 1: 2 build args, 2 tags
cd plugin-integration-test
./gradlew docker:scenario-1:integrationTest

# Scenario 2: 1 build arg, 1 tag
./gradlew docker:scenario-2:integrationTest

# Scenario 3: 0 build args, 1 tag
./gradlew docker:scenario-3:integrationTest
```

**Success criteria**:
- All tests pass without errors
- Build args verification correctly identifies expected args
- Clear error messages when args don't match
- No lingering Docker containers after tests

## Gradle 9/10 Compatibility

### Configuration Cache Compatibility

✅ **Uses Provider API**:
- `ListProperty<String>` for image names
- `MapProperty<String, MapProperty<String, String>>` for build args
- `Property<Boolean>` for strict mode flag

✅ **No Project references in task action**:
- Uses `ProcessBuilder` instead of `project.exec`
- All configuration resolved via properties

✅ **Serializable task inputs**:
- All inputs are primitive types or standard collections
- No lambda captures of non-serializable objects

### Best Practices Applied

```groovy
abstract class DockerBuildArgsVerifyTask extends DefaultTask {
    // ✅ Use abstract properties with @Input annotation
    @Input
    abstract ListProperty<String> getImageNames()

    @Input
    abstract MapProperty<String, MapProperty<String, String>> getExpectedBuildArgs()

    @Input
    @Optional
    abstract Property<Boolean> getStrictMode()

    @TaskAction
    void verifyBuildArgs() {
        // ✅ Resolve providers in task action, not configuration
        def imagesToVerify = imageNames.get()
        def expectedArgs = expectedBuildArgs.get()

        // ✅ Use ProcessBuilder for external commands
        def process = new ProcessBuilder('docker', 'history', ...)
            .redirectErrorStream(true)
            .start()
    }
}
```

## Verification Matrix

| Scenario | Build Args | Tags | Test Focus | Expected Result |
|----------|------------|------|------------|-----------------|
| scenario-1 | 2 | 2 | Multiple args, multiple tags | ✓ Verify both args in both tags |
| scenario-2 | 1 | 1 | Single arg, single tag | ✓ Verify single arg present |
| scenario-3 | 0 | 1 | No args verification | ✓ Verify no args pattern |

## Implementation Checklist

### Phase 1: Core Implementation
- [ ] Create `DockerBuildArgsVerifyTask.groovy`
- [ ] Implement docker history parsing logic
- [ ] Implement build args extraction and parsing
- [ ] Implement exact match verification
- [ ] Implement error handling and messaging
- [ ] Add configuration cache compatibility

### Phase 2: Registration Function
- [ ] Add `registerVerifyBuildArgsTask` to `docker-image-testing.gradle`
- [ ] Implement validation logic
- [ ] Add logging

### Phase 3: Documentation
- [ ] Update `buildSrc/README.md` with new function
- [ ] Add usage examples
- [ ] Document task ordering requirements

### Phase 4: Unit Tests
- [ ] Create `DockerBuildArgsVerifyTaskTest.groovy`
- [ ] Implement all test cases from testing strategy
- [ ] Verify 100% code coverage

### Phase 5: Integration Test Updates
- [ ] Update scenario-1 build.gradle with verification
- [ ] Update scenario-2 build.gradle with verification
- [ ] Update scenario-3 build.gradle with verification
- [ ] Update task ordering in all scenarios

### Phase 6: Validation
- [ ] Run unit tests: `./gradlew :buildSrc:test`
- [ ] Run scenario-1 integration test
- [ ] Run scenario-2 integration test
- [ ] Run scenario-3 integration test
- [ ] Verify no lingering containers: `docker ps -a`
- [ ] Test configuration cache: `--configuration-cache`

## Limitations and Edge Cases

### Known Limitations

1. **Requires RUN instruction after ARG**: Build args only appear in history if a RUN instruction executes after ARG
   declarations. All current Dockerfiles satisfy this requirement.

2. **Unused ARGs won't appear**: If a build arg is declared in Dockerfile but never used, it won't appear in history.
   This is acceptable as we're verifying what was PASSED, not what was DECLARED.

3. **Docker version dependencies**: Format of `docker history` output may vary slightly across Docker versions.
   Tested with Docker 20+.

4. **Value truncation**: Some Docker versions may truncate very long arg values in history output. Use reasonable
   length arg values.

### Edge Cases Handled

✅ **Image doesn't exist**: Fail with clear error directing to run build first
✅ **Multiple tags same image**: Correctly verifies same args across all tags
✅ **Zero build args**: Correctly identifies absence of build args
✅ **Special characters in values**: Parsing handles spaces and special chars in arg values
✅ **Docker command failure**: Captures stderr and includes in error message

## Alternative Approaches (Not Recommended)

### Alternative 1: Label-Based Verification

Modify Dockerfiles to echo build args to labels:
```dockerfile
ARG JAR_FILE
LABEL build_arg.JAR_FILE="${JAR_FILE}"
```

Then verify via `docker inspect --format '{{json .Config.Labels}}'`

**Why not recommended**:
- ❌ Requires modifying all Dockerfiles (intrusive)
- ❌ Violates principle of testing as user would
- ❌ Adds label pollution to production images
- ❌ Doesn't test actual Docker build arg behavior

### Alternative 2: Build Log Parsing

Parse Docker build output for build arg usage.

**Why not recommended**:
- ❌ Build logs may not be captured in all scenarios
- ❌ Log format varies across Docker versions
- ❌ Less reliable than image history
- ❌ Cannot verify after-the-fact (requires build log retention)

## Success Criteria

### Definition of Done

✅ All unit tests pass with 100% code coverage
✅ All three integration test scenarios pass
✅ Build args correctly verified for 2-arg, 1-arg, and 0-arg cases
✅ Multiple image tags correctly verified
✅ Clear error messages for mismatches
✅ No lingering Docker containers after tests
✅ Configuration cache compatible (verified with `--configuration-cache`)
✅ Documentation complete and accurate
✅ Code follows project standards (line length, complexity, etc.)

## Timeline Estimate

- **Phase 1-2** (Core + Registration): 2-3 hours
- **Phase 3** (Documentation): 30 minutes
- **Phase 4** (Unit Tests): 2 hours
- **Phase 5** (Integration Updates): 1 hour
- **Phase 6** (Validation): 1 hour

**Total**: 6.5-7.5 hours

## Conclusion

This implementation plan provides a robust, non-intrusive solution for verifying Docker build args in integration
tests. The approach:

✅ Works with existing Dockerfiles (no modifications required)
✅ Tests actual Docker behavior (not synthetic proxies)
✅ Supports zero, single, and multiple build args
✅ Works across multiple image tags
✅ Follows established helper patterns
✅ Fully Gradle 9/10 compatible
✅ Comprehensive test coverage

The solution is production-ready and can be implemented immediately following this plan.
