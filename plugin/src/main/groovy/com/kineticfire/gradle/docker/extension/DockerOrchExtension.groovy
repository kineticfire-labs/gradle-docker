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
    private final Project project
    
    @Inject
    DockerOrchExtension(ObjectFactory objectFactory, Project project) {
        this.objectFactory = objectFactory
        this.project = project
        this.composeStacks = objectFactory.domainObjectContainer(ComposeStackSpec) { name ->
            def stackSpec = objectFactory.newInstance(ComposeStackSpec, name, project)
            // Set default project name
            stackSpec.projectName.convention("${project.name}-${stackSpec.name}")
            
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
        composeStacks.all { stackSpec ->
            validateStackSpec(stackSpec)
        }
    }
    
    private void validateStackSpec(ComposeStackSpec stackSpec) {
        // Validate compose files exist
        if (stackSpec.files.present && !stackSpec.files.get().empty) {
            stackSpec.files.get().each { file ->
                if (!file.asFile.exists()) {
                    throw new GradleException(
                        "Compose file does not exist: ${file.asFile.absolutePath} in stack '${stackSpec.name}'\\n" +
                        "Suggestion: Create the compose file or update the path"
                    )
                }
            }
        }
        
        // Validate env files if specified
        if (stackSpec.envFiles.present && !stackSpec.envFiles.get().empty) {
            stackSpec.envFiles.get().each { file ->
                if (!file.asFile.exists()) {
                    throw new GradleException(
                        "Environment file does not exist: ${file.asFile.absolutePath} in stack '${stackSpec.name}'\\n" +
                        "Suggestion: Create the env file or remove it from configuration"
                    )
                }
            }
        }
    }
}