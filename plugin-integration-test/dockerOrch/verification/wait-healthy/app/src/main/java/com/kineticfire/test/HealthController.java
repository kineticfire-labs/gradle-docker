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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller providing health check endpoint for wait-healthy verification.
 */
@RestController
public class HealthController {

    private final long startTime = System.currentTimeMillis();
    private final long startupDelayMs;

    public HealthController() {
        // Read startup delay from environment variable
        // The entrypoint.sh script delays for this amount before starting Java
        String delayMs = System.getenv().getOrDefault("STARTUP_DELAY_MS", "0");
        try {
            this.startupDelayMs = Long.parseLong(delayMs);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid STARTUP_DELAY_MS: " + delayMs, e);
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        // Include startup delay in uptime to reflect total time since container initialization
        response.put("uptimeMs", (System.currentTimeMillis() - startTime) + startupDelayMs);
        return response;
    }

    @GetMapping("/")
    public Map<String, String> root() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Wait-healthy verification test app");
        response.put("version", "1.0.0");
        return response;
    }
}
