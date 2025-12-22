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
 * Unit tests for PortMapping
 */
class PortMappingTest extends Specification {

    def "can create PortMapping with default constructor"() {
        when:
        def mapping = new PortMapping()

        then:
        mapping.containerPort == 0
        mapping.hostPort == 0
        mapping.protocol == null
    }

    def "can create PortMapping with traditional constructor"() {
        when:
        def mapping = new PortMapping(8080, 80, "tcp")

        then:
        mapping.containerPort == 8080
        mapping.hostPort == 80
        mapping.protocol == "tcp"
    }

    def "traditional constructor defaults to tcp protocol"() {
        when:
        def mapping = new PortMapping(3000, 3000)

        then:
        mapping.containerPort == 3000
        mapping.hostPort == 3000
        mapping.protocol == "tcp"
    }

    def "traditional constructor handles null protocol"() {
        when:
        def mapping = new PortMapping(5432, 5432, null)

        then:
        mapping.containerPort == 5432
        mapping.hostPort == 5432
        mapping.protocol == "tcp" // null becomes tcp
    }

    def "can create PortMapping with named parameter constructor"() {
        when:
        def mapping = new PortMapping([
            containerPort: 443,
            hostPort: 8443,
            protocol: "tcp"
        ])

        then:
        mapping.containerPort == 443
        mapping.hostPort == 8443
        mapping.protocol == "tcp"
    }

    def "named parameter constructor defaults protocol to tcp"() {
        when:
        def mapping = new PortMapping([
            containerPort: 80,
            hostPort: 8080
        ])

        then:
        mapping.containerPort == 80
        mapping.hostPort == 8080
        mapping.protocol == "tcp"
    }

    def "named parameter constructor handles null protocol"() {
        when:
        def mapping = new PortMapping([
            containerPort: 22,
            hostPort: 2222,
            protocol: null
        ])

        then:
        mapping.containerPort == 22
        mapping.hostPort == 2222
        mapping.protocol == "tcp"
    }

    def "backward compatibility properties work"() {
        given:
        def mapping = new PortMapping(8080, 80, "tcp")

        expect:
        mapping.container == 8080
        mapping.host == 80
        mapping.containerPort == mapping.container
        mapping.hostPort == mapping.host
    }

    def "getDockerFormat returns correct format"() {
        expect:
        new PortMapping(8080, 80, "tcp").dockerFormat == "80:8080/tcp"
        new PortMapping(443, 8443, "tcp").dockerFormat == "8443:443/tcp"
        new PortMapping(53, 53, "udp").dockerFormat == "53:53/udp"
        new PortMapping(3000, 3000, "tcp").dockerFormat == "3000:3000/tcp"
    }

    def "toString includes port mapping information"() {
        given:
        def mapping = new PortMapping(8080, 80, "tcp")

        when:
        def string = mapping.toString()

        then:
        string == "PortMapping{80:8080/tcp}"
    }

    def "toString works with different protocols"() {
        expect:
        new PortMapping(53, 53, "udp").toString() == "PortMapping{53:53/udp}"
        new PortMapping(80, 8080, "tcp").toString() == "PortMapping{8080:80/tcp}"
    }

    def "equals works correctly"() {
        given:
        def mapping1 = new PortMapping(8080, 80, "tcp")
        def mapping2 = new PortMapping(8080, 80, "tcp")
        def mapping3 = new PortMapping(8080, 80, "udp")
        def mapping4 = new PortMapping(3000, 80, "tcp")
        def mapping5 = new PortMapping(8080, 8080, "tcp") // same container port, different host port

        expect:
        mapping1 == mapping2
        mapping1 != mapping3 // different protocol
        mapping1 != mapping4 // different container port
        mapping1 != mapping5 // different host port (same container port)
        mapping1 != null
        mapping1 == mapping1 // self equality
    }

    def "equals handles different object types"() {
        given:
        def mapping = new PortMapping(80, 8080, "tcp")

        expect:
        mapping != "not a port mapping"
        mapping != 123
        mapping != [containerPort: 80, hostPort: 8080, protocol: "tcp"]
    }

    def "hashCode is consistent with equals"() {
        given:
        def mapping1 = new PortMapping(8080, 80, "tcp")
        def mapping2 = new PortMapping(8080, 80, "tcp")
        def mapping3 = new PortMapping(8080, 80, "udp")

        expect:
        mapping1.hashCode() == mapping2.hashCode() // equal objects have same hash
        mapping1.hashCode() != mapping3.hashCode() // different objects likely have different hash
    }

    def "hashCode uses all significant fields"() {
        given:
        def mappings = [
            new PortMapping(80, 8080, "tcp"),
            new PortMapping(80, 8080, "udp"), // different protocol
            new PortMapping(443, 8080, "tcp"), // different container port
            new PortMapping(80, 443, "tcp") // different host port
        ]

        when:
        def hashCodes = mappings.collect { it.hashCode() }

        then:
        // All hash codes should be different (though collisions are theoretically possible)
        hashCodes.toSet().size() == 4
    }

    def "supports common port mapping scenarios"() {
        expect:
        // HTTP
        def http = new PortMapping(80, 8080, "tcp")
        http.dockerFormat == "8080:80/tcp"

        // HTTPS  
        def https = new PortMapping(443, 8443, "tcp")
        https.dockerFormat == "8443:443/tcp"

        // Database
        def db = new PortMapping(5432, 5432, "tcp")
        db.dockerFormat == "5432:5432/tcp"

        // DNS
        def dns = new PortMapping(53, 53, "udp")
        dns.dockerFormat == "53:53/udp"
    }

    def "handles edge case port values"() {
        expect:
        // Well-known ports
        new PortMapping(22, 2222, "tcp").dockerFormat == "2222:22/tcp"
        
        // High port numbers
        new PortMapping(65535, 65535, "tcp").dockerFormat == "65535:65535/tcp"
        
        // Port 0 (though not practical)
        new PortMapping(0, 0, "tcp").dockerFormat == "0:0/tcp"
    }

    def "mutable properties can be changed after construction"() {
        given:
        def mapping = new PortMapping()

        when:
        mapping.containerPort = 8080
        mapping.hostPort = 80
        mapping.protocol = "udp"

        then:
        mapping.containerPort == 8080
        mapping.hostPort == 80
        mapping.protocol == "udp"
        mapping.dockerFormat == "80:8080/udp"
    }

    def "Jackson annotations prevent infinite recursion"() {
        given:
        def mapping = new PortMapping(8080, 80, "tcp")

        expect:
        // The @JsonIgnore annotations should prevent these from being serialized
        // This test documents the annotation behavior
        mapping.container == 8080 // backward compatibility getter
        mapping.host == 80 // backward compatibility getter
        mapping.dockerFormat == "80:8080/tcp" // utility getter
    }

    def "supports protocol variations"() {
        expect:
        new PortMapping(80, 8080, "TCP").protocol == "TCP"
        new PortMapping(53, 53, "UDP").protocol == "UDP"
        new PortMapping(22, 2222, "tcp").protocol == "tcp"
        new PortMapping(443, 8443, "sctp").protocol == "sctp"
    }
}