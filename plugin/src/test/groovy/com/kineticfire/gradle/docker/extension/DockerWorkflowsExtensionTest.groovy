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

package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for DockerWorkflowsExtension
 */
class DockerWorkflowsExtensionTest extends Specification {

    Project project
    DockerWorkflowsExtension extension

    def setup() {
        project = ProjectBuilder.builder().build()
        extension = project.objects.newInstance(DockerWorkflowsExtension)
    }

    def "extension can be created"() {
        expect:
        extension != null
        extension.pipelines != null
    }

    def "pipelines container is initially empty"() {
        expect:
        extension.pipelines.size() == 0
    }

    def "can configure single pipeline"() {
        when:
        extension.pipelines {
            testPipeline {
                description.set('Test pipeline for validation')
            }
        }

        then:
        extension.pipelines.size() == 1
        extension.pipelines.getByName('testPipeline') != null

        and:
        PipelineSpec pipelineSpec = extension.pipelines.getByName('testPipeline')
        pipelineSpec.name == 'testPipeline'
        pipelineSpec.description.get() == 'Test pipeline for validation'
    }

    def "can configure multiple pipelines"() {
        when:
        extension.pipelines {
            devPipeline {
                description.set('Development pipeline')
            }
            stagingPipeline {
                description.set('Staging pipeline')
            }
            productionPipeline {
                description.set('Production pipeline')
            }
        }

        then:
        extension.pipelines.size() == 3
        extension.pipelines.getByName('devPipeline') != null
        extension.pipelines.getByName('stagingPipeline') != null
        extension.pipelines.getByName('productionPipeline') != null

        and:
        extension.pipelines.getByName('devPipeline').description.get() == 'Development pipeline'
        extension.pipelines.getByName('stagingPipeline').description.get() == 'Staging pipeline'
        extension.pipelines.getByName('productionPipeline').description.get() == 'Production pipeline'
    }

    def "pipeline names are unique"() {
        when:
        extension.pipelines {
            myPipeline {
                description.set('First definition')
            }
            myPipeline {
                description.set('Second definition')
            }
        }

        then:
        extension.pipelines.size() == 1
        extension.pipelines.getByName('myPipeline').description.get() == 'Second definition'
    }

    def "can use Action-based configuration"() {
        when:
        extension.pipelines({ pipelines ->
            pipelines.create('actionPipeline') { pipeline ->
                pipeline.description.set('Pipeline configured via Action')
            }
        })

        then:
        extension.pipelines.size() == 1
        extension.pipelines.getByName('actionPipeline').description.get() == 'Pipeline configured via Action'
    }

    def "description has convention value"() {
        when:
        extension.pipelines {
            pipelineWithDefaultDescription {
            }
        }

        then:
        PipelineSpec pipeline = extension.pipelines.getByName('pipelineWithDefaultDescription')
        pipeline.description.get() == ""
    }

    // ===== COVERAGE ENHANCEMENT TESTS =====

    def "can use Action-based pipelines configuration method"() {
        when:
        extension.pipelines(new org.gradle.api.Action<org.gradle.api.NamedDomainObjectContainer<PipelineSpec>>() {
            @Override
            void execute(org.gradle.api.NamedDomainObjectContainer<PipelineSpec> pipelines) {
                pipelines.create('actionPipeline') { pipeline ->
                    pipeline.description.set('Pipeline configured via Action method')
                }
            }
        })

        then:
        extension.pipelines.size() == 1
        extension.pipelines.getByName('actionPipeline').description.get() == 'Pipeline configured via Action method'
    }
}
