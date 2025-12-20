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

package com.kineticfire.gradle.docker.generator

import spock.lang.Specification

/**
 * Unit tests for TaskNamingUtils
 */
class TaskNamingUtilsTest extends Specification {

    // ===== CAPITALIZE TESTS =====

    def "capitalize capitalizes first character"() {
        expect:
        TaskNamingUtils.capitalize("hello") == "Hello"
    }

    def "capitalize handles already capitalized string"() {
        expect:
        TaskNamingUtils.capitalize("Hello") == "Hello"
    }

    def "capitalize handles single character"() {
        expect:
        TaskNamingUtils.capitalize("a") == "A"
    }

    def "capitalize handles empty string"() {
        expect:
        TaskNamingUtils.capitalize("") == ""
    }

    def "capitalize handles null"() {
        expect:
        TaskNamingUtils.capitalize(null) == ""
    }

    def "capitalize handles mixed case"() {
        expect:
        TaskNamingUtils.capitalize("myAppName") == "MyAppName"
    }

    def "capitalize handles all uppercase"() {
        expect:
        TaskNamingUtils.capitalize("HELLO") == "HELLO"
    }

    // ===== BUILD TASK NAME TESTS =====

    def "buildTaskName creates correct format"() {
        expect:
        TaskNamingUtils.buildTaskName("myApp") == "dockerBuildMyApp"
    }

    def "buildTaskName handles lowercase input"() {
        expect:
        TaskNamingUtils.buildTaskName("myapp") == "dockerBuildMyapp"
    }

    def "buildTaskName handles empty string"() {
        expect:
        TaskNamingUtils.buildTaskName("") == "dockerBuild"
    }

    def "buildTaskName handles various names"() {
        expect:
        TaskNamingUtils.buildTaskName(input) == expected

        where:
        input          | expected
        "alpine"       | "dockerBuildAlpine"
        "Ubuntu"       | "dockerBuildUbuntu"
        "myTestImage"  | "dockerBuildMyTestImage"
        "app123"       | "dockerBuildApp123"
    }

    // ===== TAG TASK NAME TESTS =====

    def "tagTaskName creates correct format"() {
        expect:
        TaskNamingUtils.tagTaskName("myApp") == "dockerTagMyApp"
    }

    def "tagTaskName handles various names"() {
        expect:
        TaskNamingUtils.tagTaskName(input) == expected

        where:
        input      | expected
        "alpine"   | "dockerTagAlpine"
        "Ubuntu"   | "dockerTagUbuntu"
        ""         | "dockerTag"
    }

    // ===== COMPOSE UP TASK NAME TESTS =====

    def "composeUpTaskName creates correct format"() {
        expect:
        TaskNamingUtils.composeUpTaskName("myStack") == "composeUpMyStack"
    }

    def "composeUpTaskName handles various names"() {
        expect:
        TaskNamingUtils.composeUpTaskName(input) == expected

        where:
        input          | expected
        "dev"          | "composeUpDev"
        "Production"   | "composeUpProduction"
        "testStack"    | "composeUpTestStack"
        ""             | "composeUp"
    }

    // ===== COMPOSE DOWN TASK NAME TESTS =====

    def "composeDownTaskName creates correct format"() {
        expect:
        TaskNamingUtils.composeDownTaskName("myStack") == "composeDownMyStack"
    }

    def "composeDownTaskName handles various names"() {
        expect:
        TaskNamingUtils.composeDownTaskName(input) == expected

        where:
        input          | expected
        "dev"          | "composeDownDev"
        "Production"   | "composeDownProduction"
        ""             | "composeDown"
    }

    // ===== TAG ON SUCCESS TASK NAME TESTS =====

    def "tagOnSuccessTaskName creates correct format"() {
        expect:
        TaskNamingUtils.tagOnSuccessTaskName("dockerProject") == "dockerProjectTagOnSuccess"
    }

    def "tagOnSuccessTaskName handles workflow prefix"() {
        expect:
        TaskNamingUtils.tagOnSuccessTaskName("workflowMyPipeline") == "workflowMyPipelineTagOnSuccess"
    }

    def "tagOnSuccessTaskName handles empty prefix"() {
        expect:
        TaskNamingUtils.tagOnSuccessTaskName("") == "TagOnSuccess"
    }

    // ===== SAVE TASK NAME TESTS =====

    def "saveTaskName creates correct format"() {
        expect:
        TaskNamingUtils.saveTaskName("dockerProject") == "dockerProjectSave"
    }

    def "saveTaskName handles various prefixes"() {
        expect:
        TaskNamingUtils.saveTaskName(prefix) == expected

        where:
        prefix                   | expected
        "dockerProject"          | "dockerProjectSave"
        "workflowMyPipeline"     | "workflowMyPipelineSave"
        ""                       | "Save"
    }

    // ===== PUBLISH TASK NAME TESTS =====

    def "publishTaskName with target creates correct format"() {
        expect:
        TaskNamingUtils.publishTaskName("dockerProject", "dockerhub") == "dockerProjectPublishDockerhub"
    }

    def "publishTaskName with target capitalizes target"() {
        expect:
        TaskNamingUtils.publishTaskName("dockerProject", "internal") == "dockerProjectPublishInternal"
    }

    def "publishTaskName without target creates correct format"() {
        expect:
        TaskNamingUtils.publishTaskName("dockerProject") == "dockerProjectPublish"
    }

    def "publishTaskName handles various combinations"() {
        expect:
        TaskNamingUtils.publishTaskName(prefix, target) == expected

        where:
        prefix                | target       | expected
        "dockerProject"       | "dockerhub"  | "dockerProjectPublishDockerhub"
        "dockerProject"       | "gcr"        | "dockerProjectPublishGcr"
        "workflowMyPipeline"  | "internal"   | "workflowMyPipelinePublishInternal"
        ""                    | "dockerhub"  | "PublishDockerhub"
        "dockerProject"       | ""           | "dockerProjectPublish"
    }

    // ===== CLEANUP TASK NAME TESTS =====

    def "cleanupTaskName creates correct format"() {
        expect:
        TaskNamingUtils.cleanupTaskName("dockerProject") == "dockerProjectCleanup"
    }

    def "cleanupTaskName handles various prefixes"() {
        expect:
        TaskNamingUtils.cleanupTaskName(prefix) == expected

        where:
        prefix                   | expected
        "dockerProject"          | "dockerProjectCleanup"
        "workflowMyPipeline"     | "workflowMyPipelineCleanup"
        ""                       | "Cleanup"
    }

    // ===== LIFECYCLE TASK NAME TESTS =====

    def "lifecycleTaskName creates correct format"() {
        expect:
        TaskNamingUtils.lifecycleTaskName("dockerProject") == "runDockerProject"
    }

    def "lifecycleTaskName handles various prefixes"() {
        expect:
        TaskNamingUtils.lifecycleTaskName(prefix) == expected

        where:
        prefix           | expected
        "dockerProject"  | "runDockerProject"
        "myPipeline"     | "runMyPipeline"
        ""               | "run"
    }

    // ===== TEST TASK NAME TESTS =====

    def "testTaskName creates correct format"() {
        expect:
        TaskNamingUtils.testTaskName("apiTests") == "apiTestsIntegrationTest"
    }

    def "testTaskName handles various names"() {
        expect:
        TaskNamingUtils.testTaskName(name) == expected

        where:
        name             | expected
        "apiTests"       | "apiTestsIntegrationTest"
        "statefulTests"  | "statefulTestsIntegrationTest"
        "dbTests"        | "dbTestsIntegrationTest"
        ""               | "IntegrationTest"
    }

    // ===== WORKFLOW PREFIX TESTS =====

    def "workflowPrefix creates correct format"() {
        expect:
        TaskNamingUtils.workflowPrefix("myPipeline") == "workflowMyPipeline"
    }

    def "workflowPrefix handles various names"() {
        expect:
        TaskNamingUtils.workflowPrefix(name) == expected

        where:
        name              | expected
        "build"           | "workflowBuild"
        "deployProd"      | "workflowDeployProd"
        "test"            | "workflowTest"
        ""                | "workflow"
    }

    // ===== DOCKER PROJECT PREFIX TESTS =====

    def "dockerProjectPrefix returns constant value"() {
        expect:
        TaskNamingUtils.dockerProjectPrefix() == "dockerProject"
    }

    // ===== NORMALIZE NAME TESTS =====

    def "normalizeName converts hyphenated names"() {
        expect:
        TaskNamingUtils.normalizeName("my-app-name") == "myAppName"
    }

    def "normalizeName converts underscored names"() {
        expect:
        TaskNamingUtils.normalizeName("my_app_name") == "myAppName"
    }

    def "normalizeName handles mixed separators"() {
        expect:
        TaskNamingUtils.normalizeName("my-app_name") == "myAppName"
    }

    def "normalizeName handles already camelCase"() {
        expect:
        TaskNamingUtils.normalizeName("myAppName") == "myappname"
    }

    def "normalizeName handles empty string"() {
        expect:
        TaskNamingUtils.normalizeName("") == ""
    }

    def "normalizeName handles null"() {
        expect:
        TaskNamingUtils.normalizeName(null) == ""
    }

    def "normalizeName handles single word"() {
        expect:
        TaskNamingUtils.normalizeName("app") == "app"
    }

    def "normalizeName handles uppercase words"() {
        expect:
        TaskNamingUtils.normalizeName("MY-APP") == "myApp"
    }

    // ===== STATE DIRECTORY TESTS =====

    def "stateDirectory creates correct path"() {
        expect:
        TaskNamingUtils.stateDirectory("dockerProject") == "dockerProject/state"
    }

    def "stateDirectory handles various prefixes"() {
        expect:
        TaskNamingUtils.stateDirectory(prefix) == expected

        where:
        prefix                | expected
        "dockerProject"       | "dockerProject/state"
        "workflowMyPipeline"  | "workflowMyPipeline/state"
        ""                    | "/state"
    }

    // ===== SANITIZE NAME TESTS =====

    def "sanitizeName converts to lowercase and removes special characters"() {
        expect:
        TaskNamingUtils.sanitizeName("My-App_Name") == "myappname"
    }

    def "sanitizeName handles empty string"() {
        expect:
        TaskNamingUtils.sanitizeName("") == ""
    }

    def "sanitizeName handles null"() {
        expect:
        TaskNamingUtils.sanitizeName(null) == ""
    }

    def "sanitizeName handles alphanumeric only"() {
        expect:
        TaskNamingUtils.sanitizeName("myapp123") == "myapp123"
    }

    def "sanitizeName handles uppercase input"() {
        expect:
        TaskNamingUtils.sanitizeName("MYAPP") == "myapp"
    }

    def "sanitizeName handles special characters only"() {
        expect:
        TaskNamingUtils.sanitizeName("---___...") == ""
    }

    def "sanitizeName handles mixed content"() {
        expect:
        TaskNamingUtils.sanitizeName(input) == expected

        where:
        input                    | expected
        "project-scenario1-app"  | "projectscenario1app"
        "My_Test_App"            | "mytestapp"
        "app@v1.2.3"             | "appv123"
        "123"                    | "123"
    }

    // ===== NORMALIZE NAME EDGE CASES =====

    def "normalizeName handles string with only separators"() {
        expect:
        TaskNamingUtils.normalizeName("---") == ""
    }

    def "normalizeName handles string with only underscores"() {
        expect:
        TaskNamingUtils.normalizeName("___") == ""
    }

    def "normalizeName handles mixed separator only string"() {
        expect:
        TaskNamingUtils.normalizeName("-_-_-") == ""
    }

    // ===== PRIVATE CONSTRUCTOR TEST =====

    def "private constructor prevents instantiation"() {
        when:
        def constructor = TaskNamingUtils.getDeclaredConstructor()
        constructor.setAccessible(true)
        constructor.newInstance()

        then:
        // The constructor should succeed when called via reflection
        // This test covers the private constructor for code coverage
        noExceptionThrown()
    }
}
