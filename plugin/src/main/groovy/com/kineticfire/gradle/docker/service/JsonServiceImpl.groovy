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
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.kineticfire.gradle.docker.model.ComposeState
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import java.nio.file.Files
import java.nio.file.Path

/**
 * Jackson-based implementation of JSON service
 */
abstract class JsonServiceImpl implements BuildService<BuildServiceParameters.None>, JsonService {
    
    private final ObjectMapper objectMapper
    
    JsonServiceImpl() {
        this.objectMapper = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, false)
    }
    
    @Override
    String toJson(Object object) throws JsonProcessingException {
        if (object == null) {
            return "null"
        }
        return objectMapper.writeValueAsString(object)
    }
    
    @Override
    <T> T fromJson(String json, Class<T> type) throws JsonProcessingException {
        if (!json || json.trim().empty) {
            return null
        }
        return objectMapper.readValue(json, type)
    }
    
    @Override
    List<Map<String, Object>> parseJsonArray(String json) throws JsonProcessingException {
        if (!json || json.trim().empty) {
            return []
        }
        
        TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<List<Map<String, Object>>>() {}
        return objectMapper.readValue(json, typeRef)
    }
    
    /**
     * Parse a generic object from JSON
     */
    Object parseJson(String json) throws JsonProcessingException {
        if (!json || json.trim().empty) {
            return null
        }
        return objectMapper.readValue(json, Object.class)
    }
    
    /**
     * Get the underlying ObjectMapper for advanced operations
     */
    ObjectMapper getObjectMapper() {
        return objectMapper
    }
    
    /**
     * Write ComposeState to a JSON file
     */
    void writeComposeState(ComposeState composeState, Path outputFile) {
        try {
            def json = toJson(composeState)
            Files.createDirectories(outputFile.parent)
            Files.writeString(outputFile, json)
        } catch (Exception e) {
            throw new RuntimeException("Failed to write compose state to ${outputFile}", e)
        }
    }
    
    /**
     * Read ComposeState from a JSON file
     */
    ComposeState readComposeState(Path inputFile) {
        try {
            if (!Files.exists(inputFile)) {
                throw new RuntimeException("File does not exist: ${inputFile}")
            }
            def json = Files.readString(inputFile)
            return fromJson(json, ComposeState.class)
        } catch (Exception e) {
            throw new RuntimeException("Failed to read compose state from ${inputFile}", e)
        }
    }
}