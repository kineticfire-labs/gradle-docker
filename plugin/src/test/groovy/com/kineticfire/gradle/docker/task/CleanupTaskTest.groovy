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

import org.gradle.api.tasks.UntrackedTask

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

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

    def "cleanup removes containers when enabled"() {
        given:
        task.removeContainers.set(true)
        task.containerNames.set(["container1", "container2"])
        mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeContainer("container2") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        1 * mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeContainer("container2") >> CompletableFuture.completedFuture(true)
    }

    def "cleanup skips container removal when disabled"() {
        given:
        task.removeContainers.set(false)
        task.containerNames.set(["container1"])

        when:
        task.cleanup()

        then:
        0 * mockDockerService.removeContainer(_)
    }
    
    def "cleanup handles container removal failure gracefully"() {
        given:
        task.removeContainers.set(true)
        task.containerNames.set(["container1"])
        mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(false)

        when:
        task.cleanup()

        then:
        1 * mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(false)
        noExceptionThrown()
    }
    
    def "cleanup handles container removal exception gracefully"() {
        given:
        task.removeContainers.set(true)
        task.containerNames.set(["container1"])
        def failingFuture = new CompletableFuture<Boolean>()
        failingFuture.completeExceptionally(new RuntimeException("Container removal failed"))
        mockDockerService.removeContainer("container1") >> failingFuture

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    // ===== NETWORK CLEANUP TESTS =====

    def "cleanup removes networks when enabled"() {
        given:
        task.removeNetworks.set(true)
        task.networkNames.set(["network1", "network2"])
        mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeNetwork("network2") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        1 * mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeNetwork("network2") >> CompletableFuture.completedFuture(true)
    }

    def "cleanup skips network removal when disabled"() {
        given:
        task.removeNetworks.set(false)
        task.networkNames.set(["network1"])

        when:
        task.cleanup()

        then:
        0 * mockDockerService.removeNetwork(_)
    }
    
    def "cleanup handles network removal failure gracefully"() {
        given:
        task.removeNetworks.set(true)
        task.networkNames.set(["network1"])
        mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(false)

        when:
        task.cleanup()

        then:
        1 * mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(false)
        noExceptionThrown()
    }
    
    def "cleanup handles network removal exception gracefully"() {
        given:
        task.removeNetworks.set(true)
        task.networkNames.set(["network1"])
        def failingFuture = new CompletableFuture<Boolean>()
        failingFuture.completeExceptionally(new RuntimeException("Network removal failed"))
        mockDockerService.removeNetwork("network1") >> failingFuture

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    // ===== IMAGE CLEANUP TESTS =====

    def "cleanup removes images when enabled"() {
        given:
        task.removeImages.set(true)
        task.imageNames.set(["image1:latest", "image2:v1"])
        mockDockerService.removeImage("image1:latest") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeImage("image2:v1") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        1 * mockDockerService.removeImage("image1:latest") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeImage("image2:v1") >> CompletableFuture.completedFuture(true)
    }

    def "cleanup skips image removal when disabled"() {
        given:
        task.removeImages.set(false)
        task.imageNames.set(["image1"])

        when:
        task.cleanup()

        then:
        0 * mockDockerService.removeImage(_)
    }
    
    def "cleanup handles image removal failure gracefully"() {
        given:
        task.removeImages.set(true)
        task.imageNames.set(["image1:latest"])
        mockDockerService.removeImage("image1:latest") >> CompletableFuture.completedFuture(false)

        when:
        task.cleanup()

        then:
        1 * mockDockerService.removeImage("image1:latest") >> CompletableFuture.completedFuture(false)
        noExceptionThrown()
    }
    
    def "cleanup handles image removal exception gracefully"() {
        given:
        task.removeImages.set(true)
        task.imageNames.set(["image1:latest"])
        def failingFuture = new CompletableFuture<Boolean>()
        failingFuture.completeExceptionally(new RuntimeException("Image removal failed"))
        mockDockerService.removeImage("image1:latest") >> failingFuture

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
        mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeImage("image1") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        1 * mockComposeService.downStack("test-stack") >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeImage("image1") >> CompletableFuture.completedFuture(true)
    }

    def "cleanup completes even when compose fails"() {
        given:
        task.stackName.set("failing-stack")
        task.removeContainers.set(true)
        task.containerNames.set(["container1"])

        mockComposeService.downStack("failing-stack") >> {
            throw new RuntimeException("Compose failed")
        }
        mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        // Compose fails but other cleanup operations should still proceed
        1 * mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)
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

    // ===== ANNOTATION TESTS =====

    def "task has @UntrackedTask annotation"() {
        expect:
        CleanupTask.class.isAnnotationPresent(UntrackedTask)
    }

    def "@UntrackedTask annotation has correct reason"() {
        given:
        def annotation = CleanupTask.class.getAnnotation(UntrackedTask)

        expect:
        annotation != null
        annotation.because() == "Cleanup operations have side effects that must always execute"
    }

    // ===== FUTURE EXCEPTION TESTS =====

    def "cleanup handles future.get() ExecutionException gracefully"() {
        given:
        task.stackName.set("failing-stack")
        def failingFuture = new CompletableFuture<Void>()
        failingFuture.completeExceptionally(new RuntimeException("Docker compose error"))
        mockComposeService.downStack("failing-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        // Should not throw - cleanup should be best-effort
        noExceptionThrown()
    }

    def "cleanup handles future.get() with InterruptedException gracefully"() {
        given:
        task.stackName.set("interrupt-stack")
        def failingFuture = new CompletableFuture<Void>()
        // Simulate an interrupted execution
        failingFuture.completeExceptionally(new InterruptedException("Thread interrupted"))
        mockComposeService.downStack("interrupt-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        // Should not throw - cleanup should be best-effort
        noExceptionThrown()
    }

    def "cleanup handles exception with null message gracefully"() {
        given:
        task.stackName.set("null-message-stack")
        def failingFuture = new CompletableFuture<Void>()
        // Exception with null message (no message provided)
        failingFuture.completeExceptionally(new RuntimeException((String) null))
        mockComposeService.downStack("null-message-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        // Should not throw - cleanup should handle null message gracefully
        noExceptionThrown()
    }

    def "cleanup handles exception with empty message gracefully"() {
        given:
        task.stackName.set("empty-message-stack")
        def failingFuture = new CompletableFuture<Void>()
        // Exception with empty message
        failingFuture.completeExceptionally(new RuntimeException(""))
        mockComposeService.downStack("empty-message-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        // Should not throw - cleanup should handle empty message gracefully
        noExceptionThrown()
    }

    def "cleanup handles direct exception throw with null message"() {
        given:
        task.stackName.set("direct-null-stack")
        mockComposeService.downStack("direct-null-stack") >> {
            throw new RuntimeException((String) null)
        }

        when:
        task.cleanup()

        then:
        // Should not throw - cleanup should be best-effort
        noExceptionThrown()
    }

    def "cleanup handles exception with cause"() {
        given:
        task.stackName.set("cause-stack")
        def cause = new IOException("IO error")
        def failingFuture = new CompletableFuture<Void>()
        failingFuture.completeExceptionally(new RuntimeException("Wrapper error", cause))
        mockComposeService.downStack("cause-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    def "cleanup handles ExecutionException wrapping RuntimeException"() {
        given:
        task.stackName.set("exec-exception-stack")
        def failingFuture = new CompletableFuture<Void>()
        def cause = new RuntimeException("Inner error")
        failingFuture.completeExceptionally(new java.util.concurrent.ExecutionException("Execution failed", cause))
        mockComposeService.downStack("exec-exception-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    def "cleanup handles Error wrapped in ExecutionException"() {
        given:
        task.stackName.set("error-stack")
        def failingFuture = new CompletableFuture<Void>()
        // When future.get() throws, the Error is wrapped in ExecutionException
        failingFuture.completeExceptionally(new java.util.concurrent.ExecutionException(new Error("Test error")))
        mockComposeService.downStack("error-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        // ExecutionException is caught by catch(Exception e)
        noExceptionThrown()
    }

    def "cleanup handles AssertionError"() {
        given:
        task.stackName.set("assertion-stack")
        def failingFuture = new CompletableFuture<Void>()
        failingFuture.completeExceptionally(new AssertionError("Assertion failed"))
        mockComposeService.downStack("assertion-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        // AssertionError extends Error, not Exception - wrapping in ExecutionException
        noExceptionThrown()
    }

    def "cleanup handles exception with very long message"() {
        given:
        task.stackName.set("long-message-stack")
        def longMessage = "A" * 10000  // Very long message
        def failingFuture = new CompletableFuture<Void>()
        failingFuture.completeExceptionally(new RuntimeException(longMessage))
        mockComposeService.downStack("long-message-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    def "cleanup handles exception with special characters in message"() {
        given:
        task.stackName.set("special-chars-stack")
        def failingFuture = new CompletableFuture<Void>()
        failingFuture.completeExceptionally(new RuntimeException("Error: {}'\"\\n\\t\$@#%^&*()"))
        mockComposeService.downStack("special-chars-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    def "cleanup handles exception with unicode message"() {
        given:
        task.stackName.set("unicode-stack")
        def failingFuture = new CompletableFuture<Void>()
        failingFuture.completeExceptionally(new RuntimeException("错误消息 🚀 émoji"))
        mockComposeService.downStack("unicode-stack") >> failingFuture

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    // ===== NO OPERATIONS TESTS =====

    def "cleanup completes when all options disabled and lists empty"() {
        given:
        // All default values - no stack, all remove flags false, all lists empty
        def newTask = project.tasks.register('noOpsTask', CleanupTask).get()

        when:
        newTask.cleanup()

        then:
        // Task should complete without any operations
        noExceptionThrown()
    }

    def "cleanup completes with only compose service but no stack name"() {
        given:
        // Compose service present but stack name empty
        task.composeService.set(mockComposeService)
        task.stackName.set("")

        when:
        task.cleanup()

        then:
        // Should not call downStack
        0 * mockComposeService.downStack(_)
        noExceptionThrown()
    }

    // ===== COMPOSE WITH ALL OTHER OPERATIONS TESTS =====

    def "cleanup performs all operations when all options enabled"() {
        given:
        task.stackName.set("full-stack")
        task.removeContainers.set(true)
        task.containerNames.set(["container1", "container2"])
        task.removeNetworks.set(true)
        task.networkNames.set(["network1"])
        task.removeImages.set(true)
        task.imageNames.set(["image1:latest", "image2:v1", "image3"])

        mockComposeService.downStack("full-stack") >> CompletableFuture.completedFuture(null)
        mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeContainer("container2") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeImage("image1:latest") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeImage("image2:v1") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeImage("image3") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        1 * mockComposeService.downStack("full-stack") >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeContainer("container2") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeImage("image1:latest") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeImage("image2:v1") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeImage("image3") >> CompletableFuture.completedFuture(true)
    }

    def "cleanup continues all operations when compose fails"() {
        given:
        task.stackName.set("failing-stack")
        task.removeContainers.set(true)
        task.containerNames.set(["container1"])
        task.removeNetworks.set(true)
        task.networkNames.set(["network1"])
        task.removeImages.set(true)
        task.imageNames.set(["image1"])

        def failingFuture = new CompletableFuture<Void>()
        failingFuture.completeExceptionally(new RuntimeException("Compose down failed"))
        mockComposeService.downStack("failing-stack") >> failingFuture
        mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(true)
        mockDockerService.removeImage("image1") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        // Compose fails but other cleanup operations should still proceed
        1 * mockDockerService.removeContainer("container1") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeNetwork("network1") >> CompletableFuture.completedFuture(true)
        1 * mockDockerService.removeImage("image1") >> CompletableFuture.completedFuture(true)
        noExceptionThrown()
    }

    // ===== EDGE CASE - DEFAULT VALUES VIA getOrElse TESTS =====

    def "cleanup handles unset removeContainers via getOrElse"() {
        given:
        def newTask = project.tasks.register('unsetContainerTask', CleanupTask).get()
        // removeContainers not set - uses convention (false)
        newTask.containerNames.set(["container1"])

        when:
        newTask.cleanup()

        then:
        // Should not attempt cleanup since removeContainers defaults to false
        noExceptionThrown()
    }

    def "cleanup handles unset removeNetworks via getOrElse"() {
        given:
        def newTask = project.tasks.register('unsetNetworkTask', CleanupTask).get()
        // removeNetworks not set - uses convention (false)
        newTask.networkNames.set(["network1"])

        when:
        newTask.cleanup()

        then:
        // Should not attempt cleanup since removeNetworks defaults to false
        noExceptionThrown()
    }

    def "cleanup handles unset removeImages via getOrElse"() {
        given:
        def newTask = project.tasks.register('unsetImageTask', CleanupTask).get()
        // removeImages not set - uses convention (false)
        newTask.imageNames.set(["image1"])

        when:
        newTask.cleanup()

        then:
        // Should not attempt cleanup since removeImages defaults to false
        noExceptionThrown()
    }

    def "cleanup handles unset containerNames via getOrElse"() {
        given:
        def newTask = project.tasks.register('unsetContainerNamesTask', CleanupTask).get()
        newTask.removeContainers.set(true)
        // containerNames not set - uses convention (empty list)

        when:
        newTask.cleanup()

        then:
        // Should skip container cleanup since list is empty by default
        noExceptionThrown()
    }

    def "cleanup handles unset networkNames via getOrElse"() {
        given:
        def newTask = project.tasks.register('unsetNetworkNamesTask', CleanupTask).get()
        newTask.removeNetworks.set(true)
        // networkNames not set - uses convention (empty list)

        when:
        newTask.cleanup()

        then:
        // Should skip network cleanup since list is empty by default
        noExceptionThrown()
    }

    def "cleanup handles unset imageNames via getOrElse"() {
        given:
        def newTask = project.tasks.register('unsetImageNamesTask', CleanupTask).get()
        newTask.removeImages.set(true)
        // imageNames not set - uses convention (empty list)

        when:
        newTask.cleanup()

        then:
        // Should skip image cleanup since list is empty by default
        noExceptionThrown()
    }

    def "cleanup handles unset stackName via getOrElse"() {
        given:
        def newTask = project.tasks.register('unsetStackTask', CleanupTask).get()
        newTask.composeService.set(mockComposeService)
        // stackName not set - uses convention (empty string)

        when:
        newTask.cleanup()

        then:
        // Should skip compose cleanup since stackName is empty by default
        0 * mockComposeService.downStack(_)
        noExceptionThrown()
    }

    // ===== COMPOSE SERVICE STATE TESTS =====

    def "cleanup skips compose when stackName set but composeService not present"() {
        given:
        def newTask = project.tasks.register('noServiceStackTask', CleanupTask).get()
        newTask.stackName.set("test-stack")
        // composeService explicitly not set

        when:
        newTask.cleanup()

        then:
        // No compose service to call
        noExceptionThrown()
    }

    def "cleanup skips compose when both stackName empty and composeService not present"() {
        given:
        def newTask = project.tasks.register('noStackNoServiceTask', CleanupTask).get()
        newTask.stackName.set("")
        // composeService not set

        when:
        newTask.cleanup()

        then:
        noExceptionThrown()
    }

    // ===== SINGLE ITEM LIST TESTS =====

    def "cleanup handles single container in list"() {
        given:
        task.removeContainers.set(true)
        task.containerNames.set(["single-container"])
        mockDockerService.removeContainer("single-container") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        1 * mockDockerService.removeContainer("single-container") >> CompletableFuture.completedFuture(true)
    }

    def "cleanup handles single network in list"() {
        given:
        task.removeNetworks.set(true)
        task.networkNames.set(["single-network"])
        mockDockerService.removeNetwork("single-network") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        1 * mockDockerService.removeNetwork("single-network") >> CompletableFuture.completedFuture(true)
    }

    def "cleanup handles single image in list"() {
        given:
        task.removeImages.set(true)
        task.imageNames.set(["single-image:latest"])
        mockDockerService.removeImage("single-image:latest") >> CompletableFuture.completedFuture(true)

        when:
        task.cleanup()

        then:
        1 * mockDockerService.removeImage("single-image:latest") >> CompletableFuture.completedFuture(true)
    }

    // ===== LARGE LIST TESTS =====

    def "cleanup handles large container list"() {
        given:
        task.removeContainers.set(true)
        def containers = (1..100).collect { "container-$it" }
        task.containerNames.set(containers)
        containers.each { container ->
            mockDockerService.removeContainer(container) >> CompletableFuture.completedFuture(true)
        }

        when:
        task.cleanup()

        then:
        100 * mockDockerService.removeContainer(_) >> CompletableFuture.completedFuture(true)
    }

    def "cleanup handles large network list"() {
        given:
        task.removeNetworks.set(true)
        def networks = (1..50).collect { "network-$it" }
        task.networkNames.set(networks)
        networks.each { network ->
            mockDockerService.removeNetwork(network) >> CompletableFuture.completedFuture(true)
        }

        when:
        task.cleanup()

        then:
        50 * mockDockerService.removeNetwork(_) >> CompletableFuture.completedFuture(true)
    }

    def "cleanup handles large image list"() {
        given:
        task.removeImages.set(true)
        def images = (1..75).collect { "image-$it:v1" }
        task.imageNames.set(images)
        images.each { image ->
            mockDockerService.removeImage(image) >> CompletableFuture.completedFuture(true)
        }

        when:
        task.cleanup()

        then:
        75 * mockDockerService.removeImage(_) >> CompletableFuture.completedFuture(true)
    }
}
