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

import java.nio.file.Files
import java.nio.file.Path

/**
 * Functional tests for Docker build operations
 * 
 * TEMPORARY DISABLED: These tests are temporarily commented out due to known incompatibility 
 * between Gradle TestKit and Gradle 9.0.0. The issue is tracked and will be re-enabled 
 * when TestKit compatibility is improved or an alternative testing approach is implemented.
 * 
 * Issue: InvalidPluginMetadataException when using withPluginClasspath() in Gradle 9.0.0
 * Root cause: Gradle 9.0.0 TestKit has breaking changes in plugin classpath resolution
 * 
 * Tests affected: All tests using withPluginClasspath() method (5 tests)
 * Functionality affected: 
 * - Docker build task execution
 * - Build task error handling
 * - Custom build arguments support
 * - Docker daemon availability checks
 * - Build output verification
 */
class DockerBuildFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    // TEMPORARILY DISABLED - All tests in this class use withPluginClasspath() which is incompatible with Gradle 9.0.0 TestKit
    /*

    def "docker build task executes successfully with valid Dockerfile"() {
        given:
        settingsFile << "rootProject.name = 'test-docker-build'"
        
        // Create a simple Dockerfile
        def dockerFile = testProjectDir.resolve('Dockerfile').toFile()
        dockerFile << """
            FROM alpine:latest
            RUN echo "Test image"
            LABEL test=true
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    test {
                        dockerfile = 'Dockerfile'
                        contextPath = '.'
                        tags = ['test:latest']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildTest', '--info')
            .build()

        then:
        result.task(':dockerBuildTest').outcome == TaskOutcome.SUCCESS
        result.output.contains('Building Docker image')
    }

    def "docker build task skips when no Docker daemon available"() {
        given:
        settingsFile << "rootProject.name = 'test-no-docker'"
        
        def dockerFile = testProjectDir.resolve('Dockerfile').toFile()
        dockerFile << """
            FROM alpine:latest
            RUN echo "Test image"
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    test {
                        dockerfile = 'Dockerfile'
                        contextPath = '.'
                        tags = ['test:latest']
                    }
                }
            }
            
            // Mock the Docker service to simulate no daemon
            tasks.named('dockerBuildTest') {
                doFirst {
                    // This test verifies error handling when Docker is not available
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildTest', '--info')
            .buildAndFail()

        then:
        result.task(':dockerBuildTest').outcome == TaskOutcome.FAILED
        result.output.contains('Docker')
    }

    def "docker build task uses custom build args"() {
        given:
        settingsFile << "rootProject.name = 'test-build-args'"
        
        def dockerFile = testProjectDir.resolve('Dockerfile').toFile()
        dockerFile << """
            FROM alpine:latest
            ARG BUILD_VERSION=unknown
            ARG BUILD_ENV=dev
            RUN echo "Version: \${BUILD_VERSION}, Env: \${BUILD_ENV}"
            LABEL version=\${BUILD_VERSION}
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    app {
                        dockerfile = 'Dockerfile'
                        contextPath = '.'
                        tags = ['app:1.2.3']
                        buildArgs = [
                            'BUILD_VERSION': '1.2.3',
                            'BUILD_ENV': 'production'
                        ]
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildApp', '--info')
            .build()

        then:
        result.task(':dockerBuildApp').outcome == TaskOutcome.SUCCESS
        result.output.contains('Building Docker image')
    }

    def "docker build task fails with invalid Dockerfile"() {
        given:
        settingsFile << "rootProject.name = 'test-invalid-dockerfile'"
        
        def dockerFile = testProjectDir.resolve('Dockerfile').toFile()
        dockerFile << """
            INVALID_INSTRUCTION alpine:latest
            RUN echo "This will fail"
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    broken {
                        dockerfile = 'Dockerfile'
                        contextPath = '.'
                        tags = ['broken:latest']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildBroken', '--info')
            .buildAndFail()

        then:
        result.task(':dockerBuildBroken').outcome == TaskOutcome.FAILED
        result.output.contains('Build failed') || result.output.contains('Docker')
    }

    def "docker build task creates output correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-output'"
        
        def dockerFile = testProjectDir.resolve('Dockerfile').toFile()
        dockerFile << """
            FROM alpine:latest
            RUN echo "Test output" > /tmp/test.txt
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    output {
                        dockerfile = 'Dockerfile'
                        contextPath = '.'
                        tags = ['output-test:latest']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildOutput', '--info')
            .build()

        then:
        result.task(':dockerBuildOutput').outcome == TaskOutcome.SUCCESS
        result.output.contains('Building Docker image') || result.output.contains('Successfully built')
    }

    def "docker build task uses dockerfileName with context directory"() {
        given:
        settingsFile << "rootProject.name = 'test-dockerfile-name'"
        
        // Create custom Dockerfile with different name
        testProjectDir.resolve('docker').toFile().mkdirs()
        def dockerFile = testProjectDir.resolve('docker/Dockerfile.prod').toFile()
        dockerFile << """
            FROM alpine:latest
            RUN echo "Production image"
            LABEL environment=production
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    prodApp {
                        context = file('docker')
                        dockerfileName = 'Dockerfile.prod'
                        tags = ['prod-app:latest']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildProdApp', '--info')
            .build()

        then:
        result.task(':dockerBuildProdApp').outcome == TaskOutcome.SUCCESS
        result.output.contains('Building Docker image')
    }

    def "docker build task uses dockerfileName with contextTask"() {
        given:
        settingsFile << "rootProject.name = 'test-dockerfile-name-context-task'"
        
        // Create source directory with custom Dockerfile name
        testProjectDir.resolve('src/main/docker').toFile().mkdirs()
        def dockerFile = testProjectDir.resolve('src/main/docker/Dockerfile.dev').toFile()
        dockerFile << """
            FROM alpine:latest
            RUN echo "Development image"
            LABEL environment=development
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    devApp {
                        context {
                            from 'src/main/docker'
                        }
                        dockerfileName = 'Dockerfile.dev'
                        tags = ['dev-app:latest']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildDevApp', '--info')
            .build()

        then:
        result.task(':dockerBuildDevApp').outcome == TaskOutcome.SUCCESS
        result.output.contains('Building Docker image')
    }

    def "docker build task fails when both dockerfile and dockerfileName are set"() {
        given:
        settingsFile << "rootProject.name = 'test-conflicting-dockerfile-config'"
        
        // Create both Dockerfiles
        def dockerFile1 = testProjectDir.resolve('Dockerfile').toFile()
        dockerFile1 << """
            FROM alpine:latest
            RUN echo "Default Dockerfile"
        """
        
        def dockerFile2 = testProjectDir.resolve('Dockerfile.custom').toFile()
        dockerFile2 << """
            FROM alpine:latest
            RUN echo "Custom Dockerfile"
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    conflictingApp {
                        context = file('.')
                        dockerfile = file('Dockerfile')
                        dockerfileName = 'Dockerfile.custom'
                        tags = ['conflict-app:latest']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildConflictingApp', '--info')
            .buildAndFail()

        then:
        result.task(':dockerBuildConflictingApp') == null // Task creation should fail during configuration
        result.output.contains("cannot have both 'dockerfile' and 'dockerfileName' set")
    }

    def "docker build task uses default Dockerfile when neither dockerfile nor dockerfileName set"() {
        given:
        settingsFile << "rootProject.name = 'test-default-dockerfile'"
        
        // Create context directory with default Dockerfile
        testProjectDir.resolve('docker-context').toFile().mkdirs()
        def dockerFile = testProjectDir.resolve('docker-context/Dockerfile').toFile()
        dockerFile << """
            FROM alpine:latest
            RUN echo "Default Dockerfile name"
            LABEL type=default
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    defaultApp {
                        context = file('docker-context')
                        tags = ['default-app:latest']
                        // Neither dockerfile nor dockerfileName set
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildDefaultApp', '--info')
            .build()

        then:
        result.task(':dockerBuildDefaultApp').outcome == TaskOutcome.SUCCESS
        result.output.contains('Building Docker image')
    }

    def "docker build task handles various dockerfileName formats"() {
        given:
        settingsFile << "rootProject.name = 'test-various-dockerfile-names'"
        
        // Create context directory with various Dockerfile names
        testProjectDir.resolve('build-context').toFile().mkdirs()
        
        def customNames = ['Dockerfile.prod', 'MyDockerfile', 'app.dockerfile', 'Dockerfile-alpine']
        customNames.each { name ->
            def dockerFile = testProjectDir.resolve("build-context/${name}").toFile()
            dockerFile << """
                FROM alpine:latest
                RUN echo "Image built with ${name}"
                LABEL dockerfile-name=${name}
            """
        }
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    prodImage {
                        context = file('build-context')
                        dockerfileName = 'Dockerfile.prod'
                        tags = ['prod:latest']
                    }
                    customImage {
                        context = file('build-context')  
                        dockerfileName = 'MyDockerfile'
                        tags = ['custom:latest']
                    }
                    appImage {
                        context = file('build-context')
                        dockerfileName = 'app.dockerfile'
                        tags = ['app:latest']
                    }
                    alpineImage {
                        context = file('build-context')
                        dockerfileName = 'Dockerfile-alpine'
                        tags = ['alpine:latest']
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('dockerBuildProdImage', 'dockerBuildCustomImage', 'dockerBuildAppImage', 'dockerBuildAlpineImage', '--info')
            .build()

        then:
        result.task(':dockerBuildProdImage').outcome == TaskOutcome.SUCCESS
        result.task(':dockerBuildCustomImage').outcome == TaskOutcome.SUCCESS
        result.task(':dockerBuildAppImage').outcome == TaskOutcome.SUCCESS
        result.task(':dockerBuildAlpineImage').outcome == TaskOutcome.SUCCESS
        result.output.contains('Building Docker image')
    }
    */
}