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

import com.github.dockerjava.api.model.BuildResponseItem
import com.github.dockerjava.api.model.PushResponseItem
import com.github.dockerjava.api.model.PullResponseItem
import groovy.transform.CompileStatic

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Utility for verifying callback invocations and streaming responses in Docker operations.
 * Provides mechanisms to capture, verify, and assert on callback behavior.
 */
@CompileStatic
class CallbackTestVerifier {
    
    /**
     * Captures build callback invocations for verification
     */
    static class BuildCallbackCapture {
        private final List<BuildResponseItem> items = Collections.synchronizedList([])
        private final List<String> streamMessages = Collections.synchronizedList([])
        private final List<String> errorMessages = Collections.synchronizedList([])
        private final CountDownLatch completionLatch = new CountDownLatch(1)
        private volatile boolean completed = false
        private volatile Throwable error = null
        
        void onNext(BuildResponseItem item) {
            items.add(item)
            if (item.stream) {
                streamMessages.add(item.stream.trim())
            }
            if (item.error) {
                errorMessages.add(item.error)
            }
            if (item.errorDetail) {
                errorMessages.add(item.errorDetail.message)
            }
        }
        
        void onComplete() {
            completed = true
            completionLatch.countDown()
        }
        
        void onError(Throwable throwable) {
            error = throwable
            completionLatch.countDown()
        }
        
        // Verification methods
        
        boolean waitForCompletion(Duration timeout = Duration.ofSeconds(5)) {
            return completionLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)
        }
        
        void assertCompleted() {
            assert completed : "Callback should have completed"
            assert error == null : "Callback should not have errored: $error"
        }
        
        void assertErrored(Class<? extends Throwable> expectedType = null) {
            assert error != null : "Callback should have errored"
            if (expectedType) {
                assert expectedType.isInstance(error) : 
                    "Expected error type ${expectedType.simpleName}, got ${error.class.simpleName}"
            }
        }
        
        void assertStreamContains(String expectedMessage) {
            assert streamMessages.any { it.contains(expectedMessage) } : 
                "Stream should contain '$expectedMessage'. Actual messages: $streamMessages"
        }
        
        void assertStreamMessagesReceived(int expectedCount) {
            assert streamMessages.size() >= expectedCount : 
                "Expected at least $expectedCount stream messages, got ${streamMessages.size()}"
        }
        
        void assertNoErrors() {
            assert errorMessages.isEmpty() : "Expected no error messages, got: $errorMessages"
        }
        
        void assertErrorsContain(String expectedError) {
            assert errorMessages.any { it.contains(expectedError) } :
                "Expected error message containing '$expectedError'. Actual errors: $errorMessages"
        }
        
        void assertItemsReceived(int expectedCount) {
            assert items.size() >= expectedCount :
                "Expected at least $expectedCount items, got ${items.size()}"
        }
        
        List<BuildResponseItem> getItems() { return new ArrayList<>(items) }
        List<String> getStreamMessages() { return new ArrayList<>(streamMessages) }
        List<String> getErrorMessages() { return new ArrayList<>(errorMessages) }
        boolean isCompleted() { return completed }
        Throwable getError() { return error }
    }
    
    /**
     * Captures push callback invocations for verification
     */
    static class PushCallbackCapture {
        private final List<PushResponseItem> items = Collections.synchronizedList([])
        private final List<String> statusMessages = Collections.synchronizedList([])
        private final CountDownLatch completionLatch = new CountDownLatch(1)
        private volatile boolean completed = false
        private volatile Throwable error = null
        
        void onNext(PushResponseItem item) {
            items.add(item)
            if (item.status) {
                statusMessages.add(item.status)
            }
        }
        
        void onComplete() {
            completed = true
            completionLatch.countDown()
        }
        
        void onError(Throwable throwable) {
            error = throwable
            completionLatch.countDown()
        }
        
        // Verification methods
        
        boolean waitForCompletion(Duration timeout = Duration.ofSeconds(5)) {
            return completionLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)
        }
        
        void assertCompleted() {
            assert completed : "Push callback should have completed"
            assert error == null : "Push callback should not have errored: $error"
        }
        
        void assertErrored(Class<? extends Throwable> expectedType = null) {
            assert error != null : "Push callback should have errored"
            if (expectedType) {
                assert expectedType.isInstance(error) :
                    "Expected error type ${expectedType.simpleName}, got ${error.class.simpleName}"
            }
        }
        
        void assertStatusContains(String expectedStatus) {
            assert statusMessages.any { it.contains(expectedStatus) } :
                "Status should contain '$expectedStatus'. Actual status messages: $statusMessages"
        }
        
        void assertProgressStages(List<String> expectedStages) {
            expectedStages.each { stage ->
                assert statusMessages.any { it.toLowerCase().contains(stage.toLowerCase()) } :
                    "Expected progress stage '$stage' not found. Actual messages: $statusMessages"
            }
        }
        
        List<PushResponseItem> getItems() { return new ArrayList<>(items) }
        List<String> getStatusMessages() { return new ArrayList<>(statusMessages) }
        boolean isCompleted() { return completed }
        Throwable getError() { return error }
    }
    
    /**
     * Captures pull callback invocations for verification
     */
    static class PullCallbackCapture {
        private final List<PullResponseItem> items = Collections.synchronizedList([])
        private final List<String> statusMessages = Collections.synchronizedList([])
        private final CountDownLatch completionLatch = new CountDownLatch(1)
        private volatile boolean completed = false
        private volatile Throwable error = null
        
        void onNext(PullResponseItem item) {
            items.add(item)
            if (item.status) {
                statusMessages.add(item.status)
            }
        }
        
        void onComplete() {
            completed = true
            completionLatch.countDown()
        }
        
        void onError(Throwable throwable) {
            error = throwable
            completionLatch.countDown()
        }
        
        // Verification methods
        
        boolean waitForCompletion(Duration timeout = Duration.ofSeconds(5)) {
            return completionLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)
        }
        
        void assertCompleted() {
            assert completed : "Pull callback should have completed"
            assert error == null : "Pull callback should not have errored: $error"
        }
        
        void assertErrored(Class<? extends Throwable> expectedType = null) {
            assert error != null : "Pull callback should have errored"
            if (expectedType) {
                assert expectedType.isInstance(error) :
                    "Expected error type ${expectedType.simpleName}, got ${error.class.simpleName}"
            }
        }
        
        void assertStatusContains(String expectedStatus) {
            assert statusMessages.any { it.contains(expectedStatus) } :
                "Status should contain '$expectedStatus'. Actual status messages: $statusMessages"
        }
        
        void assertLayerOperations(List<String> expectedOperations = ["Pulling", "Download complete", "Pull complete"]) {
            expectedOperations.each { operation ->
                assert statusMessages.any { it.contains(operation) } :
                    "Expected layer operation '$operation' not found. Actual messages: $statusMessages"
            }
        }
        
        List<PullResponseItem> getItems() { return new ArrayList<>(items) }
        List<String> getStatusMessages() { return new ArrayList<>(statusMessages) }
        boolean isCompleted() { return completed }
        Throwable getError() { return error }
    }
    
    /**
     * Generic callback verifier for custom callback types
     */
    static class GenericCallbackCapture<T> {
        private final List<T> items = Collections.synchronizedList([])
        private final CountDownLatch completionLatch = new CountDownLatch(1)
        private volatile boolean completed = false
        private volatile Throwable error = null
        
        void onNext(T item) {
            items.add(item)
        }
        
        void onComplete() {
            completed = true
            completionLatch.countDown()
        }
        
        void onError(Throwable throwable) {
            error = throwable
            completionLatch.countDown()
        }
        
        // Verification methods
        
        boolean waitForCompletion(Duration timeout = Duration.ofSeconds(5)) {
            return completionLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)
        }
        
        void assertCompleted() {
            assert completed : "Generic callback should have completed"
            assert error == null : "Generic callback should not have errored: $error"
        }
        
        void assertErrored(Class<? extends Throwable> expectedType = null) {
            assert error != null : "Generic callback should have errored"
            if (expectedType) {
                assert expectedType.isInstance(error) :
                    "Expected error type ${expectedType.simpleName}, got ${error.class.simpleName}"
            }
        }
        
        void assertItemCount(int expectedCount) {
            assert items.size() == expectedCount :
                "Expected $expectedCount items, got ${items.size()}"
        }
        
        void assertMinimumItemCount(int minimumCount) {
            assert items.size() >= minimumCount :
                "Expected at least $minimumCount items, got ${items.size()}"
        }
        
        void assertItemsMatch(Closure<Boolean> predicate) {
            assert items.every(predicate) :
                "Not all items match the predicate. Items: $items"
        }
        
        List<T> getItems() { return new ArrayList<>(items) }
        boolean isCompleted() { return completed }
        Throwable getError() { return error }
    }
    
    /**
     * Utility methods for common callback verification scenarios
     */
    static class Scenarios {
        
        static void verifySuccessfulBuild(BuildCallbackCapture capture, String expectedImageId = null) {
            assert capture.waitForCompletion() : "Build callback did not complete within timeout"
            capture.assertCompleted()
            capture.assertNoErrors()
            capture.assertStreamMessagesReceived(1)
            
            if (expectedImageId) {
                capture.assertStreamContains(expectedImageId)
            }
        }
        
        static void verifyFailedBuild(BuildCallbackCapture capture, String expectedError) {
            assert capture.waitForCompletion() : "Build callback did not complete within timeout"
            capture.assertErrored()
            capture.assertErrorsContain(expectedError)
        }
        
        static void verifySuccessfulPush(PushCallbackCapture capture) {
            assert capture.waitForCompletion() : "Push callback did not complete within timeout"
            capture.assertCompleted()
            capture.assertProgressStages(["preparing", "pushing", "pushed"])
        }
        
        static void verifyFailedPush(PushCallbackCapture capture, String expectedError = "authentication") {
            assert capture.waitForCompletion() : "Push callback did not complete within timeout"
            capture.assertErrored()
            // Error details are typically in the error throwable, not status messages
        }
        
        static void verifySuccessfulPull(PullCallbackCapture capture) {
            assert capture.waitForCompletion() : "Pull callback did not complete within timeout"
            capture.assertCompleted()
            capture.assertLayerOperations()
        }
        
        static void verifyProgressSequence(List<String> actualMessages, List<String> expectedSequence) {
            def messageIndex = 0
            expectedSequence.each { expectedMsg ->
                def found = false
                while (messageIndex < actualMessages.size()) {
                    if (actualMessages[messageIndex].contains(expectedMsg)) {
                        found = true
                        break
                    }
                    messageIndex++
                }
                assert found : "Expected message '$expectedMsg' not found in sequence after previous messages"
                messageIndex++ // Move to next message for next search
            }
        }
    }
}