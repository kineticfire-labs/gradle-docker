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
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ComposeStackSpec
 */
class ComposeStackSpecTest extends Specification {

    def project
    def composeStack

    def setup() {
        project = ProjectBuilder.builder().build()
        composeStack = project.objects.newInstance(ComposeStackSpec, 'testStack', project)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with name and project"() {
        expect:
        composeStack != null
        composeStack.name == 'testStack'
    }

    def "constructor with different names"() {
        given:
        def stack1 = project.objects.newInstance(ComposeStackSpec, 'web', project)
        def stack2 = project.objects.newInstance(ComposeStackSpec, 'database', project)

        expect:
        stack1.name == 'web'
        stack2.name == 'database'
    }

    // ===== BASIC PROPERTY TESTS =====

    def "composeFile property works correctly"() {
        given:
        def composeFile = project.file('docker-compose.yml')

        when:
        composeStack.composeFile.set(composeFile)

        then:
        composeStack.composeFile.present
        composeStack.composeFile.get().asFile == composeFile
    }

    def "envFile property works correctly"() {
        given:
        def envFile = project.file('.env')

        when:
        composeStack.envFile.set(envFile)

        then:
        composeStack.envFile.present
        composeStack.envFile.get().asFile == envFile
    }

    def "profiles property works correctly"() {
        when:
        composeStack.profiles.set(['dev', 'debug'])

        then:
        composeStack.profiles.present
        composeStack.profiles.get() == ['dev', 'debug']
    }

    def "services property works correctly"() {
        when:
        composeStack.services.set(['web', 'db', 'redis'])

        then:
        composeStack.services.present
        composeStack.services.get() == ['web', 'db', 'redis']
    }

    def "environment property works correctly"() {
        when:
        composeStack.environment.set(['ENV': 'test', 'DEBUG': 'true'])

        then:
        composeStack.environment.present
        composeStack.environment.get() == ['ENV': 'test', 'DEBUG': 'true']
    }

    // ===== ORIGINAL PROPERTY TESTS =====

    def "files property works correctly"() {
        given:
        def compose1 = project.file('docker-compose.yml')
        def compose2 = project.file('docker-compose.override.yml')

        when:
        composeStack.files.from(compose1, compose2)

        then:
        composeStack.files.files.size() == 2
        composeStack.files.files.contains(compose1)
        composeStack.files.files.contains(compose2)
    }

    def "envFiles property works correctly"() {
        given:
        def env1 = project.file('.env')
        def env2 = project.file('.env.local')

        when:
        composeStack.envFiles.from(env1, env2)

        then:
        composeStack.envFiles.files.size() == 2
        composeStack.envFiles.files.contains(env1)
        composeStack.envFiles.files.contains(env2)
    }

    def "projectName property works correctly"() {
        when:
        composeStack.projectName.set('my-web-project')

        then:
        composeStack.projectName.present
        composeStack.projectName.get() == 'my-web-project'
    }

    // ===== WAIT FOR RUNNING TESTS =====

    def "waitForRunning(Closure) configures wait spec"() {
        when:
        composeStack.waitForRunning {
            services = ['web', 'db']
            timeoutSeconds = 120
            pollSeconds = 5
        }

        then:
        composeStack.waitForRunning.present
        composeStack.waitForRunning.get().services.get() == ['web', 'db']
        composeStack.waitForRunning.get().timeoutSeconds.get() == 120
        composeStack.waitForRunning.get().pollSeconds.get() == 5
    }

    def "waitForRunning(Action) configures wait spec"() {
        when:
        composeStack.waitForRunning(new Action<WaitSpec>() {
            @Override
            void execute(WaitSpec waitSpec) {
                waitSpec.services.set(['api', 'worker'])
                waitSpec.timeoutSeconds.set(180)
            }
        })

        then:
        composeStack.waitForRunning.present
        composeStack.waitForRunning.get().services.get() == ['api', 'worker']
        composeStack.waitForRunning.get().timeoutSeconds.get() == 180
        composeStack.waitForRunning.get().pollSeconds.get() == 2 // default
    }

    // ===== WAIT FOR HEALTHY TESTS =====

    def "waitForHealthy(Closure) configures wait spec"() {
        when:
        composeStack.waitForHealthy {
            services = ['web']
            timeoutSeconds = 300
            pollSeconds = 10
        }

        then:
        composeStack.waitForHealthy.present
        composeStack.waitForHealthy.get().services.get() == ['web']
        composeStack.waitForHealthy.get().timeoutSeconds.get() == 300
        composeStack.waitForHealthy.get().pollSeconds.get() == 10
    }

    def "waitForHealthy(Action) configures wait spec"() {
        when:
        composeStack.waitForHealthy(new Action<WaitSpec>() {
            @Override
            void execute(WaitSpec waitSpec) {
                waitSpec.services.set(['database'])
                waitSpec.timeoutSeconds.set(240)
                waitSpec.pollSeconds.set(15)
            }
        })

        then:
        composeStack.waitForHealthy.present
        composeStack.waitForHealthy.get().services.get() == ['database']
        composeStack.waitForHealthy.get().timeoutSeconds.get() == 240
        composeStack.waitForHealthy.get().pollSeconds.get() == 15
    }

    // ===== LOGS CONFIGURATION TESTS =====

    def "logs(Closure) configures logs spec"() {
        given:
        def logFile = project.file('compose.log')

        when:
        composeStack.logs {
            writeTo = logFile
            tailLines = 500
        }

        then:
        composeStack.logs.present
        composeStack.logs.get().writeTo.get().asFile == logFile
        composeStack.logs.get().tailLines.get() == 500
    }

    def "logs(Action) configures logs spec"() {
        given:
        def logFile = project.file('output.log')

        when:
        composeStack.logs(new Action<LogsSpec>() {
            @Override
            void execute(LogsSpec logsSpec) {
                logsSpec.writeTo.set(logFile)
                logsSpec.tailLines.set(200)
            }
        })

        then:
        composeStack.logs.present
        composeStack.logs.get().writeTo.get().asFile == logFile
        composeStack.logs.get().tailLines.get() == 200
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        def envFile = project.file('.env')
        def logFile = project.file('logs.txt')

        when:
        composeStack.composeFile.set(composeFile)
        composeStack.envFile.set(envFile)
        composeStack.profiles.set(['prod', 'monitoring'])
        composeStack.services.set(['web', 'api', 'db'])
        composeStack.environment.set(['STAGE': 'production', 'LOG_LEVEL': 'info'])
        composeStack.projectName.set('myproject-prod')
        composeStack.waitForRunning {
            services = ['web', 'api']
            timeoutSeconds = 120
        }
        composeStack.waitForHealthy {
            services = ['db']
            timeoutSeconds = 180
        }
        composeStack.logs {
            writeTo = logFile
            tailLines = 1000
        }

        then:
        composeStack.name == 'testStack'
        composeStack.composeFile.get().asFile == composeFile
        composeStack.envFile.get().asFile == envFile
        composeStack.profiles.get() == ['prod', 'monitoring']
        composeStack.services.get() == ['web', 'api', 'db']
        composeStack.environment.get() == ['STAGE': 'production', 'LOG_LEVEL': 'info']
        composeStack.projectName.get() == 'myproject-prod'
        composeStack.waitForRunning.present
        composeStack.waitForHealthy.present
        composeStack.logs.present
    }

    // ===== EDGE CASES =====

    def "nested specs can be reconfigured"() {
        given:
        def logFile1 = project.file('log1.txt')
        def logFile2 = project.file('log2.txt')

        when:
        composeStack.logs {
            writeTo = logFile1
            tailLines = 100
        }

        then:
        composeStack.logs.get().writeTo.get().asFile == logFile1
        composeStack.logs.get().tailLines.get() == 100

        when:
        composeStack.logs {
            writeTo = logFile2
            tailLines = 500
        }

        then:
        composeStack.logs.get().writeTo.get().asFile == logFile2
        composeStack.logs.get().tailLines.get() == 500
    }

    def "empty lists and maps are supported"() {
        when:
        composeStack.profiles.set([])
        composeStack.services.set([])
        composeStack.environment.set([:])

        then:
        composeStack.profiles.get().isEmpty()
        composeStack.services.get().isEmpty()
        composeStack.environment.get().isEmpty()
    }
}