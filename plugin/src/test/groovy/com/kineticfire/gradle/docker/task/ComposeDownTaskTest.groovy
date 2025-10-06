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
import com.kineticfire.gradle.docker.service.ComposeService
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for ComposeDownTask
 */
class ComposeDownTaskTest extends Specification {

    Project project
    ComposeDownTask task
    ComposeService mockComposeService = Mock()

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testComposeDown', ComposeDownTask).get()
        task.composeService.set(mockComposeService)
    }

    // ===== BASIC TASK TESTS =====

    def "task can be created"() {
        expect:
        task != null
        task instanceof ComposeDownTask
    }

    def "task has correct group and description"() {
        expect:
        task.group == 'docker compose'
        task.description == 'Stop Docker Compose stack'
    }

    def "task extends DefaultTask"() {
        expect:
        task instanceof org.gradle.api.DefaultTask
    }

    // ===== PROPERTY TESTS =====

    def "composeService property can be set and retrieved"() {
        given:
        def service = Mock(ComposeService)

        when:
        task.composeService.set(service)

        then:
        task.composeService.get() == service
    }

    def "projectName property can be set and retrieved"() {
        given:
        def projectName = 'test-project'

        when:
        task.projectName.set(projectName)

        then:
        task.projectName.get() == projectName
    }

    def "stackName property can be set and retrieved"() {
        given:
        def stackName = 'test-stack'

        when:
        task.stackName.set(stackName)

        then:
        task.stackName.get() == stackName
    }

    // ===== TASK ACTION TESTS =====

    def "composeDown executes successfully with valid configuration"() {
        given:
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        
        and:
        1 * mockComposeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        task.composeDown()

        then:
        noExceptionThrown()
    }

    def "composeDown handles service failure gracefully"() {
        given:
        task.projectName.set('failing-project')
        task.stackName.set('failing-stack')
        
        and:
        def exception = new RuntimeException("Docker compose failed")
        def failedFuture = CompletableFuture.failedFuture(exception)
        1 * mockComposeService.downStack('failing-project') >> failedFuture

        when:
        task.composeDown()

        then:
        def thrownException = thrown(RuntimeException)
        thrownException.message.contains("Failed to stop compose stack 'failing-stack'")
        thrownException.cause != null
    }

    def "composeDown throws exception when projectName is not set"() {
        given:
        task.stackName.set('test-stack')
        // projectName is not set

        when:
        task.composeDown()

        then:
        thrown(Exception) // Could be IllegalStateException or similar
    }

    def "composeDown throws exception when stackName is not set"() {
        given:
        task.projectName.set('test-project')
        // stackName is not set

        when:
        task.composeDown()

        then:
        thrown(Exception) // Could be IllegalStateException or similar
    }

    def "composeDown throws exception when composeService is not set"() {
        given:
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        task.composeService.set(null)

        when:
        task.composeDown()

        then:
        thrown(Exception) // Could be NullPointerException or similar
    }

    def "composeDown handles interrupted execution"() {
        given:
        task.projectName.set('interrupted-project')
        task.stackName.set('interrupted-stack')
        
        and:
        def interruptedException = new InterruptedException("Thread interrupted")
        def failedFuture = CompletableFuture.failedFuture(interruptedException)
        1 * mockComposeService.downStack('interrupted-project') >> failedFuture

        when:
        task.composeDown()

        then:
        def thrownException = thrown(RuntimeException)
        thrownException.message.contains("Failed to stop compose stack 'interrupted-stack'")
        thrownException.cause != null
    }

    def "composeDown with special characters in names"() {
        given:
        task.projectName.set('project-with_special.chars')
        task.stackName.set('stack with spaces & symbols')
        
        and:
        1 * mockComposeService.downStack('project-with_special.chars') >> CompletableFuture.completedFuture(null)

        when:
        task.composeDown()

        then:
        noExceptionThrown()
    }

    def "composeDown with empty project name"() {
        given:
        task.projectName.set('')
        task.stackName.set('test-stack')
        
        and:
        1 * mockComposeService.downStack('') >> CompletableFuture.completedFuture(null)

        when:
        task.composeDown()

        then:
        noExceptionThrown()
    }

    // ===== COMPOSE FILES CONFIGURATION PATH TESTS =====

    def "composeDown with compose files uses ComposeConfig path"() {
        given:
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = '''services:
  web:
    image: nginx'''
        task.composeFiles.setFrom(composeFile)
        
        and:
        1 * mockComposeService.downStack(_ as ComposeConfig) >> { ComposeConfig config ->
            assert config.projectName == 'test-project'
            assert config.stackName == 'test-stack'
            assert config.composeFiles.size() == 1
            assert config.composeFiles[0].fileName.toString() == 'docker-compose.yml'
            assert config.envFiles.isEmpty()
            return CompletableFuture.completedFuture(null)
        }

        when:
        task.composeDown()

        then:
        noExceptionThrown()
    }

    def "composeDown with compose files and env files handles both collections"() {
        given:
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = '''services:
  web:
    image: nginx'''
        task.composeFiles.setFrom(composeFile)
        
        def envFile = project.file('.env')
        envFile.text = 'TEST_VAR=value'
        task.envFiles.setFrom(envFile)
        
        and:
        1 * mockComposeService.downStack(_ as ComposeConfig) >> { ComposeConfig config ->
            assert config.projectName == 'test-project'
            assert config.stackName == 'test-stack'
            assert config.composeFiles.size() == 1
            assert config.envFiles.size() == 1
            assert config.envFiles[0].fileName.toString() == '.env'
            return CompletableFuture.completedFuture(null)
        }

        when:
        task.composeDown()

        then:
        noExceptionThrown()
    }

    def "composeDown with multiple compose files"() {
        given:
        task.projectName.set('multi-compose-project')
        task.stackName.set('multi-compose-stack')
        
        def composeFile1 = project.file('docker-compose.yml')
        composeFile1.parentFile.mkdirs()
        composeFile1.text = '''services:
  web:
    image: nginx'''
        
        def composeFile2 = project.file('docker-compose.override.yml')
        composeFile2.text = '''services:
  web:
    ports:
      - "8080:80"'''
        
        task.composeFiles.setFrom(composeFile1, composeFile2)
        
        and:
        1 * mockComposeService.downStack(_ as ComposeConfig) >> { ComposeConfig config ->
            assert config.composeFiles.size() == 2
            def fileNames = config.composeFiles.collect { it.fileName.toString() }
            assert 'docker-compose.yml' in fileNames
            assert 'docker-compose.override.yml' in fileNames
            return CompletableFuture.completedFuture(null)
        }

        when:
        task.composeDown()

        then:
        noExceptionThrown()
    }

    def "composeDown with compose files but no env files handles null envFiles gracefully"() {
        given:
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = '''services:
  web:
    image: nginx'''
        task.composeFiles.setFrom(composeFile)
        // envFiles is not set (null)
        
        and:
        1 * mockComposeService.downStack(_ as ComposeConfig) >> { ComposeConfig config ->
            assert config.envFiles.isEmpty()
            return CompletableFuture.completedFuture(null)
        }

        when:
        task.composeDown()

        then:
        noExceptionThrown()
    }

    def "composeDown with compose files handles service failure"() {
        given:
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = '''services:
  web:
    image: nginx'''
        task.composeFiles.setFrom(composeFile)
        
        and:
        def exception = new RuntimeException("Compose down failed")
        def failedFuture = CompletableFuture.failedFuture(exception)
        1 * mockComposeService.downStack(_ as ComposeConfig) >> failedFuture

        when:
        task.composeDown()

        then:
        def thrownException = thrown(RuntimeException)
        thrownException.message.contains("Failed to stop compose stack 'test-stack'")
        thrownException.cause != null
    }

    def "composeDown with empty compose files falls back to project name path"() {
        given:
        task.projectName.set('test-project')
        task.stackName.set('test-stack')
        // composeFiles is set but empty
        task.composeFiles.setFrom([])
        
        and:
        1 * mockComposeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        task.composeDown()

        then:
        noExceptionThrown()
    }

    // ===== INTEGRATION-STYLE TESTS =====

    def "task can be configured through Gradle DSL"() {
        given:
        project.tasks.register('composeDown', ComposeDownTask) { task ->
            projectName.set('dsl-project')
            stackName.set('dsl-stack')
        }

        when:
        def configuredTask = project.tasks.getByName('composeDown') as ComposeDownTask

        then:
        configuredTask.projectName.get() == 'dsl-project'
        configuredTask.stackName.get() == 'dsl-stack'
    }

    def "task properties exist and can be accessed"() {
        expect:
        // Verify that properties exist and can be accessed
        task.composeService != null
        task.projectName != null
        task.stackName != null
        task.composeFiles != null
        task.envFiles != null
    }
}