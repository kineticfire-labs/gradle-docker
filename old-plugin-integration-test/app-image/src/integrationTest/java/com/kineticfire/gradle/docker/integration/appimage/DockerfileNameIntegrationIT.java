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

import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for dockerfileName feature using external Docker CLI verification.
 * 
 * Purpose: Test dockerfileName functionality by building images with custom dockerfile names
 * and verifying the results through Docker CLI commands and filesystem checks.
 * 
 * Tests: Custom dockerfile names with context directory, contextTask, and various naming patterns
 * Coverage: Validates GradleDockerPlugin dockerfile resolution and DockerServiceImpl operations
 */
class DockerfileNameIntegrationIT {
    
    private static final String PROD_IMAGE_NAME = "dockerfile-name-prod-test";
    private static final String DEV_IMAGE_NAME = "dockerfile-name-dev-test";
    private static final String CUSTOM_IMAGE_NAME = "dockerfile-name-custom-test";
    private static final String TEST_VERSION = "1.0.0";
    
    @BeforeAll
    static void setupCustomDockerfiles() throws IOException {
        // Create custom dockerfile directory structure for testing
        Path testDir = Paths.get("src/test/docker");
        Files.createDirectories(testDir);
        
        // Create Dockerfile.prod
        Path prodDockerfile = testDir.resolve("Dockerfile.prod");
        Files.write(prodDockerfile, """
            FROM eclipse-temurin:21-jre
            LABEL environment=production
            LABEL dockerfile-name=Dockerfile.prod
            ARG JAR_FILE
            COPY ${JAR_FILE} app.jar
            WORKDIR /app
            EXPOSE 8080
            CMD ["java", "-jar", "app.jar"]
            """.getBytes());
        
        // Create Dockerfile.dev  
        Path devDockerfile = testDir.resolve("Dockerfile.dev");
        Files.write(devDockerfile, """
            FROM eclipse-temurin:21-jre
            LABEL environment=development
            LABEL dockerfile-name=Dockerfile.dev
            ARG JAR_FILE
            COPY ${JAR_FILE} app.jar
            WORKDIR /app
            EXPOSE 8080
            CMD ["java", "-Xdebug", "-jar", "app.jar"]
            """.getBytes());
        
        // Create MyCustomDockerfile
        Path customDockerfile = testDir.resolve("MyCustomDockerfile");
        Files.write(customDockerfile, """
            FROM eclipse-temurin:21-jre
            LABEL environment=custom
            LABEL dockerfile-name=MyCustomDockerfile
            ARG JAR_FILE
            COPY ${JAR_FILE} app.jar
            WORKDIR /app
            EXPOSE 8080
            CMD ["java", "-jar", "app.jar"]
            """.getBytes());
    }
    
    @Test
    @DisplayName("Docker image is built using dockerfileName with context directory")
    void dockerImageIsBuiltWithDockerfileNameAndContext() throws Exception {
        // Purpose: Verify that dockerfileName property correctly resolves custom dockerfile 
        // within a context directory and builds the image successfully
        
        String imageTag = PROD_IMAGE_NAME + ":" + TEST_VERSION;
        
        // Check if the image exists with the expected tag
        ProcessBuilder pb = new ProcessBuilder("docker", "images", PROD_IMAGE_NAME, "--format", "{{.Repository}}:{{.Tag}}");
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted)
            .as("Docker images command should complete")
            .isTrue();
        
        String output = readProcessOutput(process);
        
        assertThat(output)
            .as("Built image should be present with correct tag")
            .contains(imageTag);
    }
    
    @Test
    @DisplayName("Docker image built with dockerfileName has correct labels")
    void dockerImageBuiltWithDockerfileNameHasCorrectLabels() throws Exception {
        // Purpose: Verify that the correct dockerfile was used by checking build-time labels
        
        String imageTag = PROD_IMAGE_NAME + ":" + TEST_VERSION;
        
        ProcessBuilder pb = new ProcessBuilder("docker", "inspect", imageTag, "--format", "{{.Config.Labels}}");
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted).isTrue();
        
        String output = readProcessOutput(process);
        
        assertThat(output)
            .as("Image should have environment label from production dockerfile")
            .contains("environment:production");
            
        assertThat(output)
            .as("Image should have dockerfile-name label identifying which dockerfile was used")
            .contains("dockerfile-name:Dockerfile.prod");
    }
    
    @Test
    @DisplayName("Docker image is built using dockerfileName with contextTask")
    void dockerImageIsBuiltWithDockerfileNameAndContextTask() throws Exception {
        // Purpose: Verify that dockerfileName works correctly with contextTask (prepared build context)
        
        String imageTag = DEV_IMAGE_NAME + ":" + TEST_VERSION;
        
        // Check if the image exists
        ProcessBuilder pb = new ProcessBuilder("docker", "images", DEV_IMAGE_NAME, "--format", "{{.Repository}}:{{.Tag}}");
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted).isTrue();
        
        String output = readProcessOutput(process);
        
        assertThat(output)
            .as("Dev image built with contextTask should exist")
            .contains(imageTag);
            
        // Verify it has dev-specific labels
        ProcessBuilder inspectPb = new ProcessBuilder("docker", "inspect", imageTag, "--format", "{{.Config.Labels}}");
        Process inspectProcess = inspectPb.start();
        inspectProcess.waitFor(30, TimeUnit.SECONDS);
        
        String inspectOutput = readProcessOutput(inspectProcess);
        
        assertThat(inspectOutput)
            .as("Dev image should have development environment label")
            .contains("environment:development")
            .contains("dockerfile-name:Dockerfile.dev");
    }
    
    @Test
    @DisplayName("Docker image is built with custom non-standard dockerfile name")
    void dockerImageIsBuiltWithCustomDockerfileName() throws Exception {
        // Purpose: Verify that dockerfileName supports non-standard naming patterns
        
        String imageTag = CUSTOM_IMAGE_NAME + ":" + TEST_VERSION;
        
        // Check if the image exists
        ProcessBuilder pb = new ProcessBuilder("docker", "images", CUSTOM_IMAGE_NAME, "--format", "{{.Repository}}:{{.Tag}}");
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted).isTrue();
        
        String output = readProcessOutput(process);
        
        assertThat(output)
            .as("Custom image with non-standard dockerfile name should exist")
            .contains(imageTag);
            
        // Verify it used the correct dockerfile
        ProcessBuilder inspectPb = new ProcessBuilder("docker", "inspect", imageTag, "--format", "{{.Config.Labels}}");
        Process inspectProcess = inspectPb.start();
        inspectProcess.waitFor(30, TimeUnit.SECONDS);
        
        String inspectOutput = readProcessOutput(inspectProcess);
        
        assertThat(inspectOutput)
            .as("Custom image should have custom environment label")
            .contains("environment:custom")
            .contains("dockerfile-name:MyCustomDockerfile");
    }
    
    @Test
    @DisplayName("Built context contains dockerfile with custom name")
    void builtContextContainsDockerfileWithCustomName() throws Exception {
        // Purpose: Verify that when using contextTask, the dockerfile is copied to the build context
        // with the correct name specified by dockerfileName
        
        Path buildContextDir = Paths.get("build/docker-context/dockerfileNameDevTest");
        
        assertThat(buildContextDir)
            .as("Build context directory should exist")
            .exists();
            
        Path dockerfileInContext = buildContextDir.resolve("Dockerfile.dev");
        
        assertThat(dockerfileInContext)
            .as("Custom dockerfile should exist in build context with specified name")
            .exists();
            
        // Verify content is correct
        String dockerfileContent = Files.readString(dockerfileInContext);
        
        assertThat(dockerfileContent)
            .as("Dockerfile content should match development dockerfile")
            .contains("environment=development")
            .contains("dockerfile-name=Dockerfile.dev");
    }
    
    @Test  
    @DisplayName("Docker build respects dockerfile precedence rules")
    void dockerBuildRespectsDockerfilePrecedenceRules() throws Exception {
        // Purpose: Verify that when only dockerfileName is set (no explicit dockerfile),
        // the plugin correctly resolves the dockerfile path
        
        // This test verifies the build was successful by checking image existence
        // and that it used the intended dockerfile (via labels)
        
        String[] testImages = {
            PROD_IMAGE_NAME + ":" + TEST_VERSION,
            DEV_IMAGE_NAME + ":" + TEST_VERSION, 
            CUSTOM_IMAGE_NAME + ":" + TEST_VERSION
        };
        
        String[] expectedLabels = {
            "dockerfile-name:Dockerfile.prod",
            "dockerfile-name:Dockerfile.dev",
            "dockerfile-name:MyCustomDockerfile"
        };
        
        for (int i = 0; i < testImages.length; i++) {
            ProcessBuilder pb = new ProcessBuilder("docker", "inspect", testImages[i], "--format", "{{.Config.Labels}}");
            Process process = pb.start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            String output = readProcessOutput(process);
            
            assertThat(output)
                .as("Image %s should have been built using the correct dockerfile", testImages[i])
                .contains(expectedLabels[i]);
        }
    }
    
    @Test
    @DisplayName("Multiple images can use different dockerfileName values")
    void multipleImagesCanUseDifferentDockerfileNameValues() throws Exception {
        // Purpose: Verify that multiple images in the same project can each specify
        // different dockerfileName values and build correctly
        
        // Check that all test images exist
        String[] imageNames = {PROD_IMAGE_NAME, DEV_IMAGE_NAME, CUSTOM_IMAGE_NAME};
        
        for (String imageName : imageNames) {
            ProcessBuilder pb = new ProcessBuilder("docker", "images", imageName, "--format", "{{.Repository}}");
            Process process = pb.start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            String output = readProcessOutput(process);
            
            assertThat(output.trim())
                .as("Image %s should exist in Docker registry", imageName)
                .isEqualTo(imageName);
        }
    }
    
    @AfterAll
    static void cleanupTestImages() throws Exception {
        // Clean up test images to avoid pollution
        String[] testImages = {
            PROD_IMAGE_NAME,
            DEV_IMAGE_NAME, 
            CUSTOM_IMAGE_NAME
        };
        
        for (String imageName : testImages) {
            try {
                ProcessBuilder pb = new ProcessBuilder("docker", "rmi", "-f", imageName + ":" + TEST_VERSION);
                Process process = pb.start();
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Ignore cleanup errors - they're not critical for test success
                System.out.println("Warning: Could not clean up image " + imageName + ": " + e.getMessage());
            }
        }
    }
    
    private static String readProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        }
    }
}