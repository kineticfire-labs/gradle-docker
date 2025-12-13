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

package com.kineticfire.gradle.docker

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for multi-project build scenarios
 *
 * Tests the plugin's behavior in multi-project builds including:
 * - Plugin application to root vs subprojects
 * - Configuration inheritance and overrides
 * - Task scoping and naming
 * - Cross-project dependencies
 */
class MultiProjectFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'multi-project-test'\n"
    }

    // ==================== Basic Multi-Project ====================

    def "plugin applied to root project only configures correctly"() {
        given:
        settingsFile << """
            include 'app'
            include 'lib'
        """

        // Create subproject directories
        testProjectDir.resolve('app').toFile().mkdirs()
        testProjectDir.resolve('lib').toFile().mkdirs()

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    rootImage {
                        imageName.set('root-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('dockerBuildRootImage')
        !result.output.contains('app:dockerBuild')
        !result.output.contains('lib:dockerBuild')
    }

    def "plugin applied to subproject only configures correctly"() {
        given:
        settingsFile << """
            include 'app'
            include 'lib'
        """

        // Create subproject directories
        testProjectDir.resolve('app').toFile().mkdirs()
        testProjectDir.resolve('lib').toFile().mkdirs()

        def appBuildFile = testProjectDir.resolve('app/build.gradle').toFile()
        appBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    appImage {
                        imageName.set('my-app')
                        tags.set(['v1.0'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments(':app:tasks', '--all')
            .build()

        then:
        result.output.contains('dockerBuildAppImage')
    }

    def "plugin applied to multiple subprojects creates isolated task namespaces"() {
        given:
        settingsFile << """
            include 'frontend'
            include 'backend'
        """

        def frontendBuildFile = testProjectDir.resolve('frontend/build.gradle').toFile()
        frontendBuildFile.parentFile.mkdirs()
        frontendBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    webUI {
                        imageName.set('frontend-ui')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def backendBuildFile = testProjectDir.resolve('backend/build.gradle').toFile()
        backendBuildFile.parentFile.mkdirs()
        backendBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    api {
                        imageName.set('backend-api')
                        tags.set(['v2.0'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('frontend:dockerBuildWebUI')
        result.output.contains('backend:dockerBuildApi')
        // Verify no task name conflicts - both subprojects have isolated namespaces
    }

    def "plugin applied to both root and subprojects maintains separate configurations"() {
        given:
        settingsFile << """
            include 'service'
        """

        // Create subproject directory
        testProjectDir.resolve('service').toFile().mkdirs()

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    rootImage {
                        imageName.set('root-deployment')
                        tags.set(['stable'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def serviceBuildFile = testProjectDir.resolve('service/build.gradle').toFile()
        serviceBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    serviceImage {
                        imageName.set('service-app')
                        tags.set(['dev'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('dockerBuildRootImage - ')
        result.output.contains('service:dockerBuildServiceImage')
    }

    // ==================== Configuration Inheritance ====================

    def "subproject can reference root project properties"() {
        given:
        settingsFile << """
            include 'app'
        """

        // Create subproject directory
        testProjectDir.resolve('app').toFile().mkdirs()

        buildFile << """
            ext.sharedRegistry = 'registry.example.com'
            ext.sharedNamespace = 'myorg'
        """

        def appBuildFile = testProjectDir.resolve('app/build.gradle').toFile()
        appBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    appImage {
                        registry.set(rootProject.ext.sharedRegistry)
                        namespace.set(rootProject.ext.sharedNamespace)
                        imageName.set('app')
                        tags.set(['v1.0'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def ext = project.extensions.getByName('docker')
                    def image = ext.images.getByName('appImage')
                    println "Registry: \${image.registry.get()}"
                    println "Namespace: \${image.namespace.get()}"
                    assert image.registry.get() == 'registry.example.com'
                    assert image.namespace.get() == 'myorg'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments(':app:verifyConfig')
            .build()

        then:
        result.output.contains('Registry: registry.example.com')
        result.output.contains('Namespace: myorg')
    }

    def "subproject overrides root project configuration"() {
        given:
        settingsFile << """
            include 'custom-app'
        """

        // Create subproject directory
        testProjectDir.resolve('custom-app').toFile().mkdirs()

        buildFile << """
            allprojects {
                ext.defaultRegistry = 'docker.io'
            }
        """

        def appBuildFile = testProjectDir.resolve('custom-app/build.gradle').toFile()
        appBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            ext.defaultRegistry = 'ghcr.io'  // Override

            docker {
                images {
                    customImage {
                        registry.set(project.ext.defaultRegistry)
                        imageName.set('custom')
                        tags.set(['override'])
                        context.set(file('.'))
                    }
                }
            }

            task verifyOverride {
                doLast {
                    def ext = project.extensions.getByName('docker')
                    def image = ext.images.getByName('customImage')
                    println "Overridden Registry: \${image.registry.get()}"
                    assert image.registry.get() == 'ghcr.io'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments(':custom-app:verifyOverride')
            .build()

        then:
        result.output.contains('Overridden Registry: ghcr.io')
    }

    // ==================== Task Naming and Scoping ====================

    def "tasks are correctly scoped per subproject with unique names"() {
        given:
        settingsFile << """
            include 'web'
            include 'api'
        """

        def webBuildFile = testProjectDir.resolve('web/build.gradle').toFile()
        webBuildFile.parentFile.mkdirs()
        webBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    app {
                        imageName.set('web-app')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def apiBuildFile = testProjectDir.resolve('api/build.gradle').toFile()
        apiBuildFile.parentFile.mkdirs()
        apiBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    app {
                        imageName.set('api-app')
                        tags.set(['v1.0'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('web:dockerBuildApp')
        result.output.contains('api:dockerBuildApp')
        // Both subprojects can have 'app' image with no conflicts
    }

    def "aggregate tasks work correctly in multi-project builds"() {
        given:
        settingsFile << """
            include 'service1'
            include 'service2'
        """

        def service1BuildFile = testProjectDir.resolve('service1/build.gradle').toFile()
        service1BuildFile.parentFile.mkdirs()
        service1BuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    svc1 {
                        imageName.set('service-one')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def service2BuildFile = testProjectDir.resolve('service2/build.gradle').toFile()
        service2BuildFile.parentFile.mkdirs()
        service2BuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    svc2 {
                        imageName.set('service-two')
                        tags.set(['v2.0'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('service1:dockerBuild ')
        result.output.contains('service2:dockerBuild ')
    }

    // ==================== Different Configurations Per Subproject ====================

    def "different images per subproject work correctly"() {
        given:
        settingsFile << """
            include 'web'
            include 'worker'
        """

        def webBuildFile = testProjectDir.resolve('web/build.gradle').toFile()
        webBuildFile.parentFile.mkdirs()
        webBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    nginx {
                        imageName.set('web-nginx')
                        tags.set(['latest', 'stable'])
                        context.set(file('.'))
                    }
                    frontend {
                        imageName.set('web-frontend')
                        tags.set(['dev'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def workerBuildFile = testProjectDir.resolve('worker/build.gradle').toFile()
        workerBuildFile.parentFile.mkdirs()
        workerBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    processor {
                        imageName.set('worker-processor')
                        tags.set(['v1.0'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('web:dockerBuildNginx')
        result.output.contains('web:dockerBuildFrontend')
        result.output.contains('worker:dockerBuildProcessor')
    }

    def "different compose stacks per subproject work correctly"() {
        given:
        settingsFile << """
            include 'integration-tests'
            include 'e2e-tests'
        """

        def integrationTestsBuildFile = testProjectDir.resolve('integration-tests/build.gradle').toFile()
        integrationTestsBuildFile.parentFile.mkdirs()

        def integrationComposeFile = testProjectDir.resolve('integration-tests/docker-compose.yml').toFile()
        integrationComposeFile << """
            services:
              db:
                image: postgres:latest
        """

        integrationTestsBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    integration {
                        composeFiles('docker-compose.yml')
                    }
                }
            }
        """

        def e2eTestsBuildFile = testProjectDir.resolve('e2e-tests/build.gradle').toFile()
        e2eTestsBuildFile.parentFile.mkdirs()

        def e2eComposeFile = testProjectDir.resolve('e2e-tests/docker-compose.yml').toFile()
        e2eComposeFile << """
            services:
              selenium:
                image: selenium/standalone-chrome:latest
        """

        e2eTestsBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerTest {
                composeStacks {
                    e2e {
                        composeFiles('docker-compose.yml')
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('integration-tests:composeUpIntegration')
        result.output.contains('e2e-tests:composeUpE2e')
    }

    def "shared registry configuration across subprojects"() {
        given:
        settingsFile << """
            include 'app1'
            include 'app2'
        """

        // Create subproject directories
        testProjectDir.resolve('app1').toFile().mkdirs()
        testProjectDir.resolve('app2').toFile().mkdirs()

        buildFile << """
            subprojects {
                ext.sharedRegistry = 'registry.company.com'
                ext.sharedNamespace = 'team'
            }
        """

        def app1BuildFile = testProjectDir.resolve('app1/build.gradle').toFile()
        app1BuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    app1Image {
                        registry.set(project.ext.sharedRegistry)
                        namespace.set(project.ext.sharedNamespace)
                        imageName.set('app1')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def app2BuildFile = testProjectDir.resolve('app2/build.gradle').toFile()
        app2BuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    app2Image {
                        registry.set(project.ext.sharedRegistry)
                        namespace.set(project.ext.sharedNamespace)
                        imageName.set('app2')
                        tags.set(['v2'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('app1:dockerBuildApp1Image')
        result.output.contains('app2:dockerBuildApp2Image')
    }

    def "independent namespace per subproject"() {
        given:
        settingsFile << """
            include 'team-a'
            include 'team-b'
        """

        def teamABuildFile = testProjectDir.resolve('team-a/build.gradle').toFile()
        teamABuildFile.parentFile.mkdirs()
        teamABuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    service {
                        namespace.set('team-alpha')
                        imageName.set('service')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def teamBBuildFile = testProjectDir.resolve('team-b/build.gradle').toFile()
        teamBBuildFile.parentFile.mkdirs()
        teamBBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    service {
                        namespace.set('team-beta')
                        imageName.set('service')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('team-a:dockerBuildService')
        result.output.contains('team-b:dockerBuildService')
    }

    // ==================== Cross-Project Dependencies ====================

    def "subproject can depend on another subproject build task"() {
        given:
        settingsFile << """
            include 'base-image'
            include 'app'
        """

        def baseBuildFile = testProjectDir.resolve('base-image/build.gradle').toFile()
        baseBuildFile.parentFile.mkdirs()
        baseBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    base {
                        imageName.set('base-img')
                        tags.set(['v1.0'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def appBuildFile = testProjectDir.resolve('app/build.gradle').toFile()
        appBuildFile.parentFile.mkdirs()
        appBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    app {
                        imageName.set('app-img')
                        tags.set(['latest'])
                        context.set(file('.'))
                    }
                }
            }

            afterEvaluate {
                tasks.named('dockerBuildApp') {
                    dependsOn(':base-image:dockerBuildBase')
                }
            }

            task verifyDependency {
                doLast {
                    def deps = tasks.getByName('dockerBuildApp').dependsOn
                    println "Dependencies: \${deps}"
                    assert deps.any { it.toString().contains('dockerBuildBase') }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments(':app:verifyDependency')
            .build()

        then:
        result.output.contains('dockerBuildBase')
    }

    def "nested subprojects maintain proper task isolation"() {
        given:
        settingsFile << """
            include 'services:api'
            include 'services:worker'
        """

        def servicesDir = testProjectDir.resolve('services').toFile()
        servicesDir.mkdirs()

        def apiDir = testProjectDir.resolve('services/api').toFile()
        apiDir.mkdirs()
        def apiBuildFile = new File(apiDir, 'build.gradle')
        apiBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    apiService {
                        imageName.set('api')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                }
            }
        """

        def workerDir = testProjectDir.resolve('services/worker').toFile()
        workerDir.mkdirs()
        def workerBuildFile = new File(workerDir, 'build.gradle')
        workerBuildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    workerService {
                        imageName.set('worker')
                        tags.set(['v1'])
                        context.set(file('.'))
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--all')
            .build()

        then:
        result.output.contains('services:api:dockerBuildApiService')
        result.output.contains('services:worker:dockerBuildWorkerService')
    }
}
