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

package com.kineticfire.gradle.docker.spec

import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.api.Action
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Comprehensive unit tests for ImageSpec Provider API functionality
 */
class ImageSpecComprehensiveTest extends Specification {

    def project
    def imageSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)
    }

    // ===== PROVIDER API TESTS =====

    def "all DSL methods work with Provider API"() {
        given:
        def dynamicValue = project.provider { "dynamic-value" }
        def dynamicMap = project.provider { ["key": "value"] }

        when:
        imageSpec.label("static", "staticValue")
        imageSpec.label("dynamic", dynamicValue)
        imageSpec.labels(["batch1": "value1", "batch2": "value2"])
        imageSpec.labels(dynamicMap)
        
        imageSpec.buildArg("static", "staticArg")
        imageSpec.buildArg("dynamic", dynamicValue)
        imageSpec.buildArgs(["batch1": "arg1", "batch2": "arg2"])
        imageSpec.buildArgs(dynamicMap)

        then:
        imageSpec.labels.get()["static"] == "staticValue"
        imageSpec.labels.get()["dynamic"] == "dynamic-value"
        imageSpec.labels.get()["batch1"] == "value1"
        imageSpec.labels.get()["batch2"] == "value2"
        imageSpec.labels.get()["key"] == "value"
        
        imageSpec.buildArgs.get()["static"] == "staticArg"
        imageSpec.buildArgs.get()["dynamic"] == "dynamic-value"
        imageSpec.buildArgs.get()["batch1"] == "arg1"
        imageSpec.buildArgs.get()["batch2"] == "arg2"
        imageSpec.buildArgs.get()["key"] == "value"
    }

    def "nomenclature properties work with Provider API"() {
        given:
        def registryProvider = project.provider { "dynamic.registry.io" }
        def namespaceProvider = project.provider { "dynamic-namespace" }
        def imageNameProvider = project.provider { "dynamic-image" }
        def repositoryProvider = project.provider { "dynamic/repository" }
        def versionProvider = project.provider { "2.0.0" }
        def tagsProvider = project.provider { ["dynamic1", "dynamic2"] }

        when:
        imageSpec.registry.set(registryProvider)
        imageSpec.namespace.set(namespaceProvider)
        imageSpec.imageName.set(imageNameProvider)
        imageSpec.repository.set(repositoryProvider)
        imageSpec.version.set(versionProvider)
        imageSpec.tags.set(tagsProvider)

        then:
        imageSpec.registry.get() == "dynamic.registry.io"
        imageSpec.namespace.get() == "dynamic-namespace"
        imageSpec.imageName.get() == "dynamic-image"
        imageSpec.repository.get() == "dynamic/repository"
        imageSpec.version.get() == "2.0.0"
        imageSpec.tags.get() == ["dynamic1", "dynamic2"]
    }

    def "sourceRef property works with Provider API"() {
        given:
        def sourceRefProvider = project.provider { "external:image:tag" }

        when:
        imageSpec.sourceRef.set(sourceRefProvider)

        then:
        imageSpec.sourceRef.get() == "external:image:tag"
    }

    def "context property with Provider API"() {
        given:
        def contextDir = project.file("src/main/docker")
        contextDir.mkdirs()
        def contextProvider = project.layout.dir(project.provider { contextDir })

        when:
        imageSpec.context.set(contextProvider)

        then:
        imageSpec.context.get().asFile == contextDir
    }

    def "dockerfile properties with Provider API"() {
        given:
        def dockerfileFile = project.file("Dockerfile")
        dockerfileFile.text = "FROM alpine"
        def dockerfileProvider = project.layout.file(project.provider { dockerfileFile })
        def dockerfileNameProvider = project.provider { "Dockerfile.production" }

        when:
        imageSpec.dockerfile.set(dockerfileProvider)
        imageSpec.dockerfileName.set(dockerfileNameProvider)

        then:
        imageSpec.dockerfile.get().asFile == dockerfileFile
        imageSpec.dockerfileName.get() == "Dockerfile.production"
    }

    def "context DSL throws UnsupportedOperationException"() {
        given:
        def sourceDir = project.file("app")
        sourceDir.mkdirs()
        new File(sourceDir, "app.jar").text = "fake jar"

        when:
        imageSpec.context {
            from sourceDir
            into "context"
            include "**/*.jar"
        }

        then:
        def ex = thrown(UnsupportedOperationException)
        ex.message.contains("Inline context() DSL is not supported with Gradle configuration cache")
    }

    def "context Action throws UnsupportedOperationException"() {
        given:
        def sourceDir = project.file("source")
        sourceDir.mkdirs()

        when:
        imageSpec.context(new Action() {
            @Override
            void execute(task) {
                task.from sourceDir
                task.destinationDir = project.file("build/docker-context")
            }
        })

        then:
        def ex = thrown(UnsupportedOperationException)
        ex.message.contains("Inline context() DSL is not supported with Gradle configuration cache")
    }

    // ===== NESTED SPEC CONFIGURATION TESTS =====

    def "save configuration with all options"() {
        when:
        imageSpec.save {
            compression.set(SaveCompression.XZ)
            outputFile.set(project.layout.buildDirectory.file("images/app.tar.xz"))
            // pullIfMissing moved to image level
            // auth moved to image level (pullAuth)
        }

        then:
        imageSpec.save.isPresent()
        with(imageSpec.save.get()) {
            compression.get() == SaveCompression.XZ
            outputFile.get().asFile.name == "app.tar.xz"
            // pullIfMissing and auth moved to image level
        }
    }

    def "save Action configuration"() {
        when:
        imageSpec.save(new Action<SaveSpec>() {
            @Override
            void execute(SaveSpec saveSpec) {
                saveSpec.compression.set(SaveCompression.ZIP)
                saveSpec.outputFile.set(project.file("output.zip"))
                // pullIfMissing moved to image level
            }
        })

        then:
        imageSpec.save.isPresent()
        imageSpec.save.get().compression.get() == SaveCompression.ZIP
        imageSpec.save.get().outputFile.get().asFile.name == "output.zip"
        // pullIfMissing moved to image level
    }

    def "publish configuration with multiple targets"() {
        when:
        imageSpec.publish {
            to("dockerhub") {
                registry.set("docker.io")
                namespace.set("myuser")
                publishTags(["docker.io/myuser/app:latest"])
                auth {
                    username.set("dockerUser")
                    password.set("dockerPass")
                }
            }
            to("ghcr") {
                registry.set("ghcr.io")
                namespace.set("myorg")
                publishTags(["ghcr.io/myorg/app:latest"])
                auth {
                    registryToken.set("ghp_token123")
                }
            }
            to("ecr") {
                publishTags(["123456789012.dkr.ecr.us-west-2.amazonaws.com/app:latest"])
                auth {
                    helper.set("ecr-login")
                }
            }
        }

        then:
        imageSpec.publish.isPresent()
        with(imageSpec.publish.get()) {
            to.size() == 3

            // Docker Hub target
            def dockerhubTarget = to.getByName("dockerhub")
            dockerhubTarget.name == "dockerhub"
            dockerhubTarget.registry.get() == "docker.io"
            dockerhubTarget.namespace.get() == "myuser"
            dockerhubTarget.publishTags.get() == ["docker.io/myuser/app:latest"]
            dockerhubTarget.auth.get().username.get() == "dockerUser"
            dockerhubTarget.auth.get().password.get() == "dockerPass"

            // GHCR target
            def ghcrTarget = to.getByName("ghcr")
            ghcrTarget.name == "ghcr"
            ghcrTarget.registry.get() == "ghcr.io"
            ghcrTarget.namespace.get() == "myorg"
            ghcrTarget.publishTags.get() == ["ghcr.io/myorg/app:latest"]
            ghcrTarget.auth.get().registryToken.get() == "ghp_token123"

            // ECR target
            def ecrTarget = to.getByName("ecr")
            ecrTarget.name == "ecr"
            ecrTarget.publishTags.get() == ["123456789012.dkr.ecr.us-west-2.amazonaws.com/app:latest"]
            ecrTarget.auth.get().helper.get() == "ecr-login"
        }
    }

    def "publish Action configuration"() {
        when:
        imageSpec.publish(new Action<PublishSpec>() {
            @Override
            void execute(PublishSpec publishSpec) {
                publishSpec.to("registry") {
                    publishTags.set(["registry.io/app:test"])
                }
            }
        })

        then:
        imageSpec.publish.isPresent()
        imageSpec.publish.get().to.size() == 1
        def registryTarget = imageSpec.publish.get().to.getByName("registry")
        registryTarget.name == "registry"
        registryTarget.publishTags.get() == ["registry.io/app:test"]
    }

    // ===== CONVENTION VALUES TESTS =====

    def "constructor sets proper convention values"() {
        expect:
        imageSpec.name == "testImage"
        imageSpec.registry.getOrElse("") == ""
        imageSpec.namespace.getOrElse("") == ""
        imageSpec.repository.getOrElse("") == ""
        imageSpec.version.getOrElse("") == ""  // Version must be explicitly specified (no project.version default for config cache)
        imageSpec.buildArgs.get().isEmpty()
        imageSpec.labels.get().isEmpty()
        imageSpec.sourceRef.getOrElse("") == ""
        imageSpec.context.get().asFile.path.endsWith("src/main/docker")
    }

    def "version must be explicitly set"() {
        given:
        def newImageSpec = project.objects.newInstance(ImageSpec, 'versionTest', project.objects, project.providers, project.layout)

        when:
        newImageSpec.version.set("3.0.0-SNAPSHOT")

        then:
        newImageSpec.version.get() == "3.0.0-SNAPSHOT"
    }

    // ===== EDGE CASES AND ERROR HANDLING =====

    def "label methods handle null and empty values"() {
        when:
        imageSpec.label("empty", "")
        imageSpec.label("null", null)  // This will be ignored due to null check
        imageSpec.labels([:])
        imageSpec.labels(null as Map)  // This will be ignored due to null check

        then:
        imageSpec.labels.get()["empty"] == ""
        !imageSpec.labels.get().containsKey("null")  // null value was ignored
        imageSpec.labels.get().size() >= 1  // At least the empty one we added
    }

    def "buildArg methods handle null and empty values"() {
        when:
        imageSpec.buildArg("empty", "")
        imageSpec.buildArg("null", null)  // This will be ignored due to null check
        imageSpec.buildArgs([:])
        imageSpec.buildArgs(null as Map)  // This will be ignored due to null check

        then:
        imageSpec.buildArgs.get()["empty"] == ""
        !imageSpec.buildArgs.get().containsKey("null")  // null value was ignored
        imageSpec.buildArgs.get().size() >= 1  // At least the empty one we added
    }

    def "properties can be overridden"() {
        when:
        imageSpec.registry.set("first.registry.io")
        imageSpec.registry.set("second.registry.io")
        
        imageSpec.tags.set(["tag1", "tag2"])
        imageSpec.tags.set(["tag3"])
        
        imageSpec.labels(["old": "value"])
        imageSpec.labels(["new": "value"])

        then:
        imageSpec.registry.get() == "second.registry.io"
        imageSpec.tags.get() == ["tag3"]
        imageSpec.labels.get()["new"] == "value"
        imageSpec.labels.get()["old"] == "value"  // Maps merge
    }

    def "complex scenario with all properties"() {
        given:
        def contextDir = project.file("complex/context")
        contextDir.mkdirs()
        def dockerfileFile = new File(contextDir, "Dockerfile")
        dockerfileFile.text = "FROM openjdk:17"

        when:
        imageSpec.registry.set("complex.registry.io")
        imageSpec.namespace.set("complex/namespace")
        imageSpec.imageName.set("complex-app")
        imageSpec.version.set("1.0.0-complex")
        imageSpec.tags.set(["complex1", "complex2", "latest"])
        imageSpec.context.set(contextDir)
        imageSpec.dockerfile.set(dockerfileFile)
        imageSpec.buildArgs([
            "JAVA_VERSION": "17",
            "APP_JAR": "complex-app.jar"
        ])
        imageSpec.buildArg("BUILD_DATE", project.provider { new Date().toString() })
        imageSpec.labels([
            "maintainer": "complex-team@company.com"
        ])
        imageSpec.label("version", imageSpec.version)
        imageSpec.label("build.timestamp", project.provider { System.currentTimeMillis().toString() })

        imageSpec.save {
            compression.set(SaveCompression.GZIP)
            outputFile.set(project.layout.buildDirectory.file("images/complex-app.tar.gz"))
            // pullIfMissing moved to image level
        }

        imageSpec.publish {
            to("production") {
                registry.set("prod.registry.io")
                namespace.set("production")
                publishTags.set([
                    "prod.registry.io/production/complex-app:1.0.0-complex",
                    "prod.registry.io/production/complex-app:latest"
                ])
                auth {
                    username.set("prod-user")
                    password.set("prod-password")
                }
            }
        }

        then:
        with(imageSpec) {
            registry.get() == "complex.registry.io"
            namespace.get() == "complex/namespace"
            imageName.get() == "complex-app"
            version.get() == "1.0.0-complex"
            tags.get() == ["complex1", "complex2", "latest"]
            context.get().asFile == contextDir
            dockerfile.get().asFile == dockerfileFile
            buildArgs.get()["JAVA_VERSION"] == "17"
            buildArgs.get()["APP_JAR"] == "complex-app.jar"
            buildArgs.get()["BUILD_DATE"] != null
            labels.get()["maintainer"] == "complex-team@company.com"
            labels.get()["version"] == "1.0.0-complex"
            labels.get()["build.timestamp"] != null
            save.isPresent()
            publish.isPresent()
        }
    }
}