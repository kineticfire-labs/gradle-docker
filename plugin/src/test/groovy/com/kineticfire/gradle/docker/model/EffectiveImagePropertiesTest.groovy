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

package com.kineticfire.gradle.docker.model

import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.PublishTarget
import com.kineticfire.gradle.docker.task.DockerPublishTask
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class EffectiveImagePropertiesTest extends Specification {

    Project project
    ObjectFactory objectFactory

    def setup() {
        project = ProjectBuilder.builder().build()
        objectFactory = project.objects
    }

    def "fromImageSpec should parse direct sourceRef"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.sourceRef.set("docker.io/library/nginx:1.21")

        when:
        def result = EffectiveImageProperties.fromImageSpec(imageSpec)

        then:
        result.registry == "docker.io"
        result.namespace == "library"
        result.imageName == "nginx"
        result.repository == ""
        result.tags == ["1.21"]
    }

    def "fromImageSpec should handle sourceRef components"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.sourceRefRegistry.set("ghcr.io")
        imageSpec.sourceRefNamespace.set("company")
        imageSpec.sourceRefImageName.set("myapp")
        imageSpec.sourceRefTag.set("v1.0")

        when:
        def result = EffectiveImageProperties.fromImageSpec(imageSpec)

        then:
        result.registry == "ghcr.io"
        result.namespace == "company"
        result.imageName == "myapp"
        result.repository == ""
        result.tags == ["v1.0"]
    }

    def "fromImageSpec should handle sourceRef repository components"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.sourceRefRegistry.set("localhost:5000")
        imageSpec.sourceRefRepository.set("project/service")
        imageSpec.sourceRefTag.set("latest")

        when:
        def result = EffectiveImageProperties.fromImageSpec(imageSpec)

        then:
        result.registry == "localhost:5000"
        result.namespace == "project"
        result.imageName == "service"
        result.repository == ""
        result.tags == ["latest"]
    }

    def "fromImageSpec should handle build mode properties"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.registry.set("docker.io")
        imageSpec.namespace.set("mycompany")
        imageSpec.imageName.set("webapp")
        imageSpec.tags.set(["latest", "v2.0"])

        when:
        def result = EffectiveImageProperties.fromImageSpec(imageSpec)

        then:
        result.registry == "docker.io"
        result.namespace == "mycompany"
        result.imageName == "webapp"
        result.repository == ""
        result.tags == ["latest", "v2.0"]
    }

    def "fromImageSpec should handle build mode with repository"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.registry.set("ghcr.io")
        imageSpec.repository.set("username/project")
        imageSpec.tags.set(["stable"])

        when:
        def result = EffectiveImageProperties.fromImageSpec(imageSpec)

        then:
        result.registry == "ghcr.io"
        result.namespace == ""
        result.imageName == ""
        result.repository == "username/project"
        result.tags == ["stable"]
    }

    def "applyTargetOverrides should override all properties"() {
        given:
        def sourceProps = new EffectiveImageProperties(
            "source-registry.com",
            "source-namespace",
            "source-image",
            "",
            ["source-tag"]
        )
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("target-registry.com")
        target.namespace.set("target-namespace")
        target.imageName.set("target-image")
        target.publishTags(["target-tag"])

        when:
        def result = sourceProps.applyTargetOverrides(target)

        then:
        result.registry == "target-registry.com"
        result.namespace == "target-namespace"
        result.imageName == "target-image"
        result.repository == ""
        result.tags == ["target-tag"]
    }

    def "applyTargetOverrides should inherit missing properties"() {
        given:
        def sourceProps = new EffectiveImageProperties(
            "source-registry.com",
            "source-namespace",
            "source-image",
            "",
            ["source-tag"]
        )
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("target-registry.com")
        // namespace and imageName not set - should inherit

        when:
        def result = sourceProps.applyTargetOverrides(target)

        then:
        result.registry == "target-registry.com"
        result.namespace == "source-namespace"  // Inherited
        result.imageName == "source-image"      // Inherited
        result.repository == ""
        result.tags == ["source-tag"]           // Inherited (empty publishTags)
    }

    def "applyTargetOverrides should handle empty target"() {
        given:
        def sourceProps = new EffectiveImageProperties(
            "source-registry.com",
            "source-namespace",
            "source-image",
            "",
            ["source-tag"]
        )
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        // All properties empty - should inherit everything

        when:
        def result = sourceProps.applyTargetOverrides(target)

        then:
        result.registry == "source-registry.com"
        result.namespace == "source-namespace"
        result.imageName == "source-image"
        result.repository == ""
        result.tags == ["source-tag"]
    }

    def "applyTargetOverrides should handle repository mode"() {
        given:
        def sourceProps = new EffectiveImageProperties(
            "source-registry.com",
            "",
            "",
            "source/repo",
            ["source-tag"]
        )
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("target-registry.com")
        target.repository.set("target/repo")
        target.publishTags(["target-tag"])

        when:
        def result = sourceProps.applyTargetOverrides(target)

        then:
        result.registry == "target-registry.com"
        result.namespace == ""
        result.imageName == ""
        result.repository == "target/repo"
        result.tags == ["target-tag"]
    }

    def "applyTargetOverrides should inherit source tags when target tags empty"() {
        given:
        def sourceProps = new EffectiveImageProperties(
            "registry.com",
            "namespace",
            "image",
            "",
            ["v1.0", "latest"]
        )
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("new-registry.com")
        // publishTags not set - should inherit source tags

        when:
        def result = sourceProps.applyTargetOverrides(target)

        then:
        result.registry == "new-registry.com"
        result.namespace == "namespace"
        result.imageName == "image"
        result.repository == ""
        result.tags == ["v1.0", "latest"]  // Inherited from source
    }

    def "constructor should handle null values"() {
        when:
        def result = new EffectiveImageProperties(null, null, null, null, null)

        then:
        result.registry == ""
        result.namespace == ""
        result.imageName == ""
        result.repository == ""
        result.tags == []
    }

    def "toString should provide useful debug information"() {
        given:
        def props = new EffectiveImageProperties(
            "docker.io",
            "library",
            "nginx",
            "",
            ["1.21"]
        )

        when:
        def result = props.toString()

        then:
        result.contains("docker.io")
        result.contains("library")
        result.contains("nginx")
        result.contains("1.21")
    }

    // ===== BUILDFULLREFERENCE TESTS =====

    def "buildFullReference handles namespace + imageName mode"() {
        given:
        def props = new EffectiveImageProperties(
            "docker.io",
            "library",
            "nginx",
            "",
            ["1.21", "latest"]
        )

        when:
        def result = props.buildFullReference()

        then:
        result == "docker.io/library/nginx:1.21"
    }

    def "buildFullReference handles repository mode"() {
        given:
        def props = new EffectiveImageProperties(
            "ghcr.io",
            "",
            "",
            "myorg/myapp",
            ["v1.0"]
        )

        when:
        def result = props.buildFullReference()

        then:
        result == "ghcr.io/myorg/myapp:v1.0"
    }

    def "buildFullReference handles repository mode without registry"() {
        given:
        def props = new EffectiveImageProperties(
            "",
            "",
            "",
            "myorg/myapp",
            ["latest"]
        )

        when:
        def result = props.buildFullReference()

        then:
        result == "myorg/myapp:latest"
    }

    def "buildFullReference handles imageName without registry"() {
        given:
        def props = new EffectiveImageProperties(
            "",
            "myorg",
            "myapp",
            "",
            ["v2.0"]
        )

        when:
        def result = props.buildFullReference()

        then:
        result == "myorg/myapp:v2.0"
    }

    def "buildFullReference handles imageName without namespace"() {
        given:
        def props = new EffectiveImageProperties(
            "docker.io",
            "",
            "nginx",
            "",
            ["latest"]
        )

        when:
        def result = props.buildFullReference()

        then:
        result == "docker.io/nginx:latest"
    }

    def "buildFullReference defaults to latest when tags empty"() {
        given:
        def props = new EffectiveImageProperties(
            "",
            "",
            "myapp",
            "",
            []
        )

        when:
        def result = props.buildFullReference()

        then:
        result == "myapp:latest"
    }

    def "buildFullReference throws exception when neither repository nor imageName is set"() {
        given:
        def props = new EffectiveImageProperties(
            "docker.io",
            "library",
            "",
            "",
            ["latest"]
        )

        when:
        props.buildFullReference()

        then:
        thrown(IllegalStateException)
    }
}