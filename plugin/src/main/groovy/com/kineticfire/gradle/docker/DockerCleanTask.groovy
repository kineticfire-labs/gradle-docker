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


import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.StopExecutionException



/**
 *
 */
abstract class DockerCleanTask extends Delete {

    @TaskAction
    def dockerClean( ) {

        //todo
        println "Hi from DockerCleanTask"

    }

}
