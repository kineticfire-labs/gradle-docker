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
 * Functional tests for task naming conventions.
 *
 * Tests verify that the plugin generates proper task names from image names with various
 * naming patterns (camelCase, hyphenated, underscored, etc.) and ensures no conflicts
 * with Gradle built-in tasks.
 */
class TaskNamingFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'task-naming-test'\n"
    }

    // ==================== CamelCase Image Names ====================

    def "camelCase image name generates correct task names"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myAppImage {
                        imageName.set('my-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyTaskNames {
                doLast {
                    // Verify task names are capitalized correctly
                    assert tasks.findByName('dockerBuildMyAppImage') != null
                    assert tasks.findByName('dockerTagMyAppImage') != null
                    assert tasks.findByName('dockerImageMyAppImage') != null  // Aggregate task for this image

                    println "CamelCase task names verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTaskNames')
            .build()

        then:
        result.task(':verifyTaskNames').outcome == TaskOutcome.SUCCESS
        result.output.contains('CamelCase task names verified')
    }

    def "PascalCase image name generates correct task names"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    WebApplication {
                        imageName.set('web-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyTaskNames {
                doLast {
                    // PascalCase should preserve capitalization
                    assert tasks.findByName('dockerBuildWebApplication') != null
                    assert tasks.findByName('dockerTagWebApplication') != null
                    assert tasks.findByName('dockerImageWebApplication') != null

                    println "PascalCase task names verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTaskNames')
            .build()

        then:
        result.task(':verifyTaskNames').outcome == TaskOutcome.SUCCESS
        result.output.contains('PascalCase task names verified')
    }

    // ==================== Hyphenated Image Names ====================

    def "hyphenated image name generates correct task names"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    'my-web-app' {
                        imageName.set('my-web-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyTaskNames {
                doLast {
                    // Hyphens in image spec name are preserved
                    assert tasks.findByName('dockerBuildMy-web-app') != null
                    assert tasks.findByName('dockerTagMy-web-app') != null
                    assert tasks.findByName('dockerImageMy-web-app') != null

                    println "Hyphenated task names verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTaskNames')
            .build()

        then:
        result.task(':verifyTaskNames').outcome == TaskOutcome.SUCCESS
        result.output.contains('Hyphenated task names verified')
    }

    // ==================== Underscored Image Names ====================

    def "underscored image name generates correct task names"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    my_service_app {
                        imageName.set('my-service')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyTaskNames {
                doLast {
                    // Underscores are preserved
                    assert tasks.findByName('dockerBuildMy_service_app') != null
                    assert tasks.findByName('dockerTagMy_service_app') != null
                    assert tasks.findByName('dockerImageMy_service_app') != null

                    println "Underscored task names verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTaskNames')
            .build()

        then:
        result.task(':verifyTaskNames').outcome == TaskOutcome.SUCCESS
        result.output.contains('Underscored task names verified')
    }

    // ==================== Numeric Image Names ====================

    def "image name with numbers generates correct task names"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    app2024v2 {
                        imageName.set('app-2024')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyTaskNames {
                doLast {
                    // Numbers should be preserved
                    assert tasks.findByName('dockerBuildApp2024v2') != null
                    assert tasks.findByName('dockerTagApp2024v2') != null
                    assert tasks.findByName('dockerImageApp2024v2') != null

                    println "Numeric task names verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTaskNames')
            .build()

        then:
        result.task(':verifyTaskNames').outcome == TaskOutcome.SUCCESS
        result.output.contains('Numeric task names verified')
    }

    // ==================== No Conflicts with Gradle Built-ins ====================

    def "task names do not conflict with Gradle built-in tasks"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        imageName.set('myapp')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyNoConflicts {
                doLast {
                    // Verify Gradle built-in tasks still exist
                    assert tasks.findByName('build') != null
                    assert tasks.findByName('clean') != null
                    assert tasks.findByName('test') != null
                    assert tasks.findByName('jar') != null
                    assert tasks.findByName('assemble') != null

                    // Verify docker tasks exist with no conflicts
                    assert tasks.findByName('dockerBuildMyapp') != null
                    assert tasks.findByName('dockerTagMyapp') != null
                    assert tasks.findByName('dockerImageMyapp') != null

                    // Verify aggregate tasks exist
                    assert tasks.findByName('dockerBuild') != null
                    assert tasks.findByName('dockerImages') != null

                    println "No task name conflicts verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyNoConflicts')
            .build()

        then:
        result.task(':verifyNoConflicts').outcome == TaskOutcome.SUCCESS
        result.output.contains('No task name conflicts verified')
    }

    // ==================== Task Name Capitalization ====================

    def "task names have proper capitalization"() {
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
                        context.set(file('.'))
                    }
                }
            }

            task verifyCapitalization {
                doLast {
                    // Verify first letter of image name is capitalized in task name
                    def buildTask = tasks.getByName('dockerBuildMyapp')
                    assert buildTask.name == 'dockerBuildMyapp'

                    def tagTask = tasks.getByName('dockerTagMyapp')
                    assert tagTask.name == 'dockerTagMyapp'

                    println "Task name capitalization verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyCapitalization')
            .build()

        then:
        result.task(':verifyCapitalization').outcome == TaskOutcome.SUCCESS
        result.output.contains('Task name capitalization verified')
    }

    // ==================== Multiple Images with Different Naming ====================

    def "multiple images with different naming conventions coexist"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    // Different naming conventions
                    webApp {
                        imageName.set('web-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }

                    'api-service' {
                        imageName.set('api-service')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }

                    worker_process {
                        imageName.set('worker')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyAllTaskNames {
                doLast {
                    // Verify all task naming conventions work together
                    assert tasks.findByName('dockerBuildWebApp') != null
                    assert tasks.findByName('dockerBuildApi-service') != null
                    assert tasks.findByName('dockerBuildWorker_process') != null

                    assert tasks.findByName('dockerTagWebApp') != null
                    assert tasks.findByName('dockerTagApi-service') != null
                    assert tasks.findByName('dockerTagWorker_process') != null

                    println "Multiple naming conventions verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyAllTaskNames')
            .build()

        then:
        result.task(':verifyAllTaskNames').outcome == TaskOutcome.SUCCESS
        result.output.contains('Multiple naming conventions verified')
    }
}
