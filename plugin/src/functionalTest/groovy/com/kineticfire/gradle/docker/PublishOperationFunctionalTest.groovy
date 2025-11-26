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
 * Functional tests for PublishOperationExecutor
 *
 * These tests verify the publish operation workflow through Gradle's task infrastructure.
 * Note: These tests do not call actual Docker operations - they validate DSL,
 * configuration, validation, and executor patterns.
 */
class PublishOperationFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-publish-operation'"
    }

    def "PublishOperationExecutor builds correct source image reference"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec

            task verifySourceReference {
                doLast {
                    def executor = new PublishOperationExecutor()

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.imageName.set('myapp')
                    imageSpec.tags.set(['1.0.0'])

                    def sourceRef = executor.buildSourceImageReference(imageSpec)
                    assert sourceRef == 'myapp:1.0.0' : "Expected 'myapp:1.0.0' but got '\${sourceRef}'"

                    println "Source reference verified: \${sourceRef}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifySourceReference')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Source reference verified: myapp:1.0.0')
        result.task(':verifySourceReference').outcome == TaskOutcome.SUCCESS
    }

    def "PublishOperationExecutor builds source reference with registry and namespace"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec

            task verifyFullReference {
                doLast {
                    def executor = new PublishOperationExecutor()

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

                    def sourceRef = executor.buildSourceImageReference(imageSpec)
                    assert sourceRef == 'ghcr.io/myorg/myapp:v1.2.3'

                    println "Full reference verified: \${sourceRef}"
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

    def "PublishOperationExecutor builds target references"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec
            import com.kineticfire.gradle.docker.spec.PublishTarget

            task verifyTargetReferences {
                doLast {
                    def executor = new PublishOperationExecutor()

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.imageName.set('myapp')
                    imageSpec.tags.set(['1.0.0'])

                    def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
                    target.registry.set('ghcr.io')
                    target.namespace.set('myorg')
                    target.imageName.set('myapp')

                    def targetRefs = executor.buildTargetReferences(target, imageSpec, ['v1.0.0', 'latest'])
                    def targetRefStrings = targetRefs.collect { it.toString() }
                    assert targetRefStrings.size() == 2
                    assert targetRefStrings.contains('ghcr.io/myorg/myapp:v1.0.0')
                    assert targetRefStrings.contains('ghcr.io/myorg/myapp:latest')

                    println "Target references verified: \${targetRefStrings}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyTargetReferences')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Target references verified:')
        result.output.contains('ghcr.io/myorg/myapp:v1.0.0')
        result.task(':verifyTargetReferences').outcome == TaskOutcome.SUCCESS
    }

    def "PublishOperationExecutor resolves publish tags with priority"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec
            import com.kineticfire.gradle.docker.spec.PublishSpec
            import com.kineticfire.gradle.docker.spec.PublishTarget

            task verifyTagPriority {
                doLast {
                    def executor = new PublishOperationExecutor()

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.tags.set(['1.0.0'])

                    def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
                    publishSpec.publishTags.set(['spec-tag'])

                    // Target with its own tags - should use target tags
                    def targetWithTags = project.objects.newInstance(PublishTarget, 'prod', project.objects)
                    targetWithTags.publishTags.set(['target-tag'])
                    def tagsWithTarget = executor.resolvePublishTags(targetWithTags, publishSpec, imageSpec)
                    assert tagsWithTarget == ['target-tag'] : "Expected target tags"

                    // Target without tags - should use spec tags
                    def targetWithoutTags = project.objects.newInstance(PublishTarget, 'prod', project.objects)
                    def tagsFromSpec = executor.resolvePublishTags(targetWithoutTags, publishSpec, imageSpec)
                    assert tagsFromSpec == ['spec-tag'] : "Expected spec tags"

                    println "Tag priority verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyTagPriority')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Tag priority verified!')
        result.task(':verifyTagPriority').outcome == TaskOutcome.SUCCESS
    }

    def "PublishOperationExecutor validates null PublishSpec"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
            import org.gradle.api.GradleException

            task verifyNullPublishSpec {
                doLast {
                    def executor = new PublishOperationExecutor()

                    try {
                        executor.execute(null, null, null)
                        throw new AssertionError('Should have thrown exception')
                    } catch (GradleException e) {
                        assert e.message.contains('PublishSpec cannot be null')
                    }

                    println "Null PublishSpec validation verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyNullPublishSpec')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Null PublishSpec validation verified!')
        result.task(':verifyNullPublishSpec').outcome == TaskOutcome.SUCCESS
    }

    def "PublishOperationExecutor validates null ImageSpec"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
            import com.kineticfire.gradle.docker.spec.PublishSpec
            import org.gradle.api.GradleException

            task verifyNullImageSpec {
                doLast {
                    def executor = new PublishOperationExecutor()
                    def publishSpec = project.objects.newInstance(PublishSpec, project.objects)

                    try {
                        executor.execute(publishSpec, null, null)
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

    def "PublishOperationExecutor validates no tags configured"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec
            import org.gradle.api.GradleException

            task verifyNoTags {
                doLast {
                    def executor = new PublishOperationExecutor()

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
                        executor.buildSourceImageReference(imageSpec)
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

    def "SuccessStepExecutor hasPublishConfigured returns correct values"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.SuccessStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.spec.PublishSpec

            task verifyHasPublishConfigured {
                doLast {
                    def executor = new SuccessStepExecutor()

                    // Empty spec - no publish
                    def emptySpec = project.objects.newInstance(SuccessStepSpec)
                    assert !executor.hasPublishConfigured(emptySpec) : "Empty spec should have no publish"

                    // Spec with publish configured
                    def withPublishSpec = project.objects.newInstance(SuccessStepSpec)
                    def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
                    withPublishSpec.publish.set(publishSpec)
                    assert executor.hasPublishConfigured(withPublishSpec) : "Spec with publish should return true"

                    println "hasPublishConfigured check verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyHasPublishConfigured')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('hasPublishConfigured check verified!')
        result.task(':verifyHasPublishConfigured').outcome == TaskOutcome.SUCCESS
    }

    def "SuccessStepExecutor executePublishOperation throws when no built image"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.SuccessStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.spec.PublishSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import org.gradle.api.GradleException

            task verifyNoBuiltImage {
                doLast {
                    def executor = new SuccessStepExecutor()

                    def spec = project.objects.newInstance(SuccessStepSpec)
                    def publishSpec = project.objects.newInstance(PublishSpec, project.objects)
                    spec.publish.set(publishSpec)

                    def context = PipelineContext.create('testPipeline')

                    try {
                        executor.executePublishOperation(spec, context)
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

    def "PublishSpec allows configuring multiple targets"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.PublishSpec

            task verifyMultipleTargets {
                doLast {
                    def publishSpec = project.objects.newInstance(PublishSpec, project.objects)

                    publishSpec.to('staging') {
                        registry.set('staging.registry.io')
                        imageName.set('myapp')
                        publishTags.set(['rc1'])
                    }

                    publishSpec.to('production') {
                        registry.set('prod.registry.io')
                        imageName.set('myapp')
                        publishTags.set(['1.0.0', 'latest'])
                    }

                    assert publishSpec.to.size() == 2
                    assert publishSpec.to.findByName('staging')?.registry?.get() == 'staging.registry.io'
                    assert publishSpec.to.findByName('production')?.registry?.get() == 'prod.registry.io'

                    println "Multiple targets verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyMultipleTargets')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Multiple targets verified!')
        result.task(':verifyMultipleTargets').outcome == TaskOutcome.SUCCESS
    }

    def "PublishTarget allows configuring authentication"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.PublishSpec
            import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor

            task verifyAuthentication {
                doLast {
                    def publishSpec = project.objects.newInstance(PublishSpec, project.objects)

                    publishSpec.to('prod') {
                        registry.set('ghcr.io')
                        imageName.set('myapp')
                        auth {
                            username.set('myuser')
                            password.set('mypassword')
                        }
                    }

                    def target = publishSpec.to.findByName('prod')
                    assert target.auth.isPresent()

                    def executor = new PublishOperationExecutor()
                    def authConfig = executor.resolveAuth(target)
                    assert authConfig != null
                    assert authConfig.username == 'myuser'
                    assert authConfig.password == 'mypassword'

                    println "Authentication verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyAuthentication')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Authentication verified!')
        result.task(':verifyAuthentication').outcome == TaskOutcome.SUCCESS
    }

    def "PublishOperationExecutor inherits imageName from source when not set"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec
            import com.kineticfire.gradle.docker.spec.PublishTarget

            task verifyInheritance {
                doLast {
                    def executor = new PublishOperationExecutor()

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.imageName.set('sourceapp')
                    imageSpec.tags.set(['1.0.0'])

                    // Target without imageName - should inherit from source
                    def target = project.objects.newInstance(PublishTarget, 'prod', project.objects)
                    target.registry.set('ghcr.io')

                    def targetRefs = executor.buildTargetReferences(target, imageSpec, ['v1.0.0'])
                    assert targetRefs.size() == 1
                    assert targetRefs[0] == 'ghcr.io/sourceapp:v1.0.0'

                    println "ImageName inheritance verified: \${targetRefs[0]}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyInheritance')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('ImageName inheritance verified: ghcr.io/sourceapp:v1.0.0')
        result.task(':verifyInheritance').outcome == TaskOutcome.SUCCESS
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
