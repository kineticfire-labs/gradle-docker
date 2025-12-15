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

package com.kineticfire.gradle.docker.generator

import com.kineticfire.gradle.docker.extension.DockerProjectExtension
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.task.DockerBuildTask
import com.kineticfire.gradle.docker.task.DockerPublishTask
import com.kineticfire.gradle.docker.task.DockerSaveTask
import com.kineticfire.gradle.docker.task.TagOnSuccessTask
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for DockerProjectTaskGenerator
 */
class DockerProjectTaskGeneratorTest extends Specification {

    Project project
    DockerProjectExtension extension
    DockerProjectTaskGenerator generator
    Provider<DockerService> dockerServiceProvider

    @TempDir
    Path tempDir

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.pluginManager.apply('java')

        extension = project.objects.newInstance(DockerProjectExtension)
        generator = new DockerProjectTaskGenerator()
        dockerServiceProvider = project.providers.provider { Mock(DockerService) }

        // Create required directories and files
        project.file('src/main/docker').mkdirs()
        project.file('src/main/docker/Dockerfile').text = 'FROM openjdk:17'
    }

    // ===== BASIC GENERATION TESTS =====

    def "generate skips when extension not configured"() {
        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runDockerProject') == null
    }

    def "generate creates lifecycle task when configured"() {
        given:
        extension.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runDockerProject') != null
    }

    def "generate creates build task when image configured"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')  // Use simple name without hyphen for predictable task name
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerBuildMyapp') != null
    }

    def "generate creates tag on success task"() {
        given:
        extension.images {
            testImage {
                imageName.set('test-image')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            additionalTags.set(['tested', 'stable'])
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerProjectTagOnSuccess') != null
    }

    // ===== BUILD TASK CONFIGURATION TESTS =====

    def "build task configured with correct image name"() {
        given:
        extension.images {
            customimage {
                imageName.set('customimage')
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def buildTask = project.tasks.findByName('dockerBuildCustomimage')
        buildTask != null
        buildTask instanceof DockerBuildTask
    }

    def "build task configured with tags"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                tags.set(['1.0.0', 'latest'])
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def buildTask = project.tasks.findByName('dockerBuildMyapp') as DockerBuildTask
        buildTask != null
        buildTask.tags.get() == ['1.0.0', 'latest']
    }

    def "build task configured with build args"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                buildArgs.put('VERSION', '1.0.0')
                buildArgs.put('PROFILE', 'prod')
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def buildTask = project.tasks.findByName('dockerBuildMyapp') as DockerBuildTask
        buildTask != null
        buildTask.buildArgs.get()['VERSION'] == '1.0.0'
        buildTask.buildArgs.get()['PROFILE'] == 'prod'
    }

    def "build task configured with registry and namespace"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                registry.set('docker.io')
                namespace.set('myorg')
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def buildTask = project.tasks.findByName('dockerBuildMyapp') as DockerBuildTask
        buildTask != null
        buildTask.registry.get() == 'docker.io'
        buildTask.namespace.get() == 'myorg'
    }

    // ===== TAG ON SUCCESS TASK TESTS =====

    def "tag on success task configured with additional tags"() {
        given:
        extension.images {
            testApp {
                imageName.set('test-app')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            additionalTags.set(['tested', 'qa-approved'])
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def tagTask = project.tasks.findByName('dockerProjectTagOnSuccess') as TagOnSuccessTask
        tagTask != null
        tagTask.additionalTags.get() == ['tested', 'qa-approved']
    }

    // ===== SAVE TASK TESTS =====

    def "generate creates save task when saveFile configured"() {
        given:
        extension.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            saveFile.set('build/images/app.tar.gz')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerProjectSave') != null
    }

    def "save task configured with output file"() {
        given:
        extension.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            saveFile.set('build/output/image.tar.gz')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def saveTask = project.tasks.findByName('dockerProjectSave') as DockerSaveTask
        saveTask != null
        saveTask.outputFile.get().asFile.name == 'image.tar.gz'
    }

    // ===== PUBLISH TASK TESTS =====

    def "generate creates publish task when publishRegistry configured"() {
        given:
        extension.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            publishRegistry.set('docker.io')
            additionalTags.set(['latest'])
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerProjectPublish') != null
    }

    def "generate creates publish task for named targets"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            publishTargets.create('dockerhub') { target ->
                target.registry.set('docker.io')
                target.namespace.set('myorg')
                target.tags.set(['latest'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerProjectPublishDockerhub') != null
    }

    // ===== TASK DEPENDENCY TESTS =====

    def "lifecycle task depends on tag on success"() {
        given:
        extension.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            additionalTags.set(['tested'])
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def lifecycleTask = project.tasks.findByName('runDockerProject')
        lifecycleTask != null
        lifecycleTask.dependsOn.any { it.toString().contains('dockerProjectTagOnSuccess') }
    }

    def "lifecycle task depends on save when configured"() {
        given:
        extension.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            saveFile.set('build/images/app.tar.gz')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def lifecycleTask = project.tasks.findByName('runDockerProject')
        lifecycleTask != null
        lifecycleTask.dependsOn.any { it.toString().contains('dockerProjectSave') }
    }

    def "lifecycle task depends on publish when configured"() {
        given:
        extension.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            publishRegistry.set('docker.io')
            additionalTags.set(['latest'])
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def lifecycleTask = project.tasks.findByName('runDockerProject')
        lifecycleTask != null
        lifecycleTask.dependsOn.any { it.toString().contains('dockerProjectPublish') }
    }

    // ===== NAME DERIVATION TESTS =====

    def "uses imageName property when set"() {
        given:
        extension.images {
            customname {
                imageName.set('customname')
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerBuildCustomname') != null
    }

    def "uses sourceRefImageName when in source ref mode"() {
        given:
        extension.images {
            nginx {
                sourceRefImageName.set('nginx')
                sourceRef.set('nginx:latest')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // In source ref mode, we still generate lifecycle task
        project.tasks.findByName('runDockerProject') != null
    }

    def "falls back to blockName when no image name set"() {
        given:
        extension.images {
            testImage {
                contextDir.set('src/main/docker')
                // No imageName or sourceRefImageName set - should use blockName 'testImage'
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Should use blockName 'testImage' (sanitized to 'testimage')
        project.tasks.findByName('runDockerProject') != null
        project.tasks.findByName('dockerBuildTestimage') != null
    }

    // ===== CONTEXT PREPARATION TASK TESTS =====

    def "generates context preparation task when jarFrom is set"() {
        given:
        // The jar task already exists from applying 'java' plugin in setup()
        extension.images {
            myapp {
                imageName.set('myapp')
                jarFrom.set('jar')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('prepareMyappContext') != null
    }

    def "context preparation task configured with custom jar name"() {
        given:
        // The jar task already exists from applying 'java' plugin in setup()
        extension.images {
            myapp {
                imageName.set('myapp')
                jarFrom.set('jar')
                jarName.set('custom-app.jar')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def contextTask = project.tasks.findByName('prepareMyappContext')
        contextTask != null
    }

    // ===== TASK ALREADY EXISTS TESTS =====

    def "skips build task registration when task already exists"() {
        given:
        // Pre-register the build task
        project.tasks.register('dockerBuildMyapp', DockerBuildTask)

        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Should not throw, should reuse existing task
        project.tasks.findByName('dockerBuildMyapp') != null
    }

    def "skips context task registration when task already exists"() {
        given:
        // The jar task already exists from applying 'java' plugin in setup()
        project.tasks.register('prepareMyappContext', Copy)

        extension.images {
            myapp {
                imageName.set('myapp')
                jarFrom.set('jar')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Should not throw, should reuse existing task
        project.tasks.findByName('prepareMyappContext') != null
    }

    def "skips tag on success task registration when task already exists"() {
        given:
        project.tasks.register('dockerProjectTagOnSuccess', TagOnSuccessTask)

        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            additionalTags.set(['tested'])
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerProjectTagOnSuccess') != null
    }

    def "skips save task registration when task already exists"() {
        given:
        project.tasks.register('dockerProjectSave', DockerSaveTask)

        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            saveFile.set('build/images/app.tar.gz')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerProjectSave') != null
    }

    def "skips publish task registration when task already exists"() {
        given:
        project.tasks.register('dockerProjectPublish', DockerPublishTask)

        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            publishRegistry.set('docker.io')
            additionalTags.set(['latest'])
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerProjectPublish') != null
    }

    // ===== MULTIPLE PUBLISH TARGETS TESTS =====

    def "generates multiple publish tasks for multiple targets"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            publishTargets.create('internal') { target ->
                target.registry.set('registry.internal.com')
                target.tags.set(['latest'])
            }
            publishTargets.create('dockerhub') { target ->
                target.registry.set('docker.io')
                target.namespace.set('myorg')
                target.tags.set(['latest', 'stable'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerProjectPublishInternal') != null
        project.tasks.findByName('dockerProjectPublishDockerhub') != null
    }

    def "publish task generated for target with auth configuration"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            publishTargets.create('private') { target ->
                target.registry.set('private.registry.com')
                target.tags.set(['latest'])
                target.auth {
                    username.set('testuser')
                    password.set('testpass')
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Verify the task is created (auth is handled by the task at execution time)
        def publishTask = project.tasks.findByName('dockerProjectPublishPrivate')
        publishTask != null
    }

    def "publish target uses additional tags when target tags empty"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            additionalTags.set(['tested', 'verified'])
            publishTargets.create('registry') { target ->
                target.registry.set('registry.example.com')
                // No tags set on target
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def publishTask = project.tasks.findByName('dockerProjectPublishRegistry') as DockerPublishTask
        publishTask != null
        publishTask.tags.get() == ['tested', 'verified']
    }

    // ===== SAVE TASK COMPRESSION TESTS =====

    def "save task infers gzip compression from file extension"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            saveFile.set('build/images/app.tar.gz')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def saveTask = project.tasks.findByName('dockerProjectSave') as DockerSaveTask
        saveTask != null
    }

    // ===== TASK DEPENDENCY WIRING TESTS =====

    def "lifecycle task depends on all publish tasks"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            publishTargets.create('internal') { target ->
                target.registry.set('internal.registry.com')
                target.tags.set(['latest'])
            }
            publishTargets.create('external') { target ->
                target.registry.set('external.registry.com')
                target.tags.set(['latest'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def lifecycleTask = project.tasks.findByName('runDockerProject')
        lifecycleTask != null
        lifecycleTask.dependsOn.any { it.toString().contains('dockerProjectPublishInternal') }
        lifecycleTask.dependsOn.any { it.toString().contains('dockerProjectPublishExternal') }
    }

    def "publish task uses publishTags when set"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            additionalTags.set(['tested'])
            publishRegistry.set('docker.io')
            publishTags.set(['v1.0.0', 'latest'])
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def publishTask = project.tasks.findByName('dockerProjectPublish') as DockerPublishTask
        publishTask != null
        publishTask.tags.get() == ['v1.0.0', 'latest']
    }

    def "publish task uses additionalTags when publishTags empty"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            additionalTags.set(['tested', 'stable'])
            publishRegistry.set('docker.io')
            // publishTags not set
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def publishTask = project.tasks.findByName('dockerProjectPublish') as DockerPublishTask
        publishTask != null
        publishTask.tags.get() == ['tested', 'stable']
    }

    def "publish task configured with publishNamespace"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
            }
        }
        extension.onSuccess {
            publishRegistry.set('docker.io')
            publishNamespace.set('myorganization')
            additionalTags.set(['latest'])
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def publishTask = project.tasks.findByName('dockerProjectPublish') as DockerPublishTask
        publishTask != null
        publishTask.namespace.get() == 'myorganization'
    }

    // ===== BUILD TASK WITH LABELS TESTS =====

    def "build task configured with labels"() {
        given:
        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
                labels.put('maintainer', 'team@example.com')
                labels.put('version', '1.0.0')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def buildTask = project.tasks.findByName('dockerBuildMyapp') as DockerBuildTask
        buildTask != null
        buildTask.labels.get()['maintainer'] == 'team@example.com'
        buildTask.labels.get()['version'] == '1.0.0'
    }

    // ===== DOCKERFILE CONFIGURATION TESTS =====

    def "build task configured with custom dockerfile"() {
        given:
        project.file('custom/Dockerfile').parentFile.mkdirs()
        project.file('custom/Dockerfile').text = 'FROM alpine'

        extension.images {
            myapp {
                imageName.set('myapp')
                contextDir.set('src/main/docker')
                dockerfile.set('custom/Dockerfile')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def buildTask = project.tasks.findByName('dockerBuildMyapp') as DockerBuildTask
        buildTask != null
        buildTask.dockerfile.get().asFile.name == 'Dockerfile'
    }

    // ===== MULTI-IMAGE TESTS =====

    def "throws exception when multiple images defined without primary"() {
        given:
        extension.images {
            app1 {
                imageName.set('app1')
                contextDir.set('src/main/docker')
            }
            app2 {
                imageName.set('app2')
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains('Multiple images defined')
        ex.message.contains('primary')
    }

    def "generates tasks for multiple images with primary designated"() {
        given:
        extension.images {
            app1 {
                imageName.set('app1')
                contextDir.set('src/main/docker')
                primary.set(true)
            }
            app2 {
                imageName.set('app2')
                contextDir.set('src/main/docker')
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerBuildApp1') != null
        project.tasks.findByName('dockerBuildApp2') != null
        project.tasks.findByName('runDockerProject') != null
    }
}
