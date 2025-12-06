# Phase 1 Improvements Plan: Better Error Messages and Documentation

**Status:** PLANNED
**Date:** 2025-12-06
**Author:** Development Team
**Estimated Effort:** 4-8 hours
**Risk Level:** None
**Related Documents:**
- [Add Method Workflow Analysis](add-method-workflow-analysis.md)
- [Decision: Do Not Implement Phase 2](do-not-implement-auto-detection-phase-2.md)

---

## Executive Summary

This plan describes low-risk improvements to the Phase 1 implementation that enhance user experience without
the complexity and risks of auto-detection (Phase 2). The improvements focus on:

1. **Better error messages** when tests fail due to missing annotations
2. **Documentation updates** explaining why annotations are required
3. **IDE templates** for quick test class creation

These improvements address the primary user friction point (forgetting the annotation) without introducing
JVM-wide auto-detection risks or complex global extension machinery.

---

## Problem Statement

When users configure `usesCompose()` in build.gradle but forget to add the required annotation to their test
class, they receive confusing errors like:

```
Docker Compose stack name not configured.
Ensure test task is configured with usesCompose and docker.compose.stack system property is set.
```

This error is misleading because:
1. The user DID configure `usesCompose()` in build.gradle
2. The system property IS set
3. The actual problem is a missing annotation on the test class

---

## Improvement 1: Better Error Messages

### Goal

Provide clear, actionable error messages that:
1. Identify the actual problem (missing annotation)
2. Show exactly what annotation to add
3. Include the test class name for easy identification

### Target Error Message

**When system properties are set but extension is not invoked:**

```
================================================================================
ERROR: Missing Docker Compose annotation on test class

Test class 'com.example.MyIntegrationTest' appears to be configured for Docker
Compose (system properties are set) but is missing the required test framework
annotation.

Add one of the following annotations to your test class:

  Spock:
    @ComposeUp
    class MyIntegrationTest extends Specification { ... }

  JUnit 5 (class lifecycle):
    @ExtendWith(DockerComposeClassExtension.class)
    class MyIntegrationTest { ... }

  JUnit 5 (method lifecycle):
    @ExtendWith(DockerComposeMethodExtension.class)
    class MyIntegrationTest { ... }

The annotation is required to enable the test framework extension that manages
Docker Compose container lifecycle (start before tests, stop after tests).

For more information, see: docs/usage/usage-docker-orch.md
================================================================================
```

### Implementation Approach

The challenge is that we cannot detect "missing annotation" from within the extension—if the annotation is
missing, the extension never runs. Instead, we need to detect the failure from the **test task** side.

**Option A: Test Task Listener (Recommended)**

Add a test listener to the test task that checks for compose-related failures:

```groovy
// In TestIntegrationExtension.configureMethodLifecycle() / configureClassLifecycle()
test.addTestListener(new ComposeAnnotationHintListener(stackName, lifecycle))
```

The listener examines test failures and enhances error messages when:
1. System properties indicate compose was configured (`docker.compose.stack` is set)
2. The failure message suggests configuration issues

**Option B: Gradle Test Failure Hook**

Use Gradle's `Test.afterTest` closure to detect failures and provide hints:

```groovy
test.afterTest { desc, result ->
    if (result.resultType == TestResult.ResultType.FAILURE) {
        checkForMissingAnnotationHint(desc, result, stackName)
    }
}
```

**Option C: Documentation-Only Approach**

If runtime detection proves too complex, improve the existing error messages in the extensions
themselves to be more helpful, even though they only trigger in edge cases.

### Files to Modify

| File | Change |
|------|--------|
| `TestIntegrationExtension.groovy` | Add test listener for annotation hints |
| `DockerComposeMethodExtension.groovy` | Improve existing error messages |
| `DockerComposeClassExtension.groovy` | Improve existing error messages |
| `DockerComposeSpockExtension.groovy` | Improve existing error messages |

### Implementation Steps

**Step 1.1: Create ComposeAnnotationHintListener (2 hours)**

```groovy
// plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/ComposeAnnotationHintListener.groovy
package com.kineticfire.gradle.docker.extension

import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

/**
 * Test listener that detects potential missing annotation issues and provides helpful hints.
 */
class ComposeAnnotationHintListener implements TestListener {

    private final String stackName
    private final String lifecycle
    private boolean hintProvided = false

    ComposeAnnotationHintListener(String stackName, String lifecycle) {
        this.stackName = stackName
        this.lifecycle = lifecycle
    }

    @Override
    void beforeSuite(TestDescriptor suite) {}

    @Override
    void afterSuite(TestDescriptor suite, TestResult result) {
        // Check at suite level for class-wide failures
        if (result.resultType == TestResult.ResultType.FAILURE && !hintProvided) {
            checkAndProvideHint(suite, result)
        }
    }

    @Override
    void beforeTest(TestDescriptor testDescriptor) {}

    @Override
    void afterTest(TestDescriptor testDescriptor, TestResult result) {
        if (result.resultType == TestResult.ResultType.FAILURE && !hintProvided) {
            checkAndProvideHint(testDescriptor, result)
        }
    }

    private void checkAndProvideHint(TestDescriptor descriptor, TestResult result) {
        // Check if failure might be related to missing compose annotation
        def exception = result.exception
        if (exception == null) return

        def message = exception.message ?: ''
        def className = descriptor.className ?: 'UnknownClass'

        // Heuristics for detecting missing annotation issues:
        // 1. Connection refused errors (containers not started)
        // 2. "stack name not configured" from within extensions (edge case)
        // 3. Timeout waiting for service (containers not started)

        boolean likelyMissingAnnotation =
            message.contains('Connection refused') ||
            message.contains('connect timed out') ||
            message.contains('No route to host') ||
            message.contains('COMPOSE_STATE_FILE') ||
            (message.contains('stack') && message.contains('not configured'))

        if (likelyMissingAnnotation) {
            provideAnnotationHint(className)
            hintProvided = true
        }
    }

    private void provideAnnotationHint(String className) {
        def simpleClassName = className.contains('.') ?
            className.substring(className.lastIndexOf('.') + 1) : className

        def extensionClass = lifecycle == 'method' ?
            'DockerComposeMethodExtension' : 'DockerComposeClassExtension'

        System.err.println('''
================================================================================
HINT: Possible missing Docker Compose annotation

Test class '${className}' is configured to use Docker Compose stack '${stackName}'
with '${lifecycle}' lifecycle, but tests are failing with connection errors.

This often indicates the test class is missing the required annotation.
Add one of the following:

  Spock:
    @ComposeUp
    class ${simpleClassName} extends Specification { ... }

  JUnit 5:
    @ExtendWith(${extensionClass}.class)
    class ${simpleClassName} { ... }

The annotation enables the test framework extension that starts containers
before your tests run.

For more information: docs/usage/usage-docker-orch.md
================================================================================
'''.stripIndent().trim()
            .replace('${className}', className)
            .replace('${stackName}', stackName)
            .replace('${lifecycle}', lifecycle)
            .replace('${simpleClassName}', simpleClassName)
            .replace('${extensionClass}', extensionClass)
        )
    }
}
```

**Step 1.2: Register Listener in TestIntegrationExtension (0.5 hours)**

```groovy
// In configureClassLifecycle() and configureMethodLifecycle():
test.addTestListener(new ComposeAnnotationHintListener(stackName, lifecycle))
```

**Step 1.3: Improve Existing Extension Error Messages (1 hour)**

Update error messages in the three extension classes to be more helpful:

```java
// DockerComposeMethodExtension.groovy / DockerComposeClassExtension.groovy
throw new IllegalStateException(
    "Docker Compose stack name not configured for test class '" +
    context.getTestClass().map(Class::getName).orElse("unknown") + "'.\n\n" +
    "If you configured usesCompose() in build.gradle, ensure the test class has " +
    "the required annotation:\n" +
    "  Spock: @ComposeUp\n" +
    "  JUnit 5: @ExtendWith(DockerComposeMethodExtension.class)\n\n" +
    "If you're not using usesCompose(), configure the annotation parameters directly:\n" +
    "  @ComposeUp(stackName = \"myStack\", composeFile = \"path/to/compose.yml\")"
);
```

**Step 1.4: Unit Tests (1.5 hours)**

```groovy
// plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/ComposeAnnotationHintListenerTest.groovy

class ComposeAnnotationHintListenerTest extends Specification {

    def "provides hint when connection refused error occurs"() {
        given:
        def listener = new ComposeAnnotationHintListener('testStack', 'class')
        def descriptor = Mock(TestDescriptor) {
            getClassName() >> 'com.example.MyTest'
        }
        def result = Mock(TestResult) {
            getResultType() >> TestResult.ResultType.FAILURE
            getException() >> new RuntimeException('Connection refused')
        }
        def output = new ByteArrayOutputStream()
        System.setErr(new PrintStream(output))

        when:
        listener.afterTest(descriptor, result)

        then:
        output.toString().contains('HINT: Possible missing Docker Compose annotation')
        output.toString().contains('MyTest')
        output.toString().contains('@ComposeUp')
    }

    def "does not provide hint for unrelated failures"() {
        given:
        def listener = new ComposeAnnotationHintListener('testStack', 'class')
        def descriptor = Mock(TestDescriptor) {
            getClassName() >> 'com.example.MyTest'
        }
        def result = Mock(TestResult) {
            getResultType() >> TestResult.ResultType.FAILURE
            getException() >> new AssertionError('expected true but was false')
        }
        def output = new ByteArrayOutputStream()
        System.setErr(new PrintStream(output))

        when:
        listener.afterTest(descriptor, result)

        then:
        !output.toString().contains('HINT')
    }

    def "provides hint only once per test run"() {
        given:
        def listener = new ComposeAnnotationHintListener('testStack', 'method')
        def descriptor = Mock(TestDescriptor) {
            getClassName() >> 'com.example.MyTest'
        }
        def result = Mock(TestResult) {
            getResultType() >> TestResult.ResultType.FAILURE
            getException() >> new RuntimeException('Connection refused')
        }
        def output = new ByteArrayOutputStream()
        System.setErr(new PrintStream(output))

        when:
        listener.afterTest(descriptor, result)
        listener.afterTest(descriptor, result)  // Second call

        then:
        output.toString().count('HINT: Possible missing') == 1
    }
}
```

---

## Improvement 2: Documentation Updates

### Goal

Update documentation to:
1. Clearly explain WHY the annotation is required
2. Provide copy-paste examples for common scenarios
3. Add a troubleshooting section for the "missing annotation" error

### Files to Update

| File | Changes |
|------|---------|
| `docs/usage/usage-docker-orch.md` | Add "Why Annotations Are Required" section, troubleshooting |
| `docs/usage/usage-docker-workflows.md` | Add note about annotation requirement for METHOD lifecycle |

### Implementation Steps

**Step 2.1: Add "Why Annotations Are Required" Section (1 hour)**

Add to `docs/usage/usage-docker-orch.md` after the Quick Start section:

```markdown
## Why Test Annotations Are Required

When using `usesCompose()` in build.gradle, you must also add an annotation to your test class.
This is required because:

1. **Gradle and test frameworks are separate**: Gradle configures the test task and sets system
   properties, but it cannot inject behavior into individual test classes. Only test framework
   extensions (Spock extensions, JUnit 5 extensions) can hook into test lifecycle.

2. **The annotation activates the extension**: The `@ComposeUp` annotation (Spock) or
   `@ExtendWith(...)` annotation (JUnit 5) tells the test framework to invoke our extension
   code that starts/stops containers.

3. **System properties provide configuration**: The extension reads system properties set by
   `usesCompose()` to know which stack to start, which lifecycle to use, etc.

**Think of it as two parts working together:**
- `usesCompose()` in build.gradle = "Configure WHAT compose settings to use"
- `@ComposeUp` / `@ExtendWith` in test class = "ACTIVATE the extension that uses those settings"

### Common Mistake

❌ **Wrong** - Missing annotation:
```groovy
// build.gradle
tasks.named('integrationTest') {
    usesCompose(stack: "myApp", lifecycle: "class")
}

// Test class - MISSING ANNOTATION!
class MyAppIT extends Specification {
    def "test something"() { ... }  // Will fail - containers never started
}
```

✅ **Correct** - With annotation:
```groovy
// build.gradle
tasks.named('integrationTest') {
    usesCompose(stack: "myApp", lifecycle: "class")
}

// Test class - HAS ANNOTATION
@ComposeUp  // This activates the extension!
class MyAppIT extends Specification {
    def "test something"() { ... }  // Works - containers started before test
}
```
```

**Step 2.2: Add Troubleshooting Section (0.5 hours)**

Add to `docs/usage/usage-docker-orch.md`:

```markdown
## Troubleshooting

### "Connection refused" or timeout errors

**Symptom:** Tests fail with connection errors like:
- `java.net.ConnectException: Connection refused`
- `connect timed out`
- `No route to host`

**Likely cause:** Missing test annotation. The containers were never started.

**Solution:** Add the appropriate annotation to your test class:

```groovy
// Spock
@ComposeUp
class MyTest extends Specification { ... }

// JUnit 5 - class lifecycle
@ExtendWith(DockerComposeClassExtension.class)
class MyTest { ... }

// JUnit 5 - method lifecycle
@ExtendWith(DockerComposeMethodExtension.class)
class MyTest { ... }
```

### "Docker Compose stack name not configured"

**Symptom:** Error message says stack name is not configured, but you configured `usesCompose()`.

**Likely cause:** You're using annotation parameters instead of `usesCompose()`, or there's a
mismatch between them.

**Solution:** Choose ONE configuration approach:

Option A - Configure in build.gradle (recommended):
```groovy
// build.gradle
tasks.named('integrationTest') {
    usesCompose(stack: "myApp", lifecycle: "class")
}

// Test class - zero parameters
@ComposeUp
class MyTest extends Specification { ... }
```

Option B - Configure in annotation only:
```groovy
// build.gradle - NO usesCompose() call

// Test class - full configuration
@ComposeUp(stackName = "myApp", composeFile = "path/to/compose.yml", waitForHealthy = ["service1"])
class MyTest extends Specification { ... }
```

Do NOT mix both approaches for the same test class.
```

---

## Improvement 3: IDE Templates

### Goal

Provide IntelliJ IDEA live templates that allow users to quickly create properly-configured
integration test classes.

### Templates to Create

**Template 1: Spock Integration Test with ComposeUp**

- Abbreviation: `spockcompose`
- Description: Creates a Spock integration test class with @ComposeUp annotation
- Template text:
```groovy
package $PACKAGE_NAME$

import com.kineticfire.gradle.docker.spock.ComposeUp
import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.Shared

@ComposeUp
class $NAME$ extends Specification {

    @Shared
    static String baseUrl

    def setupSpec() {
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)
        def port = stateData.services['$SERVICE_NAME$'].publishedPorts[0].host
        baseUrl = "http://localhost:\${port}"
    }

    def "$TEST_NAME$"() {
        $END$
    }
}
```

**Template 2: JUnit 5 Integration Test with DockerComposeClassExtension**

- Abbreviation: `junit5compose`
- Description: Creates a JUnit 5 integration test class with Docker Compose extension
- Template text:
```java
package $PACKAGE_NAME$;

import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.nio.file.Files;

@ExtendWith(DockerComposeClassExtension.class)
class $NAME$ {

    private static String baseUrl;

    @BeforeAll
    static void setUp() throws Exception {
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        // Parse state file to get container ports
        // baseUrl = "http://localhost:" + port;
    }

    @Test
    void $TEST_NAME$() {
        $END$
    }
}
```

### Implementation Steps

**Step 3.1: Create Live Template XML (0.5 hours)**

Create `docs/ide/intellij-live-templates.xml`:

```xml
<templateSet group="Docker Compose Tests">
  <template name="spockcompose"
            value="package $PACKAGE_NAME$&#10;&#10;import com.kineticfire.gradle.docker.spock.ComposeUp&#10;import groovy.json.JsonSlurper&#10;import spock.lang.Specification&#10;import spock.lang.Shared&#10;&#10;@ComposeUp&#10;class $NAME$ extends Specification {&#10;&#10;    @Shared&#10;    static String baseUrl&#10;&#10;    def setupSpec() {&#10;        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))&#10;        def stateData = new JsonSlurper().parse(stateFile)&#10;        def port = stateData.services['$SERVICE_NAME$'].publishedPorts[0].host&#10;        baseUrl = &quot;http://localhost:\${port}&quot;&#10;    }&#10;&#10;    def &quot;$TEST_NAME$&quot;() {&#10;        $END$&#10;    }&#10;}"
            description="Spock integration test with @ComposeUp"
            toReformat="true"
            toShortenFQNames="true">
    <variable name="PACKAGE_NAME" expression="groovyScript(&quot;_editor.getVirtualFile().getParent().getPath().replaceAll('.*/groovy/', '').replaceAll('/', '.')&quot;)" defaultValue="" alwaysStopAt="false"/>
    <variable name="NAME" expression="fileNameWithoutExtension()" defaultValue="" alwaysStopAt="true"/>
    <variable name="SERVICE_NAME" expression="" defaultValue="my-service" alwaysStopAt="true"/>
    <variable name="TEST_NAME" expression="" defaultValue="should do something" alwaysStopAt="true"/>
    <context>
      <option name="GROOVY_DECLARATION" value="true"/>
    </context>
  </template>

  <template name="junit5compose"
            value="package $PACKAGE_NAME$;&#10;&#10;import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;&#10;import org.junit.jupiter.api.BeforeAll;&#10;import org.junit.jupiter.api.Test;&#10;import org.junit.jupiter.api.extension.ExtendWith;&#10;&#10;@ExtendWith(DockerComposeClassExtension.class)&#10;class $NAME$ {&#10;&#10;    private static String baseUrl;&#10;&#10;    @BeforeAll&#10;    static void setUp() throws Exception {&#10;        String stateFilePath = System.getProperty(&quot;COMPOSE_STATE_FILE&quot;);&#10;        // Parse state file to get container ports&#10;    }&#10;&#10;    @Test&#10;    void $TEST_NAME$() {&#10;        $END$&#10;    }&#10;}"
            description="JUnit 5 integration test with Docker Compose extension"
            toReformat="true"
            toShortenFQNames="true">
    <variable name="PACKAGE_NAME" expression="groovyScript(&quot;_editor.getVirtualFile().getParent().getPath().replaceAll('.*/java/', '').replaceAll('/', '.')&quot;)" defaultValue="" alwaysStopAt="false"/>
    <variable name="NAME" expression="fileNameWithoutExtension()" defaultValue="" alwaysStopAt="true"/>
    <variable name="TEST_NAME" expression="" defaultValue="shouldDoSomething" alwaysStopAt="true"/>
    <context>
      <option name="JAVA_DECLARATION" value="true"/>
    </context>
  </template>
</templateSet>
```

**Step 3.2: Document Template Installation (0.5 hours)**

Create `docs/ide/README.md`:

```markdown
# IDE Integration

## IntelliJ IDEA Live Templates

Live templates allow you to quickly create integration test classes with the correct
Docker Compose annotations.

### Installation

1. Open IntelliJ IDEA
2. Go to **File → Manage IDE Settings → Import Settings...**
3. Select `intellij-live-templates.xml` from this directory
4. Restart IntelliJ IDEA

### Usage

In a new Groovy or Java file, type the template abbreviation and press Tab:

| Abbreviation | Description |
|--------------|-------------|
| `spockcompose` | Spock integration test with `@ComposeUp` |
| `junit5compose` | JUnit 5 integration test with `@ExtendWith(DockerComposeClassExtension.class)` |

### Manual Installation

If import doesn't work, you can add templates manually:

1. Go to **File → Settings → Editor → Live Templates**
2. Click **+** to create a new template group called "Docker Compose Tests"
3. Add templates with the content from `intellij-live-templates.xml`
```

---

## Implementation Summary

| Step | Description | Effort | Files |
|------|-------------|--------|-------|
| 1.1 | Create ComposeAnnotationHintListener | 2 hours | New file |
| 1.2 | Register listener in TestIntegrationExtension | 0.5 hours | TestIntegrationExtension.groovy |
| 1.3 | Improve extension error messages | 1 hour | 3 extension files |
| 1.4 | Unit tests for listener | 1.5 hours | New test file |
| 2.1 | Add "Why Annotations Required" docs | 1 hour | usage-docker-orch.md |
| 2.2 | Add troubleshooting section | 0.5 hours | usage-docker-orch.md |
| 3.1 | Create live template XML | 0.5 hours | New file |
| 3.2 | Document template installation | 0.5 hours | New README |
| **Total** | | **7.5 hours** | |

---

## Acceptance Criteria

### Error Messages
- [ ] When tests fail with connection errors and `usesCompose()` is configured, a hint is printed
- [ ] Hint includes the test class name, stack name, and correct annotation syntax
- [ ] Hint is printed only once per test run (not per failed test)
- [ ] Extension error messages include annotation guidance

### Documentation
- [ ] "Why Annotations Are Required" section explains the two-part system
- [ ] Troubleshooting section covers common errors
- [ ] Copy-paste examples are correct and complete

### IDE Templates
- [ ] `spockcompose` template creates valid Spock test class
- [ ] `junit5compose` template creates valid JUnit 5 test class
- [ ] Installation instructions are clear and tested

### Tests
- [ ] Unit tests cover ComposeAnnotationHintListener
- [ ] Tests verify hint is shown for relevant errors only
- [ ] Tests verify hint is shown only once

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Hint false positives | Low | Low | Use specific error patterns; label as "hint" not "error" |
| Hint missed when relevant | Medium | Low | Cover common patterns; hint is supplemental, not critical |
| Template syntax errors | Low | Low | Test templates before documentation |

**Overall Risk:** Very Low

These improvements are additive and non-breaking. The worst case is that hints don't appear
when they should—users still get the original error messages.

---

## Future Considerations

If these improvements prove insufficient, consider:

1. **Build-time validation**: Add a Gradle task that scans test source files for missing annotations
   when `usesCompose()` is configured. This could run as part of `check` task.

2. **Annotation processor**: Create a compile-time annotation processor that warns when test classes
   in `integrationTest` source set don't have compose annotations.

These are more complex solutions that should only be pursued if the runtime hints prove inadequate.
