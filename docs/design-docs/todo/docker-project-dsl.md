# Design Plan: `dockerProject` Simplified Facade DSL

**Created:** 2025-12-06
**Status:** TODO
**Priority:** Medium-term improvement
**Last Updated:** 2025-12-06 (plan review corrections applied)

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
// NOTE: Uses Groovy property assignment syntax which works with Property<T> types
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
        save 'build/images/my-app.tar.gz'
        // publish to: 'registry.example.com', namespace: 'myproject'
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
        saveCompression.convention('gzip')
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
     */
    SaveCompression inferCompression() {
        if (!saveFile.isPresent()) {
            return null
        }

        def filename = saveFile.get()

        // Check explicit override first
        if (saveCompression.isPresent() && saveCompression.get() != 'gzip') {
            return parseCompression(saveCompression.get())
        }

        // Infer from filename
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

        // Validate configuration
        validateSpec(projectSpec, dockerExt)

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

    private void validateSpec(DockerProjectSpec projectSpec, DockerExtension dockerExt) {
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
        dockerExt.images.create(sanitizedName) { image ->
            // Common properties - use .set() for Property<T> types
            // Map ProjectImageSpec.name -> ImageSpec.imageName
            if (imageSpec.name.isPresent()) {
                image.imageName.set(imageSpec.name)
            }
            image.tags.set(imageSpec.tags)
            image.registry.set(imageSpec.registry)
            image.namespace.set(imageSpec.namespace)
            image.buildArgs.putAll(imageSpec.buildArgs)
            
            // Map version - derive from tags if not explicitly set
            def derivedVersion = imageSpec.deriveVersion()
            if (!derivedVersion.isEmpty()) {
                image.version.set(derivedVersion)
            }

            if (imageSpec.isBuildMode()) {
                // Build mode configuration
                if (imageSpec.jarFrom.isPresent() && !imageSpec.jarFrom.get().isEmpty()) {
                    def contextTask = createContextTask(project, sanitizedName, imageSpec)
                    image.contextTask = contextTask
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
            // Handle both ':app:jar' and 'jar' formats
            def jarTaskName = jarTaskPath.startsWith(':') ?
                jarTaskPath.substring(jarTaskPath.lastIndexOf(':') + 1) : jarTaskPath
            def jarProject = jarTaskPath.contains(':') && jarTaskPath.startsWith(':') ?
                project.project(jarTaskPath.substring(0, jarTaskPath.lastIndexOf(':'))) : project

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
     */
    private void validateJarTaskPath(Project project, String jarTaskPath) {
        // Validate required plugins are applied
        if (!project.plugins.hasPlugin('java') && !project.plugins.hasPlugin('groovy')) {
            throw new GradleException(
                "dockerProject.image.jarFrom requires 'java' or 'groovy' plugin to be applied. " +
                "Add: plugins { id 'java' } or plugins { id 'groovy' } to your build.gradle"
            )
        }
        
        if (jarTaskPath.startsWith(':') && jarTaskPath.contains(':')) {
            // Cross-project reference like ':app:jar'
            def projectPath = jarTaskPath.substring(0, jarTaskPath.lastIndexOf(':'))
            try {
                project.project(projectPath)
            } catch (Exception e) {
                throw new GradleException(
                    "dockerProject.image.jarFrom references non-existent project '${projectPath}'. " +
                    "Verify the project path in your jarFrom setting: '${jarTaskPath}'"
                )
            }
        }
        // Note: Task existence is validated lazily by Gradle when tasks.named() is called
    }

    private void configureComposeStack(Project project, DockerOrchExtension dockerOrchExt,
                                        ProjectTestSpec testSpec, String stackName) {
        dockerOrchExt.composeStacks.create(stackName) { stack ->
            stack.files.from(testSpec.compose.get())

            if (testSpec.projectName.isPresent()) {
                stack.projectName.set(testSpec.projectName)
            } else {
                stack.projectName.set("${project.name}-${stackName}")
            }

            if (testSpec.waitForHealthy.isPresent() && !testSpec.waitForHealthy.get().isEmpty()) {
                stack.waitForHealthy {
                    // Use .get() to unwrap the ListProperty value
                    waitForServices.set(testSpec.waitForHealthy.get())
                    timeoutSeconds.set(testSpec.timeoutSeconds.get())
                    pollSeconds.set(testSpec.pollSeconds.get())
                }
            }

            if (testSpec.waitForRunning.isPresent() && !testSpec.waitForRunning.get().isEmpty()) {
                stack.waitForRunning {
                    // Use .get() to unwrap the ListProperty value
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
        dockerWorkflowsExt.pipelines.create(pipelineName) { pipeline ->
            pipeline.description.set("Auto-generated pipeline for ${imageName}")

            // Configure build step - use .set() for Property<T> types
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

#### Changes to `GradleDockerPlugin.groovy`

In the `apply()` method, add after existing extension creation:

```groovy
// Create dockerProject extension (simplified facade)
// Gradle injects ObjectFactory, ProviderFactory, ProjectLayout automatically via @Inject
def dockerProjectExt = project.extensions.create(
    'dockerProject',
    DockerProjectExtension,
    project.objects  // Only ObjectFactory needed; it injects other services into DockerProjectSpec
)
```

In `configureAfterEvaluation()`, add translation step before existing validation:

```groovy
project.afterEvaluate {
    try {
        // Translate dockerProject to docker/dockerOrch/dockerWorkflows if configured
        // NOTE: This runs AFTER registerTaskCreationRules's afterEvaluate, so all tasks exist
        if (dockerProjectExt.spec.isConfigured()) {
            def translator = new DockerProjectTranslator()
            translator.translate(project, dockerProjectExt.spec, dockerExt, dockerOrchExt, dockerWorkflowsExt)
        }

        // ... existing validation and configuration
        dockerExt.validate()
        dockerOrchExt.validate()
        // ...
    }
}
```

---

### Phase 5: Testing

#### New Test Files

| File | Purpose |
|------|---------|
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/DockerProjectSpecTest.groovy` | Unit tests for DockerProjectSpec |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectImageSpecTest.groovy` | Unit tests for ProjectImageSpec |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectTestSpecTest.groovy` | Unit tests for ProjectTestSpec |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/project/ProjectSuccessSpecTest.groovy` | Unit tests for ProjectSuccessSpec |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerProjectExtensionTest.groovy` | Unit tests for DockerProjectExtension |
| `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/DockerProjectTranslatorTest.groovy` | Unit tests for DockerProjectTranslator |
| `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/DockerProjectFunctionalTest.groovy` | Functional tests for DSL parsing |

#### Integration Test Scenarios

Integration tests follow the existing project structure convention established in `plugin-integration-test/`.
Each scenario is organized under the `app-image/integrationTest/` directory pattern.

| Directory | Purpose |
|-----------|---------|
| `plugin-integration-test/app-image/integrationTest/dockerProject/scenario-1-build-mode/` | Basic build mode: jarFrom, test, additionalTags |
| `plugin-integration-test/app-image/integrationTest/dockerProject/scenario-2-sourceref-mode/` | SourceRef mode with component properties |
| `plugin-integration-test/app-image/integrationTest/dockerProject/scenario-3-save-publish/` | Save and publish on success |
| `plugin-integration-test/app-image/integrationTest/dockerProject/scenario-4-method-lifecycle/` | Method lifecycle mode |

##### Scenario 2: SourceRef Mode with Component Properties

This scenario tests using an existing image via `sourceRef` component properties:

```groovy
// plugin-integration-test/app-image/integrationTest/dockerProject/scenario-2-sourceref-mode/build.gradle

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

### New Files (17)

| Directory | File | Purpose |
|-----------|------|---------|
| `spec/project/` | `DockerProjectSpec.groovy` | Top-level spec |
| `spec/project/` | `ProjectImageSpec.groovy` | Image configuration |
| `spec/project/` | `ProjectTestSpec.groovy` | Test configuration |
| `spec/project/` | `ProjectSuccessSpec.groovy` | Success operations |
| `spec/project/` | `ProjectFailureSpec.groovy` | Failure operations |
| `extension/` | `DockerProjectExtension.groovy` | DSL extension |
| `service/` | `DockerProjectTranslator.groovy` | Translation logic |
| `test/.../spec/project/` | `DockerProjectSpecTest.groovy` | Unit tests |
| `test/.../spec/project/` | `ProjectImageSpecTest.groovy` | Unit tests |
| `test/.../spec/project/` | `ProjectTestSpecTest.groovy` | Unit tests |
| `test/.../spec/project/` | `ProjectSuccessSpecTest.groovy` | Unit tests |
| `test/.../extension/` | `DockerProjectExtensionTest.groovy` | Extension tests |
| `test/.../service/` | `DockerProjectTranslatorTest.groovy` | Translator tests |
| `functionalTest/` | `DockerProjectFunctionalTest.groovy` | Functional tests |
| `app-image/integrationTest/dockerProject/` | `scenario-1-build-mode/` | Build mode integration test |
| `app-image/integrationTest/dockerProject/` | `scenario-2-sourceref-mode/` | SourceRef mode integration test |
| `app-image/integrationTest/dockerProject/` | `scenario-3-save-publish/` | Save/publish integration test |

### Modified Files (5)

| File | Changes |
|------|---------|
| `GradleDockerPlugin.groovy` | Register extension, call translator |
| `docs/usage/usage-docker.md` | Cross-reference to dockerProject |
| `docs/usage/usage-docker-orch.md` | Cross-reference to dockerProject |
| `docs/usage/usage-docker-workflows.md` | Cross-reference to dockerProject |
| `docs/design-docs/project-reviews/2025-12-06-project-review.md` | Mark recommendation as completed |

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
