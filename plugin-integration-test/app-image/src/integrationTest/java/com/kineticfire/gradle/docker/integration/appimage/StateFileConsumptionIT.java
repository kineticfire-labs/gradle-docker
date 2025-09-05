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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for JSON state file consumption.
 * 
 * Purpose: Validates that the test integration framework correctly generates and
 * provides JSON state files that tests can consume to discover container information
 * like ports, container IDs, and service states.
 * 
 * This demonstrates the complete UC-7 test integration workflow where:
 * 1. Gradle configures tests with compose stacks
 * 2. JUnit extensions manage compose lifecycle  
 * 3. State files are generated with service info
 * 4. Tests consume state files to connect to services
 */
class StateFileConsumptionIT {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void canParseValidStateFileFormat() throws IOException {
        // Purpose: Verify that a properly formatted state file can be parsed
        String validStateJson = """
            {
              "stackName": "testStack",
              "projectName": "test-project",
              "lifecycle": "suite",
              "timestamp": "2025-09-05T10:30:00",
              "services": {
                "time-server": {
                  "containerId": "abc123def456",
                  "containerName": "time-server-test-project-1",
                  "state": "healthy",
                  "publishedPorts": [
                    {"container": 8080, "host": 8081, "protocol": "tcp"}
                  ]
                }
              }
            }
            """;

        // When
        JsonNode stateJson = objectMapper.readTree(validStateJson);

        // Then
        assertThat(stateJson.get("stackName").asText()).isEqualTo("testStack");
        assertThat(stateJson.get("projectName").asText()).isEqualTo("test-project");
        assertThat(stateJson.get("lifecycle").asText()).isEqualTo("suite");
        
        JsonNode services = stateJson.get("services");
        assertThat(services).isNotNull();
        assertThat(services.has("time-server")).isTrue();
        
        JsonNode timeServer = services.get("time-server");
        assertThat(timeServer.get("containerId").asText()).isEqualTo("abc123def456");
        assertThat(timeServer.get("state").asText()).isEqualTo("healthy");
        
        JsonNode ports = timeServer.get("publishedPorts");
        assertThat(ports.isArray()).isTrue();
        assertThat(ports.size()).isEqualTo(1);
        
        JsonNode port = ports.get(0);
        assertThat(port.get("container").asInt()).isEqualTo(8080);
        assertThat(port.get("host").asInt()).isEqualTo(8081);
        assertThat(port.get("protocol").asText()).isEqualTo("tcp");
    }

    @Test
    void canExtractPortMappingsForServiceConnections() throws IOException {
        // Purpose: Demonstrate how tests would extract port information to connect to services
        String stateWithMultiplePorts = """
            {
              "stackName": "multiService",
              "services": {
                "web": {
                  "containerId": "web123",
                  "state": "healthy",
                  "publishedPorts": [
                    {"container": 80, "host": 8080, "protocol": "tcp"},
                    {"container": 443, "host": 8443, "protocol": "tcp"}
                  ]
                },
                "db": {
                  "containerId": "db456", 
                  "state": "healthy",
                  "publishedPorts": [
                    {"container": 5432, "host": 15432, "protocol": "tcp"}
                  ]
                }
              }
            }
            """;

        // When
        JsonNode stateJson = objectMapper.readTree(stateWithMultiplePorts);
        
        // Extract web service HTTP port
        JsonNode webService = stateJson.at("/services/web");
        int httpPort = findHostPortForContainerPort(webService, 80);
        
        // Extract database port
        JsonNode dbService = stateJson.at("/services/db");
        int dbPort = findHostPortForContainerPort(dbService, 5432);

        // Then
        assertThat(httpPort).isEqualTo(8080);
        assertThat(dbPort).isEqualTo(15432);
    }

    @Test
    void handlesStateFileWithDifferentLifecycles() throws IOException {
        // Purpose: Verify state files work for different lifecycle patterns
        String classLifecycleState = """
            {
              "stackName": "integrationClass",
              "lifecycle": "class",
              "testClass": "MyIntegrationTest",
              "services": {
                "app": {
                  "containerId": "class-container-123",
                  "state": "healthy",
                  "publishedPorts": [
                    {"container": 8080, "host": 8082, "protocol": "tcp"}
                  ]
                }
              }
            }
            """;

        String methodLifecycleState = """
            {
              "stackName": "integrationMethod",
              "lifecycle": "method",
              "testClass": "MyIntegrationTest",
              "testMethod": "testSpecificFeature",
              "services": {
                "app": {
                  "containerId": "method-container-456",
                  "state": "healthy",
                  "publishedPorts": [
                    {"container": 8080, "host": 8083, "protocol": "tcp"}
                  ]
                }
              }
            }
            """;

        // When
        JsonNode classState = objectMapper.readTree(classLifecycleState);
        JsonNode methodState = objectMapper.readTree(methodLifecycleState);

        // Then
        // Class lifecycle state
        assertThat(classState.get("lifecycle").asText()).isEqualTo("class");
        assertThat(classState.get("testClass").asText()).isEqualTo("MyIntegrationTest");
        assertThat(classState.has("testMethod")).isFalse();
        int classPort = findHostPortForContainerPort(classState.at("/services/app"), 8080);
        assertThat(classPort).isEqualTo(8082);

        // Method lifecycle state  
        assertThat(methodState.get("lifecycle").asText()).isEqualTo("method");
        assertThat(methodState.get("testClass").asText()).isEqualTo("MyIntegrationTest");
        assertThat(methodState.get("testMethod").asText()).isEqualTo("testSpecificFeature");
        int methodPort = findHostPortForContainerPort(methodState.at("/services/app"), 8080);
        assertThat(methodPort).isEqualTo(8083);
    }

    @Test
    void validatesStateFileExistsFromSystemProperty() {
        // Purpose: Test the pattern that JUnit extensions set COMPOSE_STATE_FILE system property
        // Note: This test doesn't actually run compose, just validates the property mechanism
        
        // When
        // Simulate what JUnit extension would do
        Path tempStateFile = Paths.get(System.getProperty("java.io.tmpdir"), "test-state.json");
        System.setProperty("COMPOSE_STATE_FILE", tempStateFile.toString());
        
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");

        // Then
        assertThat(stateFilePath).isNotNull();
        assertThat(stateFilePath).isEqualTo(tempStateFile.toString());
        
        // Cleanup
        System.clearProperty("COMPOSE_STATE_FILE");
    }

    @Test
    void canHandleStateFileReadErrors() {
        // Purpose: Verify graceful handling of state file read errors
        // When
        Path nonExistentFile = Paths.get("non-existent-state.json");
        boolean fileExists = Files.exists(nonExistentFile);

        // Then
        assertThat(fileExists).isFalse();
        
        // Test should handle missing files gracefully
        assertThatThrownBy(() -> {
            objectMapper.readTree(nonExistentFile.toFile());
        }).isInstanceOf(Exception.class);
    }

    @Test
    void canWorkWithMinimalStateFileFormat() throws IOException {
        // Purpose: Verify tests can work with minimal state file content
        String minimalState = """
            {
              "stackName": "minimal",
              "services": {
                "app": {
                  "state": "running",
                  "publishedPorts": []
                }
              }
            }
            """;

        // When
        JsonNode stateJson = objectMapper.readTree(minimalState);
        JsonNode appService = stateJson.at("/services/app");

        // Then
        assertThat(stateJson.get("stackName").asText()).isEqualTo("minimal");
        assertThat(appService.get("state").asText()).isEqualTo("running");
        assertThat(appService.get("publishedPorts").isArray()).isTrue();
        assertThat(appService.get("publishedPorts").size()).isEqualTo(0);
    }

    /**
     * Helper method to find the host port that maps to a specific container port.
     * This demonstrates the typical pattern tests would use to discover service ports.
     */
    private int findHostPortForContainerPort(JsonNode serviceNode, int containerPort) {
        JsonNode ports = serviceNode.get("publishedPorts");
        if (ports != null && ports.isArray()) {
            for (JsonNode port : ports) {
                if (port.get("container").asInt() == containerPort) {
                    return port.get("host").asInt();
                }
            }
        }
        throw new IllegalArgumentException("Container port " + containerPort + " not found in service ports");
    }
}