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

import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Ignore
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for Provider API edge cases and advanced configuration cache scenarios
 *
 * NOTE: These tests focus on configuration verification rather than actual Docker execution
 * due to TestKit limitations with Gradle 9. Real Docker operations are covered by
 * integration tests in the plugin-integration-test project.
 */
class DockerProviderAPIFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "docker Provider API supports complex gradle property resolution chains"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-chains'"

        buildFile << """
            import com.kineticfire.gradle.docker.model.SaveCompression

            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            // Complex provider chains with multiple fallbacks
            def registryProvider = providers.gradleProperty('docker.registry')
                .orElse(providers.systemProperty('DOCKER_REGISTRY'))
                .orElse(providers.environmentVariable('DOCKER_REGISTRY'))
                .orElse('docker.io')

            def namespaceProvider = providers.gradleProperty('docker.namespace')
                .orElse(providers.provider {
                    // Dynamic fallback based on project structure
                    def group = project.group?.toString()
                    return group ? group.replace('.', '/') : 'default'
                })

            def versionProvider = providers.gradleProperty('release.version')
                .orElse(providers.provider {
                    def baseVersion = project.version.toString()
                    def buildId = System.getenv('BUILD_ID')
                    return buildId ? "\${baseVersion}-build.\${buildId}" : "\${baseVersion}-SNAPSHOT"
                })

            def tagsProvider = providers.provider {
                def version = versionProvider.get()
                def tags = ['latest']

                // Add version-based tags
                if (!version.contains('SNAPSHOT')) {
                    tags.add("v\${version}")

                    // Add semantic version tags
                    def versionParts = version.split('\\\\.')
                    if (versionParts.length >= 2) {
                        tags.add("v\${versionParts[0]}.\${versionParts[1]}")
                    }
                    if (versionParts.length >= 1) {
                        tags.add("v\${versionParts[0]}")
                    }
                } else {
                    tags.add('snapshot')
                    tags.add("snapshot-\${new Date().format('yyyyMMdd')}")
                }

                return tags
            }

            docker {
                images {
                    providerChainApp {
                        registry.set(registryProvider)
                        namespace.set(namespaceProvider)
                        imageName.set('provider-chain-test')
                        version.set(versionProvider)
                        tags.set(tagsProvider)
                        context.set(file('.'))

                        // Provider-based build args
                        buildArgs.put('REGISTRY', registryProvider)
                        buildArgs.put('VERSION', versionProvider)
                        buildArgs.put('BUILD_ENV', providers.gradleProperty('build.env').orElse('development'))

                        // Provider-based labels
                        labels.put('registry.source', registryProvider)
                        labels.put('version.resolved', versionProvider)
                        labels.put('namespace.computed', namespaceProvider)
                        labels.put('build.timestamp', providers.provider { new Date().toString() })
                    }
                }
            }

            tasks.register('verifyProviderChains') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildProviderChainApp')

                    println "=== Provider Chain Resolution Verification ==="

                    // Verify all providers are resolved
                    println "Registry: " + buildTask.registry.get()
                    println "Namespace: " + buildTask.namespace.get()
                    println "Version: " + buildTask.version.get()
                    println "Tags: " + buildTask.tags.get()

                    // Verify fallback resolution worked
                    assert buildTask.registry.get() == 'docker.io' // Should use default fallback
                    assert buildTask.namespace.get() != null
                    assert buildTask.version.get().contains('SNAPSHOT') // Should use project version fallback

                    // Verify labels resolved
                    def labels = buildTask.labels.get()
                    assert labels['registry.source'] == 'docker.io'
                    assert labels.containsKey('version.resolved')
                    assert labels.containsKey('build.timestamp')

                    println "Provider chain resolution verification completed"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyProviderChains', '--info')
            .build()

        then:
        result.task(':verifyProviderChains').outcome == TaskOutcome.SUCCESS
        result.output.contains('=== Provider Chain Resolution Verification ===')
        result.output.contains('Registry: docker.io')
        result.output.contains('Provider chain resolution verification completed')
    }

    def "provider map chains work correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-map'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    mapTest {
                        // Chain multiple map operations
                        def baseVersion = providers.provider { '1.0.0' }
                        def mappedVersion = baseVersion
                            .map { v -> "v\${v}" }
                            .map { v -> "\${v}-release" }
                            .map { v -> v.toUpperCase() }

                        imageName.set('map-test')
                        tags.set(providers.provider { ['latest'] })
                        version.set(mappedVersion)
                        context.set(file('.'))
                    }
                }
            }

            task verifyMapChain {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildMapTest')
                    def version = buildTask.version.get()
                    println "Mapped version: \${version}"
                    assert version == 'V1.0.0-RELEASE'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyMapChain')
            .build()

        then:
        result.output.contains('Mapped version: V1.0.0-RELEASE')
    }

    def "provider flatMap resolves correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-flatmap'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    flatMapTest {
                        def envProvider = providers.provider { 'prod' }
                        def registryProvider = envProvider.flatMap { env ->
                            providers.provider {
                                env == 'prod' ? 'prod.registry.io' : 'dev.registry.io'
                            }
                        }

                        registry.set(registryProvider)
                        imageName.set('flatmap-test')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyFlatMap {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildFlatMapTest')
                    def registry = buildTask.registry.get()
                    println "FlatMapped registry: \${registry}"
                    assert registry == 'prod.registry.io'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyFlatMap')
            .build()

        then:
        result.output.contains('FlatMapped registry: prod.registry.io')
    }

    def "provider zip combines values"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-zip'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    zipTest {
                        def nameProvider = providers.provider { 'myapp' }
                        def versionProvider = providers.provider { '2.0' }

                        def combinedProvider = nameProvider.zip(versionProvider) { name, version ->
                            "\${name}-\${version}"
                        }

                        imageName.set(combinedProvider)
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyZip {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildZipTest')
                    def imageName = buildTask.imageName.get()
                    println "Zipped image name: \${imageName}"
                    assert imageName == 'myapp-2.0'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyZip')
            .build()

        then:
        result.output.contains('Zipped image name: myapp-2.0')
    }

    def "nested providers resolve correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-nested-providers'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    nestedTest {
                        def level1 = providers.provider { 'base' }
                        def level2 = providers.provider { level1.get() + '-middle' }
                        def level3 = providers.provider { level2.get() + '-final' }

                        namespace.set(level3)
                        imageName.set('nested-test')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyNested {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildNestedTest')
                    def namespace = buildTask.namespace.get()
                    println "Nested namespace: \${namespace}"
                    assert namespace == 'base-middle-final'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyNested')
            .build()

        then:
        result.output.contains('Nested namespace: base-middle-final')
    }

    def "providers resolve during execution not configuration"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-lifecycle'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            def configTimeValue = 'config'
            def execTimeValue = 'exec'

            docker {
                images {
                    lifecycleTest {
                        // Provider should NOT resolve during configuration
                        def lazyProvider = providers.provider {
                            println "Provider resolving"
                            execTimeValue
                        }

                        namespace.set(lazyProvider)
                        imageName.set('lifecycle-test')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyLifecycle {
                doLast {
                    // Provider should resolve here during execution
                    def buildTask = tasks.getByName('dockerBuildLifecycleTest')
                    def namespace = buildTask.namespace.get()
                    println "Namespace resolved: \${namespace}"
                    assert namespace == 'exec'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyLifecycle')
            .build()

        then:
        result.output.contains('Namespace resolved: exec')
        result.output.contains('Provider resolving')
    }

    def "provider orElse provides fallback"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-fallback'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    fallbackTest {
                        // Provider with fallback chain
                        def registryProvider = providers.gradleProperty('custom.registry')
                            .orElse(providers.environmentVariable('DOCKER_REGISTRY'))
                            .orElse('default.registry.io')

                        registry.set(registryProvider)
                        imageName.set('fallback-test')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyFallback {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildFallbackTest')
                    def registry = buildTask.registry.get()
                    println "Registry with fallback: \${registry}"
                    // Should use last fallback since property/env not set
                    assert registry == 'default.registry.io'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyFallback')
            .build()

        then:
        result.output.contains('Registry with fallback: default.registry.io')
    }

    def "provider with gradle property resolution"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-gradleprop'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    propTest {
                        registry.set(providers.gradleProperty('docker.registry').orElse('docker.io'))
                        namespace.set(providers.gradleProperty('docker.namespace').orElse('library'))
                        imageName.set('prop-test')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyGradleProperty {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildPropTest')
                    def registry = buildTask.registry.get()
                    def namespace = buildTask.namespace.get()
                    println "Registry: \${registry}, Namespace: \${namespace}"
                    assert registry == 'custom.registry.io'
                    assert namespace == 'myorg'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyGradleProperty', '-Pdocker.registry=custom.registry.io', '-Pdocker.namespace=myorg')
            .build()

        then:
        result.output.contains('Registry: custom.registry.io, Namespace: myorg')
    }

    def "provider with environment variable resolution"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-env'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    envTest {
                        registry.set(providers.environmentVariable('TEST_REGISTRY').orElse('docker.io'))
                        imageName.set('env-test')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyEnvVar {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildEnvTest')
                    def registry = buildTask.registry.get()
                    println "Registry from env: \${registry}"
                    // Should use fallback since TEST_REGISTRY not set
                    assert registry == 'docker.io'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyEnvVar')
            .build()

        then:
        result.output.contains('Registry from env: docker.io')
    }
}