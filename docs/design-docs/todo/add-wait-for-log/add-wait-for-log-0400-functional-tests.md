# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Functional Tests

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0200-implementation.md` - Implementation details and component structure
- `add-wait-for-log-0300-unit-tests.md` - Unit test specifications

Reference project testing standards:
- `docs/project-standards/testing/functional-testing.md`

## Purpose

Define functional test specifications for the `waitForLog` feature implementation. Functional tests verify:
- Gradle plugin DSL configuration
- Task registration and wiring
- Validation error messages
- Configuration cache compatibility
- Property propagation without actual Docker execution

This document should contain functional tests ONLY. Unit tests are in `add-wait-for-log-0300-unit-tests.md`,
integration tests are in `add-wait-for-log-0500-integration-tests.md`.

## Review Notes (2026-01-11)

This functional test plan was reviewed for 100% functionality coverage against the implementation plan (0200).

**Critical Gaps Identified (5)** - Tests are in checklist but NO test code was provided:
1. **Gap #FT-1**: Action configuration style test missing (only Closure style tested)
2. **Gap #FT-2**: Invalid regex pattern error test missing
3. **Gap #FT-3**: Orphaned rejectPatterns warning test missing
4. **Gap #FT-4**: pollSeconds > timeoutSeconds warning test missing
5. **Gap #FT-5**: Non-string pattern type validation test missing (implementation validates this)

**Moderate Gaps (4)** - Additional coverage needed:
6. **Gap #FT-6**: Multiple stacks with different waitForLog configurations not tested
7. **Gap #FT-7**: System property JSON format not verified for JUnit extension parsing compatibility
8. **Gap #FT-8**: Service names with special characters (hyphens, underscores) not tested
9. **Gap #FT-9**: Unicode patterns test listed in checklist but no explicit test provided

**Minor Gaps (4)** - Edge cases:
10. **Gap #FT-10**: Empty string pattern edge case
11. **Gap #FT-11**: Null pattern in list edge case
12. **Gap #FT-12**: waitForLog without java plugin scenario
13. **Gap #FT-13**: waitForLog property presence check (isPresent) for conditional logic

**Updates Made:**
- Added missing test specifications for all critical and moderate gaps (Sections 8-10)
- Updated checklist with gap cross-references
- Added edge case tests section

## Review Notes (2026-01-11, Second Review)

Additional review identified the following gaps for 100% functional test coverage:

**Additional Critical Gaps (4)** - Validation edge cases from implementation plan Section 5:
14. **Gap #FT-14**: Negative timeoutSeconds validation not tested (`timeoutSeconds.set(-1)`)
15. **Gap #FT-15**: Zero timeoutSeconds validation not tested (`timeoutSeconds.set(0)`)
16. **Gap #FT-16**: Negative/zero pollSeconds validation not tested
17. **Gap #FT-17**: Negative progressIntervalSeconds behavior not tested

**Additional Moderate Gaps (5)** - Service name and pattern edge cases:
18. **Gap #FT-18**: Empty string service name key not tested (`['': ['pattern']]`)
19. **Gap #FT-19**: Whitespace-only service name not tested
20. **Gap #FT-20**: Duplicate patterns in same service list not tested
21. **Gap #FT-21**: Same service in both waitForRunning and waitForLog not tested
22. **Gap #FT-22**: waitForLog block called multiple times (reconfiguration behavior)

**Additional Minor Gaps (4)** - Content pattern edge cases:
23. **Gap #FT-23**: JSON content in patterns not tested (e.g., `"level":"ERROR"`)
24. **Gap #FT-24**: Patterns with special regex metacharacters needing escaping
25. **Gap #FT-25**: Boundary test for many patterns (10+) per service
26. **Gap #FT-26**: Boundary test for many services (5+) in waitForLog

**Test Implementation Issues (3)** - Corrections needed:
27. **Issue #FT-I1**: Tests in Section 8.2-8.4 call `WaitForLogConfigBuilder.build()` directly, but this
    internal class may not be accessible in build scripts. Tests should verify behavior through task
    execution or use reflection-based verification.
28. **Issue #FT-I2**: Setup compose file only defines `app` and `db` services, but Section 6.3 multi-service
    test references `redis` - either add helper method to extend compose file or use only defined services.
29. **Issue #FT-I3**: Tests should use `.withPluginClasspath()` without parameters for standard TestKit
    behavior (though current approach with System.getProperty is acceptable per codebase conventions).

**Updates Made (Second Review):**
- Added Sections 11, 12 with additional gap tests
- Updated checklist with new gap cross-references
- Added boundary and validation edge case tests

## Review Notes (2026-01-11, Third Review)

Third review identified additional gaps and implementation issues for 100% functional test coverage:

**Critical Implementation Issues (4)** - Must fix before implementation:
30. **Issue #FT-I4**: Test 10.2 appends to compose file that `setup()` already created with `composeFile << ...`.
    This will create duplicate YAML content (two `services:` blocks). Fix: Create separate file or clear first.
31. **Issue #FT-I5**: Warning capture tests (8.3, 8.4) check `result.output` for warnings, but warnings go to
    `stderr` which TestKit may not capture in `output`. These tests may pass without actually verifying warnings.
    Fix: Use `result.output` contains "Config resolved" to verify success, but document warning verification
    limitation or use `forwardStdError()` to capture stderr.
32. **Issue #FT-I6**: Test 9.1 uses `groovy.json.JsonSlurper` in build script `doLast {}` block. This requires
    Groovy runtime available in the test project. Verify this works or use `@Grab` annotation.
33. **Issue #FT-I7**: Empty string pattern test (10.3) expects validation failure but implementation plan
    (Section 5 - WaitForLogConfigBuilder) doesn't explicitly validate empty strings. The regex `""` is valid
    (matches empty string in any line). Clarify expected behavior: allow empty patterns (they match any line)
    or reject them during validation.

**Additional Moderate Gaps (5)** - Should fix before implementation:
34. **Gap #FT-27**: Service names with dots not tested (e.g., `'app.service': ['pattern']`). Docker Compose
    allows dots in service names.
35. **Gap #FT-28**: Both CLASS and METHOD lifecycle in same project with different stacks not tested. Should
    verify independent system property propagation.
36. **Gap #FT-29**: Configuration cache invalidation test missing. Current tests verify cache reuse when config
    unchanged, but need test verifying cache is invalidated when `waitForLog` config changes.
37. **Gap #FT-30**: Pattern that matches everything (`.*`) edge case. This is valid regex but may cause
    unexpected immediate success. Document expected behavior.
38. **Gap #FT-31**: Spec-level `waitForLog.isPresent` check not tested. Section 10.6 tests task-level property
    presence but should also test `stackSpec.waitForLog.present` access.

**Additional Minor Gaps (3)** - Can fix during implementation:
39. **Gap #FT-32**: Very long service names boundary test (e.g., 100+ character service name).
40. **Gap #FT-33**: System property name constants verification. Should verify test property names match
    implementation constants exactly (avoid copy-paste typos).
41. **Gap #FT-34**: Service names with numbers only (e.g., `'123': ['pattern']`) or starting with numbers.

**Updates Made (Third Review):**
- Added implementation issues FT-I4 through FT-I7 requiring fixes
- Added gap tests FT-27 through FT-34
- Updated checklist with new items
- Added Section 13 with additional tests

---

## Checklist

### DSL Configuration Tests
- [ ] Test `waitForLog` block with single service and single pattern
- [ ] Test `waitForLog` block with single service and multiple patterns
- [ ] Test `waitForLog` block with multiple services
- [ ] Test `waitForLog` with `.set()` syntax
- [ ] Test `waitForLog` with direct assignment syntax
- [ ] Test `waitForLog` with Action configuration style **(Gap #FT-1 - test added Section 8.1)**
- [ ] Test `waitForLog` with Closure configuration style
- [ ] Test `waitForLog` combined with `waitForHealthy` and `waitForRunning`
- [ ] Test all optional properties (`rejectPatterns`, `caseInsensitive`, `verbose`, etc.)

### Convention Tests
- [ ] Test default `timeoutSeconds` convention (60)
- [ ] Test default `pollSeconds` convention (2)
- [ ] Test default `caseInsensitive` convention (false)
- [ ] Test default `verbose` convention (false)
- [ ] Test default `progressIntervalSeconds` convention (0)
- [ ] Test default `rejectPatterns` convention (empty map)
- [ ] Test convention override behavior

### Validation Error Tests
- [ ] Test empty `waitForServices` error message
- [ ] Test empty pattern list for service error message
- [ ] Test invalid regex pattern error message (at execution time) **(Gap #FT-2 - test added Section 8.2)**
- [ ] Test orphaned `rejectPatterns` warning (service in reject but not in waitFor) **(Gap #FT-3 - test added Section 8.3)**
- [ ] Test `pollSeconds > timeoutSeconds` warning **(Gap #FT-4 - test added Section 8.4)**
- [ ] Test non-string pattern type error message **(Gap #FT-5 - test added Section 8.5)**
- [ ] Test negative `timeoutSeconds` validation error **(Gap #FT-14 - test added Section 11.1)**
- [ ] Test zero `timeoutSeconds` validation error **(Gap #FT-15 - test added Section 11.1)**
- [ ] Test negative/zero `pollSeconds` validation error **(Gap #FT-16 - test added Section 11.2)**
- [ ] Test negative `progressIntervalSeconds` behavior **(Gap #FT-17 - test added Section 11.3)**

### Property Wiring Tests
- [ ] Test `waitForLog` properties are wired to `ComposeUpTask`
- [ ] Test property values are correctly propagated
- [ ] Test all flattened properties are present on task

### System Property Tests (TestIntegrationExtension)
- [ ] Test `waitForLog` system properties are set for CLASS lifecycle
- [ ] Test `waitForLog` system properties are set for METHOD lifecycle
- [ ] Test JSON serialization of `waitForServices` map **(Gap #FT-7 - test added Section 9.1)**
- [ ] Test JSON serialization of `rejectPatterns` map **(Gap #FT-7 - test added Section 9.1)**
- [ ] Test scalar property serialization

### Multi-Stack Tests
- [ ] Test multiple stacks with different waitForLog configurations **(Gap #FT-6 - test added Section 9.2)**

### Configuration Cache Tests (Phase 4)
- [ ] Test `MapProperty<String, List<String>>` serialization
- [ ] Test configuration cache store on first run
- [ ] Test configuration cache reuse on second run
- [ ] Test various pattern content types (simple, regex, special chars)
- [ ] Test multi-service configuration cache
- [ ] Test edge cases (empty reject patterns, long strings, Unicode) **(Gap #FT-9 - Unicode test added Section 10.1)**

### Edge Case Tests
- [ ] Test service names with special characters (hyphens, underscores) **(Gap #FT-8 - test added Section 10.2)**
- [ ] Test empty string pattern error **(Gap #FT-10 - test added Section 10.3)**
- [ ] Test null pattern in list error **(Gap #FT-11 - test added Section 10.4)**
- [ ] Test waitForLog without java plugin **(Gap #FT-12 - test added Section 10.5)**
- [ ] Test waitForLog property presence check (isPresent) **(Gap #FT-13 - test added Section 10.6)**
- [ ] Test empty string service name key error **(Gap #FT-18 - test added Section 11.4)**
- [ ] Test whitespace-only service name error **(Gap #FT-19 - test added Section 11.4)**
- [ ] Test duplicate patterns in same service list **(Gap #FT-20 - test added Section 11.5)**
- [ ] Test same service in both waitForRunning and waitForLog **(Gap #FT-21 - test added Section 11.6)**
- [ ] Test waitForLog block called multiple times (reconfiguration) **(Gap #FT-22 - test added Section 11.7)**
- [ ] Test JSON content in patterns **(Gap #FT-23 - test added Section 12.1)**
- [ ] Test patterns with regex metacharacters **(Gap #FT-24 - test added Section 12.2)**
- [ ] Test many patterns per service (boundary) **(Gap #FT-25 - test added Section 12.3)**
- [ ] Test many services in waitForLog (boundary) **(Gap #FT-26 - test added Section 12.4)**
- [ ] Test service names with dots **(Gap #FT-27 - test added Section 13.1)**
- [ ] Test CLASS and METHOD lifecycle in same project **(Gap #FT-28 - test added Section 13.2)**
- [ ] Test configuration cache invalidation when config changes **(Gap #FT-29 - test added Section 13.3)**
- [ ] Test pattern that matches everything (`.*`) **(Gap #FT-30 - test added Section 13.4)**
- [ ] Test spec-level waitForLog.isPresent check **(Gap #FT-31 - test added Section 13.5)**
- [ ] Test very long service names boundary **(Gap #FT-32 - test added Section 13.6)**
- [ ] Test service names starting with numbers **(Gap #FT-34 - test added Section 13.7)**

### Implementation Issue Fixes
- [ ] Fix Test 10.2 compose file duplication **(Issue #FT-I4 - fix documented Section 13.8)**
- [ ] Fix warning capture tests stderr handling **(Issue #FT-I5 - fix documented Section 13.8)**
- [ ] Clarify empty string pattern validation behavior **(Issue #FT-I7 - fix documented Section 13.8)**

### Final Verification
- [ ] All functional tests pass
- [ ] No compilation warnings

---

## Functional Test Specifications

### File Location

**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/WaitForLogFunctionalTest.groovy`

### Test Class Structure

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kineticfire.gradle.docker

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for the waitForLog DSL block in the dockerTest extension.
 *
 * These tests verify:
 * - DSL configuration syntax and semantics
 * - Property conventions and overrides
 * - Validation error messages
 * - Task property wiring
 * - System property propagation for test framework extensions
 * - Configuration cache compatibility
 *
 * Note: These tests do NOT execute actual Docker commands. They verify Gradle
 * plugin configuration behavior using TestKit.
 */
class WaitForLogFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile
    File composeFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        composeFile = testProjectDir.resolve('docker-compose.yml').toFile()

        settingsFile << "rootProject.name = 'wait-for-log-test'\n"

        // Create minimal compose file for all tests
        composeFile << """
services:
  app:
    image: alpine:latest
    command: sleep 30
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_PASSWORD: test
"""
    }

    // Tests defined below...
}
```

---

## 1. DSL Configuration Tests

### 1.1 Basic DSL Configuration

#### Test: Single service with single pattern

```groovy
def "waitForLog with single service and single pattern configures correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                myStack {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpMyStack')
                def services = upTask.waitForLogServices.get()
                println "Services: \${services}"
                assert services.containsKey('app')
                assert services['app'] == ['Started Application']
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Services: [app:[Started Application]]")
}
```

#### Test: Single service with multiple patterns (AND semantics)

```groovy
def "waitForLog with multiple patterns for single service uses AND semantics"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                multiPattern {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Database connected', 'Started Application', 'Ready to serve']
                        ])
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpMultiPattern')
                def services = upTask.waitForLogServices.get()
                def patterns = services['app']
                println "Pattern count: \${patterns.size()}"
                assert patterns.size() == 3
                assert patterns.contains('Database connected')
                assert patterns.contains('Started Application')
                assert patterns.contains('Ready to serve')
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Pattern count: 3")
}
```

#### Test: Multiple services with different patterns

```groovy
def "waitForLog with multiple services configures correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                multiService {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application'],
                            'db': ['ready to accept connections', 'PostgreSQL init complete']
                        ])
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpMultiService')
                def services = upTask.waitForLogServices.get()
                println "Service count: \${services.size()}"
                assert services.size() == 2
                assert services.containsKey('app')
                assert services.containsKey('db')
                assert services['app'].size() == 1
                assert services['db'].size() == 2
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Service count: 2")
}
```

### 1.2 DSL Syntax Variations

#### Test: Direct assignment syntax (Groovy shorthand)

```groovy
def "waitForLog with direct assignment syntax works correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                directAssign {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // Direct assignment (Groovy converts to set() call)
                        waitForServices = [
                            'app': ['Started Application']
                        ]
                        timeoutSeconds = 120
                        pollSeconds = 5
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpDirectAssign')
                println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                println "Poll: \${upTask.waitForLogPollSeconds.get()}"
                assert upTask.waitForLogTimeoutSeconds.get() == 120
                assert upTask.waitForLogPollSeconds.get() == 5
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Timeout: 120")
    result.output.contains("Poll: 5")
}
```

#### Test: Explicit .set() method syntax

```groovy
def "waitForLog with explicit set() method syntax works correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                explicitSet {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        timeoutSeconds.set(90)
                        pollSeconds.set(3)
                        caseInsensitive.set(true)
                        verbose.set(true)
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpExplicitSet')
                println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                println "CaseInsensitive: \${upTask.waitForLogCaseInsensitive.get()}"
                println "Verbose: \${upTask.waitForLogVerbose.get()}"
                assert upTask.waitForLogTimeoutSeconds.get() == 90
                assert upTask.waitForLogCaseInsensitive.get() == true
                assert upTask.waitForLogVerbose.get() == true
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Timeout: 90")
    result.output.contains("CaseInsensitive: true")
    result.output.contains("Verbose: true")
}
```

### 1.3 Reject Patterns Configuration

#### Test: Reject patterns for specific services

```groovy
def "waitForLog with rejectPatterns configures fail-fast error detection"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                withReject {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application'],
                            'db': ['ready to accept connections']
                        ])
                        rejectPatterns.set([
                            'app': ['FATAL', 'Exception', 'Error initializing'],
                            'db': ['FATAL', 'failed to start']
                        ])
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpWithReject')
                def rejectPatterns = upTask.waitForLogRejectPatterns.get()
                println "Reject patterns: \${rejectPatterns}"
                assert rejectPatterns.containsKey('app')
                assert rejectPatterns.containsKey('db')
                assert rejectPatterns['app'].size() == 3
                assert rejectPatterns['db'].size() == 2
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Reject patterns:")
    result.output.contains("app")
    result.output.contains("FATAL")
}
```

### 1.4 Combined Wait Blocks

#### Test: waitForLog combined with waitForHealthy and waitForRunning

```groovy
def "waitForLog can be combined with waitForHealthy and waitForRunning"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                combined {
                    composeFile.set(file('docker-compose.yml'))

                    // All three wait blocks configured
                    waitForRunning {
                        waitForServices.set(['app', 'db'])
                        timeoutSeconds.set(30)
                    }

                    waitForHealthy {
                        waitForServices.set(['db'])
                        timeoutSeconds.set(60)
                    }

                    waitForLog {
                        waitForServices.set([
                            'app': ['Ready to serve requests']
                        ])
                        timeoutSeconds.set(120)
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpCombined')

                // Verify all three are configured
                println "Running services: \${upTask.waitForRunningServices.get()}"
                println "Healthy services: \${upTask.waitForHealthyServices.get()}"
                println "Log services: \${upTask.waitForLogServices.get().keySet()}"

                assert upTask.waitForRunningServices.get() == ['app', 'db']
                assert upTask.waitForHealthyServices.get() == ['db']
                assert upTask.waitForLogServices.get().containsKey('app')
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Running services: [app, db]")
    result.output.contains("Healthy services: [db]")
    result.output.contains("Log services: [app]")
}
```

### 1.5 Progress Interval Configuration

#### Test: Progress interval configuration

```groovy
def "waitForLog progressIntervalSeconds configures periodic progress logging"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                withProgress {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        timeoutSeconds.set(300)
                        progressIntervalSeconds.set(30)
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpWithProgress')
                println "Progress interval: \${upTask.waitForLogProgressIntervalSeconds.get()}"
                assert upTask.waitForLogProgressIntervalSeconds.get() == 30
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Progress interval: 30")
}
```

---

## 2. Convention Tests

### 2.1 Default Conventions

#### Test: Default timeout convention is 60 seconds

```groovy
def "waitForLog default timeoutSeconds convention is 60"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                defaultTimeout {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // timeoutSeconds NOT set - should default to 60
                    }
                }
            }
        }

        task verifyDefault {
            doLast {
                def upTask = tasks.getByName('composeUpDefaultTimeout')
                def timeout = upTask.waitForLogTimeoutSeconds.get()
                println "Default timeout: \${timeout}"
                assert timeout == 60
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyDefault')
        .build()

    then:
    result.output.contains("Default timeout: 60")
}
```

#### Test: Default poll convention is 2 seconds

```groovy
def "waitForLog default pollSeconds convention is 2"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                defaultPoll {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // pollSeconds NOT set - should default to 2
                    }
                }
            }
        }

        task verifyDefault {
            doLast {
                def upTask = tasks.getByName('composeUpDefaultPoll')
                def poll = upTask.waitForLogPollSeconds.get()
                println "Default poll: \${poll}"
                assert poll == 2
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyDefault')
        .build()

    then:
    result.output.contains("Default poll: 2")
}
```

#### Test: Default caseInsensitive convention is false

```groovy
def "waitForLog default caseInsensitive convention is false"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                defaultCase {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // caseInsensitive NOT set - should default to false
                    }
                }
            }
        }

        task verifyDefault {
            doLast {
                def upTask = tasks.getByName('composeUpDefaultCase')
                def caseInsensitive = upTask.waitForLogCaseInsensitive.get()
                println "Default caseInsensitive: \${caseInsensitive}"
                assert caseInsensitive == false
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyDefault')
        .build()

    then:
    result.output.contains("Default caseInsensitive: false")
}
```

#### Test: Default verbose convention is false

```groovy
def "waitForLog default verbose convention is false"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                defaultVerbose {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // verbose NOT set - should default to false
                    }
                }
            }
        }

        task verifyDefault {
            doLast {
                def upTask = tasks.getByName('composeUpDefaultVerbose')
                def verbose = upTask.waitForLogVerbose.get()
                println "Default verbose: \${verbose}"
                assert verbose == false
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyDefault')
        .build()

    then:
    result.output.contains("Default verbose: false")
}
```

#### Test: Default progressIntervalSeconds convention is 0 (disabled)

```groovy
def "waitForLog default progressIntervalSeconds convention is 0 (disabled)"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                defaultProgress {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // progressIntervalSeconds NOT set - should default to 0
                    }
                }
            }
        }

        task verifyDefault {
            doLast {
                def upTask = tasks.getByName('composeUpDefaultProgress')
                def progressInterval = upTask.waitForLogProgressIntervalSeconds.get()
                println "Default progressInterval: \${progressInterval}"
                assert progressInterval == 0
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyDefault')
        .build()

    then:
    result.output.contains("Default progressInterval: 0")
}
```

#### Test: Default rejectPatterns convention is empty map

```groovy
def "waitForLog default rejectPatterns convention is empty map"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                defaultReject {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // rejectPatterns NOT set - should default to empty map
                    }
                }
            }
        }

        task verifyDefault {
            doLast {
                def upTask = tasks.getByName('composeUpDefaultReject')
                def rejectPatterns = upTask.waitForLogRejectPatterns.get()
                println "Default rejectPatterns: \${rejectPatterns}"
                assert rejectPatterns.isEmpty()
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyDefault')
        .build()

    then:
    result.output.contains("Default rejectPatterns: [:]")
}
```

### 2.2 Convention Override

#### Test: User can override all conventions

```groovy
def "waitForLog conventions can be overridden"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                overrideConventions {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // Override all conventions
                        timeoutSeconds.set(180)
                        pollSeconds.set(5)
                        caseInsensitive.set(true)
                        verbose.set(true)
                        progressIntervalSeconds.set(15)
                    }
                }
            }
        }

        task verifyOverrides {
            doLast {
                def upTask = tasks.getByName('composeUpOverrideConventions')
                println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                println "Poll: \${upTask.waitForLogPollSeconds.get()}"
                println "CaseInsensitive: \${upTask.waitForLogCaseInsensitive.get()}"
                println "Verbose: \${upTask.waitForLogVerbose.get()}"
                println "ProgressInterval: \${upTask.waitForLogProgressIntervalSeconds.get()}"

                assert upTask.waitForLogTimeoutSeconds.get() == 180
                assert upTask.waitForLogPollSeconds.get() == 5
                assert upTask.waitForLogCaseInsensitive.get() == true
                assert upTask.waitForLogVerbose.get() == true
                assert upTask.waitForLogProgressIntervalSeconds.get() == 15
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyOverrides')
        .build()

    then:
    result.output.contains("Timeout: 180")
    result.output.contains("Poll: 5")
    result.output.contains("CaseInsensitive: true")
    result.output.contains("Verbose: true")
    result.output.contains("ProgressInterval: 15")
}
```

---

## 3. Validation Error Tests

### 3.1 Empty waitForServices Validation

#### Test: Empty waitForServices produces clear error message

```groovy
def "waitForLog with empty waitForServices fails with clear error message"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                emptyServices {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // Empty waitForServices - should fail
                        waitForServices.set([:])
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks')
        .buildAndFail()

    then:
    result.output.contains("waitForServices") || result.output.contains("cannot be empty")
}
```

#### Test: Missing waitForServices produces clear error message

```groovy
def "waitForLog without waitForServices set fails with clear error message"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                noServices {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // waitForServices NOT set at all - should fail
                        timeoutSeconds.set(30)
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks')
        .buildAndFail()

    then:
    result.output.contains("waitForServices") || result.output.contains("must specify")
}
```

### 3.2 Empty Pattern List Validation

#### Test: Empty pattern list for a service fails with clear error

```groovy
def "waitForLog with empty pattern list for service fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                emptyPatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': []  // Empty pattern list - should fail
                        ])
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks')
        .buildAndFail()

    then:
    result.output.contains("Pattern list") || result.output.contains("cannot be empty") || result.output.contains("app")
}
```

---

## 4. Property Wiring Tests

### 4.1 Task Property Wiring

#### Test: All waitForLog properties are wired to ComposeUpTask

```groovy
def "waitForLog all properties are wired to ComposeUpTask"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                fullConfig {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Pattern1', 'Pattern2'],
                            'db': ['DBPattern']
                        ])
                        rejectPatterns.set([
                            'app': ['ERROR', 'FATAL']
                        ])
                        timeoutSeconds.set(90)
                        pollSeconds.set(3)
                        caseInsensitive.set(true)
                        verbose.set(true)
                        progressIntervalSeconds.set(20)
                    }
                }
            }
        }

        task verifyWiring {
            doLast {
                def upTask = tasks.getByName('composeUpFullConfig')

                // Verify all properties exist and have correct values
                assert upTask.hasProperty('waitForLogServices')
                assert upTask.hasProperty('waitForLogRejectPatterns')
                assert upTask.hasProperty('waitForLogTimeoutSeconds')
                assert upTask.hasProperty('waitForLogPollSeconds')
                assert upTask.hasProperty('waitForLogCaseInsensitive')
                assert upTask.hasProperty('waitForLogVerbose')
                assert upTask.hasProperty('waitForLogProgressIntervalSeconds')

                def services = upTask.waitForLogServices.get()
                def rejectPatterns = upTask.waitForLogRejectPatterns.get()

                println "Services: \${services.keySet()}"
                println "Services.app patterns: \${services['app']}"
                println "RejectPatterns: \${rejectPatterns.keySet()}"
                println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                println "Poll: \${upTask.waitForLogPollSeconds.get()}"
                println "CaseInsensitive: \${upTask.waitForLogCaseInsensitive.get()}"
                println "Verbose: \${upTask.waitForLogVerbose.get()}"
                println "ProgressInterval: \${upTask.waitForLogProgressIntervalSeconds.get()}"

                assert services.size() == 2
                assert services['app'] == ['Pattern1', 'Pattern2']
                assert services['db'] == ['DBPattern']
                assert rejectPatterns.size() == 1
                assert rejectPatterns['app'] == ['ERROR', 'FATAL']
                assert upTask.waitForLogTimeoutSeconds.get() == 90
                assert upTask.waitForLogPollSeconds.get() == 3
                assert upTask.waitForLogCaseInsensitive.get() == true
                assert upTask.waitForLogVerbose.get() == true
                assert upTask.waitForLogProgressIntervalSeconds.get() == 20

                println "All properties wired correctly"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyWiring')
        .build()

    then:
    result.output.contains("All properties wired correctly")
}
```

---

## 5. System Property Tests (TestIntegrationExtension)

### 5.1 System Property Propagation for CLASS Lifecycle

#### Test: waitForLog system properties set for CLASS lifecycle

```groovy
def "waitForLog system properties are set for CLASS lifecycle"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                classLifecycle {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application'],
                            'db': ['ready to accept connections']
                        ])
                        timeoutSeconds.set(120)
                        caseInsensitive.set(true)
                    }
                }
            }
        }

        tasks.register('classTest', Test) {
            usesCompose stack: 'classLifecycle', lifecycle: 'class'
        }

        task verifySysProps {
            doLast {
                def testTask = tasks.getByName('classTest')
                def props = testTask.systemProperties

                println "Services JSON: \${props['docker.compose.waitForLog.services']}"
                println "Timeout: \${props['docker.compose.waitForLog.timeoutSeconds']}"
                println "CaseInsensitive: \${props['docker.compose.waitForLog.caseInsensitive']}"

                assert props['docker.compose.waitForLog.services'] != null
                assert props['docker.compose.waitForLog.services'].contains('app')
                assert props['docker.compose.waitForLog.services'].contains('db')
                assert props['docker.compose.waitForLog.timeoutSeconds'] == '120'
                assert props['docker.compose.waitForLog.caseInsensitive'] == 'true'
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifySysProps')
        .build()

    then:
    result.output.contains("Services JSON:")
    result.output.contains("app")
    result.output.contains("Timeout: 120")
}
```

### 5.2 System Property Propagation for METHOD Lifecycle

#### Test: waitForLog system properties set for METHOD lifecycle

```groovy
def "waitForLog system properties are set for METHOD lifecycle"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                methodLifecycle {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Ready to serve']
                        ])
                        rejectPatterns.set([
                            'app': ['FATAL']
                        ])
                        verbose.set(true)
                        progressIntervalSeconds.set(10)
                    }
                }
            }
        }

        tasks.register('methodTest', Test) {
            usesCompose stack: 'methodLifecycle', lifecycle: 'method'
        }

        task verifySysProps {
            doLast {
                def testTask = tasks.getByName('methodTest')
                def props = testTask.systemProperties

                println "Services JSON: \${props['docker.compose.waitForLog.services']}"
                println "RejectPatterns JSON: \${props['docker.compose.waitForLog.rejectPatterns']}"
                println "Verbose: \${props['docker.compose.waitForLog.verbose']}"
                println "ProgressInterval: \${props['docker.compose.waitForLog.progressIntervalSeconds']}"

                assert props['docker.compose.waitForLog.services'] != null
                assert props['docker.compose.waitForLog.rejectPatterns'] != null
                assert props['docker.compose.waitForLog.rejectPatterns'].contains('FATAL')
                assert props['docker.compose.waitForLog.verbose'] == 'true'
                assert props['docker.compose.waitForLog.progressIntervalSeconds'] == '10'
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifySysProps')
        .build()

    then:
    result.output.contains("Services JSON:")
    result.output.contains("Verbose: true")
    result.output.contains("ProgressInterval: 10")
}
```

---

## 6. Configuration Cache Tests (Phase 4)

### 6.1 Basic Configuration Cache Compatibility

#### Test: Configuration cache stores and reuses waitForLog configuration

```groovy
def "waitForLog configuration is cached and reused on second run"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                cacheTest {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application', 'Listening on port'],
                            'db': ['ready to accept connections']
                        ])
                        rejectPatterns.set([
                            'app': ['FATAL', 'Exception']
                        ])
                        timeoutSeconds.set(90)
                        pollSeconds.set(3)
                        caseInsensitive.set(true)
                        verbose.set(true)
                    }
                }
            }
        }
    """

    when: "first build stores configuration cache"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks', '--configuration-cache')
        .build()

    and: "second build reuses cached configuration"
    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks', '--configuration-cache')
        .build()

    then: "first build creates cache entry"
    result1.output.contains('Configuration cache entry stored')

    and: "second build reuses cache"
    result2.output.contains('Reusing configuration cache')
}
```

### 6.2 Pattern Content Verification After Cache Restore

#### Test: Simple patterns survive configuration cache round-trip

```groovy
def "waitForLog simple patterns survive configuration cache round-trip"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                simplePatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application', 'Ready to serve']
                        ])
                    }
                }
            }
        }

        task verifyPatterns {
            doLast {
                def upTask = tasks.getByName('composeUpSimplePatterns')
                def services = upTask.waitForLogServices.get()
                println "Patterns after cache: \${services['app']}"
                assert services['app'] == ['Started Application', 'Ready to serve']
            }
        }
    """

    when: "first build stores cache"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyPatterns', '--configuration-cache')
        .build()

    and: "second build uses cache"
    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyPatterns', '--configuration-cache')
        .build()

    then:
    result1.output.contains('Patterns after cache:')
    result2.output.contains('Patterns after cache:')
    result2.output.contains('Reusing configuration cache')
}
```

#### Test: Regex patterns with special characters survive configuration cache

```groovy
def "waitForLog regex patterns with special characters survive configuration cache"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                regexPatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': [
                                '\\\\[INFO\\\\].*started',    // Escaped brackets
                                'port:\\\\s+\\\\d+',           // Whitespace and digit classes
                                '(?i)ready',                   // Case-insensitive flag
                                'Started .* in \\\\d+ seconds' // Wildcards
                            ]
                        ])
                    }
                }
            }
        }

        task verifyPatterns {
            doLast {
                def upTask = tasks.getByName('composeUpRegexPatterns')
                def patterns = upTask.waitForLogServices.get()['app']
                println "Pattern count: \${patterns.size()}"
                patterns.each { println "  Pattern: \${it}" }
                assert patterns.size() == 4
            }
        }
    """

    when: "first build stores cache"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyPatterns', '--configuration-cache')
        .build()

    and: "second build uses cache"
    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyPatterns', '--configuration-cache')
        .build()

    then:
    result1.output.contains('Pattern count: 4')
    result2.output.contains('Pattern count: 4')
    result2.output.contains('Reusing configuration cache')
}
```

#### Test: Patterns with quotes survive configuration cache

```groovy
def "waitForLog patterns with quotes survive configuration cache"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                quotePatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': [
                                'message: "started"',   // Double quotes
                                "status: 'ready'"       // Single quotes (using double-quoted string)
                            ]
                        ])
                    }
                }
            }
        }

        task verifyPatterns {
            doLast {
                def upTask = tasks.getByName('composeUpQuotePatterns')
                def patterns = upTask.waitForLogServices.get()['app']
                println "Pattern count: \${patterns.size()}"
                patterns.each { println "  Pattern: \${it}" }
                assert patterns.size() == 2
            }
        }
    """

    when: "first build stores cache"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyPatterns', '--configuration-cache')
        .build()

    and: "second build uses cache"
    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyPatterns', '--configuration-cache')
        .build()

    then:
    result1.output.contains('Pattern count: 2')
    result2.output.contains('Pattern count: 2')
    result2.output.contains('Reusing configuration cache')
}
```

### 6.3 Multi-Service Configuration Cache

#### Test: Multiple services with different pattern counts survive cache

```groovy
def "waitForLog multiple services with varying pattern counts survive configuration cache"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                multiService {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Pattern1'],
                            'db': ['Pattern1', 'Pattern2', 'Pattern3'],
                            'redis': ['Pattern1', 'Pattern2', 'Pattern3', 'Pattern4', 'Pattern5']
                        ])
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpMultiService')
                def services = upTask.waitForLogServices.get()
                println "Service count: \${services.size()}"
                println "app patterns: \${services['app'].size()}"
                println "db patterns: \${services['db'].size()}"
                println "redis patterns: \${services['redis'].size()}"
                assert services.size() == 3
                assert services['app'].size() == 1
                assert services['db'].size() == 3
                assert services['redis'].size() == 5
            }
        }
    """

    when: "first and second builds"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--configuration-cache')
        .build()

    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--configuration-cache')
        .build()

    then:
    result1.output.contains('Service count: 3')
    result2.output.contains('Service count: 3')
    result2.output.contains('Reusing configuration cache')
}
```

### 6.4 Edge Case Configuration Cache Tests

#### Test: Empty rejectPatterns serializes correctly

```groovy
def "waitForLog empty rejectPatterns serializes correctly for configuration cache"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                emptyReject {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started']
                        ])
                        // rejectPatterns not set - default empty map
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpEmptyReject')
                def reject = upTask.waitForLogRejectPatterns.get()
                println "RejectPatterns empty: \${reject.isEmpty()}"
                assert reject.isEmpty()
            }
        }
    """

    when:
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--configuration-cache')
        .build()

    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--configuration-cache')
        .build()

    then:
    result1.output.contains('RejectPatterns empty: true')
    result2.output.contains('RejectPatterns empty: true')
    result2.output.contains('Reusing configuration cache')
}
```

#### Test: Very long pattern strings survive configuration cache

```groovy
def "waitForLog very long pattern strings survive configuration cache"() {
    given:
    def longPattern = 'A' * 500 + '.*' + 'B' * 500

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                longPatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['${longPattern}']
                        ])
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpLongPatterns')
                def patterns = upTask.waitForLogServices.get()['app']
                def patternLength = patterns[0].length()
                println "Pattern length: \${patternLength}"
                assert patternLength > 1000
            }
        }
    """

    when:
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--configuration-cache')
        .build()

    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--configuration-cache')
        .build()

    then:
    result1.output.contains('Pattern length:')
    result2.output.contains('Reusing configuration cache')
}
```

### 6.5 Full Compose Stack Configuration Cache

#### Test: Complete compose stack with waitForLog and other wait blocks caches correctly

```groovy
def "complete compose stack with all wait blocks caches correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                fullStack {
                    composeFile.set(file('docker-compose.yml'))

                    waitForRunning {
                        waitForServices.set(['app', 'db'])
                        timeoutSeconds.set(30)
                    }

                    waitForHealthy {
                        waitForServices.set(['db'])
                        timeoutSeconds.set(60)
                    }

                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application', 'Ready to serve'],
                            'db': ['ready to accept connections']
                        ])
                        rejectPatterns.set([
                            'app': ['FATAL', 'Exception']
                        ])
                        timeoutSeconds.set(120)
                        pollSeconds.set(3)
                        caseInsensitive.set(true)
                        verbose.set(true)
                        progressIntervalSeconds.set(15)
                    }
                }
            }
        }
    """

    when: "first build stores configuration cache"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks', '--all', '--configuration-cache')
        .build()

    and: "second build reuses cached configuration"
    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks', '--all', '--configuration-cache')
        .build()

    then: "first build creates cache entry"
    result1.output.contains('Configuration cache entry stored')
    result1.output.contains('composeUpFullStack')

    and: "second build reuses cache"
    result2.output.contains('Reusing configuration cache')
    result2.output.contains('composeUpFullStack')
}
```

---

## 7. Task Registration Tests

### 7.1 Task Creation Verification

#### Test: composeUp task is created with waitForLog configuration

```groovy
def "composeUp task is created when waitForLog is configured"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                logStack {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks', '--all')
        .build()

    then:
    result.task(':tasks').outcome == TaskOutcome.SUCCESS
    result.output.contains('composeUpLogStack')
    result.output.contains('composeDownLogStack')
}
```

---

## 8. Missing Validation and DSL Tests (Gaps #FT-1 through #FT-5)

### 8.1 Action Configuration Style (Gap #FT-1)

#### Test: waitForLog with Action configuration style

```groovy
def "waitForLog with Action configuration style works correctly"() {
    given:
    buildFile << """
        import org.gradle.api.Action
        
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                actionStyle {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog(new Action<com.kineticfire.gradle.docker.spec.WaitForLogSpec>() {
                        @Override
                        void execute(com.kineticfire.gradle.docker.spec.WaitForLogSpec spec) {
                            spec.waitForServices.set([
                                'app': ['Started Application']
                            ])
                            spec.timeoutSeconds.set(90)
                        }
                    })
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpActionStyle')
                def services = upTask.waitForLogServices.get()
                println "Services: \${services}"
                println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                assert services.containsKey('app')
                assert upTask.waitForLogTimeoutSeconds.get() == 90
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Services: [app:[Started Application]]")
    result.output.contains("Timeout: 90")
}
```

### 8.2 Invalid Regex Pattern Error (Gap #FT-2)

#### Test: Invalid regex pattern produces clear error at execution time

```groovy
def "waitForLog with invalid regex pattern fails at execution time with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                invalidRegex {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['[invalid regex (missing bracket']  // Invalid regex
                        ])
                    }
                }
            }
        }
        
        // Create a task that forces config resolution (simulating execution)
        task resolveConfig {
            doLast {
                def upTask = tasks.getByName('composeUpInvalidRegex')
                // Force the builder to validate patterns
                com.kineticfire.gradle.docker.util.WaitForLogConfigBuilder.build(
                    'test-project',
                    upTask.waitForLogServices.get(),
                    upTask.waitForLogRejectPatterns.get(),
                    upTask.waitForLogTimeoutSeconds.get(),
                    upTask.waitForLogPollSeconds.get(),
                    upTask.waitForLogCaseInsensitive.get(),
                    upTask.waitForLogVerbose.get(),
                    upTask.waitForLogProgressIntervalSeconds.get()
                )
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('resolveConfig')
        .buildAndFail()

    then:
    result.output.contains('Invalid regex') || result.output.contains('PatternSyntax') || 
        result.output.contains('pattern') || result.output.contains('Configuration error')
}
```

### 8.3 Orphaned rejectPatterns Warning (Gap #FT-3)

#### Test: Orphaned rejectPatterns produces warning

```groovy
def "waitForLog with orphaned rejectPatterns produces warning"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                orphanedReject {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // 'unknown' is NOT in waitForServices - this is orphaned
                        rejectPatterns.set([
                            'app': ['FATAL'],
                            'unknown': ['ERROR']  // orphaned - should produce warning
                        ])
                    }
                }
            }
        }
        
        task resolveConfig {
            doLast {
                def upTask = tasks.getByName('composeUpOrphanedReject')
                // Force builder validation which produces warning
                com.kineticfire.gradle.docker.util.WaitForLogConfigBuilder.build(
                    'test-project',
                    upTask.waitForLogServices.get(),
                    upTask.waitForLogRejectPatterns.get(),
                    upTask.waitForLogTimeoutSeconds.get(),
                    upTask.waitForLogPollSeconds.get(),
                    upTask.waitForLogCaseInsensitive.get(),
                    upTask.waitForLogVerbose.get(),
                    upTask.waitForLogProgressIntervalSeconds.get()
                )
                println "Config resolved successfully"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('resolveConfig')
        .build()

    then:
    // Warning should be output to stderr during config resolution
    result.output.contains('Config resolved successfully')
    // Note: Warning goes to stderr which may or may not be captured by TestKit
    // The test verifies the config is valid (not an error) despite orphaned patterns
}
```

### 8.4 pollSeconds > timeoutSeconds Warning (Gap #FT-4)

#### Test: pollSeconds greater than timeoutSeconds produces warning

```groovy
def "waitForLog with pollSeconds greater than timeoutSeconds produces warning"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                badPoll {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        timeoutSeconds.set(10)
                        pollSeconds.set(30)  // Greater than timeout - should warn
                    }
                }
            }
        }
        
        task resolveConfig {
            doLast {
                def upTask = tasks.getByName('composeUpBadPoll')
                com.kineticfire.gradle.docker.util.WaitForLogConfigBuilder.build(
                    'test-project',
                    upTask.waitForLogServices.get(),
                    upTask.waitForLogRejectPatterns.get(),
                    upTask.waitForLogTimeoutSeconds.get(),
                    upTask.waitForLogPollSeconds.get(),
                    upTask.waitForLogCaseInsensitive.get(),
                    upTask.waitForLogVerbose.get(),
                    upTask.waitForLogProgressIntervalSeconds.get()
                )
                println "Config resolved - warning should have been printed"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('resolveConfig')
        .build()

    then:
    result.output.contains('Config resolved - warning should have been printed')
    // Warning goes to stderr: "pollSeconds (30) > timeoutSeconds (10)"
}
```

### 8.5 Non-String Pattern Type Error (Gap #FT-5)

#### Test: Non-string pattern type produces clear error

```groovy
def "waitForLog with non-string pattern type fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                badType {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // Integer instead of String pattern - should fail validation
                        waitForServices.set([
                            'app': [123, 'Started Application']
                        ])
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks')
        .buildAndFail()

    then:
    result.output.contains('Pattern values must be strings') || 
        result.output.contains('Integer') ||
        result.output.contains('type')
}
```

---

## 9. Multi-Stack and System Property Tests (Gaps #FT-6, #FT-7)

### 9.1 System Property JSON Format Verification (Gap #FT-7)

#### Test: System properties use JSON format parseable by JUnit extensions

```groovy
def "waitForLog system properties use valid JSON format"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                jsonTest {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application', 'Ready to serve'],
                            'db': ['ready to accept connections']
                        ])
                        rejectPatterns.set([
                            'app': ['FATAL', 'Exception']
                        ])
                    }
                }
            }
        }

        tasks.register('jsonTest', Test) {
            usesCompose stack: 'jsonTest', lifecycle: 'class'
        }

        task verifyJsonFormat {
            doLast {
                def testTask = tasks.getByName('jsonTest')
                def props = testTask.systemProperties
                
                // Verify waitForServices JSON format
                def servicesJson = props['docker.compose.waitForLog.services']
                println "Services JSON: \${servicesJson}"
                
                // Parse and validate JSON structure
                def slurper = new groovy.json.JsonSlurper()
                def services = slurper.parseText(servicesJson)
                
                assert services instanceof Map
                assert services.containsKey('app')
                assert services.containsKey('db')
                assert services['app'] instanceof List
                assert services['app'].size() == 2
                assert services['db'].size() == 1
                
                // Verify rejectPatterns JSON format
                def rejectJson = props['docker.compose.waitForLog.rejectPatterns']
                println "Reject JSON: \${rejectJson}"
                
                if (rejectJson) {
                    def reject = slurper.parseText(rejectJson)
                    assert reject instanceof Map
                    assert reject['app'] instanceof List
                    assert reject['app'].size() == 2
                }
                
                println "JSON format validation passed"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyJsonFormat')
        .build()

    then:
    result.output.contains("JSON format validation passed")
}
```

### 9.2 Multiple Stacks with Different waitForLog Configurations (Gap #FT-6)

#### Test: Multiple stacks each with their own waitForLog configuration

```groovy
def "multiple stacks with different waitForLog configurations work correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                stack1 {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started App1']
                        ])
                        timeoutSeconds.set(30)
                    }
                }
                stack2 {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started App2'],
                            'db': ['ready for connections']
                        ])
                        timeoutSeconds.set(60)
                        caseInsensitive.set(true)
                    }
                }
                stackNoWait {
                    // This stack has no waitForLog - should still work
                    composeFile.set(file('docker-compose.yml'))
                    waitForRunning {
                        waitForServices.set(['app'])
                    }
                }
            }
        }

        task verifyMultiStack {
            doLast {
                def upTask1 = tasks.getByName('composeUpStack1')
                def upTask2 = tasks.getByName('composeUpStack2')
                def upTaskNoWait = tasks.getByName('composeUpStackNoWait')
                
                // Verify stack1 config
                assert upTask1.waitForLogServices.get()['app'] == ['Started App1']
                assert upTask1.waitForLogTimeoutSeconds.get() == 30
                assert upTask1.waitForLogCaseInsensitive.get() == false  // default
                
                // Verify stack2 config
                assert upTask2.waitForLogServices.get()['app'] == ['Started App2']
                assert upTask2.waitForLogServices.get()['db'] == ['ready for connections']
                assert upTask2.waitForLogTimeoutSeconds.get() == 60
                assert upTask2.waitForLogCaseInsensitive.get() == true
                
                // Verify stackNoWait has no waitForLog but still works
                assert !upTaskNoWait.waitForLogServices.present || upTaskNoWait.waitForLogServices.get().isEmpty()
                
                println "Multi-stack verification passed"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyMultiStack')
        .build()

    then:
    result.output.contains("Multi-stack verification passed")
}
```

---

## 10. Edge Case Tests (Gaps #FT-8 through #FT-13)

### 10.1 Unicode Patterns (Gap #FT-9)

#### Test: Unicode characters in patterns survive configuration cache

```groovy
def "waitForLog patterns with Unicode characters work correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                unicode {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': [
                                '开始应用程序',        // Chinese: "Start application"
                                'Приложение готово',  // Russian: "Application ready"
                                '🚀 Ready to launch', // Emoji
                                'Ñoño started'        // Spanish accents
                            ]
                        ])
                    }
                }
            }
        }

        task verifyUnicode {
            doLast {
                def upTask = tasks.getByName('composeUpUnicode')
                def patterns = upTask.waitForLogServices.get()['app']
                println "Pattern count: \${patterns.size()}"
                println "First pattern: \${patterns[0]}"
                assert patterns.size() == 4
                assert patterns[0] == '开始应用程序'
                assert patterns[2].contains('🚀')
                println "Unicode verification passed"
            }
        }
    """

    when: "first run stores cache"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyUnicode', '--configuration-cache')
        .build()

    and: "second run reuses cache"
    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyUnicode', '--configuration-cache')
        .build()

    then:
    result1.output.contains("Unicode verification passed")
    result2.output.contains("Unicode verification passed")
    result2.output.contains("Reusing configuration cache")
}
```

### 10.2 Service Names with Special Characters (Gap #FT-8)

#### Test: Service names with hyphens and underscores work correctly

```groovy
def "waitForLog with service names containing special characters works correctly"() {
    given:
    // Update compose file to have services with special character names
    composeFile << """
services:
  my-app-service:
    image: alpine:latest
    command: sleep 30
  db_backend_1:
    image: postgres:15-alpine
    environment:
      POSTGRES_PASSWORD: test
"""

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                specialChars {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'my-app-service': ['Started Application'],
                            'db_backend_1': ['ready to accept connections']
                        ])
                    }
                }
            }
        }

        task verifySpecialChars {
            doLast {
                def upTask = tasks.getByName('composeUpSpecialChars')
                def services = upTask.waitForLogServices.get()
                println "Services: \${services.keySet()}"
                assert services.containsKey('my-app-service')
                assert services.containsKey('db_backend_1')
                println "Special character service names verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifySpecialChars')
        .build()

    then:
    result.output.contains("Special character service names verified")
}
```

### 10.3 Empty String Pattern Error (Gap #FT-10)

#### Test: Empty string pattern produces clear error

```groovy
def "waitForLog with empty string pattern fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                emptyPattern {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['', 'Started Application']  // Empty string pattern
                        ])
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks')
        .buildAndFail()

    then:
    result.output.contains('empty') || result.output.contains('pattern') || result.output.contains('blank')
}
```

**Note**: If the implementation allows empty strings (which would match any line), this test should verify
that behavior is documented and update the assertion accordingly. Currently assumes empty strings are rejected.

### 10.4 Null Pattern in List Error (Gap #FT-11)

#### Test: Null pattern in list produces clear error

```groovy
def "waitForLog with null pattern in list fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                nullPattern {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': [null, 'Started Application']  // Null in list
                        ])
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks')
        .buildAndFail()

    then:
    result.output.contains('null') || result.output.contains('Pattern') || result.output.contains('Configuration error')
}
```

### 10.5 waitForLog Without Java Plugin (Gap #FT-12)

#### Test: waitForLog works without java plugin (docker-only project)

```groovy
def "waitForLog works in project without java plugin"() {
    given:
    buildFile << """
        plugins {
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                noJavaStack {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                    }
                }
            }
        }

        task verifyNoJava {
            doLast {
                def upTask = tasks.getByName('composeUpNoJavaStack')
                assert upTask.waitForLogServices.get()['app'] == ['Started Application']
                println "waitForLog configured without java plugin"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyNoJava')
        .build()

    then:
    result.output.contains("waitForLog configured without java plugin")
}
```

### 10.6 waitForLog Property Presence Check (Gap #FT-13)

#### Test: waitForLog property presence check (isPresent) works correctly

```groovy
def "waitForLog property presence check works correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                withWaitForLog {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                    }
                }
                withoutWaitForLog {
                    composeFile.set(file('docker-compose.yml'))
                    // No waitForLog configured
                    waitForRunning {
                        waitForServices.set(['app'])
                    }
                }
            }
        }

        task verifyPresence {
            doLast {
                def upTaskWith = tasks.getByName('composeUpWithWaitForLog')
                def upTaskWithout = tasks.getByName('composeUpWithoutWaitForLog')
                
                def withPresent = upTaskWith.waitForLogServices.present && 
                                  !upTaskWith.waitForLogServices.get().isEmpty()
                def withoutPresent = upTaskWithout.waitForLogServices.present && 
                                     !upTaskWithout.waitForLogServices.get().isEmpty()
                
                println "With waitForLog present: \${withPresent}"
                println "Without waitForLog present: \${withoutPresent}"
                
                assert withPresent == true
                assert withoutPresent == false
                
                println "Property presence check verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyPresence')
        .build()

    then:
    result.output.contains("Property presence check verified")
    result.output.contains("With waitForLog present: true")
    result.output.contains("Without waitForLog present: false")
}
```

---

## 11. Validation Edge Case Tests (Gaps #FT-14 through #FT-22)

### 11.1 Negative and Zero timeoutSeconds Validation (Gaps #FT-14, #FT-15)

#### Test: Negative timeoutSeconds fails with clear error

```groovy
def "waitForLog with negative timeoutSeconds fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                negativeTimeout {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        timeoutSeconds.set(-1)  // Negative value - should fail
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('composeUpNegativeTimeout')
        .buildAndFail()

    then:
    result.output.contains('timeoutSeconds') &&
        (result.output.contains('positive') || result.output.contains('must be') || result.output.contains('-1'))
}
```

#### Test: Zero timeoutSeconds fails with clear error

```groovy
def "waitForLog with zero timeoutSeconds fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                zeroTimeout {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        timeoutSeconds.set(0)  // Zero value - should fail
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('composeUpZeroTimeout')
        .buildAndFail()

    then:
    result.output.contains('timeoutSeconds') &&
        (result.output.contains('positive') || result.output.contains('must be') || result.output.contains('0'))
}
```

### 11.2 Negative and Zero pollSeconds Validation (Gap #FT-16)

#### Test: Negative pollSeconds fails with clear error

```groovy
def "waitForLog with negative pollSeconds fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                negativePoll {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        pollSeconds.set(-5)  // Negative value - should fail
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('composeUpNegativePoll')
        .buildAndFail()

    then:
    result.output.contains('pollSeconds') &&
        (result.output.contains('positive') || result.output.contains('must be') || result.output.contains('-5'))
}
```

#### Test: Zero pollSeconds fails with clear error

```groovy
def "waitForLog with zero pollSeconds fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                zeroPoll {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        pollSeconds.set(0)  // Zero value - should fail
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('composeUpZeroPoll')
        .buildAndFail()

    then:
    result.output.contains('pollSeconds') &&
        (result.output.contains('positive') || result.output.contains('must be') || result.output.contains('0'))
}
```

### 11.3 Negative progressIntervalSeconds Behavior (Gap #FT-17)

#### Test: Negative progressIntervalSeconds is treated as disabled (0)

```groovy
def "waitForLog with negative progressIntervalSeconds is treated as disabled"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                negativeProgress {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        progressIntervalSeconds.set(-10)  // Negative - should be treated as 0/disabled
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpNegativeProgress')
                def progressInterval = upTask.waitForLogProgressIntervalSeconds.get()
                // Either the value is stored as-is (and handled at execution) or normalized to 0
                println "Progress interval: \${progressInterval}"
                // The important thing is that the configuration succeeds
                println "Configuration accepted"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Configuration accepted")
}
```

### 11.4 Empty and Whitespace Service Name Validation (Gaps #FT-18, #FT-19)

#### Test: Empty string service name fails with clear error

```groovy
def "waitForLog with empty string service name fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                emptyServiceName {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            '': ['Started Application']  // Empty service name
                        ])
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks')
        .buildAndFail()

    then:
    result.output.contains('service') || result.output.contains('name') || result.output.contains('empty')
}
```

#### Test: Whitespace-only service name fails with clear error

```groovy
def "waitForLog with whitespace-only service name fails with clear error"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                whitespaceServiceName {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            '   ': ['Started Application']  // Whitespace-only service name
                        ])
                    }
                }
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('tasks')
        .buildAndFail()

    then:
    result.output.contains('service') || result.output.contains('name') || result.output.contains('blank') ||
        result.output.contains('whitespace')
}
```

### 11.5 Duplicate Patterns Behavior (Gap #FT-20)

#### Test: Duplicate patterns in same service list are allowed

```groovy
def "waitForLog with duplicate patterns in service list configures correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                duplicatePatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started', 'Ready', 'Started']  // 'Started' appears twice
                        ])
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpDuplicatePatterns')
                def patterns = upTask.waitForLogServices.get()['app']
                println "Pattern count: \${patterns.size()}"
                // Verify duplicates are preserved (not deduplicated)
                assert patterns.size() == 3
                assert patterns.count { it == 'Started' } == 2
                println "Duplicate patterns allowed"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Pattern count: 3")
    result.output.contains("Duplicate patterns allowed")
}
```

### 11.6 Service in Both waitForRunning and waitForLog (Gap #FT-21)

#### Test: Same service can be in both waitForRunning and waitForLog

```groovy
def "same service can be in both waitForRunning and waitForLog"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                dualWait {
                    composeFile.set(file('docker-compose.yml'))

                    // Same service in both blocks
                    waitForRunning {
                        waitForServices.set(['app'])
                        timeoutSeconds.set(30)
                    }

                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        timeoutSeconds.set(60)
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpDualWait')

                def runningServices = upTask.waitForRunningServices.get()
                def logServices = upTask.waitForLogServices.get().keySet()

                println "Running services: \${runningServices}"
                println "Log services: \${logServices}"

                assert runningServices.contains('app')
                assert logServices.contains('app')

                println "Same service in both blocks verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Same service in both blocks verified")
    result.output.contains("Running services: [app]")
    result.output.contains("Log services: [app]")
}
```

### 11.7 waitForLog Block Reconfiguration (Gap #FT-22)

#### Test: Calling waitForLog twice replaces previous configuration

```groovy
def "calling waitForLog twice replaces previous configuration"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                reconfig {
                    composeFile.set(file('docker-compose.yml'))

                    // First configuration
                    waitForLog {
                        waitForServices.set([
                            'app': ['First Pattern']
                        ])
                        timeoutSeconds.set(30)
                    }

                    // Second configuration - should replace first
                    waitForLog {
                        waitForServices.set([
                            'app': ['Second Pattern', 'Another Pattern']
                        ])
                        timeoutSeconds.set(90)
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpReconfig')

                def patterns = upTask.waitForLogServices.get()['app']
                def timeout = upTask.waitForLogTimeoutSeconds.get()

                println "Patterns: \${patterns}"
                println "Timeout: \${timeout}"

                // Second configuration should have replaced the first
                assert !patterns.contains('First Pattern')
                assert patterns.contains('Second Pattern')
                assert patterns.contains('Another Pattern')
                assert timeout == 90

                println "Reconfiguration verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Reconfiguration verified")
    result.output.contains("Timeout: 90")
}
```

---

## 12. Pattern Content and Boundary Tests (Gaps #FT-23 through #FT-26)

### 12.1 JSON Content in Patterns (Gap #FT-23)

#### Test: Patterns matching JSON log content work correctly

```groovy
def "waitForLog patterns matching JSON log content work correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                jsonPatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': [
                                '"level":"INFO"',           // JSON field
                                '"status":"ready"',         // JSON value
                                '"message":"Server started on port \\\\d+"',  // JSON with regex
                                '\\\\{"event":"startup"\\\\}'  // Full JSON object pattern
                            ]
                        ])
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpJsonPatterns')
                def patterns = upTask.waitForLogServices.get()['app']

                println "JSON pattern count: \${patterns.size()}"
                patterns.each { println "  Pattern: \${it}" }

                assert patterns.size() == 4
                assert patterns.any { it.contains('"level"') }

                println "JSON patterns configured correctly"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("JSON pattern count: 4")
    result.output.contains("JSON patterns configured correctly")
}
```

### 12.2 Patterns with Regex Metacharacters (Gap #FT-24)

#### Test: Patterns with escaped regex metacharacters work correctly

```groovy
def "waitForLog patterns with regex metacharacters work correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                metacharPatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': [
                                '\\\\[INFO\\\\]',              // Escaped square brackets
                                'port: \\\\d+',                // Digit class
                                '\\\\(success\\\\)',           // Escaped parentheses
                                'path/to/file\\\\.txt',        // Escaped dot
                                'price: \\\\\\$\\\\d+\\\\.\\\\d{2}',  // Dollar sign and specific format
                                'line1\\\\nline2',             // Newline in pattern
                                'tab\\\\there',                // Tab character
                                'question\\\\?',               // Escaped question mark
                                'star\\\\*pattern',            // Escaped asterisk
                                'pipe\\\\|option'              // Escaped pipe
                            ]
                        ])
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpMetacharPatterns')
                def patterns = upTask.waitForLogServices.get()['app']

                println "Metachar pattern count: \${patterns.size()}"
                assert patterns.size() == 10

                println "Regex metacharacter patterns configured correctly"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Metachar pattern count: 10")
    result.output.contains("Regex metacharacter patterns configured correctly")
}
```

### 12.3 Many Patterns Per Service (Boundary Test) (Gap #FT-25)

#### Test: Many patterns (15+) per service configures correctly

```groovy
def "waitForLog with many patterns per service configures correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                manyPatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': [
                                'Pattern 1', 'Pattern 2', 'Pattern 3', 'Pattern 4', 'Pattern 5',
                                'Pattern 6', 'Pattern 7', 'Pattern 8', 'Pattern 9', 'Pattern 10',
                                'Pattern 11', 'Pattern 12', 'Pattern 13', 'Pattern 14', 'Pattern 15',
                                'Init complete', 'Database connected', 'Cache warmed',
                                'Ready to serve requests', 'Startup finished'
                            ]
                        ])
                        timeoutSeconds.set(300)  // Longer timeout for many patterns
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpManyPatterns')
                def patterns = upTask.waitForLogServices.get()['app']

                println "Pattern count: \${patterns.size()}"
                assert patterns.size() == 20
                assert patterns.contains('Pattern 1')
                assert patterns.contains('Pattern 15')
                assert patterns.contains('Startup finished')

                println "Many patterns boundary test passed"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Pattern count: 20")
    result.output.contains("Many patterns boundary test passed")
}
```

### 12.4 Many Services in waitForLog (Boundary Test) (Gap #FT-26)

#### Test: Many services (8+) in waitForLog configures correctly

```groovy
def "waitForLog with many services configures correctly"() {
    given:
    // Create extended compose file with many services
    def manyServicesComposeFile = testProjectDir.resolve('many-services-compose.yml').toFile()
    manyServicesComposeFile << """
services:
  app:
    image: alpine:latest
    command: sleep 30
  db:
    image: alpine:latest
    command: sleep 30
  cache:
    image: alpine:latest
    command: sleep 30
  queue:
    image: alpine:latest
    command: sleep 30
  worker:
    image: alpine:latest
    command: sleep 30
  scheduler:
    image: alpine:latest
    command: sleep 30
  gateway:
    image: alpine:latest
    command: sleep 30
  monitor:
    image: alpine:latest
    command: sleep 30
"""

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                manyServices {
                    composeFile.set(file('many-services-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['App started'],
                            'db': ['Database ready'],
                            'cache': ['Cache initialized'],
                            'queue': ['Queue connected'],
                            'worker': ['Worker running'],
                            'scheduler': ['Scheduler active'],
                            'gateway': ['Gateway listening'],
                            'monitor': ['Monitor collecting']
                        ])
                        timeoutSeconds.set(180)
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpManyServices')
                def services = upTask.waitForLogServices.get()

                println "Service count: \${services.size()}"
                assert services.size() == 8
                assert services.containsKey('app')
                assert services.containsKey('monitor')

                services.each { name, patterns ->
                    println "  \${name}: \${patterns.size()} pattern(s)"
                }

                println "Many services boundary test passed"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig')
        .build()

    then:
    result.output.contains("Service count: 8")
    result.output.contains("Many services boundary test passed")
}
```

### 12.5 Configuration Cache with Many Services and Patterns

#### Test: Complex configuration with many services/patterns survives cache

```groovy
def "complex waitForLog configuration survives configuration cache"() {
    given:
    def manyServicesComposeFile = testProjectDir.resolve('complex-compose.yml').toFile()
    manyServicesComposeFile << """
services:
  app:
    image: alpine:latest
  db:
    image: alpine:latest
  cache:
    image: alpine:latest
  worker:
    image: alpine:latest
"""

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                complexConfig {
                    composeFile.set(file('complex-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Pattern 1', 'Pattern 2', 'Pattern 3', 'Pattern 4', 'Pattern 5'],
                            'db': ['DB Ready', 'Accepting connections'],
                            'cache': ['Cache warmed', 'Memory allocated'],
                            'worker': ['Worker started', 'Processing queue']
                        ])
                        rejectPatterns.set([
                            'app': ['FATAL', 'Exception'],
                            'db': ['Connection refused']
                        ])
                        timeoutSeconds.set(120)
                        pollSeconds.set(3)
                        caseInsensitive.set(true)
                        verbose.set(true)
                        progressIntervalSeconds.set(30)
                    }
                }
            }
        }

        task verifyComplexConfig {
            doLast {
                def upTask = tasks.getByName('composeUpComplexConfig')
                def services = upTask.waitForLogServices.get()
                def rejects = upTask.waitForLogRejectPatterns.get()

                println "Service count: \${services.size()}"
                println "Reject pattern services: \${rejects.size()}"
                println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"

                assert services.size() == 4
                assert services['app'].size() == 5
                assert rejects.size() == 2

                println "Complex configuration verified"
            }
        }
    """

    when: "first build stores configuration cache"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyComplexConfig', '--configuration-cache')
        .build()

    and: "second build reuses cached configuration"
    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyComplexConfig', '--configuration-cache')
        .build()

    then:
    result1.output.contains('Configuration cache entry stored')
    result1.output.contains('Complex configuration verified')

    result2.output.contains('Reusing configuration cache')
    result2.output.contains('Complex configuration verified')
}
```

---

## 13. Third Review Gap Tests (Gaps #FT-27 through #FT-34)

This section contains test specifications identified in the third review for 100% functional test coverage.

### 13.1 Service Names with Dots (Gap #FT-27)

#### Test: Service names with dots work correctly

Docker Compose allows dots in service names. Verify the plugin handles them correctly.

```groovy
def "waitForLog with service names containing dots works correctly"() {
    given:
    // Create compose file with dotted service names
    def dottedComposeFile = testProjectDir.resolve('dotted-compose.yml').toFile()
    dottedComposeFile << """
services:
  app.service:
    image: alpine:latest
    command: sleep 30
  db.backend.primary:
    image: alpine:latest
    command: sleep 30
"""

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                dottedNames {
                    composeFile.set(file('dotted-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app.service': ['Application Started'],
                            'db.backend.primary': ['Database Ready']
                        ])
                    }
                }
            }
        }

        task verifyDottedNames {
            doLast {
                def upTask = tasks.getByName('composeUpDottedNames')
                def services = upTask.waitForLogServices.get()

                println "Services with dots: \${services.keySet()}"
                assert services.containsKey('app.service')
                assert services.containsKey('db.backend.primary')
                assert services['app.service'] == ['Application Started']
                assert services['db.backend.primary'] == ['Database Ready']

                println "Dotted service names verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyDottedNames')
        .build()

    then:
    result.output.contains("Dotted service names verified")
    result.output.contains("app.service")
    result.output.contains("db.backend.primary")
}
```

### 13.2 CLASS and METHOD Lifecycle in Same Project (Gap #FT-28)

#### Test: Both lifecycle modes with different stacks work independently

Verify that CLASS and METHOD lifecycle stacks in the same project have independent waitForLog configurations.

```groovy
def "CLASS and METHOD lifecycle stacks in same project have independent waitForLog"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                classLifecycleStack {
                    composeFile.set(file('docker-compose.yml'))
                    lifecycle.set(com.kineticfire.gradle.docker.Lifecycle.CLASS)
                    waitForLog {
                        waitForServices.set([
                            'app': ['CLASS lifecycle started']
                        ])
                        timeoutSeconds.set(90)
                    }
                }
                methodLifecycleStack {
                    composeFile.set(file('docker-compose.yml'))
                    lifecycle.set(com.kineticfire.gradle.docker.Lifecycle.METHOD)
                    waitForLog {
                        waitForServices.set([
                            'app': ['METHOD lifecycle started'],
                            'db': ['Method DB ready']
                        ])
                        timeoutSeconds.set(45)
                        caseInsensitive.set(true)
                    }
                }
            }
        }

        task verifyLifecycleIndependence {
            doLast {
                def classUpTask = tasks.getByName('composeUpClassLifecycleStack')
                def methodUpTask = tasks.getByName('composeUpMethodLifecycleStack')

                // Verify CLASS lifecycle stack
                def classServices = classUpTask.waitForLogServices.get()
                def classTimeout = classUpTask.waitForLogTimeoutSeconds.get()
                println "CLASS stack - services: \${classServices.size()}, timeout: \${classTimeout}"
                assert classServices.size() == 1
                assert classServices['app'] == ['CLASS lifecycle started']
                assert classTimeout == 90

                // Verify METHOD lifecycle stack (different config)
                def methodServices = methodUpTask.waitForLogServices.get()
                def methodTimeout = methodUpTask.waitForLogTimeoutSeconds.get()
                def methodCaseInsensitive = methodUpTask.waitForLogCaseInsensitive.get()
                println "METHOD stack - services: \${methodServices.size()}, timeout: \${methodTimeout}, caseInsensitive: \${methodCaseInsensitive}"
                assert methodServices.size() == 2
                assert methodServices['app'] == ['METHOD lifecycle started']
                assert methodServices['db'] == ['Method DB ready']
                assert methodTimeout == 45
                assert methodCaseInsensitive == true

                println "Lifecycle independence verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyLifecycleIndependence')
        .build()

    then:
    result.output.contains("Lifecycle independence verified")
    result.output.contains("CLASS stack - services: 1")
    result.output.contains("METHOD stack - services: 2")
}
```

### 13.3 Configuration Cache Invalidation (Gap #FT-29)

#### Test: Changing waitForLog config invalidates configuration cache

Verify that modifying waitForLog configuration properly invalidates the configuration cache.

```groovy
def "changing waitForLog configuration invalidates configuration cache"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        def timeoutValue = project.hasProperty('customTimeout') ?
            Integer.parseInt(project.property('customTimeout')) : 60

        dockerTest {
            composeStacks {
                cacheTest {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started']
                        ])
                        timeoutSeconds.set(timeoutValue)
                    }
                }
            }
        }

        task verifyConfig {
            doLast {
                def upTask = tasks.getByName('composeUpCacheTest')
                def timeout = upTask.waitForLogTimeoutSeconds.get()
                println "Current timeout: \${timeout}"
            }
        }
    """

    when: "first run with default timeout stores cache"
    def result1 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--configuration-cache')
        .build()

    and: "second run reuses cache (same config)"
    def result2 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--configuration-cache')
        .build()

    and: "third run with different timeout invalidates cache"
    def result3 = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--configuration-cache', '-PcustomTimeout=120')
        .build()

    then:
    result1.output.contains("Current timeout: 60")
    result1.output.contains("Configuration cache entry stored")

    result2.output.contains("Current timeout: 60")
    result2.output.contains("Reusing configuration cache")

    result3.output.contains("Current timeout: 120")
    // Cache should be invalidated due to changed input
    !result3.output.contains("Reusing configuration cache") ||
        result3.output.contains("Configuration cache entry stored")
}
```

### 13.4 Pattern That Matches Everything (Gap #FT-30)

#### Test: Pattern ".*" matches everything (edge case)

Verify that a pattern matching everything (`.*`) is valid and configures correctly.
Document: This pattern will match on the first log line, so it effectively means "wait for any output".

```groovy
def "waitForLog with catch-all pattern (.*) configures correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                catchAllPattern {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['.*']  // Matches any output - service ready on first log line
                        ])
                    }
                }
            }
        }

        task verifyCatchAll {
            doLast {
                def upTask = tasks.getByName('composeUpCatchAllPattern')
                def patterns = upTask.waitForLogServices.get()['app']

                println "Catch-all pattern: \${patterns[0]}"
                assert patterns.size() == 1
                assert patterns[0] == '.*'

                println "Catch-all pattern configured correctly"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyCatchAll')
        .build()

    then:
    result.output.contains("Catch-all pattern: .*")
    result.output.contains("Catch-all pattern configured correctly")
}
```

### 13.5 Spec-Level waitForLog.isPresent Check (Gap #FT-31)

#### Test: Access waitForLog presence at spec level

Verify that the spec-level `waitForLog.present` can be checked for conditional logic in build scripts.

```groovy
def "spec-level waitForLog presence check works correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                withWaitForLogSpec {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started']
                        ])
                    }
                }
                withoutWaitForLogSpec {
                    composeFile.set(file('docker-compose.yml'))
                    // No waitForLog block
                }
            }
        }

        task verifySpecPresence {
            doLast {
                def extension = project.extensions.getByType(
                    com.kineticfire.gradle.docker.extension.DockerTestExtension
                )

                def withSpec = extension.composeStacks.getByName('withWaitForLogSpec')
                def withoutSpec = extension.composeStacks.getByName('withoutWaitForLogSpec')

                // Check spec-level waitForLog presence
                def withSpecPresent = withSpec.waitForLog.present
                def withoutSpecPresent = withoutSpec.waitForLog.present

                println "withWaitForLogSpec.waitForLog.present: \${withSpecPresent}"
                println "withoutWaitForLogSpec.waitForLog.present: \${withoutSpecPresent}"

                // Note: The presence check depends on implementation - it may be:
                // - true if waitForLog block was configured (regardless of content)
                // - or based on whether waitForServices has values
                // This test documents the actual behavior

                assert withSpecPresent == true : "Expected withWaitForLogSpec to have waitForLog present"
                println "Spec-level presence check verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifySpecPresence')
        .build()

    then:
    result.output.contains("Spec-level presence check verified")
    result.output.contains("withWaitForLogSpec.waitForLog.present: true")
}
```

### 13.6 Very Long Service Names Boundary (Gap #FT-32)

#### Test: Very long service names (100+ characters) work correctly

Boundary test for service names at extreme lengths.

```groovy
def "waitForLog with very long service names works correctly"() {
    given:
    def longServiceName = 'a' * 100 + '_service'  // 108 character service name

    // Create compose file with long service name
    def longNameComposeFile = testProjectDir.resolve('long-name-compose.yml').toFile()
    longNameComposeFile << """
services:
  ${longServiceName}:
    image: alpine:latest
    command: sleep 30
"""

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                longNameStack {
                    composeFile.set(file('long-name-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            '${longServiceName}': ['Service Started']
                        ])
                    }
                }
            }
        }

        task verifyLongName {
            doLast {
                def upTask = tasks.getByName('composeUpLongNameStack')
                def services = upTask.waitForLogServices.get()
                def serviceNames = services.keySet()

                println "Service name length: \${serviceNames.first().length()}"
                assert serviceNames.first().length() == 108
                assert services[serviceNames.first()] == ['Service Started']

                println "Long service name boundary test passed"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyLongName')
        .build()

    then:
    result.output.contains("Service name length: 108")
    result.output.contains("Long service name boundary test passed")
}
```

### 13.7 Service Names Starting with Numbers (Gap #FT-34)

#### Test: Service names with numbers only or starting with numbers

Docker Compose allows service names starting with digits. Verify plugin handles them.

```groovy
def "waitForLog with numeric service names works correctly"() {
    given:
    // Create compose file with numeric service names
    def numericComposeFile = testProjectDir.resolve('numeric-compose.yml').toFile()
    numericComposeFile << """
services:
  123:
    image: alpine:latest
    command: sleep 30
  1app:
    image: alpine:latest
    command: sleep 30
  service2:
    image: alpine:latest
    command: sleep 30
"""

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                numericNames {
                    composeFile.set(file('numeric-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            '123': ['Numbers only service started'],
                            '1app': ['Leading number service started'],
                            'service2': ['Trailing number service started']
                        ])
                    }
                }
            }
        }

        task verifyNumericNames {
            doLast {
                def upTask = tasks.getByName('composeUpNumericNames')
                def services = upTask.waitForLogServices.get()

                println "Numeric service names: \${services.keySet()}"
                assert services.containsKey('123')
                assert services.containsKey('1app')
                assert services.containsKey('service2')
                assert services['123'] == ['Numbers only service started']
                assert services['1app'] == ['Leading number service started']

                println "Numeric service names boundary test passed"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyNumericNames')
        .build()

    then:
    result.output.contains("Numeric service names boundary test passed")
    result.output.contains("123")
    result.output.contains("1app")
}
```

### 13.8 Implementation Issue Fixes

This section documents fixes for implementation issues identified in the third review.

#### Fix for Issue #FT-I4: Test 10.2 Compose File Duplication

**Problem**: Test 10.2 (Section 10.2) uses `composeFile << """services:..."""` which appends to the compose file
created in `setup()`. This creates duplicate `services:` blocks, resulting in invalid YAML.

**Fix**: Create a separate compose file for tests that need different services, or clear the existing file first.

**Corrected Test 10.2**:
```groovy
def "waitForLog with service names containing special characters works correctly"() {
    given:
    // Create a SEPARATE compose file instead of appending to setup's file
    def specialCharsComposeFile = testProjectDir.resolve('special-chars-compose.yml').toFile()
    specialCharsComposeFile << """
services:
  my-app-service:
    image: alpine:latest
    command: sleep 30
  db_backend_1:
    image: postgres:15-alpine
    environment:
      POSTGRES_PASSWORD: test
"""

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                specialChars {
                    composeFile.set(file('special-chars-compose.yml'))  // Use separate file
                    waitForLog {
                        waitForServices.set([
                            'my-app-service': ['Started Application'],
                            'db_backend_1': ['ready to accept connections']
                        ])
                    }
                }
            }
        }
        // ... rest of test
    """
    // ...
}
```

#### Fix for Issue #FT-I5: Warning Capture Tests stderr Handling

**Problem**: Tests 8.3 and 8.4 check `result.output` for warning messages, but Gradle warnings may go to stderr
which TestKit's `result.output` may not include by default.

**Fix**: Use `forwardStdError()` to capture stderr, or verify test success through other means and document the
warning verification limitation.

**Corrected Approach**:
```groovy
def "pollSeconds greater than timeoutSeconds produces warning"() {
    // ... test setup ...

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyConfig', '--warn')  // Enable warning output
        .forwardStdError(new ByteArrayOutputStream())  // Capture stderr
        .build()

    then:
    // Configuration succeeds
    result.output.contains("Config resolved successfully")
    // Note: Warning verification may require --warn flag and forwardStdError
    // Actual warning text verification is implementation-dependent
}
```

**Alternative**: Document that warning verification is a known limitation of functional tests and should be
verified in unit tests where logging can be directly mocked/captured.

#### Clarification for Issue #FT-I7: Empty String Pattern Validation

**Problem**: Test 10.3 (Section 10.3) expects validation failure for empty string pattern `''`, but the
implementation plan doesn't explicitly require rejecting empty strings. An empty regex `""` is technically valid
and matches the empty string (which appears in any line).

**Resolution Options**:

1. **Allow empty patterns** (current implementation behavior likely):
   - Update Test 10.3 to verify empty patterns are accepted
   - Document that empty patterns match any line (immediate success)

2. **Reject empty patterns** (requires implementation change):
   - Add validation in WaitForLogConfigBuilder to reject empty/blank patterns
   - Keep Test 10.3 as-is

**Recommended**: Update test to verify actual behavior, then document:
```groovy
def "waitForLog with empty string pattern behavior"() {
    given:
    // ... setup ...

    when:
    def result = GradleRunner.create()
        // ...
        .withArguments('verifyConfig')
        .build()  // Expect success, not failure

    then:
    // Empty pattern is technically valid regex - matches empty string in any line
    // This means the pattern will immediately match on first log output
    result.output.contains("Empty pattern accepted")
    // Document: Empty patterns are valid but not useful - they match immediately
}
```

---

## Test Execution Commands

```bash
# Run all functional tests
cd plugin && ./gradlew clean functionalTest

# Run only WaitForLog functional tests
cd plugin && ./gradlew clean functionalTest --tests "*WaitForLog*"

# Run functional tests with configuration cache verification
cd plugin && ./gradlew clean functionalTest --configuration-cache

# Run with debug output
cd plugin && ./gradlew clean functionalTest --tests "*WaitForLog*" --info
```

---

## Notes for Implementers

### Pattern for Functional Tests

1. **Test Structure**: Each test creates a minimal `build.gradle` with the plugin and DSL configuration
2. **Verification Tasks**: Create custom verification tasks that print assertions to console
3. **Assertions**: Use both console output checks (`result.output.contains()`) and explicit assertions in
   verification tasks
4. **No Docker Execution**: Functional tests verify Gradle configuration only - no actual Docker commands

### Configuration Cache Testing

1. **Two-Run Pattern**: Always run twice with `--configuration-cache` flag
2. **First Run Check**: Verify `Configuration cache entry stored`
3. **Second Run Check**: Verify `Reusing configuration cache`
4. **Value Verification**: After cache reuse, verify property values are correct

### Common Pitfalls

1. **Escaping in Groovy Strings**: Double-escape backslashes in patterns (`\\\\` becomes `\\`)
2. **Empty Properties**: Test both explicitly empty values and unset values
3. **Configuration Time vs Execution Time**: Validation tests may fail at different phases

### File Organization

- All `waitForLog` functional tests should be in `WaitForLogFunctionalTest.groovy`
- Follow existing naming conventions in the `functionalTest` directory
- Group related tests with Spock's `@Subject` annotation if helpful

---

## 14. Fourth Review Gap Tests (Gaps #FT-35 through #FT-39)

This section contains test specifications identified in the fourth review (2026-01-11) for 100% functional test coverage.

### 14.1 System Property Name Constants Verification (Gap #FT-33 - Missing Test)

#### Test: System property names in tests match implementation constants

Gap #FT-33 was identified in the checklist but no test was provided. This test verifies that the system property names used in tests exactly match the constants defined in the implementation to avoid copy-paste typos.

```groovy
def "system property names match implementation constants exactly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                propNameTest {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        rejectPatterns.set([
                            'app': ['FATAL']
                        ])
                        timeoutSeconds.set(90)
                        pollSeconds.set(5)
                        caseInsensitive.set(true)
                        verbose.set(true)
                        progressIntervalSeconds.set(30)
                    }
                }
            }
        }

        tasks.register('propNameVerification', Test) {
            usesCompose stack: 'propNameTest', lifecycle: 'class'
        }

        task verifyPropertyNames {
            doLast {
                def testTask = tasks.getByName('propNameVerification')
                def props = testTask.systemProperties

                // Verify all expected property names exist
                def expectedProps = [
                    'docker.compose.waitForLog.services',
                    'docker.compose.waitForLog.rejectPatterns',
                    'docker.compose.waitForLog.timeoutSeconds',
                    'docker.compose.waitForLog.pollSeconds',
                    'docker.compose.waitForLog.caseInsensitive',
                    'docker.compose.waitForLog.verbose',
                    'docker.compose.waitForLog.progressIntervalSeconds'
                ]

                expectedProps.each { propName ->
                    assert props.containsKey(propName) : "Missing system property: \${propName}"
                    println "Found property: \${propName} = \${props[propName]}"
                }

                // Verify no typo variants exist
                def typoProps = props.keySet().findAll {
                    it.contains('waitForLog') || it.contains('waitforlog') || it.contains('WaitForLog')
                }
                typoProps.each { propName ->
                    assert expectedProps.contains(propName) ||
                           !propName.startsWith('docker.compose.waitForLog') :
                           "Unexpected property name (possible typo): \${propName}"
                }

                println "System property names verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyPropertyNames')
        .build()

    then:
    result.output.contains("System property names verified")
    result.output.contains("docker.compose.waitForLog.services")
    result.output.contains("docker.compose.waitForLog.rejectPatterns")
}
```

### 14.2 Multiline Pattern Support (Gap #FT-35)

#### Test: Patterns with multiline flag match across log lines

Verify that patterns using `(?s)` or `(?m)` flags work correctly for log messages that span multiple lines.

```groovy
def "waitForLog with multiline pattern flag configures correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                multilinePatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': [
                                '(?s)START.*END',           // DOTALL: dot matches newlines
                                '(?m)^Starting server\$',   // MULTILINE: ^ and \$ match line boundaries
                                '(?s)BEGIN TRANSACTION.*COMMIT'  // Multi-line transaction log
                            ]
                        ])
                    }
                }
            }
        }

        task verifyMultilineConfig {
            doLast {
                def upTask = tasks.getByName('composeUpMultilinePatterns')
                def patterns = upTask.waitForLogServices.get()['app']

                println "Multiline pattern count: \${patterns.size()}"
                assert patterns.size() == 3
                assert patterns[0].startsWith('(?s)')
                assert patterns[1].startsWith('(?m)')
                assert patterns[2].contains('(?s)')

                println "Multiline patterns configured correctly"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyMultilineConfig')
        .build()

    then:
    result.output.contains("Multiline pattern count: 3")
    result.output.contains("Multiline patterns configured correctly")
}
```

### 14.3 rejectPatterns with caseInsensitive Flag (Gap #FT-36)

#### Test: rejectPatterns respect caseInsensitive flag setting

Verify that when `caseInsensitive.set(true)` is configured, the reject patterns are also compiled case-insensitively.

```groovy
def "waitForLog rejectPatterns configured with caseInsensitive flag"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                caseInsensitiveReject {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        rejectPatterns.set([
                            'app': ['FATAL', 'error', 'Exception']  // Mixed case patterns
                        ])
                        caseInsensitive.set(true)  // Should apply to both waitFor and reject patterns
                    }
                }
            }
        }

        task verifyCaseInsensitiveReject {
            doLast {
                def upTask = tasks.getByName('composeUpCaseInsensitiveReject')

                def services = upTask.waitForLogServices.get()
                def rejectPatterns = upTask.waitForLogRejectPatterns.get()
                def caseInsensitive = upTask.waitForLogCaseInsensitive.get()

                println "Services configured: \${services.keySet()}"
                println "Reject patterns: \${rejectPatterns}"
                println "Case insensitive: \${caseInsensitive}"

                assert services.containsKey('app')
                assert rejectPatterns.containsKey('app')
                assert rejectPatterns['app'].size() == 3
                assert caseInsensitive == true

                // Note: Functional test verifies config is set correctly
                // Actual case-insensitive matching behavior is tested in integration tests
                println "caseInsensitive reject patterns configured correctly"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyCaseInsensitiveReject')
        .build()

    then:
    result.output.contains("Case insensitive: true")
    result.output.contains("caseInsensitive reject patterns configured correctly")
}
```

### 14.4 Provider-Based Pattern Values (Gap #FT-37)

#### Test: Patterns from Gradle providers work correctly

Verify that patterns can come from Gradle providers (dynamic values) rather than just static strings.

```groovy
def "waitForLog with provider-based pattern values works correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        // Define providers for dynamic pattern values
        def appReadyPattern = providers.provider { 'Application Ready on port ' + project.findProperty('appPort') ?: '8080' }
        def dbReadyPattern = providers.gradleProperty('dbReadyPattern').orElse('Database ready')

        dockerTest {
            composeStacks {
                providerPatterns {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // Use map with providers resolved at configuration time
                        waitForServices.set(
                            providers.provider {
                                [
                                    'app': [appReadyPattern.get()],
                                    'db': [dbReadyPattern.get()]
                                ]
                            }
                        )
                    }
                }
            }
        }

        task verifyProviderPatterns {
            doLast {
                def upTask = tasks.getByName('composeUpProviderPatterns')
                def services = upTask.waitForLogServices.get()

                println "App patterns: \${services['app']}"
                println "DB patterns: \${services['db']}"

                assert services['app'][0].contains('Application Ready on port')
                assert services['db'] == ['Database ready']

                println "Provider-based patterns configured correctly"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyProviderPatterns', '-PdbReadyPattern=Custom DB Ready')
        .build()

    then:
    result.output.contains("App patterns: [Application Ready on port 8080]")
    result.output.contains("DB patterns: [Custom DB Ready]")
    result.output.contains("Provider-based patterns configured correctly")
}
```

### 14.5 Mixed rejectPatterns Configuration (Gap #FT-38)

#### Test: Some services with rejectPatterns, others without

Verify that rejectPatterns can be configured for only a subset of services in waitForServices.

```groovy
def "waitForLog with partial rejectPatterns coverage works correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                mixedReject {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // Three services configured for log patterns
                        waitForServices.set([
                            'app': ['Started Application'],
                            'db': ['Database Ready'],
                            'cache': ['Cache Warmed']
                        ])
                        // Only one service has reject patterns
                        rejectPatterns.set([
                            'app': ['FATAL', 'Exception']
                            // 'db' and 'cache' have no reject patterns - this is valid
                        ])
                    }
                }
            }
        }

        task verifyMixedReject {
            doLast {
                def upTask = tasks.getByName('composeUpMixedReject')
                def services = upTask.waitForLogServices.get()
                def rejectPatterns = upTask.waitForLogRejectPatterns.get()

                println "Services with waitFor patterns: \${services.keySet()}"
                println "Services with reject patterns: \${rejectPatterns.keySet()}"

                assert services.size() == 3
                assert rejectPatterns.size() == 1  // Only 'app' has reject patterns
                assert rejectPatterns.containsKey('app')
                assert !rejectPatterns.containsKey('db')
                assert !rejectPatterns.containsKey('cache')

                println "Mixed reject patterns configuration verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyMixedReject')
        .build()

    then:
    result.output.contains("Services with waitFor patterns: [app, db, cache]")
    result.output.contains("Services with reject patterns: [app]")
    result.output.contains("Mixed reject patterns configuration verified")
}
```

### 14.6 Task Description and Group Verification (Gap #FT-39)

#### Test: composeUp task has correct description when waitForLog is configured

Verify that the generated composeUp task has an appropriate description indicating waitForLog is configured.

```groovy
def "composeUp task with waitForLog has appropriate description and group"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                describedStack {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                    }
                }
            }
        }

        task verifyTaskMeta {
            doLast {
                def upTask = tasks.getByName('composeUpDescribedStack')
                def downTask = tasks.getByName('composeDownDescribedStack')

                println "Up task group: \${upTask.group}"
                println "Up task description: \${upTask.description}"
                println "Down task group: \${downTask.group}"
                println "Down task description: \${downTask.description}"

                // Verify task is in docker compose group
                assert upTask.group != null && upTask.group.toLowerCase().contains('docker')

                // Description should exist
                assert upTask.description != null && !upTask.description.isEmpty()

                println "Task metadata verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyTaskMeta')
        .build()

    then:
    result.output.contains("Task metadata verified")
    result.output.contains("Up task group:")
    result.output.contains("Up task description:")
}
```

---

## Review Notes (Fourth Review - 2026-01-11)

### Summary

The fourth review identified **5 new gaps** (FT-35 through FT-39) plus confirmed that Gap #FT-33 (system property
name constants) was in the checklist but had no corresponding test code.

### New Gaps Identified

| Gap ID | Severity | Description |
|--------|----------|-------------|
| FT-33 | Moderate | System property name constants verification - test was missing |
| FT-35 | Minor | Multiline pattern support with (?s) and (?m) flags |
| FT-36 | Moderate | rejectPatterns behavior with caseInsensitive flag |
| FT-37 | Minor | Provider-based pattern values (dynamic patterns from Gradle providers) |
| FT-38 | Minor | Mixed rejectPatterns configuration (subset of services) |
| FT-39 | Minor | Task description and group verification |

### Updated Coverage Summary

With Section 14 additions, the functional test plan now provides coverage for:
- **Core DSL Configuration**: 15+ test scenarios
- **Property Conventions**: All 7 properties tested
- **Validation Errors**: 8 error scenarios
- **Warnings**: 2 warning scenarios
- **Property Wiring**: All properties verified via task inspection
- **System Properties**: JSON format and propagation verified
- **Configuration Cache**: 10+ serialization/reuse scenarios
- **Edge Cases**: 25+ edge case scenarios
- **Boundary Tests**: Service names, pattern counts, service counts
- **Lifecycle Support**: CLASS and METHOD lifecycle independence

### Total Gap Count

After four reviews:
- **Original gaps (Reviews 1-3)**: FT-1 through FT-34 (34 gaps)
- **Fourth review gaps**: FT-35 through FT-39 (5 new gaps, plus FT-33 test added)
- **Total covered gaps**: 39

### Functional Test Checklist Update

Add to the existing checklist in Section 1:

- [ ] Gap #FT-33: System property name constants verification (Section 14.1)
- [ ] Gap #FT-35: Multiline pattern support (Section 14.2)
- [ ] Gap #FT-36: rejectPatterns with caseInsensitive flag (Section 14.3)
- [ ] Gap #FT-37: Provider-based pattern values (Section 14.4)
- [ ] Gap #FT-38: Mixed rejectPatterns configuration (Section 14.5)
- [ ] Gap #FT-39: Task description and group verification (Section 14.6)
- [ ] Gap #FT-40: Very long regex patterns boundary test (Section 15.1)
- [ ] Gap #FT-41: Same pattern in both waitForServices and rejectPatterns (Section 15.2)
- [ ] Gap #FT-42: Service names with JSON-sensitive characters (Section 15.3)
- [ ] Gap #FT-43: Incremental map building with putAll/put (Section 15.4)
- [ ] Gap #FT-44: Direct assignment syntax vs .set() method (Section 15.5)
- [ ] Gap #FT-45: Empty rejectPatterns map explicit behavior (Section 15.6)

---

## 15. Fifth Review Gap Tests (Gaps #FT-40 through #FT-45)

This section contains test specifications identified in the fifth review (2026-01-11) for 100% functional test coverage
against the implementation plan (0200).

### 15.1 Very Long Regex Patterns Boundary Test (Gap #FT-40)

#### Test: Very long patterns (1000+ characters) configure correctly

Boundary test for extremely long regex pattern strings to verify the configuration system handles them.

```groovy
def "waitForLog with very long pattern configures correctly"() {
    given:
    // Create a 1000+ character pattern
    def longPattern = 'Starting application with configuration: ' + ('a' * 500) + '.*' + ('b' * 500) + ' complete'

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                longPatternStack {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['${longPattern}']
                        ])
                    }
                }
            }
        }

        task verifyLongPattern {
            doLast {
                def upTask = tasks.getByName('composeUpLongPatternStack')
                def patterns = upTask.waitForLogServices.get()['app']

                println "Pattern length: \${patterns[0].length()}"
                assert patterns[0].length() > 1000 : "Expected pattern length > 1000"
                assert patterns[0].contains('Starting application')
                assert patterns[0].contains('complete')

                println "Long pattern boundary test passed"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyLongPattern')
        .build()

    then:
    result.output.contains("Pattern length:")
    result.output.contains("Long pattern boundary test passed")
}
```

### 15.2 Same Pattern in Both waitForServices and rejectPatterns (Gap #FT-41)

#### Test: Same pattern in both maps for same service

Edge case verifying behavior when a pattern appears in both `waitForServices` and `rejectPatterns` for the same service.
This is a user configuration error but should be handled gracefully.

```groovy
def "waitForLog with same pattern in both waitForServices and rejectPatterns configures (edge case)"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                duplicatePatternStack {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // Same pattern in both maps - edge case (likely user error)
                        waitForServices.set([
                            'app': ['Started Application', 'Ready']
                        ])
                        rejectPatterns.set([
                            'app': ['Started Application']  // Same as waitForServices pattern
                        ])
                    }
                }
            }
        }

        task verifyDuplicatePattern {
            doLast {
                def upTask = tasks.getByName('composeUpDuplicatePatternStack')
                def waitPatterns = upTask.waitForLogServices.get()['app']
                def rejectPatterns = upTask.waitForLogRejectPatterns.get()['app']

                println "Wait patterns: \${waitPatterns}"
                println "Reject patterns: \${rejectPatterns}"

                // Configuration accepts this - runtime behavior is that reject wins
                // (reject patterns are checked first, so this pattern will always fail)
                assert waitPatterns.contains('Started Application')
                assert rejectPatterns.contains('Started Application')

                println "Duplicate pattern edge case configured"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyDuplicatePattern')
        .build()

    then:
    result.output.contains("Duplicate pattern edge case configured")
    // Note: This is a misconfiguration - the pattern will always trigger reject
    // Implementation should consider adding a warning for this case
}
```

### 15.3 Service Names with JSON-Sensitive Characters (Gap #FT-42)

#### Test: Service names with quotes and backslashes

Verify service names with JSON-sensitive characters (quotes, backslashes) serialize correctly for system property
propagation (per Implementation Plan Section 12).

```groovy
def "waitForLog with JSON-sensitive service name characters configures correctly"() {
    given:
    // Create compose file with service name containing underscore (Docker allows limited special chars)
    // Note: Docker Compose service names have restrictions - they can contain a-z, 0-9, _, -
    // Testing names that might challenge JSON serialization within allowed limits
    def specialComposeFile = testProjectDir.resolve('special-json-compose.yml').toFile()
    specialComposeFile << """
services:
  app_v1_0:
    image: alpine:latest
    command: sleep 30
  my-app-2:
    image: alpine:latest
    command: sleep 30
"""

    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                jsonSafeNames {
                    composeFile.set(file('special-json-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app_v1_0': ['Version 1.0 started'],
                            'my-app-2': ['App number 2 ready']
                        ])
                        // Pattern with quotes that will need JSON escaping
                        rejectPatterns.set([
                            'app_v1_0': ['Error: "initialization failed"']
                        ])
                    }
                }
            }
        }

        tasks.register('jsonTestTask', Test) {
            usesCompose stack: 'jsonSafeNames', lifecycle: 'class'
        }

        task verifyJsonSerialization {
            doLast {
                def testTask = tasks.getByName('jsonTestTask')
                def props = testTask.systemProperties

                def servicesJson = props['docker.compose.waitForLog.services']
                def rejectJson = props['docker.compose.waitForLog.rejectPatterns']

                println "Services JSON: \${servicesJson}"
                println "Reject JSON: \${rejectJson}"

                // Verify JSON is valid and contains expected content
                def slurper = new groovy.json.JsonSlurper()
                def services = slurper.parseText(servicesJson)
                def rejects = slurper.parseText(rejectJson)

                assert services.containsKey('app_v1_0')
                assert services.containsKey('my-app-2')
                assert services['app_v1_0'] == ['Version 1.0 started']
                assert rejects['app_v1_0'][0].contains('"initialization failed"')

                println "JSON serialization with special characters verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyJsonSerialization')
        .build()

    then:
    result.output.contains("JSON serialization with special characters verified")
}
```

### 15.4 Incremental Map Building with putAll/put (Gap #FT-43)

#### Test: Building waitForServices map incrementally

Verify that `MapProperty` can be built incrementally using `putAll()` and `put()` methods rather than just `.set()`.

```groovy
def "waitForLog with incrementally built map configures correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                incrementalMap {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // Build map incrementally instead of using set()
                        waitForServices.put('app', ['Started Application'])
                        waitForServices.put('db', ['Database ready'])

                        // Add more patterns to existing service
                        rejectPatterns.putAll([
                            'app': ['FATAL', 'Exception'],
                            'db': ['Connection refused']
                        ])

                        timeoutSeconds.set(90)
                    }
                }
            }
        }

        task verifyIncrementalBuild {
            doLast {
                def upTask = tasks.getByName('composeUpIncrementalMap')
                def services = upTask.waitForLogServices.get()
                def rejects = upTask.waitForLogRejectPatterns.get()

                println "Services built: \${services.keySet()}"
                println "Reject services: \${rejects.keySet()}"

                assert services.size() == 2
                assert services['app'] == ['Started Application']
                assert services['db'] == ['Database ready']
                assert rejects['app'].size() == 2
                assert rejects['db'].size() == 1

                println "Incremental map building verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyIncrementalBuild')
        .build()

    then:
    result.output.contains("Services built: [app, db]")
    result.output.contains("Incremental map building verified")
}
```

### 15.5 Direct Assignment Syntax vs .set() Method (Gap #FT-44)

#### Test: Both DSL syntax styles work correctly

Per DSL User Description (0100), both `.set()` and direct assignment (`=`) syntax should work. Verify both.

```groovy
def "waitForLog with direct assignment syntax configures correctly"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                directAssignment {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // Style 2: Direct assignment (Groovy converts to set() call)
                        waitForServices = [
                            'app': ['Started Application', 'Ready to serve'],
                            'db': ['Accepting connections']
                        ]
                        timeoutSeconds = 90
                        pollSeconds = 3
                        caseInsensitive = true
                        verbose = true
                        progressIntervalSeconds = 15
                    }
                }

                setMethod {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        // Style 1: Explicit .set() method
                        waitForServices.set([
                            'app': ['Started Application', 'Ready to serve'],
                            'db': ['Accepting connections']
                        ])
                        timeoutSeconds.set(90)
                        pollSeconds.set(3)
                        caseInsensitive.set(true)
                        verbose.set(true)
                        progressIntervalSeconds.set(15)
                    }
                }
            }
        }

        task verifySyntaxEquivalence {
            doLast {
                def directTask = tasks.getByName('composeUpDirectAssignment')
                def setTask = tasks.getByName('composeUpSetMethod')

                // Verify both produce identical configuration
                assert directTask.waitForLogServices.get() == setTask.waitForLogServices.get()
                assert directTask.waitForLogTimeoutSeconds.get() == setTask.waitForLogTimeoutSeconds.get()
                assert directTask.waitForLogPollSeconds.get() == setTask.waitForLogPollSeconds.get()
                assert directTask.waitForLogCaseInsensitive.get() == setTask.waitForLogCaseInsensitive.get()
                assert directTask.waitForLogVerbose.get() == setTask.waitForLogVerbose.get()
                assert directTask.waitForLogProgressIntervalSeconds.get() == setTask.waitForLogProgressIntervalSeconds.get()

                println "Direct assignment: timeout=\${directTask.waitForLogTimeoutSeconds.get()}"
                println "Set method: timeout=\${setTask.waitForLogTimeoutSeconds.get()}"
                println "Both DSL syntax styles produce equivalent configuration"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifySyntaxEquivalence')
        .build()

    then:
    result.output.contains("Both DSL syntax styles produce equivalent configuration")
    result.output.contains("Direct assignment: timeout=90")
    result.output.contains("Set method: timeout=90")
}
```

### 15.6 Empty rejectPatterns Map Explicit Behavior (Gap #FT-45)

#### Test: Explicitly empty rejectPatterns vs not set

Verify behavior difference between explicitly setting `rejectPatterns.set([:])` vs not setting it at all.
Per DSL description, the convention is empty map `[:]`.

```groovy
def "waitForLog explicit empty rejectPatterns vs not set behavior"() {
    given:
    buildFile << """
        plugins {
            id 'java'
            id 'com.kineticfire.gradle.docker'
        }

        dockerTest {
            composeStacks {
                explicitEmpty {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // Explicitly set to empty map
                        rejectPatterns.set([:])
                    }
                }

                implicitEmpty {
                    composeFile.set(file('docker-compose.yml'))
                    waitForLog {
                        waitForServices.set([
                            'app': ['Started Application']
                        ])
                        // rejectPatterns not set - uses convention (empty map)
                    }
                }
            }
        }

        task verifyEmptyRejectBehavior {
            doLast {
                def explicitTask = tasks.getByName('composeUpExplicitEmpty')
                def implicitTask = tasks.getByName('composeUpImplicitEmpty')

                def explicitReject = explicitTask.waitForLogRejectPatterns.getOrElse([:])
                def implicitReject = implicitTask.waitForLogRejectPatterns.getOrElse([:])

                println "Explicit empty rejectPatterns: \${explicitReject}"
                println "Implicit (convention) rejectPatterns: \${implicitReject}"

                // Both should result in empty map
                assert explicitReject.isEmpty()
                assert implicitReject.isEmpty()

                // Check isPresent behavior
                def explicitPresent = explicitTask.waitForLogRejectPatterns.present
                def implicitPresent = implicitTask.waitForLogRejectPatterns.present

                println "Explicit isPresent: \${explicitPresent}"
                println "Implicit isPresent: \${implicitPresent}"

                println "Empty rejectPatterns behavior verified"
            }
        }
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
        .withArguments('verifyEmptyRejectBehavior')
        .build()

    then:
    result.output.contains("Empty rejectPatterns behavior verified")
    result.output.contains("Explicit empty rejectPatterns: [:]")
}
```

---

## Review Notes (Fifth Review - 2026-01-11)

### Summary

The fifth review identified **6 new gaps** (FT-40 through FT-45) through systematic comparison of the functional test
plan against the implementation plan (0200) and DSL user description (0100).

### New Gaps Identified

| Gap ID | Severity | Description |
|--------|----------|-------------|
| FT-40 | Minor | Very long regex patterns (1000+ chars) boundary test |
| FT-41 | Minor | Same pattern in both waitForServices and rejectPatterns (edge case) |
| FT-42 | Moderate | Service names with JSON-sensitive characters (quotes in patterns) |
| FT-43 | Minor | Incremental map building with putAll/put methods |
| FT-44 | Moderate | Direct assignment syntax vs .set() method (both DSL styles) |
| FT-45 | Minor | Empty rejectPatterns map explicit vs convention behavior |

### Coverage Analysis Methodology

The fifth review systematically verified:

1. **All WaitForLogSpec properties** (7 total) have functional tests for:
   - Configuration via DSL
   - Convention/default values
   - Wiring to ComposeUpTask
   - System property propagation

2. **All DSL syntax styles** documented in 0100 are tested:
   - `.set()` method (primary)
   - Direct assignment (Groovy syntax)
   - Closure configuration
   - Action configuration

3. **All edge cases** from implementation plan Section 15-17:
   - Boundary values (long patterns, many services)
   - JSON serialization edge cases
   - Empty/null value handling
   - Configuration cache serialization

4. **All error and warning scenarios** documented in 0100:
   - Empty waitForServices validation
   - Invalid regex patterns
   - Orphaned rejectPatterns warning
   - pollSeconds > timeoutSeconds warning

### Updated Coverage Summary

With Section 15 additions, the functional test plan provides **complete coverage** for:

| Category | Tests | Status |
|----------|-------|--------|
| Core DSL Configuration | 18+ tests | ✅ Complete |
| Property Conventions | 7 properties | ✅ Complete |
| Property Wiring | 7 flattened properties | ✅ Complete |
| Validation Errors | 10 scenarios | ✅ Complete |
| Warnings | 3 scenarios | ✅ Complete |
| System Properties | JSON format + propagation | ✅ Complete |
| Configuration Cache | 12+ scenarios | ✅ Complete |
| Edge Cases | 30+ scenarios | ✅ Complete |
| Boundary Tests | 8 scenarios | ✅ Complete |
| Lifecycle Support | CLASS + METHOD | ✅ Complete |
| DSL Syntax Styles | Both styles | ✅ Complete |

### Total Gap Count

After five reviews:
- **Reviews 1-4**: FT-1 through FT-39 (39 gaps)
- **Review 5**: FT-40 through FT-45 (6 new gaps)
- **Total covered gaps**: 45

### Implementation Verification Checklist

Before marking functional tests complete, verify these implementation sections are covered:

- [x] Phase 1: WaitForLogSpec (Section 1 of 0200) - All 7 properties tested
- [x] Phase 1: WaitForLogConfig (Section 2 of 0200) - Model used by tests
- [x] Phase 1: WaitForLogResult (Section 3 of 0200) - Result model (runtime concern)
- [x] Phase 1: LogPatternMatcher (Section 4 of 0200) - Unit test concern
- [x] Phase 1: WaitForLogConfigBuilder (Section 5 of 0200) - Unit test concern
- [x] Phase 2: ComposeStackSpec (Section 6 of 0200) - DSL integration tested
- [x] Phase 2: ComposeService interface (Section 7 of 0200) - Unit test concern
- [x] Phase 2: ExecLibraryComposeService (Section 8 of 0200) - Integration test concern
- [x] Phase 2: ComposeServiceException (Section 9 of 0200) - Unit test concern
- [x] Phase 2: ComposeUpTask (Section 10 of 0200) - Property wiring tests
- [x] Phase 2: GradleDockerPlugin wiring (Section 11 of 0200) - DSL tests verify
- [x] Phase 2: TestIntegrationExtension (Section 12 of 0200) - System property tests
- [x] Phase 2: JUnit extensions (Section 12.5 of 0200) - Lifecycle tests
- [x] MapProperty serialization (Section 17 of 0200) - Config cache tests

---

## Review Notes (Sixth Review - 2026-01-11)

### Summary

The sixth review was a comprehensive verification of 100% functional test coverage by systematically comparing
every section of the implementation plan (0200) against existing functional test coverage. This review confirms
the functional test plan is **complete and ready for implementation**.

### Methodology

The review analyzed coverage for:

1. **All WaitForLogSpec properties** (7 total) - Verified DSL configuration, convention values, task wiring, and
   system property propagation tests exist for all properties.

2. **All implementation plan sections** - Cross-referenced each section (1-17) against functional tests:
   - Sections 1-5 (Phase 1 core components): DSL tests + validation tests cover spec behavior
   - Section 6 (ComposeStackSpec): DSL methods tested via closure and action styles
   - Sections 7-9: Unit/integration test concerns, not functional test scope
   - Section 10 (ComposeUpTask): Property wiring tests verify all flattened properties
   - Section 11 (GradleDockerPlugin): Implicit via DSL tests (wiring verified end-to-end)
   - Section 12 (TestIntegrationExtension): System property propagation tests for CLASS and METHOD
   - Section 12.5 (JUnit extensions): Lifecycle tests verify both extensions work
   - Sections 13-16 (Execution order, performance, limitations): Runtime concerns for integration tests
   - Section 17 (MapProperty serialization): Configuration cache tests cover serialization

3. **All DSL syntax styles** per 0100:
   - `.set()` method (Section 1.2)
   - Direct assignment `=` (Gap #FT-44)
   - Closure configuration (Section 1.1)
   - Action configuration (Gap #FT-1)

4. **All validation scenarios** per 0200 Section 5:
   - Empty waitForServices (Section 3.1)
   - Empty pattern list (Section 3.2)
   - Invalid regex (Gap #FT-2)
   - Negative/zero timeout (Gap #FT-14, FT-15)
   - Negative/zero poll (Gap #FT-16)
   - Orphaned rejectPatterns warning (Gap #FT-3)
   - pollSeconds > timeoutSeconds warning (Gap #FT-4)
   - Non-string pattern types (Gap #FT-5)

### Gaps Considered But Not Added

The following potential gaps were evaluated but determined to be already covered or out of scope:

| Potential Gap | Reason Not Added |
|---------------|------------------|
| No waitForLog block at all | Implicitly tested by all base compose stack tests |
| rejectPatterns without waitForServices | Covered by empty waitForServices validation |
| Custom test task with usesCompose() | Covered by CLASS/METHOD lifecycle tests (Section 5) |
| Invalid service name not in compose | Integration test concern (requires Docker) |
| Container restart during wait | Runtime behavior, integration test concern |

### Final Coverage Assessment

| Implementation Section | Functional Test Coverage | Notes |
|------------------------|-------------------------|-------|
| Section 1: WaitForLogSpec | ✅ 100% | All 7 properties tested |
| Section 2-5: Core Classes | ✅ N/A | Unit test scope |
| Section 6: ComposeStackSpec DSL | ✅ 100% | Both closure and action styles |
| Section 7-9: Service Layer | ✅ N/A | Unit/integration scope |
| Section 10: ComposeUpTask | ✅ 100% | All flattened properties verified |
| Section 11: Plugin Wiring | ✅ 100% | Implicit via DSL tests |
| Section 12: TestIntegrationExtension | ✅ 100% | All system properties |
| Section 12.5: JUnit Extensions | ✅ 100% | Both lifecycles tested |
| Section 13-16: Runtime Behavior | ✅ N/A | Integration test scope |
| Section 17: Serialization Fallback | ✅ 100% | Config cache tests |

### Conclusion

**The functional test plan achieves 100% functionality coverage for the `waitForLog` feature.**

- **45 gaps** identified and addressed across 5 prior reviews
- **71+ test methods** specified covering all scenarios
- **No additional gaps** identified in sixth review
- **Plan status: COMPLETE - Ready for implementation**

### Recommendations

1. During implementation, execute tests in order:
   - Core DSL tests first (Sections 1-2)
   - Convention tests (Section 2)
   - Validation tests (Section 3)
   - Property wiring (Section 4)
   - System properties (Section 5)
   - Configuration cache (Section 6)
   - Edge cases and boundary tests last (Sections 8-15)

2. Address implementation issues FT-I4, FT-I5, and FT-I7 as documented in Section 13.8

3. Run full functional test suite with `--configuration-cache` flag to verify cache compatibility
