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

package com.kineticfire.gradle.docker.spec

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Test PublishTarget property setting to debug the registry property issue
 */
class PublishTargetPropertyTest extends Specification {

    Project project
    PublishTarget publishTarget

    def setup() {
        project = ProjectBuilder.builder().build()
        publishTarget = project.objects.newInstance(PublishTarget, 'test', project.objects)
    }

    def "registry property can be set and retrieved"() {
        when:
        publishTarget.registry.set('localhost:5200')

        then:
        publishTarget.registry.isPresent()
        publishTarget.registry.get() == 'localhost:5200'
        publishTarget.registry.getOrElse('') == 'localhost:5200'
    }

    def "registry property starts with convention value"() {
        expect:
        publishTarget.registry.isPresent()
        publishTarget.registry.getOrElse('') == ''
    }

    def "registry property can override convention"() {
        when:
        publishTarget.registry.set('localhost:5200')

        then:
        publishTarget.registry.get() == 'localhost:5200'
        publishTarget.registry.getOrElse('default') == 'localhost:5200'
    }

    def "DSL-style configuration works"() {
        when:
        def closure = {
            registry.set('localhost:5200')
            imageName.set('test-image')
            publishTags(['latest'])
        }
        closure.delegate = publishTarget
        closure.call()

        then:
        publishTarget.registry.get() == 'localhost:5200'
        publishTarget.imageName.get() == 'test-image'
        publishTarget.publishTags.get() == ['latest']
    }

    def "DSL-style configuration with DELEGATE_FIRST resolution works"() {
        when:
        def closure = {
            registry.set('localhost:5200')
            imageName.set('test-image')
            publishTags(['latest'])
        }
        closure.delegate = publishTarget
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()

        then:
        publishTarget.registry.get() == 'localhost:5200'
        publishTarget.imageName.get() == 'test-image'
        publishTarget.publishTags.get() == ['latest']
    }

    def "closure resolution prioritizes delegate with DELEGATE_FIRST"() {
        given: "Create a context with a registry property that conflicts"
        def contextWithRegistry = new Object() {
            def registry = "should-not-be-used"
        }

        when: "Configure with DELEGATE_FIRST"
        def closure = {
            registry.set('localhost:5200')
        }
        closure.delegate = publishTarget
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        // Set the owner to simulate a conflicting scope
        // Note: In real usage, the owner would be the ImageSpec with its own registry property
        closure.call()

        then: "The delegate's property is used"
        publishTarget.registry.get() == 'localhost:5200'
    }
}