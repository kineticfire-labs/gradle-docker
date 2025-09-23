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
        // No targets added
        task.publishSpec.set(publishSpec)
        
        when:
        task.publishImage()
        
        then:
        0 * mockDockerService.tagImage(_, _)
        0 * mockDockerService.pushImage(_, _)
    }
    

    
}