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
 * Unit tests for BuildContext
 */
class BuildContextTest extends Specification {

    @TempDir
    Path tempDir

    def "can create BuildContext with all properties"() {
        given:
        def contextPath = tempDir
        def dockerfile = tempDir.resolve('Dockerfile')
        def tags = ['latest', '1.0.0']
        def buildArgs = [VERSION: '1.0.0', BUILD_DATE: '2025-01-01']
        
        // Create required files
        Files.createFile(dockerfile)

        when:
        def context = new BuildContext(contextPath, dockerfile, buildArgs, tags)

        then:
        context.dockerfile == dockerfile
        context.contextPath == contextPath
        context.tags == tags
        context.buildArgs == buildArgs
    }

    def "can create BuildContext with minimal properties"() {
        given:
        def contextPath = tempDir.resolve('simple')
        def dockerfile = contextPath.resolve('Dockerfile')
        def tags = ['latest']
        
        Files.createDirectories(contextPath)
        Files.createFile(dockerfile)

        when:
        def context = new BuildContext(contextPath, dockerfile, [:], tags)

        then:
        context.dockerfile == dockerfile
        context.contextPath == contextPath
        context.tags == tags
        context.buildArgs == [:]
    }

    def "validates that context path exists"() {
        given:
        def nonExistentPath = tempDir.resolve('nonexistent')
        def dockerfile = tempDir.resolve('Dockerfile')
        Files.createFile(dockerfile)

        when:
        new BuildContext(nonExistentPath, dockerfile, [:], ['test:latest'])

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains('Context path does not exist')
    }

    def "validates that dockerfile exists"() {
        given:
        def contextPath = tempDir
        def nonExistentDockerfile = tempDir.resolve('nonexistent-Dockerfile')

        when:
        new BuildContext(contextPath, nonExistentDockerfile, [:], ['test:latest'])

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains('Dockerfile does not exist')
    }

    def "validates that at least one tag is provided"() {
        given:
        def contextPath = tempDir
        def dockerfile = tempDir.resolve('Dockerfile')
        Files.createFile(dockerfile)

        when:
        new BuildContext(contextPath, dockerfile, [:], [])

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains('At least one tag must be specified')
    }

    def "toString includes key information"() {
        given:
        def contextPath = tempDir.resolve('app')
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createDirectories(contextPath)
        Files.createFile(dockerfile)
        
        def context = new BuildContext(contextPath, dockerfile, [VERSION: '1.0'], ['app:latest'])

        when:
        def string = context.toString()

        then:
        string.contains('BuildContext')
        string.contains('Dockerfile')
        string.contains('[app:latest]')
        string.contains('1 args')
    }
}