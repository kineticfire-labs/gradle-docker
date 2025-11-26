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

import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TestResult
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for ConditionalExecutor
 */
class ConditionalExecutorTest extends Specification {

    @TempDir
    Path tempDir

    Project project
    ConditionalExecutor executor

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        executor = new ConditionalExecutor()
    }

    // ===== EXECUTE CONDITIONAL TESTS =====

    def "executeConditional returns context unchanged when testResult is null"() {
        given:
        def context = PipelineContext.create('test')
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

        when:
        def result = executor.executeConditional(null, successSpec, failureSpec, context)

        then:
        result == context
    }

    def "executeConditional executes success path when tests pass"() {
        given:
        def context = PipelineContext.create('test')
        def testResult = TestResult.success(10, 10, 0)
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        successSpec.additionalTags.set(['success-tag'])
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

        when:
        def result = executor.executeConditional(testResult, successSpec, failureSpec, context)

        then:
        result.appliedTags.contains('success-tag')
    }

    def "executeConditional executes failure path when tests fail"() {
        given:
        def context = PipelineContext.create('test')
        def testResult = TestResult.failure(10, 8, 2, 0)
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        failureSpec.additionalTags.set(['failure-tag'])

        when:
        def result = executor.executeConditional(testResult, successSpec, failureSpec, context)

        then:
        result.appliedTags.contains('failure-tag')
    }

    // ===== EXECUTE SUCCESS PATH TESTS =====

    def "executeSuccessPath returns context unchanged when successSpec is null"() {
        given:
        def context = PipelineContext.create('test')

        when:
        def result = executor.executeSuccessPath(null, context)

        then:
        result == context
    }

    def "executeSuccessPath applies additional tags when configured"() {
        given:
        def context = PipelineContext.create('test')
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        successSpec.additionalTags.set(['latest', 'stable', 'v1.0'])

        when:
        def result = executor.executeSuccessPath(successSpec, context)

        then:
        result.appliedTags.containsAll(['latest', 'stable', 'v1.0'])
    }

    def "executeSuccessPath does not apply tags when list is empty"() {
        given:
        def context = PipelineContext.create('test')
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        successSpec.additionalTags.set([])

        when:
        def result = executor.executeSuccessPath(successSpec, context)

        then:
        result.appliedTags.isEmpty()
    }

    def "executeSuccessPath executes afterSuccess hook"() {
        given:
        def hookExecuted = false
        def context = PipelineContext.create('test')
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        successSpec.afterSuccess.set({ hookExecuted = true } as Action<Void>)

        when:
        executor.executeSuccessPath(successSpec, context)

        then:
        hookExecuted
    }

    // ===== EXECUTE FAILURE PATH TESTS =====

    def "executeFailurePath returns context unchanged when failureSpec is null"() {
        given:
        def context = PipelineContext.create('test')

        when:
        def result = executor.executeFailurePath(null, context)

        then:
        result == context
    }

    def "executeFailurePath applies additional tags when configured"() {
        given:
        def context = PipelineContext.create('test')
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        failureSpec.additionalTags.set(['failed', 'broken'])

        when:
        def result = executor.executeFailurePath(failureSpec, context)

        then:
        result.appliedTags.containsAll(['failed', 'broken'])
    }

    def "executeFailurePath does not apply tags when list is empty"() {
        given:
        def context = PipelineContext.create('test')
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        failureSpec.additionalTags.set([])

        when:
        def result = executor.executeFailurePath(failureSpec, context)

        then:
        result.appliedTags.isEmpty()
    }

    def "executeFailurePath executes afterFailure hook"() {
        given:
        def hookExecuted = false
        def context = PipelineContext.create('test')
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        failureSpec.afterFailure.set({ hookExecuted = true } as Action<Void>)

        when:
        executor.executeFailurePath(failureSpec, context)

        then:
        hookExecuted
    }

    def "executeFailurePath handles saveFailureLogsDir configuration"() {
        given:
        def context = PipelineContext.create('test')
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        def logsDir = tempDir.resolve('failure-logs').toFile()
        logsDir.mkdirs()
        failureSpec.saveFailureLogsDir.set(logsDir)

        when:
        def result = executor.executeFailurePath(failureSpec, context)

        then:
        result != null
    }

    // ===== HAS ADDITIONAL TAGS TESTS =====

    def "hasAdditionalTags returns true for SuccessStepSpec with tags"() {
        given:
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        successSpec.additionalTags.set(['tag1', 'tag2'])

        expect:
        executor.hasAdditionalTags(successSpec)
    }

    def "hasAdditionalTags returns false for SuccessStepSpec without tags"() {
        given:
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)

        expect:
        !executor.hasAdditionalTags(successSpec)
    }

    def "hasAdditionalTags returns false for SuccessStepSpec with empty tags"() {
        given:
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        successSpec.additionalTags.set([])

        expect:
        !executor.hasAdditionalTags(successSpec)
    }

    def "hasAdditionalTags returns true for FailureStepSpec with tags"() {
        given:
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        failureSpec.additionalTags.set(['tag1'])

        expect:
        executor.hasAdditionalTags(failureSpec)
    }

    def "hasAdditionalTags returns false for FailureStepSpec without tags"() {
        given:
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

        expect:
        !executor.hasAdditionalTags(failureSpec)
    }

    def "hasAdditionalTags returns false for FailureStepSpec with empty tags"() {
        given:
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        failureSpec.additionalTags.set([])

        expect:
        !executor.hasAdditionalTags(failureSpec)
    }

    // ===== HOOK EXECUTION TESTS =====

    def "executeAfterSuccessHook does nothing when hook not configured"() {
        given:
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)

        when:
        executor.executeAfterSuccessHook(successSpec)

        then:
        noExceptionThrown()
    }

    def "executeAfterSuccessHook executes hook when configured"() {
        given:
        def executed = false
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        successSpec.afterSuccess.set({ executed = true } as Action<Void>)

        when:
        executor.executeAfterSuccessHook(successSpec)

        then:
        executed
    }

    def "executeAfterFailureHook does nothing when hook not configured"() {
        given:
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

        when:
        executor.executeAfterFailureHook(failureSpec)

        then:
        noExceptionThrown()
    }

    def "executeAfterFailureHook executes hook when configured"() {
        given:
        def executed = false
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        failureSpec.afterFailure.set({ executed = true } as Action<Void>)

        when:
        executor.executeAfterFailureHook(failureSpec)

        then:
        executed
    }

    def "executeHook executes action with null parameter"() {
        given:
        def receivedParam = 'not-null'
        def hook = { param -> receivedParam = param } as Action<Void>

        when:
        executor.executeHook(hook)

        then:
        receivedParam == null
    }

    // ===== INTEGRATION-LIKE TESTS =====

    def "conditional execution preserves existing context data"() {
        given:
        def context = PipelineContext.create('integration-test')
            .withMetadata('version', '1.0.0')
            .withMetadata('environment', 'ci')
        def testResult = TestResult.success(5, 5, 0)
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        successSpec.additionalTags.set(['release'])

        when:
        def result = executor.executeConditional(testResult, successSpec, null, context)

        then:
        result.pipelineName == 'integration-test'
        result.getMetadataValue('version') == '1.0.0'
        result.getMetadataValue('environment') == 'ci'
        result.appliedTags.contains('release')
    }

    def "failure path preserves context metadata"() {
        given:
        def context = PipelineContext.create('test')
            .withMetadata('build-id', '12345')
        def testResult = TestResult.failure(10, 8, 2, 0)
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        failureSpec.additionalTags.set(['failed-build'])

        when:
        def result = executor.executeConditional(testResult, null, failureSpec, context)

        then:
        result.getMetadataValue('build-id') == '12345'
        result.appliedTags.contains('failed-build')
    }

    def "handles null success spec with passing tests"() {
        given:
        def context = PipelineContext.create('test')
        def testResult = TestResult.success(5, 5, 0)

        when:
        def result = executor.executeConditional(testResult, null, null, context)

        then:
        result == context
    }

    def "handles null failure spec with failing tests"() {
        given:
        def context = PipelineContext.create('test')
        def testResult = TestResult.failure(5, 3, 2, 0)

        when:
        def result = executor.executeConditional(testResult, null, null, context)

        then:
        result == context
    }
}
