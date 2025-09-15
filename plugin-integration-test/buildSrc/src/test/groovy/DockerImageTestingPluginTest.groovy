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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Simplified unit tests for Docker image testing functionality.
 * 
 * Tests basic task creation and configuration without complex plugin loading.
 */
class DockerImageTestingPluginTest extends Specification {

    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    def "can register clean task directly"() {
        when:
        def task = project.tasks.register('testCleanDockerImages', DockerImageCleanTask) {
            imageNames.set(['test-image:1.0', 'test-image:latest'])
            group = 'verification'
        }
        
        then:
        def cleanTask = task.get()
        cleanTask != null
        cleanTask instanceof DockerImageCleanTask
        cleanTask.imageNames.get() == ['test-image:1.0', 'test-image:latest']
        cleanTask.group == 'verification'
    }

    def "can register verify task directly"() {
        when:
        def task = project.tasks.register('testVerifyDockerImages', DockerImageVerifyTask) {
            imageNames.set(['test-image:1.0', 'test-image:latest'])
            group = 'verification'
        }
        
        then:
        def verifyTask = task.get()
        verifyTask != null
        verifyTask instanceof DockerImageVerifyTask
        verifyTask.imageNames.get() == ['test-image:1.0', 'test-image:latest']
        verifyTask.group == 'verification'
    }

    def "tasks can handle multiple images"() {
        given:
        def images = ['app:1.0', 'app:latest', 'db:5.7', 'cache:redis']
        
        when:
        def cleanTask = project.tasks.register('cleanTest', DockerImageCleanTask) {
            imageNames.set(images)
        }.get()
        
        def verifyTask = project.tasks.register('verifyTest', DockerImageVerifyTask) {
            imageNames.set(images)
        }.get()
        
        then:
        cleanTask.imageNames.get() == images
        verifyTask.imageNames.get() == images
        cleanTask.imageNames.get().size() == 4
        verifyTask.imageNames.get().size() == 4
    }

    def "tasks accept empty image list"() {
        when:
        def cleanTask = project.tasks.register('cleanEmpty', DockerImageCleanTask) {
            imageNames.set([])
        }.get()
        
        def verifyTask = project.tasks.register('verifyEmpty', DockerImageVerifyTask) {
            imageNames.set([])
        }.get()
        
        then:
        cleanTask.imageNames.get().isEmpty()
        verifyTask.imageNames.get().isEmpty()
        noExceptionThrown()
    }

    def "task inputs are properly configured for configuration cache"() {
        when:
        def cleanTask = project.tasks.register('cleanCache', DockerImageCleanTask) {
            imageNames.set(['test:1.0'])
        }.get()
        
        then:
        // Verify that inputs are of the correct Provider type for configuration cache
        cleanTask.imageNames != null
        cleanTask.imageNames.get() instanceof List
        cleanTask.imageNames.get().every { it instanceof String }
    }
}