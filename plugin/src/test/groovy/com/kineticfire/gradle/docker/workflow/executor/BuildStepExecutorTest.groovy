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

package com.kineticfire.gradle.docker.workflow.executor

import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TaskLookup
import com.kineticfire.gradle.docker.workflow.TaskLookupFactory
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for BuildStepExecutor
 */
class BuildStepExecutorTest extends Specification {

    Project project
    BuildStepExecutor executor
    ImageSpec imageSpec
    BuildStepSpec buildStepSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        executor = new BuildStepExecutor(project.tasks)

        imageSpec = project.objects.newInstance(
            ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )

        buildStepSpec = project.objects.newInstance(BuildStepSpec)
        buildStepSpec.image.set(imageSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor accepts TaskContainer"() {
        when:
        def exec = new BuildStepExecutor(project.tasks)

        then:
        exec != null
    }

    def "constructor accepts TaskLookup"() {
        when:
        def exec = new BuildStepExecutor(TaskLookupFactory.from(project.tasks))

        then:
        exec != null
    }

    // ===== VALIDATE BUILD SPEC TESTS =====

    def "validateBuildSpec throws exception when buildSpec is null"() {
        when:
        executor.validateBuildSpec(null)

        then:
        def e = thrown(GradleException)
        e.message.contains('cannot be null')
    }

    def "validateBuildSpec throws exception when image is not set"() {
        given:
        def spec = project.objects.newInstance(BuildStepSpec)

        when:
        executor.validateBuildSpec(spec)

        then:
        def e = thrown(GradleException)
        e.message.contains('image must be configured')
    }

    def "validateBuildSpec passes when image is set"() {
        when:
        executor.validateBuildSpec(buildStepSpec)

        then:
        noExceptionThrown()
    }

    // ===== COMPUTE BUILD TASK NAME TESTS =====

    def "computeBuildTaskName capitalizes image name"() {
        expect:
        executor.computeBuildTaskName(imageName) == expected

        where:
        imageName       | expected
        'myImage'       | 'dockerBuildMyImage'
        'testApp'       | 'dockerBuildTestApp'
        'app'           | 'dockerBuildApp'
        'API'           | 'dockerBuildAPI'
    }

    def "computeBuildTaskName handles single character"() {
        expect:
        executor.computeBuildTaskName('a') == 'dockerBuildA'
    }

    def "computeBuildTaskName handles empty string"() {
        expect:
        executor.computeBuildTaskName('') == 'dockerBuild'
    }

    def "computeBuildTaskName handles null"() {
        expect:
        executor.computeBuildTaskName(null) == 'dockerBuildnull'
    }

    // ===== CAPITALIZE FIRST LETTER TESTS =====

    def "capitalizeFirstLetter capitalizes correctly"() {
        expect:
        executor.capitalizeFirstLetter(input) == expected

        where:
        input       | expected
        'test'      | 'Test'
        'Test'      | 'Test'
        'TEST'      | 'TEST'
        'a'         | 'A'
        'ab'        | 'Ab'
        'myApp'     | 'MyApp'
    }

    def "capitalizeFirstLetter handles empty string"() {
        expect:
        executor.capitalizeFirstLetter('') == ''
    }

    def "capitalizeFirstLetter handles null"() {
        expect:
        executor.capitalizeFirstLetter(null) == null
    }

    // ===== EXECUTE BUILD TASK TESTS =====

    def "executeBuildTask throws exception when task not found"() {
        when:
        executor.executeBuildTask('nonExistentTask')

        then:
        def e = thrown(GradleException)
        e.message.contains('not found')
        e.message.contains('nonExistentTask')
    }

    def "executeBuildTask executes task actions"() {
        given:
        def actionExecuted = false
        def task = project.tasks.create('dockerBuildTest') {
            doLast {
                actionExecuted = true
            }
        }

        when:
        executor.executeBuildTask('dockerBuildTest')

        then:
        actionExecuted
    }

    // ===== LOOKUP TASK TESTS =====

    def "lookupTask returns task when exists"() {
        given:
        project.tasks.create('myTask')

        expect:
        executor.lookupTask('myTask') != null
    }

    def "lookupTask returns null when task does not exist"() {
        expect:
        executor.lookupTask('nonExistent') == null
    }

    // ===== EXECUTE TASK TESTS =====

    def "executeTask runs all task actions"() {
        given:
        def action1Executed = false
        def action2Executed = false
        def task = project.tasks.create('testTask') {
            doLast { action1Executed = true }
            doLast { action2Executed = true }
        }

        when:
        executor.executeTask(task)

        then:
        action1Executed
        action2Executed
    }

    def "executeTask handles task with no actions"() {
        given:
        def task = project.tasks.create('emptyTask')

        when:
        executor.executeTask(task)

        then:
        noExceptionThrown()
    }

    // ===== HOOK EXECUTION TESTS =====

    def "executeBeforeBuildHook executes hook when present"() {
        given:
        def hookExecuted = false
        def hook = { hookExecuted = true } as Action<Void>
        buildStepSpec.beforeBuild.set(hook)

        when:
        executor.executeBeforeBuildHook(buildStepSpec)

        then:
        hookExecuted
    }

    def "executeBeforeBuildHook does nothing when hook not present"() {
        when:
        executor.executeBeforeBuildHook(buildStepSpec)

        then:
        noExceptionThrown()
    }

    def "executeAfterBuildHook executes hook when present"() {
        given:
        def hookExecuted = false
        def hook = { hookExecuted = true } as Action<Void>
        buildStepSpec.afterBuild.set(hook)

        when:
        executor.executeAfterBuildHook(buildStepSpec)

        then:
        hookExecuted
    }

    def "executeAfterBuildHook does nothing when hook not present"() {
        when:
        executor.executeAfterBuildHook(buildStepSpec)

        then:
        noExceptionThrown()
    }

    def "executeHook executes action with null parameter"() {
        given:
        def receivedParam = 'notNull'
        def hook = { param -> receivedParam = param } as Action<Void>

        when:
        executor.executeHook(hook)

        then:
        receivedParam == null
    }

    // ===== EXECUTE TESTS =====

    def "execute validates buildSpec"() {
        given:
        def context = PipelineContext.create('test')

        when:
        executor.execute(null, context)

        then:
        thrown(GradleException)
    }

    def "execute throws exception when build task not found"() {
        given:
        def context = PipelineContext.create('test')

        when:
        executor.execute(buildStepSpec, context)

        then:
        def e = thrown(GradleException)
        e.message.contains('not found')
    }

    def "execute calls hooks and task in correct order"() {
        given:
        def executionOrder = []

        def beforeHook = { executionOrder << 'before' } as Action<Void>
        def afterHook = { executionOrder << 'after' } as Action<Void>
        buildStepSpec.beforeBuild.set(beforeHook)
        buildStepSpec.afterBuild.set(afterHook)

        project.tasks.create('dockerBuildTestImage') {
            doLast { executionOrder << 'task' }
        }

        def context = PipelineContext.create('test')

        when:
        executor.execute(buildStepSpec, context)

        then:
        executionOrder == ['before', 'task', 'after']
    }

    def "execute returns context with built image"() {
        given:
        project.tasks.create('dockerBuildTestImage')
        def context = PipelineContext.create('test')

        when:
        def result = executor.execute(buildStepSpec, context)

        then:
        result.builtImage == imageSpec
        result.buildCompleted
    }

    def "execute preserves existing context data"() {
        given:
        project.tasks.create('dockerBuildTestImage')
        def context = PipelineContext.create('myPipeline')
            .withMetadata('key', 'value')

        when:
        def result = executor.execute(buildStepSpec, context)

        then:
        result.pipelineName == 'myPipeline'
        result.metadata['key'] == 'value'
        result.builtImage == imageSpec
    }

    def "execute works without hooks"() {
        given:
        project.tasks.create('dockerBuildTestImage')
        def context = PipelineContext.create('test')

        when:
        def result = executor.execute(buildStepSpec, context)

        then:
        result.buildCompleted
        result.builtImage == imageSpec
    }

    // ===== ERROR HANDLING TESTS =====

    def "execute propagates hook exceptions"() {
        given:
        def hook = { throw new RuntimeException('Hook failed') } as Action<Void>
        buildStepSpec.beforeBuild.set(hook)
        project.tasks.create('dockerBuildTestImage')
        def context = PipelineContext.create('test')

        when:
        executor.execute(buildStepSpec, context)

        then:
        def e = thrown(RuntimeException)
        e.message == 'Hook failed'
    }

    def "execute propagates task action exceptions"() {
        given:
        project.tasks.create('dockerBuildTestImage') {
            doLast { throw new RuntimeException('Task failed') }
        }
        def context = PipelineContext.create('test')

        when:
        executor.execute(buildStepSpec, context)

        then:
        def e = thrown(RuntimeException)
        e.message == 'Task failed'
    }

    // ===== DIFFERENT IMAGE NAMES TESTS =====

    def "execute works with various image names"() {
        given:
        def imgSpec = project.objects.newInstance(
            ImageSpec,
            imgName,
            project.objects,
            project.providers,
            project.layout
        )
        def spec = project.objects.newInstance(BuildStepSpec)
        spec.image.set(imgSpec)
        project.tasks.create("dockerBuild${imgName.capitalize()}")
        def context = PipelineContext.create('test')

        when:
        def result = executor.execute(spec, context)

        then:
        result.builtImage == imgSpec

        where:
        imgName << ['app', 'myService', 'webApi']
    }
}
