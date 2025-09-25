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

import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Comprehensive unit tests for SaveSpec
 * NOTE: pullIfMissing and auth functionality moved to ImageSpec level
 */
class SaveSpecComprehensiveTest extends Specification {

    def project
    def saveSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
    }

    def "constructor sets proper convention values"() {
        expect:
        saveSpec != null
        saveSpec.compression.present
        saveSpec.compression.get() == SaveCompression.NONE
        saveSpec.outputFile.present
        saveSpec.outputFile.get().asFile.name == "image.tar"
    }

    def "compression property works with all enum values"() {
        expect:
        [SaveCompression.NONE, SaveCompression.GZIP, SaveCompression.BZIP2, SaveCompression.XZ, SaveCompression.ZIP].each { compressionType ->
            saveSpec.compression.set(compressionType)
            assert saveSpec.compression.get() == compressionType
        }
    }

    def "outputFile property works correctly"() {
        given:
        def outputFile = project.file('custom-output.tar.gz')

        when:
        saveSpec.outputFile.set(outputFile)

        then:
        saveSpec.outputFile.get().asFile == outputFile
    }

    def "properties can be updated after initial configuration"() {
        given:
        def file1 = project.file('file1.tar')
        def file2 = project.file('file2.tar.gz')

        when:
        saveSpec.outputFile.set(file1)
        saveSpec.compression.set(SaveCompression.NONE)

        then:
        saveSpec.outputFile.get().asFile == file1
        saveSpec.compression.get() == SaveCompression.NONE

        when:
        saveSpec.outputFile.set(file2)
        saveSpec.compression.set(SaveCompression.GZIP)

        then:
        saveSpec.outputFile.get().asFile == file2
        saveSpec.compression.get() == SaveCompression.GZIP
    }
}