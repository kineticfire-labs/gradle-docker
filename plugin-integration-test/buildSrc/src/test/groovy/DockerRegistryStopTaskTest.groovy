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
 * Unit tests for DockerRegistryStopTask.
 */
class DockerRegistryStopTaskTest extends Specification {

    Project project
    DockerRegistryStopTask task
    RegistryTestFixture mockFixture

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testStopRegistries', DockerRegistryStopTask).get()
        mockFixture = Mock(RegistryTestFixture)
    }

    def "task has correct properties"() {
        expect:
        task.group == 'docker registry'
        task.description == 'Stop Docker registries after integration testing'
    }

    def "task accepts registry fixture"() {
        when:
        task.registryFixture.set(mockFixture)
        
        then:
        task.registryFixture.get() == mockFixture
    }

    def "task stops registries successfully"() {
        given:
        task.registryFixture.set(mockFixture)
        
        when:
        task.stopRegistries()
        
        then:
        1 * mockFixture.stopAllRegistries()
        noExceptionThrown()
    }

    def "task removes testRegistries extension"() {
        given:
        task.registryFixture.set(mockFixture)
        project.extensions.create('testRegistries', Map, ['test': 'data'])
        
        when:
        task.stopRegistries()
        
        then:
        1 * mockFixture.stopAllRegistries()
        project.extensions.findByName('testRegistries') == null
    }

    def "task handles missing testRegistries extension"() {
        given:
        task.registryFixture.set(mockFixture)
        
        when:
        task.stopRegistries()
        
        then:
        1 * mockFixture.stopAllRegistries()
        noExceptionThrown()
    }

    def "task handles stop failure gracefully"() {
        given:
        task.registryFixture.set(mockFixture)
        mockFixture.stopAllRegistries() >> { throw new RuntimeException('Stop failed') }
        
        when:
        task.stopRegistries()
        
        then:
        1 * mockFixture.stopAllRegistries() >> { throw new RuntimeException('Stop failed') }
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