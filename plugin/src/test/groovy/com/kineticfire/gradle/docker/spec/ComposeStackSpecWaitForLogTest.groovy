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

package com.kineticfire.gradle.docker.spec

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ComposeStackSpec.waitForLog extension
 */
class ComposeStackSpecWaitForLogTest extends Specification {

    def project
    def composeStack

    def setup() {
        project = ProjectBuilder.builder().build()
        composeStack = project.objects.newInstance(ComposeStackSpec, 'testStack')
    }

    // ===== WAIT FOR LOG CLOSURE TESTS =====

    def "waitForLog(Closure) configures wait for log spec with single service"() {
        when:
        composeStack.waitForLog {
            waitForServices.set(['app': ['Started Application']])
            timeoutSeconds.set(90)
            pollSeconds.set(3)
        }

        then:
        composeStack.waitForLog.present
        composeStack.waitForLog.get().waitForServices.get() == ['app': ['Started Application']]
        composeStack.waitForLog.get().timeoutSeconds.get() == 90
        composeStack.waitForLog.get().pollSeconds.get() == 3
    }

    def "waitForLog(Closure) configures wait for log spec with multiple services"() {
        when:
        composeStack.waitForLog {
            waitForServices.set([
                'app': ['Started Application', 'Listening on port'],
                'db': ['ready for connections'],
                'cache': ['Server started']
            ])
        }

        then:
        composeStack.waitForLog.present
        def services = composeStack.waitForLog.get().waitForServices.get()
        services.size() == 3
        services['app'] == ['Started Application', 'Listening on port']
        services['db'] == ['ready for connections']
        services['cache'] == ['Server started']
    }

    def "waitForLog(Closure) configures reject patterns"() {
        when:
        composeStack.waitForLog {
            waitForServices.set(['app': ['Started']])
            rejectPatterns.set(['app': ['Error', 'Exception']])
        }

        then:
        composeStack.waitForLog.present
        composeStack.waitForLog.get().rejectPatterns.get() == ['app': ['Error', 'Exception']]
    }

    def "waitForLog(Closure) configures all options"() {
        when:
        composeStack.waitForLog {
            waitForServices.set(['app': ['Started']])
            rejectPatterns.set(['app': ['Fatal']])
            timeoutSeconds.set(120)
            pollSeconds.set(5)
            caseInsensitive.set(true)
            verbose.set(true)
            progressIntervalSeconds.set(10)
        }

        then:
        def spec = composeStack.waitForLog.get()
        spec.waitForServices.get() == ['app': ['Started']]
        spec.rejectPatterns.get() == ['app': ['Fatal']]
        spec.timeoutSeconds.get() == 120
        spec.pollSeconds.get() == 5
        spec.caseInsensitive.get() == true
        spec.verbose.get() == true
        spec.progressIntervalSeconds.get() == 10
    }

    def "waitForLog(Closure) uses default values for optional properties"() {
        when:
        composeStack.waitForLog {
            waitForServices.set(['app': ['Started']])
        }

        then:
        def spec = composeStack.waitForLog.get()
        spec.timeoutSeconds.get() == 60  // default
        spec.pollSeconds.get() == 2  // default
        spec.caseInsensitive.get() == false  // default
        spec.verbose.get() == false  // default
        spec.progressIntervalSeconds.get() == 0  // default
        spec.rejectPatterns.get() == [:]  // default empty
    }

    // ===== WAIT FOR LOG ACTION TESTS =====

    def "waitForLog(Action) configures wait for log spec"() {
        when:
        composeStack.waitForLog(new Action<WaitForLogSpec>() {
            @Override
            void execute(WaitForLogSpec spec) {
                spec.waitForServices.set(['web': ['Application ready']])
                spec.timeoutSeconds.set(180)
                spec.pollSeconds.set(10)
            }
        })

        then:
        composeStack.waitForLog.present
        composeStack.waitForLog.get().waitForServices.get() == ['web': ['Application ready']]
        composeStack.waitForLog.get().timeoutSeconds.get() == 180
        composeStack.waitForLog.get().pollSeconds.get() == 10
    }

    def "waitForLog(Action) configures multiple services with Action"() {
        when:
        composeStack.waitForLog(new Action<WaitForLogSpec>() {
            @Override
            void execute(WaitForLogSpec spec) {
                spec.waitForServices.set([
                    'api': ['API started', 'Health check passed'],
                    'worker': ['Worker initialized']
                ])
                spec.caseInsensitive.set(true)
                spec.verbose.set(true)
            }
        })

        then:
        def waitSpec = composeStack.waitForLog.get()
        waitSpec.waitForServices.get().size() == 2
        waitSpec.waitForServices.get()['api'] == ['API started', 'Health check passed']
        waitSpec.waitForServices.get()['worker'] == ['Worker initialized']
        waitSpec.caseInsensitive.get() == true
        waitSpec.verbose.get() == true
    }

    // ===== VALIDATION ERROR TESTS =====

    def "waitForLog(Closure) throws exception when waitForServices is not set"() {
        when:
        composeStack.waitForLog {
            timeoutSeconds.set(60)
            // No services specified
        }

        then:
        def e = thrown(GradleException)
        e.message.contains("Configuration error in 'waitForLog' block")
        e.message.contains("'waitForServices' must specify at least one service")
        e.message.contains(composeStack.name)
    }

    def "waitForLog(Closure) throws exception when waitForServices is explicitly empty map"() {
        when:
        composeStack.waitForLog {
            waitForServices.set([:])
            timeoutSeconds.set(60)
        }

        then:
        def e = thrown(GradleException)
        e.message.contains("Configuration error in 'waitForLog' block")
        e.message.contains("'waitForServices' must specify at least one service")
    }

    def "waitForLog(Action) throws exception when waitForServices is not set"() {
        when:
        composeStack.waitForLog(new Action<WaitForLogSpec>() {
            @Override
            void execute(WaitForLogSpec spec) {
                spec.timeoutSeconds.set(60)
                // No services specified
            }
        })

        then:
        def e = thrown(GradleException)
        e.message.contains("Configuration error in 'waitForLog' block")
        e.message.contains("'waitForServices' must specify at least one service")
    }

    def "waitForLog(Action) throws exception when waitForServices is empty map"() {
        when:
        composeStack.waitForLog(new Action<WaitForLogSpec>() {
            @Override
            void execute(WaitForLogSpec spec) {
                spec.waitForServices.set([:])
            }
        })

        then:
        def e = thrown(GradleException)
        e.message.contains("Configuration error in 'waitForLog' block")
        e.message.contains("'waitForServices' must specify at least one service")
    }

    def "waitForLog(Closure) throws exception when pattern list is null for service"() {
        when:
        composeStack.waitForLog {
            waitForServices.set(['app': null])
        }

        then:
        // Gradle's MapProperty throws PropertyQueryException when a null value is set,
        // which is an acceptable error for this invalid configuration
        thrown(Exception)
    }

    def "waitForLog(Closure) throws exception when pattern list is empty for service"() {
        when:
        composeStack.waitForLog {
            waitForServices.set(['app': []])
        }

        then:
        def e = thrown(GradleException)
        e.message.contains("Configuration error in 'waitForLog' block")
        e.message.contains("Pattern list for service 'app' cannot be empty")
    }

    def "waitForLog(Closure) throws exception when pattern value is not a string"() {
        when:
        composeStack.waitForLog {
            waitForServices.set(['app': [123]])  // Integer instead of String
        }

        then:
        def e = thrown(GradleException)
        e.message.contains("Configuration error in 'waitForLog' block")
        e.message.contains("Pattern values must be strings")
    }

    def "waitForLog(Action) throws exception when pattern list is empty for service"() {
        when:
        composeStack.waitForLog(new Action<WaitForLogSpec>() {
            @Override
            void execute(WaitForLogSpec spec) {
                spec.waitForServices.set(['db': []])
            }
        })

        then:
        def e = thrown(GradleException)
        e.message.contains("Configuration error in 'waitForLog' block")
        e.message.contains("Pattern list for service 'db' cannot be empty")
    }

    // ===== REGEX PATTERN TESTS =====

    def "waitForLog(Closure) accepts regex patterns in strings"() {
        when:
        composeStack.waitForLog {
            waitForServices.set([
                'app': ['\\[INFO\\].*started', 'port:\\s+\\d+', '(?i)ready']
            ])
        }

        then:
        def patterns = composeStack.waitForLog.get().waitForServices.get()['app']
        patterns.size() == 3
        patterns[0] == '\\[INFO\\].*started'
        patterns[1] == 'port:\\s+\\d+'
        patterns[2] == '(?i)ready'
    }

    // ===== RECONFIGURATION TESTS =====

    def "waitForLog can be reconfigured"() {
        when:
        composeStack.waitForLog {
            waitForServices.set(['app': ['Pattern1']])
            timeoutSeconds.set(30)
        }

        then:
        composeStack.waitForLog.get().waitForServices.get() == ['app': ['Pattern1']]
        composeStack.waitForLog.get().timeoutSeconds.get() == 30

        when:
        composeStack.waitForLog {
            waitForServices.set(['db': ['Pattern2', 'Pattern3']])
            timeoutSeconds.set(60)
        }

        then:
        composeStack.waitForLog.get().waitForServices.get() == ['db': ['Pattern2', 'Pattern3']]
        composeStack.waitForLog.get().timeoutSeconds.get() == 60
    }

    // ===== COEXISTENCE WITH OTHER WAIT SPECS =====

    def "waitForLog can coexist with waitForRunning and waitForHealthy"() {
        when:
        composeStack.waitForRunning {
            waitForServices.set(['app', 'db'])
            timeoutSeconds.set(60)
        }
        composeStack.waitForHealthy {
            waitForServices.set(['app'])
            timeoutSeconds.set(120)
        }
        composeStack.waitForLog {
            waitForServices.set(['app': ['Application started']])
            timeoutSeconds.set(90)
        }

        then:
        composeStack.waitForRunning.present
        composeStack.waitForHealthy.present
        composeStack.waitForLog.present
        composeStack.waitForRunning.get().waitForServices.get() == ['app', 'db']
        composeStack.waitForHealthy.get().waitForServices.get() == ['app']
        composeStack.waitForLog.get().waitForServices.get() == ['app': ['Application started']]
    }

    // ===== EDGE CASES =====

    def "waitForLog property is initially not present"() {
        expect:
        !composeStack.waitForLog.present
    }

    def "waitForLog supports services with underscores and hyphens"() {
        when:
        composeStack.waitForLog {
            waitForServices.set([
                'my_app': ['Started'],
                'my-db': ['Ready'],
                'service_v2': ['Initialized']
            ])
        }

        then:
        def services = composeStack.waitForLog.get().waitForServices.get()
        services.containsKey('my_app')
        services.containsKey('my-db')
        services.containsKey('service_v2')
    }

    def "waitForLog supports unicode in patterns"() {
        when:
        composeStack.waitForLog {
            waitForServices.set(['app': ['日本語パターン', 'Test']])
        }

        then:
        composeStack.waitForLog.get().waitForServices.get()['app'] == ['日本語パターン', 'Test']
    }

    def "waitForLog handles service with many patterns"() {
        given:
        def patterns = (1..100).collect { "Pattern${it}" }

        when:
        composeStack.waitForLog {
            waitForServices.set(['app': patterns])
        }

        then:
        composeStack.waitForLog.get().waitForServices.get()['app'].size() == 100
    }
}
