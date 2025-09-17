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

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty

import javax.inject.Inject

/**
 * Extension for configuring Docker registry management in integration tests.
 * 
 * Provides DSL for defining test registries with various configurations:
 * - Multiple registries with different ports
 * - Authentication support
 * - Custom labels and configurations
 */
abstract class RegistryManagementExtension {

    private final Project project

    @Inject
    RegistryManagementExtension(Project project) {
        this.project = project
    }

    /**
     * List of registry configurations to start for testing
     */
    abstract ListProperty<RegistryTestFixture.RegistryConfig> getRegistryConfigs()

    /**
     * Configure a simple unauthenticated registry
     */
    void registry(String name, int port) {
        registryConfigs.add(new RegistryTestFixture.RegistryConfig(name, port))
    }

    /**
     * Configure a registry with authentication
     */
    void authenticatedRegistry(String name, int port, String username, String password) {
        def config = new RegistryTestFixture.RegistryConfig(name, port)
            .withAuth(username, password)
        registryConfigs.add(config)
    }

    /**
     * Configure a registry with custom configuration closure
     */
    void registry(String name, int port, Closure<RegistryTestFixture.RegistryConfig> configClosure) {
        def config = new RegistryTestFixture.RegistryConfig(name, port)
        configClosure.delegate = config
        configClosure.call(config)
        registryConfigs.add(config)
    }

    /**
     * Add a pre-configured registry
     */
    void add(RegistryTestFixture.RegistryConfig config) {
        registryConfigs.add(config)
    }
}