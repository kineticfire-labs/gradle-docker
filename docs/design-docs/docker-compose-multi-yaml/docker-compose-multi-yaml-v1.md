# Docker Compose Multi-YAML Support Implementation Plan v1

## Overview

This document outlines the implementation plan to enhance the Gradle Docker Plugin's `composeUp` task to support multiple Docker Compose YAML files. The enhancement allows users to specify multiple compose files, similar to Docker Compose's `-f` flag functionality.

### Current Behavior
- Single compose file via `composeFile` property in DSL
- Single file passed to `docker compose -f <file>` command

### Target Behavior  
- Multiple compose files via new `composeFiles` collection property
- Multiple files passed as `docker compose -f <file1> -f <file2> -f <file3>` 
- Order-sensitive file merging (last file takes precedence)
- **ComposeDown automatically uses same files as ComposeUp** for proper service teardown
- User can optionally specify different files for ComposeDown if needed
- Backward compatibility with existing single-file configurations

## Key Findings

### Current Implementation Analysis

**Good News**: The core infrastructure already supports multiple files!

1. **`ComposeUpTask`** (line 46): Already has `ConfigurableFileCollection getComposeFiles()`
2. **`ComposeDownTask`**: Likely has similar `ConfigurableFileCollection getComposeFiles()` capability
3. **`ComposeConfig`** (line 26): Already accepts `List<Path> composeFiles`  
4. **`ExecLibraryComposeService`** (lines 95-97): Already iterates over multiple files
5. **Tests**: `ComposeUpTaskTest.groovy` line 168 already tests multiple file scenarios

**Main Gap**: The DSL configuration (`ComposeStackSpec`) only exposes single-file properties to users.

### Constraint Analysis

#### Gradle 9 Configuration Cache Compatibility ✅
- Current implementation uses Gradle's Provider API correctly
- `ConfigurableFileCollection` is fully serializable
- No project access during task execution
- Existing pattern already compliant with configuration cache requirements

#### Test Coverage Requirements ✅  
- Unit tests: Current `ComposeUpTaskTest` has 100% coverage including multi-file tests
- Functional tests: Temporarily disabled due to Gradle 9 TestKit issues (will remain disabled)
- Integration tests: Need updates to test multi-file scenarios

## Implementation Plan

### Phase 1: DSL Enhancement (Primary Changes)

#### 1.1 Update `ComposeStackSpec.groovy`

**Current single-file properties** (keep for backward compatibility):
```groovy
abstract RegularFileProperty getComposeFile()
```

**Add new multi-file properties**:
```groovy
abstract ListProperty<String> getComposeFiles()  // File paths
abstract ConfigurableFileCollection getComposeFileCollection() // File objects
```

**Add DSL methods**:
```groovy
void composeFiles(String... files) {
    composeFiles.set(Arrays.asList(files))
}

void composeFiles(List<String> files) {
    composeFiles.set(files)  
}

void composeFiles(File... files) {
    composeFileCollection.from(files)
}
```

#### 1.2 Update Task Configuration Logic

**In plugin configuration** (where tasks are created):
- Configure `ComposeUpTask.composeFiles` from `ComposeStackSpec.composeFiles` 
- **Configure `ComposeDownTask.composeFiles` to automatically use same files as `ComposeUpTask`**
- Allow optional override: if `ComposeStackSpec` has separate down files, use those instead
- Handle backward compatibility: if `composeFile` is set, use it; if `composeFiles` is set, use that
- Validate at least one file is specified
- Convert file paths to File objects and validate existence

#### 1.3 Backward Compatibility Strategy

**Legacy single-file support**:
```groovy
dockerOrch {
    composeStacks {
        webapp {
            composeFile = 'docker-compose.yml'  // Still works
        }
    }
}
```

**New multi-file support**:
```groovy  
dockerOrch {
    composeStacks {
        webapp {
            composeFiles('docker-compose.yml', 'docker-compose.override.yml')
            // ComposeDown automatically uses same files as ComposeUp
            
            // OR
            composeFiles = ['docker-compose.yml', 'docker-compose.override.yml'] 
            
            // OR  
            composeFiles(file('base.yml'), file('prod.yml'))
            
            // Optional: Specify different files for ComposeDown if needed
            // composeDownFiles('docker-compose.yml')  // hypothetical API
        }
    }
}
```

### Phase 2: Test Updates

#### 2.1 Unit Tests (plugin/src/test/)

**New test cases needed**:

1. **`ComposeStackSpecTest.groovy`** (new file):
   - Test `composeFiles` property setters and getters  
   - Test DSL methods (`composeFiles(String...)`, etc.)
   - Test backward compatibility (both `composeFile` and `composeFiles` work)
   - Test validation (empty files list, non-existent files)
   - Test file ordering preservation

2. **`ComposeUpTaskTest.groovy`** updates:
   - Test task configuration from multi-file `ComposeStackSpec`
   - Test error scenarios (missing files, empty collection)
   - Verify file order is preserved in `ComposeConfig`
   - Test mixed single/multi-file configurations

3. **`ComposeDownTaskTest.groovy`** updates:
   - Test that ComposeDown automatically uses same files as ComposeUp
   - Test file order preservation for proper service teardown
   - Test optional override capability for ComposeDown-specific files
   - Test integration between ComposeUp and ComposeDown task configuration

3. **`DockerOrchExtensionTest.groovy`** updates:
   - Test new multi-file DSL parsing
   - Test validation of multi-file stacks
   - Test error messages for invalid configurations

**Coverage requirements**: Maintain 100% line and branch coverage

#### 2.2 Integration Tests (plugin-integration-test/)

**New test scenarios**:

1. **Multi-file compose up/down** test:
   - Base compose file + override file
   - Verify services from both files are running after composeUp
   - Test precedence (override file wins for conflicts)
   - **Verify composeDown uses same files automatically for proper teardown**
   - Test that all services are properly stopped

2. **Complex multi-file scenario**:
   - 3+ compose files with overlapping services
   - Verify final configuration matches expected precedence
   - Test with environment files

3. **Error handling**:
   - Invalid file paths in multi-file configuration
   - Mixing valid and invalid files

**Files to update**:
- Create new test class: `MultiFileComposeIntegrationIT.java`
- Update existing `EnhancedComposeIntegrationIT.java` with multi-file scenarios
- Add test compose files in `src/integrationTest/resources/compose/`

#### 2.3 Functional Tests

**Status**: Will remain disabled due to Gradle 9 TestKit incompatibility
**Action**: Document the multi-file scenarios that would be tested if functional tests were enabled

### Phase 3: Documentation and Validation

#### 3.1 Documentation Updates

**Files to update**:
- Plugin README: Add multi-file examples
- Javadoc/Groovydoc: Update `ComposeStackSpec` documentation
- Integration test documentation: Document new test scenarios

#### 3.2 Validation Requirements

**Pre-commit validation**:
1. All unit tests pass with 100% coverage
2. All integration tests pass  
3. Gradle configuration cache compatibility verified:
   ```bash
   cd plugin && ./gradlew clean build --configuration-cache
   ```
4. Integration tests pass:
   ```bash
   cd plugin-integration-test && ./gradlew clean testAll
   ```

## Implementation Sequence

### Step 1: Core DSL Changes
1. Update `ComposeStackSpec.groovy` with new properties and methods
2. Update plugin configuration logic to handle multi-file stacks
3. Run existing unit tests to ensure no regressions

### Step 2: Enhanced Testing  
1. Add new unit tests for `ComposeStackSpec` multi-file functionality
2. Update existing task and extension tests
3. Verify 100% test coverage maintained

### Step 3: Integration Testing
1. Create new multi-file integration test scenarios
2. Update existing integration tests
3. Validate end-to-end functionality

### Step 4: Validation and Documentation
1. Run full test suite including configuration cache compatibility
2. Update documentation with new API examples
3. Verify backward compatibility with existing projects

## Risk Assessment

### Low Risk Areas ✅
- **Core task execution**: Already implemented and tested
- **Service layer**: Already handles multiple files correctly
- **Configuration cache**: Current patterns are compliant
- **Backward compatibility**: Single-file properties remain unchanged

### Medium Risk Areas ⚠️
- **DSL API design**: New properties must integrate smoothly with existing API
- **File validation**: Need robust error handling for invalid file collections
- **Test coverage**: Must maintain 100% coverage with new functionality

### Mitigation Strategies
- **Incremental testing**: Test each component in isolation before integration
- **Backward compatibility testing**: Verify existing configurations continue to work
- **Error message quality**: Provide clear guidance when configuration is invalid

## File Change Summary

### New Files
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/ComposeStackSpecTest.groovy`
- `plugin-integration-test/app-image/src/integrationTest/java/.../MultiFileComposeIntegrationIT.java`
- `plugin-integration-test/app-image/src/integrationTest/resources/compose/multi-file-*.yml` (test files)

### Modified Files  
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/ComposeStackSpec.groovy`
- `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtension.groovy` (task configuration)
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/ComposeUpTaskTest.groovy`
- `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerOrchExtensionTest.groovy`
- `plugin-integration-test/app-image/src/integrationTest/java/.../EnhancedComposeIntegrationIT.java`

### Configuration Files
- No changes to `build.gradle` files - implementation uses existing Gradle APIs
- No changes to CI/CD - existing test commands remain the same

## Success Criteria

1. **Functional**: Users can specify multiple compose files in DSL and they are passed correctly to Docker Compose
2. **Compatible**: Existing single-file configurations continue to work unchanged  
3. **Tested**: 100% unit test coverage maintained, integration tests cover multi-file scenarios
4. **Cache-safe**: Configuration cache compatibility verified with `--configuration-cache` flag
5. **Documented**: Clear examples and error messages for new multi-file functionality

## Future Enhancements (Out of Scope)

- **File watching**: Automatic task re-execution when compose files change
- **Compose file templating**: Dynamic compose file generation
- **Service-specific file mapping**: Different files for different services
- **Validation**: Compose file syntax validation before execution

## Conclusion

This implementation leverages existing multi-file infrastructure in the core plugin, requiring primarily DSL enhancements and comprehensive testing. The approach minimizes risk while providing the requested functionality with full backward compatibility and Gradle 9 configuration cache support.