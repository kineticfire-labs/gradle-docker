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
 * NOTE: These tests validate OBSOLETE plugin functionality from an older DSL design.
 * The original design used combined image references like: tags = ['myapp:1.0.0', 'myapp:latest']
 * The current design uses separate properties: imageName, version, tags
 *
 * Tests marked @Ignore test the old combined reference format validation which no longer exists.
 * Two tests (tags requirement and empty tags) still pass as they test different validation paths.
 *
 * To re-enable these tests, they would need to be completely rewritten for the current DSL,
 * testing different validation scenarios (e.g., missing imageName, invalid version format, etc.)
 *
 * Original functionality tested (now obsolete):
 * - Full image reference validation (isValidImageReference method) - OBSOLETE
 * - Image name extraction from full references (extractImageName method) - OBSOLETE
 * - Required tags validation for build contexts - STILL VALID
 * - Consistent image name validation across multiple tags - OBSOLETE (different mechanism now)
 * - Enhanced error messaging for invalid image references - OBSOLETE FORMAT
 * - Registry/namespace/port parsing in image references - DONE VIA SEPARATE PROPERTIES NOW
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

    @spock.lang.Ignore("Tests obsolete DSL - old 'tags=[myapp:1.0.0]' format no longer supported")
    def "plugin validates full image references successfully"() {
        given: "Build file with valid full image references"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    simpleApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        tags = ['myapp:latest', 'myapp:1.0.0']
                    }
                    registryApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        tags = ['registry.example.com/team/myapp:latest', 'registry.example.com/team/myapp:1.0.0']
                    }
                    portApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        tags = ['localhost:5000/myapp:test', 'localhost:5000/myapp:dev']
                    }
                }
            }
        """

        when: "Gradle configuration is processed"
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then: "All configurations validate successfully"
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('dockerBuildSimpleApp')
        result.output.contains('dockerBuildRegistryApp')
        result.output.contains('dockerBuildPortApp')
        !result.output.contains('Invalid')
        !result.output.contains('BUILD FAILED')
    }

    @spock.lang.Ignore("Tests obsolete DSL - old 'tags=[invalid-reference]' format no longer supported")
    def "plugin rejects invalid image references with helpful messages"() {
        given: "Build file with invalid image reference"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    invalidApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        tags = ['invalid-reference-without-tag']
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

        then: "Helpful error message is provided"
        result.output.contains('Invalid image reference') || result.output.contains('Invalid Docker image reference')
        result.output.contains('invalid-reference-without-tag')
        result.output.contains('invalidApp')
    }

    @spock.lang.Ignore("Tests obsolete DSL - old combined tag format validation no longer exists")
    def "plugin validates consistent image names across multiple tags"() {
        given: "Build file with inconsistent image names"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    inconsistentApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        tags = ['myapp:1.0.0', 'otherapp:1.0.0']
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

        then: "Error message about inconsistent image names"
        result.output.contains('All tags must reference the same image name') || 
        result.output.contains('different image names') ||
        result.output.contains('myapp') && result.output.contains('otherapp')
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

    @spock.lang.Ignore("Tests obsolete DSL - old 'tags=[registry.com/myapp:1.0.0]' format no longer supported")
    def "plugin accepts complex registry configurations"() {
        given: "Build file with complex registry configurations"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    complexApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        tags = [
                            'gcr.io/my-project-123/team/myapp:latest',
                            'registry.company.com:8080/namespace/myapp:1.0.0',
                            'localhost:5000/dev/myapp:dev-branch-abc123',
                            'docker.io/username/myapp:stable'
                        ]
                    }
                }
            }
        """

        when: "Gradle configuration is processed"
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then: "Complex configurations validate successfully"
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('dockerBuildComplexApp')
        !result.output.contains('Invalid')
        !result.output.contains('BUILD FAILED')
    }

    @spock.lang.Ignore("Tests obsolete DSL - old combined tag format no longer supported")
    def "plugin validates image references with special characters"() {
        given: "Build file with special characters in image references"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    specialApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        tags = [
                            'my-app_2.0:latest',
                            'registry.com/team_name/app-name:v2.0.1-rc.1',
                            'localhost:5000/my.project/app_name:build-123'
                        ]
                    }
                }
            }
        """

        when: "Gradle configuration is processed"
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then: "Special character configurations validate successfully"
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('dockerBuildSpecialApp')
        !result.output.contains('Invalid')
        !result.output.contains('BUILD FAILED')
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

    @spock.lang.Ignore("Tests obsolete DSL - old 'tags=[latest]' format no longer supported")
    def "plugin provides migration guidance for old simple tag format"() {
        given: "Build file attempting to use old simple tag format"
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            docker {
                images {
                    oldFormatApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        tags = ['latest', '1.0.0']  // Old format - simple tags
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

        then: "Migration guidance is provided"
        result.output.contains('Invalid image reference') || result.output.contains('must contain')
        result.output.contains('latest') || result.output.contains('1.0.0')
        result.output.contains('oldFormatApp')
    }
}