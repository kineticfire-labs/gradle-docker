package com.kineticfire.test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class StateController {

    private final Map<String, String> state = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @PostMapping("/state")
    public ResponseEntity<Map<String, String>> setState(@RequestBody Map<String, String> payload) {
        String key = payload.get("key");
        String value = payload.get("value");

        if (key == null || value == null) {
            return ResponseEntity.badRequest().build();
        }

        state.put(key, value);

        Map<String, String> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/state/{key}")
    public ResponseEntity<Map<String, String>> getState(@PathVariable String key) {
        String value = state.get(key);

        if (value == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/")
    public Map<String, String> root() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Lifecycle Class Test App");
        response.put("version", "1.0.0");
        return response;
    }
}
