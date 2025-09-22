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
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Service interface for file system operations.
 * <p>
 * This interface abstracts file system operations to enable testing and dependency injection.
 * It provides methods for file existence checks, directory creation, file operations, and path manipulation.
 */
public interface FileService {

    /**
     * Tests whether a file or directory exists.
     *
     * @param path the path to test for existence
     * @return true if the path exists, false otherwise
     */
    boolean exists(Path path);

    /**
     * Creates directories as needed, including any parent directories.
     *
     * @param path the directory to create
     * @throws IOException if an I/O error occurs during directory creation
     */
    void createDirectories(Path path) throws IOException;

    /**
     * Writes a string to a file using UTF-8 encoding.
     *
     * @param path the path to the file to write
     * @param content the content to write
     * @throws IOException if an I/O error occurs during file writing
     */
    void writeString(Path path, String content) throws IOException;

    /**
     * Deletes a file or directory.
     *
     * @param path the path to delete
     * @throws IOException if an I/O error occurs during deletion
     */
    void delete(Path path) throws IOException;

    /**
     * Returns a stream of the entries in a directory.
     *
     * @param dir the directory to list
     * @return a stream of directory entries
     * @throws IOException if an I/O error occurs during directory listing
     */
    Stream<Path> list(Path dir) throws IOException;

    /**
     * Converts a path string or sequence of strings to a Path.
     *
     * @param first the path string or initial part of the path string
     * @param more additional strings to be joined to form the path string
     * @return the resulting Path
     */
    Path resolve(String first, String... more);

    /**
     * Converts this path to a File object.
     *
     * @param path the path to convert
     * @return a File object representing this path
     */
    File toFile(Path path);
}