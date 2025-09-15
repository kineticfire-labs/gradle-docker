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

package com.kineticfire.gradle.docker.integration.appimage

import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Integration tests for Docker save/tag functionality.
 * 
 * These tests use real Docker images built from the integration test application to validate:
 * - Image save operations with various compression formats
 * - Image tagging operations with sourceRef and pullIfMissing
 * - Build -> tag -> save workflows
 * - SourceRef -> tag -> save workflows
 * 
 * Prerequisites:
 * - Docker daemon running
 * - Test image built from integration test application
 */
@Stepwise
@IgnoreIf({ !isDockerAvailable() })
class DockerSaveTagIntegrationIT extends Specification {

    @Shared
    String testImage = "gradle-docker-test-app:latest"
    
    @Shared
    String testImageId
    
    @Shared
    File tempDir
    
    @Shared
    boolean setupComplete = false

    static boolean isDockerAvailable() {
        try {
            def process = ["docker", "--version"].execute()
            process.waitFor()
            return process.exitValue() == 0
        } catch (Exception e) {
            return false
        }
    }

    def setupSpec() {
        tempDir = Files.createTempDirectory("docker-save-tag-test").toFile()
        
        // Build test image for integration tests
        buildTestImage()
        setupComplete = true
    }

    def cleanupSpec() {
        if (tempDir && tempDir.exists()) {
            tempDir.deleteDir()
        }
        
        // Clean up test images
        if (testImageId) {
            try {
                ["docker", "rmi", testImageId].execute().waitFor()
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    def setup() {
        assume:
        setupComplete
    }

    // ===== BUILD TEST IMAGE HELPER =====

    private void buildTestImage() {
        // Create a simple test Dockerfile
        def dockerfile = new File(tempDir, "Dockerfile")
        dockerfile.text = """
FROM eclipse-temurin:21-jre-alpine
RUN echo "Test image for Docker save/tag integration tests" > /test-marker
CMD ["echo", "Integration test image"]
""".trim()

        // Build the test image
        def process = ["docker", "build", "-t", testImage, tempDir.absolutePath].execute()
        def exitCode = process.waitFor()
        
        if (exitCode != 0) {
            def error = process.errorStream.text
            throw new RuntimeException("Failed to build test image: $error")
        }
        
        // Get the image ID
        def inspectProcess = ["docker", "inspect", "--format={{.Id}}", testImage].execute()
        inspectProcess.waitFor()
        testImageId = inspectProcess.text.trim()
    }

    // ===== IMAGE SAVE TESTS =====

    def "should save image with GZIP compression"() {
        given:
        def outputFile = new File(tempDir, "test-gzip.tar.gz")

        when:
        def process = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerSaveTestApp",
            "-PtestImageName=$testImage",
            "-PoutputFile=$outputFile.absolutePath",
            "-Pcompression=gzip"
        ].execute(null, new File("."))
        def exitCode = process.waitFor()

        then:
        exitCode == 0
        outputFile.exists()
        outputFile.size() > 0
        outputFile.name.endsWith(".tar.gz")
    }

    def "should save image with BZIP2 compression"() {
        given:
        def outputFile = new File(tempDir, "test-bzip2.tar.bz2")

        when:
        def process = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerSaveTestApp",
            "-PtestImageName=$testImage",
            "-PoutputFile=$outputFile.absolutePath",
            "-Pcompression=bzip2"
        ].execute(null, new File("."))
        def exitCode = process.waitFor()

        then:
        exitCode == 0
        outputFile.exists()
        outputFile.size() > 0
        outputFile.name.endsWith(".tar.bz2")
    }

    def "should save image with XZ compression"() {
        given:
        def outputFile = new File(tempDir, "test-xz.tar.xz")

        when:
        def process = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerSaveTestApp",
            "-PtestImageName=$testImage",
            "-PoutputFile=$outputFile.absolutePath",
            "-Pcompression=xz"
        ].execute(null, new File("."))
        def exitCode = process.waitFor()

        then:
        exitCode == 0
        outputFile.exists()
        outputFile.size() > 0
        outputFile.name.endsWith(".tar.xz")
    }

    def "should save image with ZIP compression"() {
        given:
        def outputFile = new File(tempDir, "test-zip.zip")

        when:
        def process = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerSaveTestApp",
            "-PtestImageName=$testImage",
            "-PoutputFile=$outputFile.absolutePath",
            "-Pcompression=zip"
        ].execute(null, new File("."))
        def exitCode = process.waitFor()

        then:
        exitCode == 0
        outputFile.exists()
        outputFile.size() > 0
        outputFile.name.endsWith(".zip")
    }

    def "should save image with no compression"() {
        given:
        def outputFile = new File(tempDir, "test-none.tar")

        when:
        def process = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerSaveTestApp",
            "-PtestImageName=$testImage",
            "-PoutputFile=$outputFile.absolutePath",
            "-Pcompression=none"
        ].execute(null, new File("."))
        def exitCode = process.waitFor()

        then:
        exitCode == 0
        outputFile.exists()
        outputFile.size() > 0
        outputFile.name.endsWith(".tar")
    }

    // ===== IMAGE TAG TESTS =====

    def "should tag image with multiple tags"() {
        given:
        def newTags = ["${testImage}-tagged:1.0.0", "${testImage}-tagged:latest"]

        when:
        def process = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerTagTestApp",
            "-PsourceImageName=$testImage",
            "-PtargetTags=${newTags.join(',')}"
        ].execute(null, new File("."))
        def exitCode = process.waitFor()

        then:
        exitCode == 0
        
        and: "tagged images exist"
        for (tag in newTags) {
            def inspectProcess = ["docker", "inspect", tag].execute()
            inspectProcess.waitFor() == 0
        }

        cleanup:
        // Clean up tagged images
        newTags.each { tag ->
            try {
                ["docker", "rmi", tag].execute().waitFor()
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    def "should tag image from sourceRef"() {
        given:
        def sourceRef = testImage
        def newTag = "${testImage}-sourceref:latest"

        when:
        def process = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerTagTestApp",
            "-PsourceRef=$sourceRef",
            "-PtargetTags=$newTag"
        ].execute(null, new File("."))
        def exitCode = process.waitFor()

        then:
        exitCode == 0
        
        and: "tagged image exists"
        def inspectProcess = ["docker", "inspect", newTag].execute()
        inspectProcess.waitFor() == 0

        cleanup:
        try {
            ["docker", "rmi", newTag].execute().waitFor()
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    // ===== WORKFLOW TESTS =====

    def "should complete build -> tag -> save workflow"() {
        given:
        def workflowImage = "${testImage}-workflow:latest"
        def outputFile = new File(tempDir, "workflow-test.tar.gz")

        when: "build image"
        def buildProcess = ["docker", "build", "-t", workflowImage, tempDir.absolutePath].execute()
        buildProcess.waitFor()

        then:
        buildProcess.exitValue() == 0

        when: "tag image"
        def tagProcess = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerTagTestApp",
            "-PsourceImageName=$workflowImage",
            "-PtargetTags=${workflowImage}-tagged:1.0.0"
        ].execute(null, new File("."))
        tagProcess.waitFor()

        then:
        tagProcess.exitValue() == 0

        when: "save image"
        def saveProcess = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerSaveTestApp",
            "-PtestImageName=${workflowImage}-tagged:1.0.0",
            "-PoutputFile=$outputFile.absolutePath",
            "-Pcompression=gzip"
        ].execute(null, new File("."))
        saveProcess.waitFor()

        then:
        saveProcess.exitValue() == 0
        outputFile.exists()
        outputFile.size() > 0

        cleanup:
        try {
            ["docker", "rmi", workflowImage].execute().waitFor()
            ["docker", "rmi", "${workflowImage}-tagged:1.0.0"].execute().waitFor()
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    def "should complete sourceRef -> tag -> save workflow"() {
        given:
        def sourceRef = testImage
        def taggedImage = "${testImage}-sourceref-workflow:latest"
        def outputFile = new File(tempDir, "sourceref-workflow-test.tar.gz")

        when: "tag from sourceRef"
        def tagProcess = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerTagTestApp",
            "-PsourceRef=$sourceRef",
            "-PtargetTags=$taggedImage"
        ].execute(null, new File("."))
        tagProcess.waitFor()

        then:
        tagProcess.exitValue() == 0

        when: "save tagged image"
        def saveProcess = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerSaveTestApp",
            "-PtestImageName=$taggedImage",
            "-PoutputFile=$outputFile.absolutePath",
            "-Pcompression=gzip"
        ].execute(null, new File("."))
        saveProcess.waitFor()

        then:
        saveProcess.exitValue() == 0
        outputFile.exists()
        outputFile.size() > 0

        cleanup:
        try {
            ["docker", "rmi", taggedImage].execute().waitFor()
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    // ===== VALIDATION TESTS =====

    def "saved image can be loaded back into Docker"() {
        given:
        def saveImage = "${testImage}-load-test:latest"
        def outputFile = new File(tempDir, "load-test.tar.gz")

        // First tag our test image with a unique name for this test
        ["docker", "tag", testImage, saveImage].execute().waitFor()

        when: "save image"
        def saveProcess = [
            "./gradlew", "-p", "plugin-integration-test/app-image", 
            "dockerSaveTestApp",
            "-PtestImageName=$saveImage",
            "-PoutputFile=$outputFile.absolutePath",
            "-Pcompression=gzip"
        ].execute(null, new File("."))
        saveProcess.waitFor()

        then:
        saveProcess.exitValue() == 0
        outputFile.exists()

        when: "remove image from Docker"
        def removeProcess = ["docker", "rmi", saveImage].execute()
        removeProcess.waitFor()

        then:
        removeProcess.exitValue() == 0

        when: "load image back"
        def loadProcess = ["docker", "load", "-i", outputFile.absolutePath].execute()
        loadProcess.waitFor()

        then:
        loadProcess.exitValue() == 0

        and: "image is available again"
        def inspectProcess = ["docker", "inspect", saveImage].execute()
        inspectProcess.waitFor() == 0

        cleanup:
        try {
            ["docker", "rmi", saveImage].execute().waitFor()
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    // ===== ERROR HANDLING TESTS =====

    def "should handle missing source image gracefully"() {
        given:
        def nonExistentImage = "nonexistent:latest"
        def outputFile = new File(tempDir, "missing-image.tar.gz")

        when:
        def process = [
            "./gradlew", "-p", "plugin-integration-test/app-image",
            "dockerSaveTestApp",
            "-PtestImageName=$nonExistentImage",
            "-PoutputFile=$outputFile.absolutePath",
            "-Pcompression=gzip"
        ].execute(null, new File("."))
        def exitCode = process.waitFor()

        then:
        exitCode != 0 // Should fail
        !outputFile.exists() // No output file should be created
    }
}