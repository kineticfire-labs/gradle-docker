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

package com.kineticfire.gradle.docker.workflow.operation

import com.kineticfire.gradle.docker.model.SaveCompression
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.SaveSpec
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for SaveOperationExecutor
 */
class SaveOperationExecutorTest extends Specification {

    @TempDir
    Path tempDir

    Project project
    SaveOperationExecutor executor
    ImageSpec imageSpec
    SaveSpec saveSpec
    DockerService dockerService

    def setup() {
        project = ProjectBuilder.builder().build()
        executor = new SaveOperationExecutor()

        imageSpec = project.objects.newInstance(
            ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])

        saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
        saveSpec.outputFile.set(tempDir.resolve('image.tar').toFile())

        dockerService = Mock(DockerService)
    }

    // ===== EXECUTE VALIDATION TESTS =====

    def "execute throws exception when saveSpec is null"() {
        when:
        executor.execute(null, imageSpec, dockerService)

        then:
        def e = thrown(GradleException)
        e.message.contains('SaveSpec cannot be null')
    }

    def "execute throws exception when imageSpec is null"() {
        when:
        executor.execute(saveSpec, null, dockerService)

        then:
        def e = thrown(GradleException)
        e.message.contains('ImageSpec cannot be null')
    }

    def "execute throws exception when dockerService is null"() {
        when:
        executor.execute(saveSpec, imageSpec, null)

        then:
        def e = thrown(GradleException)
        e.message.contains('DockerService cannot be null')
    }

    // ===== BUILD IMAGE REFERENCE TESTS =====

    def "buildImageReference builds reference from imageName and first tag"() {
        given:
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0', 'latest'])

        when:
        def result = executor.buildImageReference(imageSpec)

        then:
        result == 'myapp:1.0.0'
    }

    def "buildImageReference includes registry when set"() {
        given:
        imageSpec.registry.set('docker.io')
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])

        when:
        def result = executor.buildImageReference(imageSpec)

        then:
        result == 'docker.io/myapp:1.0.0'
    }

    def "buildImageReference includes namespace when set"() {
        given:
        imageSpec.namespace.set('mycompany')
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])

        when:
        def result = executor.buildImageReference(imageSpec)

        then:
        result == 'mycompany/myapp:1.0.0'
    }

    def "buildImageReference includes registry and namespace"() {
        given:
        imageSpec.registry.set('gcr.io')
        imageSpec.namespace.set('myproject')
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['v1.2.3'])

        when:
        def result = executor.buildImageReference(imageSpec)

        then:
        result == 'gcr.io/myproject/myapp:v1.2.3'
    }

    def "buildImageReference uses repository when set"() {
        given:
        imageSpec.registry.set('docker.io')
        imageSpec.repository.set('library/nginx')
        imageSpec.imageName.set('')
        imageSpec.tags.set(['1.25.0'])

        when:
        def result = executor.buildImageReference(imageSpec)

        then:
        result == 'docker.io/library/nginx:1.25.0'
    }

    def "buildImageReference throws when no tags configured"() {
        given:
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set([])

        when:
        executor.buildImageReference(imageSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains('no tags configured')
    }

    def "buildImageReference throws when cannot build reference"() {
        given:
        imageSpec.imageName.set('')
        imageSpec.repository.set('')
        imageSpec.tags.set(['1.0.0'])

        when:
        executor.buildImageReference(imageSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains('Cannot build image reference')
    }

    // ===== RESOLVE OUTPUT FILE TESTS =====

    def "resolveOutputFile returns path from saveSpec"() {
        given:
        def outputPath = tempDir.resolve('my-image.tar')
        saveSpec.outputFile.set(outputPath.toFile())

        when:
        def result = executor.resolveOutputFile(saveSpec)

        then:
        result == outputPath
    }

    def "resolveOutputFile uses convention when not explicitly set"() {
        given:
        // Create SaveSpec which will have convention value for outputFile
        def specWithConvention = project.objects.newInstance(SaveSpec, project.objects, project.layout)

        when:
        def result = executor.resolveOutputFile(specWithConvention)

        then:
        // Convention sets default to build/docker-images/image.tar
        result.toString().endsWith('docker-images/image.tar')
    }

    // ===== RESOLVE COMPRESSION TESTS =====

    def "resolveCompression returns NONE when not set"() {
        when:
        def result = executor.resolveCompression(saveSpec)

        then:
        result == SaveCompression.NONE
    }

    def "resolveCompression returns GZIP when set"() {
        given:
        saveSpec.compression.set(SaveCompression.GZIP)

        when:
        def result = executor.resolveCompression(saveSpec)

        then:
        result == SaveCompression.GZIP
    }

    def "resolveCompression returns BZIP2 when set"() {
        given:
        saveSpec.compression.set(SaveCompression.BZIP2)

        when:
        def result = executor.resolveCompression(saveSpec)

        then:
        result == SaveCompression.BZIP2
    }

    def "resolveCompression returns XZ when set"() {
        given:
        saveSpec.compression.set(SaveCompression.XZ)

        when:
        def result = executor.resolveCompression(saveSpec)

        then:
        result == SaveCompression.XZ
    }

    def "resolveCompression returns ZIP when set"() {
        given:
        saveSpec.compression.set(SaveCompression.ZIP)

        when:
        def result = executor.resolveCompression(saveSpec)

        then:
        result == SaveCompression.ZIP
    }

    // ===== EXECUTE SAVE TESTS =====

    def "executeSave calls dockerService.saveImage"() {
        given:
        def imageRef = 'myapp:1.0.0'
        def outputFile = tempDir.resolve('image.tar')
        def compression = SaveCompression.NONE
        dockerService.saveImage(imageRef, outputFile, compression) >> CompletableFuture.completedFuture(null)

        when:
        executor.executeSave(imageRef, outputFile, compression, dockerService)

        then:
        1 * dockerService.saveImage(imageRef, outputFile, compression) >> CompletableFuture.completedFuture(null)
    }

    def "executeSave with GZIP compression"() {
        given:
        def imageRef = 'myapp:1.0.0'
        def outputFile = tempDir.resolve('image.tar.gz')
        def compression = SaveCompression.GZIP
        dockerService.saveImage(imageRef, outputFile, compression) >> CompletableFuture.completedFuture(null)

        when:
        executor.executeSave(imageRef, outputFile, compression, dockerService)

        then:
        1 * dockerService.saveImage(imageRef, outputFile, compression) >> CompletableFuture.completedFuture(null)
    }

    def "executeSave waits for future completion"() {
        given:
        def imageRef = 'myapp:1.0.0'
        def outputFile = tempDir.resolve('image.tar')
        def compression = SaveCompression.NONE
        def futureCompleted = false
        def future = new CompletableFuture<Void>()
        future.whenComplete { result, error -> futureCompleted = true }
        future.complete(null)
        dockerService.saveImage(imageRef, outputFile, compression) >> future

        when:
        executor.executeSave(imageRef, outputFile, compression, dockerService)

        then:
        futureCompleted
    }

    // ===== EXECUTE INTEGRATION TESTS =====

    def "execute saves image successfully"() {
        given:
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])
        def outputPath = tempDir.resolve('image.tar')
        saveSpec.outputFile.set(outputPath.toFile())
        saveSpec.compression.set(SaveCompression.NONE)
        dockerService.saveImage('myapp:1.0.0', outputPath, SaveCompression.NONE) >>
            CompletableFuture.completedFuture(null)

        when:
        executor.execute(saveSpec, imageSpec, dockerService)

        then:
        1 * dockerService.saveImage('myapp:1.0.0', outputPath, SaveCompression.NONE) >>
            CompletableFuture.completedFuture(null)
    }

    def "execute with GZIP compression"() {
        given:
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])
        def outputPath = tempDir.resolve('image.tar.gz')
        saveSpec.outputFile.set(outputPath.toFile())
        saveSpec.compression.set(SaveCompression.GZIP)
        dockerService.saveImage('myapp:1.0.0', outputPath, SaveCompression.GZIP) >>
            CompletableFuture.completedFuture(null)

        when:
        executor.execute(saveSpec, imageSpec, dockerService)

        then:
        1 * dockerService.saveImage('myapp:1.0.0', outputPath, SaveCompression.GZIP) >>
            CompletableFuture.completedFuture(null)
    }

    def "execute with full image path"() {
        given:
        imageSpec.registry.set('ghcr.io')
        imageSpec.namespace.set('myorg')
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['v1.0.0'])
        def outputPath = tempDir.resolve('image.tar')
        saveSpec.outputFile.set(outputPath.toFile())
        dockerService.saveImage('ghcr.io/myorg/myapp:v1.0.0', outputPath, SaveCompression.NONE) >>
            CompletableFuture.completedFuture(null)

        when:
        executor.execute(saveSpec, imageSpec, dockerService)

        then:
        1 * dockerService.saveImage('ghcr.io/myorg/myapp:v1.0.0', outputPath, SaveCompression.NONE) >>
            CompletableFuture.completedFuture(null)
    }

    def "execute wraps dockerService exception in GradleException"() {
        given:
        imageSpec.imageName.set('myapp')
        imageSpec.tags.set(['1.0.0'])
        def outputPath = tempDir.resolve('image.tar')
        saveSpec.outputFile.set(outputPath.toFile())
        def failedFuture = new CompletableFuture<Void>()
        failedFuture.completeExceptionally(new RuntimeException("Docker error"))
        dockerService.saveImage(_, _, _) >> failedFuture

        when:
        executor.execute(saveSpec, imageSpec, dockerService)

        then:
        def e = thrown(GradleException)
        e.message.contains('Failed to save image')
    }
}
