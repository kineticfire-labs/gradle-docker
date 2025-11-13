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

package com.kineticfire.gradle.docker.util

import spock.lang.Specification
import spock.lang.Unroll
import java.nio.file.Paths

/**
 * Comprehensive tests for DockerfilePathResolver utility
 * Achieves 100% code and branch coverage
 */
class DockerfilePathResolverTest extends Specification {

    // validateDockerfileLocation tests

    def "validateDockerfileLocation allows Dockerfile at context root"() {
        given:
        def context = Paths.get("/home/user/project")
        def dockerfile = Paths.get("/home/user/project/Dockerfile")

        when:
        DockerfilePathResolver.validateDockerfileLocation(context, dockerfile)

        then:
        noExceptionThrown()
    }

    def "validateDockerfileLocation allows Dockerfile in subdirectory"() {
        given:
        def context = Paths.get("/home/user/project")
        def dockerfile = Paths.get("/home/user/project/docker/Dockerfile")

        when:
        DockerfilePathResolver.validateDockerfileLocation(context, dockerfile)

        then:
        noExceptionThrown()
    }

    def "validateDockerfileLocation allows Dockerfile in deep subdirectory"() {
        given:
        def context = Paths.get("/home/user/project")
        def dockerfile = Paths.get("/home/user/project/src/main/docker/Dockerfile")

        when:
        DockerfilePathResolver.validateDockerfileLocation(context, dockerfile)

        then:
        noExceptionThrown()
    }

    def "validateDockerfileLocation rejects Dockerfile outside context"() {
        given:
        def context = Paths.get("/home/user/project")
        def dockerfile = Paths.get("/home/user/other/Dockerfile")

        when:
        DockerfilePathResolver.validateDockerfileLocation(context, dockerfile)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Dockerfile must be within the build context directory")
        e.message.contains("/home/user/other/Dockerfile")
        e.message.contains("/home/user/project")
    }

    def "validateDockerfileLocation rejects Dockerfile in parent directory"() {
        given:
        def context = Paths.get("/home/user/project/subdir")
        def dockerfile = Paths.get("/home/user/project/Dockerfile")

        when:
        DockerfilePathResolver.validateDockerfileLocation(context, dockerfile)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Dockerfile must be within the build context directory")
    }

    def "validateDockerfileLocation handles relative paths correctly"() {
        given:
        def context = Paths.get("project")
        def dockerfile = Paths.get("project/Dockerfile")

        when:
        DockerfilePathResolver.validateDockerfileLocation(context, dockerfile)

        then:
        noExceptionThrown()
    }

    /**
     * SKIPPED on non-Windows platforms: This test validates Windows-specific path handling
     *
     * Reason: This test verifies that DockerfilePathResolver correctly handles Windows-style paths
     * (e.g., C:\Users\user\project) which cannot be tested on Unix/Linux systems
     *
     * Platform: Only runs on Windows OS
     * Skip Condition: Skipped when os.name does not contain 'windows'
     *
     * Alternative Coverage: Unix/Linux path handling is tested by other tests in this file:
     * - validateDockerfileLocation succeeds for valid paths
     * - validateDockerfileLocation throws exception when Dockerfile outside context
     * - validateDockerfileLocation throws exception when Dockerfile at parent level
     *
     * See: docs/design-docs/testing/unit-testing-strategy.md for full documentation
     */
    @spock.lang.IgnoreIf({ !System.getProperty('os.name').toLowerCase().contains('windows') })
    def "validateDockerfileLocation handles Windows-style paths"() {
        given:
        def context = Paths.get("C:\\Users\\user\\project")
        def dockerfile = Paths.get("C:\\Users\\user\\project\\Dockerfile")

        when:
        DockerfilePathResolver.validateDockerfileLocation(context, dockerfile)

        then:
        noExceptionThrown()
    }

    // calculateRelativePath tests

    def "calculateRelativePath returns filename when Dockerfile at context root"() {
        given:
        def context = Paths.get("/home/user/project")
        def dockerfile = Paths.get("/home/user/project/Dockerfile")

        when:
        def relative = DockerfilePathResolver.calculateRelativePath(context, dockerfile)

        then:
        relative == "Dockerfile"
    }

    def "calculateRelativePath returns subdirectory path"() {
        given:
        def context = Paths.get("/home/user/project")
        def dockerfile = Paths.get("/home/user/project/docker/Dockerfile")

        when:
        def relative = DockerfilePathResolver.calculateRelativePath(context, dockerfile)

        then:
        relative == "docker${File.separator}Dockerfile" || relative == "docker/Dockerfile"
    }

    def "calculateRelativePath returns deep subdirectory path"() {
        given:
        def context = Paths.get("/home/user/project")
        def dockerfile = Paths.get("/home/user/project/src/main/docker/Dockerfile")

        when:
        def relative = DockerfilePathResolver.calculateRelativePath(context, dockerfile)

        then:
        relative.contains("src")
        relative.contains("main")
        relative.contains("docker")
        relative.contains("Dockerfile")
    }

    def "calculateRelativePath handles relative paths"() {
        given:
        def context = Paths.get("project")
        def dockerfile = Paths.get("project/docker/Dockerfile")

        when:
        def relative = DockerfilePathResolver.calculateRelativePath(context, dockerfile)

        then:
        relative.contains("docker")
        relative.contains("Dockerfile")
    }

    def "calculateRelativePath handles same directory"() {
        given:
        def context = Paths.get("/home/user/project")
        def dockerfile = Paths.get("/home/user/project/Dockerfile.custom")

        when:
        def relative = DockerfilePathResolver.calculateRelativePath(context, dockerfile)

        then:
        relative == "Dockerfile.custom"
    }

    // needsTemporaryDockerfile tests

    def "needsTemporaryDockerfile returns false for root-level Dockerfile"() {
        when:
        def result = DockerfilePathResolver.needsTemporaryDockerfile("Dockerfile")

        then:
        !result
    }

    def "needsTemporaryDockerfile returns true for Dockerfile in subdirectory (Unix separator)"() {
        when:
        def result = DockerfilePathResolver.needsTemporaryDockerfile("docker/Dockerfile")

        then:
        result
    }

    def "needsTemporaryDockerfile returns true for Dockerfile in subdirectory (Windows separator)"() {
        when:
        def result = DockerfilePathResolver.needsTemporaryDockerfile("docker\\Dockerfile")

        then:
        result
    }

    def "needsTemporaryDockerfile returns true for deep subdirectory (Unix)"() {
        when:
        def result = DockerfilePathResolver.needsTemporaryDockerfile("src/main/docker/Dockerfile")

        then:
        result
    }

    def "needsTemporaryDockerfile returns true for deep subdirectory (Windows)"() {
        when:
        def result = DockerfilePathResolver.needsTemporaryDockerfile("src\\main\\docker\\Dockerfile")

        then:
        result
    }

    def "needsTemporaryDockerfile returns false for alternative filename at root"() {
        when:
        def result = DockerfilePathResolver.needsTemporaryDockerfile("Dockerfile.custom")

        then:
        !result
    }

    def "needsTemporaryDockerfile returns true for mixed separators"() {
        when:
        def result = DockerfilePathResolver.needsTemporaryDockerfile("docker/subdir\\Dockerfile")

        then:
        result
    }

    // generateTempDockerfileName tests

    def "generateTempDockerfileName returns unique name with timestamp"() {
        when:
        def name1 = DockerfilePathResolver.generateTempDockerfileName()
        Thread.sleep(2) // Ensure different timestamp
        def name2 = DockerfilePathResolver.generateTempDockerfileName()

        then:
        name1.startsWith("Dockerfile.tmp.")
        name2.startsWith("Dockerfile.tmp.")
        name1 != name2  // Different timestamps
    }

    def "generateTempDockerfileName follows expected pattern"() {
        when:
        def name = DockerfilePathResolver.generateTempDockerfileName()

        then:
        name.matches(/Dockerfile\.tmp\.\d+/)
    }

    // Integration-style tests

    @Unroll
    def "complete workflow for scenario: #description"() {
        given:
        def context = Paths.get(contextPath)
        def dockerfile = Paths.get(dockerfilePath)

        when:
        DockerfilePathResolver.validateDockerfileLocation(context, dockerfile)
        def relative = DockerfilePathResolver.calculateRelativePath(context, dockerfile)
        def needsTemp = DockerfilePathResolver.needsTemporaryDockerfile(relative)

        then:
        needsTemp == expectedNeedsTemp

        where:
        description                   | contextPath           | dockerfilePath                         || expectedNeedsTemp
        "root Dockerfile"             | "/project"            | "/project/Dockerfile"                  || false
        "subdirectory Dockerfile"     | "/project"            | "/project/docker/Dockerfile"           || true
        "deep subdirectory"           | "/project"            | "/project/src/main/docker/Dockerfile"  || true
        "custom name at root"         | "/project"            | "/project/Dockerfile.custom"           || false
    }

    def "validation failure prevents further processing"() {
        given:
        def context = Paths.get("/project")
        def dockerfile = Paths.get("/other/Dockerfile")

        when:
        DockerfilePathResolver.validateDockerfileLocation(context, dockerfile)
        def relative = DockerfilePathResolver.calculateRelativePath(context, dockerfile)

        then:
        thrown(IllegalArgumentException)
    }
}
