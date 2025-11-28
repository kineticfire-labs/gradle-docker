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

import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for FailureStepSpec
 */
class FailureStepSpecTest extends Specification {

    def project
    def failureStepSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        failureStepSpec = project.objects.newInstance(FailureStepSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        failureStepSpec != null
        failureStepSpec.additionalTags.present
        failureStepSpec.additionalTags.get() == []
        failureStepSpec.includeServices.present
        failureStepSpec.includeServices.get() == []
    }

    // ===== PROPERTY TESTS =====

    def "additionalTags property works correctly"() {
        given:
        def tags = ['failed', 'debug']

        when:
        failureStepSpec.additionalTags.set(tags)

        then:
        failureStepSpec.additionalTags.present
        failureStepSpec.additionalTags.get() == tags
    }

    def "additionalTags has default empty list"() {
        expect:
        failureStepSpec.additionalTags.present
        failureStepSpec.additionalTags.get() == []
    }

    def "additionalTags can be added incrementally"() {
        when:
        failureStepSpec.additionalTags.add('failed')
        failureStepSpec.additionalTags.add('needs-investigation')

        then:
        failureStepSpec.additionalTags.get() == ['failed', 'needs-investigation']
    }

    def "saveFailureLogsDir property works correctly"() {
        given:
        def logsDir = project.file('build/failure-logs')

        when:
        failureStepSpec.saveFailureLogsDir.set(logsDir)

        then:
        failureStepSpec.saveFailureLogsDir.present
        failureStepSpec.saveFailureLogsDir.get().asFile == logsDir
    }

    def "includeServices property works correctly"() {
        given:
        def services = ['app', 'database', 'cache']

        when:
        failureStepSpec.includeServices.set(services)

        then:
        failureStepSpec.includeServices.present
        failureStepSpec.includeServices.get() == services
    }

    def "includeServices has default empty list"() {
        expect:
        failureStepSpec.includeServices.present
        failureStepSpec.includeServices.get() == []
    }

    def "includeServices can be added incrementally"() {
        when:
        failureStepSpec.includeServices.add('app')
        failureStepSpec.includeServices.add('db')

        then:
        failureStepSpec.includeServices.get() == ['app', 'db']
    }

    def "afterFailure hook property works correctly"() {
        given:
        def hookCalled = false
        def hook = { hookCalled = true } as Action<Void>

        when:
        failureStepSpec.afterFailure.set(hook)

        then:
        failureStepSpec.afterFailure.present
        failureStepSpec.afterFailure.get() != null

        when:
        failureStepSpec.afterFailure.get().execute(null)

        then:
        hookCalled
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def tags = ['failed', 'test-env', 'debug']
        def logsDir = project.file('logs/failures')
        def services = ['web', 'api', 'worker']
        def hookCalled = false
        def hook = { hookCalled = true } as Action<Void>

        when:
        failureStepSpec.additionalTags.set(tags)
        failureStepSpec.saveFailureLogsDir.set(logsDir)
        failureStepSpec.includeServices.set(services)
        failureStepSpec.afterFailure.set(hook)

        then:
        failureStepSpec.additionalTags.get() == tags
        failureStepSpec.saveFailureLogsDir.get().asFile == logsDir
        failureStepSpec.includeServices.get() == services
        failureStepSpec.afterFailure.present

        when:
        failureStepSpec.afterFailure.get().execute(null)

        then:
        hookCalled
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        given:
        def initialTags = ['failed-v1']
        def updatedTags = ['failed-v2', 'retry']
        def initialDir = project.file('logs1')
        def updatedDir = project.file('logs2')

        when:
        failureStepSpec.additionalTags.set(initialTags)
        failureStepSpec.saveFailureLogsDir.set(initialDir)

        then:
        failureStepSpec.additionalTags.get() == initialTags
        failureStepSpec.saveFailureLogsDir.get().asFile == initialDir

        when:
        failureStepSpec.additionalTags.set(updatedTags)
        failureStepSpec.saveFailureLogsDir.set(updatedDir)

        then:
        failureStepSpec.additionalTags.get() == updatedTags
        failureStepSpec.saveFailureLogsDir.get().asFile == updatedDir
    }

    def "lists can be cleared and reset"() {
        given:
        failureStepSpec.additionalTags.add('tag1')
        failureStepSpec.includeServices.add('service1')

        when:
        failureStepSpec.additionalTags.set([])
        failureStepSpec.includeServices.set([])

        then:
        failureStepSpec.additionalTags.get() == []
        failureStepSpec.includeServices.get() == []

        when:
        failureStepSpec.additionalTags.add('new_tag')
        failureStepSpec.includeServices.add('new_service')

        then:
        failureStepSpec.additionalTags.get() == ['new_tag']
        failureStepSpec.includeServices.get() == ['new_service']
    }

    // ===== EDGE CASES =====

    def "multiple failure tags with various naming conventions work correctly"() {
        given:
        def tags = ['failed', 'test-failed', 'integration_failure', 'ERROR', 'needs-debug']

        when:
        failureStepSpec.additionalTags.set(tags)

        then:
        failureStepSpec.additionalTags.get() == tags
        failureStepSpec.additionalTags.get().size() == 5
    }

    def "service names with various formats work correctly"() {
        given:
        def services = ['app-service', 'db_service', 'cache.service', 'queue']

        when:
        failureStepSpec.includeServices.set(services)

        then:
        failureStepSpec.includeServices.get() == services
        failureStepSpec.includeServices.get().size() == 4
    }

    def "hook can be replaced after initial setting"() {
        given:
        def firstHookCalled = false
        def secondHookCalled = false
        def firstHook = { firstHookCalled = true } as Action<Void>
        def secondHook = { secondHookCalled = true } as Action<Void>

        when:
        failureStepSpec.afterFailure.set(firstHook)
        failureStepSpec.afterFailure.get().execute(null)

        then:
        firstHookCalled
        !secondHookCalled

        when:
        failureStepSpec.afterFailure.set(secondHook)
        failureStepSpec.afterFailure.get().execute(null)

        then:
        secondHookCalled
    }

    def "saveFailureLogsDir accepts various directory paths"() {
        given:
        def dirs = [
            project.file('build/logs'),
            project.file('failures'),
            project.file('test-results/failed')
        ]

        expect:
        dirs.each { dir ->
            failureStepSpec.saveFailureLogsDir.set(dir)
            assert failureStepSpec.saveFailureLogsDir.get().asFile == dir
        }
    }

    def "empty lists are valid configurations"() {
        when:
        failureStepSpec.additionalTags.set([])
        failureStepSpec.includeServices.set([])

        then:
        failureStepSpec.additionalTags.present
        failureStepSpec.additionalTags.get() == []
        failureStepSpec.includeServices.present
        failureStepSpec.includeServices.get() == []
    }

    def "single item lists work correctly"() {
        when:
        failureStepSpec.additionalTags.set(['failed'])
        failureStepSpec.includeServices.set(['app'])

        then:
        failureStepSpec.additionalTags.get() == ['failed']
        failureStepSpec.includeServices.get() == ['app']
    }
}
