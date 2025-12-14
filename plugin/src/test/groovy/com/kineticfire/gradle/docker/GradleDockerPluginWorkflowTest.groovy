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

import com.kineticfire.gradle.docker.extension.DockerWorkflowsExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for GradleDockerPlugin workflow task registration.
 *
 * These tests verify that WorkflowTaskGenerator creates the expected task graph
 * for pipeline configurations. The tasks are now generated as a static task graph
 * instead of using PipelineRunTask for dynamic execution.
 */
class GradleDockerPluginWorkflowTest extends Specification {

    @TempDir
    Path tempDir

    Project project

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        // Apply the plugin
        project.plugins.apply('com.kineticfire.gradle.docker')
    }

    // ===== EXTENSION REGISTRATION TESTS =====

    def "plugin registers dockerWorkflows extension"() {
        expect:
        project.extensions.findByName('dockerWorkflows') != null
        project.extensions.findByType(DockerWorkflowsExtension) != null
    }

    def "dockerWorkflows extension has pipelines container"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)

        expect:
        extension.pipelines != null
        extension.pipelines.isEmpty()
    }

    // ===== AGGREGATE TASK REGISTRATION TESTS =====

    def "plugin registers runPipelines aggregate task after evaluation with pipelines"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            testPipeline { }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('runPipelines') != null
    }

    def "runPipelines task has correct group"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            testPipeline { }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('runPipelines')
        task.group == 'docker workflows'
    }

    def "runPipelines task has description"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            testPipeline { }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('runPipelines')
        task.description != null
        task.description.contains('pipelines')
    }

    // ===== PIPELINE LIFECYCLE TASK REGISTRATION TESTS =====

    def "plugin registers pipeline lifecycle task after evaluation"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            ciPipeline {
                description.set('CI Pipeline')
            }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('runCiPipeline') != null
    }

    def "pipeline lifecycle task is a Task"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            testPipeline { }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('runTestPipeline') instanceof Task
    }

    def "pipeline lifecycle task has correct group"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            myPipeline { }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('runMyPipeline')
        task.group == 'docker workflows'
    }

    def "pipeline lifecycle task has description"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            descPipeline { }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('runDescPipeline')
        task.description != null
        task.description.contains('descPipeline')
    }

    // ===== MULTIPLE PIPELINES TESTS =====

    def "plugin registers lifecycle tasks for multiple pipelines"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            pipeline1 { }
            pipeline2 { }
            pipeline3 { }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('runPipeline1') != null
        project.tasks.findByName('runPipeline2') != null
        project.tasks.findByName('runPipeline3') != null
    }

    def "aggregate task depends on all pipeline lifecycle tasks"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            first { }
            second { }
        }

        when:
        project.evaluate()

        then:
        def aggregateTask = project.tasks.findByName('runPipelines')
        aggregateTask.dependsOn.find { it.toString().contains('runFirst') }
        aggregateTask.dependsOn.find { it.toString().contains('runSecond') }
    }

    // ===== NAMING CONVENTION TESTS =====

    def "pipeline lifecycle task name follows run{PipelineName} convention"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            myTest { }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('runMyTest') != null
    }

    def "pipeline lifecycle task capitalizes first letter of pipeline name"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            lowercase { }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('runLowercase') != null
    }

    def "pipeline lifecycle task handles already capitalized names"() {
        given:
        def extension = project.extensions.findByType(DockerWorkflowsExtension)
        extension.pipelines {
            AlreadyCapitalized { }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('runAlreadyCapitalized') != null
    }

    // ===== NO PIPELINES TESTS =====

    def "runPipelines task exists even when no pipelines configured"() {
        when:
        project.evaluate()

        then:
        // Aggregate task always exists
        project.tasks.findByName('runPipelines') != null
        // No individual pipeline tasks
        project.tasks.findAll { it.name.startsWith('run') && it.name != 'runPipelines' }.isEmpty()
    }
}
