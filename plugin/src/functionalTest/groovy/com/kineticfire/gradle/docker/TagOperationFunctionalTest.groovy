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
 * Functional tests for TagOperationExecutor and SuccessStepExecutor
 *
 * These tests verify the tag operation workflow through Gradle's task infrastructure.
 * Note: These tests do not call actual Docker operations - they validate DSL,
 * configuration, validation, and task registration patterns.
 */
class TagOperationFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-tag-operation'"
    }

    def "TagOperationExecutor builds correct source image reference"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.TagOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec

            task verifySourceReference {
                doLast {
                    def executor = new TagOperationExecutor()

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

    def "TagOperationExecutor builds source reference with registry and namespace"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.TagOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec

            task verifyFullReference {
                doLast {
                    def executor = new TagOperationExecutor()

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

    def "TagOperationExecutor builds target image references for additional tags"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.TagOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec

            task verifyTargetReferences {
                doLast {
                    def executor = new TagOperationExecutor()

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.imageName.set('myapp')
                    imageSpec.tags.set(['1.0.0'])

                    def targetRefs = executor.buildTargetImageReferences(imageSpec, ['stable', 'production'])
                    assert targetRefs.size() == 2
                    def targetRefsStrings = targetRefs.collect { it.toString() }
                    assert targetRefsStrings.contains('myapp:stable')
                    assert targetRefsStrings.contains('myapp:production')

                    println "Target references verified: \${targetRefs}"
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
        result.output.contains('myapp:stable')
        result.output.contains('myapp:production')
        result.task(':verifyTargetReferences').outcome == TaskOutcome.SUCCESS
    }

    def "TagOperationExecutor validates null ImageSpec"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.TagOperationExecutor
            import org.gradle.api.GradleException

            task verifyNullValidation {
                doLast {
                    def executor = new TagOperationExecutor()

                    try {
                        executor.execute(null, ['stable'], null)
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
            .withArguments('verifyNullValidation')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Null ImageSpec validation verified!')
        result.task(':verifyNullValidation').outcome == TaskOutcome.SUCCESS
    }

    def "TagOperationExecutor handles empty tags gracefully"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.operation.TagOperationExecutor
            import com.kineticfire.gradle.docker.spec.ImageSpec

            task verifyEmptyTags {
                doLast {
                    def executor = new TagOperationExecutor()

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.imageName.set('myapp')
                    imageSpec.tags.set(['1.0.0'])

                    // Should not throw - just returns early
                    executor.execute(imageSpec, [], null)
                    executor.execute(imageSpec, null, null)

                    println "Empty tags handling verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyEmptyTags')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Empty tags handling verified!')
        result.task(':verifyEmptyTags').outcome == TaskOutcome.SUCCESS
    }

    def "SuccessStepExecutor handles null successSpec"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.SuccessStepExecutor
            import com.kineticfire.gradle.docker.workflow.PipelineContext

            task verifyNullSpec {
                doLast {
                    def executor = new SuccessStepExecutor()
                    def context = PipelineContext.create('testPipeline')

                    def result = executor.execute(null, context)
                    assert result == context

                    println "Null spec handling verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyNullSpec')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Null spec handling verified!')
        result.task(':verifyNullSpec').outcome == TaskOutcome.SUCCESS
    }

    def "SuccessStepExecutor hasAdditionalTags checks correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.SuccessStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec

            task verifyHasAdditionalTags {
                doLast {
                    def executor = new SuccessStepExecutor()

                    // Empty spec - no tags
                    def emptySpec = project.objects.newInstance(SuccessStepSpec)
                    assert !executor.hasAdditionalTags(emptySpec) : "Empty spec should have no tags"

                    // Spec with empty list
                    def emptyListSpec = project.objects.newInstance(SuccessStepSpec)
                    emptyListSpec.additionalTags.set([])
                    assert !executor.hasAdditionalTags(emptyListSpec) : "Empty list should return false"

                    // Spec with tags
                    def withTagsSpec = project.objects.newInstance(SuccessStepSpec)
                    withTagsSpec.additionalTags.set(['stable', 'production'])
                    assert executor.hasAdditionalTags(withTagsSpec) : "Spec with tags should return true"

                    println "hasAdditionalTags check verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyHasAdditionalTags')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('hasAdditionalTags check verified!')
        result.task(':verifyHasAdditionalTags').outcome == TaskOutcome.SUCCESS
    }

    def "SuccessStepExecutor executes afterSuccess hook"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.SuccessStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import org.gradle.api.Action

            task verifyAfterSuccessHook {
                doLast {
                    def executor = new SuccessStepExecutor()
                    def spec = project.objects.newInstance(SuccessStepSpec)
                    def context = PipelineContext.create('testPipeline')

                    def hookCalled = false
                    spec.afterSuccess.set({ hookCalled = true } as Action<Void>)

                    executor.execute(spec, context)
                    assert hookCalled : "afterSuccess hook should have been called"

                    println "afterSuccess hook execution verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyAfterSuccessHook')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('afterSuccess hook execution verified!')
        result.task(':verifyAfterSuccessHook').outcome == TaskOutcome.SUCCESS
    }

    def "SuccessStepExecutor adds tags to context"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.SuccessStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.spec.ImageSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext

            task verifyTagsAddedToContext {
                doLast {
                    def executor = new SuccessStepExecutor()
                    def spec = project.objects.newInstance(SuccessStepSpec)
                    spec.additionalTags.set(['stable', 'production'])

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )
                    imageSpec.imageName.set('myapp')
                    imageSpec.tags.set(['1.0.0'])

                    def context = PipelineContext.create('testPipeline').withBuiltImage(imageSpec)

                    def result = executor.execute(spec, context)
                    assert result.appliedTags.contains('stable')
                    assert result.appliedTags.contains('production')

                    println "Tags added to context verified!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyTagsAddedToContext')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Tags added to context verified!')
        result.task(':verifyTagsAddedToContext').outcome == TaskOutcome.SUCCESS
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
