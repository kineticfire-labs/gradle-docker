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
import org.gradle.api.GradleException

import spock.lang.Specification
import spock.lang.TempDir


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

    // Settings to allow sufficient time for containers to reach 'running' state and optionally 'healthy/unhealthy' status.  Maximum wait time is 44000 milliseconds = 44 seconds.  Using same numbers from defaults originally set in DockerUtils.groovy, which could change and be different than these settings. 
        // Used in two cases:
        // (1) When this test implements its own loop to check for a container disposition, uses NUM_RETRIES and SLEEP_TIME_MILLIS
        // (2) When testing method waitForContainer(..., retrySeconds, retryNum) and don't expect a timeout, uses SLEEP_TIME_SECONDS and NUM_RETRIES
    static final int NUM_RETRIES = 22 
    static final long SLEEP_TIME_MILLIS = 2000L
    static final int SLEEP_TIME_SECONDS = 2

    // Expected wait time in milliseconds for a query to timeout using default timeout settings in DockerUtils.groovy, which is 22 retries with 2 seconds of sleep time = 44000 milliseconds = 44 seconds.  If the default in DockerUtils.groovy is changed, then this value needs to be updated.
    static final long TIME_WAIT_EXPECTED_MILLIS = 44000L

    // Settings used when testing a method that is expected to timeout, to help speed testing, since what is being tested is the method's loop counter and sleep time.
    static final int FAST_FAIL_NUM_RETRIES = 2
    static final int FAST_FAIL_SLEEP_TIME_SECONDS = 2
    static final long FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS = 4000L

    // Tolerance in milliseconds for delta time (e.g., +/- a diff time) when testing 'waitForContainer' with 'retrySeconds' and 'retryNum'
    static final long TIME_TOLERANCE_MILLIS = 3000L
    // commented out those lines measuring time diff, because there seemed to be too much variation in time diff based on tests and system usage

    // create a postfix to append to container/service names to make them unique to this test run such that multiple concurrent tests can be run on the same system without name collisions
    static final String CONTAINER_NAME_POSTFIX = '-DockerUtilsTest-' + System.currentTimeMillis( ) + '-' + new Random( ).nextInt( 999999 )


    @TempDir
    Path tempDir

    File composeFile
    File composeFile2


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
        composeFile2 = new File( tempDir.toString( ) + File.separatorChar + COMPOSE_FILE_NAME + '2' )
    }


    //***********************************
    //***********************************
    //***********************************
    // getContainerHealth

    /* comment
    def "getContainerHealth(String container) returns correctly when container not found"( ) {
        given:
        String containerName = 'container-shouldnt-exist'

        when:
        def resultMap = DockerUtils.getContainerHealth( containerName )
        String health = resultMap.health

        then:
        resultMap instanceof Map
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


        def healthResultMap = DockerUtils.getContainerHealth( containerName )
        String health = healthResultMap.health


        GradleExecUtils.execWithException( dockerStopCommand )

        then:
        healthResultMap instanceof Map
        'no-healthcheck'.equals( health )

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    comment */

    // not testing the health status 'starting' because it is difficult to hold that state for the test
    /*
    def "getContainerHealth(String container) returns correctly when container has a health check and is starting"( ) {
    }
    */

    /* comment

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


        def healthResultMap = DockerUtils.getContainerHealth( containerName )


        then:
        healthResultMap instanceof Map
        healthResultMap.health.equals( 'healthy' )

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


        def healthResultMap = DockerUtils.getContainerHealth( containerName )

        then:
        healthResultMap instanceof Map
        healthResultMap.health.equals( 'unhealthy' )

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


        def healthResultMap = DockerUtils.getContainerHealth( containerName )

        then:
        healthResultMap instanceof Map
        healthResultMap.health.equals( 'unhealthy' )

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
        String dockerUnpauseCommand = 'docker pause ' + containerName
        String dockerStopCommand = 'docker unpause ' + containerName
        String dockerRemoveCommand = 'docker remove ' + containerName

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


        def healthResultMap = DockerUtils.getContainerHealth( containerName )

        then:
        healthResultMap instanceof Map
        healthResultMap.health.equals( 'unhealthy' )

        cleanup:
        GradleExecUtils.exec( dockerUnpauseCommand )
        GradleExecUtils.exec( dockerComposeDownCommand )
        GradleExecUtils.exec( dockerStopCommand )
        GradleExecUtils.exec( dockerRemoveCommand )
    }

    def "getContainerHealth(String container) returns correctly when command is in error"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        def resultMap = DockerUtils.getContainerHealth( additionalCommand )
        String health = resultMap.health
        String reason = resultMap.reason

        then:
        resultMap instanceof Map
        'error'.equals( health )
        reason.contains( 'unknown flag' )
    }

    comment */


    //***********************************
    //***********************************
    //***********************************
    // getContainerState

        /* comment

    def "getContainerState(String container) returns correctly when container not found"( ) {
        given:
        String containerName = 'container-shouldnt-exist'

        when:
        def responseMap = DockerUtils.getContainerState( containerName )
        String state = responseMap.state

        then:
        responseMap instanceof Map
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


        def responseMap = DockerUtils.getContainerState( containerName )
        String state = responseMap.state

        GradleExecUtils.execWithException( dockerStopCommand )

        then:
        responseMap instanceof Map
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
        String dockerUnpauseCommand = 'docker pause ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName
        String dockerRemoveCommand = 'docker remove ' + containerName

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



        def responseMap = DockerUtils.getContainerState( containerName )
        String state = responseMap.state

        then:
        responseMap instanceof Map
        'paused'.equals( state )

        cleanup:
        GradleExecUtils.exec( dockerUnpauseCommand )
        GradleExecUtils.exec( dockerStopCommand )
        GradleExecUtils.exec( dockerRemoveCommand )
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



        def responseMap = DockerUtils.getContainerState( containerName )
        String state = responseMap.state

        then:
        responseMap instanceof Map
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



        def responseMap = DockerUtils.getContainerState( containerName )
        String state = responseMap.state

        then:
        responseMap instanceof Map
        'created'.equals( state )

        cleanup:
        GradleExecUtils.exec( dockerRmCommand )
    }

    comment */


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

    /* comment
    def "getContainerState(String container) returns correctly when command is in error"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        def resultMap = DockerUtils.getContainerState( additionalCommand )
        String state = resultMap.state
        String reason = resultMap.reason

        then:
        resultMap instanceof Map
        'error'.equals( state )
        reason.contains( 'unknown flag' )
    }

    comment */




    //***********************************
    //***********************************
    //***********************************
    // waitForContainer


        //***********************************
        // waitForContainer( String container, String target )


            //***********************************
            // state

        /* comment
    def "waitForContainer(String container, String target) returns correctly for container in 'running' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-running-running' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )


        def resultMap = DockerUtils.waitForContainer( containerName, 'running' )
        boolean success = resultMap.success


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' not in "running" state.' )
        }


        then:
        resultMap instanceof Map
        success == true

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for not found container with target of 'running' state"( ) {
        given:
        String containerName = 'nosuchcontainer'

        when:
        long start = System.currentTimeMillis( )
        def resultMap = DockerUtils.waitForContainer( containerName, 'running' )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container
        //( diff >= ( TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )


        then:
        resultMap instanceof Map
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



        def resultMap = DockerUtils.waitForContainer( containerName, 'running' )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
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
        String dockerUnpauseCommand = 'docker unpause ' + containerName
        String dockerRemoveCommand = 'docker rm ' + containerName

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



        def resultMap = DockerUtils.waitForContainer( containerName, 'running' )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'failed' )
        message.equals( 'paused' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerUnpauseCommand )
        GradleExecUtils.exec( dockerStopCommand )
        GradleExecUtils.exec( dockerRemoveCommand )
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


        long start = System.currentTimeMillis( )
        def resultMap = DockerUtils.waitForContainer( containerName, 'running' )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container



        boolean isCreated = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'created' )

        if ( !isCreated ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "created" state.' )
        }


        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'created' )
        container.equals( containerName )
        //( diff >= ( TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )

        cleanup:
        GradleExecUtils.exec( dockerRmCommand )
    }



    def "waitForContainer(String container, String target) returns correctly given an error with target of 'running' state"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        def resultMap = DockerUtils.waitForContainer( additionalCommand, 'running' )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'error' )
        message.contains( 'unknown flag' )
    }

    comment */

        /* todo below!


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


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = result.get( 'success' )


        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }

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


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'unhealthy' )

        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "unhealthy" status.' )
        }

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


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


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
        long start = System.currentTimeMillis( )
        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy' )
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
        //( diff >= ( TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(String container, String target) returns correctly for previously healthy container in 'exited' state with target of 'healthy'"( ) {

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
            // error (not related to target disposition)

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


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', SLEEP_TIME_SECONDS, NUM_RETRIES )
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
        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
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



        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', SLEEP_TIME_SECONDS, NUM_RETRIES )
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



        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', SLEEP_TIME_SECONDS, NUM_RETRIES )
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


        long start = System.currentTimeMillis( ) 
        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'running', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start 
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        boolean isCreated = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'created' )

        if ( !isCreated ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "created" state.' )
        }


        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'created' )
        container.equals( containerName )
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )

        cleanup:
        GradleExecUtils.exec( dockerRmCommand )
    }



    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given an error with target of 'running' state"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        Map<String, String> result = DockerUtils.waitForContainer( additionalCommand, 'running', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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


    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for 'healthy' container with target of 'healthy'"( ) {
        given:

        String containerName = 'waitforcontainer-str-int-healthy-healthy' + CONTAINER_NAME_POSTFIX

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

        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )

        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }


        then:
        success == true

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for 'unhealthy' container with target of 'healthy'"( ) {
        given:

        String containerName = 'waitforcontainer-str-int-unhealthy-healthy' + CONTAINER_NAME_POSTFIX

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

        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'unhealthy' )

        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "unhealthy" status.' )
        }

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container without health check with target of 'healthy'"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-nohealth-healthy' + CONTAINER_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


        then:
        success == false
        reason.equals( 'no-health-check' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container not found with target of 'healthy'"( ) {
        given:
        String containerName = 'nosuchcontainer'

        when:
        long start = System.currentTimeMillis( )
        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for previously healthy container in 'exited' state with target of 'healthy'"( ) {

        given:

        String containerName = 'waitforcontainer-str-int-exited-healthy' + CONTAINER_NAME_POSTFIX

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


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
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

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for previously healthy container in 'paused' state with target of 'healthy'"( ) {

        given:

        String containerName = 'waitforcontainer-str-int-paused-healthy' + CONTAINER_NAME_POSTFIX

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


        Map<String, String> result = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
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

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given an error with target of 'healthy'"( ) {
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
            // error (not related to target disposition)

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given invalid target disposition as string"( ) {
        given:
        String containerName = 'container-doesnt-matter'

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerName, 'invalid-disposition', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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
        Map<String,String> result = DockerUtils.waitForContainer( containerName, null, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container in 'running' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerGood1Name = 'waitforcontainer-map-running-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-running-running-good2' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-running-running-good3' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )
        containerMap.put( containerGood3Name, 'running' )

        when:
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood3 = [ 'docker', 'run', '--rm', '--name', containerGood3Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerInspectCommandGood3 = 'docker inspect -f {{.State.Status}} ' + containerGood3Name
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name
        String dockerStopCommandGood3 = 'docker stop ' + containerGood3Name

        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )
        GradleExecUtils.execWithException( dockerRunCommandGood3 )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )


        boolean isRunningGood1 = GradleExecUtils.execWithException( dockerInspectCommandGood1 ).equals( 'running' )
        boolean isRunningGood2 = GradleExecUtils.execWithException( dockerInspectCommandGood2 ).equals( 'running' )
        boolean isRunningGood3 = GradleExecUtils.execWithException( dockerInspectCommandGood3 ).equals( 'running' )

        if ( !isRunningGood1 ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' not in "running" state.' )
        }

        if ( !isRunningGood2 ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' not in "running" state.' )
        }

        if ( !isRunningGood3 ) {
            throw new GradleException( 'Docker container ' + containerGood3Name + ' not in "running" state.' )
        }


        then:
        success == true

        cleanup:
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
        GradleExecUtils.exec( dockerStopCommandGood3 )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly when container not found for target state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'container-shouldnt-exist'

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerName, 'running' )

        when:
        long start = System.currentTimeMillis( )
        Map<String,String> result = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'num-retries-exceeded'.equals( reason )
        'not-found'.equals( message )
        containerName.equals( container )
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container in 'exited' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-exited-running-bad' + CONTAINER_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-exited-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-exited-running-good2' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )

        when:
        String[] dockerRunCommandBad = [ 'docker', 'run', '--name', containerBadName, '-d', containerImageRef, 'tail', '-f' ] // intentionally not using '--rm' so container won't be removed when it exits
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerStopCommandBad = 'docker stop ' + containerBadName
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name
        String dockerRmCommandBad = 'docker rm ' + containerBadName


        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )
        GradleExecUtils.execWithException( dockerRunCommandBad )


        int count = 0
        boolean isRunningBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )

        while ( !isRunningBad && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunningBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )
            count++
        }

        if ( !isRunningBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerStopCommandBad )

        count = 0
        boolean isExitedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'exited' )

        while ( !isExitedBad && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExitedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'exited' )
            count++
        }

        if ( !isExitedBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "exited" state.' )
        }



        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'exited' )
        container.equals( containerBadName )


        cleanup:
        GradleExecUtils.exec( dockerStopCommandBad )
        GradleExecUtils.exec( dockerRmCommandBad )
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container in 'paused' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-paused-running-bad' + CONTAINER_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-paused-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-paused-running-good2' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )

        when:
        String[] dockerRunCommandBad = [ 'docker', 'run', '--rm', '--name', containerBadName, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerStopCommandBad = 'docker stop ' + containerBadName
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name
        String dockerPauseCommandBad = 'docker pause ' + containerBadName


        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )
        GradleExecUtils.execWithException( dockerRunCommandBad )


        int count = 0
        boolean isRunningBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )

        while ( !isRunningBad && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunningBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )
            count++
        }

        if ( !isRunningBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }


        GradleExecUtils.execWithException( dockerPauseCommandBad )


        count = 0
        boolean isPausedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'paused' )

        while ( !isPausedBad && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isPausedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'paused' )
            count++
        }

        if ( !isPausedBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "paused" state.' )
        }



        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'paused' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerStopCommandBad )
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container in 'created' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-created-running-bad' + CONTAINER_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-created-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-created-running-good2' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )

        when:
        String[] dockerCreateCommandBad = [ 'docker', 'create', '--name', containerBadName, containerImageRef ]
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerRmCommandBad = 'docker rm ' + containerBadName
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name


        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )
        GradleExecUtils.execWithException( dockerCreateCommandBad )


        long start = System.currentTimeMillis( )
        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        long diff = System.currentTimeMillis( ) - start
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        boolean isCreatedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'created' )

        if ( !isCreatedBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "created" state.' )
        }

        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'created' )
        container.equals( containerBadName )
        //( diff >= ( TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )

        cleanup:
        GradleExecUtils.exec( dockerRmCommandBad )
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly given an error with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        // adding invalid '--blah' flag to produce command error
        String containerBadName = '--blah' + TEST_IMAGE_REF
        String containerGood1Name = 'waitforcontainer-map-created-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-created-running-good2' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )

        when:
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name



        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommandGood1 ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommandGood1 ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "running" state.' )
        }


        count = 0
        isRunning = GradleExecUtils.execWithException( dockerInspectCommandGood2 ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommandGood2 ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "running" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'error' )
        message.contains( 'unknown flag' )

        cleanup:
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }



            //***********************************
            // health

    def "waitForContainer(Map<String,String> containerMap) returns correctly for 'healthy' container with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-healthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-healthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-healthy-healthy-good3' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerGood3Name, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood3Name}:
                container_name: ${containerGood3Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandGood1 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood1Name
        String dockerInspectHealthCommandGood2 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood2Name
        String dockerInspectHealthCommandGood3 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood3Name


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )


        boolean isHealthy1 = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )
        if ( !isHealthy1 ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "healthy" status.' )
        }

        boolean isHealthy2 = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )
        if ( !isHealthy2 ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "healthy" status.' )
        }

        boolean isHealthy3 = GradleExecUtils.execWithException( dockerInspectHealthCommandGood3 ).equals( 'healthy' )
        if ( !isHealthy3 ) {
            throw new GradleException( 'Docker container ' + containerGood3Name + ' did not reach "healthy" status.' )
        }


        then:
        success == true

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for 'unhealthy' container with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-unhealthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-unhealthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-unhealthy-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandGood1 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood1Name
        String dockerInspectHealthCommandGood2 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood2Name
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )


        boolean isUnhealthyBad = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'unhealthy' )

        if ( !isUnhealthyBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "unhealthy" status.' )
        }

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container without health check with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-unhealthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-unhealthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-nohealth-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
                image: ${TEST_IMAGE_REF}
                command: tail -f
        """.stripIndent( )


        when:
        String[] dockerComposeUpCommand = [ 'docker-compose', '-f', composeFile, 'up', '-d' ]
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }


        then:
        success == false
        reason.equals( 'no-health-check' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container not found with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-notfound-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-notfound-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'nosuchcontainer'

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandGood1 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood1Name
        String dockerInspectHealthCommandGood2 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood2Name
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "healthy" status.' )
        }

        count = 0
        isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "healthy" status.' )
        }


        long start = System.currentTimeMillis( )
        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        long diff = System.currentTimeMillis( ) - start
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'not-found' )
        container.equals( containerBadName )
        //( diff >= ( TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for previously healthy container in 'exited' state with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-exited-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-exited-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-exited-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName
        String dockerInspectStateCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerStopCommandBad = 'docker stop ' + containerBadName

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "healthy" status.' )
        }


        GradleExecUtils.execWithException( dockerStopCommandBad )


        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExited = GradleExecUtils.execWithException( dockerInspectStateCommandBadCommand ).equals( 'exited' )
            count++
        }

        if ( !isExited ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "exited" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for previously healthy container in 'paused' state with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-paused-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-paused-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-paused-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName
        String dockerInspectStateCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerPauseCommandBad = 'docker pause ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "healthy" status.' )
        }

        GradleExecUtils.execWithException( dockerPauseCommandBad )

        count = 0
        boolean isPaused = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'paused' )

        while ( !isPaused && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isPaused = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'paused' )
            count++
        }

        if ( !isPaused ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "paused" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly given an error with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-error-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-error-healthy-good2' + CONTAINER_NAME_POSTFIX
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( additionalCommand, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandGood1 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood1Name
        String dockerInspectHealthCommandGood2 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood2Name


        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "healthy" status.' )
        }

        count = 0
        isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "healthy" status.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )

        then:
        success == false
        reason.equals( 'error' )
        message.contains( 'unknown flag' )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


            //***********************************
            // both

    def "waitForContainer(Map<String,String> containerMap) returns correctly with containers in 'running' state with target of 'running' and with containers in 'healthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-running-running-healthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-running-running-healthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-running-running-healthy-healthy-good3' + CONTAINER_NAME_POSTFIX
        String containerGood4Name = 'waitforcontainer-map-running-running-healthy-healthy-good4' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )
        containerMap.put( containerGood3Name, 'healthy' )
        containerMap.put( containerGood4Name, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood3Name}:
                container_name: ${containerGood3Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood4Name}:
                container_name: ${containerGood4Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectStateCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectStateCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerInspectHealthCommandGood3 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood3Name
        String dockerInspectHealthCommandGood4 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood4Name


        GradleExecUtils.execWithException( dockerComposeUpCommand )



        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectStateCommandGood1 ).equals( 'running' )
        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "running" state.' )
        }


        isRunning = GradleExecUtils.execWithException( dockerInspectStateCommandGood2 ).equals( 'running' )
        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "running" state.' )
        }


        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood3 ).equals( 'healthy' )
        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood3Name + ' did not reach "healthy" status.' )
        }


        isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood4 ).equals( 'healthy' )
        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood4Name + ' did not reach "healthy" status.' )
        }


        then:
        success == true


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly with container in 'exited' state with target of 'running' and with containers in 'healthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-exited-running-healthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-exited-running-healthy-healthy-bad' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-exited-running-healthy-healthy-good3' + CONTAINER_NAME_POSTFIX
        String containerGood4Name = 'waitforcontainer-map-exited-running-healthy-healthy-good4' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood3Name, 'healthy' )
        containerMap.put( containerGood4Name, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerBadName}:
                container_name: ${containerBadName}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood3Name}:
                container_name: ${containerGood3Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood4Name}:
                container_name: ${containerGood4Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectStateCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectStateCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerInspectHealthCommandGood3 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood3Name
        String dockerInspectHealthCommandGood4 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood4Name
        String dockerStopCommandBad = 'docker stop ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )



        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }


        GradleExecUtils.execWithException( dockerStopCommandBad )


        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExited = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'exited' )
            count++
        }

        if ( !isExited ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "exited" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'exited' )
        container.equals( containerBadName )


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }



    def "waitForContainer(Map<String,String> containerMap) returns correctly with containers in 'running' state with target of 'running' and with container in 'unhealthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-running-running-unhealthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-running-running-unhealthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-running-running-unhealthy-healthy-good3' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-running-running-unhealthy-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )
        containerMap.put( containerGood3Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood3Name}:
                container_name: ${containerGood3Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )


        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'unhealthy' )
        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "unhealthy" status.' )
        }


        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


            //***********************************
            // error (not related to target disposition)

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

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container in 'running' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerGood1Name = 'waitforcontainer-map-int-running-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-running-running-good2' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-running-running-good3' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )
        containerMap.put( containerGood3Name, 'running' )

        when:
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood3 = [ 'docker', 'run', '--rm', '--name', containerGood3Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerInspectCommandGood3 = 'docker inspect -f {{.State.Status}} ' + containerGood3Name
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name
        String dockerStopCommandGood3 = 'docker stop ' + containerGood3Name

        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )
        GradleExecUtils.execWithException( dockerRunCommandGood3 )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )


        boolean isRunningGood1 = GradleExecUtils.execWithException( dockerInspectCommandGood1 ).equals( 'running' )
        boolean isRunningGood2 = GradleExecUtils.execWithException( dockerInspectCommandGood2 ).equals( 'running' )
        boolean isRunningGood3 = GradleExecUtils.execWithException( dockerInspectCommandGood3 ).equals( 'running' )

        if ( !isRunningGood1 ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' not in "running" state.' )
        }

        if ( !isRunningGood2 ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' not in "running" state.' )
        }

        if ( !isRunningGood3 ) {
            throw new GradleException( 'Docker container ' + containerGood3Name + ' not in "running" state.' )
        }


        then:
        success == true

        cleanup:
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
        GradleExecUtils.exec( dockerStopCommandGood3 )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly when container not found for target state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'container-shouldnt-exist'

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerName, 'running' )

        when:
        long start = System.currentTimeMillis( )
        Map<String,String> result = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        'num-retries-exceeded'.equals( reason )
        'not-found'.equals( message )
        containerName.equals( container )
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container in 'exited' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-int-exited-running-bad' + CONTAINER_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-int-exited-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-exited-running-good2' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )

        when:
        String[] dockerRunCommandBad = [ 'docker', 'run', '--name', containerBadName, '-d', containerImageRef, 'tail', '-f' ] // intentionally not using '--rm' so container won't be removed when it exits
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerStopCommandBad = 'docker stop ' + containerBadName
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name
        String dockerRmCommandBad = 'docker rm ' + containerBadName


        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )
        GradleExecUtils.execWithException( dockerRunCommandBad )


        int count = 0
        boolean isRunningBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )

        while ( !isRunningBad && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunningBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )
            count++
        }

        if ( !isRunningBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }



        GradleExecUtils.execWithException( dockerStopCommandBad )

        count = 0
        boolean isExitedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'exited' )

        while ( !isExitedBad && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExitedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'exited' )
            count++
        }

        if ( !isExitedBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "exited" state.' )
        }



        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'exited' )
        container.equals( containerBadName )


        cleanup:
        GradleExecUtils.exec( dockerStopCommandBad )
        GradleExecUtils.exec( dockerRmCommandBad )
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container in 'paused' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-int-paused-running-bad' + CONTAINER_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-int-paused-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-paused-running-good2' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )

        when:
        String[] dockerRunCommandBad = [ 'docker', 'run', '--rm', '--name', containerBadName, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerStopCommandBad = 'docker stop ' + containerBadName
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name
        String dockerPauseCommandBad = 'docker pause ' + containerBadName


        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )
        GradleExecUtils.execWithException( dockerRunCommandBad )


        int count = 0
        boolean isRunningBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )

        while ( !isRunningBad && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunningBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )
            count++
        }

        if ( !isRunningBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }


        GradleExecUtils.execWithException( dockerPauseCommandBad )


        count = 0
        boolean isPausedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'paused' )

        while ( !isPausedBad && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isPausedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'paused' )
            count++
        }

        if ( !isPausedBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "paused" state.' )
        }



        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'paused' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerStopCommandBad )
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container in 'created' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-int-created-running-bad' + CONTAINER_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-int-created-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-created-running-good2' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )

        when:
        String[] dockerCreateCommandBad = [ 'docker', 'create', '--name', containerBadName, containerImageRef ]
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerRmCommandBad = 'docker rm ' + containerBadName
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name


        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )
        GradleExecUtils.execWithException( dockerCreateCommandBad )


        long start = System.currentTimeMillis( )
        Map<String, String> result = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        boolean isCreatedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'created' )

        if ( !isCreatedBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "created" state.' )
        }

        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'created' )
        container.equals( containerBadName )
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )

        cleanup:
        GradleExecUtils.exec( dockerRmCommandBad )
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly given an error with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        // adding invalid '--blah' flag to produce command error
        String containerBadName = '--blah' + TEST_IMAGE_REF
        String containerGood1Name = 'waitforcontainer-map-int-created-running-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-created-running-good2' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )

        when:
        String[] dockerRunCommandGood1 = [ 'docker', 'run', '--rm', '--name', containerGood1Name, '-d', containerImageRef, 'tail', '-f' ]
        String[] dockerRunCommandGood2 = [ 'docker', 'run', '--rm', '--name', containerGood2Name, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerStopCommandGood1 = 'docker stop ' + containerGood1Name
        String dockerStopCommandGood2 = 'docker stop ' + containerGood2Name



        GradleExecUtils.execWithException( dockerRunCommandGood1 )
        GradleExecUtils.execWithException( dockerRunCommandGood2 )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommandGood1 ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommandGood1 ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "running" state.' )
        }


        count = 0
        isRunning = GradleExecUtils.execWithException( dockerInspectCommandGood2 ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectCommandGood2 ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "running" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'error' )
        message.contains( 'unknown flag' )

        cleanup:
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }


            //***********************************
            // health

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for 'healthy' container with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-int-healthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-healthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-healthy-healthy-good3' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerGood3Name, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood3Name}:
                container_name: ${containerGood3Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandGood1 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood1Name
        String dockerInspectHealthCommandGood2 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood2Name
        String dockerInspectHealthCommandGood3 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood3Name


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )


        boolean isHealthy1 = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )
        if ( !isHealthy1 ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "healthy" status.' )
        }

        boolean isHealthy2 = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )
        if ( !isHealthy2 ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "healthy" status.' )
        }

        boolean isHealthy3 = GradleExecUtils.execWithException( dockerInspectHealthCommandGood3 ).equals( 'healthy' )
        if ( !isHealthy3 ) {
            throw new GradleException( 'Docker container ' + containerGood3Name + ' did not reach "healthy" status.' )
        }


        then:
        success == true

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for 'unhealthy' container with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-int-unhealthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-unhealthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-unhealthy-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandGood1 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood1Name
        String dockerInspectHealthCommandGood2 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood2Name
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )


        boolean isUnhealthyBad = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'unhealthy' )

        if ( !isUnhealthyBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "unhealthy" status.' )
        }

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container without health check with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-int-unhealthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-unhealthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-nohealth-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
                image: ${TEST_IMAGE_REF}
                command: tail -f
        """.stripIndent( )


        when:
        String[] dockerComposeUpCommand = [ 'docker-compose', '-f', composeFile, 'up', '-d' ]
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }


        then:
        success == false
        reason.equals( 'no-health-check' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container not found with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-int-notfound-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-notfound-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'nosuchcontainer'

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandGood1 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood1Name
        String dockerInspectHealthCommandGood2 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood2Name
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "healthy" status.' )
        }

        count = 0
        isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "healthy" status.' )
        }


        long start = System.currentTimeMillis( )
        Map<String, String> result = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'not-found' )
        container.equals( containerBadName )
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for previously healthy container in 'exited' state with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-exited-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-exited-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-exited-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName
        String dockerInspectStateCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerStopCommandBad = 'docker stop ' + containerBadName

        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "healthy" status.' )
        }


        GradleExecUtils.execWithException( dockerStopCommandBad )


        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExited = GradleExecUtils.execWithException( dockerInspectStateCommandBadCommand ).equals( 'exited' )
            count++
        }

        if ( !isExited ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "exited" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for previously healthy container in 'paused' state with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-paused-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-paused-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-paused-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName
        String dockerInspectStateCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerPauseCommandBad = 'docker pause ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "healthy" status.' )
        }

        GradleExecUtils.execWithException( dockerPauseCommandBad )

        count = 0
        boolean isPaused = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'paused' )

        while ( !isPaused && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isPaused = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'paused' )
            count++
        }

        if ( !isPaused ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "paused" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )

        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly given an error with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-error-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-error-healthy-good2' + CONTAINER_NAME_POSTFIX
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'healthy' )
        containerMap.put( containerGood2Name, 'healthy' )
        containerMap.put( additionalCommand, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandGood1 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood1Name
        String dockerInspectHealthCommandGood2 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood2Name


        GradleExecUtils.execWithException( dockerComposeUpCommand )

        int count = 0
        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood1 ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "healthy" status.' )
        }

        count = 0
        isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )

        while ( !isHealthy && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood2 ).equals( 'healthy' )
            count++
        }

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "healthy" status.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )

        then:
        success == false
        reason.equals( 'error' )
        message.contains( 'unknown flag' )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }



            //***********************************
            // both


    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly with containers in 'running' state with target of 'running' and with containers in 'healthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-running-running-healthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-running-running-healthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-running-running-healthy-healthy-good3' + CONTAINER_NAME_POSTFIX
        String containerGood4Name = 'waitforcontainer-map-int-running-running-healthy-healthy-good4' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )
        containerMap.put( containerGood3Name, 'healthy' )
        containerMap.put( containerGood4Name, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood3Name}:
                container_name: ${containerGood3Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood4Name}:
                container_name: ${containerGood4Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectStateCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectStateCommandGood2 = 'docker inspect -f {{.State.Status}} ' + containerGood2Name
        String dockerInspectHealthCommandGood3 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood3Name
        String dockerInspectHealthCommandGood4 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood4Name


        GradleExecUtils.execWithException( dockerComposeUpCommand )



        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectStateCommandGood1 ).equals( 'running' )
        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerGood1Name + ' did not reach "running" state.' )
        }


        isRunning = GradleExecUtils.execWithException( dockerInspectStateCommandGood2 ).equals( 'running' )
        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerGood2Name + ' did not reach "running" state.' )
        }


        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood3 ).equals( 'healthy' )
        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood3Name + ' did not reach "healthy" status.' )
        }


        isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandGood4 ).equals( 'healthy' )
        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerGood4Name + ' did not reach "healthy" status.' )
        }


        then:
        success == true


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly with container in 'exited' state with target of 'running' and with containers in 'healthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-exited-running-healthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-exited-running-healthy-healthy-bad' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-exited-running-healthy-healthy-good3' + CONTAINER_NAME_POSTFIX
        String containerGood4Name = 'waitforcontainer-map-int-exited-running-healthy-healthy-good4' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerBadName, 'running' )
        containerMap.put( containerGood3Name, 'healthy' )
        containerMap.put( containerGood4Name, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerBadName}:
                container_name: ${containerBadName}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood3Name}:
                container_name: ${containerGood3Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerGood4Name}:
                container_name: ${containerGood4Name}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectStateCommandGood1 = 'docker inspect -f {{.State.Status}} ' + containerGood1Name
        String dockerInspectStateCommandBad = 'docker inspect -f {{.State.Status}} ' + containerBadName
        String dockerInspectHealthCommandGood3 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood3Name
        String dockerInspectHealthCommandGood4 = 'docker inspect -f {{.State.Health.Status}} ' + containerGood4Name
        String dockerStopCommandBad = 'docker stop ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )



        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }


        GradleExecUtils.execWithException( dockerStopCommandBad )


        count = 0
        boolean isExited = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'exited' )

        while ( !isExited && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isExited = GradleExecUtils.execWithException( dockerInspectStateCommandBad ).equals( 'exited' )
            count++
        }

        if ( !isExited ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "exited" state.' )
        }


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String message = result.get( 'message' )
        String container = result.get( 'container' )


        then:
        success == false
        reason.equals( 'failed' )
        message.equals( 'exited' )
        container.equals( containerBadName )


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }



    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly with containers in 'running' state with target of 'running' and with container in 'unhealthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-running-running-unhealthy-healthy-good1' + CONTAINER_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-running-running-unhealthy-healthy-good2' + CONTAINER_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-running-running-unhealthy-healthy-good3' + CONTAINER_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-running-running-unhealthy-healthy-bad' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerGood1Name, 'running' )
        containerMap.put( containerGood2Name, 'running' )
        containerMap.put( containerGood3Name, 'healthy' )
        containerMap.put( containerBadName, 'healthy' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerGood1Name}:
                container_name: ${containerGood1Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood2Name}:
                container_name: ${containerGood2Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f

              ${containerGood3Name}:
                container_name: ${containerGood3Name}
                image: ${TEST_IMAGE_REF}
                command: tail -f
                healthcheck:
                  test: exit 0
                  interval: 1s
                  retries: 2
                  start_period: 1s
                  timeout: 2s

              ${containerBadName}:
                container_name: ${containerBadName}
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
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        String dockerInspectHealthCommandBad = 'docker inspect -f {{.State.Health.Status}} ' + containerBadName


        GradleExecUtils.execWithException( dockerComposeUpCommand )


        Map<String, String> result = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )
        String container = result.get( 'container' )


        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'unhealthy' )
        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "unhealthy" status.' )
        }


        then:
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


            //***********************************
            // error (not related to target disposition)

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly given no containers to monitor"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )

        when:
        Map<String,String> result = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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
        Map<String,String> result = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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
        Map<String,String> result = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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
    //***********************************
    //***********************************
    // docker compose


    def "getComposeUpCommand(java.lang.String... composeFilePaths) returns correctly given a single argument"( ) {
        given:
        String composeFilePath = '/path/to/composefile.yml'

        when:
        String[] composeUpCommand = DockerUtils.getComposeUpCommand( composeFilePath )

        then:
        String[] expected = [ 'docker-compose', '-f', composeFilePath, 'up', '-d' ]
        expected == composeUpCommand
    }


    def "getComposeUpCommand(java.lang.String... composeFilePaths) returns correctly given multiple arguments"( ) {
        given:
        String composeFilePath1 = '/path/to/composefile1.yml'
        String composeFilePath2 = '/path/to/composefile2.yml'

        when:
        String[] composeUpCommand = DockerUtils.getComposeUpCommand( composeFilePath1, composeFilePath2 )

        then:
        String[] expected = [ 'docker-compose', '-f', composeFilePath1, '-f', composeFilePath2, 'up', '-d' ]
        expected == composeUpCommand
    }


    def "getComposeDownCommand(String composeFilePath) returns correctly"( ) {
        given:
        String composeFilePath = '/path/to/composefile.yml'

        when:
        String[] composeDownCommand = DockerUtils.getComposeDownCommand( composeFilePath )

        then:
        String[] expected = [ 'docker-compose', '-f', composeFilePath, 'down' ]
        expected == composeDownCommand
    }


    def "composeUp(java.lang.String... composeFilePaths) returns correctly given one compose file"( ) {
        given:
        String containerName = 'composeup-one-composefile' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerName, 'running' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerName}:
                container_name: ${containerName}
                image: ${TEST_IMAGE_REF}
                command: tail -f
        """.stripIndent( )

        when:
        Map<String, String> result = DockerUtils.composeUp( composeFile.getAbsolutePath( ) )
        boolean success = result.get( 'success' )

        Map<String, String> resultWait = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )

        if ( !resultWait.get( 'success' ) ) {
            if ( resultWait.get( 'reason' ).equals( 'error' ) ) {
                throw new GradleException( 'An error occurred when running "docker-compose up": ' + resultWait.get( 'message' ) )
            } else {
                throw new GradleException( 'A container failed when running "docker-compose up".' )
            }
        }

        then:
        success == true

        cleanup:
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


    def "composeUp(java.lang.String... composeFilePaths) returns correctly given two compose files"( ) {
        given:
        String containerName = 'composeup-two-composefiles-container' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerName, 'running' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerName}:
                container_name: ${containerName}
        """.stripIndent( )

        composeFile2 << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerName}:
                image: ${TEST_IMAGE_REF}
                command: tail -f
        """.stripIndent( )

        when:
        Map<String, String> result = DockerUtils.composeUp( composeFile.getAbsolutePath( ), composeFile2.getAbsolutePath( ) )
        boolean success = result.get( 'success' )

        Map<String, String> resultWait = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )

        if ( !resultWait.get( 'success' ) ) {
            if ( resultWait.get( 'reason' ).equals( 'error' ) ) {
                throw new GradleException( 'An error occurred when running "docker-compose up": ' + resultWait.get( 'message' ) )
            } else {
                throw new GradleException( 'A container failed when running "docker-compose up".' )
            }
        }


        then:
        success == true


        cleanup:
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


    def "composeUp(java.lang.String... composeFilePaths) returns correctly when in error"( ) {
        given:
        String badComposeFile = 'bad-filename'

        when:
        Map<String, String> result = DockerUtils.composeUp( badComposeFile )
        boolean success = result.get( 'success' )
        String reason = result.get( 'reason' )

        then:
        success == false
        reason.contains( 'FileNotFoundError' )
    }


    def "composeDown(String composeFilePath) returns correctly"( ) {
        given:
        String containerName = 'composedown-container' + CONTAINER_NAME_POSTFIX

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( containerName, 'running' )

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerName}:
                container_name: ${containerName}
                image: ${TEST_IMAGE_REF}
                command: tail -f
        """.stripIndent( )

        when:
        String[] dockerComposeUpCommand = [ 'docker-compose', '-f', composeFile, 'up', '-d' ]
        GradleExecUtils.execWithException( dockerComposeUpCommand )

        Map<String, String> resultWait = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )

        if ( !resultWait.get( 'success' ) ) {
            if ( resultWait.get( 'reason' ).equals( 'error' ) ) {
                throw new GradleException( 'An error occurred when running "docker-compose up": ' + resultWait.get( 'message' ) )
            } else {
                throw new GradleException( 'A container failed when running "docker-compose up".' )
            }
        }

        Map<String, String> result = DockerUtils.composeDown( composeFile.getAbsolutePath( ) )
        String success = result.get( 'success' )


        then:
        success.equals( 'true' )


        cleanup:
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile, 'down' ]
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


    def "composeDown(String composeFilePath) returns correctly when in error"( ) {
        given:
        String badComposeFile = 'bad-filename'

        when:
        Map<String, String> result = DockerUtils.composeDown( badComposeFile )
        String success = result.get( 'success' )
        String reason = result.get( 'reason' )

        then:
        success.equals( 'false' )
        reason.contains( 'FileNotFoundError' )
    }



    //***********************************
    //***********************************
    //***********************************
    // docker run, stop, exec


    //todo docker run - no options - no command - good
    //todo docker run - yes options - no command - good
    //todo docker run - no options - yes command - good
    //todo docker run - yes options - yes command - good

    //todo docker run - bad


    def "dockerStop(String container) returns correctly"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerstop-valid' + CONTAINER_NAME_POSTFIX

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


        Map<String, String> result = DockerUtils.dockerStop( containerName )
        String success = result.get( 'success' )

        //todo debug
        if ( !success ) {
            println "dockerStop returns correctly: " + result.get( 'reason' )
        }


        then:
        success.equals( 'true' )

        boolean exited = false
        Map<String, String> check = GradleExecUtils.exec( dockerInspectCommand )
        if ( check.get( 'exitValue' ) != 0 ) {
            if ( check.get( 'err' ).contains( 'Error: No such object' ) ) {
                exited = true
            }
        }

        exited == true

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }


    def "dockerStop(String container) returns correctly when no such container"( ) {
        given:
        String container = 'dockerstop-invalid' + CONTAINER_NAME_POSTFIX

        when:

        Map<String, String> result = DockerUtils.dockerStop( container )
        String success = result.get( 'success' )
        String reason = result.get( 'reason' )

        //todo debug
        if ( !success ) {
            println "dockerStop returns correctly no such container: " + result.get( 'reason' )
        }


        then:
        success.equals( 'false' )
        reason.contains( 'Error: No such object' )
    }



    def "dockerExec(String container, String command, Map<String,String> options) returns correctly without options"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerexec-str-command-no-options-good' + CONTAINER_NAME_POSTFIX
        String command = 'ls'

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


        Map<String, String> result = DockerUtils.dockerExec( containerName, command )
        String success = result.get( 'success' )
        String output = result.get( 'out' )


        then:
        success.equals( 'true' )
        output.contains( 'bin' )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }


    def "dockerExec(String container, String command, Map<String,String> options) returns correctly with options"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerexec-str-command-with-options-good' + CONTAINER_NAME_POSTFIX
        String command = 'ls'
        Map<String, String> options = new HashMap<String, String>( )
        options.put( '-w', '/sys' ) 

        // didn't test an option with key and no value, as there aren't good choices that make sense here

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


        Map<String, String> result = DockerUtils.dockerExec( containerName, command, options )
        String success = result.get( 'success' )
        String output = result.get( 'out' )


        then:
        success.equals( 'true' )
        output.contains( 'kernel' )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }



    def "dockerExec(String container, String command, Map<String,String> options) returns correctly with bad input"( ) {
        given:
        String containerName = 'dockerexec-no-such-container' + CONTAINER_NAME_POSTFIX
        String command = 'ls'

        when:
        Map<String, String> result = DockerUtils.dockerExec( containerName, command )
        String success = result.get( 'success' )
        String reason = result.get( 'reason' )


        then:
        success.equals( 'false' )
        reason.contains( 'No such container' )
    }



    def "dockerExec(String container, String[] command, Map<String,String> options) returns correctly without options"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerexec-array-command-no-options-good' + CONTAINER_NAME_POSTFIX
        String[] command = ["/bin/bash", "-c", "ls && ps"]

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


        Map<String, String> result = DockerUtils.dockerExec( containerName, command )
        String success = result.get( 'success' )
        String output = result.get( 'out' )


        then:
        success.equals( 'true' )
        output.contains( 'bin' )
        output.contains( 'PID' )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }



    def "dockerExec(String container, String[] command, Map<String,String> options) returns correctly with options"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerexec-array-command-with-options-good' + CONTAINER_NAME_POSTFIX
        String[] command = ["/bin/bash", "-c", "ls && ps"]
        Map<String, String> options = new HashMap<String, String>( )
        options.put( '-w', '/sys' ) 

        // didn't test an option with key and no value, as there aren't good choices that make sense here

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


        Map<String, String> result = DockerUtils.dockerExec( containerName, command, options )
        String success = result.get( 'success' )
        String output = result.get( 'out' )


        then:
        success.equals( 'true' )
        output.contains( 'kernel' )
        output.contains( 'PID' )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }



    def "dockerExec(String container, String[] command, Map<String,String> options) returns correctly with bad input"( ) {
        given:
        String containerName = 'dockerexec-no-such-container' + CONTAINER_NAME_POSTFIX
        String[] command = ["/bin/bash", "-c", "ls && ps"]

        when:
        Map<String, String> result = DockerUtils.dockerExec( containerName, command )
        String success = result.get( 'success' )
        String reason = result.get( 'reason' )

        then:
        success.equals( 'false' )
        reason.contains( 'No such container' )
    }

    */

}
