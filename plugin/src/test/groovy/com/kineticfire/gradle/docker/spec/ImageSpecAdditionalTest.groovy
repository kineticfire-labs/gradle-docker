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
 * Additional unit tests for ImageSpec to achieve 100% coverage
 * Focuses on edge cases and uncovered branches
 */
class ImageSpecAdditionalTest extends Specification {

    def project
    def imageSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)
    }

    // ===== PULLAUTH MULTIPLE CONFIGURATION TESTS =====
    // These tests cover the "if (!pullAuth)" branches that were partially covered

    def "pullAuth closure updates existing AuthSpec instead of creating new one"() {
        given: "pullAuth is already configured"
        imageSpec.pullAuth {
            username.set("initial-user")
            password.set("initial-pass")
        }
        def initialAuth = imageSpec.pullAuth

        when: "pullAuth is configured again"
        imageSpec.pullAuth {
            username.set("updated-user")
            password.set("updated-pass")
            registryToken.set("new-token")
        }

        then: "pullAuth is updated, not replaced"
        imageSpec.pullAuth == initialAuth  // Same instance
        imageSpec.pullAuth.username.get() == "updated-user"
        imageSpec.pullAuth.password.get() == "updated-pass"
        imageSpec.pullAuth.registryToken.get() == "new-token"
    }

    def "pullAuth action updates existing AuthSpec instead of creating new one"() {
        given: "pullAuth is already configured via closure"
        imageSpec.pullAuth {
            username.set("closure-user")
        }
        def initialAuth = imageSpec.pullAuth

        when: "pullAuth is configured again via action"
        imageSpec.pullAuth(new Action<AuthSpec>() {
            void execute(AuthSpec spec) {
                spec.username.set("action-user")
                spec.password.set("action-pass")
            }
        })

        then: "pullAuth is updated, not replaced"
        imageSpec.pullAuth == initialAuth  // Same instance
        imageSpec.pullAuth.username.get() == "action-user"
        imageSpec.pullAuth.password.get() == "action-pass"
    }

    // ===== EDGE CASES FOR LABEL AND BUILD ARG HELPERS =====

    def "label provider helper method handles null value correctly"() {
        when:
        imageSpec.label('MY_LABEL', (org.gradle.api.provider.Provider<String>) null)

        then:
        imageSpec.labels.get().isEmpty()
    }

    def "labels map helper method handles null value correctly"() {
        when:
        imageSpec.labels((Map<String, String>) null)

        then:
        imageSpec.labels.get().isEmpty()
    }

    def "buildArg provider helper method handles null value correctly"() {
        when:
        imageSpec.buildArg('MY_ARG', (org.gradle.api.provider.Provider<String>) null)

        then:
        imageSpec.buildArgs.get().isEmpty()
    }

    def "buildArgs map helper method handles null value correctly"() {
        when:
        imageSpec.buildArgs((Map<String, String>) null)

        then:
        imageSpec.buildArgs.get().isEmpty()
    }

    // ===== ADDITIONAL EDGE CASES FOR COMPLETE COVERAGE =====

    def "label helper method with non-null value adds to labels"() {
        when:
        imageSpec.label('app.name', 'myapp')
        imageSpec.label('app.version', '1.0.0')

        then:
        imageSpec.labels.get() == ['app.name': 'myapp', 'app.version': '1.0.0']
    }

    def "label helper method with Provider adds to labels"() {
        given:
        def versionProvider = project.provider { '2.0.0' }

        when:
        imageSpec.label('version', versionProvider)

        then:
        imageSpec.labels.get()['version'] == '2.0.0'
    }

    def "labels map helper method with non-null map adds to labels"() {
        when:
        imageSpec.labels(['author': 'team', 'project': 'gradle-docker'])

        then:
        imageSpec.labels.get() == ['author': 'team', 'project': 'gradle-docker']
    }

    def "labels Provider helper method with non-null provider adds to labels"() {
        given:
        def labelsProvider = project.provider { ['env': 'test', 'tier': 'backend'] }

        when:
        imageSpec.labels(labelsProvider)

        then:
        imageSpec.labels.get() == ['env': 'test', 'tier': 'backend']
    }

    def "buildArg helper method with non-null value adds to buildArgs"() {
        when:
        imageSpec.buildArg('VERSION', '1.0')
        imageSpec.buildArg('BUILD_DATE', '2023-12-01')

        then:
        imageSpec.buildArgs.get() == ['VERSION': '1.0', 'BUILD_DATE': '2023-12-01']
    }

    def "buildArg helper method with Provider adds to buildArgs"() {
        given:
        def versionProvider = project.provider { '3.0' }

        when:
        imageSpec.buildArg('APP_VERSION', versionProvider)

        then:
        imageSpec.buildArgs.get()['APP_VERSION'] == '3.0'
    }

    def "buildArgs map helper method with non-null map adds to buildArgs"() {
        when:
        imageSpec.buildArgs(['JAVA_VERSION': '17', 'GRADLE_VERSION': '8.0'])

        then:
        imageSpec.buildArgs.get() == ['JAVA_VERSION': '17', 'GRADLE_VERSION': '8.0']
    }

    def "buildArgs Provider helper method with non-null provider adds to buildArgs"() {
        given:
        def argsProvider = project.provider { ['NODE_ENV': 'production', 'PORT': '8080'] }

        when:
        imageSpec.buildArgs(argsProvider)

        then:
        imageSpec.buildArgs.get() == ['NODE_ENV': 'production', 'PORT': '8080']
    }
}
