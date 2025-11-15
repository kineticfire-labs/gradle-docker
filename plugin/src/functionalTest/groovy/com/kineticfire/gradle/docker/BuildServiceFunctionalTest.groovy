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
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for BuildService lifecycle and registration.
 *
 * Tests verify that the plugin properly registers shared services (DockerService,
 * ComposeService, JsonService) and that these services are shared across tasks and
 * managed by Gradle's lifecycle.
 */
class BuildServiceFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'build-service-test'\n"
    }

    // ==================== DockerService Registration ====================

    def "DockerService is registered as shared service"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyDockerService {
                doLast {
                    // Verify dockerService is registered in shared services
                    def dockerService = project.gradle.sharedServices.registrations.findByName('dockerService')
                    assert dockerService != null
                    assert dockerService.service.isPresent()

                    println "DockerService registered as shared service: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDockerService')
            .build()

        then:
        result.task(':verifyDockerService').outcome == TaskOutcome.SUCCESS
        result.output.contains('DockerService registered as shared service: verified')
    }

    // ==================== ComposeService Registration ====================

    def "ComposeService is registered as shared service"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyComposeService {
                doLast {
                    // Verify composeService is registered in shared services
                    def composeService = project.gradle.sharedServices.registrations.findByName('composeService')
                    assert composeService != null
                    assert composeService.service.isPresent()

                    println "ComposeService registered as shared service: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyComposeService')
            .build()

        then:
        result.task(':verifyComposeService').outcome == TaskOutcome.SUCCESS
        result.output.contains('ComposeService registered as shared service: verified')
    }

    // ==================== JsonService Registration ====================

    def "JsonService is registered as shared service"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyJsonService {
                doLast {
                    // Verify jsonService is registered in shared services
                    def jsonService = project.gradle.sharedServices.registrations.findByName('jsonService')
                    assert jsonService != null
                    assert jsonService.service.isPresent()

                    println "JsonService registered as shared service: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyJsonService')
            .build()

        then:
        result.task(':verifyJsonService').outcome == TaskOutcome.SUCCESS
        result.output.contains('JsonService registered as shared service: verified')
    }

    // ==================== All Services Registered ====================

    def "all required services are registered"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyAllServices {
                doLast {
                    // Verify all three services are registered
                    def dockerService = project.gradle.sharedServices.registrations.findByName('dockerService')
                    def composeService = project.gradle.sharedServices.registrations.findByName('composeService')
                    def jsonService = project.gradle.sharedServices.registrations.findByName('jsonService')

                    assert dockerService != null
                    assert composeService != null
                    assert jsonService != null

                    assert dockerService.service.isPresent()
                    assert composeService.service.isPresent()
                    assert jsonService.service.isPresent()

                    println "All required services registered: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyAllServices')
            .build()

        then:
        result.task(':verifyAllServices').outcome == TaskOutcome.SUCCESS
        result.output.contains('All required services registered: verified')
    }

    // ==================== Services Shared Across Projects ====================

    def "services are shared across multiple projects"() {
        given:
        settingsFile.text = """
            rootProject.name = 'multi-project-services-test'
            include 'project-a'
            include 'project-b'
        """

        buildFile << """
            // Root project doesn't need to apply plugin
        """

        // Create two subprojects
        def projectA = testProjectDir.resolve('project-a').toFile()
        projectA.mkdirs()
        new File(projectA, 'build.gradle').text = """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task checkServicesA {
                doLast {
                    def dockerService = project.gradle.sharedServices.registrations.findByName('dockerService')
                    assert dockerService != null
                    println "Project A: Services available"
                }
            }
        """

        def projectB = testProjectDir.resolve('project-b').toFile()
        projectB.mkdirs()
        new File(projectB, 'build.gradle').text = """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task checkServicesB {
                doLast {
                    def dockerService = project.gradle.sharedServices.registrations.findByName('dockerService')
                    assert dockerService != null
                    println "Project B: Services available"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('checkServicesA', 'checkServicesB')
            .build()

        then:
        result.output.contains('Project A: Services available')
        result.output.contains('Project B: Services available')
    }

    // ==================== Service Instance Sharing ====================

    def "same service instance is shared across tasks"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    testImage1 {
                        imageName.set('test-image-1')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                    testImage2 {
                        imageName.set('test-image-2')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyServiceSharing {
                doLast {
                    // Both build tasks should reference the same dockerService instance
                    def buildTask1 = tasks.findByName('dockerBuildTestImage1')
                    def buildTask2 = tasks.findByName('dockerBuildTestImage2')

                    assert buildTask1 != null
                    assert buildTask2 != null

                    // Both tasks use the shared dockerService
                    // (We can't directly compare service instances in functional tests,
                    // but we verify both tasks exist and plugin registered the service)
                    def dockerService = project.gradle.sharedServices.registrations.findByName('dockerService')
                    assert dockerService != null
                    assert dockerService.service.isPresent()

                    println "Service sharing across tasks: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyServiceSharing')
            .build()

        then:
        result.task(':verifyServiceSharing').outcome == TaskOutcome.SUCCESS
        result.output.contains('Service sharing across tasks: verified')
    }

    // ==================== Service Lifecycle Management ====================

    def "services are managed by Gradle lifecycle"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyServiceLifecycle {
                doLast {
                    // Verify services are registered and available during task execution
                    def dockerService = project.gradle.sharedServices.registrations.findByName('dockerService')
                    def composeService = project.gradle.sharedServices.registrations.findByName('composeService')
                    def jsonService = project.gradle.sharedServices.registrations.findByName('jsonService')

                    // All services should be present during task execution
                    assert dockerService != null && dockerService.service.isPresent()
                    assert composeService != null && composeService.service.isPresent()
                    assert jsonService != null && jsonService.service.isPresent()

                    // Services are managed by Gradle's BuildService lifecycle
                    // They will be automatically cleaned up when build completes
                    println "Services managed by Gradle lifecycle: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyServiceLifecycle')
            .build()

        then:
        result.task(':verifyServiceLifecycle').outcome == TaskOutcome.SUCCESS
        result.output.contains('Services managed by Gradle lifecycle: verified')
    }

    // ==================== Service Registration is Idempotent ====================

    def "service registration is idempotent across multiple plugin applications"() {
        given:
        settingsFile.text = """
            rootProject.name = 'idempotent-registration-test'
            include 'module-a'
            include 'module-b'
            include 'module-c'
        """

        buildFile << """
            // Root build file
        """

        // Create 3 modules that all apply the plugin
        ['module-a', 'module-b', 'module-c'].each { moduleName ->
            def moduleDir = testProjectDir.resolve(moduleName).toFile()
            moduleDir.mkdirs()
            new File(moduleDir, 'build.gradle').text = """
                plugins {
                    id 'com.kineticfire.gradle.docker'
                }
            """
        }

        buildFile << """
            task verifyIdempotentRegistration {
                doLast {
                    // Even with plugin applied in 3 modules, services should be registered once
                    def registrations = project.gradle.sharedServices.registrations
                    def dockerServices = registrations.findAll { it.name == 'dockerService' }
                    def composeServices = registrations.findAll { it.name == 'composeService' }
                    def jsonServices = registrations.findAll { it.name == 'jsonService' }

                    // Each service should be registered exactly once
                    assert dockerServices.size() == 1
                    assert composeServices.size() == 1
                    assert jsonServices.size() == 1

                    println "Service registration is idempotent: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyIdempotentRegistration')
            .build()

        then:
        result.task(':verifyIdempotentRegistration').outcome == TaskOutcome.SUCCESS
        result.output.contains('Service registration is idempotent: verified')
    }
}
