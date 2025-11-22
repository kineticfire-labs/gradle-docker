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
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for TestIntegrationExtension using GradleRunner
 *
 * Tests the usesCompose() method and composeStateFileFor() helper that are invoked
 * from build.gradle files. Integration tests cover annotation-based usage.
 */
class TestExtensionFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        
        settingsFile << "rootProject.name = 'test-extension'"
    }

    def "plugin configures usesCompose extension correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('test-compose.yml')
                    }
                }
            }
            
            tasks.register('smokeTest', Test) {
                usesCompose stack: 'testStack', lifecycle: 'suite'
            }
            
            task verifyExtension {
                doLast {
                    def testTask = tasks.getByName('smokeTest')
                    def stateFile = composeStateFileFor('testStack').get()
                    
                    println "State file: \${stateFile}"
                    println "Dependencies: \${testTask.dependsOn}"
                    println "Finalizers: \${testTask.finalizedBy.getDependencies()}"

                    assert stateFile.endsWith('testStack-state.json')
                    assert testTask.dependsOn.any { it.toString().contains('composeUpTestStack') }
                }
            }
        """
        
        // Create dummy compose file
        def composeFile = testProjectDir.resolve('test-compose.yml').toFile()
        composeFile << """
            services:
              test:
                image: alpine
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyExtension', '--stacktrace')
            .build()

        then:
        result.output.contains('testStack-state.json')
    }

    def "plugin configures composeStateFileFor correctly"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }
            
            task verifyStateFile {
                doLast {
                    def stateFile = composeStateFileFor('myStack').get()
                    println "State file: \${stateFile}"
                    
                    assert stateFile.endsWith('myStack-state.json')
                    assert stateFile.contains('compose-state')
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyStateFile', '--stacktrace')
            .build()

        then:
        result.output.contains('myStack-state.json')
    }

    def "plugin handles different lifecycle configurations"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerOrch {
                composeStacks {
                    classStack {
                        files.from('class-compose.yml')
                    }
                    methodStack {
                        files.from('method-compose.yml')
                    }
                }
            }
            
            tasks.register('classTest', Test) {
                usesCompose stack: 'classStack', lifecycle: 'class'
            }
            
            tasks.register('methodTest', Test) {
                usesCompose stack: 'methodStack', lifecycle: 'method'
            }
            
            task verifyLifecycles {
                doLast {
                    def classTask = tasks.getByName('classTest')
                    def methodTask = tasks.getByName('methodTest')
                    
                    println "Class lifecycle: \${classTask.systemProperties['docker.compose.lifecycle']}"
                    println "Method lifecycle: \${methodTask.systemProperties['docker.compose.lifecycle']}"
                    
                    assert classTask.systemProperties['docker.compose.stack'] == 'classStack'
                    assert classTask.systemProperties['docker.compose.lifecycle'] == 'class'
                    assert methodTask.systemProperties['docker.compose.stack'] == 'methodStack'
                    assert methodTask.systemProperties['docker.compose.lifecycle'] == 'method'
                }
            }
        """

        // Create dummy compose files
        testProjectDir.resolve('class-compose.yml').toFile() << "services:\n  test:\n    image: alpine"
        testProjectDir.resolve('method-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyLifecycles', '--stacktrace')
            .build()

        then:
        result.output.contains('Class lifecycle: class')
        result.output.contains('Method lifecycle: method')
    }

    def "suite lifecycle configures without error"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    suiteStack {
                        files.from('suite-compose.yml')
                    }
                }
            }

            tasks.register('suiteTest', Test) {
                usesCompose stack: 'suiteStack', lifecycle: 'suite'
            }

            task verifySuiteLifecycle {
                doLast {
                    def testTask = tasks.getByName('suiteTest')
                    // Verify task exists and was configured
                    assert testTask != null
                    println "Suite lifecycle test task configured successfully"
                }
            }
        """

        testProjectDir.resolve('suite-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySuiteLifecycle')
            .build()

        then:
        result.output.contains('Suite lifecycle test task configured successfully')
    }

    def "method lifecycle configures without error"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    methodStack {
                        files.from('method-compose.yml')
                    }
                }
            }

            tasks.register('perMethodExec', Test) {
                usesCompose stack: 'methodStack', lifecycle: 'method'
            }

            task verifyMethodLifecycle {
                doLast {
                    def testTask = tasks.getByName('perMethodExec')
                    // Verify task exists and was configured
                    assert testTask != null
                    println "Method lifecycle test task configured successfully"
                }
            }
        """

        testProjectDir.resolve('method-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyMethodLifecycle')
            .build()

        then:
        result.output.contains('Method lifecycle test task configured successfully')
    }

    def "extension methods work with custom test tasks"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    customStack {
                        files.from('custom-compose.yml')
                    }
                }
            }

            // Custom test task type
            tasks.register('customTestTask', Test) {
                usesCompose stack: 'customStack', lifecycle: 'suite'
            }

            task verifyCustomTask {
                doLast {
                    def customTask = tasks.getByName('customTestTask')
                    // Verify task was configured without error
                    assert customTask != null
                    println "Custom test task configured successfully"
                }
            }
        """

        testProjectDir.resolve('custom-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyCustomTask')
            .build()

        then:
        result.output.contains('Custom test task configured successfully')
    }

    def "test task depends on composeUp with suite lifecycle"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    depStack {
                        files.from('dep-compose.yml')
                    }
                }
            }

            tasks.register('depTest', Test) {
                usesCompose stack: 'depStack', lifecycle: 'suite'
            }

            task verifyDependencies {
                doLast {
                    def testTask = tasks.getByName('depTest')
                    def deps = testTask.dependsOn
                    println "Dependencies: \${deps}"
                    assert deps.any { it.toString().contains('composeUpDepStack') }
                }
            }
        """

        testProjectDir.resolve('dep-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyDependencies')
            .build()

        then:
        result.output.contains('composeUpDepStack')
    }

    def "test task finalizedBy composeDown with suite lifecycle"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    finStack {
                        files.from('fin-compose.yml')
                    }
                }
            }

            tasks.register('finTest', Test) {
                usesCompose stack: 'finStack', lifecycle: 'suite'
            }

            task verifyFinalizers {
                doLast {
                    def testTask = tasks.getByName('finTest')
                    def finalizers = testTask.finalizedBy.getDependencies()
                    println "Finalizers: \${finalizers}"
                    assert finalizers.any { it.name.contains('composeDownFinStack') }
                }
            }
        """

        testProjectDir.resolve('fin-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyFinalizers')
            .build()

        then:
        result.output.contains('composeDownFinStack')
    }

    def "usesCompose extension configures test task"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    propStack {
                        files.from('prop-compose.yml')
                    }
                }
            }

            tasks.register('propTest', Test) {
                usesCompose stack: 'propStack', lifecycle: 'suite'
            }

            task verifyTaskConfig {
                doLast {
                    def testTask = tasks.getByName('propTest')
                    // Verify task was configured by extension
                    assert testTask != null
                    println "Test task configured via usesCompose extension"
                }
            }
        """

        testProjectDir.resolve('prop-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyTaskConfig')
            .build()

        then:
        result.output.contains('Test task configured via usesCompose extension')
    }

    def "invalid lifecycle name rejected with error"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    errStack {
                        files.from('err-compose.yml')
                    }
                }
            }

            tasks.register('errorTest', Test) {
                usesCompose stack: 'errStack', lifecycle: 'invalid'
            }
        """

        testProjectDir.resolve('err-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help')
            .buildAndFail()

        then:
        result.output.contains('invalid') || result.output.contains('lifecycle')
    }

    def "stack name not found in dockerOrch rejected with error"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    existingStack {
                        files.from('existing-compose.yml')
                    }
                }
            }

            tasks.register('notFoundTest', Test) {
                usesCompose stack: 'nonExistentStack', lifecycle: 'suite'
            }
        """

        testProjectDir.resolve('existing-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help')
            .buildAndFail()

        then:
        result.output.contains('nonExistentStack') || result.output.contains('not found')
    }

    def "composeStateFileFor works without configured stack"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            task verifyStateFileAnyStack {
                doLast {
                    def stateFile = composeStateFileFor('arbitraryStack').get()
                    println "State file: \${stateFile}"
                    assert stateFile.endsWith('arbitraryStack-state.json')
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyStateFileAnyStack')
            .build()

        then:
        result.output.contains('arbitraryStack-state.json')
    }

    def "class lifecycle auto-wires composeUp dependency"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    classAutoWireStack {
                        files.from('class-auto-compose.yml')
                    }
                }
            }

            tasks.register('classAutoTest', Test) {
                usesCompose stack: 'classAutoWireStack', lifecycle: 'class'
            }

            task verifyClassAutoDeps {
                doLast {
                    def testTask = tasks.getByName('classAutoTest')
                    def deps = testTask.dependsOn
                    println "Dependencies: \${deps}"
                    assert deps.any { it.toString().contains('composeUpClassAutoWireStack') }
                }
            }
        """

        testProjectDir.resolve('class-auto-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyClassAutoDeps')
            .build()

        then:
        result.output.contains('composeUpClassAutoWireStack')
    }

    def "class lifecycle auto-wires composeDown finalizer"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    classFinStack {
                        files.from('class-fin-compose.yml')
                    }
                }
            }

            tasks.register('classFinTest', Test) {
                usesCompose stack: 'classFinStack', lifecycle: 'class'
            }

            task verifyClassFinalizers {
                doLast {
                    def testTask = tasks.getByName('classFinTest')
                    def finalizers = testTask.finalizedBy.getDependencies()
                    println "Finalizers: \${finalizers}"
                    assert finalizers.any { it.name.contains('composeDownClassFinStack') }
                }
            }
        """

        testProjectDir.resolve('class-fin-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyClassFinalizers')
            .build()

        then:
        result.output.contains('composeDownClassFinStack')
    }

    def "method lifecycle auto-wires composeUp dependency"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    methodAutoWireStack {
                        files.from('method-auto-compose.yml')
                    }
                }
            }

            tasks.register('methodAutoTest', Test) {
                usesCompose stack: 'methodAutoWireStack', lifecycle: 'method'
            }

            task verifyMethodAutoDeps {
                doLast {
                    def testTask = tasks.getByName('methodAutoTest')
                    def deps = testTask.dependsOn
                    println "Dependencies: \${deps}"
                    assert deps.any { it.toString().contains('composeUpMethodAutoWireStack') }
                }
            }
        """

        testProjectDir.resolve('method-auto-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyMethodAutoDeps')
            .build()

        then:
        result.output.contains('composeUpMethodAutoWireStack')
    }

    def "method lifecycle auto-wires composeDown finalizer"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    methodFinStack {
                        files.from('method-fin-compose.yml')
                    }
                }
            }

            tasks.register('methodFinTest', Test) {
                usesCompose stack: 'methodFinStack', lifecycle: 'method'
            }

            task verifyMethodFinalizers {
                doLast {
                    def testTask = tasks.getByName('methodFinTest')
                    def finalizers = testTask.finalizedBy.getDependencies()
                    println "Finalizers: \${finalizers}"
                    assert finalizers.any { it.name.contains('composeDownMethodFinStack') }
                }
            }
        """

        testProjectDir.resolve('method-fin-compose.yml').toFile() << "services:\n  test:\n    image: alpine"

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyMethodFinalizers')
            .build()

        then:
        result.output.contains('composeDownMethodFinStack')
    }
}