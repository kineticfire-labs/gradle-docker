# Docker Nomenclature DSL Implementation Fix Plan 1

## Executive Summary

This plan addresses the 12 failing unit tests caused by legacy design expectations that conflict with the current functional test implementation. The goal is to remove legacy unit tests that expect full image references in `publishTarget.tags`, add comprehensive unit tests for the current `publishTags.set(['latest', 'v1.0.0'])` API, and achieve 100% unit test success while maintaining functional test success and plugin build capability.

## Background

The unit tests were written to an older design specification where publish targets were expected to contain **full image references** (e.g., `'registry.company.com/team/myapp:v1.0.0'`), but the functional tests and current implementation expect **simple tag names** (e.g., `'latest'`, `'v1.0.0'`). The functional tests achieve 100% success (47/47) proving the current design works correctly, while 12 unit tests fail due to testing deprecated legacy behavior.

## Validation Logic Issue

The validation logic in `DockerExtension.groovy` may be mismatched with the current API expectations:

**Current Problem**:
- **Functional tests expect**: Simple tag names like `['latest', 'v1.0.0']` 
- **Current validation may be using**: `isValidImageReference()` which expects full references like `'registry.company.com/team/myapp:v1.0.0'`
- **Should be using**: `isValidTagFormat()` or similar method that validates simple tag names

**Example of the issue**:
```groovy
// Current validation (WRONG for simple tags)
if (!isValidImageReference(tag)) {  // Expects 'registry.com/name:tag'
    throw new GradleException("Invalid image reference: ${tag}")
}

// Should be (CORRECT for simple tags)  
if (!isValidTagFormat(tag)) {  // Expects just 'tag' or 'v1.0.0'
    throw new GradleException("Invalid tag format: ${tag}")
}
```

This affects **both unit tests and functional tests** because the validation logic is shared code used by both.

## Goals

1. **Remove legacy unit tests** that expect full image references in `publishTarget.tags`
2. **Get unit tests to pass** (achieve 100% success rate)
3. **Ensure functional tests still pass** (maintain 47/47 success)
4. **Ensure the plugin still builds** successfully

## Implementation Plan

### **Phase 1: Investigation and Validation Logic Fix**

#### **Step 1.1: Examine Current Validation Logic**
- **Target**: `DockerExtension.groovy` - `validatePublishTarget()` method
- **Investigation**:
  - Find the current validation method used for `publishTarget.publishTags`
  - Determine if it's using `isValidImageReference()` (wrong) or `isValidTagFormat()` (correct)
  - Identify the specific validation code path that processes publishTags

#### **Step 1.2: Fix Validation Logic If Needed**
- **If using `isValidImageReference()`**: Change to `isValidTagFormat()` or equivalent
- **Ensure validation expects**: Simple tag names like `'latest'`, `'v1.0.0'`, not full references
- **Preserve validation requirements**: Tags must still be non-empty and properly formatted
- **Result**: This fix will make functional tests continue passing and prepare for unit test updates

### **Phase 2: Remove Legacy Unit Tests (HIGH PRIORITY)**

#### **Step 2.1: Remove Failing Tests from DockerExtensionTest.groovy**
Remove these specific failing unit tests:
- `validatePublishTarget accepts valid full image references` (lines 328-346)
  - **Reason**: Tests deprecated behavior expecting full image references to pass
- `validatePublishTarget fails when target has invalid image references` (lines 291-309)
  - **Reason**: Tests old validation logic for full image references
- `validation works with full image references in publish targets` (lines 367+)
  - **Reason**: Tests deprecated mixed configuration approach

#### **Step 2.2: Remove Failing Tests from DockerExtensionComprehensiveTest.groovy**
Remove these specific failing unit tests:
- `validatePublishTarget validates tag image references` (parameterized test, lines 469+)
  - **Reason**: Tests old validation expecting image reference format
- `validatePublishTarget accepts valid image references`
  - **Reason**: Tests deprecated behavior with full image references

#### **Step 2.3: Remove Any Other Legacy Tests**
Remove any other unit tests that:
- Use `publishTarget.tags.set([...])` with full image references
- Test validation expecting full image references to pass
- Test validation expecting simple tag names to fail as "invalid image references"

### **Phase 3: Add New Unit Tests for Current API (HIGH PRIORITY)**

#### **Step 3.1: Add Core Validation Tests to DockerExtensionTest.groovy**

**Test: Simple Tag Names Validation**
```groovy
def "validatePublishTarget accepts simple tag names"() {
    given: "A publish target with simple tag names"
    def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
    publishTarget.publishTags.set(['latest', 'v1.0.0', 'stable'])
    
    when: "validation is performed"
    extension.validatePublishTarget(publishTarget, 'testImage')
    
    then: "validation passes without exception"
    noExceptionThrown()
}
```

**Test: Missing PublishTags Validation**
```groovy
def "validatePublishTarget fails when publishTags are not specified"() {
    given: "A publish target with no publishTags"
    def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
    // Don't set publishTags
    
    when: "validation is performed"
    extension.validatePublishTarget(publishTarget, 'testImage')
    
    then: "validation fails with descriptive error"
    def ex = thrown(GradleException)
    ex.message.contains("must specify at least one tag")
    ex.message.contains('testTarget')
    ex.message.contains('testImage')
}
```

**Test: Invalid Tag Format Validation**
```groovy
def "validatePublishTarget fails with invalid simple tag format"() {
    given: "A publish target with invalid tag formats"
    def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
    publishTarget.publishTags.set(['invalid::tag', 'bad tag name'])
    
    when: "validation is performed"
    extension.validatePublishTarget(publishTarget, 'testImage')
    
    then: "validation fails with descriptive error"
    def ex = thrown(GradleException)
    ex.message.contains("Invalid tag format")
    ex.message.contains('testTarget')
    ex.message.contains('testImage')
}
```

#### **Step 3.2: Add Comprehensive Validation Tests to DockerExtensionComprehensiveTest.groovy**

**Test: Parameterized Tag Format Validation**
```groovy
@Unroll
def "validatePublishTarget validates simple tag format [tag: #tag, valid: #shouldPass]"() {
    given: "A publish target with test tag"
    def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
    publishTarget.publishTags.set([tag])
    
    when: "validation is performed"
    extension.validatePublishTarget(publishTarget, 'testImage')
    
    then: "validation result matches expectation"
    if (shouldPass) {
        noExceptionThrown()
    } else {
        def ex = thrown(GradleException)
        ex.message.contains("Invalid tag format") || ex.message.contains("must specify at least one tag")
    }
    
    where:
    tag           | shouldPass
    'latest'      | true
    'v1.0.0'      | true
    'stable'      | true
    'rc-1'        | true
    'main'        | true
    'test'        | true
    'invalid::'   | false
    'bad tag'     | false
    ''            | false
}
```

**Test: Edge Cases**
```groovy
def "validatePublishTarget handles edge cases correctly"() {
    given: "A publish target with edge case tags"
    def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
    publishTarget.publishTags.set(['v1.0.0-alpha', 'latest-dev', 'sha-abc123'])
    
    when: "validation is performed"
    extension.validatePublishTarget(publishTarget, 'testImage')
    
    then: "validation passes for valid tag patterns"
    noExceptionThrown()
}
```

#### **Step 3.3: Add Integration-Style Unit Tests**

**Test: Complete Configuration Validation**
```groovy
def "validate accepts image configuration with publish targets using simple tag names"() {
    given: "Complete image configuration matching functional test patterns"
    project.file('docker').mkdirs()
    project.file('docker/Dockerfile').text = 'FROM alpine'
    
    extension.images {
        testImage {
            context.set(project.file('docker'))
            dockerfile.set(project.file('docker/Dockerfile'))
            imageName.set('test-app')
            tags.set(['latest', 'v1.0.0'])
            publish {
                to('dockerhub') {
                    registry.set('docker.io')
                    namespace.set('mycompany')
                    publishTags.set(['latest', 'v1.0.0'])  // Simple tag names
                }
                to('internal') {
                    registry.set('localhost:5000')
                    namespace.set('internal')
                    publishTags.set(['internal-latest', 'test'])  // Simple tag names
                }
            }
        }
    }
    
    when: "validation is performed"
    extension.validate()
    
    then: "validation passes without exception"
    noExceptionThrown()
}
```

### **Phase 4: Integration Test Updates**

#### **Step 4.1: Verify Integration Test Patterns**
- **Check**: Any integration tests using `publishTarget.tags.set([...])` with full image references
- **Update**: Change to use `publishTarget.publishTags.set([...])` with simple tag names
- **Expected**: This should be minimal since functional tests already use the correct pattern

#### **Step 4.2: Validate Test Configuration Consistency**
- **Ensure**: All test configurations use the same pattern as functional tests
- **Verify**: Registry/namespace separation from tag names is consistent
- **Check**: No remaining full image reference usage in test configurations

### **Phase 5: Verification and Testing**

#### **Step 5.1: Unit Test Verification**
```bash
cd plugin
./gradlew clean test
```
- **Expected**: 100% unit test pass rate
- **Verify**: All new tests pass
- **Confirm**: No legacy test failures remain

#### **Step 5.2: Functional Test Verification**
```bash
cd plugin
./gradlew clean functionalTest
```
- **Expected**: 47/47 functional tests passing (100% - unchanged)
- **Verify**: No regressions from validation logic changes

#### **Step 5.3: Plugin Build Verification**
```bash
cd plugin
./gradlew clean build
```
- **Expected**: Plugin builds successfully without errors
- **Verify**: All compilation, test, and packaging steps complete

#### **Step 5.4: Integration Test Verification**
```bash
cd plugin
./gradlew clean build publishToMavenLocal
cd ../plugin-integration-test
./gradlew clean testAll integrationTestComprehensive
```
- **Expected**: All integration tests pass
- **Verify**: No regressions from API changes

## Expected Outcomes

### **Before Implementation**
- **Unit tests**: 1473/1485 passing (99.2% - 12 legacy tests failing)
- **Functional tests**: 47/47 passing (100%)
- **Plugin builds**: Successfully
- **Test coverage**: Legacy API fully covered, current API not covered in unit tests

### **After Implementation**
- **Unit tests**: 100% passing (legacy tests removed, new current API tests added)
- **Functional tests**: 47/47 passing (100% - unchanged)
- **Plugin builds**: Successfully
- **Test coverage**: Current API fully covered in both unit and functional tests

## Key Benefits

1. **Eliminates all legacy API support** as requested - no backwards compatibility burden
2. **Achieves 100% unit test success** as required by project standards
3. **Maintains functional test success** - no regressions to proven working functionality
4. **Provides comprehensive test coverage** of the actual current API used by functional tests
5. **Aligns all tests with implementation plan v3** - consistent with architectural direction
6. **Removes design inconsistency** - all tests now expect the same API behavior
7. **Simplifies maintenance** - no need to maintain tests for deprecated functionality

## Success Criteria

This implementation is considered successful when:

1. **All unit tests pass (100%)** - no failing tests due to legacy expectations
2. **All functional tests pass (100%)** - maintained 47/47 success rate
3. **Plugin builds successfully** - no compilation or packaging errors
4. **All integration tests pass** - no regressions in end-to-end functionality
5. **Complete current API coverage** - unit tests thoroughly cover `publishTags.set(['latest', 'v1.0.0'])` patterns
6. **No legacy API references** - all tests use the current supported API design
7. **Validation logic consistency** - same validation behavior expected by both unit and functional tests

## Final Notes

- **No backwards compatibility needed** - project hasn't been released
- **Focus on current proven API** - functional tests demonstrate the working design
- **Eliminate legacy confusion** - remove all traces of deprecated full image reference approach
- **Maintain test quality** - comprehensive coverage of actual supported functionality
- **Align with implementation plan** - consistent with architectural direction in plan v3