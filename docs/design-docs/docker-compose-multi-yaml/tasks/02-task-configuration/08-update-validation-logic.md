# Task: Update Validation Logic for Multi-File Support

## Area: Task Configuration Logic

## Type: Code Implementation

## Description
Update the validation logic in `DockerOrchExtension.validateStackSpec()` and related methods to properly validate multi-file compose stack configurations.

## Context
You are a Principal Software Engineer and expert at Java, Gradle, custom Gradle plugins, Groovy, Docker, and Docker Compose. You must follow the Gradle 9 configuration cache compatibility guidance provided.

## Requirements

### 1. Update Stack Validation
Update `DockerOrchExtension.validateStackSpec()` to handle:
- Validation of multi-file configurations (`composeFiles` and `composeFileCollection`)
- Ensure at least one compose file is specified (single-file OR multi-file)
- Validate file existence for all specified files
- **Validate ComposeDown will use same files as ComposeUp** (no separate validation needed)
- Maintain backward compatibility with single-file validation

### 2. Validation Logic Priorities
Implement validation that respects configuration priorities:
```groovy
// Pseudo-code for validation logic
if (stackSpec.composeFiles.present || stackSpec.composeFileCollection.present) {
    // Validate multi-file configuration
    validateMultiFileConfiguration(stackSpec)
} else if (stackSpec.composeFile.present) {
    // Validate single-file configuration (existing logic)
    validateSingleFileConfiguration(stackSpec)
} else {
    // Error: no compose files specified
    throw new GradleException("Stack '${stackSpec.name}' must specify either composeFile or composeFiles")
}
```

### 3. Multi-File Validation Requirements
- **At least one file**: Ensure at least one compose file is specified
- **File existence**: Validate that all specified compose files exist
- **File accessibility**: Verify files are readable
- **Order preservation**: Maintain and validate file order is preserved
- **Path resolution**: Handle both absolute and relative file paths correctly

### 4. Error Messages
Provide clear, actionable error messages:
- "Stack 'webapp' has no compose files specified. Use either 'composeFile' or 'composeFiles'."
- "Stack 'webapp' compose file not found: /path/to/missing-file.yml"
- "Stack 'webapp' compose files contain duplicates: file1.yml appears multiple times"
- "Stack 'webapp' composeFiles list is empty. Specify at least one compose file."
- **Note**: No separate ComposeDown validation messages needed since it uses same files as ComposeUp

### 5. Configuration Cache Compliance
Follow `@docs/design-docs/gradle-9-configuration-cache-guidance.md`:
- Use Provider API correctly for all file validation
- No `.get()` calls on providers during configuration phase
- Use `.map()` and `.flatMap()` for provider transformations when validating
- Ensure validation logic is serializable

### 6. Integration Points
- Maintain integration with existing extension validation
- Ensure validation works with task configuration logic for both ComposeUp and ComposeDown
- Keep compatibility with current error handling patterns
- Support validation during both configuration and execution phases if needed
- **Ensure validation understands ComposeDown inherits ComposeUp file configuration**

## Files to Modify
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtension.groovy`
- Any related validation helper classes or methods

## Investigation Required
1. **Find validation methods**: Locate `validateStackSpec()` method and related validation logic
2. **Current patterns**: Understand existing validation patterns and error handling
3. **Provider usage**: Determine how current validation works with Provider API
4. **Error handling**: Understand existing error message patterns and conventions

## Acceptance Criteria
1. **Multi-file validation**: Multi-file configurations are properly validated
2. **Backward compatibility**: Single-file validation continues to work unchanged
3. **Clear errors**: Error messages are clear and actionable for users
4. **File validation**: All specified files are validated for existence and accessibility
5. **Configuration cache**: Validation logic is compatible with Gradle 9 configuration cache
6. **Priority logic**: Validation respects single-file vs multi-file priority correctly
7. **Integration**: Validation integrates properly with existing extension logic
8. **ComposeDown integration**: Validation understands ComposeDown inherits ComposeUp files automatically

## Testing Note
Do not write tests in this task - tests will be covered in subsequent unit test tasks.

## Configuration Cache Guidance
Key principles from `@docs/design-docs/gradle-9-configuration-cache-guidance.md`:
- Use `Provider<T>` and `Property<T>` for all validation logic that involves dynamic values
- Never call `.get()` on providers during configuration phase
- Use provider transformations for validation: `.map()`, `.flatMap()`, `.zip()`
- Ensure validation results are serializable for configuration cache
- Capture all necessary validation data during configuration phase

## Status
- **Status**: done
- **Date**: 2025-09-08
- **Implementation Summary**: Updated `DockerOrchExtension.validateStackSpec()` with multi-file validation logic that respects priority (multi-file over single-file configuration), implements all required error messages, validates file existence/accessibility, detects duplicate files, and maintains configuration cache compliance using provider transformations instead of `.get()` calls during configuration phase.
- **Key Changes**:
  - Restructured validation with priority logic: `composeFiles`/`composeFileCollection` takes precedence over `composeFile`
  - Added separate validation methods for multi-file vs single-file configurations
  - Implemented duplicate file detection with clear error messages
  - All validation now uses provider transformations (`.map()`, `.getOrNull()`) for configuration cache compliance
  - Updated error messages to match task specifications exactly
  - Maintains backward compatibility with existing single-file validation
- **Residual Gaps**: None - all acceptance criteria met
- **Testing Note**: Main code compilation and configuration cache compatibility verified; comprehensive unit testing deferred to subsequent test tasks as specified