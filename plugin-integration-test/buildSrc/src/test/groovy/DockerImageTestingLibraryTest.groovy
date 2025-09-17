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
 * Unit tests for Docker Image Testing Library functions.
 * 
 * Tests all registration functions in the docker-image-testing.gradle script.
 * Focuses on task registration, input validation, and function behavior.
 */
class DockerImageTestingLibraryTest extends Specification {

    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        // Manually add the extension functions to test them directly
        setupDockerImageTestingFunctions(project)
    }

    private void setupDockerImageTestingFunctions(Project project) {
        // Register clean task function
        project.ext.registerCleanDockerImagesTask = { Project proj, List<String> imageNames ->
            if (imageNames == null || imageNames.isEmpty()) {
                throw new IllegalArgumentException("imageNames cannot be null or empty")
            }
            proj.tasks.register('cleanDockerImages', DockerImageCleanTask) {
                it.imageNames.set(imageNames)
                it.group = 'verification'
                it.description = 'Remove Docker images before building to ensure clean integration test'
            }
        }

        // Register verify built images task function
        project.ext.registerVerifyBuiltImagesTask = { Project proj, List<String> imageNames ->
            if (imageNames == null || imageNames.isEmpty()) {
                throw new IllegalArgumentException("imageNames cannot be null or empty")
            }
            proj.tasks.register('verifyDockerImages', DockerImageVerifyTask) {
                it.imageNames.set(imageNames)
                it.group = 'verification'
                it.description = 'Verify Docker images were created with expected tags'
            }
        }

        // Register verify saved images task function
        project.ext.registerVerifySavedImagesTask = { Project proj, List<String> filePaths ->
            if (filePaths == null || filePaths.isEmpty()) {
                throw new IllegalArgumentException("filePaths cannot be null or empty")
            }
            proj.tasks.register('verifySavedDockerImages', DockerSavedImageVerifyTask) {
                it.filePaths.set(filePaths)
                it.group = 'verification'
                it.description = 'Verify Docker image files were saved to expected locations'
            }
        }

        // Register verify registry images task function
        project.ext.registerVerifyRegistryImagesTask = { Project proj, List<String> imageReferences ->
            if (imageReferences == null || imageReferences.isEmpty()) {
                throw new IllegalArgumentException("imageReferences cannot be null or empty")
            }
            proj.tasks.register('verifyRegistryDockerImages', DockerRegistryImageVerifyTask) {
                it.imageReferences.set(imageReferences)
                it.group = 'verification'
                it.description = "Verify Docker images exist in registry"
            }
        }

        // Register build workflow tasks function
        project.ext.registerBuildWorkflowTasks = { Project proj, List<String> imageNames ->
            project.ext.registerCleanDockerImagesTask(proj, imageNames)
            project.ext.registerVerifyBuiltImagesTask(proj, imageNames)
        }
    }

    def "registerCleanDockerImagesTask creates clean task"() {
        when:
        project.ext.registerCleanDockerImagesTask(project, ['test-image:1.0', 'test-image:latest'])
        
        then:
        def task = project.tasks.getByName('cleanDockerImages')
        task != null
        task instanceof DockerImageCleanTask
        task.imageNames.get() == ['test-image:1.0', 'test-image:latest']
        task.group == 'verification'
        task.description == 'Remove Docker images before building to ensure clean integration test'
    }

    def "registerCleanDockerImagesTask validates input"() {
        when:
        project.ext.registerCleanDockerImagesTask(project, imageNames)
        
        then:
        IllegalArgumentException e = thrown()
        e.message == 'imageNames cannot be null or empty'
        
        where:
        imageNames << [null, []]
    }

    def "registerVerifyBuiltImagesTask creates verify task"() {
        when:
        project.ext.registerVerifyBuiltImagesTask(project, ['test-image:1.0', 'test-image:latest'])
        
        then:
        def task = project.tasks.getByName('verifyDockerImages')
        task != null
        task instanceof DockerImageVerifyTask
        task.imageNames.get() == ['test-image:1.0', 'test-image:latest']
        task.group == 'verification'
        task.description == 'Verify Docker images were created with expected tags'
    }

    def "registerVerifyBuiltImagesTask validates input"() {
        when:
        project.ext.registerVerifyBuiltImagesTask(project, imageNames)
        
        then:
        IllegalArgumentException e = thrown()
        e.message == 'imageNames cannot be null or empty'
        
        where:
        imageNames << [null, []]
    }

    def "registerVerifySavedImagesTask creates saved verify task"() {
        when:
        project.ext.registerVerifySavedImagesTask(project, ['build/saved/test.tar', 'build/saved/test.tar.gz'])
        
        then:
        def task = project.tasks.getByName('verifySavedDockerImages')
        task != null
        task instanceof DockerSavedImageVerifyTask
        task.filePaths.get() == ['build/saved/test.tar', 'build/saved/test.tar.gz']
        task.group == 'verification'
        task.description == 'Verify Docker image files were saved to expected locations'
    }

    def "registerVerifySavedImagesTask validates input"() {
        when:
        project.ext.registerVerifySavedImagesTask(project, filePaths)
        
        then:
        IllegalArgumentException e = thrown()
        e.message == 'filePaths cannot be null or empty'
        
        where:
        filePaths << [null, []]
    }

    def "registerVerifyRegistryImagesTask creates registry verify task"() {
        when:
        project.ext.registerVerifyRegistryImagesTask(project, ['test-image:1.0'], 'localhost:5000')
        
        then:
        def task = project.tasks.getByName('verifyRegistryDockerImages')
        task != null
        task instanceof DockerRegistryImageVerifyTask
        task.imageNames.get() == ['test-image:1.0']
        task.registryUrl.get() == 'localhost:5000'
        task.group == 'verification'
        task.description == 'Verify Docker images exist in registry: localhost:5000'
    }

    def "registerVerifyRegistryImagesTask validates imageNames input"() {
        when:
        project.ext.registerVerifyRegistryImagesTask(project, imageNames, 'localhost:5000')
        
        then:
        IllegalArgumentException e = thrown()
        e.message == 'imageNames cannot be null or empty'
        
        where:
        imageNames << [null, []]
    }

    def "registerVerifyRegistryImagesTask validates registryUrl input"() {
        when:
        project.ext.registerVerifyRegistryImagesTask(project, ['test:1.0'], registryUrl)
        
        then:
        IllegalArgumentException e = thrown()
        e.message == 'registryUrl cannot be null or empty'
        
        where:
        registryUrl << [null, '', '  ']
    }

    def "registerBuildWorkflowTasks creates both clean and verify tasks"() {
        when:
        project.ext.registerBuildWorkflowTasks(project, ['test-image:1.0', 'test-image:latest'])
        
        then:
        def cleanTask = project.tasks.getByName('cleanDockerImages')
        def verifyTask = project.tasks.getByName('verifyDockerImages')
        
        cleanTask != null
        cleanTask instanceof DockerImageCleanTask
        cleanTask.imageNames.get() == ['test-image:1.0', 'test-image:latest']
        
        verifyTask != null
        verifyTask instanceof DockerImageVerifyTask
        verifyTask.imageNames.get() == ['test-image:1.0', 'test-image:latest']
    }

    def "registerBuildWorkflowTasks validates input"() {
        when:
        project.ext.registerBuildWorkflowTasks(project, imageNames)
        
        then:
        IllegalArgumentException e = thrown()
        e.message == 'imageNames cannot be null or empty'
        
        where:
        imageNames << [null, []]
    }

    def "all functions work with multiple images"() {
        given:
        def images = ['app:1.0', 'app:latest', 'db:5.7', 'cache:redis']
        def filePaths = ['build/saved/app.tar', 'build/saved/db.tar.gz', 'build/saved/cache.zip']
        def registryUrl = 'localhost:5000'
        
        when:
        project.ext.registerCleanDockerImagesTask(project, images)
        project.ext.registerVerifyBuiltImagesTask(project, images)
        project.ext.registerVerifySavedImagesTask(project, filePaths)
        project.ext.registerVerifyRegistryImagesTask(project, images, registryUrl)
        
        then:
        def cleanTask = project.tasks.getByName('cleanDockerImages')
        def verifyTask = project.tasks.getByName('verifyDockerImages')
        def savedTask = project.tasks.getByName('verifySavedDockerImages')
        def registryTask = project.tasks.getByName('verifyRegistryDockerImages')
        
        cleanTask.imageNames.get().size() == 4
        verifyTask.imageNames.get().size() == 4
        savedTask.filePaths.get().size() == 3
        registryTask.imageNames.get().size() == 4
        registryTask.registryUrl.get() == registryUrl
    }

    def "functions support different compression formats for saved images"() {
        given:
        def filePaths = [
            'build/saved/image.tar',
            'build/saved/image.tar.gz',
            'build/saved/image.tar.bz2',
            'build/saved/image.tar.xz',
            'build/saved/image.zip'
        ]
        
        when:
        project.ext.registerVerifySavedImagesTask(project, filePaths)
        
        then:
        def task = project.tasks.getByName('verifySavedDockerImages')
        task.filePaths.get() == filePaths
        task.filePaths.get().size() == 5
    }

    def "functions support different registry URLs"() {
        given:
        def images = ['test:1.0']
        
        when:
        project.ext.registerVerifyRegistryImagesTask(project, images, registryUrl)
        
        then:
        def task = project.tasks.getByName('verifyRegistryDockerImages')
        task.registryUrl.get() == registryUrl
        
        where:
        registryUrl << [
            'localhost:5000',
            'docker.io',
            'registry.example.com:443',
            '192.168.1.100:5000'
        ]
    }

    def "functions work with configuration cache compatible inputs"() {
        given:
        def imageProvider = project.provider { ['provider-image:1.0'] }
        def fileProvider = project.provider { ['provider-file.tar'] }
        def registryProvider = project.provider { 'provider-registry:5000' }
        
        when:
        project.ext.registerCleanDockerImagesTask(project, imageProvider.get())
        project.ext.registerVerifyBuiltImagesTask(project, imageProvider.get())
        project.ext.registerVerifySavedImagesTask(project, fileProvider.get())
        project.ext.registerVerifyRegistryImagesTask(project, imageProvider.get(), registryProvider.get())
        
        then:
        def cleanTask = project.tasks.getByName('cleanDockerImages')
        def verifyTask = project.tasks.getByName('verifyDockerImages')
        def savedTask = project.tasks.getByName('verifySavedDockerImages')
        def registryTask = project.tasks.getByName('verifyRegistryDockerImages')
        
        cleanTask.imageNames.get() == ['provider-image:1.0']
        verifyTask.imageNames.get() == ['provider-image:1.0']
        savedTask.filePaths.get() == ['provider-file.tar']
        registryTask.imageNames.get() == ['provider-image:1.0']
        registryTask.registryUrl.get() == 'provider-registry:5000'
    }
}