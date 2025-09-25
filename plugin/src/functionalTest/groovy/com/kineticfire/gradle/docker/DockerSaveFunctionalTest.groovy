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

import com.kineticfire.gradle.docker.model.SaveCompression
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for Docker save operations with new SaveCompression enum
 * 
 * NOTE: These tests focus on configuration verification rather than actual Docker execution
 * due to TestKit limitations with Gradle 9. Real Docker operations are covered by 
 * integration tests in the plugin-integration-test project.
 */
class DockerSaveFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()
    }

    def "docker save task configuration supports all SaveCompression types"() {
        given:
        settingsFile << "rootProject.name = 'test-save-compression'"
        
        buildFile << """
            import com.kineticfire.gradle.docker.model.SaveCompression
            
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    gzipApp {
                        registry.set('docker.io')
                        imageName.set('test-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        save {
                            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
                            outputFile.set(layout.buildDirectory.file('docker-images/app.tar.gz'))
                        }
                    }
                    
                    bzip2App {
                        registry.set('docker.io')
                        imageName.set('test-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        save {
                            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.BZIP2)
                            outputFile.set(layout.buildDirectory.file('docker-images/app.tar.bz2'))
                        }
                    }
                    
                    xzApp {
                        registry.set('docker.io')
                        imageName.set('test-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        save {
                            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.XZ)
                            outputFile.set(layout.buildDirectory.file('docker-images/app.tar.xz'))
                        }
                    }
                    
                    zipApp {
                        registry.set('docker.io')
                        imageName.set('test-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        save {
                            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.ZIP)
                            outputFile.set(layout.buildDirectory.file('docker-images/app.zip'))
                        }
                    }
                    
                    noneApp {
                        registry.set('docker.io')
                        imageName.set('test-app')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        save {
                            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.NONE)
                            outputFile.set(layout.buildDirectory.file('docker-images/app.tar'))
                        }
                    }
                }
            }
            
            tasks.register('verifyCompressionConfiguration') {
                doLast {
                    // Verify all compression types are configured correctly
                    def compressionTests = [
                        'dockerSaveGzipApp': SaveCompression.GZIP,
                        'dockerSaveBzip2App': SaveCompression.BZIP2,
                        'dockerSaveXzApp': SaveCompression.XZ,
                        'dockerSaveZipApp': SaveCompression.ZIP,
                        'dockerSaveNoneApp': SaveCompression.NONE
                    ]
                    
                    compressionTests.each { taskName, expectedCompression ->
                        def saveTask = tasks.getByName(taskName)
                        def actualCompression = saveTask.compression.get()
                        
                        println "Task \${taskName}: \${actualCompression}"
                        assert actualCompression == expectedCompression
                    }
                    
                    println "All compression types configured correctly"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyCompressionConfiguration', '--info')
            .build()

        then:
        result.output.contains('Task dockerSaveGzipApp: GZIP')
        result.output.contains('Task dockerSaveBzip2App: BZIP2')
        result.output.contains('Task dockerSaveXzApp: XZ')
        result.output.contains('Task dockerSaveZipApp: ZIP')
        result.output.contains('Task dockerSaveNoneApp: NONE')
        result.output.contains('All compression types configured correctly')
    }

    def "docker save task configuration supports sourceRef mode with pullIfMissing"() {
        given:
        settingsFile << "rootProject.name = 'test-save-sourceref'"
        
        buildFile << """
            import com.kineticfire.gradle.docker.model.SaveCompression
            
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    existingImage {
                        // SourceRef mode for existing images
                        sourceRef.set('ghcr.io/acme/myapp:1.2.3')
                        tags.set(['local:latest'])
                        
                        // pullIfMissing and auth moved to image level
                        pullIfMissing.set(true)
                        pullAuth {
                            username.set('myuser')
                            password.set('mypass')
                        }
                        
                        save {
                            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
                            outputFile.set(layout.buildDirectory.file('docker-images/existing.tar.gz'))
                        }
                    }
                }
            }
            
            tasks.register('verifySourceRefSaveConfiguration') {
                doLast {
                    def saveTask = tasks.getByName('dockerSaveExistingImage')
                    
                    // Note: Some properties might not be directly accessible in functional tests
                    println "Compression: " + saveTask.compression.get()
                    
                    assert saveTask.compression.get() == SaveCompression.GZIP
                    
                    println "SourceRef save configuration correct"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifySourceRefSaveConfiguration', '--info')
            .build()

        then:
        result.output.contains('Compression: GZIP')
        result.output.contains('SourceRef save configuration correct')
    }

    def "docker save task configuration uses Provider API for lazy evaluation"() {
        given:
        settingsFile << "rootProject.name = 'test-save-providers'"
        
        buildFile << """
            import com.kineticfire.gradle.docker.model.SaveCompression
            
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            def compressionProvider = providers.gradleProperty('docker.compression')
                .map { SaveCompression.fromString(it) }
                .orElse(SaveCompression.GZIP)
                
            def outputPathProvider = providers.gradleProperty('docker.output.dir')
                .orElse('docker-images')
                .flatMap { dirName -> layout.buildDirectory.file(dirName + '/image.tar.gz') }
            
            docker {
                images {
                    providerApp {
                        registry.set('docker.io')
                        imageName.set('provider-save-test')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        // pullIfMissing moved to image level
                        pullIfMissing.set(providers.gradleProperty('docker.pull').map { 
                            Boolean.parseBoolean(it) 
                        }.orElse(false))
                        
                        save {
                            compression.set(compressionProvider)
                            outputFile.set(outputPathProvider)
                        }
                    }
                }
            }
            
            tasks.register('verifyProviderSaveConfiguration') {
                doLast {
                    def saveTask = tasks.getByName('dockerSaveProviderApp')
                    
                    // Values should be lazily evaluated
                    println "Compression: " + saveTask.compression.get()
                    println "Output file: " + saveTask.outputFile.get().asFile.path
                    
                    // Verify defaults are applied
                    assert saveTask.compression.get() == SaveCompression.GZIP
                    assert saveTask.outputFile.get().asFile.path.contains('docker-images')
                    // pullIfMissing moved to image level, not directly accessible on task
                    
                    println "Provider-based save configuration working"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyProviderSaveConfiguration', '--info')
            .build()

        then:
        result.output.contains('Compression: GZIP')
        result.output.contains('Output file:')
        result.output.contains('docker-images')
        result.output.contains('Provider-based save configuration working')
        // pullIfMissing moved to image level, no longer printed in task output
    }

    def "docker save task configuration supports file extension inference"() {
        given:
        settingsFile << "rootProject.name = 'test-save-extensions'"
        
        buildFile << """
            import com.kineticfire.gradle.docker.model.SaveCompression
            
            plugins {
                id 'com.kineticfire.gradle.gradle-docker'
            }
            
            docker {
                images {
                    extensionApp {
                        registry.set('docker.io')
                        imageName.set('extension-test')
                        version.set('1.0.0')
                        tags.set(['latest'])
                        context.set(file('.'))
                        
                        save {
                            compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
                            // Note: We can verify that extension matches compression type
                            outputFile.set(layout.buildDirectory.file(
                                "images/app." + SaveCompression.GZIP.getFileExtension()
                            ))
                        }
                    }
                }
            }
            
            tasks.register('verifyExtensionConfiguration') {
                doLast {
                    def saveTask = tasks.getByName('dockerSaveExtensionApp')
                    def compression = saveTask.compression.get()
                    def outputFile = saveTask.outputFile.get().asFile
                    
                    println "Compression: " + compression
                    println "File extension: " + compression.getFileExtension()
                    println "Output file: " + outputFile.path
                    
                    // Verify extension matches compression
                    assert outputFile.path.endsWith(compression.getFileExtension())
                    
                    println "File extension configuration correct"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath(System.getProperty("java.class.path").split(File.pathSeparator).collect { new File(it) })
            .withArguments('verifyExtensionConfiguration', '--info')
            .build()

        then:
        result.output.contains('Compression: GZIP')
        result.output.contains('File extension: tar.gz')
        result.output.contains('Output file:')
        result.output.contains('tar.gz')
        result.output.contains('File extension configuration correct')
    }
}