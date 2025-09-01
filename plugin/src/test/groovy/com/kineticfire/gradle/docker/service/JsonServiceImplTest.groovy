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
                'web': new ServiceState(
                    name: 'web',
                    status: 'running',
                    health: 'healthy',
                    ports: [
                        new PortMapping(hostPort: 8080, containerPort: 80, protocol: 'tcp')
                    ],
                    networks: ['default']
                ),
                'db': new ServiceState(
                    name: 'db', 
                    status: 'running',
                    health: 'healthy',
                    ports: [],
                    networks: ['default']
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
                    "name": "service1",
                    "status": "running",
                    "health": "healthy",
                    "ports": [
                        {
                            "hostPort": 3000,
                            "containerPort": 3000,
                            "protocol": "tcp"
                        }
                    ],
                    "networks": ["app-network"]
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
        result.services['service1'].name == 'service1'
        result.services['service1'].status == 'running'
        result.services['service1'].health == 'healthy'
        result.services['service1'].ports.size() == 1
        result.services['service1'].ports[0].hostPort == 3000
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
                'complex-service': new ServiceState(
                    name: 'complex-service',
                    status: 'running',
                    health: 'healthy',
                    ports: [
                        new PortMapping(hostPort: 8080, containerPort: 80, protocol: 'tcp'),
                        new PortMapping(hostPort: 8443, containerPort: 443, protocol: 'tcp')
                    ],
                    networks: ['frontend', 'backend']
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
        roundTripService.name == originalService.name
        roundTripService.status == originalService.status
        roundTripService.health == originalService.health
        roundTripService.ports.size() == 2
        roundTripService.networks == originalService.networks
        
        and:
        roundTrip.networks.containsAll(['frontend', 'backend', 'database'])
    }
}