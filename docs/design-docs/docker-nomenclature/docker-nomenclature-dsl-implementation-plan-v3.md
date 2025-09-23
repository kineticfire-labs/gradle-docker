# Comprehensive Gradle Docker Plugin Refactoring Plan v3 (Final)

This consolidated plan incorporates all requirements from v1 and critical corrections from v2 to refactor the plugin for 
Gradle 9 cache compatibility and improved Docker nomenclature while preserving existing workflows.

## **Executive Summary**

**ARCHITECTURAL PRINCIPLE**: The new Docker nomenclature properties **COMPLEMENT** existing `sourceRef` workflows, not 
replace them. The plugin supports two distinct, coexisting usage modes:

1. **Build Mode**: Use nomenclature properties (registry, namespace, imageName, etc.) for building new images
2. **SourceRef Mode**: Use `sourceRef` property for working with existing/pre-built images

Both modes have different validation rules, behavior patterns, and are fully supported throughout the plugin.

## **Phase 1: Gradle 9 Configuration Cache Foundation**

### **Step 1.1: Replace Property Types with Gradle Provider API**
- **Target**: All specs (`ImageSpec`, `SaveSpec`, `PublishSpec`, `PublishTarget`)
- **Changes**:
  - Replace concrete properties with `Property<T>`, `ListProperty<T>`, `MapProperty<K,V>`
  - Convert `String` fields to `Property<String>`
  - Convert `List<String>` to `ListProperty<String>`
  - Convert `Map<String,String>` to `MapProperty<String,String>`
  - Add `@Input`, `@Optional`, `@Nested` annotations for configuration cache compatibility

### **Step 1.2: Add Docker Image Nomenclature Properties to ImageSpec**
- **New properties for Build Mode** (building new images):
  - `Property<String> registry` (e.g., "ghcr.io", "example.com:5000")
  - `Property<String> namespace` (e.g., "kineticfire/stuff")
  - `Property<String> imageName` (e.g., "my-app") - **Note**: Changed from "name" to avoid conflicts
  - `Property<String> repository` (alternative to namespace+imageName combination)
  - `Property<String> version` (defaults to project.version)
  - `ListProperty<String> tags` (actual tag names like "latest", "1.0.0")
  - `MapProperty<String,String> labels` (**NEW FEATURE** - OCI image labels)

- **Preserve existing properties for SourceRef Mode** (working with existing images):
  - `Property<String> sourceRef` (**PRESERVED** - for existing/pre-built images)

### **Step 1.3: Create SaveCompression Enum and Enhance SaveSpec**
- Create `SaveCompression` enum with values: `NONE`, `GZIP`, `ZIP`, `BZIP2`, `XZ`
- Replace `Property<String> compression` with `Property<SaveCompression> compression`
- **Add enhanced SaveSpec properties**:
  - `Property<Boolean> pullIfMissing` (**PRESERVED** - pulls sourceRef if not local when true)
  - **Add authentication support to SaveSpec** (consistent with PublishSpec):
    - `AuthSpec auth` (for pulling from private registries)
    - Properties: `username`, `password`, `registryToken`, `helper`
    - **NO `serverAddress`** - extracted automatically from `sourceRef`

## **Phase 2: API Restructure and Validation**

### **Step 2.1: Implement Dual-Mode Validation Rules**

**Build Mode Validation** (when building new images):
- **Mutual exclusivity**: If `repository` is set, then `namespace` + `imageName` cannot be set
- **Required fields**: Either `repository` OR `imageName` must be specified
- **sourceRef exclusivity**: If `sourceRef` is set, building-related properties cannot be set
  - Excluded properties: `contextTask`, `buildArgs`, `labels`, `dockerfile`, `namespace`, `imageName`, `repository`

**SourceRef Mode Validation** (when using existing images):
- **sourceRef required**: `sourceRef` must be set and be a valid image reference
- **Build exclusivity**: Cannot set build-related properties when `sourceRef` is used
- **pullIfMissing logic**: Only applies when `sourceRef` is set
- **Auth requirement**: If `pullIfMissing=true` and registry is private, auth must be configured

### **Step 2.2: Remove Legacy Tag Handling**
- Remove automatic image name derivation from task names
- Remove `isValidTagName()` and simple tag validation
- Update validation to use new nomenclature or sourceRef validation

### **Step 2.3: Update DSL Structure Examples**

**Build Mode DSL** (building new images):
```groovy
docker {
    images {
        timeServer {
            // Build configuration
            contextTask = tasks.register('prepareTimeServerContext', Copy) { ... }
            buildArgs.put("JAR_FILE", "app-${version}.jar")
            labels.put("org.opencontainers.image.revision", gitSha)

            // Image naming (choose ONE approach)
            // Approach 1: namespace + imageName
            registry.set("ghcr.io")
            namespace.set("kineticfire/stuff")
            imageName.set("time-server")

            // Approach 2: repository
            // registry.set("ghcr.io")
            // repository.set("kineticfire/stuff/time-server")

            version.set(project.version.toString())
            tags.set(["latest", "1.0.0"])

            save {
                compression.set(SaveCompression.GZIP)
                outputFile.set(layout.buildDirectory.file("docker-images/time-server.tar.gz"))
            }

            publish {
                to("localRegistry") {
                    registry.set("localhost:25000")
                    namespace.set("test")
                    publishTags.set(["edge", "test"])
                }
            }
        }
    }
}
```

**SourceRef Mode DSL** (using existing images):
```groovy
docker {
    images {
        existingImage {
            // Use existing image - NO build properties allowed
            sourceRef = "ghcr.io/acme/myapp:1.2.3"

            // Apply new local tags to the sourceRef
            tags.set(["local:latest", "local:stable"])

            save {
                compression.set(SaveCompression.GZIP)
                outputFile.set(layout.buildDirectory.file("docker-images/existing-image.tar.gz"))
                pullIfMissing.set(true)  // Pull sourceRef if not local

                // Authentication for private registries (registry extracted from sourceRef)
                auth {
                    username.set("myuser")
                    password.set("mypass")
                    // NO serverAddress - extracted from sourceRef automatically
                }
            }

            publish {
                to("dockerhub") {
                    registry.set("docker.io")
                    repository.set("acme/myapp")
                    publishTags.set(["published-latest"])
                }
            }
        }
    }
}
```

## **Phase 3: Task Implementation Updates**

### **Step 3.1: Update DockerBuildTask**
- **Build Mode ONLY support**:
  - Use new nomenclature to construct full image references
  - Validate that sourceRef is NOT set (mutually exclusive with building)
  - Throw error if sourceRef is provided (building and existing image reference are mutually exclusive)
- Support labels via `--label` arguments to `docker build` (Build Mode only)
- Maintain Provider API patterns (only call `.get()` in `@TaskAction`)
- **Add missing properties**:
  - All nomenclature properties: `registry`, `namespace`, `imageName`, `repository`, `version`, `tags`, `labels`
  - **NO sourceRef property** - mutually exclusive with building new images

### **Step 3.2: Update DockerTagTask**
- **Dual-mode support**:
  - **Build Mode**: Construct source and target references from nomenclature
  - **SourceRef Mode**: Use sourceRef as source, apply tags as targets
- **Restore missing properties**:
  - `Property<String> sourceRef` (**CRITICAL** - was incorrectly removed in v1)
  - All nomenclature properties for Build Mode support
- Handle both modes in `buildImageReferences()` method

### **Step 3.3: Update DockerSaveTask**
- Use `SaveCompression` enum instead of string
- **Restore missing properties**:
  - `Property<String> sourceRef` (**CRITICAL** - was incorrectly removed in v1)
  - `Property<Boolean> pullIfMissing` (**CRITICAL** - was incorrectly removed in v1)
  - All nomenclature properties for Build Mode support
- **Dual-mode support**:
  - **Build Mode**: Use nomenclature to build primary image reference
  - **SourceRef Mode**: Use sourceRef as image reference
- **Enhanced pullIfMissing logic**: Pull image with auth if `pullIfMissing=true` and image not local
- **Authentication support**: Support auth for pulling from private registries

### **Step 3.4: Update DockerPublishTask**
- **Dual-mode support**:
  - **Build Mode**: Use nomenclature + publish target configuration
  - **SourceRef Mode**: Use sourceRef + publish target configuration
- Maintain authentication handling for publish operations
- **Add missing properties**:
  - All nomenclature properties for Build Mode support
  - SourceRef properties for SourceRef Mode support

## **Phase 4: Service Layer Configuration Cache Compatibility**

### **Step 4.1: Make Services Serializable**
- Ensure `DockerService`, `ComposeService`, `JsonService` implementations are serializable
- Remove any `Project` references in service implementations
- Use `@Input` properties for service configuration
- **Update DockerService.saveImage signature**: Use `SaveCompression` instead of old string type

### **Step 4.2: Use Build Services Pattern**
- Register services as Gradle Build Services for proper configuration cache support
- Ensure thread-safety in service implementations

## **Phase 5: Plugin Infrastructure Updates**

### **Step 5.1: Update GradleDockerPlugin**
- Modify task registration to use Provider API
- Update task configuration using lazy evaluation
- Remove eager property access during configuration
- **Implement dual-mode support** in task generation logic

### **Step 5.2: Task Registration with Providers**
```groovy
tasks.register("dockerBuild${capitalizedName}", DockerBuildTask) { task ->
    // Build Mode properties ONLY
    task.registry.set(imageSpec.registry)
    task.namespace.set(imageSpec.namespace)
    task.imageName.set(imageSpec.imageName)
    task.repository.set(imageSpec.repository)
    task.version.set(imageSpec.version)
    task.tags.set(imageSpec.tags)
    task.labels.set(imageSpec.labels)

    // NO sourceRef - mutually exclusive with building
    // Validation in task will ensure sourceRef is not set in ImageSpec

    // ... other provider-based configuration
}

tasks.register("dockerSave${capitalizedName}", DockerSaveTask) { task ->
    // Build Mode properties
    task.registry.set(imageSpec.registry)
    task.namespace.set(imageSpec.namespace)
    task.imageName.set(imageSpec.imageName)
    task.repository.set(imageSpec.repository)
    task.version.set(imageSpec.version)
    task.tags.set(imageSpec.tags)

    // SourceRef Mode properties
    task.sourceRef.set(imageSpec.sourceRef)

    // Save-specific properties
    task.compression.set(imageSpec.save.compression)
    task.outputFile.set(imageSpec.save.outputFile)
    task.pullIfMissing.set(imageSpec.save.pullIfMissing)
    // Auth configuration passed through
}
```

## **Phase 6: Testing Strategy**

### **Step 6.1: Unit Test Updates**
- **Fix critical API mismatch**: Update all tests expecting `sourceRef` and `pullIfMissing` properties
- **DockerTagTaskTest**: Restore tests for `sourceRef`, remove tests for non-existent properties
- **DockerSaveTaskTest**: Add tests for `pullIfMissing`, `sourceRef`, and authentication
- **DockerExtensionTest**: Test dual-mode validation rules
- **ImageSpecTest**: Test property validation and Provider API usage for both modes
- **SaveSpecTest**: Test authentication configuration and pullIfMissing logic
- **Task Tests**: Update to use correct API (both Build and SourceRef modes)
- **Service Tests**: Ensure configuration cache compatibility
- **REQUIREMENT**: **ALL (100%) of unit tests must pass**
  - Do not declare success until every unit test passes
  - Run: `./gradlew clean test` (from `plugin/` directory)
  - Do not treat partial pass rates (e.g., "most tests passed") as acceptable
  - Achieve 100% line and branch coverage as measured by JaCoCo
  - Document any gaps in `docs/design-docs/testing/unit-test-gaps.md`

### **Step 6.2: Functional Test Updates**
- Update functional tests to use new DSL structure
- Test both Build Mode and SourceRef Mode scenarios
- Test configuration cache compatibility: `--configuration-cache`
- Test build cache reuse scenarios
- **REQUIREMENT**: **ALL (100%) of functional tests must pass**
  - Do not declare success until every functional test passes
  - Run: `./gradlew clean functionalTest` (from `plugin/` directory)
  - Do not treat partial pass rates (e.g., "most tests passed") as acceptable
  - Handle TestKit dependency issues per `docs/design-docs/functional-test-testkit-gradle-issue.md`

### **Step 6.3: Integration Test Updates**
- Update integration test scenarios in `plugin-integration-test/docker/`
- Test real Docker operations with new nomenclature (Build Mode)
- Test real Docker operations with sourceRef (SourceRef Mode)
- Test pullIfMissing with authentication against local test registries
- Verify end-to-end workflows with registry operations
- **REQUIREMENT**: **ALL (100%) of integration tests must pass**
  - Do not declare success until every integration test passes
  - Run:
    - Rebuild plugin to Maven local: `./gradlew build publishToMavenLocal` (from `plugin/` directory)
    - Run tests: `./gradlew clean testAll integrationTestComprehensive` (from `plugin-integration-test/` directory)
  - Do not treat partial pass rates (e.g., "most tests passed") as acceptable
  - Write real, end-to-end integration tests that exercise the plugin exactly like a user would
  - Follow ground rules: no mocks/stubs/fakes for Docker, Compose, filesystem, or network

## **Phase 7: Validation and Documentation**

### **Step 7.1: Configuration Cache Verification**
```bash
# Test configuration cache storage and reuse
./gradlew dockerBuild --configuration-cache
./gradlew dockerBuild --configuration-cache  # Should reuse cache
```

### **Step 7.2: Coverage Verification**
- Achieve 100% unit test coverage
- Document any gaps in `docs/design-docs/testing/unit-test-gaps.md`
- Verify integration test coverage

### **Step 7.3: Final Acceptance Criteria**
- **The plugin must build successfully**
  - Do not declare success until the build completes without errors
  - Run: `./gradlew build` (from `plugin/` directory)
- **The plugin usage demo must work successfully**
  - Do not declare success until the build completes without errors
  - Run: `./gradlew clean dockerBuild` (from `plugin-usage-demo` directory)
- **No lingering containers may remain**
  - Do not declare success until `docker ps -a` shows no containers
  - Do not treat "some leftover containers are acceptable" as valid

## **Implementation Phases Summary**

1. **Phase 1-2**: Foundation and API changes (implement dual-mode with preserved sourceRef/pullIfMissing)
2. **Phase 3-4**: Task and service updates (restore missing properties, implement dual-mode support correctly)
3. **Phase 5**: Plugin infrastructure (configuration and registration with dual-mode support)
4. **Phase 6**: Comprehensive testing (fix 158 failing tests, validation with 100% pass requirements)
5. **Phase 7**: Final verification and documentation

## **Critical Architectural Corrections from Previous Plans**

- **Preserve `sourceRef` property**: Required for SourceRef Mode (existing images) - **NOT REMOVED**
- **Preserve `pullIfMissing` property**: Required for SaveSpec with authentication - **NOT REMOVED**
- **Add authentication to SaveSpec**: Consistent with PublishSpec auth DSL
- **Implement dual-mode validation**: Build Mode vs SourceRef Mode have different validation rules
- **Fix task API**: Support both nomenclature and sourceRef approaches in ALL tasks
- **Correct test expectations**: Tests expect sourceRef/pullIfMissing properties that were incorrectly planned for removal
- **Property naming**: Use "imageName" instead of "name" to avoid namespace conflicts

## **Key Benefits**

- **Full Gradle 9 compatibility**: Configuration cache, build cache, Provider API
- **Proper Docker nomenclature**: Registry, namespace, imageName, tag separation for new builds
- **Preserved sourceRef workflow**: Support for existing/pre-built images (backward compatibility)
- **Labels support**: New feature for OCI image metadata (Build Mode only)
- **Enhanced validation**: Comprehensive dual-mode validation
- **PullIfMissing with Auth**: Complete private registry support for saves
- **Better performance**: Lazy evaluation, proper caching
- **Maintainability**: Clear separation of concerns, proper abstractions

## **Success Criteria**

This refactoring is considered successful when:

1. **ALL unit tests pass (100%)** - Address the 158 currently failing tests due to API mismatch
2. **ALL functional tests pass (100%)**
3. **ALL integration tests pass (100%)**
4. **Plugin builds successfully without errors**
5. **Configuration cache works correctly** (stores and reuses)
6. **New Docker nomenclature API works as designed** (Build Mode)
7. **SourceRef workflow preserved and enhanced** (SourceRef Mode with pullIfMissing + auth)
8. **Labels feature is fully implemented and tested**
9. **PullIfMissing with authentication works correctly**
10. **No breaking changes to existing task generation patterns**
11. **100% test coverage maintained or documented gaps explained**
12. **No residual containers after test runs**

## **Final Implementation Priority**

**HIGHEST PRIORITY**: Fix the 158 failing unit tests by restoring missing `sourceRef` and `pullIfMissing` properties to 
all task classes and updating validation logic to support dual-mode operation.

This consolidated plan ensures the plugin meets all requirements while maintaining full backward compatibility for 
sourceRef workflows and implementing the new nomenclature system correctly, addressing all critical issues identified in 
the analysis.