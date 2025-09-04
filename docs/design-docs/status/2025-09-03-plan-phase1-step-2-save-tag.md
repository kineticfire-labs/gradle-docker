# Detailed Implementation Plan: Phase 1, Step 2 - Docker Image Export/Import

## Overview

This detailed plan focuses on implementing comprehensive Docker image export/import functionality, transforming the current 33-line placeholder `DockerSaveTask` into a production-ready component. Additionally, it enhances the existing `DockerTagTask` to support advanced tagging scenarios from UC-6. The implementation emphasizes multiple compression options, sourceRef handling, and comprehensive test coverage.

## Phase 1, Step 2: Docker Image Export/Import (9-11 hours total)

### What Is Needed

**1. Complete DockerSaveTask Implementation**
- Replace placeholder with real Docker Java API integration
- Support multiple compression formats: gzip (default), none, bzip2, xz, zip
- Handle both built images and sourceRef scenarios
- Integration with existing `DockerService` abstraction layer
- Proper input/output annotations for Gradle build caching
- Progress reporting for large image exports

**2. Enhanced DockerTagTask Implementation**
- Complete beyond current basic implementation
- Support advanced tagging scenarios from UC-6
- Handle sourceRef tagging (re-tagging existing images)
- Add pullIfMissing functionality
- Support batch tagging operations

**3. Docker Service Layer Enhancements**
- Extend `DockerServiceImpl` with comprehensive save/export operations
- Add image tagging operations with validation
- Implement image pulling with conditional logic
- Enhanced error handling for all operations

**4. Compression Framework**
- Pluggable compression system supporting multiple formats
- Stream-based compression for memory efficiency
- Progress monitoring during compression operations
- Compression ratio reporting and optimization

### Why This Approach

**Comprehensive Image Lifecycle:**
- Complete the build → tag → save → publish pipeline
- Support offline workflows with image export/import
- Enable complex tagging strategies for different environments
- Provide flexibility for various deployment scenarios

**Performance and Efficiency:**
- Multiple compression options optimize for different use cases
- Stream-based processing handles large images efficiently
- Proper caching integration minimizes redundant operations
- Progress reporting provides user feedback for long operations

**Flexibility and Extensibility:**
- Support both local builds and remote image references
- Conditional pulling reduces network overhead
- Batch operations improve performance
- Extensible compression framework for future formats

### How To Implement

#### 1. DockerSaveTask Implementation (4-5 hours)

##### Core Task Structure (1-2 hours)

```groovy
abstract class DockerSaveTask extends DefaultTask {
    @Input
    abstract Property<String> getImageName()
    
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    
    @Input
    abstract Property<CompressionType> getCompression()
    
    @OutputFile
    abstract RegularFileProperty getOutputFile()
    
    @Input
    @Optional
    abstract Property<Boolean> getPullIfMissing()
    
    @Nested
    abstract Property<DockerService> getDockerService()
    
    @TaskAction
    void saveImage() {
        // Implementation here
    }
}
```

**Key Implementation Requirements:**
- Determine image source (built image ID file vs. sourceRef)
- Handle compression selection and validation
- Integrate with enhanced DockerService save operations
- Provide progress reporting for large operations
- Proper error handling with meaningful messages

##### Compression Framework Implementation (2-3 hours)

**Enhanced CompressionType Enum:**
```groovy
enum CompressionType {
    NONE("tar", null),
    GZIP("tar.gz", "gzip"),
    BZIP2("tar.bz2", "bzip2"),
    XZ("tar.xz", "xz"),
    ZIP("zip", "zip")
    
    final String fileExtension
    final String compressionCommand
    
    CompressionType(String fileExtension, String compressionCommand) {
        this.fileExtension = fileExtension
        this.compressionCommand = compressionCommand
    }
}
```

**Compression Implementation Strategy:**
```groovy
class CompressionService {
    void compressImage(InputStream imageStream, OutputStream outputStream, 
                      CompressionType compression, ProgressCallback callback) {
        switch (compression) {
            case CompressionType.GZIP:
                compressWithGzip(imageStream, outputStream, callback)
                break
            case CompressionType.BZIP2:
                compressWithBzip2(imageStream, outputStream, callback)
                break
            case CompressionType.XZ:
                compressWithXz(imageStream, outputStream, callback)
                break
            case CompressionType.ZIP:
                compressWithZip(imageStream, outputStream, callback)
                break
            case CompressionType.NONE:
                copyWithProgress(imageStream, outputStream, callback)
                break
        }
    }
}
```

**Compression Requirements:**
- Stream-based processing for memory efficiency
- Progress callbacks for user feedback
- Compression ratio reporting
- Error handling for compression failures
- Support for large image files (multi-GB)

#### 2. Enhanced DockerTagTask Implementation (2-3 hours)

##### Advanced Tagging Scenarios (1-2 hours)

**Enhanced Task Structure:**
```groovy
abstract class DockerTagTask extends DefaultTask {
    @Input
    abstract Property<String> getSourceImage()
    
    @Input
    abstract ListProperty<String> getTargetTags()
    
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    
    @Input
    @Optional
    abstract Property<Boolean> getPullIfMissing()
    
    @Nested
    abstract Property<DockerService> getDockerService()
    
    @TaskAction
    void tagImage() {
        // Implementation here
    }
}
```

**UC-6 Advanced Tagging Scenarios:**
- **Local Build Tagging:** Tag images built locally with additional tags
- **SourceRef Tagging:** Re-tag existing images from registries
- **Batch Tagging:** Apply multiple tags to single image efficiently
- **Conditional Pulling:** Pull source images only if not locally available
- **Cross-Registry Tagging:** Tag images for different registry destinations

##### PullIfMissing Implementation (1 hour)

**Conditional Pull Logic:**
```groovy
private String resolveSourceImage(String sourceRef, boolean pullIfMissing) {
    if (dockerService.imageExists(sourceRef).get()) {
        return sourceRef
    }
    
    if (pullIfMissing) {
        logger.lifecycle("Pulling missing image: ${sourceRef}")
        dockerService.pullImage(sourceRef, null).get()
        return sourceRef
    }
    
    throw new DockerServiceException("Image not found locally: ${sourceRef}. Set pullIfMissing=true to pull automatically.")
}
```

#### 3. Service Layer Enhancements (2-3 hours)

##### DockerService Interface Extensions (1 hour)

```groovy
interface DockerService {
    // Save operations
    CompletableFuture<Void> saveImage(String imageRef, Path outputFile, CompressionType compression)
    CompletableFuture<SaveResult> saveImageWithProgress(String imageRef, Path outputFile, 
                                                       CompressionType compression, ProgressCallback callback)
    
    // Tag operations  
    CompletableFuture<Void> tagImage(String sourceImage, List<String> targetTags)
    CompletableFuture<Void> tagImageBatch(String sourceImage, List<String> targetTags)
    
    // Pull operations
    CompletableFuture<Void> pullImage(String imageRef, AuthConfig authConfig)
    CompletableFuture<PullResult> pullImageWithProgress(String imageRef, AuthConfig authConfig, ProgressCallback callback)
    
    // Existing methods...
}
```

##### DockerServiceImpl Enhancements (1-2 hours)

**Save Operation Implementation:**
```groovy
@Override
CompletableFuture<Void> saveImage(String imageRef, Path outputFile, CompressionType compression) {
    return CompletableFuture.runAsync(() -> {
        try {
            // Get image as tar stream from Docker
            def saveImageCmd = dockerClient.saveImageCmd(imageRef)
            def imageStream = saveImageCmd.exec()
            
            // Apply compression and write to output file
            Files.createDirectories(outputFile.parent)
            try (def fileOutputStream = Files.newOutputStream(outputFile)) {
                compressionService.compressImage(imageStream, fileOutputStream, compression, null)
            }
            
            logger.lifecycle("Image saved: ${imageRef} -> ${outputFile} (${compression})")
            
        } catch (Exception e) {
            throw new DockerServiceException("Failed to save image: ${e.message}", e)
        }
    }, executorService)
}
```

**Tag Operation Implementation:**
```groovy
@Override  
CompletableFuture<Void> tagImageBatch(String sourceImage, List<String> targetTags) {
    return CompletableFuture.runAsync(() -> {
        try {
            targetTags.each { targetTag ->
                def parts = parseImageRef(targetTag)
                dockerClient.tagImageCmd(sourceImage, parts.repository, parts.tag).exec()
                logger.debug("Tagged ${sourceImage} as ${targetTag}")
            }
            logger.lifecycle("Applied ${targetTags.size()} tags to ${sourceImage}")
        } catch (Exception e) {
            throw new DockerServiceException("Failed to tag image: ${e.message}", e)
        }
    }, executorService)
}
```

### Testing Strategy

#### Unit Test Coverage (Target: 100% line & branch coverage)

##### DockerSaveTask Unit Tests
```groovy
class DockerSaveTaskTest extends Specification {
    def "should handle all compression types"
    def "should resolve source image from build output"
    def "should resolve source image from sourceRef"
    def "should create output directory if missing"
    def "should validate output file permissions"
    def "should handle compression failures gracefully"
    def "should report progress for large images"
    def "should handle pullIfMissing scenarios"
}
```

##### DockerTagTask Unit Tests
```groovy
class DockerTagTaskTest extends Specification {
    def "should handle multiple target tags"
    def "should resolve source from built image"
    def "should resolve source from sourceRef"
    def "should handle pullIfMissing logic"
    def "should validate tag format"
    def "should handle batch tagging operations"
    def "should propagate service errors properly"
}
```

##### Compression Framework Unit Tests
```groovy
class CompressionServiceTest extends Specification {
    def "should compress with gzip format"
    def "should compress with bzip2 format" 
    def "should compress with xz format"
    def "should compress with zip format"
    def "should handle uncompressed format"
    def "should report compression progress"
    def "should handle compression errors"
    def "should calculate compression ratios"
}
```

#### Integration Test Coverage (Target: 100% functionality coverage)

##### Image Export Integration Tests
```groovy
class DockerSaveIntegrationIT extends Specification {
    def "should export image with gzip compression"
    def "should export image with bzip2 compression"
    def "should export image with xz compression"  
    def "should export image with zip compression"
    def "should export image without compression"
    def "should export large images efficiently"
    def "should handle sourceRef export with pull"
    def "should validate exported image integrity"
    def "should handle concurrent export operations"
}
```

##### Image Tagging Integration Tests
```groovy
class DockerTagIntegrationIT extends Specification {
    def "should tag built image with multiple tags"
    def "should retag existing image from registry"
    def "should pull missing image when configured"
    def "should handle cross-registry tagging"
    def "should validate tag operations in Docker"
    def "should handle batch tagging performance"
}
```

##### End-to-End Workflow Tests
```groovy
class DockerImageLifecycleIT extends Specification {
    def "should complete build -> tag -> save workflow"
    def "should complete sourceRef -> tag -> save workflow" 
    def "should handle complex multi-registry scenarios"
    def "should validate saved image can be loaded"
    def "should handle error recovery in pipeline"
}
```

#### Performance Testing

##### Load Testing Scenarios
- Export large images (1GB+) with different compression formats
- Concurrent save operations with multiple images
- Batch tagging operations with many target tags
- Memory usage monitoring during compression operations

##### Performance Benchmarks
- Compression speed vs. ratio trade-offs for each format
- Export time comparison across compression types
- Memory consumption during large image operations
- Disk I/O patterns and optimization opportunities

### Implementation Details

#### Compression Format Selection Guide

**GZIP (Default):**
- **Use Case:** General purpose, good balance of speed and compression
- **Compression Ratio:** ~70% reduction
- **Speed:** Fast compression and decompression
- **Compatibility:** Universal support

**BZIP2:**  
- **Use Case:** Better compression ratio when storage space is critical
- **Compression Ratio:** ~75% reduction
- **Speed:** Slower than gzip, better than xz
- **Compatibility:** Widely supported

**XZ:**
- **Use Case:** Maximum compression for long-term archival
- **Compression Ratio:** ~80% reduction  
- **Speed:** Slowest compression, reasonable decompression
- **Compatibility:** Modern systems

**ZIP:**
- **Use Case:** Windows compatibility and single-file distribution
- **Compression Ratio:** ~70% reduction
- **Speed:** Good compression speed
- **Compatibility:** Universal, especially Windows

**NONE:**
- **Use Case:** Maximum speed when compression not needed
- **Compression Ratio:** 0% (no compression)
- **Speed:** Fastest (direct copy)
- **Compatibility:** Universal

#### DSL Integration Examples

**Save Configuration:**
```groovy
docker {
    images {
        image("myapp") {
            // ... build configuration ...
            save {
                compression = "xz"  // gzip, none, bzip2, xz, zip
                outputFile = layout.buildDirectory.file("docker/myapp-${version}.tar.xz")
                pullIfMissing = true
            }
        }
    }
}
```

**Advanced Tagging Configuration:**
```groovy
docker {
    images {
        image("myapp") {
            sourceRef = "registry.example.com/myapp:base"
            tags = [
                "myapp:${version}",
                "myapp:latest", 
                "registry.example.com/myapp:${version}",
                "registry.example.com/myapp:latest"
            ]
            pullIfMissing = true
        }
    }
}
```

### Error Handling and Edge Cases

#### Common Error Scenarios
- Source image not found locally
- Insufficient disk space for export
- Compression library unavailable  
- Output file permissions denied
- Network timeout during pull operations
- Corrupted image data during export

#### Error Handling Strategy
```groovy
class DockerImageExportException extends DockerServiceException {
    enum ErrorType {
        SOURCE_IMAGE_NOT_FOUND("Source image not found. Consider setting pullIfMissing=true."),
        COMPRESSION_FAILED("Compression failed. Try a different compression type or check available disk space."),
        OUTPUT_FILE_ERROR("Cannot write to output file. Check directory permissions and available disk space."),
        PULL_FAILED("Failed to pull source image. Check network connectivity and authentication.")
        
        final String suggestion
        ErrorType(String suggestion) { this.suggestion = suggestion }
    }
}
```

### Success Criteria

#### Step 2 Success Criteria
- [ ] DockerSaveTask exports images with all compression formats (gzip, none, bzip2, xz, zip)
- [ ] DockerTagTask supports advanced tagging scenarios from UC-6
- [ ] PullIfMissing functionality works correctly
- [ ] SourceRef and built image scenarios both supported
- [ ] 100% unit test coverage for all new functionality
- [ ] Integration tests validate real Docker operations
- [ ] Performance acceptable for large images (1GB+)
- [ ] Error handling provides clear user guidance
- [ ] Progress reporting works for long operations
- [ ] Memory usage remains reasonable during compression

#### Quality Validation
- [ ] All compression formats produce valid, loadable images
- [ ] Compression ratios meet expected performance characteristics
- [ ] Concurrent operations don't interfere with each other
- [ ] Resource cleanup prevents memory/disk leaks
- [ ] Cross-platform compatibility (Linux, macOS)

This detailed plan provides comprehensive guidance for implementing robust Docker image export/import functionality with multiple compression options and advanced tagging capabilities.