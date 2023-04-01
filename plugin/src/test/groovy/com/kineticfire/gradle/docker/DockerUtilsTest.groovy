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
import org.gradle.api.GradleException


/**
 * Unit tests.
 *
 */
class DockerUtilsTest extends Specification {

    static final String COMPOSE_VERSION = '3.7'
    static final String ALPINE_IMAGE_REF  = 'alpine:3.17.2' // using an image that can be pulled from Docker Hub
    static final String COMPOSE_FILE_NAME  = 'docker-compose.yml'


    @TempDir
    Path tempDir

    File composeFile


    def setupSpec( ) {
        // pull image used by multiple tests
        GradleExecUtils.exec( 'docker pull ' + ALPINE_IMAGE_REF )
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

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )

        while ( !isRunning && count < 10 ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


        Map<String,String> healthResult = DockerUtils.getContainerHealth( containerName )
        String health = healthResult.get( 'health' )
        String reason = healthResult.get( 'reason' )


        GradleExecUtils.execWithException( dockerStopCommand )

        then:
        'none'.equals( health )
        'unknown'.equals( reason )

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }


    // not testing the health status 'starting' because it is difficult to hold that state for the test
    /*
    def "getContainerHealth(String container) returns correctly when container has a health check and is starting"( ) {
    }
    */


    def "getContainerHealth(String container) returns correctly when container health is 'healthy'"( ) {

        given:

        String containerName = 'myalpine-healthy'

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerName}:
                container_name: ${containerName}
                image: ${ALPINE_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s
        """.stripIndent( )

        when:
        String[] dockerComposeUpCommand = [ 'docker-compose', '-f', composeFile, 'up', '-d' ]
        String dockerInspectHealthCommand = 'docker inspect -f {{.State.Health.Status}} ' + containerName
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]

        String result = GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        while ( !isHealthy && count < 10 ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }


        Map <String,String> healthResult = DockerUtils.getContainerHealth( containerName )

        then:
        healthResult.get( 'health' ).equals( 'healthy' )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


    def "getContainerHealth(String container) returns correctly when container health is 'unhealthy'"( ) {

        given:

        String containerName = 'myalpine-unhealthy'

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerName}:
                container_name: ${containerName}
                image: ${ALPINE_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 1
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s
        """.stripIndent( )

        when:
        String[] dockerComposeUpCommand = [ 'docker-compose', '-f', composeFile, 'up', '-d' ]
        String dockerInspectHealthCommand = 'docker inspect -f {{.State.Health.Status}} ' + containerName
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]

        String result = GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'unhealthy' )

        while ( !isUnhealthy && count < 10 ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'unhealthy' )
            count++
        }

        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "unhealthy" status.' )
        }


        Map <String,String> healthResult = DockerUtils.getContainerHealth( containerName )

        then:
        healthResult.get( 'health' ).equals( 'unhealthy' )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
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

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )

        while ( !isRunning && count < 10 ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


        String state = DockerUtils.getContainerState( containerName ).get( 'state' )

        GradleExecUtils.execWithException( dockerStopCommand )

        then:
        'running'.equals( state )

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

}
