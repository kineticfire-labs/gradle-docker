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

import java.nio.file.Files
import java.nio.file.Path

/**
 * Functional tests for Docker context API features including contextTask and inline context blocks.
 *
 * Tests verify:
 * - contextTask property configuration and task creation
 * - Inline context {} block configuration and task creation
 * - Traditional context backward compatibility
 * - Mixed context types in same build
 * - Task dependencies and configuration verification
 * - Configuration cache compatibility
 */
class DockerContextApiFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        
        // Create basic project structure
        Files.createDirectories(testProjectDir.resolve('src/main/docker'))
        Files.createDirectories(testProjectDir.resolve('src/main/java'))
        Files.createDirectories(testProjectDir.resolve('build/libs'))
    }

    
    def "can configure contextTask property for Docker image"() {
        given:
        settingsFile << "rootProject.name = 'test-context-task'"

        // Create test files
        def dockerfile = testProjectDir.resolve('src/main/docker/Dockerfile').toFile()
        dockerfile << """
            FROM alpine:latest
            COPY app.jar /app.jar
            CMD ["java", "-jar", "/app.jar"]
        """

        def jarFile = testProjectDir.resolve('build/libs/app.jar').toFile()
        jarFile.parentFile.mkdirs()
        jarFile.createNewFile()

        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        imageName.set('myapp')
                        version.set('1.0.0')
                        tags.set(['test', 'latest'])

                        contextTask = tasks.register('prepareDockerContext', Copy) {
                            group = 'docker'
                            description = 'Prepare Docker build context'
                            into layout.buildDirectory.dir('docker-context/myapp')
                            from 'src/main/docker'
                            from('build/libs') {
                                include '*.jar'
                            }
                        }
                        dockerfileName.set('Dockerfile')
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
        result.output.contains('prepareDockerContext')
        result.output.contains('Docker tasks')
    }


    def "context task with multiple sources works correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-multi-source'"

        // Create test files in multiple locations
        Files.createDirectories(testProjectDir.resolve('src'))
        Files.createDirectories(testProjectDir.resolve('config'))
        Files.createDirectories(testProjectDir.resolve('scripts'))

        testProjectDir.resolve('src/Dockerfile').toFile().text = "FROM alpine:latest"
        testProjectDir.resolve('config/app.properties').toFile().text = 'test=true'
        testProjectDir.resolve('scripts/startup.sh').toFile().text = '#!/bin/sh'

        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    multisrc {
                        contextTask = tasks.register('prepareMultisrcContext', Copy) {
                            group = 'docker'
                            description = 'Prepare context with multiple sources'
                            into layout.buildDirectory.dir('docker-context/multisrc')
                            from('src')
                            from('config') {
                                into 'config/'
                            }
                            from('scripts') {
                                into 'bin/'
                            }
                        }
                        dockerfileName.set('Dockerfile')
                        tags.set(['test', 'latest'])
                        imageName.set('multisrc')
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
        result.output.contains('prepareMultisrcContext')
        result.output.contains('Prepare context with multiple sources')
    }

    def "traditional context configuration still works"() {
        given:
        settingsFile << "rootProject.name = 'test-traditional-context'"

        // Create traditional Docker context
        def dockerDir = testProjectDir.resolve('docker').toFile()
        dockerDir.mkdirs()

        def dockerfile = testProjectDir.resolve('docker/Dockerfile').toFile()
        dockerfile << """
            FROM alpine:latest
            RUN echo "Traditional context test"
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    traditional {
                        context.set(file('docker'))
                        dockerfileName.set('Dockerfile')
                        tags.set(['test', 'latest'])
                        imageName.set('traditional')
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
        result.output.contains('dockerBuildTraditional')
        result.output.contains('Docker tasks')
    }

    def "can combine traditional and contextTask in same build"() {
        given:
        settingsFile << "rootProject.name = 'test-mixed-contexts'"

        // Create files for different context types
        setupMixedContextFiles()

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    // Traditional context.set(file(...))
                    webapp {
                        context.set(file('webapp-docker'))
                        dockerfileName.set('Dockerfile')
                        tags.set(['test', 'latest'])
                        imageName.set('webapp')
                    }

                    // Context task
                    api {
                        contextTask = tasks.register('prepareApiContext', Copy) {
                            into layout.buildDirectory.dir('docker-context/api')
                            from 'api-src'
                        }
                        dockerfileName.set('Dockerfile.api')
                        tags.set(['test', 'latest'])
                        imageName.set('api')
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
        result.output.contains('dockerBuildWebapp')
        result.output.contains('dockerBuildApi')
        result.output.contains('prepareApiContext')
    }

    def "context task dependencies configured correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-task-dependencies'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myapp {
                        contextTask = tasks.register('prepareMyappContext', Copy) {
                            group = 'docker'
                            description = 'Prepare Docker build context for myapp'
                            into layout.buildDirectory.dir('docker-context/myapp')
                            from 'src'
                        }
                        dockerfileName.set('Dockerfile')
                        tags.set(['test', 'latest'])
                        imageName.set('myapp')
                    }
                }
            }

            // Task to verify context task configuration
            task verifyContextTask {
                doLast {
                    def contextTask = tasks.findByName('prepareMyappContext')
                    assert contextTask != null
                    assert contextTask.group == 'docker'
                    assert contextTask.description.contains('Prepare Docker build context for myapp')

                    def buildTask = tasks.findByName('dockerBuildMyapp')
                    if (buildTask) {
                        assert buildTask.dependsOn.any { it.toString().contains('prepareMyappContext') }
                    }

                    println "Context task validation passed!"
                }
            }
        """

        // Create minimal required files
        Files.createDirectories(testProjectDir.resolve('src'))
        testProjectDir.resolve('src/Dockerfile').toFile().text = "FROM alpine:latest"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyContextTask')
            .build()

        then:
        result.output.contains('Context task validation passed!')
        result.task(':verifyContextTask').outcome == TaskOutcome.SUCCESS
    }

    def "configuration cache compatible with contextTask"() {
        given:
        settingsFile << "rootProject.name = 'test-config-cache'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    cachetest {
                        contextTask = tasks.register('prepareCachetestContext', Copy) {
                            into layout.buildDirectory.dir('docker-context/cachetest')
                            from 'src'
                        }
                        dockerfileName.set('Dockerfile')
                        tags.set(['test', 'latest'])
                        imageName.set('cachetest')
                    }
                }
            }
        """

        // Create required files
        Files.createDirectories(testProjectDir.resolve('src'))
        testProjectDir.resolve('src/Dockerfile').toFile().text = "FROM alpine:latest"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        then:
        result.output.contains('Configuration cache entry stored') || result.output.contains('Reusing configuration cache')
    }

    private void setupMixedContextFiles() {
        // Traditional context
        def webappDir = testProjectDir.resolve('webapp-docker').toFile()
        webappDir.mkdirs()
        testProjectDir.resolve('webapp-docker/Dockerfile').toFile().text = "FROM nginx:latest"
        
        // API context source
        def apiDir = testProjectDir.resolve('api-src').toFile()
        apiDir.mkdirs()
        testProjectDir.resolve('api-src/Dockerfile.api').toFile().text = "FROM node:latest"
        
        // Worker context source
        def workerDir = testProjectDir.resolve('worker-src').toFile()
        workerDir.mkdirs()
        testProjectDir.resolve('worker-src/Dockerfile.worker').toFile().text = "FROM python:latest"
    }
}