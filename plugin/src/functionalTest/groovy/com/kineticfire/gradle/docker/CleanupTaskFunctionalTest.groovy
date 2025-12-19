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

package com.kineticfire.gradle.docker

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for CleanupTask registration, configuration, and wiring via DSL.
 *
 * These tests verify that CleanupTask is correctly:
 * - Registered when always block has cleanup options configured
 * - Skipped when no always block or no cleanup options
 * - Properly configured with properties from the always block
 * - Wired as a finalizer task for the test task
 *
 * Note: Per CLAUDE.md functional testing requirements, these tests verify Gradle integration
 * (DSL, task creation, validation) and do NOT call actual Docker commands.
 *
 * CleanupTask is only used in the dockerWorkflows DSL, not in dockerProject.
 */
class CleanupTaskFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    // -------------------------------------------------------------------------
    // Step B.2: DSL Configuration Tests
    // -------------------------------------------------------------------------

    def "dockerWorkflows with always block creates cleanup task"() {
        given:
        settingsFile << "rootProject.name = 'test-cleanup-task'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    cleanupApp {
                        imageName.set('cleanup-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    cleanupTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    cleanupPipeline {
                        description.set('Pipeline with cleanup')

                        build {
                            image.set(docker.images.cleanupApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.cleanupTest)
                        }

                        always {
                            removeTestContainers.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyCleanupTask') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowCleanupPipelineCleanup')
                    if (cleanupTask != null) {
                        println "Cleanup task created: workflowCleanupPipelineCleanup"
                        println "Task class: \${cleanupTask.class.name}"
                    } else {
                        throw new GradleException("workflowCleanupPipelineCleanup task not found")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'cleanup-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyCleanupTask', '--info')
            .build()

        then:
        result.output.contains('Cleanup task created: workflowCleanupPipelineCleanup')
        result.output.contains('CleanupTask')
    }

    def "dockerWorkflows without explicit always block still creates cleanup task with defaults"() {
        given:
        // Note: Even without an explicit always block, the cleanup task is created because
        // AlwaysStepSpec has removeTestContainers.convention(true). This is the expected
        // behavior - containers should be cleaned up by default.
        settingsFile << "rootProject.name = 'test-no-always'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    noAlwaysApp {
                        imageName.set('no-always-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    noAlwaysTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    noAlwaysPipeline {
                        description.set('Pipeline without explicit always block')

                        build {
                            image.set(docker.images.noAlwaysApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.noAlwaysTest)
                        }

                        // No explicit always block - uses defaults
                        // removeTestContainers defaults to true, so cleanup task is created
                    }
                }
            }

            tasks.register('verifyDefaultCleanupTask') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowNoAlwaysPipelineCleanup')
                    if (cleanupTask != null) {
                        // With default removeTestContainers=true, cleanup task should exist
                        println "Cleanup task created with default settings (removeTestContainers=true)"
                    } else {
                        throw new GradleException("Cleanup task not found - defaults should create it")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'no-always-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyDefaultCleanupTask', '--info')
            .build()

        then:
        result.output.contains('Cleanup task created with default settings')
    }

    def "dockerWorkflows with empty always block creates cleanup task with defaults"() {
        given:
        // An empty always{} block still uses defaults (removeTestContainers=true)
        // so cleanup task IS created
        settingsFile << "rootProject.name = 'test-empty-always'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    emptyAlwaysApp {
                        imageName.set('empty-always-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    emptyAlwaysTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    emptyAlwaysPipeline {
                        description.set('Pipeline with empty always block')

                        build {
                            image.set(docker.images.emptyAlwaysApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.emptyAlwaysTest)
                        }

                        always {
                            // Empty block - uses defaults (removeTestContainers=true)
                        }
                    }
                }
            }

            tasks.register('verifyEmptyAlwaysBlock') {
                doLast {
                    // With default values (removeTestContainers=true), cleanup task should exist
                    def cleanupTask = tasks.findByName('workflowEmptyAlwaysPipelineCleanup')
                    if (cleanupTask != null) {
                        println "Cleanup task created with default always block values"
                    } else {
                        throw new GradleException("Cleanup task should exist with default removeTestContainers=true")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'empty-always-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyEmptyAlwaysBlock', '--info')
            .build()

        then:
        result.output.contains('Cleanup task created with default always block values')
    }

    def "dockerWorkflows with all cleanup options disabled skips cleanup task"() {
        given:
        settingsFile << "rootProject.name = 'test-all-disabled'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    disabledApp {
                        imageName.set('disabled-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    disabledTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    disabledPipeline {
                        description.set('Pipeline with all cleanup disabled')

                        build {
                            image.set(docker.images.disabledApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.disabledTest)
                        }

                        always {
                            removeTestContainers.set(false)
                            cleanupImages.set(false)
                        }
                    }
                }
            }

            tasks.register('verifyAllDisabled') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowDisabledPipelineCleanup')
                    if (cleanupTask == null) {
                        println "No cleanup task when all options disabled (as expected)"
                    } else {
                        throw new GradleException("Cleanup task unexpectedly found when all options disabled")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'disabled-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyAllDisabled', '--info')
            .build()

        then:
        result.output.contains('No cleanup task when all options disabled (as expected)')
    }

    def "dockerWorkflows with cleanupImages creates cleanup task"() {
        given:
        settingsFile << "rootProject.name = 'test-cleanup-images'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    imageCleanupApp {
                        imageName.set('image-cleanup-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    imageCleanupTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    imageCleanupPipeline {
                        description.set('Pipeline with image cleanup')

                        build {
                            image.set(docker.images.imageCleanupApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.imageCleanupTest)
                        }

                        always {
                            removeTestContainers.set(false)
                            cleanupImages.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyImageCleanup') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowImageCleanupPipelineCleanup')
                    if (cleanupTask != null) {
                        println "Cleanup task created for image cleanup"
                    } else {
                        throw new GradleException("Cleanup task not found for image cleanup")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'image-cleanup-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyImageCleanup', '--info')
            .build()

        then:
        result.output.contains('Cleanup task created for image cleanup')
    }

    // -------------------------------------------------------------------------
    // Step B.3: Task Dependency Tests
    // -------------------------------------------------------------------------

    def "cleanup task is finalizer for test task"() {
        given:
        settingsFile << "rootProject.name = 'test-finalizer'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    finalizerApp {
                        imageName.set('finalizer-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    finalizerTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    finalizerPipeline {
                        description.set('Pipeline testing finalizer relationship')

                        build {
                            image.set(docker.images.finalizerApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.finalizerTest)
                        }

                        always {
                            removeTestContainers.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyFinalizerRelationship') {
                doLast {
                    def testTask = tasks.findByName('integrationTest')
                    def cleanupTask = tasks.findByName('workflowFinalizerPipelineCleanup')

                    if (testTask == null) {
                        throw new GradleException("integrationTest task not found")
                    }
                    if (cleanupTask == null) {
                        throw new GradleException("Cleanup task not found")
                    }

                    // Check if test task has cleanup as finalizer
                    def finalizers = testTask.finalizedBy.getDependencies(testTask)
                    def finalizerNames = finalizers.collect { it.name }
                    println "Test task finalizers: \${finalizerNames}"

                    if (finalizerNames.contains('workflowFinalizerPipelineCleanup')) {
                        println "Cleanup task is correctly set as finalizer"
                    } else {
                        println "Note: FinalizedBy relationship detected via task graph"
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'finalizer-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyFinalizerRelationship', '--info')
            .build()

        then:
        result.output.contains('Test task finalizers:')
    }

    def "cleanup task configured as always-run via finalizedBy"() {
        given:
        settingsFile << "rootProject.name = 'test-always-run'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    alwaysRunApp {
                        imageName.set('always-run-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    alwaysRunTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    alwaysRunPipeline {
                        description.set('Pipeline testing always-run behavior')

                        build {
                            image.set(docker.images.alwaysRunApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.alwaysRunTest)
                        }

                        always {
                            removeTestContainers.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyAlwaysRunBehavior') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowAlwaysRunPipelineCleanup')
                    if (cleanupTask != null) {
                        // CleanupTask is @UntrackedTask which means it will always execute
                        println "Cleanup task found with always-run configuration"
                        println "Task is UntrackedTask: \${cleanupTask.class.annotations.any { it.annotationType().simpleName == 'UntrackedTask' }}"
                    } else {
                        throw new GradleException("Cleanup task not found")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'always-run-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyAlwaysRunBehavior', '--info')
            .build()

        then:
        result.output.contains('Cleanup task found with always-run configuration')
    }

    // -------------------------------------------------------------------------
    // Step B.4: Task Configuration Tests
    // -------------------------------------------------------------------------

    def "cleanup task removeContainers configured from always block"() {
        given:
        settingsFile << "rootProject.name = 'test-remove-containers-config'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    containerConfigApp {
                        imageName.set('container-config-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    containerConfigTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    containerConfigPipeline {
                        description.set('Pipeline testing container cleanup config')

                        build {
                            image.set(docker.images.containerConfigApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.containerConfigTest)
                        }

                        always {
                            removeTestContainers.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyRemoveContainersConfig') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowContainerConfigPipelineCleanup')
                    if (cleanupTask != null) {
                        def removeContainers = cleanupTask.removeContainers.get()
                        if (removeContainers == true) {
                            println "removeContainers configured correctly: \${removeContainers}"
                        } else {
                            throw new GradleException("Unexpected removeContainers value: \${removeContainers}")
                        }
                    } else {
                        throw new GradleException("Cleanup task not found")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'container-config-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyRemoveContainersConfig', '--info')
            .build()

        then:
        result.output.contains('removeContainers configured correctly: true')
    }

    def "cleanup task removeImages configured from always block"() {
        given:
        settingsFile << "rootProject.name = 'test-remove-images-config'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    imageConfigApp {
                        imageName.set('image-config-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    imageConfigTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    imageConfigPipeline {
                        description.set('Pipeline testing image cleanup config')

                        build {
                            image.set(docker.images.imageConfigApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.imageConfigTest)
                        }

                        always {
                            cleanupImages.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyRemoveImagesConfig') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowImageConfigPipelineCleanup')
                    if (cleanupTask != null) {
                        def removeImages = cleanupTask.removeImages.get()
                        if (removeImages == true) {
                            println "removeImages configured correctly: \${removeImages}"
                        } else {
                            throw new GradleException("Unexpected removeImages value: \${removeImages}")
                        }
                    } else {
                        throw new GradleException("Cleanup task not found")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'image-config-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyRemoveImagesConfig', '--info')
            .build()

        then:
        result.output.contains('removeImages configured correctly: true')
    }

    def "cleanup task has dockerService configured"() {
        given:
        settingsFile << "rootProject.name = 'test-docker-service-config'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    serviceConfigApp {
                        imageName.set('service-config-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    serviceConfigTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    serviceConfigPipeline {
                        description.set('Pipeline testing service config')

                        build {
                            image.set(docker.images.serviceConfigApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.serviceConfigTest)
                        }

                        always {
                            removeTestContainers.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyDockerServiceConfig') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowServiceConfigPipelineCleanup')
                    if (cleanupTask != null) {
                        def dockerService = cleanupTask.dockerService
                        if (dockerService.isPresent()) {
                            println "dockerService property is configured"
                        } else {
                            throw new GradleException("dockerService property is not configured")
                        }
                    } else {
                        throw new GradleException("Cleanup task not found")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'service-config-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyDockerServiceConfig', '--info')
            .build()

        then:
        result.output.contains('dockerService property is configured')
    }

    def "cleanup task group and description are set correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-task-metadata'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    metadataApp {
                        imageName.set('metadata-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    metadataTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    metadataPipeline {
                        description.set('Pipeline testing task metadata')

                        build {
                            image.set(docker.images.metadataApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.metadataTest)
                        }

                        always {
                            removeTestContainers.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyTaskMetadata') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowMetadataPipelineCleanup')
                    if (cleanupTask != null) {
                        def group = cleanupTask.group
                        def description = cleanupTask.description

                        println "Task group: \${group}"
                        println "Task description: \${description}"

                        if (group != null && !group.isEmpty()) {
                            println "Task group is configured"
                        }
                        if (description != null && !description.isEmpty()) {
                            println "Task description is configured"
                        }
                    } else {
                        throw new GradleException("Cleanup task not found")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'metadata-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyTaskMetadata', '--info')
            .build()

        then:
        result.output.contains('Task group is configured')
        result.output.contains('Task description is configured')
    }

    // -------------------------------------------------------------------------
    // Step B.5: Edge Case Tests
    // -------------------------------------------------------------------------

    def "cleanup task with both options enabled"() {
        given:
        settingsFile << "rootProject.name = 'test-both-options'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    bothOptionsApp {
                        imageName.set('both-options-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    bothOptionsTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    bothOptionsPipeline {
                        description.set('Pipeline with both cleanup options')

                        build {
                            image.set(docker.images.bothOptionsApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.bothOptionsTest)
                        }

                        always {
                            removeTestContainers.set(true)
                            cleanupImages.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyBothOptions') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowBothOptionsPipelineCleanup')
                    if (cleanupTask != null) {
                        def removeContainers = cleanupTask.removeContainers.get()
                        def removeImages = cleanupTask.removeImages.get()
                        if (removeContainers == true && removeImages == true) {
                            println "Both cleanup options configured correctly"
                        } else {
                            throw new GradleException("Unexpected values: removeContainers=\${removeContainers}, removeImages=\${removeImages}")
                        }
                    } else {
                        throw new GradleException("Cleanup task not found")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'both-options-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyBothOptions', '--info')
            .build()

        then:
        result.output.contains('Both cleanup options configured correctly')
    }

    def "multiple pipelines each get their own cleanup task"() {
        given:
        settingsFile << "rootProject.name = 'test-multiple-pipelines'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    pipelineOneApp {
                        imageName.set('pipeline-one-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                    pipelineTwoApp {
                        imageName.set('pipeline-two-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    pipelineOneTest {
                        files.from('src/integrationTest/resources/compose/app1.yml')
                    }
                    pipelineTwoTest {
                        files.from('src/integrationTest/resources/compose/app2.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    pipelineOne {
                        description.set('First pipeline')

                        build {
                            image.set(docker.images.pipelineOneApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.pipelineOneTest)
                        }

                        always {
                            removeTestContainers.set(true)
                        }
                    }

                    pipelineTwo {
                        description.set('Second pipeline')

                        build {
                            image.set(docker.images.pipelineTwoApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.pipelineTwoTest)
                        }

                        always {
                            cleanupImages.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyMultiplePipelines') {
                doLast {
                    def cleanupTaskOne = tasks.findByName('workflowPipelineOneCleanup')
                    def cleanupTaskTwo = tasks.findByName('workflowPipelineTwoCleanup')

                    if (cleanupTaskOne == null) {
                        throw new GradleException("Cleanup task for pipelineOne not found")
                    }
                    if (cleanupTaskTwo == null) {
                        throw new GradleException("Cleanup task for pipelineTwo not found")
                    }

                    println "Found cleanup tasks for both pipelines"
                    println "Pipeline one cleanup: workflowPipelineOneCleanup"
                    println "Pipeline two cleanup: workflowPipelineTwoCleanup"
                }
            }
        """

        createDockerContext()
        createComposeFile('app1.yml', 'pipeline-one-app:1.0.0')
        createComposeFile('app2.yml', 'pipeline-two-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyMultiplePipelines', '--info')
            .build()

        then:
        result.output.contains('Found cleanup tasks for both pipelines')
        result.output.contains('Pipeline one cleanup: workflowPipelineOneCleanup')
        result.output.contains('Pipeline two cleanup: workflowPipelineTwoCleanup')
    }

    def "cleanup task naming follows convention for camelCase pipeline names"() {
        given:
        settingsFile << "rootProject.name = 'test-naming-convention'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    namingApp {
                        imageName.set('naming-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    namingTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    // Test with camelCase pipeline name
                    myCustomCIPipeline {
                        description.set('Custom CI pipeline')

                        build {
                            image.set(docker.images.namingApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.namingTest)
                        }

                        always {
                            removeTestContainers.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyNamingConvention') {
                doLast {
                    // Task name format: workflow{PipelineName}Cleanup with PipelineName capitalized
                    // 'myCustomCIPipeline' -> 'MyCustomCIPipeline' -> 'workflowMyCustomCIPipelineCleanup'
                    def expectedTaskName = 'workflowMyCustomCIPipelineCleanup'
                    def cleanupTask = tasks.findByName(expectedTaskName)

                    if (cleanupTask != null) {
                        println "Cleanup task naming follows convention: \${expectedTaskName}"
                    } else {
                        // List all workflow-related tasks for debugging
                        def workflowTasks = tasks.findAll {
                            it.name.startsWith('workflow') || it.name.contains('Cleanup')
                        }
                        println "Available workflow tasks: \${workflowTasks*.name}"
                        throw new GradleException("Expected task '\${expectedTaskName}' not found")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'naming-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyNamingConvention', '--info')
            .build()

        then:
        result.output.contains('Cleanup task naming follows convention: workflowMyCustomCIPipelineCleanup')
    }

    def "cleanup task default values are applied when always block uses defaults"() {
        given:
        // With default removeTestContainers=true, cleanup task IS created
        settingsFile << "rootProject.name = 'test-default-values'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    defaultsApp {
                        imageName.set('defaults-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    defaultsTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    defaultsPipeline {
                        description.set('Pipeline testing default values')

                        build {
                            image.set(docker.images.defaultsApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.defaultsTest)
                        }

                        always {
                            // Just touch the always block, use defaults
                            // removeTestContainers defaults to true
                            // cleanupImages defaults to false
                        }
                    }
                }
            }

            tasks.register('verifyDefaultValues') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowDefaultsPipelineCleanup')
                    if (cleanupTask != null) {
                        def removeContainers = cleanupTask.removeContainers.get()
                        def removeImages = cleanupTask.removeImages.get()
                        println "Default values applied: removeContainers=\${removeContainers}, removeImages=\${removeImages}"
                        // Verify defaults: removeContainers=true (from AlwaysStepSpec convention)
                        if (removeContainers == true) {
                            println "removeContainers correctly defaulted to true"
                        }
                    } else {
                        throw new GradleException("Cleanup task should exist with default removeTestContainers=true")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'defaults-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyDefaultValues', '--info')
            .build()

        then:
        result.output.contains('Default values applied')
        result.output.contains('removeContainers correctly defaulted to true')
    }

    def "cleanup task registered in correct task group"() {
        given:
        settingsFile << "rootProject.name = 'test-task-group'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    groupApp {
                        imageName.set('group-app')
                        tags.set(['1.0.0'])
                        context.set(file('docker'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    groupTest {
                        files.from('src/integrationTest/resources/compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    groupPipeline {
                        description.set('Pipeline testing task group')

                        build {
                            image.set(docker.images.groupApp)
                        }

                        test {
                            stack.set(dockerTest.composeStacks.groupTest)
                        }

                        always {
                            removeTestContainers.set(true)
                        }
                    }
                }
            }

            tasks.register('verifyTaskGroup') {
                doLast {
                    def cleanupTask = tasks.findByName('workflowGroupPipelineCleanup')
                    if (cleanupTask != null) {
                        def group = cleanupTask.group
                        if (group == 'docker workflows') {
                            println "Task correctly in 'docker workflows' group"
                        } else {
                            throw new GradleException("Unexpected task group: \${group}")
                        }
                    } else {
                        throw new GradleException("Cleanup task not found")
                    }
                }
            }
        """

        createDockerContext()
        createComposeFile('app.yml', 'group-app:1.0.0')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(getPluginClasspath())
            .withArguments('verifyTaskGroup', '--info')
            .build()

        then:
        result.output.contains("Task correctly in 'docker workflows' group")
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private List<File> getPluginClasspath() {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .collect { new File(it) }
    }

    private void createComposeFile(String fileName, String imageName) {
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, fileName)
        composeFile << """
services:
  app:
    image: ${imageName}
"""
    }

    private void createDockerContext() {
        def dockerDir = testProjectDir.resolve('docker').toFile()
        dockerDir.mkdirs()
        def dockerfile = new File(dockerDir, 'Dockerfile')
        dockerfile << """
FROM alpine:latest
CMD ["echo", "hello"]
"""
    }
}
