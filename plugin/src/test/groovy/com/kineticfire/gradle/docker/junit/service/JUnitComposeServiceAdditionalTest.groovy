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

/**
 * Additional unit tests for JUnitComposeService to achieve 100% coverage.
 * These tests focus on private methods and edge cases not covered in the main test file.
 */
class JUnitComposeServiceAdditionalTest extends Specification {

    @Subject
    JUnitComposeService service

    ProcessExecutor mockExecutor

    def setup() {
        mockExecutor = Mock(ProcessExecutor)
        service = new JUnitComposeService(mockExecutor)
    }

    // Tests for getStackServices private method

    def "getStackServices returns empty map when process execution fails"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(1, "Command failed")

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.isEmpty()
    }

    def "getStackServices returns empty map when output is null"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, null)

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.isEmpty()
    }

    def "getStackServices returns empty map when output is empty string"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, "")

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.isEmpty()
    }

    def "getStackServices returns empty map when output is only whitespace"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, "   \n  \t  ")

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.isEmpty()
    }

    def "getStackServices parses single service correctly"() {
        given:
        String jsonOutput = '{"ID":"abc123","Service":"web","Name":"project_web_1","State":"running","Ports":"0.0.0.0:8080->80/tcp"}'
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, jsonOutput)

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.size() == 1
        result.containsKey("web")
        result["web"].containerId == "abc123"
        result["web"].containerName == "web"
        result["web"].state == "running"
    }

    def "getStackServices parses multiple services correctly"() {
        given:
        String jsonOutput = """
{"ID":"abc123","Service":"web","State":"running","Ports":"0.0.0.0:8080->80/tcp"}
{"ID":"def456","Service":"db","State":"running","Ports":"0.0.0.0:5432->5432/tcp"}
"""
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, jsonOutput)

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.size() == 2
        result.containsKey("web")
        result.containsKey("db")
    }

    def "getStackServices handles service name from Name field when Service is missing"() {
        given:
        String jsonOutput = '{"ID":"abc123","Name":"project_web_1","State":"running"}'
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, jsonOutput)

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.size() == 1
        result.containsKey("web")
    }

    def "getStackServices handles missing ID field"() {
        given:
        String jsonOutput = '{"Service":"web","State":"running"}'
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, jsonOutput)

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.size() == 1
        result["web"].containerId == "unknown"
    }

    def "getStackServices skips line when service name cannot be determined"() {
        given:
        String jsonOutput = '{"ID":"abc123","State":"running"}'
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, jsonOutput)

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.isEmpty()
    }

    def "getStackServices handles invalid JSON line gracefully"() {
        given:
        String jsonOutput = """
{"ID":"abc123","Service":"web","State":"running"}
{invalid json line}
{"ID":"def456","Service":"db","State":"running"}
"""
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, jsonOutput)

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.size() == 2
        result.containsKey("web")
        result.containsKey("db")
    }

    def "getStackServices handles exception during execution"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            { throw new IOException("Process execution failed") }

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.isEmpty()
    }

    def "getStackServices handles Status field when State is missing"() {
        given:
        String jsonOutput = '{"ID":"abc123","Service":"web","Status":"Up 2 hours"}'
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, jsonOutput)

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.size() == 1
        result["web"].state == "running"
    }

    def "getStackServices parses ports correctly"() {
        given:
        String jsonOutput = '{"ID":"abc123","Service":"web","State":"running","Ports":"0.0.0.0:8080->80/tcp, :::8080->80/tcp"}'
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, jsonOutput)

        when:
        Map<String, ServiceInfo> result = invokeGetStackServices("test-project")

        then:
        result.size() == 1
        result["web"].publishedPorts.size() == 2
    }

    // Tests for checkServiceReady private method

    def "checkServiceReady returns true when service is running and target is RUNNING"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Up 2 minutes")

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.RUNNING)

        then:
        result == true
    }

    def "checkServiceReady returns true when service contains 'up' and target is RUNNING"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     up")

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.RUNNING)

        then:
        result == true
    }

    def "checkServiceReady returns true when service is healthy and target is HEALTHY"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Up 2 minutes (healthy)")

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.HEALTHY)

        then:
        result == true
    }

    def "checkServiceReady returns false when service is running but target is HEALTHY"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Up 2 minutes")

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.HEALTHY)

        then:
        result == false
    }

    def "checkServiceReady returns false when service is not running"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Exited (0)")

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.RUNNING)

        then:
        result == false
    }

    def "checkServiceReady returns false when process execution fails"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            new ProcessExecutor.ProcessResult(1, "Error")

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.RUNNING)

        then:
        result == false
    }

    def "checkServiceReady returns false when output is null"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            new ProcessExecutor.ProcessResult(0, null)

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.RUNNING)

        then:
        result == false
    }

    def "checkServiceReady returns false when exception occurs"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            { throw new IOException("Process failed") }

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.RUNNING)

        then:
        result == false
    }

    def "checkServiceReady returns false for STOPPED target state"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Up 2 minutes")

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.STOPPED)

        then:
        result == false
    }

    def "checkServiceReady handles InterruptedException"() {
        given:
        mockExecutor.execute("docker", "compose", "-p", "test-project", "ps", "web", "--format", "table") >>
            { throw new InterruptedException("Interrupted") }

        when:
        boolean result = invokeCheckServiceReady("test-project", "web", ServiceStatus.RUNNING)

        then:
        result == false
    }

    // Additional tests for parseServiceState edge cases

    def "parseServiceState handles mixed case status strings"() {
        expect:
        invokeParseServiceState("Running (Healthy)") == ServiceStatus.HEALTHY
        invokeParseServiceState("RUNNING") == ServiceStatus.RUNNING
        invokeParseServiceState("UP") == ServiceStatus.RUNNING
        invokeParseServiceState("ExITeD (1)") == ServiceStatus.STOPPED
        invokeParseServiceState("RESTARTING") == ServiceStatus.RESTARTING
    }

    def "parseServiceState handles status with additional text"() {
        expect:
        invokeParseServiceState("running for 5 minutes") == ServiceStatus.RUNNING
        invokeParseServiceState("up 2 hours") == ServiceStatus.RUNNING
        invokeParseServiceState("exited (0) 3 seconds ago") == ServiceStatus.STOPPED
        invokeParseServiceState("restarting since 1 minute ago") == ServiceStatus.RESTARTING
    }

    def "parseServiceState returns UNKNOWN for null or empty input"() {
        expect:
        invokeParseServiceState(null) == ServiceStatus.UNKNOWN
        invokeParseServiceState("") == ServiceStatus.UNKNOWN
        invokeParseServiceState("   ") == ServiceStatus.UNKNOWN
    }

    def "parseServiceState returns UNKNOWN for unrecognized statuses"() {
        expect:
        invokeParseServiceState("created") == ServiceStatus.UNKNOWN
        invokeParseServiceState("paused") == ServiceStatus.UNKNOWN
        invokeParseServiceState("dead") == ServiceStatus.UNKNOWN
        invokeParseServiceState("removing") == ServiceStatus.UNKNOWN
        invokeParseServiceState("unknown-status") == ServiceStatus.UNKNOWN
    }

    // Additional tests for parsePortMappings edge cases

    def "parsePortMappings handles IPv6 address format"() {
        when:
        List<PortMapping> result = invokeParsePortMappings(":::8080->80/tcp")

        then:
        result.size() == 1
        result[0].hostPort == 8080
        result[0].containerPort == 80
        result[0].protocol == "tcp"
    }

    def "parsePortMappings handles UDP protocol"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("0.0.0.0:5353->5353/udp")

        then:
        result.size() == 1
        result[0].hostPort == 5353
        result[0].containerPort == 5353
        result[0].protocol == "udp"
    }

    def "parsePortMappings handles mix of protocols"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("8080->80/tcp, 5353->5353/udp")

        then:
        result.size() == 2
        result[0].protocol == "tcp"
        result[1].protocol == "udp"
    }

    def "parsePortMappings handles whitespace variations"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("  8080->80/tcp  ,  9090->90/tcp  ")

        then:
        result.size() == 2
        result[0].hostPort == 8080
        result[1].hostPort == 9090
    }

    def "parsePortMappings handles empty entries in list"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("8080->80/tcp, , 9090->90/tcp")

        then:
        result.size() == 2
        result[0].hostPort == 8080
        result[1].hostPort == 9090
    }

    def "parsePortMappings handles port mapping without IP prefix"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("3000->3000")

        then:
        result.size() == 1
        result[0].hostPort == 3000
        result[0].containerPort == 3000
        result[0].protocol == "tcp"
    }

    def "parsePortMappings handles exception during number parsing"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("abc->80/tcp")

        then:
        result.isEmpty()
    }

    def "parsePortMappings handles malformed port entries"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("->80/tcp, 8080->/tcp, 8080->")

        then:
        result.isEmpty()
    }

    def "parsePortMappings handles very large port numbers"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("65535->65535/tcp")

        then:
        result.size() == 1
        result[0].hostPort == 65535
        result[0].containerPort == 65535
    }

    def "parsePortMappings handles ports with specific IP addresses"() {
        when:
        List<PortMapping> result = invokeParsePortMappings("192.168.1.1:8080->80/tcp")

        then:
        result.size() == 1
        result[0].hostPort == 8080
        result[0].containerPort == 80
    }

    // Helper methods to invoke private methods via reflection

    private Map<String, ServiceInfo> invokeGetStackServices(String projectName) {
        Method method = JUnitComposeService.getDeclaredMethod("getStackServices", String.class)
        method.setAccessible(true)
        return (Map<String, ServiceInfo>) method.invoke(service, projectName)
    }

    private boolean invokeCheckServiceReady(String projectName, String serviceName, ServiceStatus targetState) {
        Method method = JUnitComposeService.getDeclaredMethod("checkServiceReady", String.class, String.class, ServiceStatus.class)
        method.setAccessible(true)
        return (boolean) method.invoke(service, projectName, serviceName, targetState)
    }

    private ServiceStatus invokeParseServiceState(String status) {
        Method method = JUnitComposeService.getDeclaredMethod("parseServiceState", String.class)
        method.setAccessible(true)
        return (ServiceStatus) method.invoke(service, [status] as Object[])
    }

    private List<PortMapping> invokeParsePortMappings(String portsString) {
        Method method = JUnitComposeService.getDeclaredMethod("parsePortMappings", String.class)
        method.setAccessible(true)
        return (List<PortMapping>) method.invoke(service, [portsString] as Object[])
    }
}
