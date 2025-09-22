# Comprehensive Gradle Docker Plugin Refactoring Plan v2

Based on analysis of the codebase, design documents, and requirements clarification regarding `sourceRef` and `pullIfMissing` properties, here's the corrected comprehensive plan to refactor the plugin for Gradle 9 cache compatibility and improved Docker nomenclature.

## **Critical API Design Clarification**

**IMPORTANT**: The new Docker nomenclature properties should **COMPLEMENT** not **REPLACE** the existing `sourceRef` approach. The plugin must support two distinct usage modes:

1. **Build Mode**: Use nomenclature properties (registry, namespace, name, etc.) for building new images
2. **SourceRef Mode**: Use `sourceRef` property for working with existing/pre-built images

Both modes can coexist and have different validation rules and behavior patterns.

## **Phase 1: Gradle 9 Configuration Cache Foundation**

### **Step 1.1: Replace Property Types with Gradle Provider API**
- **Target**: All specs (`ImageSpec`, `SaveSpec`, `PublishSpec`, `PublishTarget`)
- **Changes**:
  - Replace concrete properties with `Property<T>`, `ListProperty<T>`, `MapProperty<K,V>`
  - Convert `String` fields to `Property<String>`
  - Convert `List<String>` to `ListProperty<String>`
  - Convert `Map<String,String>` to `MapProperty<String,String>`
  - Add `@Input`, `@Optional`, `@Nested` annotations

### **Step 1.2: Add Docker Image Nomenclature Properties to ImageSpec**
- **New properties for Build Mode**:
  - `Property<String> registry` (e.g., "ghcr.io", "example.com:5000")
  - `Property<String> namespace` (e.g., "kineticfire/stuff")
  - `Property<String> imageName` (e.g., "my-app") 
  - `Property<String> repository` (alternative to namespace+imageName)
  - `Property<String> version` (defaults to project.version)
  - `ListProperty<String> tags` (actual tag names like "latest", "1.0.0")
  - `MapProperty<String,String> labels` (**NEW FEATURE**)

- **Keep existing properties for SourceRef Mode**:
  - `Property<String> sourceRef` (**KEEP** - for existing images)

### **Step 1.3: Create SaveCompression Enum and Update SaveSpec**
- Create `SaveCompression` enum with values: `NONE`, `GZIP`, `ZIP`, `BZIP2`, `XZ`
- Replace `Property<String> compression` with `Property<SaveCompression> compression`
- **Add `pullIfMissing` support to SaveSpec**:
  - `Property<Boolean> pullIfMissing` (pulls sourceRef if not local when true)
  - **Add authentication support to SaveSpec** (consistent with PublishSpec):
    - `AuthSpec auth` (for pulling from private registries)
    - Properties: `username`, `password`, `registryToken`, `helper`
    - **NO `serverAddress`** - extracted automatically from `sourceRef`

## **Phase 2: API Restructure and Validation**

### **Step 2.1: Implement Dual-Mode Validation Rules**

**Build Mode Validation** (when building new images):
- **Mutual exclusivity**: If `repository` is set, then `namespace` + `imageName` cannot be set
- **Required fields**: Either `repository` OR `imageName` must be specified
- **sourceRef exclusivity**: If `sourceRef` is set, building-related properties cannot be set (contextTask, buildArgs, labels, dockerfile, namespace, imageName, repository)

**SourceRef Mode Validation** (when using existing images):
- **sourceRef required**: `sourceRef` must be set and valid image reference
- **Build exclusivity**: Cannot set build-related properties when `sourceRef` is used
- **pullIfMissing logic**: Only applies when `sourceRef` is set
- **Auth requirement**: If `pullIfMissing=true` and registry is private, auth must be configured

### **Step 2.2: Remove Legacy Tag Handling**
- Remove automatic image name derivation from task names
- Remove `isValidTagName()` and simple tag validation
- Update validation to use new nomenclature

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
- **Build Mode**: Use new nomenclature to construct full image references
- **SourceRef Mode**: Use sourceRef as primary image reference
- Support labels via `--label` arguments to `docker build` (Build Mode only)
- Maintain Provider API patterns (only call `.get()` in `@TaskAction`)

### **Step 3.2: Update DockerTagTask**
- **Build Mode**: Construct source and target references from nomenclature
- **SourceRef Mode**: Use sourceRef as source, apply tags as targets
- **Add missing properties**:
  - `Property<String> sourceRef` (was incorrectly removed)
  - Handle both modes in `buildImageReferences()` method

### **Step 3.3: Update DockerSaveTask**
- Use `SaveCompression` enum instead of string
- **Add missing properties**:
  - `Property<String> sourceRef` (was incorrectly removed)
  - `Property<Boolean> pullIfMissing` (was incorrectly removed)
- **Dual-mode support**:
  - **Build Mode**: Use nomenclature to build primary image reference
  - **SourceRef Mode**: Use sourceRef as image reference
- **PullIfMissing logic**: Pull image with auth if `pullIfMissing=true` and image not local
- **Authentication**: Support auth for pulling from private registries

### **Step 3.4: Update DockerPublishTask**
- **Build Mode**: Use nomenclature + publish target configuration
- **SourceRef Mode**: Use sourceRef + publish target configuration
- Maintain authentication handling for publish operations

## **Phase 4: Service Layer Configuration Cache Compatibility**

### **Step 4.1: Make Services Serializable**
- Ensure `DockerService`, `ComposeService`, `JsonService` implementations are serializable
- Remove any `Project` references in service implementations
- Use `@Input` properties for service configuration
- **Update DockerService.saveImage signature**: Use `SaveCompression` instead of old type

### **Step 4.2: Use Build Services Pattern**
- Register services as Gradle Build Services for proper configuration cache support
- Ensure thread-safety in service implementations

## **Phase 5: Plugin Infrastructure Updates**

### **Step 5.1: Update GradleDockerPlugin**
- Modify task registration to use Provider API
- Update task configuration using lazy evaluation
- Remove eager property access during configuration
- **Restore dual-mode support** in task generation logic

### **Step 5.2: Task Registration with Providers**
```groovy
tasks.register("dockerBuild${capitalizedName}", DockerBuildTask) { task ->
    // Build Mode properties
    task.registry.set(imageSpec.registry)
    task.namespace.set(imageSpec.namespace)
    task.imageName.set(imageSpec.imageName)
    task.repository.set(imageSpec.repository)
    task.version.set(imageSpec.version)
    task.tags.set(imageSpec.tags)
    task.labels.set(imageSpec.labels)
    
    // SourceRef Mode properties
    task.sourceRef.set(imageSpec.sourceRef)
    
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
- **Fix API mismatch**: Update all tests expecting `sourceRef` and `pullIfMissing` properties
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

1. **Phase 1-2**: Foundation and API changes (fix missing sourceRef/pullIfMissing properties)
2. **Phase 3-4**: Task and service updates (implement dual-mode support correctly)
3. **Phase 5**: Plugin infrastructure (configuration and registration with dual-mode)
4. **Phase 6**: Comprehensive testing (validation with 100% pass requirements - fix 158 failing tests)
5. **Phase 7**: Final verification and documentation

## **Key Architectural Corrections**

- **Restore `sourceRef` property**: Required for SourceRef Mode (existing images)
- **Restore `pullIfMissing` property**: Required for SaveSpec with authentication
- **Add authentication to SaveSpec**: Consistent with PublishSpec auth DSL
- **Implement dual-mode validation**: Build Mode vs SourceRef Mode have different rules
- **Fix task logic**: Support both nomenclature and sourceRef approaches
- **Correct test expectations**: Tests expect sourceRef/pullIfMissing properties that were incorrectly removed

## **Key Benefits**

- **Full Gradle 9 compatibility**: Configuration cache, build cache, Provider API
- **Proper Docker nomenclature**: Registry, namespace, imageName, tag separation for new builds
- **Preserved sourceRef workflow**: Support for existing/pre-built images
- **Labels support**: New feature for image metadata (Build Mode only)
- **Enhanced validation**: Comprehensive dual-mode validation
- **PullIfMissing with Auth**: Complete private registry support for saves
- **Better performance**: Lazy evaluation, proper caching
- **Maintainability**: Clear separation of concerns, proper abstractions

## **Success Criteria**

This refactoring is considered successful when:

1. **ALL unit tests pass (100%)** - Currently 158 failing due to API mismatch
2. **ALL functional tests pass (100%)**
3. **ALL integration tests pass (100%)**
4. **Plugin builds successfully without errors**
5. **Configuration cache works correctly** (stores and reuses)
6. **New Docker nomenclature API works as designed** (Build Mode)
7. **SourceRef workflow preserved and enhanced** (SourceRef Mode)
8. **Labels feature is fully implemented and tested**
9. **PullIfMissing with authentication works correctly**
10. **No breaking changes to task generation patterns**
11. **100% test coverage maintained or documented gaps explained**
12. **No residual containers after test runs**

This corrected plan ensures the plugin meets all requirements while maintaining backward compatibility for sourceRef workflows and implementing the new nomenclature system correctly.