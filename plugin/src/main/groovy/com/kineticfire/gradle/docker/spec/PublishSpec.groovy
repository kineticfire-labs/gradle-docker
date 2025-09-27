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
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested

import javax.inject.Inject

/**
 * Specification for Docker image publish configuration
 */
abstract class PublishSpec {
    
    private final NamedDomainObjectContainer<PublishTarget> targets
    private final ObjectFactory objectFactory
    
    @Inject
    PublishSpec(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory
        def factory = this.objectFactory
        this.targets = objectFactory.domainObjectContainer(PublishTarget) { name ->
            factory.newInstance(PublishTarget, name, factory)
        }
        
        // Set default values
        publishTags.convention([])
    }
    
    @Input
    abstract ListProperty<String> getPublishTags()
    
    @Nested
    NamedDomainObjectContainer<PublishTarget> getTo() {
        return targets
    }
    
    void to(@DelegatesTo(PublishTarget) Closure closure) {
        def target = targets.create("target${targets.size()}")
        closure.delegate = target
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }
    
    void to(String name, @DelegatesTo(PublishTarget) Closure closure) {
        def target = targets.create(name)
        closure.delegate = target
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }
    
    void to(Action<PublishTarget> action) {
        def target = targets.create("target${targets.size()}")
        action.execute(target)
    }
    
    void to(String name, Action<PublishTarget> action) {
        def target = targets.create(name)
        action.execute(target)
    }
}