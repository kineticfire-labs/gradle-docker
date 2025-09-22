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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of ProcessExecutor using Java ProcessBuilder.
 * <p>
 * This implementation uses the standard Java ProcessBuilder API to execute external processes
 * and capture their output. It supports timeout operations and directory specification.
 */
public class DefaultProcessExecutor implements ProcessExecutor {

    /**
     * Creates a new DefaultProcessExecutor instance.
     */
    public DefaultProcessExecutor() {
        // Default constructor
    }

    @Override
    public ProcessResult execute(String... command) throws IOException, InterruptedException {
        return executeInDirectory(null, command);
    }

    @Override
    public ProcessResult executeWithTimeout(int timeout, TimeUnit unit, String... command)
            throws IOException, InterruptedException {
        return executeInDirectoryWithTimeout(null, timeout, unit, command);
    }

    @Override
    public ProcessResult executeInDirectory(File directory, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (directory != null) {
            pb.directory(directory);
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();
        String output = readProcessOutput(process);

        return new ProcessResult(exitCode, output);
    }

    @Override
    public ProcessResult executeInDirectoryWithTimeout(File directory, int timeout, TimeUnit unit, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (directory != null) {
            pb.directory(directory);
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean finished = process.waitFor(timeout, unit);

        if (!finished) {
            process.destroyForcibly();
            throw new InterruptedException("Process timed out after " + timeout + " " + unit);
        }

        int exitCode = process.exitValue();
        String output = readProcessOutput(process);

        return new ProcessResult(exitCode, output);
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}