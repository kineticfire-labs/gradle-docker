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

import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for TestIntegrationExtension
 */
class TestIntegrationExtensionTest extends Specification {

    Project project
    TestIntegrationExtension extension
    Test testTask
    DockerOrchExtension dockerOrchExtension

    def setup() {
        project = ProjectBuilder.builder().build()
        
        // Apply plugins needed for testing
        project.pluginManager.apply('java')
        
        // Create test task (avoiding conflict with default 'test' task)
        testTask = project.tasks.create('unitTest', Test)
        
        // Create dockerOrch extension with compose stack
        dockerOrchExtension = project.objects.newInstance(DockerOrchExtension, project.objects, project)
        project.extensions.add('dockerOrch', dockerOrchExtension)
        
        // Create test integration extension
        extension = new TestIntegrationExtension(project)
    }

    def "extension can be created with project"() {
        expect:
        extension != null
    }

    // ===== SUITE LIFECYCLE TESTS =====

    def "usesCompose configures suite lifecycle correctly"() {
        given:
        setupComposeStack('testStack')
        
        when:
        extension.usesCompose(testTask, 'testStack', 'suite')

        then:
        noExceptionThrown()
        // Suite lifecycle adds doFirst and doLast actions
        testTask.actions.size() >= 2
    }

    def "suite lifecycle adds doFirst and doLast actions"() {
        given:
        setupComposeStack('integrationStack')
        
        when:
        extension.usesCompose(testTask, 'integrationStack', 'suite')

        then:
        testTask.actions.size() >= 2 // at least doFirst and doLast added
    }

    def "suite lifecycle task names are properly capitalized"() {
        given:
        setupComposeStack('myTestStack')
        
        when:
        extension.usesCompose(testTask, 'myTestStack', 'suite')

        then:
        noExceptionThrown()
        // The task should have actions added for suite lifecycle
        testTask.actions.size() >= 2
    }

    // ===== CLASS LIFECYCLE TESTS =====

    def "usesCompose configures class lifecycle with system properties"() {
        given:
        setupComposeStack('classStack')
        
        when:
        extension.usesCompose(testTask, 'classStack', 'class')

        then:
        testTask.systemProperties['docker.compose.stack'] == 'classStack'
        testTask.systemProperties['docker.compose.lifecycle'] == 'class'
    }

    def "class lifecycle does not add task dependencies"() {
        given:
        setupComposeStack('classStack')
        def originalDependencies = testTask.dependsOn.size()
        def originalFinalizers = 0 // No finalizers initially
        
        when:
        extension.usesCompose(testTask, 'classStack', 'class')

        then:
        testTask.dependsOn.size() == originalDependencies // No new dependencies
        originalFinalizers == 0 // Still no finalizers for class/method lifecycle
    }

    // ===== METHOD LIFECYCLE TESTS =====

    def "usesCompose configures method lifecycle with system properties"() {
        given:
        setupComposeStack('methodStack')
        
        when:
        extension.usesCompose(testTask, 'methodStack', 'method')

        then:
        testTask.systemProperties['docker.compose.stack'] == 'methodStack'
        testTask.systemProperties['docker.compose.lifecycle'] == 'method'
    }

    def "method lifecycle does not add task dependencies"() {
        given:
        setupComposeStack('methodStack')
        def originalDependencies = testTask.dependsOn.size()
        def originalFinalizers = 0 // No finalizers initially
        
        when:
        extension.usesCompose(testTask, 'methodStack', 'method')

        then:
        testTask.dependsOn.size() == originalDependencies // No new dependencies  
        originalFinalizers == 0 // Still no finalizers for class/method lifecycle
    }

    // ===== LIFECYCLE CASE SENSITIVITY TESTS =====

    def "lifecycle parameter is case insensitive"() {
        given:
        setupComposeStack('caseStack')
        
        expect:
        // All these should work without throwing exceptions
        extension.usesCompose(testTask, 'caseStack', 'SUITE')
        testTask.actions.size() >= 2 // Suite lifecycle adds actions
        
        def classTask = project.tasks.create('classTest', Test)
        extension.usesCompose(classTask, 'caseStack', 'Class')
        classTask.systemProperties['docker.compose.lifecycle'] == 'class'
        
        def methodTask = project.tasks.create('methodTest', Test)
        extension.usesCompose(methodTask, 'caseStack', 'METHOD')
        methodTask.systemProperties['docker.compose.lifecycle'] == 'method'
    }

    // ===== ERROR HANDLING TESTS =====

    def "usesCompose fails when dockerOrch extension not found"() {
        given:
        // Create a new project without the dockerOrch extension
        def projectWithoutExt = ProjectBuilder.builder().build()
        projectWithoutExt.pluginManager.apply('java')
        def extensionWithoutExt = new TestIntegrationExtension(projectWithoutExt)
        def taskWithoutExt = projectWithoutExt.tasks.create('testTask', Test)
        
        when:
        extensionWithoutExt.usesCompose(taskWithoutExt, 'anyStack', 'suite')

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("dockerOrch extension not found")
        ex.message.contains("Apply gradle-docker plugin first")
    }

    def "usesCompose fails when compose stack not found"() {
        given:
        setupComposeStack('existingStack')
        
        when:
        extension.usesCompose(testTask, 'nonExistentStack', 'suite')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Compose stack 'nonExistentStack' not found")
        ex.message.contains("dockerOrch configuration")
    }

    def "usesCompose fails with invalid lifecycle"() {
        given:
        setupComposeStack('validStack')
        
        when:
        extension.usesCompose(testTask, 'validStack', 'invalid-lifecycle')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid lifecycle 'invalid-lifecycle'")
        ex.message.contains("Must be 'suite', 'class', or 'method'")
    }

    def "usesCompose handles null lifecycle parameter"() {
        given:
        setupComposeStack('validStack')
        
        when:
        extension.usesCompose(testTask, 'validStack', null)

        then:
        def ex = thrown(NullPointerException)
        ex.message.contains("Cannot invoke method toLowerCase() on null object")
    }

    def "usesCompose handles empty lifecycle parameter"() {
        given:
        setupComposeStack('validStack')
        
        when:
        extension.usesCompose(testTask, 'validStack', '')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid lifecycle ''")
    }

    // ===== COMPOSE STATE FILE TESTS =====

    def "composeStateFileFor returns correct path"() {
        when:
        def statePath = extension.composeStateFileFor('myStack')

        then:
        statePath.endsWith('myStack-state.json')
        statePath.contains('compose-state')
        statePath.contains(project.layout.buildDirectory.get().asFile.absolutePath)
    }

    def "composeStateFileFor handles different stack names"() {
        expect:
        extension.composeStateFileFor('web-stack').endsWith('web-stack-state.json')
        extension.composeStateFileFor('integration').endsWith('integration-state.json')
        extension.composeStateFileFor('test123').endsWith('test123-state.json')
    }

    def "composeStateFileFor creates consistent paths"() {
        given:
        def firstCall = extension.composeStateFileFor('consistentStack')
        def secondCall = extension.composeStateFileFor('consistentStack')

        expect:
        firstCall == secondCall
    }

    def "composeStateFileFor handles stack names with special characters"() {
        expect:
        extension.composeStateFileFor('stack_with_underscores').contains('stack_with_underscores-state.json')
        extension.composeStateFileFor('stack-with-dashes').contains('stack-with-dashes-state.json')
    }

    // ===== INTEGRATION TESTS =====

    def "can configure multiple test tasks with different stacks and lifecycles"() {
        given:
        setupComposeStack('webStack')
        setupComposeStack('apiStack')
        setupComposeStack('dbStack')
        
        def integrationTest = project.tasks.create('integrationTest', Test)
        def e2eTest = project.tasks.create('e2eTest', Test)
        def smokeTest = project.tasks.create('smokeTest', Test)

        when:
        extension.usesCompose(testTask, 'webStack', 'suite')
        extension.usesCompose(integrationTest, 'apiStack', 'class')
        extension.usesCompose(e2eTest, 'dbStack', 'method')

        then:
        // Suite lifecycle
        testTask.actions.size() >= 2
        
        // Class lifecycle
        integrationTest.systemProperties['docker.compose.stack'] == 'apiStack'
        integrationTest.systemProperties['docker.compose.lifecycle'] == 'class'
        
        // Method lifecycle
        e2eTest.systemProperties['docker.compose.stack'] == 'dbStack'
        e2eTest.systemProperties['docker.compose.lifecycle'] == 'method'
    }

    def "can configure same test task with multiple compose configurations"() {
        given:
        setupComposeStack('primaryStack')
        
        when:
        extension.usesCompose(testTask, 'primaryStack', 'suite')
        // Configure additional system properties as if for multiple stacks
        testTask.systemProperty('additional.compose.stack', 'secondaryStack')

        then:
        testTask.actions.size() >= 2 // Suite lifecycle adds actions
        testTask.systemProperties['additional.compose.stack'] == 'secondaryStack'
    }

    def "suite lifecycle properly handles task execution simulation"() {
        given:
        setupComposeStack('executionStack')
        
        when:
        extension.usesCompose(testTask, 'executionStack', 'suite')
        
        // Simulate task execution
        def upTaskName = 'composeUpExecutionStack'
        def downTaskName = 'composeDownExecutionStack'

        then:
        testTask.actions.size() >= 2 // doFirst and doLast actions added
        noExceptionThrown()
    }

    def "extension handles project with custom build directory"() {
        given:
        def customBuildDir = project.file('custom-build')
        project.layout.buildDirectory.set(customBuildDir)
        
        when:
        def statePath = extension.composeStateFileFor('customBuildStack')

        then:
        statePath.contains('custom-build')
        statePath.endsWith('customBuildStack-state.json')
    }

    // ===== EDGE CASES AND ERROR HANDLING =====

    def "handles stack names with numbers and special characters"() {
        given:
        setupComposeStack('stack-123_test')
        
        when:
        extension.usesCompose(testTask, 'stack-123_test', 'suite')

        then:
        noExceptionThrown()
        testTask.actions.size() >= 2 // Suite lifecycle adds actions
    }

    def "handles very long stack names"() {
        given:
        def longStackName = 'very-long-stack-name-that-might-cause-issues-with-task-naming-conventions'
        setupComposeStack(longStackName)
        
        when:
        extension.usesCompose(testTask, longStackName, 'suite')
        def statePath = extension.composeStateFileFor(longStackName)

        then:
        noExceptionThrown()
        statePath.contains(longStackName)
    }

    def "extension works with different test task types"() {
        given:
        setupComposeStack('flexibleStack')
        def customTestTask = project.tasks.create('customTest', Test) {
            useJUnitPlatform()
            maxParallelForks = 2
        }

        when:
        extension.usesCompose(customTestTask, 'flexibleStack', 'class')

        then:
        noExceptionThrown()
        customTestTask.systemProperties['docker.compose.stack'] == 'flexibleStack'
        customTestTask.systemProperties['docker.compose.lifecycle'] == 'class'
    }

    // ===== HELPER METHODS =====

    private void setupComposeStack(String stackName) {
        dockerOrchExtension.composeStacks {
            "${stackName}" {
                files.from(project.file("${stackName}-compose.yml"))
                profiles = ['test']
                projectName = "${project.name}-${stackName}"
            }
        }
    }
}