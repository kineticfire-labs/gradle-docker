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

import java.util.Map



/**
 * 
 */
abstract class DockerSaveTask extends DefaultTask {

    @TaskAction
    def dockerSave( ) {

        String saveImageName = project.docker.saveImageName
        String saveImageFilename = project.docker.saveImageFilename

        if ( project.docker.saveImageName != null ) {
            throw StopExecutionException( "No target image 'saveImageName' defined for 'dockerSave' task." )
        }

        if ( project.docker.saveImageFilename != null ) {
            throw StopExecutionException( "No output filename 'saveImageFilename' defined for 'dockerSave' task." )
        }


        // bash -c docker save ${registry.image.ref} | gzip > registry.gz
        String task[] = [ 'bash', '-c', 'docker', 'save', saveImageName, '|', 'gzip', '>', saveImageFilename ]

        Map<String, String> result = GradleExecUtils.exec( task )

        if ( result.get( 'exitValue' ) != '0' ) {
            throw StopExecutionException( "Failed to save image '" + saveImageName + "' to file '" + saveImageFilename + "' for 'dockerSave' task.  " + result.get( 'err' ) )
        }

    }

}
