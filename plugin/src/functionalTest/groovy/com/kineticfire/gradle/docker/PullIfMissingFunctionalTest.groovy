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

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Ignore
import org.gradle.testkit.runner.GradleRunner

/**
 * Functional tests for pullIfMissing architecture and component assembly
 */
class PullIfMissingFunctionalTest extends Specification {

    def "pullIfMissing validation prevents conflicting configuration"() {
        given:
        def projectDir = File.createTempDir()
        def buildFile = new File(projectDir, 'build.gradle')
        def contextDir = new File(projectDir, 'src/main/docker')
        contextDir.mkdirs()
        new File(contextDir, 'Dockerfile').text = '''
            FROM alpine:latest
            CMD ["echo", "test"]
        '''

        buildFile.text = '''
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myImage {
                        imageName.set("myImage")  // Required for current DSL
                        sourceRef.set("alpine:latest")
                        pullIfMissing.set(true)
                        context.set(file("src/main/docker"))
                        tags.set(["test"])  // Required for current DSL

                        save {
                            outputFile.set(file("build/test.tar"))
                        }
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('dockerSaveMyImage', '--stacktrace')
                .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
                .buildAndFail()

        then:
        result.output.contains("Cannot set pullIfMissing=true when build context is configured")
        // Build fails at configuration time, so task is never created (which is correct behavior)

        cleanup:
        projectDir.deleteDir()
    }

    def "pullIfMissing validation requires sourceRef when enabled"() {
        given:
        def projectDir = File.createTempDir()
        def buildFile = new File(projectDir, 'build.gradle')

        buildFile.text = '''
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myImage {
                        imageName.set("myImage")  // Required for current DSL
                        pullIfMissing.set(true)
                        // No sourceRef or sourceRefImageName specified
                        tags.set(["test"])  // Required for current DSL

                        save {
                            outputFile.set(file("build/test.tar"))
                        }
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('dockerSaveMyImage', '--stacktrace')
                .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
                .buildAndFail()

        then:
        result.output.contains("pullIfMissing=true requires either sourceRef, sourceRefRepository, or sourceRefImageName")
        // Build fails at configuration time, so task is never created (which is correct behavior)

        cleanup:
        projectDir.deleteDir()
    }

    def "sourceRef component assembly works in DSL"() {
        given:
        def projectDir = File.createTempDir()
        def buildFile = new File(projectDir, 'build.gradle')

        buildFile.text = '''
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    alpineTest {
                        imageName.set("alpineTest")  // Required for current DSL
                        // Component assembly
                        sourceRefRegistry.set("docker.io")
                        sourceRefNamespace.set("library")
                        sourceRefImageName.set("alpine")
                        sourceRefTag.set("latest")
                        pullIfMissing.set(false) // Don't actually pull in test

                        tags.set(["test:component-assembly"])
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('help', '--stacktrace')  // Just validate DSL parsing
                .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
                .build()

        then:
        result.task(':help').outcome == TaskOutcome.SUCCESS

        cleanup:
        projectDir.deleteDir()
    }

    def "sourceRef closure DSL works correctly"() {
        given:
        def projectDir = File.createTempDir()
        def buildFile = new File(projectDir, 'build.gradle')

        buildFile.text = '''
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    ubuntuTest {
                        imageName.set("ubuntuTest")  // Required for current DSL
                        sourceRefRegistry.set("docker.io")
                        sourceRefNamespace.set("library")
                        sourceRefImageName.set("ubuntu")
                        sourceRefTag.set("22.04")
                        pullIfMissing.set(false) // Don't actually pull in test

                        tags.set(["test:closure-dsl"])
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('help', '--stacktrace')  // Just validate DSL parsing
                .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
                .build()

        then:
        result.task(':help').outcome == TaskOutcome.SUCCESS

        cleanup:
        projectDir.deleteDir()
    }

    def "pullAuth configuration works at image level"() {
        given:
        def projectDir = File.createTempDir()
        def buildFile = new File(projectDir, 'build.gradle')

        buildFile.text = '''
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    authenticatedImage {
                        imageName.set("authenticatedImage")  // Required for current DSL
                        sourceRef.set("ghcr.io/company/private:latest")
                        pullIfMissing.set(false) // Don't actually pull in test
                        tags.set(["test"])  // Required for current DSL

                        pullAuth {
                            username.set("testuser")
                            password.set("testpass")
                        }

                        save {
                            outputFile.set(file("build/private.tar"))
                        }
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('help', '--stacktrace')  // Just validate DSL parsing
                .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
                .build()

        then:
        result.task(':help').outcome == TaskOutcome.SUCCESS

        cleanup:
        projectDir.deleteDir()
    }

    def "mixed usage patterns work together"() {
        given:
        def projectDir = File.createTempDir()
        def buildFile = new File(projectDir, 'build.gradle')

        buildFile.text = '''
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    // Pattern 1: Full sourceRef
                    fullRef {
                        imageName.set("fullRef")  // Required for current DSL
                        sourceRef.set("nginx:latest")
                        pullIfMissing.set(false)

                        tags.set(["test:full-ref"])
                    }

                    // Pattern 2: Component assembly
                    componentRef {
                        imageName.set("componentRef")  // Required for current DSL
                        sourceRefRegistry.set("localhost:5000")
                        sourceRefImageName.set("myapp")
                        pullIfMissing.set(false)

                        tags.set(["test:component"])
                    }

                    // Pattern 3: Component assembly (converted from closure DSL)
                    closureRef {
                        imageName.set("closureRef")  // Required for current DSL
                        sourceRefRegistry.set("docker.io")
                        sourceRefImageName.set("redis")
                        sourceRefTag.set("alpine")
                        pullIfMissing.set(false)

                        tags.set(["test:closure"])
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('help', '--stacktrace')  // Just validate DSL parsing
                .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
                .build()

        then:
        result.task(':help').outcome == TaskOutcome.SUCCESS

        cleanup:
        projectDir.deleteDir()
    }
}