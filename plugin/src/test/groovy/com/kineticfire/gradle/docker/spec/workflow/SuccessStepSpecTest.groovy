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

    // ===== DSL METHOD TESTS =====

    def "save closure creates and configures SaveSpec"() {
        when:
        successStepSpec.save {
            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
        }

        then:
        successStepSpec.save.present
        successStepSpec.save.get().compression.get() == com.kineticfire.gradle.docker.model.SaveCompression.GZIP
    }

    def "save action creates and configures SaveSpec"() {
        when:
        successStepSpec.save({ saveSpec ->
            saveSpec.compression.set(com.kineticfire.gradle.docker.model.SaveCompression.BZIP2)
        } as org.gradle.api.Action)

        then:
        successStepSpec.save.present
        successStepSpec.save.get().compression.get() == com.kineticfire.gradle.docker.model.SaveCompression.BZIP2
    }

    def "save closure sets outputFile correctly"() {
        when:
        successStepSpec.save {
            outputFile.set(project.layout.buildDirectory.file('images/my-image.tar'))
        }

        then:
        successStepSpec.save.present
        successStepSpec.save.get().outputFile.get().asFile.path.endsWith('images/my-image.tar')
    }

    def "publish closure creates and configures PublishSpec"() {
        when:
        successStepSpec.publish {
            publishTags.set(['latest', 'v1.0'])
        }

        then:
        successStepSpec.publish.present
        successStepSpec.publish.get().publishTags.get() == ['latest', 'v1.0']
    }

    def "publish action creates and configures PublishSpec"() {
        when:
        successStepSpec.publish({ publishSpec ->
            publishSpec.publishTags.set(['prod'])
        } as org.gradle.api.Action)

        then:
        successStepSpec.publish.present
        successStepSpec.publish.get().publishTags.get() == ['prod']
    }

    def "publish closure with targets configures correctly"() {
        when:
        successStepSpec.publish {
            to('production') {
                registry.set('ghcr.io')
                namespace.set('myorg')
            }
        }

        then:
        successStepSpec.publish.present
        successStepSpec.publish.get().to.size() == 1
        successStepSpec.publish.get().to.getByName('production').registry.get() == 'ghcr.io'
        successStepSpec.publish.get().to.getByName('production').namespace.get() == 'myorg'
    }

    def "combined save and publish DSL works"() {
        when:
        successStepSpec.save {
            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.XZ)
        }
        successStepSpec.publish {
            publishTags.set(['release'])
        }

        then:
        successStepSpec.save.present
        successStepSpec.publish.present
        successStepSpec.save.get().compression.get() == com.kineticfire.gradle.docker.model.SaveCompression.XZ
        successStepSpec.publish.get().publishTags.get() == ['release']
    }

    def "save DSL can be called multiple times - last wins"() {
        when:
        successStepSpec.save {
            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
        }
        successStepSpec.save {
            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.NONE)
        }

        then:
        successStepSpec.save.present
        successStepSpec.save.get().compression.get() == com.kineticfire.gradle.docker.model.SaveCompression.NONE
    }

    def "publish DSL can be called multiple times - last wins"() {
        when:
        successStepSpec.publish {
            publishTags.set(['first'])
        }
        successStepSpec.publish {
            publishTags.set(['second'])
        }

        then:
        successStepSpec.publish.present
        successStepSpec.publish.get().publishTags.get() == ['second']
    }

    def "publish closure with multiple targets works correctly"() {
        when:
        successStepSpec.publish {
            to('staging') {
                registry.set('staging.example.com')
            }
            to('production') {
                registry.set('prod.example.com')
            }
        }

        then:
        successStepSpec.publish.present
        successStepSpec.publish.get().to.size() == 2
        successStepSpec.publish.get().to.getByName('staging').registry.get() == 'staging.example.com'
        successStepSpec.publish.get().to.getByName('production').registry.get() == 'prod.example.com'
    }

    def "combined DSL with additionalTags and hooks works correctly"() {
        given:
        def hookCalled = false

        when:
        successStepSpec.additionalTags.set(['verified', 'stable'])
        successStepSpec.save {
            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
        }
        successStepSpec.publish {
            to('release') {
                registry.set('registry.example.com')
            }
        }
        successStepSpec.afterSuccess.set({ hookCalled = true } as org.gradle.api.Action<Void>)

        then:
        successStepSpec.additionalTags.get() == ['verified', 'stable']
        successStepSpec.save.present
        successStepSpec.publish.present
        successStepSpec.afterSuccess.present

        when:
        successStepSpec.afterSuccess.get().execute(null)

        then:
        hookCalled
    }
}
