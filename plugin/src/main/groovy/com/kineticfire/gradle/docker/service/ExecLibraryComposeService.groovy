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
import com.kineticfire.gradle.docker.util.ComposeOutputParser
import com.kineticfire.gradle.docker.util.LogPatternMatcher
import com.google.common.annotations.VisibleForTesting
import groovy.json.JsonSlurper
import groovy.json.JsonException
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import javax.inject.Inject
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Real implementation of Docker Compose service using process execution
 */
abstract class ExecLibraryComposeService implements BuildService<BuildServiceParameters.None>, ComposeService {

    /**
     * Number of recent log lines to include in error messages for diagnostics.
     */
    private static final int RECENT_LOG_LINES_FOR_ERROR = 10

    protected ProcessExecutor processExecutor
    protected CommandValidator commandValidator
    protected ServiceLogger serviceLogger
    protected TimeService timeService
    
    @Inject
    ExecLibraryComposeService() {
        this(new DefaultProcessExecutor(), 
             new DefaultCommandValidator(new DefaultProcessExecutor()), 
             new DefaultServiceLogger(ExecLibraryComposeService.class),
             new SystemTimeService())
    }
    
    @VisibleForTesting
    ExecLibraryComposeService(ProcessExecutor processExecutor, 
                              CommandValidator commandValidator, 
                              ServiceLogger serviceLogger,
                              TimeService timeService) {
        this.processExecutor = processExecutor
        this.commandValidator = commandValidator
        this.serviceLogger = serviceLogger
        this.timeService = timeService
        
        commandValidator.validateDockerCompose()
        serviceLogger.info("ComposeService initialized with docker-compose CLI")
    }
    
    protected List<String> getComposeCommand() {
        return commandValidator.detectComposeCommand()
    }

    /**
     * Pure function - Build docker compose up command from configuration.
     * 100% unit testable with no external dependencies.
     *
     * @param config Compose configuration
     * @param baseCommand Base compose command (e.g., ['docker', 'compose'])
     * @return Complete command list ready for execution
     */
    @VisibleForTesting
    protected List<String> buildUpCommand(ComposeConfig config, List<String> baseCommand) {
        def command = baseCommand.clone()

        // Add compose files
        config.composeFiles.each { file ->
            command.addAll(["-f", file.toString()])
        }

        // Add project name
        command.addAll(["-p", config.projectName])

        // Add env files
        config.envFiles.each { envFile ->
            command.addAll(["--env-file", envFile.toString()])
        }

        // Add the up command
        command.addAll(["up", "-d"])

        return command
    }

    @Override
    CompletableFuture<ComposeState> upStack(ComposeConfig config) {
        if (config == null) {
            throw new NullPointerException("Compose config cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                serviceLogger.info("Starting Docker Compose stack: ${config.stackName}")

                // Build command (pure logic - extracted)
                def composeCommand = getComposeCommand()
                def command = buildUpCommand(config, composeCommand)

                serviceLogger.debug("Executing: ${command.join(' ')}")

                // Execute command (external call - keep minimal)
                def workingDir = config.composeFiles.first().parent.toFile()
                def result = processExecutor.execute(command, workingDir)
                
                if (!result.isSuccess()) {
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                        "Docker Compose up failed with exit code ${result.exitCode}: ${result.stderr}",
                        "Check your compose file syntax and service configurations"
                    )
                }
                
                // Get current stack state
                def services = getStackServices(config.projectName)
                
                def composeState = new ComposeState(
                    config.stackName,
                    config.projectName,
                    services
                )
                
                serviceLogger.info("Docker Compose stack started: ${config.stackName}")
                return composeState
                
            } catch (ComposeServiceException e) {
                throw e
            } catch (Exception e) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                    "Failed to start compose stack: ${e.message}",
                    e
                )
            }
        })
    }
    
    protected Map<String, ServiceInfo> getStackServices(String projectName) {
        try {
            def composeCommand = getComposeCommand()
            def command = composeCommand + ["-p", projectName, "ps", "--format", "json"]

            def result = processExecutor.execute(command)

            if (!result.isSuccess()) {
                serviceLogger.warn("Failed to get stack services for project ${projectName}: ${result.stderr}")
                return [:]
            }

            // Delegate parsing to ComposeOutputParser utility
            return ComposeOutputParser.parseServicesJson(result.stdout)

        } catch (Exception e) {
            serviceLogger.warn("Error getting stack services: ${e.message}")
            return [:]
        }
    }
    
    // Parsing methods removed - now using ComposeOutputParser utility
    // parseServiceState() -> ComposeOutputParser.parseServiceState()
    // parsePortMappings() -> ComposeOutputParser.parsePortMappings()

    @Override
    CompletableFuture<Void> downStack(String projectName) {
        if (projectName == null) {
            throw new NullPointerException("Project name cannot be null")
        }
        return CompletableFuture.runAsync({
            try {
                serviceLogger.info("Stopping Docker Compose stack: ${projectName}")
                
                def composeCommand = getComposeCommand()
                def command = composeCommand + ["-p", projectName, "down", "--remove-orphans"]
                
                serviceLogger.debug("Executing: ${command.join(' ')}")
                
                def result = processExecutor.execute(command)
                
                if (!result.isSuccess()) {
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                        "Docker Compose down failed with exit code ${result.exitCode}: ${result.stderr}",
                        "Check if the project exists and is accessible"
                    )
                }
                
                serviceLogger.info("Docker Compose stack stopped: ${projectName}")
                
            } catch (ComposeServiceException e) {
                throw e
            } catch (Exception e) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                    "Failed to stop compose stack: ${e.message}",
                    e
                )
            }
        })
    }
    
    /**
     * Pure function - Build docker compose down command from configuration.
     * 100% unit testable with no external dependencies.
     *
     * @param config Compose configuration
     * @param baseCommand Base compose command (e.g., ['docker', 'compose'])
     * @return Complete command list ready for execution
     */
    @VisibleForTesting
    protected List<String> buildDownCommand(ComposeConfig config, List<String> baseCommand) {
        def command = baseCommand.clone()

        // Add compose files for proper teardown
        config.composeFiles.each { file ->
            command.addAll(["-f", file.toString()])
        }

        // Add project name
        command.addAll(["-p", config.projectName])

        // Add env files if present
        config.envFiles.each { envFile ->
            command.addAll(["--env-file", envFile.toString()])
        }

        // Add the down command
        command.addAll(["down", "--remove-orphans"])

        return command
    }

    @Override
    CompletableFuture<Void> downStack(ComposeConfig config) {
        if (config == null) {
            throw new NullPointerException("Compose config cannot be null")
        }
        return CompletableFuture.runAsync({
            try {
                serviceLogger.info("Stopping Docker Compose stack: ${config.stackName} (project: ${config.projectName})")

                // Build command (pure logic - extracted)
                def composeCommand = getComposeCommand()
                def command = buildDownCommand(config, composeCommand)

                serviceLogger.debug("Executing: ${command.join(' ')}")

                // Execute command (external call - keep minimal)
                def workingDir = config.composeFiles.first().parent.toFile()
                def result = processExecutor.execute(command, workingDir)
                
                if (!result.isSuccess()) {
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                        "Docker Compose down failed with exit code ${result.exitCode}: ${result.stderr}",
                        "Check your compose file syntax and project configuration"
                    )
                }
                
                serviceLogger.info("Docker Compose stack stopped: ${config.stackName} (project: ${config.projectName})")
                
            } catch (ComposeServiceException e) {
                throw e
            } catch (Exception e) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                    "Failed to stop compose stack: ${e.message}",
                    e
                )
            }
        })
    }
    
    @Override
    CompletableFuture<ServiceStatus> waitForServices(WaitConfig config) {
        if (config == null) {
            throw new NullPointerException("Wait config cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                serviceLogger.info("Waiting for services: ${config.services}")
                
                def startTime = timeService.currentTimeMillis()
                def timeoutMillis = config.timeout.toMillis()
                
                while (timeService.currentTimeMillis() - startTime < timeoutMillis) {
                    // Check service states
                    def allReady = true
                    for (serviceName in config.services) {
                        def serviceReady = checkServiceReady(config.projectName, serviceName, config.targetState)
                        if (!serviceReady) {
                            allReady = false
                            break
                        }
                    }
                    
                    if (allReady) {
                        serviceLogger.info("All services are ready: ${config.services}")
                        return config.targetState
                    }
                    
                    timeService.sleep(config.pollInterval.toMillis())
                }
                
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_TIMEOUT,
                    "Timeout waiting for services to reach ${config.targetState}: ${config.services}",
                    "Increase timeout or check service health configuration"
                )
                
            } catch (ComposeServiceException e) {
                throw e
            } catch (Exception e) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_TIMEOUT,
                    "Error waiting for services: ${e.message}",
                    e
                )
            }
        })
    }
    
    protected boolean checkServiceReady(String projectName, String serviceName, ServiceStatus targetState) {
        try {
            def composeCommand = getComposeCommand()
            def command = composeCommand + ["-p", projectName, "ps", serviceName, "--format", "table"]
            
            def result = processExecutor.execute(command)
            
            if (result.isSuccess() && result.stdout) {
                // Simple state checking - in real implementation would parse the output properly
                if (targetState == ServiceStatus.RUNNING) {
                    return result.stdout.toLowerCase().contains("up") || result.stdout.toLowerCase().contains("running")
                } else if (targetState == ServiceStatus.HEALTHY) {
                    return result.stdout.toLowerCase().contains("healthy")
                }
            }
            
            return false
            
        } catch (Exception e) {
            serviceLogger.debug("Error checking service ready state: ${e.message}")
            return false
        }
    }
    
    /**
     * Pure function - Build docker compose logs command from configuration.
     * 100% unit testable with no external dependencies.
     *
     * @param projectName Docker Compose project name
     * @param config Logs configuration
     * @param baseCommand Base compose command (e.g., ['docker', 'compose'])
     * @return Complete command list ready for execution
     */
    @VisibleForTesting
    protected List<String> buildLogsCommand(String projectName, LogsConfig config, List<String> baseCommand) {
        def command = baseCommand + ["-p", projectName, "logs"]

        if (config.follow) {
            command.add("--follow")
        }

        if (config.hasLimitedTail()) {
            command.addAll(["--tail", config.tailLines.toString()])
        }

        if (config.services && !config.services.isEmpty()) {
            command.addAll(config.services)
        }

        return command
    }

    @Override
    CompletableFuture<String> captureLogs(String projectName, LogsConfig config) {
        if (projectName == null) {
            throw new NullPointerException("Project name cannot be null")
        }
        if (config == null) {
            throw new NullPointerException("Logs config cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                serviceLogger.info("Capturing logs for project: ${projectName}")

                // Build command (pure logic - extracted)
                def composeCommand = getComposeCommand()
                def command = buildLogsCommand(projectName, config, composeCommand)

                // Execute command (external call - keep minimal)
                def result = processExecutor.execute(command)
                
                if (!result.isSuccess()) {
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED,
                        "Failed to capture logs: ${result.stderr}",
                        "Check if the project and services exist"
                    )
                }
                
                return result.stdout
                
            } catch (ComposeServiceException e) {
                throw e
            } catch (Exception e) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED,
                    "Failed to capture logs: ${e.message}",
                    e
                )
            }
        })
    }

    @Override
    CompletableFuture<Map<String, WaitForLogResult>> waitForLogPatterns(WaitForLogConfig config) {
        if (config == null) {
            throw new NullPointerException("Wait-for-log config cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                return executeWaitForLogPatterns(config)
            } catch (ComposeServiceException e) {
                // Re-throw domain exceptions as-is
                throw e
            } catch (InterruptedException e) {
                // Preserve interrupt status and fail with clear message
                Thread.currentThread().interrupt()
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                    "Interrupted while waiting for log patterns",
                    e
                )
            } catch (java.util.concurrent.ExecutionException e) {
                // Unwrap ExecutionException from CompletableFuture.get() calls
                def cause = e.cause ?: e
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                    "Error waiting for log patterns: ${cause.message}",
                    cause
                )
            } catch (Exception e) {
                // Catch-all for unexpected exceptions
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                    "Error waiting for log patterns: ${e.message}",
                    e
                )
            }
        })
    }

    /**
     * Core logic for waiting for log patterns.
     * Separated for testability and clarity.
     */
    @VisibleForTesting
    protected Map<String, WaitForLogResult> executeWaitForLogPatterns(WaitForLogConfig config) {
        logWaitStart(config)

        // Validate all configured services exist in the compose project
        validateServicesExist(config)

        // Initialize tracking state for each service
        // NOTE: These maps are NOT thread-safe. The polling loop must remain single-threaded.
        def matchedPatternsByService = initializeMatchedPatternsMap(config)
        def matchTimesByService = initializeMatchTimesMap(config)

        def startTime = timeService.currentTimeMillis()
        def timeoutMillis = config.timeout.toMillis()
        def pollMillis = config.pollInterval.toMillis()
        def progressMillis = config.progressInterval.toMillis()
        def lastProgressLogTime = startTime
        int attemptNumber = 0

        while (timeService.currentTimeMillis() - startTime < timeoutMillis) {
            attemptNumber++
            def currentTime = timeService.currentTimeMillis()
            def elapsedSeconds = (currentTime - startTime) / 1000

            if (config.verbose) {
                logVerbosePollingStart(attemptNumber, config.totalWaitAttempts, elapsedSeconds)
            }

            // Check all services
            def checkResult = checkAllServices(
                config, matchedPatternsByService, matchTimesByService, elapsedSeconds
            )

            if (checkResult.rejected) {
                // A reject pattern was matched - fail immediately
                throw buildRejectException(checkResult.serviceName, checkResult.rejectResult,
                                           matchedPatternsByService, matchTimesByService, config)
            }

            if (checkResult.crashed) {
                // A service crashed - fail immediately
                throw buildCrashException(checkResult.serviceName, checkResult.exitCode,
                                          matchedPatternsByService, matchTimesByService, config)
            }

            // Check if all services are ready
            if (areAllServicesReady(config, matchedPatternsByService)) {
                serviceLogger.info("[waitForLog] All services ready after ${elapsedSeconds} seconds")
                return buildResults(config, matchedPatternsByService, matchTimesByService, true)
            }

            // Log periodic progress if configured
            if (config.hasProgressInterval() && (currentTime - lastProgressLogTime) >= progressMillis) {
                logPeriodicProgress(config, matchedPatternsByService, elapsedSeconds)
                lastProgressLogTime = currentTime
            }

            timeService.sleep(pollMillis)
        }

        // Timeout reached
        def elapsedSeconds = (timeService.currentTimeMillis() - startTime) / 1000
        throw buildTimeoutException(config, matchedPatternsByService, matchTimesByService, elapsedSeconds)
    }

    /**
     * Log the start of the wait operation.
     */
    private void logWaitStart(WaitForLogConfig config) {
        def serviceCount = config.services.size()
        def timeoutSecs = config.timeout.toSeconds()
        serviceLogger.info("[waitForLog] Waiting for log patterns in ${serviceCount} service(s) " +
            "(timeout: ${timeoutSecs}s)...")
    }

    /**
     * Initialize the map tracking which patterns have matched per service.
     */
    private Map<String, Set<Integer>> initializeMatchedPatternsMap(WaitForLogConfig config) {
        def result = new HashMap<String, Set<Integer>>()
        config.services.each { serviceName ->
            result[serviceName] = new HashSet<Integer>()
        }
        return result
    }

    /**
     * Initialize the map tracking when each pattern matched per service.
     */
    private Map<String, Map<Integer, Long>> initializeMatchTimesMap(WaitForLogConfig config) {
        def result = new HashMap<String, Map<Integer, Long>>()
        config.services.each { serviceName ->
            result[serviceName] = new HashMap<Integer, Long>()
        }
        return result
    }

    /**
     * Validate that all configured services exist in the compose project.
     */
    @VisibleForTesting
    protected void validateServicesExist(WaitForLogConfig config) {
        def composeServices = getComposeProjectServices(config.projectName)

        // Special case: empty compose project
        if (composeServices.isEmpty()) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                "No services found in compose project '${config.projectName}'.\n" +
                "This typically means the compose project doesn't exist or hasn't been started.\n\n" +
                "Hint: Ensure 'composeUp' task has been executed before 'waitForLog' runs.",
                "Run 'docker compose -p ${config.projectName} ps' to check project status"
            )
        }

        def missingServices = config.services.findAll { serviceName ->
            !composeServices.contains(serviceName)
        }

        if (!missingServices.isEmpty()) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
                "Service(s) not found in compose project '${config.projectName}': ${missingServices}.\n" +
                "Available services: ${composeServices}\n\n" +
                "Hint: Check for typos in service names in your waitForLog configuration.",
                "Verify service names match those defined in your docker-compose.yml"
            )
        }
    }

    /**
     * Get list of services defined in a compose project.
     */
    @VisibleForTesting
    protected Set<String> getComposeProjectServices(String projectName) {
        try {
            def composeCommand = getComposeCommand()
            def command = composeCommand + ["-p", projectName, "ps", "--format", "json", "-a"]
            def result = processExecutor.execute(command)

            if (result.isSuccess() && result.stdout) {
                def jsonSlurper = new JsonSlurper()
                def services = new HashSet<String>()
                def lines = result.stdout.trim().split('\n')
                for (line in lines) {
                    if (line.trim()) {
                        try {
                            def container = jsonSlurper.parseText(line.trim())
                            def serviceName = container?.Service
                            if (serviceName) {
                                services.add(serviceName)
                            }
                        } catch (JsonException ignored) {
                            // Skip malformed lines
                        }
                    }
                }
                return services
            }
            return new HashSet<String>()
        } catch (Exception e) {
            serviceLogger.debug("Error getting compose project services: ${e.message}")
            return new HashSet<String>()
        }
    }

    /**
     * Check all services for pattern matches, reject patterns, and crashes.
     */
    @VisibleForTesting
    protected CheckAllServicesResult checkAllServices(
            WaitForLogConfig config,
            Map<String, Set<Integer>> matchedPatternsByService,
            Map<String, Map<Integer, Long>> matchTimesByService,
            long elapsedSeconds) {

        for (String serviceName : config.services) {
            // First check if service is still running
            if (!isServiceRunning(config.projectName, serviceName)) {
                def exitCode = getServiceExitCode(config.projectName, serviceName)
                return CheckAllServicesResult.crashed(serviceName, exitCode)
            }

            // Fetch logs for this service
            def logLines = fetchServiceLogs(config.projectName, serviceName)

            // Check for reject patterns FIRST (before success patterns)
            def rejectPatterns = config.rejectPatterns[serviceName]
            if (rejectPatterns != null && !rejectPatterns.isEmpty()) {
                def rejectResult = LogPatternMatcher.checkRejectPatterns(rejectPatterns, logLines)
                if (rejectResult != null) {
                    return CheckAllServicesResult.rejected(serviceName, rejectResult)
                }
            }

            // Update success pattern matches
            def patterns = config.servicePatterns[serviceName]
            if (patterns == null) {
                throw new IllegalStateException(
                    "Internal error: service '${serviceName}' not found in servicePatterns."
                )
            }
            def matchedPatterns = matchedPatternsByService[serviceName]
            def matchTimes = matchTimesByService[serviceName]

            def newMatches = LogPatternMatcher.updateMatches(
                patterns, matchedPatterns, logLines, elapsedSeconds, matchTimes
            )

            if (config.verbose && newMatches > 0) {
                logVerboseNewMatches(serviceName, matchedPatterns.size(), patterns.size())
            }
        }

        return CheckAllServicesResult.ok()
    }

    /**
     * Result object for checkAllServices().
     */
    @VisibleForTesting
    protected static class CheckAllServicesResult {
        boolean rejected = false
        boolean crashed = false
        String serviceName
        LogPatternMatcher.RejectCheckResult rejectResult
        Integer exitCode

        static CheckAllServicesResult ok() {
            return new CheckAllServicesResult()
        }

        static CheckAllServicesResult rejected(String serviceName, 
                                               LogPatternMatcher.RejectCheckResult rejectResult) {
            def result = new CheckAllServicesResult()
            result.rejected = true
            result.serviceName = serviceName
            result.rejectResult = rejectResult
            return result
        }

        static CheckAllServicesResult crashed(String serviceName, Integer exitCode) {
            def result = new CheckAllServicesResult()
            result.crashed = true
            result.serviceName = serviceName
            result.exitCode = exitCode
            return result
        }
    }

    /**
     * Check if all services have all their patterns matched.
     */
    private boolean areAllServicesReady(WaitForLogConfig config, 
                                        Map<String, Set<Integer>> matchedPatternsByService) {
        return config.services.every { serviceName ->
            def patterns = config.servicePatterns[serviceName]
            def matched = matchedPatternsByService[serviceName]
            matched.size() == patterns.size()
        }
    }

    /**
     * Fetch logs for a specific service.
     */
    @VisibleForTesting
    protected List<String> fetchServiceLogs(String projectName, String serviceName) {
        def logsConfig = new LogsConfig([serviceName], 0, false, null)
        def logsFuture = captureLogs(projectName, logsConfig)
        def logsOutput = logsFuture.get()
        def trimmedOutput = logsOutput?.trim()
        return trimmedOutput ? trimmedOutput.split('\n').toList() : []
    }

    /**
     * Check if a service container is currently running.
     */
    @VisibleForTesting
    protected boolean isServiceRunning(String projectName, String serviceName) {
        try {
            def composeCommand = getComposeCommand()
            def command = composeCommand + ["-p", projectName, "ps", serviceName, "--format", "json"]
            def result = processExecutor.execute(command)

            if (result.isSuccess() && result.stdout) {
                def jsonSlurper = new JsonSlurper()
                def lines = result.stdout.trim().split('\n')
                for (line in lines) {
                    if (line.trim()) {
                        try {
                            def container = jsonSlurper.parseText(line.trim())
                            def state = container?.State?.toLowerCase() ?: ''
                            if (state == 'running') {
                                return true
                            }
                        } catch (JsonException jsonEx) {
                            serviceLogger.warn("Failed to parse docker compose ps JSON output: ${jsonEx.message}")
                            serviceLogger.debug("Raw JSON line that failed to parse: ${line}")
                        }
                    }
                }
            }
            return false
        } catch (Exception e) {
            serviceLogger.debug("Error checking if service is running: ${e.message}")
            return false
        }
    }

    /**
     * Get the exit code of a stopped/crashed service.
     */
    @VisibleForTesting
    protected Integer getServiceExitCode(String projectName, String serviceName) {
        try {
            def composeCommand = getComposeCommand()
            def command = composeCommand + ["-p", projectName, "ps", serviceName, "--format", "json"]
            def result = processExecutor.execute(command)

            if (result.isSuccess() && result.stdout) {
                def jsonSlurper = new JsonSlurper()
                def lines = result.stdout.trim().split('\n')
                for (line in lines) {
                    if (line.trim()) {
                        def container = jsonSlurper.parseText(line.trim())
                        return container?.ExitCode as Integer
                    }
                }
            }
            return null
        } catch (Exception e) {
            serviceLogger.debug("Error getting service exit code: ${e.message}")
            return null
        }
    }

    /**
     * Get recent logs for error reporting.
     */
    @VisibleForTesting
    protected List<String> getRecentLogs(String projectName, String serviceName, int lineCount) {
        def logsConfig = new LogsConfig([serviceName], lineCount, false, null)
        def logsFuture = captureLogs(projectName, logsConfig)
        def logsOutput = logsFuture.get()
        return logsOutput ? logsOutput.split('\n').toList() : []
    }

    /**
     * Build result objects from match state.
     */
    private Map<String, WaitForLogResult> buildResults(
            WaitForLogConfig config,
            Map<String, Set<Integer>> matchedPatternsByService,
            Map<String, Map<Integer, Long>> matchTimesByService,
            boolean allReady) {

        def results = [:]
        config.services.each { serviceName ->
            def patterns = config.servicePatterns[serviceName]
            def matchedPatterns = matchedPatternsByService[serviceName]
            def matchTimes = matchTimesByService[serviceName]

            def patternMatches = patterns.withIndex().collect { pattern, index ->
                def matched = matchedPatterns.contains(index)
                def matchedAt = matchTimes[index]
                new WaitForLogResult.PatternMatch(pattern.pattern(), matched, matchedAt)
            }

            def serviceReady = matchedPatterns.size() == patterns.size()
            results[serviceName] = new WaitForLogResult(serviceName, patternMatches, serviceReady)
        }
        return results
    }

    // --- Logging helpers ---

    private void logVerbosePollingStart(int attempt, int totalAttempts, long elapsedSeconds) {
        serviceLogger.info("[waitForLog] Polling for log patterns (attempt ${attempt}/${totalAttempts}, " +
            "elapsed: ${elapsedSeconds}s)...")
    }

    private void logVerboseNewMatches(String serviceName, int matchedCount, int totalCount) {
        serviceLogger.info("[waitForLog] Service '${serviceName}': ${matchedCount}/${totalCount} patterns matched")
    }

    private void logPeriodicProgress(WaitForLogConfig config, 
                                      Map<String, Set<Integer>> matchedPatternsByService,
                                      long elapsedSeconds) {
        def summary = config.services.collect { serviceName ->
            def patterns = config.servicePatterns[serviceName]
            def matched = matchedPatternsByService[serviceName]
            "${serviceName} ${matched.size()}/${patterns.size()}"
        }.join(', ')
        serviceLogger.info("[waitForLog] Progress at ${elapsedSeconds}s: ${summary}")
    }

    // --- Exception builders ---

    private ComposeServiceException buildTimeoutException(
            WaitForLogConfig config,
            Map<String, Set<Integer>> matchedPatternsByService,
            Map<String, Map<Integer, Long>> matchTimesByService,
            long elapsedSeconds) {

        def sb = new StringBuilder()
        sb.append("Timeout waiting for log patterns after ${elapsedSeconds} seconds.\n\n")

        config.services.each { serviceName ->
            def patterns = config.servicePatterns[serviceName]
            def matchedPatterns = matchedPatternsByService[serviceName]

            sb.append("Service '${serviceName}':\n")
            patterns.eachWithIndex { pattern, index ->
                def matched = matchedPatterns.contains(index)
                def status = matched ? "✓" : "✗"
                sb.append("  ${status} ${pattern.pattern()}\n")
            }
            sb.append("\n")
        }

        sb.append("Suggestion: Check if services are producing expected log output.\n")
        sb.append("Try increasing timeoutSeconds or checking container health.")

        return new ComposeServiceException(
            ComposeServiceException.ErrorType.LOG_PATTERN_TIMEOUT,
            sb.toString(),
            "Increase timeoutSeconds or verify patterns match actual log output"
        )
    }

    private ComposeServiceException buildRejectException(
            String serviceName,
            LogPatternMatcher.RejectCheckResult rejectResult,
            Map<String, Set<Integer>> matchedPatternsByService,
            Map<String, Map<Integer, Long>> matchTimesByService,
            WaitForLogConfig config) {

        def sb = new StringBuilder()
        sb.append("Reject pattern matched for service '${serviceName}'.\n\n")
        sb.append("Pattern: ${rejectResult.patternString}\n")
        sb.append("Matched line: ${rejectResult.matchingLogLine}\n\n")

        // Include recent logs for context
        try {
            def recentLogs = getRecentLogs(config.projectName, serviceName, RECENT_LOG_LINES_FOR_ERROR)
            if (!recentLogs.isEmpty()) {
                sb.append("Recent logs:\n")
                recentLogs.each { line -> sb.append("  ${line}\n") }
            }
        } catch (Exception e) {
            sb.append("(Could not fetch recent logs: ${e.message})\n")
        }

        return new ComposeServiceException(
            ComposeServiceException.ErrorType.LOG_PATTERN_REJECTED,
            sb.toString(),
            "Check service logs for errors and fix the underlying issue"
        )
    }

    private ComposeServiceException buildCrashException(
            String serviceName,
            Integer exitCode,
            Map<String, Set<Integer>> matchedPatternsByService,
            Map<String, Map<Integer, Long>> matchTimesByService,
            WaitForLogConfig config) {

        def sb = new StringBuilder()
        sb.append("Service '${serviceName}' exited unexpectedly.\n")
        sb.append("Exit code: ${exitCode ?: 'unknown'}\n\n")

        // Include recent logs for context
        try {
            def recentLogs = getRecentLogs(config.projectName, serviceName, RECENT_LOG_LINES_FOR_ERROR)
            if (!recentLogs.isEmpty()) {
                sb.append("Recent logs:\n")
                recentLogs.each { line -> sb.append("  ${line}\n") }
            }
        } catch (Exception e) {
            sb.append("(Could not fetch recent logs: ${e.message})\n")
        }

        return new ComposeServiceException(
            ComposeServiceException.ErrorType.SERVICE_CRASHED,
            sb.toString(),
            "Check service configuration and dependencies"
        )
    }
}