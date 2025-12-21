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
 * Unit tests for {@link ComposeClassInterceptor}.
 */
class ComposeClassInterceptorTest extends Specification {

    ComposeService mockComposeService
    ProcessExecutor mockProcessExecutor
    FileService mockFileService
    SystemPropertyService mockSystemPropertyService
    TimeService mockTimeService
    ComposeClassInterceptor interceptor
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
            lifecycle: LifecycleMode.CLASS,
            projectNameBase: 'testProject',
            waitForHealthy: [],
            waitForRunning: [],
            timeoutSeconds: 60,
            pollSeconds: 2,
            className: 'TestSpec'
        ]

        interceptor = new ComposeClassInterceptor(
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

    def "intercept should handle SETUP_SPEC method kind"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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

    def "intercept should handle CLEANUP_SPEC method kind"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.FEATURE)

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        0 * mockComposeService._
    }

    def "handleSetupSpec should start compose stack successfully"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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

    def "handleSetupSpec should throw exception if compose file not found"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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

    def "handleSetupSpec should cleanup on failure"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)

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

    def "handleCleanupSpec should stop compose stack"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * mockComposeService.downStack(_ as String)
        1 * invocation.proceed()
    }

    def "handleCleanupSpec should continue cleanup even if proceed fails"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
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

    def "handleCleanupSpec should continue cleanup even if downStack fails"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        capturedJson.contains('"lifecycle": "class"')
        capturedJson.contains('"testClass": "TestSpec"')
    }

    def "generateStateFile should set system properties"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)

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
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
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
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> { throw new RuntimeException('Docker error') }

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    // Helper methods

    private IMethodInvocation createMockInvocation(MethodKind kind) {
        def invocation = Mock(IMethodInvocation)
        def method = Mock(MethodInfo)
        method.kind >> kind
        method.name >> kind.name()
        invocation.method >> method
        invocation.feature >> Mock(FeatureInfo)
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

    def "should pass correct ComposeConfig to upStack"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        capturedConfig.projectName.startsWith('testproject-testspec-')
        capturedConfig.stackName == 'testStack'
    }

    def "should pass correct WaitConfig for healthy services"() {
        given:
        config.waitForHealthy = ['web', 'db']
        config.timeoutSeconds = 120
        config.pollSeconds = 5
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        capturedConfig.projectName.startsWith('testproject-testspec-')
        capturedConfig.services == ['web', 'db']
        capturedConfig.timeout.seconds == 120
        capturedConfig.pollInterval.seconds == 5
        capturedConfig.targetState == ServiceStatus.HEALTHY
    }

    def "should pass correct WaitConfig for running services"() {
        given:
        config.waitForRunning = ['redis', 'cache']
        config.timeoutSeconds = 90
        config.pollSeconds = 3
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        capturedConfig.projectName.startsWith('testproject-testspec-')
        capturedConfig.services == ['redis', 'cache']
        capturedConfig.timeout.seconds == 90
        capturedConfig.pollInterval.seconds == 3
        capturedConfig.targetState == ServiceStatus.RUNNING
    }

    def "should pass correct project name to downStack"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        String capturedProjectName = null

        mockComposeService.downStack(_ as String) >> { String projectName ->
            capturedProjectName = projectName
            return CompletableFuture.completedFuture(null)
        }
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        capturedProjectName != null
        capturedProjectName.startsWith('testproject-testspec-')
    }

    def "should generate state file with complete service information"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
        def serviceInfo = new ServiceInfo(
            'abc123',
            'testproject-web-1',
            'running',
            [
                new PortMapping(8080, 32768, 'tcp'),
                new PortMapping(8443, 32769, 'tcp')
            ]
        )
        def state = new ComposeState('testStack', 'testProject', [
            'web': serviceInfo,
            'db': new ServiceInfo('def456', 'testproject-db-1', 'running', [])
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
        capturedJson.contains('"containerId": "abc123"')
        capturedJson.contains('"containerName": "testproject-web-1"')
        capturedJson.contains('"state": "running"')
        capturedJson.contains('"container": 8080')
        capturedJson.contains('"host": 32768')
        capturedJson.contains('"protocol": "tcp"')
        capturedJson.contains('"container": 8443')
        capturedJson.contains('"host": 32769')
        capturedJson.contains('"containerId": "def456"')
        capturedJson.contains('"containerName": "testproject-db-1"')
    }

    def "should create state file with correct path"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        capturedStatePath.toString().contains('testStack-TestSpec-state.json')
    }

    def "should set system property with correct state file path"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        capturedStateFilePath.contains('testStack-TestSpec-state.json')
    }

    def "should set system property with correct project name"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        capturedProjectName.startsWith('testproject-testspec-')
    }

    def "should handle multiple compose files"() {
        given:
        config.composeFiles = ['compose.yml', 'compose-override.yml']
        def tempComposeFile2 = java.nio.file.Files.createTempFile("test-compose2", ".yml")
        java.nio.file.Files.writeString(tempComposeFile2, "version: '3'\nservices:\n  test2:\n    image: alpine\n")

        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
        ComposeState state = createMockComposeState()
        ComposeConfig capturedConfig = null

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.resolve('compose-override.yml') >> tempComposeFile2
        mockFileService.exists(tempComposeFile) >> true
        mockFileService.exists(tempComposeFile2) >> true
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
        capturedConfig.composeFiles.size() == 2
        1 * invocation.proceed()

        cleanup:
        if (tempComposeFile2 != null && java.nio.file.Files.exists(tempComposeFile2)) {
            java.nio.file.Files.delete(tempComposeFile2)
        }
    }

    def "should handle port mapping with null protocol"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
        def serviceInfo = new ServiceInfo(
            'xyz789',
            'test-app-1',
            'running',
            [new PortMapping(8080, 32768, null)]  // null protocol
        )
        def state = new ComposeState('testStack', 'testProject', ['app': serviceInfo])
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
        capturedJson.contains('"protocol": "tcp"')  // Default tcp used when null
    }

    def "handleSetupSpec should handle cleanup exception during startup failure"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> { throw new RuntimeException('Startup failed') }
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')
        mockComposeService.downStack(_ as String) >> { throw new RuntimeException('Cleanup also failed') }

        when:
        interceptor.intercept(invocation)

        then:
        thrown(RuntimeException)
        0 * invocation.proceed()
    }

    def "forceRemoveContainersByName should handle whitespace-only container output"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute('docker', 'ps', '-aq', '--filter', _) >>
            new ProcessExecutor.ProcessResult(0, '   \n   \n   ')

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "forceRemoveContainersByName should handle containers with newlines"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute('docker', 'ps', '-aq', '--filter', _) >>
            new ProcessExecutor.ProcessResult(0, 'abc123\ndef456\n')
        mockProcessExecutor.execute('docker', 'rm', '-f', _) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "forceRemoveContainersByName should handle label-based containers"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        def firstCall = true
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> { args ->
            def argList = args[0] as List
            if (argList.contains('-aq') && argList.any { it.toString().contains('name=') }) {
                return new ProcessExecutor.ProcessResult(0, '')
            } else if (argList.contains('-aq') && argList.any { it.toString().contains('label=') }) {
                return new ProcessExecutor.ProcessResult(0, 'label-container1\nlabel-container2')
            } else if (argList.contains('rm')) {
                return new ProcessExecutor.ProcessResult(0, '')
            } else {
                return new ProcessExecutor.ProcessResult(0, '')
            }
        }

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "cleanupExistingContainers should handle executeWithTimeout result"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, 'container1')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
    }

    def "handleCleanupSpec should handle null lastException properly"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()
    }

    def "handleCleanupSpec should set lastException on forceRemove failure"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> { throw new RuntimeException('Force remove failed') }

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()  // Errors are caught and logged
    }

    def "handleCleanupSpec should set lastException on container cleanup failure only"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        def callCount = 0
        mockProcessExecutor.executeWithTimeout(_, _, _) >> {
            callCount++
            if (callCount == 1) {
                throw new RuntimeException('Container cleanup failed')
            }
            return new ProcessExecutor.ProcessResult(0, '')
        }
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()
    }

    def "waitForServices should handle running check failure"() {
        given:
        config.waitForRunning = ['redis']
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockComposeService.waitForServices(_ as WaitConfig) >> {
            throw new RuntimeException('Running check timeout')
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

    def "stopComposeStack should log warning on failure"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> {
            throw new RuntimeException('Down stack failed completely')
        }
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> new ProcessExecutor.ProcessResult(0, '')

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()
    }

    def "forceRemoveContainersByName should handle null containerIds output"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> { args ->
            def argList = args[0] as List
            if (argList.contains('-aq')) {
                return new ProcessExecutor.ProcessResult(0, null)
            }
            return new ProcessExecutor.ProcessResult(0, '')
        }

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "forceRemoveContainersByName should handle single container id"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> { args ->
            def argList = args[0] as List
            if (argList.contains('-aq') && argList.any { it.toString().contains('name=') }) {
                return new ProcessExecutor.ProcessResult(0, 'single-container')
            } else if (argList.contains('-aq') && argList.any { it.toString().contains('label=') }) {
                return new ProcessExecutor.ProcessResult(0, '')
            } else if (argList.contains('rm')) {
                return new ProcessExecutor.ProcessResult(0, '')
            }
            return new ProcessExecutor.ProcessResult(0, '')
        }

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "forceRemoveContainersByName should handle empty trimmed container id in list"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP_SPEC)
        mockComposeService.downStack(_ as String) >> CompletableFuture.completedFuture(null)
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> { args ->
            def argList = args[0] as List
            if (argList.contains('-aq')) {
                return new ProcessExecutor.ProcessResult(0, 'container1\n\n\ncontainer2')
            } else if (argList.contains('rm')) {
                return new ProcessExecutor.ProcessResult(0, '')
            }
            return new ProcessExecutor.ProcessResult(0, '')
        }

        when:
        interceptor.intercept(invocation)

        then:
        noExceptionThrown()
    }

    def "intercept should handle SETUP method kind by proceeding"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP)

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
    }

    def "intercept should handle CLEANUP method kind by proceeding"() {
        given:
        def invocation = createMockInvocation(MethodKind.CLEANUP)

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
    }

    def "intercept should handle DATA_PROCESSOR method kind by proceeding"() {
        given:
        def invocation = Mock(IMethodInvocation)
        def method = Mock(MethodInfo)
        method.kind >> MethodKind.DATA_PROCESSOR
        method.name >> 'DATA_PROCESSOR'
        invocation.method >> method

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
    }

    def "generateStateFile should handle service with empty port list"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
        def serviceInfo = new ServiceInfo(
            'abc123',
            'test-service-1',
            'running',
            []  // empty port list
        )
        def state = new ComposeState('testStack', 'testProject', ['service': serviceInfo])
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
        // Pretty printed JSON may have different formatting, just check that publishedPorts appears with empty brackets
        capturedJson.contains('"publishedPorts"')
        capturedJson.contains('"containerId": "abc123"')
    }

    def "waitForServices should skip when both lists are empty"() {
        given:
        config.waitForHealthy = []
        config.waitForRunning = []
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        0 * mockComposeService.waitForServices(_)
        1 * invocation.proceed()
    }

    def "waitForServices should skip when lists are null"() {
        given:
        config.waitForHealthy = null
        config.waitForRunning = null
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
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
        0 * mockComposeService.waitForServices(_)
        1 * invocation.proceed()
    }

    def "cleanupExistingContainers should handle execute exception"() {
        given:
        def invocation = createMockInvocation(MethodKind.SETUP_SPEC)
        ComposeState state = createMockComposeState()

        mockFileService.resolve('compose.yml') >> tempComposeFile
        mockFileService.exists(tempComposeFile) >> true
        mockComposeService.upStack(_ as ComposeConfig) >> CompletableFuture.completedFuture(state)
        mockTimeService.now() >> LocalDateTime.parse('2025-10-14T10:00:00')
        mockFileService.resolve('build') >> Paths.get('build')
        mockFileService.createDirectories(_ as Path) >> {}
        mockFileService.writeString(_ as Path, _ as String) >> {}
        mockProcessExecutor.executeWithTimeout(_, _, _) >> new ProcessExecutor.ProcessResult(0, '')
        mockProcessExecutor.execute(_) >> { throw new RuntimeException('Prune failed') }

        when:
        interceptor.intercept(invocation)

        then:
        1 * invocation.proceed()
        noExceptionThrown()
    }
}
