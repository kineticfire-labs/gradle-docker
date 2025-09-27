# Gradle 9 and 10 Compatibility

This plugin is fully compatible with Gradle 9 and 10, including configuration cache support. Follow these patterns for
best compatibility:

## Configuration Cache Best Practices

**✅ Recommended Patterns:**
```groovy
// Use providers for dynamic values
version.set(providers.provider { project.version.toString() })
buildArgs.put("VERSION", providers.provider { project.version.toString() })

// Capture values during configuration for closures
contextTask = tasks.register('prepareContext', Copy) {
    def versionString = project.version.toString()  // Capture during configuration
    from(file('libs')) {
        rename { "app-${versionString}.jar" }  // Use captured value
    }
}
```

**❌ Avoid These Patterns:**
```groovy
// Direct project property access in providers (configuration cache violations)
buildArgs.put("VERSION", project.version)  // Avoid
labels.put("version", project.version)     // Avoid

// Accessing providers in execution-time closures
rename { "app-${providers.provider { project.version }.get()}.jar" }  // Avoid
```

## Provider API Requirements

- **All dynamic values** must use `providers.provider { }` or be captured during configuration
- **Environment variables** must use `providers.environmentVariable("VAR_NAME")`
- **File properties** must use `layout.buildDirectory.file()` or `layout.projectDirectory.file()`
- **String literals** can be set directly without providers

## Configuration Cache Status

- **scenario-1 (build only)**: ✅ Full configuration cache support
- **scenario-2 (build + save + publish)**: ⚠️ Limited support due to plugin task serialization issues

For scenarios using save/publish operations, configuration cache may need to be disabled until plugin task serialization
issues are resolved.

## Provider API Properties
All properties use Gradle's Provider API for configuration cache compatibility:
- `.set(value)` - Set property value
- `.convention(defaultValue)` - Set default value
- `.get()` - Get property value (only in task actions)

**Gradle 9/10 Compatibility Notes:**
- Use `providers.provider { }` for dynamic values like `project.version`
- Use `providers.environmentVariable("VAR")` for environment variables
- Capture dynamic values during configuration phase for use in closures
- Avoid accessing `project.*` properties directly in provider blocks