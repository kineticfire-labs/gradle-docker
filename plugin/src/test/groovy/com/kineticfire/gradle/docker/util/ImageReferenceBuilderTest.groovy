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

package com.kineticfire.gradle.docker.util

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Comprehensive tests for ImageReferenceBuilder utility
 * Achieves 100% code and branch coverage
 */
class ImageReferenceBuilderTest extends Specification {

    // Repository-style tests

    def "builds repository-style reference without registry"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "", "", "myapp", "", ["1.0.0", "latest"]
        )

        then:
        refs == ["myapp:1.0.0", "myapp:latest"]
    }

    def "builds repository-style reference with registry"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "docker.io", "", "library/nginx", "", ["1.0.0", "latest"]
        )

        then:
        refs == ["docker.io/library/nginx:1.0.0", "docker.io/library/nginx:latest"]
    }

    def "builds repository-style reference with registry containing port"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "localhost:5000", "", "myapp", "", ["dev"]
        )

        then:
        refs == ["localhost:5000/myapp:dev"]
    }

    def "builds repository-style reference with single tag"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "gcr.io", "", "project/app", "", ["stable"]
        )

        then:
        refs == ["gcr.io/project/app:stable"]
    }

    // Namespace + imageName style tests

    def "builds namespace-style reference without registry or namespace"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "", "", "", "nginx", ["latest"]
        )

        then:
        refs == ["nginx:latest"]
    }

    def "builds namespace-style reference with namespace but no registry"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "", "library", "", "nginx", ["1.0.0", "latest"]
        )

        then:
        refs == ["library/nginx:1.0.0", "library/nginx:latest"]
    }

    def "builds namespace-style reference with registry and namespace"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "docker.io", "mycompany", "", "myapp", ["v1.2.3"]
        )

        then:
        refs == ["docker.io/mycompany/myapp:v1.2.3"]
    }

    def "builds namespace-style reference with registry but no namespace"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "quay.io", "", "", "redis", ["alpine"]
        )

        then:
        refs == ["quay.io/redis:alpine"]
    }

    def "builds namespace-style reference with multi-level namespace"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "gcr.io", "company/team/project", "", "service", ["latest"]
        )

        then:
        refs == ["gcr.io/company/team/project/service:latest"]
    }

    // Edge cases

    def "returns empty list when both repository and imageName are empty"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "docker.io", "library", "", "", ["latest"]
        )

        then:
        refs == []
    }

    def "handles empty tags list for repository style"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "", "", "myapp", "", []
        )

        then:
        refs == []
    }

    def "handles empty tags list for namespace style"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            "", "lib", "", "app", []
        )

        then:
        refs == []
    }

    // Helper method tests

    def "buildRepositoryStyleReference with empty registry"() {
        when:
        def ref = ImageReferenceBuilder.buildRepositoryStyleReference("", "myapp")

        then:
        ref == "myapp"
    }

    def "buildRepositoryStyleReference with registry"() {
        when:
        def ref = ImageReferenceBuilder.buildRepositoryStyleReference("docker.io", "library/nginx")

        then:
        ref == "docker.io/library/nginx"
    }

    def "buildNamespaceStyleReference with all components"() {
        when:
        def ref = ImageReferenceBuilder.buildNamespaceStyleReference("docker.io", "library", "nginx")

        then:
        ref == "docker.io/library/nginx"
    }

    def "buildNamespaceStyleReference with registry and imageName only"() {
        when:
        def ref = ImageReferenceBuilder.buildNamespaceStyleReference("gcr.io", "", "myapp")

        then:
        ref == "gcr.io/myapp"
    }

    def "buildNamespaceStyleReference with namespace and imageName only"() {
        when:
        def ref = ImageReferenceBuilder.buildNamespaceStyleReference("", "company", "app")

        then:
        ref == "company/app"
    }

    def "buildNamespaceStyleReference with imageName only"() {
        when:
        def ref = ImageReferenceBuilder.buildNamespaceStyleReference("", "", "nginx")

        then:
        ref == "nginx"
    }

    // Parameterized tests for comprehensive coverage

    @Unroll
    def "builds correct reference for scenario: #scenario"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            registry, namespace, repository, imageName, tags
        )

        then:
        refs == expected

        where:
        scenario                          | registry         | namespace    | repository     | imageName | tags              || expected
        "simple image"                    | ""               | ""           | ""             | "nginx"   | ["latest"]        || ["nginx:latest"]
        "image with namespace"            | ""               | "library"    | ""             | "nginx"   | ["1.0"]           || ["library/nginx:1.0"]
        "image with registry"             | "docker.io"      | ""           | ""             | "nginx"   | ["latest"]        || ["docker.io/nginx:latest"]
        "full namespace style"            | "docker.io"      | "library"    | ""             | "nginx"   | ["1.0", "latest"] || ["docker.io/library/nginx:1.0", "docker.io/library/nginx:latest"]
        "repository style"                | ""               | ""           | "my/repo"      | ""        | ["v1"]            || ["my/repo:v1"]
        "repository with registry"        | "localhost:5000" | ""           | "app"          | ""        | ["dev"]           || ["localhost:5000/app:dev"]
        "registry with port"              | "registry:443"   | "team"       | ""             | "svc"     | ["1.0"]           || ["registry:443/team/svc:1.0"]
        "multi-level repo"                | "gcr.io"         | ""           | "proj/sub/app" | ""        | ["stable"]        || ["gcr.io/proj/sub/app:stable"]
        "complex namespace"               | "quay.io"        | "org/team"   | ""             | "service" | ["2.0"]           || ["quay.io/org/team/service:2.0"]
        "multiple tags"                   | ""               | ""           | "app"          | ""        | ["1.0", "1", "v1"]|| ["app:1.0", "app:1", "app:v1"]
    }

    @Unroll
    def "handles edge case: #description"() {
        when:
        def refs = ImageReferenceBuilder.buildImageReferences(
            registry, namespace, repository, imageName, tags
        )

        then:
        refs == expected

        where:
        description                  | registry | namespace | repository | imageName | tags     || expected
        "no repo or image"           | "reg"    | "ns"      | ""         | ""        | ["tag"]  || []
        "empty tags"                 | ""       | ""        | "repo"     | ""        | []       || []
        "registry only, no target"   | "reg"    | ""        | ""         | ""        | ["tag"]  || []
        "namespace only, no target"  | ""       | "ns"      | ""         | ""        | ["tag"]  || []
    }
}
