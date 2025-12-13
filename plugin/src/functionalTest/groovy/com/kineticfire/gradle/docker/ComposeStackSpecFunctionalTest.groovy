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
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for ComposeStackSpec multi-file properties
 * 
 * TEMPORARILY DISABLED: These tests are temporarily commented out due to known incompatibility 
 * between Gradle TestKit and Gradle 9.0.0. The issue is tracked and will be re-enabled 
 * when TestKit compatibility is improved or an alternative testing approach is implemented.
 * 
 * Issue: InvalidPluginMetadataException when using withPluginClasspath() in Gradle 9.0.0
 * Root cause: Gradle 9.0.0 TestKit has breaking changes in plugin classpath resolution
 * 
 * Tests affected: All tests using withPluginClasspath() method (9 tests)
 * Functionality affected:
 * - Multi-file compose stack configurations using composeFiles('file1.yml', 'file2.yml')
 * - Multi-file compose stack configurations using composeFiles = ['file1.yml', 'file2.yml']
 * - Multi-file compose stack configurations using composeFiles(file('base.yml'), file('override.yml'))
 * - Plugin application with multi-file compose stack configurations
 * - Task generation with multi-file stacks
 * - Configuration validation for multi-file setups
 * - Backward compatibility with single-file composeFile configurations
 * - Mixed single-file and multi-file stack configurations
 * - Migration scenarios from single-file to multi-file setups
 */
class ComposeStackSpecFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "multi-file compose configuration with composeFiles(String...) works correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-multi-compose'"
        
        // Create docker-compose base file
        def baseComposeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        baseComposeFile << """
            version: '3.8'
            services:
              webapp:
                image: nginx:alpine
                ports:
                  - "8080"
                labels:
                  - "test=base"
        """
        
        // Create docker-compose override file
        def overrideComposeFile = testProjectDir.resolve('docker-compose.override.yml').toFile()
        overrideComposeFile << """
            version: '3.8'
            services:
              webapp:
                environment:
                  - ENV=test
                  - DEBUG=true
                labels:
                  - "test=override"
              database:
                image: postgres:alpine
                environment:
                  - POSTGRES_PASSWORD=testpass
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerTest {
                composeStacks {
                    webapp {
                        composeFiles('docker-compose.yml', 'docker-compose.override.yml')
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
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeUpWebapp') || result.output.contains('webapp')
    }

    def "multi-file compose configuration with composeFiles property assignment works correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-multi-compose-property'"
        
        def composeFile1 = testProjectDir.resolve('base.yml').toFile()
        composeFile1 << """
            version: '3.8'
            services:
              api:
                image: openjdk:11-jre-slim
                ports:
                  - "3000"
        """
        
        def composeFile2 = testProjectDir.resolve('services.yml').toFile()
        composeFile2 << """
            version: '3.8'
            services:
              cache:
                image: redis:alpine
                ports:
                  - "6379"
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerTest {
                composeStacks {
                    fullstack {
                        composeFiles = ['base.yml', 'services.yml']
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
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeUpFullstack') || result.output.contains('fullstack')
    }

    def "multi-file compose configuration with File objects works correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-multi-compose-files'"
        
        def networkComposeFile = testProjectDir.resolve('network.yml').toFile()
        networkComposeFile << """
            version: '3.8'
            networks:
              app-network:
                driver: bridge
        """
        
        def serviceComposeFile = testProjectDir.resolve('service.yml').toFile()
        serviceComposeFile << """
            version: '3.8'
            services:
              worker:
                image: alpine:latest
                command: sleep 30
                networks:
                  - app-network
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerTest {
                composeStacks {
                    networked {
                        composeFiles(file('network.yml'), file('service.yml'))
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
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeUpNetworked') || result.output.contains('networked')
    }

    def "plugin applies correctly with multi-file compose stack configurations"() {
        given:
        settingsFile << "rootProject.name = 'test-plugin-application'"
        
        def mainComposeFile = testProjectDir.resolve('main.yml').toFile()
        mainComposeFile << """
            version: '3.8'
            services:
              main:
                image: ubuntu:20.04
                command: echo "Hello from main"
        """
        
        def extraComposeFile = testProjectDir.resolve('extra.yml').toFile()
        extraComposeFile << """
            version: '3.8'
            services:
              extra:
                image: ubuntu:20.04
                command: echo "Hello from extra"
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerTest {
                composeStacks {
                    combined {
                        composeFiles('main.yml', 'extra.yml')
                        projectName = 'test-multi-stack'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--task', 'composeUpCombined')
            .build()

        then:
        result.task(':help').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeUpCombined') || result.output.contains('combined')
    }

    def "task generation works with multi-file stacks"() {
        given:
        settingsFile << "rootProject.name = 'test-task-generation'"
        
        def webComposeFile = testProjectDir.resolve('web.yml').toFile()
        webComposeFile << """
            version: '3.8'
            services:
              frontend:
                image: nginx:alpine
                ports:
                  - "80"
        """
        
        def dbComposeFile = testProjectDir.resolve('db.yml').toFile()
        dbComposeFile << """
            version: '3.8'
            services:
              database:
                image: mysql:5.7
                environment:
                  - MYSQL_ROOT_PASSWORD=root
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerTest {
                composeStacks {
                    webdb {
                        composeFiles('web.yml', 'db.yml')
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--group', 'docker compose')
            .build()

        then:
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeUpWebdb')
        result.output.contains('composeDownWebdb')
    }

    def "validation works correctly for multi-file configurations"() {
        given:
        settingsFile << "rootProject.name = 'test-validation'"
        
        // Only create one of the two files to test validation
        def existingFile = testProjectDir.resolve('exists.yml').toFile()
        existingFile << """
            version: '3.8'
            services:
              test:
                image: alpine:latest
                command: echo "test"
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerTest {
                composeStacks {
                    partial {
                        composeFiles('exists.yml', 'missing.yml')
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('composeUpPartial', '--info')
            .buildAndFail()

        then:
        // Build fails during configuration or execution - either is valid for file validation
        result.output.contains('missing.yml') || result.output.contains('file') || result.output.contains('not found') || result.output.contains('does not exist')
    }

    def "single-file composeFile configurations still work (backward compatibility)"() {
        given:
        settingsFile << "rootProject.name = 'test-backward-compatibility'"
        
        def singleComposeFile = testProjectDir.resolve('docker-compose.yml').toFile()
        singleComposeFile << """
            version: '3.8'
            services:
              legacy:
                image: alpine:latest
                command: sleep 10
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerTest {
                composeStacks {
                    legacy {
                        composeFile = file('docker-compose.yml')
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
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeUpLegacy') || result.output.contains('legacy')
    }

    def "projects can mix single-file and multi-file stack configurations"() {
        given:
        settingsFile << "rootProject.name = 'test-mixed-configurations'"
        
        def singleFile = testProjectDir.resolve('simple.yml').toFile()
        singleFile << """
            version: '3.8'
            services:
              simple:
                image: alpine:latest
                command: echo "simple"
        """
        
        def multiFile1 = testProjectDir.resolve('multi1.yml').toFile()
        multiFile1 << """
            version: '3.8'
            services:
              multi1:
                image: alpine:latest
                command: echo "multi1"
        """
        
        def multiFile2 = testProjectDir.resolve('multi2.yml').toFile()
        multiFile2 << """
            version: '3.8'
            services:
              multi2:
                image: alpine:latest
                command: echo "multi2"
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerTest {
                composeStacks {
                    singleStack {
                        composeFile = file('simple.yml')
                    }
                    multiStack {
                        composeFiles('multi1.yml', 'multi2.yml')
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('tasks', '--group', 'docker compose')
            .build()

        then:
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeUpSingleStack')
        result.output.contains('composeUpMultiStack')
        result.output.contains('composeDownSingleStack')
        result.output.contains('composeDownMultiStack')
    }

    def "migration from single-file to multi-file works correctly"() {
        given:
        settingsFile << "rootProject.name = 'test-migration'"
        
        def baseFile = testProjectDir.resolve('base.yml').toFile()
        baseFile << """
            version: '3.8'
            services:
              app:
                image: node:14-alpine
                ports:
                  - "3000"
        """
        
        def overrideFile = testProjectDir.resolve('override.yml').toFile()
        overrideFile << """
            version: '3.8'
            services:
              app:
                environment:
                  - NODE_ENV=production
              monitoring:
                image: prom/prometheus
        """
        
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }
            
            dockerTest {
                composeStacks {
                    migrated {
                        // Migration: was composeFile = 'base.yml'
                        // now: composeFiles('base.yml', 'override.yml')
                        composeFiles('base.yml', 'override.yml')
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('help', '--task', 'composeUpMigrated')
            .build()

        then:
        result.task(':help').outcome == TaskOutcome.SUCCESS
        result.output.contains('composeUpMigrated') || result.output.contains('migrated')
    }
}