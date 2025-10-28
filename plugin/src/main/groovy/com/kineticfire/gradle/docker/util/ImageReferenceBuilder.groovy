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

package com.kineticfire.gradle.docker.util

/**
 * Pure utility for building Docker image references from nomenclature components.
 * All methods are static and have no side effects, making them 100% unit testable.
 *
 * This class is stateless and Gradle 9/10 configuration-cache compatible.
 */
class ImageReferenceBuilder {

    /**
     * Build full image references from nomenclature components
     *
     * @param registry Optional registry (e.g., "docker.io", "localhost:5000")
     * @param namespace Optional namespace (e.g., "library", "mycompany")
     * @param repository Repository name when using repository-style nomenclature
     * @param imageName Image name when using namespace+imageName style
     * @param tags List of tags to apply (e.g., ["1.0.0", "latest"])
     * @return List of full image references (e.g., ["docker.io/library/nginx:1.0.0"])
     */
    static List<String> buildImageReferences(
        String registry,
        String namespace,
        String repository,
        String imageName,
        List<String> tags
    ) {
        def references = []

        if (!repository.isEmpty()) {
            // Using repository format
            def baseRef = buildRepositoryStyleReference(registry, repository)
            tags.each { tag ->
                references.add("${baseRef}:${tag}")
            }
        } else if (!imageName.isEmpty()) {
            // Using namespace + imageName format
            def baseRef = buildNamespaceStyleReference(registry, namespace, imageName)
            tags.each { tag ->
                references.add("${baseRef}:${tag}")
            }
        }

        return references
    }

    /**
     * Build base reference using repository-style nomenclature
     *
     * @param registry Optional registry prefix
     * @param repository Repository name
     * @return Base reference without tag
     */
    static String buildRepositoryStyleReference(String registry, String repository) {
        return registry.isEmpty() ? repository : "${registry}/${repository}"
    }

    /**
     * Build base reference using namespace+imageName style nomenclature
     *
     * @param registry Optional registry prefix
     * @param namespace Optional namespace
     * @param imageName Image name (required)
     * @return Base reference without tag
     */
    static String buildNamespaceStyleReference(String registry, String namespace, String imageName) {
        def parts = []
        if (!registry.isEmpty()) {
            parts.add(registry)
        }
        if (!namespace.isEmpty()) {
            parts.add(namespace)
        }
        parts.add(imageName)
        return parts.join('/')
    }
}
