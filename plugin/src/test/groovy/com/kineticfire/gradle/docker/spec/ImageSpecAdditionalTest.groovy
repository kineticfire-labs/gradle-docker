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

    // ===== VALIDATEMMODECONSISTENCY UNCOVERED ERROR PATHS =====

    def "validateModeConsistency throws when direct sourceRef is combined with sourceRefImageName"() {
        when:
        imageSpec.sourceRef.set("docker.io/library/nginx:latest")
        imageSpec.sourceRefImageName.set("conflicting")
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot use direct sourceRef with component assembly properties")
        ex.message.contains("testImage")
    }

    def "validateModeConsistency throws when direct sourceRef is combined with sourceRefNamespace"() {
        when:
        imageSpec.sourceRef.set("alpine:latest")
        imageSpec.sourceRefNamespace.set("conflicting")
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot use direct sourceRef with component assembly properties")
    }

    def "validateModeConsistency throws when direct sourceRef is combined with sourceRefRepository"() {
        when:
        imageSpec.sourceRef.set("nginx:1.21")
        imageSpec.sourceRefRepository.set("myorg/myapp")
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot use direct sourceRef with component assembly properties")
    }

    def "validateModeConsistency throws when direct sourceRef is combined with buildArgs"() {
        when:
        imageSpec.sourceRef.set("ubuntu:22.04")
        imageSpec.buildArgs.set(['VERSION': '1.0'])
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateModeConsistency throws when direct sourceRef is combined with labels"() {
        when:
        imageSpec.sourceRef.set("node:18")
        imageSpec.labels.set(['version': '1.0'])
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateModeConsistency throws when sourceRefRepository is combined with buildArgs"() {
        when:
        imageSpec.sourceRefRepository.set("company/webapp")
        imageSpec.buildArgs.set(['ENV': 'production'])
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateModeConsistency throws when sourceRefRepository is combined with labels"() {
        when:
        imageSpec.sourceRefRepository.set("myorg/backend")
        imageSpec.labels.set(['app': 'backend'])
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateModeConsistency throws when sourceRefRepository is combined with sourceRefImageName"() {
        when:
        imageSpec.sourceRefRepository.set("company/api")
        imageSpec.sourceRefImageName.set("alpine")
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot use both repository approach and namespace+imageName approach")
    }

    def "validateModeConsistency throws when sourceRefRepository is combined with sourceRefNamespace"() {
        when:
        imageSpec.sourceRefRepository.set("team/service")
        imageSpec.sourceRefNamespace.set("library")
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot use both repository approach and namespace+imageName approach")
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

    // ===== ADDITIONAL MODE CONSISTENCY TESTS =====

    def "validateModeConsistency succeeds with sourceRefTag alone"() {
        when:
        imageSpec.sourceRefTag.set("latest")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency throws when build mode context exists with sourceRefImageName"() {
        given:
        def contextDir = project.file('custom-context')
        contextDir.mkdirs()

        when:
        imageSpec.context.set(contextDir)
        imageSpec.sourceRefImageName.set("alpine")
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateModeConsistency succeeds with only sourceRefRegistry"() {
        when:
        imageSpec.sourceRefRegistry.set("ghcr.io")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency throws when contextTask is set with sourceRefImageName"() {
        when:
        imageSpec.contextTask = project.tasks.register("myContextTask")
        imageSpec.sourceRefImageName.set("ubuntu")
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateModeConsistency succeeds when traditional registry property alone is set"() {
        when:
        imageSpec.registry.set("docker.io")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds when traditional namespace property alone is set"() {
        when:
        imageSpec.namespace.set("myorg")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds when traditional imageName property alone is set"() {
        when:
        imageSpec.imageName.set("myimage")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds when traditional repository property alone is set"() {
        when:
        imageSpec.repository.set("myorg/myrepo")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }
}
