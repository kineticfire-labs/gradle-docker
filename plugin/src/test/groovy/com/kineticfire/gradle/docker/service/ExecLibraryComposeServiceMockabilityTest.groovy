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

import com.kineticfire.gradle.docker.exception.ComposeServiceException
import com.kineticfire.gradle.docker.model.*
import com.kineticfire.gradle.docker.testutil.MockServiceBuilder
import spock.lang.Specification

import java.nio.file.Paths
import java.time.Duration

/**
 * Demonstrates the mockability benefits of injecting TimeService into ExecLibraryComposeService.
 * Tests can now run fast without actual sleep delays and can precisely control time advancement.
 */
class ExecLibraryComposeServiceMockabilityTest extends Specification {
    
    ProcessExecutor mockProcessExecutor
    CommandValidator mockCommandValidator
    ServiceLogger mockServiceLogger
    MockServiceBuilder.ControllableTimeService mockTimeService
    ExecLibraryComposeService service
    
    def setup() {
        mockProcessExecutor = Mock(ProcessExecutor)
        mockCommandValidator = Mock(CommandValidator)
        mockServiceLogger = Mock(ServiceLogger)
        mockTimeService = MockServiceBuilder.createControllableTimeService(1000L)
        
        service = new ExecLibraryComposeService(
            mockProcessExecutor,
            mockCommandValidator,
            mockServiceLogger,
            mockTimeService
        ) {
            @Override
            org.gradle.api.services.BuildServiceParameters.None getParameters() {
                return null
            }
        }
    }
    
    def "waitForServices completes when services become ready - demonstrating fast mock-based testing"() {
        given: "A wait configuration"
        def config = new WaitConfig(
            "test-project",
            ["web", "db"],
            Duration.ofSeconds(10),
            Duration.ofMillis(100),
            ServiceStatus.RUNNING
        )

        and: "Mock command validator"
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']

        and: "Mock process executor that simulates services becoming ready on second check"
        def checkCount = 0
        mockProcessExecutor.execute(_ as List<String>) >> {
            checkCount++
            if (checkCount >= 2) {
                // Services are ready on second check
                return new ProcessResult(0, "NAME STATUS\nweb Up\ndb Up", "")
            } else {
                // Services not ready on first check
                return new ProcessResult(0, "NAME STATUS\nweb starting\ndb starting", "")
            }
        }

        when: "Waiting for services with mock time service"
        def startTime = System.currentTimeMillis()
        def result = service.waitForServices(config).get()
        def endTime = System.currentTimeMillis()
        def actualElapsed = endTime - startTime

        then: "Services are reported as ready"
        result == ServiceStatus.RUNNING

        and: "Sleep was called but time advanced instantly (no actual delay)"
        mockTimeService.methodCalls.count { it.startsWith("sleep:") } == 1

        and: "Test completed quickly despite mock time advancement"
        actualElapsed < 15000 // Completes in reasonable time (< 15s) - allows for CI environment overhead

        and: "Mock time advanced by the poll interval"
        mockTimeService.currentTimeMillis() == 1100L // Started at 1000, slept 100ms
    }
    
    def "waitForServices times out when services never become ready - controlled time advancement"() {
        given: "A wait configuration with short timeout"
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofSeconds(1),
            Duration.ofMillis(100),
            ServiceStatus.RUNNING
        )

        and: "Mock process executor that always reports service as not ready"
        mockProcessExecutor.execute(_ as List<String>) >> {
            return new ProcessResult(0, "NAME STATUS\nweb starting", "")
        }

        and: "Mock command validator"
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']

        when: "Waiting for services"
        def startTime = System.currentTimeMillis()
        service.waitForServices(config).get()

        then: "Timeout exception is thrown"
        def ex = thrown(Exception)
        ex.cause instanceof ComposeServiceException
        ex.cause.message.contains("Timeout waiting for services")

        and: "Test completed quickly (under 1 second actual time, not the timeout duration)"
        def actualElapsed = System.currentTimeMillis() - startTime
        actualElapsed < 500 // Much faster than actual 1 second timeout would be

        and: "Multiple sleep calls were made while polling"
        mockTimeService.methodCalls.count { it.startsWith("sleep:") } >= 10
    }
    
    def "waitForServices uses TimeService for all time operations"() {
        given: "A wait configuration"
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofSeconds(10),
            Duration.ofMillis(50),
            ServiceStatus.RUNNING
        )

        and: "Mock command validator"
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']

        and: "Mock that makes service ready on second check"
        def checkCount = 0
        mockProcessExecutor.execute(_ as List<String>) >> {
            checkCount++
            // Make service ready on second check
            if (checkCount >= 2) {
                return new ProcessResult(0, "NAME STATUS\nweb Up", "")
            } else {
                return new ProcessResult(0, "NAME STATUS\nweb starting", "")
            }
        }

        when: "Waiting for services"
        service.waitForServices(config).get()

        then: "TimeService methods were called"
        mockTimeService.methodCalls.count { it == "currentTimeMillis" } >= 2
        mockTimeService.methodCalls.any { it.startsWith("sleep:") }
    }
}
