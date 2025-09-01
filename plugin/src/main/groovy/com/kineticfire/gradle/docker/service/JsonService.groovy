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

/**
 * Service interface for JSON processing operations
 */
interface JsonService {
    
    /**
     * Convert an object to JSON string
     * @param object Object to serialize
     * @return JSON string representation
     * @throws JsonProcessingException if serialization fails
     */
    String toJson(Object object) throws JsonProcessingException
    
    /**
     * Parse JSON string to specified type
     * @param json JSON string
     * @param type Target type class
     * @return Parsed object
     * @throws JsonProcessingException if parsing fails
     */
    <T> T fromJson(String json, Class<T> type) throws JsonProcessingException
    
    /**
     * Parse JSON array string to list
     * @param json JSON array string
     * @return List of maps representing JSON objects
     * @throws JsonProcessingException if parsing fails
     */
    List<Map<String, Object>> parseJsonArray(String json) throws JsonProcessingException
}