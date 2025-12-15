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

package com.kineticfire.gradle.docker.service

import com.kineticfire.gradle.docker.Lifecycle
import com.kineticfire.gradle.docker.extension.DockerExtension
import com.kineticfire.gradle.docker.extension.DockerTestExtension
import com.kineticfire.gradle.docker.extension.DockerWorkflowsExtension
import com.kineticfire.gradle.docker.extension.DockerProjectExtension
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

/**
 * Unit tests for DockerProjectTranslator
 */
class DockerProjectTranslatorTest extends Specification {

    Project project
    DockerExtension dockerExt
    DockerTestExtension dockerTestExt
    DockerWorkflowsExtension dockerWorkflowsExt
    DockerProjectExtension dockerProjectExt
    DockerProjectTranslator translator

    @TempDir
    Path tempDir

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.pluginManager.apply('java')
        project.pluginManager.apply('com.kineticfire.gradle.docker')

        dockerExt = project.extensions.getByType(DockerExtension)
        dockerTestExt = project.extensions.getByType(DockerTestExtension)
        dockerWorkflowsExt = project.extensions.getByType(DockerWorkflowsExtension)
        dockerProjectExt = project.objects.newInstance(DockerProjectExtension)

        translator = new DockerProjectTranslator()

        // Create required directories and files
        project.file('src/main/docker').mkdirs()
        project.file('src/main/docker/Dockerfile').text = 'FROM openjdk:17'
        project.file('src/integrationTest/resources/compose').mkdirs()
        project.file('src/integrationTest/resources/compose/app.yml').text = '''
services:
  app:
    image: test-app:latest
'''
    }

    // ===== HELPER METHOD TESTS =====

    def "deriveImageName returns imageName when set"() {
        given:
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
            }
        }

        expect:
        translator.deriveImageName(dockerProjectExt.spec.images.getByName('myApp'), project) == 'my-app'
    }

    def "deriveImageName returns sourceRefImageName when imageName not set"() {
        given:
        dockerProjectExt.images {
            nginx {
                sourceRefImageName.set('nginx')
            }
        }

        expect:
        translator.deriveImageName(dockerProjectExt.spec.images.getByName('nginx'), project) == 'nginx'
    }

    def "deriveImageName returns blockName when imageName and sourceRefImageName not set"() {
        given:
        dockerProjectExt.images {
            myApp {
                contextDir.set('docker')
            }
        }
        project.file('docker').mkdirs()

        expect:
        translator.deriveImageName(dockerProjectExt.spec.images.getByName('myApp'), project) == 'myApp'
    }

    def "sanitizeName removes special characters and lowercases"() {
        expect:
        translator.sanitizeName('my-app') == 'myapp'
        translator.sanitizeName('my_app') == 'myapp'
        translator.sanitizeName('my.app') == 'myapp'
        translator.sanitizeName('MyApp') == 'myapp'
        translator.sanitizeName('myNginx') == 'mynginx'
        translator.sanitizeName(null) == ''
        translator.sanitizeName('') == ''
    }

    def "parseLifecycle returns correct enum values"() {
        expect:
        translator.parseLifecycle('class') == WorkflowLifecycle.CLASS
        translator.parseLifecycle('CLASS') == WorkflowLifecycle.CLASS
        translator.parseLifecycle('method') == WorkflowLifecycle.METHOD
        translator.parseLifecycle('METHOD') == WorkflowLifecycle.METHOD
    }

    def "parseLifecycle throws for unknown value"() {
        when:
        translator.parseLifecycle('invalid')

        then:
        def e = thrown(GradleException)
        e.message.contains("Unknown lifecycle 'invalid'")
    }

    def "convertLifecycle returns correct enum values"() {
        expect:
        translator.convertLifecycle(Lifecycle.CLASS) == WorkflowLifecycle.CLASS
        translator.convertLifecycle(Lifecycle.METHOD) == WorkflowLifecycle.METHOD
    }

    def "convertLifecycle throws for null value"() {
        when:
        translator.convertLifecycle(null)

        then:
        thrown(GradleException)
    }

    // ===== VALIDATION TESTS =====

    def "validation fails when docker extension is null"() {
        given:
        dockerProjectExt.images {
            myApp {
                jarFrom.set('jar')
            }
        }

        when:
        translator.translate(project, dockerProjectExt.spec, null, dockerTestExt, dockerWorkflowsExt)

        then:
        def e = thrown(GradleException)
        e.message.contains("dockerProject requires the 'docker' extension")
    }

    def "validation fails when dockerTest extension is null"() {
        given:
        dockerProjectExt.images {
            myApp {
                jarFrom.set('jar')
            }
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, null, dockerWorkflowsExt)

        then:
        def e = thrown(GradleException)
        e.message.contains("dockerProject requires the 'dockerTest' extension")
    }

    def "validation fails when dockerWorkflows extension is null"() {
        given:
        dockerProjectExt.images {
            myApp {
                jarFrom.set('jar')
            }
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, null)

        then:
        def e = thrown(GradleException)
        e.message.contains("dockerProject requires the 'dockerWorkflows' extension")
    }

    def "validation fails when neither build mode nor sourceRef mode configured"() {
        given:
        dockerProjectExt.images {
            testApp {
                imageName.set('test-app')
                // No jarFrom, contextDir, or sourceRef properties
            }
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def e = thrown(GradleException)
        e.message.contains("must specify either build properties")
    }

    def "validation fails when both build mode and sourceRef mode configured"() {
        given:
        dockerProjectExt.images {
            myApp {
                jarFrom.set('jar')
                sourceRefImageName.set('nginx')
            }
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def e = thrown(GradleException)
        e.message.contains("cannot mix build mode")
    }

    def "validation fails when both jarFrom and contextDir specified"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                jarFrom.set('jar')
                contextDir.set('docker')
            }
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def e = thrown(GradleException)
        e.message.contains("cannot specify both jarFrom and contextDir")
    }

    def "validation fails when same image configured in both docker{} and dockerProject{}"() {
        given:
        // First configure via docker{}
        dockerExt.images.create('myapp') { image ->
            image.imageName.set('my-app')
            image.tags.set(['latest'])
        }

        // Then try to configure same name via dockerProject{}
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                jarFrom.set('jar')
            }
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def e = thrown(GradleException)
        e.message.contains("already configured in docker.images")
    }

    // ===== JAR FROM VALIDATION TESTS =====

    def "validateJarTaskPath fails when java plugin not applied"() {
        given:
        def plainProject = ProjectBuilder.builder().withProjectDir(tempDir.resolve('plain').toFile()).build()
        plainProject.file('src/main/docker').mkdirs()

        when:
        translator.validateJarTaskPath(plainProject, ':app:jar')

        then:
        def e = thrown(GradleException)
        e.message.contains("requires 'java' or 'groovy' plugin")
    }

    def "validateJarTaskPath fails for empty path"() {
        when:
        translator.validateJarTaskPath(project, '')

        then:
        def e = thrown(GradleException)
        e.message.contains("cannot be empty")
    }

    def "validateJarTaskPath fails for null path"() {
        when:
        translator.validateJarTaskPath(project, null)

        then:
        def e = thrown(GradleException)
        e.message.contains("cannot be empty")
    }

    def "validateJarTaskPath fails for path ending with colon"() {
        when:
        translator.validateJarTaskPath(project, ':app:')

        then:
        def e = thrown(GradleException)
        e.message.contains("must end with a task name")
    }

    def "validateJarTaskPath fails for cross-project path not starting with colon"() {
        when:
        translator.validateJarTaskPath(project, 'app:jar')

        then:
        def e = thrown(GradleException)
        e.message.contains("must start with ':'")
    }

    def "validateJarTaskPath accepts simple task name"() {
        when:
        translator.validateJarTaskPath(project, 'jar')

        then:
        noExceptionThrown()
    }

    def "validateJarTaskPath accepts root project task reference"() {
        when:
        translator.validateJarTaskPath(project, ':jar')

        then:
        noExceptionThrown()
    }

    // ===== BUILD MODE TRANSLATION TESTS =====

    def "translate creates docker image for build mode with contextDir"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM openjdk:17'

        dockerProjectExt.images {
            myService {
                imageName.set('my-service')
                tags.set(['1.0.0', 'latest'])
                contextDir.set('docker')
                dockerfile.set('docker/Dockerfile')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app'])
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        dockerExt.images.findByName('myservice') != null

        and:
        def image = dockerExt.images.getByName('myservice')
        image.imageName.get() == 'my-service'
        image.tags.get() == ['1.0.0', 'latest']
        image.context.get().asFile == project.file('docker')
    }

    def "translate creates docker image for sourceRef mode"() {
        given:
        dockerProjectExt.images {
            nginx {
                sourceRefRegistry.set('docker.io')
                sourceRefNamespace.set('library')
                sourceRefImageName.set('nginx')
                sourceRefTag.set('1.25')
                tags.set(['my-nginx', 'latest'])
                pullIfMissing.set(true)
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        dockerExt.images.findByName('nginx') != null

        and:
        def image = dockerExt.images.getByName('nginx')
        image.sourceRefRegistry.get() == 'docker.io'
        image.sourceRefNamespace.get() == 'library'
        image.sourceRefImageName.get() == 'nginx'
        image.sourceRefTag.get() == '1.25'
        image.pullIfMissing.get() == true
    }

    def "translate creates docker image for sourceRef mode with direct sourceRef"() {
        given:
        dockerProjectExt.images {
            myNginx {
                sourceRef.set('docker.io/library/nginx:1.25')
                tags.set(['my-nginx'])
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        // Uses blockName 'myNginx' when no imageName set
        dockerExt.images.findByName('mynginx') != null

        and:
        def image = dockerExt.images.getByName('mynginx')
        image.sourceRef.get() == 'docker.io/library/nginx:1.25'
    }

    def "translate creates docker image for sourceRef mode with pullAuth"() {
        given:
        dockerProjectExt.images {
            privateImage {
                sourceRefImageName.set('private-image')
                tags.set(['latest'])
                pullAuth {
                    username.set('testuser')
                    password.set('testpass')
                }
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def image = dockerExt.images.getByName('privateimage')
        image.pullAuth != null
        image.pullAuth.username.get() == 'testuser'
        image.pullAuth.password.get() == 'testpass'
    }

    // ===== COMPOSE STACK TRANSLATION TESTS =====

    def "translate creates compose stack when test block configured"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app', 'db'])
            timeoutSeconds.set(120)
            pollSeconds.set(5)
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        dockerTestExt.composeStacks.findByName('myappTest') != null

        and:
        def stack = dockerTestExt.composeStacks.getByName('myappTest')
        stack.files.files.any { it.name == 'app.yml' }
        // Docker Compose project names must be lowercase
        stack.projectName.get() == "${project.name}-myappTest".toLowerCase()
        stack.waitForHealthy.get().waitForServices.get() == ['app', 'db']
    }

    def "translate skips compose stack when test block not configured"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        // No test block configured

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        dockerTestExt.composeStacks.size() == 0
    }

    def "translate creates compose stack with custom project name"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            projectName.set('custom-project')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def stack = dockerTestExt.composeStacks.getByName('myappTest')
        stack.projectName.get() == 'custom-project'
    }

    def "translate creates compose stack with waitForRunning"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            waitForRunning.set(['app'])
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def stack = dockerTestExt.composeStacks.getByName('myappTest')
        stack.waitForRunning.get().waitForServices.get() == ['app']
    }

    // ===== PIPELINE TRANSLATION TESTS =====

    def "translate creates pipeline with build and test steps"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            testTaskName.set('integrationTest')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        dockerWorkflowsExt.pipelines.findByName('myappPipeline') != null

        and:
        def pipeline = dockerWorkflowsExt.pipelines.getByName('myappPipeline')
        pipeline.description.get().contains('myapp')
        pipeline.build.get().image.get() == dockerExt.images.getByName('myapp')
        pipeline.test.get().stack.get() == dockerTestExt.composeStacks.getByName('myappTest')
        pipeline.test.get().testTaskName.get() == 'integrationTest'
    }

    def "translate creates pipeline with method lifecycle"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            lifecycle.set(Lifecycle.METHOD)
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def pipeline = dockerWorkflowsExt.pipelines.getByName('myappPipeline')
        pipeline.test.get().lifecycle.get() == WorkflowLifecycle.METHOD
        pipeline.test.get().delegateStackManagement.get() == true
    }

    def "translate creates pipeline with onTestSuccess"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }
        dockerProjectExt.onSuccess {
            additionalTags.set(['tested', 'stable'])
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def pipeline = dockerWorkflowsExt.pipelines.getByName('myappPipeline')
        pipeline.onTestSuccess.get().additionalTags.get() == ['tested', 'stable']
    }

    def "translate creates pipeline with save configuration"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }
        dockerProjectExt.onSuccess {
            saveFile.set('build/images/app.tar.gz')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def pipeline = dockerWorkflowsExt.pipelines.getByName('myappPipeline')
        pipeline.onTestSuccess.get().save.isPresent()
        pipeline.onTestSuccess.get().save.get().outputFile.get().asFile == project.file('build/images/app.tar.gz')
    }

    def "translate creates pipeline with publish configuration"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }
        dockerProjectExt.onSuccess {
            additionalTags.set(['tested'])
            publishRegistry.set('ghcr.io')
            publishNamespace.set('myorg')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def pipeline = dockerWorkflowsExt.pipelines.getByName('myappPipeline')
        pipeline.onTestSuccess.get().publish.isPresent()
        def publishSpec = pipeline.onTestSuccess.get().publish.get()
        publishSpec.publishTags.get() == ['tested']
        publishSpec.to.getByName('default').registry.get() == 'ghcr.io'
        publishSpec.to.getByName('default').namespace.get() == 'myorg'
    }

    def "translate creates pipeline with onTestFailure"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }
        dockerProjectExt.onFailure {
            additionalTags.set(['failed', 'needs-review'])
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def pipeline = dockerWorkflowsExt.pipelines.getByName('myappPipeline')
        pipeline.onTestFailure.get().additionalTags.get() == ['failed', 'needs-review']
    }

    // ===== EMPTY/DEFAULT VALUES TESTS =====

    def "translate works with empty additionalTags"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }
        dockerProjectExt.onSuccess {
            additionalTags.set([])
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        noExceptionThrown()

        and:
        def pipeline = dockerWorkflowsExt.pipelines.getByName('myappPipeline')
        pipeline.onTestSuccess.get().additionalTags.get() == []
    }

    def "translate uses default test task name when not specified"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            // No testTaskName specified
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def pipeline = dockerWorkflowsExt.pipelines.getByName('myappPipeline')
        pipeline.test.get().testTaskName.get() == 'integrationTest'
    }

    def "translate uses default lifecycle when not specified"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            // No lifecycle specified
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def pipeline = dockerWorkflowsExt.pipelines.getByName('myappPipeline')
        pipeline.test.get().lifecycle.get() == WorkflowLifecycle.CLASS
    }

    // ===== VERSION DERIVATION TESTS =====

    def "translate derives version from tags"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
                tags.set(['1.0.0', 'latest'])
                // No explicit version
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def image = dockerExt.images.getByName('myapp')
        image.version.get() == '1.0.0'
    }

    def "translate uses explicit version when set"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
                tags.set(['1.0.0', 'latest'])
                version.set('2.0.0')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def image = dockerExt.images.getByName('myapp')
        image.version.get() == '2.0.0'
    }

    // ===== BUILD ARGS AND LABELS TESTS =====

    def "translate copies buildArgs to image"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
                buildArgs.put('VERSION', '1.0.0')
                buildArgs.put('BUILD_DATE', '2024-01-01')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def image = dockerExt.images.getByName('myapp')
        image.buildArgs.get() == [VERSION: '1.0.0', BUILD_DATE: '2024-01-01']
    }

    def "translate copies labels to image"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
                labels.put('maintainer', 'team@example.com')
                labels.put('version', '1.0')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def image = dockerExt.images.getByName('myapp')
        image.labels.get() == [maintainer: 'team@example.com', version: '1.0']
    }

    // ===== REGISTRY AND NAMESPACE TESTS =====

    def "translate copies registry and namespace to image"() {
        given:
        project.file('docker').mkdirs()
        dockerProjectExt.images {
            myApp {
                imageName.set('my-app')
                contextDir.set('docker')
                registry.set('ghcr.io')
                namespace.set('myorg')
            }
        }
        dockerProjectExt.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
        }

        when:
        translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        then:
        def image = dockerExt.images.getByName('myapp')
        image.registry.get() == 'ghcr.io'
        image.namespace.get() == 'myorg'
    }
}
