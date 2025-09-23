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

package com.kineticfire.gradle.docker.junit.service

import spock.lang.Specification
import spock.lang.Subject

/**
 * Unit tests for DefaultSystemPropertyService to achieve 100% coverage.
 */
class DefaultSystemPropertyServiceTest extends Specification {

    @Subject
    DefaultSystemPropertyService systemPropertyService

    def setup() {
        systemPropertyService = new DefaultSystemPropertyService()
    }

    def cleanup() {
        // Clean up any test properties we set
        System.clearProperty("test.property.key")
        System.clearProperty("test.property.with.default")
        System.clearProperty("test.property.null")
        System.clearProperty("test.property.empty")
        System.clearProperty("test.property.spaces")
        System.clearProperty("test.property.unicode")
    }

    def "constructor creates instance successfully"() {
        expect:
        systemPropertyService != null
    }

    def "getProperty returns existing system property value"() {
        given:
        String key = "test.property.key"
        String expectedValue = "test-value-123"
        System.setProperty(key, expectedValue)

        when:
        String result = systemPropertyService.getProperty(key)

        then:
        result == expectedValue
    }

    def "getProperty returns null for non-existing property"() {
        given:
        String key = "test.property.nonexistent"
        // Ensure the property doesn't exist
        System.clearProperty(key)

        when:
        String result = systemPropertyService.getProperty(key)

        then:
        result == null
    }

    def "getProperty returns empty string for empty property value"() {
        given:
        String key = "test.property.empty"
        System.setProperty(key, "")

        when:
        String result = systemPropertyService.getProperty(key)

        then:
        result == ""
    }

    def "getProperty with default value returns existing property value"() {
        given:
        String key = "test.property.with.default"
        String propertyValue = "actual-value"
        String defaultValue = "default-value"
        System.setProperty(key, propertyValue)

        when:
        String result = systemPropertyService.getProperty(key, defaultValue)

        then:
        result == propertyValue
    }

    def "getProperty with default value returns default for non-existing property"() {
        given:
        String key = "test.property.nonexistent.with.default"
        String defaultValue = "default-value-123"
        // Ensure the property doesn't exist
        System.clearProperty(key)

        when:
        String result = systemPropertyService.getProperty(key, defaultValue)

        then:
        result == defaultValue
    }

    def "getProperty with default value returns empty string when property is empty"() {
        given:
        String key = "test.property.empty.with.default"
        String defaultValue = "default-value"
        System.setProperty(key, "")

        when:
        String result = systemPropertyService.getProperty(key, defaultValue)

        then:
        result == ""
    }

    def "getProperty with null default value returns null for non-existing property"() {
        given:
        String key = "test.property.nonexistent.null.default"
        String defaultValue = null
        // Ensure the property doesn't exist
        System.clearProperty(key)

        when:
        String result = systemPropertyService.getProperty(key, defaultValue)

        then:
        result == null
    }

    def "setProperty sets system property successfully"() {
        given:
        String key = "test.property.key"
        String value = "new-test-value"

        when:
        systemPropertyService.setProperty(key, value)

        then:
        System.getProperty(key) == value
    }

    def "setProperty overwrites existing property value"() {
        given:
        String key = "test.property.key"
        String oldValue = "old-value"
        String newValue = "new-value"
        System.setProperty(key, oldValue)

        when:
        systemPropertyService.setProperty(key, newValue)

        then:
        System.getProperty(key) == newValue
        System.getProperty(key) != oldValue
    }

    def "setProperty handles empty string value"() {
        given:
        String key = "test.property.empty"
        String value = ""

        when:
        systemPropertyService.setProperty(key, value)

        then:
        System.getProperty(key) == ""
    }

    def "setProperty throws NullPointerException for null value"() {
        given:
        String key = "test.property.null"
        String value = null

        when:
        systemPropertyService.setProperty(key, value)

        then:
        thrown(NullPointerException)
    }

    def "setProperty handles special characters in value"() {
        given:
        String key = "test.property.spaces"
        String value = "value with spaces and !@#\$%^&*() special chars"

        when:
        systemPropertyService.setProperty(key, value)

        then:
        System.getProperty(key) == value
    }

    def "setProperty handles unicode characters in value"() {
        given:
        String key = "test.property.unicode"
        String value = "测试值 🚀 αβγ"

        when:
        systemPropertyService.setProperty(key, value)

        then:
        System.getProperty(key) == value
    }

    def "all methods work together in sequence"() {
        given:
        String key = "test.property.sequence"
        String defaultValue = "default"
        String firstValue = "first"
        String secondValue = "second"

        when: "Property doesn't exist initially"
        String result1 = systemPropertyService.getProperty(key)
        String result2 = systemPropertyService.getProperty(key, defaultValue)

        then:
        result1 == null
        result2 == defaultValue

        when: "Set property to first value"
        systemPropertyService.setProperty(key, firstValue)
        String result3 = systemPropertyService.getProperty(key)
        String result4 = systemPropertyService.getProperty(key, defaultValue)

        then:
        result3 == firstValue
        result4 == firstValue

        when: "Update property to second value"
        systemPropertyService.setProperty(key, secondValue)
        String result5 = systemPropertyService.getProperty(key)

        then:
        result5 == secondValue

        cleanup:
        System.clearProperty(key)
    }

    def "getProperty handles various key formats"() {
        expect:
        // Test with existing system properties that should always be available
        systemPropertyService.getProperty("java.version") != null
        systemPropertyService.getProperty("java.home") != null
        systemPropertyService.getProperty("user.dir") != null

        // Test with non-existing properties
        systemPropertyService.getProperty("non.existent.property") == null
        systemPropertyService.getProperty("a") == null
    }

    def "setProperty and getProperty work with edge case keys"() {
        given:
        String[] testKeys = [
            "a",
            "test.key.with.many.dots",
            "test_key_with_underscores",
            "test-key-with-hyphens",
            "testKeyWithCamelCase"
        ]

        expect:
        testKeys.each { key ->
            String value = "value-for-${key}"
            systemPropertyService.setProperty(key, value)
            assert systemPropertyService.getProperty(key) == value
            assert systemPropertyService.getProperty(key, "default") == value
            System.clearProperty(key) // Clean up
        }
    }
}