# Architectural Limitations Analysis: docker vs dockerTest DSL Separation

## Executive Summary

This document analyzes a fundamental architectural limitation in the current plugin design: the separation of `docker`
and `dockerTest` DSLs prevents natural test-driven workflows where images are tested before publication operations.
This affects the plugin's usability for common CI/CD patterns.

**Status**: Analysis complete, awaiting design decision

**Date**: 2025-01-23

**Impact**: Medium-High (affects real-world CI/CD usage patterns)

## Current Design Analysis

### Architecture Overview

The plugin uses **two independent DSLs** with distinct responsibilities:

**1. `docker` DSL** (DockerExtension)
- **Purpose**: Docker CLI operations
- **Capabilities**: build, tag, save, publish
- **Container**: `NamedDomainObjectContainer<ImageSpec>`
- **Generated Tasks**: `dockerBuild*`, `dockerTag*`, `dockerSave*`, `dockerPublish*`, `dockerImage*`
- **Lifecycle**: Build-time operations on images

**2. `dockerTest` DSL** (DockerTestExtension)
- **Purpose**: Docker Compose orchestration for testing
- **Capabilities**: composeUp, composeDown, health checking, log capture
- **Container**: `NamedDomainObjectContainer<ComposeStackSpec>`
- **Generated Tasks**: `composeUp*`, `composeDown*`
- **Lifecycle**: Test-time container orchestration

### Current Relationship

```
docker DSL (ImageSpec) ──────────────X NO CONNECTION X────────────────> dockerTest DSL (ComposeStackSpec)
     │                                                                           │
     ├─ dockerBuild*                                                            ├─ composeUp*
     ├─ dockerTag*                                                              └─ composeDown*
     ├─ dockerSave*
     └─ dockerPublish*
```

The **only connection** is manual:
```groovy
afterEvaluate {
    tasks.named('composeUpWebAppTest') {
        dependsOn tasks.named('dockerBuildWebApp')  // User must wire this manually
    }
}
```

## Desired Workflow (Industry-Standard CI/CD Pattern)

The typical CI/CD workflow for containerized applications:

```
1. BUILD IMAGE
   └─ docker: build + initial tags
          ↓
2. TEST IMAGE
   └─ dockerTest: compose up → run tests → compose down
          ↓
3. IF TESTS PASS:
   ├─ docker: add additional tags (e.g., 'stable', 'production')
   ├─ docker: save to tar (optional)
   └─ docker: publish to registry
```

This is **industry-standard practice** for container-based applications.

## Problem Statement

### The Gap

**Current Design Supports:**
```groovy
// Option A: Build + publish (NO testing)
docker {
    images {
        myApp {
            // build, tag, save, publish
        }
    }
}

// Option B: Build + test (separate DSL)
docker { images { myApp { /* build only */ } } }
dockerTest { composeStacks { test { /* test */ } } }
```

**Current Design CANNOT Support:**
```groovy
// Option C: Build → Test → Conditional Publish
docker {
    images {
        myApp {
            build + initialTags
            // << TESTING SHOULD HAPPEN HERE >>
            additionalTags (if tests pass)
            save (if tests pass)
            publish (if tests pass)
        }
    }
}
```

### Real-World Example

**What users want:**

```groovy
docker {
    images {
        webApp {
            imageName = 'example-web-app'
            tags = ['latest', '1.0.0']  // Initial build tags

            contextTask = tasks.register('prepareWebAppContext', Copy) { /* ... */ }

            // << TESTS SHOULD GO HERE >>
            // test {
            //     composeFile = 'src/integrationTest/resources/compose/web-app.yml'
            //     waitForHealthy { /* ... */ }
            // }

            // << CONDITIONAL ON TESTS PASSING >>
            // additionalTags = ['stable', 'production']  // Only if tests pass

            save {
                compression.set(GZIP)
                outputFile.set(file('build/web-app.tar.gz'))
            }

            publish {
                to('production') {
                    registry.set('ghcr.io')
                    publishTags.set(['stable'])
                }
            }
        }
    }
}
```

**What users actually write** (current workaround):

```groovy
// TWO SEPARATE DSL BLOCKS
docker {
    images {
        webApp {
            imageName = 'example-web-app'
            tags = ['latest', '1.0.0']
            contextTask = tasks.register('prepareWebAppContext', Copy) { /* ... */ }

            // << Can't test here >>

            save { /* ... */ }
            publish { /* ... */ }
        }
    }
}

dockerTest {
    composeStacks {
        webAppTest {  // << Completely separate definition >>
            files.from('src/integrationTest/resources/compose/web-app.yml')
            waitForHealthy { /* ... */ }
        }
    }
}

// << Manual wiring required >>
afterEvaluate {
    tasks.named('composeUpWebAppTest') {
        dependsOn tasks.named('dockerBuildWebApp')
    }
    tasks.named('dockerPublishWebApp') {
        dependsOn tasks.named('integrationTest')  // << This is awkward >>
    }
}
```

## Root Cause Analysis

### 1. Architectural Separation

The two DSLs are **architecturally isolated**:
- Different domain objects (`ImageSpec` vs `ComposeStackSpec`)
- Different service layers (`DockerService` vs `ComposeService`)
- No shared state or cross-references
- No built-in dependency management between them

### 2. Task Generation Independence

Tasks are generated **independently**:
```groovy
// GradleDockerPlugin.groovy (conceptual)
registerTaskCreationRules(project, dockerExt, dockerTestExt, ...)

// Two separate registration paths:
dockerExt.images.all { image ->
    registerDockerBuildTask(image)
    registerDockerTagTask(image)
    registerDockerSaveTask(image)
    registerDockerPublishTask(image)
}

dockerTestExt.composeStacks.all { stack ->
    registerComposeUpTask(stack)
    registerComposeDownTask(stack)
}

// NO cross-wiring between them!
```

### 3. Naming Disconnect

Even the **naming is disconnected**:
- `docker.images.webApp` → generates `dockerBuildWebApp`
- `dockerTest.composeStacks.webAppTest` → generates `composeUpWebAppTest`

The user must **manually infer and wire** the relationship:
```groovy
// User must know:
// 1. webApp (docker) → dockerBuildWebApp task
// 2. webAppTest (dockerTest) → composeUpWebAppTest task
// 3. These should be related somehow
// 4. Manual dependency wiring required

afterEvaluate {
    tasks.named('composeUpWebAppTest') {
        dependsOn tasks.named('dockerBuildWebApp')
    }
}
```

### 4. Conditional Logic Not Supported

The current design has **no mechanism** for:
- Conditional tag application (only if tests pass)
- Conditional save operation (only if tests pass)
- Conditional publish operation (only if tests pass)

All operations in `docker.images.myApp` are **unconditionally executed** when the task runs.

## Impact on User Experience

### Current Workarounds (from examples)

All integration test examples show the **same workaround pattern**:

1. Define image in `docker` DSL
2. Define test stack in `dockerTest` DSL (completely separate)
3. Manually wire `composeUp` to depend on `dockerBuild`
4. Manually wire `integrationTest` to depend on `composeUp`
5. Use test framework extensions (@ComposeUp) for lifecycle management

**Evidence from examples:**
- `plugin-integration-test/dockerTest/examples/web-app/app-image/build.gradle:133-137`
- `plugin-integration-test/dockerTest/examples/database-app/app-image/build.gradle:100-102`
- `plugin-integration-test/dockerTest/examples/stateful-web-app/app-image/build.gradle:136-140`
- `plugin-integration-test/dockerTest/examples/isolated-tests/app-image/build.gradle` (same pattern)

### User Pain Points

1. **Cognitive Load**: Users must understand TWO separate DSLs and how they relate
2. **Manual Wiring**: Every project requires boilerplate `afterEvaluate` blocks
3. **Error-Prone**: Easy to forget dependency wiring, leading to race conditions
4. **No Conditional Logic**: Can't express "publish only if tests pass" in DSL
5. **Duplication**: Image reference appears in multiple places:
   - `docker.images.webApp.imageName = 'example-web-app'`
   - Compose file: `image: example-web-app:latest`
   - Manual task wiring: `dockerBuildWebApp` → `composeUpWebAppTest`

## Findings and Recommendations

### Finding 1: The Design Is Correct... For Its Original Scope

The **separation of concerns** is architecturally sound:
- `docker` → Image lifecycle (build, distribute)
- `dockerTest` → Container lifecycle (test, orchestrate)

This follows **SOLID principles** (Single Responsibility Principle).

### Finding 2: The Design Is Incomplete... For Real-World Usage

The design **fails to address** the most common CI/CD pattern:
- Build → Test → Conditional Publish

This is not a "nice-to-have" - it's **industry-standard practice**.

### Finding 3: Integration Test Examples Expose the Gap

The fact that **EVERY integration test example** requires manual wiring demonstrates that the plugin is **missing a
first-class integration pattern**.

## Proposed Solutions

### Option A: Embed Testing in ImageSpec (High Integration)

**Description**: Add first-class testing support directly to the `docker` DSL.

**Example DSL:**
```groovy
docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['latest', '1.0.0']
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }

            // NEW: First-class testing support
            test {
                composeFile = file('src/integrationTest/resources/compose/app.yml')
                waitForHealthy {
                    waitForServices.set(['my-app'])
                    timeoutSeconds.set(60)
                }
                testTask = tasks.named('integrationTest')
            }

            // These only run if test succeeds
            additionalTags = ['stable', 'production']

            save {
                compression.set(GZIP)
                outputFile.set(file('build/my-app.tar.gz'))
            }

            publish {
                to('production') {
                    registry.set('ghcr.io')
                    publishTags.set(['stable'])
                }
            }
        }
    }
}
```

**Benefits:**
- ✅ Single location for complete image lifecycle
- ✅ Built-in test → publish dependency
- ✅ Conditional operations (only run if tests pass)
- ✅ Eliminates manual wiring

**Challenges:**
- ❌ Breaks existing separation of concerns
- ❌ Increases complexity of ImageSpec
- ❌ May confuse users who only want build OR test (not both)
- ❌ Requires significant refactoring

**Recommendation**: Consider for major version (2.0.0) with migration guide.

---

### Option B: Add Cross-Reference Support (Moderate Integration)

**Description**: Link `docker` and `dockerTest` DSLs with explicit references.

**Example DSL:**
```groovy
docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['latest', '1.0.0']
            contextTask = tasks.register('prepareContext', Copy) { /* ... */ }

            // Link to test stack
            testStack = dockerTest.composeStacks.getByName('myAppTest')
        }
    }
}

dockerTest {
    composeStacks {
        myAppTest {
            files.from('compose.yml')
            testedImage = docker.images.getByName('myApp')  // Bi-directional link
        }
    }
}
```

**Benefits:**
- ✅ Maintains separation of concerns
- ✅ Explicit linkage for users who need it
- ✅ Plugin can auto-wire dependencies

**Challenges:**
- ❌ Still requires two DSL blocks
- ❌ Bi-directional references can be complex
- ❌ May introduce circular dependencies
- ❌ Moderate refactoring required

**Recommendation**: Good middle-ground option; consider for 1.x release.

---

### Option C: Introduce Pipeline/Workflow DSL (New Abstraction)

**Description**: Create a new top-level DSL for modeling complete workflows.

**Example DSL:**
```groovy
dockerWorkflows {
    myAppPipeline {
        // Step 1: Build
        build {
            image = docker.images.myApp
        }

        // Step 2: Test
        test {
            stack = dockerTest.composeStacks.myAppTest
        }

        // Step 3: Conditional operations
        onTestSuccess {
            addTags(['stable', 'production'])
            save { /* ... */ }
            publish { /* ... */ }
        }
    }
}
```

**Benefits:**
- ✅ Explicit workflow modeling
- ✅ Clear build → test → publish pipeline
- ✅ Maintains separation of docker and dockerTest
- ✅ Extensible for other workflow patterns

**Challenges:**
- ❌ Adds third DSL (complexity)
- ❌ Major architectural change
- ❌ May be overengineered for simple use cases
- ❌ Significant development effort

**Recommendation**: Consider for major version (2.0.0) if workflow modeling becomes a core feature.

---

### Option D: Convention-Based Auto-Wiring (Minimal Change) **RECOMMENDED**

**Description**: Use intelligent naming conventions to auto-wire relationships.

**Example DSL:**
```groovy
docker {
    images {
        myApp { /* ... */ }
    }
}

dockerTest {
    composeStacks {
        myApp {  // << Same name! Plugin auto-wires
            files.from('compose.yml')
        }
    }
}

// Plugin automatically:
// 1. Detects matching names
// 2. Wires composeUpMyApp.dependsOn dockerBuildMyApp
// 3. Wires dockerPublishMyApp.dependsOn integrationTest (if exists)
```

**Benefits:**
- ✅ Minimal user code changes
- ✅ Backward compatible (opt-in via naming)
- ✅ Simple to understand and use
- ✅ Low development effort
- ✅ Can be implemented incrementally

**Challenges:**
- ⚠️ "Magic" behavior may surprise users (mitigate with clear documentation)
- ⚠️ Limited to simple cases (but covers 80% of use cases)
- ❌ Doesn't solve conditional operations (but reduces boilerplate significantly)

**Recommendation**: **Implement for 1.x release** as immediate improvement; document as best practice.

**Implementation Plan:**
1. Add `ConventionBasedWiringService` to detect matching names
2. Auto-wire tasks during `afterEvaluate` phase
3. Add configuration option to disable auto-wiring (for advanced users)
4. Update documentation with naming convention guidelines
5. Add examples demonstrating convention-based wiring

---

## Conclusion

### Core Issue Summary

The plugin treats testing as **orthogonal to image operations**, when in practice, testing is a **mandatory gate**
between build and publish in modern CI/CD.

### Impact Assessment

- **Current Design**: Works for simple scenarios, fails for real-world CI/CD
- **User Pain**: High cognitive load, manual wiring, error-prone
- **Evidence**: Every integration test example requires manual workaround
- **Severity**: Medium-High (affects common use cases)

### Recommended Path Forward

**Short-term (Low Risk, High ROI):**
1. **Implement Option D**: Convention-based auto-wiring for matching names
2. Document the pattern explicitly in usage guides
3. Add helper methods for common wiring scenarios
4. Target release: 1.1.0

**Long-term (Architectural Evolution):**
1. **Evaluate Option A or C**: First-class testing OR workflow DSL
2. Gather user feedback on convention-based approach
3. Design comprehensive solution for major version
4. Maintain backward compatibility with deprecation warnings
5. Target release: 2.0.0

### Next Steps

1. ✅ **Analysis Complete**: Document architectural limitation
2. ⬜ **Validate**: Gather feedback from real-world users
3. ⬜ **Prototype**: Implement Option D (convention-based auto-wiring)
4. ⬜ **Test**: Verify with integration test examples
5. ⬜ **Document**: Update usage guides with new patterns
6. ⬜ **Design**: Long-term solution for 2.0.0

## References

- **Integration Test Examples**: `plugin-integration-test/dockerTest/examples/`
- **Current Workarounds**: Every example's `build.gradle` file
- **DockerExtension**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerExtension.groovy`
- **DockerTestExtension**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerTestExtension.groovy`
- **GradleDockerPlugin**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy`

## Related Issues

- None currently tracked (this is the initial analysis)

## Version Information

- **Analysis Date**: 2025-01-23
- **Plugin Version**: 1.0.0
- **Status**: Analysis complete, awaiting design decision
