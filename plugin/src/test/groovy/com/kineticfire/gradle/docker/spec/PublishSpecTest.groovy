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
        publishSpec = project.objects.newInstance(PublishSpec, project)
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
            repository.set('docker.io/myrepo')
            tags.set(['v1.0', 'latest'])
        }

        then:
        publishSpec.to.size() == 1
        publishSpec.to.getByName('target0').repository.get() == 'docker.io/myrepo'
        publishSpec.to.getByName('target0').tags.get() == ['v1.0', 'latest']
    }

    def "to(String, Closure) creates named target"() {
        when:
        publishSpec.to('production') {
            repository.set('registry.company.com/app')
            tags.set(['prod', 'v1.0.0'])
        }

        then:
        publishSpec.to.size() == 1
        publishSpec.to.getByName('production').name == 'production'
        publishSpec.to.getByName('production').repository.get() == 'registry.company.com/app'
        publishSpec.to.getByName('production').tags.get() == ['prod', 'v1.0.0']
    }

    def "to(Closure) with auth configuration"() {
        when:
        publishSpec.to {
            repository.set('private.registry.com/app')
            tags.set(['latest'])
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
                target.repository.set('docker.io/myapp')
                target.tags.set(['v2.0', 'stable'])
            }
        })

        then:
        publishSpec.to.size() == 1
        publishSpec.to.getByName('target0').repository.get() == 'docker.io/myapp'
        publishSpec.to.getByName('target0').tags.get() == ['v2.0', 'stable']
    }

    def "to(String, Action) creates named target"() {
        when:
        publishSpec.to('staging', new Action<PublishTarget>() {
            @Override
            void execute(PublishTarget target) {
                target.repository.set('staging.registry.com/app')
                target.tags.set(['staging'])
            }
        })

        then:
        publishSpec.to.size() == 1
        def target = publishSpec.to.getByName('staging')
        target.name == 'staging'
        target.repository.get() == 'staging.registry.com/app'
        target.tags.get() == ['staging']
    }

    def "to(Action) with auth configuration"() {
        when:
        publishSpec.to(new Action<PublishTarget>() {
            @Override
            void execute(PublishTarget target) {
                target.repository.set('secure.registry.com/app')
                target.tags.set(['secure'])
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
            repository.set('docker.io/app')
            tags.set(['latest'])
        }
        publishSpec.to {
            repository.set('quay.io/app')
            tags.set(['v1.0'])
        }

        then:
        publishSpec.to.size() == 2
        publishSpec.to.getByName('target0').repository.get() == 'docker.io/app'
        publishSpec.to.getByName('target1').repository.get() == 'quay.io/app'
    }

    def "mixed named and auto-named targets"() {
        when:
        publishSpec.to('dockerhub') {
            repository.set('docker.io/myapp')
            tags.set(['latest'])
        }
        publishSpec.to {
            repository.set('ghcr.io/myapp')
            tags.set(['dev'])
        }
        publishSpec.to('quay') {
            repository.set('quay.io/myapp')
            tags.set(['stable'])
        }

        then:
        publishSpec.to.size() == 3
        publishSpec.to.getByName('dockerhub').repository.get() == 'docker.io/myapp'
        publishSpec.to.getByName('target1').repository.get() == 'ghcr.io/myapp'
        publishSpec.to.getByName('quay').repository.get() == 'quay.io/myapp'
    }

    // ===== GETTER TESTS =====

    def "getTo returns the targets container"() {
        when:
        publishSpec.to('test') {
            repository.set('test.registry.com/app')
        }

        then:
        publishSpec.to != null
        publishSpec.getTo() == publishSpec.to
        publishSpec.getTo().size() == 1
        publishSpec.getTo().getByName('test').repository.get() == 'test.registry.com/app'
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
            repository.set('reg1.com/app')
        }
        publishSpec.to('registry2') {
            repository.set('reg2.com/app')
        }

        then:
        publishSpec.to.findByName('registry1') != null
        publishSpec.to.findByName('registry2') != null
        publishSpec.to.findByName('nonexistent') == null
    }
}