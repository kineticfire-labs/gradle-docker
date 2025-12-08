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

package com.kineticfire.gradle.docker.spec.project

import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject

/**
 * Simplified success operations configuration for dockerProject DSL.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses @Inject for service injection.
 * All properties use Provider API with proper @Input/@Optional annotations.
 *
 * NOTE: This class uses SaveCompression enum from com.kineticfire.gradle.docker.model.
 * This matches the enum used in SaveSpec for consistency.
 */
abstract class ProjectSuccessSpec {

    private final ObjectFactory objectFactory
    private final ProjectLayout layout

    @Inject
    ProjectSuccessSpec(ObjectFactory objectFactory, ProjectLayout layout) {
        this.objectFactory = objectFactory
        this.layout = layout

        additionalTags.convention([])
        saveCompression.convention('')  // Empty means use SaveSpec default (NONE)
        publishTags.convention([])
    }

    /**
     * Additional tags to apply when tests pass.
     * These are ADDED to the base tags, not replacing them.
     */
    @Input
    abstract ListProperty<String> getAdditionalTags()

    /**
     * Path to save the image as a tar file.
     * Compression is inferred from the file extension:
     * - .tar.gz, .tgz -> GZIP
     * - .tar.bz2, .tbz2 -> BZIP2
     * - .tar.xz, .txz -> XZ
     * - .tar -> NONE
     * - .zip -> ZIP
     */
    @Input
    @Optional
    abstract Property<String> getSaveFile()

    /**
     * Explicit compression override. Only needed if filename doesn't have
     * a recognized extension. Values: 'none', 'gzip', 'bzip2', 'xz', 'zip'
     */
    @Input
    @Optional
    abstract Property<String> getSaveCompression()

    // === PUBLISH PROPERTIES ===

    /**
     * Registry to publish to (e.g., 'registry.example.com')
     */
    @Input
    @Optional
    abstract Property<String> getPublishRegistry()

    /**
     * Namespace to publish to (e.g., 'myproject')
     */
    @Input
    @Optional
    abstract Property<String> getPublishNamespace()

    /**
     * Tags to publish. If not specified, uses additionalTags.
     */
    @Input
    abstract ListProperty<String> getPublishTags()

    // === DSL METHODS ===

    /**
     * Shorthand for setting saveFile
     */
    void save(String filePath) {
        saveFile.set(filePath)
    }

    /**
     * Extended save with explicit compression
     */
    void save(Map<String, String> args) {
        if (args.file) {
            saveFile.set(args.file)
        }
        if (args.compression) {
            saveCompression.set(args.compression)
        }
    }

    /**
     * Infer SaveCompression from filename extension.
     * Uses SaveCompression enum from com.kineticfire.gradle.docker.model package.
     *
     * Logic:
     * 1. If saveCompression is explicitly set (non-empty), use it
     * 2. Otherwise, infer compression from the filename extension
     * 3. The convention is empty string (''), meaning "infer from filename"
     *
     * NOTE: The underlying SaveSpec uses SaveCompression.NONE as its default.
     * This spec uses filename inference as the primary mechanism, which is more user-friendly.
     */
    SaveCompression inferCompression() {
        if (!saveFile.isPresent()) {
            return null
        }

        def filename = saveFile.get()

        // Check explicit override first - if user explicitly set compression, use it.
        // Empty string ('') is the convention, meaning "infer from filename".
        if (saveCompression.isPresent() && !saveCompression.get().isEmpty()) {
            return parseCompression(saveCompression.get())
        }

        // Infer from filename (this is the common case)
        if (filename.endsWith('.tar.gz') || filename.endsWith('.tgz')) {
            return SaveCompression.GZIP
        } else if (filename.endsWith('.tar.bz2') || filename.endsWith('.tbz2')) {
            return SaveCompression.BZIP2
        } else if (filename.endsWith('.tar.xz') || filename.endsWith('.txz')) {
            return SaveCompression.XZ
        } else if (filename.endsWith('.zip')) {
            return SaveCompression.ZIP
        } else if (filename.endsWith('.tar')) {
            return SaveCompression.NONE
        } else {
            throw new GradleException(
                "Cannot infer compression from filename '${filename}'. " +
                "Use one of: .tar.gz, .tgz, .tar.bz2, .tbz2, .tar.xz, .txz, .tar, .zip " +
                "or specify compression explicitly: save file: '...', compression: 'gzip'"
            )
        }
    }

    private SaveCompression parseCompression(String compression) {
        switch (compression.toLowerCase()) {
            case 'none': return SaveCompression.NONE
            case 'gzip': return SaveCompression.GZIP
            case 'bzip2': return SaveCompression.BZIP2
            case 'xz': return SaveCompression.XZ
            case 'zip': return SaveCompression.ZIP
            default:
                throw new GradleException(
                    "Unknown compression type '${compression}'. " +
                    "Valid values: none, gzip, bzip2, xz, zip"
                )
        }
    }
}
