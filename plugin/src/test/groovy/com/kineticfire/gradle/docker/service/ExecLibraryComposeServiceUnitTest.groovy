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
    TestExecLibraryComposeService service

    def setup() {
        mockProcessExecutor = Mock(ProcessExecutor)
        mockCommandValidator = Mock(CommandValidator)
        mockServiceLogger = Mock(ServiceLogger)

        service = new TestExecLibraryComposeService(mockProcessExecutor, mockCommandValidator, mockServiceLogger)
    }

    // ============ Constructor Tests ============

    def "constructor initializes with mocked dependencies"() {
        given:
        mockCommandValidator.validateDockerCompose() >> {}

        when:
        def svc = new TestExecLibraryComposeService(mockProcessExecutor, mockCommandValidator, mockServiceLogger)

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
                                      ServiceLogger serviceLogger) {
            super(processExecutor, commandValidator, serviceLogger)
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

    // ============ parseServiceState Tests ============

    def "parseServiceState correctly identifies RUNNING status"() {
        expect:
        service.parseServiceState(status) == ServiceStatus.RUNNING

        where:
        status << ["running", "Up 5 minutes", "up", "RUNNING", "UP"]
    }

    def "parseServiceState correctly identifies HEALTHY status"() {
        expect:
        service.parseServiceState(status) == ServiceStatus.HEALTHY

        where:
        status << ["running (healthy)", "Up (healthy)", "RUNNING (HEALTHY)", "up healthy"]
    }

    def "parseServiceState correctly identifies STOPPED status"() {
        expect:
        service.parseServiceState(status) == ServiceStatus.STOPPED

        where:
        status << ["stopped", "Exit 0", "exit 1", "STOPPED", "EXIT"]
    }

    def "parseServiceState correctly identifies RESTARTING status"() {
        expect:
        service.parseServiceState(status) == ServiceStatus.RESTARTING

        where:
        status << ["Restarting", "restarting (1)", "RESTARTING"]
    }

    def "parseServiceState returns UNKNOWN for unrecognized or null status"() {
        expect:
        service.parseServiceState(status) == ServiceStatus.UNKNOWN

        where:
        status << [null, "", "unknown", "weird state", "   "]
    }

    // ============ parsePortMappings Tests ============

    def "parsePortMappings correctly parses port mappings"() {
        when:
        def mappings = service.parsePortMappings("0.0.0.0:8080->80/tcp, :::9090->90/tcp")

        then:
        mappings.size() == 2
        mappings[0].hostPort == 8080
        mappings[0].containerPort == 80
        mappings[0].protocol == "tcp"
        mappings[1].hostPort == 9090
        mappings[1].containerPort == 90
    }

    def "parsePortMappings handles empty or null input"() {
        expect:
        service.parsePortMappings(input).isEmpty()

        where:
        input << [null, "", "   "]
    }

    def "parsePortMappings handles malformed port strings gracefully"() {
        when:
        def mappings = service.parsePortMappings("invalid-port-format")

        then:
        mappings.isEmpty()
    }

    def "parsePortMappings handles UDP protocol"() {
        when:
        def mappings = service.parsePortMappings("0.0.0.0:5353->53/udp")

        then:
        mappings.size() == 1
        mappings[0].protocol == "udp"
    }

    def "parsePortMappings defaults to tcp when protocol not specified"() {
        when:
        def mappings = service.parsePortMappings("8080->80")

        then:
        mappings.size() == 1
        mappings[0].protocol == "tcp"
    }

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
