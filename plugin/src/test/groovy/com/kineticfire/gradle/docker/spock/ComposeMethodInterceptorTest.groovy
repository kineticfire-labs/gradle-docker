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
import com.kineticfire.gradle.docker.model.*
import com.kineticfire.gradle.docker.service.ComposeService
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.MethodInfo
import org.spockframework.runtime.model.MethodKind
import spock.lang.Specification

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for {@link ComposeMethodInterceptor}.
 */
class ComposeMethodInterceptorTest extends Specification {

    ComposeService mockComposeService
    ProcessExecutor mockProcessExecutor
    FileService mockFileService
    SystemPropertyService mockSystemPropertyService
    TimeService mockTimeService
    ComposeMethodInterceptor interceptor
    Map<String, Object> config
    Path tempComposeFile

    def setup() {
        mockComposeService = Mock(ComposeService)
        mockProcessExecutor = Mock(ProcessExecutor)
        mockFileService = Mock(FileService)
        mockSystemPropertyService = Mock(SystemPropertyService)
        mockTimeService = Mock(TimeService)

        // Create a temporary compose file for tests that need real file validation
        tempComposeFile = java.nio.file.Files.createTempFile("test-compose", ".yml")
        java.nio.file.Files.writeString(tempComposeFile, "version: '3'\nservices:\n  test:\n    image: alpine\n")

        config = [
            stackName: 'testStack',
            composeFiles: ['compose.yml'],
            lifecycle: LifecycleMode.METHOD,
            projectNameBase: 'testProject',
            waitForHealthy: [],
            waitForRunning: [],
            timeoutSeconds: 60,
            pollSeconds: 2,
            className: 'TestSpec'
        ]

        interceptor = new ComposeMethodInterceptor(
            config,
            mockComposeService,
            mockProcessExecutor,
            mockFileService,
            mockSystemPropertyService,
            mockTimeService
        )
    }

    def cleanup() {
        // Delete the temporary compose file
        if (tempComposeFile != null && java.nio.file.Files.exists(tempComposeFile)) {
            java.nio.file.Files.delete(tempComposeFile)
        }
    }

    def "constructor should initialize with provided services"() {
        expect:
        interceptor != null
    }

    def "intercept should handle SETUP method kind"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
    }

    def "intercept should handle CLEANUP method kind"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP, 'testMethod')
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
    }

    def "intercept should proceed for other method kinds"() {
        given:
        def invocation = createMockInvocation(MethodKind.FEATURE, 'testMethod')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        0 * mockComposeService._
    }

    def "handleSetup should start compose stack successfully"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        1 * invocation.proceed()
    }

    def "handleSetup should throw exception if compose file not found"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        Path composePath = Paths.get('compose.yml')

        mockFileService.resolve('compose.yml') >> composePath
        mockFileService.exists(composePath) >> false
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)

        when:
        interceptor.intercept(invocation)

        then:
        thrown(IllegalStateException)
        0 * invocation.proceed()
    }

    def "handleSetup should cleanup on failure"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> { throw new RuntimeException('Startup failed') }
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)

        when:
        interceptor.intercept(invocation)

        then:
        thrown(RuntimeException)
        0 * invocation.proceed()
    }

    def "handleCleanup should stop compose stack"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP, 'testMethod')
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * mockComposeService.downStack(_ as String)
        1 * invocation.proceed()
    }

    def "handleCleanup should continue cleanup even if proceed fails"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP, 'testMethod')
        invocation.proceed() >> { throw new RuntimeException('Proceed failed') }
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * mockComposeService.downStack(_ as String)
        noExceptionThrown()
    }

    def "handleCleanup should continue cleanup even if downStack fails"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP, 'testMethod')
        mockComposeService.downStack(_ as String) >> { throw new RuntimeException('Down failed') }
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()
    }

    def "waitForServices should wait for healthy services"() {
        given:
        config.waitForHealthy = ['web', 'db']
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockComposeService.waitForServices(_ as WaitConfig) >> CompletableFuture.completedFuture(null)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * mockComposeService.waitForServices(_ as WaitConfig)
    }

    def "waitForServices should wait for running services"() {
        given:
        config.waitForRunning = ['redis', 'cache']
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockComposeService.waitForServices(_ as WaitConfig) >> CompletableFuture.completedFuture(null)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * mockComposeService.waitForServices(_ as WaitConfig)
    }

    def "waitForServices should handle both healthy and running services"() {
        given:
        config.waitForHealthy = ['web']
        config.waitForRunning = ['redis']
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockComposeService.waitForServices(_ as WaitConfig) >> CompletableFuture.completedFuture(null)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        2 * mockComposeService.waitForServices(_ as WaitConfig)
    }

    def "waitForServices should continue if health check fails"() {
        given:
        config.waitForHealthy = ['web']
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockComposeService.waitForServices(_ as WaitConfig) >> {
            throw new RuntimeException('Health check timeout')
        }
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()
    }

    def "generateStateFile should create state file with correct format"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        Path buildDir = Paths.get('build')
        Path stateDir = Paths.get('build/compose-state')
        ComposeState state = createMockComposeState()
        String capturedJson = null

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> buildDir
        mockFileService.createDirectories(stateDir) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> { Path path, String json ->
            capturedJson = json
        }
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        capturedJson != null
        capturedJson.contains('"stackName": "testStack"')
        capturedJson.contains('"lifecycle": "method"')
        capturedJson.contains('"testClass": "TestSpec"')
        capturedJson.contains('"testMethod": "testMethod"')
    }

    def "generateStateFile should set system properties"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * mockSystemPropertyService.setProperty('COMPOSE_STATE_FILE', _)
        1 * mockSystemPropertyService.setProperty('COMPOSE_PROJECT_NAME', _)
    }

    def "generateStateFile should handle null ComposeState gracefully"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'testMethod')
        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(null)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        0 * mockFileService.writeString(_, _)
        1 * invocation.proceed()
    }

    def "cleanupExistingContainers should execute docker commands"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP, 'testMethod')
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')
    }

    def "cleanupExistingContainers should continue on failure"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP, 'testMethod')
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> { throw new RuntimeException('Cleanup failed') }
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "forceRemoveContainersByName should remove containers by name"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP, 'testMethod')
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute('docker', 'ps', '-aq', '--filter', _) >>
            new ProcessExecutor.ProcessResult(0, 'container1\ncontainer2')
        mockProcessExecutor.execute('docker', 'rm', '-f', _) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "forceRemoveContainersByName should handle empty container list"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP, 'testMethod')
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute('docker', 'ps', '-aq', '--filter', _) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "forceRemoveContainersByName should continue on error"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP, 'testMethod')
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> { throw new RuntimeException('Docker error') }

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "handleSetup should include method name in project name"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'specificTestMethod')
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()
    }

    def "handleCleanup should handle null feature name gracefully"() {
        given:
        def invocation = Mock(IMethodInvocation)
        def method = Mock(MethodInfo)
        method.kind >> MethodKind.CLEANUP
        method.name >> 'CLEANUP'
        invocation.method >> method
        invocation.feature >> null
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()
    }

    def "handleSetup should handle null feature name gracefully"() {
        given:
        def invocation = Mock(IMethodInvocation)
        def method = Mock(MethodInfo)
        method.kind >> MethodKind.SETUP
        method.name >> 'SETUP'
        invocation.method >> method
        invocation.feature >> null
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()
    }

    // Helper methods

    private IMethodInvocation createMockInvocation(MethodKind kind, String featureName = 'testMethod') {
        def invocation = Mock(IMethodInvocation)
        def method = Mock(MethodInfo)
        def feature = Mock(FeatureInfo)
        method.kind >> kind
        method.name >> kind.name()
        feature.name >> featureName
        invocation.method >> method
        invocation.feature >> feature
        return invocation
    }

    private ComposeState createMockComposeState() {
        def serviceInfo = new ServiceInfo(
            'container123',
            'test-web-1',
            'running',
            [new PortMapping(8080, 32768, 'tcp')]
        )
        return new ComposeState(
            'testStack',
            'testProject',
            ['web': serviceInfo]
        )
    }

    // Detailed verification tests for ComposeConfig and WaitConfig

    def "should pass correct ComposeConfig to upStack with method name"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'myTestMethod')
        ComposeState state = createMockComposeState()
        ComposeConfig capturedConfig = null

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> { ComposeConfig cfg ->
            capturedConfig = cfg
            return CompletableFuture.completedFuture(state)
        }
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        capturedConfig != null
        capturedConfig.composeFiles.size() == 1
        capturedConfig.composeFiles[0] == tempComposeFile
        capturedConfig.projectName.contains('testproject')
        capturedConfig.projectName.contains('testspec')
        capturedConfig.projectName.contains('mytestmethod')
        capturedConfig.stackName == 'testStack'
    }

    def "should pass correct WaitConfig for healthy services with method context"() {
        given:
        config.waitForHealthy = ['web', 'api']
        config.timeoutSeconds = 120
        config.pollSeconds = 5
        def invocation = createMockInvocation(MethodKind.SETUP, 'healthCheckTest')
        ComposeState state = createMockComposeState()
        WaitConfig capturedConfig = null

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockComposeService.waitForServices(_ as WaitConfig) >> { WaitConfig cfg ->
            capturedConfig = cfg
            return CompletableFuture.completedFuture(null)
        }
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        capturedConfig != null
        capturedConfig.projectName.contains('healthchecktest')
        capturedConfig.services == ['web', 'api']
        capturedConfig.timeout.seconds == 120
        capturedConfig.pollInterval.seconds == 5
        capturedConfig.targetState == ServiceStatus.HEALTHY
    }

    def "should pass correct WaitConfig for running services with method context"() {
        given:
        config.waitForRunning = ['db', 'queue']
        config.timeoutSeconds = 90
        config.pollSeconds = 3
        def invocation = createMockInvocation(MethodKind.SETUP, 'runningCheckTest')
        ComposeState state = createMockComposeState()
        WaitConfig capturedConfig = null

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockComposeService.waitForServices(_ as WaitConfig) >> { WaitConfig cfg ->
            capturedConfig = cfg
            return CompletableFuture.completedFuture(null)
        }
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        capturedConfig != null
        capturedConfig.projectName.contains('runningchecktest')
        capturedConfig.services == ['db', 'queue']
        capturedConfig.timeout.seconds == 90
        capturedConfig.pollInterval.seconds == 3
        capturedConfig.targetState == ServiceStatus.RUNNING
    }

    def "should pass correct project name with method name to downStack"() {
        given:
        // First setup to populate ThreadLocal
        def setupInvocation = createMockInvocation(MethodKind.SETUP, 'cleanupTestMethod')
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        interceptor.intercept(setupInvocation)

        // Now cleanup
        def cleanupInvocation = createMockInvocation(MethodKind.CLEANUP, 'cleanupTestMethod')
        String capturedProjectName = null

        mockComposeService.downStack(_ as String) >> { String projectName ->
            capturedProjectName = projectName
            return CompletableFuture.completedFuture(null)
        }

        when:
        interceptor.intercept(cleanupInvocation)

        then:
        capturedProjectName != null
        capturedProjectName.contains('testproject')
        capturedProjectName.contains('testspec')
        capturedProjectName.contains('cleanuptestmethod')
    }

    def "should generate state file with method name in path"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'specificMethod')
        ComposeState state = createMockComposeState()
        Path capturedStatePath = null

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> { Path path, String json ->
            capturedStatePath = path
        }
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        capturedStatePath != null
        capturedStatePath.toString().contains('testStack')
        capturedStatePath.toString().contains('TestSpec')
        capturedStatePath.toString().contains('specificMethod')
        capturedStatePath.toString().endsWith('-state.json')
    }

    def "should generate state file with complete service information including ports"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'portTest')
        def serviceInfo = new ServiceInfo(
            'xyz789',
            'method-app-1',
            'running',
            [
                new PortMapping(3000, 30001, 'tcp'),
                new PortMapping(443, 30002, 'tcp')
            ]
        )
        def state = new ComposeState('testStack', 'testProject', [
            'app': serviceInfo,
            'cache': new ServiceInfo('cache123', 'method-cache-1', 'healthy', [])
        ])
        String capturedJson = null

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> { Path path, String json ->
            capturedJson = json
        }
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        capturedJson != null
        capturedJson.contains('"containerId": "xyz789"')
        capturedJson.contains('"containerName": "method-app-1"')
        capturedJson.contains('"state": "running"')
        capturedJson.contains('"container": 3000')
        capturedJson.contains('"host": 30001')
        capturedJson.contains('"container": 443')
        capturedJson.contains('"host": 30002')
        capturedJson.contains('"containerId": "cache123"')
        capturedJson.contains('"state": "healthy"')
    }

    def "should set system property with state file path including method name"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'propTest')
        ComposeState state = createMockComposeState()
        String capturedStateFilePath = null

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockSystemPropertyService.setProperty('COMPOSE_STATE_FILE', _) >> { String key, String value ->
            capturedStateFilePath = value
        }
        mockSystemPropertyService.setProperty('COMPOSE_PROJECT_NAME', _) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        capturedStateFilePath != null
        capturedStateFilePath.contains('testStack')
        capturedStateFilePath.contains('TestSpec')
        capturedStateFilePath.contains('propTest')
    }

    def "should set system property with project name including method name"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP, 'projNameTest')
        ComposeState state = createMockComposeState()
        String capturedProjectName = null

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockSystemPropertyService.setProperty('COMPOSE_STATE_FILE', _) >> {}
        mockSystemPropertyService.setProperty('COMPOSE_PROJECT_NAME', _) >> { String key, String value ->
            capturedProjectName = value
        }
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        capturedProjectName != null
        capturedProjectName.contains('testproject')
        capturedProjectName.contains('testspec')
        capturedProjectName.contains('projnametest')
    }
}
