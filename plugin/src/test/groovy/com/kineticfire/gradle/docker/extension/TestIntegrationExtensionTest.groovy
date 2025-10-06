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
import spock.lang.Unroll

/**
 * Unit tests for TestIntegrationExtension.
 *
 * Tests core functionality using ProjectBuilder for realistic project simulation.
 * Note: usesCompose() method is tested in integration tests as it requires full plugin setup.
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
        def resolvedPath = stateFileProvider.get()

        then:
        stateFileProvider != null
        resolvedPath != null
        resolvedPath.contains("compose-state")
        resolvedPath.contains("${stackName}-state.json")
    }

    @Unroll
    def "composeStateFileFor works with stack name '#stackName'"() {
        when:
        def stateFileProvider = extension.composeStateFileFor(stackName)
        def resolvedPath = stateFileProvider.get()

        then:
        resolvedPath != null
        resolvedPath.contains("${stackName}-state.json")

        where:
        stackName << ["stack1", "stack-name", "complexStackName123", "test_stack", "UPPERCASE", "with123numbers"]
    }

    def "composeStateFileFor generates unique paths for different stacks"() {
        when:
        def path1 = extension.composeStateFileFor("stack1").get()
        def path2 = extension.composeStateFileFor("stack2").get()

        then:
        path1 != path2
        path1.contains("stack1-state.json")
        path2.contains("stack2-state.json")
    }

    def "composeStateFileFor path is under build directory"() {
        when:
        def stateFilePath = extension.composeStateFileFor("myStack").get()

        then:
        stateFilePath.contains(project.layout.buildDirectory.get().asFile.absolutePath)
    }

    def "composeStateFileFor path includes correct subdirectory"() {
        when:
        def stateFilePath = extension.composeStateFileFor("testStack").get()

        then:
        stateFilePath.contains("compose-state")
    }

    def "composeStateFileFor handles special characters in stack name"() {
        when:
        def stateFilePath = extension.composeStateFileFor("my-test_stack.123").get()

        then:
        stateFilePath.contains("my-test_stack.123-state.json")
    }

    def "multiple calls to composeStateFileFor return consistent paths"() {
        given:
        def stackName = "consistentStack"

        when:
        def path1 = extension.composeStateFileFor(stackName).get()
        def path2 = extension.composeStateFileFor(stackName).get()

        then:
        path1 == path2
    }
}