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

#### Configure ComposeDownTask.composeFiles (Critical UX Enhancement)
**Key Requirement**: ComposeDown must use the same files as ComposeUp for proper service teardown:
- **Automatically configure `ComposeDownTask.composeFiles` with same files as `ComposeUpTask`**
- Maintain same file order for proper Docker Compose precedence during teardown
- Allow optional override if user specifies different ComposeDown files
- Ensure ComposeDown can properly tear down services created by multi-file ComposeUp

#### Task Configuration Priority Logic
Implement configuration logic for both ComposeUp and ComposeDown:
```groovy
// Pseudo-code for ComposeUp task configuration
def composeFiles = configureComposeFiles(stackSpec)
composeUpTask.composeFiles.from(composeFiles)

// Pseudo-code for ComposeDown task configuration  
// ComposeDown automatically uses same files as ComposeUp for proper teardown
composeDownTask.composeFiles.from(composeFiles)

def configureComposeFiles(stackSpec) {
    if (stackSpec.composeFiles.present || stackSpec.composeFileCollection.present) {
        // Use new multi-file configuration
        def files = []
        files.addAll(stackSpec.composeFileCollection)
        stackSpec.composeFiles.orElse([]).each { path ->
            files.add(project.file(path))
        }
        return files
    } else if (stackSpec.composeFile.present) {
        // Use legacy single-file configuration  
        return [stackSpec.composeFile.get()]
    } else {
        // Error: no compose files specified
        throw new GradleException("No compose files specified for stack '${stackSpec.name}'")
    }
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
- **ComposeDownTask**: Configure `ComposeDownTask` to automatically use same files as `ComposeUpTask`
- **Validation logic**: Update `DockerOrchExtension.validateStackSpec()` to validate multi-file configurations
- **Task naming**: Ensure task naming and dependency configuration work with multi-file stacks
- **Task dependencies**: Maintain proper ComposeUp/ComposeDown task dependencies
- **Existing patterns**: Follow existing task configuration patterns in the plugin

## Files to Modify
Likely files (investigate to confirm):
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtension.groovy`
- Main plugin class (find where tasks are registered)
- Any task factory or configuration helper classes

## Investigation Required
1. **Find task creation**: Locate where `ComposeUpTask` and `ComposeDownTask` instances are created and configured
2. **Task configuration patterns**: Identify current task configuration patterns for both Up and Down tasks
3. **Validation logic**: Locate validation logic for compose stack specifications in `DockerOrchExtension`
4. **File handling**: Understand existing file handling and conversion logic
5. **ComposeDownTask capabilities**: Verify `ComposeDownTask` has `composeFiles` property like `ComposeUpTask`
6. **Task relationships**: Understand current relationship and dependencies between Up and Down tasks

## Acceptance Criteria
1. **Multi-file support**: Both ComposeUp and ComposeDown tasks handle single-file and multi-file configurations
2. **Automatic ComposeDown**: ComposeDown automatically uses same files as ComposeUp for proper teardown
3. **Backward compatibility**: Existing single-file configurations work unchanged
4. **File validation**: File validation implemented with clear error messages
5. **File order preservation**: File order preserved for Docker Compose precedence in both Up and Down
6. **Configuration cache**: Compatible with Gradle 9 configuration cache requirements
7. **Project conventions**: Code follows existing project patterns and conventions
8. **Validation integration**: Integration with existing validation logic is maintained
9. **UX enhancement**: Seamless user experience - no need to specify ComposeDown files separately

## Configuration Cache Guidance
Key principles from `@docs/design-docs/gradle-9-configuration-cache-guidance.md`:
- Use `Provider<T>` and `Property<T>` for all dynamic values
- Never call `.get()` on providers during configuration
- Use provider transformations: `.map()`, `.flatMap()`, `.zip()`
- Capture project properties during configuration phase
- Ensure all values are serializable for configuration cache

## Testing Note
Do not write tests in this task - tests will be covered in subsequent tasks.