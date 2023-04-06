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
   static Map<String,String> getContainerHealth( String container ) {

      Map<String,String> response = new HashMap<String,String>( )


      String command = 'docker inspect --format {{.State.Health.Status}} ' + container

      Map<String, String> query = GradleExecUtils.exec( command )


      if ( query.get( 'exitValue' ) == 0 ) {
         response.put( 'health', query.get( 'out' ).toLowerCase( ) )
      } else if ( query.get( 'err' ).contains( 'map has no entry for key "Health"' ) ) {
         response.put( 'health', 'no-healthcheck' )
      } else if ( query.get( 'err' ).contains( 'No such object' ) ) {
         response.put( 'health', 'not-found' )
      } else {
         response.put( 'health', 'error' )
         response.put( 'reason', query.get( 'err' ) )
      }

      return( response )

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
   Map<String, String> waitForContainer( String container, String target ) {
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
   Map<String, String> waitForContainer( String container, String target, int retrySeconds, int retryNum ) {
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
   Map<String, String> waitForContainer( Map<String,String> containerMap ) {
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
    *          <li>illegal-target-disposition-{&lt;disposition&gt;, null} -- the target state or health status is not valid</li>
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
   Map<String, String> waitForContainer( Map<String,String> containerMap, int retrySeconds, int retryNum ) {

      Map<String, String> result = new HashMap<String, String>( )
      result.put( 'success', false )

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

               if ( targetContainerDisposition != null ) {
                  result.put( 'message', 'illegal-target-disposition-' + targetContainerDisposition )
               } else {
                  result.put( 'message', 'illegal-target-disposition-null'  )
               }

            }

         } // end while loop



         if ( !done && !it.hasNext( ) && proceed ) {
            done = true
            result.put( 'success', true )
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


   private DockerUtils( ) { }
}
