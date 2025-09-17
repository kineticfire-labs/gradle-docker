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
 * Task for emergency cleanup of orphaned Docker registries
 */
abstract class DockerRegistryCleanupTask extends DefaultTask {

    DockerRegistryCleanupTask() {
        group = 'docker registry'
        description = 'Emergency cleanup of orphaned Docker registries'
    }

    @Internal
    abstract Property<RegistryTestFixture> getRegistryFixture()

    @TaskAction
    void emergencyCleanup() {
        def fixture = registryFixture.get()

        logger.lifecycle("Performing emergency cleanup of orphaned Docker registries")

        try {
            fixture.emergencyCleanup()
            logger.lifecycle("✓ Emergency cleanup completed")
            
        } catch (Exception e) {
            logger.error("Emergency cleanup encountered errors", e)
            // Don't rethrow - this is best-effort cleanup
        }
    }
}