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

package com.kineticfire.gradle.docker.integration.appimage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * Integration tests for Docker registry publishing functionality.
 * 
 * These tests use real Docker registries spun up via Docker Compose to validate:
 * - Image push operations to unauthenticated registries
 * - Image push operations to authenticated registries 
 * - Authentication failure scenarios
 * - Multiple registry publishing
 * 
 * Prerequisites:
 * - Docker daemon running
 * - Docker Compose available
 * - Network connectivity for registry containers
 */
@Stepwise
@IgnoreIf({ !isDockerAvailable() })
class DockerRegistryPublishIntegrationIT extends Specification {

    static final String BASIC_REGISTRY = "localhost:25000"
    static final String AUTH_REGISTRY = "localhost:25001"
    static final String TEST_USERNAME = "integrationuser"
    static final String TEST_PASSWORD = "integrationpass"
    
    @Shared
    boolean registriesStarted = false
    
    @Shared
    File testProjectDir
    
    @Shared
    File buildFile
    
    @Shared
    ObjectMapper objectMapper = new ObjectMapper()
    
    static boolean isDockerAvailable() {
        try {
            def process = "docker info".execute()
            process.waitFor()
            return process.exitValue() == 0
        } catch (Exception e) {
            return false
        }
    }

    def setupSpec() {
        println "Setting up Docker Registry Integration Tests..."
        
        // Use the existing test project directory structure
        testProjectDir = new File(System.getProperty("user.dir"))
        buildFile = new File(testProjectDir, "build.gradle")
        
        // Start test registries
        startTestRegistries()
        
        // Verify registries are running
        waitForRegistry(BASIC_REGISTRY)
        waitForRegistry(AUTH_REGISTRY)
        
        println "Test registries are ready for integration testing"
    }

    def cleanupSpec() {
        println "Cleaning up Docker Registry Integration Tests..."
        stopTestRegistries()
        println "Test registries stopped"
    }

    def "debug test - should build timeServer without publish configuration"() {
        given: "the original timeServer configuration"
        // Use the existing build.gradle as-is
        
        when: "dockerBuild task is executed"  
        def result = executeGradleTasks(['dockerBuildTimeServer'])
        
        then: "the build task succeeds"
        result.contains("BUILD SUCCESSFUL")
    }

    def "should publish time-server image to basic registry without authentication"() {
        given: "the time-server image is built and tagged"
        def originalBuildContent = buildFile.text
        
        when: "build.gradle is configured with basic registry publish target"
        def modifiedBuildContent = addSimplePublishConfig(originalBuildContent)
        buildFile.text = modifiedBuildContent
        
        and: "dockerBuild and dockerPublish tasks are executed"
        def result = executeGradleTasks(['dockerBuildTimeServer', 'dockerPublishTimeServer'])
        
        then: "the publish task succeeds"
        result.contains("BUILD SUCCESSFUL")
        result.contains("Successfully pushed")
        
        and: "the image exists in the basic registry"
        def imageExists = verifyImageInRegistry(BASIC_REGISTRY, "time-server-integration", "latest")
        imageExists
        
        cleanup:
        buildFile.text = originalBuildContent
    }

    def "should publish time-server image to authenticated registry with valid credentials"() {
        given: "the time-server image is built and tagged"
        def originalBuildContent = buildFile.text
        
        when: "build.gradle is configured with authenticated registry publish target"
        def modifiedBuildContent = addAuthenticatedRegistryPublishConfig(originalBuildContent)
        buildFile.text = modifiedBuildContent
        
        and: "dockerBuild and dockerPublish tasks are executed"
        def result = executeGradleTasks(['dockerBuildTimeServer', 'dockerPublishTimeServer'])
        
        then: "the publish task succeeds"
        result.contains("BUILD SUCCESSFUL")
        result.contains("Successfully pushed")
        
        and: "the image exists in the authenticated registry"
        def imageExists = verifyImageInAuthenticatedRegistry(AUTH_REGISTRY, "time-server-auth", "latest", TEST_USERNAME, TEST_PASSWORD)
        imageExists
        
        cleanup:
        buildFile.text = originalBuildContent
    }

    def "should publish time-server image to multiple registries simultaneously"() {
        given: "the time-server image is built and tagged"
        def originalBuildContent = buildFile.text
        
        when: "build.gradle is configured with multiple registry publish targets"
        def modifiedBuildContent = addMultipleRegistryPublishConfig(originalBuildContent)
        buildFile.text = modifiedBuildContent
        
        and: "dockerBuild and dockerPublish tasks are executed"
        def result = executeGradleTasks(['dockerBuildTimeServer', 'dockerPublishTimeServer'])
        
        then: "the publish task succeeds"
        result.contains("BUILD SUCCESSFUL")
        result.contains("Successfully pushed")
        
        and: "the image exists in both registries"
        verifyImageInRegistry(BASIC_REGISTRY, "time-server-multi", "multi")
        verifyImageInAuthenticatedRegistry(AUTH_REGISTRY, "time-server-multi-auth", "multi", TEST_USERNAME, TEST_PASSWORD)
        
        cleanup:
        buildFile.text = originalBuildContent
    }

    def "should fail to publish to authenticated registry with invalid credentials"() {
        given: "the time-server image is built and tagged"
        def originalBuildContent = buildFile.text
        
        when: "build.gradle is configured with invalid credentials"
        def modifiedBuildContent = addInvalidAuthRegistryPublishConfig(originalBuildContent)
        buildFile.text = modifiedBuildContent
        
        and: "dockerBuild and dockerPublish tasks are executed"
        def result = executeGradleTasks(['dockerBuildTimeServer', 'dockerPublishTimeServer'], true)
        
        then: "the publish task fails"
        result.contains("BUILD FAILED") || result.contains("authentication") || result.contains("unauthorized")
        
        cleanup:
        buildFile.text = originalBuildContent
    }

    def "should handle registry connectivity failures gracefully"() {
        given: "the time-server image is built and tagged"
        def originalBuildContent = buildFile.text
        
        when: "build.gradle is configured with unreachable registry"
        def modifiedBuildContent = addUnreachableRegistryPublishConfig(originalBuildContent)
        buildFile.text = modifiedBuildContent
        
        and: "dockerBuild and dockerPublish tasks are executed"
        def result = executeGradleTasks(['dockerBuildTimeServer', 'dockerPublishTimeServer'], true)
        
        then: "the publish task fails with appropriate error"
        result.contains("BUILD FAILED")
        result.contains("connection") || result.contains("refused") || result.contains("unreachable")
        
        cleanup:
        buildFile.text = originalBuildContent
    }

    def "validates enhanced publish configuration with complex scenarios"() {
        given: "the time-server image is built and tagged"
        def originalBuildContent = buildFile.text
        
        when: "build.gradle is configured with complex multi-registry publish setup"
        def modifiedBuildContent = addComplexValidationTestConfig(originalBuildContent)
        buildFile.text = modifiedBuildContent
        
        and: "validation occurs during configuration"
        def result = executeGradleTasks(['tasks', '--all'])
        
        then: "enhanced validation succeeds for complex configurations"
        result.contains("BUILD SUCCESSFUL")
        !result.contains("Invalid Docker tag")
        !result.contains("Invalid repository")
        result.contains("dockerPublishTimeServer")
        
        cleanup:
        buildFile.text = originalBuildContent
    }

    def "validates that enhanced error messages are user-friendly"() {
        given: "the time-server image is built and tagged"
        def originalBuildContent = buildFile.text
        
        when: "build.gradle is configured with invalid tag names"
        def modifiedBuildContent = addInvalidTagValidationTestConfig(originalBuildContent)
        buildFile.text = modifiedBuildContent
        
        and: "gradle configuration is processed"
        def result = executeGradleTasks(['tasks'], true)
        
        then: "enhanced validation provides helpful error messages"
        result.contains("BUILD FAILED")
        result.contains("Invalid Docker tag name")
        result.contains("Image tags should be simple names")
        result.contains("For registry publishing, specify the repository")
        
        cleanup:
        buildFile.text = originalBuildContent
    }

    // ===== REGISTRY MANAGEMENT =====
    
    private void startTestRegistries() {
        if (registriesStarted) return
        
        println "Starting integration test registries..."
        def composeFile = new File("src/integrationTest/resources/docker-compose-test-registries.yml")
        def workingDir = testProjectDir
        
        // Start registries
        def startProcess = "docker compose -f ${composeFile.path} up -d".execute(null, workingDir)
        startProcess.waitForProcessOutput(System.out, System.err)
        startProcess.waitFor()
        
        if (startProcess.exitValue() != 0) {
            throw new RuntimeException("Failed to start test registries")
        }
        
        registriesStarted = true
        println "Integration test registries started successfully"
    }
    
    private void stopTestRegistries() {
        if (!registriesStarted) return
        
        println "Stopping integration test registries..."
        def composeFile = new File("src/integrationTest/resources/docker-compose-test-registries.yml")
        def workingDir = testProjectDir
        
        def stopProcess = "docker compose -f ${composeFile.path} down -v".execute(null, workingDir)
        stopProcess.waitForProcessOutput(System.out, System.err)
        stopProcess.waitFor()
        registriesStarted = false
        println "Integration test registries stopped"
    }
    
    private void waitForRegistry(String registryHost) {
        println "Waiting for registry ${registryHost} to be ready..."
        for (int i = 0; i < 30; i++) {
            try {
                def connection = new URL("http://${registryHost}/v2/").openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                def responseCode = connection.responseCode
                // Accept both 200 (basic registry) and 401 (authenticated registry)
                if (responseCode == 200 || responseCode == 401) {
                    println "Registry ${registryHost} is ready (response: ${responseCode})"
                    return
                }
            } catch (Exception e) {
                // Registry not ready yet
            }
            Thread.sleep(2000)
        }
        throw new RuntimeException("Registry ${registryHost} did not become ready within 60 seconds")
    }

    // ===== BUILD CONFIGURATION HELPERS =====
    
    private String addSimplePublishConfig(String originalContent) {
        // Find the exact location to insert the publish config
        // Look for the buildArgs closing brace and insert publish config after it
        def buildArgsEndPattern = /]\s*\n\s*}/
        def publishConfig = """            publish {
                to('basic') {
                    repository = '${BASIC_REGISTRY}/time-server-integration'
                    tags = ['latest']
                }
            }
"""
        
        // Find the timeServer block and insert publish config before its closing brace
        def result = originalContent.replaceFirst(
            /(\s*buildArgs\s*=\s*\[[^\]]*\]\s*\n\s*)(})/, 
            "\$1${publishConfig}        \$2"
        )
        
        return result
    }
    
    private String createBuildGradleWithoutPublish() {
        return """
plugins {
    id 'groovy'
    id 'java'
    id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
}

group = 'com.kineticfire.gradle.docker.integration'
version = '1.0.0'

repositories {
    mavenCentral()
}

docker {
    images {
        timeServer {
            contextTask = tasks.register('prepareTimeServerContext', Copy) {
                into layout.buildDirectory.dir('docker-context/timeServer')
                from('src/main/docker')
            }
            dockerfile = 'Dockerfile' 
            tags = [
                "\$version",
                "latest"
            ]
            buildArgs = [
                'JAR_FILE': "app-\$version.jar",
                'BUILD_VERSION': version,
                'BUILD_TIME': new Date().format('yyyy-MM-dd HH:mm:ss')
            ]
        }
    }
}

// Copy the app JAR to Docker context before building image
tasks.register('copyAppJar', Copy) {
    description = 'Copy app JAR to Docker build context'
    from project(':app').tasks.jar.outputs.files
    into 'src/main/docker'
    rename { "app-\$version.jar" }
}

// Ensure JAR is copied before Docker build
afterEvaluate {
    tasks.named('dockerBuildTimeServer') {
        dependsOn 'copyAppJar'
        dependsOn ':app:jar'
    }
}
"""
    }
    
    private String createBuildGradleWithBasicPublish() {
        return """
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

plugins {
    id 'groovy'
    id 'java'
    id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
}

group = 'com.kineticfire.gradle.docker.integration'
version = '1.0.0'

repositories {
    mavenCentral()
}

// Configure source sets for testing
sourceSets {
    functionalTest {
        groovy.srcDir 'src/functionalTest/groovy'
        resources.srcDir 'src/functionalTest/resources'
        compileClasspath += sourceSets.main.output
        runtimeClasspath += output + compileClasspath
    }
    integrationTest {
        java.srcDir 'src/integrationTest/java'
        groovy.srcDir 'src/integrationTest/groovy'
        resources.srcDir 'src/integrationTest/resources'  
        compileClasspath += sourceSets.main.output
        runtimeClasspath += output + compileClasspath
    }
}

// Configure test configurations
configurations {
    functionalTestImplementation.extendsFrom implementation
    functionalTestRuntimeOnly.extendsFrom runtimeOnly
    integrationTestImplementation.extendsFrom implementation
    integrationTestRuntimeOnly.extendsFrom runtimeOnly
}

// Handle duplicate resources
tasks.withType(ProcessResources) {
    duplicatesStrategy = DuplicatesStrategy.WARN
}

dependencies {
    // Functional Test dependencies (Groovy/Spock)
    functionalTestImplementation platform('org.spockframework:spock-bom:2.4-M4-groovy-4.0')
    functionalTestImplementation 'org.spockframework:spock-core'
    functionalTestImplementation 'org.spockframework:spock-junit4'
    functionalTestImplementation 'com.fasterxml.jackson.core:jackson-databind:2.16.0'
    functionalTestRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    functionalTestRuntimeOnly 'org.junit.vintage:junit-vintage-engine'
    
    // Integration Test dependencies (Java/JUnit)  
    integrationTestImplementation platform('org.junit:junit-bom:5.10.0')
    integrationTestImplementation 'org.junit.jupiter:junit-jupiter'
    integrationTestImplementation 'org.assertj:assertj-core:3.24.2'
    integrationTestImplementation 'com.fasterxml.jackson.core:jackson-databind:2.16.0'
    integrationTestImplementation gradleTestKit()
    integrationTestRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    
    // Integration Test dependencies (Groovy/Spock for registry tests)
    integrationTestImplementation platform('org.spockframework:spock-bom:2.4-M4-groovy-4.0')
    integrationTestImplementation 'org.spockframework:spock-core'
    integrationTestImplementation 'org.spockframework:spock-junit4'
}

docker {
    images {
        timeServer {
            contextTask = tasks.register('prepareTimeServerContext', Copy) {
                into layout.buildDirectory.dir('docker-context/timeServer')
                from('src/main/docker')
            }
            dockerfile = 'Dockerfile' 
            tags = [
                "\$version",
                "latest"
            ]
            buildArgs = [
                'JAR_FILE': "app-\$version.jar",
                'BUILD_VERSION': version,
                'BUILD_TIME': new Date().format('yyyy-MM-dd HH:mm:ss')
            ]
            publish {
                to('basic') {
                    repository = '${BASIC_REGISTRY}/time-server-integration'
                    tags = ['latest']
                }
            }
        }
    }
}

dockerOrch {
    composeStacks {
        smokeTest {
            files.from('src/functionalTest/resources/compose/smoke-test.yml')
            projectName = "smoke-\$project.name"
            waitForHealthy {
                services = ["time-server"]
                timeoutSeconds = 30
            }
            logs {
                writeTo = file("\$buildDir/compose-logs/smokeTest")
                tailLines = 100
            }
        }
        integrationSuite {
            files.from('src/integrationTest/resources/compose/integration-suite.yml')
            projectName = "integration-suite-\$project.name"
            waitForHealthy {
                services = ["time-server"]
                timeoutSeconds = 30  
            }
            logs {
                writeTo = file("\$buildDir/compose-logs/integrationSuite")
                tailLines = 200
            }
        }
        integrationClass {
            files.from('src/integrationTest/resources/compose/integration-class.yml')
            projectName = "integration-class-\$project.name"
            waitForHealthy {
                services = ["time-server"]
                timeoutSeconds = 45
            }
            logs {
                writeTo = file("\$buildDir/compose-logs/integrationClass") 
                tailLines = 200
            }
        }
        integrationMethod {
            files.from('src/integrationTest/resources/compose/integration-method.yml')
            projectName = "integration-method-\$project.name"
            waitForHealthy {
                services = ["time-server"] 
                timeoutSeconds = 20
            }
            logs {
                writeTo = file("\$buildDir/compose-logs/integrationMethod")
                tailLines = 100
            }
        }
    }
}

// Copy the app JAR to Docker context before building image
tasks.register('copyAppJar', Copy) {
    description = 'Copy app JAR to Docker build context'
    from project(':app').tasks.jar.outputs.files
    into 'src/main/docker'
    rename { "app-\${version}.jar" }
}

// Ensure JAR is copied before Docker build
afterEvaluate {
    tasks.named('dockerBuildTimeServer') {
        dependsOn 'copyAppJar'
        dependsOn ':app:jar'
    }
}

// SMOKE TESTS: Quick validation using suite lifecycle (fastest)
tasks.register('smokeTest', Test) {
    description = 'Smoke tests with suite lifecycle - quick Docker image validation'
    group = 'verification'
    testClassesDirs = sourceSets.functionalTest.output.classesDirs  
    classpath = sourceSets.functionalTest.runtimeClasspath
    useJUnitPlatform()
    
    // DEMONSTRATE: Suite lifecycle - compose up once for all tests (fastest)
    usesCompose stack: "smokeTest", lifecycle: "suite" 
    systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("smokeTest")
    
    dependsOn 'dockerBuildTimeServer'
}

// INTEGRATION TESTS: Demonstrating different lifecycle patterns

tasks.register('integrationTestSuite', Test) {
    description = 'Integration tests with suite lifecycle - shared environment for performance'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    include '**/TimeServerSuiteLifecycleIT.class'
    
    // DEMONSTRATE: Suite lifecycle - one compose up/down per test suite
    usesCompose stack: "integrationSuite", lifecycle: "suite"
    systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("integrationSuite")
    
    dependsOn 'dockerBuildTimeServer'
    shouldRunAfter 'smokeTest'
}

tasks.register('integrationTestClass', Test) {
    description = 'Integration tests with class lifecycle - fresh environment per test class'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    include '**/TimeServerClassLifecycleIT.class'
    
    // DEMONSTRATE: Class lifecycle - compose up/down per test class  
    // TODO: usesCompose functionality not yet implemented in plugin
    // usesCompose stack: "integrationClass", lifecycle: "class"
    // systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("integrationClass")
    
    dependsOn 'dockerBuildTimeServer'
    shouldRunAfter 'integrationTestSuite'
}

tasks.register('integrationTestMethod', Test) {
    description = 'Integration tests with method lifecycle - maximum isolation (slowest)'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    include '**/TimeServerMethodLifecycleIT.class'
    
    // DEMONSTRATE: Method lifecycle - compose up/down per test method (maximum isolation)
    // TODO: usesCompose functionality not yet implemented in plugin
    // usesCompose stack: "integrationMethod", lifecycle: "method"
    // systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("integrationMethod")
    
    dependsOn 'dockerBuildTimeServer'
    shouldRunAfter 'integrationTestClass'
}

tasks.register('integrationTestRegistry', Test) {
    description = 'Integration tests for Docker registry publishing functionality'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    include '**/DockerRegistryPublishIntegrationIT.class'
    
    dependsOn 'dockerBuildTimeServer'
    shouldRunAfter 'integrationTestMethod'
}
"""
    }
    
    private String addBasicRegistryPublishConfig(String originalContent) {
        def publishConfig = """
            publish {
                to('basic') {
                    repository = '${BASIC_REGISTRY}/time-server-integration'
                    tags = ['latest']
                }
            }
        """
        return addPublishConfigToTimeServer(originalContent, publishConfig)
    }
    
    private String addAuthenticatedRegistryPublishConfig(String originalContent) {
        def publishConfig = """
            publish {
                to('authenticated') {
                    repository = '${AUTH_REGISTRY}/time-server-auth'
                    tags = ['latest']
                    auth {
                        username = '${TEST_USERNAME}'
                        password = '${TEST_PASSWORD}'
                    }
                }
            }
        """
        return addPublishConfigToTimeServer(originalContent, publishConfig)
    }
    
    private String addMultipleRegistryPublishConfig(String originalContent) {
        def publishConfig = """
            publish {
                to('basic-multi') {
                    repository = '${BASIC_REGISTRY}/time-server-multi'
                    tags = ['multi']
                }
                to('auth-multi') {
                    repository = '${AUTH_REGISTRY}/time-server-multi-auth'
                    tags = ['multi']
                    auth {
                        username = '${TEST_USERNAME}'
                        password = '${TEST_PASSWORD}'
                    }
                }
            }
        """
        return addPublishConfigToTimeServer(originalContent, publishConfig)
    }
    
    private String addInvalidAuthRegistryPublishConfig(String originalContent) {
        def publishConfig = """
            publish {
                to('invalid-auth') {
                    repository = '${AUTH_REGISTRY}/time-server-invalid'
                    tags = ['invalid']
                    auth {
                        username = 'wronguser'
                        password = 'wrongpass'
                    }
                }
            }
        """
        return addPublishConfigToTimeServer(originalContent, publishConfig)
    }
    
    private String addUnreachableRegistryPublishConfig(String originalContent) {
        def publishConfig = """
            publish {
                to('unreachable') {
                    repository = 'localhost:99999/time-server-unreachable'
                    tags = ['unreachable']
                }
            }
        """
        return addPublishConfigToTimeServer(originalContent, publishConfig)
    }
    
    private String addPublishConfigToTimeServer(String originalContent, String publishConfig) {
        // Find the timeServer image configuration and add publish config
        // Look for the timeServer block that spans multiple lines
        def startPattern = /timeServer \{/
        def startMatcher = originalContent =~ startPattern
        
        if (startMatcher.find()) {
            def startIndex = startMatcher.start()
            
            // Find the matching closing brace for the timeServer block
            def braceCount = 0
            def endIndex = -1
            def inTimeServerBlock = false
            
            for (int i = startIndex; i < originalContent.length(); i++) {
                char c = originalContent.charAt(i)
                
                if (c == '{') {
                    braceCount++
                    if (braceCount == 1) {
                        inTimeServerBlock = true
                    }
                } else if (c == '}') {
                    braceCount--
                    if (braceCount == 0 && inTimeServerBlock) {
                        endIndex = i
                        break
                    }
                }
            }
            
            if (endIndex != -1) {
                def beforeTimeServer = originalContent.substring(0, endIndex)
                def afterTimeServer = originalContent.substring(endIndex)
                
                return beforeTimeServer + "            ${publishConfig}\n        " + afterTimeServer
            } else {
                throw new RuntimeException("Could not find closing brace for timeServer configuration block")
            }
        } else {
            throw new RuntimeException("Could not find timeServer configuration block in build.gradle")
        }
    }

    // ===== GRADLE EXECUTION HELPERS =====
    
    private String executeGradleTasks(List<String> tasks, boolean allowFailure = false) {
        // Use gradlew from plugin directory and specify project path
        def gradlewPath = '../../plugin/gradlew'
        def command = [gradlewPath, '-p', '.', '--no-daemon'] + tasks
        def process = command.execute(null, testProjectDir)
        
        def output = new StringBuilder()
        def error = new StringBuilder()
        
        process.waitForProcessOutput(output, error)
        process.waitFor()
        
        def result = output.toString() + error.toString()
        
        if (!allowFailure && process.exitValue() != 0) {
            println "Gradle execution failed with exit code: ${process.exitValue()}"
            println "Output: ${result}"
            throw new RuntimeException("Gradle task execution failed: ${tasks}")
        }
        
        return result
    }

    // ===== REGISTRY VERIFICATION HELPERS =====
    
    private boolean verifyImageInRegistry(String registryHost, String imageName, String tag) {
        try {
            Thread.sleep(3000) // Allow time for registry to process push
            def url = new URL("http://${registryHost}/v2/${imageName}/tags/list")
            def connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                def response = connection.inputStream.text
                def jsonNode = objectMapper.readTree(response)
                def tags = jsonNode.get("tags")
                
                if (tags && tags.isArray()) {
                    def tagList = []
                    tags.forEach { tagList << it.asText() }
                    return tagList.contains(tag)
                }
            }
            return false
        } catch (Exception e) {
            println "Error verifying image in registry: ${e.message}"
            return false
        }
    }
    
    private boolean verifyImageInAuthenticatedRegistry(String registryHost, String imageName, String tag, String username, String password) {
        try {
            Thread.sleep(3000) // Allow time for registry to process push
            def url = new URL("http://${registryHost}/v2/${imageName}/tags/list")
            def connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Add basic authentication
            def auth = Base64.encoder.encodeToString("${username}:${password}".bytes)
            connection.setRequestProperty("Authorization", "Basic ${auth}")
            
            if (connection.responseCode == 200) {
                def response = connection.inputStream.text
                def jsonNode = objectMapper.readTree(response)
                def tags = jsonNode.get("tags")
                
                if (tags && tags.isArray()) {
                    def tagList = []
                    tags.forEach { tagList << it.asText() }
                    return tagList.contains(tag)
                }
            }
            return false
        } catch (Exception e) {
            println "Error verifying image in authenticated registry: ${e.message}"
            return false
        }
    }

    // ===== ENHANCED VALIDATION TEST HELPER METHODS =====

    private String addComplexValidationTestConfig(String originalContent) {
        def publishConfig = """            publish {
                to('staging') {
                    repository = '${BASIC_REGISTRY}/time-server-staging'
                    tags = ['latest', 'v1.0.0', 'staging-ready']
                }
                to('production') {
                    repository = '${AUTH_REGISTRY}/time-server-prod'
                    tags = ['v1.0.0', 'stable', 'prod-ready']
                    auth {
                        username = '${TEST_USERNAME}'
                        password = '${TEST_PASSWORD}'
                    }
                }
                to('backup') {
                    repository = 'backup-registry.company.com:8443/archive/time-server'
                    tags = ['v1.0.0-backup', 'archived']
                }
            }
"""
        
        // Insert the publish configuration
        def result = originalContent.replaceFirst(
            /(\s*buildArgs\s*=\s*\[[^\]]*\]\s*\n\s*)(})/, 
            "\$1${publishConfig}        \$2"
        )
        
        return result
    }

    private String addInvalidTagValidationTestConfig(String originalContent) {
        // Create configuration with invalid tag names to test validation
        def invalidConfig = originalContent.replaceFirst(
            /(tags\s*=\s*\[)[^\]]*(\])/,
            "\$1'invalid tag with spaces', 'another:bad:format'\$2"
        )
        
        return invalidConfig
    }
}