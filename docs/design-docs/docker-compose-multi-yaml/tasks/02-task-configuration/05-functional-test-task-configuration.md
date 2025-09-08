# Task: Write Functional Tests for Task Configuration Logic

## Area: Task Configuration Logic

## Type: Functional Test Implementation

## Description
Create functional tests for task configuration logic with multi-file compose stacks, considering TestKit compatibility issues and aiming for comprehensive coverage.

## Context
You are a Test Engineer and Principal Software Engineer expert at Java, Gradle, custom Gradle plugins, Groovy, Docker, and Docker Compose. Be aware of the Gradle cache configuration for TestKit dependency per `@docs/design-docs/gradle-9-configuration-cache-guidance.md` and potential test disabling per `@docs/design-docs/functional-test-testkit-gradle-issue.md`.

## Requirements

### 1. TestKit Compatibility Awareness
- Gradle 9.0.0 has known TestKit issues with `withPluginClasspath()`
- Tests using `withPluginClasspath()` may encounter `InvalidPluginMetadataException`
- Tests may need to be disabled with proper documentation
- Code should be configuration cache compatible

### 2. Functional Test Areas

#### Plugin Configuration Integration
- Test that plugin correctly processes multi-file compose stack configurations
- Test task generation with multi-file stacks
- Test validation during project configuration
- Test integration between extension and task configuration

#### Build Script Parsing
- Test various DSL syntaxes for multi-file configuration:
  ```groovy
  dockerOrch {
      composeStacks {
          webapp {
              composeFiles('base.yml', 'override.yml')
          }
      }
  }
  ```
- Test backward compatibility with single-file syntax
- Test mixed configurations across multiple stacks

#### Task Generation and Configuration
- Test that correct tasks are generated for multi-file stacks
- Test task naming conventions are maintained
- Test task dependencies and relationships
- Test task configuration from extension properties

### 3. Test Implementation Approach

#### Option A: Implement Active Tests (If TestKit Works)
Create tests that verify end-to-end functionality:
```groovy
def "plugin creates compose tasks with multi-file configuration"() {
    given:
    settingsFile << "rootProject.name = 'test-multi-file'"
    def composeBase = testProjectDir.resolve('docker-compose.base.yml').toFile()
    def composeOverride = testProjectDir.resolve('docker-compose.override.yml').toFile()
    
    composeBase << """
        version: '3.8'
        services:
          web:
            image: nginx:latest
    """
    
    composeOverride << """
        version: '3.8'
        services:
          web:
            ports:
              - "8080:80"
    """
    
    buildFile << """
        plugins {
            id 'com.kineticfire.gradle.gradle-docker'
        }
        
        dockerOrch {
            composeStacks {
                webapp {
                    composeFiles('docker-compose.base.yml', 'docker-compose.override.yml')
                    projectName = 'test-project'
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath()
        .withArguments('tasks', '--group', 'docker compose')
        .build()

    then:
    result.task(':tasks').outcome == TaskOutcome.SUCCESS
    result.output.contains('composeUpWebapp')
    result.output.contains('composeDownWebapp')
}
```

#### Option B: Document and Disable (If TestKit Issues)
If TestKit compatibility issues prevent execution:
- Document the functional test scenarios in detail
- Add comprehensive comments explaining what would be tested
- Reference the TestKit compatibility issue
- Provide clear plan for re-enabling tests

### 4. Test Scenarios to Cover

#### Multi-File Configuration Scenarios
- Basic two-file configuration (base + override)
- Complex multi-file configuration (3+ files)
- File ordering and precedence testing
- Mixed file types and sources

#### Validation Scenarios
- Configuration with missing files
- Configuration with no files specified
- Invalid file paths and error handling
- Error message quality and clarity

#### Integration Scenarios
- Multiple stacks with different configurations
- Integration with existing single-file stacks
- Task generation and naming
- Configuration cache compatibility (if testable)

### 5. File Locations
Add/update tests in:
- `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/` directory
- Create new test class or update existing ones

## TestKit Issue Context
From `@docs/design-docs/functional-test-testkit-gradle-issue.md`:
- 18/20 functional tests currently disabled due to TestKit issues
- `InvalidPluginMetadataException` with `withPluginClasspath()`
- Known Gradle 9.0.0 compatibility problem
- Tests may need temporary disabling with documentation

## Configuration Cache Context  
From `@docs/design-docs/gradle-9-configuration-cache-guidance.md`:
- Tested code must use Provider API correctly
- No `.get()` calls during configuration
- Test compatibility with `--configuration-cache` if possible

## Acceptance Criteria

### If TestKit Works:
1. Functional tests verify end-to-end multi-file configuration
2. Plugin integration with multi-file stacks is tested
3. Task generation and configuration is verified
4. Error scenarios are functionally tested
5. Tests pass with `./gradlew clean functionalTest`
6. Configuration cache compatibility is verified if testable

### If TestKit Issues Persist:
1. Test scenarios are comprehensively documented
2. Tests are commented out with clear explanations
3. TestKit compatibility issue is referenced
4. Re-enabling plan is documented
5. Build succeeds without functional test failures

## Implementation Priority
1. **Assess TestKit compatibility** for current project state
2. **Implement tests** if TestKit works with multi-file scenarios
3. **Document and disable** if TestKit issues prevent execution
4. **Ensure build success** regardless of TestKit state

## Files to Create/Modify
- `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/MultiFileConfigurationFunctionalTest.groovy` (new)
- Update existing functional test files if appropriate

## Commands to Test
```bash
cd plugin
./gradlew clean functionalTest
./gradlew clean build
```

## Success Outcome
Either comprehensive functional tests that verify multi-file configuration behavior, or well-documented disabled tests that ensure build success while documenting the functionality that needs testing once TestKit compatibility is resolved.

## Status
**Status**: done  
**Date**: 2025-01-25  
**Description**: Implemented comprehensive functional tests for multi-file Docker Compose configuration in `MultiFileConfigurationFunctionalTest.groovy`. Due to Gradle 9.0.0 TestKit compatibility issues (InvalidPluginMetadataException), all 12 tests are documented but disabled. Tests cover multi-file configuration, task generation, DSL syntax variations, validation scenarios, error handling, and integration with environment files. Build succeeds with all tests properly documented for future re-enabling when TestKit compatibility is restored.