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
import java.util.concurrent.TimeUnit

/**
 * Default implementation of ProcessExecutor using ProcessBuilder
 */
class DefaultProcessExecutor implements ProcessExecutor {
    
    @Override
    ProcessResult execute(List<String> command, File workingDir, Duration timeout) {
        try {
            def processBuilder = new ProcessBuilder(command)
            if (workingDir) {
                processBuilder.directory(workingDir)
            }
            
            def process = processBuilder.start()
            
            def completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                throw new RuntimeException("Process timed out after ${timeout.seconds} seconds")
            }
            
            def stdout = process.inputStream.text
            def stderr = process.errorStream.text
            
            return new ProcessResult(process.exitValue(), stdout, stderr)
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute command: ${command}", e)
        }
    }
    
    @Override
    ProcessResult execute(List<String> command, File workingDir) {
        return execute(command, workingDir, Duration.ofMinutes(5))
    }
    
    @Override
    ProcessResult execute(List<String> command) {
        return execute(command, null, Duration.ofMinutes(5))
    }
}