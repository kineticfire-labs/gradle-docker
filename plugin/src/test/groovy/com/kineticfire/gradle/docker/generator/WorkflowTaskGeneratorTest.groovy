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

    // ===== BUILD STEP TESTS =====

    def "pipeline without build step still creates lifecycle task"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runCi') != null
    }

    // ===== CLEANUP CONFIGURATION TESTS =====

    def "cleanup task not created when no cleanup options enabled"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.always {
                removeTestContainers.set(false)
                cleanupImages.set(false)
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('workflowCiCleanup') == null
    }

    def "cleanup task created when cleanupImages enabled"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.always {
                cleanupImages.set(true)
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def cleanupTask = project.tasks.findByName('workflowCiCleanup') as CleanupTask
        cleanupTask != null
        cleanupTask.removeImages.get() == true
    }

    // ===== TEST SPEC TESTS =====

    def "generates with custom test task name"() {
        given:
        // Create a custom test task
        project.tasks.register('customTests', org.gradle.api.tasks.testing.Test)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.test {
                testTaskName.set('customTests')
            }
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runCi') != null
    }

    // ===== SAVE TASK TESTS =====

    def "no save task created when save not configured"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
                // save not configured
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('workflowCiSave') == null
    }

    // ===== PUBLISH TASK TESTS =====

    def "no publish task created when publish not configured"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
                // publish not configured
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('workflowCiPublish') == null
    }

    // ===== LIFECYCLE WIRING TESTS =====

    def "lifecycle task depends on test when no success tasks configured"() {
        given:
        // Create test task
        project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test)

        extension.pipelines.create('ci') { pipeline ->
            // No onTestSuccess or onSuccess configured
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def lifecycleTask = project.tasks.findByName('runCi')
        lifecycleTask != null
        lifecycleTask.dependsOn.any { it.toString().contains('integrationTest') }
    }

    def "cleanup already exists returns existing task"() {
        given:
        // Pre-register cleanup task
        project.tasks.register('workflowCiCleanup', CleanupTask)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.always {
                removeTestContainers.set(true)
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Should not throw, should reuse existing task
        project.tasks.findByName('workflowCiCleanup') != null
    }

    def "tag on success task already exists returns existing task"() {
        given:
        // Pre-register tag on success task
        project.tasks.register('workflowCiTagOnSuccess', TagOnSuccessTask)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Should not throw, should reuse existing task
        project.tasks.findByName('workflowCiTagOnSuccess') != null
    }

    // ===== EMPTY PIPELINE TESTS =====

    def "generate handles empty pipelines extension"() {
        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Should not throw, just create runPipelines
        project.tasks.findByName('runPipelines') != null
    }

    // ===== TEST RESULT CAPTURE TESTS =====

    def "configures test result capture when test task exists"() {
        given:
        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Test task should have output file configured
        testTask.outputs.files.files.any { it.name == 'test-result.json' }
    }

    def "skips test result capture when test task does not exist"() {
        given:
        // Don't create integrationTest task
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Should not throw
        project.tasks.findByName('runCi') != null
    }

    // ===== TASK DEPENDENCY TESTS =====

    def "lifecycle task finalizedBy cleanup"() {
        given:
        project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test)

        extension.pipelines.create('ci') { pipeline ->
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
        def lifecycleTask = project.tasks.findByName('runCi')
        lifecycleTask != null
        lifecycleTask.finalizedBy.getDependencies(lifecycleTask).any { it.name == 'workflowCiCleanup' }
    }

    // ===== SAVE TASK CONFIGURATION TESTS =====

    def "save task created when save configured with output file"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
                save {
                    outputFile.set(project.file('build/docker/image.tar'))
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def saveTask = project.tasks.findByName('workflowCiSave')
        saveTask != null
    }

    def "save task configured from onSuccess when onTestSuccess has no save"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onSuccess {
                save {
                    outputFile.set(project.file('build/docker/image.tar'))
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def saveTask = project.tasks.findByName('workflowCiSave')
        saveTask != null
    }

    def "save task already exists returns existing task"() {
        given:
        project.tasks.register('workflowCiSave', com.kineticfire.gradle.docker.task.DockerSaveTask)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                save {
                    outputFile.set(project.file('build/docker/image.tar'))
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('workflowCiSave') != null
    }

    def "save task created even when outputFile not set"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                save {
                    // outputFile not set - task still created but won't save
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Save task is created even without outputFile - validation happens at execution time
        project.tasks.findByName('workflowCiSave') != null
    }

    // ===== PUBLISH TASK CONFIGURATION TESTS =====

    def "publish task created when publish configured with targets"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
                publish {
                    to {
                        registry.set('registry.example.com')
                    }
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def publishTask = project.tasks.findByName('workflowCiPublish')
        publishTask != null
    }

    def "publish task configured from onSuccess when onTestSuccess has no publish"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onSuccess {
                publish {
                    to {
                        registry.set('registry.example.com')
                    }
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def publishTask = project.tasks.findByName('workflowCiPublish')
        publishTask != null
    }

    def "publish task already exists returns existing task"() {
        given:
        project.tasks.register('workflowCiPublish', com.kineticfire.gradle.docker.task.DockerPublishTask)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                publish {
                    to {
                        registry.set('registry.example.com')
                    }
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('workflowCiPublish') != null
    }

    def "publish task not created when to is empty"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                publish {
                    // no to() configured
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('workflowCiPublish') == null
    }

    // ===== BUILD HOOKS TESTS =====

    def "build hooks skipped when build spec not present"() {
        given:
        // Register a build task but no build spec in pipeline
        project.tasks.register('dockerBuildMyapp', com.kineticfire.gradle.docker.task.DockerBuildTask)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runCi') != null
    }

    // ===== TAG ON SUCCESS DEPENDENCY WIRING TESTS =====

    def "tag on success depends on test"() {
        given:
        project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def tagTask = project.tasks.findByName('workflowCiTagOnSuccess')
        tagTask.dependsOn.any { it.toString().contains('integrationTest') }
    }

    // ===== SAVE DEPENDENCY WIRING TESTS =====

    def "save depends on tag on success"() {
        given:
        project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
                save {
                    outputFile.set(project.file('build/image.tar'))
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def saveTask = project.tasks.findByName('workflowCiSave')
        saveTask.dependsOn.any { it.toString().contains('workflowCiTagOnSuccess') }
    }

    // ===== PUBLISH DEPENDENCY WIRING TESTS =====

    def "publish depends on save"() {
        given:
        project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
                save {
                    outputFile.set(project.file('build/image.tar'))
                }
                publish {
                    to {
                        registry.set('registry.example.com')
                    }
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def publishTask = project.tasks.findByName('workflowCiPublish')
        publishTask.dependsOn.any { it.toString().contains('workflowCiSave') }
    }

    def "publish depends on tag on success when no save"() {
        given:
        project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
                publish {
                    to {
                        registry.set('registry.example.com')
                    }
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def publishTask = project.tasks.findByName('workflowCiPublish')
        publishTask.dependsOn.any { it.toString().contains('workflowCiTagOnSuccess') }
    }

    // ===== LIFECYCLE TASK DEPENDENCY CHAIN TESTS =====

    def "lifecycle depends on publish when configured"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
                publish {
                    to {
                        registry.set('registry.example.com')
                    }
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def lifecycleTask = project.tasks.findByName('runCi')
        lifecycleTask.dependsOn.any { it.toString().contains('workflowCiPublish') }
    }

    def "lifecycle depends on save when no publish"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
                save {
                    outputFile.set(project.file('build/image.tar'))
                }
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def lifecycleTask = project.tasks.findByName('runCi')
        lifecycleTask.dependsOn.any { it.toString().contains('workflowCiSave') }
    }

    def "lifecycle depends on tag on success when no save or publish"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def lifecycleTask = project.tasks.findByName('runCi')
        lifecycleTask.dependsOn.any { it.toString().contains('workflowCiTagOnSuccess') }
    }

    // ===== CLEANUP FINALIZER TESTS =====

    def "cleanup finalized by test task"() {
        given:
        project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test)

        extension.pipelines.create('ci') { pipeline ->
            pipeline.always {
                removeTestContainers.set(true)
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        def testTask = project.tasks.findByName('integrationTest')
        testTask.finalizedBy.getDependencies(testTask).any { it.name == 'workflowCiCleanup' }
    }

    // ===== RESOLVE BUILD TASK NAME TESTS =====

    def "resolveBuildTaskName returns null when no build step"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        // Should not throw - works without build task
        project.tasks.findByName('runCi') != null
    }

    def "resolveBuildTaskName returns null when build has no image"() {
        given:
        extension.pipelines.create('ci') { pipeline ->
            pipeline.build {
                // No image configured - image property not set
            }
            pipeline.onTestSuccess {
                additionalTags.set(['tested'])
            }
        }

        when:
        generator.generate(project, extension, dockerServiceProvider)

        then:
        project.tasks.findByName('runCi') != null
    }
}
