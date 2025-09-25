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
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Basic functional test to verify TestKit setup
 *
 * NOTE: This file contains only tests that do NOT use withPluginClasspath()
 * to avoid Gradle 9.0.0 TestKit compatibility issues.
 */
class BasicFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "can run basic gradle task"() {
        given:
        settingsFile << "rootProject.name = 'test-basic'"
        buildFile << """
            task hello {
                doLast {
                    println 'Hello from test!'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('hello')
            .build()

        then:
        result.output.contains('Hello from test!')
    }

    def "can compile basic java project"() {
        given:
        settingsFile << "rootProject.name = 'test-java'"
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                mavenCentral()
            }
        """

        // Create a simple Java class
        def javaDir = testProjectDir.resolve('src/main/java/com/test').toFile()
        javaDir.mkdirs()
        def javaFile = new File(javaDir, 'TestClass.java')
        javaFile.text = '''
            package com.test;
            public class TestClass {
                public String getMessage() {
                    return "Hello, World!";
                }
            }
        '''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('build')
            .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    /*
     * TEMPORARILY DISABLED: Gradle 9.0.0 TestKit Compatibility Issue
     *
     * This test is commented out due to known compatibility issues with TestKit in Gradle 9.0.0.
     * The withPluginClasspath() method causes InvalidPluginMetadataException errors.
     *
     * Issue: TestKit service cleanup conflicts with configuration cache in Gradle 9.0.0
     * See: docs/design-docs/functional-test-testkit-gradle-issue.md
     *
     * This test will be re-enabled when TestKit compatibility is improved in future Gradle versions.
     */
    // def "can apply docker plugin without withPluginClasspath"() {
    //     given:
    //     settingsFile << "rootProject.name = 'test-apply-plugin'"
    //
    //     when:
    //     def result = GradleRunner.create()
    //         .withProjectDir(testProjectDir.toFile())
    //         .withPluginClasspath(System.getProperty("java.class.path").split(":").collect { new File(it) })
    //         .withArguments('tasks', '--all')
    //         .build()
    //
    //     then:
    //     result.output.contains('tasks')
    // }
}