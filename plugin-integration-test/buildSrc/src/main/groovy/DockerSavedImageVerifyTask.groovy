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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Verifies that Docker image files have been saved to the specified file paths.
 * 
 * Supports multiple compression formats: .tar, .tar.gz, .tar.bz2, .tar.xz, .zip
 * 
 * This task is configuration cache compatible and uses file system operations
 * to verify saved Docker images exist.
 */
abstract class DockerSavedImageVerifyTask extends DefaultTask {

    @Input
    abstract ListProperty<String> getFilePaths()

    @Inject
    abstract ProjectLayout getLayout()

    @TaskAction
    void verifySavedImages() {
        def filePaths = getFilePaths().get()
        
        if (filePaths.isEmpty()) {
            logger.info('No saved image file paths to verify')
            return
        }

        def missingFiles = []
        def verifiedFiles = []

        for (String filePath : filePaths) {
            Path path = Paths.get(filePath)
            
            // Handle relative paths by resolving against project directory
            if (!path.isAbsolute()) {
                path = layout.projectDirectory.asFile.toPath().resolve(path)
            }

            if (Files.exists(path) && Files.isRegularFile(path)) {
                long fileSize = Files.size(path)
                logger.info("✓ Verified saved image file: ${path} (${fileSize} bytes)")
                verifiedFiles.add(filePath)
            } else {
                logger.error("✗ Missing saved image file: ${path}")
                missingFiles.add(filePath)
            }
        }

        if (!missingFiles.isEmpty()) {
            throw new RuntimeException("Failed to verify ${missingFiles.size()} saved image file(s): ${missingFiles}")
        }

        logger.lifecycle("Successfully verified ${verifiedFiles.size()} saved Docker image file(s)")

        // Verify that Docker can successfully load the saved image files
        verifyDockerLoadCapability(verifiedFiles)
    }

    /**
     * Verify that saved image files can be successfully loaded back to Docker.
     * This ensures image integrity and Docker compatibility.
     */
    private void verifyDockerLoadCapability(List<String> verifiedFiles) {
        def loadFailures = []

        for (String filePath : verifiedFiles) {
            File tempDir = null
            try {
                // Resolve relative paths
                Path path = Paths.get(filePath)
                if (!path.isAbsolute()) {
                    path = layout.projectDirectory.asFile.toPath().resolve(path)
                }

                File imageFile = path.toFile()
                File fileToLoad = imageFile

                // If it's a ZIP file, extract it first
                if (imageFile.name.toLowerCase().endsWith('.zip')) {
                    logger.info("Extracting ZIP file before load: ${filePath}")
                    
                    // Create temporary directory for extraction
                    tempDir = Files.createTempDirectory('docker-load-').toFile()
                    
                    // Extract ZIP using unzip command
                    def unzipProcess = ['unzip', '-q', imageFile.absolutePath, '-d', tempDir.absolutePath].execute()
                    def unzipExitCode = unzipProcess.waitFor()
                    
                    if (unzipExitCode != 0) {
                        def unzipStderr = unzipProcess.errorStream.text.trim()
                        throw new RuntimeException("Failed to extract ZIP file: ${unzipStderr}")
                    }
                    
                    // Find the extracted tar file (should be only file in temp dir)
                    def extractedFiles = tempDir.listFiles()
                    if (extractedFiles == null || extractedFiles.length == 0) {
                        throw new RuntimeException("No files found after extracting ZIP")
                    }
                    if (extractedFiles.length > 1) {
                        throw new RuntimeException("Multiple files found after extracting ZIP: ${extractedFiles.collect { it.name }}")
                    }
                    
                    fileToLoad = extractedFiles[0]
                    logger.info("Extracted tar file: ${fileToLoad.name}")
                }

                // Load the image (either original tar or extracted tar from ZIP)
                def process = ['docker', 'load', '-i', fileToLoad.absolutePath].execute()
                def exitCode = process.waitFor()
                def stdout = process.inputStream.text.trim()
                def stderr = process.errorStream.text.trim()

                if (exitCode == 0) {
                    logger.info("✓ Docker load verified: ${filePath} → ${stdout}")
                } else {
                    logger.error("✗ Docker load failed: ${filePath} → ${stderr}")
                    loadFailures.add("${filePath}: ${stderr}")
                }
            } catch (Exception e) {
                logger.error("✗ Docker load exception: ${filePath} → ${e.message}")
                loadFailures.add("${filePath}: ${e.message}")
            } finally {
                // Clean up temporary directory if created
                if (tempDir != null && tempDir.exists()) {
                    tempDir.deleteDir()
                }
            }
        }

        if (!loadFailures.isEmpty()) {
            throw new RuntimeException("Docker load verification failed for ${loadFailures.size()} file(s):\n${loadFailures.join('\n')}")
        }

        logger.lifecycle("Docker load verification passed for ${verifiedFiles.size()} image file(s)")
    }
}