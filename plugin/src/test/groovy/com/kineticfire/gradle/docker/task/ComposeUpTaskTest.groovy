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

import com.kineticfire.gradle.docker.model.ComposeConfig
import com.kineticfire.gradle.docker.model.ComposeState
import com.kineticfire.gradle.docker.model.ServiceInfo
import com.kineticfire.gradle.docker.model.ServiceState
import com.kineticfire.gradle.docker.service.ComposeService
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for ComposeUpTask
 */
class ComposeUpTaskTest extends Specification {

    Project project
    ComposeUpTask task
    ComposeService mockComposeService = Mock()

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testComposeUp', ComposeUpTask).get()
        task.composeService.set(mockComposeService)
    }

    // ===== BASIC TASK TESTS =====

    def "task can be created"() {
        expect:
        task != null
        task instanceof ComposeUpTask
    }

    def "task extends DefaultTask"() {
        expect:
        task instanceof org.gradle.api.DefaultTask
    }

    def "task has correct group and description"() {
        expect:
        task.group == 'docker compose'
        task.description == 'Start Docker Compose stack'
    }

    def "task has composeUp action"() {
        expect:
        task.actions.size() == 1
        task.actions[0].displayName.contains('composeUp')
    }

    // ===== TASK PROPERTY TESTS =====

    def "task has required properties"() {
        expect:
        task.hasProperty('composeService')
        task.hasProperty('composeFiles')
        task.hasProperty('envFiles')
        task.hasProperty('projectName')
        task.hasProperty('stackName')
    }

    def "composeFiles property works correctly"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()

        when:
        task.composeFiles.from(composeFile)

        then:
        task.composeFiles.files.contains(composeFile)
    }

    def "envFiles property works correctly"() {
        given:
        def envFile = project.file('.env')
        envFile.parentFile.mkdirs()
        envFile.createNewFile()

        when:
        task.envFiles.from(envFile)

        then:
        task.envFiles.files.contains(envFile)
    }

    def "projectName property works correctly"() {
        when:
        task.projectName.set('my-project')

        then:
        task.projectName.get() == 'my-project'
    }

    def "stackName property works correctly"() {
        when:
        task.stackName.set('webapp-stack')

        then:
        task.stackName.get() == 'webapp-stack'
    }

    // ===== TASK EXECUTION TESTS =====

    def "composeUp executes successfully"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        
        task.composeFiles.from(composeFile)
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        
        def mockComposeState = new ComposeState([
            'web': new ServiceInfo('container-id', 'web', 'running', [])
        ])
        
        when:
        task.composeUp()

        then:
        1 * mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(mockComposeState)
    }

    def "composeUp executes successfully with logging"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        
        task.composeFiles.from(composeFile)
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        
        def mockComposeState = new ComposeState([
            'web': new ServiceInfo('container-id', 'web', 'running', [])
        ])
        
        when:
        task.composeUp()

        then:
        1 * mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(mockComposeState)
        noExceptionThrown()
    }

    def "composeUp handles multiple compose files"() {
        given:
        def composeFile1 = project.file('docker-compose.yml')
        def composeFile2 = project.file('docker-compose.override.yml')
        [composeFile1, composeFile2].each {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
        
        task.composeFiles.from([composeFile1, composeFile2])
        task.projectName.set('multi-file-project')
        task.stackName.set('multi-file-stack')
        
        def mockComposeState = new ComposeState([:])

        when:
        task.composeUp()

        then:
        1 * mockComposeService.upStack({ ComposeConfig config ->
            config.composeFiles.size() == 2 &&
            config.composeFiles.contains(composeFile1.toPath()) &&
            config.composeFiles.contains(composeFile2.toPath())
        }) >> CompletableFuture.completedFuture(mockComposeState)
    }

    def "composeUp handles env files"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        def envFile = project.file('.env')
        [composeFile, envFile].each {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
        
        task.composeFiles.from(composeFile)
        task.envFiles.from(envFile)
        task.projectName.set('env-project')
        task.stackName.set('env-stack')
        
        def mockComposeState = new ComposeState([:])

        when:
        task.composeUp()

        then:
        1 * mockComposeService.upStack({ ComposeConfig config ->
            config.envFiles.size() == 1 &&
            config.envFiles.contains(envFile.toPath())
        }) >> CompletableFuture.completedFuture(mockComposeState)
    }

    def "composeUp handles empty env files"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        
        task.composeFiles.from(composeFile)
        task.projectName.set('no-env-project')
        task.stackName.set('no-env-stack')
        
        def mockComposeState = new ComposeState([:])

        when:
        task.composeUp()

        then:
        1 * mockComposeService.upStack({ ComposeConfig config ->
            config.envFiles.isEmpty()
        }) >> CompletableFuture.completedFuture(mockComposeState)
    }

    // ===== ERROR HANDLING TESTS =====

    def "composeUp throws RuntimeException when service fails"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        
        task.composeFiles.from(composeFile)
        task.projectName.set('fail-project')
        task.stackName.set('fail-stack')
        
        def failedFuture = CompletableFuture.supplyAsync({
            throw new RuntimeException("Compose service failure")
        })

        when:
        task.composeUp()

        then:
        1 * mockComposeService.upStack(_ as ComposeConfig) >> failedFuture
        RuntimeException e = thrown()
        e.message.contains("Failed to start compose stack 'fail-stack'")
        e.message.contains("Compose service failure")
    }

    def "composeUp throws RuntimeException with original cause"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        
        task.composeFiles.from(composeFile)
        task.projectName.set('cause-project')
        task.stackName.set('cause-stack')
        
        def originalException = new IllegalStateException("Original cause")
        def failedFuture = CompletableFuture.supplyAsync({
            throw originalException
        })

        when:
        task.composeUp()

        then:
        1 * mockComposeService.upStack(_ as ComposeConfig) >> failedFuture
        RuntimeException e = thrown()
        // The ExecutionException from CompletableFuture is wrapped, so check the cause chain
        e.cause.cause == originalException
    }

    // ===== CONFIGURATION TESTS =====

    def "composeUp creates correct ComposeConfig"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        
        task.composeFiles.from(composeFile)
        task.projectName.set('config-project')
        task.stackName.set('config-stack')
        
        def mockComposeState = new ComposeState([:])

        when:
        task.composeUp()

        then:
        1 * mockComposeService.upStack(_) >> { ComposeConfig config ->
            assert config.projectName == 'config-project'
            assert config.stackName == 'config-stack'
            assert config.composeFiles.size() == 1
            assert config.composeFiles[0] == composeFile.toPath()
            assert config.environment.isEmpty()
            return CompletableFuture.completedFuture(mockComposeState)
        }
    }

    // ===== SERVICE INTEGRATION TESTS =====

    def "composeUp logs service details"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        
        task.composeFiles.from(composeFile)
        task.projectName.set('service-project')
        task.stackName.set('service-stack')
        
        def mockComposeState = new ComposeState([
            'web': new ServiceInfo('web-container-id', 'web', 'running', []),
            'db': new ServiceInfo('db-container-id', 'db', 'healthy', [])
        ])

        when:
        task.composeUp()

        then:
        1 * mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(mockComposeState)
        noExceptionThrown()
        // Service details are logged to default Gradle logger
    }

    def "task can be configured with different names"() {
        given:
        def task1 = project.tasks.register('upWebapp', ComposeUpTask).get()
        def task2 = project.tasks.register('upDatabase', ComposeUpTask).get()

        expect:
        task1.name == 'upWebapp'
        task2.name == 'upDatabase'
        task1 != task2
    }
}