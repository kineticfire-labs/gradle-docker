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
    abstract ListProperty<String> getImageReferences()

    @Input
    @org.gradle.api.tasks.Optional
    abstract Property<String> getRegistryUsername()

    @Input
    @org.gradle.api.tasks.Optional
    abstract Property<String> getRegistryPassword()

    @Input
    @org.gradle.api.tasks.Optional
    abstract Property<String> getRegistryServer()

    @TaskAction
    void verifyRegistryImages() {
        def imageReferences = getImageReferences().get()

        if (imageReferences.isEmpty()) {
            logger.info('No registry image references to verify')
            return
        }

        // Login to registry if credentials are provided
        boolean loggedIn = false
        if (getRegistryUsername().isPresent() && getRegistryPassword().isPresent()) {
            loggedIn = loginToRegistry()
        }

        try {
            def missingImages = []
            def verifiedImages = []

            for (String imageRef : imageReferences) {
                try {
                    // Use docker pull to check if image exists in registry
                    // This actually pulls the image but is more reliable than manifest inspect
                    def process = new ProcessBuilder(['docker', 'pull', imageRef])
                            .redirectErrorStream(true)
                            .start()

                    def exitCode = process.waitFor()
                    def output = process.inputStream.text.trim()

                    if (exitCode == 0) {
                        logger.info("✓ Verified registry image exists: ${imageRef}")
                        verifiedImages.add(imageRef)
                    } else {
                        logger.error("✗ Registry image not found: ${imageRef}")
                        logger.debug("Docker output: ${output}")
                        missingImages.add(imageRef)
                    }
                } catch (Exception e) {
                    logger.error("✗ Failed to verify registry image: ${imageRef}", e)
                    missingImages.add(imageRef)
                }
            }

            if (!missingImages.isEmpty()) {
                throw new RuntimeException("Expected images were not found: ${missingImages}")
            }

            logger.lifecycle("Successfully verified ${verifiedImages.size()} Docker image(s) in registry")
        } finally {
            // Logout from registry if we logged in
            if (loggedIn) {
                logoutFromRegistry()
            }
        }
    }

    private boolean loginToRegistry() {
        try {
            def server = getRegistryServer().isPresent() ? getRegistryServer().get() : null
            def loginCommand = ['docker', 'login']

            if (server) {
                loginCommand.add(server)
            }

            loginCommand.addAll(['-u', getRegistryUsername().get(), '--password-stdin'])

            def process = new ProcessBuilder(loginCommand)
                    .redirectErrorStream(true)
                    .start()

            // Write password to stdin
            process.outputStream.write(getRegistryPassword().get().bytes)
            process.outputStream.close()

            def exitCode = process.waitFor()
            def output = process.inputStream.text.trim()

            if (exitCode == 0) {
                logger.info("Successfully logged in to registry")
                return true
            } else {
                logger.warn("Failed to login to registry: ${output}")
                return false
            }
        } catch (Exception e) {
            logger.warn("Failed to login to registry", e)
            return false
        }
    }

    private void logoutFromRegistry() {
        try {
            def server = getRegistryServer().isPresent() ? getRegistryServer().get() : null
            def logoutCommand = ['docker', 'logout']

            if (server) {
                logoutCommand.add(server)
            }

            def process = new ProcessBuilder(logoutCommand)
                    .redirectErrorStream(true)
                    .start()

            def exitCode = process.waitFor()
            if (exitCode == 0) {
                logger.info("Successfully logged out from registry")
            } else {
                logger.warn("Failed to logout from registry")
            }
        } catch (Exception e) {
            logger.warn("Failed to logout from registry", e)
        }
    }
}