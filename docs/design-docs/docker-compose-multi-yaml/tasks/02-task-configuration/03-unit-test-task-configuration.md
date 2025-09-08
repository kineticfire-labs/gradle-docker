# Task: Write Unit Tests for Task Configuration Logic

## Area: Task Configuration Logic

## Type: Unit Test Implementation

## Description
Create comprehensive unit tests for the task configuration logic that handles multi-file compose stacks, ensuring 100% coverage and build success with `./gradlew clean build`.

## Context
You are a Test Engineer and Principal Software Engineer expert at Java, Gradle, custom Gradle plugins, Groovy, Docker, and Docker Compose. You must ensure all unit tests pass and the build works with `./gradlew clean build`.

## Requirements

### 1. Test Areas to Cover

#### Task Configuration Testing
- Test configuration of `ComposeUpTask.composeFiles` from multi-file properties
- Test configuration from single-file properties (backward compatibility)
- Test priority logic when both single-file and multi-file are specified
- Test file path to File object conversion
- Test file order preservation

#### Validation Testing
- Test validation of at least one compose file requirement
- Test file existence validation
- Test error messages for invalid configurations
- Test handling of missing files
- Test mixed valid/invalid file scenarios

#### Extension Integration Testing
- Test integration with `DockerOrchExtension.validateStackSpec()`
- Test task naming and registration
- Test configuration with different stack names
- Test integration with existing validation patterns

#### Provider API Testing
- Test that configuration uses Provider API correctly
- Test that no `.get()` calls occur during configuration
- Test provider transformations work correctly
- Test serialization compatibility

### 2. Test Files to Update/Create

#### Update Existing Tests
- Update `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtensionTest.groovy`
  - Add multi-file configuration tests
  - Test validation with new properties
  - Test backward compatibility scenarios

#### Create New Tests (if needed)
- Create tests for any new configuration classes or helper methods
- Test any new validation logic separately

### 3. Test Scenarios

#### Single-File Scenarios (Backward Compatibility)
- Test existing `composeFile` property still works
- Test task is configured correctly from single file
- Test validation works with single file

#### Multi-File Scenarios
- Test `composeFiles` string list configuration
- Test `composeFileCollection` File collection configuration
- Test mixed configuration approaches
- Test file ordering preservation

#### Error Scenarios
- Test configuration with no files specified
- Test configuration with non-existent files
- Test configuration with invalid file paths
- Test meaningful error messages

#### Integration Scenarios
- Test plugin configuration creates correct tasks
- Test task dependencies are maintained
- Test multiple stacks with different configurations

### 4. Testing Patterns
Follow existing project patterns:
- Use Spock framework for test structure
- Use `ProjectBuilder` for Gradle project testing
- Use `@TempDir` for file system operations
- Mock external dependencies appropriately
- Follow given/when/then structure

### 5. Coverage Requirements
- Achieve 100% line coverage for new configuration logic
- Achieve 100% branch coverage for new configuration logic
- All tests must pass with `./gradlew clean test`
- Build must succeed with `./gradlew clean build`

## Files to Modify/Create
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtensionTest.groovy`
- Any additional test files for new configuration classes

## Test Implementation Guidelines

### Setup Pattern
```groovy
def setup() {
    project = ProjectBuilder.builder().build()
    project.pluginManager.apply('com.kineticfire.gradle.gradle-docker')
    extension = project.extensions.getByType(DockerOrchExtension)
}
```

### Multi-File Test Example
```groovy
def "configures task with multiple compose files"() {
    given:
    def composeFile1 = project.file('compose1.yml')
    def composeFile2 = project.file('compose2.yml')
    [composeFile1, composeFile2].each { 
        it.parentFile.mkdirs()
        it.createNewFile() 
    }

    when:
    extension.composeStacks {
        webapp {
            composeFiles('compose1.yml', 'compose2.yml')
        }
    }
    project.evaluate() // Trigger task configuration

    then:
    def task = project.tasks.getByName('composeUpWebapp')
    task.composeFiles.files.containsAll([composeFile1, composeFile2])
}
```

## Acceptance Criteria
1. All new configuration logic is thoroughly tested
2. Backward compatibility is verified through tests
3. Error scenarios are properly tested
4. 100% line and branch coverage achieved for new code
5. All tests pass with `./gradlew clean test`
6. Build succeeds with `./gradlew clean build`
7. Tests follow existing project conventions
8. Provider API usage is tested correctly
9. Integration with existing extension logic is verified

## Coverage Areas
Ensure tests cover:
- Task configuration from multi-file properties
- Task configuration from single-file properties
- Priority and precedence logic
- File validation and error handling
- Integration with extension validation
- Provider API usage and serialization
- File order preservation
- Error message quality and clarity