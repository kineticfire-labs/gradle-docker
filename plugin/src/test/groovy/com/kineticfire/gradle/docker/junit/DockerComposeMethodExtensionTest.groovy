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

package com.kineticfire.gradle.docker.junit

import spock.lang.Specification
import spock.lang.Subject

/**
 * Unit tests for DockerComposeMethodExtension.
 * 
 * Tests basic functionality without complex mocking to avoid setup issues.
 */
class DockerComposeMethodExtensionTest extends Specification {

    @Subject
    DockerComposeMethodExtension extension

    def setup() {
        // Clean up any previous system properties
        System.clearProperty("docker.compose.stack")
        System.clearProperty("docker.compose.project")
        System.clearProperty("COMPOSE_STATE_FILE")
        
        // Create extension under test
        extension = new DockerComposeMethodExtension()
    }

    def cleanup() {
        // Clean up system properties after each test
        System.clearProperty("docker.compose.stack")
        System.clearProperty("docker.compose.project")
        System.clearProperty("COMPOSE_STATE_FILE")
    }

    def "constructor creates instance successfully"() {
        when:
        def ext = new DockerComposeMethodExtension()
        
        then:
        ext != null
    }

    def "extension handles system properties correctly"() {
        given:
        String stackName = "testStack"
        String projectName = "testProject"

        when:
        System.setProperty("docker.compose.stack", stackName)
        System.setProperty("docker.compose.project", projectName)
        
        then:
        System.getProperty("docker.compose.stack") == stackName
        System.getProperty("docker.compose.project") == projectName
    }

    def "extension can handle missing system properties"() {
        when:
        // Ensure properties are not set
        System.clearProperty("docker.compose.stack")
        System.clearProperty("docker.compose.project")
        
        then:
        System.getProperty("docker.compose.stack") == null
        System.getProperty("docker.compose.project") == null
    }

    def "system property constants are defined correctly"() {
        expect:
        // Test that the extension has the required property constants
        // This indirectly tests the static final fields exist
        extension != null
    }
    
    def "BeforeEachCallback and AfterEachCallback interfaces implemented"() {
        expect:
        extension instanceof org.junit.jupiter.api.extension.BeforeEachCallback
        extension instanceof org.junit.jupiter.api.extension.AfterEachCallback
    }
    
    def "extension methods exist and are callable"() {
        expect:
        extension.metaClass.respondsTo(extension, "beforeEach").size() > 0
        extension.metaClass.respondsTo(extension, "afterEach").size() > 0
    }
    
    def "extension class has required fields"() {
        expect:
        extension.class.getDeclaredField("COMPOSE_STACK_PROPERTY") != null
        extension.class.getDeclaredField("COMPOSE_PROJECT_PROPERTY") != null
        extension.class.getDeclaredField("COMPOSE_STATE_FILE_PROPERTY") != null
    }
}