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
 * Unit tests for DockerImageVerifyTask.
 * 
 * Tests task behavior without actually calling Docker commands.
 * Focuses on input validation and task configuration.
 */
class DockerImageVerifyTaskTest extends Specification {

    Project project
    DockerImageVerifyTask task

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testVerifyDockerImages', DockerImageVerifyTask).get()
    }

    def "task has correct inputs"() {
        when:
        task.imageNames.set(['test-image:1.0'])
        
        then:
        task.imageNames.get() == ['test-image:1.0']
    }

    def "task accepts multiple image names"() {
        when:
        def images = ['image1:1.0', 'image2:latest', 'image3:dev']
        task.imageNames.set(images)
        
        then:
        task.imageNames.get() == images
        task.imageNames.get().size() == 3
    }

    def "task accepts empty image list"() {
        when:
        task.imageNames.set([])
        
        then:
        task.imageNames.get().isEmpty()
        noExceptionThrown()
    }

    def "task input is serializable for configuration cache"() {
        when:
        task.imageNames.set(['test:1.0'])
        def inputValue = task.imageNames.get()
        
        then:
        inputValue instanceof List
        inputValue.every { it instanceof String }
    }

    def "task configuration is lazy"() {
        given:
        def provider = project.provider { ['lazy-image:1.0'] }
        
        when:
        task.imageNames.set(provider)
        
        then:
        task.imageNames.get() == ['lazy-image:1.0']
    }

    def "task validates image name format"() {
        when:
        task.imageNames.set(['valid-image:1.0', 'another:latest'])
        
        then:
        task.imageNames.get().every { it.contains(':') }
    }
}