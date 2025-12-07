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

package com.kineticfire.gradle.docker.spec.workflow

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for PipelineSpec
 */
class PipelineSpecTest extends Specification {

    def project
    def pipelineSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        pipelineSpec = project.objects.newInstance(PipelineSpec, 'testPipeline')
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes name correctly"() {
        expect:
        pipelineSpec.name == 'testPipeline'
    }

    def "constructor initializes description with empty string convention"() {
        expect:
        pipelineSpec.description.present
        pipelineSpec.description.get() == ""
    }

    def "constructor initializes build with convention"() {
        expect:
        pipelineSpec.build.present
        pipelineSpec.build.get() != null
        pipelineSpec.build.get() instanceof BuildStepSpec
    }

    def "constructor initializes test with convention"() {
        expect:
        pipelineSpec.test.present
        pipelineSpec.test.get() != null
        pipelineSpec.test.get() instanceof TestStepSpec
    }

    def "constructor initializes onTestSuccess with convention"() {
        expect:
        pipelineSpec.onTestSuccess.present
        pipelineSpec.onTestSuccess.get() != null
        pipelineSpec.onTestSuccess.get() instanceof SuccessStepSpec
    }

    def "constructor initializes onTestFailure with convention"() {
        expect:
        pipelineSpec.onTestFailure.present
        pipelineSpec.onTestFailure.get() != null
        pipelineSpec.onTestFailure.get() instanceof FailureStepSpec
    }

    def "constructor initializes onSuccess with convention"() {
        expect:
        pipelineSpec.onSuccess.present
        pipelineSpec.onSuccess.get() != null
        pipelineSpec.onSuccess.get() instanceof SuccessStepSpec
    }

    def "constructor initializes onFailure with convention"() {
        expect:
        pipelineSpec.onFailure.present
        pipelineSpec.onFailure.get() != null
        pipelineSpec.onFailure.get() instanceof FailureStepSpec
    }

    def "constructor initializes always with convention"() {
        expect:
        pipelineSpec.always.present
        pipelineSpec.always.get() != null
        pipelineSpec.always.get() instanceof AlwaysStepSpec
    }

    // ===== PROPERTY TESTS =====

    def "description property works correctly"() {
        when:
        pipelineSpec.description.set('My test pipeline')

        then:
        pipelineSpec.description.get() == 'My test pipeline'
    }

    // ===== DSL METHOD TESTS =====

    def "build action configures BuildStepSpec"() {
        when:
        pipelineSpec.build { buildStep ->
            // BuildStepSpec has an image property
        }

        then:
        pipelineSpec.build.present
    }

    def "test action configures TestStepSpec"() {
        when:
        pipelineSpec.test { testStep ->
            testStep.testTaskName.set('integrationTest')
        }

        then:
        pipelineSpec.test.present
        pipelineSpec.test.get().testTaskName.get() == 'integrationTest'
    }

    def "onTestSuccess action configures SuccessStepSpec"() {
        when:
        pipelineSpec.onTestSuccess { successStep ->
            successStep.additionalTags.set(['tested'])
        }

        then:
        pipelineSpec.onTestSuccess.present
        pipelineSpec.onTestSuccess.get().additionalTags.get() == ['tested']
    }

    def "onTestFailure action configures FailureStepSpec"() {
        when:
        pipelineSpec.onTestFailure { failureStep ->
            failureStep.additionalTags.set(['failed'])
        }

        then:
        pipelineSpec.onTestFailure.present
        pipelineSpec.onTestFailure.get().additionalTags.get() == ['failed']
    }

    def "onSuccess action configures SuccessStepSpec"() {
        when:
        pipelineSpec.onSuccess { successStep ->
            successStep.additionalTags.set(['success'])
        }

        then:
        pipelineSpec.onSuccess.present
        pipelineSpec.onSuccess.get().additionalTags.get() == ['success']
    }

    def "onFailure action configures FailureStepSpec"() {
        when:
        pipelineSpec.onFailure { failureStep ->
            failureStep.additionalTags.set(['failure'])
        }

        then:
        pipelineSpec.onFailure.present
        pipelineSpec.onFailure.get().additionalTags.get() == ['failure']
    }

    def "always action configures AlwaysStepSpec"() {
        when:
        pipelineSpec.always { alwaysStep ->
            // AlwaysStepSpec is available
        }

        then:
        pipelineSpec.always.present
    }

    // ===== INDEPENDENCE TESTS =====

    def "onTestSuccess and onSuccess are independent instances"() {
        when:
        pipelineSpec.onTestSuccess { successStep ->
            successStep.additionalTags.set(['test-success'])
        }
        pipelineSpec.onSuccess { successStep ->
            successStep.additionalTags.set(['build-success'])
        }

        then:
        pipelineSpec.onTestSuccess.get().additionalTags.get() == ['test-success']
        pipelineSpec.onSuccess.get().additionalTags.get() == ['build-success']
        pipelineSpec.onTestSuccess.get() != pipelineSpec.onSuccess.get()
    }

    def "onTestFailure and onFailure are independent instances"() {
        when:
        pipelineSpec.onTestFailure { failureStep ->
            failureStep.additionalTags.set(['test-failed'])
        }
        pipelineSpec.onFailure { failureStep ->
            failureStep.additionalTags.set(['build-failed'])
        }

        then:
        pipelineSpec.onTestFailure.get().additionalTags.get() == ['test-failed']
        pipelineSpec.onFailure.get().additionalTags.get() == ['build-failed']
        pipelineSpec.onTestFailure.get() != pipelineSpec.onFailure.get()
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete pipeline configuration with all steps"() {
        when:
        pipelineSpec.description.set('Complete pipeline')
        pipelineSpec.build { }
        pipelineSpec.test { testStep ->
            testStep.testTaskName.set('integrationTest')
        }
        pipelineSpec.onTestSuccess { successStep ->
            successStep.additionalTags.set(['passed'])
        }
        pipelineSpec.onTestFailure { failureStep ->
            failureStep.additionalTags.set(['failed'])
        }
        pipelineSpec.always { }

        then:
        pipelineSpec.description.get() == 'Complete pipeline'
        pipelineSpec.build.present
        pipelineSpec.test.get().testTaskName.get() == 'integrationTest'
        pipelineSpec.onTestSuccess.get().additionalTags.get() == ['passed']
        pipelineSpec.onTestFailure.get().additionalTags.get() == ['failed']
        pipelineSpec.always.present
    }

    def "pipeline with onSuccess and onFailure instead of test variants"() {
        when:
        pipelineSpec.description.set('Build-only pipeline')
        pipelineSpec.build { }
        pipelineSpec.onSuccess { successStep ->
            successStep.additionalTags.set(['built'])
        }
        pipelineSpec.onFailure { failureStep ->
            failureStep.additionalTags.set(['build-failed'])
        }

        then:
        pipelineSpec.description.get() == 'Build-only pipeline'
        pipelineSpec.build.present
        pipelineSpec.onSuccess.get().additionalTags.get() == ['built']
        pipelineSpec.onFailure.get().additionalTags.get() == ['build-failed']
    }

    // ===== EDGE CASES =====

    def "pipeline with empty description is valid"() {
        expect:
        pipelineSpec.description.get() == ""
    }

    def "multiple pipelines can be created with different names"() {
        when:
        def pipeline1 = project.objects.newInstance(PipelineSpec, 'pipeline1')
        def pipeline2 = project.objects.newInstance(PipelineSpec, 'pipeline2')

        then:
        pipeline1.name == 'pipeline1'
        pipeline2.name == 'pipeline2'
        pipeline1 != pipeline2
    }

    def "pipeline name with special characters"() {
        when:
        def pipeline = project.objects.newInstance(PipelineSpec, 'my-test-pipeline_v1')

        then:
        pipeline.name == 'my-test-pipeline_v1'
    }

    def "accessing onSuccess convention does not throw NPE"() {
        expect:
        // This should not throw NPE because we added the convention
        pipelineSpec.onSuccess.get() != null
        pipelineSpec.onSuccess.get().additionalTags.present
    }

    def "accessing onFailure convention does not throw NPE"() {
        expect:
        // This should not throw NPE because we added the convention
        pipelineSpec.onFailure.get() != null
        pipelineSpec.onFailure.get().additionalTags.present
    }
}
