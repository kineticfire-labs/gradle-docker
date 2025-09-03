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
 * Unit tests for ServiceStatus
 */
class ServiceStatusTest extends Specification {

    def "enum contains expected service statuses"() {
        when:
        def statuses = ServiceStatus.values()

        then:
        statuses.length == 5
        statuses.contains(ServiceStatus.RUNNING)
        statuses.contains(ServiceStatus.HEALTHY)
        statuses.contains(ServiceStatus.STOPPED)
        statuses.contains(ServiceStatus.RESTARTING)
        statuses.contains(ServiceStatus.UNKNOWN)
    }

    def "RUNNING status has correct properties"() {
        expect:
        ServiceStatus.RUNNING.statusName == "running"
        ServiceStatus.RUNNING.toString() == "running"
    }

    def "HEALTHY status has correct properties"() {
        expect:
        ServiceStatus.HEALTHY.statusName == "healthy"
        ServiceStatus.HEALTHY.toString() == "healthy"
    }

    def "STOPPED status has correct properties"() {
        expect:
        ServiceStatus.STOPPED.statusName == "stopped"
        ServiceStatus.STOPPED.toString() == "stopped"
    }

    def "RESTARTING status has correct properties"() {
        expect:
        ServiceStatus.RESTARTING.statusName == "restarting"
        ServiceStatus.RESTARTING.toString() == "restarting"
    }

    def "UNKNOWN status has correct properties"() {
        expect:
        ServiceStatus.UNKNOWN.statusName == "unknown"
        ServiceStatus.UNKNOWN.toString() == "unknown"
    }

    def "can be used in collections and comparisons"() {
        given:
        def runningServices = [ServiceStatus.RUNNING, ServiceStatus.HEALTHY]
        def problematicServices = [ServiceStatus.STOPPED, ServiceStatus.RESTARTING, ServiceStatus.UNKNOWN]

        expect:
        runningServices.contains(ServiceStatus.RUNNING)
        runningServices.contains(ServiceStatus.HEALTHY)
        !runningServices.contains(ServiceStatus.STOPPED)
        
        problematicServices.contains(ServiceStatus.STOPPED)
        !problematicServices.contains(ServiceStatus.RUNNING)
    }

    def "can be used in switch statements"() {
        given:
        def getServiceAction = { ServiceStatus status ->
            switch (status) {
                case ServiceStatus.RUNNING:
                case ServiceStatus.HEALTHY:
                    return "continue"
                case ServiceStatus.STOPPED:
                    return "restart"
                case ServiceStatus.RESTARTING:
                    return "wait"
                case ServiceStatus.UNKNOWN:
                default:
                    return "investigate"
            }
        }

        expect:
        getServiceAction(ServiceStatus.RUNNING) == "continue"
        getServiceAction(ServiceStatus.HEALTHY) == "continue"
        getServiceAction(ServiceStatus.STOPPED) == "restart"
        getServiceAction(ServiceStatus.RESTARTING) == "wait"
        getServiceAction(ServiceStatus.UNKNOWN) == "investigate"
    }

    def "enum values are immutable constants"() {
        expect:
        ServiceStatus.RUNNING.is(ServiceStatus.valueOf("RUNNING"))
        ServiceStatus.HEALTHY.is(ServiceStatus.valueOf("HEALTHY"))
        ServiceStatus.STOPPED.is(ServiceStatus.valueOf("STOPPED"))
        ServiceStatus.RESTARTING.is(ServiceStatus.valueOf("RESTARTING"))
        ServiceStatus.UNKNOWN.is(ServiceStatus.valueOf("UNKNOWN"))
    }

    def "toString returns statusName value"() {
        expect:
        ServiceStatus.values().every { status ->
            status.toString() == status.statusName
        }
    }

    def "can create sets and perform set operations"() {
        given:
        def activeStates = [ServiceStatus.RUNNING, ServiceStatus.HEALTHY] as Set
        def inactiveStates = [ServiceStatus.STOPPED, ServiceStatus.UNKNOWN] as Set
        def transitionStates = [ServiceStatus.RESTARTING] as Set

        expect:
        activeStates.intersect(inactiveStates).isEmpty()
        !activeStates.intersect(transitionStates).isEmpty() == false // no overlap
        (activeStates + inactiveStates + transitionStates).size() == 5
    }

    def "status names are unique"() {
        given:
        def statusNames = ServiceStatus.values().collect { it.statusName }

        expect:
        statusNames.size() == statusNames.toSet().size() // No duplicates
    }

    def "enum ordinal values are stable"() {
        expect:
        // Document the current order for stability
        ServiceStatus.RUNNING.ordinal() == 0
        ServiceStatus.HEALTHY.ordinal() == 1
        ServiceStatus.STOPPED.ordinal() == 2
        ServiceStatus.RESTARTING.ordinal() == 3
        ServiceStatus.UNKNOWN.ordinal() == 4
    }
}