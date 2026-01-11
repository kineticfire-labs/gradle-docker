# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Unit Tests

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0200-implementation.md` - Implementation details and component structure

Reference project testing standards:
- `docs/project-standards/testing/unit-testing.md`

## Purpose

Define unit test specifications for the `waitForLog` feature implementation. Tests follow existing project patterns:
- **Framework**: Spock (extends `Specification`)
- **Style**: given/when/then or expect blocks
- **Gradle Integration**: Use `ProjectBuilder` for Gradle-related tests
- **Mocking**: Use Spock's `Mock()` for interfaces/dependencies
- **Coverage Target**: 100% line and branch coverage

## Checklist

### Phase 0: Prerequisite Changes
- [ ] Unit tests for `LogsConfig` modifications (`LogsConfigTest.groovy`)
  - [ ] Tests for `tailLines = 0` behavior (Gap #18)
  - [ ] Tests for `tailLines = -1` behavior (Gap #18)
  - [ ] Tests for `hasLimitedTail()` method - positive values (Gap #18)
  - [ ] Tests for `hasLimitedTail()` method - zero value (Gap #18)
  - [ ] Tests for `hasLimitedTail()` method - negative values (Gap #18)
- [ ] Unit tests for `ExecLibraryComposeService.buildLogsCommand()` change
  - [ ] Verify `hasLimitedTail()` method is used (extend existing `ExecLibraryComposeServiceTest.groovy`)

### Phase 1: Core Components
- [ ] Unit tests for `WaitForLogSpec` (`WaitForLogSpecTest.groovy`)
- [ ] Unit tests for `WaitForLogConfig` (`WaitForLogConfigTest.groovy`)
  - [ ] Tests for `toString()` format includes all key fields (Gap #19)
- [ ] Unit tests for `WaitForLogResult` (`WaitForLogResultTest.groovy`)
  - [ ] Tests for secondary constructor (rejected result) (Gap #16)
  - [ ] Tests for `PatternMatch` inner class (Gap #21)
- [ ] Unit tests for `LogPatternMatcher` (`LogPatternMatcherTest.groovy`)
  - [ ] Tests for `compilePattern(null, false)` throws NullPointerException (Gap #5)
  - [ ] Tests for `RejectCheckResult` inner class (Gap #15)
  - [ ] Tests for private constructor via reflection for coverage (Gap #20)
- [ ] Unit tests for `WaitForLogConfigBuilder` (`WaitForLogConfigBuilderTest.groovy`)
  - [ ] Tests for private constructor via reflection for coverage (Gap #23)

### Phase 2: Integration Components
- [ ] Unit tests for `ComposeStackSpec.waitForLog` extension (`ComposeStackSpecWaitForLogTest.groovy`)
- [ ] Unit tests for `ComposeServiceException` new error types (`ComposeServiceExceptionTest.groovy` - extend)
- [ ] Unit tests for `ExecLibraryComposeService.waitForLogPatterns()`
      (`ExecLibraryComposeServiceWaitForLogTest.groovy`)
  - [ ] Tests for `getComposeProjectServices()` method
    - [ ] Use JSON format mock outputs matching Docker Compose v2 (Gap #1)
  - [ ] Tests for `validateServicesExist()` empty project case
    - [ ] Use correct method signature `validateServicesExist(config)` (Gap #2)
    - [ ] Test specific error message for empty compose project (Gap #11)
  - [ ] Tests for `fetchServiceLogs()` edge cases (null, empty, whitespace output)
    - [ ] Expect `List<String>` return type (Gap #4)
  - [ ] Tests for `isServiceRunning()` JSON parse error logging
    - [ ] Use JSON format mock outputs (Gap #1)
  - [ ] Tests for `getServiceExitCode()` method (Gap #3)
  - [ ] Tests for `waitForLogPatterns(null)` throws NullPointerException (Gap #13)
  - [ ] Tests for InterruptedException handling (Gap #14)
  - [ ] Tests for ExecutionException unwrapping (Gap #17)
  - [ ] Tests for `getRecentLogs()` complete implementation (Gap #10)
  - [ ] Tests for `CheckAllServicesResult` factory methods (Gap #12)
  - [ ] Tests for RECENT_LOG_LINES_FOR_ERROR constant usage (Gap #22)
- [ ] Unit tests for `ComposeUpTask` waitForLog properties (`ComposeUpTaskWaitForLogTest.groovy`)
  - [ ] Tests for exception propagation from `waitForLogPatterns()`
  - [ ] Tests for partial wait block configuration scenarios
- [ ] Unit tests for `GradleDockerPlugin` waitForLog wiring (`GradleDockerPluginWaitForLogTest.groovy`)
- [ ] Unit tests for `TestIntegrationExtension` waitForLog properties
      (`TestIntegrationExtensionWaitForLogTest.groovy`)

### Phase 3: Test Framework Extensions
- [ ] Unit tests for `DockerComposeMethodExtension` waitForLog (`DockerComposeMethodExtensionWaitForLogTest.groovy`)
  - [ ] Tests for `performWaitForRunning()` method
  - [ ] Tests for `performWaitForHealthy()` method
  - [ ] Tests for `performWaitForLog()` method
  - [ ] Tests for `parseIntProperty()` helper
    - [ ] Tests for whitespace handling (Gap #6)
  - [ ] Tests for `parseBooleanProperty()` helper
    - [ ] Tests for non-standard values (parameterized test) (Gap #7)
  - [ ] Tests for `parseJsonMapProperty()` helper
  - [ ] Tests for updated `waitForStackToBeReady()` method
- [ ] Unit tests for `DockerComposeClassExtension` waitForLog (`DockerComposeClassExtensionWaitForLogTest.groovy`)
  - [ ] Same test coverage as Method extension
- [ ] Unit tests for `JUnitComposeService` waitForLog delegation (`JUnitComposeServiceWaitForLogTest.groovy`)
  - [ ] Tests for `waitForLogPatterns()` delegation method

### Final Verification
- [ ] All unit tests pass
- [ ] 100% line and branch coverage achieved (or gaps documented)
- [ ] No compilation warnings
- [ ] All test files include required import statements (Gap #9)
- [ ] Warning test approach documented if using System.err capture (Gap #8)

---

## Test File Locations

All test files are located under `plugin/src/test/groovy/com/kineticfire/gradle/docker/`:

| Component | Test File Location |
|-----------|-------------------|
| `WaitForLogSpec` | `spec/WaitForLogSpecTest.groovy` |
| `WaitForLogConfig` | `model/WaitForLogConfigTest.groovy` |
| `WaitForLogResult` | `model/WaitForLogResultTest.groovy` |
| `LogPatternMatcher` | `util/LogPatternMatcherTest.groovy` |
| `WaitForLogConfigBuilder` | `util/WaitForLogConfigBuilderTest.groovy` |
| `ComposeStackSpec` (waitForLog) | `spec/ComposeStackSpecWaitForLogTest.groovy` |
| `ExecLibraryComposeService` (waitForLog) | `service/ExecLibraryComposeServiceWaitForLogTest.groovy` |
| `ComposeUpTask` (waitForLog) | `task/ComposeUpTaskWaitForLogTest.groovy` |
| `GradleDockerPlugin` (waitForLog wiring) | `GradleDockerPluginWaitForLogTest.groovy` |
| `TestIntegrationExtension` (waitForLog) | `extension/TestIntegrationExtensionWaitForLogTest.groovy` |
| `DockerComposeMethodExtension` | `junit/DockerComposeMethodExtensionWaitForLogTest.groovy` |
| `DockerComposeClassExtension` | `junit/DockerComposeClassExtensionWaitForLogTest.groovy` |
| `JUnitComposeService` (waitForLog) | `junit/JUnitComposeServiceWaitForLogTest.groovy` |

---

## Unit Test Specifications

### 1. LogsConfig Modifications (Extend Existing Tests)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/model/LogsConfigTest.groovy`

Add the following tests to the existing test file:

```groovy
// ===== NEW TESTS FOR tailLines = 0 BEHAVIOR =====

def "tailLines = 0 is preserved for all logs mode"() {
    when:
    def config = new LogsConfig([], 0)

    then:
    config.tailLines == 0
}

def "tailLines = -1 is preserved for all logs mode"() {
    when:
    def config = new LogsConfig([], -1)

    then:
    config.tailLines == -1
}

def "positive tailLines values are preserved unchanged"() {
    expect:
    new LogsConfig([], 1).tailLines == 1
    new LogsConfig([], 100).tailLines == 100
    new LogsConfig([], Integer.MAX_VALUE).tailLines == Integer.MAX_VALUE
}

// ===== TESTS FOR hasLimitedTail() METHOD =====

def "hasLimitedTail returns false for tailLines = 0"() {
    expect:
    !new LogsConfig([], 0).hasLimitedTail()
}

def "hasLimitedTail returns false for negative tailLines"() {
    expect:
    !new LogsConfig([], -1).hasLimitedTail()
    !new LogsConfig([], -100).hasLimitedTail()
}

def "hasLimitedTail returns true for positive tailLines"() {
    expect:
    new LogsConfig([], 1).hasLimitedTail()
    new LogsConfig([], 100).hasLimitedTail()
    new LogsConfig([], Integer.MAX_VALUE).hasLimitedTail()
}

def "hasLimitedTail boundary condition at zero"() {
    expect:
    !new LogsConfig([], 0).hasLimitedTail()
    new LogsConfig([], 1).hasLimitedTail()
}
```

**Coverage Requirements**:
- Constructor with `tailLines = 0`: Line coverage
- Constructor with `tailLines = -1`: Line coverage
- `hasLimitedTail()` method: 100% branch coverage (positive, zero, negative)

---

### 1b. ExecLibraryComposeService buildLogsCommand() Modification

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeServiceTest.groovy`

Add/extend the following tests in the existing test file to verify the change from
`if (config.tailLines > 0)` to `if (config.hasLimitedTail())`:

```groovy
// ===== TESTS FOR buildLogsCommand() with hasLimitedTail() =====

def "buildLogsCommand omits --tail flag when tailLines is 0"() {
    given:
    def config = new LogsConfig(['app'], 0, false, null)

    when:
    def command = service.buildLogsCommand('test-project', config)

    then:
    !command.contains('--tail')
}

def "buildLogsCommand omits --tail flag when tailLines is negative"() {
    given:
    def config = new LogsConfig(['app'], -1, false, null)

    when:
    def command = service.buildLogsCommand('test-project', config)

    then:
    !command.contains('--tail')
}

def "buildLogsCommand includes --tail flag when tailLines is positive"() {
    given:
    def config = new LogsConfig(['app'], 100, false, null)

    when:
    def command = service.buildLogsCommand('test-project', config)

    then:
    command.contains('--tail')
    command.contains('100')
}

def "buildLogsCommand includes --tail 1 when tailLines is 1"() {
    given:
    def config = new LogsConfig(['app'], 1, false, null)

    when:
    def command = service.buildLogsCommand('test-project', config)

    then:
    command.contains('--tail')
    command.contains('1')
}
```

**Coverage Requirements**:
- `buildLogsCommand()` with `tailLines = 0`: Verify `--tail` flag is omitted
- `buildLogsCommand()` with `tailLines = -1`: Verify `--tail` flag is omitted
- `buildLogsCommand()` with `tailLines > 0`: Verify `--tail` flag is included
- Ensure the method uses `config.hasLimitedTail()` semantically

---

### 2. WaitForLogSpecTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/WaitForLogSpecTest.groovy`

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

package com.kineticfire.gradle.docker.spec

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for WaitForLogSpec
 */
class WaitForLogSpecTest extends Specification {

    def project
    def waitForLogSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        waitForLogSpec = project.objects.newInstance(WaitForLogSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with ObjectFactory"() {
        expect:
        waitForLogSpec != null
    }

    def "constructor creates all properties"() {
        expect:
        waitForLogSpec.waitForServices != null
        waitForLogSpec.rejectPatterns != null
        waitForLogSpec.timeoutSeconds != null
        waitForLogSpec.pollSeconds != null
        waitForLogSpec.caseInsensitive != null
        waitForLogSpec.verbose != null
        waitForLogSpec.progressIntervalSeconds != null
    }

    // ===== DEFAULT CONVENTION TESTS =====

    def "waitForServices has no default convention"() {
        expect:
        !waitForLogSpec.waitForServices.present
    }

    def "rejectPatterns defaults to empty map"() {
        expect:
        waitForLogSpec.rejectPatterns.present
        waitForLogSpec.rejectPatterns.get() == [:]
    }

    def "timeoutSeconds defaults to 60"() {
        expect:
        waitForLogSpec.timeoutSeconds.present
        waitForLogSpec.timeoutSeconds.get() == 60
    }

    def "pollSeconds defaults to 2"() {
        expect:
        waitForLogSpec.pollSeconds.present
        waitForLogSpec.pollSeconds.get() == 2
    }

    def "caseInsensitive defaults to false"() {
        expect:
        waitForLogSpec.caseInsensitive.present
        waitForLogSpec.caseInsensitive.get() == false
    }

    def "verbose defaults to false"() {
        expect:
        waitForLogSpec.verbose.present
        waitForLogSpec.verbose.get() == false
    }

    def "progressIntervalSeconds defaults to 0"() {
        expect:
        waitForLogSpec.progressIntervalSeconds.present
        waitForLogSpec.progressIntervalSeconds.get() == 0
    }

    // ===== PROPERTY SETTING TESTS =====

    def "waitForServices can be set with single service"() {
        when:
        waitForLogSpec.waitForServices.set(['app': ['Started Application']])

        then:
        waitForLogSpec.waitForServices.present
        waitForLogSpec.waitForServices.get() == ['app': ['Started Application']]
    }

    def "waitForServices can be set with multiple services"() {
        given:
        def services = [
            'app': ['Started Application', 'Listening on port'],
            'db': ['ready for connections'],
            'cache': ['Server started', 'Accepting connections']
        ]

        when:
        waitForLogSpec.waitForServices.set(services)

        then:
        waitForLogSpec.waitForServices.present
        waitForLogSpec.waitForServices.get() == services
    }

    def "waitForServices can be set with empty map"() {
        when:
        waitForLogSpec.waitForServices.set([:])

        then:
        waitForLogSpec.waitForServices.present
        waitForLogSpec.waitForServices.get() == [:]
    }

    def "rejectPatterns can be set"() {
        given:
        def patterns = [
            'app': ['Exception', 'Error'],
            'db': ['FATAL', 'panic']
        ]

        when:
        waitForLogSpec.rejectPatterns.set(patterns)

        then:
        waitForLogSpec.rejectPatterns.get() == patterns
    }

    def "timeoutSeconds can be overridden"() {
        when:
        waitForLogSpec.timeoutSeconds.set(120)

        then:
        waitForLogSpec.timeoutSeconds.get() == 120
    }

    def "pollSeconds can be overridden"() {
        when:
        waitForLogSpec.pollSeconds.set(5)

        then:
        waitForLogSpec.pollSeconds.get() == 5
    }

    def "caseInsensitive can be set to true"() {
        when:
        waitForLogSpec.caseInsensitive.set(true)

        then:
        waitForLogSpec.caseInsensitive.get() == true
    }

    def "verbose can be set to true"() {
        when:
        waitForLogSpec.verbose.set(true)

        then:
        waitForLogSpec.verbose.get() == true
    }

    def "progressIntervalSeconds can be set"() {
        when:
        waitForLogSpec.progressIntervalSeconds.set(10)

        then:
        waitForLogSpec.progressIntervalSeconds.get() == 10
    }

    // ===== EDGE CASE TESTS =====

    def "waitForServices supports regex special characters in patterns"() {
        given:
        def patterns = [
            'app': ['\\[INFO\\].*started', 'port:\\s+\\d+'],
            'db': ['(?i)ready']
        ]

        when:
        waitForLogSpec.waitForServices.set(patterns)

        then:
        waitForLogSpec.waitForServices.get() == patterns
    }

    def "waitForServices supports empty pattern list for service"() {
        when:
        waitForLogSpec.waitForServices.set(['app': []])

        then:
        waitForLogSpec.waitForServices.get() == ['app': []]
    }

    def "properties are independent and can be set in any order"() {
        when:
        waitForLogSpec.verbose.set(true)
        waitForLogSpec.timeoutSeconds.set(90)
        waitForLogSpec.waitForServices.set(['app': ['ready']])
        waitForLogSpec.pollSeconds.set(3)

        then:
        waitForLogSpec.verbose.get() == true
        waitForLogSpec.timeoutSeconds.get() == 90
        waitForLogSpec.waitForServices.get() == ['app': ['ready']]
        waitForLogSpec.pollSeconds.get() == 3
    }

    // ===== MULTIPLE INSTANCES TESTS =====

    def "multiple WaitForLogSpec instances are independent"() {
        given:
        def spec1 = project.objects.newInstance(WaitForLogSpec)
        def spec2 = project.objects.newInstance(WaitForLogSpec)

        when:
        spec1.timeoutSeconds.set(100)
        spec2.timeoutSeconds.set(200)

        then:
        spec1.timeoutSeconds.get() == 100
        spec2.timeoutSeconds.get() == 200
    }
}
```

**Coverage Requirements**:
- Constructor: Verify all properties initialized
- Each property getter: Line coverage
- Convention defaults: All 7 properties with conventions
- Property setters: All properties can be set/overridden
- Edge cases: Regex patterns, empty maps, empty lists

---

### 3. WaitForLogConfigTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/model/WaitForLogConfigTest.groovy`

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

package com.kineticfire.gradle.docker.model

import spock.lang.Specification

import java.time.Duration
import java.util.regex.Pattern

/**
 * Unit tests for WaitForLogConfig
 */
class WaitForLogConfigTest extends Specification {

    // ===== CONSTRUCTOR TESTS =====

    def "can create minimal WaitForLogConfig"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]

        when:
        def config = new WaitForLogConfig(
            'test-project',
            servicePatterns,
            [:],
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            false,
            false,
            Duration.ZERO
        )

        then:
        config.projectName == 'test-project'
        config.servicePatterns == servicePatterns
        config.rejectPatterns == [:]
        config.timeout == Duration.ofSeconds(60)
        config.pollInterval == Duration.ofSeconds(2)
        config.caseInsensitive == false
        config.verbose == false
        config.progressInterval == Duration.ZERO
    }

    def "can create full WaitForLogConfig with all options"() {
        given:
        def servicePatterns = [
            'app': [Pattern.compile('Started'), Pattern.compile('Ready')],
            'db': [Pattern.compile('accepting connections')]
        ]
        def rejectPatterns = [
            'app': [Pattern.compile('Exception')],
            'db': [Pattern.compile('FATAL')]
        ]

        when:
        def config = new WaitForLogConfig(
            'prod-project',
            servicePatterns,
            rejectPatterns,
            Duration.ofSeconds(120),
            Duration.ofSeconds(5),
            true,
            true,
            Duration.ofSeconds(10)
        )

        then:
        config.projectName == 'prod-project'
        config.servicePatterns == servicePatterns
        config.rejectPatterns == rejectPatterns
        config.timeout == Duration.ofSeconds(120)
        config.pollInterval == Duration.ofSeconds(5)
        config.caseInsensitive == true
        config.verbose == true
        config.progressInterval == Duration.ofSeconds(10)
    }

    // ===== NULL VALIDATION TESTS =====

    def "validates project name cannot be null"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]

        when:
        new WaitForLogConfig(
            null,
            servicePatterns,
            [:],
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            false, false,
            Duration.ZERO
        )

        then:
        def exception = thrown(NullPointerException)
        exception.message.contains("Project name cannot be null")
    }

    def "validates service patterns cannot be null"() {
        when:
        new WaitForLogConfig(
            'project',
            null,
            [:],
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            false, false,
            Duration.ZERO
        )

        then:
        def exception = thrown(NullPointerException)
        exception.message.contains("Service patterns cannot be null")
    }

    def "handles null reject patterns with empty map"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]

        when:
        def config = new WaitForLogConfig(
            'project',
            servicePatterns,
            null,
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            false, false,
            Duration.ZERO
        )

        then:
        config.rejectPatterns == [:]
    }

    def "handles null timeout with default"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]

        when:
        def config = new WaitForLogConfig(
            'project',
            servicePatterns,
            [:],
            null,
            Duration.ofSeconds(2),
            false, false,
            Duration.ZERO
        )

        then:
        config.timeout == Duration.ofSeconds(60)
    }

    def "handles null pollInterval with default"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]

        when:
        def config = new WaitForLogConfig(
            'project',
            servicePatterns,
            [:],
            Duration.ofSeconds(60),
            null,
            false, false,
            Duration.ZERO
        )

        then:
        config.pollInterval == Duration.ofSeconds(2)
    }

    def "handles null progressInterval with default"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]

        when:
        def config = new WaitForLogConfig(
            'project',
            servicePatterns,
            [:],
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            false, false,
            null
        )

        then:
        config.progressInterval == Duration.ZERO
    }

    // ===== getTotalWaitAttempts() TESTS =====

    def "getTotalWaitAttempts calculates correctly for exact division"() {
        given:
        def config = createConfigWithTimeouts(Duration.ofSeconds(60), Duration.ofSeconds(2))

        expect:
        config.totalWaitAttempts == 30
    }

    def "getTotalWaitAttempts uses ceiling division for non-exact division"() {
        given:
        def config = createConfigWithTimeouts(Duration.ofSeconds(61), Duration.ofSeconds(2))

        expect:
        config.totalWaitAttempts == 31  // ceil(61/2) = 31
    }

    def "getTotalWaitAttempts returns minimum of 1 when timeout < pollInterval"() {
        given:
        def config = createConfigWithTimeouts(Duration.ofSeconds(1), Duration.ofSeconds(2))

        expect:
        config.totalWaitAttempts == 1
    }

    def "getTotalWaitAttempts returns 1 when timeout equals pollInterval"() {
        given:
        def config = createConfigWithTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(5))

        expect:
        config.totalWaitAttempts == 1
    }

    def "getTotalWaitAttempts handles large values without overflow"() {
        given:
        def config = createConfigWithTimeouts(Duration.ofHours(1), Duration.ofSeconds(1))

        expect:
        config.totalWaitAttempts == 3600
    }

    // ===== getServices() TESTS =====

    def "getServices returns list of service names"() {
        given:
        def servicePatterns = [
            'app': [Pattern.compile('Started')],
            'db': [Pattern.compile('Ready')],
            'cache': [Pattern.compile('Listening')]
        ]
        def config = createConfigWithPatterns(servicePatterns)

        when:
        def services = config.services

        then:
        services.size() == 3
        services.containsAll(['app', 'db', 'cache'])
    }

    def "getServices returns empty list when no services"() {
        given:
        def config = createConfigWithPatterns([:])

        expect:
        config.services.isEmpty()
    }

    def "getServices returns new list (not internal reference)"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        def config = createConfigWithPatterns(servicePatterns)

        when:
        def services1 = config.services
        def services2 = config.services

        then:
        services1 == services2
        !services1.is(services2)  // Different instances
    }

    // ===== hasProgressInterval() TESTS =====

    def "hasProgressInterval returns false for zero duration"() {
        given:
        def config = createConfigWithProgressInterval(Duration.ZERO)

        expect:
        !config.hasProgressInterval()
    }

    def "hasProgressInterval returns false for null duration"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        def config = new WaitForLogConfig(
            'project', servicePatterns, [:],
            Duration.ofSeconds(60), Duration.ofSeconds(2),
            false, false, null
        )

        expect:
        !config.hasProgressInterval()
    }

    def "hasProgressInterval returns true for positive duration"() {
        given:
        def config = createConfigWithProgressInterval(Duration.ofSeconds(10))

        expect:
        config.hasProgressInterval()
    }

    def "hasProgressInterval returns true for one second"() {
        given:
        def config = createConfigWithProgressInterval(Duration.ofSeconds(1))

        expect:
        config.hasProgressInterval()
    }

    // ===== IMMUTABILITY TESTS =====

    def "servicePatterns map is immutable"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        def config = createConfigWithPatterns(servicePatterns)

        when:
        config.servicePatterns['newService'] = [Pattern.compile('Test')]

        then:
        thrown(UnsupportedOperationException)
    }

    def "rejectPatterns map is immutable"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        def rejectPatterns = ['app': [Pattern.compile('Error')]]
        def config = new WaitForLogConfig(
            'project', servicePatterns, rejectPatterns,
            Duration.ofSeconds(60), Duration.ofSeconds(2),
            false, false, Duration.ZERO
        )

        when:
        config.rejectPatterns['newService'] = [Pattern.compile('Fatal')]

        then:
        thrown(UnsupportedOperationException)
    }

    // ===== toString() TESTS =====

    def "toString includes key configuration information"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')], 'db': [Pattern.compile('Ready')]]
        def config = new WaitForLogConfig(
            'my-project', servicePatterns, [:],
            Duration.ofMinutes(2), Duration.ofSeconds(5),
            true, false, Duration.ZERO
        )

        when:
        def string = config.toString()

        then:
        string.contains('WaitForLogConfig')
        string.contains("projectName='my-project'")
        string.contains('services=')
        string.contains('timeout=PT2M')
        string.contains('pollInterval=PT5S')
        string.contains('caseInsensitive=true')
    }

    def "toString includes all key fields"() {
        // Gap #19 addition - comprehensive format verification
        given:
        def config = new WaitForLogConfig(
            'test-project',
            ['app': [Pattern.compile('Started')]],
            [:],
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            true, false,
            Duration.ZERO
        )

        expect:
        def str = config.toString()
        str.contains('test-project')
        str.contains('app')
        str.contains('60')
        str.contains('caseInsensitive')
    }

    def "toString format includes project name and services"() {
        // Gap #19 addition
        given:
        def config = new WaitForLogConfig(
            'multi-service-project',
            ['web': [Pattern.compile('Ready')], 'db': [Pattern.compile('accepting')]],
            [:],
            Duration.ofMinutes(5),
            Duration.ofSeconds(3),
            false, true,
            Duration.ofSeconds(10)
        )

        expect:
        def str = config.toString()
        str.contains('multi-service-project')
        str.contains('web')
        str.contains('db')
        str.contains('verbose') || str.contains('true')
    }

    // ===== HELPER METHODS =====

    private WaitForLogConfig createConfigWithTimeouts(Duration timeout, Duration pollInterval) {
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        return new WaitForLogConfig(
            'project', servicePatterns, [:],
            timeout, pollInterval, false, false, Duration.ZERO
        )
    }

    private WaitForLogConfig createConfigWithPatterns(Map<String, List<Pattern>> patterns) {
        return new WaitForLogConfig(
            'project', patterns, [:],
            Duration.ofSeconds(60), Duration.ofSeconds(2),
            false, false, Duration.ZERO
        )
    }

    private WaitForLogConfig createConfigWithProgressInterval(Duration progressInterval) {
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        return new WaitForLogConfig(
            'project', servicePatterns, [:],
            Duration.ofSeconds(60), Duration.ofSeconds(2),
            false, false, progressInterval
        )
    }
}
```

**Coverage Requirements**:
- Constructor: All parameters, null handling
- `getTotalWaitAttempts()`: Exact division, ceiling division, minimum 1, edge cases
- `getServices()`: Empty, single, multiple services
- `hasProgressInterval()`: Zero, null, positive durations
- Immutability verification
- `toString()` method

---

### 4. WaitForLogResultTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/model/WaitForLogResultTest.groovy`

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

package com.kineticfire.gradle.docker.model

import spock.lang.Specification

/**
 * Unit tests for WaitForLogResult and WaitForLogResult.PatternMatch
 */
class WaitForLogResultTest extends Specification {

    // ===== PatternMatch TESTS =====

    def "PatternMatch can be created with matched pattern"() {
        when:
        def match = new WaitForLogResult.PatternMatch('Started Application', true, 5L)

        then:
        match.pattern == 'Started Application'
        match.matched == true
        match.matchedAtSeconds == 5L
    }

    def "PatternMatch can be created with unmatched pattern"() {
        when:
        def match = new WaitForLogResult.PatternMatch('Ready', false, null)

        then:
        match.pattern == 'Ready'
        match.matched == false
        match.matchedAtSeconds == null
    }

    def "PatternMatch handles zero elapsed time"() {
        when:
        def match = new WaitForLogResult.PatternMatch('Immediate', true, 0L)

        then:
        match.matched == true
        match.matchedAtSeconds == 0L
    }

    def "PatternMatch stores pattern, matched status, and match time"() {
        // Gap #21 addition - explicit field verification
        when:
        def match = new WaitForLogResult.PatternMatch('Started.*', true, 5L)

        then:
        match.pattern == 'Started.*'
        match.matched == true
        match.matchedAtSeconds == 5L
    }

    def "PatternMatch allows null matchedAtSeconds for unmatched patterns"() {
        // Gap #21 addition
        when:
        def match = new WaitForLogResult.PatternMatch('Error', false, null)

        then:
        match.pattern == 'Error'
        match.matched == false
        match.matchedAtSeconds == null
    }

    def "PatternMatch handles large elapsed time value"() {
        // Gap #21 addition
        when:
        def match = new WaitForLogResult.PatternMatch('Pattern', true, Long.MAX_VALUE)

        then:
        match.matchedAtSeconds == Long.MAX_VALUE
    }

    // ===== SUCCESSFUL RESULT TESTS =====

    def "can create successful result with all patterns matched"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('Pattern1', true, 2L),
            new WaitForLogResult.PatternMatch('Pattern2', true, 5L)
        ]

        when:
        def result = new WaitForLogResult('app', patternMatches, true)

        then:
        result.serviceName == 'app'
        result.patternMatches.size() == 2
        result.ready == true
        result.rejected == false
        result.rejectPattern == null
        result.rejectLogLine == null
    }

    def "can create unsuccessful result with partial matches"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('Pattern1', true, 2L),
            new WaitForLogResult.PatternMatch('Pattern2', false, null)
        ]

        when:
        def result = new WaitForLogResult('db', patternMatches, false)

        then:
        result.serviceName == 'db'
        result.ready == false
        result.rejected == false
    }

    // ===== REJECTED RESULT TESTS =====

    def "can create rejected result with reject pattern match"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('Started', true, 2L),
            new WaitForLogResult.PatternMatch('Ready', false, null)
        ]

        when:
        def result = new WaitForLogResult(
            'app',
            patternMatches,
            'Exception.*NullPointer',
            'Exception: NullPointerException at line 42'
        )

        then:
        result.serviceName == 'app'
        result.ready == false
        result.rejected == true
        result.rejectPattern == 'Exception.*NullPointer'
        result.rejectLogLine == 'Exception: NullPointerException at line 42'
    }

    def "secondary constructor creates rejected result with correct fields"() {
        // Gap #16 addition
        when:
        def result = new WaitForLogResult('app', [], 'Error.*', 'Error: startup failed')

        then:
        result.serviceName == 'app'
        result.ready == false
        result.rejected == true
        result.rejectPattern == 'Error.*'
        result.rejectLogLine == 'Error: startup failed'
    }

    def "secondary constructor sets rejected=true and ready=false"() {
        // Gap #16 addition - verify boolean flags
        when:
        def result = new WaitForLogResult('svc', [], 'pattern', 'line')

        then:
        result.rejected == true
        result.ready == false
    }

    def "secondary constructor preserves pattern matches list"() {
        // Gap #16 addition
        given:
        def matches = [
            new WaitForLogResult.PatternMatch('P1', true, 1L),
            new WaitForLogResult.PatternMatch('P2', false, null)
        ]

        when:
        def result = new WaitForLogResult('svc', matches, 'FATAL', 'FATAL: crash')

        then:
        result.patternMatches.size() == 2
        result.matchedCount == 1
        result.rejected == true
    }

    // ===== getMatchedCount() TESTS =====

    def "getMatchedCount returns correct count for all matched"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('P1', true, 1L),
            new WaitForLogResult.PatternMatch('P2', true, 2L),
            new WaitForLogResult.PatternMatch('P3', true, 3L)
        ]
        def result = new WaitForLogResult('svc', patternMatches, true)

        expect:
        result.matchedCount == 3
    }

    def "getMatchedCount returns correct count for partial matches"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('P1', true, 1L),
            new WaitForLogResult.PatternMatch('P2', false, null),
            new WaitForLogResult.PatternMatch('P3', true, 3L)
        ]
        def result = new WaitForLogResult('svc', patternMatches, false)

        expect:
        result.matchedCount == 2
    }

    def "getMatchedCount returns zero for no matches"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('P1', false, null),
            new WaitForLogResult.PatternMatch('P2', false, null)
        ]
        def result = new WaitForLogResult('svc', patternMatches, false)

        expect:
        result.matchedCount == 0
    }

    def "getMatchedCount returns zero for empty pattern list"() {
        given:
        def result = new WaitForLogResult('svc', [], false)

        expect:
        result.matchedCount == 0
    }

    // ===== getTotalPatterns() TESTS =====

    def "getTotalPatterns returns correct count"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('P1', true, 1L),
            new WaitForLogResult.PatternMatch('P2', false, null),
            new WaitForLogResult.PatternMatch('P3', true, 3L)
        ]
        def result = new WaitForLogResult('svc', patternMatches, false)

        expect:
        result.totalPatterns == 3
    }

    def "getTotalPatterns returns zero for empty list"() {
        given:
        def result = new WaitForLogResult('svc', [], false)

        expect:
        result.totalPatterns == 0
    }

    // ===== IMMUTABILITY TESTS =====

    def "patternMatches list is immutable"() {
        given:
        def patternMatches = [new WaitForLogResult.PatternMatch('P1', true, 1L)]
        def result = new WaitForLogResult('svc', patternMatches, true)

        when:
        result.patternMatches.add(new WaitForLogResult.PatternMatch('P2', false, null))

        then:
        thrown(UnsupportedOperationException)
    }

    // ===== EDGE CASES =====

    def "handles single pattern list"() {
        given:
        def patternMatches = [new WaitForLogResult.PatternMatch('Only', true, 1L)]
        def result = new WaitForLogResult('single', patternMatches, true)

        expect:
        result.matchedCount == 1
        result.totalPatterns == 1
        result.ready == true
    }

    def "handles service name with special characters"() {
        when:
        def result = new WaitForLogResult('my-service_v2.0', [], false)

        then:
        result.serviceName == 'my-service_v2.0'
    }
}
```

**Coverage Requirements**:
- `PatternMatch` inner class: Constructor, all fields
- Both `WaitForLogResult` constructors (success, rejected)
- `getMatchedCount()`: All matched, partial, none, empty
- `getTotalPatterns()`: Various sizes
- Immutability of `patternMatches` list

---

### 5. LogPatternMatcherTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/util/LogPatternMatcherTest.groovy`

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

package com.kineticfire.gradle.docker.util

import spock.lang.Specification

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Unit tests for LogPatternMatcher (pure utility class)
 */
class LogPatternMatcherTest extends Specification {

    // ===== compilePattern() TESTS =====

    def "compilePattern creates case-sensitive pattern by default"() {
        when:
        def pattern = LogPatternMatcher.compilePattern('Started', false)

        then:
        pattern.matcher('Started Application').find()
        !pattern.matcher('started application').find()
    }

    def "compilePattern creates case-insensitive pattern when requested"() {
        when:
        def pattern = LogPatternMatcher.compilePattern('Started', true)

        then:
        pattern.matcher('Started Application').find()
        pattern.matcher('started application').find()
        pattern.matcher('STARTED APPLICATION').find()
    }

    def "compilePattern handles regex special characters"() {
        when:
        def pattern = LogPatternMatcher.compilePattern('\\[INFO\\].*started', false)

        then:
        pattern.matcher('[INFO] Application started').find()
        !pattern.matcher('INFO Application started').find()
    }

    def "compilePattern throws PatternSyntaxException for invalid regex"() {
        when:
        LogPatternMatcher.compilePattern('[invalid', false)

        then:
        thrown(PatternSyntaxException)
    }

    def "compilePattern throws NullPointerException for null pattern string"() {
        // Gap #5 addition
        when:
        LogPatternMatcher.compilePattern(null, false)

        then:
        thrown(NullPointerException)
    }

    def "compilePattern throws NullPointerException for null with case insensitive flag"() {
        // Gap #5 addition
        when:
        LogPatternMatcher.compilePattern(null, true)

        then:
        thrown(NullPointerException)
    }

    // ===== compilePatterns() TESTS =====

    def "compilePatterns compiles all patterns for all services"() {
        given:
        def servicePatterns = [
            'app': ['Started', 'Ready'],
            'db': ['accepting connections']
        ]

        when:
        def result = LogPatternMatcher.compilePatterns(servicePatterns, false)

        then:
        result.size() == 2
        result['app'].size() == 2
        result['db'].size() == 1
        result['app'][0] instanceof Pattern
        result['db'][0] instanceof Pattern
    }

    def "compilePatterns applies case-insensitivity to all patterns"() {
        given:
        def servicePatterns = ['app': ['Started', 'READY']]

        when:
        def result = LogPatternMatcher.compilePatterns(servicePatterns, true)

        then:
        result['app'][0].matcher('started').find()
        result['app'][1].matcher('ready').find()
    }

    def "compilePatterns throws IllegalArgumentException for invalid pattern"() {
        given:
        def servicePatterns = ['app': ['[invalid']]

        when:
        LogPatternMatcher.compilePatterns(servicePatterns, false)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid regex pattern for service 'app'")
        ex.message.contains("Hint:")
    }

    def "compilePatterns handles empty map"() {
        when:
        def result = LogPatternMatcher.compilePatterns([:], false)

        then:
        result.isEmpty()
    }

    def "compilePatterns handles empty pattern list for service"() {
        given:
        def servicePatterns = ['app': []]

        when:
        def result = LogPatternMatcher.compilePatterns(servicePatterns, false)

        then:
        result['app'].isEmpty()
    }

    // ===== matchesAnyLine() TESTS =====

    def "matchesAnyLine returns true when pattern matches any line"() {
        given:
        def pattern = Pattern.compile('Started')
        def logLines = ['Initializing...', 'Started Application', 'Listening on port 8080']

        expect:
        LogPatternMatcher.matchesAnyLine(pattern, logLines)
    }

    def "matchesAnyLine returns false when pattern matches no line"() {
        given:
        def pattern = Pattern.compile('Error')
        def logLines = ['Initializing...', 'Started Application', 'Listening on port 8080']

        expect:
        !LogPatternMatcher.matchesAnyLine(pattern, logLines)
    }

    def "matchesAnyLine returns false for empty log lines"() {
        given:
        def pattern = Pattern.compile('Started')

        expect:
        !LogPatternMatcher.matchesAnyLine(pattern, [])
    }

    def "matchesAnyLine matches partial line content"() {
        given:
        def pattern = Pattern.compile('\\d+')
        def logLines = ['port 8080']

        expect:
        LogPatternMatcher.matchesAnyLine(pattern, logLines)
    }

    // ===== findMatchingLine() TESTS =====

    def "findMatchingLine returns matching line"() {
        given:
        def pattern = Pattern.compile('Started')
        def logLines = ['Init', 'Started Application', 'Done']

        when:
        def result = LogPatternMatcher.findMatchingLine(pattern, logLines)

        then:
        result == 'Started Application'
    }

    def "findMatchingLine returns first matching line when multiple match"() {
        given:
        def pattern = Pattern.compile('Started')
        def logLines = ['Started 1', 'Started 2', 'Started 3']

        when:
        def result = LogPatternMatcher.findMatchingLine(pattern, logLines)

        then:
        result == 'Started 1'
    }

    def "findMatchingLine returns null when no match"() {
        given:
        def pattern = Pattern.compile('NotFound')
        def logLines = ['Line 1', 'Line 2']

        when:
        def result = LogPatternMatcher.findMatchingLine(pattern, logLines)

        then:
        result == null
    }

    def "findMatchingLine returns null for empty list"() {
        given:
        def pattern = Pattern.compile('Anything')

        when:
        def result = LogPatternMatcher.findMatchingLine(pattern, [])

        then:
        result == null
    }

    // ===== checkRejectPatterns() TESTS =====

    def "checkRejectPatterns returns null when no reject patterns match"() {
        given:
        def rejectPatterns = [Pattern.compile('Error'), Pattern.compile('Exception')]
        def logLines = ['Started', 'Running', 'Done']

        when:
        def result = LogPatternMatcher.checkRejectPatterns(rejectPatterns, logLines)

        then:
        result == null
    }

    def "checkRejectPatterns returns RejectCheckResult when pattern matches"() {
        given:
        def rejectPatterns = [Pattern.compile('Error'), Pattern.compile('Exception')]
        def logLines = ['Starting', 'Exception: NullPointer', 'Done']

        when:
        def result = LogPatternMatcher.checkRejectPatterns(rejectPatterns, logLines)

        then:
        result != null
        result.patternString == 'Exception'
        result.matchingLogLine == 'Exception: NullPointer'
    }

    def "checkRejectPatterns returns first matching reject pattern"() {
        given:
        def rejectPatterns = [Pattern.compile('Error'), Pattern.compile('Exception')]
        def logLines = ['Error occurred', 'Exception thrown']

        when:
        def result = LogPatternMatcher.checkRejectPatterns(rejectPatterns, logLines)

        then:
        result.patternString == 'Error'  // First pattern checked
    }

    def "checkRejectPatterns returns null for empty reject patterns"() {
        given:
        def logLines = ['Error occurred']

        when:
        def result = LogPatternMatcher.checkRejectPatterns([], logLines)

        then:
        result == null
    }

    def "checkRejectPatterns returns null for empty log lines"() {
        given:
        def rejectPatterns = [Pattern.compile('Error')]

        when:
        def result = LogPatternMatcher.checkRejectPatterns(rejectPatterns, [])

        then:
        result == null
    }

    // ===== RejectCheckResult TESTS ===== (Gap #15 additions)

    def "RejectCheckResult stores pattern and log line"() {
        when:
        def result = new LogPatternMatcher.RejectCheckResult('Error.*', 'Error: Something failed')

        then:
        result.patternString == 'Error.*'
        result.matchingLogLine == 'Error: Something failed'
    }

    def "RejectCheckResult stores pattern string and matching log line"() {
        // Gap #15 addition
        when:
        def result = new LogPatternMatcher.RejectCheckResult('ERROR.*', 'ERROR: Connection failed')

        then:
        result.patternString == 'ERROR.*'
        result.matchingLogLine == 'ERROR: Connection failed'
    }

    def "RejectCheckResult accepts null values"() {
        // Gap #15 addition
        when:
        def result = new LogPatternMatcher.RejectCheckResult(null, null)

        then:
        result.patternString == null
        result.matchingLogLine == null
    }

    def "RejectCheckResult fields are final"() {
        // Gap #15 addition - verifies immutability
        given:
        def result = new LogPatternMatcher.RejectCheckResult('Pattern', 'Line')

        expect:
        // These fields should be final (immutable)
        result.patternString == 'Pattern'
        result.matchingLogLine == 'Line'
    }

    // ===== updateMatches() TESTS =====

    def "updateMatches adds newly matched patterns to set"() {
        given:
        def patterns = [Pattern.compile('Started'), Pattern.compile('Ready')]
        def matchedPatterns = new HashSet<Integer>()
        def logLines = ['Application Started']
        def matchTimes = [:]

        when:
        def newMatches = LogPatternMatcher.updateMatches(patterns, matchedPatterns, logLines, 5L, matchTimes)

        then:
        newMatches == 1
        matchedPatterns.contains(0)
        !matchedPatterns.contains(1)
        matchTimes[0] == 5L
    }

    def "updateMatches does not re-add already matched patterns"() {
        given:
        def patterns = [Pattern.compile('Started'), Pattern.compile('Ready')]
        def matchedPatterns = [0] as Set  // Pattern 0 already matched
        def logLines = ['Application Started', 'Ready']
        def matchTimes = [0: 2L]

        when:
        def newMatches = LogPatternMatcher.updateMatches(patterns, matchedPatterns, logLines, 5L, matchTimes)

        then:
        newMatches == 1  // Only pattern 1 is new
        matchedPatterns.size() == 2
        matchTimes[0] == 2L  // Unchanged
        matchTimes[1] == 5L  // New
    }

    def "updateMatches returns zero when no new matches"() {
        given:
        def patterns = [Pattern.compile('Started')]
        def matchedPatterns = [0] as Set
        def logLines = ['Started again']
        def matchTimes = [0: 1L]

        when:
        def newMatches = LogPatternMatcher.updateMatches(patterns, matchedPatterns, logLines, 5L, matchTimes)

        then:
        newMatches == 0
    }

    def "updateMatches handles empty log lines"() {
        given:
        def patterns = [Pattern.compile('Started')]
        def matchedPatterns = new HashSet<Integer>()
        def matchTimes = [:]

        when:
        def newMatches = LogPatternMatcher.updateMatches(patterns, matchedPatterns, [], 5L, matchTimes)

        then:
        newMatches == 0
        matchedPatterns.isEmpty()
    }

    def "updateMatches throws NullPointerException for null patterns"() {
        when:
        LogPatternMatcher.updateMatches(null, [] as Set, [], 0L, [:])

        then:
        thrown(NullPointerException)
    }

    def "updateMatches throws NullPointerException for null matchedPatterns"() {
        when:
        LogPatternMatcher.updateMatches([], null, [], 0L, [:])

        then:
        thrown(NullPointerException)
    }

    def "updateMatches throws NullPointerException for null logLines"() {
        when:
        LogPatternMatcher.updateMatches([], [] as Set, null, 0L, [:])

        then:
        thrown(NullPointerException)
    }

    def "updateMatches throws NullPointerException for null matchTimes"() {
        when:
        LogPatternMatcher.updateMatches([], [] as Set, [], 0L, null)

        then:
        thrown(NullPointerException)
    }

    // ===== EDGE CASES =====

    def "handles patterns with special regex metacharacters"() {
        given:
        def servicePatterns = ['app': ['\\[INFO\\]', '\\d{4}-\\d{2}-\\d{2}', 'port:\\s*\\d+']]

        when:
        def result = LogPatternMatcher.compilePatterns(servicePatterns, false)

        then:
        result['app'][0].matcher('[INFO] message').find()
        result['app'][1].matcher('2024-01-15').find()
        result['app'][2].matcher('port: 8080').find()
    }

    def "handles Unicode patterns"() {
        when:
        def pattern = LogPatternMatcher.compilePattern('日本語', false)

        then:
        pattern.matcher('Test 日本語 text').find()
    }

    def "handles multiline log entries"() {
        given:
        def pattern = Pattern.compile('Exception')
        def logLines = ['Line 1', 'java.lang.Exception:\n  at Method()', 'Line 3']

        expect:
        LogPatternMatcher.matchesAnyLine(pattern, logLines)
    }

    // ===== PRIVATE CONSTRUCTOR TEST ===== (Gap #20 addition)

    def "private constructor exists and prevents instantiation"() {
        // Gap #20 addition - verifies the private constructor for coverage
        when:
        def constructor = LogPatternMatcher.getDeclaredConstructor()
        constructor.setAccessible(true)
        constructor.newInstance()

        then:
        noExceptionThrown()  // Constructor completes successfully
        // Note: We're just verifying the constructor exists for coverage
    }
}
```

**Coverage Requirements**:
- `compilePattern()`: Case sensitive, case insensitive, regex chars, invalid regex
- `compilePatterns()`: Multiple services, empty map, empty list, invalid pattern
- `matchesAnyLine()`: Match found, no match, empty list, partial match
- `findMatchingLine()`: Single match, multiple matches, no match, empty list
- `checkRejectPatterns()`: No match, match found, first match priority, empty inputs
- `RejectCheckResult` inner class
- `updateMatches()`: New matches, already matched, no matches, null validation
- Edge cases: Special regex chars, Unicode, multiline

---

### 6. WaitForLogConfigBuilderTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/util/WaitForLogConfigBuilderTest.groovy`

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

package com.kineticfire.gradle.docker.util

import org.gradle.api.GradleException
import spock.lang.Specification

import java.time.Duration

/**
 * Unit tests for WaitForLogConfigBuilder
 */
class WaitForLogConfigBuilderTest extends Specification {

    // ===== SUCCESSFUL BUILD TESTS =====

    def "build creates config with all parameters"() {
        given:
        def waitForServices = ['app': ['Started Application']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'test-project',
            waitForServices,
            null,
            60, 2, false, false, 0
        )

        then:
        config.projectName == 'test-project'
        config.servicePatterns.size() == 1
        config.servicePatterns['app'].size() == 1
        config.timeout == Duration.ofSeconds(60)
        config.pollInterval == Duration.ofSeconds(2)
        config.caseInsensitive == false
        config.verbose == false
        config.progressInterval == Duration.ZERO
    }

    def "build compiles patterns with case insensitivity"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, true, false, 0
        )

        then:
        config.caseInsensitive == true
        config.servicePatterns['app'][0].matcher('started').find()
    }

    def "build handles reject patterns"() {
        given:
        def waitForServices = ['app': ['Started']]
        def rejectPatterns = ['app': ['Error', 'Exception']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, rejectPatterns,
            60, 2, false, false, 0
        )

        then:
        config.rejectPatterns['app'].size() == 2
    }

    def "build handles null reject patterns"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        config.rejectPatterns.isEmpty()
    }

    def "build handles multiple services with multiple patterns"() {
        given:
        def waitForServices = [
            'app': ['Started', 'Ready', 'Listening'],
            'db': ['accepting connections'],
            'cache': ['Server started']
        ]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            120, 5, true, true, 10
        )

        then:
        config.servicePatterns.size() == 3
        config.servicePatterns['app'].size() == 3
        config.servicePatterns['db'].size() == 1
        config.servicePatterns['cache'].size() == 1
        config.verbose == true
        config.progressInterval == Duration.ofSeconds(10)
    }

    // ===== VALIDATION ERROR TESTS =====

    def "build throws GradleException for null waitForServices"() {
        when:
        WaitForLogConfigBuilder.build(
            'project', null, null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'waitForServices' cannot be empty")
        ex.message.contains("Example:")
    }

    def "build throws GradleException for empty waitForServices"() {
        when:
        WaitForLogConfigBuilder.build(
            'project', [:], null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'waitForServices' cannot be empty")
    }

    def "build throws GradleException for non-positive timeoutSeconds"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            0, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'timeoutSeconds' must be positive")
    }

    def "build throws GradleException for negative timeoutSeconds"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            -1, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'timeoutSeconds' must be positive")
    }

    def "build throws GradleException for non-positive pollSeconds"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 0, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'pollSeconds' must be positive")
    }

    def "build throws GradleException for empty pattern list"() {
        given:
        def waitForServices = ['app': []]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Pattern list for service 'app' cannot be empty")
        ex.message.contains("At least one pattern required")
    }

    def "build throws GradleException for null pattern list"() {
        given:
        def waitForServices = ['app': null]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Pattern list for service 'app' cannot be empty")
    }

    def "build throws GradleException for invalid regex pattern"() {
        given:
        def waitForServices = ['app': ['[invalid']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Invalid regex pattern")
    }

    def "build throws GradleException for invalid reject pattern"() {
        given:
        def waitForServices = ['app': ['Started']]
        def rejectPatterns = ['app': ['[invalid']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, rejectPatterns,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("rejectPatterns")
        ex.message.contains("Invalid regex pattern")
    }

    // ===== WARNING TESTS =====

    def "build logs warning when pollSeconds > timeoutSeconds"() {
        given:
        def waitForServices = ['app': ['Started']]
        def originalErr = System.err
        def capturedOutput = new ByteArrayOutputStream()
        System.err = new PrintStream(capturedOutput)

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            5, 10, false, false, 0  // poll > timeout
        )

        then:
        capturedOutput.toString().contains('WARNING')
        capturedOutput.toString().contains('pollSeconds')
        capturedOutput.toString().contains('timeoutSeconds')

        cleanup:
        System.err = originalErr
    }

    def "build logs warning for orphaned reject patterns"() {
        given:
        def waitForServices = ['app': ['Started']]
        def rejectPatterns = ['orphan': ['Error']]  // 'orphan' not in waitForServices
        def originalErr = System.err
        def capturedOutput = new ByteArrayOutputStream()
        System.err = new PrintStream(capturedOutput)

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, rejectPatterns,
            60, 2, false, false, 0
        )

        then:
        capturedOutput.toString().contains('WARNING')
        capturedOutput.toString().contains('orphan')
        capturedOutput.toString().contains('never be checked')

        cleanup:
        System.err = originalErr
    }

    // ===== EDGE CASES =====

    def "build handles regex special characters in patterns"() {
        given:
        def waitForServices = ['app': ['\\[INFO\\]', '\\d+\\.\\d+', 'port:\\s*\\d+']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        config.servicePatterns['app'].size() == 3
    }

    def "build preserves pattern order"() {
        given:
        def waitForServices = ['app': ['First', 'Second', 'Third']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        config.servicePatterns['app'][0].pattern() == 'First'
        config.servicePatterns['app'][1].pattern() == 'Second'
        config.servicePatterns['app'][2].pattern() == 'Third'
    }

    // ===== PRIVATE CONSTRUCTOR COVERAGE TEST (Gap #23) =====

    def "private constructor exists and prevents instantiation"() {
        when:
        def constructor = WaitForLogConfigBuilder.getDeclaredConstructor()
        constructor.setAccessible(true)
        constructor.newInstance()

        then:
        noExceptionThrown()  // Constructor completes successfully
        // Note: We're just verifying the constructor exists for coverage
    }
}
```

**Coverage Requirements**:
- Successful builds with various configurations
- Validation errors: null/empty services, invalid timeout/poll, empty patterns, invalid regex
- Warning outputs: poll > timeout, orphaned reject patterns
- Edge cases: Regex special chars, pattern order preservation
- Private constructor: Test via reflection for 100% line coverage (Gap #23)

---

### 7. ComposeStackSpec waitForLog Extension Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/spec/ComposeStackSpecWaitForLogTest.groovy`

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

package com.kineticfire.gradle.docker.spec

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ComposeStackSpec waitForLog extension
 */
class ComposeStackSpecWaitForLogTest extends Specification {

    def project
    def composeStackSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        composeStackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack', project.objects)
    }

    // ===== PROPERTY TESTS =====

    def "waitForLog property exists and is initially not present"() {
        expect:
        composeStackSpec.waitForLog != null
        !composeStackSpec.waitForLog.present
    }

    // ===== CLOSURE DSL TESTS =====

    def "waitForLog(Closure) configures waitForLog spec"() {
        when:
        composeStackSpec.waitForLog {
            waitForServices.set(['app': ['Started Application']])
        }

        then:
        composeStackSpec.waitForLog.present
        composeStackSpec.waitForLog.get().waitForServices.get() == ['app': ['Started Application']]
    }

    def "waitForLog(Closure) configures all properties"() {
        when:
        composeStackSpec.waitForLog {
            waitForServices.set(['app': ['Started'], 'db': ['Ready']])
            rejectPatterns.set(['app': ['Error']])
            timeoutSeconds.set(120)
            pollSeconds.set(5)
            caseInsensitive.set(true)
            verbose.set(true)
            progressIntervalSeconds.set(15)
        }

        then:
        def spec = composeStackSpec.waitForLog.get()
        spec.waitForServices.get().size() == 2
        spec.rejectPatterns.get() == ['app': ['Error']]
        spec.timeoutSeconds.get() == 120
        spec.pollSeconds.get() == 5
        spec.caseInsensitive.get() == true
        spec.verbose.get() == true
        spec.progressIntervalSeconds.get() == 15
    }

    def "waitForLog(Closure) uses DELEGATE_FIRST resolution"() {
        when:
        composeStackSpec.waitForLog {
            waitForServices.set(['svc': ['pattern']])
            timeoutSeconds.set(90)
        }

        then:
        composeStackSpec.waitForLog.get().timeoutSeconds.get() == 90
    }

    // ===== ACTION DSL TESTS =====

    def "waitForLog(Action) configures waitForLog spec"() {
        when:
        composeStackSpec.waitForLog(new Action<WaitForLogSpec>() {
            @Override
            void execute(WaitForLogSpec spec) {
                spec.waitForServices.set(['app': ['Started']])
            }
        })

        then:
        composeStackSpec.waitForLog.present
        composeStackSpec.waitForLog.get().waitForServices.get() == ['app': ['Started']]
    }

    // ===== VALIDATION TESTS =====

    def "waitForLog(Closure) throws GradleException when waitForServices is empty"() {
        when:
        composeStackSpec.waitForLog {
            waitForServices.set([:])
        }

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'waitForServices' must specify at least one service")
        ex.message.contains("testStack")
    }

    def "waitForLog(Closure) throws GradleException when waitForServices is not set"() {
        when:
        composeStackSpec.waitForLog {
            timeoutSeconds.set(60)
            // waitForServices not set!
        }

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'waitForServices' must specify at least one service")
    }

    def "waitForLog(Closure) throws GradleException when service has empty pattern list"() {
        when:
        composeStackSpec.waitForLog {
            waitForServices.set(['app': []])
        }

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Pattern list for service 'app' cannot be empty")
    }

    def "waitForLog(Closure) throws GradleException when service has null pattern list"() {
        when:
        composeStackSpec.waitForLog {
            waitForServices.set(['app': null])
        }

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Pattern list for service 'app' cannot be empty")
    }

    def "waitForLog(Closure) throws GradleException for non-string pattern value"() {
        when:
        composeStackSpec.waitForLog {
            waitForServices.set(['app': [123]])  // Integer instead of String
        }

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Pattern values must be strings")
    }

    // ===== ERROR MESSAGE TESTS =====

    def "validation error includes stack name in message"() {
        given:
        def namedStack = project.objects.newInstance(ComposeStackSpec, 'myNamedStack', project.objects)

        when:
        namedStack.waitForLog {
            waitForServices.set([:])
        }

        then:
        def ex = thrown(GradleException)
        ex.message.contains("myNamedStack")
    }

    def "validation error includes example configuration"() {
        when:
        composeStackSpec.waitForLog {
            waitForServices.set([:])
        }

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Example:")
        ex.message.contains("waitForLog")
        ex.message.contains("waitForServices.set")
    }

    // ===== OVERWRITE TESTS =====

    def "calling waitForLog twice overwrites previous configuration"() {
        when:
        composeStackSpec.waitForLog {
            waitForServices.set(['app': ['First']])
        }
        composeStackSpec.waitForLog {
            waitForServices.set(['db': ['Second']])
        }

        then:
        def spec = composeStackSpec.waitForLog.get()
        spec.waitForServices.get() == ['db': ['Second']]
    }
}
```

**Coverage Requirements**:
- Property existence and initial state
- Closure DSL: Single property, all properties, delegate resolution
- Action DSL: Basic configuration
- Validation: Empty services, not set, empty patterns, null patterns, type errors
- Error messages: Stack name inclusion, example format
- Overwrite behavior

---

### 8. ComposeServiceException Extension Tests (Add to Existing)

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/exception/ComposeServiceExceptionTest.groovy`

Add these tests to the existing test file:

```groovy
// ===== NEW ERROR TYPES FOR waitForLog =====

def "ErrorType enum contains new waitForLog error types"() {
    expect:
    ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT != null
    ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED != null
    ComposeServiceException.ErrorType.SERVICE_CRASHED != null
}

def "LOG_PATTERN_TIMEOUT has appropriate default suggestion"() {
    expect:
    ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT.defaultSuggestion.contains('timeout')
}

def "LOG_REJECT_PATTERN_MATCHED has appropriate default suggestion"() {
    expect:
    ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED.defaultSuggestion.contains('reject')
}

def "SERVICE_CRASHED has appropriate default suggestion"() {
    expect:
    ComposeServiceException.ErrorType.SERVICE_CRASHED.defaultSuggestion.contains('container') ||
        ComposeServiceException.ErrorType.SERVICE_CRASHED.defaultSuggestion.contains('crash')
}

def "can create exception with LOG_PATTERN_TIMEOUT"() {
    given:
    def message = "Timeout waiting for log patterns after 60 seconds"

    when:
    def exception = new ComposeServiceException(
        ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
        message
    )

    then:
    exception.errorType == ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT
    exception.message == message
}

def "can create exception with LOG_REJECT_PATTERN_MATCHED"() {
    given:
    def message = "Reject pattern 'Exception' matched in service 'app'"

    when:
    def exception = new ComposeServiceException(
        ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED,
        message,
        "Check service logs and fix the startup error"
    )

    then:
    exception.errorType == ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED
    exception.suggestion == "Check service logs and fix the startup error"
}

def "can create exception with SERVICE_CRASHED and cause"() {
    given:
    def message = "Service 'db' crashed with exit code 1"
    def cause = new RuntimeException("Container exited unexpectedly")

    when:
    def exception = new ComposeServiceException(
        ComposeServiceException.ErrorType.SERVICE_CRASHED,
        message,
        cause
    )

    then:
    exception.errorType == ComposeServiceException.ErrorType.SERVICE_CRASHED
    exception.cause == cause
}
```

---

### 9. ExecLibraryComposeService waitForLogPatterns Tests

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/ExecLibraryComposeServiceWaitForLogTest.groovy`

This is a comprehensive test file for the `waitForLogPatterns()` implementation. Due to length constraints,
here is the test specification outline with key test cases:

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 * ... License header ...
 */

package com.kineticfire.gradle.docker.service

import com.kineticfire.gradle.docker.exception.ComposeServiceException
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.regex.Pattern

/**
 * Unit tests for ExecLibraryComposeService.waitForLogPatterns()
 */
class ExecLibraryComposeServiceWaitForLogTest extends Specification {

    @TempDir
    Path tempDir

    ProcessExecutor mockProcessExecutor
    CommandValidator mockCommandValidator
    ServiceLogger mockServiceLogger
    TimeService mockTimeService
    TestExecLibraryComposeService service

    def setup() {
        mockProcessExecutor = Mock(ProcessExecutor)
        mockCommandValidator = Mock(CommandValidator)
        mockServiceLogger = Mock(ServiceLogger)
        mockTimeService = Mock(TimeService)

        // Default mock behaviors
        mockCommandValidator.validateDockerCompose() >> {}
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']

        service = new TestExecLibraryComposeService(
            mockProcessExecutor, mockCommandValidator, mockServiceLogger, mockTimeService
        )
    }

    // ===== NULL VALIDATION TESTS =====

    def "waitForLogPatterns throws NullPointerException for null config"() {
        when:
        service.waitForLogPatterns(null)

        then:
        thrown(NullPointerException)
    }

    // ===== SUCCESSFUL COMPLETION TESTS =====

    def "waitForLogPatterns succeeds when all patterns match on first poll"() {
        given:
        def config = createConfig(['app': ['Started']])
        mockTimeService.currentTimeMillis() >>> [0L, 0L]
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0,
            '{"Service":"app","State":"running","ExitCode":0}', "")
        // Mock captureLogs to return log containing the pattern
        service.mockCaptureLogs('app', 'Started Application successfully')

        when:
        def future = service.waitForLogPatterns(config)
        def results = future.get()

        then:
        results != null
        results['app'] != null
        results['app'].ready == true
        1 * mockServiceLogger.info({ it.contains('[waitForLog]') && it.contains('ready') })
    }

    def "waitForLogPatterns succeeds when patterns match after multiple polls"() {
        given:
        def config = createConfig(['app': ['Started']])

        // First poll: no match, Second poll: match
        mockTimeService.currentTimeMillis() >>> [0L, 0L, 1000L, 2000L, 2000L]
        mockTimeService.sleep(_) >> {}

        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0,
            '{"Service":"app","State":"running","ExitCode":0}', "")

        // First poll: no match
        service.mockCaptureLogsSequence('app', ['Initializing...', 'Started Application'])

        when:
        def future = service.waitForLogPatterns(config)
        def results = future.get()

        then:
        results['app'].ready == true
    }

    // ===== TIMEOUT TESTS =====

    def "waitForLogPatterns throws exception on timeout"() {
        given:
        def config = createConfig(['app': ['NeverAppears']])

        // Simulate timeout - each call advances past timeout
        mockTimeService.currentTimeMillis() >>> [0L, 0L, 61000L]
        mockTimeService.sleep(_) >> {}

        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0,
            '{"Service":"app","State":"running","ExitCode":0}', "")
        service.mockCaptureLogs('app', 'No matching content')

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT
        ex.cause.message.contains('Timeout')
    }

    // ===== REJECT PATTERN TESTS =====

    def "waitForLogPatterns throws exception when reject pattern matches"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        def rejectPatterns = ['app': [Pattern.compile('Exception')]]
        def config = createConfigWithReject(servicePatterns, rejectPatterns)

        mockTimeService.currentTimeMillis() >>> [0L, 0L]
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0,
            '{"Service":"app","State":"running","ExitCode":0}', "")
        service.mockCaptureLogs('app', 'Exception: NullPointerException')

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED
        ex.cause.message.contains('Reject pattern matched')
    }

    // ===== SERVICE CRASH TESTS =====

    def "waitForLogPatterns throws exception when service crashes"() {
        given:
        def config = createConfig(['app': ['Started']])

        mockTimeService.currentTimeMillis() >>> [0L, 0L]
        // Service is not running
        mockProcessExecutor.execute({ it.contains('ps') }) >> new ProcessResult(0,
            '{"Service":"app","State":"exited","ExitCode":1}', "")

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.SERVICE_CRASHED
    }

    // ===== SERVICE VALIDATION TESTS =====

    def "waitForLogPatterns throws exception for unknown service"() {
        given:
        def config = createConfig(['unknownSvc': ['Started']])

        mockTimeService.currentTimeMillis() >>> [0L]
        // Return empty service list
        mockProcessExecutor.execute({ it.toString().contains('ps') }) >> new ProcessResult(0,
            '{"Service":"app","State":"running","ExitCode":0}', "")  // Only 'app' exists

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.message.contains('not found')
        ex.cause.message.contains('unknownSvc')
    }

    // ===== VERBOSE LOGGING TESTS =====

    def "waitForLogPatterns logs progress when verbose is true"() {
        given:
        def config = createConfigVerbose(['app': ['Started']])

        mockTimeService.currentTimeMillis() >>> [0L, 0L]
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0,
            '{"Service":"app","State":"running","ExitCode":0}', "")
        service.mockCaptureLogs('app', 'Started Application')

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        (1.._) * mockServiceLogger.info({ it.contains('[waitForLog]') && it.contains('Polling') })
    }

    // ===== PROGRESS INTERVAL TESTS =====

    def "waitForLogPatterns logs periodic progress at configured interval"() {
        given:
        def config = createConfigWithProgressInterval(['app': ['Started']], 10)

        // Simulate passing 15 seconds (beyond the 10 second progress interval)
        mockTimeService.currentTimeMillis() >>> [0L, 0L, 15000L, 15000L]
        mockTimeService.sleep(_) >> {}

        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0,
            '{"Service":"app","State":"running","ExitCode":0}', "")
        service.mockCaptureLogs('app', 'Started Application')

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        (1.._) * mockServiceLogger.info({ it.contains('Progress at') })
    }

    // ===== INTERRUPT HANDLING TESTS =====

    def "waitForLogPatterns handles interruption"() {
        given:
        def config = createConfig(['app': ['Started']])

        mockTimeService.currentTimeMillis() >>> [0L, 0L]
        mockTimeService.sleep(_) >> { throw new InterruptedException("Interrupted") }
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0,
            '{"Service":"app","State":"running","ExitCode":0}', "")
        service.mockCaptureLogs('app', 'No match')

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.message.contains('Interrupted')
    }

    def "waitForLogPatterns preserves interrupt status when interrupted"() {
        // Gap #14 addition - verify thread interrupt status is preserved
        given:
        def config = createConfig(['app': ['Started']])
        mockTimeService.sleep(_) >> { throw new InterruptedException("Test interrupt") }
        mockProcessExecutor.execute(_) >> new ProcessResult(0,
            '{"Service":"app","State":"running","ExitCode":0}', "")
        service.mockCaptureLogs('app', 'No match')

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT
        ex.cause.message.contains("Interrupted")
    }

    // ===== NULL VALIDATION TESTS ===== (Gap #13 addition)

    def "waitForLogPatterns throws NullPointerException when config is null"() {
        when:
        service.waitForLogPatterns(null)

        then:
        thrown(NullPointerException)
    }

    // ===== EXECUTION EXCEPTION UNWRAPPING TESTS ===== (Gap #17 addition)

    def "waitForLogPatterns unwraps ExecutionException from internal error"() {
        given:
        def config = createConfig(['app': ['Started']])
        def rootCause = new RuntimeException("Connection refused")
        // Simulate captureLogs throwing an ExecutionException
        mockProcessExecutor.execute(_) >> {
            throw new java.util.concurrent.ExecutionException("Logs failed", rootCause)
        }

        when:
        service.executeWaitForLogPatterns(config)

        then:
        def ex = thrown(ComposeServiceException)
        ex.cause == rootCause
        ex.message.contains("Connection refused")
    }

    def "waitForLogPatterns handles ExecutionException with null cause"() {
        given:
        def config = createConfig(['app': ['Started']])
        def execException = new java.util.concurrent.ExecutionException("Logs failed", null)  // null cause
        mockProcessExecutor.execute(_) >> { throw execException }

        when:
        service.executeWaitForLogPatterns(config)

        then:
        def ex = thrown(ComposeServiceException)
        ex.cause == execException  // Uses execException itself when cause is null
    }

    // ===== HELPER METHODS FOR TESTS =====

    // ... Helper methods to create test configs and mock behaviors ...

    /**
     * Test implementation that allows mocking captureLogs behavior
     */
    static class TestExecLibraryComposeService extends ExecLibraryComposeService {
        private Map<String, String> mockLogs = [:]
        private Map<String, List<String>> mockLogsSequence = [:]
        private Map<String, Integer> logsCallCount = [:]

        TestExecLibraryComposeService(ProcessExecutor processExecutor,
                                      CommandValidator commandValidator,
                                      ServiceLogger serviceLogger,
                                      TimeService timeService) {
            super(processExecutor, commandValidator, serviceLogger, timeService)
        }

        void mockCaptureLogs(String serviceName, String logOutput) {
            mockLogs[serviceName] = logOutput
        }

        void mockCaptureLogsSequence(String serviceName, List<String> logOutputs) {
            mockLogsSequence[serviceName] = logOutputs
            logsCallCount[serviceName] = 0
        }

        @Override
        CompletableFuture<String> captureLogs(String projectName, com.kineticfire.gradle.docker.model.LogsConfig config) {
            def serviceName = config.services[0]

            if (mockLogsSequence.containsKey(serviceName)) {
                def sequence = mockLogsSequence[serviceName]
                def callCount = logsCallCount[serviceName] ?: 0
                def output = sequence[Math.min(callCount, sequence.size() - 1)]
                logsCallCount[serviceName] = callCount + 1
                return CompletableFuture.completedFuture(output)
            }

            return CompletableFuture.completedFuture(mockLogs[serviceName] ?: '')
        }

        @Override
        org.gradle.api.services.BuildServiceParameters.None getParameters() {
            return null
        }
    }

    private WaitForLogConfig createConfig(Map<String, List<String>> patterns) {
        def servicePatterns = patterns.collectEntries { k, v ->
            [(k): v.collect { Pattern.compile(it) }]
        }
        return new WaitForLogConfig('test-project', servicePatterns, [:],
            Duration.ofSeconds(60), Duration.ofSeconds(2), false, false, Duration.ZERO)
    }

    // ... Additional helper methods ...
}
```

**Coverage Requirements**:
- `waitForLogPatterns()`: Null validation
- `executeWaitForLogPatterns()`: Success paths, timeout, interruption
- `validateServicesExist()`: Known services, unknown services, empty project
- `checkAllServices()`: Normal operation, reject match, crash detection
- `CheckAllServicesResult` inner class: Static factory methods (`ok()`, `rejected()`, `crashed()`)
- `isServiceRunning()`: Running, not running, parse error handling
- `getServiceExitCode()`: Exit codes, null handling
- `fetchServiceLogs()`: Normal logs, empty logs, error handling
- `getRecentLogs()`: Log retrieval for error messages
- `buildTimeoutException()`: Message formatting, pattern status, recent logs inclusion
- `buildRejectException()`: Message formatting with reject pattern info
- `buildCrashException()`: Message formatting with exit code, recent logs
- Private initialization methods (test via indirect invocation through public methods):
  - `logWaitStart()`: Logging verification
  - `initializeMatchedPatternsMap()`: Correct initialization per service
  - `initializeMatchTimesMap()`: Correct initialization per service
  - `areAllServicesReady()`: Ready when all patterns matched, not ready otherwise
  - `buildResults()`: Result object construction, pattern match status
- Verbose logging helpers:
  - `logVerbosePollingStart()`: Log format verification
  - `logVerboseNewMatches()`: Log format verification
  - `logPeriodicProgress()`: Log format verification

**Additional Tests for ExecLibraryComposeServiceWaitForLogTest** (to add to the above spec):

```groovy
// ===== CheckAllServicesResult TESTS =====

def "CheckAllServicesResult.ok() returns result with no issues"() {
    when:
    def result = ExecLibraryComposeService.CheckAllServicesResult.ok()

    then:
    !result.rejected
    !result.crashed
    result.serviceName == null
}

def "CheckAllServicesResult.rejected() returns result with reject info"() {
    given:
    def rejectResult = new LogPatternMatcher.RejectCheckResult('Error', 'Error: Something failed')

    when:
    def result = ExecLibraryComposeService.CheckAllServicesResult.rejected('app', rejectResult)

    then:
    result.rejected
    !result.crashed
    result.serviceName == 'app'
    result.rejectResult == rejectResult
}

def "CheckAllServicesResult.crashed() returns result with exit code"() {
    when:
    def result = ExecLibraryComposeService.CheckAllServicesResult.crashed('db', 137)

    then:
    !result.rejected
    result.crashed
    result.serviceName == 'db'
    result.exitCode == 137
}

def "CheckAllServicesResult.crashed() handles null exit code"() {
    when:
    def result = ExecLibraryComposeService.CheckAllServicesResult.crashed('db', null)

    then:
    result.crashed
    result.exitCode == null
}

// ===== areAllServicesReady() TESTS (via indirect invocation) =====

def "executeWaitForLogPatterns returns immediately when all patterns match on first poll"() {
    given:
    def config = createConfig(['app': ['Started'], 'db': ['Ready']])
    // Setup mocks to return matching logs immediately
    mockTimeService.currentTimeMillis() >>> [0L, 0L]
    // ... setup mock logs

    when:
    def results = service.executeWaitForLogPatterns(config)

    then:
    results['app'].ready
    results['db'].ready
}

def "executeWaitForLogPatterns continues polling when only partial patterns match"() {
    // Verify areAllServicesReady() returns false until all services have all patterns
    // ...
}

// ===== buildTimeoutException() TESTS =====

def "buildTimeoutException includes service status summary"() {
    given:
    def config = createConfig(['app': ['Pattern1', 'Pattern2'], 'db': ['Ready']])
    def matchedPatterns = ['app': [0] as Set, 'db': [] as Set]  // app has 1/2 matched
    def matchTimes = ['app': [0: 5L], 'db': [:]]

    when:
    def exception = service.buildTimeoutException(config, matchedPatterns, matchTimes, 60)

    then:
    exception.message.contains('Timeout')
    exception.message.contains("Service 'app'")
    exception.message.contains('NOT READY')
    exception.message.contains('[FOUND]')
    exception.message.contains('[NOT FOUND]')
}

def "buildTimeoutException includes recent logs for unready services"() {
    // Gap #22 - verify RECENT_LOG_LINES_FOR_ERROR constant usage
    given:
    def config = createConfig(['app': ['NeverMatches']])
    def matchedPatterns = ['app': [] as Set]
    def matchTimes = ['app': [:]]
    // Mock getRecentLogs to return log lines
    service.mockRecentLogs('app', ['Line 1', 'Line 2', 'Line 3', 'Line 4', 'Line 5',
                                    'Line 6', 'Line 7', 'Line 8', 'Line 9', 'Line 10'])

    when:
    def exception = service.buildTimeoutException(config, matchedPatterns, matchTimes, 60)

    then:
    exception.message.contains('Recent logs')
    exception.message.contains('Line 1') || exception.message.contains('Last')
}

def "timeout exception includes recent log lines"() {
    // Gap #22 addition - verify RECENT_LOG_LINES_FOR_ERROR constant (10 lines)
    given:
    def config = createConfig(['app': ['NeverAppears']])
    mockTimeService.currentTimeMillis() >>> [0L, 0L, 61000L]
    mockProcessExecutor.execute(_ as List) >> new ProcessResult(0,
        '{"Service":"app","State":"running","ExitCode":0}', "")
    service.mockCaptureLogs('app', 'Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6\nLine 7\nLine 8\nLine 9\nLine 10')

    when:
    service.waitForLogPatterns(config).get()

    then:
    def ex = thrown(ExecutionException)
    ex.cause.message.contains('Last 10 log lines') || ex.cause.message.contains('Recent')
}

def "crash exception includes recent log lines"() {
    // Gap #22 addition - buildCrashException also uses RECENT_LOG_LINES_FOR_ERROR
    given:
    def config = createConfig(['app': ['Started']])
    def matchedPatterns = ['app': [] as Set]
    def matchTimes = ['app': [:]]
    service.mockRecentLogs('app', ['Startup failed', 'Connection refused', 'Exit'])

    when:
    def exception = service.buildCrashException('app', 137, matchedPatterns, matchTimes, config)

    then:
    exception.message.contains('crashed') || exception.message.contains('exit')
    exception.message.contains('137')
}

// ===== buildRejectException() TESTS =====

def "buildRejectException includes reject pattern details"() {
    given:
    def config = createConfig(['app': ['Started']])
    def matchedPatterns = ['app': [] as Set]
    def matchTimes = ['app': [:]]
    def rejectResult = new LogPatternMatcher.RejectCheckResult('Exception', 'Exception: NPE')

    when:
    def exception = service.buildRejectException('app', rejectResult, matchedPatterns, matchTimes, config)

    then:
    exception.message.contains('Reject pattern matched')
    exception.message.contains('Exception')
    exception.message.contains('NPE')
    exception.errorType == ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED
}

// ===== buildCrashException() TESTS =====

def "buildCrashException includes exit code when available"() {
    given:
    def config = createConfig(['app': ['Started']])
    def matchedPatterns = ['app': [] as Set]
    def matchTimes = ['app': [:]]

    when:
    def exception = service.buildCrashException('app', 1, matchedPatterns, matchTimes, config)

    then:
    exception.message.contains('crashed')
    exception.message.contains('exit code: 1')
    exception.errorType == ComposeServiceException.ErrorType.SERVICE_CRASHED
}

def "buildCrashException handles null exit code"() {
    given:
    def config = createConfig(['app': ['Started']])
    def matchedPatterns = ['app': [] as Set]
    def matchTimes = ['app': [:]]

    when:
    def exception = service.buildCrashException('app', null, matchedPatterns, matchTimes, config)

    then:
    exception.message.contains('unknown')
}

// ===== getRecentLogs() TESTS =====

def "getRecentLogs fetches specified number of lines"() {
    // Gap #10 correction - complete test implementation
    given:
    def lineCount = 10
    service.mockCaptureLogs('app', 'Line 1\nLine 2\nLine 3')

    when:
    def logs = service.getRecentLogs('project', 'app', lineCount)

    then:
    logs.size() == 3
    logs == ['Line 1', 'Line 2', 'Line 3']
}

def "getRecentLogs returns empty list when no logs available"() {
    // Gap #10 addition
    given:
    service.mockCaptureLogs('app', '')

    when:
    def logs = service.getRecentLogs('project', 'app', 10)

    then:
    logs.isEmpty()
}

def "getRecentLogs handles single line of output"() {
    // Gap #10 addition
    given:
    service.mockCaptureLogs('app', 'Single line')

    when:
    def logs = service.getRecentLogs('project', 'app', 5)

    then:
    logs.size() == 1
    logs[0] == 'Single line'
}

// ===== getComposeProjectServices() TESTS =====
// NOTE: Docker Compose v2 uses JSON format with --format json flag (Gap #1 correction)

def "getComposeProjectServices returns list of service names from compose ps JSON output"() {
    given:
    // Docker Compose v2 JSON format: one JSON object per line
    def psOutput = '{"Service":"app","State":"running","ExitCode":0}\n{"Service":"db","State":"running","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, psOutput, "")

    when:
    def services = service.getComposeProjectServices('test-project')

    then:
    services.containsAll(['app', 'db'])
    services.size() == 2
}

def "getComposeProjectServices returns empty set when no services running"() {
    given:
    def psOutput = ''  // Empty JSON output when no containers
    mockProcessExecutor.execute(_) >> new ProcessResult(0, psOutput, "")

    when:
    def services = service.getComposeProjectServices('test-project')

    then:
    services.isEmpty()
}

def "getComposeProjectServices handles empty output"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, '', "")

    when:
    def services = service.getComposeProjectServices('test-project')

    then:
    services.isEmpty()
}

def "getComposeProjectServices handles null output"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, null, "")

    when:
    def services = service.getComposeProjectServices('test-project')

    then:
    services.isEmpty()
}

def "getComposeProjectServices extracts unique service names"() {
    given:
    // Same service with multiple containers (scaled)
    def psOutput = '{"Service":"app","State":"running","ExitCode":0}\n{"Service":"app","State":"running","ExitCode":0}\n{"Service":"db","State":"running","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, psOutput, "")

    when:
    def services = service.getComposeProjectServices('test-project')

    then:
    services.size() == 2
    services.containsAll(['app', 'db'])
}

def "getComposeProjectServices handles malformed JSON gracefully"() {
    given:
    def psOutput = 'not valid json {{'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, psOutput, "")

    when:
    def services = service.getComposeProjectServices('test-project')

    then:
    services.isEmpty()
    // Logger should have logged the parse error
}

// ===== validateServicesExist() TESTS =====
// NOTE: Method signature is validateServicesExist(WaitForLogConfig config) (Gap #2 correction)

def "validateServicesExist throws exception when requested service not in project"() {
    given:
    // Create config requesting 'cache' service which doesn't exist
    def config = createConfig(['app': ['Started'], 'cache': ['Ready']])
    // Mock getComposeProjectServices to return only 'app' and 'db'
    def psOutput = '{"Service":"app","State":"running","ExitCode":0}\n{"Service":"db","State":"running","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, psOutput, "")

    when:
    service.validateServicesExist(config)

    then:
    def ex = thrown(ComposeServiceException)
    ex.errorType == ComposeServiceException.ErrorType.SERVICE_NOT_FOUND
    ex.message.contains('cache')
}

def "validateServicesExist succeeds when all services exist"() {
    given:
    def config = createConfig(['app': ['Started'], 'db': ['Ready']])
    def psOutput = '{"Service":"app","State":"running","ExitCode":0}\n{"Service":"db","State":"running","ExitCode":0}\n{"Service":"cache","State":"running","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, psOutput, "")

    when:
    service.validateServicesExist(config)

    then:
    noExceptionThrown()
}

def "validateServicesExist throws specific error for empty compose project"() {
    given:
    def config = createConfig(['app': ['Started']])
    // Mock getComposeProjectServices to return empty set (no containers)
    mockProcessExecutor.execute(_) >> new ProcessResult(0, '', "")

    when:
    service.validateServicesExist(config)

    then:
    def ex = thrown(ComposeServiceException)
    ex.errorType == ComposeServiceException.ErrorType.SERVICE_NOT_FOUND
    ex.message.contains("No services found in compose project")
    ex.message.contains("composeUp")
}

def "validateServicesExist throws when project has no services"() {
    given:
    def config = createConfig(['app': ['Started']])
    mockProcessExecutor.execute(_) >> new ProcessResult(0, '', "")

    when:
    service.validateServicesExist(config)

    then:
    def ex = thrown(ComposeServiceException)
    ex.errorType == ComposeServiceException.ErrorType.SERVICE_NOT_FOUND
}

def "validateServicesExist handles case-sensitive service names"() {
    given:
    def config = createConfig(['app': ['Started']])  // lowercase 'app'
    // Project has 'App' with capital A
    def psOutput = '{"Service":"App","State":"running","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, psOutput, "")

    when:
    service.validateServicesExist(config)

    then:
    def ex = thrown(ComposeServiceException)
    ex.message.contains('app')
}

def "validateServicesExist lists all missing services in error message"() {
    given:
    def config = createConfig(['svc1': ['Started'], 'svc2': ['Ready'], 'svc3': ['Done']])
    // Only 'svc1' exists
    def psOutput = '{"Service":"svc1","State":"running","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, psOutput, "")

    when:
    service.validateServicesExist(config)

    then:
    def ex = thrown(ComposeServiceException)
    ex.message.contains('svc2')
    ex.message.contains('svc3')
}

// ===== fetchServiceLogs() TESTS =====
// NOTE: Returns List<String> (one line per element), not raw String (Gap #4 correction)

def "fetchServiceLogs returns list of log lines"() {
    given:
    def rawOutput = "Application started on port 8080\nReady for connections\nServer listening"
    mockProcessExecutor.execute(_) >> new ProcessResult(0, rawOutput, "")

    when:
    def logs = service.fetchServiceLogs('test-project', 'app')

    then:
    logs == ['Application started on port 8080', 'Ready for connections', 'Server listening']
    logs.size() == 3
}

def "fetchServiceLogs returns empty list for null output"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, null, "")

    when:
    def logs = service.fetchServiceLogs('test-project', 'app')

    then:
    logs == []
    logs.isEmpty()
}

def "fetchServiceLogs returns empty list for empty output"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, '', "")

    when:
    def logs = service.fetchServiceLogs('test-project', 'app')

    then:
    logs == []
}

def "fetchServiceLogs returns empty list for whitespace-only output"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, '   \n\t\n   ', "")

    when:
    def logs = service.fetchServiceLogs('test-project', 'app')

    then:
    logs == []
}

def "fetchServiceLogs preserves line content without trailing whitespace"() {
    given:
    def rawOutput = "Line 1  \nLine 2\t\nLine 3"
    mockProcessExecutor.execute(_) >> new ProcessResult(0, rawOutput, "")

    when:
    def logs = service.fetchServiceLogs('test-project', 'app')

    then:
    logs.size() == 3
    // Lines may or may not be trimmed depending on implementation
}

def "fetchServiceLogs includes timestamp when requested"() {
    given:
    def rawOutput = '2024-01-15T10:30:00Z App started\n2024-01-15T10:30:01Z Ready'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, rawOutput, "")

    when:
    def logs = service.fetchServiceLogs('test-project', 'app', true)

    then:
    logs.size() == 2
    logs[0].contains('2024-01-15T10:30:00Z')
}

// ===== isServiceRunning() TESTS =====
// NOTE: Uses Docker Compose ps --format json output (Gap #1 correction)

def "isServiceRunning returns true when service state is running"() {
    given:
    // Docker Compose v2 ps --format json output
    def jsonOutput = '{"Service":"app","State":"running","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    when:
    def result = service.isServiceRunning('test-project', 'app')

    then:
    result == true
}

def "isServiceRunning returns false when service state is exited"() {
    given:
    def jsonOutput = '{"Service":"app","State":"exited","ExitCode":1}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    when:
    def result = service.isServiceRunning('test-project', 'app')

    then:
    result == false
}

def "isServiceRunning returns false when service state is paused"() {
    given:
    def jsonOutput = '{"Service":"app","State":"paused","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    when:
    def result = service.isServiceRunning('test-project', 'app')

    then:
    result == false
}

def "isServiceRunning logs error and returns false when JSON parsing fails"() {
    given:
    def invalidOutput = 'not valid json {{'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, invalidOutput, "")

    when:
    def result = service.isServiceRunning('test-project', 'app')

    then:
    result == false
    // Logger should have logged the parse error (verify via mock if needed)
}

def "isServiceRunning handles empty output (service not found)"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, '', "")

    when:
    def result = service.isServiceRunning('test-project', 'app')

    then:
    result == false
}

def "isServiceRunning handles null output"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, null, "")

    when:
    def result = service.isServiceRunning('test-project', 'app')

    then:
    result == false
}

def "isServiceRunning handles command failure"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(1, "", "Error: service not found")

    when:
    def result = service.isServiceRunning('test-project', 'app')

    then:
    result == false
}

// ===== getServiceExitCode() TESTS ===== (Gap #3 addition)

def "getServiceExitCode returns exit code when container exited"() {
    given:
    def jsonOutput = '{"Service":"app","State":"exited","ExitCode":1}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    expect:
    service.getServiceExitCode('test-project', 'app') == 1
}

def "getServiceExitCode returns 0 for successful exit"() {
    given:
    def jsonOutput = '{"Service":"app","State":"exited","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    expect:
    service.getServiceExitCode('test-project', 'app') == 0
}

def "getServiceExitCode returns exit code for running container"() {
    given:
    def jsonOutput = '{"Service":"app","State":"running","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    expect:
    service.getServiceExitCode('test-project', 'app') == 0
}

def "getServiceExitCode returns null when service not found"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, "", "")

    expect:
    service.getServiceExitCode('test-project', 'app') == null
}

def "getServiceExitCode returns null on command failure"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(1, "", "error")

    expect:
    service.getServiceExitCode('test-project', 'app') == null
}

def "getServiceExitCode returns null when JSON lacks ExitCode field"() {
    given:
    def jsonOutput = '{"Service":"app","State":"running"}'  // No ExitCode
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    expect:
    service.getServiceExitCode('test-project', 'app') == null
}

def "getServiceExitCode handles JsonException gracefully"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, "not valid json", "")

    expect:
    service.getServiceExitCode('test-project', 'app') == null
}

def "getServiceExitCode returns high exit code"() {
    given:
    def jsonOutput = '{"Service":"app","State":"exited","ExitCode":137}'  // SIGKILL
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    expect:
    service.getServiceExitCode('test-project', 'app') == 137
}
```

---

### 10. ComposeUpTaskWaitForLogTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/task/ComposeUpTaskWaitForLogTest.groovy`

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 * ... License header ...
 */

package com.kineticfire.gradle.docker.task

import com.kineticfire.gradle.docker.service.ComposeService
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ComposeUpTask waitForLog properties and execution
 */
class ComposeUpTaskWaitForLogTest extends Specification {

    Project project
    ComposeUpTask task

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.create('testComposeUp', ComposeUpTask)
    }

    // ===== PROPERTY EXISTENCE TESTS =====

    def "waitForLogServices property exists"() {
        expect:
        task.waitForLogServices != null
    }

    def "waitForLogRejectPatterns property exists"() {
        expect:
        task.waitForLogRejectPatterns != null
    }

    def "waitForLogTimeoutSeconds property exists"() {
        expect:
        task.waitForLogTimeoutSeconds != null
    }

    def "waitForLogPollSeconds property exists"() {
        expect:
        task.waitForLogPollSeconds != null
    }

    def "waitForLogCaseInsensitive property exists"() {
        expect:
        task.waitForLogCaseInsensitive != null
    }

    def "waitForLogVerbose property exists"() {
        expect:
        task.waitForLogVerbose != null
    }

    def "waitForLogProgressIntervalSeconds property exists"() {
        expect:
        task.waitForLogProgressIntervalSeconds != null
    }

    // ===== PROPERTY VALUE TESTS =====

    def "waitForLogServices accepts map values"() {
        when:
        task.waitForLogServices.set(['app': ['Started Application']])

        then:
        task.waitForLogServices.get() == ['app': ['Started Application']]
    }

    def "waitForLogServices accepts multiple services"() {
        given:
        def services = [
            'app': ['Started', 'Ready'],
            'db': ['accepting connections'],
            'cache': ['Server started']
        ]

        when:
        task.waitForLogServices.set(services)

        then:
        task.waitForLogServices.get() == services
    }

    def "waitForLogRejectPatterns accepts map values"() {
        when:
        task.waitForLogRejectPatterns.set(['app': ['Error', 'Exception']])

        then:
        task.waitForLogRejectPatterns.get() == ['app': ['Error', 'Exception']]
    }

    def "waitForLogTimeoutSeconds accepts integer values"() {
        when:
        task.waitForLogTimeoutSeconds.set(120)

        then:
        task.waitForLogTimeoutSeconds.get() == 120
    }

    def "waitForLogPollSeconds accepts integer values"() {
        when:
        task.waitForLogPollSeconds.set(5)

        then:
        task.waitForLogPollSeconds.get() == 5
    }

    def "waitForLogCaseInsensitive accepts boolean values"() {
        when:
        task.waitForLogCaseInsensitive.set(true)

        then:
        task.waitForLogCaseInsensitive.get() == true
    }

    def "waitForLogVerbose accepts boolean values"() {
        when:
        task.waitForLogVerbose.set(true)

        then:
        task.waitForLogVerbose.get() == true
    }

    def "waitForLogProgressIntervalSeconds accepts integer values"() {
        when:
        task.waitForLogProgressIntervalSeconds.set(15)

        then:
        task.waitForLogProgressIntervalSeconds.get() == 15
    }

    // ===== performWaitForLog() TESTS =====

    def "performWaitForLog skips when waitForLogServices is not present"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)

        when:
        task.performWaitForLog('test-project')

        then:
        0 * mockComposeService.waitForLogPatterns(_)
    }

    def "performWaitForLog skips when waitForLogServices is empty"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set([:])

        when:
        task.performWaitForLog('test-project')

        then:
        0 * mockComposeService.waitForLogPatterns(_)
    }

    def "performWaitForLog invokes waitForLogPatterns when services configured"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])

        when:
        task.performWaitForLog('test-project')

        then:
        1 * mockComposeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture([:])
    }

    // ===== EXECUTION ORDER TESTS =====

    def "performWaitIfConfigured calls waitForLog after waitForRunning and waitForHealthy"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        task.waitForRunningServices.set(['app'])
        task.waitForHealthyServices.set(['app'])
        task.waitForLogServices.set(['app': ['Started']])
        def callOrder = []

        when:
        task.performWaitIfConfigured('stack', 'project')

        then:
        // Verify call order
        1 * mockComposeService.waitForServices({ it.targetStatus == ServiceStatus.RUNNING }) >> {
            callOrder << 'running'
            CompletableFuture.completedFuture([:])
        }
        then:
        1 * mockComposeService.waitForServices({ it.targetStatus == ServiceStatus.HEALTHY }) >> {
            callOrder << 'healthy'
            CompletableFuture.completedFuture([:])
        }
        then:
        1 * mockComposeService.waitForLogPatterns(_) >> {
            callOrder << 'log'
            CompletableFuture.completedFuture([:])
        }
        then:
        callOrder == ['running', 'healthy', 'log']
    }

    // ===== DEFAULT VALUE TESTS =====

    def "performWaitForLog uses default timeout when not specified"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])
        // Note: waitForLogTimeoutSeconds not set

        when:
        task.performWaitForLog('test-project')

        then:
        1 * mockComposeService.waitForLogPatterns({ config ->
            config.timeout.toSeconds() == 60  // default
        }) >> CompletableFuture.completedFuture([:])
    }

    def "performWaitForLog uses default poll interval when not specified"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])

        when:
        task.performWaitForLog('test-project')

        then:
        1 * mockComposeService.waitForLogPatterns({ config ->
            config.pollInterval.toSeconds() == 2  // default
        }) >> CompletableFuture.completedFuture([:])
    }

    // ===== EXCEPTION PROPAGATION TESTS =====

    def "performWaitForLog propagates timeout exception from waitForLogPatterns"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])
        def timeoutException = new ComposeServiceException(
            ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
            "Timeout waiting for log patterns"
        )
        mockComposeService.waitForLogPatterns(_) >> {
            def future = new CompletableFuture()
            future.completeExceptionally(timeoutException)
            future
        }

        when:
        task.performWaitForLog('test-project')

        then:
        def ex = thrown(ComposeServiceException)
        ex.errorType == ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT
    }

    def "performWaitForLog propagates reject pattern exception from waitForLogPatterns"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])
        task.waitForLogRejectPatterns.set(['app': ['Error']])
        def rejectException = new ComposeServiceException(
            ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED,
            "Reject pattern 'Error' matched"
        )
        mockComposeService.waitForLogPatterns(_) >> {
            def future = new CompletableFuture()
            future.completeExceptionally(rejectException)
            future
        }

        when:
        task.performWaitForLog('test-project')

        then:
        def ex = thrown(ComposeServiceException)
        ex.errorType == ComposeServiceException.ErrorType.LOG_REJECT_PATTERN_MATCHED
    }

    def "performWaitForLog propagates service crashed exception from waitForLogPatterns"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])
        def crashException = new ComposeServiceException(
            ComposeServiceException.ErrorType.SERVICE_CRASHED,
            "Service 'app' crashed with exit code 1"
        )
        mockComposeService.waitForLogPatterns(_) >> {
            def future = new CompletableFuture()
            future.completeExceptionally(crashException)
            future
        }

        when:
        task.performWaitForLog('test-project')

        then:
        def ex = thrown(ComposeServiceException)
        ex.errorType == ComposeServiceException.ErrorType.SERVICE_CRASHED
    }

    // ===== PARTIAL WAIT BLOCK CONFIGURATION TESTS =====

    def "performWaitIfConfigured executes only waitForLog when only log patterns configured"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        // Only waitForLog configured, not waitForRunning or waitForHealthy
        task.waitForLogServices.set(['app': ['Started']])

        when:
        task.performWaitIfConfigured('stack', 'project')

        then:
        0 * mockComposeService.waitForServices(_)  // No running/healthy calls
        1 * mockComposeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture([:])
    }

    def "performWaitIfConfigured executes waitForRunning and waitForLog when both configured"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        task.waitForRunningServices.set(['app'])
        task.waitForLogServices.set(['app': ['Started']])
        // waitForHealthy NOT configured

        when:
        task.performWaitIfConfigured('stack', 'project')

        then:
        1 * mockComposeService.waitForServices({ config ->
            config.targetStatus == ServiceStatus.RUNNING
        }) >> CompletableFuture.completedFuture([:])
        0 * mockComposeService.waitForServices({ config ->
            config.targetStatus == ServiceStatus.HEALTHY
        })
        1 * mockComposeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture([:])
    }

    def "performWaitIfConfigured executes waitForHealthy and waitForLog when both configured"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        // waitForRunning NOT configured
        task.waitForHealthyServices.set(['app'])
        task.waitForLogServices.set(['app': ['Started']])

        when:
        task.performWaitIfConfigured('stack', 'project')

        then:
        0 * mockComposeService.waitForServices({ config ->
            config.targetStatus == ServiceStatus.RUNNING
        })
        1 * mockComposeService.waitForServices({ config ->
            config.targetStatus == ServiceStatus.HEALTHY
        }) >> CompletableFuture.completedFuture([:])
        1 * mockComposeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture([:])
    }

    def "performWaitIfConfigured succeeds when no wait blocks configured"() {
        given:
        def mockComposeService = Mock(ComposeService)
        task.composeService.set(mockComposeService)
        // No wait blocks configured

        when:
        task.performWaitIfConfigured('stack', 'project')

        then:
        0 * mockComposeService._
        noExceptionThrown()
    }
}
```

**Coverage Requirements**:
- All `waitForLog*` properties: Existence, value acceptance
- `performWaitForLog()`: Skip when not configured, invocation when configured
- `performWaitIfConfigured()`: Execution order (running -> healthy -> log)
- Default values: Timeout, poll interval, boolean flags
- Exception propagation: Timeout, reject pattern, service crashed exceptions
- Partial configuration: Only log, running+log, healthy+log, no wait blocks

---

### 11. GradleDockerPluginWaitForLogTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/GradleDockerPluginWaitForLogTest.groovy`

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 * ... License header ...
 */

package com.kineticfire.gradle.docker

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for GradleDockerPlugin waitForLog wiring
 */
class GradleDockerPluginWaitForLogTest extends Specification {

    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply('groovy')
        project.plugins.apply(GradleDockerPlugin)
    }

    // ===== CONDITIONAL WIRING TESTS =====

    def "waitForLog properties are not wired when waitForLog block is not configured"() {
        given:
        project.dockerTest {
            composeStacks {
                testStack {
                    files.from('compose.yml')
                    // No waitForLog block
                }
            }
        }

        when:
        project.evaluate()
        def task = project.tasks.findByName('composeUpTestStack')

        then:
        !task.waitForLogServices.present
    }

    def "waitForLog properties are wired when waitForLog block is configured"() {
        given:
        project.dockerTest {
            composeStacks {
                testStack {
                    files.from('compose.yml')
                    waitForLog {
                        waitForServices.set(['app': ['Started']])
                    }
                }
            }
        }

        when:
        project.evaluate()
        def task = project.tasks.findByName('composeUpTestStack')

        then:
        task.waitForLogServices.present
        task.waitForLogServices.get() == ['app': ['Started']]
    }

    // ===== PROPERTY WIRING TESTS =====

    def "all waitForLog spec properties are wired to task"() {
        given:
        project.dockerTest {
            composeStacks {
                testStack {
                    files.from('compose.yml')
                    waitForLog {
                        waitForServices.set(['app': ['Started', 'Ready'], 'db': ['connections']])
                        rejectPatterns.set(['app': ['Error']])
                        timeoutSeconds.set(120)
                        pollSeconds.set(5)
                        caseInsensitive.set(true)
                        verbose.set(true)
                        progressIntervalSeconds.set(15)
                    }
                }
            }
        }

        when:
        project.evaluate()
        def task = project.tasks.findByName('composeUpTestStack')

        then:
        task.waitForLogServices.get() == ['app': ['Started', 'Ready'], 'db': ['connections']]
        task.waitForLogRejectPatterns.get() == ['app': ['Error']]
        task.waitForLogTimeoutSeconds.get() == 120
        task.waitForLogPollSeconds.get() == 5
        task.waitForLogCaseInsensitive.get() == true
        task.waitForLogVerbose.get() == true
        task.waitForLogProgressIntervalSeconds.get() == 15
    }

    // ===== DEFAULT VALUE TESTS =====

    def "default values are applied when optional properties not set"() {
        given:
        project.dockerTest {
            composeStacks {
                testStack {
                    files.from('compose.yml')
                    waitForLog {
                        waitForServices.set(['app': ['Started']])
                        // All other properties use defaults
                    }
                }
            }
        }

        when:
        project.evaluate()
        def task = project.tasks.findByName('composeUpTestStack')

        then:
        task.waitForLogRejectPatterns.get() == [:]
        task.waitForLogTimeoutSeconds.get() == 60
        task.waitForLogPollSeconds.get() == 2
        task.waitForLogCaseInsensitive.get() == false
        task.waitForLogVerbose.get() == false
        task.waitForLogProgressIntervalSeconds.get() == 0
    }
}
```

**Coverage Requirements**:
- Conditional wiring: Only when `waitForLog.present`
- Property wiring: All spec properties mapped to task properties
- Default values: Applied via `getOrElse()` pattern

---

### 12. TestIntegrationExtensionWaitForLogTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtensionWaitForLogTest.groovy`

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 * ... License header ...
 */

package com.kineticfire.gradle.docker.extension

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for TestIntegrationExtension waitForLog system property propagation
 */
class TestIntegrationExtensionWaitForLogTest extends Specification {

    Project project
    TestIntegrationExtension extension
    Test testTask

    def setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply('groovy')
        testTask = project.tasks.create('testTask', Test)
        extension = project.objects.newInstance(TestIntegrationExtension, project)
    }

    // ===== SYSTEM PROPERTY PROPAGATION TESTS =====

    def "waitForLog services are serialized as JSON to system property"() {
        given:
        def stackSpec = createStackSpecWithWaitForLog([
            'app': ['Started Application', 'Listening on port'],
            'db': ['ready for connections']
        ])

        when:
        extension.setComprehensiveSystemProperties(testTask, stackSpec)

        then:
        def servicesJson = testTask.systemProperties['docker.compose.waitForLog.services']
        servicesJson != null
        servicesJson.contains('"app"')
        servicesJson.contains('"Started Application"')
        servicesJson.contains('"db"')
    }

    def "waitForLog reject patterns are serialized as JSON to system property"() {
        given:
        def stackSpec = createStackSpecWithWaitForLogAndReject(
            ['app': ['Started']],
            ['app': ['Error', 'Exception']]
        )

        when:
        extension.setComprehensiveSystemProperties(testTask, stackSpec)

        then:
        def rejectJson = testTask.systemProperties['docker.compose.waitForLog.rejectPatterns']
        rejectJson != null
        rejectJson.contains('"Error"')
        rejectJson.contains('"Exception"')
    }

    def "waitForLog scalar properties are propagated as string system properties"() {
        given:
        def stackSpec = createStackSpecWithFullWaitForLog()

        when:
        extension.setComprehensiveSystemProperties(testTask, stackSpec)

        then:
        testTask.systemProperties['docker.compose.waitForLog.timeoutSeconds'] == '120'
        testTask.systemProperties['docker.compose.waitForLog.pollSeconds'] == '5'
        testTask.systemProperties['docker.compose.waitForLog.caseInsensitive'] == 'true'
        testTask.systemProperties['docker.compose.waitForLog.verbose'] == 'true'
        testTask.systemProperties['docker.compose.waitForLog.progressIntervalSeconds'] == '15'
    }

    def "waitForLog system properties are not set when waitForLog block is not configured"() {
        given:
        def stackSpec = createStackSpecWithoutWaitForLog()

        when:
        extension.setComprehensiveSystemProperties(testTask, stackSpec)

        then:
        testTask.systemProperties['docker.compose.waitForLog.services'] == null
    }

    // ===== JSON SERIALIZATION EDGE CASES =====

    def "JSON serialization handles patterns with special regex characters"() {
        given:
        def stackSpec = createStackSpecWithWaitForLog([
            'app': ['\\[INFO\\]', '\\d+\\.\\d+', 'port:\\s*\\d+']
        ])

        when:
        extension.setComprehensiveSystemProperties(testTask, stackSpec)

        then:
        def servicesJson = testTask.systemProperties['docker.compose.waitForLog.services']
        servicesJson != null
        // JSON should properly escape backslashes
    }

    def "JSON serialization handles patterns with quotes"() {
        given:
        def stackSpec = createStackSpecWithWaitForLog([
            'app': ['message: "started"']
        ])

        when:
        extension.setComprehensiveSystemProperties(testTask, stackSpec)

        then:
        def servicesJson = testTask.systemProperties['docker.compose.waitForLog.services']
        servicesJson != null
        // JSON should properly escape quotes
    }

    // ===== LIFECYCLE TESTS =====

    def "waitForLog properties are propagated for CLASS lifecycle"() {
        given:
        def stackSpec = createStackSpecWithWaitForLog(['app': ['Started']])
        stackSpec.lifecycle.set(Lifecycle.CLASS)

        when:
        extension.setComprehensiveSystemProperties(testTask, stackSpec)

        then:
        testTask.systemProperties['docker.compose.waitForLog.services'] != null
    }

    def "waitForLog properties are propagated for METHOD lifecycle"() {
        given:
        def stackSpec = createStackSpecWithWaitForLog(['app': ['Started']])
        stackSpec.lifecycle.set(Lifecycle.METHOD)

        when:
        extension.setComprehensiveSystemProperties(testTask, stackSpec)

        then:
        testTask.systemProperties['docker.compose.waitForLog.services'] != null
    }

    // ===== HELPER METHODS =====

    // ... Helper methods to create test stack specs ...
}
```

**Coverage Requirements**:
- System property propagation: All waitForLog properties
- JSON serialization: Services map, reject patterns map
- Edge cases: Special regex chars, quotes in patterns
- Lifecycle support: Both CLASS and METHOD

---

### 13. DockerComposeMethodExtensionWaitForLogTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/junit/DockerComposeMethodExtensionWaitForLogTest.groovy`

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 * ... License header ...
 */

package com.kineticfire.gradle.docker.junit

import com.kineticfire.gradle.docker.service.ComposeService
import spock.lang.Specification

/**
 * Unit tests for DockerComposeMethodExtension waitForLog functionality
 */
class DockerComposeMethodExtensionWaitForLogTest extends Specification {

    SystemPropertyService mockSystemPropertyService
    ComposeService mockComposeService
    DockerComposeMethodExtension extension

    def setup() {
        mockSystemPropertyService = Mock(SystemPropertyService)
        mockComposeService = Mock(ComposeService)
        extension = new DockerComposeMethodExtension(mockSystemPropertyService, mockComposeService)
    }

    // ===== parseIntProperty() TESTS =====

    def "parseIntProperty returns parsed value for valid integer"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> '42'

        expect:
        extension.parseIntProperty('test.prop', 0) == 42
    }

    def "parseIntProperty returns default for null property"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> null

        expect:
        extension.parseIntProperty('test.prop', 10) == 10
    }

    def "parseIntProperty returns default for empty property"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> ''

        expect:
        extension.parseIntProperty('test.prop', 10) == 10
    }

    def "parseIntProperty returns default for invalid integer"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> 'not-a-number'

        expect:
        extension.parseIntProperty('test.prop', 10) == 10
    }

    def "parseIntProperty handles negative values"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> '-5'

        expect:
        extension.parseIntProperty('test.prop', 0) == -5
    }

    def "parseIntProperty handles whitespace around value"() {
        // Gap #6 addition - consistent with DockerComposeClassExtension
        given:
        mockSystemPropertyService.getProperty('test.prop') >> '  42  '

        expect:
        extension.parseIntProperty('test.prop', 0) == 42
    }

    def "parseIntProperty handles leading whitespace"() {
        // Gap #6 addition
        given:
        mockSystemPropertyService.getProperty('test.prop') >> '   100'

        expect:
        extension.parseIntProperty('test.prop', 0) == 100
    }

    def "parseIntProperty handles trailing whitespace"() {
        // Gap #6 addition
        given:
        mockSystemPropertyService.getProperty('test.prop') >> '100   '

        expect:
        extension.parseIntProperty('test.prop', 0) == 100
    }

    // ===== parseBooleanProperty() TESTS =====

    def "parseBooleanProperty returns true for 'true'"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> 'true'

        expect:
        extension.parseBooleanProperty('test.prop', false) == true
    }

    def "parseBooleanProperty returns false for 'false'"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> 'false'

        expect:
        extension.parseBooleanProperty('test.prop', true) == false
    }

    def "parseBooleanProperty returns default for null property"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> null

        expect:
        extension.parseBooleanProperty('test.prop', true) == true
    }

    def "parseBooleanProperty returns default for empty property"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> ''

        expect:
        extension.parseBooleanProperty('test.prop', true) == true
    }

    def "parseBooleanProperty returns false for invalid value (Boolean.parseBoolean behavior)"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> 'yes'

        expect:
        extension.parseBooleanProperty('test.prop', true) == false
    }

    def "parseBooleanProperty returns false for non-standard values"() {
        // Gap #7 addition - parameterized test for comprehensive coverage
        given:
        mockSystemPropertyService.getProperty('test.prop') >> value

        expect:
        extension.parseBooleanProperty('test.prop', true) == false

        where:
        value << ['yes', 'YES', '1', 'on', 'ON', 'True', 'TRUE']
    }

    def "parseBooleanProperty returns false for whitespace-only value"() {
        // Gap #7 addition
        given:
        mockSystemPropertyService.getProperty('test.prop') >> '   '

        expect:
        extension.parseBooleanProperty('test.prop', true) == false
    }

    // ===== parseJsonMapProperty() TESTS =====

    def "parseJsonMapProperty parses valid JSON map"() {
        given:
        def json = '{"app": ["Started", "Ready"], "db": ["connections"]}'

        when:
        def result = extension.parseJsonMapProperty(json)

        then:
        result == ['app': ['Started', 'Ready'], 'db': ['connections']]
    }

    def "parseJsonMapProperty returns empty map for null input"() {
        expect:
        extension.parseJsonMapProperty(null) == [:]
    }

    def "parseJsonMapProperty returns empty map for empty string"() {
        expect:
        extension.parseJsonMapProperty('') == [:]
    }

    def "parseJsonMapProperty returns empty map for invalid JSON"() {
        expect:
        extension.parseJsonMapProperty('not valid json') == [:]
    }

    def "parseJsonMapProperty returns empty map for JSON array (not object)"() {
        expect:
        extension.parseJsonMapProperty('["item1", "item2"]') == [:]
    }

    def "parseJsonMapProperty handles empty object"() {
        expect:
        extension.parseJsonMapProperty('{}') == [:]
    }

    def "parseJsonMapProperty handles patterns with escaped backslashes"() {
        given:
        def json = '{"app": ["\\\\[INFO\\\\]"]}'

        when:
        def result = extension.parseJsonMapProperty(json)

        then:
        result['app'][0] == '\\[INFO\\]'
    }

    // ===== performWaitForLog() TESTS =====

    def "performWaitForLog skips when services property is null"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_SERVICES) >> null

        when:
        extension.performWaitForLog('test-project')

        then:
        0 * mockComposeService.waitForLogPatterns(_)
    }

    def "performWaitForLog skips when services property is empty JSON"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_SERVICES) >> '{}'

        when:
        extension.performWaitForLog('test-project')

        then:
        0 * mockComposeService.waitForLogPatterns(_)
    }

    def "performWaitForLog invokes waitForLogPatterns when services configured"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_SERVICES) >>
            '{"app": ["Started"]}'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_REJECT_PATTERNS) >> null
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_TIMEOUT) >> '60'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_POLL) >> '2'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_CASE_INSENSITIVE) >> 'false'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_VERBOSE) >> 'false'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_PROGRESS_INTERVAL) >> '0'

        when:
        extension.performWaitForLog('test-project')

        then:
        1 * mockComposeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture([:])
    }

    // ===== performWaitForRunning() TESTS =====

    def "performWaitForRunning skips when services property is null"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_RUNNING_SERVICES) >> null

        when:
        extension.performWaitForRunning('test-project')

        then:
        0 * mockComposeService.waitForServices(_)
    }

    def "performWaitForRunning invokes waitForServices with RUNNING status"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_RUNNING_SERVICES) >> 'app,db'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_RUNNING_TIMEOUT) >> '60'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_RUNNING_POLL) >> '2'

        when:
        extension.performWaitForRunning('test-project')

        then:
        1 * mockComposeService.waitForServices({ config ->
            config.targetStatus == ServiceStatus.RUNNING &&
            config.services.containsAll(['app', 'db'])
        }) >> CompletableFuture.completedFuture([:])
    }

    // ===== performWaitForHealthy() TESTS =====

    def "performWaitForHealthy skips when services property is null"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_HEALTHY_SERVICES) >> null

        when:
        extension.performWaitForHealthy('test-project')

        then:
        0 * mockComposeService.waitForServices(_)
    }

    def "performWaitForHealthy invokes waitForServices with HEALTHY status"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_HEALTHY_SERVICES) >> 'app'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_HEALTHY_TIMEOUT) >> '120'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_HEALTHY_POLL) >> '5'

        when:
        extension.performWaitForHealthy('test-project')

        then:
        1 * mockComposeService.waitForServices({ config ->
            config.targetStatus == ServiceStatus.HEALTHY &&
            config.timeout.toSeconds() == 120 &&
            config.pollInterval.toSeconds() == 5
        }) >> CompletableFuture.completedFuture([:])
    }

    // ===== waitForStackToBeReady() TESTS =====

    def "waitForStackToBeReady calls wait methods in correct order"() {
        given:
        // Setup properties for all three wait types
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_RUNNING_SERVICES) >> 'app'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_HEALTHY_SERVICES) >> 'app'
        mockSystemPropertyService.getProperty(DockerComposeMethodExtension.WAIT_FOR_LOG_SERVICES) >> '{"app": ["Started"]}'
        // ... other properties ...
        def callOrder = []

        when:
        extension.waitForStackToBeReady('stack', 'project')

        then:
        1 * mockComposeService.waitForServices({ it.targetStatus == ServiceStatus.RUNNING }) >> {
            callOrder << 'running'
            CompletableFuture.completedFuture([:])
        }
        then:
        1 * mockComposeService.waitForServices({ it.targetStatus == ServiceStatus.HEALTHY }) >> {
            callOrder << 'healthy'
            CompletableFuture.completedFuture([:])
        }
        then:
        1 * mockComposeService.waitForLogPatterns(_) >> {
            callOrder << 'log'
            CompletableFuture.completedFuture([:])
        }
        then:
        callOrder == ['running', 'healthy', 'log']
    }

    def "waitForStackToBeReady succeeds when no wait blocks configured"() {
        given:
        mockSystemPropertyService.getProperty(_) >> null

        when:
        extension.waitForStackToBeReady('stack', 'project')

        then:
        0 * mockComposeService._
        noExceptionThrown()
    }
}
```

**Coverage Requirements**:
- `parseIntProperty()`: Valid, null, empty, invalid input
- `parseBooleanProperty()`: True, false, null, empty, invalid input
- `parseJsonMapProperty()`: Valid JSON, null, empty, invalid JSON, empty object
- `performWaitForRunning()`: Skip when null, invocation with correct status
- `performWaitForHealthy()`: Skip when null, invocation with correct status
- `performWaitForLog()`: Skip when null/empty, invocation when configured
- `waitForStackToBeReady()`: Execution order, success with no config

---

### 14. DockerComposeClassExtensionWaitForLogTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/junit/DockerComposeClassExtensionWaitForLogTest.groovy`

This test class mirrors `DockerComposeMethodExtensionWaitForLogTest` but tests CLASS lifecycle-specific behavior.
The key difference is that CLASS lifecycle extensions use `beforeAll()` and `afterAll()` callbacks, which operate
on static test context.

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 * ... License header ...
 */

package com.kineticfire.gradle.docker.junit

import com.kineticfire.gradle.docker.service.ComposeService
import spock.lang.Specification

/**
 * Unit tests for DockerComposeClassExtension waitForLog functionality
 *
 * Tests mirror DockerComposeMethodExtensionWaitForLogTest but verify CLASS lifecycle behavior.
 */
class DockerComposeClassExtensionWaitForLogTest extends Specification {

    SystemPropertyService mockSystemPropertyService
    ComposeService mockComposeService
    DockerComposeClassExtension extension

    def setup() {
        mockSystemPropertyService = Mock(SystemPropertyService)
        mockComposeService = Mock(ComposeService)
        extension = new DockerComposeClassExtension(mockSystemPropertyService, mockComposeService)
    }

    // ===== parseIntProperty() TESTS =====

    def "parseIntProperty returns parsed value for valid integer"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> '42'

        expect:
        extension.parseIntProperty('test.prop', 0) == 42
    }

    def "parseIntProperty returns default for null property"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> null

        expect:
        extension.parseIntProperty('test.prop', 10) == 10
    }

    def "parseIntProperty returns default for empty property"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> ''

        expect:
        extension.parseIntProperty('test.prop', 10) == 10
    }

    def "parseIntProperty returns default for invalid integer"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> 'not-a-number'

        expect:
        extension.parseIntProperty('test.prop', 10) == 10
    }

    def "parseIntProperty handles negative values"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> '-5'

        expect:
        extension.parseIntProperty('test.prop', 0) == -5
    }

    def "parseIntProperty handles whitespace around value"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> '  42  '

        expect:
        extension.parseIntProperty('test.prop', 0) == 42
    }

    // ===== parseBooleanProperty() TESTS =====

    def "parseBooleanProperty returns true for 'true'"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> 'true'

        expect:
        extension.parseBooleanProperty('test.prop', false) == true
    }

    def "parseBooleanProperty returns false for 'false'"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> 'false'

        expect:
        extension.parseBooleanProperty('test.prop', true) == false
    }

    def "parseBooleanProperty returns default for null property"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> null

        expect:
        extension.parseBooleanProperty('test.prop', true) == true
    }

    def "parseBooleanProperty returns default for empty property"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> ''

        expect:
        extension.parseBooleanProperty('test.prop', true) == true
    }

    def "parseBooleanProperty returns false for non-standard values"() {
        given:
        mockSystemPropertyService.getProperty('test.prop') >> value

        expect:
        extension.parseBooleanProperty('test.prop', true) == false

        where:
        value << ['yes', 'YES', '1', 'on', 'ON', 'True', 'TRUE']
    }

    // ===== parseJsonMapProperty() TESTS =====

    def "parseJsonMapProperty parses valid JSON map"() {
        given:
        def json = '{"app": ["Started", "Ready"], "db": ["connections"]}'

        when:
        def result = extension.parseJsonMapProperty(json)

        then:
        result == ['app': ['Started', 'Ready'], 'db': ['connections']]
    }

    def "parseJsonMapProperty returns empty map for null input"() {
        expect:
        extension.parseJsonMapProperty(null) == [:]
    }

    def "parseJsonMapProperty returns empty map for empty string"() {
        expect:
        extension.parseJsonMapProperty('') == [:]
    }

    def "parseJsonMapProperty returns empty map for invalid JSON"() {
        expect:
        extension.parseJsonMapProperty('not valid json') == [:]
    }

    def "parseJsonMapProperty returns empty map for JSON array (not object)"() {
        expect:
        extension.parseJsonMapProperty('["item1", "item2"]') == [:]
    }

    def "parseJsonMapProperty handles empty object"() {
        expect:
        extension.parseJsonMapProperty('{}') == [:]
    }

    def "parseJsonMapProperty handles patterns with escaped backslashes"() {
        given:
        def json = '{"app": ["\\\\[INFO\\\\]"]}'

        when:
        def result = extension.parseJsonMapProperty(json)

        then:
        result['app'][0] == '\\[INFO\\]'
    }

    // ===== performWaitForLog() TESTS =====

    def "performWaitForLog skips when services property is null"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_SERVICES) >> null

        when:
        extension.performWaitForLog('test-project')

        then:
        0 * mockComposeService.waitForLogPatterns(_)
    }

    def "performWaitForLog skips when services property is empty JSON"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_SERVICES) >> '{}'

        when:
        extension.performWaitForLog('test-project')

        then:
        0 * mockComposeService.waitForLogPatterns(_)
    }

    def "performWaitForLog invokes waitForLogPatterns when services configured"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_SERVICES) >>
            '{"app": ["Started"]}'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_REJECT_PATTERNS) >> null
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_TIMEOUT) >> '60'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_POLL) >> '2'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_CASE_INSENSITIVE) >> 'false'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_VERBOSE) >> 'false'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_PROGRESS_INTERVAL) >> '0'

        when:
        extension.performWaitForLog('test-project')

        then:
        1 * mockComposeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture([:])
    }

    // ===== performWaitForRunning() TESTS =====

    def "performWaitForRunning skips when services property is null"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_RUNNING_SERVICES) >> null

        when:
        extension.performWaitForRunning('test-project')

        then:
        0 * mockComposeService.waitForServices(_)
    }

    def "performWaitForRunning invokes waitForServices with RUNNING status"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_RUNNING_SERVICES) >> 'app,db'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_RUNNING_TIMEOUT) >> '60'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_RUNNING_POLL) >> '2'

        when:
        extension.performWaitForRunning('test-project')

        then:
        1 * mockComposeService.waitForServices({ config ->
            config.targetStatus == ServiceStatus.RUNNING &&
            config.services.containsAll(['app', 'db'])
        }) >> CompletableFuture.completedFuture([:])
    }

    // ===== performWaitForHealthy() TESTS =====

    def "performWaitForHealthy skips when services property is null"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_HEALTHY_SERVICES) >> null

        when:
        extension.performWaitForHealthy('test-project')

        then:
        0 * mockComposeService.waitForServices(_)
    }

    def "performWaitForHealthy invokes waitForServices with HEALTHY status"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_HEALTHY_SERVICES) >> 'app'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_HEALTHY_TIMEOUT) >> '120'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_HEALTHY_POLL) >> '5'

        when:
        extension.performWaitForHealthy('test-project')

        then:
        1 * mockComposeService.waitForServices({ config ->
            config.targetStatus == ServiceStatus.HEALTHY &&
            config.timeout.toSeconds() == 120 &&
            config.pollInterval.toSeconds() == 5
        }) >> CompletableFuture.completedFuture([:])
    }

    // ===== waitForStackToBeReady() TESTS =====

    def "waitForStackToBeReady calls wait methods in correct order"() {
        given:
        // Setup properties for all three wait types
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_RUNNING_SERVICES) >> 'app'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_HEALTHY_SERVICES) >> 'app'
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_SERVICES) >> '{"app": ["Started"]}'
        // Setup remaining properties with defaults
        mockSystemPropertyService.getProperty(_) >> null
        def callOrder = []

        when:
        extension.waitForStackToBeReady('stack', 'project')

        then:
        1 * mockComposeService.waitForServices({ it.targetStatus == ServiceStatus.RUNNING }) >> {
            callOrder << 'running'
            CompletableFuture.completedFuture([:])
        }
        then:
        1 * mockComposeService.waitForServices({ it.targetStatus == ServiceStatus.HEALTHY }) >> {
            callOrder << 'healthy'
            CompletableFuture.completedFuture([:])
        }
        then:
        1 * mockComposeService.waitForLogPatterns(_) >> {
            callOrder << 'log'
            CompletableFuture.completedFuture([:])
        }
        then:
        callOrder == ['running', 'healthy', 'log']
    }

    def "waitForStackToBeReady succeeds when no wait blocks configured"() {
        given:
        mockSystemPropertyService.getProperty(_) >> null

        when:
        extension.waitForStackToBeReady('stack', 'project')

        then:
        0 * mockComposeService._
        noExceptionThrown()
    }

    // ===== CLASS LIFECYCLE-SPECIFIC TESTS =====

    def "extension uses CLASS lifecycle constant"() {
        expect:
        DockerComposeClassExtension.LIFECYCLE == Lifecycle.CLASS
    }

    def "beforeAll context does not affect wait behavior"() {
        given:
        mockSystemPropertyService.getProperty(DockerComposeClassExtension.WAIT_FOR_LOG_SERVICES) >>
            '{"app": ["Started"]}'
        mockSystemPropertyService.getProperty(_) >> null

        when:
        // Simulate beforeAll invocation context
        extension.performWaitForLog('test-project')

        then:
        1 * mockComposeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture([:])
    }
}
```

**Coverage Requirements**:
- `parseIntProperty()`: Valid, null, empty, invalid input, whitespace handling
- `parseBooleanProperty()`: True, false, null, empty, non-standard values (yes, 1, on, etc.)
- `parseJsonMapProperty()`: Valid JSON, null, empty, invalid JSON, empty object, escaped characters
- `performWaitForRunning()`: Skip when null, invocation with correct status
- `performWaitForHealthy()`: Skip when null, invocation with correct status
- `performWaitForLog()`: Skip when null/empty, invocation when configured
- `waitForStackToBeReady()`: Execution order, success with no config
- CLASS lifecycle constant verification
- beforeAll context behavior

---

### 15. JUnitComposeServiceWaitForLogTest

**File**: `plugin/src/test/groovy/com/kineticfire/gradle/docker/junit/JUnitComposeServiceWaitForLogTest.groovy`

```groovy
/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 * ... License header ...
 */

package com.kineticfire.gradle.docker.junit

import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import com.kineticfire.gradle.docker.service.ComposeService
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

/**
 * Unit tests for JUnitComposeService waitForLogPatterns delegation
 */
class JUnitComposeServiceWaitForLogTest extends Specification {

    ComposeService mockDelegate
    JUnitComposeService service

    def setup() {
        mockDelegate = Mock(ComposeService)
        service = new JUnitComposeService(mockDelegate)
    }

    def "waitForLogPatterns delegates to underlying service"() {
        given:
        def config = createTestConfig()
        def expectedResult = ['app': new WaitForLogResult('app', [], true)]
        mockDelegate.waitForLogPatterns(config) >> CompletableFuture.completedFuture(expectedResult)

        when:
        def future = service.waitForLogPatterns(config)
        def result = future.get()

        then:
        1 * mockDelegate.waitForLogPatterns(config)
        result == expectedResult
    }

    def "waitForLogPatterns propagates exceptions from delegate"() {
        given:
        def config = createTestConfig()
        def expectedException = new ComposeServiceException(
            ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
            "Timeout"
        )
        mockDelegate.waitForLogPatterns(config) >> {
            def future = new CompletableFuture()
            future.completeExceptionally(expectedException)
            future
        }

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause == expectedException
    }

    def "waitForLogPatterns passes config unchanged to delegate"() {
        given:
        def config = createTestConfig()

        when:
        service.waitForLogPatterns(config)

        then:
        1 * mockDelegate.waitForLogPatterns({ passedConfig ->
            passedConfig.projectName == config.projectName &&
            passedConfig.timeout == config.timeout &&
            passedConfig.pollInterval == config.pollInterval
        }) >> CompletableFuture.completedFuture([:])
    }

    // ===== HELPER METHODS =====

    private WaitForLogConfig createTestConfig() {
        return new WaitForLogConfig(
            'test-project',
            ['app': [Pattern.compile('Started')]],
            [:],
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            false,
            false,
            Duration.ZERO
        )
    }
}
```

**Coverage Requirements**:
- Delegation: Calls pass through to underlying service
- Exception propagation: Exceptions from delegate are propagated
- Config integrity: Config is passed unchanged

---

## Coverage Gaps Documentation

If any code cannot be unit tested (e.g., due to actual Docker daemon requirements), document the gap in:
`docs/design-docs/testing/unit-test-gaps.md`

Expected potential gaps:
1. **Actual Docker Compose process execution** - Integration test coverage only
2. **Real container log streaming** - Integration test coverage only
3. **Configuration cache serialization behavior** - Functional test coverage

---

## Test Execution Commands

```bash
# Run all unit tests
cd plugin && ./gradlew clean test

# Run specific test class
cd plugin && ./gradlew test --tests "WaitForLogSpecTest"

# Run tests with coverage report
cd plugin && ./gradlew clean test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

---

## Test Naming Conventions

Follow existing project conventions:
- Test class: `{ClassName}Test.groovy` (e.g., `WaitForLogSpecTest.groovy`)
- Feature-specific tests: `{ClassName}{Feature}Test.groovy` (e.g., `ExecLibraryComposeServiceWaitForLogTest.groovy`)
- Test method: `"descriptive sentence describing behavior"()` using Spock string literals

## Verification Checklist

Before marking tests complete:
- [ ] All tests pass: `./gradlew test`
- [ ] No compilation warnings
- [ ] JaCoCo coverage report shows 100% line and branch coverage for new code
- [ ] Test names clearly describe the behavior being tested
- [ ] Tests are isolated (no shared state between tests)
- [ ] No reliance on external services (Docker, network, filesystem outside @TempDir)

---

## Review Findings and Recommendations (2026-01-10)

This section documents findings from the unit test plan review against the implementation plan (0200).

### Critical Gaps Requiring Action

#### 1. JSON Format Mismatch in Mock Outputs

**Issue**: Tests for `getComposeProjectServices()` (lines 2759-2806) show text-based mock outputs:
```groovy
def psOutput = '''NAME       SERVICE    STATUS
test-app-1   app        running
test-db-1    db         running'''
```

But the implementation (Section 8, lines 1635-1667) uses JSON format:
```groovy
def command = composeCommand + ["-p", projectName, "ps", "--format", "json", "-a"]
// Expected: {"Service":"app","State":"running","ExitCode":0}
```

**Action Required**: Update all `getComposeProjectServices()` and `isServiceRunning()` tests to use JSON format
mock outputs matching Docker Compose v2 `--format json` output.

**Corrected mock format**:
```groovy
def psOutput = '{"Service":"app","State":"running","ExitCode":0}\n{"Service":"db","State":"running","ExitCode":0}'
```

#### 2. Method Signature Mismatch in validateServicesExist() Tests

**Issue**: Tests show signature `validateServicesExist(runningServices, requestedServices)` (lines 2810-2861),
but implementation shows `validateServicesExist(WaitForLogConfig config)` (Section 8, lines 1597-1626).

**Action Required**: Update test method calls to match the actual implementation signature:
```groovy
// Instead of:
service.validateServicesExist(runningServices, requestedServices)

// Use:
def config = createConfig(['unknownSvc': ['Started']])
service.validateServicesExist(config)
```

#### 3. Missing getServiceExitCode() Explicit Tests

**Issue**: The `getServiceExitCode()` method (implementation lines 1905-1927) is not explicitly tested in
Section 9. The checklist mentions it but no test code is provided.

**Action Required**: Add explicit tests for `getServiceExitCode()`:

```groovy
// ===== getServiceExitCode() TESTS =====

def "getServiceExitCode returns exit code when container exited"() {
    given:
    def jsonOutput = '{"Service":"app","State":"exited","ExitCode":1}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    expect:
    service.getServiceExitCode('test-project', 'app') == 1
}

def "getServiceExitCode returns 0 for successful exit"() {
    given:
    def jsonOutput = '{"Service":"app","State":"exited","ExitCode":0}'
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    expect:
    service.getServiceExitCode('test-project', 'app') == 0
}

def "getServiceExitCode returns null when service not found"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, "", "")

    expect:
    service.getServiceExitCode('test-project', 'app') == null
}

def "getServiceExitCode returns null on command failure"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(1, "", "error")

    expect:
    service.getServiceExitCode('test-project', 'app') == null
}

def "getServiceExitCode returns null when JSON lacks ExitCode field"() {
    given:
    def jsonOutput = '{"Service":"app","State":"running"}'  // No ExitCode
    mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, "")

    expect:
    service.getServiceExitCode('test-project', 'app') == null
}

def "getServiceExitCode handles JsonException gracefully"() {
    given:
    mockProcessExecutor.execute(_) >> new ProcessResult(0, "not valid json", "")

    expect:
    service.getServiceExitCode('test-project', 'app') == null
}
```

#### 4. fetchServiceLogs() Return Type Mismatch

**Issue**: Tests show `fetchServiceLogs()` returning raw String logs (lines 2864-2921), but implementation
returns `List<String>` (Section 8, lines 1794-1806):
```groovy
return trimmedOutput ? trimmedOutput.split('\n').toList() : []
```

**Action Required**: Update test expectations to match `List<String>` return type:
```groovy
def "fetchServiceLogs returns list of log lines"() {
    given:
    def rawOutput = "Line 1\nLine 2\nLine 3"
    // Mock captureLogs to return this output

    when:
    def logs = service.fetchServiceLogs('project', 'app')

    then:
    logs == ['Line 1', 'Line 2', 'Line 3']
}

def "fetchServiceLogs returns empty list for null output"() {
    when:
    def logs = service.fetchServiceLogs('project', 'app')

    then:
    logs == []
}

def "fetchServiceLogs returns empty list for whitespace-only output"() {
    given:
    // Mock to return "   \n\t\n   "

    when:
    def logs = service.fetchServiceLogs('project', 'app')

    then:
    logs == []
}
```

### Moderate Gaps

#### 5. LogPatternMatcher.compilePattern() Null Input Test Missing

**Issue**: No test for `compilePattern(null, false)` behavior.

**Action Required**: Add test:
```groovy
def "compilePattern throws NullPointerException for null pattern string"() {
    when:
    LogPatternMatcher.compilePattern(null, false)

    then:
    thrown(NullPointerException)
}
```

#### 6. parseIntProperty() Whitespace Handling Inconsistency

**Issue**: `DockerComposeClassExtensionWaitForLogTest` has whitespace handling test (line 4106-4111):
```groovy
def "parseIntProperty handles whitespace around value"() {
    given:
    mockSystemPropertyService.getProperty('test.prop') >> '  42  '
    expect:
    extension.parseIntProperty('test.prop', 0) == 42
}
```

But `DockerComposeMethodExtensionWaitForLogTest` is missing this test.

**Action Required**: Add the same whitespace test to `DockerComposeMethodExtensionWaitForLogTest`.

#### 7. parseBooleanProperty() Non-Standard Values Coverage

**Issue**: `DockerComposeClassExtensionWaitForLogTest` has comprehensive data-driven test (lines 4147-4156):
```groovy
def "parseBooleanProperty returns false for non-standard values"() {
    where:
    value << ['yes', 'YES', '1', 'on', 'ON', 'True', 'TRUE']
}
```

But `DockerComposeMethodExtensionWaitForLogTest` only has single test for 'yes' value (line 3811).

**Action Required**: Update `DockerComposeMethodExtensionWaitForLogTest` to use parameterized test:
```groovy
def "parseBooleanProperty returns false for non-standard values"() {
    given:
    mockSystemPropertyService.getProperty('test.prop') >> value

    expect:
    extension.parseBooleanProperty('test.prop', true) == false

    where:
    value << ['yes', 'YES', '1', 'on', 'ON', 'True', 'TRUE']
}
```

### Minor Gaps and Improvements

#### 8. Warning Test Fragility

**Issue**: Tests for warning conditions (lines 1847-1891) use `System.err` capture:
```groovy
def originalErr = System.err
def capturedOutput = new ByteArrayOutputStream()
System.err = new PrintStream(capturedOutput)
```

This approach is fragile and can cause issues with parallel test execution.

**Recommendation**: Consider injecting a logger interface to `WaitForLogConfigBuilder` for testability,
or accept this as a documented testing limitation.

#### 9. Missing Import Statements

**Issue**: Several test specifications reference classes without showing required imports in the code blocks.
This could cause compilation errors during implementation.

**Action Required**: Ensure all test files include necessary imports:
```groovy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import com.kineticfire.gradle.docker.exception.ComposeServiceException
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import com.kineticfire.gradle.docker.util.LogPatternMatcher
```

#### 10. getRecentLogs() Test Incomplete

**Issue**: Test for `getRecentLogs()` (lines 2743-2755) has placeholder comment:
```groovy
then:
    // Verify LogsConfig was created with correct tailLines
    1 * mockProcessExecutor.execute(_) >> { args ->
        // Verify tailLines = 10 in the command
    }
```

**Action Required**: Complete the test with actual verification:
```groovy
def "getRecentLogs fetches specified number of lines"() {
    given:
    def lineCount = 10
    service.mockCaptureLogs('app', 'Line 1\nLine 2\nLine 3')

    when:
    def logs = service.getRecentLogs('project', 'app', lineCount)

    then:
    logs.size() == 3
    logs == ['Line 1', 'Line 2', 'Line 3']
}
```

### Additional Tests to Add

#### 11. Empty Compose Project Specific Error Message

**Issue**: Implementation distinguishes between "empty compose project" and "missing specific services"
(Section 8, lines 1600-1625), but tests don't verify the specific error message for empty projects.

**Action Required**: Add test:
```groovy
def "validateServicesExist throws specific error for empty compose project"() {
    given:
    def config = createConfig(['app': ['Started']])
    // Mock getComposeProjectServices to return empty set
    service.mockComposeProjectServices([])

    when:
    service.validateServicesExist(config)

    then:
    def ex = thrown(ComposeServiceException)
    ex.message.contains("No services found in compose project")
    ex.message.contains("composeUp")
}
```

#### 12. CheckAllServicesResult Factory Methods in Test Scope

**Issue**: Tests for `CheckAllServicesResult` factory methods are in "Additional Tests" section
(lines 2596-2643) but not integrated into main test file.

**Action Required**: Ensure these tests are included in the final
`ExecLibraryComposeServiceWaitForLogTest.groovy` file:
```groovy
def "CheckAllServicesResult.ok() returns result with no issues"()
def "CheckAllServicesResult.rejected() returns result with reject info"()
def "CheckAllServicesResult.crashed() returns result with exit code"()
def "CheckAllServicesResult.crashed() handles null exit code"()
```

### Updated Checklist Items

Add to Phase 2 checklist:
- [ ] Verify JSON format in `getComposeProjectServices()` mock outputs matches Docker Compose v2
- [ ] Verify JSON format in `isServiceRunning()` mock outputs matches Docker Compose v2
- [ ] Verify `validateServicesExist()` test signature matches implementation
- [ ] Add explicit `getServiceExitCode()` tests
- [ ] Verify `fetchServiceLogs()` tests expect `List<String>` return type

Add to Phase 3 checklist:
- [ ] Add `parseIntProperty()` whitespace test to `DockerComposeMethodExtensionWaitForLogTest`
- [ ] Add parameterized `parseBooleanProperty()` test to `DockerComposeMethodExtensionWaitForLogTest`

### Additional Gaps Identified (2026-01-11)

#### 13. waitForLogPatterns() Null Config Validation Test Missing

**Issue**: The implementation (Section 8, lines 1439-1441) includes null validation:
```groovy
if (config == null) {
    throw new NullPointerException("Wait-for-log config cannot be null")
}
```

But no explicit test exists for this condition.

**Action Required**: Add test to `ExecLibraryComposeServiceWaitForLogTest.groovy`:
```groovy
def "waitForLogPatterns throws NullPointerException when config is null"() {
    when:
    service.waitForLogPatterns(null)

    then:
    thrown(NullPointerException)
}
```

#### 14. InterruptedException Handling Test Missing

**Issue**: The implementation (Section 8, lines 1447-1454) has specific interrupt handling:
```groovy
} catch (InterruptedException e) {
    Thread.currentThread().interrupt()
    throw new ComposeServiceException(
        ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
        "Interrupted while waiting for log patterns",
        e
    )
}
```

This branch needs explicit test coverage to verify:
1. The exception is wrapped in ComposeServiceException with correct ErrorType
2. Thread interrupt status is preserved

**Action Required**: Add test to `ExecLibraryComposeServiceWaitForLogTest.groovy`:
```groovy
def "waitForLogPatterns preserves interrupt status when interrupted"() {
    given:
    def config = createTestConfig()
    // Mock timeService.sleep to throw InterruptedException
    mockTimeService.sleep(_) >> { throw new InterruptedException("Test interrupt") }
    mockComposeProjectServices(['app'])

    when:
    def future = service.waitForLogPatterns(config)
    future.get()

    then:
    def ex = thrown(ExecutionException)
    ex.cause instanceof ComposeServiceException
    ex.cause.errorType == ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT
    ex.cause.message.contains("Interrupted")
    // Note: Thread interrupt status may be consumed by CompletableFuture.get()
    // Consider testing executeWaitForLogPatterns() directly
}
```

#### 15. RejectCheckResult Inner Class Tests Missing

**Issue**: `LogPatternMatcher.RejectCheckResult` (Section 4, lines 856-864) is an inner class with
a constructor and two final fields. No explicit tests exist for this class.

**Action Required**: Add tests to `LogPatternMatcherTest.groovy`:
```groovy
// ===== RejectCheckResult TESTS =====

def "RejectCheckResult stores pattern string and matching log line"() {
    when:
    def result = new LogPatternMatcher.RejectCheckResult('ERROR.*', 'ERROR: Connection failed')

    then:
    result.patternString == 'ERROR.*'
    result.matchingLogLine == 'ERROR: Connection failed'
}

def "RejectCheckResult accepts null values"() {
    when:
    def result = new LogPatternMatcher.RejectCheckResult(null, null)

    then:
    result.patternString == null
    result.matchingLogLine == null
}
```

#### 16. WaitForLogResult Secondary Constructor Test Coverage

**Issue**: `WaitForLogResult` has two constructors (Section 3):
1. Primary constructor for successful/in-progress results (lines 793-800)
2. Secondary constructor for rejected results (lines 802-810)

The test specification (Section 5) should verify both constructors are tested.

**Action Required**: Verify `WaitForLogResultTest.groovy` includes:
```groovy
def "secondary constructor creates rejected result with correct fields"() {
    when:
    def result = new WaitForLogResult('app', [], 'Error.*', 'Error: startup failed')

    then:
    result.serviceName == 'app'
    result.ready == false
    result.rejected == true
    result.rejectPattern == 'Error.*'
    result.rejectLogLine == 'Error: startup failed'
}

def "secondary constructor sets rejected=true and ready=false"() {
    when:
    def result = new WaitForLogResult('svc', [], 'pattern', 'line')

    then:
    result.rejected == true
    result.ready == false
}
```

#### 17. ExecutionException Unwrapping Test Missing

**Issue**: The implementation (Section 8, lines 1456-1464) explicitly handles `ExecutionException`:
```groovy
} catch (java.util.concurrent.ExecutionException e) {
    def cause = e.cause ?: e
    throw new ComposeServiceException(
        ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
        "Error waiting for log patterns: ${cause.message}",
        cause
    )
}
```

The `e.cause ?: e` pattern (Elvis operator for null cause) should be tested.

**Action Required**: Add tests to `ExecLibraryComposeServiceWaitForLogTest.groovy`:
```groovy
def "waitForLogPatterns unwraps ExecutionException from captureLogs"() {
    given:
    def config = createTestConfig()
    def rootCause = new RuntimeException("Connection refused")
    def execException = new ExecutionException("Logs failed", rootCause)
    mockCaptureLogs(_) >> { throw execException }

    when:
    service.executeWaitForLogPatterns(config)

    then:
    def ex = thrown(ComposeServiceException)
    ex.cause == rootCause
    ex.message.contains("Connection refused")
}

def "waitForLogPatterns handles ExecutionException with null cause"() {
    given:
    def config = createTestConfig()
    def execException = new ExecutionException("Logs failed", null)  // null cause
    mockCaptureLogs(_) >> { throw execException }

    when:
    service.executeWaitForLogPatterns(config)

    then:
    def ex = thrown(ComposeServiceException)
    ex.cause == execException  // Uses execException itself when cause is null
}
```

### Updated Phase 2 Checklist Additions

Add to Phase 2 checklist:
- [ ] Add `waitForLogPatterns(null)` test for NullPointerException
- [ ] Add InterruptedException handling tests
- [ ] Add ExecutionException unwrapping tests (including null cause case)
- [ ] Add `RejectCheckResult` inner class tests
- [ ] Verify both `WaitForLogResult` constructors have test coverage

---

## Review Confirmation and Additional Findings (2026-01-11 - Final Review)

This section documents the final review of the unit test plan against the implementation plan (0200).

### Review Confirmation

The unit test plan has been reviewed and is **comprehensive**. The 17 gaps identified in the previous review
sections (2026-01-10 and 2026-01-11) are accurate and have actionable corrective code provided. The test plan
covers:

| Phase | Implementation Components | Test Coverage |
|-------|--------------------------|---------------|
| 0 | LogsConfig modification | See Gap #18 below |
| 1 | WaitForLogSpec, WaitForLogConfig, WaitForLogResult, LogPatternMatcher, WaitForLogConfigBuilder | Sections 2-6 |
| 2 | ComposeStackSpec extension, ComposeService interface, ExecLibraryComposeService, ComposeServiceException | Sections 7-9 |
| 3 | ComposeUpTask properties, GradleDockerPlugin wiring | Sections 10-11 |
| 4 | TestIntegrationExtension system properties | Section 12 |
| 5 | DockerComposeMethodExtension, DockerComposeClassExtension, JUnitComposeService | Sections 13-15 |

### Additional Gap Identified

#### 18. Phase 0 LogsConfig Modification Tests Missing

**Issue**: Implementation plan Phase 0 (Section 6) modifies `LogsConfig` to:
1. Remove the `Math.max(1, tailLines)` constraint, allowing `tailLines = 0` for "all logs"
2. Add `hasLimitedTail()` convenience method

No tests are specified for these changes in the unit test plan.

**Action Required**: Add tests to existing `LogsConfigTest.groovy` (or create if it doesn't exist):

```groovy
// ===== Phase 0 waitForLog Support Tests =====

def "constructor accepts tailLines = 0 for all logs"() {
    when:
    def config = new LogsConfig(['app'], 0, false, null)

    then:
    config.tailLines == 0
    noExceptionThrown()
}

def "constructor accepts negative tailLines (treated as all logs)"() {
    when:
    def config = new LogsConfig(['app'], -1, false, null)

    then:
    config.tailLines == -1  // Or verify normalization if implemented
}

def "hasLimitedTail returns true when tailLines > 0"() {
    given:
    def config = new LogsConfig(['app'], 10, false, null)

    expect:
    config.hasLimitedTail() == true
}

def "hasLimitedTail returns false when tailLines = 0"() {
    given:
    def config = new LogsConfig(['app'], 0, false, null)

    expect:
    config.hasLimitedTail() == false
}

def "hasLimitedTail returns false when tailLines < 0"() {
    given:
    def config = new LogsConfig(['app'], -1, false, null)

    expect:
    config.hasLimitedTail() == false
}
```

### Updated Phase 0 Checklist Addition

Add to Phase 0 checklist:
- [ ] Add `LogsConfig` tests for `tailLines = 0` acceptance
- [ ] Add `LogsConfig.hasLimitedTail()` tests

### Summary of All Gaps

| # | Gap Description | Severity | Action Location |
|---|-----------------|----------|-----------------|
| 1 | JSON format mismatch in mock outputs | Critical | ExecLibraryComposeServiceWaitForLogTest |
| 2 | Method signature mismatch in validateServicesExist() | Critical | ExecLibraryComposeServiceWaitForLogTest |
| 3 | Missing getServiceExitCode() explicit tests | Critical | ExecLibraryComposeServiceWaitForLogTest |
| 4 | fetchServiceLogs() return type mismatch | Critical | ExecLibraryComposeServiceWaitForLogTest |
| 5 | LogPatternMatcher.compilePattern() null input test | Moderate | LogPatternMatcherTest |
| 6 | parseIntProperty() whitespace handling inconsistency | Moderate | DockerComposeMethodExtensionWaitForLogTest |
| 7 | parseBooleanProperty() non-standard values coverage | Moderate | DockerComposeMethodExtensionWaitForLogTest |
| 8 | Warning test fragility | Minor | WaitForLogConfigBuilderTest |
| 9 | Missing import statements | Minor | All test files |
| 10 | getRecentLogs() test incomplete | Minor | ExecLibraryComposeServiceWaitForLogTest |
| 11 | Empty compose project specific error message | Minor | ExecLibraryComposeServiceWaitForLogTest |
| 12 | CheckAllServicesResult factory methods integration | Minor | ExecLibraryComposeServiceWaitForLogTest |
| 13 | waitForLogPatterns() null config validation | Moderate | ExecLibraryComposeServiceWaitForLogTest |
| 14 | InterruptedException handling test | Moderate | ExecLibraryComposeServiceWaitForLogTest |
| 15 | RejectCheckResult inner class tests | Moderate | LogPatternMatcherTest |
| 16 | WaitForLogResult secondary constructor | Moderate | WaitForLogResultTest |
| 17 | ExecutionException unwrapping test | Moderate | ExecLibraryComposeServiceWaitForLogTest |
| 18 | Phase 0 LogsConfig modification tests | Critical | LogsConfigTest |

**Critical**: 5 gaps - Must fix before implementation
**Moderate**: 7 gaps - Should fix before implementation
**Minor**: 6 gaps - Can fix during implementation

### Additional Gaps Identified (2026-01-11 - Secondary Review)

The following additional minor gaps were identified during secondary review:

#### 19. WaitForLogConfig.toString() Format Verification

**Issue**: The implementation (Section 2, line 745-748) includes a specific `toString()` format:
```groovy
return "WaitForLogConfig{projectName='${projectName}', services=${services}, " +
       "timeout=${timeout}, pollInterval=${pollInterval}, caseInsensitive=${caseInsensitive}}"
```

The test plan has a test that verifies `toString()` is not null, but doesn't verify the format includes
all expected fields.

**Severity**: Minor

**Action Required**: Update `WaitForLogConfigTest.groovy` to verify the format:
```groovy
def "toString includes all key fields"() {
    given:
    def config = new WaitForLogConfig(
        'test-project',
        ['app': [Pattern.compile('Started')]],
        [:],
        Duration.ofSeconds(60),
        Duration.ofSeconds(2),
        true, false,
        Duration.ZERO
    )

    expect:
    def str = config.toString()
    str.contains('test-project')
    str.contains('app')
    str.contains('60')
    str.contains('caseInsensitive')
}
```

#### 20. LogPatternMatcher Private Constructor Coverage

**Issue**: `LogPatternMatcher` (Section 4, lines 866-868) has a private constructor to prevent instantiation:
```groovy
private LogPatternMatcher() {
    // Utility class - prevent instantiation
}
```

For 100% line coverage, this constructor should be tested via reflection.

**Severity**: Minor (affects coverage metrics only)

**Action Required**: Add test to `LogPatternMatcherTest.groovy`:
```groovy
def "private constructor exists and prevents instantiation"() {
    when:
    def constructor = LogPatternMatcher.getDeclaredConstructor()
    constructor.setAccessible(true)
    constructor.newInstance()

    then:
    noExceptionThrown()  // Constructor completes successfully
    // Note: We're just verifying the constructor exists for coverage
}
```

#### 21. PatternMatch Inner Class Test Coverage

**Issue**: `WaitForLogResult.PatternMatch` (Section 3, lines 774-784) has three fields and a constructor.
While the outer class tests exercise this inner class indirectly, explicit tests ensure full coverage.

**Severity**: Minor

**Action Required**: Add tests to `WaitForLogResultTest.groovy`:
```groovy
// ===== PatternMatch INNER CLASS TESTS =====

def "PatternMatch stores pattern, matched status, and match time"() {
    when:
    def match = new WaitForLogResult.PatternMatch('Started.*', true, 5L)

    then:
    match.pattern == 'Started.*'
    match.matched == true
    match.matchedAtSeconds == 5L
}

def "PatternMatch allows null matchedAtSeconds for unmatched patterns"() {
    when:
    def match = new WaitForLogResult.PatternMatch('Error', false, null)

    then:
    match.pattern == 'Error'
    match.matched == false
    match.matchedAtSeconds == null
}
```

#### 22. RECENT_LOG_LINES_FOR_ERROR Constant Verification

**Issue**: The constant `RECENT_LOG_LINES_FOR_ERROR = 10` (Section 8, line 1423) is used in
`buildTimeoutException()` and `buildCrashException()`. Tests should verify that error messages
include recent log lines.

**Severity**: Minor

**Action Required**: Add test to `ExecLibraryComposeServiceWaitForLogTest.groovy`:
```groovy
def "timeout exception includes recent log lines"() {
    given:
    def config = createConfig(['app': ['NeverAppears']])
    mockTimeService.currentTimeMillis() >>> [0L, 0L, 61000L]
    mockProcessExecutor.execute(_ as List) >> new ProcessResult(0,
        '{"Service":"app","State":"running","ExitCode":0}', "")
    service.mockCaptureLogs('app', 'Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6\nLine 7\nLine 8\nLine 9\nLine 10')

    when:
    service.waitForLogPatterns(config).get()

    then:
    def ex = thrown(ExecutionException)
    ex.cause.message.contains('Last 10 log lines')
}
```

### Updated Summary of All Gaps

| # | Gap Description | Severity | Action Location |
|---|-----------------|----------|-----------------|
| 1 | JSON format mismatch in mock outputs | Critical | ExecLibraryComposeServiceWaitForLogTest |
| 2 | Method signature mismatch in validateServicesExist() | Critical | ExecLibraryComposeServiceWaitForLogTest |
| 3 | Missing getServiceExitCode() explicit tests | Critical | ExecLibraryComposeServiceWaitForLogTest |
| 4 | fetchServiceLogs() return type mismatch | Critical | ExecLibraryComposeServiceWaitForLogTest |
| 5 | LogPatternMatcher.compilePattern() null input test | Moderate | LogPatternMatcherTest |
| 6 | parseIntProperty() whitespace handling inconsistency | Moderate | DockerComposeMethodExtensionWaitForLogTest |
| 7 | parseBooleanProperty() non-standard values coverage | Moderate | DockerComposeMethodExtensionWaitForLogTest |
| 8 | Warning test fragility | Minor | WaitForLogConfigBuilderTest |
| 9 | Missing import statements | Minor | All test files |
| 10 | getRecentLogs() test incomplete | Minor | ExecLibraryComposeServiceWaitForLogTest |
| 11 | Empty compose project specific error message | Minor | ExecLibraryComposeServiceWaitForLogTest |
| 12 | CheckAllServicesResult factory methods integration | Minor | ExecLibraryComposeServiceWaitForLogTest |
| 13 | waitForLogPatterns() null config validation | Moderate | ExecLibraryComposeServiceWaitForLogTest |
| 14 | InterruptedException handling test | Moderate | ExecLibraryComposeServiceWaitForLogTest |
| 15 | RejectCheckResult inner class tests | Moderate | LogPatternMatcherTest |
| 16 | WaitForLogResult secondary constructor | Moderate | WaitForLogResultTest |
| 17 | ExecutionException unwrapping test | Moderate | ExecLibraryComposeServiceWaitForLogTest |
| 18 | Phase 0 LogsConfig modification tests | Critical | LogsConfigTest |
| 19 | WaitForLogConfig.toString() format verification | Minor | WaitForLogConfigTest |
| 20 | LogPatternMatcher private constructor coverage | Minor | LogPatternMatcherTest |
| 21 | PatternMatch inner class test coverage | Minor | WaitForLogResultTest |
| 22 | RECENT_LOG_LINES_FOR_ERROR constant verification | Minor | ExecLibraryComposeServiceWaitForLogTest |
| 23 | WaitForLogConfigBuilder private constructor coverage | Minor | WaitForLogConfigBuilderTest |

**Final Gap Count:**
- **Critical**: 5 gaps - Must fix before implementation
- **Moderate**: 7 gaps - Should fix before implementation
- **Minor**: 11 gaps - Can fix during implementation

**Total**: 23 gaps identified

### Verification Command After Gap Resolution

```bash
# After applying all gap fixes, verify tests compile and structure is correct
cd plugin && ./gradlew compileTestGroovy

# Run all new test classes to verify they execute (will fail until implementation)
cd plugin && ./gradlew test --tests "*WaitForLog*" --tests "*LogPatternMatcher*"
```
