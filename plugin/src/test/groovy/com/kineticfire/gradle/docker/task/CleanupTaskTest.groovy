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

import com.kineticfire.gradle.docker.service.ComposeService
import com.kineticfire.gradle.docker.service.DockerService
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for CleanupTask
 */
class CleanupTaskTest extends Specification {

    @TempDir
    Path tempDir

    Project project
    CleanupTask task
    DockerService mockDockerService = Mock()
    ComposeService mockComposeService = Mock()

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        task = project.tasks.register('testCleanup', CleanupTask).get()
        task.dockerService.set(mockDockerService)
        task.composeService.set(mockComposeService)
    }

    // ===== BASIC TESTS =====

    def "task can be created"() {
        expect:
        task != null
        task instanceof CleanupTask
    }

    def "task has default property values"() {
        expect:
        task.removeContainers.get() == false
        task.removeNetworks.get() == false
        task.removeImages.get() == false
        task.imageNames.get() == []
        task.stackName.get() == ""
        task.containerNames.get() == []
        task.networkNames.get() == []
    }

    // ===== COMPOSE CLEANUP TESTS =====

    def "cleanup stops compose stack when stackName is set"() {
        given:
        task.stackName.set("my-stack")
        mockComposeService.downStack("my-stack") >> CompletableFuture.completedFuture(null)

        when:
        task.cleanup()

        then:
        1 * mockComposeService.downStack("my-stack") >> CompletableFuture.completedFuture(null)
    }

    def "cleanup does not call compose when stackName is empty"() {
        given:
        task.stackName.set("")

        when:
        task.cleanup()

        then:
        0 * mockComposeService.downStack(_)
    }

    def "cleanup handles compose down failure gracefully"() {
        given:
        task.stackName.set("failing-stack")
        mockComposeService.downStack("failing-stack") >> {
            throw new RuntimeException("Compose down failed")
        }

        when:
        task.cleanup()

        then:
        // Should not throw - cleanup should be best-effort
        noExceptionThrown()
    }

    // ===== PROPERTY CONFIGURATION TESTS =====

    def "task allows property configuration"() {
        when:
        task.removeContainers.set(true)
        task.removeNetworks.set(true)
        task.removeImages.set(true)
        task.stackName.set("test-stack")
        task.imageNames.set(["image1", "image2"])
        task.containerNames.set(["container1"])
        task.networkNames.set(["network1"])

        then:
        task.removeContainers.get() == true
        task.removeNetworks.get() == true
        task.removeImages.get() == true
        task.stackName.get() == "test-stack"
        task.imageNames.get() == ["image1", "image2"]
        task.containerNames.get() == ["container1"]
        task.networkNames.get() == ["network1"]
    }

    def "task allows adding items incrementally"() {
        when:
        task.imageNames.add("image1")
        task.imageNames.add("image2")
        task.containerNames.add("container1")
        task.networkNames.add("network1")

        then:
        task.imageNames.get() == ["image1", "image2"]
        task.containerNames.get() == ["container1"]
        task.networkNames.get() == ["network1"]
    }

    // ===== SERVICE CONFIGURATION TESTS =====

    def "dockerService property can be configured"() {
        when:
        task.dockerService.set(mockDockerService)

        then:
        task.dockerService.get() == mockDockerService
    }

    def "composeService property can be configured"() {
        when:
        task.composeService.set(mockComposeService)

        then:
        task.composeService.get() == mockComposeService
    }

    // ===== CLEANUP WITHOUT SERVICES TESTS =====

    def "cleanup runs without error when no services configured"() {
        given:
        def newTask = project.tasks.register('noServicesCleanup', CleanupTask).get()
        // No services set

        when:
        newTask.cleanup()

        then:
        noExceptionThrown()
    }

    // ===== CONTAINER CLEANUP TESTS =====

    def "cleanup logs container removal when enabled"() {
        given:
        task.removeContainers.set(true)
        task.containerNames.set(["container1", "container2"])

        when:
        task.cleanup()

        then:
        // Currently a placeholder - validates the configuration path
        noExceptionThrown()
    }

    def "cleanup skips container removal when disabled"() {
        given:
        task.removeContainers.set(false)
        task.containerNames.set(["container1"])

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    // ===== NETWORK CLEANUP TESTS =====

    def "cleanup logs network removal when enabled"() {
        given:
        task.removeNetworks.set(true)
        task.networkNames.set(["network1", "network2"])

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    def "cleanup skips network removal when disabled"() {
        given:
        task.removeNetworks.set(false)
        task.networkNames.set(["network1"])

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    // ===== IMAGE CLEANUP TESTS =====

    def "cleanup logs image removal when enabled"() {
        given:
        task.removeImages.set(true)
        task.imageNames.set(["image1:latest", "image2:v1"])

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    def "cleanup skips image removal when disabled"() {
        given:
        task.removeImages.set(false)
        task.imageNames.set(["image1"])

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    // ===== COMBINED CLEANUP TESTS =====

    def "cleanup handles multiple operations"() {
        given:
        task.stackName.set("test-stack")
        task.removeContainers.set(true)
        task.containerNames.set(["container1"])
        task.removeNetworks.set(true)
        task.networkNames.set(["network1"])
        task.removeImages.set(true)
        task.imageNames.set(["image1"])

        mockComposeService.downStack("test-stack") >> CompletableFuture.completedFuture(null)

        when:
        task.cleanup()

        then:
        1 * mockComposeService.downStack("test-stack") >> CompletableFuture.completedFuture(null)
        noExceptionThrown()
    }

    def "cleanup completes even when compose fails"() {
        given:
        task.stackName.set("failing-stack")
        task.removeContainers.set(true)
        task.containerNames.set(["container1"])

        mockComposeService.downStack("failing-stack") >> {
            throw new RuntimeException("Compose failed")
        }

        when:
        task.cleanup()

        then:
        // Should complete without throwing
        noExceptionThrown()
    }

    // ===== EMPTY LIST TESTS =====

    def "cleanup handles empty container list"() {
        given:
        task.removeContainers.set(true)
        task.containerNames.set([])

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    def "cleanup handles empty network list"() {
        given:
        task.removeNetworks.set(true)
        task.networkNames.set([])

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    def "cleanup handles empty image list"() {
        given:
        task.removeImages.set(true)
        task.imageNames.set([])

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    // ===== COMPOSE SERVICE ABSENCE TESTS =====

    def "cleanup handles missing compose service gracefully"() {
        given:
        def newTask = project.tasks.register('noComposeTask', CleanupTask).get()
        newTask.stackName.set("test-stack")
        // composeService not set

        when:
        newTask.cleanup()

        then:
        noExceptionThrown()
    }
}
