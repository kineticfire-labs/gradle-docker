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

package com.kineticfire.gradle.docker.task

import com.kineticfire.gradle.docker.exception.ComposeServiceException
import com.kineticfire.gradle.docker.model.ComposeConfig
import com.kineticfire.gradle.docker.model.ComposeState
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import com.kineticfire.gradle.docker.service.ComposeService
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for ComposeUpTask waitForLog properties
 */
class ComposeUpTaskWaitForLogTest extends Specification {

    def project
    ComposeUpTask task

    @TempDir
    File tempDir

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.create('composeUpTest', ComposeUpTask)
        task.outputDirectory.set(tempDir)
    }

    // ===== WAIT FOR LOG PROPERTY INITIALIZATION TESTS =====

    def "waitForLogServices property is initially empty"() {
        expect:
        // MapProperty is always present but empty by default
        task.waitForLogServices.get().isEmpty()
    }

    def "waitForLogRejectPatterns property is initially empty"() {
        expect:
        // MapProperty is always present but empty by default
        task.waitForLogRejectPatterns.get().isEmpty()
    }

    def "waitForLogTimeoutSeconds property is initially not present"() {
        expect:
        !task.waitForLogTimeoutSeconds.present
    }

    def "waitForLogPollSeconds property is initially not present"() {
        expect:
        !task.waitForLogPollSeconds.present
    }

    def "waitForLogCaseInsensitive property is initially not present"() {
        expect:
        !task.waitForLogCaseInsensitive.present
    }

    def "waitForLogVerbose property is initially not present"() {
        expect:
        !task.waitForLogVerbose.present
    }

    def "waitForLogProgressIntervalSeconds property is initially not present"() {
        expect:
        !task.waitForLogProgressIntervalSeconds.present
    }

    // ===== WAIT FOR LOG PROPERTY SETTER TESTS =====

    def "waitForLogServices can be set with single service"() {
        when:
        task.waitForLogServices.set(['app': ['Started Application']])

        then:
        task.waitForLogServices.present
        task.waitForLogServices.get() == ['app': ['Started Application']]
    }

    def "waitForLogServices can be set with multiple services"() {
        when:
        task.waitForLogServices.set([
            'app': ['Started', 'Ready'],
            'db': ['accepting connections']
        ])

        then:
        task.waitForLogServices.present
        task.waitForLogServices.get().size() == 2
        task.waitForLogServices.get()['app'] == ['Started', 'Ready']
        task.waitForLogServices.get()['db'] == ['accepting connections']
    }

    def "waitForLogRejectPatterns can be set"() {
        when:
        task.waitForLogRejectPatterns.set([
            'app': ['Error', 'Exception'],
            'db': ['FATAL']
        ])

        then:
        task.waitForLogRejectPatterns.present
        task.waitForLogRejectPatterns.get() == ['app': ['Error', 'Exception'], 'db': ['FATAL']]
    }

    def "waitForLogTimeoutSeconds can be set"() {
        when:
        task.waitForLogTimeoutSeconds.set(120)

        then:
        task.waitForLogTimeoutSeconds.present
        task.waitForLogTimeoutSeconds.get() == 120
    }

    def "waitForLogPollSeconds can be set"() {
        when:
        task.waitForLogPollSeconds.set(5)

        then:
        task.waitForLogPollSeconds.present
        task.waitForLogPollSeconds.get() == 5
    }

    def "waitForLogCaseInsensitive can be set to true"() {
        when:
        task.waitForLogCaseInsensitive.set(true)

        then:
        task.waitForLogCaseInsensitive.present
        task.waitForLogCaseInsensitive.get() == true
    }

    def "waitForLogCaseInsensitive can be set to false"() {
        when:
        task.waitForLogCaseInsensitive.set(false)

        then:
        task.waitForLogCaseInsensitive.present
        task.waitForLogCaseInsensitive.get() == false
    }

    def "waitForLogVerbose can be set"() {
        when:
        task.waitForLogVerbose.set(true)

        then:
        task.waitForLogVerbose.present
        task.waitForLogVerbose.get() == true
    }

    def "waitForLogProgressIntervalSeconds can be set"() {
        when:
        task.waitForLogProgressIntervalSeconds.set(10)

        then:
        task.waitForLogProgressIntervalSeconds.present
        task.waitForLogProgressIntervalSeconds.get() == 10
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "all waitForLog properties can be configured together"() {
        when:
        task.waitForLogServices.set(['web': ['Listening on port 8080']])
        task.waitForLogRejectPatterns.set(['web': ['FATAL']])
        task.waitForLogTimeoutSeconds.set(90)
        task.waitForLogPollSeconds.set(3)
        task.waitForLogCaseInsensitive.set(true)
        task.waitForLogVerbose.set(true)
        task.waitForLogProgressIntervalSeconds.set(15)

        then:
        task.waitForLogServices.get() == ['web': ['Listening on port 8080']]
        task.waitForLogRejectPatterns.get() == ['web': ['FATAL']]
        task.waitForLogTimeoutSeconds.get() == 90
        task.waitForLogPollSeconds.get() == 3
        task.waitForLogCaseInsensitive.get() == true
        task.waitForLogVerbose.get() == true
        task.waitForLogProgressIntervalSeconds.get() == 15
    }

    // ===== PARTIAL CONFIGURATION SCENARIOS =====

    def "task handles partial waitForLog configuration - only services"() {
        when:
        task.waitForLogServices.set(['app': ['Started']])
        // No other waitForLog properties set

        then:
        task.waitForLogServices.present
        task.waitForLogServices.get() == ['app': ['Started']]
        // MapProperty is always present but empty when not explicitly set
        task.waitForLogRejectPatterns.get().isEmpty()
        !task.waitForLogTimeoutSeconds.present
        !task.waitForLogPollSeconds.present
        !task.waitForLogCaseInsensitive.present
        !task.waitForLogVerbose.present
        !task.waitForLogProgressIntervalSeconds.present
    }

    def "task handles partial waitForLog configuration - services and timeout"() {
        when:
        task.waitForLogServices.set(['db': ['ready']])
        task.waitForLogTimeoutSeconds.set(180)

        then:
        task.waitForLogServices.present
        task.waitForLogServices.get() == ['db': ['ready']]
        task.waitForLogTimeoutSeconds.present
        task.waitForLogTimeoutSeconds.get() == 180
        // MapProperty is always present but empty when not explicitly set
        task.waitForLogRejectPatterns.get().isEmpty()
        !task.waitForLogPollSeconds.present
    }

    // ===== EMPTY VALUES TESTS =====

    def "waitForLogServices can be set to empty map"() {
        when:
        task.waitForLogServices.set([:])

        then:
        task.waitForLogServices.present
        task.waitForLogServices.get().isEmpty()
    }

    def "waitForLogRejectPatterns can be set to empty map"() {
        when:
        task.waitForLogRejectPatterns.set([:])

        then:
        task.waitForLogRejectPatterns.present
        task.waitForLogRejectPatterns.get().isEmpty()
    }

    // ===== TASK EXECUTION TESTS (WITH MOCK) =====

    def "composeUp task calls waitForLogPatterns when waitForLogServices is configured"() {
        given:
        def mockComposeService = Mock(ComposeService)
        def composeFile = new File(tempDir, 'docker-compose.yml')
        composeFile.text = 'services:\n  app:\n    image: alpine'

        task.composeFiles.from(composeFile)
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])

        def mockState = new ComposeState('test-stack', 'test-project', [:])
        mockComposeService.upStack(_) >> CompletableFuture.completedFuture(mockState)

        def mockWaitResult = ['app': new WaitForLogResult('app', [], true)]
        mockComposeService.waitForLogPatterns(_) >> CompletableFuture.completedFuture(mockWaitResult)

        when:
        task.composeUp()

        then:
        1 * mockComposeService.waitForLogPatterns({ WaitForLogConfig config ->
            config.projectName == 'test-project'
            config.servicePatterns.containsKey('app')
        }) >> CompletableFuture.completedFuture(mockWaitResult)
    }

    def "composeUp task does not call waitForLogPatterns when waitForLogServices is empty"() {
        given:
        def mockComposeService = Mock(ComposeService)
        def composeFile = new File(tempDir, 'docker-compose.yml')
        composeFile.text = 'services:\n  app:\n    image: alpine'

        task.composeFiles.from(composeFile)
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set([:])

        def mockState = new ComposeState('test-stack', 'test-project', [:])
        mockComposeService.upStack(_) >> CompletableFuture.completedFuture(mockState)

        when:
        task.composeUp()

        then:
        0 * mockComposeService.waitForLogPatterns(_)
    }

    def "composeUp task does not call waitForLogPatterns when waitForLogServices is not set"() {
        given:
        def mockComposeService = Mock(ComposeService)
        def composeFile = new File(tempDir, 'docker-compose.yml')
        composeFile.text = 'services:\n  app:\n    image: alpine'

        task.composeFiles.from(composeFile)
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        task.composeService.set(mockComposeService)
        // waitForLogServices NOT set

        def mockState = new ComposeState('test-stack', 'test-project', [:])
        mockComposeService.upStack(_) >> CompletableFuture.completedFuture(mockState)

        when:
        task.composeUp()

        then:
        0 * mockComposeService.waitForLogPatterns(_)
    }

    // ===== EXCEPTION PROPAGATION TESTS =====

    def "composeUp task propagates exception from waitForLogPatterns"() {
        given:
        def mockComposeService = Mock(ComposeService)
        def composeFile = new File(tempDir, 'docker-compose.yml')
        composeFile.text = 'services:\n  app:\n    image: alpine'

        task.composeFiles.from(composeFile)
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])

        def mockState = new ComposeState('test-stack', 'test-project', [:])
        mockComposeService.upStack(_) >> CompletableFuture.completedFuture(mockState)

        def failedFuture = new CompletableFuture<Map<String, WaitForLogResult>>()
        failedFuture.completeExceptionally(new ComposeServiceException(
            ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
            "Timeout waiting for log patterns"
        ))
        mockComposeService.waitForLogPatterns(_) >> failedFuture

        when:
        task.composeUp()

        then:
        def e = thrown(RuntimeException)
        e.message.contains("test-stack")
        e.cause.message.contains("Timeout")
    }

    // ===== DEFAULT VALUES TESTS =====

    def "task uses default timeoutSeconds when not explicitly set"() {
        given:
        def mockComposeService = Mock(ComposeService)
        def composeFile = new File(tempDir, 'docker-compose.yml')
        composeFile.text = 'services:\n  app:\n    image: alpine'

        task.composeFiles.from(composeFile)
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])
        // timeoutSeconds NOT set - should use default of 60

        def mockState = new ComposeState('test-stack', 'test-project', [:])
        mockComposeService.upStack(_) >> CompletableFuture.completedFuture(mockState)

        WaitForLogConfig capturedConfig = null
        mockComposeService.waitForLogPatterns(_) >> { args ->
            capturedConfig = args[0]
            return CompletableFuture.completedFuture(['app': new WaitForLogResult('app', [], true)])
        }

        when:
        task.composeUp()

        then:
        capturedConfig.timeout.toSeconds() == 60
    }

    def "task uses default pollSeconds when not explicitly set"() {
        given:
        def mockComposeService = Mock(ComposeService)
        def composeFile = new File(tempDir, 'docker-compose.yml')
        composeFile.text = 'services:\n  app:\n    image: alpine'

        task.composeFiles.from(composeFile)
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        task.composeService.set(mockComposeService)
        task.waitForLogServices.set(['app': ['Started']])
        // pollSeconds NOT set - should use default of 2

        def mockState = new ComposeState('test-stack', 'test-project', [:])
        mockComposeService.upStack(_) >> CompletableFuture.completedFuture(mockState)

        WaitForLogConfig capturedConfig = null
        mockComposeService.waitForLogPatterns(_) >> { args ->
            capturedConfig = args[0]
            return CompletableFuture.completedFuture(['app': new WaitForLogResult('app', [], true)])
        }

        when:
        task.composeUp()

        then:
        capturedConfig.pollInterval.toSeconds() == 2
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "waitForLog properties can be updated after initial configuration"() {
        when:
        task.waitForLogServices.set(['app': ['Pattern1']])
        task.waitForLogTimeoutSeconds.set(30)

        then:
        task.waitForLogServices.get() == ['app': ['Pattern1']]
        task.waitForLogTimeoutSeconds.get() == 30

        when:
        task.waitForLogServices.set(['db': ['Pattern2']])
        task.waitForLogTimeoutSeconds.set(60)

        then:
        task.waitForLogServices.get() == ['db': ['Pattern2']]
        task.waitForLogTimeoutSeconds.get() == 60
    }
}
