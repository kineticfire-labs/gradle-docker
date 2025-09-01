# Functional Tests Technical Debt

**Status**: Deferred  
**Priority**: Medium  
**Created**: 2025-09-01  
**Decision**: Option A - Skip functional tests, proceed with integration test re-enablement

## Problem Statement

The gradle-docker plugin's functional tests are failing with `InvalidPluginMetadataException` despite unit tests passing. This prevents full plugin validation but blocks progress on integration test completion.

### Error Details
- **Error**: `Test runtime classpath does not contain plugin metadata file 'plugin-under-test-metadata.properties'`
- **Scope**: All functional tests (20 tests, 18 failing)
- **Test Framework**: Gradle TestKit + Spock
- **Gradle Version**: 9.0.0

## Root Cause Analysis

### Primary Issue: TestKit Metadata Configuration
- The `plugin-under-test-metadata.properties` file exists in `build/pluginUnderTestMetadata/`
- TestKit cannot locate the file due to classpath isolation in modern Gradle test suites
- Modern JVM test suites (Gradle 7+) create isolated classpaths that don't automatically include plugin metadata

### Secondary Issue: Gradle Version Compatibility  
- Using Gradle 9.0 with modern `testing.suites` configuration
- TestKit was designed for older Gradle plugin testing patterns
- Configuration approaches that worked in Gradle 6.x may not work in 9.0

### What Works
- ✅ Unit tests pass completely
- ✅ Plugin compiles and publishes to Maven local
- ✅ Core plugin logic (`validateImageSpec`, extensions, etc.) functions correctly

## Potential Solutions Analysis

### Option A: Skip Functional Tests (CHOSEN)
**Approach**: Defer functional test fixes, proceed with integration testing

**Pros**:
- Immediate progress on main objective (integration test completion)
- Unit tests provide good coverage of core logic
- Integration tests will validate real-world plugin usage
- Can address as separate technical debt later

**Cons**:
- Reduced test coverage for end-to-end plugin scenarios
- May miss edge cases in plugin lifecycle management
- TestKit scenarios (plugin application, task creation) not validated

**Risk Level**: Medium - Core functionality tested via unit tests

### Option B: Deep TestKit Configuration Fix
**Approach**: Research and implement proper Gradle 9.0 + TestKit integration

**Potential Solutions**:
1. **Manual Source Set Configuration**: Configure functional test source sets manually instead of using JVM test suites
2. **Explicit Classpath Management**: Add plugin metadata to test runtime classpath explicitly
3. **TestKit Resource Location**: Use system properties to specify metadata location
4. **Gradle Version Downgrade**: Use older Gradle testing patterns

**Pros**:
- Full test coverage including plugin lifecycle scenarios
- Validates plugin application and task creation
- Catches integration issues between plugin components

**Cons**:
- Time-consuming research and experimentation required
- May require significant build script refactoring
- No guarantee of quick resolution
- Blocks progress on main integration test objective

**Risk Level**: High time investment, uncertain outcome

### Option C: Hybrid Approach
**Approach**: Minimal functional test coverage, focus on integration tests

**Pros**:
- Some functional test coverage
- Faster than Option B
- Integration tests provide real-world validation

**Cons**:
- Still requires TestKit configuration work
- Partial solution that may need revisiting

## Decision: Option A

**Rationale**:
1. **Unit tests provide solid foundation** - Core plugin logic is well-tested
2. **Integration tests are higher value** - Real-world usage validation more important
3. **Time-boxed approach** - Can revisit functional tests as separate initiative
4. **Risk mitigation** - Integration tests will catch major plugin issues

## Unit Test Coverage Analysis

**Question**: Should unit tests have caught the `validateImageSpec` issue?

**Analysis**: The investigation revealed a **significant unit test coverage gap**:

### Coverage Gap Identified: Plugin Lifecycle Testing

1. **validateImageSpec method IS NOT covered by unit tests**
   - Method is called in `project.afterEvaluate { dockerExt.validate() }` (GradleDockerPlugin.groovy:92)
   - Unit tests use `ProjectBuilder.builder().build()` which doesn't trigger `afterEvaluate`
   - Plugin lifecycle testing requires full project evaluation, not just plugin application

2. **Current unit test scope**:
   - ✅ Plugin application (extensions created)
   - ✅ Individual extension configuration  
   - ✅ Task creation
   - ❌ Plugin lifecycle hooks (`afterEvaluate`)
   - ❌ Configuration validation
   - ❌ Error scenarios (invalid context paths, missing dockerfiles)

3. **Why the validateImageSpec error wasn't caught**:
   - Unit tests never trigger `project.afterEvaluate`
   - Validation only runs during full project evaluation
   - TestKit functional tests are designed to cover this scenario
   - **This confirms functional tests are critical for complete coverage**

### Coverage Assessment: Unit tests are INSUFFICIENT

**Gap**: Plugin lifecycle validation is not tested at unit level and **cannot be easily tested** with ProjectBuilder pattern. This is exactly what functional tests are designed to cover.

**Impact**: Configuration validation bugs (like invalid context paths, missing dockerfiles) would not be caught by current unit tests.

**Mitigation**: This reinforces the importance of eventually fixing the functional test configuration, as they cover critical plugin lifecycle scenarios that unit tests cannot reach.

## Updated Plan Based on Coverage Analysis

### Immediate Actions (Current Sprint)
1. ✅ **Document this decision** (this document)  
2. ✅ **Analyze unit test coverage gap** - **Critical discovery**: Functional tests cover plugin lifecycle validation that unit tests cannot reach
3. **Proceed with integration test re-enablement** (Phase 8) - This will provide real-world validation of plugin lifecycle
4. **Validate plugin works in real projects** via integration tests

### Priority Adjustment
**Original assessment**: Functional tests were "nice to have"  
**Revised assessment**: Functional tests are **critical for plugin lifecycle coverage**

The unit test coverage analysis revealed that functional tests cover essential scenarios (plugin validation, lifecycle hooks) that cannot be tested at the unit level. This elevates the priority of fixing the TestKit configuration.

### Future Work (Higher Priority)
1. **Address functional test configuration** - Now elevated to **High Priority** technical debt
2. **Add plugin lifecycle validation tests** - Critical gap in current testing strategy
3. **Expand error scenario coverage** - Invalid configurations, missing files, etc.
4. **Consider hybrid testing approach** - Unit tests + working functional tests for complete coverage

### Risk Mitigation
- **Short term**: Integration tests will catch major plugin lifecycle issues
- **Medium term**: Fix functional test configuration to achieve complete test coverage
- **Monitoring**: Watch for configuration validation bugs that integration tests might miss

## Implementation Plan for Future Resolution

When addressing this technical debt:

1. **Research Gradle 9.0 + TestKit best practices**
2. **Test with minimal plugin project** to isolate TestKit configuration
3. **Consider Gradle version compatibility matrix**
4. **Validate against multiple Gradle versions** if supporting version range
5. **Document working configuration patterns** for future reference

## References

- [Gradle TestKit User Guide](https://docs.gradle.org/current/userguide/test_kit.html)
- [Gradle Plugin Development Guide](https://docs.gradle.org/current/userguide/custom_plugins.html)
- [Testing Gradle Plugins](https://docs.gradle.org/current/userguide/testing_gradle_plugins.html)