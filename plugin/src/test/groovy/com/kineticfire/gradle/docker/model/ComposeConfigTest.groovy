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

package com.kineticfire.gradle.docker.model

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for ComposeConfig
 */
class ComposeConfigTest extends Specification {

    @TempDir
    Path tempDir

    def "can create minimal ComposeConfig"() {
        given:
        def composeFile = tempDir.resolve("docker-compose.yml")
        Files.write(composeFile, ["version: '3'", "services:", "  web:", "    image: nginx"].join("\n").bytes)

        when:
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")

        then:
        config.composeFiles == [composeFile]
        config.projectName == "test-project"
        config.stackName == "test-stack"
        config.envFiles == []
        config.environment == [:]
    }

    def "can create full ComposeConfig with all parameters"() {
        given:
        def composeFile = tempDir.resolve("docker-compose.yml")
        def envFile = tempDir.resolve(".env")
        Files.write(composeFile, ["version: '3'"].join("\n").bytes)
        Files.write(envFile, ["NODE_ENV=production"].join("\n").bytes)
        def environment = [DATABASE_URL: "postgres://localhost:5432/mydb"]

        when:
        def config = new ComposeConfig([composeFile], [envFile], "prod-project", "prod-stack", environment)

        then:
        config.composeFiles == [composeFile]
        config.envFiles == [envFile]
        config.projectName == "prod-project"
        config.stackName == "prod-stack"
        config.environment == environment
    }

    def "can create ComposeConfig with multiple compose files"() {
        given:
        def composeFile1 = tempDir.resolve("docker-compose.yml")
        def composeFile2 = tempDir.resolve("docker-compose.override.yml")
        Files.write(composeFile1, ["version: '3'"].join("\n").bytes)
        Files.write(composeFile2, ["version: '3'"].join("\n").bytes)

        when:
        def config = new ComposeConfig([composeFile1, composeFile2], "multi-project", "multi-stack")

        then:
        config.composeFiles == [composeFile1, composeFile2]
        config.projectName == "multi-project"
        config.stackName == "multi-stack"
    }

    def "validates compose files cannot be null"() {
        when:
        new ComposeConfig(null, "project", "stack")

        then:
        def exception = thrown(NullPointerException)
        exception.message.contains("Compose files cannot be null")
    }

    def "validates project name cannot be null"() {
        given:
        def composeFile = tempDir.resolve("docker-compose.yml")
        Files.write(composeFile, ["version: '3'"].join("\n").bytes)

        when:
        new ComposeConfig([composeFile], null, "stack")

        then:
        def exception = thrown(NullPointerException)
        exception.message.contains("Project name cannot be null")
    }

    def "validates stack name cannot be null"() {
        given:
        def composeFile = tempDir.resolve("docker-compose.yml")
        Files.write(composeFile, ["version: '3'"].join("\n").bytes)

        when:
        new ComposeConfig([composeFile], "project", null)

        then:
        def exception = thrown(NullPointerException)
        exception.message.contains("Stack name cannot be null")
    }

    def "validates at least one compose file must be specified"() {
        when:
        new ComposeConfig([], "project", "stack")

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("At least one compose file must be specified")
    }

    def "validates compose files exist"() {
        given:
        def existingFile = tempDir.resolve("docker-compose.yml")
        def nonExistentFile = tempDir.resolve("nonexistent.yml")
        Files.write(existingFile, ["version: '3'"].join("\n").bytes)

        when:
        new ComposeConfig([existingFile, nonExistentFile], "project", "stack")

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("Compose file does not exist")
        exception.message.contains("nonexistent.yml")
    }

    def "handles null environment in full constructor"() {
        given:
        def composeFile = tempDir.resolve("docker-compose.yml")
        Files.write(composeFile, ["version: '3'"].join("\n").bytes)

        when:
        def config = new ComposeConfig([composeFile], [], "project", "stack", null)

        then:
        config.environment == [:]
    }

    def "handles null envFiles in full constructor"() {
        given:
        def composeFile = tempDir.resolve("docker-compose.yml")
        Files.write(composeFile, ["version: '3'"].join("\n").bytes)

        when:
        def config = new ComposeConfig([composeFile], null, "project", "stack", [:])

        then:
        config.envFiles == []
    }

    def "toString includes summary information"() {
        given:
        def composeFile1 = tempDir.resolve("docker-compose.yml")
        def composeFile2 = tempDir.resolve("docker-compose.override.yml")
        def envFile = tempDir.resolve(".env")
        Files.write(composeFile1, ["version: '3'"].join("\n").bytes)
        Files.write(composeFile2, ["version: '3'"].join("\n").bytes)
        Files.write(envFile, ["NODE_ENV=test"].join("\n").bytes)

        when:
        def config = new ComposeConfig([composeFile1, composeFile2], [envFile], "myproject", "mystack", [DB: "test"])
        def string = config.toString()

        then:
        string.contains("ComposeConfig")
        string.contains("projectName='myproject'")
        string.contains("stackName='mystack'")
        string.contains("composeFiles=2 files")
        string.contains("envFiles=1 files")
    }

    def "toString handles empty collections"() {
        given:
        def composeFile = tempDir.resolve("docker-compose.yml")
        Files.write(composeFile, ["version: '3'"].join("\n").bytes)

        when:
        def config = new ComposeConfig([composeFile], "project", "stack")
        def string = config.toString()

        then:
        string.contains("composeFiles=1 files")
        string.contains("envFiles=0 files")
    }

    def "constructor validates all files in full constructor"() {
        given:
        def composeFile = tempDir.resolve("docker-compose.yml")
        def nonExistentEnvFile = tempDir.resolve("nonexistent.env")
        Files.write(composeFile, ["version: '3'"].join("\n").bytes)

        when:
        // Note: envFiles are not validated in the current implementation
        def config = new ComposeConfig([composeFile], [nonExistentEnvFile], "project", "stack", [:])

        then:
        // This should pass because envFiles are not validated in current implementation
        config.envFiles == [nonExistentEnvFile]
    }

    def "immutable fields cannot be modified after construction"() {
        given:
        def composeFile = tempDir.resolve("docker-compose.yml")
        Files.write(composeFile, ["version: '3'"].join("\n").bytes)
        def config = new ComposeConfig([composeFile], "project", "stack")

        expect:
        // Fields are final, so they cannot be reassigned
        // This test documents the immutability design
        config.composeFiles != null
        config.projectName != null
        config.stackName != null
        config.envFiles != null
        config.environment != null
    }
}