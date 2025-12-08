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

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ProjectFailureSpec
 */
class ProjectFailureSpecTest extends Specification {

    def project
    def failureSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        failureSpec = project.objects.newInstance(ProjectFailureSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        failureSpec != null
        failureSpec.additionalTags.get() == []
    }

    // ===== PROPERTY TESTS =====

    def "additionalTags property works correctly"() {
        when:
        failureSpec.additionalTags.set(['failed', 'needs-review'])

        then:
        failureSpec.additionalTags.get() == ['failed', 'needs-review']
    }

    def "additionalTags can be added incrementally"() {
        when:
        failureSpec.additionalTags.add('broken')
        failureSpec.additionalTags.add('fix-required')

        then:
        failureSpec.additionalTags.get() == ['broken', 'fix-required']
    }

    def "additionalTags has default empty list"() {
        expect:
        failureSpec.additionalTags.present
        failureSpec.additionalTags.get() == []
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        when:
        failureSpec.additionalTags.set(['initial-failure'])

        then:
        failureSpec.additionalTags.get() == ['initial-failure']

        when:
        failureSpec.additionalTags.set(['updated-failure', 'critical'])

        then:
        failureSpec.additionalTags.get() == ['updated-failure', 'critical']
    }

    def "additionalTags can be cleared and reset"() {
        when:
        failureSpec.additionalTags.add('tag1')
        failureSpec.additionalTags.add('tag2')

        then:
        failureSpec.additionalTags.get() == ['tag1', 'tag2']

        when:
        failureSpec.additionalTags.set([])

        then:
        failureSpec.additionalTags.get() == []

        when:
        failureSpec.additionalTags.add('new-tag')

        then:
        failureSpec.additionalTags.get() == ['new-tag']
    }

    // ===== EDGE CASES =====

    def "empty additionalTags is valid configuration"() {
        when:
        failureSpec.additionalTags.set([])

        then:
        failureSpec.additionalTags.present
        failureSpec.additionalTags.get() == []
    }

    def "single tag in additionalTags"() {
        when:
        failureSpec.additionalTags.set(['build-failed'])

        then:
        failureSpec.additionalTags.get() == ['build-failed']
        failureSpec.additionalTags.get().size() == 1
    }

    def "multiple tags with various naming conventions"() {
        when:
        failureSpec.additionalTags.set(['failed', 'test-failed', 'ci-failure', 'needs_review', 'v1.0-broken'])

        then:
        failureSpec.additionalTags.get().size() == 5
        failureSpec.additionalTags.get().contains('failed')
        failureSpec.additionalTags.get().contains('test-failed')
        failureSpec.additionalTags.get().contains('ci-failure')
        failureSpec.additionalTags.get().contains('needs_review')
        failureSpec.additionalTags.get().contains('v1.0-broken')
    }
}
