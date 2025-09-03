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
import org.gradle.api.GradleException
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
        extension.composeStacks != null
    }

    def "can configure single compose stack"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        
        when:
        extension.composeStacks {
            testStack {
                files.from(project.file('docker-compose.yml'))
                profiles = ['test']
            }
        }

        then:
        extension.composeStacks.size() == 1
        extension.composeStacks.getByName('testStack') != null
        
        and:
        ComposeStackSpec stack = extension.composeStacks.getByName('testStack')
        stack.profiles.get() == ['test']
        stack.projectName.get() == "${project.name}-testStack"
    }

    def "can configure multiple compose stacks"() {
        given:
        project.file('docker-compose.dev.yml').text = 'version: "3.8"'
        project.file('docker-compose.prod.yml').text = 'version: "3.8"'
        
        when:
        extension.composeStacks {
            development {
                files.from(project.file('docker-compose.dev.yml'))
                profiles = ['dev', 'debug']
            }
            production {
                files.from(project.file('docker-compose.prod.yml'))
                profiles = ['prod']
            }
        }

        then:
        extension.composeStacks.size() == 2
        extension.composeStacks.getByName('development') != null
        extension.composeStacks.getByName('production') != null
        
        and:
        extension.composeStacks.getByName('development').profiles.get() == ['dev', 'debug']
        extension.composeStacks.getByName('production').profiles.get() == ['prod']
    }

    def "validate passes for valid stack configuration"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        
        extension.composeStacks {
            validStack {
                files.from(project.file('docker-compose.yml'))
                profiles = ['test']
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate fails when compose file does not exist"() {
        given:
        extension.composeStacks {
            missingFile {
                files.from(project.file('non-existent-compose.yml'))
                profiles = ['test']
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Compose file does not exist")
        ex.message.contains('missingFile')
    }

    def "validate fails when env file does not exist"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        
        extension.composeStacks {
            missingEnv {
                files.from(project.file('docker-compose.yml'))
                envFiles.from(project.file('non-existent.env'))
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Environment file does not exist")
        ex.message.contains('missingEnv')
    }

    def "can configure with environment variables"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        
        when:
        extension.composeStacks {
            envStack {
                files.from(project.file('docker-compose.yml'))
                environment = [
                    'ENV': 'test',
                    'DEBUG': 'true'
                ]
            }
        }

        then:
        ComposeStackSpec stack = extension.composeStacks.getByName('envStack')
        stack.environment.get() == [ENV: 'test', DEBUG: 'true']
    }

    def "can configure with services list"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        
        when:
        extension.composeStacks {
            servicesStack {
                files.from(project.file('docker-compose.yml'))
                services = ['web', 'db']
            }
        }

        then:
        ComposeStackSpec stack = extension.composeStacks.getByName('servicesStack')
        stack.services.get() == ['web', 'db']
    }

    def "can access stacks by name"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        
        extension.composeStacks {
            stack1 { files.from(project.file('docker-compose.yml')) }
            stack2 { files.from(project.file('docker-compose.yml')) }
        }

        expect:
        extension.composeStacks.getByName('stack1') != null
        extension.composeStacks.getByName('stack2') != null
        extension.composeStacks.getByName('stack1').name == 'stack1'
    }

    def "composeConfigs is alias for composeStacks"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        
        when:
        extension.composeConfigs {
            aliasTest {
                files.from(project.file('docker-compose.yml'))
                profiles = ['test']
            }
        }

        then:
        extension.composeConfigs.size() == 1
        extension.composeStacks.size() == 1
        extension.composeConfigs.getByName('aliasTest') != null
        extension.composeStacks.getByName('aliasTest') != null
    }

    def "validate handles empty stacks"() {
        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "stack names are preserved"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        
        when:
        extension.composeStacks {
            'my-test-stack' {
                files.from(project.file('docker-compose.yml'))
            }
        }

        then:
        extension.composeStacks.getByName('my-test-stack').name == 'my-test-stack'
        extension.composeStacks.getByName('my-test-stack').projectName.get() == "${project.name}-my-test-stack"
    }
}