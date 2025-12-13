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

package com.kineticfire.gradle.docker.spock

import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to configure Docker Compose orchestration for Spock integration tests.
 *
 * <p>This annotation enables automatic Docker Compose stack management with configurable
 * lifecycle modes, health waiting, and state file generation.</p>
 *
 * <h3>Recommended Pattern: Configuration in build.gradle</h3>
 * <p>Define all Docker Compose configuration in build.gradle and use zero-parameter annotation:</p>
 *
 * <p><b>build.gradle:</b></p>
 * <pre>
 * {@code
 * dockerTest {
 *     composeStacks {
 *         myApp {
 *             files.from('src/integrationTest/resources/compose/app.yml')
 *             waitForHealthy {
 *                 waitForServices.set(['web-app'])
 *                 timeoutSeconds.set(60)
 *             }
 *         }
 *     }
 * }
 *
 * tasks.named('integrationTest') {
 *     usesCompose stack: "myApp", lifecycle: "class"
 * }
 * }
 * </pre>
 *
 * <p><b>Test class:</b></p>
 * <pre>
 * {@code
 * @ComposeUp  // No parameters! All config from build.gradle
 * class MyIntegrationTest extends Specification {
 *     static String baseUrl
 *
 *     def setupSpec() {
 *         def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
 *         def stateData = new JsonSlurper().parse(stateFile)
 *         def port = stateData.services['web-app'].publishedPorts[0].host
 *         baseUrl = "http://localhost:${port}"
 *     }
 *
 *     def "test 1"() { ... }  // Shares containers
 *     def "test 2"() { ... }  // Shares containers
 * }
 * }
 * </pre>
 *
 * <h3>Alternative: Annotation-Only Configuration (Backward Compatible)</h3>
 * <p>For backward compatibility, you can still specify all parameters in the annotation:</p>
 * <pre>
 * {@code
 * @ComposeUp(
 *     stackName = "myApp",
 *     composeFile = "src/integrationTest/resources/compose/app.yml",
 *     lifecycle = LifecycleMode.CLASS,
 *     waitForHealthy = ["web-app"]
 * )
 * class MyIntegrationTest extends Specification { ... }
 * }
 * </pre>
 *
 * <h3>State File Format</h3>
 * <p>The extension generates a JSON state file with container information:</p>
 * <pre>
 * {
 *   "stackName": "myApp",
 *   "projectName": "my-app-MyTest-123456",
 *   "lifecycle": "class",
 *   "timestamp": "2025-10-11T10:30:45Z",
 *   "services": {
 *     "web-app": {
 *       "containerId": "abc123",
 *       "containerName": "my-app-web-app-1",
 *       "state": "running",
 *       "publishedPorts": [{"container": 8080, "host": 32768}]
 *     }
 *   }
 * }
 * </pre>
 *
 * @see LifecycleMode
 * @see DockerComposeSpockExtension
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@ExtensionAnnotation(DockerComposeSpockExtension)
@interface ComposeUp {

    /**
     * Stack name used for state file naming and identification.
     *
     * <p>This name is used to:</p>
     * <ul>
     *   <li>Generate the state file name: {@code build/compose-state/<stackName>-state.json}</li>
     *   <li>Identify the stack in logs and error messages</li>
     * </ul>
     *
     * <p>If empty (default), the stack name will be read from the system property
     * {@code docker.compose.stack} set by {@code usesCompose} in build.gradle.</p>
     *
     * @return the stack name (default: empty = read from system property)
     */
    String stackName() default ""

    /**
     * Path to the Docker Compose file relative to the project root.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code "src/integrationTest/resources/compose/app.yml"}</li>
     *   <li>{@code "compose/integration-test.yml"}</li>
     * </ul>
     *
     * <p>If empty (default), the compose file path will be read from the system property
     * {@code docker.compose.files} set by {@code usesCompose} in build.gradle.</p>
     *
     * @return the compose file path (default: empty = read from system property)
     */
    String composeFile() default ""

    /**
     * Paths to multiple Docker Compose files relative to the project root.
     *
     * <p>Use this when you need to compose multiple files (e.g., base + override).
     * Files are applied in the order specified.</p>
     *
     * <p>If empty (default), the compose file paths will be read from the system property
     * {@code docker.compose.files} set by {@code usesCompose} in build.gradle.</p>
     *
     * @return array of compose file paths (default: empty = read from system property)
     */
    String[] composeFiles() default []

    /**
     * Lifecycle mode determining when containers start and stop.
     *
     * <p>Options:</p>
     * <ul>
     *   <li>{@link LifecycleMode#CLASS} - Start once per class (setupSpec/cleanupSpec)</li>
     *   <li>{@link LifecycleMode#METHOD} - Start fresh for each method (setup/cleanup)</li>
     * </ul>
     *
     * <p>Can be overridden by system property {@code docker.compose.lifecycle}
     * set by {@code usesCompose} in build.gradle.</p>
     *
     * @return the lifecycle mode (default: CLASS)
     */
    LifecycleMode lifecycle() default LifecycleMode.CLASS

    /**
     * Base name for the Docker Compose project.
     *
     * <p>If not specified, a unique project name is auto-generated using the pattern:</p>
     * <pre>{@code <stackName>-<className>-<timestamp>}</pre>
     *
     * <p>For METHOD lifecycle, the method name is also included:</p>
     * <pre>{@code <stackName>-<className>-<methodName>-<timestamp>}</pre>
     *
     * @return the project name base (default: auto-generated)
     */
    String projectName() default ""

    /**
     * List of service names to wait for HEALTHY status.
     *
     * <p>Services must have a health check defined in the compose file.
     * The extension will poll until all services report healthy status
     * or the timeout is reached.</p>
     *
     * <p>Example compose file health check:</p>
     * <pre>
     * services:
     *   web-app:
     *     healthcheck:
     *       test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
     *       interval: 5s
     *       timeout: 3s
     *       retries: 10
     * </pre>
     *
     * <p>If empty (default), the service list will be read from the system property
     * {@code docker.compose.waitForHealthy.services} set by {@code usesCompose} in build.gradle.</p>
     *
     * @return array of service names to wait for healthy (default: empty = read from system property)
     */
    String[] waitForHealthy() default []

    /**
     * List of service names to wait for RUNNING status.
     *
     * <p>Use this for services without health checks or when running status
     * is sufficient. The extension will poll until all services are in
     * running state or the timeout is reached.</p>
     *
     * <p>If empty (default), the service list will be read from the system property
     * {@code docker.compose.waitForRunning.services} set by {@code usesCompose} in build.gradle.</p>
     *
     * @return array of service names to wait for running (default: empty = read from system property)
     */
    String[] waitForRunning() default []

    /**
     * Timeout in seconds for wait operations.
     *
     * <p>Applies to both {@link #waitForHealthy()} and {@link #waitForRunning()}.
     * If services don't reach the desired state within this timeout, the test fails.</p>
     *
     * <p>Can be overridden by system properties {@code docker.compose.waitForHealthy.timeoutSeconds}
     * or {@code docker.compose.waitForRunning.timeoutSeconds} set by {@code usesCompose} in build.gradle.</p>
     *
     * @return timeout in seconds (default: 60)
     */
    int timeoutSeconds() default 60

    /**
     * Poll interval in seconds for wait operations.
     *
     * <p>The extension will check service status every {@code pollSeconds} seconds
     * until all services reach the desired state or the timeout is reached.</p>
     *
     * <p>Can be overridden by system properties {@code docker.compose.waitForHealthy.pollSeconds}
     * or {@code docker.compose.waitForRunning.pollSeconds} set by {@code usesCompose} in build.gradle.</p>
     *
     * @return poll interval in seconds (default: 2)
     */
    int pollSeconds() default 2
}
