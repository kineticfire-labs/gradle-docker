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
 * 
 */
final class GradleExecUtils {


    /*
    todo:  move to other utils

    username = System.properties[ 'user.name' ]
    uid = [ "id", "-u", username ].execute( ).text.trim( )
    */


    // generally simpler to use than the 'String[] task' version, but can't use when task has arguments that have spaces or wildcards to then use 'String[] task' version
    static Map<String, String> exec( String task ) { 

       Map<String, String> result = new HashMap<String, String>( )

       StringBuffer sout = new StringBuffer( )
       StringBuffer serr = new StringBuffer( )

       Process proc = task.execute( )
       proc.consumeProcessOutput( sout, serr )
       proc.waitFor( )

       int exitValue = proc.exitValue( )
       result.put( 'exitValue', exitValue )

       result.put( 'out', sout.toString( ).trim( ) ) 

       if ( exitValue < 0 || exitValue > 0 ) { 
          result.put( 'err', serr.toString( ).trim( ) ) 
       }   


       return( result )

    }


    // use when task has arguments that have spaces or wildcards
    static Map<String, String> exec( String[] task ) { 

       Map<String, String> result = new HashMap<String, String>( )

       StringBuffer sout = new StringBuffer( )
       StringBuffer serr = new StringBuffer( )

       Process proc = task.execute( )
       proc.consumeProcessOutput( sout, serr )
       proc.waitFor( )

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
