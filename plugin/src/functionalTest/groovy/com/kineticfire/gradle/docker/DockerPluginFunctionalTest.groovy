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
 * Functional tests for the Docker Plugin using Gradle TestKit
 */
class DockerPluginFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "can apply plugin"() {
        given:
        settingsFile << "rootProject.name = 'test-project'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('dockerBuild')
        result.output.contains('dockerTag')
        result.output.contains('dockerPush')
        result.output.contains('dockerSave')
        result.output.contains('composeUp')
        result.output.contains('composeDown')
    }

    def "can configure docker extension"() {
        given:
        settingsFile << "rootProject.name = 'test-project'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                registry = 'docker.example.com'
                
                images {
                    app {
                        dockerfile = 'Dockerfile'
                        contextPath = '.'
                        tags = ['app:latest', 'app:1.0.0']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('dockerBuild')
        result.output.contains('app')
    }

    def "can configure compose extension"() {
        given:
        settingsFile << "rootProject.name = 'test-project'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            compose {
                stacks {
                    integration {
                        composeFile = 'docker-compose.yml'
                        projectName = 'test-integration'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('composeUp')
        result.output.contains('composeDown')
        result.output.contains('integration')
    }

    def "creates docker tasks for configured images"() {
        given:
        settingsFile << "rootProject.name = 'test-project'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    myapp {
                        dockerfile = 'Dockerfile'
                        contextPath = '.'
                        tags = ['myapp:latest']
                    }
                    worker {
                        dockerfile = 'worker/Dockerfile'
                        contextPath = 'worker'
                        tags = ['worker:latest']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('dockerBuildMyapp')
        result.output.contains('dockerTagMyapp')
        result.output.contains('dockerPushMyapp')
        result.output.contains('dockerSaveMyapp')
        result.output.contains('dockerBuildWorker')
        result.output.contains('dockerTagWorker')
        result.output.contains('dockerPushWorker')
        result.output.contains('dockerSaveWorker')
    }

    def "creates compose tasks for configured stacks"() {
        given:
        settingsFile << "rootProject.name = 'test-project'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            compose {
                stacks {
                    dev {
                        composeFile = 'docker-compose.dev.yml'
                        projectName = 'myproject-dev'
                    }
                    test {
                        composeFile = 'docker-compose.test.yml'
                        projectName = 'myproject-test'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('composeUpDev')
        result.output.contains('composeDownDev')
        result.output.contains('composeUpTest')
        result.output.contains('composeDownTest')
    }

    def "can configure authentication"() {
        given:
        settingsFile << "rootProject.name = 'test-project'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                registry = 'private.registry.com'
                authentication {
                    username = 'testuser'
                    password = 'testpass'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('dockerPush')
    }

    def "can configure build arguments"() {
        given:
        settingsFile << "rootProject.name = 'test-project'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    app {
                        dockerfile = 'Dockerfile'
                        contextPath = '.'
                        tags = ['app:latest']
                        buildArgs = [
                            'BUILD_VERSION': '1.0.0',
                            'BUILD_ENV': 'production'
                        ]
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('dockerBuildApp')
    }
}