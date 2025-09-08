# Task: Write Unit Tests for Multi-File Validation Logic

## Area: Task Configuration Logic

## Type: Unit Test Implementation

## Description
Create comprehensive unit tests for the multi-file validation logic in `DockerOrchExtension.validateStackSpec()` and related validation methods, ensuring 100% coverage.

## Context
You are a Test Engineer and Principal Software Engineer expert at Java, Gradle, custom Gradle plugins, Groovy, Docker, and Docker Compose. You must ensure all unit tests pass and the build works with `./gradlew clean build`.

## Requirements

### 1. Validation Testing Areas

#### Multi-File Configuration Validation
- Test validation of `composeFiles` ListProperty configurations
- Test validation of `composeFileCollection` ConfigurableFileCollection configurations  
- Test mixed multi-file configuration scenarios
- Test empty multi-file configuration handling

#### Single-File Configuration Validation (Backward Compatibility)
- Test existing single-file `composeFile` validation continues to work
- Test single-file validation with different file types and paths
- Test migration scenarios from single-file to multi-file

#### Priority Logic Testing
- Test validation when both single-file and multi-file properties are set
- Test validation when only single-file property is set
- Test validation when only multi-file properties are set
- Test validation when no properties are set (error case)
- **Test that ComposeDown validation inherits from ComposeUp configuration** (no separate validation needed)

#### File Existence Validation
- Test validation with existing compose files
- Test validation with missing compose files  
- Test validation with mixed existing/missing files
- Test validation with invalid file paths
- Test validation with relative vs absolute paths

#### Error Message Testing
- Test error messages for missing files
- Test error messages for empty configurations
- Test error messages for invalid file paths
- Test error messages for duplicate files (if applicable)
- Verify error messages are clear and actionable

### 2. Test Structure and Patterns

#### Follow Existing Test Patterns
- Use Spock framework for test structure
- Use `ProjectBuilder` for Gradle project testing
- Use `@TempDir` for file system operations during tests
- Follow given/when/then structure
- Use meaningful test names and descriptions

#### Provider API Testing
- Test validation logic that works with Provider API
- Test that validation doesn't call `.get()` during configuration
- Test provider transformations used in validation
- Test serialization compatibility of validation results

### 3. Test File Location
Update or create tests in:
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtensionTest.groovy`

### 4. Test Examples

#### Multi-File Validation Test
```groovy
def "validates multi-file compose configuration successfully"() {
    given:
    def composeFile1 = createTempFile('compose1.yml')
    def composeFile2 = createTempFile('compose2.yml')
    
    extension.composeStacks {
        webapp {
            composeFiles(composeFile1.path, composeFile2.path)
        }
    }

    when:
    def result = extension.validateStackSpec('webapp')

    then:
    result == null // No validation errors
}
```

#### Error Handling Test
```groovy
def "throws meaningful error for missing compose files"() {
    given:
    extension.composeStacks {
        webapp {
            composeFiles('missing-file.yml', 'another-missing.yml')
        }
    }

    when:
    extension.validateStackSpec('webapp')

    then:
    def exception = thrown(GradleException)
    exception.message.contains('compose file not found')
    exception.message.contains('missing-file.yml')
}
```

#### Backward Compatibility Test
```groovy
def "validates single-file configuration unchanged"() {
    given:
    def composeFile = createTempFile('docker-compose.yml')
    
    extension.composeStacks {
        webapp {
            composeFile = composeFile
        }
    }

    when:
    def result = extension.validateStackSpec('webapp')

    then:
    result == null // Existing validation continues to work
}
```

### 5. Coverage Requirements
- Achieve 100% line coverage for new validation code
- Achieve 100% branch coverage for new validation code
- All tests must pass with `./gradlew clean test`
- Build must succeed with `./gradlew clean build`

### 6. Configuration Cache Compliance Testing
- Test that validation logic works with Provider API correctly
- Verify no `.get()` calls during configuration in validation code
- Test that validation results are serializable
- Verify validation works with configuration cache enabled

## Files to Modify
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtensionTest.groovy`

## Acceptance Criteria
1. **Comprehensive Coverage**: All new validation logic is thoroughly tested
2. **Error Scenarios**: All error cases are tested with meaningful error messages
3. **Backward Compatibility**: Existing single-file validation is verified to still work
4. **Provider API**: Validation logic using Provider API is properly tested
5. **100% Coverage**: Line and branch coverage achieved for new validation code
6. **Build Success**: All tests pass and build succeeds with `./gradlew clean build`
7. **Configuration Cache**: Validation logic is compatible with configuration cache
8. **Test Quality**: Tests follow existing project patterns and are maintainable

## Testing Patterns
Follow these patterns from existing tests:
- Use Spock framework with proper given/when/then structure
- Use `ProjectBuilder` for creating test Gradle projects
- Use `@TempDir` for temporary file creation during tests
- Mock external dependencies appropriately
- Use descriptive test method names that explain the scenario
- Test both positive and negative scenarios thoroughly

## Configuration Cache Testing
Apply configuration cache principles in tests:
- Test that validation code works correctly with Provider API
- Verify no `.get()` calls during configuration in tested code
- Test serialization compatibility of validation logic where applicable
- Verify validation works when configuration cache is enabled