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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for DockerRegistryCleanupTask.
 */
class DockerRegistryCleanupTaskTest extends Specification {

    Project project
    DockerRegistryCleanupTask task
    RegistryTestFixture mockFixture

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testCleanupRegistries', DockerRegistryCleanupTask).get()
        mockFixture = Mock(RegistryTestFixture)
    }

    def "task has correct properties"() {
        expect:
        task.group == 'docker registry'
        task.description == 'Emergency cleanup of orphaned Docker registries'
    }

    def "task accepts registry fixture"() {
        when:
        task.registryFixture.set(mockFixture)
        
        then:
        task.registryFixture.get() == mockFixture
    }

    def "task performs emergency cleanup successfully"() {
        given:
        task.registryFixture.set(mockFixture)
        
        when:
        task.emergencyCleanup()
        
        then:
        1 * mockFixture.emergencyCleanup()
        noExceptionThrown()
    }

    def "task handles cleanup failure gracefully"() {
        given:
        task.registryFixture.set(mockFixture)
        mockFixture.emergencyCleanup() >> { throw new RuntimeException('Cleanup failed') }
        
        when:
        task.emergencyCleanup()
        
        then:
        1 * mockFixture.emergencyCleanup() >> { throw new RuntimeException('Cleanup failed') }
        noExceptionThrown() // Task should not rethrow exceptions
    }

    def "task configuration is lazy"() {
        given:
        def fixtureProvider = project.provider { mockFixture }
        
        when:
        task.registryFixture.set(fixtureProvider)
        
        then:
        task.registryFixture.get() == mockFixture
    }
}