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

package com.kineticfire.test

import groovy.transform.CompileStatic

/**
 * Validator for HTTP operations
 * Used to verify web services are responding correctly
 */
@CompileStatic
class HttpValidator {

    /**
     * Make HTTP GET request and return response code
     */
    static int getResponseCode(String urlString, int timeoutMs = 5000) {
        def url = new URL(urlString)
        def connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.requestMethod = 'GET'

        try {
            connection.connect()
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Make HTTP GET request and return response body
     */
    static String getResponseBody(String urlString, int timeoutMs = 5000) {
        def url = new URL(urlString)
        def connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.requestMethod = 'GET'

        try {
            connection.connect()
            if (connection.responseCode == 200) {
                return connection.inputStream.text
            } else {
                return connection.errorStream?.text ?: "HTTP ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Check if HTTP service is responding with 200 OK
     */
    static boolean isServiceResponding(String host, int port, String path = '/', int timeoutMs = 5000) {
        try {
            def url = "http://${host}:${port}${path}"
            def responseCode = getResponseCode(url, timeoutMs)
            return responseCode == 200
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Wait for HTTP service to respond with 200 OK
     */
    static boolean waitForService(String host, int port, String path = '/',
                                   int maxWaitSeconds = 30, int pollIntervalMs = 500) {
        def endTime = System.currentTimeMillis() + (maxWaitSeconds * 1000)

        while (System.currentTimeMillis() < endTime) {
            if (isServiceResponding(host, port, path, 2000)) {
                return true
            }
            Thread.sleep(pollIntervalMs)
        }

        return false
    }

    /**
     * Make HTTP POST request
     */
    static String post(String urlString, String body, String contentType = 'application/json',
                       int timeoutMs = 5000) {
        def url = new URL(urlString)
        def connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.requestMethod = 'POST'
        connection.doOutput = true
        connection.setRequestProperty('Content-Type', contentType)

        try {
            connection.outputStream.withWriter { writer ->
                writer.write(body)
            }

            if (connection.responseCode >= 200 && connection.responseCode < 300) {
                return connection.inputStream.text
            } else {
                throw new IOException("HTTP ${connection.responseCode}: ${connection.errorStream?.text}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
