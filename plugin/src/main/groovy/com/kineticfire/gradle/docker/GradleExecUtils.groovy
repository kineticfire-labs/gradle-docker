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


import java.io.IOException

import java.util.Map
import java.util.HashMap



/**
 * Provides command line execution utilities.
 *
 */
final class GradleExecUtils {


   /**
    * Executes the command as a command line process under the current working directory using Groovy's String.execute() method, and returns a String result on success or throws an exception on failure.
    * <p>
    * Returns the trimmed output returned by the process as a String, which could be an empty String.
    * <p>
    * This method is generally simpler to use than than the 'execWithException( String[] task )', however that method may be required over this one when using arguments that have spaces or wildcards; and also similar to 'exec( String task ), except that method will return a Map with results while this method returns a String on success and throws an exception on failure.
    *
    * @param task
    *    the task (or command) to execute as a String
    * @return a String result of the command execution's output
    * @throws IOException
    *    if the task execution returned a non-zero exit value
    */
   static String execWithException( String task ) { 

      def resultMap = exec( task )

      if ( resultMap.get( 'exitValue' ) != 0 ) {

         StringBuffer sb = new StringBuffer( )

         sb.append( 'Executing command "' + task + '" failed with exit value ' + resultMap.get( 'exitValue' ) + '.' )

         if ( !resultMap.get( 'err' ).equals( '' ) ) {
            sb.append( '  ' + resultMap.get( 'err' ) )
         }

         throw new IOException( sb.toString( ) )
      }

      return( resultMap.get( 'out' ) )
   }


   /**
    * Executes the command as a command line process under the current working directory using Groovy's String.execute() method, and returns a Map result.
    * <p>
    * Returns a result as a Map with key-value pairs:
    * <ul>
    *    <ol>success - boolean true (exitValue is 0) if the exec process was successful and false otherwise (exitValue is non-zero)</ol>
    *    <ol>exitValue - the integer exit value returned by the process in range of [0,255]; 0 for success and other value indicates error</ol>
    *    <ol>out - the trimmed output returned by the process as a String, which could be an empty String</ol>
    *    <ol>err - contains the error output returned by the process as a String; only present if an an error occurred e.g. exitValue is non-zero</ol>
    * </ul>
    * <p>
    * This method is generally simpler to use than than the 'exec( String[] task )', however that method may be required over this one when using arguments that have spaces or wildcards; and also similar to 'execWithException( String task )', except that method returns a String result on success and throws an exception on failure while this method returns a Map with results.
    *
    * @param task
    *    the task (or command) to execute as a String
    * @return a Map of the result of the command execution
    */
   static def exec( String task ) { 

      def resultMap = [:]

      StringBuffer sout = new StringBuffer( )
      StringBuffer serr = new StringBuffer( )

      Process proc = task.execute( )
      proc.waitForProcessOutput( sout, serr )


      int exitValue = proc.exitValue( )
      resultMap.put( 'exitValue', exitValue )

      resultMap.put( 'out', sout.toString( ).trim( ) ) 

      if ( exitValue == 0 ) { 
         resultMap.success = true
      } else {
         resultMap.success = false
         resultMap.put( 'err', serr.toString( ).trim( ) ) 
      }   


      return( resultMap )

   }


   /**
    * Executes the command as a command line process under the current working directory using Groovy's String[].execute() method, and either returns a String result on success or throws an exception on failure.
    * <p>
    * Calls Groovy's toString() method on each item in the array.  The first item in the array is treated as the command and executed with Groovy's String.execute() method and any additional array items are treated as parameters.
    * <p>
    * Returns the trimmed output returned by the process as a String, which could be an empty String.
    * <p>
    * This method is needed to use over the simpler 'exec( String task )' when using arguments that have spaces or wildcards; and is similar to 'exec( String[] task )', except that method returns a Map with results while this method returns a String result on succcess and throws an exception on failure.
    *
    * @param task
    *    the task (or command) to execute as a String array, where the first item is the command and any subsequent items are arguments
    * @return a String result of the command execution's output
    * @throws IOException
    *    if the task execution returned a non-zero exit value
    */
   static String execWithException( String[] task ) { 

      def resultMap = exec( task )

      if ( resultMap.get( 'exitValue' ) != 0 ) {

         StringBuffer sb = new StringBuffer( )

         sb.append( 'Executing command "' + task + '" failed with exit value ' + resultMap.get( 'exitValue' ) + '.' )

         if ( !resultMap.get( 'err' ).equals( '' ) ) {
            sb.append( '  ' + resultMap.get( 'err' ) )
         }

         throw new IOException( sb.toString( ) )
      }

      return( resultMap.get( 'out' ) )
   }


   /**
    * Executes the command as a command line process under the current working directory using Groovy's String[].execute() method, and returns a Map result.
    * <p>
    * Calls Groovy's toString() method on each item in the array.  The first item in the array is treated as the command and executed with Groovy's String.execute() method and any additional array items are treated as parameters.
    * <p>
    * Returns a result as a Map<String,String> with key-value pairs:
    * <ul>
    *    <ol>success - boolean true (exitValue is 0) if the exec process was successful and false otherwise (exitValue is non-zero)</ol>
    *    <ol>exitValue - the integer exit value returned by the process in range of [0,255]; 0 for success and other value indicates error</ol>
    *    <ol>out - the trimmed output returned by the process as a String, which could be an empty String</ol>
    *    <ol>err - contains the error output returned by the process as a String; only present if an an error occurred, e.g. exitValue is non-zero</ol>
    * </ul>
    * <p>
    * This method is needed to use over the simpler 'exec( String task )' when using arguments that have spaces or wildcards; and is similar to 'execWithException( String[] task )', except that method returns a String result on success and a Map on failure while this method returns a Map with results.
    *
    * @param task
    *    the task (or command) to execute as a String array, where the first item is the command and any subsequent items are arguments
    * @return a Map of the result of the command execution
    */
   static def exec( String[] task ) { 

      def resultMap = [:]

      StringBuffer sout = new StringBuffer( )
      StringBuffer serr = new StringBuffer( )

      Process proc = task.execute( )
      proc.waitForProcessOutput( sout, serr )

      int exitValue = proc.exitValue( )
      resultMap.put( 'exitValue', exitValue )

      resultMap.put( 'out', sout.toString( ).trim( ) ) 

      if ( exitValue == 0 ) { 
         resultMap.success = true
      } else {
         resultMap.success = false
         resultMap.put( 'err', serr.toString( ).trim( ) ) 
      }   


      return( resultMap )

   }


   private GradleExecUtils( ) { }
}
