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
                compression = "gzip"  // Options: "none", "gzip", "bzip2", "xz", "zip"
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

**Complete Interface Definition**:
```groovy
interface DockerService {
    /**
     * Build a Docker image from the provided build context
     * @param context Build configuration including dockerfile, context path, build args, and tags
     * @return CompletableFuture with the built image ID
     * @throws DockerServiceException if build fails
     */
    CompletableFuture<String> buildImage(BuildContext context)
    
    /**
     * Tag an existing image with additional tags
     * @param sourceImage Source image ID or reference
     * @param tags List of new tags to apply
     * @return CompletableFuture that completes when tagging is done
     * @throws DockerServiceException if tagging fails
     */
    CompletableFuture<Void> tagImage(String sourceImage, List<String> tags)
    
    /**
     * Save an image to a tar file with optional compression
     * @param imageId Image ID to save
     * @param outputFile Output file path
     * @param compression Compression type (NONE, GZIP)
     * @return CompletableFuture that completes when save is done
     * @throws DockerServiceException if save fails
     */
    CompletableFuture<Void> saveImage(String imageId, Path outputFile, CompressionType compression)
    
    /**
     * Push an image to a registry
     * @param imageRef Full image reference (repository:tag)
     * @param auth Authentication configuration (can be null)
     * @return CompletableFuture that completes when push is done
     * @throws DockerServiceException if push fails
     */
    CompletableFuture<Void> pushImage(String imageRef, AuthConfig auth)
    
    /**
     * Check if an image exists locally
     * @param imageRef Image reference to check
     * @return CompletableFuture with boolean indicating existence
     */
    CompletableFuture<Boolean> imageExists(String imageRef)
    
    /**
     * Pull an image from a registry
     * @param imageRef Image reference to pull
     * @param auth Authentication configuration (can be null)
     * @return CompletableFuture that completes when pull is done
     * @throws DockerServiceException if pull fails
     */
    CompletableFuture<Void> pullImage(String imageRef, AuthConfig auth)
    
    /**
     * Clean up resources and close connections
     */
    void close()
}

interface ComposeService {
    /**
     * Start a Docker Compose stack
     * @param config Compose configuration with files, project name, etc.
     * @return CompletableFuture with the current state of services
     * @throws ComposeServiceException if startup fails
     */
    CompletableFuture<ComposeState> upStack(ComposeConfig config)
    
    /**
     * Stop a Docker Compose stack
     * @param projectName Compose project name
     * @return CompletableFuture that completes when stack is stopped
     * @throws ComposeServiceException if shutdown fails
     */
    CompletableFuture<Void> downStack(String projectName)
    
    /**
     * Wait for services to reach a desired state
     * @param config Wait configuration including services, timeout, target state
     * @return CompletableFuture with final service state
     * @throws ComposeServiceException if timeout or other error occurs
     */
    CompletableFuture<ServiceState> waitForServices(WaitConfig config)
    
    /**
     * Capture logs from compose services
     * @param projectName Compose project name
     * @param config Logs configuration
     * @return CompletableFuture with captured logs
     * @throws ComposeServiceException if log capture fails
     */
    CompletableFuture<String> captureLogs(String projectName, LogsConfig config)
}

interface JsonService {
    /**
     * Convert an object to JSON string
     * @param object Object to serialize
     * @return JSON string representation
     * @throws JsonProcessingException if serialization fails
     */
    String toJson(Object object)
    
    /**
     * Parse JSON string to specified type
     * @param json JSON string
     * @param type Target type class
     * @return Parsed object
     * @throws JsonProcessingException if parsing fails
     */
    <T> T fromJson(String json, Class<T> type)
    
    /**
     * Parse JSON array string to list
     * @param json JSON array string
     * @return List of maps representing JSON objects
     * @throws JsonProcessingException if parsing fails
     */
    List<Map<String, Object>> parseJsonArray(String json)
}

// === Complete Data Model Definitions ===

class BuildContext {
    final Path contextPath
    final Path dockerfile
    final Map<String, String> buildArgs
    final List<String> tags
    
    BuildContext(Path contextPath, Path dockerfile, Map<String, String> buildArgs, List<String> tags) {
        this.contextPath = Objects.requireNonNull(contextPath, "Context path cannot be null")
        this.dockerfile = Objects.requireNonNull(dockerfile, "Dockerfile path cannot be null") 
        this.buildArgs = buildArgs ?: [:]
        this.tags = tags ?: []
        validate()
    }
    
    private void validate() {
        if (!Files.exists(contextPath)) {
            throw new IllegalArgumentException("Context path does not exist: ${contextPath}")
        }
        if (!Files.exists(dockerfile)) {
            throw new IllegalArgumentException("Dockerfile does not exist: ${dockerfile}")
        }
        if (tags.empty) {
            throw new IllegalArgumentException("At least one tag must be specified")
        }
    }
}

class ComposeConfig {
    final List<Path> composeFiles
    final List<Path> envFiles
    final String projectName
    final String stackName
    final Map<String, String> environment
    
    ComposeConfig(List<Path> composeFiles, String projectName, String stackName) {
        this.composeFiles = Objects.requireNonNull(composeFiles, "Compose files cannot be null")
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null")
        this.stackName = Objects.requireNonNull(stackName, "Stack name cannot be null")
        this.envFiles = []
        this.environment = [:]
        validate()
    }
    
    ComposeConfig(List<Path> composeFiles, List<Path> envFiles, String projectName, String stackName, Map<String, String> environment) {
        this.composeFiles = Objects.requireNonNull(composeFiles, "Compose files cannot be null")
        this.envFiles = envFiles ?: []
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null") 
        this.stackName = Objects.requireNonNull(stackName, "Stack name cannot be null")
        this.environment = environment ?: [:]
        validate()
    }
    
    private void validate() {
        if (composeFiles.empty) {
            throw new IllegalArgumentException("At least one compose file must be specified")
        }
        composeFiles.each { file ->
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("Compose file does not exist: ${file}")
            }
        }
    }
}

class ComposeState {
    final String stackName
    final Map<String, ServiceInfo> services
    final String composeProject
    final Instant timestamp
    
    ComposeState(String stackName, Map<String, ServiceInfo> services, String composeProject, Instant timestamp) {
        this.stackName = Objects.requireNonNull(stackName, "Stack name cannot be null")
        this.services = services ?: [:]
        this.composeProject = Objects.requireNonNull(composeProject, "Compose project cannot be null")
        this.timestamp = timestamp ?: Instant.now()
    }
}

class ServiceInfo {
    final String containerId
    final String containerName
    final String state
    final List<PortMapping> publishedPorts
    
    ServiceInfo(String containerId, String containerName, String state, List<PortMapping> publishedPorts) {
        this.containerId = Objects.requireNonNull(containerId, "Container ID cannot be null")
        this.containerName = Objects.requireNonNull(containerName, "Container name cannot be null")
        this.state = Objects.requireNonNull(state, "State cannot be null")
        this.publishedPorts = publishedPorts ?: []
    }
}

class PortMapping {
    final int container
    final int host
    final String protocol
    
    PortMapping(int container, int host, String protocol = "tcp") {
        this.container = container
        this.host = host
        this.protocol = protocol
    }
}

class WaitConfig {
    final String projectName
    final List<String> services
    final int timeoutSeconds
    final int pollSeconds
    final ServiceState targetState
    
    WaitConfig(String projectName, List<String> services, int timeoutSeconds, ServiceState targetState) {
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null")
        this.services = Objects.requireNonNull(services, "Services list cannot be null")
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60
        this.pollSeconds = 2
        this.targetState = Objects.requireNonNull(targetState, "Target state cannot be null")
    }
    
    WaitConfig(String projectName, List<String> services, int timeoutSeconds, int pollSeconds, ServiceState targetState) {
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null")
        this.services = Objects.requireNonNull(services, "Services list cannot be null")
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60
        this.pollSeconds = pollSeconds > 0 ? pollSeconds : 2
        this.targetState = Objects.requireNonNull(targetState, "Target state cannot be null")
    }
}

class LogsConfig {
    final List<String> services
    final int tailLines
    final boolean follow
    final Path outputFile
    
    LogsConfig(List<String> services, int tailLines = 100, boolean follow = false, Path outputFile = null) {
        this.services = services ?: []
        this.tailLines = tailLines
        this.follow = follow
        this.outputFile = outputFile
    }
}

enum ServiceState {
    RUNNING, HEALTHY, STOPPED, ERROR, UNKNOWN
}

enum CompressionType {
    NONE, GZIP, BZIP2, XZ, ZIP
}

// Authentication and Registry Support Classes
class AuthConfig {
    final String username
    final String password
    final String registryToken
    final String serverAddress
    
    AuthConfig(String username = null, String password = null, String registryToken = null, String serverAddress = null) {
        this.username = username
        this.password = password
        this.registryToken = registryToken
        this.serverAddress = serverAddress
    }
    
    boolean hasCredentials() {
        return (username && password) || registryToken
    }
}

class ImageRefParts {
    final String repository
    final String tag
    final String registry
    
    ImageRefParts(String repository, String tag, String registry = null) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null")
        this.tag = tag ?: "latest"
        this.registry = registry
    }
    
    String getFullReference() {
        def ref = registry ? "${registry}/${repository}" : repository
        return "${ref}:${tag}"
    }
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

### Phase 1: Core Infrastructure (Address High Priority Gaps)
- **Plugin Infrastructure & Registration**: Complete plugin descriptor, extension wiring, build configuration
- **Task Implementation Mechanics**: Detailed task actions, input/output annotations, dependency resolution
- **Service Layer Implementation**: Docker Java Client setup, exec library integration, service lifecycle
- **DSL Implementation Mechanics**: Container configuration, property validation, extension initialization
- Unit test framework establishment

### Phase 2: Quality & Integration Features
- **Test Integration**: Complete `usesCompose` implementation and lifecycle management
- **Error Handling**: Comprehensive exception hierarchy and user-friendly error reporting
- **Resource Management**: Connection pooling, cleanup strategies, shutdown hooks
- Docker Compose orchestration and JSON state file generation

### Phase 3: Performance & Platform Support
- **Configuration Management**: Advanced property handling and validation
- **Performance Implementation**: Caching strategies and optimization techniques
- **Platform Abstraction**: Cross-platform support and feature detection
- Final integration testing and documentation

## Implementation Understanding Gaps & Priorities

### High Priority Items (Critical - Address Immediately)

#### 1. Plugin Infrastructure & Registration Details
**Status**: ✅ **ADDRESSED**
**Implementation Impact**: Critical for plugin creation

**Complete Build Configuration** (`plugin/build.gradle`):
```groovy
plugins {
    id 'groovy'
    id 'java-gradle-plugin'
    id 'maven-publish'
    id 'com.gradle.plugin-publish' version '1.2.1'
}

group = 'com.kineticfire.gradle'
version = '1.0.0'

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Docker Java Client
    implementation 'com.github.docker-java:docker-java-core:3.3.4'
    implementation 'com.github.docker-java:docker-java-transport-httpclient5:3.3.4'
    
    // exec library for Docker Compose
    implementation 'com.kineticfire.labs:exec:1.0.0'
    
    // JSON processing
    implementation 'com.fasterxml.jackson.core:jackson-core:2.16.0'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.16.0'
    
    // Gradle API
    compileOnly gradleApi()
    compileOnly localGroovy()
    
    // Testing
    testImplementation platform('org.spockframework:spock-bom:2.4-M4-groovy-4.0')
    testImplementation 'org.spockframework:spock-core'
    testImplementation gradleTestKit()
    testImplementation 'junit:junit:4.13.2'
    
    // Functional testing
    functionalTestImplementation platform('org.spockframework:spock-bom:2.4-M4-groovy-4.0')
    functionalTestImplementation 'org.spockframework:spock-core'
    functionalTestImplementation gradleTestKit()
}

testing {
    suites {
        functionalTest(JvmTestSuite) {
            testType = TestSuiteType.FUNCTIONAL_TEST
        }
    }
}

tasks.named('check') {
    dependsOn testing.suites.functionalTest
}

gradlePlugin {
    website = 'https://github.com/kineticfire-labs/gradle-docker'
    vcsUrl = 'https://github.com/kineticfire-labs/gradle-docker'
    
    plugins {
        gradleDockerPlugin {
            id = 'com.kineticfire.gradle.gradle-docker'
            implementationClass = 'com.kineticfire.gradle.docker.GradleDockerPlugin'
            displayName = 'Gradle Docker Plugin'
            description = 'Provides Docker image build/publish and Compose orchestration for Gradle projects'
            tags = ['docker', 'containers', 'docker-compose', 'testing']
        }
    }
}
```

**Plugin Descriptor** (`src/main/resources/META-INF/gradle-plugins/com.kineticfire.gradle.gradle-docker.properties`):
```properties
implementation-class=com.kineticfire.gradle.docker.GradleDockerPlugin
```

**Project Structure**:
```
plugin/
├── src/
│   ├── main/
│   │   ├── groovy/com/kineticfire/gradle/docker/
│   │   │   ├── GradleDockerPlugin.groovy           # Main plugin
│   │   │   ├── extensions/
│   │   │   │   ├── DockerExtension.groovy          # docker { } DSL
│   │   │   │   └── DockerOrchExtension.groovy      # dockerOrch { } DSL
│   │   │   ├── tasks/
│   │   │   │   ├── DockerBuildTask.groovy
│   │   │   │   ├── DockerSaveTask.groovy
│   │   │   │   ├── DockerTagTask.groovy
│   │   │   │   ├── DockerPublishTask.groovy
│   │   │   │   ├── ComposeUpTask.groovy
│   │   │   │   └── ComposeDownTask.groovy
│   │   │   ├── services/
│   │   │   │   ├── DockerService.groovy            # Interface
│   │   │   │   ├── DockerServiceImpl.groovy
│   │   │   │   ├── ComposeService.groovy           # Interface
│   │   │   │   ├── ComposeServiceImpl.groovy
│   │   │   │   └── JsonService.groovy
│   │   │   └── specs/
│   │   │       ├── ImageSpec.groovy
│   │   │       ├── ComposeStackSpec.groovy
│   │   │       └── [other spec classes]
│   │   └── resources/META-INF/gradle-plugins/
│   │       └── com.kineticfire.gradle.gradle-docker.properties
│   ├── test/groovy/                               # Unit tests
│   └── functionalTest/groovy/                     # Functional tests
└── build.gradle
```

#### 2. Task Implementation Mechanics
**Status**: ✅ **ADDRESSED**
**Implementation Impact**: Critical for task execution

**Complete Task Action Templates**:

**DockerBuildTask Implementation**:
```groovy
@CacheableTask
abstract class DockerBuildTask extends DefaultTask {
    
    @Input
    abstract Property<String> getImageName()
    
    @Input
    abstract MapProperty<String, String> getBuildArgs()
    
    @Input
    abstract ListProperty<String> getTags()
    
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getContextDirectory()
    
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getDockerfile()
    
    @OutputFile
    abstract RegularFileProperty getImageIdFile()
    
    @Inject
    abstract DockerService getDockerService()
    
    @TaskAction
    void buildImage() {
        try {
            logger.lifecycle("Building Docker image: {}", imageName.get())
            
            // Create build context
            def buildContext = new BuildContext(
                contextPath: contextDirectory.get().asFile.toPath(),
                dockerfile: dockerfile.get().asFile.toPath(),
                buildArgs: buildArgs.get(),
                tags: tags.get()
            )
            
            // Execute build
            def imageId = dockerService.buildImage(buildContext).get()
            
            // Write output
            imageIdFile.get().asFile.text = imageId
            
            logger.lifecycle("Successfully built image: {} (ID: {})", imageName.get(), imageId)
            
        } catch (Exception e) {
            throw new GradleException("Failed to build Docker image '${imageName.get()}': ${e.message}", e)
        }
    }
    
    @Internal
    Provider<Boolean> getUpToDateCheck() {
        return providers.provider {
            def imageIdFile = imageIdFile.get().asFile
            if (!imageIdFile.exists()) return false
            
            def imageId = imageIdFile.text.trim()
            return dockerService.imageExists(imageId).get()
        }
    }
}
```

**DockerPublishTask Implementation**:
```groovy
abstract class DockerPublishTask extends DefaultTask {
    
    @Input
    abstract Property<String> getImageName()
    
    @Input
    abstract ListProperty<PublishTarget> getPublishTargets()
    
    @InputFile
    @Optional
    abstract RegularFileProperty getImageIdFile()
    
    @Inject
    abstract DockerService getDockerService()
    
    @TaskAction
    void publishImage() {
        def targets = publishTargets.get()
        if (targets.empty) {
            logger.info("No publish targets configured for image: {}", imageName.get())
            return
        }
        
        try {
            def imageId = getImageId()
            logger.lifecycle("Publishing Docker image: {} to {} registries", imageName.get(), targets.size())
            
            targets.each { target ->
                target.tags.each { tag ->
                    def fullImageRef = "${target.repository}:${tag}"
                    logger.lifecycle("Publishing to: {}", fullImageRef)
                    
                    // Tag image for target registry
                    dockerService.tagImage(imageId, [fullImageRef]).get()
                    
                    // Push to registry
                    def authConfig = createAuthConfig(target.auth)
                    dockerService.pushImage(fullImageRef, authConfig).get()
                    
                    logger.lifecycle("Successfully published: {}", fullImageRef)
                }
            }
            
        } catch (Exception e) {
            throw new GradleException("Failed to publish Docker image '${imageName.get()}': ${e.message}", e)
        }
    }
    
    private String getImageId() {
        if (imageIdFile.present && imageIdFile.get().asFile.exists()) {
            return imageIdFile.get().asFile.text.trim()
        } else {
            // Fallback to first tag if no image ID file
            return tags.get().first()
        }
    }
    
    private AuthConfig createAuthConfig(AuthSpec authSpec) {
        if (!authSpec) return null
        
        return new AuthConfig()
            .withUsername(authSpec.username.orNull)
            .withPassword(authSpec.password.orNull)
    }
}
```

**ComposeUpTask Implementation**:
```groovy
abstract class ComposeUpTask extends DefaultTask {
    
    @Input
    abstract Property<String> getStackName()
    
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getComposeFiles()
    
    @Input
    abstract Property<String> getProjectName()
    
    @Input
    @Optional
    abstract Property<WaitSpec> getWaitForHealthy()
    
    @OutputFile
    abstract RegularFileProperty getStateFile()
    
    @Inject
    abstract ComposeService getComposeService()
    
    @TaskAction
    void composeUp() {
        try {
            logger.lifecycle("Starting Docker Compose stack: {}", stackName.get())
            
            def composeConfig = new ComposeConfig(
                composeFiles: composeFiles.files.collect { it.toPath() },
                projectName: projectName.get(),
                environment: System.getenv()
            )
            
            // Execute compose up
            def composeState = composeService.upStack(composeConfig).get()
            
            // Wait for services if configured
            if (waitForHealthy.present) {
                logger.lifecycle("Waiting for services to become healthy...")
                def waitConfig = new WaitConfig(
                    projectName: projectName.get(),
                    services: waitForHealthy.get().services.get(),
                    timeoutSeconds: waitForHealthy.get().timeoutSeconds.get(),
                    targetState: ServiceState.HEALTHY
                )
                composeService.waitForServices(waitConfig).get()
            }
            
            // Write state file
            writeStateFile(composeState)
            
            logger.lifecycle("Successfully started stack: {}", stackName.get())
            
        } catch (Exception e) {
            throw new GradleException("Failed to start Compose stack '${stackName.get()}': ${e.message}", e)
        }
    }
    
    private void writeStateFile(ComposeState state) {
        def stateJson = JsonService.toJson(state)
        stateFile.get().asFile.text = stateJson
        logger.info("State file written to: {}", stateFile.get().asFile.absolutePath)
    }
}
```

**Task Dependency Resolution Algorithm**:
```groovy
// In GradleDockerPlugin.groovy
void configureDependencies(Project project, DockerExtension dockerExt) {
    project.afterEvaluate {
        dockerExt.images.all { imageSpec ->
            def imageName = imageSpec.name
            def buildTaskName = "dockerBuild${imageName.capitalize()}"
            def saveTaskName = "dockerSave${imageName.capitalize()}"
            def publishTaskName = "dockerPublish${imageName.capitalize()}"
            
            // Dependency rules based on build context presence
            if (imageSpec.context.present) {
                // Has build context - save/publish depend on build
                project.tasks.named(saveTaskName) {
                    dependsOn buildTaskName
                }
                project.tasks.named(publishTaskName) {
                    dependsOn buildTaskName
                }
            } else if (imageSpec.sourceRef.present) {
                // No build context - tasks are independent
                // sourceRef operations don't depend on build
                logger.info("Image '{}' uses sourceRef - tasks are independent", imageName)
            }
        }
        
        // Configure aggregate task dependencies
        project.tasks.named('dockerBuild') {
            dependsOn dockerExt.images.names.collect { "dockerBuild${it.capitalize()}" }
        }
        project.tasks.named('dockerSave') {
            dependsOn dockerExt.images.names.collect { "dockerSave${it.capitalize()}" }
        }
        project.tasks.named('dockerPublish') {
            dependsOn dockerExt.images.names.collect { "dockerPublish${it.capitalize()}" }
        }
    }
}
```

**Error Propagation Pattern**:
```groovy
// Common error handling pattern for all tasks
abstract class BaseDockerTask extends DefaultTask {
    
    protected void handleDockerException(Exception e, String operation) {
        if (e instanceof DockerServiceException) {
            def dockerEx = (DockerServiceException) e
            throw new GradleException(
                "${operation} failed: ${dockerEx.message}\n" +
                "Suggestion: ${dockerEx.suggestion}", 
                dockerEx
            )
        } else {
            throw new GradleException("${operation} failed: ${e.message}", e)
        }
    }
}
```

#### 3. Main Plugin Class Implementation Skeleton
**Status**: ✅ **ADDRESSED**  
**Implementation Impact**: Critical for plugin structure and wiring

**Complete GradleDockerPlugin Implementation**:
```groovy
class GradleDockerPlugin implements Plugin<Project> {
    
    @Override
    void apply(Project project) {
        // Validate minimum requirements
        validateRequirements(project)
        
        // Register shared services
        def dockerService = registerDockerService(project)
        def composeService = registerComposeService(project) 
        def jsonService = registerJsonService(project)
        
        // Create extensions
        def dockerExt = project.extensions.create('docker', DockerExtension, project.objects, project)
        def dockerOrchExt = project.extensions.create('dockerOrch', DockerOrchExtension, project.objects, project)
        
        // Register task creation rules
        registerTaskCreationRules(project, dockerExt, dockerOrchExt, dockerService, composeService, jsonService)
        
        // Configure validation and dependency resolution
        configureAfterEvaluation(project, dockerExt, dockerOrchExt)
        
        // Setup cleanup hooks
        configureCleanupHooks(project, dockerService, composeService)
    }
    
    private void validateRequirements(Project project) {
        // Validate Java version
        def javaVersion = JavaVersion.current()
        if (javaVersion < JavaVersion.VERSION_21) {
            throw new GradleException(
                "gradle-docker plugin requires Java 21 or higher. Current version: ${javaVersion}\n" +
                "Suggestion: Update your Java installation to version 21 or higher"
            )
        }
        
        // Validate Gradle version
        def gradleVersion = project.gradle.gradleVersion
        if (GradleVersion.version(gradleVersion) < GradleVersion.version("9.0.0")) {
            throw new GradleException(
                "gradle-docker plugin requires Gradle 9.0.0 or higher. Current version: ${gradleVersion}\n" +
                "Suggestion: Update your Gradle wrapper to version 9.0.0 or higher"
            )
        }
        
        project.logger.info("gradle-docker plugin applied successfully (Java ${javaVersion}, Gradle ${gradleVersion})")
    }
    
    private Provider<DockerService> registerDockerService(Project project) {
        return project.gradle.sharedServices.registerIfAbsent('dockerService', DockerServiceImpl) {
            // Service configuration if needed
        }
    }
    
    private Provider<ComposeService> registerComposeService(Project project) {
        return project.gradle.sharedServices.registerIfAbsent('composeService', ExecLibraryComposeService) { spec ->
            spec.parameters { params ->
                // Service parameters if needed
            }
        }
    }
    
    private Provider<JsonService> registerJsonService(Project project) {
        return project.gradle.sharedServices.registerIfAbsent('jsonService', JsonServiceImpl) {
            // Service configuration if needed
        }
    }
    
    private void registerTaskCreationRules(Project project, DockerExtension dockerExt, DockerOrchExtension dockerOrchExt,
                                         Provider<DockerService> dockerService, Provider<ComposeService> composeService, 
                                         Provider<JsonService> jsonService) {
        
        // Register aggregate tasks first
        registerAggregateTasks(project)
        
        // Register per-image tasks after evaluation
        project.afterEvaluate {
            registerDockerImageTasks(project, dockerExt, dockerService)
            registerComposeStackTasks(project, dockerOrchExt, composeService, jsonService)
        }
    }
    
    private void registerAggregateTasks(Project project) {
        project.tasks.register('dockerBuild') {
            group = 'docker'
            description = 'Build all configured Docker images'
            // Dependencies will be configured after evaluation
        }
        
        project.tasks.register('dockerSave') {
            group = 'docker'
            description = 'Save all configured Docker images to files'
        }
        
        project.tasks.register('dockerTag') {
            group = 'docker'
            description = 'Tag all configured Docker images'
        }
        
        project.tasks.register('dockerPublish') {
            group = 'docker'
            description = 'Publish all configured Docker images to registries'
        }
        
        project.tasks.register('composeUp') {
            group = 'docker compose'
            description = 'Start all configured Docker Compose stacks'
        }
        
        project.tasks.register('composeDown') {
            group = 'docker compose'
            description = 'Stop all configured Docker Compose stacks'
        }
    }
    
    private void registerDockerImageTasks(Project project, DockerExtension dockerExt, Provider<DockerService> dockerService) {
        dockerExt.images.all { imageSpec ->
            def imageName = imageSpec.name
            def capitalizedName = imageName.capitalize()
            
            // Build task
            project.tasks.register("dockerBuild${capitalizedName}", DockerBuildTask) { task ->
                configureDockerBuildTask(task, imageSpec, dockerService)
            }
            
            // Save task
            if (imageSpec.save.present) {
                project.tasks.register("dockerSave${capitalizedName}", DockerSaveTask) { task ->
                    configureDockerSaveTask(task, imageSpec, dockerService)
                }
            }
            
            // Tag task
            project.tasks.register("dockerTag${capitalizedName}", DockerTagTask) { task ->
                configureDockerTagTask(task, imageSpec, dockerService)
            }
            
            // Publish task
            if (imageSpec.publish.present) {
                project.tasks.register("dockerPublish${capitalizedName}", DockerPublishTask) { task ->
                    configureDockerPublishTask(task, imageSpec, dockerService)
                }
            }
        }
    }
    
    private void registerComposeStackTasks(Project project, DockerOrchExtension dockerOrchExt, 
                                         Provider<ComposeService> composeService, Provider<JsonService> jsonService) {
        dockerOrchExt.composeStacks.all { stackSpec ->
            def stackName = stackSpec.name
            def capitalizedName = stackName.capitalize()
            
            // Up task
            project.tasks.register("composeUp${capitalizedName}", ComposeUpTask) { task ->
                configureComposeUpTask(task, stackSpec, composeService, jsonService)
            }
            
            // Down task  
            project.tasks.register("composeDown${capitalizedName}", ComposeDownTask) { task ->
                configureComposeDownTask(task, stackSpec, composeService)
            }
        }
    }
    
    private void configureAfterEvaluation(Project project, DockerExtension dockerExt, DockerOrchExtension dockerOrchExt) {
        project.afterEvaluate {
            try {
                // Validate configurations
                dockerExt.validate()
                dockerOrchExt.validate()
                
                // Configure task dependencies
                configureTaskDependencies(project, dockerExt, dockerOrchExt)
                
                project.logger.info("gradle-docker plugin configuration completed successfully")
                
            } catch (Exception e) {
                throw new GradleException("gradle-docker plugin configuration failed: ${e.message}", e)
            }
        }
    }
    
    private void configureTaskDependencies(Project project, DockerExtension dockerExt, DockerOrchExtension dockerOrchExt) {
        // Configure per-image task dependencies
        dockerExt.images.all { imageSpec ->
            def imageName = imageSpec.name
            def capitalizedName = imageName.capitalize()
            def buildTaskName = "dockerBuild${capitalizedName}"
            
            // If image has build context, save/publish depend on build
            if (imageSpec.context.present) {
                project.tasks.findByName("dockerSave${capitalizedName}")?.dependsOn(buildTaskName)
                project.tasks.findByName("dockerPublish${capitalizedName}")?.dependsOn(buildTaskName)
            }
        }
        
        // Configure aggregate task dependencies
        project.tasks.named('dockerBuild') {
            dependsOn dockerExt.images.names.collect { "dockerBuild${it.capitalize()}" }
        }
        
        project.tasks.named('dockerSave') {
            def saveTaskNames = dockerExt.images.findAll { it.save.present }.collect { "dockerSave${it.name.capitalize()}" }
            if (saveTaskNames) {
                dependsOn saveTaskNames
            }
        }
        
        project.tasks.named('dockerPublish') {
            def publishTaskNames = dockerExt.images.findAll { it.publish.present }.collect { "dockerPublish${it.name.capitalize()}" }
            if (publishTaskNames) {
                dependsOn publishTaskNames
            }
        }
        
        // Configure compose aggregate dependencies
        project.tasks.named('composeUp') {
            dependsOn dockerOrchExt.composeStacks.names.collect { "composeUp${it.capitalize()}" }
        }
        
        project.tasks.named('composeDown') {
            dependsOn dockerOrchExt.composeStacks.names.collect { "composeDown${it.capitalize()}" }
        }
    }
    
    private void configureCleanupHooks(Project project, Provider<DockerService> dockerService, Provider<ComposeService> composeService) {
        project.gradle.buildFinished { result ->
            try {
                dockerService.get().close()
                project.logger.debug("Docker service closed successfully")
            } catch (Exception e) {
                project.logger.warn("Error closing Docker service: ${e.message}")
            }
        }
    }
    
    // Task configuration methods
    private void configureDockerBuildTask(DockerBuildTask task, ImageSpec imageSpec, Provider<DockerService> dockerService) {
        task.group = 'docker'
        task.description = "Build Docker image: ${imageSpec.name}"
        task.imageName.set(imageSpec.name)
        task.buildArgs.set(imageSpec.buildArgs)
        task.tags.set(imageSpec.tags)
        task.contextDirectory.set(imageSpec.context)
        task.dockerfile.set(imageSpec.dockerfile)
        task.imageIdFile.set(task.project.layout.buildDirectory.file("docker/image-ids/${imageSpec.name}.txt"))
        task.usesService(dockerService)
    }
    
    private void configureDockerSaveTask(DockerSaveTask task, ImageSpec imageSpec, Provider<DockerService> dockerService) {
        task.group = 'docker'
        task.description = "Save Docker image to file: ${imageSpec.name}"
        task.imageName.set(imageSpec.name)
        task.outputFile.set(imageSpec.save.outputFile)
        task.compression.set(imageSpec.save.compression)
        task.usesService(dockerService)
    }
    
    private void configureDockerTagTask(DockerTagTask task, ImageSpec imageSpec, Provider<DockerService> dockerService) {
        task.group = 'docker'
        task.description = "Tag Docker image: ${imageSpec.name}"
        task.imageName.set(imageSpec.name)
        task.tags.set(imageSpec.tags)
        task.usesService(dockerService)
    }
    
    private void configureDockerPublishTask(DockerPublishTask task, ImageSpec imageSpec, Provider<DockerService> dockerService) {
        task.group = 'docker'
        task.description = "Publish Docker image: ${imageSpec.name}"
        task.imageName.set(imageSpec.name)
        task.publishTargets.set(imageSpec.publish.to)
        task.usesService(dockerService)
    }
    
    private void configureComposeUpTask(ComposeUpTask task, ComposeStackSpec stackSpec, 
                                      Provider<ComposeService> composeService, Provider<JsonService> jsonService) {
        task.group = 'docker compose'
        task.description = "Start Docker Compose stack: ${stackSpec.name}"
        task.stackName.set(stackSpec.name)
        task.composeFiles.setFrom(stackSpec.files)
        task.projectName.set(stackSpec.projectName)
        task.waitForHealthy.set(stackSpec.waitForHealthy)
        task.stateFile.set(task.project.layout.buildDirectory.file("compose/state/${stackSpec.name}.json"))
        task.usesService(composeService)
        task.usesService(jsonService)
    }
    
    private void configureComposeDownTask(ComposeDownTask task, ComposeStackSpec stackSpec, Provider<ComposeService> composeService) {
        task.group = 'docker compose'
        task.description = "Stop Docker Compose stack: ${stackSpec.name}"
        task.stackName.set(stackSpec.name)
        task.projectName.set(stackSpec.projectName)
        task.usesService(composeService)
    }
}
```

#### 4. Service Layer Implementation Details  
**Status**: ✅ **ADDRESSED**
**Implementation Impact**: Critical for Docker/Compose integration

**Complete Service Implementations**:

**DockerServiceImpl**:
```groovy
@Singleton
class DockerServiceImpl implements DockerService {
    private final DockerClient dockerClient
    private final ExecutorService executorService
    
    @Inject
    DockerServiceImpl() {
        this.dockerClient = createDockerClient()
        this.executorService = Executors.newCachedThreadPool()
    }
    
    private DockerClient createDockerClient() {
        def dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost("unix:///var/run/docker.sock")  // Default, will detect from environment
            .build()
            
        def httpTransport = new ApacheDockerHttpClient.Builder()
            .dockerHost(dockerClientConfig.getDockerHost())
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()
            
        return DockerClientBuilder.getInstance(dockerClientConfig)
            .withDockerHttpClient(httpTransport)
            .build()
    }
    
    @Override
    CompletableFuture<String> buildImage(BuildContext context) {
        return CompletableFuture.supplyAsync({
            try {
                def buildImageResultCallback = new BuildImageResultCallback() {
                    @Override
                    void onNext(BuildResponseItem item) {
                        if (item.stream) {
                            logger.info("Docker build: {}", item.stream.trim())
                        }
                    }
                }
                
                def imageId = dockerClient.buildImageCmd()
                    .withDockerfile(context.dockerfile.toFile())
                    .withBaseDirectory(context.contextPath.toFile())
                    .withTags(new HashSet<>(context.tags))
                    .withBuildArgs(context.buildArgs)
                    .exec(buildImageResultCallback)
                    .awaitImageId()
                    
                return imageId
                
            } catch (Exception e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.BUILD_FAILED,
                    "Build failed: ${e.message}",
                    e
                )
            }
        }, executorService)
    }
    
    @Override
    CompletableFuture<Void> tagImage(String sourceImage, List<String> tags) {
        return CompletableFuture.runAsync({
            try {
                tags.each { tag ->
                    def parts = parseImageRef(tag)
                    dockerClient.tagImageCmd(sourceImage, parts.repository, parts.tag).exec()
                }
            } catch (Exception e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.TAG_FAILED,
                    "Tag operation failed: ${e.message}",
                    e
                )
            }
        }, executorService)
    }
    
    @Override
    CompletableFuture<Void> pushImage(String imageRef, AuthConfig authConfig) {
        return CompletableFuture.runAsync({
            try {
                def parts = parseImageRef(imageRef)
                def pushCallback = new PushImageResultCallback()
                
                dockerClient.pushImageCmd(parts.repository)
                    .withTag(parts.tag)
                    .withAuthConfig(authConfig)
                    .exec(pushCallback)
                    .awaitCompletion()
                    
            } catch (Exception e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.PUSH_FAILED,
                    "Push failed: ${e.message}",
                    e
                )
            }
        }, executorService)
    }
    
    @Override
    CompletableFuture<Boolean> imageExists(String imageRef) {
        return CompletableFuture.supplyAsync({
            try {
                dockerClient.inspectImageCmd(imageRef).exec()
                return true
            } catch (NotFoundException e) {
                return false
            }
        }, executorService)
    }
    
    private ImageRefParts parseImageRef(String imageRef) {
        def parts = imageRef.split(':')
        return new ImageRefParts(
            repository: parts[0],
            tag: parts.length > 1 ? parts[1] : 'latest'
        )
    }
    
    void close() {
        executorService.shutdown()
        dockerClient.close()
    }
}
```

**ExecLibraryComposeService**:
```groovy
@Singleton
class ExecLibraryComposeService implements ComposeService {
    private final ExecLibrary execLib
    private final JsonService jsonService
    
    @Inject
    ExecLibraryComposeService(JsonService jsonService) {
        validatePlatform()
        this.execLib = new ExecLibrary()
        this.jsonService = jsonService
    }
    
    private void validatePlatform() {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            throw new UnsupportedOperationException(
                "Docker Compose operations are currently supported on Linux/Unix platforms only. " +
                "Windows support is planned for future releases."
            )
        }
    }
    
    @Override
    CompletableFuture<ComposeState> upStack(ComposeConfig config) {
        return CompletableFuture.supplyAsync({
            try {
                def composeArgs = buildComposeArgs(config) + ['up', '-d']
                def result = execLib.execute(composeArgs)
                
                if (result.exitCode != 0) {
                    throw new ComposeServiceException("Compose up failed: ${result.stderr}")
                }
                
                // Parse and return state
                return parseComposeState(config)
                
            } catch (Exception e) {
                throw new ComposeServiceException("Failed to start compose stack: ${e.message}", e)
            }
        })
    }
    
    @Override
    CompletableFuture<Void> downStack(String projectName) {
        return CompletableFuture.runAsync({
            try {
                def composeArgs = ['docker-compose', '-p', projectName, 'down']
                def result = execLib.execute(composeArgs)
                
                if (result.exitCode != 0) {
                    throw new ComposeServiceException("Compose down failed: ${result.stderr}")
                }
                
            } catch (Exception e) {
                throw new ComposeServiceException("Failed to stop compose stack: ${e.message}", e)
            }
        })
    }
    
    @Override
    CompletableFuture<ServiceState> waitForServices(WaitConfig config) {
        return CompletableFuture.supplyAsync({
            def startTime = System.currentTimeMillis()
            def timeout = config.timeoutSeconds * 1000
            
            while (System.currentTimeMillis() - startTime < timeout) {
                def allReady = config.services.every { serviceName ->
                    isServiceInState(config.projectName, serviceName, config.targetState)
                }
                
                if (allReady) {
                    return ServiceState.READY
                }
                
                Thread.sleep(config.pollSeconds * 1000)
            }
            
            throw new ComposeServiceException("Timeout waiting for services: ${config.services}")
        })
    }
    
    private boolean isServiceInState(String projectName, String serviceName, ServiceState targetState) {
        try {
            def psArgs = ['docker-compose', '-p', projectName, 'ps', '--format', 'json']
            def result = execLib.execute(psArgs)
            
            if (result.exitCode != 0) return false
            
            def services = jsonService.parseJson(result.stdout)
            def service = services.find { it.Service == serviceName }
            
            return service && matchesTargetState(service.State, targetState)
            
        } catch (Exception e) {
            logger.debug("Error checking service state: {}", e.message)
            return false
        }
    }
    
    private boolean matchesTargetState(String actualState, ServiceState targetState) {
        switch (targetState) {
            case ServiceState.RUNNING:
                return actualState.toLowerCase().contains('up')
            case ServiceState.HEALTHY:
                return actualState.toLowerCase().contains('healthy')
            default:
                return false
        }
    }
    
    private ComposeState parseComposeState(ComposeConfig config) {
        def psArgs = buildComposeArgs(config) + ['ps', '--format', 'json']
        def result = execLib.execute(psArgs)
        
        if (result.exitCode != 0) {
            throw new ComposeServiceException("Failed to get compose state: ${result.stderr}")
        }
        
        def services = jsonService.parseJson(result.stdout)
        def serviceInfoMap = services.collectEntries { serviceData ->
            [serviceData.Service, new ServiceInfo(
                containerId: serviceData.ID,
                containerName: serviceData.Name,
                state: parseServiceState(serviceData.State),
                publishedPorts: parsePortMappings(serviceData.Ports)
            )]
        }
        
        return new ComposeState(
            stackName: config.stackName,
            services: serviceInfoMap,
            composeProject: config.projectName,
            timestamp: Instant.now()
        )
    }
    
    private List<String> buildComposeArgs(ComposeConfig config) {
        def args = ['docker-compose', '-p', config.projectName]
        config.composeFiles.each { file ->
            args.addAll(['-f', file.toString()])
        }
        return args
    }
}
```

**Service Factory and Dependency Injection**:
```groovy
// In GradleDockerPlugin.groovy
class GradleDockerPlugin implements Plugin<Project> {
    
    @Override
    void apply(Project project) {
        // Register services with project service registry
        def dockerService = project.gradle.sharedServices
            .registerIfAbsent('dockerService', DockerServiceImpl) {}
            
        def composeService = project.gradle.sharedServices
            .registerIfAbsent('composeService', ExecLibraryComposeService) {}
            
        def jsonService = project.gradle.sharedServices
            .registerIfAbsent('jsonService', JsonService) {}
        
        // Create extensions
        def dockerExt = project.extensions.create('docker', DockerExtension)
        def dockerOrchExt = project.extensions.create('dockerOrch', DockerOrchExtension)
        
        // Register tasks with service injection
        registerDockerTasks(project, dockerExt, dockerService, jsonService)
        registerComposeTasks(project, dockerOrchExt, composeService, jsonService)
        
        // Configure task dependencies
        configureDependencies(project, dockerExt)
    }
    
    private void registerDockerTasks(Project project, DockerExtension dockerExt, 
                                   Provider<DockerService> dockerService,
                                   Provider<JsonService> jsonService) {
        project.afterEvaluate {
            dockerExt.images.all { imageSpec ->
                def imageName = imageSpec.name
                
                project.tasks.register("dockerBuild${imageName.capitalize()}", DockerBuildTask) {
                    it.imageName.set(imageName)
                    it.buildArgs.set(imageSpec.buildArgs)
                    it.tags.set(imageSpec.tags)
                    it.contextDirectory.set(imageSpec.context)
                    it.dockerfile.set(imageSpec.dockerfile)
                    it.imageIdFile.set(
                        project.layout.buildDirectory.file("docker/image-ids/${imageName}.txt")
                    )
                    it.usesService(dockerService)
                }
                
                // Register other task types similarly...
            }
        }
    }
}
```

**Resource Management Pattern**:
```groovy
// Service cleanup on build completion
project.gradle.buildFinished { result ->
    try {
        dockerService.get().close()
    } catch (Exception e) {
        logger.warn("Error closing Docker service: {}", e.message)
    }
}
```

#### 4. DSL Implementation Mechanics
**Status**: ✅ **ADDRESSED**
**Implementation Impact**: Critical for configuration parsing

**Complete DSL Implementation**:

**DockerExtension with NamedDomainObjectContainer**:
```groovy
abstract class DockerExtension {
    private final NamedDomainObjectContainer<ImageSpec> images
    private final ObjectFactory objectFactory
    private final Project project
    
    @Inject
    DockerExtension(ObjectFactory objectFactory, Project project) {
        this.objectFactory = objectFactory
        this.project = project
        this.images = objectFactory.domainObjectContainer(ImageSpec) { name ->
            def imageSpec = objectFactory.newInstance(ImageSpec, name)
            configureImageDefaults(imageSpec)
            return imageSpec
        }
    }
    
    NamedDomainObjectContainer<ImageSpec> getImages() {
        return images
    }
    
    void images(@DelegatesTo(NamedDomainObjectContainer) Closure closure) {
        images.configure(closure)
    }
    
    private void configureImageDefaults(ImageSpec imageSpec) {
        // Set default context if not specified
        imageSpec.context.convention(
            project.layout.projectDirectory.dir("src/main/docker")
        )
        
        // Set default dockerfile if not specified
        imageSpec.dockerfile.convention(
            imageSpec.context.file("Dockerfile")
        )
        
        // Default compression for save operations
        if (imageSpec.save) {
            imageSpec.save.compression.convention("gzip")
        }
    }
    
    // Validation method called during configuration
    void validate() {
        images.all { imageSpec ->
            validateImageSpec(imageSpec)
        }
    }
    
    private void validateImageSpec(ImageSpec imageSpec) {
        // Validate required properties
        if (!imageSpec.context.present && !imageSpec.sourceRef.present) {
            throw new GradleException(
                "Image '${imageSpec.name}' must specify either 'context' for building or 'sourceRef' for referencing existing image"
            )
        }
        
        // Validate context exists if specified
        if (imageSpec.context.present) {
            def contextDir = imageSpec.context.get().asFile
            if (!contextDir.exists()) {
                throw new GradleException(
                    "Docker context directory does not exist: ${contextDir.absolutePath}\n" +
                    "Suggestion: Create the directory or update the context path in image '${imageSpec.name}'"
                )
            }
        }
        
        // Validate dockerfile exists
        if (imageSpec.dockerfile.present) {
            def dockerfileFile = imageSpec.dockerfile.get().asFile
            if (!dockerfileFile.exists()) {
                throw new GradleException(
                    "Dockerfile does not exist: ${dockerfileFile.absolutePath}\n" +
                    "Suggestion: Create the Dockerfile or update the dockerfile path in image '${imageSpec.name}'"
                )
            }
        }
        
        // Validate tags format
        imageSpec.tags.get().each { tag ->
            if (!tag.matches(/^[a-zA-Z0-9][a-zA-Z0-9_.-]*:[a-zA-Z0-9][a-zA-Z0-9_.-]*$/)) {
                throw new GradleException(
                    "Invalid Docker tag format: '${tag}' in image '${imageSpec.name}'\n" +
                    "Suggestion: Use format 'repository:tag' (e.g., 'myapp:latest')"
                )
            }
        }
    }
}
```

**ImageSpec with Property-based Configuration**:
```groovy
abstract class ImageSpec {
    private final String name
    
    @Inject
    ImageSpec(String name) {
        this.name = name
    }
    
    String getName() { return name }
    
    @Input
    @Optional
    abstract DirectoryProperty getContext()
    
    @Input
    @Optional
    abstract RegularFileProperty getDockerfile()
    
    @Input
    abstract MapProperty<String, String> getBuildArgs()
    
    @Input
    abstract ListProperty<String> getTags()
    
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    
    @Nested
    @Optional
    abstract Property<SaveSpec> getSave()
    
    @Nested
    @Optional
    abstract Property<PublishSpec> getPublish()
    
    // DSL methods for nested configuration
    void save(@DelegatesTo(SaveSpec) Closure closure) {
        def saveSpec = project.objects.newInstance(SaveSpec)
        closure.delegate = saveSpec
        closure.call()
        save.set(saveSpec)
    }
    
    void publish(@DelegatesTo(PublishSpec) Closure closure) {
        def publishSpec = project.objects.newInstance(PublishSpec)
        closure.delegate = publishSpec
        closure.call()
        publish.set(publishSpec)
    }
    
    // Property defaults with validation
    @Internal
    Provider<DirectoryProperty> getContextWithDefault() {
        return context.orElse(
            project.layout.projectDirectory.dir("src/main/docker")
        )
    }
}
```

**DockerOrchExtension Implementation**:
```groovy
abstract class DockerOrchExtension {
    private final NamedDomainObjectContainer<ComposeStackSpec> composeStacks
    private final ObjectFactory objectFactory
    private final Project project
    
    @Inject
    DockerOrchExtension(ObjectFactory objectFactory, Project project) {
        this.objectFactory = objectFactory
        this.project = project
        this.composeStacks = objectFactory.domainObjectContainer(ComposeStackSpec) { name ->
            def stackSpec = objectFactory.newInstance(ComposeStackSpec, name, project)
            configureStackDefaults(stackSpec)
            return stackSpec
        }
    }
    
    NamedDomainObjectContainer<ComposeStackSpec> getComposeStacks() {
        return composeStacks
    }
    
    void composeStacks(@DelegatesTo(NamedDomainObjectContainer) Closure closure) {
        composeStacks.configure(closure)
    }
    
    private void configureStackDefaults(ComposeStackSpec stackSpec) {
        // Set default project name
        stackSpec.projectName.convention("${project.name}-${stackSpec.name}")
        
        // Set default polling interval
        stackSpec.waitForHealthy.pollSeconds.convention(2)
        stackSpec.waitForRunning.pollSeconds.convention(2)
        
        // Set default timeout
        stackSpec.waitForHealthy.timeoutSeconds.convention(60)
        stackSpec.waitForRunning.timeoutSeconds.convention(60)
    }
    
    void validate() {
        composeStacks.all { stackSpec ->
            validateStackSpec(stackSpec)
        }
    }
    
    private void validateStackSpec(ComposeStackSpec stackSpec) {
        // Validate compose files exist
        stackSpec.files.get().each { file ->
            if (!file.asFile.exists()) {
                throw new GradleException(
                    "Compose file does not exist: ${file.asFile.absolutePath} in stack '${stackSpec.name}'\n" +
                    "Suggestion: Create the compose file or update the path"
                )
            }
        }
        
        // Validate env files if specified
        if (stackSpec.envFiles.present) {
            stackSpec.envFiles.get().each { file ->
                if (!file.asFile.exists()) {
                    throw new GradleException(
                        "Environment file does not exist: ${file.asFile.absolutePath} in stack '${stackSpec.name}'\n" +
                        "Suggestion: Create the env file or remove it from configuration"
                    )
                }
            }
        }
    }
}
```

**Extension Registration and Configuration Processing**:
```groovy
// In GradleDockerPlugin.apply()
@Override
void apply(Project project) {
    // Create extensions with proper injection
    def dockerExt = project.extensions.create('docker', DockerExtension, project.objects, project)
    def dockerOrchExt = project.extensions.create('dockerOrch', DockerOrchExtension, project.objects, project)
    
    // Validation hook
    project.afterEvaluate {
        try {
            dockerExt.validate()
            dockerOrchExt.validate()
        } catch (Exception e) {
            throw new GradleException("Configuration validation failed: ${e.message}", e)
        }
    }
    
    // Register task creation hooks
    registerTaskCreationRules(project, dockerExt, dockerOrchExt)
}
```

**Complex Nested DSL Processing**:
```groovy
// Handle complex nested publish configuration
abstract class PublishSpec {
    private final NamedDomainObjectContainer<PublishTarget> targets
    
    @Inject
    PublishSpec(ObjectFactory objectFactory) {
        this.targets = objectFactory.domainObjectContainer(PublishTarget)
    }
    
    NamedDomainObjectContainer<PublishTarget> getTo() {
        return targets
    }
    
    void to(@DelegatesTo(PublishTarget) Closure closure) {
        targets.create { target ->
            closure.delegate = target
            closure.call()
        }
    }
    
    // Alternative syntax: to("registry-name") { ... }
    void to(String name, @DelegatesTo(PublishTarget) Closure closure) {
        def target = targets.create(name)
        closure.delegate = target
        closure.call()
    }
}
```

### Medium Priority Items (Address During Implementation)

#### 5. Test Integration Implementation
**Current Understanding Gap**: How `usesCompose` method extension actually works in practice
**Expected Address Timeline**: Phase 2, Week 1-2
**Implementation Complexity**: Requires Groovy metaClass manipulation and TestListener implementation

**Key Questions to Resolve**:
- Exact mechanism for extending Test task with custom methods
- TestListener implementation for per-class/method lifecycle management  
- State file path resolution and system property injection
- Parallel test execution with unique Compose project names

#### 6. Error Handling Strategy  
**Current Understanding Gap**: Complete exception hierarchy and user experience design
**Expected Address Timeline**: Phase 2, Week 2-3
**Implementation Complexity**: Need comprehensive error message templates and recovery strategies

**Key Questions to Resolve**:
- Complete exception type hierarchy beyond basic DockerServiceException
- Error message internationalization strategy
- Retry mechanisms for transient network failures
- User-friendly suggestions for common configuration errors

#### 7. Resource Management Strategy
**Current Understanding Gap**: Docker connection lifecycle and cleanup patterns
**Expected Address Timeline**: Phase 2, Week 3-4
**Implementation Complexity**: Critical for preventing resource leaks in long-running builds

**Key Questions to Resolve**:
- Docker client connection pooling vs. per-task instances
- Gradle build lifecycle integration for cleanup
- Process management for long-running Compose operations
- Timeout handling and resource disposal on build cancellation

### Medium-Low Priority Items (Enhancement - Address in Polish Phase)

#### 8. Configuration Management Details
**Current Understanding Gap**: Advanced property resolution and inheritance patterns
**Expected Address Timeline**: Phase 3, Week 1-2
**Implementation Notes**: Can use basic property handling initially, enhance later

#### 9. Performance Implementation
**Current Understanding Gap**: Specific caching strategies and optimization techniques
**Expected Address Timeline**: Phase 3, Week 2-3
**Implementation Notes**: Profile first implementation to identify bottlenecks

#### 10. Platform Abstraction Details
**Current Understanding Gap**: Cross-platform detection and conditional feature enabling
**Expected Address Timeline**: Phase 3, Week 3-4  
**Implementation Notes**: Start with Linux/macOS, add Windows support incrementally

### Low Priority Items (Future Enhancements - Post-Release)

#### 11. Build and Publication Configuration
**Current Understanding Gap**: Complete plugin development and release pipeline setup
**Expected Address Timeline**: Post Phase 3 / Version 1.1
**Implementation Notes**: Can publish manually initially, automate later

#### 12. Advanced Integration Patterns
**Current Understanding Gap**: Extension points and third-party integration patterns  
**Expected Address Timeline**: Version 2.0+ based on user feedback
**Implementation Notes**: Design extension points based on actual usage patterns

## Priority-Based Implementation Strategy

**Immediate Action Required (This Sprint)**:
1. Complete plugin infrastructure specifications with concrete build.gradle and plugin descriptors
2. Detail all task implementation mechanics with specific @TaskAction logic
3. Specify service layer implementations with resource management
4. Document DSL processing mechanics with validation rules

**Next Sprint Focus**:
- Test integration implementation details
- Comprehensive error handling specifications  
- Resource management strategy implementation

**Future Sprint Planning**:
- Configuration management enhancements
- Performance optimization specifications
- Cross-platform abstraction details

This approach ensures we have sufficient detail to begin implementation immediately while acknowledging areas that need further specification during development.

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

