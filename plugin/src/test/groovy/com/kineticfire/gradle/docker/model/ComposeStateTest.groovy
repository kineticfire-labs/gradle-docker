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
 * Unit tests for ComposeState
 */
class ComposeStateTest extends Specification {

    def "can create ComposeState with default constructor"() {
        when:
        def state = new ComposeState()

        then:
        state.configName == null
        state.projectName == null
        state.services == [:]
        state.networks == []
    }

    def "can create ComposeState with traditional constructor"() {
        given:
        def services = ["web": new ServiceInfo("web_123", "nginx", "running")]
        def networks = ["mynetwork", "default"]

        when:
        def state = new ComposeState("myconfig", "myproject", services, networks)

        then:
        state.configName == "myconfig"
        state.projectName == "myproject"
        state.services == services
        state.networks == networks
    }

    def "can create ComposeState with traditional constructor minimal parameters"() {
        when:
        def state = new ComposeState("config", "project")

        then:
        state.configName == "config"
        state.projectName == "project"
        state.services == [:]
        state.networks == []
    }

    def "can create ComposeState with named parameter constructor"() {
        given:
        def services = ["db": new ServiceInfo("db_456", "postgres", "healthy")]
        def networks = ["backend"]

        when:
        def state = new ComposeState([
            configName: "test-config",
            projectName: "test-project",
            services: services,
            networks: networks
        ])

        then:
        state.configName == "test-config"
        state.projectName == "test-project"
        state.services == services
        state.networks == networks
    }

    def "named parameter constructor handles null services and networks"() {
        when:
        def state = new ComposeState([
            configName: "config",
            projectName: "project",
            services: null,
            networks: null
        ])

        then:
        state.configName == "config"
        state.projectName == "project"
        state.services == [:]
        state.networks == []
    }

    def "named parameter constructor handles missing services and networks"() {
        when:
        def state = new ComposeState([
            configName: "config",
            projectName: "project"
        ])

        then:
        state.configName == "config"
        state.projectName == "project"
        state.services == [:]
        state.networks == []
    }

    def "can create ComposeState with complex service setup"() {
        given:
        def webPortMapping = new PortMapping(80, 8080, "tcp")
        def dbPortMapping = new PortMapping(5432, 5432, "tcp")
        def webService = new ServiceInfo("web_123", "web_container", "running", [webPortMapping])
        def dbService = new ServiceInfo("db_456", "postgres_container", "healthy", [dbPortMapping])
        def services = [web: webService, db: dbService]
        def networks = ["frontend", "backend", "default"]

        when:
        def state = new ComposeState("webapp", "production", services, networks)

        then:
        state.configName == "webapp"
        state.projectName == "production"
        state.services.size() == 2
        state.services["web"] == webService
        state.services["db"] == dbService
        state.networks == networks
    }

    def "toString includes summary information"() {
        given:
        def services = [
            "web": new ServiceInfo("web_123", "nginx", "running"),
            "db": new ServiceInfo("db_456", "postgres", "healthy"),
            "cache": new ServiceInfo("cache_789", "redis", "running")
        ]
        def networks = ["frontend", "backend"]

        when:
        def state = new ComposeState("myapp", "prod", services, networks)
        def string = state.toString()

        then:
        string.contains("ComposeState")
        string.contains("configName='myapp'")
        string.contains("projectName='prod'")
        string.contains("services=3 services")
        string.contains("networks=2 networks")
    }

    def "toString handles empty collections"() {
        when:
        def state = new ComposeState("empty", "test")
        def string = state.toString()

        then:
        string.contains("services=0 services")
        string.contains("networks=0 networks")
    }

    def "toString handles null values"() {
        when:
        def state = new ComposeState()
        def string = state.toString()

        then:
        string.contains("configName='null'")
        string.contains("projectName='null'")
        string.contains("services=0 services")
        string.contains("networks=0 networks")
    }

    def "services map is mutable after construction"() {
        given:
        def state = new ComposeState("test", "test")
        def newService = new ServiceInfo("redis_123", "redis", "running")

        when:
        state.services["redis"] = newService

        then:
        state.services["redis"] == newService
        state.services.size() == 1
    }

    def "networks list is mutable after construction"() {
        given:
        def state = new ComposeState("test", "test")

        when:
        state.networks.add("custom_network")

        then:
        state.networks.contains("custom_network")
        state.networks.size() == 1
    }

    def "can modify state properties after construction"() {
        given:
        def state = new ComposeState("old", "old")

        when:
        state.configName = "new"
        state.projectName = "new"

        then:
        state.configName == "new"
        state.projectName == "new"
    }

    def "supports Jackson default constructor pattern"() {
        when:
        def state = new ComposeState()
        // Simulate Jackson setting properties
        state.configName = "jackson-config"
        state.projectName = "jackson-project"
        state.services = ["app": new ServiceInfo("app_123", "myapp", "running")]
        state.networks = ["mynet"]

        then:
        state.configName == "jackson-config"
        state.projectName == "jackson-project"
        state.services.size() == 1
        state.networks.size() == 1
    }
}