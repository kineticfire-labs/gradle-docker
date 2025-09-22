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
        mockDockerService.tagImage('myapp:latest', ['localhost:5000/myapp:latest']) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> CompletableFuture.failedFuture(new RuntimeException('Network error'))
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(GradleException)
        e.message.contains('Docker publish failed: Network error')
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
}