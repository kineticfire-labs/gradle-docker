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

package com.kineticfire.gradle.docker.workflow

import com.kineticfire.gradle.docker.spec.ImageSpec

/**
 * Context object passed between pipeline step executors
 *
 * Maintains state across workflow execution including built image info and test results.
 * Immutable with copy-on-modify pattern for thread safety and predictable state.
 */
class PipelineContext implements Serializable {

    private static final long serialVersionUID = 1L

    private final String pipelineName
    private final ImageSpec builtImage
    private final TestResult testResult
    private final Map<String, Object> metadata
    private final List<String> appliedTags
    private final boolean buildCompleted
    private final boolean testCompleted

    private PipelineContext(Builder builder) {
        this.pipelineName = builder.pipelineName
        this.builtImage = builder.builtImage
        this.testResult = builder.testResult
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata))
        this.appliedTags = Collections.unmodifiableList(new ArrayList<>(builder.appliedTags))
        this.buildCompleted = builder.buildCompleted
        this.testCompleted = builder.testCompleted
    }

    /**
     * Create a new context for starting a pipeline
     */
    static PipelineContext create(String pipelineName) {
        return new Builder(pipelineName).build()
    }

    String getPipelineName() {
        return pipelineName
    }

    ImageSpec getBuiltImage() {
        return builtImage
    }

    TestResult getTestResult() {
        return testResult
    }

    Map<String, Object> getMetadata() {
        return metadata
    }

    List<String> getAppliedTags() {
        return appliedTags
    }

    boolean isBuildCompleted() {
        return buildCompleted
    }

    boolean isTestCompleted() {
        return testCompleted
    }

    /**
     * Get metadata value by key
     */
    Object getMetadataValue(String key) {
        return metadata.get(key)
    }

    /**
     * Check if build was successful (completed and image is available)
     */
    boolean isBuildSuccessful() {
        return buildCompleted && builtImage != null
    }

    /**
     * Check if tests passed
     */
    boolean isTestSuccessful() {
        return testCompleted && testResult != null && testResult.isSuccess()
    }

    /**
     * Create a new context with the built image set
     */
    PipelineContext withBuiltImage(ImageSpec image) {
        return toBuilder()
            .builtImage(image)
            .buildCompleted(true)
            .build()
    }

    /**
     * Create a new context with test result set
     */
    PipelineContext withTestResult(TestResult result) {
        return toBuilder()
            .testResult(result)
            .testCompleted(true)
            .build()
    }

    /**
     * Create a new context with additional metadata
     */
    PipelineContext withMetadata(String key, Object value) {
        def newMetadata = new LinkedHashMap<>(this.metadata)
        newMetadata.put(key, value)
        return toBuilder()
            .metadata(newMetadata)
            .build()
    }

    /**
     * Create a new context with an applied tag added
     */
    PipelineContext withAppliedTag(String tag) {
        def newTags = new ArrayList<>(this.appliedTags)
        newTags.add(tag)
        return toBuilder()
            .appliedTags(newTags)
            .build()
    }

    /**
     * Create a new context with multiple applied tags added
     */
    PipelineContext withAppliedTags(List<String> tags) {
        def newTags = new ArrayList<>(this.appliedTags)
        newTags.addAll(tags)
        return toBuilder()
            .appliedTags(newTags)
            .build()
    }

    /**
     * Create a builder from this context for modification
     */
    Builder toBuilder() {
        return new Builder(this)
    }

    @Override
    String toString() {
        return "PipelineContext[pipeline=${pipelineName}, buildCompleted=${buildCompleted}, " +
               "testCompleted=${testCompleted}, appliedTags=${appliedTags.size()}]"
    }

    /**
     * Builder for creating PipelineContext instances
     */
    static class Builder {
        private String pipelineName
        private ImageSpec builtImage
        private TestResult testResult
        private Map<String, Object> metadata = new LinkedHashMap<>()
        private List<String> appliedTags = new ArrayList<>()
        private boolean buildCompleted = false
        private boolean testCompleted = false

        Builder(String pipelineName) {
            this.pipelineName = pipelineName
        }

        Builder(PipelineContext context) {
            this.pipelineName = context.pipelineName
            this.builtImage = context.builtImage
            this.testResult = context.testResult
            this.metadata = new LinkedHashMap<>(context.metadata)
            this.appliedTags = new ArrayList<>(context.appliedTags)
            this.buildCompleted = context.buildCompleted
            this.testCompleted = context.testCompleted
        }

        Builder builtImage(ImageSpec image) {
            this.builtImage = image
            return this
        }

        Builder testResult(TestResult result) {
            this.testResult = result
            return this
        }

        Builder metadata(Map<String, Object> metadata) {
            this.metadata = new LinkedHashMap<>(metadata)
            return this
        }

        Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value)
            return this
        }

        Builder appliedTags(List<String> tags) {
            this.appliedTags = new ArrayList<>(tags)
            return this
        }

        Builder addAppliedTag(String tag) {
            this.appliedTags.add(tag)
            return this
        }

        Builder buildCompleted(boolean completed) {
            this.buildCompleted = completed
            return this
        }

        Builder testCompleted(boolean completed) {
            this.testCompleted = completed
            return this
        }

        PipelineContext build() {
            return new PipelineContext(this)
        }
    }
}
