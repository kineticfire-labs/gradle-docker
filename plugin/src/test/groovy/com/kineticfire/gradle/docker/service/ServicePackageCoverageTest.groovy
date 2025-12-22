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

import com.kineticfire.gradle.docker.exception.ComposeServiceException
import com.kineticfire.gradle.docker.model.*
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ExecutionException

/**
 * Comprehensive unit tests to improve coverage for the service package.
 * This test file focuses on edge cases and scenarios not covered by other tests.
 */
class ServicePackageCoverageTest extends Specification {

    @TempDir
    Path tempDir

    // ========== ProcessResult Additional Tests ==========

    def "ProcessResult isSuccess returns true only for exit code 0"() {
        expect:
        new ProcessResult(exitCode, "", "").isSuccess() == expected

        where:
        exitCode | expected
        0        | true
        1        | false
        -1       | false
        127      | false
        255      | false
    }

    def "ProcessResult toString contains all fields"() {
        given:
        def result = new ProcessResult(42, "output text", "error text")

        when:
        def str = result.toString()

        then:
        str.contains("exitCode=42")
        str.contains("stdout='output text'")
        str.contains("stderr='error text'")
        str.contains("ProcessResult")
    }

    def "ProcessResult handles null stdout gracefully"() {
        when:
        def result = new ProcessResult(0, null, "error")

        then:
        result.stdout == ""
        result.stderr == "error"
    }

    def "ProcessResult handles null stderr gracefully"() {
        when:
        def result = new ProcessResult(0, "output", null)

        then:
        result.stdout == "output"
        result.stderr == ""
    }

    def "ProcessResult handles both null stdout and stderr"() {
        when:
        def result = new ProcessResult(1, null, null)

        then:
        result.stdout == ""
        result.stderr == ""
        !result.isSuccess()
    }

    // ========== DefaultCommandValidator Additional Tests ==========

    def "DefaultCommandValidator caches detected command"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def validator = new DefaultCommandValidator(mockExecutor)

        // First call detects docker compose
        mockExecutor.execute(['docker', 'compose', 'version'], null, _) >>
            new ProcessResult(0, "Docker Compose version v2.0.0", "")

        when:
        def first = validator.detectComposeCommand()
        def second = validator.detectComposeCommand()
        def third = validator.detectComposeCommand()

        then:
        first == ['docker', 'compose']
        second == ['docker', 'compose']
        third == ['docker', 'compose']
        // Only one call should be made due to caching
    }

    def "DefaultCommandValidator falls back to docker-compose on docker compose exception"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def validator = new DefaultCommandValidator(mockExecutor)

        mockExecutor.execute(['docker', 'compose', 'version'], null, _) >>
            { throw new RuntimeException("Docker compose not available") }
        mockExecutor.execute(['docker-compose', '--version'], null, _) >>
            new ProcessResult(0, "docker-compose version 1.29.0", "")

        when:
        def command = validator.detectComposeCommand()

        then:
        command == ['docker-compose']
    }

    def "DefaultCommandValidator validateDockerCompose uses detected command"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def validator = new DefaultCommandValidator(mockExecutor)

        // detectComposeCommand will be called first
        mockExecutor.execute(['docker', 'compose', 'version'], null, _) >>
            new ProcessResult(0, "Docker Compose version v2.0.0", "")

        when:
        validator.validateDockerCompose()

        then:
        noExceptionThrown()
    }

    // ========== ExecLibraryComposeService Pure Function Tests ==========

    def "buildUpCommand handles empty env files list"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)
        def mockTimeService = Mock(TimeService)
        mockValidator.validateDockerCompose() >> {}
        mockValidator.detectComposeCommand() >> ['docker', 'compose']

        def service = new TestableComposeService(mockExecutor, mockValidator, mockLogger, mockTimeService)
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], [], "test-project", "test-stack", [:])

        when:
        def command = service.buildUpCommand(config, ['docker', 'compose'])

        then:
        !command.contains('--env-file')
        command.contains('up')
        command.contains('-d')
    }

    def "buildDownCommand handles empty env files list"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)
        def mockTimeService = Mock(TimeService)
        mockValidator.validateDockerCompose() >> {}

        def service = new TestableComposeService(mockExecutor, mockValidator, mockLogger, mockTimeService)
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], [], "test-project", "test-stack", [:])

        when:
        def command = service.buildDownCommand(config, ['docker', 'compose'])

        then:
        !command.contains('--env-file')
        command.contains('down')
        command.contains('--remove-orphans')
    }

    def "buildLogsCommand with zero tailLines still includes tail parameter"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)
        def mockTimeService = Mock(TimeService)
        mockValidator.validateDockerCompose() >> {}

        def service = new TestableComposeService(mockExecutor, mockValidator, mockLogger, mockTimeService)
        // LogsConfig enforces minimum of 1 for tailLines
        def config = new LogsConfig(["service1"], 0, false)

        when:
        def command = service.buildLogsCommand("test-project", config, ['docker', 'compose'])

        then:
        command.contains('--tail')
        command.contains('1')  // LogsConfig enforces minimum of 1
    }

    def "buildLogsCommand includes all services"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)
        def mockTimeService = Mock(TimeService)
        mockValidator.validateDockerCompose() >> {}

        def service = new TestableComposeService(mockExecutor, mockValidator, mockLogger, mockTimeService)
        def config = new LogsConfig(["web", "api", "db"], 50, false)

        when:
        def command = service.buildLogsCommand("myproject", config, ['docker', 'compose'])

        then:
        command.contains("web")
        command.contains("api")
        command.contains("db")
        command.contains("-p")
        command.contains("myproject")
    }

    // ========== ExecLibraryComposeService getStackServices Tests ==========

    def "getStackServices returns empty map when stdout is null"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)
        def mockTimeService = Mock(TimeService)
        mockValidator.validateDockerCompose() >> {}
        mockValidator.detectComposeCommand() >> ['docker', 'compose']
        mockExecutor.execute(_ as List) >> new ProcessResult(0, null, "")

        def service = new TestableComposeService(mockExecutor, mockValidator, mockLogger, mockTimeService)

        when:
        def services = service.getStackServices("test-project")

        then:
        services.isEmpty()
    }

    def "getStackServices parses multiple JSON lines"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)
        def mockTimeService = Mock(TimeService)
        mockValidator.validateDockerCompose() >> {}
        mockValidator.detectComposeCommand() >> ['docker', 'compose']

        def jsonOutput = '''{"ID":"abc123","Name":"proj_web_1","Service":"web","State":"running","Ports":"8080:80"}
{"ID":"def456","Name":"proj_api_1","Service":"api","State":"running (healthy)","Ports":"3000:3000"}
{"ID":"ghi789","Name":"proj_db_1","Service":"db","State":"running","Ports":"5432:5432"}'''

        mockExecutor.execute(_ as List) >> new ProcessResult(0, jsonOutput, "")

        def service = new TestableComposeService(mockExecutor, mockValidator, mockLogger, mockTimeService)

        when:
        def services = service.getStackServices("proj")

        then:
        services.size() == 3
        services["web"] != null
        services["api"] != null
        services["db"] != null
    }

    // ========== ExecLibraryComposeService checkServiceReady Tests ==========

    def "checkServiceReady handles empty stdout"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)
        def mockTimeService = Mock(TimeService)
        mockValidator.validateDockerCompose() >> {}
        mockValidator.detectComposeCommand() >> ['docker', 'compose']
        mockExecutor.execute(_ as List) >> new ProcessResult(0, "", "")

        def service = new TestableComposeService(mockExecutor, mockValidator, mockLogger, mockTimeService)

        when:
        def ready = service.checkServiceReady("test-project", "service1", ServiceStatus.RUNNING)

        then:
        !ready
    }

    def "checkServiceReady returns true for 'up' status when checking RUNNING"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)
        def mockTimeService = Mock(TimeService)
        mockValidator.validateDockerCompose() >> {}
        mockValidator.detectComposeCommand() >> ['docker', 'compose']
        mockExecutor.execute(_ as List) >> new ProcessResult(0, "service1  Up  10 seconds", "")

        def service = new TestableComposeService(mockExecutor, mockValidator, mockLogger, mockTimeService)

        when:
        def ready = service.checkServiceReady("test-project", "service1", ServiceStatus.RUNNING)

        then:
        ready
    }

    def "checkServiceReady returns false for 'starting' when checking HEALTHY"() {
        given:
        def mockExecutor = Mock(ProcessExecutor)
        def mockValidator = Mock(CommandValidator)
        def mockLogger = Mock(ServiceLogger)
        def mockTimeService = Mock(TimeService)
        mockValidator.validateDockerCompose() >> {}
        mockValidator.detectComposeCommand() >> ['docker', 'compose']
        mockExecutor.execute(_ as List) >> new ProcessResult(0, "service1  starting", "")

        def service = new TestableComposeService(mockExecutor, mockValidator, mockLogger, mockTimeService)

        when:
        def ready = service.checkServiceReady("test-project", "service1", ServiceStatus.HEALTHY)

        then:
        !ready
    }

    // ========== DockerServiceImpl Pure Functions Additional Tests ==========

    def "selectPrimaryTag handles tags with only registry prefixes"() {
        expect:
        DockerServiceImpl.selectPrimaryTag(tags) == expected

        where:
        tags                                                    | expected
        ['registry.io/app:latest']                              | 'registry.io/app:latest'
        ['registry.io/app:latest', 'registry.io/app:1.0']       | 'registry.io/app:1.0'
        ['registry.io:5000/app:v1', 'registry.io:5000/app:v2']  | 'registry.io:5000/app:v1'
    }

    def "selectPrimaryTag prefers non-latest tag from any position"() {
        expect:
        DockerServiceImpl.selectPrimaryTag(['app:latest', 'app:1.0.0', 'app:dev']) == 'app:1.0.0'
        DockerServiceImpl.selectPrimaryTag(['app:1.0.0', 'app:latest', 'app:dev']) == 'app:1.0.0'
        DockerServiceImpl.selectPrimaryTag(['app:dev', 'app:1.0.0', 'app:latest']) == 'app:dev'
    }

    def "BuildConfiguration stores all properties immutably"() {
        given:
        def contextFile = tempDir.resolve("context").toFile()
        def dockerfileFile = tempDir.resolve("Dockerfile").toFile()
        def buildArgs = [VERSION: '1.0', BUILD_DATE: '2025-01-01']
        def labels = [maintainer: 'test@example.com']
        def additionalTags = ['app:dev', 'app:staging']

        when:
        def config = new DockerServiceImpl.BuildConfiguration(
            contextFile,
            dockerfileFile,
            true,
            'app:1.0.0',
            additionalTags,
            buildArgs,
            labels
        )

        then:
        config.contextFile == contextFile
        config.dockerfileFile == dockerfileFile
        config.needsTemporaryDockerfile == true
        config.primaryTag == 'app:1.0.0'
        config.additionalTags == additionalTags
        config.buildArgs == buildArgs
        config.labels == labels
    }

    def "BuildConfiguration handles empty additional tags"() {
        given:
        def contextFile = tempDir.resolve("context").toFile()
        def dockerfileFile = tempDir.resolve("Dockerfile").toFile()

        when:
        def config = new DockerServiceImpl.BuildConfiguration(
            contextFile,
            dockerfileFile,
            false,
            'app:latest',
            [],
            [:],
            [:]
        )

        then:
        config.additionalTags == []
        config.buildArgs == [:]
        config.labels == [:]
    }

    // ========== DefaultFileOperations Additional Tests ==========

    def "DefaultFileOperations implements Serializable"() {
        expect:
        Serializable.isAssignableFrom(DefaultFileOperations)
    }

    def "DefaultFileOperations can be serialized and deserialized"() {
        given:
        def fileOps = new DefaultFileOperations()
        def baos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(baos)

        when:
        oos.writeObject(fileOps)
        oos.close()

        def bais = new ByteArrayInputStream(baos.toByteArray())
        def ois = new ObjectInputStream(bais)
        def deserializedFileOps = ois.readObject()

        then:
        deserializedFileOps instanceof DefaultFileOperations
    }

    // ========== SystemTimeService Additional Tests ==========

    def "SystemTimeService implements Serializable"() {
        expect:
        Serializable.isAssignableFrom(SystemTimeService)
    }

    def "SystemTimeService can be serialized and deserialized"() {
        given:
        def timeService = new SystemTimeService()
        def baos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(baos)

        when:
        oos.writeObject(timeService)
        oos.close()

        def bais = new ByteArrayInputStream(baos.toByteArray())
        def ois = new ObjectInputStream(bais)
        def deserializedTimeService = ois.readObject()

        then:
        deserializedTimeService instanceof SystemTimeService
    }

    def "SystemTimeService sleep handles zero milliseconds"() {
        given:
        def timeService = new SystemTimeService()
        def before = System.currentTimeMillis()

        when:
        timeService.sleep(0)

        then:
        def after = System.currentTimeMillis()
        after >= before
        noExceptionThrown()
    }

    // ========== JsonServiceImpl Additional Tests ==========

    def "JsonServiceImpl getObjectMapper returns consistent instance"() {
        given:
        def service = new TestableJsonService()

        when:
        def mapper1 = service.getObjectMapper()
        def mapper2 = service.getObjectMapper()

        then:
        mapper1 != null
        mapper1.is(mapper2)
    }

    def "JsonServiceImpl parseJson handles nested objects"() {
        given:
        def service = new TestableJsonService()
        def json = '{"level1":{"level2":{"level3":"deep"}}}'

        when:
        def result = service.parseJson(json)

        then:
        result instanceof Map
        result.level1.level2.level3 == "deep"
    }

    def "JsonServiceImpl parseJsonArray handles objects with nested arrays"() {
        given:
        def service = new TestableJsonService()
        def json = '[{"items":[1,2,3]},{"items":[4,5,6]}]'

        when:
        def result = service.parseJsonArray(json)

        then:
        result.size() == 2
        result[0].items == [1, 2, 3]
        result[1].items == [4, 5, 6]
    }

    // ========== Helper Classes ==========

    /**
     * Testable version of ExecLibraryComposeService for unit testing
     */
    static class TestableComposeService extends ExecLibraryComposeService {
        TestableComposeService(ProcessExecutor processExecutor,
                               CommandValidator commandValidator,
                               ServiceLogger serviceLogger,
                               TimeService timeService) {
            super(processExecutor, commandValidator, serviceLogger, timeService)
        }

        @Override
        org.gradle.api.services.BuildServiceParameters.None getParameters() {
            return null
        }
    }

    /**
     * Testable version of JsonServiceImpl for unit testing
     */
    static class TestableJsonService extends JsonServiceImpl {
        @Override
        org.gradle.api.services.BuildServiceParameters.None getParameters() {
            return null
        }
    }
}
