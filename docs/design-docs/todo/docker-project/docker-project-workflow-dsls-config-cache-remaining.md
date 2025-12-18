# Remaining Work: dockerProject DSL Changes and Integration Tests

## Status: ALL STEPS COMPLETED

### Completion Notes (2025-12-17)

**Step 1: Implement Planned DSL Changes** - ✅ COMPLETED
- 1.1: Used `NamedDomainObjectContainer<ProjectImageSpec>` directly instead of separate wrapper class
- 1.2: `ProjectImageSpec` updated with `blockName`, `primary`, `repository` properties; implements `Named` interface
- 1.3: `DockerProjectSpec` uses `images` container with `getImages()`, `getPrimaryImage()`, `isConfigured()` methods
- 1.4: `DockerProjectExtension` has `images(Closure)` and `images(Action)` DSL methods
- 1.5: `DockerProjectTaskGenerator` iterates all images and handles primary designation for success operations
- 1.6: Validation exists for empty images and missing primary when multiple images defined
- 1.7 & 1.8: Unit and functional tests work with new DSL structure

**Step 2: Update Existing Integration Tests for New DSL** - ✅ COMPLETED
- 2.1: scenario-1-build-mode uses `images { scenario1App { imageName.set('project-scenario1-app')... } }`
- 2.2: scenario-2-sourceref-mode uses `images { scenario2Nginx { sourceRefImageName.set('nginx')... } }`
- 2.3: scenario-3-save-publish uses `images { scenario3App { imageName.set('project-scenario3-app')... } }`
- 2.4: scenario-4-method-lifecycle uses `images { scenario4App { imageName.set('project-scenario4-app')... } }`
- 2.5: scenario-5-contextdir-mode uses `images { scenario5App { imageName.set('project-scenario5-app')... } }`

**Step 3: Create New Integration Test Scenarios** - ✅ COMPLETED (2025-12-17)
- 3.1: scenario-6-repository-mode - Tests `repository` property as alternative to imageName (port 9306)
- 3.2: scenario-7-repository-registry - Tests repository mode with private registry publishing (port 9307, registry 5037)
- 3.3: scenario-8-imagename-full - Tests full imageName mode with registry, namespace, buildArgs, labels (port 9308, registry 5038)
- 3.4: scenario-9-config-cache - Tests configuration cache compatibility verification (port 9309)
- Updated settings.gradle with new project includes
- Updated dockerProject/build.gradle with new scenario dependencies
- Updated dockerProject/README.md with new scenario documentation and feature matrix

---

## Background

The original plan (`docker-project-workflow-dsls-config-cache.md`) described both infrastructure changes for configuration cache compatibility AND DSL changes to support multiple images. Phases 0-4 implemented the **infrastructure** but the **DSL changes** were never implemented.

### What Was Completed (Phases 0-4)

- `TaskGraphGenerator` base class for static task graph generation
- `TaskNamingUtils` for consistent task naming
- `DockerProjectTaskGenerator` for generating dockerProject task graphs
- `WorkflowTaskGenerator` for generating dockerWorkflows task graphs
- Static task registration at configuration time
- File-based state communication for conditional execution

### What Was NOT Completed (Original List - Now Partially Done)

1. ~~**DSL not changed from `image` (singular) to `images` (plural container)**~~ ✅ DONE
2. ~~**No support for multiple images with `primary` designation**~~ ✅ DONE
3. ~~**No support for Repository Mode (`repository` property)**~~ ✅ DONE (property exists)
4. ~~**Integration tests not updated for new DSL**~~ ✅ DONE (scenarios 1-5)
5. ~~**New integration test scenarios not created**~~ ✅ DONE (scenarios 6-9 created)

### Current State

**Current DSL (NOW IMPLEMENTED):**
```groovy
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            primary.set(true)  // receives onSuccess.additionalTags (auto-true for single image)
        }
        testDb {
            imageName.set('test-db')
            contextDir.set('src/test/docker/db')
        }
    }
    test { ... }
    onSuccess { ... }
}
```

**Existing Integration Test Scenarios (all updated to new DSL):**
- `scenario-1-build-mode` - uses NEW `images { scenario1App { } }` DSL ✅
- `scenario-2-sourceref-mode` - uses NEW `images { scenario2Nginx { } }` DSL ✅
- `scenario-3-save-publish` - uses NEW `images { scenario3App { } }` DSL ✅
- `scenario-4-method-lifecycle` - uses NEW `images { scenario4App { } }` DSL ✅
- `scenario-5-contextdir-mode` - uses NEW `images { scenario5App { } }` DSL ✅

---

## Step 1: Implement Planned DSL Changes ✅ COMPLETED

### 1.1 Create New Spec Classes

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/ProjectImagesContainer.groovy`

Create a `NamedDomainObjectContainer` wrapper for named image specs:

```groovy
abstract class ProjectImagesContainer {
    private final NamedDomainObjectContainer<ProjectImageSpec> images

    @Inject
    ProjectImagesContainer(ObjectFactory objectFactory) {
        this.images = objectFactory.domainObjectContainer(ProjectImageSpec) { name ->
            def spec = objectFactory.newInstance(ProjectImageSpec)
            spec.blockName.set(name)  // Store the DSL block name
            return spec
        }
    }

    NamedDomainObjectContainer<ProjectImageSpec> getImages() {
        return images
    }

    void configure(Action<NamedDomainObjectContainer<ProjectImageSpec>> action) {
        action.execute(images)
    }
}
```

### 1.2 Update ProjectImageSpec

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/ProjectImageSpec.groovy`

Add new properties:

```groovy
// Block name from DSL (e.g., "myApp" from images { myApp { } })
abstract Property<String> getBlockName()

// Primary image designation (receives onSuccess.additionalTags)
abstract Property<Boolean> getPrimary()

// Repository Mode support (mutually exclusive with imageName)
abstract Property<String> getRepository()

// Initialize defaults
void initializeDefaults() {
    primary.convention(false)
}
```

### 1.3 Update DockerProjectSpec

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/DockerProjectSpec.groovy`

Replace singular `image` with plural `images`:

```groovy
// REMOVE:
// abstract Property<ProjectImageSpec> getImage()
// void image(Closure closure) { ... }
// void image(Action<ProjectImageSpec> action) { ... }

// ADD:
private NamedDomainObjectContainer<ProjectImageSpec> imagesContainer

void initializeNestedSpecs() {
    if (imagesContainer == null) {
        imagesContainer = objectFactory.domainObjectContainer(ProjectImageSpec) { name ->
            def spec = objectFactory.newInstance(ProjectImageSpec)
            spec.blockName.set(name)
            spec.initializeDefaults()
            return spec
        }
    }
    // ... rest of initialization
}

NamedDomainObjectContainer<ProjectImageSpec> getImages() {
    initializeNestedSpecs()
    return imagesContainer
}

void images(Action<NamedDomainObjectContainer<ProjectImageSpec>> action) {
    initializeNestedSpecs()
    action.execute(imagesContainer)
}

void images(Closure closure) {
    initializeNestedSpecs()
    closure.delegate = imagesContainer
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure.call()
}

// Update isConfigured() to check images container
boolean isConfigured() {
    return imagesContainer != null && !imagesContainer.isEmpty()
}

// Helper to get primary image
ProjectImageSpec getPrimaryImage() {
    if (imagesContainer == null || imagesContainer.isEmpty()) {
        return null
    }
    // If only one image, it's automatically primary
    if (imagesContainer.size() == 1) {
        return imagesContainer.first()
    }
    // Otherwise, find the one marked primary
    return imagesContainer.find { it.primary.getOrElse(false) }
}
```

### 1.4 Update DockerProjectExtension

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerProjectExtension.groovy`

```groovy
// REMOVE:
// void image(Closure closure) { spec.image(closure) }
// void image(Action<ProjectImageSpec> action) { spec.image(action) }

// ADD:
NamedDomainObjectContainer<ProjectImageSpec> getImages() {
    return spec.images
}

void images(Action<NamedDomainObjectContainer<ProjectImageSpec>> action) {
    spec.images(action)
}

void images(Closure closure) {
    spec.images(closure)
}
```

### 1.5 Update DockerProjectTaskGenerator

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/generator/DockerProjectTaskGenerator.groovy`

Update to iterate over all images and handle primary designation:

```groovy
void generate(Project project, DockerProjectExtension extension, Provider<DockerService> dockerServiceProvider) {
    if (!extension.spec.isConfigured()) {
        return
    }

    def spec = extension.spec
    def testSpec = spec.test.get()
    def successSpec = spec.onSuccess.get()

    // Get all images
    def allImages = spec.images.toList()
    if (allImages.isEmpty()) {
        return
    }

    // Determine primary image
    def primaryImage = spec.primaryImage
    if (primaryImage == null && allImages.size() > 1) {
        throw new GradleException("Multiple images defined but none marked as primary. " +
            "Set primary.set(true) on exactly one image.")
    }

    // Build tasks for ALL images
    def buildTaskProviders = [:]
    allImages.each { imageSpec ->
        def imageName = deriveImageName(imageSpec, project)
        def sanitizedName = TaskNamingUtils.capitalize(TaskNamingUtils.normalizeName(imageName))
        buildTaskProviders[imageName] = registerBuildTask(project, imageSpec, sanitizedName, dockerServiceProvider)
    }

    // Tag on success applies ONLY to primary image
    def primaryImageName = deriveImageName(primaryImage, project)
    def primarySanitizedName = TaskNamingUtils.capitalize(TaskNamingUtils.normalizeName(primaryImageName))
    def tagOnSuccessTaskProvider = registerTagOnSuccessTask(
        project, primaryImage, successSpec, primarySanitizedName, dockerServiceProvider
    )

    // ... rest of task wiring
}
```

### 1.6 Add Validation

**File:** `plugin/src/main/groovy/com/kineticfire/gradle/docker/generator/DockerProjectTaskGenerator.groovy`

Add validation for:
- At least one image must be defined
- Exactly one image must be primary (or only one image total)
- `imageName` and `repository` are mutually exclusive
- Build mode properties and `sourceRef` are mutually exclusive

### 1.7 Update Unit Tests

**File:** `plugin/src/test/groovy/com/kineticfire/gradle/docker/generator/DockerProjectTaskGeneratorTest.groovy`

Update all tests to use new `images { }` DSL structure.

### 1.8 Update Functional Tests

Update all functional tests in `plugin/src/functionalTest/` that test dockerProject DSL.

---

## Step 2: Update Existing Integration Tests for New DSL ✅ COMPLETED

### 2.1 Update scenario-1-build-mode

**File:** `plugin-integration-test/dockerProject/scenario-1-build-mode/app-image/build.gradle`

Change from:
```groovy
dockerProject {
    image {
        name.set('project-scenario1-app')
        tags.set(['latest', '1.0.0'])
        jarFrom.set(':app:jar')
    }
    // ...
}
```

To:
```groovy
dockerProject {
    images {
        scenario1App {
            imageName.set('project-scenario1-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            // Single image is automatically primary
        }
    }
    // ...
}
```

Also add `--configuration-cache` verification to the test.

### 2.2 Update scenario-2-sourceref-mode

**File:** `plugin-integration-test/dockerProject/scenario-2-sourceref-mode/app-image/build.gradle`

Change from:
```groovy
dockerProject {
    image {
        sourceRefImageName.set('nginx')
        // ...
    }
}
```

To:
```groovy
dockerProject {
    images {
        scenario2Nginx {
            sourceRefImageName.set('nginx')
            // ...
        }
    }
}
```

Also add `--configuration-cache` verification.

### 2.3 Update scenario-3-save-publish

**File:** `plugin-integration-test/dockerProject/scenario-3-save-publish/app-image/build.gradle`

Change to use `images { scenario3App { ... } }` structure.

Also add `--configuration-cache` verification.

### 2.4 Update scenario-4-method-lifecycle

**File:** `plugin-integration-test/dockerProject/scenario-4-method-lifecycle/app-image/build.gradle`

Change to use `images { ... }` structure.

### 2.5 Update scenario-5-contextdir-mode

**File:** `plugin-integration-test/dockerProject/scenario-5-contextdir-mode/app-image/build.gradle`

Change to use `images { ... }` structure.

---

## Step 3: Create New Integration Test Scenarios

The original plan's scenarios 4-7 conflict with existing scenario-4 and scenario-5. Rename to scenarios 6-9.

### 3.1 Create scenario-6-repository-mode

**Location:** `plugin-integration-test/dockerProject/scenario-6-repository-mode/`

**Purpose:** Verify Repository Mode works end-to-end

```groovy
dockerProject {
    images {
        scenario6App {
            repository.set('scenario6org/scenario6-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

**Verifications:**
- Image builds with repository-style name: `scenario6org/scenario6-app:latest`
- Tests run against the image
- On success, `scenario6org/scenario6-app:tested` tag is applied

### 3.2 Create scenario-7-repository-registry

**Location:** `plugin-integration-test/dockerProject/scenario-7-repository-registry/`

**Purpose:** Verify Repository Mode with private registry publishing

```groovy
dockerProject {
    images {
        scenario7App {
            repository.set('scenario7org/scenario7-app')
            registry.set('localhost:5037')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested', 'stable'])
        publishRegistry.set('localhost:5037')
        publishTags.set(['latest', '1.0.0', 'tested'])
    }
}
```

**Verifications:**
- Spin up local registry on port 5037
- Build image with repository name
- Run tests
- Publish to registry on success
- Verify images exist in registry

### 3.3 Create scenario-8-imagename-full

**Location:** `plugin-integration-test/dockerProject/scenario-8-imagename-full/`

**Purpose:** Verify Image Name Mode with registry, namespace, multiple buildArgs and labels

```groovy
dockerProject {
    images {
        scenario8App {
            imageName.set('scenario8-app')
            registry.set('localhost:5038')
            namespace.set('scenario8ns')
            tags.set(['latest', '1.0.0', 'dev'])
            jarFrom.set(':app:jar')

            // Multiple build arguments
            buildArgs.put('BUILD_VERSION', '1.0.0')
            buildArgs.put('BUILD_DATE', '2025-01-15')
            buildArgs.put('JAVA_VERSION', '21')

            // Multiple labels (OCI standard)
            labels.put('org.opencontainers.image.title', 'Scenario 8 App')
            labels.put('org.opencontainers.image.version', '1.0.0')
            labels.put('org.opencontainers.image.vendor', 'Test Vendor')
            labels.put('org.opencontainers.image.authors', 'test@example.com')
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested', 'verified'])
        saveFile.set('build/images/scenario8-app.tar.gz')
        publishRegistry.set('localhost:5038')
        publishNamespace.set('scenario8ns')
        publishTags.set(['latest', '1.0.0', 'tested'])
    }
}
```

**Verifications:**
- Build image with full naming: `localhost:5038/scenario8ns/scenario8-app`
- All 4 labels verified via `docker inspect`
- Image saved to tar.gz
- Published to registry

### 3.4 Create scenario-9-config-cache

**Location:** `plugin-integration-test/dockerProject/scenario-9-config-cache/`

**Purpose:** Explicitly verify configuration cache compatibility with two consecutive runs

```groovy
dockerProject {
    images {
        scenario9App {
            imageName.set('scenario9-app')
            tags.set(['latest'])
            jarFrom.set(':app:jar')
        }
    }
    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }
    onSuccess {
        additionalTags.set(['tested'])
    }
}
```

**Test Script:**
```bash
# First run - stores configuration cache
./gradlew --configuration-cache runScenario9Pipeline

# Second run - must reuse cache (no "Calculating task graph")
./gradlew --configuration-cache runScenario9Pipeline 2>&1 | grep -q "Reusing configuration cache"
```

**Verifications:**
- First run completes successfully with `--configuration-cache`
- Second run reuses configuration cache (output contains "Reusing configuration cache")
- No configuration cache problems reported

---

## Port Allocation

Per README conventions, allocate ports for new scenarios:

| Scenario | Service Port | Registry Port |
|----------|--------------|---------------|
| scenario-6-repository-mode | 9306 | N/A |
| scenario-7-repository-registry | 9307 | 5037 |
| scenario-8-imagename-full | 9308 | 5038 |
| scenario-9-config-cache | 9309 | N/A |

---

## Acceptance Criteria

1. All unit tests pass with 100% coverage on new/modified code
2. All functional tests pass
3. All existing integration tests (scenarios 1-5) pass with updated DSL
4. All new integration tests (scenarios 6-9) pass
5. Configuration cache verification passes (scenario-9)
6. No lingering Docker containers after test runs
