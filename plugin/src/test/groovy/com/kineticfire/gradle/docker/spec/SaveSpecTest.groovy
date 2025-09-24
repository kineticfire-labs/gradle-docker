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
 * Unit tests for SaveSpec
 */
class SaveSpecTest extends Specification {

    def project
    def saveSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        saveSpec = project.objects.newInstance(SaveSpec, project.objects)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        saveSpec != null
        saveSpec.compression.present  // Has default compression
        saveSpec.compression.get() == SaveCompression.NONE
        saveSpec.pullIfMissing.get() == false  // pullIfMissing still has default
    }

    // ===== PROPERTY TESTS =====

    def "compression property works correctly"() {
        when:
        saveSpec.compression.set(SaveCompression.BZIP2)

        then:
        saveSpec.compression.present
        saveSpec.compression.get() == SaveCompression.BZIP2
    }

    def "compression has default value"() {
        expect:
        saveSpec.compression.present
        saveSpec.compression.get() == SaveCompression.NONE
    }

    def "outputFile property works correctly"() {
        given:
        def outputFile = project.file('myimage.tar')

        when:
        saveSpec.outputFile.set(outputFile)

        then:
        saveSpec.outputFile.present
        saveSpec.outputFile.get().asFile == outputFile
    }

    def "pullIfMissing property works correctly"() {
        when:
        saveSpec.pullIfMissing.set(true)

        then:
        saveSpec.pullIfMissing.present
        saveSpec.pullIfMissing.get() == true
    }

    def "pullIfMissing has default value"() {
        expect:
        saveSpec.pullIfMissing.present
        saveSpec.pullIfMissing.get() == false
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def outputFile = project.file('complete-image.tar.gz')

        when:
        saveSpec.compression.set(SaveCompression.GZIP)
        saveSpec.outputFile.set(outputFile)
        saveSpec.pullIfMissing.set(true)

        then:
        saveSpec.compression.get() == SaveCompression.GZIP
        saveSpec.outputFile.get().asFile == outputFile
        saveSpec.pullIfMissing.get() == true
    }

    def "configuration with different compression types"() {
        expect:
        // Test different compression types
        [SaveCompression.NONE, SaveCompression.GZIP, SaveCompression.BZIP2, SaveCompression.XZ, SaveCompression.ZIP].each { compressionType ->
            saveSpec.compression.set(compressionType)
            assert saveSpec.compression.get() == compressionType
        }
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        given:
        def initialFile = project.file('initial.tar')
        def updatedFile = project.file('updated.tar.gz')

        when:
        saveSpec.outputFile.set(initialFile)
        saveSpec.compression.set(SaveCompression.NONE)
        saveSpec.pullIfMissing.set(false)

        then:
        saveSpec.outputFile.get().asFile == initialFile
        saveSpec.compression.get() == SaveCompression.NONE
        saveSpec.pullIfMissing.get() == false

        when:
        saveSpec.outputFile.set(updatedFile)
        saveSpec.compression.set(SaveCompression.GZIP)
        saveSpec.pullIfMissing.set(true)

        then:
        saveSpec.outputFile.get().asFile == updatedFile
        saveSpec.compression.get() == SaveCompression.GZIP
        saveSpec.pullIfMissing.get() == true
    }

    // ===== EDGE CASES =====

    def "outputFile has default value"() {
        expect:
        saveSpec.outputFile.present
        saveSpec.outputFile.get().asFile.name == "image.tar"
    }

    def "various file types can be set"() {
        given:
        def tarFile = project.file('image.tar')
        def gzipFile = project.file('image.tar.gz')
        def bz2File = project.file('image.tar.bz2')

        expect:
        [tarFile, gzipFile, bz2File].each { file ->
            saveSpec.outputFile.set(file)
            assert saveSpec.outputFile.get().asFile == file
        }
    }

    def "compression property accepts various valid values"() {
        expect:
        [SaveCompression.NONE, SaveCompression.GZIP, SaveCompression.BZIP2, SaveCompression.XZ, SaveCompression.ZIP].each { compression ->
            saveSpec.compression.set(compression)
            assert saveSpec.compression.get() == compression
        }
    }

    // ===== NEW VALIDATION TESTS =====

    def "compression property can be explicitly set to each valid type"() {
        expect:
        // Test all valid compression types
        [SaveCompression.NONE, SaveCompression.GZIP, SaveCompression.BZIP2, SaveCompression.XZ, SaveCompression.ZIP].each { compressionType ->
            saveSpec.compression.set(compressionType)
            assert saveSpec.compression.present
            assert saveSpec.compression.get() == compressionType
        }
    }

    def "compression property accepts all enum values"() {
        when:
        saveSpec.compression.set(SaveCompression.GZIP)

        then:
        saveSpec.compression.present
        saveSpec.compression.get() == SaveCompression.GZIP
    }

    def "compression property can be set to any enum value"() {
        when:
        saveSpec.compression.set(SaveCompression.XZ)

        then:
        saveSpec.compression.present
        saveSpec.compression.get() == SaveCompression.XZ
    }

    def "compression property can be overridden"() {
        given:
        saveSpec.compression.set(SaveCompression.GZIP)

        when:
        saveSpec.compression.set(SaveCompression.ZIP)

        then:
        saveSpec.compression.present
        saveSpec.compression.get() == SaveCompression.ZIP
    }

    // SAVESPEC AUTHENTICATION INTEGRATION TESTS

    def "SaveSpec auth property can be configured via DSL"() {
        given: "SaveSpec with authentication configuration"
        saveSpec.auth {
            username.set("testuser")
            password.set("testpass")
            registryToken.set("token123")
        }

        when: "auth property is accessed"
        def authSpec = saveSpec.auth.get()

        then: "auth properties are correctly configured"
        saveSpec.auth.isPresent()
        authSpec.username.get() == "testuser"
        authSpec.password.get() == "testpass"
        authSpec.registryToken.get() == "token123"
    }

    def "SaveSpec auth property has correct defaults"() {
        when: "SaveSpec is created without auth configuration"
        // No auth configuration

        then: "auth property is not present by default"
        !saveSpec.auth.isPresent()
    }

    def "SaveSpec auth property validates required fields for authentication"() {
        given: "SaveSpec with partial auth configuration"
        saveSpec.auth {
            username.set("testuser")
            // Missing password and token
        }

        when: "auth property is accessed"
        def authSpec = saveSpec.auth.get()

        then: "username is set but others are empty"
        authSpec.username.get() == "testuser"
        !authSpec.password.isPresent()
        !authSpec.registryToken.isPresent()
    }

    def "SaveSpec auth converts to AuthConfig correctly"() {
        given: "SaveSpec with complete auth configuration"
        saveSpec.auth {
            username.set("testuser")
            password.set("testpass")
            registryToken.set("token123")
            // serverAddress removed - extracted automatically from image reference
        }

        when: "auth is converted to AuthConfig"
        def authSpec = saveSpec.auth.get()
        def authConfig = authSpec.toAuthConfig()

        then: "AuthConfig has correct values"
        authConfig.username == "testuser"
        authConfig.password == "testpass"
        authConfig.registryToken == "token123"
        // serverAddress removed - extracted automatically from image reference
    }

    def "SaveSpec pullIfMissing property has correct default"() {
        when: "SaveSpec is created without pullIfMissing configuration"
        // No pullIfMissing configuration

        then: "pullIfMissing defaults to false"
        saveSpec.pullIfMissing.get() == false
    }

    def "SaveSpec pullIfMissing property can be configured"() {
        when: "pullIfMissing is explicitly set"
        saveSpec.pullIfMissing.set(true)

        then: "pullIfMissing value is preserved"
        saveSpec.pullIfMissing.get() == true
    }

    def "SaveSpec supports complex authentication scenarios"() {
        given: "SaveSpec with authentication and pullIfMissing"
        saveSpec.compression.set(SaveCompression.GZIP)
        saveSpec.outputFile.set(project.layout.buildDirectory.file("authenticated-image.tar.gz"))
        saveSpec.pullIfMissing.set(true)
        saveSpec.auth {
            username.set("registry-user")
            password.set("secret-password")
            helper.set("docker-credential-helper")
        }

        when: "all properties are accessed"
        def authSpec = saveSpec.auth.get()

        then: "all properties are correctly configured"
        saveSpec.compression.get() == SaveCompression.GZIP
        saveSpec.pullIfMissing.get() == true
        authSpec.username.get() == "registry-user"
        authSpec.password.get() == "secret-password"
        authSpec.helper.get() == "docker-credential-helper"
    }
}