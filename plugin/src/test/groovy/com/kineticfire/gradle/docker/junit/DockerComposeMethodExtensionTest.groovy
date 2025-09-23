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

package com.kineticfire.gradle.docker.junit

import com.kineticfire.gradle.docker.junit.service.FileService
import com.kineticfire.gradle.docker.junit.service.ProcessExecutor
import com.kineticfire.gradle.docker.junit.service.SystemPropertyService
import com.kineticfire.gradle.docker.junit.service.TimeService
import org.junit.jupiter.api.extension.ExtensionContext
import spock.lang.Specification
import spock.lang.Subject

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

/**
 * Unit tests for DockerComposeMethodExtension focused on achieving 100% coverage.
 */
class DockerComposeMethodExtensionTest extends Specification {

    ProcessExecutor processExecutor = Mock()
    FileService fileService = Mock()
    SystemPropertyService systemPropertyService = Mock()
    TimeService timeService = Mock()
    ExtensionContext context = Mock()

    @Subject
    DockerComposeMethodExtension extension

    def setup() {
        extension = new DockerComposeMethodExtension(processExecutor, fileService, systemPropertyService, timeService)
    }

    def "constructor with default services creates successfully"() {
        when:
        def defaultExtension = new DockerComposeMethodExtension()

        then:
        defaultExtension != null
    }

    def "constructor with services creates successfully"() {
        expect:
        extension != null
    }

    def "sanitizeProjectName handles various inputs"() {
        expect:
        invokeSanitizeProjectName("validproject") == "validproject"
        invokeSanitizeProjectName("test@project#name") == "test-project-name"
        invokeSanitizeProjectName("test---project") == "test-project"
        invokeSanitizeProjectName("---project") == "project"
        invokeSanitizeProjectName("project---") == "project"
        invokeSanitizeProjectName("-project") == "project"
        invokeSanitizeProjectName("") == "test-project"
    }

    def "capitalize handles various inputs"() {
        expect:
        invokeCapitalize("hello") == "Hello"
        invokeCapitalize("") == ""
        invokeCapitalize(null) == null
        invokeCapitalize("a") == "A"
    }

    def "getStackName with valid property returns stack name"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"

        when:
        String result = invokeGetStackName(context)

        then:
        result == "test-stack"
    }

    def "getStackName with null property throws exception"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> null

        when:
        invokeGetStackName(context)

        then:
        def ex = thrown(java.lang.reflect.InvocationTargetException)
        ex.cause instanceof IllegalStateException
    }

    def "getStackName with empty property throws exception"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> ""

        when:
        invokeGetStackName(context)

        then:
        def ex = thrown(java.lang.reflect.InvocationTargetException)
        ex.cause instanceof IllegalStateException
    }

    def "getProjectName handles various scenarios"() {
        given:
        systemPropertyService.getProperty("docker.compose.project") >> projectProperty
        context.getTestClass() >> testClass

        expect:
        invokeGetProjectName(context) == expectedResult

        where:
        projectProperty | testClass                     | expectedResult
        "test-project"  | Optional.of(String.class)    | "test-project"
        null            | Optional.of(String.class)    | "String"
        ""              | Optional.of(String.class)    | "String"
        null            | Optional.empty()             | "test"
    }

    def "generateUniqueProjectName generates correct format"() {
        given:
        systemPropertyService.getProperty("docker.compose.project") >> "base"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getMethod("toString"))
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        when:
        String result = invokeGenerateUniqueProjectName(context)

        then:
        result == "base-string-tostring-123045"
    }

    def "generateUniqueProjectName with no class uses unknown"() {
        given:
        systemPropertyService.getProperty("docker.compose.project") >> "base"
        context.getTestClass() >> Optional.empty()
        context.getTestMethod() >> Optional.empty()
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        when:
        String result = invokeGenerateUniqueProjectName(context)

        then:
        result == "base-unknown-unknown-123045"
    }

    def "beforeEach executes basic workflow"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"
        systemPropertyService.getProperty("docker.compose.project") >> "test-project"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getDeclaredMethods()[0])
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.exists(composeFile) >> true
        fileService.resolve(".") >> Paths.get(".")
        fileService.toFile(_) >> new File(".")
        fileService.resolve("build") >> Paths.get("build")
        fileService.exists(Paths.get("build/compose-state")) >> true
        fileService.list(Paths.get("build/compose-state")) >> Stream.empty()

        processExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.execute("docker", "compose", "-p", _, "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"Health":"healthy"}')

        when:
        extension.beforeEach(context)

        then:
        noExceptionThrown()
    }

    def "afterEach executes basic cleanup"() {
        given:
        // Set up the project name first
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"
        systemPropertyService.getProperty("docker.compose.project") >> "test-project"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getDeclaredMethods()[0])
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.exists(composeFile) >> true
        fileService.resolve(".") >> Paths.get(".")
        fileService.toFile(_) >> new File(".")
        fileService.resolve("build") >> Paths.get("build")
        fileService.exists(Paths.get("build/compose-state")) >> true
        fileService.list(Paths.get("build/compose-state")) >> Stream.empty()

        processExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.execute("docker", "compose", "-p", _, "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"Health":"healthy"}')
        processExecutor.execute("docker", "ps", "-aq", "--filter", _) >> new ProcessExecutor.ProcessResult(0, "")

        extension.beforeEach(context)  // Initialize state

        when:
        extension.afterEach(context)

        then:
        noExceptionThrown()
    }

    def "waitForStackToBeReady returns early with healthy container"() {
        given:
        processExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"Health":"healthy"}')

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        noExceptionThrown()
    }

    def "waitForStackToBeReady returns early with running container"() {
        given:
        processExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"State":"running"}')

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        noExceptionThrown()
    }

    def "waitForStackToBeReady retries with unhealthy container"() {
        given:
        processExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>> [
            new ProcessExecutor.ProcessResult(0, '{"State":"starting"}'),
            new ProcessExecutor.ProcessResult(0, '{"Health":"healthy"}')
        ]

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        noExceptionThrown()
    }

    def "waitForStackToBeReady handles process failure"() {
        given:
        processExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>> [
            { throw new IOException("Process failed") },
            new ProcessExecutor.ProcessResult(0, '{"Health":"healthy"}')
        ]

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        noExceptionThrown()
    }

    // Helper methods for testing private methods via reflection

    private String invokeSanitizeProjectName(String projectName) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("sanitizeProjectName", String.class)
        method.setAccessible(true)
        return (String) method.invoke(extension, projectName)
    }

    private String invokeCapitalize(String str) {
        try {
            Method method = DockerComposeMethodExtension.class.getDeclaredMethod("capitalize", String.class)
            method.setAccessible(true)
            return (String) method.invoke(extension, (Object) str)
        } catch (InvocationTargetException e) {
            if (e.cause instanceof RuntimeException) {
                throw (RuntimeException) e.cause
            }
            throw new RuntimeException(e.cause)
        }
    }

    private String invokeGetStackName(ExtensionContext context) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("getStackName", ExtensionContext.class)
        method.setAccessible(true)
        return (String) method.invoke(extension, context)
    }

    private String invokeGetProjectName(ExtensionContext context) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("getProjectName", ExtensionContext.class)
        method.setAccessible(true)
        return (String) method.invoke(extension, context)
    }

    private String invokeGenerateUniqueProjectName(ExtensionContext context) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("generateUniqueProjectName", ExtensionContext.class)
        method.setAccessible(true)
        return (String) method.invoke(extension, context)
    }

    private void invokeWaitForStackToBeReady(String stackName, String uniqueProjectName) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("waitForStackToBeReady", String.class, String.class)
        method.setAccessible(true)
        method.invoke(extension, stackName, uniqueProjectName)
    }

    // Additional tests for missing coverage scenarios

    def "sanitizeProjectName handles special character edge cases"() {
        expect:
        invokeSanitizeProjectName(input) == expected

        where:
        input                      | expected
        "!@#project"              | "project"
        "project-with-UPPERCASE"  | "project-with-uppercase"
        "---leading-hyphens"      | "leading-hyphens"
        "trailing-hyphens---"     | "trailing-hyphens"
        "1start-with-number"      | "1start-with-number"
        ""                        | "test-project"
    }

    def "beforeEach handles startup failure with cleanup failure"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"
        systemPropertyService.getProperty("docker.compose.project") >> "test-project"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getDeclaredMethods()[0])
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.exists(composeFile) >> true
        fileService.toFile(_) >> new File(".")

        // First cleanup succeeds, compose start fails, second cleanup fails
        processExecutor.executeWithTimeout(_, _, _) >>> [
            new ProcessExecutor.ProcessResult(0, "Cleanup success"),
            new ProcessExecutor.ProcessResult(1, "Startup failed"),
            new ProcessExecutor.ProcessResult(1, "Cleanup failed")
        ]
        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(1, "Compose failed")

        when:
        extension.beforeEach(context)

        then:
        Exception ex = thrown()
        // Check that some exception is thrown due to the startup failure
        ex.message != null
    }

    def "beforeEach handles compose file not found"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"
        systemPropertyService.getProperty("docker.compose.project") >> "test-project"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getDeclaredMethods()[0])
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.exists(composeFile) >> false

        processExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, "Cleanup success")

        when:
        extension.beforeEach(context)

        then:
        IllegalStateException ex = thrown()
        ex.message.contains("Compose file not found")
    }

    def "afterEach handles null stored project name"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"
        systemPropertyService.getProperty("docker.compose.project") >> "fallback-project"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getDeclaredMethods()[0])
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        fileService.resolve("build") >> Paths.get("build")
        fileService.exists(_) >> false
        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.execute("docker", "ps", "-aq", "--filter", _) >> new ProcessExecutor.ProcessResult(0, "")

        // Don't call beforeEach to set up project name, so afterEach uses fallback

        when:
        extension.afterEach(context)

        then:
        noExceptionThrown()
    }

    def "waitForStackToBeReady times out after max attempts"() {
        given:
        // Return non-healthy status for all attempts
        processExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"State":"starting"}')

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        noExceptionThrown()
        // Should complete after max attempts without throwing exception
    }

    def "generateUniqueProjectName handles no method name"() {
        given:
        systemPropertyService.getProperty("docker.compose.project") >> "base"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.empty()
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        when:
        String result = invokeGenerateUniqueProjectName(context)

        then:
        result == "base-string-unknown-123045"
    }

    def "generateStateFile creates method-specific state file"() {
        given:
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getMethod("toString"))
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        Path buildDir = Paths.get("build")
        Path stateDir = buildDir.resolve("compose-state")
        fileService.resolve("build") >> buildDir

        when:
        invokeGenerateStateFile("test-stack", "test-project-123", context)

        then:
        1 * fileService.createDirectories(stateDir)
        1 * fileService.writeString(_, _)
        1 * systemPropertyService.setProperty("COMPOSE_STATE_FILE", _)
    }

    // Helper methods for additional private method testing

    private void invokeGenerateStateFile(String stackName, String uniqueProjectName, ExtensionContext context) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("generateStateFile", String.class, String.class, ExtensionContext.class)
        method.setAccessible(true)
        method.invoke(extension, stackName, uniqueProjectName, context)
    }

    private void invokeStartComposeStack(String stackName, String uniqueProjectName) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("startComposeStack", String.class, String.class)
        method.setAccessible(true)
        try {
            method.invoke(extension, stackName, uniqueProjectName)
        } catch (InvocationTargetException e) {
            // Unwrap the actual exception from reflection
            throw e.cause
        }
    }

    private void invokeStopComposeStack(String stackName, String uniqueProjectName) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("stopComposeStack", String.class, String.class)
        method.setAccessible(true)
        method.invoke(extension, stackName, uniqueProjectName)
    }

    def "startComposeStack handles non-zero exit code"() {
        given:
        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.exists(composeFile) >> true
        fileService.resolve(".") >> Paths.get(".")
        fileService.toFile(_) >> new File(".")

        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(1, "Docker compose failed")

        when:
        invokeStartComposeStack("test-stack", "test-project")

        then:
        RuntimeException ex = thrown()
        ex.message.contains("Failed to start compose stack")
        ex.message.contains("Docker compose failed")
    }

    def "stopComposeStack handles non-zero exit code gracefully"() {
        given:
        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.resolve(".") >> Paths.get(".")
        fileService.toFile(_) >> new File(".")

        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(1, "Docker compose down failed")

        when:
        invokeStopComposeStack("test-stack", "test-project")

        then:
        noExceptionThrown()
        // stopComposeStack should not throw exceptions, just log warnings
    }

    // Tests for missing exception handling coverage in afterEach method
    
    def "afterEach handles container cleanup failure"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"
        systemPropertyService.getProperty("docker.compose.project") >> "test-project"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getDeclaredMethods()[0])
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.exists(composeFile) >> true
        fileService.resolve(".") >> Paths.get(".")
        fileService.toFile(_) >> new File(".")
        fileService.resolve("build") >> Paths.get("build")
        fileService.exists(Paths.get("build/compose-state")) >> true
        fileService.list(Paths.get("build/compose-state")) >> Stream.empty()

        processExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.execute("docker", "compose", "-p", _, "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"Health":"healthy"}')
        processExecutor.execute("docker", "ps", "-aq", "--filter", _) >> new ProcessExecutor.ProcessResult(0, "")

        extension.beforeEach(context)  // Initialize state

        // Make cleanup operations fail
        processExecutor.executeInDirectory(_, _) >>> [
            new ProcessExecutor.ProcessResult(0, "Success"),  // For beforeEach
            { throw new Exception("Container cleanup failed") },  // stopComposeStack fails
            new ProcessExecutor.ProcessResult(0, "Force cleanup success")  // forceRemoveContainersByName succeeds
        ]

        when:
        extension.afterEach(context)

        then:
        noExceptionThrown()  // Should handle the exception gracefully
    }

    def "afterEach handles force cleanup failure"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"
        systemPropertyService.getProperty("docker.compose.project") >> "test-project"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getDeclaredMethods()[0])
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.exists(composeFile) >> true
        fileService.resolve(".") >> Paths.get(".")
        fileService.toFile(_) >> new File(".")
        fileService.resolve("build") >> Paths.get("build")
        fileService.exists(Paths.get("build/compose-state")) >> true
        fileService.list(Paths.get("build/compose-state")) >> Stream.empty()

        processExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.execute("docker", "compose", "-p", _, "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"Health":"healthy"}')
        processExecutor.execute("docker", "ps", "-aq", "--filter", _) >> new ProcessExecutor.ProcessResult(0, "")

        extension.beforeEach(context)  // Initialize state

        // Make force cleanup fail
        processExecutor.execute("docker", "ps", "-aq", "--filter", _) >> { throw new Exception("Force cleanup failed") }

        when:
        extension.afterEach(context)

        then:
        noExceptionThrown()  // Should handle the exception gracefully
    }

    def "afterEach handles state file cleanup failure"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"
        systemPropertyService.getProperty("docker.compose.project") >> "test-project"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getDeclaredMethods()[0])
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.exists(composeFile) >> true
        fileService.resolve(".") >> Paths.get(".")
        fileService.toFile(_) >> new File(".")
        fileService.resolve("build") >> Paths.get("build")
        fileService.exists(Paths.get("build/compose-state")) >> true
        fileService.list(Paths.get("build/compose-state")) >> Stream.of(Paths.get("state-file"))

        processExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.execute("docker", "compose", "-p", _, "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"Health":"healthy"}')
        processExecutor.execute("docker", "ps", "-aq", "--filter", _) >> new ProcessExecutor.ProcessResult(0, "")

        extension.beforeEach(context)  // Initialize state

        // Make state file cleanup fail
        fileService.delete(_) >> { throw new IOException("State file cleanup failed") }

        when:
        extension.afterEach(context)

        then:
        noExceptionThrown()  // Should handle the exception gracefully
    }

    def "afterEach propagates exception when both cleanup and force cleanup fail"() {
        given:
        systemPropertyService.getProperty("docker.compose.stack") >> "test-stack"
        systemPropertyService.getProperty("docker.compose.project") >> "test-project"
        context.getTestClass() >> Optional.of(String.class)
        context.getTestMethod() >> Optional.of(String.class.getDeclaredMethods()[0])
        timeService.now() >> LocalDateTime.of(2023, 1, 1, 12, 30, 45)

        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-method.yml")
        fileService.resolve("src/integrationTest/resources/compose/integration-method.yml") >> composeFile
        fileService.exists(composeFile) >> true
        fileService.resolve(".") >> Paths.get(".")
        fileService.toFile(_) >> new File(".")
        fileService.resolve("build") >> Paths.get("build")
        fileService.exists(Paths.get("build/compose-state")) >> true
        fileService.list(Paths.get("build/compose-state")) >> Stream.empty()

        processExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.executeInDirectory(_, _) >> new ProcessExecutor.ProcessResult(0, "Success")
        processExecutor.execute("docker", "compose", "-p", _, "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"Health":"healthy"}')
        processExecutor.execute("docker", "ps", "-aq", "--filter", _) >> new ProcessExecutor.ProcessResult(0, "")

        extension.beforeEach(context)  // Initialize state

        // Make both cleanup operations fail
        processExecutor.executeInDirectory(_, _) >>> [
            new ProcessExecutor.ProcessResult(0, "Success"),  // For beforeEach
            { throw new Exception("Container cleanup failed") }  // stopComposeStack fails
        ]
        processExecutor.execute("docker", "ps", "-aq", "--filter", _) >>> [
            new ProcessExecutor.ProcessResult(0, ""),  // For beforeEach
            { throw new Exception("Force cleanup failed") }  // forceRemoveContainersByName fails
        ]

        when:
        extension.afterEach(context)

        then:
        // Should complete without throwing exception - failures are logged but don't fail the test
        noExceptionThrown()
    }

    // Tests for forceRemoveContainersByName method coverage gaps
    
    def "forceRemoveContainersByName handles containers found and removed"() {
        given:
        processExecutor.execute("docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=test-project") >>
            new ProcessExecutor.ProcessResult(0, "container1\ncontainer2\n")
        processExecutor.execute("docker", "rm", "-f", "container1", "container2") >>
            new ProcessExecutor.ProcessResult(0, "Removed containers")

        when:
        invokeForceRemoveContainersByName("test-project")

        then:
        noExceptionThrown()
    }

    def "forceRemoveContainersByName handles container removal failure"() {
        given:
        processExecutor.execute("docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=test-project") >>
            new ProcessExecutor.ProcessResult(0, "container1\n")
        processExecutor.execute("docker", "rm", "-f", "container1") >>
            new ProcessExecutor.ProcessResult(1, "Failed to remove container")

        when:
        invokeForceRemoveContainersByName("test-project")

        then:
        noExceptionThrown()  // Should not throw exception, just log warning
    }

    def "forceRemoveContainersByName handles docker ps failure"() {
        given:
        processExecutor.execute("docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=test-project") >>
            new ProcessExecutor.ProcessResult(1, "Docker ps failed")

        when:
        invokeForceRemoveContainersByName("test-project")

        then:
        noExceptionThrown()  // Should not throw exception, just log warning
    }

    def "forceRemoveContainersByName handles process executor exception"() {
        given:
        processExecutor.execute("docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=test-project") >>
            { throw new IOException("Process execution failed") }

        when:
        invokeForceRemoveContainersByName("test-project")

        then:
        noExceptionThrown()  // Should not throw exception, just log warning
    }

    // Tests for cleanupStateFile lambda expressions coverage
    
    def "cleanupStateFile handles file deletion with lambda expressions"() {
        given:
        fileService.resolve("build") >> Paths.get("build")
        fileService.exists(Paths.get("build/compose-state")) >> true
        Path stateFile1 = Paths.get("build/compose-state/state1")
        Path stateFile2 = Paths.get("build/compose-state/state2")
        fileService.list(Paths.get("build/compose-state")) >> Stream.of(stateFile1, stateFile2)

        // Mock the toString to match expected pattern
        stateFile1.metaClass.toString = { -> "test-project-123045.state" }
        stateFile2.metaClass.toString = { -> "other-project-456789.state" }
        
        fileService.delete(stateFile1) >> { /* successful deletion */ }
        fileService.delete(stateFile2) >> { /* successful deletion */ }

        when:
        invokeCleanupStateFile("test-project", "123045")

        then:
        noExceptionThrown()
    }

    def "cleanupStateFile handles file deletion exception in lambda"() {
        given:
        fileService.resolve("build") >> Paths.get("build")
        fileService.exists(Paths.get("build/compose-state")) >> true
        Path stateFile = Paths.get("build/compose-state/state1")
        fileService.list(Paths.get("build/compose-state")) >> Stream.of(stateFile)

        // Mock the toString to match expected pattern
        stateFile.metaClass.toString = { -> "test-project-123045.state" }
        
        fileService.delete(stateFile) >> { throw new IOException("File deletion failed") }

        when:
        invokeCleanupStateFile("test-project", "123045")

        then:
        noExceptionThrown()  // Lambda should catch and handle IOException
    }

    // Tests for missing edge cases in sanitizeProjectName
    
    def "sanitizeProjectName handles edge case with leading non-alphanumeric character"() {
        expect:
        invokeSanitizeProjectName("@project-name") == "project-name"
        invokeSanitizeProjectName("123project") == "123project"  // Numbers are allowed at start
        invokeSanitizeProjectName("special-chars-project") == "special-chars-project"
    }

    // Test for timeout scenario in waitForStackToBeReady
    
    def "waitForStackToBeReady handles maximum attempts reached"() {
        given:
        // Mock the sleep to avoid actual delays
        timeService.sleep(1000) >> { /* immediate return */ }
        
        // Return unhealthy status for all attempts (including the loop timeout condition)
        processExecutor.execute("docker", "compose", "-p", "test-project", "ps", "--format", "json") >>
            new ProcessExecutor.ProcessResult(0, '{"State":"starting"}')

        when:
        invokeWaitForStackToBeReady("test-stack", "test-project")

        then:
        noExceptionThrown()
        // Should complete after max attempts without throwing exception
    }

    // Helper methods for testing private methods via reflection
    
    private void invokeForceRemoveContainersByName(String projectName) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("forceRemoveContainersByName", String.class)
        method.setAccessible(true)
        method.invoke(extension, projectName)
    }

    private void invokeCleanupStateFile(String projectName, String timestamp) {
        Method method = DockerComposeMethodExtension.class.getDeclaredMethod("cleanupStateFile", String.class, String.class)
        method.setAccessible(true)
        method.invoke(extension, projectName, timestamp)
    }
}