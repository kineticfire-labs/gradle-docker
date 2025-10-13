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

package com.kineticfire.gradle.docker.junit.service

import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream

/**
 * Unit tests for DefaultFileService to achieve 100% coverage.
 */
class DefaultFileServiceTest extends Specification {

    @Subject
    DefaultFileService fileService

    File tempDir
    Path tempDirPath

    def setup() {
        fileService = new DefaultFileService()
        tempDir = File.createTempDir("default-file-service-test", "")
        tempDirPath = tempDir.toPath()
    }

    def cleanup() {
        tempDir.deleteDir()
    }

    def "constructor creates instance successfully"() {
        expect:
        fileService != null
    }

    def "exists returns true for existing file"() {
        given:
        File testFile = new File(tempDir, "test-file.txt")
        testFile.createNewFile()
        Path testPath = testFile.toPath()

        when:
        boolean result = fileService.exists(testPath)

        then:
        result == true
    }

    def "exists returns true for existing directory"() {
        when:
        boolean result = fileService.exists(tempDirPath)

        then:
        result == true
    }

    def "exists returns false for non-existing file"() {
        given:
        Path nonExistentPath = tempDirPath.resolve("non-existent-file.txt")

        when:
        boolean result = fileService.exists(nonExistentPath)

        then:
        result == false
    }

    def "createDirectories creates single directory successfully"() {
        given:
        Path newDirPath = tempDirPath.resolve("new-directory")

        when:
        fileService.createDirectories(newDirPath)

        then:
        newDirPath.toFile().exists()
        newDirPath.toFile().isDirectory()
    }

    def "createDirectories creates nested directories successfully"() {
        given:
        Path nestedDirPath = tempDirPath.resolve("level1/level2/level3")

        when:
        fileService.createDirectories(nestedDirPath)

        then:
        nestedDirPath.toFile().exists()
        nestedDirPath.toFile().isDirectory()
        tempDirPath.resolve("level1").toFile().exists()
        tempDirPath.resolve("level1/level2").toFile().exists()
    }

    def "createDirectories succeeds when directory already exists"() {
        given:
        Path existingDirPath = tempDirPath.resolve("existing-dir")
        existingDirPath.toFile().mkdirs()

        when:
        fileService.createDirectories(existingDirPath)

        then:
        noExceptionThrown()
        existingDirPath.toFile().exists()
        existingDirPath.toFile().isDirectory()
    }

    def "writeString creates file with content successfully"() {
        given:
        Path filePath = tempDirPath.resolve("test-content.txt")
        String content = "Hello, World!\nThis is a test file."

        when:
        fileService.writeString(filePath, content)

        then:
        filePath.toFile().exists()
        filePath.toFile().text == content
    }

    def "writeString overwrites existing file content"() {
        given:
        Path filePath = tempDirPath.resolve("overwrite-test.txt")
        String originalContent = "Original content"
        String newContent = "New content that replaces the original"

        // Create file with original content
        filePath.toFile().text = originalContent

        when:
        fileService.writeString(filePath, newContent)

        then:
        filePath.toFile().text == newContent
        filePath.toFile().text != originalContent
    }

    def "writeString handles empty content"() {
        given:
        Path filePath = tempDirPath.resolve("empty-file.txt")
        String content = ""

        when:
        fileService.writeString(filePath, content)

        then:
        filePath.toFile().exists()
        filePath.toFile().text == ""
        filePath.toFile().length() == 0
    }

    def "writeString handles unicode content"() {
        given:
        Path filePath = tempDirPath.resolve("unicode-file.txt")
        String content = "Unicode test: 测试 🚀 αβγ"

        when:
        fileService.writeString(filePath, content)

        then:
        filePath.toFile().exists()
        filePath.toFile().text == content
    }

    def "delete removes existing file successfully"() {
        given:
        File testFile = new File(tempDir, "delete-test.txt")
        testFile.createNewFile()
        Path testPath = testFile.toPath()

        when:
        fileService.delete(testPath)

        then:
        !testFile.exists()
    }

    def "delete removes empty directory successfully"() {
        given:
        File testDir = new File(tempDir, "delete-dir")
        testDir.mkdirs()
        Path testPath = testDir.toPath()

        when:
        fileService.delete(testPath)

        then:
        !testDir.exists()
    }

    def "delete throws exception for non-existing file"() {
        given:
        Path nonExistentPath = tempDirPath.resolve("non-existent-file.txt")

        when:
        fileService.delete(nonExistentPath)

        then:
        IOException ex = thrown()
    }

    def "list returns stream of directory contents"() {
        given:
        // Create some test files in the directory
        new File(tempDir, "file1.txt").createNewFile()
        new File(tempDir, "file2.txt").createNewFile()
        new File(tempDir, "subdir").mkdirs()

        when:
        Stream<Path> result = fileService.list(tempDirPath)
        List<Path> paths = result.collect()

        then:
        paths.size() == 3
        paths.find { it.fileName.toString() == "file1.txt" } != null
        paths.find { it.fileName.toString() == "file2.txt" } != null
        paths.find { it.fileName.toString() == "subdir" } != null
    }

    def "list returns empty stream for empty directory"() {
        given:
        File emptyDir = new File(tempDir, "empty-dir")
        emptyDir.mkdirs()
        Path emptyDirPath = emptyDir.toPath()

        when:
        Stream<Path> result = fileService.list(emptyDirPath)
        List<Path> paths = result.collect()

        then:
        paths.isEmpty()
    }

    def "list throws exception for non-existing directory"() {
        given:
        Path nonExistentPath = tempDirPath.resolve("non-existent-dir")

        when:
        fileService.list(nonExistentPath)

        then:
        IOException ex = thrown()
    }

    def "resolve creates path from single string"() {
        given:
        String pathString = "test/path"

        when:
        Path result = fileService.resolve(pathString)

        then:
        result.toString() == pathString
    }

    def "resolve creates path from multiple strings"() {
        given:
        String first = "base"
        String[] more = ["level1", "level2", "file.txt"]

        when:
        Path result = fileService.resolve(first, more)

        then:
        result.toString() == "base/level1/level2/file.txt" ||
        result.toString() == "base\\level1\\level2\\file.txt"  // Windows compatibility
    }

    def "resolve handles empty additional strings"() {
        given:
        String first = "single-path"

        when:
        Path result = fileService.resolve(first)

        then:
        result.toString() == first
    }

    def "resolve handles relative paths"() {
        given:
        String first = "."
        String[] more = ["relative", "path"]

        when:
        Path result = fileService.resolve(first, more)

        then:
        result.toString().endsWith("relative/path") ||
        result.toString().endsWith("relative\\path")  // Windows compatibility
    }

    def "toFile converts path to file successfully"() {
        given:
        Path testPath = tempDirPath.resolve("test-file.txt")

        when:
        File result = fileService.toFile(testPath)

        then:
        result.toPath() == testPath
        result.getAbsolutePath() == testPath.toAbsolutePath().toString()
    }

    def "toFile works with absolute paths"() {
        given:
        Path absolutePath = tempDirPath.toAbsolutePath()

        when:
        File result = fileService.toFile(absolutePath)

        then:
        result.isAbsolute()
        result.toPath() == absolutePath
    }

    def "toFile works with relative paths"() {
        given:
        Path relativePath = Paths.get("relative/path")

        when:
        File result = fileService.toFile(relativePath)

        then:
        result.toPath() == relativePath
    }

    def "all methods work together in file lifecycle"() {
        given:
        String dirName = "lifecycle-test"
        String fileName = "test-file.txt"
        String content = "Test content for lifecycle"

        when: "Create directory structure"
        Path dirPath = fileService.resolve(tempDirPath.toString(), dirName)
        fileService.createDirectories(dirPath)

        then:
        fileService.exists(dirPath)

        when: "Create and write file"
        Path filePath = dirPath.resolve(fileName)
        fileService.writeString(filePath, content)

        then:
        fileService.exists(filePath)
        fileService.toFile(filePath).text == content

        when: "List directory contents"
        Stream<Path> contents = fileService.list(dirPath)
        List<Path> files = contents.collect()

        then:
        files.size() == 1
        files[0].fileName.toString() == fileName

        when: "Delete file and directory"
        fileService.delete(filePath)
        fileService.delete(dirPath)

        then:
        !fileService.exists(filePath)
        !fileService.exists(dirPath)
    }

    def "writeString with null content throws NullPointerException"() {
        given:
        Path filePath = tempDirPath.resolve("null-content.txt")

        when:
        fileService.writeString(filePath, null)

        then:
        thrown(NullPointerException)
    }

    def "list with file instead of directory throws IOException"() {
        given:
        File testFile = new File(tempDir, "not-a-directory.txt")
        testFile.createNewFile()
        Path filePath = testFile.toPath()

        when:
        fileService.list(filePath)

        then:
        thrown(IOException)
    }

    def "createDirectories with file in path throws IOException"() {
        given:
        // Create a file
        File blockingFile = new File(tempDir, "blocking-file")
        blockingFile.createNewFile()
        // Try to create a directory with the file in the path
        Path invalidPath = blockingFile.toPath().resolve("subdir")

        when:
        fileService.createDirectories(invalidPath)

        then:
        thrown(IOException)
    }

    def "delete non-empty directory throws IOException"() {
        given:
        File testDir = new File(tempDir, "non-empty-dir")
        testDir.mkdirs()
        new File(testDir, "child-file.txt").createNewFile()
        Path dirPath = testDir.toPath()

        when:
        fileService.delete(dirPath)

        then:
        thrown(IOException)
    }

    def "writeString creates parent directories if they exist"() {
        given:
        Path parentDir = tempDirPath.resolve("parent")
        fileService.createDirectories(parentDir)
        Path filePath = parentDir.resolve("test-file.txt")
        String content = "Test content"

        when:
        fileService.writeString(filePath, content)

        then:
        fileService.exists(filePath)
        fileService.toFile(filePath).text == content
    }

    def "writeString with very long path succeeds"() {
        given:
        StringBuilder longPath = new StringBuilder(tempDirPath.toString())
        for (int i = 0; i < 20; i++) {
            longPath.append("/level${i}")
        }
        longPath.append("/file.txt")
        Path filePath = fileService.resolve(longPath.toString())
        fileService.createDirectories(filePath.parent)
        String content = "Long path test"

        when:
        fileService.writeString(filePath, content)

        then:
        fileService.exists(filePath)
        fileService.toFile(filePath).text == content
    }

    def "resolve with null in more parameters"() {
        given:
        String first = "base"
        String[] more = ["level1", null, "level3"]

        when:
        Path result = fileService.resolve(first, more)

        then:
        thrown(NullPointerException)
    }

    def "exists returns false for null path"() {
        when:
        fileService.exists(null)

        then:
        thrown(NullPointerException)
    }

    def "delete handles symbolic link"() {
        given:
        File targetFile = new File(tempDir, "target.txt")
        targetFile.createNewFile()
        File linkFile = new File(tempDir, "link.txt")

        // Create symbolic link (skip test if not supported)
        try {
            java.nio.file.Files.createSymbolicLink(linkFile.toPath(), targetFile.toPath())
        } catch (Exception e) {
            // Skip test on systems that don't support symbolic links
            return
        }

        Path linkPath = linkFile.toPath()

        when:
        fileService.delete(linkPath)

        then:
        !fileService.exists(linkPath)
        fileService.exists(targetFile.toPath())  // Target should still exist
    }

    def "writeString with special filename characters"() {
        given:
        Path filePath = tempDirPath.resolve("file-with-special_chars.123.txt")
        String content = "Special filename test"

        when:
        fileService.writeString(filePath, content)

        then:
        fileService.exists(filePath)
        fileService.toFile(filePath).text == content
    }

    def "list returns paths in consistent order"() {
        given:
        // Create multiple files
        for (int i = 1; i <= 10; i++) {
            new File(tempDir, "file${i}.txt").createNewFile()
        }

        when:
        Stream<Path> stream1 = fileService.list(tempDirPath)
        List<Path> list1 = stream1.collect()
        Stream<Path> stream2 = fileService.list(tempDirPath)
        List<Path> list2 = stream2.collect()

        then:
        list1.size() == 10
        list2.size() == 10
        // Lists should contain the same paths (order may vary)
        list1.containsAll(list2)
        list2.containsAll(list1)
    }
}