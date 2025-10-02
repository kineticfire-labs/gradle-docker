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

import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.PublishTarget
import com.kineticfire.gradle.docker.service.DockerService
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DockerPublishTaskInheritanceTest extends Specification {

    Project project
    ObjectFactory objectFactory
    DockerPublishTask task
    DockerService dockerService

    def setup() {
        project = ProjectBuilder.builder().build()
        objectFactory = project.objects
        task = project.tasks.create("testPublish", DockerPublishTask)
        dockerService = Mock(DockerService)
        task.dockerService.set(dockerService)
    }

    def "buildSourceImageReference should use ImageSpec.getEffectiveSourceRef when available"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.sourceRefRegistry.set("docker.io")
        imageSpec.sourceRefNamespace.set("library")
        imageSpec.sourceRefImageName.set("nginx")
        imageSpec.sourceRefTag.set("1.21")
        task.imageSpec.set(imageSpec)

        when:
        def result = task.buildSourceImageReference()

        then:
        result == "docker.io/library/nginx:1.21"
    }

    def "buildSourceImageReference should fallback to task properties"() {
        given:
        task.registry.set("localhost:5000")
        task.namespace.set("company")
        task.imageName.set("app")
        task.tags.set(["latest"])

        when:
        def result = task.buildSourceImageReference()

        then:
        result == "localhost:5000/company/app:latest"
    }

    def "buildTargetImageReferences should inherit namespace from source"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.registry.set("localhost:5000")
        imageSpec.namespace.set("scenario3")
        imageSpec.imageName.set("scenario3-time-server")
        imageSpec.tags.set(["latest"])
        task.imageSpec.set(imageSpec)

        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("localhost:5031")
        target.imageName.set("scenario3-time-server")
        target.publishTags(["latest", "1.0.0"])

        when:
        def result = task.buildTargetImageReferences(target)

        then:
        result == [
            "localhost:5031/scenario3/scenario3-time-server:latest",
            "localhost:5031/scenario3/scenario3-time-server:1.0.0"
        ]
    }

    def "buildTargetImageReferences should handle empty target (inherit everything)"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.registry.set("docker.io")
        imageSpec.namespace.set("mycompany")
        imageSpec.imageName.set("webapp")
        imageSpec.tags.set(["v2.0"])
        task.imageSpec.set(imageSpec)

        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        // Empty target - should inherit everything

        when:
        def result = task.buildTargetImageReferences(target)

        then:
        result == ["docker.io/mycompany/webapp:v2.0"]
    }

    def "buildTargetImageReferences should handle repository mode inheritance"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.registry.set("ghcr.io")
        imageSpec.repository.set("company/project")
        imageSpec.tags.set(["stable"])
        task.imageSpec.set(imageSpec)

        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("docker.io")
        target.publishTags(["deployed"])
        // Should inherit repository

        when:
        def result = task.buildTargetImageReferences(target)

        then:
        result == ["docker.io/company/project:deployed"]
    }

    def "buildTargetImageReferences should handle partial overrides"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.namespace.set("dev")
        imageSpec.imageName.set("service")
        imageSpec.tags.set(["latest"])
        task.imageSpec.set(imageSpec)

        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("prod.company.com")
        target.namespace.set("production")
        target.publishTags(["deployed"])
        // Should inherit imageName only

        when:
        def result = task.buildTargetImageReferences(target)

        then:
        result == ["prod.company.com/production/service:deployed"]
    }

    def "buildTargetImageReferences should handle sourceRef component inheritance"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.sourceRefRegistry.set("ghcr.io")
        imageSpec.sourceRefNamespace.set("company")
        imageSpec.sourceRefImageName.set("myapp")
        imageSpec.sourceRefTag.set("v1.0")
        task.imageSpec.set(imageSpec)

        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("docker.io")
        target.publishTags(["latest"])
        // Should inherit namespace and imageName

        when:
        def result = task.buildTargetImageReferences(target)

        then:
        result == ["docker.io/company/myapp:latest"]
    }

    def "buildTargetImageReferences should default to latest when no tags available"() {
        given:
        task.registry.set("docker.io")
        task.imageName.set("app")
        task.tags.set([])  // No source tags

        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        // No publishTags either

        when:
        def result = task.buildTargetImageReferences(target)

        then:
        result == ["docker.io/app:latest"]  // Default tag
    }

    def "buildTargetImageReferences should handle missing required properties gracefully"() {
        given:
        task.registry.set("docker.io")
        // No imageName or repository

        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.publishTags(["test"])

        when:
        def result = task.buildTargetImageReferences(target)

        then:
        result == []  // Cannot build valid reference
    }

    def "buildTargetImageReferences should handle fallback to task properties"() {
        given:
        // No ImageSpec - should use task properties
        task.sourceRefRegistry.set("localhost:5000")
        task.sourceRefNamespace.set("project")
        task.sourceRefImageName.set("service")
        task.sourceRefTag.set("v1.0")

        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("prod.registry.com")
        target.publishTags(["deployed"])

        when:
        def result = task.buildTargetImageReferences(target)

        then:
        result == ["prod.registry.com/project/service:deployed"]
    }

    def "buildTargetImageReferences should handle direct sourceRef"() {
        given:
        def imageSpec = objectFactory.newInstance(ImageSpec, "test", project)
        imageSpec.sourceRef.set("ghcr.io/company/app:v2.0")
        task.imageSpec.set(imageSpec)

        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("docker.io")
        target.publishTags(["latest"])

        when:
        def result = task.buildTargetImageReferences(target)

        then:
        result == ["docker.io/company/app:latest"]
    }
}