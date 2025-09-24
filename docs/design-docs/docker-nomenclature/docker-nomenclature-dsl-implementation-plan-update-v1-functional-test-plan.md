# Docker Nomenclature DSL Functional Test Plan

**Date**: 2025-01-23  
**Based on**: Analysis of current functional test coverage vs implemented Docker nomenclature DSL features  
**Status**: Comprehensive functional test gap analysis and implementation plan

## **Executive Summary**

After comprehensive analysis of the functional test suite against the implemented Docker nomenclature DSL features, the current functional tests provide **good foundational coverage** but have **critical gaps** in testing advanced scenarios and newly implemented features.

**Key Finding**: While basic nomenclature validation and save compression features are well-covered, the **labels feature** and **advanced dual-mode scenarios** lack functional test coverage entirely.

## **📊 CURRENT FUNCTIONAL TEST COVERAGE STATUS**

### **✅ WELL COVERED AREAS**

#### **1. Docker Nomenclature Configuration (DockerNomenclatureFunctionalTest.groovy)**
- ✅ Build Mode validation (repository vs namespace+imageName mutual exclusivity)
- ✅ SourceRef Mode validation (exclusive with build properties)  
- ✅ Provider API support for lazy evaluation
- ✅ Mutual exclusivity validation rules
- ✅ Valid configurations for both Build and SourceRef modes
- ✅ Provider-based nomenclature configuration with gradle properties

#### **2. Save Compression Features (DockerSaveFunctionalTest.groovy)**
- ✅ All 5 SaveCompression types (NONE, GZIP, ZIP, BZIP2, XZ)
- ✅ File extension inference and matching (.tar, .tar.gz, .tar.bz2, .tar.xz, .zip)
- ✅ Provider-based configuration for compression settings
- ✅ SourceRef mode with pullIfMissing functionality
- ✅ Authentication configuration for private registry pulls
- ✅ Lazy evaluation of output file paths and compression settings

#### **3. Docker Publish Operations (DockerPublishFunctionalTest.groovy)**
- ✅ Multiple publish targets configuration
- ✅ Authentication for different registry types (username/password, token)
- ✅ PublishTarget configurations with registry, namespace, publishTags
- ✅ Cross-registry publishing scenarios

#### **4. Tag Operations (DockerTagFunctionalTest.groovy)**
- ✅ Build mode with nomenclature properties (registry, namespace, imageName)
- ✅ Basic task configuration verification
- ✅ Provider API integration for tag properties

---

## **🔴 CRITICAL FUNCTIONAL TEST GAPS**

### **Gap 1: Labels Feature Coverage - COMPLETELY MISSING**

**Issue**: The new `labels` feature (MapProperty<String,String>) has **NO functional test coverage**

**Impact**: OCI image labels functionality is completely untested at functional level

**Missing Coverage**:
- Labels configuration in Build Mode
- Multiple label types (OCI standard labels, custom labels)
- Provider-based label values with lazy evaluation
- Label validation and property resolution
- Labels integration with DockerBuildTask

**Required Test Scenarios**:
```groovy
// MISSING: Basic labels configuration
docker {
    images {
        labeledApp {
            imageName.set('test-app')
            context.set(file('.'))
            
            labels.put('org.opencontainers.image.version', '1.0.0')
            labels.put('org.opencontainers.image.vendor', 'KineticFire')
            labels.put('org.opencontainers.image.title', 'Test Application')
            labels.put('custom.environment', 'production')
        }
    }
}

// MISSING: Provider-based labels
docker {
    images {
        providerLabelsApp {
            imageName.set('provider-app')
            context.set(file('.'))
            
            labels.put('version', providers.provider { project.version.toString() })
            labels.put('build.time', providers.provider { new Date().toString() })
            labels.put('git.sha', providers.gradleProperty('git.sha').orElse('unknown'))
        }
    }
}
```

### **Gap 2: SourceRef Dual-Mode Coverage - INCOMPLETE**

**Issue**: While DockerSaveFunctionalTest covers sourceRef mode, other tasks lack comprehensive sourceRef coverage

**Missing Coverage**:
- **DockerTagTask** in SourceRef mode (retagging existing images)
- **DockerPublishTask** in SourceRef mode (republishing existing images)
- **Cross-task dual-mode validation** consistency
- **SourceRef + tags combination** scenarios

**Required Test Scenarios**:
```groovy
// MISSING: DockerTag with sourceRef (retagging existing images)
docker {
    images {
        retagExistingApp {
            sourceRef.set('ghcr.io/upstream/awesome-app:1.0.0')
            tags.set(['local:latest', 'local:stable', 'local:v1.0.0'])
        }
    }
}

// MISSING: DockerPublish with sourceRef (republishing existing images)
docker {
    images {
        republishApp {
            sourceRef.set('ghcr.io/upstream/awesome-app:1.0.0')
            
            publish {
                to('internal') {
                    registry.set('internal.company.com')
                    namespace.set('mirrors')
                    publishTags(['mirror-latest', 'mirror-v1.0.0'])
                    
                    auth {
                        username.set('internal-user')
                        password.set('internal-pass')
                    }
                }
            }
        }
    }
}
```

### **Gap 3: Authentication Integration - INCOMPLETE**

**Issue**: SaveSpec authentication is covered, but cross-component authentication scenarios are missing

**Missing Coverage**:
- Authentication with **various credential types** across different tasks
- Authentication **failure scenarios** and error handling
- **Cross-task authentication** consistency between save/publish operations
- **Multiple registry authentication** in single workflow

**Required Test Scenarios**:
```groovy
// MISSING: Cross-task authentication consistency
docker {
    images {
        authWorkflowApp {
            sourceRef.set('private.registry.com/app:1.0.0')
            
            save {
                compression.set(SaveCompression.GZIP)
                outputFile.set(layout.buildDirectory.file('images/app.tar.gz'))
                pullIfMissing.set(true)
                
                auth {
                    registryToken.set('pull-token-123')
                }
            }
            
            publish {
                to('target') {
                    registry.set('target.registry.com')
                    namespace.set('published')
                    publishTags(['republished'])
                    
                    auth {
                        username.set('push-user')
                        password.set('push-pass')
                    }
                }
            }
        }
    }
}
```

### **Gap 4: Complex Nomenclature Scenarios - MISSING**

**Issue**: Current tests cover basic scenarios but miss complex real-world configurations

**Missing Coverage**:
- **Multi-registry workflows** (build → save → republish to different registries)
- **Version handling** with complex version schemes and Provider resolution
- **Tag propagation** between build, save, and publish phases  
- **Repository vs namespace+imageName** edge cases and combinations
- **Registry hostname validation** and complex registry URLs

**Required Test Scenarios**:
```groovy
// MISSING: Complex multi-registry workflow
docker {
    images {
        complexWorkflowApp {
            // Build with comprehensive nomenclature
            registry.set('build.company.com:5000')
            namespace.set('engineering/applications')
            imageName.set('complex-app')
            version.set(providers.provider { "${project.version}-${buildNumber}" })
            tags.set(['latest', 'nightly', providers.provider { "build-${buildId}" }])
            
            context.set(file('.'))
            
            labels.put('org.opencontainers.image.version', version)
            labels.put('org.opencontainers.image.revision', gitSha)
            labels.put('build.environment', 'ci')
            
            save {
                compression.set(SaveCompression.XZ)
                outputFile.set(providers.provider { 
                    layout.buildDirectory.file("archives/app-${version.get()}.tar.xz")
                })
            }
            
            publish {
                to('staging') {
                    registry.set('staging.company.com')
                    namespace.set('staging/apps')
                    publishTags(['staging-latest', providers.provider { "staging-${version.get()}" }])
                }
                
                to('production') {
                    registry.set('prod.company.com')
                    repository.set('production/applications/complex-app')
                    publishTags(['prod-latest', 'release'])
                }
            }
        }
    }
}
```

### **Gap 5: Provider API Edge Cases - INCOMPLETE**

**Issue**: Provider API testing is basic, missing advanced configuration cache and property resolution scenarios

**Missing Coverage**:
- **Gradle property resolution** with different sources (gradle.properties, system properties, environment variables)
- **Provider chain resolution** failures and fallback mechanisms
- **Configuration cache compatibility** verification with complex Provider scenarios
- **Late-bound property resolution** with interdependent properties

**Required Test Scenarios**:
```groovy
// MISSING: Advanced Provider API scenarios
docker {
    images {
        providerEdgeCasesApp {
            // Complex provider chains with fallbacks
            registry.set(
                providers.gradleProperty('docker.registry')
                    .orElse(providers.systemProperty('DOCKER_REGISTRY'))
                    .orElse(providers.environmentVariable('DOCKER_REGISTRY'))
                    .orElse('docker.io')
            )
            
            // Provider-dependent properties
            namespace.set(providers.gradleProperty('app.namespace').orElse('default'))
            imageName.set(providers.provider { 
                "${rootProject.name}-${project.name}".toLowerCase()
            })
            
            // Late-bound version resolution
            version.set(providers.provider {
                if (project.hasProperty('release.version')) {
                    project.property('release.version').toString()
                } else {
                    "${project.version}-SNAPSHOT"
                }
            })
            
            context.set(file('.'))
        }
    }
}
```

---

## **📋 IMPLEMENTATION PLAN**

### **Phase 1: CRITICAL GAPS - High Priority**

#### **Action 1.1: Create DockerLabelsFunctionalTest.groovy**
**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/DockerLabelsFunctionalTest.groovy`

**Test Coverage**:
- Basic labels configuration with OCI standard labels
- Custom labels with various value types
- Provider-based label values with lazy evaluation
- Labels validation and property resolution
- Labels integration verification with DockerBuildTask

**Estimated Effort**: ~150-200 lines

#### **Action 1.2: Enhance DockerTagFunctionalTest.groovy**
**Enhancement**: Add comprehensive sourceRef mode coverage

**Test Coverage**:
- SourceRef mode retagging scenarios with multiple tags
- Dual-mode validation (Build vs SourceRef exclusivity)
- Tag conflicts and resolution in sourceRef mode
- Provider-based tag generation in sourceRef mode

**Estimated Effort**: ~100-150 additional lines

#### **Action 1.3: Enhance DockerPublishFunctionalTest.groovy**
**Enhancement**: Add sourceRef mode publishing scenarios

**Test Coverage**:
- SourceRef republishing to multiple targets
- Authentication in sourceRef mode for pulls and pushes
- Cross-registry republishing workflows
- PublishTarget configuration in sourceRef mode

**Estimated Effort**: ~100-150 additional lines

### **Phase 2: COMPREHENSIVE INTEGRATION - Medium Priority**

#### **Action 2.1: Create DockerNomenclatureIntegrationFunctionalTest.groovy**
**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/DockerNomenclatureIntegrationFunctionalTest.groovy`

**Test Coverage**:
- End-to-end workflows: Build → Save → Tag → Publish
- Multi-registry complex workflows
- Version handling with complex version schemes
- Repository vs namespace+imageName edge cases
- Cross-mode validation scenarios

**Estimated Effort**: ~200-250 lines

#### **Action 2.2: Create DockerProviderAPIFunctionalTest.groovy**
**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/DockerProviderAPIFunctionalTest.groovy`

**Test Coverage**:
- Gradle property resolution from multiple sources
- Provider chain failures and fallback mechanisms
- Configuration cache compatibility verification
- Late-bound property resolution scenarios
- Provider interdependency resolution

**Estimated Effort**: ~150-200 lines

### **Phase 3: AUTHENTICATION & EDGE CASES - Lower Priority**

#### **Action 3.1: Create DockerAuthenticationIntegrationFunctionalTest.groovy**
**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/DockerAuthenticationIntegrationFunctionalTest.groovy`

**Test Coverage**:
- Cross-task authentication consistency
- Multiple credential types across different operations
- Authentication failure scenarios simulation
- Registry-specific authentication patterns

**Estimated Effort**: ~100-150 lines

#### **Action 3.2: Enhance Existing Tests with Edge Cases**
**Enhancement**: Add edge case coverage to all existing functional tests

**Test Coverage**:
- Complex registry hostname validation
- Unicode and special character handling in properties
- Property validation edge cases
- Configuration cache stress testing

**Estimated Effort**: ~50-100 additional lines across files

---

## **🎯 SUMMARY & PRIORITIES**

### **Current Status Assessment**:
- **Foundational Coverage**: Good (70-80% of basic scenarios covered)
- **Advanced Scenarios**: Poor (20-30% of complex scenarios covered)
- **New Features**: Critical Gaps (Labels feature 0% covered)

### **Priority Actions**:

1. **🔴 CRITICAL (Phase 1)**: 
   - Add `DockerLabelsFunctionalTest.groovy` - **IMMEDIATE**
   - Enhance sourceRef coverage in existing tests - **IMMEDIATE**

2. **🟡 IMPORTANT (Phase 2)**: 
   - Create comprehensive integration test - **NEXT SPRINT**
   - Add Provider API stress testing - **NEXT SPRINT**

3. **🟢 BENEFICIAL (Phase 3)**: 
   - Authentication integration testing - **FUTURE**
   - Edge case coverage expansion - **FUTURE**

### **Effort Estimation**:
- **Phase 1 (Critical)**: ~350-500 lines of functional test code
- **Phase 2 (Important)**: ~350-450 lines of functional test code  
- **Phase 3 (Beneficial)**: ~150-250 lines of functional test code
- **Total**: ~850-1200 lines of comprehensive functional test coverage

### **Success Criteria**:
- **100% coverage** of Docker nomenclature DSL features in functional tests
- **90%+ coverage** of dual-mode scenarios (Build vs SourceRef)
- **100% coverage** of new features (labels, SaveCompression enum)
- **80%+ coverage** of Provider API advanced scenarios
- **Integration test coverage** for end-to-end workflows

This plan ensures comprehensive functional test coverage for all implemented Docker nomenclature DSL features while maintaining the current TestKit compatibility approach used throughout the existing functional test suite.