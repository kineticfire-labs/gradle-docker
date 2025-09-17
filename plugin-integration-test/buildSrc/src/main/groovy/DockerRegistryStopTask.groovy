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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Task for stopping Docker registries after integration testing
 */
abstract class DockerRegistryStopTask extends DefaultTask {

    DockerRegistryStopTask() {
        group = 'docker registry'
        description = 'Stop Docker registries after integration testing'
    }

    @Internal
    abstract Property<RegistryTestFixture> getRegistryFixture()

    @TaskAction
    void stopRegistries() {
        def fixture = registryFixture.get()

        logger.lifecycle("Stopping all test registries")

        try {
            fixture.stopAllRegistries()
            
            // Clean up the test registries extension
            if (project.ext.hasProperty('testRegistries')) {
                project.ext.testRegistries = null
            }
            
            logger.lifecycle("✓ All test registries stopped and cleaned up")
            
        } catch (Exception e) {
            logger.error("Failed to stop test registries cleanly", e)
            // Don't rethrow - we want cleanup to complete even if some containers fail
        }
    }
}