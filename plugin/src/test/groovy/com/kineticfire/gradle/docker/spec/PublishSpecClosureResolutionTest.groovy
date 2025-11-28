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
 * Test PublishSpec closure resolution to ensure DSL configuration works correctly
 */
class PublishSpecClosureResolutionTest extends Specification {

    Project project
    PublishSpec publishSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        publishSpec = project.objects.newInstance(PublishSpec)
    }

    def "to(String, Closure) with registry configuration works"() {
        when:
        publishSpec.to('testRegistry') {
            registry.set('localhost:5200')
            imageName.set('test-image')
            publishTags(['latest'])
        }

        then:
        publishSpec.to.size() == 1
        def target = publishSpec.to.getByName('testRegistry')
        target.registry.get() == 'localhost:5200'
        target.imageName.get() == 'test-image'
        target.publishTags.get() == ['latest']
    }

    def "to(Closure) with registry configuration works"() {
        when:
        publishSpec.to {
            registry.set('localhost:5200')
            imageName.set('test-image')
            publishTags(['latest'])
        }

        then:
        publishSpec.to.size() == 1
        def target = publishSpec.to.first()
        target.registry.get() == 'localhost:5200'
        target.imageName.get() == 'test-image'
        target.publishTags.get() == ['latest']
    }

    def "multiple publish targets work correctly"() {
        when:
        publishSpec.to('prod') {
            registry.set('docker.io')
            imageName.set('prod-image')
            publishTags(['latest', 'v1.0'])
        }
        publishSpec.to('test') {
            registry.set('localhost:5200')
            imageName.set('test-image')
            publishTags(['test'])
        }

        then:
        publishSpec.to.size() == 2

        def prodTarget = publishSpec.to.getByName('prod')
        prodTarget.registry.get() == 'docker.io'
        prodTarget.imageName.get() == 'prod-image'
        prodTarget.publishTags.get() == ['latest', 'v1.0']

        def testTarget = publishSpec.to.getByName('test')
        testTarget.registry.get() == 'localhost:5200'
        testTarget.imageName.get() == 'test-image'
        testTarget.publishTags.get() == ['test']
    }

    def "registry property resolves to delegate not owner scope"() {
        given: "A context that has its own registry property (simulating ImageSpec)"
        def contextWithRegistry = new Object() {
            // This simulates ImageSpec having its own registry property
            def registry = project.objects.property(String)

            void configurePublish() {
                registry.set('context-registry-value')  // This should NOT be used by PublishTarget

                publishSpec.to('testTarget') {
                    registry.set('localhost:5200')  // This SHOULD be used by PublishTarget
                    imageName.set('test-image')
                    publishTags(['latest'])
                }
            }
        }

        when:
        contextWithRegistry.configurePublish()

        then:
        publishSpec.to.size() == 1
        def target = publishSpec.to.getByName('testTarget')
        target.registry.get() == 'localhost:5200'  // Should be PublishTarget value, not context value
        target.imageName.get() == 'test-image'
        target.publishTags.get() == ['latest']

        and: "Context registry remains unchanged"
        contextWithRegistry.registry.get() == 'context-registry-value'
    }

    def "validation passes with properly configured registry"() {
        given:
        publishSpec.to('validTarget') {
            registry.set('localhost:5200')
            imageName.set('test-image')
            publishTags(['latest'])
        }
        def target = publishSpec.to.getByName('validTarget')

        when:
        target.validateRegistry()

        then:
        noExceptionThrown()
    }

    def "validation fails when registry is not configured due to closure resolution issue"() {
        given: "A target that would fail if registry isn't properly resolved"
        publishSpec.to('invalidTarget') {
            // Intentionally not setting registry to test validation
            imageName.set('test-image')
            publishTags(['latest'])
        }
        def target = publishSpec.to.getByName('invalidTarget')

        when:
        target.validateRegistry()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Registry must be explicitly specified for publish target 'invalidTarget'")
    }
}