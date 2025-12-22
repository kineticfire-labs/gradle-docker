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
 * Unit tests for ImageRefParts
 */
class ImageRefPartsTest extends Specification {

    def "parse simple image name"() {
        when:
        def parts = ImageRefParts.parse('nginx')

        then:
        parts.registry == ""
        parts.repository == 'nginx'
        parts.fullRepository == 'nginx'
        parts.tag == 'latest'
        parts.fullReference == 'nginx:latest'
    }

    def "parse image with tag"() {
        when:
        def parts = ImageRefParts.parse('nginx:1.25.3')

        then:
        parts.registry == ""
        parts.repository == 'nginx'
        parts.fullRepository == 'nginx'
        parts.tag == '1.25.3'
        parts.fullReference == 'nginx:1.25.3'
    }

    def "parse image with namespace"() {
        when:
        def parts = ImageRefParts.parse('mycompany/myapp:v1.0.0')

        then:
        parts.registry == ""
        parts.namespace == 'mycompany'
        parts.repository == 'myapp'
        parts.fullRepository == 'mycompany/myapp'
        parts.tag == 'v1.0.0'
        parts.fullReference == 'mycompany/myapp:v1.0.0'
    }

    def "parse image with custom registry"() {
        when:
        def parts = ImageRefParts.parse('registry.company.com/team/service:2.1.0')

        then:
        parts.registry == 'registry.company.com'
        parts.namespace == 'team'
        parts.repository == 'service'
        parts.fullRepository == 'team/service'
        parts.tag == '2.1.0'
        parts.fullReference == 'team/service:2.1.0'
    }

    def "parse localhost with port"() {
        when:
        def parts = ImageRefParts.parse('localhost:5000/myapp:dev')

        then:
        // Now correctly parses localhost:5000 as registry
        parts.registry == 'localhost:5000'
        parts.repository == 'myapp'
        parts.fullRepository == 'myapp'
        parts.tag == 'dev'
        parts.fullReference == 'myapp:dev'
    }

    def "parse fully qualified image reference"() {
        when:
        def parts = ImageRefParts.parse('ghcr.io/company/project/service:v2.5.1')

        then:
        parts.registry == 'ghcr.io'
        parts.namespace == 'company/project'
        parts.repository == 'service'
        parts.fullRepository == 'company/project/service'
        parts.tag == 'v2.5.1'
        parts.fullReference == 'company/project/service:v2.5.1'
    }

    def "handles empty string gracefully"() {
        when:
        def parts = ImageRefParts.parse('')

        then:
        parts != null
        parts.repository == 'unknown'
        parts.tag == 'latest'
    }

    def "handles null input gracefully"() {
        when:
        def parts = ImageRefParts.parse(null)

        then:
        parts != null
        parts.repository == 'unknown'
        parts.tag == 'latest'
    }

    def "validates image reference format"() {
        when:
        def parts = ImageRefParts.parse('nginx:1.25.3')

        then:
        parts.repository == 'nginx'
        parts.tag == '1.25.3'
    }

    // ===== ISREGISTRYQUALIFIED TESTS =====

    def "isRegistryQualified returns true for qualified reference"() {
        when:
        def parts = ImageRefParts.parse('docker.io/library/nginx:1.21')

        then:
        parts.isRegistryQualified()
    }

    def "isRegistryQualified returns true for custom registry"() {
        when:
        def parts = ImageRefParts.parse('ghcr.io/myorg/myapp:latest')

        then:
        parts.isRegistryQualified()
    }

    def "isRegistryQualified returns false for unqualified reference"() {
        when:
        def parts = ImageRefParts.parse('nginx:latest')

        then:
        !parts.isRegistryQualified()
    }

    def "isRegistryQualified returns false for namespace only reference"() {
        when:
        def parts = ImageRefParts.parse('myorg/myapp:v1.0')

        then:
        !parts.isRegistryQualified()
    }

    // ===== ISDOCKERHUB TESTS =====

    def "isDockerHub returns true for unqualified reference"() {
        when:
        def parts = ImageRefParts.parse('nginx:latest')

        then:
        parts.isDockerHub()
    }

    def "isDockerHub returns true for docker.io registry"() {
        when:
        def parts = ImageRefParts.parse('docker.io/library/nginx:1.21')

        then:
        parts.isDockerHub()
    }

    def "isDockerHub returns false for non-Docker Hub registry"() {
        when:
        def parts = ImageRefParts.parse('ghcr.io/company/app:v1.0')

        then:
        !parts.isDockerHub()
    }

    def "isDockerHub returns false for custom registry"() {
        when:
        def parts = ImageRefParts.parse('registry.company.com/team/service:2.1.0')

        then:
        !parts.isDockerHub()
    }

    // ===== EQUALS METHOD TESTS =====

    def "equals returns true for same reference"() {
        when:
        def parts1 = ImageRefParts.parse('nginx:latest')
        def parts2 = parts1

        then:
        parts1.equals(parts2)
    }

    def "equals returns true for equal objects"() {
        when:
        def parts1 = ImageRefParts.parse('docker.io/library/nginx:1.21')
        def parts2 = ImageRefParts.parse('docker.io/library/nginx:1.21')

        then:
        parts1.equals(parts2)
    }

    def "equals returns false for different tags"() {
        when:
        def parts1 = ImageRefParts.parse('nginx:latest')
        def parts2 = ImageRefParts.parse('nginx:1.21')

        then:
        !parts1.equals(parts2)
    }

    def "equals returns false for different repositories"() {
        when:
        def parts1 = ImageRefParts.parse('nginx:latest')
        def parts2 = ImageRefParts.parse('httpd:latest')

        then:
        !parts1.equals(parts2)
    }

    def "equals returns false for different namespaces"() {
        when:
        def parts1 = ImageRefParts.parse('org1/app:latest')
        def parts2 = ImageRefParts.parse('org2/app:latest')

        then:
        !parts1.equals(parts2)
    }

    def "equals returns false for different registries"() {
        when:
        def parts1 = ImageRefParts.parse('docker.io/library/nginx:latest')
        def parts2 = ImageRefParts.parse('ghcr.io/library/nginx:latest')

        then:
        !parts1.equals(parts2)
    }

    def "equals returns false for non-ImageRefParts object"() {
        when:
        def parts = ImageRefParts.parse('nginx:latest')

        then:
        !parts.equals("nginx:latest")
        !parts.equals(null)
    }

    // ===== HASHCODE METHOD TESTS =====

    def "hashCode returns same value for equal objects"() {
        when:
        def parts1 = ImageRefParts.parse('docker.io/library/nginx:1.21')
        def parts2 = ImageRefParts.parse('docker.io/library/nginx:1.21')

        then:
        parts1.hashCode() == parts2.hashCode()
    }

    def "hashCode returns different values for different tags"() {
        when:
        def parts1 = ImageRefParts.parse('nginx:latest')
        def parts2 = ImageRefParts.parse('nginx:1.21')

        then:
        parts1.hashCode() != parts2.hashCode()
    }

    def "hashCode returns different values for different repositories"() {
        when:
        def parts1 = ImageRefParts.parse('nginx:latest')
        def parts2 = ImageRefParts.parse('httpd:latest')

        then:
        parts1.hashCode() != parts2.hashCode()
    }

    def "hashCode returns different values for different namespaces"() {
        when:
        def parts1 = ImageRefParts.parse('org1/app:latest')
        def parts2 = ImageRefParts.parse('org2/app:latest')

        then:
        parts1.hashCode() != parts2.hashCode()
    }

    def "hashCode returns different values for different registries"() {
        when:
        def parts1 = ImageRefParts.parse('docker.io/library/nginx:latest')
        def parts2 = ImageRefParts.parse('ghcr.io/library/nginx:latest')

        then:
        parts1.hashCode() != parts2.hashCode()
    }

    def "hashCode is consistent across multiple calls"() {
        given:
        def parts = ImageRefParts.parse('nginx:latest')
        def hash1 = parts.hashCode()
        def hash2 = parts.hashCode()
        def hash3 = parts.hashCode()

        expect:
        hash1 == hash2
        hash2 == hash3
    }

    def "hashCode uses all significant fields"() {
        given:
        def refs = [
            ImageRefParts.parse('nginx:latest'),
            ImageRefParts.parse('nginx:1.21'),
            ImageRefParts.parse('httpd:latest'),
            ImageRefParts.parse('myorg/nginx:latest'),
            ImageRefParts.parse('docker.io/library/nginx:latest')
        ]

        when:
        def hashCodes = refs.collect { it.hashCode() }

        then:
        // All hash codes should be different (though collisions are theoretically possible)
        hashCodes.toSet().size() == 5
    }

    def "equals and hashCode contract is maintained"() {
        given:
        def parts1 = ImageRefParts.parse('nginx:latest')
        def parts2 = ImageRefParts.parse('nginx:latest')
        def parts3 = ImageRefParts.parse('nginx:1.21')

        expect:
        // If objects are equal, hashCodes must be equal
        parts1.equals(parts2) implies parts1.hashCode() == parts2.hashCode()

        // If objects are not equal, hashCodes should be different (though not required)
        !parts1.equals(parts3)
    }

    // ===== EDGE CASE TESTS =====

    def "handles just colon input"() {
        when:
        def parts = ImageRefParts.parse(':')

        then:
        parts != null
        parts.registry == ""
        parts.namespace == ""
        parts.repository == 'unknown'
        parts.tag == 'latest'
    }

    def "handles colon with tag only"() {
        when:
        def parts = ImageRefParts.parse(':tag')

        then:
        // When colon is at position 0, the tag extraction logic is skipped
        // and ":tag" is treated as the repository name
        parts != null
        parts.registry == ""
        parts.namespace == ""
        parts.repository == ':tag'
        parts.tag == 'latest'
    }

    def "getFullRepository with empty namespace"() {
        when:
        def parts = ImageRefParts.parse('nginx:latest')

        then:
        parts.namespace == ""
        parts.repository == 'nginx'
        parts.fullRepository == 'nginx'
    }

    def "getFullRepository with namespace"() {
        when:
        def parts = ImageRefParts.parse('library/nginx:latest')

        then:
        parts.namespace == 'library'
        parts.repository == 'nginx'
        parts.fullRepository == 'library/nginx'
    }

    def "getFullRepository with multi-level namespace"() {
        when:
        def parts = ImageRefParts.parse('company/team/app:latest')

        then:
        parts.namespace == 'company/team'
        parts.repository == 'app'
        parts.fullRepository == 'company/team/app'
    }

    def "toString provides useful information"() {
        when:
        def parts = ImageRefParts.parse('docker.io/library/nginx:1.21')
        def string = parts.toString()

        then:
        string.contains('ImageRefParts')
        string.contains('docker.io')
        string.contains('library')
        string.contains('nginx')
        string.contains('1.21')
    }

    def "handles namespace with numbers and dashes"() {
        when:
        def parts = ImageRefParts.parse('company-123/my-app:v1.0')

        then:
        parts.namespace == 'company-123'
        parts.repository == 'my-app'
        parts.tag == 'v1.0'
    }

    def "handles repository with underscores"() {
        when:
        def parts = ImageRefParts.parse('my_app_name:latest')

        then:
        parts.repository == 'my_app_name'
        parts.tag == 'latest'
    }

    def "handles tags with special characters"() {
        when:
        def parts = ImageRefParts.parse('nginx:1.21.3-alpine')

        then:
        parts.repository == 'nginx'
        parts.tag == '1.21.3-alpine'
    }

    // ===== CONSTRUCTOR EDGE CASE TESTS =====

    def "constructor handles null repository"() {
        when:
        def parts = new ImageRefParts("docker.io", "library", null, "latest")

        then:
        parts.registry == "docker.io"
        parts.namespace == "library"
        parts.repository == "unknown"
        parts.tag == "latest"
    }

    def "constructor handles null namespace"() {
        when:
        def parts = new ImageRefParts("docker.io", null, "nginx", "latest")

        then:
        parts.registry == "docker.io"
        parts.namespace == null
        parts.repository == "nginx"
        parts.tag == "latest"
    }

    def "constructor handles null registry"() {
        when:
        def parts = new ImageRefParts(null, "library", "nginx", "latest")

        then:
        parts.registry == null
        parts.namespace == "library"
        parts.repository == "nginx"
        parts.tag == "latest"
        !parts.isRegistryQualified()
    }

    def "constructor handles null tag"() {
        when:
        def parts = new ImageRefParts("docker.io", "library", "nginx", null)

        then:
        parts.registry == "docker.io"
        parts.namespace == "library"
        parts.repository == "nginx"
        parts.tag == "latest"
    }

    def "getFullRepository handles null namespace"() {
        when:
        def parts = new ImageRefParts("docker.io", null, "nginx", "latest")

        then:
        parts.fullRepository == "nginx"
    }

    // ===== PORT VS TAG DISAMBIGUATION TESTS =====

    def "parse image with port in path and slash after colon"() {
        // Test where potentialTag contains '/' so tag extraction is skipped
        when:
        def parts = ImageRefParts.parse('localhost:5000/path/to/image')

        then:
        // The tag extraction is skipped because '/' appears after the last ':'
        // but that's not the case here - the last colon is part of localhost:5000
        // This tests the branch where potentialTag.contains('/') is true
        parts.registry == 'localhost:5000'
        parts.namespace == 'path/to'
        parts.repository == 'image'
        parts.tag == 'latest'
    }

    def "parse reference with registry port and nested path"() {
        when:
        def parts = ImageRefParts.parse('registry.example.com:5000/org/team/service:v2.0')

        then:
        parts.registry == 'registry.example.com:5000'
        parts.namespace == 'org/team'
        parts.repository == 'service'
        parts.tag == 'v2.0'
    }

    def "parse reference where potential tag contains slash skips tag extraction"() {
        // Test case where what follows the colon contains a slash
        // This should skip tag extraction because it's likely a path, not a tag
        // Example: "repo:path/to/something" - the ":path/to/something" looks like a path
        when:
        def parts = ImageRefParts.parse('something:8080/path')

        then:
        // "something" has a colon, and after that is "8080/path" which contains '/'
        // So tag extraction should be skipped
        parts.registry == 'something:8080'
        parts.repository == 'path'
        parts.tag == 'latest'
    }

    // ===== TWO PATH PARTS EDGE CASES =====

    def "parse with exactly two parts where first looks like registry"() {
        when:
        def parts = ImageRefParts.parse('my.registry.com/myapp')

        then:
        parts.registry == 'my.registry.com'
        parts.namespace == ''
        parts.repository == 'myapp'
        parts.tag == 'latest'
    }

    def "parse with exactly two parts where first is namespace"() {
        when:
        def parts = ImageRefParts.parse('myorg/myapp')

        then:
        parts.registry == ''
        parts.namespace == 'myorg'
        parts.repository == 'myapp'
        parts.tag == 'latest'
    }
}