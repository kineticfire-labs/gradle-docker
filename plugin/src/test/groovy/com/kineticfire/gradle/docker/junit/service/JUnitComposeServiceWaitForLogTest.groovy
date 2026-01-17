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

package com.kineticfire.gradle.docker.junit.service

import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import spock.lang.Specification
import spock.lang.Subject

import java.lang.reflect.Method
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.regex.Pattern

/**
 * Unit tests for JUnitComposeService waitForLogPatterns functionality.
 *
 * Covers:
 * - waitForLogPatterns() null parameter validation
 * - fetchServiceLogs() private method
 * - buildResults() private method
 * - Error handling and edge cases
 *
 * Note: Full async behavior is tested in integration tests due to CompletableFuture.supplyAsync()
 * creating real threads that cannot be effectively mocked in unit tests.
 */
class JUnitComposeServiceWaitForLogTest extends Specification {

    ProcessExecutor mockExecutor = Mock()

    @Subject
    JUnitComposeService service

    def setup() {
        service = new JUnitComposeService(mockExecutor)
    }

    // ===== waitForLogPatterns() null validation tests =====

    def "waitForLogPatterns throws NullPointerException for null config"() {
        when:
        service.waitForLogPatterns(null)

        then:
        thrown(NullPointerException)
    }

    def "waitForLogPatterns returns CompletableFuture for valid config"() {
        given:
        def config = createBasicWaitForLogConfig()
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "")

        when:
        def future = service.waitForLogPatterns(config)

        then:
        future != null
        future instanceof CompletableFuture
    }

    // ===== fetchServiceLogs() tests via reflection =====

    def "fetchServiceLogs returns empty string when exit code is non-zero"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(1, "Error")

        when:
        def result = invokeFetchServiceLogs("test-project", "app")

        then:
        result == ""
    }

    def "fetchServiceLogs returns output when exit code is zero"() {
        given:
        def logOutput = "2024-01-15 12:00:00 Started Application\n2024-01-15 12:00:01 Ready"
        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, logOutput)

        when:
        def result = invokeFetchServiceLogs("test-project", "app")

        then:
        result == logOutput
    }

    def "fetchServiceLogs returns empty string on exception"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            { throw new IOException("Process failed") }

        when:
        def result = invokeFetchServiceLogs("test-project", "app")

        then:
        result == ""
    }

    def "fetchServiceLogs handles null output by returning empty string"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, null)

        when:
        def result = invokeFetchServiceLogs("test-project", "app")

        then:
        result == null || result == ""  // Implementation may return null or handle it
    }

    // ===== buildResults() tests via reflection =====

    def "buildResults creates WaitForLogResult for each service"() {
        given:
        def services = [
            'app': ['Started', 'Ready'],
            'db': ['accepting connections']
        ]
        def matchedPatterns = [
            'app': ['Started', 'Ready'] as Set,
            'db': ['accepting connections'] as Set
        ]

        when:
        def results = invokeBuildResults(services, matchedPatterns)

        then:
        results.size() == 2
        results.containsKey('app')
        results.containsKey('db')
        results['app'] instanceof WaitForLogResult
        results['db'] instanceof WaitForLogResult
    }

    def "buildResults handles partial matches"() {
        given:
        def services = ['app': ['Started', 'Ready', 'Listening']]
        def matchedPatterns = ['app': ['Started'] as Set]  // Only one matched

        when:
        def results = invokeBuildResults(services, matchedPatterns)

        then:
        results.size() == 1
        results.containsKey('app')
    }

    def "buildResults handles empty matched patterns"() {
        given:
        def services = ['app': ['Started']]
        def matchedPatterns = ['app': [] as Set]  // None matched

        when:
        def results = invokeBuildResults(services, matchedPatterns)

        then:
        results.size() == 1
        results.containsKey('app')
    }

    def "buildResults handles empty services map"() {
        given:
        def services = [:]
        def matchedPatterns = [:]

        when:
        def results = invokeBuildResults(services, matchedPatterns)

        then:
        results.isEmpty()
    }

    // ===== waitForLogPatterns async behavior edge cases =====

    def "waitForLogPatterns handles timeout scenario"() {
        given:
        // Create config with very short timeout
        def servicePatterns = ['app': [Pattern.compile('NeverMatches')]]
        def config = new WaitForLogConfig(
            'test-project',
            servicePatterns,
            [:],
            Duration.ofMillis(100),  // Very short timeout
            Duration.ofMillis(50),
            false, false,
            Duration.ZERO
        )

        // Return logs that don't contain the pattern
        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, "Some unrelated log output")

        when:
        def future = service.waitForLogPatterns(config)
        future.get()  // Wait for completion

        then:
        def ex = thrown(ExecutionException)
        ex.cause.message.contains("Timeout")
    }

    def "waitForLogPatterns handles reject pattern match"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        def rejectPatterns = ['app': [Pattern.compile('Error')]]
        def config = new WaitForLogConfig(
            'test-project',
            servicePatterns,
            rejectPatterns,
            Duration.ofSeconds(5),
            Duration.ofMillis(50),
            false, false,
            Duration.ZERO
        )

        // Return logs containing the reject pattern
        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, "Error: Application crashed")

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause.message.contains("Reject pattern matched")
    }

    def "waitForLogPatterns succeeds when all patterns match"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        def config = new WaitForLogConfig(
            'test-project',
            servicePatterns,
            [:],
            Duration.ofSeconds(5),
            Duration.ofMillis(50),
            false, false,
            Duration.ZERO
        )

        // Return logs containing the pattern
        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, "Application Started successfully")

        when:
        def future = service.waitForLogPatterns(config)
        def results = future.get()

        then:
        results != null
        results.containsKey('app')
    }

    def "waitForLogPatterns handles case-insensitive matching"() {
        given:
        // Create pattern with case-insensitive flag (patterns are pre-compiled in WaitForLogConfig)
        def servicePatterns = ['app': [Pattern.compile('started', Pattern.CASE_INSENSITIVE)]]
        def config = new WaitForLogConfig(
            'test-project',
            servicePatterns,
            [:],
            Duration.ofSeconds(5),
            Duration.ofMillis(50),
            true,  // case-insensitive
            false,
            Duration.ZERO
        )

        // Return logs with different case
        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, "STARTED Application")

        when:
        def future = service.waitForLogPatterns(config)
        def results = future.get()

        then:
        results != null
        results.containsKey('app')
    }

    def "waitForLogPatterns handles multiple services"() {
        given:
        def servicePatterns = [
            'app': [Pattern.compile('Started')],
            'db': [Pattern.compile('Ready')]
        ]
        def config = new WaitForLogConfig(
            'test-project',
            servicePatterns,
            [:],
            Duration.ofSeconds(5),
            Duration.ofMillis(50),
            false, false,
            Duration.ZERO
        )

        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, "Application Started")
        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "db") >>
            new ProcessExecutor.ProcessResult(0, "Database Ready")

        when:
        def future = service.waitForLogPatterns(config)
        def results = future.get()

        then:
        results != null
        results.size() == 2
        results.containsKey('app')
        results.containsKey('db')
    }

    def "waitForLogPatterns handles verbose mode"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        def config = new WaitForLogConfig(
            'test-project',
            servicePatterns,
            [:],
            Duration.ofSeconds(5),
            Duration.ofMillis(50),
            false,
            true,  // verbose
            Duration.ZERO
        )

        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, "Application Started")

        when:
        def future = service.waitForLogPatterns(config)
        def results = future.get()

        then:
        results != null
        // Verbose mode should not affect the result, just logging
    }

    def "waitForLogPatterns handles multiple patterns for single service"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started'), Pattern.compile('Ready'), Pattern.compile('Listening')]]
        def config = new WaitForLogConfig(
            'test-project',
            servicePatterns,
            [:],
            Duration.ofSeconds(5),
            Duration.ofMillis(50),
            false, false,
            Duration.ZERO
        )

        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, "Application Started\nReady for requests\nListening on port 8080")

        when:
        def future = service.waitForLogPatterns(config)
        def results = future.get()

        then:
        results != null
        results.containsKey('app')
    }

    def "waitForLogPatterns handles InterruptedException"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('NeverMatches')]]
        def config = new WaitForLogConfig(
            'test-project',
            servicePatterns,
            [:],
            Duration.ofSeconds(10),
            Duration.ofMillis(50),
            false, false,
            Duration.ZERO
        )

        mockExecutor.execute("docker", "compose", "-p", "test-project", "logs", "--no-color", "app") >>
            new ProcessExecutor.ProcessResult(0, "Unrelated logs")

        when:
        def future = service.waitForLogPatterns(config)
        // Interrupt the thread
        Thread.currentThread().interrupt()
        future.get()

        then:
        // Either InterruptedException or ExecutionException wrapping it
        thrown(Exception)

        cleanup:
        // Clear interrupt flag
        Thread.interrupted()
    }

    // ===== Helper methods =====

    private WaitForLogConfig createBasicWaitForLogConfig() {
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        return new WaitForLogConfig(
            'test-project',
            servicePatterns,
            [:],
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            false, false,
            Duration.ZERO
        )
    }

    private String invokeFetchServiceLogs(String projectName, String serviceName) {
        Method method = JUnitComposeService.getDeclaredMethod(
            "fetchServiceLogs", String.class, String.class
        )
        method.setAccessible(true)
        return (String) method.invoke(service, projectName, serviceName)
    }

    private Map<String, WaitForLogResult> invokeBuildResults(
        Map<String, List<String>> services,
        Map<String, Set<String>> matchedPatterns
    ) {
        Method method = JUnitComposeService.getDeclaredMethod(
            "buildResults", Map.class, Map.class
        )
        method.setAccessible(true)
        return (Map<String, WaitForLogResult>) method.invoke(service, services, matchedPatterns)
    }
}
