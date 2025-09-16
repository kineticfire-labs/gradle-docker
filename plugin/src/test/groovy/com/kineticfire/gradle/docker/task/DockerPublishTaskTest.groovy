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
        task.group == 'docker'
        task.description == 'Publishes Docker image to configured registries'
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

    def "task has publishImage action"() {
        expect:
        task.actions.size() == 1
        task.actions[0].displayName.contains('publishImage')
    }

    // ===== NO TARGETS TEST =====

    def "should skip publish when no targets configured"() {
        given:
        task.imageName.set('test:latest')
        task.publishTargets.set([])
        
        when:
        task.publishImage()
        
        then:
        0 * mockDockerService.pushImage(_, _)
    }

    // ===== SINGLE TARGET TESTS =====

    def "should publish single target with image name"() {
        given:
        task.imageName.set('test:latest')
        def target = createPublishTarget(['localhost:5000/myapp:v1.0', 'localhost:5000/myapp:latest'])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.pushImage('localhost:5000/myapp:v1.0', null) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    def "should publish single target with default latest tag"() {
        given:
        task.imageName.set('test:latest')
        def target = createPublishTarget([])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    def "should read image ID from file when image name not set"() {
        given:
        def imageIdFile = project.layout.buildDirectory.file('image-id.txt').get().asFile
        imageIdFile.parentFile.mkdirs()
        imageIdFile.text = 'sha256:abcd1234'
        task.imageIdFile.set(project.layout.buildDirectory.file('image-id.txt'))
        def target = createPublishTarget(['localhost:5000/myapp:latest'])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    // ===== MULTIPLE TARGETS TESTS =====

    def "should handle multiple publish targets"() {
        given:
        task.imageName.set('test:latest')
        def target1 = createPublishTarget(['localhost:5000/myapp:v1.0'])
        def target2 = createPublishTarget(['docker.io/myapp:latest'])
        task.publishTargets.set([target1, target2])
        
        and:
        mockDockerService.pushImage('localhost:5000/myapp:v1.0', null) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage('docker.io/myapp:latest', null) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    def "should handle multiple tags per target"() {
        given:
        task.imageName.set('test:latest')
        def target = createPublishTarget(['localhost:5000/myapp:v1.0', 'localhost:5000/myapp:v1.0.1', 'localhost:5000/myapp:latest'])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.pushImage('localhost:5000/myapp:v1.0', null) >> CompletableFuture.completedFuture(null)
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
        task.imageName.set('test:latest')
        def authSpec = createAuthSpec('testuser', 'testpass', null)
        def target = createPublishTargetWithAuth(['localhost:5000/myapp:latest'], authSpec)
        task.publishTargets.set([target])
        
        and:
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
        task.imageName.set('test:latest')
        def authSpec = createAuthSpec(null, null, 'test-token')
        def target = createPublishTargetWithAuth(['localhost:5000/myapp:latest'], authSpec)
        task.publishTargets.set([target])
        
        and:
        mockDockerService.pushImage('localhost:5000/myapp:latest', { AuthConfig auth ->
            auth.registryToken == 'test-token'
        }) >> CompletableFuture.completedFuture(null)
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
    }

    // ===== ERROR HANDLING TESTS =====

    def "should fail when no image name and no image ID file"() {
        given:
        def target = createPublishTarget(['localhost:5000/myapp:latest'])
        task.publishTargets.set([target])
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(GradleException)
        e.message.contains('No image name specified and no image ID file found')
    }

    def "should fail when image ID file does not exist"() {
        given:
        task.imageIdFile.set(project.layout.buildDirectory.file('nonexistent.txt'))
        def target = createPublishTarget(['localhost:5000/myapp:latest'])
        task.publishTargets.set([target])
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(GradleException)
        e.message.contains('No image name specified and no image ID file found')
    }

    def "should handle Docker service exception"() {
        given:
        task.imageName.set('test:latest')
        def target = createPublishTarget(['localhost:5000/myapp:latest'])
        task.publishTargets.set([target])
        def serviceException = new DockerServiceException(
            DockerServiceException.ErrorType.PUSH_FAILED,
            'Push failed: authentication required',
            'Check registry credentials'
        )
        
        and:
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
        task.imageName.set('test:latest')
        def target = createPublishTarget(['localhost:5000/myapp:latest'])
        task.publishTargets.set([target])
        
        and:
        mockDockerService.pushImage('localhost:5000/myapp:latest', null) >> CompletableFuture.failedFuture(new RuntimeException('Network error'))
        
        when:
        task.publishImage()
        
        then:
        def e = thrown(GradleException)
        e.message.contains('Docker publish failed: Network error')
    }

    // ===== HELPER METHODS =====

    private PublishTarget createPublishTarget(List<String> fullImageReferences) {
        def target = project.objects.newInstance(PublishTarget, "test", project)
        target.tags.set(fullImageReferences)
        return target
    }
    
    private PublishTarget createPublishTargetWithAuth(List<String> fullImageReferences, AuthSpec authSpec) {
        def target = project.objects.newInstance(PublishTarget, "test", project)
        target.tags.set(fullImageReferences)
        target.auth.set(authSpec)
        return target
    }
    
    private AuthSpec createAuthSpec(String username, String password, String token) {
        def authSpec = project.objects.newInstance(AuthSpec, project)
        if (username) authSpec.username.set(username)
        if (password) authSpec.password.set(password)
        if (token) authSpec.registryToken.set(token)
        return authSpec
    }
}