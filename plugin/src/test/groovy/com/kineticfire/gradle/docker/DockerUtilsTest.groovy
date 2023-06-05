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
 * Dependencies needed for tests:
 * <ol>
 *    <li>docker with docker compose v2, and can be executed by test runner</li>
 *    <li>test image and registry image (as defined in properties file) local or access to providing registry, such as Docker Hub</li>
 *    <li>bash</li>
 *    <li>curl</li>
 * </ol>
 */
class DockerUtilsTest extends Specification {

    private static final Properties properties = loadProperties( )

    static final String COMPOSE_VERSION = properties.get( 'testCompose.version' )

    static final String TEST_IMAGE_NAME = properties.get( 'testImage.name' )
    static final String TEST_IMAGE_VERSION = properties.get( 'testImage.version' )
    static final String TEST_IMAGE_REF = TEST_IMAGE_NAME + ':' + TEST_IMAGE_VERSION 

    static final String REGISTRY_IMAGE_NAME = properties.get( 'registryImage.name' )
    static final String REGISTRY_IMAGE_VERSION = properties.get( 'registryImage.version' )
    static final String REGISTRY_IMAGE_REF = REGISTRY_IMAGE_NAME + ':' + REGISTRY_IMAGE_VERSION 

    static final String COMPOSE_FILE_NAME  = 'compose.yaml'

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

    // create a postfix to append to container/service names to make them unique to this test run such that multiple concurrent tests can be run on the same system without name collisions.  lowercase so can use as Docker image tag.
    static final String UNIQUE_NAME_POSTFIX = '-dockerutilstest-' + System.currentTimeMillis( ) + '-' + new Random( ).nextInt( 999999 )


    // Tests involving the pausing of Docker containers include commands to 'unpause', 'stop', and 'remove'.  Although these shouldn't be necessary, there were instances were paused containers wouldn't exit; then couldn't be exited and removed from the command line.  This could have been due to using ctrl-c when forcibly stopping tests in progress.


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
        GradleExecUtils.exec( 'docker pull ' + REGISTRY_IMAGE_REF )
    }


    def setup( ) {
        composeFile = new File( tempDir.toString( ) + File.separatorChar + COMPOSE_FILE_NAME )
        composeFile2 = new File( tempDir.toString( ) + File.separatorChar + COMPOSE_FILE_NAME + '2' )
    }


    //todo see notes in DockerUtils.groovy about changing to Compose v2

    //todo verify that containers and networks are removed

    //todo how to deal with port contention for dockerPush() methods?


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
        String containerName = 'getcontainerhealth-nohealth' + UNIQUE_NAME_POSTFIX

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

        String containerName = 'getcontainerhealth-healthy' + UNIQUE_NAME_POSTFIX

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

        String containerName = 'getcontainerhealth-unhealthy' + UNIQUE_NAME_POSTFIX

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

        String containerName = 'getcontainerhealth-exited' + UNIQUE_NAME_POSTFIX

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

        String containerName = 'getcontainerhealth-paused' + UNIQUE_NAME_POSTFIX

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
        String containerName = 'getcontainerstate-running' + UNIQUE_NAME_POSTFIX

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
        String containerName = 'getcontainerstate-paused' + UNIQUE_NAME_POSTFIX

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
        String containerName = 'getcontainerstate-exited' + UNIQUE_NAME_POSTFIX

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
        String containerName = 'getcontainerstate-created' + UNIQUE_NAME_POSTFIX

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
        String containerName = 'waitforcontainer-str-running-running' + UNIQUE_NAME_POSTFIX

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
        String containerName = 'waitforcontainer-str-exited-running' + UNIQUE_NAME_POSTFIX

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
        String containerName = 'waitforcontainer-str-paused-running' + UNIQUE_NAME_POSTFIX

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
        String containerName = 'waitforcontainer-str-created-running' + UNIQUE_NAME_POSTFIX

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



            //***********************************
            // health

        /* comment
    def "waitForContainer(String container, String target) returns correctly for 'healthy' container with target of 'healthy'"( ) {
        given:

        String containerName = 'waitforcontainer-str-healthy-healthy' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = resultMap.success


        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }

        then:
        resultMap instanceof Map
        success == true

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for 'unhealthy' container with target of 'healthy'"( ) {
        given:

        String containerName = 'waitforcontainer-str-unhealthy-healthy' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'unhealthy' )

        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "unhealthy" status.' )
        }

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for container without health check with target of 'healthy'"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-nohealth-healthy' + UNIQUE_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )


        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


        then:
        resultMap instanceof Map
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
        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy' )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'not-found' )
        container.equals( containerName )
        //( diff >= ( TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(String container, String target) returns correctly for previously healthy container in 'exited' state with target of 'healthy'"( ) {

        given:

        String containerName = 'waitforcontainer-str-exited-healthy' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target) returns correctly for previously healthy container in 'paused' state with target of 'healthy'"( ) {

        given:

        String containerName = 'waitforcontainer-str-paused-healthy' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy' )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container

        then:
        resultMap instanceof Map
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
        def resultMap = DockerUtils.waitForContainer( additionalCommand, 'healthy' )
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
        def resultMap = DockerUtils.waitForContainer( containerName, null )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }

    comment */



        //***********************************
        // waitForContainer( String container, String target, int retrySeconds, int retryNum )


            //***********************************
            // state

        /* comment
    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container in 'running' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-running-running' + UNIQUE_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )


        def resultMap = DockerUtils.waitForContainer( containerName, 'running', SLEEP_TIME_SECONDS, NUM_RETRIES )
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

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for not found container with target of 'running' state"( ) {
        given:
        String containerName = 'nosuchcontainer'

        when:
        long start = System.currentTimeMillis( ) 
        def resultMap = DockerUtils.waitForContainer( containerName, 'running', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start 
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'not-found' )
        container.equals( containerName )
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container in 'exited' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-exited-running' + UNIQUE_NAME_POSTFIX

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



        def resultMap = DockerUtils.waitForContainer( containerName, 'running', SLEEP_TIME_SECONDS, NUM_RETRIES )
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

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container in 'paused' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-paused-running' + UNIQUE_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerPauseCommand = 'docker pause ' + containerName
        String dockerUnpauseCommand = 'docker unpause ' + containerName
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



        def resultMap = DockerUtils.waitForContainer( containerName, 'running', SLEEP_TIME_SECONDS, NUM_RETRIES )
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

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container in 'created' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-created-running' + UNIQUE_NAME_POSTFIX

        when:
        String[] dockerCreateCommand = [ 'docker', 'create', '--name', containerName, containerImageRef ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerRmCommand = 'docker rm ' + containerName

        GradleExecUtils.execWithException( dockerCreateCommand )


        long start = System.currentTimeMillis( ) 
        def resultMap = DockerUtils.waitForContainer( containerName, 'running', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )

        cleanup:
        GradleExecUtils.exec( dockerRmCommand )
    }



    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given an error with target of 'running' state"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        def resultMap = DockerUtils.waitForContainer( additionalCommand, 'running', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
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



            //***********************************
            // health


    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for 'healthy' container with target of 'healthy'"( ) {
        given:

        String containerName = 'waitforcontainer-str-int-healthy-healthy' + UNIQUE_NAME_POSTFIX

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

        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success

        boolean isHealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'healthy' )

        if ( !isHealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "healthy" status.' )
        }


        then:
        resultMap instanceof Map
        success == true

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for 'unhealthy' container with target of 'healthy'"( ) {
        given:

        String containerName = 'waitforcontainer-str-int-unhealthy-healthy' + UNIQUE_NAME_POSTFIX

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

        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommand ).equals( 'unhealthy' )

        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "unhealthy" status.' )
        }

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for container without health check with target of 'healthy'"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'waitforcontainer-str-int-nohealth-healthy' + UNIQUE_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )


        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommand ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


        then:
        resultMap instanceof Map
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
        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'num-retries-exceeded' )
        message.equals( 'not-found' )
        container.equals( containerName )
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for previously healthy container in 'exited' state with target of 'healthy'"( ) {

        given:

        String containerName = 'waitforcontainer-str-int-exited-healthy' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly for previously healthy container in 'paused' state with target of 'healthy'"( ) {

        given:

        String containerName = 'waitforcontainer-str-int-paused-healthy' + UNIQUE_NAME_POSTFIX

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
        String dockerUnpauseCommand = 'docker unpause ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName
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


        def resultMap = DockerUtils.waitForContainer( containerName, 'healthy', SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerName )

        cleanup:
        GradleExecUtils.exec( dockerUnpauseCommand )
        GradleExecUtils.exec( dockerComposeDownCommand )
        GradleExecUtils.exec( dockerStopCommand )
        GradleExecUtils.exec( dockerRemoveCommand )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given an error with target of 'healthy'"( ) {
        given:
        // adding invalid '--blah' flag to produce command error
        String additionalCommand = '--blah ' + TEST_IMAGE_REF

        when:
        def resultMap = DockerUtils.waitForContainer( additionalCommand, 'healthy' )
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


            //***********************************
            // error (not related to target disposition)

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given invalid target disposition as string"( ) {
        given:
        String containerName = 'container-doesnt-matter'

        when:
        def resultMap = DockerUtils.waitForContainer( containerName, 'invalid-disposition', FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        'error'.equals( reason )
        'illegal-target-disposition'.equals( message )
        containerName.equals( container )
    }

    def "waitForContainer(String container, String target, int retrySeconds, int retryNum) returns correctly given invalid target disposition as null"( ) {
        given:
        String containerName = 'container-doesnt-matter'

        when:
        def resultMap = DockerUtils.waitForContainer( containerName, null, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
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
        String containerGood1Name = 'waitforcontainer-map-running-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-running-running-good2' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-running-running-good3' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success


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
        resultMap instanceof Map
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
        def resultMap = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        'num-retries-exceeded'.equals( reason )
        'not-found'.equals( message )
        containerName.equals( container )
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container in 'exited' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-exited-running-bad' + UNIQUE_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-exited-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-exited-running-good2' + UNIQUE_NAME_POSTFIX

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



        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
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
        String containerBadName = 'waitforcontainer-map-paused-running-bad' + UNIQUE_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-paused-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-paused-running-good2' + UNIQUE_NAME_POSTFIX

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
        String dockerUnpauseCommandBad = 'docker unpause ' + containerBadName
        String dockerRemoveCommandBad = 'docker remove ' + containerBadName


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



        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'failed' )
        message.equals( 'paused' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerUnpauseCommandBad )
        GradleExecUtils.exec( dockerStopCommandBad )
        GradleExecUtils.exec( dockerRemoveCommandBad )
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container in 'created' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-created-running-bad' + UNIQUE_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-created-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-created-running-good2' + UNIQUE_NAME_POSTFIX

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
        def resultMap = DockerUtils.waitForContainer( containerMap )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        boolean isCreatedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'created' )

        if ( !isCreatedBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "created" state.' )
        }

        then:
        resultMap instanceof Map
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
        String containerGood1Name = 'waitforcontainer-map-created-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-created-running-good2' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
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

        String containerGood1Name = 'waitforcontainer-map-healthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-healthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-healthy-healthy-good3' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success


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
        resultMap instanceof Map
        success == true

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for 'unhealthy' container with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-unhealthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-unhealthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-unhealthy-healthy-bad' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container


        boolean isUnhealthyBad = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'unhealthy' )

        if ( !isUnhealthyBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "unhealthy" status.' )
        }

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container without health check with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-unhealthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-unhealthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-nohealth-healthy-bad' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }


        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'no-health-check' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for container not found with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-notfound-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-notfound-healthy-good2' + UNIQUE_NAME_POSTFIX
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
        def resultMap = DockerUtils.waitForContainer( containerMap )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
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
        String containerGood1Name = 'waitforcontainer-map-exited-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-exited-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-exited-healthy-bad' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly for previously healthy container in 'paused' state with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-paused-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-paused-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-paused-healthy-bad' + UNIQUE_NAME_POSTFIX

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
        String dockerUnpauseCommandBad = 'docker unpause ' + containerBadName
        String dockerStopCommandBad = 'docker stop ' + containerBadName
        String dockerRemoveCommandBad = 'docker remove ' + containerBadName


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


        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerUnpauseCommandBad )
        GradleExecUtils.exec( dockerComposeDownCommand )
        GradleExecUtils.exec( dockerStopCommandBad )
        GradleExecUtils.exec( dockerRemoveCommandBad )
    }


    def "waitForContainer(Map<String,String> containerMap) returns correctly given an error with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-error-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-error-healthy-good2' + UNIQUE_NAME_POSTFIX
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


        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message

        then:
        resultMap instanceof Map
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
        String containerGood1Name = 'waitforcontainer-map-running-running-healthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-running-running-healthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-running-running-healthy-healthy-good3' + UNIQUE_NAME_POSTFIX
        String containerGood4Name = 'waitforcontainer-map-running-running-healthy-healthy-good4' + UNIQUE_NAME_POSTFIX

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



        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success


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
        resultMap instanceof Map
        success == true


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly with container in 'exited' state with target of 'running' and with containers in 'healthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-exited-running-healthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-exited-running-healthy-healthy-bad' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-exited-running-healthy-healthy-good3' + UNIQUE_NAME_POSTFIX
        String containerGood4Name = 'waitforcontainer-map-exited-running-healthy-healthy-good4' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'failed' )
        message.equals( 'exited' )
        container.equals( containerBadName )


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }



    def "waitForContainer(Map<String,String> containerMap) returns correctly with containers in 'running' state with target of 'running' and with container in 'unhealthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-running-running-unhealthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-running-running-unhealthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-running-running-unhealthy-healthy-good3' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-running-running-unhealthy-healthy-bad' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container


        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'unhealthy' )
        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "unhealthy" status.' )
        }


        then:
        resultMap instanceof Map
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
        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == true
    }

    def "waitForContainer(Map<String,String> containerMap) returns correctly given invalid target disposition as string"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )
        String containerName = 'container-doesnt-matter'
        containerMap.put( containerName, 'invalid-disposition' )

        when:
        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
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
        def resultMap = DockerUtils.waitForContainer( containerMap )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
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
        String containerGood1Name = 'waitforcontainer-map-int-running-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-running-running-good2' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-running-running-good3' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success


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
        resultMap instanceof Map
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
        def resultMap = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        'num-retries-exceeded'.equals( reason )
        'not-found'.equals( message )
        containerName.equals( container )
        //( diff >= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS - TIME_TOLERANCE_MILLIS ) ) && ( diff <= ( FAST_FAIL_TIME_WAIT_EXPECTED_MILLIS + TIME_TOLERANCE_MILLIS ) )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container in 'exited' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-int-exited-running-bad' + UNIQUE_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-int-exited-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-exited-running-good2' + UNIQUE_NAME_POSTFIX

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



        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
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
        String containerBadName = 'waitforcontainer-map-int-paused-running-bad' + UNIQUE_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-int-paused-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-paused-running-good2' + UNIQUE_NAME_POSTFIX

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
        String dockerUnpauseCommandBad = 'docker unpause ' + containerBadName
        String dockerRemoveCommandBad = 'docker remove ' + containerBadName


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



        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'failed' )
        message.equals( 'paused' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerUnpauseCommandBad )
        GradleExecUtils.exec( dockerStopCommandBad )
        GradleExecUtils.exec( dockerRemoveCommandBad )
        GradleExecUtils.exec( dockerStopCommandGood1 )
        GradleExecUtils.exec( dockerStopCommandGood2 )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container in 'created' state with target of 'running' state"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerBadName = 'waitforcontainer-map-int-created-running-bad' + UNIQUE_NAME_POSTFIX
        String containerGood1Name = 'waitforcontainer-map-int-created-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-created-running-good2' + UNIQUE_NAME_POSTFIX

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
        def resultMap = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        boolean isCreatedBad = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'created' )

        if ( !isCreatedBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "created" state.' )
        }

        then:
        resultMap instanceof Map
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
        String containerGood1Name = 'waitforcontainer-map-int-created-running-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-created-running-good2' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
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

        String containerGood1Name = 'waitforcontainer-map-int-healthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-healthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-healthy-healthy-good3' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success


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
        resultMap instanceof Map
        success == true

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for 'unhealthy' container with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-int-unhealthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-unhealthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-unhealthy-healthy-bad' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container


        boolean isUnhealthyBad = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'unhealthy' )

        if ( !isUnhealthyBad ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "unhealthy" status.' )
        }

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container without health check with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-int-unhealthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-unhealthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-nohealth-healthy-bad' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container


        boolean isRunning = GradleExecUtils.execWithException( dockerInspectCommandBad ).equals( 'running' )

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "running" state.' )
        }


        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'no-health-check' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for container not found with target of 'healthy'"( ) {
        given:

        String containerGood1Name = 'waitforcontainer-map-int-notfound-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-notfound-healthy-good2' + UNIQUE_NAME_POSTFIX
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
        def resultMap = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        long diff = System.currentTimeMillis( ) - start
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
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
        String containerGood1Name = 'waitforcontainer-map-int-exited-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-exited-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-exited-healthy-bad' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly for previously healthy container in 'paused' state with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-paused-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-paused-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-paused-healthy-bad' + UNIQUE_NAME_POSTFIX

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
        String dockerUnpauseCommandBad = 'docker unpause ' + containerBadName
        String dockerStopCommandBad = 'docker stop ' + containerBadName
        String dockerRemoveCommandBad = 'docker remove ' + containerBadName


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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container

        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'unhealthy' )
        container.equals( containerBadName )

        cleanup:
        GradleExecUtils.exec( dockerUnpauseCommandBad )
        GradleExecUtils.exec( dockerComposeDownCommand )
        GradleExecUtils.exec( dockerStopCommandBad )
        GradleExecUtils.exec( dockerRemoveCommandBad )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly given an error with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-error-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-error-healthy-good2' + UNIQUE_NAME_POSTFIX
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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message

        then:
        resultMap instanceof Map
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
        String containerGood1Name = 'waitforcontainer-map-int-running-running-healthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-running-running-healthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-running-running-healthy-healthy-good3' + UNIQUE_NAME_POSTFIX
        String containerGood4Name = 'waitforcontainer-map-int-running-running-healthy-healthy-good4' + UNIQUE_NAME_POSTFIX

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



        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success


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
        resultMap instanceof Map
        success == true


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly with container in 'exited' state with target of 'running' and with containers in 'healthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-exited-running-healthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-exited-running-healthy-healthy-bad' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-exited-running-healthy-healthy-good3' + UNIQUE_NAME_POSTFIX
        String containerGood4Name = 'waitforcontainer-map-int-exited-running-healthy-healthy-good4' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container


        then:
        resultMap instanceof Map
        success == false
        reason.equals( 'failed' )
        message.equals( 'exited' )
        container.equals( containerBadName )


        cleanup:
        GradleExecUtils.exec( dockerComposeDownCommand )
    }



    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly with containers in 'running' state with target of 'running' and with container in 'unhealthy' disposition' with target of 'healthy'"( ) {
        given:
        String containerGood1Name = 'waitforcontainer-map-int-running-running-unhealthy-healthy-good1' + UNIQUE_NAME_POSTFIX
        String containerGood2Name = 'waitforcontainer-map-int-running-running-unhealthy-healthy-good2' + UNIQUE_NAME_POSTFIX
        String containerGood3Name = 'waitforcontainer-map-int-running-running-unhealthy-healthy-good3' + UNIQUE_NAME_POSTFIX
        String containerBadName = 'waitforcontainer-map-int-running-running-unhealthy-healthy-bad' + UNIQUE_NAME_POSTFIX

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


        def resultMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String container = resultMap.container


        boolean isUnhealthy = GradleExecUtils.execWithException( dockerInspectHealthCommandBad ).equals( 'unhealthy' )
        if ( !isUnhealthy ) {
            throw new GradleException( 'Docker container ' + containerBadName + ' did not reach "unhealthy" status.' )
        }


        then:
        resultMap instanceof Map
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
        def resultMap = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == true
    }

    def "waitForContainer(Map<String,String> containerMap, int retrySeconds, int retryNum) returns correctly given invalid target disposition as string"( ) {
        given:
        Map<String,String> containerMap = new HashMap<String, String>( )
        String containerName = 'container-doesnt-matter'
        containerMap.put( containerName, 'invalid-disposition' )

        when:
        def resultMap = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
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
        def resultMap = DockerUtils.waitForContainer( containerMap, FAST_FAIL_SLEEP_TIME_SECONDS, FAST_FAIL_NUM_RETRIES )
        boolean success = resultMap.success
        String reason = resultMap.reason
        String message = resultMap.message
        String container = resultMap.container

        then:
        resultMap instanceof Map
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

    def "validateComposeFile(java.lang.String... composeFilePaths) returns correctly for one compose file in error"( ) {
        given:
        composeFile << """
            services:
              my-service:
                container_name: my-container
                image: "test:\${GREAT_VERSION}"
                ports:
                  - "8080:\${PORT-OF2}"
                invaliddirective: tail -f
        """.stripIndent( )

        when:
        def resultMap = DockerUtils.validateComposeFile( composeFile.getAbsolutePath( ) )
        boolean success = resultMap.success
        String error = resultMap.reason

        then:
        resultMap instanceof Map
        success == false
        error.contains( 'Additional property invaliddirective is not allowed' )
    }

    def "validateComposeFile(java.lang.String... composeFilePaths) returns correctly for one compose file"( ) {
        given:
        composeFile << """
            services:
              my-service:
                container_name: my-container
                image: "test:\${GREAT_VERSION}"
                ports:
                  - "8080:\${PORT-OF2}"
                command: tail -f
        """.stripIndent( )

        when:
        def resultMap = DockerUtils.validateComposeFile( composeFile.getAbsolutePath( ) )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == true
    }

    def "validateComposeFile(java.lang.String... composeFilePaths) returns correctly for two compose files"( ) {
        given:
        composeFile << """
            services:
              my-service:
                container_name: my-container
                image: "test:\${GREAT_VERSION}"
                ports:
                  - "8080:\${PORT-OF2}"
        """.stripIndent( )

        composeFile2 << """
            services:
              my-service:
                command: tail -f
        """.stripIndent( )

        when:
        def resultMap = DockerUtils.validateComposeFile( composeFile.getAbsolutePath( ), composeFile2.getAbsolutePath( ) )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == true
    }

    def "composeUp(java.lang.String... composeFilePaths) returns correctly given one compose file"( ) {
        given:
        String containerName = 'composeup-one-composefile' + UNIQUE_NAME_POSTFIX

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
        def resultMap = DockerUtils.composeUp( composeFile.getAbsolutePath( ) )
        boolean success = resultMap.success

        def resultWaitMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )

        if ( !resultWaitMap.success ) {
            if ( resultWaitMap.reason.equals( 'error' ) ) {
                throw new GradleException( 'An error occurred when running "docker-compose up": ' + resultWaitMap.message )
            } else {
                throw new GradleException( 'A container failed when running "docker-compose up".' )
            }
        }

        then:
        resultMap instanceof Map
        success == true

        cleanup:
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile.getAbsolutePath( ), 'down' ]
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


    def "composeUp(java.lang.String... composeFilePaths) returns correctly given two compose files"( ) {
        given:
        String containerName = 'composeup-two-composefiles-container' + UNIQUE_NAME_POSTFIX

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
        def resultMap = DockerUtils.composeUp( composeFile.getAbsolutePath( ), composeFile2.getAbsolutePath( ) )
        boolean success = resultMap.success

        def resultWaitMap = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )

        if ( !resultWaitMap.success ) {
            if ( resultWaitMap.reason.equals( 'error' ) ) {
                throw new GradleException( 'An error occurred when running "docker-compose up": ' + resultWaitMap.message )
            } else {
                throw new GradleException( 'A container failed when running "docker-compose up".' )
            }
        }


        then:
        resultMap instanceof Map
        success == true


        cleanup:
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile.getAbsolutePath( ), 'down' ]
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


    def "composeUp(java.lang.String... composeFilePaths) returns correctly when in error"( ) {
        given:
        String badComposeFile = 'bad-filename'

        when:
        def resultMap = DockerUtils.composeUp( badComposeFile )
        boolean success = resultMap.success
        String reason = resultMap.reason

        then:
        resultMap instanceof Map
        success == false
        reason.contains( 'FileNotFoundError' )
    }


    def "composeDown(String composeFilePath) returns correctly"( ) {
        given:
        String containerName = 'composedown-container' + UNIQUE_NAME_POSTFIX

        composeFile << """
            version: '${COMPOSE_VERSION}'

            services:
              ${containerName}:
                container_name: ${containerName}
                image: ${TEST_IMAGE_REF}
                command: tail -f
        """.stripIndent( )

        when:
        String dockerInspectStateCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String[] dockerComposeUpCommand = [ 'docker-compose', '-f', composeFile.getAbsolutePath( ), 'up', '-d' ]
        GradleExecUtils.execWithException( dockerComposeUpCommand )


        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectStateCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectStateCommand ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


        def resultMap = DockerUtils.composeDown( composeFile.getAbsolutePath( ) )
        boolean success = resultMap.success


        count = 0
        boolean isExited = false

        def checkMap = GradleExecUtils.exec( dockerInspectStateCommand )

        if ( checkMap.exitValue == 0 && checkMap.out.equals( 'exited' ) ) {
            isExited = true
        } else if ( checkMap.exitValue != 0 && checkMap.err.contains( 'No such object' ) ) {
            isExited = true
        }

        while ( !isExited && count < NUM_RETRIES ) {

            Thread.sleep( SLEEP_TIME_MILLIS )

            checkMap = GradleExecUtils.exec( dockerInspectStateCommand )

            if ( checkMap.exitValue == 0 && checkMap.out.equals( 'exited' ) ) {
                isExited = true
            } else if ( checkMap.exitValue != 0 && checkMap.err.contains( 'No such object' ) ) {
                isExited = true
            }

            count++
        }


        then:
        resultMap instanceof Map
        success == true
        isExited == true

        cleanup:
        String[] dockerComposeDownCommand = [ 'docker-compose', '-f', composeFile.getAbsolutePath( ), 'down' ]
        GradleExecUtils.exec( dockerComposeDownCommand )
    }


    def "composeDown(String composeFilePath) returns correctly when in error"( ) {
        given:
        String badComposeFile = 'bad-filename'

        when:
        def resultMap = DockerUtils.composeDown( badComposeFile )
        boolean success = resultMap.success
        String reason = resultMap.reason

        then:
        resultMap instanceof Map
        success == false
        reason.contains( 'FileNotFoundError' )
    }



    //***********************************
    //***********************************
    //***********************************
    // docker run, stop, exec


    def "getDockerRunCommand(String image,command) returns correctly without a command"( ) {
        given:
        String image = 'myimage'

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image )

        then:
        String[] expected = [ 'docker', 'run', image ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,command) returns correctly without a command, as null"( ) {
        given:
        String image = 'myimage'

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image, null )

        then:
        String[] expected = [ 'docker', 'run', image ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,command) returns correctly with a String command"( ) {
        given:
        String image = 'myimage'
        String command = 'mycommand'

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image, command )

        then:
        String[] expected = [ 'docker', 'run', image, command ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,command) returns correctly without a command in an empty String[] command"( ) {
        given:
        String image = 'myimage'
        String[] command = [ ]

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image, command )

        then:
        String[] expected = [ 'docker', 'run', image ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,command) returns correctly with one command in the String[] command"( ) {
        given:
        String image = 'myimage'
        String[] command = [ 'mycommand1' ]

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image, command )

        then:
        String[] expected = [ 'docker', 'run', image, 'mycommand1' ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,command) returns correctly with more than one command in the String[] command"( ) {
        given:
        String image = 'myimage'
        String[] command = [ 'mycommand1', 'mycommand2', 'mycommand3' ]

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image, command )

        then:
        String[] expected = [ 'docker', 'run', image, 'mycommand1', 'mycommand2', 'mycommand3' ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,command) returns correctly when command has the wrong type of argument"( ) {
        given:
        String image = 'myimage'
        int command = 3

        when:
        DockerUtils.getDockerRunCommand( image, command )

        then:
        final ClassCastException exception = thrown()
    }

    def "getDockerRunCommand(String image,Map<String,String> options,command) returns correctly with null options"( ) {
        given:
        String image = 'myimage'
        String command = 'mycommand1'

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image, null, command )

        then:
        String[] expected = [ 'docker', 'run', image, 'mycommand1' ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,Map<String,String> options,command) returns correctly with empty options"( ) {
        given:
        String image = 'myimage'
        Map<String,String> options = new HashMap<String, String>( )
        String command = 'mycommand1'

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image, options, command )

        then:
        String[] expected = [ 'docker', 'run', image, 'mycommand1' ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,Map<String,String> options,command) returns correctly with one option"( ) {
        given:
        String image = 'myimage'
        Map<String,String> options = new HashMap<String, String>( )
        options.put( 'a', 'b' )
        String command = 'mycommand1'

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image, options, command )

        then:
        String[] expected = [ 'docker', 'run', 'a', 'b', image, 'mycommand1' ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,Map<String,String> options,command) returns correctly with more than one option"( ) {
        given:
        String image = 'myimage'
        Map<String,String> options = new HashMap<String, String>( )
        options.put( 'a', 'b' )
        options.put( 'c', 'd' )
        options.put( 'e', null )
        options.put( 'f', '' )
        options.put( 'g', 'h' )
        String command = 'mycommand1'

        when:
        String[] dockerRunCommand = DockerUtils.getDockerRunCommand( image, options, command )

        then:
        String[] expected = [ 'docker', 'run', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', image, 'mycommand1' ]
        expected == dockerRunCommand
    }

    def "getDockerRunCommand(String image,Map<String,String> options,command) returns correctly when command has wrong type of argument "( ) {
        given:
        String image = 'myimage'
        Map<String,String> options = new HashMap<String, String>( )
        options.put( 'a', 'b' )
        int command = 3

        when:
        DockerUtils.getDockerRunCommand( image, options, command )

        then:
        final ClassCastException exception = thrown()
    }

    def "dockerRun(String container,command) returns correctly when no such image"( ) {
        given:
        String image = 'blahnosuchimage'

        when:
        def resultMap = DockerUtils.dockerRun( image )
        boolean success = resultMap.success
        String reason = resultMap.reason

        then:
        resultMap instanceof Map
        success == false
        reason.contains( 'Unable to find image' )
    }

    def "dockerRun(String container,command) returns correctly when command isn't a String or String[]"( ) {
        given:
        String image = 'blahnosuchimage'
        int command = 5

        when:
        DockerUtils.dockerRun( image, command )

        then:
        final ClassCastException exception = thrown()
    }

    // not testing because difficult to get reference to exited container to remove
    //def "dockerRun(String image,command) returns correctly without command"( ) {
    //}

    // not testing because difficult to get reference to exited container to remove
    //def "dockerRun(String image,command) returns correctly with command"( ) {
    //}

    // not testing because difficult to get reference to exited container to remove
    //def "dockerRun(String image,Map<String,String> options, command) returns correctly without options or command"( ) {
    //}

    def "dockerRun(String image,Map<String,String> options, command) returns correctly when command isn't a String or String[]"( ) {
        given:
        String image = TEST_IMAGE_REF
        int command = 5

        Map<String,String> options = new HashMap<String,String>( )
        options.put( '--rm', null )

        when:
        DockerUtils.dockerRun( image, options, command )

        then:
        final ClassCastException exception = thrown()
    }

    def "dockerRun(String image,Map<String,String> options, command) returns correctly with options and without command"( ) {
        given:
        String image = TEST_IMAGE_REF

        Map<String,String> options = new HashMap<String,String>( )
        options.put( '--rm', null )

        when:
        def resultMap = DockerUtils.dockerRun( image, options )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == true
    }

    def "dockerRun(String image,Map<String,String> options, command) returns correctly with options and without command"( ) {
        given:
        String image = TEST_IMAGE_REF
        String container = 'dockerrun-string-map-options-multi-no-command' + UNIQUE_NAME_POSTFIX

        Map<String,String> options = new HashMap<String,String>( )
        options.put( '--rm', null )
        options.put( '--name', container )

        when:
        def resultMap = DockerUtils.dockerRun( image, options )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == true
    }

    def "dockerRun(String image,Map<String,String> options, command) returns correctly with options and with command string"( ) {
        given:
        String image = TEST_IMAGE_REF
        String container = 'dockerrun-string-map-options-multi-command-string' + UNIQUE_NAME_POSTFIX

        Map<String,String> options = new HashMap<String,String>( )
        options.put( '--rm', null )
        options.put( '--name', container )

        String command = 'ls'

        when:
        def resultMap = DockerUtils.dockerRun( image, options, command )
        boolean success = resultMap.success
        String output = resultMap.out

        then:
        resultMap instanceof Map
        success == true
        output.contains( 'bin' )
    }

    def "dockerRun(String image,Map<String,String> options, command) returns correctly with options and with command string array"( ) {
        given:
        String image = TEST_IMAGE_REF
        String container = 'dockerrun-string-map-options-multi-command-string-array' + UNIQUE_NAME_POSTFIX

        Map<String,String> options = new HashMap<String,String>( )
        options.put( '--rm', null )
        options.put( '--name', container )

        String[] command = ["/bin/ash", "-c", "ls && ps"]

        when:
        def resultMap = DockerUtils.dockerRun( image, options, command )
        boolean success = resultMap.success
        String output = resultMap.out

        then:
        resultMap instanceof Map
        success == true
        output.contains( 'bin' )
        output.contains( 'PID' )
    }

    def "getDockerStopCommand(String container) returns correctly"( ) {
        given:
        String container = 'mycontainer'

        when:
        String[] dockerStopCommand = DockerUtils.getDockerStopCommand( container )

        then:
        String[] expected = [ 'docker', 'stop', container ]
        expected == dockerStopCommand
    }

    def "dockerStop(String container) returns correctly when no such container"( ) {
        given:
        String container = 'dockerstop-invalid' + UNIQUE_NAME_POSTFIX

        when:
        def resultMap = DockerUtils.dockerStop( container )
        boolean success = resultMap.success
        String reason = resultMap.reason

        then:
        resultMap instanceof Map
        success == false
        reason.contains( 'No such container' )
    }


    def "dockerStop(String container) returns correctly"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerstop-valid' + UNIQUE_NAME_POSTFIX

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', containerImageRef, 'tail', '-f' ]
        String dockerInspectStateCommand = 'docker inspect -f {{.State.Status}} ' + containerName
        String dockerStopCommand = 'docker stop ' + containerName

        GradleExecUtils.execWithException( dockerRunCommand )

        int count = 0
        boolean isRunning = GradleExecUtils.execWithException( dockerInspectStateCommand ).equals( 'running' )

        while ( !isRunning && count < NUM_RETRIES ) {
            Thread.sleep( SLEEP_TIME_MILLIS )
            isRunning = GradleExecUtils.execWithException( dockerInspectStateCommand ).equals( 'running' )
            count++
        }

        if ( !isRunning ) {
            throw new GradleException( 'Docker container ' + containerName + ' did not reach "running" state.' )
        }


        def resultMap = DockerUtils.dockerStop( containerName )
        boolean success = resultMap.success


        count = 0
        boolean isExited = false

        def checkMap = GradleExecUtils.exec( dockerInspectStateCommand )

        if ( checkMap.exitValue == 0 && checkMap.out.equals( 'exited' ) ) {
            isExited = true
        } else if ( checkMap.exitValue != 0 && checkMap.err.contains( 'No such object' ) ) {
            isExited = true
        }

        while ( !isExited && count < NUM_RETRIES ) {

            Thread.sleep( SLEEP_TIME_MILLIS )

            checkMap = GradleExecUtils.exec( dockerInspectStateCommand )

            if ( checkMap.exitValue == 0 && checkMap.out.equals( 'exited' ) ) {
                isExited = true
            } else if ( checkMap.exitValue != 0 && checkMap.err.contains( 'No such object' ) ) {
                isExited = true
            }

            count++
        }

        then:
        resultMap instanceof Map
        success == true
        isExited == true

        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "getDockerExecCommand(String container, String command, Map<String,String> options) returns correctly without options"( ) {
        given:
        String container = 'mycontainer'
        String command = 'mycommand'

        when:
        String[] dockerExecCommand = DockerUtils.getDockerExecCommand( container, command )

        then:
        String[] expected = [ 'docker', 'exec', container, command ]
        expected == dockerExecCommand
    }

    def "getDockerExecCommand(String container, String command, Map<String,String> options) returns correctly with one option"( ) {
        given:
        String container = 'mycontainer'
        String command = 'mycommand'

        Map<String,String> options = new HashMap<String,String>( )
        options.put( 'a', 'b' )

        when:
        String[] dockerExecCommand = DockerUtils.getDockerExecCommand( container, command, options )

        then:
        String[] expected = [ 'docker', 'exec', 'a', 'b', container, command ]
        expected == dockerExecCommand
    }

    def "getDockerExecCommand(String container, String command, Map<String,String> options) returns correctly with more than one option"( ) {
        given:
        String container = 'mycontainer'
        String command = 'mycommand'

        Map<String,String> options = new HashMap<String,String>( )
        options.put( 'a', 'b' )
        options.put( 'c', '2' )
        options.put( 'd', '' )
        options.put( 'e', null )

        when:
        String[] dockerExecCommand = DockerUtils.getDockerExecCommand( container, command, options )

        then:
        String[] expected = [ 'docker', 'exec', 'a', 'b', 'c', '2', 'd', 'e', container, command ]
        expected == dockerExecCommand
    }

    def "getDockerExecCommand(String container, String[] command, Map<String,String> options) returns correctly without options"( ) {
        given:
        String container = 'mycontainer'
        String[] command = [ 'mycommand', 'arg1', 'arg2' ]

        when:
        String[] dockerExecCommand = DockerUtils.getDockerExecCommand( container, command )

        then:
        String[] expected = [ 'docker', 'exec', container, 'mycommand', 'arg1', 'arg2' ]
        expected == dockerExecCommand
    }

    def "getDockerExecCommand(String container, String[] command, Map<String,String> options) returns correctly with one option"( ) {
        given:
        String container = 'mycontainer'
        String[] command = [ 'mycommand', 'arg1', 'arg2' ]

        Map<String,String> options = new HashMap<String,String>( )
        options.put( 'a', 'b' )

        when:
        String[] dockerExecCommand = DockerUtils.getDockerExecCommand( container, command, options )

        then:
        String[] expected = [ 'docker', 'exec', 'a', 'b', container, 'mycommand', 'arg1', 'arg2' ]
        expected == dockerExecCommand
    }

    def "getDockerExecCommand(String container, String[] command, Map<String,String> options) returns correctly with more than one option"( ) {
        given:
        String container = 'mycontainer'
        String[] command = [ 'mycommand', 'arg1', 'arg2' ]

        Map<String,String> options = new HashMap<String,String>( )
        options.put( 'a', 'b' )
        options.put( 'c', '2' )
        options.put( 'd', '' )
        options.put( 'e', null )

        when:
        String[] dockerExecCommand = DockerUtils.getDockerExecCommand( container, command, options )

        then:
        String[] expected = [ 'docker', 'exec', 'a', 'b', 'c', '2', 'd', 'e', container, 'mycommand', 'arg1', 'arg2' ]
        expected == dockerExecCommand
    }

    def "dockerExec(String container, String command, Map<String,String> options) returns correctly without options"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerexec-str-command-no-options-good' + UNIQUE_NAME_POSTFIX
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


        def resultMap = DockerUtils.dockerExec( containerName, command )
        boolean success = resultMap.success
        String output = resultMap.out


        then:
        resultMap instanceof Map
        success == true
        output.contains( 'bin' )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "dockerExec(String container, String command, Map<String,String> options) returns correctly with options"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerexec-str-command-with-options-good' + UNIQUE_NAME_POSTFIX
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


        def resultMap = DockerUtils.dockerExec( containerName, command, options )
        boolean success = resultMap.success
        String output = resultMap.out


        then:
        resultMap instanceof Map
        success == true
        output.contains( 'kernel' )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "dockerExec(String container, String command, Map<String,String> options) returns correctly with bad input"( ) {
        given:
        String containerName = 'dockerexec-no-such-container' + UNIQUE_NAME_POSTFIX
        String command = 'ls'

        when:
        def resultMap = DockerUtils.dockerExec( containerName, command )
        boolean success = resultMap.success
        String reason = resultMap.reason


        then:
        resultMap instanceof Map
        success == false
        reason.contains( 'No such container' )
    }

    def "dockerExec(String container, String[] command, Map<String,String> options) returns correctly without options"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerexec-array-command-no-options-good' + UNIQUE_NAME_POSTFIX
        String[] command = ["/bin/ash", "-c", "ls && ps"]

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


        def resultMap = DockerUtils.dockerExec( containerName, command )
        boolean success = resultMap.success
        String output = resultMap.out


        then:
        resultMap instanceof Map
        success == true
        output.contains( 'bin' )
        output.contains( 'PID' )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "dockerExec(String container, String[] command, Map<String,String> options) returns correctly with options"( ) {
        given:
        String containerImageRef = TEST_IMAGE_REF
        String containerName = 'dockerexec-array-command-with-options-good' + UNIQUE_NAME_POSTFIX
        String[] command = ["/bin/ash", "-c", "ls && ps"]
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


        def resultMap = DockerUtils.dockerExec( containerName, command, options )
        boolean success = resultMap.success
        String output = resultMap.out


        then:
        resultMap instanceof Map
        success == true
        output.contains( 'kernel' )
        output.contains( 'PID' )


        cleanup:
        GradleExecUtils.exec( dockerStopCommand )
    }

    def "dockerExec(String container, String[] command, Map<String,String> options) returns correctly with bad input"( ) {
        given:
        String containerName = 'dockerexec-no-such-container' + UNIQUE_NAME_POSTFIX
        String[] command = ["/bin/bash", "-c", "ls && ps"]

        when:
        def resultMap = DockerUtils.dockerExec( containerName, command )
        boolean success = resultMap.success
        String reason = resultMap.reason

        then:
        resultMap instanceof Map
        success == false
        reason.contains( 'No such container' )
    }




    //***********************************
    //***********************************
    //***********************************
    // docker save, tag, push


    def "dockerSave(String image,String filename,boolean gzip) returns correctly without gzip option"( ) {
        given:
        String image = TEST_IMAGE_REF
        String filename = 'dockersave-gzip-none' + UNIQUE_NAME_POSTFIX + '.tar.gz'
        String filenamepath = tempDir.toString( ) + File.separatorChar + filename
        File outputFile = new File( tempDir.toString( ) + File.separatorChar + filename )

        when:
        def resultMap = DockerUtils.dockerSave( image, filenamepath )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == true
        outputFile.exists( )
    }


    def "dockerSave(String image,String filename,boolean gzip) returns correctly with gzip true"( ) {
        given:
        String image = TEST_IMAGE_REF
        String filename = 'dockersave-gzip-true' + UNIQUE_NAME_POSTFIX + '.tar.gz'
        String filenamepath = tempDir.toString( ) + File.separatorChar + filename
        File outputFile = new File( tempDir.toString( ) + File.separatorChar + filename )

        when:
        def resultMap = DockerUtils.dockerSave( image, filenamepath, true )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == true
        outputFile.exists( )
    }


    def "dockerSave(String image,String filename,boolean gzip) returns correctly with gzip false"( ) {
        given:
        String image = TEST_IMAGE_REF
        String filename = 'dockersave-gzip-false' + UNIQUE_NAME_POSTFIX + '.tar'
        String filenamepath = tempDir.toString( ) + File.separatorChar + filename
        File outputFile = new File( tempDir.toString( ) + File.separatorChar + filename )

        when:
        def resultMap = DockerUtils.dockerSave( image, filenamepath, false )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == true
        outputFile.exists( )
    }

    def "dockerSave(String image,String filename,boolean gzip) returns correctly when no such image"( ) {
        given:
        String image = 'asdlkfsdf'
        String filename = 'dockersave-nosuchimage' + UNIQUE_NAME_POSTFIX + '.tar'
        String filenamepath = tempDir.toString( ) + File.separatorChar + filename

        when:
        def resultMap = DockerUtils.dockerSave( image, filenamepath, false )
        boolean success = resultMap.success
        String reason = resultMap.reason

        then:
        resultMap instanceof Map
        success == false
        reason.contains( 'No such image' )
    }
    comment */

    /* comment

    def "dockerTag(String image,String tag) returns correctly"( ) {
        given:
        String image = TEST_IMAGE_REF
        String tag = 'dockertag-img-tag' + UNIQUE_NAME_POSTFIX + ':1.0'

        when:
        def resultMap = DockerUtils.dockerTag( image, tag )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck = [ 'docker', 'image', 'inspect', tag ]
        if ( GradleExecUtils.exec( tagCheck ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        GradleExecUtils.exec( 'docker rmi ' + tag )
    }

    def "dockerTag(String image,String tag) returns correctly when no such image"( ) {
        given:
        String image = 'nosuchimage'
        String tag = 'dockertag-nosuchimage' + UNIQUE_NAME_POSTFIX + ':1.0'

        when:
        def resultMap = DockerUtils.dockerTag( image, tag )
        boolean success = resultMap.success
        String error = resultMap.reason

        then:
        resultMap instanceof Map
        success == false
        error.contains( 'No such image' )
    }

    def "dockerTag(String image,String[] tag) returns correctly given one tag"( ) {
        given:
        String image = TEST_IMAGE_REF
        String tag1 = 'dockertag-img-1tag' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [ tag1 ]

        when:
        def resultMap = DockerUtils.dockerTag( image, tag1 )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck = [ 'docker', 'image', 'inspect', tag1 ]
        if ( GradleExecUtils.exec( tagCheck ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        GradleExecUtils.exec( 'docker rmi ' + tag1 )
    }

    def "dockerTag(String image,String[] tag) returns correctly given two tags"( ) {
        given:
        String image = TEST_IMAGE_REF
        String tag1 = 'dockertag-img-2tag1' + UNIQUE_NAME_POSTFIX + ':1.0'
        String tag2 = 'dockertag-img-2tag2' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [ tag1, tag2 ]

        when:
        def resultMap = DockerUtils.dockerTag( image, tags )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck1 = [ 'docker', 'image', 'inspect', tag1 ]
        String[] tagCheck2 = [ 'docker', 'image', 'inspect', tag2 ]
        if ( ( GradleExecUtils.exec( tagCheck1 ).exitValue + GradleExecUtils.exec( tagCheck2 ).exitValue ) == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        GradleExecUtils.exec( 'docker rmi ' + tag1 )
        GradleExecUtils.exec( 'docker rmi ' + tag2 )
    }

    def "dockerTag(String image,String[] tag) returns correctly when no such image"( ) {
        given:
        String image = 'nosuchimage'
        String tag1 = 'dockertag-nosuchimage' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [ tag1 ]

        when:
        def resultMap = DockerUtils.dockerTag( image, tags )
        boolean success = resultMap.success
        def tagErrorMap = resultMap.reason
        String error = tagErrorMap.get( tag1 )

        then:
        resultMap instanceof Map
        success == false
        tagErrorMap instanceof Map
        error.contains( 'No such image' )
    }

    def "dockerTag(Map<String,String> imageTag) returns correctly given one image-tag pair"( ) {
        given:
        String image = TEST_IMAGE_REF
        String tag1 = 'dockertag-1img-tag' + UNIQUE_NAME_POSTFIX + ':1.0'

        Map<String,String> tagMap = new HashMap<String,String>( )
        tagMap.put( image, tag1 )

        when:
        def resultMap = DockerUtils.dockerTag( tagMap )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck = [ 'docker', 'image', 'inspect', tag1 ]
        if ( GradleExecUtils.exec( tagCheck ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        GradleExecUtils.exec( 'docker rmi ' + tag1 )
    }

    def "dockerTag(Map<String,String> imageTag) returns correctly given two image-tag pairs"( ) {
        given:
        String tagTest = 'dockertag-str-str-2img-tagt' + UNIQUE_NAME_POSTFIX + ':1.0'
        GradleExecUtils.exec( 'docker tag ' + TEST_IMAGE_REF + ' ' + tagTest )

        String image1 = TEST_IMAGE_REF
        String image2 = tagTest
        String tag1 = 'dockertag-str-str-2img-tag1' + UNIQUE_NAME_POSTFIX + ':1.0'
        String tag2 = 'dockertag-str-str-2img-tag2' + UNIQUE_NAME_POSTFIX + ':1.0'

        Map<String,String> tagMap = new HashMap<String,String>( )
        tagMap.put( image1, tag1 )
        tagMap.put( image2, tag2 )

        when:
        def resultMap = DockerUtils.dockerTag( tagMap )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck1 = [ 'docker', 'image', 'inspect', tag1 ]
        String[] tagCheck2 = [ 'docker', 'image', 'inspect', tag2 ]
        if ( ( GradleExecUtils.exec( tagCheck1 ).exitValue + GradleExecUtils.exec( tagCheck2 ).exitValue ) == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        GradleExecUtils.exec( 'docker rmi ' + tagTest )
        GradleExecUtils.exec( 'docker rmi ' + tag1 )
        GradleExecUtils.exec( 'docker rmi ' + tag2 )
    }

    def "dockerTag(Map<String,String> imageTag) returns correctly when tag is not of String type"( ) {
        given:
        String image = 'nosuchimage'
        Object tag1 = new Object( )

        Map<String,Object> tagMap = new HashMap<String,Object>( )
        tagMap.put( image, tag1 )

        when:
        DockerUtils.dockerTag( tagMap )

        then:
        final ClassCastException exception = thrown()
    }

    def "dockerTag(Map<String,String> imageTag) returns correctly when no such image"( ) {
        given:
        String image = 'nosuchimage'
        String tag1 = 'dockertag-nosuchimage' + UNIQUE_NAME_POSTFIX + ':1.0'

        Map<String,String> tagMap = new HashMap<String,String>( )
        tagMap.put( image, tag1 )

        when:
        def resultMap = DockerUtils.dockerTag( tagMap )
        boolean success = resultMap.success
        def imageTagMap = resultMap.reason
        def tagErrorMap = imageTagMap.get( image )
        String error = tagErrorMap.get( tag1 )

        then:
        resultMap instanceof Map
        success == false
        imageTagMap instanceof Map
        tagErrorMap instanceof Map
        error.contains( 'No such image' )
    }

    def "dockerTag(Map<String,String[]> imageTag) returns correctly given one image to one tag"( ) {
        given:
        String image = TEST_IMAGE_REF
        String tag1 = 'dockertag-1img-1tag' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [ tag1 ]
        Map<String,String[]> tagMap = new HashMap<String,String[]>( )
        tagMap.put( image, tags )

        when:
        def resultMap = DockerUtils.dockerTag( tagMap )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck = [ 'docker', 'image', 'inspect', tag1 ]
        if ( GradleExecUtils.exec( tagCheck ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        GradleExecUtils.exec( 'docker rmi ' + tag1 )
    }

    def "dockerTag(Map<String,String[]> imageTag) returns correctly given one image to two tags"( ) {
        given:
        String image = TEST_IMAGE_REF
        String tag1 = 'dockertag-1img-2tag1' + UNIQUE_NAME_POSTFIX + ':1.0'
        String tag2 = 'dockertag-1img-2tag2' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [ tag1, tag2 ]
        Map<String,String[]> tagMap = new HashMap<String,String[]>( )
        tagMap.put( image, tags )

        when:
        def resultMap = DockerUtils.dockerTag( tagMap )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck1 = [ 'docker', 'image', 'inspect', tag1 ]
        String[] tagCheck2 = [ 'docker', 'image', 'inspect', tag2 ]
        if ( ( GradleExecUtils.exec( tagCheck1 ).exitValue + GradleExecUtils.exec( tagCheck2 ).exitValue ) == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        GradleExecUtils.exec( 'docker rmi ' + tag1 )
        GradleExecUtils.exec( 'docker rmi ' + tag2 )
    }

    def "dockerTag(Map<String,String[]> imageTag) returns correctly given two images to one tag"( ) {
        given:
        String tagTest = 'dockertag-2img-1tagt' + UNIQUE_NAME_POSTFIX + ':1.0'
        GradleExecUtils.exec( 'docker tag ' + TEST_IMAGE_REF + ' ' + tagTest )

        String image1 = TEST_IMAGE_REF
        String image2 = tagTest
        String tag1 = 'dockertag-2img-1tag1' + UNIQUE_NAME_POSTFIX + ':1.0'
        String tag2 = 'dockertag-2img-1tag2' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags1 = [ tag1 ]
        String[] tags2 = [ tag2 ]
        Map<String,String[]> tagMap = new HashMap<String,String[]>( )
        tagMap.put( image1, tags1 )
        tagMap.put( image2, tags2 )

        when:
        def resultMap = DockerUtils.dockerTag( tagMap )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck1 = [ 'docker', 'image', 'inspect', tag1 ]
        String[] tagCheck2 = [ 'docker', 'image', 'inspect', tag2 ]
        if ( ( GradleExecUtils.exec( tagCheck1 ).exitValue + GradleExecUtils.exec( tagCheck2 ).exitValue ) == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        GradleExecUtils.exec( 'docker rmi ' + tagTest )
        GradleExecUtils.exec( 'docker rmi ' + tag1 )
        GradleExecUtils.exec( 'docker rmi ' + tag2 )
    }

    def "dockerTag(Map<String,String[]> imageTag) returns correctly given two images to two tags"( ) {
        given:
        String tagTest = 'dockertag-2img-2tagt' + UNIQUE_NAME_POSTFIX + ':1.0'
        GradleExecUtils.exec( 'docker tag ' + TEST_IMAGE_REF + ' ' + tagTest )

        String image1 = TEST_IMAGE_REF
        String image2 = tagTest
        String tag11 = 'dockertag-2img-2tag11' + UNIQUE_NAME_POSTFIX + ':1.0'
        String tag12 = 'dockertag-2img-2tag12' + UNIQUE_NAME_POSTFIX + ':1.0'
        String tag21 = 'dockertag-2img-2tag21' + UNIQUE_NAME_POSTFIX + ':1.0'
        String tag22 = 'dockertag-2img-2tag22' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags1 = [ tag11, tag12 ]
        String[] tags2 = [ tag21, tag22 ]
        Map<String,String[]> tagMap = new HashMap<String,String[]>( )
        tagMap.put( image1, tags1 )
        tagMap.put( image2, tags2 )

        when:
        def resultMap = DockerUtils.dockerTag( tagMap )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck11 = [ 'docker', 'image', 'inspect', tag11 ]
        String[] tagCheck12 = [ 'docker', 'image', 'inspect', tag12 ]
        String[] tagCheck21 = [ 'docker', 'image', 'inspect', tag21 ]
        String[] tagCheck22 = [ 'docker', 'image', 'inspect', tag22 ]
        if ( ( GradleExecUtils.exec( tagCheck11 ).exitValue + GradleExecUtils.exec( tagCheck12 ).exitValue + GradleExecUtils.exec( tagCheck21 ).exitValue + GradleExecUtils.exec( tagCheck22 ).exitValue ) == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        GradleExecUtils.exec( 'docker rmi ' + tagTest )
        GradleExecUtils.exec( 'docker rmi ' + tag11 )
        GradleExecUtils.exec( 'docker rmi ' + tag12 )
        GradleExecUtils.exec( 'docker rmi ' + tag21 )
        GradleExecUtils.exec( 'docker rmi ' + tag22 )
    }
    comment */

    /* comment


    def "dockerTag(Map<String,String[]> imageTag) returns correctly when no such image"( ) {
        given:
        String image = 'nosuchimage'
        String tag1 = 'dockertag-nosuchimage' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [ tag1 ]
        Map<String,String[]> tagMap = new HashMap<String,String[]>( )
        tagMap.put( image, tags )

        when:
        def resultMap = DockerUtils.dockerTag( tagMap )
        boolean success = resultMap.success
        def imageTagMap = resultMap.reason
        def tagErrorMap = imageTagMap.get( image )
        String error = tagErrorMap.get( tag1 )

        then:
        resultMap instanceof Map
        success == false
        imageTagMap instanceof Map
        tagErrorMap instanceof Map
        error.contains( 'No such image' )
    }

    def "dockerTag(Map<String,String[]> imageTag) returns correctly when tag is not of String[] type"( ) {
        given:
        String image = 'nosuchimage'
        Object tag1 = new Object( )

        Object[] tags = [ tag1 ]

        Map<String,Object[]> tagMap = new HashMap<String,Object[]>( )
        tagMap.put( image, tags )

        when:
        DockerUtils.dockerTag( tagMap )

        then:
        final ClassCastException exception = thrown()
    }

    def "dockerBuild(String buildDirectory,String tag) returns correctly"( ) {
        given:

        String buildDirString = tempDir.toString( ) + File.separatorChar + 'dockerbuild-string-valid'
        File buildDir = new File( buildDirString )
        buildDir.mkdirs( )

        String dockerfileString = buildDirString + File.separatorChar + 'Dockerfile'
        File dockerfile = new File( dockerfileString )
        dockerfile.createNewFile( )

        dockerfile << """
            FROM ${TEST_IMAGE_REF}
            ENV X 1
            COPY myfile.txt /
            WORKDIR /
        """.stripIndent( )

        String myfileString = buildDirString + File.separatorChar + 'myfile.txt'
        File myfile = new File( myfileString )
        myfile.createNewFile( )

        myfile << """
            Some important data or functionality
        """.stripIndent( )

        String tag = 'dockerbuild-string-valid' + UNIQUE_NAME_POSTFIX + ':1.0'

        when:
        def resultMap = DockerUtils.dockerBuild( buildDirString, tag )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck = [ 'docker', 'image', 'inspect', tag ]
        if ( GradleExecUtils.exec( tagCheck ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        String[] removeImage = [ 'docker', 'rmi', tag ]
        GradleExecUtils.exec( removeImage )
    }

    def "dockerBuild(String buildDirectory,String tags) returns correctly when invalid directory"( ) {
        given:
        String invalidDirectory = tempDir.toString( ) + File.separatorChar + 'blah'

        String buildDirString = tempDir.toString( ) + File.separatorChar + 'dockerbuild-string-invalid-dir'
        File buildDir = new File( buildDirString )
        buildDir.mkdirs( )

        String dockerfileString = buildDirString + File.separatorChar + 'Dockerfile'
        File dockerfile = new File( dockerfileString )
        dockerfile.createNewFile( )

        dockerfile << """
            FROM ${TEST_IMAGE_REF}
            ENV X 1
            COPY myfile.txt /
            WORKDIR /
        """.stripIndent( )

        String myfileString = buildDirString + File.separatorChar + 'myfile.txt'
        File myfile = new File( myfileString )
        myfile.createNewFile( )

        myfile << """
            Some important data or functionality
        """.stripIndent( )

        String tag = 'dockerbuild-string-invalid-dir' + UNIQUE_NAME_POSTFIX + ':1.0'

        when:
        def resultMap = DockerUtils.dockerBuild( invalidDirectory, tag )
        boolean success = resultMap.success
        String reason = resultMap.reason

        boolean imageTagged = false
        String[] tagCheck = [ 'docker', 'image', 'inspect', tag ]
        if ( GradleExecUtils.exec( tagCheck ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == false
        imageTagged == false
        reason.contains( 'unable to prepare context: path' )
    }

    def "dockerBuild(String buildDirectory,String tags) returns correctly when no Dockerfile"( ) {
        given:

        String buildDirString = tempDir.toString( ) + File.separatorChar + 'dockerbuild-string-no-dockerfile'
        File buildDir = new File( buildDirString )
        buildDir.mkdirs( )

        String myfileString = buildDirString + File.separatorChar + 'myfile.txt'
        File myfile = new File( myfileString )
        myfile.createNewFile( )

        myfile << """
            Some important data or functionality
        """.stripIndent( )

        String tag = 'dockerbuild-string-no-dockerfile' + UNIQUE_NAME_POSTFIX + ':1.0'

        when:
        def resultMap = DockerUtils.dockerBuild( buildDirString, tag )
        boolean success = resultMap.success
        String reason = resultMap.reason

        boolean imageTagged = false
        String[] tagCheck = [ 'docker', 'image', 'inspect', tag ]
        if ( GradleExecUtils.exec( tagCheck ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == false
        imageTagged == false
        reason.contains( 'failed to read dockerfile' )
    }

    def "dockerBuild(String buildDirectory,String tags) returns correctly when Dockerfile in error"( ) {
        given:

        String buildDirString = tempDir.toString( ) + File.separatorChar + 'string-dockerfile-error'
        File buildDir = new File( buildDirString )
        buildDir.mkdirs( )

        String dockerfileString = buildDirString + File.separatorChar + 'Dockerfile'
        File dockerfile = new File( dockerfileString )
        dockerfile.createNewFile( )

        dockerfile << """
            FROM ${TEST_IMAGE_REF}
            badcommand
            ENV X 1
            COPY myfile.txt /
            WORKDIR /
        """.stripIndent( )

        String myfileString = buildDirString + File.separatorChar + 'myfile.txt'
        File myfile = new File( myfileString )
        myfile.createNewFile( )

        myfile << """
            Some important data or functionality
        """.stripIndent( )

        String tag = 'dockerbuild-string-dockerfile-error' + UNIQUE_NAME_POSTFIX + ':1.0'

        when:
        def resultMap = DockerUtils.dockerBuild( buildDirString, tag )
        boolean success = resultMap.success
        String reason = resultMap.reason

        boolean imageTagged = false
        String[] tagCheck = [ 'docker', 'image', 'inspect', tag ]
        if ( GradleExecUtils.exec( tagCheck ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == false
        imageTagged == false
        reason.contains( 'dockerfile parse error' )
    }

    def "dockerBuild(String buildDirectory,String[] tags) returns correctly with one tag"( ) {
        given:

        String buildDirString = tempDir.toString( ) + File.separatorChar + 'dockerbuild-array-one-tag'
        File buildDir = new File( buildDirString )
        buildDir.mkdirs( )

        String dockerfileString = buildDirString + File.separatorChar + 'Dockerfile'
        File dockerfile = new File( dockerfileString )
        dockerfile.createNewFile( )

        dockerfile << """
            FROM ${TEST_IMAGE_REF}
            ENV X 1
            COPY myfile.txt /
            WORKDIR /
        """.stripIndent( )

        String myfileString = buildDirString + File.separatorChar + 'myfile.txt'
        File myfile = new File( myfileString )
        myfile.createNewFile( )

        myfile << """
            Some important data or functionality
        """.stripIndent( )

        String tag1 = 'dockerbuild-array-one-tag' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [tag1]

        when:
        def resultMap = DockerUtils.dockerBuild( buildDirString, tags )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck1 = [ 'docker', 'image', 'inspect', tag1 ]
        if ( GradleExecUtils.exec( tagCheck1 ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        String[] removeImage = [ 'docker', 'rmi', tag1 ]
        GradleExecUtils.exec( removeImage )
    }

    def "dockerBuild(String buildDirectory,String[] tags) returns correctly with two tags"( ) {
        given:

        String buildDirString = tempDir.toString( ) + File.separatorChar + 'dockerbuild-array-two-tag'
        File buildDir = new File( buildDirString )
        buildDir.mkdirs( )

        String dockerfileString = buildDirString + File.separatorChar + 'Dockerfile'
        File dockerfile = new File( dockerfileString )
        dockerfile.createNewFile( )

        dockerfile << """
            FROM ${TEST_IMAGE_REF}
            ENV X 1
            COPY myfile.txt /
            WORKDIR /
        """.stripIndent( )

        String myfileString = buildDirString + File.separatorChar + 'myfile.txt'
        File myfile = new File( myfileString )
        myfile.createNewFile( )

        myfile << """
            Some important data or functionality
        """.stripIndent( )

        String tag1 = 'dockerbuild-array-two-tag' + UNIQUE_NAME_POSTFIX + ':1.0'
        String tag2 = 'dockerbuild-array-two-tag' + UNIQUE_NAME_POSTFIX + ':1.1'

        String[] tags = [ tag1, tag2 ]

        when:
        def resultMap = DockerUtils.dockerBuild( buildDirString, tags )
        boolean success = resultMap.success

        boolean imageTagged = false
        String[] tagCheck1 = [ 'docker', 'image', 'inspect', tag1 ]
        String[] tagCheck2 = [ 'docker', 'image', 'inspect', tag2 ]
        if ( ( GradleExecUtils.exec( tagCheck1 ).exitValue + GradleExecUtils.exec( tagCheck2 ).exitValue ) == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == true
        imageTagged == true

        cleanup:
        String[] removeImage = [ 'docker', 'rmi', tag1, tag2 ]
        GradleExecUtils.exec( removeImage )
    }

    def "dockerBuild(String buildDirectory,String[] tags) returns correctly when invalid directory"( ) {
        given:
        String invalidDirectory = tempDir.toString( ) + File.separatorChar + 'blah'

        String buildDirString = tempDir.toString( ) + File.separatorChar + 'dockerbuild-array-invalid-dir'
        File buildDir = new File( buildDirString )
        buildDir.mkdirs( )

        String dockerfileString = buildDirString + File.separatorChar + 'Dockerfile'
        File dockerfile = new File( dockerfileString )
        dockerfile.createNewFile( )

        dockerfile << """
            FROM ${TEST_IMAGE_REF}
            ENV X 1
            COPY myfile.txt /
            WORKDIR /
        """.stripIndent( )

        String myfileString = buildDirString + File.separatorChar + 'myfile.txt'
        File myfile = new File( myfileString )
        myfile.createNewFile( )

        myfile << """
            Some important data or functionality
        """.stripIndent( )

        String tag1 = 'dockerbuild-array-invalid-dir' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [tag1]

        when:
        def resultMap = DockerUtils.dockerBuild( invalidDirectory, tags )
        boolean success = resultMap.success
        String reason = resultMap.reason

        boolean imageTagged = false
        String[] tagCheck1 = [ 'docker', 'image', 'inspect', tag1 ]
        if ( GradleExecUtils.exec( tagCheck1 ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == false
        imageTagged == false
        reason.contains( 'unable to prepare context: path' )
    }

    def "dockerBuild(String buildDirectory,String[] tags) returns correctly when no Dockerfile"( ) {
        given:

        String buildDirString = tempDir.toString( ) + File.separatorChar + 'dockerbuild-array-no-dockerfile'
        File buildDir = new File( buildDirString )
        buildDir.mkdirs( )

        String myfileString = buildDirString + File.separatorChar + 'myfile.txt'
        File myfile = new File( myfileString )
        myfile.createNewFile( )

        myfile << """
            Some important data or functionality
        """.stripIndent( )

        String tag1 = 'dockerbuild-array-no-dockerfile' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [tag1]

        when:
        def resultMap = DockerUtils.dockerBuild( buildDirString, tags )
        boolean success = resultMap.success
        String reason = resultMap.reason

        boolean imageTagged = false
        String[] tagCheck1 = [ 'docker', 'image', 'inspect', tag1 ]
        if ( GradleExecUtils.exec( tagCheck1 ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == false
        imageTagged == false
        reason.contains( 'failed to read dockerfile' )
    }

    def "dockerBuild(String buildDirectory,String[] tags) returns correctly when Dockerfile in error"( ) {
        given:

        String buildDirString = tempDir.toString( ) + File.separatorChar + 'dockerbuild-array-dockerfile-error'
        File buildDir = new File( buildDirString )
        buildDir.mkdirs( )

        String dockerfileString = buildDirString + File.separatorChar + 'Dockerfile'
        File dockerfile = new File( dockerfileString )
        dockerfile.createNewFile( )

        dockerfile << """
            FROM ${TEST_IMAGE_REF}
            badcommand
            ENV X 1
            COPY myfile.txt /
            WORKDIR /
        """.stripIndent( )

        String myfileString = buildDirString + File.separatorChar + 'myfile.txt'
        File myfile = new File( myfileString )
        myfile.createNewFile( )

        myfile << """
            Some important data or functionality
        """.stripIndent( )

        String tag1 = 'dockerbuild-array-dockerfile-error' + UNIQUE_NAME_POSTFIX + ':1.0'

        String[] tags = [tag1]

        when:
        def resultMap = DockerUtils.dockerBuild( buildDirString, tags )
        boolean success = resultMap.success
        String reason = resultMap.reason

        boolean imageTagged = false
        String[] tagCheck1 = [ 'docker', 'image', 'inspect', tag1 ]
        if ( GradleExecUtils.exec( tagCheck1 ).exitValue == 0 ) {
            imageTagged = true
        }

        then:
        resultMap instanceof Map
        success == false
        imageTagged == false
        reason.contains( 'dockerfile parse error' )
    }

    comment */



    def "dockerPush(String tag,boolean allTags=false) returns correctly without 'allTags'"( ) {
        given:
        String testName = 'dockerpush-string-tagnone-good'

        String registryImageRef = REGISTRY_IMAGE_REF
        String registryName = testName + UNIQUE_NAME_POSTFIX

        String port = 5000

        String tagNameBaseA = testName
        String tagNameWithRegistryA = 'localhost:' + port + '/' + tagNameBaseA
        String tagVersionA1 = '1.0'
        String tagA1 = tagNameWithRegistryA + ':' + tagVersionA1

        String tagCommandA1 = 'docker tag ' + REGISTRY_IMAGE_REF + ' ' + tagA1


        GradleExecUtils.execWithException( tagCommandA1 )

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( registryName, 'running' )

        composeFile << """
            services:
              ${registryName}:
                container_name: ${registryName}
                image: ${registryImageRef}
                ports:
                  - ${port}:5000
        """.stripIndent( )

        when:

        def resultMapUp = DockerUtils.composeUp( composeFile.getAbsolutePath( ) )
        
        if ( !resultMapUp.success ) {
            throw new GradleException( 'An error occurred when running "docker compose up": ' + resultMapUp.message )
        }

        def resultWaitMapWait = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )

        if ( !resultWaitMapWait.success ) {
            if ( resultWaitMapWait.reason.equals( 'error' ) ) {
                throw new GradleException( 'An error occurred when running "docker compose up": ' + resultWaitMapWait.message )
            } else {
                throw new GradleException( 'A container failed when running "docker compose up".' )
            }
        }


        String registryCheckCommand = 'curl --fail http://localhost:' + port

        int count = 0
        boolean registryOk = false

        if ( GradleExecUtils.exec( registryCheckCommand ).exitValue == 0 ) {
            registryOk = true
        }

        while ( !registryOk && count < NUM_RETRIES ) {

            Thread.sleep( SLEEP_TIME_MILLIS )

            if ( GradleExecUtils.exec( registryCheckCommand ).exitValue == 0 ) {
                registryOk = true
            }

            count++

        }

        if ( !registryOk ) {
            throw new GradleException( 'Docker private registery container ' + registryName + ' is not responding.' )
        }


        def resultMap = DockerUtils.dockerPush( tagA1 )
        boolean success = resultMap.success


        String registryImageCheckA1 = 'curl -X GET localhost:' + port + '/v2/' + tagNameBaseA + '/manifests/' + tagVersionA1
        boolean registryHasImageA1 = false

        if ( GradleExecUtils.exec( registryImageCheckA1 ).exitValue == 0 ) {
            registryHasImageA1 = true
        }


        then:
        resultMap instanceof Map
        success == true
        registryHasImageA1 == true


        cleanup:
        DockerUtils.composeDown( composeFile.getAbsolutePath( ) )

        String rmTagCommandA1 = 'docker rmi ' + tagA1
        GradleExecUtils.exec( rmTagCommandA1 )
    }

    def "dockerPush(String tag,boolean allTags=false) returns correctly with 'allTags' false"( ) {
        given:
        String testName = 'dockerpush-string-tagfalse-good'

        String registryImageRef = REGISTRY_IMAGE_REF
        String registryName = testName + UNIQUE_NAME_POSTFIX

        String port = 5000

        String tagNameBaseA = testName
        String tagNameWithRegistryA = 'localhost:' + port + '/' + tagNameBaseA
        String tagVersionA1 = '1.0'
        String tagA1 = tagNameWithRegistryA + ':' + tagVersionA1

        String tagCommandA1 = 'docker tag ' + REGISTRY_IMAGE_REF + ' ' + tagA1


        GradleExecUtils.execWithException( tagCommandA1 )

        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( registryName, 'running' )

        composeFile << """
            services:
              ${registryName}:
                container_name: ${registryName}
                image: ${registryImageRef}
                ports:
                  - ${port}:5000
        """.stripIndent( )

        when:

        def resultMapUp = DockerUtils.composeUp( composeFile.getAbsolutePath( ) )
        
        if ( !resultMapUp.success ) {
            throw new GradleException( 'An error occurred when running "docker compose up": ' + resultMapUp.message )
        }

        def resultWaitMapWait = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )

        if ( !resultWaitMapWait.success ) {
            if ( resultWaitMapWait.reason.equals( 'error' ) ) {
                throw new GradleException( 'An error occurred when running "docker compose up": ' + resultWaitMapWait.message )
            } else {
                throw new GradleException( 'A container failed when running "docker compose up".' )
            }
        }


        String registryCheckCommand = 'curl --fail http://localhost:' + port

        int count = 0
        boolean registryOk = false

        if ( GradleExecUtils.exec( registryCheckCommand ).exitValue == 0 ) {
            registryOk = true
        }

        while ( !registryOk && count < NUM_RETRIES ) {

            Thread.sleep( SLEEP_TIME_MILLIS )

            if ( GradleExecUtils.exec( registryCheckCommand ).exitValue == 0 ) {
                registryOk = true
            }

            count++

        }

        if ( !registryOk ) {
            throw new GradleException( 'Docker private registery container ' + registryName + ' is not responding.' )
        }


        def resultMap = DockerUtils.dockerPush( tagA1, false )
        boolean success = resultMap.success


        String registryImageCheckA1 = 'curl -X GET localhost:' + port + '/v2/' + tagNameBaseA + '/manifests/' + tagVersionA1
        boolean registryHasImageA1 = false

        if ( GradleExecUtils.exec( registryImageCheckA1 ).exitValue == 0 ) {
            registryHasImageA1 = true
        }


        then:
        resultMap instanceof Map
        success == true
        registryHasImageA1 == true


        cleanup:
        DockerUtils.composeDown( composeFile.getAbsolutePath( ) )

        String rmTagCommandA1 = 'docker rmi ' + tagA1
        GradleExecUtils.exec( rmTagCommandA1 )
    }

    def "dockerPush(String tag,boolean allTags=false) returns correctly with 'allTags' true"( ) {
        given:
        String testName = 'dockerpush-string-tagtrue-good'

        String registryImageRef = REGISTRY_IMAGE_REF
        String registryName = testName + UNIQUE_NAME_POSTFIX

        String port = 5000


        // A
        String tagNameBaseA = testName
        String tagNameWithRegistryA = 'localhost:' + port + '/' + tagNameBaseA
        // A - version 1.0
        String tagVersionA1 = '1.0'
        String tagA1 = tagNameWithRegistryA + ':' + tagVersionA1
        String tagCommandA1 = 'docker tag ' + REGISTRY_IMAGE_REF + ' ' + tagA1
        GradleExecUtils.execWithException( tagCommandA1 )
        // A - version 2.0
        String tagVersionA2 = '2.0'
        String tagA2 = tagNameWithRegistryA + ':' + tagVersionA2
        String tagCommandA2 = 'docker tag ' + REGISTRY_IMAGE_REF + ' ' + tagA2
        GradleExecUtils.execWithException( tagCommandA2 )



        Map<String,String> containerMap = new HashMap<String, String>( )
        containerMap.put( registryName, 'running' )

        composeFile << """
            services:
              ${registryName}:
                container_name: ${registryName}
                image: ${registryImageRef}
                ports:
                  - ${port}:5000
        """.stripIndent( )

        when:

        def resultMapUp = DockerUtils.composeUp( composeFile.getAbsolutePath( ) )
        
        if ( !resultMapUp.success ) {
            throw new GradleException( 'An error occurred when running "docker compose up": ' + resultMapUp.message )
        }

        def resultWaitMapWait = DockerUtils.waitForContainer( containerMap, SLEEP_TIME_SECONDS, NUM_RETRIES )

        if ( !resultWaitMapWait.success ) {
            if ( resultWaitMapWait.reason.equals( 'error' ) ) {
                throw new GradleException( 'An error occurred when running "docker compose up": ' + resultWaitMapWait.message )
            } else {
                throw new GradleException( 'A container failed when running "docker compose up".' )
            }
        }


        String registryCheckCommand = 'curl --fail http://localhost:' + port

        int count = 0
        boolean registryOk = false

        if ( GradleExecUtils.exec( registryCheckCommand ).exitValue == 0 ) {
            registryOk = true
        }

        while ( !registryOk && count < NUM_RETRIES ) {

            Thread.sleep( SLEEP_TIME_MILLIS )

            if ( GradleExecUtils.exec( registryCheckCommand ).exitValue == 0 ) {
                registryOk = true
            }

            count++

        }

        if ( !registryOk ) {
            throw new GradleException( 'Docker private registery container ' + registryName + ' is not responding.' )
        }


        def resultMap = DockerUtils.dockerPush( tagNameWithRegistryA, true )
        boolean success = resultMap.success


        String registryImageCheckA1 = 'curl -X GET localhost:' + port + '/v2/' + tagNameBaseA + '/manifests/' + tagVersionA1
        boolean registryHasImageA1 = false

        if ( GradleExecUtils.exec( registryImageCheckA1 ).exitValue == 0 ) {
            registryHasImageA1 = true
        }


        String registryImageCheckA2 = 'curl -X GET localhost:' + port + '/v2/' + tagNameBaseA + '/manifests/' + tagVersionA2
        boolean registryHasImageA2 = false

        if ( GradleExecUtils.exec( registryImageCheckA2 ).exitValue == 0 ) {
            registryHasImageA2 = true
        }


        then:
        resultMap instanceof Map
        success == true
        registryHasImageA1 == true
        registryHasImageA2 == true


        cleanup:
        DockerUtils.composeDown( composeFile.getAbsolutePath( ) )

        String rmTagCommandA1 = 'docker rmi ' + tagA1
        GradleExecUtils.exec( rmTagCommandA1 )

        String rmTagCommandA2 = 'docker rmi ' + tagA2
        GradleExecUtils.exec( rmTagCommandA2 )
    }

    def "dockerPush(String tag,boolean allTags=false) returns correctly when image can't be found"( ) {
        given:
        String testName = 'dockerpush-string-tagnone-nosuchimage'

        String port = 5000

        String tagNameBaseA = testName
        String tagNameWithRegistryA = 'localhost:' + port + '/' + tagNameBaseA
        String tagVersionA1 = '1.0'
        String tagA1 = tagNameWithRegistryA + ':' + tagVersionA1

        String tagCommandA1 = 'docker tag ' + REGISTRY_IMAGE_REF + ' ' + tagA1


        GradleExecUtils.execWithException( tagCommandA1 )

        when:
        def resultMap = DockerUtils.dockerPush( tagA1 )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == false

        cleanup:
        String rmTagCommandA1 = 'docker rmi ' + tagA1
        GradleExecUtils.exec( rmTagCommandA1 )
    }

    def "dockerPush(String tag,boolean allTags=false) returns correctly when registry not found"( ) {
        given:
        String testName = 'dockerpush-string-tagnone-badregistry'

        String port = 5001

        String tagNameBaseA = testName
        String tagNameWithRegistryA = 'localhost:' + port + '/' + tagNameBaseA
        String tagVersionA1 = '1.0'
        String tagA1 = tagNameWithRegistryA + ':' + tagVersionA1

        String tagCommandA1 = 'docker tag ' + REGISTRY_IMAGE_REF + ' ' + tagA1


        GradleExecUtils.execWithException( tagCommandA1 )

        when:
        def resultMap = DockerUtils.dockerPush( tagA1 )
        boolean success = resultMap.success

        then:
        resultMap instanceof Map
        success == false

        cleanup:
        String rmTagCommandA1 = 'docker rmi ' + tagA1
        GradleExecUtils.exec( rmTagCommandA1 )
    }


    //todo tests for the array version of dockerPush()

}
