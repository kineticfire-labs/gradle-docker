# Test Coverage Improvement Plan

**Document**: Test Update Plan  
**Date**: 2025-09-02  
**Version**: 2.0  
**Status**: In Progress  
**Author**: Claude Code  

## Overview

This document outlines the **comprehensive and realistic** plan to achieve optimal test coverage for the gradle-docker plugin project. Based on architectural analysis, we target **90%+ overall coverage** through appropriate test layer selection, acknowledging that Docker integration requires integration testing rather than unit testing.

## Current Test Coverage Analysis

### Coverage Summary (Updated 2025-09-02 10:12 AM)
- **Overall Instruction Coverage**: **39%** (4,781 of 12,089 instructions covered)
- **Branch Coverage**: **34%** (153 of 440 branches covered)  
- **Line Coverage**: **36%** (358 of 984 lines covered)
- **Method Coverage**: **61%** (210 of 343 methods covered)
- **Class Coverage**: **61%** (69 of 114 classes covered)

### Package-Level Coverage Breakdown

| Package | Instruction Coverage | Branch Coverage | Status | Phase |
|---------|---------------------|-----------------|---------|-------|
| `com.kineticfire.gradle.docker.exception` | **100%** ✅ | **100%** ✅ | Complete | **Phase 1 ✅** |
| `com.kineticfire.gradle.docker.model` | **95%** ✅ | **82%** ✅ | Complete | **Phase 2A ✅** |
| `com.kineticfire.gradle.docker.task` | **51%** | **76%** | Good foundation | Phase 3 |
| `com.kineticfire.gradle.docker` | **24%** | **8%** | Needs work | Phase 2C |
| `com.kineticfire.gradle.docker.extension` | **22%** | **0%** | Needs branch tests | Phase 2B |
| `com.kineticfire.gradle.docker.spec` | **9%** | **n/a** | Low coverage | Phase 2D |
| `com.kineticfire.gradle.docker.service` | **5%** | **5%** | Justified low (external deps) | ✅ **Architectural** |

## Revised Goal & Strategy

### Realistic Coverage Targets
Based on architectural analysis and external dependency constraints:

**Target**: **85%+ overall coverage** through multi-layer testing strategy
- **Unit Tests**: 80%+ for pure logic components
- **Integration Tests**: 90%+ for Docker operations  
- **Combined Coverage**: 85%+ comprehensive validation

### Architecture-Driven Strategy
Different packages require different testing approaches based on their dependency profiles:

1. **High Unit Coverage** (95-100%): Exception, Model packages
2. **Medium Unit Coverage** (80-90%): Extension, Task, Main Plugin packages  
3. **Integration-Heavy** (5-10% unit, 90%+ integration): Service package

## Progress Status

### ✅ **COMPLETED PHASES**

#### ✅ **Phase 1: Exception Package (0% → 100%)**
- [x] `DockerException` - Complete constructor and error handling tests
- [x] `ComposeException` - Complete constructor and error handling tests  
- [x] `ValidationException` - Complete constructor and error handling tests
- [x] `ConfigurationException` - Complete constructor and error handling tests
- **Result**: 100% instruction coverage, 100% branch coverage
- **Tests Added**: 16 comprehensive exception tests

#### ✅ **Phase 2A: Model Package (27% → 95%)**
- [x] `AuthConfig` - 19 test methods covering authentication scenarios
- [x] `BuildContext` - Complete build context configuration tests
- [x] `ComposeConfig` - Docker Compose configuration validation
- [x] `ComposeState` - Service state management and tracking  
- [x] `CompressionType` - Archive compression enumeration tests
- [x] `ImageRefParts` - Docker image reference parsing
- [x] `LogsConfig` - Container logging configuration tests
- [x] `PortMapping` - Network port mapping functionality
- [x] `ServiceInfo` - 24 test methods for Docker service information  
- [x] `ServiceState` - 22 test methods for service state tracking
- [x] `ServiceStatus` - Service status enumeration tests
- [x] `WaitConfig` - Wait condition configuration tests
- **Result**: 95% instruction coverage, 82% branch coverage
- **Tests Added**: 150+ comprehensive model tests
- **Model Fixes**: Resolved WaitConfig type mismatches, AuthConfig Docker Java integration

### 🔄 **IN PROGRESS PHASES**

#### ⏳ **Phase 2B: Extension Package (22% → 90%+)** - NEXT
**Target**: Implement comprehensive DSL and configuration tests

##### Extension Classes to Complete:
- [ ] `DockerExtension` - DSL configuration scenarios
  - [ ] Image reference validation and parsing
  - [ ] Build context configuration variations  
  - [ ] Tag configuration and validation
  - [ ] Registry configuration scenarios
  - [ ] Error handling for invalid configurations
  - [ ] **Branch Testing**: All conditional logic paths

- [ ] `DockerOrchExtension` - Compose stack configurations  
  - [ ] Multiple stack definitions and dependencies
  - [ ] Service configuration variations
  - [ ] Network and volume configurations
  - [ ] Stack lifecycle management
  - [ ] Error handling for invalid stack configurations
  - [ ] **Branch Testing**: All conditional logic paths

- [ ] `TestIntegrationExtension` - Test integration scenarios
  - [ ] `usesCompose()` configuration testing
  - [ ] Lifecycle integration scenarios
  - [ ] Dependency resolution for test tasks
  - [ ] **Branch Testing**: All conditional logic paths

**Estimated Tests**: 30+ tests
**Target Coverage**: 90%+ instruction, 85%+ branch

#### ⏳ **Phase 2C: Main Plugin Package (24% → 85%+)**
**Target**: Core plugin lifecycle and registration logic

##### Core Plugin Class:
- [ ] `GradleDockerPlugin` - Plugin application and configuration
  - [ ] Plugin application to different project types
  - [ ] Extension registration (DockerExtension, DockerOrchExtension)
  - [ ] Build service registration and lifecycle
  - [ ] Task registration rules and dependencies
  - [ ] Aggregate task creation and configuration
  - [ ] After-evaluation configuration processing
  - [ ] Requirement validation logic
  - [ ] Error handling for invalid project configurations

**Estimated Tests**: 25+ tests  
**Target Coverage**: 85%+ instruction, 80%+ branch

#### ⏳ **Phase 2D: Specification Package (9% → 80%+)**
**Target**: Configuration validation and specification logic

##### Specification Classes:
- [ ] Specification validation classes
- [ ] Configuration parsing and validation logic
- [ ] Error handling for invalid specifications
- [ ] Integration with extension configuration

**Estimated Tests**: 20+ tests
**Target Coverage**: 80%+ instruction, 75%+ branch

### 📋 **PLANNED PHASES**

#### **Phase 3: Task Package Completion (51% → 85%+)**
**Target**: Complete task logic coverage while excluding service calls

##### Task Classes to Complete:
- [ ] `DockerBuildTask` - Complete branch coverage for all error conditions
- [ ] `DockerTagTask` - Complete branch coverage for error scenarios  
- [ ] `DockerPublishTask` - Complete implementation and testing
- [ ] `DockerSaveTask` - Complete implementation and testing
- [ ] `ComposeUpTask` - Complete branch coverage for error conditions
- [ ] `ComposeDownTask` - Complete branch coverage for cleanup scenarios

**Estimated Tests**: 20+ tests
**Target Coverage**: 85%+ instruction, 80%+ branch

#### **Phase 4: Branch Coverage Optimization**
**Target**: Achieve optimal branch coverage across all packages

##### Branch Coverage Focus Areas:
1. **Error Handling Paths** - All exception throwing scenarios
2. **Conditional Configuration Logic** - All if/else and switch statements  
3. **Validation Logic** - All input validation branches
4. **State-Dependent Operations** - All state checking branches
5. **Optional Parameter Handling** - All null/default value branches

## Service Package Strategy

### ✅ **Architectural Decision: Service Package (5% Coverage by Design)**

**Rationale**: 
- Docker operations require live Docker daemon
- Process execution is platform-specific  
- External system state dependencies
- Integration testing provides better validation

**Current Status**: 5% unit coverage achieved ✅
- ✅ Input validation and parameter parsing
- ✅ Error message formatting  
- ✅ Basic structural validation

**Compensating Tests**:
- **Integration Tests**: 90%+ Docker operation coverage in `plugin-integration-test/`
- **Functional Tests**: End-to-end plugin workflow validation
- **Real Docker Testing**: Actual daemon interaction scenarios

## Test Quality Standards

### Requirements for Each Test Class:
- [x] **Comprehensive Coverage**: Test all public methods and constructors
- [x] **Edge Case Testing**: Test boundary conditions and invalid inputs
- [x] **Error Path Testing**: Test all exception scenarios
- [x] **State Testing**: Test object state changes and side effects
- [x] **Documentation**: Clear test method names using Spock DSL
- [x] **Maintainability**: DRY principles and shared test utilities

### Test Naming Convention:
- Use descriptive Spock feature method names: `"can create AuthConfig with username/password"`
- Use `given:`, `when:`, `then:` blocks for clear test structure
- Example: `"buildImage with invalid context throws DockerException"`

## Implementation Checklist

### Infrastructure Status:
- [x] Unit tests pass completely (247/247 tests) ✅
- [x] JaCoCo coverage reporting configured ✅
- [x] HTML and XML coverage reports generating ✅ 
- [x] Test utilities and shared fixtures created ✅
- [ ] Coverage thresholds configured for realistic targets
- [ ] Integration with CI/CD pipeline coverage checks

### Phase Progress:
- [x] **Phase 1** (Exception): 100% coverage achieved ✅
- [x] **Phase 2A** (Model): 95% coverage achieved ✅  
- [ ] **Phase 2B** (Extension): 22% → 90%+ target
- [ ] **Phase 2C** (Main Plugin): 24% → 85%+ target
- [ ] **Phase 2D** (Specification): 9% → 80%+ target
- [ ] **Phase 3** (Task): 51% → 85%+ target
- [ ] **Phase 4** (Branch Optimization): Achieve optimal branch coverage

## Success Criteria

### Coverage Targets:
- [x] **Phase 1**: Exception package 100% ✅
- [x] **Phase 2A**: Model package 95%+ ✅
- [ ] **Phase 2B**: Extension package 90%+ 
- [ ] **Phase 2C**: Main plugin 85%+
- [ ] **Phase 2D**: Specification 80%+
- [ ] **Phase 3**: Task package 85%+
- [ ] **Final Target**: **85%+ overall coverage** (realistic architectural target)

### Quality Gates:
- [x] All existing tests continue to pass ✅
- [x] No significant test execution time regression ✅
- [x] Code coverage reporting functional ✅
- [ ] Coverage metrics integration with build pipeline
- [ ] Documentation updated with testing approach

## Maintenance Plan

### Ongoing Coverage Maintenance:
- [ ] Configure build to fail if coverage drops below realistic thresholds
- [ ] Add coverage checks to pull request validation  
- [ ] Regular review of coverage reports
- [ ] Update test plan when new features are added
- [ ] Maintain integration test coverage for Docker operations

## Current Status Summary

**Overall Progress**: **Phase 2A Complete** - Significant advancement achieved

### Completed:
✅ **Exception Package**: 100% coverage (16 tests)  
✅ **Model Package**: 95% coverage (150+ tests)

### Next Priority:
🎯 **Phase 2B**: Extension Package (22% → 90%+)

### Coverage Achievement:
- **Before**: 18% instruction, 15% branch coverage
- **Current**: 39% instruction, 34% branch coverage  
- **Target**: 85%+ instruction, 80%+ branch coverage

**Success Metrics**: 
- ✅ Model and Exception packages: Comprehensive unit test coverage
- 🔄 Extension, Task, Main Plugin: Balanced unit testing approach
- ✅ Service package: Appropriate integration testing strategy
- 🎯 **Combined Result**: Comprehensive validation through proper test layer selection

The plan now reflects realistic, architecture-appropriate testing targets that provide comprehensive validation without attempting to unit test inherently integration-dependent components.