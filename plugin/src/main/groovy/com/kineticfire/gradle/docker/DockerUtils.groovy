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
import java.util.HashMap
import java.util.Set
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.io.FileOutputStream



/**
 * Provides Docker utilities.
 *
 */
final class DockerUtils {


   /**
    * Returns a Map indicating the state of the container 'container'.
    * <p>
    * The 'container' argument can be the container ID or the container name.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>state -- the state of the container as a String</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'state' is 'error'</li>
    * </ul>
    * <p>
    * The 'state' entry of the returned Map will have one of the following String values.  The first four are Docker-defined container states.
    * <ul>
    *    <li>created</li>
    *    <li>restarting</li>
    *    <li>running</li>
    *    <li>paused</li>
    *    <li>exited</li>
    *    <li>dead</li>
    *    <li>not-found -- the container wasn't found</li>
    *    <li>error -- an error occurred when executing the command</li>
    * </ul>
    *
    * @param container
    *    the container to query
    * @return a Map containing the current state of the container
    */
   static def getContainerState( String container ) {

      def responseMap = [:]


      String command = 'docker inspect --format {{.State.Status}} ' + container

      def queryMap = ExecUtils.exec( command )


      if ( queryMap.exitValue == 0 ) {
         responseMap.state = queryMap.out.toLowerCase( )
      } else if ( queryMap.err.contains( 'No such object' ) ) {
         responseMap.state = 'not-found'
      } else {
         responseMap.state = 'error'
         responseMap.reason = queryMap.err
      }

      return( responseMap )

   }


   /**
    * Returns a Map indicating the health of the container 'container'.
    * <p>
    * The 'container' argument can be the container ID or the container name.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>health -- the health of the container</li>
    *    <li>reason -- reason why the command failed; only present if 'health' is 'error'</li>
    * </ul>
    * <p>
    * The 'health' entry of the returned Map will have one of the following values.  The first three are Docker-defined health statuses.
    * <ul>
    *    <li>healthy -- the container is healthy</li>
    *    <li>unhealthy -- container could be in the 'running' or 'exited' states; an exited (e.g., stopped container) is always 'unhealthy'</li>
    *    <li>starting -- the health check hasn't returned yet</li>
    *    <li>no-healthcheck -- the container has no healthcheck</li>
    *    <li>not-found -- the container wasn't found</li>
    *    <li>error -- an error occurred when executing the command</li>
    * </ul>
    * <p>
    * The 'reason' entry of the returned Map consists of the following entries:
    * <ul>
    *    <li>if 'health' is 'error'</li>
    *    <ul>
    *       <li>the error output from the command</li>
    *    </ul>
    * </ul>
    *
    * @param container
    *    the container to query
    * @return a Map containing the current health of the container
    */
   static def getContainerHealth( String container ) {

      def responseMap = [:]

      String command = 'docker inspect --format {{.State.Health.Status}} ' + container

      def queryMap = ExecUtils.exec( command )


      if ( queryMap.get( 'exitValue' ) == 0 ) {
         responseMap.health = queryMap.out.toLowerCase( )
      } else if ( queryMap.err.contains( 'map has no entry for key "Health"' ) ) {
         responseMap.health = 'no-healthcheck'
      } else if ( queryMap.err.contains( 'No such object' ) ) {
         responseMap.health = 'not-found'
      } else {
         responseMap.health = 'error'
         responseMap.reason = queryMap.err
      }

      return( responseMap )

   }


   /**
    * Waits for the container 'container' to reach the desired 'target' state or health status.
    * <p>
    * Supports a defined target state of 'running' or a health status of 'healthy'.
    * <p>
    * Retries up to 22 times, waiting two seconds between attempts, for a total of 44 seconds.
    * <p>
    * This is a convenience method for waitForContainer( Map&lt;String,String&gt;, int retrySeconds, int retryNum ) where 'container' and 'target' are added to a Map, 'retrySeconds' is '2', and 'retryNum' is '19'.  See that method for details.
    * 
    * @param container
    *    a container reference (ID or name) as a String to query
    * @param target
    *    the target state or health status for the container
    * @return a Map indicating if the container is in the 'running' state with additional information
    */
   static def waitForContainer( String container, String target ) {
      Map<String,String> containerMap = new HashMap<String,String>( )
      containerMap.put( container, target )
      return( waitForContainer( containerMap ) )
   }


   /**
    * Waits for the container 'container' to reach the desired 'target' state or health status.
    * <p>
    * Supports a defined target state of 'running' or a health status of 'healthy'.
    * <p>
    * This is a convenience method for waitForContainer( Map&lt;String,String&gt;, int retrySeconds, int retryNum ) where 'container' and 'target' are added to a Map.  See that method for details.
    * 
    * @param container
    *    a container reference (ID or name) as a String to query
    * @param target
    *    the target state or health status for the container
    * @param retrySeconds
    *    integer number of seconds to wait between retries
    * @param retryNum
    *    number of times to retry, waiting for the container to reach the desired state or health status until a failure is returned
    * @return a Map indicating if the container reached the desired state or health status with additional information
    *
    */
   static def waitForContainer( String container, String target, int retrySeconds, int retryNum ) {
      Map<String,String> containerMap = new HashMap<String,String>( )
      containerMap.put( container, target )
      return( waitForContainer( containerMap, retrySeconds, retryNum ) )
   }


   /**
    * Waits for a group of containers to reach desired target states or health status, as defined in the 'containerMap'.
    * <p>
    * Supports a defined target state of 'running' or a health status of 'healthy'.
    * <p>
    * Retries up to 22 times, waiting two seconds between attempts, for a total of 44 seconds.
    * <p>
    * This is a convenience method for waitForContainer( Map&lt;String,String&gt;, int retrySeconds, int retryNum ) where 'retrySeconds' is '2' and 'retryNum' is '19'.  See that method for details.
    * @param containerMap
    *    a Map containing one or more container references (IDs and/or names) as Strings mapped to its target state or health status as a String
    * @return a Map indicating if the container reached the desired state or health status with additional information
    */
   static def waitForContainer( Map<String,String> containerMap ) {
      return( waitForContainer( containerMap, 2, 22 ) )
   }


   /**
    * Waits for a group of containers to reach desired target states or health status, as defined in the 'containerMap'.
    * <p>
    * Supports a defined target state of 'running' or a health status of 'healthy'.
    * <p>
    * Blocks while waiting for all containers in the 'containerMap' to reach their target states or health statuses, or until an error or non-recoverable state or health status is reached by at least one container or 'numRetries' is exceeded.  Performs first query for containers' states and health status when the method is called (no initial delay).  If all containers are not in their target states or health statuses, then will block while waiting 'retrySeconds' and retry up to 'retryNum' times.  Returns, possibly immediately or otherwise will block, until one of the following conditions is met:
    * <ul>
    *    <li>all containers are in their target states or health statuses</li>
    *    <li>at least one container is in an unrecoverable running state (e.g. 'restarting', 'paused', or 'exited') or 'unhealthy' health status</li>
    *    <li>at least one container is not in its target state or health status and the number of retries 'retryNum' has been hit</li>
    *    <li>a container couldn't be found and the number of retries 'retryNum' has been hit</li>
    *    <li>an error occurred</li>
    * </ul>
    * <p>
    * A Map is returned with the result of the method that contains the following entries:
    * <ul>
    *    <li>success -- a boolean that is 'true' if all containers in the 'containerMap' are in their target states or health statuses and 'false' otherwise</li>
    *    <li>reason -- a String indicating why all containers are not in their target states or health statuses; present only if 'success' is false</li>
    *    <li>message -- a String description for the 'reason'; present only if 'success' is false and 'status' is 'not-running' or 'not-found'</li>
    *    <li>container -- a String identifying the container described by the 'reason' and 'message' fields, which is the first container queried that was not in its target state or health status; present only if 'success' is false</li>
    * </ul>
    * <p>
    * Valid values for the 'reason' entry in the returned map are as Strings: 
    * <ul>
    *    <li>unhealthy -- if target is 'healthy' health status, then returns this value for the first container that is 'unhealthy'</li>
    *    <li>no-health-check -- if target is 'healthy' health status, then returns this value for the first container that does not have a health check</li>
    *    <li>failed -- if target is 'running' state, then returns this value if at least one container is in a non-running state that indicates a failure or unexpected state (e.g., 'restarting', 'paused', or 'exited')</li>
    *    <li>num-retries-exceeded -- the number of retries was exceeded and at least one container was not in its target state or health status and didn't qualify for a condition above e.g., 'unhealthy' or 'failed'</li>
    *    <li>error -- an unexpected error occurred</li>
    * </ul>
    * <p>
    * Valid values for the 'message' entry in the returned map as Strings are:
    * <ul>
    *    <li>If 'reason' is 'failed':</li>
    *       <ul>
    *          <li>{restarting, paused, exited} -- the container is in one of these states which indicates a failure or unexpected state</li>
    *       </ul>
    *    <li>If 'reason' is 'num-retries-exceeded':</li>
    *       <ul>
    *          <li>not-found -- the container could not be found</li>
    *          <li>and if 'target' is 'healthy':</li>
    *             <ul>
    *                <li>starting -- the health check hasn't returned</li>
    *             </ul>
    *          <li>and if 'target' is 'running':</li>
    *             <ul>
    *                <li>created</li>
    *             </ul>
    *       </ul>
    *    <li>If 'reason' is 'error':</li>
    *       <ul>
    *          <li>illegal-target-disposition -- the target state or health status is not valid</li>
    *          <li>else, the error output from the command</li>
    *       </ul>
    * </ul>
    *
    * @param containerMap
    *    a Map containing one or more container references (IDs and/or names) as Strings mapped to its target state or health status as a String
    * @param retrySeconds
    *    integer number of seconds to wait between retries
    * @param retryNum
    *    number of times to retry, waiting for all containers to reach their target states or health status or until a failure is returned
    * @return a Map indicating if all containers achieved their targets states or health status with additional information
    */
   static def waitForContainer( Map<String,String> containerMap, int retrySeconds, int retryNum ) {

      def resultMap = [:]
      resultMap.success = false

      boolean done= false
      int count = 0 // counting up to 'retryNum' times

      // loop local variables
      boolean proceed // no failures, so current container has no failures or indeterminate result and no errors
      String currentContainer
      String targetContainerDisposition  // state or health status
      String currentContainerDisposition // state or health status
      Set<String> keySet
      def queryResultMap
      def tempResultMap = [:]  // populates data for a failure case to retry later, in the event 'retryNum' exceeded

      while ( !done ) {

         proceed = true

         keySet = containerMap.keySet( )

         Iterator it = keySet.iterator( )

         while ( proceed && it.hasNext( ) ) {

            proceed = false

            currentContainer = it.next( )

            targetContainerDisposition = containerMap.get( currentContainer )

            if ( targetContainerDisposition.equals( 'healthy' ) ) {

               // returns 'health' entry with one of {healthy, unhealthy, starting, none, error}
               queryResultMap = getContainerHealth( currentContainer )

               currentContainerDisposition = queryResultMap.get( 'health' )

               if ( currentContainerDisposition.equalsIgnoreCase( 'healthy' ) ) {
                  proceed = true
               } else if ( currentContainerDisposition.equalsIgnoreCase( 'unhealthy' ) ) {

                  done = true
                  // implicit proceed = false

                  resultMap.reason = 'unhealthy'
                  resultMap.container = currentContainer

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'no-healthcheck' ) ) {

                  done = true
                  // implicit proceed = false

                  resultMap.reason = 'no-health-check'
                  resultMap.container = currentContainer

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'error' ) ) {

                  done = true
                  // implicit proceed = false

                  resultMap.reason = 'error'
                  resultMap.message = queryResultMap.reason
                  resultMap.container = currentContainer

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'starting' ) ) {
                  tempResultMap.message = 'starting'
                  tempResultMap.container = currentContainer
               } else if ( currentContainerDisposition.equalsIgnoreCase( 'not-found' ) ) {
                  tempResultMap.message = 'not-found'
                  tempResultMap.container = currentContainer
               }

            } else if ( targetContainerDisposition.equals( 'running' ) ) {

               // returns 'state' entry with one of {created, restarting, running, paused, exited, dead, not-found, error}
               queryResultMap = getContainerState( currentContainer )

               currentContainerDisposition = queryResultMap.get( 'state' )

               if ( currentContainerDisposition.equalsIgnoreCase( 'running' ) ) {
                  proceed = true
               } else if (
                          currentContainerDisposition.equalsIgnoreCase( 'restarting' )   ||
                          currentContainerDisposition.equalsIgnoreCase( 'paused' )   ||
                          currentContainerDisposition.equalsIgnoreCase( 'dead' )     ||
                          currentContainerDisposition.equalsIgnoreCase( 'exited' ) ) {

                  done = true
                  // implicit proceed = false

                  resultMap.reason = 'failed'
                  resultMap.message = currentContainerDisposition
                  resultMap.container = currentContainer

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'error' ) ) {

                  done = true
                  // implicit proceed = false

                  resultMap.reason = 'error'
                  resultMap.message = queryResultMap.reason
                  resultMap.container = currentContainer

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'created' ) ) {
                  tempResultMap.message = 'created'
                  tempResultMap.container = currentContainer
               } else if ( currentContainerDisposition.equalsIgnoreCase( 'not-found' ) ) {
                  tempResultMap.message = 'not-found'
                  tempResultMap.container = currentContainer
               }

            } else {

               done = true;
               // implicit proceed = false

               resultMap.reason = 'error'
               resultMap.container = currentContainer
               resultMap.message = 'illegal-target-disposition'

            }

         } // end while loop



         if ( !done && !it.hasNext( ) && proceed ) {
            done = true
            resultMap.success = true
         } else if ( done ) {
            // catches failure cases and data provided above, preventing the next 'else' with 'num retries' from over-writing
         } else {

            count++

            if ( count == retryNum + 1 ) {
               done = true
               resultMap.reason = 'num-retries-exceeded'
               resultMap.message = tempResultMap.message
               resultMap.container = tempResultMap.container
            } else {
               Thread.sleep( retrySeconds * 1000 )
            }

         }


      } // end while loop

      return( resultMap )
   }


   /**
    * Returns a String array, suitable for executing, describing a "docker compose up" command with one or more compose files 'composeFilePaths'.
    * <p>
    * The command will include the '-d' option for running the docker compose "up" command in daemon mode.
    * <p>
    * Multiple compose files may be used when separating common directives into one file and then environment-specific directives into one or more additional files.
    * <p>
    * Examples for calling this method include:
    * <ul>
    *    <li>getComposeUpCommand( myComposeFilePath )</li>
    *    <li>getComposeUpCommand( myComposeFilePath1, myComposeFilePath2 )</li>
    *    <li>getComposeUpCommand( [myComposeFilePath1, myComposeFilePath2] as String[] )</li>
    * </ul>
    * <p>
    * This method is purposely separate from 'composeUp(...)' because a build process may need to dynamically create such a command to generate an appropriate bash script etc.
    * <p>
    * @param composeFilePaths
    *    one or more paths to Docker compose files
    * @return a String array, suitable for executing, describing a "docker compose up" command using the compose file path or paths
    */
   static String[] getComposeUpCommand( java.lang.String... composeFilePaths ) {

      int totalSize = 4 + ( composeFilePaths.length * 2 )

      String[] composeUpCommand = new String[totalSize]

      composeUpCommand[0] = 'docker'
      composeUpCommand[1] = 'compose'

      int index
      int i
      for ( i = 0; i < composeFilePaths.length; i++ ) {
         index = ( i * 2 ) + 2
         composeUpCommand[index] = '-f'
         composeUpCommand[index+1] = composeFilePaths[i]
      }

      index = ( i * 2 ) + 2
      composeUpCommand[index] = 'up'
      composeUpCommand[index+1] = '-d'


      return( composeUpCommand )
   }


   /**
    * Returns a String array, suitable for executing, describing a "docker compose down" command with the compose file 'composeFilePath'.
    * <p>
    * If using multiple compose files combined in the "up" command, the common compose file describing all services must be used for this method.
    * <p>
    * This method is purposely separate from 'composeDown(...)' because a build process may need to dynamically create such a command to generate an appropriate bash script etc.
    *
    * @param composeFilePath
    *    the path to the Docker compose file
    * @return a String array, suitable for executing, describing a "docker compose down" command using a compose file
    */
   static String[] getComposeDownCommand( String composeFilePath ) {
      String[] composeDownCommand = [ 'docker', 'compose', '-f', composeFilePath, 'down' ]
      return( composeDownCommand )
   }


   /**
    * Validates one or more files specified in 'composeFilePaths'.
    * <p>
    * Validation based on on "docker compose config" command.
    * <p>
    * Note that this method copies the source compose file(s) to be evaluated to temporary compose file(s) and performs a replacement of environment variables in the temporary file(s) that that any environment variable is set to the String "2" which can evaluate to integer two.  This process resolves an issue with the "docker compose config" command that environment variables for ports must resolve to integers.  See https://github.com/docker/compose/issues/7964 for more details.  Environment variable replacement recognizes environment variables in the format of ${NAME} where "NAME" can consist of uppercase and lowercase characters, digits zero through nine, hyphen, and underscore.
    * <p>
    * Multiple compose files may be used when separating common directives into one file and then environment-specific directives into one or more additional files.
    * <p>
    * Examples for calling this method include:
    * <ul>
    *    <li>validateComposeFile( myComposeFilePath )</li>
    *    <li>validateComposeFile( myComposeFilePath1, myComposeFilePath2 )</li>
    *    <li>validateComposeFile( [myComposeFilePath1, myComposeFilePath2] as String[] )</li>
    * </ul>
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param composeFilePaths
    *    one or more compose files with paths to validate
    * @return a Map containing the result of the command
    */
   static def validateComposeFile( java.lang.String... composeFilePaths ) {

      // Should separate into two functions:  one to create the temp compose files and regex them, and one to perform the "docker compose config" command.  Also should clean-up the variables names to be less confusing.  Will defer addressing these until the "docker compose config" command issue with environment variables for ports needing to resolve to an integer is fixed, which will obviate the need for function to to genereate temp compose files and regex.


      def responseMap = [:]



      int numComposeFilePaths = composeFilePaths.length

      String[] tempComposeFiles = new String[numComposeFilePaths]


      String currentComposeFilePath
      File tempComposeFile
      String composeText
      String newComposeText

      for ( int i = 0; i < numComposeFilePaths; i++ ) {

         currentComposeFilePath = composeFilePaths[i]

         try {

            // read text of temp compose file into memory, regex, and write back to file.  Not most efficient if file size is large, but presume file size to be relatively small to memory.

            // create empty temp compose file
            tempComposeFile = java.io.File.createTempFile( 'compose' + i + '-', '.yml', null )

            // read contents of source compose file into memory
            composeText = new File( currentComposeFilePath ).text

            // perform regex.  replace with String "2" that be converted into int to overcome "docker compose config" issue with ports need to resolve to int; see documentation for details of issue.
            newComposeText = composeText.replaceAll( /\$\{[a-zA-Z0-9_-]+\}/, "2" )

            // write contents to temp compose file
            tempComposeFile.write( newComposeText )

            // put the String path of the temp compose file into an array to use later
            tempComposeFiles[i] = tempComposeFile.getAbsolutePath( )

         } catch ( Exception e ) {
            responseMap.success = false
            responseMap.reason = 'An exception occurred.  ' + e.getMessage( )
         }


      }



      int totalSize = 5 + (numComposeFilePaths * 2 )

      String[] configCommand = new String[totalSize]

      configCommand[0] = 'docker'
      configCommand[1] = 'compose'

      int index
      int j
      for ( j = 0; j < tempComposeFiles.length; j++ ) {
         index = ( j * 2 ) + 2
         configCommand[index] = '-f'
         configCommand[index+1] = tempComposeFiles[j]
      }

      index = ( j * 2 ) + 2
      configCommand[index] = 'config'
      configCommand[index+1] = '--no-interpolate'
      configCommand[index+2] = '-q'


      def queryMap = ExecUtils.exec( configCommand )


      if ( queryMap.get( 'exitValue' ) == 0 ) {
         responseMap.put( 'success', true )
      } else {
         responseMap.put( 'success', false )
         responseMap.put( 'reason', queryMap.get( 'err' ) )
      }


      return( responseMap )

   }


   /**
    * Performs a "docker compose up" command using one or more compose file pathss 'composeFilePaths' and returns a Map indicating the result of the action.
    * <p>
    * The command will include the '-d' option for running the docker compose "up" command in daemon mode.
    * <p>
    * Multiple compose files may be used when separating common directives into one file and then environment-specific directives into one or more additional files.
    * <p>
    * Examples for calling this method include:
    * <ul>
    *    <li>getComposeUpCommand( myComposeFilePath )</li>
    *    <li>getComposeUpCommand( myComposeFilePath1, myComposeFilePath2 )</li>
    *    <li>getComposeUpCommand( [myComposeFilePath1, myComposeFilePath2] as String[] )</li>
    * </ul>
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param composeFilePaths
    *    one or more paths to Docker compose files to use
    * @return a Map containing the result of the command
    */
   static def composeUp( java.lang.String... composeFilePaths ) {

      String[] composeUpCommand = getComposeUpCommand( composeFilePaths as String[] )

      def queryMap = ExecUtils.exec( composeUpCommand )


      def responseMap = [:]

      if ( queryMap.get( 'exitValue' ) == 0 ) {
         responseMap.put( 'success', true )
      } else {
         responseMap.put( 'success', false )
         responseMap.put( 'reason', queryMap.get( 'err' ) )
      }

      return( responseMap )

   }


   /**
    * Performs a "docker compose down" command using the compose file path 'composeFilePath' and returns a Map indicating the result of the action.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- reason why the command failed, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param composeFilePath
    *    the path to the Docker compose file to use
    * @return a Map containing the result of the command
    */
   static def composeDown( String composeFilePath ) {

      String[] composeDownCommand = getComposeDownCommand( composeFilePath )

      def queryMap = ExecUtils.exec( composeDownCommand )


      def responseMap = [:]

      if ( queryMap.get( 'exitValue' ) == 0 ) {
         responseMap.put( 'success', true )
      } else {
         responseMap.put( 'success', false )
         responseMap.put( 'reason', queryMap.get( 'err' ) )
      }

      return( responseMap )

   }


   /**
    * Returns a String array, suitable for executing, describing a 'docker run' command for the image 'image' with command 'command'.
    * <p>
    * <p>
    * This method is a convenience method for getDockerRunCommand(String,Map&lt;String,String&gt;,command) when no options are needed.  The 'command' parameter can be a String or an array of Strings that describe the command.
    * <p>
    * For further details and complete documentation, see getDockerRunCommand(String,Map&lt;String,String&gt;,String[]).
    * <p>
    * This method is purposely separate from 'dockerRun(...)' because a build process may need to dynamically create such a command to generate an appropriate bash script etc.
    *
    * @param image
    *    the image to run
    * @param command
    *    command to include; optional
    * @return a String array, suitable for executing, describing a 'docker run' command
    * @throws ClassCastException if the command isn't of type String or String[]
    */
   static String[] getDockerRunCommand( String image, command = null ) {

      String[] commandArray

      if ( command == null ) {
         commandArray = null
      } else {
         if ( command instanceof String ) {
            commandArray = [command]
         } else if ( command instanceof String[] ) {
            commandArray = command
         } else {
            throw new ClassCastException( )
         }
      }

      return( getDockerRunCommand( image, null, commandArray ) )
   }


   /**
    * Returns a String array, suitable for executing, describing a 'docker run' command for the image 'image' with options 'options' and command 'command'.
    * <p>
    * The 'image' is the image name or ID of the image to run.
    * <p>
    * The 'options' is a Map of options, and is not required so can be null or an empty Map.  Options may have both key and value such as "--user" mapped to "&lt;user&gt;", or some options are one item, in which case leave the Map value as empty String or null such as "-t" mapped to "" or null.
    * <p>
    * The optional 'command' is a String or an array of one or more Strings to define the command (or commands) to be exec'd on the container.  Due to the way command line arguments are interpreted, it may be neccessary to break a command up into its components into a String array in order for it to be processed correctly.  Note that one approach is to exec the shell in one array element and then the desired command can be given in another array element (see example 1); multiple commands can be combined with ampersand (see example 2):
    * <ol>
    *    <li>["/bin/bash", "-c", "pwd"]</li>
    *    <li>["/bin/bash", "-c", "pwd &amp;&amp; ls"]</li>
    * </ol>
    * <p>
    * This method is purposely separate from 'dockerRun(...)' because a build process may need to dynamically create such a command to generate an appropriate bash script etc.
    *
    * @param image
    *    the image to run
    * @param options
    *    options to include; optional, can be null or empty Map
    * @param command
    *    command to includ; optional
    * @return a String array, suitable for executing, describing a 'docker run' command
    * @throws ClassCastException if the command isn't of type String or String[]
    */
   static String[] getDockerRunCommand( String image, Map<String,String> options, command = null ) {

      String[] commandArray

      if ( command == null ) {
         commandArray = []
      } else {
         if ( command instanceof String ) {
            commandArray = [command]
         } else if ( command instanceof String[] ) {
            commandArray = command
         } else {
            throw new ClassCastException( )
         }
      }



      int sizeFromOptions = 0

      if ( options != null ) {

         sizeFromOptions += options.size( ) // counts the keys

         for ( String value : options.values( ) ) {
            if ( value != null && !value.equals( '' ) ) {
               sizeFromOptions++ // counts values that are not null or empty String
            }
         }

      }


      // docker run [OPTIONS] IMAGE [COMMAND] [ARG...]
      // 'docker', 'run', '<options>', 'image', '<command>'
      // = 1 + 1 + sizeFromOptions + 1 + commandArray.length
      int totalSize = 3 + sizeFromOptions + commandArray.length

      String[] runCommand = new String[totalSize]
      runCommand[0] = 'docker'
      runCommand[1] = 'run'

      int index = 2

      if ( options != null ) {

         for ( Map.Entry<String, String> entry : options.entrySet( ) ) {

            runCommand[index] = entry.getKey( )
            index++

            if ( entry.getValue( ) != null && !entry.getValue( ).equals( '' ) ) {
               runCommand[index] = entry.getValue( )
               index++
            }

         }

      }

      runCommand[index] = image

      index++

      for ( String part : commandArray ) {
         runCommand[index] = part
         index++
      }


      return( runCommand )
   }


   /**
    * Runs the image 'image' with optional command 'command', and returns a Map with the result of the action.
    * <p>
    * The 'image' is the image to run as a container, with 'image' as a String image name or ID.
    * <p>
    * The 'command' is the command to run in the container as a String or an array of Strings.  The command is optional (need not be supplied or can be null).
    * <p>
    * This method is a convenience method for dockerRun(String,Map&lt;String,String&gt;,command) when no command is needed or the command to execute can be expressed as a single String and need not be written as an array of Strings.
    * <p>
    * For further details and complete documentation, see dockerRun(String,Map&lt;String,String&gt;,command).
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>out -- output from the command as a String, if any; only present if 'success' is true</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param image
    *    the image to run
    * @param command
    *    the command to run on the container; optional
    * @return a Map containing the result of the command
    * @throws ClassCastException if the command isn't of type String or String[]
    */
   static def dockerRun( String image, command = null ) {
      return( dockerRun( image, null, command ) )
   }


   /**
    * Runs the image 'image' with options 'options' and optional command 'command', and returns a Map with the result of the action.
    * <p>
    * The 'image' is the image to run as a container, with 'image' as a String image name or ID.
    * <p>
    * The 'options' is a Map of options, and is not required.  Options may have both key and value such as "--user" mapped to "&lt;user&gt;", or some options are one item, in which case leave the Map value as empty String or null such as "-d" mapped to "" or null.
    * <p>
    * Command is the command to run in the container as a String or an array of Strings.  The command is optional (need not be supplied or can be null).
    * The 'command' is an array of one or more Strings to define the command (or commands) to be exec'd on the container.  Due to the way command line arguments are interpreted, it may be neccessary to break a command up into its components into a String array in order for it to be processed correctly.  Note that one approach is to exec the shell in one array element and then the desired command can be given in another array element (see example 1); multiple commands can be combined with ampersand (see example 2):
    * <ol>
    *    <li>["/bin/bash", "-c", "pwd"]</li>
    *    <li>["/bin/bash", "-c", "pwd &amp;&amp; ls"]</li>
    * </ol>
    * <p>
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>out -- output from the command as a String, if any; only present if 'success' is true</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param image
    *    the image to run
    * @param options
    *    options to add to the run command
    * @param command
    *    the command to run on the container; optional
    * @return a Map containing the result of the command
    * @throws ClassCastException if the command isn't of type String or String[]
    */
   static def dockerRun( String image, Map<String,String> options, command = null ) {

      String[] runCommand = getDockerRunCommand( image, options, command )

      def queryMap = ExecUtils.exec( runCommand )

      def responseMap = [:]

      if ( queryMap.exitValue == 0 ) {
         responseMap.success = true
         responseMap.out = queryMap.out
      } else {
         responseMap.success = false
         responseMap.reason = queryMap.err
      }

      return( responseMap )
   }


   /**
    * Returns a String array, suitable for executing, describing a 'docker stop' command for the container 'container'.
    * <p>
    * This method is purposely separate from 'dockerStop(...)' because a build process may need to dynamically create such a command to generate an appropriate bash script etc.
    *
    * @param container
    *    the container to stop
    * @return a String array, suitable for executing, describing a 'docker stop' command
    */
   static String[] getDockerStopCommand( String container ) {
      String[] dockerStopCommand = [ 'docker', 'stop', container ]
      return( dockerStopCommand )
   }


   /**
    * Stops the container 'container' and returns a Map with the result of the action.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param container
    *    the container to stop
    * @return a Map containing the result of the command
    */
   static def dockerStop( String container ) {

      String[] dockerStopCommand = getDockerStopCommand( container )

      def queryMap = ExecUtils.exec( dockerStopCommand )


      def responseMap = [:]

      if ( queryMap.exitValue == 0 ) {
         responseMap.success = true
      } else {
         responseMap.success = false
         responseMap.reason = queryMap.err
      }

      return( responseMap )
   }


   /**
    * Returns a String array, suitable for executing, describing a 'docker exec' command with optional options.
    * <p>
    * This method is a convenience method for getDockerExecCommand(String,String[],Map&lt;String,String&gt;) when the command to execute can be expressed as a single String and need not be written as an array of Strings.
    * <p>
    * For further details and complete documentation, see getDockerExecCommand(String,String[],Map&lt;String,String&gt;).
    * <p>
    * This method is purposely separate from 'dockerExec(...)' because a build process may need to dynamically create such a command to generate an appropriate bash script etc.
    *
    * @param container
    *    the container to exec
    * @param command
    *    the command to exec
    * @param options
    *    options to add to the exec command; optional
    * @return a String array describing the command
    */
   static String[] getDockerExecCommand( String container, String command, Map<String, String> options = null ) {
      String[] commandArray = [command]
      return( getDockerExecCommand( container, commandArray, options ) )
   }


   /**
    * Returns a String array, suitable for executing, describing a 'docker exec' command with optional options.
    * <p>
    * The 'container' can be the name or ID of the container.
    * <p>
    * The 'command' is an array of one or more Strings to define the command (or commands) to be exec'd on the container.  Due to the way command line arguments are interpreted, it may be neccessary to break a command up into its components into a String array in order for it to be processed correctly.  Note that one approach is to exec the shell in one array element and then the desired command can be given in another array element (see example 1); multiple commands can be combined with ampersand (see example 2):
    * <ol>
    *    <li>["/bin/bash", "-c", "pwd"]</li>
    *    <li>["/bin/bash", "-c", "pwd &amp;&amp; ls"]</li>
    * </ol>
    * <p>
    * The 'options' is a Map of options, and is not required.  Options may have both key and value such as "--user" mapped to "&lt;user&gt;", or some options are one item, in which case leave the Map value as empty String or null such as "-t" mapped to "" or null.
    * <p>
    * This method is purposely separate from 'dockerExec(...)' because a build process may need to dynamically create such a command to generate an appropriate bash script etc.
    *
    * @param container
    *    the container to exec
    * @param command
    *    the command to exec
    * @param options
    *    options to add to the exec command; optional
    * @return a String array describing the command
    */
   static String[] getDockerExecCommand( String container, String[] command, Map<String, String> options = null ) {

      int sizeFromOptions = 0

      if ( options != null ) {

         sizeFromOptions += options.size( ) // counts the keys

         for ( String value : options.values( ) ) {
            if ( value != null && !value.equals( '' ) ) {
               sizeFromOptions++ // counts values that are not null or empty String
            }
         }

      }


      // docker exec [OPTIONS] CONTAINER COMMAND [ARG...]
      // 'docker', 'exec', '<options>', 'container', '<command>'
      // = 1 + 1 + sizeFromOptions + 1 + command.length
      int totalSize = 3 + sizeFromOptions + command.length

      String[] execCommand = new String[totalSize]
      execCommand[0] = 'docker'
      execCommand[1] = 'exec'

      int index = 2

      if ( options != null ) {

         for ( Map.Entry<String, String> entry : options.entrySet( ) ) {

            execCommand[index] = entry.getKey( )
            index++

            if ( entry.getValue( ) != null && !entry.getValue( ).equals( '' ) ) {
               execCommand[index] = entry.getValue( )
               index++
            }

         }

      }

      execCommand[index] = container

      index++

      for ( String part : command ) {
         execCommand[index] = part
         index++
      }

      return( execCommand )
   }


   /**
    * Execs into the container 'container' with the command 'command' and optional options 'options', and returns a Map with the result of the action.
    * <p>
    * This method is a convenience method for dockerExec(String,String[],Map&lt;String,String&gt;) when the command to execute can be expressed as a single String and need not be written as an array of Strings.
    * <p>
    * For further details and complete documentation, see dockerExec(String,String[],Map&lt;String,String&gt;).
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>out -- output from the command as a String, if any; only present if 'success' is true</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param container
    *    the container to exec
    * @param command
    *    the command to exec
    * @param options
    *    options to add to the exec command; optional
    * @return a Map containing the result of the command
    */
   static def dockerExec( String container, String command, Map<String, String> options = null ) {

      String[] commandArray = [command]

      return( dockerExec( container, commandArray, options ) )
   }


   /**
    * Execs into the container 'container' with the command 'command' and optional options 'options', and returns a Map with the result of the action.
    * <p>
    * The 'container' can be the name or ID of the container.
    * <p>
    * The 'command' is an array of one or more Strings to define the command (or commands) to be exec'd on the container.  Due to the way command line arguments are interpreted, it may be neccessary to break a command up into its components into a String array in order for it to be processed correctly.  Note that one approach is to exec the shell in one array element and then the desired command can be given in another array element (see example 1); multiple commands can be combined with ampersand (see example 2):
    * <ol>
    *    <li>["/bin/bash", "-c", "pwd"]</li>
    *    <li>["/bin/bash", "-c", "pwd &amp;&amp; ls"]</li>
    * </ol>
    * <p>
    * The 'options' is a Map of options, and is not required.  Options may have both key and value such as "--user" mapped to "&lt;user&gt;", or some options are one item, in which case leave the Map value as empty String or null such as "-d" mapped to "" or null.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>out -- output from the command as a String, if any; only present if 'success' is true</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param container
    *    the container to exec
    * @param command
    *    the command to exec
    * @param options
    *    options to add to the exec command; optional
    * @return a Map containing the result of the command
    */
   static def dockerExec( String container, String[] command, Map<String, String> options = null ) {

      String[] execCommand = getDockerExecCommand( container, command, options )


      def queryMap = ExecUtils.exec( execCommand )

      def responseMap = [:]

      if ( queryMap.exitValue == 0 ) {
         responseMap.success = true
         responseMap.out = queryMap.out
      } else {
         responseMap.success = false
         responseMap.reason = queryMap.err
      }

      return( responseMap )
   }


   /**
    * Queries if the image 'image' exists locally.
    * <p>
    * The 'image' can be the name or ID of the image.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>exists -- boolean true if the image exists and false otherwise; only present if 'success' is 'true'</li>
    *    <li>out -- output from the command as a String, if any; only present if 'success' is true</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param image
    *    the image to query
    * @return a Map containing the result of the command
    */
   static def dockerImageExists( String image ) {

      String command = 'docker images -q ' + image


      def queryMap = ExecUtils.exec( command )

      def responseMap = [:]

      if ( queryMap.exitValue == 0 ) {

         responseMap.success = true

         if ( queryMap.out.equals( '' ) ) {
            responseMap.exists = false
         } else {
            responseMap.exists = true
            responseMap.out = queryMap.out
         }

      } else {
         responseMap.success = false
         responseMap.reason = queryMap.err
      }

      return( responseMap )
   }


   /**
    * Saves the image 'image' as a tar file using the file and path in 'filename' with optional gzip compression per 'gzip'.
    * <p>
    * The 'image' can be the name or ID of the image.  This method will return succesful (e.g. 'success' is 'true') even if the image does not exist, and then the resultant file will be an invalid tar file.
    * <p>
    * The 'filename' is the name of the output file, which can include a path.  If gzip compression is not used (e.g. gzip is 'false'), then the recommended filename is &lt;filename&gt;.tar.  If gzip compression is used (e.g. gzip is 'true' or not set), then the recommended filename is &lt;filename&gt;.tar.gz.
    * <p>
    * The 'gzip' option determines if gzip compression is used:  'false' for no gzip, and 'true' or not set for gzip.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>out -- output from the command as a String, if any; only present if 'success' is true</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param image
    *    the image to save
    * @param filename
    *    the resultant output filename with optional path
    * @param gzip
    *    'true' to enable gzip compression of output file and 'false' otherwise; optional, defaults to 'true'
    * @return a Map containing the result of the command
    */
   static def dockerSave( String image, String filename, boolean gzip = true ) {

      String[] saveCommand

      if ( gzip ) {
         String ds = 'docker save ' + image + ' | gzip > ' + filename
         saveCommand = [ '/bin/bash',
                         '-c',
                         ds
                       ]
      } else {
         saveCommand = [ 'docker',
                         'save',
                         image,
                         '-o',
                         filename
                       ]
      }

      def queryMap = ExecUtils.exec( saveCommand )

      def responseMap = [:]

      if ( queryMap.exitValue == 0 ) {
         responseMap.success = true
         responseMap.out = queryMap.out
      } else {
         responseMap.success = false
         responseMap.reason = queryMap.err
      }

      return( responseMap )
   }


   /**
    * Tags the image 'image' with the tag 'tag'.
    * <p>
    * This is a convenience method for dockerTag(Map&lt;String,String[]&gt;) where only one image with one tag is needed.
    * <p>
    * The 'image' can be the name or ID of the image.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- a String describing the error; only present if 'success' is false</li>
    * </ul>
    *
    * @param image
    *    the image to tag
    * @param tag
    *    the tag for the image
    * @return a Map containing the result of the command
    */
   static def dockerTag( String image, String tag ) {

      Map<String,String[]> imageTag = new HashMap<String,String[]>( )
      String[] tagArray = [tag]
      imageTag.put( image, tagArray )


      def resultMap

      def tempMap = dockerTag( imageTag )

      if ( tempMap.success ) {
         resultMap = tempMap
      } else {
         resultMap = [:]
         resultMap.success = false
         resultMap.reason = tempMap.reason.get( image ).get( tag )
      }

      return( resultMap )
   }


   /**
    * Tags the image 'image' with one or more tags defined in the 'tag' argument.
    * <p>
    * This is a convenience method for dockerTag(Map&lt;String,String[]&gt;) where only one image needs to be tagged.
    * <p>
    * The 'image' can be the name or ID of the image.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- a Map associating the tag to value error, so { tag -&gt; error } }; only present if 'success' is false</li>
    * </ul>
    *
    * @param image
    *    the image to tag
    * @param tag
    *    the tags to tag the image
    * @return a Map containing the result of the command
    */
   static def dockerTag( String image, String[] tag ) {

      Map<String,String[]> imageTag = new HashMap<String,String[]>( )
      imageTag.put( image, tag )

      def resultMap

      def tempMap = dockerTag( imageTag )

      if ( tempMap.success ) {
         resultMap = tempMap
      } else {
         resultMap = [:]
         resultMap.success = false
         resultMap.reason = tempMap.reason.get( image )
      }

      return( resultMap )
   }


   /**
    * Tags the images with the associations found in the 'imageTag' argument.
    * <p>
    * The 'imageTag' is a Map of String image names to either one tag as a String or multiple tags as a String array.  An image can be the name or ID of the image.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- a Map associating image to value of a Map associating tag to value error, so { image -&gt; { tag -&gt; error } }; only present if 'success' is false</li>
    * </ul>
    *
    * @param imageTag
    *    a Map of images to tags
    * @return a Map containing the result of the command
    * @throws ClassCastException if the value in the Map isn't of type String or String[]
    */
   static def dockerTag( imageTag ) {

      if ( !imageTag.isEmpty( ) ) {
         def entry = imageTag.entrySet( ).iterator( ).next( );
         def key = entry.getKey( );
         def value = entry.getValue( );

         if ( key instanceof String ) {

            if ( value instanceof String ) {

               Map<String,String[]> imageTagArray = new HashMap<String,String[]>( )
               String[] tagArray

               for ( var entrySub : imageTag.entrySet( ) ) {
                  tagArray = [ entrySub.getValue( ) ]
                  imageTagArray.put( entrySub.getKey( ), tagArray )
               }

               imageTag = imageTagArray

            } else if ( value instanceof String[] ) {
               // do nothing
            } else {
               throw new ClassCastException( )
            }

         } else {
            throw new ClassCastException( )
         }
      }



      String[] tagCommand = new String[4]
      tagCommand[0] = 'docker'
      tagCommand[1] = 'tag'


      def queryMap

      def responseMap = [:]
      responseMap.success = true


      String currentImage

      for ( var entry : imageTag.entrySet( ) ) {

         currentImage = entry.getKey( ) // String image name
         tagCommand[2] = currentImage


         for ( String currentTag : entry.getValue( ) ) { // String[] of tags

            tagCommand[3] = currentTag

            queryMap = ExecUtils.exec( tagCommand )

            if ( queryMap.exitValue != 0 ) {

               responseMap.success = false


               // responseMap.reason = { image -> { tag -> err } }

               def imageTagMap
               def tagErrorMap

               if ( responseMap.containsKey( 'reason' ) ) {
                  imageTagMap = responseMap.reason
               } else {
                  imageTagMap = [:]
                  responseMap.reason = imageTagMap
               }


               if ( imageTagMap.containsKey( currentImage ) ) {
                  tagErrorMap = imageTagMap.get( currentImage )
               } else {
                  tagErrorMap = [:]
                  imageTagMap.put( currentImage, tagErrorMap )
               }


               tagErrorMap.put( currentTag, queryMap.err )

            }

         }

      }


      return( responseMap )

   }


   /**
    * Builds a Docker image defined by the Dockerfile in the directory of 'buildDirectory', and tags the image the tag 'tag'.
    * <p>
    * The Dockerfile defining the build of the image must be named 'Dockerfile' and located in the directory 'buildDirectory'.
    * <p>
    * This method is a convenience method for 'dockerBuild(String buildDirectory, String[] tags)' when only one tag is needed.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param buildDirectory
    *    directory containing the Dockerfile
    * @param tag
    *    tag to tag the built image
    * @return a Map containing the result of the command
    */
   static def dockerBuild( String buildDirectory, String tag ) {
      String[] tagArray = [tag]
      return( dockerBuild( buildDirectory, tagArray ) )
   }


   /**
    * Builds a Docker image defined by the Dockerfile in the directory of 'buildDirectory', and tags the image with one or more tags as defined in 'tags'.
    * <p>
    * The Dockerfile defining the build of the image must be named 'Dockerfile' and located in the directory 'buildDirectory'.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param buildDirectory
    *    directory containing the Dockerfile
    * @param tags
    *    one or more tags with which to tag the built image
    * @return a Map containing the result of the command
    */
   static def dockerBuild( String buildDirectory, String[] tags ) {

      def responseMap = [:]

      String[] buildCommand = [ 'docker', 'build', '-t', tags[0], buildDirectory ]

      def queryMap = ExecUtils.exec( buildCommand )

      if ( queryMap.exitValue == 0 && tags.length > 1 ) {

         String[] remainderTags = tags[1..-1].collect { it as String } as String[]

         responseMap = dockerTag( tags[0], remainderTags )

      } else {

         if ( queryMap.exitValue == 0 ) {
            responseMap.success = true
            responseMap.out = queryMap.out
         } else {
            responseMap.success = false
            responseMap.reason = queryMap.err
         }

      }

      return( responseMap )
   }


   /**
    * Pushes one or more Docker images identified by 'tag' to a registry.
    * <p>
    * This method can be used in two ways: to push a single tag or to push all tags of an image.
    * <p>
    * To push a single tag for an image, then the argument 'tag' should contain the tag (version) such as &lt;registry&gt;&lt;:optional port&gt;/&lt;repository&gt;/&lt;image name&gt;&lt;:optional version&gt; with 'allTags' not provided or set to 'false'.  Note that if no version is given, then 'latest' is used.
    * <p>
    * To push multiple tags for an image, set 'tag' as above less the optional 'version' with 'allTags' set to 'true'.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>out -- String output from the command; always provided, even if the command fails</li>
    *    <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param tag
    *    tag to push
    * @param allTags
    *    'true' to push all tags for the image or 'false' or not provided for otherwise
    * @return a Map containing the result of the command
    */
   static def dockerPush( String tag, boolean allTags = false ) {

      int totalSize = 4

      if ( allTags ) {
         totalSize++
      }

      String[] pushCommand = new String[totalSize]

      pushCommand[0] = 'docker'
      pushCommand[1] = 'image'
      pushCommand[2] = 'push'

      int index = 3

      if ( allTags ) {
         pushCommand[3] = '--all-tags'
         index++
      }

      pushCommand[index] = tag


      def queryMap = ExecUtils.exec( pushCommand )

      def responseMap = [:]

      // note that including stdout regardless of success or failure
      responseMap.out = queryMap.out

      if ( queryMap.exitValue == 0 ) {
         responseMap.success = true
      } else {
         responseMap.success = false
         responseMap.reason = queryMap.err
      }

      return( responseMap )

   }


   /**
    * Pushes one or more Docker images identified by 'tags' to a registry.
    * <p>
    * This method can be used in two ways: to push a single tag per array entry or to push all tags of an image for an array entry.
    * <p>
    * To push a single tag per array entry for an image, then the entry's argument should contain the tag (version) such as &lt;registry&gt;&lt;:optional port&gt;/&lt;repository&gt;/&lt;image name&gt;&lt;:optional version&gt; with 'allTags' not provided or set to 'false'.  Note that if no version is given, then 'latest' is used.
    * <p>
    * To push multiple tags for an array entry's image, set the entries in the 'tags' array as above less the optional 'version' with 'allTags' set to 'true'.
    * <p>
    * Note that 'allTags' applies to all images/tags across the entire 'tags' array.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if all tag pushes were successful and false if one or more tag pushes failed</li>
    *    <li>tags -- a Map of &lt;tag;&gt; to result of the push command for that tag</li>
    *       <li>&lt;tag&gt;</li>
    *       <ul>
    *          <li>success -- boolean true if this tag push was successful and false otherwise</li>
    *          <li>out -- String output from the command; always provided, even if the command fails</li>
    *          <li>reason -- reason why the command failed as a String, which is the error output returned from executing the command; only present if 'success' is false</li>
    *       </ul>
    * </ul>
    *
    * @param tags
    *    array of tags to push
    * @param allTags
    *    'true' to push all tags for the image or 'false' or not provided for otherwise
    * @return a Map containing the result of the command
    */
   static def dockerPush( String[] tags, boolean allTags = false ) {

      def responseMap = [:]
      responseMap.success = true
      responseMap.tags = [:]

      for ( String tag : tags ) {

         def resultMap = dockerPush( tag, allTags )

         responseMap.tags.put( tag, resultMap )

         if ( !resultMap.success ) {
            responseMap.success = false
         }
      }

      return( responseMap )
   }



   private DockerUtils( ) { }

}
