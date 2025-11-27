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
    SuccessStepExecutor successStepExecutor
    FailureStepExecutor failureStepExecutor

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        successStepExecutor = Mock(SuccessStepExecutor)
        failureStepExecutor = Mock(FailureStepExecutor)
        executor = new ConditionalExecutor(successStepExecutor, failureStepExecutor)
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
        def expectedContext = context.withAppliedTag('success-tag')
        def testResult = TestResult.success(10, 10, 0)
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

        when:
        def result = executor.executeConditional(testResult, successSpec, failureSpec, context)

        then:
        1 * successStepExecutor.execute(successSpec, context) >> expectedContext
        0 * failureStepExecutor.execute(_, _)
        result.appliedTags.contains('success-tag')
    }

    def "executeConditional executes failure path when tests fail"() {
        given:
        def context = PipelineContext.create('test')
        def expectedContext = context.withAppliedTag('failure-tag')
        def testResult = TestResult.failure(10, 8, 2, 0)
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

        when:
        def result = executor.executeConditional(testResult, successSpec, failureSpec, context)

        then:
        0 * successStepExecutor.execute(_, _)
        1 * failureStepExecutor.execute(failureSpec, context) >> expectedContext
        result.appliedTags.contains('failure-tag')
    }

    // ===== EXECUTE SUCCESS PATH TESTS =====

    def "executeSuccessPath returns context unchanged when successSpec is null"() {
        given:
        def context = PipelineContext.create('test')
        successStepExecutor.execute(null, context) >> context

        when:
        def result = executor.executeSuccessPath(null, context)

        then:
        result == context
    }

    def "executeSuccessPath delegates to success step executor"() {
        given:
        def context = PipelineContext.create('test')
        def expectedContext = context.withAppliedTags(['latest', 'stable', 'v1.0'])
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)

        when:
        def result = executor.executeSuccessPath(successSpec, context)

        then:
        1 * successStepExecutor.execute(successSpec, context) >> expectedContext
        result.appliedTags.containsAll(['latest', 'stable', 'v1.0'])
    }

    def "executeSuccessPath returns empty tags when executor returns empty context"() {
        given:
        def context = PipelineContext.create('test')
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)

        when:
        def result = executor.executeSuccessPath(successSpec, context)

        then:
        1 * successStepExecutor.execute(successSpec, context) >> context
        result.appliedTags.isEmpty()
    }

    def "executeSuccessPath delegates hook execution to executor"() {
        given:
        def context = PipelineContext.create('test')
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
        successSpec.afterSuccess.set({ } as Action<Void>)

        when:
        executor.executeSuccessPath(successSpec, context)

        then:
        1 * successStepExecutor.execute(successSpec, context) >> context
    }

    // ===== EXECUTE FAILURE PATH TESTS =====

    def "executeFailurePath returns context unchanged when failureSpec is null"() {
        given:
        def context = PipelineContext.create('test')
        failureStepExecutor.execute(null, context) >> context

        when:
        def result = executor.executeFailurePath(null, context)

        then:
        result == context
    }

    def "executeFailurePath delegates to failure step executor"() {
        given:
        def context = PipelineContext.create('test')
        def expectedContext = context.withAppliedTags(['failed', 'broken'])
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

        when:
        def result = executor.executeFailurePath(failureSpec, context)

        then:
        1 * failureStepExecutor.execute(failureSpec, context) >> expectedContext
        result.appliedTags.containsAll(['failed', 'broken'])
    }

    def "executeFailurePath returns empty tags when executor returns empty context"() {
        given:
        def context = PipelineContext.create('test')
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

        when:
        def result = executor.executeFailurePath(failureSpec, context)

        then:
        1 * failureStepExecutor.execute(failureSpec, context) >> context
        result.appliedTags.isEmpty()
    }

    def "executeFailurePath delegates hook execution to executor"() {
        given:
        def context = PipelineContext.create('test')
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        failureSpec.afterFailure.set({ } as Action<Void>)

        when:
        executor.executeFailurePath(failureSpec, context)

        then:
        1 * failureStepExecutor.execute(failureSpec, context) >> context
    }

    def "executeFailurePath handles saveFailureLogsDir via executor"() {
        given:
        def context = PipelineContext.create('test')
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
        def logsDir = tempDir.resolve('failure-logs').toFile()
        logsDir.mkdirs()
        failureSpec.saveFailureLogsDir.set(logsDir)

        when:
        def result = executor.executeFailurePath(failureSpec, context)

        then:
        1 * failureStepExecutor.execute(failureSpec, context) >> context
        result != null
    }

    // ===== INTEGRATION-LIKE TESTS =====

    def "conditional execution preserves existing context data"() {
        given:
        def context = PipelineContext.create('integration-test')
            .withMetadata('version', '1.0.0')
            .withMetadata('environment', 'ci')
        def expectedContext = context.withAppliedTag('release')
        def testResult = TestResult.success(5, 5, 0)
        def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)

        when:
        def result = executor.executeConditional(testResult, successSpec, null, context)

        then:
        1 * successStepExecutor.execute(successSpec, context) >> expectedContext
        result.pipelineName == 'integration-test'
        result.getMetadataValue('version') == '1.0.0'
        result.getMetadataValue('environment') == 'ci'
        result.appliedTags.contains('release')
    }

    def "failure path preserves context metadata"() {
        given:
        def context = PipelineContext.create('test')
            .withMetadata('build-id', '12345')
        def expectedContext = context.withAppliedTag('failed-build')
        def testResult = TestResult.failure(10, 8, 2, 0)
        def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

        when:
        def result = executor.executeConditional(testResult, null, failureSpec, context)

        then:
        1 * failureStepExecutor.execute(failureSpec, context) >> expectedContext
        result.getMetadataValue('build-id') == '12345'
        result.appliedTags.contains('failed-build')
    }

    def "handles null success spec with passing tests"() {
        given:
        def context = PipelineContext.create('test')
        def testResult = TestResult.success(5, 5, 0)
        successStepExecutor.execute(null, context) >> context

        when:
        def result = executor.executeConditional(testResult, null, null, context)

        then:
        result == context
    }

    def "handles null failure spec with failing tests"() {
        given:
        def context = PipelineContext.create('test')
        def testResult = TestResult.failure(5, 3, 2, 0)
        failureStepExecutor.execute(null, context) >> context

        when:
        def result = executor.executeConditional(testResult, null, null, context)

        then:
        result == context
    }
}
