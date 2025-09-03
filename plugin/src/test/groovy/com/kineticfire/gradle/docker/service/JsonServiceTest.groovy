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

package com.kineticfire.gradle.docker.service

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for JsonService interface
 */
class JsonServiceTest extends Specification {

    @TempDir
    Path tempDir

    TestJsonService service = new TestJsonService()

    def "JsonService interface can be implemented"() {
        expect:
        service instanceof JsonService
    }

    def "toJson method signature is correct"() {
        given:
        def testObject = [name: "test", value: 42]

        when:
        def result = service.toJson(testObject)

        then:
        result instanceof String
        result.contains("test")
        result.contains("42")
    }

    def "fromJson method signature is correct"() {
        given:
        def json = '{"name":"test","value":42}'
        def targetClass = Map

        when:
        def result = service.fromJson(json, targetClass)

        then:
        result instanceof Map
        result.name == "test"
        result.value == 42
    }

    def "parseJsonArray method signature is correct"() {
        given:
        def jsonArray = '[{"name":"test1"},{"name":"test2"}]'

        when:
        def result = service.parseJsonArray(jsonArray)

        then:
        result instanceof List
        result.size() == 2
        result[0] instanceof Map
    }

    def "toJson handles null object"() {
        when:
        def result = service.toJson(null)

        then:
        result == "null"
    }

    def "fromJson handles empty string"() {
        when:
        def result = service.fromJson("", Map)

        then:
        result instanceof Map
        result.isEmpty()
    }

    def "interface methods exist with correct signatures"() {
        expect:
        JsonService.getDeclaredMethods().find { it.name == 'toJson' }.returnType == String
        JsonService.getDeclaredMethods().find { it.name == 'fromJson' }.returnType == Object
        JsonService.getDeclaredMethods().find { it.name == 'parseJsonArray' }.returnType == List
    }

    /**
     * Test implementation of JsonService for interface testing
     */
    static class TestJsonService implements JsonService {

        @Override
        String toJson(Object object) {
            if (object == null) return "null"
            return "{\"name\":\"test\",\"value\":42}"
        }

        @Override
        Object fromJson(String json, Class<?> targetClass) {
            if (json == null || json.trim().isEmpty()) {
                return [:]
            }
            return [name: "test", value: 42]
        }

        @Override
        List<Map<String, Object>> parseJsonArray(String json) {
            return [
                [name: "test1"],
                [name: "test2"]
            ]
        }
    }
}