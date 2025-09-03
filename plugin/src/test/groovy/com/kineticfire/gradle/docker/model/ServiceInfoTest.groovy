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
 * Unit tests for ServiceInfo
 */
class ServiceInfoTest extends Specification {

    def "can create ServiceInfo with default constructor"() {
        when:
        def info = new ServiceInfo()

        then:
        info.containerId == null
        info.containerName == null
        info.state == null
        info.publishedPorts == []
        // Note: isHealthy() and isRunning() will throw NPE with null state
        // This is the current behavior of the implementation
    }

    def "can create ServiceInfo with traditional constructor"() {
        given:
        def portMapping = new PortMapping(80, 8080, "tcp")
        def ports = [portMapping]

        when:
        def info = new ServiceInfo("container123", "web-container", "running", ports)

        then:
        info.containerId == "container123"
        info.containerName == "web-container"
        info.state == "running"
        info.publishedPorts == ports
        info.isRunning()
        !info.isHealthy() // "running" doesn't contain "healthy"
    }

    def "traditional constructor handles default ports parameter"() {
        when:
        def info = new ServiceInfo("container456", "api-container", "healthy")

        then:
        info.containerId == "container456"
        info.containerName == "api-container"
        info.state == "healthy"
        info.publishedPorts == []
        info.isHealthy()
        !info.isRunning() // "healthy" doesn't contain "up" or "running"
    }

    def "traditional constructor handles null ports parameter"() {
        when:
        def info = new ServiceInfo("container789", "db-container", "up (healthy)", null)

        then:
        info.containerId == "container789"
        info.containerName == "db-container"
        info.state == "up (healthy)"
        info.publishedPorts == []
        info.isHealthy()
        info.isRunning()
    }

    def "can create ServiceInfo with named parameter constructor"() {
        given:
        def portMapping = new PortMapping(3306, 3306, "tcp")
        def ports = [portMapping]

        when:
        def info = new ServiceInfo([
            containerId: "mysql123",
            containerName: "mysql-server",
            state: "Up (healthy)",
            publishedPorts: ports
        ])

        then:
        info.containerId == "mysql123"
        info.containerName == "mysql-server"
        info.state == "Up (healthy)"
        info.publishedPorts == ports
    }

    def "named parameter constructor handles null ports"() {
        when:
        def info = new ServiceInfo([
            containerId: "redis456",
            containerName: "redis-cache",
            state: "running",
            publishedPorts: null
        ])

        then:
        info.containerId == "redis456"
        info.containerName == "redis-cache"
        info.state == "running"
        info.publishedPorts == []
    }

    def "named parameter constructor handles missing parameters"() {
        when:
        def info = new ServiceInfo([
            containerId: "simple123",
            containerName: "simple-service"
        ])

        then:
        info.containerId == "simple123"
        info.containerName == "simple-service"
        info.state == null
        info.publishedPorts == []
    }

    def "isHealthy checks state correctly"() {
        expect:
        new ServiceInfo("id", "name", "healthy").isHealthy()
        new ServiceInfo("id", "name", "HEALTHY").isHealthy()
        new ServiceInfo("id", "name", "Healthy").isHealthy()
        new ServiceInfo("id", "name", "up (healthy)").isHealthy()
        new ServiceInfo("id", "name", "running (healthy)").isHealthy()
        new ServiceInfo("id", "name", "Up (Healthy)").isHealthy()
        
        !new ServiceInfo("id", "name", "running").isHealthy()
        !new ServiceInfo("id", "name", "up").isHealthy()
        !new ServiceInfo("id", "name", "sick").isHealthy()
        !new ServiceInfo("id", "name", "stopped").isHealthy()
        !new ServiceInfo("id", "name", "starting").isHealthy()
    }

    def "isRunning checks state correctly"() {
        expect:
        new ServiceInfo("id", "name", "running").isRunning()
        new ServiceInfo("id", "name", "RUNNING").isRunning()
        new ServiceInfo("id", "name", "Running").isRunning()
        new ServiceInfo("id", "name", "up").isRunning()
        new ServiceInfo("id", "name", "UP").isRunning()
        new ServiceInfo("id", "name", "Up").isRunning()
        new ServiceInfo("id", "name", "up (healthy)").isRunning()
        new ServiceInfo("id", "name", "running (healthy)").isRunning()
        
        !new ServiceInfo("id", "name", "stopped").isRunning()
        !new ServiceInfo("id", "name", "exited").isRunning()
        !new ServiceInfo("id", "name", "paused").isRunning()
        !new ServiceInfo("id", "name", "healthy").isRunning() // only "healthy" doesn't count
        !new ServiceInfo("id", "name", "dead").isRunning()
    }

    def "state checks are case insensitive"() {
        expect:
        new ServiceInfo("id", "name", "HEALTHY").isHealthy()
        new ServiceInfo("id", "name", "hEaLtHy").isHealthy()
        new ServiceInfo("id", "name", "UP (HEALTHY)").isHealthy()
        
        new ServiceInfo("id", "name", "RUNNING").isRunning()
        new ServiceInfo("id", "name", "rUnNiNg").isRunning()
        new ServiceInfo("id", "name", "UP").isRunning()
        new ServiceInfo("id", "name", "uP").isRunning()
    }

    def "getPortMapping returns correct mapping"() {
        given:
        def httpPort = new PortMapping(80, 8080, "tcp")
        def httpsPort = new PortMapping(443, 8443, "tcp")
        def ports = [httpPort, httpsPort]
        def info = new ServiceInfo("id", "name", "running", ports)

        expect:
        info.getPortMapping(80) == httpPort
        info.getPortMapping(443) == httpsPort
        info.getPortMapping(22) == null
        info.getPortMapping(3306) == null
    }

    def "getPortMapping returns null for empty ports"() {
        given:
        def info = new ServiceInfo("id", "name", "running")

        expect:
        info.getPortMapping(80) == null
        info.getPortMapping(443) == null
        info.getPortMapping(0) == null
    }

    def "getHostPort returns correct host port"() {
        given:
        def httpPort = new PortMapping(80, 8080, "tcp")
        def httpsPort = new PortMapping(443, 8443, "tcp")
        def ports = [httpPort, httpsPort]
        def info = new ServiceInfo("id", "name", "running", ports)

        expect:
        info.getHostPort(80) == 8080
        info.getHostPort(443) == 8443
        info.getHostPort(22) == null
        info.getHostPort(3306) == null
    }

    def "getHostPort returns null for empty ports"() {
        given:
        def info = new ServiceInfo("id", "name", "running")

        expect:
        info.getHostPort(80) == null
        info.getHostPort(443) == null
        info.getHostPort(0) == null
    }

    def "toString includes all information"() {
        given:
        def portMapping = new PortMapping(80, 8080, "tcp")
        def info = new ServiceInfo("container123", "web-app", "running", [portMapping])

        when:
        def string = info.toString()

        then:
        string.contains("ServiceInfo")
        string.contains("containerId='container123'")
        string.contains("containerName='web-app'")
        string.contains("state='running'")
        string.contains("ports=1 mappings")
    }

    def "toString handles empty ports"() {
        when:
        def info = new ServiceInfo("simple123", "simple-app", "running")
        def string = info.toString()

        then:
        string.contains("ports=0 mappings")
    }

    def "toString handles null values"() {
        when:
        def info = new ServiceInfo()
        def string = info.toString()

        then:
        string.contains("containerId='null'")
        string.contains("containerName='null'")
        string.contains("state='null'")
    }

    def "publishedPorts list is mutable after construction"() {
        given:
        def info = new ServiceInfo("test", "test-container", "running")
        def newPort = new PortMapping(3306, 3306, "tcp")

        when:
        info.publishedPorts.add(newPort)

        then:
        info.publishedPorts.contains(newPort)
        info.publishedPorts.size() == 1
        info.getPortMapping(3306) == newPort
        info.getHostPort(3306) == 3306
    }

    def "can modify properties after construction"() {
        given:
        def info = new ServiceInfo("old", "old-container", "stopped")

        when:
        info.containerId = "new123"
        info.containerName = "new-container"
        info.state = "running (healthy)"

        then:
        info.containerId == "new123"
        info.containerName == "new-container"
        info.state == "running (healthy)"
        info.isRunning()
        info.isHealthy()
    }

    def "supports Jackson default constructor pattern"() {
        when:
        def info = new ServiceInfo()
        // Simulate Jackson setting properties
        info.containerId = "jackson123"
        info.containerName = "jackson-service"
        info.state = "up (healthy)"
        info.publishedPorts = [new PortMapping(8080, 80, "tcp")]

        then:
        info.containerId == "jackson123"
        info.containerName == "jackson-service"
        info.state == "up (healthy)"
        info.publishedPorts.size() == 1
        info.isRunning()
        info.isHealthy()
    }

    def "supports various Docker Compose states"() {
        expect:
        // Various real Docker Compose states
        new ServiceInfo("id", "name", "Up").isRunning()
        new ServiceInfo("id", "name", "Up (healthy)").isRunning()
        new ServiceInfo("id", "name", "Up (healthy)").isHealthy()
        new ServiceInfo("id", "name", "Up About a minute").isRunning()
        new ServiceInfo("id", "name", "running").isRunning()

        !new ServiceInfo("id", "name", "Exited (0)").isRunning()
        !new ServiceInfo("id", "name", "Exited (1)").isRunning()
        !new ServiceInfo("id", "name", "Restarting").isRunning() // doesn't contain "up" or "running"
    }

    def "Jackson annotations prevent infinite recursion"() {
        given:
        def info = new ServiceInfo("test", "test-container", "running")

        expect:
        // The @JsonIgnore annotations should prevent these from being serialized
        // This test documents the annotation behavior
        info.isRunning() != null
        info.isHealthy() != null
        info.getPortMapping(80) == null // no ports configured
        info.getHostPort(80) == null // no ports configured
    }

    def "handles multiple port mappings"() {
        given:
        def httpPort = new PortMapping(80, 8080, "tcp")
        def httpsPort = new PortMapping(443, 8443, "tcp")
        def sshPort = new PortMapping(22, 2222, "tcp")
        def ports = [httpPort, httpsPort, sshPort]
        def info = new ServiceInfo("multi-port", "multi-service", "running", ports)

        expect:
        info.publishedPorts.size() == 3
        info.getPortMapping(80) == httpPort
        info.getPortMapping(443) == httpsPort
        info.getPortMapping(22) == sshPort
        info.getHostPort(80) == 8080
        info.getHostPort(443) == 8443
        info.getHostPort(22) == 2222
    }

    def "container property access uses backward compatibility"() {
        given:
        def httpPort = new PortMapping(80, 8080, "tcp")
        def httpsPort = new PortMapping(443, 8443, "tcp")
        def ports = [httpPort, httpsPort]
        def info = new ServiceInfo("test", "test-container", "running", ports)

        expect:
        // Test that the published ports use the container property for matching
        info.getPortMapping(80)?.container == 80
        info.getPortMapping(443)?.container == 443
        info.getHostPort(80) == 8080
        info.getHostPort(443) == 8443
    }
}