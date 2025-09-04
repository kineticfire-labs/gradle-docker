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
 * Functional tests for plugin integration with test extensions
 * 
 * NOTE: These tests are disabled due to test framework compatibility issues 
 * with Gradle 9.0.0 configuration cache. The functionality they test is 
 * verified by integration tests in plugin-integration-test/app-image/src/integrationTest/
 */
@Ignore("Disabled due to test framework compatibility with Gradle 9.0.0 configuration cache")
class PluginIntegrationFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        
        settingsFile << "rootProject.name = 'test-plugin-integration'"
    }

    def "plugin configures test integration extension methods"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            task verifyExtensionMethods {
                doLast {
                    def testTask = tasks.create('tempTest', Test)
                    
                    println "usesCompose method available: \${testTask.ext.has('usesCompose')}"
                    println "composeStateFileFor method available: \${project.ext.has('composeStateFileFor')}"
                    
                    def stateFile = project.ext.composeStateFileFor('testStack').get()
                    println "State file: \${stateFile}"
                    
                    assert testTask.ext.has('usesCompose')
                    assert project.ext.has('composeStateFileFor')
                    assert stateFile.endsWith('testStack-state.json')
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('verifyExtensionMethods', '--stacktrace')
            .build()

        then:
        result.output.contains('usesCompose method available: true')
        result.output.contains('composeStateFileFor method available: true')
        result.output.contains('testStack-state.json')
    }

    def "plugin integration usesCompose method works correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('test-compose.yml')
                    }
                }
            }
            
            tasks.register('integrationTest', Test) {
                usesCompose stack: 'testStack', lifecycle: 'suite'
            }
            
            task verifyUsesCompose {
                doLast {
                    def testTask = tasks.getByName('integrationTest')
                    
                    println "Dependencies: \${testTask.dependsOn}"
                    println "Finalizers: \${testTask.finalizedBy.getDependencies()}"
                    
                    def hasUpDep = testTask.dependsOn.contains('composeUpTestStack')
                    def hasDownFinalizer = testTask.finalizedBy.getDependencies().any { 
                        it.toString().contains('composeDownTestStack') 
                    }
                    
                    println "Has composeUp dependency: \${hasUpDep}"
                    println "Has composeDown finalizer: \${hasDownFinalizer}"
                    
                    assert hasUpDep
                    assert hasDownFinalizer
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
            .withArguments('verifyUsesCompose', '--stacktrace')
            .build()

        then:
        result.output.contains('Has composeUp dependency: true')
        result.output.contains('Has composeDown finalizer: true')
    }
}