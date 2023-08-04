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
import org.gradle.api.tasks.StopExecutionException

import java.util.Map



/**
 * Saves the Docker image as a .tar.gz file.
 *
 */
abstract class DockerSaveTask extends DefaultTask {

    @TaskAction
    def dockerSave( ) {

        String saveImageName = project.docker.saveImageName
        String saveImageFilename = project.docker.saveImageFilename

        /* todo: commented out to check functional test

        if ( project.docker.saveImageName == null || project.docker.saveImageName.equals( '' ) ) {
            throw new StopExecutionException( "No target image 'saveImageName' defined for 'dockerSave' task." )
        }

        if ( project.docker.saveImageFilename == null || project.docker.saveImageFilename.equals( '' ) ) {
            throw new StopExecutionException( "No output filename 'saveImageFilename' defined for 'dockerSave' task." )
        }


        //todo query if image exists first?


        Map<String, String> resultMap = DockerUtils.dockerSave( saveImageName, saveImageFilename )

        if ( resultMap.success ) {
            throw new StopExecutionException( "Failed to save image '" + saveImageName + "' to file '" + saveImageFilename + "' for 'dockerSave' task.  " + resultMap.get( 'err' ) )
        }

        */


        //todo
        println "DockerSaveTask dockerBuildDir: " + project.docker.dockerBuildDir
        println "DockerSaveTask dockerfile: " + project.docker.dockerfile
        println "Hi from DockerSaveTask"

    }

}
