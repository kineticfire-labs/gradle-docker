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
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Custom task for cleaning Docker images before integration tests.
 * 
 * This task removes specified Docker images to ensure a clean test state.
 * Uses ProcessBuilder for configuration cache compatibility.
 */
abstract class DockerImageCleanTask extends DefaultTask {
    
    /**
     * List of Docker image names to clean (format: "image-name:tag")
     */
    @Input
    abstract ListProperty<String> getImageNames()
    
    @TaskAction
    void cleanImages() {
        def imagesToClean = imageNames.get()
        
        if (imagesToClean.isEmpty()) {
            logger.info("No images specified for cleaning")
            return
        }
        
        logger.info("Cleaning Docker images: ${imagesToClean}")
        
        imagesToClean.each { fullImageName ->
            try {
                // Check if image exists using ProcessBuilder (configuration cache compatible)
                def checkProcess = new ProcessBuilder('docker', 'images', '-q', fullImageName)
                    .redirectErrorStream(true)
                    .start()
                def checkOutput = checkProcess.inputStream.text.trim()
                checkProcess.waitFor()
                
                if (checkProcess.exitValue() == 0 && checkOutput) {
                    logger.lifecycle("Removing existing image: ${fullImageName}")
                    def removeProcess = new ProcessBuilder('docker', 'rmi', '-f', fullImageName)
                        .redirectErrorStream(true)
                        .start()
                    removeProcess.waitFor()
                    if (removeProcess.exitValue() == 0) {
                        logger.lifecycle("Successfully removed image: ${fullImageName}")
                    } else {
                        logger.warn("Warning: Could not remove image ${fullImageName}")
                    }
                } else {
                    logger.lifecycle("Image ${fullImageName} does not exist, skipping removal")
                }
            } catch (Exception e) {
                logger.warn("Warning: Could not process image ${fullImageName}: ${e.message}")
            }
        }
    }
}