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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Custom task for verifying Docker build arguments in images after integration tests.
 *
 * This task verifies that specified Docker images were built with expected build arguments
 * by parsing the docker history output. Supports verification of images with zero, one, or
 * multiple build args.
 *
 * Uses ProcessBuilder for configuration cache compatibility.
 */
abstract class DockerBuildArgsVerifyTask extends DefaultTask {

    /**
     * List of Docker image names to verify (format: "image-name:tag")
     */
    @Input
    abstract ListProperty<String> getImageNames()

    /**
     * Map of image references to their expected build args.
     * Format: ['image:tag': ['ARG_NAME': 'expected_value', ...]]
     * Empty map for an image means expect zero build args.
     */
    @Input
    abstract MapProperty<String, Map<String, String>> getExpectedBuildArgs()

    /**
     * Whether to use strict mode (exact match) or allow additional args.
     * Default: true (exact match)
     */
    @Input
    @Optional
    abstract Property<Boolean> getStrictMode()

    @TaskAction
    void verifyBuildArgs() {
        def imagesToVerify = imageNames.get()
        def expectedArgs = expectedBuildArgs.get()
        def strict = strictMode.getOrElse(true)

        if (imagesToVerify.isEmpty()) {
            logger.info("No images specified for build args verification")
            return
        }

        logger.info("Verifying build args for Docker images: ${imagesToVerify}")
        logger.info("Strict mode: ${strict}")

        def failedImages = []

        for (imageRef in imagesToVerify) {
            try {
                def expected = expectedArgs[imageRef] ?: [:]
                def actual = extractBuildArgsFromHistory(imageRef)

                if (strict) {
                    verifyExactMatch(imageRef, expected, actual)
                } else {
                    verifySubsetMatch(imageRef, expected, actual)
                }
            } catch (Exception e) {
                failedImages.add([image: imageRef, error: e.message])
                logger.lifecycle("✗ Failed verification for ${imageRef}: ${e.message}")
            }
        }

        if (!failedImages.isEmpty()) {
            def errorMsg = new StringBuilder()
            errorMsg.append("Build args verification failed for ${failedImages.size()} image(s):\n")
            failedImages.each { failure ->
                errorMsg.append("  - ${failure.image}: ${failure.error}\n")
            }
            throw new RuntimeException(errorMsg.toString())
        }

        logger.lifecycle("✓ All build args verified successfully for ${imagesToVerify.size()} image(s)!")
    }

    /**
     * Extract build args from docker history output.
     *
     * @param imageRef Full image reference (e.g., "my-app:1.0.0")
     * @return Map of arg name to value
     */
    private Map<String, String> extractBuildArgsFromHistory(String imageRef) {
        logger.info("Extracting build args from history for ${imageRef}")

        // Execute docker history command
        def process = new ProcessBuilder('docker', 'history', imageRef, '--no-trunc',
                                         '--format', '{{.CreatedBy}}')
            .redirectErrorStream(true)
            .start()

        def output = process.inputStream.text
        process.waitFor()

        if (process.exitValue() != 0) {
            throw new RuntimeException("""
Failed to get docker history for ${imageRef}.
Make sure the image exists by running the build task first.
Docker output: ${output}
""")
        }

        // Parse each line looking for build args pattern: |N ARG1=val1 ARG2=val2 /bin/sh -c ...
        def buildArgPattern = ~/\|(\d+)\s+(.+?)\s+\/bin\/sh/

        for (line in output.split('\n')) {
            def matcher = buildArgPattern.matcher(line)
            if (matcher.find()) {
                def count = matcher.group(1).toInteger()
                def argsString = matcher.group(2)

                logger.info("Found build args in history: count=${count}, args='${argsString}'")

                // Parse "ARG1=val1 ARG2=val2" into map
                def args = parseArgString(argsString)
                logger.info("Parsed build args: ${args}")

                return args
            }
        }

        // No build args found in history
        logger.info("No build args found in history for ${imageRef}")
        return [:]
    }

    /**
     * Parse arg string into map.
     * Format: "ARG1=value1 ARG2=value2 ARG3=value with spaces"
     *
     * @param argsString String containing space-separated ARG=value pairs
     * @return Map of arg name to value
     */
    private Map<String, String> parseArgString(String argsString) {
        def args = [:]

        // Split by spaces that are followed by uppercase letters and equals
        // This handles values with spaces: ARG1=val1 ARG2=value with spaces ARG3=val3
        def parts = argsString.split(/\s+(?=[A-Z_]+=)/)

        parts.each { part ->
            def equalIndex = part.indexOf('=')
            if (equalIndex > 0) {
                def argName = part.substring(0, equalIndex)
                def argValue = part.substring(equalIndex + 1)
                args[argName] = argValue
            }
        }

        return args
    }

    /**
     * Verify exact match: expected args must exactly match actual args.
     *
     * @param imageRef Image reference being verified
     * @param expected Expected build args
     * @param actual Actual build args from history
     */
    private void verifyExactMatch(String imageRef, Map expected, Map actual) {
        // Check arg count matches
        if (expected.size() != actual.size()) {
            throw new RuntimeException("""
Build args count mismatch for ${imageRef}:
  Expected ${expected.size()} arg(s): ${expected.keySet()}
  Found ${actual.size()} arg(s): ${actual.keySet()}
""")
        }

        // Check each expected arg exists with correct value
        expected.each { argName, expectedValue ->
            if (!actual.containsKey(argName)) {
                throw new RuntimeException("""
Missing build arg '${argName}' in ${imageRef}.
  Expected args: ${expected.keySet()}
  Actual args: ${actual.keySet()}
""")
            }

            if (actual[argName] != expectedValue) {
                throw new RuntimeException("""
Build arg value mismatch for '${argName}' in ${imageRef}:
  Expected: '${expectedValue}'
  Actual: '${actual[argName]}'
""")
            }
        }

        logger.lifecycle("✓ Verified ${expected.size()} build arg(s) for ${imageRef}")
    }

    /**
     * Verify subset match: expected args must be present (allows additional args).
     *
     * @param imageRef Image reference being verified
     * @param expected Expected build args
     * @param actual Actual build args from history
     */
    private void verifySubsetMatch(String imageRef, Map expected, Map actual) {
        // Verify each expected arg is present with correct value
        expected.each { argName, expectedValue ->
            if (!actual.containsKey(argName)) {
                throw new RuntimeException("""
Missing build arg '${argName}' in ${imageRef}.
  Expected args: ${expected.keySet()}
  Actual args: ${actual.keySet()}
""")
            }

            if (actual[argName] != expectedValue) {
                throw new RuntimeException("""
Build arg value mismatch for '${argName}' in ${imageRef}:
  Expected: '${expectedValue}'
  Actual: '${actual[argName]}'
""")
            }
        }

        def extraArgs = actual.keySet() - expected.keySet()
        if (extraArgs) {
            logger.lifecycle(
                "✓ Verified ${expected.size()} expected build arg(s) in ${imageRef} " +
                "(${extraArgs.size()} additional arg(s) present: ${extraArgs})"
            )
        } else {
            logger.lifecycle("✓ Verified ${expected.size()} build arg(s) for ${imageRef}")
        }
    }
}
