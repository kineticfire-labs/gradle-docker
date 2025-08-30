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


import java.util.Map
import static java.util.concurrent.TimeUnit.MINUTES
import java.io.IOException

import spock.lang.Specification
import spock.lang.Timeout


/**
 * Unit tests.
 */
// Set timeout for all feature methods.  Probably longer than is needed for a test.
@Timeout( value = 5, unit = MINUTES )
class ExecUtilsTest extends Specification {

    def "execWithException(String task) for successful command returns output"( ) {
        given: "command to execute to get the current username"
        String task = 'whoami'

        when: "execute the command"
        String result = ExecUtils.execWithException( task )

        then: "return the current username"
        String usernameExpected = System.properties[ 'user.name' ]
        usernameExpected.equals( result )
    }

    def "execWithException(String task) for failed command throws correct exception"( ) {
        given: "an invalid command to execute"
        String task = 'whoami x'

        when: "execute the command"
        String result = ExecUtils.execWithException( task )

        then: "throw an exception"
        thrown( IOException )
    }

    def "exec(String task) for successful command returns correct exit value and output"( ) {
        given: "command to execute to get the current username"
        String task = 'whoami'

        when: "execute the command"
        def resultMap = ExecUtils.exec( task )

        then: "return a Map"
        resultMap instanceof Map

        and: "map key 'success' is true"
        true == resultMap.success

        and: "map key 'exitValue' is 0"
        0 == resultMap.get( 'exitValue' )

        and: "returns the correct username from executing the command in map key 'out'"
        String usernameExpected = System.properties[ 'user.name' ]
        usernameExpected.equals( resultMap.get( 'out' ) )

        and: "map key 'err' is not present e.g null"
        null == resultMap.get( 'err' )
    }

    def "exec(String task) for failed command returns correct exit value and error output"( ) {
        given: "an invalid command to execute"
        String task = 'whoami x'

        when: "execute the command"
        def resultMap = ExecUtils.exec( task )

        then: "return a map"
        resultMap instanceof Map

        and: "map key 'success' is false"
        false == resultMap.success

        and: "map key 'exitValue' is 1"
        1 == resultMap.get( 'exitValue' )

        and: "map key 'out' is an empty String"
        ''.equals( resultMap.get( 'out' ) )

        and: "map key 'err' contains an explanation of the failure"
        String errResponseExpected = 'extra operand'
        resultMap.get( 'err' ).contains( errResponseExpected )
    }

    def "execWithException(String[] task) for successful command returns correct output"( ) {
        given: "command to execute"
        String[] task = [ 'whoami', '--help' ]

        when: "execute the command"
        String result = ExecUtils.execWithException( task )

        then: "return the result of the command"
        String responseExpected = 'Usage: whoami'
        result.contains( responseExpected )
    }

    def "execWithException(String[] task) for failed command throws correct exception"( ) {
        given: "invalid command to execute"
        String[] task = [ 'whoami', '--help', 'x' ]

        when: "execute the command"
        String result = ExecUtils.execWithException( task )

        then: "throw an exception"
        thrown( IOException )
    }

    def "exec(String[] task) for successful command returns correct exit value and output"( ) {
        given: "command to execute"
        String[] task = [ 'whoami', '--help' ]

        when: "execute the command"
        def resultMap = ExecUtils.exec( task )

        then: "return a map"
        resultMap instanceof Map

        and: "map key 'success' is true"
        true == resultMap.success

        and: "map key 'exiteValue' is 0"
        0 == resultMap.get( 'exitValue' )

        and: "map key 'out' contains the result of the executed command"
        String responseExpected = 'Usage: whoami'
        resultMap.get( 'out' ).contains( responseExpected )

        and: "map key 'err' is not set e.g. null"
        null == resultMap.get( 'err' )
    }

    def "exec(String[] task) for failed command returns correct exit value and error output"( ) {
        given: "an invalid command to execute"
        String[] task = [ 'whoami', '--help', 'x' ]

        when: "execute the command"
        def resultMap = ExecUtils.exec( task )

        then: "return a map"
        resultMap instanceof Map

        and: "map key 'success' is false"
        false == resultMap.success

        and: "map key 'exiteValue' is 1"
        1 == resultMap.get( 'exitValue' )

        and: "map key 'out' is an empty String"
        "".equals( resultMap.get( 'out' ) )

        and: "map key 'err' contains an explanation of the error"
        String errResponseExpected = 'whoami: unrecognized option'
        resultMap.get( 'err' ).contains( errResponseExpected )
    }

}
