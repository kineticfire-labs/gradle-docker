# Fix Publish and Save Tasks for Gradle 9 and 10 Configuration Cache Compatibility

## **Overview**

This document outlines the plan to fix configuration cache compatibility issues in `DockerPublishTask` and `DockerSaveTask` for Gradle 9 and 10. Currently, these tasks require configuration cache to be disabled due to serialization problems with Project references, Task references, and custom property types.

## **Current Problem**

### **Configuration Cache Overview**

Gradle's configuration cache is a performance feature that serializes the task graph and configuration to disk, allowing subsequent builds to skip the configuration phase entirely. However, this requires all tasks and their properties to be **serializable**.

### **The Specific Incompatibility**

The current implementation has serialization problems because:

1. **Capture Project references** - The `Project` object contains the entire build state and cannot be serialized
2. **Capture Task references** - Direct task references create circular dependencies during serialization
3. **Have file property serialization issues in SaveSpec** - Custom property types that don't implement proper serialization

### **Why This Breaks Configuration Cache**

When Gradle tries to serialize the task graph:
```groovy
// ❌ PROBLEMATIC PATTERNS:
class DockerPublishTask extends DefaultTask {
    @Internal
    Project project  // Cannot serialize entire project

    @Internal
    Task dependentTask  // Creates circular references

    @Nested
    SaveSpec saveSpec  // If SaveSpec has non-serializable properties
}
```

## **Current Workaround**

### **Why settings.gradle Was Needed**

A `settings.gradle` file disabling configuration cache was added as a **temporary workaround** because:

1. **Inheritance Issue**: Scenario-2 inherits configuration cache settings from parent projects
2. **Selective Disabling**: Need to disable cache only for problematic scenarios while keeping it enabled elsewhere
3. **Build Isolation**: Prevents configuration cache failures from breaking the entire build

```groovy
// settings.gradle content:
org.gradle.configuration-cache=false
org.gradle.configuration-cache-problems=warn
```

## **Root Cause Analysis**

### **1. Project Reference Capture**
```groovy
// ❌ PROBLEMATIC:
class DockerPublishTask {
    void configurePublish() {
        // Accessing project during execution captures reference
        def targetRegistry = project.findProperty('registry.url')
        project.tasks.named('someTask').configure { ... }
    }
}
```

### **2. Task Reference Capture**
```groovy
// ❌ PROBLEMATIC:
class DockerSaveTask {
    @Internal
    Task buildTask  // Direct task reference

    void performSave() {
        buildTask.outputs.files.forEach { ... }  // Serialization breaks here
    }
}
```

### **3. SaveSpec Serialization Issues**
```groovy
// ❌ PROBLEMATIC:
class SaveSpec {
    @Internal
    FileCollection inputFiles  // May not be properly serializable

    @Internal
    Provider<String> compressionType  // Provider serialization issues
}
```

## **Solution Plan**

### **Phase 1: Audit Existing Tasks**

1. **Identify all problematic patterns** in `DockerPublishTask` and `DockerSaveTask`
2. **Document current Project and Task reference usage**
3. **Analyze SaveSpec and related specification classes**
4. **Create test cases** to verify configuration cache compatibility

### **Phase 2: Refactor Task Implementations**

#### **1. Replace Project References with Injected Services**
```groovy
// ✅ CORRECT APPROACH:
@CacheableTask
abstract class DockerPublishTask extends DefaultTask {

    @Inject
    abstract ObjectFactory getObjectFactory()

    @Inject
    abstract ProviderFactory getProviderFactory()

    // Use injected services instead of project references
    void configurePublish() {
        def registryUrl = getProviderFactory().gradleProperty('registry.url')
    }
}
```

#### **2. Replace Task References with Provider-Based Configuration**
```groovy
// ✅ CORRECT APPROACH:
abstract class DockerSaveTask extends DefaultTask {

    @InputFiles
    abstract ConfigurableFileCollection getInputImages()

    @TaskAction
    void performSave() {
        // Use file collections, not task references
        getInputImages().files.forEach { ... }
    }
}

// Configuration:
tasks.register('dockerSave', DockerSaveTask) {
    // Connect via providers, not direct task references
    inputImages.from(tasks.named('dockerBuild').map { it.outputs.files })
}
```

#### **3. Fix SaveSpec Serialization**
```groovy
// ✅ CORRECT APPROACH:
abstract class SaveSpec {
    @InputFiles
    abstract ConfigurableFileCollection getInputFiles()

    @Input
    abstract Property<CompressionType> getCompressionType()

    // Use Property<T> and Provider<T> instead of raw types
}
```

#### **4. Use @CacheableTask and Proper Annotations**
```groovy
// ✅ CORRECT APPROACH:
@CacheableTask
abstract class DockerPublishTask extends DefaultTask {

    @Input
    abstract Property<String> getImageName()

    @Input
    abstract Property<String> getRegistryUrl()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void publishImage() {
        // All inputs/outputs are properly declared and serializable
    }
}
```

### **Phase 3: Update Plugin Configuration**

1. **Update plugin registration** to use new task implementations
2. **Migrate existing DSL configuration** to use providers
3. **Ensure backward compatibility** where possible
4. **Update task creation and configuration**

### **Phase 4: Testing and Validation**

1. **Add configuration cache tests** to all affected tasks
2. **Test with `./gradlew --configuration-cache`**
3. **Verify all integration test scenarios work**
4. **Performance testing** to ensure cache benefits are realized

### **Phase 5: Cleanup**

1. **Remove temporary settings.gradle workarounds**
2. **Update documentation** to reflect configuration cache compatibility
3. **Add configuration cache to CI/CD validation**

## **Configuration Cache Best Practices**

### **Avoid These Patterns:**
- ❌ `Project` references in task fields
- ❌ `Task` references in task fields
- ❌ Accessing `project.*` during task execution
- ❌ Non-serializable custom types without proper configuration

### **Use These Patterns:**
- ✅ `@Inject` services (`ObjectFactory`, `ProviderFactory`, etc.)
- ✅ `Property<T>` and `Provider<T>` for all inputs
- ✅ `ConfigurableFileCollection` for file inputs
- ✅ Provider chains for task dependencies
- ✅ `@CacheableTask` when appropriate

## **Success Criteria**

1. **All Docker plugin tasks are configuration cache compatible**
2. **No need for settings.gradle workarounds**
3. **Integration tests pass with configuration cache enabled**
4. **Performance improvement from configuration cache usage**
5. **Backward compatibility maintained**

## **Implementation Order**

1. Start with `DockerSaveTask` (simpler, fewer dependencies)
2. Move to `DockerPublishTask` (more complex registry interactions)
3. Fix all related specification classes (`SaveSpec`, `PublishSpec`, etc.)
4. Update plugin configuration and task registration
5. Remove workarounds and validate

## **Testing Strategy**

- Unit tests with configuration cache enabled
- Integration tests across all scenarios
- Performance benchmarks before/after
- Compatibility testing with Gradle 9.0+ and 10.0+

## **References**

- [Gradle Configuration Cache Documentation](https://docs.gradle.org/current/userguide/configuration_cache.html)
- [Task Input/Output Declaration](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:task_input_output_annotations)
- [Provider API Documentation](https://docs.gradle.org/current/userguide/lazy_configuration.html)