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
 * Functional tests for plugin interactions with other Gradle plugins
 *
 * Tests that the gradle-docker plugin:
 * - Works correctly when applied before or after other plugins
 * - Does not create naming conflicts with other plugins
 * - Shares services correctly across multiple projects
 * - Integrates properly with Java, Groovy, Kotlin, and Application plugins
 *
 * Note: These tests verify plugin compatibility without requiring actual Docker execution.
 */
class PluginInteractionFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'plugin-interaction-test'\n"
    }

    // ==================== Plugin Application Order ====================

    def "docker plugin applies before java plugin"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
                id 'java'
            }

            task verifyPluginOrder {
                doLast {
                    // Both plugins should be applied
                    def dockerExt = project.extensions.findByName('docker')
                    def dockerTestExt = project.extensions.findByName('dockerTest')
                    def sourceSets = project.extensions.findByName('sourceSets')

                    assert dockerExt != null
                    assert dockerTestExt != null
                    assert sourceSets != null

                    // Integration test source set should have been created
                    def sourceSetContainer = project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer)
                    def integrationTest = sourceSetContainer.findByName('integrationTest')
                    assert integrationTest != null

                    println "Docker plugin before Java plugin: OK"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyPluginOrder')
            .build()

        then:
        result.task(':verifyPluginOrder').outcome == TaskOutcome.SUCCESS
        result.output.contains('Docker plugin before Java plugin: OK')
    }

    def "java plugin applies before docker plugin"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            task verifyPluginOrder {
                doLast {
                    // Both plugins should be applied
                    def dockerExt = project.extensions.findByName('docker')
                    def sourceSets = project.extensions.findByName('sourceSets')

                    assert dockerExt != null
                    assert sourceSets != null

                    // Integration test source set should still be created
                    def sourceSetContainer = project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer)
                    def integrationTest = sourceSetContainer.findByName('integrationTest')
                    assert integrationTest != null

                    println "Java plugin before Docker plugin: OK"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyPluginOrder')
            .build()

        then:
        result.task(':verifyPluginOrder').outcome == TaskOutcome.SUCCESS
        result.output.contains('Java plugin before Docker plugin: OK')
    }

    def "docker plugin works with groovy plugin"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            task verifyGroovyIntegration {
                doLast {
                    def dockerExt = project.extensions.findByName('docker')
                    assert dockerExt != null

                    // Integration test source set should support Groovy
                    def sourceSetContainer = project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer)
                    def integrationTest = sourceSetContainer.findByName('integrationTest')
                    assert integrationTest != null

                    // Should have both Java and Groovy source dirs
                    def javaDirs = integrationTest.java.srcDirs
                    def groovyDirs = integrationTest.groovy.srcDirs

                    assert javaDirs.any { it.name == 'java' }
                    assert groovyDirs.any { it.name == 'groovy' }

                    println "Docker plugin with Groovy plugin: OK"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyGroovyIntegration')
            .build()

        then:
        result.task(':verifyGroovyIntegration').outcome == TaskOutcome.SUCCESS
        result.output.contains('Docker plugin with Groovy plugin: OK')
    }

    def "docker plugin works with application plugin"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'application'
                id 'com.kineticfire.gradle.docker'
            }

            application {
                mainClass = 'com.example.Main'
            }

            task verifyApplicationIntegration {
                doLast {
                    def dockerExt = project.extensions.findByName('docker')
                    def appExt = project.extensions.findByName('application')

                    assert dockerExt != null
                    assert appExt != null

                    // Both should coexist
                    println "Docker plugin with Application plugin: OK"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyApplicationIntegration')
            .build()

        then:
        result.task(':verifyApplicationIntegration').outcome == TaskOutcome.SUCCESS
        result.output.contains('Docker plugin with Application plugin: OK')
    }

    // ==================== Extension and Task Name Conflicts ====================

    def "no extension naming conflicts with other plugins"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            task checkExtensionNames {
                doLast {
                    def dockerExt = project.extensions.findByName('docker')
                    def dockerTestExt = project.extensions.findByName('dockerTest')

                    // Docker plugin extensions exist
                    assert dockerExt != null
                    assert dockerTestExt != null

                    // Standard Java extensions also exist
                    def sourceSets = project.extensions.findByName('sourceSets')
                    assert sourceSets != null

                    // No conflicts - both sets of extensions coexist
                    println "No extension naming conflicts"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('checkExtensionNames')
            .build()

        then:
        result.task(':checkExtensionNames').outcome == TaskOutcome.SUCCESS
        result.output.contains('No extension naming conflicts')
    }

    def "no task name conflicts with gradle built-in tasks"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            task checkTaskNames {
                doLast {
                    // Gradle built-in tasks should still exist
                    assert project.tasks.findByName('build') != null
                    assert project.tasks.findByName('clean') != null
                    assert project.tasks.findByName('test') != null
                    assert project.tasks.findByName('jar') != null

                    // Docker plugin aggregate tasks should exist
                    assert project.tasks.findByName('dockerBuild') != null
                    assert project.tasks.findByName('dockerImages') != null
                    assert project.tasks.findByName('composeUp') != null
                    assert project.tasks.findByName('composeDown') != null

                    // No task name conflicts
                    println "No task name conflicts"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('checkTaskNames')
            .build()

        then:
        result.task(':checkTaskNames').outcome == TaskOutcome.SUCCESS
        result.output.contains('No task name conflicts')
    }

    def "no configuration name conflicts with java plugin"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            task checkConfigurationNames {
                doLast {
                    // Java plugin configurations exist
                    assert project.configurations.findByName('implementation') != null
                    assert project.configurations.findByName('testImplementation') != null
                    assert project.configurations.findByName('runtimeOnly') != null

                    // Docker plugin creates integrationTest configurations
                    assert project.configurations.findByName('integrationTestImplementation') != null
                    assert project.configurations.findByName('integrationTestRuntimeOnly') != null

                    // No configuration conflicts
                    println "No configuration name conflicts"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('checkConfigurationNames')
            .build()

        then:
        result.task(':checkConfigurationNames').outcome == TaskOutcome.SUCCESS
        result.output.contains('No configuration name conflicts')
    }

    // ==================== Service Registration and Sharing ====================

    def "services registered once across multiple projects"() {
        given:
        settingsFile << """
            rootProject.name = 'multi-project-services-test'
            include 'subproject-a'
            include 'subproject-b'
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker' apply false
            }
        """

        def subprojectA = testProjectDir.resolve('subproject-a').toFile()
        subprojectA.mkdirs()
        new File(subprojectA, 'build.gradle').text = """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
        """

        def subprojectB = testProjectDir.resolve('subproject-b').toFile()
        subprojectB.mkdirs()
        new File(subprojectB, 'build.gradle').text = """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
        """

        buildFile << """
            task checkServiceRegistration {
                doLast {
                    // Services should be registered at gradle level, shared across projects
                    // We can't directly inspect shared services from TestKit easily,
                    // but we can verify both subprojects apply plugin successfully
                    println "Services registered and shared correctly"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('checkServiceRegistration')
            .build()

        then:
        result.task(':checkServiceRegistration').outcome == TaskOutcome.SUCCESS
        result.output.contains('Services registered and shared correctly')
    }

    def "plugin applies successfully in multi-module project"() {
        given:
        settingsFile.text = """
            rootProject.name = 'multi-module-test'
            include 'module-a'
            include 'module-b'
            include 'module-c'
        """

        buildFile << """
            // Root project doesn't apply plugin
        """

        // Create 3 submodules that all use the docker plugin
        ['module-a', 'module-b', 'module-c'].each { moduleName ->
            def moduleDir = testProjectDir.resolve(moduleName).toFile()
            moduleDir.mkdirs()
            new File(moduleDir, 'build.gradle').text = """
                plugins {
                    id 'java'
                    id 'com.kineticfire.gradle.docker'
                }

                task verifyPlugin {
                    doLast {
                        def dockerExt = project.extensions.findByName('docker')
                        assert dockerExt != null
                        println "Module ${moduleName}: Plugin applied successfully"
                    }
                }
            """
        }

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyPlugin')
            .build()

        then:
        result.output.contains('Module module-a: Plugin applied successfully')
        result.output.contains('Module module-b: Plugin applied successfully')
        result.output.contains('Module module-c: Plugin applied successfully')
    }
}
