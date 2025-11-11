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

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ExecutionException

/**
 * Comprehensive unit tests for ExecLibraryComposeService that properly mock external dependencies
 */
class ExecLibraryComposeServiceUnitTest extends Specification {

    @TempDir
    Path tempDir

    ProcessExecutor mockProcessExecutor
    CommandValidator mockCommandValidator
    ServiceLogger mockServiceLogger
    TimeService mockTimeService
    TestExecLibraryComposeService service

    def setup() {
        mockProcessExecutor = Mock(ProcessExecutor)
        mockCommandValidator = Mock(CommandValidator)
        mockServiceLogger = Mock(ServiceLogger)
        mockTimeService = Mock(TimeService)

        service = new TestExecLibraryComposeService(mockProcessExecutor, mockCommandValidator, mockServiceLogger, mockTimeService)
    }

    // ============ Constructor Tests ============

    def "constructor initializes with mocked dependencies"() {
        given:
        mockCommandValidator.validateDockerCompose() >> {}

        when:
        def svc = new TestExecLibraryComposeService(mockProcessExecutor, mockCommandValidator, mockServiceLogger, mockTimeService)

        then:
        svc != null
        1 * mockCommandValidator.validateDockerCompose()
        1 * mockServiceLogger.info("ComposeService initialized with docker-compose CLI")
    }

    // ============ Test Helper Class ============

    /**
     * Concrete test implementation of ExecLibraryComposeService for unit testing
     */
    static class TestExecLibraryComposeService extends ExecLibraryComposeService {
        TestExecLibraryComposeService(ProcessExecutor processExecutor,
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

    // ============ upStack Tests ============

    def "upStack throws NullPointerException for null config"() {
        when:
        service.upStack(null)

        then:
        thrown(NullPointerException)
    }

    def "upStack successfully starts stack with single compose file"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"),
            "services:\n  test:\n    image: alpine".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List, _ as File) >> new ProcessResult(0, "", "")
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "", "")

        when:
        def future = service.upStack(config)
        def result = future.get()

        then:
        result != null
        result.configName == "test-stack"
        result.projectName == "test-project"
        1 * mockServiceLogger.info("Starting Docker Compose stack: test-stack")
        1 * mockServiceLogger.info("Docker Compose stack started: test-stack")
    }

    def "upStack handles multiple compose files"() {
        given:
        def composeFile1 = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def composeFile2 = Files.write(tempDir.resolve("docker-compose.override.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile1, composeFile2], "test-project", "test-stack")

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List, _ as File) >> new ProcessResult(0, "", "")
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "", "")

        when:
        def future = service.upStack(config)
        def result = future.get()

        then:
        result != null
        result.projectName == "test-project"
    }

    def "upStack handles env files in config"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def envFile = Files.write(tempDir.resolve(".env"), "VAR=value".bytes)
        def config = new ComposeConfig([composeFile], [envFile], "test-project", "test-stack", [:])

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List, _ as File) >> new ProcessResult(0, "", "")
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "", "")

        when:
        def future = service.upStack(config)
        def result = future.get()

        then:
        result != null
    }

    def "upStack throws exception when docker compose fails"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List, _ as File) >> new ProcessResult(1, "", "Compose error")

        when:
        def future = service.upStack(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.SERVICE_START_FAILED
        ex.cause.message.contains("Docker Compose up failed with exit code 1")
    }

    def "upStack wraps generic exceptions"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List, _ as File) >> { throw new RuntimeException("Process failed") }

        when:
        def future = service.upStack(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.SERVICE_START_FAILED
        ex.cause.message.contains("Failed to start compose stack")
    }

    // ============ buildUpCommand Tests ============

    def "buildUpCommand builds command with single compose file"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildUpCommand(config, baseCommand)

        then:
        command == ['docker', 'compose', '-f', composeFile.toString(),
                    '-p', 'test-project', 'up', '-d']
    }

    def "buildUpCommand builds command with multiple compose files"() {
        given:
        def file1 = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def file2 = Files.write(tempDir.resolve("docker-compose.override.yml"), "services:".bytes)
        def config = new ComposeConfig([file1, file2], "test-project", "test-stack")
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildUpCommand(config, baseCommand)

        then:
        command.contains('-f')
        command.indexOf(file1.toString()) < command.indexOf(file2.toString())
        command.contains('up')
        command.contains('-d')
    }

    def "buildUpCommand includes env files when present"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def envFile = Files.write(tempDir.resolve(".env"), "VAR=value".bytes)
        def config = new ComposeConfig([composeFile], [envFile], "test-project", "test-stack", [:])
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildUpCommand(config, baseCommand)

        then:
        command.contains('--env-file')
        command.contains(envFile.toString())
    }

    def "buildUpCommand uses custom project name"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "custom-project-name", "test-stack")
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildUpCommand(config, baseCommand)

        then:
        def projectIndex = command.indexOf('-p')
        projectIndex >= 0
        command[projectIndex + 1] == 'custom-project-name'
    }

    def "buildUpCommand does not mutate base command"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")
        def baseCommand = ['docker', 'compose']
        def originalSize = baseCommand.size()

        when:
        service.buildUpCommand(config, baseCommand)

        then:
        baseCommand.size() == originalSize  // Ensure clone() worked
    }

    def "buildUpCommand handles edge cases: #scenario"() {
        given:
        // Create the necessary files in tempDir
        def composeFile = Files.write(tempDir.resolve("compose-${scenario}.yml"), "services:".bytes)
        def createdEnvFiles = envFileNames.collect { name ->
            Files.write(tempDir.resolve(name), "VAR=value".bytes)
        }
        def config = new ComposeConfig([composeFile], createdEnvFiles, projectName, stackName, [:])
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildUpCommand(config, baseCommand)

        then:
        command.contains('up')
        command.contains('-d')
        command.contains(projectName)

        where:
        scenario                  | envFileNames                 | projectName                              | stackName
        "no env files"            | []                           | "proj1"                                  | "stack1"
        "multiple env files"      | [".env", ".env.local"]       | "proj2"                                  | "stack2"
        "long project name"       | []                           | "very-long-project-name-with-hyphens"    | "stack3"
    }

    def "buildUpCommand includes all compose files in correct order"() {
        given:
        def file1 = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def file2 = Files.write(tempDir.resolve("docker-compose.override.yml"), "services:".bytes)
        def file3 = Files.write(tempDir.resolve("docker-compose.local.yml"), "services:".bytes)
        def config = new ComposeConfig([file1, file2, file3], "test-project", "test-stack")
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildUpCommand(config, baseCommand)

        then:
        command.count { it == '-f' } == 3
        def file1Index = command.indexOf(file1.toString())
        def file2Index = command.indexOf(file2.toString())
        def file3Index = command.indexOf(file3.toString())
        file1Index < file2Index
        file2Index < file3Index
    }

    // ============ getStackServices Tests ============

    def "getStackServices parses JSON output correctly"() {
        given:
        def jsonOutput = '''{"ID":"abc123","Name":"project_service1_1","Service":"service1","State":"running","Ports":"0.0.0.0:8080->80/tcp"}
{"ID":"def456","Name":"project_service2_1","Service":"service2","State":"running (healthy)","Ports":""}'''

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, jsonOutput, "")

        when:
        def services = service.getStackServices("test-project")

        then:
        services.size() == 2
        services["service1"] != null
        services["service1"].containerName == "service1"
        services["service1"].state.toUpperCase() == "RUNNING" ||  services["service1"].state == "running"
        services["service2"] != null
        services["service2"].state.toLowerCase().contains("healthy")
    }

    def "getStackServices handles empty output"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "", "")

        when:
        def services = service.getStackServices("test-project")

        then:
        services.isEmpty()
    }

    def "getStackServices handles command failure gracefully"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(1, "", "error")

        when:
        def services = service.getStackServices("test-project")

        then:
        services.isEmpty()
        1 * mockServiceLogger.warn(_ as String)
    }

    def "getStackServices handles exception gracefully"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> { throw new RuntimeException("Process failed") }

        when:
        def services = service.getStackServices("test-project")

        then:
        services.isEmpty()
        1 * mockServiceLogger.warn(_ as String)
    }

    def "getStackServices parses JSON with fallback name parsing"() {
        given:
        def jsonOutput = '{"ID":"abc123","Name":"project_service1_1","State":"running","Ports":""}'

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, jsonOutput, "")

        when:
        def services = service.getStackServices("test-project")

        then:
        services.size() == 1
        services["service1"] != null
    }

    // parseServiceState and parsePortMappings tests removed - these methods were extracted to
    // ComposeOutputParser utility class and are comprehensively tested in ComposeOutputParserTest

    // ============ downStack(String) Tests ============

    def "downStack with project name throws NullPointerException for null"() {
        when:
        service.downStack((String) null)

        then:
        thrown(NullPointerException)
    }

    def "downStack with project name succeeds"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "", "")

        when:
        def future = service.downStack("test-project")
        future.get()

        then:
        notThrown(Exception)
        1 * mockServiceLogger.info("Stopping Docker Compose stack: test-project")
        1 * mockServiceLogger.info("Docker Compose stack stopped: test-project")
    }

    def "downStack with project name throws exception on failure"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(1, "", "error")

        when:
        def future = service.downStack("test-project")
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.SERVICE_STOP_FAILED
    }

    def "downStack with project name wraps generic exceptions"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> { throw new RuntimeException("Process failed") }

        when:
        def future = service.downStack("test-project")
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.SERVICE_STOP_FAILED
    }

    // ============ buildDownCommand Tests ============

    def "buildDownCommand builds command with single compose file"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildDownCommand(config, baseCommand)

        then:
        command == ['docker', 'compose', '-f', composeFile.toString(),
                    '-p', 'test-project', 'down', '--remove-orphans']
    }

    def "buildDownCommand includes remove-orphans flag"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildDownCommand(config, baseCommand)

        then:
        command.contains('--remove-orphans')
    }

    def "buildDownCommand handles multiple compose files"() {
        given:
        def file1 = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def file2 = Files.write(tempDir.resolve("docker-compose.override.yml"), "services:".bytes)
        def config = new ComposeConfig([file1, file2], "test-project", "test-stack")
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildDownCommand(config, baseCommand)

        then:
        command.count { it == '-f' } == 2
        command.contains(file1.toString())
        command.contains(file2.toString())
    }

    def "buildDownCommand includes env files when present"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def envFile = Files.write(tempDir.resolve(".env"), "VAR=value".bytes)
        def config = new ComposeConfig([composeFile], [envFile], "test-project", "test-stack", [:])
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildDownCommand(config, baseCommand)

        then:
        command.contains('--env-file')
        command.contains(envFile.toString())
    }

    def "buildDownCommand does not mutate base command"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")
        def baseCommand = ['docker', 'compose']
        def originalSize = baseCommand.size()

        when:
        service.buildDownCommand(config, baseCommand)

        then:
        baseCommand.size() == originalSize  // Ensure clone() worked
    }

    // ============ downStack(ComposeConfig) Tests ============

    def "downStack with config throws NullPointerException for null"() {
        when:
        service.downStack((ComposeConfig) null)

        then:
        thrown(NullPointerException)
    }

    def "downStack with config succeeds"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List, _ as File) >> new ProcessResult(0, "", "")

        when:
        def future = service.downStack(config)
        future.get()

        then:
        notThrown(Exception)
        1 * mockServiceLogger.info("Stopping Docker Compose stack: test-stack (project: test-project)")
        1 * mockServiceLogger.info("Docker Compose stack stopped: test-stack (project: test-project)")
    }

    def "downStack with config handles env files"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def envFile = Files.write(tempDir.resolve(".env"), "VAR=value".bytes)
        def config = new ComposeConfig([composeFile], [envFile], "test-project", "test-stack", [:])

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List, _ as File) >> new ProcessResult(0, "", "")

        when:
        def future = service.downStack(config)
        future.get()

        then:
        notThrown(Exception)
    }

    def "downStack with config throws exception on failure"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "services:".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List, _ as File) >> new ProcessResult(1, "", "error")

        when:
        def future = service.downStack(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.SERVICE_STOP_FAILED
    }

    // ============ waitForServices Tests ============

    def "waitForServices throws NullPointerException for null config"() {
        when:
        service.waitForServices(null)

        then:
        thrown(NullPointerException)
    }

    def "waitForServices succeeds when services are ready immediately"() {
        given:
        def config = new WaitConfig("test-project", ["service1"], Duration.ofSeconds(10),
                                    Duration.ofMillis(100), ServiceStatus.RUNNING)

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "service1  running", "")

        when:
        def future = service.waitForServices(config)
        def result = future.get()

        then:
        result == ServiceStatus.RUNNING
        1 * mockServiceLogger.info("Waiting for services: [service1]")
        1 * mockServiceLogger.info("All services are ready: [service1]")
    }

    def "waitForServices waits for HEALTHY status"() {
        given:
        def config = new WaitConfig("test-project", ["service1"], Duration.ofSeconds(10),
                                    Duration.ofMillis(100), ServiceStatus.HEALTHY)

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "service1  healthy", "")

        when:
        def future = service.waitForServices(config)
        def result = future.get()

        then:
        result == ServiceStatus.HEALTHY
    }

    def "waitForServices throws timeout exception when services not ready"() {
        given:
        def config = new WaitConfig("test-project", ["service1"], Duration.ofMillis(200),
                                    Duration.ofMillis(50), ServiceStatus.RUNNING)

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "service1  starting", "")

        // Mock time progression to trigger timeout - use >>> for thread-safe list of responses
        mockTimeService.currentTimeMillis() >>> [0L, 0L, 250L, 250L, 250L]  // Extra values in case of multiple calls
        mockTimeService.sleep(_ as Long) >> { /* no-op */ }

        when:
        def future = service.waitForServices(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.SERVICE_TIMEOUT
        ex.cause.message.contains("Timeout waiting for services")
    }

    def "waitForServices wraps generic exceptions"() {
        given:
        def config = new WaitConfig("test-project", ["service1"], Duration.ofSeconds(10),
                                    Duration.ofMillis(100), ServiceStatus.RUNNING)

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> { throw new RuntimeException("Process failed") }

        // Mock time service to trigger timeout after exception is thrown
        // Timeout is 10 seconds (10000ms), so time needs to exceed that to trigger timeout
        mockTimeService.currentTimeMillis() >>> [0L, 0L, 10001L, 10001L, 10001L]
        mockTimeService.sleep(_ as Long) >> {}

        when:
        def future = service.waitForServices(config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.SERVICE_TIMEOUT
    }

    // ============ checkServiceReady Tests ============

    def "checkServiceReady returns true for RUNNING status when service is up"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "service1  running", "")

        when:
        def ready = service.checkServiceReady("test-project", "service1", ServiceStatus.RUNNING)

        then:
        ready == true
    }

    def "checkServiceReady returns true for HEALTHY status when service is healthy"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "service1  healthy", "")

        when:
        def ready = service.checkServiceReady("test-project", "service1", ServiceStatus.HEALTHY)

        then:
        ready == true
    }

    def "checkServiceReady returns false when service not in target state"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "service1  starting", "")

        when:
        def ready = service.checkServiceReady("test-project", "service1", ServiceStatus.RUNNING)

        then:
        ready == false
    }

    def "checkServiceReady returns false on command failure"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(1, "", "error")

        when:
        def ready = service.checkServiceReady("test-project", "service1", ServiceStatus.RUNNING)

        then:
        ready == false
    }

    def "checkServiceReady returns false on exception"() {
        given:
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> { throw new RuntimeException("Process failed") }

        when:
        def ready = service.checkServiceReady("test-project", "service1", ServiceStatus.RUNNING)

        then:
        ready == false
        1 * mockServiceLogger.debug(_ as String)
    }

    // ============ buildLogsCommand Tests ============

    def "buildLogsCommand builds basic command"() {
        given:
        // LogsConfig has default tailLines=100, which will be included in the command
        def config = new LogsConfig(["service1"])
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildLogsCommand("test-project", config, baseCommand)

        then:
        command.contains('-p')
        command.contains('test-project')
        command.contains('logs')
        command.contains('--tail')
        command.contains('100')
        command.contains('service1')
    }

    def "buildLogsCommand includes follow flag when enabled"() {
        given:
        def config = new LogsConfig(["service1"], 0, true)
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildLogsCommand("test-project", config, baseCommand)

        then:
        command.contains('--follow')
    }

    def "buildLogsCommand includes tail lines when specified"() {
        given:
        def config = new LogsConfig(["service1"], 100, false)
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildLogsCommand("test-project", config, baseCommand)

        then:
        command.contains('--tail')
        command.contains('100')
    }

    def "buildLogsCommand handles multiple services"() {
        given:
        def config = new LogsConfig(["service1", "service2", "service3"])
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildLogsCommand("test-project", config, baseCommand)

        then:
        command.contains('service1')
        command.contains('service2')
        command.contains('service3')
    }

    def "buildLogsCommand handles empty services list"() {
        given:
        // LogsConfig has default tailLines=100, which will be included even with empty services
        def config = new LogsConfig([])
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildLogsCommand("test-project", config, baseCommand)

        then:
        command.contains('-p')
        command.contains('test-project')
        command.contains('logs')
        command.contains('--tail')
        command.contains('100')
        !command.any { it.startsWith('service') }  // No service names
    }

    def "buildLogsCommand combines all options"() {
        given:
        def config = new LogsConfig(["service1", "service2"], 50, true)
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildLogsCommand("test-project", config, baseCommand)

        then:
        command.contains('--follow')
        command.contains('--tail')
        command.contains('50')
        command.contains('service1')
        command.contains('service2')
    }

    def "buildLogsCommand handles edge cases: #scenario"() {
        given:
        def config = new LogsConfig(services, tailLines, follow)
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildLogsCommand(projectName, config, baseCommand)

        then:
        command.contains(projectName)
        command.contains('logs')

        where:
        scenario                      | projectName  | services          | tailLines | follow
        "no services, no options"     | "proj1"      | []                | 0         | false
        "single service with follow"  | "proj2"      | ["web"]           | 0         | true
        "multiple services with tail" | "proj3"      | ["web", "db"]     | 200       | false
        "all options combined"        | "proj4"      | ["web"]           | 100       | true
    }

    def "buildLogsCommand includes tail even when zero passed (LogsConfig enforces min of 1)"() {
        given:
        // LogsConfig uses Math.max(1, tailLines), so 0 becomes 1
        def config = new LogsConfig(["service1"], 0, false)
        def baseCommand = ['docker', 'compose']

        when:
        def command = service.buildLogsCommand("test-project", config, baseCommand)

        then:
        command.contains('--tail')
        command.contains('1')  // LogsConfig enforces minimum of 1
    }

    // ============ captureLogs Tests ============

    def "captureLogs throws NullPointerException for null project name"() {
        when:
        service.captureLogs(null, new LogsConfig([]))

        then:
        thrown(NullPointerException)
    }

    def "captureLogs throws NullPointerException for null config"() {
        when:
        service.captureLogs("test-project", null)

        then:
        thrown(NullPointerException)
    }

    def "captureLogs successfully captures logs"() {
        given:
        def config = new LogsConfig(["service1"])

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "log output", "")

        when:
        def future = service.captureLogs("test-project", config)
        def result = future.get()

        then:
        result == "log output"
        1 * mockServiceLogger.info("Capturing logs for project: test-project")
    }

    def "captureLogs handles follow option"() {
        given:
        def config = new LogsConfig(["service1"], 100, true)

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "logs", "")

        when:
        def future = service.captureLogs("test-project", config)
        def result = future.get()

        then:
        result == "logs"
    }

    def "captureLogs handles tail lines option"() {
        given:
        def config = new LogsConfig(["service1"], 100, false)

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "logs", "")

        when:
        def future = service.captureLogs("test-project", config)
        def result = future.get()

        then:
        result == "logs"
    }

    def "captureLogs handles empty services list"() {
        given:
        def config = new LogsConfig([])

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(0, "all logs", "")

        when:
        def future = service.captureLogs("test-project", config)
        def result = future.get()

        then:
        result == "all logs"
    }

    def "captureLogs throws exception on failure"() {
        given:
        def config = new LogsConfig(["service1"])

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> new ProcessResult(1, "", "error")

        when:
        def future = service.captureLogs("test-project", config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED
    }

    def "captureLogs wraps generic exceptions"() {
        given:
        def config = new LogsConfig(["service1"])

        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockProcessExecutor.execute(_ as List) >> { throw new RuntimeException("Process failed") }

        when:
        def future = service.captureLogs("test-project", config)
        future.get()

        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof ComposeServiceException
        ex.cause.errorType == ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED
    }
}
