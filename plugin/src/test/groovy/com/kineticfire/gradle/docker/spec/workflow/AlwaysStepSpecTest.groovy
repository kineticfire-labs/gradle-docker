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

package com.kineticfire.gradle.docker.spec.workflow

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for AlwaysStepSpec
 */
class AlwaysStepSpecTest extends Specification {

    def project
    def alwaysStepSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        alwaysStepSpec = project.objects.newInstance(AlwaysStepSpec, project.objects)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        alwaysStepSpec != null
        alwaysStepSpec.removeTestContainers.present
        alwaysStepSpec.removeTestContainers.get() == true
        alwaysStepSpec.keepFailedContainers.present
        alwaysStepSpec.keepFailedContainers.get() == false
        alwaysStepSpec.cleanupImages.present
        alwaysStepSpec.cleanupImages.get() == false
    }

    // ===== PROPERTY TESTS =====

    def "removeTestContainers property works correctly"() {
        when:
        alwaysStepSpec.removeTestContainers.set(false)

        then:
        alwaysStepSpec.removeTestContainers.present
        alwaysStepSpec.removeTestContainers.get() == false
    }

    def "removeTestContainers has default value of true"() {
        expect:
        alwaysStepSpec.removeTestContainers.present
        alwaysStepSpec.removeTestContainers.get() == true
    }

    def "keepFailedContainers property works correctly"() {
        when:
        alwaysStepSpec.keepFailedContainers.set(true)

        then:
        alwaysStepSpec.keepFailedContainers.present
        alwaysStepSpec.keepFailedContainers.get() == true
    }

    def "keepFailedContainers has default value of false"() {
        expect:
        alwaysStepSpec.keepFailedContainers.present
        alwaysStepSpec.keepFailedContainers.get() == false
    }

    def "cleanupImages property works correctly"() {
        when:
        alwaysStepSpec.cleanupImages.set(true)

        then:
        alwaysStepSpec.cleanupImages.present
        alwaysStepSpec.cleanupImages.get() == true
    }

    def "cleanupImages has default value of false"() {
        expect:
        alwaysStepSpec.cleanupImages.present
        alwaysStepSpec.cleanupImages.get() == false
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        when:
        alwaysStepSpec.removeTestContainers.set(false)
        alwaysStepSpec.keepFailedContainers.set(true)
        alwaysStepSpec.cleanupImages.set(true)

        then:
        alwaysStepSpec.removeTestContainers.get() == false
        alwaysStepSpec.keepFailedContainers.get() == true
        alwaysStepSpec.cleanupImages.get() == true
    }

    def "typical debugging configuration"() {
        when:
        alwaysStepSpec.removeTestContainers.set(false)
        alwaysStepSpec.keepFailedContainers.set(true)
        alwaysStepSpec.cleanupImages.set(false)

        then:
        alwaysStepSpec.removeTestContainers.get() == false
        alwaysStepSpec.keepFailedContainers.get() == true
        alwaysStepSpec.cleanupImages.get() == false
    }

    def "typical CI configuration"() {
        when:
        alwaysStepSpec.removeTestContainers.set(true)
        alwaysStepSpec.keepFailedContainers.set(false)
        alwaysStepSpec.cleanupImages.set(true)

        then:
        alwaysStepSpec.removeTestContainers.get() == true
        alwaysStepSpec.keepFailedContainers.get() == false
        alwaysStepSpec.cleanupImages.get() == true
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        when:
        alwaysStepSpec.removeTestContainers.set(false)
        alwaysStepSpec.keepFailedContainers.set(true)
        alwaysStepSpec.cleanupImages.set(true)

        then:
        alwaysStepSpec.removeTestContainers.get() == false
        alwaysStepSpec.keepFailedContainers.get() == true
        alwaysStepSpec.cleanupImages.get() == true

        when:
        alwaysStepSpec.removeTestContainers.set(true)
        alwaysStepSpec.keepFailedContainers.set(false)
        alwaysStepSpec.cleanupImages.set(false)

        then:
        alwaysStepSpec.removeTestContainers.get() == true
        alwaysStepSpec.keepFailedContainers.get() == false
        alwaysStepSpec.cleanupImages.get() == false
    }

    def "each property can be toggled independently"() {
        when:
        alwaysStepSpec.removeTestContainers.set(false)

        then:
        alwaysStepSpec.removeTestContainers.get() == false
        alwaysStepSpec.keepFailedContainers.get() == false  // Still default
        alwaysStepSpec.cleanupImages.get() == false  // Still default

        when:
        alwaysStepSpec.keepFailedContainers.set(true)

        then:
        alwaysStepSpec.removeTestContainers.get() == false
        alwaysStepSpec.keepFailedContainers.get() == true
        alwaysStepSpec.cleanupImages.get() == false  // Still default

        when:
        alwaysStepSpec.cleanupImages.set(true)

        then:
        alwaysStepSpec.removeTestContainers.get() == false
        alwaysStepSpec.keepFailedContainers.get() == true
        alwaysStepSpec.cleanupImages.get() == true
    }

    // ===== EDGE CASES =====

    def "all properties can be set to true"() {
        when:
        alwaysStepSpec.removeTestContainers.set(true)
        alwaysStepSpec.keepFailedContainers.set(true)
        alwaysStepSpec.cleanupImages.set(true)

        then:
        alwaysStepSpec.removeTestContainers.get() == true
        alwaysStepSpec.keepFailedContainers.get() == true
        alwaysStepSpec.cleanupImages.get() == true
    }

    def "all properties can be set to false"() {
        when:
        alwaysStepSpec.removeTestContainers.set(false)
        alwaysStepSpec.keepFailedContainers.set(false)
        alwaysStepSpec.cleanupImages.set(false)

        then:
        alwaysStepSpec.removeTestContainers.get() == false
        alwaysStepSpec.keepFailedContainers.get() == false
        alwaysStepSpec.cleanupImages.get() == false
    }

    def "properties can be toggled multiple times"() {
        expect:
        [true, false, true, false].each { value ->
            alwaysStepSpec.removeTestContainers.set(value)
            assert alwaysStepSpec.removeTestContainers.get() == value
        }
    }

    def "default configuration is safe for typical use"() {
        expect:
        alwaysStepSpec.removeTestContainers.get() == true  // Clean up containers
        alwaysStepSpec.keepFailedContainers.get() == false  // Don't keep failures by default
        alwaysStepSpec.cleanupImages.get() == false  // Keep images by default for reuse
    }

    def "conflicting settings can coexist"() {
        when:
        alwaysStepSpec.removeTestContainers.set(true)
        alwaysStepSpec.keepFailedContainers.set(true)

        then:
        alwaysStepSpec.removeTestContainers.get() == true
        alwaysStepSpec.keepFailedContainers.get() == true
    }

    def "boolean properties accept only true or false"() {
        expect:
        [true, false].each { value ->
            alwaysStepSpec.removeTestContainers.set(value)
            assert alwaysStepSpec.removeTestContainers.get() == value

            alwaysStepSpec.keepFailedContainers.set(value)
            assert alwaysStepSpec.keepFailedContainers.get() == value

            alwaysStepSpec.cleanupImages.set(value)
            assert alwaysStepSpec.cleanupImages.get() == value
        }
    }
}
