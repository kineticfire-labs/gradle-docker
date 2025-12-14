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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for PublishTargetAuthSpec
 */
class PublishTargetAuthSpecTest extends Specification {

    Project project
    PublishTargetAuthSpec spec

    def setup() {
        project = ProjectBuilder.builder().build()
        spec = project.objects.newInstance(PublishTargetAuthSpec)
    }

    // ===== CONVENTION TESTS =====

    def "username has convention of empty string"() {
        expect:
        spec.username.get() == ''
    }

    def "password has convention of empty string"() {
        expect:
        spec.password.get() == ''
    }

    // ===== PROPERTY TESTS =====

    def "username property can be set"() {
        when:
        spec.username.set('myuser')

        then:
        spec.username.get() == 'myuser'
    }

    def "password property can be set"() {
        when:
        spec.password.set('mysecret')

        then:
        spec.password.get() == 'mysecret'
    }

    // ===== isConfigured TESTS =====

    def "isConfigured returns false with default conventions"() {
        expect:
        !spec.isConfigured()
    }

    def "isConfigured returns false when only username is set"() {
        when:
        spec.username.set('myuser')

        then:
        !spec.isConfigured()
    }

    def "isConfigured returns false when only password is set"() {
        when:
        spec.password.set('mysecret')

        then:
        !spec.isConfigured()
    }

    def "isConfigured returns false when username is empty"() {
        when:
        spec.username.set('')
        spec.password.set('mysecret')

        then:
        !spec.isConfigured()
    }

    def "isConfigured returns false when password is empty"() {
        when:
        spec.username.set('myuser')
        spec.password.set('')

        then:
        !spec.isConfigured()
    }

    def "isConfigured returns true when both username and password are set"() {
        when:
        spec.username.set('myuser')
        spec.password.set('mysecret')

        then:
        spec.isConfigured()
    }

    def "isConfigured returns true with non-empty credentials"() {
        when:
        spec.username.set('docker-user')
        spec.password.set('docker-token-12345')

        then:
        spec.isConfigured()
        spec.username.get() == 'docker-user'
        spec.password.get() == 'docker-token-12345'
    }
}
