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

import com.kineticfire.gradle.docker.service.ComposeService
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.workflow.AlwaysStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TestResult
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for AlwaysStepExecutor
 */
class AlwaysStepExecutorTest extends Specification {

    Project project
    AlwaysStepExecutor executor
    ComposeService composeService
    DockerService dockerService
    AlwaysStepSpec alwaysSpec
    PipelineContext context
    ImageSpec imageSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        executor = new AlwaysStepExecutor()
        composeService = Mock(ComposeService)
        dockerService = Mock(DockerService)
        executor.setComposeService(composeService)
        executor.setDockerService(dockerService)
        executor.setComposeProjectName('test-project')

        alwaysSpec = project.objects.newInstance(AlwaysStepSpec)

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

    def "default constructor creates executor"() {
        when:
        def exec = new AlwaysStepExecutor()

        then:
        exec != null
    }

    // ===== EXECUTE TESTS =====

    def "execute returns context unchanged when alwaysSpec is null"() {
        when:
        def result = executor.execute(null, context, true)

        then:
        result == context
    }

    def "execute completes without error for default alwaysSpec"() {
        given:
        composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        def result = executor.execute(alwaysSpec, context, true)

        then:
        result != null
        result.pipelineName == 'testPipeline'
    }

    def "execute preserves existing context data"() {
        given:
        def enrichedContext = context.withMetadata('key', 'value')
        composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        def result = executor.execute(alwaysSpec, enrichedContext, true)

        then:
        result.getMetadataValue('key') == 'value'
    }

    // ===== REMOVE TEST CONTAINERS TESTS =====

    def "removeTestContainersIfConfigured calls composeService when enabled"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        executor.removeTestContainersIfConfigured(alwaysSpec, true)

        then:
        1 * composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)
    }

    def "removeTestContainersIfConfigured skips when disabled"() {
        given:
        alwaysSpec.removeTestContainers.set(false)

        when:
        executor.removeTestContainersIfConfigured(alwaysSpec, true)

        then:
        0 * composeService.downStack(_)
    }

    def "removeTestContainersIfConfigured skips when composeService is null"() {
        given:
        executor.setComposeService(null)
        alwaysSpec.removeTestContainers.set(true)

        when:
        executor.removeTestContainersIfConfigured(alwaysSpec, true)

        then:
        noExceptionThrown()
    }

    def "removeTestContainersIfConfigured skips when projectName is null"() {
        given:
        executor.setComposeProjectName(null)
        alwaysSpec.removeTestContainers.set(true)

        when:
        executor.removeTestContainersIfConfigured(alwaysSpec, true)

        then:
        0 * composeService.downStack(_)
    }

    def "removeTestContainersIfConfigured handles exception gracefully"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        composeService.downStack(_) >> { throw new RuntimeException('Down failed') }

        when:
        executor.removeTestContainersIfConfigured(alwaysSpec, true)

        then:
        noExceptionThrown()
    }

    // ===== KEEP FAILED CONTAINERS TESTS =====

    def "removeTestContainersIfConfigured keeps containers when tests failed and keepFailedContainers is true"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        alwaysSpec.keepFailedContainers.set(true)

        when:
        executor.removeTestContainersIfConfigured(alwaysSpec, false)

        then:
        0 * composeService.downStack(_)
    }

    def "removeTestContainersIfConfigured removes containers when tests passed and keepFailedContainers is true"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        alwaysSpec.keepFailedContainers.set(true)
        composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        executor.removeTestContainersIfConfigured(alwaysSpec, true)

        then:
        1 * composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)
    }

    def "removeTestContainersIfConfigured removes containers when tests failed and keepFailedContainers is false"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        alwaysSpec.keepFailedContainers.set(false)
        composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        executor.removeTestContainersIfConfigured(alwaysSpec, false)

        then:
        1 * composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)
    }

    // ===== SHOULD REMOVE CONTAINERS TESTS =====

    def "shouldRemoveContainers returns true by default"() {
        expect:
        executor.shouldRemoveContainers(alwaysSpec, true)
    }

    def "shouldRemoveContainers returns false when removeTestContainers is false"() {
        given:
        alwaysSpec.removeTestContainers.set(false)

        expect:
        !executor.shouldRemoveContainers(alwaysSpec, true)
    }

    def "shouldRemoveContainers returns false when tests failed and keepFailedContainers is true"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        alwaysSpec.keepFailedContainers.set(true)

        expect:
        !executor.shouldRemoveContainers(alwaysSpec, false)
    }

    def "shouldRemoveContainers returns true when tests passed and keepFailedContainers is true"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        alwaysSpec.keepFailedContainers.set(true)

        expect:
        executor.shouldRemoveContainers(alwaysSpec, true)
    }

    // ===== REMOVE TEST CONTAINERS TESTS =====

    def "removeTestContainers calls downStack with project name"() {
        given:
        composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        executor.removeTestContainers()

        then:
        1 * composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)
    }

    // ===== CLEANUP IMAGES TESTS =====

    def "cleanupImagesIfConfigured skips when not configured"() {
        given:
        alwaysSpec.cleanupImages.set(false)

        when:
        executor.cleanupImagesIfConfigured(alwaysSpec, context)

        then:
        noExceptionThrown()
    }

    def "cleanupImagesIfConfigured skips when dockerService is null"() {
        given:
        executor.setDockerService(null)
        alwaysSpec.cleanupImages.set(true)

        when:
        executor.cleanupImagesIfConfigured(alwaysSpec, context)

        then:
        noExceptionThrown()
    }

    def "cleanupImagesIfConfigured skips when no built image"() {
        given:
        alwaysSpec.cleanupImages.set(true)
        def noImageContext = PipelineContext.create('test')

        when:
        executor.cleanupImagesIfConfigured(alwaysSpec, noImageContext)

        then:
        noExceptionThrown()
    }

    def "cleanupImagesIfConfigured handles exception gracefully"() {
        given:
        alwaysSpec.cleanupImages.set(true)

        when:
        executor.cleanupImagesIfConfigured(alwaysSpec, context)

        then:
        noExceptionThrown()
    }

    // ===== SHOULD CLEANUP IMAGES TESTS =====

    def "shouldCleanupImages returns false by default"() {
        expect:
        !executor.shouldCleanupImages(alwaysSpec)
    }

    def "shouldCleanupImages returns true when cleanupImages is true"() {
        given:
        alwaysSpec.cleanupImages.set(true)

        expect:
        executor.shouldCleanupImages(alwaysSpec)
    }

    // ===== CLEANUP IMAGES TESTS =====

    def "cleanupImages logs image name when called"() {
        when:
        executor.cleanupImages(context)

        then:
        noExceptionThrown()
    }

    def "cleanupImages handles null builtImage gracefully"() {
        given:
        def noImageContext = PipelineContext.create('test')

        when:
        executor.cleanupImages(noImageContext)

        then:
        noExceptionThrown()
    }

    // ===== FULL EXECUTION FLOW TESTS =====

    def "execute removes containers when tests pass"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        def result = executor.execute(alwaysSpec, context, true)

        then:
        result != null
        1 * composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)
    }

    def "execute removes containers when tests fail and keepFailedContainers is false"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        alwaysSpec.keepFailedContainers.set(false)
        composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)

        when:
        def result = executor.execute(alwaysSpec, context, false)

        then:
        result != null
        1 * composeService.downStack('test-project') >> CompletableFuture.completedFuture(null)
    }

    def "execute keeps containers when tests fail and keepFailedContainers is true"() {
        given:
        alwaysSpec.removeTestContainers.set(true)
        alwaysSpec.keepFailedContainers.set(true)

        when:
        def result = executor.execute(alwaysSpec, context, false)

        then:
        result != null
        0 * composeService.downStack(_)
    }

    def "execute maintains pipeline name through execution"() {
        given:
        alwaysSpec.removeTestContainers.set(false)

        when:
        def result = executor.execute(alwaysSpec, context, true)

        then:
        result.pipelineName == 'testPipeline'
    }
}
