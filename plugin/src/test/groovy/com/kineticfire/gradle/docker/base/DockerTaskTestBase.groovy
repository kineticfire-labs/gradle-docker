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

package com.kineticfire.gradle.docker.base

import com.kineticfire.gradle.docker.mocks.DockerTestDataFactory
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.AuthSpec
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Base class for Docker task tests with common setup
 */
abstract class DockerTaskTestBase extends Specification {
    
    @TempDir
    Path tempDir
    
    protected Project project
    protected DockerService mockDockerService
    
    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        mockDockerService = Mock(DockerService)
        
        // Set up common mock behaviors
        mockDockerService.buildImage(_) >> CompletableFuture.completedFuture("sha256:12345")
        mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)
        mockDockerService.saveImage(_, _, _) >> CompletableFuture.completedFuture(null)
        mockDockerService.pushImage(_, _) >> CompletableFuture.completedFuture(null)
        mockDockerService.pullImage(_, _) >> CompletableFuture.completedFuture(null)
        mockDockerService.imageExists(_) >> CompletableFuture.completedFuture(true)
    }
    
    protected File createDockerfile() {
        def dockerfile = new File(tempDir.toFile(), "Dockerfile")
        dockerfile.text = """
            FROM openjdk:17-jre
            COPY app.jar /app.jar
            ENTRYPOINT ["java", "-jar", "/app.jar"]
        """.stripIndent()
        return dockerfile
    }
    
    protected File createContextDirectory() {
        def contextDir = new File(tempDir.toFile(), "context")
        contextDir.mkdirs()
        
        def dockerfile = new File(contextDir, "Dockerfile")
        dockerfile.text = """
            FROM openjdk:17-jre
            COPY app.jar /app.jar
            ENTRYPOINT ["java", "-jar", "/app.jar"]
        """.stripIndent()
        
        def appJar = new File(contextDir, "app.jar")
        appJar.text = "fake jar content"
        
        return contextDir
    }
    
    protected RegularFile createOutputFile(String name) {
        def outputDir = new File(tempDir.toFile(), "output")
        outputDir.mkdirs()
        return project.layout.projectDirectory.file("output/${name}")
    }
    
    protected AuthSpec createAuthSpec(String username = "testuser", String password = "testpass") {
        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.username.set(username)
        authSpec.password.set(password)
        // serverAddress removed - extracted automatically from image reference
        return authSpec
    }
    
    protected AuthSpec createAuthSpec(String username, String password, String registryToken) {
        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.username.set(username)
        if (password != null) {
            authSpec.password.set(password)
        }
        if (registryToken != null) {
            authSpec.registryToken.set(registryToken)
        }
        // serverAddress removed - extracted automatically from image reference
        return authSpec
    }
    
    protected void setupMockServiceForFailure(Exception error) {
        mockDockerService.buildImage(_) >> CompletableFuture.failedFuture(error)
        mockDockerService.tagImage(_, _) >> CompletableFuture.failedFuture(error)
        mockDockerService.saveImage(_, _, _) >> CompletableFuture.failedFuture(error)
        mockDockerService.pushImage(_, _) >> CompletableFuture.failedFuture(error)
        mockDockerService.pullImage(_, _) >> CompletableFuture.failedFuture(error)
    }
    
    protected void verifyNoDockerServiceCalls() {
        0 * mockDockerService.buildImage(_)
        0 * mockDockerService.tagImage(_, _)
        0 * mockDockerService.saveImage(_, _, _)
        0 * mockDockerService.pushImage(_, _)
        0 * mockDockerService.pullImage(_, _)
    }

    protected Object createPublishSpec() {
        def publishSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.PublishSpec, project.objects)
        def target = project.objects.newInstance(com.kineticfire.gradle.docker.spec.PublishTarget, "default", project.objects)
        target.publishTags.set(["docker.io/test/app:latest"])
        def auth = project.objects.newInstance(AuthSpec)
        auth.username.set("testuser")
        auth.password.set("testpass")
        target.auth.set(auth)
        publishSpec.to.add(target)
        return publishSpec
    }

    protected Object createImageSpec(String name = "testImage") {
        return project.objects.newInstance(com.kineticfire.gradle.docker.spec.ImageSpec, name, project)
    }
}