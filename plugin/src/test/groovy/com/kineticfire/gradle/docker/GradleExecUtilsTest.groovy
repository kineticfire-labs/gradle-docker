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
        def resultMap = GradleExecUtils.exec( task )

        then:
        resultMap instanceof Map
        true == resultMap.success
        0 == resultMap.get( 'exitValue' )
        usernameExpected.equals( resultMap.get( 'out' ) )
        null == resultMap.get( 'err' )
    }

    def "exec(String task) for failed command returns correct exit value and error output"( ) {
        given:
        String task = 'whoami x'
        String errResponseExpected = 'extra operand'

        when:
        def resultMap = GradleExecUtils.exec( task )

        then:
        resultMap instanceof Map
        false == resultMap.success
        1 == resultMap.get( 'exitValue' )
        ''.equals( resultMap.get( 'out' ) )
        resultMap.get( 'err' ).contains( errResponseExpected )
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
        def resultMap = GradleExecUtils.exec( task )

        then:
        resultMap instanceof Map
        true == resultMap.success
        0 == resultMap.get( 'exitValue' )
        resultMap.get( 'out' ).contains( responseExpected )
        null == resultMap.get( 'err' )
    }

    def "exec(String[] task) for failed command returns correct exit value and error output"( ) {
        given:
        String[] task = [ 'whoami', '--help', 'x' ]
        String errResponseExpected = 'whoami: unrecognized option'

        when:
        def resultMap = GradleExecUtils.exec( task )

        then:
        resultMap instanceof Map
        false == resultMap.success
        1 == resultMap.get( 'exitValue' )
        "".equals( resultMap.get( 'out' ) )
        resultMap.get( 'err' ).contains( errResponseExpected )
    }

}
