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
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * Specification for a Docker registry publish target
 */
abstract class PublishTarget {
    
    private final String name
    private final ObjectFactory objectFactory
    
    @Inject
    PublishTarget(String name, ObjectFactory objectFactory) {
        this.name = name
        this.objectFactory = objectFactory
    }
    
    String getName() { 
        return name 
    }
    
    // Changed: removed repository property, renamed publishTags to tags for full image references
    abstract ListProperty<String> getTags()
    abstract Property<AuthSpec> getAuth()
    
    void tags(List<String> tags) {
        getTags().set(tags)
    }
    
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