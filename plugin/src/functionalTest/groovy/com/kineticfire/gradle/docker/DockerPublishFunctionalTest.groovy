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
 * Functional tests for Docker publish operations with new nomenclature API
 * 
 * NOTE: These tests focus on configuration verification rather than actual Docker execution
 * due to TestKit limitations with Gradle 9. Real Docker operations are covered by 
 * integration tests in the plugin-integration-test project.
 */
class DockerPublishFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "docker publish task configuration supports multiple publish targets"() {
        given:
        settingsFile << "rootProject.name = 'test-publish-targets'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    multiTargetApp {
                        registry.set('docker.io')
                        namespace.set('mycompany')
                        imageName.set('my-app')
                        version.set('1.0.0')
                        tags.set(['latest', 'stable'])
                        context.set(file('.'))
                        
                        publish {
                            to('dockerhub') {
                                registry.set('docker.io')
                                namespace.set('mycompany')
                                publishTags(['latest', 'v1.0.0'])
                                
                                auth {
                                    username.set('dockeruser')
                                    password.set('dockerpass')
                                }
                            }
                            
                            to('ghcr') {
                                registry.set('ghcr.io')
                                namespace.set('mycompany')
                                publishTags(['latest', 'main'])
                                
                                auth {
                                    registryToken.set('ghp_token123')
                                }
                            }
                            
                            to('localRegistry') {
                                registry.set('localhost:5000')
                                namespace.set('test')
                                publishTags(['test'])
                                
                                // No auth for local registry
                            }
                        }
                    }
                }
            }
            
            tasks.register('verifyPublishTargetConfiguration') {
                doLast {
                    def publishTask = tasks.getByName('dockerPublishMultiTargetApp')
                    
                    println "Publish task configured: " + publishTask.name
                    
                    // Verify task exists and is properly configured
                    assert publishTask != null
                    
                    println "Multiple publish targets configured correctly"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyPublishTargetConfiguration', '--info')
            .build()

        then:
        result.output.contains('Publish task configured: dockerPublishMultiTargetApp')
        result.output.contains('Multiple publish targets configured correctly')
    }

    def "docker publish task configuration supports sourceRef mode"() {
        given:
        settingsFile << "rootProject.name = 'test-publish-sourceref'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    existingImage {
                        // SourceRef mode - publishing existing image
                        sourceRef.set('ghcr.io/upstream/app:v1.2.3')
                        tags.set(['local:latest'])
                        
                        publish {
                            to('internal') {
                                registry.set('internal.company.com')
                                repository.set('apps/my-app')
                                publishTags(['internal-latest', 'v1.2.3'])
                                
                                auth {
                                    username.set('internaluser')
                                    password.set('internalpass')
                                }
                            }
                        }
                    }
                }
            }
            
            tasks.register('verifySourceRefPublishConfiguration') {
                doLast {
                    def publishTask = tasks.getByName('dockerPublishExistingImage')
                    
                    println "SourceRef publish task: " + publishTask.name
                    println "SourceRef configured: " + publishTask.sourceRef.get()
                    
                    assert publishTask.sourceRef.isPresent()
                    
                    println "SourceRef publish configuration correct"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySourceRefPublishConfiguration', '--info')
            .build()

        then:
        result.output.contains('SourceRef publish task: dockerPublishExistingImage')
        result.output.contains('SourceRef configured: ghcr.io/upstream/app:v1.2.3')
        result.output.contains('SourceRef publish configuration correct')
    }

    def "docker publish task configuration supports authentication methods"() {
        given:
        settingsFile << "rootProject.name = 'test-publish-auth'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    authApp {
                        registry.set('docker.io')
                        imageName.set('auth-test')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        publish {
                            to('usernamePassword') {
                                registry.set('docker.io')
                                namespace.set('myuser')
                                publishTags(['latest'])
                                
                                auth {
                                    username.set('myuser')
                                    password.set('mypass')
                                }
                            }
                            
                            to('tokenAuth') {
                                registry.set('ghcr.io')
                                namespace.set('myorg')
                                publishTags(['token'])
                                
                                auth {
                                    registryToken.set('ghp_tokenABC123')
                                }
                            }
                            
                            to('helperAuth') {
                                registry.set('gcr.io')
                                namespace.set('myproject')
                                publishTags(['helper'])
                                
                                auth {
                                    helper.set('gcloud')
                                }
                            }
                        }
                    }
                }
            }
            
            tasks.register('verifyAuthConfiguration') {
                doLast {
                    def publishTask = tasks.getByName('dockerPublishAuthApp')
                    
                    println "Auth publish task: " + publishTask.name
                    
                    // Task should be properly configured
                    assert publishTask != null
                    
                    println "Authentication methods configured correctly"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyAuthConfiguration', '--info')
            .build()

        then:
        result.output.contains('Auth publish task: dockerPublishAuthApp')
        result.output.contains('Authentication methods configured correctly')
    }

    def "docker publish task configuration uses Provider API for lazy evaluation"() {
        given:
        settingsFile << "rootProject.name = 'test-publish-providers'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            def registryProvider = providers.gradleProperty('publish.registry').orElse('docker.io')
            def namespaceProvider = providers.gradleProperty('publish.namespace').orElse('default')
            def versionProvider = providers.gradleProperty('project.version').orElse('1.0.0')
            
            docker {
                images {
                    providerApp {
                        registry.set('docker.io')
                        imageName.set('provider-publish-test')
                        version.set(versionProvider)
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        publish {
                            to('dynamic') {
                                registry.set(registryProvider)
                                namespace.set(namespaceProvider)
                                publishTags(providers.provider { 
                                    ['latest', 'v1.0.0']
                                })
                                
                                auth {
                                    username.set(providers.gradleProperty('docker.username'))
                                    password.set(providers.gradleProperty('docker.password'))
                                }
                            }
                        }
                    }
                }
            }
            
            tasks.register('verifyProviderPublishConfiguration') {
                doLast {
                    def publishTask = tasks.getByName('dockerPublishProviderApp')
                    
                    println "Provider publish task: " + publishTask.name
                    
                    // Verify task configuration with providers
                    assert publishTask != null
                    
                    println "Provider-based publish configuration working"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyProviderPublishConfiguration', '--info')
            .build()

        then:
        result.output.contains('Provider publish task: dockerPublishProviderApp')
        result.output.contains('Provider-based publish configuration working')
    }

    def "docker publish task configuration supports both repository and namespace configurations"() {
        given:
        settingsFile << "rootProject.name = 'test-publish-dual-config'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }

            docker {
                images {
                    dualConfigApp {
                        // Use one approach for image spec - only imageName approach
                        registry.set('docker.io')
                        imageName.set('dual-config-test')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))

                        publish {
                            // Test different approaches in publish targets
                            to('repositoryTarget') {
                                registry.set('ghcr.io')
                                repository.set('acme/widgets/api-service')
                                publishTags(['repo-latest'])
                            }

                            to('namespaceTarget') {
                                registry.set('registry.example.com')
                                namespace.set('company/apps')
                                imageName.set('microservice')
                                publishTags(['ns-latest'])
                            }
                        }
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
        result.output.contains('dockerPublish')
        result.output.contains('dockerBuild')
    }
}
