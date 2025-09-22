# Unit Test Enhancement Plan for Gradle Docker Plugin (Non-Compose Scope)

## Scope Statement

**This plan covers unit testing for all Docker functionality EXCEPT Docker Compose features.**

Excluded from this plan:
- Docker Compose services (`ExecLibraryComposeService`, `ComposeService`)
- Docker Compose tasks (`ComposeUpTask`, `ComposeDownTask`)
- Docker Compose specifications (`ComposeStackSpec`, `ComposeConfig`)
- Docker Compose JUnit extensions (`DockerComposeClassExtension`, `DockerComposeMethodExtension`)
- Docker Compose models (`ComposeState`, `ComposeConfig`)
- All compose-related functionality in `DockerOrchExtension`

These compose features will be covered in a separate unit test plan.

## Executive Summary

Current unit test coverage for non-compose Docker functionality is approximately **60-65%** with significant gaps in Docker service implementation, Docker tasks, and validation logic. This plan outlines the systematic approach to achieve **100% unit and branch coverage** for all non-compose Docker features through comprehensive testing with mocks for external dependencies.

## Current Coverage Analysis (Non-Compose Only)

### Overall Metrics (Estimated for Non-Compose)
- **Instructions**: ~60% (excluding compose-related code)
- **Branches**: ~50% (excluding compose-related code)
- **Methods**: ~65% (excluding compose-related code)
- **Classes**: ~75% (excluding compose-related classes)

### Package-Level Coverage (Non-Compose Components)

| Package/Component | Instructions | Branches | Priority | Issues |
|-------------------|-------------|----------|----------|--------|
| `DockerServiceImpl` | 10% | 20% | **CRITICAL** | Docker client calls unmocked |
| `DockerBuildTask` | 70% | 55% | **HIGH** | Missing edge cases |
| `DockerSaveTask` | 65% | 50% | **HIGH** | Dual-mode paths untested |
| `DockerTagTask` | 68% | 52% | **HIGH** | Reference building gaps |
| `DockerPublishTask` | 60% | 45% | **HIGH** | Auth scenarios untested |
| `DockerExtension` | 75% | 70% | **MEDIUM** | Validation gaps |
| `ImageSpec` | 92% | 100% | **LOW** | Near complete |
| `SaveSpec` | 90% | 95% | **LOW** | Minor gaps |
| `PublishSpec` | 88% | 90% | **LOW** | Target validation |
| `AuthSpec` | 95% | 100% | **LOW** | Nearly complete |
| `SaveCompression` | 100% | 100% | **DONE** | Complete |
| `DockerServiceException` | 100% | 100% | **DONE** | Complete |

## Testing Strategy (Non-Compose Focus)

### 1. Docker Service Layer (`DockerServiceImpl`)
**Current**: 10% coverage  
**Target**: 100% coverage

**Mockable Components**:
- `DockerClient` (docker-java API)
- `ExecutorService` for async operations
- File I/O for image save operations
- Network operations for push/pull

**Testing Approach**:
```groovy
// Comprehensive mock pattern for DockerServiceImpl
class DockerServiceImplTest extends Specification {
    DockerClient mockDockerClient = Mock()
    ExecutorService mockExecutor = Mock()
    
    def "buildImage with all nomenclature parameters"() {
        given:
        def service = new DockerServiceImpl() {
            @Override
            protected DockerClient createDockerClient() { mockDockerClient }
        }
        def buildCmd = Mock(BuildImageCmd)
        def callback = Mock(BuildImageResultCallback)
        mockDockerClient.buildImageCmd() >> buildCmd
        buildCmd.exec(_) >> callback
        callback.awaitImageId() >> "sha256:123abc"
        
        when:
        def result = service.buildImage(context).get()
        
        then:
        1 * buildCmd.withDockerfile(_)
        1 * buildCmd.withTags(["registry.io/namespace/name:tag"])
        1 * buildCmd.withLabels(["version": "1.0.0"])
        1 * buildCmd.withBuildArgs(["JAR": "app.jar"])
        result == "sha256:123abc"
    }
    
    def "saveImage with compression"() {
        given:
        def service = new DockerServiceImpl() {
            @Override
            protected DockerClient createDockerClient() { mockDockerClient }
        }
        def saveCmd = Mock(SaveImageCmd)
        mockDockerClient.saveImageCmd("image:tag") >> saveCmd
        
        when:
        service.saveImage("image:tag", path, SaveCompression.GZIP).get()
        
        then:
        1 * saveCmd.exec() >> Mock(InputStream)
        // Verify GZIP compression applied
    }
    
    def "pullImage with authentication"() {
        given:
        def pullCmd = Mock(PullImageCmd)
        mockDockerClient.pullImageCmd("private/image:tag") >> pullCmd
        
        when:
        service.pullImage("private/image:tag", authConfig).get()
        
        then:
        1 * pullCmd.withAuthConfig(_)
        1 * pullCmd.exec(_)
    }
}
```

### 2. Docker Task Testing (Non-Compose Tasks Only)

#### `DockerBuildTask`
**Current**: 70% coverage  
**Target**: 100% coverage

**Missing Coverage Areas**:
- Nomenclature validation (registry/namespace/imageName)
- Dual-mode logic (sourceRef vs build mode)
- Label application
- Error scenarios

```groovy
class DockerBuildTaskTest extends Specification {
    DockerService mockService = Mock()
    
    def "buildImage with new nomenclature - repository format"() {
        given:
        def task = createTask()
        task.dockerService.set(mockService)
        task.repository.set("namespace/imagename")
        task.registry.set("ghcr.io")
        task.tags.set(["1.0.0", "latest"])
        task.labels.set(["maintainer": "team"])
        
        when:
        task.buildImage()
        
        then:
        1 * mockService.buildImage({ BuildContext ctx ->
            ctx.imageRefs.contains("ghcr.io/namespace/imagename:1.0.0") &&
            ctx.imageRefs.contains("ghcr.io/namespace/imagename:latest") &&
            ctx.labels["maintainer"] == "team"
        })
    }
    
    def "buildImage with nomenclature - namespace and imageName format"() {
        given:
        def task = createTask()
        task.namespace.set("mycompany")
        task.imageName.set("myapp")
        task.registry.set("docker.io")
        task.tags.set(["v2.0"])
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == ["docker.io/mycompany/myapp:v2.0"]
    }
    
    def "buildImage validates mutual exclusivity"() {
        given:
        def task = createTask()
        task.repository.set("repo/name")
        task.namespace.set("namespace")
        task.imageName.set("name")
        
        when:
        task.buildImage()
        
        then:
        thrown(IllegalStateException)
    }
}
```

#### `DockerSaveTask`
**Current**: 65% coverage  
**Target**: 100% coverage

**Missing Coverage**:
- SourceRef mode with pullIfMissing
- Authentication for private registry pulls
- All compression formats
- Dual-mode reference building

```groovy
class DockerSaveTaskTest extends Specification {
    def "saveImage in sourceRef mode with pullIfMissing"() {
        given:
        def task = createTask()
        task.sourceRef.set("existing:image")
        task.pullIfMissing.set(true)
        task.auth.set(createAuthSpec())
        
        when:
        task.saveImage()
        
        then:
        1 * mockService.imageExists("existing:image") >> false
        1 * mockService.pullImage("existing:image", _)
        1 * mockService.saveImage("existing:image", _, SaveCompression.GZIP)
    }
    
    def "saveImage with each compression type"() {
        expect:
        testCompression(compression)
        
        where:
        compression << SaveCompression.values()
    }
}
```

#### `DockerTagTask`
**Current**: 68% coverage  
**Target**: 100% coverage

```groovy
class DockerTagTaskTest extends Specification {
    def "tagImage in dual-mode scenarios"() {
        given:
        def task = createTask()
        task.sourceRef.set(sourceRef)
        task.repository.set(repository)
        task.tags.set(tags)
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == expectedRefs
        
        where:
        sourceRef | repository | tags | expectedRefs
        "old:1.0" | null | ["new:2.0"] | ["old:1.0", "new:2.0"]
        null | "repo/name" | ["1.0", "2.0"] | ["repo/name:1.0", "repo/name:2.0"]
    }
}
```

#### `DockerPublishTask`
**Current**: 60% coverage  
**Target**: 100% coverage

```groovy
class DockerPublishTaskTest extends Specification {
    def "publishImage with authentication scenarios"() {
        given:
        def task = createTask()
        task.configureForPublish(publishSpec)
        
        when:
        task.publishImage()
        
        then:
        1 * mockService.pushImage(_, expectedAuth)
        
        where:
        authType | expectedAuth
        "basic" | AuthConfig(username: "user", password: "pass")
        "token" | AuthConfig(registryToken: "token123")
        "helper" | AuthConfig(helper: "ecr-login")
    }
}
```

### 3. Extension and Validation Testing (Non-Compose)

#### `DockerExtension`
**Current**: 75% coverage  
**Target**: 100% coverage

**Missing Coverage**:
- Nomenclature validation rules
- Mutual exclusivity validation
- Edge cases in image reference validation

```groovy
class DockerExtensionTest extends Specification {
    def "validateNomenclature enforces mutual exclusivity"() {
        given:
        def extension = new DockerExtension()
        def imageSpec = Mock(ImageSpec)
        imageSpec.repository.present >> true
        imageSpec.repository.get() >> "repo/name"
        imageSpec.namespace.present >> true
        imageSpec.imageName.present >> true
        
        when:
        extension.validateNomenclature(imageSpec)
        
        then:
        thrown(GradleException)
    }
    
    def "validates Docker image reference formats"() {
        expect:
        extension.isValidImageReference(ref) == valid
        
        where:
        ref | valid
        "image:tag" | true
        "registry.io/namespace/name:tag" | true
        "invalid::tag" | false
        "no-tag" | false
    }
}
```

### 4. Model and Spec Testing (Non-Compose)

**Target**: 100% coverage for all non-compose models

```groovy
class ImageSpecTest extends Specification {
    def "all DSL methods work with Provider API"() {
        given:
        def spec = new ImageSpec("test", project)
        
        when:
        spec.label("key", provider { "value" })
        spec.buildArg("arg", provider { "val" })
        
        then:
        spec.labels.get()["key"] == "value"
        spec.buildArgs.get()["arg"] == "val"
    }
}
```

## Implementation Plan (Non-Compose Focus)

### Phase 1: Critical Docker Components (Week 1)
1. **Day 1-2**: Docker service layer mocking
   - Create MockDockerClient with full API coverage
   - Test all Docker operations (build, tag, save, push, pull)
   - Handle async operations properly

2. **Day 3-4**: Docker task comprehensive testing
   - DockerBuildTask with nomenclature scenarios
   - DockerSaveTask with dual-mode and compression
   - DockerTagTask with reference building

3. **Day 5**: Docker publish task
   - Authentication scenarios
   - Registry interactions
   - Error handling

### Phase 2: Validation and Extensions (Week 2)
1. **Day 1-2**: DockerExtension validation
   - Nomenclature validation rules
   - Image reference validation
   - Mutual exclusivity checks

2. **Day 3-4**: Spec classes completion
   - ImageSpec DSL methods
   - SaveSpec with compression
   - PublishSpec with targets
   - AuthSpec scenarios

3. **Day 5**: Plugin configuration
   - Task registration for Docker tasks only
   - Configuration cache compatibility

### Phase 3: Completion (Days 11-13)
1. **Day 11**: Model classes
   - SaveCompression enum
   - AuthConfig
   - ImageRefParts
   - BuildContext

2. **Day 12**: Error handling
   - DockerServiceException scenarios
   - Validation error messages
   - Edge cases

3. **Day 13**: Final verification
   - Coverage report analysis
   - Performance testing
   - Documentation updates

## Test Infrastructure Requirements (Docker-Specific)

### 1. Mock Utilities
Create `plugin/src/test/groovy/com/kineticfire/gradle/docker/mocks/`:
- `MockDockerClient.groovy` - Full docker-java API mock
- `MockDockerResponses.groovy` - Response fixtures
- `DockerTestDataFactory.groovy` - Docker-specific test data

### 2. Test Fixtures
- Docker build responses
- Image manifests
- Registry responses
- Authentication configurations

### 3. Base Test Classes
- `DockerServiceTestBase` - Docker service test setup
- `DockerTaskTestBase` - Docker task test setup
- `DockerSpecTestBase` - Spec test setup

## Unmockable Code (Docker-Specific)

### DockerServiceImpl
- `createDockerClient()` - Network socket creation (lines 75-95)
- Docker daemon ping - Actual daemon check (lines 89-90)

### Total Unmockable
- ~50-75 lines of Docker-specific code
- < 2% of non-compose codebase
- All covered by integration tests

## Success Metrics (Non-Compose Scope)

1. **100% instruction coverage** for all Docker (non-compose) code
2. **100% branch coverage** for all Docker (non-compose) code
3. **All Docker tests pass** consistently
4. **No Docker daemon calls** in unit tests
5. **Fast execution** - Docker tests under 15 seconds

### Validation Commands
```bash
# Run only non-compose tests
./gradlew test -x testCompose

# Generate coverage for Docker components
./gradlew jacocoTestReport -PexcludeCompose=true

# Verify no Docker daemon dependency
./gradlew test --offline
```

## Risk Mitigation (Docker-Specific)

### Technical Risks
1. **Docker API complexity**: docker-java API is extensive
   - **Mitigation**: Focus on used API methods only

2. **Async operations**: CompletableFuture testing
   - **Mitigation**: Use immediate executors in tests

3. **Image reference parsing**: Complex validation rules
   - **Mitigation**: Property-based testing for validation

## Conclusion

This plan focuses exclusively on achieving 100% unit test coverage for Docker functionality (excluding Docker Compose). The narrowed scope allows for:
1. Deeper testing of Docker-specific features
2. Comprehensive nomenclature validation testing
3. Complete dual-mode (sourceRef vs build) coverage
4. Thorough authentication and registry interaction testing

Estimated effort: **13 days** for one developer focusing only on Docker (non-compose) functionality.

The resulting test suite will provide complete confidence in the Docker plugin's core functionality without any dependency on Docker daemon during unit testing.

## Executive Summary

Current unit test coverage is **55.8%** (instructions) with **47.7%** branch coverage. This plan outlines the systematic approach to achieve **100% unit and branch coverage** through comprehensive testing with mocks for external dependencies.

## Current Coverage Analysis

### Overall Metrics
- **Instructions**: 55.8% (11,516/20,631)
- **Branches**: 47.7% (496/1,040)
- **Lines**: 49.0% (960/1,960)
- **Methods**: 63.9% (320/501)
- **Classes**: 72.7% (112/154)

### Package-Level Coverage

| Package | Instructions | Branches | Priority | Issues |
|---------|-------------|----------|----------|--------|
| `com.kineticfire.gradle.docker.junit` | 0.7% | 0.0% | **CRITICAL** | JUnit extensions untested |
| `com.kineticfire.gradle.docker.service` | 6.6% | 16.5% | **CRITICAL** | External service calls unmocked |
| `com.kineticfire.gradle.docker.task` | 66.7% | 51.2% | **HIGH** | Missing edge cases |
| `com.kineticfire.gradle.docker.extension` | 72.1% | 68.9% | **MEDIUM** | Validation gaps |
| `com.kineticfire.gradle.docker` | 83.6% | 56.3% | **MEDIUM** | Plugin configuration gaps |
| `com.kineticfire.gradle.docker.model` | 88.2% | 73.5% | **LOW** | Minor gaps |
| `com.kineticfire.gradle.docker.spec` | 91.4% | 100.0% | **LOW** | Near complete |
| `com.kineticfire.gradle.docker.exception` | 100.0% | 100.0% | **DONE** | Complete |

## Testing Strategy

### 1. Mock Strategy for External Dependencies

#### Docker Service Layer (`com.kineticfire.gradle.docker.service`)
**Current**: 6.6% coverage  
**Target**: 100% coverage

**Mockable Components**:
- `DockerClient` (docker-java API)
- Process execution for docker CLI
- File I/O operations
- Network operations

**Testing Approach**:
```groovy
// Example mock pattern for DockerServiceImpl
class DockerServiceImplTest extends Specification {
    DockerClient mockDockerClient = Mock()
    ExecutorService mockExecutor = Mock()
    
    def "buildImage with all parameters"() {
        given:
        def service = new DockerServiceImpl() {
            @Override
            protected DockerClient createDockerClient() { mockDockerClient }
        }
        def buildCmd = Mock(BuildImageCmd)
        mockDockerClient.buildImageCmd() >> buildCmd
        
        when:
        service.buildImage(context).get()
        
        then:
        1 * buildCmd.withDockerfile(_)
        1 * buildCmd.withTags(_)
        1 * buildCmd.withLabels(_)
        1 * buildCmd.exec(_)
    }
}
```

#### Compose Service Layer (`ExecLibraryComposeService`)
**Current**: Low coverage  
**Target**: 100% coverage

**Mockable Components**:
- ProcessBuilder
- Process execution
- File system checks

**Testing Approach**:
```groovy
class ExecLibraryComposeServiceTest extends Specification {
    def "upStack executes compose up with correct parameters"() {
        given:
        def service = Spy(ExecLibraryComposeService)
        def mockProcess = Mock(Process)
        service.executeCommand(_) >> mockProcess
        
        when:
        service.upStack(config).get()
        
        then:
        1 * service.executeCommand({ it.contains("up") })
    }
}
```

### 2. JUnit Extension Testing (`com.kineticfire.gradle.docker.junit`)
**Current**: 0.7% coverage  
**Target**: 100% coverage

**Challenge**: JUnit extensions interact with test lifecycle  
**Solution**: Mock ExtensionContext and test lifecycle callbacks

```groovy
class DockerComposeClassExtensionTest extends Specification {
    ExtensionContext mockContext = Mock()
    ExtensionContext.Store mockStore = Mock()
    
    def "beforeAll starts compose stack"() {
        given:
        def extension = new DockerComposeClassExtension()
        mockContext.getStore(_) >> mockStore
        
        when:
        extension.beforeAll(mockContext)
        
        then:
        1 * mockStore.put(_, _)
        // Verify compose up logic
    }
    
    def "afterAll stops compose stack"() {
        // Similar pattern for teardown
    }
}
```

### 3. Task Layer Testing (`com.kineticfire.gradle.docker.task`)
**Current**: 66.7% coverage  
**Target**: 100% coverage

**Missing Coverage Areas**:
- Error handling paths
- Edge cases (empty collections, null values)
- Dual-mode logic branches
- Validation failures

**Testing Approach**:
```groovy
class DockerBuildTaskTest extends Specification {
    DockerService mockService = Mock()
    
    def "buildImage handles missing dockerfile"() {
        given:
        def task = createTask()
        task.dockerService.set(mockService)
        // Don't set dockerfile
        
        when:
        task.buildImage()
        
        then:
        thrown(IllegalStateException)
    }
    
    def "buildImage with sourceRef mode"() {
        given:
        def task = createTask()
        task.sourceRef.set("existing:image")
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs.contains("existing:image")
    }
}
```

### 4. Extension and Plugin Testing
**Current**: 72.1% / 83.6% coverage  
**Target**: 100% coverage

**Missing Coverage**:
- Validation edge cases
- Configuration cache scenarios
- Task registration branches

## Implementation Plan

### Phase 1: Critical Packages (Week 1)
1. **Day 1-2**: Service layer mocking infrastructure
   - Create base test classes with mock factories
   - Implement MockDockerClient wrapper
   - Create ProcessExecutionMocker utility

2. **Day 3-4**: JUnit extension tests
   - Mock ExtensionContext hierarchy
   - Test all lifecycle callbacks
   - Cover error scenarios

3. **Day 5**: Service implementation tests
   - DockerServiceImpl with mocked DockerClient
   - ExecLibraryComposeService with mocked processes
   - JsonServiceImpl with test fixtures

### Phase 2: High Priority (Week 2)
1. **Day 1-2**: Task layer comprehensive testing
   - All task classes with mocked services
   - Error handling paths
   - Edge cases and validation

2. **Day 3-4**: Extension validation testing
   - DockerExtension validation logic
   - Nomenclature validation
   - Dual-mode validation

3. **Day 5**: Plugin configuration testing
   - Task registration scenarios
   - Configuration cache compatibility

### Phase 3: Completion (Week 3)
1. **Day 1-2**: Model classes gap coverage
   - Remaining constructors
   - Edge case handling
   - Serialization tests

2. **Day 3-4**: Spec classes final coverage
   - DSL method variations
   - Provider API edge cases

3. **Day 5**: Final verification and documentation
   - Run full test suite with coverage
   - Document remaining gaps
   - Update unit-test-gaps.md

## Test Infrastructure Requirements

### 1. Mock Utilities Package
Create `plugin/src/test/groovy/com/kineticfire/gradle/docker/mocks/`:
- `MockDockerClient.groovy` - Configurable Docker client mock
- `MockProcessExecutor.groovy` - Process execution mock
- `MockFileSystem.groovy` - File operations mock
- `TestDataFactory.groovy` - Test data builders

### 2. Test Fixtures
Enhance `plugin/src/test/groovy/com/kineticfire/gradle/docker/fixture/`:
- Docker response fixtures
- Compose configuration fixtures
- Error scenario generators

### 3. Base Test Classes
Create abstract test bases:
- `ServiceTestBase` - Common service test setup
- `TaskTestBase` - Common task test setup
- `ExtensionTestBase` - Common extension test setup

## Unmockable Code Documentation

Code that genuinely cannot be unit tested will be documented in `docs/design-docs/testing/unit-test-gaps.md`:

### Expected Unmockable Areas
1. **Native process spawning**: Actual OS process creation
2. **Network socket creation**: Raw socket operations
3. **File system watchers**: Native file system events
4. **Thread management**: Low-level thread operations
5. **Gradle internal APIs**: Some Gradle internals that require runtime

### Documentation Format
```markdown
## Class: DockerServiceImpl
### Method: createDockerClient() 
- **Lines**: 75-95
- **Reason**: Creates actual network socket to Docker daemon
- **Coverage**: Integration tests verify this
- **Risk**: Low - fails fast with clear error

## Class: ExecLibraryComposeService  
### Method: validateDockerCompose()
- **Lines**: 35-55
- **Reason**: Spawns actual OS process to check Docker installation
- **Coverage**: Integration tests verify this
- **Risk**: Low - one-time validation at startup
```

## Success Metrics

### Required Outcomes
1. **100% instruction coverage** (excluding documented gaps)
2. **100% branch coverage** (excluding documented gaps)
3. **All tests pass** consistently
4. **No flaky tests** - all tests deterministic
5. **Mock isolation** - no external dependencies in unit tests
6. **Fast execution** - full suite under 30 seconds

### Validation Steps
1. Run: `./gradlew clean test jacocoTestReport`
2. Verify HTML report shows 100% (or documented gaps)
3. Run tests 10 times to ensure stability
4. Measure execution time
5. Verify no network/Docker calls in unit tests

## Risk Mitigation

### Technical Risks
1. **Complex mocking**: Some Docker APIs are complex
   - **Mitigation**: Create simplified mock facades
   
2. **Gradle API testing**: Some Gradle APIs hard to test
   - **Mitigation**: Use Gradle TestKit where possible
   
3. **Threading issues**: Async code testing challenges
   - **Mitigation**: Use controlled executors in tests

### Schedule Risks
1. **Underestimated complexity**: Service layer more complex than expected
   - **Mitigation**: Start with critical packages first
   
2. **Mock infrastructure time**: Building mocks takes longer
   - **Mitigation**: Reuse existing mock libraries where possible

## Conclusion

This plan provides a systematic approach to achieve 100% unit test coverage through:
1. Comprehensive mocking of external dependencies
2. Systematic testing of all code paths
3. Clear documentation of genuinely untestable code
4. Robust test infrastructure

Total estimated effort: **3 weeks** with one developer, or **1.5 weeks** with two developers working in parallel.

The resulting test suite will be fast, deterministic, and maintainable, providing confidence in the plugin's correctness without requiring external dependencies during unit testing.