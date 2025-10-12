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

import java.util.List;
import java.util.Map;

/**
 * REST controller for user management, demonstrating METHOD lifecycle testing.
 * Each test gets a fresh database, ensuring complete isolation.
 */
@RestController
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Health endpoint for Docker health checks
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis(),
            "userCount", userRepository.count()
        );
    }

    /**
     * Create a new user.
     * POST /users
     * Body: {"username": "alice", "email": "alice@example.com"}
     * Response: {"id": 1, "username": "alice", "email": "alice@example.com"}
     */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String email = payload.get("email");

        if (username == null || email == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and email required"));
        }

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.status(409).body(Map.of("error", "user already exists"));
        }

        User user = new User(username, email);
        user = userRepository.save(user);

        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail()
        ));
    }

    /**
     * Get a user by username.
     * GET /users/{username}
     * Response: {"id": 1, "username": "alice", "email": "alice@example.com"}
     */
    @GetMapping("/users/{username}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String username) {
        return userRepository.findByUsername(username)
            .map(user -> ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail()
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all users.
     * GET /users
     * Response: [{"id": 1, "username": "alice", "email": "alice@example.com"}, ...]
     */
    @GetMapping("/users")
    public List<Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream()
            .map(user -> Map.of(
                "id", (Object) user.getId(),
                "username", (Object) user.getUsername(),
                "email", (Object) user.getEmail()
            ))
            .toList();
    }

    /**
     * Delete a user by username.
     * DELETE /users/{username}
     * Response: {"status": "deleted", "username": "alice"}
     */
    @DeleteMapping("/users/{username}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable String username) {
        return userRepository.findByUsername(username)
            .map(user -> {
                userRepository.delete(user);
                return ResponseEntity.ok(Map.of(
                    "status", "deleted",
                    "username", username
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Root endpoint for basic connectivity testing.
     */
    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
            "message", "Isolated Tests App is running",
            "version", "1.0.0"
        );
    }
}
