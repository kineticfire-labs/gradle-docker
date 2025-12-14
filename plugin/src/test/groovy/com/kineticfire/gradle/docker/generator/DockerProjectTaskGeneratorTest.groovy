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
import com.kineticfire.gradle.docker.task.DockerSaveTask
import com.kineticfire.gradle.docker.task.TagOnSuccessTask
import org.gradle.api.Project
import org.gradle.api.provider.Provider
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
        extension.image {
            name.set('my-app')
            contextDir.set('src/main/docker')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runDockerProject') != null
    }

    def "generate creates build task when image configured"() {
        given:
        extension.image {
            name.set('myapp')  // Use simple name without hyphen for predictable task name
            contextDir.set('src/main/docker')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerBuildMyapp') != null
    }

    def "generate creates tag on success task"() {
        given:
        extension.image {
            name.set('test-image')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('customimage')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('myapp')
            tags.set(['1.0.0', 'latest'])
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('myapp')
            buildArgs.put('VERSION', '1.0.0')
            buildArgs.put('PROFILE', 'prod')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('myapp')
            registry.set('docker.io')
            namespace.set('myorg')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('test-app')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('my-app')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('my-app')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('my-app')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('myapp')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('my-app')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('my-app')
            contextDir.set('src/main/docker')
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
        extension.image {
            name.set('my-app')
            contextDir.set('src/main/docker')
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
        extension.image {
            imageName.set('customname')
            contextDir.set('src/main/docker')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('dockerBuildCustomname') != null
    }

    def "uses sourceRefImageName when in source ref mode"() {
        given:
        extension.image {
            sourceRefImageName.set('nginx')
            sourceRef.set('nginx:latest')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // In source ref mode, we still generate lifecycle task
        project.tasks.findByName('runDockerProject') != null
    }
}
