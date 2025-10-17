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

package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

/**
 * Extension for Docker Compose orchestration (dockerOrch { } DSL)
 */
abstract class DockerOrchExtension {
    
    private final NamedDomainObjectContainer<ComposeStackSpec> composeStacks
    private final ObjectFactory objectFactory
    
    @Inject
    DockerOrchExtension(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory
        this.composeStacks = objectFactory.domainObjectContainer(ComposeStackSpec) { name ->
            def stackSpec = objectFactory.newInstance(ComposeStackSpec, name, objectFactory)
            // Note: Default project name should be set by the plugin using providers
            // to avoid capturing Project reference during configuration cache
            
            // Note: waitForHealthy and waitForRunning default configurations are handled by WaitSpec defaults
            return stackSpec
        }
    }
    
    NamedDomainObjectContainer<ComposeStackSpec> getComposeStacks() {
        return composeStacks
    }
    
    // Alias for tests that expect composeConfigs
    NamedDomainObjectContainer<ComposeStackSpec> getComposeConfigs() {
        return composeStacks
    }
    
    void composeStacks(@DelegatesTo(NamedDomainObjectContainer) Closure closure) {
        composeStacks.configure(closure)
    }
    
    void composeStacks(Action<NamedDomainObjectContainer<ComposeStackSpec>> action) {
        action.execute(composeStacks)
    }
    
    // Alias methods for tests that expect composeConfigs
    void composeConfigs(@DelegatesTo(NamedDomainObjectContainer) Closure closure) {
        composeStacks.configure(closure)
    }
    
    void composeConfigs(Action<NamedDomainObjectContainer<ComposeStackSpec>> action) {
        action.execute(composeStacks)
    }
    
    
    /**
     * Validation method called during configuration
     */
    void validate() {
        composeStacks.each { stackSpec ->
            validateStackSpec(stackSpec)
        }
    }
    
    void validateStackSpec(ComposeStackSpec stackSpec) {
        // Check if any configuration is present using original files collection
        def hasFiles = !stackSpec.files.empty
        def hasComposeFile = stackSpec.composeFile.present
        def hasComposeFiles = stackSpec.composeFiles.present && !stackSpec.composeFiles.get().empty
        def hasComposeFileCollection = !stackSpec.composeFileCollection.empty
        
        if (!hasFiles && !hasComposeFile && !hasComposeFiles && !hasComposeFileCollection) {
            throw new GradleException(
                "Stack '${stackSpec.name}': No compose files specified"
            )
        }
        
        // Validate compose files exist (use the files collection for immediate validation)
        if (hasFiles) {
            stackSpec.files.each { file ->
                if (!file.exists()) {
                    throw new GradleException(
                        "Stack '${stackSpec.name}': Compose file does not exist: ${file.absolutePath}"
                    )
                }
            }
        }
        
        // Validate env files if specified  
        validateEnvFiles(stackSpec)
    }
    
    private void validateEnvFiles(ComposeStackSpec stackSpec) {
        if (!stackSpec.envFiles.empty) {
            stackSpec.envFiles.each { file ->
                if (!file.exists()) {
                    throw new GradleException(
                        "Stack '${stackSpec.name}': Environment file does not exist: ${file.absolutePath}"
                    )
                }
                if (!file.canRead()) {
                    throw new GradleException(
                        "Stack '${stackSpec.name}': Environment file is not readable: ${file.absolutePath}"
                    )
                }
            }
        }
    }
}