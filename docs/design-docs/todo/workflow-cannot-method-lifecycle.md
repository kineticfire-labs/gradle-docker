# Why dockerWorkflows Cannot Support Method Lifecycle

**Date:** 2025-11-29
**Status:** Architectural Limitation (Not a Bug)
**Related:** [Workflow Support Lifecycle](workflow-support-lifecycle.md)

---

## Summary

The `dockerWorkflows` DSL cannot support per-method container lifecycle (fresh containers for each test method).
This is a fundamental architectural limitation, not a bug or missing feature. This document explains why.

---

## Background: Container Lifecycle Modes

The plugin supports three container lifecycle modes for integration testing:

| Lifecycle | Containers Start | Containers Stop | Use Case |
|-----------|------------------|-----------------|----------|
| **Suite** | Before all tests | After all tests | Fast, shared state OK |
| **Class** | Before each test class | After each test class | Isolation per class |
| **Method** | Before each test method | After each test method | Complete isolation |

---

## The Limitation Explained

### How dockerWorkflows Works

`dockerWorkflows` orchestrates pipelines using **Gradle task dependencies**:

```
runMyPipeline
    ├── dockerBuildMyApp          (build step)
    ├── composeUpTestStack        (test step - compose up)
    ├── integrationTest           (test step - run tests)
    ├── composeDownTestStack      (test step - compose down)
    └── dockerTagMyApp:tested     (onTestSuccess step)
```

The key insight: **Gradle tasks execute once per build invocation**. When `integrationTest` runs, it executes
ALL test methods in a single task execution.

### Why Method Lifecycle Cannot Work

For method lifecycle to work, the compose stack would need to:
1. Start before test method 1
2. Stop after test method 1
3. Start before test method 2
4. Stop after test method 2
5. ... and so on

But Gradle task dependencies only provide:
1. `composeUp` runs once (before `integrationTest` task starts)
2. `integrationTest` runs (executes ALL test methods)
3. `composeDown` runs once (after `integrationTest` task completes)

**There is no Gradle mechanism to restart a task between individual test method executions.**

### The delegateStackManagement Feature

The `delegateStackManagement = true` feature was added to allow `testIntegration.usesCompose()` to manage the
compose lifecycle instead of the pipeline. However, `testIntegration.usesCompose()` also uses Gradle task
dependencies:

```groovy
testIntegration {
    usesCompose(integrationTest, 'myStack', 'class')  // or 'method'
}
```

Even when you specify `'method'` as the lifecycle parameter, this only affects how `testIntegration` **configures**
the Spock extension. The actual Gradle tasks (`composeUp`/`composeDown`) still only run once per build.

---

## How Method Lifecycle Actually Works

Method lifecycle is achieved through the **`@ComposeUp` Spock annotation**, which hooks into Spock's test
lifecycle at the framework level (not Gradle's task level):

```groovy
@ComposeUp  // Spock extension manages lifecycle
class IsolatedTestsExampleIT extends Specification {

    def "test 1: creates user alice"() {
        // Container started fresh before this method
        // ...
    }
    // Container stopped after this method

    def "test 2: alice does NOT exist"() {
        // Container started fresh again - proves isolation!
        // ...
    }
    // Container stopped after this method
}
```

The `@ComposeUp` annotation:
1. Intercepts Spock's `setup()` method → calls compose up
2. Runs the test method
3. Intercepts Spock's `cleanup()` method → calls compose down

This happens **inside the JVM running the tests**, not through Gradle task dependencies.

---

## Why You Cannot Combine @ComposeUp with dockerWorkflows

If you try to use `@ComposeUp` on tests that are also managed by `dockerWorkflows`, you get **port conflicts**:

1. Pipeline's `composeUp` task starts the stack on port 8080
2. Test runs, `@ComposeUp` tries to start the stack again on port 8080
3. **Error: Port 8080 is already in use**

The two mechanisms are mutually exclusive for the same compose stack.

---

## What Users Can Do

### Option 1: Use dockerWorkflows with Class/Suite Lifecycle

If you need conditional post-test actions (tag on success, publish on success):

```groovy
dockerWorkflows {
    pipelines {
        ciPipeline {
            build { image = docker.images.myApp }
            test {
                testTaskName = 'integrationTest'
                delegateStackManagement = true  // testIntegration handles class lifecycle
            }
            onTestSuccess {
                additionalTags = ['tested']
            }
        }
    }
}

testIntegration {
    usesCompose(integrationTest, 'testStack', 'class')  // Class lifecycle only
}
```

### Option 2: Use @ComposeUp for Method Lifecycle (No Pipeline)

If you need complete isolation per test method:

```groovy
// NO dockerWorkflows pipeline - just use dockerOrch + @ComposeUp

dockerOrch {
    composeStacks {
        isolatedTest {
            files.from('src/integrationTest/resources/compose/app.yml')
        }
    }
}

testIntegration {
    usesCompose(integrationTest, 'isolatedTest', 'method')
}
```

```groovy
@ComposeUp  // Method lifecycle via Spock extension
class IsolatedTestIT extends Specification {
    // Each test gets fresh containers
}
```

**Trade-off:** You lose the pipeline's conditional actions (tag on success, publish on success).

### Option 3: Separate Pipelines for Build and Test

Split the workflow into separate Gradle invocations:

```bash
# Build and test with method lifecycle
./gradlew dockerBuildMyApp integrationTest

# If tests passed, tag the image
./gradlew dockerTagMyApp -PadditionalTags=tested
```

This is manual but gives full control.

---

## Comparison Table

| Feature | dockerWorkflows | @ComposeUp |
|---------|-----------------|------------|
| Suite lifecycle | ✅ | ❌ |
| Class lifecycle | ✅ (with delegateStackManagement) | ✅ |
| Method lifecycle | ❌ (impossible) | ✅ |
| Conditional tag on success | ✅ | ❌ |
| Conditional publish on success | ✅ | ❌ |
| Pipeline orchestration | ✅ | ❌ |

---

## Where Method Lifecycle IS Tested

The method lifecycle feature is fully tested, just not within `dockerWorkflows`:

- **Location:** `plugin-integration-test/dockerOrch/examples/isolated-tests/`
- **Test file:** `IsolatedTestsExampleIT.groovy`
- **What it proves:**
  - Test 1 creates user "alice"
  - Test 2 verifies "alice" does NOT exist (fresh database!)
  - Test 3 creates "alice" again (succeeds because database is fresh)

This demonstrates complete isolation between test methods.

---

## Conclusion

The inability to support method lifecycle in `dockerWorkflows` is a **fundamental architectural constraint**,
not a missing feature. Gradle's task execution model does not support restarting tasks between individual test
method executions.

Users who need method lifecycle should use the `@ComposeUp` Spock annotation directly, without wrapping it in
a `dockerWorkflows` pipeline. The trade-off is losing the pipeline's conditional post-test actions.

---

## Related Documents

- [Workflow Support Lifecycle Plan](workflow-support-lifecycle.md) - Original implementation plan
- [Spock/JUnit Test Extensions](../../usage/spock-junit-test-extensions.md) - @ComposeUp documentation
- [Isolated Tests Example](../../../plugin-integration-test/dockerOrch/examples/isolated-tests/) - Method lifecycle demo
