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
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for TestIntegrationExtension using GradleRunner
 * 
 * NOTE: These tests are disabled due to test framework compatibility issues 
 * with Gradle 9.0.0 configuration cache. The functionality they test is 
 * verified by integration tests in plugin-integration-test/app-image/src/integrationTest/
 */
@Ignore("Disabled due to test framework compatibility with Gradle 9.0.0 configuration cache")
class TestExtensionFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        
        settingsFile << "rootProject.name = 'test-extension'"
    }

    def "plugin configures usesCompose extension correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('test-compose.yml')
                    }
                }
            }
            
            tasks.register('smokeTest', Test) {
                usesCompose stack: 'testStack', lifecycle: 'suite'
            }
            
            task verifyExtension {
                doLast {
                    def testTask = tasks.getByName('smokeTest')
                    def stateFile = composeStateFileFor('testStack').get()
                    
                    println "State file: \${stateFile}"
                    println "Dependencies: \${testTask.dependsOn}"
                    println "Finalizers: \${testTask.finalizedBy.getDependencies()}"
                    
                    assert stateFile.endsWith('testStack-state.json')
                    assert testTask.dependsOn.contains('composeUpTestStack')
                }
            }
        """
        
        // Create dummy compose file
        def composeFile = testProjectDir.resolve('test-compose.yml').toFile()
        composeFile << """
            version: '3'
            services:
              test:
                image: alpine
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('verifyExtension', '--stacktrace')
            .build()

        then:
        result.output.contains('testStack-state.json')
    }

    def "plugin configures composeStateFileFor correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }
            
            task verifyStateFile {
                doLast {
                    def stateFile = composeStateFileFor('myStack').get()
                    println "State file: \${stateFile}"
                    
                    assert stateFile.endsWith('myStack-state.json')
                    assert stateFile.contains('compose-state')
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('verifyStateFile', '--stacktrace')
            .build()

        then:
        result.output.contains('myStack-state.json')
    }

    def "plugin handles different lifecycle configurations"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerOrch {
                composeStacks {
                    classStack {
                        files.from('class-compose.yml')
                    }
                    methodStack {
                        files.from('method-compose.yml')
                    }
                }
            }
            
            tasks.register('classTest', Test) {
                usesCompose stack: 'classStack', lifecycle: 'class'
            }
            
            tasks.register('methodTest', Test) {
                usesCompose stack: 'methodStack', lifecycle: 'method'
            }
            
            task verifyLifecycles {
                doLast {
                    def classTask = tasks.getByName('classTest')
                    def methodTask = tasks.getByName('methodTest')
                    
                    println "Class lifecycle: \${classTask.systemProperties['docker.compose.lifecycle']}"
                    println "Method lifecycle: \${methodTask.systemProperties['docker.compose.lifecycle']}"
                    
                    assert classTask.systemProperties['docker.compose.stack'] == 'classStack'
                    assert classTask.systemProperties['docker.compose.lifecycle'] == 'class'
                    assert methodTask.systemProperties['docker.compose.stack'] == 'methodStack'
                    assert methodTask.systemProperties['docker.compose.lifecycle'] == 'method'
                }
            }
        """
        
        // Create dummy compose files
        testProjectDir.resolve('class-compose.yml').toFile() << "version: '3'\nservices:\n  test:\n    image: alpine"
        testProjectDir.resolve('method-compose.yml').toFile() << "version: '3'\nservices:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('verifyLifecycles', '--stacktrace')
            .build()

        then:
        result.output.contains('Class lifecycle: class')
        result.output.contains('Method lifecycle: method')
    }
}