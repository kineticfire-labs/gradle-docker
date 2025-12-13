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

package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import com.kineticfire.gradle.docker.task.ComposeUpTask
import com.kineticfire.gradle.docker.task.ComposeDownTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

/**
 * Unit tests for DockerTestExtension
 */
class DockerTestExtensionTest extends Specification {

    Project project
    DockerTestExtension extension

    @TempDir
    Path tempDir

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.pluginManager.apply('com.kineticfire.gradle.docker')
        extension = project.extensions.getByType(DockerTestExtension)
    }

    def "extension can be created"() {
        expect:
        extension != null
        extension.composeStacks != null
    }

    def "can configure single compose stack"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        when:
        extension.composeStacks {
            testStack {
                files.from(project.file('docker-compose.yml'))
                profiles = ['test']
            }
        }

        then:
        extension.composeStacks.size() == 1
        extension.composeStacks.getByName('testStack') != null

        and:
        ComposeStackSpec stack = extension.composeStacks.getByName('testStack')
        stack.profiles.get() == ['test']
        stack.projectName.get() == "${project.name}-testStack"
    }

    def "can configure multiple compose stacks"() {
        given:
        project.file('docker-compose.dev.yml').text = 'version: "3.8"'
        project.file('docker-compose.prod.yml').text = 'version: "3.8"'

        when:
        extension.composeStacks {
            development {
                files.from(project.file('docker-compose.dev.yml'))
                profiles = ['dev', 'debug']
            }
            production {
                files.from(project.file('docker-compose.prod.yml'))
                profiles = ['prod']
            }
        }

        then:
        extension.composeStacks.size() == 2
        extension.composeStacks.getByName('development') != null
        extension.composeStacks.getByName('production') != null

        and:
        extension.composeStacks.getByName('development').profiles.get() == ['dev', 'debug']
        extension.composeStacks.getByName('production').profiles.get() == ['prod']
    }

    def "validate passes for valid stack configuration"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        extension.composeStacks {
            validStack {
                files.from(project.file('docker-compose.yml'))
                profiles = ['test']
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate fails when compose file does not exist"() {
        given:
        extension.composeStacks {
            missingFile {
                files.from(project.file('non-existent-compose.yml'))
                profiles = ['test']
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Compose file does not exist")
        ex.message.contains('missingFile')
    }

    def "validate fails when env file does not exist"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        extension.composeStacks {
            missingEnv {
                files.from(project.file('docker-compose.yml'))
                envFiles.from(project.file('non-existent.env'))
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Environment file does not exist")
        ex.message.contains('missingEnv')
    }

    def "can configure with environment variables"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        when:
        extension.composeStacks {
            envStack {
                files.from(project.file('docker-compose.yml'))
                environment = [
                    'ENV': 'test',
                    'DEBUG': 'true'
                ]
            }
        }

        then:
        ComposeStackSpec stack = extension.composeStacks.getByName('envStack')
        stack.environment.get() == [ENV: 'test', DEBUG: 'true']
    }

    def "can configure with services list"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        when:
        extension.composeStacks {
            servicesStack {
                files.from(project.file('docker-compose.yml'))
                services = ['web', 'db']
            }
        }

        then:
        ComposeStackSpec stack = extension.composeStacks.getByName('servicesStack')
        stack.services.get() == ['web', 'db']
    }

    def "can access stacks by name"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        extension.composeStacks {
            stack1 { files.from(project.file('docker-compose.yml')) }
            stack2 { files.from(project.file('docker-compose.yml')) }
        }

        expect:
        extension.composeStacks.getByName('stack1') != null
        extension.composeStacks.getByName('stack2') != null
        extension.composeStacks.getByName('stack1').name == 'stack1'
    }

    def "composeConfigs is alias for composeStacks"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        when:
        extension.composeConfigs {
            aliasTest {
                files.from(project.file('docker-compose.yml'))
                profiles = ['test']
            }
        }

        then:
        extension.composeConfigs.size() == 1
        extension.composeStacks.size() == 1
        extension.composeConfigs.getByName('aliasTest') != null
        extension.composeStacks.getByName('aliasTest') != null
    }

    def "validate handles empty stacks"() {
        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "stack names are preserved"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        when:
        extension.composeStacks {
            'my-test-stack' {
                files.from(project.file('docker-compose.yml'))
            }
        }

        then:
        extension.composeStacks.getByName('my-test-stack').name == 'my-test-stack'
        extension.composeStacks.getByName('my-test-stack').projectName.get() == "${project.name}-my-test-stack"
    }

    // =========================
    // Multi-File Configuration Tests
    // =========================

    def "configures both ComposeUp and ComposeDown tasks with multiple compose files via composeFiles string list"() {
        given:
        def composeFile1 = project.file('compose1.yml')
        def composeFile2 = project.file('compose2.yml')
        [composeFile1, composeFile2].each {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.text = 'version: "3.8"'
        }

        when:
        extension.composeStacks {
            webapp {
                composeFiles('compose1.yml', 'compose2.yml')
            }
        }
        project.evaluate() // Trigger task configuration

        then:
        def upTask = project.tasks.getByName('composeUpWebapp') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownWebapp') as ComposeDownTask

        // Both tasks should have same files in same order
        upTask.composeFiles.files.containsAll([composeFile1, composeFile2])
        downTask.composeFiles.files.containsAll([composeFile1, composeFile2])

        // Verify file order is preserved in both tasks
        def upFiles = upTask.composeFiles.files as List
        def downFiles = downTask.composeFiles.files as List
        upFiles == downFiles  // Same order
        upFiles.size() == 2
        downFiles.size() == 2
    }

    def "configures both ComposeUp and ComposeDown tasks with multiple compose files via File collection"() {
        given:
        def composeFile1 = project.file('docker-compose.base.yml')
        def composeFile2 = project.file('docker-compose.override.yml')
        [composeFile1, composeFile2].each {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.text = 'version: "3.8"'
        }

        when:
        extension.composeStacks {
            production {
                composeFiles(composeFile1, composeFile2)
            }
        }
        project.evaluate() // Trigger task configuration

        then:
        def upTask = project.tasks.getByName('composeUpProduction') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownProduction') as ComposeDownTask

        // Both tasks should have same files in same order
        def upFiles = upTask.composeFiles.files as List
        def downFiles = downTask.composeFiles.files as List
        upFiles.containsAll([composeFile1, composeFile2])
        downFiles.containsAll([composeFile1, composeFile2])
        upFiles == downFiles  // Same order
    }

    def "configures tasks with mixed configuration approaches - composeFiles list takes priority"() {
        given:
        def priorityFile1 = project.file('priority1.yml')
        def priorityFile2 = project.file('priority2.yml')
        def legacyFile = project.file('legacy.yml')
        def originalFile = project.file('original.yml')
        [priorityFile1, priorityFile2, legacyFile, originalFile].each {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.text = 'version: "3.8"'
        }

        when:
        extension.composeStacks {
            mixed {
                // Multiple configuration methods - priority should be honored
                composeFiles('priority1.yml', 'priority2.yml') // Highest priority
                composeFile.set(project.layout.projectDirectory.file('legacy.yml')) // Lower priority
                files.from(originalFile) // Lowest priority
            }
        }
        project.evaluate()

        then:
        def upTask = project.tasks.getByName('composeUpMixed') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownMixed') as ComposeDownTask

        // Should use priority files only
        def upFiles = upTask.composeFiles.files as List
        def downFiles = downTask.composeFiles.files as List
        upFiles.containsAll([priorityFile1, priorityFile2])
        !upFiles.contains(legacyFile)
        !upFiles.contains(originalFile)
        upFiles == downFiles
    }

    def "preserves file order when multiple files are specified"() {
        given:
        def files = (1..5).collect {
            def file = project.file("compose-${it}.yml")
            file.parentFile.mkdirs()
            file.createNewFile()
            file.text = 'version: "3.8"'
            file
        }

        when:
        extension.composeStacks {
            ordered {
                composeFiles(files.collect { it.path })
            }
        }
        project.evaluate()

        then:
        def upTask = project.tasks.getByName('composeUpOrdered') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownOrdered') as ComposeDownTask

        def upFiles = upTask.composeFiles.files as List
        def downFiles = downTask.composeFiles.files as List
        upFiles == files
        downFiles == files
        upFiles == downFiles
    }

    def "supports both string list and File collection composeFiles configurations simultaneously"() {
        given:
        def stringFiles = ['string1.yml', 'string2.yml'].collect { name ->
            def file = project.file(name)
            file.parentFile.mkdirs()
            file.createNewFile()
            file.text = 'version: "3.8"'
            file
        }
        def collectionFiles = ['collection1.yml', 'collection2.yml'].collect { name ->
            def file = project.file(name)
            file.parentFile.mkdirs()
            file.createNewFile()
            file.text = 'version: "3.8"'
            file
        }

        when:
        extension.composeStacks {
            combined {
                composeFiles('string1.yml', 'string2.yml') // String list API
                composeFiles(collectionFiles[0], collectionFiles[1]) // File collection API
            }
        }
        project.evaluate()

        then:
        def upTask = project.tasks.getByName('composeUpCombined') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownCombined') as ComposeDownTask

        def upFiles = upTask.composeFiles.files as List
        def downFiles = downTask.composeFiles.files as List

        // Both string and collection files should be included
        upFiles.containsAll(stringFiles)
        upFiles.containsAll(collectionFiles)
        upFiles == downFiles
    }

    // =========================
    // Backward Compatibility Tests
    // =========================

    def "maintains backward compatibility with single composeFile property"() {
        given:
        def composeFileObj = project.file('docker-compose.yml')
        composeFileObj.parentFile.mkdirs()
        composeFileObj.createNewFile()
        composeFileObj.text = 'version: "3.8"'

        when:
        extension.composeStacks {
            legacy {
                composeFile.set(project.layout.projectDirectory.file('docker-compose.yml'))
            }
        }
        project.evaluate()

        then:
        def upTask = project.tasks.getByName('composeUpLegacy') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownLegacy') as ComposeDownTask

        def upFiles = upTask.composeFiles.files as List
        def downFiles = downTask.composeFiles.files as List
        upFiles == [composeFileObj]
        downFiles == [composeFileObj]
        upFiles == downFiles
    }

    def "maintains backward compatibility with original files property"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        composeFile.text = 'version: "3.8"'

        when:
        extension.composeStacks {
            original {
                files.from(composeFile)
            }
        }
        project.evaluate()

        then:
        def upTask = project.tasks.getByName('composeUpOriginal') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownOriginal') as ComposeDownTask

        def upFiles = upTask.composeFiles.files as List
        def downFiles = downTask.composeFiles.files as List
        upFiles.contains(composeFile)
        downFiles.contains(composeFile)
        upFiles == downFiles
    }

    // =========================
    // ComposeDown Inheritance Tests
    // =========================

    def "ComposeDown automatically inherits exact same files and order as ComposeUp"() {
        given:
        def files = ['base.yml', 'override.yml', 'local.yml'].collect { name ->
            def file = project.file(name)
            file.parentFile.mkdirs()
            file.createNewFile()
            file.text = 'version: "3.8"'
            file
        }

        when:
        extension.composeStacks {
            inheritance {
                composeFiles(files.collect { it.path })
            }
        }
        project.evaluate()

        then:
        def upTask = project.tasks.getByName('composeUpInheritance') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownInheritance') as ComposeDownTask

        def upFiles = upTask.composeFiles.files as List
        def downFiles = downTask.composeFiles.files as List

        // Exact same files in exact same order
        upFiles == downFiles
        upFiles == files
        downFiles == files
    }

    def "multiple stacks have independent ComposeUp/ComposeDown file synchronization"() {
        given:
        def stack1Files = ['stack1-base.yml', 'stack1-override.yml'].collect { name ->
            def file = project.file(name)
            file.parentFile.mkdirs()
            file.createNewFile()
            file.text = 'version: "3.8"'
            file
        }
        def stack2Files = ['stack2-base.yml', 'stack2-dev.yml', 'stack2-test.yml'].collect { name ->
            def file = project.file(name)
            file.parentFile.mkdirs()
            file.createNewFile()
            file.text = 'version: "3.8"'
            file
        }

        when:
        extension.composeStacks {
            stack1 {
                composeFiles(stack1Files.collect { it.path })
            }
            stack2 {
                composeFiles(stack2Files.collect { it.path })
            }
        }
        project.evaluate()

        then:
        def up1Task = project.tasks.getByName('composeUpStack1') as ComposeUpTask
        def down1Task = project.tasks.getByName('composeDownStack1') as ComposeDownTask
        def up2Task = project.tasks.getByName('composeUpStack2') as ComposeUpTask
        def down2Task = project.tasks.getByName('composeDownStack2') as ComposeDownTask

        // Stack 1: Up and Down synchronized
        def up1Files = up1Task.composeFiles.files as List
        def down1Files = down1Task.composeFiles.files as List
        up1Files == down1Files
        up1Files == stack1Files

        // Stack 2: Up and Down synchronized
        def up2Files = up2Task.composeFiles.files as List
        def down2Files = down2Task.composeFiles.files as List
        up2Files == down2Files
        up2Files == stack2Files

        // Stacks are independent
        up1Files != up2Files
        down1Files != down2Files
    }

    // =========================
    // Validation Tests
    // =========================

    def "validateStackSpec validates multi-file configurations"() {
        given:
        def existingFile1 = project.file('existing1.yml')
        def existingFile2 = project.file('existing2.yml')
        [existingFile1, existingFile2].each {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.text = 'version: "3.8"'
        }

        extension.composeStacks {
            validMulti {
                files.from(existingFile1, existingFile2)
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validation fails when multi-file configuration contains non-existent files"() {
        given:
        def existingFile = project.file('existing.yml')
        existingFile.parentFile.mkdirs()
        existingFile.createNewFile()
        existingFile.text = 'version: "3.8"'

        extension.composeStacks {
            invalidMulti {
                files.from(existingFile, project.file('non-existent.yml'))
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Compose file does not exist")
        ex.message.contains('invalidMulti')
        ex.message.contains('non-existent.yml')
    }

    def "validation requires at least one compose file"() {
        given:
        extension.composeStacks {
            noFiles {
                // No files specified
                profiles = ['test']
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("No compose files specified")
        ex.message.contains('noFiles')
    }

    def "validation handles mixed valid and invalid file scenarios"() {
        given:
        def validFile = project.file('valid.yml')
        validFile.parentFile.mkdirs()
        validFile.createNewFile()
        validFile.text = 'version: "3.8"'

        extension.composeStacks {
            mixedScenario {
                files.from(validFile, project.file('invalid.yml'), project.file('another-invalid.yml'))
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Compose file does not exist")
        ex.message.contains('mixedScenario')
        // Should report first invalid file encountered
        ex.message.contains('invalid.yml')
    }

    // =========================
    // Provider API Tests
    // =========================

    def "configuration uses Provider API correctly for composeFiles"() {
        given:
        def composeFile1 = project.file('compose1.yml')
        def composeFile2 = project.file('compose2.yml')
        [composeFile1, composeFile2].each {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.text = 'version: "3.8"'
        }

        when:
        extension.composeStacks {
            providerTest {
                composeFiles('compose1.yml', 'compose2.yml')
            }
        }

        then:
        ComposeStackSpec stack = extension.composeStacks.getByName('providerTest')
        // Verify providers are configured, not direct values
        stack.composeFiles.present
        stack.composeFiles.get() == ['compose1.yml', 'compose2.yml']
    }

    def "configuration uses Provider API correctly for composeFileCollection"() {
        given:
        def composeFile1 = project.file('compose1.yml')
        def composeFile2 = project.file('compose2.yml')
        [composeFile1, composeFile2].each {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.text = 'version: "3.8"'
        }

        when:
        extension.composeStacks {
            collectionTest {
                composeFiles(composeFile1, composeFile2)
            }
        }

        then:
        ComposeStackSpec stack = extension.composeStacks.getByName('collectionTest')
        // Verify collection is configured
        !stack.composeFileCollection.empty
        stack.composeFileCollection.files.containsAll([composeFile1, composeFile2])
    }

    def "provider transformations work correctly during task configuration"() {
        given:
        def composeFile1 = project.file('transform1.yml')
        def composeFile2 = project.file('transform2.yml')
        [composeFile1, composeFile2].each {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.text = 'version: "3.8"'
        }

        when:
        extension.composeStacks {
            transformation {
                composeFiles('transform1.yml', 'transform2.yml')
            }
        }
        project.evaluate() // This triggers provider evaluation

        then:
        def upTask = project.tasks.getByName('composeUpTransformation') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownTransformation') as ComposeDownTask

        // Provider transformations should result in correct File objects
        upTask.composeFiles.files.containsAll([composeFile1, composeFile2])
        downTask.composeFiles.files.containsAll([composeFile1, composeFile2])
    }

    // =========================
    // Error Message Quality Tests
    // =========================

    def "error messages provide clear guidance for configuration issues"() {
        given:
        extension.composeStacks {
            badConfig {
                files.from(project.file('missing1.yml'), project.file('missing2.yml'))
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        with(ex.message) {
            contains("Compose file does not exist")
            contains('badConfig')
            contains('missing1.yml')
            // Should provide helpful context
            contains('Stack') || contains('stack')
        }
    }

    def "error messages distinguish between different validation failures"() {
        given:
        def validFile = project.file('valid.yml')
        validFile.parentFile.mkdirs()
        validFile.createNewFile()
        validFile.text = 'version: "3.8"'

        extension.composeStacks {
            validStack {
                files.from(validFile)
                envFiles.from(project.file('missing.env'))
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Environment file does not exist")
        // Should not confuse with compose file error
        !ex.message.contains("Compose file does not exist")
    }

    // =========================
    // Integration Tests
    // =========================

    def "plugin configuration creates correct ComposeUp and ComposeDown tasks"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        composeFile.text = 'version: "3.8"'

        when:
        extension.composeStacks {
            testStack {
                files.from(composeFile)
            }
        }
        project.evaluate()

        then:
        project.tasks.getByName('composeUpTestStack') != null
        project.tasks.getByName('composeDownTestStack') != null

        def upTask = project.tasks.getByName('composeUpTestStack')
        def downTask = project.tasks.getByName('composeDownTestStack')
        upTask instanceof ComposeUpTask
        downTask instanceof ComposeDownTask
    }

    def "task dependencies are maintained between Up and Down tasks"() {
        given:
        def composeFile = project.file('docker-compose.yml')
        composeFile.parentFile.mkdirs()
        composeFile.createNewFile()
        composeFile.text = 'version: "3.8"'

        when:
        extension.composeStacks {
            dependencyTest {
                files.from(composeFile)
            }
        }
        project.evaluate()

        then:
        def upTask = project.tasks.getByName('composeUpDependencyTest') as ComposeUpTask
        def downTask = project.tasks.getByName('composeDownDependencyTest') as ComposeDownTask

        // Both tasks should be configured with same project and stack names
        upTask.projectName.get().endsWith('dependencyTest')
        downTask.projectName.get().endsWith('dependencyTest')
        upTask.stackName.get() == 'dependencyTest'
        downTask.stackName.get() == 'dependencyTest'
    }

    def "multiple stacks with different configurations work independently"() {
        given:
        def stack1File = project.file('stack1.yml')
        def stack2File = project.file('stack2.yml')
        [stack1File, stack2File].each {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.text = 'version: "3.8"'
        }

        when:
        extension.composeStacks {
            stack1 {
                files.from(stack1File)
                profiles = ['dev']
            }
            stack2 {
                files.from(stack2File)
                profiles = ['prod']
            }
        }
        project.evaluate()

        then:
        project.tasks.getByName('composeUpStack1') != null
        project.tasks.getByName('composeDownStack1') != null
        project.tasks.getByName('composeUpStack2') != null
        project.tasks.getByName('composeDownStack2') != null

        def stack1Spec = extension.composeStacks.getByName('stack1')
        def stack2Spec = extension.composeStacks.getByName('stack2')

        stack1Spec.profiles.get() == ['dev']
        stack2Spec.profiles.get() == ['prod']
    }

    def "ComposeUp and ComposeDown file synchronization works across multiple stacks"() {
        given:
        def stack1Files = ['stack1-base.yml', 'stack1-override.yml'].collect { name ->
            def file = project.file(name)
            file.parentFile.mkdirs()
            file.createNewFile()
            file.text = 'version: "3.8"'
            file
        }
        def stack2Files = ['stack2-base.yml', 'stack2-dev.yml'].collect { name ->
            def file = project.file(name)
            file.parentFile.mkdirs()
            file.createNewFile()
            file.text = 'version: "3.8"'
            file
        }

        when:
        extension.composeStacks {
            multiStack1 {
                files.from(stack1Files)
            }
            multiStack2 {
                files.from(stack2Files)
            }
        }
        project.evaluate()

        then:
        def up1Task = project.tasks.getByName('composeUpMultiStack1') as ComposeUpTask
        def down1Task = project.tasks.getByName('composeDownMultiStack1') as ComposeDownTask
        def up2Task = project.tasks.getByName('composeUpMultiStack2') as ComposeUpTask
        def down2Task = project.tasks.getByName('composeDownMultiStack2') as ComposeDownTask

        // Stack 1 synchronization
        up1Task.composeFiles.files as List == down1Task.composeFiles.files as List
        up1Task.composeFiles.files.containsAll(stack1Files)

        // Stack 2 synchronization
        up2Task.composeFiles.files as List == down2Task.composeFiles.files as List
        up2Task.composeFiles.files.containsAll(stack2Files)

        // Stacks are independent
        up1Task.composeFiles.files != up2Task.composeFiles.files
    }

    // =========================
    // UNIT TEST VALIDATION LOGIC - Multi-File Configuration Tests
    // =========================

    def "validateStackSpec succeeds for valid composeFiles ListProperty configuration"() {
        given:
        def composeFile1 = createTempFile('compose1.yml')
        def composeFile2 = createTempFile('compose2.yml')

        extension.composeStacks {
            webapp {
                composeFiles(composeFile1.absolutePath, composeFile2.absolutePath)
                files.from(composeFile1, composeFile2)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackSpec succeeds for valid composeFileCollection ConfigurableFileCollection configuration"() {
        given:
        def composeFile1 = createTempFile('compose1.yml')
        def composeFile2 = createTempFile('compose2.yml')

        extension.composeStacks {
            webapp {
                composeFiles(composeFile1, composeFile2)
                files.from(composeFile1, composeFile2)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackSpec succeeds for mixed multi-file configuration scenarios"() {
        given:
        def composeFile1 = createTempFile('compose1.yml')
        def composeFile2 = createTempFile('compose2.yml')
        def composeFile3 = createTempFile('compose3.yml')

        extension.composeStacks {
            webapp {
                composeFiles(composeFile1.absolutePath, composeFile2.absolutePath)
                composeFiles(composeFile3)
                files.from(composeFile1, composeFile2, composeFile3)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackSpec handles empty multi-file configuration"() {
        given:
        extension.composeStacks {
            webapp {
                // Configure empty collections - should trigger validation error
                composeFiles([])
                // Don't add any files to the files collection
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        def exception = thrown(GradleException)
        exception.message.contains("No compose files specified")
        exception.message.contains("webapp")
    }

    // =========================
    // Single-File Configuration Validation (Backward Compatibility)
    // =========================

    def "validateStackSpec succeeds for existing single-file composeFile validation"() {
        given:
        def composeFile = createTempFile('docker-compose.yml')

        extension.composeStacks {
            webapp {
                composeFile = composeFile
                files.from(composeFile)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackSpec handles single-file validation with different file types and paths"() {
        given:
        def ymlFile = createTempFile('docker-compose.yml')
        def yamlFile = createTempFile('docker-compose.yaml')

        extension.composeStacks {
            ymlStack {
                composeFile.set(project.layout.projectDirectory.file(ymlFile.name))
                files.from(ymlFile)
            }
            yamlStack {
                composeFile.set(project.layout.projectDirectory.file(yamlFile.name))
                files.from(yamlFile)
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validateStackSpec handles migration scenarios from single-file to multi-file"() {
        given:
        def singleFile = createTempFile('docker-compose.yml')
        def additionalFile = createTempFile('docker-compose.override.yml')

        extension.composeStacks {
            webapp {
                composeFile.set(project.layout.projectDirectory.file(singleFile.name))
                composeFiles(singleFile.absolutePath, additionalFile.absolutePath)
                files.from(singleFile, additionalFile)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
    }

    // =========================
    // Priority Logic Testing
    // =========================

    def "validateStackSpec succeeds when both single-file and multi-file properties are set"() {
        given:
        def singleFile = createTempFile('docker-compose.yml')
        def multiFile1 = createTempFile('compose1.yml')
        def multiFile2 = createTempFile('compose2.yml')

        extension.composeStacks {
            webapp {
                composeFile.set(project.layout.projectDirectory.file(singleFile.name))
                composeFiles(multiFile1.absolutePath, multiFile2.absolutePath)
                files.from(singleFile, multiFile1, multiFile2)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackSpec succeeds when only single-file property is set"() {
        given:
        def composeFile = createTempFile('docker-compose.yml')

        extension.composeStacks {
            webapp {
                composeFile = composeFile
                files.from(composeFile)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackSpec succeeds when only multi-file properties are set"() {
        given:
        def composeFile1 = createTempFile('compose1.yml')
        def composeFile2 = createTempFile('compose2.yml')

        extension.composeStacks {
            webapp {
                composeFiles(composeFile1.absolutePath, composeFile2.absolutePath)
                files.from(composeFile1, composeFile2)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackSpec fails when no properties are set"() {
        given:
        extension.composeStacks {
            webapp {
                // No file configuration at all
                profiles = ['test']
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        def exception = thrown(GradleException)
        exception.message.contains("No compose files specified")
        exception.message.contains("webapp")
    }

    // =========================
    // File Existence Validation
    // =========================

    def "validateStackSpec succeeds with existing compose files"() {
        given:
        def composeFile1 = createTempFile('existing1.yml')
        def composeFile2 = createTempFile('existing2.yml')

        extension.composeStacks {
            webapp {
                files.from(composeFile1, composeFile2)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackSpec fails with missing compose files"() {
        given:
        extension.composeStacks {
            webapp {
                files.from(project.file('missing-file1.yml'), project.file('missing-file2.yml'))
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        def exception = thrown(GradleException)
        exception.message.contains("Compose file does not exist")
        exception.message.contains("webapp")
        exception.message.contains("missing-file1.yml")
    }

    def "validateStackSpec handles mixed existing and missing files"() {
        given:
        def existingFile = createTempFile('existing.yml')

        extension.composeStacks {
            webapp {
                files.from(existingFile, project.file('missing.yml'))
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        def exception = thrown(GradleException)
        exception.message.contains("Compose file does not exist")
        exception.message.contains("webapp")
        exception.message.contains("missing.yml")
    }

    def "validateStackSpec handles invalid file paths"() {
        given:
        extension.composeStacks {
            webapp {
                files.from(project.file('nonexistent-invalid-file.yml'))
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        def exception = thrown(GradleException)
        exception.message.contains("Compose file does not exist")
        exception.message.contains("webapp")
    }

    def "validateStackSpec handles relative vs absolute paths"() {
        given:
        def composeFile = createTempFile('relative-test.yml')

        extension.composeStacks {
            relativeStack {
                files.from(project.file(composeFile.name))
            }
            absoluteStack {
                files.from(composeFile)
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    // =========================
    // Error Message Testing
    // =========================

    def "validateStackSpec provides clear error messages for missing files"() {
        given:
        extension.composeStacks {
            webapp {
                files.from(project.file('clearly-missing-file.yml'))
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('webapp')
        extension.validateStackSpec(stackSpec)

        then:
        def exception = thrown(GradleException)
        exception.message.contains("Compose file does not exist")
        exception.message.contains("webapp")
        exception.message.contains("clearly-missing-file.yml")
        exception.message.contains("Stack")
    }

    def "validateStackSpec provides clear error messages for empty configurations"() {
        given:
        extension.composeStacks {
            emptyStack {
                // No file configuration at all
                profiles = ['test']
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('emptyStack')
        extension.validateStackSpec(stackSpec)

        then:
        def exception = thrown(GradleException)
        exception.message.contains("No compose files specified")
        exception.message.contains("emptyStack")
        exception.message.contains("Stack")
    }

    def "validateStackSpec error messages are actionable"() {
        given:
        extension.composeStacks {
            actionableTest {
                files.from(project.file('non-existent-compose.yml'))
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('actionableTest')
        extension.validateStackSpec(stackSpec)

        then:
        def exception = thrown(GradleException)
        with(exception.message) {
            contains("Compose file does not exist")
            contains("actionableTest")
            contains("non-existent-compose.yml")
            // Should include absolute path for clarity
            contains(project.file('non-existent-compose.yml').absolutePath)
        }
    }

    // =========================
    // Provider API Testing
    // =========================

    def "validateStackSpec works correctly with Provider API for composeFiles"() {
        given:
        def composeFile1 = createTempFile('provider1.yml')
        def composeFile2 = createTempFile('provider2.yml')

        extension.composeStacks {
            providerTest {
                composeFiles(composeFile1.absolutePath, composeFile2.absolutePath)
                files.from(composeFile1, composeFile2)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('providerTest')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
        // Verify provider state
        stackSpec.composeFiles.present
        stackSpec.composeFiles.get().containsAll([composeFile1.absolutePath, composeFile2.absolutePath])
    }

    def "validateStackSpec works correctly with Provider API for composeFileCollection"() {
        given:
        def composeFile1 = createTempFile('collection1.yml')
        def composeFile2 = createTempFile('collection2.yml')

        extension.composeStacks {
            collectionTest {
                composeFiles(composeFile1, composeFile2)
                files.from(composeFile1, composeFile2)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('collectionTest')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
        // Verify provider state
        !stackSpec.composeFileCollection.empty
        stackSpec.composeFileCollection.files.containsAll([composeFile1, composeFile2])
    }

    def "validateStackSpec serialization compatibility works correctly"() {
        given:
        def composeFile = createTempFile('serializable.yml')

        extension.composeStacks {
            serializableTest {
                composeFiles(composeFile.absolutePath)
                files.from(composeFile)
            }
        }

        when:
        def stackSpec = extension.composeStacks.getByName('serializableTest')
        extension.validateStackSpec(stackSpec)

        then:
        noExceptionThrown()
        // Verify provider serialization doesn't break validation
        stackSpec.composeFiles.present
        stackSpec.composeFiles.orNull != null
    }

    // =========================
    // Action Parameter Tests
    // =========================

    def "composeStacks method accepts Action parameter"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        when:
        extension.composeStacks({ container ->
            container.create('actionStack') { stackSpec ->
                stackSpec.files.from(project.file('docker-compose.yml'))
            }
        } as org.gradle.api.Action)

        then:
        extension.composeStacks.size() == 1
        extension.composeStacks.getByName('actionStack') != null
    }

    def "composeConfigs method accepts Action parameter"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'

        when:
        extension.composeConfigs({ container ->
            container.create('configActionStack') { stackSpec ->
                stackSpec.files.from(project.file('docker-compose.yml'))
            }
        } as org.gradle.api.Action)

        then:
        extension.composeConfigs.size() == 1
        extension.composeConfigs.getByName('configActionStack') != null
    }

    // =========================
    // Environment File Validation Tests
    // =========================

    def "validate fails when env file is not readable"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        def envFile = project.file('test.env')
        envFile.text = 'VAR=value'
        // Make file not readable (on Unix systems)
        envFile.setReadable(false)

        extension.composeStacks {
            unreadableEnv {
                files.from(project.file('docker-compose.yml'))
                envFiles.from(envFile)
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Environment file is not readable") || ex.message.contains("Environment file does not exist")
        ex.message.contains('unreadableEnv')

        cleanup:
        // Restore permissions for cleanup
        envFile.setReadable(true)
    }

    def "validate passes with readable env file"() {
        given:
        project.file('docker-compose.yml').text = 'version: "3.8"'
        def envFile = project.file('readable.env')
        envFile.text = 'VAR=value'

        extension.composeStacks {
            readableEnv {
                files.from(project.file('docker-compose.yml'))
                envFiles.from(envFile)
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    // =========================
    // Helper Methods
    // =========================

    private File createTempFile(String name) {
        def file = new File(tempDir.toFile(), name)
        file.parentFile.mkdirs()
        file.createNewFile()
        file.text = 'version: "3.8"'
        return file
    }

}
