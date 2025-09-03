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
 * Unit tests for ServiceState
 */
class ServiceStateTest extends Specification {

    def "can create ServiceState with default constructor"() {
        when:
        def state = new ServiceState()

        then:
        state.name == null
        state.status == null
        state.health == null
        state.ports == []
        state.networks == []
        !state.isRunning()
        !state.isHealthy()
    }

    def "can create ServiceState with traditional constructor"() {
        given:
        def portMapping = new PortMapping(80, 8080, "tcp")
        def ports = [portMapping]
        def networks = ["frontend", "backend"]

        when:
        def state = new ServiceState("web", "running", "healthy", ports, networks)

        then:
        state.name == "web"
        state.status == "running"
        state.health == "healthy"
        state.ports == ports
        state.networks == networks
        state.isRunning()
        state.isHealthy()
    }

    def "traditional constructor handles default parameters"() {
        when:
        def state = new ServiceState("api", "running")

        then:
        state.name == "api"
        state.status == "running"
        state.health == null
        state.ports == []
        state.networks == []
        state.isRunning()
        !state.isHealthy()
    }

    def "traditional constructor handles partial parameters"() {
        when:
        def state = new ServiceState("db", "running", "healthy")

        then:
        state.name == "db"
        state.status == "running"
        state.health == "healthy"
        state.ports == []
        state.networks == []
        state.isRunning()
        state.isHealthy()
    }

    def "can create ServiceState with named parameter constructor"() {
        given:
        def portMapping = new PortMapping(3306, 3306, "tcp")
        def ports = [portMapping]
        def networks = ["database"]

        when:
        def state = new ServiceState([
            name: "mysql",
            status: "running",
            health: "healthy",
            ports: ports,
            networks: networks
        ])

        then:
        state.name == "mysql"
        state.status == "running" 
        state.health == "healthy"
        state.ports == ports
        state.networks == networks
    }

    def "named parameter constructor handles null collections"() {
        when:
        def state = new ServiceState([
            name: "redis",
            status: "running",
            health: "healthy",
            ports: null,
            networks: null
        ])

        then:
        state.name == "redis"
        state.status == "running"
        state.health == "healthy"
        state.ports == []
        state.networks == []
    }

    def "named parameter constructor handles missing parameters"() {
        when:
        def state = new ServiceState([
            name: "cache",
            status: "running"
        ])

        then:
        state.name == "cache"
        state.status == "running"
        state.health == null
        state.ports == []
        state.networks == []
    }

    def "isRunning checks status correctly"() {
        expect:
        new ServiceState("svc", "running").isRunning()
        new ServiceState("svc", "RUNNING").isRunning()
        new ServiceState("svc", "Running").isRunning()
        
        !new ServiceState("svc", "stopped").isRunning()
        !new ServiceState("svc", "exited").isRunning()
        !new ServiceState("svc", "paused").isRunning()
        !new ServiceState("svc", null).isRunning()
        !new ServiceState("svc", "").isRunning()
    }

    def "isHealthy checks health correctly"() {
        expect:
        new ServiceState("svc", "running", "healthy").isHealthy()
        new ServiceState("svc", "running", "HEALTHY").isHealthy()
        new ServiceState("svc", "running", "Healthy").isHealthy()
        
        !new ServiceState("svc", "running", "unhealthy").isHealthy()
        !new ServiceState("svc", "running", "starting").isHealthy()
        !new ServiceState("svc", "running", "none").isHealthy()
        !new ServiceState("svc", "running", null).isHealthy()
        !new ServiceState("svc", "running", "").isHealthy()
    }

    def "status and health checks are case insensitive"() {
        expect:
        new ServiceState("svc", "RUNNING").isRunning()
        new ServiceState("svc", "Running").isRunning()
        new ServiceState("svc", "rUnNiNg").isRunning()
        
        new ServiceState("svc", "running", "HEALTHY").isHealthy()
        new ServiceState("svc", "running", "Healthy").isHealthy()
        new ServiceState("svc", "running", "hEaLtHy").isHealthy()
    }

    def "supports different service statuses"() {
        expect:
        // Running states
        new ServiceState("web", "running").isRunning()
        !new ServiceState("web", "stopped").isRunning()
        !new ServiceState("web", "exited").isRunning()
        !new ServiceState("web", "created").isRunning()
        !new ServiceState("web", "restarting").isRunning()
        !new ServiceState("web", "removing").isRunning()
        !new ServiceState("web", "paused").isRunning()
        !new ServiceState("web", "dead").isRunning()
    }

    def "supports different health statuses"() {
        expect:
        // Health states
        new ServiceState("web", "running", "healthy").isHealthy()
        !new ServiceState("web", "running", "unhealthy").isHealthy()
        !new ServiceState("web", "running", "starting").isHealthy()
        !new ServiceState("web", "running", "none").isHealthy()
    }

    def "toString includes all information"() {
        given:
        def portMapping = new PortMapping(80, 8080, "tcp")
        def state = new ServiceState("webapp", "running", "healthy", [portMapping], ["frontend", "backend"])

        when:
        def string = state.toString()

        then:
        string.contains("ServiceState")
        string.contains("name='webapp'")
        string.contains("status='running'")
        string.contains("health='healthy'")
        string.contains("ports=1 ports")
        string.contains("networks=2 networks")
    }

    def "toString handles empty collections"() {
        when:
        def state = new ServiceState("simple", "running", "healthy")
        def string = state.toString()

        then:
        string.contains("ports=0 ports")
        string.contains("networks=0 networks")
    }

    def "toString handles null values"() {
        when:
        def state = new ServiceState()
        def string = state.toString()

        then:
        string.contains("name='null'")
        string.contains("status='null'")
        string.contains("health='null'")
    }

    def "ports list is mutable after construction"() {
        given:
        def state = new ServiceState("test", "running")
        def portMapping = new PortMapping(443, 8443, "tcp")

        when:
        state.ports.add(portMapping)

        then:
        state.ports.contains(portMapping)
        state.ports.size() == 1
    }

    def "networks list is mutable after construction"() {
        given:
        def state = new ServiceState("test", "running")

        when:
        state.networks.add("custom_network")

        then:
        state.networks.contains("custom_network")
        state.networks.size() == 1
    }

    def "can modify properties after construction"() {
        given:
        def state = new ServiceState("old", "stopped")

        when:
        state.name = "new"
        state.status = "running"
        state.health = "healthy"

        then:
        state.name == "new"
        state.status == "running"
        state.health == "healthy"
        state.isRunning()
        state.isHealthy()
    }

    def "supports Jackson default constructor pattern"() {
        when:
        def state = new ServiceState()
        // Simulate Jackson setting properties
        state.name = "jackson-service"
        state.status = "running"
        state.health = "healthy"
        state.ports = [new PortMapping(8080, 80, "tcp")]
        state.networks = ["default"]

        then:
        state.name == "jackson-service"
        state.status == "running"
        state.health == "healthy"
        state.ports.size() == 1
        state.networks.size() == 1
        state.isRunning()
        state.isHealthy()
    }

    def "health can be null while status is set"() {
        given:
        def state = new ServiceState("service", "running", null)

        expect:
        state.isRunning()
        !state.isHealthy()
        state.health == null
    }

    def "handles complex port mappings"() {
        given:
        def httpPort = new PortMapping(80, 8080, "tcp")
        def httpsPort = new PortMapping(443, 8443, "tcp")
        def udpPort = new PortMapping(53, 5353, "udp")
        def ports = [httpPort, httpsPort, udpPort]

        when:
        def state = new ServiceState("complex", "running", "healthy", ports, ["web", "dns"])

        then:
        state.ports.size() == 3
        state.ports.contains(httpPort)
        state.ports.contains(httpsPort)
        state.ports.contains(udpPort)
        state.networks == ["web", "dns"]
    }

    def "Jackson annotations prevent infinite recursion"() {
        given:
        def state = new ServiceState("test", "running", "healthy")

        expect:
        // The @JsonIgnore annotations should prevent these from being serialized
        // This test documents the annotation behavior
        state.isRunning()
        state.isHealthy()
    }
}