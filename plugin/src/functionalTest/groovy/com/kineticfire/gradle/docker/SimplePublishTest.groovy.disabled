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
 * Simple test to debug publish target tags issue
 */
class SimplePublishTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "simple publish target with tags should work"() {
        given:
        settingsFile << "rootProject.name = 'simple-publish-test'"
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    testApp {
                        registry.set('docker.io')
                        imageName.set('test-app') 
                        version.set('1.0.0')
                        context.set(file('.'))
                        
                        publish {
                            to('dockerhub') {
                                publishTags(['test-tag'])
                            }
                        }
                    }
                }
            }
            
            tasks.register('printConfig') {
                doLast {
                    println "Configuration successful"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('printConfig', '--info')
            .build()

        then:
        result.task(':printConfig').outcome == TaskOutcome.SUCCESS
        result.output.contains('Configuration successful')
    }
}