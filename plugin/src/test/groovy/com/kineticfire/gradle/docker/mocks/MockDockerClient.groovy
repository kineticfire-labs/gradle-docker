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

package com.kineticfire.gradle.docker.mocks

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.*
import com.github.dockerjava.api.model.Image
import spock.lang.Specification

/**
 * Configurable mock factory for DockerClient with common scenarios
 */
class MockDockerClient extends Specification {

    static DockerClient createSuccessfulClient() {
        def spec = new MockDockerClient()
        return spec.createSuccessfulMock()
    }

    static DockerClient createFailingClient(Exception error) {
        def spec = new MockDockerClient()
        return spec.createFailingMock(error)
    }

    DockerClient createSuccessfulMock() {
        def client = Mock(DockerClient)

        // Build command chain
        def buildCmd = Mock(BuildImageCmd)
        def buildCallback = Mock(BuildImageResultCallback)
        client.buildImageCmd() >> buildCmd
        buildCmd.withDockerfile(_) >> buildCmd
        buildCmd.withTags(_) >> buildCmd
        buildCmd.withLabels(_) >> buildCmd
        buildCmd.withBuildArgs(_) >> buildCmd
        buildCmd.exec(_) >> buildCallback
        buildCallback.awaitImageId() >> "sha256:12345abcdef"

        // Tag command
        def tagCmd = Mock(TagImageCmd)
        client.tagImageCmd(_, _, _) >> tagCmd
        tagCmd.exec() >> null

        // Save command
        def saveCmd = Mock(SaveImageCmd)
        def saveStream = Mock(InputStream)
        client.saveImageCmd(_) >> saveCmd
        saveCmd.exec() >> saveStream

        // Push command
        def pushCmd = Mock(PushImageCmd)
        def pushCallback = Mock(PushImageResultCallback)
        client.pushImageCmd(_) >> pushCmd
        pushCmd.withAuthConfig(_) >> pushCmd
        pushCmd.exec(_) >> pushCallback
        pushCallback.awaitCompletion() >> pushCallback

        // Pull command
        def pullCmd = Mock(PullImageCmd)
        def pullCallback = Mock(PullImageResultCallback)
        client.pullImageCmd(_) >> pullCmd
        pullCmd.withAuthConfig(_) >> pullCmd
        pullCmd.exec(_) >> pullCallback
        pullCallback.awaitCompletion() >> pullCallback

        // List images command
        def listImagesCmd = Mock(ListImagesCmd)
        client.listImagesCmd() >> listImagesCmd
        listImagesCmd.withImageNameFilter(_) >> listImagesCmd
        listImagesCmd.exec() >> [createMockImage()]

        return client
    }

    DockerClient createFailingMock(Exception error) {
        def client = Mock(DockerClient)

        // Build command - fails
        def buildCmd = Mock(BuildImageCmd)
        client.buildImageCmd() >> buildCmd
        buildCmd.withDockerfile(_) >> buildCmd
        buildCmd.withTags(_) >> buildCmd
        buildCmd.withLabels(_) >> buildCmd
        buildCmd.withBuildArgs(_) >> buildCmd
        buildCmd.exec(_) >> { throw error }

        // Tag command - fails
        def tagCmd = Mock(TagImageCmd)
        client.tagImageCmd(_, _, _) >> tagCmd
        tagCmd.exec() >> { throw error }

        // Save command - succeeds normally
        def saveCmd = Mock(SaveImageCmd)
        def saveStream = Mock(InputStream)
        client.saveImageCmd(_) >> saveCmd
        saveCmd.exec() >> saveStream

        // Push command - fails
        def pushCmd = Mock(PushImageCmd)
        client.pushImageCmd(_) >> pushCmd
        pushCmd.withAuthConfig(_) >> pushCmd
        pushCmd.exec(_) >> { throw error }

        // Pull command - fails
        def pullCmd = Mock(PullImageCmd)
        client.pullImageCmd(_) >> pullCmd
        pullCmd.withAuthConfig(_) >> pullCmd
        pullCmd.exec(_) >> { throw error }

        return client
    }

    static Image createMockImage() {
        def spec = new MockDockerClient()
        return spec.createMockImageImpl()
    }

    Image createMockImageImpl() {
        Mock(Image) {
            getId() >> "sha256:12345abcdef"
            getRepoTags() >> ["test:latest"]
            getSize() >> 1234567L
        }
    }
}