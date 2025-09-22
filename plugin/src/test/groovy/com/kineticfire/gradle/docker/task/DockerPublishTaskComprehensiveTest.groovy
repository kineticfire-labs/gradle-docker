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

import com.kineticfire.gradle.docker.base.DockerTaskTestBase
import com.kineticfire.gradle.docker.exception.DockerServiceException
import com.kineticfire.gradle.docker.model.AuthConfig
import com.kineticfire.gradle.docker.spec.AuthSpec
import com.kineticfire.gradle.docker.spec.PublishSpec
import com.kineticfire.gradle.docker.spec.PublishTarget

import java.util.concurrent.CompletableFuture

/**
 * Comprehensive unit tests for DockerPublishTask
 */
class DockerPublishTaskComprehensiveTest extends DockerTaskTestBase {

    def "publishImage in build mode with basic authentication"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors and set up required ones
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)

        task.registry.set("docker.io")
        task.namespace.set("company")
        task.imageName.set("app")
        task.tags.set(["1.0.0", "latest"])

        def publishSpec = createPublishSpec()
        task.publishSpec.set(publishSpec)

        when:
        task.publishImage()

        then:
        // Should tag source image with target tag, then push - using wildcards to match any parameters
        1 * mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)
    }
    
    def "publishImage in sourceRef mode"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        // DockerPublishTask doesn't have sourceRef - it uses image nomenclature
        task.imageName.set("existing")
        task.tags.set(["image"])

        def publishSpec = createPublishSpec()
        task.publishSpec.set(publishSpec)

        when:
        task.publishImage()

        then:
        1 * mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)
    }
    
    def "publishImage with multiple publish targets"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.imageName.set("app")
        task.tags.set(["test"])
        
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        
        // Docker Hub target
        def dockerHubTarget = project.objects.newInstance(PublishTarget, "dockerhub", project.objects)
        dockerHubTarget.registry.set("docker.io")
        dockerHubTarget.namespace.set("myuser")
        dockerHubTarget.publishTags.set(["myuser/app:latest"])
        def dockerHubAuth = project.objects.newInstance(AuthSpec)
        dockerHubAuth.username.set("dockeruser")
        dockerHubAuth.password.set("dockerpass")
        dockerHubTarget.auth.set(dockerHubAuth)
        
        // GHCR target
        def ghcrTarget = project.objects.newInstance(PublishTarget, "ghcr", project.objects)
        ghcrTarget.registry.set("ghcr.io")
        ghcrTarget.namespace.set("myorg")
        ghcrTarget.publishTags.set(["ghcr.io/myorg/app:latest"])
        def ghcrAuth = project.objects.newInstance(AuthSpec)
        ghcrAuth.registryToken.set("ghp_token123")
        ghcrTarget.auth.set(ghcrAuth)
        
        publishSpec.to.add(dockerHubTarget)
        publishSpec.to.add(ghcrTarget)
        task.publishSpec.set(publishSpec)
        
        when:
        task.publishImage()
        
        then:
        2 * mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        2 * mockDockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)
    }
    
    def "publishImage with token authentication"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.imageName.set("app")
        task.tags.set(["test"])
        
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        def target = project.objects.newInstance(PublishTarget, "registry", project.objects)
        target.publishTags.set(["registry.io/app:test"])
        def auth = project.objects.newInstance(AuthSpec)
        auth.registryToken.set("bearer_token_xyz")
        target.auth.set(auth)
        publishSpec.to.add(target)
        task.publishSpec.set(publishSpec)
        
        when:
        task.publishImage()
        
        then:
        1 * mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)
    }
    
    def "publishImage with helper authentication"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.imageName.set("app")
        task.tags.set(["test"])
        
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        def target = project.objects.newInstance(PublishTarget, "ecr", project.objects)
        target.publishTags.set(["123456789012.dkr.ecr.us-west-2.amazonaws.com/app:test"])
        def auth = project.objects.newInstance(AuthSpec)
        auth.helper.set("ecr-login")
        target.auth.set(auth)
        publishSpec.to.add(target)
        task.publishSpec.set(publishSpec)
        
        when:
        task.publishImage()
        
        then:
        1 * mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)
    }
    
    def "publishImage without authentication"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.imageName.set("app")
        task.tags.set(["test"])
        
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        def target = project.objects.newInstance(PublishTarget, "public", project.objects)
        target.publishTags.set(["docker.io/library/app:test"])
        // No auth set
        publishSpec.to.add(target)
        task.publishSpec.set(publishSpec)
        
        when:
        task.publishImage()
        
        then:
        1 * mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)
    }
    
    def "publishImage with repository format"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.registry.set("registry.io")
        task.repository.set("company/application")
        task.tags.set(["1.0", "latest"])
        
        def publishSpec = createPublishSpec()
        task.publishSpec.set(publishSpec)

        when:
        task.publishImage()

        then:
        // Target references can't be built from default publishSpec, so no calls expected
        0 * mockDockerService.tagImage(_, _)
        0 * mockDockerService.pushImage(_, _)
    }

    def "publishImage validates publishSpec is present"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.imageName.set("app")
        task.tags.set(["test"])
        // No publishSpec set

        when:
        task.publishImage()

        then:
        // Task no longer throws IllegalStateException - it just logs and returns
        0 * mockDockerService.tagImage(_, _)
        0 * mockDockerService.pushImage(_, _)
    }
    
    def "publishImage validates source image exists"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        // No sourceRef, repository, or imageName set
        
        def publishSpec = createPublishSpec()
        task.publishSpec.set(publishSpec)
        
        when:
        task.publishImage()
        
        then:
        thrown(IllegalStateException)
        0 * mockDockerService.tagImage(_, _)
        0 * mockDockerService.pushImage(_, _)
    }
    
    def "publishImage handles authentication failure"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.imageName.set("app")
        task.tags.set(["test"])
        
        def publishSpec = createPublishSpec()
        task.publishSpec.set(publishSpec)
        
        def authError = new DockerServiceException(
            DockerServiceException.ErrorType.PUSH_FAILED,
            "Authentication failed"
        )
        
        when:
        task.publishImage()
        
        then:
        1 * mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.pushImage(_, _) >> CompletableFuture.failedFuture(authError)
        def ex = thrown(Exception)
        ex.cause == authError
    }
    
    def "publishImage handles network failure"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        // DockerPublishTask doesn't have sourceRef - it uses image nomenclature
        task.imageName.set("app")
        task.tags.set(["test"])
        
        def publishSpec = createPublishSpec()
        task.publishSpec.set(publishSpec)
        
        def networkError = new DockerServiceException(
            DockerServiceException.ErrorType.PUSH_FAILED,
            "Network unreachable"
        )
        
        when:
        task.publishImage()
        
        then:
        1 * mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.pushImage(_, _) >> CompletableFuture.failedFuture(networkError)
        def ex = thrown(Exception)
        ex.cause == networkError
    }
    
    def "publishImage with complex publish targets and tags"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.namespace.set("mycompany")
        task.imageName.set("myapp")
        task.tags.set(["1.0.0"])
        
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        
        // Target with multiple publish tags
        def target = project.objects.newInstance(PublishTarget, "multi", project.objects)
        target.publishTags.set([
            "registry1.io/mycompany/myapp:1.0.0",
            "registry1.io/mycompany/myapp:latest",
            "registry1.io/mycompany/myapp:stable",
            "backup.registry.io/mycompany/myapp:1.0.0"
        ])
        def auth = project.objects.newInstance(AuthSpec)
        auth.username.set("user")
        auth.password.set("pass")
        target.auth.set(auth)
        publishSpec.to.add(target)
        task.publishSpec.set(publishSpec)
        
        when:
        task.publishImage()
        
        then:
        4 * mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        4 * mockDockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)
    }
    
    def "publishImage with empty publish targets does nothing"() {
        given:
        def task = project.tasks.create("dockerPublishTest", DockerPublishTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.imageName.set("app")
        task.tags.set(["test"])
        
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        // No targets added
        task.publishSpec.set(publishSpec)
        
        when:
        task.publishImage()
        
        then:
        0 * mockDockerService.tagImage(_, _)
        0 * mockDockerService.pushImage(_, _)
    }
    
}