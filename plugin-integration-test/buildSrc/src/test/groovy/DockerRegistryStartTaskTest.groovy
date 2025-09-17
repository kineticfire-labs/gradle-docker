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
 * Unit tests for DockerRegistryStartTask.
 * 
 * Tests task configuration, input validation, and behavior
 * without actually starting Docker containers.
 */
class DockerRegistryStartTaskTest extends Specification {

    Project project
    DockerRegistryStartTask task
    RegistryTestFixture mockFixture

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testStartRegistries', DockerRegistryStartTask).get()
        mockFixture = Mock(RegistryTestFixture)
    }

    def "task has correct properties"() {
        expect:
        task.group == 'docker registry'
        task.description == 'Start Docker registries for integration testing'
    }

    def "task accepts registry fixture"() {
        when:
        task.registryFixture.set(mockFixture)
        
        then:
        task.registryFixture.get() == mockFixture
    }

    def "task accepts registry configs"() {
        given:
        def configs = [
            new RegistryTestFixture.RegistryConfig('test1', 5000),
            new RegistryTestFixture.RegistryConfig('test2', 5001)
        ]
        
        when:
        task.registryConfigs.set(configs)
        
        then:
        task.registryConfigs.get() == configs
    }

    def "task inputs are serializable for configuration cache"() {
        given:
        def configs = [new RegistryTestFixture.RegistryConfig('test', 5000)]
        
        when:
        task.registryFixture.set(mockFixture)
        task.registryConfigs.set(configs)
        
        then:
        task.registryFixture.get() instanceof RegistryTestFixture
        task.registryConfigs.get() instanceof List
        task.registryConfigs.get().every { it instanceof RegistryTestFixture.RegistryConfig }
    }

    def "task handles empty config list"() {
        given:
        task.registryFixture.set(mockFixture)
        task.registryConfigs.set([])
        mockFixture.startTestRegistries([]) >> [:]
        
        when:
        task.startRegistries()
        
        then:
        1 * mockFixture.startTestRegistries([])
        noExceptionThrown()
    }

    def "task starts registries and creates extension"() {
        given:
        def configs = [new RegistryTestFixture.RegistryConfig('test', 5000)]
        def registryInfo = new RegistryTestFixture.RegistryInfo(
            name: 'test',
            port: 5000,
            containerId: 'abc123',
            requiresAuth: false,
            username: null,
            password: null
        )
        def expectedRegistries = ['test': registryInfo]
        
        task.registryFixture.set(mockFixture)
        task.registryConfigs.set(configs)
        mockFixture.startTestRegistries(configs) >> expectedRegistries
        
        when:
        task.startRegistries()
        
        then:
        1 * mockFixture.startTestRegistries(configs) >> expectedRegistries
        project.extensions.findByName('testRegistries') != null
        project.extensions.testRegistries == expectedRegistries
    }

    def "task handles startup failure"() {
        given:
        def configs = [new RegistryTestFixture.RegistryConfig('test', 5000)]
        task.registryFixture.set(mockFixture)
        task.registryConfigs.set(configs)
        mockFixture.startTestRegistries(configs) >> { throw new RuntimeException('Docker not available') }
        
        when:
        task.startRegistries()
        
        then:
        1 * mockFixture.startTestRegistries(configs) >> { throw new RuntimeException('Docker not available') }
        thrown(RuntimeException)
    }

    def "task configuration is lazy"() {
        given:
        def fixtureProvider = project.provider { mockFixture }
        def configsProvider = project.provider { [new RegistryTestFixture.RegistryConfig('lazy', 5000)] }
        
        when:
        task.registryFixture.set(fixtureProvider)
        task.registryConfigs.set(configsProvider)
        
        then:
        task.registryFixture.get() == mockFixture
        task.registryConfigs.get().size() == 1
        task.registryConfigs.get()[0].name == 'lazy'
    }

    def "task handles multiple registry configs"() {
        given:
        def configs = [
            new RegistryTestFixture.RegistryConfig('registry1', 5000),
            new RegistryTestFixture.RegistryConfig('registry2', 5001).withAuth('user', 'pass'),
            new RegistryTestFixture.RegistryConfig('registry3', 5002).withLabels(['env': 'test'])
        ]
        
        def registries = [
            'registry1': new RegistryTestFixture.RegistryInfo(name: 'registry1', port: 5000, containerId: 'id1', requiresAuth: false, username: null, password: null),
            'registry2': new RegistryTestFixture.RegistryInfo(name: 'registry2', port: 5001, containerId: 'id2', requiresAuth: true, username: 'user', password: 'pass'),
            'registry3': new RegistryTestFixture.RegistryInfo(name: 'registry3', port: 5002, containerId: 'id3', requiresAuth: false, username: null, password: null)
        ]
        
        task.registryFixture.set(mockFixture)
        task.registryConfigs.set(configs)
        mockFixture.startTestRegistries(configs) >> registries
        
        when:
        task.startRegistries()
        
        then:
        1 * mockFixture.startTestRegistries(configs) >> registries
        project.extensions.testRegistries.size() == 3
        project.extensions.testRegistries['registry1'].port == 5000
        project.extensions.testRegistries['registry2'].requiresAuth
        project.extensions.testRegistries['registry3'].containerId == 'id3'
    }

    def "task overwrites existing testRegistries extension"() {
        given:
        def configs = [new RegistryTestFixture.RegistryConfig('new', 5000)]
        def newRegistries = ['new': new RegistryTestFixture.RegistryInfo(name: 'new', port: 5000, containerId: 'new123', requiresAuth: false, username: null, password: null)]
        
        // Create existing extension
        project.extensions.create('testRegistries', Map, ['old': 'data'])
        
        task.registryFixture.set(mockFixture)
        task.registryConfigs.set(configs)
        mockFixture.startTestRegistries(configs) >> newRegistries
        
        when:
        task.startRegistries()
        
        then:
        project.extensions.testRegistries == newRegistries
        !project.extensions.testRegistries.containsKey('old')
    }
}