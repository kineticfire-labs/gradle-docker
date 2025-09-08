# Task: Update Task Configuration Logic for Multi-File Support

## Area: Task Configuration Logic

## Type: Code Implementation

## Description
Update the plugin configuration logic to handle multi-file compose stacks, configuring `ComposeUpTask.composeFiles` from `ComposeStackSpec` multi-file properties with backward compatibility.

## Context
You are a Principal Software Engineer and expert at Java, Gradle, custom Gradle plugins, Groovy, Docker, and Docker Compose. You must follow the Gradle 9 configuration cache compatibility guidance provided.

## Requirements

### 1. Locate Task Configuration Logic
Find where `ComposeUpTask` instances are created and configured in:
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtension.groovy` (validation logic)
- Main plugin file where tasks are registered and configured
- Any task factory or builder classes

### 2. Update Task Configuration

#### Configure ComposeUpTask.composeFiles
Update task configuration to:
- Read from `ComposeStackSpec.composeFiles` (new multi-file property)
- Read from `ComposeStackSpec.composeFileCollection` (new file collection property)  
- Handle backward compatibility with `ComposeStackSpec.composeFile` (existing single-file)
- Convert file paths to File objects for validation

#### Backward Compatibility Logic
Implement priority logic:
```groovy
// Pseudo-code for task configuration
if (stackSpec.composeFiles.present || stackSpec.composeFileCollection.present) {
    // Use new multi-file configuration
    task.composeFiles.from(stackSpec.composeFileCollection)
    // Convert string paths to files if needed
    stackSpec.composeFiles.orElse([]).each { path ->
        task.composeFiles.from(project.file(path))
    }
} else if (stackSpec.composeFile.present) {
    // Use legacy single-file configuration  
    task.composeFiles.from(stackSpec.composeFile)
} else {
    // Error: no compose files specified
    throw new GradleException("No compose files specified for stack '${stackSpec.name}'")
}
```

### 3. File Validation
Add validation logic:
- Ensure at least one compose file is specified
- Validate that specified files exist
- Provide clear error messages for missing files
- Maintain file order preservation (important for Docker Compose precedence)

### 4. Configuration Cache Compliance
Follow `@docs/design-docs/gradle-9-configuration-cache-guidance.md`:
- Use Provider API correctly for all configuration
- No `.get()` calls on providers during configuration phase
- Store all values in serializable forms
- Use `.map()`, `.flatMap()` for provider transformations
- Capture project properties during configuration phase

### 5. Integration Points
Ensure integration with:
- Existing `ComposeDownTask` if it needs similar updates
- Validation logic in `DockerOrchExtension.validateStackSpec()`
- Task naming and dependency configuration
- Any existing task configuration patterns

## Files to Modify
Likely files (investigate to confirm):
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtension.groovy`
- Main plugin class (find where tasks are registered)
- Any task factory or configuration helper classes

## Investigation Required
1. Find where `ComposeUpTask` instances are created and configured
2. Identify current task configuration patterns
3. Locate validation logic for compose stack specifications
4. Understand existing file handling and conversion logic

## Acceptance Criteria
1. Task configuration logic handles both single-file and multi-file configurations
2. Backward compatibility is maintained - existing configurations work unchanged
3. File validation is implemented with clear error messages
4. File order is preserved for Docker Compose precedence
5. Configuration is compatible with Gradle 9 configuration cache
6. Code follows existing project patterns and conventions
7. Integration with existing validation logic is maintained

## Configuration Cache Guidance
Key principles from `@docs/design-docs/gradle-9-configuration-cache-guidance.md`:
- Use `Provider<T>` and `Property<T>` for all dynamic values
- Never call `.get()` on providers during configuration
- Use provider transformations: `.map()`, `.flatMap()`, `.zip()`
- Capture project properties during configuration phase
- Ensure all values are serializable for configuration cache

## Testing Note
Do not write tests in this task - tests will be covered in subsequent tasks.