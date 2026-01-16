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

package com.kineticfire.gradle.docker

import com.kineticfire.gradle.docker.extension.DockerTestExtension
import com.kineticfire.gradle.docker.task.ComposeUpTask
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for GradleDockerPlugin waitForLog wiring.
 * Tests that waitForLog properties are correctly wired from ComposeStackSpec to ComposeUpTask.
 */
class GradleDockerPluginWaitForLogTest extends Specification {

    Project project
    GradleDockerPlugin plugin

    def setup() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply(JavaPlugin)
        plugin = new GradleDockerPlugin()

        // Set system property to indicate test environment for validation
        System.setProperty("gradle.test.running", "true")
    }

    def cleanup() {
        System.clearProperty("gradle.test.running")
    }

    // ===== WAIT FOR LOG WIRING TESTS =====

    def "plugin wires waitForLog properties from ComposeStackSpec to ComposeUpTask"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose file
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = "services:\n  app:\n    image: alpine"

        // Configure a compose stack with waitForLog
        dockerTestExt.composeStacks {
            testStack {
                files.from(composeFile)
                projectName = 'test-project'
                waitForLog {
                    waitForServices.set(['app': ['Started', 'Ready']])
                    rejectPatterns.set(['app': ['Error', 'Fatal']])
                    timeoutSeconds.set(120)
                    pollSeconds.set(5)
                    caseInsensitive.set(true)
                    verbose.set(true)
                    progressIntervalSeconds.set(15)
                }
            }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('composeUpTestStack') as ComposeUpTask
        task != null

        // Verify all waitForLog properties are wired
        task.waitForLogServices.present
        task.waitForLogServices.get() == ['app': ['Started', 'Ready']]

        task.waitForLogRejectPatterns.present
        task.waitForLogRejectPatterns.get() == ['app': ['Error', 'Fatal']]

        task.waitForLogTimeoutSeconds.present
        task.waitForLogTimeoutSeconds.get() == 120

        task.waitForLogPollSeconds.present
        task.waitForLogPollSeconds.get() == 5

        task.waitForLogCaseInsensitive.present
        task.waitForLogCaseInsensitive.get() == true

        task.waitForLogVerbose.present
        task.waitForLogVerbose.get() == true

        task.waitForLogProgressIntervalSeconds.present
        task.waitForLogProgressIntervalSeconds.get() == 15
    }

    def "plugin wires waitForLog with minimal configuration using defaults"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose file
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = "services:\n  db:\n    image: postgres"

        // Configure a compose stack with minimal waitForLog (only required services)
        dockerTestExt.composeStacks {
            minimalStack {
                files.from(composeFile)
                projectName = 'minimal-project'
                waitForLog {
                    waitForServices.set(['db': ['ready to accept connections']])
                }
            }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('composeUpMinimalStack') as ComposeUpTask
        task != null

        // Verify services are wired
        task.waitForLogServices.present
        task.waitForLogServices.get() == ['db': ['ready to accept connections']]

        // Verify defaults are applied (from WaitForLogSpec conventions via getOrElse)
        task.waitForLogRejectPatterns.present
        task.waitForLogRejectPatterns.get() == [:]

        task.waitForLogTimeoutSeconds.present
        task.waitForLogTimeoutSeconds.get() == 60  // default

        task.waitForLogPollSeconds.present
        task.waitForLogPollSeconds.get() == 2  // default

        task.waitForLogCaseInsensitive.present
        task.waitForLogCaseInsensitive.get() == false  // default

        task.waitForLogVerbose.present
        task.waitForLogVerbose.get() == false  // default

        task.waitForLogProgressIntervalSeconds.present
        task.waitForLogProgressIntervalSeconds.get() == 0  // default
    }

    def "plugin does not wire waitForLog properties when not configured"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose file
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = "services:\n  web:\n    image: nginx"

        // Configure a compose stack WITHOUT waitForLog
        dockerTestExt.composeStacks {
            noWaitStack {
                files.from(composeFile)
                projectName = 'no-wait-project'
            }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('composeUpNoWaitStack') as ComposeUpTask
        task != null

        // Verify waitForLog properties are NOT set
        // Note: MapProperty is always present but empty when not configured
        task.waitForLogServices.get().isEmpty()
        task.waitForLogRejectPatterns.get().isEmpty()
        !task.waitForLogTimeoutSeconds.present
        !task.waitForLogPollSeconds.present
        !task.waitForLogCaseInsensitive.present
        !task.waitForLogVerbose.present
        !task.waitForLogProgressIntervalSeconds.present
    }

    def "plugin wires waitForLog with multiple services"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose file
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = "services:\n  app:\n    image: alpine\n  db:\n    image: postgres\n  cache:\n    image: redis"

        // Configure a compose stack with waitForLog for multiple services
        dockerTestExt.composeStacks {
            multiServiceStack {
                files.from(composeFile)
                projectName = 'multi-service'
                waitForLog {
                    waitForServices.set([
                        'app': ['Application started', 'Listening on port'],
                        'db': ['ready to accept connections'],
                        'cache': ['Ready to accept connections']
                    ])
                    rejectPatterns.set([
                        'app': ['FATAL', 'Exception'],
                        'db': ['PANIC']
                    ])
                }
            }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('composeUpMultiServiceStack') as ComposeUpTask
        task != null

        // Verify multiple services are wired
        task.waitForLogServices.present
        def services = task.waitForLogServices.get()
        services.size() == 3
        services['app'] == ['Application started', 'Listening on port']
        services['db'] == ['ready to accept connections']
        services['cache'] == ['Ready to accept connections']

        // Verify reject patterns for multiple services
        task.waitForLogRejectPatterns.present
        def rejectPatterns = task.waitForLogRejectPatterns.get()
        rejectPatterns.size() == 2
        rejectPatterns['app'] == ['FATAL', 'Exception']
        rejectPatterns['db'] == ['PANIC']
    }

    def "plugin wires waitForLog alongside waitForHealthy and waitForRunning"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose file
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = "services:\n  app:\n    image: alpine"

        // Configure a compose stack with all three wait types
        dockerTestExt.composeStacks {
            allWaitsStack {
                files.from(composeFile)
                projectName = 'all-waits'

                waitForRunning {
                    waitForServices.set(['app'])
                    timeoutSeconds.set(30)
                }

                waitForHealthy {
                    waitForServices.set(['app'])
                    timeoutSeconds.set(90)
                }

                waitForLog {
                    waitForServices.set(['app': ['Started']])
                    timeoutSeconds.set(120)
                }
            }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('composeUpAllWaitsStack') as ComposeUpTask
        task != null

        // Verify waitForRunning is wired
        task.waitForRunningServices.present
        task.waitForRunningServices.get() == ['app']
        task.waitForRunningTimeoutSeconds.get() == 30

        // Verify waitForHealthy is wired
        task.waitForHealthyServices.present
        task.waitForHealthyServices.get() == ['app']
        task.waitForHealthyTimeoutSeconds.get() == 90

        // Verify waitForLog is wired
        task.waitForLogServices.present
        task.waitForLogServices.get() == ['app': ['Started']]
        task.waitForLogTimeoutSeconds.get() == 120
    }

    def "plugin wires waitForLog with regex patterns"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose file
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = "services:\n  app:\n    image: alpine"

        // Configure a compose stack with regex patterns
        dockerTestExt.composeStacks {
            regexStack {
                files.from(composeFile)
                projectName = 'regex-project'
                waitForLog {
                    waitForServices.set([
                        'app': [
                            '\\[INFO\\].*started',
                            'Listening on port \\d+',
                            '(?i)ready'
                        ]
                    ])
                    rejectPatterns.set([
                        'app': ['Exception:\\s+.*', '\\[ERROR\\].*']
                    ])
                }
            }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('composeUpRegexStack') as ComposeUpTask
        task != null

        // Verify regex patterns are preserved exactly
        task.waitForLogServices.present
        def patterns = task.waitForLogServices.get()['app']
        patterns.size() == 3
        patterns[0] == '\\[INFO\\].*started'
        patterns[1] == 'Listening on port \\d+'
        patterns[2] == '(?i)ready'

        task.waitForLogRejectPatterns.present
        def rejectPatterns = task.waitForLogRejectPatterns.get()['app']
        rejectPatterns.size() == 2
        rejectPatterns[0] == 'Exception:\\s+.*'
        rejectPatterns[1] == '\\[ERROR\\].*'
    }

    def "plugin correctly names composeUp task with waitForLog configuration"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose file
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = "services:\n  app:\n    image: alpine"

        // Configure a compose stack with underscore/hyphen in name
        dockerTestExt.composeStacks {
            myTestStack {
                files.from(composeFile)
                projectName = 'my-test-project'
                waitForLog {
                    waitForServices.set(['app': ['Started']])
                }
            }
        }

        when:
        project.evaluate()

        then:
        // Task name should be properly capitalized
        def task = project.tasks.findByName('composeUpMyTestStack') as ComposeUpTask
        task != null
        task.description == 'Start Docker Compose stack: myTestStack'
        task.group == 'docker compose'
    }

    // ===== EDGE CASE TESTS =====

    def "plugin handles waitForLog with special characters in patterns"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose file
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = "services:\n  app:\n    image: alpine"

        // Configure with special characters in patterns
        dockerTestExt.composeStacks {
            specialCharsStack {
                files.from(composeFile)
                projectName = 'special-chars'
                waitForLog {
                    waitForServices.set([
                        'app': [
                            'Status: [OK]',
                            'Path: /api/v1/health',
                            'Response: {"status": "healthy"}'
                        ]
                    ])
                }
            }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('composeUpSpecialCharsStack') as ComposeUpTask
        task != null

        def patterns = task.waitForLogServices.get()['app']
        patterns.size() == 3
        patterns[0] == 'Status: [OK]'
        patterns[1] == 'Path: /api/v1/health'
        patterns[2] == 'Response: {"status": "healthy"}'
    }

    def "plugin handles waitForLog with unicode patterns"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose file
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.text = "services:\n  app:\n    image: alpine"

        // Configure with unicode patterns
        dockerTestExt.composeStacks {
            unicodeStack {
                files.from(composeFile)
                projectName = 'unicode-project'
                waitForLog {
                    waitForServices.set([
                        'app': ['日本語ログ', 'Café ready', 'Успех']
                    ])
                }
            }
        }

        when:
        project.evaluate()

        then:
        def task = project.tasks.findByName('composeUpUnicodeStack') as ComposeUpTask
        task != null

        def patterns = task.waitForLogServices.get()['app']
        patterns.size() == 3
        patterns[0] == '日本語ログ'
        patterns[1] == 'Café ready'
        patterns[2] == 'Успех'
    }

    def "plugin handles multiple stacks with different waitForLog configurations"() {
        given:
        plugin.apply(project)
        def dockerTestExt = project.extensions.getByType(DockerTestExtension)

        // Create dummy compose files
        def composeFile1 = project.file('compose1.yml')
        composeFile1.parentFile.mkdirs()
        composeFile1.text = "services:\n  app:\n    image: alpine"

        def composeFile2 = project.file('compose2.yml')
        composeFile2.text = "services:\n  db:\n    image: postgres"

        // Configure multiple stacks
        dockerTestExt.composeStacks {
            stack1 {
                files.from(composeFile1)
                projectName = 'stack1-project'
                waitForLog {
                    waitForServices.set(['app': ['Started']])
                    timeoutSeconds.set(60)
                }
            }
            stack2 {
                files.from(composeFile2)
                projectName = 'stack2-project'
                waitForLog {
                    waitForServices.set(['db': ['ready']])
                    timeoutSeconds.set(120)
                    verbose.set(true)
                }
            }
            stack3 {
                files.from(composeFile1)
                projectName = 'stack3-project'
                // No waitForLog
            }
        }

        when:
        project.evaluate()

        then:
        // Stack 1 has waitForLog with timeout 60
        def task1 = project.tasks.findByName('composeUpStack1') as ComposeUpTask
        task1.waitForLogServices.present
        task1.waitForLogServices.get() == ['app': ['Started']]
        task1.waitForLogTimeoutSeconds.get() == 60
        !task1.waitForLogVerbose.get()  // default

        // Stack 2 has waitForLog with timeout 120 and verbose
        def task2 = project.tasks.findByName('composeUpStack2') as ComposeUpTask
        task2.waitForLogServices.present
        task2.waitForLogServices.get() == ['db': ['ready']]
        task2.waitForLogTimeoutSeconds.get() == 120
        task2.waitForLogVerbose.get() == true

        // Stack 3 has no waitForLog - MapProperty is always present but empty
        def task3 = project.tasks.findByName('composeUpStack3') as ComposeUpTask
        task3.waitForLogServices.get().isEmpty()
    }
}
