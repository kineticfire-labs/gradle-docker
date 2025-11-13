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
 * Functional tests for Docker tag operations with new nomenclature API
 * 
 * NOTE: These tests focus on configuration verification rather than actual Docker execution
 * due to TestKit limitations with Gradle 9. Real Docker operations are covered by 
 * integration tests in the plugin-integration-test project.
 */
class DockerTagFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "docker tag task configuration supports build mode with nomenclature"() {
        given:
        settingsFile << "rootProject.name = 'test-tag-nomenclature'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    tagApp {
                        // Build mode using nomenclature
                        registry.set('docker.io')
                        namespace.set('mycompany')
                        imageName.set('tag-test')
                        version.set('1.0.0')
                        tags.set(['latest', 'stable', 'v1.0.0'])
                        context.set(file('.'))
                    }
                }
            }
            
            tasks.register('verifyTagNomenclatureConfiguration') {
                doLast {
                    def tagTask = tasks.getByName('dockerTagTagApp')
                    
                    println "Tag task: " + tagTask.name
                    println "Registry: " + tagTask.registry.get()
                    println "Namespace: " + tagTask.namespace.get()
                    println "Image name: " + tagTask.imageName.get()
                    println "Version: " + tagTask.version.get()
                    println "Tags: " + tagTask.tags.get()
                    
                    assert tagTask.registry.isPresent()
                    assert tagTask.namespace.isPresent()
                    assert tagTask.imageName.isPresent()
                    assert tagTask.version.isPresent()
                    assert !tagTask.tags.get().isEmpty()
                    
                    println "Tag nomenclature configuration correct"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTagNomenclatureConfiguration', '--info')
            .build()

        then:
        result.output.contains('Tag task: dockerTagTagApp')
        result.output.contains('Registry: docker.io')
        result.output.contains('Namespace: mycompany')
        result.output.contains('Image name: tag-test')
        result.output.contains('Version: 1.0.0')
        result.output.contains('Tags: [latest, stable, v1.0.0]')
        result.output.contains('Tag nomenclature configuration correct')
    }

    def "docker tag task configuration supports sourceRef mode"() {
        given:
        settingsFile << "rootProject.name = 'test-tag-sourceref'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    existingImage {
                        // SourceRef mode for existing images
                        sourceRef.set('ghcr.io/upstream/app:v1.2.3')
                        tags.set(['local:latest', 'local:stable', 'local:v1.2.3'])
                    }
                }
            }
            
            tasks.register('verifyTagSourceRefConfiguration') {
                doLast {
                    def tagTask = tasks.getByName('dockerTagExistingImage')
                    
                    println "Tag task: " + tagTask.name
                    println "Tags: " + tagTask.tags.get()
                    
                    assert !tagTask.tags.get().isEmpty()
                    
                    println "Tag sourceRef configuration correct"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTagSourceRefConfiguration', '--info')
            .build()

        then:
        result.output.contains('Tag task: dockerTagExistingImage')
        result.output.contains('Tags: [local:latest, local:stable, local:v1.2.3]')
        result.output.contains('Tag sourceRef configuration correct')
    }

    def "docker tag task configuration supports repository format"() {
        given:
        settingsFile << "rootProject.name = 'test-tag-repository'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    repoApp {
                        // Using repository format
                        registry.set('ghcr.io')
                        repository.set('myorg/apps/my-app')
                        version.set('2.0.0')
                        tags.set(['latest', 'v2.0.0'])
                        context.set(file('.'))
                    }
                }
            }
            
            tasks.register('verifyTagRepositoryConfiguration') {
                doLast {
                    def tagTask = tasks.getByName('dockerTagRepoApp')
                    
                    println "Tag task: " + tagTask.name
                    println "Registry: " + tagTask.registry.get()
                    println "Repository: " + tagTask.repository.get()
                    println "Version: " + tagTask.version.get()
                    println "Tags: " + tagTask.tags.get()
                    
                    assert tagTask.registry.isPresent()
                    assert tagTask.repository.isPresent()
                    assert tagTask.version.isPresent()
                    assert !tagTask.tags.get().isEmpty()
                    
                    println "Tag repository configuration correct"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTagRepositoryConfiguration', '--info')
            .build()

        then:
        result.output.contains('Tag task: dockerTagRepoApp')
        result.output.contains('Registry: ghcr.io')
        result.output.contains('Repository: myorg/apps/my-app')
        result.output.contains('Version: 2.0.0')
        result.output.contains('Tags: [latest, v2.0.0]')
        result.output.contains('Tag repository configuration correct')
    }

    def "docker tag task configuration uses Provider API for lazy evaluation"() {
        given:
        settingsFile << "rootProject.name = 'test-tag-providers'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            def versionProvider = providers.provider { project.version.toString() }
            def registryProvider = providers.gradleProperty('docker.registry').orElse('docker.io')
            def tagsProvider = providers.provider { 
                ['latest', "v\${project.version}", new Date().format('yyyyMMdd')]
            }
            
            docker {
                images {
                    providerApp {
                        registry.set(registryProvider)
                        imageName.set('provider-tag-test')
                        version.set(versionProvider)
                        tags.set(tagsProvider)
                        context.set(file('.'))
                    }
                }
            }
            
            tasks.register('verifyTagProviderConfiguration') {
                doLast {
                    def tagTask = tasks.getByName('dockerTagProviderApp')
                    
                    println "Tag task: " + tagTask.name
                    
                    // Test lazy evaluation - should work during execution
                    println "Registry: " + tagTask.registry.get()
                    println "Version: " + tagTask.version.get()
                    println "Tags: " + tagTask.tags.get()
                    
                    assert tagTask.registry.isPresent()
                    assert tagTask.version.isPresent()
                    assert !tagTask.tags.get().isEmpty()
                    
                    println "Tag provider configuration working"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTagProviderConfiguration', '--info')
            .build()

        then:
        result.output.contains('Tag task: dockerTagProviderApp')
        result.output.contains('Registry: docker.io')
        result.output.contains('Tags: [latest,')
        result.output.contains('Tag provider configuration working')
    }

    def "docker tag task configuration supports dynamic tag generation"() {
        given:
        settingsFile << "rootProject.name = 'test-tag-dynamic'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    dynamicApp {
                        registry.set('docker.io')
                        imageName.set('dynamic-tag-test')
                        version.set('1.0.0')
                        
                        // Dynamic tag generation based on build environment
                        tags.set(providers.provider {
                            def baseTags = ['latest']
                            
                            // Add version tag
                            baseTags.add("v\${project.version}")
                            
                            // Add branch-based tag if available
                            def branch = providers.environmentVariable('GIT_BRANCH').getOrElse('main')
                            if (branch != 'main') {
                                baseTags.add("branch-\${branch}")
                            }
                            
                            // Add build number if available
                            def buildNumber = providers.environmentVariable('BUILD_NUMBER').getOrNull()
                            if (buildNumber) {
                                baseTags.add("build-\${buildNumber}")
                            }
                            
                            return baseTags
                        })
                        
                        context.set(file('.'))
                    }
                }
            }
            
            tasks.register('verifyDynamicTagConfiguration') {
                doLast {
                    def tagTask = tasks.getByName('dockerTagDynamicApp')
                    
                    println "Dynamic tag task: " + tagTask.name
                    
                    def tags = tagTask.tags.get()
                    println "Generated tags: " + tags
                    
                    // Should always have at least latest and version tags
                    assert tags.contains('latest')
                    assert tags.any { it.startsWith('v') }
                    
                    println "Dynamic tag generation working"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDynamicTagConfiguration', '--info')
            .build()

        then:
        result.output.contains('Dynamic tag task: dockerTagDynamicApp')
        result.output.contains('Generated tags:')
        result.output.contains('latest')
        result.output.contains('Dynamic tag generation working')
    }

    def "docker tag task configuration supports sourceRef mode with complex tag scenarios"() {
        given:
        settingsFile << "rootProject.name = 'test-tag-sourceref-complex'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    complexSourceRefApp {
                        // SourceRef mode for retagging existing images with complex tags
                        sourceRef.set('ghcr.io/upstream/awesome-app:v2.1.0')
                        tags.set([
                            'internal:latest',
                            'internal:stable',
                            'internal:v2.1.0',
                            'backup.registry.com/mirrors/awesome-app:latest',
                            'backup.registry.com/mirrors/awesome-app:v2.1.0'
                        ])
                    }
                }
            }

            tasks.register('verifyComplexSourceRefTagConfiguration') {
                doLast {
                    def tagTask = tasks.getByName('dockerTagComplexSourceRefApp')

                    println "Complex SourceRef tag task: " + tagTask.name

                    def tags = tagTask.tags.get()
                    println "SourceRef tags: " + tags

                    // Verify all expected tags are present
                    assert tags.contains('internal:latest')
                    assert tags.contains('internal:stable')
                    assert tags.contains('internal:v2.1.0')
                    assert tags.contains('backup.registry.com/mirrors/awesome-app:latest')
                    assert tags.contains('backup.registry.com/mirrors/awesome-app:v2.1.0')
                    assert tags.size() == 5

                    println "Complex sourceRef tag configuration verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyComplexSourceRefTagConfiguration', '--info')
            .build()

        then:
        result.output.contains('Complex SourceRef tag task: dockerTagComplexSourceRefApp')
        result.output.contains('internal:latest')
        result.output.contains('backup.registry.com/mirrors/awesome-app:latest')
        result.output.contains('Complex sourceRef tag configuration verified')
    }

    def "docker tag task configuration validates sourceRef mode excludes build properties"() {
        given:
        settingsFile << "rootProject.name = 'test-tag-sourceref-validation'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    invalidSourceRefApp {
                        // SourceRef mode with build properties (should be invalid)
                        sourceRef.set('ghcr.io/upstream/app:1.0.0')
                        tags.set(['local:latest'])

                        // These should be invalid with sourceRef
                        context.set(file('.'))
                        buildArgs.put('VERSION', '1.0.0')
                        labels.put('build.time', 'now')
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .buildAndFail()

        then:
        // Should fail due to sourceRef + build properties combination
        result.output.contains('Cannot mix Build Mode and SourceRef Mode')
    }

    def "docker tag task configuration supports provider-based sourceRef and tags"() {
        given:
        settingsFile << "rootProject.name = 'test-tag-provider-sourceref'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            def sourceImageProvider = providers.gradleProperty('source.image')
                .orElse('ghcr.io/default/app:latest')
            def targetRegistryProvider = providers.gradleProperty('target.registry')
                .orElse('local')
            def versionProvider = providers.provider { project.version.toString() }

            docker {
                images {
                    providerSourceRefApp {
                        // Provider-based sourceRef mode
                        sourceRef.set(sourceImageProvider)
                        tags.set(providers.provider {
                            def registry = targetRegistryProvider.get()
                            def version = versionProvider.get()
                            return [
                                "\${registry}:latest",
                                "\${registry}:stable",
                                "\${registry}:v\${version}"
                            ]
                        })
                    }
                }
            }

            tasks.register('verifyProviderSourceRefTagConfiguration') {
                doLast {
                    def tagTask = tasks.getByName('dockerTagProviderSourceRefApp')

                    println "Provider SourceRef tag task: " + tagTask.name

                    def tags = tagTask.tags.get()
                    println "Provider-based tags: " + tags

                    // Verify provider-based tags are resolved
                    assert tags.contains('local:latest')
                    assert tags.contains('local:stable')
                    assert tags.any { it.startsWith('local:v') }
                    assert tags.size() == 3

                    println "Provider-based sourceRef tag configuration verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyProviderSourceRefTagConfiguration', '--info')
            .build()

        then:
        result.output.contains('Provider SourceRef tag task: dockerTagProviderSourceRefApp')
        result.output.contains('local:latest')
        result.output.contains('local:stable')
        result.output.contains('Provider-based sourceRef tag configuration verified')
    }

    def "docker tag task configuration supports dual-mode detection and validation"() {
        given:
        settingsFile << "rootProject.name = 'test-tag-dual-mode'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    buildModeApp {
                        // Build mode - should work
                        registry.set('docker.io')
                        imageName.set('build-app')
                        version.set('1.0.0')
                        tags.set(['latest', 'build'])
                        context.set(file('.'))
                    }

                    sourceRefModeApp {
                        // SourceRef mode - should work
                        sourceRef.set('ghcr.io/existing/app:v1.0.0')
                        tags.set(['local:latest', 'local:imported'])
                    }
                }
            }

            tasks.register('verifyDualModeTagConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerTagBuildModeApp')
                    def sourceRefTask = tasks.getByName('dockerTagSourceRefModeApp')

                    println "Build mode task: " + buildTask.name
                    println "SourceRef mode task: " + sourceRefTask.name

                    // Both tasks should be configured correctly
                    assert buildTask.registry.isPresent()
                    assert buildTask.imageName.isPresent()
                    assert !buildTask.tags.get().isEmpty()

                    assert !sourceRefTask.tags.get().isEmpty()

                    println "Dual-mode configuration verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDualModeTagConfiguration', '--info')
            .build()

        then:
        result.output.contains('Build mode task: dockerTagBuildModeApp')
        result.output.contains('SourceRef mode task: dockerTagSourceRefModeApp')
        result.output.contains('Dual-mode configuration verified')
    }
}