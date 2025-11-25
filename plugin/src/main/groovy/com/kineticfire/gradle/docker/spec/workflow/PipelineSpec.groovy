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

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * Specification for a complete CI/CD pipeline workflow
 *
 * Defines the build → test → conditional publish pipeline with support for
 * success/failure paths and cleanup operations.
 */
abstract class PipelineSpec {

    private final String name
    private final ObjectFactory objectFactory

    @Inject
    PipelineSpec(String name, ObjectFactory objectFactory) {
        this.name = name
        this.objectFactory = objectFactory

        description.convention("")

        build.convention(objectFactory.newInstance(BuildStepSpec, objectFactory))
        test.convention(objectFactory.newInstance(TestStepSpec, objectFactory))
        onTestSuccess.convention(objectFactory.newInstance(SuccessStepSpec, objectFactory))
        onTestFailure.convention(objectFactory.newInstance(FailureStepSpec, objectFactory))
        always.convention(objectFactory.newInstance(AlwaysStepSpec, objectFactory))
    }

    String getName() {
        return name
    }

    abstract Property<String> getDescription()

    abstract Property<BuildStepSpec> getBuild()

    abstract Property<TestStepSpec> getTest()

    abstract Property<SuccessStepSpec> getOnTestSuccess()

    abstract Property<SuccessStepSpec> getOnSuccess()

    abstract Property<FailureStepSpec> getOnTestFailure()

    abstract Property<FailureStepSpec> getOnFailure()

    abstract Property<AlwaysStepSpec> getAlways()

    void build(Action<BuildStepSpec> action) {
        action.execute(build.get())
    }

    void test(Action<TestStepSpec> action) {
        action.execute(test.get())
    }

    void onTestSuccess(Action<SuccessStepSpec> action) {
        action.execute(onTestSuccess.get())
    }

    void onSuccess(Action<SuccessStepSpec> action) {
        action.execute(onSuccess.get())
    }

    void onTestFailure(Action<FailureStepSpec> action) {
        action.execute(onTestFailure.get())
    }

    void onFailure(Action<FailureStepSpec> action) {
        action.execute(onFailure.get())
    }

    void always(Action<AlwaysStepSpec> action) {
        action.execute(always.get())
    }
}
