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
 * Functional tests for convention over configuration behavior
 *
 * Tests that the plugin applies sensible defaults and conventions that can be overridden:
 * - IntegrationTest source set auto-creation
 * - Default value conventions
 * - Convention overriding behavior
 *
 * Note: These tests verify that conventions are applied correctly without requiring Docker execution.
 */
class ConventionFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'convention-test'\n"
    }

    // ==================== Integration Test Source Set Auto-Creation ====================

    def "integration test source set created when java plugin applied"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            task verifySourceSet {
                doLast {
                    // Verify source set exists
                    def sourceSets = project.sourceSets
                    assert sourceSets.findByName('integrationTest') != null

                    // Verify Java source directory
                    def integrationTest = sourceSets.getByName('integrationTest')
                    def javaDirs = integrationTest.java.srcDirs
                    println "Java source dirs: \${javaDirs}"
                    assert javaDirs.any { it.name == 'java' }

                    // Verify resources directory
                    def resourceDirs = integrationTest.resources.srcDirs
                    println "Resource dirs: \${resourceDirs}"
                    assert resourceDirs.any { it.name == 'resources' }

                    println "Integration test source set created via convention"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySourceSet')
            .build()

        then:
        result.output.contains('Integration test source set created via convention')
    }

    def "integration test source set NOT created when java plugin not applied"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifySourceSet {
                doLast {
                    // Verify SourceSetContainer doesn't exist
                    def sourceSets = project.extensions.findByType(org.gradle.api.tasks.SourceSetContainer)
                    if (sourceSets == null) {
                        println "SourceSetContainer not found - convention correctly skipped"
                    } else {
                        // If SourceSetContainer exists, verify integrationTest source set was NOT created
                        assert sourceSets.findByName('integrationTest') == null
                        println "Integration test source set not created - convention correctly skipped"
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySourceSet')
            .build()

        then:
        result.output.contains('convention correctly skipped')
    }

    def "integration test source set created when groovy plugin applied"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            task verifySourceSet {
                doLast {
                    // Verify source set exists
                    def sourceSets = project.sourceSets
                    assert sourceSets.findByName('integrationTest') != null

                    // Verify both Java and Groovy source directories
                    def integrationTest = sourceSets.getByName('integrationTest')
                    def javaDirs = integrationTest.java.srcDirs
                    def groovyDirs = integrationTest.groovy.srcDirs

                    println "Java source dirs: \${javaDirs}"
                    println "Groovy source dirs: \${groovyDirs}"

                    assert javaDirs.any { it.name == 'java' }
                    assert groovyDirs.any { it.name == 'groovy' }

                    println "Integration test source set created with Groovy support"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySourceSet')
            .build()

        then:
        result.output.contains('Integration test source set created with Groovy support')
    }

    def "integration test configurations created and extend from test"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            task verifyConfigurations {
                doLast {
                    // Verify configurations exist
                    assert configurations.findByName('integrationTestImplementation') != null
                    assert configurations.findByName('integrationTestRuntimeOnly') != null

                    // Verify they extend from test configurations
                    def integrationTestImpl = configurations.getByName('integrationTestImplementation')
                    def integrationTestRuntime = configurations.getByName('integrationTestRuntimeOnly')

                    assert integrationTestImpl.extendsFrom.any { it.name == 'testImplementation' }
                    assert integrationTestRuntime.extendsFrom.any { it.name == 'testRuntimeOnly' }

                    println "Integration test configurations created and extend test configurations"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfigurations')
            .build()

        then:
        result.output.contains('Integration test configurations created and extend test configurations')
    }

    def "integration test task created automatically"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            task verifyTask {
                doLast {
                    // Verify task exists
                    def integrationTestTask = tasks.findByName('integrationTest')
                    assert integrationTestTask != null

                    // Verify task configuration
                    assert integrationTestTask.group == 'verification'
                    assert integrationTestTask.description == 'Runs integration tests'

                    println "Integration test task created via convention"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTask')
            .build()

        then:
        result.output.contains('Integration test task created via convention')
    }

    def "integration test classpath includes main output"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            task verifyClasspath {
                doLast {
                    def sourceSets = project.sourceSets
                    def integrationTest = sourceSets.getByName('integrationTest')
                    def mainSourceSet = sourceSets.getByName('main')

                    // Verify compile classpath includes main output
                    def compileClasspath = integrationTest.compileClasspath
                    def mainOutputFiles = mainSourceSet.output.files
                    assert compileClasspath.files.containsAll(mainOutputFiles)

                    // Verify runtime classpath includes main output
                    def runtimeClasspath = integrationTest.runtimeClasspath
                    assert runtimeClasspath.files.containsAll(mainOutputFiles)

                    println "Integration test classpath includes main output"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyClasspath')
            .build()

        then:
        result.output.contains('Integration test classpath includes main output')
    }

    def "user can modify source set after plugin application"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            // User modifies the integrationTest source set AFTER plugin creates it
            sourceSets {
                integrationTest {
                    java.srcDir 'src/custom/java'
                }
            }

            task verifyModification {
                doLast {
                    def sourceSets = project.sourceSets
                    def integrationTest = sourceSets.getByName('integrationTest')

                    // Verify both convention and user directories exist
                    def javaDirs = integrationTest.java.srcDirs
                    println "Java source dirs: \${javaDirs}"

                    // Should have both the convention directory and the custom directory
                    assert javaDirs.any { it.path.contains('integrationTest/java') }
                    assert javaDirs.any { it.path.contains('custom/java') }

                    println "User source set modification preserved"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyModification')
            .build()

        then:
        result.output.contains('User source set modification preserved')
    }

    // ==================== Default Values ====================

    def "default dockerfile name is Dockerfile"() {
        given:
        def contextDir = testProjectDir.resolve('context').toFile()
        contextDir.mkdirs()
        def dockerfile = new File(contextDir, 'Dockerfile')
        dockerfile << """
            FROM alpine:latest
            CMD ["/bin/sh"]
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    testImage {
                        imageName.set('test-app')
                        tags.set(['latest'])
                        context.set(file('context'))
                        // No dockerfileName specified - should default to 'Dockerfile'
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildTestImage')
                    def dockerfilePath = buildTask.dockerfile.get().asFile.name
                    println "Dockerfile name: \${dockerfilePath}"
                    assert dockerfilePath == 'Dockerfile'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains('Dockerfile name: Dockerfile')
    }

    def "default wait timeout is 60 seconds"() {
        given:
        def composeFile = testProjectDir.resolve('compose.yml').toFile()
        composeFile << """
            services:
              web:
                image: alpine:latest
                command: sleep 10
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        composeFile.set(file('compose.yml'))
                        waitForHealthy {
                            waitForServices.set(['web'])
                            // No timeout specified - should default to 60 seconds
                        }
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def upTask = tasks.getByName('composeUpTestStack')
                    def timeout = upTask.waitForHealthyTimeoutSeconds.get()
                    println "Wait timeout: \${timeout} seconds"
                    assert timeout == 60
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains('Wait timeout: 60 seconds')
    }

    def "default poll interval is 2 seconds"() {
        given:
        def composeFile = testProjectDir.resolve('compose.yml').toFile()
        composeFile << """
            services:
              web:
                image: alpine:latest
                command: sleep 10
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        composeFile.set(file('compose.yml'))
                        waitForRunning {
                            waitForServices.set(['web'])
                            // No poll interval specified - should default to 2 seconds
                        }
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def upTask = tasks.getByName('composeUpTestStack')
                    def pollInterval = upTask.waitForRunningPollSeconds.get()
                    println "Poll interval: \${pollInterval} seconds"
                    assert pollInterval == 2
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains('Poll interval: 2 seconds')
    }

    def "default compose project name is gradle project name plus stack name"() {
        given:
        def composeFile = testProjectDir.resolve('compose.yml').toFile()
        composeFile << """
            services:
              web:
                image: alpine:latest
                command: sleep 10
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    myStack {
                        composeFile.set(file('compose.yml'))
                        // No projectName specified - should default to gradle project name + stack name
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def upTask = tasks.getByName('composeUpMyStack')
                    def projectName = upTask.projectName.get()
                    println "Project name: \${projectName}"
                    // Convention is: gradle_project_name-stack_name
                    assert projectName == 'convention-test-myStack'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains('Project name: convention-test-myStack')
    }

    // ==================== Convention Overriding ====================

    def "user can override dockerfile name convention"() {
        given:
        def contextDir = testProjectDir.resolve('context').toFile()
        contextDir.mkdirs()
        def dockerfile = new File(contextDir, 'CustomDockerfile')
        dockerfile << """
            FROM alpine:latest
            CMD ["/bin/sh"]
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    testImage {
                        imageName.set('test-app')
                        tags.set(['latest'])
                        context.set(file('context'))
                        dockerfileName.set('CustomDockerfile')  // Override default
                    }
                }
            }

            task verifyOverride {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildTestImage')
                    def dockerfilePath = buildTask.dockerfile.get().asFile.name
                    println "Dockerfile name: \${dockerfilePath}"
                    assert dockerfilePath == 'CustomDockerfile'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyOverride')
            .build()

        then:
        result.output.contains('Dockerfile name: CustomDockerfile')
    }

    def "user can override wait timeout convention"() {
        given:
        def composeFile = testProjectDir.resolve('compose.yml').toFile()
        composeFile << """
            services:
              web:
                image: alpine:latest
                command: sleep 10
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        composeFile.set(file('compose.yml'))
                        waitForHealthy {
                            waitForServices.set(['web'])
                            timeoutSeconds.set(120)  // Override default 60
                        }
                    }
                }
            }

            task verifyOverride {
                doLast {
                    def upTask = tasks.getByName('composeUpTestStack')
                    def timeout = upTask.waitForHealthyTimeoutSeconds.get()
                    println "Wait timeout: \${timeout} seconds"
                    assert timeout == 120
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyOverride')
            .build()

        then:
        result.output.contains('Wait timeout: 120 seconds')
    }

    def "user can override project name convention"() {
        given:
        def composeFile = testProjectDir.resolve('compose.yml').toFile()
        composeFile << """
            services:
              web:
                image: alpine:latest
                command: sleep 10
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    myStack {
                        composeFile.set(file('compose.yml'))
                        projectName.set('custom-project-name')  // Override default
                    }
                }
            }

            task verifyOverride {
                doLast {
                    def upTask = tasks.getByName('composeUpMyStack')
                    def projectName = upTask.projectName.get()
                    println "Project name: \${projectName}"
                    assert projectName == 'custom-project-name'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyOverride')
            .build()

        then:
        result.output.contains('Project name: custom-project-name')
    }

    def "mixed convention and explicit settings work together"() {
        given:
        def composeFile = testProjectDir.resolve('compose.yml').toFile()
        composeFile << """
            services:
              web:
                image: alpine:latest
                command: sleep 10
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    mixedStack {
                        composeFile.set(file('compose.yml'))
                        waitForRunning {
                            waitForServices.set(['web'])
                            timeoutSeconds.set(90)  // Override timeout
                            // pollSeconds NOT specified - use default 2 seconds
                        }
                    }
                }
            }

            task verifyMixed {
                doLast {
                    def upTask = tasks.getByName('composeUpMixedStack')
                    def timeout = upTask.waitForRunningTimeoutSeconds.get()
                    def pollInterval = upTask.waitForRunningPollSeconds.get()

                    println "Wait timeout: \${timeout} seconds (overridden)"
                    println "Poll interval: \${pollInterval} seconds (default)"

                    assert timeout == 90       // User override
                    assert pollInterval == 2   // Default convention
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyMixed')
            .build()

        then:
        result.output.contains('Wait timeout: 90 seconds (overridden)')
        result.output.contains('Poll interval: 2 seconds (default)')
    }
}
