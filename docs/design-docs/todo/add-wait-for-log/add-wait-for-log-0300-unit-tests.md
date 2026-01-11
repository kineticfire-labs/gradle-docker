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
  - [ ] Tests for `tailLines = 0` behavior
  - [ ] Tests for `hasLimitedTail()` method

### Phase 1: Core Components
- [ ] Unit tests for `WaitForLogSpec` (`WaitForLogSpecTest.groovy`)
- [ ] Unit tests for `WaitForLogConfig` (`WaitForLogConfigTest.groovy`)
- [ ] Unit tests for `WaitForLogResult` (`WaitForLogResultTest.groovy`)
- [ ] Unit tests for `LogPatternMatcher` (`LogPatternMatcherTest.groovy`)
- [ ] Unit tests for `WaitForLogConfigBuilder` (`WaitForLogConfigBuilderTest.groovy`)

### Phase 2: Integration Components
- [ ] Unit tests for `ComposeStackSpec.waitForLog` extension (`ComposeStackSpecWaitForLogTest.groovy`)
- [ ] Unit tests for `ComposeServiceException` new error types (`ComposeServiceExceptionTest.groovy` - extend)
- [ ] Unit tests for `ExecLibraryComposeService.waitForLogPatterns()`
      (`ExecLibraryComposeServiceWaitForLogTest.groovy`)
- [ ] Unit tests for `ComposeUpTask` waitForLog properties (`ComposeUpTaskWaitForLogTest.groovy`)
- [ ] Unit tests for `GradleDockerPlugin` waitForLog wiring (`GradleDockerPluginWaitForLogTest.groovy`)
- [ ] Unit tests for `TestIntegrationExtension` waitForLog properties
      (`TestIntegrationExtensionWaitForLogTest.groovy`)

### Phase 3: Test Framework Extensions
- [ ] Unit tests for `DockerComposeMethodExtension` waitForLog (`DockerComposeMethodExtensionWaitForLogTest.groovy`)
- [ ] Unit tests for `DockerComposeClassExtension` waitForLog (`DockerComposeClassExtensionWaitForLogTest.groovy`)
- [ ] Unit tests for helper methods (`parseIntProperty`, `parseBooleanProperty`, `parseJsonMapProperty`)

### Final Verification
- [ ] All unit tests pass
- [ ] 100% line and branch coverage achieved (or gaps documented)
- [ ] No compilation warnings

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

    // ===== RejectCheckResult TESTS =====

    def "RejectCheckResult stores pattern and log line"() {
        when:
        def result = new LogPatternMatcher.RejectCheckResult('Error.*', 'Error: Something failed')

        then:
        result.patternString == 'Error.*'
        result.matchingLogLine == 'Error: Something failed'
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
}
```

**Coverage Requirements**:
- Successful builds with various configurations
- Validation errors: null/empty services, invalid timeout/poll, empty patterns, invalid regex
- Warning outputs: poll > timeout, orphaned reject patterns
- Edge cases: Regex special chars, pattern order preservation

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
- `isServiceRunning()`: Running, not running, parse error handling
- `getServiceExitCode()`: Exit codes, null handling
- `fetchServiceLogs()`: Normal logs, empty logs, error handling
- `buildTimeoutException()`, `buildRejectException()`, `buildCrashException()`: Message formatting
- Logging helpers: Verbose logging, progress logging

---

### 10. Additional Test Files Summary

Due to space constraints, the following test files follow similar patterns and should be created:

#### ComposeUpTaskWaitForLogTest.groovy
- Test all `waitForLog*` properties exist and accept values
- Test `performWaitForLog()` method invocation
- Test property wiring from spec to task inputs
- Test execution order in `performWaitIfConfigured()`

#### GradleDockerPluginWaitForLogTest.groovy
- Test waitForLog property wiring from `ComposeStackSpec` to `ComposeUpTask`
- Test conditional wiring (only when `waitForLog.present`)
- Test default values are applied

#### TestIntegrationExtensionWaitForLogTest.groovy
- Test system property propagation for all waitForLog properties
- Test JSON serialization of `waitForServices` map
- Test JSON serialization of `rejectPatterns` map
- Test both CLASS and METHOD lifecycle support

#### DockerComposeMethodExtensionWaitForLogTest.groovy
- Test `performWaitForLog()` method
- Test `parseIntProperty()` helper: Valid values, invalid values, defaults
- Test `parseBooleanProperty()` helper: True, false, invalid, defaults
- Test `parseJsonMapProperty()` helper: Valid JSON, invalid JSON, empty
- Test integration with `waitForStackToBeReady()`

#### DockerComposeClassExtensionWaitForLogTest.groovy
- Similar tests to Method extension
- Test CLASS lifecycle-specific behavior

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
