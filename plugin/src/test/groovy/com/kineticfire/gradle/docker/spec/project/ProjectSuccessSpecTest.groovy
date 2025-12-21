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

package com.kineticfire.gradle.docker.spec.project

import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ProjectSuccessSpec
 */
class ProjectSuccessSpecTest extends Specification {

    def project
    def successSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        successSpec = project.objects.newInstance(ProjectSuccessSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        successSpec != null
        successSpec.additionalTags.get() == []
        successSpec.saveCompression.get() == ''
        successSpec.publishTags.get() == []
    }

    // ===== ADDITIONAL TAGS TESTS =====

    def "additionalTags property works correctly"() {
        when:
        successSpec.additionalTags.set(['tested', 'stable', 'production'])

        then:
        successSpec.additionalTags.get() == ['tested', 'stable', 'production']
    }

    def "additionalTags can be added incrementally"() {
        when:
        successSpec.additionalTags.add('tag1')
        successSpec.additionalTags.add('tag2')

        then:
        successSpec.additionalTags.get() == ['tag1', 'tag2']
    }

    // ===== SAVE FILE TESTS =====

    def "saveFile property works correctly"() {
        when:
        successSpec.saveFile.set('build/images/my-app.tar.gz')

        then:
        successSpec.saveFile.present
        successSpec.saveFile.get() == 'build/images/my-app.tar.gz'
    }

    def "save shorthand method sets saveFile"() {
        when:
        successSpec.save('build/output/image.tar')

        then:
        successSpec.saveFile.get() == 'build/output/image.tar'
    }

    def "save with map sets file and compression"() {
        when:
        successSpec.save(file: 'build/image.tar', compression: 'gzip')

        then:
        successSpec.saveFile.get() == 'build/image.tar'
        successSpec.saveCompression.get() == 'gzip'
    }

    def "save with map sets only file when compression not provided"() {
        when:
        successSpec.save(file: 'build/image.tar.gz')

        then:
        successSpec.saveFile.get() == 'build/image.tar.gz'
        successSpec.saveCompression.get() == ''
    }

    // ===== COMPRESSION INFERENCE TESTS =====

    def "inferCompression returns GZIP for .tar.gz extension"() {
        when:
        successSpec.saveFile.set('image.tar.gz')

        then:
        successSpec.inferCompression() == SaveCompression.GZIP
    }

    def "inferCompression returns GZIP for .tgz extension"() {
        when:
        successSpec.saveFile.set('image.tgz')

        then:
        successSpec.inferCompression() == SaveCompression.GZIP
    }

    def "inferCompression returns BZIP2 for .tar.bz2 extension"() {
        when:
        successSpec.saveFile.set('image.tar.bz2')

        then:
        successSpec.inferCompression() == SaveCompression.BZIP2
    }

    def "inferCompression returns BZIP2 for .tbz2 extension"() {
        when:
        successSpec.saveFile.set('image.tbz2')

        then:
        successSpec.inferCompression() == SaveCompression.BZIP2
    }

    def "inferCompression returns XZ for .tar.xz extension"() {
        when:
        successSpec.saveFile.set('image.tar.xz')

        then:
        successSpec.inferCompression() == SaveCompression.XZ
    }

    def "inferCompression returns XZ for .txz extension"() {
        when:
        successSpec.saveFile.set('image.txz')

        then:
        successSpec.inferCompression() == SaveCompression.XZ
    }

    def "inferCompression returns ZIP for .zip extension"() {
        when:
        successSpec.saveFile.set('image.zip')

        then:
        successSpec.inferCompression() == SaveCompression.ZIP
    }

    def "inferCompression returns NONE for .tar extension"() {
        when:
        successSpec.saveFile.set('image.tar')

        then:
        successSpec.inferCompression() == SaveCompression.NONE
    }

    def "inferCompression uses explicit compression when set"() {
        when:
        successSpec.saveFile.set('image.bin')
        successSpec.saveCompression.set('gzip')

        then:
        successSpec.inferCompression() == SaveCompression.GZIP
    }

    def "inferCompression returns null when saveFile not set"() {
        expect:
        successSpec.inferCompression() == null
    }

    def "inferCompression throws for unrecognized extension without explicit compression"() {
        when:
        successSpec.saveFile.set('image.bin')
        successSpec.inferCompression()

        then:
        def e = thrown(GradleException)
        e.message.contains("Cannot infer compression from filename")
        e.message.contains("image.bin")
    }

    def "inferCompression throws for unknown explicit compression"() {
        when:
        successSpec.saveFile.set('image.tar')
        successSpec.saveCompression.set('unknown')
        successSpec.inferCompression()

        then:
        def e = thrown(GradleException)
        e.message.contains("Unknown compression type")
        e.message.contains("unknown")
    }

    def "parseCompression handles all valid compression types"() {
        when:
        successSpec.saveFile.set('file.tar')

        then:
        successSpec.saveCompression.set('none')
        successSpec.inferCompression() == SaveCompression.NONE

        successSpec.saveCompression.set('gzip')
        successSpec.inferCompression() == SaveCompression.GZIP

        successSpec.saveCompression.set('bzip2')
        successSpec.inferCompression() == SaveCompression.BZIP2

        successSpec.saveCompression.set('xz')
        successSpec.inferCompression() == SaveCompression.XZ

        successSpec.saveCompression.set('zip')
        successSpec.inferCompression() == SaveCompression.ZIP
    }

    def "parseCompression is case insensitive"() {
        when:
        successSpec.saveFile.set('file.tar')

        then:
        successSpec.saveCompression.set('GZIP')
        successSpec.inferCompression() == SaveCompression.GZIP

        successSpec.saveCompression.set('GzIp')
        successSpec.inferCompression() == SaveCompression.GZIP
    }

    // ===== PUBLISH TESTS =====

    def "publishRegistry property works correctly"() {
        when:
        successSpec.publishRegistry.set('registry.example.com')

        then:
        successSpec.publishRegistry.present
        successSpec.publishRegistry.get() == 'registry.example.com'
    }

    def "publishNamespace property works correctly"() {
        when:
        successSpec.publishNamespace.set('myproject')

        then:
        successSpec.publishNamespace.present
        successSpec.publishNamespace.get() == 'myproject'
    }

    def "publishTags property works correctly"() {
        when:
        successSpec.publishTags.set(['v1.0', 'latest'])

        then:
        successSpec.publishTags.get() == ['v1.0', 'latest']
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with save and publish"() {
        when:
        successSpec.additionalTags.set(['tested', 'verified'])
        successSpec.saveFile.set('build/images/app.tar.gz')
        successSpec.publishRegistry.set('ghcr.io')
        successSpec.publishNamespace.set('myorg')
        successSpec.publishTags.set(['release', 'stable'])

        then:
        successSpec.additionalTags.get() == ['tested', 'verified']
        successSpec.saveFile.get() == 'build/images/app.tar.gz'
        successSpec.inferCompression() == SaveCompression.GZIP
        successSpec.publishRegistry.get() == 'ghcr.io'
        successSpec.publishNamespace.get() == 'myorg'
        successSpec.publishTags.get() == ['release', 'stable']
    }

    def "minimal configuration with just additional tags"() {
        when:
        successSpec.additionalTags.set(['passed'])

        then:
        successSpec.additionalTags.get() == ['passed']
        !successSpec.saveFile.present
        !successSpec.publishRegistry.present
    }

    // ===== EDGE CASES =====

    def "empty additionalTags is valid"() {
        expect:
        successSpec.additionalTags.get() == []
    }

    def "single tag in additionalTags"() {
        when:
        successSpec.additionalTags.set(['production'])

        then:
        successSpec.additionalTags.get() == ['production']
        successSpec.additionalTags.get().size() == 1
    }

    def "saveFile with complex path"() {
        when:
        successSpec.saveFile.set('/absolute/path/to/build/docker/images/my-app-v1.0.0.tar.gz')

        then:
        successSpec.saveFile.get() == '/absolute/path/to/build/docker/images/my-app-v1.0.0.tar.gz'
        successSpec.inferCompression() == SaveCompression.GZIP
    }

    // ===== SAVE MAP BRANCH COVERAGE TESTS =====

    def "save with map sets only compression when file not provided"() {
        when:
        // Pre-set saveFile, then call save with only compression
        successSpec.saveFile.set('image.tar')
        successSpec.save(compression: 'gzip')

        then:
        successSpec.saveFile.get() == 'image.tar'
        successSpec.saveCompression.get() == 'gzip'
    }

    def "save with empty map does not change properties"() {
        given:
        successSpec.saveFile.set('original.tar')
        successSpec.saveCompression.set('none')

        when:
        successSpec.save([:])

        then:
        successSpec.saveFile.get() == 'original.tar'
        successSpec.saveCompression.get() == 'none'
    }

    // ===== PUBLISH ACTION BRANCH COVERAGE TESTS =====

    def "publish action configures targets"() {
        when:
        successSpec.publish({ container ->
            container.create('actionTarget') { target ->
                target.registry.set('action-registry.io')
                target.namespace.set('action-ns')
            }
        } as org.gradle.api.Action)

        then:
        successSpec.publishTargets.size() == 1
        def target = successSpec.publishTargets.findByName('actionTarget')
        target.registry.get() == 'action-registry.io'
        target.namespace.get() == 'action-ns'
    }

    // ===== HASFLATPUBLISHCONFIG BRANCH COVERAGE TESTS =====

    def "hasFlatPublishConfig returns false when publishRegistry is present but empty"() {
        when:
        successSpec.publishRegistry.set('')

        then:
        !successSpec.hasFlatPublishConfig()
    }

    def "hasFlatPublishConfig returns false when publishNamespace is present but empty"() {
        when:
        successSpec.publishNamespace.set('')

        then:
        !successSpec.hasFlatPublishConfig()
    }

    def "hasFlatPublishConfig returns true when only publishNamespace is set"() {
        when:
        successSpec.publishNamespace.set('myorg')

        then:
        successSpec.hasFlatPublishConfig()
    }

    def "hasFlatPublishConfig evaluates all conditions in order"() {
        when:
        // Set empty values for registry and namespace, but set publishTags
        successSpec.publishRegistry.set('')
        successSpec.publishNamespace.set('')
        successSpec.publishTags.set(['v1.0'])

        then:
        successSpec.hasFlatPublishConfig()
    }

    def "hasFlatPublishConfig returns false when all are empty or default"() {
        expect:
        // All default/empty values
        !successSpec.hasFlatPublishConfig()
    }

    // ===== INFERCOMPRESSION BRANCH COVERAGE TESTS =====

    def "inferCompression with .tgz extension"() {
        when:
        successSpec.saveFile.set('image.tgz')

        then:
        successSpec.inferCompression() == SaveCompression.GZIP
    }

    def "inferCompression with .tbz2 extension"() {
        when:
        successSpec.saveFile.set('image.tbz2')

        then:
        successSpec.inferCompression() == SaveCompression.BZIP2
    }

    def "inferCompression with .txz extension"() {
        when:
        successSpec.saveFile.set('image.txz')

        then:
        successSpec.inferCompression() == SaveCompression.XZ
    }

    def "inferCompression with explicit saveCompression set to non-empty value"() {
        when:
        successSpec.saveFile.set('image.unknown')
        successSpec.saveCompression.set('gzip')

        then:
        successSpec.inferCompression() == SaveCompression.GZIP
    }

    def "inferCompression with saveCompression set but empty falls back to filename inference"() {
        when:
        successSpec.saveFile.set('image.tar.xz')
        successSpec.saveCompression.set('')

        then:
        successSpec.inferCompression() == SaveCompression.XZ
    }
}
