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

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for GradleDockerPlugin
 */
class GradleDockerPluginTest extends Specification {

    Project project
    GradleDockerPlugin plugin

    def setup() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply(JavaPlugin)
        plugin = new GradleDockerPlugin()
    }

    def "plugin class exists and implements Plugin interface"() {
        expect:
        plugin instanceof Plugin
        Plugin.isAssignableFrom(GradleDockerPlugin)
    }

    def "validateRequirements method handles Java version correctly"() {
        when:
        // This will test the current JVM version
        plugin.apply(project)

        then:
        // If we get here, validation passed (which it should with Java 21+)
        project.extensions.findByName('docker') != null
    }

    def "plugin creates extensions when applied"() {
        when:
        plugin.apply(project)

        then:
        project.extensions.findByName('docker') != null
        project.extensions.findByName('dockerOrch') != null
        project.extensions.findByName('docker').class.simpleName.startsWith('DockerExtension')
        project.extensions.findByName('dockerOrch').class.simpleName.startsWith('DockerOrchExtension')
    }
}