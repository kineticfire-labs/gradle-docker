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
import com.kineticfire.gradle.docker.model.WaitForLogConfig
import com.kineticfire.gradle.docker.model.WaitForLogResult
import com.kineticfire.gradle.docker.util.LogPatternMatcher
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.regex.Pattern

/**
 * Unit tests for ExecLibraryComposeService.waitForLogPatterns() and related methods
 */
class ExecLibraryComposeServiceWaitForLogTest extends Specification {

    ProcessExecutor mockProcessExecutor
    CommandValidator mockCommandValidator
    ServiceLogger mockServiceLogger
    TimeService mockTimeService
    TestableWaitForLogService service

    def setup() {
        mockProcessExecutor = Mock(ProcessExecutor)
        mockCommandValidator = Mock(CommandValidator)
        mockServiceLogger = Mock(ServiceLogger)
        mockTimeService = Mock(TimeService)

        // Default command validator behavior
        mockCommandValidator.detectComposeCommand() >> ['docker', 'compose']
        mockCommandValidator.validateDockerCompose() >> null

        service = new TestableWaitForLogService(
            mockProcessExecutor, mockCommandValidator, mockServiceLogger, mockTimeService
        )
    }

    // ===== waitForLogPatterns() NULL VALIDATION TESTS (Gap #13) =====

    def "waitForLogPatterns throws NullPointerException when config is null"() {
        when:
        service.waitForLogPatterns(null)

        then:
        thrown(NullPointerException)
    }

    // ===== getComposeProjectServices() TESTS (Gap #1, JSON format) =====

    def "getComposeProjectServices parses JSON output correctly"() {
        given:
        def jsonOutput = '{"Service":"app","State":"running"}\n{"Service":"db","State":"running"}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, '')

        when:
        def services = service.getComposeProjectServices('test-project')

        then:
        services.contains('app')
        services.contains('db')
        services.size() == 2
    }

    def "getComposeProjectServices handles single JSON line output"() {
        given:
        def jsonOutput = '{"Service":"web","State":"running"}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, '')

        when:
        def services = service.getComposeProjectServices('test-project')

        then:
        services.contains('web')
        services.size() == 1
    }

    def "getComposeProjectServices handles empty JSON output"() {
        given:
        mockProcessExecutor.execute(_) >> new ProcessResult(0, '', '')

        when:
        def services = service.getComposeProjectServices('test-project')

        then:
        services.isEmpty()
    }

    def "getComposeProjectServices handles malformed JSON lines gracefully"() {
        given:
        def mixedOutput = '{"Service":"app","State":"running"}\nnot-json\n{"Service":"db","State":"running"}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, mixedOutput, '')

        when:
        def services = service.getComposeProjectServices('test-project')

        then:
        // Should still parse the valid lines
        services.contains('app')
        services.contains('db')
    }

    def "getComposeProjectServices returns empty set on command failure"() {
        given:
        mockProcessExecutor.execute(_) >> new ProcessResult(1, '', 'error')

        when:
        def services = service.getComposeProjectServices('test-project')

        then:
        services.isEmpty()
    }

    def "getComposeProjectServices handles exception gracefully"() {
        given:
        mockProcessExecutor.execute(_) >> { throw new RuntimeException("Network error") }

        when:
        def services = service.getComposeProjectServices('test-project')

        then:
        services.isEmpty()
        1 * mockServiceLogger.debug(_)
    }

    // ===== validateServicesExist() TESTS (Gap #2, #11) =====

    def "validateServicesExist throws exception for empty compose project"() {
        given:
        def config = createConfig(['app': [Pattern.compile('Started')]], 'empty-project')
        mockProcessExecutor.execute(_) >> new ProcessResult(0, '', '')

        when:
        service.validateServicesExist(config)

        then:
        def e = thrown(ComposeServiceException)
        e.message.contains("No services found in compose project 'empty-project'")
        e.message.contains("compose project doesn't exist")
    }

    def "validateServicesExist throws exception for missing service"() {
        given:
        def config = createConfig(['app': [Pattern.compile('Started')], 'missing': [Pattern.compile('Ready')]], 'proj')
        def jsonOutput = '{"Service":"app","State":"running"}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, '')

        when:
        service.validateServicesExist(config)

        then:
        def e = thrown(ComposeServiceException)
        e.message.contains("Service(s) not found")
        e.message.contains("missing")
        e.message.contains("Available services")
    }

    def "validateServicesExist passes when all services exist"() {
        given:
        def config = createConfig(['app': [Pattern.compile('Started')]], 'proj')
        def jsonOutput = '{"Service":"app","State":"running"}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, '')

        when:
        service.validateServicesExist(config)

        then:
        noExceptionThrown()
    }

    // ===== fetchServiceLogs() TESTS (Gap #4) =====

    def "fetchServiceLogs returns empty list for null output"() {
        given:
        mockProcessExecutor.execute(_) >> new ProcessResult(0, null, '')

        when:
        def logs = service.fetchServiceLogs('proj', 'app')

        then:
        logs == []
    }

    def "fetchServiceLogs returns empty list for empty output"() {
        given:
        mockProcessExecutor.execute(_) >> new ProcessResult(0, '', '')

        when:
        def logs = service.fetchServiceLogs('proj', 'app')

        then:
        logs == []
    }

    def "fetchServiceLogs returns empty list for whitespace-only output"() {
        given:
        mockProcessExecutor.execute(_) >> new ProcessResult(0, '   \n  \t  ', '')

        when:
        def logs = service.fetchServiceLogs('proj', 'app')

        then:
        logs == []
    }

    def "fetchServiceLogs parses log lines correctly"() {
        given:
        def logOutput = "Line 1\nLine 2\nLine 3"
        mockProcessExecutor.execute(_) >> new ProcessResult(0, logOutput, '')

        when:
        def logs = service.fetchServiceLogs('proj', 'app')

        then:
        logs.size() == 3
        logs == ['Line 1', 'Line 2', 'Line 3']
    }

    // ===== isServiceRunning() TESTS (Gap #1 - JSON format) =====

    def "isServiceRunning returns true when service is running (JSON format)"() {
        given:
        def jsonOutput = '{"Service":"app","State":"running"}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, '')

        expect:
        service.isServiceRunning('proj', 'app')
    }

    def "isServiceRunning returns false when service is not running (JSON format)"() {
        given:
        def jsonOutput = '{"Service":"app","State":"exited"}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, '')

        expect:
        !service.isServiceRunning('proj', 'app')
    }

    def "isServiceRunning returns false on parse error and logs warning"() {
        given:
        def invalidJson = 'not-valid-json'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, invalidJson, '')

        when:
        def result = service.isServiceRunning('proj', 'app')

        then:
        !result
        // The warn method is called with a single argument (the message string)
        1 * mockServiceLogger.warn({ it.contains('Failed to parse') })
    }

    def "isServiceRunning returns false on command failure"() {
        given:
        mockProcessExecutor.execute(_) >> new ProcessResult(1, '', 'error')

        expect:
        !service.isServiceRunning('proj', 'app')
    }

    def "isServiceRunning handles exception gracefully"() {
        given:
        mockProcessExecutor.execute(_) >> { throw new RuntimeException("Connection error") }

        expect:
        !service.isServiceRunning('proj', 'app')
    }

    // ===== getServiceExitCode() TESTS (Gap #3) =====

    def "getServiceExitCode returns exit code from JSON output"() {
        given:
        def jsonOutput = '{"Service":"app","State":"exited","ExitCode":137}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, '')

        expect:
        service.getServiceExitCode('proj', 'app') == 137
    }

    def "getServiceExitCode returns zero exit code"() {
        given:
        def jsonOutput = '{"Service":"app","State":"exited","ExitCode":0}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, '')

        expect:
        service.getServiceExitCode('proj', 'app') == 0
    }

    def "getServiceExitCode returns null when no exit code in output"() {
        given:
        def jsonOutput = '{"Service":"app","State":"running"}'
        mockProcessExecutor.execute(_) >> new ProcessResult(0, jsonOutput, '')

        expect:
        service.getServiceExitCode('proj', 'app') == null
    }

    def "getServiceExitCode returns null on command failure"() {
        given:
        mockProcessExecutor.execute(_) >> new ProcessResult(1, '', 'error')

        expect:
        service.getServiceExitCode('proj', 'app') == null
    }

    def "getServiceExitCode returns null on exception"() {
        given:
        mockProcessExecutor.execute(_) >> { throw new RuntimeException("Error") }

        expect:
        service.getServiceExitCode('proj', 'app') == null
    }

    // ===== getRecentLogs() TESTS (Gap #10, #22) =====

    def "getRecentLogs returns recent log lines"() {
        given:
        def logOutput = "Line 1\nLine 2\nLine 3"
        mockProcessExecutor.execute(_) >> new ProcessResult(0, logOutput, '')

        when:
        def logs = service.getRecentLogs('proj', 'app', 10)

        then:
        logs == ['Line 1', 'Line 2', 'Line 3']
    }

    def "getRecentLogs returns empty list on failure"() {
        given:
        mockProcessExecutor.execute(_) >> new ProcessResult(0, '', '')

        when:
        def logs = service.getRecentLogs('proj', 'app', 10)

        then:
        logs == []
    }

    // ===== CheckAllServicesResult TESTS (Gap #12) =====

    def "CheckAllServicesResult.ok() creates non-rejected, non-crashed result"() {
        when:
        def result = ExecLibraryComposeService.CheckAllServicesResult.ok()

        then:
        !result.rejected
        !result.crashed
        result.serviceName == null
        result.rejectResult == null
        result.exitCode == null
    }

    def "CheckAllServicesResult.rejected() creates rejected result"() {
        given:
        def rejectResult = new LogPatternMatcher.RejectCheckResult('Error.*', 'Error: failed')

        when:
        def result = ExecLibraryComposeService.CheckAllServicesResult.rejected('app', rejectResult)

        then:
        result.rejected
        !result.crashed
        result.serviceName == 'app'
        result.rejectResult == rejectResult
    }

    def "CheckAllServicesResult.crashed() creates crashed result"() {
        when:
        def result = ExecLibraryComposeService.CheckAllServicesResult.crashed('db', 137)

        then:
        !result.rejected
        result.crashed
        result.serviceName == 'db'
        result.exitCode == 137
    }

    // ===== checkAllServices() TESTS =====

    def "checkAllServices returns ok when service is running and no patterns match reject"() {
        given:
        def config = createConfig(['app': [Pattern.compile('Started')]], 'proj')
        def matchedPatterns = ['app': new HashSet<Integer>()]
        def matchTimes = ['app': new HashMap<Integer, Long>()]

        // Service is running
        mockProcessExecutor.execute({ it.contains('ps') && it.contains('app') }) >>
            new ProcessResult(0, '{"Service":"app","State":"running"}', '')
        // Logs
        mockProcessExecutor.execute({ it.contains('logs') }) >>
            new ProcessResult(0, 'Some log output', '')

        when:
        def result = service.checkAllServices(config, matchedPatterns, matchTimes, 5)

        then:
        !result.rejected
        !result.crashed
    }

    def "checkAllServices returns crashed when service is not running"() {
        given:
        def config = createConfig(['app': [Pattern.compile('Started')]], 'proj')
        def matchedPatterns = ['app': new HashSet<Integer>()]
        def matchTimes = ['app': new HashMap<Integer, Long>()]

        // Service is NOT running
        mockProcessExecutor.execute({ it.contains('ps') && it.contains('app') }) >>
            new ProcessResult(0, '{"Service":"app","State":"exited","ExitCode":1}', '')

        when:
        def result = service.checkAllServices(config, matchedPatterns, matchTimes, 5)

        then:
        !result.rejected
        result.crashed
        result.serviceName == 'app'
        result.exitCode == 1
    }

    def "checkAllServices returns rejected when reject pattern matches"() {
        given:
        def servicePatterns = ['app': [Pattern.compile('Started')]]
        def rejectPatterns = ['app': [Pattern.compile('Error')]]
        def config = createConfigWithReject(servicePatterns, rejectPatterns, 'proj')
        def matchedPatterns = ['app': new HashSet<Integer>()]
        def matchTimes = ['app': new HashMap<Integer, Long>()]

        // Service is running
        mockProcessExecutor.execute({ it.contains('ps') && it.contains('app') }) >>
            new ProcessResult(0, '{"Service":"app","State":"running"}', '')
        // Logs contain error
        mockProcessExecutor.execute({ it.contains('logs') }) >>
            new ProcessResult(0, 'Error: something failed', '')

        when:
        def result = service.checkAllServices(config, matchedPatterns, matchTimes, 5)

        then:
        result.rejected
        !result.crashed
        result.serviceName == 'app'
        result.rejectResult.patternString == 'Error'
    }

    // ===== InterruptedException HANDLING (Gap #14) =====

    def "waitForLogPatterns handles InterruptedException by preserving interrupt status"() {
        given:
        def config = createConfig(['app': [Pattern.compile('Started')]], 'proj')
        // Set up service to throw InterruptedException during execution
        service.throwInterruptedExceptionOnExecute = true

        // Service exists
        mockProcessExecutor.execute({ it.contains('ps') && it.contains('-a') }) >>
            new ProcessResult(0, '{"Service":"app","State":"running"}', '')

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof ComposeServiceException
        e.cause.message.contains("Interrupted")
    }

    // ===== ExecutionException UNWRAPPING (Gap #17) =====

    def "waitForLogPatterns unwraps ExecutionException cause"() {
        given:
        def config = createConfig(['app': [Pattern.compile('Started')]], 'proj')
        // Set up to throw ExecutionException
        service.throwExecutionExceptionOnExecute = true

        // Service exists
        mockProcessExecutor.execute({ it.contains('ps') && it.contains('-a') }) >>
            new ProcessResult(0, '{"Service":"app","State":"running"}', '')

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof ComposeServiceException
    }

    // ===== SUCCESSFUL WAIT FOR LOG PATTERNS =====

    def "waitForLogPatterns succeeds when pattern is found"() {
        given:
        def config = createConfig(['app': [Pattern.compile('Started')]], 'proj')

        // Time service returns increasing time
        def currentTime = 0L
        mockTimeService.currentTimeMillis() >> { currentTime }
        mockTimeService.sleep(_) >> { currentTime += 1000 }

        // Service exists
        mockProcessExecutor.execute({ it.contains('ps') && it.contains('-a') }) >>
            new ProcessResult(0, '{"Service":"app","State":"running"}', '')
        // Service is running
        mockProcessExecutor.execute({ it.contains('ps') && it.contains('app') && !it.contains('-a') }) >>
            new ProcessResult(0, '{"Service":"app","State":"running"}', '')
        // Logs contain pattern
        mockProcessExecutor.execute({ it.contains('logs') }) >>
            new ProcessResult(0, 'Application Started successfully', '')

        when:
        def future = service.waitForLogPatterns(config)
        def results = future.get()

        then:
        results.size() == 1
        results['app'].ready
        results['app'].matchedCount == 1
    }

    def "waitForLogPatterns times out when pattern is not found"() {
        given:
        def config = createConfigWithTimeout(['app': [Pattern.compile('NeverFound')]], 'proj', 1, 1)

        // Time service simulates timeout
        def currentTime = 0L
        mockTimeService.currentTimeMillis() >> { currentTime += 2000; currentTime }

        // Service exists
        mockProcessExecutor.execute({ it.contains('ps') && it.contains('-a') }) >>
            new ProcessResult(0, '{"Service":"app","State":"running"}', '')
        // Service is running
        mockProcessExecutor.execute({ it.contains('ps') && it.contains('app') && !it.contains('-a') }) >>
            new ProcessResult(0, '{"Service":"app","State":"running"}', '')
        // Logs don't contain pattern
        mockProcessExecutor.execute({ it.contains('logs') }) >>
            new ProcessResult(0, 'Normal output', '')

        when:
        def future = service.waitForLogPatterns(config)
        future.get()

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof ComposeServiceException
        e.cause.errorType == ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT
    }

    // ===== HELPER METHODS =====

    private WaitForLogConfig createConfig(Map<String, List<Pattern>> servicePatterns, String projectName) {
        return new WaitForLogConfig(
            projectName,
            servicePatterns,
            [:],
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            false,
            false,
            Duration.ZERO
        )
    }

    private WaitForLogConfig createConfigWithReject(
            Map<String, List<Pattern>> servicePatterns,
            Map<String, List<Pattern>> rejectPatterns,
            String projectName) {
        return new WaitForLogConfig(
            projectName,
            servicePatterns,
            rejectPatterns,
            Duration.ofSeconds(60),
            Duration.ofSeconds(2),
            false,
            false,
            Duration.ZERO
        )
    }

    private WaitForLogConfig createConfigWithTimeout(
            Map<String, List<Pattern>> servicePatterns,
            String projectName,
            int timeoutSeconds,
            int pollSeconds) {
        return new WaitForLogConfig(
            projectName,
            servicePatterns,
            [:],
            Duration.ofSeconds(timeoutSeconds),
            Duration.ofSeconds(pollSeconds),
            false,
            false,
            Duration.ZERO
        )
    }

    /**
     * Testable subclass that exposes protected methods and allows exception injection
     */
    static class TestableWaitForLogService extends ExecLibraryComposeService {

        boolean throwInterruptedExceptionOnExecute = false
        boolean throwExecutionExceptionOnExecute = false

        TestableWaitForLogService(
                ProcessExecutor processExecutor,
                CommandValidator commandValidator,
                ServiceLogger serviceLogger,
                TimeService timeService) {
            super(processExecutor, commandValidator, serviceLogger, timeService)
        }

        @Override
        org.gradle.api.services.BuildServiceParameters.None getParameters() {
            // Return null since ExecLibraryComposeService uses BuildServiceParameters.None
            return null
        }

        @Override
        protected List<String> getComposeCommand() {
            return ['docker', 'compose']
        }

        @Override
        protected Map<String, WaitForLogResult> executeWaitForLogPatterns(WaitForLogConfig config) {
            if (throwInterruptedExceptionOnExecute) {
                throw new InterruptedException("Test interrupt")
            }
            if (throwExecutionExceptionOnExecute) {
                throw new java.util.concurrent.ExecutionException(
                    "Test execution error",
                    new RuntimeException("Wrapped error")
                )
            }
            return super.executeWaitForLogPatterns(config)
        }
    }
}
