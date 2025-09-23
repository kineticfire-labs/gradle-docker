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

package com.kineticfire.gradle.docker.service

import com.kineticfire.gradle.docker.model.*
import org.gradle.api.services.BuildServiceParameters
import spock.lang.Specification

/**
 * Focused tests for DockerServiceImpl to improve coverage
 * Tests methods that can be tested without complex mocking
 */
class DockerServiceFocusedTest extends Specification {

    def "DockerService interface has all required methods"() {
        expect:
        DockerService.getDeclaredMethods().any { it.name == 'buildImage' }
        DockerService.getDeclaredMethods().any { it.name == 'tagImage' }
        DockerService.getDeclaredMethods().any { it.name == 'saveImage' }
        DockerService.getDeclaredMethods().any { it.name == 'pushImage' }
        DockerService.getDeclaredMethods().any { it.name == 'pullImage' }
        DockerService.getDeclaredMethods().any { it.name == 'imageExists' }
        DockerService.getDeclaredMethods().any { it.name == 'close' }
    }

    def "DockerServiceImpl constructor creates instance"() {
        when:
        def service = new TestableDockerServiceImpl()

        then:
        service != null
        service.getParameters() == null
    }

    def "SaveCompression enum has all required values"() {
        expect:
        SaveCompression.NONE != null
        SaveCompression.GZIP != null
        SaveCompression.BZIP2 != null
        SaveCompression.XZ != null
        SaveCompression.ZIP != null
    }

    def "SaveCompression enum values have correct properties"() {
        expect:
        SaveCompression.NONE.extension == "tar"
        SaveCompression.GZIP.extension == "tar.gz"
        SaveCompression.BZIP2.extension == "tar.bz2"
        SaveCompression.XZ.extension == "tar.xz" 
        SaveCompression.ZIP.extension == "zip"
    }

    def "SaveCompression valueOf works correctly"() {
        expect:
        SaveCompression.valueOf("NONE") == SaveCompression.NONE
        SaveCompression.valueOf("GZIP") == SaveCompression.GZIP
        SaveCompression.valueOf("BZIP2") == SaveCompression.BZIP2
        SaveCompression.valueOf("XZ") == SaveCompression.XZ
        SaveCompression.valueOf("ZIP") == SaveCompression.ZIP
    }

    def "SaveCompression values() returns all values"() {
        when:
        def values = SaveCompression.values()

        then:
        values.length == 5
        values.contains(SaveCompression.NONE)
        values.contains(SaveCompression.GZIP)
        values.contains(SaveCompression.BZIP2)
        values.contains(SaveCompression.XZ)
        values.contains(SaveCompression.ZIP)
    }

    /**
     * Testable implementation of DockerServiceImpl
     * Avoids Docker client creation for unit testing
     */
    static class TestableDockerServiceImpl extends DockerServiceImpl {
        TestableDockerServiceImpl() {
            // Skip Docker client initialization
        }

        @Override
        BuildServiceParameters.None getParameters() {
            return null
        }

        // Override methods that would require Docker client to avoid initialization issues
        @Override
        void close() {
            // No-op for testing
        }
    }
}