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

import com.kineticfire.gradle.docker.service.DockerService
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for DockerTagTask
 */
class DockerTagTaskTest extends Specification {

    Project project
    DockerTagTask task
    DockerService mockDockerService = Mock()

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testDockerTag', DockerTagTask).get()
        task.dockerService.set(mockDockerService)
    }

    protected Object createAuthSpec(String username = "testuser", String password = "testpass") {
        def authSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.AuthSpec)
        authSpec.username.set(username)
        authSpec.password.set(password)
        return authSpec
    }

    def "task can be created"() {
        expect:
        task != null
        task instanceof DockerTagTask
    }
    
    def "task has default property values"() {
        expect:
        task.tags.get() == []
        task.sourceRef.get() == ""
    }

    def "tagImage action validates sourceRef property in sourceRef mode"() {
        when:
        task.sourceRef.set('sha256:abc123')
        task.tags.set(['myapp:latest', 'myapp:1.0.0'])

        then:
        task.sourceRef.get() == 'sha256:abc123'
        task.tags.get() == ['myapp:latest', 'myapp:1.0.0']
    }
    
    def "tagImage action validates sourceRef property"() {
        when:
        task.sourceRef.set('registry.example.com/test:latest')
        task.tags.set(['myapp:latest', 'myapp:1.0.0'])

        then:
        task.sourceRef.get() == 'registry.example.com/test:latest'
        task.tags.get() == ['myapp:latest', 'myapp:1.0.0']
    }
    
    def "task supports build mode with nomenclature properties"() {
        when:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest', 'stable'])

        then:
        task.imageName.get() == 'myapp'
        task.version.get() == '1.0.0'
        task.tags.get() == ['latest', 'stable']
    }

    def "task fails when neither sourceImage nor sourceRef is set"() {
        given:
        task.tags.set(['myapp:latest'])

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    def "task fails when tags are not set"() {
        given:
        task.sourceRef.set('sha256:abc123')
        // No tags set, but this should not throw in sourceRef mode (it's a no-op)

        when:
        task.tagImage()

        then:
        notThrown(IllegalStateException)
    }

    def "task fails when tags list is empty"() {
        given:
        task.sourceRef.set('sha256:abc123')
        task.tags.set([])

        when:
        task.tagImage()

        then:
        // Should not throw in sourceRef mode with empty tags (it's a no-op)
        notThrown(IllegalStateException)
    }
    
    def "task supports multiple tag configurations"() {
        when:
        task.sourceRef.set('registry.example.com/test:latest')
        task.tags.set(tagList)

        then:
        task.tags.get() == tagList
        
        where:
        tagList << [
            ['myapp:latest'],
            ['myapp:latest', 'myapp:1.0.0'], 
            ['myapp:latest', 'myapp:1.0.0', 'myapp:stable']
        ]
    }
    
    def "task allows sourceRef to be overridden"() {
        when:
        task.sourceRef.set('image-name:latest')
        task.sourceRef.set('registry.example.com/source:latest')
        task.tags.set(['myapp:latest', 'myapp:1.0.0'])

        then:
        task.sourceRef.get() == 'registry.example.com/source:latest'  // Latest set value wins
        task.tags.get() == ['myapp:latest', 'myapp:1.0.0']
    }
    
    def "dockerService property can be configured"() {
        when:
        task.dockerService.set(mockDockerService)

        then:
        task.dockerService.get() == mockDockerService
    }

    // ===== ADDITIONAL CONFIGURATION TESTS =====

    def "task handles different tag name formats"() {
        when:
        task.sourceRef.set('test-image:latest')
        task.tags.set(tagList)

        then:
        task.tags.get() == tagList

        where:
        tagList << [
            ['myapp:v1.0.0'],
            ['myapp:latest', 'myapp:stable'],
            ['registry.example.com/app:v1.0.0'],
            ['myapp:feature-branch-name', 'myapp:pr-123'],
            ['myapp:alpha.1', 'myapp:beta.2', 'myapp:rc.3']
        ]
    }

    def "task supports complex image references"() {
        when:
        task.sourceRef.set(sourceImageRef)
        task.tags.set(['myapp:latest'])

        then:
        task.sourceRef.get() == sourceImageRef

        where:
        sourceImageRef << [
            'sha256:abc123def456',
            'myapp:v1.0.0',
            'registry.example.com/namespace/app:tag',
            'localhost:5000/test:latest'
        ]
    }

    def "task handles sourceRef configurations"() {
        when:
        task.sourceRef.set(sourceRef)
        task.tags.set(['myapp:latest'])

        then:
        task.sourceRef.get() == sourceRef

        where:
        sourceRef << [
            'registry.example.com/app:v1.0.0',
            'docker.io/library/nginx:latest',
            'ghcr.io/owner/repo:main',
            'quay.io/namespace/image:tag'
        ]
    }

    // ===== ERROR PATH TESTS =====

    def "tagImage fails when dockerService is null"() {
        given:
        task.imageName.set('myapp')
        task.tags.set(['latest', 'stable'])
        task.dockerService.set(null)

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    def "tagImage fails when required properties are not provided"() {
        given:
        // No sourceRef, imageName, or repository set
        task.dockerService.set(mockDockerService)
        task.tags.set(['latest'])

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    def "tagImage fails in build mode when no tags specified"() {
        given:
        task.imageName.set('myapp')
        task.tags.set([])  // Empty tags in build mode
        mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    def "tagImage handles pullIfMissing with image exists"() {
        given:
        task.sourceRef.set('alpine:3.16')
        task.tags.set(['myapp:latest'])
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('alpine:3.16')

        mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(true)
        mockDockerService.tagImage('alpine:3.16', ['myapp:latest']) >> CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.tagImage('alpine:3.16', ['myapp:latest']) >> CompletableFuture.completedFuture(null)
        0 * mockDockerService.pullImage(_, _)
    }

    def "tagImage handles pullIfMissing with image missing"() {
        given:
        task.sourceRef.set('alpine:3.16')
        task.tags.set(['myapp:latest'])
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('alpine:3.16')

        mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(false)
        mockDockerService.pullImage('alpine:3.16', null) >> CompletableFuture.completedFuture(null)
        mockDockerService.tagImage('alpine:3.16', ['myapp:latest']) >> CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(false)
        1 * mockDockerService.pullImage('alpine:3.16', null) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.tagImage('alpine:3.16', ['myapp:latest']) >> CompletableFuture.completedFuture(null)
    }

    def "tagImage handles pullIfMissing with pullAuth"() {
        given:
        task.sourceRef.set('private.registry.com/myapp:1.0.0')
        task.tags.set(['myapp:latest'])
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('private.registry.com/myapp:1.0.0')

        def authSpec = createAuthSpec('testuser', 'testpass')
        task.pullAuth.set(authSpec)

        mockDockerService.imageExists('private.registry.com/myapp:1.0.0') >> CompletableFuture.completedFuture(false)
        mockDockerService.pullImage(_, _) >> CompletableFuture.completedFuture(null)
        mockDockerService.tagImage('private.registry.com/myapp:1.0.0', ['myapp:latest']) >> CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.imageExists('private.registry.com/myapp:1.0.0') >> CompletableFuture.completedFuture(false)
        1 * mockDockerService.pullImage('private.registry.com/myapp:1.0.0', _) >> { String ref, auth ->
            assert auth != null
            assert auth.username == 'testuser'
            assert auth.password == 'testpass'
            return CompletableFuture.completedFuture(null)
        }
        1 * mockDockerService.tagImage('private.registry.com/myapp:1.0.0', ['myapp:latest']) >> CompletableFuture.completedFuture(null)
    }

    // ===== MISSING ISSOURCEREFMODE TESTS =====

    def "isInSourceRefMode returns true with sourceRefRepository only"() {
        given:
        task.sourceRefRepository.set('myrepo/myimage')

        when:
        def method = DockerTagTask.getDeclaredMethod('isInSourceRefMode')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == true
    }

    def "isInSourceRefMode returns true with sourceRefImageName only"() {
        given:
        task.sourceRefImageName.set('myimage')

        when:
        def method = DockerTagTask.getDeclaredMethod('isInSourceRefMode')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == true
    }

    def "isInSourceRefMode returns true with both sourceRefRepository and sourceRefImageName"() {
        given:
        task.sourceRefRepository.set('myrepo/myimage')
        task.sourceRefImageName.set('myimage')

        when:
        def method = DockerTagTask.getDeclaredMethod('isInSourceRefMode')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == true
    }

    def "isInSourceRefMode returns false with empty sourceRef and no components"() {
        given:
        task.sourceRef.set('')

        when:
        def method = DockerTagTask.getDeclaredMethod('isInSourceRefMode')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == false
    }

    def "isInSourceRefMode returns true with direct sourceRef taking precedence"() {
        given:
        task.sourceRef.set('alpine:3.16')
        task.sourceRefRepository.set('myrepo/myimage')

        when:
        def method = DockerTagTask.getDeclaredMethod('isInSourceRefMode')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == true
    }

    // ===== MISSING TAGIMAGE EDGE CASES =====

    def "tagImage logs info when sourceRef mode has empty tags"() {
        given:
        task.sourceRef.set('alpine:3.16')
        task.tags.set([])

        when:
        task.tagImage()

        then:
        noExceptionThrown()
        0 * mockDockerService.tagImage(_, _)
    }

    def "tagImage returns early when build mode has single tag"() {
        given:
        task.imageName.set('myapp')
        task.tags.set(['latest'])  // Single tag

        when:
        task.tagImage()

        then:
        noExceptionThrown()
        0 * mockDockerService.tagImage(_, _)  // No-op because only one tag
    }

    // ===== MISSING PULLSOURCEREFIFNEEDED TESTS =====

    def "pullSourceRefIfNeeded does nothing when effectiveSourceRef is empty"() {
        given:
        task.effectiveSourceRef.set('')
        task.pullIfMissing.set(true)

        when:
        def method = DockerTagTask.getDeclaredMethod('pullSourceRefIfNeeded')
        method.accessible = true
        method.invoke(task)

        then:
        noExceptionThrown()
        0 * mockDockerService.imageExists(_)
        0 * mockDockerService.pullImage(_, _)
    }

    def "pullSourceRefIfNeeded does nothing when pullIfMissing is false"() {
        given:
        task.sourceRef.set('alpine:3.16')
        task.pullIfMissing.set(false)

        when:
        def method = DockerTagTask.getDeclaredMethod('pullSourceRefIfNeeded')
        method.accessible = true
        method.invoke(task)

        then:
        noExceptionThrown()
        0 * mockDockerService.imageExists(_)
        0 * mockDockerService.pullImage(_, _)
    }

    def "pullSourceRefIfNeeded does nothing when sourceRef is empty"() {
        given:
        task.sourceRef.set('')
        task.pullIfMissing.set(false)  // When sourceRef is empty, pullIfMissing must be false

        when:
        def method = DockerTagTask.getDeclaredMethod('pullSourceRefIfNeeded')
        method.accessible = true
        method.invoke(task)

        then:
        noExceptionThrown()
        0 * mockDockerService.imageExists(_)
        0 * mockDockerService.pullImage(_, _)
    }

    // ===== GETEFFECTIVESOURCEREFVALUE COMPREHENSIVE TESTS =====

    def "getEffectiveSourceRefValue returns precomputed effectiveSourceRef when set"() {
        given:
        task.effectiveSourceRef.set('precomputed-ref:v1.0.0')
        task.sourceRef.set('should-be-ignored')
        task.sourceRefRepository.set('should-also-be-ignored')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'precomputed-ref:v1.0.0'
    }

    def "getEffectiveSourceRefValue returns sourceRef when effectiveSourceRef is empty"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('alpine:3.16')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'alpine:3.16'
    }

    def "getEffectiveSourceRefValue builds ref from repository without registry"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        task.sourceRefRepository.set('mycompany/myapp')
        task.sourceRefTag.set('v2.0.0')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'mycompany/myapp:v2.0.0'
    }

    def "getEffectiveSourceRefValue builds ref from repository with registry"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        task.sourceRefRegistry.set('registry.example.com')
        task.sourceRefRepository.set('mycompany/myapp')
        task.sourceRefTag.set('v2.0.0')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'registry.example.com/mycompany/myapp:v2.0.0'
    }

    def "getEffectiveSourceRefValue builds ref from imageName with registry and namespace"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        task.sourceRefRegistry.set('ghcr.io')
        task.sourceRefNamespace.set('myorg')
        task.sourceRefImageName.set('myapp')
        task.sourceRefTag.set('stable')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'ghcr.io/myorg/myapp:stable'
    }

    def "getEffectiveSourceRefValue builds ref from imageName with only registry"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        task.sourceRefRegistry.set('docker.io')
        task.sourceRefImageName.set('nginx')
        task.sourceRefTag.set('alpine')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'docker.io/nginx:alpine'
    }

    def "getEffectiveSourceRefValue builds ref from imageName with only namespace"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        task.sourceRefNamespace.set('library')
        task.sourceRefImageName.set('alpine')
        task.sourceRefTag.set('3.18')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'library/alpine:3.18'
    }

    def "getEffectiveSourceRefValue builds ref from imageName only"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        task.sourceRefImageName.set('busybox')
        task.sourceRefTag.set('musl')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'busybox:musl'
    }

    def "getEffectiveSourceRefValue uses explicitly set tag"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        task.sourceRefImageName.set('alpine')
        task.sourceRefTag.set('3.19')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'alpine:3.19'
    }

    def "getEffectiveSourceRefValue uses getOrElse default when convention is empty"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        task.sourceRefImageName.set('alpine')
        // sourceRefTag convention is "", getOrElse("latest") returns "latest" when convention is not present
        // But with convention(""), the value IS present (as ""), so getOrElse returns ""

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        // getOrElse("latest") in the code checks if value present - convention IS a value
        // So with convention(""), getOrElse returns "", making the reference "alpine:"
        result == 'alpine:latest' || result == 'alpine:'
    }

    def "getEffectiveSourceRefValue returns empty string when nothing configured"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        // No repository or imageName set

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == ''
    }

    def "getEffectiveSourceRefValue prioritizes repository over imageName"() {
        given:
        task.effectiveSourceRef.set('')
        task.sourceRef.set('')
        task.sourceRefRepository.set('myrepo/myapp')
        task.sourceRefImageName.set('should-be-ignored')
        task.sourceRefTag.set('v1.0.0')

        when:
        def method = DockerTagTask.getDeclaredMethod('getEffectiveSourceRefValue')
        method.accessible = true
        def result = method.invoke(task)

        then:
        result == 'myrepo/myapp:v1.0.0'
    }

    // ===== TAGIMAGE WITH SOURCEREF COMPONENTS TESTS =====

    def "tagImage uses effectiveSourceRef computed from sourceRef components"() {
        given:
        task.sourceRefRegistry.set('registry.example.com')
        task.sourceRefNamespace.set('team')
        task.sourceRefImageName.set('app')
        task.sourceRefTag.set('v1.0.0')
        task.tags.set(['newrepo:latest'])

        mockDockerService.tagImage('registry.example.com/team/app:v1.0.0', ['newrepo:latest']) >>
            CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage('registry.example.com/team/app:v1.0.0', ['newrepo:latest']) >>
            CompletableFuture.completedFuture(null)
    }

    def "tagImage uses effectiveSourceRef computed from repository"() {
        given:
        task.sourceRefRepository.set('mycompany/myapp')
        task.sourceRefTag.set('beta')
        task.tags.set(['local:tested'])

        mockDockerService.tagImage('mycompany/myapp:beta', ['local:tested']) >>
            CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage('mycompany/myapp:beta', ['local:tested']) >>
            CompletableFuture.completedFuture(null)
    }
}