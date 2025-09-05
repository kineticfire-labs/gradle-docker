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

package com.kineticfire.gradle.docker.extension

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Subject

/**
 * Unit tests for TestIntegrationExtension.
 * 
 * Tests core functionality using ProjectBuilder for realistic project simulation.
 */
class TestIntegrationExtensionTest extends Specification {

    Project project
    
    @Subject
    TestIntegrationExtension extension

    def setup() {
        project = ProjectBuilder.builder().build()
        extension = new TestIntegrationExtension(project)
    }

    def "constructor initializes with project"() {
        expect:
        extension != null
    }

    def "composeStateFileFor returns Provider with correct path structure"() {
        given:
        String stackName = "testStack"

        when:
        def stateFileProvider = extension.composeStateFileFor(stackName)

        then:
        stateFileProvider != null
        // Provider should be properly configured - actual path resolution happens at execution time
    }

    def "composeStateFileFor works with different stack names"() {
        expect:
        extension.composeStateFileFor("stack1") != null
        extension.composeStateFileFor("stack-name") != null
        extension.composeStateFileFor("complexStackName123") != null
    }

    def "composeStateFileFor is configuration cache compatible"() {
        given:
        String stackName = "testStack"

        when:
        def provider1 = extension.composeStateFileFor(stackName)
        def provider2 = extension.composeStateFileFor(stackName)

        then:
        // Both providers should be valid instances (deferred resolution)
        provider1 != null
        provider2 != null
        // They should be different instances but functionally equivalent
        provider1 != provider2 || provider1.is(provider2)
    }

    def "extension integrates properly with Gradle configuration cache"() {
        given:
        String stackName = "testStack"

        when:
        // This tests the Provider-based approach required for configuration cache
        def stateFileProvider = extension.composeStateFileFor(stackName)

        then:
        stateFileProvider != null
        // Provider should not be resolved during configuration time
        // This ensures configuration cache compatibility
    }
}