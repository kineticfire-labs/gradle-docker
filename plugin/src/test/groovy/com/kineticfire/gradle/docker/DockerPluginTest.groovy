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


import static java.util.concurrent.TimeUnit.MINUTES

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project

import spock.lang.Specification
import spock.lang.Timeout


/**
 * unit test
 */
// Set timeout for all feature methods.  Probably longer than is needed for a test.
@Timeout( value = 5, unit = MINUTES )
class DockerPluginTest extends Specification {

    def "plugin registers task"( ) {
        given:
        def project = ProjectBuilder.builder( ).build( )

        when:
        project.plugins.apply( 'com.kineticfire.docker' )

        then:
        project.tasks.findByName( 'docker-save-task' ) != null
    }

}
