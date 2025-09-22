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

package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.SaveSpec
import com.kineticfire.gradle.docker.spec.PublishSpec
import com.kineticfire.gradle.docker.spec.PublishTarget
import com.kineticfire.gradle.docker.spec.AuthSpec
import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

/**
 * Comprehensive unit tests for DockerExtension validation
 */
class DockerExtensionComprehensiveTest extends Specification {

    @TempDir
    Path tempDir
    
    def project
    def extension

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        extension = project.objects.newInstance(DockerExtension, project.objects, project.providers, project.layout, project)
    }

    // ===== NOMENCLATURE VALIDATION TESTS =====

    def "validateNomenclature enforces mutual exclusivity between repository and namespace+imageName"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.repository.set("repo/name")
        imageSpec.namespace.set("namespace")
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("mutual")
    }

    def "validateNomenclature allows repository without namespace+imageName"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.repository.set("valid/repo")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature allows namespace+imageName without repository"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.namespace.set("namespace")
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature allows only imageName"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature accepts valid tag formats"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.imageName.set("name")
        imageSpec.tags.set([validTag])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()

        where:
        validTag << [
            "latest",
            "1.0.0",
            "v1.0.0",
            "stable",
            "test-branch",
            "feature_123",
            "build.456"
        ]
    }

    def "validateNomenclature rejects invalid tag formats"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.imageName.set("name")
        imageSpec.tags.set([invalidTag])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        thrown(GradleException)

        where:
        invalidTag << [
            ".invalid",
            "-invalid",
            "invalid:",
            "invalid/tag",
            "invalid tag",
            ""
        ]
    }

    // ===== IMAGE SPEC VALIDATION TESTS =====

    def "validateImageSpec requires context, contextTask, or sourceRef"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])
        // No context, contextTask, or sourceRef

        when:
        extension.validateImageSpec(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify either 'context', 'contextTask', or 'sourceRef'")
    }

    def "validateImageSpec allows sourceRef without context"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateImageSpec allows explicit context"() {
        given:
        def contextDir = tempDir.resolve("docker-context").toFile()
        contextDir.mkdirs()
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.context.set(contextDir)
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateImageSpec allows contextTask"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.contextTask = project.tasks.register("prepareContext") {
            it.group = "docker"
        }
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateImageSpec prevents multiple context configurations"() {
        given:
        def contextDir = tempDir.resolve("custom-context").toFile()
        contextDir.mkdirs()
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.context.set(contextDir)
        imageSpec.contextTask = project.tasks.register("prepareContext") {
            it.group = "docker"
        }
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify only one of")
    }

    def "validateImageSpec prevents both dockerfile and dockerfileName"() {
        given:
        def contextDir = tempDir.resolve("docker-context").toFile()
        contextDir.mkdirs()
        def dockerfileFile = new File(contextDir, "Dockerfile")
        dockerfileFile.text = "FROM alpine"
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.context.set(contextDir)
        imageSpec.dockerfile.set(dockerfileFile)
        imageSpec.dockerfileName.set("Dockerfile.custom")
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("cannot specify both 'dockerfile' and 'dockerfileName'")
    }

    def "validateImageSpec validates context directory exists"() {
        given:
        def nonExistentDir = tempDir.resolve("nonexistent").toFile()
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.context.set(nonExistentDir)
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Docker context directory does not exist")
    }

    def "validateImageSpec validates dockerfile exists when specified"() {
        given:
        def contextDir = tempDir.resolve("docker-context").toFile()
        contextDir.mkdirs()
        def nonExistentDockerfile = new File(contextDir, "NonExistent")
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.context.set(contextDir)
        imageSpec.dockerfile.set(nonExistentDockerfile)
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Dockerfile does not exist")
    }

    def "validateImageSpec skips validation for sourceRef images"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.tags.set(["tag"])
        // No need for nomenclature or context validation

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    // ===== IMAGE REFERENCE VALIDATION TESTS =====

    def "isValidImageReference validates various formats"() {
        expect:
        extension.isValidImageReference(ref) == valid

        where:
        ref                                          | valid
        "image:tag"                                  | true
        "namespace/image:tag"                        | true
        "registry.io/namespace/image:tag"            | true
        "registry.io:5000/namespace/image:tag"       | true
        "localhost:5000/image:latest"                | true
        "ghcr.io/owner/repo:v1.0.0"                 | true
        "docker.io/library/alpine:3.16"             | true
        "image"                                      | false  // No tag
        "image:"                                     | false  // Empty tag
        ":tag"                                       | false  // No image
        ""                                           | false  // Empty
        null                                         | false  // Null
        "invalid::tag"                               | false  // Double colon
        "registry.io/namespace/image:tag:extra"      | false  // Multiple colons
    }

    def "isValidRepositoryFormat validates repository formats"() {
        expect:
        extension.isValidRepositoryFormat(repo) == valid

        where:
        repo              | valid
        "namespace/name"  | true
        "a/b"             | true
        "org/project"     | true
        "a/b/c"           | true
        "deep/path/name"  | true
        "name"            | false  // No slash
        ""                | false  // Empty
        "a/"              | true   // Trailing slash allowed
        "/a"              | true   // Leading slash allowed
        "a//b"            | true   // Double slash allowed (though unusual)
    }

    def "isValidTagFormat validates tag formats"() {
        expect:
        extension.isValidTagFormat(tag) == valid

        where:
        tag           | valid
        "latest"      | true
        "1.0.0"       | true
        "v1.0.0"      | true
        "stable"      | true
        "test-branch" | true
        "feature_123" | true
        "build.456"   | true
        "UPPERCASE"   | true
        "mixed_Case"  | true
        ".invalid"    | false  // Starts with dot
        "-invalid"    | false  // Starts with dash
        "invalid:"    | false  // Contains colon
        "invalid/tag" | false  // Contains slash
        "invalid tag" | false  // Contains space
        ""            | false  // Empty
        "a" * 129     | false  // Too long (>128)
    }

    def "isValidRegistryFormat validates registry formats"() {
        expect:
        extension.isValidRegistryFormat(registry) == valid

        where:
        registry              | valid
        "docker.io"           | true
        "registry.io"         | true
        "localhost"           | true
        "registry.io:5000"    | true
        "localhost:8080"      | true
        "gcr.io"              | true
        "ghcr.io"             | true
        "my-registry.com"     | true
        "192.168.1.100"       | true
        "192.168.1.100:5000"  | true
        ""                    | false  // Empty
        "invalid:port:extra"  | false  // Multiple colons
        "invalid port"        | false  // Space
        "registry:"           | false  // Empty port
        ":5000"               | false  // No hostname
    }

    def "isValidNamespaceFormat validates namespace formats"() {
        expect:
        extension.isValidNamespaceFormat(namespace) == valid

        where:
        namespace         | valid
        "company"         | true
        "my-company"      | true
        "my_company"      | true
        "company.name"    | true
        "a/b"             | true
        "deep/path/name"  | true
        "123company"      | true
        "company123"      | true
        ""                | false  // Empty
        "Company"         | false  // Uppercase
        "company space"   | false  // Space
        "company:tag"     | false  // Colon
        "a" * 256         | false  // Too long (>255)
    }

    def "isValidImageNameFormat validates image name formats"() {
        expect:
        extension.isValidImageNameFormat(imageName) == valid

        where:
        imageName     | valid
        "app"         | true
        "my-app"      | true
        "my_app"      | true
        "app.name"    | true
        "app123"      | true
        "123app"      | true
        ""            | false  // Empty
        "App"         | false  // Uppercase
        "app name"    | false  // Space
        "app:tag"     | false  // Colon
        "app/name"    | false  // Slash
        "a" * 129     | false  // Too long (>128)
    }

    // ===== PUBLISH CONFIGURATION VALIDATION =====

    def "validatePublishConfiguration requires at least one target"() {
        given:
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        // No targets added

        when:
        extension.validatePublishConfiguration(publishSpec, "testImage")

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one target")
    }

    def "validatePublishTarget requires publishTags"() {
        given:
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        // No publishTags set

        when:
        extension.validatePublishTarget(target, "testImage")

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one tag")
    }

    def "validatePublishTarget validates tag image references"() {
        given:
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.publishTags.set([invalidTag])

        when:
        extension.validatePublishTarget(target, "testImage")

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Invalid image reference")

        where:
        invalidTag << ["invalid", "invalid::", "invalid tag:latest"]
    }

    def "validatePublishTarget accepts valid image references"() {
        given:
        def target = project.objects.newInstance(PublishTarget, "test", project.objects)
        target.publishTags.set(["registry.io/namespace/name:tag"])

        when:
        extension.validatePublishTarget(target, "testImage")

        then:
        noExceptionThrown()
    }

    // ===== SAVE CONFIGURATION VALIDATION =====

    def "validateSaveConfiguration requires compression"() {
        given:
        def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        // compression not set (using convention)
        saveSpec.outputFile.set(project.layout.buildDirectory.file("image.tar"))

        when:
        extension.validateSaveConfiguration(saveSpec, "testImage")

        then:
        // Should not throw since compression has a convention
        noExceptionThrown()
    }

    def "validateSaveConfiguration requires outputFile"() {
        given:
        def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        saveSpec.compression.set(SaveCompression.GZIP)
        // No outputFile set

        when:
        extension.validateSaveConfiguration(saveSpec, "testImage")

        then:
        def ex = thrown(GradleException)
        ex.message.contains("outputFile parameter is required")
    }

    def "validateSaveConfiguration accepts valid configuration"() {
        given:
        def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        saveSpec.compression.set(SaveCompression.GZIP)
        saveSpec.outputFile.set(project.layout.buildDirectory.file("image.tar.gz"))

        when:
        extension.validateSaveConfiguration(saveSpec, "testImage")

        then:
        noExceptionThrown()
    }

    // ===== INTEGRATION TESTS =====

    def "validate processes complete image configuration"() {
        given:
        def contextDir = tempDir.resolve("docker-context").toFile()
        contextDir.mkdirs()
        new File(contextDir, "Dockerfile").text = "FROM alpine"
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project)
        imageSpec.context.set(contextDir)
        imageSpec.namespace.set("company")
        imageSpec.imageName.set("app")
        imageSpec.tags.set(["1.0.0", "latest"])
        
        def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        saveSpec.compression.set(SaveCompression.GZIP)
        saveSpec.outputFile.set(project.layout.buildDirectory.file("image.tar.gz"))
        imageSpec.save.set(saveSpec)
        
        def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
        def target = project.objects.newInstance(PublishTarget, "dockerhub", project.objects)
        target.publishTags.set(["docker.io/company/app:1.0.0", "docker.io/company/app:latest"])
        publishSpec.to.add(target)
        imageSpec.publish.set(publishSpec)
        
        extension.images.add(imageSpec)

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate processes sourceRef image configuration"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "existing", project)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.tags.set(["local:latest"])
        
        extension.images.add(imageSpec)

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate catches multiple validation errors"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "invalid", project)
        imageSpec.repository.set("repo/name")
        imageSpec.namespace.set("namespace")  // Conflicts with repository
        imageSpec.imageName.set("name")       // Conflicts with repository
        imageSpec.tags.set([".invalid"])      // Invalid tag format
        // No context, contextTask, or sourceRef
        
        extension.images.add(imageSpec)

        when:
        extension.validate()

        then:
        thrown(GradleException)
    }
}