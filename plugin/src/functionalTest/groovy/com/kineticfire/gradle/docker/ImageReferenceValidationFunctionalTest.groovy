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
 * Functional tests for Docker image reference validation functionality.
 *
 * Tests validation rules for tag configuration in the current DSL.
 */
class ImageReferenceValidationFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile
    File dockerFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        dockerFile = testProjectDir.resolve('Dockerfile').toFile()

        settingsFile << "rootProject.name = 'image-reference-validation-test'"
        dockerFile << '''FROM alpine:latest
CMD ["echo", "test image"]
'''
    }

    def "plugin requires tags for build contexts"() {
        given: "Build file with missing tags"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    noTagsApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        // No tags specified
                    }
                }
            }
        """

        when: "Gradle configuration is processed"
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then: "Error message about required tags"
        result.output.contains('must specify at least one tag') ||
        result.output.contains('tags are required') ||
        result.output.contains('noTagsApp')
    }

    def "plugin handles empty tags list appropriately"() {
        given: "Build file with empty tags list"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    emptyTagsApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        tags = []
                    }
                }
            }
        """

        when: "Gradle configuration is processed"
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks')
            .buildAndFail()

        then: "Error message about empty tags"
        result.output.contains('must specify at least one tag') ||
        result.output.contains('empty') ||
        result.output.contains('emptyTagsApp')
    }
}
