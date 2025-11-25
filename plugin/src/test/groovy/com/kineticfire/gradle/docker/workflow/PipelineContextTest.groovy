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

package com.kineticfire.gradle.docker.workflow

import com.kineticfire.gradle.docker.spec.ImageSpec
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for PipelineContext
 */
class PipelineContextTest extends Specification {

    Project project
    ImageSpec imageSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        imageSpec = project.objects.newInstance(
            ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
    }

    // ===== FACTORY METHOD TESTS =====

    def "create() creates context with pipeline name"() {
        when:
        def context = PipelineContext.create('myPipeline')

        then:
        context.pipelineName == 'myPipeline'
        context.builtImage == null
        context.testResult == null
        context.metadata.isEmpty()
        context.appliedTags.isEmpty()
        !context.buildCompleted
        !context.testCompleted
    }

    def "create() creates context with different pipeline names"() {
        expect:
        PipelineContext.create(name).pipelineName == name

        where:
        name << ['pipeline1', 'test-pipeline', 'myApp', 'ci-cd-pipeline']
    }

    // ===== BUILDER TESTS =====

    def "builder creates context with all properties"() {
        given:
        def testResult = new TestResult(true, 10, 0, 0, 0, 10)

        when:
        def context = new PipelineContext.Builder('myPipeline')
            .builtImage(imageSpec)
            .testResult(testResult)
            .addMetadata('key1', 'value1')
            .addAppliedTag('v1.0')
            .buildCompleted(true)
            .testCompleted(true)
            .build()

        then:
        context.pipelineName == 'myPipeline'
        context.builtImage == imageSpec
        context.testResult == testResult
        context.metadata == ['key1': 'value1']
        context.appliedTags == ['v1.0']
        context.buildCompleted
        context.testCompleted
    }

    def "builder can set metadata map"() {
        when:
        def context = new PipelineContext.Builder('test')
            .metadata(['key1': 'value1', 'key2': 'value2'])
            .build()

        then:
        context.metadata.size() == 2
        context.metadata['key1'] == 'value1'
        context.metadata['key2'] == 'value2'
    }

    def "builder can set applied tags list"() {
        when:
        def context = new PipelineContext.Builder('test')
            .appliedTags(['v1.0', 'latest', 'stable'])
            .build()

        then:
        context.appliedTags == ['v1.0', 'latest', 'stable']
    }

    def "builder copy constructor copies all properties"() {
        given:
        def testResult = new TestResult(true, 5, 0, 0, 0, 5)
        def original = new PipelineContext.Builder('original')
            .builtImage(imageSpec)
            .testResult(testResult)
            .addMetadata('key', 'value')
            .addAppliedTag('tag1')
            .buildCompleted(true)
            .testCompleted(true)
            .build()

        when:
        def copied = new PipelineContext.Builder(original).build()

        then:
        copied.pipelineName == original.pipelineName
        copied.builtImage == original.builtImage
        copied.testResult == original.testResult
        copied.metadata == original.metadata
        copied.appliedTags == original.appliedTags
        copied.buildCompleted == original.buildCompleted
        copied.testCompleted == original.testCompleted
    }

    // ===== IMMUTABILITY TESTS =====

    def "withBuiltImage returns new context"() {
        given:
        def context = PipelineContext.create('test')

        when:
        def newContext = context.withBuiltImage(imageSpec)

        then:
        newContext !== context
        newContext.builtImage == imageSpec
        newContext.buildCompleted
        context.builtImage == null
        !context.buildCompleted
    }

    def "withTestResult returns new context"() {
        given:
        def context = PipelineContext.create('test')
        def testResult = new TestResult(true, 10, 0, 0, 0, 10)

        when:
        def newContext = context.withTestResult(testResult)

        then:
        newContext !== context
        newContext.testResult == testResult
        newContext.testCompleted
        context.testResult == null
        !context.testCompleted
    }

    def "withMetadata returns new context"() {
        given:
        def context = PipelineContext.create('test')

        when:
        def newContext = context.withMetadata('key', 'value')

        then:
        newContext !== context
        newContext.metadata['key'] == 'value'
        context.metadata.isEmpty()
    }

    def "withMetadata preserves existing metadata"() {
        given:
        def context = PipelineContext.create('test')
            .withMetadata('key1', 'value1')

        when:
        def newContext = context.withMetadata('key2', 'value2')

        then:
        newContext.metadata['key1'] == 'value1'
        newContext.metadata['key2'] == 'value2'
    }

    def "withAppliedTag returns new context"() {
        given:
        def context = PipelineContext.create('test')

        when:
        def newContext = context.withAppliedTag('v1.0')

        then:
        newContext !== context
        newContext.appliedTags == ['v1.0']
        context.appliedTags.isEmpty()
    }

    def "withAppliedTag preserves existing tags"() {
        given:
        def context = PipelineContext.create('test')
            .withAppliedTag('v1.0')

        when:
        def newContext = context.withAppliedTag('latest')

        then:
        newContext.appliedTags == ['v1.0', 'latest']
    }

    def "withAppliedTags adds multiple tags"() {
        given:
        def context = PipelineContext.create('test')
            .withAppliedTag('v1.0')

        when:
        def newContext = context.withAppliedTags(['latest', 'stable'])

        then:
        newContext.appliedTags == ['v1.0', 'latest', 'stable']
    }

    def "metadata map is unmodifiable"() {
        given:
        def context = PipelineContext.create('test')
            .withMetadata('key', 'value')

        when:
        context.metadata.put('newKey', 'newValue')

        then:
        thrown(UnsupportedOperationException)
    }

    def "applied tags list is unmodifiable"() {
        given:
        def context = PipelineContext.create('test')
            .withAppliedTag('v1.0')

        when:
        context.appliedTags.add('newTag')

        then:
        thrown(UnsupportedOperationException)
    }

    // ===== HELPER METHOD TESTS =====

    def "getMetadataValue returns value for existing key"() {
        given:
        def context = PipelineContext.create('test')
            .withMetadata('key', 'value')

        expect:
        context.getMetadataValue('key') == 'value'
    }

    def "getMetadataValue returns null for non-existing key"() {
        given:
        def context = PipelineContext.create('test')

        expect:
        context.getMetadataValue('nonExistent') == null
    }

    def "isBuildSuccessful returns true when build completed with image"() {
        given:
        def context = PipelineContext.create('test')
            .withBuiltImage(imageSpec)

        expect:
        context.isBuildSuccessful()
    }

    def "isBuildSuccessful returns false when build not completed"() {
        given:
        def context = PipelineContext.create('test')

        expect:
        !context.isBuildSuccessful()
    }

    def "isBuildSuccessful returns false when image is null"() {
        given:
        def context = new PipelineContext.Builder('test')
            .buildCompleted(true)
            .build()

        expect:
        !context.isBuildSuccessful()
    }

    def "isTestSuccessful returns true when tests passed"() {
        given:
        def testResult = new TestResult(true, 10, 0, 0, 0, 10)
        def context = PipelineContext.create('test')
            .withTestResult(testResult)

        expect:
        context.isTestSuccessful()
    }

    def "isTestSuccessful returns false when tests failed"() {
        given:
        def testResult = new TestResult(false, 8, 0, 0, 2, 10)
        def context = PipelineContext.create('test')
            .withTestResult(testResult)

        expect:
        !context.isTestSuccessful()
    }

    def "isTestSuccessful returns false when test not completed"() {
        given:
        def context = PipelineContext.create('test')

        expect:
        !context.isTestSuccessful()
    }

    def "isTestSuccessful returns false when test result is null"() {
        given:
        def context = new PipelineContext.Builder('test')
            .testCompleted(true)
            .build()

        expect:
        !context.isTestSuccessful()
    }

    // ===== TOBUILDER TESTS =====

    def "toBuilder creates modifiable builder"() {
        given:
        def context = PipelineContext.create('test')
            .withBuiltImage(imageSpec)
            .withMetadata('key', 'value')

        when:
        def newContext = context.toBuilder()
            .addMetadata('key2', 'value2')
            .build()

        then:
        newContext.metadata.size() == 2
        context.metadata.size() == 1
    }

    // ===== SERIALIZATION TESTS =====

    def "PipelineContext is serializable"() {
        given:
        def testResult = new TestResult(true, 10, 0, 0, 0, 10)
        def context = new PipelineContext.Builder('myPipeline')
            .testResult(testResult)
            .addMetadata('key', 'value')
            .addAppliedTag('v1.0')
            .buildCompleted(true)
            .testCompleted(true)
            .build()

        when:
        def baos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(baos)
        oos.writeObject(context)
        oos.close()

        def bais = new ByteArrayInputStream(baos.toByteArray())
        def ois = new ObjectInputStream(bais)
        def deserialized = ois.readObject() as PipelineContext
        ois.close()

        then:
        deserialized.pipelineName == context.pipelineName
        deserialized.testResult.success == context.testResult.success
        deserialized.metadata == context.metadata
        deserialized.appliedTags == context.appliedTags
        deserialized.buildCompleted == context.buildCompleted
        deserialized.testCompleted == context.testCompleted
    }

    // ===== TOSTRING TESTS =====

    def "toString includes key information"() {
        given:
        def context = PipelineContext.create('myPipeline')
            .withAppliedTag('v1.0')
            .withBuiltImage(imageSpec)

        when:
        def string = context.toString()

        then:
        string.contains('myPipeline')
        string.contains('buildCompleted=true')
        string.contains('appliedTags=1')
    }

    // ===== CHAINING TESTS =====

    def "methods can be chained"() {
        when:
        def testResult = new TestResult(true, 10, 0, 0, 0, 10)
        def context = PipelineContext.create('pipeline')
            .withBuiltImage(imageSpec)
            .withTestResult(testResult)
            .withMetadata('env', 'prod')
            .withAppliedTag('v1.0')
            .withAppliedTags(['latest', 'stable'])

        then:
        context.pipelineName == 'pipeline'
        context.buildCompleted
        context.testCompleted
        context.metadata['env'] == 'prod'
        context.appliedTags == ['v1.0', 'latest', 'stable']
    }

    // ===== EDGE CASES =====

    def "metadata can store various object types"() {
        when:
        def context = PipelineContext.create('test')
            .withMetadata('string', 'value')
            .withMetadata('number', 42)
            .withMetadata('list', [1, 2, 3])
            .withMetadata('map', [a: 1, b: 2])

        then:
        context.getMetadataValue('string') == 'value'
        context.getMetadataValue('number') == 42
        context.getMetadataValue('list') == [1, 2, 3]
        context.getMetadataValue('map') == [a: 1, b: 2]
    }

    def "empty pipeline name is allowed"() {
        when:
        def context = PipelineContext.create('')

        then:
        context.pipelineName == ''
    }

    def "withAppliedTags with empty list"() {
        given:
        def context = PipelineContext.create('test')
            .withAppliedTag('v1.0')

        when:
        def newContext = context.withAppliedTags([])

        then:
        newContext.appliedTags == ['v1.0']
    }
}
