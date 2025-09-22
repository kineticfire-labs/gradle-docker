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

import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.api.Action
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

import javax.inject.Inject

/**
 * Specification for Docker image save configuration
 */
abstract class SaveSpec {
    
    private final ObjectFactory objectFactory
    private final ProjectLayout layout
    
    @Inject
    SaveSpec(ObjectFactory objectFactory, ProjectLayout layout) {
        this.objectFactory = objectFactory
        this.layout = layout
        
        // Set defaults
        compression.convention(SaveCompression.NONE)
        pullIfMissing.convention(false)
        outputFile.convention(layout.buildDirectory.file("docker-images/image.tar"))
    }
    
    @Input
    abstract Property<SaveCompression> getCompression()
    
    @OutputFile
    abstract RegularFileProperty getOutputFile()
    
    @Input
    @Optional
    abstract Property<Boolean> getPullIfMissing()

    @Nested
    @Optional
    abstract Property<AuthSpec> getAuth()

    void auth(@DelegatesTo(AuthSpec) Closure closure) {
        def authSpec = objectFactory.newInstance(AuthSpec)
        closure.delegate = authSpec
        closure.call()
        auth.set(authSpec)
    }

    void auth(Action<AuthSpec> action) {
        def authSpec = objectFactory.newInstance(AuthSpec)
        action.execute(authSpec)
        auth.set(authSpec)
    }
}