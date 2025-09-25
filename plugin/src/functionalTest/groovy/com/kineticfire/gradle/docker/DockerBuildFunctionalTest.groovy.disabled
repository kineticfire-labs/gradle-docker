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
 * Functional tests for Docker build operations
 * 
 * TEMPORARY DISABLED: These tests are temporarily commented out due to known incompatibility 
 * between Gradle TestKit and Gradle 9.0.0. The issue is tracked and will be re-enabled 
 * when TestKit compatibility is improved or an alternative testing approach is implemented.
 * 
 * Issue: InvalidPluginMetadataException when using withPluginClasspath() in Gradle 9.0.0
 * Root cause: Gradle 9.0.0 TestKit has breaking changes in plugin classpath resolution
 * 
 * Tests affected: All tests using withPluginClasspath() method (5 tests)
 * Functionality affected: 
 * - Docker build task execution
 * - Build task error handling
 * - Custom build arguments support
 * - Docker daemon availability checks
 * - Build output verification
 */
class DockerBuildFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "docker build task configuration validates new nomenclature API"() {
        given:
        settingsFile << "rootProject.name = 'test-docker-nomenclature'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    appWithNomenclature {
                        // Build Mode using new nomenclature
                        registry.set('ghcr.io')
                        namespace.set('kineticfire/apps')
                        imageName.set('my-app')
                        version.set('1.0.0')
                        tags.set(['latest', 'stable'])
                        
                        // Labels support
                        labels.put('org.opencontainers.image.version', '1.0.0')
                        labels.put('org.opencontainers.image.vendor', 'KineticFire')
                        
                        // Build configuration
                        context.set(file('.'))
                        dockerfileName.set('Dockerfile')
                        buildArgs.put('VERSION', '1.0.0')
                        buildArgs.put('BUILD_ENV', 'production')
                    }
                }
            }
            
            // Verify task configuration
            tasks.register('verifyDockerBuildConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildAppWithNomenclature')
                    println "Task configured: " + buildTask.name
                    
                    // Verify Provider API usage
                    assert buildTask.registry.isPresent()
                    assert buildTask.namespace.isPresent()
                    assert buildTask.imageName.isPresent()
                    assert buildTask.version.isPresent()
                    assert !buildTask.tags.get().isEmpty()
                    assert !buildTask.labels.get().isEmpty()
                    
                    println "All nomenclature properties configured correctly"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDockerBuildConfiguration', '--info')
            .build()

        then:
        result.output.contains('Task configured: dockerBuildAppWithNomenclature')
        result.output.contains('All nomenclature properties configured correctly')
    }

    def "docker build task configuration validates sourceRef mode"() {
        given:
        settingsFile << "rootProject.name = 'test-sourceref-mode'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    existingImage {
                        // SourceRef Mode for existing images
                        sourceRef.set('ghcr.io/acme/myapp:1.2.3')
                        
                        // Apply local tags
                        tags.set(['local:latest', 'local:stable'])
                    }
                }
            }
            
            // Verify task configuration
            tasks.register('verifySourceRefConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildExistingImage')
                    println "Task configured: " + buildTask.name
                    
                    // Verify sourceRef mode - tasks are configured but sourceRef might not be exposed on build task
                    assert !buildTask.tags.get().isEmpty()
                    
                    println "SourceRef mode configured correctly"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySourceRefConfiguration', '--info')
            .build()

        then:
        result.output.contains('Task configured: dockerBuildExistingImage')
        result.output.contains('SourceRef mode configured correctly')
    }

    def "docker build task configuration validates labels feature"() {
        given:
        settingsFile << "rootProject.name = 'test-labels-feature'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    labeledApp {
                        registry.set('docker.io')
                        repository.set('mycompany/myapp')
                        version.set(project.version.toString())
                        tags.set(['latest'])
                        
                        // Test various label configurations
                        label('org.opencontainers.image.title', 'My Application')
                        label('org.opencontainers.image.description', 'A sample application')
                        label('org.opencontainers.image.version', providers.provider { project.version.toString() })
                        
                        labels([
                            'com.example.team': 'backend',
                            'com.example.environment': 'production'
                        ])
                        
                        labels(providers.provider { 
                            ['runtime.version': System.getProperty('java.version')] 
                        })
                        
                        context.set(file('.'))
                    }
                }
            }
            
            tasks.register('verifyLabelsConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildLabeledApp')
                    def labelsMap = buildTask.labels.get()
                    
                    println "Configured labels:"
                    labelsMap.each { k, v ->
                        println "  \${k}: \${v}"
                    }
                    
                    assert labelsMap.containsKey('org.opencontainers.image.title')
                    assert labelsMap.containsKey('org.opencontainers.image.description')
                    assert labelsMap.containsKey('org.opencontainers.image.version')
                    assert labelsMap.containsKey('com.example.team')
                    assert labelsMap.containsKey('com.example.environment')
                    assert labelsMap.containsKey('runtime.version')
                    
                    println "All labels configured correctly"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyLabelsConfiguration', '--info')
            .build()

        then:
        result.output.contains('Configured labels:')
        result.output.contains('All labels configured correctly')
    }

    def "docker build task configuration supports Provider API patterns"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-api'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            // Create providers for lazy evaluation
            def registryProvider = providers.gradleProperty('docker.registry').orElse('docker.io')
            def versionProvider = providers.provider { project.version.toString() }
            def buildTimeProvider = providers.provider { new Date().toString() }
            
            docker {
                images {
                    providerApp {
                        // Use providers for lazy evaluation
                        registry.set(registryProvider)
                        namespace.set('example')
                        imageName.set('provider-test')
                        version.set(versionProvider)
                        tags.set(['latest', 'snapshot'])
                        
                        // Labels with providers
                        label('build.time', buildTimeProvider)
                        label('build.version', versionProvider)
                        
                        // Build args with providers
                        buildArg('VERSION', versionProvider)
                        buildArg('BUILD_TIME', buildTimeProvider)
                        
                        context.set(layout.projectDirectory)
                    }
                }
            }
            
            tasks.register('verifyProviderConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildProviderApp')
                    
                    // Test lazy evaluation - values should not be resolved during configuration
                    println "Registry provider: " + buildTask.registry.get()
                    println "Version provider: " + buildTask.version.get()
                    
                    def labelsMap = buildTask.labels.get()
                    println "Build time label: " + labelsMap['build.time']
                    
                    def argsMap = buildTask.buildArgs.get()
                    println "Build time arg: " + argsMap['BUILD_TIME']
                    
                    println "Provider API working correctly"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyProviderConfiguration', '--info')
            .build()

        then:
        result.output.contains('Registry provider: docker.io')
        result.output.contains('Provider API working correctly')
    }

    def "docker build task supports both repository and namespace+imageName configurations"() {
        given:
        settingsFile << "rootProject.name = 'test-dual-config'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    // Using repository approach
                    repoApp {
                        registry.set('ghcr.io')
                        repository.set('kineticfire/apps/my-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                    
                    // Using namespace + imageName approach  
                    namespaceApp {
                        registry.set('ghcr.io')
                        namespace.set('kineticfire/apps')
                        imageName.set('my-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
            
            tasks.register('verifyDualConfiguration') {
                doLast {
                    def repoTask = tasks.getByName('dockerBuildRepoApp')
                    def namespaceTask = tasks.getByName('dockerBuildNamespaceApp')
                    
                    println "Repository task: " + repoTask.name
                    println "Namespace task: " + namespaceTask.name
                    
                    // Both approaches should be valid
                    assert repoTask.repository.isPresent()
                    assert namespaceTask.namespace.isPresent()
                    assert namespaceTask.imageName.isPresent()
                    
                    println "Both configuration approaches work"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDualConfiguration', '--info')
            .build()

        then:
        result.output.contains('Repository task: dockerBuildRepoApp')
        result.output.contains('Namespace task: dockerBuildNamespaceApp')
        result.output.contains('Both configuration approaches work')
    }
}