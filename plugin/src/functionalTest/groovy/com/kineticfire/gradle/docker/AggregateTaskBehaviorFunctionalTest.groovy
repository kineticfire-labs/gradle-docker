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
 * Functional tests for aggregate task behavior.
 *
 * Tests verify that the plugin creates aggregate tasks that properly coordinate and
 * depend on all related per-image or per-stack tasks:
 * - dockerBuild aggregates all per-image build tasks
 * - dockerTag aggregates all per-image tag tasks
 * - dockerSave aggregates all per-image save tasks
 * - dockerPublish aggregates all per-image publish tasks
 * - dockerImages aggregates all per-image dockerImage tasks
 * - composeUp aggregates all per-stack compose up tasks
 * - composeDown aggregates all per-stack compose down tasks
 * - Empty aggregates behave correctly when no images/stacks are configured
 */
class AggregateTaskBehaviorFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'aggregate-task-test'\n"
    }

    // ==================== dockerBuild Aggregate ====================

    def "dockerBuild aggregates all build tasks"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    alpineImage {
                        imageName.set('my-alpine')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                    ubuntuImage {
                        imageName.set('my-ubuntu')
                        tags.set(['1.0'])
                        context.set(file('.'))
                    }
                    debianImage {
                        imageName.set('my-debian')
                        tags.set(['stable'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyDockerBuildAggregate {
                doLast {
                    // Verify dockerBuild aggregate task exists
                    def dockerBuildTask = tasks.findByName('dockerBuild')
                    assert dockerBuildTask != null

                    // Verify individual build tasks exist
                    def alpineBuild = tasks.findByName('dockerBuildAlpineImage')
                    def ubuntuBuild = tasks.findByName('dockerBuildUbuntuImage')
                    def debianBuild = tasks.findByName('dockerBuildDebianImage')

                    assert alpineBuild != null
                    assert ubuntuBuild != null
                    assert debianBuild != null

                    // Verify dockerBuild depends on all individual build tasks
                    def dependencies = dockerBuildTask.taskDependencies.getDependencies(dockerBuildTask)
                    assert dependencies.contains(alpineBuild)
                    assert dependencies.contains(ubuntuBuild)
                    assert dependencies.contains(debianBuild)

                    println "dockerBuild aggregates all build tasks: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDockerBuildAggregate')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyDockerBuildAggregate').outcome == TaskOutcome.SUCCESS
        result.output.contains('dockerBuild aggregates all build tasks: verified')
    }

    // ==================== dockerTag Aggregate ====================

    def "dockerTag aggregates all tag tasks"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    webapp {
                        imageName.set('web-app')
                        tags.set(['latest', '1.0', '1.0.0'])
                        context.set(file('.'))
                    }
                    api {
                        imageName.set('api-service')
                        tags.set(['dev', 'staging'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyDockerTagAggregate {
                doLast {
                    def dockerTagTask = tasks.findByName('dockerTag')
                    assert dockerTagTask != null

                    def webappTag = tasks.findByName('dockerTagWebapp')
                    def apiTag = tasks.findByName('dockerTagApi')

                    assert webappTag != null
                    assert apiTag != null

                    // Verify dockerTag depends on all tag tasks
                    def dependencies = dockerTagTask.taskDependencies.getDependencies(dockerTagTask)
                    assert dependencies.contains(webappTag)
                    assert dependencies.contains(apiTag)

                    println "dockerTag aggregates all tag tasks: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDockerTagAggregate')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyDockerTagAggregate').outcome == TaskOutcome.SUCCESS
        result.output.contains('dockerTag aggregates all tag tasks: verified')
    }

    // ==================== dockerSave Aggregate ====================

    def "dockerSave aggregates all save tasks when configured"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    alpine {
                        imageName.set('alpine-img')
                        tags.set(['latest'])
                        context.set(file('.'))
                        save {
                            outputFile.set(file('build/alpine.tar'))
                        }
                    }
                    nginx {
                        imageName.set('nginx-img')
                        tags.set(['1.0'])
                        context.set(file('.'))
                        save {
                            outputFile.set(file('build/nginx.tar'))
                        }
                    }
                }
            }

            task verifyDockerSaveAggregate {
                doLast {
                    def dockerSaveTask = tasks.findByName('dockerSave')
                    assert dockerSaveTask != null

                    def alpineSave = tasks.findByName('dockerSaveAlpine')
                    def nginxSave = tasks.findByName('dockerSaveNginx')

                    assert alpineSave != null
                    assert nginxSave != null

                    // Verify dockerSave depends on all save tasks
                    def dependencies = dockerSaveTask.taskDependencies.getDependencies(dockerSaveTask)
                    assert dependencies.contains(alpineSave)
                    assert dependencies.contains(nginxSave)

                    println "dockerSave aggregates all save tasks: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDockerSaveAggregate')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyDockerSaveAggregate').outcome == TaskOutcome.SUCCESS
        result.output.contains('dockerSave aggregates all save tasks: verified')
    }

    // ==================== dockerPublish Aggregate ====================

    def "dockerPublish aggregate task exists"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyDockerPublishAggregate {
                doLast {
                    // Verify dockerPublish aggregate task exists
                    def dockerPublishTask = tasks.findByName('dockerPublish')
                    assert dockerPublishTask != null

                    println "dockerPublish aggregate task exists: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDockerPublishAggregate')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyDockerPublishAggregate').outcome == TaskOutcome.SUCCESS
        result.output.contains('dockerPublish aggregate task exists: verified')
    }

    // ==================== dockerImages Aggregate ====================

    def "dockerImages aggregates all per-image dockerImage tasks"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    imageOne {
                        imageName.set('image-one')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                    imageTwo {
                        imageName.set('image-two')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                    imageThree {
                        imageName.set('image-three')
                        tags.set(['stable'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyDockerImagesAggregate {
                doLast {
                    def dockerImagesTask = tasks.findByName('dockerImages')
                    assert dockerImagesTask != null

                    // Per-image aggregate tasks
                    def imageOne = tasks.findByName('dockerImageImageOne')
                    def imageTwo = tasks.findByName('dockerImageImageTwo')
                    def imageThree = tasks.findByName('dockerImageImageThree')

                    assert imageOne != null
                    assert imageTwo != null
                    assert imageThree != null

                    // Verify dockerImages depends on all per-image dockerImage tasks
                    def dependencies = dockerImagesTask.taskDependencies.getDependencies(dockerImagesTask)
                    assert dependencies.contains(imageOne)
                    assert dependencies.contains(imageTwo)
                    assert dependencies.contains(imageThree)

                    println "dockerImages aggregates all per-image tasks: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDockerImagesAggregate')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyDockerImagesAggregate').outcome == TaskOutcome.SUCCESS
        result.output.contains('dockerImages aggregates all per-image tasks: verified')
    }

    // ==================== composeUp Aggregate ====================

    def "composeUp aggregates all compose stack up tasks"() {
        given:
        // Create compose files
        def composeDir = testProjectDir.resolve('compose').toFile()
        composeDir.mkdirs()

        new File(composeDir, 'stack-a.yml').text = """
            services:
              web:
                image: nginx:latest
        """

        new File(composeDir, 'stack-b.yml').text = """
            services:
              db:
                image: postgres:latest
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    stackA {
                        files.from('compose/stack-a.yml')
                    }
                    stackB {
                        files.from('compose/stack-b.yml')
                    }
                }
            }

            task verifyComposeUpAggregate {
                doLast {
                    def composeUpTask = tasks.findByName('composeUp')
                    assert composeUpTask != null

                    def stackAUp = tasks.findByName('composeUpStackA')
                    def stackBUp = tasks.findByName('composeUpStackB')

                    assert stackAUp != null
                    assert stackBUp != null

                    // Verify composeUp depends on all stack up tasks
                    def dependencies = composeUpTask.taskDependencies.getDependencies(composeUpTask)
                    assert dependencies.contains(stackAUp)
                    assert dependencies.contains(stackBUp)

                    println "composeUp aggregates all stack up tasks: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyComposeUpAggregate')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyComposeUpAggregate').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeUp aggregates all stack up tasks: verified')
    }

    // ==================== composeDown Aggregate ====================

    def "composeDown aggregates all compose stack down tasks"() {
        given:
        def composeDir = testProjectDir.resolve('compose').toFile()
        composeDir.mkdirs()

        new File(composeDir, 'services.yml').text = """
            services:
              redis:
                image: redis:latest
        """

        new File(composeDir, 'cache.yml').text = """
            services:
              memcached:
                image: memcached:latest
        """

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    services {
                        files.from('compose/services.yml')
                    }
                    cache {
                        files.from('compose/cache.yml')
                    }
                }
            }

            task verifyComposeDownAggregate {
                doLast {
                    def composeDownTask = tasks.findByName('composeDown')
                    assert composeDownTask != null

                    def servicesDown = tasks.findByName('composeDownServices')
                    def cacheDown = tasks.findByName('composeDownCache')

                    assert servicesDown != null
                    assert cacheDown != null

                    // Verify composeDown depends on all stack down tasks
                    def dependencies = composeDownTask.taskDependencies.getDependencies(composeDownTask)
                    assert dependencies.contains(servicesDown)
                    assert dependencies.contains(cacheDown)

                    println "composeDown aggregates all stack down tasks: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyComposeDownAggregate')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyComposeDownAggregate').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeDown aggregates all stack down tasks: verified')
    }

    // ==================== Empty Aggregates ====================

    def "empty dockerBuild aggregate when no images configured"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            // No docker images configured

            task verifyEmptyDockerBuild {
                doLast {
                    def dockerBuildTask = tasks.findByName('dockerBuild')
                    assert dockerBuildTask != null

                    // Should have no dependencies since no images
                    def dependencies = dockerBuildTask.taskDependencies.getDependencies(dockerBuildTask)
                    assert dependencies.isEmpty()

                    println "Empty dockerBuild aggregate: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyEmptyDockerBuild')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyEmptyDockerBuild').outcome == TaskOutcome.SUCCESS
        result.output.contains('Empty dockerBuild aggregate: verified')
    }

    def "empty dockerImages aggregate when no images configured"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyEmptyDockerImages {
                doLast {
                    def dockerImagesTask = tasks.findByName('dockerImages')
                    assert dockerImagesTask != null

                    def dependencies = dockerImagesTask.taskDependencies.getDependencies(dockerImagesTask)
                    assert dependencies.isEmpty()

                    println "Empty dockerImages aggregate: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyEmptyDockerImages')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyEmptyDockerImages').outcome == TaskOutcome.SUCCESS
        result.output.contains('Empty dockerImages aggregate: verified')
    }

    def "empty composeUp aggregate when no stacks configured"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            // No compose stacks configured

            task verifyEmptyComposeUp {
                doLast {
                    def composeUpTask = tasks.findByName('composeUp')
                    assert composeUpTask != null

                    def dependencies = composeUpTask.taskDependencies.getDependencies(composeUpTask)
                    assert dependencies.isEmpty()

                    println "Empty composeUp aggregate: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyEmptyComposeUp')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyEmptyComposeUp').outcome == TaskOutcome.SUCCESS
        result.output.contains('Empty composeUp aggregate: verified')
    }

    def "empty composeDown aggregate when no stacks configured"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyEmptyComposeDown {
                doLast {
                    def composeDownTask = tasks.findByName('composeDown')
                    assert composeDownTask != null

                    def dependencies = composeDownTask.taskDependencies.getDependencies(composeDownTask)
                    assert dependencies.isEmpty()

                    println "Empty composeDown aggregate: verified"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyEmptyComposeDown')
            .forwardOutput()
            .build()

        then:
        result.task(':verifyEmptyComposeDown').outcome == TaskOutcome.SUCCESS
        result.output.contains('Empty composeDown aggregate: verified')
    }
}
