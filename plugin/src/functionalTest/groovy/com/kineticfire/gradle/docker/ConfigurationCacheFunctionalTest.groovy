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
 * Functional tests for configuration cache compatibility
 *
 * These tests verify that the gradle-docker plugin works correctly with Gradle's
 * configuration cache feature, which is critical for Gradle 9+ compatibility.
 *
 * Configuration cache improves build performance by caching the result of the
 * configuration phase and reusing it on subsequent builds.
 */
class ConfigurationCacheFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        settingsFile << "rootProject.name = 'config-cache-test'"
    }

    // ==================== Basic Cache Behavior ====================

    def "plugin configuration is cached and reused on second run"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    testImage {
                        imageName.set('test-app')
                        tags.set(['v1.0', 'latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when: "first build stores configuration cache"
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        and: "second build reuses cached configuration"
        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        then: "first build creates cache entry"
        result1.output.contains('Configuration cache entry stored')

        and: "second build reuses cache"
        result2.output.contains('Reusing configuration cache')
    }

    def "docker build task configuration is cached correctly"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    buildCacheTest {
                        imageName.set('build-test')
                        tags.set(['cached'])
                        labels.put('version', '1.0')
                        buildArgs.put('BUILD_ENV', 'test')
                        context.set(file('.'))
                    }
                }
            }
        """

        when: "build with configuration cache"
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('dockerBuildBuildCacheTest')

        result2.output.contains('Reusing configuration cache')
        result2.output.contains('dockerBuildBuildCacheTest')
    }

    def "docker save task configuration is cached correctly"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    saveTest {
                        imageName.set('save-test')
                        tags.set(['v1'])
                        context.set(file('.'))

                        save {
                            outputFile.set(file('build/images/test.tar.gz'))
                            compression.set('gzip')
                        }
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('dockerSaveSaveTest')

        result2.output.contains('Reusing configuration cache')
    }

    def "docker publish task configuration is cached correctly"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    publishTest {
                        imageName.set('publish-test')
                        tags.set(['v1'])
                        context.set(file('.'))

                        publish {
                            to('example') {
                                registry.set('registry.example.com')
                            }
                            to('ghcr') {
                                registry.set('ghcr.io')
                            }
                        }
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('dockerPublishPublishTest')

        result2.output.contains('Reusing configuration cache')
    }

    def "compose stack configuration is cached correctly"() {
        given:
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            services:
              web:
                image: nginx:alpine
                ports:
                  - "8080"
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    webapp {
                        composeFiles('docker-compose.yml')
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('composeUpWebapp')

        result2.output.contains('Reusing configuration cache')
    }

    // ==================== Cache Invalidation ====================

    def "cache invalidated when build.gradle changes"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    test {
                        imageName.set('test-v1')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when: "first build"
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        and: "modify build.gradle"
        buildFile.text = """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    test {
                        imageName.set('test-v2')
                        tags.set(['v2'])
                        context.set(file('.'))
                    }
                }
            }
        """

        and: "second build after modification"
        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result2.output.contains('Configuration cache entry discarded') ||
            result2.output.contains('Configuration cache entry stored')
    }

    def "cache invalidated when image configuration changes"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    dynamic {
                        imageName.set('app')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when: "first build"
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        and: "add new image"
        buildFile.text = """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    dynamic {
                        imageName.set('app')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                    newImage {
                        imageName.set('new')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        and: "second build"
        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result2.output.contains('Configuration cache entry discarded') ||
            result2.output.contains('Configuration cache entry stored')
    }

    def "cache invalidated when gradle property changes"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    test {
                        imageName.set(providers.gradleProperty('imageName').orElse('default'))
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when: "first build with property"
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache', '-PimageName=custom')
            .build()

        and: "second build with different property"
        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache', '-PimageName=different')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        // Cache should be invalidated because property changed
        result2.output.contains('Configuration cache entry discarded') ||
            result2.output.contains('Configuration cache entry stored')
    }

    def "cache NOT invalidated when unrelated file changes"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    stable {
                        imageName.set('stable-image')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def unrelatedFile = testProjectDir.resolve('README.md').toFile()
        unrelatedFile << "# Test Project"

        when: "first build"
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        and: "modify unrelated file"
        unrelatedFile << "\nUpdated content"

        and: "second build"
        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result2.output.contains('Reusing configuration cache')
    }

    // ==================== Provider and Multi-File Scenarios ====================

    def "provider-based image configuration works with cache"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    providerTest {
                        imageName.set(providers.provider { "app-\${project.name}" })
                        version.set(providers.provider { project.version })
                        tags.set(providers.provider { ['latest', "v\${project.version}"] })
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result2.output.contains('Reusing configuration cache')
    }

    def "multi-file compose stacks work with cache"() {
        given:
        def baseComposeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        baseComposeFile << """
            services:
              app:
                image: alpine:latest
        """

        def overrideComposeFile = testProjectDir.resolve('docker-compose.override.yml').toFile()
        overrideComposeFile << """
            services:
              app:
                environment:
                  - ENV=test
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    multiFile {
                        composeFiles('docker-compose.yml', 'docker-compose.override.yml')
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('composeUpMultiFile')

        result2.output.contains('Reusing configuration cache')
    }

    def "file provider-based configuration works with cache"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    fileProviderTest {
                        imageName.set('file-test')
                        tags.set(['v1'])
                        context.set(file('.'))

                        save {
                            outputFile.set(layout.buildDirectory.file('docker/image.tar'))
                            compression.set('none')
                        }
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result2.output.contains('Reusing configuration cache')
    }

    // ==================== Aggregate Tasks ====================

    def "dockerBuild aggregate task works with cache"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    app1 {
                        imageName.set('app1')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                    app2 {
                        imageName.set('app2')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('dockerBuild ')

        result2.output.contains('Reusing configuration cache')
    }

    def "dockerImages aggregate task works with cache"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    image1 {
                        imageName.set('image1')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                    image2 {
                        imageName.set('image2')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('dockerImages')

        result2.output.contains('Reusing configuration cache')
    }

    def "composeUp aggregate task works with cache"() {
        given:
        def compose1 = testProjectDir.resolve('compose1.yml').toFile()
        compose1 << """
            services:
              web1:
                image: nginx:alpine
        """

        def compose2 = testProjectDir.resolve('compose2.yml').toFile()
        compose2 << """
            services:
              web2:
                image: nginx:alpine
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    stack1 {
                        composeFiles('compose1.yml')
                    }
                    stack2 {
                        composeFiles('compose2.yml')
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('composeUp ')

        result2.output.contains('Reusing configuration cache')
    }

    // ==================== Complex Scenarios ====================

    def "complex configuration with multiple images and compose stacks works with cache"() {
        given:
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            services:
              api:
                image: alpine:latest
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    backend {
                        registry.set('ghcr.io')
                        namespace.set('myorg')
                        imageName.set('backend')
                        version.set('1.0.0')
                        tags.set(['latest', 'stable'])
                        labels.put('version', '1.0.0')
                        context.set(file('.'))

                        save {
                            outputFile.set(file('build/backend.tar.gz'))
                            compression.set('gzip')
                        }

                        publish {
                            to('ghcr') {
                                registry.set('ghcr.io')
                            }
                            to('dockerhub') {
                                registry.set('docker.io')
                            }
                        }
                    }

                    frontend {
                        imageName.set('frontend')
                        tags.set(['dev'])
                        context.set(file('.'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    integration {
                        composeFiles('docker-compose.yml')

                        waitForHealthy {
                            services.set(['api'])
                            timeoutSeconds.set(30)
                        }
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('dockerBuildBackend')
        result1.output.contains('dockerBuildFrontend')
        result1.output.contains('dockerSaveBackend')
        result1.output.contains('dockerPublishBackend')
        result1.output.contains('composeUpIntegration')

        result2.output.contains('Reusing configuration cache')
    }

    def "SourceRef mode configuration works with cache"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    fromRegistry {
                        sourceRef.set('docker.io/library/alpine:3.18')
                        pullIfMissing.set(true)
                        tags.set(['local:latest', 'local:stable'])

                        publish {
                            to('internal') {
                                registry.set('registry.internal.com')
                            }
                        }
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result1.output.contains('dockerTagFromRegistry')
        result1.output.contains('dockerPublishFromRegistry')

        result2.output.contains('Reusing configuration cache')
    }

// ==================== Workflow Configuration ====================

    def "dockerWorkflows DSL configuration is cached correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myApp {
                        imageName.set('my-app')
                        tags.set(['v1.0', 'latest'])
                        context.set(file('.'))
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    release {
                        description.set('Release pipeline')
                    }
                }
            }
        """

        when: "first build stores configuration cache"
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        and: "second build reuses cached configuration"
        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        then: "first build creates cache entry"
        result1.output.contains('Configuration cache entry stored')

        and: "second build reuses cache"
        result2.output.contains('Reusing configuration cache')
    }

    def "workflow with multiple pipelines is cached correctly"() {
        given:
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            services:
              app:
                image: alpine:latest
        """

        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    backend {
                        imageName.set('backend')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    integrationTest {
                        composeFiles('docker-compose.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    ci {
                        description.set('CI pipeline')
                    }
                    release {
                        description.set('Release pipeline')
                    }
                }
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')

        result2.output.contains('Reusing configuration cache')
    }

    def "test integration extension configuration works with cache"() {
        given:
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            services:
              db:
                image: postgres:alpine
        """

        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    testDb {
                        composeFiles('docker-compose.yml')
                    }
                }
            }

            test {
                usesCompose(stack: 'testDb', lifecycle: 'class')
            }
        """

        when:
        def result1 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        def result2 = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--configuration-cache')
            .build()

        then:
        result1.output.contains('Configuration cache entry stored')
        result2.output.contains('Reusing configuration cache')
    }
}
