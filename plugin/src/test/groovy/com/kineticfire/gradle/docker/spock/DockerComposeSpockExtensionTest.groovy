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
        e.message.contains('Stack name not configured')
    }

    def "visitSpecAnnotation should validate composeFile is not empty"() {
        given:
        def annotation = createAnnotation(stackName: 'test', composeFile: '', composeFiles: [] as String[])

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Compose file(s) not configured')
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

    def "sanitizeProjectName should handle string with only underscores"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('___')

        then:
        // Underscores are preserved but start is non-alphanumeric so prefix is added
        result == 'test-___'
    }

    def "sanitizeProjectName should handle mixed leading characters"() {
        when:
        def result = DockerComposeSpockExtension.sanitizeProjectName('123abc')

        then:
        result == '123abc'
    }

    // Tests for system property configuration

    def "visitSpecAnnotation should use system property for stackName"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'sysPropStack'
            getProperty('docker.compose.files', _) >> 'compose-from-sysprop.yml'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
        1 * mockSpec.addSetupSpecInterceptor(_)
    }

    def "visitSpecAnnotation should detect conflict when stackName in both system property and annotation"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'sysPropStack'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: 'annotationStack', composeFile: 'compose.yml')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('Configuration conflict for Stack name')
    }

    def "visitSpecAnnotation should detect conflict when compose files in both system property and annotation"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'sysprop-compose.yml'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: 'annotation-compose.yml')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('Configuration conflict for compose files')
    }

    def "visitSpecAnnotation should detect conflict when compose files array in annotation and system property set"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'sysprop-compose.yml'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '', composeFiles: ['file1.yml', 'file2.yml'] as String[])

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('Configuration conflict for compose files')
    }

    def "visitSpecAnnotation should use composeFiles array from annotation"() {
        given:
        def annotation = createAnnotation(
            stackName: 'testStack',
            composeFile: '',
            composeFiles: ['compose1.yml', 'compose2.yml'] as String[]
        )

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
        1 * mockSpec.addSetupSpecInterceptor(_)
    }

    def "visitSpecAnnotation should parse comma-separated compose files from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'file1.yml, file2.yml, file3.yml'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should throw exception for invalid lifecycle in system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.lifecycle', _) >> 'invalid'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid lifecycle mode in system property")
    }

    def "visitSpecAnnotation should use lifecycle CLASS from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.lifecycle', _) >> 'class'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
        1 * mockSpec.addSetupSpecInterceptor(_)
    }

    def "visitSpecAnnotation should use lifecycle METHOD from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.lifecycle', _) >> 'method'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
        1 * mockSpec.addSetupInterceptor(_)
    }

    def "visitSpecAnnotation should throw exception for invalid timeout in system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.timeoutSeconds', _) >> 'notanumber'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid timeout value in system property")
    }

    def "visitSpecAnnotation should use timeout from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.timeoutSeconds', _) >> '120'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should use running timeout from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.timeoutSeconds', _) >> ''
            getProperty('docker.compose.waitForRunning.timeoutSeconds', _) >> '90'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should throw exception for invalid poll seconds in system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.pollSeconds', _) >> 'notanumber'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid poll value in system property")
    }

    def "visitSpecAnnotation should use poll seconds from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.pollSeconds', _) >> '5'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should use running poll seconds from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.pollSeconds', _) >> ''
            getProperty('docker.compose.waitForRunning.pollSeconds', _) >> '3'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should detect conflict when waitForHealthy in both system property and annotation"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.services', _) >> 'web,db'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '', waitForHealthy: ['api'] as String[])

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('Configuration conflict for waitForHealthy')
    }

    def "visitSpecAnnotation should detect conflict when waitForRunning in both system property and annotation"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForRunning.services', _) >> 'redis,cache'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '', waitForRunning: ['queue'] as String[])

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('Configuration conflict for waitForRunning')
    }

    def "visitSpecAnnotation should use waitForHealthy from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.services', _) >> 'web, db, api'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should use waitForRunning from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForRunning.services', _) >> 'redis, queue'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should use projectName from system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.projectName', _) >> 'customProject'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should handle empty spec name"() {
        given:
        mockSpec = Stub(SpecInfo) {
            getName() >> ''
        }
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    // Edge case tests for Groovy truthiness branches

    def "visitSpecAnnotation should throw for null annotation stackName"() {
        given:
        def annotation = createAnnotation(stackName: null, composeFile: 'compose.yml')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Stack name not configured')
    }

    def "visitSpecAnnotation should handle null annotation composeFile"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: null)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should handle null waitForHealthy array"() {
        given:
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml', waitForHealthy: null)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should handle null waitForRunning array"() {
        given:
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml', waitForRunning: null)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should handle null system property returns"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty(_, _) >> null  // All properties return null
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: 'testStack', composeFile: 'compose.yml')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should handle empty composeFiles array from annotation"() {
        given:
        def annotation = createAnnotation(
            stackName: 'testStack',
            composeFile: 'single.yml',
            composeFiles: [] as String[]
        )

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should use annotation lifecycle when system property is null"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.lifecycle', _) >> null
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml', lifecycle: LifecycleMode.METHOD)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        1 * mockSpec.addSetupInterceptor(_)
    }

    def "visitSpecAnnotation should use annotation timeout when system property is null"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.waitForHealthy.timeoutSeconds', _) >> null
            getProperty('docker.compose.waitForRunning.timeoutSeconds', _) >> null
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml', timeoutSeconds: 120)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should use annotation poll when system property is null"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.waitForHealthy.pollSeconds', _) >> null
            getProperty('docker.compose.waitForRunning.pollSeconds', _) >> null
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: 'test', composeFile: 'compose.yml', pollSeconds: 5)

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should throw for invalid running timeout in system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.timeoutSeconds', _) >> ''
            getProperty('docker.compose.waitForRunning.timeoutSeconds', _) >> 'invalid'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Invalid timeout value')
    }

    def "visitSpecAnnotation should throw for invalid running poll in system property"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> 'compose.yml'
            getProperty('docker.compose.waitForHealthy.pollSeconds', _) >> ''
            getProperty('docker.compose.waitForRunning.pollSeconds', _) >> 'invalid'
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '')

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Invalid poll value')
    }

    def "visitSpecAnnotation should handle null composeFiles in annotation"() {
        given:
        def annotation = createAnnotation(
            stackName: 'testStack',
            composeFile: 'single.yml',
            composeFiles: null
        )

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        noExceptionThrown()
    }

    def "visitSpecAnnotation should throw when missing both composeFile and compose files system prop"() {
        given:
        mockSystemPropertyService = Stub(SystemPropertyService) {
            getProperty('docker.compose.stack', _) >> 'testStack'
            getProperty('docker.compose.files', _) >> ''
            getProperty(_, _) >> { String key, String defaultValue -> defaultValue }
        }
        extension = new DockerComposeSpockExtension(
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
        def annotation = createAnnotation(stackName: '', composeFile: '', composeFiles: [] as String[])

        when:
        extension.visitSpecAnnotation(annotation, mockSpec)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Compose file(s) not configured')
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
