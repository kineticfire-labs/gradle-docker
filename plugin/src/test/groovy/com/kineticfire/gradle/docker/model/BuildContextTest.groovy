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

    // ===== EQUALS METHOD TESTS =====

    def "equals returns true for equal BuildContext objects"() {
        given:
        def contextPath = tempDir.resolve('app')
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createDirectories(contextPath)
        Files.createFile(dockerfile)

        def context1 = new BuildContext(contextPath, dockerfile, [VERSION: '1.0'], ['app:latest'])
        def context2 = new BuildContext(contextPath, dockerfile, [VERSION: '1.0'], ['app:latest'])

        expect:
        context1.equals(context2)
    }

    def "equals returns false for different context paths"() {
        given:
        def contextPath1 = tempDir.resolve('app1')
        def contextPath2 = tempDir.resolve('app2')
        def dockerfile1 = contextPath1.resolve('Dockerfile')
        def dockerfile2 = contextPath2.resolve('Dockerfile')
        Files.createDirectories(contextPath1)
        Files.createDirectories(contextPath2)
        Files.createFile(dockerfile1)
        Files.createFile(dockerfile2)

        def context1 = new BuildContext(contextPath1, dockerfile1, [VERSION: '1.0'], ['app:latest'])
        def context2 = new BuildContext(contextPath2, dockerfile2, [VERSION: '1.0'], ['app:latest'])

        expect:
        !context1.equals(context2)
    }

    def "equals returns false for different dockerfiles"() {
        given:
        def contextPath = tempDir
        def dockerfile1 = contextPath.resolve('Dockerfile')
        def dockerfile2 = contextPath.resolve('Dockerfile.alt')
        Files.createFile(dockerfile1)
        Files.createFile(dockerfile2)

        def context1 = new BuildContext(contextPath, dockerfile1, [:], ['app:latest'])
        def context2 = new BuildContext(contextPath, dockerfile2, [:], ['app:latest'])

        expect:
        !context1.equals(context2)
    }

    def "equals returns false for different build args"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        def context1 = new BuildContext(contextPath, dockerfile, [VERSION: '1.0'], ['app:latest'])
        def context2 = new BuildContext(contextPath, dockerfile, [VERSION: '2.0'], ['app:latest'])

        expect:
        !context1.equals(context2)
    }

    def "equals returns false for different tags"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        def context1 = new BuildContext(contextPath, dockerfile, [:], ['app:latest'])
        def context2 = new BuildContext(contextPath, dockerfile, [:], ['app:v1.0'])

        expect:
        !context1.equals(context2)
    }

    def "equals returns false for different labels"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        def context1 = new BuildContext(contextPath, dockerfile, [:], ['app:latest'], [version: '1.0'])
        def context2 = new BuildContext(contextPath, dockerfile, [:], ['app:latest'], [version: '2.0'])

        expect:
        !context1.equals(context2)
    }

    def "equals returns false for non-BuildContext object"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        def context = new BuildContext(contextPath, dockerfile, [:], ['app:latest'])

        expect:
        !context.equals("not a BuildContext")
        !context.equals(null)
    }

    def "hashCode returns same value for equal objects"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        def context1 = new BuildContext(contextPath, dockerfile, [VERSION: '1.0'], ['app:latest'])
        def context2 = new BuildContext(contextPath, dockerfile, [VERSION: '1.0'], ['app:latest'])

        expect:
        context1.hashCode() == context2.hashCode()
    }

    def "equals returns true for same object reference"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)
        def context = new BuildContext(contextPath, dockerfile, [:], ['app:latest'])

        expect:
        context.equals(context)
        context.is(context)
    }

    def "can create BuildContext with null buildArgs"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        when:
        def context = new BuildContext(contextPath, dockerfile, null, ['app:latest'])

        then:
        context.buildArgs == [:]
    }

    def "can create BuildContext with null tags"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        when:
        new BuildContext(contextPath, dockerfile, [:], null)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains('At least one tag must be specified')
    }

    def "four-parameter constructor handles null buildArgs"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        when:
        def context = new BuildContext(contextPath, dockerfile, null, ['app:latest'], [version: '1.0'])

        then:
        context.buildArgs == [:]
        context.labels == [version: '1.0']
    }

    def "four-parameter constructor handles null tags"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        when:
        new BuildContext(contextPath, dockerfile, [:], null, [version: '1.0'])

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains('At least one tag must be specified')
    }

    def "four-parameter constructor handles null labels"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)

        when:
        def context = new BuildContext(contextPath, dockerfile, [:], ['app:latest'], null)

        then:
        context.labels == [:]
    }

    def "four-parameter constructor validates contextPath cannot be null"() {
        given:
        def dockerfile = tempDir.resolve('Dockerfile')
        Files.createFile(dockerfile)

        when:
        new BuildContext(null, dockerfile, [:], ['test:latest'], [:])

        then:
        thrown(NullPointerException)
    }

    def "four-parameter constructor validates dockerfile cannot be null"() {
        given:
        def contextPath = tempDir

        when:
        new BuildContext(contextPath, null, [:], ['test:latest'], [:])

        then:
        thrown(NullPointerException)
    }

    def "toString includes labels information"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)
        def context = new BuildContext(contextPath, dockerfile, [:], ['app:latest'], [version: '1.0', env: 'prod'])

        when:
        def string = context.toString()

        then:
        string.contains('labels=2 labels')
    }

    def "toString handles empty labels"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)
        def context = new BuildContext(contextPath, dockerfile, [:], ['app:latest'], [:])

        when:
        def string = context.toString()

        then:
        string.contains('labels=0 labels')
    }

    def "equals with labels - equal labels"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)
        def context1 = new BuildContext(contextPath, dockerfile, [:], ['app:latest'], [version: '1.0'])
        def context2 = new BuildContext(contextPath, dockerfile, [:], ['app:latest'], [version: '1.0'])

        expect:
        context1.equals(context2)
        context1.hashCode() == context2.hashCode()
    }

    def "hashCode with different labels"() {
        given:
        def contextPath = tempDir
        def dockerfile = contextPath.resolve('Dockerfile')
        Files.createFile(dockerfile)
        def context1 = new BuildContext(contextPath, dockerfile, [:], ['app:latest'], [version: '1.0'])
        def context2 = new BuildContext(contextPath, dockerfile, [:], ['app:latest'], [version: '2.0'])

        expect:
        context1.hashCode() != context2.hashCode()
    }
}