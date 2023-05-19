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


/**
 * Provides Docker utilities.
 *
 */
final class DockerUtils {

   /* todo: here and tests:

      - filename preferred is compose.yaml
      - remove 'version'
      - can use -p for project name
      - use hyphen not underscore for word separator

      */


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

      def queryMap = GradleExecUtils.exec( command )


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

      def queryMap = GradleExecUtils.exec( command )


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
    * Returns a String array, suitable for executing, describing a docker-compose "up" command with one or more compose files 'composeFilePaths'.
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
    * @return a String array, suitable for executing, describing a docker-compose "up" command using the compose file path or paths
    */
   static String[] getComposeUpCommand( java.lang.String... composeFilePaths ) {

      int totalSize = 3 + ( composeFilePaths.length * 2 )

      String[] composeUpCommand = new String[totalSize]

      composeUpCommand[0] = 'docker-compose'

      int index
      int i
      for ( i = 0; i < composeFilePaths.length; i++ ) {
         index = ( i * 2 ) + 1
         composeUpCommand[index] = '-f'
         composeUpCommand[index+1] = composeFilePaths[i]
      }

      index = ( i * 2 ) + 1
      composeUpCommand[index] = 'up'
      composeUpCommand[index+1] = '-d'


      return( composeUpCommand )
   }


   /**
    * Returns a String array, suitable for executing, describing a docker-compose "down" command with the compose file 'composeFilePath'.
    * <p>
    * If using multiple compose files combined in the "up" command, the common compose file describing all services must be used for this method.
    * <p>
    * This method is purposely separate from 'composeDown(...)' because a build process may need to dynamically create such a command to generate an appropriate bash script etc.
    *
    * @param composeFilePath
    *    the path to the Docker compose file
    * @return a String array, suitable for executing, describing a docker-compose "down" command using a compose file
    */
   static String[] getComposeDownCommand( String composeFilePath ) {
      String[] composeDownCommand = [ 'docker-compose', '-f', composeFilePath, 'down' ]
      return( composeDownCommand )
   }


   /**
    * Performs a "docker-compose up" command using one or more compose file pathss 'composeFilePaths' and returns a Map indicating the result of the action.
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

      def queryMap = GradleExecUtils.exec( composeUpCommand )


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
    * Performs a "docker-compose down" command using the compose file path 'composeFilePath' and returns a Map indicating the result of the action.
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

      def queryMap = GradleExecUtils.exec( composeDownCommand )


      def responseMap = [:]

      if ( queryMap.get( 'exitValue' ) == 0 ) {
         responseMap.put( 'success', true )
      } else {
         responseMap.put( 'success', false )
         responseMap.put( 'reason', queryMap.get( 'err' ) )
      }

      return( responseMap )

   }


   /*
      docker run [OPTIONS] IMAGE [COMMAND] [ARG...]

      docker run 
         [options:
            --rm 
            --name <friendly name> 
            --user format: <name|uid>[:<group|gid>] 
            --volume , -v 
            -p or --expose 80 or 80:80
         ]
         image
         [command]
         [arg]


       docker run [OPTIONS] IMAGE [COMMAND] [ARG...]
       docker run IMAGE [OPTIONS] [COMMAND]

   */


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
            commandArray = null
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
            commandArray = []
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
    */
   static def dockerRun( String image, Map<String,String> options, command = null ) {

      String[] runCommand = getDockerRunCommand( image, options, command )

      def queryMap = GradleExecUtils.exec( runCommand )

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

      def queryMap = GradleExecUtils.exec( dockerStopCommand )


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


      def queryMap = GradleExecUtils.exec( execCommand )

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
    * Saves the image 'image' as a tar file using the file and path in 'filename' with optional gzip compression per 'gzip.
    * <p>
    * The 'image' can be the name or ID of the image.
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
                         ds,
                       ]
      } else {
         saveCommand = [ 'docker',
                         'save',
                         image,
                         '-o',
                         filename
                       ]
      }

      def queryMap = GradleExecUtils.exec( saveCommand )

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


   //todo docs
   static def dockerTag( String image, String tag ) {
      Map<String,String> imageTag = new HashMap<String,String[]>( )
      String[] tagArray = [tag]
      imageTag.put( image, tagArray )
      return( dockerTag( imageTag ) )
   }


   //todo docs
   static def dockerTag( String image, String[] tag ) {
      Map<String,String> imageTag = new HashMap<String,String[]>( )
      imageTag.put( image, tag )
      return( dockerTag( imageTag ) )
   }


   /*
   //todo docs
   static def dockerTag( Map<String,String> imageTag ) {

      Map<String,String> imageTagArray = new HashMap<String,String[]>( )
      String[] tag


      return( 'todo' )
   }
   */


   //todo docs
   static def dockerTag( Map<String,String[]> imageTag ) {

      String[] tagCommand = new String[4]
      tagCommand[0] = 'docker'
      tagCommand[1] = 'tag'


      def queryMap

      def responseMap = [:]
      responseMap.success = true



      String currentImage

      for ( var entry : imageTag.entrySet( ) ) {

         println "image " + entry.getKey( )

         currentImage = entry.getKey( ) // String image name
         tagCommand[2] = currentImage


         for ( String currentTag : entry.getValue( ) ) { // String[] of tags

            println "tag " + currentTag
            tagCommand[3] = currentTag

            println "command " + tagCommand

            queryMap = GradleExecUtils.exec( tagCommand )

            if ( queryMap.exitValue != 0 ) {

               println 'err = ' + queryMap.err

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
                  tagErrorMap = imageTagMap.currentImage
               } else {
                  tagErrorMap = [:]
                  tagErrorMap.currentImage = imageTagMap
               }


               tagErrorMap.currentTag = queryMap.err

            }

         }

      }


      return( responseMap )

   }
   //todo


   private DockerUtils( ) { }

}
