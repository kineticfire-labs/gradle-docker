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

import java.util.Map
import java.io.IOException


/**
 * Unit tests.
 */
class GradleExecUtilsTest extends Specification {

    def "execWithException(String task) for successful command returns output"( ) {
        given:
        String task = 'whoami'
        String usernameExpected = System.properties[ 'user.name' ]

        when:
        String result = GradleExecUtils.execWithException( task )

        then:
        usernameExpected.equals( result )
    }

    def "execWithException(String task) for failed command throws correct exception"( ) {
        given:
        String task = 'whoami x'

        when:
        String result = GradleExecUtils.execWithException( task )

        then:
        thrown( IOException )
    }

    def "exec(String task) for successful command returns correct exit value and output"( ) {
        given:
        String task = 'whoami'
        String usernameExpected = System.properties[ 'user.name' ]

        when:
        Map<String, String> result = GradleExecUtils.exec( task )

        then:
        0 == result.get( 'exitValue' )
        usernameExpected.equals( result.get( 'out' ) )
        null == result.get( 'err' )
    }

    def "exec(String task) for failed command returns correct exit value and error output"( ) {
        given:
        String task = 'whoami x'
        String errResponseExpected = 'extra operand'

        when:
        Map<String, String> result = GradleExecUtils.exec( task )

        then:
        1 == result.get( 'exitValue' )
        "".equals( result.get( 'out' ) )
        result.get( 'err' ).contains( errResponseExpected )
    }

    def "execWithException(String[] task) for successful command returns correct output"( ) {
        given:
        String[] task = [ 'whoami', '--help' ]
        String responseExpected = 'Usage: whoami'

        when:
        String result = GradleExecUtils.execWithException( task )

        then:
        result.contains( responseExpected )
    }

    def "execWithException(String[] task) for failed command throws correct exception"( ) {
        given:
        String[] task = [ 'whoami', '--help', 'x' ]

        when:
        String result = GradleExecUtils.execWithException( task )

        then:
        thrown( IOException )
    }

    def "exec(String[] task) for successful command returns correct exit value and output"( ) {
        given:
        String[] task = [ 'whoami', '--help' ]
        String responseExpected = 'Usage: whoami'

        when:
        Map<String, String> result = GradleExecUtils.exec( task )

        then:
        0 == result.get( 'exitValue' )
        result.get( 'out' ).contains( responseExpected )
        null == result.get( 'err' )
    }

    def "exec(String[] task) for failed command returns correct exit value and error output"( ) {
        given:
        String[] task = [ 'whoami', '--help', 'x' ]
        String errResponseExpected = 'whoami: unrecognized option'

        when:
        Map<String, String> result = GradleExecUtils.exec( task )

        then:
        1 == result.get( 'exitValue' )
        "".equals( result.get( 'out' ) )
        result.get( 'err' ).contains( errResponseExpected )
    }

}
