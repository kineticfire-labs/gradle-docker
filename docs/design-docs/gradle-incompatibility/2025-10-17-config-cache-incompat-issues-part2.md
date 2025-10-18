# Configuration Cache Incompatibility Issues - Part 2: Remove ImageSpec from Tasks

**Date**: 2025-10-17
**Status**: In Progress - Part 1 Completed
**Priority**: Critical - Blocking integration tests
**Related Work**: Continuation of 2025-10-17-config-cache-incompat-issues.md

## Part 1 Completion Summary

### ✅ Completed Work:
1. **Removed Project field from ImageSpec** (src/main/groovy/com/kineticfire/gradle/docker/spec/ImageSpec.groovy)
   - Changed constructor from `ImageSpec(String name, Project project)`
   - To: `ImageSpec(String name, ObjectFactory objectFactory, ProviderFactory providers, ProjectLayout layout)`
   - Removed `private final Project project` field
   - Deprecated `context()` DSL methods that created tasks

2. **Removed Project from all Spec classes**
   - SaveSpec: Changed from `SaveSpec(Project project)` to `SaveSpec(ObjectFactory objectFactory, ProjectLayout layout)`
   - PublishSpec: Changed from `PublishSpec(ObjectFactory objectFactory)` (already had no Project)
   - LogsSpec: Changed from `LogsSpec(Project project)` to `LogsSpec()`
   - WaitSpec: Changed from `WaitSpec(Project project)` to `WaitSpec()`
   - ComposeStackSpec: Changed from `ComposeStackSpec(String name, Project project)` to `ComposeStackSpec(String name, ObjectFactory objectFactory)`
   - AuthSpec: No changes needed (already had no Project)

3. **Removed Project from all Extension classes**
   - DockerExtension: Changed from `DockerExtension(ObjectFactory, ProviderFactory, ProjectLayout, Project)`
     to `DockerExtension(ObjectFactory, ProviderFactory, ProjectLayout)`
   - DockerOrchExtension: Changed from `DockerOrchExtension(ObjectFactory, Project)` to `DockerOrchExtension(ObjectFactory)`
   - TestIntegrationExtension: Made abstract, changed from `TestIntegrationExtension(Project)`
     to `TestIntegrationExtension(ProjectLayout, ProviderFactory)`

4. **Updated GradleDockerPlugin constructor calls**
   - All extension creation calls updated to not pass Project
   - Task wiring updated to use injected services

5. **Removed imageSpec property from DockerSaveTask**
   - Deleted `@Internal abstract Property<ImageSpec> getImageSpec()` declaration
   - Task action already uses flattened properties only

6. **Updated all test constructor calls**
   - Fixed ImageSpec instantiations: `newInstance(ImageSpec, name, project.objects, project.providers, project.layout)`
   - Fixed DockerExtension instantiations: `newInstance(DockerExtension, project.objects, project.providers, project.layout)`
   - Fixed TestIntegrationExtension instantiations: `newInstance(TestIntegrationExtension, project.layout, project.providers)`

7. **Build verification**
   - Unit tests pass: `./gradlew clean test` - BUILD SUCCESSFUL
   - Plugin builds: `./gradlew -Pplugin_version=1.0.0 build publishToMavenLocal` - BUILD SUCCESSFUL
   - Code coverage: 81.0% instructions, 80.1% branches

### ❌ Integration Test Results - 128 Configuration Cache Violations

```
BUILD FAILED in 36s
Configuration cache entry stored with 128 problems.

Errors:
- Task `:docker:scenario-10:dockerSaveAlpineTest`: cannot serialize/deserialize 'org.gradle.api.Project'
- Task `:docker:scenario-10:dockerTagAlpineTest`: cannot serialize/deserialize 'org.gradle.api.Project'
- Task `:docker:scenario-11:dockerPublishBusyboxSourceRef`: cannot serialize/deserialize 'org.gradle.api.Project'
- Task `:docker:scenario-11:dockerSaveBusyboxSourceRef`: cannot serialize/deserialize 'org.gradle.api.Project'
- Task `:docker:scenario-11:dockerTagBusyboxSourceRef`: cannot serialize/deserialize 'org.gradle.api.Project'
- Task `:docker:scenario-12:dockerPublishHaproxyTest`: cannot serialize/deserialize 'org.gradle.api.Project'
- Task `:docker:scenario-12:dockerSaveHaproxyTest`: cannot serialize/deserialize 'org.gradle.api.Project'
- Task `:docker:scenario-12:dockerTagHaproxyTest`: cannot serialize/deserialize 'org.gradle.api.Project'
```

## Root Cause Analysis

### The Core Problem

Even though we removed the `Project` field from `ImageSpec`, the tasks still have:
```groovy
@Internal
abstract Property<ImageSpec> getImageSpec()
```

**Why this causes configuration cache violations:**

1. **`@Internal` does NOT prevent serialization** in Gradle 9/10's configuration cache
   - `@Internal` only means "don't use for up-to-date checks"
   - The configuration cache still serializes ALL properties, including `@Internal` ones

2. **ImageSpec contains nested objects** that may transitively capture Gradle services:
   - `private final ObjectFactory objectFactory`
   - `private final ProviderFactory providers`
   - `private final ProjectLayout layout`
   - Nested `SaveSpec` with `ObjectFactory` and `ProjectLayout`
   - Nested `PublishSpec` with `ObjectFactory`
   - These services are implemented by Gradle internals that may reference `Project`

3. **Gradle's serialization is deep**
   - It attempts to serialize the entire object graph
   - Even indirect references through service implementations get captured

### Why DockerSaveTask Works But Others Don't

**DockerSaveTask** (✅ Fixed):
- Removed `Property<ImageSpec> imageSpec`
- Task action uses only flattened `@Input` properties: `registry`, `namespace`, `imageName`, etc.
- No nested object serialization

**DockerTagTask** (❌ Still broken):
- Still has `@Internal abstract Property<ImageSpec> getImageSpec()` (line 48)
- Task action extensively uses `imageSpec.get()` throughout (lines 90-233):
  ```groovy
  def imageSpec = this.imageSpec.get()
  def sourceRefValue = imageSpec.sourceRef.getOrElse("")
  def tagsValue = imageSpec.tags.getOrElse([])
  def isSourceRefMode = isInSourceRefMode(imageSpec)
  ```

**DockerPublishTask** (❌ Still broken):
- Still has `@Internal abstract Property<ImageSpec> getImageSpec()`
- Similar usage pattern to DockerTagTask

## Part 2: Detailed Implementation Plan

### Phase 1: Refactor DockerTagTask (Priority 1)

#### Current State Analysis

**File**: `src/main/groovy/com/kineticfire/gradle/docker/task/DockerTagTask.groovy`

**Current imageSpec usage locations:**
1. Line 48: Property declaration
2. Line 90: `def imageSpec = this.imageSpec.get()`
3. Line 91: `imageSpec.sourceRef.getOrElse("")`
4. Line 92: `imageSpec.tags.getOrElse([])`
5. Line 93: `isInSourceRefMode(imageSpec)` - passes to helper method
6. Line 98: `getEffectiveSourceRef(imageSpec)` - passes to helper method
7. Line 133: `isInSourceRefMode(imageSpec)` - helper method signature
8. Line 135-141: Accesses imageSpec properties in helper
9. Line 147: `getEffectiveSourceRef(imageSpec)` - helper method signature
10. Line 148: `EffectiveImageProperties.fromImageSpec(imageSpec)`
11. Line 157: `imageSpec.orNull` - checks for presence
12. Line 162-180: Accesses imageSpec properties for building references
13. Line 214-232: `pullSourceRefIfNeeded()` helper accesses imageSpec extensively

**Required Changes:**

1. **Remove imageSpec property:**
   ```groovy
   // DELETE:
   @Internal
   abstract Property<ImageSpec> getImageSpec()
   ```

2. **Add flattened properties (DockerTagTask already has most of these):**
   - Already exists: `sourceRef`, `registry`, `namespace`, `imageName`, `repository`, `version`, `tags`
   - Need to add:
     - `@Input @Optional abstract Property<String> getSourceRefRegistry()`
     - `@Input @Optional abstract Property<String> getSourceRefNamespace()`
     - `@Input @Optional abstract Property<String> getSourceRefImageName()`
     - `@Input @Optional abstract Property<String> getSourceRefRepository()`
     - `@Input @Optional abstract Property<String> getSourceRefTag()`
     - `@Input @Optional abstract Property<Boolean> getPullIfMissing()`
     - `@Input @Optional abstract Property<String> getEffectiveSourceRef()`
     - `@Nested @Optional abstract Property<AuthSpec> getPullAuth()`

3. **Refactor `tagImage()` task action:**
   ```groovy
   @TaskAction
   void tagImage() {
       // Pull source image if needed
       pullSourceRefIfNeeded()

       def service = dockerService.get()
       if (!service) {
           throw new IllegalStateException("dockerService must be provided")
       }

       // Use flattened properties instead of imageSpec
       def sourceRefValue = sourceRef.getOrElse("")
       def tagsValue = tags.getOrElse([])
       def isSourceRefMode = isInSourceRefMode()

       if (isSourceRefMode) {
           // SourceRef Mode: Tag existing image with new tags
           if (!tagsValue.isEmpty()) {
               def effectiveSourceRef = getEffectiveSourceRefValue()
               def future = service.tagImage(effectiveSourceRef, tagsValue)
               future.get()
           } else {
               logger.info("No additional tags specified for sourceRef mode, skipping tag operation")
           }
       } else {
           // Build Mode: Validate tags are provided
           if (tagsValue.isEmpty()) {
               throw new IllegalStateException("At least one tag must be specified")
           }

           def imageReferences = buildImageReferences()
           if (imageReferences.size() < 1) {
               throw new IllegalStateException("At least one tag must be specified")
           }

           if (imageReferences.size() == 1) {
               logger.info("Image already has tag from build, no additional tagging needed: ${imageReferences[0]}")
               return
           }

           // Multiple tags: use first tag as source, apply remaining as targets
           def sourceImageRef = imageReferences[0]
           def targetRefs = imageReferences.drop(1)
           def future = service.tagImage(sourceImageRef, targetRefs)
           future.get()
       }
   }
   ```

4. **Refactor `isInSourceRefMode()` helper:**
   ```groovy
   private boolean isInSourceRefMode() {
       // Check direct sourceRef
       if (sourceRef.isPresent() && !sourceRef.get().isEmpty()) {
           return true
       }
       // Check sourceRef components
       def hasRepository = sourceRefRepository.isPresent() && !sourceRefRepository.get().isEmpty()
       def hasImageName = sourceRefImageName.isPresent() && !sourceRefImageName.get().isEmpty()
       return hasRepository || hasImageName
   }
   ```

5. **Refactor `getEffectiveSourceRefValue()` helper:**
   ```groovy
   private String getEffectiveSourceRefValue() {
       // Priority 1: Direct sourceRef
       def sourceRefValue = sourceRef.getOrElse("")
       if (!sourceRefValue.isEmpty()) {
           return sourceRefValue
       }

       // Priority 2: SourceRef components
       def sourceRefRegistryValue = sourceRefRegistry.getOrElse("")
       def sourceRefNamespaceValue = sourceRefNamespace.getOrElse("")
       def sourceRefRepositoryValue = sourceRefRepository.getOrElse("")
       def sourceRefImageNameValue = sourceRefImageName.getOrElse("")
       def sourceRefTagValue = sourceRefTag.getOrElse("latest")

       // Repository approach
       if (!sourceRefRepositoryValue.isEmpty()) {
           def baseRef = sourceRefRegistryValue.isEmpty() ?
               sourceRefRepositoryValue :
               "${sourceRefRegistryValue}/${sourceRefRepositoryValue}"
           return "${baseRef}:${sourceRefTagValue}"
       }

       // Namespace + imageName approach
       if (!sourceRefImageNameValue.isEmpty()) {
           def reference = ""
           if (!sourceRefRegistryValue.isEmpty()) {
               reference += sourceRefRegistryValue + "/"
           }
           if (!sourceRefNamespaceValue.isEmpty()) {
               reference += sourceRefNamespaceValue + "/"
           }
           reference += sourceRefImageNameValue
           reference += ":" + sourceRefTagValue
           return reference
       }

       return ""
   }
   ```

6. **Refactor `buildImageReferences()` helper:**
   ```groovy
   List<String> buildImageReferences() {
       def references = []

       def sourceRefValue = sourceRef.getOrElse("")
       def tagsValue = tags.getOrElse([])

       if (!sourceRefValue.isEmpty()) {
           // SourceRef Mode: Use sourceRef as source, apply tags as targets
           references.add(sourceRefValue)
           tagsValue.each { tag ->
               references.add(tag)
           }
       } else {
           // Build Mode: Use nomenclature to build image references
           if (tagsValue.isEmpty()) {
               throw new IllegalStateException("At least one tag must be specified")
           }

           def registryValue = registry.getOrElse("")
           def namespaceValue = namespace.getOrElse("")
           def repositoryValue = repository.getOrElse("")
           def imageNameValue = imageName.getOrElse("")

           if (repositoryValue.isEmpty() && imageNameValue.isEmpty()) {
               throw new IllegalStateException("Either repository OR imageName must be specified when not using sourceRef")
           }

           if (!repositoryValue.isEmpty()) {
               // Using repository format
               def baseRef = registryValue.isEmpty() ? repositoryValue : "${registryValue}/${repositoryValue}"
               tagsValue.each { tag ->
                   references.add("${baseRef}:${tag}")
               }
           } else if (!imageNameValue.isEmpty()) {
               // Using namespace + imageName format
               def baseRef = ""
               if (!registryValue.isEmpty()) {
                   baseRef += "${registryValue}/"
               }
               if (!namespaceValue.isEmpty()) {
                   baseRef += "${namespaceValue}/"
               }
               baseRef += imageNameValue

               tagsValue.each { tag ->
                   references.add("${baseRef}:${tag}")
               }
           }
       }

       return references
   }
   ```

7. **Refactor `pullSourceRefIfNeeded()` helper:**
   ```groovy
   private void pullSourceRefIfNeeded() {
       if (pullIfMissing.getOrElse(false)) {
           def sourceRefValue = effectiveSourceRef.getOrElse("")
           if (sourceRefValue && !sourceRefValue.isEmpty()) {
               def authConfig = pullAuth.isPresent() ?
                   pullAuth.get().toAuthConfig() : null

               def service = dockerService.get()
               if (!service.imageExists(sourceRefValue).get()) {
                   service.pullImage(sourceRefValue, authConfig).get()
               }
           }
       }
   }
   ```

#### Testing DockerTagTask Changes

1. **Update unit tests** (`src/test/groovy/com/kineticfire/gradle/docker/task/DockerTagTaskComprehensiveTest.groovy`):
   - Remove all `task.imageSpec.set(imageSpec)` calls
   - Replace with flattened property setting:
     ```groovy
     // Build Mode example:
     task.registry.set("docker.io")
     task.namespace.set("mycompany")
     task.imageName.set("myapp")
     task.version.set("1.0.0")
     task.tags.set(["latest", "1.0.0"])

     // SourceRef Mode example:
     task.sourceRef.set("alpine:latest")
     task.tags.set(["myapp:latest", "myapp:1.0.0"])
     ```

2. **Run unit tests:**
   ```bash
   cd plugin
   ./gradlew test --tests "*DockerTagTask*"
   ```

3. **Verify no compilation errors:**
   ```bash
   ./gradlew compileGroovy
   ```

### Phase 2: Refactor DockerPublishTask (Priority 2)

#### Current State Analysis

**File**: `src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`

**Current imageSpec usage pattern** (similar to DockerTagTask):
- Property declaration for imageSpec
- Extensive use in task action for accessing properties
- Helper methods that take imageSpec as parameter

**Required Changes:**

1. **Remove imageSpec property:**
   ```groovy
   // DELETE:
   @Internal
   abstract Property<ImageSpec> getImageSpec()
   ```

2. **Add flattened properties:**
   - SourceRef properties (same as DockerTagTask)
   - Build mode nomenclature properties (already exists)
   - PullIfMissing properties (same as DockerTagTask)
   - Publish-specific properties:
     - `@Nested abstract Property<PublishSpec> getPublishSpec()`

3. **Refactor task action:**
   - Replace `imageSpec.get()` calls with direct property access
   - Use flattened properties for all image reference building
   - Use `publishSpec` for publish targets and authentication

4. **Refactor helper methods:**
   - Remove imageSpec parameters
   - Access task properties directly
   - Follow same pattern as DockerTagTask refactoring

#### Testing DockerPublishTask Changes

1. **Update unit tests** (`src/test/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask*Test.groovy`):
   - Remove `task.imageSpec.set(imageSpec)` calls
   - Set flattened properties directly

2. **Run unit tests:**
   ```bash
   cd plugin
   ./gradlew test --tests "*DockerPublishTask*"
   ```

### Phase 3: Update GradleDockerPlugin Task Wiring (Priority 3)

#### Current State

**File**: `src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`

**Current problematic code:**
```groovy
// Lines with task.imageSpec.set(imageSpec) need to be removed/refactored
```

**Search for all occurrences:**
```bash
cd plugin/src/main/groovy/com/kineticfire/gradle/docker
rg "\.imageSpec\.set" GradleDockerPlugin.groovy
```

**Expected output locations:**
- `createDockerBuildTask()` method - sets imageSpec on DockerBuildTask (if it still has one)
- `createDockerTagTask()` method - sets imageSpec on DockerTagTask
- `createDockerSaveTask()` method - sets imageSpec on DockerSaveTask (already removed)
- `createDockerPublishTask()` method - sets imageSpec on DockerPublishTask

#### Required Changes

For each task creation method, replace:
```groovy
// OLD:
task.imageSpec.set(imageSpec)

// NEW: Set flattened properties directly
task.registry.set(imageSpec.registry)
task.namespace.set(imageSpec.namespace)
task.imageName.set(imageSpec.imageName)
task.repository.set(imageSpec.repository)
task.version.set(imageSpec.version)
task.tags.set(imageSpec.tags)
task.sourceRef.set(imageSpec.sourceRef)
task.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
task.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
task.sourceRefImageName.set(imageSpec.sourceRefImageName)
task.sourceRefRepository.set(imageSpec.sourceRefRepository)
task.sourceRefTag.set(imageSpec.sourceRefTag)
task.pullIfMissing.set(imageSpec.pullIfMissing)
task.effectiveSourceRef.set(imageSpec.providers.provider { imageSpec.getEffectiveSourceRef() })
task.pullAuth.set(imageSpec.pullAuth)
```

**Example for DockerTagTask:**
```groovy
private void createDockerTagTask(Project project, ImageSpec imageSpec, DockerService dockerService) {
    def taskName = "dockerTag${imageSpec.name.capitalize()}"

    project.tasks.register(taskName, DockerTagTask) { task ->
        task.group = 'docker'
        task.description = "Tag Docker image for ${imageSpec.name}"

        // Set service
        task.dockerService.set(dockerService)

        // Set flattened properties from imageSpec
        task.registry.set(imageSpec.registry)
        task.namespace.set(imageSpec.namespace)
        task.imageName.set(imageSpec.imageName)
        task.repository.set(imageSpec.repository)
        task.version.set(imageSpec.version)
        task.tags.set(imageSpec.tags)

        // SourceRef properties
        task.sourceRef.set(imageSpec.sourceRef)
        task.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
        task.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
        task.sourceRefImageName.set(imageSpec.sourceRefImageName)
        task.sourceRefRepository.set(imageSpec.sourceRefRepository)
        task.sourceRefTag.set(imageSpec.sourceRefTag)

        // PullIfMissing properties
        task.pullIfMissing.set(imageSpec.pullIfMissing)
        task.effectiveSourceRef.set(imageSpec.providers.provider {
            imageSpec.getEffectiveSourceRef()
        })
        task.pullAuth.set(imageSpec.pullAuth)

        // Task dependencies
        def buildTask = project.tasks.named("dockerBuild${imageSpec.name.capitalize()}")
        task.dependsOn(buildTask)
    }
}
```

**Repeat similar pattern for:**
- `createDockerPublishTask()`
- Any other methods that set imageSpec on tasks

### Phase 4: Update All Test Files (Priority 4)

#### Affected Test Files

**Search for all test files that use imageSpec:**
```bash
cd plugin
rg "\.imageSpec\.set" src/test/groovy --files-with-matches
```

**Expected files:**
- `src/test/groovy/com/kineticfire/gradle/docker/task/DockerTagTaskComprehensiveTest.groovy`
- `src/test/groovy/com/kineticfire/gradle/docker/task/DockerSaveTaskComprehensiveTest.groovy`
- `src/test/groovy/com/kineticfire/gradle/docker/task/DockerPublishTaskComprehensiveTest.groovy`
- `src/test/groovy/com/kineticfire/gradle/docker/task/DockerPublishTaskInheritanceTest.groovy`
- Any other task tests

#### Test Update Pattern

**For each test method:**

1. **Build Mode test - OLD:**
   ```groovy
   def "tag image with multiple tags"() {
       given:
       def imageSpec = project.objects.newInstance(ImageSpec, 'test', project.objects, project.providers, project.layout)
       imageSpec.registry.set("docker.io")
       imageSpec.namespace.set("mycompany")
       imageSpec.imageName.set("myapp")
       imageSpec.version.set("1.0.0")
       imageSpec.tags.set(["latest", "1.0.0", "stable"])

       def task = project.tasks.create('dockerTagTest', DockerTagTask)
       task.dockerService.set(mockDockerService)
       task.imageSpec.set(imageSpec)  // ❌ DELETE THIS

       when:
       task.tagImage()

       then:
       // assertions
   }
   ```

2. **Build Mode test - NEW:**
   ```groovy
   def "tag image with multiple tags"() {
       given:
       def task = project.tasks.create('dockerTagTest', DockerTagTask)
       task.dockerService.set(mockDockerService)

       // Set flattened properties directly
       task.registry.set("docker.io")
       task.namespace.set("mycompany")
       task.imageName.set("myapp")
       task.version.set("1.0.0")
       task.tags.set(["latest", "1.0.0", "stable"])

       when:
       task.tagImage()

       then:
       // assertions
   }
   ```

3. **SourceRef Mode test - OLD:**
   ```groovy
   def "tag sourceRef image"() {
       given:
       def imageSpec = project.objects.newInstance(ImageSpec, 'test', project.objects, project.providers, project.layout)
       imageSpec.sourceRef.set("alpine:latest")
       imageSpec.tags.set(["myapp:latest", "myapp:1.0.0"])

       def task = project.tasks.create('dockerTagTest', DockerTagTask)
       task.dockerService.set(mockDockerService)
       task.imageSpec.set(imageSpec)  // ❌ DELETE THIS

       when:
       task.tagImage()

       then:
       // assertions
   }
   ```

4. **SourceRef Mode test - NEW:**
   ```groovy
   def "tag sourceRef image"() {
       given:
       def task = project.tasks.create('dockerTagTest', DockerTagTask)
       task.dockerService.set(mockDockerService)

       // Set flattened properties directly
       task.sourceRef.set("alpine:latest")
       task.tags.set(["myapp:latest", "myapp:1.0.0"])

       when:
       task.tagImage()

       then:
       // assertions
   }
   ```

#### Automated Test Updates

**Use sed for batch updates (after manual verification of pattern):**
```bash
cd plugin/src/test/groovy/com/kineticfire/gradle/docker/task

# Find all lines that set imageSpec on tasks
rg "task.*\.imageSpec\.set" -l

# For each file, create a helper script to convert tests
# (Manual process - sed patterns may vary by test structure)
```

### Phase 5: Comprehensive Testing and Verification (Priority 5)

#### Step 1: Unit Tests

```bash
cd plugin

# Test each task class individually
./gradlew test --tests "*DockerTagTask*" --no-daemon
./gradlew test --tests "*DockerPublishTask*" --no-daemon
./gradlew test --tests "*DockerSaveTask*" --no-daemon
./gradlew test --tests "*DockerBuildTask*" --no-daemon

# Run all unit tests
./gradlew clean test --no-daemon

# Verify coverage
# Check: build/reports/jacoco/test/html/index.html
```

**Acceptance Criteria:**
- All unit tests pass
- No compilation warnings
- Code coverage maintained or improved

#### Step 2: Build Plugin

```bash
cd plugin
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal --no-daemon
```

**Acceptance Criteria:**
- Build succeeds without errors
- Build succeeds without warnings
- Plugin JAR created successfully
- Plugin published to Maven Local

#### Step 3: Integration Tests WITHOUT Configuration Cache

```bash
cd ../plugin-integration-test

# First verify tests pass without config cache
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest

# Check for leftover containers
docker ps -a
```

**Acceptance Criteria:**
- All integration tests pass
- No leftover Docker containers
- No test failures

#### Step 4: Integration Tests WITH Configuration Cache (Critical)

```bash
cd plugin-integration-test

# Run with configuration cache enabled
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest --configuration-cache 2>&1 | tee /tmp/config-cache-final-test.log

# Check for violations
rg "problems were found storing the configuration cache" /tmp/config-cache-final-test.log

# Count remaining violations (should be 0)
rg "cannot serialize|cannot deserialize" /tmp/config-cache-final-test.log | wc -l

# Check for configuration cache reuse
./gradlew -Pplugin_version=1.0.0 integrationTest --configuration-cache 2>&1 | tee /tmp/config-cache-reuse-test.log
rg "Reusing configuration cache" /tmp/config-cache-reuse-test.log
```

**Acceptance Criteria:**
- Zero configuration cache violations reported
- Build succeeds with configuration cache enabled
- Configuration cache reuse works on second run
- All integration tests pass
- No leftover Docker containers

#### Step 5: Verify Specific Scenarios

**Test the previously failing scenarios individually:**

```bash
cd plugin-integration-test

# Scenario 10 - dockerSave, dockerTag
./gradlew -Pplugin_version=1.0.0 :docker:scenario-10:cleanDockerImages :docker:scenario-10:dockerBuildAlpineTest :docker:scenario-10:dockerTagAlpineTest :docker:scenario-10:dockerSaveAlpineTest --configuration-cache

# Scenario 11 - dockerSave, dockerTag, dockerPublish (SourceRef mode)
./gradlew -Pplugin_version=1.0.0 :docker:scenario-11:cleanDockerImages :docker:scenario-11:dockerPullBusyboxSourceRef :docker:scenario-11:dockerTagBusyboxSourceRef :docker:scenario-11:dockerSaveBusyboxSourceRef :docker:scenario-11:dockerPublishBusyboxSourceRef --configuration-cache

# Scenario 12 - dockerSave, dockerTag, dockerPublish (Build mode)
./gradlew -Pplugin_version=1.0.0 :docker:scenario-12:cleanDockerImages :docker:scenario-12:dockerBuildHaproxyTest :docker:scenario-12:dockerTagHaproxyTest :docker:scenario-12:dockerSaveHaproxyTest :docker:scenario-12:dockerPublishHaproxyTest --configuration-cache

# Check configuration cache report
# Look for HTML report URL in output
```

**Acceptance Criteria:**
- Each scenario completes successfully
- No configuration cache violations
- Docker images created/tagged/saved/published correctly
- Verification tasks pass

### Phase 6: Documentation and Cleanup (Priority 6)

#### Update Documentation

1. **Update gradle-9-and-10-compatibility.md:**
   ```markdown
   ## Configuration Cache Compatibility ✅

   As of 2025-10-17, the plugin is fully compatible with Gradle 9/10 configuration cache:

   - All tasks use Provider API exclusively
   - No Project references in task execution
   - No nested non-serializable objects in task properties
   - All 4 core task types (Build, Tag, Save, Publish) are configuration cache compatible
   - Zero configuration cache violations in integration tests

   ### Architectural Approach

   Tasks use **flattened properties** instead of nested spec objects:
   - ❌ OLD: `@Internal Property<ImageSpec> imageSpec`
   - ✅ NEW: Individual `@Input` properties for each value

   This ensures all task inputs are primitives or serializable types, avoiding
   transitive Project reference capture through nested object graphs.
   ```

2. **Update task documentation** in each task class:
   ```groovy
   /**
    * Task for tagging Docker images
    *
    * Configuration Cache Compatible: ✅
    * - Uses flattened @Input properties only
    * - No nested object serialization
    * - No Project reference capture
    *
    * @since 1.0.0
    */
   abstract class DockerTagTask extends DefaultTask {
   ```

3. **Update CHANGELOG.md:**
   ```markdown
   ## [Unreleased]

   ### Changed
   - **BREAKING**: Removed `imageSpec` property from DockerTagTask, DockerSaveTask, and DockerPublishTask
     - Tasks now use flattened properties instead of nested ImageSpec object
     - Unit tests must set task properties directly instead of via imageSpec
     - Plugin wiring automatically maps ImageSpec to flattened task properties

   ### Fixed
   - Fixed 128 configuration cache violations in Docker tasks
   - Plugin now fully compatible with Gradle 9/10 configuration cache
   - Configuration cache reuse now works correctly
   ```

4. **Mark TODO as completed:**
   Update `docs/design-docs/todo/2025-10-17-config-cache-incompat-issues.md`:
   ```markdown
   **Status**: ✅ Completed on 2025-10-17

   ## Resolution Summary

   All configuration cache violations have been resolved by:
   1. Removing Project fields from all Spec and Extension classes
   2. Removing `Property<ImageSpec>` from all task classes
   3. Using flattened @Input properties in tasks instead of nested objects
   4. Updating all task wiring to map ImageSpec properties to task properties
   5. Updating all tests to set task properties directly

   **Result**: Zero configuration cache violations, all integration tests pass.
   ```

#### Code Cleanup

1. **Remove unused imports** in task files:
   ```groovy
   // If ImageSpec import is no longer used:
   // DELETE: import com.kineticfire.gradle.docker.spec.ImageSpec
   ```

2. **Remove dead code** (if any helper methods are no longer needed)

3. **Verify code style:**
   ```bash
   cd plugin
   ./gradlew checkstyleMain checkstyleTest
   ```

4. **Run final verification:**
   ```bash
   cd plugin
   ./gradlew clean build --no-daemon

   cd ../plugin-integration-test
   ./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest --configuration-cache

   # Verify zero violations
   docker ps -a  # Should show no leftover containers
   ```

## Implementation Checklist

### Phase 1: DockerTagTask
- [ ] Remove `@Internal Property<ImageSpec> getImageSpec()` from DockerTagTask.groovy
- [ ] Add sourceRef flattened properties to DockerTagTask.groovy
- [ ] Add pullIfMissing properties to DockerTagTask.groovy
- [ ] Refactor `tagImage()` task action to use flattened properties
- [ ] Refactor `isInSourceRefMode()` to not take imageSpec parameter
- [ ] Refactor `getEffectiveSourceRefValue()` to not take imageSpec parameter
- [ ] Refactor `buildImageReferences()` to not access imageSpec
- [ ] Refactor `pullSourceRefIfNeeded()` to not access imageSpec
- [ ] Update DockerTagTaskComprehensiveTest.groovy - remove imageSpec.set() calls
- [ ] Update any other DockerTagTask tests
- [ ] Run `./gradlew test --tests "*DockerTagTask*"` - verify all pass
- [ ] Fix any test failures
- [ ] Verify no compilation warnings

### Phase 2: DockerPublishTask
- [ ] Remove `@Internal Property<ImageSpec> getImageSpec()` from DockerPublishTask.groovy
- [ ] Add all required flattened properties to DockerPublishTask.groovy
- [ ] Refactor task action to use flattened properties
- [ ] Refactor all helper methods
- [ ] Update DockerPublishTaskComprehensiveTest.groovy
- [ ] Update DockerPublishTaskInheritanceTest.groovy
- [ ] Update any other DockerPublishTask tests
- [ ] Run `./gradlew test --tests "*DockerPublishTask*"` - verify all pass
- [ ] Fix any test failures
- [ ] Verify no compilation warnings

### Phase 3: GradleDockerPlugin
- [ ] Find all `task.imageSpec.set(imageSpec)` calls in GradleDockerPlugin.groovy
- [ ] Replace with flattened property setting for DockerTagTask creation
- [ ] Replace with flattened property setting for DockerPublishTask creation
- [ ] Verify DockerSaveTask creation doesn't set imageSpec (already removed)
- [ ] Verify DockerBuildTask creation doesn't set imageSpec (shouldn't have one)
- [ ] Run `./gradlew compileGroovy` - verify no compilation errors
- [ ] Run `./gradlew build` - verify no warnings

### Phase 4: Test Files
- [ ] Run `rg "\.imageSpec\.set" src/test/groovy --files-with-matches` to find all files
- [ ] For each file, manually review and update test methods
- [ ] Remove imageSpec object creation where no longer needed
- [ ] Replace `task.imageSpec.set(spec)` with direct property setting
- [ ] Run `./gradlew test` - verify all tests pass
- [ ] Fix any test failures
- [ ] Verify test coverage maintained

### Phase 5: Testing and Verification
- [ ] Unit tests: `./gradlew clean test --no-daemon` - all pass
- [ ] Build plugin: `./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal` - success
- [ ] Integration tests (no cache): `./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest` - all pass
- [ ] Integration tests (with cache): `./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest --configuration-cache` - all pass
- [ ] Count violations: Should be **0**
- [ ] Verify cache reuse: Second run shows "Reusing configuration cache"
- [ ] Test scenario-10 individually with --configuration-cache
- [ ] Test scenario-11 individually with --configuration-cache
- [ ] Test scenario-12 individually with --configuration-cache
- [ ] Verify no leftover containers: `docker ps -a`
- [ ] Check configuration cache report HTML for any warnings

### Phase 6: Documentation
- [ ] Update gradle-9-and-10-compatibility.md
- [ ] Add doc comments to DockerTagTask
- [ ] Add doc comments to DockerPublishTask
- [ ] Add doc comments to DockerSaveTask
- [ ] Update CHANGELOG.md
- [ ] Mark 2025-10-17-config-cache-incompat-issues.md as completed
- [ ] Remove unused imports from task files
- [ ] Run `./gradlew checkstyleMain checkstyleTest`
- [ ] Final verification build
- [ ] Final verification integration test with --configuration-cache

## Success Criteria

All of the following must be true:

- ✅ Zero configuration cache violations reported
- ✅ All 4 task classes (Build, Save, Tag, Publish) have no `Property<ImageSpec>`
- ✅ All unit tests pass (100% pass rate)
- ✅ Plugin builds successfully with no warnings
- ✅ All integration tests pass without configuration cache
- ✅ All integration tests pass WITH configuration cache enabled
- ✅ Configuration cache reuse works on second run ("Reusing configuration cache" message appears)
- ✅ No leftover Docker containers after integration tests (`docker ps -a` is clean)
- ✅ Code coverage maintained at ≥80%
- ✅ Documentation updated
- ✅ No compilation warnings
- ✅ No checkstyle violations

## Estimated Effort

**Phase 1: DockerTagTask**
- Remove property & refactor task action: 2-3 hours
- Refactor helper methods: 1-2 hours
- Update tests: 1-2 hours
- **Subtotal**: 4-7 hours

**Phase 2: DockerPublishTask**
- Remove property & refactor task action: 2-3 hours
- Refactor helper methods: 1-2 hours
- Update tests: 1-2 hours
- **Subtotal**: 4-7 hours

**Phase 3: GradleDockerPlugin**
- Update task wiring: 1-2 hours
- **Subtotal**: 1-2 hours

**Phase 4: Test Files**
- Update remaining test files: 2-3 hours
- **Subtotal**: 2-3 hours

**Phase 5: Testing & Verification**
- Run tests, fix issues: 2-4 hours
- **Subtotal**: 2-4 hours

**Phase 6: Documentation**
- Update docs and cleanup: 1-2 hours
- **Subtotal**: 1-2 hours

**Total Estimated Time**: 14-25 hours

## Risk Assessment

### High Risk Items
1. **Breaking test compatibility** - Many tests need updates
   - Mitigation: Update tests incrementally, verify each batch

2. **Missing edge cases** - Complex imageSpec usage patterns
   - Mitigation: Comprehensive test coverage, manual testing of all scenarios

3. **Performance regression** - Flattened properties vs nested objects
   - Mitigation: Should be negligible; Provider API is designed for this

### Medium Risk Items
1. **Build cache invalidation** - Large changes to task classes
   - Mitigation: Expected and acceptable for architectural fix

2. **Integration test instability** - Docker daemon issues
   - Mitigation: Clean containers before/after tests

### Low Risk Items
1. **Documentation gaps** - May miss some updates
   - Mitigation: Comprehensive checklist above

## References

- Parent document: `docs/design-docs/todo/2025-10-17-config-cache-incompat-issues.md`
- Gradle docs: https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html
- Gradle compatibility guide: `docs/design-docs/gradle-9-and-10-compatibility.md`
- Configuration cache report: `plugin-integration-test/build/reports/configuration-cache/`

## Notes

- This is Part 2 of the configuration cache compatibility fix
- Part 1 (removing Project from Spec classes) is complete and verified
- The remaining work focuses on removing `Property<ImageSpec>` from tasks
- All changes maintain backward compatibility for plugin users (DSL unchanged)
- Only internal task structure changes; user-facing API remains the same
