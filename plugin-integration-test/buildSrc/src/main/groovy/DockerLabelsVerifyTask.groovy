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
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Custom task for verifying Docker labels in images after integration tests.
 *
 * This task verifies that specified Docker images contain expected labels
 * by parsing the docker inspect JSON output. Supports verification of images
 * with one or multiple custom labels.
 *
 * Uses ProcessBuilder for configuration cache compatibility.
 * Uses subset matching - verifies expected labels exist but allows additional
 * labels from base images and Dockerfile LABEL instructions.
 */
abstract class DockerLabelsVerifyTask extends DefaultTask {

    /**
     * List of Docker image names to verify (format: "image-name:tag")
     */
    @Input
    abstract ListProperty<String> getImageNames()

    /**
     * Map of image references to their expected labels.
     * Format: ['image:tag': ['label.key': 'expected_value', ...]]
     * Empty map for an image means skip verification (no custom labels).
     */
    @Input
    abstract MapProperty<String, Map<String, String>> getExpectedLabels()

    @TaskAction
    void verifyLabels() {
        def imagesToVerify = imageNames.get()
        def expectedLabelsMap = expectedLabels.get()

        if (imagesToVerify.isEmpty()) {
            logger.info("No images specified for label verification")
            return
        }

        logger.info("Verifying labels for Docker images: ${imagesToVerify}")

        def failedImages = []

        for (imageRef in imagesToVerify) {
            try {
                def expected = expectedLabelsMap[imageRef] ?: [:]
                def actual = extractLabelsFromImage(imageRef)

                verifyExpectedLabelsPresent(imageRef, expected, actual)
            } catch (Exception e) {
                failedImages.add([image: imageRef, error: e.message])
                logger.lifecycle("✗ Failed verification for ${imageRef}: ${e.message}")
            }
        }

        if (!failedImages.isEmpty()) {
            def errorMsg = new StringBuilder()
            errorMsg.append("Label verification failed for ${failedImages.size()} image(s):\n")
            failedImages.each { failure ->
                errorMsg.append("  - ${failure.image}: ${failure.error}\n")
            }
            throw new RuntimeException(errorMsg.toString())
        }

        logger.lifecycle("✓ All labels verified successfully for ${imagesToVerify.size()} image(s)!")
    }

    /**
     * Extract labels from docker inspect output.
     *
     * @param imageRef Full image reference (e.g., "my-app:1.0.0")
     * @return Map of label key to value
     */
    private Map<String, String> extractLabelsFromImage(String imageRef) {
        logger.info("Extracting labels from ${imageRef}")

        // Execute docker inspect command
        def process = new ProcessBuilder('docker', 'inspect', imageRef,
                                         '--format', '{{json .Config.Labels}}')
            .redirectErrorStream(true)
            .start()

        def output = process.inputStream.text
        process.waitFor()

        if (process.exitValue() != 0) {
            throw new RuntimeException("""
Failed to inspect ${imageRef}.
Make sure the image exists by running the build task first.
Docker output: ${output}
""")
        }

        // Parse JSON output
        def slurper = new groovy.json.JsonSlurper()
        def labels = output.trim() ? slurper.parseText(output) : [:]

        logger.info("Extracted ${labels.size()} label(s) from ${imageRef}: ${labels.keySet()}")

        return labels
    }

    /**
     * Verify expected labels are present with correct values.
     * Uses subset matching - allows additional labels from base images.
     *
     * @param imageRef Image reference being verified
     * @param expected Expected labels
     * @param actual Actual labels from inspect
     */
    private void verifyExpectedLabelsPresent(String imageRef, Map expected, Map actual) {
        if (expected.isEmpty()) {
            logger.info("No expected labels specified for ${imageRef}, skipping verification")
            return
        }

        // Check each expected label exists with correct value
        expected.each { labelKey, expectedValue ->
            if (!actual.containsKey(labelKey)) {
                throw new RuntimeException("""
Missing label '${labelKey}' in ${imageRef}.
  Expected labels: ${expected.keySet()}
  Actual labels: ${actual.keySet()}
""")
            }

            if (actual[labelKey] != expectedValue) {
                throw new RuntimeException("""
Label value mismatch for '${labelKey}' in ${imageRef}:
  Expected: '${expectedValue}'
  Actual: '${actual[labelKey]}'
""")
            }
        }

        def extraLabels = actual.keySet() - expected.keySet()
        if (extraLabels) {
            logger.lifecycle(
                "✓ Verified ${expected.size()} expected label(s) in ${imageRef} " +
                "(${extraLabels.size()} additional labels from base image/Dockerfile)"
            )
        } else {
            logger.lifecycle("✓ Verified ${expected.size()} label(s) for ${imageRef}")
        }
    }
}
