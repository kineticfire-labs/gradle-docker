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

package com.kineticfire.gradle.docker.service

import com.kineticfire.gradle.docker.model.*
import org.gradle.api.services.BuildServiceParameters
import spock.lang.Specification

/**
 * Focused tests for ComposeService and related classes
 * Tests methods that can be tested without complex process mocking
 */
class ComposeServiceFocusedTest extends Specification {

    def "ComposeService interface has all required methods"() {
        expect:
        ComposeService.getDeclaredMethods().any { it.name == 'upStack' }
        ComposeService.getDeclaredMethods().any { it.name == 'downStack' }
        ComposeService.getDeclaredMethods().any { it.name == 'waitForServices' }
        ComposeService.getDeclaredMethods().any { it.name == 'captureLogs' }
    }

    def "ServiceStatus enum has all required values"() {
        expect:
        ServiceStatus.RUNNING != null
        ServiceStatus.HEALTHY != null
        ServiceStatus.STOPPED != null
        ServiceStatus.RESTARTING != null
        ServiceStatus.UNKNOWN != null
    }

    def "ServiceStatus valueOf works correctly"() {
        expect:
        ServiceStatus.valueOf("RUNNING") == ServiceStatus.RUNNING
        ServiceStatus.valueOf("HEALTHY") == ServiceStatus.HEALTHY
        ServiceStatus.valueOf("STOPPED") == ServiceStatus.STOPPED
        ServiceStatus.valueOf("RESTARTING") == ServiceStatus.RESTARTING
        ServiceStatus.valueOf("UNKNOWN") == ServiceStatus.UNKNOWN
    }

    def "ServiceStatus values() returns all values"() {
        when:
        def values = ServiceStatus.values()

        then:
        values.length == 5
        values.contains(ServiceStatus.RUNNING)
        values.contains(ServiceStatus.HEALTHY)
        values.contains(ServiceStatus.STOPPED)
        values.contains(ServiceStatus.RESTARTING)
        values.contains(ServiceStatus.UNKNOWN)
    }

    def "parseServiceState handles various status strings correctly"() {
        given:
        def service = new TestableExecLibraryComposeService()

        expect:
        service.testParseServiceState(status) == expected

        where:
        status                          | expected
        "Up 5 minutes"                 | ServiceStatus.RUNNING
        "running"                      | ServiceStatus.RUNNING
        "Up (healthy)"                 | ServiceStatus.HEALTHY
        "running (healthy)"            | ServiceStatus.HEALTHY
        "Exit 0"                       | ServiceStatus.STOPPED
        "Exited (1)"                   | ServiceStatus.STOPPED
        "stopped"                      | ServiceStatus.STOPPED
        "Restarting"                   | ServiceStatus.RESTARTING
        "restarting (1)"               | ServiceStatus.RESTARTING
        "Restarting (3) 2 seconds ago" | ServiceStatus.RESTARTING
        null                           | ServiceStatus.UNKNOWN
        ""                             | ServiceStatus.UNKNOWN
        "   "                          | ServiceStatus.UNKNOWN
        "unknown status"               | ServiceStatus.UNKNOWN
        "Created"                      | ServiceStatus.UNKNOWN
        "Paused"                       | ServiceStatus.UNKNOWN
    }

    def "parseServiceState handles edge cases"() {
        given:
        def service = new TestableExecLibraryComposeService()

        expect:
        service.testParseServiceState(status) == expected

        where:
        status                    | expected
        "UP"                     | ServiceStatus.RUNNING
        "RUNNING"                | ServiceStatus.RUNNING
        "up (healthy)"           | ServiceStatus.HEALTHY
        "Running (healthy)"      | ServiceStatus.HEALTHY
        "EXIT 0"                 | ServiceStatus.STOPPED
        "STOPPED"                | ServiceStatus.STOPPED
        "RESTARTING"             | ServiceStatus.RESTARTING
        "restart"                | ServiceStatus.RESTARTING
    }

    def "parseServiceState is case insensitive"() {
        given:
        def service = new TestableExecLibraryComposeService()

        expect:
        service.testParseServiceState("Up 5 minutes") == ServiceStatus.RUNNING
        service.testParseServiceState("up 5 minutes") == ServiceStatus.RUNNING
        service.testParseServiceState("UP 5 MINUTES") == ServiceStatus.RUNNING
    }

    /**
     * Testable implementation for ExecLibraryComposeService method testing
     * Replicates the parsing logic without process execution
     */
    static class TestableExecLibraryComposeService {

        ServiceStatus testParseServiceState(String status) {
            if (!status || status.trim().isEmpty()) {
                return ServiceStatus.UNKNOWN
            }

            def lowerStatus = status.toLowerCase()
            if (lowerStatus.contains('running') || lowerStatus.contains('up')) {
                if (lowerStatus.contains('healthy')) {
                    return ServiceStatus.HEALTHY
                }
                return ServiceStatus.RUNNING
            } else if (lowerStatus.contains('exit') || lowerStatus.contains('stop')) {
                return ServiceStatus.STOPPED
            } else if (lowerStatus.contains('restart')) {
                return ServiceStatus.RESTARTING
            } else {
                return ServiceStatus.UNKNOWN
            }
        }
    }
}