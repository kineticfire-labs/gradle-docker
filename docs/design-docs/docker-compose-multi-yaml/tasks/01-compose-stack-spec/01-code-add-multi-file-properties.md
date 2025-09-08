# Task: Add Multi-File Properties to ComposeStackSpec

## Area: ComposeStackSpec Enhancement

## Type: Code Implementation

## Description
Add new multi-file properties and DSL methods to `ComposeStackSpec.groovy` to support multiple Docker Compose files.

## Context
You are a Principal Software Engineer and expert at Java, Gradle, custom Gradle plugins, Groovy, Docker, and Docker Compose. You must follow the Gradle 9 configuration cache compatibility guidance provided.

## Requirements

### 1. Add New Properties
Add these abstract properties to `ComposeStackSpec.groovy`:
- `abstract ListProperty<String> getComposeFiles()` - For file paths
- `abstract ConfigurableFileCollection getComposeFileCollection()` - For file objects

### 2. Add DSL Methods
Implement these convenience methods:
```groovy
void composeFiles(String... files) {
    composeFiles.set(Arrays.asList(files))
}

void composeFiles(List<String> files) {
    composeFiles.set(files)  
}

void composeFiles(File... files) {
    composeFileCollection.from(files)
}
```

### 3. Maintain Backward Compatibility
- Keep existing `abstract RegularFileProperty getComposeFile()` property unchanged
- Ensure existing single-file configurations continue to work

### 4. Gradle 9 Configuration Cache Compliance
- Use Gradle's Provider API correctly
- Ensure all properties are serializable
- Follow guidance from `@docs/design-docs/gradle-9-configuration-cache-guidance.md`
- Use `Property<T>` and `Provider<T>` for all dynamic values
- Never call `.get()` on providers during configuration

## Files to Modify
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ComposeStackSpec.groovy`

## Acceptance Criteria
1. New multi-file properties are added to `ComposeStackSpec`
2. DSL methods for convenient multi-file configuration are implemented
3. Existing single-file properties remain unchanged for backward compatibility
4. Code follows Gradle 9 configuration cache best practices
5. All properties use appropriate Gradle Provider API types
6. Code compiles without errors

## Testing Note
Do not write tests in this task - tests will be covered in subsequent tasks.

## Configuration Cache Guidance
Reference the following guidance from `@docs/design-docs/gradle-9-configuration-cache-guidance.md`:
- Use `Provider<T>` and `Property<T>` for all dynamic values
- Capture project properties during configuration phase
- Use `.map()`, `.flatMap()`, and `.zip()` for provider transformations
- Never call `.get()` on providers during configuration
- Avoid accessing `task.project` in doFirst/doLast blocks
- Don't use `project.*` references in task actions