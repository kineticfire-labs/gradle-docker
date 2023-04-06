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
class SystemExecUtilsTest extends Specification {

    def "getUserName() returns correct user"( ) {
        given:
        String usernameExpected = System.properties[ 'user.name' ]

        when:
        String usernameResult = SystemUtils.getUserName( )

        then:
        usernameExpected.equals( usernameResult )
    }

    def "getUid() returns correct UID"( ) {
        given:
        String username = System.properties[ 'user.name' ]
        String uidString = [ 'id', '-u', username ].execute( ).text.trim( )
        int uidExpected = Integer.parseInt( uidString )

        when:
        int uidResult = SystemUtils.getUid( )

        then:
        uidExpected == uidResult
    }

    def "getUid(String username) returns correct UID"( ) {
        given:
        String username = System.properties[ 'user.name' ]
        String uidString = [ 'id', '-u', username ].execute( ).text.trim( )
        int uidExpected = Integer.parseInt( uidString )

        when:
        int uidResult = SystemUtils.getUid( username )

        then:
        uidExpected == uidResult
    }

}