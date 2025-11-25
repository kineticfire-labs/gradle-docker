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

package com.kineticfire.gradle.docker.spec.workflow

import com.kineticfire.gradle.docker.spec.ImageSpec
import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for BuildStepSpec
 */
class BuildStepSpecTest extends Specification {

    def project
    def buildStepSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        buildStepSpec = project.objects.newInstance(BuildStepSpec, project.objects)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        buildStepSpec != null
        buildStepSpec.buildArgs.present
        buildStepSpec.buildArgs.get() == [:]
    }

    // ===== PROPERTY TESTS =====

    def "image property works correctly"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects)

        when:
        buildStepSpec.image.set(imageSpec)

        then:
        buildStepSpec.image.present
        buildStepSpec.image.get() == imageSpec
    }

    def "buildArgs property works correctly"() {
        given:
        def args = [ARG1: 'value1', ARG2: 'value2']

        when:
        buildStepSpec.buildArgs.set(args)

        then:
        buildStepSpec.buildArgs.present
        buildStepSpec.buildArgs.get() == args
    }

    def "buildArgs has default empty map"() {
        expect:
        buildStepSpec.buildArgs.present
        buildStepSpec.buildArgs.get() == [:]
    }

    def "buildArgs can be added incrementally"() {
        when:
        buildStepSpec.buildArgs.put('KEY1', 'value1')
        buildStepSpec.buildArgs.put('KEY2', 'value2')

        then:
        buildStepSpec.buildArgs.get() == [KEY1: 'value1', KEY2: 'value2']
    }

    def "beforeBuild hook property works correctly"() {
        given:
        def hookCalled = false
        def hook = { hookCalled = true } as Action<Void>

        when:
        buildStepSpec.beforeBuild.set(hook)

        then:
        buildStepSpec.beforeBuild.present
        buildStepSpec.beforeBuild.get() != null

        when:
        buildStepSpec.beforeBuild.get().execute(null)

        then:
        hookCalled
    }

    def "afterBuild hook property works correctly"() {
        given:
        def hookCalled = false
        def hook = { hookCalled = true } as Action<Void>

        when:
        buildStepSpec.afterBuild.set(hook)

        then:
        buildStepSpec.afterBuild.present
        buildStepSpec.afterBuild.get() != null

        when:
        buildStepSpec.afterBuild.get().execute(null)

        then:
        hookCalled
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'myApp', project.objects)
        def args = [VERSION: '1.0', ENV: 'prod']
        def beforeHookCalled = false
        def afterHookCalled = false
        def beforeHook = { beforeHookCalled = true } as Action<Void>
        def afterHook = { afterHookCalled = true } as Action<Void>

        when:
        buildStepSpec.image.set(imageSpec)
        buildStepSpec.buildArgs.set(args)
        buildStepSpec.beforeBuild.set(beforeHook)
        buildStepSpec.afterBuild.set(afterHook)

        then:
        buildStepSpec.image.get() == imageSpec
        buildStepSpec.buildArgs.get() == args
        buildStepSpec.beforeBuild.present
        buildStepSpec.afterBuild.present

        when:
        buildStepSpec.beforeBuild.get().execute(null)
        buildStepSpec.afterBuild.get().execute(null)

        then:
        beforeHookCalled
        afterHookCalled
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        given:
        def initialImage = project.objects.newInstance(ImageSpec, 'initial', project.objects)
        def updatedImage = project.objects.newInstance(ImageSpec, 'updated', project.objects)
        def initialArgs = [KEY1: 'value1']
        def updatedArgs = [KEY2: 'value2']

        when:
        buildStepSpec.image.set(initialImage)
        buildStepSpec.buildArgs.set(initialArgs)

        then:
        buildStepSpec.image.get() == initialImage
        buildStepSpec.buildArgs.get() == initialArgs

        when:
        buildStepSpec.image.set(updatedImage)
        buildStepSpec.buildArgs.set(updatedArgs)

        then:
        buildStepSpec.image.get() == updatedImage
        buildStepSpec.buildArgs.get() == updatedArgs
    }

    def "buildArgs can be cleared and reset"() {
        given:
        buildStepSpec.buildArgs.put('KEY1', 'value1')
        buildStepSpec.buildArgs.put('KEY2', 'value2')

        when:
        buildStepSpec.buildArgs.set([:])

        then:
        buildStepSpec.buildArgs.get() == [:]

        when:
        buildStepSpec.buildArgs.put('NEW_KEY', 'new_value')

        then:
        buildStepSpec.buildArgs.get() == [NEW_KEY: 'new_value']
    }

    // ===== EDGE CASES =====

    def "multiple hooks can be set and replaced"() {
        given:
        def firstHookCalled = false
        def secondHookCalled = false
        def firstHook = { firstHookCalled = true } as Action<Void>
        def secondHook = { secondHookCalled = true } as Action<Void>

        when:
        buildStepSpec.beforeBuild.set(firstHook)
        buildStepSpec.beforeBuild.get().execute(null)

        then:
        firstHookCalled
        !secondHookCalled

        when:
        buildStepSpec.beforeBuild.set(secondHook)
        buildStepSpec.beforeBuild.get().execute(null)

        then:
        secondHookCalled
    }

    def "buildArgs accepts various data types as strings"() {
        given:
        def args = [
            STRING_ARG: 'string',
            NUMBER_ARG: '123',
            BOOL_ARG: 'true',
            EMPTY_ARG: ''
        ]

        when:
        buildStepSpec.buildArgs.set(args)

        then:
        buildStepSpec.buildArgs.get() == args
        buildStepSpec.buildArgs.get()['STRING_ARG'] == 'string'
        buildStepSpec.buildArgs.get()['NUMBER_ARG'] == '123'
        buildStepSpec.buildArgs.get()['BOOL_ARG'] == 'true'
        buildStepSpec.buildArgs.get()['EMPTY_ARG'] == ''
    }

    def "image property can reference different ImageSpec instances"() {
        given:
        def image1 = project.objects.newInstance(ImageSpec, 'app1', project.objects)
        def image2 = project.objects.newInstance(ImageSpec, 'app2', project.objects)

        expect:
        [image1, image2].each { img ->
            buildStepSpec.image.set(img)
            assert buildStepSpec.image.get() == img
        }
    }

    def "hooks are independent of each other"() {
        given:
        def beforeCalled = false
        def afterCalled = false
        def beforeHook = { beforeCalled = true } as Action<Void>
        def afterHook = { afterCalled = true } as Action<Void>

        when:
        buildStepSpec.beforeBuild.set(beforeHook)
        buildStepSpec.afterBuild.set(afterHook)
        buildStepSpec.beforeBuild.get().execute(null)

        then:
        beforeCalled
        !afterCalled

        when:
        buildStepSpec.afterBuild.get().execute(null)

        then:
        afterCalled
    }
}
