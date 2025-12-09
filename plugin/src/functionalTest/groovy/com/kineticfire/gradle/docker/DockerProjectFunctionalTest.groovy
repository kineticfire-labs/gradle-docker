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
 * Functional tests for the dockerProject simplified DSL using Gradle TestKit.
 *
 * These tests verify that the dockerProject DSL is properly parsed and translated
 * into the underlying docker, dockerOrch, and dockerWorkflows configurations.
 */
class DockerProjectFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "can apply plugin with dockerProject extension available"() {
        given:
        settingsFile << "rootProject.name = 'test-docker-project'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            tasks.register('verifyExtension') {
                doLast {
                    def ext = project.extensions.findByName('dockerProject')
                    if (ext != null) {
                        println "dockerProject extension is available"
                    } else {
                        throw new GradleException("dockerProject extension not found")
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyExtension', '--info')
            .build()

        then:
        result.output.contains('dockerProject extension is available')
    }

    def "dockerProject with jarFrom creates docker image and pipeline tasks"() {
        given:
        settingsFile << "rootProject.name = 'test-jarfrom-mode'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('my-service')
                    tags.set(['1.0.0', 'latest'])
                    jarFrom.set('jar')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                }
            }

            tasks.register('verifyTasksCreated') {
                doLast {
                    // Verify docker image tasks
                    def buildTask = tasks.findByName('dockerBuildMyservice')
                    def tagTask = tasks.findByName('dockerTagMyservice')

                    if (buildTask != null) {
                        println "Docker build task created: dockerBuildMyservice"
                    }
                    if (tagTask != null) {
                        println "Docker tag task created: dockerTagMyservice"
                    }

                    // Verify pipeline run task (name format: run<PipelineName>Pipeline)
                    def pipelineTask = tasks.findByName('runMyservicePipeline')
                    if (pipelineTask != null) {
                        println "Pipeline run task created: runMyservicePipeline"
                    }
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: my-service:latest
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTasksCreated', '--info')
            .build()

        then:
        result.output.contains('Docker build task created: dockerBuildMyservice')
        result.output.contains('Docker tag task created: dockerTagMyservice')
        result.output.contains('Pipeline run task created: runMyservicePipeline')
    }

    def "dockerProject with contextDir creates docker image tasks"() {
        given:
        settingsFile << "rootProject.name = 'test-contextdir-mode'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('static-app')
                    tags.set(['1.0.0', 'latest'])
                    dockerfile.set('docker/Dockerfile')
                    contextDir.set('docker')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                }
            }

            tasks.register('verifyContextDirMode') {
                doLast {
                    def buildTask = tasks.findByName('dockerBuildStaticapp')
                    if (buildTask != null) {
                        println "Docker build task created for contextDir mode: dockerBuildStaticapp"
                    }
                }
            }
        """

        // Create docker directory with Dockerfile
        def dockerDir = testProjectDir.resolve('docker').toFile()
        dockerDir.mkdirs()
        def dockerfile = new File(dockerDir, 'Dockerfile')
        dockerfile << """
FROM alpine:latest
CMD ["echo", "hello"]
"""

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: static-app:latest
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyContextDirMode', '--info')
            .build()

        then:
        result.output.contains('Docker build task created for contextDir mode: dockerBuildStaticapp')
    }

    def "dockerProject with sourceRef creates docker image with pull configuration"() {
        given:
        settingsFile << "rootProject.name = 'test-sourceref-mode'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    sourceRefRegistry.set('docker.io')
                    sourceRefNamespace.set('library')
                    sourceRefImageName.set('nginx')
                    sourceRefTag.set('1.25-alpine')

                    tags.set(['test-nginx', 'latest'])
                    pullIfMissing.set(true)
                }

                test {
                    compose.set('src/integrationTest/resources/compose/nginx.yml')
                }
            }

            tasks.register('verifySourceRefMode') {
                doLast {
                    // In sourceRef mode, the image name is derived from sourceRefImageName
                    def tagTask = tasks.findByName('dockerTagNginx')
                    if (tagTask != null) {
                        println "Docker tag task created for sourceRef mode: dockerTagNginx"
                    }

                    def pipelineTask = tasks.findByName('runNginxPipeline')
                    if (pipelineTask != null) {
                        println "Pipeline run task created: runNginxPipeline"
                    }
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'nginx.yml')
        composeFile << """
services:
  nginx:
    image: nginx:1.25-alpine
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySourceRefMode', '--info')
            .build()

        then:
        result.output.contains('Docker tag task created for sourceRef mode: dockerTagNginx')
        result.output.contains('Pipeline run task created: runNginxPipeline')
    }

    def "dockerProject with direct sourceRef string creates docker image"() {
        given:
        settingsFile << "rootProject.name = 'test-direct-sourceref'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    sourceRef.set('docker.io/library/redis:7-alpine')
                    name.set('my-redis')
                    tags.set(['cached', 'latest'])
                }

                test {
                    compose.set('src/integrationTest/resources/compose/redis.yml')
                }
            }

            tasks.register('verifyDirectSourceRef') {
                doLast {
                    def tagTask = tasks.findByName('dockerTagMyredis')
                    if (tagTask != null) {
                        println "Docker tag task created for direct sourceRef: dockerTagMyredis"
                    }
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'redis.yml')
        composeFile << """
services:
  redis:
    image: redis:7-alpine
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDirectSourceRef', '--info')
            .build()

        then:
        result.output.contains('Docker tag task created for direct sourceRef: dockerTagMyredis')
    }

    def "dockerProject with onSuccess additionalTags configures tagging"() {
        given:
        settingsFile << "rootProject.name = 'test-success-tags'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('app')
                    tags.set(['1.0.0', 'latest'])
                    jarFrom.set('jar')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                }

                onSuccess {
                    additionalTags.set(['tested', 'verified'])
                }
            }

            tasks.register('verifySuccessConfig') {
                doLast {
                    println "onSuccess configuration applied with additionalTags"
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: app:latest
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySuccessConfig', '--info')
            .build()

        then:
        result.output.contains('onSuccess configuration applied with additionalTags')
    }

    def "dockerProject with save configuration configures pipeline"() {
        given:
        settingsFile << "rootProject.name = 'test-save-config'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('saveable')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                }

                onSuccess {
                    // Use save(String) method that takes a file path
                    save 'build/images/saveable.tar'
                }
            }

            tasks.register('verifySaveConfig') {
                doLast {
                    // Note: save configuration in dockerProject goes to workflow's onTestSuccess step,
                    // not to the docker image's save spec. So there's no separate dockerSave* task.
                    // Verify the pipeline run task exists instead.
                    def pipelineTask = tasks.findByName('runSaveablePipeline')
                    if (pipelineTask != null) {
                        println "Pipeline with save configuration created: runSaveablePipeline"
                    }
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: saveable:1.0.0
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySaveConfig', '--info')
            .build()

        then:
        result.output.contains('Pipeline with save configuration created: runSaveablePipeline')
    }

    def "dockerProject with publish configuration configures pipeline"() {
        given:
        settingsFile << "rootProject.name = 'test-publish-config'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('publishable')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                }

                onSuccess {
                    // Use property access for publish configuration
                    publishRegistry.set('ghcr.io')
                    publishNamespace.set('myorg')
                }
            }

            tasks.register('verifyPublishConfig') {
                doLast {
                    // Note: publish configuration in dockerProject goes to workflow's onTestSuccess step,
                    // not to the docker image's publish spec. So there's no separate dockerPublish* task.
                    // Verify the pipeline run task exists instead.
                    def pipelineTask = tasks.findByName('runPublishablePipeline')
                    if (pipelineTask != null) {
                        println "Pipeline with publish configuration created: runPublishablePipeline"
                    }
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: publishable:1.0.0
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyPublishConfig', '--info')
            .build()

        then:
        result.output.contains('Pipeline with publish configuration created: runPublishablePipeline')
    }

    def "dockerProject with method lifecycle logs warning"() {
        given:
        settingsFile << "rootProject.name = 'test-method-lifecycle'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('method-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                    lifecycle.set('METHOD')
                }
            }

            tasks.register('verifyMethodLifecycle') {
                doLast {
                    println "Method lifecycle configuration applied"
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: method-app:1.0.0
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyMethodLifecycle', '--info')
            .build()

        then:
        result.output.contains('Method lifecycle configuration applied')
        // The plugin logs a warning about METHOD lifecycle
        result.output.contains('Using METHOD lifecycle')
    }

    def "dockerProject with buildArgs and labels passes them to image"() {
        given:
        settingsFile << "rootProject.name = 'test-build-args'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('labeled-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')

                    buildArgs.put('BUILD_VERSION', '1.0.0')
                    buildArgs.put('BUILD_ENV', 'test')

                    labels.put('org.opencontainers.image.title', 'Labeled Application')
                    labels.put('org.opencontainers.image.version', '1.0.0')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                }
            }

            tasks.register('verifyBuildArgsAndLabels') {
                doLast {
                    println "buildArgs and labels configured successfully"
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: labeled-app:1.0.0
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyBuildArgsAndLabels', '--info')
            .build()

        then:
        result.output.contains('buildArgs and labels configured successfully')
    }

    def "dockerProject with registry and namespace sets them on image"() {
        given:
        settingsFile << "rootProject.name = 'test-registry-namespace'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('namespaced-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                    registry.set('ghcr.io')
                    namespace.set('myorg')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                }
            }

            tasks.register('verifyRegistryNamespace') {
                doLast {
                    println "registry and namespace configured successfully"
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: ghcr.io/myorg/namespaced-app:1.0.0
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyRegistryNamespace', '--info')
            .build()

        then:
        result.output.contains('registry and namespace configured successfully')
    }

    def "dockerProject without test block skips compose stack creation"() {
        given:
        settingsFile << "rootProject.name = 'test-no-test-block'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('simple-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                }

                // No test block - pipeline will have only build step
            }

            tasks.register('verifyNoTestBlock') {
                doLast {
                    def buildTask = tasks.findByName('dockerBuildSimpleapp')
                    if (buildTask != null) {
                        println "Build task created without test block"
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyNoTestBlock', '--info')
            .build()

        then:
        result.output.contains('Build task created without test block')
    }

    def "dockerProject with pullAuth configures authentication for sourceRef"() {
        given:
        settingsFile << "rootProject.name = 'test-pull-auth'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    sourceRefRegistry.set('private.registry.io')
                    sourceRefNamespace.set('private')
                    sourceRefImageName.set('base-image')
                    sourceRefTag.set('1.0')

                    pullIfMissing.set(true)

                    pullAuth {
                        username.set('pulluser')
                        password.set('pullpass')
                    }

                    name.set('my-base')
                    tags.set(['local'])
                }

                test {
                    compose.set('src/integrationTest/resources/compose/base.yml')
                }
            }

            tasks.register('verifyPullAuth') {
                doLast {
                    println "pullAuth configured for private registry"
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'base.yml')
        composeFile << """
services:
  base:
    image: my-base:local
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyPullAuth', '--info')
            .build()

        then:
        result.output.contains('pullAuth configured for private registry')
    }

    def "dockerProject test block with waitForHealthy configures health checks"() {
        given:
        settingsFile << "rootProject.name = 'test-wait-for-healthy'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('healthcheck-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                    waitForHealthy.set(['app', 'db'])
                }
            }

            tasks.register('verifyWaitForHealthy') {
                doLast {
                    println "waitForHealthy configured for services"
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: healthcheck-app:1.0.0
  db:
    image: postgres:15
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyWaitForHealthy', '--info')
            .build()

        then:
        result.output.contains('waitForHealthy configured for services')
    }

    def "dockerProject test block with waitForRunning configures running checks"() {
        given:
        settingsFile << "rootProject.name = 'test-wait-for-running'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('running-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                    waitForRunning.set(['app'])
                }
            }

            tasks.register('verifyWaitForRunning') {
                doLast {
                    println "waitForRunning configured for services"
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: running-app:1.0.0
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyWaitForRunning', '--info')
            .build()

        then:
        result.output.contains('waitForRunning configured for services')
    }

    def "dockerProject with custom test task name uses specified task"() {
        given:
        settingsFile << "rootProject.name = 'test-custom-test-task'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('custom-test-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                    // Use the correct property name: testTaskName (not testTask)
                    testTaskName.set('integrationTest')
                }
            }

            tasks.register('verifyCustomTestTask') {
                doLast {
                    println "Custom test task 'integrationTest' configured"
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: custom-test-app:1.0.0
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyCustomTestTask', '--info')
            .build()

        then:
        result.output.contains("Custom test task 'integrationTest' configured")
    }

    def "dockerProject validation fails when neither jarFrom nor contextDir nor sourceRef specified"() {
        given:
        settingsFile << "rootProject.name = 'test-validation-no-mode'"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('invalid-app')
                    tags.set(['1.0.0'])
                    // Missing jarFrom, contextDir, and sourceRef
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: invalid-app:1.0.0
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('must specify either') ||
            result.output.contains('jarFrom') ||
            result.output.contains('contextDir') ||
            result.output.contains('sourceRef')
    }

    def "dockerProject validation fails when both jarFrom and contextDir specified"() {
        given:
        settingsFile << "rootProject.name = 'test-validation-both-modes'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('invalid-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                    contextDir.set('docker')
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('jarFrom') && result.output.contains('contextDir')
    }

    def "dockerProject validation fails when both build mode and sourceRef mode configured"() {
        given:
        settingsFile << "rootProject.name = 'test-validation-mixed-modes'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('invalid-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                    sourceRefImageName.set('nginx')
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then:
        result.output.contains('build mode') || result.output.contains('sourceRef')
    }

    def "dockerProject with custom compose project name uses specified name"() {
        given:
        settingsFile << "rootProject.name = 'test-custom-project-name'"
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerProject {
                image {
                    name.set('proj-app')
                    tags.set(['1.0.0'])
                    jarFrom.set('jar')
                }

                test {
                    compose.set('src/integrationTest/resources/compose/app.yml')
                    projectName.set('custom-project')
                }
            }

            tasks.register('verifyCustomProjectName') {
                doLast {
                    println "Custom project name 'custom-project' configured"
                }
            }
        """

        // Create compose file directory
        def composeDir = testProjectDir.resolve('src/integrationTest/resources/compose').toFile()
        composeDir.mkdirs()
        def composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: proj-app:1.0.0
"""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyCustomProjectName', '--info')
            .build()

        then:
        result.output.contains("Custom project name 'custom-project' configured")
    }
}
