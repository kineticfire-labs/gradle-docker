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


import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction


// bash -c docker save ${registry.image.ref} | gzip > registry.gz

/**
 * 
 */
abstract class DockerSaveTask extends DefaultTask {

    @TaskAction
    def dockerSave( ) {

        String imagePointer // can be an 'image reference' or 'image ID'

        if ( project.docker.imageReference != null ) {
            imagePointer = project.docker.imageReference
        } else if ( project.docker.imageID != null ) {
            imagePointer = project.docker.imageID
        } else {
            throw StopExecutionException( "No target image defined for 'dockerSave' task." )
        }

        /*
        String imageRef = project.docker.imageReference
        println "Hi from DockerSaveTask " + imageRef
        */
        println "Hi from DockerSaveTask hiya"

        //println GradleExecUtils.exec( 'ls' ).get( 'out' )
        //println GradleExecUtils.exec( 'pwd' ).get( 'out' )
        //println SystemUtils.getUserName( )
        //println SystemUtils.getUid( )
        //println SystemUtils.getUid( SystemUtils.getUserName( ) )

    }

}