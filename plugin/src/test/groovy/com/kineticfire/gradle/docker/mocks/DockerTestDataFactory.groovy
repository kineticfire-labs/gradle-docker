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

package com.kineticfire.gradle.docker.mocks

import com.kineticfire.gradle.docker.model.*
import com.kineticfire.gradle.docker.spec.*
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import java.nio.file.Paths

/**
 * Factory for creating test data objects for Docker functionality
 */
class DockerTestDataFactory {
    
    static Project createTestProject() {
        return ProjectBuilder.builder().build()
    }
    
    static BuildContext createBuildContext() {
        // Create actual temporary directories and files for tests
        def tempDir = java.nio.file.Files.createTempDirectory("docker-test-context")
        def dockerfile = tempDir.resolve("Dockerfile")
        java.nio.file.Files.createFile(dockerfile)

        return new BuildContext(
            tempDir,
            dockerfile,
            ["JAR_FILE": "app.jar", "VERSION": "1.0.0"],
            ["registry.io/namespace/name:tag", "registry.io/namespace/name:latest"],
            ["maintainer": "team", "version": "1.0.0"]
        )
    }

    static BuildContext createBuildContext(java.nio.file.Path tempDir) {
        // Use provided temp directory for tests that manage their own cleanup
        def dockerfile = tempDir.resolve("Dockerfile")
        if (!java.nio.file.Files.exists(dockerfile)) {
            java.nio.file.Files.createFile(dockerfile)
        }

        return new BuildContext(
            tempDir,
            dockerfile,
            ["JAR_FILE": "app.jar", "VERSION": "1.0.0"],
            ["registry.io/namespace/name:tag", "registry.io/namespace/name:latest"],
            ["maintainer": "team", "version": "1.0.0"]
        )
    }
    
    static AuthConfig createAuthConfig() {
        return new AuthConfig(
            "testuser",
            "testpass",
            null,
            "registry.io"
        )
    }
    
    static AuthConfig createTokenAuthConfig() {
        return new AuthConfig(
            null,
            null,
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9",
            "ghcr.io"
        )
    }
    
    static ImageSpec createImageSpec(Project project, String name = "test") {
        def spec = project.objects.newInstance(ImageSpec, name, project)
        spec.registry.set("docker.io")
        spec.namespace.set("mycompany")
        spec.imageName.set("myapp")
        spec.version.set("1.0.0")
        spec.tags.set(["latest", "1.0.0"])
        spec.labels.set(["maintainer": "team"])
        spec.buildArgs.set(["JAR_FILE": "app.jar"])
        return spec
    }
    
    static ImageSpec createSourceRefImageSpec(Project project, String name = "sourceRef") {
        def spec = project.objects.newInstance(ImageSpec, name, project)
        spec.sourceRef.set("existing:image")
        spec.tags.set(["local:latest"])
        return spec
    }
    
    static SaveSpec createSaveSpec(Project project) {
        def spec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        spec.compression.set(SaveCompression.GZIP)
        spec.outputFile.set(project.layout.buildDirectory.file("docker-images/test.tar.gz"))
        spec.pullIfMissing.set(false)
        return spec
    }
    
    static SaveSpec createSaveSpecWithAuth(Project project) {
        def spec = createSaveSpec(project)
        spec.pullIfMissing.set(true)

        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.username.set("testuser")
        authSpec.password.set("testpass")
        // serverAddress removed - extracted automatically from image reference
        spec.auth.set(authSpec)

        return spec
    }
    
    static PublishSpec createPublishSpec(Project project) {
        def spec = project.objects.newInstance(PublishSpec, project.objects)
        
        def target = project.objects.newInstance(PublishTarget, "dockerhub", project.objects)
        target.registry.set("docker.io")
        target.namespace.set("published")
        target.publishTags.set(["published:latest", "published:1.0.0"])
        
        def authSpec = project.objects.newInstance(AuthSpec)
        authSpec.username.set("publishuser")
        authSpec.password.set("publishpass")
        target.auth.set(authSpec)
        
        spec.to.add(target)
        return spec
    }
    
    static Map<String, String> createBuildArgs() {
        return [
            "JAR_FILE": "app-1.0.0.jar",
            "JAVA_VERSION": "17",
            "BASE_IMAGE": "openjdk:17-jre"
        ]
    }
    
    static Map<String, String> createLabels() {
        return [
            "maintainer": "team@company.com",
            "version": "1.0.0",
            "org.opencontainers.image.source": "https://github.com/company/repo",
            "org.opencontainers.image.revision": "abc123def"
        ]
    }
    
    static List<String> createImageReferences() {
        return [
            "docker.io/mycompany/myapp:latest",
            "docker.io/mycompany/myapp:1.0.0",
            "ghcr.io/mycompany/myapp:latest"
        ]
    }
    
    static List<String> createTags() {
        return ["latest", "1.0.0", "stable"]
    }
}