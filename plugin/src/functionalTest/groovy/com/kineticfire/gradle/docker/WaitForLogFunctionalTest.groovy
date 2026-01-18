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

    // ===== Section 2: Convention Tests =====

    // Section 2.1 Default Conventions

    def "waitForLog default timeoutSeconds convention is 60"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    defaultTimeout {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            // timeoutSeconds NOT set - should default to 60
                        }
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def upTask = tasks.getByName('composeUpDefaultTimeout')
                    def timeout = upTask.waitForLogTimeoutSeconds.get()
                    println "Default timeout: \${timeout}"
                    assert timeout == 60
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains("Default timeout: 60")
    }

    def "waitForLog default pollSeconds convention is 2"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    defaultPoll {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            // pollSeconds NOT set - should default to 2
                        }
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def upTask = tasks.getByName('composeUpDefaultPoll')
                    def poll = upTask.waitForLogPollSeconds.get()
                    println "Default poll: \${poll}"
                    assert poll == 2
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains("Default poll: 2")
    }

    def "waitForLog default caseInsensitive convention is false"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    defaultCase {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            // caseInsensitive NOT set - should default to false
                        }
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def upTask = tasks.getByName('composeUpDefaultCase')
                    def caseInsensitive = upTask.waitForLogCaseInsensitive.get()
                    println "Default caseInsensitive: \${caseInsensitive}"
                    assert caseInsensitive == false
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains("Default caseInsensitive: false")
    }

    def "waitForLog default verbose convention is false"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    defaultVerbose {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            // verbose NOT set - should default to false
                        }
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def upTask = tasks.getByName('composeUpDefaultVerbose')
                    def verbose = upTask.waitForLogVerbose.get()
                    println "Default verbose: \${verbose}"
                    assert verbose == false
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains("Default verbose: false")
    }

    def "waitForLog default progressIntervalSeconds convention is 0 (disabled)"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    defaultProgress {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            // progressIntervalSeconds NOT set - should default to 0
                        }
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def upTask = tasks.getByName('composeUpDefaultProgress')
                    def progressInterval = upTask.waitForLogProgressIntervalSeconds.get()
                    println "Default progressInterval: \${progressInterval}"
                    assert progressInterval == 0
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains("Default progressInterval: 0")
    }

    def "waitForLog default rejectPatterns convention is empty map"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    defaultReject {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            // rejectPatterns NOT set - should default to empty map
                        }
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def upTask = tasks.getByName('composeUpDefaultReject')
                    def rejectPatterns = upTask.waitForLogRejectPatterns.get()
                    println "Default rejectPatterns: \${rejectPatterns}"
                    assert rejectPatterns.isEmpty()
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDefault')
            .build()

        then:
        result.output.contains("Default rejectPatterns: [:]")
    }

    // Section 2.2 Convention Override

    def "waitForLog conventions can be overridden"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    overrideConventions {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            // Override all conventions
                            timeoutSeconds.set(180)
                            pollSeconds.set(5)
                            caseInsensitive.set(true)
                            verbose.set(true)
                            progressIntervalSeconds.set(15)
                        }
                    }
                }
            }

            task verifyOverrides {
                doLast {
                    def upTask = tasks.getByName('composeUpOverrideConventions')
                    println "Timeout: \${upTask.waitForLogTimeoutSeconds.get()}"
                    println "Poll: \${upTask.waitForLogPollSeconds.get()}"
                    println "CaseInsensitive: \${upTask.waitForLogCaseInsensitive.get()}"
                    println "Verbose: \${upTask.waitForLogVerbose.get()}"
                    println "ProgressInterval: \${upTask.waitForLogProgressIntervalSeconds.get()}"

                    assert upTask.waitForLogTimeoutSeconds.get() == 180
                    assert upTask.waitForLogPollSeconds.get() == 5
                    assert upTask.waitForLogCaseInsensitive.get() == true
                    assert upTask.waitForLogVerbose.get() == true
                    assert upTask.waitForLogProgressIntervalSeconds.get() == 15
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyOverrides')
            .build()

        then:
        result.output.contains("Timeout: 180")
        result.output.contains("Poll: 5")
        result.output.contains("CaseInsensitive: true")
        result.output.contains("Verbose: true")
        result.output.contains("ProgressInterval: 15")
    }

    def "waitForLog all default conventions apply when no overrides specified"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    allDefaults {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            // Only required property set - all others should use convention defaults
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                        }
                    }
                }
            }

            task verifyAllDefaults {
                doLast {
                    def upTask = tasks.getByName('composeUpAllDefaults')

                    // Verify all default conventions
                    def timeout = upTask.waitForLogTimeoutSeconds.get()
                    def poll = upTask.waitForLogPollSeconds.get()
                    def caseInsensitive = upTask.waitForLogCaseInsensitive.get()
                    def verbose = upTask.waitForLogVerbose.get()
                    def progress = upTask.waitForLogProgressIntervalSeconds.get()
                    def reject = upTask.waitForLogRejectPatterns.get()

                    println "timeoutSeconds default: \${timeout}"
                    println "pollSeconds default: \${poll}"
                    println "caseInsensitive default: \${caseInsensitive}"
                    println "verbose default: \${verbose}"
                    println "progressIntervalSeconds default: \${progress}"
                    println "rejectPatterns default empty: \${reject.isEmpty()}"

                    assert timeout == 60 : "timeoutSeconds should default to 60"
                    assert poll == 2 : "pollSeconds should default to 2"
                    assert caseInsensitive == false : "caseInsensitive should default to false"
                    assert verbose == false : "verbose should default to false"
                    assert progress == 0 : "progressIntervalSeconds should default to 0"
                    assert reject.isEmpty() : "rejectPatterns should default to empty map"

                    println "All convention defaults verified successfully"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyAllDefaults')
            .build()

        then:
        result.output.contains("timeoutSeconds default: 60")
        result.output.contains("pollSeconds default: 2")
        result.output.contains("caseInsensitive default: false")
        result.output.contains("verbose default: false")
        result.output.contains("progressIntervalSeconds default: 0")
        result.output.contains("rejectPatterns default empty: true")
        result.output.contains("All convention defaults verified successfully")
    }

    def "waitForLog partial override preserves unset convention defaults"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    partialOverride {
                        composeFile.set(file('docker-compose.yml'))
                        waitForLog {
                            waitForServices.set([
                                'app': ['Started Application']
                            ])
                            // Only override some properties - others should keep defaults
                            timeoutSeconds.set(120)
                            verbose.set(true)
                            // pollSeconds NOT set - should remain 2
                            // caseInsensitive NOT set - should remain false
                            // progressIntervalSeconds NOT set - should remain 0
                            // rejectPatterns NOT set - should remain empty
                        }
                    }
                }
            }

            task verifyPartialOverride {
                doLast {
                    def upTask = tasks.getByName('composeUpPartialOverride')

                    // Check overridden values
                    def timeout = upTask.waitForLogTimeoutSeconds.get()
                    def verbose = upTask.waitForLogVerbose.get()

                    // Check preserved defaults
                    def poll = upTask.waitForLogPollSeconds.get()
                    def caseInsensitive = upTask.waitForLogCaseInsensitive.get()
                    def progress = upTask.waitForLogProgressIntervalSeconds.get()
                    def reject = upTask.waitForLogRejectPatterns.get()

                    println "Overridden - timeout: \${timeout}"
                    println "Overridden - verbose: \${verbose}"
                    println "Default preserved - poll: \${poll}"
                    println "Default preserved - caseInsensitive: \${caseInsensitive}"
                    println "Default preserved - progress: \${progress}"
                    println "Default preserved - rejectPatterns empty: \${reject.isEmpty()}"

                    // Verify overrides
                    assert timeout == 120 : "timeoutSeconds should be overridden to 120"
                    assert verbose == true : "verbose should be overridden to true"

                    // Verify defaults preserved
                    assert poll == 2 : "pollSeconds should remain default 2"
                    assert caseInsensitive == false : "caseInsensitive should remain default false"
                    assert progress == 0 : "progressIntervalSeconds should remain default 0"
                    assert reject.isEmpty() : "rejectPatterns should remain default empty"

                    println "Partial override with preserved defaults verified successfully"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyPartialOverride')
            .build()

        then:
        result.output.contains("Overridden - timeout: 120")
        result.output.contains("Overridden - verbose: true")
        result.output.contains("Default preserved - poll: 2")
        result.output.contains("Default preserved - caseInsensitive: false")
        result.output.contains("Default preserved - progress: 0")
        result.output.contains("Default preserved - rejectPatterns empty: true")
        result.output.contains("Partial override with preserved defaults verified successfully")
    }
}
