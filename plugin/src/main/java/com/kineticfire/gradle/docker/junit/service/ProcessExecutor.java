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

package com.kineticfire.gradle.docker.junit.service;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Service interface for executing external processes.
 * <p>
 * This interface abstracts process execution to enable testing and dependency injection.
 * It provides methods for executing commands with optional timeout and working directory control.
 */
public interface ProcessExecutor {

    /**
     * Executes a command and waits for it to complete.
     *
     * @param command the command and its arguments to execute
     * @return the result containing exit code and output
     * @throws IOException if an I/O error occurs during process execution
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    ProcessResult execute(String... command) throws IOException, InterruptedException;

    /**
     * Executes a command with a timeout.
     *
     * @param timeout the maximum time to wait for the process
     * @param unit the time unit of the timeout argument
     * @param command the command and its arguments to execute
     * @return the result containing exit code and output
     * @throws IOException if an I/O error occurs during process execution
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    ProcessResult executeWithTimeout(int timeout, TimeUnit unit, String... command) throws IOException, InterruptedException;

    /**
     * Executes a command in a specific working directory.
     *
     * @param directory the working directory for the process
     * @param command the command and its arguments to execute
     * @return the result containing exit code and output
     * @throws IOException if an I/O error occurs during process execution
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    ProcessResult executeInDirectory(File directory, String... command) throws IOException, InterruptedException;

    /**
     * Executes a command in a specific working directory with a timeout.
     *
     * @param directory the working directory for the process
     * @param timeout the maximum time to wait for the process
     * @param unit the time unit of the timeout argument
     * @param command the command and its arguments to execute
     * @return the result containing exit code and output
     * @throws IOException if an I/O error occurs during process execution
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    ProcessResult executeInDirectoryWithTimeout(File directory, int timeout, TimeUnit unit, String... command)
            throws IOException, InterruptedException;

    /**
     * Represents the result of a process execution.
     * <p>
     * Contains the exit code and captured output from the process.
     */
    public static class ProcessResult {
        private final int exitCode;
        private final String output;

        /**
         * Creates a new ProcessResult.
         *
         * @param exitCode the exit code of the process
         * @param output the captured output from the process
         */
        public ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        /**
         * Returns the exit code of the process.
         *
         * @return the exit code
         */
        public int getExitCode() {
            return exitCode;
        }

        /**
         * Returns the captured output from the process.
         *
         * @return the process output
         */
        public String getOutput() {
            return output;
        }
    }
}