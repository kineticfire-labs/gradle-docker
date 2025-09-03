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

import com.fasterxml.jackson.core.JsonProcessingException
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Comprehensive unit tests for JsonServiceImpl basic functionality.
 * Tests all core methods to achieve high coverage.
 */
class JsonServiceImplSimpleTest extends Specification {

    @TempDir
    Path tempDir

    JsonServiceImpl service

    def setup() {
        service = new TestableJsonServiceImpl()
    }

    // Constructor and ObjectMapper tests

    def "service initializes with configured ObjectMapper"() {
        when:
        def mapper = service.getObjectMapper()

        then:
        mapper != null
        !mapper.getSerializationConfig().isEnabled(
            com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
        )
    }

    // toJson method tests

    def "toJson handles null input"() {
        when:
        def result = service.toJson(null)

        then:
        result == "null"
    }

    def "toJson handles primitive values"() {
        expect:
        service.toJson("hello") == '"hello"'
        service.toJson(42) == "42"
        service.toJson(true) == "true"
        service.toJson(false) == "false"
    }

    def "toJson handles collections"() {
        expect:
        service.toJson([]) == "[]"
        service.toJson([:]) == "{}"
        service.toJson([1, 2, 3]) == "[1,2,3]"
    }

    def "toJson handles maps"() {
        when:
        def result = service.toJson([name: "test", value: 123])

        then:
        result.contains('"name":"test"')
        result.contains('"value":123')
    }

    def "toJson handles nested structures"() {
        given:
        def nested = [
            data: [values: [1, 2, 3], enabled: true],
            metadata: [name: "test"]
        ]

        when:
        def result = service.toJson(nested)

        then:
        result.contains('"data"')
        result.contains('"values":[1,2,3]')
        result.contains('"enabled":true')
        result.contains('"metadata"')
    }

    def "toJson handles special characters"() {
        when:
        def result = service.toJson("Special chars: \\n \\t \\\"")

        then:
        result.contains("\\\\n")
        result.contains("\\\\t")
        result.contains('\\\\"')
    }

    // fromJson method tests

    def "fromJson handles null and empty inputs"() {
        expect:
        service.fromJson(null, String.class) == null
        service.fromJson("", String.class) == null
        service.fromJson("   ", String.class) == null
    }

    def "fromJson handles primitive values"() {
        expect:
        service.fromJson('"hello"', String.class) == "hello"
        service.fromJson('42', Integer.class) == 42
        service.fromJson('true', Boolean.class) == true
        service.fromJson('false', Boolean.class) == false
    }

    def "fromJson handles maps"() {
        when:
        def result = service.fromJson('{"name":"test","value":123}', Map.class)

        then:
        result.name == "test"
        result.value == 123
    }

    def "fromJson handles lists"() {
        when:
        def result = service.fromJson('[1,2,3,"four"]', List.class)

        then:
        result.size() == 4
        result[0] == 1
        result[3] == "four"
    }

    def "fromJson throws exception for invalid JSON"() {
        when:
        service.fromJson("invalid json", Map.class)

        then:
        thrown(JsonProcessingException)
    }

    def "fromJson throws exception for type mismatch"() {
        when:
        service.fromJson('"not a number"', Integer.class)

        then:
        thrown(JsonProcessingException)
    }

    // parseJsonArray method tests

    def "parseJsonArray handles null and empty inputs"() {
        expect:
        service.parseJsonArray(null) == []
        service.parseJsonArray("") == []
        service.parseJsonArray("   ") == []
    }

    def "parseJsonArray handles empty array"() {
        when:
        def result = service.parseJsonArray("[]")

        then:
        result == []
    }

    def "parseJsonArray handles array of objects"() {
        when:
        def result = service.parseJsonArray('[{"id":1,"name":"one"},{"id":2,"name":"two"}]')

        then:
        result.size() == 2
        result[0].id == 1
        result[0].name == "one"
        result[1].id == 2
        result[1].name == "two"
    }

    def "parseJsonArray handles nested structures"() {
        when:
        def json = '''[
            {"service":"web","config":{"port":80},"tags":["frontend"]},
            {"service":"db","config":{"port":5432},"tags":["backend"]}
        ]'''
        def result = service.parseJsonArray(json)

        then:
        result.size() == 2
        result[0].service == "web"
        result[0].config.port == 80
        result[0].tags == ["frontend"]
        result[1].service == "db"
        result[1].config.port == 5432
    }

    def "parseJsonArray throws exception for invalid JSON"() {
        when:
        service.parseJsonArray("[invalid json")

        then:
        thrown(JsonProcessingException)
    }

    def "parseJsonArray throws exception for non-array"() {
        when:
        service.parseJsonArray('{"not":"array"}')

        then:
        thrown(JsonProcessingException)
    }

    // parseJson method tests

    def "parseJson handles null and empty inputs"() {
        expect:
        service.parseJson(null) == null
        service.parseJson("") == null
        service.parseJson("   ") == null
    }

    def "parseJson handles primitive values"() {
        expect:
        service.parseJson('"string"') == "string"
        service.parseJson('42') == 42
        service.parseJson('3.14') == 3.14
        service.parseJson('true') == true
        service.parseJson('false') == false
        service.parseJson('null') == null
    }

    def "parseJson handles objects"() {
        when:
        def result = service.parseJson('{"name":"test","value":123}')

        then:
        result instanceof Map
        result.name == "test"
        result.value == 123
    }

    def "parseJson handles arrays"() {
        when:
        def result = service.parseJson('[1,"two",true,null]')

        then:
        result instanceof List
        result.size() == 4
        result[0] == 1
        result[1] == "two"
        result[2] == true
        result[3] == null
    }

    def "parseJson throws exception for invalid JSON"() {
        when:
        service.parseJson("invalid json {")

        then:
        thrown(JsonProcessingException)
    }

    // Round-trip tests

    def "round-trip serialization preserves primitive values"() {
        expect:
        service.fromJson(service.toJson(value), value.class) == value

        where:
        value << ["test", 42, true, false]
    }

    def "round-trip serialization preserves collections"() {
        given:
        def original = [name: "test", values: [1, 2, 3], enabled: true]

        when:
        def json = service.toJson(original)
        def restored = service.fromJson(json, Map.class)

        then:
        restored.name == original.name
        restored.values == original.values
        restored.enabled == original.enabled
    }

    def "round-trip serialization preserves nested structures"() {
        given:
        def original = [
            metadata: [name: "app", version: "1.0"],
            config: [debug: true, port: 8080],
            items: [
                [id: 1, active: true],
                [id: 2, active: false]
            ]
        ]

        when:
        def json = service.toJson(original)
        def restored = service.fromJson(json, Map.class)

        then:
        restored.metadata.name == original.metadata.name
        restored.metadata.version == original.metadata.version
        restored.config.debug == original.config.debug
        restored.config.port == original.config.port
        restored.items.size() == original.items.size()
        restored.items[0].id == original.items[0].id
    }

    // Edge cases and error handling

    def "handles large data structures"() {
        given:
        def large = [:]
        (1..100).each { i ->
            large["item$i"] = [
                id: i,
                name: "Item $i",
                data: [value: i * 2, enabled: i % 2 == 0]
            ]
        }

        when:
        def json = service.toJson(large)
        def restored = service.fromJson(json, Map.class)

        then:
        json.length() > 5000
        restored.size() == 100
        restored["item50"].id == 50
        restored["item50"].data.value == 100
    }

    def "handles Unicode and special characters"() {
        given:
        def data = [
            unicode: "测试 ñoño русский",
            special: "Line\\nBreak\\tTab\\\"Quote",
            symbols: "©®™€£¥"
        ]

        when:
        def json = service.toJson(data)
        def restored = service.fromJson(json, Map.class)

        then:
        restored.unicode == data.unicode
        restored.special == data.special
        restored.symbols == data.symbols
    }

    def "handles empty and whitespace strings"() {
        expect:
        service.toJson("") == '""'
        service.toJson("   ") == '"   "'
        service.fromJson('""', String.class) == ""
        service.fromJson('"   "', String.class) == "   "
    }

    def "parseJsonArray handles mixed data types"() {
        when:
        def result = service.parseJsonArray('[{"key":"value"},{"id":1,"name":"test"}]')

        then:
        result.size() == 2
        result[0] instanceof Map
        result[0].key == "value"
        result[1] instanceof Map
        result[1].id == 1
        result[1].name == "test"
    }

    /**
     * Testable implementation of JsonServiceImpl
     */
    static class TestableJsonServiceImpl extends JsonServiceImpl {
        TestableJsonServiceImpl() {
            super()
        }
        
        @Override
        org.gradle.api.services.BuildServiceParameters.None getParameters() {
            return null
        }
    }
}