# Gradle 9.0.0 Configuration Cache Compatibility Guide

## Overview

Gradle 9.0.0 introduces stricter configuration cache requirements that prevent tasks from accessing the Project object during execution time. This document provides guidance for maintaining compatibility with configuration caching.

## Key Principle

**Configuration vs Execution Separation**: All project-dependent values must be captured during configuration phase and stored in serializable forms (Providers, Properties) for use during execution.

## Common Issues & Solutions

### 1. Provider Resolution During Configuration

**❌ Anti-pattern:**
```groovy
String composeStateFileFor(String stackName) {
    def buildDirProvider = project.layout.buildDirectory
    def stateDirProvider = buildDirProvider.dir("compose-state")
    def stateFile = stateDirProvider.get().asFile.toPath().resolve("${stackName}-state.json").toString()
    return stateFile  // .get() resolves provider during configuration
}
```

**✅ Pattern:**
```groovy
Provider<String> composeStateFileFor(String stackName) {
    def buildDirProvider = project.layout.buildDirectory
    def stateDirProvider = buildDirProvider.dir("compose-state")
    return stateDirProvider.map { dir ->
        dir.asFile.toPath().resolve("${stackName}-state.json").toString()
    }  // Deferred resolution using .map()
}
```

### 2. Project Access in Task Actions

**❌ Anti-pattern:**
```groovy
task.doFirst {
    task.logger.lifecycle("Starting process")  // task.logger may access project
}

task.doLast {
    project.logger.info("Process completed")   // Direct project access
}
```

**✅ Pattern:**
```groovy
// Capture values during configuration
def taskName = task.name
def stackName = "myStack"

// Avoid doFirst/doLast blocks that access project
// Use task dependencies and external logging instead
task.dependsOn 'composeUpMyStack'
task.finalizedBy 'composeDownMyStack'
```

### 3. System Properties with Providers

**❌ Anti-pattern:**
```groovy
systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("stack").get()
```

**✅ Pattern:**
```groovy
systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("stack")
// Gradle's systemProperty method handles Provider<String> automatically
```

## Configuration Cache Best Practices

### Do's
- Use `Provider<T>` and `Property<T>` for all dynamic values
- Capture project properties during configuration phase
- Use `.map()`, `.flatMap()`, and `.zip()` for provider transformations
- Test with `--configuration-cache` flag enabled
- Prefer task dependencies over doFirst/doLast blocks

### Don'ts
- Never call `.get()` on providers during configuration
- Avoid accessing `task.project` in doFirst/doLast blocks
- Don't use `project.*` references in task actions
- Avoid task.logger calls in execution blocks

## Testing Configuration Cache Compatibility

```bash
# Test with configuration cache enabled
./gradlew clean build --configuration-cache

# Verify cache storage
# Look for: "Configuration cache entry stored."

# Test cache reuse
./gradlew clean build --configuration-cache
# Look for: "Reusing configuration cache."
```

## Project-Specific Learnings

### TestIntegrationExtension Issues Found
- **Problem**: `composeStateFileFor` method resolved providers during configuration
- **Solution**: Return `Provider<String>` and use `.map()` for deferred resolution
- **Impact**: Fixed "invocation of 'Task.project' at execution time" error

### Task Action Logging Issues
- **Problem**: `doFirst`/`doLast` blocks with logger calls accessed project during execution
- **Solution**: Removed execution-time logging, rely on compose task logging
- **Impact**: Eliminated project access during task execution

### Test Updates Required
- **Problem**: Unit tests expected `String` return type from `composeStateFileFor`
- **Solution**: Updated tests to call `.get()` on returned provider
- **Pattern**: `extension.composeStateFileFor('stack').get()` in tests

## Resources

- [Gradle Configuration Cache Requirements](https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution)
- [Gradle Provider API Documentation](https://docs.gradle.org/current/javadoc/org/gradle/api/provider/Provider.html)
- [Configuration Cache User Guide](https://docs.gradle.org/9.0.0/userguide/configuration_cache.html)

## Error Messages to Watch For

- `"invocation of 'Task.project' at execution time is unsupported with the configuration cache"`
- `"Build file 'build.gradle': invocation of 'Task.project' at execution time"`
- Configuration cache entries being discarded instead of stored

## Verification Commands

```bash
# Check configuration cache compatibility
./gradlew clean build --configuration-cache

# Generate detailed configuration cache report
./gradlew clean build --configuration-cache --configuration-cache-problems=warn
```