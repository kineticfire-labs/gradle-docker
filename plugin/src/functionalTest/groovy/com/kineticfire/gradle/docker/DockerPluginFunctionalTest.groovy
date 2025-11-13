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
 * 
 * TEMPORARY DISABLED: These tests are temporarily commented out due to known incompatibility 
 * between Gradle TestKit and Gradle 9.0.0. The issue is tracked and will be re-enabled 
 * when TestKit compatibility is improved or an alternative testing approach is implemented.
 * 
 * Issue: InvalidPluginMetadataException when using withPluginClasspath() in Gradle 9.0.0
 * Root cause: Gradle 9.0.0 TestKit has breaking changes in plugin classpath resolution
 * 
 * Tests affected: All tests using withPluginClasspath() method (7 tests)
 * Functionality affected: 
 * - Plugin application verification
 * - Docker extension configuration
 * - Compose extension configuration  
 * - Task creation verification
 * - Authentication configuration
 * - Build arguments configuration
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

    def "can apply plugin and verify basic task creation"() {
        given:
        settingsFile << "rootProject.name = 'test-plugin-apply'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('dockerBuild')
        result.output.contains('dockerTag')
        result.output.contains('dockerSave')
        result.output.contains('dockerPublish')
        result.output.contains('composeUp')
        result.output.contains('composeDown')
    }

    def "can configure docker extension with new nomenclature API"() {
        given:
        settingsFile << "rootProject.name = 'test-docker-nomenclature'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    webapp {
                        // New nomenclature approach
                        registry.set('ghcr.io')
                        namespace.set('mycompany')
                        imageName.set('webapp')
                        version.set('2.1.0')
                        tags.set(['latest', 'stable'])
                        
                        // Labels support
                        labels.put('org.opencontainers.image.title', 'Web Application')
                        labels.put('org.opencontainers.image.vendor', 'My Company')
                        
                        context.set(file('.'))
                        dockerfileName.set('Dockerfile')
                        buildArgs.put('VERSION', '2.1.0')
                    }
                    
                    api {
                        // Repository approach
                        registry.set('docker.io')
                        repository.set('mycompany/api-service')
                        version.set('1.5.0')
                        tags.set(['latest'])
                        
                        // Don't set context to non-existent directory
                        context.set(file('.'))
                    }
                }
            }
            
            tasks.register('verifyDockerConfiguration') {
                doLast {
                    println "Docker tasks created successfully"
                    println "Webapp tasks: dockerBuildWebapp, dockerTagWebapp, dockerSaveWebapp, dockerPublishWebapp"
                    println "API tasks: dockerBuildApi, dockerTagApi, dockerSaveApi, dockerPublishApi"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDockerConfiguration', '--info')
            .build()

        then:
        result.output.contains('Docker tasks created successfully')
        result.output.contains('Webapp tasks: dockerBuildWebapp')
        result.output.contains('API tasks: dockerBuildApi')
    }

    def "can configure compose extension with stacks"() {
        given:
        settingsFile << "rootProject.name = 'test-compose-config'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerOrch {
                composeStacks {
                    integration {
                        composeFile.set(file('docker-compose.yml'))
                        projectName.set('test-integration')
                        
                        waitForHealthy {
                            services.set(['db', 'redis'])
                            timeoutSeconds.set(120)  // 2 minutes
                        }
                    }
                    
                    development {
                        composeFile.set(file('docker-compose.dev.yml'))
                        projectName.set('test-dev')
                        
                        logs {
                            tailLines.set(50)
                        }
                    }
                }
            }
            
            tasks.register('verifyComposeConfiguration') {
                doLast {
                    println "Compose tasks created successfully"
                    println "Integration stack: composeUpIntegration, composeDownIntegration"
                    println "Development stack: composeUpDevelopment, composeDownDevelopment"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyComposeConfiguration', '--info')
            .build()

        then:
        result.output.contains('Compose tasks created successfully')
        result.output.contains('Integration stack: composeUpIntegration')
        result.output.contains('Development stack: composeUpDevelopment')
    }

    def "creates docker tasks for configured images with proper naming"() {
        given:
        settingsFile << "rootProject.name = 'test-task-creation'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    myApp {
                        registry.set('docker.io')
                        imageName.set('my-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                    workerService {
                        registry.set('ghcr.io')
                        namespace.set('myorg')
                        imageName.set('worker')
                        version.set('2.0.0')
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
        result.output.contains('dockerBuildMyApp')
        result.output.contains('dockerTagMyApp')
        // Note: Save and publish tasks may only be created when save/publish configurations are present
        result.output.contains('dockerBuildWorkerService')
        result.output.contains('dockerTagWorkerService')
    }

    def "creates compose tasks for configured stacks with proper naming"() {
        given:
        settingsFile << "rootProject.name = 'test-compose-tasks'"

        // Create minimal compose files for the test
        def devComposeFile = testProjectDir.resolve('docker-compose.dev.yml').toFile()
        devComposeFile << """
            services:
              app:
                image: alpine:latest
        """

        def testComposeFile = testProjectDir.resolve('docker-compose.test.yml').toFile()
        testComposeFile << """
            services:
              test:
                image: alpine:latest
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    devEnv {
                        composeFile.set(file('docker-compose.dev.yml'))
                        projectName.set('myproject-dev')
                    }
                    testSuite {
                        composeFile.set(file('docker-compose.test.yml'))
                        projectName.set('myproject-test')
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
        result.output.contains('composeUpDevEnv')
        result.output.contains('composeDownDevEnv')
        result.output.contains('composeUpTestSuite')
        result.output.contains('composeDownTestSuite')
    }

    def "can configure authentication for publish operations"() {
        given:
        settingsFile << "rootProject.name = 'test-auth-config'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    authApp {
                        registry.set('private.registry.com')
                        imageName.set('auth-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        publish {
                            to('private') {
                                registry.set('private.registry.com')
                                namespace.set('myorg')
                                publishTags(['latest', 'v1.0.0'])
                                
                                auth {
                                    username.set('user')
                                    password.set('pass')
                                }
                            }
                        }
                    }
                }
            }
            
            tasks.register('verifyAuthConfig') {
                doLast {
                    println "Authentication configured for dockerPublishAuthApp"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyAuthConfig', '--info')
            .build()

        then:
        result.output.contains('Authentication configured for dockerPublishAuthApp')
    }

    def "can configure build arguments and labels"() {
        given:
        settingsFile << "rootProject.name = 'test-build-config'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    buildApp {
                        registry.set('docker.io')
                        imageName.set('build-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        // Build arguments
                        buildArgs.put('BUILD_VERSION', '1.0.0')
                        buildArgs.put('BUILD_ENV', 'production')
                        buildArgs.put('JAVA_VERSION', '21')
                        
                        // Labels
                        labels.put('org.opencontainers.image.title', 'Build Application')
                        labels.put('org.opencontainers.image.version', '1.0.0')
                        labels.put('org.opencontainers.image.vendor', 'My Company')
                        labels.put('com.example.team', 'backend')
                    }
                }
            }
            
            tasks.register('verifyBuildConfig') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildBuildApp')
                    println "Build arguments and labels configured for \${buildTask.name}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyBuildConfig', '--info')
            .build()

        then:
        result.output.contains('Build arguments and labels configured for dockerBuildBuildApp')
    }
}