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

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.*
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.exception.NotFoundException
import com.kineticfire.gradle.docker.exception.DockerServiceException
import com.kineticfire.gradle.docker.model.*
import org.gradle.api.services.BuildServiceParameters
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for DockerServiceImpl
 * Note: These are basic structural tests since DockerServiceImpl requires
 * a running Docker daemon for full integration testing.
 */
class DockerServiceImplTest extends Specification {

    def "DockerServiceImpl implements DockerService interface"() {
        expect:
        DockerService.isAssignableFrom(DockerServiceImpl)
    }

    def "DockerServiceImpl extends BuildService"() {
        expect:
        org.gradle.api.services.BuildService.isAssignableFrom(DockerServiceImpl)
    }

}