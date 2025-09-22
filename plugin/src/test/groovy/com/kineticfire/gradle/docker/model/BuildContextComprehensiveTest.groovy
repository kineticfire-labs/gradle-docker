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
import java.nio.file.Paths

/**
 * Comprehensive unit tests for BuildContext
 */
class BuildContextComprehensiveTest extends Specification {

    @TempDir
    Path tempDir

    // Helper method to create valid test context and dockerfile
    def createValidTestContext() {
        def contextPath = tempDir.resolve("context")
        Files.createDirectories(contextPath)
        def dockerfilePath = tempDir.resolve("Dockerfile")
        Files.createFile(dockerfilePath)
        return [contextPath, dockerfilePath]
    }

    def "constructor with all parameters"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()
        def buildArgs = ["ARG1": "value1", "ARG2": "value2"]
        def tags = ["registry.io/app:1.0", "registry.io/app:latest"]
        def labels = ["maintainer": "team", "version": "1.0"]

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, buildArgs, tags, labels)

        then:
        buildContext.contextPath == contextPath
        buildContext.dockerfile == dockerfilePath
        buildContext.buildArgs == buildArgs
        buildContext.tags == tags
        buildContext.labels == labels
    }

    def "constructor with minimal parameters"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, [:], ["minimal:latest"], [:])

        then:
        buildContext.contextPath == contextPath
        buildContext.dockerfile == dockerfilePath
        buildContext.buildArgs.isEmpty()
        buildContext.tags == ["minimal:latest"]
        buildContext.labels.isEmpty()
    }

    def "constructor with null collections defaults to empty"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, null, ["test:latest"], null)

        then:
        buildContext.contextPath == contextPath
        buildContext.dockerfile == dockerfilePath
        buildContext.buildArgs.isEmpty()
        buildContext.tags == ["test:latest"]
        buildContext.labels.isEmpty()
    }

    def "constructor validates context path exists"() {
        given:
        def contextPath = tempDir.resolve("nonexistent")
        def dockerfilePath = tempDir.resolve("Dockerfile")
        Files.createFile(dockerfilePath)

        when:
        new BuildContext(contextPath, dockerfilePath, [:], ["test:latest"], [:])

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor validates dockerfile exists"() {
        given:
        def contextPath = tempDir.resolve("context")
        Files.createDirectories(contextPath)
        def dockerfilePath = tempDir.resolve("nonexistent-dockerfile")

        when:
        new BuildContext(contextPath, dockerfilePath, [:], ["test:latest"], [:])

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor validates at least one tag is specified"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()

        when:
        new BuildContext(contextPath, dockerfilePath, [:], [], [:])

        then:
        thrown(IllegalArgumentException)
    }

    def "buildArgs property handling"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()
        def buildArgs = [
            "STRING_ARG": "simple value",
            "NUMERIC_ARG": "123",
            "BOOLEAN_ARG": "true",
            "PATH_ARG": "/path/to/file",
            "URL_ARG": "https://example.com",
            "EMPTY_ARG": "",
            "COMPLEX_ARG": "value with spaces and symbols !@#\$"
        ]

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, buildArgs, ["test:latest"], [:])

        then:
        buildContext.buildArgs["STRING_ARG"] == "simple value"
        buildContext.buildArgs["NUMERIC_ARG"] == "123"
        buildContext.buildArgs["BOOLEAN_ARG"] == "true"
        buildContext.buildArgs["PATH_ARG"] == "/path/to/file"
        buildContext.buildArgs["URL_ARG"] == "https://example.com"
        buildContext.buildArgs["EMPTY_ARG"] == ""
        buildContext.buildArgs["COMPLEX_ARG"] == "value with spaces and symbols !@#\$"
    }

    def "tags property handling"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()
        def tags = [
            "simple:latest",
            "registry.io/namespace/app:1.0.0",
            "localhost:5000/app:dev",
            "complex-registry.example.com:8080/deep/namespace/app:feature-branch"
        ]

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, [:], tags, [:])

        then:
        buildContext.tags == tags
        buildContext.tags.size() == 4
    }

    def "labels property handling"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()
        def labels = [
            "org.opencontainers.image.title": "My Application",
            "org.opencontainers.image.version": "1.0.0",
            "maintainer": "development-team@company.com",
            "build.timestamp": "2023-01-01T00:00:00Z"
        ]

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, [:], ["test:latest"], labels)

        then:
        buildContext.labels == labels
        buildContext.labels.size() == 4
    }

    def "path handling with different path types"() {
        given:
        def contextPath = tempDir.resolve("context")
        Files.createDirectories(contextPath)
        def dockerfilePath = tempDir.resolve("Dockerfile")
        Files.createFile(dockerfilePath)

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, [:], ["test:latest"], [:])

        then:
        buildContext.contextPath == contextPath
        buildContext.dockerfile == dockerfilePath
    }

    def "dockerfile path handling"() {
        given:
        def contextPath = tempDir.resolve("context")
        Files.createDirectories(contextPath)
        def dockerfilePath = contextPath.resolve("custom.Dockerfile")
        Files.createFile(dockerfilePath)

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, [:], ["test:latest"], [:])

        then:
        buildContext.contextPath == contextPath
        buildContext.dockerfile == dockerfilePath
        dockerfilePath.fileName.toString() == "custom.Dockerfile"
    }

    def "complex build context scenario"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()
        def buildArgs = [
            "JAR_FILE": "app-\${version}.jar",
            "BASE_IMAGE": "openjdk:11-jre-slim",
            "MAINTAINER": "team@company.com"
        ]
        def tags = [
            "app:latest",
            "app:1.0.0",
            "registry.company.com/apps/myapp:latest",
            "registry.company.com/apps/myapp:1.0.0"
        ]
        def labels = [
            "org.opencontainers.image.source": "https://github.com/company/myapp",
            "org.opencontainers.image.revision": "abc123def456",
            "org.opencontainers.image.created": "2023-01-01T00:00:00Z"
        ]

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, buildArgs, tags, labels)

        then:
        buildContext.contextPath == contextPath
        buildContext.dockerfile == dockerfilePath
        buildContext.buildArgs.size() == 3
        buildContext.tags.size() == 4
        buildContext.labels.size() == 3
        buildContext.buildArgs["JAR_FILE"] == "app-\${version}.jar"
        buildContext.tags.contains("app:latest")
        buildContext.labels["org.opencontainers.image.source"] == "https://github.com/company/myapp"
    }

    def "equals and hashCode contract"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()
        def buildArgs = ["ARG": "value"]
        def tags = ["app:1.0"]
        def labels = ["label": "value"]

        when:
        def context1 = new BuildContext(contextPath, dockerfilePath, buildArgs, tags, labels)
        def context2 = new BuildContext(contextPath, dockerfilePath, buildArgs, tags, labels)
        def context3 = new BuildContext(contextPath, dockerfilePath, ["DIFFERENT": "arg"], tags, labels)

        then:
        context1 == context2
        context1.hashCode() == context2.hashCode()
        context1 != context3
    }

    def "toString contains relevant information"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()
        def buildArgs = ["ARG1": "value1", "ARG2": "value2"]
        def tags = ["app:1.0", "app:latest"]
        def labels = ["maintainer": "team"]

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, buildArgs, tags, labels)
        def toString = buildContext.toString()

        then:
        toString.contains("BuildContext")
        toString.contains(contextPath.toString())
        toString.contains(dockerfilePath.toString())
        toString.contains("2 args")
        toString.contains("1 labels")
    }

    def "immutability of fields"() {
        given:
        def (contextPath, dockerfilePath) = createValidTestContext()
        def buildArgs = ["ARG": "value"]
        def tags = ["app:1.0"]
        def labels = ["label": "value"]

        when:
        def buildContext = new BuildContext(contextPath, dockerfilePath, buildArgs, tags, labels)

        then:
        buildContext.contextPath == contextPath
        buildContext.dockerfile == dockerfilePath
        buildContext.buildArgs == buildArgs
        buildContext.tags == tags
        buildContext.labels == labels
    }
}