# Implementation Plan: Add Spock Extension for METHOD Lifecycle Support

**Date**: 2025-10-11  
**Status**: Ready to Implement  
**Priority**: High  
**Related**: `2025-10-11-no-support-method-lifecycle-bug.md`  
**Estimated Total Effort**: 14-18 hours

## Executive Summary

Implement `DockerComposeSpockExtension` to provide METHOD-level lifecycle support for Spock users, achieving feature
parity with JUnit 5. This fixes the architectural limitation where Gradle tasks can only support CLASS-level lifecycle.

After implementation:
- ✅ Both Spock and JUnit 5 users can use CLASS and METHOD lifecycles
- ✅ Test framework extensions become the primary system for test lifecycle
- ✅ Gradle tasks remain available for manual/CI operations (not test lifecycle)
- ✅ Complete documentation and examples for plugin users

## Architecture Decision

**Two Orchestration Systems by Design:**

1. **Test Framework Extensions** (Primary for Test Lifecycle)
   - Spock: `@ComposeUp` annotation
   - JUnit 5: `@ExtendWith(DockerCompose*Extension.class)`
   - Supports: CLASS and METHOD lifecycles
   - Used by: Integration test code

2. **Gradle Tasks** (Optional for Manual/CI Operations)
   - `composeUp*`, `composeDown*` tasks
   - Supports: Manual up/down only (not lifecycle-aware)
   - Used by: CLI, CI/CD scripts, manual operations

---

## Phase 1: Create Spock Extension Infrastructure (4-5 hours)

### 1.1 Create Annotation and Enum (30 minutes)

**Files to Create:**

#### `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeUp.groovy`
```groovy
package com.kineticfire.gradle.docker.spock

import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to configure Docker Compose orchestration for Spock tests.
 *
 * Usage:
 * <pre>
 * {@code
 * @ComposeUp(
 *     stackName = "myApp",
 *     composeFile = "src/integrationTest/resources/compose/app.yml",
 *     lifecycle = LifecycleMode.METHOD,
 *     waitForHealthy = ["web-app"]
 * )
 * class MyIntegrationTest extends Specification {
 *     def "test 1"() { ... }  // Fresh containers
 *     def "test 2"() { ... }  // Fresh containers
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@ExtensionAnnotation(DockerComposeSpockExtension)
@interface ComposeUp {
    
    /** Stack name (used for state file naming) */
    String stackName()
    
    /** Path to compose file relative to project root */
    String composeFile()
    
    /** Lifecycle mode: CLASS or METHOD */
    LifecycleMode lifecycle() default LifecycleMode.CLASS
    
    /** Docker Compose project name base */
    String projectName() default ""
    
    /** Services to wait for healthy state */
    String[] waitForHealthy() default []
    
    /** Services to wait for running state */
    String[] waitForRunning() default []
    
    /** Timeout in seconds for wait operations */
    int timeoutSeconds() default 60
    
    /** Poll interval in seconds for wait operations */
    int pollSeconds() default 2
}
```

#### `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/LifecycleMode.groovy`
```groovy
package com.kineticfire.gradle.docker.spock

/**
 * Lifecycle modes for Docker Compose orchestration in tests.
 */
enum LifecycleMode {
    /**
     * Containers start once before all test methods (setupSpec/cleanupSpec)
     * and remain running throughout all tests in the class.
     */
    CLASS,
    
    /**
     * Containers start fresh before each test method (setup/cleanup)
     * and are torn down after each test completes.
     */
    METHOD
}
```

**Estimated Time**: 30 minutes

---

### 1.2 Create Extension Implementation (2-2.5 hours)

**Files to Create:**

#### `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtension.groovy`
Main extension that processes `@ComposeUp` annotation and registers interceptors.

**Key Responsibilities:**
- Parse `@ComposeUp` annotation
- Create unique project names (with timestamp)
- Register CLASS or METHOD interceptors based on lifecycle mode
- Inject services (reuse existing `JUnitComposeService`, `FileService`, etc.)

**Implementation Notes:**
- Extends `AbstractAnnotationDrivenExtension<ComposeUp>`
- Override `visitSpecAnnotation()` to handle CLASS lifecycle
- Override `visitFeatureAnnotation()` or use interceptors for METHOD lifecycle
- Reuse existing service layer (no new services needed)

#### `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeClassInterceptor.groovy`
Interceptor for CLASS lifecycle (setupSpec/cleanupSpec).

**Key Responsibilities:**
- Start containers before first test (setupSpec equivalent)
- Wait for services (healthy/running)
- Generate state file with `"lifecycle": "class"`
- Set system property `COMPOSE_STATE_FILE`
- Stop containers after last test (cleanupSpec equivalent)
- Clean up resources aggressively

**Implementation Notes:**
- Implement `IMethodInterceptor`
- Intercept `setupSpec` and `cleanupSpec` methods
- Mirror behavior of `DockerComposeClassExtension.java`
- Use `ThreadLocal` to store project name for cleanup

#### `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeMethodInterceptor.groovy`
Interceptor for METHOD lifecycle (setup/cleanup).

**Key Responsibilities:**
- Start containers before each test (setup equivalent)
- Wait for services
- Generate state file with `"lifecycle": "method"`
- Update system property `COMPOSE_STATE_FILE` for each test
- Stop containers after each test (cleanup equivalent)
- Generate unique project name per test

**Implementation Notes:**
- Implement `IMethodInterceptor`
- Intercept `setup` and `cleanup` methods (or feature execution)
- Mirror behavior of `DockerComposeMethodExtension.java`
- Ensure complete isolation between test methods

**Estimated Time**: 2-2.5 hours

---

### 1.3 Add Unit Tests for Extension (1-1.5 hours)

**Files to Create:**

#### `plugin/src/test/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtensionTest.groovy`
Test extension logic with mocked services.

**Test Coverage:**
- Annotation parsing
- Lifecycle mode selection (CLASS vs METHOD)
- Unique project name generation
- Service injection
- Interceptor registration
- Error handling

#### `plugin/src/test/groovy/com/kineticfire/gradle/docker/spock/ComposeClassInterceptorTest.groovy`
Test CLASS lifecycle interceptor.

**Test Coverage:**
- setupSpec interception
- cleanupSpec interception
- Service calls (start, wait, stop)
- State file generation
- System property setting
- Cleanup on failure

#### `plugin/src/test/groovy/com/kineticfire/gradle/docker/spock/ComposeMethodInterceptorTest.groovy`
Test METHOD lifecycle interceptor.

**Test Coverage:**
- setup interception (per test)
- cleanup interception (per test)
- Unique project names per test
- State file generation per test
- System property updates per test
- Cleanup on failure

**Estimated Time**: 1-1.5 hours

---

## Phase 2: Refactor Integration Tests (3-4 hours)

### 2.1 Update Verification Tests (1.5-2 hours)

**Files to Update:**

#### `verification/lifecycle-method/app-image/build.gradle`
**REMOVE** Gradle task dependencies:
```groovy
// OLD - REMOVE these lines
tasks.register('integrationTest', Test) {
    dependsOn 'composeUpLifecycleMethodTest'
    finalizedBy 'composeDownLifecycleMethodTest'
    systemProperty 'COMPOSE_STATE_FILE', '...'
    systemProperty 'COMPOSE_PROJECT_NAME', '...'
}

// NEW - Extension handles everything
tasks.register('integrationTest', Test) {
    description = 'Runs METHOD lifecycle verification tests'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    outputs.cacheIf { false }
}
```

#### `verification/lifecycle-method/app-image/src/integrationTest/groovy/.../LifecycleMethodIT.groovy`
**ADD** `@ComposeUp` annotation:
```groovy
@ComposeUp(
    stackName = "lifecycleMethodTest",
    composeFile = "src/integrationTest/resources/compose/lifecycle-method.yml",
    lifecycle = LifecycleMode.METHOD,
    waitForHealthy = ["state-app"],
    timeoutSeconds = 60,
    pollSeconds = 2
)
class LifecycleMethodIT extends Specification {
    // Remove manual state file reading from setup()
    // Extension provides COMPOSE_STATE_FILE automatically
    
    def "test 1"() { ... }  // Fresh containers
    def "test 2"() { ... }  // Fresh containers
}
```

#### `verification/lifecycle-class/app-image/src/integrationTest/groovy/.../LifecycleClassIT.groovy`
**OPTIONAL**: Convert to extension for consistency
```groovy
@ComposeUp(
    stackName = "lifecycleClassTest",
    composeFile = "src/integrationTest/resources/compose/lifecycle-class.yml",
    lifecycle = LifecycleMode.CLASS,
    waitForHealthy = ["state-app"],
    timeoutSeconds = 60
)
class LifecycleClassIT extends Specification {
    // Keep static variables
    // Remove setupSpec() state file reading (extension handles it)
}
```

**Estimated Time**: 1.5-2 hours

---

### 2.2 Update Example Tests (1.5-2 hours)

**Files to Update:**

#### `examples/isolated-tests/app-image/build.gradle`
Remove Gradle task dependencies (same as verification tests).

#### `examples/isolated-tests/app-image/src/integrationTest/groovy/.../IsolatedTestsExampleIT.groovy`
**ADD** `@ComposeUp` annotation with METHOD lifecycle:
```groovy
@ComposeUp(
    stackName = "isolatedTestsExample",
    composeFile = "src/integrationTest/resources/compose/isolated-tests.yml",
    lifecycle = LifecycleMode.METHOD,
    waitForHealthy = ["isolated-tests"],
    timeoutSeconds = 60
)
class IsolatedTestsExampleIT extends Specification {
    // Instance variables (NOT static)
    String baseUrl
    
    def setup() {
        // Extension provides COMPOSE_STATE_FILE
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)
        def port = stateData.services['isolated-tests'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"
        RestAssured.baseURI = baseUrl
    }
}
```

#### `examples/web-app/` and `examples/stateful-web-app/`
**OPTIONAL**: Convert to extension for consistency, or keep as Gradle task examples (demonstrates both approaches).

**Estimated Time**: 1.5-2 hours

---

## Phase 3: Add JUnit 5 Examples (2-3 hours)

### 3.1 Create JUnit 5 CLASS Lifecycle Example (1-1.5 hours)

**Directory**: `examples/web-app-junit/`

**Structure**:
```
examples/web-app-junit/
├── app/                          # Spring Boot app (copy from web-app/app)
│   ├── build.gradle
│   └── src/main/java/...
├── app-image/
│   ├── src/
│   │   ├── main/docker/          # Dockerfile
│   │   └── integrationTest/
│   │       ├── java/com/kineticfire/test/
│   │       │   └── WebAppJUnit5ClassIT.java
│   │       └── resources/compose/
│   │           └── web-app.yml
│   └── build.gradle
├── build.gradle
└── README.md
```

**Test File**: `WebAppJUnit5ClassIT.java`
```java
package com.kineticfire.test;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;
import io.restassured.RestAssured;
import static io.restassured.RestAssured.given;

/**
 * Example: JUnit 5 with CLASS Lifecycle
 *
 * Demonstrates how to use DockerComposeClassExtension for CLASS-level lifecycle:
 * - Containers start once before all tests (@BeforeAll)
 * - All tests run against the same containers
 * - Containers stop once after all tests (@AfterAll)
 */
@ExtendWith(DockerComposeClassExtension.class)
class WebAppJUnit5ClassIT {
    
    private static String baseUrl;
    
    @BeforeAll
    static void setupAll() {
        // Extension provides COMPOSE_STATE_FILE system property
        // Read state file to get port mapping
        // ... setup code ...
        RestAssured.baseURI = baseUrl;
    }
    
    @Test
    void shouldRespondToHealthCheck() {
        // ... test implementation ...
    }
    
    @Test
    void shouldReturnAppInformation() {
        // ... test implementation ...
    }
}
```

**README.md**: Document JUnit 5 CLASS lifecycle usage for plugin users.

**Estimated Time**: 1-1.5 hours

---

### 3.2 Create JUnit 5 METHOD Lifecycle Example (1-1.5 hours)

**Directory**: `examples/isolated-tests-junit/`

**Structure**: Same as web-app-junit

**Test File**: `IsolatedTestsJUnit5MethodIT.java`
```java
package com.kineticfire.test;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;
import io.restassured.RestAssured;
import static io.restassured.RestAssured.given;

/**
 * Example: JUnit 5 with METHOD Lifecycle
 *
 * Demonstrates how to use DockerComposeMethodExtension for METHOD-level lifecycle:
 * - Containers start fresh before each test (@BeforeEach)
 * - One test runs
 * - Containers stop after each test (@AfterEach)
 * - Complete isolation between tests
 */
@ExtendWith(DockerComposeMethodExtension.class)
class IsolatedTestsJUnit5MethodIT {
    
    private String baseUrl;
    
    @BeforeEach
    void setupEach() {
        // Extension provides COMPOSE_STATE_FILE system property
        // Fresh state file for EACH test
        // ... setup code ...
        RestAssured.baseURI = baseUrl;
    }
    
    @Test
    void test1_shouldCreateUserAlice() {
        // ... test implementation ...
    }
    
    @Test
    void test2_shouldNotFindAlice() {
        // Proves isolation - alice from test1 doesn't exist
        // ... test implementation ...
    }
}
```

**README.md**: Document JUnit 5 METHOD lifecycle usage.

**Estimated Time**: 1-1.5 hours

---

## Phase 4: Update Documentation (4-5 hours)

### 4.1 Update `docs/usage/usage-docker-orch.md` (2-2.5 hours)

**Replace Section**: "Overview of Docker Compose Orchestration" (lines 52-65)

**New Content**:
```markdown
## Overview: Two Approaches for Docker Compose Orchestration

The plugin provides two approaches for Docker Compose orchestration:

### Approach 1: Test Framework Extensions (Recommended for Test Lifecycle)

**Use For**: CLASS and METHOD lifecycle management in integration tests

Test framework extensions provide native integration with test lifecycles:
- Automatic container startup/shutdown
- State file generation
- Health/readiness waiting
- Full support for both CLASS and METHOD lifecycles

#### Spock Tests

**CLASS Lifecycle** (setupSpec/cleanupSpec):
```groovy
import com.kineticfire.gradle.docker.spock.ComposeUp
import com.kineticfire.gradle.docker.spock.LifecycleMode

@ComposeUp(
    stackName = "myApp",
    composeFile = "src/integrationTest/resources/compose/app.yml",
    lifecycle = LifecycleMode.CLASS,
    waitForHealthy = ["web-app"],
    timeoutSeconds = 60
)
class MyIntegrationTest extends Specification {
    static String baseUrl
    
    def setupSpec() {
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)
        def port = stateData.services['web-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"
    }
    
    def "test 1"() { /* shares containers */ }
    def "test 2"() { /* shares containers */ }
}
```

**METHOD Lifecycle** (setup/cleanup):
```groovy
@ComposeUp(
    stackName = "myApp",
    composeFile = "src/integrationTest/resources/compose/app.yml",
    lifecycle = LifecycleMode.METHOD,
    waitForHealthy = ["web-app"]
)
class IsolatedTest extends Specification {
    String baseUrl  // Instance variable (NOT static)
    
    def setup() {
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)
        def port = stateData.services['web-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"
    }
    
    def "test 1"() { /* fresh containers */ }
    def "test 2"() { /* fresh containers */ }
}
```

#### JUnit 5 Tests

**CLASS Lifecycle** (@BeforeAll/@AfterAll):
```java
import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;

@ExtendWith(DockerComposeClassExtension.class)
class MyIntegrationTest {
    private static String baseUrl;
    
    @BeforeAll
    static void setupAll() {
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        // Parse state file and setup baseUrl
    }
    
    @Test void test1() { /* shares containers */ }
    @Test void test2() { /* shares containers */ }
}
```

**METHOD Lifecycle** (@BeforeEach/@AfterEach):
```java
import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;

@ExtendWith(DockerComposeMethodExtension.class)
class IsolatedTest {
    private String baseUrl;
    
    @BeforeEach
    void setupEach() {
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        // Parse state file and setup baseUrl (fresh for each test)
    }
    
    @Test void test1() { /* fresh containers */ }
    @Test void test2() { /* fresh containers */ }
}
```

**When to Use Each Lifecycle:**

| Lifecycle | Use When | Example |
|-----------|----------|---------|
| **CLASS** | Tests share state, workflow tests, read-only operations | Register → Login → Update → Logout |
| **METHOD** | Tests need isolation, independent tests, database operations | Create user → verify isolation |

### Approach 2: Gradle Tasks (Optional for Manual/CI Operations)

**Use For**: Manual orchestration, CI/CD workflows, build-time operations

**⚠️ Limitation**: Gradle tasks support CLASS lifecycle only (containers cannot restart between test methods)

```gradle
dockerOrch {
    composeStacks {
        myStack {
            files.from('compose.yml')
            projectName = "my-project"
            
            waitForHealthy {
                waitForServices.set(['web-app'])
                timeoutSeconds.set(60)
            }
        }
    }
}

// Manual CLI usage
./gradlew composeUpMyStack     // Start stack
./gradlew composeDownMyStack   // Stop stack

// Build-time orchestration (CLASS lifecycle only)
tasks.register('integrationTest', Test) {
    dependsOn 'composeUpMyStack'
    finalizedBy 'composeDownMyStack'
}
```

**Recommendation**: Use test framework extensions for test lifecycle, use Gradle tasks for manual/CI operations.

```

**Update Remaining Sections**:
- Replace all lifecycle examples to show extension approach
- Add troubleshooting section for extensions
- Update best practices to recommend extensions

**Estimated Time**: 2-2.5 hours

---

### 4.2 Create New Documentation File (1-1.5 hours)

**File**: `docs/usage/spock-junit-test-extensions.md`

**Content**:
```markdown
# Spock and JUnit 5 Test Extensions Guide

Complete guide to using test framework extensions for Docker Compose orchestration.

## Overview

Test framework extensions provide the best way to manage Docker Compose lifecycles in integration tests:
- ✅ Native integration with test frameworks
- ✅ Full support for CLASS and METHOD lifecycles
- ✅ Automatic state file generation
- ✅ Clean separation of test and build concerns

## Spock Extension (@ComposeUp)

### Installation

The extension is included in the plugin - no additional dependencies needed.

### Basic Usage

(... detailed examples ...)

### Configuration Options

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| stackName | String | required | Stack name for state file |
| composeFile | String | required | Path to compose file |
| lifecycle | LifecycleMode | CLASS | CLASS or METHOD |
| projectName | String | auto-generated | Docker Compose project name |
| waitForHealthy | String[] | [] | Services to wait for healthy |
| waitForRunning | String[] | [] | Services to wait for running |
| timeoutSeconds | int | 60 | Wait timeout |
| pollSeconds | int | 2 | Poll interval |

### CLASS Lifecycle Example

(... complete example ...)

### METHOD Lifecycle Example

(... complete example ...)

### Reading State Files

(... examples ...)

## JUnit 5 Extensions

### Installation

(... dependency info if needed ...)

### DockerComposeClassExtension

(... complete examples ...)

### DockerComposeMethodExtension

(... complete examples ...)

## Troubleshooting

(... common issues and solutions ...)

## Best Practices

1. Use CLASS lifecycle for workflow tests
2. Use METHOD lifecycle for isolated tests
3. Read state files in setup methods
4. Configure timeouts appropriately
5. Use health checks in compose files

## Migration from Gradle Tasks

(... migration guide ...)
```

**Estimated Time**: 1-1.5 hours

---

### 4.3 Update Example READMEs (1 hour)

**Files to Update:**
- `plugin-integration-test/dockerOrch/README.md` - Add extension info
- `plugin-integration-test/dockerOrch/examples/README.md` - Document new examples
- Individual example READMEs - Show extension usage

**Content Updates:**
- Add sections explaining Spock vs JUnit examples
- Document CLASS vs METHOD lifecycle examples
- Show extension annotation usage
- Update running instructions

**Estimated Time**: 1 hour

---

## Phase 5: Verification and Testing (1-2 hours)

### 5.1 Run All Tests

**Commands**:
```bash
# Plugin unit tests
cd plugin
./gradlew clean test

# Plugin build
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

# Integration tests
cd ../plugin-integration-test
./gradlew cleanAll integrationTest

# Verify no containers remain
docker ps -a
```

**Success Criteria**:
- ✅ All plugin unit tests pass (including new extension tests)
- ✅ Plugin builds successfully
- ✅ All verification tests pass (including lifecycle-method: 7/7)
- ✅ All example tests pass
- ✅ No containers remain after tests

**Estimated Time**: 1-2 hours

---

## Summary of Changes

### New Files Created

**Plugin Source**:
1. `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeUp.groovy`
2. `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/LifecycleMode.groovy`
3. `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtension.groovy`
4. `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeClassInterceptor.groovy`
5. `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/ComposeMethodInterceptor.groovy`

**Plugin Tests**:
6. `plugin/src/test/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtensionTest.groovy`
7. `plugin/src/test/groovy/com/kineticfire/gradle/docker/spock/ComposeClassInterceptorTest.groovy`
8. `plugin/src/test/groovy/com/kineticfire/gradle/docker/spock/ComposeMethodInterceptorTest.groovy`

**Integration Test Examples**:
9. `plugin-integration-test/dockerOrch/examples/web-app-junit/` (complete directory)
10. `plugin-integration-test/dockerOrch/examples/isolated-tests-junit/` (complete directory)

**Documentation**:
11. `docs/usage/spock-junit-test-extensions.md`
12. `docs/design-docs/todo/2025-10-11-no-support-method-lifecycle-bug-fix-plan.md` (this file)

### Files Modified

**Integration Tests**:
1. `plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/build.gradle`
2. `plugin-integration-test/dockerOrch/verification/lifecycle-method/app-image/src/integrationTest/groovy/.../LifecycleMethodIT.groovy`
3. `plugin-integration-test/dockerOrch/verification/lifecycle-class/app-image/src/integrationTest/groovy/.../LifecycleClassIT.groovy` (optional)
4. `plugin-integration-test/dockerOrch/examples/isolated-tests/app-image/build.gradle`
5. `plugin-integration-test/dockerOrch/examples/isolated-tests/app-image/src/integrationTest/groovy/.../IsolatedTestsExampleIT.groovy`

**Documentation**:
6. `docs/usage/usage-docker-orch.md` (major updates)
7. `plugin-integration-test/dockerOrch/README.md`
8. `plugin-integration-test/dockerOrch/examples/README.md`
9. Individual example READMEs

---

## Feature Matrix After Implementation

| Feature | Spock | JUnit 5 | Gradle Tasks |
|---------|-------|---------|--------------|
| **CLASS Lifecycle** | ✅ `@ComposeUp(lifecycle=CLASS)` | ✅ `@ExtendWith(DockerComposeClassExtension)` | ✅ `dependsOn`/`finalizedBy` |
| **METHOD Lifecycle** | ✅ `@ComposeUp(lifecycle=METHOD)` | ✅ `@ExtendWith(DockerComposeMethodExtension)` | ❌ Architecturally impossible |
| **Auto State File** | ✅ Yes | ✅ Yes | ✅ Yes |
| **Health Waiting** | ✅ Yes | ✅ Yes | ✅ Yes |
| **Use Case** | Integration tests | Integration tests | Manual/CI operations |

---

## Gradle 9/10 Compatibility

✅ **All changes are Gradle 9/10 compatible:**
- Extensions run at test execution time (no configuration cache impact)
- Reuse existing services (already configuration-cache safe)
- No `Project` references at execution time
- No `afterEvaluate` in extension code
- Provider API used where needed

---

## Success Criteria

After implementation, all of these must be true:

- [ ] `DockerComposeSpockExtension` implemented with CLASS and METHOD support
- [ ] Unit tests for extension achieve 100% coverage
- [ ] `lifecycle-method` verification tests pass (7/7 success rate)
- [ ] `lifecycle-class` verification tests continue passing
- [ ] New JUnit 5 examples created (web-app-junit, isolated-tests-junit)
- [ ] Existing Spock examples updated to use extensions
- [ ] `docs/usage/usage-docker-orch.md` completely updated
- [ ] `docs/usage/spock-junit-test-extensions.md` created
- [ ] All example READMEs updated
- [ ] Plugin builds successfully with no warnings
- [ ] All integration tests pass
- [ ] No containers remain after test execution (`docker ps -a` clean)
- [ ] Documentation clearly explains when to use extensions vs tasks
- [ ] Examples demonstrate both Spock and JUnit 5 for both lifecycles

---

## Effort Summary

| Phase | Description | Estimated Time |
|-------|-------------|----------------|
| **Phase 1** | Create Spock Extension Infrastructure | 4-5 hours |
| **Phase 2** | Refactor Integration Tests | 3-4 hours |
| **Phase 3** | Add JUnit 5 Examples | 2-3 hours |
| **Phase 4** | Update Documentation | 4-5 hours |
| **Phase 5** | Verification and Testing | 1-2 hours |
| **Total** | | **14-19 hours** |

---

## Next Steps

1. Review and approve this plan
2. Implement Phase 1 (Spock Extension Infrastructure)
3. Run unit tests to validate extension
4. Implement Phase 2 (Refactor Integration Tests)
5. Verify lifecycle-method tests pass
6. Implement Phase 3 (JUnit 5 Examples)
7. Implement Phase 4 (Documentation)
8. Run full verification (Phase 5)
9. Mark `2025-10-11-no-support-method-lifecycle-bug.md` as resolved

---

## References

- **Bug Document**: `docs/design-docs/todo/2025-10-11-no-support-method-lifecycle-bug.md`
- **Gradle 9/10 Compatibility**: `docs/design-docs/gradle-9-and-10-compatibility.md`
- **Current Usage Doc**: `docs/usage/usage-docker-orch.md`
- **JUnit Extensions** (existing): 
  - `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeClassExtension.groovy`
  - `plugin/src/main/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtension.groovy`
