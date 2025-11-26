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

package com.kineticfire.gradle.docker

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for SaveOperationExecutor
 *
 * These tests verify the save operation workflow through Gradle's task infrastructure.
 * Note: These tests do not call actual Docker operations - they validate DSL,
 * configuration, validation, and executor patterns.
 */
class SaveOperationFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-save-operation'"
    }

    def "SaveOperationExecutor builds correct image reference"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.SaveOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec

            task verifyImageReference {
                doLast {
                    def executor = new SaveOperationExecutor()

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.imageName.set('myapp')
                    imageSpec.tags.set(['1.0.0'])

                    def imageRef = executor.buildImageReference(imageSpec)
                    assert imageRef == 'myapp:1.0.0' : "Expected 'myapp:1.0.0' but got '\${imageRef}'"

                    println "Image reference verified: \${imageRef}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyImageReference')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Image reference verified: myapp:1.0.0')
        result.task(':verifyImageReference').outcome == TaskOutcome.SUCCESS
    }

    def "SaveOperationExecutor builds image reference with registry and namespace"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.SaveOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec

            task verifyFullReference {
                doLast {
                    def executor = new SaveOperationExecutor()

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.registry.set('ghcr.io')
                    imageSpec.namespace.set('myorg')
                    imageSpec.imageName.set('myapp')
                    imageSpec.tags.set(['v1.2.3'])

                    def imageRef = executor.buildImageReference(imageSpec)
                    assert imageRef == 'ghcr.io/myorg/myapp:v1.2.3'

                    println "Full reference verified: \${imageRef}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyFullReference')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Full reference verified: ghcr.io/myorg/myapp:v1.2.3')
        result.task(':verifyFullReference').outcome == TaskOutcome.SUCCESS
    }

    def "SaveOperationExecutor resolves output file from SaveSpec"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.SaveOperationExecutor
            import com.kineticfire.gradle.docker.spec.SaveSpec

            task verifyOutputFile {
                doLast {
                    def executor = new SaveOperationExecutor()

                    def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
                    saveSpec.outputFile.set(file('output/my-image.tar'))

                    def outputPath = executor.resolveOutputFile(saveSpec)
                    assert outputPath.toString().endsWith('output/my-image.tar')

                    println "Output file verified: \${outputPath}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyOutputFile')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Output file verified:')
        result.output.contains('output/my-image.tar')
        result.task(':verifyOutputFile').outcome == TaskOutcome.SUCCESS
    }

    def "SaveOperationExecutor resolves compression settings"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.SaveOperationExecutor
            import com.kineticfire.gradle.docker.spec.SaveSpec
            import com.kineticfire.gradle.docker.model.SaveCompression

            task verifyCompression {
                doLast {
                    def executor = new SaveOperationExecutor()

                    // Test default compression (NONE)
                    def defaultSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
                    def defaultCompression = executor.resolveCompression(defaultSpec)
                    assert defaultCompression == SaveCompression.NONE : "Expected NONE but got \${defaultCompression}"

                    // Test GZIP compression
                    def gzipSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
                    gzipSpec.compression.set(SaveCompression.GZIP)
                    def gzipCompression = executor.resolveCompression(gzipSpec)
                    assert gzipCompression == SaveCompression.GZIP : "Expected GZIP but got \${gzipCompression}"

                    // Test BZIP2 compression
                    def bzip2Spec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
                    bzip2Spec.compression.set(SaveCompression.BZIP2)
                    def bzip2Compression = executor.resolveCompression(bzip2Spec)
                    assert bzip2Compression == SaveCompression.BZIP2 : "Expected BZIP2 but got \${bzip2Compression}"

                    println "Compression settings verified: NONE, GZIP, BZIP2"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyCompression')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Compression settings verified: NONE, GZIP, BZIP2')
        result.task(':verifyCompression').outcome == TaskOutcome.SUCCESS
    }

    def "SaveOperationExecutor validates null SaveSpec"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.SaveOperationExecutor
            import org.gradle.api.GradleException

            task verifyNullSaveSpec {
                doLast {
                    def executor = new SaveOperationExecutor()

                    try {
                        executor.execute(null, null, null)
                        throw new AssertionError('Should have thrown exception')
                    } catch (GradleException e) {
                        assert e.message.contains('SaveSpec cannot be null')
                    }

                    println "Null SaveSpec validation verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyNullSaveSpec')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Null SaveSpec validation verified!')
        result.task(':verifyNullSaveSpec').outcome == TaskOutcome.SUCCESS
    }

    def "SaveOperationExecutor validates null ImageSpec"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.SaveOperationExecutor
            import com.kineticfire.gradle.docker.spec.SaveSpec
            import org.gradle.api.GradleException

            task verifyNullImageSpec {
                doLast {
                    def executor = new SaveOperationExecutor()
                    def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)

                    try {
                        executor.execute(saveSpec, null, null)
                        throw new AssertionError('Should have thrown exception')
                    } catch (GradleException e) {
                        assert e.message.contains('ImageSpec cannot be null')
                    }

                    println "Null ImageSpec validation verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyNullImageSpec')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Null ImageSpec validation verified!')
        result.task(':verifyNullImageSpec').outcome == TaskOutcome.SUCCESS
    }

    def "SaveOperationExecutor validates no tags configured"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.SaveOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec
            import org.gradle.api.GradleException

            task verifyNoTags {
                doLast {
                    def executor = new SaveOperationExecutor()

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.imageName.set('myapp')
                    imageSpec.tags.set([])

                    try {
                        executor.buildImageReference(imageSpec)
                        throw new AssertionError('Should have thrown exception')
                    } catch (GradleException e) {
                        assert e.message.contains('no tags configured')
                    }

                    println "No tags validation verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyNoTags')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('No tags validation verified!')
        result.task(':verifyNoTags').outcome == TaskOutcome.SUCCESS
    }

    def "SuccessStepExecutor hasSaveConfigured returns correct values"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.SuccessStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.spec.SaveSpec

            task verifyHasSaveConfigured {
                doLast {
                    def executor = new SuccessStepExecutor()

                    // Empty spec - no save
                    def emptySpec = project.objects.newInstance(SuccessStepSpec)
                    assert !executor.hasSaveConfigured(emptySpec) : "Empty spec should have no save"

                    // Spec with save configured
                    def withSaveSpec = project.objects.newInstance(SuccessStepSpec)
                    def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
                    withSaveSpec.save.set(saveSpec)
                    assert executor.hasSaveConfigured(withSaveSpec) : "Spec with save should return true"

                    println "hasSaveConfigured check verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyHasSaveConfigured')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('hasSaveConfigured check verified!')
        result.task(':verifyHasSaveConfigured').outcome == TaskOutcome.SUCCESS
    }

    def "SuccessStepExecutor executeSaveOperation throws when no built image"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.SuccessStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.spec.SaveSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import org.gradle.api.GradleException

            task verifyNoBuiltImage {
                doLast {
                    def executor = new SuccessStepExecutor()

                    def spec = project.objects.newInstance(SuccessStepSpec)
                    def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)
                    spec.save.set(saveSpec)

                    def context = PipelineContext.create('testPipeline')

                    try {
                        executor.executeSaveOperation(spec, context)
                        throw new AssertionError('Should have thrown exception')
                    } catch (GradleException e) {
                        assert e.message.contains('no built image')
                    }

                    println "No built image validation verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyNoBuiltImage')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('No built image validation verified!')
        result.task(':verifyNoBuiltImage').outcome == TaskOutcome.SUCCESS
    }

    def "SaveSpec defaults work correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.SaveSpec
            import com.kineticfire.gradle.docker.model.SaveCompression

            task verifySaveSpecDefaults {
                doLast {
                    def saveSpec = project.objects.newInstance(SaveSpec, project.objects, project.layout)

                    // Compression defaults to NONE
                    def compression = saveSpec.compression.getOrElse(SaveCompression.NONE)
                    assert compression == SaveCompression.NONE : "Default compression should be NONE"

                    // Output file has a convention
                    assert saveSpec.outputFile.isPresent() : "Output file should have convention"
                    assert saveSpec.outputFile.get().asFile.path.contains('docker-images/image.tar')

                    println "SaveSpec defaults verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifySaveSpecDefaults')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('SaveSpec defaults verified!')
        result.task(':verifySaveSpecDefaults').outcome == TaskOutcome.SUCCESS
    }

    /**
     * Get the plugin classpath for TestKit
     */
    private List<File> getPluginClasspath() {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .collect { new File(it) }
    }
}
