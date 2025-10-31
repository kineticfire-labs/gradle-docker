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
import com.kineticfire.gradle.docker.testutil.MockServiceBuilder
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for pure functions extracted from DockerServiceImpl.
 * These functions have no external dependencies and are 100% testable.
 */
class DockerServiceImplPureFunctionsTest extends Specification {

    // ========== selectPrimaryTag() Tests ==========

    def "selectPrimaryTag prefers non-latest tag"() {
        when:
        def result = DockerServiceImpl.selectPrimaryTag(['myapp:latest', 'myapp:1.0.0', 'myapp:dev'])

        then:
        result == 'myapp:1.0.0'  // First non-latest tag
    }

    def "selectPrimaryTag falls back to first tag when all are latest"() {
        when:
        def result = DockerServiceImpl.selectPrimaryTag(['myapp:latest', 'other:latest'])

        then:
        result == 'myapp:latest'
    }

    def "selectPrimaryTag returns single tag"() {
        when:
        def result = DockerServiceImpl.selectPrimaryTag(['myapp:1.0.0'])

        then:
        result == 'myapp:1.0.0'
    }

    def "selectPrimaryTag throws on null tag list"() {
        when:
        DockerServiceImpl.selectPrimaryTag(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "selectPrimaryTag throws on empty tag list"() {
        when:
        DockerServiceImpl.selectPrimaryTag([])

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    def "selectPrimaryTag handles edge case: #scenario"() {
        when:
        def result = DockerServiceImpl.selectPrimaryTag(tags)

        then:
        result == expected

        where:
        scenario                       | tags                                          || expected
        "single latest tag"            | ['myapp:latest']                              || 'myapp:latest'
        "single non-latest tag"        | ['myapp:1.0.0']                               || 'myapp:1.0.0'
        "multiple non-latest tags"     | ['myapp:1.0.0', 'myapp:2.0.0']                || 'myapp:1.0.0'
        "latest at end"                | ['myapp:1.0.0', 'myapp:latest']               || 'myapp:1.0.0'
        "latest in middle"             | ['myapp:1.0.0', 'myapp:latest', 'myapp:dev']  || 'myapp:1.0.0'
        "registry-qualified tags"      | ['docker.io/myapp:latest', 'docker.io/myapp:1.0.0'] || 'docker.io/myapp:1.0.0'
    }

    // ========== prepareBuildConfiguration() Tests ==========

    def "prepareBuildConfiguration creates valid configuration"() {
        given: "A temporary directory structure"
        def tempDir = Files.createTempDirectory("docker-test")
        def dockerfile = tempDir.resolve("Dockerfile")
        Files.writeString(dockerfile, "FROM alpine:3.18")

        and: "A build context"
        def context = new BuildContext(
            tempDir,                                   // contextPath
            dockerfile,                                // dockerfile
            [VERSION: '1.0.0'],                        // buildArgs
            ['myapp:1.0.0', 'myapp:latest'],           // tags
            [maintainer: 'team@example.com']           // labels
        )

        when:
        def config = DockerServiceImpl.prepareBuildConfiguration(context)

        then:
        config.contextFile == tempDir.toFile()
        config.dockerfileFile == dockerfile.toFile()
        config.needsTemporaryDockerfile == false
        config.primaryTag == 'myapp:1.0.0'
        config.additionalTags == ['myapp:latest']
        config.buildArgs == [VERSION: '1.0.0']
        config.labels == [maintainer: 'team@example.com']

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "prepareBuildConfiguration handles Dockerfile in subdirectory"() {
        given: "A temporary directory with subdirectory"
        def tempDir = Files.createTempDirectory("docker-test")
        def dockerDir = tempDir.resolve("docker")
        Files.createDirectories(dockerDir)
        def dockerfile = dockerDir.resolve("Dockerfile")
        Files.writeString(dockerfile, "FROM alpine:3.18")

        and: "A build context"
        def context = new BuildContext(
            tempDir,           // contextPath
            dockerfile,        // dockerfile
            [:],               // buildArgs
            ['myapp:dev'],     // tags
            [:]                // labels
        )

        when:
        def config = DockerServiceImpl.prepareBuildConfiguration(context)

        then:
        config.needsTemporaryDockerfile == true
        config.primaryTag == 'myapp:dev'
        config.additionalTags == []

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "prepareBuildConfiguration throws on null context"() {
        when:
        DockerServiceImpl.prepareBuildConfiguration(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "prepareBuildConfiguration validates Dockerfile outside context"() {
        given: "Two separate directories"
        def contextDir = Files.createTempDirectory("context")
        def otherDir = Files.createTempDirectory("other")
        def dockerfile = otherDir.resolve("Dockerfile")
        Files.writeString(dockerfile, "FROM alpine:3.18")

        and: "A build context with Dockerfile outside context"
        def context = new BuildContext(
            contextDir,          // contextPath
            dockerfile,          // dockerfile
            [:],                 // buildArgs
            ['myapp:1.0.0'],     // tags
            [:]                  // labels
        )

        when:
        DockerServiceImpl.prepareBuildConfiguration(context)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        contextDir.toFile().deleteDir()
        otherDir.toFile().deleteDir()
    }

    def "prepareBuildConfiguration handles empty build args and labels"() {
        given:
        def tempDir = Files.createTempDirectory("docker-test")
        def dockerfile = tempDir.resolve("Dockerfile")
        Files.writeString(dockerfile, "FROM alpine:3.18")

        def context = new BuildContext(
            tempDir,             // contextPath
            dockerfile,          // dockerfile
            [:],                 // buildArgs
            ['myapp:1.0.0'],     // tags
            [:]                  // labels
        )

        when:
        def config = DockerServiceImpl.prepareBuildConfiguration(context)

        then:
        config.buildArgs == [:]
        config.labels == [:]

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "prepareBuildConfiguration handles single tag"() {
        given:
        def tempDir = Files.createTempDirectory("docker-test")
        def dockerfile = tempDir.resolve("Dockerfile")
        Files.writeString(dockerfile, "FROM alpine:3.18")

        def context = new BuildContext(
            tempDir,          // contextPath
            dockerfile,       // dockerfile
            [:],              // buildArgs
            ['myapp:only'],   // tags
            [:]               // labels
        )

        when:
        def config = DockerServiceImpl.prepareBuildConfiguration(context)

        then:
        config.primaryTag == 'myapp:only'
        config.additionalTags == []

        cleanup:
        tempDir.toFile().deleteDir()
    }

    // ========== BuildConfiguration Tests ==========

    def "BuildConfiguration holds all data correctly"() {
        given:
        def contextFile = new File("/tmp/context")
        def dockerfileFile = new File("/tmp/context/Dockerfile")
        def buildArgs = [VERSION: '1.0.0', BUILD_DATE: '2025-01-29']
        def labels = [maintainer: 'team@example.com', version: '1.0.0']

        when:
        def config = new DockerServiceImpl.BuildConfiguration(
            contextFile,
            dockerfileFile,
            true,
            'myapp:1.0.0',
            ['myapp:latest', 'myapp:dev'],
            buildArgs,
            labels
        )

        then:
        config.contextFile == contextFile
        config.dockerfileFile == dockerfileFile
        config.needsTemporaryDockerfile == true
        config.primaryTag == 'myapp:1.0.0'
        config.additionalTags == ['myapp:latest', 'myapp:dev']
        config.buildArgs == buildArgs
        config.labels == labels
    }

    def "BuildConfiguration handles null collections as empty"() {
        given:
        def contextFile = new File("/tmp/context")
        def dockerfileFile = new File("/tmp/context/Dockerfile")

        when:
        def config = new DockerServiceImpl.BuildConfiguration(
            contextFile,
            dockerfileFile,
            false,
            'myapp:1.0.0',
            null,  // null additional tags
            null,  // null build args
            null   // null labels
        )

        then:
        config.additionalTags == []
        config.buildArgs == [:]
        config.labels == [:]
    }

}
