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


   /**
    * Returns a Map indicating the state of the container 'container'.
    * <p>
    * The 'container' argument can be the container ID or the container name.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>state -- the state of the container</li>
    *    <li>reason -- reason why the command failed, which is the error output returned from executing the command; only present if 'state' is 'error'</li>
    * </ul>
    * <p>
    * The 'state' entry of the returned Map will have one of the following values.  The first four are Docker-defined container states.
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
   static Map<String,String> getContainerState( String container ) {

      Map<String,String> response = new HashMap<String,String>( )


      String command = 'docker inspect --format {{.State.Status}} ' + container

      Map<String, String> query = GradleExecUtils.exec( command )


      if ( query.get( 'exitValue' ) == 0 ) {
         response.put( 'state', query.get( 'out' ).toLowerCase( ) )
      } else if ( query.get( 'err' ).contains( 'No such object' ) ) {
         response.put( 'state', 'not-found' )
      } else {
         response.put( 'state', 'error' )
         response.put( 'reason', query.get( 'err' ) )
      }

      return( response )

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
         responseMap.put( 'health', queryMap.get( 'out' ).toLowerCase( ) )
      } else if ( queryMap.get( 'err' ).contains( 'map has no entry for key "Health"' ) ) {
         responseMap.put( 'health', 'no-healthcheck' )
      } else if ( queryMap.get( 'err' ).contains( 'No such object' ) ) {
         responseMap.put( 'health', 'not-found' )
      } else {
         responseMap.put( 'health', 'error' )
         responseMap.put( 'reason', queryMap.get( 'err' ) )
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
   static Map<String, String> waitForContainer( String container, String target ) {
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
   static Map<String, String> waitForContainer( String container, String target, int retrySeconds, int retryNum ) {
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
   static Map<String, String> waitForContainer( Map<String,String> containerMap ) {
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
    * Valid values for the 'reason' entry in the returned map are: 
    * <ul>
    *    <li>unhealthy -- if target is 'healthy' health status, then returns this value for the first container that is 'unhealthy'</li>
    *    <li>no-health-check -- if target is 'healthy' health status, then returns this value for the first container that does not have a health check</li>
    *    <li>failed -- if target is 'running' state, then returns this value if at least one container is in a non-running state that indicates a failure or unexpected state (e.g., 'restarting', 'paused', or 'exited')</li>
    *    <li>num-retries-exceeded -- the number of retries was exceeded and at least one container was not in its target state or health status and didn't qualify for a condition above e.g., 'unhealthy' or 'failed'</li>
    *    <li>error -- an unexpected error occurred</li>
    * </ul>
    * <p>
    * Valid values for the 'message' entry in the returned map are:
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
   static Map<String, String> waitForContainer( Map<String,String> containerMap, int retrySeconds, int retryNum ) {

      Map<String, String> result = new HashMap<String, String>( )
      result.put( 'success', 'false' )

      boolean done= false
      int count = 0 // counting up to 'retryNum' times

      // loop local variables
      boolean proceed // no failures, so current container has no failures or indeterminate result and no errors
      String currentContainer
      String targetContainerDisposition  // state or health status
      String currentContainerDisposition // state or health status
      Set<String> keySet
      Map<String, String> queryResult
      Map<String, String> tempResult = new HashMap<String, String>( )  // populates data for a failure case to retry later, in the event 'retryNum' exceeded

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
               queryResult = getContainerHealth( currentContainer )

               currentContainerDisposition = queryResult.get( 'health' )

               if ( currentContainerDisposition.equalsIgnoreCase( 'healthy' ) ) {
                  proceed = true
               } else if ( currentContainerDisposition.equalsIgnoreCase( 'unhealthy' ) ) {

                  done = true
                  // implicit proceed = false

                  result.put( 'reason', 'unhealthy' )
                  result.put( 'container', currentContainer )

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'no-healthcheck' ) ) {

                  done = true
                  // implicit proceed = false

                  result.put( 'reason', 'no-health-check' )
                  result.put( 'container', currentContainer )

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'error' ) ) {

                  done = true
                  // implicit proceed = false

                  result.put( 'reason', 'error' )
                  result.put( 'message', queryResult.get( 'reason' ) )
                  result.put( 'container', currentContainer )

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'starting' ) ) {
                  tempResult.put( 'message', 'starting' )
                  tempResult.put( 'container', currentContainer )
               } else if ( currentContainerDisposition.equalsIgnoreCase( 'not-found' ) ) {
                  tempResult.put( 'message', 'not-found' )
                  tempResult.put( 'container', currentContainer )
               }

            } else if ( targetContainerDisposition.equals( 'running' ) ) {

               // returns 'state' entry with one of {created, restarting, running, paused, exited, dead, not-found, error}
               queryResult = getContainerState( currentContainer )

               currentContainerDisposition = queryResult.get( 'state' )

               if ( currentContainerDisposition.equalsIgnoreCase( 'running' ) ) {
                  proceed = true
               } else if (
                          currentContainerDisposition.equalsIgnoreCase( 'restarting' )   ||
                          currentContainerDisposition.equalsIgnoreCase( 'paused' )   ||
                          currentContainerDisposition.equalsIgnoreCase( 'dead' )     ||
                          currentContainerDisposition.equalsIgnoreCase( 'exited' ) ) {

                  done = true
                  // implicit proceed = false

                  result.put( 'reason', 'failed' )
                  result.put( 'message', currentContainerDisposition )
                  result.put( 'container', currentContainer )

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'error' ) ) {

                  done = true
                  // implicit proceed = false

                  result.put( 'reason', 'error' )
                  result.put( 'message', queryResult.get( 'reason' ) )
                  result.put( 'container', currentContainer )

               } else if ( currentContainerDisposition.equalsIgnoreCase( 'created' ) ) {
                  tempResult.put( 'message', 'created' )
                  tempResult.put( 'container', currentContainer )
               } else if ( currentContainerDisposition.equalsIgnoreCase( 'not-found' ) ) {
                  tempResult.put( 'message', 'not-found' )
                  tempResult.put( 'container', currentContainer )
               }

            } else {

               done = true;
               // implicit proceed = false

               result.put( 'reason', 'error' )
               result.put( 'container', currentContainer )
               result.put( 'message', 'illegal-target-disposition' )

            }

         } // end while loop



         if ( !done && !it.hasNext( ) && proceed ) {
            done = true
            result.put( 'success', 'true' )
         } else if ( done ) {
            // catches failure cases and data provided above, preventing the next 'else' with 'num retries' from over-writing
         } else {

            count++

            if ( count == retryNum + 1 ) {
               done = true
               result.put( 'reason', 'num-retries-exceeded' )
               result.put( 'message', tempResult.get( 'message' ) )
               result.put( 'container', tempResult.get( 'container' ) )
            } else {
               Thread.sleep( retrySeconds * 1000 )
            }

         }


      } // end while loop

      return( result )
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
    *    <li>reason -- reason why the command failed, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param composeFilePaths
    *    one or more paths to Docker compose files to use
    * @return a Map containing the result of the command
    */
   static Map<String,String> composeUp( java.lang.String... composeFilePaths ) {

      String[] composeUpCommand = getComposeUpCommand( composeFilePaths as String[] )

      Map<String, String> query = GradleExecUtils.exec( composeUpCommand )


      Map<String, String> response = new HashMap( )

      if ( query.get( 'exitValue' ) == 0 ) {
         response.put( 'success', 'true' )
      } else {
         response.put( 'success', 'false' )
         response.put( 'reason', query.get( 'err' ) )
      }

      return( response )

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
   static Map<String, String> composeDown( String composeFilePath ) {

      String[] composeDownCommand = getComposeDownCommand( composeFilePath )

      Map<String, String> query = GradleExecUtils.exec( composeDownCommand )


      Map<String, String> response = new HashMap( )

      if ( query.get( 'exitValue' ) == 0 ) {
         response.put( 'success', 'true' )
      } else {
         response.put( 'success', 'false' )
         response.put( 'reason', query.get( 'err' ) )
      }

      return( response )

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


   /* todo
   static Map<String, String> dockerRun( String image ) {
   }
   */




   /**
    * Stops the container 'container' and returns a Map with the result of the action.
    * <p>
    * This method returns a Map with the following entries:
    * <ul>
    *    <li>success -- boolean true if the command was successful and false otherwise</li>
    *    <li>reason -- reason why the command failed, which is the error output returned from executing the command; only present if 'success' is false</li>
    * </ul>
    *
    * @param container
    *    the container to stop
    * @return a Map containing the result of the command
    */
   static Map<String, String> dockerStop( String container ) {

      Map<String, String> query = GradleExecUtils.exec( 'docker stop ' + container )


      Map<String, String> response = new HashMap( )

      if ( query.get( 'exitValue' ) == 0 ) {
         response.put( 'success', 'true' )
      } else {
         response.put( 'success', 'false' )
         response.put( 'reason', query.get( 'err' ) )
      }

      return( response )
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
    *    <li>out -- output from the command, if any; only present if 'success' is true</li>
    *    <li>reason -- reason why the command failed, which is the error output returned from executing the command; only present if 'success' is false</li>
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
   static Map<String, String> dockerExec( String container, String command, Map<String, String> options = null ) {

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
    *    <li>out -- output from the command, if any; only present if 'success' is true</li>
    *    <li>reason -- reason why the command failed, which is the error output returned from executing the command; only present if 'success' is false</li>
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
   static Map<String, String> dockerExec( String container, String[] command, Map<String, String> options = null ) {

      //todo examples
   // cmdArray = [ "docker", "exec", "--user", "git", "git-client-john", "/bin/ash", "-c", "echo 'hi' > /repos/testRepoA/blah.txt" ]
   // cmdArray = [ "docker", "exec", "--user", "git", "git-client-john", "/bin/ash", "-c", "cd testRepoB && git add . && git commit -m 'Initial commit'" ]


      int sizeFromMap = 0

      if ( options != null ) {

         sizeFromMap += options.size( ) // counts the keys

         for ( String value : options.values( ) ) {
            if ( value != null || !value.equals( '' ) ) {
               sizeFromMap++ // counts values that are not null or empty String
            }
         }

      }


      // docker exec [OPTIONS] CONTAINER COMMAND [ARG...]
      // 'docker', 'exec', '<options>', 'container', '<command>'
      // = 1 + 1 + sizeFromMap + 1 + command.length
      int totalSize = 3 + sizeFromMap + command.length

      String[] execCommand = new String[totalSize]
      execCommand[0] = 'docker'
      execCommand[1] = 'exec'

      int index = 2

      if ( options != null ) {

         for ( Map.Entry<String, String> entry : options.entrySet( ) ) {

            execCommand[index] = entry.getKey( )
            index++

            if ( entry.getValue( ) != null || !entry.getValue( ).equals( '' ) ) {
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



      Map<String, String> query = GradleExecUtils.exec( execCommand )

      Map<String, String> response = new HashMap<String, String>( )

      if ( query.get( 'exitValue' ) == 0 ) {
         response.put( 'success', 'true' )
         response.put( 'out', query.get( 'out' ) )
      } else {
         response.put( 'success', 'false' )
         response.put( 'reason', query.get( 'err' ) )
      }

      return( response )
   }





   private DockerUtils( ) { }
}
