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

package com.kineticfire.gradle.docker.workflow.operation

import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.ImageSpec
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for TagOperationExecutor
 */
class TagOperationExecutorTest extends Specification {

    Project project
    TagOperationExecutor executor
    ImageSpec imageSpec
    DockerService dockerService

    def setup() {
        project = ProjectBuilder.builder().build()
        executor = new TagOperationExecutor()

        imageSpec = project.objects.newInstance(
            ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])

        dockerService = Mock(DockerService)
    }

    // ===== EXECUTE VALIDATION TESTS =====

    def "execute throws exception when imageSpec is null"() {
        when:
        executor.execute(null, ['stable'], dockerService)

        then:
        def e = thrown(GradleException)
        e.message.contains('ImageSpec cannot be null')
    }

    def "execute returns early when additionalTags is null"() {
        when:
        executor.execute(imageSpec, null, dockerService)

        then:
        0 * dockerService.tagImage(_, _)
    }

    def "execute returns early when additionalTags is empty"() {
        when:
        executor.execute(imageSpec, [], dockerService)

        then:
        0 * dockerService.tagImage(_, _)
    }

    def "execute throws exception when dockerService is null"() {
        when:
        executor.execute(imageSpec, ['stable'], null)

        then:
        def e = thrown(GradleException)
        e.message.contains('DockerService cannot be null')
    }

    // ===== BUILD SOURCE IMAGE REFERENCE TESTS =====

    def "buildSourceImageReference builds reference from imageName and first tag"() {
        given:
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0', 'latest'])

        when:
        def result = executor.buildSourceImageReference(imageSpec)

        then:
        result == 'myapp:1.0.0'
    }

    def "buildSourceImageReference includes registry when set"() {
        given:
        imageSpec.registry.set('docker.io')
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])

        when:
        def result = executor.buildSourceImageReference(imageSpec)

        then:
        result == 'docker.io/myapp:1.0.0'
    }

    def "buildSourceImageReference includes namespace when set"() {
        given:
        imageSpec.namespace.set('mycompany')
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])

        when:
        def result = executor.buildSourceImageReference(imageSpec)

        then:
        result == 'mycompany/myapp:1.0.0'
    }

    def "buildSourceImageReference includes registry and namespace"() {
        given:
        imageSpec.registry.set('gcr.io')
        imageSpec.namespace.set('myproject')
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['v1.2.3'])

        when:
        def result = executor.buildSourceImageReference(imageSpec)

        then:
        result == 'gcr.io/myproject/myapp:v1.2.3'
    }

    def "buildSourceImageReference uses repository when set"() {
        given:
        imageSpec.registry.set('docker.io')
        imageSpec.repository.set('library/nginx')
        imageSpec.imageName.set('')
        imageSpec.tags.set(['1.25.0'])

        when:
        def result = executor.buildSourceImageReference(imageSpec)

        then:
        result == 'docker.io/library/nginx:1.25.0'
    }

    def "buildSourceImageReference throws when no tags configured"() {
        given:
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set([])

        when:
        executor.buildSourceImageReference(imageSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains('no tags configured')
    }

    def "buildSourceImageReference throws when cannot build reference"() {
        given:
        imageSpec.imageName.set('')
        imageSpec.repository.set('')
        imageSpec.tags.set(['1.0.0'])

        when:
        executor.buildSourceImageReference(imageSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains('Cannot build image reference')
    }

    // ===== BUILD TARGET IMAGE REFERENCES TESTS =====

    def "buildTargetImageReferences builds single reference"() {
        given:
        imageSpec.imageName.set('myapp')

        when:
        def result = executor.buildTargetImageReferences(imageSpec, ['stable'])

        then:
        result.size() == 1
        result[0] == 'myapp:stable'
    }

    def "buildTargetImageReferences builds multiple references"() {
        given:
        imageSpec.imageName.set('myapp')

        when:
        def result = executor.buildTargetImageReferences(imageSpec, ['stable', 'production', 'latest'])

        then:
        result.size() == 3
        result.collect { it.toString() }.containsAll(['myapp:stable', 'myapp:production', 'myapp:latest'])
    }

    def "buildTargetImageReferences includes registry"() {
        given:
        imageSpec.registry.set('ghcr.io')
        imageSpec.namespace.set('myorg')
        imageSpec.imageName.set('myapp')

        when:
        def result = executor.buildTargetImageReferences(imageSpec, ['stable'])

        then:
        result.size() == 1
        result[0] == 'ghcr.io/myorg/myapp:stable'
    }

    def "buildTargetImageReferences uses repository style"() {
        given:
        imageSpec.registry.set('docker.io')
        imageSpec.repository.set('company/product')
        imageSpec.imageName.set('')

        when:
        def result = executor.buildTargetImageReferences(imageSpec, ['stable', 'prod'])

        then:
        result.size() == 2
        result.collect { it.toString() }.containsAll([
            'docker.io/company/product:stable', 'docker.io/company/product:prod'
        ])
    }

    def "buildTargetImageReferences returns empty list when no imageName or repository"() {
        given:
        imageSpec.imageName.set('')
        imageSpec.repository.set('')

        when:
        def result = executor.buildTargetImageReferences(imageSpec, ['stable'])

        then:
        result.isEmpty()
    }

    // ===== APPLY TAGS TESTS =====

    def "applyTags calls dockerService.tagImage"() {
        given:
        def sourceImage = 'myapp:1.0.0'
        def targetTags = ['myapp:stable', 'myapp:production']
        dockerService.tagImage(sourceImage, targetTags) >> CompletableFuture.completedFuture(null)

        when:
        executor.applyTags(sourceImage, targetTags, dockerService)

        then:
        1 * dockerService.tagImage(sourceImage, targetTags) >> CompletableFuture.completedFuture(null)
    }

    def "applyTags waits for future completion"() {
        given:
        def sourceImage = 'myapp:1.0.0'
        def targetTags = ['myapp:stable']
        def futureCompleted = false
        def future = new CompletableFuture<Void>()
        future.whenComplete { result, error -> futureCompleted = true }
        future.complete(null)
        dockerService.tagImage(sourceImage, targetTags) >> future

        when:
        executor.applyTags(sourceImage, targetTags, dockerService)

        then:
        futureCompleted
    }

    // ===== EXECUTE INTEGRATION TESTS =====

    def "execute applies tags successfully"() {
        given:
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])
        def additionalTags = ['stable', 'production']
        dockerService.tagImage('myapp:1.0.0', ['myapp:stable', 'myapp:production']) >>
            CompletableFuture.completedFuture(null)

        when:
        executor.execute(imageSpec, additionalTags, dockerService)

        then:
        1 * dockerService.tagImage('myapp:1.0.0', ['myapp:stable', 'myapp:production']) >>
            CompletableFuture.completedFuture(null)
    }

    def "execute wraps dockerService exception in GradleException"() {
        given:
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])
        def failedFuture = new CompletableFuture<Void>()
        failedFuture.completeExceptionally(new RuntimeException("Docker error"))
        dockerService.tagImage(_, _) >> failedFuture

        when:
        executor.execute(imageSpec, ['stable'], dockerService)

        then:
        def e = thrown(GradleException)
        e.message.contains('Failed to apply tags')
    }

    def "execute applies tags with full image path"() {
        given:
        imageSpec.registry.set('ghcr.io')
        imageSpec.namespace.set('myorg')
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['v1.0.0'])
        dockerService.tagImage('ghcr.io/myorg/myapp:v1.0.0', ['ghcr.io/myorg/myapp:stable']) >>
            CompletableFuture.completedFuture(null)

        when:
        executor.execute(imageSpec, ['stable'], dockerService)

        then:
        1 * dockerService.tagImage('ghcr.io/myorg/myapp:v1.0.0', ['ghcr.io/myorg/myapp:stable']) >>
            CompletableFuture.completedFuture(null)
    }
}
