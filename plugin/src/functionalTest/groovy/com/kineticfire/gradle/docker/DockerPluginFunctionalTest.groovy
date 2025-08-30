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
/*
 * KineticFire Labs: https://labs.kineticfire.com/
 *     project site: https://github.com/kineticfire-labs/gradle-docker/
 */
package com.kineticfire.gradle.docker

import spock.lang.Specification
import spock.lang.TempDir
import org.gradle.testkit.runner.GradleRunner

/**
 * functional test
 */
class DockerPluginFunctionalTest extends Specification {

    @TempDir
    private File projectDir

    private getBuildFile() {
        new File( projectDir, 'build.gradle' )
    }

    private getSettingsFile() {
        new File( projectDir, 'settings.gradle' )
    }

    def "can run dockerClean task"( ) {
        given:

        settingsFile << ""

        buildFile << """

plugins {
    id( 'com.kineticfire.docker' )
}

docker {
}

"""

        when:
        def runner = GradleRunner.create( )
        runner.forwardOutput( )
        runner.withPluginClasspath( )
        runner.withArguments( 'dockerClean' )
        runner.withProjectDir( projectDir )
        def result = runner.build( )

        then:
        result.output.contains( 'Hi from DockerCleanTask' )
    }

    def "can run dockerPrepareBuild task"( ) {
        given:

        settingsFile << ""

        buildFile << """

plugins {
    id( 'com.kineticfire.docker' )
}

docker {
}

"""

        when:
        def runner = GradleRunner.create( )
        runner.forwardOutput( )
        runner.withPluginClasspath( )
        runner.withArguments( 'dockerPrepareBuild' )
        runner.withProjectDir( projectDir )
        def result = runner.build( )

        then:
        result.output.contains( 'Hi from DockerPrepareBuildTask' )
    }


    def "can run dockerSave task"( ) {
        given:
        String imageName = 'alpine:3.17.2' //todo
        String imageFileName = '/home/user/test-1.0.tar.gz' //todo

        settingsFile << ""

        buildFile << """

plugins {
    id( 'com.kineticfire.docker' )
}

docker {
    saveImageName 'alpine:3.17.2'
    saveImageFilename '/home/user/test-1.0.tar.gz'
}

"""

        when:
        def runner = GradleRunner.create( )
        runner.forwardOutput( )
        runner.withPluginClasspath( )
        runner.withArguments( 'dockerSave' )
        runner.withProjectDir( projectDir )
        def result = runner.build( )

        then:
        result.output.contains( 'Hi from DockerSaveTask' )
    }
}
