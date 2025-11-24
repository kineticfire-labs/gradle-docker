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
 * Functional tests for validation messages and error handling in the gradle-docker plugin.
 *
 * Tests verify that the plugin provides clear, helpful error messages when:
 * - Required configuration is missing
 * - Configuration values are invalid
 * - Conflicting configurations are detected
 * - File paths don't exist or are malformed
 *
 * These tests use TestKit to verify validation occurs at configuration time and provides
 * actionable error messages to users.
 */
class ValidationMessagesFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'validation-test'\n"
    }

    // ==================== Image Naming Validation ====================

    def "missing image naming shows clear error message"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        context.set(file('.'))
                        tags.set(['latest'])
                        // Missing: imageName, repository, or sourceRef
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('must specify some form of image naming')
        result.output.contains('repository, imageName, or sourceRef')
    }

    def "invalid tag format shows clear error message"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        imageName.set('myapp')
                        tags.set(['invalid:tag:with:colons'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('Invalid tag format')
        result.output.contains('invalid:tag:with:colons')
    }

    def "tag starting with period shows clear error message"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        imageName.set('myapp')
                        tags.set(['.invalid'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('Invalid tag format')
        result.output.contains('cannot start with')
    }

    def "tag starting with hyphen shows clear error message"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        imageName.set('myapp')
                        tags.set(['-invalid'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('Invalid tag format')
        result.output.contains('cannot start with')
    }

    def "tag exceeding 128 characters shows clear error message"() {
        given:
        def longTag = 'a' * 129

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        imageName.set('myapp')
                        tags.set(['${longTag}'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('Invalid tag format')
        result.output.contains('128')
    }

    // ==================== Compose Stack Validation ====================

    def "missing compose files shows clear error message"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    myStack {
                        // Missing: files configuration
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('Compose stack') || result.output.contains('files')
    }

    def "nonexistent compose file shows clear error message"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    myStack {
                        files.from('nonexistent-compose.yml')
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('composeUpMyStack')
            .buildAndFail()

        then:
        result.output.contains('nonexistent-compose.yml') || result.output.contains('does not exist')
    }

    def "invalid stack name in usesCompose shows clear error message"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    validStack {
                        files.from('compose.yml')
                    }
                }
            }

            tasks.register('customTest', Test) {
                usesCompose stack: 'invalidStackName', lifecycle: 'class'
            }
        """

        // Create minimal compose file
        testProjectDir.resolve('compose.yml').toFile() << "services:\n  test:\n    image: alpine\n"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('invalidStackName') && (result.output.contains('not found') || result.output.contains('does not exist'))
    }

    def "invalid lifecycle value shows clear error message"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    myStack {
                        files.from('compose.yml')
                    }
                }
            }

            tasks.register('lifecycleTest', Test) {
                usesCompose stack: 'myStack', lifecycle: 'invalidLifecycle'
            }
        """

        // Create minimal compose file
        testProjectDir.resolve('compose.yml').toFile() << "services:\n  test:\n    image: alpine\n"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('invalidLifecycle') || result.output.contains('lifecycle')
    }

    // ==================== Context Configuration Validation ====================

    def "missing context configuration shows clear error message"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        imageName.set('myapp')
                        tags.set(['latest'])
                        // Missing: context, contextTask, or dockerfile
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('dockerBuildMyapp')
            .buildAndFail()

        then:
        result.output.contains('context') || result.output.contains('Dockerfile')
    }

    // ==================== Registry and Repository Validation ====================

    def "empty registry string is accepted"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        registry.set('')
                        imageName.set('myapp')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildMyapp')
                    println "Registry: '\${buildTask.registry.get()}'"
                    assert buildTask.registry.get() == ''
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Registry: ''")
    }

    def "empty namespace string is accepted"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        namespace.set('')
                        imageName.set('myapp')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildMyapp')
                    println "Namespace: '\${buildTask.namespace.get()}'"
                    assert buildTask.namespace.get() == ''
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Namespace: ''")
    }

}
