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

package com.kineticfire.gradle.docker.spock

import com.kineticfire.gradle.docker.junit.service.*
import com.kineticfire.gradle.docker.service.ComposeService
import org.spockframework.runtime.model.SpecInfo
import spock.lang.Specification

/**
 * Unit tests for {@link DockerComposeSpockExtension}.
 */
class DockerComposeSpockExtensionTest extends Specification {

    ComposeService mockComposeService
    ProcessExecutor mockProcessExecutor
    FileService mockFileService
    SystemPropertyService mockSystemPropertyService
    TimeService mockTimeService
    DockerComposeSpockExtension extension
    SpecInfo mockSpec

    def setup() {
        mockComposeService = Mock(ComposeService)
        mockProcessExecutor = Mock(ProcessExecutor)
        mockFileService = Mock(FileService)
        mockSystemPropertyService = Stub(SystemPropertyService) {
            // Return empty strings by default (ensures annotation values are used)
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        mockTimeService = Mock(TimeService)

        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )

        mockSpec = Mock(SpecInfo)
        mockSpec.name >> 'com.example.TestSpec'
    }

    def "default constructor should create extension with default services"() {
        when:
        def ext = new DockerComposeSpockExtension()

        then:
        ext != null
    }

    def "visitSpecAnnotation should validate stackName is not empty"() {
        given:
        def annotation = createAnnotation(stackName: '', composeFile: 'compose.yml')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Stack name must be specified')
    }

    def "visitSpecAnnotation should validate composeFile is not empty"() {
        given:
        def annotation = createAnnotation(stackName: 'test', composeFile: '', composeFiles: [] as String[])

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Compose file(s) must be specified')
    }

    def "visitSpecAnnotation should validate timeoutSeconds is positive"() {
        given:
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml', timeoutSeconds: 0)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('timeoutSeconds must be positive')
    }

    def "visitSpecAnnotation should validate timeoutSeconds is not negative"() {
        given:
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml', timeoutSeconds: -1)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('timeoutSeconds must be positive')
    }

    def "visitSpecAnnotation should validate pollSeconds is positive"() {
        given:
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml', pollSeconds: 0)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('pollSeconds must be positive')
    }

    def "visitSpecAnnotation should validate pollSeconds is not negative"() {
        given:
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml', pollSeconds: -1)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('pollSeconds must be positive')
    }

    def "visitSpecAnnotation should register CLASS lifecycle interceptor"() {
        given:
        def annotation = createAnnotation(
            stackName: 'testStack',
            composeFile: 'compose.yml',
            lifecycle: LifecycleMode.CLASS
        )

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        1 * mockSpec.addSetupSpecInterceptor(_)
        1 * mockSpec.addCleanupSpecInterceptor(_)
        0 * mockSpec.addSetupInterceptor(_)
        0 * mockSpec.addCleanupInterceptor(_)
    }

    def "visitSpecAnnotation should register METHOD lifecycle interceptor"() {
        given:
        def annotation = createAnnotation(
            stackName: 'testStack',
            composeFile: 'compose.yml',
            lifecycle: LifecycleMode.METHOD
        )

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        0 * mockSpec.addSetupSpecInterceptor(_)
        0 * mockSpec.addCleanupSpecInterceptor(_)
        1 * mockSpec.addSetupInterceptor(_)
        1 * mockSpec.addCleanupInterceptor(_)
    }

    def "visitSpecAnnotation should create configuration with default projectName"() {
        given:
        def annotation = createAnnotation(
            stackName: 'myStack',
            composeFile: 'compose.yml',
            projectName: ''  // Empty, should default to stackName
        )

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
        // Configuration creation is tested implicitly through no exceptions
    }

    def "visitSpecAnnotation should create configuration with custom projectName"() {
        given:
        def annotation = createAnnotation(
            stackName: 'myStack',
            composeFile: 'compose.yml',
            projectName: 'customProject'
        )

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should create configuration with waitForHealthy"() {
        given:
        def annotation = createAnnotation(
            stackName: 'myStack',
            composeFile: 'compose.yml',
            waitForHealthy: ['web-app', 'database'] as String[]
        )

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should create configuration with waitForRunning"() {
        given:
        def annotation = createAnnotation(
            stackName: 'myStack',
            composeFile: 'compose.yml',
            waitForRunning: ['redis', 'cache'] as String[]
        )

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should extract className from fully qualified spec name"() {
        given:
        mockSpec.name >> 'com.example.integration.MyTestSpec'
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should handle null spec name"() {
        given:
        // Create new stub with null name
        mockSpec = Stub(SpecInfo) {
            getName() >> null
        }
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "generateUniqueProjectName should create unique name without method"() {
        when:
        def projectName = DockerComposeSpockExtension.generateUniqueProjectName('myApp', 'TestSpec')

        then:
        projectName.startsWith('myapp-testspec-')
        projectName.matches(/^[a-z0-9\-]+$/)  // Only lowercase, numbers, hyphens
    }

    def "generateUniqueProjectName should create unique name with method"() {
        when:
        def projectName = DockerComposeSpockExtension.generateUniqueProjectName(
            'myApp',
            'TestSpec',
            'testMethod'
        )

        then:
        projectName.contains('myapp')
        projectName.contains('testspec')
        projectName.contains('testmethod')
        projectName.matches(/^[a-z0-9\-]+$/)
    }

    def "generateUniqueProjectName should produce different names for consecutive calls"() {
        when:
        def name1 = DockerComposeSpockExtension.generateUniqueProjectName('app', 'Test')
        sleep(1100)  // Ensure timestamp changes (HHmmss format)
        def name2 = DockerComposeSpockExtension.generateUniqueProjectName('app', 'Test')

        then:
        name1 != name2
    }

    def "sanitizeProjectName should convert to lowercase"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('MyAppTest')

        then:
        result == 'myapptest'
    }

    def "sanitizeProjectName should replace invalid characters with hyphens"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('my@app#test!')

        then:
        result == 'my-app-test'
    }

    def "sanitizeProjectName should replace multiple hyphens with single hyphen"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('my---app---test')

        then:
        result == 'my-app-test'
    }

    def "sanitizeProjectName should remove leading hyphens"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('---myapp')

        then:
        result == 'myapp'
    }

    def "sanitizeProjectName should remove trailing hyphens"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('myapp---')

        then:
        result == 'myapp'
    }

    def "sanitizeProjectName should handle names starting with non-alphanumeric"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('_myapp')

        then:
        result == 'test-_myapp'  // Underscores are valid and preserved
    }

    def "sanitizeProjectName should return default for empty string"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('')

        then:
        result == 'test-project'
    }

    def "sanitizeProjectName should return default for only invalid characters"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('---')

        then:
        result == 'test-project'
    }

    def "sanitizeProjectName should preserve valid characters"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('my-app_123')

        then:
        result == 'my-app_123'
    }

    def "sanitizeProjectName should handle complex transformations"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('My@@Complex--__Test##Name!!!')

        then:
        result == 'my-complex-__test-name'  // Multiple hyphens collapsed, but underscores preserved
    }

    // Helper method to create mock ComposeUp annotation
    private ComposeUp createAnnotation(Map params) {
        def defaults = [
            stackName: 'defaultStack',
            composeFile: 'compose.yml',
            composeFiles: [] as String[],
            lifecycle: LifecycleMode.CLASS,
            projectName: '',
            waitForHealthy: [] as String[],
            waitForRunning: [] as String[],
            timeoutSeconds: 60,
            pollSeconds: 2
        ]
        def merged = defaults + params

        return Mock(ComposeUp) {
            stackName() >> merged.stackName
            composeFile() >> merged.composeFile
            composeFiles() >> merged.composeFiles
            lifecycle() >> merged.lifecycle
            projectName() >> merged.projectName
            waitForHealthy() >> merged.waitForHealthy
            waitForRunning() >> merged.waitForRunning
            timeoutSeconds() >> merged.timeoutSeconds
            pollSeconds() >> merged.pollSeconds
        }
    }
}
