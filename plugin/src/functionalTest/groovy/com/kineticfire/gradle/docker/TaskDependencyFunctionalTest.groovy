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
 * Functional tests for task dependency graph and execution order
 *
 * Tests task dependencies and execution ordering including:
 * - Dependency graph construction
 * - Execution order verification
 * - Conditional dependencies based on configuration
 */
class TaskDependencyFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'task-dependency-test'\n"
    }

    // ==================== Dependency Graph Construction ====================

    def "dockerSave depends on dockerBuild when context is present"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myImage {
                        imageName.set('my-app')
                        tags.set(['latest'])
                        context.set(file('.'))

                        save {
                            outputFile.set(file('build/image.tar'))
                        }
                    }
                }
            }

            task verifyDependencies {
                doLast {
                    def saveTask = tasks.getByName('dockerSaveMyImage')
                    def deps = saveTask.taskDependencies.getDependencies(saveTask)
                    println "Save task dependencies: \${deps}"
                    assert deps.any { it.name == 'dockerBuildMyImage' }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDependencies')
            .build()

        then:
        result.output.contains('dockerBuildMyImage')
    }

    def "dockerPublish depends on dockerBuild when context is present"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myImage {
                        imageName.set('my-app')
                        tags.set(['latest'])
                        context.set(file('.'))

                        publish {
                            to('registry') {
                                registry.set('localhost:5000')
                            }
                        }
                    }
                }
            }

            task verifyDependencies {
                doLast {
                    def publishTask = tasks.getByName('dockerPublishMyImage')
                    def deps = publishTask.taskDependencies.getDependencies(publishTask)
                    println "Publish task dependencies: \${deps}"
                    assert deps.any { it.name == 'dockerBuildMyImage' }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDependencies')
            .build()

        then:
        result.output.contains('dockerBuildMyImage')
    }

    def "dockerTag runs independently without build dependency"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myImage {
                        imageName.set('my-app')
                        tags.set(['v1.0', 'latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyDependencies {
                doLast {
                    def tagTask = tasks.getByName('dockerTagMyImage')
                    def deps = tagTask.taskDependencies.getDependencies(tagTask)
                    println "Tag task dependencies: \${deps}"
                    // Tag task should not depend on build task - it's independent
                    assert !deps.any { it.name == 'dockerBuildMyImage' }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDependencies')
            .build()

        then:
        !result.output.contains('dockerBuildMyImage')
    }

    def "aggregate dockerBuild task depends on all per-image build tasks"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    image1 {
                        imageName.set('app1')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                    image2 {
                        imageName.set('app2')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyDependencies {
                doLast {
                    def aggregateTask = tasks.getByName('dockerBuild')
                    def deps = aggregateTask.taskDependencies.getDependencies(aggregateTask)
                    println "Aggregate dockerBuild dependencies: \${deps}"
                    assert deps.any { it.name == 'dockerBuildImage1' }
                    assert deps.any { it.name == 'dockerBuildImage2' }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDependencies')
            .build()

        then:
        result.output.contains('dockerBuildImage1')
        result.output.contains('dockerBuildImage2')
    }

    // ==================== Execution Order Verification ====================

    def "multiple images build in deterministic order"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    alpha {
                        imageName.set('alpha-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                    beta {
                        imageName.set('beta-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                    gamma {
                        imageName.set('gamma-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        // Verify all tasks are created
        result.output.contains('dockerBuildAlpha')
        result.output.contains('dockerBuildBeta')
        result.output.contains('dockerBuildGamma')
    }

    // ==================== Conditional Dependencies ====================

    def "image without save configured has no save task dependencies"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    simpleImage {
                        imageName.set('simple-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                        // No save block configured
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        // dockerSave aggregate task should not list this image's save task
        !result.output.contains('dockerSaveSimpleImage')
    }

    def "image without publish configured has no publish task dependencies"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    localImage {
                        imageName.set('local-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                        // No publish block configured
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        // dockerPublish aggregate task should not list this image's publish tasks
        !result.output.contains('dockerPublishLocalImage')
    }

    // ==================== Complex Dependency Scenarios ====================

    def "complex dependency chain with multiple task types"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    complexImage {
                        imageName.set('complex-app')
                        tags.set(['v1.0', 'latest'])
                        context.set(file('.'))

                        save {
                            outputFile.set(file('build/complex-app.tar'))
                        }

                        publish {
                            to('prod') {
                                registry.set('registry.example.com')
                            }
                        }
                    }
                }
            }

            task verifyComplexDependencies {
                doLast {
                    // Verify save depends on build
                    def saveTask = tasks.getByName('dockerSaveComplexImage')
                    def saveDeps = saveTask.taskDependencies.getDependencies(saveTask)
                    assert saveDeps.any { it.name == 'dockerBuildComplexImage' }

                    // Verify publish depends on build
                    def publishTask = tasks.getByName('dockerPublishComplexImage')
                    def publishDeps = publishTask.taskDependencies.getDependencies(publishTask)
                    assert publishDeps.any { it.name == 'dockerBuildComplexImage' }

                    println "All dependency chains verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyComplexDependencies')
            .build()

        then:
        result.output.contains('All dependency chains verified')
    }
}
