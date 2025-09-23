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

import java.time.Duration

/**
 * Interface for executing external processes
 * Allows for testable abstraction of ProcessBuilder operations
 */
interface ProcessExecutor {
    
    /**
     * Execute a command with working directory and timeout
     * @param command List of command and arguments
     * @param workingDir Working directory for command execution
     * @param timeout Maximum time to wait for process completion
     * @return ProcessResult containing exit code, stdout, and stderr
     */
    ProcessResult execute(List<String> command, File workingDir, Duration timeout)
    
    /**
     * Execute a command with working directory (no timeout)
     * @param command List of command and arguments
     * @param workingDir Working directory for command execution
     * @return ProcessResult containing exit code, stdout, and stderr
     */
    ProcessResult execute(List<String> command, File workingDir)
    
    /**
     * Execute a command without working directory
     * @param command List of command and arguments
     * @return ProcessResult containing exit code, stdout, and stderr
     */
    ProcessResult execute(List<String> command)
}

/**
 * Result of process execution
 */
class ProcessResult {
    final int exitCode
    final String stdout
    final String stderr
    
    ProcessResult(int exitCode, String stdout, String stderr) {
        this.exitCode = exitCode
        this.stdout = stdout ?: ""
        this.stderr = stderr ?: ""
    }
    
    boolean isSuccess() {
        return exitCode == 0
    }
    
    @Override
    String toString() {
        return "ProcessResult{exitCode=${exitCode}, stdout='${stdout}', stderr='${stderr}'}"
    }
}