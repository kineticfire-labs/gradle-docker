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

import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for PublishSpec
 */
class PublishSpecTest extends Specification {

    def project
    def publishSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        publishSpec = project.objects.newInstance(PublishSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes correctly"() {
        expect:
        publishSpec != null
        publishSpec.to.isEmpty()
    }

    // ===== TO METHOD TESTS WITH CLOSURE =====

    def "to(Closure) creates target with automatic naming"() {
        when:
        publishSpec.to {
            publishTags.set(['v1.0', 'latest'])
        }

        then:
        publishSpec.to.size() == 1
        publishSpec.to.getByName('target0').publishTags.get() == ['v1.0', 'latest']
    }

    def "to(String, Closure) creates named target"() {
        when:
        publishSpec.to('production') {
            publishTags.set(['prod', 'v1.0.0'])
        }

        then:
        publishSpec.to.size() == 1
        publishSpec.to.getByName('production').name == 'production'
        publishSpec.to.getByName('production').publishTags.get() == ['prod', 'v1.0.0']
    }

    def "to(Closure) with auth configuration"() {
        when:
        publishSpec.to {
            publishTags.set(['latest'])
            auth {
                username.set('myuser')
                password.set('mypass')
            }
        }

        then:
        publishSpec.to.size() == 1
        def target = publishSpec.to.getByName('target0')
        target.auth.present
        target.auth.get().username.get() == 'myuser'
        target.auth.get().password.get() == 'mypass'
    }

    // ===== TO METHOD TESTS WITH ACTION =====

    def "to(Action) creates target with automatic naming"() {
        when:
        publishSpec.to(new Action<PublishTarget>() {
            @Override
            void execute(PublishTarget target) {
                target.publishTags.set(['v2.0', 'stable'])
            }
        })

        then:
        publishSpec.to.size() == 1
        publishSpec.to.getByName('target0').publishTags.get() == ['v2.0', 'stable']
    }

    def "to(String, Action) creates named target"() {
        when:
        publishSpec.to('staging', new Action<PublishTarget>() {
            @Override
            void execute(PublishTarget target) {
                target.publishTags.set(['staging'])
            }
        })

        then:
        publishSpec.to.size() == 1
        def target = publishSpec.to.getByName('staging')
        target.name == 'staging'
        target.publishTags.get() == ['staging']
    }

    def "to(Action) with auth configuration"() {
        when:
        publishSpec.to(new Action<PublishTarget>() {
            @Override
            void execute(PublishTarget target) {
                target.publishTags.set(['secure'])
                target.auth(new Action<AuthSpec>() {
                    @Override
                    void execute(AuthSpec auth) {
                        auth.username.set('serviceuser')
                        auth.registryToken.set('abc123')
                    }
                })
            }
        })

        then:
        publishSpec.to.size() == 1
        def target = publishSpec.to.getByName('target0')
        target.auth.present
        target.auth.get().username.get() == 'serviceuser'
        target.auth.get().registryToken.get() == 'abc123'
    }

    // ===== MULTIPLE TARGETS TESTS =====

    def "multiple to calls create multiple targets with auto-naming"() {
        when:
        publishSpec.to {
            publishTags.set(['latest'])
        }
        publishSpec.to {
            publishTags.set(['v1.0'])
        }

        then:
        publishSpec.to.size() == 2
        publishSpec.to.getByName('target0').publishTags.get() == ['latest']
        publishSpec.to.getByName('target1').publishTags.get() == ['v1.0']
    }

    def "mixed named and auto-named targets"() {
        when:
        publishSpec.to('dockerhub') {
            publishTags.set(['latest'])
        }
        publishSpec.to {
            publishTags.set(['dev'])
        }
        publishSpec.to('quay') {
            publishTags.set(['stable'])
        }

        then:
        publishSpec.to.size() == 3
        publishSpec.to.getByName('dockerhub').publishTags.get() == ['latest']
        publishSpec.to.getByName('target1').publishTags.get() == ['dev']
        publishSpec.to.getByName('quay').publishTags.get() == ['stable']
    }

    // ===== GETTER TESTS =====

    def "getTo returns the targets container"() {
        when:
        publishSpec.to('test') {
            publishTags.set(['latest'])
        }

        then:
        publishSpec.to != null
        publishSpec.getTo() == publishSpec.to
        publishSpec.getTo().size() == 1
        publishSpec.getTo().getByName('test').publishTags.get() == ['latest']
    }

    // ===== EDGE CASES =====

    def "empty publish spec has no targets"() {
        expect:
        publishSpec.to.isEmpty()
        publishSpec.to.size() == 0
    }

    def "targets can be accessed by name after creation"() {
        when:
        publishSpec.to('registry1') {
            publishTags.set(['latest'])
        }
        publishSpec.to('registry2') {
            publishTags.set(['latest'])
        }

        then:
        publishSpec.to.findByName('registry1') != null
        publishSpec.to.findByName('registry2') != null
        publishSpec.to.findByName('nonexistent') == null
    }
}