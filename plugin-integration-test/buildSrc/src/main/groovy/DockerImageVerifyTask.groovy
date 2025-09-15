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
 * Custom task for verifying Docker images exist after integration tests.
 * 
 * This task verifies that specified Docker images were created successfully.
 * Uses ProcessBuilder for configuration cache compatibility.
 */
abstract class DockerImageVerifyTask extends DefaultTask {
    
    /**
     * List of Docker image names to verify (format: "image-name:tag")
     */
    @Input
    abstract ListProperty<String> getImageNames()
    
    @TaskAction
    void verifyImages() {
        def imagesToVerify = imageNames.get()
        
        if (imagesToVerify.isEmpty()) {
            logger.info("No images specified for verification")
            return
        }
        
        logger.info("Verifying Docker images: ${imagesToVerify}")
        
        def failedImages = []
        
        imagesToVerify.each { fullImageName ->
            try {
                // Check if image exists using ProcessBuilder (configuration cache compatible)
                def checkProcess = new ProcessBuilder('docker', 'images', '-q', fullImageName)
                    .redirectErrorStream(true)
                    .start()
                def checkOutput = checkProcess.inputStream.text.trim()
                checkProcess.waitFor()
                
                if (checkProcess.exitValue() == 0 && checkOutput) {
                    logger.lifecycle("✓ Verified image exists: ${fullImageName}")
                } else {
                    failedImages.add(fullImageName)
                    logger.lifecycle("✗ Expected image ${fullImageName} was not found!")
                }
            } catch (Exception e) {
                failedImages.add(fullImageName)
                logger.lifecycle("✗ Error checking image ${fullImageName}: ${e.message}")
            }
        }
        
        if (!failedImages.isEmpty()) {
            throw new RuntimeException("Expected images were not found: ${failedImages.join(', ')}")
        }
        
        logger.lifecycle("✓ All expected Docker images verified successfully!")
    }
}