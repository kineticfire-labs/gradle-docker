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

import java.nio.file.Path

/**
 * Interface for file system operations.
 * Enables testing by allowing mock implementations.
 * Implementations must be Serializable for Gradle configuration cache compatibility.
 */
interface FileOperations extends Serializable {

    /**
     * Create directories for the given path, including any necessary parent directories
     *
     * @param path The directory path to create
     * @throws IOException if directory creation fails
     */
    void createDirectories(Path path)

    /**
     * Write text content to a file
     *
     * @param path The file path to write to
     * @param content The text content to write
     * @throws IOException if write operation fails
     */
    void writeText(Path path, String content)

    /**
     * Read text content from a file
     *
     * @param path The file path to read from
     * @return The text content of the file
     * @throws IOException if read operation fails
     */
    String readText(Path path)

    /**
     * Check if a file or directory exists
     *
     * @param path The path to check
     * @return true if the path exists, false otherwise
     */
    boolean exists(Path path)

    /**
     * Delete a file or empty directory
     *
     * @param path The path to delete
     * @throws IOException if delete operation fails
     */
    void delete(Path path)

    /**
     * Convert a Path to a File object
     *
     * @param path The path to convert
     * @return The File object representing the path
     */
    File toFile(Path path)
}

/**
 * Default implementation using Java NIO and Groovy file operations.
 * Keep this class minimal - it's the boundary to the file system.
 * Configuration cache compatible (Serializable).
 */
class DefaultFileOperations implements FileOperations {

    @Override
    void createDirectories(Path path) {
        java.nio.file.Files.createDirectories(path)
    }

    @Override
    void writeText(Path path, String content) {
        path.toFile().text = content
    }

    @Override
    String readText(Path path) {
        return path.toFile().text
    }

    @Override
    boolean exists(Path path) {
        return java.nio.file.Files.exists(path)
    }

    @Override
    void delete(Path path) {
        java.nio.file.Files.delete(path)
    }

    @Override
    File toFile(Path path) {
        return path.toFile()
    }
}
