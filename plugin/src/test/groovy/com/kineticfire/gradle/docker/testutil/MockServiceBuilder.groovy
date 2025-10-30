/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
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

package com.kineticfire.gradle.docker.testutil

import com.kineticfire.gradle.docker.service.FileOperations
import com.kineticfire.gradle.docker.service.TimeService

import java.nio.file.Path

/**
 * Utility class for creating mock service implementations for testing.
 * Provides builders for creating mocks with configurable behavior.
 */
class MockServiceBuilder {
    
    /**
     * Create a mock FileOperations that tracks calls without performing actual I/O.
     * Useful for testing file operations without touching the filesystem.
     *
     * @param fileContents Map of Path to String content for files that "exist"
     * @return Mock FileOperations implementation
     */
    static FileOperations createMockFileOperations(Map<Path, String> fileContents = [:]) {
        return new FileOperations() {
            private final Map<Path, String> files = new HashMap<>(fileContents)
            final List<String> methodCalls = []
            
            @Override
            void createDirectories(Path path) {
                methodCalls.add("createDirectories:${path}")
            }
            
            @Override
            void writeText(Path path, String content) {
                methodCalls.add("writeText:${path}")
                files[path] = content
            }
            
            @Override
            String readText(Path path) {
                methodCalls.add("readText:${path}")
                return files.get(path)
            }
            
            @Override
            boolean exists(Path path) {
                methodCalls.add("exists:${path}")
                return files.containsKey(path)
            }
            
            @Override
            void delete(Path path) {
                methodCalls.add("delete:${path}")
                files.remove(path)
            }
            
            @Override
            File toFile(Path path) {
                methodCalls.add("toFile:${path}")
                return path.toFile()
            }
        }
    }
    
    /**
     * Create a mock TimeService with controllable time.
     * Allows tests to control time progression without actual delays.
     *
     * @param initialTime Starting time in milliseconds
     * @return Mock TimeService implementation
     */
    static TimeService createMockTimeService(long initialTime = 0L) {
        return new TimeService() {
            private long currentTime = initialTime
            final List<String> methodCalls = []
            
            @Override
            long currentTimeMillis() {
                methodCalls.add("currentTimeMillis")
                return currentTime
            }
            
            @Override
            void sleep(long millis) {
                methodCalls.add("sleep:${millis}")
                // Advance time instead of actually sleeping
                currentTime += millis
            }
            
            /**
             * Manually advance the mock clock
             */
            void advanceTime(long millis) {
                currentTime += millis
            }
        }
    }
    
    /**
     * Create a mock TimeService that allows external control of time advancement.
     * Useful for testing timeout scenarios where time needs to jump forward.
     *
     * @return Controllable TimeService mock with time advancement capability
     */
    static ControllableTimeService createControllableTimeService(long initialTime = 0L) {
        return new ControllableTimeService(initialTime)
    }
    
    /**
     * TimeService implementation that allows tests to control time progression.
     */
    static class ControllableTimeService implements TimeService {
        private long currentTime
        final List<String> methodCalls = []
        
        ControllableTimeService(long initialTime = 0L) {
            this.currentTime = initialTime
        }
        
        @Override
        long currentTimeMillis() {
            methodCalls.add("currentTimeMillis")
            return currentTime
        }
        
        @Override
        void sleep(long millis) {
            methodCalls.add("sleep:${millis}")
            // Auto-advance time on sleep to prevent infinite loops in tests
            currentTime += millis
        }
        
        /**
         * Manually advance the mock clock
         */
        void advanceTime(long millis) {
            currentTime += millis
        }
        
        /**
         * Set the current time to a specific value
         */
        void setCurrentTime(long time) {
            currentTime = time
        }
    }
}
