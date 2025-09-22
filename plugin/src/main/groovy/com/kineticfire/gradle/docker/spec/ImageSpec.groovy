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

package com.kineticfire.gradle.docker.spec

import org.gradle.api.*
import org.gradle.api.model.ObjectFactory
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Copy

import javax.inject.Inject

/**
 * Specification for a Docker image configuration using Gradle 9 Provider API
 */
abstract class ImageSpec {
    
    private final String name
    private final ObjectFactory objectFactory
    private final ProviderFactory providers
    private final ProjectLayout layout
    private final Project project
    
    @Inject
    ImageSpec(String name, Project project) {
        this.name = name
        this.objectFactory = project.objects
        this.providers = project.providers
        this.layout = project.layout
        this.project = project
        
        // Set conventions for new nomenclature properties
        registry.convention("")
        namespace.convention("")
        repository.convention("")
        version.convention(providers.provider { project.version.toString() })
        // tags.convention(["latest"]) // No default - tags must be explicitly specified
        buildArgs.convention([:])
        labels.convention([:])
        sourceRef.convention("")
        
        // Set context convention
        context.convention(layout.projectDirectory.dir("src/main/docker"))
    }
    
    String getName() { 
        return name 
    }
    
    // Docker Image Nomenclature Properties (NEW)
    @Input
    @Optional
    abstract Property<String> getRegistry()
    
    @Input 
    @Optional
    abstract Property<String> getNamespace()
    
    @Input
    @Optional 
    abstract Property<String> getImageName()
    
    @Input
    @Optional
    abstract Property<String> getRepository()
    
    @Input
    abstract Property<String> getVersion()
    
    @Input
    abstract ListProperty<String> getTags()
    
    @Input
    abstract MapProperty<String, String> getLabels()
    
    // Build Configuration Properties
    @Input
    @Optional
    abstract DirectoryProperty getContext()
    
    @Internal
    TaskProvider<Task> contextTask
    
    @Input
    @Optional
    abstract RegularFileProperty getDockerfile()
    
    @Input
    @Optional
    abstract Property<String> getDockerfileName()
    
    @Input
    abstract MapProperty<String, String> getBuildArgs()
    
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    
    // Nested Specifications
    @Nested
    @Optional
    abstract Property<SaveSpec> getSave()
    
    @Nested
    @Optional
    abstract Property<PublishSpec> getPublish()
    
    // DSL helper methods for labels (NEW FEATURE)
    void label(String key, String value) {
        labels.put(key, value)
    }
    
    void label(String key, Provider<String> value) {
        labels.put(key, value)
    }
    
    void labels(Map<String, String> labelMap) {
        labels.putAll(labelMap)
    }
    
    void labels(Provider<? extends Map<String, String>> labelMapProvider) {
        labels.putAll(labelMapProvider)
    }
    
    // DSL helper methods for build args
    void buildArg(String key, String value) {
        buildArgs.put(key, value)
    }
    
    void buildArg(String key, Provider<String> value) {
        buildArgs.put(key, value)
    }
    
    void buildArgs(Map<String, String> argMap) {
        buildArgs.putAll(argMap)
    }
    
    void buildArgs(Provider<? extends Map<String, String>> argMapProvider) {
        buildArgs.putAll(argMapProvider)
    }
    
    // DSL methods for nested configuration
    void save(@DelegatesTo(SaveSpec) Closure closure) {
        def saveSpec = objectFactory.newInstance(SaveSpec, objectFactory, layout)
        closure.delegate = saveSpec
        closure.call()
        save.set(saveSpec)
    }
    
    void save(Action<SaveSpec> action) {
        def saveSpec = objectFactory.newInstance(SaveSpec, objectFactory, layout)
        action.execute(saveSpec)
        save.set(saveSpec)
    }
    
    void publish(@DelegatesTo(PublishSpec) Closure closure) {
        def publishSpec = objectFactory.newInstance(PublishSpec, objectFactory)
        closure.delegate = publishSpec
        closure.call()
        publish.set(publishSpec)
    }
    
    void publish(Action<PublishSpec> action) {
        def publishSpec = objectFactory.newInstance(PublishSpec, objectFactory)
        action.execute(publishSpec)
        publish.set(publishSpec)
    }
    
    /**
     * Configure inline context using Copy task DSL
     * Creates an anonymous Copy task to prepare build context
     */
    void context(@DelegatesTo(Copy) Closure closure) {
        // For testing purposes, create a basic copy task-like configuration
        // In real plugin usage, this would be handled during plugin configuration
        def copyTask = project.tasks.register("prepare${name.capitalize()}Context", Copy) { task ->
            task.group = 'docker'
            task.description = "Prepare build context for Docker image"
            closure.delegate = task
            closure.call()
        }
        this.contextTask = copyTask
    }
    
    /**
     * Configure inline context using Copy task Action
     */
    void context(Action<Copy> action) {
        // For testing purposes, create a basic copy task-like configuration  
        // In real plugin usage, this would be handled during plugin configuration
        def copyTask = project.tasks.register("prepare${name.capitalize()}Context", Copy) { task ->
            task.group = 'docker'
            task.description = "Prepare build context for Docker image"
            action.execute(task)
        }
        this.contextTask = copyTask
    }
}