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

package com.kineticfire.gradle.docker.integration.appimage;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Plugin DSL and Gradle task integration.
 * 
 * Purpose: Test the plugin DSL configuration, task creation, and Gradle integration.
 * This validates that the plugin correctly integrates with Gradle builds and creates
 * the expected tasks with proper configuration.
 * 
 * Tests: docker{} DSL, dockerOrch{} DSL, task dependencies, multi-project builds
 * Coverage: Targets plugin integration and task execution paths
 */
class PluginDSLIntegrationIT {
    
    @TempDir
    Path testProjectDir;
    
    private Path buildFile;
    private Path settingsFile;
    
    @BeforeEach
    void setUp() throws IOException {
        buildFile = testProjectDir.resolve("build.gradle");
        settingsFile = testProjectDir.resolve("settings.gradle");
        
        // Create basic settings.gradle
        Files.write(settingsFile, "rootProject.name = 'plugin-dsl-test'".getBytes());
    }
    
    @Test
    @DisplayName("Plugin applies successfully with docker DSL block")
    void pluginAppliesSuccessfullyWithDockerDSL() throws IOException {
        // Purpose: Test basic plugin application and docker{} DSL parsing
        
        String buildScript = """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
            }
            
            docker {
                images {
                    testImage {
                        context = file('src/main/docker')
                        dockerfile = file('src/main/docker/Dockerfile')
                        tags = ['test:latest', 'test:1.0.0']
                        buildArgs = [
                            'BUILD_VERSION': '1.0.0'
                        ]
                    }
                }
            }
            """;
            
        Files.write(buildFile, buildScript.getBytes());
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("tasks", "--group=docker")
            .withPluginClasspath()
            .build();
            
        assertThat(result.getOutput())
            .contains("dockerBuildTestImage")
            .contains("Docker tasks");
            
        assertThat(result.task(":tasks").getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }
    
    @Test
    @DisplayName("Plugin creates correct docker build tasks")
    void pluginCreatesCorrectDockerBuildTasks() throws IOException {
        // Purpose: Test docker build task creation and configuration
        
        createDockerContext();
        
        String buildScript = """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
            }
            
            docker {
                images {
                    myApp {
                        context = file('docker')
                        dockerfile = file('docker/Dockerfile')
                        tags = ['my-app:test']
                        buildArgs = [
                            'BUILD_TIME': new Date().toString()
                        ]
                    }
                }
            }
            """;
            
        Files.write(buildFile, buildScript.getBytes());
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("dockerBuildMyApp", "--dry-run")
            .withPluginClasspath()
            .build();
            
        assertThat(result.getOutput())
            .contains("dockerBuildMyApp");
            
        assertThat(result.task(":dockerBuildMyApp").getOutcome())
            .isEqualTo(TaskOutcome.SKIPPED); // Dry run skips execution
    }
    
    @Test
    @DisplayName("Plugin supports dockerOrch DSL for compose orchestration")
    void pluginSupportsDockerOrchDSLForComposeOrchestration() throws IOException {
        // Purpose: Test dockerOrch{} DSL configuration and task creation
        
        createComposeFiles();
        
        String buildScript = """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
            }
            
            dockerOrch {
                stacks {
                    testStack {
                        composeFiles = [file('docker-compose.test.yml')]
                        projectName = 'dsl-test'
                        envFiles = [file('.env.test')]
                    }
                }
                
                tests {
                    integrationSuite {
                        stack = 'testStack'
                        lifecycle = 'suite'
                        testSourceSet = 'integrationTest'
                        services = ['web']
                        waitFor = 'healthy'
                        timeout = '60s'
                    }
                }
            }
            """;
            
        Files.write(buildFile, buildScript.getBytes());
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("tasks", "--group=docker-compose")
            .withPluginClasspath()
            .build();
            
        assertThat(result.getOutput())
            .contains("composeUpTestStack")
            .contains("composeDownTestStack")
            .contains("integrationSuite");
    }
    
    @Test
    @DisplayName("Plugin handles multi-image docker configuration")
    void pluginHandlesMultiImageDockerConfiguration() throws IOException {
        // Purpose: Test multiple image configuration in single build
        
        createMultiDockerContext();
        
        String buildScript = """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
            }
            
            docker {
                images {
                    frontend {
                        context = file('frontend-docker')
                        dockerfile = file('frontend-docker/Dockerfile')
                        tags = ['frontend:latest', 'frontend:dev']
                    }
                    
                    backend {
                        context = file('backend-docker')
                        dockerfile = file('backend-docker/Dockerfile')
                        tags = ['backend:latest', 'backend:dev']
                        buildArgs = [
                            'SERVICE_TYPE': 'backend'
                        ]
                    }
                }
            }
            """;
            
        Files.write(buildFile, buildScript.getBytes());
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build();
            
        assertThat(result.getOutput())
            .contains("dockerBuildFrontend")
            .contains("dockerBuildBackend");
    }
    
    @Test
    @DisplayName("Plugin supports complex compose stack configurations")
    void pluginSupportsComplexComposeStackConfigurations() throws IOException {
        // Purpose: Test complex multi-stack compose configurations
        
        createComplexComposeSetup();
        
        String buildScript = """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
            }
            
            dockerOrch {
                stacks {
                    development {
                        composeFiles = [
                            file('docker-compose.yml'),
                            file('docker-compose.dev.yml')
                        ]
                        projectName = 'myapp-dev'
                        envFiles = [file('.env.dev')]
                    }
                    
                    testing {
                        composeFiles = [
                            file('docker-compose.yml'),
                            file('docker-compose.test.yml')
                        ]
                        projectName = 'myapp-test'
                        envFiles = [file('.env.test')]
                    }
                }
                
                tests {
                    smokeTest {
                        stack = 'testing'
                        lifecycle = 'suite'
                        services = ['web', 'db']
                        waitFor = 'running'
                        timeout = '30s'
                    }
                    
                    fullIntegration {
                        stack = 'testing'
                        lifecycle = 'method'
                        services = ['web', 'db', 'redis']
                        waitFor = 'healthy'
                        timeout = '120s'
                    }
                }
            }
            """;
            
        Files.write(buildFile, buildScript.getBytes());
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("tasks")
            .withPluginClasspath()
            .build();
            
        // Verify multiple stack and test configurations are recognized
        assertThat(result.getOutput())
            .contains("composeUpDevelopment")
            .contains("composeUpTesting")
            .contains("smokeTest")
            .contains("fullIntegration");
    }
    
    @Test
    @DisplayName("Plugin task dependencies work correctly")
    void pluginTaskDependenciesWorkCorrectly() throws IOException {
        // Purpose: Test task dependency configuration and execution order
        
        createDockerContext();
        
        String buildScript = """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
            }
            
            docker {
                images {
                    app {
                        context = file('docker')
                        dockerfile = file('docker/Dockerfile')
                        tags = ['app:test']
                    }
                }
            }
            
            // Test explicit task dependencies
            tasks.named('dockerBuildApp') {
                dependsOn 'jar'
            }
            """;
            
        Files.write(buildFile, buildScript.getBytes());
        
        // Create minimal Java source to make jar task work
        createJavaSource();
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("dockerBuildApp", "--dry-run")
            .withPluginClasspath()
            .build();
            
        // Verify task dependencies are respected
        assertThat(result.getOutput())
            .contains("jar")
            .contains("dockerBuildApp");
    }
    
    @Test
    @DisplayName("Plugin handles build failures gracefully")
    void pluginHandlesBuildFailuresGracefully() throws IOException {
        // Purpose: Test error handling in plugin task execution
        
        String buildScript = """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
            }
            
            docker {
                images {
                    invalidImage {
                        context = file('nonexistent')
                        dockerfile = file('nonexistent/Dockerfile')
                        tags = ['invalid:test']
                    }
                }
            }
            """;
            
        Files.write(buildFile, buildScript.getBytes());
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("dockerBuildInvalidImage")
            .withPluginClasspath()
            .buildAndFail(); // Expect this to fail
            
        assertThat(result.task(":dockerBuildInvalidImage").getOutcome())
            .isEqualTo(TaskOutcome.FAILED);
            
        assertThat(result.getOutput())
            .contains("dockerBuildInvalidImage");
    }
    
    @Test
    @DisplayName("Plugin configuration validation works correctly")
    void pluginConfigurationValidationWorksCorrectly() throws IOException {
        // Purpose: Test DSL validation and error reporting
        
        String buildScript = """
            plugins {
                id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
            }
            
            docker {
                images {
                    testImage {
                        // Missing required context and dockerfile
                        tags = ['test:invalid']
                    }
                }
            }
            """;
            
        Files.write(buildFile, buildScript.getBytes());
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("dockerBuildTestImage")
            .withPluginClasspath()
            .buildAndFail();
            
        // Should fail with configuration error
        assertThat(result.task(":dockerBuildTestImage").getOutcome())
            .isEqualTo(TaskOutcome.FAILED);
    }
    
    // Helper Methods
    
    private void createDockerContext() throws IOException {
        Path dockerDir = testProjectDir.resolve("docker");
        Files.createDirectories(dockerDir);
        
        String dockerfile = """
            FROM alpine:latest
            RUN echo "Test image"
            CMD ["echo", "Hello from test image"]
            """;
            
        Files.write(dockerDir.resolve("Dockerfile"), dockerfile.getBytes());
    }
    
    private void createMultiDockerContext() throws IOException {
        // Frontend context
        Path frontendDir = testProjectDir.resolve("frontend-docker");
        Files.createDirectories(frontendDir);
        Files.write(frontendDir.resolve("Dockerfile"), "FROM nginx:alpine\nCOPY . /usr/share/nginx/html".getBytes());
        
        // Backend context
        Path backendDir = testProjectDir.resolve("backend-docker");
        Files.createDirectories(backendDir);
        Files.write(backendDir.resolve("Dockerfile"), "FROM openjdk:11-jre\nCOPY app.jar /app.jar\nCMD [\"java\", \"-jar\", \"/app.jar\"]".getBytes());
    }
    
    private void createComposeFiles() throws IOException {
        String composeContent = """
            version: '3.8'
            services:
              web:
                image: nginx:alpine
                ports:
                  - "8080:80"
                healthcheck:
                  test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost/"]
                  interval: 10s
                  timeout: 5s
                  retries: 3
            """;
            
        Files.write(testProjectDir.resolve("docker-compose.test.yml"), composeContent.getBytes());
        Files.write(testProjectDir.resolve(".env.test"), "TEST_ENV=integration".getBytes());
    }
    
    private void createComplexComposeSetup() throws IOException {
        // Base compose file
        String baseCompose = """
            version: '3.8'
            services:
              web:
                build: .
                ports:
                  - "8080:8080"
                depends_on:
                  - db
                  
              db:
                image: postgres:13
                environment:
                  POSTGRES_DB: myapp
                  POSTGRES_USER: user
                  POSTGRES_PASSWORD: pass
            """;
            
        // Development overlay
        String devCompose = """
            version: '3.8'
            services:
              web:
                environment:
                  - DEBUG=true
              redis:
                image: redis:alpine
            """;
            
        // Test overlay
        String testCompose = """
            version: '3.8'
            services:
              web:
                environment:
                  - TEST_MODE=true
                healthcheck:
                  test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
                  interval: 5s
                  timeout: 3s
                  retries: 5
              db:
                environment:
                  POSTGRES_DB: myapp_test
            """;
            
        Files.write(testProjectDir.resolve("docker-compose.yml"), baseCompose.getBytes());
        Files.write(testProjectDir.resolve("docker-compose.dev.yml"), devCompose.getBytes());
        Files.write(testProjectDir.resolve("docker-compose.test.yml"), testCompose.getBytes());
        Files.write(testProjectDir.resolve(".env.dev"), "ENV=development".getBytes());
        Files.write(testProjectDir.resolve(".env.test"), "ENV=test".getBytes());
    }
    
    private void createJavaSource() throws IOException {
        Path srcDir = testProjectDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        
        String javaSource = """
            public class TestApp {
                public static void main(String[] args) {
                    System.out.println("Test application");
                }
            }
            """;
            
        Files.write(srcDir.resolve("TestApp.java"), javaSource.getBytes());
    }
}