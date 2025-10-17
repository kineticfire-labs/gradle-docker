# Configuration Cache Incompatibility Issues - Plugin Tasks

**Date**: 2025-10-17
**Status**: Discovered, Not Started
**Priority**: High
**Related Work**: Follows completion of Option 3 (RegistryManagementPlugin afterEvaluate refactoring)

## Problem Summary

Integration tests with configuration cache enabled (`org.gradle.configuration-cache=true`) reveal **128 configuration
cache violations** across multiple core plugin tasks. All violations stem from tasks capturing `Project` references,
which violates Gradle 9/10 configuration cache requirements.

### Test Results

```
BUILD FAILED in 36s
40 actionable tasks: 27 executed, 3 from cache, 10 up-to-date
Configuration cache entry stored with 128 problems.

128 problems were found storing the configuration cache.
```

### Affected Components

1. **DockerBuildTask**
   - Immediate failure: `Could not evaluate onlyIf predicate for task ':docker:scenario-1:dockerBuildTimeServer'`
   - Error: `Could not evaluate spec for 'Task satisfies onlyIf closure'`

2. **DockerSaveTask** (32+ violations)
   - Error: `cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject'`
   - Error: `cannot deserialize object of type 'org.gradle.api.Project'`
   - Affected scenarios: 10, 11, 12

3. **DockerTagTask** (32+ violations)
   - Error: `cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject'`
   - Error: `cannot deserialize object of type 'org.gradle.api.Project'`
   - Affected scenarios: 10, 11, 12

4. **DockerPublishTask** (32+ violations)
   - Error: `cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject'`
   - Error: `cannot deserialize object of type 'org.gradle.api.Project'`
   - Affected scenarios: 11, 12

### Root Cause

Per Gradle 9/10 configuration cache requirements (see `docs/design-docs/gradle-9-and-10-compatibility.md`):

> **No `Project` at execution time**
> - Inside `@TaskAction`, never call `task.project` or any `Project` APIs. Inject services instead.

Tasks are capturing Project references during configuration, which cannot be serialized for the configuration cache.
This violates the fundamental requirement that tasks must be fully serializable.

## Detailed Problem Analysis

### Configuration Cache Violation Pattern

All violations follow the same pattern:

```
- Task `:<path>:<task-name>` of type `<TaskClass>`:
  cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject',
  a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.
```

This indicates:
1. Tasks are storing Project references as fields
2. Project references are being captured in closures
3. Task configuration is not using Provider API properly

### Why This Was Hidden Previously

The `plugin-integration-test/gradle.properties` had:
```properties
org.gradle.configuration-cache=false
```

This disabled configuration cache validation, masking these architectural issues. The setting was intentionally disabled
with the comment:
```
# Configuration cache compatibility status:
# - Scenario 1: Compatible (no contextTask usage)
# - Scenarios 2-5: Disabled - awaiting Part 2 architectural refactoring
```

After completing the RegistryManagementPlugin refactoring (Option 3), we enabled configuration cache globally, which
immediately exposed these pre-existing violations.

## Investigation Approach (Option 2)

### Phase 1: Identify Project Reference Capture Points

For each affected task class, systematically search for:

1. **Direct Project Field Storage**
   ```groovy
   class MyTask extends DefaultTask {
       private Project project  // ❌ NOT ALLOWED
   ```

2. **Project in Closures**
   ```groovy
   onlyIf {
       project.hasProperty('foo')  // ❌ NOT ALLOWED
   }
   ```

3. **Project Access in @TaskAction**
   ```groovy
   @TaskAction
   void execute() {
       def proj = project  // ❌ NOT ALLOWED
   }
   ```

4. **Indirect Capture via Properties**
   ```groovy
   @Internal
   Property<Project> projectProp  // ❌ NOT ALLOWED
   ```

#### Investigation Commands

```bash
# Search for Project usage in task classes
cd plugin/src/main/groovy/com/kineticfire/gradle/docker/task

# Look for direct Project references
rg "Project project" *.groovy
rg "\.project\b" *.groovy

# Look for Project in closures (onlyIf, doFirst, doLast)
rg -A5 "onlyIf" *.groovy | grep -i project
rg -A5 "doFirst" *.groovy | grep -i project
rg -A5 "doLast" *.groovy | grep -i project

# Check for @Internal Project properties
rg "@Internal.*Project" *.groovy
```

### Phase 2: Analyze Plugin Wiring Code

Check how tasks are configured in `GradleDockerPlugin.groovy`:

1. **Task Registration**
   - Are tasks registered with `tasks.register()` (lazy) or `tasks.create()` (eager)?
   - Is task configuration done in registration block or afterwards?

2. **Property Wiring**
   - Are Project properties being passed directly to tasks?
   - Are closures capturing Project being used?

3. **onlyIf Predicates**
   - Where are onlyIf predicates configured?
   - Do they reference Project?

#### Investigation Commands

```bash
cd plugin/src/main/groovy/com/kineticfire/gradle/docker

# Find task registration sites
rg "tasks\.register|tasks\.create" GradleDockerPlugin.groovy

# Find onlyIf usage in plugin
rg "onlyIf" GradleDockerPlugin.groovy -A3

# Find property wiring that might capture Project
rg "\.set\(.*project\." GradleDockerPlugin.groovy
```

### Phase 3: Check Spec Classes

Review `ImageSpec`, `SaveSpec`, `PublishSpec` for Project capture:

```bash
cd plugin/src/main/groovy/com/kineticfire/gradle/docker/spec

# Check for Project fields
rg "private.*Project|Project project" *.groovy

# Check for Project in constructors
rg "@Inject.*Project" *.groovy
```

## Fix Approach

### General Strategy

Follow Gradle 9/10 guidelines from `docs/design-docs/gradle-9-and-10-compatibility.md`:

1. **Replace Project with Injected Services**
   ```groovy
   // Instead of:
   project.layout.buildDirectory

   // Use:
   @javax.inject.Inject abstract ProjectLayout getLayout()
   layout.buildDirectory
   ```

2. **Replace Project-Based Properties with Providers**
   ```groovy
   // Instead of:
   String value = project.property('foo')

   // Use:
   @Input abstract Property<String> getValue()
   value.set(providers.gradleProperty('foo'))
   ```

3. **Remove Project from onlyIf Predicates**
   ```groovy
   // Instead of:
   onlyIf {
       project.hasProperty('foo')
   }

   // Use input properties:
   @Input @Optional abstract Property<Boolean> getShouldRun()

   onlyIf {
       shouldRun.getOrElse(true)
   }
   ```

### Task-Specific Fixes

#### 1. DockerBuildTask

**Likely Issues:**
- onlyIf predicate accessing Project
- Context directory resolution using Project
- Dockerfile resolution using Project

**Fix Approach:**
1. Identify the onlyIf closure in the task or plugin
2. Convert Project-based checks to Provider-based inputs
3. Use injected `ProjectLayout` for file resolution
4. Use `@Input` properties instead of runtime Project access

**Example Fix:**
```groovy
abstract class DockerBuildTask extends DefaultTask {

    // Inject services, not Project
    @javax.inject.Inject abstract ProjectLayout getLayout()
    @javax.inject.Inject abstract ProviderFactory getProviders()

    // Use providers for conditional logic
    @Input @Optional
    abstract Property<Boolean> getShouldBuild()

    // Use layout for file resolution
    @InputDirectory @Optional
    abstract DirectoryProperty getContextDirectory()

    @TaskAction
    void build() {
        // No Project access here
        def context = contextDirectory.get()
        // ... rest of implementation
    }
}
```

**Plugin Wiring:**
```groovy
tasks.register('dockerBuild', DockerBuildTask) {
    shouldBuild.convention(providers.gradleProperty('docker.build.enabled').map { it.toBoolean() }.orElse(true))
    contextDirectory.convention(layout.projectDirectory.dir('src/main/docker'))
}
```

#### 2. DockerSaveTask

**Likely Issues:**
- Output file path using `project.buildDir`
- Image reference resolution using Project properties
- Compression settings from Project properties

**Fix Approach:**
1. Replace `project.layout` with injected `ProjectLayout`
2. Convert all Project property reads to `ProviderFactory.gradleProperty()`
3. Ensure all properties are Provider-based

**Example Fix:**
```groovy
abstract class DockerSaveTask extends DefaultTask {

    @javax.inject.Inject abstract ProjectLayout getLayout()
    @javax.inject.Inject abstract ProviderFactory getProviders()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    // Already fixed in our previous work - ensure no new violations
}
```

#### 3. DockerTagTask

**Likely Issues:**
- Image name resolution using Project
- Tag values from Project properties
- Registry configuration from Project

**Fix Approach:**
1. Use injected `ProviderFactory` for property reads
2. Ensure all image/tag values are Provider-based inputs
3. Remove any Project references in tagging logic

**Example Fix:**
```groovy
abstract class DockerTagTask extends DefaultTask {

    @javax.inject.Inject abstract ProviderFactory getProviders()
    @javax.inject.Inject abstract DockerService getDockerService()

    @Input abstract Property<String> getSourceImage()
    @Input abstract ListProperty<String> getTargetTags()

    @TaskAction
    void tag() {
        // No Project access - only use injected services and input properties
        def source = sourceImage.get()
        def targets = targetTags.get()
        // ... rest of implementation
    }
}
```

#### 4. DockerPublishTask

**Likely Issues:**
- Registry URL from Project properties
- Authentication from Project properties
- Push logic accessing Project state

**Fix Approach:**
1. Use injected services for Docker operations
2. Convert registry config to nested `@Nested` spec objects
3. Use Provider API for all authentication data

**Example Fix:**
```groovy
abstract class DockerPublishTask extends DefaultTask {

    @javax.inject.Inject abstract ProviderFactory getProviders()
    @javax.inject.Inject abstract DockerService getDockerService()

    @Input abstract Property<String> getRegistry()
    @Input abstract Property<String> getImageName()

    @Nested @Optional
    abstract Property<AuthSpec> getAuth()

    @TaskAction
    void publish() {
        // No Project access
        def reg = registry.get()
        def img = imageName.get()
        def authConfig = auth.isPresent() ? auth.get() : null
        // ... rest of implementation
    }
}
```

### Plugin Wiring Fixes

In `GradleDockerPlugin.groovy`, ensure:

1. **Task Registration is Lazy**
   ```groovy
   // Use tasks.register, not tasks.create
   tasks.register('myTask', MyTaskClass) {
       // Configure here
   }
   ```

2. **No Project Passed to Tasks**
   ```groovy
   // Instead of:
   myTask.projectRef.set(project)

   // Use providers:
   myTask.propertyValue.set(providers.gradleProperty('foo'))
   ```

3. **onlyIf Configured with Providers**
   ```groovy
   // Instead of:
   myTask.configure {
       onlyIf { project.hasProperty('foo') }
   }

   // Use:
   myTask.configure {
       shouldRun.set(providers.gradleProperty('foo').map { it.toBoolean() }.orElse(false))
       onlyIf { shouldRun.get() }
   }
   ```

## Testing Strategy

### Unit Tests

1. **Verify No Project References**
   - Add tests that task instances are serializable
   - Mock all injected services
   - Verify tasks can execute without Project

2. **Test Task Actions in Isolation**
   ```groovy
   def "task action does not access Project"() {
       given:
       def task = project.tasks.create('test', MyTask)
       task.inputProp.set('value')

       when:
       task.actions.each { it.execute(task) }

       then:
       noExceptionThrown()
       // Verify task didn't call project.* methods
   }
   ```

### Integration Tests

1. **Configuration Cache Validation**
   ```bash
   # Run with config cache (first time - stores)
   ./gradlew <task> --configuration-cache

   # Run again (should reuse)
   ./gradlew <task> --configuration-cache

   # Verify reuse message
   ```

2. **Incremental Testing**
   - Fix one task at a time
   - Run integration tests after each fix
   - Verify violation count decreases
   - Target: 0 violations

3. **Full Test Suite**
   ```bash
   cd plugin-integration-test
   ./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest
   ```

## Implementation Plan

### Step 1: DockerBuildTask (Highest Priority)

This task causes immediate build failure, so fix it first.

1. Search for onlyIf predicates
2. Search for Project references in task class
3. Search for Project references in plugin wiring
4. Refactor to use Provider API
5. Test with scenario-1
6. Verify configuration cache stores without errors

**Acceptance Criteria:**
- `./gradlew :docker:scenario-1:dockerBuildTimeServer --configuration-cache` succeeds
- No "cannot serialize Project" errors for DockerBuildTask
- Unit tests pass
- Integration test scenario-1 passes

### Step 2: DockerSaveTask

1. Review existing code from recent SaveSpec fix
2. Find any remaining Project references
3. Refactor to use injected services
4. Test with scenarios 10, 11, 12

**Acceptance Criteria:**
- No "cannot serialize Project" errors for DockerSaveTask
- Scenarios 10, 11, 12 save operations succeed
- Unit tests pass

### Step 3: DockerTagTask

1. Search for Project usage in tagging logic
2. Convert to Provider-based inputs
3. Test with scenarios 10, 11, 12

**Acceptance Criteria:**
- No "cannot serialize Project" errors for DockerTagTask
- Scenarios 10, 11, 12 tag operations succeed
- Unit tests pass

### Step 4: DockerPublishTask

1. Search for Project usage in publish logic
2. Convert registry/auth to Provider-based
3. Test with scenarios 11, 12

**Acceptance Criteria:**
- No "cannot serialize Project" errors for DockerPublishTask
- Scenarios 11, 12 publish operations succeed
- Unit tests pass

### Step 5: Full Verification

1. Run complete integration test suite
2. Verify 0 configuration cache violations
3. Verify configuration cache reuse on second run
4. Clean up any leftover containers

**Acceptance Criteria:**
- `./gradlew cleanAll integrationTest --configuration-cache` succeeds
- Second run shows "Reusing configuration cache"
- Zero violations reported
- All integration tests pass
- `docker ps -a` shows no leftover containers

## Verification Commands

### Check for Violations

```bash
# Run integration tests with config cache
cd plugin-integration-test
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest --configuration-cache 2>&1 | tee /tmp/config-cache-test.log

# Count violations
rg "problems were found storing the configuration cache" /tmp/config-cache-test.log

# View full report
# Check the HTML report URL in the output
```

### Verify Cache Reuse

```bash
# First run (stores cache)
./gradlew clean build --configuration-cache

# Second run (should reuse)
./gradlew clean build --configuration-cache

# Look for:
# "Reusing configuration cache"
```

### Check Task Serializability

```bash
# Look for serialization errors
rg "cannot serialize|cannot deserialize" /tmp/config-cache-test.log
```

## Success Criteria

- [ ] Zero configuration cache violations reported
- [ ] All 4 task classes (Build, Save, Tag, Publish) are configuration cache compatible
- [ ] Plugin unit tests pass with configuration cache enabled
- [ ] Integration tests pass with configuration cache enabled
- [ ] Configuration cache reuse works on second run
- [ ] No leftover Docker containers after tests
- [ ] Documentation updated to reflect configuration cache compatibility

## Estimated Effort

**Investigation**: 2-3 hours
**DockerBuildTask Fix**: 2-4 hours
**DockerSaveTask Fix**: 1-2 hours
**DockerTagTask Fix**: 1-2 hours
**DockerPublishTask Fix**: 1-2 hours
**Testing & Verification**: 2-3 hours
**Total**: 9-16 hours

## Dependencies

- Gradle 9.0.0 configuration cache features
- Docker Java Client API
- Existing task implementations
- Integration test infrastructure

## References

- `docs/design-docs/gradle-9-and-10-compatibility.md` - Gradle 9/10 best practices
- Gradle Documentation: [Configuration Cache Requirements](https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html)
- Gradle Documentation: [Lazy Configuration](https://docs.gradle.org/current/userguide/lazy_configuration.html)
- Build output: `/home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/`

## Notes

- This issue was discovered after completing Option 3 (RegistryManagementPlugin refactoring)
- The SaveSpec @OutputFile issue has already been fixed as part of discovery
- Configuration cache was intentionally disabled before due to known issues
- These are pre-existing architectural issues, not regressions from recent work
- Fixing these issues will make the plugin fully Gradle 9/10 compatible
