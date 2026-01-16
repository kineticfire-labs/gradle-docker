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

    def "getTotalWaitAttempts returns minimum of 1 when timeout less than pollInterval"() {
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

    def "getServices returns new list not internal reference"() {
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
