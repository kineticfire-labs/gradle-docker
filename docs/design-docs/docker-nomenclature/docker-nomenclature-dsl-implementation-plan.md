# Comprehensive Gradle Docker Plugin Refactoring Plan

Based on analysis of the codebase, design documents, and requirements, here's a comprehensive plan to refactor the plugin for Gradle 9 cache compatibility and improved Docker nomenclature.

## **Phase 1: Gradle 9 Configuration Cache Foundation**

### **Step 1.1: Replace Property Types with Gradle Provider API**
- **Target**: All specs (`ImageSpec`, `SaveSpec`, `PublishSpec`, `PublishTarget`)
- **Changes**:
  - Replace concrete properties with `Property<T>`, `ListProperty<T>`, `MapProperty<K,V>`
  - Convert `String` fields to `Property<String>`
  - Convert `List<String>` to `ListProperty<String>`
  - Convert `Map<String,String>` to `MapProperty<String,String>`
  - Add `@Input`, `@Optional`, `@Nested` annotations

### **Step 1.2: Add Docker Image Nomenclature Properties**
- **New properties in ImageSpec**:
  - `Property<String> registry` (e.g., "ghcr.io", "example.com:5000")
  - `Property<String> namespace` (e.g., "kineticfire/stuff")
  - `Property<String> name` (e.g., "my-app") 
  - `Property<String> repository` (alternative to namespace+name)
  - `Property<String> version` (defaults to project.version)
  - `ListProperty<String> tags` (actual tag names like "latest", "1.0.0")
  - `MapProperty<String,String> labels` (**NEW FEATURE**)

### **Step 1.3: Create SaveCompression Enum**
- Create `SaveCompression` enum with values: `NONE`, `GZIP`, `ZIP`, `BZIP2`, `XZ`
- Replace `Property<String> compression` with `Property<SaveCompression> compression`

## **Phase 2: API Restructure and Validation**

### **Step 2.1: Implement Validation Rules**
- **Mutual exclusivity**: If `repository` is set, then `namespace` + `name` cannot be set
- **Required fields**: Either `repository` OR `name` must be specified
- **sourceRef validation**: If `sourceRef` is set, building-related properties cannot be set
- **Consistent naming**: Validate registry/namespace/name/tag format

### **Step 2.2: Remove Legacy Tag Handling**
- Remove automatic image name derivation from task names
- Remove `isValidTagName()` and simple tag validation
- Update validation to use new nomenclature

### **Step 2.3: Update DSL Structure**
```groovy
docker {
    images {
        timeServer {
            // Build configuration
            contextTask = tasks.register('prepareTimeServerContext', Copy) { ... }
            buildArgs.put("JAR_FILE", "app-${version}.jar")
            labels.put("org.opencontainers.image.revision", gitSha)
            
            // Image naming (choose ONE approach)
            // Approach 1: namespace + name
            registry.set("ghcr.io")
            namespace.set("kineticfire/stuff")
            name.set("time-server")
            
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

## **Phase 3: Task Implementation Updates**

### **Step 3.1: Update DockerBuildTask**
- Use new nomenclature to construct full image references
- Support labels via `--label` arguments to `docker build`
- Maintain Provider API patterns (only call `.get()` in `@TaskAction`)

### **Step 3.2: Update DockerTagTask**
- Construct source and target references from new nomenclature
- Handle registry/namespace/name/tag combinations

### **Step 3.3: Update DockerSaveTask**
- Use `SaveCompression` enum instead of string
- Support new image reference construction

### **Step 3.4: Update DockerPublishTask**
- Use publish target's registry/namespace/name + publishTags
- Maintain authentication handling

## **Phase 4: Service Layer Configuration Cache Compatibility**

### **Step 4.1: Make Services Serializable**
- Ensure `DockerService`, `ComposeService`, `JsonService` implementations are serializable
- Remove any `Project` references in service implementations
- Use `@Input` properties for service configuration

### **Step 4.2: Use Build Services Pattern**
- Register services as Gradle Build Services for proper configuration cache support
- Ensure thread-safety in service implementations

## **Phase 5: Plugin Infrastructure Updates**

### **Step 5.1: Update GradleDockerPlugin**
- Modify task registration to use Provider API
- Update task configuration using lazy evaluation
- Remove eager property access during configuration

### **Step 5.2: Task Registration with Providers**
```groovy
tasks.register("dockerBuild${capitalizedName}", DockerBuildTask) { task ->
    task.registry.set(imageSpec.registry)
    task.namespace.set(imageSpec.namespace)
    task.name.set(imageSpec.name)
    task.version.set(imageSpec.version)
    task.tags.set(imageSpec.tags)
    task.labels.set(imageSpec.labels)
    // ... other provider-based configuration
}
```

## **Phase 6: Testing Strategy**

### **Step 6.1: Unit Test Updates**
- **DockerExtensionTest**: Test new nomenclature validation rules
- **ImageSpecTest**: Test property validation and Provider API usage
- **Task Tests**: Update to use new nomenclature and Provider patterns
- **Service Tests**: Ensure configuration cache compatibility
- **REQUIREMENT**: **ALL (100%) of unit tests must pass**
  - Do not declare success until every unit test passes
  - Run: `./gradlew clean test` (from `plugin/` directory)
  - Do not treat partial pass rates (e.g., "most tests passed") as acceptable
  - Achieve 100% line and branch coverage as measured by JaCoCo
  - Document any gaps in `docs/design-docs/testing/unit-test-gaps.md`

### **Step 6.2: Functional Test Updates**
- Update functional tests to use new DSL structure
- Test configuration cache compatibility: `--configuration-cache`
- Test build cache reuse scenarios
- **REQUIREMENT**: **ALL (100%) of functional tests must pass**
  - Do not declare success until every functional test passes
  - Run: `./gradlew clean functionalTest` (from `plugin/` directory)
  - Do not treat partial pass rates (e.g., "most tests passed") as acceptable
  - Handle TestKit dependency issues per `docs/design-docs/functional-test-testkit-gradle-issue.md`

### **Step 6.3: Integration Test Updates**
- Update integration test scenarios in `plugin-integration-test/docker/`
- Test real Docker operations with new nomenclature
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

1. **Phase 1-2**: Foundation and API changes (most disruptive changes)
2. **Phase 3-4**: Task and service updates (implementation details)
3. **Phase 5**: Plugin infrastructure (configuration and registration)
4. **Phase 6**: Comprehensive testing (validation with 100% pass requirements)
5. **Phase 7**: Final verification and documentation

## **Key Benefits**

- **Full Gradle 9 compatibility**: Configuration cache, build cache, Provider API
- **Proper Docker nomenclature**: Registry, namespace, name, tag separation
- **Labels support**: New feature for image metadata
- **Enhanced validation**: Comprehensive image reference validation
- **Better performance**: Lazy evaluation, proper caching
- **Maintainability**: Clear separation of concerns, proper abstractions

## **Success Criteria**

This refactoring is considered successful when:

1. **ALL unit tests pass (100%)**
2. **ALL functional tests pass (100%)**
3. **ALL integration tests pass (100%)**
4. **Plugin builds successfully without errors**
5. **Configuration cache works correctly** (stores and reuses)
6. **New Docker nomenclature API works as designed**
7. **Labels feature is fully implemented and tested**
8. **No breaking changes to task generation patterns**
9. **100% test coverage maintained or documented gaps explained**
10. **No residual containers after test runs**

This plan ensures the plugin meets all requirements while maintaining high quality standards and following Gradle 9 best practices throughout.