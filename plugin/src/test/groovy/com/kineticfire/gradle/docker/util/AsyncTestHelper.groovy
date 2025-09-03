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

package com.kineticfire.gradle.docker.util

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Utility class for testing CompletableFuture scenarios including timeouts,
 * cancellation, exception handling, and concurrent operations.
 */
class AsyncTestHelper {
    
    /**
     * Waits for a CompletableFuture to complete within the specified timeout
     */
    static <T> T waitFor(CompletableFuture<T> future, Duration timeout = Duration.ofSeconds(5)) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (TimeoutException e) {
            future.cancel(true)
            throw new AssertionError("CompletableFuture did not complete within ${timeout}", e)
        } catch (ExecutionException e) {
            // Unwrap the cause for cleaner test assertions
            throw e.cause ?: e
        }
    }
    
    /**
     * Asserts that a CompletableFuture completes successfully within timeout
     */
    static <T> T assertCompletes(CompletableFuture<T> future, Duration timeout = Duration.ofSeconds(5)) {
        return waitFor(future, timeout)
    }
    
    /**
     * Asserts that a CompletableFuture completes with an exception of the specified type
     */
    static <T extends Throwable> T assertFailsWith(CompletableFuture<?> future, Class<T> exceptionType, Duration timeout = Duration.ofSeconds(5)) {
        try {
            def result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            throw new AssertionError("Expected CompletableFuture to fail with ${exceptionType.simpleName}, but it completed successfully with: ${result}")
        } catch (ExecutionException e) {
            def cause = e.cause
            if (exceptionType.isInstance(cause)) {
                return exceptionType.cast(cause)
            } else {
                throw new AssertionError("Expected exception type ${exceptionType.simpleName}, but got ${cause?.class?.simpleName}: ${cause?.message}", cause)
            }
        } catch (TimeoutException e) {
            future.cancel(true)
            throw new AssertionError("CompletableFuture did not complete within ${timeout}", e)
        } catch (Exception e) {
            if (exceptionType.isInstance(e)) {
                return exceptionType.cast(e)
            } else {
                throw new AssertionError("Expected exception type ${exceptionType.simpleName}, but got ${e.class.simpleName}: ${e.message}", e)
            }
        }
    }
    
    /**
     * Asserts that a CompletableFuture times out and does not complete
     */
    static void assertTimesOut(CompletableFuture<?> future, Duration timeout = Duration.ofMillis(100)) {
        try {
            def result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            throw new AssertionError("Expected CompletableFuture to timeout, but it completed with: ${result}")
        } catch (TimeoutException expected) {
            // This is what we expect
            future.cancel(true)
        } catch (Exception e) {
            throw new AssertionError("Expected timeout, but got exception: ${e.class.simpleName}: ${e.message}", e)
        }
    }
    
    /**
     * Asserts that a CompletableFuture can be cancelled
     */
    static void assertCancellable(CompletableFuture<?> future) {
        boolean cancelled = future.cancel(true)
        if (!cancelled && !future.isDone()) {
            throw new AssertionError("CompletableFuture could not be cancelled and is not done")
        }
        assert future.isCancelled() || future.isDone()
    }
    
    /**
     * Creates a CompletableFuture that completes after the specified delay
     */
    static <T> CompletableFuture<T> completesAfter(T value, Duration delay) {
        return CompletableFuture.supplyAsync({
            try {
                Thread.sleep(delay.toMillis())
                return value
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                throw new RuntimeException(e)
            }
        })
    }
    
    /**
     * Creates a CompletableFuture that fails after the specified delay
     */
    static CompletableFuture<Void> failsAfter(Exception exception, Duration delay) {
        return CompletableFuture.runAsync({
            try {
                Thread.sleep(delay.toMillis())
                throw new RuntimeException(exception)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                throw new RuntimeException(e)
            }
        })
    }
    
    /**
     * Creates a CompletableFuture that never completes (for timeout testing)
     */
    static <T> CompletableFuture<T> neverCompletes() {
        return new CompletableFuture<T>()
    }
    
    /**
     * Test multiple futures completing concurrently
     */
    static <T> List<T> waitForAll(List<CompletableFuture<T>> futures, Duration timeout = Duration.ofSeconds(10)) {
        def allOf = CompletableFuture.allOf(futures as CompletableFuture[])
        waitFor(allOf, timeout)
        return futures.collect { it.get() }
    }
    
    /**
     * Test that any of the futures completes within timeout
     */
    static <T> T waitForAny(List<CompletableFuture<T>> futures, Duration timeout = Duration.ofSeconds(10)) {
        def anyOf = CompletableFuture.anyOf(futures as CompletableFuture[])
        return waitFor(anyOf, timeout) as T
    }
    
    /**
     * Test exception propagation in CompletableFuture chains
     */
    static void assertExceptionPropagation(CompletableFuture<?> future, Class<? extends Throwable> expectedType) {
        def chainedFuture = future
            .thenApply { result -> "processed: $result" }
            .thenApply { result -> result.toUpperCase() }
        
        assertFailsWith(chainedFuture, expectedType)
    }
    
    /**
     * Verify that cleanup code runs even if future is cancelled
     */
    static void testCancellationCleanup(CompletableFuture<?> future, Runnable cleanup) {
        future.whenComplete { result, throwable ->
            if (future.isCancelled()) {
                cleanup.run()
            }
        }
        
        future.cancel(true)
        
        // Give cleanup a moment to run
        Thread.sleep(50)
    }
    
    /**
     * Test CompletableFuture composition and chaining
     */
    static <T, U> CompletableFuture<U> testChaining(
        CompletableFuture<T> initial,
        java.util.function.Function<T, CompletableFuture<U>> mapper
    ) {
        return initial.thenCompose(mapper)
    }
    
    /**
     * Test parallel execution of multiple operations
     */
    static <T> CompletableFuture<List<T>> testParallelExecution(List<java.util.function.Supplier<T>> operations) {
        def futures = operations.collect { operation ->
            CompletableFuture.supplyAsync(operation)
        }
        
        return CompletableFuture.allOf(futures as CompletableFuture[])
            .thenApply { ignored -> futures.collect { it.get() } }
    }
}