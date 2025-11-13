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
 * Functional tests for Docker labels feature with MapProperty integration
 *
 * NOTE: These tests focus on configuration verification rather than actual Docker execution
 * due to TestKit limitations with Gradle 9. Real Docker operations are covered by
 * integration tests in the plugin-integration-test project.
 */
class DockerLabelsFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "docker build task configuration supports OCI standard labels"() {
        given:
        settingsFile << "rootProject.name = 'test-oci-labels'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    ociLabelsApp {
                        registry.set('docker.io')
                        namespace.set('mycompany')
                        imageName.set('oci-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))

                        // OCI standard labels
                        labels.put('org.opencontainers.image.title', 'My Application')
                        labels.put('org.opencontainers.image.description', 'A sample application with OCI labels')
                        labels.put('org.opencontainers.image.version', '1.0.0')
                        labels.put('org.opencontainers.image.vendor', 'KineticFire')
                        labels.put('org.opencontainers.image.licenses', 'Apache-2.0')
                        labels.put('org.opencontainers.image.source', 'https://github.com/kineticfire/gradle-docker')
                        labels.put('org.opencontainers.image.documentation', 'https://docs.example.com')
                    }
                }
            }

            tasks.register('verifyOCILabelsConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildOciLabelsApp')
                    def labels = buildTask.labels.get()

                    println "OCI Labels configured:"
                    labels.each { key, value ->
                        println "  \${key}: \${value}"
                    }

                    // Verify OCI standard labels are present
                    assert labels.containsKey('org.opencontainers.image.title')
                    assert labels.containsKey('org.opencontainers.image.version')
                    assert labels.containsKey('org.opencontainers.image.vendor')
                    assert labels['org.opencontainers.image.title'] == 'My Application'
                    assert labels['org.opencontainers.image.version'] == '1.0.0'
                    assert labels['org.opencontainers.image.vendor'] == 'KineticFire'

                    println "OCI labels configuration verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyOCILabelsConfiguration', '--info')
            .build()

        then:
        result.task(':verifyOCILabelsConfiguration').outcome == TaskOutcome.SUCCESS
        result.output.contains('OCI Labels configured:')
        result.output.contains('org.opencontainers.image.title: My Application')
        result.output.contains('org.opencontainers.image.version: 1.0.0')
        result.output.contains('org.opencontainers.image.vendor: KineticFire')
        result.output.contains('OCI labels configuration verified')
    }

    def "docker build task configuration supports custom application labels"() {
        given:
        settingsFile << "rootProject.name = 'test-custom-labels'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    customLabelsApp {
                        registry.set('ghcr.io')
                        repository.set('mycompany/custom-app')
                        version.set('2.1.0')
                        tags.set(['latest', 'v2.1.0'])
                        context.set(file('.'))

                        // Custom application labels
                        labels.put('app.name', 'custom-application')
                        labels.put('app.environment', 'production')
                        labels.put('app.tier', 'backend')
                        labels.put('app.component', 'api-server')
                        labels.put('build.number', '42')
                        labels.put('deployment.strategy', 'rolling-update')
                        labels.put('monitoring.enabled', 'true')
                        labels.put('security.scan', 'passed')
                    }
                }
            }

            tasks.register('verifyCustomLabelsConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildCustomLabelsApp')
                    def labels = buildTask.labels.get()

                    println "Custom Labels configured:"
                    labels.each { key, value ->
                        println "  \${key}: \${value}"
                    }

                    // Verify custom labels are present
                    assert labels.containsKey('app.name')
                    assert labels.containsKey('app.environment')
                    assert labels.containsKey('build.number')
                    assert labels['app.name'] == 'custom-application'
                    assert labels['app.environment'] == 'production'
                    assert labels['build.number'] == '42'

                    // Verify we have the expected number of labels
                    assert labels.size() == 8

                    println "Custom labels configuration verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyCustomLabelsConfiguration', '--info')
            .build()

        then:
        result.task(':verifyCustomLabelsConfiguration').outcome == TaskOutcome.SUCCESS
        result.output.contains('Custom Labels configured:')
        result.output.contains('app.name: custom-application')
        result.output.contains('app.environment: production')
        result.output.contains('build.number: 42')
        result.output.contains('Custom labels configuration verified')
    }

    def "docker build task configuration supports provider-based labels"() {
        given:
        settingsFile << "rootProject.name = 'test-provider-labels'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            def versionProvider = providers.provider { project.version.toString() }
            def timestampProvider = providers.provider { new Date().format('yyyy-MM-dd HH:mm:ss') }
            def gitShaProvider = providers.gradleProperty('git.sha').orElse('unknown')
            def buildEnvProvider = providers.gradleProperty('build.env').orElse('development')

            docker {
                images {
                    providerLabelsApp {
                        registry.set('docker.io')
                        namespace.set('providertest')
                        imageName.set('provider-labels-app')
                        version.set(versionProvider)
                        tags.set(['latest'])
                        context.set(file('.'))

                        // Provider-based labels with lazy evaluation
                        labels.put('org.opencontainers.image.version', versionProvider)
                        labels.put('build.timestamp', timestampProvider)
                        labels.put('git.revision', gitShaProvider)
                        labels.put('build.environment', buildEnvProvider)
                        labels.put('project.name', providers.provider { project.name })
                        labels.put('project.group', providers.provider { project.group.toString() })
                    }
                }
            }

            tasks.register('verifyProviderLabelsConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildProviderLabelsApp')
                    def labels = buildTask.labels.get()

                    println "Provider-based Labels configured:"
                    labels.each { key, value ->
                        println "  \${key}: \${value}"
                    }

                    // Verify provider-based labels are resolved
                    assert labels.containsKey('org.opencontainers.image.version')
                    assert labels.containsKey('build.timestamp')
                    assert labels.containsKey('git.revision')
                    assert labels.containsKey('build.environment')
                    assert labels.containsKey('project.name')

                    // Verify values are resolved (not just Provider objects)
                    assert labels['org.opencontainers.image.version'] != null
                    assert labels['build.timestamp'] != null
                    assert labels['git.revision'] == 'unknown' // default value
                    assert labels['build.environment'] == 'development' // default value
                    assert labels['project.name'] == 'test-provider-labels'

                    println "Provider-based labels configuration verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyProviderLabelsConfiguration', '--info')
            .build()

        then:
        result.task(':verifyProviderLabelsConfiguration').outcome == TaskOutcome.SUCCESS
        result.output.contains('Provider-based Labels configured:')
        result.output.contains('org.opencontainers.image.version:')
        result.output.contains('build.timestamp:')
        result.output.contains('git.revision: unknown')
        result.output.contains('build.environment: development')
        result.output.contains('project.name: test-provider-labels')
        result.output.contains('Provider-based labels configuration verified')
    }

    def "docker build task configuration supports mixed static and provider labels"() {
        given:
        settingsFile << "rootProject.name = 'test-mixed-labels'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    mixedLabelsApp {
                        registry.set('registry.company.com:5000')
                        namespace.set('engineering')
                        imageName.set('mixed-labels-app')
                        version.set('1.5.0')
                        tags.set(['latest', 'stable'])
                        context.set(file('.'))

                        // Mix of static and provider-based labels
                        labels.put('org.opencontainers.image.title', 'Mixed Labels Application')
                        labels.put('org.opencontainers.image.vendor', 'Example Corp')
                        labels.put('org.opencontainers.image.version', providers.provider { project.version.toString() })
                        labels.put('app.static', 'static-value')
                        labels.put('app.dynamic', providers.provider { "dynamic-\${System.currentTimeMillis()}" })
                        labels.put('build.mode', providers.gradleProperty('build.mode').orElse('standard'))
                        labels.put('config.profile', 'production')
                    }
                }
            }

            tasks.register('verifyMixedLabelsConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildMixedLabelsApp')
                    def labels = buildTask.labels.get()

                    println "Mixed Labels configured:"
                    labels.each { key, value ->
                        println "  \${key}: \${value}"
                    }

                    // Verify static labels
                    assert labels['org.opencontainers.image.title'] == 'Mixed Labels Application'
                    assert labels['org.opencontainers.image.vendor'] == 'Example Corp'
                    assert labels['app.static'] == 'static-value'
                    assert labels['config.profile'] == 'production'

                    // Verify provider-based labels are resolved
                    assert labels.containsKey('org.opencontainers.image.version')
                    assert labels.containsKey('app.dynamic')
                    assert labels.containsKey('build.mode')

                    // Verify provider defaults
                    assert labels['build.mode'] == 'standard'

                    // Verify we have all expected labels
                    assert labels.size() == 7

                    println "Mixed labels configuration verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyMixedLabelsConfiguration', '--info')
            .build()

        then:
        result.task(':verifyMixedLabelsConfiguration').outcome == TaskOutcome.SUCCESS
        result.output.contains('Mixed Labels configured:')
        result.output.contains('org.opencontainers.image.title: Mixed Labels Application')
        result.output.contains('app.static: static-value')
        result.output.contains('build.mode: standard')
        result.output.contains('Mixed labels configuration verified')
    }

    def "docker build task configuration validates empty labels configuration"() {
        given:
        settingsFile << "rootProject.name = 'test-empty-labels'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    emptyLabelsApp {
                        registry.set('docker.io')
                        imageName.set('empty-labels-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))

                        // No labels specified - should be empty map
                    }
                }
            }

            tasks.register('verifyEmptyLabelsConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildEmptyLabelsApp')
                    def labels = buildTask.labels.get()

                    println "Labels map size: \${labels.size()}"

                    // Verify labels map is empty but not null
                    assert labels != null
                    assert labels.isEmpty()
                    assert labels.size() == 0

                    println "Empty labels configuration verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyEmptyLabelsConfiguration', '--info')
            .build()

        then:
        result.task(':verifyEmptyLabelsConfiguration').outcome == TaskOutcome.SUCCESS
        result.output.contains('Labels map size: 0')
        result.output.contains('Empty labels configuration verified')
    }

    def "docker build task configuration supports labels with special characters"() {
        given:
        settingsFile << "rootProject.name = 'test-special-labels'"

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    specialLabelsApp {
                        registry.set('docker.io')
                        imageName.set('special-labels-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))

                        // Labels with special characters and various formats
                        labels.put('com.example.app-name', 'my-special-app')
                        labels.put('com.example.app_version', '1.0.0-beta.1')
                        labels.put('maintainer.email', 'developer@example.com')
                        labels.put('description.multiword', 'This is a description with spaces')
                        labels.put('config.json', '{"key": "value", "nested": {"prop": "data"}}')
                        labels.put('path.absolute', '/opt/app/config/settings.conf')
                        labels.put('url.documentation', 'https://docs.example.com/api/v1')
                        labels.put('version.semantic', '1.0.0-alpha.1+build.123')
                    }
                }
            }

            tasks.register('verifySpecialLabelsConfiguration') {
                doLast {
                    def buildTask = tasks.getByName('dockerBuildSpecialLabelsApp')
                    def labels = buildTask.labels.get()

                    println "Special Character Labels configured:"
                    labels.each { key, value ->
                        println "  \${key}: \${value}"
                    }

                    // Verify special character labels
                    assert labels['com.example.app-name'] == 'my-special-app'
                    assert labels['com.example.app_version'] == '1.0.0-beta.1'
                    assert labels['maintainer.email'] == 'developer@example.com'
                    assert labels['description.multiword'] == 'This is a description with spaces'
                    assert labels['config.json'] == '{"key": "value", "nested": {"prop": "data"}}'
                    assert labels['path.absolute'] == '/opt/app/config/settings.conf'
                    assert labels['url.documentation'] == 'https://docs.example.com/api/v1'
                    assert labels['version.semantic'] == '1.0.0-alpha.1+build.123'

                    assert labels.size() == 8

                    println "Special character labels configuration verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySpecialLabelsConfiguration', '--info')
            .build()

        then:
        result.task(':verifySpecialLabelsConfiguration').outcome == TaskOutcome.SUCCESS
        result.output.contains('Special Character Labels configured:')
        result.output.contains('com.example.app-name: my-special-app')
        result.output.contains('maintainer.email: developer@example.com')
        result.output.contains('config.json: {"key": "value", "nested": {"prop": "data"}}')
        result.output.contains('Special character labels configuration verified')
    }
}