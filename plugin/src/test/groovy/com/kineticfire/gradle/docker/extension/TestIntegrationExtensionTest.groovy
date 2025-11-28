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

package com.kineticfire.gradle.docker.extension

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Unit tests for TestIntegrationExtension.
 *
 * Tests core functionality using ProjectBuilder for realistic project simulation.
 * Note: usesCompose() method is tested in integration tests as it requires full plugin setup.
 */
class TestIntegrationExtensionTest extends Specification {

    Project project

    @Subject
    TestIntegrationExtension extension

    def setup() {
        project = ProjectBuilder.builder().build()
        extension = project.objects.newInstance(TestIntegrationExtension, project.name)
    }

    def "constructor initializes with project"() {
        expect:
        extension != null
    }

    def "composeStateFileFor returns Provider with correct path structure"() {
        given:
        String stackName = "testStack"

        when:
        def stateFileProvider = extension.composeStateFileFor(stackName)
        def resolvedPath = stateFileProvider.get()

        then:
        stateFileProvider != null
        resolvedPath != null
        resolvedPath.contains("compose-state")
        resolvedPath.contains("${stackName}-state.json")
    }

    @Unroll
    def "composeStateFileFor works with stack name '#stackName'"() {
        when:
        def stateFileProvider = extension.composeStateFileFor(stackName)
        def resolvedPath = stateFileProvider.get()

        then:
        resolvedPath != null
        resolvedPath.contains("${stackName}-state.json")

        where:
        stackName << ["stack1", "stack-name", "complexStackName123", "test_stack", "UPPERCASE", "with123numbers"]
    }

    def "composeStateFileFor generates unique paths for different stacks"() {
        when:
        def path1 = extension.composeStateFileFor("stack1").get()
        def path2 = extension.composeStateFileFor("stack2").get()

        then:
        path1 != path2
        path1.contains("stack1-state.json")
        path2.contains("stack2-state.json")
    }

    def "composeStateFileFor path is under build directory"() {
        when:
        def stateFilePath = extension.composeStateFileFor("myStack").get()

        then:
        stateFilePath.contains(project.layout.buildDirectory.get().asFile.absolutePath)
    }

    def "composeStateFileFor path includes correct subdirectory"() {
        when:
        def stateFilePath = extension.composeStateFileFor("testStack").get()

        then:
        stateFilePath.contains("compose-state")
    }

    def "composeStateFileFor handles special characters in stack name"() {
        when:
        def stateFilePath = extension.composeStateFileFor("my-test_stack.123").get()

        then:
        stateFilePath.contains("my-test_stack.123-state.json")
    }

    def "multiple calls to composeStateFileFor return consistent paths"() {
        given:
        def stackName = "consistentStack"

        when:
        def path1 = extension.composeStateFileFor(stackName).get()
        def path2 = extension.composeStateFileFor(stackName).get()

        then:
        path1 == path2
    }

    // ===== USES COMPOSE TESTS =====

    def "usesCompose configures suite lifecycle correctly"() {
        given:
        // Apply plugin to get full setup
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        // Get the extension created by the plugin
        def testIntegrationExt = project.extensions.getByType(TestIntegrationExtension)

        // Create compose stack
        def dockerOrchExt = project.extensions.getByType(DockerOrchExtension)
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerOrchExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        // Evaluate project to create compose tasks
        project.evaluate()

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', 'class')

        then:
        noExceptionThrown()
        // Task should have dependencies configured
        testTask.dependsOn.find { it.toString().contains('composeUpTestStack') }
        testTask.finalizedBy.getDependencies(testTask).find { it.name.contains('composeDownTestStack') }
    }

    def "usesCompose configures class lifecycle correctly"() {
        given:
        // Manually create extensions without applying the full plugin
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name)
        def dockerOrchExt = project.objects.newInstance(DockerOrchExtension)
        testIntegrationExt.setDockerOrchExtension(dockerOrchExt)

        // Create compose stack
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerOrchExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', 'class')

        then:
        noExceptionThrown()
        // Should configure system properties for JUnit extension
        testTask.systemProperties['docker.compose.stack'] == 'testStack'
        testTask.systemProperties['docker.compose.lifecycle'] == 'class'
        testTask.systemProperties['docker.compose.project'] == project.name
    }

    def "usesCompose configures method lifecycle correctly"() {
        given:
        // Manually create extensions without applying the full plugin
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name)
        def dockerOrchExt = project.objects.newInstance(DockerOrchExtension)
        testIntegrationExt.setDockerOrchExtension(dockerOrchExt)

        // Create compose stack
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerOrchExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', 'method')

        then:
        noExceptionThrown()
        // Should configure system properties for JUnit extension
        testTask.systemProperties['docker.compose.stack'] == 'testStack'
        testTask.systemProperties['docker.compose.lifecycle'] == 'method'
        testTask.systemProperties['docker.compose.project'] == project.name
    }

    def "usesCompose fails when dockerOrch extension not found"() {
        given:
        // Do NOT apply the plugin - we want to test the error case
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        extension.usesCompose(testTask, 'testStack', 'class')

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("dockerOrch extension not found")
    }

    def "usesCompose fails when compose stack not found"() {
        given:
        // Apply plugin to get dockerOrch extension
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        // Get the extension created by the plugin
        def testIntegrationExt = project.extensions.getByType(TestIntegrationExtension)

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'nonExistentStack', 'class')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Compose stack 'nonExistentStack' not found")
    }

    def "usesCompose fails with invalid lifecycle"() {
        given:
        // Apply plugin to get full setup
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        // Get the extension created by the plugin
        def testIntegrationExt = project.extensions.getByType(TestIntegrationExtension)

        // Create compose stack
        def dockerOrchExt = project.extensions.getByType(DockerOrchExtension)
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerOrchExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', 'invalid')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid lifecycle")
        ex.message.contains("Must be 'class' or 'method'")
    }

    @Unroll
    def "usesCompose handles different lifecycle values case-insensitively: #lifecycle"() {
        given:
        // Apply plugin to get full setup
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        // Get the extension created by the plugin
        def testIntegrationExt = project.extensions.getByType(TestIntegrationExtension)

        // Create compose stack
        def dockerOrchExt = project.extensions.getByType(DockerOrchExtension)
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerOrchExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', lifecycle)

        then:
        noExceptionThrown()

        where:
        lifecycle << ['CLASS', 'Class', 'CLASS', 'Class', 'METHOD', 'Method']
    }
}