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
 * <p>
 * The methods execute a given command, passed as an argument to the method, and then return the String output of that command.  The methods differ in return types, error handling, and the type of argument for the command.
 * <p>
 * Two types of methods, differing in error handling and return values, are available to execute commands:
 * <ol>
 *   <li>exec(...) returns a Map of the result of the command that includes a boolean value if the command was successful or not based on the exit value, the integer exit value, the output as a String, and (if an error did occur) the error output as a String.</li>
 *   <li>execWithException(...) returns the output of the command as a String or, if an error occurred, throws an exception.  The exit value and error output are captured in the exception's message.</li>
 * </ol>
 * <p>
 * Methods accepting two types of arguments for the command to execute are available:
 * <ol>
 *   <li>argument 'String' is generally simpler of the two methods to use, however complex commands/arguments may not work as expected</li>
 *   <li>argument 'String[]' will work as expected for more complex commands/arguments</li>
 * </ol>
 *
 */
final class ExecUtils {


   /**
    * Executes the command as a command line process under the current working directory using Groovy's String.execute() method, and returns a String result on success or throws an exception on failure.
    * <p>
    * Returns the output returned by the process as a trimmed String, which could be an empty String if the command didn't generate output.
    * <p>
    * This method is generally simpler to use than than the 'execWithException( String[] task )', however that method may be required over this one for complex commands/arguments.  This method is similar to 'exec( String task ), except that method will return a Map with results (success or failure) while this method returns a String on success and throws an exception on failure.
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
    *    <ol>success - boolean true if the exec process was successful (exitValue is 0) and false otherwise (exitValue is non-zero)</ol>
    *    <ol>exitValue - the integer exit value returned by the process on the range of [0,255]; 0 for success and other values indicate an error</ol>
    *    <ol>out - the output returned by the process as a trimmed String, which could be an empty String</ol>
    *    <ol>err - contains the error output returned by the process as a trimmed String; only present if an an error occurred e.g. exitValue is non-zero</ol>
    * </ul>
    * <p>
    * This method is generally simpler to use than than the 'exec( String[] task )', however that method may be required over this one for complex commands/arguments.  This method is similar to 'execWithException( String task )', except that method returns a String result on success and throws an exception on failure while this method returns a Map with results (success or failure).
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
    * Returns the output returned by the process as a trimmed String, which could be an empty String if the command didn't generate output.
    * <p>
    * This method is needed to use over the simpler 'exec( String task )' when using complex commands/arguments.  This method is similar to 'exec( String[] task )', except that method returns a Map with results (success or failure) while this method returns a String result on succcess and throws an exception on failure.
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
    * Returns a result as a Map with key-value pairs:
    * <ul>
    *    <ol>success - boolean true if the exec process was successful (exitValue is 0) and false otherwise (exitValue is non-zero)</ol>
    *    <ol>exitValue - the integer exit value returned by the process on the range of [0,255]; 0 for success and other values indicate an error</ol>
    *    <ol>out - the output returned by the process as a trimmed String, which could be an empty String</ol>
    *    <ol>err - contains the error output returned by the process as a trimmed String; only present if an an error occurred e.g. exitValue is non-zero</ol>
    * </ul>
    * <p>
    * This method is needed to use over the simpler 'exec( String task )' when using complex commands/arguments. This method is similar to 'execWithException( String[] task )', except that method returns a String result on success and an exception on failure while this method returns a Map with results (success or failure).
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


   private ExecUtils( ) { }
}
