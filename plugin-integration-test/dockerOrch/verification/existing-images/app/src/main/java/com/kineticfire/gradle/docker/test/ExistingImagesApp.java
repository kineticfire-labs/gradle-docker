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

package com.kineticfire.gradle.docker.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import redis.clients.jedis.Jedis;

import java.util.Map;

/**
 * Simple Spring Boot app that demonstrates interaction with public Docker images (nginx and redis).
 *
 * This app provides endpoints to:
 * - Test health check
 * - Fetch content from nginx
 * - Store and retrieve data from redis
 */
@SpringBootApplication
@RestController
public class ExistingImagesApp {

    private final String nginxHost;
    private final int nginxPort;
    private final String redisHost;
    private final int redisPort;
    private final RestTemplate restTemplate;

    public ExistingImagesApp() {
        // Read nginx configuration from environment variables
        this.nginxHost = System.getenv().getOrDefault("NGINX_HOST", "localhost");
        this.nginxPort = Integer.parseInt(System.getenv().getOrDefault("NGINX_PORT", "80"));

        // Read redis configuration from environment variables
        this.redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        this.redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        this.restTemplate = new RestTemplate();
    }

    public static void main(String[] args) {
        SpringApplication.run(ExistingImagesApp.class, args);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    /**
     * Fetch content from nginx static file server.
     */
    @GetMapping("/nginx")
    public ResponseEntity<String> getNginxContent() {
        try {
            String url = String.format("http://%s:%d/index.html", nginxHost, nginxPort);
            String content = restTemplate.getForObject(url, String.class);
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /**
     * Test redis connection with ping.
     */
    @GetMapping("/redis/test")
    public ResponseEntity<Map<String, String>> testRedis() {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            String response = jedis.ping();
            return ResponseEntity.ok(Map.of("redis", response));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Store a value in redis cache.
     */
    @PostMapping("/cache/{key}")
    public ResponseEntity<Map<String, String>> setCache(
            @PathVariable String key,
            @RequestBody String value) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            jedis.set(key, value);
            return ResponseEntity.ok(Map.of("status", "stored", "key", key));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Retrieve a value from redis cache.
     */
    @GetMapping("/cache/{key}")
    public ResponseEntity<String> getCache(@PathVariable String key) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            String value = jedis.get(key);
            if (value == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(value);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /**
     * Delete a value from redis cache.
     */
    @DeleteMapping("/cache/{key}")
    public ResponseEntity<Map<String, String>> deleteCache(@PathVariable String key) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            Long deleted = jedis.del(key);
            if (deleted > 0) {
                return ResponseEntity.ok(Map.of("status", "deleted", "key", key));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
