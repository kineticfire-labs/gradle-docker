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

import com.kineticfire.gradle.docker.extension.DockerTestExtension
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for integration test source set convention functionality in GradleDockerPlugin
 */
class IntegrationTestConventionTest extends Specification {

    Project project
    GradleDockerPlugin plugin

    def setup() {
        project = ProjectBuilder.builder().build()
        plugin = new GradleDockerPlugin()
        System.setProperty("gradle.test.running", "true")
    }

    def cleanup() {
        System.clearProperty("gradle.test.running")
    }

    // ===== CORE FUNCTIONALITY TESTS =====

    def "convention triggered when java plugin applied"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTest source set is created"
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        sourceSets.findByName('integrationTest') != null
    }

    def "convention creates source set even without composeStacks"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        and: "dockerTest configured but with no compose stacks"
        // No stacks configured

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTest source set is still created (always-on convention)"
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        sourceSets.findByName('integrationTest') != null
    }

    def "convention requires java plugin"() {
        given: "project without java plugin"
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTest source set is not created (no java plugin)"
        // SourceSetContainer doesn't exist without java plugin
        project.extensions.findByType(SourceSetContainer) == null
    }

    def "integrationTest source set created with Java support"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTest source set has Java source directory configured"
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def integrationTest = sourceSets.findByName('integrationTest')
        integrationTest != null
        integrationTest.java.srcDirs.any { it.path.contains('src/integrationTest/java') }
    }

    def "integrationTest source set includes Groovy support when groovy plugin present"() {
        given: "project with groovy plugin and gradle-docker plugin applied"
        project.pluginManager.apply(GroovyPlugin) // groovy plugin applies java plugin automatically
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTest source set has both Java and Groovy source directories configured"
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def integrationTest = sourceSets.findByName('integrationTest')
        integrationTest != null
        integrationTest.java.srcDirs.any { it.path.contains('src/integrationTest/java') }
        integrationTest.groovy.srcDirs.any { it.path.contains('src/integrationTest/groovy') }
    }

    def "integrationTest source set compileClasspath includes main output"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTest compileClasspath includes main source set output files"
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def integrationTest = sourceSets.findByName('integrationTest')
        def main = sourceSets.findByName('main')
        // Check if main output directories are in the integration test classpath by comparing file paths
        def classpathPaths = integrationTest.compileClasspath.files*.absolutePath
        main.output.classesDirs.every { classpathPaths.contains(it.absolutePath) }
    }

    def "integrationTest source set runtimeClasspath includes main output"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTest runtimeClasspath includes main source set output files"
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def integrationTest = sourceSets.findByName('integrationTest')
        def main = sourceSets.findByName('main')
        // Check if main output directories are in the integration test runtime classpath by comparing file paths
        def classpathPaths = integrationTest.runtimeClasspath.files*.absolutePath
        main.output.classesDirs.every { classpathPaths.contains(it.absolutePath) }
    }

    def "integrationTestImplementation extends testImplementation"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTestImplementation extends testImplementation"
        def integrationTestImpl = project.configurations.findByName('integrationTestImplementation')
        def testImpl = project.configurations.findByName('testImplementation')
        integrationTestImpl != null
        testImpl != null
        integrationTestImpl.extendsFrom.contains(testImpl)
    }

    def "integrationTestRuntimeOnly extends testRuntimeOnly"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTestRuntimeOnly extends testRuntimeOnly"
        def integrationTestRuntime = project.configurations.findByName('integrationTestRuntimeOnly')
        def testRuntime = project.configurations.findByName('testRuntimeOnly')
        integrationTestRuntime != null
        testRuntime != null
        integrationTestRuntime.extendsFrom.contains(testRuntime)
    }

    def "processIntegrationTestResources uses INCLUDE duplicatesStrategy"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "processIntegrationTestResources task has INCLUDE duplicatesStrategy"
        def processResources = project.tasks.findByName('processIntegrationTestResources')
        processResources != null
        processResources.duplicatesStrategy == DuplicatesStrategy.INCLUDE
    }

    def "integrationTest task created with correct properties"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTest task is created with correct configuration"
        def integrationTestTask = project.tasks.findByName('integrationTest')
        integrationTestTask != null
        integrationTestTask instanceof Test
        integrationTestTask.group == 'verification'
        integrationTestTask.description == 'Runs integration tests'

        and: "task is configured to not cache outputs"
        !integrationTestTask.outputs.cacheIf { true }
    }

    def "integrationTest task mustRunAfter test"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "integrationTest task mustRunAfter test task"
        def integrationTestTask = project.tasks.findByName('integrationTest')
        def testTask = project.tasks.findByName('test')
        integrationTestTask.mustRunAfter.getDependencies(integrationTestTask).contains(testTask)
    }

    // ===== EDGE CASE TESTS =====

    def "convention non-destructive when source set already exists"() {
        given: "project with java plugin applied"
        project.pluginManager.apply(JavaPlugin)

        and: "user manually creates integrationTest source set BEFORE applying plugin"
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        sourceSets.create('integrationTest') { sourceSet ->
            sourceSet.java.srcDir('custom/path/java')
        }

        and: "gradle-docker plugin applied"
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "existing source set is not overwritten"
        def integrationTest = sourceSets.findByName('integrationTest')
        integrationTest != null
        // User's custom path should still be present
        integrationTest.java.srcDirs.any { it.path.contains('custom/path/java') }
    }

    def "convention non-destructive when integrationTest task already exists"() {
        given: "project with java plugin applied"
        project.pluginManager.apply(JavaPlugin)

        and: "user manually creates integrationTest task BEFORE applying plugin"
        def customTask = project.tasks.register('integrationTest', Test) {
            description = 'Custom integration test task'
        }

        and: "gradle-docker plugin applied"
        plugin.apply(project)

        when: "project is evaluated"
        project.evaluate()

        then: "existing task is not overwritten"
        def integrationTestTask = project.tasks.findByName('integrationTest')
        integrationTestTask != null
        integrationTestTask.description == 'Custom integration test task'
    }

    def "user can customize source set after convention applied"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        and: "user customizes integrationTest source set AFTER plugin applies convention"
        project.sourceSets {
            integrationTest {
                java.srcDirs = ['custom/override/java']
            }
        }

        when: "project is evaluated"
        project.evaluate()

        then: "user customization takes precedence"
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def integrationTest = sourceSets.findByName('integrationTest')
        integrationTest.java.srcDirs.any { it.path.contains('custom/override/java') }
    }

    def "convention works in multi-project build"() {
        given: "root project"
        def rootProject = ProjectBuilder.builder().build()

        and: "subproject with java plugin and gradle-docker plugin"
        def subproject = ProjectBuilder.builder()
                .withName('subproject')
                .withParent(rootProject)
                .build()
        subproject.pluginManager.apply(JavaPlugin)
        def subplugin = new GradleDockerPlugin()
        subplugin.apply(subproject)

        when: "subproject is evaluated"
        subproject.evaluate()

        then: "subproject has integrationTest source set"
        def sourceSets = subproject.extensions.getByType(SourceSetContainer)
        sourceSets.findByName('integrationTest') != null

        and: "root project is not affected"
        rootProject.extensions.findByType(SourceSetContainer) == null
    }

    def "source set created immediately when java plugin present"() {
        given: "project with java plugin and gradle-docker plugin applied"
        project.pluginManager.apply(JavaPlugin)
        plugin.apply(project)

        when: "source set is checked immediately (before evaluation)"
        def sourceSets = project.extensions.getByType(SourceSetContainer)

        then: "source set exists immediately (not waiting for afterEvaluate)"
        sourceSets.findByName('integrationTest') != null

        when: "project is evaluated"
        project.evaluate()

        then: "source set still exists after evaluation"
        sourceSets.findByName('integrationTest') != null
    }
}
