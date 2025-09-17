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

import spock.lang.Specification

/**
 * Unit tests for RegistryTestFixture.
 * 
 * Tests registry configuration, tracking, and cleanup mechanisms without
 * actually starting Docker containers.
 */
class RegistryTestFixtureTest extends Specification {

    RegistryTestFixture fixture

    def setup() {
        fixture = new RegistryTestFixture()
    }

    def "RegistryConfig creates basic configuration"() {
        when:
        def config = new RegistryTestFixture.RegistryConfig('test-registry', 5000)
        
        then:
        config.name == 'test-registry'
        config.port == 5000
        !config.requiresAuth
        config.username == null
        config.password == null
        config.extraLabels.isEmpty()
    }

    def "RegistryConfig supports authentication"() {
        when:
        def config = new RegistryTestFixture.RegistryConfig('auth-registry', 5001)
            .withAuth('testuser', 'testpass')
        
        then:
        config.name == 'auth-registry'
        config.port == 5001
        config.requiresAuth
        config.username == 'testuser'
        config.password == 'testpass'
        config.extraLabels.isEmpty()
    }

    def "RegistryConfig supports custom labels"() {
        when:
        def labels = ['environment': 'test', 'component': 'integration']
        def config = new RegistryTestFixture.RegistryConfig('labeled-registry', 5002)
            .withLabels(labels)
        
        then:
        config.name == 'labeled-registry'
        config.port == 5002
        !config.requiresAuth
        config.extraLabels == labels
    }

    def "RegistryConfig supports chained configuration"() {
        when:
        def config = new RegistryTestFixture.RegistryConfig('full-registry', 5003)
            .withAuth('admin', 'secret')
            .withLabels(['tier': 'testing'])
        
        then:
        config.name == 'full-registry'
        config.port == 5003
        config.requiresAuth
        config.username == 'admin'
        config.password == 'secret'
        config.extraLabels == ['tier': 'testing']
    }

    def "RegistryInfo provides correct URL"() {
        when:
        def info = new RegistryTestFixture.RegistryInfo(
            name: 'test',
            port: 5000,
            containerId: 'abc123',
            requiresAuth: false,
            username: null,
            password: null
        )
        
        then:
        info.url == 'localhost:5000'
        info.getFullImageName('my-app:latest') == 'localhost:5000/my-app:latest'
    }

    def "RegistryInfo handles authenticated registries"() {
        when:
        def info = new RegistryTestFixture.RegistryInfo(
            name: 'secure',
            port: 5001,
            containerId: 'def456',
            requiresAuth: true,
            username: 'user',
            password: 'pass'
        )
        
        then:
        info.name == 'secure'
        info.port == 5001
        info.containerId == 'def456'
        info.requiresAuth
        info.username == 'user'
        info.password == 'pass'
        info.url == 'localhost:5001'
    }

    def "fixture initializes with clean state"() {
        when:
        def fixture = new RegistryTestFixture()
        
        then:
        fixture.sessionId != null
        fixture.sessionId.length() > 0
        // Cannot directly test private fields, but behavior tests will verify clean state
    }

    def "multiple fixtures have unique session IDs"() {
        when:
        def fixture1 = new RegistryTestFixture()
        def fixture2 = new RegistryTestFixture()
        
        then:
        fixture1.sessionId != fixture2.sessionId
    }

    def "fixture accepts empty config list"() {
        when:
        def result = fixture.startTestRegistries([])
        
        then:
        result.isEmpty()
        noExceptionThrown()
    }

    def "fixture handles null config list gracefully"() {
        when:
        fixture.startTestRegistries(null)
        
        then:
        thrown(NullPointerException)
    }

    def "stopAllRegistries completes without active registries"() {
        when:
        fixture.stopAllRegistries()
        
        then:
        noExceptionThrown()
    }

    def "emergencyCleanup completes without active registries"() {
        when:
        fixture.emergencyCleanup()
        
        then:
        noExceptionThrown()
    }

    def "verifyRegistryHealth completes without active registries"() {
        when:
        fixture.verifyRegistryHealth()
        
        then:
        noExceptionThrown()
    }

    def "RegistryConfig constructor validates parameters"() {
        when:
        new RegistryTestFixture.RegistryConfig(name, port)
        
        then:
        thrown(expectedException)
        
        where:
        name    | port | expectedException
        null    | 5000 | NullPointerException
        ""      | 5000 | IllegalArgumentException
        "valid" | -1   | IllegalArgumentException
        "valid" | 0    | IllegalArgumentException
    }

    def "withAuth validates parameters"() {
        given:
        def config = new RegistryTestFixture.RegistryConfig('test', 5000)
        
        when:
        config.withAuth(username, password)
        
        then:
        thrown(expectedException)
        
        where:
        username | password | expectedException
        null     | "pass"   | NullPointerException
        "user"   | null     | NullPointerException
        ""       | "pass"   | IllegalArgumentException
        "user"   | ""       | IllegalArgumentException
    }

    def "withLabels validates parameters"() {
        given:
        def config = new RegistryTestFixture.RegistryConfig('test', 5000)
        
        when:
        config.withLabels(labels)
        
        then:
        thrown(expectedException)
        
        where:
        labels                      | expectedException
        null                        | NullPointerException
        ['': 'value']              | IllegalArgumentException
        ['key': '']                | IllegalArgumentException
        ['key': null]              | NullPointerException
    }
}