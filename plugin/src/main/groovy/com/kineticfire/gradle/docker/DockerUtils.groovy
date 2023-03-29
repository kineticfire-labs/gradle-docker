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
import java.util.HashSet


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
    *    <li>reason -- reason why the command failed; only present if 'state' is 'error'</li>
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
    *    <li>healthy</li>
    *    <li>unhealthy -- container could be in the 'running' or 'exited' states; an exited (e.g., stopped container) is always 'unhealthy'</li>
    *    <li>starting -- the health check hasn't returned yet</li>
    *    <li>none -- could not determine the container's health; populates the 'reason' entry</li>
    *    <li>error -- an error occurred when executing the command</li>
    * </ul>
    * <p>
    * The 'reason' entry of the returned Map is populated only if 'health' is 'none' and consists of the following fields:
    * <ol>
    *    <li>unknown -- the container has no health check</li>
    *    <li>not-found -- the container wasn't found</li>
    * </ol>
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
         response.put( 'health', 'none' )
         response.put( 'reason', 'unknown' )
      } else if ( query.get( 'err' ).contains( 'No such object' ) ) {
         response.put( 'health', 'none' )
         response.put( 'reason', 'not-found' )
      } else {
         response.put( 'health', 'error' )
         response.put( 'reason', query.get( 'err' ) )
      }

      return( response )

   }


   /**
    * Waits for the container 'container' to reach the 'running' state.
    * <p>
    * Retries up to 10 times, waiting two seconds between attempts.
    * <p>
    * This is a convience method for waitForContainerStateRunning( String[] containerSet, int retrySeconds, int retryNum ) where 'container' is added to a Set, 'retrySeconds' is '2', and 'retryNum' is '10'.  See that method for details.
    * 
    * @param container
    *    a container reference (ID or name) as a String to query
    * @return a Map indicating if the container is in the 'running' state with additional information
    */
   Map<String, String> waitForContainerStateRunning( String container ) {
      Set<String> containerSet = new HashSet<String>( )
      containerSet.add( container )
      return( waitForContainerStateRunning( containerSet ) )
   }


   /**
    * Waits for the container 'container' to reach the 'running' state.
    * <p>
    * This is a convience method for waitForContainerStateRunning( String[] containerSet, int retrySeconds, int retryNum ), where 'container' is added to a Set.  See that method for details.
    * 
    * @param container
    *    a container reference (ID or name) as a String to query
    * @param retrySeconds
    *    integer number of seconds to wait between retries
    * @param retryNum
    *    number of times to retry, waiting for the container to reach the 'running' state until a failure is returned
    * @return a Map indicating if the container is in the 'running' state with additional information
    *
    */
   Map<String, String> waitForContainerStateRunning( String container, int retrySeconds, int retryNum ) {
      Set<String> containerSet = new HashSet<String>( )
      containerSet.add( container )
      return( waitForContainerStateRunning( containerSet, retrySeconds, retryNum ) )
   }


   /**
    * Waits for a set of containers 'containerSet' to reach 'running' states.
    * <p>
    * Retries up to 10 times, waiting two seconds between attempts.
    * <p>
    * This is a convience method for waitForContainerStateRunning( String[] containerSet, int retrySeconds, int retryNum ) where 'retrySeconds' is '2' and 'retryNum' is '10'.  See that method for details.
    * @param containerSet
    *    a Set containing one or more container references (IDs and/or names) as Strings to query
    * @return a Map indicating if all containers are in 'running' states with additional information
    */
   Map<String, String> waitForContainerStateRunning( Set<String> containerSet ) {
      return( waitForContainerStateRunning( containerSet, 2, 10 ) )
   }


   /**
    * Waits for a set of containers 'containerSet' to reach 'running' states.
    * <p>
    * Blocks while waiting for all containers in the 'containerSet' to reach 'running' states or until an error or non-recoverable state is reached by at least one container or 'numRetries' is exceeded.  Performs first query for containers' states when the method is called (no initial delay).  If all containers are not in 'running' states, then will block while waiting 'retrySeconds' and retry up to 'retryNum' times.  Returns, possibly immediately or otherwise will block, until one of the following conditions is met:
    * <ul>
    *    <li>all containers are in 'running' states</li>
    *    <li>at least one container is in a non-running state, e.g. 'restarting', 'paused', or 'exited'</li>
    *    <li>at least one container is not in a 'running' state and the number of retries 'retryNum' has been hit</li>
    *    <li>a container couldn't be found</li>
    *    <li>an error occurred</li>
    * </ul>
    * <p>
    * A Map is returned with the result of the method that contains the following entries:
    * <ul>
    *   <li>allRunning -- a boolean that is 'true' if all containers in the 'containerSet' are in 'running' states and 'false' otherwise</li>
    *   <li>status -- a String status indicating why all containers are not in 'running' states; present only if 'allRunning' is false</li>
    *   <li>reason -- a String reason for the 'status'; present only if 'allRunning' is false and 'status' is 'container-state-not-running' or 'container-not-found'</li>
    *   <li>container -- a String identifying the first container queried that was not in a 'running' state when 'retryNum' was exceeded; present only if 'allRunning' is false</li>
    * </ul>
    * <p>
    * Status indicators for the 'status' entry in the returned map are:
    * <ul>
    *   <li>container-not-running -- a container is in a non-running state and 'reason' has the state 'Container restarting', 'Container paused,' or 'Container exited'</li>
    *   <li>num-retries-exceeded</li>
    *   <li>container-not-found</li>
    * </ul>
    * <p>
    * If the method returns and indicates all containers are in 'running' states, consider that (1) the container state can change later e.g. it can exit and (2) a continer state of 'running' state may not be indicative of the health and availability of the service provided by the container, so other checks may be needed.
    *
    * @param containerSet
    *    a Set containing one or more container references (IDs and/or names) as Strings to query
    * @param retrySeconds
    *    integer number of seconds to wait between retries
    * @param retryNum
    *    number of times to retry, waiting for all containers to reach 'running' states until a failure is returned
    * @return a Map indicating if all containers are in 'running' states with additional information
    */
   Map<String, String> waitForContainerStateRunning( Set<String> containerSet, int retrySeconds, int retryNum ) {

      Map<String, String> result = new HashMap<String, String>( )
      result.put( 'allRunning', false )

      boolean done= false
      int count = 0

      // loop local variables
      boolean proceed // current container in 'running' state
      String currentContainer
      String containerState
      Map<String, String> queryResult

      while ( !done ) {

         proceed = true

         Iterator it = containerSet.iterator( )

         while ( proceed && it.hasNext( ) ) {

            proceed = false

            currentContainer = it.next( )

            queryResult = getContainerState( currentContainer )
            containerState = queryResult.get( 'state' )

            if ( containerState.equalsIgnoreCase( 'running' ) ) {
               proceed = true
            } else if (
               containerState.equalsIgnoreCase( 'paused' )   ||
               containerState.equalsIgnoreCase( 'dead' )     ||
               containerState.equalsIgnoreCase( 'exited' ) ) {
               // implicit proceed = false
               done = true
               result.put( 'status', 'container-not-running' )
               result.put( 'reason', 'Container state ' + containerState )
               result.put( 'container', currentContainer )
            }
            // implicit else, which ignores the following states
               // implicit proceed = false
               // container state may be {created, restarting, not-found, error}
         }


         if ( !done && !it.hasNext( ) && proceed ) {
            done = true
            result.put( 'allRunning', true )
         } else if ( done ) {
            // catches failure cases and data provided above, preventing the next 'else' with 'Num retries' from over-writing
         } else {

            count++

            if ( count == retryNum + 1 ) {
               done = true
               result.put( 'status', 'num-retries-exceeded' )
               result.put( 'container', currentContainer )
            } else {
               Thread.sleep( retrySeconds * 1000 )
            }

         }

      }


      return( result )

   }


   private DockerUtils( ) { }
}
