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
import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Comprehensive unit tests for SaveSpec
 */
class SaveSpecComprehensiveTest extends Specification {

    def project
    def saveSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
    }

    // ===== CONSTRUCTOR AND CONVENTIONS =====

    def "constructor sets proper convention values"() {
        expect:
        saveSpec.compression.get() == SaveCompression.NONE
        saveSpec.pullIfMissing.get() == false
        saveSpec.outputFile.get().asFile.path.endsWith("docker-images/image.tar")
    }

    // ===== COMPRESSION TESTS =====

    def "compression property works with all enum values"() {
        expect:
        saveSpec.compression.set(compression)
        saveSpec.compression.get() == compression

        where:
        compression << SaveCompression.values()
    }

    def "compression property works with Provider API"() {
        given:
        def compressionProvider = project.provider { SaveCompression.GZIP }

        when:
        saveSpec.compression.set(compressionProvider)

        then:
        saveSpec.compression.get() == SaveCompression.GZIP
    }

    // ===== OUTPUT FILE TESTS =====

    def "outputFile property works with various file types"() {
        given:
        def outputFile = project.file(fileName)

        when:
        saveSpec.outputFile.set(outputFile)

        then:
        saveSpec.outputFile.get().asFile.name == expectedName

        where:
        fileName                | expectedName
        "image.tar"            | "image.tar"
        "image.tar.gz"         | "image.tar.gz"
        "image.tar.bz2"        | "image.tar.bz2"
        "image.tar.xz"         | "image.tar.xz"
        "image.zip"            | "image.zip"
        "custom-name.archive"  | "custom-name.archive"
        "path/to/image.tar"    | "image.tar"
    }

    def "outputFile property works with Provider API"() {
        given:
        def fileProvider = project.layout.file(project.provider { project.file("dynamic-image.tar.gz") })

        when:
        saveSpec.outputFile.set(fileProvider)

        then:
        saveSpec.outputFile.get().asFile.name == "dynamic-image.tar.gz"
    }

    def "outputFile property works with layout provider"() {
        when:
        saveSpec.outputFile.set(project.layout.buildDirectory.file("docker/custom-image.tar"))

        then:
        saveSpec.outputFile.get().asFile.path.contains("build")
        saveSpec.outputFile.get().asFile.name == "custom-image.tar"
    }

    // ===== PULL IF MISSING TESTS =====

    def "pullIfMissing property works with boolean values"() {
        expect:
        saveSpec.pullIfMissing.set(value)
        saveSpec.pullIfMissing.get() == value

        where:
        value << [true, false]
    }

    def "pullIfMissing property works with Provider API"() {
        given:
        def pullProvider = project.provider { true }

        when:
        saveSpec.pullIfMissing.set(pullProvider)

        then:
        saveSpec.pullIfMissing.get() == true
    }

    // ===== AUTHENTICATION TESTS =====

    def "auth configuration with username and password"() {
        when:
        saveSpec.auth {
            username.set("saveUser")
            password.set("savePass")
            serverAddress.set("save.registry.io")
        }

        then:
        saveSpec.auth.isPresent()
        with(saveSpec.auth.get()) {
            username.get() == "saveUser"
            password.get() == "savePass"
            serverAddress.get() == "save.registry.io"
            !registryToken.isPresent()
            !helper.isPresent()
        }
    }

    def "auth configuration with registry token"() {
        when:
        saveSpec.auth {
            registryToken.set("save_token_xyz")
            serverAddress.set("token.registry.io")
        }

        then:
        saveSpec.auth.isPresent()
        with(saveSpec.auth.get()) {
            registryToken.get() == "save_token_xyz"
            serverAddress.get() == "token.registry.io"
            !username.isPresent()
            !password.isPresent()
            !helper.isPresent()
        }
    }

    def "auth configuration with helper"() {
        when:
        saveSpec.auth {
            helper.set("save-credential-helper")
            serverAddress.set("helper.registry.io")
        }

        then:
        saveSpec.auth.isPresent()
        with(saveSpec.auth.get()) {
            helper.get() == "save-credential-helper"
            serverAddress.get() == "helper.registry.io"
            !username.isPresent()
            !password.isPresent()
            !registryToken.isPresent()
        }
    }

    def "auth Action configuration"() {
        when:
        saveSpec.auth(new Action<AuthSpec>() {
            @Override
            void execute(AuthSpec authSpec) {
                authSpec.username.set("actionUser")
                authSpec.password.set("actionPass")
            }
        })

        then:
        saveSpec.auth.isPresent()
        saveSpec.auth.get().username.get() == "actionUser"
        saveSpec.auth.get().password.get() == "actionPass"
    }

    def "auth configuration with Provider API"() {
        given:
        def usernameProvider = project.provider { "dynamicUser" }
        def passwordProvider = project.provider { "dynamicPass" }
        def serverProvider = project.provider { "dynamic.registry.io" }

        when:
        saveSpec.auth {
            username.set(usernameProvider)
            password.set(passwordProvider)
            serverAddress.set(serverProvider)
        }

        then:
        saveSpec.auth.isPresent()
        with(saveSpec.auth.get()) {
            username.get() == "dynamicUser"
            password.get() == "dynamicPass"
            serverAddress.get() == "dynamic.registry.io"
        }
    }

    // ===== COMPLEX SCENARIOS =====

    def "complete save configuration with all options"() {
        given:
        def outputDir = project.file("build/docker-saves")
        outputDir.mkdirs()

        when:
        saveSpec.compression.set(SaveCompression.XZ)
        saveSpec.outputFile.set(new File(outputDir, "complete-image.tar.xz"))
        saveSpec.pullIfMissing.set(true)
        saveSpec.auth {
            username.set("completeUser")
            password.set("completePass")
            serverAddress.set("complete.registry.io")
        }

        then:
        with(saveSpec) {
            compression.get() == SaveCompression.XZ
            outputFile.get().asFile.name == "complete-image.tar.xz"
            outputFile.get().asFile.parentFile.name == "docker-saves"
            pullIfMissing.get() == true
            auth.isPresent()
            auth.get().username.get() == "completeUser"
            auth.get().password.get() == "completePass"
            auth.get().serverAddress.get() == "complete.registry.io"
        }
    }

    def "save configuration for sourceRef scenario"() {
        when:
        saveSpec.compression.set(SaveCompression.BZIP2)
        saveSpec.outputFile.set(project.layout.buildDirectory.file("sourceref/pulled-image.tar.bz2"))
        saveSpec.pullIfMissing.set(true)
        saveSpec.auth {
            registryToken.set("sourceref_token_abc")
            serverAddress.set("private.sourceref.registry")
        }

        then:
        with(saveSpec) {
            compression.get() == SaveCompression.BZIP2
            outputFile.get().asFile.name == "pulled-image.tar.bz2"
            pullIfMissing.get() == true
            auth.isPresent()
            auth.get().registryToken.get() == "sourceref_token_abc"
            auth.get().serverAddress.get() == "private.sourceref.registry"
        }
    }

    def "save configuration for build mode scenario"() {
        when:
        saveSpec.compression.set(SaveCompression.ZIP)
        saveSpec.outputFile.set(project.file("exports/built-image.zip"))
        saveSpec.pullIfMissing.set(false)  // Not needed for built images

        then:
        with(saveSpec) {
            compression.get() == SaveCompression.ZIP
            outputFile.get().asFile.name == "built-image.zip"
            pullIfMissing.get() == false
            !auth.isPresent()  // No auth needed for local images
        }
    }

    // ===== PROPERTY OVERRIDING TESTS =====

    def "properties can be overridden"() {
        when:
        saveSpec.compression.set(SaveCompression.GZIP)
        saveSpec.compression.set(SaveCompression.XZ)
        
        saveSpec.pullIfMissing.set(true)
        saveSpec.pullIfMissing.set(false)
        
        saveSpec.outputFile.set(project.file("first.tar"))
        saveSpec.outputFile.set(project.file("second.tar"))

        then:
        saveSpec.compression.get() == SaveCompression.XZ
        saveSpec.pullIfMissing.get() == false
        saveSpec.outputFile.get().asFile.name == "second.tar"
    }

    def "auth configuration can be overridden"() {
        when:
        saveSpec.auth {
            username.set("firstUser")
            password.set("firstPass")
        }
        saveSpec.auth {
            registryToken.set("newToken")
            serverAddress.set("new.registry.io")
        }

        then:
        saveSpec.auth.isPresent()
        with(saveSpec.auth.get()) {
            registryToken.get() == "newToken"
            serverAddress.get() == "new.registry.io"
            !username.isPresent()  // Overridden auth spec
            !password.isPresent()
        }
    }

    // ===== EDGE CASES =====

    def "empty auth configuration"() {
        when:
        saveSpec.auth {
            // No properties set
        }

        then:
        saveSpec.auth.isPresent()
        with(saveSpec.auth.get()) {
            !username.isPresent()
            !password.isPresent()
            !registryToken.isPresent()
            !serverAddress.isPresent()
            !helper.isPresent()
        }
    }

    def "outputFile with nested directories"() {
        given:
        def nestedFile = project.file("very/deep/nested/path/to/image.tar.gz")

        when:
        saveSpec.outputFile.set(nestedFile)

        then:
        saveSpec.outputFile.get().asFile.path.contains("very/deep/nested/path/to")
        saveSpec.outputFile.get().asFile.name == "image.tar.gz"
    }

    def "compression with different file extensions match"() {
        expect:
        saveSpec.compression.set(compression)
        saveSpec.outputFile.set(project.file(fileName))
        
        saveSpec.compression.get() == compression
        saveSpec.outputFile.get().asFile.name == fileName

        where:
        compression              | fileName
        SaveCompression.NONE     | "image.tar"
        SaveCompression.GZIP     | "image.tar.gz"
        SaveCompression.BZIP2    | "image.tar.bz2"
        SaveCompression.XZ       | "image.tar.xz"
        SaveCompression.ZIP      | "image.zip"
    }
}