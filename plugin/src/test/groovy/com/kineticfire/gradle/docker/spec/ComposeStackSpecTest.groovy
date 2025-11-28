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

package com.kineticfire.gradle.docker.spec

import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ComposeStackSpec
 */
class ComposeStackSpecTest extends Specification {

    def project
    def composeStack

    def setup() {
        project = ProjectBuilder.builder().build()
        composeStack = project.objects.newInstance(ComposeStackSpec, 'testStack')
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with name and project"() {
        expect:
        composeStack != null
        composeStack.name == 'testStack'
    }

    def "constructor with different names"() {
        given:
        def stack1 = project.objects.newInstance(ComposeStackSpec, 'web')
        def stack2 = project.objects.newInstance(ComposeStackSpec, 'database')

        expect:
        stack1.name == 'web'
        stack2.name == 'database'
    }

    // ===== BASIC PROPERTY TESTS =====

    def "composeFile property works correctly"() {
        given:
        def composeFile = project.file('docker-compose.yml')

        when:
        composeStack.composeFile.set(composeFile)

        then:
        composeStack.composeFile.present
        composeStack.composeFile.get().asFile == composeFile
    }

    def "envFile property works correctly"() {
        given:
        def envFile = project.file('.env')

        when:
        composeStack.envFile.set(envFile)

        then:
        composeStack.envFile.present
        composeStack.envFile.get().asFile == envFile
    }

    def "profiles property works correctly"() {
        when:
        composeStack.profiles.set(['dev', 'debug'])

        then:
        composeStack.profiles.present
        composeStack.profiles.get() == ['dev', 'debug']
    }

    def "services property works correctly"() {
        when:
        composeStack.services.set(['web', 'db', 'redis'])

        then:
        composeStack.services.present
        composeStack.services.get() == ['web', 'db', 'redis']
    }

    def "environment property works correctly"() {
        when:
        composeStack.environment.set(['ENV': 'test', 'DEBUG': 'true'])

        then:
        composeStack.environment.present
        composeStack.environment.get() == ['ENV': 'test', 'DEBUG': 'true']
    }

    // ===== ORIGINAL PROPERTY TESTS =====

    def "files property works correctly"() {
        given:
        def compose1 = project.file('docker-compose.yml')
        def compose2 = project.file('docker-compose.override.yml')

        when:
        composeStack.files.from(compose1, compose2)

        then:
        composeStack.files.files.size() == 2
        composeStack.files.files.contains(compose1)
        composeStack.files.files.contains(compose2)
    }

    def "envFiles property works correctly"() {
        given:
        def env1 = project.file('.env')
        def env2 = project.file('.env.local')

        when:
        composeStack.envFiles.from(env1, env2)

        then:
        composeStack.envFiles.files.size() == 2
        composeStack.envFiles.files.contains(env1)
        composeStack.envFiles.files.contains(env2)
    }

    def "projectName property works correctly"() {
        when:
        composeStack.projectName.set('my-web-project')

        then:
        composeStack.projectName.present
        composeStack.projectName.get() == 'my-web-project'
    }

    // ===== WAIT FOR RUNNING TESTS =====

    def "waitForRunning(Closure) configures wait spec"() {
        when:
        composeStack.waitForRunning {
            waitForServices = ['web', 'db']
            timeoutSeconds = 120
            pollSeconds = 5
        }

        then:
        composeStack.waitForRunning.present
        composeStack.waitForRunning.get().waitForServices.get() == ['web', 'db']
        composeStack.waitForRunning.get().timeoutSeconds.get() == 120
        composeStack.waitForRunning.get().pollSeconds.get() == 5
    }

    def "waitForRunning(Action) configures wait spec"() {
        when:
        composeStack.waitForRunning(new Action<WaitSpec>() {
            @Override
            void execute(WaitSpec waitSpec) {
                waitSpec.waitForServices.set(['api', 'worker'])
                waitSpec.timeoutSeconds.set(180)
            }
        })

        then:
        composeStack.waitForRunning.present
        composeStack.waitForRunning.get().waitForServices.get() == ['api', 'worker']
        composeStack.waitForRunning.get().timeoutSeconds.get() == 180
        composeStack.waitForRunning.get().pollSeconds.get() == 2 // default
    }

    // ===== WAIT FOR HEALTHY TESTS =====

    def "waitForHealthy(Closure) configures wait spec"() {
        when:
        composeStack.waitForHealthy {
            waitForServices = ['web']
            timeoutSeconds = 300
            pollSeconds = 10
        }

        then:
        composeStack.waitForHealthy.present
        composeStack.waitForHealthy.get().waitForServices.get() == ['web']
        composeStack.waitForHealthy.get().timeoutSeconds.get() == 300
        composeStack.waitForHealthy.get().pollSeconds.get() == 10
    }

    def "waitForHealthy(Action) configures wait spec"() {
        when:
        composeStack.waitForHealthy(new Action<WaitSpec>() {
            @Override
            void execute(WaitSpec waitSpec) {
                waitSpec.waitForServices.set(['database'])
                waitSpec.timeoutSeconds.set(240)
                waitSpec.pollSeconds.set(15)
            }
        })

        then:
        composeStack.waitForHealthy.present
        composeStack.waitForHealthy.get().waitForServices.get() == ['database']
        composeStack.waitForHealthy.get().timeoutSeconds.get() == 240
        composeStack.waitForHealthy.get().pollSeconds.get() == 15
    }

    // ===== LOGS CONFIGURATION TESTS =====

    def "logs(Closure) configures logs spec"() {
        given:
        def logFile = project.file('compose.log')

        when:
        composeStack.logs {
            writeTo = logFile
            tailLines = 500
        }

        then:
        composeStack.logs.present
        composeStack.logs.get().writeTo.get().asFile == logFile
        composeStack.logs.get().tailLines.get() == 500
    }

    def "logs(Action) configures logs spec"() {
        given:
        def logFile = project.file('output.log')

        when:
        composeStack.logs(new Action<LogsSpec>() {
            @Override
            void execute(LogsSpec logsSpec) {
                logsSpec.writeTo.set(logFile)
                logsSpec.tailLines.set(200)
            }
        })

        then:
        composeStack.logs.present
        composeStack.logs.get().writeTo.get().asFile == logFile
        composeStack.logs.get().tailLines.get() == 200
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        def envFile = project.file('.env')
        def logFile = project.file('logs.txt')

        when:
        composeStack.composeFile.set(composeFile)
        composeStack.envFile.set(envFile)
        composeStack.profiles.set(['prod', 'monitoring'])
        composeStack.services.set(['web', 'api', 'db'])
        composeStack.environment.set(['STAGE': 'production', 'LOG_LEVEL': 'info'])
        composeStack.projectName.set('myproject-prod')
        composeStack.waitForRunning {
            waitForServices = ['web', 'api']
            timeoutSeconds = 120
        }
        composeStack.waitForHealthy {
            waitForServices = ['db']
            timeoutSeconds = 180
        }
        composeStack.logs {
            writeTo = logFile
            tailLines = 1000
        }

        then:
        composeStack.name == 'testStack'
        composeStack.composeFile.get().asFile == composeFile
        composeStack.envFile.get().asFile == envFile
        composeStack.profiles.get() == ['prod', 'monitoring']
        composeStack.services.get() == ['web', 'api', 'db']
        composeStack.environment.get() == ['STAGE': 'production', 'LOG_LEVEL': 'info']
        composeStack.projectName.get() == 'myproject-prod'
        composeStack.waitForRunning.present
        composeStack.waitForHealthy.present
        composeStack.logs.present
    }

    // ===== MULTI-FILE PROPERTIES TESTS =====

    def "composeFiles property initialization"() {
        expect:
        composeStack.composeFiles != null
        // Property is initialized but may be empty/present by default in Gradle
    }

    def "composeFiles property getter/setter functionality"() {
        when:
        composeStack.composeFiles.set(['docker-compose.yml', 'docker-compose.override.yml'])

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get() == ['docker-compose.yml', 'docker-compose.override.yml']
    }

    def "composeFileCollection property initialization"() {
        expect:
        composeStack.composeFileCollection != null
        composeStack.composeFileCollection.files.isEmpty()
    }

    def "composeFileCollection property getter/setter functionality"() {
        given:
        def file1 = project.file('docker-compose.yml')
        def file2 = project.file('docker-compose.override.yml')

        when:
        composeStack.composeFileCollection.from(file1, file2)

        then:
        composeStack.composeFileCollection.files.size() == 2
        composeStack.composeFileCollection.files.contains(file1)
        composeStack.composeFileCollection.files.contains(file2)
    }

    def "composeFiles property handles null input"() {
        when:
        composeStack.composeFiles.set((List<String>) null)

        then:
        !composeStack.composeFiles.present
    }

    def "composeFileCollection property handles null input"() {
        when:
        // ConfigurableFileCollection.from() doesn't accept null arrays
        // This is expected behavior - test that it throws appropriate exception
        composeStack.composeFileCollection.from((File[]) null)

        then:
        thrown(NullPointerException)
    }

    // ===== DSL METHODS TESTING =====

    def "composeFiles(String...) with single file"() {
        when:
        composeStack.composeFiles('docker-compose.yml')

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get() == ['docker-compose.yml']
    }

    def "composeFiles(String...) with multiple files"() {
        when:
        composeStack.composeFiles('docker-compose.yml', 'docker-compose.prod.yml', 'docker-compose.test.yml')

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get() == ['docker-compose.yml', 'docker-compose.prod.yml', 'docker-compose.test.yml']
    }

    def "composeFiles(String...) with empty array"() {
        when:
        composeStack.composeFiles()

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get().isEmpty()
    }

    def "composeFiles(String...) with null inputs"() {
        when:
        composeStack.composeFiles((String[]) null)

        then:
        noExceptionThrown()
        // Property remains in its previous state when null is passed
    }

    def "composeFiles(List<String>) with single file list"() {
        when:
        composeStack.composeFiles(['docker-compose.yml'])

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get() == ['docker-compose.yml']
    }

    def "composeFiles(List<String>) with multiple files list"() {
        when:
        composeStack.composeFiles(['docker-compose.yml', 'docker-compose.dev.yml', 'docker-compose.local.yml'])

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get() == ['docker-compose.yml', 'docker-compose.dev.yml', 'docker-compose.local.yml']
    }

    def "composeFiles(List<String>) with empty list"() {
        when:
        composeStack.composeFiles([])

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get().isEmpty()
    }

    def "composeFiles(List<String>) with null list"() {
        when:
        composeStack.composeFiles((List<String>) null)

        then:
        noExceptionThrown()
        // Property remains in its previous state when null is passed
    }

    def "composeFiles(File...) with single file"() {
        given:
        def composeFile = project.file('docker-compose.yml')

        when:
        composeStack.composeFiles(composeFile)

        then:
        composeStack.composeFileCollection.files.size() == 1
        composeStack.composeFileCollection.files.contains(composeFile)
    }

    def "composeFiles(File...) with multiple files"() {
        given:
        def file1 = project.file('docker-compose.yml')
        def file2 = project.file('docker-compose.override.yml')
        def file3 = project.file('docker-compose.prod.yml')

        when:
        composeStack.composeFiles(file1, file2, file3)

        then:
        composeStack.composeFileCollection.files.size() == 3
        composeStack.composeFileCollection.files.contains(file1)
        composeStack.composeFileCollection.files.contains(file2)
        composeStack.composeFileCollection.files.contains(file3)
    }

    def "composeFiles(File...) with empty array"() {
        when:
        composeStack.composeFiles()

        then:
        composeStack.composeFileCollection.files.isEmpty()
    }

    def "composeFiles(File...) with null inputs"() {
        when:
        composeStack.composeFiles((File[]) null)

        then:
        composeStack.composeFileCollection.files.isEmpty()
    }

    // ===== BACKWARD COMPATIBILITY TESTING =====

    def "existing composeFile property still works"() {
        given:
        def composeFile = project.file('docker-compose.yml')

        when:
        composeStack.composeFile.set(composeFile)

        then:
        composeStack.composeFile.present
        composeStack.composeFile.get().asFile == composeFile
    }

    def "single-file and multi-file configurations can coexist"() {
        given:
        def singleFile = project.file('docker-compose.yml')
        def multiFiles = ['docker-compose.override.yml', 'docker-compose.prod.yml']

        when:
        composeStack.composeFile.set(singleFile)
        composeStack.composeFiles.set(multiFiles)

        then:
        composeStack.composeFile.present
        composeStack.composeFile.get().asFile == singleFile
        composeStack.composeFiles.present
        composeStack.composeFiles.get() == multiFiles
    }

    def "both single-file and multi-file configurations maintain independence"() {
        given:
        def singleFile = project.file('docker-compose.yml')
        def fileCollection = [project.file('docker-compose.override.yml')]

        when:
        composeStack.composeFile.set(singleFile)
        composeStack.composeFiles(fileCollection[0])

        then:
        composeStack.composeFile.present
        composeStack.composeFile.get().asFile == singleFile
        composeStack.composeFileCollection.files.size() == 1
        composeStack.composeFileCollection.files.contains(fileCollection[0])
    }

    // ===== INTEGRATION TESTING =====

    def "interaction between different property types"() {
        given:
        def stringFiles = ['docker-compose.yml', 'docker-compose.dev.yml']
        def fileObjects = [project.file('docker-compose.override.yml'), project.file('docker-compose.test.yml')]

        when:
        composeStack.composeFiles.set(stringFiles)
        composeStack.composeFiles(fileObjects[0], fileObjects[1])

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get() == stringFiles
        composeStack.composeFileCollection.files.size() == 2
        composeStack.composeFileCollection.files.containsAll(fileObjects)
    }

    def "file ordering preservation in string methods"() {
        given:
        def orderedFiles = ['base.yml', 'override.yml', 'prod.yml', 'local.yml']

        when:
        composeStack.composeFiles(orderedFiles)

        then:
        composeStack.composeFiles.get() == orderedFiles
        // Verify the order is maintained
        composeStack.composeFiles.get()[0] == 'base.yml'
        composeStack.composeFiles.get()[1] == 'override.yml'
        composeStack.composeFiles.get()[2] == 'prod.yml'
        composeStack.composeFiles.get()[3] == 'local.yml'
    }

    def "conversion between file paths and File objects"() {
        given:
        def stringPath = 'docker-compose.yml'
        def fileObject = project.file(stringPath)

        when:
        composeStack.composeFiles(stringPath)

        then:
        composeStack.composeFiles.get() == [stringPath]

        when:
        composeStack.composeFiles(fileObject)

        then:
        composeStack.composeFileCollection.files.contains(fileObject)
        fileObject.path.endsWith(stringPath)
    }

    // ===== ERROR HANDLING TESTING =====

    def "composeFiles DSL methods handle null arrays gracefully"() {
        when:
        composeStack.composeFiles((String[]) null)
        composeStack.composeFiles((File[]) null)

        then:
        noExceptionThrown()
        // Properties remain in their previous state when null is passed
    }

    def "composeFiles DSL methods handle null list gracefully"() {
        when:
        composeStack.composeFiles((List<String>) null)

        then:
        noExceptionThrown()
        // Property remains in its previous state when null is passed
    }

    def "composeFiles handles empty inputs correctly"() {
        when:
        composeStack.composeFiles()
        composeStack.composeFiles([])

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get().isEmpty()
    }

    def "composeFiles File method can be called multiple times"() {
        given:
        def file1 = project.file('compose1.yml')
        def file2 = project.file('compose2.yml')
        def file3 = project.file('compose3.yml')

        when:
        composeStack.composeFiles(file1)
        composeStack.composeFiles(file2, file3)

        then:
        composeStack.composeFileCollection.files.size() == 3
        composeStack.composeFileCollection.files.containsAll([file1, file2, file3])
    }

    def "properties work correctly with Provider API"() {
        given:
        def filesProvider = project.provider { ['docker-compose.yml', 'docker-compose.override.yml'] }

        when:
        composeStack.composeFiles.set(filesProvider)

        then:
        composeStack.composeFiles.present
        composeStack.composeFiles.get() == ['docker-compose.yml', 'docker-compose.override.yml']
    }

    // ===== EDGE CASES =====

    def "nested specs can be reconfigured"() {
        given:
        def logFile1 = project.file('log1.txt')
        def logFile2 = project.file('log2.txt')

        when:
        composeStack.logs {
            writeTo = logFile1
            tailLines = 100
        }

        then:
        composeStack.logs.get().writeTo.get().asFile == logFile1
        composeStack.logs.get().tailLines.get() == 100

        when:
        composeStack.logs {
            writeTo = logFile2
            tailLines = 500
        }

        then:
        composeStack.logs.get().writeTo.get().asFile == logFile2
        composeStack.logs.get().tailLines.get() == 500
    }

    def "empty lists and maps are supported"() {
        when:
        composeStack.profiles.set([])
        composeStack.services.set([])
        composeStack.environment.set([:])

        then:
        composeStack.profiles.get().isEmpty()
        composeStack.services.get().isEmpty()
        composeStack.environment.get().isEmpty()
    }

    def "multiple DSL method calls can be combined"() {
        given:
        def stringFiles = ['base.yml', 'override.yml']
        def fileObjects = [project.file('prod.yml'), project.file('test.yml')]

        when:
        composeStack.composeFiles(stringFiles)
        composeStack.composeFiles(fileObjects[0], fileObjects[1])

        then:
        composeStack.composeFiles.get() == stringFiles
        composeStack.composeFileCollection.files.size() == 2
        composeStack.composeFileCollection.files.containsAll(fileObjects)
    }

    def "complete multi-file configuration with all new properties"() {
        given:
        def stringFiles = ['docker-compose.yml', 'docker-compose.override.yml']
        def fileObjects = [project.file('docker-compose.prod.yml'), project.file('docker-compose.local.yml')]

        when:
        composeStack.composeFiles.set(stringFiles)
        composeStack.composeFiles(fileObjects[0], fileObjects[1])
        composeStack.profiles.set(['dev', 'test'])
        composeStack.services.set(['web', 'db'])

        then:
        composeStack.composeFiles.get() == stringFiles
        composeStack.composeFileCollection.files.size() == 2
        composeStack.composeFileCollection.files.containsAll(fileObjects)
        composeStack.profiles.get() == ['dev', 'test']
        composeStack.services.get() == ['web', 'db']
    }
}