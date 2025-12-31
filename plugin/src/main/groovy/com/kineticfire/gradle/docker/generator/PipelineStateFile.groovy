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

package com.kineticfire.gradle.docker.generator

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JSON-based state communication between pipeline tasks.
 *
 * This utility enables file-based state transfer between tasks in a configuration-cache-compatible
 * way. Instead of passing state through non-serializable objects, tasks write results to JSON files
 * that downstream tasks can read via @InputFile properties.
 *
 * Thread Safety: File writes use atomic operations where supported by the filesystem. The write
 * process creates a temporary file, writes all content to it, then atomically moves it to the
 * target location. This prevents readers from seeing partially-written files. On filesystems that
 * don't support atomic moves, a fallback to regular move is used, which may briefly expose the
 * write operation but still ensures complete file content.
 */
final class PipelineStateFile {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStateFile)

    private PipelineStateFile() {
        // Utility class - prevent instantiation
    }

    /**
     * Write test result to a JSON file.
     *
     * @param file The output file
     * @param success Whether the tests passed
     * @param message A message describing the result
     * @param timestamp The timestamp when the result was recorded
     */
    static void writeTestResult(File file, boolean success, String message, long timestamp) {
        def data = new TestResultData(success: success, message: message ?: "", timestamp: timestamp)
        writeToFile(file, data)
        LOGGER.debug("Wrote test result to {}: success={}", file.absolutePath, success)
    }

    /**
     * Read test result from a JSON file.
     *
     * @param file The input file
     * @return TestResultData containing the test result
     * @throws IllegalArgumentException if file doesn't exist or is invalid
     */
    static TestResultData readTestResult(File file) {
        validateFileExists(file)
        def data = readFromFile(file)
        return new TestResultData(
            success: data.success ?: false,
            message: data.message ?: "",
            timestamp: data.timestamp ?: 0L
        )
    }

    /**
     * Check if tests were successful by reading the result file.
     *
     * @param file The test result file
     * @return true if tests passed, false otherwise or if file doesn't exist
     */
    static boolean isTestSuccessful(File file) {
        if (!file.exists()) {
            LOGGER.debug("Test result file does not exist: {}", file.absolutePath)
            return false
        }
        try {
            def result = readTestResult(file)
            return result.success
        } catch (Exception e) {
            LOGGER.warn("Failed to read test result from {}: {}", file.absolutePath, e.message)
            return false
        }
    }

    /**
     * Write build result to a JSON file.
     *
     * @param file The output file
     * @param imageName The name of the built image
     * @param tags The list of tags applied to the image
     */
    static void writeBuildResult(File file, String imageName, List<String> tags) {
        writeBuildResult(file, imageName, tags, System.currentTimeMillis())
    }

    /**
     * Write build result to a JSON file with explicit timestamp.
     *
     * @param file The output file
     * @param imageName The name of the built image
     * @param tags The list of tags applied to the image
     * @param timestamp The timestamp when the build completed
     */
    static void writeBuildResult(File file, String imageName, List<String> tags, long timestamp) {
        def data = new BuildResultData(
            imageName: imageName ?: "",
            tags: tags ?: [],
            timestamp: timestamp
        )
        writeToFile(file, data)
        LOGGER.debug("Wrote build result to {}: imageName={}", file.absolutePath, imageName)
    }

    /**
     * Read build result from a JSON file.
     *
     * @param file The input file
     * @return BuildResultData containing the build result
     * @throws IllegalArgumentException if file doesn't exist or is invalid
     */
    static BuildResultData readBuildResult(File file) {
        validateFileExists(file)
        def data = readFromFile(file)
        return new BuildResultData(
            imageName: data.imageName ?: "",
            tags: data.tags ?: [],
            timestamp: data.timestamp ?: 0L
        )
    }

    /**
     * Write generic state data to a JSON file.
     *
     * @param file The output file
     * @param data Map of state data to write
     */
    static void writeState(File file, Map<String, Object> data) {
        writeToFile(file, data)
        LOGGER.debug("Wrote state to {}", file.absolutePath)
    }

    /**
     * Read generic state data from a JSON file.
     *
     * @param file The input file
     * @return Map containing the state data
     * @throws IllegalArgumentException if file doesn't exist or is invalid
     */
    static Map<String, Object> readState(File file) {
        validateFileExists(file)
        return readFromFile(file) as Map<String, Object>
    }

    private static void writeToFile(File file, Object data) {
        File tempFile = null
        try {
            file.parentFile?.mkdirs()
            def json = JsonOutput.prettyPrint(JsonOutput.toJson(data))
            
            // Write to temp file first for atomic operation
            // Use unique suffix to prevent concurrent writes from interfering with each other
            def uniqueSuffix = "${Thread.currentThread().id}-${System.nanoTime()}"
            tempFile = new File(file.parentFile, "${file.name}.${uniqueSuffix}.tmp")
            tempFile.text = json
            
            // Attempt atomic move
            atomicMoveFile(tempFile, file)
        } catch (Exception e) {
            // Clean up temp file on failure
            cleanupTempFile(tempFile)
            throw new IllegalStateException("Failed to write state to ${file.absolutePath}: ${e.message}", e)
        }
    }

    /**
     * Atomically moves a file from source to target. Falls back to regular move if atomic
     * move is not supported by the filesystem.
     *
     * @param source The source file to move
     * @param target The target file location
     * @throws IOException if the move fails
     */
    private static void atomicMoveFile(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), 
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (AtomicMoveNotSupportedException e) {
            // Fallback to regular move if atomic is not supported
            LOGGER.debug("Atomic move not supported, falling back to regular move for {}", target.absolutePath)
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Safely cleans up a temporary file, logging any failures.
     *
     * @param tempFile The temporary file to delete (may be null)
     */
    private static void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            try {
                Files.deleteIfExists(tempFile.toPath())
            } catch (IOException e) {
                LOGGER.warn("Failed to clean up temp file {}: {}", tempFile.absolutePath, e.message)
            }
        }
    }

    private static Object readFromFile(File file) {
        try {
            def json = file.text
            return new JsonSlurper().parseText(json)
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read state from ${file.absolutePath}: ${e.message}", e)
        }
    }

    private static void validateFileExists(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null")
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: ${file.absolutePath}")
        }
    }

    /**
     * Data class for test results.
     */
    static class TestResultData {
        boolean success
        String message
        long timestamp

        boolean equals(Object obj) {
            if (this.is(obj)) return true
            if (!(obj instanceof TestResultData)) return false
            TestResultData that = (TestResultData) obj
            return success == that.success &&
                   timestamp == that.timestamp &&
                   Objects.equals(message, that.message)
        }

        int hashCode() {
            return Objects.hash(success, message, timestamp)
        }

        String toString() {
            return "TestResultData{success=${success}, message='${message}', timestamp=${timestamp}}"
        }
    }

    /**
     * Data class for build results.
     */
    static class BuildResultData {
        String imageName
        List<String> tags
        long timestamp

        boolean equals(Object obj) {
            if (this.is(obj)) return true
            if (!(obj instanceof BuildResultData)) return false
            BuildResultData that = (BuildResultData) obj
            return timestamp == that.timestamp &&
                   Objects.equals(imageName, that.imageName) &&
                   Objects.equals(tags, that.tags)
        }

        int hashCode() {
            return Objects.hash(imageName, tags, timestamp)
        }

        String toString() {
            return "BuildResultData{imageName='${imageName}', tags=${tags}, timestamp=${timestamp}}"
        }
    }
}
