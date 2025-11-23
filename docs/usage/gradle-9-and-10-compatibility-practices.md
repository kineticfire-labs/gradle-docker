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

## afterEvaluate: When It's Acceptable

### General Rule
**Avoid afterEvaluate; prefer provider-based lazy wiring.**

The Provider API eliminates most needs for `afterEvaluate` through lazy evaluation and task providers.

### Exception: Cross-Project Task Dependencies
**afterEvaluate is acceptable for wiring task dependencies across projects** when the dependency cannot be expressed
through Provider API alone.

#### ✅ Acceptable Pattern: Task Dependency Wiring

```groovy
afterEvaluate {
    tasks.named('composeUpMyTest') {
        dependsOn tasks.named('dockerBuildMyApp')
    }
}
```

**Why this is safe:**
- Uses `tasks.named()` which returns `TaskProvider` (configuration-cache safe)
- Only wires task dependency graph, doesn't mutate configuration
- No `Project` references captured in closures
- No external state read or cached

**When to use:**
- Image must be built before compose up can start containers
- Cross-project dependencies where plugin cannot auto-wire
- Task exists in different project than the one declaring dependency

#### ❌ Unacceptable Patterns

**1. Creating tasks in afterEvaluate:**
```groovy
afterEvaluate {
    tasks.create('newTask', Copy) {  // ❌ BAD
        // ...
    }
}
```
**Why:** Use `tasks.register()` instead, which is lazy and doesn't need afterEvaluate.

---

**2. Mutating configuration in afterEvaluate:**
```groovy
afterEvaluate {
    docker.images.myApp.imageName = project.version  // ❌ BAD
}
```
**Why:** Use Provider API with convention/set pattern instead.

---

**3. Reading external state in afterEvaluate:**
```groovy
afterEvaluate {
    def port = System.getenv('PORT')  // ❌ BAD
    systemProperty 'test.port', port
}
```
**Why:** Use `providers.environmentVariable()` for lazy, tracked evaluation.

---

**4. Configuring other tasks from task action:**
```groovy
tasks.register('myTask') {
    doLast {
        tasks.named('otherTask').configure {  // ❌ BAD
            enabled = false
        }
    }
}
```
**Why:** Task configuration must be done during configuration phase, not execution phase.

---

### Alternative Patterns (Preferred Over afterEvaluate)

#### Pattern 1: Provider-Based Defaults
```groovy
// Instead of afterEvaluate for defaults
docker {
    images {
        myApp {
            // Use convention (provider-based default)
            version.convention(providers.provider { project.version.toString() })
        }
    }
}
```

#### Pattern 2: Lazy Task Configuration
```groovy
// Instead of afterEvaluate for task configuration
tasks.named('dockerBuild') {
    // Configuration block is already evaluated lazily by Gradle
    onlyIf { task -> !task.sourceRef.isPresent() }
}
```

#### Pattern 3: Task Provider Dependencies
```groovy
// Instead of afterEvaluate for same-project dependencies
def buildTask = tasks.named('dockerBuild')
def saveTask = tasks.named('dockerSave')

saveTask.configure {
    dependsOn buildTask  // Direct provider dependency, no afterEvaluate needed
}
```

---

### Decision Flow: Do I Need afterEvaluate?

```
Need to wire task dependencies?
  ↓
  Same project?
    ↓ Yes → Use direct TaskProvider dependency (no afterEvaluate)
    ↓ No → Cross-project?
      ↓ Yes → Use afterEvaluate with tasks.named() (acceptable)

Need to set configuration values?
  ↓
  Use Provider API with .set() or .convention() (no afterEvaluate)

Need to create tasks?
  ↓
  Use tasks.register() instead (no afterEvaluate)

Need to read external state?
  ↓
  Use providers.environmentVariable() or ValueSource (no afterEvaluate)
```

---

### Summary

| Scenario | Use afterEvaluate? | Alternative |
|----------|-------------------|-------------|
| Cross-project task dependencies | ✅ Yes | None (this is the right pattern) |
| Same-project task dependencies | ❌ No | Direct TaskProvider dependency |
| Setting configuration defaults | ❌ No | `.convention()` with Provider |
| Creating tasks | ❌ No | `tasks.register()` |
| Reading environment variables | ❌ No | `providers.environmentVariable()` |
| Reading project properties | ❌ No | `providers.provider { }` |
| Mutating configuration | ❌ No | Set values during configuration normally |

**Bottom line:** The only acceptable use of `afterEvaluate` in modern Gradle is cross-project task dependency wiring
with `tasks.named()`.