# Technical Specifications Document (TSD)

**Status:** Draft  
**Version:** 1.0.0  
**Last Updated:** 2025-08-31  
**Traceability:** Links to FSD v1.0.0 and NFSD v1.0.0

A Technical Specifications Document (TSD) details how the system will be built, providing developers with the technical 
blueprint for the architecture, data models, integrations, and implementation details needed to fulfill the FSD's and 
NFSD's requirements.

## Executive Summary & Context

### Goals
The `gradle-docker` plugin provides comprehensive Docker integration for Gradle projects, enabling developers to build, tag, save, and publish Docker images, as well as orchestrate Docker Compose services for testing. The plugin is designed to support modern Gradle development practices while providing a clean, intuitive DSL for Docker operations.

### Approach
The plugin follows a modular architecture with clear separation of concerns:
- **DSL Layer**: Provides intuitive configuration syntax for Docker and Compose operations
- **Service Layer**: Abstracts external dependencies (Docker Java Client, exec library) through well-defined interfaces
- **Task Layer**: Implements Gradle tasks with proper dependency management and modern Gradle features
- **Integration Layer**: Seamlessly integrates with Gradle's test framework for container-based testing

### Solution Overview
Key architectural decisions include:
- **Hybrid Library Strategy**: Docker Java Client for daemon operations, exec library for Compose operations
- **Modern Gradle Integration**: Full support for Provider API, Configuration Cache, and lazy evaluation
- **Multi-Image Support**: Per-image task generation with intelligent dependency management
- **Test Integration**: First-class support for container-based testing with lifecycle management

### Acceptance Criteria
- All unit, functional, and integration tests pass
- 100% line coverage and 100% branch coverage achieved
- Performance targets met (clean build <60s, unit tests <30s, functional tests <2min)
- Cross-platform compatibility verified (Linux, macOS, Windows WSL2)
- Real Docker daemon integration validated

### User Experience
Users apply the plugin and configure Docker operations using an intuitive DSL:

```groovy
plugins { 
    id "com.kineticfire.gradle.gradle-docker" version "1.0.0" 
}

docker {
    images {
        image("alpine") {
            context = file("src/main/docker")
            tags = ["myapp:${version}-alpine", "myapp:alpine"]
        }
    }
}
```

Common usage patterns:
```bash
# Build all configured Docker images
./gradlew dockerBuild

# Build specific images
./gradlew dockerBuildAlpine dockerBuildUbuntu

# Save images to tar.gz files
./gradlew dockerSave

# Publish to configured registries
./gradlew dockerPublish

# Run tests with Docker Compose orchestration
./gradlew functionalTest integrationTest
```

## System Architecture

### High-Level Architecture

The plugin integrates with Gradle through standard extension mechanisms and provides two main DSL entry points:
- `docker { }` - For Docker image operations (build, tag, save, publish)
- `dockerOrch { }` - For Docker Compose orchestration and testing

**Component Interaction Flow:**
1. User configures DSL in `build.gradle`
2. Plugin registers tasks based on configuration
3. Tasks execute through service abstractions
4. Services interact with external systems (Docker daemon, registries)
5. Results are captured and made available to subsequent tasks

### Architectural Components and Their Interactions

#### Plugin Core
- **GradleDockerPlugin**: Main plugin class implementing `Plugin<Project>`
- **DockerExtension**: Provides `docker { }` DSL
- **DockerOrchExtension**: Provides `dockerOrch { }` DSL
- **Task Registration**: Dynamic task creation based on configuration

#### DSL Layer
- **ImageSpec**: Configuration for individual Docker images
- **ComposeStackSpec**: Configuration for Docker Compose stacks
- **Property-based Configuration**: Full Provider API integration
- **Validation**: Early configuration validation with clear error messages

#### Service Layer
- **DockerService**: Abstracts Docker Java Client operations
- **ComposeService**: Abstracts exec library for Compose operations
- **JsonService**: Handles state file generation and parsing
- **Error Handling**: Graceful degradation with informative messages

#### Task Layer
- **DockerBuildTask, DockerSaveTask, DockerTagTask, DockerPublishTask**: Docker operations
- **ComposeUpTask, ComposeDownTask**: Compose orchestration
- **Aggregate Tasks**: Operations across all configured images/stacks
- **Dependency Management**: Intelligent task dependencies based on configuration

#### Integration Layer
- **Test Task Extension**: `usesCompose` method for Test tasks
- **Lifecycle Management**: Suite/class/method level container orchestration
- **State File Integration**: JSON connectivity information for tests

### Technologies and Frameworks Used

#### Core Technologies
- **Java 21+**: Modern language features and performance improvements
- **Gradle 9.0.0+**: Latest build system capabilities
- **Groovy 4.0+**: DSL implementation and testing

#### External Libraries
- **Docker Java Client**: Docker daemon communication for build/tag/push operations
- **exec library**: Cross-platform command execution for Docker Compose operations
- **Jackson**: JSON processing for state files and configuration
- **Spock Framework**: BDD-style testing with Groovy integration

#### Gradle Features
- **Provider API**: Lazy evaluation and configuration avoidance
- **Configuration Cache**: Fast build startup and execution
- **Task Configuration Avoidance**: Efficient task registration
- **Build Cache**: Incremental build support

## Detailed Design

### Module/Component Descriptions

#### Docker Operations Module (UC-6 Implementation)

**Purpose**: Implements Docker image build, tag, save, publish operations  
**Traceability**: fs-11 through fs-22, nfs-9, nfs-23-25

**DSL Structure**:
```groovy
docker {
    images {
        image("alpine") {
            context = file("src/main/docker")
            dockerfile = file("src/main/docker/Dockerfile")
            buildArgs = ["BASE_IMAGE": "eclipse-temurin:21-jre-alpine", "JAR_FILE": "app.jar"]
            tags = ["myapp:${version}-alpine", "myapp:alpine"]
            save {
                compression = "gzip"
                outputFile = layout.buildDirectory.file("docker/pkg/myapp-${version}-alpine.tar.gz")
            }
            publish {
                to {
                    name = "ghcr"
                    repository = "ghcr.io/acme/myapp"
                    tags = ["${version}-alpine", "alpine"]
                    auth {
                        username = providers.gradleProperty("GHCR_USER")
                        password = providers.gradleProperty("GHCR_TOKEN")
                    }
                }
            }
        }
    }
    
    // Optional: extra retags after build
    dockerTag {
        images = [
            "myapp:${version}-alpine": ["ghcr.io/acme/myapp:edge"]
        ]
    }
}
```

**Task Generation Pattern**:
- `docker<Action><ImageName>` (e.g., `dockerBuildAlpine`, `dockerSaveAlpine`)
- Aggregate tasks for all images (`dockerBuild`, `dockerSave`, etc.)
- Dependency management based on build context presence

**Modern Gradle Features**:
```groovy
tasks.register("dockerBuild${imageName.capitalize()}", DockerBuildTask) {
    group = "docker"
    description = "Build Docker image for ${imageName}"
    
    imageSpec.set(extension.images.getByName(imageName))
    outputImageId.set(providers.provider { /* lazy computation */ })
    
    inputs.files(imageSpec.map { it.dockerfile })
    inputs.dir(imageSpec.map { it.context })
    inputs.property("buildArgs", imageSpec.map { it.buildArgs })
    outputs.upToDateWhen { /* custom up-to-date logic */ }
}
```

**Data Structures**:
```groovy
abstract class ImageSpec {
    abstract Property<RegularFile> getContext()
    abstract Property<RegularFile> getDockerfile()
    abstract MapProperty<String, String> getBuildArgs()
    abstract ListProperty<String> getTags()
    abstract Property<String> getSourceRef()
    abstract SaveSpec getSave()
    abstract PublishSpec getPublish()
}

abstract class SaveSpec {
    abstract Property<String> getCompression()
    abstract Property<RegularFile> getOutputFile()
    abstract Property<Boolean> getPullIfMissing()
}

abstract class PublishSpec {
    abstract NamedDomainObjectContainer<PublishTarget> getTo()
}
```

#### Compose Orchestration Module (UC-7 Implementation)

**Purpose**: Implements Docker Compose orchestration for testing  
**Traceability**: fs-23 through fs-32, nfs-26, nfs-31

**DSL Structure**:
```groovy
dockerOrch {
    composeStacks {
        stack("dbOnly") {
            files = [file("compose-db.yml")]
            envFiles = [file(".env")]
            projectName = "db-${project.name}"
            waitForRunning {
                services = ["another"]
                timeoutSeconds = 60
            }
            waitForHealthy {
                services = ["db"]
                timeoutSeconds = 60
            }
            logs {
                writeTo = file("$buildDir/compose/dbOnly-logs")
                tailLines = 200
            }
        }
    }
}
```

**Test Integration**:
```groovy
tasks.register("functionalTest", Test) {
    description = "Functional tests against db-only stack"
    testClassesDirs = sourceSets.functionalTest.output.classesDirs
    classpath = sourceSets.functionalTest.runtimeClasspath
    useJUnitPlatform()
    
    // Plugin-provided method for Compose integration
    usesCompose stack: "dbOnly", lifecycle: "class"
    systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("dbOnly")
    
    shouldRunAfter("test")
}
```

**JSON State File Generation**:
```json
{
  "stackName": "dbOnly",
  "services": {
    "db": {
      "containerId": "abc123def456",
      "containerName": "db-myproject-1",
      "state": "healthy",
      "publishedPorts": [
        {"container": 5432, "host": 54321, "protocol": "tcp"}
      ]
    }
  },
  "composeProject": "db-myproject",
  "timestamp": "2025-08-31T10:30:00Z"
}
```

**Data Structures**:
```groovy
abstract class ComposeStackSpec {
    abstract ListProperty<RegularFile> getFiles()
    abstract ListProperty<RegularFile> getEnvFiles()
    abstract Property<String> getProjectName()
    abstract WaitSpec getWaitForRunning()
    abstract WaitSpec getWaitForHealthy()
    abstract LogsSpec getLogs()
}

abstract class WaitSpec {
    abstract ListProperty<String> getServices()
    abstract Property<Integer> getTimeoutSeconds()
    abstract Property<Integer> getPollSeconds()
}
```

#### Service Abstraction Layer Design

##### Docker Service Interface

**Purpose**: Abstract Docker Java Client operations  
**Traceability**: fs-8, nfs-9, nfs-10

**Interface Design**:
```java
public interface DockerService {
    CompletableFuture<String> buildImage(BuildContext context);
    CompletableFuture<Void> tagImage(String sourceImage, List<String> tags);
    CompletableFuture<Void> saveImage(String imageId, Path outputFile, CompressionType compression);
    CompletableFuture<Void> pushImage(String imageRef, AuthConfig auth);
    CompletableFuture<Boolean> imageExists(String imageRef);
    CompletableFuture<Void> pullImage(String imageRef, AuthConfig auth);
}

public class BuildContext {
    private final Path contextPath;
    private final Path dockerfile;
    private final Map<String, String> buildArgs;
    private final List<String> tags;
    // constructors, getters, validation
}
```

**Error Handling**: Graceful degradation with clear messages (nfs-23-25)
```java
public class DockerServiceException extends RuntimeException {
    private final ErrorType type;
    private final String suggestion;
    
    public enum ErrorType {
        DAEMON_UNAVAILABLE("Docker daemon is not running. Please start Docker and try again."),
        NETWORK_ERROR("Network connectivity issue. Check your internet connection and proxy settings."),
        AUTHENTICATION_FAILED("Authentication failed. Verify your registry credentials."),
        IMAGE_NOT_FOUND("Image not found. Check the image name and tag.");
        
        private final String defaultMessage;
    }
}
```

##### Compose Service Interface

**Purpose**: Abstract exec library for Compose operations  
**Traceability**: fs-32, nfs-31

**Interface Design**:
```java
public interface ComposeService {
    CompletableFuture<ComposeState> upStack(ComposeConfig config);
    CompletableFuture<Void> downStack(String projectName);
    CompletableFuture<ServiceState> waitForServices(WaitConfig config);
    CompletableFuture<String> captureLogs(String projectName, LogsConfig config);
}

public class ComposeConfig {
    private final List<Path> composeFiles;
    private final List<Path> envFiles;
    private final String projectName;
    private final Map<String, String> environment;
}

public class ComposeState {
    private final String stackName;
    private final Map<String, ServiceInfo> services;
    private final String composeProject;
    private final Instant timestamp;
}
```

**Platform Limitations**: Linux/Unix only (nfs-31)
```java
public class ComposeServiceFactory {
    public static ComposeService create() {
        if (!SystemUtils.isUnix()) {
            throw new UnsupportedOperationException(
                "Docker Compose operations are currently supported on Linux/Unix platforms only. " +
                "Windows support is planned for future releases."
            );
        }
        return new ExecLibraryComposeService();
    }
}
```

### Implementation Strategy

#### Gradle 9.0.0+ Modern Features Integration

**Task Registration**: Lazy task creation with `tasks.register()`
```groovy
// Instead of tasks.create() - use lazy registration
tasks.register("dockerBuild", Task) {
    group = "docker"
    description = "Build all configured Docker images"
    
    dependsOn(providers.provider {
        extension.images.names.map { name ->
            "dockerBuild${name.capitalize()}"
        }
    })
}
```

**Provider API**: All configurations use `Property<T>` and `Provider<T>`
```groovy
abstract class DockerExtension {
    abstract NamedDomainObjectContainer<ImageSpec> getImages()
    
    // Helper methods using providers
    Provider<List<String>> getAllImageTags() {
        return images.map { container ->
            container.collectMany { imageSpec ->
                imageSpec.tags.get()
            }
        }
    }
}
```

**Configuration Cache**: Serializable task implementations
```groovy
abstract class DockerBuildTask extends DefaultTask {
    @Input
    abstract Property<String> getImageName()
    
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getContextFiles()
    
    @OutputFile
    abstract Property<RegularFile> getOutputImageIdFile()
    
    @TaskAction
    void buildImage() {
        // Implementation that doesn't capture Project references
        String imageName = imageSpec.get().name
        dockerService.buildImage(createBuildContext()).get()
    }
}
```

#### DSL Implementation Approach

**Extension Classes with NamedDomainObjectContainer**:
```groovy
abstract class DockerExtension {
    private final NamedDomainObjectContainer<ImageSpec> images
    
    @Inject
    DockerExtension(ObjectFactory objectFactory) {
        images = objectFactory.domainObjectContainer(ImageSpec)
    }
    
    void images(Action<NamedDomainObjectContainer<ImageSpec>> action) {
        action.execute(images)
    }
}
```

**Property-based Configuration with Validation**:
```groovy
abstract class ImageSpec {
    @Input
    abstract Property<String> getName()
    
    @InputDirectory
    @Optional
    abstract Property<RegularFile> getContext()
    
    @Internal
    Provider<RegularFile> getContextWithDefault() {
        return context.orElse(
            project.layout.projectDirectory.file("src/main/docker")
        )
    }
}
```

## Testing Architecture

### Testing Strategy Implementation

**Coverage Targets**: 100% line coverage, 100% branch coverage (Updated from nfs-12)  
**Traceability**: nfs-12 through nfs-16, fs-4

**Test Pyramid**:
- **Unit Tests (70%)**: Component isolation with mocks, <30s execution time
- **Functional Tests (20%)**: Plugin integration testing, <2min execution time  
- **Integration Tests (8%)**: Real Docker daemon interaction
- **End-to-End Tests (2%)**: Complete workflow validation

**Unit Test Structure**:
```groovy
class DockerServiceSpec extends Specification {
    DockerClient dockerClient = Mock()
    DockerService service = new DockerServiceImpl(dockerClient)
    
    def "should build image with correct context"() {
        given:
        BuildContext context = new BuildContext(
            contextPath: Paths.get("/src/main/docker"),
            dockerfile: Paths.get("/src/main/docker/Dockerfile"),
            buildArgs: ["ARG1": "value1"],
            tags: ["myapp:latest"]
        )
        
        when:
        String imageId = service.buildImage(context).get()
        
        then:
        1 * dockerClient.buildImageCmd(_) >> { args ->
            assert args[0].dockerfile == context.dockerfile
            return mockBuildImageCmd("sha256:abc123")
        }
        imageId == "sha256:abc123"
    }
}
```

**Functional Test Structure**:
```groovy
class GradleDockerPluginFunctionalSpec extends Specification {
    @TempDir
    Path testProjectDir
    
    def "should generate per-image tasks"() {
        given:
        buildFile << """
            plugins { id 'com.kineticfire.gradle.gradle-docker' }
            docker {
                images {
                    image('alpine') {
                        context = file('src/main/docker')
                        tags = ['myapp:alpine']
                    }
                }
            }
        """
        
        when:
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks', '--group=docker')
            .build()
            
        then:
        result.output.contains('dockerBuildAlpine')
        result.output.contains('dockerSaveAlpine')
        result.output.contains('dockerTagAlpine')
        result.output.contains('dockerPublishAlpine')
    }
}
```

### Acceptance Testing Framework

**Performance Validation**:
- Clean build completes in <60 seconds (nfs-17)
- Incremental build completes in <10 seconds (nfs-18)
- Unit tests execute in <30 seconds (nfs-19)
- Functional tests execute in <2 minutes (nfs-20)

**Cross-Platform Compatibility**:
- Linux: Full functionality including Compose operations
- macOS: Full functionality including Compose operations  
- Windows WSL2: Docker operations only (Compose operations require Unix platform)

**Real Docker Integration**:
- Validate against live Docker daemon
- Test with multiple Docker CE versions
- Verify registry authentication flows
- Test failure scenarios (daemon down, network issues)

## Traceability Matrix

### Functional Specification Traceability

| Component | Implements | Specification IDs | Description |
|-----------|------------|------------------|-------------|
| DockerExtension | Docker DSL | fs-11, fs-12, fs-13, fs-14 | Core Docker tasks |
| ImageSpec | Multi-image support | fs-15, fs-16, fs-17, fs-18, fs-19, fs-20, fs-21, fs-22 | Image configuration |
| DockerOrchExtension | Compose DSL | fs-23, fs-24, fs-25 | Compose orchestration |
| ComposeStackSpec | Service management | fs-26, fs-27, fs-28 | State and health checking |
| Test Integration | usesCompose | fs-29, fs-30, fs-31 | Test lifecycle |
| Service Abstractions | Library usage | fs-8, fs-9, fs-10, fs-32 | External library integration |

### Non-Functional Specification Traceability  

| Architecture Component | Addresses | Specification IDs | Description |
|----------------------|-----------|------------------|-------------|
| Service Abstractions | Library isolation | nfs-10, nfs-11 | Dependency management |
| Error Handling | Graceful failures | nfs-23, nfs-24, nfs-25, nfs-26 | User experience |
| Performance Design | Build/test targets | nfs-17, nfs-18, nfs-19, nfs-20 | Execution efficiency |
| Testing Strategy | Coverage targets | nfs-12, nfs-13, nfs-14, nfs-15, nfs-16 | Quality assurance |
| Version Enforcement | Platform requirements | nfs-1, nfs-2, nfs-3 | Compatibility |
| Documentation | User guidance | nfs-27, nfs-28, nfs-29, nfs-30 | Adoption support |

## Implementation Phases & Milestones

### Phase 1: Core Infrastructure
- Plugin architecture and DSL foundations
- Basic Docker operations (build, tag, save, publish)
- Version enforcement (Java 21+, Gradle 9.0.0+, Groovy 4.0+)
- Unit test framework establishment

### Phase 2: Advanced Features  
- Docker Compose orchestration
- Test integration with `usesCompose`
- JSON state file generation
- Service health monitoring

### Phase 3: Polish & Documentation
- Performance optimization to meet targets
- Cross-platform testing and validation
- Comprehensive documentation and examples
- Final integration testing

## Risk Mitigation & Constraints

### Technical Risks
- **Docker Daemon Availability**: Graceful error handling when Docker is not running
- **Platform Limitations**: exec library supports Linux/Unix only for Compose operations
- **Network Connectivity**: Robust retry mechanisms for registry operations
- **Version Compatibility**: Testing matrix across supported versions

### Implementation Constraints
- **Java 21+ Requirement**: Modern JVM features required for optimal performance
- **Gradle 9.0.0+ Requirement**: Provider API and Configuration Cache dependencies
- **Linux/Unix Limitation**: Docker Compose operations not available on Windows
- **Docker Java Client Compatibility**: Version alignment with Docker daemon versions

## Appendices

### Appendix A: Complete DSL Examples

See Use Case documentation:
- [UC-6: Docker Image Operations](../requirements/use-cases/uc-6-proj-dev-create-and-publish-image.md)
- [UC-7: Docker Compose Orchestration](../requirements/use-cases/uc-7-proj-dev-compose-orchestration.md)

### Appendix B: API Reference

**Core Extensions**:
- `DockerExtension`: Provides `docker { }` DSL
- `DockerOrchExtension`: Provides `dockerOrch { }` DSL

**Task Types**:
- `DockerBuildTask`, `DockerSaveTask`, `DockerTagTask`, `DockerPublishTask`
- `ComposeUpTask`, `ComposeDownTask`

**Service Interfaces**:
- `DockerService`: Docker daemon operations
- `ComposeService`: Docker Compose operations
- `JsonService`: State file management

