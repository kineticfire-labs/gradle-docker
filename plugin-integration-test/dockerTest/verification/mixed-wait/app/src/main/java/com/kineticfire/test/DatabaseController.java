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

package com.kineticfire.test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests database connectivity to PostgreSQL.
 * Demonstrates realistic multi-service orchestration (app + database).
 */
@RestController
public class DatabaseController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/db")
    public Map<String, Object> testDatabase() {
        Map<String, Object> response = new HashMap<>();
        try {
            // Test database connectivity with simple query
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            response.put("status", "connected");
            response.put("testQuery", result);
            response.put("database", "PostgreSQL");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }
}
