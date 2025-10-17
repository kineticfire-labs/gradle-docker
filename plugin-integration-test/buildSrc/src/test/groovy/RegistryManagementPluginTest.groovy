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
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for RegistryManagementPlugin.
 * 
 * Tests plugin application, extension creation, task registration,
 * and convenience method setup.
 */
class RegistryManagementPluginTest extends Specification {

    Project project
    RegistryManagementPlugin plugin

    def setup() {
        project = ProjectBuilder.builder().build()
        plugin = new RegistryManagementPlugin()
    }

    def "plugin applies successfully"() {
        when:
        plugin.apply(project)
        
        then:
        noExceptionThrown()
    }

    def "plugin creates registryManagement extension"() {
        when:
        plugin.apply(project)
        
        then:
        project.extensions.findByName('registryManagement') != null
        project.extensions.registryManagement instanceof RegistryManagementExtension
    }

    def "plugin registers required tasks"() {
        when:
        plugin.apply(project)
        
        then:
        project.tasks.findByName('startTestRegistries') != null
        project.tasks.findByName('stopTestRegistries') != null
        project.tasks.findByName('cleanupTestRegistries') != null
    }

    def "plugin registers tasks with correct types"() {
        when:
        plugin.apply(project)
        
        then:
        project.tasks.getByName('startTestRegistries') instanceof DockerRegistryStartTask
        project.tasks.getByName('stopTestRegistries') instanceof DockerRegistryStopTask
        project.tasks.getByName('cleanupTestRegistries') instanceof DockerRegistryCleanupTask
    }

    def "plugin sets correct task groups"() {
        when:
        plugin.apply(project)
        
        then:
        project.tasks.getByName('startTestRegistries').group == 'docker registry'
        project.tasks.getByName('stopTestRegistries').group == 'docker registry'
        project.tasks.getByName('cleanupTestRegistries').group == 'docker registry'
    }

    def "plugin sets task descriptions"() {
        when:
        plugin.apply(project)
        
        then:
        project.tasks.getByName('startTestRegistries').description == 'Start Docker registries for integration testing'
        project.tasks.getByName('stopTestRegistries').description == 'Stop Docker registries after integration testing'
        project.tasks.getByName('cleanupTestRegistries').description == 'Emergency cleanup of orphaned Docker registries'
    }

    def "plugin adds convenience methods to project"() {
        when:
        plugin.apply(project)
        
        then:
        project.ext.has('createRegistryConfig')
        project.ext.has('addRegistryConfig')
        project.ext.has('withTestRegistry')
        project.ext.has('withAuthenticatedRegistry')
    }

    def "createRegistryConfig convenience method works"() {
        given:
        plugin.apply(project)
        
        when:
        def config = project.ext.createRegistryConfig('test', 5000)
        
        then:
        config instanceof RegistryTestFixture.RegistryConfig
        config.name == 'test'
        config.port == 5000
        !config.requiresAuth
    }

    def "addRegistryConfig convenience method works"() {
        given:
        plugin.apply(project)
        def config = new RegistryTestFixture.RegistryConfig('test', 5000)
        
        when:
        project.ext.addRegistryConfig(config)
        
        then:
        project.extensions.registryManagement.registryConfigs.get().contains(config)
    }

    def "withTestRegistry convenience method works"() {
        given:
        plugin.apply(project)
        def configureCalled = false
        
        when:
        project.ext.withTestRegistry('test', 5000) { RegistryTestFixture.RegistryConfig config ->
            configureCalled = true
            assert config.name == 'test'
            assert config.port == 5000
        }
        
        then:
        configureCalled
        project.extensions.registryManagement.registryConfigs.get().size() == 1
        project.extensions.registryManagement.registryConfigs.get()[0].name == 'test'
        project.extensions.registryManagement.registryConfigs.get()[0].port == 5000
    }

    def "withAuthenticatedRegistry convenience method works"() {
        given:
        plugin.apply(project)
        
        when:
        project.ext.withAuthenticatedRegistry('secure', 5001, 'user', 'pass')
        
        then:
        project.extensions.registryManagement.registryConfigs.get().size() == 1
        def config = project.extensions.registryManagement.registryConfigs.get()[0]
        config.name == 'secure'
        config.port == 5001
        config.requiresAuth
        config.username == 'user'
        config.password == 'pass'
    }

    def "withAuthenticatedRegistry convenience method supports closure"() {
        given:
        plugin.apply(project)
        def configureCalled = false
        
        when:
        project.ext.withAuthenticatedRegistry('secure', 5001, 'user', 'pass') { RegistryTestFixture.RegistryConfig config ->
            configureCalled = true
            config.withLabels(['custom': 'value'])
        }
        
        then:
        configureCalled
        def config = project.extensions.registryManagement.registryConfigs.get()[0]
        config.extraLabels == ['custom': 'value']
    }

    def "plugin configures task dependencies with lazy wiring"() {
        given:
        plugin.apply(project)
        
        // Create a Test task to verify dependency setup
        def testTask = project.tasks.register('integrationTest', Test) {
            it.group = 'verification'
        }
        
        when:
        // Force task realization to trigger configureEach
        project.tasks.getByName('integrationTest')
        
        then:
        def stopTask = project.tasks.getByName('stopTestRegistries')
        
        // Verify stop task runs after the test task using lazy wiring
        stopTask.mustRunAfter.getDependencies(stopTask).contains(testTask.get())
    }

    def "plugin handles projects without test tasks"() {
        when:
        plugin.apply(project)
        // No need to call project.evaluate() anymore - lazy wiring happens automatically
        
        then:
        noExceptionThrown()
    }

    def "plugin configures dependencies for test tasks with lazy wiring"() {
        given:
        plugin.apply(project)
        
        // Create Test tasks with names that match the plugin's filter
        def testTask = project.tasks.register('test', Test) { it.group = 'verification' }
        def integrationTestTask = project.tasks.register('integrationTest', Test) { it.group = 'verification' }
        // Create a task that should NOT be wired (wrong name)
        def customTestTask = project.tasks.register('customTest', Test) { it.group = 'verification' }
        
        when:
        // Force task realization to trigger configureEach
        project.tasks.getByName('test')
        project.tasks.getByName('integrationTest')
        project.tasks.getByName('customTest')
        
        then:
        def stopTask = project.tasks.getByName('stopTestRegistries')
        
        // Verify stop task runs after standard test tasks
        stopTask.mustRunAfter.getDependencies(stopTask).contains(testTask.get())
        stopTask.mustRunAfter.getDependencies(stopTask).contains(integrationTestTask.get())
        
        // Verify custom test task is NOT wired (name doesn't match filter)
        !stopTask.mustRunAfter.getDependencies(stopTask).contains(customTestTask.get())
    }

    def "plugin can be applied multiple times safely"() {
        when:
        plugin.apply(project)
        plugin.apply(project)
        
        then:
        noExceptionThrown()
        project.extensions.findByName('registryManagement') != null
        project.tasks.findByName('startTestRegistries') != null
    }
}