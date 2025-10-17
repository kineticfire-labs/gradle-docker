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

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

import java.io.File
import java.util.Arrays
import javax.inject.Inject

/**
 * Specification for a Docker Compose stack configuration
 */
abstract class ComposeStackSpec {
    
    private final String name
    private final ObjectFactory objectFactory
    
    @Inject
    ComposeStackSpec(String name, ObjectFactory objectFactory) {
        this.name = name
        this.objectFactory = objectFactory
    }
    
    String getName() { 
        return name 
    }
    
    // Properties expected by tests
    abstract RegularFileProperty getComposeFile()
    abstract ListProperty<String> getComposeFiles()
    abstract ConfigurableFileCollection getComposeFileCollection()
    abstract RegularFileProperty getEnvFile()
    abstract ListProperty<String> getProfiles()
    abstract ListProperty<String> getServices()
    abstract MapProperty<String, String> getEnvironment()
    
    // Original properties for future plugin functionality
    abstract ConfigurableFileCollection getFiles()
    abstract ConfigurableFileCollection getEnvFiles()
    abstract Property<String> getProjectName()
    abstract Property<WaitSpec> getWaitForRunning()
    abstract Property<WaitSpec> getWaitForHealthy()
    abstract Property<LogsSpec> getLogs()
    
    void waitForRunning(@DelegatesTo(WaitSpec) Closure closure) {
        def waitSpec = objectFactory.newInstance(WaitSpec)
        closure.delegate = waitSpec
        closure.call()
        waitForRunning.set(waitSpec)
    }
    
    void waitForRunning(Action<WaitSpec> action) {
        def waitSpec = objectFactory.newInstance(WaitSpec)
        action.execute(waitSpec)
        waitForRunning.set(waitSpec)
    }
    
    void waitForHealthy(@DelegatesTo(WaitSpec) Closure closure) {
        def waitSpec = objectFactory.newInstance(WaitSpec)
        closure.delegate = waitSpec
        closure.call()
        waitForHealthy.set(waitSpec)
    }
    
    void waitForHealthy(Action<WaitSpec> action) {
        def waitSpec = objectFactory.newInstance(WaitSpec)
        action.execute(waitSpec)
        waitForHealthy.set(waitSpec)
    }
    
    void logs(@DelegatesTo(LogsSpec) Closure closure) {
        def logsSpec = objectFactory.newInstance(LogsSpec)
        closure.delegate = logsSpec
        closure.call()
        logs.set(logsSpec)
    }
    
    void logs(Action<LogsSpec> action) {
        def logsSpec = objectFactory.newInstance(LogsSpec)
        action.execute(logsSpec)
        logs.set(logsSpec)
    }
    
    void composeFiles(String... files) {
        if (files != null) {
            composeFiles.set(Arrays.asList(files))
        }
    }

    void composeFiles(List<String> files) {
        if (files != null) {
            composeFiles.set(files)
        }
    }

    void composeFiles(File... files) {
        if (files != null) {
            composeFileCollection.from(files)
        }
    }
}