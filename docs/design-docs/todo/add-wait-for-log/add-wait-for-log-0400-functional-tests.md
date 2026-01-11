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

## Checklist

### DSL Configuration Tests
- [ ] Test `waitForLog` block with single service and single pattern
- [ ] Test `waitForLog` block with single service and multiple patterns
- [ ] Test `waitForLog` block with multiple services
- [ ] Test `waitForLog` with `.set()` syntax
- [ ] Test `waitForLog` with direct assignment syntax
- [ ] Test `waitForLog` with Action configuration style
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
- [ ] Test invalid regex pattern error message (at execution time)
- [ ] Test orphaned `rejectPatterns` warning (service in reject but not in waitFor)
- [ ] Test `pollSeconds > timeoutSeconds` warning

### Property Wiring Tests
- [ ] Test `waitForLog` properties are wired to `ComposeUpTask`
- [ ] Test property values are correctly propagated
- [ ] Test all flattened properties are present on task

### System Property Tests (TestIntegrationExtension)
- [ ] Test `waitForLog` system properties are set for CLASS lifecycle
- [ ] Test `waitForLog` system properties are set for METHOD lifecycle
- [ ] Test JSON serialization of `waitForServices` map
- [ ] Test JSON serialization of `rejectPatterns` map
- [ ] Test scalar property serialization

### Configuration Cache Tests (Phase 4)
- [ ] Test `MapProperty<String, List<String>>` serialization
- [ ] Test configuration cache store on first run
- [ ] Test configuration cache reuse on second run
- [ ] Test various pattern content types (simple, regex, special chars)
- [ ] Test multi-service configuration cache
- [ ] Test edge cases (empty reject patterns, long strings, Unicode)

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
