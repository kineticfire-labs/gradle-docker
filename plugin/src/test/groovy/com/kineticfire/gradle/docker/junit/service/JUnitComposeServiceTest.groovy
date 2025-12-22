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

import com.kineticfire.gradle.docker.model.*
import spock.lang.Specification
import spock.lang.Subject

import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for JUnitComposeService.
 *
 * UNIT TEST GAP DOCUMENTATION:
 * This class cannot be fully unit tested due to its implementation using CompletableFuture.supplyAsync()
 * which creates real threads that cannot be effectively mocked in unit tests.
 *
 * Coverage Impact:
 * - JUnitComposeService: ~664 instructions (includes generated closures)
 * - Package junit.service total: 2,181 instructions
 * - Unit test coverage improved with null checks and private method tests
 *
 * Alternative Coverage:
 * - Integration tests fully exercise this class's functionality
 * - See: plugin-integration-test/docker/ test scenarios
 *
 * Justification for Gap:
 * 1. CompletableFuture.supplyAsync() creates real background threads
 * 2. Mocking async operations leads to test hangs and race conditions
 * 3. The service layer is an impure boundary component (async I/O, process execution)
 * 4. Refactoring to make it unit-testable would require significant architectural changes
 *    that compromise its purpose as a convenience wrapper for async Docker Compose operations
 *
 * This test file covers:
 * - Constructor tests
 * - Null parameter validation (synchronous checks before async execution)
 * - Private helper method tests via reflection
 */
class JUnitComposeServiceTest extends Specification {

    @Subject
    JUnitComposeService service

    ProcessExecutor mockExecutor

    def setup() {
        mockExecutor = Mock(ProcessExecutor)
        service = new JUnitComposeService(mockExecutor)
    }

    def "constructor creates instance"() {
        when:
        JUnitComposeService defaultService = new JUnitComposeService()

        then:
        defaultService != null
    }

    def "constructor with executor creates instance"() {
        given:
        ProcessExecutor executor = Mock(ProcessExecutor)

        when:
        JUnitComposeService customService = new JUnitComposeService(executor)

        then:
        customService != null
    }

    def "upStack throws NullPointerException for null config"() {
        when:
        service.upStack(null)

        then:
        thrown(NullPointerException)
    }

    def "downStack with String throws NullPointerException for null project name"() {
        when:
        service.downStack((String) null)

        then:
        thrown(NullPointerException)
    }

    def "downStack with ComposeConfig throws NullPointerException for null config"() {
        when:
        service.downStack((ComposeConfig) null)

        then:
        thrown(NullPointerException)
    }

    def "waitForServices throws NullPointerException for null config"() {
        when:
        service.waitForServices(null)

        then:
        thrown(NullPointerException)
    }

    def "captureLogs throws NullPointerException for null project name"() {
        when:
        service.captureLogs((String) null, new LogsConfig(Collections.emptyList(), 0, false))

        then:
        thrown(NullPointerException)
    }

    def "captureLogs throws NullPointerException for null logs config"() {
        when:
        service.captureLogs("test-project", (LogsConfig) null)

        then:
        thrown(NullPointerException)
    }

    // Tests for private helper methods via reflection

    def "parseServiceState returns HEALTHY for status containing 'healthy' with 'running' or 'up'"() {
        expect:
        invokeParseServiceState(status) == ServiceStatus.HEALTHY

        where:
        status << ["running (healthy)", "Up (healthy)", "running healthy"]
    }

    def "parseServiceState returns RUNNING for status containing 'running' without 'healthy'"() {
        expect:
        invokeParseServiceState(status) == ServiceStatus.RUNNING

        where:
        status << ["running", "up", "Up", "Running"]
    }

    def "parseServiceState returns STOPPED for status containing 'exit' or 'stop'"() {
        expect:
        invokeParseServiceState(status) == ServiceStatus.STOPPED

        where:
        status << ["exit", "exited", "stopped", "Exited (0)", "stop", "STOP", "EXIT"]
    }

    def "parseServiceState returns RESTARTING for status containing 'restart'"() {
        expect:
        invokeParseServiceState(status) == ServiceStatus.RESTARTING

        where:
        status << ["restarting", "Restarting", "restart pending", "restart", "RESTART"]
    }

    def "parseServiceState returns UNKNOWN for unrecognized status"() {
        expect:
        invokeParseServiceState(status) == ServiceStatus.UNKNOWN

        where:
        status << ["created", "paused", "dead", "removing", "healthy", ""]
    }

    def "parseServiceState handles null status"() {
        expect:
        invokeParseServiceState(null) == ServiceStatus.UNKNOWN
    }

    def "parseServiceState handles empty status"() {
        expect:
        invokeParseServiceState("") == ServiceStatus.UNKNOWN
    }

    def "parsePortMappings parses valid port string"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("0.0.0.0:8080->80/tcp")

        then:
        result.size() == 1
        result[0].hostPort == 8080
        result[0].containerPort == 80
        result[0].protocol == "tcp"
    }

    def "parsePortMappings parses multiple ports"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("0.0.0.0:8080->80/tcp, :::8080->80/tcp, 0.0.0.0:9090->90/udp")

        then:
        result.size() == 3
        result[0].hostPort == 8080
        result[0].containerPort == 80
        result[0].protocol == "tcp"
        result[1].hostPort == 8080
        result[1].containerPort == 80
        result[1].protocol == "tcp"
        result[2].hostPort == 9090
        result[2].containerPort == 90
        result[2].protocol == "udp"
    }

    def "parsePortMappings handles port string without IP"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("9091->8080/tcp")

        then:
        result.size() == 1
        result[0].hostPort == 9091
        result[0].containerPort == 8080
        result[0].protocol == "tcp"
    }

    def "parsePortMappings handles port string without protocol"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("0.0.0.0:8080->80")

        then:
        result.size() == 1
        result[0].hostPort == 8080
        result[0].containerPort == 80
        result[0].protocol == "tcp"  // Defaults to tcp
    }

    def "parsePortMappings returns empty list for null input"() {
        expect:
        invokeParsePortMappings(null).isEmpty()
    }

    def "parsePortMappings returns empty list for empty string"() {
        expect:
        invokeParsePortMappings("").isEmpty()
    }

    def "parsePortMappings handles invalid port format gracefully"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("invalid-port-format, 0.0.0.0:8080->80/tcp")

        then:
        result.size() == 1  // Only the valid one is parsed
        result[0].hostPort == 8080
    }

    def "parsePortMappings handles exception in parsing"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("not-a-valid-port-at-all")

        then:
        result.isEmpty()  // Should handle exception gracefully
    }

    // Helper methods to invoke private methods via reflection

    private ServiceStatus invokeParseServiceState(String status) {
        Method method = JUnitComposeService.getDeclaredMethod("parseServiceState", String.class)
        method.setAccessible(true)
        // Use Object array to properly pass null parameter in Groovy
        return (ServiceStatus) method.invoke(service, [status] as Object[])
    }

    private List<PortMapping> invokeParsePortMappings(String portsString) {
        Method method = JUnitComposeService.getDeclaredMethod("parsePortMappings", String.class)
        method.setAccessible(true)
        // Use Object array to properly pass null parameter in Groovy
        return (List<PortMapping>) method.invoke(service, [portsString] as Object[])
    }
}
