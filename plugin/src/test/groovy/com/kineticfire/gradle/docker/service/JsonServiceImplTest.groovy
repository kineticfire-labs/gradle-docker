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
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for JsonServiceImpl
 */
class JsonServiceImplTest extends Specification {

    @TempDir
    Path tempDir

    JsonServiceImpl service = new JsonServiceImpl() {
        @Override
        BuildServiceParameters.None getParameters() {
            return null
        }
    }

    def "writeComposeState creates valid JSON file"() {
        given:
        def outputFile = tempDir.resolve('compose-state.json')
        def composeState = new ComposeState(
            configName: 'testCompose',
            projectName: 'test_project',
            services: [
                'web': new ServiceInfo(
                    containerId: 'web_container_id',
                    containerName: 'web',
                    state: 'running',
                    publishedPorts: [
                        new PortMapping(hostPort: 8080, containerPort: 80, protocol: 'tcp')
                    ]
                ),
                'db': new ServiceInfo(
                    containerId: 'db_container_id',
                    containerName: 'db', 
                    state: 'running',
                    publishedPorts: []
                )
            ],
            networks: ['default']
        )

        when:
        service.writeComposeState(composeState, outputFile)

        then:
        Files.exists(outputFile)
        
        and:
        def content = Files.readString(outputFile)
        content.contains('"configName":"testCompose"')
        content.contains('"projectName":"test_project"')
        content.contains('"web"')
        content.contains('"db"')
        content.contains('"hostPort":8080')
        content.contains('"containerPort":80')
        content.contains('"containerId":"web_container_id"')
        content.contains('"containerName":"web"')
    }

    def "readComposeState reads valid JSON file"() {
        given:
        def inputFile = tempDir.resolve('input-state.json')
        def jsonContent = '''
        {
            "configName": "testConfig",
            "projectName": "test_proj",
            "services": {
                "service1": {
                    "containerId": "service1_container_id",
                    "containerName": "service1",
                    "state": "running",
                    "publishedPorts": [
                        {
                            "hostPort": 3000,
                            "containerPort": 3000,
                            "protocol": "tcp"
                        }
                    ]
                }
            },
            "networks": ["app-network"]
        }
        '''
        Files.writeString(inputFile, jsonContent)

        when:
        def result = service.readComposeState(inputFile)

        then:
        result.configName == 'testConfig'
        result.projectName == 'test_proj'
        result.services.size() == 1
        result.services['service1'].containerId == 'service1_container_id'
        result.services['service1'].containerName == 'service1'
        result.services['service1'].state == 'running'
        result.services['service1'].publishedPorts.size() == 1
        result.services['service1'].publishedPorts[0].hostPort == 3000
        result.networks == ['app-network']
    }

    def "writeComposeState handles empty services"() {
        given:
        def outputFile = tempDir.resolve('empty-services.json')
        def composeState = new ComposeState(
            configName: 'emptyConfig',
            projectName: 'empty_project',
            services: [:],
            networks: []
        )

        when:
        service.writeComposeState(composeState, outputFile)

        then:
        Files.exists(outputFile)
        
        and:
        def content = Files.readString(outputFile)
        content.contains('"services":{}')
        content.contains('"networks":[]')
    }

    def "readComposeState throws exception for invalid JSON"() {
        given:
        def invalidFile = tempDir.resolve('invalid.json')
        Files.writeString(invalidFile, 'invalid json content {')

        when:
        service.readComposeState(invalidFile)

        then:
        thrown(RuntimeException)
    }

    def "readComposeState throws exception for non-existent file"() {
        given:
        def nonExistentFile = tempDir.resolve('does-not-exist.json')

        when:
        service.readComposeState(nonExistentFile)

        then:
        thrown(RuntimeException)
    }

    def "JSON serialization preserves all service information"() {
        given:
        def outputFile = tempDir.resolve('full-service.json')
        def composeState = new ComposeState(
            configName: 'fullConfig',
            projectName: 'full_project',
            services: [
                'complex-service': new ServiceInfo(
                    containerId: 'complex_service_container_id',
                    containerName: 'complex-service',
                    state: 'running',
                    publishedPorts: [
                        new PortMapping(hostPort: 8080, containerPort: 80, protocol: 'tcp'),
                        new PortMapping(hostPort: 8443, containerPort: 443, protocol: 'tcp')
                    ]
                )
            ],
            networks: ['frontend', 'backend', 'database']
        )

        when:
        service.writeComposeState(composeState, outputFile)
        def roundTrip = service.readComposeState(outputFile)

        then:
        roundTrip.configName == composeState.configName
        roundTrip.projectName == composeState.projectName
        roundTrip.services.size() == 1
        
        and:
        def originalService = composeState.services['complex-service']
        def roundTripService = roundTrip.services['complex-service']
        roundTripService.containerId == originalService.containerId
        roundTripService.containerName == originalService.containerName
        roundTripService.state == originalService.state
        roundTripService.publishedPorts.size() == 2
        
        and:
        roundTrip.networks.containsAll(['frontend', 'backend', 'database'])
    }

    def "writeComposeState handles null compose state"() {
        given:
        def outputFile = tempDir.resolve('output.json')

        when:
        service.writeComposeState(null, outputFile)

        then:
        Files.exists(outputFile)
        Files.readString(outputFile) == "null"
    }

    def "writeComposeState throws exception for null output file"() {
        given:
        def composeState = new ComposeState('test', 'test', [:], [])

        when:
        service.writeComposeState(composeState, null)

        then:
        thrown(RuntimeException)
    }

    def "readComposeState throws exception for null input file"() {
        when:
        service.readComposeState(null)

        then:
        thrown(RuntimeException)
    }

    def "writeComposeState handles special characters in names"() {
        given:
        def outputFile = tempDir.resolve('special-chars.json')
        def composeState = new ComposeState(
            configName: 'config with spaces & symbols!@#',
            projectName: 'project_with-dashes.and.dots',
            services: [
                'service-with_symbols.123': new ServiceInfo(
                    containerId: 'container_with-special.chars_123',
                    containerName: 'container with spaces',
                    state: 'exited (0)',
                    publishedPorts: []
                )
            ],
            networks: ['network-with_special.chars']
        )

        when:
        service.writeComposeState(composeState, outputFile)

        then:
        Files.exists(outputFile)
        
        and:
        def content = Files.readString(outputFile)
        content.contains('"config with spaces & symbols!@#"')
        content.contains('"project_with-dashes.and.dots"')
        content.contains('"service-with_symbols.123"')
    }

    def "readComposeState handles empty services object"() {
        given:
        def inputFile = tempDir.resolve('empty-services.json')
        def jsonContent = '''
        {
            "configName": "emptyServicesConfig",
            "projectName": "empty_services_project",
            "services": {},
            "networks": []
        }
        '''
        Files.writeString(inputFile, jsonContent)

        when:
        def result = service.readComposeState(inputFile)

        then:
        result.configName == 'emptyServicesConfig'
        result.projectName == 'empty_services_project'
        result.services.isEmpty()
        result.networks.isEmpty()
    }

    def "readComposeState handles malformed port mapping"() {
        given:
        def inputFile = tempDir.resolve('malformed-ports.json')
        def jsonContent = '''
        {
            "configName": "malformedConfig",
            "projectName": "malformed_project",
            "services": {
                "service1": {
                    "containerId": "container_id",
                    "containerName": "service1",
                    "state": "running",
                    "publishedPorts": [
                        {
                            "hostPort": "invalid",
                            "containerPort": 3000,
                            "protocol": "tcp"
                        }
                    ]
                }
            },
            "networks": []
        }
        '''
        Files.writeString(inputFile, jsonContent)

        when:
        service.readComposeState(inputFile)

        then:
        thrown(RuntimeException)
    }

    def "JsonServiceImpl implements JsonService interface"() {
        expect:
        JsonService.isAssignableFrom(JsonServiceImpl)
    }

    def "JsonServiceImpl extends BuildService"() {
        expect:
        org.gradle.api.services.BuildService.isAssignableFrom(JsonServiceImpl)
    }

    def "writeComposeState creates directory if it doesn't exist"() {
        given:
        def nestedDir = tempDir.resolve('nested/deep/directory')
        def outputFile = nestedDir.resolve('compose-state.json')
        def composeState = new ComposeState('test', 'test', [:], [])

        when:
        service.writeComposeState(composeState, outputFile)

        then:
        Files.exists(outputFile)
        Files.exists(nestedDir)
    }
}