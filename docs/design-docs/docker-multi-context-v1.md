# Docker Multi-Context Support - Design Document

## Document Metadata

| Key     | Value    |
|---------|----------|
| Status  | Planned  |
| Version | 1.0.0    |
| Updated | 2025-09-06 |

## Overview

This document outlines the design for enhancing the gradle-docker plugin to support multiple context sources for Docker image builds. The current implementation only supports a single directory as build context. This enhancement will allow users to specify multiple files and directories that will be aggregated into a temporary build context directory.

## Problem Statement

Currently, users must manually assemble all Docker build context files into a single directory. This is limiting for:

1. **Monorepo Scenarios**: Context files scattered across different directories
2. **Multi-stage Builds**: Need to include artifacts from different build outputs  
3. **Configuration Management**: Separate config files from different sources
4. **Debugging**: No visibility into the final assembled context

## Solution Approach

Leverage Gradle's Copy task to aggregate multiple context sources into a temporary build directory (`build/docker-context/<imageName>`). This provides:

- Native Gradle file handling with incremental build support
- Up-to-date checking and caching
- Powerful include/exclude patterns
- Integration with existing Gradle file operations

## API Design

### Current API (to be replaced)
```groovy
docker {
    images {
        image("alpine") {
            context = file("src/main/docker")
            dockerfile = file("src/main/docker/Dockerfile")
        }
    }
}
```

### New API Design

#### Option 1: Copy Task Reference (Recommended)
```groovy
docker {
    images {
        image("alpine") {
            contextTask = tasks.register("prepareAlpineContext", Copy) {
                into layout.buildDirectory.dir("docker-context/alpine")
                from("src/main/docker")
                from("build/libs") { 
                    include "*.jar"
                    rename { "app.jar" }
                }
                from("config/docker") { include "*.conf" }
            }
            dockerfile = "Dockerfile" // relative to contextTask output
        }
    }
}
```

#### Option 2: Inline Context Configuration
```groovy
docker {
    images {
        image("alpine") {
            context {
                from("src/main/docker")
                from("build/libs") { 
                    include "*.jar"
                    rename { "app.jar" }
                }
                from("config/docker") { include "*.conf" }
                into layout.buildDirectory.dir("docker-context/alpine")
            }
            dockerfile = "Dockerfile"
        }
    }
}
```

## Implementation Plan

### Phase 1: ImageSpec API Changes

1. **Update ImageSpec.groovy**:
   - Replace `DirectoryProperty context` with `Property<TaskProvider<Copy>> contextTask`
   - Add `context(Action<Copy>)` DSL method for inline configuration
   - Update `dockerfile` to support relative paths within context

2. **Context Resolution Logic**:
   - If `contextTask` is provided, use its output directory
   - If inline `context {}` is used, create anonymous Copy task
   - Default fallback to `src/main/docker` if neither specified

### Phase 2: Task Integration

1. **Update DockerBuildTask**:
   - Change `@InputDirectory contextPath` to depend on Copy task output
   - Add dependency on contextTask execution
   - Update build context path resolution

2. **Plugin Task Creation**:
   - Modify `configureDockerBuildTask()` to wire Copy task dependencies
   - Ensure Copy tasks execute before Docker build tasks
   - Handle task ordering for aggregate tasks

### Phase 3: Default Context Handling

1. **Convention-based Defaults**:
   - Auto-create Copy task for `src/main/docker` if no context specified
   - Set default dockerfile path relative to context output
   - Maintain existing validation for Dockerfile existence

2. **Directory Structure**:
   ```
   build/
     docker-context/
       alpine/
         Dockerfile
         app.jar
         config.conf
       ubuntu/
         Dockerfile
         app.jar
         ...
   ```

### Phase 4: Validation & Error Handling

1. **Pre-build Validation**:
   - Ensure contextTask produces valid output directory
   - Validate Dockerfile exists in final context
   - Check for file naming conflicts

2. **Error Messages**:
   - Clear messaging when Copy task fails
   - Helpful suggestions for common configuration issues
   - Context directory inspection commands for debugging

## Updated Use Case Examples

### Build with Multiple Context Sources
```groovy
plugins { 
    id "com.kineticfire.gradle.gradle-docker" version "0.1.0" 
}

docker {
    images {
        image("alpine") {
            contextTask = tasks.register("prepareAlpineContext", Copy) {
                into layout.buildDirectory.dir("docker-context/alpine")
                
                // Base Docker files
                from("src/main/docker") 
                
                // Application JAR
                from("build/libs") { 
                    include "app-*.jar"
                    rename { "app.jar" }
                }
                
                // Configuration files
                from("config/production") {
                    include "*.properties", "*.yml"
                }
                
                // Scripts
                from("scripts") {
                    include "entrypoint.sh"
                    fileMode = 0755
                }
            }
            
            dockerfile = "Dockerfile"
            tags = ["myapp:${version}-alpine", "myapp:alpine"]
            buildArgs = ["JAR_FILE": "app.jar"]
        }
    }
}
```

### Inline Context Configuration
```groovy
docker {
    images {
        image("ubuntu") {
            context {
                from("src/main/docker")
                from("build/libs") { include "*.jar" }
                from("config") { 
                    include "*.conf"
                    into "config/"
                }
            }
            
            dockerfile = "Dockerfile"
            tags = ["myapp:ubuntu"]
        }
    }
}
```

### Single File Context (Edge Case)
```groovy
docker {
    images {
        image("minimal") {
            contextTask = tasks.register("prepareMinimalContext", Copy) {
                into layout.buildDirectory.dir("docker-context/minimal")
                from("docker/Dockerfile.minimal") {
                    rename { "Dockerfile" }
                }
                from("build/libs") { include "app.jar" }
            }
            
            dockerfile = "Dockerfile"
            tags = ["myapp:minimal"]
        }
    }
}
```

## Task Dependencies

```
dockerBuildAlpine
├── prepareAlpineContext (Copy)
│   ├── jar (if building from source)
│   └── [other dependencies]
└── [Docker build execution]

dockerSaveAlpine
└── dockerBuildAlpine
    └── prepareAlpineContext

dockerPublishAlpine  
└── dockerBuildAlpine
    └── prepareAlpineContext
```

## Migration Strategy

Since the plugin hasn't been deployed, no backward compatibility is needed. However:

1. **Update existing integration tests** to use new API
2. **Update use case documentation** with new examples
3. **Verify all functional tests** pass with new context handling

## Performance Considerations

1. **Incremental Builds**: Gradle Copy task provides built-in up-to-date checking
2. **Large Contexts**: Copy task handles large file sets efficiently
3. **Parallel Execution**: Multiple Copy tasks can run in parallel when possible
4. **Caching**: Gradle's build cache applies to Copy task outputs

## Testing Strategy

1. **Unit Tests**: Verify API changes and task configuration
2. **Functional Tests**: Test various context configurations
3. **Integration Tests**: End-to-end Docker builds with complex contexts
4. **Performance Tests**: Measure impact on build times

## Future Enhancements

1. **Context Validation**: Pre-build checks for common issues (sensitive files, size limits)
2. **Context Inspection**: Tasks to preview assembled context before building
3. **Advanced Filtering**: More sophisticated include/exclude patterns
4. **Context Sharing**: Reuse context assemblies across multiple images

## Implementation Checklist

- [ ] Update ImageSpec API
- [ ] Modify DockerBuildTask context handling  
- [ ] Update plugin task configuration logic
- [ ] Add context DSL methods
- [ ] Update validation logic
- [ ] Modify existing tests
- [ ] Add new test scenarios
- [ ] Update use case documentation
- [ ] Update integration test examples