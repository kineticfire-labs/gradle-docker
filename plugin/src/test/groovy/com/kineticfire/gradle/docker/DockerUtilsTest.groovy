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
import java.util.Random
import java.util.Map
import java.util.HashMap

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

    // using an image that can be pulled from Docker Hub
    static final String TEST_IMAGE_NAME = 'alpine'
    static final String TEST_IMAGE_VERSION = '3.17.2'
    static final String TEST_IMAGE_REF = TEST_IMAGE_NAME + ':' + TEST_IMAGE_VERSION 

    static final String COMPOSE_FILE_NAME  = 'docker-compose.yml'

    // number of retries waiting for a container to reach a state or health status
    // with a 2-second sleep time, total time is 44 seconds
    static final int NUM_RETRIES = 22 

    // create a postfix to append to container/service names to make them unique to this test run such that multiple concurrent tests can be run on the same system without name collisions
    static final String CONTAINER_NAME_POSTFIX = '-DockerUtilsTest-' + System.currentTimeMillis( ) + '-' + new Random( ).nextInt( 9999 )


    //todo put versions into external file?

    @TempDir
    Path tempDir

    File composeFile


    def setupSpec( ) {
        // pull image used by multiple tests
        GradleExecUtils.exec( 'docker pull ' + TEST_IMAGE_REF )
    }


    def setup( ) {
        composeFile = new File( tempDir.toString( ) + File.separatorChar + COMPOSE_FILE_NAME )
    }


    //***********************************
    //***********************************
    //***********************************
    // getContainerHealth

    def "getContainerHealth(String container) returns correctly when container not found"( ) {
        given:
        String containerName = 'container-shouldnt-exist'

        when:
        Map<String,String> result = DockerUtils.getContainerHealth( containerName )
        String health = result.get( 'health' )

        then:
        'not-found'.equals( health )
    }


    def "getContainerHealth(String container) returns correctly when container has no health check"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'getcontainerhealth-nohealth' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectRunningCommand = 'docker inspect -f {{.State.Running}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


        Map<String,String> healthResult = DockerUtils.getContainerHealth( containerName )
        String health = healthResult.get( 'health' )


        GradleExecUtils.execWithException( dockerStopCommand )

        then:
        'no-healthcheck'.equals( health )

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

        String containerName = 'getcontainerhealth-healthy' + CONTAINER_NAME_POSTFIX

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerName}:
                container_name: ${containerName}
                image: ${TEST_IMAGE_REF}
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

        while ( !isHealthy && count < NUM_RETRIES ) {
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

        String containerName = 'getcontainerhealth-unhealthy' + CONTAINER_NAME_POSTFIX

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerName}:
                container_name: ${containerName}
                image: ${TEST_IMAGE_REF}
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

        while ( !isUnhealthy && count < NUM_RETRIES ) {
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

    def "getContainerHealth(String container) returns correctly when command is in error"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        Map<String,String> result = DockerUtils.getContainerHealth( additionalCommand )
        String health = result.get( 'health' )
        String reason = result.get( 'reason' )

        then:
        'error'.equals( health )
        reason.contains( 'unknown flag' )
    }



    //***********************************
    //***********************************
    //***********************************
    // getContainerState


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
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'getcontainerstate-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectRunningCommand = 'docker inspect -f {{.State.Running}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )

        while ( !isRunning && count < NUM_RETRIES ) {
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

    def "getContainerState(String container) returns correctly when container in 'paused' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'getcontainerstate-paused' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectRunningCommand = 'docker inspect -f {{.State.Running}} ' + containerName
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerPauseCommand = 'docker pause ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerPauseCommand )

        count = 0
        boolean isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )

        while ( !isPaused && count < NUM_RETRIES ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )
            count++
        }

        if ( !isPaused ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "paused" state.' )
        }



        String state = DockerUtils.getContainerState( containerName ).get( 'state' )

        then:
        'paused'.equals( state )

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "getContainerState(String container) returns correctly when container in 'exited' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'getcontainerstate-exited' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ] // intentionally not using '--rm' so container won't be removed when it exits
        String dockerInspectRunningCommand = 'docker inspect -f {{.State.Running}} ' + containerName
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName
        String dockerRmCommand = 'docker rm ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isRunning = GradleExecUtils.execWithException( dockerInspectRunningCommand ).equals( 'true' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerStopCommand )

        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )
            count++
        }

        if ( !isExited ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "exited" state.' )
        }



        String state = DockerUtils.getContainerState( containerName ).get( 'state' )

        then:
        'exited'.equals( state )

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
        GradleExecUtils.exec( dockerRmCommand )
    }

    def "getContainerState(String container) returns correctly when container in 'created' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'getcontainerstate-created' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerCreateCommand = [ 'docker', 'create', '--name', containerName, containerImageRef ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerRmCommand = 'docker rm ' + containerName

        GradleExecUtils.execWithException( dockerCreateCommand )

        int count = 0
        boolean isCreated = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'created' )

        while ( !isCreated && count < NUM_RETRIES ) {
            Thread.sleep( 2000 ) // wait 2 seconds
            isCreated = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'created' )
            count++
        }

        if ( !isCreated ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "created" state.' )
        }



        String state = DockerUtils.getContainerState( containerName ).get( 'state' )

        then:
        'created'.equals( state )

        cleanup:
        GradleExecUtils.exec( dockerRmCommand )
    }


    // not testing because hard to maintain 'restarting' state
    /*
    def "getContainerState(String container) returns correctly when container in 'restarting' state"( ) {
    }
    */

    // not testing because hard to re-create 'dead' state
    /*
    def "getContainerState(String container) returns correctly when container in 'dead' state"( ) {
    }
    */

    def "getContainerState(String container) returns correctly when command is in error"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        Map<String,String> result = DockerUtils.getContainerState( additionalCommand )
        String state = result.get( 'state' )
        String reason = result.get( 'reason' )

        then:
        'error'.equals( state )
        reason.contains( 'unknown flag' )
    }




    //***********************************
    //***********************************
    //***********************************
    // waitForContainer



    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly when container not found for target state"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )
        String containerName = 'container-shouldnt-exist'
        containerMap.put( containerName, 'running' )

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerMap, 2, 4 )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'num-retries-exceeded'.equals( reason )
        'not-found'.equals( message )
        containerName.equals( container )
    }


    //***********************************
    //***********************************
    //***********************************
    // waitForContainer



}
