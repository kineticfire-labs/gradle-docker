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

package com.kineticfire.gradle.docker.workflow.executor

import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.PublishSpec
import com.kineticfire.gradle.docker.spec.SaveSpec
import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
import com.kineticfire.gradle.docker.workflow.operation.SaveOperationExecutor
import com.kineticfire.gradle.docker.workflow.operation.TagOperationExecutor
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for SuccessStepExecutor
 */
class SuccessStepExecutorTest extends Specification {

    Project project
    SuccessStepExecutor executor
    TagOperationExecutor tagOperationExecutor
    SaveOperationExecutor saveOperationExecutor
    PublishOperationExecutor publishOperationExecutor
    DockerService dockerService
    SuccessStepSpec successSpec
    PipelineContext context
    ImageSpec imageSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        tagOperationExecutor = Mock(TagOperationExecutor)
        saveOperationExecutor = Mock(SaveOperationExecutor)
        publishOperationExecutor = Mock(PublishOperationExecutor)
        executor = new SuccessStepExecutor(tagOperationExecutor, saveOperationExecutor, publishOperationExecutor)
        dockerService = Mock(DockerService)
        executor.setDockerService(dockerService)

        successSpec = project.objects.newInstance(SuccessStepSpec)

        imageSpec = project.objects.newInstance(
            ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])

        context = PipelineContext.create('testPipeline').withBuiltImage(imageSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "default constructor creates all operation executors"() {
        when:
        def exec = new SuccessStepExecutor()

        then:
        exec != null
    }

    def "constructor accepts TagOperationExecutor"() {
        given:
        def mockTagExecutor = Mock(TagOperationExecutor)

        when:
        def exec = new SuccessStepExecutor(mockTagExecutor)

        then:
        exec != null
    }

    def "constructor accepts TagOperationExecutor and SaveOperationExecutor"() {
        given:
        def mockTagExecutor = Mock(TagOperationExecutor)
        def mockSaveExecutor = Mock(SaveOperationExecutor)

        when:
        def exec = new SuccessStepExecutor(mockTagExecutor, mockSaveExecutor)

        then:
        exec != null
    }

    def "constructor accepts all three operation executors"() {
        given:
        def mockTagExecutor = Mock(TagOperationExecutor)
        def mockSaveExecutor = Mock(SaveOperationExecutor)
        def mockPublishExecutor = Mock(PublishOperationExecutor)

        when:
        def exec = new SuccessStepExecutor(mockTagExecutor, mockSaveExecutor, mockPublishExecutor)

        then:
        exec != null
    }

    // ===== EXECUTE TESTS =====

    def "execute returns context unchanged when successSpec is null"() {
        when:
        def result = executor.execute(null, context)

        then:
        result == context
    }

    def "execute completes without error for empty successSpec"() {
        when:
        def result = executor.execute(successSpec, context)

        then:
        result != null
        result.pipelineName == 'testPipeline'
    }

    def "execute preserves existing context data"() {
        given:
        def enrichedContext = context.withMetadata('key', 'value')

        when:
        def result = executor.execute(successSpec, enrichedContext)

        then:
        result.getMetadataValue('key') == 'value'
    }

    // ===== APPLY ADDITIONAL TAGS TESTS =====

    def "applyAdditionalTags returns context unchanged when no tags configured"() {
        when:
        def result = executor.applyAdditionalTags(successSpec, context)

        then:
        result.appliedTags.isEmpty()
    }

    def "applyAdditionalTags returns context unchanged when tags list is empty"() {
        given:
        successSpec.additionalTags.set([])

        when:
        def result = executor.applyAdditionalTags(successSpec, context)

        then:
        result.appliedTags.isEmpty()
    }

    def "applyAdditionalTags adds tags to context"() {
        given:
        successSpec.additionalTags.set(['stable', 'production'])

        when:
        def result = executor.applyAdditionalTags(successSpec, context)

        then:
        result.appliedTags.containsAll(['stable', 'production'])
    }

    def "applyAdditionalTags throws when no built image"() {
        given:
        successSpec.additionalTags.set(['stable'])
        def noImageContext = PipelineContext.create('test')

        when:
        executor.applyAdditionalTags(successSpec, noImageContext)

        then:
        def e = thrown(GradleException)
        e.message.contains('no built image')
    }

    def "applyAdditionalTags calls TagOperationExecutor"() {
        given:
        successSpec.additionalTags.set(['stable', 'production'])

        when:
        executor.applyAdditionalTags(successSpec, context)

        then:
        1 * tagOperationExecutor.execute(imageSpec, ['stable', 'production'], dockerService)
    }

    def "applyAdditionalTags works without dockerService"() {
        given:
        executor.setDockerService(null)
        successSpec.additionalTags.set(['stable'])

        when:
        def result = executor.applyAdditionalTags(successSpec, context)

        then:
        result.appliedTags.contains('stable')
        0 * tagOperationExecutor.execute(_, _, _)
    }

    // ===== HAS ADDITIONAL TAGS TESTS =====

    def "hasAdditionalTags returns false when not set"() {
        expect:
        !executor.hasAdditionalTags(successSpec)
    }

    def "hasAdditionalTags returns false when empty"() {
        given:
        successSpec.additionalTags.set([])

        expect:
        !executor.hasAdditionalTags(successSpec)
    }

    def "hasAdditionalTags returns true when tags present"() {
        given:
        successSpec.additionalTags.set(['stable'])

        expect:
        executor.hasAdditionalTags(successSpec)
    }

    def "hasAdditionalTags returns true for multiple tags"() {
        given:
        successSpec.additionalTags.set(['stable', 'production', 'v1.0'])

        expect:
        executor.hasAdditionalTags(successSpec)
    }

    // ===== EXECUTE SAVE OPERATION TESTS =====

    def "executeSaveOperation does nothing when save not configured"() {
        when:
        executor.executeSaveOperation(successSpec, context)

        then:
        noExceptionThrown()
        0 * saveOperationExecutor.execute(_, _, _)
    }

    def "executeSaveOperation calls SaveOperationExecutor when configured"() {
        given:
        def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        successSpec.save.set(saveSpec)

        when:
        executor.executeSaveOperation(successSpec, context)

        then:
        1 * saveOperationExecutor.execute(saveSpec, imageSpec, dockerService)
    }

    def "executeSaveOperation throws when no built image"() {
        given:
        def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        successSpec.save.set(saveSpec)
        def noImageContext = PipelineContext.create('test')

        when:
        executor.executeSaveOperation(successSpec, noImageContext)

        then:
        def e = thrown(GradleException)
        e.message.contains('no built image')
    }

    def "executeSaveOperation works without dockerService"() {
        given:
        executor.setDockerService(null)
        def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        successSpec.save.set(saveSpec)

        when:
        executor.executeSaveOperation(successSpec, context)

        then:
        noExceptionThrown()
        0 * saveOperationExecutor.execute(_, _, _)
    }

    // ===== HAS SAVE CONFIGURED TESTS =====

    def "hasSaveConfigured returns false when save not set"() {
        expect:
        !executor.hasSaveConfigured(successSpec)
    }

    def "hasSaveConfigured returns true when save is set"() {
        given:
        def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        successSpec.save.set(saveSpec)

        expect:
        executor.hasSaveConfigured(successSpec)
    }

    // ===== EXECUTE PUBLISH OPERATION TESTS =====

    def "executePublishOperation does nothing when publish not configured"() {
        when:
        executor.executePublishOperation(successSpec, context)

        then:
        noExceptionThrown()
        0 * publishOperationExecutor.execute(_, _, _)
    }

    def "executePublishOperation calls PublishOperationExecutor when configured"() {
        given:
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        successSpec.publish.set(publishSpec)

        when:
        executor.executePublishOperation(successSpec, context)

        then:
        1 * publishOperationExecutor.execute(publishSpec, imageSpec, dockerService)
    }

    def "executePublishOperation throws when no built image"() {
        given:
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        successSpec.publish.set(publishSpec)
        def noImageContext = PipelineContext.create('test')

        when:
        executor.executePublishOperation(successSpec, noImageContext)

        then:
        def e = thrown(GradleException)
        e.message.contains('no built image')
    }

    def "executePublishOperation works without dockerService"() {
        given:
        executor.setDockerService(null)
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        successSpec.publish.set(publishSpec)

        when:
        executor.executePublishOperation(successSpec, context)

        then:
        noExceptionThrown()
        0 * publishOperationExecutor.execute(_, _, _)
    }

    // ===== HAS PUBLISH CONFIGURED TESTS =====

    def "hasPublishConfigured returns false when publish not set"() {
        expect:
        !executor.hasPublishConfigured(successSpec)
    }

    def "hasPublishConfigured returns true when publish is set"() {
        given:
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        successSpec.publish.set(publishSpec)

        expect:
        executor.hasPublishConfigured(successSpec)
    }

    // ===== EXECUTE AFTER SUCCESS HOOK TESTS =====

    def "executeAfterSuccessHook does nothing when hook not configured"() {
        when:
        executor.executeAfterSuccessHook(successSpec)

        then:
        noExceptionThrown()
    }

    def "executeAfterSuccessHook executes hook when configured"() {
        given:
        def hookCalled = false
        def hook = { hookCalled = true } as Action<Void>
        successSpec.afterSuccess.set(hook)

        when:
        executor.executeAfterSuccessHook(successSpec)

        then:
        hookCalled
    }

    // ===== EXECUTE HOOK TESTS =====

    def "executeHook executes action with null parameter"() {
        given:
        def receivedParam = 'not-null'
        def hook = { param -> receivedParam = param } as Action<Void>

        when:
        executor.executeHook(hook)

        then:
        receivedParam == null
    }

    // ===== FULL EXECUTION FLOW TESTS =====

    def "execute applies tags and runs hook"() {
        given:
        successSpec.additionalTags.set(['stable'])
        def hookCalled = false
        successSpec.afterSuccess.set({ hookCalled = true } as Action<Void>)

        when:
        def result = executor.execute(successSpec, context)

        then:
        result.appliedTags.contains('stable')
        hookCalled
    }

    def "execute maintains pipeline name through execution"() {
        given:
        successSpec.additionalTags.set(['stable'])

        when:
        def result = executor.execute(successSpec, context)

        then:
        result.pipelineName == 'testPipeline'
    }

    def "execute accumulates tags from multiple calls"() {
        given:
        successSpec.additionalTags.set(['stable', 'production'])
        def preTaggedContext = context.withAppliedTag('initial')

        when:
        def result = executor.execute(successSpec, preTaggedContext)

        then:
        result.appliedTags.containsAll(['initial', 'stable', 'production'])
    }
}
