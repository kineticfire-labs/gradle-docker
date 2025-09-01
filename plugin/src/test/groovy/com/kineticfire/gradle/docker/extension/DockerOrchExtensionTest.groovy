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

package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for DockerOrchExtension
 */
class DockerOrchExtensionTest extends Specification {

    Project project
    DockerOrchExtension extension

    def setup() {
        project = ProjectBuilder.builder().build()
        extension = project.objects.newInstance(DockerOrchExtension, project.objects, project)
    }

    def "extension can be created"() {
        expect:
        extension != null
        extension.composeConfigs != null
    }

    def "can configure single compose config"() {
        when:
        extension.composeConfigs {
            testCompose {
                composeFile = project.file('docker-compose.yml')
                profiles = ['test']
                envFile = project.file('.env.test')
            }
        }

        then:
        extension.composeConfigs.size() == 1
        extension.composeConfigs.getByName('testCompose') != null

        and:
        ComposeStackSpec config = extension.composeConfigs.getByName('testCompose')
        config.composeFile.get().asFile == project.file('docker-compose.yml')
        config.profiles.get() == ['test']
        config.envFile.get().asFile == project.file('.env.test')
    }

    def "can configure multiple compose configs"() {
        when:
        extension.composeConfigs {
            development {
                composeFile = project.file('docker-compose.dev.yml')
                profiles = ['dev', 'debug']
            }
            production {
                composeFile = project.file('docker-compose.prod.yml')
                profiles = ['prod']
                envFile = project.file('.env.prod')
            }
        }

        then:
        extension.composeConfigs.size() == 2
        extension.composeConfigs.getByName('development') != null
        extension.composeConfigs.getByName('production') != null

        and:
        extension.composeConfigs.getByName('development').profiles.get() == ['dev', 'debug']
        extension.composeConfigs.getByName('production').profiles.get() == ['prod']
    }

    def "can configure services for compose config"() {
        when:
        extension.composeConfigs {
            fullStack {
                composeFile = project.file('docker-compose.yml')
                services = ['web', 'api', 'database']
            }
        }

        then:
        ComposeStackSpec config = extension.composeConfigs.getByName('fullStack')
        config.services.get() == ['web', 'api', 'database']
    }

    def "can configure environment variables"() {
        when:
        extension.composeConfigs {
            envTest {
                composeFile = project.file('docker-compose.yml')
                environment = [
                    'NODE_ENV': 'test',
                    'DATABASE_URL': 'postgresql://localhost:5432/test'
                ]
            }
        }

        then:
        ComposeStackSpec config = extension.composeConfigs.getByName('envTest')
        config.environment.get() == [
            'NODE_ENV': 'test',
            'DATABASE_URL': 'postgresql://localhost:5432/test'
        ]
    }

    def "can access configured compose configs by name"() {
        given:
        extension.composeConfigs {
            config1 { composeFile = project.file('compose1.yml') }
            config2 { composeFile = project.file('compose2.yml') }
            config3 { composeFile = project.file('compose3.yml') }
        }

        expect:
        extension.composeConfigs.getByName('config1') != null
        extension.composeConfigs.getByName('config2') != null
        extension.composeConfigs.getByName('config3') != null
        extension.composeConfigs.getByName('config1').composeFile.get().asFile == project.file('compose1.yml')
    }
}