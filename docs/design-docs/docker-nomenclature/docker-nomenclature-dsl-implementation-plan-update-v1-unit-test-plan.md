# Docker Nomenclature DSL Implementation Unit Test Plan

**Date**: 2025-01-23
**Based on**: Implementation gaps analysis after completing plan update v1
**Purpose**: Address missing unit test coverage for newly implemented Docker nomenclature features

## **Executive Summary**

Following successful implementation of the Docker nomenclature DSL changes, analysis reveals **15% critical test coverage gaps** that need to be addressed to achieve the plan's 100% unit test requirement. All gaps are **low to medium complexity** and can be implemented using existing testing patterns.

**Key Finding**: While the implementation is functionally complete with 100% passing tests, several critical new features lack comprehensive unit test validation, particularly the new sourceRef exclusivity validation and plugin property configuration.

## **📊 COVERAGE GAP ANALYSIS**

### **Current Test Coverage Status**
- **✅ 85% Complete**: Core functionality, Provider API, basic property validation
- **🔴 15% Critical Gaps**: New validation logic, plugin configuration, integration scenarios
- **Overall Complexity**: Low to Medium (standard Spock patterns)

### **Test Categories by Priority**
1. **HIGH PRIORITY**: Critical functionality gaps (must implement)
2. **MEDIUM PRIORITY**: Enhanced coverage for robustness
3. **LOW PRIORITY**: Edge cases and advanced scenarios

## **🔴 HIGH PRIORITY - CRITICAL COVERAGE GAPS**

### **1. SourceRef Exclusivity Validation Tests**

**Location**: Add to existing `DockerExtensionTest` or `DockerExtensionComprehensiveTest`
**Complexity**: **Low** (standard validation testing)
**Implementation Effort**: **1-2 hours**

#### **Test Cases Required**:

```groovy
// Test Case 1: Basic sourceRef + build property conflict
def "validateImageSpec rejects sourceRef with buildArgs"() {
    given:
    def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
    imageSpec.sourceRef.set("existing:image")
    imageSpec.buildArgs.put("VERSION", "1.0")

    when:
    extension.validateImageSpec(imageSpec)

    then:
    def ex = thrown(GradleException)
    ex.message.contains("cannot use both") && ex.message.contains("sourceRef")
}

// Test Case 2: sourceRef + context directory conflict
def "validateImageSpec rejects sourceRef with explicit context"()

// Test Case 3: sourceRef + contextTask conflict
def "validateImageSpec rejects sourceRef with contextTask"()

// Test Case 4: sourceRef + labels conflict
def "validateImageSpec rejects sourceRef with labels"()

// Test Case 5: sourceRef + dockerfile conflict
def "validateImageSpec rejects sourceRef with dockerfile"()

// Test Case 6: sourceRef + dockerfileName conflict
def "validateImageSpec rejects sourceRef with dockerfileName"()

// Test Case 7: Multiple build properties + sourceRef
def "validateImageSpec rejects sourceRef with multiple build properties"()

// Test Case 8: Valid sourceRef-only configuration
def "validateImageSpec allows sourceRef without build properties"()
```

#### **Error Message Validation**:
- Verify specific error message: "cannot use both 'sourceRef' and build-related properties"
- Verify architectural explanation included
- Verify suggestion to use one mode or the other

### **2. Plugin Registration Configuration Tests**

**Location**: Add to existing `GradleDockerPluginTest`
**Complexity**: **Medium** (requires project setup and task verification)
**Implementation Effort**: **3-4 hours**

#### **Test Cases Required**:

```groovy
// Test Case 1: DockerTagTask sourceRef configuration
def "plugin configures sourceRef property for DockerTagTask"() {
    given:
    project.apply plugin: GradleDockerPlugin

    when:
    project.docker {
        images {
            testImage {
                sourceRef.set("existing:image")
                tags.set(["new:tag"])
            }
        }
    }
    project.evaluate()

    then:
    def tagTask = project.tasks.getByName("dockerTagTestImage")
    tagTask.sourceRef.get() == "existing:image"
}

// Test Case 2: DockerSaveTask sourceRef configuration
def "plugin configures sourceRef property for DockerSaveTask"()

// Test Case 3: DockerSaveTask pullIfMissing configuration
def "plugin configures pullIfMissing from SaveSpec"()

// Test Case 4: DockerSaveTask auth configuration
def "plugin configures auth from SaveSpec"()

// Test Case 5: Property flow validation
def "plugin preserves property values through configuration chain"()

// Test Case 6: DockerBuildTask excludes sourceRef
def "plugin does NOT configure sourceRef for DockerBuildTask"()
```

#### **Integration Verification**:
- Verify property flow: `ImageSpec` → `SaveSpec` → `DockerSaveTask`
- Verify property isolation: `sourceRef` not configured for `DockerBuildTask`
- Verify lazy evaluation: Properties resolved correctly at task execution time

### **3. SaveSpec Authentication Integration Tests**

**Location**: Add to existing `SaveSpecTest` or create `SaveSpecIntegrationTest`
**Complexity**: **Low** (standard property testing)
**Implementation Effort**: **1-2 hours**

#### **Test Cases Required**:

```groovy
// Test Case 1: Auth property configuration
def "SaveSpec auth property can be configured via DSL"() {
    given:
    def saveSpec = objectFactory.newInstance(SaveSpec, objectFactory, layout)

    when:
    saveSpec.auth {
        username.set("testuser")
        password.set("testpass")
        registryToken.set("token123")
    }

    then:
    saveSpec.auth.isPresent()
    saveSpec.auth.get().username.get() == "testuser"
    saveSpec.auth.get().password.get() == "testpass"
    saveSpec.auth.get().registryToken.get() == "token123"
}

// Test Case 2: Auth property default values
def "SaveSpec auth property has correct defaults"()

// Test Case 3: Auth property validation
def "SaveSpec auth property validates required fields"()

// Test Case 4: Auth toAuthConfig conversion
def "SaveSpec auth converts to AuthConfig correctly"()
```

## **🟡 MEDIUM PRIORITY - ENHANCED COVERAGE**

### **4. Dual-Mode Task Behavior Enhancement**

**Location**: Enhance existing task test classes
**Complexity**: **Medium** (mock services, state verification)
**Implementation Effort**: **4-6 hours**

#### **DockerTagTask Enhancements**:
```groovy
// Test Case 1: SourceRef mode image reference building
def "DockerTagTask builds references correctly in SourceRef mode"()

// Test Case 2: Build mode image reference building
def "DockerTagTask builds references correctly in Build mode"()

// Test Case 3: Mode detection validation
def "DockerTagTask validates mode requirements correctly"()

// Test Case 4: Edge case handling
def "DockerTagTask handles empty tag lists appropriately"()
```

#### **DockerSaveTask Enhancements**:
```groovy
// Test Case 1: pullIfMissing with authentication
def "DockerSaveTask pulls missing image with auth in SourceRef mode"()

// Test Case 2: Dual-mode primary reference building
def "DockerSaveTask builds primary image reference in both modes"()

// Test Case 3: Auth configuration usage
def "DockerSaveTask uses auth configuration for pullIfMissing"()
```

#### **DockerPublishTask Enhancements**:
```groovy
// Test Case 1: SourceRef mode source reference building
def "DockerPublishTask uses sourceRef as source in SourceRef mode"()

// Test Case 2: Build mode source reference building
def "DockerPublishTask builds source from nomenclature in Build mode"()
```

### **5. SaveCompression Service Integration**

**Location**: Enhance task comprehensive test classes
**Complexity**: **Medium** (service mocking)
**Implementation Effort**: **2-3 hours**

#### **Test Cases Required**:
```groovy
// Test Case 1: Enum integration verification
def "DockerSaveTask passes SaveCompression enum to service correctly"() {
    given:
    task.compression.set(SaveCompression.GZIP)
    // ... setup task properties

    when:
    task.saveImage()

    then:
    1 * mockDockerService.saveImage(_, _, SaveCompression.GZIP)
}

// Test Case 2: All compression types verification
@Unroll
def "DockerSaveTask handles all SaveCompression types: #compressionType"()

// Test Case 3: Default compression behavior
def "DockerSaveTask uses NONE compression by default"()
```

## **🟢 LOW PRIORITY - EDGE CASES**

### **6. Provider API Edge Cases**

**Complexity**: **Medium to High**
**Implementation Effort**: **3-4 hours**

#### **Test Cases**:
- TestKit compatibility scenarios
- Lazy evaluation edge cases
- Property resolution timing
- Configuration cache serialization

### **7. Complex Validation Scenarios**

**Complexity**: **Medium**
**Implementation Effort**: **2-3 hours**

#### **Test Cases**:
- Multiple simultaneous validation failures
- Nested property validation
- Provider exception handling

## **📋 IMPLEMENTATION PLAN**

### **Phase 1: Critical Gaps (Must Complete) - Est. 6-8 hours**

**Week 1 Priority**:
1. **SourceRef Exclusivity Validation** (1-2 hours)
   - Add 8 test cases to `DockerExtensionComprehensiveTest`
   - Focus on validation logic and error messages

2. **Plugin Registration Configuration** (3-4 hours)
   - Add 6 test cases to `GradleDockerPluginTest`
   - Focus on property configuration flow

3. **SaveSpec Authentication Integration** (1-2 hours)
   - Add 4 test cases to `SaveSpecTest`
   - Focus on auth property behavior

### **Phase 2: Enhanced Coverage (Recommended) - Est. 8-10 hours**

**Week 2 Priority**:
4. **Dual-Mode Task Behavior** (4-6 hours)
   - Enhance existing task test classes
   - Add comprehensive mode validation

5. **SaveCompression Service Integration** (2-3 hours)
   - Enhance task comprehensive test classes
   - Add service interaction validation

### **Phase 3: Edge Cases (Optional) - Est. 5-7 hours**

**Future Priority**:
6. **Provider API Edge Cases** (3-4 hours)
7. **Complex Validation Scenarios** (2-3 hours)

## **🎯 SUCCESS CRITERIA**

### **Minimum Acceptable Coverage (Phase 1)**
- ✅ **SourceRef exclusivity validation**: 100% covered
- ✅ **Plugin property configuration**: 100% covered
- ✅ **SaveSpec authentication**: 100% covered
- ✅ **All critical paths tested**: No functionality gaps
- ✅ **100% unit test pass rate**: No failing tests

### **Optimal Coverage (Phase 1 + 2)**
- ✅ **Dual-mode task behavior**: Comprehensive coverage
- ✅ **Service integration**: All enum values tested
- ✅ **Edge case handling**: Robust validation
- ✅ **Error scenarios**: Complete failure path coverage

## **📊 TESTING STRATEGIES**

### **Validation Testing Pattern**
```groovy
def "validation method rejects invalid configuration"() {
    given: "Invalid configuration setup"
    when: "Validation method called"
    then: "Specific exception thrown with correct message"
}
```

### **Property Configuration Pattern**
```groovy
def "plugin configures property correctly"() {
    given: "Project with plugin applied"
    when: "DSL configuration executed"
    then: "Task property has expected value"
}
```

### **Service Integration Pattern**
```groovy
def "task calls service with correct parameters"() {
    given: "Task with configured properties"
    when: "Task action executed"
    then: "Service method called with expected parameters"
}
```

## **🔧 IMPLEMENTATION GUIDELINES**

### **Test Organization**
- **Add to existing test classes** where logical grouping exists
- **Create new test classes** only for completely new functionality
- **Use comprehensive test classes** for complex scenarios
- **Follow existing naming conventions**: `*Test.groovy` vs `*ComprehensiveTest.groovy`

### **Mock Usage**
- **Minimal mocking**: Use only when necessary for service calls
- **Standard patterns**: Follow existing mock patterns in codebase
- **Verify interactions**: Use Spock verification syntax consistently
- **Service mocking**: Mock only boundary interfaces, not internal logic

### **Property Testing**
- **Provider API**: Use `.set()` and `.get()` pattern consistently
- **Convention testing**: Verify default values are set correctly
- **Lazy evaluation**: Test that providers resolve at correct time
- **Validation**: Test both valid and invalid property combinations

### **Error Testing**
- **Exception types**: Verify correct exception types thrown
- **Error messages**: Validate error message content and helpfulness
- **Suggestion text**: Ensure error messages include actionable suggestions
- **Multiple errors**: Test that first error is caught and reported clearly

## **🏆 EXPECTED OUTCOMES**

### **Immediate Benefits**
- **100% unit test coverage** for new Docker nomenclature features
- **Validated implementation**: All critical paths tested and verified
- **Regression protection**: Future changes caught by comprehensive tests
- **Documentation**: Tests serve as executable specification

### **Long-term Value**
- **Maintainability**: Clear test coverage for complex validation logic
- **Confidence**: High assurance in plugin behavior across all scenarios
- **Developer productivity**: Fast feedback on changes via comprehensive test suite
- **Quality assurance**: Robust testing foundation for future enhancements

## **📝 DELIVERABLES**

### **Test Files to Modify/Create**
1. `DockerExtensionComprehensiveTest.groovy` - Add sourceRef exclusivity validation tests
2. `GradleDockerPluginTest.groovy` - Add plugin configuration tests
3. `SaveSpecTest.groovy` - Add authentication integration tests
4. Various task test classes - Add dual-mode behavior tests
5. Task comprehensive test classes - Add service integration tests

### **Coverage Reports**
- **JaCoCo reports**: Verify 100% line and branch coverage for new code
- **Gap documentation**: Update any remaining gaps in `docs/design-docs/testing/unit-test-gaps.md`
- **Test execution verification**: Confirm all tests pass with new coverage

### **Documentation Updates**
- **Test plan completion**: Mark phases as completed
- **Coverage summary**: Update overall project test coverage metrics
- **Gap resolution**: Document resolution of identified coverage gaps

This unit test plan ensures comprehensive coverage of all newly implemented Docker nomenclature features while maintaining the existing high-quality testing standards of the project.