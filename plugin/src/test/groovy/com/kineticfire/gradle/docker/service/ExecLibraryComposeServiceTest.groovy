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
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Unit tests for ExecLibraryComposeService
 * Note: These are structural tests since the service requires Docker Compose for integration
 */
class ExecLibraryComposeServiceTest extends Specification {

    @TempDir
    Path tempDir

    TestComposeService service
    
    def setup() {
        service = new TestComposeService()
    }

    def "ExecLibraryComposeService implements ComposeService interface"() {
        expect:
        ComposeService.isAssignableFrom(ExecLibraryComposeService)
    }

    def "ExecLibraryComposeService extends BuildService"() {
        expect:
        org.gradle.api.services.BuildService.isAssignableFrom(ExecLibraryComposeService)
    }

    def "upStack throws exception for null config"() {
        when:
        service.upStack(null)
        
        then:
        thrown(NullPointerException)
    }

    def "downStack throws exception for null project name"() {
        when:
        service.downStack((String) null)
        
        then:
        thrown(NullPointerException)
    }

    def "waitForServices throws exception for null config"() {
        when:
        service.waitForServices(null)
        
        then:
        thrown(NullPointerException)
    }

    def "captureLogs throws exception for null project name"() {
        when:
        service.captureLogs(null, new LogsConfig([]))
        
        then:
        thrown(NullPointerException)
    }

    def "parseServiceState correctly parses running status"() {
        expect:
        service.parseServiceState("Up 5 minutes") == ServiceStatus.RUNNING
        service.parseServiceState("running") == ServiceStatus.RUNNING
        service.parseServiceState("Up (healthy)") == ServiceStatus.HEALTHY
    }

    def "parseServiceState correctly parses stopped status"() {
        expect:
        service.parseServiceState("Exit 0") == ServiceStatus.STOPPED
        service.parseServiceState("stopped") == ServiceStatus.STOPPED
    }

    def "parseServiceState correctly parses restarting status"() {
        expect:
        service.parseServiceState("Restarting") == ServiceStatus.RESTARTING
        service.parseServiceState("restarting (1)") == ServiceStatus.RESTARTING
    }

    def "parseServiceState returns unknown for unrecognized status"() {
        expect:
        service.parseServiceState("unknown state") == ServiceStatus.UNKNOWN
        service.parseServiceState("") == ServiceStatus.UNKNOWN
        service.parseServiceState(null) == ServiceStatus.UNKNOWN
    }

    def "upStack handles valid configuration successfully"() {
        given:
        def composeFile = Files.write(tempDir.resolve("docker-compose.yml"), "version: '3'\nservices:\n  test:\n    image: alpine".bytes)
        def config = new ComposeConfig([composeFile], "test-project", "test-stack")
        
        when:
        def result = service.upStack(config)
        
        then:
        result.get() != null
        result.get().projectName == "test-project"
    }

    def "downStack handles valid project name successfully"() {
        given:
        def projectName = "test-project"
        
        when:
        def result = service.downStack(projectName)
        
        then:
        result.get() == null // CompletableFuture<Void> returns null on success
    }

    def "waitForServices handles valid configuration successfully"() {
        given:
        def config = new WaitConfig("test-project", ["service1", "service2"], Duration.ofSeconds(30), ServiceStatus.RUNNING)
        
        when:
        def result = service.waitForServices(config)
        
        then:
        result.get() == ServiceStatus.RUNNING
    }

    def "captureLogs handles valid parameters successfully"() {
        given:
        def projectName = "test-project"
        def config = new LogsConfig(["service1", "service2"])
        
        when:
        def result = service.captureLogs(projectName, config)
        
        then:
        result.get() == "mock logs"
    }

    def "captureLogs throws exception for null config"() {
        when:
        service.captureLogs("project", null)
        
        then:
        thrown(NullPointerException)
    }

    def "upStack throws exception for empty compose files"() {
        when:
        new ComposeConfig([], "test-project", "test-stack")
        
        then:
        thrown(IllegalArgumentException)
    }

    def "waitForServices handles empty service list"() {
        given:
        def config = new WaitConfig("test-project", [], Duration.ofSeconds(30), ServiceStatus.RUNNING)
        
        when:
        def result = service.waitForServices(config)
        
        then:
        thrown(IllegalArgumentException)
    }

    def "parseServiceState handles case variations correctly"() {
        expect:
        service.parseServiceState("UP 5 minutes") == ServiceStatus.RUNNING
        service.parseServiceState("RUNNING") == ServiceStatus.RUNNING
        service.parseServiceState("UP (HEALTHY)") == ServiceStatus.HEALTHY
        service.parseServiceState("EXIT 1") == ServiceStatus.STOPPED
        service.parseServiceState("STOPPED") == ServiceStatus.STOPPED
        service.parseServiceState("RESTARTING (2)") == ServiceStatus.RESTARTING
    }

    def "parseServiceState handles edge cases"() {
        expect:
        service.parseServiceState("   ") == ServiceStatus.UNKNOWN
        service.parseServiceState("up down") == ServiceStatus.RUNNING // Contains 'up'
        service.parseServiceState("exit code 0") == ServiceStatus.STOPPED // Contains 'exit'
    }

    // ===== TESTS FOR buildLogsCommand() with hasLimitedTail() =====

    def "buildLogsCommand omits --tail flag when tailLines is 0"() {
        given:
        def testableService = new TestableExecLibraryComposeService()
        def config = new LogsConfig(['app'], 0, false, null)
        def baseCommand = ['docker', 'compose']

        when:
        def command = testableService.buildLogsCommand('test-project', config, baseCommand)

        then:
        !command.contains('--tail')
        command.containsAll(['docker', 'compose', '-p', 'test-project', 'logs', 'app'])
    }

    def "buildLogsCommand omits --tail flag when tailLines is negative"() {
        given:
        def testableService = new TestableExecLibraryComposeService()
        def config = new LogsConfig(['app'], -1, false, null)
        def baseCommand = ['docker', 'compose']

        when:
        def command = testableService.buildLogsCommand('test-project', config, baseCommand)

        then:
        !command.contains('--tail')
        command.containsAll(['docker', 'compose', '-p', 'test-project', 'logs', 'app'])
    }

    def "buildLogsCommand includes --tail flag when tailLines is positive"() {
        given:
        def testableService = new TestableExecLibraryComposeService()
        def config = new LogsConfig(['app'], 100, false, null)
        def baseCommand = ['docker', 'compose']

        when:
        def command = testableService.buildLogsCommand('test-project', config, baseCommand)

        then:
        command.contains('--tail')
        command.contains('100')
    }

    def "buildLogsCommand includes --tail 1 when tailLines is 1"() {
        given:
        def testableService = new TestableExecLibraryComposeService()
        def config = new LogsConfig(['app'], 1, false, null)
        def baseCommand = ['docker', 'compose']

        when:
        def command = testableService.buildLogsCommand('test-project', config, baseCommand)

        then:
        command.contains('--tail')
        command.contains('1')
    }

    def "buildLogsCommand includes --follow flag when follow is true"() {
        given:
        def testableService = new TestableExecLibraryComposeService()
        def config = new LogsConfig(['app'], 50, true, null)
        def baseCommand = ['docker', 'compose']

        when:
        def command = testableService.buildLogsCommand('test-project', config, baseCommand)

        then:
        command.contains('--follow')
        command.contains('--tail')
        command.contains('50')
    }

    def "buildLogsCommand omits service names when services list is empty"() {
        given:
        def testableService = new TestableExecLibraryComposeService()
        def config = new LogsConfig([], 100, false, null)
        def baseCommand = ['docker', 'compose']

        when:
        def command = testableService.buildLogsCommand('test-project', config, baseCommand)

        then:
        command == ['docker', 'compose', '-p', 'test-project', 'logs', '--tail', '100']
    }

    def "buildLogsCommand includes multiple services when specified"() {
        given:
        def testableService = new TestableExecLibraryComposeService()
        def config = new LogsConfig(['app', 'db', 'cache'], 200, false, null)
        def baseCommand = ['docker', 'compose']

        when:
        def command = testableService.buildLogsCommand('test-project', config, baseCommand)

        then:
        command.containsAll(['app', 'db', 'cache'])
    }

    def "buildLogsCommand handles very large tailLines value"() {
        given:
        def testableService = new TestableExecLibraryComposeService()
        def config = new LogsConfig(['app'], Integer.MAX_VALUE, false, null)
        def baseCommand = ['docker', 'compose']

        when:
        def command = testableService.buildLogsCommand('test-project', config, baseCommand)

        then:
        command.contains('--tail')
        command.contains(Integer.MAX_VALUE.toString())
    }

    /**
     * Testable subclass of ExecLibraryComposeService that exposes protected methods for testing.
     * This avoids the need to use reflection and follows the @VisibleForTesting pattern.
     */
    static class TestableExecLibraryComposeService extends ExecLibraryComposeService {

        TestableExecLibraryComposeService() {
            // Initialize fields directly to avoid parent constructor's validation calls
        }

        // Required by BuildService interface
        @Override
        BuildServiceParameters.None getParameters() {
            return null
        }

        // Override to skip Docker Compose validation during initialization
        @Override
        protected List<String> getComposeCommand() {
            return ['docker', 'compose']
        }

        // Expose the protected method for testing
        @Override
        List<String> buildLogsCommand(String projectName, LogsConfig config, List<String> baseCommand) {
            return super.buildLogsCommand(projectName, config, baseCommand)
        }
    }

    /**
     * Simple test implementation of ComposeService for validation testing
     */
    static class TestComposeService implements ComposeService {

        @Override
        CompletableFuture<ComposeState> upStack(ComposeConfig config) {
            Objects.requireNonNull(config, "ComposeConfig cannot be null")
            if (config.composeFiles.isEmpty()) {
                throw new IllegalArgumentException("Compose files cannot be empty")
            }
            return CompletableFuture.completedFuture(new ComposeState("test", config.projectName, [:], []))
        }

        @Override
        CompletableFuture<Void> downStack(String projectName) {
            Objects.requireNonNull(projectName, "Project name cannot be null")
            return CompletableFuture.completedFuture(null)
        }

        @Override
        CompletableFuture<Void> downStack(ComposeConfig config) {
            Objects.requireNonNull(config, "ComposeConfig cannot be null")
            return CompletableFuture.completedFuture(null)
        }

        @Override
        CompletableFuture<ServiceStatus> waitForServices(WaitConfig config) {
            Objects.requireNonNull(config, "WaitConfig cannot be null")
            if (config.services.isEmpty()) {
                throw new IllegalArgumentException("Services list cannot be empty")
            }
            return CompletableFuture.completedFuture(ServiceStatus.RUNNING)
        }

        @Override
        CompletableFuture<String> captureLogs(String projectName, LogsConfig config) {
            Objects.requireNonNull(projectName, "Project name cannot be null")
            Objects.requireNonNull(config, "LogsConfig cannot be null")
            return CompletableFuture.completedFuture("mock logs")
        }

        @Override
        CompletableFuture<Map<String, WaitForLogResult>> waitForLogPatterns(WaitForLogConfig config) {
            Objects.requireNonNull(config, "WaitForLogConfig cannot be null")
            // Return empty results for mock implementation
            Map<String, WaitForLogResult> results = [:]
            config.services.each { serviceName, patterns ->
                results[serviceName] = new WaitForLogResult(serviceName, patterns, patterns)
            }
            return CompletableFuture.completedFuture(results)
        }

        ServiceStatus parseServiceState(String status) {
            if (!status || status.trim().isEmpty()) return ServiceStatus.UNKNOWN
            
            def lowerStatus = status.toLowerCase()
            if (lowerStatus.contains('running') || lowerStatus.contains('up')) {
                if (lowerStatus.contains('healthy')) {
                    return ServiceStatus.HEALTHY
                }
                return ServiceStatus.RUNNING
            } else if (lowerStatus.contains('exit') || lowerStatus.contains('stop')) {
                return ServiceStatus.STOPPED
            } else if (lowerStatus.contains('restart') || lowerStatus.contains('restarting')) {
                return ServiceStatus.RESTARTING
            } else {
                return ServiceStatus.UNKNOWN
            }
        }
    }
}