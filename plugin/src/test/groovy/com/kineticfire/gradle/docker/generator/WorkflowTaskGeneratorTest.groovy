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

import com.kineticfire.gradle.docker.extension.DockerWorkflowsExtension
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.task.CleanupTask
import com.kineticfire.gradle.docker.task.TagOnSuccessTask
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for WorkflowTaskGenerator
 */
class WorkflowTaskGeneratorTest extends Specification {

    Project project
    DockerWorkflowsExtension extension
    WorkflowTaskGenerator generator
    Provider<DockerService> dockerServiceProvider

    @TempDir
    Path tempDir

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.pluginManager.apply('java')

        extension = project.objects.newInstance(DockerWorkflowsExtension)
        generator = new WorkflowTaskGenerator()
        dockerServiceProvider = project.providers.provider { Mock(DockerService) }
    }

    // ===== BASIC GENERATION TESTS =====

    def "generate creates runPipelines even when no pipelines configured"() {
        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runPipelines') != null
    }

    def "generate creates runPipelines task when pipelines configured"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.description.set('CI pipeline')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runPipelines') != null
    }

    def "generate creates pipeline lifecycle task"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.description.set('CI pipeline')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runCi') != null
    }

    def "generate creates lifecycle task for each pipeline"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.description.set('CI pipeline')
        }
        extension.pipelines.create('release') { pipeline ->
            pipeline.description.set('Release pipeline')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runCi') != null
        project.tasks.findByName('runRelease') != null
    }

    // ===== TAG ON SUCCESS TASK TESTS =====

    def "generate creates tag on success task when additionalTags configured"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested', 'ci-passed'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('workflowCiTagOnSuccess') != null
    }

    def "tag on success task configured with additional tags"() {
        given:
        extension.pipelines.create('mypipeline') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['qa-approved', 'stable'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def tagTask = project.tasks.findByName('workflowMypipelineTagOnSuccess') as TagOnSuccessTask
        tagTask != null
        tagTask.additionalTags.get() == ['qa-approved', 'stable']
    }

    def "generate skips tag on success task when no additional tags"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.description.set('CI pipeline')
            // No additional tags configured
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('workflowCiTagOnSuccess') == null
    }

    // ===== CLEANUP TASK TESTS =====

    def "generate creates cleanup task when always step configured"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.always {
                removeTestContainers.set(true)
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('workflowCiCleanup') != null
    }

    def "cleanup task configured with options"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.always {
                removeTestContainers.set(true)
                cleanupImages.set(false)
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def cleanupTask = project.tasks.findByName('workflowCiCleanup') as CleanupTask
        cleanupTask != null
        cleanupTask.removeContainers.get() == true
        cleanupTask.removeImages.get() == false
    }

    // ===== TASK NAMING TESTS =====

    def "task names follow naming convention"() {
        given:
        extension.pipelines.create('mypipeline') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
            pipeline.always {
                removeTestContainers.set(true)
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Lifecycle task
        project.tasks.findByName('runMypipeline') != null
        // Tag on success task
        project.tasks.findByName('workflowMypipelineTagOnSuccess') != null
        // Cleanup task
        project.tasks.findByName('workflowMypipelineCleanup') != null
    }

    // ===== RUNPIPELINES AGGREGATE TASK TESTS =====

    def "runPipelines depends on all pipeline lifecycle tasks"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['ci'])
            }
        }
        extension.pipelines.create('release') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['release'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def runPipelinesTask = project.tasks.findByName('runPipelines')
        runPipelinesTask != null
        runPipelinesTask.dependsOn.any { it.toString().contains('runCi') }
        runPipelinesTask.dependsOn.any { it.toString().contains('runRelease') }
    }

    // ===== TASK GROUP AND DESCRIPTION TESTS =====

    def "lifecycle task has correct group"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.description.set('CI pipeline')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def runCiTask = project.tasks.findByName('runCi')
        runCiTask.group == 'docker workflows'
    }

    def "lifecycle task has correct description"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.description.set('CI pipeline')
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def runCiTask = project.tasks.findByName('runCi')
        runCiTask.description.contains('ci')
    }

    // ===== ONSUCESS FALLBACK TESTS =====

    def "uses onSuccess when onTestSuccess not configured"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onSuccess {
                additionalTags.set(['success-tag'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def tagTask = project.tasks.findByName('workflowCiTagOnSuccess') as TagOnSuccessTask
        tagTask != null
        tagTask.additionalTags.get() == ['success-tag']
    }

    def "prefers onTestSuccess over onSuccess"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['test-success-tag'])
            }
            pipeline.onSuccess {
                additionalTags.set(['general-success-tag'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def tagTask = project.tasks.findByName('workflowCiTagOnSuccess') as TagOnSuccessTask
        tagTask != null
        tagTask.additionalTags.get() == ['test-success-tag']
    }

    // ===== MULTIPLE PIPELINES TESTS =====

    def "each pipeline gets its own set of tasks"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['ci-tag'])
            }
            pipeline.always {
                removeTestContainers.set(true)
            }
        }
        extension.pipelines.create('nightly') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['nightly-tag'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // CI pipeline tasks
        project.tasks.findByName('runCi') != null
        project.tasks.findByName('workflowCiTagOnSuccess') != null
        project.tasks.findByName('workflowCiCleanup') != null

        // Nightly pipeline tasks
        project.tasks.findByName('runNightly') != null
        project.tasks.findByName('workflowNightlyTagOnSuccess') != null
        // Cleanup task is created by default (removeTestContainers convention is true)
        project.tasks.findByName('workflowNightlyCleanup') != null
    }
}
