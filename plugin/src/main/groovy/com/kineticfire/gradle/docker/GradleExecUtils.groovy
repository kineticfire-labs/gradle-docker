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



/**
 * Provides command line execution utilities.
 *
 */
final class GradleExecUtils {


   /**
    * Executes the command as a command line process under the current working directory using Groovy's String.execute() method.
    * <p>
    * Returns a result as a Map<String,String> with key-value pairs:
    * <ul>
    *    <ol>exitValue - the integer exit value returned by the process in range of [0,255]; 0 for success and other value indicates error</ol>
    *    <ol>out - the trimmed output returned by the process, which could be an empty string</ol>
    *    <ol>err - not present if an error didn't occur; if an error occurred, then contains the error output returned by the process</ol>
    * </ul>
    * <p>
    * This method is generally simpler to use than than the 'exec( String[] task )', however that method may be required over this one when using arguments that have spaces or wildcards.
    *
    * @param task
    *    the task (or command) to execute as a String
    * @return a Map of the result of the command execution
    */
   static Map<String, String> exec( String task ) { 

      Map<String, String> result = new HashMap<String, String>( )

      StringBuffer sout = new StringBuffer( )
      StringBuffer serr = new StringBuffer( )

      Process proc = task.execute( )
      proc.waitForProcessOutput( sout, serr )


      int exitValue = proc.exitValue( )
      result.put( 'exitValue', exitValue )

      result.put( 'out', sout.toString( ).trim( ) ) 

      if ( exitValue < 0 || exitValue > 0 ) { 
         result.put( 'err', serr.toString( ).trim( ) ) 
      }   


      return( result )

   }


   /**
    * Executes the command as a command line process under the current working directory using Groovy's String[].execute() method.
    * <p>
    * Calls Groovy's toString() method on each item in the array.  The first item in the array is treated as the command and executed with Groovy's String.execute() method and any additional array items are treated as parameters.
    * <p>
    * Returns a result as a Map<String,String> with key-value pairs:
    * <ul>
    *    <ol>exitValue - the integer exit value returned by the process in range of [0,255]; 0 for success and other value indicates error</ol>
    *    <ol>out - the trimmed output returned by the process, which could be an empty string</ol>
    *    <ol>err - not present if an error didn't occur; if an error occurred, then contains the error output returned by the process</ol>
    * </ul>
    * <p>
    * This method is needed to use over the simpler 'exec( String task )' when using arguments that have spaces or wildcards.
    *
    * @param task
    *    the task (or command) to execute as a String array, where the first item is the command and any subsequent items are arguments
    * @return a Map of the result of the command execution
    */
   static Map<String, String> exec( String[] task ) { 

      Map<String, String> result = new HashMap<String, String>( )

      StringBuffer sout = new StringBuffer( )
      StringBuffer serr = new StringBuffer( )

      // https://stackoverflow.com/questions/19988946/executing-many-sub-processes-in-groovy-fails

      Process proc = task.execute( )
      proc.waitForProcessOutput( sout, serr )

      int exitValue = proc.exitValue( )
      result.put( 'exitValue', exitValue )

      result.put( 'out', sout.toString( ).trim( ) ) 

      if ( exitValue < 0 || exitValue > 0 ) { 
         result.put( 'err', serr.toString( ).trim( ) ) 
      }   


      return( result )

   }


   private GradleExecUtils( ) { }
}
