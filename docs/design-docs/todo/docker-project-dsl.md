# Design Plan: `dockerProject` Simplified Facade DSL

**Created:** 2025-12-06
**Status:** MOSTLY COMPLETE - Integration tests NOT implemented
**Priority:** Medium-term improvement
**Last Updated:** 2025-12-09 (implementation status update)

---

## Implementation Status Update (2025-12-09)

**Phases 1-4 and Phase 6 are COMPLETE:**
- Phase 1: Core Infrastructure (Spec Classes) - DONE
- Phase 2: Extension Class - DONE
- Phase 3: Translator Service - DONE
- Phase 4: Plugin Integration - DONE
- Phase 6: Documentation - DONE

**Phase 5 is PARTIALLY complete:**
- Functional tests (DSL parsing via Gradle TestKit) - DONE (20 tests)
- **Integration tests - NOT DONE**

**REMAINING WORK:**
The following integration tests in `plugin-integration-test/dockerProject/` were NOT implemented and need to be added:
1. `scenario-1-build-mode/` - Basic build mode: jarFrom, test, additionalTags
2. `scenario-2-sourceref-mode/` - SourceRef mode with component properties
3. `scenario-3-save-publish/` - Save and publish on success
4. `scenario-4-method-lifecycle/` - Method lifecycle mode
5. `scenario-5-contextdir-mode/` - Build mode using contextDir instead of jarFrom
6. `README.md` - Documentation of scenarios

---

## Origin

This feature was identified as a recommendation in the project review conducted on 2025-12-06.
See: `docs/design-docs/project-reviews/2025-12-06-project-review.md`

The review identified that while the plugin architecture is sound, the three-DSL approach
(`docker`, `dockerOrch`, `dockerWorkflows`) creates a high cognitive load for users.
The `dockerProject` simplified facade addresses the "80% use case" recommendation from
that review.

---

## Overview

The `dockerProject` DSL is a **high-level abstraction** that internally generates the lower-level `docker`,
`dockerOrch`, and `dockerWorkflows` configurations. It targets the 80% use case of:
**build image -> test with compose -> tag/save/publish on success**.

---

## Target User Experience

### Build Mode Example

```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

// Simplified DSL - one block instead of three
// NOTE: Uses Provider API .set() syntax for Property<T> types (Gradle 9/10 best practice)
dockerProject {
    image {
        name.set('my-app')
        tags.set(['latest', '1.0.0'])
        dockerfile.set('src/main/docker/Dockerfile')
        jarFrom.set(':app:jar')  // Auto-creates contextTask + wiring
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        lifecycle.set('class')  // or 'method'
    }

    onSuccess {
        additionalTags.set(['tested', 'stable'])
        saveFile.set('build/images/my-app.tar.gz')  // Uses .set() for consistency
        // publish registry: 'registry.example.com', namespace: 'myproject'
    }
}

dependencies {
    integrationTestImplementation libs.rest.assured
}
```

### SourceRef Mode Example

```groovy
dockerProject {
    image {
        // Use existing image instead of building
        sourceRefRegistry.set('docker.io')
        sourceRefNamespace.set('library')
        sourceRefImageName.set('nginx')
        sourceRefTag.set('1.25')

        // Local tags to apply
        tags.set(['my-nginx', 'latest'])

        pullIfMissing.set(true)

        // For private registries (optional)
        pullAuth {
            username.set(System.getenv('DOCKER_USER'))
            password.set(System.getenv('DOCKER_PASS'))
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

This replaces 100+ lines of `docker { }`, `dockerOrch { }`, `dockerWorkflows { }`, and wiring code with ~20 lines.

---

## Architecture Approach

### Design Principle: Translator Pattern

The `dockerProject` DSL doesn't introduce new runtime behavior. It is a **configuration translator** that:

1. Collects simplified configuration at configuration time
2. Translates into equivalent `docker`, `dockerOrch`, `dockerWorkflows` configurations
3. Lets the existing infrastructure do all the work

This has several advantages:

- **No new tasks needed** - reuses existing task types
- **No new services needed** - reuses existing DockerService, ComposeService
- **Full backward compatibility** - advanced users can still use the three separate DSLs
- **Reduced test surface** - only need to test the translation logic

---

## Prerequisite Tasks (Before Implementation)

Before implementing the `dockerProject` DSL, the following changes should be made to the existing codebase
to improve defensive coding. These are **recommended improvements**, not strict blockers, because:

- The `dockerProject` translator uses `onTestSuccess` (which HAS a convention), not `onSuccess`
- The translator code already handles empty `waitForServices` via `.getOrElse([])` patterns
- However, adding these conventions improves overall code quality and prevents future issues

### Classification of Prerequisites

| Task | Severity | Rationale |
|------|----------|-----------|
| Add convention for `PipelineSpec.onSuccess/onFailure` | **Recommended** | Prevents NPE if future code uses `onSuccess` instead of `onTestSuccess` |
| Add convention for `WaitSpec.waitForServices` | **Optional** | Defensive coding; translator already handles empty case |
| Update unit tests | **Required if changes made** | Verify conventions work correctly |

### 1. Add Convention for PipelineSpec.onSuccess and onFailure (Recommended)

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/workflow/PipelineSpec.groovy`

**Current State**: The constructor (lines 43-47) sets conventions for `onTestSuccess` and `onTestFailure`
but NOT for `onSuccess` and `onFailure`:

```groovy
// Current code in PipelineSpec constructor:
build.convention(objectFactory.newInstance(BuildStepSpec))
test.convention(objectFactory.newInstance(TestStepSpec))
onTestSuccess.convention(objectFactory.newInstance(SuccessStepSpec))  // ✅ HAS convention
onTestFailure.convention(objectFactory.newInstance(FailureStepSpec))  // ✅ HAS convention
always.convention(objectFactory.newInstance(AlwaysStepSpec))
// onSuccess - NO convention set ❌
// onFailure - NO convention set ❌
```

**Impact**: Calling `pipeline.onSuccess { }` without checking `isPresent()` first will throw NPE.
The `dockerProject` translator safely uses `onTestSuccess` which has a convention.

**Recommended Change**: Add conventions in the constructor:

```groovy
// Add after existing conventions in PipelineSpec constructor
onSuccess.convention(objectFactory.newInstance(SuccessStepSpec))
onFailure.convention(objectFactory.newInstance(FailureStepSpec))
```

### 2. Add Convention for WaitSpec.waitForServices (Optional)

**File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/WaitSpec.groovy`

**Current State**: `waitForServices` has no convention set (line 37), while `timeoutSeconds` and
`pollSeconds` do have conventions (lines 33-34).

**Impact**: Low - Gradle's `ListProperty` returns empty list when calling `.getOrElse([])`, and
the translator code already uses this pattern. However, adding the convention is cleaner.

**Optional Change**: Add convention in the constructor:

```groovy
@Inject
WaitSpec() {
    timeoutSeconds.convention(60)
    pollSeconds.convention(2)
    waitForServices.convention([])  // ADD THIS LINE - defensive coding
}
```

### 3. Update Unit Tests (If Changes Made)

After making the above changes, verify existing unit tests still pass and add tests for the new conventions.

---

## Implementation Plan

### Phase 1: Core Infrastructure (Spec Classes)

#### New Files to Create

| File | Purpose |
|------|---------|
| `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/DockerProjectSpec.groovy` | Top-level spec for the entire dockerProject block |
| `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/ProjectImageSpec.groovy` | Simplified image configuration |
| `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/ProjectTestSpec.groovy` | Simplified test configuration |
| `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/ProjectSuccessSpec.groovy` | Simplified onSuccess configuration |
| `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/project/ProjectFailureSpec.groovy` | Simplified onFailure configuration (optional) |

#### `DockerProjectSpec.groovy`

```groovy
package com.kineticfire.gradle.docker.spec.project

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.file.ProjectLayout

import javax.inject.Inject

/**
 * Top-level specification for the dockerProject { } simplified DSL.
 *
 * This spec collects simplified configuration and is later translated
 * into docker, dockerOrch, and dockerWorkflows configurations.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: This class uses @Inject for service injection.
 * Gradle's ObjectFactory will inject ObjectFactory, ProviderFactory, and ProjectLayout
 * automatically when using objectFactory.newInstance(DockerProjectSpec).
 */
abstract class DockerProjectSpec {

    private final ObjectFactory objectFactory
    private final ProviderFactory providers
    private final ProjectLayout layout

    // Lazy-initialized nested specs to avoid calling .convention() on uninitialized abstract properties
    private ProjectImageSpec imageSpec
    private ProjectTestSpec testSpec
    private ProjectSuccessSpec successSpec
    private ProjectFailureSpec failureSpec

    @Inject
    DockerProjectSpec(ObjectFactory objectFactory, ProviderFactory providers, ProjectLayout layout) {
        this.objectFactory = objectFactory
        this.providers = providers
        this.layout = layout
    }

    /**
     * Initialize nested specs lazily. This must be called after construction
     * because abstract Property fields are not available in the constructor.
     * Called automatically by DockerProjectExtension after instantiation.
     */
    void initializeNestedSpecs() {
        if (imageSpec == null) {
            imageSpec = objectFactory.newInstance(ProjectImageSpec)
            image.set(imageSpec)
        }
        if (testSpec == null) {
            testSpec = objectFactory.newInstance(ProjectTestSpec)
            test.set(testSpec)
        }
        if (successSpec == null) {
            successSpec = objectFactory.newInstance(ProjectSuccessSpec)
            onSuccess.set(successSpec)
        }
        if (failureSpec == null) {
            failureSpec = objectFactory.newInstance(ProjectFailureSpec)
            onFailure.set(failureSpec)
        }
    }

    abstract Property<ProjectImageSpec> getImage()
    abstract Property<ProjectTestSpec> getTest()
    abstract Property<ProjectSuccessSpec> getOnSuccess()
    abstract Property<ProjectFailureSpec> getOnFailure()

    // DSL methods with Closure support (Groovy DSL compatibility)
    void image(@DelegatesTo(ProjectImageSpec) Closure closure) {
        initializeNestedSpecs()
        closure.delegate = image.get()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void image(Action<ProjectImageSpec> action) {
        initializeNestedSpecs()
        action.execute(image.get())
    }

    void test(@DelegatesTo(ProjectTestSpec) Closure closure) {
        initializeNestedSpecs()
        closure.delegate = test.get()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void test(Action<ProjectTestSpec> action) {
        initializeNestedSpecs()
        action.execute(test.get())
    }

    void onSuccess(@DelegatesTo(ProjectSuccessSpec) Closure closure) {
        initializeNestedSpecs()
        closure.delegate = onSuccess.get()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void onSuccess(Action<ProjectSuccessSpec> action) {
        initializeNestedSpecs()
        action.execute(onSuccess.get())
    }

    void onFailure(@DelegatesTo(ProjectFailureSpec) Closure closure) {
        initializeNestedSpecs()
        closure.delegate = onFailure.get()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void onFailure(Action<ProjectFailureSpec> action) {
        initializeNestedSpecs()
        action.execute(onFailure.get())
    }

    /**
     * Check if this spec has been configured (at minimum, image block is present)
     */
    boolean isConfigured() {
        if (!image.isPresent()) {
            return false
        }
        def img = image.get()
        return img.name.isPresent() ||
               img.sourceRef.isPresent() ||
               img.sourceRefImageName.isPresent() ||
               img.sourceRefRepository.isPresent()
    }
}
```

#### `ProjectImageSpec.groovy`

```groovy
package com.kineticfire.gradle.docker.spec.project

import com.kineticfire.gradle.docker.spec.AuthSpec
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Nested

import javax.inject.Inject

/**
 * Simplified image configuration for dockerProject DSL.
 *
 * Supports two modes:
 * - Build Mode: build image from Dockerfile (name, dockerfile, jarFrom/contextDir)
 * - SourceRef Mode: use existing image (sourceRef or component properties)
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses @Inject for Gradle service injection.
 * All properties use Provider API with proper @Input/@Optional annotations.
 */
abstract class ProjectImageSpec {

    private final ObjectFactory objectFactory

    @Inject
    ProjectImageSpec(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory

        // Build mode conventions
        dockerfile.convention('src/main/docker/Dockerfile')
        buildArgs.convention([:])
        labels.convention([:])

        // SourceRef mode conventions
        sourceRef.convention('')
        sourceRefRegistry.convention('')
        sourceRefNamespace.convention('')
        sourceRefImageName.convention('')
        sourceRefTag.convention('')
        sourceRefRepository.convention('')
        pullIfMissing.convention(false)

        // Common conventions
        registry.convention('')
        namespace.convention('')
        tags.convention(['latest'])
        
        // Version property - empty by default, derived from first non-'latest' tag if not set
        version.convention('')
    }

    // === BUILD MODE PROPERTIES ===

    /**
     * Image name (e.g., 'my-app')
     */
    @Input
    @Optional
    abstract Property<String> getName()

    /**
     * Tags to apply to the built image (e.g., ['latest', '1.0.0'])
     */
    @Input
    abstract ListProperty<String> getTags()
    
    /**
     * Image version (e.g., '1.0.0').
     * If not explicitly set, derived from the first non-'latest' tag.
     * Maps to ImageSpec.version in the underlying docker DSL.
     */
    @Input
    @Optional
    abstract Property<String> getVersion()

    /**
     * Path to Dockerfile relative to project root.
     * Default: 'src/main/docker/Dockerfile'
     */
    @Input
    @Optional
    abstract Property<String> getDockerfile()

    /**
     * Task path that produces a JAR to include in context (e.g., ':app:jar').
     * When specified, auto-creates a Copy task that:
     * - Copies the Dockerfile to build context
     * - Copies the JAR as 'app.jar' to build context
     */
    @Input
    @Optional
    abstract Property<String> getJarFrom()

    /**
     * Alternative to jarFrom: specify a directory as build context.
     * Mutually exclusive with jarFrom.
     */
    @Input
    @Optional
    abstract Property<String> getContextDir()

    /**
     * Build arguments to pass to docker build
     */
    @Input
    abstract MapProperty<String, String> getBuildArgs()

    /**
     * Labels to apply to the built image.
     * Maps to ImageSpec.labels in the underlying docker DSL.
     */
    @Input
    abstract MapProperty<String, String> getLabels()

    // === SOURCE REF MODE PROPERTIES ===

    /**
     * Full source image reference (e.g., 'docker.io/library/nginx:1.25').
     * When specified, skips build and uses this existing image.
     */
    @Input
    @Optional
    abstract Property<String> getSourceRef()

    /**
     * SourceRef component: registry (e.g., 'docker.io')
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefRegistry()

    /**
     * SourceRef component: namespace (e.g., 'library')
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefNamespace()

    /**
     * SourceRef component: image name (e.g., 'nginx')
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefImageName()

    /**
     * SourceRef component: tag (e.g., '1.25')
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefTag()

    /**
     * SourceRef component: repository (e.g., 'library/nginx')
     * Alternative to namespace+imageName
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefRepository()

    /**
     * Whether to pull the image if not present locally.
     * Only applies in sourceRef mode.
     */
    @Input
    abstract Property<Boolean> getPullIfMissing()

    // === COMMON PROPERTIES ===

    /**
     * Target registry for publishing (e.g., 'docker.io')
     */
    @Input
    @Optional
    abstract Property<String> getRegistry()

    /**
     * Target namespace for publishing (e.g., 'myorg')
     */
    @Input
    @Optional
    abstract Property<String> getNamespace()

    /**
     * Authentication for pulling images (sourceRef mode with private registries)
     */
    @Nested
    @Optional
    AuthSpec pullAuth

    // === DSL METHODS ===

    void pullAuth(@DelegatesTo(AuthSpec) Closure closure) {
        if (!pullAuth) {
            pullAuth = objectFactory.newInstance(AuthSpec)
        }
        closure.delegate = pullAuth
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void pullAuth(Action<AuthSpec> action) {
        if (!pullAuth) {
            pullAuth = objectFactory.newInstance(AuthSpec)
        }
        action.execute(pullAuth)
    }

    /**
     * Check if this spec is in sourceRef mode
     */
    boolean isSourceRefMode() {
        return (sourceRef.isPresent() && !sourceRef.get().isEmpty()) ||
               (sourceRefRepository.isPresent() && !sourceRefRepository.get().isEmpty()) ||
               (sourceRefImageName.isPresent() && !sourceRefImageName.get().isEmpty())
    }

    /**
     * Check if this spec is in build mode
     */
    boolean isBuildMode() {
        return !isSourceRefMode() && (
            (jarFrom.isPresent() && !jarFrom.get().isEmpty()) ||
            (contextDir.isPresent() && !contextDir.get().isEmpty())
        )
    }
    
    /**
     * Derive version from tags if not explicitly set.
     * Returns the first non-'latest' tag, or empty string if none found.
     */
    String deriveVersion() {
        if (version.isPresent() && !version.get().isEmpty()) {
            return version.get()
        }
        def tagList = tags.getOrElse([])
        def nonLatestTag = tagList.find { it != 'latest' }
        return nonLatestTag ?: ''
    }
}
```

#### `ProjectTestSpec.groovy`

```groovy
package com.kineticfire.gradle.docker.spec.project

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject

/**
 * Simplified test configuration for dockerProject DSL.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses @Inject for ObjectFactory injection.
 * All properties use Provider API with proper @Input/@Optional annotations.
 */
abstract class ProjectTestSpec {

    @Inject
    ProjectTestSpec(ObjectFactory objectFactory) {
        lifecycle.convention('class')
        timeoutSeconds.convention(60)
        pollSeconds.convention(2)
        testTaskName.convention('integrationTest')
        waitForHealthy.convention([])
        waitForRunning.convention([])
    }

    /**
     * Path to Docker Compose file relative to project root.
     * (e.g., 'src/integrationTest/resources/compose/app.yml')
     */
    @Input
    @Optional
    abstract Property<String> getCompose()

    /**
     * Services to wait for healthy status before running tests.
     * (e.g., ['app', 'db'])
     */
    @Input
    abstract ListProperty<String> getWaitForHealthy()

    /**
     * Services to wait for running status before running tests.
     * Alternative to waitForHealthy for services without health checks.
     */
    @Input
    abstract ListProperty<String> getWaitForRunning()

    /**
     * Container lifecycle mode: 'class' or 'method'.
     * - 'class': containers start once per test class (default)
     * - 'method': containers restart for each test method
     * 
     * When 'method' is selected, the translator automatically configures
     * delegateStackManagement=true in the workflow's TestStepSpec, enabling
     * the test framework extension to control container lifecycle.
     */
    @Input
    abstract Property<String> getLifecycle()

    /**
     * Timeout in seconds for waiting on containers.
     * Default: 60
     */
    @Input
    abstract Property<Integer> getTimeoutSeconds()

    /**
     * Poll interval in seconds when waiting for containers.
     * Default: 2
     */
    @Input
    abstract Property<Integer> getPollSeconds()

    /**
     * Compose project name. If not specified, derived from project name.
     */
    @Input
    @Optional
    abstract Property<String> getProjectName()

    /**
     * Name of the test task to execute.
     * Default: 'integrationTest'
     */
    @Input
    abstract Property<String> getTestTaskName()
}
```

#### `ProjectSuccessSpec.groovy`

```groovy
package com.kineticfire.gradle.docker.spec.project

import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject

/**
 * Simplified success operations configuration for dockerProject DSL.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses @Inject for service injection.
 * All properties use Provider API with proper @Input/@Optional annotations.
 *
 * NOTE: This class uses SaveCompression enum from com.kineticfire.gradle.docker.model.
 * This matches the enum used in SaveSpec for consistency.
 */
abstract class ProjectSuccessSpec {

    private final ObjectFactory objectFactory
    private final ProjectLayout layout

    @Inject
    ProjectSuccessSpec(ObjectFactory objectFactory, ProjectLayout layout) {
        this.objectFactory = objectFactory
        this.layout = layout

        additionalTags.convention([])
        saveCompression.convention('')  // Empty means use SaveSpec default (NONE)
        publishTags.convention([])
    }

    /**
     * Additional tags to apply when tests pass.
     * These are ADDED to the base tags, not replacing them.
     */
    @Input
    abstract ListProperty<String> getAdditionalTags()

    /**
     * Path to save the image as a tar file.
     * Compression is inferred from the file extension:
     * - .tar.gz, .tgz -> GZIP
     * - .tar.bz2, .tbz2 -> BZIP2
     * - .tar.xz, .txz -> XZ
     * - .tar -> NONE
     * - .zip -> ZIP
     */
    @Input
    @Optional
    abstract Property<String> getSaveFile()

    /**
     * Explicit compression override. Only needed if filename doesn't have
     * a recognized extension. Values: 'none', 'gzip', 'bzip2', 'xz', 'zip'
     */
    @Input
    @Optional
    abstract Property<String> getSaveCompression()

    // === PUBLISH PROPERTIES ===

    /**
     * Registry to publish to (e.g., 'registry.example.com')
     */
    @Input
    @Optional
    abstract Property<String> getPublishRegistry()

    /**
     * Namespace to publish to (e.g., 'myproject')
     */
    @Input
    @Optional
    abstract Property<String> getPublishNamespace()

    /**
     * Tags to publish. If not specified, uses additionalTags.
     */
    @Input
    abstract ListProperty<String> getPublishTags()

    // === DSL METHODS ===

    /**
     * Shorthand for setting saveFile
     */
    void save(String filePath) {
        saveFile.set(filePath)
    }

    /**
     * Extended save with explicit compression
     */
    void save(Map<String, String> args) {
        if (args.file) {
            saveFile.set(args.file)
        }
        if (args.compression) {
            saveCompression.set(args.compression)
        }
    }

    /**
     * Infer SaveCompression from filename extension.
     * Uses SaveCompression enum from com.kineticfire.gradle.docker.model package.
     *
     * Logic:
     * 1. If saveCompression is explicitly set (non-empty), use it
     * 2. Otherwise, infer compression from the filename extension
     * 3. The convention is empty string (''), meaning "infer from filename"
     *
     * NOTE: The underlying SaveSpec uses SaveCompression.NONE as its default.
     * This spec uses filename inference as the primary mechanism, which is more user-friendly.
     */
    SaveCompression inferCompression() {
        if (!saveFile.isPresent()) {
            return null
        }

        def filename = saveFile.get()

        // Check explicit override first - if user explicitly set compression, use it.
        // Empty string ('') is the convention, meaning "infer from filename".
        if (saveCompression.isPresent() && !saveCompression.get().isEmpty()) {
            return parseCompression(saveCompression.get())
        }

        // Infer from filename (this is the common case)
        if (filename.endsWith('.tar.gz') || filename.endsWith('.tgz')) {
            return SaveCompression.GZIP
        } else if (filename.endsWith('.tar.bz2') || filename.endsWith('.tbz2')) {
            return SaveCompression.BZIP2
        } else if (filename.endsWith('.tar.xz') || filename.endsWith('.txz')) {
            return SaveCompression.XZ
        } else if (filename.endsWith('.zip')) {
            return SaveCompression.ZIP
        } else if (filename.endsWith('.tar')) {
            return SaveCompression.NONE
        } else {
            throw new GradleException(
                "Cannot infer compression from filename '${filename}'. " +
                "Use one of: .tar.gz, .tgz, .tar.bz2, .tbz2, .tar.xz, .txz, .tar, .zip " +
                "or specify compression explicitly: save file: '...', compression: 'gzip'"
            )
        }
    }

    private SaveCompression parseCompression(String compression) {
        switch (compression.toLowerCase()) {
            case 'none': return SaveCompression.NONE
            case 'gzip': return SaveCompression.GZIP
            case 'bzip2': return SaveCompression.BZIP2
            case 'xz': return SaveCompression.XZ
            case 'zip': return SaveCompression.ZIP
            default:
                throw new GradleException(
                    "Unknown compression type '${compression}'. " +
                    "Valid values: none, gzip, bzip2, xz, zip"
                )
        }
    }
}
```

#### `ProjectFailureSpec.groovy`

```groovy
package com.kineticfire.gradle.docker.spec.project

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input

import javax.inject.Inject

/**
 * Simplified failure operations configuration for dockerProject DSL.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses @Inject for ObjectFactory injection.
 * All properties use Provider API with proper @Input annotations.
 */
abstract class ProjectFailureSpec {

    @Inject
    ProjectFailureSpec(ObjectFactory objectFactory) {
        additionalTags.convention([])
    }

    /**
     * Additional tags to apply when tests fail (e.g., ['failed', 'needs-review'])
     */
    @Input
    abstract ListProperty<String> getAdditionalTags()
}
```

---

### Phase 2: Extension Class

#### New File

| File | Purpose |
|------|---------|
| `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerProjectExtension.groovy` | Extension providing the `dockerProject { }` DSL |

#### `DockerProjectExtension.groovy`

```groovy
package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.spec.project.DockerProjectSpec
import com.kineticfire.gradle.docker.spec.project.ProjectImageSpec
import com.kineticfire.gradle.docker.spec.project.ProjectTestSpec
import com.kineticfire.gradle.docker.spec.project.ProjectSuccessSpec
import com.kineticfire.gradle.docker.spec.project.ProjectFailureSpec
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.file.ProjectLayout

import javax.inject.Inject

/**
 * Extension providing the dockerProject { } simplified DSL.
 *
 * This extension provides a high-level facade that internally translates
 * to docker, dockerOrch, and dockerWorkflows configurations.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses ObjectFactory.newInstance() for spec creation,
 * allowing Gradle to inject services automatically. The nested specs are initialized
 * lazily via initializeNestedSpecs() to work with abstract Property fields.
 */
abstract class DockerProjectExtension {

    private final DockerProjectSpec spec

    @Inject
    DockerProjectExtension(ObjectFactory objectFactory) {
        // Let Gradle inject services into DockerProjectSpec via ObjectFactory
        this.spec = objectFactory.newInstance(DockerProjectSpec)
        // Initialize nested specs after the spec is created
        this.spec.initializeNestedSpecs()
    }

    DockerProjectSpec getSpec() {
        return spec
    }

    // DSL methods with Closure support (Groovy DSL compatibility)
    void image(@DelegatesTo(ProjectImageSpec) Closure closure) {
        spec.image(closure)
    }

    void image(Action<ProjectImageSpec> action) {
        spec.image(action)
    }

    void test(@DelegatesTo(ProjectTestSpec) Closure closure) {
        spec.test(closure)
    }

    void test(Action<ProjectTestSpec> action) {
        spec.test(action)
    }

    void onSuccess(@DelegatesTo(ProjectSuccessSpec) Closure closure) {
        spec.onSuccess(closure)
    }

    void onSuccess(Action<ProjectSuccessSpec> action) {
        spec.onSuccess(action)
    }

    void onFailure(@DelegatesTo(ProjectFailureSpec) Closure closure) {
        spec.onFailure(closure)
    }

    void onFailure(Action<ProjectFailureSpec> action) {
        spec.onFailure(action)
    }
}
```

---

### Phase 3: Translator Service

#### New File

| File | Purpose |
|------|---------|
| `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/DockerProjectTranslator.groovy` | Translates `DockerProjectSpec` -> existing DSL configurations |

This is the **key class** that does the heavy lifting. It reads the simplified spec and configures:

1. `docker.images { }` with appropriate ImageSpec
2. `dockerOrch.composeStacks { }` with appropriate ComposeStackSpec
3. `dockerWorkflows.pipelines { }` with appropriate PipelineSpec
4. Creates the `contextTask` (Copy task) if `jarFrom` is specified
5. Wires task dependencies automatically

#### `DockerProjectTranslator.groovy`

```groovy
package com.kineticfire.gradle.docker.service

import com.kineticfire.gradle.docker.extension.DockerExtension
import com.kineticfire.gradle.docker.extension.DockerOrchExtension
import com.kineticfire.gradle.docker.extension.DockerWorkflowsExtension
import com.kineticfire.gradle.docker.spec.project.DockerProjectSpec
import com.kineticfire.gradle.docker.spec.project.ProjectImageSpec
import com.kineticfire.gradle.docker.spec.project.ProjectTestSpec
import com.kineticfire.gradle.docker.spec.project.ProjectSuccessSpec
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.Copy

/**
 * Translates dockerProject spec into docker, dockerOrch, and dockerWorkflows configurations.
 *
 * This translator implements the "facade pattern" where the simplified dockerProject DSL
 * is translated into the full three-DSL configuration that the plugin already supports.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: This translator runs during CONFIGURATION TIME only,
 * within the plugin's existing afterEvaluate block. It does NOT run at execution time.
 * All task dependency wiring uses provider-based lazy configuration via tasks.named().configure()
 * instead of additional afterEvaluate blocks.
 *
 * EXECUTION ORDER NOTE: This translator is called from the plugin's second afterEvaluate block
 * (in configureAfterEvaluation), AFTER the first afterEvaluate (in registerTaskCreationRules)
 * has already registered tasks. This ensures all referenced tasks exist when wiring dependencies.
 */
class DockerProjectTranslator {

    /**
     * Translate the dockerProject spec into the underlying DSL configurations.
     *
     * IMPORTANT: This method runs during configuration time (within afterEvaluate).
     * It configures extensions and wires task dependencies using provider-based patterns.
     *
     * @param project The Gradle project (configuration-time only)
     * @param projectSpec The dockerProject specification
     * @param dockerExt The docker extension to configure
     * @param dockerOrchExt The dockerOrch extension to configure
     * @param dockerWorkflowsExt The dockerWorkflows extension to configure
     */
    void translate(Project project, DockerProjectSpec projectSpec,
                   DockerExtension dockerExt, DockerOrchExtension dockerOrchExt,
                   DockerWorkflowsExtension dockerWorkflowsExt) {

        def imageSpec = projectSpec.image.get()
        def testSpec = projectSpec.test.get()
        def successSpec = projectSpec.onSuccess.get()
        def failureSpec = projectSpec.onFailure.get()

        // Validate configuration and extension availability
        validateSpec(projectSpec, dockerExt, dockerOrchExt, dockerWorkflowsExt)

        // Generate sanitized names for internal use
        def imageName = deriveImageName(imageSpec, project)
        def sanitizedName = sanitizeName(imageName)
        def stackName = "${sanitizedName}Test"
        def pipelineName = "${sanitizedName}Pipeline"

        // 1. Configure docker.images
        configureDockerImage(project, dockerExt, imageSpec, sanitizedName)

        // 2. Configure dockerOrch.composeStacks (if test block is configured)
        if (testSpec.compose.isPresent()) {
            configureComposeStack(project, dockerOrchExt, testSpec, stackName)
        }

        // 3. Configure dockerWorkflows.pipelines
        configurePipeline(project, dockerWorkflowsExt, dockerExt, dockerOrchExt,
                          sanitizedName, stackName, pipelineName, testSpec, successSpec, failureSpec)

        // 4. Configure task dependencies using provider-based wiring (no afterEvaluate)
        configureTaskDependencies(project, sanitizedName, stackName, testSpec)

        project.logger.lifecycle("dockerProject: Configured image '${imageName}' with pipeline '${pipelineName}'")
    }

    private void validateSpec(DockerProjectSpec projectSpec, DockerExtension dockerExt,
                              DockerOrchExtension dockerOrchExt, DockerWorkflowsExtension dockerWorkflowsExt) {
        // Validate all three extensions are available
        // This should always be true since the plugin registers all three, but defensive coding
        if (dockerExt == null) {
            throw new GradleException(
                "dockerProject requires the 'docker' extension to be registered. " +
                "Ensure the com.kineticfire.gradle.docker plugin is applied."
            )
        }
        if (dockerOrchExt == null) {
            throw new GradleException(
                "dockerProject requires the 'dockerOrch' extension to be registered. " +
                "Ensure the com.kineticfire.gradle.docker plugin is applied."
            )
        }
        if (dockerWorkflowsExt == null) {
            throw new GradleException(
                "dockerProject requires the 'dockerWorkflows' extension to be registered. " +
                "Ensure the com.kineticfire.gradle.docker plugin is applied."
            )
        }

        def imageSpec = projectSpec.image.get()

        // Must have either build mode or sourceRef mode configured
        if (!imageSpec.isBuildMode() && !imageSpec.isSourceRefMode()) {
            throw new GradleException(
                "dockerProject.image must specify either build properties (jarFrom or contextDir) " +
                "or sourceRef properties (sourceRef, sourceRefImageName, or sourceRefRepository)"
            )
        }

        // Cannot mix build mode and sourceRef mode
        if (imageSpec.isBuildMode() && imageSpec.isSourceRefMode()) {
            throw new GradleException(
                "dockerProject.image cannot mix build mode (jarFrom/contextDir) with sourceRef mode"
            )
        }

        // Cannot specify both jarFrom and contextDir
        def hasJarFrom = imageSpec.jarFrom.isPresent() && !imageSpec.jarFrom.get().isEmpty()
        def hasContextDir = imageSpec.contextDir.isPresent() && !imageSpec.contextDir.get().isEmpty()
        if (hasJarFrom && hasContextDir) {
            throw new GradleException(
                "dockerProject.image cannot specify both jarFrom and contextDir - use one or the other"
            )
        }
        
        // Validate mutual exclusivity: check for conflicting direct docker{} configuration
        def derivedImageName = imageSpec.name.isPresent() ? imageSpec.name.get() : ''
        def sanitizedName = sanitizeName(derivedImageName)
        if (!sanitizedName.isEmpty() && dockerExt.images.findByName(sanitizedName) != null) {
            throw new GradleException(
                "Image '${sanitizedName}' is already configured in docker.images { }. " +
                "Cannot use dockerProject { } and docker { } to configure the same image. " +
                "Use either dockerProject { } (simplified) OR docker { } (advanced), not both."
            )
        }
    }

    private String deriveImageName(ProjectImageSpec imageSpec, Project project) {
        if (imageSpec.name.isPresent() && !imageSpec.name.get().isEmpty()) {
            return imageSpec.name.get()
        }
        if (imageSpec.sourceRefImageName.isPresent() && !imageSpec.sourceRefImageName.get().isEmpty()) {
            return imageSpec.sourceRefImageName.get()
        }
        // Fall back to project name
        return project.name
    }

    /**
     * Sanitize name for use in task names and container names.
     * Reuses the same sanitization pattern as existing plugin code.
     */
    private String sanitizeName(String name) {
        if (name == null || name.isEmpty()) {
            return ''
        }
        // Remove special characters, convert to camelCase-safe format
        return name.replaceAll('[^a-zA-Z0-9]', '')
                   .replaceFirst('^([A-Z])', { it[0].toLowerCase() })
    }

    private void configureDockerImage(Project project, DockerExtension dockerExt,
                                       ProjectImageSpec imageSpec, String sanitizedName) {
        // IMPORTANT: ImageSpec has two different 'name' concepts:
        // 1. ImageSpec.name (String field) - internal identifier, set via images.create(sanitizedName)
        // 2. ImageSpec.imageName (Property<String>) - Docker image name for tagging
        //
        // The sanitizedName passed to create() becomes ImageSpec.name (internal).
        // ProjectImageSpec.name maps to ImageSpec.imageName (the Docker image name).
        dockerExt.images.create(sanitizedName) { image ->
            // Common properties - use .set() for Property<T> types
            // Map ProjectImageSpec.name -> ImageSpec.imageName (the Docker image name)
            //
            // IMPORTANT: In build mode, imageName MUST be set. If ProjectImageSpec.name is not
            // provided, we derive it from project.name (done in deriveImageName()).
            // The sanitizedName is used for ImageSpec.name (internal identifier),
            // while the derived image name goes to ImageSpec.imageName (Docker image name).
            def derivedImageName = deriveImageName(imageSpec, project)
            if (imageSpec.name.isPresent() && !imageSpec.name.get().isEmpty()) {
                image.imageName.set(imageSpec.name)
            } else {
                // Fallback: use derived name (from project.name) as the Docker image name
                image.imageName.set(derivedImageName)
            }
            image.tags.set(imageSpec.tags)
            image.registry.set(imageSpec.registry)
            image.namespace.set(imageSpec.namespace)
            image.buildArgs.putAll(imageSpec.buildArgs)
            image.labels.putAll(imageSpec.labels)

            // Map version - derive from tags if not explicitly set
            def derivedVersion = imageSpec.deriveVersion()
            if (!derivedVersion.isEmpty()) {
                image.version.set(derivedVersion)
            }

            if (imageSpec.isBuildMode()) {
                // Build mode configuration
                if (imageSpec.jarFrom.isPresent() && !imageSpec.jarFrom.get().isEmpty()) {
                    def contextTaskProvider = createContextTask(project, sanitizedName, imageSpec)
                    image.contextTask = contextTaskProvider
                    // Also set contextTaskName for configuration cache compatibility
                    image.contextTaskName.set("prepare${sanitizedName.capitalize()}Context")
                } else if (imageSpec.contextDir.isPresent()) {
                    image.context.set(project.file(imageSpec.contextDir.get()))
                }

                if (imageSpec.dockerfile.isPresent()) {
                    image.dockerfile.set(project.file(imageSpec.dockerfile.get()))
                }
            } else {
                // SourceRef mode configuration
                if (imageSpec.sourceRef.isPresent() && !imageSpec.sourceRef.get().isEmpty()) {
                    image.sourceRef.set(imageSpec.sourceRef)
                } else {
                    image.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
                    image.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
                    image.sourceRefImageName.set(imageSpec.sourceRefImageName)
                    image.sourceRefTag.set(imageSpec.sourceRefTag)
                    image.sourceRefRepository.set(imageSpec.sourceRefRepository)
                }

                image.pullIfMissing.set(imageSpec.pullIfMissing)

                if (imageSpec.pullAuth != null) {
                    image.pullAuth {
                        username.set(imageSpec.pullAuth.username)
                        password.set(imageSpec.pullAuth.password)
                    }
                }
            }
        }
    }

    private def createContextTask(Project project, String imageName, ProjectImageSpec imageSpec) {
        def taskName = "prepare${imageName.capitalize()}Context"
        def jarTaskPath = imageSpec.jarFrom.get()

        // Validate jarFrom task path exists before attempting to resolve
        validateJarTaskPath(project, jarTaskPath)

        return project.tasks.register(taskName, Copy) { task ->
            task.group = 'docker'
            task.description = "Prepare Docker build context for ${imageName}"
            task.into(project.layout.buildDirectory.dir("docker-context/${imageName}"))

            // Copy Dockerfile
            def dockerfilePath = imageSpec.dockerfile.getOrElse('src/main/docker/Dockerfile')
            def dockerfileFile = project.file(dockerfilePath)
            task.from(dockerfileFile.parentFile) { spec ->
                spec.include(dockerfileFile.name)
            }

            // Copy JAR from specified task
            // Handle various formats:
            // - 'jar' -> current project's jar task
            // - ':jar' -> root project's jar task
            // - ':app:jar' -> subproject :app's jar task
            // - ':sub:project:jar' -> nested subproject's jar task
            //
            // PARSING APPROACH: Split by ':' and analyze the resulting parts.
            // This is clearer than complex string manipulation and handles all edge cases.
            def jarTaskName
            def jarProject

            if (!jarTaskPath.contains(':')) {
                // Case 1: Simple task name like 'jar' - use current project
                jarTaskName = jarTaskPath
                jarProject = project
            } else if (jarTaskPath.startsWith(':')) {
                // Starts with ':' - either root project task or subproject task
                def pathWithoutLeadingColon = jarTaskPath.substring(1)
                def lastColonIndex = pathWithoutLeadingColon.lastIndexOf(':')
                
                if (lastColonIndex == -1) {
                    // Case 2: Root project reference like ':jar' (no more colons after first)
                    jarTaskName = pathWithoutLeadingColon
                    jarProject = project.rootProject
                } else {
                    // Case 3: Subproject reference like ':app:jar' or ':sub:project:jar'
                    jarTaskName = pathWithoutLeadingColon.substring(lastColonIndex + 1)
                    def projectPath = ':' + pathWithoutLeadingColon.substring(0, lastColonIndex)
                    jarProject = project.project(projectPath)
                }
            } else {
                // Invalid format like 'foo:jar' - should have been caught by validation
                throw new GradleException(
                    "Invalid jarFrom format '${jarTaskPath}'. " +
                    "Task paths must start with ':' for cross-project references or be a simple task name."
                )
            }

            def jarTask = jarProject.tasks.named(jarTaskName)
            task.from(jarTask.flatMap { it.archiveFile }) { spec ->
                spec.rename { 'app.jar' }
            }

            task.dependsOn(jarTaskPath)
        }
    }

    /**
     * Validate that the jarFrom task path references an existing project and task.
     * Provides clear error messages for common misconfigurations.
     *
     * Supported formats:
     * - 'jar' -> current project's jar task
     * - ':jar' -> root project's jar task
     * - ':app:jar' -> subproject :app's jar task
     * - ':sub:project:jar' -> nested subproject's jar task
     */
    private void validateJarTaskPath(Project project, String jarTaskPath) {
        // Validate required plugins are applied
        if (!project.plugins.hasPlugin('java') && !project.plugins.hasPlugin('groovy')) {
            throw new GradleException(
                "dockerProject.image.jarFrom requires 'java' or 'groovy' plugin to be applied. " +
                "Add: plugins { id 'java' } or plugins { id 'groovy' } to your build.gradle"
            )
        }

        // Validate jarTaskPath is not empty
        if (jarTaskPath == null || jarTaskPath.trim().isEmpty()) {
            throw new GradleException(
                "dockerProject.image.jarFrom cannot be empty. " +
                "Provide a valid task path like 'jar', ':jar', or ':app:jar'"
            )
        }

        // Validate format - should end with a task name, not a colon
        if (jarTaskPath.endsWith(':')) {
            throw new GradleException(
                "dockerProject.image.jarFrom '${jarTaskPath}' is invalid. " +
                "Task path must end with a task name, not a colon."
            )
        }

        // Validate format - cross-project references must start with ':'
        if (jarTaskPath.contains(':') && !jarTaskPath.startsWith(':')) {
            throw new GradleException(
                "dockerProject.image.jarFrom '${jarTaskPath}' is invalid. " +
                "Cross-project task paths must start with ':'. " +
                "Use ':${jarTaskPath}' or a simple task name like 'jar'."
            )
        }

        // Validate cross-project references - check project exists
        if (jarTaskPath.startsWith(':') && jarTaskPath.indexOf(':', 1) > 0) {
            // Has colon after the first one, so it's a subproject reference like ':app:jar'
            def pathWithoutLeadingColon = jarTaskPath.substring(1)
            def lastColonIndex = pathWithoutLeadingColon.lastIndexOf(':')
            def projectPath = ':' + pathWithoutLeadingColon.substring(0, lastColonIndex)

            try {
                project.project(projectPath)
            } catch (Exception e) {
                throw new GradleException(
                    "dockerProject.image.jarFrom references non-existent project '${projectPath}'. " +
                    "Verify the project path in your jarFrom setting: '${jarTaskPath}'. " +
                    "Available subprojects: ${project.rootProject.subprojects.collect { it.path }}"
                )
            }
        }
        // Note: Task existence is validated lazily by Gradle when tasks.named() is called
    }

    private void configureComposeStack(Project project, DockerOrchExtension dockerOrchExt,
                                        ProjectTestSpec testSpec, String stackName) {
        dockerOrchExt.composeStacks.create(stackName) { stack ->
            // Resolve compose file path and add to files collection
            stack.files.from(project.file(testSpec.compose.get()))

            if (testSpec.projectName.isPresent()) {
                stack.projectName.set(testSpec.projectName)
            } else {
                stack.projectName.set("${project.name}-${stackName}")
            }

            if (testSpec.waitForHealthy.isPresent() && !testSpec.waitForHealthy.get().isEmpty()) {
                stack.waitForHealthy {
                    // PROPERTY MAPPING EXPLANATION:
                    // - ProjectTestSpec.waitForHealthy (ListProperty<String>) -> service names to wait for
                    // - WaitSpec.waitForServices (ListProperty<String>) -> same data, different property name
                    // The naming differs because WaitSpec is reused for both waitForHealthy and waitForRunning,
                    // so it uses the generic name "waitForServices" while ProjectTestSpec uses semantic names.
                    waitForServices.set(testSpec.waitForHealthy.get())
                    timeoutSeconds.set(testSpec.timeoutSeconds.get())
                    pollSeconds.set(testSpec.pollSeconds.get())
                }
            }

            if (testSpec.waitForRunning.isPresent() && !testSpec.waitForRunning.get().isEmpty()) {
                stack.waitForRunning {
                    // Same mapping as waitForHealthy above - different wait condition, same WaitSpec structure
                    waitForServices.set(testSpec.waitForRunning.get())
                    timeoutSeconds.set(testSpec.timeoutSeconds.get())
                    pollSeconds.set(testSpec.pollSeconds.get())
                }
            }
        }
    }

    private void configurePipeline(Project project, DockerWorkflowsExtension dockerWorkflowsExt,
                                    DockerExtension dockerExt, DockerOrchExtension dockerOrchExt,
                                    String imageName, String stackName, String pipelineName,
                                    ProjectTestSpec testSpec, ProjectSuccessSpec successSpec,
                                    def failureSpec) {
        // NOTE: PipelineSpec has both onTestSuccess and onSuccess properties.
        // - onTestSuccess: Operations after tests pass (the common case for dockerProject) - HAS CONVENTION
        // - onSuccess: Operations after build succeeds (without test step) - NO CONVENTION SET
        // The dockerProject DSL uses onTestSuccess since it always includes a test step.
        // Both properties are of type Property<SuccessStepSpec> and behave identically when set.
        //
        // CRITICAL: onSuccess has NO convention set in PipelineSpec. If you ever change this code
        // to use pipeline.onSuccess { } instead of pipeline.onTestSuccess { }, you MUST either:
        // 1. Add a convention for onSuccess in PipelineSpec, OR
        // 2. Check onSuccess.isPresent() before calling onSuccess.get() to avoid NPE
        //
        // Current implementation uses onTestSuccess which has a convention, so this is safe.
        dockerWorkflowsExt.pipelines.create(pipelineName) { pipeline ->
            pipeline.description.set("Auto-generated pipeline for ${imageName}")

            // Configure build step - use .set() for Property<T> types
            // NOTE: PipelineSpec currently only has Action<T> methods (not Closure DSL methods).
            // This is intentional - the translator uses Action-style configuration internally.
            // Users of dockerProject DSL use Closure syntax which is handled by the project specs.
            pipeline.build { buildStep ->
                buildStep.image.set(dockerExt.images.getByName(imageName))
            }

            if (testSpec.compose.isPresent()) {
                // Configure test step - use .set() for Property<T> types
                pipeline.test { testStep ->
                    testStep.stack.set(dockerOrchExt.composeStacks.getByName(stackName))
                    testStep.testTaskName.set(testSpec.testTaskName.getOrElse('integrationTest'))
                    
                    def lifecycleValue = parseLifecycle(testSpec.lifecycle.getOrElse('class'))
                    testStep.lifecycle.set(lifecycleValue)
                    
                    // When METHOD lifecycle, delegate stack management to test framework extension
                    if (lifecycleValue == WorkflowLifecycle.METHOD) {
                        testStep.delegateStackManagement.set(true)
                        
                        // VALIDATION WARNING: METHOD lifecycle requires maxParallelForks=1 on the test task
                        // to avoid port conflicts when multiple tests try to start containers simultaneously.
                        // The translator logs a warning here; actual validation happens in PipelineValidator.
                        // Users must ensure their test task has: maxParallelForks = 1
                        project.logger.warn(
                            "dockerProject: Using METHOD lifecycle for pipeline '${pipelineName}'. " +
                            "Ensure test task '${testSpec.testTaskName.getOrElse('integrationTest')}' has maxParallelForks = 1 " +
                            "and test classes use @ComposeUp (Spock) or @ExtendWith(DockerComposeMethodExtension.class) (JUnit 5)."
                        )
                    }
                }
            }

            pipeline.onTestSuccess { successStep ->
                successStep.additionalTags.set(successSpec.additionalTags)

                if (successSpec.saveFile.isPresent()) {
                    successStep.save { saveSpec ->
                        saveSpec.outputFile.set(project.file(successSpec.saveFile.get()))
                        saveSpec.compression.set(successSpec.inferCompression())
                    }
                }

                if (successSpec.publishRegistry.isPresent()) {
                    successStep.publish { publishSpec ->
                        // Determine which tags to publish
                        def tagsToPublish = successSpec.publishTags.isPresent() && 
                            !successSpec.publishTags.get().isEmpty() ?
                            successSpec.publishTags.get() : successSpec.additionalTags.get()
                        publishSpec.publishTags.set(tagsToPublish)
                        
                        // Use PublishSpec.to(String, Action) API to create named target
                        publishSpec.to('default') { target ->
                            target.registry.set(successSpec.publishRegistry)
                            if (successSpec.publishNamespace.isPresent()) {
                                target.namespace.set(successSpec.publishNamespace)
                            }
                        }
                    }
                }
            }

            if (failureSpec.additionalTags.isPresent() && !failureSpec.additionalTags.get().isEmpty()) {
                pipeline.onTestFailure { failureStep ->
                    failureStep.additionalTags.set(failureSpec.additionalTags)
                }
            }
        }
    }

    private WorkflowLifecycle parseLifecycle(String lifecycle) {
        switch (lifecycle.toLowerCase()) {
            case 'class': return WorkflowLifecycle.CLASS
            case 'method': return WorkflowLifecycle.METHOD
            default:
                throw new GradleException(
                    "Unknown lifecycle '${lifecycle}'. Valid values: class, method"
                )
        }
    }

    /**
     * Configure task dependencies using provider-based lazy wiring.
     *
     * GRADLE 9/10 COMPATIBILITY NOTE: This method does NOT use afterEvaluate.
     * Instead, it uses tasks.named().configure() which is configuration-cache safe.
     * This translator is called from the plugin's existing afterEvaluate block,
     * so tasks are already registered at this point.
     */
    private void configureTaskDependencies(Project project, String imageName, String stackName,
                                            ProjectTestSpec testSpec) {
        def capitalizedImage = imageName.capitalize()
        def capitalizedStack = stackName.capitalize()

        if (testSpec.compose.isPresent()) {
            // composeUp depends on dockerBuild - use tasks.named().configure() for lazy wiring
            project.tasks.named("composeUp${capitalizedStack}").configure { task ->
                task.dependsOn("dockerBuild${capitalizedImage}")
            }

            // integrationTest depends on composeUp, finalizedBy composeDown
            def testTaskName = testSpec.testTaskName.getOrElse('integrationTest')
            project.tasks.named(testTaskName).configure { task ->
                task.dependsOn("composeUp${capitalizedStack}")
                task.finalizedBy("composeDown${capitalizedStack}")
            }
        }
    }
}
```

---

### Phase 4: Plugin Integration

#### File to Modify

| File | Change |
|------|--------|
| `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy` | Register `dockerProject` extension and call translator |

#### Understanding the Current Plugin Structure

**CRITICAL**: The current `GradleDockerPlugin` uses THREE separate `afterEvaluate` blocks, each serving
a distinct purpose. Understanding this structure is essential for correct integration.

**Current afterEvaluate Structure (GradleDockerPlugin.groovy):**

```
apply() method:
├── validateRequirements()
├── registerDockerService/ComposeService/JsonService
├── Create extensions: docker, dockerOrch, dockerWorkflows
├── registerTaskCreationRules()  ← Contains FIRST afterEvaluate (line 174)
│   └── afterEvaluate {
│       ├── registerDockerImageTasks()     // Iterates dockerExt.images.all { }
│       └── registerComposeStackTasks()    // Iterates dockerOrchExt.composeStacks.all { }
│   }
├── registerWorkflowTasks()  ← Contains SECOND afterEvaluate (line 305)
│   └── afterEvaluate {
│       └── Register PipelineRunTask for each pipeline
│   }
├── configureAfterEvaluation()  ← Contains THIRD afterEvaluate (line 371)
│   └── afterEvaluate {
│       ├── dockerExt.validate()
│       ├── dockerOrchExt.validate()
│       ├── validatePipelines()
│       └── configureTaskDependencies()
│   }
├── configureCleanupHooks()
├── setupTestIntegration()
└── setupIntegrationTestSourceSet()
```

**Key Insight**: The `afterEvaluate` blocks execute in registration order. For the translator to work:
- Translation MUST happen BEFORE `registerDockerImageTasks()` iterates the images container
- This means translation must be the FIRST thing in the FIRST `afterEvaluate`

#### Changes to `GradleDockerPlugin.groovy`

##### Step 1: Register Extension in apply() Method

Add after existing extension creation (around line 61):

```groovy
// Create dockerProject extension (simplified facade)
// Gradle injects ObjectFactory, ProviderFactory, ProjectLayout automatically via @Inject
def dockerProjectExt = project.extensions.create(
    'dockerProject',
    DockerProjectExtension,
    project.objects  // Only ObjectFactory needed; it injects other services into DockerProjectSpec
)
```

##### Step 2: Update registerTaskCreationRules() Method Signature

The current method signature (line 142):
```groovy
private void registerTaskCreationRules(Project project, DockerExtension dockerExt, DockerOrchExtension dockerOrchExt,
                                     Provider<DockerService> dockerService, Provider<ComposeService> composeService,
                                     Provider<JsonService> jsonService)
```

**Change to** (add two parameters):
```groovy
private void registerTaskCreationRules(Project project, DockerExtension dockerExt, DockerOrchExtension dockerOrchExt,
                                       DockerWorkflowsExtension dockerWorkflowsExt,  // ADD: needed for translator
                                       DockerProjectExtension dockerProjectExt,       // ADD: the new extension
                                       Provider<DockerService> dockerService, Provider<ComposeService> composeService,
                                       Provider<JsonService> jsonService)
```

##### Step 3: Update the Call Site in apply()

Change the call at line 64 from:
```groovy
registerTaskCreationRules(project, dockerExt, dockerOrchExt, dockerService, composeService, jsonService)
```

**To:**
```groovy
registerTaskCreationRules(project, dockerExt, dockerOrchExt, dockerWorkflowsExt, dockerProjectExt,
                          dockerService, composeService, jsonService)
```

##### Step 4: Add Translation Logic to FIRST afterEvaluate

Modify the `afterEvaluate` block in `registerTaskCreationRules()` (currently at line 174):

```groovy
project.afterEvaluate {
    // STEP 1: Translate dockerProject FIRST (before iterating images)
    // This adds images/stacks/pipelines to the existing containers before task registration
    if (dockerProjectExt.spec.isConfigured()) {
        def translator = new DockerProjectTranslator()
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerOrchExt, dockerWorkflowsExt)
    }

    // STEP 2: Now register tasks for ALL images (including translator-created ones)
    // The .all { } callback will fire for both pre-existing AND newly-added items
    registerDockerImageTasks(project, dockerExt, dockerService)
    registerComposeStackTasks(project, dockerOrchExt, composeService, jsonService)
}
```

##### Step 5: Add Required Import

Add to imports section:
```groovy
import com.kineticfire.gradle.docker.service.DockerProjectTranslator
import com.kineticfire.gradle.docker.extension.DockerProjectExtension
```

#### Why This Ordering Works

1. **Configuration Phase**: User's `dockerProject { }` DSL populates `DockerProjectSpec`

2. **First afterEvaluate** (in `registerTaskCreationRules`):
   - Translator runs FIRST, adding images to `dockerExt.images` container
   - Translator also adds stacks to `dockerOrchExt.composeStacks` container
   - Translator also adds pipelines to `dockerWorkflowsExt.pipelines` container
   - `registerDockerImageTasks()` then iterates `dockerExt.images.all { }` - sees ALL images
   - `registerComposeStackTasks()` then iterates `dockerOrchExt.composeStacks.all { }` - sees ALL stacks

3. **Second afterEvaluate** (in `registerWorkflowTasks`):
   - Iterates `dockerWorkflowsExt.pipelines.all { }` - sees translator-created pipelines
   - Registers `PipelineRunTask` for each pipeline

4. **Third afterEvaluate** (in `configureAfterEvaluation`):
   - `dockerExt.validate()` validates ALL images including translator-created ones
   - `dockerOrchExt.validate()` validates ALL stacks including translator-created ones
   - `validatePipelines()` validates ALL pipelines including translator-created ones
   - `configureTaskDependencies()` wires dependencies for ALL tasks

**Key Point**: The `NamedDomainObjectContainer.all { }` callback fires for BOTH existing AND newly-added
items. Since translation happens BEFORE iteration starts (both in the same `afterEvaluate` block),
the iterator sees all items including those just added by the translator.

---

### Phase 5: Testing

#### New Test Files

| File | Purpose |
|------|---------|
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/DockerProjectSpecTest.groovy` | Unit tests for DockerProjectSpec |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectImageSpecTest.groovy` | Unit tests for ProjectImageSpec |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectTestSpecTest.groovy` | Unit tests for ProjectTestSpec |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectSuccessSpecTest.groovy` | Unit tests for ProjectSuccessSpec |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectFailureSpecTest.groovy` | Unit tests for ProjectFailureSpec |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerProjectExtensionTest.groovy` | Unit tests for DockerProjectExtension |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/DockerProjectTranslatorTest.groovy` | Unit tests for DockerProjectTranslator |
| `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/DockerProjectFunctionalTest.groovy` | Functional tests for DSL parsing |

#### Critical Unit Test Requirements for DockerProjectTranslatorTest

The `DockerProjectTranslatorTest` must include tests that verify translator-created images pass
the existing `DockerExtension.validate()` validation. This is critical because the translator
creates images dynamically, and validation runs afterwards in `configureAfterEvaluation()`.

**Required test cases:**

1. **Build mode with jarFrom**: Verify that `contextTask` is set correctly and validation passes
2. **Build mode with contextDir**: Verify that `context` is set correctly and validation passes
3. **SourceRef mode**: Verify that sourceRef properties are set and validation passes
4. **SourceRef mode with pullAuth**: Verify pullAuth is properly configured
5. **Missing required properties**: Verify appropriate errors are thrown during translation
6. **Mutual exclusivity**: Verify error when both jarFrom and contextDir are specified
7. **Conflicting docker{} configuration**: Verify error when same image is configured in both DSLs
8. **Empty additionalTags**: Verify translation works with empty additionalTags list
9. **Missing compose file**: Verify translation works when no test block is configured
10. **jarFrom edge cases**: Test all task path formats (`:jar`, `jar`, `:app:jar`, `:sub:project:jar`)

#### Integration Test Scenarios

Integration tests follow the existing project structure convention established in `plugin-integration-test/`.
The existing conventions are:
- `plugin-integration-test/docker/scenario-<number>/` for docker tasks (build, tag, save, publish)
- `plugin-integration-test/dockerOrch/` for dockerOrch tasks (composeUp, composeDown)
- `plugin-integration-test/dockerWorkflows/` for dockerWorkflows pipelines

For `dockerProject` tests, we create a new directory to match this pattern:

| Directory | Purpose |
|-----------|---------|
| `plugin-integration-test/dockerProject/scenario-1-build-mode/` | Basic build mode: jarFrom, test, additionalTags |
| `plugin-integration-test/dockerProject/scenario-2-sourceref-mode/` | SourceRef mode with component properties |
| `plugin-integration-test/dockerProject/scenario-3-save-publish/` | Save and publish on success |
| `plugin-integration-test/dockerProject/scenario-4-method-lifecycle/` | Method lifecycle mode |
| `plugin-integration-test/dockerProject/scenario-5-contextdir-mode/` | Build mode using contextDir instead of jarFrom |

**NOTE:** A README.md file should be created at `plugin-integration-test/dockerProject/README.md` to document
the scenarios, following the pattern of existing README files in `plugin-integration-test/docker/README.md`
and `plugin-integration-test/dockerOrch/README.md`.

##### Scenario 2: SourceRef Mode with Component Properties

This scenario tests using an existing image via `sourceRef` component properties:

```groovy
// plugin-integration-test/dockerProject/scenario-2-sourceref-mode/build.gradle

plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

dockerProject {
    image {
        // Use existing nginx image via component properties
        sourceRefRegistry.set('docker.io')
        sourceRefNamespace.set('library')
        sourceRefImageName.set('nginx')
        sourceRefTag.set('1.25-alpine')

        // Apply local tags
        tags.set(['test-nginx', 'latest'])

        pullIfMissing.set(true)
    }

    test {
        compose.set('src/integrationTest/resources/compose/nginx.yml')
        waitForHealthy.set(['nginx'])
    }

    onSuccess {
        additionalTags.set(['verified'])
    }
}

dependencies {
    integrationTestImplementation libs.rest.assured
}
```

##### Scenario 5: Build Mode with contextDir

This scenario tests build mode using `contextDir` instead of `jarFrom`:

```groovy
// plugin-integration-test/dockerProject/scenario-5-contextdir-mode/build.gradle

plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

dockerProject {
    image {
        name.set('static-app')
        tags.set(['latest', '1.0.0'])
        dockerfile.set('docker/Dockerfile')
        contextDir.set('docker')  // Use existing directory as context (alternative to jarFrom)
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
    }

    onSuccess {
        additionalTags.set(['tested'])
    }
}

dependencies {
    integrationTestImplementation libs.rest.assured
}
```

This scenario verifies that `contextDir` works as an alternative to `jarFrom` for cases where:
- The Docker context already exists (no JAR to copy)
- Static assets or pre-built artifacts are used
- The context directory is managed externally

---

### Phase 6: Documentation

#### Files to Create/Update

| File | Purpose |
|------|---------|
| `docs/usage/usage-docker-project.md` | New usage guide for simplified DSL |
| `docs/usage/usage-docker.md` | Add reference to dockerProject as simplified alternative |
| `docs/usage/usage-docker-orch.md` | Add reference to dockerProject |
| `docs/usage/usage-docker-workflows.md` | Add reference to dockerProject |

#### Key Documentation Topics

1. **When to use dockerProject vs the three-DSL approach**
2. **Build mode vs SourceRef mode**
3. **Save file compression inference from filename**
4. **Task wiring that happens automatically**
5. **Migration guide from three-DSL to dockerProject**

---

## File Summary

### New Files (22)

| Directory | File | Purpose |
|-----------|------|---------|
| `plugin/.../spec/project/` | `DockerProjectSpec.groovy` | Top-level spec |
| `plugin/.../spec/project/` | `ProjectImageSpec.groovy` | Image configuration |
| `plugin/.../spec/project/` | `ProjectTestSpec.groovy` | Test configuration |
| `plugin/.../spec/project/` | `ProjectSuccessSpec.groovy` | Success operations |
| `plugin/.../spec/project/` | `ProjectFailureSpec.groovy` | Failure operations |
| `plugin/.../extension/` | `DockerProjectExtension.groovy` | DSL extension |
| `plugin/.../service/` | `DockerProjectTranslator.groovy` | Translation logic |
| `plugin/.../test/.../spec/project/` | `DockerProjectSpecTest.groovy` | Unit tests |
| `plugin/.../test/.../spec/project/` | `ProjectImageSpecTest.groovy` | Unit tests |
| `plugin/.../test/.../spec/project/` | `ProjectTestSpecTest.groovy` | Unit tests |
| `plugin/.../test/.../spec/project/` | `ProjectSuccessSpecTest.groovy` | Unit tests |
| `plugin/.../test/.../spec/project/` | `ProjectFailureSpecTest.groovy` | Unit tests |
| `plugin/.../test/.../extension/` | `DockerProjectExtensionTest.groovy` | Extension tests |
| `plugin/.../test/.../service/` | `DockerProjectTranslatorTest.groovy` | Translator tests |
| `plugin/.../functionalTest/` | `DockerProjectFunctionalTest.groovy` | Functional tests |
| `plugin-integration-test/dockerProject/` | `scenario-1-build-mode/` | Build mode integration test |
| `plugin-integration-test/dockerProject/` | `scenario-2-sourceref-mode/` | SourceRef mode integration test |
| `plugin-integration-test/dockerProject/` | `scenario-3-save-publish/` | Save/publish integration test |
| `plugin-integration-test/dockerProject/` | `scenario-4-method-lifecycle/` | Method lifecycle integration test |
| `plugin-integration-test/dockerProject/` | `scenario-5-contextdir-mode/` | Build mode with contextDir integration test |
| `plugin-integration-test/dockerProject/` | `README.md` | Scenario documentation |
| `docs/usage/` | `usage-docker-project.md` | New usage guide for simplified DSL |

### Modified Files (5 required + 2 optional)

| File | Changes | Required? |
|------|---------|-----------|
| `GradleDockerPlugin.groovy` | Register extension, call translator | **Required** |
| `docs/usage/usage-docker.md` | Cross-reference to dockerProject | **Required** |
| `docs/usage/usage-docker-orch.md` | Cross-reference to dockerProject | **Required** |
| `docs/usage/usage-docker-workflows.md` | Cross-reference to dockerProject | **Required** |
| `docs/design-docs/project-reviews/2025-12-06-project-review.md` | Mark recommendation as completed | **Required** |
| `PipelineSpec.groovy` | Add conventions for onSuccess and onFailure | Optional (recommended) |
| `WaitSpec.groovy` | Add convention for waitForServices | Optional (defensive coding) |

---

## Key Design Decisions

1. **Translator Pattern**: The `dockerProject` DSL is purely a configuration translator. It does not introduce
   new runtime behavior, tasks, or services.

2. **Optional Facade**: Users can still use `docker { }`, `dockerOrch { }`, and `dockerWorkflows { }` directly.
   The `dockerProject` DSL is additive.

3. **Mutual Exclusivity**: If `dockerProject { }` is configured, it should be the only way to configure that
   image. Mixing `dockerProject` with direct `docker { }` configuration for the same image would be an error.
   The translator validates this and throws a clear error message.

4. **Convention Over Configuration**: Heavy use of defaults:
   - Default tags: `['latest']`
   - Default compose project name: derived from project name
   - Default lifecycle: `'class'`
   - Default compression: inferred from filename
   - Default test task: `'integrationTest'`

5. **Configuration Cache Compatible**: All new code follows the Provider API patterns and avoids Project
   references at execution time.

6. **Property Naming**: Use `additionalTags` instead of `tags` in `onSuccess` to clearly communicate
   additive semantics and avoid `+=` vs `=` confusion.

7. **Compression Inference**: Save file compression is inferred from the filename extension to reduce
   configuration verbosity.

8. **SourceRef Support**: Full support for both build mode (jarFrom/contextDir) and sourceRef mode
   (sourceRef string or component properties).

9. **Version Derivation**: The `version` property can be explicitly set, or it will be derived from
   the first non-'latest' tag in the `tags` list.

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Translator complexity | Thorough unit testing of translation logic |
| Edge cases in wiring | Comprehensive functional tests |
| Naming conflicts | Use sanitized, unique names for generated entities |
| User confusion about which DSL to use | Clear documentation with decision flowchart |
| Configuration cache violations | Follow existing patterns from working code |
| Conflicting docker{} and dockerProject{} usage | Explicit validation with clear error messages |

---

## Implementation Notes (Gradle 9/10 Compatibility)

This section documents key implementation patterns required for Gradle 9/10 configuration cache compatibility.

### 1. Service Injection Pattern

All spec classes use `@Inject` constructors with only `ObjectFactory` parameter:

```groovy
@Inject
ProjectImageSpec(ObjectFactory objectFactory) {
    this.objectFactory = objectFactory
    // Set conventions here
}
```

When creating instances, let Gradle inject services automatically:
```groovy
// CORRECT: Gradle injects ObjectFactory, ProviderFactory, ProjectLayout
objectFactory.newInstance(DockerProjectSpec)

// INCORRECT: Don't pass services manually
objectFactory.newInstance(DockerProjectSpec, objectFactory, providers, layout)
```

### 2. Lazy Property Initialization

Abstract `Property<T>` fields cannot have `.convention()` called in the constructor
because they are not yet initialized. Use lazy initialization:

```groovy
abstract class DockerProjectSpec {
    private ProjectImageSpec imageSpec  // Lazy holder

    void initializeNestedSpecs() {
        if (imageSpec == null) {
            imageSpec = objectFactory.newInstance(ProjectImageSpec)
            image.set(imageSpec)
        }
    }
}
```

### 3. Provider API for Property Assignment

Always use `.set()` for `Property<T>` types, never direct assignment:

```groovy
// CORRECT
buildStep.image.set(dockerExt.images.getByName(imageName))
testStep.testTaskName.set('integrationTest')
testStep.lifecycle.set(WorkflowLifecycle.CLASS)

// INCORRECT (compiles but doesn't work correctly)
buildStep.image = dockerExt.images.getByName(imageName)
```

### 4. No afterEvaluate in Translator

The translator runs within the plugin's existing `afterEvaluate` block, so tasks
are already registered. Use `tasks.named().configure()` for dependency wiring:

```groovy
// CORRECT: Provider-based lazy wiring
project.tasks.named("composeUp${stackName}").configure { task ->
    task.dependsOn("dockerBuild${imageName}")
}

// INCORRECT: Nested afterEvaluate
project.afterEvaluate {
    project.tasks.named(...).configure { ... }
}
```

### 5. @Input/@Optional Annotations

All properties should have proper annotations for task input tracking:

```groovy
@Input
@Optional
abstract Property<String> getSourceRef()

@Input
abstract ListProperty<String> getTags()

@Nested
@Optional
AuthSpec pullAuth
```

### 6. Unwrap Providers When Passing to DSL Methods

When calling DSL methods that expect values (not providers), unwrap with `.get()`:

```groovy
// CORRECT: Unwrap the provider
waitForServices.set(testSpec.waitForHealthy.get())
timeoutSeconds.set(testSpec.timeoutSeconds.get())

// INCORRECT: Passing provider to method expecting value
waitForServices.set(testSpec.waitForHealthy)  // Works but may cause issues
```

### 7. Closure DSL Support (Groovy Compatibility)

All spec classes should provide both `Action<T>` and `@DelegatesTo(T) Closure` DSL methods
for Groovy DSL compatibility:

```groovy
// Closure support for Groovy DSL
void image(@DelegatesTo(ProjectImageSpec) Closure closure) {
    initializeNestedSpecs()
    closure.delegate = image.get()
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure.call()
}

// Action support for Kotlin DSL and programmatic use
void image(Action<ProjectImageSpec> action) {
    initializeNestedSpecs()
    action.execute(image.get())
}
```

---

## Implementation Order

1. **Phase 1**: Create spec classes (foundation)
2. **Phase 2**: Create extension class (DSL entry point)
3. **Phase 3**: Create translator service (core logic)
4. **Phase 4**: Integrate into plugin (wire it up)
5. **Phase 5**: Write tests (validate behavior)
6. **Phase 6**: Write documentation (user-facing)

Each phase can be completed and tested independently before moving to the next.

---

## Completion Checklist

- [ ] Phase 1: Spec classes created and unit tested
- [ ] Phase 2: Extension class created and unit tested
- [ ] Phase 3: Translator service created and unit tested
- [ ] Phase 4: Plugin integration complete
- [ ] Phase 5: All tests passing (unit, functional, integration)
- [ ] Phase 6: Documentation complete
- [ ] Update project review with completion note

---

## Follow-up Actions

Upon completion of this feature:

1. Update `docs/design-docs/project-reviews/2025-12-06-project-review.md` to mark the
   "Simplified facade for 80% use case" recommendation as completed

2. Add reference to this plan document in the project review

3. Consider additional improvements identified during implementation

---

## Plan Review Notes (2025-12-06)

The following corrections were applied based on code review:

### Corrections Applied

1. **DSL Examples Updated**: Changed from Groovy property assignment (`name = 'my-app'`) to
   explicit `.set()` calls (`name.set('my-app')`) for consistency with existing plugin patterns.

2. **Version Property Added**: Added `version` property to `ProjectImageSpec` with `deriveVersion()`
   method that extracts version from the first non-'latest' tag if not explicitly set.

3. **Closure DSL Support Added**: All spec classes now provide both `@DelegatesTo(T) Closure` and
   `Action<T>` DSL methods for Groovy DSL compatibility, matching existing plugin patterns.

4. **Mutual Exclusivity Validation Added**: `DockerProjectTranslator.validateSpec()` now checks
   for conflicting `docker{}` configuration and throws a clear error message.

5. **Plugin Dependency Validation Added**: `validateJarTaskPath()` now validates that required
   plugins (java/groovy) are applied before using `jarFrom`.

6. **PublishSpec API Corrected**: Updated translator to use correct `PublishSpec.to(String, Action)`
   API based on actual implementation.

7. **Execution Order Documentation Added**: Added comments clarifying that translator runs in the
   second `afterEvaluate` block, after tasks are registered.

8. **Test Lifecycle Integration**: Added `delegateStackManagement.set(true)` when `lifecycle = METHOD`
   to properly integrate with test framework extensions.

---

## Second Plan Review Notes (2025-12-06)

The following additional corrections were applied based on detailed codebase analysis:

### Corrections Applied

1. **ImageSpec Name Clarification**: Added detailed comments in `configureDockerImage()` explaining
   the distinction between `ImageSpec.name` (String field - internal identifier from `images.create()`)
   and `ImageSpec.imageName` (Property<String> - Docker image name for tagging). The `ProjectImageSpec.name`
   maps to `ImageSpec.imageName`.

2. **Configuration Cache Compatibility**: Added `contextTaskName.set()` call when creating context tasks
   via `jarFrom`. This ensures configuration cache compatibility by providing the task name as a string
   in addition to the `TaskProvider` reference.

3. **onSuccess vs onTestSuccess Semantics**: Added documentation clarifying that `PipelineSpec` has
   both properties:
   - `onTestSuccess`: Operations after tests pass (used by dockerProject since it includes tests) - HAS CONVENTION
   - `onSuccess`: Operations after build succeeds (without test step) - NO CONVENTION SET
   Both are `Property<SuccessStepSpec>` and behave identically when set.
   **IMPORTANT:** The translator uses `onTestSuccess` which has a convention. If `onSuccess` is ever
   used, ensure the convention is set first or handle the case where it's not present.

4. **PipelineSpec Closure DSL Note**: Added comment noting that `PipelineSpec` only has `Action<T>`
   methods (not `@DelegatesTo Closure` methods). This is intentional - the translator uses Action-style
   configuration, while users interact via the project specs which do support Closure syntax.

5. **Compression Inference Logic**: Enhanced comments in `inferCompression()` to clarify the logic:
   - If explicitly set to non-default value, use it
   - Otherwise, infer from filename extension
   - 'gzip' is the default convention

6. **Integration Test Directory Convention**: Changed from `app-image/integrationTest/dockerProject/`
   to `plugin-integration-test/dockerProject/` to match existing conventions:
   - `plugin-integration-test/docker/scenario-X/` for docker tasks
   - `plugin-integration-test/dockerOrch/scenario-X/` for dockerOrch tasks
   - `plugin-integration-test/dockerProject/scenario-X/` for dockerProject (new)
   Added note to create `README.md` for scenario documentation.

7. **afterEvaluate Ordering Clarification**: The translator comment already documents that it runs
   in the plugin's existing `afterEvaluate` block. The ordering is determined by registration order
   in `apply()`: `registerTaskCreationRules` → `registerWorkflowTasks` → `configureAfterEvaluation`.

### Verification Status

All corrections have been applied inline to the relevant code sections in this document. The plan
is now ready for implementation.

---

## Third Plan Review Notes (2025-12-06)

The following additional corrections were applied based on comprehensive code analysis:

### Corrections Applied

1. **DSL Example Syntax Consistency**: Updated Build Mode Example to use `.set()` consistently:
   - Changed `save 'build/...'` to `saveFile.set('build/images/my-app.tar.gz')`
   - All property assignments now use Provider API `.set()` method

2. **jarFrom Path Parsing Logic Rewritten**: Fixed fragile parsing logic in `createContextTask()` to
   handle all task path formats correctly:
   - `'jar'` → current project's jar task
   - `':jar'` → root project's jar task
   - `':app:jar'` → subproject :app's jar task
   - `':sub:project:jar'` → nested subproject's jar task

   The original logic broke on `:jar` (root project reference). Now uses explicit conditional
   handling with clear comments for each case.

3. **validateJarTaskPath Enhanced**: Improved validation with:
   - Better error messages that list available subprojects
   - Format documentation in method comments
   - Clear guidance on valid task path formats

4. **Phase 4 Plugin Integration Rewritten**: Critical fix for translator timing:
   - **Problem**: Original plan had translator running after task registration, causing tasks
     not to be registered for dockerProject-created images
   - **Solution**: Move translation INTO `registerTaskCreationRules` BEFORE iterating images
   - Translation now happens at Step 1, then task registration at Step 2, all within same afterEvaluate
   - This ensures `dockerExt.images.all { }` sees translator-created images

5. **Test Files Table Updated**: Added missing `ProjectFailureSpecTest.groovy` to Phase 5 test files list.

6. **PipelineSpec.onSuccess Convention Note**: Added documentation clarifying that `PipelineSpec.onSuccess`
   has no convention set (unlike `onTestSuccess`), but this is not a blocker since `dockerProject`
   uses `onTestSuccess` for its test-centric workflow.

### Key Technical Insight

The `NamedDomainObjectContainer.all {}` callback pattern fires for BOTH existing AND newly-added
items. However, this only works if the translation happens within the same `afterEvaluate` block
and BEFORE the iteration. The rewritten Phase 4 ensures this ordering is explicit and correct.

### Verification Status

All corrections have been applied. The plan is now ready for implementation with proper:
- DSL syntax consistency
- Task path parsing robustness
- Translator timing correctness
- Complete test coverage documentation

---

## Fourth Plan Review Notes (2025-12-06)

The following additional corrections were applied based on comprehensive codebase verification:

### Corrections Applied

1. **Integration Test Directory Convention Fixed**: Changed incorrect directory references:
   - Changed `plugin-integration-test/compose/scenario-<number>/` to `plugin-integration-test/dockerOrch/`
   - Added reference to `plugin-integration-test/dockerWorkflows/` directory
   - The actual codebase uses `dockerOrch/` not `compose/` for Docker Compose integration tests

2. **DockerWorkflowsExtension Parameter Added**: Fixed the `registerTaskCreationRules()` method signature
   to include `DockerWorkflowsExtension dockerWorkflowsExt` parameter. The original plan referenced
   `dockerWorkflowsExt` in the translator call but didn't pass it as a parameter.

3. **ComposeStackSpec Files API Fixed**: Updated `configureComposeStack()` to properly resolve file paths:
   - Changed `stack.files.from(testSpec.compose.get())` to `stack.files.from(project.file(testSpec.compose.get()))`
   - `ConfigurableFileCollection.from()` requires resolved File objects, not String paths

4. **ProjectLayout Import Added**: Added missing import `org.gradle.api.file.ProjectLayout` to the
   DockerProjectTranslator class imports.

5. **PipelineSpec Convention Warning Enhanced**: Strengthened the documentation about `onSuccess` vs
   `onTestSuccess` conventions to make it clear that `onSuccess` has NO convention set while
   `onTestSuccess` does. Added explicit warning about handling this if `onSuccess` is ever used.

6. **BuildStepSpec.image Property Verified**: Confirmed that `BuildStepSpec` has
   `abstract Property<ImageSpec> getImage()` at line 46 of BuildStepSpec.groovy. The translator's
   usage of `buildStep.image.set(dockerExt.images.getByName(imageName))` is correct.

### Verification Status

All identified issues have been corrected. The plan is now fully ready for implementation with:
- Correct directory structure references matching actual codebase
- Complete method signatures with all required parameters
- Proper file resolution for ConfigurableFileCollection usage
- All necessary imports documented
- Convention handling documented and clarified
- BuildStepSpec API verified as correct

---

## Fifth Plan Review Notes (2025-12-06)

The following corrections were applied based on comprehensive implementation readiness review:

### Critical Issues Fixed

1. **jarFrom Parsing Logic Rewritten**: Replaced fragile string manipulation with cleaner approach:
   - Split by ':' and analyze resulting parts explicitly
   - Added case for invalid format like 'foo:jar' (must start with ':' for cross-project)
   - Clearer conditional handling with explicit comments for each case
   - Updated `validateJarTaskPath()` to catch invalid formats early

2. **Build Mode imageName Fallback Added**: Fixed potential issue where `ImageSpec.imageName` could
   remain unset if `ProjectImageSpec.name` was not provided:
   - Now derives image name from `project.name` if not explicitly set
   - Uses `deriveImageName()` result as fallback for `image.imageName.set()`
   - Added explanatory comments about the two "name" concepts

3. **PipelineSpec.onSuccess NPE Guard Documented**: Enhanced documentation in `configurePipeline()`
   with explicit warning about `onSuccess` having no convention:
   - Clarified that `onTestSuccess` has a convention (safe to use)
   - Documented the two options if `onSuccess` is ever used: set convention OR check isPresent()
   - Added "CRITICAL" marker for future maintainers

### Moderate Issues Fixed

4. **WaitSpec Property Mapping Explained**: Added comments explaining the semantic mapping:
   - `ProjectTestSpec.waitForHealthy` -> `WaitSpec.waitForServices`
   - Explained why names differ (WaitSpec is generic, ProjectTestSpec is semantic)

5. **METHOD Lifecycle Validation Warning Added**: When METHOD lifecycle is selected, the
   translator now logs a warning reminding users about requirements:
   - `maxParallelForks = 1` on test task
   - Appropriate annotations on test classes (@ComposeUp or @ExtendWith)

6. **File Summary Table Corrected**: Updated from 17 to 20 files:
   - Added `scenario-4-method-lifecycle/` integration test
   - Fixed directory path prefixes for clarity (`plugin/...`, `plugin-integration-test/...`)

### Key Implementation Notes

The jarFrom parsing now uses this cleaner pattern:
```groovy
if (!jarTaskPath.contains(':')) {
    // Case 1: Simple task name 'jar' - current project
} else if (jarTaskPath.startsWith(':')) {
    def pathWithoutLeadingColon = jarTaskPath.substring(1)
    def lastColonIndex = pathWithoutLeadingColon.lastIndexOf(':')
    if (lastColonIndex == -1) {
        // Case 2: Root project ':jar'
    } else {
        // Case 3: Subproject ':app:jar' or ':sub:project:jar'
    }
} else {
    // Invalid format - throw error
}
```

### Verification Status

All corrections have been applied inline. The plan is now ready for implementation with:
- Robust jarFrom parsing that handles all edge cases
- Guaranteed imageName population in build mode
- NPE-safe onSuccess/onTestSuccess usage with clear documentation
- Semantic comments explaining property mappings
- User-facing warnings for METHOD lifecycle requirements
- Accurate file count and directory structure

---

## Sixth Plan Review Notes (2025-12-07)

The following corrections were applied based on implementation readiness verification:

### Corrections Applied

1. **WorkflowLifecycle Import Verified**: Confirmed that the import statement
   `import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle` is already present
   in the `DockerProjectTranslator` code at line 940. No changes needed.

2. **Labels Property Added to ProjectImageSpec**: Added the missing `labels` property:
   - Added `abstract MapProperty<String, String> getLabels()` with `@Input` annotation
   - Added `labels.convention([:])` in the constructor
   - Added `image.labels.putAll(imageSpec.labels)` in the translator's `configureDockerImage()` method
   - This allows users to specify Docker labels in the simplified DSL

3. **Extension Availability Validation Added**: Enhanced `validateSpec()` to validate all three
   extensions are available before translation:
   - Updated method signature to accept `dockerOrchExt` and `dockerWorkflowsExt` parameters
   - Added null checks with clear error messages for each extension
   - This is defensive coding since the plugin always registers all three extensions
   - Provides clear guidance if the plugin is partially applied or misconfigured

4. **PipelineValidator METHOD Lifecycle Note**: The existing documentation already contains
   a warning about METHOD lifecycle requiring `maxParallelForks = 1`. This is logged as a
   warning at translation time. The actual validation happens in `PipelineValidator`.

### Verification Status

All corrections have been applied inline. The plan is now complete and ready for implementation with:
- Labels property support for Docker image metadata
- Defensive validation for extension availability
- Complete import statements for all referenced classes
- Comprehensive documentation of conventions and NPE safety

---

## Seventh Plan Review Notes (2025-12-07)

The following corrections were applied based on comprehensive implementation readiness analysis:

### Critical Issues Fixed

1. **Prerequisite Tasks Section Added**: Created a new "Prerequisite Tasks (Before Implementation)" section
   that documents required changes to the existing codebase before implementing dockerProject:
   - Add conventions for `PipelineSpec.onSuccess` and `PipelineSpec.onFailure` to prevent NPE
   - Add convention for `WaitSpec.waitForServices` (empty list) for defensive coding
   - These changes must be made and tested before implementing the new spec classes

2. **DSL Syntax Comment Corrected**: Updated the misleading comment in Build Mode Example from
   "Uses Groovy property assignment syntax" to "Uses Provider API .set() syntax" since the example
   actually uses `.set()` method calls, not Groovy property assignment.

### Moderate Issues Fixed

3. **Integration Test Scenario Added**: Added `scenario-5-contextdir-mode/` to test the `contextDir`
   build mode alternative to `jarFrom`. This ensures both build mode options are covered in
   integration tests.

4. **Critical Unit Test Requirements Added**: Added a detailed list of required test cases for
   `DockerProjectTranslatorTest` that specifically verify translator-created images pass the
   existing `DockerExtension.validate()` validation. This addresses the timing concern where
   translation creates images that must pass validation running afterwards.

5. **File Summary Table Updated**: 
   - Changed from 20 to 22 new files (added scenario-5 and usage-docker-project.md)
   - Changed from 5 to 7 modified files (added PipelineSpec.groovy and WaitSpec.groovy prerequisites)
   - Added `usage-docker-project.md` to new files list

### Documentation Improvements

6. **Scenario 5 Example Added**: Added complete build.gradle example for the contextDir scenario,
   explaining when contextDir is preferred over jarFrom (pre-built artifacts, static assets, etc.)

7. **Test Case Documentation**: Documented 10 specific test cases that must be implemented in
   `DockerProjectTranslatorTest` to ensure comprehensive validation coverage.

### Verification Status

All recommendations have been applied. The plan now includes:
- Clear prerequisite tasks that must be completed before implementation
- Correct DSL syntax documentation
- Complete integration test coverage (5 scenarios)
- Comprehensive unit test requirements for translator validation
- Accurate file counts reflecting all changes
- The plan is now ready for implementation

---

## Eighth Plan Review Notes (2025-12-07)

### Changes Applied

1. **ProjectSuccessSpec saveCompression Convention**: Changed `saveCompression.convention('gzip')` to
   `saveCompression.convention('')` (empty string) for clearer semantics. The underlying `SaveSpec` already
   defaults to `SaveCompression.NONE`, so using an empty string here signals "use the default from SaveSpec"
   rather than overriding with a potentially conflicting value.

2. **Integration Test Directory Corrections**: Fixed remaining references to use the correct directory names:
   - Changed `plugin-integration-test/compose/README.md` to `plugin-integration-test/dockerOrch/README.md`
   - Changed `plugin-integration-test/compose/scenario-X/` to `plugin-integration-test/dockerOrch/scenario-X/`
   
   The actual directory structure is:
   - `plugin-integration-test/docker/` - for docker build/tag/save/publish tasks
   - `plugin-integration-test/dockerOrch/` - for compose-based testing tasks
   - `plugin-integration-test/dockerWorkflows/` - for workflow tasks
   - `plugin-integration-test/dockerProject/` - for the new dockerProject DSL (to be created)

3. **Confirmed Prerequisite Code Is Correct**: Verified that the prerequisite section's code for adding
   conventions to `PipelineSpec.onSuccess` and `onFailure` is correct:
   ```groovy
   onSuccess.convention(objectFactory.newInstance(SuccessStepSpec))
   onFailure.convention(objectFactory.newInstance(FailureStepSpec))
   ```
   Gradle's `ObjectFactory.newInstance()` automatically injects `@Inject` constructor parameters
   (`ObjectFactory`, `ProjectLayout`, etc.), so no manual parameter passing is needed.

4. **Confirmed DockerProjectTranslator Import Is Present**: Verified that `import org.gradle.api.tasks.Copy`
   is already present in the DockerProjectTranslator imports (line 995).

### Verification Status

All eighth plan review corrections have been applied. The plan is ready for implementation.

---

## Ninth Plan Review Notes (2025-12-07)

The following corrections were applied based on comprehensive codebase verification against actual
source files (`PipelineSpec.groovy`, `WaitSpec.groovy`, `GradleDockerPlugin.groovy`).

### Critical Issues Addressed

1. **Prerequisite Tasks Reclassified**: Changed prerequisites from "required blockers" to "recommended
   improvements" with clear classification table:

   | Task | Severity | Rationale |
   |------|----------|-----------|
   | PipelineSpec.onSuccess/onFailure conventions | Recommended | Translator uses `onTestSuccess` (has convention) |
   | WaitSpec.waitForServices convention | Optional | Translator uses `.getOrElse([])` pattern |

   **Key Finding**: The translator safely uses `onTestSuccess` which HAS a convention in the current
   codebase (PipelineSpec.groovy line 45). The `onSuccess` property has NO convention, but the
   translator doesn't use it. Prerequisites are defensive improvements, not blockers.

2. **Phase 4 Plugin Integration Completely Rewritten**: The previous description was inaccurate about
   the plugin's `afterEvaluate` structure. Added detailed documentation of the actual THREE-block
   structure found in `GradleDockerPlugin.groovy`:

   - **First afterEvaluate** (line 174): `registerDockerImageTasks()` + `registerComposeStackTasks()`
   - **Second afterEvaluate** (line 305): Workflow pipeline task registration
   - **Third afterEvaluate** (line 371): Validation and task dependency configuration

   The plan now includes:
   - ASCII tree diagram showing the complete `apply()` method structure
   - Step-by-step changes with exact line numbers
   - Method signature changes with before/after comparison
   - Call site update with exact code
   - Required imports list

3. **saveCompression Logic Fixed**: Updated `inferCompression()` method documentation and code:
   - Changed comment from "default convention is 'gzip'" to "convention is empty string"
   - Changed check from `!= 'gzip'` to `!isEmpty()` to match actual convention value (`''`)
   - Added note explaining relationship to `SaveSpec.compression` default (`NONE`)

### Verification Performed

| Item | Verified Against | Result |
|------|------------------|--------|
| PipelineSpec.onTestSuccess convention | Line 45 | ✅ Has convention |
| PipelineSpec.onSuccess convention | Lines 62, 82-84 | ❌ No convention (confirmed) |
| WaitSpec.waitForServices | Line 37 | ❌ No convention (confirmed) |
| GradleDockerPlugin afterEvaluate count | Lines 174, 305, 371 | 3 blocks (confirmed) |
| SaveSpec.compression default | SaveSpec.groovy line 46 | `SaveCompression.NONE` |

### Plan Readiness Assessment

**Status: READY FOR IMPLEMENTATION**

The plan is now 100% accurate with respect to the existing codebase. All assumptions have been
verified against actual source code. The implementation can proceed with confidence.

**Implementation Notes:**
- Prerequisites are optional (implement them for better code quality, but not required for translator)
- Phase 4 changes are exact - follow the step-by-step instructions
- All 19 key API assumptions have been verified against actual source code
