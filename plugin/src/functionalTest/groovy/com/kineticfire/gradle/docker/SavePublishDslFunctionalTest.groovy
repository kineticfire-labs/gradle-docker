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
 * Functional tests for save and publish DSL methods in onTestSuccess block.
 *
 * These tests verify that the DSL parsing works correctly via Gradle TestKit.
 */
class SavePublishDslFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-save-publish-dsl'"
    }

    private List<File> getPluginClasspath() {
        return System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) }
    }

    def "onTestSuccess with save block parses correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myApp {
                        imageName = 'test-app'
                        tags = ['latest']
                        context.set(file('.'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    myTest {
                        files.from('compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    savePipeline {
                        description = 'Pipeline with save operation'

                        build {
                            image = docker.images.myApp
                        }

                        test {
                            stack = dockerTest.composeStacks.myTest
                            testTaskName = 'integrationTest'
                        }

                        onTestSuccess {
                            additionalTags = ['tested']

                            save {
                                outputFile.set(file('build/images/my-image.tar'))
                                compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
                            }
                        }
                    }
                }
            }

            tasks.register('verifySaveConfig') {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('savePipeline')
                    def successSpec = pipeline.onTestSuccess.get()

                    assert successSpec.additionalTags.get() == ['tested']
                    assert successSpec.save.present
                    assert successSpec.save.get().compression.get().name() == 'GZIP'
                    println "SUCCESS: Save DSL parsed correctly"
                }
            }
        """

        // Create minimal required files
        testProjectDir.resolve('Dockerfile').toFile().text = 'FROM alpine:latest'
        testProjectDir.resolve('compose').toFile().mkdirs()
        testProjectDir.resolve('compose/app.yml').toFile().text = '''
services:
  app:
    image: alpine:latest
'''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifySaveConfig', '--stacktrace')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('SUCCESS: Save DSL parsed correctly')
        result.task(':verifySaveConfig').outcome == TaskOutcome.SUCCESS
    }

    def "onTestSuccess with publish block parses correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myApp {
                        imageName = 'test-app'
                        tags = ['latest']
                        context.set(file('.'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    myTest {
                        files.from('compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    publishPipeline {
                        description = 'Pipeline with publish operation'

                        build {
                            image = docker.images.myApp
                        }

                        test {
                            stack = dockerTest.composeStacks.myTest
                            testTaskName = 'integrationTest'
                        }

                        onTestSuccess {
                            publish {
                                publishTags.set(['v1.0', 'latest'])
                            }
                        }
                    }
                }
            }

            tasks.register('verifyPublishConfig') {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('publishPipeline')
                    def successSpec = pipeline.onTestSuccess.get()

                    assert successSpec.publish.present
                    assert successSpec.publish.get().publishTags.get() == ['v1.0', 'latest']
                    println "SUCCESS: Publish DSL parsed correctly"
                }
            }
        """

        // Create minimal required files
        testProjectDir.resolve('Dockerfile').toFile().text = 'FROM alpine:latest'
        testProjectDir.resolve('compose').toFile().mkdirs()
        testProjectDir.resolve('compose/app.yml').toFile().text = '''
services:
  app:
    image: alpine:latest
'''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyPublishConfig', '--stacktrace')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('SUCCESS: Publish DSL parsed correctly')
        result.task(':verifyPublishConfig').outcome == TaskOutcome.SUCCESS
    }

    def "onTestSuccess with save and publish blocks parses correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myApp {
                        imageName = 'test-app'
                        tags = ['latest']
                        context.set(file('.'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    myTest {
                        files.from('compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    fullPipeline {
                        description = 'Pipeline with save and publish'

                        build {
                            image = docker.images.myApp
                        }

                        test {
                            stack = dockerTest.composeStacks.myTest
                            testTaskName = 'integrationTest'
                        }

                        onTestSuccess {
                            additionalTags = ['verified', 'stable']

                            save {
                                outputFile.set(file('build/images/release.tar'))
                                compression.set(com.kineticfire.gradle.docker.model.SaveCompression.XZ)
                            }

                            publish {
                                publishTags.set(['release'])
                                to('production') {
                                    registry.set('ghcr.io')
                                    namespace.set('myorg')
                                }
                            }
                        }
                    }
                }
            }

            tasks.register('verifyFullConfig') {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('fullPipeline')
                    def successSpec = pipeline.onTestSuccess.get()

                    assert successSpec.additionalTags.get() == ['verified', 'stable']
                    assert successSpec.save.present
                    assert successSpec.save.get().compression.get().name() == 'XZ'
                    assert successSpec.publish.present
                    assert successSpec.publish.get().publishTags.get() == ['release']
                    assert successSpec.publish.get().to.size() == 1
                    assert successSpec.publish.get().to.getByName('production').registry.get() == 'ghcr.io'
                    println "SUCCESS: Full DSL parsed correctly"
                }
            }
        """

        // Create minimal required files
        testProjectDir.resolve('Dockerfile').toFile().text = 'FROM alpine:latest'
        testProjectDir.resolve('compose').toFile().mkdirs()
        testProjectDir.resolve('compose/app.yml').toFile().text = '''
services:
  app:
    image: alpine:latest
'''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyFullConfig', '--stacktrace')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('SUCCESS: Full DSL parsed correctly')
        result.task(':verifyFullConfig').outcome == TaskOutcome.SUCCESS
    }

    def "save block with all compression options parses correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myApp {
                        imageName = 'test-app'
                        tags = ['latest']
                        context.set(file('.'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    myTest {
                        files.from('compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    gzipPipeline {
                        build { image = docker.images.myApp }
                        test { stack = dockerTest.composeStacks.myTest; testTaskName = 'integrationTest' }
                        onTestSuccess {
                            save {
                                compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
                            }
                        }
                    }
                    bzip2Pipeline {
                        build { image = docker.images.myApp }
                        test { stack = dockerTest.composeStacks.myTest; testTaskName = 'integrationTest' }
                        onTestSuccess {
                            save {
                                compression.set(com.kineticfire.gradle.docker.model.SaveCompression.BZIP2)
                            }
                        }
                    }
                    nonePipeline {
                        build { image = docker.images.myApp }
                        test { stack = dockerTest.composeStacks.myTest; testTaskName = 'integrationTest' }
                        onTestSuccess {
                            save {
                                compression.set(com.kineticfire.gradle.docker.model.SaveCompression.NONE)
                            }
                        }
                    }
                }
            }

            tasks.register('verifyCompressionOptions') {
                doLast {
                    def gzip = dockerWorkflows.pipelines.getByName('gzipPipeline')
                        .onTestSuccess.get().save.get().compression.get()
                    def bzip2 = dockerWorkflows.pipelines.getByName('bzip2Pipeline')
                        .onTestSuccess.get().save.get().compression.get()
                    def none = dockerWorkflows.pipelines.getByName('nonePipeline')
                        .onTestSuccess.get().save.get().compression.get()

                    assert gzip.name() == 'GZIP'
                    assert bzip2.name() == 'BZIP2'
                    assert none.name() == 'NONE'
                    println "SUCCESS: All compression options parsed correctly"
                }
            }
        """

        // Create minimal required files
        testProjectDir.resolve('Dockerfile').toFile().text = 'FROM alpine:latest'
        testProjectDir.resolve('compose').toFile().mkdirs()
        testProjectDir.resolve('compose/app.yml').toFile().text = '''
services:
  app:
    image: alpine:latest
'''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyCompressionOptions', '--stacktrace')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('SUCCESS: All compression options parsed correctly')
        result.task(':verifyCompressionOptions').outcome == TaskOutcome.SUCCESS
    }

    def "publish block with multiple targets parses correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myApp {
                        imageName = 'test-app'
                        tags = ['latest']
                        context.set(file('.'))
                    }
                }
            }

            dockerTest {
                composeStacks {
                    myTest {
                        files.from('compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    multiTargetPipeline {
                        description = 'Pipeline publishing to multiple registries'

                        build {
                            image = docker.images.myApp
                        }

                        test {
                            stack = dockerTest.composeStacks.myTest
                            testTaskName = 'integrationTest'
                        }

                        onTestSuccess {
                            publish {
                                to('dockerhub') {
                                    registry.set('docker.io')
                                    namespace.set('myuser')
                                    publishTags.set(['v1.0'])
                                }
                                to('ghcr') {
                                    registry.set('ghcr.io')
                                    namespace.set('myorg')
                                    publishTags.set(['v1.0', 'latest'])
                                }
                                to('ecr') {
                                    registry.set('123456789.dkr.ecr.us-east-1.amazonaws.com')
                                    namespace.set('myrepo')
                                }
                            }
                        }
                    }
                }
            }

            tasks.register('verifyMultiTarget') {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('multiTargetPipeline')
                    def publishSpec = pipeline.onTestSuccess.get().publish.get()

                    assert publishSpec.to.size() == 3

                    def dockerhub = publishSpec.to.getByName('dockerhub')
                    assert dockerhub.registry.get() == 'docker.io'
                    assert dockerhub.namespace.get() == 'myuser'
                    assert dockerhub.publishTags.get() == ['v1.0']

                    def ghcr = publishSpec.to.getByName('ghcr')
                    assert ghcr.registry.get() == 'ghcr.io'
                    assert ghcr.namespace.get() == 'myorg'
                    assert ghcr.publishTags.get() == ['v1.0', 'latest']

                    def ecr = publishSpec.to.getByName('ecr')
                    assert ecr.registry.get() == '123456789.dkr.ecr.us-east-1.amazonaws.com'
                    assert ecr.namespace.get() == 'myrepo'

                    println "SUCCESS: Multiple publish targets parsed correctly"
                }
            }
        """

        // Create minimal required files
        testProjectDir.resolve('Dockerfile').toFile().text = 'FROM alpine:latest'
        testProjectDir.resolve('compose').toFile().mkdirs()
        testProjectDir.resolve('compose/app.yml').toFile().text = '''
services:
  app:
    image: alpine:latest
'''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyMultiTarget', '--stacktrace')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('SUCCESS: Multiple publish targets parsed correctly')
        result.task(':verifyMultiTarget').outcome == TaskOutcome.SUCCESS
    }
}
