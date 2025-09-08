# Task: Write Functional Tests for ComposeStackSpec Multi-File Properties

## Area: ComposeStackSpec Enhancement

## Type: Functional Test Implementation

## Description
Create functional tests for ComposeStackSpec multi-file properties using TestKit, aiming for 100% functional test coverage while being aware of Gradle 9 configuration cache compatibility issues.

## Context
You are a Test Engineer and Principal Software Engineer expert at Java, Gradle, custom Gradle plugins, Groovy, Docker, and Docker Compose. Be aware of the Gradle cache configuration for TestKit dependency per `@docs/design-docs/gradle-9-configuration-cache-guidance.md` and potential test disabling per `@docs/design-docs/functional-test-testkit-gradle-issue.md`.

## Requirements

### 1. TestKit Compatibility Considerations
- Be aware that Gradle 9.0.0 has TestKit compatibility issues with `withPluginClasspath()`
- Tests using `withPluginClasspath()` may need to be disabled due to `InvalidPluginMetadataException`
- Code should be compatible with Gradle configuration cache, but tests may be disabled if they use problematic dependencies

### 2. Functional Test Areas

#### Multi-File DSL Configuration Testing
- Test plugin applies correctly with multi-file compose stack configurations
- Test DSL parsing of `composeFiles('file1.yml', 'file2.yml')`
- Test DSL parsing of `composeFiles = ['file1.yml', 'file2.yml']`
- Test DSL parsing of `composeFiles(file('base.yml'), file('override.yml'))`

#### Integration with Plugin Application
- Test that multi-file configurations are correctly processed during plugin application
- Test that task generation works with multi-file stacks
- Test validation of multi-file configurations during build configuration

#### Backward Compatibility
- Test that single-file `composeFile` configurations still work
- Test that projects can mix single-file and multi-file stack configurations
- Test migration scenarios from single-file to multi-file

### 3. Test Implementation Strategy

#### Option A: Implement Tests (If TestKit Works)
If TestKit compatibility is resolved:
```groovy
def "multi-file compose configuration works correctly"() {
    given:
    settingsFile << "rootProject.name = 'test-multi-compose'"
    buildFile << """
        plugins {
            id 'com.kineticfire.gradle.gradle-docker'
        }
        
        dockerOrch {
            composeStacks {
                webapp {
                    composeFiles('docker-compose.yml', 'docker-compose.override.yml')
                }
            }
        }
    """
    
    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath()
        .withArguments('tasks', '--all')
        .build()

    then:
    result.task(':tasks').outcome == TaskOutcome.SUCCESS
}
```

#### Option B: Document and Disable (If TestKit Issues Persist)
If TestKit has compatibility issues:
- Document the functional test scenarios that would be tested
- Add detailed comments explaining the Gradle 9.0.0 TestKit limitation
- Temporarily disable the tests with clear documentation

### 4. Test File Structure
Add tests to existing functional test file or create new one:
- Update `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/ComposeStackSpecFunctionalTest.groovy`

## Files to Modify
- `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/` (new or existing test files)

## TestKit Issue Awareness
Reference `@docs/design-docs/functional-test-testkit-gradle-issue.md`:
- 18 out of 20 functional tests are currently disabled due to TestKit compatibility issues
- `InvalidPluginMetadataException` occurs with `withPluginClasspath()` in Gradle 9.0.0
- Tests may need to be temporarily disabled with detailed documentation

## Configuration Cache Compatibility
Reference `@docs/design-docs/gradle-9-configuration-cache-guidance.md`:
- Ensure tested code is configuration cache compatible
- Use Provider API correctly in DSL implementations
- No `.get()` calls on providers during configuration
- Test that configurations work with `--configuration-cache` flag

## Acceptance Criteria

### If TestKit Works:
1. Functional tests are implemented for all multi-file scenarios
2. Tests cover DSL parsing and plugin integration
3. Backward compatibility is verified
4. All tests pass with `./gradlew clean functionalTest`
5. Tests provide meaningful coverage of functional behavior

### If TestKit Issues Persist:
1. Functional test scenarios are documented in detail
2. Tests are commented out with clear explanations
3. References to TestKit compatibility issue are included
4. Documentation explains what would be tested when TestKit is fixed
5. Build succeeds without functional test failures

## Commands to Test
```bash
cd plugin
./gradlew clean functionalTest
./gradlew clean build
```

## Expected Outcome
Either working functional tests or properly documented disabled tests, ensuring that the build succeeds and the multi-file functionality would be properly tested once TestKit compatibility is resolved.