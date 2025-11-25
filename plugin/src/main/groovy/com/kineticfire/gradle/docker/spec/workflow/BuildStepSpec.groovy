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

package com.kineticfire.gradle.docker.spec.workflow

import com.kineticfire.gradle.docker.spec.ImageSpec
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * Specification for the build step in a pipeline workflow
 *
 * Defines which Docker image to build and any build-time customizations.
 */
abstract class BuildStepSpec {

    private final ObjectFactory objectFactory

    @Inject
    BuildStepSpec(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory

        buildArgs.convention([:])
    }

    /**
     * Reference to the ImageSpec from docker.images that should be built
     */
    abstract Property<ImageSpec> getImage()

    /**
     * Build arguments to override or supplement those defined in ImageSpec
     */
    abstract MapProperty<String, String> getBuildArgs()

    /**
     * Hook executed before the build step runs
     */
    abstract Property<Action<Void>> getBeforeBuild()

    /**
     * Hook executed after the build step completes successfully
     */
    abstract Property<Action<Void>> getAfterBuild()
}
