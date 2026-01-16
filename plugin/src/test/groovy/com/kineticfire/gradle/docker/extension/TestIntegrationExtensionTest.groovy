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

package com.kineticfire.gradle.docker.extension

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Unit tests for TestIntegrationExtension.
 *
 * Tests core functionality using ProjectBuilder for realistic project simulation.
 * Note: usesCompose() method is tested in integration tests as it requires full plugin setup.
 */
class TestIntegrationExtensionTest extends Specification {

    Project project

    @Subject
    TestIntegrationExtension extension

    def setup() {
        project = ProjectBuilder.builder().build()
        extension = project.objects.newInstance(TestIntegrationExtension, project.name)
    }

    def "constructor initializes with project"() {
        expect:
        extension != null
    }

    def "composeStateFileFor returns Provider with correct path structure"() {
        given:
        String stackName = "testStack"

        when:
        def stateFileProvider = extension.composeStateFileFor(stackName)
        def resolvedPath = stateFileProvider.get()

        then:
        stateFileProvider != null
        resolvedPath != null
        resolvedPath.contains("compose-state")
        resolvedPath.contains("${stackName}-state.json")
    }

    @Unroll
    def "composeStateFileFor works with stack name '#stackName'"() {
        when:
        def stateFileProvider = extension.composeStateFileFor(stackName)
        def resolvedPath = stateFileProvider.get()

        then:
        resolvedPath != null
        resolvedPath.contains("${stackName}-state.json")

        where:
        stackName << ["stack1", "stack-name", "complexStackName123", "test_stack", "UPPERCASE", "with123numbers"]
    }

    def "composeStateFileFor generates unique paths for different stacks"() {
        when:
        def path1 = extension.composeStateFileFor("stack1").get()
        def path2 = extension.composeStateFileFor("stack2").get()

        then:
        path1 != path2
        path1.contains("stack1-state.json")
        path2.contains("stack2-state.json")
    }

    def "composeStateFileFor path is under build directory"() {
        when:
        def stateFilePath = extension.composeStateFileFor("myStack").get()

        then:
        stateFilePath.contains(project.layout.buildDirectory.get().asFile.absolutePath)
    }

    def "composeStateFileFor path includes correct subdirectory"() {
        when:
        def stateFilePath = extension.composeStateFileFor("testStack").get()

        then:
        stateFilePath.contains("compose-state")
    }

    def "composeStateFileFor handles special characters in stack name"() {
        when:
        def stateFilePath = extension.composeStateFileFor("my-test_stack.123").get()

        then:
        stateFilePath.contains("my-test_stack.123-state.json")
    }

    def "multiple calls to composeStateFileFor return consistent paths"() {
        given:
        def stackName = "consistentStack"

        when:
        def path1 = extension.composeStateFileFor(stackName).get()
        def path2 = extension.composeStateFileFor(stackName).get()

        then:
        path1 == path2
    }

    // ===== USES COMPOSE TESTS =====

    def "usesCompose configures class lifecycle correctly"() {
        given:
        // Apply plugin to get full setup
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        // Get the extension created by the plugin
        def testIntegrationExt = project.extensions.getByType(TestIntegrationExtension)

        // Create compose stack
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        // Evaluate project to create compose tasks
        project.evaluate()

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', 'class')

        then:
        noExceptionThrown()
        // Task should have dependencies configured
        testTask.dependsOn.find { it.toString().contains('composeUpTestStack') }
        testTask.finalizedBy.getDependencies(testTask).find { it.name.contains('composeDownTestStack') }
    }

    def "usesCompose configures class lifecycle correctly"() {
        given:
        // Manually create extensions without applying the full plugin
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        // Create compose stack
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', 'class')

        then:
        noExceptionThrown()
        // Should configure system properties for JUnit extension
        testTask.systemProperties['docker.compose.stack'] == 'testStack'
        testTask.systemProperties['docker.compose.lifecycle'] == 'class'
        testTask.systemProperties['docker.compose.project'] == project.name
    }

    def "usesCompose configures method lifecycle correctly"() {
        given:
        // Manually create extensions without applying the full plugin
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        // Create compose stack
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', 'method')

        then:
        noExceptionThrown()
        // Should configure system properties for JUnit extension
        testTask.systemProperties['docker.compose.stack'] == 'testStack'
        testTask.systemProperties['docker.compose.lifecycle'] == 'method'
        testTask.systemProperties['docker.compose.project'] == project.name
    }

    def "usesCompose fails when dockerTest extension not found"() {
        given:
        // Do NOT apply the plugin - we want to test the error case
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        extension.usesCompose(testTask, 'testStack', 'class')

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("dockerTest extension not found")
    }

    def "usesCompose fails when compose stack not found"() {
        given:
        // Apply plugin to get dockerTest extension
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        // Get the extension created by the plugin
        def testIntegrationExt = project.extensions.getByType(TestIntegrationExtension)

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'nonExistentStack', 'class')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Compose stack 'nonExistentStack' not found")
    }

    def "usesCompose fails with invalid lifecycle"() {
        given:
        // Apply plugin to get full setup
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        // Get the extension created by the plugin
        def testIntegrationExt = project.extensions.getByType(TestIntegrationExtension)

        // Create compose stack
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', 'invalid')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid lifecycle")
        ex.message.contains("Must be 'class' or 'method'")
    }

    @Unroll
    def "usesCompose handles different lifecycle values case-insensitively: #lifecycle"() {
        given:
        // Apply plugin to get full setup
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        // Get the extension created by the plugin
        def testIntegrationExt = project.extensions.getByType(TestIntegrationExtension)

        // Create compose stack
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', lifecycle)

        then:
        noExceptionThrown()

        where:
        lifecycle << ['CLASS', 'Class', 'CLASS', 'Class', 'METHOD', 'Method']
    }

    // ===== COVERAGE ENHANCEMENT TESTS =====

    def "usesCompose fails with helpful message when stack not found but other stacks exist"() {
        given:
        // Apply plugin to get full setup
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        // Get the extension created by the plugin
        def testIntegrationExt = project.extensions.getByType(TestIntegrationExtension)

        // Create a compose stack (but not the one we'll request)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            existingStack {
                files.from(composeFile)
            }
            anotherStack {
                files.from(composeFile)
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'nonExistentStack', 'class')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Compose stack 'nonExistentStack' not found")
        ex.message.contains("Available stacks:")
        ex.message.contains("existingStack")
        ex.message.contains("anotherStack")
    }

    def "usesCompose with Lifecycle enum configures class lifecycle correctly"() {
        given:
        // Manually create extensions without applying the full plugin
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        // Create compose stack
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()
        testTask.systemProperties['docker.compose.stack'] == 'testStack'
        testTask.systemProperties['docker.compose.lifecycle'] == 'class'
    }

    def "usesCompose with Lifecycle enum configures method lifecycle correctly"() {
        given:
        // Manually create extensions without applying the full plugin
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        // Create compose stack
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'testStack', com.kineticfire.gradle.docker.Lifecycle.METHOD)

        then:
        noExceptionThrown()
        testTask.systemProperties['docker.compose.stack'] == 'testStack'
        testTask.systemProperties['docker.compose.lifecycle'] == 'method'
    }

    def "usesCompose configures waitForHealthy system properties"() {
        given:
        // Manually create extensions without applying the full plugin
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        // Create compose stack with waitForHealthy config
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            healthyStack {
                files.from(composeFile)
                waitForHealthy {
                    waitForServices.set(['db', 'redis'])
                    timeoutSeconds.set(120)
                    pollSeconds.set(5)
                }
            }
        }

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'healthyStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()
        testTask.systemProperties['docker.compose.waitForHealthy.services'] == 'db,redis'
        testTask.systemProperties['docker.compose.waitForHealthy.timeoutSeconds'] == '120'
        testTask.systemProperties['docker.compose.waitForHealthy.pollSeconds'] == '5'
    }

    def "usesCompose configures waitForRunning system properties"() {
        given:
        // Manually create extensions without applying the full plugin
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        // Create compose stack with waitForRunning config
        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            runningStack {
                files.from(composeFile)
                waitForRunning {
                    waitForServices.set(['app', 'worker'])
                    timeoutSeconds.set(90)
                    pollSeconds.set(3)
                }
            }
        }

        // Create test task
        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'runningStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()
        testTask.systemProperties['docker.compose.waitForRunning.services'] == 'app,worker'
        testTask.systemProperties['docker.compose.waitForRunning.timeoutSeconds'] == '90'
        testTask.systemProperties['docker.compose.waitForRunning.pollSeconds'] == '3'
    }

    def "usesCompose configures waitForHealthy with default values"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            defaultsStack {
                files.from(composeFile)
                waitForHealthy {
                    // Only set services, use defaults for timeoutSeconds and pollSeconds
                    waitForServices.set(['db'])
                }
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'defaultsStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()
        testTask.systemProperties['docker.compose.waitForHealthy.services'] == 'db'
        testTask.systemProperties['docker.compose.waitForHealthy.timeoutSeconds'] == '60'
        testTask.systemProperties['docker.compose.waitForHealthy.pollSeconds'] == '2'
    }

    def "usesCompose configures waitForRunning with default values"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            defaultsStack {
                files.from(composeFile)
                waitForRunning {
                    // Only set services, use defaults for timeoutSeconds and pollSeconds
                    waitForServices.set(['app'])
                }
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'defaultsStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()
        testTask.systemProperties['docker.compose.waitForRunning.services'] == 'app'
        testTask.systemProperties['docker.compose.waitForRunning.timeoutSeconds'] == '60'
        testTask.systemProperties['docker.compose.waitForRunning.pollSeconds'] == '2'
    }

    def "usesCompose with method lifecycle does not auto-wire task dependencies"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            methodStack {
                files.from(composeFile)
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'methodStack', com.kineticfire.gradle.docker.Lifecycle.METHOD)

        then:
        noExceptionThrown()
        // Method lifecycle should NOT wire task dependencies
        !testTask.dependsOn.any { it.toString().contains('composeUp') }
    }

    def "usesCompose with empty waitForServices throws exception"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        when:
        dockerTestExt.composeStacks {
            emptyServicesStack {
                files.from(composeFile)
                waitForHealthy {
                    // Empty services list - should throw exception
                    waitForServices.set([])
                    timeoutSeconds.set(30)
                }
            }
        }

        then:
        def e = thrown(GradleException)
        e.message.contains("Configuration error in 'waitForHealthy' block")
        e.message.contains("'waitForServices' must specify at least one service")
    }

    // ===== WAIT FOR LOG SYSTEM PROPERTY TESTS =====

    def "usesCompose configures waitForLog system properties"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            logStack {
                files.from(composeFile)
                waitForLog {
                    waitForServices.set([
                        'app': ['Started', 'Ready'],
                        'db': ['accepting connections']
                    ])
                    rejectPatterns.set([
                        'app': ['Error', 'Fatal']
                    ])
                    timeoutSeconds.set(120)
                    pollSeconds.set(5)
                    caseInsensitive.set(true)
                    verbose.set(true)
                    progressIntervalSeconds.set(15)
                }
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'logStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()

        // Services should be JSON serialized
        def servicesJson = testTask.systemProperties['docker.compose.waitForLog.services']
        servicesJson != null
        servicesJson.contains('app')
        servicesJson.contains('Started')
        servicesJson.contains('Ready')
        servicesJson.contains('db')
        servicesJson.contains('accepting connections')

        // Reject patterns should be JSON serialized
        def rejectJson = testTask.systemProperties['docker.compose.waitForLog.rejectPatterns']
        rejectJson != null
        rejectJson.contains('app')
        rejectJson.contains('Error')
        rejectJson.contains('Fatal')

        // Scalar properties
        testTask.systemProperties['docker.compose.waitForLog.timeoutSeconds'] == '120'
        testTask.systemProperties['docker.compose.waitForLog.pollSeconds'] == '5'
        testTask.systemProperties['docker.compose.waitForLog.caseInsensitive'] == 'true'
        testTask.systemProperties['docker.compose.waitForLog.verbose'] == 'true'
        testTask.systemProperties['docker.compose.waitForLog.progressIntervalSeconds'] == '15'
    }

    def "usesCompose configures waitForLog with default values"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            defaultLogStack {
                files.from(composeFile)
                waitForLog {
                    // Only set required services, use defaults for everything else
                    waitForServices.set(['app': ['Started']])
                }
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'defaultLogStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()

        // Services should be set
        def servicesJson = testTask.systemProperties['docker.compose.waitForLog.services']
        servicesJson != null
        servicesJson.contains('app')
        servicesJson.contains('Started')

        // Default values for optional properties
        testTask.systemProperties['docker.compose.waitForLog.rejectPatterns'] == '{}'
        testTask.systemProperties['docker.compose.waitForLog.timeoutSeconds'] == '60'
        testTask.systemProperties['docker.compose.waitForLog.pollSeconds'] == '2'
        testTask.systemProperties['docker.compose.waitForLog.caseInsensitive'] == 'false'
        testTask.systemProperties['docker.compose.waitForLog.verbose'] == 'false'
        testTask.systemProperties['docker.compose.waitForLog.progressIntervalSeconds'] == '0'
    }

    def "usesCompose does not set waitForLog properties when not configured"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            noLogStack {
                files.from(composeFile)
                // No waitForLog configured
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'noLogStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()

        // waitForLog properties should NOT be set
        testTask.systemProperties['docker.compose.waitForLog.services'] == null
        testTask.systemProperties['docker.compose.waitForLog.rejectPatterns'] == null
        testTask.systemProperties['docker.compose.waitForLog.timeoutSeconds'] == null
        testTask.systemProperties['docker.compose.waitForLog.pollSeconds'] == null
        testTask.systemProperties['docker.compose.waitForLog.caseInsensitive'] == null
        testTask.systemProperties['docker.compose.waitForLog.verbose'] == null
        testTask.systemProperties['docker.compose.waitForLog.progressIntervalSeconds'] == null
    }

    def "usesCompose configures waitForLog alongside other wait specs"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            allWaitsStack {
                files.from(composeFile)
                waitForRunning {
                    waitForServices.set(['app'])
                    timeoutSeconds.set(30)
                }
                waitForHealthy {
                    waitForServices.set(['db'])
                    timeoutSeconds.set(90)
                }
                waitForLog {
                    waitForServices.set(['app': ['Application started']])
                    timeoutSeconds.set(120)
                }
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'allWaitsStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()

        // All three wait specs should be configured
        testTask.systemProperties['docker.compose.waitForRunning.services'] == 'app'
        testTask.systemProperties['docker.compose.waitForRunning.timeoutSeconds'] == '30'

        testTask.systemProperties['docker.compose.waitForHealthy.services'] == 'db'
        testTask.systemProperties['docker.compose.waitForHealthy.timeoutSeconds'] == '90'

        def servicesJson = testTask.systemProperties['docker.compose.waitForLog.services']
        servicesJson != null
        servicesJson.contains('app')
        servicesJson.contains('Application started')
        testTask.systemProperties['docker.compose.waitForLog.timeoutSeconds'] == '120'
    }

    def "usesCompose configures waitForLog for METHOD lifecycle"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            methodLogStack {
                files.from(composeFile)
                waitForLog {
                    waitForServices.set(['app': ['Ready']])
                    timeoutSeconds.set(45)
                    verbose.set(true)
                }
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'methodLogStack', com.kineticfire.gradle.docker.Lifecycle.METHOD)

        then:
        noExceptionThrown()

        // waitForLog should still be configured for METHOD lifecycle
        // (the test framework extension uses these properties)
        def servicesJson = testTask.systemProperties['docker.compose.waitForLog.services']
        servicesJson != null
        servicesJson.contains('app')
        servicesJson.contains('Ready')
        testTask.systemProperties['docker.compose.waitForLog.timeoutSeconds'] == '45'
        testTask.systemProperties['docker.compose.waitForLog.verbose'] == 'true'

        // But METHOD lifecycle should NOT wire task dependencies
        !testTask.dependsOn.any { it.toString().contains('composeUp') }
    }

    def "usesCompose configures waitForLog with regex patterns in JSON"() {
        given:
        def testIntegrationExt = project.objects.newInstance(TestIntegrationExtension, project.name,
            project.layout, project.providers)
        def dockerTestExt = project.objects.newInstance(DockerTestExtension)
        testIntegrationExt.setDockerTestExtension(dockerTestExt)

        def composeFile = project.file('docker-compose.yml')
        composeFile.text = 'services: {}'

        dockerTestExt.composeStacks {
            regexLogStack {
                files.from(composeFile)
                waitForLog {
                    waitForServices.set([
                        'app': ['\\[INFO\\].*started', 'port:\\s+\\d+']
                    ])
                    rejectPatterns.set([
                        'app': ['Exception:\\s+.*']
                    ])
                }
            }
        }

        def testTask = project.tasks.register('integrationTest', org.gradle.api.tasks.testing.Test).get()

        when:
        testIntegrationExt.usesCompose(testTask, 'regexLogStack', com.kineticfire.gradle.docker.Lifecycle.CLASS)

        then:
        noExceptionThrown()

        // JSON should preserve regex patterns
        def servicesJson = testTask.systemProperties['docker.compose.waitForLog.services']
        servicesJson != null
        // JSON escapes backslashes, so \\ becomes \\\\
        servicesJson.contains('INFO')
        servicesJson.contains('started')
        servicesJson.contains('port')

        def rejectJson = testTask.systemProperties['docker.compose.waitForLog.rejectPatterns']
        rejectJson != null
        rejectJson.contains('Exception')
    }
}