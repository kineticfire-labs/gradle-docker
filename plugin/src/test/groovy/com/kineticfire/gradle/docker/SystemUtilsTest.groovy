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


import java.nio.file.Path
import static java.util.concurrent.TimeUnit.MINUTES

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Timeout


/**
 * Unit tests.
 *
 */
// Set timeout for all feature methods.  Probably longer than is needed for a test.
@Timeout( value = 5, unit = MINUTES )
class SystemExecUtilsTest extends Specification {

    static final String SCRIPT_FILE_NAME  = 'test.sh'


    @TempDir
    Path tempDir

    File scriptFile


    def setup( ) {
        scriptFile = new File( tempDir.toString( ) + File.separatorChar + SCRIPT_FILE_NAME )
    }




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

    def "getUid(String username) returns correct UID for valid user"( ) {
        given:
        String username = System.properties[ 'user.name' ]
        String uidString = [ 'id', '-u', username ].execute( ).text.trim( )
        int uidExpected = Integer.parseInt( uidString )

        when:
        int uidResult = SystemUtils.getUid( username )

        then:
        uidExpected == uidResult
    }

    def "getUid(String username) returns correct UID for invalid username"( ) {
        given:
        String username = 'xxxxxxxx'

        when:
        int uidResult = SystemUtils.getUid( username )

        then:
        uidResult == -1
    }

    def "validateScript(String scriptPath) returns correctly"( ) {
        given:

        // must put the shebang on the first line
        scriptFile <<
            """#! /bin/bash

            if [ "\$1" == "deploy" ]; then
                echo "********** deploy:"

                if docker stack deploy -c docker-compose.yml -c docker-compose-override-production.yml support-production

                then
                  echo "Success deploying the stack."
                  exit 0
                else
                  echo "Failed deploying the stack" >&2
                  exit 1
                fi

                elif [ "\$1" == "remove" ]; then
                    echo "********** remove:"
                    sudo docker stack rm support-production
                elif [ "\$1" == "status" ]; then
                    echo "********** All stacks:"
                    sudo docker stack ls
                    echo "********** All networks:"
                    sudo docker network ls
                    echo "********** ps support-production:"
                    sudo docker ps | grep support-production
                    echo "********** stack ps support-production:"
                    sudo docker stack ps support-production
                elif [ "\$1" == "images" ]; then
                    echo "********** images:"
                    curl -X GET http://localhost:5000/v2/_catalog | grep support
                elif [ "\$1" == "tags" ]; then
                    echo "********** tags:"
                    curl -X GET http://localhost:5000/v2/kf/devops/support/git/tags/list | grep support
                else
                    echo "not recognized:  \$1"
                fi

                exit 0
        """.stripIndent( )

        when:

        def resultMap = SystemUtils.validateScript( scriptFile.getAbsolutePath( ) )

        then:
        true == resultMap.ok
        0 == resultMap.exitValue
        '' == resultMap.out
        null == resultMap.err
    }

    def "validateScript(String scriptPath) returns correctly with a script error"( ) {
        given:

        // must put the shebang on the first line
        scriptFile <<
            """#! /bin/bash

            if [ "\$1" == "deploy" ]; then
                echo "********** deploy:"

                if docker stack deploy -c docker-compose.yml -c docker-compose-override-production.yml support-production

                then
                  echo "Success deploying the stack."
                  exit 0
                else
                  echo "Failed deploying the stack" >&2
                  exit 1
                # inserted error here by commenting on this line:  fi

                elif [ "\$1" == "remove" ]; then
                    echo "********** remove:"
                    sudo docker stack rm support-production
                elif [ "\$1" == "status" ]; then
                    echo "********** All stacks:"
                    sudo docker stack ls
                    echo "********** All networks:"
                    sudo docker network ls
                    echo "********** ps support-production:"
                    sudo docker ps | grep support-production
                    echo "********** stack ps support-production:"
                    sudo docker stack ps support-production
                elif [ "\$1" == "images" ]; then
                    echo "********** images:"
                    curl -X GET http://localhost:5000/v2/_catalog | grep support
                elif [ "\$1" == "tags" ]; then
                    echo "********** tags:"
                    curl -X GET http://localhost:5000/v2/kf/devops/support/git/tags/list | grep support
                else
                    echo "not recognized:  \$1"
                fi

                exit 0
        """.stripIndent( )

        when:

        def resultMap = SystemUtils.validateScript( scriptFile.getAbsolutePath( ) )

        then:
        false == resultMap.ok
        1 == resultMap.exitValue
        resultMap.out.contains( "Couldn't find 'fi' for this 'if'" )
        '' == resultMap.err
    }

}
