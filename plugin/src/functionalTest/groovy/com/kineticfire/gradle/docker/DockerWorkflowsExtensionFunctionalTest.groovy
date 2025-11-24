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
 * Functional tests for DockerWorkflowsExtension visibility and DSL parsing
 */
class DockerWorkflowsExtensionFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-docker-workflows'"
    }

    def "dockerWorkflows block can be declared"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
                pipelines {
                    testPipeline {
                        description.set('Test pipeline')
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks', '--stacktrace')
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    def "empty dockerWorkflows block is valid"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks')
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    def "multiple pipelines can be configured"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
                pipelines {
                    devPipeline {
                        description.set('Development pipeline')
                    }
                    stagingPipeline {
                        description.set('Staging pipeline')
                    }
                    productionPipeline {
                        description.set('Production pipeline')
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks')
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    def "pipeline descriptions can be configured"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
                pipelines {
                    myPipeline {
                        description.set('Complete CI/CD pipeline: build → test → publish')
                    }
                }
            }

            task verifyPipeline {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('myPipeline')
                    assert pipeline.name == 'myPipeline'
                    assert pipeline.description.get() == 'Complete CI/CD pipeline: build → test → publish'
                    println "Pipeline verification successful!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyPipeline')
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .build()

        then:
        result.output.contains('Pipeline verification successful!')
        result.output.contains('BUILD SUCCESSFUL')
    }
}
