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

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir
import org.gradle.testkit.runner.GradleRunner

import java.nio.file.Path

/**
 * Functional tests for sourceRef component assembly and mode consistency validation
 */
class SourceRefComponentAssemblyFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        
        settingsFile << "rootProject.name = 'sourceref-component-test'"
    }

    def "sourceRef repository component assembly works in DSL"() {
        given:
        buildFile.text = '''
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    repositoryTest {
                        // Add minimal build mode properties to satisfy existing validation
                        imageName.set("repositoryTest")
                        
                        // Set empty sourceRef to allow component assembly
                        sourceRef.set("")
                        
                        // Repository approach assembly
                        sourceRefRegistry.set("docker.io")
                        sourceRefRepository.set("company/webapp")
                        sourceRefTag.set("v1.0")
                        pullIfMissing.set(false) // Don't actually pull in test
                        
                        tags.set(["test-repository-assembly"])
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('help', '--stacktrace')  // Just validate DSL parsing
                .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
                .build()

        then:
        result.task(':help').outcome == TaskOutcome.SUCCESS
    }

    def "sourceRef namespace+imageName component assembly works in DSL"() {
        given:
        buildFile.text = '''
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    namespaceTest {
                        // Add minimal build mode properties to satisfy existing validation
                        imageName.set("namespaceTest")
                        
                        // Namespace+imageName approach assembly
                        sourceRefRegistry.set("docker.io")
                        sourceRefNamespace.set("library")
                        sourceRefImageName.set("alpine")
                        sourceRefTag.set("3.18")
                        pullIfMissing.set(false) // Don't actually pull in test
                        
                        tags.set(["test-namespace-assembly"])
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('help', '--stacktrace')  // Just validate DSL parsing
                .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
                .build()

        then:
        result.task(':help').outcome == TaskOutcome.SUCCESS
    }

    def "mixed component assembly patterns work together"() {
        given:
        buildFile.text = '''
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    // Pattern 1: Full sourceRef
                    fullRef {
                        // Add minimal build mode properties to satisfy existing validation
                        imageName.set("fullRef")
                        
                        sourceRef.set("nginx:latest")
                        pullIfMissing.set(false)
                        
                        tags.set(["test-full-ref"])
                    }
                    
                    // Pattern 2: Repository approach
                    repositoryApp {
                        // Add minimal build mode properties to satisfy existing validation
                        imageName.set("repositoryApp")
                        
                        sourceRefRegistry.set("localhost:5000")
                        sourceRefRepository.set("company/app")
                        sourceRefTag.set("dev")
                        pullIfMissing.set(false)
                        
                        tags.set(["test-repository"])
                    }
                    
                    // Pattern 3: Namespace+imageName approach
                    namespaceApp {
                        // Add minimal build mode properties to satisfy existing validation
                        imageName.set("namespaceApp")
                        
                        sourceRefRegistry.set("docker.io")
                        sourceRefNamespace.set("library")
                        sourceRefImageName.set("redis")
                        sourceRefTag.set("alpine")
                        pullIfMissing.set(false)
                        
                        tags.set(["test-namespace"])
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('help', '--stacktrace')  // Just validate DSL parsing
                .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
                .build()

        then:
        result.task(':help').outcome == TaskOutcome.SUCCESS
    }
}