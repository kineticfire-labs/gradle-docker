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
import spock.lang.Unroll
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
        extension = project.objects.newInstance(DockerExtension, project.objects, project.providers, project.layout)
    }

    // ===== NOMENCLATURE VALIDATION TESTS =====

    def "validateNomenclature enforces mutual exclusivity between repository and namespace+imageName"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.repository.set("valid/repo")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature allows namespace+imageName without repository"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature accepts valid tag formats"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.context.set(nonExistentDir)
        imageSpec.imageName.set("name")
        imageSpec.tags.set(["tag"])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Docker context directory does not exist")
    }

    // NOTE: Dockerfile existence validation moved to DockerBuildTask execution phase
    // for Gradle 9/10 configuration cache compatibility. Test removed as validation
    // no longer occurs during configuration phase.

    def "validateImageSpec skips validation for sourceRef images"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
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
        target.publishTags(["1.0.0", "latest"])
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
        def imageSpec = project.objects.newInstance(ImageSpec, "existing", project.objects, project.providers, project.layout)
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
        def imageSpec = project.objects.newInstance(ImageSpec, "invalid", project.objects, project.providers, project.layout)
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

    // ===== NEW PUBLISHTAGS API COMPREHENSIVE VALIDATION TESTS =====

    @Unroll
    def "validatePublishTarget accepts valid simple tag format [tag: #tag]"() {
        given: "A publish target with valid tag"
        def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
        publishTarget.publishTags([tag])
        
        when: "validation is performed"
        extension.validatePublishTarget(publishTarget, 'testImage')
        
        then: "validation passes"
        noExceptionThrown()
        
        where:
        tag << ['latest', 'v1.0.0', 'stable', 'rc-1', 'main', 'test', 'dev-branch', 'sha-abc123', 'v1.0.0-alpha', 'latest-dev']
    }

    @Unroll
    def "validatePublishTarget rejects invalid simple tag format [tag: #tag]"() {
        given: "A publish target with invalid tag"
        def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
        publishTarget.publishTags([tag])
        
        when: "validation is performed"
        extension.validatePublishTarget(publishTarget, 'testImage')
        
        then: "validation fails"
        thrown(GradleException)
        
        where:
        tag << ['invalid::', 'bad tag', '', '.invalid', '-invalid']
    }

    def "validatePublishTarget handles edge cases correctly"() {
        given: "A publish target with edge case tags"
        def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
        publishTarget.publishTags(['v1.0.0-alpha', 'latest-dev', 'sha-abc123'])
        
        when: "validation is performed"
        extension.validatePublishTarget(publishTarget, 'testImage')
        
        then: "validation passes for valid tag patterns"
        noExceptionThrown()
    }

    def "validate accepts multiple publish targets with simple tag names"() {
        given: "Image configuration with multiple publish targets using simple tags"
        def contextDir = tempDir.resolve("docker-context").toFile()
        contextDir.mkdirs()
        new File(contextDir, "Dockerfile").text = "FROM alpine"
        
        extension.images {
            multipleTargetsImage {
                context.set(contextDir)
                dockerfile.set(new File(contextDir, "Dockerfile"))
                imageName.set('multi-target-app')
                tags.set(['latest', 'v2.0.0'])
                publish {
                    to('dockerhub') {
                        registry.set('docker.io')
                        namespace.set('mycompany')
                        publishTags(['latest', 'stable', 'v2.0.0'])  // Simple tag names
                    }
                    to('internal') {
                        registry.set('localhost:5000')
                        namespace.set('internal')
                        publishTags(['internal-latest', 'test', 'dev'])  // Simple tag names
                    }
                    to('staging') {
                        registry.set('staging.company.com')
                        namespace.set('staging')
                        publishTags(['staging-latest', 'pre-release'])  // Simple tag names
                    }
                }
            }
        }
        
        when: "validation is performed"
        extension.validate()
        
        then: "validation passes without exception"
        noExceptionThrown()
    }

    def "validate fails with mixed valid and invalid publishTags"() {
        given: "A publish target with both valid and invalid tag formats"
        def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
        publishTarget.publishTags(['latest', 'invalid::', 'v1.0.0'])  // Mixed valid/invalid
        
        when: "validation is performed"
        extension.validatePublishTarget(publishTarget, 'testImage')
        
        then: "validation fails on first invalid tag"
        def ex = thrown(GradleException)
        ex.message.contains("Invalid tag format")
        ex.message.contains("invalid::")
        ex.message.contains('testTarget')
        ex.message.contains('testImage')
    }

    def "publishTags property works correctly via tags alias"() {
        given: "A publish target configured via tags alias"
        def publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
        publishTarget.tags(['latest', 'v1.0.0'])  // Using alias

        when: "validation is performed"
        extension.validatePublishTarget(publishTarget, 'testImage')

        then: "validation passes since tags is alias for publishTags"
        noExceptionThrown()

        and: "both properties return the same values"
        publishTarget.tags.get() == ['latest', 'v1.0.0']
        publishTarget.publishTags.get() == ['latest', 'v1.0.0']
    }

    // SOURCEREF EXCLUSIVITY VALIDATION TESTS

    def "validateImageSpec rejects sourceRef with buildArgs"() {
        given: "ImageSpec with sourceRef and buildArgs (conflicting configuration)"
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.buildArgs.put("VERSION", "1.0")

        when: "validation is performed"
        extension.validateImageSpec(imageSpec)

        then: "validation fails with exclusivity error"
        def ex = thrown(GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateImageSpec rejects sourceRef with explicit context"() {
        given: "ImageSpec with sourceRef and explicit context directory"
        def contextDir = tempDir.resolve("custom-context").toFile()
        contextDir.mkdirs()

        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.context.set(project.layout.projectDirectory.dir(contextDir.name))

        when: "validation is performed"
        extension.validateImageSpec(imageSpec)

        then: "validation fails with exclusivity error"
        def ex = thrown(GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateImageSpec rejects sourceRef with contextTask"() {
        given: "ImageSpec with sourceRef and contextTask"
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.contextTask = project.tasks.register("prepareContext") {
            // Mock context task
        }

        when: "validation is performed"
        extension.validateImageSpec(imageSpec)

        then: "validation fails with exclusivity error"
        def ex = thrown(GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateImageSpec rejects sourceRef with labels"() {
        given: "ImageSpec with sourceRef and labels"
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.labels.put("build.version", "1.0.0")

        when: "validation is performed"
        extension.validateImageSpec(imageSpec)

        then: "validation fails with exclusivity error"
        def ex = thrown(GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateImageSpec rejects sourceRef with dockerfile"() {
        given: "ImageSpec with sourceRef and custom dockerfile"
        def dockerfileFile = tempDir.resolve("custom.dockerfile").toFile()
        dockerfileFile.createNewFile()

        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.dockerfile.set(project.layout.projectDirectory.file(dockerfileFile.name))

        when: "validation is performed"
        extension.validateImageSpec(imageSpec)

        then: "validation fails with exclusivity error"
        def ex = thrown(GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateImageSpec rejects sourceRef with dockerfileName"() {
        given: "ImageSpec with sourceRef and custom dockerfileName"
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.dockerfileName.set("Custom.dockerfile")

        when: "validation is performed"
        extension.validateImageSpec(imageSpec)

        then: "validation fails with exclusivity error"
        def ex = thrown(GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateImageSpec rejects sourceRef with multiple build properties"() {
        given: "ImageSpec with sourceRef and multiple build properties"
        def contextDir = tempDir.resolve("multi-context").toFile()
        contextDir.mkdirs()

        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.context.set(project.layout.projectDirectory.dir(contextDir.name))
        imageSpec.buildArgs.put("VERSION", "1.0")
        imageSpec.labels.put("build.time", "now")

        when: "validation is performed"
        extension.validateImageSpec(imageSpec)

        then: "validation fails with exclusivity error"
        def ex = thrown(GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")

    }

    def "validateImageSpec allows sourceRef without build properties"() {
        given: "ImageSpec with only sourceRef (valid SourceRef Mode)"
        def imageSpec = project.objects.newInstance(ImageSpec, "test", project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set("existing:image")
        imageSpec.tags.set(["local:latest"])  // Tags are allowed with sourceRef

        when: "validation is performed"
        extension.validateImageSpec(imageSpec)

        then: "validation passes"
        noExceptionThrown()
    }
}