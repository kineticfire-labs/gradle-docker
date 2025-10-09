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

package com.kineticfire.gradle.docker.service

import com.kineticfire.gradle.docker.model.BuildContext
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for DockerServiceImpl label functionality
 * Tests that BuildContext correctly accepts and stores labels
 */
class DockerServiceLabelsTest extends Specification {

    @TempDir
    Path tempDir

    def "BuildContext accepts and stores labels"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)
        def labels = [
            "org.opencontainers.image.version": "1.0.0",
            "maintainer": "team@example.com"
        ]

        when:
        def context = new BuildContext(
            tempDir,
            dockerfile,
            [:],
            ["test:latest"],
            labels
        )

        then:
        context.labels == labels
        context.labels.size() == 2
        context.labels["org.opencontainers.image.version"] == "1.0.0"
        context.labels["maintainer"] == "team@example.com"
    }

    def "BuildContext accepts empty labels map"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)

        when:
        def context = new BuildContext(
            tempDir,
            dockerfile,
            [:],
            ["test:latest"],
            [:]  // Empty labels
        )

        then:
        context.labels == [:]
        context.labels.isEmpty()
    }

    def "BuildContext handles null labels as empty map"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)

        when:
        def context = new BuildContext(
            tempDir,
            dockerfile,
            [:],
            ["test:latest"],
            null  // Null labels
        )

        then:
        context.labels == [:]
        context.labels.isEmpty()
    }

    def "BuildContext with labels equals another with same labels"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)
        def labels1 = ["version": "1.0.0", "maintainer": "team"]
        def labels2 = ["version": "1.0.0", "maintainer": "team"]

        when:
        def context1 = new BuildContext(tempDir, dockerfile, [:], ["test:latest"], labels1)
        def context2 = new BuildContext(tempDir, dockerfile, [:], ["test:latest"], labels2)

        then:
        context1 == context2
        context1.hashCode() == context2.hashCode()
    }

    def "BuildContext with different labels are not equal"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)
        def labels1 = ["version": "1.0.0"]
        def labels2 = ["version": "2.0.0"]

        when:
        def context1 = new BuildContext(tempDir, dockerfile, [:], ["test:latest"], labels1)
        def context2 = new BuildContext(tempDir, dockerfile, [:], ["test:latest"], labels2)

        then:
        context1 != context2
    }

    def "BuildContext toString includes label count"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)
        def labels = ["version": "1.0.0", "maintainer": "team", "custom": "value"]

        when:
        def context = new BuildContext(tempDir, dockerfile, [:], ["test:latest"], labels)

        then:
        context.toString().contains("labels=3 labels")
    }
}
