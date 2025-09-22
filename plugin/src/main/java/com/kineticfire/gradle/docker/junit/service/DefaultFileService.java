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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Default implementation of FileService using standard Java NIO.
 * <p>
 * This implementation delegates to the standard Java NIO APIs for file system operations.
 */
public class DefaultFileService implements FileService {

    /**
     * Creates a new DefaultFileService instance.
     */
    public DefaultFileService() {
        // Default constructor
    }

    @Override
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    @Override
    public void createDirectories(Path path) throws IOException {
        Files.createDirectories(path);
    }

    @Override
    public void writeString(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    @Override
    public void delete(Path path) throws IOException {
        Files.delete(path);
    }

    @Override
    public Stream<Path> list(Path dir) throws IOException {
        return Files.list(dir);
    }

    @Override
    public Path resolve(String first, String... more) {
        return Paths.get(first, more);
    }

    @Override
    public File toFile(Path path) {
        return path.toFile();
    }
}