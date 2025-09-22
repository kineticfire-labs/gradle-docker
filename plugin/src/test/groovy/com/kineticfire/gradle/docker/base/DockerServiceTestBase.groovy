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

package com.kineticfire.gradle.docker.base

import com.github.dockerjava.api.DockerClient
import com.kineticfire.gradle.docker.mocks.MockDockerClient
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.service.DockerServiceImpl
import org.gradle.api.services.BuildServiceParameters
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Base class for Docker service tests with common mock setup
 */
abstract class DockerServiceTestBase extends Specification {
    
    protected DockerClient mockDockerClient
    protected ExecutorService immediateExecutor
    protected TestDockerServiceImpl service
    
    def setup() {
        mockDockerClient = MockDockerClient.createSuccessfulClient()
        immediateExecutor = createImmediateExecutor()
        service = new TestDockerServiceImpl(mockDockerClient, immediateExecutor)
    }
    
    def cleanup() {
        if (immediateExecutor && !immediateExecutor.isShutdown()) {
            immediateExecutor.shutdown()
        }
    }
    
    /**
     * Creates an executor that runs tasks immediately on the calling thread
     */
    protected ExecutorService createImmediateExecutor() {
        return new ExecutorService() {
            @Override
            void shutdown() {}
            
            @Override
            List<Runnable> shutdownNow() { return [] }
            
            @Override
            boolean isShutdown() { return false }
            
            @Override
            boolean isTerminated() { return false }
            
            @Override
            boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) { return true }
            
            @Override
            def <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
                try {
                    def result = task.call()
                    return java.util.concurrent.CompletableFuture.completedFuture(result)
                } catch (Exception e) {
                    def future = new java.util.concurrent.CompletableFuture<T>()
                    future.completeExceptionally(e)
                    return future
                }
            }
            
            @Override
            def <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
                task.run()
                return java.util.concurrent.CompletableFuture.completedFuture(result)
            }
            
            @Override
            java.util.concurrent.Future<?> submit(Runnable task) {
                task.run()
                return java.util.concurrent.CompletableFuture.completedFuture(null)
            }
            
            @Override
            def <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends java.util.concurrent.Callable<T>> tasks) {
                return tasks.collect { submit(it) }
            }
            
            @Override
            def <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) {
                return invokeAll(tasks)
            }
            
            @Override
            def <T> T invokeAny(Collection<? extends java.util.concurrent.Callable<T>> tasks) {
                return tasks.first().call()
            }
            
            @Override
            def <T> T invokeAny(Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) {
                return invokeAny(tasks)
            }
            
            @Override
            void execute(Runnable command) {
                command.run()
            }
        }
    }
    
    /**
     * Test implementation of DockerServiceImpl that allows dependency injection
     */
    static class TestDockerServiceImpl implements DockerService {
        private final DockerClient injectedClient
        private final ExecutorService injectedExecutor

        TestDockerServiceImpl(DockerClient client, ExecutorService executor) {
            this.injectedClient = client
            this.injectedExecutor = executor
        }

        @Override
        java.util.concurrent.CompletableFuture<String> buildImage(com.kineticfire.gradle.docker.model.BuildContext context) {
            return java.util.concurrent.CompletableFuture.supplyAsync({
                try {
                    // Mock implementation for testing
                    return "sha256:test-image-id"
                } catch (Exception e) {
                    throw new RuntimeException(e)
                }
            }, injectedExecutor)
        }

        @Override
        java.util.concurrent.CompletableFuture<Void> tagImage(String sourceImageRef, List<String> targetTags) {
            return java.util.concurrent.CompletableFuture.runAsync({
                // Mock implementation for testing
            }, injectedExecutor)
        }

        @Override
        java.util.concurrent.CompletableFuture<Void> saveImage(String imageRef, java.nio.file.Path outputPath, com.kineticfire.gradle.docker.model.SaveCompression compression) {
            return java.util.concurrent.CompletableFuture.runAsync({
                // Mock implementation for testing
                try {
                    java.nio.file.Files.createDirectories(outputPath.parent)
                    java.nio.file.Files.write(outputPath, "mock image data".bytes)
                } catch (Exception e) {
                    throw new RuntimeException(e)
                }
            }, injectedExecutor)
        }

        @Override
        java.util.concurrent.CompletableFuture<Void> pullImage(String imageRef, com.kineticfire.gradle.docker.model.AuthConfig authConfig) {
            return java.util.concurrent.CompletableFuture.runAsync({
                // Mock implementation for testing
            }, injectedExecutor)
        }

        @Override
        java.util.concurrent.CompletableFuture<Void> pushImage(String imageRef, com.kineticfire.gradle.docker.model.AuthConfig authConfig) {
            return java.util.concurrent.CompletableFuture.runAsync({
                // Mock implementation for testing
            }, injectedExecutor)
        }

        @Override
        java.util.concurrent.CompletableFuture<Boolean> imageExists(String imageRef) {
            return java.util.concurrent.CompletableFuture.supplyAsync({
                // Mock implementation for testing - assume image exists
                return true
            }, injectedExecutor)
        }

        @Override
        void close() {
            // Mock implementation for testing
        }

        protected DockerClient getDockerClient() {
            return injectedClient
        }

        protected ExecutorService getExecutorService() {
            return injectedExecutor
        }
    }
}