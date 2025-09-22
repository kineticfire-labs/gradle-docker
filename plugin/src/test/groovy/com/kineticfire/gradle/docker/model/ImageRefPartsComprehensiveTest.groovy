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

import spock.lang.Specification

/**
 * Comprehensive unit tests for ImageRefParts
 */
class ImageRefPartsComprehensiveTest extends Specification {

    def "parse simple image with tag"() {
        when:
        def parts = ImageRefParts.parse("alpine:3.16")

        then:
        parts.registry == ""
        parts.namespace == ""
        parts.repository == "alpine"
        parts.fullRepository == "alpine"
        parts.tag == "3.16"
    }

    def "parse image with namespace"() {
        when:
        def parts = ImageRefParts.parse("library/alpine:latest")

        then:
        parts.registry == ""
        parts.namespace == "library"
        parts.repository == "alpine"
        parts.fullRepository == "library/alpine"
        parts.tag == "latest"
    }

    def "parse image with registry"() {
        when:
        def parts = ImageRefParts.parse("docker.io/alpine:3.16")

        then:
        parts.registry == "docker.io"
        parts.namespace == ""
        parts.repository == "alpine"
        parts.fullRepository == "alpine"
        parts.tag == "3.16"
    }

    def "parse image with registry and namespace"() {
        when:
        def parts = ImageRefParts.parse("docker.io/library/alpine:latest")

        then:
        parts.registry == "docker.io"
        parts.namespace == "library"
        parts.repository == "alpine"
        parts.fullRepository == "library/alpine"
        parts.tag == "latest"
    }

    def "parse image with registry and port"() {
        when:
        def parts = ImageRefParts.parse("localhost:5000/myapp:v1.0")

        then:
        parts.registry == "localhost:5000"
        parts.namespace == ""
        parts.repository == "myapp"
        parts.fullRepository == "myapp"
        parts.tag == "v1.0"
    }

    def "parse complex registry with multiple path segments"() {
        when:
        def parts = ImageRefParts.parse("registry.io:8080/company/team/project:1.0.0")

        then:
        parts.registry == "registry.io:8080"
        parts.namespace == "company/team"
        parts.repository == "project"
        parts.fullRepository == "company/team/project"
        parts.tag == "1.0.0"
    }

    def "parse GitHub Container Registry format"() {
        when:
        def parts = ImageRefParts.parse("ghcr.io/username/repository:main")

        then:
        parts.registry == "ghcr.io"
        parts.namespace == "username"
        parts.repository == "repository"
        parts.fullRepository == "username/repository"
        parts.tag == "main"
    }

    def "parse Amazon ECR format"() {
        when:
        def parts = ImageRefParts.parse("123456789012.dkr.ecr.us-west-2.amazonaws.com/my-app:latest")

        then:
        parts.registry == "123456789012.dkr.ecr.us-west-2.amazonaws.com"
        parts.namespace == ""
        parts.repository == "my-app"
        parts.fullRepository == "my-app"
        parts.tag == "latest"
    }

    def "parse Google Container Registry format"() {
        when:
        def parts = ImageRefParts.parse("gcr.io/project-id/image-name:v2.0")

        then:
        parts.registry == "gcr.io"
        parts.namespace == "project-id"
        parts.repository == "image-name"
        parts.fullRepository == "project-id/image-name"
        parts.tag == "v2.0"
    }

    def "parse various tag formats"() {
        expect:
        def parts = ImageRefParts.parse("image:${tag}")
        parts.tag == tag

        where:
        tag << [
            "latest",
            "1.0.0",
            "v1.0.0",
            "stable",
            "main",
            "develop",
            "feature-branch",
            "build.123",
            "test_tag",
            "2023-12-01",
            "sha-a1b2c3d"
        ]
    }

    def "parse deep namespace hierarchies"() {
        when:
        def parts = ImageRefParts.parse("registry.com/org/team/subteam/project/component:tag")

        then:
        parts.registry == "registry.com"
        parts.namespace == "org/team/subteam/project"
        parts.repository == "component"
        parts.fullRepository == "org/team/subteam/project/component"
        parts.tag == "tag"
    }

    def "parse edge case image references"() {
        expect:
        def parts = ImageRefParts.parse(imageRef)
        parts.registry == expectedRegistry
        parts.namespace == expectedNamespace
        parts.repository == expectedRepository
        parts.fullRepository == expectedFullRepository
        parts.tag == expectedTag

        where:
        imageRef                              | expectedRegistry | expectedNamespace | expectedRepository | expectedFullRepository | expectedTag
        "a:b"                                | ""               | ""                | "a"                | "a"                    | "b"
        "registry.io/a:b"                    | "registry.io"    | ""                | "a"                | "a"                    | "b"
        "registry.io/ns/a:b"                 | "registry.io"    | "ns"              | "a"                | "ns/a"                 | "b"
        "localhost:5000/very/deep/path:tag"  | "localhost:5000" | "very/deep"       | "path"             | "very/deep/path"       | "tag"
        "r.io:80/a/b/c/d/e/f:tag"           | "r.io:80"        | "a/b/c/d/e"       | "f"                | "a/b/c/d/e/f"         | "tag"
    }

    def "constructor creates proper ImageRefParts"() {
        when:
        def parts = new ImageRefParts("registry.io", "namespace", "repository", "tag")

        then:
        parts.registry == "registry.io"
        parts.namespace == "namespace"
        parts.repository == "repository"
        parts.tag == "tag"
        parts.fullRepository == "namespace/repository"
    }

    def "constructor with empty namespace"() {
        when:
        def parts = new ImageRefParts("registry.io", "", "repository", "tag")

        then:
        parts.registry == "registry.io"
        parts.namespace == ""
        parts.repository == "repository"
        parts.tag == "tag"
        parts.fullRepository == "repository"
    }

    def "constructor with null namespace"() {
        when:
        def parts = new ImageRefParts("registry.io", null, "repository", "tag")

        then:
        parts.registry == "registry.io"
        parts.namespace == null
        parts.repository == "repository"
        parts.tag == "tag"
        parts.fullRepository == "repository"
    }

    def "fullRepository property logic"() {
        expect:
        def parts = new ImageRefParts(registry, namespace, repository, tag)
        parts.fullRepository == expectedFullRepository

        where:
        registry      | namespace  | repository | tag   | expectedFullRepository
        "registry.io" | "ns"       | "repo"     | "tag" | "ns/repo"
        "registry.io" | ""         | "repo"     | "tag" | "repo"
        "registry.io" | null       | "repo"     | "tag" | "repo"
        ""            | "ns"       | "repo"     | "tag" | "ns/repo"
        ""            | ""         | "repo"     | "tag" | "repo"
        ""            | null       | "repo"     | "tag" | "repo"
    }

    def "equals and hashCode contract"() {
        given:
        def parts1 = new ImageRefParts("registry", "namespace", "repo", "tag")
        def parts2 = new ImageRefParts("registry", "namespace", "repo", "tag")
        def parts3 = new ImageRefParts("different", "namespace", "repo", "tag")

        expect:
        parts1 == parts2
        parts1.hashCode() == parts2.hashCode()
        parts1 != parts3
        parts1 != null
        parts1 != "string"
    }

    def "toString contains relevant information"() {
        given:
        def parts = new ImageRefParts("registry.io", "namespace", "repository", "tag")

        when:
        def string = parts.toString()

        then:
        string.contains("ImageRefParts")
        string.contains("registry.io")
        string.contains("namespace")
        string.contains("repository")
        string.contains("tag")
    }

    def "parse handles malformed input gracefully"() {
        expect:
        // These should not throw exceptions, but may have unexpected results
        ImageRefParts.parse(input) != null

        where:
        input << [
            "",
            ":",
            "image",
            "image:",
            ":tag",
            "registry.io/",
            "registry.io/:tag",
            "/image:tag",
            "//image:tag",
            "registry.io//image:tag"
        ]
    }

    def "parse complex real-world examples"() {
        expect:
        def parts = ImageRefParts.parse(imageRef)
        parts.registry == expectedRegistry
        parts.fullRepository == expectedFullRepository
        parts.tag == expectedTag

        where:
        imageRef                                                                    | expectedRegistry                                        | expectedFullRepository                        | expectedTag
        "docker.io/library/ubuntu:20.04"                                          | "docker.io"                                             | "library/ubuntu"                              | "20.04"
        "ghcr.io/actions/runner:latest"                                           | "ghcr.io"                                               | "actions/runner"                              | "latest"
        "quay.io/prometheus/prometheus:v2.30.0"                                   | "quay.io"                                               | "prometheus/prometheus"                       | "v2.30.0"
        "123456789012.dkr.ecr.us-east-1.amazonaws.com/my-web-app:production"     | "123456789012.dkr.ecr.us-east-1.amazonaws.com"        | "my-web-app"                                  | "production"
        "europe-west1-docker.pkg.dev/my-project/my-repo/my-image:1.0.0"          | "europe-west1-docker.pkg.dev"                          | "my-project/my-repo/my-image"                | "1.0.0"
        "registry.gitlab.com/group/project/image:main"                            | "registry.gitlab.com"                                  | "group/project/image"                        | "main"
        "localhost:5000/test/app:dev"                                             | "localhost:5000"                                        | "test/app"                                    | "dev"
    }

    def "roundtrip parsing and reconstruction"() {
        given:
        def originalRef = "registry.io:8080/company/team/project:1.0.0"

        when:
        def parts = ImageRefParts.parse(originalRef)
        def reconstructed = "${parts.registry}/${parts.fullRepository}:${parts.tag}"

        then:
        reconstructed == originalRef
    }

    def "immutability of parsed parts"() {
        given:
        def parts = ImageRefParts.parse("registry.io/namespace/repo:tag")

        expect:
        // Fields should be immutable
        parts.registry == "registry.io"
        parts.namespace == "namespace"  
        parts.repository == "repo"
        parts.tag == "tag"
        parts.fullRepository == "namespace/repo"
    }
}