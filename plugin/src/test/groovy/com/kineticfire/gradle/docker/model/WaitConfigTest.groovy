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

/**
 * Unit tests for WaitConfig
 */
class WaitConfigTest extends Specification {

    def "can create minimal WaitConfig"() {
        given:
        def services = ["web", "db"]
        def timeout = Duration.ofMinutes(5)
        def targetState = ServiceStatus.RUNNING

        when:
        def config = new WaitConfig("test-project", services, timeout, targetState)

        then:
        config.projectName == "test-project"
        config.services == services
        config.timeout == timeout
        config.pollInterval == Duration.ofSeconds(2) // default
        config.targetState == targetState
    }

    def "can create full WaitConfig with custom poll interval"() {
        given:
        def services = ["redis", "postgres"]
        def timeout = Duration.ofSeconds(120)
        def pollInterval = Duration.ofMillis(500)
        def targetState = ServiceStatus.HEALTHY

        when:
        def config = new WaitConfig("prod-project", services, timeout, pollInterval, targetState)

        then:
        config.projectName == "prod-project"
        config.services == services
        config.timeout == timeout
        config.pollInterval == pollInterval
        config.targetState == targetState
    }

    def "validates project name cannot be null"() {
        when:
        new WaitConfig(null, ["web"], Duration.ofMinutes(1), ServiceStatus.RUNNING)

        then:
        def exception = thrown(NullPointerException)
        exception.message.contains("Project name cannot be null")
    }

    def "validates services list cannot be null"() {
        when:
        new WaitConfig("project", null, Duration.ofMinutes(1), ServiceStatus.RUNNING)

        then:
        def exception = thrown(NullPointerException)
        exception.message.contains("Services list cannot be null")
    }

    def "validates target state cannot be null"() {
        when:
        new WaitConfig("project", ["web"], Duration.ofMinutes(1), null)

        then:
        def exception = thrown(NullPointerException)
        exception.message.contains("Target state cannot be null")
    }

    def "handles null timeout with default value"() {
        when:
        def config = new WaitConfig("project", ["web"], null, ServiceStatus.RUNNING)

        then:
        config.timeout == Duration.ofSeconds(60)
    }

    def "handles null timeout and poll interval with defaults"() {
        when:
        def config = new WaitConfig("project", ["web"], null, null, ServiceStatus.RUNNING)

        then:
        config.timeout == Duration.ofSeconds(60)
        config.pollInterval == Duration.ofSeconds(2)
    }

    def "can handle empty services list"() {
        when:
        def config = new WaitConfig("project", [], Duration.ofMinutes(1), ServiceStatus.RUNNING)

        then:
        config.services == []
        config.projectName == "project"
    }

    def "calculates total wait attempts correctly"() {
        expect:
        new WaitConfig("p", ["s"], Duration.ofSeconds(60), Duration.ofSeconds(5), ServiceStatus.RUNNING).totalWaitAttempts == 12
        new WaitConfig("p", ["s"], Duration.ofSeconds(30), Duration.ofSeconds(2), ServiceStatus.RUNNING).totalWaitAttempts == 15
        new WaitConfig("p", ["s"], Duration.ofSeconds(10), Duration.ofSeconds(10), ServiceStatus.RUNNING).totalWaitAttempts == 1
        new WaitConfig("p", ["s"], Duration.ofSeconds(5), Duration.ofSeconds(10), ServiceStatus.RUNNING).totalWaitAttempts == 1 // Min 1
    }

    def "calculates total wait attempts handles fractional results"() {
        expect:
        // 7 seconds / 2 seconds = 3.5, should round down to 3
        new WaitConfig("p", ["s"], Duration.ofSeconds(7), Duration.ofSeconds(2), ServiceStatus.RUNNING).totalWaitAttempts == 3
        // But minimum is always 1
        new WaitConfig("p", ["s"], Duration.ofMillis(500), Duration.ofSeconds(1), ServiceStatus.RUNNING).totalWaitAttempts == 1
    }

    def "toString includes all configuration information"() {
        given:
        def config = new WaitConfig("myproject", ["web", "api"], Duration.ofMinutes(3), Duration.ofSeconds(5), ServiceStatus.HEALTHY)

        when:
        def string = config.toString()

        then:
        string.contains("WaitConfig")
        string.contains("projectName='myproject'")
        string.contains("services=[web, api]")
        string.contains("timeout=PT3M")
        string.contains("pollInterval=PT5S")
        string.contains("targetState=healthy")
    }

    def "can create config for single service"() {
        when:
        def config = new WaitConfig("app", ["nginx"], Duration.ofSeconds(30), ServiceStatus.RUNNING)

        then:
        config.services == ["nginx"]
        config.services.size() == 1
    }

    def "can create config for multiple services"() {
        given:
        def services = ["web", "api", "db", "cache", "worker"]

        when:
        def config = new WaitConfig("microservices", services, Duration.ofMinutes(10), ServiceStatus.HEALTHY)

        then:
        config.services == services
        config.services.size() == 5
    }

    def "supports different target states"() {
        expect:
        new WaitConfig("p", ["s"], Duration.ofSeconds(10), ServiceStatus.RUNNING).targetState == ServiceStatus.RUNNING
        new WaitConfig("p", ["s"], Duration.ofSeconds(10), ServiceStatus.HEALTHY).targetState == ServiceStatus.HEALTHY
        new WaitConfig("p", ["s"], Duration.ofSeconds(10), ServiceStatus.STOPPED).targetState == ServiceStatus.STOPPED
    }

    def "immutable fields cannot be modified after construction"() {
        given:
        def config = new WaitConfig("test", ["service"], Duration.ofMinutes(1), ServiceStatus.RUNNING)

        expect:
        // Fields are final, so they cannot be reassigned
        // This test documents the immutability design
        config.projectName != null
        config.services != null
        config.timeout != null
        config.pollInterval != null
        config.targetState != null
    }

    def "duration properties work with time calculations"() {
        given:
        def config = new WaitConfig("test", ["svc"], Duration.ofMinutes(2), Duration.ofSeconds(10), ServiceStatus.RUNNING)

        expect:
        config.timeout.toSeconds() == 120
        config.pollInterval.toMillis() == 10000
        config.timeout.toMillis() > config.pollInterval.toMillis()
    }

    def "handles edge case durations"() {
        expect:
        // Very short durations
        def shortConfig = new WaitConfig("p", ["s"], Duration.ofSeconds(10), Duration.ofSeconds(5), ServiceStatus.RUNNING)
        shortConfig.totalWaitAttempts == 2
        
        // Very long durations  
        def longConfig = new WaitConfig("p", ["s"], Duration.ofMinutes(5), Duration.ofMinutes(1), ServiceStatus.RUNNING)
        longConfig.totalWaitAttempts == 5
    }
}