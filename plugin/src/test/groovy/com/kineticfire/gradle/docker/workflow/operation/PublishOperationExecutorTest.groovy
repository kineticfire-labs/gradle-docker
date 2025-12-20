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

import com.kineticfire.gradle.docker.model.AuthConfig
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.AuthSpec
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.PublishSpec
import com.kineticfire.gradle.docker.spec.PublishTarget
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for PublishOperationExecutor
 */
class PublishOperationExecutorTest extends Specification {

    Project project
    PublishOperationExecutor executor
    ImageSpec imageSpec
    PublishSpec publishSpec
    DockerService dockerService

    def setup() {
        project = ProjectBuilder.builder().build()
        executor = new PublishOperationExecutor()

        imageSpec = project.objects.newInstance(
            ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])

        publishSpec = project.objects.newInstance(PublishSpec)

        dockerService = Mock(DockerService)
    }

    // ===== EXECUTE VALIDATION TESTS =====

    def "execute throws exception when publishSpec is null"() {
        when:
        executor.execute(null, imageSpec, dockerService)

        then:
        def e = thrown(GradleException)
        e.message.contains('PublishSpec cannot be null')
    }

    def "execute throws exception when imageSpec is null"() {
        when:
        executor.execute(publishSpec, null, dockerService)

        then:
        def e = thrown(GradleException)
        e.message.contains('ImageSpec cannot be null')
    }

    def "execute throws exception when dockerService is null"() {
        when:
        executor.execute(publishSpec, imageSpec, null)

        then:
        def e = thrown(GradleException)
        e.message.contains('DockerService cannot be null')
    }

    def "execute does nothing when no targets configured"() {
        when:
        executor.execute(publishSpec, imageSpec, dockerService)

        then:
        noExceptionThrown()
        0 * dockerService._
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

    // ===== RESOLVE PUBLISH TAGS TESTS =====

    def "resolvePublishTags returns target tags when set"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.publishTags.set(['stable', 'production'])

        when:
        def result = executor.resolvePublishTags(target, publishSpec, imageSpec)

        then:
        result == ['stable', 'production']
    }

    def "resolvePublishTags returns spec tags when target tags not set"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        publishSpec.publishTags.set(['latest', 'v1'])

        when:
        def result = executor.resolvePublishTags(target, publishSpec, imageSpec)

        then:
        result == ['latest', 'v1']
    }

    def "resolvePublishTags returns empty when no tags configured"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)

        when:
        def result = executor.resolvePublishTags(target, publishSpec, imageSpec)

        then:
        result == []
    }

    def "resolvePublishTags prefers target tags over spec tags"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.publishTags.set(['target-tag'])
        publishSpec.publishTags.set(['spec-tag'])

        when:
        def result = executor.resolvePublishTags(target, publishSpec, imageSpec)

        then:
        result == ['target-tag']
    }

    // ===== BUILD TARGET REFERENCES TESTS =====

    def "buildTargetReferences uses target registry"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('ghcr.io')
        target.imageName.set('myapp')

        when:
        def result = executor.buildTargetReferences(target, imageSpec, ['1.0.0'])

        then:
        result == ['ghcr.io/myapp:1.0.0']
    }

    def "buildTargetReferences uses target namespace"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('docker.io')
        target.namespace.set('myorg')
        target.imageName.set('myapp')

        when:
        def result = executor.buildTargetReferences(target, imageSpec, ['v2.0'])

        then:
        result == ['docker.io/myorg/myapp:v2.0']
    }

    def "buildTargetReferences inherits imageName from source when not set"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('ghcr.io')
        imageSpec.imageName.set('sourceapp')

        when:
        def result = executor.buildTargetReferences(target, imageSpec, ['1.0.0'])

        then:
        result == ['ghcr.io/sourceapp:1.0.0']
    }

    def "buildTargetReferences creates references for multiple tags"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('docker.io')
        target.imageName.set('myapp')

        when:
        def result = executor.buildTargetReferences(target, imageSpec, ['1.0.0', 'latest', 'stable'])
        def resultStrings = result.collect { it.toString() }

        then:
        resultStrings.size() == 3
        resultStrings.contains('docker.io/myapp:1.0.0')
        resultStrings.contains('docker.io/myapp:latest')
        resultStrings.contains('docker.io/myapp:stable')
    }

    // ===== RESOLVE AUTH TESTS =====

    def "resolveAuth returns null when auth not configured"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)

        when:
        def result = executor.resolveAuth(target)

        then:
        result == null
    }

    def "resolveAuth returns AuthConfig when auth configured"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.username.set('user')
        authSpec.password.set('pass')
        target.auth.set(authSpec)

        when:
        def result = executor.resolveAuth(target)

        then:
        result != null
        result.username == 'user'
        result.password == 'pass'
    }

    def "resolveAuth returns token-based AuthConfig"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.registryToken.set('my-token')
        target.auth.set(authSpec)

        when:
        def result = executor.resolveAuth(target)

        then:
        result != null
        result.registryToken == 'my-token'
    }

    // ===== TAG FOR PUBLISH TESTS =====

    def "tagForPublish skips when source and target are same"() {
        when:
        executor.tagForPublish('myapp:1.0.0', 'myapp:1.0.0', dockerService)

        then:
        0 * dockerService.tagImage(_, _)
    }

    def "tagForPublish calls dockerService when different"() {
        given:
        dockerService.tagImage('myapp:1.0.0', ['ghcr.io/myapp:1.0.0']) >>
            CompletableFuture.completedFuture(null)

        when:
        executor.tagForPublish('myapp:1.0.0', 'ghcr.io/myapp:1.0.0', dockerService)

        then:
        1 * dockerService.tagImage('myapp:1.0.0', ['ghcr.io/myapp:1.0.0']) >>
            CompletableFuture.completedFuture(null)
    }

    // ===== PUSH IMAGE TESTS =====

    def "pushImage calls dockerService without auth"() {
        given:
        dockerService.pushImage('ghcr.io/myapp:1.0.0', null) >>
            CompletableFuture.completedFuture(null)

        when:
        executor.pushImage('ghcr.io/myapp:1.0.0', null, dockerService)

        then:
        1 * dockerService.pushImage('ghcr.io/myapp:1.0.0', null) >>
            CompletableFuture.completedFuture(null)
    }

    def "pushImage calls dockerService with auth"() {
        given:
        def auth = new AuthConfig('user', 'pass', null)
        dockerService.pushImage('ghcr.io/myapp:1.0.0', auth) >>
            CompletableFuture.completedFuture(null)

        when:
        executor.pushImage('ghcr.io/myapp:1.0.0', auth, dockerService)

        then:
        1 * dockerService.pushImage('ghcr.io/myapp:1.0.0', auth) >>
            CompletableFuture.completedFuture(null)
    }

    // ===== PUBLISH IMAGE TESTS =====

    def "publishImage tags and pushes image"() {
        given:
        dockerService.tagImage('myapp:1.0.0', ['ghcr.io/myapp:1.0.0']) >>
            CompletableFuture.completedFuture(null)
        dockerService.pushImage('ghcr.io/myapp:1.0.0', null) >>
            CompletableFuture.completedFuture(null)

        when:
        executor.publishImage('myapp:1.0.0', 'ghcr.io/myapp:1.0.0', null, dockerService)

        then:
        1 * dockerService.tagImage('myapp:1.0.0', ['ghcr.io/myapp:1.0.0']) >>
            CompletableFuture.completedFuture(null)
        1 * dockerService.pushImage('ghcr.io/myapp:1.0.0', null) >>
            CompletableFuture.completedFuture(null)
    }

    // ===== PUBLISH TO TARGET TESTS =====

    def "publishToTarget publishes with source tag when no tags configured"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('ghcr.io')
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])

        dockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        dockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)

        when:
        executor.publishToTarget('myapp:1.0.0', imageSpec, target, publishSpec, dockerService)

        then:
        1 * dockerService.tagImage('myapp:1.0.0', ['ghcr.io/myapp:1.0.0']) >>
            CompletableFuture.completedFuture(null)
        1 * dockerService.pushImage('ghcr.io/myapp:1.0.0', null) >>
            CompletableFuture.completedFuture(null)
    }

    def "publishToTarget publishes multiple tags"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('ghcr.io')
        target.imageName.set('myapp')
        target.publishTags.set(['v1.0.0', 'latest'])

        dockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        dockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)

        when:
        executor.publishToTarget('myapp:1.0.0', imageSpec, target, publishSpec, dockerService)

        then:
        2 * dockerService.tagImage('myapp:1.0.0', _) >> CompletableFuture.completedFuture(null)
        2 * dockerService.pushImage(_, null) >> CompletableFuture.completedFuture(null)
    }

    // ===== EXECUTE INTEGRATION TESTS =====

    def "execute publishes to single target"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('ghcr.io')
        target.imageName.set('myapp')
        target.publishTags.set(['1.0.0'])
        publishSpec.to.add(target)

        dockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        dockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)

        when:
        executor.execute(publishSpec, imageSpec, dockerService)

        then:
        1 * dockerService.tagImage('myapp:1.0.0', ['ghcr.io/myapp:1.0.0']) >>
            CompletableFuture.completedFuture(null)
        1 * dockerService.pushImage('ghcr.io/myapp:1.0.0', null) >>
            CompletableFuture.completedFuture(null)
    }

    def "execute publishes to multiple targets"() {
        given:
        def target1 = project.objects.newInstance(PublishTarget, 'staging', project.objects)
        target1.registry.set('staging.registry.io')
        target1.imageName.set('myapp')
        target1.publishTags.set(['1.0.0'])
        publishSpec.to.add(target1)

        def target2 = project.objects.newInstance(PublishTarget, 'production', project.objects)
        target2.registry.set('prod.registry.io')
        target2.imageName.set('myapp')
        target2.publishTags.set(['1.0.0'])
        publishSpec.to.add(target2)

        dockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        dockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)

        when:
        executor.execute(publishSpec, imageSpec, dockerService)

        then:
        2 * dockerService.tagImage('myapp:1.0.0', _) >> CompletableFuture.completedFuture(null)
        2 * dockerService.pushImage(_, null) >> CompletableFuture.completedFuture(null)
    }

    def "execute wraps dockerService exception in GradleException"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('ghcr.io')
        target.imageName.set('myapp')
        target.publishTags.set(['1.0.0'])
        publishSpec.to.add(target)

        def failedFuture = new CompletableFuture<Void>()
        failedFuture.completeExceptionally(new RuntimeException("Docker error"))
        dockerService.tagImage(_, _) >> failedFuture

        when:
        executor.execute(publishSpec, imageSpec, dockerService)

        then:
        def e = thrown(GradleException)
        e.message.contains('Failed to publish image')
    }

    def "execute publishes with authentication"() {
        given:
        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.username.set('user')
        authSpec.password.set('secret')

        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('ghcr.io')
        target.imageName.set('myapp')
        target.publishTags.set(['1.0.0'])
        target.auth.set(authSpec)
        publishSpec.to.add(target)

        dockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        dockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)

        when:
        executor.execute(publishSpec, imageSpec, dockerService)

        then:
        1 * dockerService.pushImage('ghcr.io/myapp:1.0.0', { it.username == 'user' && it.password == 'secret' }) >>
            CompletableFuture.completedFuture(null)
    }

    // ===== ADDITIONAL COVERAGE TESTS =====

    def "resolvePublishTags returns empty when no publish tags configured anywhere"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        // No publishTags on target or publishSpec
        imageSpec.tags.set(['source-tag'])

        when:
        def result = executor.resolvePublishTags(target, publishSpec, imageSpec)

        then:
        result.isEmpty()  // Falls back to empty since neither target nor spec has tags
    }

    def "publishToTarget uses source tag when no publish tags configured"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('ghcr.io')
        target.imageName.set('myapp')
        // No publishTags set - should use source tag

        imageSpec.tags.set(['1.0.0'])

        dockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        dockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)

        when:
        executor.publishToTarget('myapp:1.0.0', imageSpec, target, publishSpec, dockerService)

        then:
        1 * dockerService.tagImage('myapp:1.0.0', ['ghcr.io/myapp:1.0.0']) >> CompletableFuture.completedFuture(null)
        1 * dockerService.pushImage('ghcr.io/myapp:1.0.0', null) >> CompletableFuture.completedFuture(null)
    }

    def "resolveAuth returns null when auth property is not present"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        // auth not set

        when:
        def result = executor.resolveAuth(target)

        then:
        result == null
    }

    def "buildTargetReferences uses source repository when target has repository"() {
        given:
        def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
        target.registry.set('ghcr.io')
        target.repository.set('myorg/myapp')

        when:
        def result = executor.buildTargetReferences(target, imageSpec, ['1.0.0'])

        then:
        result.size() == 1
        result[0] == 'ghcr.io/myorg/myapp:1.0.0'
    }
}
