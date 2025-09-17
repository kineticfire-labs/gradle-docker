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
 * Unit tests for RegistryManagementExtension.
 * 
 * Tests DSL configuration methods, registry configuration options,
 * and parameter validation.
 */
class RegistryManagementExtensionTest extends Specification {

    Project project
    RegistryManagementExtension extension

    def setup() {
        project = ProjectBuilder.builder().build()
        extension = project.objects.newInstance(RegistryManagementExtension, project)
    }

    def "extension initializes with empty registry configs"() {
        expect:
        extension.registryConfigs.get().isEmpty()
    }

    def "registry method adds simple registry"() {
        when:
        extension.registry('test-registry', 5000)
        
        then:
        extension.registryConfigs.get().size() == 1
        def config = extension.registryConfigs.get()[0]
        config.name == 'test-registry'
        config.port == 5000
        !config.requiresAuth
    }

    def "authenticatedRegistry method adds registry with auth"() {
        when:
        extension.authenticatedRegistry('secure-registry', 5001, 'user', 'pass')
        
        then:
        extension.registryConfigs.get().size() == 1
        def config = extension.registryConfigs.get()[0]
        config.name == 'secure-registry'
        config.port == 5001
        config.requiresAuth
        config.username == 'user'
        config.password == 'pass'
    }

    def "registry method with closure supports custom configuration"() {
        when:
        extension.registry('custom-registry', 5002) { RegistryTestFixture.RegistryConfig config ->
            config.withAuth('admin', 'secret')
            config.withLabels(['environment': 'test'])
        }
        
        then:
        extension.registryConfigs.get().size() == 1
        def config = extension.registryConfigs.get()[0]
        config.name == 'custom-registry'
        config.port == 5002
        config.requiresAuth
        config.username == 'admin'
        config.password == 'secret'
        config.extraLabels == ['environment': 'test']
    }

    def "add method accepts pre-configured registry"() {
        given:
        def preConfig = new RegistryTestFixture.RegistryConfig('pre-configured', 5003)
            .withAuth('test', 'test')
        
        when:
        extension.add(preConfig)
        
        then:
        extension.registryConfigs.get().size() == 1
        extension.registryConfigs.get()[0] == preConfig
    }

    def "multiple registries can be added"() {
        when:
        extension.registry('registry1', 5000)
        extension.authenticatedRegistry('registry2', 5001, 'user', 'pass')
        extension.registry('registry3', 5002) { config ->
            config.withLabels(['type': 'custom'])
        }
        
        then:
        extension.registryConfigs.get().size() == 3
        extension.registryConfigs.get()[0].name == 'registry1'
        extension.registryConfigs.get()[1].name == 'registry2'
        extension.registryConfigs.get()[2].name == 'registry3'
    }

    def "registry configurations are independent"() {
        when:
        extension.registry('registry1', 5000)
        extension.authenticatedRegistry('registry2', 5001, 'user', 'pass')
        
        then:
        def config1 = extension.registryConfigs.get()[0]
        def config2 = extension.registryConfigs.get()[1]
        
        !config1.requiresAuth
        config2.requiresAuth
        config1.username == null
        config2.username == 'user'
    }

    def "closure configuration modifies registry correctly"() {
        when:
        extension.registry('test', 5000) { config ->
            config.withAuth('user', 'pass')
            config.withLabels([
                'environment': 'test',
                'component': 'integration'
            ])
        }
        
        then:
        def config = extension.registryConfigs.get()[0]
        config.requiresAuth
        config.username == 'user'
        config.password == 'pass'
        config.extraLabels.size() == 2
        config.extraLabels['environment'] == 'test'
        config.extraLabels['component'] == 'integration'
    }

    def "DSL methods validate parameters"() {
        when:
        extension.registry(name, port)
        
        then:
        thrown(expectedException)
        
        where:
        name    | port | expectedException
        null    | 5000 | NullPointerException
        ""      | 5000 | IllegalArgumentException
        "valid" | -1   | IllegalArgumentException
        "valid" | 0    | IllegalArgumentException
    }

    def "authenticatedRegistry validates parameters"() {
        when:
        extension.authenticatedRegistry(name, port, username, password)
        
        then:
        thrown(expectedException)
        
        where:
        name    | port | username | password | expectedException
        null    | 5000 | "user"   | "pass"   | NullPointerException
        "test"  | -1   | "user"   | "pass"   | IllegalArgumentException
        "test"  | 5000 | null     | "pass"   | NullPointerException
        "test"  | 5000 | "user"   | null     | NullPointerException
        "test"  | 5000 | ""       | "pass"   | IllegalArgumentException
        "test"  | 5000 | "user"   | ""       | IllegalArgumentException
    }

    def "add method validates parameter"() {
        when:
        extension.add(config)
        
        then:
        thrown(NullPointerException)
        
        where:
        config << [null]
    }

    def "closure delegation works correctly"() {
        given:
        def closureExecuted = false
        def configInClosure = null
        
        when:
        extension.registry('test', 5000) {
            closureExecuted = true
            configInClosure = it
            withAuth('user', 'pass')
        }
        
        then:
        closureExecuted
        configInClosure instanceof RegistryTestFixture.RegistryConfig
        configInClosure.name == 'test'
        configInClosure.requiresAuth
    }

    def "extension supports fluent configuration"() {
        when:
        extension.with {
            registry('simple', 5000)
            authenticatedRegistry('secure', 5001, 'user', 'pass')
            registry('custom', 5002) {
                withLabels(['tier': 'testing'])
            }
        }
        
        then:
        extension.registryConfigs.get().size() == 3
        extension.registryConfigs.get()[0].name == 'simple'
        extension.registryConfigs.get()[1].name == 'secure'
        extension.registryConfigs.get()[2].name == 'custom'
        extension.registryConfigs.get()[2].extraLabels == ['tier': 'testing']
    }
}