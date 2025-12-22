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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for FileOperations interface and DefaultFileOperations implementation.
 * Demonstrates that the abstraction layer works correctly.
 */
class FileOperationsTest extends Specification {
    
    @TempDir
    Path tempDir
    
    FileOperations fileOps = new DefaultFileOperations()
    
    def "createDirectories creates nested directories"() {
        given:
        def nestedPath = tempDir.resolve("a/b/c")
        
        when:
        fileOps.createDirectories(nestedPath)
        
        then:
        Files.exists(nestedPath)
        Files.isDirectory(nestedPath)
    }
    
    def "writeText and readText handle content correctly"() {
        given:
        def filePath = tempDir.resolve("test.txt")
        def content = "Hello, World!"
        
        when:
        fileOps.writeText(filePath, content)
        def readContent = fileOps.readText(filePath)
        
        then:
        readContent == content
    }
    
    def "exists returns true for existing files"() {
        given:
        def filePath = tempDir.resolve("exists.txt")
        fileOps.writeText(filePath, "content")
        
        expect:
        fileOps.exists(filePath)
    }
    
    def "exists returns false for non-existing files"() {
        given:
        def filePath = tempDir.resolve("nonexistent.txt")
        
        expect:
        !fileOps.exists(filePath)
    }
    
    def "delete removes existing file"() {
        given:
        def filePath = tempDir.resolve("delete-me.txt")
        fileOps.writeText(filePath, "content")
        
        when:
        fileOps.delete(filePath)
        
        then:
        !fileOps.exists(filePath)
    }
    
    def "toFile converts Path to File correctly"() {
        given:
        def path = tempDir.resolve("test.txt")

        when:
        def file = fileOps.toFile(path)

        then:
        file instanceof File
        file.toPath() == path
    }

    def "writeText handles empty content"() {
        given:
        def filePath = tempDir.resolve("empty.txt")

        when:
        fileOps.writeText(filePath, "")
        def content = fileOps.readText(filePath)

        then:
        content == ""
    }

    def "writeText handles multiline content"() {
        given:
        def filePath = tempDir.resolve("multiline.txt")
        def content = "line1\nline2\nline3"

        when:
        fileOps.writeText(filePath, content)
        def readContent = fileOps.readText(filePath)

        then:
        readContent == content
    }

    def "writeText handles unicode content"() {
        given:
        def filePath = tempDir.resolve("unicode.txt")
        def content = "Hello 世界 🌍"

        when:
        fileOps.writeText(filePath, content)
        def readContent = fileOps.readText(filePath)

        then:
        readContent == content
    }

    def "createDirectories handles existing directory"() {
        given:
        def path = tempDir.resolve("existing")
        Files.createDirectories(path)

        when:
        fileOps.createDirectories(path)

        then:
        noExceptionThrown()
        fileOps.exists(path)
    }

    def "exists returns true for directories"() {
        given:
        def dirPath = tempDir.resolve("testdir")
        fileOps.createDirectories(dirPath)

        expect:
        fileOps.exists(dirPath)
    }

    def "delete removes empty directory"() {
        given:
        def dirPath = tempDir.resolve("deletedir")
        fileOps.createDirectories(dirPath)

        when:
        fileOps.delete(dirPath)

        then:
        !fileOps.exists(dirPath)
    }
}
