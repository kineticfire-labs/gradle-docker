/*
 * (c) Copyright 2023 KineticFire. All rights reserved.
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

import java.io.File
import java.nio.file.Path

import org.gradle.api.Project
import spock.lang.Specification
import spock.lang.TempDir


/**
 * Unit tests.
 *
 */
class DockerUtilsTest extends Specification {

    static file String COMPOSE_VERSION = '3.1'
    static final String ALPINE_IMAGE_REF  = 'alpine:3.17.2' // using an image that can be pulled from Docker Hub
    static final String COMPOSE_FILE_NAME  = 'docker-compose.yml'


    @TempDir
    Path tempDir

    File composeFile


    def setupSpec( ) {
        // pull image used by multiple tests
        GradleExecUtils.exec( 'docker pull ' + ALPINE_IMAGE_REF )
    }


    //todo -- need?
    def cleanupSpec( ) {
    }


    def setup( ) {
        composeFile = new File( tempDir.toString( ) + File.separatorChar + COMPOSE_FILE_NAME )
    }


    def "getContainerHealth(String container) returns correctly when container not found"( ) {
        given:
        String containerName = 'container-shouldnt-exist'

        when:
        Map<String,String> result = DockerUtils.getContainerHealth( containerName )
        String health = result.get( 'health' )
        String reason = result.get( 'reason' )

        then:
        'none'.equals( health )
        'not-found'.equals( reason )
    }


    def "getContainerHealth(String container) returns correctly when container has no health check"( ) {
        given:
        String containerImageRef = ALPINE_IMAGE_REF
        String containerName = 'my-alpine-nohealth'

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectRunningCommand = 'docker inspect -f {{.State.Running}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        Map<String, String> result = GradleExecUtils.exec( dockerRunCommand )
        //todo check exitValue == 0

        int count = 0
        boolean isRunning = GradleExecUtils.exec( dockerInspectRunningCommand ).get( 'out' ).equals( 'true' )

        while ( !isRunning && count < 10 ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isRunning = GradleExecUtils.exec( dockerInspectRunningCommand ).get( 'out' ).equals( 'true' )
            count++
        }


        Map<String,String> healthResult = DockerUtils.getContainerHealth( containerName )
        String health = healthResult.get( 'health' )
        String reason = healthResult.get( 'reason' )


        GradleExecUtils.exec( dockerStopCommand )

        then:
        'none'.equals( health )
        'unknown'.equals( reason )
    }


    //todo
    def "getContainerHealth(String container) returns correctly when container has a health check and is starting"( ) {
        given:
        composeFile << """
            version '${COMPOSE_VERSION}'

            services:
              myalpine-healthy:
                image: ${ALPINE_IMAGE_REF}
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s
        """.stripIndent( )

    }


    //todo
    def "getContainerHealth(String container) returns correctly when container health is 'healthy'"( ) {
    }


    //todo
    def "getContainerHealth(String container) returns correctly when container health is 'unhealthy'"( ) {
    }


    def "getContainerState(String container) returns correctly when container not found"( ) {
        given:
        String containerName = 'container-shouldnt-exist'

        when:
        String state = DockerUtils.getContainerState( containerName ).get( 'state' )

        then:
        'not-found'.equals( state )
    }

    def "getContainerState(String container) returns correctly when container in 'running' state"( ) {
        given:
        String containerImageRef = ALPINE_IMAGE_REF
        String containerName = 'my-alpine'

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectRunningCommand = 'docker inspect -f {{.State.Running}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        Map<String, String> result = GradleExecUtils.exec( dockerRunCommand )
        //todo check exitValue == 0

        int count = 0
        boolean isRunning = GradleExecUtils.exec( dockerInspectRunningCommand ).get( 'out' ).equals( 'true' )

        while ( !isRunning && count < 10 ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isRunning = GradleExecUtils.exec( dockerInspectRunningCommand ).get( 'out' ).equals( 'true' )
            count++
        }


        String state = DockerUtils.getContainerState( containerName ).get( 'state' )

        GradleExecUtils.exec( dockerStopCommand )

        then:
        'running'.equals( state )
    }

}
