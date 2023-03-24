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

import spock.lang.Specification


/**
 * Unit tests.
 *
 */
class DockerUtilsTest extends Specification {

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
        // using an image that can pulled from Docker Hub
        String containerImageRef = 'alpine:latest'
        String containerName = 'my-alpine'

        when:
        String[] dockerRunCommand = [ 'docker', 'run', '--rm', '--name', containerName, '-d', 'alpine:latest', 'tail', '-f' ]
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
