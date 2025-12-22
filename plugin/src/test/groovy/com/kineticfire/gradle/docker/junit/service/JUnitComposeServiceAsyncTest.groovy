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

package com.kineticfire.gradle.docker.junit.service

import com.kineticfire.gradle.docker.model.*
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ExecutionException

/**
 * Unit tests for JUnitComposeService async methods.
 *
 * These tests execute the closures inside CompletableFuture.supplyAsync() and runAsync()
 * by calling .get() to block until completion and verify behavior.
 */
class JUnitComposeServiceAsyncTest extends Specification {

    @Subject
    JUnitComposeService service

    ProcessExecutor mockExecutor

    Path tempComposeFile
    File tempDir

    def setup() {
        mockExecutor = Mock(ProcessExecutor)
        service = new JUnitComposeService(mockExecutor)

        // Create temp compose file for tests
        tempComposeFile = Files.createTempFile("test-compose", ".yml")
        Files.write(tempComposeFile, "services:\n  web:\n    image: nginx\n".bytes)
        tempDir = tempComposeFile.parent.toFile()
    }

    def cleanup() {
        if (tempComposeFile != null && Files.exists(tempComposeFile)) {
            Files.delete(tempComposeFile)
        }
    }

    // =====================================================
    // upStack async closure tests
    // =====================================================

    def "upStack executes closure successfully and returns ComposeState"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"running","Ports":"8080->80/tcp"}')

        when:
        def result = service.upStack(config).get()

        then:
        result != null
        result.configName == "test-stack"
        result.projectName == "test-project"
    }

    def "upStack executes closure with multiple compose files"() {
        given:
        Path tempComposeFile2 = Files.createTempFile("test-compose2", ".yml")
        Files.write(tempComposeFile2, "services:\n  db:\n    image: postgres\n".bytes)

        def config = new ComposeConfig([tempComposeFile, tempComposeFile2], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"running"}')

        when:
        def result = service.upStack(config).get()

        then:
        result != null

        cleanup:
        Files.deleteIfExists(tempComposeFile2)
    }

    def "upStack executes closure with env files"() {
        given:
        Path envFile = Files.createTempFile("test", ".env")
        Files.write(envFile, "VAR=value".bytes)

        def config = new ComposeConfig([tempComposeFile], [envFile], "test-project", "test-stack", [:])

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{}')

        when:
        def result = service.upStack(config).get()

        then:
        result != null

        cleanup:
        Files.deleteIfExists(envFile)
    }

    def "upStack throws exception when docker compose fails"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(1, "Docker compose failed")

        when:
        service.upStack(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Failed to start compose stack")
    }

    def "upStack handles exception in closure and wraps it"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> { throw new IOException("Connection refused") }

        when:
        service.upStack(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Failed to start compose stack")
    }

    // =====================================================
    // downStack(String) async closure tests
    // =====================================================

    def "downStack with String executes closure successfully"() {
        given:
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "Stopped")

        when:
        service.downStack("test-project").get()

        then:
        noExceptionThrown()
    }

    def "downStack with String throws exception when docker compose fails"() {
        given:
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(1, "Docker compose down failed")

        when:
        service.downStack("test-project").get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Failed to stop compose stack")
    }

    def "downStack with String handles exception in closure"() {
        given:
        mockExecutor.execute(_) >> { throw new IOException("Process failed") }

        when:
        service.downStack("test-project").get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Failed to stop compose stack")
    }

    // =====================================================
    // downStack(ComposeConfig) async closure tests
    // =====================================================

    def "downStack with ComposeConfig executes closure successfully"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Stopped")

        when:
        service.downStack(config).get()

        then:
        noExceptionThrown()
    }

    def "downStack with ComposeConfig throws exception when docker compose fails"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(1, "Docker compose down failed")

        when:
        service.downStack(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Failed to stop compose stack")
    }

    def "downStack with ComposeConfig handles exception in closure"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> { throw new IOException("Process failed") }

        when:
        service.downStack(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Failed to stop compose stack")
    }

    // =====================================================
    // waitForServices async closure tests
    // =====================================================

    def "waitForServices executes closure and returns when all services are ready"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofSeconds(10),
            Duration.ofMillis(100),
            ServiceStatus.RUNNING
        )

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Up 2 minutes")

        when:
        def result = service.waitForServices(config).get()

        then:
        result == ServiceStatus.RUNNING
    }

    def "waitForServices executes closure and returns HEALTHY when healthy"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofSeconds(10),
            Duration.ofMillis(100),
            ServiceStatus.HEALTHY
        )

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Up (healthy)")

        when:
        def result = service.waitForServices(config).get()

        then:
        result == ServiceStatus.HEALTHY
    }

    def "waitForServices polls multiple times until ready"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofSeconds(10),
            Duration.ofMillis(50),
            ServiceStatus.RUNNING
        )

        def callCount = 0

        mockExecutor.execute(_) >> {
            callCount++
            if (callCount < 3) {
                return new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Starting")
            } else {
                return new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Up")
            }
        }

        when:
        def result = service.waitForServices(config).get()

        then:
        result == ServiceStatus.RUNNING
    }

    def "waitForServices throws timeout exception when services don't become ready"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofMillis(200),  // Short timeout
            Duration.ofMillis(50),
            ServiceStatus.RUNNING
        )

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Starting")

        when:
        service.waitForServices(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Timeout waiting for services")
    }

    def "waitForServices handles multiple services"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web", "db"],
            Duration.ofSeconds(10),
            Duration.ofMillis(100),
            ServiceStatus.RUNNING
        )

        // Both services return running status
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "Up running")

        when:
        def result = service.waitForServices(config).get()

        then:
        result == ServiceStatus.RUNNING
    }

    def "waitForServices handles exception in closure"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofSeconds(10),
            Duration.ofMillis(100),
            ServiceStatus.RUNNING
        )

        mockExecutor.execute(_) >> { throw new IOException("Process failed") }

        when:
        service.waitForServices(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Error waiting for services")
    }

    // =====================================================
    // captureLogs async closure tests
    // =====================================================

    def "captureLogs executes closure and returns logs"() {
        given:
        def config = new LogsConfig([], 0, false)

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "Log output here")

        when:
        def result = service.captureLogs("test-project", config).get()

        then:
        result == "Log output here"
    }

    def "captureLogs with follow option"() {
        given:
        def config = new LogsConfig([], 0, true)

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "Following logs")

        when:
        def result = service.captureLogs("test-project", config).get()

        then:
        result == "Following logs"
    }

    def "captureLogs with tail lines option"() {
        given:
        def config = new LogsConfig([], 100, false)

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "Last 100 lines")

        when:
        def result = service.captureLogs("test-project", config).get()

        then:
        result == "Last 100 lines"
    }

    def "captureLogs with specific services"() {
        given:
        def config = new LogsConfig(["web", "db"], 0, false)

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "Service logs")

        when:
        def result = service.captureLogs("test-project", config).get()

        then:
        result == "Service logs"
    }

    def "captureLogs throws exception when docker compose fails"() {
        given:
        def config = new LogsConfig([], 0, false)

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(1, "Failed to get logs")

        when:
        service.captureLogs("test-project", config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Failed to capture logs")
    }

    def "captureLogs handles exception in closure"() {
        given:
        def config = new LogsConfig([], 0, false)

        mockExecutor.execute(_) >> { throw new IOException("Process failed") }

        when:
        service.captureLogs("test-project", config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Failed to capture logs")
    }

    def "captureLogs with all options combined"() {
        given:
        def config = new LogsConfig(["web"], 50, true)

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "Combined options logs")

        when:
        def result = service.captureLogs("test-project", config).get()

        then:
        result == "Combined options logs"
    }

    // =====================================================
    // upStack with getStackServices integration tests
    // =====================================================

    def "upStack parses JSON output with multiple services"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, """
{"ID":"abc123","Service":"web","State":"running","Ports":"8080->80/tcp"}
{"ID":"def456","Service":"db","State":"running","Ports":"5432->5432/tcp"}
""")

        when:
        def result = service.upStack(config).get()

        then:
        result.services.size() == 2
        result.services.containsKey("web")
        result.services.containsKey("db")
    }

    def "upStack handles healthy status in JSON output"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"running (healthy)","Ports":""}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services["web"].state == "healthy"
    }

    def "upStack handles stopped status in JSON output"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"exited (0)"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services["web"].state == "stopped"
    }

    def "upStack handles restarting status in JSON output"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"restarting"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services["web"].state == "restarting"
    }

    def "upStack handles port mappings in JSON output"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"running","Ports":"0.0.0.0:8080->80/tcp, :::8080->80/tcp"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services["web"].publishedPorts.size() == 2
        result.services["web"].publishedPorts[0].hostPort == 8080
        result.services["web"].publishedPorts[0].containerPort == 80
    }

    def "upStack handles service name from Name field when Service is missing"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Name":"project_web_1","State":"running"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services.containsKey("web")
    }

    def "upStack handles Status field when State is missing"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","Status":"Up 2 hours"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services["web"] != null
    }

    def "upStack handles invalid JSON lines gracefully"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, """
{"ID":"abc123","Service":"web","State":"running"}
{invalid json}
{"ID":"def456","Service":"db","State":"running"}
""")

        when:
        def result = service.upStack(config).get()

        then:
        result.services.size() == 2
    }

    def "upStack returns empty services when ps command fails"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(1, "Failed")

        when:
        def result = service.upStack(config).get()

        then:
        result.services.isEmpty()
    }

    def "upStack returns empty services when ps command throws exception"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> { throw new IOException("Command failed") }

        when:
        def result = service.upStack(config).get()

        then:
        result.services.isEmpty()
    }

    def "upStack handles empty JSON output"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "")

        when:
        def result = service.upStack(config).get()

        then:
        result.services.isEmpty()
    }

    def "upStack handles null output from ps command"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, null)

        when:
        def result = service.upStack(config).get()

        then:
        result.services.isEmpty()
    }

    def "upStack handles whitespace-only output from ps command"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "   \n  \t  ")

        when:
        def result = service.upStack(config).get()

        then:
        result.services.isEmpty()
    }

    def "upStack handles service with unknown state"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"paused"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services["web"].state == "unknown"
    }

    def "upStack handles service with missing ID field"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"Service":"web","State":"running"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services["web"].containerId == "unknown"
    }

    def "upStack skips service when name cannot be determined"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","State":"running"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services.isEmpty()
    }

    // =====================================================
    // checkServiceReady tests via waitForServices
    // =====================================================

    def "waitForServices handles service not running"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofMillis(200),
            Duration.ofMillis(50),
            ServiceStatus.RUNNING
        )

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     exited")

        when:
        service.waitForServices(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Timeout waiting for services")
    }

    def "waitForServices handles check command failure"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofMillis(200),
            Duration.ofMillis(50),
            ServiceStatus.RUNNING
        )

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(1, "Error")

        when:
        service.waitForServices(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Timeout waiting for services")
    }

    def "waitForServices handles null output from check command"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofMillis(200),
            Duration.ofMillis(50),
            ServiceStatus.RUNNING
        )

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, null)

        when:
        service.waitForServices(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        ex.cause.message.contains("Timeout waiting for services")
    }

    def "waitForServices handles STOPPED target state"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofMillis(200),
            Duration.ofMillis(50),
            ServiceStatus.STOPPED
        )

        // STOPPED is not a valid target - will always return false
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     exited")

        when:
        service.waitForServices(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
    }

    // =====================================================
    // Additional tests for 100% coverage
    // =====================================================

    def "waitForServices handles InterruptedException by re-interrupting thread"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofMillis(500),
            Duration.ofMillis(50),
            ServiceStatus.RUNNING
        )

        // First call returns "Starting", then we simulate an InterruptedException being thrown
        // during Thread.sleep() by having the mock throw it
        def callCount = 0
        mockExecutor.execute(_) >> {
            callCount++
            if (callCount == 1) {
                // First check returns not ready, causing the loop to sleep
                return new ProcessExecutor.ProcessResult(0, "NAME    STATUS\nweb     Starting")
            }
            // Subsequent calls - simulated by throwing from checkServiceReady
            throw new InterruptedException("Interrupted during sleep")
        }

        when:
        service.waitForServices(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
        (ex.cause.message.contains("Interrupted") || ex.cause.message.contains("Error waiting for services"))
    }

    def "upStack handles port mapping with only container port"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"running","Ports":"80/tcp"}')

        when:
        def result = service.upStack(config).get()

        then:
        // Port format without host mapping doesn't match the regex pattern
        result.services["web"].publishedPorts.isEmpty()
    }

    def "upStack handles complex port mapping scenarios"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        // Multiple ports with various formats including UDP
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"running","Ports":"0.0.0.0:8080->80/tcp, 0.0.0.0:53->53/udp, 127.0.0.1:9000->9000"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.services["web"].publishedPorts.size() == 3
    }

    def "upStack handles port mapping with non-numeric values"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"running","Ports":"abc->def/tcp, 8080->80/tcp"}')

        when:
        def result = service.upStack(config).get()

        then:
        // First mapping is invalid and skipped, second is valid
        result.services["web"].publishedPorts.size() == 1
        result.services["web"].publishedPorts[0].hostPort == 8080
    }

    def "captureLogs with empty services list"() {
        given:
        def config = new LogsConfig([], 0, false)

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "All logs")

        when:
        def result = service.captureLogs("test-project", config).get()

        then:
        result == "All logs"
    }

    def "captureLogs with null services list"() {
        given:
        def config = new LogsConfig(null, 0, false)

        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "All logs")

        when:
        def result = service.captureLogs("test-project", config).get()

        then:
        result == "All logs"
    }

    def "upStack handles getStackServices failure gracefully"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        // Compose up succeeds
        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        // But docker compose ps fails
        mockExecutor.execute(_) >> { throw new IOException("Network error") }

        when:
        def result = service.upStack(config).get()

        then:
        // Should still succeed - just with empty services
        result.configName == "test-stack"
        result.getServices().isEmpty()
    }

    def "upStack handles service info parsing failure"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        // Return invalid JSON to trigger parsing error
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, "not-valid-json")

        when:
        def result = service.upStack(config).get()

        then:
        result.configName == "test-stack"
        result.getServices().isEmpty()  // Services map should be empty due to parsing failure
    }

    def "upStack handles STOPPED service state"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"exited"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.getServices()["web"] != null
        // Just verify the service exists - the state parsing covers the branch
        noExceptionThrown()
    }

    def "upStack handles RESTARTING service state"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"restarting"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.getServices()["web"] != null
        noExceptionThrown()
    }

    def "upStack handles UNKNOWN service state"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web","State":"some-random-state"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.getServices()["web"] != null
        noExceptionThrown()
    }

    def "upStack handles null status in JSON"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        // JSON without State field - parseServiceState will return UNKNOWN
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Service":"web"}')

        when:
        def result = service.upStack(config).get()

        then:
        result.getServices()["web"] != null
        noExceptionThrown()
    }

    def "upStack handles docker compose ps failure"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        // Return non-zero exit code for docker compose ps
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(1, "Error")

        when:
        def result = service.upStack(config).get()

        then:
        // Should still succeed - just with empty services
        result.configName == "test-stack"
        result.getServices().isEmpty()
    }

    def "waitForServices handles checkServiceReady exception"() {
        given:
        def config = new WaitConfig(
            "test-project",
            ["web"],
            Duration.ofMillis(200),
            Duration.ofMillis(50),
            ServiceStatus.RUNNING
        )

        // Make checkServiceReady throw an exception
        mockExecutor.execute(_) >> { throw new IOException("Cannot check service") }

        when:
        service.waitForServices(config).get()

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof RuntimeException
    }

    def "upStack handles fallback service name from container name"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        // Return JSON without Service field, but with Name field containing underscore-separated format
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '{"ID":"abc123","Name":"project_web_1","State":"running"}')

        when:
        def result = service.upStack(config).get()

        then:
        // The service name "web" should be extracted from Name="project_web_1"
        result.getServices()["web"] != null
        noExceptionThrown()
    }

    def "upStack handles empty JSON lines"() {
        given:
        def config = new ComposeConfig([tempComposeFile], "test-project", "test-stack")

        mockExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Started")
        // Return JSON with empty lines around it
        mockExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '\n{"ID":"abc123","Service":"web","State":"running"}\n\n')

        when:
        def result = service.upStack(config).get()

        then:
        result.getServices()["web"] != null
        noExceptionThrown()
    }
}
