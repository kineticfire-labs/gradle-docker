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

package com.kineticfire.gradle.docker.junit

import com.kineticfire.gradle.docker.junit.service.FileService
import com.kineticfire.gradle.docker.junit.service.ProcessExecutor
import com.kineticfire.gradle.docker.junit.service.SystemPropertyService
import com.kineticfire.gradle.docker.junit.service.TimeService
import com.kineticfire.gradle.docker.model.ServiceStatus
import com.kineticfire.gradle.docker.model.WaitForLogResult
import com.kineticfire.gradle.docker.service.ComposeService
import org.junit.jupiter.api.extension.ExtensionContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.lang.reflect.Method
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for DockerComposeClassExtension waitForLog functionality.
 *
 * Covers:
 * - parseIntProperty() with various inputs including whitespace (Gap #6)
 * - parseBooleanProperty() with non-standard values (Gap #7)
 * - parseJsonMapProperty() with valid/invalid JSON
 * - performWaitForRunning() method
 * - performWaitForHealthy() method
 * - performWaitForLog() method
 * - waitForStackToBeReady() method with all wait blocks
 */
class DockerComposeClassExtensionWaitForLogTest extends Specification {

    ComposeService composeService = Mock()
    ProcessExecutor processExecutor = Mock()
    FileService fileService = Mock()
    SystemPropertyService systemPropertyService = Mock()
    TimeService timeService = Mock()
    ExtensionContext context = Mock()

    @Subject
    DockerComposeClassExtension extension

    def setup() {
        extension = new DockerComposeClassExtension(
            composeService, processExecutor, fileService, systemPropertyService, timeService
        )
    }

    // ===== parseIntProperty() tests (Gap #6: whitespace handling) =====

    def "parseIntProperty returns default when property is null"() {
        given:
        systemPropertyService.getProperty("test.property") >> null

        expect:
        invokeParseIntProperty("test.property", 42) == 42
    }

    def "parseIntProperty returns default when property is empty"() {
        given:
        systemPropertyService.getProperty("test.property") >> ""

        expect:
        invokeParseIntProperty("test.property", 42) == 42
    }

    def "parseIntProperty parses valid integer"() {
        given:
        systemPropertyService.getProperty("test.property") >> "100"

        expect:
        invokeParseIntProperty("test.property", 42) == 100
    }

    def "parseIntProperty parses negative integer"() {
        given:
        systemPropertyService.getProperty("test.property") >> "-50"

        expect:
        invokeParseIntProperty("test.property", 42) == -50
    }

    def "parseIntProperty parses zero"() {
        given:
        systemPropertyService.getProperty("test.property") >> "0"

        expect:
        invokeParseIntProperty("test.property", 42) == 0
    }

    def "parseIntProperty returns default for invalid integer"() {
        given:
        systemPropertyService.getProperty("test.property") >> "not-a-number"

        expect:
        invokeParseIntProperty("test.property", 42) == 42
    }

    def "parseIntProperty returns default for decimal value"() {
        given:
        systemPropertyService.getProperty("test.property") >> "3.14"

        expect:
        invokeParseIntProperty("test.property", 42) == 42
    }

    @Unroll
    def "parseIntProperty handles whitespace value '#value' by returning default"() {
        given:
        systemPropertyService.getProperty("test.property") >> value

        expect:
        invokeParseIntProperty("test.property", 42) == expected

        where:
        value       | expected
        "   "       | 42        // Whitespace only returns default
        " 100 "     | 42        // Leading/trailing whitespace causes parse failure
        "\t50\t"    | 42        // Tab whitespace causes parse failure
        "  "        | 42        // Multiple spaces
        "\n100\n"   | 42        // Newlines cause parse failure
    }

    def "parseIntProperty handles Integer.MAX_VALUE"() {
        given:
        systemPropertyService.getProperty("test.property") >> String.valueOf(Integer.MAX_VALUE)

        expect:
        invokeParseIntProperty("test.property", 42) == Integer.MAX_VALUE
    }

    def "parseIntProperty handles Integer.MIN_VALUE"() {
        given:
        systemPropertyService.getProperty("test.property") >> String.valueOf(Integer.MIN_VALUE)

        expect:
        invokeParseIntProperty("test.property", 42) == Integer.MIN_VALUE
    }

    // ===== parseBooleanProperty() tests (Gap #7: non-standard values) =====

    def "parseBooleanProperty returns default when property is null"() {
        given:
        systemPropertyService.getProperty("test.property") >> null

        expect:
        invokeParseBooleanProperty("test.property", true) == true
        invokeParseBooleanProperty("test.property", false) == false
    }

    def "parseBooleanProperty returns default when property is empty"() {
        given:
        systemPropertyService.getProperty("test.property") >> ""

        expect:
        invokeParseBooleanProperty("test.property", true) == true
        invokeParseBooleanProperty("test.property", false) == false
    }

    def "parseBooleanProperty parses 'true'"() {
        given:
        systemPropertyService.getProperty("test.property") >> "true"

        expect:
        invokeParseBooleanProperty("test.property", false) == true
    }

    def "parseBooleanProperty parses 'false'"() {
        given:
        systemPropertyService.getProperty("test.property") >> "false"

        expect:
        invokeParseBooleanProperty("test.property", true) == false
    }

    @Unroll
    def "parseBooleanProperty parses '#value' as #expected (Boolean.parseBoolean semantics)"() {
        given:
        systemPropertyService.getProperty("test.property") >> value

        expect:
        invokeParseBooleanProperty("test.property", !expected) == expected

        where:
        value       | expected
        "true"      | true
        "TRUE"      | true
        "True"      | true
        "TrUe"      | true
        "false"     | false
        "FALSE"     | false
        "yes"       | false   // Not recognized by Boolean.parseBoolean
        "no"        | false
        "1"         | false   // Not recognized by Boolean.parseBoolean
        "0"         | false
        "on"        | false
        "off"       | false
        "enabled"   | false
        "disabled"  | false
        "random"    | false
        " true"     | false   // Leading whitespace - not "true"
        "true "     | false   // Trailing whitespace - not "true"
    }

    // ===== parseJsonMapProperty() tests =====

    def "parseJsonMapProperty returns empty map for null input"() {
        expect:
        invokeParseJsonMapProperty(null) == [:]
    }

    def "parseJsonMapProperty returns empty map for empty string"() {
        expect:
        invokeParseJsonMapProperty("") == [:]
    }

    def "parseJsonMapProperty parses valid single-service JSON"() {
        given:
        def json = '{"app": ["Started Application"]}'

        when:
        def result = invokeParseJsonMapProperty(json)

        then:
        result == ['app': ['Started Application']]
    }

    def "parseJsonMapProperty parses valid multi-service JSON"() {
        given:
        def json = '{"app": ["Started", "Ready"], "db": ["accepting connections"]}'

        when:
        def result = invokeParseJsonMapProperty(json)

        then:
        result.size() == 2
        result['app'] == ['Started', 'Ready']
        result['db'] == ['accepting connections']
    }

    def "parseJsonMapProperty parses JSON with empty pattern list"() {
        given:
        def json = '{"app": []}'

        when:
        def result = invokeParseJsonMapProperty(json)

        then:
        result == ['app': []]
    }

    def "parseJsonMapProperty parses empty JSON object"() {
        given:
        def json = '{}'

        when:
        def result = invokeParseJsonMapProperty(json)

        then:
        result == [:]
    }

    def "parseJsonMapProperty returns empty map for invalid JSON"() {
        given:
        def json = 'not valid json'

        when:
        def result = invokeParseJsonMapProperty(json)

        then:
        result == [:]
    }

    def "parseJsonMapProperty returns empty map for JSON array instead of object"() {
        given:
        def json = '["app", "db"]'

        when:
        def result = invokeParseJsonMapProperty(json)

        then:
        result == [:]
    }

    def "parseJsonMapProperty handles nested objects by skipping non-list values"() {
        given:
        def json = '{"app": ["Started"], "nested": {"key": "value"}}'

        when:
        def result = invokeParseJsonMapProperty(json)

        then:
        result == ['app': ['Started']]  // nested object is skipped
    }

    def "parseJsonMapProperty handles special characters in patterns"() {
        given:
        def json = '{"app": ["\\\\[INFO\\\\].*started", "port:\\\\s+\\\\d+"]}'

        when:
        def result = invokeParseJsonMapProperty(json)

        then:
        result['app'].size() == 2
        result['app'][0] == '\\[INFO\\].*started'
        result['app'][1] == 'port:\\s+\\d+'
    }

    def "parseJsonMapProperty converts non-string list items to strings"() {
        given:
        def json = '{"app": [123, true, "text"]}'

        when:
        def result = invokeParseJsonMapProperty(json)

        then:
        result['app'] == ['123', 'true', 'text']
    }

    // ===== performWaitForRunning() tests =====

    def "performWaitForRunning returns early when services property is null"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForRunning.services") >> null

        when:
        invokePerformWaitForRunning("test-project")

        then:
        0 * composeService.waitForServices(_)
    }

    def "performWaitForRunning returns early when services property is empty"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForRunning.services") >> ""

        when:
        invokePerformWaitForRunning("test-project")

        then:
        0 * composeService.waitForServices(_)
    }

    def "performWaitForRunning calls waitForServices with correct config"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForRunning.services") >> "app,db"
        systemPropertyService.getProperty("docker.compose.waitForRunning.timeoutSeconds") >> "120"
        systemPropertyService.getProperty("docker.compose.waitForRunning.pollSeconds") >> "5"
        composeService.waitForServices(_) >> CompletableFuture.completedFuture(ServiceStatus.RUNNING)

        when:
        invokePerformWaitForRunning("test-project")

        then:
        1 * composeService.waitForServices({ config ->
            config.projectName == "test-project" &&
            config.services == ["app", "db"] &&
            config.timeout == Duration.ofSeconds(120) &&
            config.pollInterval == Duration.ofSeconds(5) &&
            config.targetState == ServiceStatus.RUNNING
        }) >> CompletableFuture.completedFuture(ServiceStatus.RUNNING)
    }

    def "performWaitForRunning uses default timeout and poll values"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForRunning.services") >> "app"
        systemPropertyService.getProperty("docker.compose.waitForRunning.timeoutSeconds") >> null
        systemPropertyService.getProperty("docker.compose.waitForRunning.pollSeconds") >> null
        composeService.waitForServices(_) >> CompletableFuture.completedFuture(ServiceStatus.RUNNING)

        when:
        invokePerformWaitForRunning("test-project")

        then:
        1 * composeService.waitForServices({ config ->
            config.timeout == Duration.ofSeconds(60) &&  // default
            config.pollInterval == Duration.ofSeconds(2)  // default
        }) >> CompletableFuture.completedFuture(ServiceStatus.RUNNING)
    }

    // ===== performWaitForHealthy() tests =====

    def "performWaitForHealthy returns early when services property is null"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> null

        when:
        invokePerformWaitForHealthy("test-project")

        then:
        0 * composeService.waitForServices(_)
    }

    def "performWaitForHealthy returns early when services property is empty"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> ""

        when:
        invokePerformWaitForHealthy("test-project")

        then:
        0 * composeService.waitForServices(_)
    }

    def "performWaitForHealthy calls waitForServices with HEALTHY target state"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> "app,db"
        systemPropertyService.getProperty("docker.compose.waitForHealthy.timeoutSeconds") >> "90"
        systemPropertyService.getProperty("docker.compose.waitForHealthy.pollSeconds") >> "3"
        composeService.waitForServices(_) >> CompletableFuture.completedFuture(ServiceStatus.HEALTHY)

        when:
        invokePerformWaitForHealthy("test-project")

        then:
        1 * composeService.waitForServices({ config ->
            config.projectName == "test-project" &&
            config.services == ["app", "db"] &&
            config.timeout == Duration.ofSeconds(90) &&
            config.pollInterval == Duration.ofSeconds(3) &&
            config.targetState == ServiceStatus.HEALTHY
        }) >> CompletableFuture.completedFuture(ServiceStatus.HEALTHY)
    }

    // ===== performWaitForLog() tests =====

    def "performWaitForLog returns early when services JSON is null"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> null

        when:
        invokePerformWaitForLog("test-project")

        then:
        0 * composeService.waitForLogPatterns(_)
    }

    def "performWaitForLog returns early when services JSON is empty"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> ""

        when:
        invokePerformWaitForLog("test-project")

        then:
        0 * composeService.waitForLogPatterns(_)
    }

    def "performWaitForLog returns early when services JSON is empty object"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> "{}"

        when:
        invokePerformWaitForLog("test-project")

        then:
        0 * composeService.waitForLogPatterns(_)
    }

    def "performWaitForLog calls waitForLogPatterns with correct config"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> '{"app": ["Started"]}'
        systemPropertyService.getProperty("docker.compose.waitForLog.rejectPatterns") >> '{"app": ["Error"]}'
        systemPropertyService.getProperty("docker.compose.waitForLog.timeoutSeconds") >> "120"
        systemPropertyService.getProperty("docker.compose.waitForLog.pollSeconds") >> "5"
        systemPropertyService.getProperty("docker.compose.waitForLog.caseInsensitive") >> "true"
        systemPropertyService.getProperty("docker.compose.waitForLog.verbose") >> "true"
        systemPropertyService.getProperty("docker.compose.waitForLog.progressIntervalSeconds") >> "10"

        def mockResult = ['app': createWaitForLogResult('app')]
        composeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture(mockResult)

        when:
        invokePerformWaitForLog("test-project")

        then:
        1 * composeService.waitForLogPatterns({ config ->
            config.projectName == "test-project" &&
            config.timeout == Duration.ofSeconds(120) &&
            config.pollInterval == Duration.ofSeconds(5) &&
            config.caseInsensitive == true &&
            config.verbose == true &&
            config.progressInterval == Duration.ofSeconds(10)
        }) >> CompletableFuture.completedFuture(mockResult)
    }

    def "performWaitForLog uses default values when properties not set"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> '{"app": ["Started"]}'
        systemPropertyService.getProperty("docker.compose.waitForLog.rejectPatterns") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.timeoutSeconds") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.pollSeconds") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.caseInsensitive") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.verbose") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.progressIntervalSeconds") >> null

        def mockResult = ['app': createWaitForLogResult('app')]
        composeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture(mockResult)

        when:
        invokePerformWaitForLog("test-project")

        then:
        1 * composeService.waitForLogPatterns({ config ->
            config.timeout == Duration.ofSeconds(60) &&       // default
            config.pollInterval == Duration.ofSeconds(2) &&   // default
            config.caseInsensitive == false &&                // default
            config.verbose == false &&                        // default
            config.progressInterval == Duration.ZERO          // default (0 seconds)
        }) >> CompletableFuture.completedFuture(mockResult)
    }

    def "performWaitForLog handles empty parsed services map"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> "invalid json"

        when:
        invokePerformWaitForLog("test-project")

        then:
        0 * composeService.waitForLogPatterns(_)
    }

    // ===== waitForStackToBeReady() tests =====

    def "waitForStackToBeReady executes all wait blocks in order"() {
        given:
        // Setup for waitForRunning
        systemPropertyService.getProperty("docker.compose.waitForRunning.services") >> "app"
        systemPropertyService.getProperty("docker.compose.waitForRunning.timeoutSeconds") >> "60"
        systemPropertyService.getProperty("docker.compose.waitForRunning.pollSeconds") >> "2"

        // Setup for waitForHealthy
        systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> "app"
        systemPropertyService.getProperty("docker.compose.waitForHealthy.timeoutSeconds") >> "60"
        systemPropertyService.getProperty("docker.compose.waitForHealthy.pollSeconds") >> "2"

        // Setup for waitForLog
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> '{"app": ["Started"]}'
        systemPropertyService.getProperty("docker.compose.waitForLog.rejectPatterns") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.timeoutSeconds") >> "60"
        systemPropertyService.getProperty("docker.compose.waitForLog.pollSeconds") >> "2"
        systemPropertyService.getProperty("docker.compose.waitForLog.caseInsensitive") >> "false"
        systemPropertyService.getProperty("docker.compose.waitForLog.verbose") >> "false"
        systemPropertyService.getProperty("docker.compose.waitForLog.progressIntervalSeconds") >> "0"

        def mockLogResult = ['app': createWaitForLogResult('app')]

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        1 * composeService.waitForServices({ it.targetState == ServiceStatus.RUNNING }) >>
            CompletableFuture.completedFuture(ServiceStatus.RUNNING)

        then:
        1 * composeService.waitForServices({ it.targetState == ServiceStatus.HEALTHY }) >>
            CompletableFuture.completedFuture(ServiceStatus.HEALTHY)

        then:
        1 * composeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture(mockLogResult)
    }

    def "waitForStackToBeReady skips unconfigured wait blocks"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForRunning.services") >> null
        systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> null

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        0 * composeService.waitForServices(_)
        0 * composeService.waitForLogPatterns(_)
    }

    def "waitForStackToBeReady executes only waitForRunning when only it is configured"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForRunning.services") >> "app"
        systemPropertyService.getProperty("docker.compose.waitForRunning.timeoutSeconds") >> "60"
        systemPropertyService.getProperty("docker.compose.waitForRunning.pollSeconds") >> "2"
        systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> null

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        1 * composeService.waitForServices({ it.targetState == ServiceStatus.RUNNING }) >>
            CompletableFuture.completedFuture(ServiceStatus.RUNNING)
        0 * composeService.waitForLogPatterns(_)
    }

    def "waitForStackToBeReady executes only waitForLog when only it is configured"() {
        given:
        systemPropertyService.getProperty("docker.compose.waitForRunning.services") >> null
        systemPropertyService.getProperty("docker.compose.waitForHealthy.services") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.services") >> '{"app": ["Ready"]}'
        systemPropertyService.getProperty("docker.compose.waitForLog.rejectPatterns") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.timeoutSeconds") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.pollSeconds") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.caseInsensitive") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.verbose") >> null
        systemPropertyService.getProperty("docker.compose.waitForLog.progressIntervalSeconds") >> null

        def mockResult = ['app': createWaitForLogResult('app')]

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        0 * composeService.waitForServices(_)
        1 * composeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture(mockResult)
    }

    // ===== Helper methods for invoking private methods via reflection =====

    private int invokeParseIntProperty(String propertyName, int defaultValue) {
        Method method = DockerComposeClassExtension.getDeclaredMethod(
            "parseIntProperty", String.class, int.class
        )
        method.setAccessible(true)
        return (int) method.invoke(extension, propertyName, defaultValue)
    }

    private boolean invokeParseBooleanProperty(String propertyName, boolean defaultValue) {
        Method method = DockerComposeClassExtension.getDeclaredMethod(
            "parseBooleanProperty", String.class, boolean.class
        )
        method.setAccessible(true)
        return (boolean) method.invoke(extension, propertyName, defaultValue)
    }

    private Map<String, List<String>> invokeParseJsonMapProperty(String json) {
        Method method = DockerComposeClassExtension.getDeclaredMethod(
            "parseJsonMapProperty", String.class
        )
        method.setAccessible(true)
        // Cast to Object array to properly handle null values in reflection
        return (Map<String, List<String>>) method.invoke(extension, [json] as Object[])
    }

    private void invokePerformWaitForRunning(String projectName) {
        Method method = DockerComposeClassExtension.getDeclaredMethod(
            "performWaitForRunning", String.class
        )
        method.setAccessible(true)
        method.invoke(extension, projectName)
    }

    private void invokePerformWaitForHealthy(String projectName) {
        Method method = DockerComposeClassExtension.getDeclaredMethod(
            "performWaitForHealthy", String.class
        )
        method.setAccessible(true)
        method.invoke(extension, projectName)
    }

    private void invokePerformWaitForLog(String projectName) {
        Method method = DockerComposeClassExtension.getDeclaredMethod(
            "performWaitForLog", String.class
        )
        method.setAccessible(true)
        method.invoke(extension, projectName)
    }

    private void invokeWaitForStackToBeReady(String stackName, String projectName) {
        Method method = DockerComposeClassExtension.getDeclaredMethod(
            "waitForStackToBeReady", String.class, String.class
        )
        method.setAccessible(true)
        method.invoke(extension, stackName, projectName)
    }

    private WaitForLogResult createWaitForLogResult(String serviceName) {
        return new WaitForLogResult(serviceName, [], true)
    }
}
