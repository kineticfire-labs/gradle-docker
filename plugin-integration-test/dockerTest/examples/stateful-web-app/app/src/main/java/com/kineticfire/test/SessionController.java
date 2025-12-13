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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for session management, demonstrating CLASS lifecycle testing.
 * Tests build on each other: register → login → update → logout
 */
@RestController
public class SessionController {

    // In-memory user storage (username → password)
    private final Map<String, String> users = new ConcurrentHashMap<>();

    // Active sessions (sessionId → username)
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    // User profiles (username → profile data)
    private final Map<String, Map<String, String>> profiles = new ConcurrentHashMap<>();

    /**
     * Health endpoint for Docker health checks
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Register a new user account.
     * POST /register
     * Body: {"username": "alice", "password": "secret123"}
     * Response: {"status": "registered", "username": "alice"}
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and password required"));
        }

        if (users.containsKey(username)) {
            return ResponseEntity.status(409).body(Map.of("error", "user already exists"));
        }

        users.put(username, password);
        profiles.put(username, new ConcurrentHashMap<>());

        return ResponseEntity.ok(Map.of(
            "status", "registered",
            "username", username
        ));
    }

    /**
     * Login to an existing account.
     * POST /login
     * Body: {"username": "alice", "password": "secret123"}
     * Response: {"status": "logged_in", "sessionId": "uuid-here", "username": "alice"}
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and password required"));
        }

        String storedPassword = users.get(username);
        if (storedPassword == null || !storedPassword.equals(password)) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid credentials"));
        }

        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, username);

        return ResponseEntity.ok(Map.of(
            "status", "logged_in",
            "sessionId", sessionId,
            "username", username
        ));
    }

    /**
     * Update user profile (requires active session).
     * PUT /profile
     * Body: {"sessionId": "uuid", "email": "alice@example.com", "fullName": "Alice Smith"}
     * Response: {"status": "updated", "username": "alice"}
     */
    @PutMapping("/profile")
    public ResponseEntity<Map<String, String>> updateProfile(@RequestBody Map<String, String> payload) {
        String sessionId = payload.get("sessionId");

        if (sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId required"));
        }

        String username = sessions.get(sessionId);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid or expired session"));
        }

        Map<String, String> profile = profiles.get(username);
        payload.forEach((key, value) -> {
            if (!key.equals("sessionId")) {
                profile.put(key, value);
            }
        });

        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "username", username
        ));
    }

    /**
     * Get user profile (requires active session).
     * GET /profile/{sessionId}
     * Response: {"username": "alice", "email": "alice@example.com", "fullName": "Alice Smith"}
     */
    @GetMapping("/profile/{sessionId}")
    public ResponseEntity<Map<String, String>> getProfile(@PathVariable String sessionId) {
        String username = sessions.get(sessionId);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid or expired session"));
        }

        Map<String, String> profile = new ConcurrentHashMap<>(profiles.get(username));
        profile.put("username", username);

        return ResponseEntity.ok(profile);
    }

    /**
     * Logout (invalidate session).
     * DELETE /logout/{sessionId}
     * Response: {"status": "logged_out"}
     */
    @DeleteMapping("/logout/{sessionId}")
    public ResponseEntity<Map<String, String>> logout(@PathVariable String sessionId) {
        String username = sessions.remove(sessionId);

        if (username == null) {
            return ResponseEntity.status(404).body(Map.of("error", "session not found"));
        }

        return ResponseEntity.ok(Map.of(
            "status", "logged_out",
            "username", username
        ));
    }

    /**
     * Root endpoint for basic connectivity testing.
     */
    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
            "message", "Stateful Web App is running",
            "version", "1.0.0"
        );
    }
}
