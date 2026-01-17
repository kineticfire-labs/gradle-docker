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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for the waitForLog DSL block in the dockerTest extension.
 *
 * These tests verify:
 * - DSL configuration syntax and semantics
 * - Property conventions and overrides
 * - Validation error messages
 * - Task property wiring
 * - System property propagation for test framework extensions
 * - Configuration cache compatibility
 *
 * Note: These tests do NOT execute actual Docker commands. They verify Gradle
 * plugin configuration behavior using TestKit.
 */
class WaitForLogFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile
    File composeFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        composeFile = testProjectDir.resolve('docker-compose.yml').toFile()

        settingsFile << "rootProject.name = 'wait-for-log-test'\n"

        // Create minimal compose file for all tests (no deprecated 'version' field per project standards)
        composeFile << """
services:
  app:
    image: alpine:latest
    command: sleep 30
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_PASSWORD: test
"""
    }

    // ===== Section 1: DSL Configuration Tests =====

    // Section 1.1 Basic DSL Configuration

    def "waitForLog with single service and single pattern configures correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    myStack {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                        }
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpMyStack')
                    def services = upTask.waitForLogServices.get()
                    println "Services: \${services}"
                    assert services.containsKey('app')
                    assert services['app'] == ['Started Application']
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Services: [app:[Started Application]]")
    }

    def "waitForLog with multiple patterns for single service uses AND semantics"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    multiPattern {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Database connected', 'Started Application', 'Ready to serve']
                            ])
                        }
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpMultiPattern')
                    def services = upTask.waitForLogServices.get()
                    def patterns = services['app']
                    println "Pattern count: \${patterns.size()}"
                    assert patterns.size() == 3
                    assert patterns.contains('Database connected')
                    assert patterns.contains('Started Application')
                    assert patterns.contains('Ready to serve')
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Pattern count: 3")
    }

    def "waitForLog with multiple services configures correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    multiService {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application'],
                                'db': ['ready to accept connections', 'PostgreSQL init complete']
                            ])
                        }
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpMultiService')
                    def services = upTask.waitForLogServices.get()
                    println "Service count: \${services.size()}"
                    assert services.size() == 2
                    assert services.containsKey('app')
                    assert services.containsKey('db')
                    assert services['app'].size() == 1
                    assert services['db'].size() == 2
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Service count: 2")
    }

    // Section 1.2 DSL Syntax Variations

    def "waitForLog with direct assignment syntax works correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    directAssign {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            // Direct assignment (Groovy converts to set() call)
                            waitForServices = [
                                'app': ['Started Application']
                            ]
                            timeoutSeconds = 120
                            pollSeconds = 5
                        }
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpDirectAssign')
                    println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                    println "Poll: \${upTask.waitForLogPollSeconds.get()}"
                    assert upTask.waitForLogTimeoutSeconds.get() == 120
                    assert upTask.waitForLogPollSeconds.get() == 5
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Timeout: 120")
        result.output.contains("Poll: 5")
    }

    def "waitForLog with explicit set() method syntax works correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    explicitSet {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            timeoutSeconds.set(90)
                            pollSeconds.set(3)
                            caseInsensitive.set(true)
                            verbose.set(true)
                        }
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpExplicitSet')
                    println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                    println "CaseInsensitive: \${upTask.waitForLogCaseInsensitive.get()}"
                    println "Verbose: \${upTask.waitForLogVerbose.get()}"
                    assert upTask.waitForLogTimeoutSeconds.get() == 90
                    assert upTask.waitForLogCaseInsensitive.get() == true
                    assert upTask.waitForLogVerbose.get() == true
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Timeout: 90")
        result.output.contains("CaseInsensitive: true")
        result.output.contains("Verbose: true")
    }

    def "waitForLog with Closure configuration style works correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    closureStyle {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Server started', 'Listening on port']
                            ])
                            timeoutSeconds.set(45)
                            caseInsensitive.set(true)
                        }
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpClosureStyle')
                    def services = upTask.waitForLogServices.get()
                    println "Patterns: \${services['app']}"
                    println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                    println "CaseInsensitive: \${upTask.waitForLogCaseInsensitive.get()}"
                    assert services['app'].size() == 2
                    assert upTask.waitForLogTimeoutSeconds.get() == 45
                    assert upTask.waitForLogCaseInsensitive.get() == true
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Patterns: [Server started, Listening on port]")
        result.output.contains("Timeout: 45")
        result.output.contains("CaseInsensitive: true")
    }

    def "waitForLog with Action configuration style works correctly"() {
        given:
        buildFile << """
            import org.gradle.api.Action
            
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    actionStyle {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog(new Action<com.kineticfire.gradle.docker.spec.WaitForLogSpec>() {
                            @Override
                            void execute(com.kineticfire.gradle.docker.spec.WaitForLogSpec spec) {
                                spec.waitForServices.set([
                                    'app': ['Application ready']
                                ])
                                spec.timeoutSeconds.set(75)
                                spec.verbose.set(true)
                            }
                        })
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpActionStyle')
                    def services = upTask.waitForLogServices.get()
                    println "Services: \${services}"
                    println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                    println "Verbose: \${upTask.waitForLogVerbose.get()}"
                    assert services.containsKey('app')
                    assert services['app'] == ['Application ready']
                    assert upTask.waitForLogTimeoutSeconds.get() == 75
                    assert upTask.waitForLogVerbose.get() == true
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Services: [app:[Application ready]]")
        result.output.contains("Timeout: 75")
        result.output.contains("Verbose: true")
    }

    // Section 1.3 Reject Patterns Configuration

    def "waitForLog with rejectPatterns configures fail-fast error detection"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    withReject {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application'],
                                'db': ['ready to accept connections']
                            ])
                            rejectPatterns.set([
                                'app': ['FATAL', 'Exception', 'Error initializing'],
                                'db': ['FATAL', 'failed to start']
                            ])
                        }
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpWithReject')
                    def rejectPatterns = upTask.waitForLogRejectPatterns.get()
                    println "Reject patterns: \${rejectPatterns}"
                    assert rejectPatterns.containsKey('app')
                    assert rejectPatterns.containsKey('db')
                    assert rejectPatterns['app'].size() == 3
                    assert rejectPatterns['db'].size() == 2
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Reject patterns:")
        result.output.contains("app")
        result.output.contains("FATAL")
    }

    // Section 1.4 Combined Wait Blocks

    def "waitForLog can be combined with waitForHealthy and waitForRunning"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    combined {
                        composeFile.set(file('docker-compose.yml'))

                        // All three wait blocks configured
                        waitForRunning {
                            waitForServices.set(['app', 'db'])
                            timeoutSeconds.set(30)
                        }

                        waitForHealthy {
                            waitForServices.set(['db'])
                            timeoutSeconds.set(60)
                        }

                        waitForLog {
                            waitForServices.set([
                                'app': ['Ready to serve requests']
                            ])
                            timeoutSeconds.set(120)
                        }
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpCombined')

                    // Verify all three are configured
                    println "Running services: \${upTask.waitForRunningServices.get()}"
                    println "Healthy services: \${upTask.waitForHealthyServices.get()}"
                    println "Log services: \${upTask.waitForLogServices.get().keySet()}"

                    assert upTask.waitForRunningServices.get() == ['app', 'db']
                    assert upTask.waitForHealthyServices.get() == ['db']
                    assert upTask.waitForLogServices.get().containsKey('app')
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Running services: [app, db]")
        result.output.contains("Healthy services: [db]")
        result.output.contains("Log services: [app]")
    }

    // Section 1.5 Progress Interval Configuration

    def "waitForLog progressIntervalSeconds configures periodic progress logging"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    withProgress {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            timeoutSeconds.set(300)
                            progressIntervalSeconds.set(30)
                        }
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def upTask = tasks.getByName('composeUpWithProgress')
                    println "Progress interval: \${upTask.waitForLogProgressIntervalSeconds.get()}"
                    assert upTask.waitForLogProgressIntervalSeconds.get() == 30
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyConfig')
            .build()

        then:
        result.output.contains("Progress interval: 30")
    }

    // Section 1.6 All Optional Properties Test

    def "waitForLog all optional properties can be configured together"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    allOptional {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started', 'Ready'],
                                'db': ['accepting connections']
                            ])
                            rejectPatterns.set([
                                'app': ['ERROR', 'FATAL', 'Exception'],
                                'db': ['PANIC', 'FATAL']
                            ])
                            timeoutSeconds.set(180)
                            pollSeconds.set(5)
                            caseInsensitive.set(true)
                            verbose.set(true)
                            progressIntervalSeconds.set(15)
                        }
                    }
                }
            }

            task verifyAllProperties {
                doLast {
                    def upTask = tasks.getByName('composeUpAllOptional')
                    
                    // Verify waitForServices
                    def services = upTask.waitForLogServices.get()
                    assert services.size() == 2
                    assert services['app'].size() == 2
                    assert services['db'].size() == 1
                    
                    // Verify rejectPatterns
                    def rejectPatterns = upTask.waitForLogRejectPatterns.get()
                    assert rejectPatterns.size() == 2
                    assert rejectPatterns['app'].size() == 3
                    assert rejectPatterns['db'].size() == 2
                    
                    // Verify all scalar properties
                    assert upTask.waitForLogTimeoutSeconds.get() == 180
                    assert upTask.waitForLogPollSeconds.get() == 5
                    assert upTask.waitForLogCaseInsensitive.get() == true
                    assert upTask.waitForLogVerbose.get() == true
                    assert upTask.waitForLogProgressIntervalSeconds.get() == 15
                    
                    println "All optional properties verified successfully"
                    println "Services: \${services}"
                    println "RejectPatterns: \${rejectPatterns}"
                    println "TimeoutSeconds: \${upTask.waitForLogTimeoutSeconds.get()}"
                    println "PollSeconds: \${upTask.waitForLogPollSeconds.get()}"
                    println "CaseInsensitive: \${upTask.waitForLogCaseInsensitive.get()}"
                    println "Verbose: \${upTask.waitForLogVerbose.get()}"
                    println "ProgressIntervalSeconds: \${upTask.waitForLogProgressIntervalSeconds.get()}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyAllProperties')
            .build()

        then:
        result.output.contains("All optional properties verified successfully")
        result.output.contains("TimeoutSeconds: 180")
        result.output.contains("PollSeconds: 5")
        result.output.contains("CaseInsensitive: true")
        result.output.contains("Verbose: true")
        result.output.contains("ProgressIntervalSeconds: 15")
    }
}
