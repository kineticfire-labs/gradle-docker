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

import com.kineticfire.gradle.docker.exception.ComposeServiceException
import spock.lang.Specification

/**
 * Unit tests for CommandValidator interface and implementations
 */
class CommandValidatorTest extends Specification {

    ProcessExecutor mockProcessExecutor = Mock(ProcessExecutor)
    DefaultCommandValidator validator

    def setup() {
        validator = new DefaultCommandValidator(mockProcessExecutor)
    }

    def "detectComposeCommand returns docker compose when available"() {
        given:
        mockProcessExecutor.execute(['docker', 'compose', 'version'], null, _) >> 
            new ProcessResult(0, "docker compose version", "")

        when:
        def command = validator.detectComposeCommand()

        then:
        command == ['docker', 'compose']
    }

    def "detectComposeCommand falls back to docker-compose when docker compose fails"() {
        given:
        mockProcessExecutor.execute(['docker', 'compose', 'version'], null, _) >> 
            new ProcessResult(1, "", "command not found")
        mockProcessExecutor.execute(['docker-compose', '--version'], null, _) >> 
            new ProcessResult(0, "docker-compose version", "")

        when:
        def command = validator.detectComposeCommand()

        then:
        command == ['docker-compose']
    }

    def "detectComposeCommand throws exception when neither command works"() {
        given:
        mockProcessExecutor.execute(['docker', 'compose', 'version'], null, _) >> 
            new ProcessResult(1, "", "command not found")
        mockProcessExecutor.execute(['docker-compose', '--version'], null, _) >> 
            new ProcessResult(127, "", "command not found")

        when:
        validator.detectComposeCommand()

        then:
        def ex = thrown(ComposeServiceException)
        ex.message.contains("Neither 'docker compose' nor 'docker-compose' command is available")
        ex.errorType == ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE
    }

    def "detectComposeCommand throws exception when process executor throws"() {
        given:
        mockProcessExecutor.execute(['docker', 'compose', 'version'], null, _) >> 
            { throw new RuntimeException("Process execution failed") }
        mockProcessExecutor.execute(['docker-compose', '--version'], null, _) >> 
            { throw new RuntimeException("Process execution failed") }

        when:
        validator.detectComposeCommand()

        then:
        def ex = thrown(ComposeServiceException)
        ex.errorType == ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE
    }

    def "detectComposeCommand works with docker compose"() {
        given:
        mockProcessExecutor.execute(['docker', 'compose', 'version'], null, _) >> 
            new ProcessResult(0, "docker compose version", "")

        when:
        def command = validator.detectComposeCommand()

        then:
        command == ['docker', 'compose']
    }

    def "validateDockerCompose succeeds when version command works"() {
        given:
        mockProcessExecutor.execute(['docker', 'compose', 'version'], null, _) >>> [
            new ProcessResult(0, "docker compose version", ""),
            new ProcessResult(0, "Docker Compose version v2.0.0", "")
        ]

        when:
        validator.validateDockerCompose()

        then:
        noExceptionThrown()
    }

    def "validateDockerCompose throws exception when version command fails"() {
        given:
        mockProcessExecutor.execute(['docker', 'compose', 'version'], null, _) >>> [
            new ProcessResult(0, "docker compose version", ""),
            new ProcessResult(1, "", "Docker Compose not working")
        ]

        when:
        validator.validateDockerCompose()

        then:
        def ex = thrown(ComposeServiceException)
        ex.message.contains("Docker Compose validation failed")
        ex.errorType == ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE
    }

    def "validateDockerCompose throws exception when process executor throws"() {
        given:
        mockProcessExecutor.execute(['docker', 'compose', 'version'], null, _) >>> [
            new ProcessResult(0, "docker compose version", ""),
            { throw new RuntimeException("Process execution failed") }
        ]

        when:
        validator.validateDockerCompose()

        then:
        def ex = thrown(ComposeServiceException)
        ex.message.contains("Docker Compose is not available or not working")
        ex.errorType == ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE
        ex.cause instanceof RuntimeException
    }
}