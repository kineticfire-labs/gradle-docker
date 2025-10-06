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

package com.kineticfire.gradle.docker.task

import com.kineticfire.gradle.docker.exception.DockerServiceException
import com.kineticfire.gradle.docker.model.AuthConfig
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.AuthSpec
import com.kineticfire.gradle.docker.spec.PublishTarget
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for DockerPublishTask
 */
class DockerPublishTaskTest extends Specification {

    Project project
    DockerPublishTask task
    DockerService mockDockerService = Mock()

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testDockerPublish', DockerPublishTask).get()
        task.dockerService.set(mockDockerService)
    }

    // ===== BASIC TASK TESTS =====

    def "task can be created"() {
        expect:
        task != null
        task instanceof DockerPublishTask
    }

    def "task extends DefaultTask"() {
        expect:
        task instanceof org.gradle.api.DefaultTask
    }

    def "task has required properties"() {
        expect:
        task.hasProperty('imageName')
        task.hasProperty('publishTargets')
        task.hasProperty('imageIdFile')
        task.hasProperty('dockerService')
    }

    // ===== NO TARGETS TEST =====

    def "should skip publish when no targets configured"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        task.publishTargets.set([])
        
        when:
        task.publishImage()
        
        then:
        0 * mockDockerService.pushImage(_, _)
    }

    // ===== SINGLE TARGET TESTS =====

    def "should publish single target with image name"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        def target = createPublishTarget(['v1.0.0', 'latest'])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:v1.0.0']) >> CompletableFuture.completedFuture(null)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:v1.0.0', null) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    def "should handle repository format"() {
        given:
        task.repository.set('mycompany/myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        def target = createPublishTargetRepository(['v1.0.0'])
        task.publishTargets.set([target])

        and:
        mockDockerService.imageExists('mycompany/myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('mycompany/myapp:latest', ['localhost:5000/mycompany/myapp:v1.0.0']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/mycompany/myapp:v1.0.0', null) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    // ===== MULTIPLE TARGETS TESTS =====

    def "should handle multiple publish targets"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        def target1 = createPublishTarget(['v1.0.0'])
        def target2 = createPublishTargetDockerHub(['latest'])
        task.publishTargets.set([target1, target2])
        
        and:
        mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:v1.0.0']) >> CompletableFuture.completedFuture(null)
        mockDockerService.tagImage('myapp:latest', ['docker.io/myapp:latest']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:v1.0.0', null) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('docker.io/myapp:latest', null) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    def "should handle multiple tags per target"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        def target = createPublishTarget(['v1.0.0', 'v1.0.1', 'latest'])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:v1.0.0']) >> CompletableFuture.completedFuture(null)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:v1.0.1']) >> CompletableFuture.completedFuture(null)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:v1.0.0', null) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:v1.0.1', null) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    // ===== AUTHENTICATION TESTS =====

    def "should publish with authentication"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        def authSpec = createAuthSpec('testuser', 'testpass', null)
        def target = createPublishTargetWithAuth(['latest'], authSpec)
        task.publishTargets.set([target])
        
        and:
        mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:latest', { AuthConfig auth ->
            auth.username == 'testuser' && auth.password == 'testpass'
        }) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    def "should publish with token authentication"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        def authSpec = createAuthSpec(null, null, 'test-token')
        def target = createPublishTargetWithAuth(['latest'], authSpec)
        task.publishTargets.set([target])
        
        and:
        mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:latest', { AuthConfig auth ->
            auth.registryToken == 'test-token'
        }) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    // ===== ERROR HANDLING TESTS =====

    def "should fail when no image reference can be built"() {
        given:
        task.version.set('1.0.0')
        task.tags.set([])  // Empty tags
        def target = createPublishTarget(['latest'])
        task.publishTargets.set([target])
        
        when:
        task.publishImage()
        
        then:
        thrown(IllegalStateException)
    }

    def "should handle Docker service exception"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        def target = createPublishTarget(['latest'])
        task.publishTargets.set([target])
        def serviceException = new DockerServiceException(
            DockerServiceException.ErrorType.PUSH_FAILED,
            'Push failed: authentication required',
            'Check registry credentials'
        )
        
        and:
        mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> CompletableFuture.failedFuture(serviceException)
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(GradleException)
        e.message.contains('Docker publish failed')
        e.message.contains('authentication required')
        e.message.contains('Check registry credentials')
    }

    def "should handle generic push exception"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        def target = createPublishTarget(['latest'])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> CompletableFuture.failedFuture(new RuntimeException('Network error'))
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(GradleException)
        e.message.contains('Docker publish failed: Network error')
    }

    def "should fail when source image does not exist"() {
        given:
        task.imageName.set('nonexistent')
        task.tags.set(['latest'])
        def target = createPublishTarget(['latest'])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.imageExists('nonexistent:latest') >> CompletableFuture.completedFuture(false)
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("Source image 'nonexistent:latest' does not exist")
        e.message.contains("Build the image first")
        
        // Verify no tag or push operations were attempted
        0 * mockDockerService.tagImage(_, _)
        0 * mockDockerService.pushImage(_, _)
    }

    def "should fail immediately when tag operation fails"() {
        given:
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        def target = createPublishTarget(['latest'])
        task.publishTargets.set([target])
        def tagException = new DockerServiceException(
            DockerServiceException.ErrorType.TAG_FAILED,
            'No such image: myapp:latest'
        )
        
        and:
        mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> 
            CompletableFuture.failedFuture(tagException)
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(GradleException)
        e.message.contains('Docker publish failed')
        e.message.contains('No such image: myapp:latest')
        
        // Verify push was never attempted since tag failed
        0 * mockDockerService.pushImage(_, _)
    }

    def "should complete successfully when both tag and push succeed"() {
        given:
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        def target = createPublishTarget(['latest'])
        task.publishTargets.set([target])
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
        
        // Mock setup and verification in then block
        1 * mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> 
            CompletableFuture.completedFuture(null)
        1 * mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> 
            CompletableFuture.completedFuture(null)
    }

    def "should handle tag failure and not attempt push"() {
        given:
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        def target = createPublishTarget(['latest'])
        task.publishTargets.set([target])
        def tagException = new DockerServiceException(
            DockerServiceException.ErrorType.TAG_FAILED,
            'Tag operation failed: Status 404: {"message":"No such image: myapp:latest"}'
        )
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(GradleException)
        e.message.contains('Docker publish failed')
        e.message.contains('Tag operation failed')
        
        // Mock setup and verification in then block
        1 * mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> 
            CompletableFuture.failedFuture(tagException)
        0 * mockDockerService.pushImage(_, _)
    }

    def "should handle push failure after successful tag"() {
        given:
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        def target = createPublishTarget(['latest'])
        task.publishTargets.set([target])
        def pushException = new DockerServiceException(
            DockerServiceException.ErrorType.PUSH_FAILED,
            'Push failed: authentication required'
        )
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(GradleException)
        e.message.contains('Docker publish failed')
        e.message.contains('authentication required')
        
        // Mock setup and verification in then block
        1 * mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> 
            CompletableFuture.completedFuture(null)
        1 * mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> 
            CompletableFuture.failedFuture(pushException)
    }

    // ===== NOMENCLATURE TESTS =====

    def "buildSourceImageReference with imageName format"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest', 'v1.0.0'])

        when:
        def sourceRef = task.buildSourceImageReference()

        then:
        sourceRef == 'myapp:latest'
    }

    def "buildSourceImageReference with repository format"() {
        given:
        task.repository.set('mycompany/myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])

        when:
        def sourceRef = task.buildSourceImageReference()

        then:
        sourceRef == 'mycompany/myapp:latest'
    }

    def "buildSourceImageReference with registry and namespace"() {
        given:
        task.registry.set('my-registry.com')
        task.namespace.set('mycompany')
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])

        when:
        def sourceRef = task.buildSourceImageReference()

        then:
        sourceRef == 'my-registry.com/mycompany/myapp:latest'
    }

    def "buildSourceImageReference returns null when no tags"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set([])

        when:
        def sourceRef = task.buildSourceImageReference()

        then:
        sourceRef == null
    }

    def "buildTargetImageReferences with target nomenclature"() {
        given:
        task.imageName.set('myapp')
        def target = createPublishTarget(['v1.0.0', 'latest'])

        when:
        def targetRefs = task.buildTargetImageReferences(target)

        then:
        targetRefs == ['localhost:5000/myapp:v1.0.0', 'localhost:5000/myapp:latest']
    }

    // ===== ENVIRONMENT VARIABLE VALIDATION TESTS =====

    def "validateAuthenticationCredentials succeeds when credentials are valid"() {
        given:
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.registry.set('docker.io')
        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.username.set("testuser")
        authSpec.password.set("testpass")

        when:
        task.validateAuthenticationCredentials(target, authSpec)

        then:
        noExceptionThrown()
    }

    def "validateAuthenticationCredentials fails when username is empty"() {
        given:
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.registry.set('docker.io')
        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.username.set("")
        authSpec.password.set("testpass")

        when:
        task.validateAuthenticationCredentials(target, authSpec)

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Authentication username is empty for registry 'docker.io' in target 'test'")
        exception.message.contains("Ensure your username environment variable contains a valid value")
    }

    def "validateAuthenticationCredentials fails when password is empty"() {
        given:
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.registry.set('ghcr.io')
        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.username.set("testuser")
        authSpec.password.set("")

        when:
        task.validateAuthenticationCredentials(target, authSpec)

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Authentication password/token is empty for registry 'ghcr.io' in target 'test'")
        exception.message.contains("Ensure your password/token environment variable contains a valid value")
    }

    def "getEffectiveRegistry returns registry when explicitly set"() {
        given:
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.registry.set('docker.io')

        when:
        def registry = task.getEffectiveRegistry(target)

        then:
        registry == 'docker.io'
    }

    def "getEffectiveRegistry extracts registry from repository"() {
        given:
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.repository.set('ghcr.io/user/app')

        when:
        def registry = task.getEffectiveRegistry(target)

        then:
        registry == 'ghcr.io'
    }

    def "getEffectiveRegistry returns unknown-registry as fallback"() {
        given:
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.repository.set('user/app') // No registry part

        when:
        def registry = task.getEffectiveRegistry(target)

        then:
        registry == 'unknown-registry'
    }

    def "getExampleEnvironmentVariables provides Docker Hub suggestions"() {
        when:
        def examples = task.getExampleEnvironmentVariables('docker.io')

        then:
        examples.username.contains('DOCKERHUB_USERNAME')
        examples.username.contains('DOCKER_USERNAME')
        examples.password.contains('DOCKERHUB_TOKEN')
        examples.password.contains('DOCKER_TOKEN')
    }

    def "getExampleEnvironmentVariables provides GitHub suggestions"() {
        when:
        def examples = task.getExampleEnvironmentVariables('ghcr.io')

        then:
        examples.username.contains('GHCR_USERNAME')
        examples.username.contains('GITHUB_USERNAME')
        examples.password.contains('GHCR_TOKEN')
        examples.password.contains('GITHUB_TOKEN')
    }

    def "getExampleEnvironmentVariables provides localhost suggestions"() {
        when:
        def examples = task.getExampleEnvironmentVariables('localhost:5000')

        then:
        examples.username.contains('REGISTRY_USERNAME')
        examples.username.contains('LOCAL_USERNAME')
        examples.password.contains('REGISTRY_PASSWORD')
        examples.password.contains('LOCAL_PASSWORD')
    }

    def "getExampleEnvironmentVariables provides generic suggestions for custom registry"() {
        when:
        def examples = task.getExampleEnvironmentVariables('my-company.com')

        then:
        examples.username.contains('REGISTRY_USERNAME')
        examples.username.contains('MY_COMPANY_COM_USERNAME')
        examples.password.contains('REGISTRY_TOKEN')
        examples.password.contains('MY_COMPANY_COM_TOKEN')
    }

    def "publish calls validation methods"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        def target = createPublishTargetWithValidation(['latest'])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.imageExists('myapp:latest') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('myapp:latest', ['docker.io/myapp:latest']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('docker.io/myapp:latest', null) >> CompletableFuture.completedFuture(null)

        when:
        task.publish()

        then:
        noExceptionThrown() // All validations should pass
    }

    // ===== HELPER METHODS =====

    private PublishTarget createPublishTarget(List<String> publishTags) {
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.registry.set('localhost:5000')
        target.imageName.set('myapp')
        target.publishTags.set(publishTags)
        return target
    }
    
    private PublishTarget createPublishTargetRepository(List<String> publishTags) {
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.registry.set('localhost:5000')
        target.repository.set('mycompany/myapp')
        target.publishTags.set(publishTags)
        return target
    }
    
    private PublishTarget createPublishTargetDockerHub(List<String> publishTags) {
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.registry.set('docker.io')
        target.imageName.set('myapp')
        target.publishTags.set(publishTags)
        return target
    }
    
    private PublishTarget createPublishTargetWithAuth(List<String> publishTags, AuthSpec authSpec) {
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.registry.set('localhost:5000')
        target.imageName.set('myapp')
        target.publishTags.set(publishTags)
        target.auth.set(authSpec)
        return target
    }
    
    private AuthSpec createAuthSpec(String username, String password, String token) {
        def authSpec = project.objects.newInstance(AuthSpec)
        if (username) authSpec.username.set(username)
        if (password) authSpec.password.set(password)
        if (token) authSpec.registryToken.set(token)
        return authSpec
    }
    
    private PublishTarget createPublishTargetWithValidation(List<String> publishTags) {
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.registry.set('docker.io')
        target.imageName.set('myapp')
        target.publishTags.set(publishTags)
        return target
    }

    // ===== PULLSOURCEREF TESTS =====

    def "pullSourceRefIfNeeded does nothing when pullIfMissing is false"() {
        given:
        task.pullIfMissing.set(false)
        task.effectiveSourceRef.set('alpine:3.16')

        when:
        def method = DockerPublishTask.getDeclaredMethod('pullSourceRefIfNeeded')
        method.accessible = true
        method.invoke(task)

        then:
        0 * mockDockerService.imageExists(_)
        0 * mockDockerService.pullImage(_, _)
    }

    def "pullSourceRefIfNeeded pulls image when missing"() {
        given:
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('alpine:3.16')
        mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(false)
        mockDockerService.pullImage('alpine:3.16', null) >> CompletableFuture.completedFuture(null)

        when:
        def method = DockerPublishTask.getDeclaredMethod('pullSourceRefIfNeeded')
        method.accessible = true
        method.invoke(task)

        then:
        1 * mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(false)
        1 * mockDockerService.pullImage('alpine:3.16', null) >> CompletableFuture.completedFuture(null)
    }

    def "pullSourceRefIfNeeded skips pull when image exists"() {
        given:
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('alpine:3.16')
        mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(true)

        when:
        def method = DockerPublishTask.getDeclaredMethod('pullSourceRefIfNeeded')
        method.accessible = true
        method.invoke(task)

        then:
        1 * mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(true)
        0 * mockDockerService.pullImage(_, _)
    }

    def "pullSourceRefIfNeeded does nothing when effectiveSourceRef is empty"() {
        given:
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('')

        when:
        def method = DockerPublishTask.getDeclaredMethod('pullSourceRefIfNeeded')
        method.accessible = true
        method.invoke(task)

        then:
        0 * mockDockerService.imageExists(_)
        0 * mockDockerService.pullImage(_, _)
    }

    // ===== ADDITIONAL AUTH VALIDATION TESTS =====

    def "validateAuthenticationCredentials skips when authSpec is null"() {
        given:
        def target = createPublishTarget(['latest'])

        when:
        task.validateAuthenticationCredentials(target, null)

        then:
        noExceptionThrown()
    }

    def "getEffectiveRegistry returns registry from target when present"() {
        given:
        def target = createPublishTarget(['latest'])
        target.registry.set('my-registry.com')

        when:
        def registry = task.getEffectiveRegistry(target)

        then:
        registry == 'my-registry.com'
    }

    def "getEffectiveRegistry extracts registry from repository with port"() {
        given:
        def target = createPublishTarget(['latest'])
        target.registry.set('')
        target.repository.set('localhost:5000/myapp')

        when:
        def registry = task.getEffectiveRegistry(target)

        then:
        registry == 'localhost:5000'
    }

    def "getEffectiveRegistry returns unknown-registry when cannot be determined"() {
        given:
        def target = createPublishTarget(['latest'])
        target.registry.set('')
        target.repository.set('myapp')

        when:
        def registry = task.getEffectiveRegistry(target)

        then:
        registry == 'unknown-registry'
    }

    def "getExampleEnvironmentVariables returns Docker Hub examples"() {
        when:
        def examples = task.getExampleEnvironmentVariables('docker.io')

        then:
        examples.username.contains('DOCKERHUB_USERNAME')
        examples.password.contains('DOCKERHUB_TOKEN')
    }

    def "getExampleEnvironmentVariables returns GitHub Container Registry examples"() {
        when:
        def examples = task.getExampleEnvironmentVariables('ghcr.io')

        then:
        examples.username.contains('GHCR_USERNAME')
        examples.password.contains('GHCR_TOKEN')
    }

    def "getExampleEnvironmentVariables returns localhost examples"() {
        when:
        def examples = task.getExampleEnvironmentVariables('localhost:5000')

        then:
        examples.username.contains('REGISTRY_USERNAME')
        examples.password.contains('REGISTRY_PASSWORD')
    }

    def "getExampleEnvironmentVariables returns generic examples for unknown registry"() {
        when:
        def examples = task.getExampleEnvironmentVariables('my-custom-registry.com')

        then:
        examples.username.contains('MY_CUSTOM_REGISTRY_COM_USERNAME')
        examples.password.contains('MY_CUSTOM_REGISTRY_COM_TOKEN')
    }
}