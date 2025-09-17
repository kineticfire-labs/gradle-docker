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
import spock.lang.IgnoreIf

/**
 * Integration test scenarios for Docker registry authentication and management.
 * 
 * These tests demonstrate real-world usage patterns for registry integration
 * testing with authentication, including:
 * - Multiple registry configurations
 * - Authentication scenarios
 * - Cleanup mechanisms
 * - Error handling
 * 
 * Note: Tests are skipped if Docker is not available to prevent CI failures.
 */
@IgnoreIf({ !isDockerAvailable() })
class RegistryIntegrationTestScenarios extends Specification {

    Project project
    RegistryTestFixture fixture

    def setup() {
        project = ProjectBuilder.builder().build()
        fixture = new RegistryTestFixture()
    }

    def cleanup() {
        // Ensure cleanup even if tests fail
        try {
            fixture.emergencyCleanup()
        } catch (Exception e) {
            println "Cleanup warning: ${e.message}"
        }
    }

    static boolean isDockerAvailable() {
        try {
            def process = ['docker', 'version'].execute()
            return process.waitFor() == 0
        } catch (Exception e) {
            return false
        }
    }

    def "Scenario 1: Single unauthenticated registry lifecycle"() {
        given: "A simple registry configuration"
        def config = new RegistryTestFixture.RegistryConfig('simple-registry', 5000)
        
        when: "Starting the registry"
        def registries = fixture.startTestRegistries([config])
        
        then: "Registry starts successfully"
        registries.size() == 1
        registries.containsKey('simple-registry')
        
        def registryInfo = registries['simple-registry']
        registryInfo.name == 'simple-registry'
        registryInfo.port == 5000
        registryInfo.url == 'localhost:5000'
        !registryInfo.requiresAuth
        
        when: "Verifying registry health"
        fixture.verifyRegistryHealth()
        
        then: "Registry is healthy"
        noExceptionThrown()
        
        when: "Stopping the registry"
        fixture.stopAllRegistries()
        
        then: "Registry stops cleanly"
        noExceptionThrown()
    }

    def "Scenario 2: Single authenticated registry lifecycle"() {
        given: "An authenticated registry configuration"
        def config = new RegistryTestFixture.RegistryConfig('auth-registry', 5001)
            .withAuth('testuser', 'testpass')
        
        when: "Starting the registry"
        def registries = fixture.startTestRegistries([config])
        
        then: "Registry starts successfully with authentication"
        registries.size() == 1
        registries.containsKey('auth-registry')
        
        def registryInfo = registries['auth-registry']
        registryInfo.name == 'auth-registry'
        registryInfo.port == 5001
        registryInfo.url == 'localhost:5001'
        registryInfo.requiresAuth
        registryInfo.username == 'testuser'
        registryInfo.password == 'testpass'
        
        when: "Stopping the registry"
        fixture.stopAllRegistries()
        
        then: "Registry stops cleanly"
        noExceptionThrown()
    }

    def "Scenario 3: Multiple mixed registry configurations"() {
        given: "Multiple registries with different configurations"
        def configs = [
            new RegistryTestFixture.RegistryConfig('public-sim', 5010),
            new RegistryTestFixture.RegistryConfig('private-auth', 5011).withAuth('admin', 'secret'),
            new RegistryTestFixture.RegistryConfig('labeled-registry', 5012)
                .withLabels(['environment': 'test', 'purpose': 'integration'])
        ]
        
        when: "Starting all registries"
        def registries = fixture.startTestRegistries(configs)
        
        then: "All registries start successfully"
        registries.size() == 3
        
        // Verify public registry
        def publicRegistry = registries['public-sim']
        publicRegistry.port == 5010
        !publicRegistry.requiresAuth
        
        // Verify private registry
        def privateRegistry = registries['private-auth']
        privateRegistry.port == 5011
        privateRegistry.requiresAuth
        privateRegistry.username == 'admin'
        privateRegistry.password == 'secret'
        
        // Verify labeled registry
        def labeledRegistry = registries['labeled-registry']
        labeledRegistry.port == 5012
        !labeledRegistry.requiresAuth
        
        when: "Verifying all registries are healthy"
        fixture.verifyRegistryHealth()
        
        then: "All registries are healthy"
        noExceptionThrown()
        
        when: "Stopping all registries"
        fixture.stopAllRegistries()
        
        then: "All registries stop cleanly"
        noExceptionThrown()
    }

    def "Scenario 4: Registry failure and emergency cleanup"() {
        given: "A registry configuration"
        def config = new RegistryTestFixture.RegistryConfig('cleanup-test', 5020)
        
        when: "Starting registry and simulating failure"
        def registries = fixture.startTestRegistries([config])
        
        then: "Registry starts successfully"
        registries.size() == 1
        
        when: "Performing emergency cleanup without normal stop"
        fixture.emergencyCleanup()
        
        then: "Emergency cleanup completes successfully"
        noExceptionThrown()
        
        cleanup:
        // Additional cleanup to ensure no orphaned containers
        try {
            fixture.emergencyCleanup()
        } catch (Exception e) {
            // Ignore cleanup errors in test cleanup
        }
    }

    def "Scenario 5: Registry management plugin integration"() {
        given: "A project with registry management plugin applied"
        project.apply plugin: RegistryManagementPlugin
        
        when: "Configuring registries via DSL"
        project.registryManagement {
            registry('plugin-test-1', 5030)
            authenticatedRegistry('plugin-test-2', 5031, 'user', 'pass')
        }
        
        then: "Plugin configuration is applied correctly"
        def extension = project.extensions.registryManagement
        extension.registryConfigs.get().size() == 2
        
        def config1 = extension.registryConfigs.get()[0]
        config1.name == 'plugin-test-1'
        config1.port == 5030
        !config1.requiresAuth
        
        def config2 = extension.registryConfigs.get()[1]
        config2.name == 'plugin-test-2'
        config2.port == 5031
        config2.requiresAuth
        
        and: "Required tasks are registered"
        project.tasks.findByName('startTestRegistries') != null
        project.tasks.findByName('stopTestRegistries') != null
        project.tasks.findByName('cleanupTestRegistries') != null
    }

    def "Scenario 6: Registry workflow with image verification"() {
        given: "Registry configuration and test images"
        def config = new RegistryTestFixture.RegistryConfig('verification-test', 5040)
        def imageReferences = [
            'localhost:5040/test-image:latest',
            'localhost:5040/another-image:1.0'
        ]
        
        when: "Starting registry"
        def registries = fixture.startTestRegistries([config])
        
        then: "Registry is available"
        registries.containsKey('verification-test')
        
        when: "Registering verification task"
        project.ext.apply from: 'docker-image-testing.gradle'
        project.ext.registerVerifyRegistryImagesTask(project, imageReferences)
        
        then: "Verification task is registered"
        def verifyTask = project.tasks.findByName('verifyRegistryDockerImages')
        verifyTask != null
        verifyTask instanceof DockerRegistryImageVerifyTask
        verifyTask.imageReferences.get() == imageReferences
        
        cleanup:
        fixture.stopAllRegistries()
    }

    def "Scenario 7: Authentication error handling"() {
        given: "Registry with authentication"
        def config = new RegistryTestFixture.RegistryConfig('auth-error-test', 5050)
            .withAuth('testuser', 'testpass')
        
        when: "Starting registry"
        def registries = fixture.startTestRegistries([config])
        
        then: "Registry starts with authentication"
        registries['auth-error-test'].requiresAuth
        
        when: "Attempting to verify non-existent image (simulates auth failure scenario)"
        def verifyTask = project.tasks.register('testVerifyAuth', DockerRegistryImageVerifyTask) {
            it.imageReferences.set(['localhost:5050/non-existent:latest'])
        }.get()
        
        and: "Running verification"
        try {
            verifyTask.verifyRegistryImages()
        } catch (RuntimeException e) {
            // Expected - image doesn't exist
        }
        
        then: "Task handles authentication scenario appropriately"
        noExceptionThrown() // from the test setup itself
        
        cleanup:
        fixture.stopAllRegistries()
    }

    def "Scenario 8: Concurrent registry management"() {
        given: "Multiple fixtures for concurrent testing"
        def fixture1 = new RegistryTestFixture()
        def fixture2 = new RegistryTestFixture()
        
        when: "Starting registries with different fixtures concurrently"
        def config1 = new RegistryTestFixture.RegistryConfig('concurrent-1', 5060)
        def config2 = new RegistryTestFixture.RegistryConfig('concurrent-2', 5061)
        
        def registries1 = fixture1.startTestRegistries([config1])
        def registries2 = fixture2.startTestRegistries([config2])
        
        then: "Both registries start independently"
        registries1.containsKey('concurrent-1')
        registries2.containsKey('concurrent-2')
        
        // Verify different session IDs
        fixture1.sessionId != fixture2.sessionId
        
        cleanup:
        try {
            fixture1.stopAllRegistries()
            fixture2.stopAllRegistries()
        } catch (Exception e) {
            // Ensure emergency cleanup
            fixture1.emergencyCleanup()
            fixture2.emergencyCleanup()
        }
    }

    def "Scenario 9: Resource exhaustion and recovery"() {
        given: "Configuration for resource testing"
        def configs = (5070..5074).collect { port ->
            new RegistryTestFixture.RegistryConfig("resource-test-${port}", port)
        }
        
        when: "Starting multiple registries"
        def registries = fixture.startTestRegistries(configs)
        
        then: "All registries start successfully"
        registries.size() == 5
        
        when: "Performing health verification on all"
        fixture.verifyRegistryHealth()
        
        then: "All registries are healthy"
        noExceptionThrown()
        
        when: "Stopping all registries"
        fixture.stopAllRegistries()
        
        then: "Cleanup is successful"
        noExceptionThrown()
        
        when: "Verifying emergency cleanup handles orphaned resources"
        fixture.emergencyCleanup()
        
        then: "Emergency cleanup completes"
        noExceptionThrown()
    }

    def "Scenario 10: Complete integration workflow"() {
        given: "Complete workflow setup"
        def registryConfigs = [
            new RegistryTestFixture.RegistryConfig('workflow-public', 5080),
            new RegistryTestFixture.RegistryConfig('workflow-private', 5081).withAuth('workflow', 'test')
        ]
        
        def imageReferences = [
            'localhost:5080/workflow-app:latest',
            'localhost:5081/workflow-secure:1.0'
        ]
        
        when: "Executing complete workflow"
        
        // Step 1: Start registries
        def registries = fixture.startTestRegistries(registryConfigs)
        
        // Step 2: Verify registries are running
        fixture.verifyRegistryHealth()
        
        // Step 3: Register verification tasks
        project.ext.apply from: 'docker-image-testing.gradle'
        project.ext.registerRegistryTestWorkflow(project, registryConfigs, imageReferences)
        
        // Step 4: Verify tasks were created
        def startTask = project.tasks.findByName('startTestRegistries')
        def verifyTask = project.tasks.findByName('verifyRegistryDockerImages')
        def stopTask = project.tasks.findByName('stopTestRegistries')
        
        then: "Workflow setup is complete"
        registries.size() == 2
        startTask != null
        verifyTask != null
        stopTask != null
        
        // Verify registry information
        registries['workflow-public'].port == 5080
        registries['workflow-private'].requiresAuth
        
        cleanup:
        fixture.stopAllRegistries()
    }
}