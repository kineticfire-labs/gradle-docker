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

package com.kineticfire.gradle.docker.spec

import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ImageSpec
 */
class ImageSpecTest extends Specification {

    def project
    def imageSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with name and project"() {
        expect:
        imageSpec != null
        imageSpec.name == 'testImage'
    }

    def "constructor with different names"() {
        given:
        def image1 = project.objects.newInstance(ImageSpec, 'webapp', project)
        def image2 = project.objects.newInstance(ImageSpec, 'api', project)

        expect:
        image1.name == 'webapp'
        image2.name == 'api'
    }

    // ===== BASIC PROPERTY TESTS =====

    def "context property works correctly"() {
        given:
        def contextDir = project.file('.')

        when:
        imageSpec.context.set(contextDir)

        then:
        imageSpec.context.present
        imageSpec.context.get().asFile == contextDir
    }

    def "dockerfile property works correctly"() {
        given:
        def dockerFile = project.file('Dockerfile')

        when:
        imageSpec.dockerfile.set(dockerFile)

        then:
        imageSpec.dockerfile.present
        imageSpec.dockerfile.get().asFile == dockerFile
    }

    def "dockerfileName property works correctly"() {
        when:
        imageSpec.dockerfileName.set('Dockerfile.dev')

        then:
        imageSpec.dockerfileName.present
        imageSpec.dockerfileName.get() == 'Dockerfile.dev'
    }

    def "dockerfileName property handles different name formats"() {
        expect:
        imageSpec.dockerfileName.set(name)
        imageSpec.dockerfileName.get() == name

        where:
        name << [
            'Dockerfile.prod',
            'Dockerfile.test', 
            'MyDockerfile',
            'app.dockerfile',
            'Dockerfile-alpine'
        ]
    }

    def "dockerfileName property is optional"() {
        expect:
        !imageSpec.dockerfileName.present
        imageSpec.dockerfileName.getOrElse('Dockerfile') == 'Dockerfile'
    }

    def "buildArgs property works correctly"() {
        when:
        imageSpec.buildArgs.set(['VERSION': '1.0', 'ENVIRONMENT': 'production'])

        then:
        imageSpec.buildArgs.present
        imageSpec.buildArgs.get() == ['VERSION': '1.0', 'ENVIRONMENT': 'production']
    }

    def "tags property works correctly"() {
        when:
        imageSpec.tags.set(['latest', 'v1.0', 'stable'])

        then:
        imageSpec.tags.present
        imageSpec.tags.get() == ['latest', 'v1.0', 'stable']
    }

    def "sourceRef property works correctly"() {
        when:
        imageSpec.sourceRef.set('docker.io/library/alpine:3.16')

        then:
        imageSpec.sourceRef.present
        imageSpec.sourceRef.get() == 'docker.io/library/alpine:3.16'
    }

    // ===== SAVE CONFIGURATION TESTS =====

    def "save(Closure) configures save spec"() {
        given:
        def myOutputFile = project.file('myapp.tar')

        when:
        imageSpec.save {
            outputFile.set(myOutputFile)
            compression.set('gzip')
        }

        then:
        imageSpec.save.present
        imageSpec.save.get().outputFile.get().asFile == myOutputFile
        imageSpec.save.get().compression.get() == SaveCompression.GZIP
    }

    def "save(Action) configures save spec"() {
        given:
        def outputFile = project.file('output.tar.gz')

        when:
        imageSpec.save(new Action<SaveSpec>() {
            @Override
            void execute(SaveSpec saveSpec) {
                saveSpec.outputFile.set(outputFile)
                saveSpec.compression.set('bzip2')
            }
        })

        then:
        imageSpec.save.present
        imageSpec.save.get().outputFile.get().asFile == outputFile
        imageSpec.save.get().compression.get() == SaveCompression.BZIP2
    }

    def "save configuration requires compression parameter"() {
        when:
        imageSpec.save {
            outputFile.set(project.file('test.tar'))
            // Missing compression parameter
        }

        then:
        imageSpec.save.present
        imageSpec.save.get().compression.present  // Has convention value
        imageSpec.save.get().compression.get() == SaveCompression.NONE  // Default value
    }

    def "SaveCompression enum can be used directly in save configuration"() {
        given:
        def myOutputFile = project.file('myapp.tar')

        when:
        imageSpec.save {
            outputFile.set(myOutputFile)
            compression.set(SaveCompression.GZIP)
        }

        then:
        imageSpec.save.present
        imageSpec.save.get().outputFile.get().asFile == myOutputFile
        imageSpec.save.get().compression.get() == SaveCompression.GZIP
    }

    def "SaveCompression enum values are accessible"() {
        expect:
        SaveCompression.NONE != null
        SaveCompression.GZIP != null
        SaveCompression.BZIP2 != null
        SaveCompression.XZ != null
        SaveCompression.ZIP != null
    }

    // ===== PUBLISH CONFIGURATION TESTS =====

    def "publish(Closure) configures publish spec"() {
        when:
        imageSpec.publish {
            to('dockerhub') {
                tags.set(['latest', 'v1.0'])
                auth {
                    username.set('myuser')
                    password.set('mypass')
                }
            }
        }

        then:
        imageSpec.publish.present
        imageSpec.publish.get().to.size() == 1
        
        def target = imageSpec.publish.get().to.getByName('dockerhub')
        target.tags.get() == ['latest', 'v1.0']
        target.auth.present
        target.auth.get().username.get() == 'myuser'
        target.auth.get().password.get() == 'mypass'
    }

    def "publish(Action) configures publish spec"() {
        when:
        imageSpec.publish(new Action<PublishSpec>() {
            @Override
            void execute(PublishSpec publishSpec) {
                publishSpec.to('quay') {
                    tags.set(['production'])
                }
            }
        })

        then:
        imageSpec.publish.present
        imageSpec.publish.get().to.size() == 1
        
        def target = imageSpec.publish.get().to.getByName('quay')
        target.tags.get() == ['production']
    }

    def "publish with multiple targets"() {
        when:
        imageSpec.publish {
            to('dockerhub') {
                tags.set(['latest'])
            }
            to('ghcr') {
                tags.set(['main'])
            }
        }

        then:
        imageSpec.publish.present
        imageSpec.publish.get().to.size() == 2
        imageSpec.publish.get().to.getByName('dockerhub').tags.get() == ['latest']
        imageSpec.publish.get().to.getByName('ghcr').tags.get() == ['main']
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def contextDir = project.file('build-context')
        def dockerFile = project.file('custom.Dockerfile')
        def saveFile = project.file('myapp-backup.tar.gz')

        when:
        imageSpec.context.set(contextDir)
        imageSpec.dockerfile.set(dockerFile)
        imageSpec.buildArgs.set(['BUILD_DATE': '2023-01-01', 'VERSION': '1.2.3'])
        imageSpec.tags.set(['1.2.3', 'latest'])
        imageSpec.sourceRef.set('node:18-alpine')
        imageSpec.save {
            outputFile.set(saveFile)
            compression.set('bzip2')
        }
        imageSpec.publish {
            to('production') {
                tags.set(['1.2.3', 'stable'])
                auth {
                    registryToken.set('prod-token-123')
                    // serverAddress removed - extracted automatically from image reference
                }
            }
        }

        then:
        imageSpec.name == 'testImage'
        imageSpec.context.get().asFile == contextDir
        imageSpec.dockerfile.get().asFile == dockerFile
        imageSpec.buildArgs.get() == ['BUILD_DATE': '2023-01-01', 'VERSION': '1.2.3']
        imageSpec.tags.get() == ['1.2.3', 'latest']
        imageSpec.sourceRef.get() == 'node:18-alpine'
        imageSpec.save.present
        imageSpec.publish.present
    }

    // ===== OPTIONAL PROPERTIES TESTS =====

    def "optional properties can be unset"() {
        expect:
        imageSpec.context.present  // Has convention value
        !imageSpec.dockerfile.present
        imageSpec.sourceRef.present  // Has convention value (empty string)
        !imageSpec.save.present
        !imageSpec.publish.present
    }

    def "buildArgs and tags are initially empty"() {
        expect:
        // Properties are created but have empty default values
        imageSpec.buildArgs.get().isEmpty()
        imageSpec.tags.get().isEmpty()
    }

    // ===== EDGE CASES =====

    def "nested specs can be reconfigured"() {
        given:
        def file1 = project.file('save1.tar')
        def file2 = project.file('save2.tar')

        when:
        imageSpec.save {
            outputFile.set(file1)
            compression.set('gzip')
        }

        then:
        imageSpec.save.get().outputFile.get().asFile == file1
        imageSpec.save.get().compression.get() == SaveCompression.GZIP

        when:
        imageSpec.save {
            outputFile.set(file2)
            compression.set('none')
        }

        then:
        imageSpec.save.get().outputFile.get().asFile == file2
        imageSpec.save.get().compression.get() == SaveCompression.NONE
    }

    def "empty collections are supported"() {
        when:
        imageSpec.buildArgs.set([:])
        imageSpec.tags.set([])

        then:
        imageSpec.buildArgs.get().isEmpty()
        imageSpec.tags.get().isEmpty()
    }

    def "properties can be updated after initial configuration"() {
        given:
        def initialContext = project.file('initial')
        def updatedContext = project.file('updated')

        when:
        imageSpec.context.set(initialContext)
        imageSpec.tags.set(['v1.0'])

        then:
        imageSpec.context.get().asFile == initialContext
        imageSpec.tags.get() == ['v1.0']

        when:
        imageSpec.context.set(updatedContext)
        imageSpec.tags.set(['v2.0', 'latest'])

        then:
        imageSpec.context.get().asFile == updatedContext
        imageSpec.tags.get() == ['v2.0', 'latest']
    }

    // ===== NEW CONTEXT TASK TESTS =====

    def "contextTask property works correctly"() {
        given:
        def copyTask = project.tasks.register('testContextTask', org.gradle.api.tasks.Copy)

        when:
        imageSpec.contextTask = copyTask

        then:
        imageSpec.contextTask != null
        imageSpec.contextTask == copyTask
    }

    def "context(Closure) creates Copy task with correct configuration"() {
        given:
        def srcDir = project.file('src')
        def libsDir = project.file('libs')

        when:
        imageSpec.context {
            from(srcDir)
            from(libsDir) {
                include '*.jar'
            }
        }

        then:
        imageSpec.contextTask != null
        def copyTask = imageSpec.contextTask.get()
        copyTask.name == 'prepareTestImageContext'
        copyTask.group == 'docker'
        copyTask.description.contains('Prepare build context for Docker image')
    }

    def "context(Action) creates Copy task with correct configuration"() {
        given:
        def srcDir = project.file('src')

        when:
        imageSpec.context(new org.gradle.api.Action<org.gradle.api.tasks.Copy>() {
            @Override
            void execute(org.gradle.api.tasks.Copy task) {
                task.from(srcDir)
                task.include('**/*.txt')
            }
        })

        then:
        imageSpec.contextTask != null
        def copyTask = imageSpec.contextTask.get()
        copyTask.name == 'prepareTestImageContext'
        copyTask.group == 'docker'
        copyTask.description.contains('Prepare build context for Docker image')
    }

    def "context method overrides previous contextTask"() {
        given:
        def manualTask = project.tasks.register('manualContext', org.gradle.api.tasks.Copy)
        def srcDir = project.file('src')

        when:
        imageSpec.contextTask = manualTask

        then:
        imageSpec.contextTask.get() == manualTask.get()

        when:
        imageSpec.context {
            from(srcDir)
        }

        then:
        imageSpec.contextTask != null
        imageSpec.contextTask != manualTask
        imageSpec.contextTask.get().name == 'prepareTestImageContext'
    }

    def "multiple context configurations create separate tasks"() {
        given:
        def image1 = project.objects.newInstance(ImageSpec, 'webapp', project)
        def image2 = project.objects.newInstance(ImageSpec, 'api', project)
        def srcDir = project.file('src')

        when:
        image1.context { from(srcDir) }
        image2.context { from(srcDir) }

        then:
        image1.contextTask != null
        image2.contextTask != null
        image1.contextTask.get().name == 'prepareWebappContext'
        image2.contextTask.get().name == 'prepareApiContext'
        image1.contextTask.get() != image2.contextTask.get()
    }

    // ===== IMAGE-LEVEL PULLIFMISSING TESTS =====

    def "pullIfMissing property has correct default"() {
        expect:
        imageSpec.pullIfMissing.present
        imageSpec.pullIfMissing.get() == false
    }

    def "pullIfMissing property can be configured"() {
        when:
        imageSpec.pullIfMissing.set(true)

        then:
        imageSpec.pullIfMissing.get() == true

        when:
        imageSpec.pullIfMissing.set(false)

        then:
        imageSpec.pullIfMissing.get() == false
    }

    def "pullAuth property can be configured via DSL"() {
        when:
        imageSpec.pullAuth {
            username.set("testuser")
            password.set("testpass")
            registryToken.set("token123")
        }

        then:
        imageSpec.pullAuth != null
        def authSpec = imageSpec.pullAuth
        authSpec.username.get() == "testuser"
        authSpec.password.get() == "testpass"
        authSpec.registryToken.get() == "token123"
    }

    def "pullAuth property can be configured via Action"() {
        when:
        imageSpec.pullAuth(new Action<AuthSpec>() {
            @Override
            void execute(AuthSpec authSpec) {
                authSpec.username.set("actionuser")
                authSpec.password.set("actionpass")
            }
        })

        then:
        imageSpec.pullAuth != null
        def authSpec = imageSpec.pullAuth
        authSpec.username.get() == "actionuser"
        authSpec.password.get() == "actionpass"
    }

    // ===== SOURCEREF COMPONENT ASSEMBLY TESTS =====

    def "sourceRef component properties have correct defaults"() {
        expect:
        imageSpec.sourceRefRegistry.present
        imageSpec.sourceRefRegistry.get() == ""
        imageSpec.sourceRefNamespace.present
        imageSpec.sourceRefNamespace.get() == ""
        imageSpec.sourceRefRepository.present
        imageSpec.sourceRefRepository.get() == ""
        imageSpec.sourceRefImageName.present
        imageSpec.sourceRefImageName.get() == ""
        imageSpec.sourceRefTag.present
        imageSpec.sourceRefTag.get() == ""
    }

    def "sourceRef component properties can be configured"() {
        when:
        imageSpec.sourceRefRegistry.set("docker.io")
        imageSpec.sourceRefNamespace.set("library")
        imageSpec.sourceRefRepository.set("myorg/myapp")
        imageSpec.sourceRefImageName.set("alpine")
        imageSpec.sourceRefTag.set("3.18")

        then:
        imageSpec.sourceRefRegistry.get() == "docker.io"
        imageSpec.sourceRefNamespace.get() == "library"
        imageSpec.sourceRefRepository.get() == "myorg/myapp"
        imageSpec.sourceRefImageName.get() == "alpine"
        imageSpec.sourceRefTag.get() == "3.18"
    }

    def "sourceRef component properties old test can be configured"() {
        when:
        imageSpec.sourceRefRegistry.set("docker.io")
        imageSpec.sourceRefNamespace.set("library")
        imageSpec.sourceRefImageName.set("alpine")
        imageSpec.sourceRefTag.set("3.18")

        then:
        imageSpec.sourceRefRegistry.get() == "docker.io"
        imageSpec.sourceRefNamespace.get() == "library"
        imageSpec.sourceRefImageName.get() == "alpine"
        imageSpec.sourceRefTag.get() == "3.18"
    }

    def "sourceRef builder method sets all components"() {
        when:
        imageSpec.sourceRef("ghcr.io", "company", "myapp", "v1.0")

        then:
        imageSpec.sourceRefRegistry.get() == "ghcr.io"
        imageSpec.sourceRefNamespace.get() == "company"
        imageSpec.sourceRefImageName.get() == "myapp"
        imageSpec.sourceRefTag.get() == "v1.0"
    }

    def "getEffectiveSourceRef returns direct sourceRef when present"() {
        when:
        imageSpec.sourceRef.set("docker.io/library/nginx:latest")

        then:
        imageSpec.getEffectiveSourceRef() == "docker.io/library/nginx:latest"
    }

    def "getEffectiveSourceRef assembles from namespace+imageName when sourceRef is empty"() {
        when:
        imageSpec.sourceRefRegistry.set("docker.io")
        imageSpec.sourceRefNamespace.set("library")
        imageSpec.sourceRefImageName.set("alpine")
        imageSpec.sourceRefTag.set("3.18")

        then:
        imageSpec.getEffectiveSourceRef() == "docker.io/library/alpine:3.18"
    }

    def "getEffectiveSourceRef assembles from repository approach when present"() {
        when:
        imageSpec.sourceRefRegistry.set("docker.io")
        imageSpec.sourceRefRepository.set("myorg/myapp")
        imageSpec.sourceRefTag.set("v1.0")

        then:
        imageSpec.getEffectiveSourceRef() == "docker.io/myorg/myapp:v1.0"
    }

    def "getEffectiveSourceRef repository approach takes precedence over namespace+imageName"() {
        when:
        imageSpec.sourceRefRegistry.set("docker.io")
        imageSpec.sourceRefRepository.set("company/webapp")
        imageSpec.sourceRefNamespace.set("library")
        imageSpec.sourceRefImageName.set("alpine")
        imageSpec.sourceRefTag.set("latest")

        then:
        imageSpec.getEffectiveSourceRef() == "docker.io/company/webapp:latest"
    }

    def "getEffectiveSourceRef repository approach without registry"() {
        when:
        imageSpec.sourceRefRepository.set("myuser/myapp")
        imageSpec.sourceRefTag.set("v2.0")

        then:
        imageSpec.getEffectiveSourceRef() == "myuser/myapp:v2.0"
    }

    def "getEffectiveSourceRef repository approach defaults tag to latest"() {
        when:
        imageSpec.sourceRefRepository.set("company/backend")

        then:
        imageSpec.getEffectiveSourceRef() == "company/backend:latest"
    }

    def "getEffectiveSourceRef handles partial component configuration"() {
        when:
        imageSpec.sourceRefImageName.set("nginx")
        imageSpec.sourceRefTag.set("latest")

        then:
        imageSpec.getEffectiveSourceRef() == "nginx:latest"

        when:
        imageSpec.sourceRefRegistry.set("localhost:5000")

        then:
        imageSpec.getEffectiveSourceRef() == "localhost:5000/nginx:latest"
    }

    def "getEffectiveSourceRef handles empty tag correctly"() {
        when:
        imageSpec.sourceRefImageName.set("alpine")
        // tag remains empty

        then:
        imageSpec.getEffectiveSourceRef() == "alpine:latest"
    }

    // ===== VALIDATION TESTS =====

    def "validatePullIfMissingConfiguration throws exception when pullIfMissing=true with build context"() {
        given:
        def contextDir = project.file('build-context')
        contextDir.mkdirs()

        when:
        imageSpec.context.set(contextDir)
        imageSpec.pullIfMissing.set(true)
        imageSpec.validatePullIfMissingConfiguration()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot set pullIfMissing=true when build context is configured")
        ex.message.contains("testImage")
    }

    def "validatePullIfMissingConfiguration succeeds when pullIfMissing=false with build context"() {
        given:
        def contextDir = project.file('build-context')
        contextDir.mkdirs()

        when:
        imageSpec.context.set(contextDir)
        imageSpec.pullIfMissing.set(false)
        imageSpec.validatePullIfMissingConfiguration()

        then:
        noExceptionThrown()
    }

    def "validatePullIfMissingConfiguration succeeds when pullIfMissing=true with sourceRef"() {
        when:
        imageSpec.sourceRef.set("docker.io/library/alpine:latest")
        imageSpec.pullIfMissing.set(true)
        imageSpec.validatePullIfMissingConfiguration()

        then:
        noExceptionThrown()
    }

    def "validateSourceRefConfiguration throws when pullIfMissing=true but no sourceRef"() {
        when:
        imageSpec.pullIfMissing.set(true)
        imageSpec.validateSourceRefConfiguration()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("pullIfMissing=true requires either sourceRef, sourceRefRepository, or sourceRefImageName")
        ex.message.contains("testImage")
    }

    def "validateSourceRefConfiguration succeeds when pullIfMissing=false"() {
        when:
        imageSpec.pullIfMissing.set(false)
        imageSpec.validateSourceRefConfiguration()

        then:
        noExceptionThrown()
    }

    def "validateSourceRefConfiguration succeeds when pullIfMissing=true with sourceRef"() {
        when:
        imageSpec.sourceRef.set("nginx:latest")
        imageSpec.pullIfMissing.set(true)
        imageSpec.validateSourceRefConfiguration()

        then:
        noExceptionThrown()
    }

    def "validateSourceRefConfiguration succeeds when pullIfMissing=true with sourceRefImageName"() {
        when:
        imageSpec.sourceRefImageName.set("alpine")
        imageSpec.pullIfMissing.set(true)
        imageSpec.validateSourceRefConfiguration()

        then:
        noExceptionThrown()
    }

    def "validateSourceRefConfiguration succeeds when pullIfMissing=true with sourceRefRepository"() {
        when:
        imageSpec.sourceRefRepository.set("myorg/myapp")
        imageSpec.pullIfMissing.set(true)
        imageSpec.validateSourceRefConfiguration()

        then:
        noExceptionThrown()
    }

    // ===== MODE CONSISTENCY VALIDATION TESTS =====

    def "validateModeConsistency succeeds with pure Build Mode configuration"() {
        given:
        def contextDir = project.file('build-context')
        contextDir.mkdirs()

        when:
        imageSpec.context.set(contextDir)
        imageSpec.dockerfile.set(project.file('Dockerfile'))
        imageSpec.buildArgs.set(['VERSION': '1.0'])
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds with pure SourceRef Mode - full reference"() {
        when:
        imageSpec.sourceRef.set("docker.io/library/alpine:3.18")
        imageSpec.pullIfMissing.set(true)
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds with pure SourceRef Mode - namespace approach"() {
        when:
        imageSpec.sourceRefRegistry.set("docker.io")
        imageSpec.sourceRefNamespace.set("library")
        imageSpec.sourceRefImageName.set("ubuntu")
        imageSpec.sourceRefTag.set("22.04")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds with pure SourceRef Mode - repository approach"() {
        when:
        imageSpec.sourceRefRegistry.set("ghcr.io")
        imageSpec.sourceRefRepository.set("company/webapp")
        imageSpec.sourceRefTag.set("v1.0")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency throws when mixing Build Mode with SourceRef Mode"() {
        given:
        def contextDir = project.file('build-context')
        contextDir.mkdirs()

        when:
        imageSpec.context.set(contextDir)
        imageSpec.sourceRef.set("alpine:latest")
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateModeConsistency throws when mixing namespace and repository approaches"() {
        when:
        imageSpec.sourceRefNamespace.set("library")
        imageSpec.sourceRefImageName.set("alpine")
        imageSpec.sourceRefRepository.set("myorg/webapp")
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot use both repository approach and namespace+imageName approach")
    }

    def "validateModeConsistency throws when repository approach is incomplete"() {
        when:
        imageSpec.sourceRefRepository.set("company/webapp")
        imageSpec.sourceRefImageName.set("alpine")  // This makes repository approach invalid
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot use both repository approach and namespace+imageName approach")
    }

    def "validateModeConsistency throws when namespace approach is incomplete"() {
        when:
        imageSpec.sourceRefNamespace.set("library")
        // Missing sourceRefImageName makes this invalid
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("When using namespace+imageName approach, both namespace and imageName are required")
        ex.message.contains("testImage")
    }

    def "validatePullIfMissingConfiguration succeeds when pullIfMissing=true with sourceRef"() {
        when:
        imageSpec.sourceRef.set("docker.io/library/alpine:latest")
        imageSpec.pullIfMissing.set(true)
        imageSpec.validatePullIfMissingConfiguration()

        then:
        noExceptionThrown()
    }

    def "validateSourceRefConfiguration throws when pullIfMissing=true but no sourceRef"() {
        when:
        imageSpec.pullIfMissing.set(true)
        imageSpec.validateSourceRefConfiguration()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("pullIfMissing=true requires either sourceRef, sourceRefRepository, or sourceRefImageName")
        ex.message.contains("testImage")
    }

    def "validateSourceRefConfiguration succeeds when pullIfMissing=false"() {
        when:
        imageSpec.pullIfMissing.set(false)
        imageSpec.validateSourceRefConfiguration()

        then:
        noExceptionThrown()
    }

    def "validateSourceRefConfiguration succeeds when pullIfMissing=true with sourceRef"() {
        when:
        imageSpec.sourceRef.set("nginx:latest")
        imageSpec.pullIfMissing.set(true)
        imageSpec.validateSourceRefConfiguration()

        then:
        noExceptionThrown()
    }

    def "validateSourceRefConfiguration succeeds when pullIfMissing=true with sourceRefImageName"() {
        when:
        imageSpec.sourceRefImageName.set("alpine")
        imageSpec.pullIfMissing.set(true)
        imageSpec.validateSourceRefConfiguration()

        then:
        noExceptionThrown()
    }

    // ===== CLOSURE-BASED SOURCEREF DSL TESTS =====

    def "sourceRef closure DSL configures components correctly"() {
        when:
        imageSpec.sourceRef {
            registry "docker.io"
            namespace "library"
            imageName "ubuntu"
            tag "22.04"
        }

        then:
        imageSpec.sourceRefRegistry.get() == "docker.io"
        imageSpec.sourceRefNamespace.get() == "library"
        imageSpec.sourceRefImageName.get() == "ubuntu"
        imageSpec.sourceRefTag.get() == "22.04"
        imageSpec.getEffectiveSourceRef() == "docker.io/library/ubuntu:22.04"
    }

    def "sourceRef closure DSL with partial configuration"() {
        when:
        imageSpec.sourceRef {
            registry "localhost:5000"
            imageName "myapp"
            // namespace and tag omitted
        }

        then:
        imageSpec.sourceRefRegistry.get() == "localhost:5000"
        imageSpec.sourceRefNamespace.get() == ""
        imageSpec.sourceRefImageName.get() == "myapp"
        imageSpec.sourceRefTag.get() == ""
        imageSpec.getEffectiveSourceRef() == "localhost:5000/myapp:latest"
    }

    def "sourceRef closure DSL can be called multiple times"() {
        when:
        imageSpec.sourceRef {
            registry "initial.registry"
            imageName "initial"
        }

        then:
        imageSpec.sourceRefRegistry.get() == "initial.registry"
        imageSpec.sourceRefImageName.get() == "initial"

        when:
        imageSpec.sourceRef {
            registry "updated.registry"
            namespace "updated-ns"
            imageName "updated"
            tag "v2.0"
        }

        then:
        imageSpec.sourceRefRegistry.get() == "updated.registry"
        imageSpec.sourceRefNamespace.get() == "updated-ns"
        imageSpec.sourceRefImageName.get() == "updated"
        imageSpec.sourceRefTag.get() == "v2.0"
        imageSpec.getEffectiveSourceRef() == "updated.registry/updated-ns/updated:v2.0"
    }

    def "sourceRef closure DSL supports repository approach"() {
        when:
        imageSpec.sourceRef {
            registry "docker.io"
            repository "company/backend-api"
            tag "v1.5"
        }

        then:
        imageSpec.sourceRefRegistry.get() == "docker.io"
        imageSpec.sourceRefRepository.get() == "company/backend-api"
        imageSpec.sourceRefTag.get() == "v1.5"
        imageSpec.getEffectiveSourceRef() == "docker.io/company/backend-api:v1.5"
    }

    def "sourceRef closure DSL repository approach without registry"() {
        when:
        imageSpec.sourceRef {
            repository "myuser/myproject"
            tag "stable"
        }

        then:
        imageSpec.sourceRefRepository.get() == "myuser/myproject"
        imageSpec.sourceRefTag.get() == "stable"
        imageSpec.getEffectiveSourceRef() == "myuser/myproject:stable"
    }

    // ===== NULL HANDLING TESTS =====

    def "label helper method handles null value"() {
        when:
        imageSpec.label('MY_LABEL', (String) null)

        then:
        imageSpec.labels.get().isEmpty()
    }

    def "labels provider helper method handles null provider"() {
        when:
        imageSpec.labels((org.gradle.api.provider.Provider) null)

        then:
        imageSpec.labels.get().isEmpty()
    }

    def "buildArg helper method handles null value"() {
        when:
        imageSpec.buildArg('MY_ARG', (String) null)

        then:
        imageSpec.buildArgs.get().isEmpty()
    }

    def "buildArgs provider helper method handles null provider"() {
        when:
        imageSpec.buildArgs((org.gradle.api.provider.Provider) null)

        then:
        imageSpec.buildArgs.get().isEmpty()
    }

    // ===== PULLAUTH EDGE CASES =====

    def "pullAuth closure creates AuthSpec when null"() {
        given:
        imageSpec.pullAuth == null

        when:
        imageSpec.pullAuth {
            username.set "testuser"
            password.set "testpass"
        }

        then:
        imageSpec.pullAuth != null
        imageSpec.pullAuth.username.get() == "testuser"
        imageSpec.pullAuth.password.get() == "testpass"
    }

    def "pullAuth action creates AuthSpec when null"() {
        given:
        imageSpec.pullAuth == null

        when:
        imageSpec.pullAuth(new Action<AuthSpec>() {
            void execute(AuthSpec spec) {
                spec.username.set("actionuser")
                spec.password.set("actionpass")
            }
        })

        then:
        imageSpec.pullAuth != null
        imageSpec.pullAuth.username.get() == "actionuser"
        imageSpec.pullAuth.password.get() == "actionpass"
    }

    // ===== GETEFFECTIVESOURCEREF ERROR CASES =====

    def "getEffectiveSourceRef throws when neither repository nor imageName specified"() {
        when:
        imageSpec.sourceRefRegistry.set("docker.io")
        imageSpec.sourceRefNamespace.set("library")
        // Neither repository nor imageName set
        def result = imageSpec.getEffectiveSourceRef()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Either sourceRef, sourceRefRepository, or sourceRefImageName must be specified")
        ex.message.contains("testImage")
    }

    // ===== VALIDATEPULLIMISSING WITH CONTEXT TASK =====

    def "validatePullIfMissingConfiguration throws when pullIfMissing=true with contextTask"() {
        when:
        imageSpec.contextTask = project.tasks.register("customContextTask")
        imageSpec.pullIfMissing.set(true)
        imageSpec.validatePullIfMissingConfiguration()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot set pullIfMissing=true when build context is configured")
        ex.message.contains("testImage")
    }

    // ===== VALIDATEMMODECONSISTENCY EDGE CASES =====

    def "validateModeConsistency succeeds when only imageName is set without sourceRef components"() {
        when:
        imageSpec.imageName.set("myimage")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds when only namespace is set without sourceRef components"() {
        when:
        imageSpec.namespace.set("myorg")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds when only repository is set without sourceRef components"() {
        when:
        imageSpec.repository.set("myorg/myapp")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds when only registry is set without sourceRef components"() {
        when:
        imageSpec.registry.set("docker.io")
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency succeeds when only labels are set"() {
        when:
        imageSpec.labels.set(['app.version': '1.0'])
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }

    def "validateModeConsistency throws when using only sourceRefImageName without namespace"() {
        when:
        imageSpec.sourceRefImageName.set("alpine")
        // Missing sourceRefNamespace makes this invalid
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("When using namespace+imageName approach, both namespace and imageName are required")
    }

    def "validateModeConsistency throws when using labels with sourceRef"() {
        when:
        imageSpec.sourceRef.set("alpine:latest")
        imageSpec.labels.set(['version': '1.0'])
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateModeConsistency throws when using buildArgs with sourceRef"() {
        when:
        imageSpec.sourceRef.set("nginx:latest")
        imageSpec.buildArgs.set(['VERSION': '2.0'])
        imageSpec.validateModeConsistency()

        then:
        def ex = thrown(org.gradle.api.GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validateModeConsistency succeeds with context path using default convention"() {
        when:
        // Default context convention is src/main/docker, which should not trigger build mode
        imageSpec.validateModeConsistency()

        then:
        noExceptionThrown()
    }
}