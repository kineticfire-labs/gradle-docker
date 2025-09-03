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

package com.kineticfire.gradle.docker.model

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for LogsConfig
 */
class LogsConfigTest extends Specification {

    @TempDir
    Path tempDir

    def "can create minimal LogsConfig"() {
        when:
        def config = new LogsConfig([])

        then:
        config.services == []
        config.tailLines == 100 // default
        config.follow == false // default
        config.outputFile == null // default
    }

    def "can create LogsConfig with services only"() {
        given:
        def services = ["web", "api", "db"]

        when:
        def config = new LogsConfig(services)

        then:
        config.services == services
        config.tailLines == 100
        config.follow == false
        config.outputFile == null
    }

    def "can create LogsConfig with custom tail lines"() {
        when:
        def config = new LogsConfig(["web"], 50)

        then:
        config.services == ["web"]
        config.tailLines == 50
        config.follow == false
        config.outputFile == null
    }

    def "can create LogsConfig with follow enabled"() {
        when:
        def config = new LogsConfig(["web"], 200, true)

        then:
        config.services == ["web"]
        config.tailLines == 200
        config.follow == true
        config.outputFile == null
    }

    def "can create full LogsConfig with output file"() {
        given:
        def outputFile = tempDir.resolve("logs.txt")
        def services = ["nginx", "postgres"]

        when:
        def config = new LogsConfig(services, 500, true, outputFile)

        then:
        config.services == services
        config.tailLines == 500
        config.follow == true
        config.outputFile == outputFile
    }

    def "handles null services with empty list"() {
        when:
        def config = new LogsConfig(null)

        then:
        config.services == []
    }

    def "handles null services in full constructor"() {
        when:
        def config = new LogsConfig(null, 100, false, null)

        then:
        config.services == []
    }

    def "enforces minimum tail lines of 1"() {
        expect:
        new LogsConfig([], 0).tailLines == 1
        new LogsConfig([], -5).tailLines == 1
        new LogsConfig([], 1).tailLines == 1
        new LogsConfig([], 10).tailLines == 10
    }

    def "hasSpecificServices returns correct values"() {
        expect:
        !new LogsConfig([]).hasSpecificServices()
        !new LogsConfig(null).hasSpecificServices()
        new LogsConfig(["web"]).hasSpecificServices()
        new LogsConfig(["web", "db"]).hasSpecificServices()
    }

    def "hasOutputFile returns correct values"() {
        given:
        def outputFile = tempDir.resolve("test.log")

        expect:
        !new LogsConfig([]).hasOutputFile()
        !new LogsConfig([], 100, false, null).hasOutputFile()
        new LogsConfig([], 100, false, outputFile).hasOutputFile()
    }

    def "toString includes configuration summary"() {
        given:
        def outputFile = tempDir.resolve("app.log")
        def config = new LogsConfig(["web", "api"], 250, true, outputFile)

        when:
        def string = config.toString()

        then:
        string.contains("LogsConfig")
        string.contains("services=[web, api]")
        string.contains("tailLines=250")
        string.contains("follow=true")
        string.contains("outputFile=" + outputFile.toString())
    }

    def "toString handles null output file"() {
        when:
        def config = new LogsConfig(["service"], 100, false, null)
        def string = config.toString()

        then:
        string.contains("outputFile=null")
    }

    def "toString handles empty services"() {
        when:
        def config = new LogsConfig([])
        def string = config.toString()

        then:
        string.contains("services=[]")
    }

    def "supports common use cases"() {
        expect:
        // All services, last 50 lines
        def allServices = new LogsConfig([], 50)
        !allServices.hasSpecificServices()
        allServices.tailLines == 50

        // Specific services, follow mode
        def followServices = new LogsConfig(["web", "worker"], 100, true)
        followServices.hasSpecificServices()
        followServices.follow

        // Output to file
        def fileOutput = new LogsConfig(["db"], 1000, false, tempDir.resolve("db.log"))
        fileOutput.hasOutputFile()
        fileOutput.tailLines == 1000
    }

    def "immutable fields cannot be modified after construction"() {
        given:
        def config = new LogsConfig(["test"])

        expect:
        // Fields are final, so they cannot be reassigned
        config.services != null
        config.tailLines > 0
        config.follow != null
        // outputFile can be null, that's valid
    }

    def "handles large tail line values"() {
        expect:
        new LogsConfig([], Integer.MAX_VALUE).tailLines == Integer.MAX_VALUE
        new LogsConfig([], 1_000_000).tailLines == 1_000_000
    }

    def "supports service list variations"() {
        expect:
        // Single service
        new LogsConfig(["nginx"]).services == ["nginx"]
        
        // Multiple services
        new LogsConfig(["web", "api", "db", "cache"]).services.size() == 4
        
        // Duplicate services (if allowed by application logic)
        new LogsConfig(["web", "web"]).services == ["web", "web"]
    }

    def "boolean flags work correctly"() {
        expect:
        new LogsConfig([]).follow == false
        new LogsConfig([], 100, true).follow == true
        new LogsConfig([], 100, false).follow == false
    }

    def "output file can be any path"() {
        given:
        def absolutePath = tempDir.resolve("logs").resolve("app.log")
        def relativePath = Path.of("./logs.txt")

        expect:
        new LogsConfig([], 100, false, absolutePath).outputFile == absolutePath
        new LogsConfig([], 100, false, relativePath).outputFile == relativePath
    }
}