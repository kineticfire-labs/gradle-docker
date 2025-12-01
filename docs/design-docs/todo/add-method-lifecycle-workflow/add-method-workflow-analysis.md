# Analysis: Adding Method Lifecycle Support to dockerWorkflows DSL

**Status:** Analysis Complete - Ready for Implementation
**Date:** 2025-12-01
**Author:** Development Team
**Related Documents:**
- [Architectural Limitations Analysis](../architectural-limitations/architectural-limitations-analysis.md)
- [Architectural Limitations Plan](../architectural-limitations/architectural-limitations-plan.md)
- [Workflow Support Lifecycle](../workflow-lifecycle/workflow-support-lifecycle.md)
- [Why dockerWorkflows Cannot Support Method Lifecycle](../workflow-lifecycle/workflow-cannot-method-lifecycle.md)

---

## Executive Summary

The current `dockerWorkflows` DSL cannot support per-method container lifecycle because Gradle tasks execute once per
build invocation. This analysis explores alternatives to enable method-level container isolation while preserving the
pipeline's conditional post-test actions (tag/save/publish on success).

**Recommendation:** Option E (Enhanced Test Framework Integration) provides the best balance of user experience,
implementation complexity, and architectural fit.

---

## Problem Statement

### Current Architecture

```
dockerWorkflows Pipeline Execution:
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline (Gradle Task)                                    │
│    ├── BuildStepExecutor.execute()                              │
│    │      └── dockerBuildMyApp (runs ONCE)                      │
│    ├── TestStepExecutor.execute()                               │
│    │      ├── composeUpTestStack (runs ONCE)                    │
│    │      ├── integrationTest   (runs ONCE - all methods)       │
│    │      └── composeDownTestStack (runs ONCE)                  │
│    └── ConditionalExecutor.execute()                            │
│           └── dockerTagMyApp:tested (if tests passed)           │
└─────────────────────────────────────────────────────────────────┘
```

### Why Method Lifecycle Fails

For method lifecycle, we need:
```
Test Method 1: composeUp → run test → composeDown
Test Method 2: composeUp → run test → composeDown
Test Method 3: composeUp → run test → composeDown
```

But Gradle provides:
```
composeUp (ONCE) → all test methods → composeDown (ONCE)
```

**The gap**: Gradle has no mechanism to re-execute tasks between individual test method invocations within a single
test task execution.

### Impact

Users wanting per-method container isolation AND conditional post-test actions (tag/save/publish on success) are caught
between two incomplete solutions:

| Feature | dockerOrch + testIntegration | dockerWorkflows |
|---------|------------------------------|--------------------|
| Method lifecycle | ✅ | ❌ |
| Class lifecycle | ✅ | ✅ (with delegateStackManagement) |
| Conditional on test result | ❌ | ✅ |
| Tag/Save/Publish on success | ❌ | ✅ |
| Pipeline orchestration | ❌ | ✅ |

---

## Alternative Solutions Analysis

### Option A: Test Framework Hooks with Pipeline Result Aggregation

**Concept**: Let the test framework (JUnit 5 / Spock) manage container lifecycle per method, then aggregate results
back to the pipeline for conditional actions.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline                                                  │
│    ├── BuildStepExecutor.execute()                              │
│    │      └── dockerBuildMyApp                                  │
│    ├── TestStepExecutor.execute()                               │
│    │      ├── [NO composeUp - framework manages]                │
│    │      ├── integrationTest (each method does compose up/down)│
│    │      │      ├── @BeforeEach: ComposeLifecycleManager.up()  │
│    │      │      ├── test method                                │
│    │      │      └── @AfterEach: ComposeLifecycleManager.down() │
│    │      └── [NO composeDown - framework manages]              │
│    └── ConditionalExecutor.execute()                            │
│           └── dockerTagMyApp:tested (if all tests passed)       │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation**:
1. Add `lifecycle = 'method'` property to `TestStepSpec`
2. When `lifecycle = 'method'`:
   - Skip pipeline's composeUp/composeDown calls
   - Set system properties for test framework to detect
   - Test uses `@ComposeUp` (Spock) or `@ExtendWith(ComposeExtension.class)` (JUnit 5)
3. Pipeline captures test results regardless of lifecycle mode

**Pros**:
- ✅ Leverages existing `@ComposeUp` implementation
- ✅ Works with current architecture
- ✅ Configuration cache compatible (no Project references)
- ✅ Clear separation: framework handles lifecycle, pipeline handles conditional actions

**Cons**:
- ⚠️ Requires coordination between DSL config and test annotations
- ⚠️ User must add annotation to test class when using method lifecycle
- ⚠️ Potential for misconfiguration (DSL says method, test doesn't have annotation)

**Effort**: ~3-4 days

---

### Option B: Custom Test Executor with Embedded Compose Control

**Concept**: Replace Gradle's test task execution with a custom executor that controls compose lifecycle per test
method.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline                                                  │
│    ├── BuildStepExecutor.execute()                              │
│    ├── TestStepExecutor.execute()                               │
│    │      └── CustomMethodLifecycleTestExecutor                 │
│    │            ├── Discover test classes/methods               │
│    │            ├── For each method:                            │
│    │            │      ├── composeUp (via ComposeService)       │
│    │            │      ├── Run single test method               │
│    │            │      └── composeDown (via ComposeService)     │
│    │            └── Aggregate results                           │
│    └── ConditionalExecutor.execute()                            │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation**:
1. Create `CustomMethodLifecycleTestExecutor` that:
   - Uses JUnit Platform Launcher API to discover and run tests
   - Wraps each test method execution with compose up/down
   - Injects `ComposeService` for container control
2. Replace standard test task execution when `lifecycle = 'method'`

**Pros**:
- ✅ Complete control over execution order
- ✅ No test annotations required
- ✅ Works for both JUnit 5 and Spock (via JUnit Platform)

**Cons**:
- ❌ Complex implementation (replicate test execution logic)
- ❌ Must handle test filtering, parallel execution, reports
- ❌ Configuration cache challenges (JUnit launcher may hold non-serializable state)
- ❌ High maintenance burden

**Effort**: ~10-15 days

---

### Option C: Gradle Worker API with Per-Method Compose Control

**Concept**: Use Gradle's Worker API to execute tests in isolated workers, each controlling its own compose lifecycle.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline                                                  │
│    ├── BuildStepExecutor.execute()                              │
│    ├── TestStepExecutor.execute()                               │
│    │      └── WorkerExecutor.submit(TestMethodWorker) per method│
│    │            ├── Worker 1: up → test1 → down                 │
│    │            ├── Worker 2: up → test2 → down                 │
│    │            └── Worker 3: up → test3 → down                 │
│    └── ConditionalExecutor.execute() (after all workers done)   │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation**:
1. Discover test methods at configuration time or execution start
2. Submit each test method as a separate Worker
3. Each worker runs compose up/down around its test

**Pros**:
- ✅ Gradle-native parallelism control
- ✅ Configuration cache compatible (Workers are serializable)
- ✅ Clean isolation per test method

**Cons**:
- ❌ Port conflicts when running parallel workers (each needs unique compose project)
- ❌ Complex test discovery (need to enumerate methods before execution)
- ❌ Sequential execution required to avoid port conflicts, negating Worker benefits
- ❌ Report aggregation complexity

**Effort**: ~8-12 days

---

### Option D: Process-Per-Test with Result File Aggregation

**Concept**: Fork a new Gradle process for each test method, each with its own compose lifecycle.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  runMyPipeline                                                  │
│    ├── BuildStepExecutor.execute()                              │
│    ├── TestStepExecutor.execute()                               │
│    │      ├── Discover test methods                             │
│    │      ├── For each method:                                  │
│    │      │      └── ExecOperations.exec {                      │
│    │      │            ./gradlew test --tests "Class.method"    │
│    │      │            // includes composeUp/Down via task deps │
│    │      │          }                                          │
│    │      └── Aggregate exit codes / test results               │
│    └── ConditionalExecutor.execute()                            │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation**:
1. Use `ExecOperations` to spawn Gradle processes
2. Each process runs single test with compose task dependencies
3. Collect results from JUnit XML reports

**Pros**:
- ✅ Complete isolation (separate JVM, separate compose project)
- ✅ Leverages existing compose task infrastructure
- ✅ Configuration cache compatible (ExecOperations is safe)

**Cons**:
- ❌ Very slow (JVM startup per test method)
- ❌ Complex test discovery
- ❌ Gradle daemon overhead per invocation
- ❌ Configuration cache invalidated frequently

**Effort**: ~6-8 days

---

### Option E: Enhanced Test Framework Integration (Recommended)

**Concept**: Extend current test framework integration to communicate lifecycle intent from DSL to test runtime, with
automatic annotation injection.

**Architecture**:
```
┌─────────────────────────────────────────────────────────────────┐
│  Configuration Time:                                            │
│    dockerWorkflows { pipelines { ci {                           │
│        test {                                                   │
│            lifecycle = 'method'  // NEW                         │
│            testTaskName = 'integrationTest'                     │
│        }                                                        │
│    }}}                                                          │
│                                                                 │
│  → Sets system property: docker.workflow.lifecycle=method       │
│  → Sets system property: docker.workflow.stack=testStack        │
├─────────────────────────────────────────────────────────────────┤
│  Test Runtime:                                                  │
│    Enhanced @ComposeUp / JUnit extension reads system props     │
│    → Detects lifecycle=method                                   │
│    → Auto-applies method lifecycle behavior                     │
│    → Reports results to pipeline via shared file                │
├─────────────────────────────────────────────────────────────────┤
│  Pipeline Execution:                                            │
│    TestStepExecutor detects lifecycle=method                    │
│    → Skips composeUp/Down (framework handles)                   │
│    → Runs test task                                             │
│    → Reads aggregated result                                    │
│    → Passes to ConditionalExecutor                              │
└─────────────────────────────────────────────────────────────────┘
```

**Key Innovation**: The test framework extension auto-detects pipeline context via system properties and applies
appropriate lifecycle without explicit annotation changes.

**Implementation Details**:

1. **Add `lifecycle` Property to TestStepSpec**:
   ```groovy
   // TestStepSpec.groovy
   abstract Property<String> getLifecycle()  // 'suite', 'class', 'method'

   // Constructor
   lifecycle.convention('suite')
   ```

2. **Modify TestStepExecutor**:
   ```groovy
   void execute(TestStepSpec spec, PipelineContext ctx) {
       def lifecycle = spec.lifecycle.getOrElse('suite')

       if (lifecycle == 'method') {
           // Set system properties for test framework
           setSystemProperty('docker.workflow.lifecycle', 'method')
           setSystemProperty('docker.workflow.stack', spec.stack.get().name)
           setSystemProperty('docker.workflow.compose.files', getComposeFiles(spec))

           // Skip pipeline compose management
           executeTestTask(spec.testTaskName.get())
       } else {
           // Existing suite/class behavior
           executeComposeUp()
           executeTestTask()
           executeComposeDown()
       }

       captureResults()
   }
   ```

3. **Enhance ComposeExtension (JUnit 5) / @ComposeUp (Spock)**:
   ```groovy
   // In extension beforeEach/setup:
   def lifecycle = System.getProperty('docker.workflow.lifecycle')
   if (lifecycle == 'method') {
       def stackName = System.getProperty('docker.workflow.stack')
       composeService.up(stackName)
   }

   // In extension afterEach/cleanup:
   if (lifecycle == 'method') {
       composeService.down(stackName)
   }
   ```

4. **Handle Port Conflicts**:
   ```groovy
   // Use unique project name per test method to avoid port conflicts
   def projectName = "${stackName}-${testClassName}-${testMethodName}"
   composeService.up(stackName, projectName)
   ```

**Pros**:
- ✅ Minimal user-facing change (just set `lifecycle = 'method'` in DSL)
- ✅ No required test annotation changes
- ✅ Configuration cache compatible
- ✅ Leverages existing compose service infrastructure
- ✅ Works for both JUnit 5 and Spock
- ✅ Maintains pipeline's conditional action capability

**Cons**:
- ⚠️ Requires enhancement to existing test extensions
- ⚠️ System property coordination (could fail if misconfigured)
- ⚠️ Sequential test execution required to avoid port conflicts

**Effort**: ~5-7 days

---

## Evaluation Criteria Matrix

| Criterion | Weight | Option A | Option B | Option C | Option D | Option E |
|-----------|--------|----------|----------|----------|----------|----------|
| Implementation complexity | 20% | 3/5 | 1/5 | 2/5 | 2/5 | 4/5 |
| User experience (simplicity) | 25% | 3/5 | 5/5 | 4/5 | 3/5 | 5/5 |
| Configuration cache compat | 15% | 5/5 | 2/5 | 4/5 | 4/5 | 5/5 |
| Maintenance burden | 15% | 4/5 | 1/5 | 2/5 | 3/5 | 4/5 |
| Works with JUnit & Spock | 10% | 5/5 | 4/5 | 4/5 | 5/5 | 5/5 |
| Performance | 10% | 4/5 | 3/5 | 3/5 | 1/5 | 4/5 |
| Risk level | 5% | 4/5 | 2/5 | 2/5 | 3/5 | 4/5 |
| **Weighted Score** | **100%** | **3.65** | **2.55** | **3.05** | **2.80** | **4.40** |

---

## Recommendation: Option E (Enhanced Test Framework Integration)

### Rationale

1. **Best User Experience**: Users only need to set `lifecycle = 'method'` in the DSL—no test code changes required
   in most cases.

2. **Leverages Existing Infrastructure**: Builds on the already-working `@ComposeUp` and `ComposeExtension`
   implementations rather than creating parallel systems.

3. **Configuration Cache Safe**: Uses system properties (safe) rather than holding non-serializable state.

4. **Clear Architecture**: Maintains separation of concerns:
   - DSL declares intent
   - Test framework executes lifecycle
   - Pipeline handles conditional actions

5. **Incremental Implementation**: Can be built in phases:
   - Phase 1: Add `lifecycle` property and system property passing
   - Phase 2: Enhance Spock extension
   - Phase 3: Enhance JUnit 5 extension
   - Phase 4: Integration tests and documentation

### Target DSL Usage

```groovy
dockerWorkflows {
    pipelines {
        ciPipeline {
            description = 'CI pipeline with method-level test isolation'

            build {
                image = docker.images.myApp
            }

            test {
                stack = dockerOrch.composeStacks.testStack
                testTaskName = 'integrationTest'
                lifecycle = 'method'  // NEW: enables per-method container lifecycle
            }

            onTestSuccess {
                additionalTags = ['tested', 'approved']
                publish {
                    to('production') {
                        registry = 'ghcr.io'
                        namespace = 'myorg'
                    }
                }
            }
        }
    }
}
```

---

## Implementation Plan

### Step Overview

```
Step 1: Add lifecycle property to TestStepSpec (0.5 day)
   └── Property<String> lifecycle with convention('suite')

Step 2: Modify TestStepExecutor for method lifecycle (1 day)
   ├── Detect lifecycle = 'method'
   ├── Set system properties
   └── Skip internal compose management

Step 3: Enhance Spock @ComposeUp extension (1.5 days)
   ├── Read system properties in setup/cleanup
   ├── Handle per-method compose up/down
   └── Generate unique project names

Step 4: Enhance JUnit 5 ComposeExtension (1.5 days)
   ├── Read system properties in beforeEach/afterEach
   ├── Handle per-method compose up/down
   └── Generate unique project names

Step 5: Unit and functional tests (1 day)
   ├── TestStepSpecTest for lifecycle property
   ├── TestStepExecutorTest for method lifecycle handling
   └── Functional tests for DSL parsing

Step 6: Integration test scenarios (1 day)
   ├── scenario-7: Method lifecycle with Spock
   └── scenario-8: Method lifecycle with JUnit 5

Step 7: Documentation (0.5 day)
   └── Update usage-docker-workflows.md
```

**Total Estimated Effort: 7 days**

### Key Design Decisions

1. **Sequential Test Execution**: When `lifecycle = 'method'`, tests must run sequentially (not in parallel) to avoid
   port conflicts. This is acceptable for method-lifecycle scenarios where isolation is prioritized over speed.

2. **Unique Project Names**: Each test method gets a unique compose project name (`{stack}-{class}-{method}`) to
   ensure complete isolation.

3. **Backward Compatibility**: Default `lifecycle = 'suite'` preserves existing behavior.

4. **Validation**: Add PipelineValidator check that warns if `lifecycle = 'method'` but test parallelism is enabled.

---

## Files to Modify/Create

### Production Code

| File | Change Type | Description |
|------|-------------|-------------|
| `TestStepSpec.groovy` | Modify | Add `lifecycle` property |
| `TestStepExecutor.groovy` | Modify | Handle method lifecycle flow |
| `PipelineValidator.groovy` | Modify | Validate lifecycle configuration |
| `ComposeUpExtension.groovy` (Spock) | Modify | Read system props, method lifecycle |
| `ComposeExtension.java` (JUnit 5) | Modify | Read system props, method lifecycle |

### Test Code

| File | Change Type | Description |
|------|-------------|-------------|
| `TestStepSpecTest.groovy` | Modify | Tests for lifecycle property |
| `TestStepExecutorTest.groovy` | Modify | Tests for method lifecycle |
| `PipelineValidatorTest.groovy` | Modify | Tests for lifecycle validation |
| `MethodLifecycleFunctionalTest.groovy` | Create | DSL parsing tests |

### Integration Tests

| Directory | Change Type | Description |
|-----------|-------------|-------------|
| `scenario-7-method-lifecycle-spock/` | Create | Spock method lifecycle demo |
| `scenario-8-method-lifecycle-junit/` | Create | JUnit 5 method lifecycle demo |

### Documentation

| File | Change Type | Description |
|------|-------------|-------------|
| `usage-docker-workflows.md` | Modify | Document lifecycle option |
| `workflow-cannot-method-lifecycle.md` | Modify | Update to reflect new capability |

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Port conflicts during parallel test execution | Validate and warn when `lifecycle = 'method'` and parallel enabled |
| System property not reaching test JVM | Use `Test.systemProperty()` API, document in troubleshooting |
| ComposeService not available in test runtime | Inject via service locator pattern, fallback to Docker Compose CLI |
| Performance degradation | Document that method lifecycle is slower; recommend class for speed |

---

## Gradle 9/10 Configuration Cache Compatibility

All proposed changes maintain configuration cache compatibility:

1. **TestStepSpec.lifecycle**: Uses `Property<String>` (lazy evaluation, serializable)
2. **System properties**: Set via `Test.systemProperty()` which is configuration cache safe
3. **TestStepExecutor**: No Project references captured; uses TaskContainer lookup
4. **Test extensions**: Read system properties at execution time, not configuration time

---

## Comparison: Before and After

### Before (Current Limitation)

Users wanting method lifecycle with conditional actions must choose:

**Option 1: Use dockerOrch (method lifecycle, NO conditional actions)**
```groovy
dockerOrch {
    composeStacks {
        testStack { files.from('compose.yml') }
    }
}

testIntegration {
    usesCompose(integrationTest, 'testStack', 'method')
}

// Must manually run: ./gradlew dockerBuild integrationTest
// Then manually: ./gradlew dockerTag -PadditionalTags=tested (if tests passed)
```

**Option 2: Use dockerWorkflows (conditional actions, NO method lifecycle)**
```groovy
dockerWorkflows {
    pipelines {
        ci {
            build { image = docker.images.myApp }
            test {
                stack = dockerOrch.composeStacks.testStack
                testTaskName = 'integrationTest'
                // lifecycle = 'method' NOT AVAILABLE
            }
            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}
```

### After (With Option E Implementation)

**Both method lifecycle AND conditional actions:**
```groovy
dockerWorkflows {
    pipelines {
        ci {
            build { image = docker.images.myApp }
            test {
                stack = dockerOrch.composeStacks.testStack
                testTaskName = 'integrationTest'
                lifecycle = 'method'  // NEW: enables per-method isolation
            }
            onTestSuccess {
                additionalTags = ['tested']
                publish { ... }
            }
        }
    }
}
```

---

## Lifecycle Options Summary

After implementation, the following lifecycle options will be available:

| Lifecycle | Containers Start | Containers Stop | Use Case |
|-----------|------------------|-----------------|----------|
| `'suite'` (default) | Before all tests | After all tests | Fast, shared state OK |
| `'class'` | Before each test class | After each test class | Isolation per class |
| `'method'` | Before each test method | After each test method | Complete isolation |

All three lifecycles will work with the full pipeline capabilities:
- Build step execution
- Test step execution
- Conditional actions on test success (tag, save, publish)
- Conditional actions on test failure
- Always step (cleanup)

---

## Success Criteria

The implementation is complete when:

- [ ] `lifecycle` property added to TestStepSpec with `convention('suite')`
- [ ] TestStepExecutor handles `lifecycle = 'method'` by setting system properties
- [ ] Spock `@ComposeUp` extension respects `docker.workflow.lifecycle` system property
- [ ] JUnit 5 `ComposeExtension` respects `docker.workflow.lifecycle` system property
- [ ] Unit tests achieve 100% coverage on new code
- [ ] Functional tests verify DSL parsing for lifecycle option
- [ ] Integration test scenario-7 (Spock method lifecycle) passes
- [ ] Integration test scenario-8 (JUnit 5 method lifecycle) passes
- [ ] No Docker containers remain after integration tests
- [ ] Configuration cache works with method lifecycle scenarios
- [ ] Documentation updated with lifecycle option details

---

## Conclusion

Option E (Enhanced Test Framework Integration) provides the best balance of user experience, implementation complexity,
and architectural fit. It builds on existing infrastructure, maintains configuration cache compatibility, and requires
minimal user-facing changes while delivering the requested method lifecycle capability.

The key insight is that **the test framework already has hooks at the method level** (JUnit's `@BeforeEach`/`@AfterEach`,
Spock's `setup()`/`cleanup()`). By passing lifecycle intent from the DSL to the test framework via system properties,
we bridge the gap between Gradle's task-level execution model and the test framework's method-level execution model.

This approach preserves the pipeline's ability to perform conditional post-test actions while enabling complete
container isolation per test method—achieving the best of both worlds.

---

## Related Documents

- [Architectural Limitations Analysis](../architectural-limitations/architectural-limitations-analysis.md)
- [Architectural Limitations Plan](../architectural-limitations/architectural-limitations-plan.md)
- [Workflow Support Lifecycle](../workflow-lifecycle/workflow-support-lifecycle.md)
- [Why dockerWorkflows Cannot Support Method Lifecycle](../workflow-lifecycle/workflow-cannot-method-lifecycle.md)
- [Gradle 9/10 Compatibility](../../gradle-9-and-10-compatibility.md)
- [Spock/JUnit Test Extensions](../../../usage/spock-junit-test-extensions.md)
