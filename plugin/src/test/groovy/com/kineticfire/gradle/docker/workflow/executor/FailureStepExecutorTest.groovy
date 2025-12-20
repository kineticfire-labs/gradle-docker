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

import com.kineticfire.gradle.docker.model.LogsConfig
import com.kineticfire.gradle.docker.service.ComposeService
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.operation.TagOperationExecutor
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for FailureStepExecutor
 */
class FailureStepExecutorTest extends Specification {

    @TempDir
    File tempDir

    Project project
    FailureStepExecutor executor
    TagOperationExecutor tagOperationExecutor
    DockerService dockerService
    ComposeService composeService
    FailureStepSpec failureSpec
    PipelineContext context
    ImageSpec imageSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        tagOperationExecutor = Mock(TagOperationExecutor)
        executor = new FailureStepExecutor(tagOperationExecutor)
        dockerService = Mock(DockerService)
        composeService = Mock(ComposeService)
        executor.setDockerService(dockerService)
        executor.setComposeService(composeService)
        executor.setComposeProjectName('test-project')

        failureSpec = project.objects.newInstance(FailureStepSpec)

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

    def "default constructor creates TagOperationExecutor"() {
        when:
        def exec = new FailureStepExecutor()

        then:
        exec != null
    }

    def "constructor accepts TagOperationExecutor"() {
        given:
        def mockTagExecutor = Mock(TagOperationExecutor)

        when:
        def exec = new FailureStepExecutor(mockTagExecutor)

        then:
        exec != null
    }

    // ===== EXECUTE TESTS =====

    def "execute returns context unchanged when failureSpec is null"() {
        when:
        def result = executor.execute(null, context)

        then:
        result == context
    }

    def "execute completes without error for empty failureSpec"() {
        when:
        def result = executor.execute(failureSpec, context)

        then:
        result != null
        result.pipelineName == 'testPipeline'
    }

    def "execute preserves existing context data"() {
        given:
        def enrichedContext = context.withMetadata('key', 'value')

        when:
        def result = executor.execute(failureSpec, enrichedContext)

        then:
        result.getMetadataValue('key') == 'value'
    }

    // ===== APPLY FAILURE TAGS TESTS =====

    def "applyFailureTags returns context unchanged when no tags configured"() {
        when:
        def result = executor.applyFailureTags(failureSpec, context)

        then:
        result.appliedTags.isEmpty()
    }

    def "applyFailureTags returns context unchanged when tags list is empty"() {
        given:
        failureSpec.additionalTags.set([])

        when:
        def result = executor.applyFailureTags(failureSpec, context)

        then:
        result.appliedTags.isEmpty()
    }

    def "applyFailureTags adds tags to context"() {
        given:
        failureSpec.additionalTags.set(['failed', 'debug'])

        when:
        def result = executor.applyFailureTags(failureSpec, context)

        then:
        result.appliedTags.containsAll(['failed', 'debug'])
    }

    def "applyFailureTags logs warning when no built image"() {
        given:
        failureSpec.additionalTags.set(['failed'])
        def noImageContext = PipelineContext.create('test')

        when:
        def result = executor.applyFailureTags(failureSpec, noImageContext)

        then:
        result == noImageContext
        0 * tagOperationExecutor.execute(_, _, _)
    }

    def "applyFailureTags calls TagOperationExecutor"() {
        given:
        failureSpec.additionalTags.set(['failed', 'debug'])

        when:
        executor.applyFailureTags(failureSpec, context)

        then:
        1 * tagOperationExecutor.execute(imageSpec, ['failed', 'debug'], dockerService)
    }

    def "applyFailureTags works without dockerService"() {
        given:
        executor.setDockerService(null)
        failureSpec.additionalTags.set(['failed'])

        when:
        def result = executor.applyFailureTags(failureSpec, context)

        then:
        result.appliedTags.contains('failed')
        0 * tagOperationExecutor.execute(_, _, _)
    }

    // ===== HAS ADDITIONAL TAGS TESTS =====

    def "hasAdditionalTags returns false when not set"() {
        expect:
        !executor.hasAdditionalTags(failureSpec)
    }

    def "hasAdditionalTags returns false when empty"() {
        given:
        failureSpec.additionalTags.set([])

        expect:
        !executor.hasAdditionalTags(failureSpec)
    }

    def "hasAdditionalTags returns true when tags present"() {
        given:
        failureSpec.additionalTags.set(['failed'])

        expect:
        executor.hasAdditionalTags(failureSpec)
    }

    def "hasAdditionalTags returns true for multiple tags"() {
        given:
        failureSpec.additionalTags.set(['failed', 'debug', 'broken'])

        expect:
        executor.hasAdditionalTags(failureSpec)
    }

    // ===== SAVE FAILURE LOGS TESTS =====

    def "saveFailureLogs does nothing when not configured"() {
        when:
        executor.saveFailureLogs(failureSpec, context)

        then:
        noExceptionThrown()
        0 * composeService.captureLogs(_, _)
    }

    def "saveFailureLogs calls composeService when configured"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        failureSpec.saveFailureLogsDir.set(logsDir)
        composeService.captureLogs('test-project', _ as LogsConfig) >>
            CompletableFuture.completedFuture('log content')

        when:
        executor.saveFailureLogs(failureSpec, context)

        then:
        1 * composeService.captureLogs('test-project', _ as LogsConfig) >>
            CompletableFuture.completedFuture('log content')
    }

    def "saveFailureLogs creates log directory if it doesn't exist"() {
        given:
        def logsDir = new File(tempDir, 'newlogs')
        failureSpec.saveFailureLogsDir.set(logsDir)
        composeService.captureLogs('test-project', _ as LogsConfig) >>
            CompletableFuture.completedFuture('log content')

        when:
        executor.saveFailureLogs(failureSpec, context)

        then:
        logsDir.exists()
    }

    def "saveFailureLogs logs warning when composeService not set"() {
        given:
        executor.setComposeService(null)
        def logsDir = new File(tempDir, 'logs')
        failureSpec.saveFailureLogsDir.set(logsDir)

        when:
        executor.saveFailureLogs(failureSpec, context)

        then:
        noExceptionThrown()
        0 * composeService.captureLogs(_, _)
    }

    def "saveFailureLogs logs warning when projectName not set"() {
        given:
        executor.setComposeProjectName(null)
        def logsDir = new File(tempDir, 'logs')
        failureSpec.saveFailureLogsDir.set(logsDir)

        when:
        executor.saveFailureLogs(failureSpec, context)

        then:
        noExceptionThrown()
        0 * composeService.captureLogs(_, _)
    }

    def "saveFailureLogs handles exception gracefully"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        failureSpec.saveFailureLogsDir.set(logsDir)
        composeService.captureLogs(_, _) >> { throw new RuntimeException('Log capture failed') }

        when:
        executor.saveFailureLogs(failureSpec, context)

        then:
        noExceptionThrown()
    }

    // ===== HAS SAVE LOGS CONFIGURED TESTS =====

    def "hasSaveLogsConfigured returns false when not set"() {
        expect:
        !executor.hasSaveLogsConfigured(failureSpec)
    }

    def "hasSaveLogsConfigured returns true when saveFailureLogsDir is set"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        failureSpec.saveFailureLogsDir.set(logsDir)

        expect:
        executor.hasSaveLogsConfigured(failureSpec)
    }

    // ===== SAVE LOGS TO DIRECTORY TESTS =====

    def "saveLogsToDirectory creates log file with captured logs"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        logsDir.mkdirs()
        composeService.captureLogs('test-project', _ as LogsConfig) >>
            CompletableFuture.completedFuture('captured log content')

        when:
        executor.saveLogsToDirectory(logsDir, [])

        then:
        def logFiles = logsDir.listFiles()
        logFiles.length == 1
        logFiles[0].name.startsWith('failure-logs-')
        logFiles[0].text == 'captured log content'
    }

    def "saveLogsToDirectory creates directory if missing"() {
        given:
        def logsDir = new File(tempDir, 'newdir')
        composeService.captureLogs('test-project', _ as LogsConfig) >>
            CompletableFuture.completedFuture('log content')

        when:
        executor.saveLogsToDirectory(logsDir, [])

        then:
        logsDir.exists()
        logsDir.isDirectory()
    }

    def "saveLogsToDirectory passes service names to LogsConfig"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        logsDir.mkdirs()

        when:
        executor.saveLogsToDirectory(logsDir, ['service1', 'service2'])

        then:
        1 * composeService.captureLogs('test-project', { LogsConfig config ->
            config.services.containsAll(['service1', 'service2'])
        }) >> CompletableFuture.completedFuture('log content')
    }

    def "saveLogsToDirectory passes empty services when empty list provided"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        logsDir.mkdirs()

        when:
        executor.saveLogsToDirectory(logsDir, [])

        then:
        1 * composeService.captureLogs('test-project', { LogsConfig config ->
            config.services.isEmpty()
        }) >> CompletableFuture.completedFuture('log content')
    }

    // ===== EXECUTE AFTER FAILURE HOOK TESTS =====

    def "executeAfterFailureHook does nothing when hook not configured"() {
        when:
        executor.executeAfterFailureHook(failureSpec)

        then:
        noExceptionThrown()
    }

    def "executeAfterFailureHook executes hook when configured"() {
        given:
        def hookCalled = false
        def hook = { hookCalled = true } as Action<Void>
        failureSpec.afterFailure.set(hook)

        when:
        executor.executeAfterFailureHook(failureSpec)

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
        failureSpec.additionalTags.set(['failed'])
        def hookCalled = false
        failureSpec.afterFailure.set({ hookCalled = true } as Action<Void>)

        when:
        def result = executor.execute(failureSpec, context)

        then:
        result.appliedTags.contains('failed')
        hookCalled
    }

    def "execute maintains pipeline name through execution"() {
        given:
        failureSpec.additionalTags.set(['failed'])

        when:
        def result = executor.execute(failureSpec, context)

        then:
        result.pipelineName == 'testPipeline'
    }

    def "execute accumulates tags from multiple calls"() {
        given:
        failureSpec.additionalTags.set(['failed', 'debug'])
        def preTaggedContext = context.withAppliedTag('initial')

        when:
        def result = executor.execute(failureSpec, preTaggedContext)

        then:
        result.appliedTags.containsAll(['initial', 'failed', 'debug'])
    }

    def "execute saves logs and applies tags"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        failureSpec.saveFailureLogsDir.set(logsDir)
        failureSpec.additionalTags.set(['failed'])
        composeService.captureLogs('test-project', _ as LogsConfig) >>
            CompletableFuture.completedFuture('log content')

        when:
        def result = executor.execute(failureSpec, context)

        then:
        result.appliedTags.contains('failed')
        logsDir.exists()
    }

    def "execute runs all operations in sequence"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        failureSpec.saveFailureLogsDir.set(logsDir)
        failureSpec.additionalTags.set(['failed'])
        def hookCalled = false
        failureSpec.afterFailure.set({ hookCalled = true } as Action<Void>)
        composeService.captureLogs('test-project', _ as LogsConfig) >>
            CompletableFuture.completedFuture('log content')

        when:
        def result = executor.execute(failureSpec, context)

        then:
        result.appliedTags.contains('failed')
        logsDir.exists()
        hookCalled
    }

    // ===== ADDITIONAL COVERAGE TESTS =====

    def "saveFailureLogs logs error message when exception occurs"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        failureSpec.saveFailureLogsDir.set(logsDir)
        def errorMessage = 'Log capture error'
        composeService.captureLogs(_, _) >> { throw new RuntimeException(errorMessage) }

        when:
        executor.saveFailureLogs(failureSpec, context)

        then:
        noExceptionThrown()
        // Error is logged with message but doesn't propagate
    }

    def "saveFailureLogs with includeServices passes services to LogsConfig"() {
        given:
        def logsDir = new File(tempDir, 'logs')
        failureSpec.saveFailureLogsDir.set(logsDir)
        failureSpec.includeServices.set(['app', 'db'])
        composeService.captureLogs('test-project', _ as LogsConfig) >>
            CompletableFuture.completedFuture('log content')

        when:
        executor.saveFailureLogs(failureSpec, context)

        then:
        1 * composeService.captureLogs('test-project', { LogsConfig config ->
            config.services != null && config.services.containsAll(['app', 'db'])
        }) >> CompletableFuture.completedFuture('log content')
    }
}
