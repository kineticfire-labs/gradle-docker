# Feasibility Analysis: Pipeline/Workflow DSL (Option C)

**Document Version:** 1.0
**Date:** 2025-01-23
**Status:** Analysis Complete
**Related Documents:**
- [Architectural Limitations Analysis](architectural-limitations-analysis.md)
- [Option C: Objective Approach Usage](architectural-limitations-objective-approach-usage.md)

---

## Executive Summary

This document analyzes the **feasibility and implementation challenges** of Option C (Pipeline/Workflow DSL) proposed to
address the architectural limitations in the gradle-docker plugin. The analysis examines three key challenges:

1. **API Duplication** - The workflow DSL replicates `save` and `publish` APIs from the docker DSL
2. **Implementation Complexity** - Estimated 3000-5000 LOC of new code required
3. **Conditional Logic in Gradle** - Gradle's task graph model makes conditional execution challenging

**Key Findings:**
- ✅ **API Duplication is acceptable** - Partial duplication with 90%+ code reuse
- ✅ **Complexity is manageable** - Can be decomposed into testable layers
- ✅ **Conditional logic is solvable** - Hybrid orchestration approach works within Gradle constraints

**Overall Verdict:** Option C is **FEASIBLE and PRACTICAL** with proper architectural approach.

**Estimated Effort:** 7-10 weeks (development + testing + documentation)

---

## Table of Contents

1. [Challenge 1: API Duplication](#challenge-1-api-duplication)
2. [Challenge 2: Implementation Complexity](#challenge-2-implementation-complexity)
3. [Challenge 3: Conditional Logic in Gradle](#challenge-3-conditional-logic-in-gradle)
4. [Recommended Implementation Approach](#recommended-implementation-approach)
5. [Risk Assessment](#risk-assessment)
6. [Conclusion](#conclusion)

---

## Challenge 1: API Duplication

### The Issue

**Two ways to save/publish an image:**

```groovy
// Approach 1: Direct in docker DSL (no testing)
docker {
    images {
        myApp {
            save { compression.set(GZIP) }
            publish { to('prod') { ... } }
        }
    }
}

// Approach 2: In workflow DSL (after testing)
dockerWorkflows {
    pipeline {
        onTestSuccess {
            save { compression.set(GZIP) }
            publish { to('prod') { ... } }
        }
    }
}
```

### Analysis: Acceptable Duplication

#### 1. Partial Duplication (DSL Syntax Only)

The duplication exists at the **DSL syntax level**, NOT the implementation level:

```groovy
// SAME DSL block syntax:
save {
    compression.set(GZIP)
    outputFile.set(file('...'))
}

publish {
    to('prod') {
        registry.set('...')
        publishTags.set([...])
    }
}
```

**But underneath:**
- Different context (direct vs workflow)
- Different timing (build-time vs post-test)
- Different conditional logic (unconditional vs conditional)

#### 2. Implementation Code Reuse (~90%)

**Architecture for maximum code reuse:**

```groovy
// Shared Spec Classes (EXISTING - 100% reused)
class SaveSpec {
    Property<CompressionType> compression
    RegularFileProperty outputFile
    // ... implementation
}

class PublishSpec {
    NamedDomainObjectContainer<PublishTargetSpec> to
    // ... implementation
}

// docker DSL uses directly (EXISTING)
class ImageSpec {
    SaveSpec save
    PublishSpec publish
}

// workflow DSL references (NEW - reuses existing specs)
class SuccessStepSpec {
    SaveSpec save        // REUSE existing spec
    PublishSpec publish  // REUSE existing spec

    // Only new code: conditional execution wrapper
    Property<Boolean> runOnlyIfTestsPass
}

// SAME task implementation (EXISTING - 90% reused)
class DockerSaveTask {
    void executeSave(SaveSpec saveSpec, String imageRef) {
        // Core logic - used by BOTH docker DSL and workflow DSL
    }
}

class DockerPublishTask {
    void executePublish(PublishSpec publishSpec, String imageRef) {
        // Core logic - used by BOTH docker DSL and workflow DSL
    }
}
```

**Code reuse breakdown:**
- Spec classes: **100% reused** (SaveSpec, PublishSpec)
- Task implementation: **90% reused** (core Docker operations)
- New code: **Only workflow orchestration** (~300-500 LOC)

**Result:** NOT duplicating implementation, only providing two entry points to the same functionality.

#### 3. Expected API Pattern (Industry Precedent)

**Analogy: Gradle's built-in design patterns**

Gradle itself has similar "duplication":

```groovy
// Approach 1: Direct configuration
tasks.register('myTask', Copy) {
    from 'src'
    into 'dest'
}

// Approach 2: Via distribution plugin
distributions {
    main {
        contents {
            from 'src'  // SAME API, different context
            into 'dest'
        }
    }
}
```

**Both use the same CopySpec API, but in different contexts.**

**For docker plugin:**
- `docker.images.myApp.save {}` = "Save after build (no testing)"
- `dockerWorkflows.pipeline.onTestSuccess.save {}` = "Save after tests pass"

**Users understand context-dependent APIs:**
- Same operation (save/publish)
- Different trigger points (immediate vs conditional)
- Different use cases (no-test vs tested workflow)

### Mitigation Strategies

1. **Clear documentation** - Explain when to use each approach
2. **Consistent API** - Ensure both save/publish blocks use identical syntax
3. **Shared validation** - Use same validation logic for both contexts
4. **Error messages** - Guide users to the appropriate approach based on use case

### Verdict: API Duplication

**✅ ACCEPTABLE** - The duplication is:
- Partial (syntax only, not implementation)
- Expected (industry pattern for context-dependent APIs)
- Reusable (90%+ code reuse)
- Valuable (provides flexibility for different workflows)

**Risk Level:** LOW

---

## Challenge 2: Implementation Complexity

### Complexity Breakdown

**Estimated 3000-5000 LOC production code + 6000 LOC test code = 9000 LOC total**

### Layer 1: Extension Layer (~800 LOC)

**Purpose:** DSL parsing and configuration

```groovy
// New top-level extension
class DockerWorkflowsExtension {
    // Container for pipelines
    NamedDomainObjectContainer<PipelineSpec> pipelines

    // DSL configuration
    void pipelines(Action<NamedDomainObjectContainer<PipelineSpec>> action)
}

// Pipeline specification
class PipelineSpec {
    String name
    Property<String> description

    // Workflow steps
    BuildStepSpec build
    TestStepSpec test
    SuccessStepSpec onTestSuccess
    FailureStepSpec onTestFailure
    AlwaysStepSpec always

    // DSL methods
    void build(Action<BuildStepSpec> action)
    void test(Action<TestStepSpec> action)
    void onTestSuccess(Action<SuccessStepSpec> action)
    // ... etc
}

// Build step configuration
class BuildStepSpec {
    Property<ImageSpec> image  // Reference to docker.images.*
    MapProperty<String, String> buildArgs  // Override build args
    // Hooks
    Action<Void> beforeBuild
    Action<Void> afterBuild
}

// Test step configuration
class TestStepSpec {
    Property<ComposeStackSpec> stack  // Reference to dockerTest.composeStacks.*
    Property<Task> testTask  // Reference to test task
    Property<Integer> timeoutMinutes
    Property<Integer> retryOnFailure
    // Hooks
    Action<Void> beforeTest
    Action<TestResult> afterTest
}

// Success step configuration
class SuccessStepSpec {
    ListProperty<String> additionalTags
    SaveSpec save      // REUSE existing SaveSpec
    PublishSpec publish  // REUSE existing PublishSpec
    Action<Void> afterSuccess
}

// Similar for FailureStepSpec, AlwaysStepSpec
```

**Complexity factors:**
- Multiple nested DSL levels (pipelines → steps → operations)
- Cross-references to existing DSLs (docker.images, dockerTest.composeStacks)
- Provider API throughout for Gradle 9/10 compatibility

**Decomposition strategy:**
1. **Start with skeleton** - Empty extensions with basic structure (100 LOC)
2. **Add DSL parsing** - Configure specs from user input (200 LOC)
3. **Add validation** - Ensure references are valid (150 LOC)
4. **Add hooks** - beforeBuild, afterTest, etc. (100 LOC)
5. **Wire to existing DSLs** - Connect to docker/dockerTest (250 LOC)

**Testing strategy:**
- Unit tests for each spec class (50 tests, ~1000 LOC)
- Functional tests for DSL parsing (20 tests, ~500 LOC)
- Integration tests for complete workflows (10 tests, ~1000 LOC)

### Layer 2: Task Orchestration Layer (~1200 LOC)

**Purpose:** Execute workflow steps in order with conditional logic

```groovy
// Master pipeline task
class PipelineRunTask extends DefaultTask {
    @Input
    Property<String> pipelineName

    @Internal
    Property<PipelineSpec> pipelineSpec

    @Nested
    Property<BuildStepSpec> buildStep

    @Nested
    Property<TestStepSpec> testStep

    @Nested
    Property<SuccessStepSpec> successStep

    @TaskAction
    void runPipeline() {
        // 1. Execute build step
        executeBuildStep()

        // 2. Execute test step
        def testResult = executeTestStep()

        // 3. Execute conditional steps based on test result
        if (testResult.success) {
            executeSuccessStep()
        } else {
            executeFailureStep()
        }

        // 4. Execute always step (cleanup)
        executeAlwaysStep()
    }

    private void executeBuildStep() {
        // Invoke existing dockerBuild* task
        def buildTask = project.tasks.named("dockerBuild${buildStep.image.name.capitalize()}")
        buildTask.get().execute()  // Direct invocation
    }

    private TestResult executeTestStep() {
        // 1. Invoke composeUp* task
        def composeUpTask = project.tasks.named("composeUp${testStep.stack.name.capitalize()}")
        composeUpTask.get().execute()

        // 2. Invoke test task
        def testTask = testStep.testTask.get()
        testTask.execute()

        // 3. Capture test result
        def result = new TestResult(
            success: testTask.state.failure == null,
            failureCount: testTask.testResultsDir ? countFailures(testTask.testResultsDir) : 0
        )

        // 4. Invoke composeDown* task (cleanup)
        def composeDownTask = project.tasks.named("composeDown${testStep.stack.name.capitalize()}")
        composeDownTask.get().execute()

        return result
    }

    private void executeSuccessStep() {
        // Apply additional tags
        if (successStep.additionalTags.present) {
            applyAdditionalTags(successStep.additionalTags.get())
        }

        // Execute save
        if (successStep.save != null) {
            executeSaveOperation(successStep.save)
        }

        // Execute publish
        if (successStep.publish != null) {
            executePublishOperation(successStep.publish)
        }
    }
}
```

**Complexity factors:**
- Task orchestration logic (invoke other tasks programmatically)
- Test result capture and propagation
- Conditional execution based on test outcome
- Error handling and cleanup

**Decomposition strategy:**
1. **Start with simple orchestration** - Build → Test only (200 LOC)
2. **Add test result capture** - Detect pass/fail (150 LOC)
3. **Add conditional execution** - Success/failure paths (200 LOC)
4. **Add operations** - Tag, save, publish (400 LOC)
5. **Add cleanup** - Always block, error handling (250 LOC)

**Testing strategy:**
- Unit tests for orchestration logic (40 tests, ~800 LOC)
- Functional tests for task invocation (15 tests, ~600 LOC)
- Integration tests for complete pipelines (10 tests, ~1000 LOC)

### Layer 3: Conditional Execution Logic (~600 LOC)

**Purpose:** Evaluate conditions and execute operations conditionally

```groovy
// Test result capture
class TestResultCapture {
    static TestResult captureResult(Task testTask) {
        def result = new TestResult(
            taskName: testTask.name,
            success: testTask.state.failure == null,
            executed: testTask.state.executed,
            upToDate: testTask.state.upToDate,
            skipped: testTask.state.skipped
        )

        // Read test results from XML/JSON reports
        if (testTask.testResultsDir?.exists()) {
            result.failureCount = countFailures(testTask.testResultsDir)
            result.totalCount = countTotal(testTask.testResultsDir)
        }

        return result
    }

    private static int countFailures(File resultsDir) {
        // Parse JUnit XML or other test result formats
        // Count test failures
    }
}

// Conditional task execution
class ConditionalExecutor {
    static void executeIfConditionMet(
        Task task,
        Provider<Boolean> condition,
        Action<Task> action
    ) {
        if (condition.get()) {
            action.execute(task)
        } else {
            logger.info("Skipping ${task.name}: condition not met")
        }
    }
}

// Tag application
class TagApplicator {
    void applyAdditionalTags(
        DockerService dockerService,
        String baseImageRef,
        List<String> additionalTags
    ) {
        additionalTags.each { tag ->
            def targetRef = buildImageReference(baseImageRef, tag)
            dockerService.tagImage(baseImageRef, targetRef)
            logger.lifecycle("Tagged ${baseImageRef} as ${targetRef}")
        }
    }
}
```

**Complexity factors:**
- Test result parsing (JUnit XML, TestNG, etc.)
- Conditional logic evaluation
- State management across task executions

**Decomposition strategy:**
1. **Test result capture** - Parse test outputs (200 LOC)
2. **Condition evaluation** - Boolean logic (100 LOC)
3. **Operation execution** - Tag, save, publish (200 LOC)
4. **Error handling** - Graceful failures (100 LOC)

**Testing strategy:**
- Unit tests for each component (30 tests, ~600 LOC)
- Mock test task states (10 tests, ~200 LOC)
- Integration tests with real test tasks (5 tests, ~400 LOC)

### Layer 4: Plugin Integration (~400 LOC)

**Purpose:** Register workflow extension and wire to existing plugin infrastructure

```groovy
// In GradleDockerPlugin.groovy
class GradleDockerPlugin {
    void apply(Project project) {
        // ... existing code ...

        // Register workflow extension
        def workflowExt = project.extensions.create(
            'dockerWorkflows',
            DockerWorkflowsExtension,
            project.objects
        )

        // Register workflow task creation rules
        project.afterEvaluate {
            registerWorkflowTasks(project, workflowExt, dockerExt, dockerTestExt)
        }
    }

    private void registerWorkflowTasks(
        Project project,
        DockerWorkflowsExtension workflowExt,
        DockerExtension dockerExt,
        DockerTestExtension dockerTestExt
    ) {
        workflowExt.pipelines.all { pipelineSpec ->
            def pipelineName = pipelineSpec.name
            def capitalizedName = pipelineName.capitalize()

            // Register master pipeline task
            project.tasks.register("run${capitalizedName}", PipelineRunTask) { task ->
                configurePipelineTask(task, pipelineSpec, dockerExt, dockerTestExt)
            }

            // Register step tasks (for manual invocation)
            project.tasks.register("build${capitalizedName}", PipelineBuildTask) { task ->
                configureBuildTask(task, pipelineSpec.build)
            }

            project.tasks.register("test${capitalizedName}", PipelineTestTask) { task ->
                configureTestTask(task, pipelineSpec.test)
            }

            // ... etc
        }
    }
}
```

**Complexity factors:**
- Integration with existing extensions
- Cross-DSL reference validation
- Task dependency management

**Decomposition strategy:**
1. **Extension registration** - Create dockerWorkflows (100 LOC)
2. **Task registration** - Create workflow tasks (150 LOC)
3. **Cross-DSL wiring** - Connect to docker/dockerTest (100 LOC)
4. **Validation** - Ensure valid references (50 LOC)

**Testing strategy:**
- Functional tests for plugin application (10 tests, ~400 LOC)
- Integration tests for complete workflows (5 tests, ~500 LOC)

### Total Complexity Summary

| Layer | Production Code | Test Code | Total |
|-------|----------------|-----------|-------|
| Extensions | 800 LOC | 1500 LOC | 2300 LOC |
| Task Orchestration | 1200 LOC | 2400 LOC | 3600 LOC |
| Conditional Logic | 600 LOC | 1200 LOC | 1800 LOC |
| Plugin Integration | 400 LOC | 900 LOC | 1300 LOC |
| **TOTAL** | **3000 LOC** | **6000 LOC** | **9000 LOC** |

### Managing Complexity Through Decomposition

**Phased implementation plan:**

#### Phase 1: Foundation (Week 1)
- Extension skeletons
- Basic DSL parsing
- Unit tests for specs

**Deliverable:** `dockerWorkflows { pipelines { myPipeline { } } }` parses correctly

#### Phase 2: Build Step (Week 2)
- BuildStepSpec implementation
- Reference docker.images
- Invoke dockerBuild* tasks

**Deliverable:** `build { image = docker.images.myApp }` executes build

#### Phase 3: Test Step (Week 3)
- TestStepSpec implementation
- Reference dockerTest.composeStacks
- Invoke compose + test tasks
- Capture test results

**Deliverable:** `test { stack = ... testTask = ... }` executes and captures results

#### Phase 4: Conditional Execution (Week 4)
- SuccessStepSpec/FailureStepSpec
- Conditional logic evaluation
- Tag/save/publish operations

**Deliverable:** `onTestSuccess { save { } publish { } }` executes conditionally

#### Phase 5: Polish & Integration (Week 5-6)
- Always/cleanup blocks
- Error handling
- Integration tests
- Documentation

**Deliverable:** Complete, tested, documented workflow DSL

**Risk mitigation:**
- Extensive unit tests at each phase (90%+ coverage)
- Functional tests for Gradle integration
- Integration tests for real Docker operations
- Incremental development prevents "big bang" failures

### Verdict: Implementation Complexity

**✅ MANAGEABLE** through:
- Systematic decomposition into testable layers
- Incremental development (5 phases)
- Comprehensive testing at each phase
- Code reuse from existing infrastructure (90%+)

**Estimated Effort:** 4-6 weeks (one experienced developer)

**Risk Level:** MEDIUM (manageable with proper approach)

---

## Challenge 3: Conditional Logic in Gradle

### The Core Problem

**Gradle's execution model:**

```
Configuration Phase                 Execution Phase
─────────────────────────────────  ───────────────────
1. Parse build.gradle              4. Execute task actions
2. Create tasks                       in dependency order
3. Build task graph
                                    ← Task graph is FIXED
                                    ← Cannot modify during execution
```

**What workflows need:**

```groovy
onTestSuccess {
    publish { ... }  // Only if tests pass
}
```

**Question:** How to make `publish` conditional when task graph is fixed at configuration time?

### Three Problems with Conditional Logic

#### Problem 1: Cannot Modify Task Graph During Execution

**Gradle constraint:**

```groovy
// Configuration phase
tasks.register('myTask') { }  // ✅ OK

// Execution phase
@TaskAction
void doWork() {
    tasks.register('anotherTask') { }  // ❌ ILLEGAL - task graph is sealed
}
```

**Implication:** Cannot create/remove tasks based on test results.

#### Problem 2: Task Dependencies Are Static

```groovy
// Configuration phase - must declare ALL dependencies
tasks.register('publish') {
    dependsOn 'test'  // ✅ OK - static dependency

    onlyIf {
        // This runs DURING execution, but dependency already wired
        tasks.named('test').get().state.failure == null
    }
}
```

**Problem:** `publish` task is IN the task graph, even if it will be skipped.

#### Problem 3: Cannot Access Test Results at Configuration Time

```groovy
// Configuration phase
tasks.register('publish') {
    // ❌ Cannot do this - tests haven't run yet!
    if (tasks.named('test').get().didTestsPass()) {
        dependsOn 'dockerPublish'
    }
}
```

### Solution Approaches

#### Approach 1: `onlyIf` Predicate (Standard Gradle Pattern)

**How it works:**

```groovy
tasks.register('conditionalTask') {
    // Dependency is STATIC
    dependsOn 'prerequisiteTask'

    // Condition is DYNAMIC (evaluated at execution time)
    onlyIf {
        tasks.named('prerequisiteTask').get().state.failure == null
    }

    doLast {
        // This only runs if onlyIf returns true
        println "Prerequisite succeeded, running conditional task"
    }
}
```

**Execution flow:**
1. **Configuration phase:** Task is registered, dependencies are wired
2. **Execution phase (before task action):** `onlyIf` predicate is evaluated
3. **If true:** Task action executes
4. **If false:** Task is skipped (marked as `SKIPPED` in output)

**Advantages:**
- ✅ Uses standard Gradle API
- ✅ Works with task dependencies
- ✅ Compatible with `--dry-run`

**Disadvantages:**
- ❌ Creates "wrapper" tasks
- ❌ Skipped tasks still appear in output
- ❌ Must invoke underlying tasks or duplicate logic

#### Approach 2: Programmatic Task Invocation (Custom Pattern)

**How it works:**

```groovy
class PipelineRunTask extends DefaultTask {
    @TaskAction
    void runPipeline() {
        // 1. Build (always runs)
        def buildTask = project.tasks.named("dockerBuild${imageName}")
        buildTask.get().execute()

        // 2. Test (always runs)
        def testTask = project.tasks.named('integrationTest')
        testTask.get().execute()

        // 3. Conditional operations based on test result
        if (testTask.get().state.failure == null) {
            // Tests passed - execute success operations
            executeSuccessOperations()
        } else {
            // Tests failed - execute failure operations
            executeFailureOperations()
        }

        // 4. Always execute cleanup
        executeCleanupOperations()
    }

    private void executeSuccessOperations() {
        // Apply additional tags
        if (additionalTags.present) {
            applyTags(additionalTags.get())
        }

        // Execute save (only if configured)
        if (saveSpec != null) {
            def saveTask = project.tasks.named("dockerSave${imageName}")
            saveTask.get().execute()
        }

        // Execute publish (only if configured)
        if (publishSpec != null) {
            def publishTask = project.tasks.named("dockerPublish${imageName}")
            publishTask.get().execute()
        }
    }
}
```

**Advantages:**
- ✅ Conditional logic is explicit and clear
- ✅ Works within Gradle's execution model
- ✅ Can inspect test task state
- ✅ Can execute operations conditionally
- ✅ Single task in task graph (clean output)

**Disadvantages:**
- ⚠️ Bypasses Gradle's task dependency graph
- ⚠️ Must programmatically invoke tasks
- ⚠️ Need to handle task lifecycle

#### Approach 3: Hybrid Orchestration (RECOMMENDED)

**Combines best of both approaches:**

```groovy
class PipelineRunTask extends DefaultTask {
    @Inject
    abstract Property<DockerService> getDockerService()

    @Internal
    Property<PipelineSpec> pipelineSpec

    @TaskAction
    void runPipeline() {
        def pipeline = pipelineSpec.get()
        def context = new PipelineContext()

        try {
            // 1. Build step
            executeBuildStep(pipeline.build, context)

            // 2. Test step
            def testResult = executeTestStep(pipeline.test, context)
            context.testResult = testResult

            // 3. Conditional success operations
            if (testResult.success) {
                executeSuccessStep(pipeline.onTestSuccess, context)
            } else {
                executeFailureStep(pipeline.onTestFailure, context)
            }
        } finally {
            // 4. Always execute cleanup
            executeAlwaysStep(pipeline.always, context)
        }
    }

    private void executeBuildStep(BuildStepSpec buildSpec, PipelineContext context) {
        // Execute pre-build hooks
        if (buildSpec.beforeBuild != null) {
            buildSpec.beforeBuild.execute(null)
        }

        // Invoke existing dockerBuild task
        def imageName = buildSpec.image.get().name
        def buildTaskProvider = project.tasks.named("dockerBuild${imageName.capitalize()}")

        // Use task provider to avoid configuration cache issues
        buildTaskProvider.get().actions.each { it.execute(buildTaskProvider.get()) }

        // Execute post-build hooks
        if (buildSpec.afterBuild != null) {
            buildSpec.afterBuild.execute(null)
        }

        context.builtImage = buildSpec.image.get()
    }

    private TestResult executeTestStep(TestStepSpec testSpec, PipelineContext context) {
        // Execute pre-test hooks
        if (testSpec.beforeTest != null) {
            testSpec.beforeTest.execute(null)
        }

        // 1. Start compose stack
        def stackName = testSpec.stack.get().name
        def composeUpTask = project.tasks.named("composeUp${stackName.capitalize()}")
        composeUpTask.get().actions.each { it.execute(composeUpTask.get()) }

        try {
            // 2. Run tests
            def testTask = testSpec.testTask.get()
            testTask.actions.each { it.execute(testTask) }

            // 3. Capture result
            def result = new TestResult(
                success: testTask.state.failure == null,
                executed: testTask.state.executed
            )

            // Execute post-test hooks
            if (testSpec.afterTest != null) {
                testSpec.afterTest.execute(result)
            }

            return result

        } finally {
            // 4. Stop compose stack (cleanup)
            def composeDownTask = project.tasks.named("composeDown${stackName.capitalize()}")
            composeDownTask.get().actions.each { it.execute(composeDownTask.get()) }
        }
    }

    private void executeSuccessStep(SuccessStepSpec successSpec, PipelineContext context) {
        def image = context.builtImage

        // Apply additional tags
        if (successSpec.additionalTags.present) {
            def baseRef = image.getEffectiveImageRef()
            successSpec.additionalTags.get().each { tag ->
                def targetRef = "${image.registry.get()}/${image.imageName.get()}:${tag}"
                dockerService.get().tagImage(baseRef, targetRef)
                logger.lifecycle("✅ Tagged: ${targetRef}")
            }
        }

        // Execute save
        if (successSpec.save != null) {
            executeSaveOperation(successSpec.save, image)
        }

        // Execute publish
        if (successSpec.publish != null) {
            executePublishOperation(successSpec.publish, image)
        }

        // Execute success hooks
        if (successSpec.afterSuccess != null) {
            successSpec.afterSuccess.execute(null)
        }
    }

    private void executeSaveOperation(SaveSpec saveSpec, ImageSpec image) {
        // Invoke existing DockerService logic
        def imageRef = image.getEffectiveImageRef()
        def outputFile = saveSpec.outputFile.get().asFile
        def compression = saveSpec.compression.get()

        dockerService.get().saveImage(imageRef, outputFile, compression)
        logger.lifecycle("✅ Saved: ${outputFile.absolutePath}")
    }

    private void executePublishOperation(PublishSpec publishSpec, ImageSpec image) {
        // Invoke existing DockerService logic for each target
        publishSpec.to.all { target ->
            def sourceRef = image.getEffectiveImageRef()
            def targetRegistry = target.registry.get()
            def targetRepo = target.repository.get()

            target.publishTags.get().each { tag ->
                def targetRef = "${targetRegistry}/${targetRepo}:${tag}"

                // Tag for target registry
                dockerService.get().tagImage(sourceRef, targetRef)

                // Push to registry
                dockerService.get().pushImage(targetRef, target.auth)

                logger.lifecycle("✅ Published: ${targetRef}")
            }
        }
    }
}

// Context object to pass data between steps
class PipelineContext {
    ImageSpec builtImage
    TestResult testResult
}
```

**Advantages:**
- ✅ Single orchestration task (clean task graph)
- ✅ Conditional logic is explicit in code
- ✅ Reuses existing service layer (DockerService, ComposeService)
- ✅ Can inspect test results programmatically
- ✅ Hooks for extensibility (beforeBuild, afterTest, etc.)
- ✅ Clear error handling (try/finally for cleanup)
- ✅ Works within Gradle's execution model

**Disadvantages:**
- ⚠️ Programmatic task invocation (bypasses some Gradle features)
- ⚠️ Need to manually handle task state
- ⚠️ Requires understanding of Gradle task internals

### Testing Conditional Logic

**Unit tests:**

```groovy
class PipelineRunTaskTest extends Specification {
    def "should execute success operations when tests pass"() {
        given:
        def task = createPipelineTask()
        def mockTestTask = Mock(Test)
        mockTestTask.state.failure >> null  // Test passed

        when:
        task.runPipeline()

        then:
        1 * dockerService.saveImage(_, _, _)
        1 * dockerService.publishImage(_, _, _)
    }

    def "should skip success operations when tests fail"() {
        given:
        def task = createPipelineTask()
        def mockTestTask = Mock(Test)
        mockTestTask.state.failure >> new Exception("Test failed")

        when:
        task.runPipeline()

        then:
        0 * dockerService.saveImage(_, _, _)
        0 * dockerService.publishImage(_, _, _)
    }
}
```

**Integration tests:**

```groovy
def "complete workflow with passing tests"() {
    given:
    buildFile << """
        dockerWorkflows {
            testPipeline {
                build { image = docker.images.myApp }
                test { stack = dockerTest.composeStacks.myTest }
                onTestSuccess {
                    additionalTags = ['stable']
                    save { compression.set(GZIP) }
                    publish { to('prod') { ... } }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withArguments('runTestPipeline')
        .build()

    then:
    result.task(':runTestPipeline').outcome == SUCCESS
    // Verify image was saved
    new File(testProjectDir, 'build/images/myapp.tar.gz').exists()
    // Verify image was published (check Docker registry)
}
```

### Verdict: Conditional Logic

**✅ SOLVABLE** using hybrid orchestration approach

**Key insights:**
1. **Cannot modify task graph** - Use single orchestration task instead
2. **Cannot access test results at configuration** - Evaluate at execution time
3. **Cannot create tasks dynamically** - Invoke operations programmatically
4. **Must work within Gradle model** - Use task actions, not task dependencies

**Solution pattern:**
- Single `PipelineRunTask` that orchestrates workflow
- Programmatic invocation of existing operations
- Conditional logic in task action (execution time)
- Reuse existing service layer (DockerService, ComposeService)

**Estimated Complexity:** Moderate (~600-800 LOC for conditional logic layer)

**Testing:** Straightforward - Mock services, test conditional branches

**Risk Level:** MEDIUM (requires Gradle internals knowledge, but well-defined approach)

---

## Recommended Implementation Approach

### Architecture Overview

```
DockerWorkflowsExtension (DSL)
    ↓
PipelineSpec (configuration)
    ↓
PipelineRunTask (orchestration)
    ↓
DockerService / ComposeService (operations)
    ↓
Docker / Docker Compose (execution)
```

### Key Design Decisions

#### 1. Single Orchestration Task

**Decision:** Use single `PipelineRunTask` per pipeline, NOT multiple wrapper tasks

**Rationale:**
- Clean task graph (one task vs many)
- Explicit conditional logic (no `onlyIf` predicates to debug)
- Reuses existing service layer
- Works within Gradle constraints

#### 2. Reuse Existing Specs and Services

**Decision:** Reuse SaveSpec, PublishSpec, DockerService, ComposeService

**Rationale:**
- 90%+ code reuse
- Consistent behavior across DSLs
- Reduced testing burden
- Easier maintenance

#### 3. Programmatic Task Invocation

**Decision:** Invoke task actions programmatically, not via task dependencies

**Rationale:**
- Enables conditional execution
- Allows test result inspection
- Provides explicit control flow
- Simplifies error handling

#### 4. Provider API Throughout

**Decision:** Use Provider API for all configuration values

**Rationale:**
- Gradle 9/10 configuration cache compatibility
- Lazy evaluation
- Proper dependency tracking

### Implementation Phases

**Phase 1 (Week 1): Foundation**
- Create extension skeletons
- Implement basic DSL parsing
- Write unit tests for specs

**Phase 2 (Week 2): Build Step**
- Implement BuildStepSpec
- Wire to docker.images
- Invoke dockerBuild* tasks

**Phase 3 (Week 3): Test Step**
- Implement TestStepSpec
- Wire to dockerTest.composeStacks
- Capture test results

**Phase 4 (Week 4): Conditional Execution**
- Implement SuccessStepSpec/FailureStepSpec
- Add tag/save/publish operations
- Implement conditional logic

**Phase 5 (Weeks 5-6): Polish**
- Add AlwaysStepSpec (cleanup)
- Error handling and logging
- Integration tests
- Documentation

### Testing Strategy

**Unit Tests (90%+ coverage):**
- Spec classes (extension layer)
- Orchestration logic (task layer)
- Conditional execution (logic layer)
- Service integration (plugin layer)

**Functional Tests:**
- DSL parsing correctness
- Task registration
- Cross-DSL references

**Integration Tests:**
- Complete workflows (build → test → publish)
- Failure scenarios (test failures)
- Cleanup operations
- Real Docker operations

---

## Risk Assessment

### Technical Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Gradle incompatibility | LOW | HIGH | Use Provider API, follow Gradle best practices |
| Configuration cache issues | MEDIUM | HIGH | Extensive testing with config cache enabled |
| Test result capture fails | LOW | MEDIUM | Multiple fallback methods (state, XML, logs) |
| Performance degradation | LOW | MEDIUM | Reuse existing infrastructure, minimize overhead |
| Complex error scenarios | MEDIUM | MEDIUM | Comprehensive error handling, try/finally blocks |

### Project Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Scope creep | MEDIUM | HIGH | Strict phase boundaries, incremental delivery |
| Insufficient testing | LOW | HIGH | 90%+ coverage requirement, automated tests |
| Documentation gaps | MEDIUM | MEDIUM | Write docs alongside code, include examples |
| User confusion | MEDIUM | MEDIUM | Clear migration guide, consistent API |
| Maintenance burden | LOW | MEDIUM | 90% code reuse reduces maintenance |

### Overall Risk Level

**MEDIUM** - Manageable with proper approach

**Success factors:**
- Systematic decomposition
- Incremental development
- Comprehensive testing
- Clear documentation

---

## Conclusion

### Summary of Findings

#### Challenge 1: API Duplication
**✅ ACCEPTABLE** - Partial duplication with 90%+ code reuse, expected pattern, valuable flexibility

**Risk Level:** LOW

#### Challenge 2: Implementation Complexity
**✅ MANAGEABLE** - Systematic decomposition, incremental development, comprehensive testing

**Estimated Effort:** 4-6 weeks development + 2-3 weeks testing + 1 week documentation = 7-10 weeks

**Risk Level:** MEDIUM

#### Challenge 3: Conditional Logic in Gradle
**✅ SOLVABLE** - Hybrid orchestration approach works within Gradle constraints

**Estimated Complexity:** 600-800 LOC for conditional logic layer

**Risk Level:** MEDIUM

### Overall Assessment

**Option C (Pipeline/Workflow DSL) is FEASIBLE and PRACTICAL** with the recommended implementation approach.

### Key Success Factors

1. **Hybrid orchestration approach** - Single task with programmatic invocation
2. **Code reuse** - 90%+ reuse of existing specs and services
3. **Incremental development** - 5 phases with clear deliverables
4. **Comprehensive testing** - 90%+ coverage at each layer
5. **Provider API usage** - Gradle 9/10 compatibility throughout

### Recommendation

**✅ PROCEED with Option C implementation** using the hybrid orchestration approach outlined in this document.

**Estimated Timeline:**
- Development: 4-6 weeks
- Testing: 2-3 weeks
- Documentation: 1 week
- **Total: 7-10 weeks**

**Expected Value:**
- Solves 100% of build → test → conditional publish use cases
- Provides declarative workflow DSL
- Enables multiple pipeline patterns (dev, staging, production)
- Maintains backward compatibility with existing DSLs

---

## References

- [Architectural Limitations Analysis](architectural-limitations-analysis.md)
- [Option C: Objective Approach Usage](architectural-limitations-objective-approach-usage.md)
- [Gradle Documentation: Task Configuration](https://docs.gradle.org/current/userguide/more_about_tasks.html)
- [Gradle Documentation: Lazy Configuration](https://docs.gradle.org/current/userguide/lazy_configuration.html)
- [Gradle Documentation: Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html)

---

**Document Status:** Final
**Next Steps:** Review with team, create implementation plan, begin Phase 1 development
