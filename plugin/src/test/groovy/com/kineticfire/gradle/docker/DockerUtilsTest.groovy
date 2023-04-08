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
import java.io.InputStream
import java.io.IOException
import java.nio.file.Path
import java.util.Random
import java.util.Map
import java.util.HashMap
import java.util.Properties

import org.gradle.api.Project
import spock.lang.Specification
import spock.lang.TempDir
import org.gradle.api.GradleException


/**
 * Unit tests.
 *
 */
class DockerUtilsTest extends Specification {

    private static final Properties properties = loadProperties( )

    static final String COMPOSE_VERSION = properties.get( 'testCompose.version' )

    static final String TEST_IMAGE_NAME = properties.get( 'testImage.name' )
    static final String TEST_IMAGE_VERSION = properties.get( 'testImage.version' )
    static final String TEST_IMAGE_REF = TEST_IMAGE_NAME + ':' + TEST_IMAGE_VERSION 

    static final String COMPOSE_FILE_NAME  = 'docker-compose.yml'

    // number of retries waiting for a container to reach a state or health status, with SLEEP_TIME in milliseconds
    // so total time is 44 seconds
    static final int NUM_RETRIES = 22 
    static final int SLEEP_TIME_MILLIS = 2000 

    // tolerance in milliseconds for delta time when testing 'waitForContainer' with 'retrySeconds' and 'retryNum'
    static final long TIME_TOLERANCE_MILLIS = 3000

    // create a postfix to append to container/service names to make them unique to this test run such that multiple concurrent tests can be run on the same system without name collisions
    static final String CONTAINER_NAME_POSTFIX = '-DockerUtilsTest-' + System.currentTimeMillis( ) + '-' + new Random( ).nextInt( 999999 )


    @TempDir
    Path tempDir

    File composeFile


    private static final Properties loadProperties( ) throws IOException {

        InputStream propertiesInputStream = DockerUtilsTest.class.getClassLoader( ).getResourceAsStream( 'config.properties' )
        Properties properties = new Properties( )

        if ( propertiesInputStream != null ) {

            try {
                properties.load( propertiesInputStream );
            } finally {
                propertiesInputStream.close( )
            }
        }


        return properties;

    }


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
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )
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

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
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

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'unhealthy' )

        while ( !isUnhealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
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

    def "getContainerHealth(String container) returns correctly when previously healthy container in 'exited' state"( ) {

        given:

        String containerName = 'getcontainerhealth-exited' + CONTAINER_NAME_POSTFIX

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
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }


        GradleExecUtils.execWithException( dockerStopCommand )

        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )
            count++
        }

        if ( !isExited ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "exited" state.' )
        }


        Map <String,String> healthResult = DockerUtils.getContainerHealth( containerName )

        then:
        healthResult.get( 'health' ).equals( 'unhealthy' )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "getContainerHealth(String container) returns correctly when healthy container in 'paused' state"( ) {

        given:

        String containerName = 'getcontainerhealth-paused' + CONTAINER_NAME_POSTFIX

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
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerPauseCommand = 'docker pause ' + containerName

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }

        GradleExecUtils.execWithException( dockerPauseCommand )

        count = 0
        boolean isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )

        while ( !isPaused && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )
            count++
        }

        if ( !isPaused ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "paused" state.' )
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
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )
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
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerPauseCommand = 'docker pause ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerPauseCommand )

        count = 0
        boolean isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )

        while ( !isPaused && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
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
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName
        String dockerRmCommand = 'docker rm ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerStopCommand )

        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
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
            Thread.sleep( SLEEP_TIME_MILLIS )
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


        //***********************************
        // waitForContainer( String container, String target )


            //***********************************
            // state

    def "waitForContainer(String container, String target) returns correctly for container in 'running' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-running-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running' )
        boolean success = result.get( 'success' )


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' not in "running" state.' )
        }


        then:
        success == true

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for not found container with target of 'running' state"( ) {
        given:
        String containerName = 'nosuchcontainer'

        when:
        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'not-found' )
        container.equals( containerName )
    }

    def "waitForContainer(String container, String target) returns correctly for container in 'exited' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-exited-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ] // intentionally not using '--rm' so container won't be removed when it exits
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName
        String dockerRmCommand = 'docker rm ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerStopCommand )

        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )
            count++
        }

        if ( !isExited ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "exited" state.' )
        }



        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'exited' )
        container.equals( containerName )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
        GradleExecUtils.exec( dockerRmCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for container in 'paused' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-paused-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerPauseCommand = 'docker pause ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerPauseCommand )

        count = 0
        boolean isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )

        while ( !isPaused && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )
            count++
        }

        if ( !isPaused ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "paused" state.' )
        }



        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'paused' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for container in 'created' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-created-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerCreateCommand = [ 'docker', 'create', '--name', containerName, containerImageRef ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerRmCommand = 'docker rm ' + containerName

        GradleExecUtils.execWithException( dockerCreateCommand )

        int count = 0
        boolean isCreated = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'created' )

        while ( !isCreated && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isCreated = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'created' )
            count++
        }

        if ( !isCreated ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "created" state.' )
        }



        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'created' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerRmCommand )
    }



    def "waitForContainer(String container, String target) returns correctly given an error with target of 'running' state"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        Map<String, String> result = DockerUtils.waitForContainer( additionalCommand, 'running' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'error' )
        message.contains( 'unknown flag' )
    }



            //***********************************
            // health

    def "waitForContainer(String container, String target) returns correctly for 'healthy' container with target of 'healthy'"( ) {
        given:

        String containerName = 'waitforcontainer-str-healthy-healthy' + CONTAINER_NAME_POSTFIX

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

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = result.get( 'success' )

        then:
        success == true

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for 'unhealthy' container with target of 'healthy'"( ) {
        given:

        String containerName = 'waitforcontainer-str-unhealthy-healthy' + CONTAINER_NAME_POSTFIX

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

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'unhealthy' )

        while ( !isUnhealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'unhealthy' )
            count++
        }

        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "unhealthy" status.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for container without health check with target of 'healthy'"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-nohealth-healthy' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'no-health-check' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for container not found with target of 'healthy'"( ) {
        given:
        String containerName = 'nosuchcontainer'

        when:
        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'not-found' )
        container.equals( containerName )
    }

    def "waitForContainer(String container, String target) returns correctly for preveiously healthy container in 'exited' state with target of 'healthy'"( ) {

        given:

        String containerName = 'waitforcontainer-str-exited-healthy' + CONTAINER_NAME_POSTFIX

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
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }


        GradleExecUtils.execWithException( dockerStopCommand )

        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )
            count++
        }

        if ( !isExited ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "exited" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for previously healthy container in 'paused' state with target of 'healthy'"( ) {

        given:

        String containerName = 'waitforcontainer-str-paused-healthy' + CONTAINER_NAME_POSTFIX

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
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerPauseCommand = 'docker pause ' + containerName

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }

        GradleExecUtils.execWithException( dockerPauseCommand )

        count = 0
        boolean isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )

        while ( !isPaused && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )
            count++
        }

        if ( !isPaused ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "paused" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target) returns correctly given an error with target of 'healthy'"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        Map<String, String> result = DockerUtils.waitForContainer( additionalCommand, 'healthy' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'error' )
        message.contains( 'unknown flag' )
    }


            //***********************************
            // error

    def "waitForContainer(String container, String target) returns correctly given invalid target disposition as string"( ) {
        given:
        String containerName = 'container-doesnt-matter'

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerName, 'invalid-disposition' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }

    def "waitForContainer(String container, String target) returns correctly given invalid target disposition as null"( ) {
        given:
        String containerName = 'container-doesnt-matter'

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerName, null )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }



        //***********************************
        // waitForContainer( String container, String target, int retrySeconds, int retryNum )


            //***********************************
            // state

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container in 'running' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-running-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', 2, 22 )
        boolean success = result.get( 'success' )


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' not in "running" state.' )
        }


        then:
        success == true

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for not found container with target of 'running' state"( ) {
        given:
        String containerName = 'nosuchcontainer'

        when:
        long start = System.currentTimeMillis( ) 
        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', 3, 3 )
        long diff = System.currentTimeMillis( ) - start 
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'not-found' )
        container.equals( containerName )
        ( diff >= ( 9000L - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( 9000L + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container in 'exited' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-exited-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ] // intentionally not using '--rm' so container won't be removed when it exits
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName
        String dockerRmCommand = 'docker rm ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerStopCommand )

        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExited = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'exited' )
            count++
        }

        if ( !isExited ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "exited" state.' )
        }



        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', 2, 22 )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'exited' )
        container.equals( containerName )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
        GradleExecUtils.exec( dockerRmCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container in 'paused' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-paused-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerPauseCommand = 'docker pause ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerPauseCommand )

        count = 0
        boolean isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )

        while ( !isPaused && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isPaused = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'paused' )
            count++
        }

        if ( !isPaused ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "paused" state.' )
        }



        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', 2, 22 )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'paused' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container in 'created' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-created-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerCreateCommand = [ 'docker', 'create', '--name', containerName, containerImageRef ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerRmCommand = 'docker rm ' + containerName

        GradleExecUtils.execWithException( dockerCreateCommand )

        int count = 0
        boolean isCreated = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'created' )

        while ( !isCreated && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isCreated = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'created' )
            count++
        }

        if ( !isCreated ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "created" state.' )
        }



        long start = System.currentTimeMillis( ) 
        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', 3, 3 )
        long diff = System.currentTimeMillis( ) - start 
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'created' )
        container.equals( containerName )
        ( diff >= ( 9000L - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( 9000L + TIME_TOLERANCE_MILLIS ) )

        cleanup:
        GradleExecUtils.exec( dockerRmCommand )
    }



    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given an error with target of 'running' state"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        Map<String, String> result = DockerUtils.waitForContainer( additionalCommand, 'running', 2, 22 )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'error' )
        message.contains( 'unknown flag' )
    }



            //***********************************
            // health

            //todo


            //***********************************
            // error

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given invalid target disposition as string"( ) {
        given:
        String containerName = 'container-doesnt-matter'

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerName, 'invalid-disposition', 2, 4 )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given invalid target disposition as null"( ) {
        given:
        String containerName = 'container-doesnt-matter'

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerName, null, 2, 4 )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }



        //***********************************
        // waitForContainer( Map<String,String> container )



            //***********************************
            // state

            // todo for single container, need to update for map e.g group of containers
            // container running - success
            // container not found
            // container exited
            // container paused
            // container created
            // command err


            //***********************************
            // health



            // todo for single container, need to update for map e.g group of containers
            // healthy
            // unhealthy
            // no healthcheck
            // not found - num-retries-exceeded
            // exited
            // paused
            // created - num-retries-exceeded
            // error


            //***********************************
            // both


            //todo


            //***********************************
            // error

    def "waitForContainer(Map<String,String> containerMap) returns correctly given no containers to monitor"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )

        then:
        success == true
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly given invalid target disposition as string"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )
        String containerName = 'container-doesnt-matter'
        containerMap.put( containerName, 'invalid-disposition' )

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly given invalid target disposition as null"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )
        String containerName = 'container-doesnt-matter'
        containerMap.put( containerName, null )

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }



        //***********************************
        // waitForContainer( Map<String,String> container, String target, int retrySeconds, int retryNum )



            //***********************************
            // state

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

    //todo

            //***********************************
            // health

    //todo

            //***********************************
            // both

    //todo

            //***********************************
            // error

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly given no containers to monitor"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerMap, 2, 4 )
        boolean success = result.get( 'success' )

        then:
        success == true
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly given invalid target disposition as string"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )
        String containerName = 'container-doesnt-matter'
        containerMap.put( containerName, 'invalid-disposition' )

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerMap, 2, 4 )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly given invalid target disposition as null"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )
        String containerName = 'container-doesnt-matter'
        containerMap.put( containerName, null )

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerMap, 2, 4 )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }


}
