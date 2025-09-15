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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for DockerSavedImageVerifyTask.
 * 
 * Tests task behavior for verifying saved Docker image files exist.
 * Focuses on input validation, file verification, and configuration cache compatibility.
 */
class DockerSavedImageVerifyTaskTest extends Specification {

    Project project
    DockerSavedImageVerifyTask task
    
    @TempDir
    Path tempDir

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        task = project.tasks.register('testVerifySavedDockerImages', DockerSavedImageVerifyTask).get()
    }

    def "task has correct inputs"() {
        when:
        task.filePaths.set(['test-image.tar'])
        
        then:
        task.filePaths.get() == ['test-image.tar']
    }

    def "task accepts multiple file paths"() {
        when:
        def files = ['image1.tar', 'image2.tar.gz', 'image3.tar.bz2', 'image4.tar.xz', 'image5.zip']
        task.filePaths.set(files)
        
        then:
        task.filePaths.get() == files
        task.filePaths.get().size() == 5
    }

    def "task accepts empty file path list"() {
        when:
        task.filePaths.set([])
        
        then:
        task.filePaths.get().isEmpty()
        noExceptionThrown()
    }

    def "task input is serializable for configuration cache"() {
        when:
        task.filePaths.set(['test.tar'])
        def inputValue = task.filePaths.get()
        
        then:
        inputValue instanceof List
        inputValue.every { it instanceof String }
    }

    def "task configuration is lazy"() {
        given:
        def provider = project.provider { ['lazy-image.tar'] }
        
        when:
        task.filePaths.set(provider)
        
        then:
        task.filePaths.get() == ['lazy-image.tar']
    }

    def "verifySavedImages succeeds with empty list"() {
        given:
        task.filePaths.set([])
        
        when:
        task.verifySavedImages()
        
        then:
        noExceptionThrown()
    }

    def "verifySavedImages succeeds when all files exist"() {
        given:
        def file1 = tempDir.resolve('test1.tar')
        def file2 = tempDir.resolve('test2.tar.gz')
        Files.createFile(file1)
        Files.createFile(file2)
        
        task.filePaths.set([file1.toString(), file2.toString()])
        
        when:
        task.verifySavedImages()
        
        then:
        noExceptionThrown()
    }

    def "verifySavedImages fails when file is missing"() {
        given:
        def existingFile = tempDir.resolve('exists.tar')
        def missingFile = tempDir.resolve('missing.tar')
        Files.createFile(existingFile)
        
        task.filePaths.set([existingFile.toString(), missingFile.toString()])
        
        when:
        task.verifySavedImages()
        
        then:
        RuntimeException e = thrown()
        e.message.contains('Failed to verify 1 saved image file(s)')
        e.message.contains('missing.tar')
    }

    def "verifySavedImages handles relative paths"() {
        given:
        def relativeFile = 'relative/path/test.tar'
        def absolutePath = tempDir.resolve(relativeFile)
        Files.createDirectories(absolutePath.parent)
        Files.createFile(absolutePath)
        
        task.filePaths.set([relativeFile])
        
        when:
        task.verifySavedImages()
        
        then:
        noExceptionThrown()
    }

    def "verifySavedImages validates file is regular file not directory"() {
        given:
        def directory = tempDir.resolve('not-a-file')
        Files.createDirectory(directory)
        
        task.filePaths.set([directory.toString()])
        
        when:
        task.verifySavedImages()
        
        then:
        RuntimeException e = thrown()
        e.message.contains('Failed to verify 1 saved image file(s)')
    }

    def "verifySavedImages supports multiple compression formats"() {
        given:
        def formats = ['test.tar', 'test.tar.gz', 'test.tar.bz2', 'test.tar.xz', 'test.zip']
        def filePaths = formats.collect { format ->
            def file = tempDir.resolve(format)
            Files.createFile(file)
            file.toString()
        }
        
        task.filePaths.set(filePaths)
        
        when:
        task.verifySavedImages()
        
        then:
        noExceptionThrown()
    }

    def "verifySavedImages reports multiple missing files"() {
        given:
        def missing1 = tempDir.resolve('missing1.tar')
        def missing2 = tempDir.resolve('missing2.tar.gz')
        
        task.filePaths.set([missing1.toString(), missing2.toString()])
        
        when:
        task.verifySavedImages()
        
        then:
        RuntimeException e = thrown()
        e.message.contains('Failed to verify 2 saved image file(s)')
        e.message.contains('missing1.tar')
        e.message.contains('missing2.tar.gz')
    }
}