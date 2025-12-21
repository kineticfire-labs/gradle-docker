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

import com.kineticfire.gradle.docker.PullPolicy
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for Phase 1 additions to ProjectImageSpec
 */
class ProjectImageSpecPhase1Test extends Specification {

    Project project
    ProjectImageSpec spec

    def setup() {
        project = ProjectBuilder.builder().build()
        spec = project.objects.newInstance(ProjectImageSpec)
    }

    // ===== NEW PROPERTY CONVENTIONS TESTS =====

    def "imageName property is optional"() {
        expect:
        !spec.imageName.isPresent()
    }

    def "primary has convention of false"() {
        expect:
        spec.primary.get() == false
    }

    def "dockerfileName has convention of empty string"() {
        expect:
        spec.dockerfileName.get() == ''
    }

    def "jarName has convention of 'app.jar'"() {
        expect:
        spec.jarName.get() == 'app.jar'
    }

    def "contextTaskName has convention of empty string"() {
        expect:
        spec.contextTaskName.get() == ''
    }

    def "contextTaskPath has convention of empty string"() {
        expect:
        spec.contextTaskPath.get() == ''
    }

    def "repository has convention of empty string"() {
        expect:
        spec.repository.get() == ''
    }

    def "pullPolicy has convention of NEVER"() {
        expect:
        spec.pullPolicy.get() == PullPolicy.NEVER
    }

    // ===== NEW PROPERTY SETTERS TESTS =====

    def "imageName property can be set"() {
        when:
        spec.imageName.set('my-app')

        then:
        spec.imageName.get() == 'my-app'
    }

    def "primary property can be set"() {
        when:
        spec.primary.set(true)

        then:
        spec.primary.get() == true
    }

    def "dockerfileName property can be set"() {
        when:
        spec.dockerfileName.set('Dockerfile.prod')

        then:
        spec.dockerfileName.get() == 'Dockerfile.prod'
    }

    def "jarName property can be set"() {
        when:
        spec.jarName.set('myapp.jar')

        then:
        spec.jarName.get() == 'myapp.jar'
    }

    def "repository property can be set"() {
        when:
        spec.repository.set('myorg/myapp')

        then:
        spec.repository.get() == 'myorg/myapp'
    }

    def "pullPolicy property can be set to IF_MISSING"() {
        when:
        spec.pullPolicy.set(PullPolicy.IF_MISSING)

        then:
        spec.pullPolicy.get() == PullPolicy.IF_MISSING
    }

    def "pullPolicy property can be set to ALWAYS"() {
        when:
        spec.pullPolicy.set(PullPolicy.ALWAYS)

        then:
        spec.pullPolicy.get() == PullPolicy.ALWAYS
    }

    // ===== CONTEXT TASK TRIPLE-PROPERTY TESTS =====

    def "contextTask method sets contextTaskName"() {
        given:
        def taskProvider = project.tasks.register('prepareContext')

        when:
        spec.contextTask(taskProvider)

        then:
        spec.contextTaskName.get() == 'prepareContext'
    }

    def "contextTask method sets contextTaskPath"() {
        given:
        def taskProvider = project.tasks.register('prepareContext')

        when:
        spec.contextTask(taskProvider)
        // Force configuration to populate contextTaskPath
        taskProvider.get()

        then:
        spec.contextTaskPath.get() == ':prepareContext'
    }

    def "contextTask stores TaskProvider reference"() {
        given:
        def taskProvider = project.tasks.register('prepareContext')

        when:
        spec.contextTask(taskProvider)

        then:
        spec.contextTask == taskProvider
    }

    def "contextTask method handles null"() {
        when:
        spec.contextTask(null)

        then:
        spec.contextTask == null
        spec.contextTaskName.get() == ''
    }

    // ===== isBuildMode TESTS WITH CONTEXT TASK =====

    def "isBuildMode returns true when contextTaskName is set"() {
        when:
        spec.contextTaskName.set('prepareContext')

        then:
        spec.isBuildMode()
    }

    def "isBuildMode returns false when contextTaskName is empty"() {
        expect:
        !spec.isBuildMode()
    }

    // ===== DSL HELPER METHODS TESTS =====

    def "buildArg method adds to buildArgs"() {
        when:
        spec.buildArg('VERSION', '1.0.0')
        spec.buildArg('ENV', 'production')

        then:
        spec.buildArgs.get() == [VERSION: '1.0.0', ENV: 'production']
    }

    def "label method adds to labels"() {
        when:
        spec.label('maintainer', 'team@example.com')
        spec.label('version', '1.0')

        then:
        spec.labels.get() == [maintainer: 'team@example.com', version: '1.0']
    }

    // ===== SOURCE REF DSL TESTS =====

    def "sourceRef closure sets sourceRef property"() {
        when:
        spec.sourceRef {
            registry = 'docker.io'
            namespace = 'library'
            name = 'nginx'
            tag = 'latest'
        }

        then:
        spec.sourceRef.get() == 'docker.io/library/nginx:latest'
    }

    def "sourceRef closure sets component properties"() {
        when:
        spec.sourceRef {
            registry = 'docker.io'
            namespace = 'library'
            name = 'nginx'
            tag = 'latest'
        }

        then:
        spec.sourceRefRegistry.get() == 'docker.io'
        spec.sourceRefNamespace.get() == 'library'
        spec.sourceRefImageName.get() == 'nginx'
        spec.sourceRefTag.get() == 'latest'
    }

    def "sourceRef closure handles registry only"() {
        when:
        spec.sourceRef {
            registry = 'gcr.io'
            name = 'my-image'
        }

        then:
        spec.sourceRef.get() == 'gcr.io/my-image'
    }

    def "sourceRef closure handles repository style"() {
        when:
        spec.sourceRef {
            registry = 'quay.io'
            repository = 'prometheus/prometheus'
            tag = 'v2.40.0'
        }

        then:
        spec.sourceRef.get() == 'quay.io/prometheus/prometheus:v2.40.0'
    }

    // ===== getEffectiveSourceRef TESTS =====

    def "getEffectiveSourceRef returns explicit sourceRef when set"() {
        when:
        spec.sourceRef.set('docker.io/library/nginx:1.25')

        then:
        spec.getEffectiveSourceRef() == 'docker.io/library/nginx:1.25'
    }

    def "getEffectiveSourceRef computes from components when sourceRef not set"() {
        when:
        spec.sourceRefRegistry.set('docker.io')
        spec.sourceRefNamespace.set('library')
        spec.sourceRefImageName.set('nginx')
        spec.sourceRefTag.set('latest')

        then:
        spec.getEffectiveSourceRef() == 'docker.io/library/nginx:latest'
    }

    def "getEffectiveSourceRef uses repository over namespace+name"() {
        when:
        spec.sourceRefRegistry.set('quay.io')
        spec.sourceRefRepository.set('prometheus/prometheus')
        spec.sourceRefTag.set('v2.40.0')

        then:
        spec.getEffectiveSourceRef() == 'quay.io/prometheus/prometheus:v2.40.0'
    }

    def "getEffectiveSourceRef returns empty string when nothing configured"() {
        expect:
        spec.getEffectiveSourceRef() == ''
    }

    // ===== SourceRefSpec TESTS =====

    def "SourceRefSpec computeReference with all components"() {
        given:
        def refSpec = new ProjectImageSpec.SourceRefSpec()
        refSpec.registry = 'docker.io'
        refSpec.namespace = 'library'
        refSpec.name = 'nginx'
        refSpec.tag = 'latest'

        expect:
        refSpec.computeReference() == 'docker.io/library/nginx:latest'
    }

    def "SourceRefSpec computeReference without registry"() {
        given:
        def refSpec = new ProjectImageSpec.SourceRefSpec()
        refSpec.namespace = 'library'
        refSpec.name = 'nginx'
        refSpec.tag = 'latest'

        expect:
        refSpec.computeReference() == 'library/nginx:latest'
    }

    def "SourceRefSpec computeReference without tag"() {
        given:
        def refSpec = new ProjectImageSpec.SourceRefSpec()
        refSpec.registry = 'docker.io'
        refSpec.namespace = 'library'
        refSpec.name = 'nginx'

        expect:
        refSpec.computeReference() == 'docker.io/library/nginx'
    }

    def "SourceRefSpec computeReference with repository"() {
        given:
        def refSpec = new ProjectImageSpec.SourceRefSpec()
        refSpec.registry = 'quay.io'
        refSpec.repository = 'prometheus/prometheus'
        refSpec.tag = 'v2.40.0'

        expect:
        refSpec.computeReference() == 'quay.io/prometheus/prometheus:v2.40.0'
    }

    def "SourceRefSpec computeReference with minimal config"() {
        given:
        def refSpec = new ProjectImageSpec.SourceRefSpec()
        refSpec.name = 'nginx'

        expect:
        refSpec.computeReference() == 'nginx'
    }

    def "SourceRefSpec computeReference with empty config"() {
        given:
        def refSpec = new ProjectImageSpec.SourceRefSpec()

        expect:
        refSpec.computeReference() == ''
    }

    def "SourceRefSpec computeReference with only tag does not append colon"() {
        given:
        def refSpec = new ProjectImageSpec.SourceRefSpec()
        refSpec.tag = 'latest'  // Only tag set, no name/namespace/registry/repository

        expect:
        // Tag should not be appended if ref is empty
        refSpec.computeReference() == ''
    }

    def "SourceRefSpec computeReference without namespace"() {
        given:
        def refSpec = new ProjectImageSpec.SourceRefSpec()
        refSpec.registry = 'docker.io'
        refSpec.name = 'nginx'
        refSpec.tag = 'latest'

        expect:
        refSpec.computeReference() == 'docker.io/nginx:latest'
    }

    // ===== DEPRECATED LEGACY NAME PROPERTY TEST =====

    def "deprecated legacyName property is optional"() {
        expect:
        !spec.legacyName.isPresent()
    }

    def "deprecated legacyName property can still be set"() {
        when:
        spec.legacyName.set('old-name')

        then:
        spec.legacyName.get() == 'old-name'
    }
}
