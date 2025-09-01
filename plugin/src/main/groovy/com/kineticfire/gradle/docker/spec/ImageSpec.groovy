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
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*

import javax.inject.Inject

/**
 * Specification for a Docker image configuration
 */
abstract class ImageSpec {
    
    private final String name
    private final Project project
    
    @Inject
    ImageSpec(String name, Project project) {
        this.name = name
        this.project = project
    }
    
    String getName() { 
        return name 
    }
    
    @Input
    @Optional
    abstract DirectoryProperty getContext()
    
    @Input
    @Optional
    abstract RegularFileProperty getDockerfile()
    
    @Input
    abstract MapProperty<String, String> getBuildArgs()
    
    @Input
    abstract ListProperty<String> getTags()
    
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    
    @Nested
    @Optional
    abstract Property<SaveSpec> getSave()
    
    @Nested
    @Optional
    abstract Property<PublishSpec> getPublish()
    
    // DSL methods for nested configuration
    void save(@DelegatesTo(SaveSpec) Closure closure) {
        def saveSpec = project.objects.newInstance(SaveSpec, project)
        closure.delegate = saveSpec
        closure.call()
        save.set(saveSpec)
    }
    
    void save(Action<SaveSpec> action) {
        def saveSpec = project.objects.newInstance(SaveSpec, project)
        action.execute(saveSpec)
        save.set(saveSpec)
    }
    
    void publish(@DelegatesTo(PublishSpec) Closure closure) {
        def publishSpec = project.objects.newInstance(PublishSpec, project)
        closure.delegate = publishSpec
        closure.call()
        publish.set(publishSpec)
    }
    
    void publish(Action<PublishSpec> action) {
        def publishSpec = project.objects.newInstance(PublishSpec, project)
        action.execute(publishSpec)
        publish.set(publishSpec)
    }
}