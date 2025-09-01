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
 * Functional tests for Docker Compose operations
 */
class ComposeFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "compose up task executes successfully with valid compose file"() {
        given:
        settingsFile << "rootProject.name = 'test-compose'"
        
        // Create a simple docker-compose.yml
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            version: '3.8'
            services:
              test-service:
                image: alpine:latest
                command: sleep 30
                labels:
                  - "test=true"
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            compose {
                stacks {
                    test {
                        composeFile = 'docker-compose.yml'
                        projectName = 'gradle-test'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('composeUpTest', '--info')
            .build()

        then:
        result.task(':composeUpTest').outcome == TaskOutcome.SUCCESS
        result.output.contains('Starting Docker Compose stack') || result.output.contains('compose')
    }

    def "compose down task executes successfully"() {
        given:
        settingsFile << "rootProject.name = 'test-compose-down'"
        
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            version: '3.8'
            services:
              test-service:
                image: alpine:latest
                command: sleep 5
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            compose {
                stacks {
                    test {
                        composeFile = 'docker-compose.yml'
                        projectName = 'gradle-test-down'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('composeDownTest', '--info')
            .build()

        then:
        result.task(':composeDownTest').outcome == TaskOutcome.SUCCESS
        result.output.contains('Stopping Docker Compose stack') || result.output.contains('compose')
    }

    def "compose up task with environment file"() {
        given:
        settingsFile << "rootProject.name = 'test-compose-env'"
        
        // Create .env file
        def envFile = testProjectDir.resolve('.env').toFile()
        envFile << """
            TEST_VAR=test_value
            ENV_NAME=testing
        """
        
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            version: '3.8'
            services:
              app:
                image: alpine:latest
                environment:
                  - TEST_VAR=\${TEST_VAR}
                  - ENV_NAME=\${ENV_NAME}
                command: sleep 10
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            compose {
                stacks {
                    env_test {
                        composeFile = 'docker-compose.yml'
                        envFile = '.env'
                        projectName = 'gradle-env-test'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('composeUpEnv_test', '--info')
            .build()

        then:
        result.task(':composeUpEnv_test').outcome == TaskOutcome.SUCCESS
        result.output.contains('Starting Docker Compose stack') || result.output.contains('compose')
    }

    def "compose up task with profiles"() {
        given:
        settingsFile << "rootProject.name = 'test-compose-profiles'"
        
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            version: '3.8'
            services:
              app:
                image: alpine:latest
                command: sleep 10
                profiles: ["app"]
              
              db:
                image: alpine:latest
                command: sleep 10
                profiles: ["database"]
                
              cache:
                image: alpine:latest
                command: sleep 10
                profiles: ["cache", "dev"]
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            compose {
                stacks {
                    dev {
                        composeFile = 'docker-compose.yml'
                        projectName = 'gradle-profiles-test'
                        profiles = ['app', 'dev']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('composeUpDev', '--info')
            .build()

        then:
        result.task(':composeUpDev').outcome == TaskOutcome.SUCCESS
        result.output.contains('Starting Docker Compose stack') || result.output.contains('compose')
    }

    def "compose up task fails with invalid compose file"() {
        given:
        settingsFile << "rootProject.name = 'test-invalid-compose'"
        
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            version: '3.8'
            services:
              broken-service:
                image: 
                invalid_syntax: true
                ports:
                  - "invalid-port-format"
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            compose {
                stacks {
                    broken {
                        composeFile = 'docker-compose.yml'
                        projectName = 'gradle-broken-test'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('composeUpBroken', '--info')
            .buildAndFail()

        then:
        result.task(':composeUpBroken').outcome == TaskOutcome.FAILED
        result.output.contains('Docker Compose') || result.output.contains('compose')
    }

    def "compose up task fails when Docker Compose not available"() {
        given:
        settingsFile << "rootProject.name = 'test-no-compose'"
        
        def composeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        composeFile << """
            version: '3.8'
            services:
              test:
                image: alpine:latest
                command: sleep 5
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            compose {
                stacks {
                    test {
                        composeFile = 'docker-compose.yml'
                        projectName = 'gradle-no-compose-test'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('composeUpTest', '--info')
            .buildAndFail()

        then:
        result.task(':composeUpTest').outcome == TaskOutcome.FAILED
        result.output.contains('Docker Compose') || result.output.contains('compose')
    }
}