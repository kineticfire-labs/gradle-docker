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

import com.kineticfire.gradle.docker.spec.PublishSpec
import com.kineticfire.gradle.docker.spec.SaveSpec
import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for SuccessStepSpec
 */
class SuccessStepSpecTest extends Specification {

    def project
    def successStepSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        successStepSpec = project.objects.newInstance(SuccessStepSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        successStepSpec != null
        successStepSpec.additionalTags.present
        successStepSpec.additionalTags.get() == []
    }

    // ===== PROPERTY TESTS =====

    def "additionalTags property works correctly"() {
        given:
        def tags = ['stable', 'production']

        when:
        successStepSpec.additionalTags.set(tags)

        then:
        successStepSpec.additionalTags.present
        successStepSpec.additionalTags.get() == tags
    }

    def "additionalTags has default empty list"() {
        expect:
        successStepSpec.additionalTags.present
        successStepSpec.additionalTags.get() == []
    }

    def "additionalTags can be added incrementally"() {
        when:
        successStepSpec.additionalTags.add('tag1')
        successStepSpec.additionalTags.add('tag2')

        then:
        successStepSpec.additionalTags.get() == ['tag1', 'tag2']
    }

    def "save property works correctly"() {
        given:
        def saveSpec = project.objects.newInstance(SaveSpec)

        when:
        successStepSpec.save.set(saveSpec)

        then:
        successStepSpec.save.present
        successStepSpec.save.get() == saveSpec
    }

    def "publish property works correctly"() {
        given:
        def publishSpec = project.objects.newInstance(PublishSpec)

        when:
        successStepSpec.publish.set(publishSpec)

        then:
        successStepSpec.publish.present
        successStepSpec.publish.get() == publishSpec
    }

    def "afterSuccess hook property works correctly"() {
        given:
        def hookCalled = false
        def hook = { hookCalled = true } as Action<Void>

        when:
        successStepSpec.afterSuccess.set(hook)

        then:
        successStepSpec.afterSuccess.present
        successStepSpec.afterSuccess.get() != null

        when:
        successStepSpec.afterSuccess.get().execute(null)

        then:
        hookCalled
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def tags = ['latest', 'v1.0', 'stable']
        def saveSpec = project.objects.newInstance(SaveSpec)
        def publishSpec = project.objects.newInstance(PublishSpec)
        def hookCalled = false
        def hook = { hookCalled = true } as Action<Void>

        when:
        successStepSpec.additionalTags.set(tags)
        successStepSpec.save.set(saveSpec)
        successStepSpec.publish.set(publishSpec)
        successStepSpec.afterSuccess.set(hook)

        then:
        successStepSpec.additionalTags.get() == tags
        successStepSpec.save.get() == saveSpec
        successStepSpec.publish.get() == publishSpec
        successStepSpec.afterSuccess.present

        when:
        successStepSpec.afterSuccess.get().execute(null)

        then:
        hookCalled
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        given:
        def initialTags = ['v1.0']
        def updatedTags = ['v2.0', 'latest']

        when:
        successStepSpec.additionalTags.set(initialTags)

        then:
        successStepSpec.additionalTags.get() == initialTags

        when:
        successStepSpec.additionalTags.set(updatedTags)

        then:
        successStepSpec.additionalTags.get() == updatedTags
    }

    def "additionalTags can be cleared and reset"() {
        given:
        successStepSpec.additionalTags.add('tag1')
        successStepSpec.additionalTags.add('tag2')

        when:
        successStepSpec.additionalTags.set([])

        then:
        successStepSpec.additionalTags.get() == []

        when:
        successStepSpec.additionalTags.add('new_tag')

        then:
        successStepSpec.additionalTags.get() == ['new_tag']
    }

    // ===== EDGE CASES =====

    def "multiple tags with various naming conventions work correctly"() {
        given:
        def tags = ['v1.0.0', 'latest', 'stable-2025', 'rc1', 'snapshot', 'feature_x']

        when:
        successStepSpec.additionalTags.set(tags)

        then:
        successStepSpec.additionalTags.get() == tags
        successStepSpec.additionalTags.get().size() == 6
    }

    def "save and publish can be configured independently"() {
        given:
        def saveSpec = project.objects.newInstance(SaveSpec)

        when:
        successStepSpec.save.set(saveSpec)

        then:
        successStepSpec.save.present
        !successStepSpec.publish.present

        when:
        def publishSpec = project.objects.newInstance(PublishSpec)
        successStepSpec.publish.set(publishSpec)

        then:
        successStepSpec.save.present
        successStepSpec.publish.present
    }

    def "hook can be replaced after initial setting"() {
        given:
        def firstHookCalled = false
        def secondHookCalled = false
        def firstHook = { firstHookCalled = true } as Action<Void>
        def secondHook = { secondHookCalled = true } as Action<Void>

        when:
        successStepSpec.afterSuccess.set(firstHook)
        successStepSpec.afterSuccess.get().execute(null)

        then:
        firstHookCalled
        !secondHookCalled

        when:
        successStepSpec.afterSuccess.set(secondHook)
        successStepSpec.afterSuccess.get().execute(null)

        then:
        secondHookCalled
    }

    def "empty tags list is valid configuration"() {
        when:
        successStepSpec.additionalTags.set([])

        then:
        successStepSpec.additionalTags.present
        successStepSpec.additionalTags.get() == []
    }

    def "single tag configuration works correctly"() {
        when:
        successStepSpec.additionalTags.set(['production'])

        then:
        successStepSpec.additionalTags.get() == ['production']
        successStepSpec.additionalTags.get().size() == 1
    }
}
