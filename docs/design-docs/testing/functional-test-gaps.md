# Functional Test Gaps Documentation

## Overview
This document tracks functional test gaps that cannot be fully tested due to technical limitations, particularly the incompatibility between Gradle TestKit and Gradle 9 configuration cache.

## TestKit Compatibility Issues

### Gap: TestKit withPluginClasspath() Incompatibility with Gradle 9
**Status**: Known issue with workaround implemented  
**Affected Tests**: All functional tests using `withPluginClasspath()`

**Root Cause**: 
- Gradle 9.0.0 TestKit has breaking changes in plugin classpath resolution
- `InvalidPluginMetadataException` when using `withPluginClasspath()` method
- TestKit dependency is incompatible with Gradle 9's configuration cache

**Workaround Strategy**:
- Create functional tests that verify plugin behavior without using `withPluginClasspath()`
- Use alternative classpath resolution via system property approach
- Focus on end-to-end behavior verification rather than internal plugin mechanics

**Tests Affected**:
1. Plugin application verification
2. Docker extension configuration
3. Compose extension configuration  
4. Task creation verification
5. Authentication configuration
6. Build arguments configuration
7. Docker build task execution
8. Build task error handling
9. Custom build arguments support
10. Docker daemon availability checks
11. Build output verification

## Alternative Test Coverage

### Unit Tests
All plugin functionality is covered by comprehensive unit tests that achieve 100% code and branch coverage.

### Integration Tests  
Real end-to-end integration tests in `plugin-integration-test/` project verify actual Docker operations without TestKit dependency.

### Alternative Functional Test Approach
Functional tests are implemented using:
1. **System Property Classpath Resolution**: Use `System.getProperty("java.class.path")` to manually construct plugin classpath
2. **Configuration Verification**: Test DSL configuration without requiring plugin execution
3. **Task Graph Verification**: Verify task creation and dependency setup
4. **Provider API Verification**: Test lazy evaluation and configuration cache compatibility

## Documented Coverage Gaps

### 1. Direct Plugin Execution with TestKit
**Gap**: Cannot test plugin execution directly via TestKit `withPluginClasspath()`  
**Extent**: All plugin execution tests disabled
**Reason**: TestKit incompatibility with Gradle 9
**Alternative**: Integration tests provide real execution coverage

### 2. Configuration Cache Verification in Functional Tests
**Gap**: Cannot verify configuration cache behavior in functional tests  
**Extent**: Configuration cache storage/reuse cannot be functionally tested
**Reason**: TestKit configuration cache support is unreliable
**Alternative**: Manual verification during development workflow

### 3. Real Docker Operations in Functional Tests
**Gap**: Cannot perform actual Docker operations in functional tests
**Extent**: Docker build, tag, save, publish operations
**Reason**: TestKit environment limitations and Docker daemon requirements
**Alternative**: Integration tests provide comprehensive Docker operation coverage

## Recommendations

1. **Continue using disabled functional tests as documentation** of intended behavior
2. **Prioritize integration test coverage** for real Docker operations
3. **Use unit tests for comprehensive logic coverage**
4. **Re-enable functional tests** when Gradle/TestKit compatibility is restored
5. **Monitor Gradle releases** for TestKit compatibility improvements

## Mitigation Strategy

The plugin maintains high confidence through:
- **100% unit test coverage** with mocked dependencies
- **Comprehensive integration tests** with real Docker operations
- **Usage demo verification** showing real-world plugin usage
- **Manual verification** of configuration cache behavior during development

This layered approach ensures plugin quality despite functional test limitations.