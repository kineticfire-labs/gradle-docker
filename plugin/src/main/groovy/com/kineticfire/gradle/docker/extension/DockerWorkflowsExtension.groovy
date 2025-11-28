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

import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

/**
 * Extension for Docker workflow orchestration (dockerWorkflows { } DSL)
 *
 * This extension provides a declarative DSL for defining complete CI/CD pipelines
 * that orchestrate build, test, and conditional publish operations.
 */
abstract class DockerWorkflowsExtension {

    private final NamedDomainObjectContainer<PipelineSpec> pipelines
    private final ObjectFactory objectFactory

    @Inject
    DockerWorkflowsExtension(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory
        this.pipelines = objectFactory.domainObjectContainer(PipelineSpec) { name ->
            def pipelineSpec = objectFactory.newInstance(PipelineSpec, name)
            return pipelineSpec
        }
    }

    NamedDomainObjectContainer<PipelineSpec> getPipelines() {
        return pipelines
    }

    void pipelines(@DelegatesTo(NamedDomainObjectContainer) Closure closure) {
        pipelines.configure(closure)
    }

    void pipelines(Action<NamedDomainObjectContainer<PipelineSpec>> action) {
        action.execute(pipelines)
    }
}
