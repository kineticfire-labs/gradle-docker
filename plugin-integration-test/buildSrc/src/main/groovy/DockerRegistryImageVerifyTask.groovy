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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Verifies that Docker images exist in a specified Docker registry.
 * 
 * Uses Docker CLI commands to check image existence in registries.
 * 
 * Registry Support:
 * - Can check image existence without authentication for public registries like Docker Hub
 * - Will need authentication support for checking images in registries that require authentication
 * - Will need authentication support for publishing to public registries like Docker Hub
 * 
 * This task is configuration cache compatible and uses ProcessBuilder for cross-platform compatibility.
 */
abstract class DockerRegistryImageVerifyTask extends DefaultTask {

    @Input
    abstract ListProperty<String> getImageNames()
    
    @Input
    abstract Property<String> getRegistryUrl()

    @TaskAction
    void verifyRegistryImages() {
        def imageNames = getImageNames().get()
        def registryUrl = getRegistryUrl().get()
        
        if (imageNames.isEmpty()) {
            logger.info('No registry image names to verify')
            return
        }

        def missingImages = []
        def verifiedImages = []

        for (String imageName : imageNames) {
            def fullImageName = "${registryUrl}/${imageName}".toString()
            
            try {
                // Use docker manifest inspect to check if image exists in registry
                // This is a lightweight operation that doesn't pull the image
                def process = new ProcessBuilder(['docker', 'manifest', 'inspect', fullImageName])
                    .redirectErrorStream(true)
                    .start()

                def exitCode = process.waitFor()
                def output = process.inputStream.text.trim()

                if (exitCode == 0) {
                    logger.info("✓ Verified registry image exists: ${fullImageName}")
                    verifiedImages.add(imageName)
                } else {
                    logger.error("✗ Registry image not found: ${fullImageName}")
                    logger.debug("Docker output: ${output}")
                    missingImages.add(imageName)
                }
            } catch (Exception e) {
                logger.error("✗ Failed to verify registry image: ${fullImageName}", e)
                missingImages.add(imageName)
            }
        }

        if (!missingImages.isEmpty()) {
            throw new RuntimeException("Failed to verify ${missingImages.size()} registry image(s): ${missingImages}")
        }

        logger.lifecycle("Successfully verified ${verifiedImages.size()} Docker image(s) in registry: ${registryUrl}")
    }
}