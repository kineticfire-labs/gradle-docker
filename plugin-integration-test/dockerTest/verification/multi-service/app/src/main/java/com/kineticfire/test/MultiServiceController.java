package com.kineticfire.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for multi-service orchestration testing.
 * <p>
 * Provides endpoints to test:
 * - PostgreSQL database operations (save, retrieve, count)
 * - Redis cache operations (set, get, delete)
 * - Service health
 */
@RestController
public class MultiServiceController {
    private static final Logger logger = LoggerFactory.getLogger(MultiServiceController.class);

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("database", "connected");
        health.put("redis", "connected");
        return health;
    }

    /**
     * Save a message to PostgreSQL database.
     */
    @PostMapping("/messages")
    public Map<String, Object> saveMessage(@RequestBody Map<String, String> payload) {
        String content = payload.get("content");
        String author = payload.get("author");
        Message message = new Message(content, author);
        message = messageRepository.save(message);
        logger.info("Saved message to database: id={}, content={}", message.getId(), content);

        Map<String, Object> response = new HashMap<>();
        response.put("id", message.getId());
        response.put("content", message.getContent());
        response.put("author", message.getAuthor());
        return response;
    }

    /**
     * Retrieve all messages from PostgreSQL database.
     */
    @GetMapping("/messages")
    public List<Message> getAllMessages() {
        List<Message> messages = messageRepository.findAll();
        logger.info("Retrieved {} messages from database", messages.size());
        return messages;
    }

    /**
     * Get count of messages in database.
     */
    @GetMapping("/messages/count")
    public Map<String, Object> getMessageCount() {
        long count = messageRepository.count();
        Map<String, Object> response = new HashMap<>();
        response.put("count", count);
        return response;
    }

    /**
     * Store a value in Redis cache.
     */
    @PostMapping("/cache/{key}")
    public Map<String, Object> setCache(@PathVariable String key, @RequestBody Map<String, String> payload) {
        String value = payload.get("value");
        redisTemplate.opsForValue().set(key, value);
        logger.info("Stored in Redis cache: key={}, value={}", key, value);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        response.put("cached", true);
        return response;
    }

    /**
     * Retrieve a value from Redis cache.
     */
    @GetMapping("/cache/{key}")
    public Map<String, Object> getCache(@PathVariable String key) {
        String value = redisTemplate.opsForValue().get(key);
        logger.info("Retrieved from Redis cache: key={}, value={}", key, value);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        response.put("found", value != null);
        return response;
    }

    /**
     * Delete a value from Redis cache.
     */
    @DeleteMapping("/cache/{key}")
    public Map<String, Object> deleteCache(@PathVariable String key) {
        Boolean deleted = redisTemplate.delete(key);
        logger.info("Deleted from Redis cache: key={}, success={}", key, deleted);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("deleted", deleted != null && deleted);
        return response;
    }

    /**
     * Get info about the application and its dependencies.
     */
    @GetMapping("/info")
    public Map<String, Object> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("app", "Multi-Service Orchestration Demo");
        info.put("services", List.of("app", "postgres", "redis", "nginx"));
        info.put("purpose", "Demonstrate complex multi-service orchestration");
        return info;
    }
}
