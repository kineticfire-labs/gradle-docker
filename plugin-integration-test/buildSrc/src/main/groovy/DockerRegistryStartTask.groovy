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
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Task for starting Docker registries for integration testing
 */
abstract class DockerRegistryStartTask extends DefaultTask {

    DockerRegistryStartTask() {
        group = 'docker registry'
        description = 'Start Docker registries for integration testing'
    }

    @Internal
    abstract Property<RegistryTestFixture> getRegistryFixture()

    @Input
    abstract ListProperty<RegistryTestFixture.RegistryConfig> getRegistryConfigs()

    @TaskAction
    void startRegistries() {
        def fixture = registryFixture.get()
        def configs = registryConfigs.get()

        logger.lifecycle("Starting ${configs.size()} test registries")

        try {
            def registries = fixture.startTestRegistries(configs)
            
            // Store registry information for use by other tasks
            project.ext.testRegistries = registries
            
            logger.lifecycle("✓ All test registries started successfully")
            registries.each { name, info ->
                logger.lifecycle("  - ${name}: localhost:${info.port} (${info.containerId})")
            }
            
        } catch (Exception e) {
            logger.error("Failed to start test registries", e)
            throw e
        }
    }
}