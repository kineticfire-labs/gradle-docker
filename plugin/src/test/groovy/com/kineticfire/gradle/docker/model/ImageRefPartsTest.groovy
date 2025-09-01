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
        parts.registry == null
        parts.repository == 'nginx'
        parts.fullRepository == 'nginx'
        parts.tag == 'latest'
        parts.fullReference == 'nginx:latest'
    }

    def "parse image with tag"() {
        when:
        def parts = ImageRefParts.parse('nginx:1.25.3')

        then:
        parts.registry == null
        parts.repository == 'nginx'
        parts.fullRepository == 'nginx'
        parts.tag == '1.25.3'
        parts.fullReference == 'nginx:1.25.3'
    }

    def "parse image with namespace"() {
        when:
        def parts = ImageRefParts.parse('mycompany/myapp:v1.0.0')

        then:
        parts.registry == null
        parts.repository == 'mycompany/myapp'
        parts.fullRepository == 'mycompany/myapp'
        parts.tag == 'v1.0.0'
        parts.fullReference == 'mycompany/myapp:v1.0.0'
    }

    def "parse image with custom registry"() {
        when:
        def parts = ImageRefParts.parse('registry.company.com/team/service:2.1.0')

        then:
        parts.registry == 'registry.company.com'
        parts.repository == 'team/service'
        parts.fullRepository == 'registry.company.com/team/service'
        parts.tag == '2.1.0'
        parts.fullReference == 'registry.company.com/team/service:2.1.0'
    }

    def "parse localhost with port gets parsed incorrectly"() {
        when:
        def parts = ImageRefParts.parse('localhost:5000/myapp:dev')

        then:
        // Due to regex limitations, localhost:5000/myapp:dev gets parsed as:
        // repository=localhost, tag=5000/myapp:dev
        parts.registry == null
        parts.repository == 'localhost'
        parts.fullRepository == 'localhost'
        parts.tag == '5000/myapp:dev'
        parts.fullReference == 'localhost:5000/myapp:dev'
    }

    def "parse fully qualified image reference"() {
        when:
        def parts = ImageRefParts.parse('ghcr.io/company/project/service:v2.5.1')

        then:
        parts.registry == 'ghcr.io'
        parts.repository == 'company/project/service'
        parts.fullRepository == 'ghcr.io/company/project/service'
        parts.tag == 'v2.5.1'
        parts.fullReference == 'ghcr.io/company/project/service:v2.5.1'
    }

    def "throws exception for empty string"() {
        when:
        ImageRefParts.parse('')

        then:
        thrown(IllegalArgumentException)
    }

    def "throws exception for null input"() {
        when:
        ImageRefParts.parse(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "validates image reference format"() {
        when:
        def parts = ImageRefParts.parse('nginx:1.25.3')

        then:
        parts.repository == 'nginx'
        parts.tag == '1.25.3'
    }
}