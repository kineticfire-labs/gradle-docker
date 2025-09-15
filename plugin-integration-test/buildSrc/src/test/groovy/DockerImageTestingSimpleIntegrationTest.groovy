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
 * Simple integration tests for Docker Image Testing Library.
 * 
 * These tests verify the basic functionality using real Docker operations
 * but with existing Docker images to avoid complex build scenarios.
 * Tests clean up after themselves to avoid resource leakage.
 */
class DockerImageTestingSimpleIntegrationTest extends Specification {

    Project project
    
    @TempDir
    Path tempDir

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
    }

    def "DockerImageCleanTask executes with real Docker command"() {
        given:
        // Skip test if Docker is not available
        if (!isDockerAvailable()) {
            println("Skipping Docker integration test - Docker not available")
            return
        }
        
        // Use Alpine image as it's small and commonly available
        def testImage = 'alpine:latest'
        
        // Pull the image to ensure it exists
        pullImage(testImage)
        
        // Create and configure clean task
        def cleanTask = project.tasks.register('testCleanDockerImages', DockerImageCleanTask).get()
        cleanTask.imageNames.set([testImage])
        
        when:
        cleanTask.cleanImages()
        
        then:
        // The clean task should execute without throwing exceptions
        // Note: we don't verify image removal as Alpine might be used by other processes
        noExceptionThrown()
        
        cleanup:
        // Restore the test image for other tests
        pullImage(testImage)
    }

    def "DockerImageVerifyTask validates existing images"() {
        given:
        // Skip test if Docker is not available
        if (!isDockerAvailable()) {
            println("Skipping Docker integration test - Docker not available")
            return
        }
        
        // Use Alpine image as it's small and commonly available
        def testImage = 'alpine:latest'
        
        // Ensure the image exists
        pullImage(testImage)
        
        // Create and configure verify task
        def verifyTask = project.tasks.register('testVerifyDockerImages', DockerImageVerifyTask).get()
        verifyTask.imageNames.set([testImage])
        
        when:
        verifyTask.verifyImages()
        
        then:
        // Should not throw exception as image exists
        noExceptionThrown()
    }

    def "DockerImageVerifyTask fails for non-existent images"() {
        given:
        // Skip test if Docker is not available
        if (!isDockerAvailable()) {
            println("Skipping Docker integration test - Docker not available")
            return
        }
        
        // Use a non-existent image name
        def nonExistentImage = 'non-existent-image:non-existent-tag'
        
        // Create and configure verify task
        def verifyTask = project.tasks.register('testVerifyDockerImages', DockerImageVerifyTask).get()
        verifyTask.imageNames.set([nonExistentImage])
        
        when:
        verifyTask.verifyImages()
        
        then:
        // Should throw RuntimeException as image doesn't exist
        RuntimeException e = thrown()
        e.message.contains('Expected images were not found')
    }

    def "DockerSavedImageVerifyTask validates file existence"() {
        given:
        // Create test files that simulate saved Docker images
        def testFile1 = tempDir.resolve('test-image.tar')
        def testFile2 = tempDir.resolve('test-image.tar.gz')
        
        Files.write(testFile1, 'fake docker image content'.bytes)
        Files.write(testFile2, 'fake compressed docker image content'.bytes)
        
        // Create and configure saved verify task
        def savedTask = project.tasks.register('testVerifySavedDockerImages', DockerSavedImageVerifyTask).get()
        savedTask.filePaths.set([testFile1.toString(), testFile2.toString()])
        
        when:
        savedTask.verifySavedImages()
        
        then:
        // Should not throw exception as files exist
        noExceptionThrown()
    }

    def "DockerSavedImageVerifyTask fails for non-existent files"() {
        given:
        def nonExistentFile1 = tempDir.resolve('missing-1.tar')
        def nonExistentFile2 = tempDir.resolve('missing-2.tar.gz')
        
        // Create and configure saved verify task
        def savedTask = project.tasks.register('testVerifySavedDockerImages', DockerSavedImageVerifyTask).get()
        savedTask.filePaths.set([nonExistentFile1.toString(), nonExistentFile2.toString()])
        
        when:
        savedTask.verifySavedImages()
        
        then:
        // Should throw RuntimeException as files don't exist
        RuntimeException e = thrown()
        e.message.contains('Failed to verify 2 saved image file(s)')
    }

    def "DockerRegistryImageVerifyTask handles Docker Hub public images"() {
        given:
        // Skip test if Docker is not available
        if (!isDockerAvailable()) {
            println("Skipping Docker integration test - Docker not available")
            return
        }
        
        // Test with a known public image from Docker Hub
        // Note: This tests registry verification without authentication
        def publicImage = 'alpine:latest'
        def registryUrl = 'docker.io'
        
        // Create and configure registry verify task
        def registryTask = project.tasks.register('testVerifyRegistryDockerImages', DockerRegistryImageVerifyTask).get()
        registryTask.imageNames.set([publicImage])
        registryTask.registryUrl.set(registryUrl)
        
        when:
        registryTask.verifyRegistryImages()
        
        then:
        // Should not throw exception as Alpine exists in Docker Hub
        // Note: This demonstrates checking image existence without authentication
        // Future enhancement: Will need authentication support for publishing to registries
        noExceptionThrown()
    }

    // Helper methods

    private boolean isDockerAvailable() {
        try {
            def process = new ProcessBuilder('docker', '--version').start()
            return process.waitFor() == 0
        } catch (Exception e) {
            return false
        }
    }

    private void pullImage(String imageName) {
        try {
            def process = new ProcessBuilder('docker', 'pull', imageName)
                .redirectErrorStream(true)
                .start()
            process.waitFor()
        } catch (Exception e) {
            // Ignore pull failures - image might already exist
        }
    }
}